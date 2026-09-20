<script setup lang="ts">
/**
 * 社区（Web）
 *
 * 数据全部来自 `miaowang-community`（经网关 7000），字段与分页信封以
 * `.dsh-tmp/community-contract.md` 的实测报告为准。这里没有一条硬编码帖子/版块。
 *
 * 三个必须记住的实现要点：
 * 1) **经网关调社区，每个接口都要登录**（连版块列表都要），所以未登录时直接给"去登录"态，
 *    不发注定被网关拦成 `{"state":-1}` 的请求。
 * 2) **两套分页信封**在这里被同一套状态机吃掉：信息流与"我的点赞/收藏"是页码分页
 *    （`pageNum/pages`），话题下的帖子是游标分页（`nextCursor` 原样回传）。
 * 3) 列表 DTO **没有正文**，卡片只有标题、封面、图片数组与计数；正文在详情页；社区**没有头像字段**，
 *    头像位置用昵称首字顶，昵称拿不到就按 uid 兜底。
 * 4) 同一个列表 DTO 里 **`liked` / `favorited` 是布尔、`isTop` 是数字 `0`/`1`**（实测），
 *    置顶标记只能从列表拿（详情 DTO 没有 `isTop`），判断统一走 `isPinned()`。
 * 5) **图片区**：列表与详情**都带 `images`**（2026-09-21「社区列表图片契约定稿」：
 *    `PostRespDTO.java:49` 的 `images` 按 `sort` 升序、`[0]` 即封面、**最多 9 张**、**无图 = `[]`**；
 *    `:57` 的 `imageCount` 是图片**总张数**，`+N = imageCount - images.length`；
 *    ⚠️ **写侧上限 9 = 读侧预览上限 9 → 该差值当前恒为 0，前端「+N」这一档当前不可达**），
 *    读图统一走 `postMediaOf()` —— 有数组按多图档位排，空/缺席才退回"单张大图"；
 *    **无图时图片区整块不渲染**（不是留个灰框）。排列档位判定在
 *    `components/community/postMediaLayout.ts`（纯函数、门禁逐档真跑）。
 * 6) **长标题**：超过 `textFold.ts` 的阈值才给「展开 / 收起」，行内切换、不跳页；
 *    真实种子帖标题都在阈值内，这个功能今天碰不到（门禁里钉住了这一点）。
 *    注意**列表 DTO 没有正文**，所以卡片上能折叠的只有标题。
 * 7) ⚠️ **2026-09-21 信息流改单列 + 卡片分区纵向重排**（用户真机反馈"第一排与第二排之间有很大空白"）。
 *    改动的因与果：
 *    - **因**：`.feed` 原来是 `repeat(auto-fill, minmax(236px,1fr))` 的**多列网格** + `align-items: start`
 *      → 同一行里高矮不齐时，矮卡下方**必须**留出到行高为止的空白（网格行高 = 该行最高那张卡）。
 *      这是网格布局的固有行为，不是某条样式的笔误，所以只能**改列数**，不能"调一下间距"。
 *    - **果**：`.feed` 改成**单列**（`display: flex` + `flex-direction: column`），
 *      **一屏一行一帖**；内容列自身给 `max-width` 并居中（单列 ≠ 拉满全宽，太长读不动）。
 *    - 卡片内部按用户要求的**固定纵向顺序**：
 *      **用户信息 → 标题 → 内容摘要 → 内容展开 → 图片 → 评论条数（点进详情）→ 计数/操作条**。
 *    - **评论内容不上卡片**（2026-09-21 用户变更："评论默认不显示，点进帖子才能看，
 *      但是外面显示有多少条评论"）：卡片只显示**条数**，且**字段缺席就整块不渲染**（不编数字）。
 *      评论的"热评/最高赞"口径**彻底作废**：后端评论接口**只有一种排序**
 *      （`comment_id` 倒序 = 最新在前，见 `CommentServiceImpl.java:145-163`），没有按热度排序的能力。
 *    - 卡片外可做**点赞 / 收藏**（真调接口、乐观更新、失败回滚、未登录跳登录）。
 *    - 布局：**热榜侧栏在左**（sticky 跟随下滑），信息流在右、**单列**。
 *    - 图标一律取自项目原本就在用的 `@phosphor-icons/vue`（无 emoji、无手绘 SVG、无新增依赖）。
 */
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import {
  PhArrowClockwise,
  PhArrowLeft,
  PhArrowRight,
  PhBookmarkSimple,
  PhChatCircle,
  PhCircleNotch,
  PhEye,
  PhFire,
  PhHash,
  PhHeart,
  PhPaperPlaneRight,
  PhPencilSimple,
  PhSignIn,
  PhSquaresFour,
  PhVideoCamera,
} from '@phosphor-icons/vue'
import { ApiError } from '@/api/request'
import PostMediaGrid from '@/components/community/PostMediaGrid.vue'
import { postMediaOf } from '@/components/community/postMediaLayout'
import { postExcerptOf, isExcerptTruncated } from '@/components/community/postExcerpt'
import {
  POST_PAGE_SIZE,
  authorName,
  commentCountOf,
  communityErrorMessage,
  countText,
  createPost,
  favoritePost,
  getPost,
  initialOf,
  isPinned,
  likePost,
  listHotTopics,
  listMyFavoritePosts,
  listMyLikedPosts,
  listPosts,
  listSections,
  listTopicPosts,
  mediaList,
  relativeTime,
  sectionName,
  titleText,
  topicList,
  unfavoritePost,
  unlikePost,
  type CommunitySection,
  type CursorPage,
  type Paged,
  type PostSummary,
  type PostTopic,
} from '@/api/community'
import { useAuthStore } from '@/stores/auth'

/**
 * 列表有四种来源：`flow`（全站流或某个版块）、`like`、`favorite` 都是**页码分页**
 * （`/post`、`/my/like`、`/my/favorite`）；`topic` 是**游标分页**（`/topic/{id}/post`），
 * 契约里话题下帖子**没有 total**，只有 `hasMore` + `nextCursor`。
 */
type FeedMode = 'flow' | 'like' | 'favorite' | 'topic'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()
const loggedIn = computed(() => authStore.isLoggedIn)

/* ---------- 版块 ---------- */

const sections = ref<CommunitySection[]>([])
const sectionsLoading = ref(false)
const sectionsError = ref('')

/* ---------- 话题热榜 ---------- */

const topics = ref<PostTopic[]>([])
const topicsLoading = ref(false)
const topicsError = ref('')

/* ---------- 信息流 ---------- */

const posts = ref<PostSummary[]>([])
// 初始就是 true：首次绘制直接给骨架，不先闪一下"还没有帖子"的空态
const feedLoading = ref(true)
const moreLoading = ref(false)
const feedError = ref('')

/** 任何一次请求被判成"未登录"就翻到登录态，不再显示半截页面 */
const unauthorized = ref(false)

const mode = ref<FeedMode>('flow')
/** `null` = 全部（**不传 `sectionId` 才是全站流**，契约里没有"推荐"版块） */
const activeSectionId = ref<number | null>(null)
const activeTopic = ref<PostTopic | null>(null)

// 页码分页的进度
const pageNum = ref(1)
const pages = ref(0)
const total = ref(0)
// 游标分页的进度（`nextCursor` 只做原样搬运，不做加减）
const nextCursor = ref<number | null>(null)
const hasMore = ref(false)

const isPagedMode = computed(() => mode.value !== 'topic')
const needLogin = computed(() => !loggedIn.value || unauthorized.value)

/* ---------- 发帖（后端已实测通过，本端还没有真调过） ---------- */

const composerOpen = ref(false)
const formSectionId = ref<number | null>(null)
const formTitle = ref('')
const formContent = ref('')
const postSubmitting = ref(false)
const postError = ref('')
const postNotice = ref('')

/* ---------- 文案 ---------- */

const feedTitle = computed(() => {
  if (mode.value === 'like') return '我赞过的帖子'
  if (mode.value === 'favorite') return '我收藏的帖子'
  if (mode.value === 'topic') return `#${activeTopic.value?.name ?? ''}`
  return activeSectionId.value === null
    ? '全部帖子'
    : sectionName(sections.value, activeSectionId.value)
})

/** 页码分页有真 total，游标分页没有，所以只说"已加载多少" */
const countLabel = computed(() =>
  isPagedMode.value ? `${countText(total.value)} 篇` : `已加载 ${posts.value.length} 篇`,
)

const emptyState = computed(() => {
  if (mode.value === 'like') {
    return { title: '还没有赞过的帖子', desc: '在帖子详情页点个赞，赞过的帖子会收在这里。' }
  }
  if (mode.value === 'favorite') {
    return {
      title: '还没有收藏的帖子',
      desc: '看到想留着的帖子就收藏，之后在这里找它。',
    }
  }
  if (mode.value === 'topic') {
    return {
      title: `#${activeTopic.value?.name ?? ''} 下还没有帖子`,
      desc: '话题下的帖子按发布时间倒序排，有第一篇就会出现在这里。',
    }
  }
  if (activeSectionId.value !== null) {
    return {
      title: `「${sectionName(sections.value, activeSectionId.value)}」还没有帖子`,
      desc: '这个版块现在是空的，第一帖的机会是你的。',
    }
  }
  return { title: '社区里还没有帖子', desc: '还没有人发过帖子，第一帖的机会是你的。' }
})

/* ---------- 卡片上的图片与长标题 ---------- */

/**
 * 一条帖子的图片数组。
 *
 * 图片数组来自列表 `images`（**后端已定稿**：按 `sort` 升序、最多 9 张、`[0]` 即封面、无图 = `[]`，
 * 见 `PostRespDTO.java:49`）；`images` 为空或缺席时 `postMediaOf` 才退回单张 `coverUrl` 折成"1 张"，
 * 于是走"单张大图"档位。2/3/4/5~9/>9 档位由同一个 `mediaGridLayout` 逐档判定，**这里一个字都不参与**。
 * 两种形状都没有 → `[]` → 图片区**整块不渲染**（不是显示成空白框）。
 */
const mediaOf = (p: PostSummary) => postMediaOf(p)

/** 单图档位：这一张直接进帖子（多图档位才走灯箱，见 `PostMediaGrid` 的说明） */
const singleMedia = (p: PostSummary) => postMediaOf(p).length === 1

/** ⚠️ **信息流卡片不再折叠标题**（2026-09-21 用户第 3 条真机反馈："标题必须完整显示"）。
 *  原来这里有一套 `expandedIds` / `isExpanded` / `toggleFold` 的标题折叠状态，**已整体删除**：
 *  标题给足行数、按数据原样换行（`white-space: pre-line`），绝不截断。
 *  折叠的对象改成 **③ 内容摘要**（见下面的 `hasExcerpt`）。
 *  `textFold.ts` 的阈值判定仍被**用户主页列表**（`UserPostList.vue`）使用，所以那个模块保留；
 *  本文件已不再 import 它 —— 门禁按渲染结果钉住"信息流里没有标题折叠/截断"这件事。 */

/* ---------- 卡片分区 ③ 内容摘要（字段未定稿，防御式；**折叠的对象是它，不是标题**） ---------- */

/**
 * 摘要区。**判据与取值全在 `postExcerpt.ts`**，这里只负责渲染。
 *
 * ⚠️ 三条红线（都在那个纯模块里被门禁真跑钉住）：
 * 1) 字段名**不写死** —— 按优先名清单逐个找（`contentExcerpt` / `excerpt` / `contentSummary` …）；
 * 2) 一个都没命中 → `text === ''` → **摘要区整块不渲染**（不是渲染一个空框，
 *    也**不是**拿标题 / 正文 / 计数冒充摘要）；
 * 3) 「有没有摘要」只有一个入口 `postExcerptOf(...).text !== ''`，页面里不许直接读某个字段名。
 */
const excerptOf = (p: PostSummary) => postExcerptOf(p)
const hasExcerpt = (p: PostSummary) => excerptOf(p).text !== ''
/** 后端有没有说"这 200 字摘要本身就是截断的"（定稿字段 `contentTruncated`） */
const excerptIsTruncated = (p: PostSummary) => isExcerptTruncated(p)

/* ---------- 摘要「超过 5 行」的判定：**真实测量**，不是字符数 ---------- */

/**
 * ⚠️ 用户要求"摘要最多 5 行，超过 5 行的部分必须展开才能看"。
 *
 * **判据是 DOM 真溢出**：`scrollHeight - clientHeight > 1`
 * （那段摘要挂着 `-webkit-line-clamp: 5` + `overflow: hidden`，溢出了才有差值）。
 *
 * **为什么不用字符数**：5 行能放多少字取决于字号、容器宽度、中英文混排。
 * 之前正是在"按字数估行数"的阈值上翻过车 —— 不同卡片宽度下行数根本不同，
 * 估出来的阈值必然在某一档上错（要么该展开没展开，要么留一个点了没用的按钮）。
 *
 * 测量只在浏览器里做（SSR 没有 DOM，`excerptOverflow` 保持全 `false`，
 * 此时"要不要展开"由后端的 `contentTruncated` 决定，逻辑仍然正确）。
 */
const excerptOverflow = reactive<Record<number, boolean>>({})
const excerptEls = new Map<number, HTMLElement>()

/** 摘要元素登记/注销（模板 `:ref`；`null` 表示元素被卸载） */
function bindExcerpt(postId: number, el: unknown) {
  if (el instanceof HTMLElement) excerptEls.set(postId, el)
  else excerptEls.delete(postId)
}

/** 量一遍所有在册摘要：真溢出了才标 `true` */
function measureExcerpts() {
  for (const [id, el] of excerptEls) {
    // +1 的容差：scrollHeight / clientHeight 都是取整值，亚像素会差 1
    excerptOverflow[id] = el.scrollHeight - el.clientHeight > 1
  }
}

const excerptOverflowed = (p: PostSummary) => Boolean(excerptOverflow[p.postId])

/**
 * 「内容展开」的出现条件（**唯一入口**）：
 * **摘要真溢出了 5 行** 或 **后端说这 200 字摘要本身是截断的** —— 任一成立就出现。
 *
 * 两个条件都不是"猜"：前者来自真实 DOM 测量，后者来自后端字段。
 * 短文（既没溢出、`contentTruncated` 又是 `false`）**不给**展开按钮 ——
 * 点了没东西可看，那是个假入口。
 */
const canExpandBody = (p: PostSummary) =>
  hasExcerpt(p) && (excerptOverflowed(p) || excerptIsTruncated(p))

/* ---------- 卡片分区 ④ 内容展开（懒加载详情，countView=false） ---------- */

/**
 * 展开出来的全文，按 `postId` 存。
 *
 * `loading` / `error` 与 `text` 分开存：**拉取失败时必须保住摘要 + 如实提示**，
 * 所以"失败"这件事本身也要是一个状态，不能拿"没有 text"糊过去（那看起来就像"这帖没正文"）。
 */
interface FullText {
  text: string
  loading: boolean
  error: string
}
const fullTexts = reactive<Record<number, FullText>>({})
/** 哪些卡当前是"展开态"。与"有没有拉到全文"分开：收起再展开不该重新请求 */
const expandedBodies = ref<number[]>([])
const isBodyExpanded = (postId: number) => expandedBodies.value.includes(postId)
const fullTextOf = (postId: number): FullText =>
  fullTexts[postId] ?? { text: '', loading: false, error: '' }

/**
 * 点「内容展开」：**懒加载详情接口拿全文**。
 *
 * ⚠️ `countView: false` 是硬要求 —— 展开只是把这张卡的正文补全，**不是一次浏览**，
 * 不能让"点展开就涨浏览量"（后端 `CommunityPostController.java:68-77` 已支持）。
 * 已经拉到过就不再请求（客户端缓存），失败时**保留摘要并如实提示**，不假装展开成功。
 */
async function toggleBody(p: PostSummary) {
  const cached = fullTexts[p.postId]
  if (cached?.text) {
    expandedBodies.value = isBodyExpanded(p.postId)
      ? expandedBodies.value.filter((id) => id !== p.postId)
      : [...expandedBodies.value, p.postId]
    return
  }
  if (cached?.loading) return
  fullTexts[p.postId] = { text: '', loading: true, error: '' }
  try {
    // ⚠️ 必须带 countView=false：预取正文不写 view_count
    const detail = await getPost(p.postId, { countView: false })
    const text = (detail?.content ?? '').trim()
    fullTexts[p.postId] = {
      text,
      loading: false,
      error: text ? '' : '这帖的详情里没有正文，只有标题。',
    }
    if (text) expandedBodies.value = [...expandedBodies.value, p.postId]
  } catch (e) {
    // 失败：保住摘要 + 如实说清原因，**不进展开态**
    fullTexts[p.postId] = { text: '', loading: false, error: `正文没能读到：${noteError(e)}` }
  }
}

/* ---------- 卡片外就能做的三个真操作（点赞 / 收藏 / 评论） ---------- */

/**
 * ⚠️ **初始状态必须是"未激活"，不许瞎猜**。
 *
 * 后端正在给列表加 `liked` / `favorited`，但**当前 fixture 快照里没有**（字段缺席 = `undefined`）。
 * 所以这里先 `typeof === 'boolean'` 判"字段在不在"：
 * - **字段在** → 用后端的真值（布尔或数字 0/1 都按真值判，见 `api/community.ts` 的类型不对称提醒）；
 * - **字段不在** → **默认 `false`（未激活）**，绝不猜"已点赞" ——
 *   宁可少显示一个已赞状态，也不能出现"显示已赞、其实没赞"的假状态。
 *   用户点击之后一律**以接口响应/本地乐观结果为准**更新。
 */
function flagOf(value: unknown): boolean | null {
  if (typeof value === 'boolean') return value
  if (typeof value === 'number') return value !== 0
  return null
}

interface CardAction {
  liked: boolean
  favorited: boolean
  busy: '' | 'like' | 'favorite'
  error: string
  /** 这条卡片的 `liked`/`favorited` 后端到底给没给（报告用，界面不显示） */
  hadLikedField: boolean
  hadFavoritedField: boolean
  /** 点赞 / 收藏的本地计数（后端不给新计数，只能按"我这一下"加减；初始取列表的真值） */
  likeCount: number
  favoriteCount: number
  // ---- 卡片内发评论 ----
  draft: string
  sending: boolean
  sendError: string
}

const actionsOf = reactive<Record<number, CardAction>>({})
const blankAction = (p: PostSummary): CardAction => {
  const liked = flagOf(p.liked)
  const favorited = flagOf(p.favorited)
  return {
    liked: liked ?? false,
    favorited: favorited ?? false,
    busy: '',
    error: '',
    hadLikedField: liked !== null,
    hadFavoritedField: favorited !== null,
    likeCount: Number(p.likeCount ?? 0),
    favoriteCount: Number(p.favoriteCount ?? 0),
    draft: '',
    sending: false,
    sendError: '',
  }
}
const actionOf = (p: PostSummary): CardAction => actionsOf[p.postId] ?? blankAction(p)

/** 后端已给的初始状态是谁（报告与门禁要能区分"后端给的"与"我们猜的"） */
const likedStateKnown = (p: PostSummary) => actionOf(p).hadLikedField
const favoritedStateKnown = (p: PostSummary) => actionOf(p).hadFavoritedField

/** ⚠️ 未登录的点按行为：**跳登录页**（真行为），不许静默失败、不许假装成功 */
function requireLogin(): boolean {
  if (authStore.isLoggedIn) return true
  // 跳登录页（真行为）：不静默失败、不假装成功。登录后回社区首页（不记深链，避免跳到一个空卡片页）
  void router.push({ name: 'login' })
  return false
}

/**
 * 点赞 / 取消点赞：**乐观更新 + 失败回滚**。
 *
 * ⚠️ 初始状态未知（后端没给 `liked`）时，第一次点击按"**设为已赞**"处理（`likePost`），
 * **不**根据本地 `false` 去猜"用户想取消" —— 那会把别人的赞取消掉。
 * 后端两条接口都在（`POST /like/{postId}` / `DELETE /like/{postId}`），取消不是假交互。
 */
async function toggleLike(p: PostSummary) {
  if (!requireLogin()) return
  const st = actionOf(p)
  if (st.busy) return
  const before = { liked: st.liked, count: st.likeCount }
  const next = !st.liked
  // 乐观：立刻改界面（这两个调用后端幂等，重试安全）
  // ⚠️ 用 `{...st, ...}` 展开**基线快照**而不是 `actionsOf[p.postId]`：
  //    `reactive` 的索引返回类型是可选的（`CardAction | undefined`），展开它会把必填字段推成可选，类型就崩了。
  const optimistic: CardAction = {
    ...st,
    liked: next,
    likeCount: Math.max(0, st.likeCount + (next ? 1 : -1)),
    busy: 'like',
    error: '',
  }
  actionsOf[p.postId] = optimistic
  try {
    if (next) await likePost(p.postId)
    else await unlikePost(p.postId)
    actionsOf[p.postId] = { ...optimistic, busy: '' }
  } catch (e) {
    // 回滚：数字与状态都改回去，并如实提示
    actionsOf[p.postId] = {
      ...st,
      busy: '',
      error: `点赞没能提交：${noteError(e)}`,
    }
  }
}

/** 收藏 / 取消收藏：同样乐观 + 回滚（`POST|DELETE /favorite/{postId}`，两条都在） */
async function toggleFavorite(p: PostSummary) {
  if (!requireLogin()) return
  const st = actionOf(p)
  if (st.busy) return
  const next = !st.favorited
  const optimistic: CardAction = {
    ...st,
    favorited: next,
    favoriteCount: Math.max(0, st.favoriteCount + (next ? 1 : -1)),
    busy: 'favorite',
    error: '',
  }
  actionsOf[p.postId] = optimistic
  try {
    if (next) await favoritePost(p.postId)
    else await unfavoritePost(p.postId)
    actionsOf[p.postId] = { ...optimistic, busy: '' }
  } catch (e) {
    actionsOf[p.postId] = {
      ...st,
      busy: '',
      error: `收藏没能提交：${noteError(e)}`,
    }
  }
}

/**
 * 卡片内发评论：**真调发评论接口**，成功后把新评论插进该卡的列表并同步评论数。
 *
 * ⚠️ 用接口返回的评论对象（`createComment` 返回 `CommentRespDTO`），**不自己编造字段**；
 * 返回对象不完整时只补 `postId` / `parentId` 这两个已知事实，其余一律以接口为准。
 */
/* ---------- 读数据 ---------- */

/** 未登录是"要登录"，其他错误才当错误显示，两者别混成一个红框 */
function noteError(e: unknown): string {
  if (e instanceof ApiError && e.isUnauthorized) {
    unauthorized.value = true
    return '登录状态失效了，请重新登录'
  }
  return communityErrorMessage(e)
}

/**
 * 切换筛选 / 重新读信息流时清掉**按帖缓存**：帖子列表整体换掉之后，
 * 旧的"展开正文"挂在新列表上就是串数据（和 `PostMediaGrid` 换数据要清收图记录同一个道理）。
 */
function resetCardCaches() {
  for (const key of Object.keys(fullTexts)) delete fullTexts[Number(key)]
  for (const key of Object.keys(actionsOf)) delete actionsOf[Number(key)]
  expandedBodies.value = []
}

/**
 * 列表渲染完（或换了一批帖子）之后**量一遍摘要**：只有真溢出了 5 行才出现「内容展开」。
 * `flush: 'post'` 保证在 DOM 更新之后再量；`nextTick` 是给图片/字体回流留一帧。
 */
watch(
  [posts, () => feedLoading.value],
  async () => {
    if (typeof window === 'undefined') return
    await nextTick()
    measureExcerpts()
  },
  { flush: 'post' },
)

async function loadSections() {
  sectionsLoading.value = true
  sectionsError.value = ''
  try {
    const list = await listSections()
    sections.value = Array.isArray(list) ? list : []
  } catch (e) {
    sectionsError.value = noteError(e)
  } finally {
    sectionsLoading.value = false
  }
}

async function loadTopics() {
  topicsLoading.value = true
  topicsError.value = ''
  try {
    const list = await listHotTopics(6)
    topics.value = Array.isArray(list) ? list : []
  } catch (e) {
    topicsError.value = noteError(e)
  } finally {
    topicsLoading.value = false
  }
}

/** 页码分页那一支：按当前来源选接口 */
function loadPagedPage(page: number) {
  if (mode.value === 'like') return listMyLikedPosts({ pageNum: page, pageSize: POST_PAGE_SIZE })
  if (mode.value === 'favorite') {
    return listMyFavoritePosts({ pageNum: page, pageSize: POST_PAGE_SIZE })
  }
  return listPosts({
    sectionId: activeSectionId.value,
    pageNum: page,
    pageSize: POST_PAGE_SIZE,
  })
}

/**
 * 页码分页落库。
 * `hasMore` 用 `pageNum < pages` 推，**不拿 `records.length` 猜**（最后一页通常不满）；
 * 同时要求本页真的拿到了记录，避免后端给了一个"空页但 pages 更大"的响应后无限加载。
 */
function applyPaged(res: Paged<PostSummary>, append: boolean) {
  const records = Array.isArray(res?.records) ? res.records : []
  posts.value = append ? [...posts.value, ...records] : records
  pageNum.value = Number(res?.pageNum ?? (append ? pageNum.value + 1 : 1))
  pages.value = Number(res?.pages ?? 0)
  total.value = Number(res?.total ?? records.length)
  nextCursor.value = null
  hasMore.value = records.length > 0 && pageNum.value < pages.value
}

/**
 * 游标分页落库。
 * `nextCursor` **原样回传**给下一次的 `lastId`（报告明确要求不要自己加减）；
 * `hasMore` 与 `nextCursor` 同时成立才算还有下一页，否则"有更多但没有游标"会让加载更多原地打转。
 */
function applyCursor(res: CursorPage<PostSummary>, append: boolean) {
  const records = Array.isArray(res?.records) ? res.records : []
  posts.value = append ? [...posts.value, ...records] : records
  pageNum.value = 1
  pages.value = 0
  total.value = posts.value.length
  nextCursor.value = res?.nextCursor ?? null
  hasMore.value = Boolean(res?.hasMore) && nextCursor.value !== null
}

async function fetchFeed(append: boolean) {
  if (append) moreLoading.value = true
  else {
    feedLoading.value = true
    // 重新读第一页 = 整份列表被换掉：按帖缓存的"展开正文"必须一起作废，否则旧正文会挂到新列表上
    resetCardCaches()
  }
  feedError.value = ''
  try {
    if (isPagedMode.value) {
      applyPaged(await loadPagedPage(append ? pageNum.value + 1 : 1), append)
    } else {
      applyCursor(
        await listTopicPosts({
          topicId: activeTopic.value?.topicId ?? 0,
          lastId: append ? nextCursor.value : undefined,
          size: POST_PAGE_SIZE,
        }),
        append,
      )
    }
  } catch (e) {
    feedError.value = noteError(e)
    if (append) hasMore.value = false
  } finally {
    feedLoading.value = false
    moreLoading.value = false
  }
}

async function loadMore() {
  if (feedLoading.value || moreLoading.value || !hasMore.value) return
  await fetchFeed(true)
}

/* ---------- 切换筛选 ---------- */

function selectSection(sectionId: number | null) {
  if (mode.value === 'flow' && activeSectionId.value === sectionId) return
  mode.value = 'flow'
  activeSectionId.value = sectionId
  activeTopic.value = null
  void fetchFeed(false)
}

function selectMode(next: 'like' | 'favorite') {
  if (mode.value === next) return
  mode.value = next
  activeTopic.value = null
  void fetchFeed(false)
}

function selectTopic(topic: PostTopic) {
  if (mode.value === 'topic' && activeTopic.value?.topicId === topic.topicId) return
  mode.value = 'topic'
  activeTopic.value = topic
  void fetchFeed(false)
}

function resetToFlow() {
  mode.value = 'flow'
  activeTopic.value = null
  void fetchFeed(false)
}

/* ---------- 发帖（UI 与前端逻辑已实现，但本轮**没有真调过**） ---------- */

function openComposer() {
  composerOpen.value = true
  postError.value = ''
  postNotice.value = ''
  if (formSectionId.value === null) {
    formSectionId.value = activeSectionId.value ?? sections.value[0]?.sectionId ?? null
  }
}

function closeComposer() {
  composerOpen.value = false
}

function onFormSection(e: Event) {
  const raw = (e.target as HTMLSelectElement).value
  formSectionId.value = raw ? Number(raw) : null
}

/**
 * 发帖。
 *
 * 契约要点（实测）：`sectionId` 必填；标题 / 正文 / 图片 / 视频不能同时为空；
 * 正文里的 `#话题` 会被后端解析成话题标签。
 * 目前只发纯文字帖：图片与视频要先走 `/community/file/image`、`/community/file/video` 上传拿
 * `url` + `objectKey`，那两个上传接口没有接入本端（它们会往 MinIO 写对象），所以 composer 里没有选图入口。
 */
async function submitPost() {
  if (postSubmitting.value) return
  const sectionId = formSectionId.value
  const title = formTitle.value.trim()
  const content = formContent.value.trim()
  if (sectionId === null) {
    postError.value = '请先选择发到哪个版块'
    return
  }
  if (!title && !content) {
    postError.value = '标题或正文至少写一个'
    return
  }
  postSubmitting.value = true
  postError.value = ''
  postNotice.value = ''
  try {
    const created = await createPost({
      sectionId,
      title: title || undefined,
      content: content || undefined,
    })
    const where = sectionName(sections.value, sectionId)
    postNotice.value = created?.postId
      ? `已发布到「${where}」，帖子编号 ${created.postId}，下面已经切到这个版块`
      : `已发布到「${where}」，下面已经切到这个版块`
    formTitle.value = ''
    formContent.value = ''
    composerOpen.value = false
    mode.value = 'flow'
    activeSectionId.value = sectionId
    activeTopic.value = null
    await fetchFeed(false)
  } catch (e) {
    postError.value = noteError(e)
  } finally {
    postSubmitting.value = false
  }
}

onMounted(async () => {
  // 未登录就别发请求了：社区每个接口经网关都要 token + username，匿名必然被拦
  if (!loggedIn.value) return
  // 话题热榜现在是**真链接**（`?topic=<id>`）：先把话题表拉回来，才能把 query 里的 id 认出来
  await Promise.all([loadSections(), loadTopics(), fetchFeed(false)])
  applyTopicQuery()
})

/**
 * 把 `?topic=<id>` 落成"当前正在看这个话题"。
 *
 * 热榜在侧栏（左），点一下就是一次真实的 RouterLink 跳转 —— 落到这里被认出来，
 * 于是页面切到该话题的帖子流。**认不出 id 就什么都不做**（不猜、不乱切列表）。
 */
function applyTopicQuery() {
  const raw = route.query.topic
  const id = Number(Array.isArray(raw) ? raw[0] : raw)
  if (!Number.isFinite(id) || id <= 0) return
  const hit = topics.value.find((t) => t.topicId === id)
  if (hit) selectTopic(hit)
}
</script>

<template>
  <div class="mw-container mw-page">
    <header class="head">
      <div>
        <h1 class="mw-h1">社区</h1>
        <p class="mw-caption">
          版块、帖子、话题与计数都来自社区服务。列表卡片只有标题与封面，正文在帖子详情页里。
        </p>
      </div>
      <button
        v-if="!needLogin"
        class="mw-btn mw-btn--primary"
        type="button"
        @click="openComposer"
      >
        <PhPencilSimple :size="16" aria-hidden="true" />
        发帖
      </button>
    </header>

    <!-- 未登录 / 会话失效：社区经网关一律要登录，这里给明确的下一步，不发注定失败的请求 -->
    <section v-if="needLogin" class="mw-state gate">
      <PhSignIn :size="26" aria-hidden="true" />
      <p class="mw-state__title">登录后才能进社区</p>
      <p class="gate__desc">
        社区的每个接口经网关都要求登录，匿名请求会被网关拦下来。登录之后这里会显示真实版块、帖子与话题。
      </p>
      <RouterLink
        class="mw-btn mw-btn--primary"
        :to="{ name: 'login', query: { redirect: '/community' } }"
      >
        去登录
      </RouterLink>
    </section>

        <section v-if="composerOpen" class="composer mw-card">
          <div class="composer__head">
            <h2 class="mw-h2">发帖</h2>
            <p class="mw-caption">标题与正文至少写一个。图片与视频需要先上传拿地址，本轮没接上传入口。</p>
          </div>

          <div class="composer__fields">
            <label class="mw-field">
              <span>版块</span>
              <select class="mw-input" :value="formSectionId ?? ''" @change="onFormSection">
                <option value="" disabled>选择版块</option>
                <option v-for="s in sections" :key="s.sectionId" :value="s.sectionId">
                  {{ s.name }}
                </option>
              </select>
            </label>

            <label class="mw-field">
              <span>标题</span>
              <input v-model="formTitle" class="mw-input" type="text" placeholder="一句话说清这帖讲什么" />
            </label>

            <label class="mw-field">
              <span>正文</span>
              <textarea
                v-model="formContent"
                class="mw-input composer__area"
                rows="5"
                placeholder="把想说的写在这里。带上 #话题 会在发布后生成话题标签。"
              />
            </label>
          </div>

          <div class="composer__actions">
            <button
              class="mw-btn mw-btn--primary"
              type="button"
              :disabled="postSubmitting"
              @click="submitPost"
            >
              <PhPaperPlaneRight :size="16" aria-hidden="true" />
              {{ postSubmitting ? '发布中' : '发布' }}
            </button>
            <button class="mw-btn mw-btn--ghost" type="button" @click="closeComposer">
              先不发
            </button>
          </div>
        </section>

        <p v-if="postError" class="mw-alert mw-alert--danger feedback">{{ postError }}</p>
        <p v-else-if="postNotice" class="mw-alert mw-alert--info feedback">{{ postNotice }}</p>

        <!-- 版块 chips：真实的 6 个版块 + "全部"（全部 = 不传 sectionId 的全站流） -->
        <div class="chips" role="group" aria-label="按版块筛选">
          <button
            class="chip"
            :class="{ 'chip--on': mode === 'flow' && activeSectionId === null }"
            type="button"
            @click="selectSection(null)"
          >
            全部
          </button>
          <!-- 版块标签：**加图标**（2026-09-21 用户要求"点赞/收藏/评论、导航等处加图标，别太单调"）。
               图标一律来自项目原本就在用的 `@phosphor-icons/vue`，**没有** emoji、
               **没有**手绘内联 SVG、**没有**引入 element-plus（报告里有依赖核实）。 -->
          <button
            v-for="s in sections"
            :key="s.sectionId"
            class="chip"
            :class="{ 'chip--on': mode === 'flow' && activeSectionId === s.sectionId }"
            type="button"
            :title="s.description ?? ''"
            @click="selectSection(s.sectionId)"
          >
            <PhSquaresFour :size="12" aria-hidden="true" />
            {{ s.name }}
          </button>
          <span v-if="sectionsLoading" class="chip chip--ghost">
            <PhCircleNotch :size="12" aria-hidden="true" />
            版块读取中
          </span>
        </div>

        <p v-if="sectionsError" class="mw-alert mw-alert--danger feedback">
          <span>版块没能读到：{{ sectionsError }}</span>
          <button class="link" type="button" @click="loadSections">重试</button>
        </p>

        <!-- 从"我的点赞/收藏"或话题进来时，给一条回到全站流的路 -->
        <div v-if="mode !== 'flow'" class="feedbar">
          <div>
            <p class="feedbar__title">{{ feedTitle }}</p>
            <p class="feedbar__sub">
              {{
                mode === 'topic'
                  ? '话题下的帖子按发布时间倒序，翻页用的是游标'
                  : '这两份列表只有你自己能看，按点赞与收藏的时间倒序'
              }}
            </p>
          </div>
          <button class="link" type="button" @click="resetToFlow">
            <PhArrowLeft :size="12" aria-hidden="true" />
            回到全部帖子
          </button>
        </div>
        <p v-else-if="!feedLoading" class="feedbar__sub feedbar__sub--plain">{{ countLabel }}</p>

    <!-- ⚠️ **`v-if="!needLogin"`（原来是 `v-else`）**：为了让右栏组合与第一条帖子**顶对齐**，
         版块 chips / 列表说明 / 发帖面板已经从 `.main` 里挪到了 `.layout` **之上**（2026-09-21）。
         这一挪会打断原来的 `v-if="needLogin"` → `v-else` 链（中间的 `v-else-if` 会把它接走），
         所以这里必须写成独立的 `v-if="!needLogin"`，否则整块布局在默认状态下不渲染（页面直接空白）。 -->
    <div v-if="!needLogin" class="layout">
      <div class="main">
        <!-- 发帖面板：契约已按实测口径实现，本端还没有真调过 -->

        <div v-if="feedLoading" class="feed">
          <span v-for="i in 6" :key="i" class="mw-skeleton card-skeleton" />
        </div>

        <div v-else-if="feedError" class="mw-state">
          <PhArrowClockwise :size="26" aria-hidden="true" />
          <p class="mw-state__title">帖子没能读到</p>
          <p>{{ feedError }}</p>
          <button class="mw-btn mw-btn--ghost" type="button" @click="fetchFeed(false)">
            重试
          </button>
        </div>

        <div v-else-if="!posts.length" class="mw-state">
          <PhChatCircle :size="26" aria-hidden="true" />
          <p class="mw-state__title">{{ emptyState.title }}</p>
          <p>{{ emptyState.desc }}</p>
          <button
            v-if="mode !== 'flow'"
            class="mw-btn mw-btn--ghost"
            type="button"
            @click="resetToFlow"
          >
            去看看全部帖子
          </button>
          <button v-else class="mw-btn mw-btn--primary" type="button" @click="openComposer">
            发第一帖
          </button>
        </div>

        <template v-else>
          <!-- 信息流 = **单列**：一屏一行一帖。
               `.feed` 是 flexcolumn（不是多列网格），所以同一行里永远只有一张卡，
               "第一排和第二排之间的大块空白"从**结构上**不可能再出现（见 `fetchFeed` 上方的说明）。
               内容列自身限宽并居中：单列 ≠ 拉满全宽，一行太长读不动 -->
          <ul class="feed" data-feed="single-column">
            <li v-for="p in posts" :key="p.postId" class="feed__row">
              <!-- 卡片不再整体是一个链接：作者名也要能点进主页，
                   而 `<a>` 里套 `<a>` 是非法 HTML（读屏与键盘都会错乱），
                   所以拆成三个链接：封面、标题各进帖子，作者名进主页。
                   ⚠️ 卡片内部的**纵向顺序是契约**（用户指定的 7 段）：
                   ① 用户信息 → ② 标题 → ③ 内容摘要 → ④ 内容展开 → ⑤ 图片
                   → ⑥ 热评/最新评论 → ⑦ 评论展开。
                   门禁按真实渲染出来的 HTML 逐段比对这个顺序，调换会变红 -->
              <div class="card">
                <!-- ① 用户信息（分区第一段） -->
                <div class="card__who">
                  <span class="avatar" aria-hidden="true">
                    {{ initialOf(authorName(p.username, p.uid)) }}
                  </span>
                  <RouterLink
                    class="card__author"
                    :to="{ name: 'user-profile', params: { uid: p.uid } }"
                  >
                    {{ authorName(p.username, p.uid) }}
                  </RouterLink>
                  <span class="card__time">{{ relativeTime(p.createTime) }}</span>
                </div>

                <div class="card__body">
                  <!-- 置顶与视频计数是**元信息标签**，不是分区，所以跟在用户信息下面、标题前面 -->
                  <p v-if="isPinned(p) || mediaList(p.videos).length" class="card__flags">
                    <span v-if="isPinned(p)" class="card__flag card__flag--top">置顶</span>
                    <span v-if="mediaList(p.videos).length" class="card__flag">
                      <PhVideoCamera :size="12" aria-hidden="true" />
                      {{ mediaList(p.videos).length }}
                    </span>
                  </p>

                  <!-- ② 标题：**完整显示，绝不截断/折叠**（用户第 3 条反馈）。
                       给足行数、按数据原样换行（样式里 `white-space: pre-line`），
                       标题里的换行不会丢。标题里**没有**展开按钮、没有两行裁切、没有渐隐 -->
                  <div class="card__titlespace">
                    <h2 class="card__title">
                      <RouterLink :to="{ name: 'community-post', params: { postId: p.postId } }">
                        {{ titleText(p.title) }}
                      </RouterLink>
                    </h2>
                  </div>

                  <p v-if="topicList(p.topics).length" class="card__topics">
                    <span v-for="t in topicList(p.topics)" :key="t.topicId" class="card__topic">
                      #{{ t.name }}
                    </span>
                  </p>

                  <!-- ③ 内容摘要（**后端已定稿**：`contentExcerpt` 是正文纯前缀，按码点截 200、
                       不加省略号、无正文 = `""`）。读不到字段就整块不渲染（不是渲染空框，
                       更不是拿标题/正文冒充）。字段名与判定全在 `postExcerpt.ts`。
                       ⚠️ **2026-09-21 用户要求：摘要最多 5 行，超过必须展开才能看**
                       —— 截断用 CSS 真截断（`-webkit-line-clamp: 5`），**不是**字符数阈值；
                       ⚠️ 这里**不许**再加第二处渐变遮罩（设计系统只允许舞台顶部一处渐变，
                       全站唯一的 `linear-gradient` 在用户主页列表的标题渐隐里）。 -->
                  <p
                    v-if="hasExcerpt(p)"
                    :ref="(el) => bindExcerpt(p.postId, el)"
                    class="card__excerpt"
                    :class="{ 'card__excerpt--clamp': !isBodyExpanded(p.postId) }"
                  >
                    {{ excerptOf(p).text }}
                  </p>

                  <!-- ④ 内容展开：出现条件是 **「摘要真溢出了 5 行」或「后端说这 200 字是截断的」**
                       任一成立（后者覆盖"后端摘要本身就被截了 200 码点"的情形）。
                       展开后**不再截断**（`card__excerpt--clamp` 被摘掉），可以收起回到 5 行。
                       全文来自详情接口（**带 countView=false**，不污染浏览量）；
                       失败时留住摘要 + 在这里如实说清原因，不假装展开成功。 -->
                  <template v-if="canExpandBody(p) || fullTextOf(p.postId).text">
                    <button
                      class="card__expand"
                      type="button"
                      :aria-expanded="isBodyExpanded(p.postId)"
                      :disabled="fullTextOf(p.postId).loading"
                      @click="toggleBody(p)"
                    >
                      {{
                        fullTextOf(p.postId).loading
                          ? '正文加载中'
                          : isBodyExpanded(p.postId)
                            ? '收起正文'
                            : '内容展开'
                      }}
                    </button>
                    <p
                      v-if="isBodyExpanded(p.postId) && fullTextOf(p.postId).text"
                      class="card__full"
                    >
                      {{ fullTextOf(p.postId).text }}
                    </p>
                    <p v-if="fullTextOf(p.postId).error" class="mw-alert mw-alert--danger card__fullerr">
                      {{ fullTextOf(p.postId).error }}
                    </p>
                  </template>

                  <!-- ⑤ 图片：单图 → 整块是进帖子的链接（保留原有入口）；
                       多图 → 正方形网格 + 灯箱，点开看大图并从点击那张开始；
                       无图 → 这一块**根本不渲染**（`PostMediaGrid` 在 0 张图时根节点都不输出） -->
                  <RouterLink
                    v-if="singleMedia(p)"
                    class="card__cover"
                    :to="{ name: 'community-post', params: { postId: p.postId } }"
                  >
                    <PostMediaGrid :images="mediaOf(p)" variant="card" single-as-link />
                  </RouterLink>
                  <PostMediaGrid v-else class="card__cover" :images="mediaOf(p)" variant="card" />

                  <!-- ⑥ 卡片底部互动区：**点赞数 → 收藏数 → 评论数**（2026-09-21 用户要求
                       "评论数量写在社区标签的最下端的收藏数量的后面"）。
                       ⚠️ 评论数**只有这一处**展示位（原来那个单独的评论条数区块已删掉，
                       同一件事不许出现在两个地方）。
                       - 点赞 / 收藏：**真调接口**（POST 与 DELETE 两条后端都有 ⇒ 可双向切换、幂等），
                         乐观更新 + 失败回滚 + 未登录跳登录；
                       - 评论数：**点一下进帖子详情**的真入口（看评论/发评论都在详情里），不是死按钮；
                       - 三个都是**图标 + 数字**，图标来自项目现有的 `@phosphor-icons/vue`；
                       - ⚠️ 评论数**字段缺席就整块不渲染**（不编造数字）；
                       - ⚠️ 界面**不许出现**「热评 / 最高赞 / 置顶」这类文案（门禁有反向断言）。 -->
                  <ul class="card__stats">
                    <li>
                      <PhEye :size="14" aria-hidden="true" />
                      <span class="mw-num">{{ countText(p.viewCount) }}</span>
                    </li>
                    <!-- ② 点赞：**卡片外就能做**的真操作（乐观更新 + 失败回滚 + 未登录跳登录） -->
                    <li>
                      <button
                        class="card__act"
                        :class="{ 'card__act--on': actionOf(p).liked }"
                        type="button"
                        :aria-pressed="actionOf(p).liked"
                        :disabled="actionOf(p).busy === 'like'"
                        @click="toggleLike(p)"
                      >
                        <PhHeart
                          :size="14"
                          :weight="actionOf(p).liked ? 'fill' : 'regular'"
                          aria-hidden="true"
                        />
                        <span class="mw-num">{{ countText(actionOf(p).likeCount) }}</span>
                      </button>
                    </li>
                    <!-- ③ 收藏：同上（`POST|DELETE /community/favorite/{postId}` 两条后端都在） -->
                    <li>
                      <button
                        class="card__act"
                        :class="{ 'card__act--on': actionOf(p).favorited }"
                        type="button"
                        :aria-pressed="actionOf(p).favorited"
                        :disabled="actionOf(p).busy === 'favorite'"
                        @click="toggleFavorite(p)"
                      >
                        <PhBookmarkSimple
                          :size="14"
                          :weight="actionOf(p).favorited ? 'fill' : 'regular'"
                          aria-hidden="true"
                        />
                        <span class="mw-num">{{ countText(actionOf(p).favoriteCount) }}</span>
                      </button>
                    </li>
                    <!-- ④ 评论数：**紧跟收藏数之后，排在最后**（用户指定的位置）。
                         真链接进帖子详情；`:aria-label` 把"多少条评论"说全（视觉上只留图标 + 数字）。 -->
                    <li v-if="commentCountOf(p) !== null">
                      <RouterLink
                        class="card__act card__act--link"
                        :to="{ name: 'community-post', params: { postId: p.postId } }"
                        :aria-label="`${commentCountOf(p)} 条评论，点进帖子查看`"
                        :title="`${commentCountOf(p)} 条评论`"
                      >
                        <PhChatCircle :size="14" aria-hidden="true" />
                        <span class="mw-num">{{ countText(commentCountOf(p)) }}</span>
                      </RouterLink>
                    </li>
                  </ul>

                  <!-- 操作失败时**如实提示**（回滚之后界面已恢复原状，这里说清原因） -->
                  <p v-if="actionOf(p).error" class="mw-alert mw-alert--danger card__acterr">
                    {{ actionOf(p).error }}
                  </p>
                </div>
              </div>
            </li>
          </ul>

          <div class="more">
            <button
              v-if="hasMore"
              class="mw-btn mw-btn--ghost"
              type="button"
              :disabled="moreLoading"
              @click="loadMore"
            >
              {{ moreLoading ? '正在加载' : '加载更多' }}
            </button>
            <p v-else class="more__end">没有更多了，一共 {{ countLabel }}</p>
          </div>
        </template>
      </div>

      <aside class="side">
        <div class="panel">
          <h2 class="panel__title">我的社区</h2>
          <p class="panel__desc">赞过与收藏过的帖子都存在服务端，换设备登录也能看到。</p>
          <div class="mine">
            <button
              class="mine__item"
              :class="{ 'mine__item--on': mode === 'like' }"
              type="button"
              @click="selectMode('like')"
            >
              <PhHeart :size="16" aria-hidden="true" />
              我赞过的
            </button>
            <button
              class="mine__item"
              :class="{ 'mine__item--on': mode === 'favorite' }"
              type="button"
              @click="selectMode('favorite')"
            >
              <PhBookmarkSimple :size="16" aria-hidden="true" />
              我收藏的
            </button>
          </div>
        </div>

        <div class="panel">
          <h2 class="panel__title">
            <PhFire :size="16" aria-hidden="true" />
            话题热榜
          </h2>
          <p v-if="topicsLoading" class="panel__desc">正在读取</p>
          <p v-else-if="topicsError" class="panel__desc">
            {{ topicsError }}
            <button class="link" type="button" @click="loadTopics">重试</button>
          </p>
          <p v-else-if="!topics.length" class="panel__desc">
            还没有话题。帖子里带上 #话题 之后，它会出现在这里。
          </p>
          <ul v-else class="topics">
            <li v-for="t in topics" :key="t.topicId">
              <!-- ⚠️ **真链接**（不是死按钮）：跳到社区页并带上 `?topic=<id>`。
                   社区页本身没有独立的话题路由，所以用 query 表达"进了这个话题"，
                   `onMounted` 读 query 就会把这个话题选上（同一页内切换、可分享、可后退）。 -->
              <RouterLink
                class="topic"
                :class="{ 'topic--on': mode === 'topic' && activeTopic?.topicId === t.topicId }"
                :to="{ name: 'community', query: { topic: String(t.topicId) } }"
              >
                <span class="topic__name">
                  <PhHash :size="12" aria-hidden="true" />
                  {{ t.name }}
                </span>
                <span class="topic__count mw-num">{{ countText(t.postCount) }} 篇</span>
              </RouterLink>
            </li>
          </ul>
        </div>

        <p class="note">
          社区接口没有头像字段，这里的圆形头像用昵称首字代替；昵称也拿不到时按用户编号显示。
          列表卡片不返回正文，想看全文进帖子详情页。
        </p>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--mw-space-4);
  margin-bottom: var(--mw-space-6);
}
.head .mw-caption {
  max-width: 62ch;
  margin-top: var(--mw-space-2);
}

.gate {
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
  background: var(--mw-surface);
}
.gate__desc {
  max-width: 52ch;
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: var(--mw-space-8);
  align-items: start;
}
.main {
  min-width: 0;
}

/* ---- 发帖面板 ---- */
.composer {
  margin-bottom: var(--mw-space-4);
}
.composer__head {
  margin-bottom: var(--mw-space-4);
}
.composer__fields {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-3);
}
/* 全局 .mw-field 只给 <label> 上了说明文字的样式，这里用的是 <span>，补一条 */
.composer__fields .mw-field > span {
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
}
.composer__area {
  min-height: 120px;
  padding: var(--mw-space-3) var(--mw-space-4);
  line-height: var(--mw-line-height);
  resize: vertical;
}
.composer__actions {
  display: flex;
  gap: var(--mw-space-2);
  margin-top: var(--mw-space-4);
}
.feedback {
  display: flex;
  align-items: center;
  gap: var(--mw-space-2);
  margin-bottom: var(--mw-space-4);
}
.link {
  border: none;
  background: none;
  padding: 0;
  color: var(--mw-brand-ink);
  font-family: inherit;
  font-size: inherit;
  font-weight: 600;
  text-decoration: underline;
  cursor: pointer;
}

/* ---- 版块 chips ---- */
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mw-space-2);
  margin-bottom: var(--mw-space-4);
}
.chip {
  padding: var(--mw-space-1) var(--mw-space-4);
  min-height: 34px;
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-pill);
  background: var(--mw-surface);
  color: var(--mw-ink-2);
  font-family: inherit;
  font-size: var(--mw-font-caption);
  font-weight: 600;
  cursor: pointer;
}
.chip--on {
  background: var(--mw-brand-ink);
  border-color: var(--mw-brand-ink);
  color: #fff;
}
.chip--ghost {
  display: inline-flex;
  align-items: center;
  color: var(--mw-muted);
  background: transparent;
  border-style: dashed;
  cursor: default;
}

.feedbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--mw-space-4);
  margin-bottom: var(--mw-space-3);
}
.feedbar__title {
  font-size: var(--mw-font-h2);
  font-weight: 600;
  letter-spacing: -0.01em;
}
.feedbar__sub {
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}
.feedbar__sub--plain {
  margin-bottom: var(--mw-space-3);
}

/* ---- 信息流卡片 ---- */
/**
 * ⚠️ **2026-09-21：信息流改成单列**（一屏一行一帖）。
 *
 * 原来这里是多列网格：
 * ```
 * display: grid;
 * grid-template-columns: repeat(auto-fill, minmax(236px, 1fr));
 * ```
 * 用户真机反馈「第一排的社区内容和第二排之间会有很大的空的区域」——
 * 根因就是**多列网格**：一行的行高 = 该行最高那张卡，矮卡下方必然留下空白。
 * 改成单列（flex column）之后，"行"这个概念消失了，空白问题从根上没了。
 *
 * ⚠️ 单列 **≠ 拉满全宽**：内容列自己限宽并居中（太长读不动）。
 * ⚠️ **2026-09-21 用户："社区内容模块再稍微窄一点"** —— 注意是**稍微**，
 * 不是回到之前被投诉过的"窄条"（那版是 `max-width: 68ch`，窄到标题频繁折行）。
 * 收窄量用**既有令牌**算：容器上限 `--mw-maxw`(1240px) 减 5 个 `--mw-space-8`(32px)
 * = 1240 − 160 = **1080px**（约为上限的 87%）——**没有新增令牌**，右栏仍是 `300px`（尺寸不变）。
 * 宽度用**容器比例**而不是 `ch`：`ch` 与字号绑死，换个字号就变成另一个宽度（之前正是那样翻车的）。
 * 手机端由 `.layout` 的媒体查询折成单列，这里不再有横向滚动风险。
 */
.feed {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-4);
  max-width: min(100%, calc(var(--mw-maxw) - var(--mw-space-8) * 5));
  margin: 0 auto;
}
.feed__row {
  display: block;
}
.card-skeleton {
  display: block;
  height: 268px;
}
.card {
  display: flex;
  flex-direction: column;
  /* ⚠️ **没有 `height: 100%`**：那是多列网格时代的写法（为了让同排卡片等高）。
     单列之后"同排"不存在了，`height: 100%` 只会让无图/短帖被**拉成同高留白** ——
     卡片高度由内容决定，不再人为撑高。 */
  overflow: hidden;
  background: var(--mw-surface);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
  color: inherit;
  transition: box-shadow 0.15s ease;
}
.card:hover {
  text-decoration: none;
  box-shadow: var(--mw-shadow-float);
}
/* ⚠️ **2026-09-21**：这里原来是"封面灰框"——固定 `aspect-ratio: 16/9` + `--mw-canvas` 底色 +
   下边框，并且在没有封面时渲染一个 `.card__cover-empty` 占位块。
   用户真机反馈过"图片有的太大了 / 灰框空占位"，所以这一整套**已经删掉**：
   图片改由 `PostMediaGrid` 负责（列数由张数决定、宽度相对容器、高度有上限），
   **无图就整块不渲染**（不是渲染一个空框，也不是 `display:none` 藏起来）。
   现在 `.card__cover` 只是一个薄薄的包裹层，**不许**再给它比例/底色/下边框。 */
.card__cover {
  display: block;
}
/**
 * ⚠️ **2026-09-21 补回（事故后重写样式块时漏掉的一条，真实回归）**：
 * 整卡不再是 `<a>`（避免"a 里套 a"），改成"封面"与"作者名"**各自是链接**。
 * 链接**必须 `color: inherit`**：否则浏览器默认的链接色会把作者名整段染成蓝紫，
 * 与暖夜舞台的暖色文字体系冲突；`text-decoration: none` 避免下划线。
 * ⚠️ 这条是 `check-userpage-web.js` 的 `列表卡作者名有了链接样式（color: inherit）`
 * 正在钉的东西 —— **不许删**。
 */
.card__cover,
.card__author {
  color: inherit;
  text-decoration: none;
}
.card__body {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-2);
  flex: 1;
  padding: var(--mw-space-4);
}
/**
 * ② 标题：⚠️ **必须完整显示**（2026-09-21 用户反馈"标题必须完整显示"）。
 *
 * 这里原来是 `display: -webkit-box` + `-webkit-line-clamp: 2` + `overflow: hidden`（两行裁切）。
 * 那一整套**已删除**：标题**没有**行数裁切、**没有** `max-height`、**没有** `overflow: hidden`、
 * **没有**渐隐遮罩，也**没有**「标题展开」按钮 —— 用户明确说标题的换行是数据的事，页面照原样显示。
 * `white-space: pre-line` 保留标题里的换行（数据里真有换行时不丢），
 * `overflow-wrap: anywhere` 防止长串（URL / 连续英文）撑破卡片。
 */
.card__titlespace {
  min-width: 0;
}
.card__title {
  font-size: var(--mw-font-body);
  font-weight: 600;
  line-height: 1.4;
  letter-spacing: -0.01em;
  white-space: pre-line;
  overflow-wrap: anywhere;
}
.card__topics {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mw-space-2);
  font-size: var(--mw-font-micro);
  color: var(--mw-brand-ink);
}
/**
 * ① 用户信息：**卡片的第一段**（2026-09-21 用户指定的卡片分区顺序：
 * 用户信息 → 标题 → 内容摘要 → 内容展开 → 图片 → 评论条数 → 计数/操作条）。
 * 所以这里**不能**再有 `margin-top: auto`（那是"用户信息贴卡片底部"的旧布局，
 * 会把作者名甩到卡片最下面，和"第一段"直接矛盾）。
 */
.card__who {
  display: flex;
  align-items: center;
  gap: var(--mw-space-2);
  padding: var(--mw-space-4) var(--mw-space-4) 0;
}
.avatar {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  flex: none;
  border-radius: var(--mw-radius-pill);
  /* ⚠️ 原来是 v2 退役色 `rgba(10, 125, 109, 0.1)`（冷青绿），设计系统 v3 已换成品牌橙的淡底 */
  background: rgba(194, 65, 12, 0.1);
  color: var(--mw-brand-ink);
  font-size: var(--mw-font-micro);
  font-weight: 700;
}
.card__author {
  font-size: var(--mw-font-micro);
  font-weight: 600;
  max-width: 12ch;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card__time {
  margin-left: auto;
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
  white-space: nowrap;
}
.card__stats {
  display: flex;
  align-items: center;
  gap: var(--mw-space-4);
  margin-top: var(--mw-space-2);
  padding-top: var(--mw-space-2);
  border-top: 1px solid var(--mw-line);
  font-size: var(--mw-font-micro);
  color: var(--mw-ink-2);
}
.card__stats li {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-1);
}
/* ---- 卡片外的点赞 / 收藏（真操作按钮：**图标 + 数字**，不许只留图标）----
   ⚠️ 图标来源：`@phosphor-icons/vue`（项目原本就在用的库）。已核实 `package.json` 里
   **没有** `element-plus` / `@element-plus/icons-vue`，所以**没有**新增依赖，也**没有** emoji、
   没有手绘内联 `<svg>`。
   ⚠️ 状态差异（已赞 / 未赞）**只用既有令牌**：颜色切到 `--mw-brand-ink` + 淡品牌底 +
   图标填充（模板里 `:weight` 切 `fill`/`regular`），不新增令牌、不写死新色值。
   ⚠️ `.stat--on` 是"旧计数条上色"的规则，随旧的只读计数条一起删掉了（现在是真按钮）。 */
.card__act {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-1);
  min-height: 28px;
  padding: 0 var(--mw-space-2);
  border: 1px solid transparent;
  border-radius: var(--mw-radius-pill);
  background: none;
  color: var(--mw-ink-2);
  font-family: inherit;
  font-size: var(--mw-font-micro);
  cursor: pointer;
}
.card__act:hover:not(:disabled) {
  border-color: var(--mw-line);
  color: var(--mw-ink);
}
/* 已激活：**明显但克制** —— 品牌色 + 淡底，与未激活的灰字形成对比 */
.card__act--on {
  border-color: var(--mw-brand-ink);
  background: rgba(194, 65, 12, 0.08);
  color: var(--mw-brand-ink);
}
.card__act:disabled {
  opacity: 0.6;
  cursor: default;
}
.card__acterr {
  margin-top: var(--mw-space-2);
  font-size: var(--mw-font-micro);
}

.more {
  display: flex;
  justify-content: center;
  margin-top: var(--mw-space-6);
}
.more__end {
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}

/* ---- 侧栏 ---- */
.side {
  display: flex;
  flex-direction: column;
  /* 尺寸不变：两块之间的距离仍是原值 `--mw-space-4`（本次只加粘性，没改尺寸） */
  gap: var(--mw-space-4);
  /* ⚠️ **跟随下滑（粘性）**：2026-09-21 用户要求"社区和话题热榜跟随用户滑动要保留"。
     做法是**整个右栏**一个 sticky 容器（两块 panel 在它内部纵向排列）——
     这样「我的社区」与「话题热榜」**一起跟随**，而且**天然不会互相遮挡**
     （它们在同一个 flex 列里顺序排版，不是两个各自 sticky 的元素去抢同一个 top 位置，
     那种写法才会叠在一起）。没有用任何滚动监听。
     `top` 取顶栏高度 `--mw-nav-h`（顶栏 `.mw-nav` 的高度令牌），
     所以粘住时**不会被顶栏盖住**、也不会盖住左侧信息流（两者是并排的两列）。
     `max-height` + `overflow-y: auto`：右栏比视口高时自己滚，绝不让内容被裁掉。
     窄屏（≤960px）`.layout` 折成单列，右栏变成正文下方的一段普通内容 ——
     那时 `position: sticky` 在单列流里没有跟随意义，由媒体查询关掉（见文件末尾）。 */
  position: sticky;
  top: var(--mw-nav-h);
  max-height: calc(100vh - var(--mw-nav-h) - var(--mw-space-4));
  overflow-y: auto;
}
.panel {
  padding: var(--mw-space-6);
  background: var(--mw-surface);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
}
.panel__title {
  display: flex;
  align-items: center;
  gap: var(--mw-space-1);
  font-size: var(--mw-font-h2);
  font-weight: 600;
}
.panel__desc {
  margin-top: var(--mw-space-2);
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
}
.mine {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-2);
  margin-top: var(--mw-space-4);
}
.mine__item {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-2);
  min-height: 40px;
  padding: 0 var(--mw-space-3);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-input);
  background: var(--mw-surface);
  color: var(--mw-ink);
  font-family: inherit;
  font-size: var(--mw-font-caption);
  font-weight: 600;
  cursor: pointer;
}
.mine__item--on {
  border-color: var(--mw-brand-ink);
  /* ⚠️ 原来是 v2 退役色 `rgba(10, 125, 109, 0.08)`（冷青绿）—— 设计系统 v3 换成品牌橙的淡底 */
  background: rgba(194, 65, 12, 0.08);
  color: var(--mw-brand-ink);
}
.topics {
  margin-top: var(--mw-space-2);
}
.topic {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--mw-space-2);
  width: 100%;
  min-height: 38px;
  padding: 0;
  border: none;
  border-bottom: 1px solid var(--mw-line);
  background: none;
  color: var(--mw-ink);
  font-family: inherit;
  font-size: var(--mw-font-caption);
  text-align: left;
  cursor: pointer;
}
.topics li:last-child .topic {
  border-bottom: none;
}
.topic__name {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-weight: 600;
}
.topic--on .topic__name {
  color: var(--mw-brand-ink);
}
.topic__count {
  color: var(--mw-muted);
  font-size: var(--mw-font-micro);
}
.note {
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}

@media (max-width: 960px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .head {
    flex-direction: column;
    align-items: flex-start;
  }
}

/* ==================================================================== */
/* 卡片分区 ③~⑥ 的样式（单列改版新增；2026-09-21）                       */
/* ==================================================================== */

/* ---- ①b 元信息标签（置顶 / 视频数）：跟用户信息同段，所以内边距不用重复给 ---- */
.card__flags {
  display: flex;
  align-items: center;
  gap: var(--mw-space-2);
  font-size: var(--mw-font-micro);
}
.card__flag {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 1px var(--mw-space-2);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-tag);
  color: var(--mw-ink-2);
  font-weight: 600;
}
.card__flag--top {
  border-color: var(--mw-brand-ink);
  color: var(--mw-brand-ink);
}
.card__topic {
  color: var(--mw-brand-ink);
}

/* ---- ③ 内容摘要（后端 `contentExcerpt`：正文纯前缀，最多 200 码点） ---- */
.card__excerpt {
  color: var(--mw-ink-2);
  font-size: var(--mw-font-caption);
  line-height: var(--mw-line-height);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
/* ③b **摘要最多 5 行**（用户要求"超过 5 行的部分必须展开才能看"）。
   标准三件套，**真 CSS 截断** —— 不是按字数估行数（不同字号/容器宽度下行数不同，
   估出来的阈值必然在某一档上错：要么该展开没展开，要么留一个点了没内容的假入口）。
   ⚠️ **没有渐隐遮罩**：设计系统只允许舞台顶部那一处渐变，全站唯一的 `linear-gradient`
   在用户主页列表的标题渐隐里，这里**不许**再加第二处。
   展开之后这个类会被摘掉（模板 `:class` 由 `isBodyExpanded` 控制）⇒ 全文不再截断。 */
.card__excerpt--clamp {
  display: -webkit-box;
  -webkit-line-clamp: 5;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* ---- ④ 内容展开 / 收起（全文来自详情接口，带 countView=false） ---- */
.card__expand {
  align-self: flex-start;
  min-height: 28px;
  padding: 0 var(--mw-space-3);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-pill);
  background: none;
  color: var(--mw-ink-2);
  font-family: inherit;
  font-size: var(--mw-font-micro);
  font-weight: 600;
  cursor: pointer;
}
.card__expand:hover:not(:disabled) {
  border-color: var(--mw-brand-ink);
  color: var(--mw-brand-ink);
}
.card__expand:disabled {
  opacity: 0.6;
  cursor: default;
}
.card__full {
  max-width: 72ch;
  padding-top: var(--mw-space-2);
  border-top: 1px solid var(--mw-line);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.card__fullerr {
  font-size: var(--mw-font-micro);
}

/* ⚠️ **2026-09-21 删除的死规则**：这里原来有 `.card__comments` / `.card__comments:hover` /
   `.card__cmore` 三条样式。它们服务的是"卡片里单独一条评论条数区块"（图标 + 「N 条评论」+
   「去看评论 / 写评论」+ 箭头），而用户随后要求把评论数**并进卡片底部互动区**、排在收藏数之后
   （「评论数量写在社区标签的最下端的收藏数量的后面就可以」）。
   那一整块 DOM 已经删掉 ⇒ 这三条规则**没有任何元素会命中**，属于死代码，一并删掉
   （门禁里有"CommunityView 源码里 `card__comments` 零命中"的断言钉住，防止再溜回来）。 */
</style>
