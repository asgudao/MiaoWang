/**
 * 用真实线上数据固化社区 fixture（**只发 GET**，登录除外）
 *
 * 为什么需要它：社区两端的展示逻辑此前是按**源码推断的契约**写的，2026-09-19 线上库里
 * 有了真实种子数据（帖子 178~182 / 评论 101~106 / 话题 68），实测推翻了若干假设
 * （`isTop` 是数字、一级评论 `parentId` 是 `0`、`children` 是正序 …）。
 * 那些结论必须由**真实返回的 JSON** 驱动断言，不能靠手写 DTO 形状自证。
 *
 * 本脚本做三件事：
 *  1) `POST /user/login` 拿 token（唯一一次写请求，是登录不是造数据）
 *  2) 按 `.dsh-tmp/community-contract.md` 的种子 id 发 **6 个 GET**（+ `/section`）
 *  3) 把响应**裁剪后**写进 `tools/community-fixtures/`，供 `check-community-api.js`
 *     与 `check-community-phone.mjs` 当输入
 *
 * 安全红线：
 *  - **token 绝不落盘**（只在内存里拼请求头，fixtures 里只有业务 data）
 *  - 只写业务字段，不写 `requestId` / 信封外壳 / 登录响应
 *  - ⚠️ `GET /post/{id}` 会让后端 `viewCount+1`（可接受的读副作用）
 *
 * 用法：
 *   node tools/fetch-community-fixtures.js                 # 登录 + 拉取 + 落盘
 *   node tools/fetch-community-fixtures.js --dry           # 只打印事实，不写文件
 * 账号可用 MIAOWANG_PROBE_USER / MIAOWANG_PROBE_PASSWORD 覆盖（默认见下）
 */
const fs = require('fs')
const path = require('path')

const GATEWAY = process.env.MIAOWANG_GATEWAY || 'http://127.0.0.1:7000'
const USERNAME = process.env.MIAOWANG_PROBE_USER || 'probe114904'
const PASSWORD = process.env.MIAOWANG_PROBE_PASSWORD || 'MiaoWang123'
const DRY = process.argv.includes('--dry')

const OUT_DIR = path.join(__dirname, 'community-fixtures')

/** 网关用 discovery locator，URL 必须带服务名前缀 */
const COMMUNITY = `${GATEWAY}/miaowang-community/mapleleaf/miaowang/v1/community`
const USER = `${GATEWAY}/miaowang-user/mapleleaf/miaowang/v1/user`

let failed = 0
function note(label, value) {
  console.log(`  ${label}：${typeof value === 'string' ? value : JSON.stringify(value)}`)
}

async function login() {
  const res = await fetch(`${USER}/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: USERNAME, password: PASSWORD }),
  })
  const env = await res.json()
  if (!env || env.code !== '0' || !env.data || !env.data.token) {
    throw new Error(`登录失败：HTTP ${res.status} ${JSON.stringify(env)}`)
  }
  return env.data.token
}

async function getJson(pathname, headers, query) {
  const url = new URL(COMMUNITY + pathname)
  for (const [k, v] of Object.entries(query || {})) {
    if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, String(v))
  }
  const res = await fetch(url, { method: 'GET', headers })
  const text = await res.text()
  let env
  try {
    env = JSON.parse(text)
  } catch {
    throw new Error(`GET ${pathname} 返回了非 JSON（HTTP ${res.status}）：${text.slice(0, 200)}`)
  }
  if (env.code !== '0') throw new Error(`GET ${pathname} 业务失败：${env.code} ${env.message}`)
  return env.data
}

/** 只留业务字段，丢掉 requestId / 信封壳，避免把无关内容写进仓库 */
const pickPost = (p) => ({
  postId: p.postId,
  sectionId: p.sectionId,
  uid: p.uid,
  username: p.username,
  title: p.title,
  coverUrl: p.coverUrl,
  isTop: p.isTop,
  viewCount: p.viewCount,
  likeCount: p.likeCount,
  favoriteCount: p.favoriteCount,
  commentCount: p.commentCount,
  liked: p.liked,
  favorited: p.favorited,
  topics: p.topics,
  videos: p.videos,
  createTime: p.createTime,
})

/** 详情比列表多 `content` / `status` / `images`，少 `isTop` / `coverUrl`（实测） */
const pickDetail = (p) => ({
  postId: p.postId,
  sectionId: p.sectionId,
  uid: p.uid,
  username: p.username,
  title: p.title,
  content: p.content,
  status: p.status,
  viewCount: p.viewCount,
  likeCount: p.likeCount,
  favoriteCount: p.favoriteCount,
  commentCount: p.commentCount,
  liked: p.liked,
  favorited: p.favorited,
  createTime: p.createTime,
  images: p.images,
  topics: p.topics,
  videos: p.videos,
})

const pickComment = (c) => ({
  commentId: c.commentId,
  postId: c.postId,
  uid: c.uid,
  username: c.username,
  content: c.content,
  likeCount: c.likeCount,
  liked: c.liked,
  createTime: c.createTime,
  parentId: c.parentId,
  replyUid: c.replyUid,
  replyUsername: c.replyUsername,
  replyCount: c.replyCount,
  children: Array.isArray(c.children) ? c.children.map(pickComment) : c.children,
})

function writeFixture(name, payload) {
  if (DRY) return
  fs.mkdirSync(OUT_DIR, { recursive: true })
  fs.writeFileSync(
    path.join(OUT_DIR, name),
    `${JSON.stringify(payload, null, 2)}\n`,
    'utf8',
  )
  console.log(`  写入 community-fixtures/${name}`)
}

async function main() {
  console.log(`[1/3] 登录 ${USERNAME}（会顶掉该账号旧会话，可接受）`)
  const token = await login()
  console.log(`  ok  拿到 token（长度 ${token.length}，**不落盘**）`)
  const headers = { username: USERNAME, token }

  console.log('[2/3] GET 真实数据')
  const feed = await getJson('/post', headers, { pageNum: 1, pageSize: 10 })
  note('/post total/pages', `${feed.total}/${feed.pages}`)
  note('/post postIds', feed.records.map((r) => r.postId))

  const detail178 = await getJson('/post/178', headers)
  const detail181 = await getJson('/post/181', headers)
  note('/post/181 images', detail181.images)

  const comments178 = await getJson('/comment', headers, { postId: 178 })
  note('/comment?postId=178 顺序', comments178.records.map((c) => c.commentId))
  const c101 = comments178.records.find((c) => c.commentId === 101)
  note('评论 101 children 顺序', c101 ? c101.children.map((c) => c.commentId) : '没找到 101')

  const replies101 = await getJson('/comment/reply', headers, { commentId: 101 })
  note('/comment/reply?commentId=101 顺序', replies101.records.map((c) => c.commentId))

  const hotTopics = await getJson('/topic/hot', headers, { size: 10 })
  note('/topic/hot', hotTopics)

  const sections = await getJson('/section', headers)
  note('/section', sections.map((s) => `${s.sectionId}:${s.name}`))

  console.log('[3/3] 实测事实（供断言直接引用）')
  const anyPost = feed.records[0]
  note('列表 isTop 的 JSON 字面量与类型', `${JSON.stringify(anyPost.isTop)} (${typeof anyPost.isTop})`)
  note('列表 liked 的类型', `${typeof anyPost.liked} / favorited ${typeof anyPost.favorited}`)
  note('列表有没有 content 字段', Object.prototype.hasOwnProperty.call(anyPost, 'content'))
  note(
    '详情有没有 isTop / coverUrl',
    `${Object.prototype.hasOwnProperty.call(detail178, 'isTop')} / ${Object.prototype.hasOwnProperty.call(detail178, 'coverUrl')}`,
  )
  const noReply = comments178.records.find((c) => !c.children || c.children.length === 0)
  if (noReply) {
    note(
      `一级评论 ${noReply.commentId} 无回复时的 replyCount/children`,
      `${JSON.stringify(noReply.replyCount)} (${typeof noReply.replyCount}) / ${JSON.stringify(noReply.children)}`,
    )
  }
  note('一级评论 101 的 parentId', `${JSON.stringify(c101.parentId)} (${typeof c101.parentId})`)
  const replyItem = replies101.records[0]
  note(
    '回复项的 replyCount/children',
    `${JSON.stringify(replyItem.replyCount)} / ${JSON.stringify(replyItem.children)}`,
  )
  note('话题 name 含 # 吗', hotTopics.map((t) => t.name.includes('#')))
  note('详情 images 的 sort', detail181.images.map((i) => i.sort))
  note('uuid=1 单帖 commentCount（含回复）', detailsOf(feed.records, 178))

  writeFixture('feed.json', { pageNum: feed.pageNum, pageSize: feed.pageSize, pages: feed.pages, total: feed.total, records: feed.records.map(pickPost) })
  writeFixture('sections.json', sections)
  writeFixture('detail-178.json', pickDetail(detail178))
  writeFixture('detail-181.json', pickDetail(detail181))
  writeFixture('comments-178.json', {
    records: comments178.records.map(pickComment),
    size: comments178.size,
    hasMore: comments178.hasMore,
    nextCursor: comments178.nextCursor,
  })
  writeFixture('replies-101.json', {
    records: replies101.records.map(pickComment),
    size: replies101.size,
    hasMore: replies101.hasMore,
    nextCursor: replies101.nextCursor,
  })
  writeFixture('topic-hot.json', hotTopics)

  if (failed) process.exitCode = 1
}

function detailsOf(records, postId) {
  const p = records.find((r) => r.postId === postId)
  return p ? p.commentCount : '不在第一页'
}

main().catch((e) => {
  failed++
  console.error(`FAIL ${e && e.stack ? e.stack : e}`)
  process.exitCode = 1
})