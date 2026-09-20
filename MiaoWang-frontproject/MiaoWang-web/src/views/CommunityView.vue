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
 * 3) 列表 DTO **没有正文**，卡片只有标题、封面与计数；正文在详情页；社区**没有头像字段**，
 *    头像位置用昵称首字顶，昵称拿不到就按 uid 兜底。
 * 4) 同一个列表 DTO 里 **`liked` / `favorited` 是布尔、`isTop` 是数字 `0`/`1`**（实测），
 *    置顶标记只能从列表拿（详情 DTO 没有 `isTop`），判断统一走 `isPinned()`。
 */
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  PhArrowClockwise,
  PhBookmarkSimple,
  PhChatCircle,
  PhEye,
  PhFire,
  PhHash,
  PhHeart,
  PhPaperPlaneRight,
  PhPencilSimple,
  PhSignIn,
  PhVideoCamera,
} from '@phosphor-icons/vue'
import { ApiError } from '@/api/request'
import {
  POST_PAGE_SIZE,
  authorName,
  communityErrorMessage,
  countText,
  createPost,
  initialOf,
  isPinned,
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

/* ---------- 读数据 ---------- */

/** 未登录是"要登录"，其他错误才当错误显示，两者别混成一个红框 */
function noteError(e: unknown): string {
  if (e instanceof ApiError && e.isUnauthorized) {
    unauthorized.value = true
    return '登录状态失效了，请重新登录'
  }
  return communityErrorMessage(e)
}

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
  else feedLoading.value = true
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

onMounted(() => {
  // 未登录就别发请求了：社区每个接口经网关都要 token + username，匿名必然被拦
  if (!loggedIn.value) return
  void Promise.all([loadSections(), loadTopics(), fetchFeed(false)])
})
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

    <div v-else class="layout">
      <div class="main">
        <!-- 发帖面板：契约已按实测口径实现，本端还没有真调过 -->
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
          <button
            v-for="s in sections"
            :key="s.sectionId"
            class="chip"
            :class="{ 'chip--on': mode === 'flow' && activeSectionId === s.sectionId }"
            type="button"
            :title="s.description ?? ''"
            @click="selectSection(s.sectionId)"
          >
            {{ s.name }}
          </button>
          <span v-if="sectionsLoading" class="chip chip--ghost">版块读取中</span>
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
          <button class="link" type="button" @click="resetToFlow">回到全部帖子</button>
        </div>
        <p v-else-if="!feedLoading" class="feedbar__sub feedbar__sub--plain">{{ countLabel }}</p>

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
          <ul class="feed">
            <li v-for="p in posts" :key="p.postId">
              <RouterLink
                class="card"
                :to="{ name: 'community-post', params: { postId: p.postId } }"
              >
                <div class="card__cover">
                  <img v-if="p.coverUrl" :src="p.coverUrl" alt="" loading="lazy" />
                  <span v-else class="card__cover-empty" aria-hidden="true">
                    {{ initialOf(titleText(p.title)) }}
                  </span>
                  <!-- isTop 实测是数字 0/1 → 走 isPinned() 按真值判断，不做 === true / === 1 -->
                  <span v-if="isPinned(p)" class="card__float card__float--top">置顶</span>
                  <span v-if="mediaList(p.videos).length" class="card__float card__float--video">
                    <PhVideoCamera :size="12" aria-hidden="true" />
                    {{ mediaList(p.videos).length }}
                  </span>
                </div>

                <div class="card__body">
                  <h2 class="card__title">{{ titleText(p.title) }}</h2>

                  <p v-if="topicList(p.topics).length" class="card__topics">
                    <span v-for="t in topicList(p.topics)" :key="t.topicId" class="card__topic">
                      #{{ t.name }}
                    </span>
                  </p>

                  <div class="card__who">
                    <span class="avatar" aria-hidden="true">
                      {{ initialOf(authorName(p.username, p.uid)) }}
                    </span>
                    <span class="card__author">{{ authorName(p.username, p.uid) }}</span>
                    <span class="card__time">{{ relativeTime(p.createTime) }}</span>
                  </div>

                  <ul class="card__stats">
                    <li>
                      <PhEye :size="14" aria-hidden="true" />
                      <span class="mw-num">{{ countText(p.viewCount) }}</span>
                    </li>
                    <li :class="{ 'stat--on': p.liked }">
                      <PhHeart :size="14" :weight="p.liked ? 'fill' : 'regular'" aria-hidden="true" />
                      <span class="mw-num">{{ countText(p.likeCount) }}</span>
                    </li>
                    <li :class="{ 'stat--on': p.favorited }">
                      <PhBookmarkSimple
                        :size="14"
                        :weight="p.favorited ? 'fill' : 'regular'"
                        aria-hidden="true"
                      />
                      <span class="mw-num">{{ countText(p.favoriteCount) }}</span>
                    </li>
                    <li title="评论总数，含回复">
                      <PhChatCircle :size="14" aria-hidden="true" />
                      <span class="mw-num">{{ countText(p.commentCount) }}</span>
                    </li>
                  </ul>
                </div>
              </RouterLink>
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
              <button
                class="topic"
                :class="{ 'topic--on': mode === 'topic' && activeTopic?.topicId === t.topicId }"
                type="button"
                @click="selectTopic(t)"
              >
                <span class="topic__name">
                  <PhHash :size="12" aria-hidden="true" />
                  {{ t.name }}
                </span>
                <span class="topic__count mw-num">{{ countText(t.postCount) }} 篇</span>
              </button>
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
.feed {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(236px, 1fr));
  gap: var(--mw-space-4);
}
.card-skeleton {
  display: block;
  height: 268px;
}
.card {
  display: flex;
  flex-direction: column;
  height: 100%;
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
.card__cover {
  position: relative;
  display: grid;
  place-items: center;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background: var(--mw-canvas);
  border-bottom: 1px solid var(--mw-line);
}
.card__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.card__cover-empty {
  font-size: 30px;
  font-weight: 700;
  color: var(--mw-muted);
}
/* 封面上的浮动标签：深色蒙层 + 深色区文字色（与设计令牌的角色分工一致） */
.card__float {
  position: absolute;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px var(--mw-space-2);
  border-radius: var(--mw-radius-tag);
  background: rgba(11, 18, 24, 0.74);
  color: var(--mw-on-night);
  font-size: var(--mw-font-micro);
  font-weight: 600;
}
.card__float--top {
  top: var(--mw-space-2);
  left: var(--mw-space-2);
}
.card__float--video {
  bottom: var(--mw-space-2);
  right: var(--mw-space-2);
}
.card__body {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-2);
  flex: 1;
  padding: var(--mw-space-4);
}
.card__title {
  font-size: var(--mw-font-body);
  font-weight: 600;
  line-height: 1.4;
  letter-spacing: -0.01em;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card__topics {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mw-space-2);
  font-size: var(--mw-font-micro);
  color: var(--mw-brand-ink);
}
.card__who {
  display: flex;
  align-items: center;
  gap: var(--mw-space-2);
  margin-top: auto;
  padding-top: var(--mw-space-2);
}
.avatar {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  flex: none;
  border-radius: var(--mw-radius-pill);
  background: rgba(10, 125, 109, 0.1);
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
  gap: var(--mw-space-4);
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
.stat--on {
  color: var(--mw-brand-ink);
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
  gap: var(--mw-space-4);
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
  background: rgba(10, 125, 109, 0.08);
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
</style>