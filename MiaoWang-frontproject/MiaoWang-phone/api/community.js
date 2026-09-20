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
 * ⚠️ 每读一次后端会把 `viewCount` +1（不是幂等读）。
 * 不存在 → `B000301`。
 */
export function getPost (postId) {
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
 * 写接口（点赞 / 收藏）
 *
 * 契约：`POST` 点赞、`DELETE` 取消；**幂等**（唯一键 + 捕 DuplicateKeyException，2026-09-19 真账号实测：
 * 连发两次一律 HTTP 200 + `code:"0"`，计数不重复累加），帖子不存在或已删 → `B000301`。返回 `data` = null。
 *
 * ⚠️ 本端**从未真调过**这两组（后端已实测通过）。页面按乐观更新 + 失败回滚处理。
 * ------------------------------------------------------------------ */
export function likePost (postId) {
  return api.post(MODULE, '/community/like/' + postId)
}

export function unlikePost (postId) {
  return api.del(MODULE, '/community/like/' + postId)
}

export function favoritePost (postId) {
  return api.post(MODULE, '/community/favorite/' + postId)
}

export function unfavoritePost (postId) {
  return api.del(MODULE, '/community/favorite/' + postId)
}