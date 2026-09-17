<script setup lang="ts">
/**
 * 注册
 *
 * 三个坑（见接口契约）：
 * 1) `POST /user` 的 `smsCode` **必填**，且短信码 scene 必须是 `register`（与登录码不通用）
 * 2) 图形验证码**一次性**，每次发短信后都要重新取一张
 * 3) 注册成功后直接拿刚填的账号密码登录，别让用户再输一遍
 * 4) **不做用户名实时查重**：`GET /user/has-username` 需要登录态（网关未白名单，实测匿名必返回 state:-1），
 *    注册时本来就还没登录，所以重复用户名只能等提交后由后端返回 `B000201 用户名已存在`
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as userApi from '@/api/user'
import { authErrorMessage, isCaptchaError } from '@/utils/auth-error'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const form = reactive({
  username: '',
  password: '',
  confirm: '',
  realName: '',
  phone: '',
  ownerCallName: '',
  captchaCode: '',
  smsCode: '',
})
const fieldError = reactive({
  username: '',
  password: '',
  confirm: '',
  realName: '',
  phone: '',
  captchaCode: '',
  smsCode: '',
})

const captcha = ref<userApi.CaptchaResp | null>(null)
const error = ref('')
const countdown = ref(0)
const sending = ref(false)
const submitting = ref(false)

const redirect = computed(() => {
  const r = route.query.redirect
  return (Array.isArray(r) ? r[0] : r) || '/'
})

function friendlyError(e: unknown): string {
  if (isCaptchaError(e)) void refreshCaptcha()
  return authErrorMessage(e)
}

async function refreshCaptcha() {
  try {
    captcha.value = await userApi.getCaptcha()
  } catch (e) {
    error.value = friendlyError(e)
  }
}

function startCountdown() {
  countdown.value = 60
  const timer = window.setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) window.clearInterval(timer)
  }, 1000)
}

function validate(): boolean {
  for (const k of Object.keys(fieldError) as (keyof typeof fieldError)[]) fieldError[k] = ''
  error.value = ''

  if (!/^[A-Za-z0-9_]{3,20}$/.test(form.username.trim())) {
    fieldError.username = '用户名用 3 到 20 位字母、数字或下划线'
  }
  if (form.password.length < 6) fieldError.password = '密码至少 6 位'
  if (form.confirm !== form.password) fieldError.confirm = '两次输入的密码不一致'
  if (!form.realName.trim()) fieldError.realName = '请填写真实姓名'
  if (!/^1[3-9]\d{9}$/.test(form.phone)) fieldError.phone = '请输入 11 位手机号'
  if (!/^\d{4,6}$/.test(form.smsCode.trim())) fieldError.smsCode = '请输入收到的短信验证码'

  return !Object.values(fieldError).some(Boolean)
}

async function onSendSms() {
  fieldError.phone = ''
  fieldError.captchaCode = ''
  error.value = ''
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    fieldError.phone = '请输入 11 位手机号'
    return
  }
  if (!form.captchaCode.trim()) {
    fieldError.captchaCode = '请输入图片验证码'
    return
  }
  if (!captcha.value) return

  sending.value = true
  try {
    await userApi.sendSms({
      phone: form.phone,
      // 注册码与登录码分场景存，这里必须是 register
      scene: 'register',
      captchaKey: captcha.value.captchaKey,
      captchaCode: form.captchaCode.trim(),
    })
    startCountdown()
  } catch (e) {
    error.value = friendlyError(e)
  } finally {
    sending.value = false
    form.captchaCode = ''
    void refreshCaptcha()
  }
}

async function submit() {
  if (!validate()) return
  submitting.value = true
  try {
    await auth.register({
      username: form.username.trim(),
      password: form.password,
      realName: form.realName.trim(),
      phone: form.phone,
      ownerCallName: form.ownerCallName.trim() || undefined,
      smsCode: form.smsCode.trim(),
    })
    await router.replace(redirect.value)
  } catch (e) {
    error.value = friendlyError(e)
  } finally {
    submitting.value = false
  }
}

watch(
  () => form.phone,
  (p) => {
    if (p && !captcha.value) void refreshCaptcha()
  },
)

onMounted(async () => {
  await auth.restore()
  if (auth.isLoggedIn) {
    void router.replace(redirect.value)
    return
  }
  // 图形验证码先取好：发短信要用它，等用户填完手机号再取会有空窗
  void refreshCaptcha()
})
</script>

<template>
  <div class="mw-auth">
    <aside class="mw-auth__aside">
      <RouterLink class="auth__brand" to="/">喵汪</RouterLink>
      <h1 class="auth__head">建个档</h1>
      <p class="auth__sub">
        注册之后就能给猫狗建健康档案、设照护提醒，还能领一只会说话的电子宠物。
      </p>
      <p class="auth__bubble">「你先把我领回家，其他的慢慢来。」</p>
    </aside>

    <main class="mw-auth__main">
      <form class="mw-auth__form" novalidate @submit.prevent="submit">
        <h2 class="mw-h2">注册喵汪</h2>

        <p v-if="error" class="mw-alert mw-alert--danger" role="alert">{{ error }}</p>

        <div class="mw-field">
          <label for="rg-username">用户名</label>
          <input
            id="rg-username"
            v-model="form.username"
            class="mw-input"
            type="text"
            autocomplete="username"
            placeholder="3 到 20 位字母、数字或下划线"
          />
          <span v-if="fieldError.username" class="mw-field__error">{{ fieldError.username }}</span>
        </div>

        <div class="auth__row">
          <div class="mw-field">
            <label for="rg-password">密码</label>
            <input
              id="rg-password"
              v-model="form.password"
              class="mw-input"
              type="password"
              autocomplete="new-password"
              placeholder="至少 6 位"
            />
            <span v-if="fieldError.password" class="mw-field__error">
              {{ fieldError.password }}
            </span>
          </div>
          <div class="mw-field">
            <label for="rg-confirm">确认密码</label>
            <input
              id="rg-confirm"
              v-model="form.confirm"
              class="mw-input"
              type="password"
              autocomplete="new-password"
              placeholder="再输一遍"
            />
            <span v-if="fieldError.confirm" class="mw-field__error">{{ fieldError.confirm }}</span>
          </div>
        </div>

        <div class="mw-field">
          <label for="rg-realname">真实姓名</label>
          <input
            id="rg-realname"
            v-model="form.realName"
            class="mw-input"
            type="text"
            autocomplete="name"
            placeholder="用于账号归属"
          />
          <span v-if="fieldError.realName" class="mw-field__error">{{ fieldError.realName }}</span>
        </div>

        <div class="mw-field">
          <label for="rg-callname">宠物怎么称呼你（可留空）</label>
          <input
            id="rg-callname"
            v-model="form.ownerCallName"
            class="mw-input"
            type="text"
            maxlength="20"
            placeholder="比如：铲屎官、妈妈、老张"
          />
        </div>

        <div class="mw-field">
          <label for="rg-phone">手机号</label>
          <input
            id="rg-phone"
            v-model="form.phone"
            class="mw-input"
            type="tel"
            inputmode="numeric"
            maxlength="11"
            autocomplete="tel"
            placeholder="11 位手机号"
          />
          <span v-if="fieldError.phone" class="mw-field__error">{{ fieldError.phone }}</span>
        </div>

        <div class="mw-field">
          <label for="rg-captcha">图片验证码</label>
          <div class="mw-captcha">
            <input
              id="rg-captcha"
              v-model="form.captchaCode"
              class="mw-input"
              type="text"
              maxlength="6"
              placeholder="看图输入"
            />
            <img
              v-if="captcha"
              class="mw-captcha__img"
              :src="captcha.imageBase64"
              alt="图形验证码，点击可换一张"
              title="看不清？点击换一张"
              @click="refreshCaptcha"
            />
          </div>
          <span v-if="fieldError.captchaCode" class="mw-field__error">
            {{ fieldError.captchaCode }}
          </span>
        </div>

        <div class="mw-field">
          <label for="rg-sms">短信验证码</label>
          <div class="mw-captcha">
            <input
              id="rg-sms"
              v-model="form.smsCode"
              class="mw-input"
              type="text"
              inputmode="numeric"
              maxlength="6"
              autocomplete="one-time-code"
              placeholder="6 位数字"
            />
            <button
              class="mw-btn mw-btn--ghost"
              type="button"
              :disabled="sending || countdown > 0"
              @click="onSendSms"
            >
              {{ countdown > 0 ? `${countdown} 秒后重发` : sending ? '发送中…' : '获取验证码' }}
            </button>
          </div>
          <span v-if="fieldError.smsCode" class="mw-field__error">{{ fieldError.smsCode }}</span>
          <span class="auth__hint">短信通道当前是 mock，验证码打在 user 服务日志里。</span>
        </div>

        <button class="mw-btn mw-btn--primary" type="submit" :disabled="submitting">
          {{ submitting ? '注册中…' : '注册并登录' }}
        </button>

        <p class="auth__foot">已经有账号了？<RouterLink to="/login">去登录</RouterLink></p>
      </form>
    </main>
  </div>
</template>

<style scoped>
.auth__brand {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--mw-on-night);
}
.auth__brand:hover {
  text-decoration: none;
}

.auth__head {
  font-size: 40px;
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1.1;
}

.auth__sub {
  max-width: 30ch;
  color: var(--mw-on-night-2);
}

.auth__bubble {
  max-width: 26ch;
  padding: var(--mw-space-3) var(--mw-space-4);
  border: 1px solid var(--mw-night-line);
  border-radius: 14px 14px 14px 4px;
  background: var(--mw-night-2);
  font-size: var(--mw-font-caption);
  line-height: 1.7;
}

/* 密码 / 确认密码：宽屏并排，窄屏自动堆叠 */
.auth__row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--mw-space-4);
}

.auth__hint {
  font-size: var(--mw-font-micro);
  color: var(--mw-ink-2);
}

.auth__foot {
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
}

@media (max-width: 860px) {
  .auth__head {
    font-size: 30px;
  }
  .auth__bubble {
    display: none;
  }
}
</style>
