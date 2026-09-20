/**
 * 社区展示工具（手机端 / JS）
 *
 * 为什么单独一个文件：手机端模板**不能调 utils / store**（Options API 的模板只认实例属性，
 * 渲染期访问不到 import 进来的对象会直接报错并白屏）。所以这里只放**纯函数**，
 * 由页面 `computed` 调一次，把原始 DTO 摊平成模板可以直接 `{{ }}` 的字段。
 *
 * 口径全部来自 `.dsh-tmp/community-contract.md` 的**实测更新（2026-09-19，有真实数据后）**，
 * **不要按 REST 惯例补字段**：
 * - 列表 DTO（PostRespDTO）**没有 `content`** → 卡片只能靠 `title` + `contentExcerpt` + `coverUrl` + 图片数组 + 计数 + 话题
 * - ⚠️ **2026-09-21「列表正文摘要定稿」**：列表**有** `contentExcerpt`（正文**纯前缀**、按码点截 200、
 *   无正文 = **空串不是 null**、**不加省略号**）与 `contentTruncated`（**布尔结论**，前端不再自己比长度）
 *   —— 依据 `dto/resp/PostRespDTO.java:58` / `:72` 与 `support/PostContentExcerpt.java`；
 *   **列表仍然没有全文**（`PostRespDTO.java:55` 特意说明），所以卡片「展开」必须去读**详情**
 *   （并且要带 `countView=false`，否则"点一下展开就涨一次浏览量"）。
 * - ⚠️ **2026-09-21「社区列表图片契约定稿」**：列表**也有** `images`（`List<PostImageRespDTO>`，
 *   按 `sort` 升序、`[0]` 即封面、**最多 9 张**、**无图 = `[]` 而不是 null**）与 `imageCount`（图片**总张数**，
 *   `+N = imageCount - images.length`）—— 依据后端 `dto/resp/PostRespDTO.java:49`（`images`）
 *   与 `dto/resp/PostRespDTO.java:57`（`imageCount`），**后端已定稿**。
 *   列表与详情**共用同一个 `PostImageRespDTO`**，元素形状天然一致。
 *   ⚠️ **「+N」当前恒为 0、前端这一档不可达**：写侧上限 9（`B000303`）= 读侧预览上限 9。
 * - 所有社区 DTO **都没有头像字段** → 头像位只能拿显示名首字（不是"没加载出来"）
 * - `username` **只在社区里没有该用户的内容时为 null**（实测 uid=7 有真值 `probe114904`、uid=1 为 null）
 *   → 按 `uid` 兜底必须留着，界面上**不许出现 null**
 * - 列表的 `videos` 无视频时是**空数组**（不是 null）；`topics` 无话题时是**空数组**；
 *   `images` 无图时也是**空数组**；`coverUrl` 无图时是 **null**（`images=[]` ⟺ `coverUrl=null`，写侧冗余保证）
 * - 详情 DTO **没有 `isTop`**、没有 `coverUrl`（封面 = images 按 sort 升序的第 0 条，`sort` 从 **0** 起）
 * - ⚠️ **类型不对称**：同一个列表 DTO 里 `liked` / `favorited` 是**布尔**，`isTop` 是**数字 `0`/`1`**
 *   → 一律**按真值判断**，禁止 `=== true` / `=== 1` 这类严格比较
 *
 * 2026-09-21 追加「用户主页」用到的三个纯函数（`profileName` / `profileView` / `petCard`）：
 * 口径来自 `.dsh-tmp/knowledge-userpage-contract.md` **§B**（88 次只读实测 + fixture 在
 * `tools/userpage-fixtures/`）。三条与社区列表**不同**的硬事实写在各自函数头上。
 */
import { ageLabel, breedLabel, genderLabel, initials, petDisplayName, sterilizedLabel } from './pet.js'

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

/**
 * **评论**里的作者显示名：`username` 优先，为空按 uid 兜底成 **`喵友 #uid`**。
 *
 * 🔴 与 `authorLabel`（社区**列表**口径，末档是"匿名用户"）**刻意不同**：
 * 后端从来没有返回过"匿名用户"这个词，写它等于凭空断言"人家匿名"。
 * 评论口径与用户主页（`profileName`）一致 —— 2026-09-21 卡片分区切片统一到这一档。
 */
export function commentAuthorName (item) {
  const o = item || {}
  const name = usernameOf(o)
  if (name) return name
  const uid = o.uid
  if (uid !== null && uid !== undefined && uid !== '') return '喵友 #' + uid
  return '喵友'
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
 * `images` 之类的列表 → url 数组（**两种元素形状都吃得下**）
 * - `[{url,objectKey,sort}]`（DTO 原样）→ 按 `sort` 升序
 * - `['http…']`（页面已经取过一轮 url 的形态，字符串项没有 sort → 保持原顺序）
 */
function toUrlList (rows) {
  if (!Array.isArray(rows)) return []
  const sorted = sortedBySort(rows)
  const out = []
  for (let i = 0; i < sorted.length; i++) {
    const it = sorted[i]
    const url = typeof it === 'string' ? it : (it && it.url)
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
  // 评论口径：`username` 为空 → `喵友 #uid`（**不用** authorLabel 的"匿名用户"末档，见 commentAuthorName）
  const author = commentAuthorName(r)
  return {
    commentId: r.commentId,
    /** 进作者主页用（`/pages/user/profile?uid=`）—— uid 只能来自社区 DTO 或页面参数（user 模块没有按 uid 查） */
    uid: r.uid,
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
  // 评论口径：同 replyCard（`喵友 #uid`，不是"匿名用户"）
  const author = commentAuthorName(c)
  // 一级评论无回复时 `children` 实测是 `[]`（不是 null）；回复项才是 null → 一律判空
  const children = Array.isArray(c.children) ? c.children : []
  return {
    commentId: c.commentId,
    /** 进作者主页用（同 replyCard） */
    uid: c.uid,
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
    /** 进作者主页用（社区列表卡片整卡是"进详情"，作者名要 `.stop` 才不会串） */
    uid: p.uid,
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
    /** `coverUrl` 有没有值（**不看**图片网格）—— 别的切片按这个字段断言，语义不要改 */
    hasCover: !!cover,
    /**
     * 图片网格（2026-09-21 追加）。⚠️ 详情与列表**都**有 `images[]`：
     * 列表的 `images` 由 2026-09-21「社区列表图片契约定稿」补齐（`PostRespDTO.java:49`，按 `sort` 升序、
     * 最多 9 张、无图 = `[]`），所以这一支现在是**常规路径**而不是"将来形状"；
     * `imageSourceOf` 仍做**防御式**取值：响应里出现图片数组就按数组排，没有（旧快照）才退回"1 张 = 大图"。
     */
    ...imageGridOf(p),
    topics: topics,
    hasTopics: topics.length > 0,
    /** 列表**也带 `images`**（≤9 张，无图 = `[]`，后端 2026-09-21 定稿）；视频只给个数量提示，列表里不渲染播放器 */
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
    /** 进作者主页用（`/pages/user/profile?uid=`）；uid 只能来自社区 DTO 或页面参数 */
    uid: d.uid,
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

/* ==================================================================
 * 用户主页（2026-09-21 追加）
 *
 * 契约：`.dsh-tmp/knowledge-userpage-contract.md` §B。三条与社区列表**不同**的口径：
 * 1. **`username` 可为 null**：社区只从**帖子表**反查昵称，所以"从没发过帖的人"（实测含 uid=1 MapleLeaf）
 *    在主页头部与关注/粉丝列表里恒为 null → 兜底文案是 **`喵友 #uid`**。
 *    ⚠️ **不许写"匿名用户"**：后端**从来没有返回过**这个串（实测所有 DTO 里只有真值或 null），
 *    那是前端早期兜底造出来的词，在主页上用它等于凭空断言"人家匿名"。
 * 2. **没有头像**（`miaowang_user.avatar` 全表 null、社区所有 DTO 都无头像字段）→ 头像位只画**显示名首字**，
 *    **绝不挂 `<image>`**（否则永远是一张空图）。
 * 3. **`uid` 只能从社区 DTO / 页面参数来**（`GET /user/{username}` 的 `id` 恒 null、user 模块没有按 uid 查）
 *    → 主页头部把 uid 原样透出，供"刷新/重进"用。
 * ================================================================== */

/**
 * 用户主页显示名：`username` 优先，为 null 时按 uid 兜底成 **`喵友 #uid`**。
 * （与 `authorLabel` 的区别：那个是社区列表的口径、末档是"匿名用户"；这里是主页口径，见上面第 1 条。）
 */
export function profileName (profile) {
  const o = profile || {}
  const name = usernameOf(o)
  if (name) return name
  const uid = o.uid
  if (uid !== null && uid !== undefined && uid !== '') return '喵友 #' + uid
  return '喵友'
}

/** `username` 的真值（null / 空串 / 纯空格一律算"没有"） */
function usernameOf (profile) {
  const v = (profile || {}).username
  return v === null || v === undefined ? '' : String(v).trim()
}

/**
 * 主页头部（`UserProfileRespDTO`）→ 模板字段
 *
 * @param {object} profile 裸对象：`{uid,username|null,self,followed,postCount,likeReceivedCount,followingCount,followerCount}`
 * @param {object} inter 关注按钮的**本地交互态** `{ followed, busy, delta }`（不传就以接口给的 `followed` 为准）
 *
 * `inter` 单独一层：接口 DTO 不再回写（避免"两处真源"），页面按**乐观更新 + 失败回滚**改它。
 * `delta` 只作用在**粉丝数**上：关注成功后"他的粉丝 +1"是逻辑必然，乐观显示、失败回滚；
 * 重新拉 profile 时由页面清成 0（那时以接口值为准）。
 */
export function profileView (profile, inter) {
  const p = profile || {}
  const i = inter || {}
  const name = profileName(p)
  const followed = i.followed === undefined || i.followed === null ? !!p.followed : !!i.followed
  const delta = Number(i.delta) || 0
  return {
    uid: p.uid,
    name: name,
    /** `username` 有没有真值：为 false 时名字是 `喵友 #uid` 兜底，界面上要说明编号从哪来 */
    hasName: !!usernameOf(p),
    initial: initialOf(name),
    /** `self` **只看接口**（实测看自己主页恒 `followed:false`）→ 关注按钮靠它隐藏 */
    isSelf: !!p.self,
    followed: followed,
    followBusy: !!i.busy,
    followText: followed ? '已关注' : '关注',
    /** 四个计数：帖子 / 获赞 / 关注 / 粉丝（原样吃接口值，不做"万"折算，数字小） */
    stats: [
      { key: 'post', label: '帖子', value: countOf(p.postCount) },
      { key: 'like', label: '获赞', value: countOf(p.likeReceivedCount) },
      { key: 'following', label: '关注', value: countOf(p.followingCount) },
      { key: 'follower', label: '粉丝', value: Math.max(0, countOf(p.followerCount) + delta) }
    ]
  }
}

/**
 * 主页的宠物卡（`PetBriefRespDTO`）→ 模板字段
 *
 * 标签口径**直接复用 `utils/pet.js`**（与"我的宠物"页同一套：空名兜底 / 品种 / 年龄 / 性别 / 绝育），
 * 不在这里另写一份，免得两个页面把同一只猫显示成两种说法。
 *
 * @param {object} pet `{pid,pName|null,species,specificSpecies|null,ownerCallName|null,birth,gender,sterilizedState}`
 */
export function petCard (pet) {
  const p = pet || {}
  return {
    pid: p.pid,
    name: petDisplayName(p),
    initial: initials(p),
    breedText: breedLabel(p) || '品种未填',
    ageText: ageLabel(p.birth),
    genderText: genderLabel(p.gender),
    sterileText: sterilizedLabel(p.sterilizedState)
  }
}

/* =====================================================================================
 * 帖子图片的排列规则 + 卡片文字折叠（2026-09-21 追加）
 *
 * 需求：「1 张单张大图 / 2 张一行两张 / 3 张一行三张 / 4 张 2×2 / 5–9 张 3×3 / >9 张只排 9 张
 * 且第 9 张右下角盖 +N；格子正方形裁切、间距一致；点开从点击那张开始」+「没有图整块不渲染」。
 *
 * ⚠️ **两端图片字段形状一致（后端已定稿，别按 REST 惯例猜）**：
 * - 详情 `PostDetailRespDTO.images` = `[{url,objectKey,sort}]`（`sort` 从 0 起）→ 天然支持九宫格
 * - 列表 `PostRespDTO`（`MiaoWang-backproject/.../dto/resp/PostRespDTO.java:49`）**也有 `images`**：
 *   同一个 `PostImageRespDTO`、同一个 `sort` 升序口径，**只是最多 9 张**（`imageCount` 给总张数，`:57`）
 *   —— 2026-09-21「社区列表图片契约定稿」起成立，**旧注释里"列表没有 images"的说法已作废**。
 *   所以 `imageSourceOf()` 的防御式分支现在只对**旧快照响应**生效：有数组就排网格，没有才退回单张。
 *   ⚠️ **不许为了凑九宫格伪造多图**，也**不许**为了拿图片给每条帖子再打一次详情请求（列表一页会变成 10 次请求）。
 *
 * ⚠️ **元素形状别抄错（最容易踩的坑）**：列表/详情的图片元素逐字为 `{url, objectKey, sort}`
 *   （`dto/resp/PostImageRespDTO.java:14,19,24`）；而图片**上传接口** `/community/file/image` 的返回
 *   是另一个 DTO `ImageUploadRespDTO`（`{url, objectKey}`，**没有 `sort`**）—— 拿上传响应当"列表元素"看会得出错误结论。
 * ⚠️ **「+N」当前不可达**：写侧上限 `MAX_POST_IMAGE_COUNT = 9`（超了 `B000303`）与读侧预览上限
 *   `LIST_IMAGE_PREVIEW_COUNT = 9` 同值 → `imageCount - images.length` **恒为 0**；
 *   本文件 `imageLayout()` 的 `>IMAGE_GRID_MAX` 那一档只有**将来放宽写侧上限**才会被真实数据触发。
 * ===================================================================================== */

/** 网格最多排几格（超过就第 9 格盖 +N） */
export const IMAGE_GRID_MAX = 9

/**
 * 格子间距的**令牌口径**（`$mw-space-1` = 8rpx）：只用于文档与断言，实际算式写在 `PostImages.vue` 的 `calc()` 里 */
export const IMAGE_GAP_RPX = 8

/**
 * 🔴 **图片显示口径（2026-09-21 用户两次拍板后的最终版，逐条覆盖更早的说法）**
 *
 * 用户最终原话：「**如果是一张图，就原比例不裁剪**，但是**超过一张就进行裁剪，排成对应的九宫格等**」。
 *
 * | 张数 | 形态 | 裁切 | 列数 |
 * |---|---|---|---|
 * | 0 | 整块不渲染 | — | — |
 * | **1** | **原比例完整大图**（`mode="widthFix"`），**不限高、不裁切** | ❌ 不裁 | 1 |
 * | 2 | 一行两列正方形缩略格 | ✅ `aspectFill` | 2 |
 * | 3 | 一行三列正方形缩略格 | ✅ | 3 |
 * | 4 | 2×2 | ✅ | 2 |
 * | 5–9 | 3×3 | ✅ | 3 |
 * | >9 | 3×3 + 最后一格「+N」（写侧上限 9 → 不可达） | ✅ | 3 |
 *
 * ⚠️ 裁切**只发生在缩略格子**里：点开看大图（`uni.previewImage`）拿到的始终是**原图 url**，
 *    详情页里"恰好 1 张"也是原图 —— 用户要的"完整原图"在这两处都成立。
 *
 * 于是本文件里**两条旧规则作废并删除**（留着就是与需求相反的残留）：
 *   1. ~~`SINGLE_IMAGE_MAX_HEIGHT_RPX`（单图封顶 900rpx）~~ → 单图**不限高**（用户："不要收窄图片高度"）
 *   2. ~~`singleImageBox()` 按自然尺寸算盒高~~ → 单图直接 `widthFix`（宽度给死、高度按原比例自动）
 *
 * 仍然成立的四条：无图 → 整块不渲染；**列数只由张数决定**；点开从**点的那张**开始；
 * 加载**真失败**要有真实处理（格子留在原地并如实写「图片加载失败」，**不许静默当"没图"**）。
 */

/**
 * 列表卡片标题折叠阈值（**字符数**）：正文区宽 622rpx、字号 `$mw-font-body` 30rpx → 一行约 20 个汉字，
 *  2 行约 40 字。超过 40 字才给「展开」，避免给本来就不换行的标题挂一个没用的按钮。 */
export const CARD_TITLE_CLAMP_CHARS = 40

/**
 * "占位图"判据阈值（**两个方向都** ≤ 32px）
 *
 * 🔴 这是**真实数据实测**得来的：post 181 那张图正文自述"1x1 像素占位图"，但把对象存储上的原图
 *   拉下来读 PNG IHDR 得到的是 `width=8 height=8`（186 字节）—— 自述与真实尺寸不一致，
 *   所以判据只能按**真实像素**给（见 `tools/probe-community-search-images.mjs` 与
 *   `tools/community-search-fixtures/_IMAGE_PROBE.json`）。
 *
 * ⚠️ **2026-09-21 定稿后，这个判据不再用来"藏图"**：用户明确要求「连极小图（≤32px）也要显示」
 *   （多图里就是一个裁剪后的模糊方块 ✅ 模糊但**不变形**）。函数与阈值留着给**门禁/文档**用
 *   （它证明我们**认得**线上那批 8×8 脏数据），**不许**再拿它把图片从列表里剔掉。
 */
export const TINY_IMAGE_MAX_PX = 32

/**
 * 这张图是不是"占位图"（8×8 这类脏数据）
 * ⚠️ 只做判定，**调用方不许据此隐藏图片**（见上面 `TINY_IMAGE_MAX_PX` 的说明）。
 * @param {object} natural `{width,height}` 自然像素
 */
export function isTinyImage (natural) {
  const w = Number(natural && natural.width)
  const h = Number(natural && natural.height)
  if (!(w > 0) || !(h > 0)) return false
  return w <= TINY_IMAGE_MAX_PX && h <= TINY_IMAGE_MAX_PX
}

/**
 * 图片张数 → 排布档位（**纯函数，门禁直接真跑它**）
 *
 * | 张数 | tier | columns(perRow) | 可见格子 | 溢出 |
 * |---|---|---|---|---|
 * | 0 | `none` | 0 | 0 | 0 |
 * | 1 | `single` | 1（**大图，不是格子**） | 1 | 0 |
 * | 2 | `two` | 2 | 2 | 0 |
 * | 3 | `three` | 3 | 3 | 0 |
 * | 4 | `four` | 2 | 4 | 0 |
 * | 5–9 | `nine` | 3 | n | 0 |
 * | >9 | `nine` | 3 | **9** | n − 9 |
 *
 * 🔴 **列数只由张数决定，与容器宽度无关**；格子宽度由 `components/PostImages.vue` 用
 * `calc((100% − (N−1)×gap) / N)` 相对容器算 —— 所以**本函数不再返回 `cellRpx`**
 * （那个字段是"9 张排成 2 列"的元凶：它假设容器恒为 622rpx，而真实容器窄 2px）。
 *
 * @param {number} count 图片张数
 */
export function imageLayout (count) {
  const n = Math.floor(Number(count))
  if (isNaN(n) || n <= 0) return { count: 0, tier: 'none', perRow: 0, columns: 0, visible: 0, overflow: 0 }
  if (n === 1) return { count: 1, tier: 'single', perRow: 1, columns: 1, visible: 1, overflow: 0 }
  if (n === 2) return { count: n, tier: 'two', perRow: 2, columns: 2, visible: 2, overflow: 0 }
  if (n === 3) return { count: n, tier: 'three', perRow: 3, columns: 3, visible: 3, overflow: 0 }
  if (n === 4) return { count: n, tier: 'four', perRow: 2, columns: 2, visible: 4, overflow: 0 }
  if (n <= IMAGE_GRID_MAX) return { count: n, tier: 'nine', perRow: 3, columns: 3, visible: n, overflow: 0 }
  return { count: n, tier: 'nine', perRow: 3, columns: 3, visible: IMAGE_GRID_MAX, overflow: n - IMAGE_GRID_MAX }
}

/**
 * 帖子的图片 url 列表（**防御式**：两种 DTO 形状都吃得下）
 *
 * 取值顺序：
 * 1. `images[]`（**详情与列表都有**，后端 2026-09-21 定稿：列表最多 9 张）→ 按 `sort` 升序、丢掉没有 url 的项
 * 2. `imageUrls[]` / `imageUrlList[]`（备选字段名；服务端**永远不会给**这两个，只当防御留着）
 * 3. **响应里根本没有图片数组**（定稿前抓的旧快照）→ 退回单张 `coverUrl`
 *
 * ⚠️ 只要**出现了图片数组字段**（哪怕是空数组），就以它为准，**不再回退 coverUrl**
 *   —— 空数组就是"这条帖子没有图"，此时整块图片区不渲染（不许拿旧的封面兜出一个灰框）。
 */
export function imageSourceOf (post) {
  const p = post || {}
  if (Array.isArray(p.images) || Array.isArray(p.imageUrls) || Array.isArray(p.imageUrlList)) {
    const fromImages = Array.isArray(p.images) ? toUrlList(p.images) : []
    if (fromImages.length) return fromImages
    const alt = Array.isArray(p.imageUrls) ? p.imageUrls : (Array.isArray(p.imageUrlList) ? p.imageUrlList : [])
    return toUrlList(alt)
  }
  const cover = p.coverUrl ? String(p.coverUrl) : ''
  return cover ? [cover] : []
}

/**
 * 图片 url[] + 运行时状态 → 模板要用的网格字段（**列表卡与详情页共用这一份口径**）
 *
 * @param {object} src `PostRespDTO` / `PostDetailRespDTO` / `{images: url[]}` 都行
 * @param {object} state `{ broken: {url:true} }`
 *   - `broken`：加载**真失败**（`@error`）的 url → 该格标记 `failed`，**仍然占位**并如实写
 *     「图片加载失败」（2026-09-21 定稿：**不许静默当"没图"**，也不许把格子抽掉让后面几张挪位）
 *   ⚠️ `sizes` 已**不再需要**（没有"最大高度/正方形"这类要按自然尺寸算的东西了，`mode="widthFix"` 自己保比例）
 */
export function imageGridOf (src, state) {
  const s = state || {}
  const broken = s.broken || {}
  const urls = Array.isArray(src && src.images)
    ? toUrlList(src.images)
    : imageSourceOf(src)
  /**
   * 图片**总张数**：DTO 的 `imageCount`（列表最多只带 9 张预览，但它给总数）优先，
   * 没有这个字段（定稿前抓的旧快照）就按数组长度。**取两者较大值**，避免脏数据下总数比数组还小。
   */
  const declared = Number(src && src.imageCount)
  const total = declared > urls.length ? Math.floor(declared) : urls.length
  const grid = imageLayout(total)
  const cells = imageCells(urls, total, broken)
  return {
    images: urls,
    /** 一个图 url 都没有 → 图片区**整块不渲染**（不是 display:none、不是 height:0） */
    hasImages: urls.length > 0,
    imageCount: total,
    grid: grid,
    cells: cells,
    /** 格子按行切好：**每行几个格子 = 每行几列**（组件只渲染结构，不自己算布局） */
    rows: imageRows(cells, grid.perRow),
    /** 有几格是真加载失败的（界面照实说，不藏） */
    failedCount: cells.length
      ? cells.filter(function (c) { return c.failed }).length
      : (urls.length === 1 && broken[urls[0]] ? 1 : 0),
    /**
     * **恰好 1 张**且这一张加载失败 → `true`：单图那一支没有"格子"，模板要单独判一下，
     * 否则图挂了就什么都不显示（又变回"静默当没图"）。
     */
    singleFailed: urls.length === 1 && !!broken[urls[0]],
    /**
     * 🔴 **多图缩略格才裁切**（`aspectFill` + 方形格），**恰好 1 张不裁**（`widthFix` 原比例）。
     * 模板/门禁都读这个字段，别在别处再判一遍张数（两处判就会分叉）。
     */
    crop: cells.length > 0
  }
}

/**
 * 图片 url[] → 格子数组（每格带 url / 原列表下标 / 「+N」文案 / 是否加载失败）
 *
 * ⚠️ **格子宽度只相对容器**：`calc((100% − (N−1)×gap) / N)`（在 `components/PostImages.vue` 里），
 * 高度由 `aspect-ratio: 1` 撑成**正方形**（缩略图裁切的前提），**没有任何按 622rpx 假定宽算出的定值**
 * —— 那正是"9 张排成 2 列 5 行"的根因（卡片 border 左右各 1px，真实可用宽比 622rpx 窄 2px）。
 *
 * `index` 是**原列表里的下标**，点开大图时原样交给 `uni.previewImage` 的 `current`
 * → 一定从**点的那张**开始。
 *
 * @param {string[]} urls 图片 url
 * @param {number} [total] 图片**总张数**（DTO 的 `imageCount`；不传 = `urls.length`）→ 决定「+N」
 * @param {object} [broken] `{url:true}` 加载失败的 url（标记 `failed`，**不剔除**）
 */
export function imageCells (urls, total, broken) {
  const list = Array.isArray(urls) ? urls.filter(function (u) { return u ? String(u) : false }).map(String) : []
  const bad = broken || {}
  const declared = Number(total)
  const all = declared > list.length ? Math.floor(declared) : list.length
  const layout = imageLayout(all)
  // **单图与无图都没有"格子"**：单图走大图那一支（模板直接用 images[0]），无图整块不渲染
  if (layout.tier === 'single' || !layout.visible) return []
  /** 真正有 url 的格子数（列表可能只带前几张预览 → 按实际有的排） */
  const shown = Math.min(layout.visible, list.length)
  const cells = []
  for (let i = 0; i < shown; i++) {
    const isLast = i === shown - 1
    const over = isLast ? Math.max(0, all - shown) : 0
    cells.push({
      index: i,
      url: list[i],
      overflow: over,
      overflowText: over > 0 ? '+' + over : '',
      /** 这一张**真的**加载失败（`@error`）→ 格子照留，界面如实写"图片加载失败" */
      failed: !!bad[list[i]],
      failedText: bad[list[i]] ? '图片加载失败' : ''
    })
  }
  return cells
}

/**
 * 卡片标题的折叠状态（**纯函数**）
 *
 * 🔴 **当前未被卡片消费**（2026-09-21 用户定稿：**标题完整显示、不折**）：
 *   `cardView()` 里恒为 `card.titleClamped = false` / `card.titleHasToggle = false`，
 *   模板直出 `{{ p.title }}`，没有 `-webkit-line-clamp`、也没有「展开」按钮。
 *   **保留原因**：它是个纯函数，有单测/门禁引用（改坏会红），且**哪天要恢复标题折叠**时
 *   只需要在 `cardView()` 里重新接上它的结论 —— 不需要把这段判据再写一遍。
 *   ⚠️ 要删就**连着门禁里的引用一起删**（`tools/check-community-search-phone.mjs` /
 *   `check-community-phone.mjs` 里都按它断言过）。
 *
 * 为什么用**字符阈值**而不是"量高度"：小程序 / App 里量不到文本行数，而 `-webkit-line-clamp`
 * 只说"显示几行"、不告诉你"到底有没有被截断" → 按钮要么永远在、要么永远不在。
 * 阈值 40 字是按正文区 622rpx ÷ 30rpx 字号 ≈ 20 字/行、2 行 ≈ 40 字推的（见 CARD_TITLE_CLAMP_CHARS）。
 *
 * @param {string} value 原始标题
 * @param {boolean} open 当前是不是展开态
 */
export function cardTitleView (value, open) {
  const full = titleOf(value)
  const long = full.length > CARD_TITLE_CLAMP_CHARS
  const isOpen = !!open && long
  return {
    full: full,
    /** 超过阈值 → 折叠（模板据此挂 `-webkit-line-clamp:2`） */
    clamped: long && !isOpen,
    /** 要不要显示「展开 / 收起」 */
    hasToggle: long,
    open: isOpen,
    toggleText: isOpen ? '收起' : '展开'
  }
}

/**
 * 卡片「正文摘要」的字段**优先名清单**（顺序 = 优先级；定稿名排第一）。
 * 与 Web 端 `components/community/postExcerpt.ts` 的 `EXCERPT_FIELDS` **逐项一致**
 * —— 两端同一份口径，改一处必须改两处（改坏了门禁会红）。
 */
export const EXCERPT_FIELDS = ['contentExcerpt', 'excerpt', 'contentSummary', 'summary', 'contentBrief', 'brief']

/** 「摘要是不是被截断」的候选字段名（读不到时保守当"是"，见 `cardExcerptView`） */
export const EXCERPT_TRUNCATED_FIELDS = ['contentTruncated', 'excerptTruncated', 'truncated', 'contentCut']

/** 摘要显示文本：**非字符串一律空串**（不 toString、不编），连续空白折成一个空格并去首尾 */
function excerptTextOf (raw) {
  if (typeof raw !== 'string') return ''
  return raw.replace(/\s+/g, ' ').trim()
}

/**
 * 摘要折叠**最多几行**（用户 2026-09-21 定稿：「摘要展开限制最多 5 行，超过 5 行的部分必须展开才能看」）
 * ⚠️ 这个数字只驱动 CSS 的 `-webkit-line-clamp` 与下面的"5 行高度"换算，**不是字符数阈值**。
 */
export const EXCERPT_CLAMP_LINES = 5
/**
 * 摘要**单行行高（rpx）**。
 * 🔴 必须与页面 CSS `.post__excerpt { line-height: 42rpx }` **逐字一致** ——
 * "真溢出判断"就是拿渲染后的实测高度跟这里的 `5 × 42rpx` 比（门禁会把两边钉在一起）。
 */
export const EXCERPT_LINE_RPX = 42
/** 折叠态允许的最大高度（rpx）= 5 行 × 行高 */
export const EXCERPT_MAX_RPX = EXCERPT_CLAMP_LINES * EXCERPT_LINE_RPX

/**
 * **真溢出判断**：渲染后量到的摘要高度 > 5 行高度吗？（纯函数，便于离线断言）
 *
 * 为什么不用字符数：同一个字符串在 320/375/414px 屏上、在不同字号设置下占的行数不一样，
 * 用字符数当"超过 5 行"的判据会时而多给一个废按钮、时而把该有的入口藏掉。
 * 所以：**渲完之后实测**（页面里 `uni.createSelectorQuery().boundingClientRect()`），
 * 拿"5 行高度（rpx → px 按当前窗口换算）"当阈值，容差 1px。
 * ⚠️ 平台不支持测量（没有 `createSelectorQuery`）时页面**如实退回**"只看服务端 `contentTruncated`"，
 * 不假装做过溢出判断（见页面注释）。
 *
 * @param {number} measuredPx 实测高度（px，来自 boundingClientRect）
 * @param {number} windowWidth 屏宽（px，来自 getSystemInfoSync）
 * @returns {boolean} 是否真的超过 5 行
 */
export function excerptOverflow (measuredPx, windowWidth) {
  const h = Number(measuredPx)
  const w = Number(windowWidth)
  if (!isFinite(h) || h <= 0 || !isFinite(w) || w <= 0) return false
  const maxPx = (EXCERPT_MAX_RPX * w) / 750
  return h > maxPx + 1
}

/**
 * 卡片「正文摘要 + 内容展开」分区（**纯函数，防御式**）
 *
 * 数据来源（后端 2026-09-21 定稿，**已落地在源码里**，不是"预计会有"）：
 * - `PostRespDTO.contentExcerpt`（`PostRespDTO.java:58`）：正文**纯前缀**，**没有正文时是空串不是 null**，
 *   不含省略号（"…"与按钮是前端表现层的事）；按**码点**截到 200（`CommunityConstant.LIST_CONTENT_EXCERPT_LENGTH`）
 * - `PostRespDTO.contentTruncated`（`PostRespDTO.java:72`）：**布尔结论**，前端**不再自己比长度**
 *   （JS 的 `length` 数 UTF-16 单元、Java 数码点，带 emoji 时自己比会算错 → 按钮消失）
 * - 列表 DTO **故意没有 `content` 字段**（`PostRespDTO.java:55`）→ 「展开」必须去读详情
 *
 * 🔴 防御式（与 Web 端 `components/community/postExcerpt.ts` **同一套口径**）：
 *   1. 字段名按**优先名清单**找（定稿名 `contentExcerpt` 排第一），哪个先出现且非空白就用哪个
 *      —— 字段名一变不会"静默消失"，也不会有人偷偷拿别的字段兜底；
 *   2. 一个都没有 → `hasExcerpt=false` → **该分区整块不渲染**（不是渲染空框，
 *      更**不许**拿标题 / 别的东西冒充摘要）；
 *   3. 「有没有被截断」读不到时**保守当"是"**（给用户留一个「内容展开」入口，而不是把入口藏掉）。
 *
 * 🔴 「展开」出现的条件（用户 2026-09-21 定稿）= **渲染超过 5 行** 或 **`contentTruncated === true`**，
 *    任一成立就出现：
 *    - 后者是服务端结论（200 字摘要本身就是前缀 → 必须能展开看全文）；
 *    - 前者由页面**实测**（`excerptOverflow()` + `uni.createSelectorQuery`）后把结论放进 `st.overflow`。
 *    ⚠️ **不拿字符数当"5 行"的判据**（同一串字在不同屏宽/字号下行数不同）。
 *    两路都没有（拿不到测量能力 + 服务端说没截断）→ 就**没有**「展开」按钮（不给用户挂废按钮）。
 *
 * @param {object} post 原始 `PostRespDTO`
 * @param {object} [st] 运行时状态 `{ open, text, loading, error, overflow }`
 *   - `open`：用户点过「展开」**且**详情拉回来了
 *   - `text`：详情接口给的**全文**（只有成功拿到才有值）
 *   - `error`：详情拉取失败的人话；此时**保留摘要**并如实提示，不算展开成功
 *   - `overflow`：页面实测"渲染超过 5 行"（`true` 才作数；`undefined` = 还没量/量不了）
 */
export function cardExcerptView (post, st) {
  const p = post || {}
  const s = st || {}
  let excerpt = ''
  let field = null
  for (let i = 0; i < EXCERPT_FIELDS.length; i++) {
    const t = excerptTextOf(p[EXCERPT_FIELDS[i]])
    if (t) {
      excerpt = t
      field = EXCERPT_FIELDS[i]
      break
    }
  }
  let truncated = true
  let truncatedField = null
  for (let i = 0; i < EXCERPT_TRUNCATED_FIELDS.length; i++) {
    const raw = p[EXCERPT_TRUNCATED_FIELDS[i]]
    if (typeof raw === 'boolean') {
      truncated = raw
      truncatedField = EXCERPT_TRUNCATED_FIELDS[i]
      break
    }
  }
  const hasExcerpt = excerpt.length > 0
  const full = s.text === null || s.text === undefined ? '' : String(s.text)
  const open = !!s.open && full.length > 0
  /** 页面实测出来的"渲染超过 5 行"（`true` 才作数） */
  const overflow = s.overflow === true
  return {
    /** 有没有摘要 → 没有就整块不渲染 */
    hasExcerpt: hasExcerpt,
    /** 分区里显示的文本：展开了就是全文，否则是摘要 */
    text: open ? full : excerpt,
    /** 命中的字段名（排查 / 门禁用，页面不显示） */
    field: field,
    /**
     * 折叠态就挂 `-webkit-line-clamp: 5`（**展开后绝不 clamp**；短摘要挂着也无害，因为根本不溢出）
     * ⚠️ 判据是"关着就 clamp"，**不是**"只有长摘要才 clamp" —— 后者要靠字符数猜，猜错就会漏行。
     */
    clamped: hasExcerpt && !open,
    /** 按钮条件：服务端说被截断 **或** 实测超过 5 行（任一成立） */
    hasToggle: hasExcerpt && (truncated || overflow),
    /** 服务端结论（`contentTruncated`） */
    truncated: truncated,
    truncatedField: truncatedField,
    /** 实测结论（页面量出来的；`false` = 没量或确实没超 5 行） */
    overflow: overflow,
    clampedLines: EXCERPT_CLAMP_LINES,
    open: open,
    toggleText: open ? '收起正文' : '内容展开',
    loading: !!s.loading,
    error: s.error ? String(s.error) : ''
  }
}

/**
 * 卡片上的「评论」入口（**只给条数 + 跳详情**，纯函数）
 *
 * 🔴 用户 2026-09-21 定稿口径（覆盖此前"卡片里显示评论/展开评论/卡片内发评论"那一版）：
 * **评论内容默认不显示，点进帖子才能看/发**；卡片外面**只显示有多少条评论**。
 * 所以这里只产出"条数 + 入口文案"，**不产出任何评论内容**。
 *
 * 字段**防御式**：`commentCount` 不是数字（字段缺失 / null / 非数值）→ `hasCount=false`
 * → 按钮只写「评论」，**不显示数字**（`0` 也是编的：我们并不知道到底有几条）。
 *
 * ⚠️ 后端**已撤销** `topComment`（列表不再批量带回最高赞评论）→ 卡片上任何"默认评论"都不存在了。
 *
 * @param {object} post 原始 `PostRespDTO`（只读 `commentCount`）
 */
export function postCommentEntryView (post) {
  const raw = post ? post.commentCount : undefined
  const num = typeof raw === 'number' && isFinite(raw) && raw >= 0 ? Math.floor(raw) : null
  return {
    /** 响应里到底有没有这个字段（没有就不显示条数） */
    hasCount: num !== null,
    count: num === null ? 0 : num,
    /** 按钮文案：有字段就带条数，没有就只说「评论」 */
    actionText: num === null ? '评论' : '评论 ' + num,
    /** 入口的去处说明（评论的看与发都在详情页） */
    hintText: '点进帖子看评论'
  }
}

/**
 * 卡片上「点赞 / 收藏 / 评论」三个**真操作**的显示状态（纯函数）
 *
 * 🔴 **不许瞎猜已点赞**：列表 DTO 的 `liked` / `favorited` 是布尔（`PostRespDTO.java:111` / `:116`，
 * 「未登录恒为 false」），但**字段缺失时必须当"不知道"**（返回 `liked === null`）——
 * 界面上不显示"已点赞"的选中态，也**不许**拿本地缓存当成后端状态（那就是假状态）。
 * 用户点过之后才由页面把结果写进本地状态（`liked` 变 `true`/`false`）。
 *
 * ⚠️ 触控目标：三个操作在模板里都是 `min-height: $mw-touch-min` 的按钮（不是挤在一起的小字）。
 *
 * @param {object} post 原始 `PostRespDTO`（读 `liked` / `favorited` / 三个计数）
 * @param {object} [st] 本地动作状态 `{ liked, likeCount, favorited, favoriteCount, busy, error }`
 */
export function postActionView (post, st) {
  const p = post || {}
  const s = st || {}
  const dtoLiked = typeof p.liked === 'boolean' ? p.liked : null
  const dtoFav = typeof p.favorited === 'boolean' ? p.favorited : null
  const liked = typeof s.liked === 'boolean' ? s.liked : dtoLiked
  const favorited = typeof s.favorited === 'boolean' ? s.favorited : dtoFav
  const likeCount = s.likeCount === undefined || s.likeCount === null ? countOf(p.likeCount) : countOf(s.likeCount)
  const favCount = s.favoriteCount === undefined || s.favoriteCount === null ? countOf(p.favoriteCount) : countOf(s.favoriteCount)
  return {
    /** `true` / `false` / **`null`（DTO 没给这个字段 → 界面不显示"已点赞"）** */
    liked: liked,
    favorited: favorited,
    likedOn: liked === true,
    favoritedOn: favorited === true,
    /** 数字计数（**乐观更新 / 回滚**要拿它当基线，别从文案里反解） */
    likeCount: likeCount,
    favoriteCount: favCount,
    /** 选中态的**辅助说明**（无障碍/文案层）：不知道状态时是空串，不许编 */
    likeStateText: liked === null ? '' : (liked ? '已点赞' : '未点赞'),
    favoritedStateText: favorited === null ? '' : (favorited ? '已收藏' : '未收藏'),
    likeText: '赞 ' + likeCount,
    favoriteText: '收藏 ' + favCount,
    /** 三个操作共用一个"正在提交"标记：同一张卡上不许并发乱点 */
    busy: !!s.busy,
    error: s.error ? String(s.error) : ''
  }
}

/**
 * 把格子切成**一行一行**（纯函数）
 *
 * 🔴 这是 2026-09-21 真机 bug 的**结构性修法**：一行就是一个 `display:flex`（**不换行**）的容器，
 * "这一行有几列"由**这一行里有几个格子**决定 —— 不再依赖 `flex-wrap` 的换行结果。
 * 旧做法是"大 flex 容器 + wrap"，格子宽度按**假定的 622rpx 卡片宽**硬算，
 * 而真实可用宽少 2px（卡片左右各有 `border: 1px`）→ 一行差 2px 就把最后一格挤到下一行
 * （9 张 → 2 列 5 行、3 张 → 2 列 2 行、2 张 → 1 列 2 行，正是真机现象）。
 *
 * @param {object[]} cells `imageCells()` 的结果
 * @param {number} perRow `imageLayout().perRow`（每行几列）
 * @returns {object[][]} 每行一个数组；空输入 → 空数组
 */
export function imageRows (cells, perRow) {
  const list = Array.isArray(cells) ? cells : []
  const n = Math.floor(Number(perRow)) > 0 ? Math.floor(Number(perRow)) : 1
  const rows = []
  for (let i = 0; i < list.length; i += n) rows.push(list.slice(i, i + n))
  return rows
}

/**
 * 列表卡片的最终字段 = `postCard()` + **运行时状态**（图片加载失败 / 占位图 / 正文摘要展开 /
 * 点赞收藏的本地乐观状态）
 *
 * 三个列表页（社区信息流 / 搜索结果 / 用户主页）共用这一份口径，免得同一张卡片在三处显示成三样。
 * ⚠️ 运行时状态**不进 `postCard`**：那个函数是纯的，别的切片的门禁按它断言
 *   （例如"post 181 的卡片 hasCover 必须还是 true"），别把 `@load` 的结果混进去。
 *
 * 卡片纵向**分区顺序**（界面契约，模板与门禁都按它比对）：
 * ① 用户信息 → ② 标题（**完整显示，不折叠**）→ ③ 内容摘要 → ④ 内容展开
 * → ⑤ 图片 → ⑥ 操作区（点赞 / 收藏 / **评论条数 + 进详情入口**）。
 *
 * 🔴 **卡片上不渲染任何评论内容**（2026-09-21 用户定稿，覆盖此前"卡片里显示/展开/发评论"那一版）：
 * 评论的看与发都在详情页，卡片外面**只显示有多少条评论**（`commentActionText`，字段缺失就不显示数字）。
 *
 * @param {object} post 原始 DTO
 * @param {object} state `{ broken, content: {open,text,loading,error,overflow},
 *                          action: {liked,likeCount,favorited,favoriteCount,busy,error} }`
 *   ⚠️ `titleOpen` / `comment` 已**不再使用**（标题不折叠、卡片不渲染评论内容）；
 *      `sizes` 也不再需要（图片不裁切、不限高，`widthFix` 自己保长宽比）。
 */
export function cardView (post, state) {
  const s = state || {}
  const card = postCard(post)
  const img = imageGridOf(post, { broken: s.broken })
  card.images = img.images
  card.hasImages = img.hasImages
  card.imageCount = img.imageCount
  card.grid = img.grid
  card.cells = img.cells
  /** 一行一个数组（每行长度 = 该行列数）：模板 `:rows="p.rows"` 用的就是它 */
  card.rows = img.rows
  card.imageSingleFailed = img.singleFailed
  card.imageFailedCount = img.failedCount
  /**
   * 🔴 **标题不再折叠**（2026-09-21 用户要求）：`title` 始终原样显示（真标题只有 14~19 字，
   * 折它只会让人看不全）。折叠的对象改成**正文摘要**（见下面的 `excerpt*`）。
   * `cardTitleView` 函数留着（别的切片按它断言），但卡片**不再消费**它的折叠结论。
   */
  card.titleClamped = false
  card.titleHasToggle = false
  const ex = cardExcerptView(post, s.content)
  card.hasExcerpt = ex.hasExcerpt
  card.excerptText = ex.text
  card.excerptClamped = ex.clamped
  card.excerptHasToggle = ex.hasToggle
  card.excerptOpen = ex.open
  card.excerptToggleText = ex.toggleText
  card.excerptLoading = ex.loading
  card.excerptError = ex.error
  const ce = postCommentEntryView(post)
  card.commentHasCount = ce.hasCount
  card.commentCount = ce.count
  card.commentActionText = ce.actionText
  card.commentHintText = ce.hintText
  // 卡片上的三个**真操作**（点赞 / 收藏 / 评论）——状态口径见 postActionView
  const act = postActionView(post, s.action)
  card.liked = act.liked
  card.favorited = act.favorited
  card.likedOn = act.likedOn
  card.favoritedOn = act.favoritedOn
  card.likeStateText = act.likeStateText
  card.favoritedStateText = act.favoritedStateText
  card.likeText = act.likeText
  card.favoriteText = act.favoriteText
  card.actionBusy = act.busy
  card.actionError = act.error
  return card
}