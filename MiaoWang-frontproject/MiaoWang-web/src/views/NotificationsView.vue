<script setup lang="ts">
/**
 * 消息通知（Web）
 *
 * 页面形态：**一行标题 + 未读概览 + 全部已读** ＋ **一个筛选（全部 / 只看未读）** ＋
 * **游标列表**（未读高亮、触底加载、四态齐全）。数据与状态机全在
 * `composables/useNotifications.ts`，这里只做渲染。
 *
 * 几条必须记住的边界（都来自 `.dsh-tmp/reminder-notification-contract.md` §2 的实测）：
 * - **不做按 type 分 Tab**：列表接口支持 `?type=`，但未读数**只有总数、没有分组计数**
 *   → 每个 Tab 各自红点做不了。页面唯一的筛选是「只看未读」（走实测过的 `unreadOnly=true`）。
 * - **空态说两句话**：没有通知 ≠ 筛选后为空（后者要告诉用户"关掉筛选还在"）。
 * - **点一下 ≠ 只是跳转**：点开即标已读（`readFlag=1` 的条目不再有未读样式），
 *   乐观更新 + 失败回滚都在 composable 里；`B000317` 静默回滚不弹错。
 * - **评论类通知只能跳到帖子**：没有"按 commentId 查评论"的接口，页面**不承诺**定位到那条评论。
 * - **关注类**（`targetId`/`postId` 都是 `0`）跳 `/user/:actorUid`，**不拿 0 去拼 URL**。
 * - **降级**：取消赞 / 取关 / 删帖都不会撤回通知，所以点进去可能已经不存在了 ——
 *   目标页（帖子详情 / 用户主页）本来就有错误态与返回入口，这里不重复造，只在页脚说明。
 * - **未登录走软门槛**（页内提示 + 去登录），沿用帖子详情与用户主页的约定，不新造登录弹窗。
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { PhChatCircleDots, PhSignIn } from '@phosphor-icons/vue'
import {
  initialOf,
  notificationActorName,
  notificationEmptyText,
  notificationExcerpt,
  notificationTarget,
  notificationText,
  notificationTimeText,
  notificationUnread,
  type NotificationItem,
} from '@/api/community'
import { useNotifications } from '@/composables/useNotifications'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const loggedIn = computed(() => authStore.isLoggedIn)

const {
  items,
  loading,
  loadingMore,
  error,
  moreError,
  hasMore,
  isEmpty,
  unauthorized,
  unreadCount,
  unreadLoading,
  unreadError,
  filter,
  filtered,
  setFilter,
  actionError,
  allBusy,
  isMarking,
  markRead,
  markAll,
  load,
  loadMore,
  refreshUnread,
} = useNotifications()

/** 未登录软门槛：页内就地说清楚 + 一个去登录的入口（不弹窗、不硬跳） */
const needLogin = computed(() => !loggedIn.value || unauthorized.value)

const empty = computed(() => notificationEmptyText(filtered.value))

const unreadLine = computed(() => {
  if (unreadError.value) return '未读数暂时读不到，红点可能不是最新的'
  if (unreadLoading.value) return '正在读取未读数…'
  return unreadCount.value > 0 ? `有 ${unreadCount.value} 条未读` : '没有未读通知'
})

/**
 * 渲染用的行（把纯函数的结果一次算好，模板里不再重复调用）。
 * `openable` 与跳转用的是**同一个** `notificationTarget()`，不会出现"看着能点、点了没反应"。
 */
const rows = computed(() =>
  items.value.map((n) => ({
    id: n.notificationId,
    text: notificationText(n),
    excerpt: notificationExcerpt(n),
    time: notificationTimeText(n.createTime),
    initial: initialOf(notificationActorName(n)),
    unread: notificationUnread(n.readFlag),
    openable: notificationTarget(n) !== null,
  })),
)

/** 点开：先乐观标已读（失败静默回滚），再按 `name + params` 跳转 */
function open(notificationId: number) {
  const item = items.value.find((n) => n.notificationId === notificationId)
  if (!item) return
  const target = notificationTarget(item)
  if (!target) return
  void markRead(notificationId)
  void router.push(target)
}

/** 筛选：清空列表从第一页重来（游标跨筛选复用会漏数据） */
function pickFilter(next: 'all' | 'unread') {
  void setFilter(next)
}

/* ---------- 触底加载（游标）：IntersectionObserver，不装 scroll 监听 ---------- */

const sentinel = ref<HTMLElement | null>(null)
let io: IntersectionObserver | null = null

function observe() {
  if (typeof IntersectionObserver === 'undefined') return
  io?.disconnect()
  if (!sentinel.value) return
  io = new IntersectionObserver(
    (entries) => {
      if (entries.some((e) => e.isIntersecting)) void fillMore()
    },
    { rootMargin: '240px 0px' },
  )
  io.observe(sentinel.value)
}

/**
 * 触底补一页。
 *
 * 补完**重新 observe**：`observe()` 会立刻回报当前相交状态，视口很高时能继续把下一页填上，
 * 否则会出现"哨兵从没离开过视口 → 只加载了一页就不再动了"的经典无限滚动 bug。
 * 只在"真的拿到了新记录且还有下一页"时才重新 observe，避免 hasMore 为 false 时回调自激。
 */
async function fillMore() {
  const before = items.value.length
  await loadMore()
  if (items.value.length > before && hasMore.value) observe()
}

onMounted(() => {
  if (needLogin.value) return
  void load()
  observe()
})

watch(sentinel, observe)
onUnmounted(() => io?.disconnect())
</script>

<template>
  <div class="mw-container mw-page">
    <RouterLink class="back" to="/mine">返回我的</RouterLink>

    <!-- 未登录软门槛（令牌过期后进这一页也走这里，不白屏） -->
    <section v-if="needLogin" class="mw-state gate">
      <PhSignIn :size="26" aria-hidden="true" />
      <p class="mw-state__title">登录后才能看消息通知</p>
      <p>通知接口经网关要求登录，未登录时一条都拿不到。</p>
      <RouterLink class="mw-btn mw-btn--primary" to="/login">去登录</RouterLink>
    </section>

    <template v-else>
      <header class="head">
        <div class="head__main">
          <h1 class="mw-h1">消息通知</h1>
          <p class="mw-caption head__sub">
            <PhChatCircleDots :size="15" aria-hidden="true" />
            <span>{{ unreadLine }}</span>
            <button
              v-if="unreadError"
              class="mw-link"
              type="button"
              @click="refreshUnread()"
            >
              重新读取
            </button>
          </p>
        </div>
        <button
          class="mw-btn mw-btn--ghost head__all"
          type="button"
          :disabled="allBusy || loading || unreadCount === 0"
          @click="markAll()"
        >
          {{ allBusy ? '处理中…' : '全部标为已读' }}
        </button>
      </header>

      <div class="mw-chips filter" role="group" aria-label="通知筛选">
        <button
          class="mw-chip"
          :class="{ 'mw-chip--on': filter === 'all' }"
          type="button"
          :disabled="loading || loadingMore"
          :aria-pressed="filter === 'all'"
          @click="pickFilter('all')"
        >
          全部
        </button>
        <button
          class="mw-chip"
          :class="{ 'mw-chip--on': filter === 'unread' }"
          type="button"
          :disabled="loading || loadingMore"
          :aria-pressed="filter === 'unread'"
          @click="pickFilter('unread')"
        >
          只看未读
        </button>
      </div>

      <p v-if="actionError" class="mw-alert mw-alert--danger act" role="alert">
        {{ actionError }}
      </p>

      <!-- 一态：加载中 -->
      <div v-if="loading" class="skeletons" aria-hidden="true">
        <div v-for="i in 4" :key="i" class="mw-skeleton skel" />
      </div>

      <!-- 二态：错误（可重试） -->
      <section v-else-if="error" class="mw-state mw-state--box">
        <p class="mw-state__title">通知没能读到</p>
        <p>{{ error }}</p>
        <button class="mw-btn mw-btn--ghost" type="button" @click="load()">重试</button>
      </section>

      <!-- 三态：空（"没有通知"与"筛选后为空"是两句话） -->
      <section v-else-if="isEmpty" class="mw-state mw-state--box">
        <p class="mw-state__title">{{ empty.title }}</p>
        <p>{{ empty.desc }}</p>
        <button
          v-if="filtered"
          class="mw-btn mw-btn--ghost"
          type="button"
          @click="pickFilter('all')"
        >
          看全部通知
        </button>
      </section>

      <!-- 四态：列表 -->
      <ul v-else class="nlist">
        <li
          v-for="r in rows"
          :key="r.id"
          class="nrow"
          :class="{ 'nrow--unread': r.unread, 'nrow--busy': isMarking(r.id) }"
        >
          <span class="nrow__dot" aria-hidden="true" />
          <span class="nrow__avatar" aria-hidden="true">{{ r.initial }}</span>
          <span class="nrow__main">
            <span class="nrow__text">{{ r.text }}</span>
            <span v-if="r.excerpt" class="nrow__excerpt">{{ r.excerpt }}</span>
          </span>
          <span class="nrow__time mw-num">{{ r.time }}</span>
          <button
            class="nrow__hit"
            type="button"
            :disabled="!r.openable"
            :aria-label="r.openable ? `${r.text}，打开查看` : r.text"
            @click="open(r.id)"
          />
        </li>
      </ul>

      <!-- 触底哨兵：始终在非门槛分支里，靠 IntersectionObserver 触发续页 -->
      <div ref="sentinel" class="sentinel" aria-hidden="true" />

      <!-- 续页失败：只在这一行报错，**上面已经读到的通知一条都不丢** -->
      <p v-if="moreError" class="mw-alert mw-alert--danger more" role="alert">
        {{ moreError }}
        <button class="mw-link" type="button" @click="loadMore()">重试</button>
      </p>
      <p v-else-if="loadingMore" class="mw-caption more">正在加载更早的通知…</p>
      <p v-else-if="rows.length && !hasMore" class="mw-caption more">没有更多了</p>
    </template>

    <p class="note">
      点一条通知会把它标为已读并跳到对应页面：点赞/评论/@提及 跳帖子，关注跳那位的个人主页。
      评论类通知**只能跳到帖子**——后端没有"按评论 id 查评论"的接口，所以不会假装能定位到某一条评论。
      另外，取消赞 / 取关 / 删帖都**不会撤回通知**，点进去可能已经不见了，那种情况由目标页面给出提示。
      未读数只在进页面和切路由时各拉一次，不做轮询（全仓库没有推送设施）。
    </p>
  </div>
</template>

<style scoped>
.back {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-1);
  margin-bottom: var(--mw-space-4);
  color: var(--mw-brand-ink);
  font-size: var(--mw-font-caption);
  font-weight: 600;
  text-decoration: none;
}
.back:hover {
  text-decoration: underline;
}

.gate {
  border: 1px dashed var(--mw-line);
  border-radius: var(--mw-radius-card);
}

.head {
  display: flex;
  align-items: flex-end;
  gap: var(--mw-space-4);
  flex-wrap: wrap;
}
.head__main {
  flex: 1;
  min-width: 0;
}
.head__sub {
  display: flex;
  align-items: center;
  gap: var(--mw-space-2);
}
.head__all {
  min-height: 40px;
  font-size: var(--mw-font-caption);
}

.filter {
  margin: var(--mw-space-4) 0;
}

.act {
  margin-bottom: var(--mw-space-3);
}

/* 骨架屏：4 行等高块，形状与真实行一致，切换时不跳 */
.skeletons {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-2);
}
.skel {
  height: 68px;
}

.nlist {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-2);
}
/* 行内所有元素排成一行；整行的点击目标是一个覆盖全行的透明按钮（见 .nrow__hit） */
.nrow {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--mw-space-3);
  padding: var(--mw-space-3) var(--mw-space-4);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-input);
  background: var(--mw-surface);
}
/* 未读：左边一个品牌橙圆点 + 一层极浅的品牌橙底（不靠颜色单独表意，圆点是形状差异） */
.nrow--unread {
  background: rgba(194, 65, 12, 0.05);
  border-color: rgba(194, 65, 12, 0.22);
}
.nrow--busy {
  opacity: 0.62;
}
.nrow__dot {
  width: 8px;
  height: 8px;
  flex: none;
  border-radius: var(--mw-radius-pill);
  background: transparent;
}
.nrow--unread .nrow__dot {
  background: var(--mw-brand-ink);
}
.nrow__avatar {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  flex: none;
  border-radius: var(--mw-radius-pill);
  background: rgba(255, 138, 91, 0.16);
  color: var(--mw-brand-ink);
  font-weight: 700;
}
.nrow__main {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}
.nrow__text {
  font-weight: 600;
  font-size: var(--mw-font-caption);
}
.nrow__excerpt {
  font-size: var(--mw-font-micro);
  color: var(--mw-ink-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.nrow__time {
  flex: none;
  align-self: flex-start;
  color: var(--mw-muted);
  font-size: var(--mw-font-micro);
}
/* 覆盖整行的点击目标：真实 <button>（可键盘操作），位置压在内容之上 */
.nrow__hit {
  position: absolute;
  inset: 0;
  z-index: 1;
  border: none;
  border-radius: inherit;
  background: transparent;
  cursor: pointer;
}
.nrow__hit:disabled {
  cursor: default;
}

.sentinel {
  height: 1px;
}

.more {
  margin-top: var(--mw-space-4);
  text-align: center;
}

.note {
  margin-top: var(--mw-space-8);
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
  line-height: var(--mw-line-height);
}
</style>