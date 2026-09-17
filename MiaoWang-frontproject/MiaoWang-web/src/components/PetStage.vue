<script setup lang="ts">
/**
 * 3D 电子宠物舞台
 *
 * 硬需求（用户指定）：
 *  - 绕模型 180° 旋转；**模型自身不旋转、不位移**
 *  - 摄像机**高度恒定、俯仰角恒定、到模型距离恒定**，只有水平位置在变
 *    （用户说的"z 轴不变、只改 xy"是 Z-up/Blender 习惯，换算到 Three.js 的 Y-up 即：y 恒定、水平绕圈）
 *
 * 取景方式：**自适应**。按 fit（模型占舞台高度的比例）+ pitchDeg（俯仰角）推导相机距离与高度，
 * 再叠加运行时归一化（居中、落地、最长边=1）→ 换任何模型都自动填满画面，不用重调参数。
 */
import { onBeforeUnmount, onMounted, ref } from 'vue'
import * as THREE from 'three'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'

const props = withDefaults(
  defineProps<{
    modelUrl?: string
    /** 模型占舞台高度的比例（越大越满，1 = 顶到上下边） */
    fit?: number
    /** 俯仰角（度）：相机俯视模型的角度，恒定 */
    pitchDeg?: number
    /** 显式指定相机距离；给了就不再按 fit 推导 */
    radius?: number
    /** 显式指定相机高度；给了就不再按俯仰角推导 */
    camY?: number
    fov?: number
    /** 方位角扫描范围（弧度），π = 180° */
    azimuthRange?: number
    /** 一个来回的秒数 */
    period?: number
    /** 模型静态朝向（摆正正面用，不是旋转动画） */
    modelYaw?: number
    /** 是否播放模型自带待机动画 */
    animate?: boolean
    /** 舞台辉光色 */
    tint?: string
    /** 是否显示浮尘 */
    motes?: boolean
  }>(),
  {
    modelUrl: '/models/toon_cat_free.glb',
    fit: 0.8,
    pitchDeg: 12,
    fov: 36,
    azimuthRange: Math.PI,
    period: 24,
    modelYaw: 0,
    animate: true,
    tint: '#35d6a8',
    motes: true,
  },
)

const emit = defineEmits<{
  ready: []
  failed: [message: string]
  progress: [percent: number]
}>()

const host = ref<HTMLDivElement | null>(null)
const loading = ref(true)
const failed = ref('')

let renderer: THREE.WebGLRenderer | undefined
let scene: THREE.Scene | undefined
let camera: THREE.PerspectiveCamera | undefined
let mixer: THREE.AnimationMixer | undefined
let halo: THREE.Mesh | undefined
let motesPoints: THREE.Points | undefined
let raf = 0
let clock: THREE.Clock | undefined
let observer: IntersectionObserver | undefined
let resizer: ResizeObserver | undefined
let visible = true

/** 取景参数：模型加载后按 fit / pitchDeg 推导出来（加载前给个安全初值） */
let camRadius = 2
let camHeight = 1
let camTargetY = 0.5

/** 方位角：sin 相位驱动 → 端点自然减速，不会硬拐弯 */
const center = () => props.azimuthRange / 2
let phase = -Math.PI / 2 // 从 0° 端开始
let dragging = false
let dragStartX = 0
let dragStartAzimuth = 0

const reduceMotion =
  typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

function clamp(v: number, a: number, b: number) {
  return Math.min(b, Math.max(a, v))
}

/** 由拖动直接设定方位角，并把相位切到"离当前相位最近"的那一支，避免跳变 */
function setAzimuth(a: number) {
  const range = props.azimuthRange
  const az = clamp(a, 0, range)
  const u = clamp((az - center()) / (range / 2), -1, 1)
  const base = Math.asin(u)
  const candidates = [base, Math.PI - base, base + 2 * Math.PI, Math.PI - base + 2 * Math.PI]
  phase = candidates.reduce((best, c) => (Math.abs(c - phase) < Math.abs(best - phase) ? c : best))
}

function currentAzimuth() {
  return center() + (props.azimuthRange / 2) * Math.sin(phase)
}

/** 生成径向渐变纹理：用于接地阴影、光池、身后追光 */
function radialTex(inner: string, mid: string, outer: string, size = 256) {
  const c = document.createElement('canvas')
  c.width = c.height = size
  const ctx = c.getContext('2d')!
  const g = ctx.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2)
  g.addColorStop(0, inner)
  g.addColorStop(0.45, mid)
  g.addColorStop(1, outer)
  ctx.fillStyle = g
  ctx.fillRect(0, 0, size, size)
  const tex = new THREE.CanvasTexture(c)
  tex.colorSpace = THREE.SRGBColorSpace
  return tex
}

function billboard(obj: THREE.Object3D) {
  obj.lookAt(camera!.position)
}

function disposeScene() {
  if (scene) {
    scene.traverse((obj) => {
      const mesh = obj as THREE.Mesh
      if (mesh.geometry) mesh.geometry.dispose()
      const mat = mesh.material as THREE.Material | THREE.Material[] | undefined
      if (Array.isArray(mat)) mat.forEach((m) => m.dispose())
      else if (mat) {
        const anyMat = mat as THREE.MeshStandardMaterial
        anyMat.map?.dispose()
        mat.dispose()
      }
    })
  }
  mixer?.stopAllAction()
  mixer = undefined
  halo = undefined
  motesPoints = undefined
  scene = undefined
}

function setup() {
  const el = host.value
  if (!el) return

  scene = new THREE.Scene()

  camera = new THREE.PerspectiveCamera(props.fov, 1, 0.1, 100)
  camera.position.set(0, camHeight, camRadius)
  camera.lookAt(0, camTargetY, 0)

  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'low-power' })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.05
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.domElement.style.width = '100%'
  renderer.domElement.style.height = '100%'
  renderer.domElement.style.display = 'block'
  renderer.domElement.style.touchAction = 'pan-y'
  el.appendChild(renderer.domElement)

  // 灯光：中性为主 + 冷暖各一路补光。**刻意不参考模型颜色** —— 模型换了也好看
  scene.add(new THREE.AmbientLight(0x8fa6b5, 0.55))
  const key = new THREE.DirectionalLight(0xffffff, 2.2)
  key.position.set(3, 5, 3)
  key.castShadow = true
  key.shadow.mapSize.set(1024, 1024)
  key.shadow.camera.near = 0.5
  key.shadow.camera.far = 20
  key.shadow.bias = -0.0008
  scene.add(key)
  const cold = new THREE.DirectionalLight(0x7fb8ff, 0.6)
  cold.position.set(-4, 2, -3)
  scene.add(cold)
  const warm = new THREE.DirectionalLight(0xffc98a, 0.5)
  warm.position.set(2, 1, -4)
  scene.add(warm)

  // 舞台元素 ①：脚下的水青光池（比接地阴影更大更淡，营造"打光"感）
  const pool = new THREE.Mesh(
    new THREE.PlaneGeometry(3.6, 3.6),
    new THREE.MeshBasicMaterial({
      map: radialTex('rgba(53,214,168,0.34)', 'rgba(53,214,168,0.12)', 'rgba(53,214,168,0)'),
      transparent: true,
      depthWrite: false,
      blending: THREE.AdditiveBlending,
    }),
  )
  pool.rotation.x = -Math.PI / 2
  pool.position.y = 0.001
  scene.add(pool)

  // 舞台元素 ②：接地阴影（只投影、不显形的地面 + 柔化渐变）
  const shadowPlane = new THREE.Mesh(
    new THREE.PlaneGeometry(4, 4),
    new THREE.ShadowMaterial({ opacity: 0.26 }),
  )
  shadowPlane.rotation.x = -Math.PI / 2
  shadowPlane.receiveShadow = true
  scene.add(shadowPlane)

  const soft = new THREE.Mesh(
    new THREE.PlaneGeometry(1.6, 1.6),
    new THREE.MeshBasicMaterial({
      map: radialTex('rgba(0,0,0,0.5)', 'rgba(0,0,0,0.18)', 'rgba(0,0,0,0)'),
      transparent: true,
      depthWrite: false,
      opacity: 0.7,
    }),
  )
  soft.rotation.x = -Math.PI / 2
  soft.position.y = 0.002
  scene.add(soft)

  // 舞台元素 ③：身后的追光晕（billboard，随相机移动，绕到背面也不穿帮）
  halo = new THREE.Mesh(
    new THREE.PlaneGeometry(5.2, 5.2),
    new THREE.MeshBasicMaterial({
      map: radialTex('rgba(53,214,168,0.26)', 'rgba(53,214,168,0.07)', 'rgba(53,214,168,0)'),
      transparent: true,
      depthWrite: false,
      blending: THREE.AdditiveBlending,
    }),
  )
  scene.add(halo)

  // 舞台元素 ④：慢飘浮尘（加一点空气感，成本极低）
  if (props.motes) {
    const COUNT = 70
    const pos = new Float32Array(COUNT * 3)
    for (let i = 0; i < COUNT; i++) {
      const r = 0.5 + Math.random() * 1.7
      const a = Math.random() * Math.PI * 2
      pos[i * 3] = Math.cos(a) * r
      pos[i * 3 + 1] = Math.random() * 1.9
      pos[i * 3 + 2] = Math.sin(a) * r
    }
    const geo = new THREE.BufferGeometry()
    geo.setAttribute('position', new THREE.BufferAttribute(pos, 3))
    motesPoints = new THREE.Points(
      geo,
      new THREE.PointsMaterial({
        color: new THREE.Color(props.tint),
        size: 0.022,
        transparent: true,
        opacity: 0.5,
        depthWrite: false,
        blending: THREE.AdditiveBlending,
        sizeAttenuation: true,
      }),
    )
    scene.add(motesPoints)
  }

  const loader = new GLTFLoader()
  loader.load(
    props.modelUrl,
    (gltf) => {
      const root = gltf.scene

      // —— 运行时归一化：先缩放，再按缩放后的实际包围盒居中并落地 ——
      const box = new THREE.Box3().setFromObject(root)
      const size = box.getSize(new THREE.Vector3())
      const maxDim = Math.max(size.x, size.y, size.z) || 1
      root.scale.setScalar(1 / maxDim)

      const box2 = new THREE.Box3().setFromObject(root)
      const c = box2.getCenter(new THREE.Vector3())
      const h = Math.max(box2.max.y - box2.min.y, 1e-3)
      const w = Math.max(box2.max.x - box2.min.x, 1e-3)
      const d = Math.max(box2.max.z - box2.min.z, 1e-3)
      root.position.x -= c.x
      root.position.z -= c.z
      root.position.y -= box2.min.y

      // 模型朝向：静态摆正，**不是**旋转动画
      root.rotation.y = props.modelYaw

      root.traverse((o) => {
        const m = o as THREE.Mesh
        if (m.isMesh) {
          m.castShadow = true
          m.receiveShadow = false
          if (m.material) {
            const mat = m.material as THREE.MeshStandardMaterial
            mat.envMapIntensity = 0.6
            mat.needsUpdate = true
          }
        }
      })

      // —— 自适应取景：让模型占满 fit 比例的舞台高度 ——
      camTargetY = h * 0.5
      camRadius = props.radius ?? h / props.fit / (2 * Math.tan((props.fov * Math.PI) / 180 / 2))
      camHeight = props.camY ?? camTargetY + Math.tan((props.pitchDeg * Math.PI) / 180) * camRadius

      // 接地阴影按模型的占地尺寸贴合，不是固定大小
      soft.scale.set(w * 2.2, d * 2.2, 1)

      scene!.add(root)

      if (props.animate && gltf.animations.length) {
        mixer = new THREE.AnimationMixer(root)
        gltf.animations.forEach((clip) => mixer!.clipAction(clip).play())
      }

      loading.value = false
      emit('ready')
    },
    (ev) => {
      if (ev.total) emit('progress', Math.round((ev.loaded / ev.total) * 100))
    },
    (err) => {
      loading.value = false
      failed.value = '模型加载失败：' + ((err as Error)?.message || '未知错误')
      emit('failed', failed.value)
    },
  )

  // 拖动 scrub（锁死在 0~180°）
  const onDown = (e: PointerEvent) => {
    dragging = true
    dragStartX = e.clientX
    dragStartAzimuth = currentAzimuth()
    renderer!.domElement.setPointerCapture?.(e.pointerId)
  }
  const onMove = (e: PointerEvent) => {
    if (!dragging) return
    const dx = e.clientX - dragStartX
    setAzimuth(dragStartAzimuth - (dx / el.clientWidth) * props.azimuthRange)
  }
  const onUp = () => {
    dragging = false
  }
  renderer.domElement.addEventListener('pointerdown', onDown)
  renderer.domElement.addEventListener('pointermove', onMove)
  renderer.domElement.addEventListener('pointerup', onUp)
  renderer.domElement.addEventListener('pointercancel', onUp)

  const resize = () => {
    if (!renderer || !camera || !el) return
    const w2 = el.clientWidth || 1
    const h2 = el.clientHeight || 1
    renderer.setSize(w2, h2, false)
    camera.aspect = w2 / h2
    camera.updateProjectionMatrix()
  }
  resize()
  resizer = new ResizeObserver(resize)
  resizer.observe(el)

  // 离屏即停：省电
  observer = new IntersectionObserver(
    (entries) => {
      visible = entries[0]?.isIntersecting ?? true
    },
    { threshold: 0.05 },
  )
  observer.observe(el)

  clock = new THREE.Clock()
  const tick = () => {
    raf = requestAnimationFrame(tick)
    const dt = Math.min(clock!.getDelta(), 0.1)

    // 浮尘与追光在离屏时也不用更新
    if (!visible || document.hidden) return

    if (!dragging && !reduceMotion) {
      phase += (Math.PI * 2 * dt) / props.period
    }

    const az = currentAzimuth()
    // 关键：只有水平位置在变，高度 camHeight 与到模型的距离 camRadius 恒定
    camera!.position.set(camRadius * Math.sin(az), camHeight, camRadius * Math.cos(az))
    camera!.lookAt(0, camTargetY, 0)

    // 追光晕始终待在模型"背面"（相机方向的反侧），并正对相机
    if (halo) {
      const dir = camera!.position.clone().normalize()
      halo.position.copy(dir).multiplyScalar(-1.7)
      halo.position.y = camTargetY + 0.15
      billboard(halo)
    }

    // 浮尘缓慢上飘 + 越界回绕
    if (motesPoints) {
      const arr = motesPoints.geometry.attributes.position as THREE.BufferAttribute
      for (let i = 0; i < arr.count; i++) {
        let y = arr.getY(i) + dt * 0.045
        if (y > 2.1) y = 0
        arr.setY(i, y)
      }
      arr.needsUpdate = true
    }

    mixer?.update(dt)
    renderer!.render(scene!, camera!)
  }
  tick()
}

onMounted(setup)

onBeforeUnmount(() => {
  cancelAnimationFrame(raf)
  observer?.disconnect()
  resizer?.disconnect()
  disposeScene()
  if (renderer) {
    renderer.dispose()
    renderer.forceContextLoss?.()
    renderer.domElement.remove()
    renderer = undefined
  }
  camera = undefined
  clock = undefined
})
</script>

<template>
  <div ref="host" class="pet-stage">
    <div v-if="loading" class="pet-stage__veil" aria-hidden="true">
      <div class="pet-stage__breath" />
    </div>
    <p v-else-if="failed" class="pet-stage__error">{{ failed }}</p>
  </div>
</template>

<style scoped>
.pet-stage {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 240px;
  overflow: hidden;
  /* 舞台底色 + 顶部极淡辉光：这是全站唯一允许用渐变的地方 */
  background:
    radial-gradient(120% 70% at 50% -10%, rgba(53, 214, 168, 0.1), transparent 60%), var(--mw-night);
}

/* 暗角：把视线收拢到中间的宠物身上 */
.pet-stage::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: radial-gradient(120% 85% at 50% 46%, transparent 52%, rgba(0, 0, 0, 0.38));
}

.pet-stage__veil {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
}

/* 首帧前：呼吸占位，不用"加载中…"纯文字 */
.pet-stage__breath {
  width: 96px;
  height: 96px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(53, 214, 168, 0.22), transparent 70%);
  animation: pet-breath 2.2s ease-in-out infinite;
}

@keyframes pet-breath {
  0%,
  100% {
    transform: scale(0.86);
    opacity: 0.5;
  }
  50% {
    transform: scale(1.06);
    opacity: 1;
  }
}

.pet-stage__error {
  position: absolute;
  inset: auto 0 16px;
  margin: 0;
  text-align: center;
  font-size: var(--mw-font-caption);
  color: var(--mw-on-night-2);
}
</style>
