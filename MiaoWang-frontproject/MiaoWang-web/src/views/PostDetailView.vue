<script setup lang="ts">
/**
 * 帖子详情（Web）
 *
 * 详情 DTO 是社区里**唯一带正文的**接口：`content` + `images[]` + `videos[]` + `topics[]`。
 * 列表 DTO 没有正文，所以信息流卡片只能显示标题与封面，看全文必须进这一页。
 *
 * 三个实现决定：
 * 1) **帖子点赞 / 收藏选"等接口返回再更新界面"，不做乐观更新**。
 *    理由：`POST|DELETE /like/{postId}` 与 `/favorite/{postId}` 的返回 `data` 是 `null`，
 *    服务端也没有把新计数吐回来，所以计数只能由前端按"我这一下"加减。
 *    这两个调用都是幂等的（后端靠唯一键兜底，2026-09-19 真实账号实测：连发两次一律 `code:"0"`、
 *    计数不重复累加），慢一点重试是安全的，所以选择"请求成功后再改界面"。**本端仍未真调过这两条。**
 *    ⚠️ **评论点赞反过来做乐观更新**（`toggleCommentLike`）：它是逐条的行内动作，往返延迟直接落在
 *    手指底下；这两个接口 2026-09-21 已用真实账号实测过（幂等、计数细节见 `api/community.ts`），
 *    所以"先改界面、失败回滚"是安全的。两处口径不同是刻意的，别顺手统一。
 * 2) **评论用游标分页**：`nextCursor` 原样回传给下一次的 `lastId`，不做任何加减；
 *    一级评论自带 `children` 只是**正序预览**（条数不保证，"最多 3 条"不是硬契约），
 *    点"查看全部"才真的去拉 `/comment/reply`（也是正序）。
 * 3) **`commentCount` 把回复也算进去**（实测帖子 178：2 一级 + 2 回复 = 4）→ 计数文案写明"含回复"，
 *    免得看起来像"一级评论数"却对不上列表条数。
 */
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  PhArrowClockwise,
  PhArrowLeft,
  PhBookmarkSimple,
  PhChatCircle,
  PhChatTeardropText,
  PhEye,
  PhHeart,
  PhPaperPlaneRight,
  PhSignIn,
} from '@phosphor-icons/vue'
import { ApiError } from '@/api/request'
import PostMediaGrid from '@/components/community/PostMediaGrid.vue'
import {
  COMMENT_FETCH_ALL_MAX_PAGES,
  COMMENT_FETCH_ALL_SIZE,
  COMMENT_PAGE_SIZE,
  authorName,
  communityErrorMessage,
  countText,
  createComment,
  favoritePost,
  fullTimeText,
  getPost,
  initialOf,
  likeComment,
  likePost,
  listCommentReplies,
  listComments,
  listSections,
  mediaListSorted,
  relativeTime,
  sectionName,
  titleText,
  topicList,
  unfavoritePost,
  unlikeComment,
  unlikePost,
  type CommunitySection,
  type PostComment,
  type PostDetail,
} from '@/api/community'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const authStore = useAuthStore()
const loggedIn = computed(() => authStore.isLoggedIn)

const postId = Number(route.params.postId)

/* ---------- 帖子 ---------- */

const post = ref<PostDetail | null>(null)
// 初始就是 true：帖子读回来之前先给骨架，不先闪"没有评论"或空态
const loading = ref(true)
const error = ref('')
const unauthorized = ref(false)

/** 详情 DTO 没有 `isTop`，但 sectionId 要翻译成版块名，所以顺带把版块表拉一份 */
const sections = ref<CommunitySection[]>([])

// 详情 DTO 没有 `coverUrl`，可渲染顺序就是 `images` 按 `sort` 升序（实测 `sort` 从 0 起）
const images = computed(() => mediaListSorted(post.value?.images))
const videos = computed(() => mediaListSorted(post.value?.videos))
const topics = computed(() => topicList(post.value?.topics))
const needLogin = computed(() => !loggedIn.value || unauthorized.value)

/* ---------- 点赞 / 收藏（写路径：源码推断，未实测） ---------- */

// 服务端不会把新计数吐回来（这两个接口的 data 是 null），所以界面上的状态与计数由本地维护
const liked = ref(false)
const favorited = ref(false)
const likeCount = ref(0)
const favoriteCount = ref(0)
const interactBusy = ref<'like' | 'favorite' | ''>('')
const interactError = ref('')

/* ---------- 评论 ---------- */

const comments = ref<PostComment[]>([])
const commentsLoading = ref(true)
const commentsMore = ref(false)
const commentsError = ref('')
const commentsTotal = ref(0)
/** 游标：原样搬运，不做加减 */
const commentsCursor = ref<number | null>(null)
const commentsHasMore = ref(false)
/** 是否真的把**所有**页都拉完了（撞到安全上限 → `false`，页面如实说明"还有更多"） */
const allCommentsLoaded = ref(false)

/**
 * 每条一级评论的回复状态。
 *
 * `nextCursor === null` 表示"还没真拉过回复列表"（此时 `items` 是接口给的正序预览）；
 * 一旦拉过一页，后续都用返回的 `nextCursor` 续页。
 */
interface ReplyBucket {
  items: PostComment[]
  nextCursor: number | null
  hasMore: boolean
  loading: boolean
  error: string
}

const replies = ref<Record<number, ReplyBucket>>({})
const EMPTY_BUCKET: ReplyBucket = {
  items: [],
  nextCursor: null,
  hasMore: false,
  loading: false,
  error: '',
}

/** 模板里只读，不在这里建状态（建状态一律走 ensureBucket） */
function bucketOf(commentId: number): ReplyBucket {
  return replies.value[commentId] ?? EMPTY_BUCKET
}

/* ---------- 评论点赞（写路径：2026-09-21 真实账号实测过） ---------- */

/**
 * 评论点赞的**本地乐观状态**，按 `commentId` 覆盖 DTO 给的值。
 *
 * 只放"用户点过的那几条"：没点过的直接用 DTO 的 `liked` / `likeCount`（真源优先，不编数字）。
 * 之所以这里做乐观更新、而帖子点赞（`toggleLike`）选择"等接口返回"：
 * 点赞是**逐条**的行内动作，一次请求的往返延迟直接体现在手指底下；而这两个接口
 * 2026-09-21 实测是幂等的（重复点赞 / 未点赞就取消都回 `code:"0"` 且计数不动），
 * 所以"先改界面、失败回滚"是安全的。
 */
interface CommentLike {
  liked: boolean
  count: number
  busy: boolean
  error: string
}

const commentLikes = ref<Record<number, CommentLike>>({})

/** 一条评论/回复当前生效的点赞态（本地乐观状态优先，否则吃 DTO） */
function likeOf(c: PostComment): CommentLike {
  const st = commentLikes.value[c.commentId]
  if (st) return st
  return {
    liked: Boolean(c.liked),
    count: Math.max(0, Number(c.likeCount ?? 0)),
    busy: false,
    error: '',
  }
}

/** 建键（模板/computed 里只读，建状态一律走这里） */
function ensureLike(c: PostComment): CommentLike {
  const existing = commentLikes.value[c.commentId]
  if (existing) return existing
  const cur = likeOf(c)
  commentLikes.value[c.commentId] = {
    liked: cur.liked,
    count: cur.count,
    busy: false,
    error: '',
  }
  // ⚠️ 必须把值从 `commentLikes.value` **读回来**（拿到响应式代理）再返回，理由同 `ensureBucket`：
  // 直接返回刚写进去的原始对象，后续 `st.busy = …` 不经过代理、不会触发视图更新。
  return commentLikes.value[c.commentId] ?? cur
}

/**
 * `children` 预览条数不保证（"最多 3 条"**不是硬契约**，实测没压到上限），
 * 所以"还有没有更多回复"只由 `replyCount > 已拿到的预览条数` 推 —— 预览多了就自然没有下一页。
 */
function ensureBucket(c: PostComment): ReplyBucket {
  const existing = replies.value[c.commentId]
  if (existing) return existing
  const preview = Array.isArray(c.children) ? c.children : []
  // 一级评论的 replyCount 是数字（无回复为 0）；回复项才是 null → null 时退回预览长度
  const total = c.replyCount === null || c.replyCount === undefined ? preview.length : Number(c.replyCount) || 0
  const fresh: ReplyBucket = {
    items: preview,
    nextCursor: null,
    hasMore: total > preview.length,
    loading: false,
    error: '',
  }
  replies.value[c.commentId] = fresh
  // ⚠️ 必须把值从 `replies.value` **读回来**（拿到响应式代理）再返回：
  // 直接返回刚写进去的原始对象，后续 `bucket.items = …` 不经过代理、不会触发视图更新，
  // 表现就是"点了查看全部回复但列表一直是空的"。
  return replies.value[c.commentId] ?? fresh
}

/* ---------- 评论 / 回复的输入 ---------- */

/** `null` = 正在写一级评论；否则是正在回复的那条（一级评论或回复都可能是它） */
const replyTarget = ref<PostComment | null>(null)
const draft = ref('')
const replyDraft = ref('')
/** 哪一份草稿正在提交（页面上同时存在两个输入框，不能用布尔量表示） */
const submittingScope = ref<'top' | 'reply' | ''>('')
const commentError = ref('')
const commentNotice = ref('')

/**
 * 回复挂在哪个一级评论下面：回复"某条回复"时也要挂到它的一级评论上。
 *
 * ⚠️ **一级评论的 `parentId` 实测是数字 `0`（不是 `null`）**，所以这里不能写成
 * `c.parentId ?? c.commentId` —— 那对一级评论会算出 `0`：回复框的 `v-if` 永远不成立（点了没反应），
 * 提交时还会把 `parentId: 0` 发出去（后端拿 0 去查评论 → `B000315`）。
 * 口径：`parentId > 0` 才是"回复"，否则它自己就是一级评论。
 */
function topLevelIdOf(c: PostComment) {
  const parentId = Number(c.parentId ?? 0)
  return parentId > 0 ? parentId : c.commentId
}

/* ---------- 读数据 ---------- */

function noteError(e: unknown): string {
  if (e instanceof ApiError && e.isUnauthorized) {
    unauthorized.value = true
    return '登录状态失效了，请重新登录'
  }
  return communityErrorMessage(e)
}

async function loadPost() {
  loading.value = true
  error.value = ''
  // 路由上是 \d+ 限定，理论上进不来，但深链被手改过时也要给出错态而不是一直转圈
  if (!Number.isFinite(postId) || postId <= 0) {
    error.value = '地址里的帖子编号不对'
    loading.value = false
    return
  }
  try {
    const detail = await getPost(postId)
    post.value = detail
    liked.value = Boolean(detail.liked)
    favorited.value = Boolean(detail.favorited)
    likeCount.value = Number(detail.likeCount ?? 0)
    favoriteCount.value = Number(detail.favoriteCount ?? 0)
    commentsTotal.value = Number(detail.commentCount ?? 0)
  } catch (e) {
    error.value = noteError(e)
  } finally {
    loading.value = false
  }
}

async function loadSections() {
  try {
    const list = await listSections()
    sections.value = Array.isArray(list) ? list : []
  } catch (e) {
    // 版块名只用于面包屑，拿不到就不显示，不因为这个挡住整页
    noteError(e)
  }
}

/**
 * 拉这一帖的**全部**评论（2026-09-21 用户要求：「帖子里默认显示所有的评论就可以了」）。
 *
 * ⚠️ 后端评论列表是**游标分页**，而且 `size` 有**硬上限 50**
 * （`CommentServiceImpl.normalizeSize()` = `Math.min(size, MAX_PAGE_SIZE)`，`MAX_PAGE_SIZE` = 50）——
 * **传 1000 也只会给 50**。所以"全部"只能是"**自动把后续页拉完**"：
 * 每页取满 50，拿 `nextCursor` 原样回传当下一页的 `lastId`，循环到 `hasMore` 为假为止。
 *
 * ⚠️ **如实**：循环有 `COMMENT_FETCH_ALL_MAX_PAGES`（20 页 = 1000 条）安全上限。
 * 真撞到上限时 `allCommentsLoaded` 会留在 `false`，页面据此显示"已加载 N 条（还有更多）"，
 * **不假装"全部已显示"**。
 *
 * ⚠️ 失败时**保留已有数据并如实报错**（`commentsError`），绝不静默渲染成"暂无评论"。
 */
async function loadAllComments() {
  if (!post.value) return
  commentsLoading.value = true
  commentsError.value = ''
  commentsMore.value = false
  try {
    const collected: PostComment[] = []
    let cursor: number | null = null
    let more = true
    let pages = 0
    while (more && pages < COMMENT_FETCH_ALL_MAX_PAGES) {
      const res = await listComments({
        postId,
        lastId: cursor ?? undefined,
        size: COMMENT_FETCH_ALL_SIZE,
      })
      const records = Array.isArray(res?.records) ? res.records : []
      collected.push(...records)
      cursor = res?.nextCursor ?? null
      const serverHasMore = Boolean(res?.hasMore)
      pages++
      /**
       * 后端说"还有下一页"却**没给游标** ⇒ 这不是"拉完了"，是**拉不动**。
       * 停下来（不能原地打转），但 **`more` 保持 true**：
       * 页面据此如实显示"服务端还有更多"，`allCommentsLoaded` 留在 `false` ——
       * **绝不谎报"全部评论已显示"**（那会让用户以为漏掉的评论不存在）。
       */
      if (serverHasMore && cursor === null) {
        more = true
        break
      }
      more = serverHasMore
    }
    comments.value = collected
    commentsCursor.value = cursor
    commentsHasMore.value = more
    allCommentsLoaded.value = !more
    // 列表整体被换掉 → 回复状态与评论点赞的本地乐观状态一并作废
    replies.value = {}
    commentLikes.value = {}
    for (const c of comments.value) ensureBucket(c)
  } catch (e) {
    // 失败：已经拿到的评论**不清空**，如实报错
    commentsError.value = noteError(e)
    commentsHasMore.value = false
  } finally {
    commentsLoading.value = false
  }
}

/** 兼容旧调用点的薄封装（详情页不再有"加载更多"，`append` 恒为 false） */
async function loadComments(append: boolean) {
  if (append) return
  await loadAllComments()
}

/** 拉某条一级评论的回复：第一次是真拉第一页（替换预览），之后按游标续页 */
async function loadReplies(c: PostComment) {
  const bucket = ensureBucket(c)
  if (bucket.loading) return
  const append = bucket.nextCursor !== null
  bucket.loading = true
  bucket.error = ''
  try {
    const res = await listCommentReplies({
      commentId: c.commentId,
      lastId: append ? bucket.nextCursor : undefined,
      size: COMMENT_PAGE_SIZE,
    })
    const records = Array.isArray(res?.records) ? res.records : []
    bucket.items = append ? [...bucket.items, ...records] : records
    bucket.nextCursor = res?.nextCursor ?? null
    bucket.hasMore = Boolean(res?.hasMore) && bucket.nextCursor !== null
  } catch (e) {
    bucket.error = noteError(e)
    if (append) bucket.hasMore = false
  } finally {
    bucket.loading = false
  }
}

/* ---------- 写操作 ---------- */

async function toggleLike() {
  if (!post.value || interactBusy.value) return
  interactBusy.value = 'like'
  interactError.value = ''
  const wasLiked = liked.value
  try {
    if (wasLiked) await unlikePost(postId)
    else await likePost(postId)
    // 请求成功了才改界面；计数只能按"我这一下"加减，因为接口不返回新计数（见文件头的说明）
    liked.value = !wasLiked
    likeCount.value = Math.max(0, likeCount.value + (wasLiked ? -1 : 1))
  } catch (e) {
    interactError.value = noteError(e)
  } finally {
    interactBusy.value = ''
  }
}

async function toggleFavorite() {
  if (!post.value || interactBusy.value) return
  interactBusy.value = 'favorite'
  interactError.value = ''
  const wasFavorited = favorited.value
  try {
    if (wasFavorited) await unfavoritePost(postId)
    else await favoritePost(postId)
    favorited.value = !wasFavorited
    favoriteCount.value = Math.max(0, favoriteCount.value + (wasFavorited ? -1 : 1))
  } catch (e) {
    interactError.value = noteError(e)
  } finally {
    interactBusy.value = ''
  }
}

/**
 * 评论 / 回复的点赞与取消（**乐观更新 + 失败回滚**）。
 *
 * 口径（2026-09-21 真实账号在种子评论 101~104 上实测）：
 * - 一级评论与回复都能点赞（`CommentRespDTO` 两级都带 `liked` / `likeCount`）
 * - 成功 `code:"0"` + `data:null`，接口**不返回新计数** → 计数只能按"我这一下"加减
 * - 幂等：重复点赞 / 未点赞就取消都回 `code:"0"` 且计数不动
 * - `POST` 传不存在的评论 → `B000311`；`DELETE` 传不存在的评论 → `code:"0"`（后端不校验）
 * - 未登录 / 掉线：沿用本页既有的 `noteError` → 翻到"去登录"软门槛，**不新造登录弹窗**
 */
async function toggleCommentLike(c: PostComment) {
  const cur = likeOf(c)
  if (cur.busy) return
  const st = ensureLike(c)
  const next = !cur.liked
  const before = cur.count
  st.liked = next
  st.count = Math.max(0, before + (next ? 1 : -1))
  st.busy = true
  st.error = ''
  try {
    if (next) await likeComment(c.commentId)
    else await unlikeComment(c.commentId)
  } catch (e) {
    // 失败回滚：状态与计数都退回点之前，并把后端的话原样显示给用户（不静默）
    st.liked = !next
    st.count = before
    st.error = (next ? '点赞没成功：' : '取消点赞没成功：') + noteError(e)
  } finally {
    st.busy = false
  }
}

function startReply(target: PostComment) {
  replyTarget.value = target
  replyDraft.value = ''
  commentError.value = ''
  commentNotice.value = ''
}

function cancelReply() {
  replyTarget.value = null
  replyDraft.value = ''
}

/**
 * 发评论 / 回复（**本端没有真调过**，路径与字段按实测契约写）。
 *
 * 字段口径：`content` 必填且 ≤1000 字；`parentId` **不传 = 一级评论**，
 * 传了就挂到该评论的一级评论下（两级封顶）；`replyUid` 是被回复的人，用来显示"回复谁"。
 * 所以回复一条**回复**时：`parentId` 传它的一级评论 id，`replyUid` 传它的 uid；
 * 回复一条**一级评论**时：`parentId` 传这条评论自己的 id（**不是 `0`** —— 0 会被后端当成
 * 不存在的评论，回 `B000315`）。这一层由 `topLevelIdOf` 统一处理。
 *
 * 页面上同时有两个输入框（一级评论、行内回复），所以用 `scope` 区分，
 * 各自只读各自的草稿，避免"点着回复却在顶部框里打字"这种串台。
 */
async function submitComment(scope: 'top' | 'reply') {
  if (submittingScope.value || !post.value) return
  const target = scope === 'reply' ? replyTarget.value : null
  if (scope === 'reply' && !target) {
    commentError.value = '先选择要回复的评论'
    return
  }
  const content = (scope === 'reply' ? replyDraft.value : draft.value).trim()
  if (!content) {
    commentError.value = '评论不能为空'
    return
  }
  submittingScope.value = scope
  commentError.value = ''
  commentNotice.value = ''
  try {
    await createComment({
      postId,
      content,
      parentId: target ? topLevelIdOf(target) : undefined,
      replyUid: target ? target.uid : undefined,
    })
    if (scope === 'reply') {
      replyDraft.value = ''
      replyTarget.value = null
      commentNotice.value = '回复已发出，下面是重新读取的评论列表'
    } else {
      draft.value = ''
      commentNotice.value = '评论已发出，下面是重新读取的评论列表'
    }
    // 后端不返回这条评论在列表里的位置，直接重读第一页（评论按 commentId 倒序，新的在最前）
    await loadComments(false)
    commentsTotal.value += 1
  } catch (e) {
    commentError.value = noteError(e)
  } finally {
    submittingScope.value = ''
  }
}

onMounted(() => {
  if (!loggedIn.value) return
  void Promise.all([loadPost(), loadSections()]).then(() => {
    if (post.value) void loadComments(false)
  })
})
</script>

<template>
  <div class="mw-container mw-page">
    <RouterLink class="back" to="/community">
      <PhArrowLeft :size="16" aria-hidden="true" />
      返回社区
    </RouterLink>

    <section v-if="needLogin" class="mw-state gate">
      <PhSignIn :size="26" aria-hidden="true" />
      <p class="mw-state__title">登录后才能看帖子</p>
      <p class="gate__desc">社区经网关的每个接口都要求登录，帖子详情也不例外。</p>
      <RouterLink
        class="mw-btn mw-btn--primary"
        :to="{ name: 'login', query: { redirect: route.fullPath } }"
      >
        去登录
      </RouterLink>
    </section>

    <template v-else>
      <div v-if="loading" class="loading">
        <span class="mw-skeleton loading__title" />
        <span class="mw-skeleton loading__body" />
      </div>

      <div v-else-if="error" class="mw-state">
        <PhArrowClockwise :size="26" aria-hidden="true" />
        <p class="mw-state__title">帖子没能读到</p>
        <p>{{ error }}</p>
        <button class="mw-btn mw-btn--ghost" type="button" @click="loadPost">重试</button>
      </div>

      <template v-else-if="post">
        <article class="detail mw-card">
          <p class="crumb">
            <RouterLink :to="{ name: 'community' }">社区</RouterLink>
            <span v-if="post.sectionId">
              发在 {{ sectionName(sections, post.sectionId) }}
            </span>
          </p>

          <h1 class="mw-h1 detail__title">{{ titleText(post.title) }}</h1>

          <div class="detail__who">
            <span class="avatar" aria-hidden="true">
              {{ initialOf(authorName(post.username, post.uid)) }}
            </span>
            <div class="detail__who-text">
              <!-- 作者名是进用户主页的入口（uid 来自社区 DTO，user 模块查不到 uid） -->
              <RouterLink
                class="detail__author"
                :to="{ name: 'user-profile', params: { uid: post.uid } }"
              >
                {{ authorName(post.username, post.uid) }}
              </RouterLink>
              <p class="detail__time" :title="fullTimeText(post.createTime)">
                {{ relativeTime(post.createTime) }}
              </p>
            </div>
            <p class="detail__views">
              <PhEye :size="14" aria-hidden="true" />
              <span class="mw-num">{{ countText(post.viewCount) }}</span>
              次浏览
            </p>
          </div>

          <p v-if="topics.length" class="detail__topics">
            <span v-for="t in topics" :key="t.topicId" class="detail__topic">#{{ t.name }}</span>
          </p>

          <!-- 正文：详情页**显示全文，不折叠**（列表卡片的"展开"只针对标题，
               因为列表 DTO 根本没有正文；详情是唯一带正文的接口，没有折叠的理由） -->
          <p v-if="post.content" class="detail__content">{{ post.content }}</p>
          <p v-else class="detail__nocontent">这帖没有正文，只有标题。</p>

          <!-- 图片：1 张 = 单张大图（保留长宽比）/ 2 张一行两张 / 3 张一行三张 / 4 张 2×2 /
               5~9 张 3×3 / >9 张只排 9 张 + 第 9 张「+N」；正方形裁切、点开看大图（灯箱）。
               无图时这个组件**整块不输出**，加载失败或尺寸退化的那一张会被收掉 -->
          <PostMediaGrid :images="images" variant="detail" label="帖子配图" />

          <ul v-if="videos.length" class="videos">
            <li v-for="(v, i) in videos" :key="v.objectKey ?? v.url">
              <video :src="v.url" controls preload="metadata" playsinline>
                第 {{ i + 1 }} 个视频
              </video>
            </li>
          </ul>

          <div class="acts">
            <button
              class="act"
              :class="{ 'act--on': liked }"
              type="button"
              :disabled="!!interactBusy"
              :aria-pressed="liked"
              @click="toggleLike"
            >
              <PhHeart :size="16" :weight="liked ? 'fill' : 'regular'" aria-hidden="true" />
              {{ liked ? '已赞' : '点赞' }}
              <span class="act__num mw-num">{{ countText(likeCount) }}</span>
            </button>

            <button
              class="act"
              :class="{ 'act--on': favorited }"
              type="button"
              :disabled="!!interactBusy"
              :aria-pressed="favorited"
              @click="toggleFavorite"
            >
              <PhBookmarkSimple
                :size="16"
                :weight="favorited ? 'fill' : 'regular'"
                aria-hidden="true"
              />
              {{ favorited ? '已收藏' : '收藏' }}
              <span class="act__num mw-num">{{ countText(favoriteCount) }}</span>
            </button>

            <!-- commentCount 把回复也算进去（实测 178：2 一级 + 2 回复 = 4）→ 这里标"含回复" -->
            <span class="act act--static" title="评论总数，含回复">
              <PhChatCircle :size="16" aria-hidden="true" />
              评论
              <span class="act__num mw-num">{{ countText(commentsTotal) }}</span>
            </span>
          </div>

          <p v-if="interactError" class="mw-alert mw-alert--danger acts__error">
            {{ interactError }}
          </p>
        </article>

        <section class="comments">
          <div class="comments__head">
            <h2 class="mw-h2">评论</h2>
            <p class="mw-caption mw-num">共 {{ countText(commentsTotal) }} 条（含回复）</p>
          </div>

          <form class="composer mw-card" @submit.prevent="submitComment('top')">
            <label class="mw-field">
              <span>写评论</span>
              <textarea
                v-model="draft"
                class="mw-input composer__area"
                rows="3"
                placeholder="说点什么。评论最多 1000 字。"
              />
            </label>
            <div class="composer__actions">
              <button
                class="mw-btn mw-btn--primary"
                type="submit"
                :disabled="!!submittingScope || !draft.trim()"
              >
                <PhPaperPlaneRight :size="16" aria-hidden="true" />
                {{ submittingScope === 'top' ? '发表中' : '发表评论' }}
              </button>
            </div>
          </form>

          <p v-if="commentError" class="mw-alert mw-alert--danger feedback">{{ commentError }}</p>
          <p v-else-if="commentNotice" class="mw-alert mw-alert--info feedback">
            {{ commentNotice }}
          </p>

          <div v-if="commentsLoading" class="comments__list">
            <span v-for="i in 3" :key="i" class="mw-skeleton comment-skeleton" />
          </div>

          <div v-else-if="commentsError" class="mw-state">
            <PhArrowClockwise :size="26" aria-hidden="true" />
            <p class="mw-state__title">评论没能读到</p>
            <p>{{ commentsError }}</p>
            <button class="mw-btn mw-btn--ghost" type="button" @click="loadComments(false)">
              重试
            </button>
          </div>

          <div v-else-if="!comments.length" class="mw-state">
            <PhChatTeardropText :size="26" aria-hidden="true" />
            <p class="mw-state__title">还没有评论</p>
            <p>上面那个框就是入口，来说第一句。</p>
          </div>

          <template v-else>
            <ul class="comments__list">
              <li v-for="c in comments" :key="c.commentId" class="comment">
                <div class="comment__head">
                  <span class="avatar" aria-hidden="true">
                    {{ initialOf(authorName(c.username, c.uid)) }}
                  </span>
                  <div class="comment__who">
                    <RouterLink
                      class="comment__author"
                      :to="{ name: 'user-profile', params: { uid: c.uid } }"
                    >
                      {{ authorName(c.username, c.uid) }}
                    </RouterLink>
                    <p class="comment__time" :title="fullTimeText(c.createTime)">
                      {{ relativeTime(c.createTime) }}
                    </p>
                  </div>
                  <button
                    class="cheart"
                    :class="{ 'cheart--on': likeOf(c).liked }"
                    type="button"
                    :disabled="likeOf(c).busy"
                    :aria-pressed="likeOf(c).liked"
                    :aria-label="likeOf(c).liked ? '取消点赞这条评论' : '点赞这条评论'"
                    @click="toggleCommentLike(c)"
                  >
                    <PhHeart
                      :size="14"
                      :weight="likeOf(c).liked ? 'fill' : 'regular'"
                      aria-hidden="true"
                    />
                    <span class="mw-num">{{ countText(likeOf(c).count) }}</span>
                  </button>
                </div>

                <p class="comment__content">{{ c.content }}</p>

                <p v-if="likeOf(c).error" class="mw-alert mw-alert--danger comment__likeerr">
                  {{ likeOf(c).error }}
                </p>

                <!-- 回复：接口给的一级评论自带正序预览（条数不保证）；想看全部要点一下 -->
                <ul v-if="bucketOf(c.commentId).items.length" class="replies">
                  <li v-for="r in bucketOf(c.commentId).items" :key="r.commentId" class="reply">
                    <p class="reply__meta">
                      <RouterLink
                        class="reply__author"
                        :to="{ name: 'user-profile', params: { uid: r.uid } }"
                      >
                        {{ authorName(r.username, r.uid) }}
                      </RouterLink>
                      <span v-if="r.replyUid" class="reply__to">
                        回复
                        <RouterLink :to="{ name: 'user-profile', params: { uid: r.replyUid } }">
                          {{ authorName(r.replyUsername, r.replyUid) }}
                        </RouterLink>
                      </span>
                      <span class="reply__time">{{ relativeTime(r.createTime) }}</span>
                    </p>
                    <p class="reply__content">{{ r.content }}</p>
                    <div class="reply__acts">
                      <button
                        class="cheart cheart--sm"
                        :class="{ 'cheart--on': likeOf(r).liked }"
                        type="button"
                        :disabled="likeOf(r).busy"
                        :aria-pressed="likeOf(r).liked"
                        :aria-label="likeOf(r).liked ? '取消点赞这条回复' : '点赞这条回复'"
                        @click="toggleCommentLike(r)"
                      >
                        <PhHeart
                          :size="12"
                          :weight="likeOf(r).liked ? 'fill' : 'regular'"
                          aria-hidden="true"
                        />
                        <span class="mw-num">{{ countText(likeOf(r).count) }}</span>
                      </button>
                      <button class="link" type="button" @click="startReply(r)">回复</button>
                    </div>
                    <p v-if="likeOf(r).error" class="mw-alert mw-alert--danger reply__err">
                      {{ likeOf(r).error }}
                    </p>
                  </li>
                </ul>

                <button
                  v-if="bucketOf(c.commentId).hasMore"
                  class="link replies__more"
                  type="button"
                  :disabled="bucketOf(c.commentId).loading"
                  @click="loadReplies(c)"
                >
                  {{
                    bucketOf(c.commentId).loading
                      ? '正在加载'
                      : bucketOf(c.commentId).items.length
                        ? '加载更多回复'
                        : `查看全部 ${c.replyCount ?? 0} 条回复`
                  }}
                </button>

                <p v-if="bucketOf(c.commentId).error" class="mw-alert mw-alert--danger">
                  {{ bucketOf(c.commentId).error }}
                </p>

                <!-- 行内回复框：回复一级评论或它的某条回复都落在这里 -->
                <form
                  v-if="replyTarget && topLevelIdOf(replyTarget) === c.commentId"
                  class="replybox"
                  @submit.prevent="submitComment('reply')"
                >
                  <label class="mw-field">
                    <span>回复 {{ authorName(replyTarget.username, replyTarget.uid) }}</span>
                    <textarea
                      v-model="replyDraft"
                      class="mw-input composer__area"
                      rows="2"
                      placeholder="回复最多 1000 字"
                    />
                  </label>
                  <div class="composer__actions">
                    <button
                      class="mw-btn mw-btn--primary"
                      type="submit"
                      :disabled="!!submittingScope || !replyDraft.trim()"
                    >
                      {{ submittingScope === 'reply' ? '发表中' : '发表回复' }}
                    </button>
                    <button class="mw-btn mw-btn--ghost" type="button" @click="cancelReply">
                      先不回
                    </button>
                  </div>
                </form>

                <button
                  v-else
                  class="link comment__reply"
                  type="button"
                  @click="startReply(c)"
                >
                  回复
                </button>
              </li>
            </ul>

            <!-- ⚠️ **没有"加载更多评论"按钮**（2026-09-21 用户要求"默认显示所有的评论"）：
                 评论在进页面时就自动把后续页拉完了（`loadAllComments`，每页 50 = 后端硬上限）。
                 只有撞到安全上限（20 页 / 1000 条）时才在这里**如实**说明"还有更多"。 -->
            <p v-if="commentsHasMore" class="more__end">
              已加载 {{ comments.length }} 条，服务端还有更多（接口每页上限 50，本次已自动翻了
              {{ COMMENT_FETCH_ALL_MAX_PAGES }} 页）。
            </p>
            <p v-else class="more__end">一级评论都在这儿了</p>
          </template>

          <p class="comments__note">
            一级评论按时间倒序（新的在前）；每条评论下的回复按时间正序（旧的在前，与一级列表相反）。
            头像是昵称首字生成的：社区接口没有头像字段，昵称也拿不到时按用户编号显示。
            帖子图片与视频用服务端返回的直链，点开大图或播放都在本页。
          </p>
        </section>
      </template>
    </template>
  </div>
</template>

<style scoped>
.back {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-1);
  margin-bottom: var(--mw-space-4);
  font-size: var(--mw-font-caption);
  font-weight: 600;
}

.gate {
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
  background: var(--mw-surface);
}
.gate__desc {
  max-width: 52ch;
}

.loading {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-3);
}
.loading__title {
  display: block;
  height: 44px;
  max-width: 420px;
}
.loading__body {
  display: block;
  height: 220px;
}

/* ---- 帖子主体 ---- */
.detail {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-3);
}
.crumb {
  display: flex;
  gap: var(--mw-space-2);
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}
.detail__title {
  max-width: 34ch;
}
.detail__who {
  display: flex;
  align-items: center;
  gap: var(--mw-space-3);
  padding-bottom: var(--mw-space-3);
  border-bottom: 1px solid var(--mw-line);
}
.avatar {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  flex: none;
  border-radius: var(--mw-radius-pill);
  background: rgba(194, 65, 12, 0.1);
  color: var(--mw-brand-ink);
  font-weight: 700;
}
.detail__who-text {
  min-width: 0;
}
.detail__author {
  font-size: var(--mw-font-caption);
  font-weight: 600;
}
.detail__time {
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}
.detail__views {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-1);
  margin-left: auto;
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
  white-space: nowrap;
}

.detail__topics {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mw-space-2);
  font-size: var(--mw-font-caption);
  color: var(--mw-brand-ink);
}

.detail__content {
  max-width: 72ch;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.detail__nocontent {
  color: var(--mw-muted);
  font-size: var(--mw-font-caption);
}

/* 图片区的样式全部搬进 components/community/PostMediaGrid.vue
   （排列档位 / 正方形裁切 / 「+N」/ 灯箱都在那里，列表卡片复用同一份） */
.videos {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-3);
}
.videos video {
  width: 100%;
  max-height: 420px;
  background: var(--mw-night);
  border-radius: var(--mw-radius-input);
}

.acts {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mw-space-2);
  padding-top: var(--mw-space-3);
  border-top: 1px solid var(--mw-line);
}
.act {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-1);
  min-height: 40px;
  padding: 0 var(--mw-space-4);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-pill);
  background: var(--mw-surface);
  color: var(--mw-ink);
  font-family: inherit;
  font-size: var(--mw-font-caption);
  font-weight: 600;
  cursor: pointer;
}
.act:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.act--on {
  border-color: var(--mw-brand-ink);
  background: rgba(194, 65, 12, 0.08);
  color: var(--mw-brand-ink);
}
.act--static {
  cursor: default;
  color: var(--mw-ink-2);
}
.act__num {
  color: inherit;
  opacity: 0.75;
}
.acts__error {
  margin-top: var(--mw-space-2);
}

/* ---- 评论 ---- */
.comments {
  margin-top: var(--mw-space-8);
}
.comments__head {
  display: flex;
  align-items: baseline;
  gap: var(--mw-space-3);
  margin-bottom: var(--mw-space-4);
}
.composer {
  margin-bottom: var(--mw-space-4);
}
/* 全局 .mw-field 只给 <label> 上了说明文字的样式，这里用的是 <span>，补一条 */
.composer .mw-field > span,
.replybox .mw-field > span {
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
}
.composer__area {
  min-height: 84px;
  padding: var(--mw-space-3) var(--mw-space-4);
  line-height: var(--mw-line-height);
  resize: vertical;
}
.composer__actions {
  display: flex;
  gap: var(--mw-space-2);
  margin-top: var(--mw-space-3);
}
.feedback {
  margin-bottom: var(--mw-space-4);
}

.comments__list {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-3);
}
.comment-skeleton {
  display: block;
  height: 96px;
}
.comment {
  padding: var(--mw-space-4);
  background: var(--mw-surface);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
}
.comment__head {
  display: flex;
  align-items: center;
  gap: var(--mw-space-2);
}
.comment__who {
  min-width: 0;
}
.comment__author {
  font-size: var(--mw-font-caption);
  font-weight: 600;
}
.comment__time {
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}
/* 评论点赞按钮（.cheart）占用原来"静态计数"那一格，自己带 margin-left: auto */
/* 评论点赞：可点区域，44px 触达（与 .act 的 40px 按钮同一族样式，只把下限抬到 44px） */
.cheart {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--mw-space-1);
  min-height: 44px;
  min-width: 44px;
  margin-left: auto;
  padding: 0 var(--mw-space-2);
  border: 1px solid transparent;
  border-radius: var(--mw-radius-pill);
  background: none;
  color: var(--mw-muted);
  font-family: inherit;
  font-size: var(--mw-font-micro);
  font-weight: 600;
  cursor: pointer;
}
.cheart:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.cheart--on {
  color: var(--mw-brand-ink);
}
.cheart--sm {
  min-height: 44px;
  min-width: 44px;
  margin-left: 0;
  padding: 0 var(--mw-space-1);
}
.comment__likeerr {
  margin-top: var(--mw-space-2);
}
.comment__content {
  margin-top: var(--mw-space-2);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

/* 回复比一级评论退一级：左侧细线 + 浅底 */
.replies {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-3);
  margin-top: var(--mw-space-3);
  padding-left: var(--mw-space-4);
  border-left: 2px solid var(--mw-line);
}
.reply__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--mw-space-2);
  font-size: var(--mw-font-micro);
}
.reply__author {
  font-weight: 600;
  color: var(--mw-ink);
}
.reply__to {
  color: var(--mw-brand-ink);
}
.reply__time {
  color: var(--mw-muted);
}
.reply__content {
  margin-top: var(--mw-space-1);
  font-size: var(--mw-font-caption);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
/* 回复行：点赞按钮 + 回复链接同一行，触达下限都是 44px */
.reply__acts {
  display: flex;
  align-items: center;
  gap: var(--mw-space-2);
  margin-top: var(--mw-space-1);
}
.reply__err {
  margin-top: var(--mw-space-1);
}
.replies__more {
  margin-top: var(--mw-space-2);
  font-size: var(--mw-font-caption);
}
.comment__reply,
.comment__reply.link {
  margin-top: var(--mw-space-2);
  font-size: var(--mw-font-micro);
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
.link:disabled {
  color: var(--mw-muted);
  cursor: not-allowed;
}
.replybox {
  margin-top: var(--mw-space-3);
  padding-top: var(--mw-space-3);
  border-top: 1px solid var(--mw-line);
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
.comments__note {
  margin-top: var(--mw-space-6);
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}

/* 作者名现在是"点进用户主页"的链接：颜色跟随原来的文字，悬停才加下划线，别整页变蓝 */
.detail__author,
.comment__author,
.reply__author,
.reply__to a {
  color: inherit;
  text-decoration: none;
}
.detail__author:hover,
.comment__author:hover,
.reply__author:hover,
.reply__to a:hover {
  color: var(--mw-brand-ink);
  text-decoration: underline;
}
</style>