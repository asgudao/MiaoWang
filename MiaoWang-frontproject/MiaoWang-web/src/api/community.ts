/**
 * 社区接口（Web）
 *
 * 唯一契约来源：`.dsh-tmp/community-contract.md` —— **以文末「实测更新（2026-09-19，有真实数据后）」一节为准**
 * （线上库已有真实种子数据：帖子 178~182 / 评论 101~106 / 话题 68，17 个真实响应体逐字段审计过）。
 * 这里**只封页面真正用到的接口**，字段名一律照实测，不按 REST 惯例猜。
 *
 * 三条最容易踩的：
 * 1) 经网关调社区，**每个接口（连版块列表 `/section` 都要）必须同时带 `token` + `username` 两个头**；
 *    匿名会被网关拦成 `{"state":-1}`，缺 `username` 头则由下游回 `B000310`。
 *    请求层（`request.ts`）已经自动带头并把这两种情况都归一成 `ApiError.isUnauthorized`，这里不再重复处理。
 * 2) **两套分页信封**，解析方式完全不同：
 *    - 页码分页 `{pageNum,pageSize,total,pages,records}`：信息流 `/post`、`/my/like`、`/my/favorite`
 *    - 游标分页 `{records,size,hasMore,nextCursor}`：评论/回复、搜索、话题下帖子、个人帖子、关注、粉丝、通知、黑名单
 *      游标分页**没有 total**，`size` 是**请求的** size（不是实际条数），
 *      `nextCursor` 必须**原样回传**给下一次的 `lastId`，不要自己加减。
 * 3) **列表 DTO 没有正文**（`PostRespDTO` 实测 16 字段，只有 `title` + `coverUrl` + 四个计数 + `topics` + `videos`），
 *    正文只在详情 `PostDetailRespDTO` 里；而且社区**所有 DTO 都没有头像字段**，头像只能拿昵称首字顶。
 *
 * ⚠️ **类型不对称（实测踩点）**：同一个列表 DTO 里 `liked` / `favorited` 是**布尔**，`isTop` 却是**数字 `0`/`1`**
 * —— 不能用同一套判断（`liked === 1` 永远 false，`isTop === true` 也永远 false）。
 *
 * 写接口（`POST` / `DELETE`）：路径与字段已按契约写好；2026-09-19 的真实账号实测确认它们**幂等**
 * （重复点赞 / 取消 / 收藏一律 HTTP 200 + `code:"0"`，计数不重复累加），但**本端从未真调过**，
 * 所以下面的注释只写实测到的口径，不写"已验证"。
 */
import { ApiError, api } from './request'

/** 社区路径前缀（服务名由请求层补：`miaowang-community`） */
const BASE = '/community'

/* ---------- 数据模型（字段与报告第 2.3 节一一对应） ---------- */

/** `GET /section` → 版块（实测 6 条，`iconUrl` 全为 null） */
export interface CommunitySection {
  sectionId: number
  name: string
  code: string
  description: string | null
  iconUrl: string | null
  sort: number
}

/** 帖子上的话题（`name` **不含** `#`）；无话题时接口给的是空数组，不是 null */
export interface PostTopic {
  topicId: number
  name: string
  postCount: number
}

/**
 * 图片 / 视频项。
 * 图片：`{url, objectKey, sort}`；视频：`{url, objectKey}`。
 * 发帖时**必须 url + objectKey 成对发**（有 url 缺 objectKey 会回 `B000331`）。
 */
export interface PostMedia {
  url: string
  objectKey?: string
  sort?: number
}

/**
 * 信息流卡片 DTO（`GET /post`、`/my/like`、`/my/favorite` 的 `records` 项）。
 *
 * ⚠️ **没有 `content`**：列表不带正文（实测 grep `"content"` 零命中），卡片只能显示 `title` + `coverUrl`。
 * ⚠️ `username` 只在**该用户在社区里没有帖子/评论**时为 `null`（实测 uid=7 已解析出 `"probe114904"`，
 *    uid=1 社区无内容仍是 `null`）→ 按 `uid` 兜底必须留着。
 */
export interface PostSummary {
  postId: number
  sectionId: number
  uid: number
  username: string | null
  title: string | null
  coverUrl: string | null
  /**
   * 置顶标记**只在列表 DTO 里有**（详情 DTO 没有），且实测是**数字** `0`/`1`（Int32），不是布尔。
   * **一律按真值判断**（`if (post.isTop)` / `Boolean(post.isTop)`），
   * 禁止 `=== true` / `=== 1` 这类严格比较（会把 truthy 的数字判掉）。
   */
  isTop: number
  viewCount: number
  likeCount: number
  favoriteCount: number
  /** 含回复（实测帖子 178：2 一级 + 2 回复 → `4`），不是一级评论数 */
  commentCount: number
  /** 实测是布尔 `true`/`false`（不是 0/1）；未登录恒 false */
  liked: boolean
  /** 实测是布尔 `true`/`false`（不是 0/1）；未登录恒 false */
  favorited: boolean
  topics: PostTopic[] | null
  /** 没有视频时是空数组（与 `images` 同形状） */
  videos: PostMedia[] | null
  createTime: string
}

/**
 * 帖子详情 DTO（`GET /post/{postId}`、`POST /post` 的返回）。
 *
 * ⚠️ 详情**没有 `isTop`**、**没有 `coverUrl`**（两者实测都不在 17 个字段里）；
 * 封面 = `images` 按 `sort` **升序**的第 0 条，而 `sort` 实测**从 0 起**。
 * ⚠️ 读详情会让 `viewCount` +1（后端副作用，实测确认）。
 */
export interface PostDetail {
  postId: number
  sectionId: number
  uid: number
  username: string | null
  title: string | null
  content: string | null
  status: number
  viewCount: number
  likeCount: number
  favoriteCount: number
  /** 含回复（见 `PostSummary.commentCount`） */
  commentCount: number
  liked: boolean
  favorited: boolean
  createTime: string
  images: PostMedia[] | null
  topics: PostTopic[] | null
  videos: PostMedia[] | null
}

/**
 * 评论 / 回复 DTO（实测 13 字段）。
 *
 * - 一级评论：`parentId` = **数字 `0`**（不是 `null`）；`replyCount` = 数字（无回复时 `0`）；
 *   `children` = 数组（无回复时 `[]`）
 * - 回复项：`parentId` = 它所属的一级评论 id（>0）；`replyCount` / `children` = **`null`**
 * - ⚠️ 判"是不是一级评论"必须对 `0` 成立（`!parentId` / `== 0`），**不能**只判 `=== null`
 * - ⚠️ `children` 是**正序预览**（与 `/comment/reply` 同向，**与一级评论列表反向**）；
 *   "最多 3 条"**不是硬契约**（实测只造了 2 条回复，未压到上限）→ 按数组实际内容渲染，别把 3 写死
 */
export interface PostComment {
  commentId: number
  postId: number
  uid: number
  username: string | null
  content: string
  likeCount: number
  liked: boolean
  createTime: string
  /** 一级评论是 `0`；回复项是一级评论 id */
  parentId: number | null
  replyUid: number | null
  replyUsername: string | null
  /** 回复项是 `null`；一级评论是数字（无回复为 `0`） */
  replyCount: number | null
  /** 回复项是 `null`；一级评论是数组（无回复为 `[]`），正序 */
  children: PostComment[] | null
}

/** 页码分页信封：信息流 / 我的点赞 / 我的收藏 */
export interface Paged<T> {
  pageNum: number
  pageSize: number
  total: number
  pages: number
  records: T[]
}

/** 游标分页信封：评论 / 回复 / 话题下帖子（**没有 total**，`size` 是请求值） */
export interface CursorPage<T> {
  records: T[]
  size: number
  hasMore: boolean
  nextCursor: number | null
}

/* ---------- 读接口 ---------- */

/** 版块列表（实测 6 条；**经网关也要登录**） */
export function listSections() {
  return api.get<CommunitySection[]>('community', `${BASE}/section`)
}

/**
 * 信息流（**页码分页**，置顶优先）。
 *
 * `sectionId` **不传才是全站流** —— 契约里没有"推荐"版块，别自己编一个 id。
 * `pageSize` 上限 50（实测传 100 会被截成 50），`pageNum/pageSize` 传 0 会被后端改成 1/10。
 */
export function listPosts(params: {
  sectionId?: number | null
  pageNum?: number
  pageSize?: number
} = {}) {
  return api.get<Paged<PostSummary>>('community', `${BASE}/post`, {
    query: {
      sectionId: params.sectionId ?? undefined,
      pageNum: params.pageNum,
      pageSize: params.pageSize,
    },
  })
}

/** 帖子详情（含 `content` / `images` / `videos` / `topics`）；不存在回 `B000301` */
export function getPost(postId: number) {
  return api.get<PostDetail>('community', `${BASE}/post/${postId}`)
}

/**
 * 一级评论列表（**游标分页**，按 `commentId` **倒序**，新的在前）。
 *
 * `lastId` 传上一次返回的 `nextCursor`，**不要自己加减**（一级评论的游标方向是 `< commentId`）。
 * 每条一级评论自带 `children`（**正序**回复预览，条数不保证）与 `replyCount`（该评论下全部回复数）。
 */
export function listComments(params: { postId: number; lastId?: number | null; size?: number }) {
  return api.get<CursorPage<PostComment>>('community', `${BASE}/comment`, {
    query: { postId: params.postId, lastId: params.lastId ?? undefined, size: params.size },
  })
}

/**
 * 某条评论的全部回复（**游标分页**，按 `commentId` **正序**，旧的在前）。
 * `commentId` 传一级评论 id；游标方向是 `> commentId`。
 * ⚠️ 与一级评论列表**反向**，界面别假设"回复最新在前"。
 */
export function listCommentReplies(params: {
  commentId: number
  lastId?: number | null
  size?: number
}) {
  return api.get<CursorPage<PostComment>>('community', `${BASE}/comment/reply`, {
    query: { commentId: params.commentId, lastId: params.lastId ?? undefined, size: params.size },
  })
}

/** 话题热榜（**裸数组，没有分页信封**）；没有话题时是空数组 */
export function listHotTopics(size = 5) {
  return api.get<PostTopic[]>('community', `${BASE}/topic/hot`, { query: { size } })
}

/** 话题下的帖子（**游标分页**）；话题不存在回 `B000326` */
export function listTopicPosts(params: { topicId: number; lastId?: number | null; size?: number }) {
  return api.get<CursorPage<PostSummary>>('community', `${BASE}/topic/${params.topicId}/post`, {
    query: { lastId: params.lastId ?? undefined, size: params.size },
  })
}

/** 我赞过的帖子（**页码分页**）；未登录回 `B000310` */
export function listMyLikedPosts(params: { pageNum?: number; pageSize?: number } = {}) {
  return api.get<Paged<PostSummary>>('community', `${BASE}/my/like`, {
    query: { pageNum: params.pageNum, pageSize: params.pageSize },
  })
}

/** 我收藏的帖子（**页码分页**）；未登录回 `B000310` */
export function listMyFavoritePosts(params: { pageNum?: number; pageSize?: number } = {}) {
  return api.get<Paged<PostSummary>>('community', `${BASE}/my/favorite`, {
    query: { pageNum: params.pageNum, pageSize: params.pageSize },
  })
}

/* ---------- 写接口（源码推断，未实测；返回的 data 都是 null） ---------- */

/**
 * 点赞（**幂等**，后端靠唯一键 + 捕 `DuplicateKeyException`）。
 * 帖子不存在 / 已删 / 有拉黑关系时回 `B000301`。
 */
export function likePost(postId: number) {
  return api.post<null>('community', `${BASE}/like/${postId}`)
}

/** 取消点赞（物理删除，幂等） */
export function unlikePost(postId: number) {
  return api.del<null>('community', `${BASE}/like/${postId}`)
}

/** 收藏（幂等） */
export function favoritePost(postId: number) {
  return api.post<null>('community', `${BASE}/favorite/${postId}`)
}

/** 取消收藏（物理删除，幂等） */
export function unfavoritePost(postId: number) {
  return api.del<null>('community', `${BASE}/favorite/${postId}`)
}

/**
 * 发帖（后端已用真实账号实测通过；**本端没有真调过**）。
 *
 * - `sectionId` 必填（缺 → `B000305`，版块不存在/停用 → `B000300`；两者均为实测过的错误码）
 * - `title` / `content` / `images` / `videos` **不能同时为空**（否则 `B000304`）
 * - 图片与视频都各最多 9 个；视频要先用 `/community/file/video` 上传拿 url + objectKey
 * - 返回的是**详情 DTO**（不是列表 DTO，实测）；落库即 `status=2` 已发布，没有审核环节
 * - 正文里的 `#话题` 会被后端**自动解析成话题**（实测 `#猫咪日常` → `topicId:68`，
 *   `name` **不含 `#`**）；同名话题复用同一个 `topicId`
 *
 * ⚠️ 目前只发**纯文字帖**：`POST /community/file/image`、`/community/file/video` 未接入本端。
 */
export function createPost(body: {
  sectionId: number
  title?: string
  content?: string
  images?: PostMedia[]
  videos?: PostMedia[]
}) {
  return api.post<PostDetail>('community', `${BASE}/post`, { body })
}

/**
 * 发表评论 / 回复。
 *
 * - `content` 必填且 ≤1000 字（空 → `B000313`、超长 → `B000314`，均为实测过的错误码）
 * - `parentId` **不传 = 一级评论**；传了就挂到该评论的**一级评论**下（两级封顶：传二级评论 id 会被
 *   后端自动上提到它的一级评论，实测 confirm）
 *   ⚠️ **传 `0` 不等于"不传"** —— 后端会拿 `0` 去查评论，命中不了就回 `B000315`。
 *   一级评论的 `parentId` 实测就是数字 `0`，所以"回复一级评论"时必须先把它换成 `commentId`。
 * - `replyUid` = 被回复的人（回复某条回复时用它，后端会带出 `replyUsername` 供前端显示"回复 X"）
 * - 命中敏感词 → `B000318`
 *
 * ⚠️ 本端**没有真调过**这条（后端已由真实账号实测通过，幂等与错误码都有据）。
 */
export function createComment(body: {
  postId: number
  content: string
  parentId?: number
  replyUid?: number
}) {
  return api.post<PostComment>('community', `${BASE}/comment`, { body })
}

/* ---------- 展示用的格式化小工具 ---------- */

/** 每页条数：后端 `pageSize` 上限 50，这里固定 12（3 列卡片正好 4 行） */
export const POST_PAGE_SIZE = 12

/** 评论每页条数（游标分页的 `size` 只是请求值，不代表实际返回条数） */
export const COMMENT_PAGE_SIZE = 10

/**
 * 昵称兜底：`username` **只在社区里没有该用户的内容时为 `null`**（实测 uid=7 有真值、uid=1 为 null），
 * 所以一律按 uid 兜底显示，别让界面出现空白或 "null"。
 */
export function authorName(username: string | null | undefined, uid: number | null | undefined) {
  const name = (username ?? '').trim()
  if (name) return name
  return uid ? `用户 ${uid}` : '匿名用户'
}

/**
 * 置顶判断的**唯一口径**：`isTop` 实测是**数字** `0`/`1`，所以**按真值判断**。
 * 不要在这里改成 `=== true` / `=== 1` —— 严格比较会把后端返回的数字判掉。
 */
export function isPinned(post: Pick<PostSummary, 'isTop'> | null | undefined): boolean {
  return Boolean(post?.isTop)
}

/** 头像只能拿昵称首字生成：社区**所有 DTO 都没有头像字段** */
export function initialOf(text: string) {
  return (text.trim() || '宠').slice(0, 1)
}

/** 列表 DTO 没有正文也没有必填标题，标题为空时给个中性占位 */
export function titleText(title: string | null | undefined) {
  const t = (title ?? '').trim()
  return t || '无标题'
}

/** 话题数组兜底：接口给的是空数组，但 DTO 类型可空，统一收敛成数组 */
export function topicList(topics: PostTopic[] | null | undefined): PostTopic[] {
  return Array.isArray(topics) ? topics : []
}

/**
 * 媒体数组兜底：**缺 `url` 的项会被后端静默丢弃**（只有"有 url 缺 objectKey"才报 `B000331`），
 * 前端同样只渲染有 url 的项，免得出现裂图。
 */
export function mediaList(items: PostMedia[] | null | undefined): PostMedia[] {
  if (!Array.isArray(items)) return []
  return items.filter((m) => Boolean(m?.url))
}

/**
 * 媒体数组按 `sort` **升序**（实测 `sort` 从 `0` 起；详情 DTO 没有 `coverUrl`，
 * 封面就是升序后的第 0 条）。与手机端 `utils/community.js` 的 `imageUrls/videoUrls` 同口径。
 */
export function mediaListSorted(items: PostMedia[] | null | undefined): PostMedia[] {
  return mediaList(items)
    .slice()
    .sort((a, b) => Number(a.sort ?? 0) - Number(b.sort ?? 0))
}

/** 详情页封面：详情 DTO 没有 `coverUrl`，封面 = `images` 按 `sort` 升序的第 0 条 */
export function detailCover(images: PostMedia[] | null | undefined): string {
  return mediaListSorted(images)[0]?.url ?? ''
}

/** `2026-09-17T06:20:53.000+00:00` → `2026-09-17 14:20`（本地时区）；解析不了就原样吐出 */
export function fullTimeText(iso: string | null | undefined): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return String(iso)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/** 相对时间：一周内说"几分钟前/几小时前/几天前"，更早给绝对时间 */
export function relativeTime(iso: string | null | undefined): string {
  if (!iso) return ''
  const t = new Date(iso).getTime()
  if (Number.isNaN(t)) return String(iso)
  const diff = Date.now() - t
  // 客户端时钟比服务端快时不显示"N 分钟前"这种负数
  if (diff < 60_000) return '刚刚'
  const minutes = Math.floor(diff / 60_000)
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days} 天前`
  return fullTimeText(iso)
}

/** 计数：上万折算成"1.2万"，避免卡片上出现一长串数字 */
export function countText(n: number | null | undefined): string {
  const v = Number(n ?? 0)
  if (!Number.isFinite(v) || v <= 0) return '0'
  if (v < 10_000) return String(v)
  const wan = v / 10_000
  return `${wan >= 100 ? Math.round(wan) : Number(wan.toFixed(1))}万`
}

/** 版块名：按 `sectionId` 在版块表里查；查不到就退回 id，不编名字 */
export function sectionName(sections: CommunitySection[], sectionId: number | null | undefined) {
  if (!sectionId) return ''
  return sections.find((s) => s.sectionId === sectionId)?.name ?? `版块 ${sectionId}`
}

/**
 * 社区错误码 → 人话（码表来自报告第 2.4 节的后端 `CommunityErrorCodeEnum`）。
 * 后端 message 已经是中文，能直接用就直接用，这里只补"下一步该怎么办"。
 */
export function communityErrorMessage(e: unknown): string {
  if (!(e instanceof ApiError)) return (e as Error)?.message || '未知错误'
  switch (e.code) {
    case 'NETWORK':
      // message 里带着实际请求地址，比笼统文案有用 → 直接透传
      return e.message || '连不上后端，请检查后端进程与 Vite 代理'
    case 'B000001':
      return '服务没能处理这个请求。多半是路径或参数写错了，也可能是社区服务正在重启，稍后重试一次'
    case 'B000300':
      return '这个版块不存在或已停用，换一个版块看看'
    case 'B000301':
      return e.message || '帖子不存在或已被删除'
    case 'B000302':
      return '只能操作自己发的帖子'
    case 'B000304':
      return '帖子内容不能全空，标题或正文至少写一个'
    case 'B000305':
      return '请先选择要发到哪个版块'
    case 'B000310':
      return '登录状态失效了，重新登录后再试'
    case 'B000311':
      return '这条评论不存在或已被删除'
    case 'B000312':
      return e.message || '只有评论作者或楼主能删这条评论'
    case 'B000313':
      return '评论不能为空'
    case 'B000314':
      return '评论太长了，最多 1000 字'
    case 'B000315':
      return '要回复的那条评论不在这篇帖子里，刷新一下再试'
    case 'B000318':
      return '内容里有不适合发布的词，改一改再发'
    case 'B000326':
      return '这个话题不存在或已被删除'
    case 'B000327':
      return '搜索关键词不能为空，也不能超过 50 个字'
    case 'B000331':
      return '视频信息不完整，url 和 objectKey 要成对提交'
    case 'B000332':
      return '一篇帖子最多 9 个视频'
    default:
      return e.message || '请求失败，请稍后再试'
  }
}