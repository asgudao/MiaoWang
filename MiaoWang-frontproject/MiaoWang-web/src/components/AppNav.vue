<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const auth = useAuthStore()
const scrolled = ref(false)

const links = [
  { to: '/', label: '首页' },
  { to: '/community', label: '社区' },
  { to: '/pets', label: '萌宠' },
  { to: '/knowledge', label: '知识库' },
]

/** 只有首页顶部是深色舞台，导航才可以透明 */
const overNight = computed(() => route.name === 'home' && !scrolled.value)

const onScroll = () => {
  scrolled.value = window.scrollY > 24
}

onMounted(() => {
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
})
onUnmounted(() => window.removeEventListener('scroll', onScroll))
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
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--mw-line);
  color: var(--mw-ink);
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
  background: rgba(53, 214, 168, 0.18);
  color: var(--mw-brand);
  font-weight: 700;
}
.nav--solid .user__avatar {
  color: var(--mw-brand-ink);
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
