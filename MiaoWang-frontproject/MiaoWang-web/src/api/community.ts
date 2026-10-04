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
 * 3) **列表 DTO 没有正文**（`PostRespDTO` 只有 `title` + `coverUrl` + 四个计数 + `topics` + `videos`；
 *    ⚠️ **2026-09-21「社区列表图片契约定稿」后字段已从 16 个变 18 个**：多了 `images`（`List<PostImageRespDTO>`，
 *    按 `sort` 升序、最多 9 张、无图是 `[]`）与 `imageCount`（图片总张数）——
 *    依据后端 `MiaoWang-community/.../dto/resp/PostRespDTO.java:49` / `:57`，**后端已定稿**），
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
  /**
   * ⚠️ **2026-09-22 新增：视频封面**（后端抽帧的 `PostVideoRespDTO.coverUrl`，**可为 `null`**）。
   * 只对 `videos[]` 元素有意义（图片的"封面"就是它自己）；空 ⇒ 列表格退回中性占位 + 角标。
   * ⚠️ **不是** `PostRespDTO.coverUrl`（那是帖子封面 = 第一张图），**不许互相顶替**。
   */
  coverUrl?: string | null
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
   * ⚠️ **2026-09-21「社区列表图片契约定稿」：这个字段已经存在，不再是"将来"**。
   *
   * 后端已定稿：`PostRespDTO.java:49` 就是 `List<PostImageRespDTO> images`（**列表与详情共用同一个元素类型**），
   * **按 `sort` 升序、`[0]` 即封面、最多 9 张、无图 = `[]`（不是 `null`）**；
   * 元素逐字为 **`{url, objectKey, sort}`**（`dto/resp/PostImageRespDTO.java:14,19,24`）。
   * ⚠️ **最容易踩的坑**：图片**上传接口** `/community/file/image` 的返回是**另一个 DTO**
   * `ImageUploadRespDTO`（`{url, objectKey}`，**没有 `sort`**）—— 拿上传响应当"列表元素"看会得出错误结论。
   * 同一个 DTO 还有 `imageCount`（`PostRespDTO.java:57`）= 该帖图片**总张数**（含未下发的），
   * `+N = imageCount - images.length`；⚠️ **写侧上限也是 9**（`MAX_POST_IMAGE_COUNT`，超了 `B000303`）
   * 与读侧预览上限（`LIST_IMAGE_PREVIEW_COUNT = 9`）**同值** → **该差值当前恒为 0，前端「+N」分支当前不可达**。
   * 搜索 / 话题 / 用户主页列表共用同一组装器，也是这个形状。
   *
   * 这里仍**可选**声明它（`?`）是防御性写法：列表卡片读图统一走 `postMediaOf()`
   * （`components/community/postMediaLayout.ts`）—— 有可用数组就按多图档位排，空/缺席就退回单张 `coverUrl`，
   * **不额外发请求、不伪造多图**。所以 `?` 不代表后端缺字段。
   * （`tools/community-fixtures/feed.json` 是**定稿前**抓的 16 字段老快照，别再拿它当契约依据。）
   */
  images?: PostMedia[] | null
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
  /**
   * 内容摘要字段族（**口径未定稿**，2026-09-21 由后端代理添加中，预计形如
   * `contentExcerpt` + `contentTruncated`）。
   *
   * ⚠️ 这里把候选名都声明成**可选**、并额外给一个索引签名，是刻意的：
   * 「用哪个字段名」这件事**在类型上也不许被写死**。页面读摘要走
   * `components/community/postExcerpt.ts` 的 `postExcerptOf()`，它按优先名清单逐个找，
   * **一个都没命中就整块不渲染摘要区**（绝不拿标题/正文冒充）。
   * 定稿后只需要把定稿名填进那个模块的 `EXCERPT_FIELDS` 第一位，本文件不用改。
   */
  contentExcerpt?: string | null
  contentTruncated?: boolean | null
  excerpt?: string | null
  excerptTruncated?: boolean | null
  contentSummary?: string | null
  summary?: string | null
  contentBrief?: string | null
  brief?: string | null
  /**
   * 「点赞数最高的一条评论」（**口径同样未定稿**，2026-09-21 后端代理正在给列表**批量带回**）。
   *
   * ⚠️ 声明成 `unknown` 而不是某个具体形状，是刻意的：**形状也不许猜死**。
   * 页面读它走 `components/community/postTopComment.ts` 的 `topCommentOf()` ——
   * 那个纯模块按优先名找字段、容忍"评论对象嵌一层"等形状，**后端没给时才降级**到
   * "取一页评论、在这一页里选点赞最高的"，并把"只在这一页里最高"如实标出来（`pageScoped`）。
   * 定稿后同样只改那一个模块的 `TOP_COMMENT_FIELDS` 第一位。
   */
  topComment?: unknown
  hotComment?: unknown
  bestComment?: unknown
  topLikedComment?: unknown
  /** 摘字段名未定稿，所以任意未知字段都要能被 `postExcerptOf()` 读到（不 then 报类型错） */
  [key: string]: unknown
}

/**
 * 帖子详情 DTO（`GET /post/{postId}`、`POST /post` 的返回）。
 *
 * ⚠️ 详情**没有 `isTop`**、**没有 `coverUrl`**（两者实测都不在 17 个字段里）；
 * 封面 = `images` 按 `sort` **升序**的第 0 条，而 `sort` 实测**从 0 起**。
 * ⚠️ 读详情**默认**会让 `viewCount` +1（后端副作用，实测确认）。
 * **2026-09-21 同批定稿**：详情新增请求参数 `countView`（Boolean，**默认 `true` = 保持上面的老行为**；
 * 传 `false` 则**一次都不写 `view_count`**，响应里的 `viewCount` 也不 +1）——
 * 依据后端 `MiaoWang-community/.../controller/CommunityPostController.java:68-77` 与
 * `service/PostService.java:39-43`。⚠️ 本端 `getPost(postId, { countView })` **支持**这个参数：
 * **不传 = 不发送该 query = 后端默认 `true`**（详情页 `PostDetailView` 走这条，与从前一字不差）；
 * 信息流卡片的「内容展开」显式传 `countView: false`（那只是补全正文，不该算一次浏览）。
 * ⚠️ 这条注释在 2026-09-21 被**并发修改过**：实现方（信息流卡片切片）把 `getPost` 由"不传"改成"可选传"，
 * 所以这里同步改成新事实，**别再写回"本端不传"**。
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
 * - `likeCount`（数字）与 `liked`（**布尔**）**一级评论与回复项都有**（2026-09-21 实测）；
 *   未登录时 `liked` 恒 `false`。评论点赞接口见 `likeComment` / `unlikeComment`
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

/**
 * 帖子详情（含 `content` / `images` / `videos` / `topics`）；不存在回 `B000301`
 *
 * ⚠️ **2026-09-21 定稿的请求参数 `countView`**：
 * - **不传** = 后端默认 `true` = 读一次 `viewCount` +1（详情页 `PostDetailView` 走这条，
 *   与从前一字不差 —— 用户点进详情页本来就该算一次浏览）
 * - **传 `false`** = **一次都不写 `view_count`**，响应里的 `viewCount` 也不 +1。
 *   信息流卡片的「内容展开」走这条：那只是"把这张卡片的正文补全"，
 *   **不是一次浏览**，不该让"点展开就涨浏览量"。
 * 依据 `MiaoWang-community/.../controller/CommunityPostController.java:68-77`
 * 与 `service/PostService.java:39-43`（`@RequestParam(defaultValue = "true") Boolean countView`）。
 */
export function getPost(postId: number, options: { countView?: boolean } = {}) {
  return api.get<PostDetail>('community', `${BASE}/post/${postId}`, {
    // 不传就整个键不出现（`buildUrl` 会跳过 `undefined`），与从前行为完全一致
    query: { countView: options.countView === false ? false : undefined },
  })
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

/**
 * **评论点赞**（`POST /comment/like/{commentId}`，返回 `data` = `null`）。
 *
 * 2026-09-21 用真实账号 `probe114904` 在种子评论 101~104 上**实测过**（实测记录见
 * `.dsh-tmp/community-contract.md`），口径与帖子点赞**不完全一样**：
 * - 幂等：重复点赞 → HTTP 200 + `code:"0"`，计数**不重复累加**（后端唯一键 + 捕 `DuplicateKeyException`）
 * - `CommentRespDTO` 对**一级评论与回复都带 `liked`**（布尔）与 `likeCount`（数字），
 *   所以回复项也能点赞，页面可以完全信任接口给的 `liked`，本地乐观状态只是过渡
 * - ⚠️ **唯一的不对称**：`POST` 传不存在的评论 → `B000311 评论不存在或已被删除`；
 *   而 **`DELETE` 传不存在的评论回 `code:"0"` 静默成功**（`InteractionServiceImpl.unlikeComment`
 *   不校验评论是否存在，只删点赞关系）。所以取消点赞这条路"成功"不代表评论还在，
 *   失败也基本不会是"评论没了"。
 */
export function likeComment(commentId: number) {
  return api.post<null>('community', `${BASE}/comment/like/${commentId}`)
}

/** 取消评论点赞（物理删除，幂等；不存在的评论也回 `code:"0"`，见 `likeComment` 的说明） */
export function unlikeComment(commentId: number) {
  return api.del<null>('community', `${BASE}/comment/like/${commentId}`)
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

/**
 * 卡片上的「评论条数」字段名**正在由后端核实**（2026-09-21），所以这里按**优先名清单**找。
 *
 * ⚠️ 红线：**找不到就返回 `null` → 卡片上不显示条数**，绝不编造数字、也绝不拿别的计数冒充。
 * 定稿后只改这一个数组的顺序。
 */
export const COMMENT_COUNT_FIELDS = [
  'commentCount',
  'commentsCount',
  'commentNum',
  'commentTotal',
] as const

/** 列表 DTO 上「评论条数」的进程内视图（正数）/ `null`（字段缺席或不可信） */
export function commentCountOf(post: Record<string, unknown> | null | undefined): number | null {
  if (!post) return null
  for (const name of COMMENT_COUNT_FIELDS) {
    const raw = post[name]
    if (raw === undefined || raw === null || raw === '') continue
    const n = Number(raw)
    if (Number.isFinite(n) && n >= 0) return n
  }
  return null
}

/* ---------- 展示用的格式化小工具 ---------- */

/** 每页条数：后端 `pageSize` 上限 50，这里固定 12（3 列卡片正好 4 行） */
export const POST_PAGE_SIZE = 12

/** 评论每页条数（游标分页的 `size` 只是请求值，不代表实际返回条数） */
export const COMMENT_PAGE_SIZE = 10

/**
 * 详情页"一次把评论拉完"用的页大小。
 *
 * ⚠️ **后端硬上限就是 50**（`CommentServiceImpl.normalizeSize()`：
 * `Math.min(size, CommunityConstant.MAX_PAGE_SIZE)` = 50；`size < 1` 或缺席回落 `DEFAULT_PAGE_SIZE`）。
 * 所以这里取满 50 —— 传更大的值**不会**多给，只会被截到 50。一次拉不完就靠 `nextCursor` 续页。
 */
export const COMMENT_FETCH_ALL_SIZE = 50

/**
 * 详情页自动翻页的**安全上限**（页数，不是条数）：50 × 20 = 1000 条。
 * 超过就不再往下拉，页面如实显示"已加载 N 条"（见 `PostDetailView` 的说明）——
 * **不假装"全部已显示"**，也不让一个异常响应把浏览器拖死。
 */
export const COMMENT_FETCH_ALL_MAX_PAGES = 20

/**
 * 信息流卡片上「最新评论」一条预取几条。
 *
 * ⚠️ **这是卡片唯一允许预取的评论数，而且只在用户点「评论展开」之后才发请求**。
 * 卡片刚渲染时不发任何评论请求 —— 一页 10 张卡各预取一次就是 10 个请求（N+1）。
 * `size` 走 `listComments`，后端 `normalizeSize` 下限是 1（`CommentServiceImpl.java:380-385`），
 * 多取一条只为判断 `hasMore`，所以 `size=1` 是合法请求。
 */
export const COMMENT_PREVIEW_SIZE = 3

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
 * 评论 / 回复 / 卡片的**头像首字**（与 `authorName` 同一套兜底口径）。
 *
 * 单独抽出来是因为信息流卡片现在要显示评论作者的首字：如果只调 `authorName()` 再 `initialOf()`，
 * 兜底值会走成「用户 7」→ 首字「用」，而用户主页那套口径是「喵友 #7」→ 首字「喵」。
 * 两套口径并存会让同一个 uid 在两张卡上显示不同的首字，所以这里明确用 `profileName` 那一套。
 *
 * ⚠️ 注意 `authorName()` 的兜底文案**一个字节都没改**（那是详情页与旧卡片在用的口径，
 * 社区 DTO 从不返回"匿名用户"这件事见 `profileName()` 的说明，属另一个已挂账的口径问题）。
 */
export function commentAuthorName(username: string | null | undefined, uid: number | null | undefined) {
  const name = (username ?? '').trim()
  if (name) return name
  const id = Number(uid)
  return Number.isFinite(id) && id > 0 ? `喵友 #${id}` : '喵友'
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
 *
 * ⚠️ **2026-09-22 更新（别再被旧说法误导）**：`videos[]` 元素**已不再是 `{url, objectKey}` 两个字段**——
 * 后端交付抽帧后 `PostVideoRespDTO` 多了 **`coverUrl` / `coverObjectKey`**（`PostVideoRespDTO.java:34,39`），
 * 所以 `PostMedia` 也带上可选的 `coverUrl`。⚠️ 这里的"详情 DTO 没有 `coverUrl`"说的是**帖子级**
 * `PostRespDTO.coverUrl`（= 第一张图），**不是**视频级封面，两者别混（视频封面只认 `videos[i].coverUrl`）。
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

/* ==================================================================== *
 * 用户主页（`/community/user/*` 与 `/community/follow/*`）
 *
 * 2026-09-21 追加，**上面既有导出一个字节都没动**。
 * 契约来源：`.dsh-tmp/knowledge-userpage-contract.md` §B 用户主页契约（子代理逐个实测，
 * 92 个脱敏 fixture 在 `tools/userpage-fixtures/`，由 `tools/check-userpage-web.js` 驱动断言）。
 *
 * 三条会坑死主页的硬事实（都写进类型与注释里，别在页面里重新踩一遍）：
 * 1) **uid 必须显式传**。`profile` / `post` / `like` 不传会**回落成登录 uid**，
 *    但 `pet` **不传恒返回 `[]`**（后端 `uid == null` 直接 emptyList，不回落成自己）
 *    → 本文件的"看别人"接口一律把 `uid` 写成**必填参数**，类型上堵死"忘了传"。
 * 2) **"是否已关注"没有独立接口**（`/follow/is-following` / `/check` / `/relation` 实测全部
 *    HTTP 200 + `B000001` 未映射）→ 关注按钮的初始态只能从 `profile.followed` 取，不许自己猜。
 * 3) **同一模块里两种分页信封并存**。`/community/post`（信息流）是页码信封，
 *    而 `/user/post`、`/user/like`、`/follow/following`、`/follow/follower` 是**游标信封**
 *    `{records,size,hasMore,nextCursor}`：**没有 `total`**，`size` 是**归一化后的请求值**
 *    （实测 `size=999` 回来 `size:50` 却只有 5 条记录），`nextCursor` 必须**原样回传**。
 *
 * ⚠️ 另外两条显示口径（不是接口形状）：
 * - **没有头像**：`miaowang_user.avatar` 全 null、社区 DTO 里根本没有头像字段
 *   → 主页第一版**不设计头像**，也不要渲染空 `<img>`。
 * - **`username` 可能为 null**（社区只从帖子表反查昵称，从没发过帖的人恒为 null，含 uid=1）
 *   → 一律走 `profileName()` 兜底成「喵友 #uid」，**不要**写后端从未返回的「匿名用户」。
 * ==================================================================== */

/** 主页头部（`GET /user/profile` → **裸对象**，没有 records/total/分页信封） */
export interface UserProfile {
  /** 恒等于入参 uid；不存在的人也是 `code:"0"` + 全 0，不报错 */
  uid: number
  /** **可为 null**（从没发过帖的人）→ 显示时必须走 `profileName()` */
  username: string | null
  /** 看的是不是自己（只带 `token` 缺 `username` 头时**静默降级成 false**） */
  self: boolean
  /** 我有没有关注 TA；看自己恒 false。这是"是否已关注"的**唯一**来源 */
  followed: boolean
  postCount: number
  likeReceivedCount: number
  followingCount: number
  followerCount: number
}

/** 关注 / 粉丝列表的一行（`GET /follow/following|follower` 的 `records[]`） */
export interface FollowUser {
  uid: number
  /** 可为 null → 显示走 `profileName()` */
  username: string | null
  /** 我有没有关注这一行的人（列表项自带，不用再查） */
  followed: boolean
  createTime: string
}

/**
 * 他的宠物（`GET /user/pet` → **裸数组**，没有信封）。
 *
 * 与 `PetBriefRespDTO` 同形；字段可直接喂给 `utils/pet.ts` 的
 * `petDisplayName` / `ageLabel` / `breedLabel` / `genderLabel` / `sterilizedLabel`（结构兼容）。
 */
export interface UserPetBrief {
  pid: number
  /** 服务端不校验空名，库里已有无名宠物 → 显示走 `petDisplayName()` */
  pName: string | null
  /** 中文大类（`"猫"` / `"狗"`…），不是 `CAT` */
  species: string
  specificSpecies: string | null
  ownerCallName: string | null
  /** `yyyy-MM-dd`（不是 ISO 时间戳） */
  birth: string | null
  /** 0=雌 1=雄 */
  gender: number
  /** 0=未绝育 1=已绝育 */
  sterilizedState: number
}

/** 主页各列表每页条数（游标分页的 `size` 只是请求值，不代表实际返回条数） */
export const USER_PAGE_SIZE = 10

/** 主页头部（`uid` 必传：不传会静默变成"看自己"） */
export function getUserProfile(uid: number) {
  return api.get<UserProfile>('community', `${BASE}/user/profile`, { query: { uid } })
}

/** 他发布的帖子（**游标分页**；不存在的人回 `{records:[],hasMore:false,nextCursor:null}`） */
export function listUserPosts(params: { uid: number; lastId?: number | null; size?: number }) {
  return api.get<CursorPage<PostSummary>>('community', `${BASE}/user/post`, {
    query: { uid: params.uid, lastId: params.lastId ?? undefined, size: params.size },
  })
}

/**
 * 他点赞过的帖子（**游标分页**，DTO 与信息流同一个 `PostRespDTO`）。
 *
 * ⚠️ 游标语义**可能是 `like_id`（点赞明细主键）而不是 `postId`** —— 实测数据只有 3 条、
 * `hasMore` 恒 false，**没测到第二页**。所以照后端给的 `nextCursor` 原样回传，不要自己拿
 * `postId` 拼游标。已删/下架的帖子会被跳过 → 本页条数可能少于 `size`。
 */
export function listUserLikedPosts(params: { uid: number; lastId?: number | null; size?: number }) {
  return api.get<CursorPage<PostSummary>>('community', `${BASE}/user/like`, {
    query: { uid: params.uid, lastId: params.lastId ?? undefined, size: params.size },
  })
}

/**
 * 他的宠物（**裸数组，一次拿全**，没有分页）。
 *
 * ⚠️ `uid` 在这里是**必填**且必须真的传：后端 `uid == null` 直接返回 `[]`，
 * 不会回落成"我的宠物"（实测 fixture `cu-pet-nouid.json` → `data: []`）。
 */
export function listUserPets(uid: number) {
  return api.get<UserPetBrief[]>('community', `${BASE}/user/pet`, { query: { uid } })
}

/** 我关注的人（**游标分页**；`uid` 不传才是"我自己关注的人"） */
export function listFollowing(params: { uid?: number; lastId?: number | null; size?: number } = {}) {
  return api.get<CursorPage<FollowUser>>('community', `${BASE}/follow/following`, {
    query: { uid: params.uid, lastId: params.lastId ?? undefined, size: params.size },
  })
}

/** 关注我的人（**游标分页**，同上） */
export function listFollowers(params: { uid?: number; lastId?: number | null; size?: number } = {}) {
  return api.get<CursorPage<FollowUser>>('community', `${BASE}/follow/follower`, {
    query: { uid: params.uid, lastId: params.lastId ?? undefined, size: params.size },
  })
}

/**
 * 关注某人（`POST /community/follow/{uid}`，`data` 为 `null`）。
 *
 * ⚠️ **写接口，本端从未真调过**：契约里"关注自己会报 `ClientException(FOLLOW_SELF)`"、
 * 幂等靠唯一键捕 `DuplicateKeyException`，都是**读源码推断**，具体错误码**未实测**
 * （红线禁止写操作）→ 所以错误文案一律透传后端 message，**不要**按推断写死码表。
 */
export function followUser(uid: number) {
  return api.post<null>('community', `${BASE}/follow/${uid}`)
}

/** 取消关注（`DELETE` 同路径；删 0 行也算成功）。同样是**未实测**的写接口 */
export function unfollowUser(uid: number) {
  return api.del<null>('community', `${BASE}/follow/${uid}`)
}

/**
 * 主页上的昵称兜底。
 *
 * 为什么不能用 `authorName()`：那条会退成「用户 7」/「匿名用户」，而**后端从不返回"匿名用户"**
 * 这种占位 —— 主页上编一个不存在的身份比留白更糟。这里统一成「喵友 #uid」。
 */
export function profileName(
  username: string | null | undefined,
  uid: number | null | undefined,
): string {
  const name = (username ?? '').trim()
  if (name) return name
  if (uid === null || uid === undefined) return '喵友'
  return `喵友 #${uid}`
}

/**
 * 用户主页错误码 → 人话。
 *
 * 与 `communityErrorMessage` 的关系：**先处理主页特有的两种情况**，其余原样交给它，
 * 这样后端给的中文 message 一定透传得到（关注/取关的错误码未实测，不能自编文案）。
 */
export function userProfileErrorMessage(e: unknown): string {
  if (!(e instanceof ApiError)) return (e as Error)?.message || '未知错误'
  if (e.isUnauthorized) return '登录状态失效了，重新登录后再看这位的主页'
  if (e.code === 'NETWORK') return e.message || '连不上后端，请检查后端进程与 Vite 代理'
  if (e.code === 'B000001') {
    // 未映射的路径也是这个码（HTTP 200），与"服务真出错"同形 → 文案要留出两种可能
    return '服务没能处理这个请求。可能是参数不对，也可能是这个接口在当前后端版本里还不存在，稍后重试一次'
  }
  return communityErrorMessage(e)
}

/* ==================================================================== *
 * 消息通知（`/community/notification/*`）
 *
 * 2026-09-21 追加，**上面既有导出一个字节都没动**。
 * 契约来源：`.dsh-tmp/reminder-notification-contract.md` §2「B 类 消息通知」
 * （19 个实测响应 fixture 在 `tools/reminder-fixtures/`，由 `tools/check-notification-web.js` 驱动断言）。
 *
 * 五条硬事实（写错任何一条，在别处照样跑绿）：
 * 1) **信封是游标那套** `{records,size,hasMore,nextCursor}`：没有 `total`，`size` 是**归一化后的请求值**
 *    （实测 `size=0` 回 `size:10`、`size=999` 回 `size:50`，而 `records.length` 只有 3），
 *    `nextCursor` = **本页最后一条的 id**，必须**原样**回传给下一次的 `lastId`；
 *    后端按 `notification_id < lastId` 取（是"往下取更小"，**不是** offset/pageNum），
 *    传 `null` 会退化成第一页 → 列表开始重复。
 * 2) **`type` 实际有 6 种**：1 赞帖子 / 2 赞评论 / 3 一级评论 / 4 回复评论 / 5 关注 / **6 @提及**。
 *    后端 DTO 注释与 javadoc **只写到 5**，第 6 种只在 `CommunityConstant.NOTIFY_TYPE_MENTION = 6`
 *    → 别按"1..5"写死枚举链，未知值要有兜底（实测 `type=99` 静默回空数组，**不报错**）。
 * 3) **关注类（type 5）的 `targetId` 与 `postId` 都是数字 `0`**（不是 null）且 `content` 是 `null`
 *    → 拼跳转目标前必须判 `> 0`，否则会跳到 `/community/post/0`。
 * 4) **`createTime` 是 ISO-8601 带偏移的 UTC**（`2026-09-20T08:03:15.000+00:00`），
 *    与 healthy 模块的 `yyyy-MM-dd HH:mm:ss`（GMT+8）**是两套口径**
 *    → 通知**不要**复用健康模块那套解析，直接 `new Date(iso)`（带偏移能正确解析成本地时间）。
 * 5) **`unread-count` 是裸数字**（`data: 3`，不是对象），而且**只给总数、不支持按 type 分组**
 *    → 页面不做"按 type 分 Tab 各自的红点"（后端没有分组计数接口）。
 *
 * 写接口（`PUT /{id}/read`、`PUT /read-all`）：按只读红线**从未真调过**（契约 §4.2），
 * 所以这里只写"从 Controller 读到的形状"，**不写"已验证"**；错误文案一律透传后端 message。
 * ==================================================================== */

/** 一条通知（`GET /notification` 的 `records[]`；字段与契约 §2.1 的 DTO 逐字段一致） */
export interface NotificationItem {
  /** 通知 id，**同时就是游标**（全局自增，有断号） */
  notificationId: number
  /** 1 赞帖子 / 2 赞评论 / 3 一级评论 / 4 回复评论 / 5 关注 / 6 @提及（见 `NOTIFICATION_TYPE`） */
  type: number
  actorUid: number
  /** 触发者昵称，**触发时的快照**（后端冗余存，本人改名不会回填） */
  actorUsername: string | null
  /** 帖子 id 或评论 id；**关注类是数字 `0`** */
  targetId: number
  /** 所属帖子 id（跳转用）；**关注类是数字 `0`** */
  postId: number
  /** 内容摘要快照（≤100 字）；**关注类是 `null`** */
  content: string | null
  /** **0 = 未读，1 = 已读**（实测现有数据全是 0） */
  readFlag: number
  /** ISO-8601 带偏移的 UTC，例：`2026-09-20T08:03:15.000+00:00` */
  createTime: string
}

/**
 * 通知类型常量。
 *
 * ⚠️ 这里写全 6 种（后端文档只写到 5，第 6 种 `@提及` 是读源码 + `MentionServiceImpl` 落库确认的）。
 * 值拿来比较用，**不要**用它去遍历——`type` 后端不校验，未知值会静默回空而不是报错。
 */
export const NOTIFICATION_TYPE = {
  POST_LIKE: 1,
  COMMENT_LIKE: 2,
  POST_COMMENT: 3,
  COMMENT_REPLY: 4,
  FOLLOW: 5,
  MENTION: 6,
} as const

/** 通知每页条数（`size` 是请求值；后端会把 0 归一成 10、>50 截成 50，这里固定 20） */
export const NOTIFICATION_PAGE_SIZE = 20

/**
 * 标记已读后广播的事件名。
 *
 * 为什么需要它：**本页不允许新建 Pinia store**（避开 `check-stores.js` 的账号清理门禁），
 * 所以"页面里的未读数"与"导航铃铛的红点"是两个组件各自的本地状态。
 * 不共享状态又想让红点在"全部已读"之后立刻消失，就在成功后广播一次，导航监听即可。
 * 这不是轮询、也不是全局 store，只是一次性的组件间通知。
 */
export const NOTIFICATION_READ_EVENT = 'mw-notifications-read'

/**
 * `readFlag` → 是否未读。
 *
 * 口径：**0 = 未读**，1 = 已读。用 `!== 1` 而不是 `=== 0`：缺字段或异常值按"未读"处理，
 * 宁可多显示一个红点，也不要把一条真未读静默当成已读（用户会漏消息）。
 */
export function notificationUnread(readFlag: number | null | undefined): boolean {
  return Number(readFlag) !== 1
}

/** 不是"帖子类"通知的类型集合（关注类跳用户主页，其余都跳帖子详情） */
export function isPostNotificationType(type: number | null | undefined): boolean {
  const t = Number(type)
  return (
    t === NOTIFICATION_TYPE.POST_LIKE ||
    t === NOTIFICATION_TYPE.COMMENT_LIKE ||
    t === NOTIFICATION_TYPE.POST_COMMENT ||
    t === NOTIFICATION_TYPE.COMMENT_REPLY ||
    t === NOTIFICATION_TYPE.MENTION
  )
}

/**
 * 触发者昵称。
 *
 * 为什么不用 `authorName()`：那条会退成「匿名用户」，而后端**从不返回这种占位**
 * （`actor_username` 是触发时的快照且 NOT NULL）。这里与 `profileName()` 同口径。
 */
export function notificationActorName(n: NotificationItem | null | undefined): string {
  const name = (n?.actorUsername ?? '').trim()
  if (name) return name
  const uid = Number(n?.actorUid)
  return uid > 0 ? `喵友 #${uid}` : '有人'
}

/**
 * 通知主文案（6 种 type 全覆盖 + 未知 type 兜底）。
 *
 * ⚠️ **type 2（赞了你的评论）与 type 4（回复了你的评论）点进去只能到帖子**：
 * 后端没有"按 commentId 查评论"的接口（评论是游标分页），所以文案不承诺"定位到那条评论"。
 * type 6 的 `targetId === postId` 表示是**帖子内**的 @，否则是**评论内**的 @（同一套 type）。
 */
export function notificationText(n: NotificationItem | null | undefined): string {
  if (!n) return ''
  const who = notificationActorName(n)
  switch (Number(n.type)) {
    case NOTIFICATION_TYPE.POST_LIKE:
      return `${who} 赞了你的帖子`
    case NOTIFICATION_TYPE.COMMENT_LIKE:
      return `${who} 赞了你的评论`
    case NOTIFICATION_TYPE.POST_COMMENT:
      return `${who} 评论了你的帖子`
    case NOTIFICATION_TYPE.COMMENT_REPLY:
      return `${who} 回复了你的评论`
    case NOTIFICATION_TYPE.FOLLOW:
      return `${who} 关注了你`
    case NOTIFICATION_TYPE.MENTION: {
      const postId = Number(n.postId)
      const inPost = postId > 0 && Number(n.targetId) === postId
      return inPost ? `${who} 在帖子里 @ 了你` : `${who} 在评论里 @ 了你`
    }
    default:
      return `${who} 和你有了新互动`
  }
}

/** 跳转目标（路由名 + 参数；**用名字不用路径**，路径改了这里不用跟着改） */
export type NotificationTarget =
  | { name: 'community-post'; params: { postId: number } }
  | { name: 'user-profile'; params: { uid: number } }

/**
 * 通知 → 跳转目标。拿不到安全目标时返回 `null`（页面渲成"不可点"，不是偷偷跳个错页）。
 *
 * - 帖子类（1/2/3/4/6）→ `community-post`（`/community/post/:postId`）
 *   ⚠️ 评论类**无法定位到具体评论**（没有按 commentId 查评论的接口），跳到帖子即可，不要假装能定位。
 * - 关注类（5）→ `user-profile`（`/user/:uid`），用 `actorUid`；
 *   ⚠️ **`targetId`/`postId` 都是 `0`，绝不能拿去拼 URL**（会变成 `/community/post/0`）。
 * - 未知 type → `null`（后端 type 不校验，将来加新类型时这里不猜）。
 */
export function notificationTarget(
  n: NotificationItem | null | undefined,
): NotificationTarget | null {
  if (!n) return null
  const type = Number(n.type)
  if (type === NOTIFICATION_TYPE.FOLLOW) {
    const uid = Number(n.actorUid)
    return uid > 0 ? { name: 'user-profile', params: { uid } } : null
  }
  if (isPostNotificationType(type)) {
    const postId = Number(n.postId)
    return postId > 0 ? { name: 'community-post', params: { postId } } : null
  }
  return null
}

/**
 * 内容摘要（`content` **可为 null**，关注类恒为 null）。
 * 返回 `''` 而不是 `'null'`/`undefined` —— 列表里要能直接 `v-if` 掉这一段。
 * 后端已截到 100 字，这里再兜一次（防御手工写入的超长内容）。
 */
export function notificationExcerpt(
  n: NotificationItem | null | undefined,
  max = 100,
): string {
  const text = (n?.content ?? '').replace(/\s+/g, ' ').trim()
  if (!text) return ''
  return text.length > max ? `${text.slice(0, max)}…` : text
}

/**
 * 通知时间 → 本地时区的相对/绝对文案。
 *
 * ⚠️ **直接 `new Date(iso)`**：`createTime` 是带 `+00:00` 偏移的 ISO-8601，`Date` 能正确解析；
 * 健康模块那套"非 ISO 的 GMT+8 字符串补 T"的解析**不能**用在这里（会把 UTC 当本地时间，差 8 小时）。
 *
 * `nowMs` 只是给断言用的注入点（默认当前时间），不影响页面调用。
 */
export function notificationTimeText(
  iso: string | null | undefined,
  nowMs: number = Date.now(),
): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return String(iso)
  const p = (n: number) => String(n).padStart(2, '0')
  const hm = `${p(d.getHours())}:${p(d.getMinutes())}`
  const day = (x: Date) => `${x.getFullYear()}-${p(x.getMonth() + 1)}-${p(x.getDate())}`
  const now = new Date(nowMs)
  if (day(d) === day(now)) return hm
  const yesterday = new Date(nowMs)
  yesterday.setDate(yesterday.getDate() - 1)
  if (day(d) === day(yesterday)) return `昨天 ${hm}`
  if (d.getFullYear() === now.getFullYear()) return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${hm}`
  return `${day(d)} ${hm}`
}

/**
 * 空态文案：**"没有通知"与"筛选后为空"必须说两句话**。
 * 两者混成一句，用户会以为自己的消息被系统吞了（其实是筛选条件挡的）。
 */
export function notificationEmptyText(filtered: boolean): { title: string; desc: string } {
  return filtered
    ? { title: '没有符合筛选的通知', desc: '试试关掉「只看未读」，看过的消息还在列表里。' }
    : { title: '还没有收到通知', desc: '有人点赞、评论、关注或 @ 你的时候，会出现在这里。' }
}

/**
 * `unread-count` 的**裸数字**解析（`data: 3`，不是对象）。
 *
 * 为什么单独抽出来：这个接口返回的不是信封里常见的对象，直接 `res.xxx` 会得到 `undefined`
 * （然后红点永远不亮，且不报错）。异常值一律收敛成 `0`，红点宁可不亮也不要显示 `NaN`。
 */
export function unreadCountOf(raw: unknown): number {
  const n = Number(raw)
  if (!Number.isFinite(n) || n <= 0) return 0
  return Math.floor(n)
}

/* ---------- 通知的四个接口（三个读 + 两个写里的两个 PUT，见文件头说明） ---------- */

/**
 * 通知列表（**游标分页**，按 `notificationId` 倒序，新的在前）。
 *
 * - `lastId` 传上一次返回的 `nextCursor`，**原样**，不要自己 ±1 或拿 `records.length` 拼
 * - `unreadOnly` 只在**筛选**时传：实测 `unreadOnly=false` 与不传等价，所以不筛选时传 `undefined`，
 *   避免 URL 上多一个没意义的参数（也让断言能区分"筛选"与"没筛选"）
 * - `type` 后端支持但不校验（非法值静默回空）→ 本页**不做按 type 分 Tab**，这个参数留给以后
 */
export function listNotifications(
  params: {
    lastId?: number | null
    size?: number
    type?: number | null
    unreadOnly?: boolean
  } = {},
) {
  return api.get<CursorPage<NotificationItem>>('community', `${BASE}/notification`, {
    query: {
      lastId: params.lastId ?? undefined,
      size: params.size,
      type: params.type ?? undefined,
      unreadOnly: params.unreadOnly === true ? true : undefined,
    },
  })
}

/** 未读总数（**裸数字**，只有总数**没有按 type 分组**）；未登录回网关 `state:-1` */
export async function getUnreadCount(): Promise<number> {
  const raw = await api.get<number | string>('community', `${BASE}/notification/unread-count`)
  return unreadCountOf(raw)
}

/**
 * 标记单条已读（`PUT`，返回 `data` = null）。
 *
 * **未实测**（写操作，只读红线）。按后端实现推断的语义（契约 §2.1 坑 4）：
 * 归属校验写在 WHERE 里 → 已经是已读时 `updated=0` 再查一次归属，**查得到 → 静默成功**（幂等）；
 * 查不到（不存在 / 别人的）→ `B000317 通知不存在或无权操作`。
 * → 页面做"乐观更新 + 失败回滚"，且**不把 `B000317` 弹给用户**（那是数据不一致，不是用户的错）。
 */
export function markNotificationRead(notificationId: number) {
  return api.put<null>('community', `${BASE}/notification/${notificationId}/read`)
}

/** 全部标为已读（`PUT`，返回 `data` = null）。同样是**未实测**的写接口，幂等 */
export function markAllNotificationsRead() {
  return api.put<null>('community', `${BASE}/notification/read-all`)
}

/** 通知错误码 → 人话：先接住通知特有的 `B000317` 与未登录，其余交给 `communityErrorMessage` */
export function notificationErrorMessage(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.isUnauthorized) return '登录状态失效了，重新登录后再看消息通知'
    if (e.code === 'B000317') return e.message || '这条通知不存在或无权操作'
  }
  return communityErrorMessage(e)
}

/* ==================================================================== *
 * 自己的资料（社区侧的那一份）
 *
 * 2026-09-21 追加，**上面既有导出一个字节都没动**（纯追加段，段首 banner 划界）。
 *
 * 为什么「个人资料」页要读社区这一路：**uid 只能从这里来**。
 * `GET /user/{username}` 的 `data.id` 恒为 null（DO 叫 uid、DTO 叫 id，拷贝对不上，挂账 #14），
 * user 模块也没有"按 uid 查"的接口（挂账）。社区这条不带 `uid` 时**回落成登录 uid**
 * （`ProfileServiceImpl.java:47`：`uid == null ? currentLoginUid() : uid`）。
 *
 * ⚠️ 与 `getUserProfile(uid)` 的区别（**别混用**）：
 * - 本函数**不传 uid** → 是"我自己"，唯一用途就是拿 uid / self / 计数；
 * - `getUserProfile(uid)` 传 uid → 是"看别人"，`uid` 在类型上是必填（不传会静默变成看自己）。
 * ==================================================================== */

/**
 * 我自己的主页头部（不带 `uid` → 后端回落成登录 uid）。
 *
 * 实测 fixture `cu-profile-nouid-self.json`：`{uid:7, username:'probe114904', self:true,
 * followed:false, postCount:5, likeReceivedCount:3, followingCount:1, followerCount:0}`
 * —— **没有 avatar 字段**（社区所有 DTO 都没有头像，这是"个人资料页不画假头像"的社区侧依据）。
 */
export function getMySelfProfile() {
  return api.get<UserProfile>('community', `${BASE}/user/profile`)
}