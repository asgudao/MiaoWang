<script setup lang="ts">
/**
 * 3D 电子宠物舞台
 *
 * 硬需求（用户指定）：
 *  - 绕模型 360° 旋转（一整圈，连续循环，首尾无缝）；**模型自身不旋转、不位移**
 *  - 摄像机**高度恒定、俯仰角恒定、到模型距离恒定**，只有水平位置在变
 *    （用户说的"z 轴不变、只改 xy"是 Z-up/Blender 习惯，换算到 Three.js 的 Y-up 即：y 恒定、水平绕圈）
 *
 * **自动转动 → 仅手动拖动（2026-09-21，用户明确要求"摄像机不要自己缓慢转动"）**：
 *  - 改前：每帧 `azimuth += 2π·dt/period`（period = 24s）**匀速自转一整圈**，拖动时暂停、松手继续自转。
 *  - 改后：**没有任何随时间的方位推进** —— 方位角只在 `pointermove` → `setAzimuth` 时被**直接赋值**；
 *    松手那一刻就定住，既不自转、也不回弹、也不"缓缓漂回初始方位"。
 *    用户禁的是"自己缓慢转动"，**手动拖动是舞台的核心交互，必须保留**（拖满一个画布宽 = 整圈）。
 *  - 自转去掉后**一起删掉的死代码**（它们唯一的作用就是驱动自转，留着等于给下一个人留坑）：
 *    · `period` prop —— 唯一用途是自转周期；调用处（`views/HomeView.vue`）也从来没传过它；
 *    · `prefers-reduced-motion` 的 `reduceMotion` —— 唯一作用是"关掉自转"，自转没了它就没对象了。
 *  - **不动的部分**：`azimuthRange`（= 2π）仍然同时管两件事 —— ① 拖动灵敏度；② 取景采样范围
 *    （见 `petStageMath.ts` 的 AZ_FIT）。所以它**不是**死常量，采样段数也不动。
 *  - 半圈（180°）→ 整圈（360°）是 2026-09-21 更早一步的改动，留下的结论是"**不做区间钳制**"：
 *    "无缝"来自 `sin/cos` 本身以 2π 为周期（取到 2π 与取到 0 是同一个相机位置），
 *    钳制才会在某个方位硬停一下（可见跳变）；拖动也因此可以连续转好几圈。
 *  - ⚠️ 取景采样范围必须与 `azimuthRange` 同步（见 `petStageMath.ts` 的 AZ_FIT / AZ_CHECK）：
 *    半径取的是"所有采样方位的最大值"，采样只覆盖半圈时，剩下的半圈没人保证。
 *
 * 取景数学全部在 `petStageMath.ts`（归一化 + 自适应取景），这里只管渲染与生命周期。
 * 抽出去的原因很实际：这段数学的失败方式是**静默**的（模型被缩成几个像素、不报错），
 * 抽成纯模块后 `tools/check-3d-model.js` 能用真 three + 真 GLB 离线复算，把"肉眼才能发现"变成门禁能抓。
 *
 * 失败**一律有可见反馈**（不能静默）：
 *  - WebGL 不可用 / 初始化抛异常 → 舞台中央的卡片 + 重试按钮
 *  - 模型加载失败 / 15 秒看门狗超时 → 同上
 *  - 舞台量到 0 尺寸 → 顶部提示条（不挡住画布，尺寸恢复后自动消失）
 *  - 加载成功但模型在画面里占比过小（"看不见"的典型症状）→ 顶部提示条
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import * as THREE from 'three'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import { frameStageModel, projectedExtent, reframe, type StageFrame } from './petStageMath'

const props = withDefaults(
  defineProps<{
    modelUrl?: string
    /** fit = 模型在任意方位最多占画框的比例（0.8 = 四周各留 20% 余量） */
    fit?: number
    /** 俯仰角（度）：相机俯视模型的角度，恒定 */
    pitchDeg?: number
    /** 显式指定相机距离；给了就不再按 fit 推导 */
    radius?: number
    /** 显式指定相机高度；给了就不再按俯仰角推导 */
    camY?: number
    fov?: number
    /** 转台扫过的方位范围（弧度），2π = 360° = 整圈；取景采样范围必须与它相同。
     *  它同时也是**拖动灵敏度**：拖满一个画布宽 = 转过整个区间（不再有自动转动，见文件头） */
    azimuthRange?: number
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
    azimuthRange: Math.PI * 2,
    modelYaw: 0,
    animate: true,
    tint: '#ffc98a',
    motes: true,
  },
)

const emit = defineEmits<{
  ready: []
  failed: [message: string]
  progress: [percent: number]
}>()

/** 模型在画面里的占比低于这个值就当成"看不见"（正常约等于 fit）→ 报出来，不静默 */
const MIN_COVERAGE = 0.15
/** 加载看门狗：模型迟迟不回调（网络卡住、请求静默失败）也要有可见结果 */
const LOAD_TIMEOUT_MS = 15000

const SIZE_MSG = '舞台量到 0 尺寸，画布没有地方可画。检查这个区域是不是被隐藏了，或者高度是 0。'
const WEBGL_MSG = '这台浏览器没有可用的 WebGL，3D 舞台显示不了。可以换个浏览器，或在设置里打开硬件加速。'
const CONTEXT_LOST_MSG = '显卡上下文丢了，通常是显存不足或驱动重启。刷新页面再看看。'
const TIMEOUT_MSG = '等了 15 秒还没拿到模型，可能是网络太慢。检查网络后重新加载试试。'

const host = ref<HTMLDivElement | null>(null)
const loading = ref(true)
const failed = ref('')
const sizeWarn = ref(false)
const coverageWarn = ref('')

/** 硬失败优先；没有硬失败才显示软警告 */
const notice = computed(() => (sizeWarn.value ? SIZE_MSG : coverageWarn.value))

let renderer: THREE.WebGLRenderer | undefined
let scene: THREE.Scene | undefined
let camera: THREE.PerspectiveCamera | undefined
let mixer: THREE.AnimationMixer | undefined
let modelRoot: THREE.Object3D | undefined
let stageFrame: StageFrame | undefined
let halo: THREE.Mesh | undefined
let motesPoints: THREE.Points | undefined
/** 接地阴影贴片：模型加载后要按它的占地尺寸缩放 */
let softPlane: THREE.Mesh | undefined
let raf = 0
let clock: THREE.Clock | undefined
let observer: IntersectionObserver | undefined
let resizer: ResizeObserver | undefined
let watchdog = 0
let visible = true

/** 取景参数：模型加载后由 petStageMath 推导出来（加载前给个安全初值） */
let camRadius = 2
let camHeight = 1
let camTargetY = 0.5

/**
 * 方位角（弧度）。**刻意不取模、也不钳制**：
 *  - 整圈没有端点，钳制到 [0, 2π] 只会在钳到的那一刻硬停一下（可见跳变）；
 *  - `sin/cos` 以 2π 为周期，所以取到 2π 与取到 0、取到 -π/2 与取到 3π/2 是同一个相机位置
 *    → 拖动可以连续转好几圈，`azimuthRange` 也才能当"拖满一屏 = 转过整圈"的灵敏度用。
 *  - **只在拖动时被赋值**（`pointermove` → `setAzimuth`）：2026-09-21 去掉自动转动后，
 *    没有任何代码路径会在没有拖动输入的时候改它 —— 不累加、不缓动、不回弹。
 *  - 起始值 0 = 正前方。
 */
let azimuth = 0
let dragging = false
let dragStartX = 0
let dragStartAzimuth = 0

function errText(e: unknown) {
  const msg = e instanceof Error ? e.message : String(e)
  return msg || '未知错误'
}

/** 硬失败：模型/画布出不来，给一张看得见的卡片，并且允许重试 */
function fail(message: string) {
  clearWatchdog()
  loading.value = false
  failed.value = message
  emit('failed', message)
}

function clearWatchdog() {
  if (watchdog) {
    window.clearTimeout(watchdog)
    watchdog = 0
  }
}

/** 拖动直接设定方位角（不钳制：整圈没有端点，见 `azimuth` 的说明） */
function setAzimuth(a: number) {
  azimuth = a
}

function currentAzimuth() {
  return azimuth
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

function disposeObject(obj: THREE.Object3D) {
  obj.traverse((o) => {
    const mesh = o as THREE.Mesh
    if (mesh.geometry) mesh.geometry.dispose()
    const mat = mesh.material as THREE.Material | THREE.Material[] | undefined
    if (Array.isArray(mat)) mat.forEach((m) => m.dispose())
    else if (mat) {
      ;(mat as THREE.MeshStandardMaterial).map?.dispose()
      mat.dispose()
    }
  })
}

function disposeScene() {
  if (scene) disposeObject(scene)
  mixer?.stopAllAction()
  mixer = undefined
  modelRoot = undefined
  stageFrame = undefined
  halo = undefined
  motesPoints = undefined
  softPlane = undefined
  scene = undefined
}

/** 画布现在有多大（量不到就按 1 处理，等 ResizeObserver 报真实尺寸再重算取景） */
function currentAspect() {
  const el = host.value
  if (!el) return 1
  const w = el.clientWidth
  const h = el.clientHeight
  return w > 1 && h > 1 ? w / h : 1
}

function fitOptions() {
  return {
    fit: props.fit,
    pitchDeg: props.pitchDeg,
    fov: props.fov,
    azimuthRange: props.azimuthRange,
    aspect: currentAspect(),
  }
}

/**
 * 取景半径与画布宽高比有关：宽高比变了要重算，否则窄窗口会切边。
 * 同时把"模型在画面里到底占多大"量出来 → 占比过小就是"加载成功但看不见"，必须报出来。
 */
function refit() {
  if (!stageFrame) return
  const opts = fitOptions()
  const fitted = reframe(stageFrame, opts)
  if (fitted) {
    camRadius = props.radius ?? fitted.camRadius
    camHeight = props.camY ?? fitted.camHeight
  }
  const coverage = projectedExtent(
    stageFrame.points,
    stageFrame.camTargetY,
    camRadius,
    camHeight,
    opts,
  )
  coverageWarn.value =
    coverage < MIN_COVERAGE
      ? `模型加载出来了，但在画面里只占 ${Math.round(coverage * 100)}%，几乎看不见。`
      : ''
}

/** WebGL 拿不到就抛带人话的错误（以前这里一抛，onMounted 静默失败，页面上什么都不显示） */
function createRenderer() {
  try {
    return new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'low-power' })
  } catch {
    throw new Error(WEBGL_MSG)
  }
}

function onModelLoaded(gltf: { scene: THREE.Object3D; animations: THREE.AnimationClip[] }) {
  clearWatchdog()
  const root = gltf.scene

  // 先把静态朝向摆正，再量尺寸：量的就是最终姿态
  root.rotation.y = props.modelYaw

  // 归一化 + 取景（关键数学都在 petStageMath 里，量不到就抛，这里转成可见报错）
  let frame: StageFrame
  try {
    frame = frameStageModel(root, fitOptions())
  } catch (e) {
    fail('模型尺寸量不出来，没法取景：' + errText(e))
    return
  }
  stageFrame = frame
  camTargetY = frame.camTargetY

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

  // 接地阴影按模型的占地尺寸贴合，不是固定大小
  softPlane?.scale.set(frame.width * 2.2, frame.depth * 2.2, 1)

  modelRoot = root
  scene!.add(root)

  if (props.animate && gltf.animations.length) {
    mixer = new THREE.AnimationMixer(root)
    gltf.animations.forEach((clip) => mixer!.clipAction(clip).play())
  }

  // 按真实画布尺寸定稿取景（顺便做"看不见"自检）
  refit()
  loading.value = false
  emit('ready')
}

function loadModel() {
  loading.value = true
  failed.value = ''
  coverageWarn.value = ''
  clearWatchdog()
  watchdog = window.setTimeout(() => {
    // 只有一直卡在加载中才报超时（成功/失败路径都会先清掉这个定时器）
    if (loading.value) fail(TIMEOUT_MSG)
  }, LOAD_TIMEOUT_MS)

  const loader = new GLTFLoader()
  loader.load(
    props.modelUrl,
    onModelLoaded,
    (ev) => {
      if (ev.total) emit('progress', Math.round((ev.loaded / ev.total) * 100))
    },
    (err) => fail('模型没能加载出来：' + errText(err)),
  )
}

/** 重试：丢掉上一份模型再加载（不重建 renderer，避免反复丢 WebGL 上下文） */
function reload() {
  if (modelRoot && scene) {
    scene.remove(modelRoot)
    disposeObject(modelRoot)
  }
  mixer?.stopAllAction()
  mixer = undefined
  modelRoot = undefined
  stageFrame = undefined
  loadModel()
}

function setup() {
  const el = host.value
  if (!el) throw new Error('舞台容器没挂上')

  scene = new THREE.Scene()

  camera = new THREE.PerspectiveCamera(props.fov, 1, 0.1, 100)
  camera.position.set(0, camHeight, camRadius)
  camera.lookAt(0, camTargetY, 0)

  renderer = createRenderer()
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.0
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.domElement.style.width = '100%'
  renderer.domElement.style.height = '100%'
  renderer.domElement.style.display = 'block'
  renderer.domElement.style.touchAction = 'pan-y'
  el.appendChild(renderer.domElement)

  // 上下文丢失同样是"舞台突然空了却不报错"的一种：接管默认行为，并给可见提示
  renderer.domElement.addEventListener('webglcontextlost', (ev) => {
    ev.preventDefault()
    fail(CONTEXT_LOST_MSG)
  })

  // 灯光：**均匀照明**（v3.1）。用户反馈"有明有暗 / 一半亮一半暗"，所以不再用
  // "一盏强主光 + 一盏弱环境光"的单侧布光。改前那种配比会让朝向主光的半边很亮、
  // 背过去的半边几乎只剩环境光：用真 three + 真 GLB 离线复算可见表面，
  // 最暗/最亮只有 0.11，屏幕左右半屏亮度比 0.74（复算见 tools/check-3d-model.js 第 ⑩ 段）。
  // 现在换成"高环境底 + 天光 + 多向低强度补光"：
  //   · 环境光给一个足够高的底，暗部不再掉进黑里；
  //   · 天光(HemisphereLight)只分上/下、没有左右偏向 → 给体积感却不制造"半边亮"；
  //   · 主光压到与补光同量级，只负责一点点方向性；
  //   · 一路低饱和冷调光保留，做轮廓分离（不是配色失误：身后的追光晕是暖的）。
  // **刻意不参考模型颜色** —— 模型换了也好看。
  // ⚠️ 曝光保持 1.0：光池 / 追光晕 / 浮尘是 MeshBasicMaterial，不受灯光影响但受曝光影响，
  //    动曝光就会连带改掉舞台背景，而背景已经定稿。
  scene.add(new THREE.AmbientLight(0xe0c3a6, 0.85))
  scene.add(new THREE.HemisphereLight(0xfff0e0, 0xcda98a, 0.75))
  const key = new THREE.DirectionalLight(0xfff6ec, 0.85)
  key.position.set(3, 5, 3)
  key.castShadow = true
  key.shadow.mapSize.set(1024, 1024)
  key.shadow.camera.near = 0.5
  key.shadow.camera.far = 20
  key.shadow.bias = -0.0008
  scene.add(key)
  const cold = new THREE.DirectionalLight(0xa8c0d8, 0.3)
  cold.position.set(-4, 2, -3)
  scene.add(cold)
  const warm = new THREE.DirectionalLight(0xffd8b0, 0.65)
  warm.position.set(2, 1, -4)
  scene.add(warm)
  // 主光对侧的补光：把主光的"背光半边"补起来，这是消除"一半亮一半暗"的关键一路
  const fill = new THREE.DirectionalLight(0xffe6c8, 0.5)
  fill.position.set(-3, 1.5, 3)
  scene.add(fill)

  // 舞台元素 ①：脚下的暖色光池（比接地阴影更大更淡，营造"打光"感）
  const pool = new THREE.Mesh(
    new THREE.PlaneGeometry(3.6, 3.6),
    new THREE.MeshBasicMaterial({
      map: radialTex('rgba(255,166,104,0.32)', 'rgba(255,138,80,0.11)', 'rgba(255,138,80,0)'),
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

  softPlane = new THREE.Mesh(
    new THREE.PlaneGeometry(1.6, 1.6),
    new THREE.MeshBasicMaterial({
      map: radialTex('rgba(0,0,0,0.5)', 'rgba(0,0,0,0.18)', 'rgba(0,0,0,0)'),
      transparent: true,
      depthWrite: false,
      opacity: 0.7,
    }),
  )
  softPlane.rotation.x = -Math.PI / 2
  softPlane.position.y = 0.002
  scene.add(softPlane)

  // 舞台元素 ③：身后的追光晕（billboard，随相机移动，绕到背面也不穿帮）
  halo = new THREE.Mesh(
    new THREE.PlaneGeometry(5.2, 5.2),
    new THREE.MeshBasicMaterial({
      map: radialTex('rgba(255,146,88,0.24)', 'rgba(255,126,70,0.07)', 'rgba(255,126,70,0)'),
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

  // 拖动 scrub（整圈，不钳制：钳制会在某个方位硬停，见 `azimuth` 的说明）
  // ⚠️ 这是舞台**唯一**会改变方位角的输入。2026-09-21 去掉自动转动后它更关键了：
  //    没有拖动 = 画面完全静止。下面这段映射被门禁（check-3d-model.js 第 9 段）原样抽出来真跑。
  const onDown = (e: PointerEvent) => {
    dragging = true
    dragStartX = e.clientX
    dragStartAzimuth = currentAzimuth()
    renderer!.domElement.setPointerCapture?.(e.pointerId)
  }
  const onMove = (e: PointerEvent) => {
    if (!dragging) return
    // #region 拖动映射
    const dx = e.clientX - dragStartX
    setAzimuth(dragStartAzimuth - (dx / el.clientWidth) * props.azimuthRange)
    // #endregion
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
    const w2 = Math.max(1, Math.round(el.clientWidth))
    const h2 = Math.max(1, Math.round(el.clientHeight))
    renderer.setSize(w2, h2, false)
    camera.aspect = w2 / h2
    camera.updateProjectionMatrix()
    // 尺寸量到 0：画布再也没法正确显示，必须说出来（尺寸恢复后自动收起提示）
    sizeWarn.value = el.clientWidth < 2 || el.clientHeight < 2
    refit()
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

    // #region 每帧方位更新（门禁 check-3d-model.js 第 9 段会原样抽出来真跑）
    // 用户 2026-09-21 要求：**摄像机不要自己缓慢转动**，只在他手动拖动时才动。
    // 所以这段里没有、也不许再有随时间的方位推进（旧写法是 `azimuth += 2π·dt/period`），
    // 也没有任何缓动 / 回弹 —— 方位角只在 pointermove → setAzimuth 里被直接赋值，松手即定住。
    // 保留这个区块（而不是把三行相机代码散在 tick 里）就是为了让门禁有东西可"真跑"：
    // 它证明的是"推进 240 帧后相机位置逐位不变"，比"源码里没有某个字符串"强得多。
    const az = currentAzimuth()
    // 关键：只有水平位置在变，高度 camHeight 与到模型的距离 camRadius 恒定
    camera!.position.set(camRadius * Math.sin(az), camHeight, camRadius * Math.cos(az))
    camera!.lookAt(0, camTargetY, 0)
    // #endregion

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

  // 场景就绪后再拉模型：模型是异步的，到了就再渲染下一帧，不需要额外的"首帧"逻辑
  loadModel()
}

onMounted(() => {
  // 初始化里任何一步抛异常（WebGL 缺失、容器没挂上、取景算不出来）都转成可见报错，
  // 不能像以前那样只丢一条控制台红字、页面上什么都不说。
  try {
    setup()
  } catch (e) {
    fail('3D 舞台初始化失败：' + errText(e))
  }
})

onBeforeUnmount(() => {
  clearWatchdog()
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

    <!-- 硬失败：给明确的说法 + 重试入口，不能只留一片空白 -->
    <div v-else-if="failed" class="pet-stage__fallback" role="status">
      <p class="pet-stage__fallback-title">3D 宠物没能显示出来</p>
      <p class="pet-stage__fallback-msg">{{ failed }}</p>
      <button class="mw-btn mw-btn--brand pet-stage__retry" type="button" @click="reload">
        重新加载
      </button>
    </div>

    <!-- 软警告：模型出来了但可疑（舞台尺寸 0 / 占比过小），说清楚比不说强 -->
    <p v-if="notice && !failed" class="pet-stage__notice" role="status">{{ notice }}</p>
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
    radial-gradient(120% 70% at 50% -10%, rgba(255, 152, 96, 0.12), transparent 60%), var(--mw-night);
}

/* 暗角：把视线收拢到中间的宠物身上。压在画布之上、提示之下 */
.pet-stage::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
  background: radial-gradient(120% 85% at 50% 46%, transparent 52%, rgba(20, 11, 5, 0.4));
}

.pet-stage__veil {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: grid;
  place-items: center;
}

/* 首帧前：呼吸占位，不用"加载中…"纯文字 */
.pet-stage__breath {
  width: 96px;
  height: 96px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 152, 96, 0.22), transparent 70%);
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

/* 失败卡片：盖住画布，说清发生了什么，并给出重试 */
.pet-stage__fallback {
  position: absolute;
  inset: 0;
  z-index: 3;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--mw-space-3);
  padding: var(--mw-space-6);
  text-align: center;
  background: rgba(26, 19, 16, 0.88);
}

.pet-stage__fallback-title {
  font-size: var(--mw-font-h2);
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--mw-on-night);
}

.pet-stage__fallback-msg {
  max-width: 34ch;
  font-size: var(--mw-font-caption);
  line-height: 1.6;
  color: var(--mw-on-night-2);
}

.pet-stage__retry {
  margin-top: var(--mw-space-2);
}

/* 软警告放顶部，不和底部那句"拖动可以左右看看它"打架 */
.pet-stage__notice {
  position: absolute;
  top: var(--mw-space-3);
  left: 50%;
  z-index: 4;
  max-width: calc(100% - var(--mw-space-6));
  transform: translateX(-50%);
  padding: 6px var(--mw-space-3);
  border: 1px solid var(--mw-night-line);
  border-radius: var(--mw-radius-tag);
  background: rgba(26, 19, 16, 0.8);
  font-size: var(--mw-font-micro);
  line-height: 1.5;
  color: var(--mw-on-night);
  text-align: center;
}

/* 键盘可达性：重试按钮在深色底上给可见 focus 环 */
.pet-stage__retry:focus-visible {
  outline: 2px solid var(--mw-brand);
  outline-offset: 2px;
}
</style>