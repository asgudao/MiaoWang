/**
 * 社区接线的离线校验（手机端 / MiaoWang-phone）
 *
 * 手机端是 HBuilderX 工程（**没有 package.json**），Node 会把 `utils/community.js` 当 CJS，
 * 直接 import 那个 ESM 文件会报 "Unexpected token 'export'" → 所以先把源文件与页面 `<script>`
 * 复制到系统临时目录再 import（**只读项目文件、不改项目文件**）。
 *
 * 做两件事，输入全部是 `community-fixtures/` 里**线上库真实种子数据**的响应体
 * （帖子 178~182 / 评论 101~106 / 话题 68，由 `fetch-community-fixtures.js` 只用 GET 固化）：
 *
 * A) **纯函数**：`utils/community.js` 的 postCard / commentCard / replyCard / postDetailView /
 *    imageUrls / topicNames / authorLabel，喂真实 DTO，断言实测推翻过的假设都被改对
 * B) **页面状态机**：把 `pages/community/community.vue` 与 `detail.vue` 的 `<script>` 真跑一遍
 *    （api / auth / request 用桩替掉），断言真实 feed / 详情 / 评论 / 回复能正确摊平成模板字段
 *
 * 验不到（没有 HBuilderX / 真机）：**像素级渲染、样式、手势**（下拉刷新、触底）。
 * 用法：node tools/check-community-phone.mjs（退出码 = 失败断言数）
 */
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const PHONE = path.resolve(HERE, '..', 'MiaoWang-phone')
const FIXTURES = path.join(HERE, 'community-fixtures')
const TMP = fs.mkdtempSync(path.join(os.tmpdir(), 'mw-community-phone-'))

let fail = 0
function check(label, cond, detail) {
  if (cond) console.log(`  ok   ${label}`)
  else {
    fail++
    console.log(`  FAIL ${label}${detail === undefined ? '' : `  →  ${detail}`}`)
  }
}
const fixture = (name) => JSON.parse(fs.readFileSync(path.join(FIXTURES, name), 'utf8'))
const tick = () => new Promise((r) => setTimeout(r, 0))
const fileUrl = (p) => 'file:///' + p.replace(/\\/g, '/')

/* ---------------- 桩模块 ---------------- */

fs.writeFileSync(
  path.join(TMP, 'stub-api.mjs'),
  `const s = () => globalThis.__mwStub
export const PAGE_SIZE = 10
export const CURSOR_SIZE = 10
export const listSections = (...a) => s().call('listSections', a)
export const listPosts = (...a) => s().call('listPosts', a)
export const getPost = (...a) => s().call('getPost', a)
export const listComments = (...a) => s().call('listComments', a)
export const listReplies = (...a) => s().call('listReplies', a)
export const likePost = (...a) => s().call('likePost', a)
export const unlikePost = (...a) => s().call('unlikePost', a)
export const favoritePost = (...a) => s().call('favoritePost', a)
export const unfavoritePost = (...a) => s().call('unfavoritePost', a)
`,
)
fs.writeFileSync(
  path.join(TMP, 'stub-request.mjs'),
  `export class ApiError extends Error {
  constructor (code, message) {
    super(message || '请求失败')
    this.name = 'ApiError'
    this.code = String(code)
  }
  get isUnauthorized () {
    return ['UNAUTHORIZED', '-1', 'B000310', 'B000204', 'B000701'].indexOf(this.code) >= 0 ||
      /未登录|登录已过期|登录失效|登录状态已失效/.test(this.message || '')
  }
}
`,
)
fs.writeFileSync(
  path.join(TMP, 'stub-auth.mjs'),
  `export const auth = {
  isLoggedIn () { return globalThis.__mwAuth.loggedIn },
  clearSession () { globalThis.__mwAuth.cleared = true }
}
`,
)
fs.writeFileSync(path.join(TMP, 'stub-tabbar.mjs'), 'export function hideNativeTabBar () {}\n')
fs.writeFileSync(path.join(TMP, 'stub-component.mjs'), 'export default { name: "FloatingTabBar" }\n')
fs.writeFileSync(
  path.join(TMP, 'community.mjs'),
  fs.readFileSync(path.join(PHONE, 'utils/community.js'), 'utf8'),
)

/* ---------------- 全局环境桩（uni / getCurrentPages） ---------------- */

globalThis.__mwAuth = { loggedIn: true, cleared: false }
globalThis.uni = {
  stopPullDownRefresh() {},
  navigateTo(o) {
    globalThis.__mwNav = o && o.url
  },
  switchTab(o) {
    globalThis.__mwNav = o && o.url
  },
  showToast(o) {
    globalThis.__mwToast = o && o.title
  },
  getSystemInfoSync() {
    return { statusBarHeight: 24 }
  },
  previewImage() {},
}
globalThis.getCurrentPages = () => [
  { route: 'pages/community/community' },
  { route: 'pages/community/detail' },
]

/* ---------------- 加载手机端源码 ---------------- */

function loadOptions(vueFile, outName) {
  const src = fs.readFileSync(path.join(PHONE, vueFile), 'utf8')
  const m = /<script>([\s\S]*?)<\/script>/.exec(src)
  if (!m) throw new Error('没找到 <script>：' + vueFile)
  const code = m[1]
    .replace(/from '@\/components\/FloatingTabBar\.vue'/g, "from './stub-component.mjs'")
    .replace(/from '@\/utils\/tabbar\.js'/g, "from './stub-tabbar.mjs'")
    .replace(/from '@\/store\/auth\.js'/g, "from './stub-auth.mjs'")
    .replace(/from '@\/utils\/request\.js'/g, "from './stub-request.mjs'")
    .replace(/from '@\/api\/community\.js'/g, "from './stub-api.mjs'")
    .replace(/from '@\/utils\/community\.js'/g, "from './community.mjs'")
  const out = path.join(TMP, outName)
  fs.writeFileSync(out, code)
  return import(fileUrl(out)).then((mod) => mod.default)
}

/** 把 Options API 的对象变成一个可读写的实例（data + computed 取值器 + methods） */
function instantiate(options) {
  const ctx = Object.assign({}, options.data ? options.data() : {}, options.methods || {})
  const comp = options.computed || {}
  Object.keys(comp).forEach((k) => {
    Object.defineProperty(ctx, k, {
      get() {
        return comp[k].call(ctx)
      },
      configurable: true,
    })
  })
  if (options.onLoad) ctx.__onLoad = options.onLoad.bind(ctx)
  return ctx
}

/** 记录 api 调用，并按 plan 给返回值；用真实 fixture 时 plan 就是 fixture 本身 */
function makeStub(plan) {
  const calls = []
  globalThis.__mwStub = {
    calls,
    call(name, args) {
      calls.push({ name, args: args[0] })
      const h = plan[name]
      if (typeof h === 'undefined') throw new Error('桩没有为 ' + name + ' 准备返回值')
      const r = typeof h === 'function' ? h(args[0]) : h
      return r instanceof Error ? Promise.reject(r) : Promise.resolve(r)
    },
  }
  return calls
}

const community = await import(fileUrl(path.join(TMP, 'community.mjs')))

/* =============== 读真实 fixture =============== */

const feed = fixture('feed.json')
const sections = fixture('sections.json')
const commentsPage = fixture('comments-178.json')
const repliesPage = fixture('replies-101.json')
const hot = fixture('topic-hot.json')
const d178 = fixture('detail-178.json')
const d181 = fixture('detail-181.json')

const realPost = (postId) => feed.records.find((p) => p.postId === postId)
const realComment = (id) => commentsPage.records.find((c) => c.commentId === id)
const realReply = (id) => repliesPage.records.find((c) => c.commentId === id)

/* =============== A. 纯函数（真实 DTO） =============== */

console.log('[A] 手机端纯函数吃真实 JSON')

check('fixture 在（先跑 fetch-community-fixtures.js 生成）', Boolean(feed && commentsPage && d178))

// 作者名：实测 uid=7 已解析出真值
check('真实帖子作者名有真值', feed.records.every((p) => community.authorLabel(p) === 'probe114904'))
check(
  'uid 兜底仍保留（实测 uid=1 社区无内容时 username 为 null）',
  community.authorLabel({ uid: 1, username: null }) === '用户 1',
)

// 置顶：真实 isTop 是数字 0
const cards = feed.records.map(community.postCard)
check('真实 feed：5 条卡片摊平成功', cards.length === 5, String(cards.length))
check('真实 isTop=0 → 卡片不显示置顶标', cards.every((c) => c.isTop === false))
check(
  'isTop 换成 1 的真值变体 → 显示置顶标（线上没有置顶帖，只能改真值）',
  community.postCard({ ...realPost(178), isTop: 1 }).isTop === true,
)
check('真实卡片带真标题（不是"无标题帖子"）', cards.every((c) => c.title !== '无标题帖子' && c.title.length > 0))
check('列表没有正文 → 卡片不带 content 字段', cards.every((c) => !Object.prototype.hasOwnProperty.call(c, 'content')))
check('无视频 → videoText 为空且不炸', cards.every((c) => c.videoText === ''))
check('有封面 vs 无封面（181 有图，178 无图）', community.postCard(realPost(181)).hasCover === true && community.postCard(realPost(178)).hasCover === false)
check('真实话题名不含 #', community.postCard(realPost(178)).topics.join(',') === '猫咪日常')
check('话题接口原始 name 也不含 #', hot.every((t) => !String(t.name).includes('#')))
check('真实计数文案', community.postCard(realPost(178)).commentText === '评论 4')
check('真实点赞态是布尔（179/180 为 true）', community.postCard(realPost(179)).liked === true && community.postCard(realPost(178)).liked === false)

// 评论：一级 parentId=0 / children=[] / replyCount=0；回复项 null
const card101 = community.commentCard(realComment(101))
const card102 = community.commentCard(realComment(102))
check('一级评论 101：正序预览 103,104', card101.children.map((c) => c.commentId).join(',') === '103,104')
check('一级评论 101：replyCount=2', card101.replyCount === 2)
check('一级评论 102：children=[] 与 replyCount=0 不炸', card102.children.length === 0 && card102.replyCount === 0)
check('一级评论作者名有真值', card101.author === 'probe114904')
const r103 = community.replyCard(realReply(103))
check('回复项：replyCount/children 为 null 也不炸', r103.likeText === '赞 0' && r103.content.length > 0)
check('回复项：replyUsername 有真值时显示"回复 谁"', r103.replyToText === '回复 probe114904', r103.replyToText)

// 详情
const v178 = community.postDetailView(d178, { liked: null, favorited: null, likeCount: null, favoriteCount: null, viewCount: null, commentCount: null })
check('详情 178：正文读得到', String(v178.content).includes('下午三点'))
check('详情 178：无图片/无视频不炸', v178.hasImages === false && v178.hasVideos === false)
check('详情 178：commentText 写明含回复（commentCount=4，一级评论只有 2 条）', v178.commentText === '评论 4 条（含回复）', v178.commentText)
check('详情 178：不编 isTop（详情 DTO 没有它）', !Object.prototype.hasOwnProperty.call(v178, 'isTop'))
const v181 = community.postDetailView(d181, null)
check('详情 181：图片按 sort 升序取到唯一那张', v181.images.length === 1 && v181.images[0] === d181.images[0].url)
check('详情 181：无视频 → videos 空数组', v181.videos.length === 0)
check('详情：相对时间可解析（真实 ISO 带 +00:00）', typeof v178.timeText === 'string' && v178.timeText.length > 0)
check('详情：空详情不炸', community.postDetailView(null, null).title === '无标题帖子')

/* =============== B. 页面状态机（真实 fixture） =============== */

console.log('\n[B] 手机端页面状态机吃真实 JSON')

const plan = {
  listSections: sections,
  listPosts: feed,
  getPost: d178,
  listComments: commentsPage,
  listReplies: repliesPage,
  likePost: null,
  unlikePost: null,
  favoritePost: null,
  unfavoritePost: null,
}

/* --- 信息流页 --- */
const feedOptions = await loadOptions('pages/community/community.vue', 'feed-page.mjs')
let calls = makeStub(plan)
let f = instantiate(feedOptions)
f.__onLoad()
await tick()
check('真实 feed：倒序进列表', f.posts.map((p) => p.postId).join(',') === '182,181,180,179,178')
check('真实 feed：页码走到下一页且没有更多（pages=1）', f.pageNum === 2 && f.hasMore === false)
check('真实 feed：5 张卡片、作者名有真值', f.cards.length === 5 && f.cards.every((c) => c.author === 'probe114904'))
check('真实 feed：没有一条显示置顶标', f.cards.every((c) => c.isTop === false))
check('真实 feed：版块 chips = 全部 + 真实 6 个版块', f.chips.length === 7 && f.chips[6].name === '活动专区', String(f.chips.length))
check('真实 feed：带图的那条显示封面', f.cards.find((c) => c.postId === 181).hasCover === true)
check('真实 feed：到底文案用的是已加载条数', f.totalText === '一共 5 条，没有更多了', f.totalText)
check('真实 feed：请求参数是页码分页', calls.find((c) => c.name === 'listPosts').args.pageSize === 10)

/* --- 详情页 --- */
const detailOptions = await loadOptions('pages/community/detail.vue', 'detail-page.mjs')
calls = makeStub(plan)
let d = instantiate(detailOptions)
d.__onLoad({ postId: '178' })
await tick()
await tick()
check('真实详情：inter 初值来自真实 DTO（liked=false + commentCount=4）', d.inter.liked === false && d.inter.commentCount === 4)
check('真实详情：正文与作者名', d.view.content.includes('下午三点') && d.view.author === 'probe114904')
check('真实详情：无视频 → 不挂播放器', d.activeVideoUrl === '' && d.videoChips.length === 0)
check('真实详情：评论 2 条且倒序 102,101', d.rawComments.map((c) => c.commentId).join(',') === '102,101')
check('真实详情：评论卡片没有越界崩（102 无回复 / 101 两条预览）', d.comments.length === 2 && d.comments[1].children.length === 2)
check('真实详情：102 无回复 → 不给"查看全部回复"按钮', d.comments[0].showReplyToggle === false)
check('真实详情：101 replyCount=预览条数 → 不给"查看全部回复"按钮', d.comments[1].showReplyToggle === false)
check('真实详情：一级评论顺序文案写清"一级评论"', d.commentsEndText === '一级评论都在这儿了，共 2 条', d.commentsEndText)
check('真实详情：commentText 含回复、与一级评论条数口径分开', d.view.commentText === '评论 4 条（含回复）')

// 展开回复：拉到真实 2 条，null 字段不炸
d.toggleReplies(101)
await tick()
check('真实回复：展开拉回 2 条', d.replyMap[101] && d.replyMap[101].items.length === 2, String(d.replyMap[101] && d.replyMap[101].items.length))
check('真实回复：hasMore=false（replyCount 已等于拿到的条数）', d.replyMap[101].hasMore === false)
check('真实回复：卡片字段摊平（含"回复 谁"）', d.comments[1].replyItems[0].replyToText === '回复 probe114904')
check(
  '真实回复：原始 DTO 的 replyCount/children 为 null 不参与渲染也不报错',
  d.replyMap[101].items.every((r) => r.replyCount === null && r.children === null) &&
    d.comments[1].replyItems.every((r) => r.likeText === '赞 0' && r.timeText !== ''),
  JSON.stringify(d.replyMap[101].items.map((r) => [r.replyCount, r.children])),
)
d.toggleReplies(101)
await tick()
check('真实回复：再点一次是收起（不再请求）', d.replyMap[101].open === false && calls.filter((c) => c.name === 'listReplies').length === 1)

// 详情页换成带图的那篇
calls = makeStub({ ...plan, getPost: d181, listComments: { records: [], size: 10, hasMore: false, nextCursor: null } })
d = instantiate(detailOptions)
d.__onLoad({ postId: '181' })
await tick()
await tick()
check('真实详情 181：图片按 sort 升序、可点开大图', d.view.images.length === 1 && d.view.images[0] === d181.images[0].url)
check('真实详情 181：无评论 → 空态文案（不静默）', d.comments.length === 0 && d.commentsEndText === '')

/* --- 掉线：真实场景里网关拦匿名，这里验页面不会被错误页糊住 --- */
globalThis.__mwAuth.loggedIn = false
calls = makeStub({})
d = instantiate(detailOptions)
d.__onLoad({ postId: '178' })
await tick()
check('未登录：不发请求，直接软门槛', calls.length === 0 && d.needLogin === true)
globalThis.__mwAuth.loggedIn = true

console.log(
  fail
    ? `\n[community-phone] 断言失败 ${fail} 条 ❌ 未通过`
    : '\n[community-phone] 全部断言通过 ✅',
)
process.exit(fail ? 1 : 0)