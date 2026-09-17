<script setup lang="ts">
/**
 * 我的（接真实登录态）
 *
 * 未登录不再假装有资料 —— 直接给登录/注册入口，这是"四态"里的空态。
 */
import { computed, onMounted, ref } from 'vue'
import { PhBell, PhChatCircleDots, PhInfo, PhUserCircle } from '@phosphor-icons/vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const busy = ref(false)
/** 退出用按钮内二次确认，不用浏览器原生 confirm（丑且打断） */
const confirming = ref(false)

const rows = [
  { key: 'profile', label: '个人资料', desc: '昵称、手机号、头像', icon: PhUserCircle },
  { key: 'remind', label: '提醒设置', desc: '推送时间与免打扰', icon: PhBell },
  { key: 'notify', label: '消息通知', desc: '评论、点赞、关注', icon: PhChatCircleDots },
  { key: 'about', label: '关于喵汪', desc: '版本与开源许可', icon: PhInfo },
]

const maskedPhone = computed(() => {
  const p = auth.info?.phone
  if (!p || p.length < 7) return p ?? ''
  return `${p.slice(0, 3)}****${p.slice(-4)}`
})

async function onLogout() {
  if (!confirming.value) {
    confirming.value = true
    return
  }
  busy.value = true
  try {
    await auth.logout()
  } finally {
    busy.value = false
    confirming.value = false
  }
}

onMounted(() => {
  // 直接进本页（没走过首页）时也要把资料补齐
  void auth.restore()
})
</script>

<template>
  <div class="mw-container mw-page">
    <!-- 已登录：深色资料卡，和首页舞台同族 -->
    <section v-if="auth.isLoggedIn" class="profile mw-night">
      <span class="profile__avatar" aria-hidden="true">
        <img v-if="auth.info?.avatar" :src="auth.info.avatar" alt="" />
        <template v-else>{{ auth.initial }}</template>
      </span>
      <div class="profile__main">
        <h1 class="profile__name">{{ auth.displayName }}</h1>
        <p class="profile__sub">
          <span v-if="maskedPhone" class="mw-num">{{ maskedPhone }}</span>
          <span v-if="auth.info?.ownerCallName"> 宠物叫你「{{ auth.info.ownerCallName }}」 </span>
          <span v-else>还没告诉宠物该怎么称呼你</span>
        </p>
      </div>
    </section>

    <!-- 未登录：空态，给出下一步 -->
    <section v-else class="mw-card guest">
      <h1 class="guest__title">还没有登录</h1>
      <p class="guest__desc">
        登录后这里会显示你的宠物档案、照护提醒和会员权益。知识库不需要登录也能随便看。
      </p>
      <div class="guest__actions">
        <RouterLink class="mw-btn mw-btn--primary" to="/login">去登录</RouterLink>
        <RouterLink class="mw-btn mw-btn--ghost" to="/register">注册一个</RouterLink>
      </div>
      <p v-if="auth.backendUnavailable" class="mw-alert mw-alert--info guest__alert">
        现在连不上后端业务服务，登录会失败。这不是前端的问题：服务启动并注册到 Nacos 之后就能用了。
      </p>
    </section>

    <div class="cols">
      <div class="panel">
        <h2 class="panel__title">账号与偏好</h2>
        <ul class="rows">
          <li v-for="r in rows" :key="r.key" class="row">
            <span class="row__icon" aria-hidden="true">
              <component :is="r.icon" :size="18" weight="regular" />
            </span>
            <div>
              <p class="row__label">{{ r.label }}</p>
              <p class="row__desc">{{ r.desc }}</p>
            </div>
            <span class="mw-badge row__badge">开发中</span>
          </li>
        </ul>
      </div>

      <div class="side">
        <div class="panel panel--vip">
          <h2 class="panel__title">喵汪会员</h2>
          <p class="panel__desc">解锁无限 AI 对话次数与多只宠物档案。</p>
          <button class="mw-btn mw-btn--primary" type="button">查看权益</button>
        </div>

        <template v-if="auth.isLoggedIn">
          <button
            class="mw-btn mw-btn--ghost logout"
            type="button"
            :disabled="busy"
            @click="onLogout"
          >
            {{ busy ? '退出中…' : confirming ? '确认退出登录' : '退出登录' }}
          </button>
          <button
            v-if="confirming"
            class="cancel"
            type="button"
            :disabled="busy"
            @click="confirming = false"
          >
            先不退
          </button>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.profile {
  display: flex;
  align-items: center;
  gap: var(--mw-space-4);
  padding: var(--mw-space-6);
  border-radius: var(--mw-radius-stage);
  margin-bottom: var(--mw-space-6);
}
.profile__avatar {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  flex: none;
  overflow: hidden;
  border-radius: var(--mw-radius-pill);
  background: rgba(53, 214, 168, 0.16);
  color: var(--mw-brand);
  font-size: 22px;
  font-weight: 700;
}
.profile__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.profile__main {
  flex: 1;
  min-width: 0;
}
.profile__name {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.01em;
}
.profile__sub {
  display: flex;
  gap: var(--mw-space-4);
  color: var(--mw-on-night-2);
  font-size: var(--mw-font-caption);
}

.guest {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-3);
  margin-bottom: var(--mw-space-6);
}
.guest__title {
  font-size: var(--mw-font-h1);
  font-weight: 700;
  letter-spacing: -0.02em;
}
.guest__desc {
  max-width: 46ch;
  color: var(--mw-ink-2);
}
.guest__actions {
  display: flex;
  gap: var(--mw-space-3);
}
.guest__alert {
  margin-top: var(--mw-space-2);
}

.cols {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: var(--mw-space-8);
  align-items: start;
}

.panel {
  padding: var(--mw-space-6);
  background: var(--mw-surface);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
}
.panel__title {
  font-size: var(--mw-font-h2);
  font-weight: 600;
  margin-bottom: var(--mw-space-3);
}
.panel__desc {
  color: var(--mw-ink-2);
  font-size: var(--mw-font-caption);
  margin-bottom: var(--mw-space-4);
}

.rows {
  display: flex;
  flex-direction: column;
}
.row {
  display: flex;
  align-items: center;
  gap: var(--mw-space-3);
  min-height: 56px;
  padding: var(--mw-space-2) 0;
  border-bottom: 1px solid var(--mw-line);
}
.row:last-child {
  border-bottom: none;
}
.row__icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex: none;
  border-radius: var(--mw-radius-tag);
  background: rgba(10, 125, 109, 0.08);
  color: var(--mw-brand-ink);
}
.row__label {
  font-weight: 600;
  font-size: var(--mw-font-caption);
}
.row__desc {
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}
.row__badge {
  margin-left: auto;
  background: var(--mw-canvas);
  color: var(--mw-muted);
}

.side {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-3);
}
.panel--vip {
  background:
    linear-gradient(0deg, rgba(10, 125, 109, 0.06), rgba(10, 125, 109, 0.06)), var(--mw-surface);
}
.logout {
  width: 100%;
  color: var(--mw-danger);
  border-color: rgba(229, 72, 77, 0.3);
}
.cancel {
  border: none;
  background: transparent;
  font-family: inherit;
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
  cursor: pointer;
}

@media (max-width: 960px) {
  .cols {
    grid-template-columns: minmax(0, 1fr);
  }
  .profile {
    flex-wrap: wrap;
  }
}
</style>
