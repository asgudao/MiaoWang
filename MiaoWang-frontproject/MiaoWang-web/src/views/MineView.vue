<script setup lang="ts">
/**
 * 我的（接真实登录态）
 *
 * 未登录不再假装有资料 —— 直接给登录/注册入口，这是"四态"里的空态。
 */
import { computed, onMounted, ref, type Component } from 'vue'
import { PhBell, PhCaretRight, PhChatCircleDots, PhInfo, PhUserCircle } from '@phosphor-icons/vue'
import { useAuthStore } from '@/stores/auth'
import { getMyBenefit, limitText, quotaText, type VipBenefit } from '@/api/vip'

const auth = useAuthStore()
const busy = ref(false)
/** 退出用按钮内二次确认，不用浏览器原生 confirm（丑且打断） */
const confirming = ref(false)

/** 会员卡片的数据（真实接口；读不到就显示原因，不假装有档位） */
const vip = ref<VipBenefit | null>(null)
const vipError = ref('')

const expireText = computed(() => {
  const iso = vip.value?.expireTime
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
})

async function loadVip() {
  if (!auth.isLoggedIn) {
    vip.value = null
    return
  }
  vipError.value = ''
  try {
    vip.value = await getMyBenefit()
  } catch (e) {
    vip.value = null
    vipError.value = e instanceof Error ? e.message : '会员权益没能读到'
  }
}

interface MineRow {
  key: string
  label: string
  desc: string
  icon: Component
  /** 有真接口的那一行才给跳转地址（没有就是「开发中」） */
  to?: string
}

/**
 * 账号与偏好的四行。
 *
 * ⚠️ **哪一行能点，唯一判据是有没有 `to`**（模板里按 `v-if="!r.to"` 挂「开发中」徽标），
 * 别在模板里按 key 写死。
 *
 * 现状：**四行都有真页面** ——
 * - `profile` 个人资料 → `/profile`（`GET /user/{username}` + `PUT /user` + `POST /user/avatar`，接口都在）
 * - `remind` 提醒设置 → `/remind/setting`（本机设置真的生效：首页待办裁剪 + 软件内弹窗 +
 *   本机系统通知；口径与手机端同一套，见 `utils/remindSetting.ts` / `utils/remindSchedule.ts`）
 *   ⚠️ 这一行的 `desc` **不许写"推送时间"**：喵汪没有接服务端推送，那些开关只写本机
 *   `localStorage`（见 `views/remind/SettingView.vue` 的诚实声明）。措辞与手机端
 *   `pages/mine/mine.vue` 的同一行逐字一致。离线门禁会因 `desc` 出现"推送"判红。
 * - `notify` 消息通知 → `/notifications`（后端 4 个通知接口）
 * - `about` 关于喵汪 → `/about`（纯静态页：版本、依赖许可、3D 模型授权、后端现状）
 *
 * ⚠️ 四行全接上**不等于**"每行都点得通"：判据仍是 `to` 指向的路由真的注册在
 * `router/index.ts` 里（离线门禁 `tools/check-web-mine-rest.js` 的 D 段逐行钉住）。
 */
const rows: MineRow[] = [
  { key: 'profile', label: '个人资料', desc: '昵称、手机号、头像', icon: PhUserCircle, to: '/profile' },
  { key: 'remind', label: '提醒设置', desc: '提前几天与免打扰', icon: PhBell, to: '/remind/setting' },
  { key: 'notify', label: '消息通知', desc: '评论、点赞、关注', icon: PhChatCircleDots, to: '/notifications' },
  { key: 'about', label: '关于喵汪', desc: '版本与开源许可', icon: PhInfo, to: '/about' },
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
  void loadVip()
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
          <li v-for="r in rows" :key="r.key" class="row" :class="{ 'row--link': r.to }">
            <span class="row__icon" aria-hidden="true">
              <component :is="r.icon" :size="18" weight="regular" />
            </span>
            <div>
              <p class="row__label">{{ r.label }}</p>
              <p class="row__desc">{{ r.desc }}</p>
            </div>
            <!-- 有接口的那一行：整行可点（覆盖式入口），不再挂「开发中」 -->
            <PhCaretRight v-if="r.to" class="row__go" :size="16" aria-hidden="true" />
            <span v-else class="mw-badge row__badge">开发中</span>
            <RouterLink
              v-if="r.to"
              class="row__hit"
              :to="r.to"
              :aria-label="`${r.label}：${r.desc}`"
            />
          </li>
        </ul>
      </div>

      <div class="side">
        <div class="panel panel--vip">
          <h2 class="panel__title">喵汪会员</h2>

          <template v-if="auth.isLoggedIn">
            <p v-if="vipError" class="mw-field__error">{{ vipError }}</p>
            <template v-else-if="vip">
              <p class="vip__state">
                <span class="mw-badge" :class="vip.vip ? 'mw-badge--done' : 'vip__free'">
                  {{ vip.vip ? '会员有效' : '免费档' }}
                </span>
                <span v-if="vip.vip && expireText" class="mw-badge mw-badge--time">
                  到期 {{ expireText }}
                </span>
              </p>
              <p class="panel__desc">
                {{ vip.vip ? vip.planNames.join('、') : '还没开通，现在按免费档给量' }}。AI 问答{{
                  quotaText(vip.ragRemaining)
                }}，宠物上限 {{ limitText(vip.petLimitPerType, '只') }}。
              </p>
            </template>
            <p v-else class="panel__desc">正在读取会员权益。</p>

            <RouterLink class="mw-btn mw-btn--primary vip__cta" :to="{ name: 'vip' }">
              {{ vip && vip.vip ? '查看权益与订单' : '去开通会员' }}
            </RouterLink>
          </template>

          <template v-else>
            <p class="panel__desc">登录后可以看到自己的档位、到期时间与订单。</p>
            <RouterLink
              class="mw-btn mw-btn--ghost vip__cta"
              :to="{ name: 'login', query: { redirect: '/vip' } }"
            >
              登录后查看
            </RouterLink>
          </template>
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
  background: rgba(255, 138, 91, 0.16);
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
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--mw-space-3);
  min-height: 56px;
  padding: var(--mw-space-2) 0;
  border-bottom: 1px solid var(--mw-line);
}
/* 有接口的那一行：鼠标可点，hover 给一点底色，别让用户猜哪儿能点 */
.row--link {
  cursor: pointer;
}
.row--link:hover {
  background: rgba(194, 65, 12, 0.04);
}
/**
 * 覆盖整行的入口：真实 <RouterLink>（`position: absolute` 不参与行内布局，所以
 * 图标/文案/徽标的排布与没有入口的那几行完全一致），可键盘 Tab 到、有 focus 环。
 */
.row__hit {
  position: absolute;
  inset: 0;
  z-index: 1;
  border-radius: var(--mw-radius-input);
}
.row__go {
  margin-left: auto;
  flex: none;
  color: var(--mw-muted);
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
  background: rgba(194, 65, 12, 0.08);
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
    linear-gradient(0deg, rgba(194, 65, 12, 0.06), rgba(194, 65, 12, 0.06)), var(--mw-surface);
}
/* 会员卡片：状态徽标 + 真实权益数值 + 一个明确的下一步 */
.vip__state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--mw-space-2);
  margin-bottom: var(--mw-space-2);
}
.vip__free {
  background: var(--mw-canvas);
  color: var(--mw-ink-2);
}
.vip__cta {
  width: 100%;
  min-height: 40px;
  font-size: var(--mw-font-caption);
}
/* 错误态没有 .panel__desc 撑间距，这里补上，保证按钮与上面文字的距离一致 */
.panel--vip .mw-field__error {
  margin-bottom: var(--mw-space-4);
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
