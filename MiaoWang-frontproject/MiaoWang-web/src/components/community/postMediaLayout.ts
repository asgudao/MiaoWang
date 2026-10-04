/**
 * 帖子图片区的**纯逻辑**（Web）
 *
 * 三条硬规矩决定了它必须是一个"无 import、无副作用、可单独 import 真跑"的模块：
 * 1) 图片排列档位是**数据驱动的判定**（1 / 2 / 3 / 4 / 5~9 / >9 张各排一套），
 *    判定错了在浏览器里很难一眼看出来（"3 张排成 2×2"看着也挺整齐），所以它必须能被门禁
 *    **逐档真跑**，而不是靠断言模板字符串。
 * 2) 组件负责渲染、这个模块负责**决定渲染什么**，两者的边界清楚，
 *    渲染层（`PostMediaGrid.vue`）就不会混进"第几张要盖 +N"这种算术。
 * 3) 列表与详情**都带图片数组**（2026-09-21「社区列表图片契约定稿」后成立），只是列表有张数上限 ——
 *    `postMediaOf()` 把"数组 / 单张封面 / 无图"收成一个入口。
 *
 * 与后端契约的关系（**后端已定稿，别改**）：
 * - 列表 `PostRespDTO`（`MiaoWang-backproject/MiaoWang-community/.../dto/resp/PostRespDTO.java:49`）：
 *   `images` = `List<PostImageRespDTO>`，按 `sort` 升序、`[0]` 即封面、**最多 9 张**、**无图 = `[]`（不是 null）**；
 *   元素逐字为 **`{url, objectKey, sort}`**（`dto/resp/PostImageRespDTO.java:14,19,24`）
 * - ⚠️ **最容易踩的坑**：图片**上传接口** `/community/file/image` 的返回是另一个 DTO
 *   `ImageUploadRespDTO`（`{url, objectKey}`，**没有 `sort`**）—— 别拿上传响应当"列表元素"看
 * - 同 DTO 另有 `imageCount`（`:57`）= 图片**总张数**（含未下发的），`+N = imageCount - images.length`；
 *   ⚠️ **写侧上限也是 9**（`MAX_POST_IMAGE_COUNT`，超了 `B000303`）与读侧预览上限同值
 *   （`LIST_IMAGE_PREVIEW_COUNT = 9`）→ **该差值当前恒为 0，前端「+N」这一档当前不可达**
 *   （`mediaGridLayout` 的 `>9` 档只有放宽写侧上限才会被真实数据触发）
 * - 详情 `PostDetailRespDTO`：`images[]` 元素形状同上（`{url, objectKey, sort}`，`sort` 从 0 起），但**不截断**
 * - ⚠️ 列表与详情**共用同一个 `PostImageRespDTO`** → 元素形状天然一致，同一套渲染两边都能用
 * 所以 `PostMediaSource` 把 `images` 声明成**可选**：有就用数组，没有就退回单张封面
 * （防御性声明，不代表后端缺字段）。
 */

/**
 * 媒体种类。**2026-09-22 起图片与视频共用同一套九宫格**（用户："视频要有和照片一样的九宫格，
 * 在社区初始页面显示，效果和照片一样"）⇒ 每个格子必须知道自己是图还是视频：
 * 图片格走"裁剪 + 灯箱"，视频格走"同一个格子的几何 + 播放角标 + 点进详情播"。
 */
export type MediaKind = 'image' | 'video'

/** 媒体项（结构上兼容 `api/community.ts` 的 `PostMedia`，但这里不 import，保持本模块零依赖） */
export interface MediaItem {
  url?: string | null
  objectKey?: string | null
  sort?: number | null
  /** 缺省 = `'image'`（旧快照 / 只给 coverUrl 的兜底支都是图片） */
  kind?: MediaKind
  /**
   * ⚠️ **2026-09-22 新增：视频格的封面图 URL**（后端抽帧交付的 `PostVideoRespDTO.coverUrl`，**可为 `null`**）。
   * 只对 `kind === 'video'` 有意义；空 ⇒ 渲染层退回"中性占位 + 角标"（**绝不空白格**）。
   * ⚠️ 与 `PostMediaSource.coverUrl`（**帖子封面 = 第一张图**）不是一回事，**不许互相顶替**。
   */
  cover?: string | null
  /** ⚠️ 后端/`api/community.ts` 的**线上字段名**（同一个值，`postMediaOf` 会把它映射到 `cover`） */
  coverUrl?: string | null
}

/** `single` = 单张大图（保留长宽比）；`grid` = 正方形裁切网格 */
export type MediaGridMode = 'single' | 'grid'

export interface MediaGridLayout {
  mode: MediaGridMode
  /** 网格列数；`single` 档位是 0（单图不走网格） */
  columns: number
  rows: number
  /** 实际渲染几张（超过 9 张时只排 9 张） */
  shown: number
  /** 被藏起来的张数 = 第 9 张右下角「+N」的 N；没藏图时是 0 */
  overflow: number
}

/**
 * 网格最多排 9 格（第 9 格盖「+N」），与 QQ空间 / 朋友圈 / 微博 同一口径。
 * ⚠️ **2026-09-22 起这是"图片 + 视频"的合计上限**，与后端 `MAX_POST_MEDIA_COUNT = 9`
 *   （`images.size() + videos.size()`，`PostServiceImpl.java:123/321`）**逐字同口径**。
 */
export const MEDIA_MAX_CELLS = 9

/**
 * 小于这个边长的图**不可能**是内容图（实测：库里唯一的脏数据是 post 181 的 8×8 占位 PNG）。
 * 换句话：把一张 8×8 拉到"大图"就是一块糊，不如按"这张图不是内容"收掉。
 * ⚠️ 这是**产品判定**而不是加载失败判定，阈值放在这里便于一处调整。
 */
export const MIN_MEDIA_PX = 32

/**
 * 网格列数：**2 张一行两张、3 张一行三张、4 张 2×2、5~9 张 3×3**。
 * 只有 2 与 4 用两列（4 张 = 两行两列），其余都用三列。
 */
export function mediaGridColumns(count: number): number {
  return count === 2 || count === 4 ? 2 : 3
}

/**
 * 档位判定（唯一的真源）。返回 `null` 表示**这一块根本不该渲染**（0 张图）。
 *
 * | 张数 | mode   | columns | rows | shown | overflow |
 * |------|--------|---------|------|-------|----------|
 * | 0    | null（整块不渲染） |  |  |  |  |
 * | 1    | single | 0       | 1    | 1     | 0        |
 * | 2    | grid   | 2       | 1    | 2     | 0        |
 * | 3    | grid   | 3       | 1    | 3     | 0        |
 * | 4    | grid   | 2       | 2    | 4     | 0        |
 * | 5~9  | grid   | 3       | 2~3  | n     | 0        |
 * | >9   | grid   | 3       | 3    | 9     | n - 9    |
 */
export function mediaGridLayout(count: number): MediaGridLayout | null {
  const n = Number.isFinite(count) ? Math.floor(count) : 0
  // 没有图 → 整块**不渲染**（不是渲染了再 display:none，调用方拿到 null 就什么都不输出）
  if (n <= 0) return null
  if (n === 1) return { mode: 'single', columns: 0, rows: 1, shown: 1, overflow: 0 }
  const columns = mediaGridColumns(n)
  const shown = Math.min(n, MEDIA_MAX_CELLS)
  return {
    mode: 'grid',
    columns,
    rows: Math.ceil(shown / columns),
    shown,
    overflow: n - shown,
  }
}

/** 只保留有 url 的媒体项（缺 url 的项后端会静默丢掉，前端同样不渲染免得裂图） */
export function mediaItems(items: MediaItem[] | null | undefined): MediaItem[] {
  if (!Array.isArray(items)) return []
  return items.filter((m) => Boolean(m && m.url))
}

/** 按 `sort` 升序（详情 DTO 的 `sort` 从 0 起）；`sort` 缺失时按 `Number(0)` 处理，顺序稳定 */
export function sortMedia(items: MediaItem[] | null | undefined): MediaItem[] {
  return mediaItems(items)
    .slice()
    .sort((a, b) => Number(a?.sort ?? 0) - Number(b?.sort ?? 0))
}

/**
 * 一张图的稳定 key。
 * 用 `objectKey` 优先、其次 `url`、最后退回下标 —— **不能用下标当 key**：
 * 收掉（加载失败 / 尺寸退化）一张图之后下标会整体前移，key 跟着变就会串图。
 */
export function mediaKey(item: MediaItem | null | undefined, index: number): string {
  const key = String(item?.objectKey ?? '').trim() || String(item?.url ?? '').trim()
  return key || `#${index}`
}

/**
 * 这张图解出来是不是"退化的"（小到不可能是内容）。
 *
 * - 量不到（`NaN`）→ `false`：宁可照常显示，也不误杀（`naturalWidth` 取不到时不该收图）
 * - 量到 0 → `true`：说明根本没解出来，等于失败
 * - 任一边小于 `MIN_MEDIA_PX` → `true`
 */
export function isDegenerate(width: number, height: number): boolean {
  const w = Number(width)
  const h = Number(height)
  if (!Number.isFinite(w) || !Number.isFinite(h)) return false
  if (w <= 0 || h <= 0) return true
  return w < MIN_MEDIA_PX || h < MIN_MEDIA_PX
}

/**
 * 一条帖子（列表或详情）的**媒体**数组 = **图片 + 视频**。**这是列表"多图 / 单图 / 无图"的唯一入口。**
 *
 * 2026-09-22 口径（用户："视频要有和照片一样的九宫格，在社区初始页面显示，效果和照片一样"）：
 * | 输入 | 返回 | 说明 |
 * |---|---|---|
 * | 有 `images` 和/或 `videos` | 图片（按 `sort` 升序）在前、**紧跟**视频（数组原序） | 每项带 `kind` |
 * | 两者都没有可用项 + 有 `coverUrl` | 单张 `{url: coverUrl, kind: 'image'}` | 防御支（旧快照） |
 * | 都没有 | `[]` | 调用方据此**整块不渲染** |
 *
 * ⚠️ **顺序口径**：后端 `PostRespDTO` 的 `images` 有 `sort`（`[0]` 即冗余的 `coverUrl`），
 *   `videos` **原本没有 `sort` 字段**（`PostVideoRespDTO` 只有 `{url, objectKey}`）→ 视频仍按数组原序；
 *   ⚠️ **2026-09-22 后端新增 `coverUrl` / `coverObjectKey`**（抽帧封面，`PostVideoRespDTO.java:34,39`）
 *   ⇒ 视频项带上 `cover`（**取 `videos[i].coverUrl`**，可能是 `null`）。
 *   ⚠️ **绝不用 `post.coverUrl` 当视频封面**：那是帖子封面（第一张图，`PostServiceImpl.java:139/341`），
 *   混用会张冠李戴 —— 与手机端同一条红线（两端门禁都钉住）。
 *   "图片在前、视频在后"是唯一有依据的稳定顺序（封面永远是图片第 0 条，不会因为插了视频而变）。
 * ⚠️ **合计上限 9**：后端写侧 `CommunityConstant.MAX_POST_MEDIA_COUNT = 9` 就是
 *   `images.size() + videos.size() > 9` 的**合计**校验（`PostServiceImpl.java:123`，编辑同理 `:321`）
 *   → 与前端 `MEDIA_MAX_CELLS = 9` 同口径，所以「+N」这一档在当前后端下依旧不可达。
 * ⚠️ 这里**不额外请求**、**不伪造**：没有可用的图片/视频数组就老老实实当单张封面。
 */
export function postMediaOf(post: PostMediaSource | null | undefined): MediaItem[] {
  if (!post) return []
  const images: MediaItem[] = sortMedia(post.images).map((m) => ({ ...m, kind: 'image' as const }))
  const videos: MediaItem[] = sortMedia(post.videos).map((m) => ({
    ...m,
    kind: 'video' as const,
    /** ⚠️ 只认视频元素自己的 `coverUrl`（线上字段名）/ `cover`（本模块归一化后的名），**不回退**到 `post.coverUrl` */
    cover: String(m.coverUrl ?? m.cover ?? ''),
  }))
  if (images.length || videos.length) return images.concat(videos)
  const cover = String(post.coverUrl ?? '').trim()
  return cover ? [{ url: cover, kind: 'image' }] : []
}

/** `postMediaOf` 的入参形状：列表 DTO 与详情 DTO 都满足（`images` / `videos` 可选） */
export interface PostMediaSource {
  images?: MediaItem[] | null
  /**
   * 视频项：`{url, objectKey}` + ⚠️ **2026-09-22 新增 `coverUrl`**（抽帧封面）。
   * `api/community.ts` 的 `PostVideo` 类型需同步带上 `coverUrl`，`postMediaOf` 会把它映射成 `MediaItem.cover`。
   */
  videos?: MediaItem[] | null
  /** ⚠️ **帖子封面 = 第一张图**（`images[0]`），**不是**视频封面，别拿它顶替视频的 `cover` */
  coverUrl?: string | null
}