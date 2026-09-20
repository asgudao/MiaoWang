/**
 * 舞台取景数学（纯函数，只依赖 three，可在 Node 里离线复算）
 *
 * 为什么要单独抽出来：这段数学的失败方式是**静默**的。
 * `Box3.setFromObject()` 量蒙皮模型时会量出比真实渲染大 100 倍的盒子（SkinnedMesh 的节点链被算了两次），
 * 于是模型被缩成几个像素：页面上没有异常、没有警告、没有任何文案，只有"猫不见了"。
 * 抽成纯模块后，`MiaoWang-frontproject/tools/check-3d-model.js` 可以用**真 three + 真 GLB** 离线复算，
 * 把"肉眼才能发现"变成门禁能抓的回归。
 *
 * 量尺寸的正确姿势（两件事缺一不可，见 node_modules/three/src/math/Box3.js:155-188）：
 *  1. 先 `root.updateMatrixWorld(true)`：Box3 内部只调 `updateWorldMatrix()`，
 *     **不会**刷新 `SkinnedMesh.bindMatrixInverse`；不刷新就会把网格节点链算两次。
 *  2. 逐顶点量（precise / `getVertexPosition`）：`getVertexPosition` 才是着色器里那套骨骼变换，
 *     且 precise 分支（Box3.js:171）优先于 SkinnedMesh 的 `boundingBox` 缓存分支（Box3.js:192）。
 */
import * as THREE from 'three'

export interface StageFitOptions {
  /** 模型在任意方位最多占画框的比例（0.8 = 四周各留 20% 余量） */
  fit: number
  /** 俯仰角（度），恒定 */
  pitchDeg: number
  /** 竖直视场（度） */
  fov: number
  /**
   * 转台扫过的方位范围（弧度）。**必须等于相机真正会走到的方位区间**：
   * 取景半径取的是"所有采样方位的最大值"，采样范围少了哪一段，那一段就会切边。
   * 现在是整圈 2π = 360°（转台）↔ 采样 0°→360°。
   */
  azimuthRange: number
  /** 画布宽高比 */
  aspect: number
}

/** 扁平化的顶点表（x,y,z 连续存放），避免为每个顶点建对象 */
export interface StagePoints {
  xyz: Float32Array
  count: number
}

export interface StageFrame {
  /** 归一化前量到的真实最长边 */
  maxDim: number
  /** 施加在模型根节点上的缩放（= 1 / maxDim） */
  scale: number
  width: number
  height: number
  depth: number
  camTargetY: number
  camRadius: number
  camHeight: number
  /** 模型在画框里的占比（1.0 = 正好贴到画框边） */
  coverage: number
  points: StagePoints
}

const DEG = Math.PI / 180
/**
 * 读扁平顶点表（`xyz[i*3 + 0/1/2]`）里的一个分量。
 *
 * 为什么要有这个函数：`tsconfig.app.json` 开了 `noUncheckedIndexedAccess`，
 * 于是 `Float32Array[i]` 的类型是 `number | undefined`（它带数字索引签名），
 * 直接参与算术会报 TS2532 / TS18048。而这里所有下标都由 `i < count = xyz.length / 3` 保证合法，
 * 所以把断言**收口到这一处**，别在十几个调用点各写一遍 `!`（也免得漏掉一个就多一条红）。
 */
const comp = (xyz: Float32Array, i: number): number => xyz[i] as number
/**
 * 求取景半径时扫描的方位数（0~360° 均匀取样，含 0° 与 360° 两端 → AZ_FIT + 1 = 49 个方位）。
 * ⚠️ 这个数必须随 `azimuthRange` 一起变：转台从半圈（180°/24 段 = 7.5°/段）改成整圈后，
 * 段数也要翻倍，否则每段变成 15°，两道采样之间会漏掉模型最宽的那个方位 → 转到那里切边。
 * 步长（7.5°）保持不变，成本只是采样点数翻倍（49 × 顶点数，一次加载算一次）。
 */
const AZ_FIT = 48
/**
 * 复核"模型到底占画面多大"时扫描的方位数：比 AZ_FIT 密，用来抓取样点之间的最坏情况。
 * 整圈 360 段 = 1°/段，与改前的半圈分辨率（180 段 / 180°）一致。
 */
const AZ_CHECK = 360

/**
 * 逐顶点量模型**真实渲染出来**的包围盒。
 * 走 `getVertexPosition` + `matrixWorld`，与着色器里 `matrixWorld * bindMatrixInverse * Σ(boneMatrix) * bindMatrix * p` 等价。
 */
export function measureRenderedBox(root: THREE.Object3D) {
  root.updateMatrixWorld(true)
  const box = new THREE.Box3()
  const v = new THREE.Vector3()
  let vertices = 0
  root.traverse((o) => {
    const mesh = o as THREE.Mesh
    if (!mesh.isMesh || !mesh.geometry) return
    const pos = mesh.geometry.getAttribute('position')
    if (!pos) return
    for (let i = 0; i < pos.count; i++) {
      mesh.getVertexPosition(i, v).applyMatrix4(mesh.matrixWorld)
      box.expandByPoint(v)
      vertices++
    }
  })
  if (vertices === 0) throw new Error('模型里没有可用的网格顶点')
  if (!Number.isFinite(box.min.x + box.min.y + box.min.z + box.max.x + box.max.y + box.max.z)) {
    throw new Error('模型包围盒含非有限值')
  }
  return box
}

/** 逐顶点收集世界坐标（归一化与落地之后调用，量到的就是最终姿态） */
export function collectRenderedPoints(root: THREE.Object3D): StagePoints {
  root.updateMatrixWorld(true)
  const meshes: THREE.Mesh[] = []
  let total = 0
  root.traverse((o) => {
    const mesh = o as THREE.Mesh
    if (!mesh.isMesh || !mesh.geometry) return
    const pos = mesh.geometry.getAttribute('position')
    if (!pos) return
    meshes.push(mesh)
    total += pos.count
  })
  const xyz = new Float32Array(total * 3)
  const v = new THREE.Vector3()
  let k = 0
  for (const mesh of meshes) {
    const pos = mesh.geometry.getAttribute('position')
    for (let i = 0; i < pos.count; i++) {
      mesh.getVertexPosition(i, v).applyMatrix4(mesh.matrixWorld)
      xyz[k++] = v.x
      xyz[k++] = v.y
      xyz[k++] = v.z
    }
  }
  return { xyz, count: total }
}

/** 相机基：n = 视线方向（相机指向注视点），up / right 与它正交 */
function basisFromDirection(nx: number, ny: number, nz: number) {
  const n = new THREE.Vector3(nx, ny, nz).normalize()
  // up = 世界 up 去掉沿视线的分量；pitch = 0 时就是世界 up
  const up = new THREE.Vector3(0, 1, 0).addScaledVector(n, -n.y).normalize()
  const right = new THREE.Vector3().crossVectors(n, up).normalize()
  return { n, up, right }
}

/** 按方位角 + 俯仰角推相机基（相机在方位 az、俯仰 pitch 方向上，视线指向注视点） */
function basisFromAzimuth(az: number, pitch: number) {
  const cp = Math.cos(pitch)
  // 相机相对注视点的单位方向 m = 水平方向*cos(pitch) + up*sin(pitch)；视线 n = -m
  const mx = Math.sin(az) * cp
  const my = Math.sin(pitch)
  const mz = Math.cos(az) * cp
  return basisFromDirection(-mx, -my, -mz)
}

/**
 * 求"把整个模型框进画框"所需的相机半径（水平距离）。
 *
 * 判据（逐顶点、逐方位，闭式解）：设 d = 顶点 - 注视点、n = 视线方向、up/right = 相机基，
 * 顶点在画框内的充要条件是 |d·up| ≤ (L + d·n)·tanV·fit 且 |d·right| ≤ (L + d·n)·tanH·fit
 * （L = 相机到注视点的三维距离）→ 每个顶点给出一个 L 下限，取所有顶点与方位的最大值。
 * 因为 up/right 与方位角有关，模型"最宽"的方向会被真正算进去，窄画布也不会切边。
 */
export function fitRadius(points: StagePoints, camTargetY: number, opts: StageFitOptions) {
  const pitch = opts.pitchDeg * DEG
  const tanV = Math.tan((opts.fov * DEG) / 2) * opts.fit
  const tanH = tanV * opts.aspect
  const { xyz, count } = points
  let maxL = 0
  for (let s = 0; s <= AZ_FIT; s++) {
    const az = (opts.azimuthRange * s) / AZ_FIT
    const { n, up, right } = basisFromAzimuth(az, pitch)
    let need = 0
    for (let i = 0; i < count; i++) {
      const dx = comp(xyz, i * 3)
      const dy = comp(xyz, i * 3 + 1) - camTargetY
      const dz = comp(xyz, i * 3 + 2)
      const du = Math.abs(dx * up.x + dy * up.y + dz * up.z)
      const dr = Math.abs(dx * right.x + dy * right.y + dz * right.z)
      const along = dx * n.x + dy * n.y + dz * n.z
      const L = Math.max(du / tanV, dr / tanH) - along
      if (L > need) need = L
    }
    if (need > maxL) maxL = need
  }
  if (!Number.isFinite(maxL) || maxL <= 0) throw new Error('取景半径算不出来')
  return maxL * Math.cos(pitch)
}

/**
 * 复核：模型在画框里的占比（1.0 = 正好贴到画框边，> 1 = 有切边）。
 * 用**实际**相机位置算基，所以显式覆盖 radius / camY 时结果依然真实。
 */
export function projectedExtent(
  points: StagePoints,
  camTargetY: number,
  camRadius: number,
  camHeight: number,
  opts: StageFitOptions,
) {
  const tanV = Math.tan((opts.fov * DEG) / 2)
  const tanH = tanV * opts.aspect
  const { xyz, count } = points
  let worst = 0
  for (let s = 0; s <= AZ_CHECK; s++) {
    const az = (opts.azimuthRange * s) / AZ_CHECK
    const camX = camRadius * Math.sin(az)
    const camZ = camRadius * Math.cos(az)
    const { n, up, right } = basisFromDirection(-camX, camTargetY - camHeight, -camZ)
    for (let i = 0; i < count; i++) {
      const ex = comp(xyz, i * 3) - camX
      const ey = comp(xyz, i * 3 + 1) - camHeight
      const ez = comp(xyz, i * 3 + 2) - camZ
      const depth = ex * n.x + ey * n.y + ez * n.z
      if (depth <= 1e-6) return 99 // 顶点跑到相机后面了 = 严重切边
      const ndcY = Math.abs(ex * up.x + ey * up.y + ez * up.z) / (depth * tanV)
      const ndcX = Math.abs(ex * right.x + ey * right.y + ez * right.z) / (depth * tanH)
      if (ndcX > worst) worst = ndcX
      if (ndcY > worst) worst = ndcY
    }
  }
  return worst
}

/**
 * 归一化 + 取景：先量**真实渲染尺寸**→ 缩放到最长边 1.0 → XZ 居中 / 底部落地 → 算取景半径与相机高度。
 * 量不到尺寸一律抛错（调用方转成可见报错，不允许静默）。
 */
export function frameStageModel(root: THREE.Object3D, opts: StageFitOptions): StageFrame {
  const box = measureRenderedBox(root)
  const boxSize = box.getSize(new THREE.Vector3())
  const maxDim = Math.max(boxSize.x, boxSize.y, boxSize.z)
  if (!Number.isFinite(maxDim) || maxDim <= 0) throw new Error('模型最长边量到 0')

  const scale = 1 / maxDim
  root.scale.setScalar(scale)

  // 缩放后重新量：尺寸按比例缩了，但中心与落地高度要按缩放后的真实姿态取
  const scaled = measureRenderedBox(root)
  const center = scaled.getCenter(new THREE.Vector3())
  root.position.set(-center.x, -scaled.min.y, -center.z)

  const points = collectRenderedPoints(root)
  // 尺寸直接从点位反推（与 measureRenderedBox 同源，少走一次遍历）
  let minX = Infinity
  let minY = Infinity
  let minZ = Infinity
  let maxX = -Infinity
  let maxY = -Infinity
  let maxZ = -Infinity
  for (let i = 0; i < points.count; i++) {
    const x = comp(points.xyz, i * 3)
    const y = comp(points.xyz, i * 3 + 1)
    const z = comp(points.xyz, i * 3 + 2)
    if (x < minX) minX = x
    if (y < minY) minY = y
    if (z < minZ) minZ = z
    if (x > maxX) maxX = x
    if (y > maxY) maxY = y
    if (z > maxZ) maxZ = z
  }

  const camTargetY = (maxY - minY) * 0.5
  const camRadius = fitRadius(points, camTargetY, opts)
  const camHeight = camTargetY + Math.tan(opts.pitchDeg * DEG) * camRadius
  const coverage = projectedExtent(points, camTargetY, camRadius, camHeight, opts)

  return {
    maxDim,
    scale,
    width: maxX - minX,
    height: maxY - minY,
    depth: maxZ - minZ,
    camTargetY,
    camRadius,
    camHeight,
    coverage,
    points,
  }
}

/** 画布宽高比变了要重算半径（否则窄窗口会切边）；退化情况返回 null，由调用方保持原参数 */
export function reframe(frame: StageFrame, opts: StageFitOptions) {
  if (!frame.points.count) return null
  try {
    const camRadius = fitRadius(frame.points, frame.camTargetY, opts)
    return { camRadius, camHeight: frame.camTargetY + Math.tan(opts.pitchDeg * DEG) * camRadius }
  } catch {
    return null
  }
}