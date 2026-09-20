<script setup lang="ts">
/**
 * 个人资料（Web）
 *
 * 入口：「我的 → 个人资料」那一行（`MineView.vue` 的 `profile` 行）。
 * ⚠️ 与 `views/UserProfileView.vue`（路由 `/user/:uid`，看**别人**）是两回事，别混：
 * 这一页是"我自己的资料"，uid 只从社区侧的自查资料拿。
 *
 * 这一页**只做后端真有的能力**，逐条对着源码写（依据在 `api/user.ts` 的追加段与
 * `composables/useProfile.ts` 的注释里）：
 *
 * | 能做什么 | 走哪个接口 |
 * |---|---|
 * | 读自己的资料（手机号已脱敏） | `GET /user/{username}` |
 * | 改显示名称 / 宠物称呼 / 邮箱 | `PUT /user` |
 * | 换头像 | `POST /user/avatar`（multipart，字段名 `file`） |
 * | 读 uid | `GET /community/user/profile`（不带 uid 就是自己） |
 *
 * **做不到、也没假装能做的**（页面上都写清楚了，不是偷偷省略）：
 * - **用户名不能改**（`PUT /user` 要求 body.username 与登录身份一致，否则 `B000206`）
 * - **手机号换绑**要新号的短信验证码，本页不带短信流程 → 只读，且不放死按钮
 * - **改密码**成功后当前 token 立刻失效，属另一个流程 → 本页不放入口
 * - **没有真头像时不画图**：显示显示名首字。**不写"后端没有上传接口"这种假话**（那个接口是存在的）
 *
 * 登录态：全局守卫看到 `requiresAuth` 就跳登录页，这是**第一道**；页内 `needLogin` 软门槛是
 * **第二道**（登录态在建页面里失效 / 网关 `state:-1` 时才走到），**不新造登录弹窗**。
 */
import { computed, onMounted, ref } from 'vue'
import { PhArrowClockwise } from '@phosphor-icons/vue'
import { useAuthStore } from '@/stores/auth'
import { useProfile } from '@/composables/useProfile'
import { AVATAR_ACCEPT } from '@/api/user'

const auth = useAuthStore()

/**
 * 状态全在 composable 里（页面只做渲染）。解构成顶层绑定之后模板里会自动解包 ref，
 * 不用在模板里写一堆 `.value`。
 */
const {
  loaded,
  loading,
  error,
  needLogin: profileNeedLogin,
  canEdit,
  load,
  uid,
  uidError,
  displayName,
  initial,
  avatarUrl,
  avatarText,
  avatarBtnText,
  usernameText,
  phoneShown,
  realNameShown,
  callNameShown,
  mailShown,
  editing,
  form,
  saving,
  saveError,
  note,
  toggleEdit,
  cancelEdit,
  clearSaveError,
  save,
  avatarBusy,
  changeAvatar,
} = useProfile()

/**
 * 页内软门槛 = 全局守卫那一道的补集：
 * 本地没有会话（守卫本来就会拦，这里防"进页面之后掉线"）或后端已否掉会话（`profileNeedLogin`）。
 */
const needLogin = computed(() => !auth.isLoggedIn || profileNeedLogin.value)

const fileInput = ref<HTMLInputElement | null>(null)

/** 原生文件选择框藏在 DOM 里，由这个按钮触发（样式不可控，但键盘与读屏都拿得到） */
function pickAvatar() {
  if (!canEdit.value || avatarBusy.value) return
  fileInput.value?.click()
}

async function onAvatarPicked(e: Event) {
  const el = e.target as HTMLInputElement
  const file = el.files && el.files[0] ? el.files[0] : null
  // 同一个文件连选两次也要能触发，所以先把 input 的值清掉
  el.value = ''
  const ok = await changeAvatar(file)
  if (ok) void auth.fetchMe()
}

async function onSave() {
  const ok = await save()
  // 「我的」页顶部与顶栏读的是 store，不刷新会跟前端显示不一致
  if (ok) await auth.fetchMe()
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="mw-container mw-page">
    <h1 class="mw-h1 title">个人资料</h1>

    <!-- 未登录 / 会话失效：软门槛（本页所有接口都要登录身份，匿名必被网关拦） -->
    <section v-if="needLogin" class="mw-state mw-state--box">
      <p class="mw-state__title">
        {{ auth.isLoggedIn ? '登录已过期' : '先登录再看个人资料' }}
      </p>
      <p class="gate__desc">
        昵称、手机号、邮箱这些只对本人可见，也只能改自己的。登录后再回来。
      </p>
      <RouterLink class="mw-btn mw-btn--primary" to="/login">去登录</RouterLink>
    </section>

    <template v-else>
      <!-- 四态之一：加载骨架（不是"加载中…"纯文字） -->
      <div v-if="loading && !loaded" class="mw-card sk" aria-hidden="true">
        <span class="mw-skeleton sk__hero" />
        <span class="mw-skeleton sk__line" />
        <span class="mw-skeleton sk__line sk__line--short" />
      </div>

      <!-- 四态之二：首屏失败，说清原因 + 重试 -->
      <section v-else-if="error && !loaded" class="mw-state mw-state--box">
        <PhArrowClockwise :size="24" aria-hidden="true" />
        <p class="mw-state__title">资料没读到</p>
        <p>{{ error }}</p>
        <button class="mw-btn mw-btn--ghost" type="button" @click="load()">重试</button>
      </section>

      <template v-else>
        <!-- 写操作的行内反馈：乐观更新失败会回滚，并把后端的话原样显示出来 -->
        <div v-if="saveError" class="mw-alert mw-alert--danger flash">
          <span class="flash__text">{{ saveError }}</span>
          <button class="mw-link" type="button" @click="clearSaveError()">知道了</button>
        </div>
        <p v-else-if="note" class="flash flash--ok">{{ note }}</p>

        <!-- 头像：没有真头像就显示显示名首字，绝不画一个假头像 -->
        <section class="hero mw-night">
          <span class="hero__avatar" aria-hidden="true">
            <img v-if="avatarUrl" :src="avatarUrl" alt="" />
            <template v-else>{{ initial }}</template>
          </span>
          <div class="hero__main">
            <h2 class="hero__name">{{ displayName }}</h2>
            <p class="hero__sub">{{ avatarText }}</p>
            <button
              class="mw-btn mw-btn--brand hero__btn"
              type="button"
              :disabled="avatarBusy"
              @click="pickAvatar"
            >
              {{ avatarBtnText }}
            </button>
            <input
              ref="fileInput"
              class="hero__file"
              type="file"
              :accept="AVATAR_ACCEPT"
              aria-label="选择头像图片"
              @change="onAvatarPicked"
            />
          </div>
        </section>
        <p class="hint">
          换头像走 POST /user/avatar（multipart，字段名 file）。后端只收 jpg / jpeg / png / webp，
          单张不超过 5MB。
        </p>

        <!-- 可改的三项 -->
        <section class="mw-card panel">
          <div class="panel__head">
            <h2 class="mw-h2">基本资料</h2>
            <button class="mw-link" type="button" :disabled="saving" @click="toggleEdit()">
              {{ editing ? '取消' : '编辑' }}
            </button>
          </div>

          <div class="field">
            <span class="field__k">用户名</span>
            <span class="field__v mw-num">{{ usernameText }}</span>
          </div>
          <p class="field__note">
            用户名改不了：资料接口要求请求体里的 username 与登录身份一致，传成别的会报 B000206。
          </p>

          <div class="field">
            <span class="field__k">显示名称</span>
            <input
              v-if="editing"
              v-model="form.realName"
              class="mw-input field__input"
              type="text"
              placeholder="还没填"
              maxlength="32"
            />
            <span v-else class="field__v">{{ realNameShown || '还没填' }}</span>
          </div>

          <div class="field">
            <span class="field__k">宠物怎么称呼你</span>
            <input
              v-if="editing"
              v-model="form.ownerCallName"
              class="mw-input field__input"
              type="text"
              placeholder="还没填"
              maxlength="32"
            />
            <span v-else class="field__v">{{ callNameShown || '还没填' }}</span>
          </div>

          <div class="field">
            <span class="field__k">邮箱</span>
            <input
              v-if="editing"
              v-model="form.mail"
              class="mw-input field__input"
              type="text"
              placeholder="还没填"
            />
            <span v-else class="field__v">{{ mailShown || '还没填' }}</span>
          </div>

          <div v-if="editing" class="actions">
            <button class="mw-btn mw-btn--ghost" type="button" :disabled="saving" @click="cancelEdit()">
              取消
            </button>
            <button class="mw-btn mw-btn--primary" type="button" :disabled="saving" @click="onSave">
              {{ saving ? '保存中…' : '保存' }}
            </button>
          </div>
          <p v-else class="field__note">
            能改的只有这三项：显示名称、宠物怎么称呼你、邮箱。写接口是 PUT /user。
          </p>
        </section>

        <!-- 手机号：只读，且说清为什么只读 -->
        <section class="mw-card panel">
          <h2 class="mw-h2">手机号</h2>
          <div class="field">
            <span class="field__k">手机号</span>
            <span class="field__v mw-num">{{ phoneShown || '还没填' }}</span>
          </div>
          <p class="field__note">
            后端返回的本来就是脱敏号（137****0402 这种），本页不再二次打码，免得把星号又切一遍。
            换绑手机号要有新号的短信验证码（PUT /user/phone，scene 是 change_phone），本页还没接这条流程，
            所以这里只读，也不放一个点了没反应的换绑按钮。
          </p>
        </section>

        <!-- uid：来源单一，说清从哪来 -->
        <section class="mw-card panel">
          <h2 class="mw-h2">账号</h2>
          <div class="field">
            <span class="field__k">uid</span>
            <span class="field__v mw-num">{{ uid === null ? '这次没读到' : uid }}</span>
          </div>
          <p class="field__note">
            uid 取自社区的用户主页接口（GET /community/user/profile，不带 uid 就是自己）。
            它只能从这里来：用户服务没有按 uid 查的接口，GET /user/{username} 里的 id 恒为 null。
            <template v-if="uidError"> {{ uidError }}</template>
          </p>
        </section>

        <p class="foot">改完资料后，「我的」页顶部的显示名也会跟着变。</p>
      </template>
    </template>
  </div>
</template>

<style scoped>
.title {
  margin-bottom: var(--mw-space-6);
}

.gate__desc {
  max-width: 46ch;
}

/* ---- 加载骨架 ---- */
.sk {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-3);
}
.sk__hero,
.sk__line {
  display: block;
}
.sk__hero {
  height: 72px;
}
.sk__line {
  height: 20px;
}
.sk__line--short {
  width: 55%;
}

/* ---- 写操作反馈 ---- */
.flash {
  display: flex;
  align-items: center;
  gap: var(--mw-space-3);
  margin-bottom: var(--mw-space-6);
}
.flash__text {
  flex: 1;
  min-width: 0;
}
.flash--ok {
  color: var(--mw-success);
  font-size: var(--mw-font-caption);
  margin-bottom: var(--mw-space-4);
}

/* ---- 头像块（深色，与首页舞台 / 我的页资料卡同族） ---- */
.hero {
  display: flex;
  align-items: center;
  gap: var(--mw-space-4);
  padding: var(--mw-space-6);
  border-radius: var(--mw-radius-card);
}
.hero__avatar {
  display: grid;
  place-items: center;
  width: 72px;
  height: 72px;
  flex: none;
  overflow: hidden;
  border-radius: var(--mw-radius-pill);
  background: rgba(255, 138, 91, 0.16);
  color: var(--mw-brand);
  font-size: 28px;
  font-weight: 700;
}
.hero__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.hero__main {
  flex: 1;
  min-width: 0;
}
.hero__name {
  font-size: var(--mw-font-h2);
  font-weight: 700;
  letter-spacing: -0.01em;
  overflow-wrap: anywhere;
}
.hero__sub {
  color: var(--mw-on-night-2);
  font-size: var(--mw-font-caption);
}
.hero__btn {
  min-height: 36px;
  margin-top: var(--mw-space-2);
  padding: 0 var(--mw-space-4);
  font-size: var(--mw-font-caption);
}
/**
 * 原生文件选择框不显示，但**留在 DOM 里**（不 display:none，而是视觉隐藏），
 * 这样 `.click()` 触发的选择器仍然可用，读屏也能读到它的 aria-label。
 */
.hero__file {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
  border: 0;
}
.hint {
  margin: var(--mw-space-2) 0 var(--mw-space-6);
  font-size: var(--mw-font-micro);
  line-height: 1.6;
  color: var(--mw-muted);
}

/* ---- 内容卡 ---- */
.panel {
  margin-bottom: var(--mw-space-4);
}
.panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--mw-space-3);
  margin-bottom: var(--mw-space-2);
}

/* ---- 键值行 ---- */
.field {
  display: flex;
  align-items: center;
  gap: var(--mw-space-3);
  min-height: 44px;
  margin-top: var(--mw-space-2);
  border-bottom: 1px solid var(--mw-line);
}
.field__k {
  flex: none;
  width: 128px;
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
}
.field__v {
  flex: 1;
  min-width: 0;
  font-weight: 600;
  overflow-wrap: anywhere;
}
.field__input {
  flex: 1;
  min-width: 0;
}
.field__note {
  margin-top: var(--mw-space-2);
  font-size: var(--mw-font-micro);
  line-height: 1.6;
  color: var(--mw-muted);
}

.actions {
  display: flex;
  gap: var(--mw-space-2);
  margin-top: var(--mw-space-4);
}

.foot {
  margin-top: var(--mw-space-6);
  text-align: center;
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}
</style>