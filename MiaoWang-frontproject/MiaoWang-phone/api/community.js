/**
 * 社区接口（手机端 / JS）
 *
 * 契约唯一来源：`.dsh-tmp/community-contract.md`（20+ 条 GET 实测 + 写接口源码推断）。
 * 三条最容易踩的坑直接体现在这里，**不要按 REST 惯例猜**：
 *
 * 1) 经网关调社区，**每一个接口（连 `/section` 都要）必须同时带 `token` + `username` 两个头**：
 *    匿名被网关拦成 `{"state":-1}`，缺 `username` 头 → 下游 `B000310`。
 *    两个头由 `utils/request.js` 自动带上（`state:-1` 与 `B000310` 已归一成 `ApiError.isUnauthorized`），
 *    本文件不碰鉴权。
 * 2) **两套分页信封**：
 *    - 信息流 `/community/post` 是**页码分页** `{pageNum,pageSize,total,pages,records}`（pageSize 上限 50）
 *    - 评论 `/community/comment` 与回复 `/community/comment/reply` 是**游标分页**
 *      `{records,size,hasMore,nextCursor}`，**没有 total**；`nextCursor` 原样回传给 `lastId`，
 *      不要自己加减（各接口游标方向不同：评论是 `< commentId` 倒序，回复是 `> commentId` 正序）
 * 3) **列表 DTO 没有正文**（只有 `title` + `coverUrl` + 计数 + `topics` + `videos`）、
 *    **所有社区 DTO 都没有头像字段**、`username` 实测**在该用户社区无内容时才为 null**（按 uid 兜底）、
 *    **没有"推荐"版块**（不传 `sectionId` 才是全站流）。
 *    ⚠️ 同一个列表 DTO 里 `liked`/`favorited` 是**布尔**、`isTop` 是**数字 0/1**（类型不对称）。
 *    展示口径统一在 `utils/community.js`，页面不自己判。
 *
 * 只封装页面真正要用的接口。**刻意不封装发帖 / 发评论**：写路径没有在本端真调过，
 * 需要时照契约的 `POST /community/post`、`POST /community/comment` 补在本文件即可
 * （后端已由真实账号实测通过，含幂等与 8 条错误码）。
 */
import { api } from '../utils/request.js'

const MODULE = 'community'

/** 信息流一页多少条（后端 pageSize 上限 50，传 100 会被截成 50） */
export const PAGE_SIZE = 10
/** 评论 / 回复一页多少条 */
export const CURSOR_SIZE = 10
/**
 * 详情页「一条不落地拉完评论」每次请求的 `size`。
 * 🔴 用**后端允许的上限**（`MAX_PAGE_SIZE = 50`，`CommentServiceImpl.java:380` 的 `normalizeSize`
 * 会把超过 50 的截成 50）—— 用户要求详情页**默认显示所有评论**，
 * 而接口是游标分页、一次最多 50 条 → 只能**循环续拉**（`detail.vue` 的 `loadComments`），
 * 每页都取满 50 能把往返次数压到最少。
 * ⚠️ **没有**"一次拿全部"的参数；超过页数上限时前端**如实说明没拉完**，不假装"全部"。
 */
export const COMMENT_PAGE_SIZE = 50
/**
 * 评论内容长度上限（`CommunityConstant.MAX_COMMENT_LENGTH = 1000`，后端 `CommentServiceImpl.java:74`
 * 超过回 `B000314`）。手机端**只用它做输入框 `maxlength`**，不做"本地截断后静默发送"——
 * 唯一权威是后端，超长的话让后端照实报错。
 */
export const COMMENT_MAX_LENGTH = 1000

/** 版块列表：SectionRespDTO[] = {sectionId,name,code,description,iconUrl,sort}（实测 6 条，iconUrl 全 null） */
export function listSections () {
  return api.get(MODULE, '/community/section')
}

/**
 * 帖子流（**页码分页**）
 * @param {object} p `{ sectionId, pageNum, pageSize }` —— `sectionId` 不传 = 全站流
 * @returns {Promise<{pageNum:number,pageSize:number,total:number,pages:number,records:object[]}>}
 */
export function listPosts (p) {
  const o = p || {}
  return api.get(MODULE, '/community/post', {
    query: {
      sectionId: o.sectionId,
      pageNum: o.pageNum || 1,
      pageSize: o.pageSize || PAGE_SIZE
    }
  })
}

/**
 * 帖子详情（PostDetailRespDTO）
 * 有 `content` / `images[{url,objectKey,sort}]` / `videos[{url,objectKey}]` / `topics[]`；
 * **没有 `isTop`**（置顶标记只在列表 DTO 上）、没有 `coverUrl`（封面 = images 按 sort 升序的第 0 条，
 * `sort` 实测从 **0** 起）；`videos` 无视频时是 `[]`。
 * ⚠️ 每读一次后端**默认**会把 `viewCount` +1（不是幂等读）。2026-09-21 定稿：详情多了请求参数
 * `countView`（**默认 `true`** = 上面的老行为；传 `false` 则一次都不写 `view_count`，响应里也不 +1），
 * 依据 `CommunityPostController.java:68-77`。
 * **不传 = 默认 `true`**（详情页进帖子照旧计一次浏览）。
 * 🔴 **卡片「内容展开」必须传 `opts.countView = false`**：那只是"在列表里把正文摊开看"，
 * 不是"进帖子看" —— 不传就是"点一下展开涨一次浏览量"（把用户的浏览数据污染掉）。
 * 不存在 → `B000301`。
 *
 * @param {number} postId 帖子 id
 * @param {object} [opts] `{ countView }`；`countView === false` 时 URL 里带 `countView=false`
 *   （只有显式传 `false` 才带 —— 传 `undefined`/`true` 与从前一字不差）
 */
export function getPost (postId, opts) {
  const o = opts || {}
  if (o.countView === false) {
    return api.get(MODULE, '/community/post/' + postId, { query: { countView: false } })
  }
  return api.get(MODULE, '/community/post/' + postId)
}

/**
 * 某个帖子的一级评论（**游标分页**，按 `commentId` **倒序**，新的在前）
 * 每条带 `children`（**正序**回复预览，条数不保证）与 `replyCount`（该评论下全部回复数）；
 * `postId` 缺省不报错，只返回空页。
 * @param {object} p `{ postId, lastId, size }`
 */
export function listComments (p) {
  const o = p || {}
  return api.get(MODULE, '/community/comment', {
    query: { postId: o.postId, lastId: o.lastId, size: o.size || CURSOR_SIZE }
  })
}

/**
 * 某条评论的全部回复（**游标分页**，按 `commentId` **正序**，旧的在前）
 * ⚠️ 与一级评论列表**反向**，界面别假设"回复最新在前"。
 * @param {object} p `{ commentId, lastId, size }`
 */
export function listReplies (p) {
  const o = p || {}
  return api.get(MODULE, '/community/comment/reply', {
    query: { commentId: o.commentId, lastId: o.lastId, size: o.size || CURSOR_SIZE }
  })
}

/* ------------------------------------------------------------------
 * 发评论（**用户 2026-09-21 定稿：评论的看与发都在详情页**，卡片上只显示条数）
 *
 * 契约（后端 `CommunityCommentController.java:39` + `CommentCreateReqDTO`）：
 * - `POST /community/comment`，body `{ postId, content, parentId?, replyUid? }`
 *   （`parentId` 不传/0 = 直接评论帖子；传值 = 回复某条评论，只做两级）
 * - 返回 **`CommentRespDTO`**（就是列表里那种评论对象）→ 前端可**直接插进列表头部**，不用重拉整页
 * - 失败码（如实透传给用户，别自己编）：空内容 `B000313`、超 1000 字 `B000314`
 *   （`CommunityConstant.MAX_COMMENT_LENGTH = 1000`）、敏感词 `B000318`、父评论无效 `B000315`、
 *   未登录 `B000310`、帖子不存在/未发布/被拉黑 `B000301`
 * ------------------------------------------------------------------ */
export function createComment (p) {
  const o = p || {}
  const body = { postId: o.postId, content: o.content }
  // 只有真的要回复时才带这两个（不传 = 顶层评论，与后端语义逐字一致）
  if (o.parentId !== undefined && o.parentId !== null && o.parentId !== 0) body.parentId = o.parentId
  if (o.replyUid !== undefined && o.replyUid !== null) body.replyUid = o.replyUid
  return api.post(MODULE, '/community/comment', { body: body })
}

/* ------------------------------------------------------------------
 * 写接口（点赞 / 收藏）
 *
 * 契约：`POST` 点赞、`DELETE` 取消；**幂等**（唯一键 + 捕 DuplicateKeyException，2026-09-19 真账号实测：
 * 连发两次一律 HTTP 200 + `code:"0"`，计数不重复累加），帖子不存在或已删 → `B000301`。返回 `data` = null。
 *
 * ⚠️ 两个方向**后端都存在**（`CommunityInteractionController.java:27/36/45/54`）→ 可做真双向切换，
 * 不是"再点取消"的假交互。必须登录（否则 `B000310`）。
 * 本端**从未真调过**这几组（后端已实测通过）。页面按乐观更新 + 失败回滚处理。
 * ------------------------------------------------------------------ */
export function likePost (postId) {
  return api.post(MODULE, '/community/like/' + postId)
}

export function unlikePost (postId) {
  return api.del(MODULE, '/community/like/' + postId)
}

/* ------------------------------------------------------------------
 * 评论点赞（2026-09-21 用真实账号 `probe114904` **实测过**，见 `.dsh-tmp/community-contract.md`）
 *
 * 实测口径（与帖子点赞**不完全一样**，照抄下面这些，别按帖子那套想当然）：
 * - `POST|DELETE /community/comment/like/{commentId}`，返回 `data` = null，成功 `code:"0"`
 * - 重复点赞 / 未点赞就取消：一律 HTTP 200 + `code:"0"`，**计数不动、也不会变负**（幂等）
 * - 回复项（`parentId>0`）**同样能点赞**，`CommentRespDTO` 对一级评论与回复都带 `liked`
 * - ⚠️ **唯一的不对称**：`POST` 传不存在的评论 → `B000311 评论不存在或已被删除`；
 *   而 **`DELETE` 传不存在的评论回 `code:"0"` 静默成功**（`unlikeComment` 不校验评论是否存在）。
 *   所以"取消点赞"这条路失败一定不是"评论没了"，别把成功当失败、也别指望它报错。
 * - 未登录经网关 → `{"state":-1}`（`utils/request.js` 已归一成 `ApiError.isUnauthorized`）
 * ------------------------------------------------------------------ */
export function likeComment (commentId) {
  return api.post(MODULE, '/community/comment/like/' + commentId)
}

export function unlikeComment (commentId) {
  return api.del(MODULE, '/community/comment/like/' + commentId)
}

export function favoritePost (postId) {
  return api.post(MODULE, '/community/favorite/' + postId)
}

export function unfavoritePost (postId) {
  return api.del(MODULE, '/community/favorite/' + postId)
}

/* ------------------------------------------------------------------
 * 用户主页（2026-09-21 只读实测，脱敏 fixture 见 `tools/userpage-fixtures/`）
 *
 * 契约唯一来源：`.dsh-tmp/knowledge-userpage-contract.md` **§B**。四个「按用户查」的接口
 * **刻意不聚合**（源码注释：头部要得急 / 帖子流滚动加载 / 宠物跨服务可能慢），所以形状**三种并存**：
 *
 *   - `profile` = **裸对象**（无 records / 无 total）
 *   - `post` / `like` = **游标信封** `{records,size,hasMore,nextCursor}`（**没有 total**）
 *   - `pet` = **裸数组**（同组里唯一的例外，**连信封都没有**）
 *
 * 五条实测坑，写页面时照这里的注释走，别按 REST 惯例猜：
 *
 * 1) 🔴 **`pet` 的 `uid` 不传恒 `[]`，不回落成"自己的宠物"**（源码 `uid == null` 直接 emptyList）
 *    —— 而同组的 `profile` / `post` / `like` **不传 uid 才会回落成登录 uid**。
 *    照抄那三个的"uid 可空"写法，主页会**静默显示"他还没有宠物"**（不报错）。
 * 2) ⚠️ **`size` 是归一化后的请求值，不是实际条数**（实测 `size=0`/`-5` → 回显 `10`；`999` → `50`）
 *    → "还有没有下一页"**只能看 `hasMore`**，禁止 `records.length === size`（`size` 会骗人，`records` 也会因
 *    跨服务 / 已删除帖子而少于 size）。
 * 3) ⚠️ **`like` 的游标是 `like_id`（点赞明细表主键），不是 `postId`**（源码推断；只有 3 条数据、
 *    `hasMore` 恒 false，实测没能翻到第二页）→ `nextCursor` **原样回传**给 `lastId`，不要自己加减，
 *    也不要假设它等于上一页最后一条的 `postId`。
 * 4) ⚠️ **"是否已关注"没有独立接口**（`/follow/is-following`、`/follow/check`、`/follow/relation`
 *    实测全是未映射路径返回的 `B000001`）→ 只能取 `profile.followed`（或关注/粉丝列表每行的 `followed`）。
 * 5) ⚠️ 不存在的 uid **不报错**（实测 `uid=999999/0/-1` → `code:"0"` + 全 0）→ 主页会渲染出一个
 *    "看起来存在、只是什么都没发过"的空页；前端**没有可靠办法**从 uid 判断用户是否存在。
 *
 * 另外：`username` 可为 **null**（社区只从帖子表反查昵称 → 从没发过帖的人恒 null，含 uid=1），
 * 展示兜底在 `utils/community.js` 的 `profileName` / `petCard`，页面不自己编。
 * ------------------------------------------------------------------ */

/** 用户主页列表一页多少条（游标；后端把 <1 归一成 10、>50 归一成 50，并用**归一化后的值**回显） */
export const USER_CURSOR_SIZE = 20

/**
 * 主页头部（**裸对象**）
 * `UserProfileRespDTO` = `{uid, username|null, self, followed, postCount, likeReceivedCount,
 * followingCount, followerCount}`（**只有这 8 个字段，没有头像**）
 * @param {number} uid 目标用户；不传 = 登录者自己（本页一律显式传，避免"看错人"）
 */
export function getUserProfile (uid) {
  return api.get(MODULE, '/community/user/profile', { query: { uid: uid } })
}

/**
 * 他发布的帖子（**游标分页**，按 `postId` 倒序）
 * @param {object} p `{ uid, lastId, size }` —— `lastId` 原样回传上一页的 `nextCursor`
 * @returns {Promise<{records:object[],size:number,hasMore:boolean,nextCursor:number|null}>}
 */
export function listUserPosts (p) {
  const o = p || {}
  return api.get(MODULE, '/community/user/post', {
    query: { uid: o.uid, lastId: o.lastId, size: o.size || USER_CURSOR_SIZE }
  })
}

/**
 * 他点赞过的帖子（**游标分页**，信封与 `PostRespDTO` 都和 `listUserPosts` 同一套）
 * ⚠️ 游标语义是 `like_id` 不是 `postId`；已删/下架的帖子会被跳过 → **本页条数可能少于 size**
 * @param {object} p `{ uid, lastId, size }`
 */
export function listUserLikes (p) {
  const o = p || {}
  return api.get(MODULE, '/community/user/like', {
    query: { uid: o.uid, lastId: o.lastId, size: o.size || USER_CURSOR_SIZE }
  })
}

/**
 * 他的宠物（**裸数组**，跨服务；宠物服务挂了会返回 `[]` + WARN，不让主页 500）
 * 🔴 `uid` **必须显式传**（不传恒 `[]` 且不报错，见本段第 1 条坑）
 * `PetBriefRespDTO` = `{pid, pName|null, species, specificSpecies|null, ownerCallName|null,
 * birth, gender(0=雌), sterilizedState(1=已绝育)}`
 */
export function listUserPets (uid) {
  return api.get(MODULE, '/community/user/pet', { query: { uid: uid } })
}

/* ------------------------------------------------------------------
 * 关注 / 取消关注（写接口，**本端从未真调过**）
 *
 * 契约（源码推断，红线禁止写操作 → **未实测**）：
 * - `POST|DELETE /community/follow/{uid}`，路径参数是 uid，返回 `data` = null，成功 `code:"0"`
 * - 幂等：关注靠 `uk_uid_follow` 唯一键捕 `DuplicateKeyException`（重复关注也 `code:"0"`）；
 *   取关删 0 行也算成功
 * - 关注自己 → `ClientException(FOLLOW_SELF)`，**具体码未实测**（既有错误码表里 `B000316` 是"不能关注自己"，
 *   `utils/community.js` 的 `communityErrorMessage` 已覆盖这一条）
 * - 未登录经网关 → `{"state":-1}`（`utils/request.js` 已归一成 `ApiError.isUnauthorized`）
 *
 * 所以页面按**乐观更新 + 失败回滚**处理，并把后端返回的话显示出来（不静默、不按推断写死文案）。
 * ------------------------------------------------------------------ */
export function followUser (uid) {
  return api.post(MODULE, '/community/follow/' + uid)
}

export function unfollowUser (uid) {
  return api.del(MODULE, '/community/follow/' + uid)
}

/* ------------------------------------------------------------------
 * 消息通知（2026-09-21 只读实测；fixture 在 `tools/reminder-fixtures/`）
 *
 * 契约唯一来源：`.dsh-tmp/reminder-notification-contract.md` **§2**
 * （19 个只读 GET 实测 + 通知表只读勘验；离线断言在 `tools/check-notification-phone.mjs`）。
 * 后端 4 个接口（`CommunityNotificationController`）：
 *
 *   GET /community/notification?lastId&size&type?  → **游标信封** `{records,size,hasMore,nextCursor}`
 *   GET /community/notification/unread-count       → **裸数字**（`{"code":"0","data":3}`）
 *   PUT /community/notification/{id}/read          → 写，**本端从未真调**（只读红线）
 *   PUT /community/notification/read-all           → 写，**本端从未真调**（只读红线）
 *
 * `NotificationRespDTO`（11 个字段，无嵌套）：
 *   `notificationId`(游标) / `type` / `actorUid` / `actorUsername`(触发时快照，**可为 null**)
 *   / `targetId`(帖子或评论 id；**关注类 = 0**) / `postId`(**关注类 = 0**) / `content`(**可为 null**)
 *   / `readFlag`(0 未读 1 已读) / `createTime`(ISO-8601 带偏移的 UTC)
 *
 * 八条实测坑（写页面前先读这里，别按 REST 惯例猜）：
 *
 * 1. 🔴 **type 实际 6 种**：1 帖子点赞 / 2 评论点赞 / 3 一级评论 / 4 回复评论 / 5 关注 / **6 @提及**。
 *    DTO 注释与 Controller javadoc 只写到 5（**文档口径过时**：`CommunityConstant.NOTIFY_TYPE_MENTION=6`
 *    且 `MentionServiceImpl` 真的在写）。`type` **不校验**：`type=99` 静默返回空数组、不报错。
 * 2. 🔴 **关注类（5）`targetId = postId = 0`（是 0，不是 null）且 `content = null`**
 *    → **绝不能拿 0 去拼 URL**（`?postId=0` / `?uid=0` 都错），只能跳 `actorUid` 的主页。
 * 3. 🔴 **`actorUsername` 可为 null**（触发时快照）；兜底口径是 **`喵友 #uid`**，
 *    **不许写"匿名用户"**（与 `utils/community.js` 的 `profileName` 同一口径）。
 * 4. ⚠️ **`createTime` 是 ISO-8601 带偏移的 UTC**（`2026-09-20T08:03:15.000+00:00`），库里存 GMT+8。
 *    **不要**复用 `utils/health.js` 的 `parseHealthTime`（那是给"非 ISO 的 GMT+8 字符串"写的）
 *    → 直接 `new Date(iso)`（带偏移能解析成正确瞬间）。
 * 5. ⚠️ **游标方向是"向下取更小"**：`nextCursor` = 本页最后一条的 id；下一页传 `lastId=<cursor>`，
 *    后端按 `id < lastId` 查。第一页**不传** `lastId`；`hasMore=false` 时 `nextCursor=null`，
 *    **别把 null/undefined 传回去**（会退化成第一页、看到重复数据）。
 * 6. ⚠️ **`size` 是归一化后的请求值**（实测 `0` → 回显 10、`999` → 回显 50，而记录集合不变）
 *    → "还有没有下一页"**只认 `hasMore`**，禁止 `records.length === size`。
 * 7. ⚠️ **`unread-count` 是裸数字**，**不是对象、也不支持按 type 分组** → "每个 Tab 各自红点"做不了
 *    （本页也因此**不做按 type 分 Tab**）。
 * 8. ⚠️ **`markRead` 的幂等语义**（源码推断，**未实测**）：归属校验写在 WHERE 里，`updated=0` 时再查一次归属 ——
 *    查得到（= 已经是已读）→ **静默成功**；查不到（不存在 / 别人的）→ `B000317 通知不存在或无权操作`。
 *    所以"已经是已读"不该弹错，`B000317` 才是真错误。
 *
 * 两条产品事实（决定了交互怎么写）：
 * - **取消赞 / 取关 / 删帖都不撤回通知**（`unlike*` / `unfollow` / `deletePost` 里没有任何通知清理）
 *   → "通知说 XX 赞了你，点进去已经没赞了"、"通知指向一个已被删除的帖子"都会真实发生
 *   → 点击必须**优雅降级**（目标缺失就提示，不硬拼 URL；目标页自己会报"帖子不存在或已被删除"）。
 * - **评论类通知无法精确定位到具体评论**（没有"按 commentId 查评论"的接口，评论是游标分页）
 *   → 1/2/3/4/6 一律只跳**帖子详情**，**不假装能定位到评论**。
 * ------------------------------------------------------------------ */

/** 通知列表一页多少条（后端把 <1 归一成 10、>50 归一成 50，并用**归一化后的值**回显） */
export const NOTIF_CURSOR_SIZE = 20

/** 6 种通知类型（数字是接口真值；文档口径只写到 5，第 6 种 @提及 来自源码真值） */
export const NOTIFY_TYPE = {
  LIKE_POST: 1,
  LIKE_COMMENT: 2,
  COMMENT: 3,
  REPLY: 4,
  FOLLOW: 5,
  MENTION: 6
}

/** 能跳到"所属帖子"的类型（1/2/3/4/6）。关注类（5）不在里面：它的 targetId/postId 都是 0 */
export const NOTIFY_POST_TYPES = [1, 2, 3, 4, 6]

/** 空态文案：**没有通知**（与"没有更多"分开，两者不是一件事） */
export const NOTIF_EMPTY_TEXT = '还没有收到消息通知'

/** 目标缺失时的降级文案（取消赞/取关/删帖都不撤回通知，所以这是常态而非异常） */
export const NOTIF_NO_TARGET_TEXT = '这条通知指向的内容已经不在了'

/**
 * 通知列表（**游标分页**，按 `notificationId` 倒序）
 * @param {object} p `{ lastId, size, type }` —— `lastId` **原样回传**上一页的 `nextCursor`（第一页不传）
 * @returns {Promise<{records:object[],size:number,hasMore:boolean,nextCursor:number|null}>}
 */
export function listNotifications (p) {
  const o = p || {}
  return api.get(MODULE, '/community/notification', {
    query: { lastId: o.lastId, size: o.size || NOTIF_CURSOR_SIZE, type: o.type }
  })
}

/** 未读数：**裸数字**（不是对象；只有总数，不支持按 type 分组）；未登录经网关 → `state:-1` */
export function fetchUnreadCount () {
  return api.get(MODULE, '/community/notification/unread-count')
}

/**
 * 单条标记已读（**写接口，本端从未真调**）
 * 幂等：已经是已读 → 静默成功；不存在 / 别人的 → `B000317`。返回 `data` = null。
 */
export function markNotificationRead (notificationId) {
  return api.put(MODULE, '/community/notification/' + notificationId + '/read')
}

/** 全部标记已读（**写接口，本端从未真调**）；返回 `data` = null */
export function markAllNotificationsRead () {
  return api.put(MODULE, '/community/notification/read-all')
}

/* ---------------- 纯函数（页面 computed 调一次，模板只 `{{ }}`；同时便于离线断言） ---------------- */

function pad2 (n) {
  return n < 10 ? '0' + n : String(n)
}

function dateText (d) {
  return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate())
}

/**
 * 触发者显示名：`actorUsername`（**触发时快照，实测可为 null**）→ 空则兜底 **`喵友 #actorUid`**。
 * 🔴 **不写"匿名用户"**：后端从没返回过这个词，写它等于凭空断言"人家匿名"。
 */
export function notificationActorName (item) {
  const o = item || {}
  const name = o.actorUsername === null || o.actorUsername === undefined ? '' : String(o.actorUsername).trim()
  if (name) return name
  const uid = o.actorUid
  if (uid !== null && uid !== undefined && uid !== '') return '喵友 #' + uid
  return '喵友'
}

/** 6 种 type 的动作（**未知 type 不给动作**，见 `notificationText`） */
const NOTIFY_ACTION = {
  1: '赞了你的帖子',
  2: '赞了你的评论',
  3: '评论了你的帖子',
  4: '回复了你的评论',
  5: '关注了你',
  6: '在内容里 @ 了你'
}

const NOTIFY_TYPE_LABEL = { 1: '赞', 2: '赞', 3: '评论', 4: '回复', 5: '关注', 6: '@' }

/** 类型标签（卡片上的小胶囊）；未知 type → 「通知」 */
export function notificationTypeLabel (type) {
  return NOTIFY_TYPE_LABEL[Number(type)] || '通知'
}

/**
 * 一句话文案：`<触发者> <动作>`。
 * ⚠️ 未知 type **不编动作**（后端加新类型时界面上不会出现瞎猜的说法），只说"给你发了一条通知"。
 */
export function notificationText (item) {
  const action = NOTIFY_ACTION[Number((item || {}).type)]
  return notificationActorName(item) + ' ' + (action || '给你发了一条通知')
}

/**
 * 内容摘要（帖子标题 / 被点赞的评论内容 / 评论内容快照）：**可为 null**（关注类恒 null）
 * → 无摘要时返回 `''`（**绝不返回字符串 "null"**，模板上也不会出现空白块）。
 */
export function notificationExcerpt (item) {
  const v = (item || {}).content
  if (v === null || v === undefined) return ''
  const s = String(v)
  return s.trim() ? s : ''
}

/**
 * 通知时间（ISO-8601 **带偏移的 UTC**）→ 相对时间。
 * ⚠️ 一律 `new Date(iso)`：带偏移能被正确解析成绝对瞬间；**别**走 `utils/health.js` 那套
 * "非 ISO 的 GMT+8 字符串"口径（那是另一套数据，混用会整体偏 8 小时）。
 * @param {string} value `createTime`
 * @param {number} [now] 参照时刻（毫秒）；不传取 `Date.now()`，传了便于断言
 */
export function notificationTimeText (value, now) {
  if (!value) return ''
  const d = new Date(value)
  if (isNaN(d.getTime())) return ''
  const base = typeof now === 'number' ? now : Date.now()
  const diff = base - d.getTime()
  if (diff < 0) return dateText(d)
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + ' 分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + ' 小时前'
  if (diff < 604800000) return Math.floor(diff / 86400000) + ' 天前'
  return dateText(d)
}

/**
 * 点击跳哪（**纯函数**，页面的 `jump` 只消费它的结果）：
 *
 * - 关注类（5）→ `/pages/user/profile?uid=<actorUid>`
 *   🔴 `targetId`/`postId` **都是 0**，**不许拿去拼 URL**（`?postId=0` 会打开一个不存在的帖子）。
 * - 帖子类（1/2/3/4/6）→ `/pages/community/detail?postId=<postId>`
 *   ⚠️ 评论类（2/3/4）**无法定位到具体评论**（没有按 commentId 查评论的接口）→ 只跳帖子，不假装能定位。
 * - 目标缺失/为 0（关注类没有 `actorUid`、帖子类没有 `postId`）→ `url: ''`
 *   → 页面给提示、不跳（**优雅降级**：取消赞 / 取关 / 删帖都不撤回通知）。
 *
 * @returns {{kind:'post'|'user'|'none', url:string, postId:number, uid:number}}
 */
export function notificationTarget (item) {
  const o = item || {}
  const type = Number(o.type)
  if (type === NOTIFY_TYPE.FOLLOW) {
    const uid = Number(o.actorUid)
    if (uid > 0) return { kind: 'user', url: '/pages/user/profile?uid=' + uid, postId: 0, uid: uid }
    return { kind: 'none', url: '', postId: 0, uid: 0 }
  }
  // 帖子类（1/2/3/4/6）以及任何"有所属帖子"的未知 type：只按 `postId` 拼 URL。
  // 🔴 关注类的 `targetId`/`postId` 是 0，已经在上面被拦下；这里 `postId <= 0` 一律当"目标没了"。
  const postId = Number(o.postId)
  if (postId > 0) {
    return { kind: 'post', url: '/pages/community/detail?postId=' + postId, postId: postId, uid: 0 }
  }
  return { kind: 'none', url: '', postId: 0, uid: 0 }
}

/**
 * 一条通知 → 模板字段（页面 `computed` 里 map 一次）
 *
 * @param {object} item `NotificationRespDTO`
 * @param {object} [inter] **本地交互态** `{ read, busy }`（乐观更新用；接口 DTO **不回写**，避免两处真源）
 *   `read` 为 null/undefined 时以接口的 `readFlag` 为准（`0` 未读 / `1` 已读）。
 */
export function notificationView (item, inter) {
  const o = item || {}
  const i = inter || {}
  const actor = notificationActorName(o)
  const excerpt = notificationExcerpt(o)
  const target = notificationTarget(o)
  const read = i.read === undefined || i.read === null ? Number(o.readFlag) === 1 : !!i.read
  return {
    id: o.notificationId,
    type: Number(o.type),
    actor: actor,
    /** 全站没有头像字段 → 只画触发者名字首字（挂 `<image>` 就是永远空的那张图） */
    initial: actor.slice(0, 1),
    typeLabel: notificationTypeLabel(o.type),
    text: notificationText(o),
    excerpt: excerpt,
    hasExcerpt: !!excerpt,
    timeText: notificationTimeText(o.createTime),
    read: read,
    /** `readFlag=1` 就不再显示未读样式（唯一判据，见契约 §2.1） */
    unread: !read,
    busy: !!i.busy,
    kind: target.kind,
    url: target.url,
    clickable: !!target.url
  }
}

/** 到底文案：**"没有更多"**（与空态"没有通知"是两件事，别共用一句） */
export function notificationEndText (count) {
  const n = Number(count)
  if (isNaN(n) || n <= 0) return ''
  return '一共 ' + n + ' 条，没有更多了'
}

/** 未读数 → 角标文案（0 / 取不到 → `''`：**不显示"0 条未读"**；超 99 折成 `99+`） */
export function notificationUnreadText (count) {
  const n = Number(count)
  if (isNaN(n) || n <= 0) return ''
  return n > 99 ? '99+ 条未读' : n + ' 条未读'
}

/** 有没有未读（**红点的唯一判据**）；取不到未读数（null/NaN）按"没有"处理 → 失败静默降级 */
export function notificationHasUnread (count) {
  const n = Number(count)
  return !isNaN(n) && n > 0
}

/* ------------------------------------------------------------------
 * 社区搜索（2026-09-21 **只读实测**；fixture 在 `tools/community-search-fixtures/`）
 *
 * ⚠️ 先说背景：这两个接口在门禁 `check-community-api.js`（247 条）里**一条都没被覆盖**
 *    （该文件里 `search` 零命中）→ 它们是"后端真实存在、但从未被验收过"的接口。
 *    本切片用真实账号 `MapleLeaf1` 发了 24 个**只读 GET**把它们钉死。实测口径：
 *
 *   GET /community/post/search?keyword&sectionId?&lastId?&size?
 *     → `Result<CursorPageRespDTO<PostRespDTO>>`，**游标信封** `{records,size,hasMore,nextCursor}`
 *     → 语义：**标题 OR 正文 包含关键词**（`LIKE '%关键词%'`，关键词里的 `%`/`_` 后端已转义）
 *     → 游标方向与帖子流一致（`postId` **倒序**，最新在前）；下一页把上一页的 `nextCursor` 原样传回 `lastId`
 *     → 实测三页（`keyword=猫&size=2`）：`[182,181]`(cursor 181) → `[180,179]`(cursor 179) → `[178]`(cursor **null**)
 *       再往下取（`lastId=178`）→ **空页 + hasMore=false + nextCursor=null** ✅ 收尾干净，不会漏也不会重
 *
 *   三条**实测补充**（派单只说了"空或超 50 字 → B000327"，真实行为更硬）：
 *   1) 🔴 **空关键词不是"可选参数"**：`keyword=''` / 只给空格 / 干脆不传，**一律 `B000327 搜索关键词为空或过长`**
 *      （后端 `StrUtil.isBlank(trim(keyword))` 先拦）→ 前端**空关键词绝不发请求**，这是硬约束不是优化。
 *   2) 🔴 `size` 是**归一化后的请求值**（实测 `size=0` → 回显 `10`；`<1` 归一成 10、`>50` 归一成 50）
 *      → "还有没有下一页"**只认 `hasMore`**，禁止 `records.length === size`。
 *   3) ⚠️ 实测 **只带 `token` 不带 `username` 头也能成功**（`code:"0"`）—— 与本文档开头"缺 username 头 →
 *      `B000310`"那句是**分接口的**；搜索这条路上游没用到 username。别把这条当"以后都可以不带"。
 *
 *   GET /community/topic/search?keyword&size? → `Result<List<TopicRespDTO>>`（**裸数组，不是信封**）
 *     → 语义是**话题名前缀匹配**（后端 `likeRight`，能走 `uk_name` 索引）—— **与帖子搜索的"包含"不是一回事**
 *     → 实测：`keyword=猫` → `[猫咪日常]`；`keyword=猫咪` → `[猫咪日常]`；`keyword=咪` → `[]`
 *       （"咪"在"猫咪日常"里出现过、但**不是前缀** → 正好反证了前缀语义）
 *     → 🔴 **空关键词同样 `B000327`**（"keyword 可选"只是 Spring `required=false` 的字面意思）
 *     → 🔴 裸数组**没有 `size` / `hasMore` / `nextCursor`** → **做不了游标分页**（后端本来就只给前 N 个）
 *     ⚠️ **本端刻意不封装话题搜索**：手机端还没有话题页（后端有 `/topic/{id}/post`，但没有话题落地页），
 *        做了就是"搜了没处去"的假入口 —— 如实记在 `.dsh-tmp/community-search-20260921/` 的报告里。
 *        将来做话题页时再照上面的实测口径补封装（**别照帖子搜索的信封写**）。
 * ------------------------------------------------------------------ */

/** 帖子搜索一页多少条（后端把 <1 归一成 10、>50 归一成 50，并**用归一化后的值回显** `size`） */
export const SEARCH_CURSOR_SIZE = 10

/**
 * 关键词长度上限 = 后端 `CommunityConstant.SEARCH_KEYWORD_MAX_LENGTH = 50`（超了也是 `B000327`）。
 * ⚠️ 前端这一层只是"别白跑一趟"，**不做静默截断**（截断了用户会以为自己搜的就是刚输的那串）。
 */
export const SEARCH_KEYWORD_MAX = 50

/** 空态文案：**这次搜索没有命中任何帖子**（与底部的"没有更多"是两句不同的话，别共用） */
export const SEARCH_EMPTY_TEXT = '没有搜到相关帖子'

/** 还没开始搜时的说明（只讲匹配口径与排序，**不承诺速度**：后端是 LIKE 包含匹配） */
export const SEARCH_INTRO_TEXT = '输入关键词后点「搜索」。匹配的是帖子标题或正文（包含即命中），结果按最新在前排。'

/** 到底文案：**没有更多**（与空态分开；count = 本次已加载的条数） */
export function searchEndText (count) {
  const n = Number(count)
  if (isNaN(n) || n <= 0) return ''
  return '共 ' + n + ' 条，没有更多了'
}

/**
 * 搜帖子（**游标分页**，按 `postId` 倒序）
 * @param {object} p `{ keyword, sectionId, lastId, size }`
 *   - `keyword` **必传且非空**（空 / 全空格 → 后端 `B000327`；页面已在前端拦下，不浪费一次请求）
 *   - `sectionId` 不传 = 搜全部版块（**`0` 不等于"全部"** → 这里 `|| undefined` 把它当"不传"吃掉）
 *   - `lastId` **原样回传**上一页的 `nextCursor`；第一页不传（`buildUrl` 会自动省略空值）
 * @returns {Promise<{records:object[],size:number,hasMore:boolean,nextCursor:number|null}>}
 */
export function searchPosts (p) {
  const o = p || {}
  return api.get(MODULE, '/community/post/search', {
    query: {
      keyword: o.keyword,
      sectionId: o.sectionId || undefined,
      lastId: o.lastId,
      size: o.size || SEARCH_CURSOR_SIZE
    }
  })
}