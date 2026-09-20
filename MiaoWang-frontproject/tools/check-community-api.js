/**
 * 社区接线的离线校验（Web）
 *
 * 由来：社区经网关的**每个接口都要登录**，Agent 无法登录，也不允许调写接口造数据，
 * 所以社区页没法用真调用联调。这个脚本不碰网络（全程用假 fetch 接住请求），做六件事：
 *
 * A) 纯函数：相对时间 / 计数 / 昵称兜底 / 标题兜底 / 媒体过滤 / 版块名兜底
 * B) **请求形状**：把 `src/api/community.ts` 与 `src/api/request.ts` 一起 transpile 进 Node，
 *    断言每个函数发出的 method + URL + query + 鉴权头，以及两套返回信封的解析结果。
 * C) **页面状态机**：把 `src/views/CommunityView.vue` 的 `<script setup>` 用
 *    `@vue/compiler-sfc` 编译出来（临时插一行探针把内部状态挂到 globalThis），在无 DOM 的
 *    Node 里真的跑一遍 `fetchFeed / loadMore / selectSection / selectTopic`，
 *    断言两套分页信封的推进、`nextCursor` 原样回传、以及"空页 + pages 更大"这类会死循环的边界。
 * D) **帖子详情页状态机**：同样真跑 `PostDetailView.vue` 的 setup（评论游标分页 / 点赞时序 / 回复桶响应式）。
 * E) **真实 JSON 驱动**：`community-fixtures/` 里的 6 个响应体来自线上库真实种子数据
 *    （帖子 178~182 / 评论 101~106 / 话题 68，由 `fetch-community-fixtures.js` 用 GET 固化），
 *    把它喂进 `api/community.ts` 与两个页面的状态机，断言实测推翻过的那些假设都被改对：
 *    `isTop` 是数字、一级评论 `parentId=0`、`children=[]`/`replyCount=0` 不炸、回复项 `null` 不炸、
 *    图片按 `sort` 正序、话题名不含 `#`、`commentCount` 含回复。
 * F) **口径静态护栏**：禁止严格比较（`isTop === true` / `isTop === 1`）与 `parentId === null`
 *    这类已实测判错的写法重新溜回两端源码。
 *
 * 用法：node tools/check-community-api.js（退出码 = 失败断言数）
 */
const fs = require('fs')
const path = require('path')
const { createRequire } = require('module')

const WEB = path.resolve(__dirname, '..', 'MiaoWang-web')
// typescript / vue / @vue/compiler-sfc 都装在 Web 工程里，不在 tools 里，从那边解析
const req = createRequire(path.join(WEB, 'package.json'))
const ts = req('typescript')

let failures = 0
function check(label, cond, detail) {
  if (cond) {
    console.log(`  ok   ${label}`)
  } else {
    failures++
    console.log(`  FAIL ${label}${detail === undefined ? '' : `  →  ${detail}`}`)
  }
}

/** 把单个 .ts / 编译产物 transpile 成 CJS 并就地求值 */
function evalTs(source, file, resolve) {
  const js = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
    },
    // ⚠️ 这里必须是 .ts：transpileModule 按扩展名决定语法的 TS/JS 模式，
    // 传 `.vue` 这类未知扩展名会退化成 JS 模式，`import type` 与类型注解都会变成语法错误
    fileName: file.endsWith('.ts') ? file : 'mw-check.ts',
  }).outputText
  const mod = { exports: {} }
  // eslint-disable-next-line no-new-func
  new Function('exports', 'require', 'module', '__filename', '__dirname', js)(
    mod.exports,
    resolve,
    mod,
    file,
    path.dirname(file),
  )
  return mod.exports
}

const loadTs = (file, resolve) => evalTs(fs.readFileSync(file, 'utf8'), file, resolve)

/* ---------- 假的 localStorage / fetch（只为让 request.ts 能跑） ---------- */

const store = new Map()
globalThis.localStorage = {
  getItem: (k) => (store.has(k) ? store.get(k) : null),
  setItem: (k, v) => store.set(k, String(v)),
  removeItem: (k) => store.delete(k),
}

/** 单发信封；`queue` 里有货就先取（页面状态机要按次序喂不同的一页） */
let envelope = { code: '0', data: null, success: true }
const queue = []
const history = []

globalThis.fetch = async (url, init) => {
  history.push({ url, init })
  const env = queue.length ? queue.shift() : envelope
  return {
    status: 200,
    ok: true,
    text: async () => JSON.stringify(env),
  }
}

const resetFetch = () => {
  queue.length = 0
  history.length = 0
}

/** 造一页页码分页信封 */
const paged = (records, pageNum, pages, total) => ({
  code: '0',
  data: { pageNum, pageSize: 12, pages, total, records },
  success: true,
})

/** 造一页游标分页信封（`size` 是**请求值**，与记录条数无关） */
const cursor = (records, hasMore, nextCursor) => ({
  code: '0',
  data: { records, size: 12, hasMore, nextCursor },
  success: true,
})

const post = (postId) => ({
  postId,
  sectionId: 1,
  uid: 7,
  username: null,
  title: `帖子 ${postId}`,
  coverUrl: null,
  isTop: 0,
  viewCount: 1,
  likeCount: 2,
  favoriteCount: 3,
  commentCount: 4,
  liked: false,
  favorited: false,
  topics: [],
  videos: [],
  createTime: new Date().toISOString(),
})

/* ---------- 加载模块 ---------- */

const requestMod = loadTs(path.join(WEB, 'src/api/request.ts'), (id) => {
  throw new Error(`意外的 import：${id}`)
})
const community = loadTs(path.join(WEB, 'src/api/community.ts'), (id) =>
  id === './request' ? requestMod : null,
)

const PREFIX = '/api/miaowang-community/mapleleaf/miaowang/v1/community'

/** 发一次请求并取回 URL / method / headers */
async function fire(fn) {
  resetFetch()
  await fn()
  if (!history.length) throw new Error('没有发出请求')
  const { url, init } = history[history.length - 1]
  return {
    url,
    method: (init && init.method) || 'GET',
    headers: (init && init.headers) || {},
    body: init && init.body,
  }
}

/* ---------- A. 纯函数 ---------- */

async function partA() {
  console.log('[A] 格式化与兜底')
  const now = Date.now()
  const iso = (msAgo) => new Date(now - msAgo).toISOString()
  check('刚刚（30 秒前）', community.relativeTime(iso(30_000)) === '刚刚')
  check('N 分钟前', community.relativeTime(iso(5 * 60_000)) === '5 分钟前')
  check('N 小时前', community.relativeTime(iso(3 * 3600_000)) === '3 小时前')
  check('N 天前', community.relativeTime(iso(3 * 86400_000)) === '3 天前')
  check(
    '超过一周给绝对时间',
    /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/.test(community.relativeTime(iso(30 * 86400_000))),
    community.relativeTime(iso(30 * 86400_000)),
  )
  check(
    '时钟快于服务端时不给负数',
    community.relativeTime(new Date(now + 60_000).toISOString()) === '刚刚',
  )
  check('空时间给空串', community.relativeTime(null) === '')

  check('计数 0', community.countText(0) === '0')
  check('计数 9999 不折算', community.countText(9999) === '9999')
  check('计数 12345 折算', community.countText(12345) === '1.2万')
  check('计数 1000000 折算', community.countText(1000000) === '100万')
  check('计数 null 兜底', community.countText(null) === '0')

  // 报告实测：库里没有帖子时 username 恒为 null → 必须按 uid 兜底
  check('昵称用真值', community.authorName('MapleLeaf', 1) === 'MapleLeaf')
  check('昵称 null 按 uid 兜底', community.authorName(null, 7) === '用户 7')
  check('昵称空串按 uid 兜底', community.authorName('   ', 7) === '用户 7')
  check('昵称与 uid 都没有', community.authorName(null, null) === '匿名用户')
  check('头像首字', community.initialOf('MapleLeaf') === 'M')

  check('标题为空给占位', community.titleText(null) === '无标题')
  check('标题保留真值', community.titleText('  剪指甲  ') === '剪指甲')

  check('topics null 收敛成空数组', community.topicList(null).length === 0)
  check('topics 原样透传', community.topicList([{ topicId: 1, name: '猫', postCount: 2 }]).length === 1)

  // 报告：缺 url 的媒体项会被后端静默丢弃，前端也不该渲染裂图
  const media = community.mediaList([{ url: 'a' }, { objectKey: 'k' }, { url: 'b' }])
  check('媒体只保留有 url 的项', media.length === 2 && media[1].url === 'b', JSON.stringify(media))
  check('媒体 null 收敛成空数组', community.mediaList(null).length === 0)

  const sections = [{ sectionId: 3, name: '幼宠专区' }]
  check('版块名按 id 查', community.sectionName(sections, 3) === '幼宠专区')
  check('版块名查不到退回 id', community.sectionName(sections, 9) === '版块 9')
  check('版块名空 id 给空串', community.sectionName(sections, null) === '')

  check(
    '绝对时间格式',
    /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/.test(community.fullTimeText('2026-09-17T06:20:53.000+00:00')),
  )
}

/* ---------- B. 请求形状（不碰网络） ---------- */

async function partB() {
  console.log('\n[B] 请求形状（假 fetch 接住，不发真实请求）')

  // 先置一个登录态：经网关调社区必须同时带 username + token 两个头
  store.set('mw_username', 'probe114904')
  store.set('mw_token', 'fake.jwt.token')

  let r = await fire(() => community.listSections())
  check('版块列表 GET /section', r.method === 'GET' && r.url === `${PREFIX}/section`, r.url)
  check(
    '鉴权头两个都带上',
    r.headers.username === 'probe114904' && r.headers.token === 'fake.jwt.token',
    JSON.stringify(r.headers),
  )

  // 关键：不传 sectionId 才是全站流（契约里没有"推荐"版块）
  r = await fire(() => community.listPosts({}))
  check('信息流不带 sectionId', r.url === `${PREFIX}/post`, r.url)
  r = await fire(() => community.listPosts({ sectionId: null, pageNum: 2, pageSize: 12 }))
  check('sectionId=null 也不下发', r.url === `${PREFIX}/post?pageNum=2&pageSize=12`, r.url)
  r = await fire(() => community.listPosts({ sectionId: 5, pageNum: 1, pageSize: 50 }))
  check(
    'sectionId 作为 query 下发',
    r.url === `${PREFIX}/post?sectionId=5&pageNum=1&pageSize=50`,
    r.url,
  )

  r = await fire(() => community.getPost(7))
  check('详情 GET /post/7', r.url === `${PREFIX}/post/7`, r.url)

  r = await fire(() => community.listComments({ postId: 3, lastId: 123, size: 10 }))
  check('评论列表游标原样回传', r.url === `${PREFIX}/comment?postId=3&lastId=123&size=10`, r.url)
  r = await fire(() => community.listComments({ postId: 3 }))
  check('评论首屏不带 lastId', r.url === `${PREFIX}/comment?postId=3`, r.url)

  r = await fire(() => community.listCommentReplies({ commentId: 9, lastId: 88, size: 10 }))
  check(
    '回复列表游标原样回传',
    r.url === `${PREFIX}/comment/reply?commentId=9&lastId=88&size=10`,
    r.url,
  )

  r = await fire(() => community.listHotTopics(6))
  check('话题热榜裸数组接口', r.url === `${PREFIX}/topic/hot?size=6`, r.url)

  r = await fire(() => community.listTopicPosts({ topicId: 4, lastId: 77, size: 12 }))
  check('话题下帖子', r.url === `${PREFIX}/topic/4/post?lastId=77&size=12`, r.url)

  r = await fire(() => community.listMyLikedPosts({ pageNum: 1, pageSize: 12 }))
  check('我的点赞（页码分页）', r.url === `${PREFIX}/my/like?pageNum=1&pageSize=12`, r.url)
  r = await fire(() => community.listMyFavoritePosts({ pageNum: 2, pageSize: 12 }))
  check('我的收藏（页码分页）', r.url === `${PREFIX}/my/favorite?pageNum=2&pageSize=12`, r.url)

  // 点赞 / 收藏必须是 POST + DELETE 成对
  r = await fire(() => community.likePost(11))
  check('点赞 POST', r.method === 'POST' && r.url === `${PREFIX}/like/11`, `${r.method} ${r.url}`)
  r = await fire(() => community.unlikePost(11))
  check(
    '取消点赞 DELETE 同一路径',
    r.method === 'DELETE' && r.url === `${PREFIX}/like/11`,
    `${r.method} ${r.url}`,
  )
  r = await fire(() => community.favoritePost(11))
  check('收藏 POST', r.method === 'POST' && r.url === `${PREFIX}/favorite/11`, `${r.method} ${r.url}`)
  r = await fire(() => community.unfavoritePost(11))
  check(
    '取消收藏 DELETE 同一路径',
    r.method === 'DELETE' && r.url === `${PREFIX}/favorite/11`,
    `${r.method} ${r.url}`,
  )

  // 写接口：只断言请求体字段名与路径，**不真调**（这里被假 fetch 接住）
  r = await fire(() => community.createPost({ sectionId: 1, title: 't', content: 'c' }))
  check('发帖 POST /post', r.method === 'POST' && r.url === `${PREFIX}/post`, r.url)
  check('发帖体字段名', r.body === JSON.stringify({ sectionId: 1, title: 't', content: 'c' }), r.body)
  r = await fire(() =>
    community.createComment({ postId: 3, content: 'hi', parentId: 5, replyUid: 7 }),
  )
  check('评论 POST /comment', r.method === 'POST' && r.url === `${PREFIX}/comment`, r.url)
  check(
    '评论体字段名（parentId + replyUid）',
    r.body === JSON.stringify({ postId: 3, content: 'hi', parentId: 5, replyUid: 7 }),
    r.body,
  )

  // 两套信封的解析结果
  envelope = { code: '0', message: null, data: { records: [], pageNum: 1 }, success: true }
  const data = await community.listPosts({})
  check('业务信封 code=0 取 data', data && data.pageNum === 1, JSON.stringify(data))

  envelope = { state: -1, message: '未登录或者的登录过期', data: null }
  let unauthorized = false
  try {
    await community.listSections()
  } catch (e) {
    unauthorized = e instanceof requestMod.ApiError && e.isUnauthorized
  }
  check('网关信封 state=-1 归一成 isUnauthorized', unauthorized)

  envelope = { code: 'B000301', message: '帖子不存在或已被删除', success: false }
  let msg = ''
  try {
    await community.getPost(1)
  } catch (e) {
    msg = community.communityErrorMessage(e)
  }
  check('业务错误码翻成人话', msg === '帖子不存在或已被删除', msg)

  check(
    'B000305 翻译',
    community.communityErrorMessage(new requestMod.ApiError('B000305', '请选择版块')) ===
      '请先选择要发到哪个版块',
  )

  // 未登录态：不该带残缺的鉴权头
  envelope = { code: '0', data: { records: [] }, success: true }
  store.clear()
  r = await fire(() => community.listPosts({}))
  check(
    '本地无会话时不带鉴权头',
    r.headers.username === undefined && r.headers.token === undefined,
    JSON.stringify(r.headers),
  )
}

/* ---------- C. 页面状态机（把 CommunityView 的 script setup 真跑一遍） ---------- */

/**
 * 用 @vue/compiler-sfc 把 `<script setup>` 编译出来，并临时插一行探针，
 * 把内部状态挂到 globalThis 供断言读取（只改内存里的字符串，不动磁盘上的文件）。
 */
function loadCommunityView() {
  const { parse, compileScript } = req('@vue/compiler-sfc')
  const file = path.join(WEB, 'src/views/CommunityView.vue')
  const probe = `
globalThis.__mwCommunity = {
  posts, feedLoading, moreLoading, feedError, unauthorized, needLogin, emptyState, countLabel,
  mode, activeSectionId, activeTopic, pageNum, pages, total, nextCursor, hasMore,
  sections, sectionsError, topics, topicsError,
  fetchFeed, loadMore, selectSection, selectMode, selectTopic, resetToFlow, loadSections, loadTopics,
}
`
  const source = fs.readFileSync(file, 'utf8').replace('</script>', `${probe}</script>`)
  const { descriptor, errors } = parse(source, { filename: file })
  if (errors.length) throw new Error(`SFC 解析失败：${errors[0].message}`)
  const compiled = compileScript(descriptor, { id: 'probe-community' })

  const authStub = { isLoggedIn: true, username: 'probe114904', token: 'fake.jwt.token' }
  const icons = new Proxy({}, { get: () => ({}) })
  const mod = evalTs(compiled.content, file + '.js', (id) => {
    if (id === 'vue') return req('vue')
    if (id === 'vue-router') {
      return { RouterLink: {}, useRoute: () => ({ params: {}, fullPath: '/community' }) }
    }
    if (id === '@phosphor-icons/vue') return icons
    if (id === '@/api/request') return requestMod
    if (id === '@/api/community') return community
    if (id === '@/stores/auth') return { useAuthStore: () => authStub }
    throw new Error(`意外的 import：${id}`)
  })

  const component = mod.default
  if (!component || typeof component.setup !== 'function') throw new Error('拿不到 setup')
  // 无 DOM 环境手动跑 setup（onMounted 的自动加载在测试里由断言自己驱动）。
  // setup 里的 onMounted 没有活动实例，Vue 会打一条 warn，这里临时静音，免得盖住断言输出
  const warn = console.warn
  console.warn = () => {}
  try {
    component.setup({}, { expose: () => {}, attrs: {}, slots: {}, emit: () => {} })
  } finally {
    console.warn = warn
  }
  const p = globalThis.__mwCommunity
  if (!p) throw new Error('探针没挂上')
  return p
}

const flush = () => new Promise((r) => setTimeout(r, 0))

async function partC() {
  console.log('\n[C] 页面状态机（真实跑 CommunityView 的 setup，全程假 fetch）')
  store.set('mw_username', 'probe114904')
  store.set('mw_token', 'fake.jwt.token')

  const p = loadCommunityView()

  // --- 页码分页：三页，逐页"加载更多" ---
  resetFetch()
  queue.push(paged([post(1), post(2), post(3)], 1, 3, 7))
  await p.fetchFeed(false)
  check('首屏页码分页：取到 3 条', p.posts.value.length === 3, String(p.posts.value.length))
  check('首屏页码分页：total=7', p.total.value === 7, String(p.total.value))
  check('首屏页码分页：还有下一页', p.hasMore.value === true)
  check('首屏：加载态已复位', p.feedLoading.value === false && p.moreLoading.value === false)

  queue.push(paged([post(4), post(5)], 2, 3, 7))
  await p.loadMore()
  check('加载更多：追加而不是替换', p.posts.value.length === 5, String(p.posts.value.length))
  check('加载更多：第 2 页（pageNum=2）', p.pageNum.value === 2, String(p.pageNum.value))
  check(
    '加载更多：请求的是第 2 页',
    /[?&]pageNum=2\b/.test(history[history.length - 1].url),
    history[history.length - 1].url,
  )

  queue.push(paged([post(6), post(7)], 3, 3, 7))
  await p.loadMore()
  check('最后一页：pageNum=3', p.pageNum.value === 3)
  check('最后一页之后不再有下一页', p.hasMore.value === false)

  // 边界：后端回了空页但 pages 更大 → 不能无限加载
  resetFetch()
  p.posts.value = []
  p.hasMore.value = true
  queue.push(paged([], 4, 9, 7))
  await p.loadMore()
  check('空页不会造成无限加载', p.hasMore.value === false)

  // --- 版块筛选：不传 sectionId 才是全站流 ---
  resetFetch()
  p.mode.value = 'flow'
  p.activeSectionId.value = 5
  p.activeTopic.value = null
  queue.push(paged([post(8)], 1, 1, 1))
  p.selectSection(null)
  await flush()
  await flush()
  check('切到"全部"：不带 sectionId', !/sectionId=/.test(history[0].url), history[0].url)
  check('切到"全部"：activeSectionId 归 null', p.activeSectionId.value === null)

  resetFetch()
  queue.push(paged([post(9)], 1, 1, 1))
  p.selectSection(3)
  await flush()
  await flush()
  check('切到某个版块：带 sectionId=3', /[?&]sectionId=3\b/.test(history[0].url), history[0].url)
  check('切版块走的是信息流接口', /\/community\/post\?/.test(history[0].url), history[0].url)

  // --- 我的点赞 / 收藏（页码分页，走另外两个路径） ---
  resetFetch()
  queue.push(paged([post(10)], 1, 1, 1))
  p.selectMode('like')
  await flush()
  await flush()
  check('我的点赞走 /my/like', /\/my\/like\?/.test(history[0].url), history[0].url)
  check('我的点赞空态文案', p.emptyState.value.title === '还没有赞过的帖子', p.emptyState.value.title)

  resetFetch()
  queue.push(paged([post(11)], 1, 1, 1))
  p.selectMode('favorite')
  await flush()
  await flush()
  check('我的收藏走 /my/favorite', /\/my\/favorite\?/.test(history[0].url), history[0].url)

  // --- 游标分页：nextCursor 必须原样回传 ---
  resetFetch()
  queue.push(cursor([post(20), post(21)], true, 21))
  p.selectTopic({ topicId: 4, name: '猫', postCount: 2 })
  await flush()
  await flush()
  check('话题流走 /topic/4/post', /\/topic\/4\/post/.test(history[0].url), history[0].url)
  check('话题流首屏不带 lastId', !/lastId=/.test(history[0].url), history[0].url)
  check('游标分页：hasMore=true 且记下 nextCursor', p.hasMore.value === true && p.nextCursor.value === 21, String(p.nextCursor.value))

  resetFetch()
  queue.push(cursor([post(22)], false, null))
  await p.loadMore()
  check('游标分页：lastId 原样回传 21', /[?&]lastId=21\b/.test(history[0].url), history[0].url)
  check('游标分页：hasMore=false 收尾', p.hasMore.value === false)

  // 边界：hasMore=true 但 nextCursor=null → 不能原地打转
  resetFetch()
  p.hasMore.value = true
  queue.push(cursor([post(23)], true, null))
  await p.loadMore()
  check('"有更多但没游标"不会原地打转', p.hasMore.value === false)

  // --- 未登录：翻到登录态，而不是显示半截页面 ---
  resetFetch()
  envelope = { state: -1, message: '未登录或者的登录过期', data: null }
  await p.fetchFeed(false)
  check('网关 state=-1：标记未登录', p.unauthorized.value === true)
  check('网关 state=-1：进入去登录态', p.needLogin.value === true)
  check(
    '网关 state=-1：不把它当"帖子读失败"',
    p.feedError.value === '登录状态失效了，请重新登录',
    p.feedError.value,
  )

  // --- 业务错误与空列表 ---
  resetFetch()
  p.unauthorized.value = false
  envelope = { code: 'B000001', message: '系统执行出错', success: false }
  await p.fetchFeed(false)
  check(
    'B000001 给出可操作的解释',
    /服务没能处理这个请求/.test(p.feedError.value),
    p.feedError.value,
  )

  resetFetch()
  // 回到信息流（页码分页）再看空列表，游标模式下计数文案是"已加载 N 篇"
  p.mode.value = 'flow'
  p.activeTopic.value = null
  envelope = paged([], 1, 0, 0)
  await p.fetchFeed(false)
  check('空列表：posts 为空且无下一页', p.posts.value.length === 0 && p.hasMore.value === false)
  check('空列表：计数显示 0 篇', p.countLabel.value === '0 篇', p.countLabel.value)

  resetFetch()
  envelope = { code: '0', data: [{ sectionId: 1, name: '宠物美照' }, { sectionId: 2, name: '宠物知识' }], success: true }
  await p.loadSections()
  check('版块列表落库 2 条', p.sections.value.length === 2, String(p.sections.value.length))
  check('版块读取无错误', p.sectionsError.value === '')

  resetFetch()
  envelope = { code: '0', data: [{ topicId: 1, name: '猫', postCount: 3 }], success: true }
  await p.loadTopics()
  check('话题热榜落库 1 条', p.topics.value.length === 1, String(p.topics.value.length))

  resetFetch()
  envelope = { code: 'B000001', message: '系统执行出错', success: false }
  await p.loadTopics()
  // 失败时保留上一次的列表（面板优先显示错误），错误文案必须落到 TopicsError 上
  check(
    '话题热榜失败时给出错误',
    p.topicsError.value !== '' && /服务没能处理这个请求/.test(p.topicsError.value),
    p.topicsError.value,
  )
}

/* ---------- D. 帖子详情页状态机（评论游标分页 / 点赞收藏时序 / 回复响应式） ---------- */

const vue = req('vue')

/**
 * 造一条评论（形状照**实测**：一级评论 `parentId` 是数字 `0`、无回复时 `replyCount` 是 `0` 且 `children` 是 `[]`）
 *
 * ⚠️ 早先这里写的是 `parentId: null` / `children: null`（源码推断的错形状），
 * 于是 `topLevelIdOf` 的 bug 一路绿灯通过。现在按真实 JSON 造，错误形状不再被放过。
 */
const comment = (commentId, extra = {}) => ({
  commentId,
  postId: 7,
  uid: 11,
  username: null,
  content: `评论 ${commentId}`,
  likeCount: 0,
  liked: false,
  createTime: new Date().toISOString(),
  parentId: 0,
  replyUid: null,
  replyUsername: null,
  replyCount: 0,
  children: [],
  ...extra,
})

/** 造一条回复（实测：`parentId` 是一级评论 id、`replyCount` / `children` 都是 `null`） */
const reply = (commentId, parentId) =>
  comment(commentId, {
    parentId,
    replyCount: null,
    children: null,
    uid: 99,
    replyUid: 11,
    replyUsername: null,
  })

const detail = (extra = {}) => ({
  postId: 7,
  sectionId: 1,
  uid: 7,
  username: null,
  title: '标题',
  content: '正文',
  status: 2,
  viewCount: 12,
  likeCount: 2,
  favoriteCount: 3,
  commentCount: 2,
  liked: false,
  favorited: false,
  createTime: new Date().toISOString(),
  images: [{ url: 'https://example.test/a.jpg', objectKey: 'k', sort: 0 }],
  topics: [],
  videos: [],
  ...extra,
})

function loadPostDetailView() {
  const { parse, compileScript } = req('@vue/compiler-sfc')
  const file = path.join(WEB, 'src/views/PostDetailView.vue')
  const probe = `
globalThis.__mwPostDetail = {
  post, loading, error, unauthorized, needLogin, sections, images, videos, topics,
  liked, favorited, likeCount, favoriteCount, interactBusy, interactError,
  comments, commentsLoading, commentsMore, commentsError, commentsTotal, commentsCursor, commentsHasMore,
  replies, bucketOf, replyTarget, draft, replyDraft, submittingScope, commentError, commentNotice,
  loadPost, loadSections, loadComments, loadReplies, toggleLike, toggleFavorite,
  startReply, cancelReply, topLevelIdOf, submitComment,
}
`
  const source = fs.readFileSync(file, 'utf8').replace('</script>', `${probe}</script>`)
  const { descriptor, errors } = parse(source, { filename: file })
  if (errors.length) throw new Error(`SFC 解析失败：${errors[0].message}`)
  const compiled = compileScript(descriptor, { id: 'probe-post' })

  const authStub = { isLoggedIn: true, username: 'probe114904', token: 'fake.jwt.token' }
  const icons = new Proxy({}, { get: () => ({}) })
  const mod = evalTs(compiled.content, file + '.probe', (id) => {
    if (id === 'vue') return vue
    if (id === 'vue-router') {
      return {
        RouterLink: {},
        useRoute: () => ({ params: { postId: '7' }, fullPath: '/community/post/7' }),
      }
    }
    if (id === '@phosphor-icons/vue') return icons
    if (id === '@/api/request') return requestMod
    if (id === '@/api/community') return community
    if (id === '@/stores/auth') return { useAuthStore: () => authStub }
    throw new Error(`意外的 import：${id}`)
  })

  const component = mod.default
  if (!component || typeof component.setup !== 'function') throw new Error('拿不到 setup')
  const warn = console.warn
  console.warn = () => {}
  try {
    component.setup({}, { expose: () => {}, attrs: {}, slots: {}, emit: () => {} })
  } finally {
    console.warn = warn
  }
  const p = globalThis.__mwPostDetail
  if (!p) throw new Error('探针没挂上')
  return p
}

async function partD() {
  console.log('\n[D] 帖子详情页状态机（真实跑 PostDetailView 的 setup，全程假 fetch）')
  store.set('mw_username', 'probe114904')
  store.set('mw_token', 'fake.jwt.token')

  const p = loadPostDetailView()

  // --- 详情：字段落库 + 交互状态镜像 ---
  resetFetch()
  envelope = { code: '0', data: detail({ liked: true, favorited: false, likeCount: 5 }), success: true }
  await p.loadPost()
  check('详情：取到帖子', p.post.value?.postId === 7, String(p.post.value?.postId))
  check('详情：详情 DTO 的正文可用', p.post.value?.content === '正文')
  check('详情：图片从 images 里取', p.images.value.length === 1, String(p.images.value.length))
  check('详情：liked 镜像到本地', p.liked.value === true)
  check('详情：计数镜像到本地', p.likeCount.value === 5 && p.favoriteCount.value === 3, String(p.likeCount.value))
  check('详情：评论总数镜像到本地', p.commentsTotal.value === 2)

  // --- 详情失败 / 未登录 ---
  resetFetch()
  envelope = { code: 'B000301', message: '帖子不存在或已被删除', success: false }
  await p.loadPost()
  check('详情：B000301 翻成人话', p.error.value === '帖子不存在或已被删除', p.error.value)
  check('详情：失败后不再转圈', p.loading.value === false)

  resetFetch()
  envelope = { state: -1, message: '未登录或者的登录过期', data: null }
  await p.loadPost()
  check('详情：网关 state=-1 进入去登录态', p.unauthorized.value === true && p.needLogin.value === true)

  // 回到正常状态继续
  p.unauthorized.value = false
  resetFetch()
  envelope = { code: '0', data: detail({ liked: false, favorited: false, likeCount: 2 }), success: true }
  await p.loadPost()

  // --- 评论：游标分页 ---
  resetFetch()
  queue.push(
    cursor(
      [
        comment(10, { children: [reply(11, 10), reply(12, 10)], replyCount: 5 }),
        comment(9),
      ],
      true,
      9,
    ),
  )
  await p.loadComments(false)
  check('评论：首屏 2 条', p.comments.value.length === 2, String(p.comments.value.length))
  check('评论：首屏不带 lastId', !/lastId=/.test(history[0].url), history[0].url)
  check('评论：hasMore + nextCursor 记下来', p.commentsHasMore.value === true && p.commentsCursor.value === 9)
  check(
    '评论：一级评论的 children 预览进了回复桶',
    p.bucketOf(10).items.length === 2 && p.bucketOf(10).hasMore === true,
    JSON.stringify({ n: p.bucketOf(10).items.length, more: p.bucketOf(10).hasMore }),
  )

  resetFetch()
  queue.push(cursor([comment(8)], true, 8))
  await p.loadComments(true)
  check('评论：加载更多是追加', p.comments.value.length === 3, String(p.comments.value.length))
  check('评论：加载更多带 lastId=9', /[?&]lastId=9\b/.test(history[history.length - 1].url), history[history.length - 1].url)

  // --- 响应式回归：回复桶必须是响应式代理，否则"查看全部回复"点了没反应 ---
  let bucketHits = 0
  const stop = vue.watch(
    () => p.replies.value[10]?.items.length ?? 0,
    () => {
      bucketHits++
    },
    { flush: 'sync' },
  )
  resetFetch()
  queue.push(cursor([reply(11, 10), reply(12, 10), reply(13, 10)], true, 13))
  await p.loadReplies(p.comments.value[0] ?? comment(10))
  stop()
  check('回复：真拉第一页替换预览（3 条）', p.bucketOf(10).items.length === 3, String(p.bucketOf(10).items.length))
  check('回复：请求带 commentId=10 且首屏不带 lastId', /commentId=10/.test(history[0].url) && !/lastId=/.test(history[0].url), history[0].url)
  check('回复：bucket 变更能触发视图更新（响应式）', bucketHits > 0, `watch 触发 ${bucketHits} 次`)

  resetFetch()
  queue.push(cursor([reply(14, 10)], false, null))
  await p.loadReplies(p.comments.value[0] ?? comment(10))
  check('回复：续页用 nextCursor=13', /[?&]lastId=13\b/.test(history[history.length - 1].url), history[history.length - 1].url)
  check('回复：续页后 4 条且收尾', p.bucketOf(10).items.length === 4 && p.bucketOf(10).hasMore === false, String(p.bucketOf(10).items.length))

  // 游标边界：有更多但没有游标
  resetFetch()
  p.commentsHasMore.value = true
  queue.push(cursor([comment(7)], true, null))
  await p.loadComments(true)
  check('评论："有更多但没游标"不会原地打转', p.commentsHasMore.value === false)

  // --- 点赞 / 收藏：先等接口成功，再改界面 ---
  p.liked.value = false
  p.likeCount.value = 2
  resetFetch()
  envelope = { code: 'B000301', message: '帖子不存在或已被删除', success: false }
  await p.toggleLike()
  check('点赞失败：界面不改（不乐观更新）', p.liked.value === false && p.likeCount.value === 2, `${p.liked.value}/${p.likeCount.value}`)
  check('点赞失败：给出错误文案', p.interactError.value === '帖子不存在或已被删除', p.interactError.value)

  resetFetch()
  envelope = { code: '0', data: null, success: true }
  await p.toggleLike()
  check('点赞成功：POST /like/7', history[history.length - 1].init.method === 'POST' && /\/like\/7$/.test(history[history.length - 1].url), history[history.length - 1].url)
  check('点赞成功：界面改为已赞 +1', p.liked.value === true && p.likeCount.value === 3, `${p.liked.value}/${p.likeCount.value}`)
  check('点赞成功：清掉上一次的错误', p.interactError.value === '')

  resetFetch()
  envelope = { code: '0', data: null, success: true }
  await p.toggleLike()
  check('取消点赞：DELETE 同一路径', history[history.length - 1].init.method === 'DELETE' && /\/like\/7$/.test(history[history.length - 1].url))
  check('取消点赞：界面回退且不会出现负数', p.liked.value === false && p.likeCount.value === 2, `${p.liked.value}/${p.likeCount.value}`)

  resetFetch()
  envelope = { code: '0', data: null, success: true }
  await p.toggleFavorite()
  check('收藏：POST /favorite/7 且计数 +1', /\/favorite\/7$/.test(history[history.length - 1].url) && p.favorited.value === true && p.favoriteCount.value === 4, `${p.favorited.value}/${p.favoriteCount.value}`)

  // --- 发评论 / 回复：字段口径 ---
  resetFetch()
  envelope = { code: '0', data: comment(30), success: true }
  queue.push(cursor([comment(30)], false, null))
  p.draft.value = '  一级评论  '
  await p.submitComment('top')
  const topBody = JSON.parse(history[0].init.body)
  check('发评论：POST /comment', history[0].init.method === 'POST' && /\/community\/comment$/.test(history[0].url), history[0].url)
  check('发评论：只带 postId + content（不带 parentId/replyUid）', topBody.postId === 7 && topBody.content === '一级评论' && !('parentId' in topBody) && !('replyUid' in topBody), JSON.stringify(topBody))
  check('发评论：草稿清空、提交态复位', p.draft.value === '' && p.submittingScope.value === '')
  check('发评论：评论数 +1', p.commentsTotal.value === 3, String(p.commentsTotal.value))

  resetFetch()
  envelope = { code: '0', data: comment(31), success: true }
  queue.push(cursor([comment(30)], false, null))
  // 回复"某条回复"（parentId=10，commentId=13）→ parentId 要挂到它的一级评论上
  p.startReply(reply(13, 10))
  p.replyDraft.value = '回复内容'
  await p.submitComment('reply')
  const replyBody = JSON.parse(history[0].init.body)
  check('发回复：parentId 挂到一级评论 10', replyBody.parentId === 10, JSON.stringify(replyBody))
  check('发回复：replyUid 是被回复的人 99', replyBody.replyUid === 99, JSON.stringify(replyBody))
  check('发回复：成功后关掉回复框', p.replyTarget.value === null && p.replyDraft.value === '')

  resetFetch()
  envelope = { code: '0', data: null, success: true }
  p.draft.value = '   '
  await p.submitComment('top')
  check('发评论：空内容前端拦住、不发请求', history.length === 0 && /不能为空/.test(p.commentError.value), p.commentError.value)

  // --- 回复目标解析（真实形状：一级评论 parentId 是数字 0） ---
  check('回复一级评论：parentId=自身（实测 parentId=0）', p.topLevelIdOf(comment(10)) === 10)
  check('回复二级回复：parentId=一级评论', p.topLevelIdOf(reply(13, 10)) === 10)
  check('parentId 为 null（旧推断形状）也退化成自身', p.topLevelIdOf(comment(10, { parentId: null })) === 10)
}

/* ---------- E. 真实 JSON 驱动（fixtures 来自线上库真实种子数据） ---------- */

const FIXTURE_DIR = path.join(__dirname, 'community-fixtures')
const fixturePath = (name) => path.join(FIXTURE_DIR, name)
const fixture = (name) => JSON.parse(fs.readFileSync(fixturePath(name), 'utf8'))
const fixtureRaw = (name) => fs.readFileSync(fixturePath(name), 'utf8')

async function partE() {
  console.log('\n[E] 真实 JSON 驱动（community-fixtures/，线上库种子数据：帖子 178~182 / 评论 101~106 / 话题 68）')

  for (const name of [
    'feed.json',
    'sections.json',
    'detail-178.json',
    'detail-181.json',
    'comments-178.json',
    'replies-101.json',
    'topic-hot.json',
  ]) {
    check(`fixture 存在：${name}`, fs.existsSync(fixturePath(name)))
  }
  check(
    'fixture 里没有 token（红线：token 不落盘）',
    ['feed.json', 'comments-178.json', 'replies-101.json', 'detail-178.json', 'sections.json', 'topic-hot.json'].every(
      (n) => !/"?token"?\s*:/.test(fixtureRaw(n)),
    ),
  )

  const feed = fixture('feed.json')
  const sections = fixture('sections.json')
  const c101 = fixture('comments-178.json').records.find((c) => c.commentId === 101)
  const c102 = fixture('comments-178.json').records.find((c) => c.commentId === 102)
  const r103 = fixture('replies-101.json').records.find((c) => c.commentId === 103)
  const d178 = fixture('detail-178.json')
  const d181 = fixture('detail-181.json')
  const hot = fixture('topic-hot.json')

  /* --- 真实 JSON 的形状本身（先把实测口径钉住） --- */
  check('列表 isTop 是数字', feed.records.every((p) => typeof p.isTop === 'number'), typeof feed.records[0].isTop)
  check('列表 liked/favorited 是布尔', feed.records.every((p) => typeof p.liked === 'boolean' && typeof p.favorited === 'boolean'))
  check('列表没有 content 字段', feed.records.every((p) => !Object.prototype.hasOwnProperty.call(p, 'content')))
  check('详情没有 isTop / coverUrl', !('isTop' in d178) && !('coverUrl' in d178))
  check('详情有 content 与 images', typeof d178.content === 'string' && Array.isArray(d181.images))
  check('一级评论 parentId 是数字 0', c101.parentId === 0 && c102.parentId === 0, `${c101.parentId}/${c102.parentId}`)
  check('一级评论无回复时 replyCount=0 且 children=[]', c102.replyCount === 0 && Array.isArray(c102.children) && c102.children.length === 0)
  check('有回复的一级评论：replyCount=2、children 正序 [103,104]', c101.replyCount === 2 && c101.children.map((c) => c.commentId).join(',') === '103,104')
  check('回复项 replyCount/children 都是 null', r103.replyCount === null && r103.children === null)
  check('回复项 parentId 指向一级评论', r103.parentId === 101)
  check('评论列表倒序（102 在 101 前）', fixture('comments-178.json').records.map((c) => c.commentId).join(',') === '102,101')
  check('话题 name 不含 #', hot.every((t) => !String(t.name).includes('#')), JSON.stringify(hot.map((t) => t.name)))
  check('详情图片 sort 从 0 起', d181.images[0].sort === 0)
  check('详情 videos 无视频时是空数组', Array.isArray(d181.videos) && d181.videos.length === 0)
  check('列表 coverUrl 无图时是 null', feed.records.find((p) => p.postId === 178).coverUrl === null)

  /* --- 纯函数（Web api/community.ts） --- */
  check('真实帖子的作者名有真值', community.authorName(feed.records[0].username, feed.records[0].uid) === 'probe114904', String(feed.records[0].username))
  check('置顶：真实 isTop=0 → false（不显示置顶标）', feed.records.every((p) => community.isPinned(p) === false))
  check('置顶：isTop=1 → true（按真值判断）', community.isPinned({ isTop: 1 }) === true)
  check('置顶：缺失/ null 不炸', community.isPinned(null) === false && community.isPinned({}) === false)
  check('版块名按真实版块表查', community.sectionName(sections, 6) === '活动专区', community.sectionName(sections, 6))
  check('真实话题名不含 #（页面自己加一个 #）', community.topicList(hot)[0].name === '猫咪日常')
  check('真实 countText 不折算（4 → "4"）', community.countText(5) === '5' && community.countText(4) === '4')
  check('详情封面 = images 按 sort 升序第 0 条', community.detailCover(d181.images) === d181.images[0].url)

  // 线上只有 1 张图（sort=0），无法用真数据压出"多图乱序"；这里用**同一个真实 item 的顺序变体**验比较器方向
  const shuffledRealImages = [{ ...d181.images[0], sort: 1 }, { ...d181.images[0], sort: 0 }, { ...d181.images[0], sort: 2 }]
  check(
    '图片按 sort 升序（真实 item 的顺序变体）',
    community.mediaListSorted(shuffledRealImages).map((m) => m.sort).join(',') === '0,1,2',
    community.mediaListSorted(shuffledRealImages).map((m) => m.sort).join(','),
  )
  check('videos 为空数组时不渲染播放器', community.mediaListSorted(d181.videos).length === 0)

  /* --- 信息流页状态机（CommunityView）吃真实 feed --- */
  const pFeed = loadCommunityView()
  resetFetch()
  envelope = { code: '0', data: feed, success: true }
  await pFeed.fetchFeed(false)
  check('真实 feed：5 条全进卡片', pFeed.posts.value.length === 5, String(pFeed.posts.value.length))
  check('真实 feed：顺序是 postId 倒序 182~178', pFeed.posts.value.map((p) => p.postId).join(',') === '182,181,180,179,178')
  check('真实 feed：total=5、pages=1 → 没有更多', pFeed.total.value === 5 && pFeed.hasMore.value === false)
  check('真实 feed：计数文案 5 篇', pFeed.countLabel.value === '5 篇', pFeed.countLabel.value)
  check('真实 feed：没有一条会显示置顶标', pFeed.posts.value.every((p) => community.isPinned(p) === false))
  check('真实 feed：卡片作者名不是用户编号兜底', pFeed.posts.value.every((p) => community.authorName(p.username, p.uid) === 'probe114904'))

  resetFetch()
  envelope = { code: '0', data: hot, success: true }
  await pFeed.loadTopics()
  check('真实话题热榜落库', pFeed.topics.value.length === 1 && pFeed.topics.value[0].topicId === 68)

  resetFetch()
  envelope = { code: '0', data: sections, success: true }
  await pFeed.loadSections()
  check('真实版块 6 条', pFeed.sections.value.length === 6, String(pFeed.sections.value.length))

  /* --- 详情页状态机（PostDetailView）吃真实详情 + 真实评论 --- */
  const pDetail = loadPostDetailView()
  resetFetch()
  envelope = { code: '0', data: d178, success: true }
  await pDetail.loadPost()
  check('真实详情：正文读得到（列表 DTO 没有它）', pDetail.post.value?.content?.includes('下午三点') === true, String(pDetail.post.value?.content).slice(0, 20))
  check('真实详情：commentCount=4（含 2 条回复）', pDetail.commentsTotal.value === 4, String(pDetail.commentsTotal.value))
  check('真实详情：liked/favorited 是布尔', pDetail.liked.value === false && pDetail.favorited.value === false)

  resetFetch()
  envelope = { code: '0', data: fixture('comments-178.json'), success: true }
  await pDetail.loadComments(false)
  check('真实评论：一级评论 2 条（倒序 102,101）', pDetail.comments.value.map((c) => c.commentId).join(',') === '102,101')
  check(
    'commentCount(4) ≠ 一级评论数(2) → 文案必须写"含回复"',
    pDetail.commentsTotal.value === 4 && pDetail.comments.value.length === 2,
  )
  check('真实一级评论 102：被识别为一级（回复目标是它自己，不是 0）', pDetail.topLevelIdOf(c102) === 102, String(pDetail.topLevelIdOf(c102)))
  check('真实一级评论 102：children=[] 与 replyCount=0 不炸', pDetail.bucketOf(102).items.length === 0 && pDetail.bucketOf(102).hasMore === false)
  check('真实一级评论 101：预览就是正序的 103,104', pDetail.bucketOf(101).items.map((r) => r.commentId).join(',') === '103,104')
  check('真实一级评论 101：replyCount=预览条数 → 不给"更多"按钮', pDetail.bucketOf(101).hasMore === false)

  resetFetch()
  envelope = { code: '0', data: fixture('replies-101.json'), success: true }
  await pDetail.loadReplies(c101)
  check('真实回复项：拉回来 2 条', pDetail.bucketOf(101).items.length === 2, String(pDetail.bucketOf(101).items.length))
  check('真实回复项：replyCount/children 为 null 不炸', pDetail.bucketOf(101).items.every((r) => r.replyCount === null && r.children === null))

  // 回复框的 v-if 条件与提交体（真数据下 parentId 必须是 101，不能是 0）
  resetFetch()
  pDetail.startReply(c101)
  check(
    '真实一级评论：回复框条件成立（topLevelIdOf(目标) === commentId）',
    Boolean(pDetail.replyTarget.value) && pDetail.topLevelIdOf(pDetail.replyTarget.value) === 101,
  )
  pDetail.replyDraft.value = '真实数据驱动的回复'
  envelope = { code: '0', data: fixture('comments-178.json'), success: true }
  await pDetail.submitComment('reply')
  const realReplyBody = JSON.parse(history[0].init.body)
  check(
    '真实一级评论：提交的 parentId=101（不是 0，0 会被后端判 B000315）',
    realReplyBody.parentId === 101 && realReplyBody.replyUid === c101.uid,
    JSON.stringify(realReplyBody),
  )
  check(
    '真实一级评论：回复体字段就是 postId/content/parentId/replyUid 四个',
    Object.keys(realReplyBody).sort().join(',') === 'content,parentId,postId,replyUid' &&
      realReplyBody.content === '真实数据驱动的回复',
    JSON.stringify(realReplyBody),
  )
}

/* ---------- F. 口径静态护栏（防止已实测判错的写法溜回来） ---------- */

/**
 * 把注释剥掉再查 —— 注释里**必须**能写"别用 `isTop === true`"这种反面教材，
 * 否则护栏会把自己的说明文档当成违规代码（这个误报真发生过一次）。
 *
 * 口径说明：只做"块注释 / HTML 注释 / 整行注释 / 行尾注释"这四种，
 * 行尾注释要求 `//` 前面是空白，`http://` 这种不会被误剥。
 */
function stripComments(text) {
  return text
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .split('\n')
    .filter((line) => !/^\s*(\/\/|\*)/.test(line))
    .map((line) => line.replace(/\s\/\/[^'"`]*$/, ''))
    .join('\n')
}

/**
 * 真实数据推翻过的写法，靠断言"源码里不再出现"来兜住。
 * 为什么用静态断言：`isTop === true` 这类写法在假数据下**照样跑绿**，
 * 只有真数据（数字 0/1、parentId 0）才暴露 —— 静态护栏是这类回归的最后一道网。
 */
function partF() {
  console.log('\n[F] 口径静态护栏（严格比较 / null 判一级 / 文案口径）')
  const WEB = path.resolve(__dirname, '..', 'MiaoWang-web')
  const PHONE = path.resolve(__dirname, '..', 'MiaoWang-phone')
  const read = (p) => stripComments(fs.readFileSync(p, 'utf8'))

  const communityTs = read(path.join(WEB, 'src/api/community.ts'))
  const communityView = read(path.join(WEB, 'src/views/CommunityView.vue'))
  const postDetailView = read(path.join(WEB, 'src/views/PostDetailView.vue'))
  const phoneCommunity = read(path.join(PHONE, 'utils/community.js'))
  const phoneFeed = read(path.join(PHONE, 'pages/community/community.vue'))
  const phoneDetail = read(path.join(PHONE, 'pages/community/detail.vue'))

  const noStrict = (label, text) =>
    check(label, !/isTop\s*===\s*(true|1)\b/.test(text) && !/Number\([^)]*isTop[^)]*\)\s*===\s*1\b/.test(text))

  noStrict('Web 社区 API 没有 isTop 严格比较', communityTs)
  noStrict('Web 信息流没有 isTop 严格比较', communityView)
  noStrict('手机 utils/community.js 没有 isTop 严格比较', phoneCommunity)
  noStrict('手机信息流页没有 isTop 严格比较', phoneFeed)

  check('Web 详情页不再用 parentId ?? commentId 判一级', !/parentId\s*\?\?\s*\w+\.commentId/.test(postDetailView))
  check('Web 详情页不再用 parentId === null 判一级', !/parentId\s*===\s*null/.test(postDetailView))
  check('Web 详情页按 parentId > 0 判"是不是回复"', /parentId\s*>\s*0/.test(postDetailView))
  check('Web 详情页图片走 mediaListSorted（按 sort 升序）', /mediaListSorted/.test(postDetailView))
  check('Web 详情页评论计数写明含回复', /含回复/.test(postDetailView))
  check('手机详情工具评论计数写明含回复', /含回复/.test(phoneCommunity))
  check('手机详情页说明一级倒序 / 回复正序', /一级评论按时间倒序/.test(phoneDetail) && /正序/.test(phoneDetail))
  check('两端都不把预览条数 3 当硬契约写进判断', !/children\.length\s*[<>]=?\s*3\b/.test(postDetailView + phoneDetail))
}

/* ---------- 跑 ---------- */

async function main() {
  await partA()
  await partB()
  await partC()
  await partD()
  await partE()
  partF()
}

main()
  .catch((e) => {
    failures++
    console.log(`  FAIL 脚本抛异常：${e && e.stack ? e.stack : e}`)
  })
  .finally(() => {
    console.log(`\n[community-api] 断言失败 ${failures} 条 ${failures ? '❌ 未通过' : '✅ 全部通过'}`)
    process.exit(failures ? 1 : 0)
  })