<template>
	<view class="pet-stage">
		<!-- 这个 view 是 renderjs 的宿主：murl 变化时会触发 stage.loadModel -->
		<view id="mw-pet-stage" class="pet-stage__canvas" :murl="modelUrl" :change:murl="stage.loadModel"></view>

		<view v-if="!ready && !errorMsg" class="pet-stage__veil">
			<view class="pet-stage__breath"></view>
		</view>
		<text v-if="errorMsg" class="pet-stage__error">{{ errorMsg }}</text>
		<text v-else class="pet-stage__hint">拖动可以左右看看它</text>
	</view>
</template>

<script>
/**
 * 3D 电子宠物舞台（手机端）
 *
 * 硬需求：绕模型 180° 旋转；模型自身不动；摄像机高度/俯仰/距离恒定，只有水平位置在变。
 * 用户说的"z 轴不变、只改 xy"是 Z-up(Blender) 习惯 → 换算到 Three.js 的 Y-up 就是 y 恒定、水平绕圈。
 *
 * 取景：**自适应**。按 fit + pitchDeg 推导相机距离与高度 → 换任何模型都自动填满画面。
 *
 * 本工程是 HBuilderX + JS，不能 npm 装 three：
 * three r160 的 ESM 构建 + GLTFLoader 已放到 libs/three/（裸导入 'three' 已改写成相对路径）。
 */
export default {
	name: 'PetStage',
	props: {
		/** 模型地址；H5 用 /static/...，App 由 renderjs 内部转成文件系统路径 */
		modelUrl: {
			type: String,
			default: '/static/model/cat/toon_cat_free.glb'
		}
	},
	data() {
		return {
			ready: false,
			errorMsg: ''
		}
	},
	methods: {
		/** renderjs 里准备好后回调，切掉骨架 */
		onStageReady() {
			this.ready = true
		},
		/**
		 * renderjs 里报错后回调。
		 *
		 * 舞台内自己显示一条错误（`pet-stage__error`），同时往父页面抛 `failed`
		 * ——与 Web 端 PetStage.vue 的 `failed` 事件同名同义，首页据此把标注优先级让给舞台错误。
		 */
		onStageError(msg) {
			this.errorMsg = msg || '3D 舞台加载失败'
			this.$emit('failed', this.errorMsg)
		}
	}
}
</script>

<script module="stage" lang="renderjs">
import * as THREE from '../libs/three/three.module.js'
import { GLTFLoader } from '../libs/three/jsm/loaders/GLTFLoader.js'

/** 舞台规格：与 Web 端 MiaoWang-web/src/components/PetStage.vue 必须保持一致 */
const CFG = {
	fit: 0.8,           // 模型占舞台高度的比例（越大越满）
	pitchDeg: 12,       // 俯仰角（度）—— 恒定
	fov: 36,
	period: 24,         // 一个来回的秒数
	modelYaw: 0,        // 静态摆正用，不是旋转动画
	animate: true,      // 播放模型自带待机动画（只动四肢/尾巴，不动根节点）
	tint: 0x35d6a8,
	motes: true
}

/** 生成径向渐变纹理：接地阴影 / 光池 / 身后追光共用 */
function radialTex (inner, mid, outer, size) {
	const c = document.createElement('canvas')
	c.width = c.height = size || 256
	const ctx = c.getContext('2d')
	const g = ctx.createRadialGradient(c.width / 2, c.height / 2, 0, c.width / 2, c.height / 2, c.width / 2)
	g.addColorStop(0, inner)
	g.addColorStop(0.45, mid)
	g.addColorStop(1, outer)
	ctx.fillStyle = g
	ctx.fillRect(0, 0, c.width, c.height)
	const tex = new THREE.CanvasTexture(c)
	tex.colorSpace = THREE.SRGBColorSpace
	return tex
}

/** base64 → ArrayBuffer（App 的 webview 里有 atob） */
function base64ToArrayBuffer (b64) {
	const bin = atob(b64)
	const bytes = new Uint8Array(bin.length)
	for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i)
	return bytes.buffer
}

export default {
	data() {
		return {}
	},
	mounted() {
		this._raf = 0
		this._dragging = false
		this._phase = -Math.PI / 2       // 从 0° 端开始
		this._currentUrl = ''
		// 取景参数：模型加载后按 fit / pitchDeg 推导（加载前给安全初值）
		this._radius = 2
		this._camY = 1
		this._targetY = 0.5
		this._reduce = false
		this.boot()
	},
	beforeDestroy() {
		this.teardown()
	},
	methods: {
		/** App 端把 /static/... 转成真实文件路径（H5 直接可用） */
		resolveUrl(p) {
			// #ifdef APP-PLUS
			try {
				if (typeof plus !== 'undefined' && plus.io) {
					return plus.io.convertLocalFileSystemURL('_www' + p)
				}
			} catch (e) {}
			// #endif
			return p
		},

		/**
		 * 找 renderjs 的宿主元素
		 *
		 * ⚠️ **绝对不要用 `this.$el`**：真机（Android App 基座）上它指向框架内部的节点、而且是 truthy，
		 * 于是既拿不到真正的容器、又跳过了下面的兜底选择器，`appendChild` 直接抛
		 * `HierarchyRequestError: This node type does not support this method`，整个舞台挂掉。
		 * （2026-09-17 真机实测的崩溃原因）
		 */
		findHost() {
			// id 优先（App 真机上最稳），再退到类选择器 —— 但每个候选都必须经过
			// "是元素节点 && 有 appendChild" 的严格校验，绝不放行框架内部的文本节点
			const cands = [
				document.getElementById('mw-pet-stage'),
				document.querySelector('.pet-stage__canvas'),
				document.querySelector('uni-view.pet-stage__canvas')
			]
			for (let i = 0; i < cands.length; i++) {
				const el = cands[i]
				if (el && el.nodeType === 1 && typeof el.appendChild === 'function') return el
			}
			return null
		},

		boot() {
			// renderjs 的 mounted 可能早于视图渲染完成 → 轮询等宿主元素出现，别一开始就放弃
			let tries = 0
			const start = () => {
				const el = this.findHost()
				if (!el) {
					if (++tries < 40) return setTimeout(start, 50)
					return this.reportError('找不到 3D 舞台容器')
				}
				try {
					this.setupScene(el)
				} catch (e) {
					this.reportError('3D 舞台初始化失败：' + ((e && e.message) || e))
				}
			}
			start()
		},

		/** 统一往逻辑层报错（找不到容器 / 初始化失败 / 模型加载失败都走这里，且不重复报同一条） */
		reportError(msg) {
			if (this._errorMsg === msg) return
			this._errorMsg = msg
			if (this.$ownerInstance && this.$ownerInstance.callMethod) {
				this.$ownerInstance.callMethod('onStageError', msg)
			}
		},

		setupScene(el) {
			this.el = el

			this.scene = new THREE.Scene()

			this.camera = new THREE.PerspectiveCamera(CFG.fov, 1, 0.1, 100)
			this.camera.position.set(0, this._camY, this._radius)
			this.camera.lookAt(0, this._targetY, 0)

			this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
			this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
			this.renderer.toneMapping = THREE.ACESFilmicToneMapping
			this.renderer.toneMappingExposure = 1.05
			this.renderer.outputColorSpace = THREE.SRGBColorSpace

			const cvs = this.renderer.domElement
			cvs.style.width = '100%'
			cvs.style.height = '100%'
			cvs.style.display = 'block'
			cvs.style.touchAction = 'none'
			el.appendChild(cvs)

			// 灯光：中性为主 + 冷暖各一路补光（刻意不参考模型颜色，模型换了也好看）
			this.scene.add(new THREE.AmbientLight(0x8fa6b5, 0.55))
			const key = new THREE.DirectionalLight(0xffffff, 2.2)
			key.position.set(3, 5, 3)
			this.scene.add(key)
			const cold = new THREE.DirectionalLight(0x7fb8ff, 0.6)
			cold.position.set(-4, 2, -3)
			this.scene.add(cold)
			const warm = new THREE.DirectionalLight(0xffc98a, 0.5)
			warm.position.set(2, 1, -4)
			this.scene.add(warm)

			// 舞台①：脚下的水青光池（营造"打光"感，比真阴影便宜）
			const pool = new THREE.Mesh(
				new THREE.PlaneGeometry(3.6, 3.6),
				new THREE.MeshBasicMaterial({
					map: radialTex('rgba(53,214,168,0.34)', 'rgba(53,214,168,0.12)', 'rgba(53,214,168,0)'),
					transparent: true,
					depthWrite: false,
					blending: THREE.AdditiveBlending
				})
			)
			pool.rotation.x = -Math.PI / 2
			pool.position.y = 0.001
			this.scene.add(pool)

			// 舞台②：接地阴影（canvas 径向渐变，比 shadowMap 稳，手机更省）
			const soft = new THREE.Mesh(
				new THREE.PlaneGeometry(1.6, 1.6),
				new THREE.MeshBasicMaterial({
					map: radialTex('rgba(0,0,0,0.5)', 'rgba(0,0,0,0.18)', 'rgba(0,0,0,0)'),
					transparent: true,
					depthWrite: false,
					opacity: 0.7
				})
			)
			soft.rotation.x = -Math.PI / 2
			soft.position.y = 0.002
			this.soft = soft
			this.scene.add(soft)

			// 舞台③：身后的追光晕（billboard，随相机走，绕到背面也不穿帮）
			this.halo = new THREE.Mesh(
				new THREE.PlaneGeometry(5.2, 5.2),
				new THREE.MeshBasicMaterial({
					map: radialTex('rgba(53,214,168,0.26)', 'rgba(53,214,168,0.07)', 'rgba(53,214,168,0)'),
					transparent: true,
					depthWrite: false,
					blending: THREE.AdditiveBlending
				})
			)
			this.scene.add(this.halo)

			// 舞台④：慢飘浮尘（手机端减到 40 个，够用且省）
			if (CFG.motes) {
				const COUNT = 40
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
				this.motes = new THREE.Points(
					geo,
					new THREE.PointsMaterial({
						color: new THREE.Color(CFG.tint),
						size: 0.022,
						transparent: true,
						opacity: 0.5,
						depthWrite: false,
						blending: THREE.AdditiveBlending,
						sizeAttenuation: true
					})
				)
				this.scene.add(this.motes)
			}

			// 拖动 scrub（锁死 0~180°）
			const onDown = (e) => {
				this._dragging = true
				this._downX = (e.touches ? e.touches[0].clientX : e.clientX)
				this._downAz = this.currentAzimuth()
			}
			const onMove = (e) => {
				if (!this._dragging) return
				const x = (e.touches ? e.touches[0].clientX : e.clientX)
				const w = this.el.clientWidth || 1
				this.setAzimuth(this._downAz - ((x - this._downX) / w) * Math.PI)
			}
			const onUp = () => { this._dragging = false }
			cvs.addEventListener('touchstart', onDown, { passive: true })
			cvs.addEventListener('touchmove', onMove, { passive: true })
			cvs.addEventListener('touchend', onUp)
			cvs.addEventListener('mousedown', onDown)
			window.addEventListener('mousemove', onMove)
			window.addEventListener('mouseup', onUp)

			this._resize = () => {
				const w = this.el.clientWidth || 1
				const h = this.el.clientHeight || 1
				this.renderer.setSize(w, h, false)
				this.camera.aspect = w / h
				this.camera.updateProjectionMatrix()
			}
			this._resize()
			window.addEventListener('resize', this._resize)

			this.clock = new THREE.Clock()
			this.tick()

			// 加载模型（H5 与 App 路径不同）
			this.loadModelFile(this.resolveUrl('/static/model/cat/toon_cat_free.glb'))
		},

		/** 外部传入新模型地址时热替换 */
		loadModel(newVal) {
			if (!newVal || !this.scene) return
			const url = this.resolveUrl(newVal)
			if (url === this._currentUrl) return
			this.loadModelFile(url)
		},

		loadModelFile(url) {
			this._currentUrl = url
			const loader = new GLTFLoader()

			const onLoad = (gltf) => {
				const root = gltf.scene

				// —— 运行时归一化：先缩放，再按缩放后的实际包围盒居中并落地（模型会换，绝不能写死） ——
				const box = new THREE.Box3().setFromObject(root)
				const size = box.getSize(new THREE.Vector3())
				const maxDim = Math.max(size.x, size.y, size.z) || 1
				root.scale.setScalar(1 / maxDim)

				const box2 = new THREE.Box3().setFromObject(root)
				const ctr = box2.getCenter(new THREE.Vector3())
				const h = Math.max(box2.max.y - box2.min.y, 1e-3)
				const w = Math.max(box2.max.x - box2.min.x, 1e-3)
				const d = Math.max(box2.max.z - box2.min.z, 1e-3)
				root.position.x -= ctr.x
				root.position.z -= ctr.z
				root.position.y -= box2.min.y

				root.rotation.y = CFG.modelYaw // 静态摆正
				root.traverse((o) => {
					if (o.isMesh && o.material) {
						o.material.envMapIntensity = 0.6
						o.material.needsUpdate = true
					}
				})

				// —— 自适应取景：让模型占满 fit 比例的舞台高度 ——
				this._targetY = h * 0.5
				this._radius = h / CFG.fit / (2 * Math.tan(((CFG.fov * Math.PI) / 180) / 2))
				this._camY = this._targetY + Math.tan((CFG.pitchDeg * Math.PI) / 180) * this._radius

				// 接地阴影贴合模型占地尺寸，不是固定大小
				if (this.soft) this.soft.scale.set(w * 2.2, d * 2.2, 1)

				if (this.model) this.scene.remove(this.model)
				this.model = root
				this.scene.add(root)

				if (CFG.animate && gltf.animations && gltf.animations.length) {
					this.mixer = new THREE.AnimationMixer(root)
					gltf.animations.forEach((clip) => this.mixer.clipAction(clip).play())
				}

				if (this.$ownerInstance && this.$ownerInstance.callMethod) {
					this.$ownerInstance.callMethod('onStageReady')
				}
			}

			const onError = (err) => this.reportError('模型加载失败：' + ((err && err.message) || '未知错误'))

			// #ifdef APP-PLUS
			// 真机上 file:// 直链常被 webview 的 XHR 拦掉 → 先用 plus.io 读成 ArrayBuffer 再 parse
			this.readFileAsArrayBuffer(url).then(
				(buf) => loader.parse(buf, '', onLoad, () => this.loadByUrl(loader, url, onLoad, onError)),
				() => this.loadByUrl(loader, url, onLoad, onError)
			)
			return
			// #endif

			// #ifndef APP-PLUS
			this.loadByUrl(loader, url, onLoad, onError)
			// #endif
		},

		loadByUrl(loader, url, onLoad, onError) {
			loader.load(url, onLoad, undefined, onError)
		},

		/**
		 * App 端把工程内文件读成 ArrayBuffer（H5 不会调用它）
		 *
		 * plus.io 明确支持的是 `readAsDataURL`；`readAsArrayBuffer` 在部分基座里没有，
		 * 所以"有就用、没有就退回 dataURL 自己解" —— 不把不确定的 API 当主路径。
		 */
		readFileAsArrayBuffer(url) {
			return new Promise((resolve, reject) => {
				// #ifdef APP-PLUS
				if (typeof plus === 'undefined' || !plus.io) {
					reject(new Error('plus.io 不可用'))
					return
				}
				const path = String(url).replace(/^file:\/\//, '')
				plus.io.resolveLocalFileSystemURL(
					path,
					(entry) => {
						entry.file(
							(file) => {
								const reader = new plus.io.FileReader()
								reader.onloadend = (e) => {
									const res = e.target.result
									if (res instanceof ArrayBuffer) return resolve(res)
									const b64 = String(res).split(',')[1] || ''
									try {
										resolve(base64ToArrayBuffer(b64))
									} catch (err) {
										reject(new Error('模型文件解码失败'))
									}
								}
								reader.onerror = () => reject(new Error('读取模型文件失败'))
								if (typeof reader.readAsArrayBuffer === 'function') reader.readAsArrayBuffer(file)
								else reader.readAsDataURL(file)
							},
							() => reject(new Error('读取模型文件失败'))
						)
					},
					() => reject(new Error('模型文件不存在：' + path))
				)
				// #endif
				// #ifndef APP-PLUS
				reject(new Error('非 App 环境'))
				// #endif
			})
		},

		clamp(v, a, b) { return Math.min(b, Math.max(a, v)) },

		/** 由拖动直接设方位角，并把相位切到离当前相位最近的一支，避免跳变 */
		setAzimuth(a) {
			const az = this.clamp(a, 0, Math.PI)
			const u = this.clamp((az - Math.PI / 2) / (Math.PI / 2), -1, 1)
			const base = Math.asin(u)
			const cands = [base, Math.PI - base, base + 2 * Math.PI, Math.PI - base + 2 * Math.PI]
			let best = cands[0]
			for (const c of cands) if (Math.abs(c - this._phase) < Math.abs(best - this._phase)) best = c
			this._phase = best
		},

		currentAzimuth() {
			return Math.PI / 2 + (Math.PI / 2) * Math.sin(this._phase)
		},

		tick() {
			this._raf = requestAnimationFrame(() => this.tick())
			const dt = Math.min(this.clock.getDelta(), 0.1)
			if (!this._dragging && !this._reduce) this._phase += (Math.PI * 2 * dt) / CFG.period

			const az = this.currentAzimuth()
			// 关键：只有水平位置在变，高度 _camY 与到模型的距离 _radius 恒定
			this.camera.position.set(
				this._radius * Math.sin(az),
				this._camY,
				this._radius * Math.cos(az)
			)
			this.camera.lookAt(0, this._targetY, 0)

			// 追光晕始终待在模型"背面"（相机方向反侧）并正对相机
			if (this.halo) {
				const dir = this.camera.position.clone().normalize()
				this.halo.position.copy(dir).multiplyScalar(-1.7)
				this.halo.position.y = this._targetY + 0.15
				this.halo.lookAt(this.camera.position)
			}

			// 浮尘缓慢上飘 + 越界回绕
			if (this.motes) {
				const arr = this.motes.geometry.attributes.position
				for (let i = 0; i < arr.count; i++) {
					let y = arr.getY(i) + dt * 0.045
					if (y > 2.1) y = 0
					arr.setY(i, y)
				}
				arr.needsUpdate = true
			}

			if (this.mixer) this.mixer.update(dt)
			this.renderer.render(this.scene, this.camera)
		},

		teardown() {
			cancelAnimationFrame(this._raf)
			if (this._resize) window.removeEventListener('resize', this._resize)
			if (this.scene) {
				this.scene.traverse((o) => {
					if (o.geometry) o.geometry.dispose()
					if (o.material) {
						if (o.material.map) o.material.map.dispose()
						o.material.dispose()
					}
				})
			}
			if (this.mixer) this.mixer.stopAllAction()
			if (this.renderer) {
				this.renderer.dispose()
				if (this.renderer.domElement && this.renderer.domElement.parentNode) {
					this.renderer.domElement.parentNode.removeChild(this.renderer.domElement)
				}
			}
			this.scene = null
			this.renderer = null
			this.camera = null
			this.model = null
			this.mixer = null
			this.halo = null
			this.motes = null
		}
	}
}
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.pet-stage {
	position: relative;
	width: 100%;
	height: 840rpx;
	overflow: hidden;
	/* 舞台底色 + 顶部极淡辉光：这是全站唯一允许用渐变的地方 */
	background:
		radial-gradient(120% 70% at 50% -10%, rgba(53, 214, 168, 0.1), transparent 60%),
		$mw-night;
}

.pet-stage__canvas {
	position: absolute;
	inset: 0;
	width: 100%;
	height: 100%;
}

/* 暗角：把视线收拢到宠物身上 */
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
	display: flex;
	align-items: center;
	justify-content: center;
}

/* 首帧前：呼吸占位，不用"加载中…"纯文字 */
.pet-stage__breath {
	width: 180rpx;
	height: 180rpx;
	border-radius: 50%;
	background: radial-gradient(circle, rgba(53, 214, 168, 0.22), transparent 70%);
	animation: pet-breath 2.2s ease-in-out infinite;
}

@keyframes pet-breath {
	0%, 100% { transform: scale(0.86); opacity: 0.5; }
	50% { transform: scale(1.06); opacity: 1; }
}

.pet-stage__hint,
.pet-stage__error {
	position: absolute;
	left: 0;
	right: 0;
	bottom: 24rpx;
	text-align: center;
	font-size: $mw-font-micro;
	color: $mw-on-night-2;
}
.pet-stage__error {
	color: $mw-danger;
	bottom: 50%;
}
</style>