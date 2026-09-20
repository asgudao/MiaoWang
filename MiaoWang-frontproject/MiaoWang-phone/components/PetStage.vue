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
 * 硬需求：绕模型 360° 旋转（一整圈，匀速连续循环，首尾无缝）；模型自身不动；
 * 摄像机高度/俯仰/距离恒定，只有水平位置在变。
 * 用户说的"z 轴不变、只改 xy"是 Z-up(Blender) 习惯 → 换算到 Three.js 的 Y-up 就是 y 恒定、水平绕圈。
 *
 * **自动转动 → 仅手动拖动（2026-09-21，用户明确要求"摄像机不要自己缓慢转动"）**：
 * 改前每帧 `_azimuth += 2π·dt/CFG.period`（period = 24s）匀速自转一整圈，拖动时暂停、松手继续。
 * 改后**没有任何随时间的方位推进**：方位角只在 touchmove → setAzimuth 时被**直接赋值**，
 * 松手那一刻就定住 —— 不自转、不回弹、也不"缓缓漂回初始方位"。
 * 用户禁的是"自己缓慢转动"，**手动拖动是舞台的核心交互，必须保留**。
 * 自转去掉后一起删掉的死代码：`CFG.period`（唯一用途是自转周期）与 `this._reduce`
 * （唯一作用是"关掉自转"）。**不动的部分**：`CFG.azimuthRange`（= 2π）仍同时管拖动灵敏度
 * 与取景采样范围（见下面的 AZ_FIT），不是死常量。
 * 半圈（180°）→ 整圈（360°）是更早一步的改动，留下的结论是"**不钳制**"（整圈没有端点，
 * 钳制会在钳到的那一刻硬停；sin/cos 以 2π 为周期 → 跨圈天然无缝）。
 * ⚠️ 取景采样范围必须与 CFG.azimuthRange 同步，见下面的 AZ_FIT / AZ_CHECK。
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
	fit: 0.8,           // 模型在任意方位最多占画框的比例（越大越满）
	pitchDeg: 12,       // 俯仰角（度）—— 恒定
	fov: 36,
	azimuthRange: Math.PI * 2, // 转台扫过的方位范围：整圈 360°（取景采样范围必须与它相同）
	// ⚠️ 这里**没有** period：2026-09-21 去掉自动转动后，自转周期没有任何消费者了（见文件头）。
	// 拖动灵敏度由 azimuthRange 决定（拖满一个画布宽 = 转过整个区间）。
	modelYaw: 0,        // 静态摆正用，不是旋转动画
	animate: true,      // 播放模型自带待机动画（只动四肢/尾巴，不动根节点）
	tint: 0xffc98a,
	motes: true,
	// 工程内默认模型路径（renderjs 拿不到 props，:change:murl 若先到就用 _pendingUrl 覆盖它）
	modelPath: '/static/model/cat/toon_cat_free.glb'
}

// #region 取景数学（与 Web 端 MiaoWang-web/src/components/petStageMath.ts 同源；改一端必须同时改另一端）
/**
 * 这一段是 2026-09-20 的定点修复。原来的 `new THREE.Box3().setFromObject(root)` 量蒙皮模型时，
 * 会量出比**真实渲染**大 100 倍的盒子（SkinnedMesh 的网格节点链被算了两次：glTF 规定蒙皮网格自身的
 * 节点变换必须被忽略，渲染时被 bindMatrixInverse 抵消，但 Box3 内部只 updateWorldMatrix、
 * 不刷新 bindMatrixInverse），于是 `root.scale.setScalar(1 / maxDim)` 把模型缩成几个像素
 * —— 页面上不报错、不警告、没有文案，用户只看到"猫不见了"。
 *
 * 两件事缺一不可：
 *   ① 量之前 `root.updateMatrixWorld(true)`（刷新 bindMatrixInverse）
 *   ② 逐顶点量（getVertexPosition，走的才是着色器里那套骨骼变换）
 * 门禁 `MiaoWang-frontproject/tools/check-3d-model.js` 会把本区块抽出来，
 * 用**真 three + 真 GLB** 离线复算，并要求手机端与 Web 端给出**同一个取景结果**。
 */
const DEG = Math.PI / 180
// ⚠️ 这两个数必须随 CFG.azimuthRange 一起变：半径取的是"所有采样方位的最大值"，
// 采样只覆盖半圈时，转台转到另外半圈就没人保证不切边了。
// 转台从半圈改成整圈 → 段数翻倍，步长仍是 7.5°/段（48 段 = 49 个方位，含 0° 与 360°）。
const AZ_FIT = 48 // 求取景半径时的方位取样数（0~360°，含两端）
const AZ_CHECK = 360 // 复核占比时的方位取样数（更密，1°/段，与改前半圈分辨率一致）

/** 相机基：n = 视线方向（相机指向注视点），up / right 与它正交 */
function mwBasis (nx, ny, nz) {
	const n = new THREE.Vector3(nx, ny, nz).normalize()
	const up = new THREE.Vector3(0, 1, 0).addScaledVector(n, -n.y).normalize()
	const right = new THREE.Vector3().crossVectors(n, up).normalize()
	return { n: n, up: up, right: right }
}

/** 按方位角 + 俯仰角推相机基（相机在方位 az、俯仰 pitch 方向上，视线指向注视点） */
function mwBasisFromAzimuth (az, pitch) {
	const cp = Math.cos(pitch)
	return mwBasis(-Math.sin(az) * cp, -Math.sin(pitch), -Math.cos(az) * cp)
}

/** 逐顶点量"真实渲染出来"的包围盒（与着色器同一套变换） */
function mwMeasureBox (root) {
	root.updateMatrixWorld(true)
	const box = new THREE.Box3()
	const v = new THREE.Vector3()
	root.traverse((o) => {
		if (!o.isMesh || !o.geometry) return
		const pos = o.geometry.getAttribute('position')
		if (!pos) return
		for (let i = 0; i < pos.count; i++) {
			o.getVertexPosition(i, v).applyMatrix4(o.matrixWorld)
			box.expandByPoint(v)
		}
	})
	return box
}

/** 逐顶点收集世界坐标（归一化与落地之后调用，量到的就是最终姿态） */
function mwCollectPoints (root) {
	root.updateMatrixWorld(true)
	const meshes = []
	let total = 0
	root.traverse((o) => {
		if (!o.isMesh || !o.geometry) return
		const pos = o.geometry.getAttribute('position')
		if (!pos) return
		meshes.push(o)
		total += pos.count
	})
	const xyz = new Float32Array(total * 3)
	const v = new THREE.Vector3()
	let k = 0
	for (let m = 0; m < meshes.length; m++) {
		const pos = meshes[m].geometry.getAttribute('position')
		for (let i = 0; i < pos.count; i++) {
			meshes[m].getVertexPosition(i, v).applyMatrix4(meshes[m].matrixWorld)
			xyz[k++] = v.x
			xyz[k++] = v.y
			xyz[k++] = v.z
		}
	}
	return { xyz: xyz, count: total }
}

/**
 * 把整个模型框进画框所需的相机半径（水平距离）。
 * 判据：顶点在画框内 ⇔ |d·up| ≤ (L + d·n)·tanV·fit 且 |d·right| ≤ (L + d·n)·tanH·fit
 * （d = 顶点−注视点、L = 相机到注视点的三维距离）→ 每个顶点给出一个 L 下限，取所有顶点与方位的最大值。
 */
function mwFitRadius (points, camTargetY, opt) {
	const pitch = opt.pitchDeg * DEG
	const tanV = Math.tan((opt.fov * DEG) / 2) * opt.fit
	const tanH = tanV * opt.aspect
	const xyz = points.xyz
	let maxL = 0
	for (let s = 0; s <= AZ_FIT; s++) {
		const b = mwBasisFromAzimuth((opt.azimuthRange * s) / AZ_FIT, pitch)
		let need = 0
		for (let i = 0; i < points.count; i++) {
			const dx = xyz[i * 3]
			const dy = xyz[i * 3 + 1] - camTargetY
			const dz = xyz[i * 3 + 2]
			const du = Math.abs(dx * b.up.x + dy * b.up.y + dz * b.up.z)
			const dr = Math.abs(dx * b.right.x + dy * b.right.y + dz * b.right.z)
			const along = dx * b.n.x + dy * b.n.y + dz * b.n.z
			const L = Math.max(du / tanV, dr / tanH) - along
			if (L > need) need = L
		}
		if (need > maxL) maxL = need
	}
	if (!isFinite(maxL) || maxL <= 0) return 0
	return maxL * Math.cos(pitch)
}

/** 复核：模型在画框里的占比（1.0 = 正好贴到画框边，> 1 = 有切边） */
function mwProjectedExtent (points, camTargetY, camRadius, camHeight, opt) {
	const tanV = Math.tan((opt.fov * DEG) / 2)
	const tanH = tanV * opt.aspect
	const xyz = points.xyz
	let worst = 0
	for (let s = 0; s <= AZ_CHECK; s++) {
		const az = (opt.azimuthRange * s) / AZ_CHECK
		const camX = camRadius * Math.sin(az)
		const camZ = camRadius * Math.cos(az)
		const b = mwBasis(-camX, camTargetY - camHeight, -camZ)
		for (let i = 0; i < points.count; i++) {
			const ex = xyz[i * 3] - camX
			const ey = xyz[i * 3 + 1] - camHeight
			const ez = xyz[i * 3 + 2] - camZ
			const depth = ex * b.n.x + ey * b.n.y + ez * b.n.z
			if (depth <= 1e-6) return 99 // 顶点跑到相机后面了 = 严重切边
			const ndcY = Math.abs(ex * b.up.x + ey * b.up.y + ez * b.up.z) / (depth * tanV)
			const ndcX = Math.abs(ex * b.right.x + ey * b.right.y + ez * b.right.z) / (depth * tanH)
			if (ndcX > worst) worst = ndcX
			if (ndcY > worst) worst = ndcY
		}
	}
	return worst
}

/** 取景参数（宽高比现算；量不到就当 1，resize 时会重算） */
function mwFitOptions (el, cfg) {
	const w = (el && el.clientWidth) || 1
	const h = (el && el.clientHeight) || 1
	return {
		fit: cfg.fit,
		pitchDeg: cfg.pitchDeg,
		fov: cfg.fov,
		azimuthRange: cfg.azimuthRange,
		aspect: w / h
	}
}

/** 归一化 + 取景：真实尺寸 → 最长边 1.0 → XZ 居中 / 底部落地 → 半径 + 相机高度 + 占比 */
function mwFrame (root, opt) {
	const box = mwMeasureBox(root)
	const size = box.getSize(new THREE.Vector3())
	const maxDim = Math.max(size.x, size.y, size.z)
	if (!isFinite(maxDim) || maxDim <= 0) throw new Error('模型最长边量到 0')
	root.scale.setScalar(1 / maxDim)
	// 缩放后重新量：中心与落地高度要按缩放后的真实姿态取
	const scaled = mwMeasureBox(root)
	const ctr = scaled.getCenter(new THREE.Vector3())
	root.position.set(-ctr.x, -scaled.min.y, -ctr.z)
	const points = mwCollectPoints(root)
	let minX = Infinity
	let minY = Infinity
	let minZ = Infinity
	let maxX = -Infinity
	let maxY = -Infinity
	let maxZ = -Infinity
	for (let i = 0; i < points.count; i++) {
		const x = points.xyz[i * 3]
		const y = points.xyz[i * 3 + 1]
		const z = points.xyz[i * 3 + 2]
		if (x < minX) minX = x
		if (y < minY) minY = y
		if (z < minZ) minZ = z
		if (x > maxX) maxX = x
		if (y > maxY) maxY = y
		if (z > maxZ) maxZ = z
	}
	const camTargetY = (maxY - minY) * 0.5
	const camRadius = mwFitRadius(points, camTargetY, opt)
	if (camRadius <= 0) throw new Error('取景半径算不出来')
	const camHeight = camTargetY + Math.tan(opt.pitchDeg * DEG) * camRadius
	return {
		maxDim: maxDim,
		scale: 1 / maxDim,
		width: maxX - minX,
		height: maxY - minY,
		depth: maxZ - minZ,
		camTargetY: camTargetY,
		camRadius: camRadius,
		camHeight: camHeight,
		coverage: mwProjectedExtent(points, camTargetY, camRadius, camHeight, opt),
		points: points
	}
}
// #endregion

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
		this._azimuth = 0              // 方位角（弧度）：0 = 正前方，与改前 180° 版本第一帧一致
		this._currentUrl = ''
		this._pendingUrl = ''            // :change:murl 早于场景就绪时先记下
		this._tried = []                 // 加载失败时用来告诉用户"试过哪些路径"
		this._disposed = false
		// 取景参数：模型加载后按 fit / pitchDeg 推导（加载前给安全初值）
		this._frame = null
		this._radius = 2
		this._camY = 1
		this._targetY = 0.5
		this.boot()
	},
	beforeDestroy() {
		this.teardown()
	},
	methods: {
		/**
		 * App 端读工程内文件的**候选地址**，按"最可能成功"排序
		 *
		 * HTML5+ 把路径分成两类：相对路径 URL（以 `_` 开头，如 `_www/static/a.glb`）
		 * 与本地绝对路径 URL（以 `file://` 开头）。`plus.io.resolveLocalFileSystemURL`
		 * **只承诺支持这两类**（官方示例就是 `resolveLocalFileSystemURL("_www/test.html", ...)`）。
		 *
		 * 改前传给它的是 `convertLocalFileSystemURL('_www' + p)` 的返回值 —— 按官方文档那是
		 * **平台绝对路径**（"也可以在前面添加 file:// 后在 html 页面中直接使用"，即返回串不带协议），
		 * 所以代码里 `replace(/^file:\/\//, '')` 是空操作，等于把一个既不是相对路径 URL、
		 * 也不是本地绝对路径 URL 的裸路径喂进去 —— 落在文档承诺之外。
		 */
		candidateUrls(p) {
			const list = ['_www' + p]
			try {
				if (typeof plus !== 'undefined' && plus.io) {
					const abs = plus.io.convertLocalFileSystemURL('_www' + p)
					if (abs) list.push('file://' + String(abs).replace(/^file:\/\//, ''))
				}
			} catch (e) {}
			return list
		},

		/** 等 5+ API 注入：renderjs 的 mounted 常早于 plusready，那一刻 plus 还是 undefined */
		waitPlus(timeout) {
			return new Promise((resolve) => {
				// #ifdef APP-PLUS
				if (typeof plus !== 'undefined' && plus.io) {
					resolve(true)
					return
				}
				const t0 = Date.now()
				const poll = () => {
					if (typeof plus !== 'undefined' && plus.io) {
						resolve(true)
						return
					}
					if (Date.now() - t0 > (timeout || 6000)) {
						resolve(false)
						return
					}
					setTimeout(poll, 50)
				}
				poll()
				// #endif
				// #ifndef APP-PLUS
				resolve(false)
				// #endif
			})
		},

		/** GLB 头校验：挡住"读成乱码 / 半截"这种静默失败（前 4 字节必须是 glTF） */
		isGlb(buf) {
			if (!buf || buf.byteLength < 12) return false
			const b = new Uint8Array(buf, 0, 4)
			return b[0] === 0x67 && b[1] === 0x6c && b[2] === 0x54 && b[3] === 0x46
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
			this.renderer.toneMappingExposure = 1.0
			this.renderer.outputColorSpace = THREE.SRGBColorSpace

			const cvs = this.renderer.domElement
			cvs.style.width = '100%'
			cvs.style.height = '100%'
			cvs.style.display = 'block'
			cvs.style.touchAction = 'none'
			el.appendChild(cvs)

			// 灯光：**均匀照明**（v3.1，与 Web 端逐值一致）。用户反馈"有明有暗 / 太暗"：
			// 改前"强主光 2.3 + 弱环境光 0.42"的单侧布光会让朝向主光那半边亮、背面掉进黑里
			// （离线复算最暗/最亮 0.11、左右半屏亮度比 0.74，见 tools/check-3d-model.js 第 ⑩ 段）。
			// 现在：高环境底 + 天光（只分上下、无左右偏向）+ 多向低强度补光；
			// 主光压到与补光同量级，冷调一路只留 0.3 做轮廓分离。
			// ⚠️ 曝光保持 1.0：光池 / 追光晕 / 浮尘是 MeshBasicMaterial，不受灯光影响但受曝光影响。
			this.scene.add(new THREE.AmbientLight(0xe0c3a6, 0.85))
			this.scene.add(new THREE.HemisphereLight(0xfff0e0, 0xcda98a, 0.75))
			const key = new THREE.DirectionalLight(0xfff6ec, 0.85)
			key.position.set(3, 5, 3)
			this.scene.add(key)
			const cold = new THREE.DirectionalLight(0xa8c0d8, 0.3)
			cold.position.set(-4, 2, -3)
			this.scene.add(cold)
			const warm = new THREE.DirectionalLight(0xffd8b0, 0.65)
			warm.position.set(2, 1, -4)
			this.scene.add(warm)
			// 主光对侧的补光：把主光的"背光半边"补起来，这是消除"一半亮一半暗"的关键一路
			const fill = new THREE.DirectionalLight(0xffe6c8, 0.5)
			fill.position.set(-3, 1.5, 3)
			this.scene.add(fill)

			// 舞台①：脚下的暖色光池（营造"打光"感，比真阴影便宜）
			const pool = new THREE.Mesh(
				new THREE.PlaneGeometry(3.6, 3.6),
				new THREE.MeshBasicMaterial({
					map: radialTex('rgba(255,166,104,0.32)', 'rgba(255,138,80,0.11)', 'rgba(255,138,80,0)'),
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
					map: radialTex('rgba(255,146,88,0.24)', 'rgba(255,126,70,0.07)', 'rgba(255,126,70,0)'),
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

			// 拖动 scrub（整圈，不钳制：整圈没有端点，钳制会在钳到的那一刻硬停）
			// ⚠️ 这是舞台**唯一**会改变方位角的输入。2026-09-21 去掉自动转动后它更关键了：
			//    没有拖动 = 画面完全静止。下面这段映射被门禁（check-3d-model.js 第 9 段）原样抽出来真跑。
			const onDown = (e) => {
				this._dragging = true
				this._downX = (e.touches ? e.touches[0].clientX : e.clientX)
				this._downAz = this.currentAzimuth()
			}
			const onMove = (e) => {
				if (!this._dragging) return
				// #region 拖动映射
				const x = (e.touches ? e.touches[0].clientX : e.clientX)
				const w = this.el.clientWidth || 1
				this.setAzimuth(this._downAz - ((x - this._downX) / w) * CFG.azimuthRange)
				// #endregion
			}
			const onUp = () => { this._dragging = false }
			cvs.addEventListener('touchstart', onDown, { passive: true })
			cvs.addEventListener('touchmove', onMove, { passive: true })
			cvs.addEventListener('touchend', onUp)
			cvs.addEventListener('mousedown', onDown)
			window.addEventListener('mousemove', onMove)
			window.addEventListener('mouseup', onUp)

			this._resize = () => {
				const w = this.el.clientWidth || 0
				const h = this.el.clientHeight || 0
				// 量到 0 尺寸时**不能**退回 1：那会让 aspect 变成 w/1（横纵比被拉成几百比一），
				// 画布被压成一条线，看起来又糊又暗，而且不会自己恢复。
				// renderjs 的 mounted 常早于布局完成，所以这里直接跳过，交给下一次调用重算。
				if (w < 2 || h < 2) return
				this.renderer.setSize(w, h, false)
				this.camera.aspect = w / h
				this.camera.updateProjectionMatrix()
				// 宽高比变了要重算取景半径，否则窄窗口 / 横屏会切边
				if (this._frame) {
					const r = mwFitRadius(this._frame.points, this._frame.camTargetY, mwFitOptions(this.el, CFG))
					if (r > 0) {
						this._radius = r
						this._camY = this._frame.camTargetY + Math.tan((CFG.pitchDeg * Math.PI) / 180) * r
					}
				}
			}
			this._resize()
			window.addEventListener('resize', this._resize)
			// 兜底补量一次：首次量到 0 尺寸时上面会跳过，这里等布局落定再量
			// （真机上没有 ResizeObserver，不加这一下就会一直停在错误尺寸上）
			setTimeout(this._resize, 120)

			this.clock = new THREE.Clock()
			this.tick()

			// 加载模型：默认用工程内模型；`:change:murl` 若已经先到就用它（renderjs 拿不到 props）
			this.loadModelFile(this._pendingUrl || CFG.modelPath)
		},

		/**
		 * 外部传入新模型地址时热替换
		 *
		 * ⚠️ `:change:murl` 可能早于 `mounted` 里的 `boot()` 触发 —— 那一刻 `this.scene` 还不存在，
		 * 改前直接 `return` 等于把外部传进来的模型**静默丢掉**（画面里永远只有内置那只）。
		 * 现在先记进 `_pendingUrl`，由 setupScene 就绪时取用。
		 */
		loadModel(newVal) {
			if (!newVal) return
			if (!this.scene) {
				this._pendingUrl = newVal
				return
			}
			if (newVal === this._currentUrl) return
			this.loadModelFile(newVal)
		},

		loadModelFile(p) {
			const path = p || CFG.modelPath
			this._currentUrl = path
			this._tried = []
			const loader = new GLTFLoader()

			const onLoad = (gltf) => {
				if (this._disposed || !this.scene) return
				const root = gltf.scene

				// 先摆正静态朝向，再量尺寸：量的就是最终姿态
				root.rotation.y = CFG.modelYaw

				// —— 归一化 + 自适应取景（数学在文件顶部的"取景数学"区，与 Web 端同源） ——
				let frame
				try {
					frame = mwFrame(root, mwFitOptions(this.el, CFG))
				} catch (e) {
					this.reportError('模型尺寸量不出来，没法取景：' + ((e && e.message) || '未知错误'))
					return
				}
				this._frame = frame
				this._targetY = frame.camTargetY
				this._radius = frame.camRadius
				this._camY = frame.camHeight
				root.traverse((o) => {
					if (o.isMesh && o.material) {
						o.material.envMapIntensity = 0.6
						o.material.needsUpdate = true
					}
				})

				// "加载成功但看不见"是这台设备上最难受的失败：给可见报错，不留假死界面
				if (frame.coverage < 0.15) {
					this.reportError(
						'模型加载出来了，但在画面里只占 ' + Math.round(frame.coverage * 100) + '%，几乎看不见'
					)
				}

				// 接地阴影贴合模型占地尺寸，不是固定大小
				if (this.soft) this.soft.scale.set(frame.width * 2.2, frame.depth * 2.2, 1)

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

			// 远端地址（OSS / 后端下发）直接按 URL 取，不走"工程内文件"那条路
			if (/^https?:\/\//i.test(path)) {
				this.loadByUrl(loader, path, onLoad, onError)
				return
			}

			// #ifdef APP-PLUS
			this.loadAppModel(loader, path, onLoad, onError)
			return
			// #endif

			// #ifndef APP-PLUS
			this.loadByUrl(loader, path, onLoad, onError)
			// #endif
		},

		loadByUrl(loader, url, onLoad, onError) {
			loader.load(url, onLoad, undefined, onError)
		},

		/**
		 * App 端加载工程内模型：**按候选地址逐个试，全失败才报错**
		 *
		 * 为什么这么啰嗦：真机上"读工程内文件"整条链每一环都可能静默失败
		 * （plus 还没注入 / 路径形态不在文档承诺内 / 基座没有某个 FileReader 方法），
		 * 而任何一环卡住，界面就只剩呼吸动画 —— 用户只看到"模型没出来"，看不出错在哪。
		 * 所以：① 先等 plus 就绪；② 每个地址都带 GLB 头校验；
		 * ③ 12 秒没结果一律报错（宁可显示错误，也不留一个假死界面）。
		 */
		loadAppModel(loader, path, onLoad, onError) {
			const cands = this.candidateUrls(path)
			let i = 0
			let done = false
			let timer = null

			const fail = (why) => {
				if (done) return
				done = true
				clearTimeout(timer)
				// 真机没有 devtools：控制台留全量，界面上只留一句人能读的
				console.error('[mw] 3D 模型加载失败：' + why + '；试过的地址 = ', this._tried)
				this.reportError(
					'3D 模型没加载出来：读不到 ' + path + '（已试 ' + this._tried.length + ' 个地址，' + why + '）'
				)
			}
			// 看门狗按"每一次尝试"重置，不是全程共用一个 12 秒：
			// 否则解析慢一点就会误判超时，把本来要成功的模型丢掉（宁可多试，不可错杀）
			const arm = () => {
				clearTimeout(timer)
				timer = setTimeout(() => fail('等待读取超时'), 12000)
			}
			arm()

			const next = () => {
				if (done || this._disposed) return
				if (i < cands.length) {
					arm()
					const url = cands[i++]
					this._tried.push(url)
					this.readLocalFile(url).then(
						(buf) => {
							if (done) return
							// 读到了但不是 GLB → 这个地址是错的，换下一个
							if (!this.isGlb(buf)) return next()
							// 字节已经到手：后面的解析不该再受"读取超时"管
							clearTimeout(timer)
							loader.parse(
								buf,
								'',
								(gltf) => {
									if (done) return
									done = true
									onLoad(gltf)
								},
								() => next()
							)
						},
						() => next()
					)
					return
				}
				// 最后一招：让 WebView 自己按 URL 取（部分基座允许 file:// 或页面相对路径）
				const xhr = this.fallbackUrls(path)
				let j = 0
				const tryXhr = () => {
					if (done || this._disposed || j >= xhr.length) {
						fail(typeof plus === 'undefined' ? 'plus 未就绪' : 'File API 与 XHR 都读不到')
						return
					}
					arm()
					const u = xhr[j++]
					this._tried.push(u)
					this.loadByUrl(
						loader,
						u,
						(gltf) => {
							if (done) return
							done = true
							clearTimeout(timer)
							onLoad(gltf)
						},
						() => tryXhr()
					)
				}
				tryXhr()
			}

			this.waitPlus(6000).then(next, next)
		},

		/** 最后一招的候选：file:// 绝对地址、相对页面基址的路径、原始工程路径 */
		fallbackUrls(p) {
			const list = []
			try {
				if (typeof plus !== 'undefined' && plus.io) {
					const abs = plus.io.convertLocalFileSystemURL('_www' + p)
					if (abs) list.push('file://' + String(abs).replace(/^file:\/\//, ''))
				}
			} catch (e) {}
			list.push(String(p).replace(/^\//, ''))
			list.push(p)
			return list
		},

		/**
		 * App 端把工程内文件读成 ArrayBuffer（H5 不会调用它）
		 *
		 * ⚠️ 改前的顺序是"有 readAsArrayBuffer 就用它"。但 HTML5+ 的 FileReader **只文档化了
		 * `readAsDataURL` / `readAsText`**（连 `result` 的文档类型都是 String），
		 * `readAsArrayBuffer` 属于未文档化能力：它存在、回调却不触发时，Promise 永不 settle → 静默卡死。
		 * 所以现在**先走文档化的 readAsDataURL**（再自己 base64 解码），它缺了才退。
		 */
		readLocalFile(url) {
			return new Promise((resolve, reject) => {
				// #ifdef APP-PLUS
				if (typeof plus === 'undefined' || !plus.io) {
					reject(new Error('plus.io 不可用'))
					return
				}
				plus.io.resolveLocalFileSystemURL(
					url,
					(entry) => {
						entry.file(
							(file) => {
								const reader = new plus.io.FileReader()
								reader.onloadend = (e) => {
									const res = e.target ? e.target.result : reader.result
									let buf = null
									if (res instanceof ArrayBuffer) {
										buf = res
									} else {
										const b64 = String(res || '').split(',')[1] || ''
										try {
											buf = base64ToArrayBuffer(b64)
										} catch (err) {
											buf = null
										}
									}
									if (!this.isGlb(buf)) {
										reject(new Error('读到的不是有效 GLB'))
										return
									}
									resolve(buf)
								}
								reader.onerror = () => reject(new Error('读取文件失败'))
								if (typeof reader.readAsDataURL === 'function') reader.readAsDataURL(file)
								else if (typeof reader.readAsArrayBuffer === 'function') reader.readAsArrayBuffer(file)
								else reject(new Error('FileReader 不支持读取'))
							},
							() => reject(new Error('取不到文件对象'))
						)
					},
					() => reject(new Error('路径不可解析'))
				)
				// #endif
				// #ifndef APP-PLUS
				reject(new Error('非 App 环境'))
				// #endif
			})
		},

		clamp(v, a, b) { return Math.min(b, Math.max(a, v)) },

		/**
		 * 拖动直接设方位角。**不钳制**：整圈没有端点，钳制到 [0, 2π] 只会在钳到的那一刻硬停
		 * （可见跳变）；而 sin/cos 以 2π 为周期，方位角取任意实数都是连续、无缝的。
		 */
		setAzimuth(a) {
			this._azimuth = a
		},

		currentAzimuth() {
			return this._azimuth
		},

		tick() {
			this._raf = requestAnimationFrame(() => this.tick())
			const dt = Math.min(this.clock.getDelta(), 0.1)

			// #region 每帧方位更新（门禁 check-3d-model.js 第 9 段会原样抽出来真跑）
			// 用户 2026-09-21 要求：**摄像机不要自己缓慢转动**，只在他手动拖动时才动。
			// 所以这里没有、也不许再有随时间的方位推进（旧写法是 `_azimuth += 2π·dt/CFG.period`），
			// 也没有任何缓动 / 回弹 —— 方位角只在 touchmove → setAzimuth 里被直接赋值，松手即定住。
			// 保留这个区块（而不是把三行相机代码散在 tick 里）就是为了让门禁有东西可"真跑"。
			const az = this.currentAzimuth()
			// 关键：只有水平位置在变，高度 _camY 与到模型的距离 _radius 恒定
			this.camera.position.set(
				this._radius * Math.sin(az),
				this._camY,
				this._radius * Math.cos(az)
			)
			this.camera.lookAt(0, this._targetY, 0)
			// #endregion

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
			// 加载是异步的：标记已销毁，避免迟到的回调往已拆掉的场景里 add
			this._disposed = true
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
	/* 舞台底色 + 顶部极淡辉光：这是全站唯一允许用渐变的地方（左右对称，没有单边亮块） */
	/* ⚠️ 2026-09-21 用户要求「手机端的 3D 背景改成和 Web 一样」→ 这一处现在与 Web 端**逐位一致**：
	      · 底色 = $mw-night（Web 端写的是 var(--mw-night)，同一个语义令牌，两端都解析成 #1a1310）
	      · 辉光 = rgba(255, 152, 96, 0.12)：**照抄 Web 端 PetStage.vue 的字面量**
	        （Web 那一处自己就是写死的；两端令牌表里都没有与它同值的令牌，
	         所以按"网页端本身写死同一值"的例外照抄，不新增令牌、也不另改一个色）
	   改前那套是"抬一档"：底色 #231a14 + 辉光 rgba(255, 160, 104, 0.16)。它是给竖屏取景做的补偿
	   （手机画布 750×840rpx = 0.893，取景算法把相机推远 15.7%，按世界尺寸画的光池/追光晕在屏幕上
	   少 25% 面积）。用户已明确要求取消这档差异，故删除。
	   代价（如实记录，不粉饰）：舞台底相对亮度 0.0115 → 0.0079（显示灰度 10.8% → 8.0%，暗 46%），
	   猫与底色的对比度 6.80:1 → 6.86:1（分离更清楚，但背景确实更暗）。
	   ⚠️ 这一改动与 .agents/skills/miaowang-design-system/SKILL.md §5.7「手机端舞台底抬一档、
	   不要以统一为名改回去」冲突，本次以用户指示为准；技能文本未改（Agent 不得改 .agents/skills/**）。
	   ⚠️ 观感（到底"看起来一样"没有）无浏览器 / 无真机，**未验证**；可静态证明的只有值/令牌一致。 */
	background:
		radial-gradient(120% 70% at 50% -10%, rgba(255, 152, 96, 0.12), transparent 60%),
		$mw-night;
}

.pet-stage__canvas {
	position: absolute;
	inset: 0;
	width: 100%;
	height: 100%;
}

/* 暗角：把视线收拢到宠物身上。叠放次序与 Web 端一致：画布在最低、暗角在其上、
   加载遮罩（呼吸占位）在暗角之上（Web 端 ::after z-index:1 / __veil z-index:2）。
   手机端原来两层都没写 z-index → 生成内容 ::after 排在最后，暗角会压住呼吸占位
   （实测无可见差异：暗角中心 52% 以内是透明的，而呼吸占位正好在正中；这里只是为了两端层级同构） */
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
	display: flex;
	align-items: center;
	justify-content: center;
}

/* 首帧前：呼吸占位，不用"加载中…"纯文字 */
.pet-stage__breath {
	width: 180rpx;
	height: 180rpx;
	border-radius: 50%;
	background: radial-gradient(circle, rgba(255, 152, 96, 0.22), transparent 70%);
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