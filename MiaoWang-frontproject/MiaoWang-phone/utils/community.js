/**
 * 社区展示工具（手机端 / JS）
 *
 * 为什么单独一个文件：手机端模板**不能调 utils / store**（Options API 的模板只认实例属性，
 * 渲染期访问不到 import 进来的对象会直接报错并白屏）。所以这里只放**纯函数**，
 * 由页面 `computed` 调一次，把原始 DTO 摊平成模板可以直接 `{{ }}` 的字段。
 *
 * 口径全部来自 `.dsh-tmp/community-contract.md` 的**实测更新（2026-09-19，有真实数据后）**，
 * **不要按 REST 惯例补字段**：
 * - 列表 DTO（PostRespDTO）**没有 `content`** → 卡片只能靠 `title` + `coverUrl` + 计数 + 话题
 * - 所有社区 DTO **都没有头像字段** → 头像位只能拿显示名首字（不是"没加载出来"）
 * - `username` **只在社区里没有该用户的内容时为 null**（实测 uid=7 有真值 `probe114904`、uid=1 为 null）
 *   → 按 `uid` 兜底必须留着，界面上**不许出现 null**
 * - 列表的 `videos` 无视频时是**空数组**（不是 null）；`topics` 无话题时是**空数组**；
 *   `coverUrl` 无图时是 **null**
 * - 详情 DTO **没有 `isTop`**、没有 `coverUrl`（封面 = images 按 sort 升序的第 0 条，`sort` 从 **0** 起）
 * - ⚠️ **类型不对称**：同一个列表 DTO 里 `liked` / `favorited` 是**布尔**，`isTop` 是**数字 `0`/`1`**
 *   → 一律**按真值判断**，禁止 `=== true` / `=== 1` 这类严格比较
 */

function countOf (value) {
  const n = Number(value)
  return isNaN(n) || n < 0 ? 0 : n
}

function pad (n) {
  return n < 10 ? '0' + n : String(n)
}

function formatDate (d) {
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
}

/**
 * 相对时间：刚刚 / x 分钟前 / x 小时前 / x 天前 / 超过一周给日期。
 * 后端的 `createTime` 是 Jackson 默认序列化的 Date（ISO-8601 带时区），统一交给 `new Date()` 解析。
 */
export function relativeTime (value) {
  if (!value) return ''
  const d = new Date(value)
  if (isNaN(d.getTime())) return ''
  const diff = Date.now() - d.getTime()
  if (diff < 0) return formatDate(d)
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + ' 分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + ' 小时前'
  if (diff < 604800000) return Math.floor(diff / 86400000) + ' 天前'
  return formatDate(d)
}

/**
 * 作者显示名：`username` 优先，为空按 `uid` 兜底。
 * 实测 `username` 只在"该用户在社区里没有任何帖子/评论"时为 null，直接渲染会看到 "null"。
 */
export function authorLabel (item) {
  const o = item || {}
  const name = o.username === null || o.username === undefined ? '' : String(o.username).trim()
  if (name) return name
  const uid = o.uid
  if (uid !== null && uid !== undefined && uid !== '') return '用户 ' + uid
  return '匿名用户'
}

/** 没有头像字段 → 拿显示名首字当头像（空名兜底一个"宠"） */
export function initialOf (label) {
  const s = label === null || label === undefined ? '' : String(label).trim()
  return s ? s.slice(0, 1) : '宠'
}

/** 标题：可以为空（后端允许「只有图片/视频、没有标题」的帖子），别显示空白卡片 */
export function titleOf (value) {
  const s = value === null || value === undefined ? '' : String(value).trim()
  return s || '无标题帖子'
}

/** topics[] → 名字数组（DTO 里的 `name` 不带 `#`，渲染时前端自己加） */
export function topicNames (topics) {
  if (!Array.isArray(topics)) return []
  const out = []
  for (let i = 0; i < topics.length; i++) {
    const t = topics[i]
    const name = t && t.name ? String(t.name).trim() : ''
    if (name) out.push(name)
  }
  return out
}

/** 详情里的 images / videos 都带 sort，按 sort 升序排（后端里封面就是 images 的第 0 条） */
function sortedBySort (rows) {
  if (!Array.isArray(rows)) return []
  return rows.slice().sort(function (a, b) {
    const sa = Number(a && a.sort)
    const sb = Number(b && b.sort)
    return (isNaN(sa) ? 0 : sa) - (isNaN(sb) ? 0 : sb)
  })
}

/** 详情 images[] → 可渲染的 url 数组（缺 url 的项丢掉，别渲染空图） */
export function imageUrls (images) {
  const out = []
  const sorted = sortedBySort(images)
  for (let i = 0; i < sorted.length; i++) {
    const url = sorted[i] && sorted[i].url
    if (url) out.push(String(url))
  }
  return out
}

/** 详情 videos[] → 可渲染的 url 数组 */
export function videoUrls (videos) {
  const out = []
  const sorted = sortedBySort(videos)
  for (let i = 0; i < sorted.length; i++) {
    const url = sorted[i] && sorted[i].url
    if (url) out.push(String(url))
  }
  return out
}

/**
 * 一条回复 → 模板字段（回复项的 `children` / `replyCount` 实测为 **null**，这里根本不读它们）
 * @param {object} reply CommentRespDTO（二级）
 */
export function replyCard (reply) {
  const r = reply || {}
  const author = authorLabel(r)
  return {
    commentId: r.commentId,
    author: author,
    initial: initialOf(author),
    content: r.content === null || r.content === undefined ? '' : String(r.content),
    timeText: relativeTime(r.createTime),
    liked: !!r.liked,
    likeText: '赞 ' + countOf(r.likeCount),
    /** 「回复 某某」：后端给的是 replyUsername（也可能为 null）+ replyUid */
    replyToText: replyToLabel(r)
  }
}

function replyToLabel (r) {
  const name = r.replyUsername === null || r.replyUsername === undefined ? '' : String(r.replyUsername).trim()
  if (name) return '回复 ' + name
  const uid = r.replyUid
  if (uid !== null && uid !== undefined && uid !== '') return '回复 用户 ' + uid
  return ''
}

/**
 * 一条一级评论 → 模板字段
 * @param {object} comment CommentRespDTO
 */
export function commentCard (comment) {
  const c = comment || {}
  const author = authorLabel(c)
  // 一级评论无回复时 `children` 实测是 `[]`（不是 null）；回复项才是 null → 一律判空
  const children = Array.isArray(c.children) ? c.children : []
  return {
    commentId: c.commentId,
    author: author,
    initial: initialOf(author),
    content: c.content === null || c.content === undefined ? '' : String(c.content),
    timeText: relativeTime(c.createTime),
    liked: !!c.liked,
    likeText: '赞 ' + countOf(c.likeCount),
    /** 一级评论无回复时实测是数字 0（不是 null）；回复项才是 null → countOf 统一兜成 0 */
    replyCount: countOf(c.replyCount),
    replyToText: '',
    /** 接口自带**正序**回复预览；"最多 3 条"不是硬契约，按数组实际内容渲染 */
    children: children.map(replyCard)
  }
}

/**
 * 信息流一条帖子 → 模板字段（**列表没有正文，这里也绝不编正文**）
 * @param {object} post PostRespDTO
 */
export function postCard (post) {
  const p = post || {}
  const author = authorLabel(p)
  const topics = topicNames(p.topics)
  const videos = Array.isArray(p.videos) ? p.videos : []
  const cover = p.coverUrl ? String(p.coverUrl) : ''
  return {
    postId: p.postId,
    title: titleOf(p.title),
    author: author,
    initial: initialOf(author),
    timeText: relativeTime(p.createTime),
    /**
     * 列表 DTO 有 isTop（详情 DTO 没有），实测是**数字** `0`/`1` → **按真值判断**。
     * 别写 `=== true` / `Number(p.isTop) === 1`：严格比较是"源码推断成布尔"时的写法，
     * 真数据回来就是 `0`，一旦后端换成别的真值（例如 `2`）又会判错。
     */
    isTop: !!p.isTop,
    coverUrl: cover,
    hasCover: !!cover,
    topics: topics,
    hasTopics: topics.length > 0,
    /** 列表不带 images，只带 coverUrl；视频只给个数量提示，列表里不渲染播放器 */
    videoText: videos.length ? '含 ' + videos.length + ' 个视频' : '',
    liked: !!p.liked,
    favorited: !!p.favorited,
    likeText: '赞 ' + countOf(p.likeCount),
    commentText: '评论 ' + countOf(p.commentCount),
    favoriteText: '收藏 ' + countOf(p.favoriteCount),
    viewText: countOf(p.viewCount) + ' 次浏览'
  }
}

/**
 * 帖子详情 → 模板字段
 *
 * 交互状态（点赞/收藏）单独走 `inter`：页面按"乐观更新 + 失败回滚"改它，
 * 详情 DTO 本身不再回写（避免两处真源）。
 *
 * @param {object} detail PostDetailRespDTO
 * @param {object} inter `{ liked, favorited, likeCount, favoriteCount, viewCount, commentCount }`
 */
export function postDetailView (detail, inter) {
  const d = detail || {}
  const i = inter || {}
  const author = authorLabel(d)
  const images = imageUrls(d.images)
  const videos = videoUrls(d.videos)
  const topics = topicNames(d.topics)
  const content = d.content === null || d.content === undefined ? '' : String(d.content)

  const liked = i.liked === undefined || i.liked === null ? !!d.liked : !!i.liked
  const favorited = i.favorited === undefined || i.favorited === null ? !!d.favorited : !!i.favorited
  const likeCount = i.likeCount === undefined || i.likeCount === null ? countOf(d.likeCount) : countOf(i.likeCount)
  const favoriteCount = i.favoriteCount === undefined || i.favoriteCount === null ? countOf(d.favoriteCount) : countOf(i.favoriteCount)
  const viewCount = i.viewCount === undefined || i.viewCount === null ? countOf(d.viewCount) : countOf(i.viewCount)
  const commentCount = i.commentCount === undefined || i.commentCount === null ? countOf(d.commentCount) : countOf(i.commentCount)

  return {
    title: titleOf(d.title),
    author: author,
    initial: initialOf(author),
    timeText: relativeTime(d.createTime),
    content: content,
    hasContent: !!content,
    images: images,
    hasImages: images.length > 0,
    videos: videos,
    hasVideos: videos.length > 0,
    topics: topics,
    hasTopics: topics.length > 0,
    liked: liked,
    favorited: favorited,
    likeCount: likeCount,
    favoriteCount: favoriteCount,
    likeText: (liked ? '已赞 ' : '赞 ') + likeCount,
    favoriteText: (favorited ? '已收藏 ' : '收藏 ') + favoriteCount,
    viewText: viewCount + ' 次浏览',
    /** `commentCount` **把回复也算进去**（实测 178：2 一级 + 2 回复 = 4）→ 文案写明"含回复" */
    commentText: '评论 ' + commentCount + ' 条（含回复）'
  }
}

/**
 * 社区错误码 → 人话（唯一真源：契约 §2 的 `CommunityErrorCodeEnum` 全表）
 *
 * 后端 message 本身就是中文，这里只覆盖"能给出更具体下一步"的那几条。
 * 特别注意 `B000001`：未映射路径 / 方法不匹配 / 参数类型转换失败**都被
 * GlobalExceptionHandler 吞成它**（挂账 #48），所以它不是"服务崩了"的同义词，
 * 文案要同时给出"路径或参数不对"和"稍后重试"两条路。
 */
export function communityErrorMessage (e) {
  if (!e) return '社区服务出错了，稍后再试'
  const code = e.code ? String(e.code) : ''
  if (code === 'UNAUTHORIZED' || e.isUnauthorized) return '登录已过期，请重新登录'
  if (code === 'NETWORK') return e.message || '连不上后端，检查网络或网关是否已启动'
  const MAP = {
    B000300: '这个版块不存在或已停用，换一个版块看看',
    B000301: '帖子不存在或已被删除',
    B000302: '只能操作自己的帖子',
    B000303: '一个帖子最多 9 张图片',
    B000304: '标题、正文、图片、视频不能同时为空',
    B000305: '请选择版块',
    B000308: '图片超出大小限制',
    B000310: '登录已过期，请重新登录',
    B000311: '评论不存在或已被删除',
    B000312: '只有评论作者或楼主可以删这条评论',
    B000313: '评论不能为空',
    B000314: '评论太长了，最多 1000 字',
    B000315: '要回复的评论不存在，或不属于这个帖子',
    B000316: '不能关注自己',
    B000317: '通知不存在或无权操作',
    B000318: '内容里有敏感词，改一下再试',
    B000319: '这个目标不存在或对你不可见',
    B000320: '不能举报自己',
    B000321: '举报理由不合法',
    B000322: '举报说明太长了，最多 200 字',
    B000323: '你已经举报过这一条了，不用重复举报',
    B000326: '话题不存在',
    B000327: '搜索关键词为空或太长（最多 50 字）',
    B000328: '视频格式不支持，只支持 mp4 与 mov',
    B000329: '视频文件是空的',
    B000330: '视频超出大小限制',
    B000331: '视频参数不完整（url 与 objectKey 要成对）',
    B000332: '一个帖子最多 9 个视频',
    B000001: '后端返回「系统执行出错」。常见原因是请求的路径或参数不对，也可能是社区服务正在重启，稍后重试一次'
  }
  return MAP[code] || e.message || '社区服务出错了，稍后再试'
}