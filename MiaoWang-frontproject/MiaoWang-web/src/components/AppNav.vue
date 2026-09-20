<script setup lang="ts">
/**
 * 顶部导航。
 *
 * 除了既有的 5 条链接与右侧头像/登录注册，这里多了一个**消息通知铃铛**：
 * 它是「入口红点」式的徽标 —— **只在路由切换时拉一次 `unread-count`**，
 * 不做全站轮询（全仓库没有任何推送设施，轮询等于凭空给后端加 QPS）。
 * 未登录不显示（`/notifications` 挂的是 `requiresAuth`，显示了只会把人推到登录页）。
 * 通知页标记已读后会广播 `NOTIFICATION_READ_EVENT`，这里跟着刷新一次，
 * 这样"页面说全读完了"和"导航上的红点"不会互相打脸（不建 store 的最小对齐手段）。
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { PhBell } from '@phosphor-icons/vue'
import { NOTIFICATION_READ_EVENT, getUnreadCount } from '@/api/community'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const auth = useAuthStore()
const scrolled = ref(false)

const links = [
  { to: '/', label: '首页' },
  { to: '/community', label: '社区' },
  { to: '/pets', label: '萌宠' },
  { to: '/knowledge', label: '知识库' },
  { to: '/ai', label: 'AI 问诊' },
]

/** 只有首页顶部是深色舞台，导航才可以透明 */
const overNight = computed(() => route.name === 'home' && !scrolled.value)

/** 未读总数（只用来决定红点显不显示；**读不到就当作 0**，入口徽标不值得打扰用户） */
const unread = ref(0)

async function refreshUnread() {
  if (!auth.isLoggedIn) {
    unread.value = 0
    return
  }
  try {
    unread.value = await getUnreadCount()
  } catch {
    unread.value = 0
  }
}

const onScroll = () => {
  scrolled.value = window.scrollY > 24
}

// 路由切换就刷新一次（进页面/换页都算），**不轮询**
watch(() => route.fullPath, () => void refreshUnread())

onMounted(() => {
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener(NOTIFICATION_READ_EVENT, refreshUnread)
  void refreshUnread()
})
onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener(NOTIFICATION_READ_EVENT, refreshUnread)
})
</script>

<template>
  <header class="nav" :class="{ 'nav--night': overNight, 'nav--solid': !overNight }">
    <div class="mw-container nav__inner">
      <RouterLink class="brand" to="/">
        <span class="brand__dot" aria-hidden="true" />
        <span class="brand__text">喵汪</span>
      </RouterLink>

      <nav class="nav__links">
        <RouterLink v-for="l in links" :key="l.to" :to="l.to" class="nav__link">
          {{ l.label }}
        </RouterLink>
      </nav>

      <div class="nav__right">
        <!-- 消息通知：入口红点（未登录不显示入口）。图标颜色继承导航，深浅两态都跟着走 -->
        <RouterLink
          v-if="auth.isLoggedIn"
          class="bell"
          to="/notifications"
          :title="unread > 0 ? `消息通知（${unread} 条未读）` : '消息通知'"
          :aria-label="unread > 0 ? `消息通知，${unread} 条未读` : '消息通知'"
        >
          <PhBell :size="20" weight="regular" aria-hidden="true" />
          <span v-if="unread > 0" class="bell__dot" aria-hidden="true" />
        </RouterLink>

        <!-- 已登录：头像 + 名字直接进「我的」 -->
        <RouterLink v-if="auth.isLoggedIn" class="user" to="/mine" :title="auth.username">
          <span class="user__avatar" aria-hidden="true">
            <img v-if="auth.info?.avatar" :src="auth.info.avatar" alt="" />
            <template v-else>{{ auth.initial }}</template>
          </span>
          <span class="user__name">{{ auth.displayName }}</span>
        </RouterLink>

        <!-- 未登录：两个不同意图的入口，不合并 -->
        <template v-else>
          <RouterLink class="mw-btn mw-btn--ghost nav__action" to="/login"> 登录 </RouterLink>
          <RouterLink
            class="nav__action"
            :class="overNight ? 'mw-btn mw-btn--brand' : 'mw-btn mw-btn--primary'"
            to="/register"
          >
            注册
          </RouterLink>
        </template>
      </div>
    </div>
  </header>
</template>

<style scoped>
.nav {
  position: fixed;
  inset: 0 0 auto;
  z-index: 50;
  height: var(--mw-nav-h);
  transition:
    background-color 0.25s ease,
    border-color 0.25s ease;
}
.nav--night {
  background: transparent;
  border-bottom: 1px solid transparent;
  color: var(--mw-on-night);
}
.nav--solid {
  background: rgba(255, 252, 249, 0.88);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--mw-line);
  color: var(--mw-ink);
}

/**
 * 夜幕态（首页未滚动，导航浮在深色舞台上）。
 * 通用类的 .mw-btn--ghost 写的是浅色区的 --mw-ink，在这里就是黑字黑底（1.02:1 → 看不见），
 * 而 main.css 里那条兜底的 `.mw-night .mw-btn--ghost` 对导航不生效（导航根节点是 .nav--night，不是 .mw-night）。
 * 所以在这里按同一套规则补：暖白字 + 夜幕细线 + 暖白 hover。
 */
.nav--night .mw-btn--ghost {
  border-color: var(--mw-night-line);
  color: var(--mw-on-night);
}
.nav--night .mw-btn--ghost:hover:not(:disabled) {
  /* --mw-on-night 的派生半透明（暖白），不用中性白，避免在暖舞台上发灰 */
  background: rgba(247, 240, 232, 0.08);
}
/* 深底上的 focus 环用亮品牌橙（与 main.css 的 `.mw-night :focus-visible` 同一条规则） */
.nav--night :focus-visible {
  outline-color: var(--mw-brand);
}

.nav__inner {
  display: flex;
  align-items: center;
  gap: var(--mw-space-8);
  height: 100%;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-2);
  color: inherit;
  font-size: 19px;
  font-weight: 700;
  letter-spacing: -0.01em;
}
.brand:hover {
  text-decoration: none;
}
.brand__dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--mw-brand);
}

.nav__links {
  display: flex;
  gap: var(--mw-space-6);
  margin-left: var(--mw-space-4);
}

.nav__link {
  color: inherit;
  opacity: 0.72;
  font-size: var(--mw-font-caption);
  font-weight: 600;
  padding: var(--mw-space-1) 0;
  border-bottom: 2px solid transparent;
}
.nav__link:hover {
  opacity: 1;
  text-decoration: none;
}
.nav__link.router-link-exact-active {
  opacity: 1;
  border-bottom-color: var(--mw-brand);
}

.nav__right {
  display: flex;
  align-items: center;
  gap: var(--mw-space-2);
  margin-left: auto;
}
.nav__action {
  min-height: 36px;
  padding: 0 var(--mw-space-4);
  font-size: var(--mw-font-caption);
}

/**
 * 消息通知铃铛 + 入口红点。
 * 颜色一律 `color: inherit`：导航有夜幕态（首页顶部）与浅色磨砂态两种底，
 * 写死颜色会在其中一种底上看不见。红点用既有的 --mw-danger，不新增令牌。
 */
.bell {
  position: relative;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  flex: none;
  border-radius: var(--mw-radius-pill);
  color: inherit;
  opacity: 0.72;
}
.bell:hover {
  opacity: 1;
  background: rgba(138, 119, 103, 0.14);
  text-decoration: none;
}
.bell__dot {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 8px;
  height: 8px;
  border-radius: var(--mw-radius-pill);
  background: var(--mw-danger);
}

.user {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-2);
  color: inherit;
  font-size: var(--mw-font-caption);
  font-weight: 600;
}
.user:hover {
  text-decoration: none;
}
.user__avatar {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  overflow: hidden;
  border-radius: var(--mw-radius-pill);
  background: rgba(255, 138, 91, 0.18);
  color: var(--mw-brand);
  font-weight: 700;
}
.nav--solid .user__avatar {
  /* 浅色区就该用浅色区的主文字色。原 --mw-brand-ink 橙字在磨砂底上只有 4.36:1，
     而首页滚动初期磨砂底仍压在深色舞台上方（合成底 #e4e0dd）时掉到 3.55:1；换 --mw-ink 后
     浅色页 15.13:1、压在深色舞台上方 12.33:1，两种情况都稳。夜幕态仍是亮品牌橙（5.79:1）。 */
  color: var(--mw-ink);
}
.user__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

@media (max-width: 720px) {
  .nav__links {
    display: none;
  }
  .user__name {
    display: none;
  }
}
</style>
