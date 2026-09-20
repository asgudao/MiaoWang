<script setup lang="ts">
/**
 * 帖子详情（Web）
 *
 * 详情 DTO 是社区里**唯一带正文的**接口：`content` + `images[]` + `videos[]` + `topics[]`。
 * 列表 DTO 没有正文，所以信息流卡片只能显示标题与封面，看全文必须进这一页。
 *
 * 三个实现决定：
 * 1) **点赞 / 收藏选"等接口返回再更新界面"，不做乐观更新**。
 *    理由：`POST|DELETE /like/{postId}` 与 `/favorite/{postId}` 的返回 `data` 是 `null`，
 *    服务端也没有把新计数吐回来，所以计数只能由前端按"我这一下"加减。
 *    这两个调用都是幂等的（后端靠唯一键兜底，2026-09-19 真实账号实测：连发两次一律 `code:"0"`、
 *    计数不重复累加），慢一点重试是安全的，所以选择"请求成功后再改界面"。**本端仍未真调过这两条。**
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
import {
  COMMENT_PAGE_SIZE,
  authorName,
  communityErrorMessage,
  countText,
  createComment,
  favoritePost,
  fullTimeText,
  getPost,
  initialOf,
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

async function loadComments(append: boolean) {
  if (!post.value) return
  if (append) commentsMore.value = true
  else commentsLoading.value = true
  commentsError.value = ''
  try {
    const res = await listComments({
      postId,
      lastId: append ? commentsCursor.value : undefined,
      size: COMMENT_PAGE_SIZE,
    })
    const records = Array.isArray(res?.records) ? res.records : []
    if (append) {
      comments.value = [...comments.value, ...records]
    } else {
      comments.value = records
      // 重新拉第一页时把回复状态一起重建，避免旧回复挂在新列表上
      replies.value = {}
    }
    for (const c of comments.value) ensureBucket(c)
    commentsCursor.value = res?.nextCursor ?? null
    commentsHasMore.value = Boolean(res?.hasMore) && commentsCursor.value !== null
  } catch (e) {
    commentsError.value = noteError(e)
    if (append) commentsHasMore.value = false
  } finally {
    commentsLoading.value = false
    commentsMore.value = false
  }
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
              <p class="detail__author">{{ authorName(post.username, post.uid) }}</p>
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

          <p v-if="post.content" class="detail__content">{{ post.content }}</p>
          <p v-else class="detail__nocontent">这帖没有正文，只有标题。</p>

          <ul v-if="images.length" class="media" :class="{ 'media--grid': images.length > 1 }">
            <li v-for="(img, i) in images" :key="img.objectKey ?? img.url">
              <img :src="img.url" :alt="`第 ${i + 1} 张配图`" loading="lazy" />
            </li>
          </ul>

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
                    <p class="comment__author">{{ authorName(c.username, c.uid) }}</p>
                    <p class="comment__time" :title="fullTimeText(c.createTime)">
                      {{ relativeTime(c.createTime) }}
                    </p>
                  </div>
                  <span class="comment__likes">
                    <PhHeart :size="12" aria-hidden="true" />
                    <span class="mw-num">{{ countText(c.likeCount) }}</span>
                  </span>
                </div>

                <p class="comment__content">{{ c.content }}</p>

                <!-- 回复：接口给的一级评论自带正序预览（条数不保证）；想看全部要点一下 -->
                <ul v-if="bucketOf(c.commentId).items.length" class="replies">
                  <li v-for="r in bucketOf(c.commentId).items" :key="r.commentId" class="reply">
                    <p class="reply__meta">
                      <span class="reply__author">{{ authorName(r.username, r.uid) }}</span>
                      <span v-if="r.replyUid" class="reply__to">
                        回复 {{ authorName(r.replyUsername, r.replyUid) }}
                      </span>
                      <span class="reply__time">{{ relativeTime(r.createTime) }}</span>
                    </p>
                    <p class="reply__content">{{ r.content }}</p>
                    <button class="link" type="button" @click="startReply(r)">回复</button>
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

            <div class="more">
              <button
                v-if="commentsHasMore"
                class="mw-btn mw-btn--ghost"
                type="button"
                :disabled="commentsMore"
                @click="loadComments(true)"
              >
                {{ commentsMore ? '正在加载' : '加载更多评论' }}
              </button>
              <p v-else class="more__end">一级评论都在这儿了</p>
            </div>
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
  background: rgba(10, 125, 109, 0.1);
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

.media {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-2);
}
.media--grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
}
.media img {
  width: 100%;
  max-height: 420px;
  object-fit: cover;
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-input);
}
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
  background: rgba(10, 125, 109, 0.08);
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
.comment__likes {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-1);
  margin-left: auto;
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
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
</style>