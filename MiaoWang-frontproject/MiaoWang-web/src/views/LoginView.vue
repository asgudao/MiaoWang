<script setup lang="ts">
/**
 * 登录（密码 / 手机号两种方式）
 *
 * 手机号登录三步不能换序（见接口契约）：
 *   取图形验证码 → 发短信码（带图形验证码校验）→ 手机号登录（带短信码）
 * 图形验证码是**一次性**的：每次发短信后都要重新取一张，否则第二次必然报 A000700。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as userApi from '@/api/user'
import { authErrorMessage, isCaptchaError } from '@/utils/auth-error'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

type Mode = 'password' | 'phone'
const mode = ref<Mode>('password')

const form = reactive({ username: '', password: '', phone: '', captchaCode: '', smsCode: '' })
const fieldError = reactive({ username: '', password: '', phone: '', captchaCode: '', smsCode: '' })
const captcha = ref<userApi.CaptchaResp | null>(null)
const error = ref('')
const countdown = ref(0)
const sending = ref(false)
const submitting = ref(false)

const redirect = computed(() => {
  const r = route.query.redirect
  return (Array.isArray(r) ? r[0] : r) || '/'
})

/** 后端错误码 → 人话。图形验证码相关的错误要顺手换一张新图 */
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
  fieldError.username = ''
  fieldError.password = ''
  fieldError.phone = ''
  fieldError.captchaCode = ''
  fieldError.smsCode = ''
  error.value = ''

  if (mode.value === 'password') {
    if (!form.username.trim()) fieldError.username = '请输入用户名'
    if (!form.password) fieldError.password = '请输入密码'
    return !fieldError.username && !fieldError.password
  }

  if (!/^1[3-9]\d{9}$/.test(form.phone)) fieldError.phone = '请输入 11 位手机号'
  if (!form.captchaCode.trim()) fieldError.captchaCode = '请输入图片验证码'
  if (!/^\d{4,6}$/.test(form.smsCode.trim())) fieldError.smsCode = '请输入收到的短信验证码'
  return !fieldError.phone && !fieldError.captchaCode && !fieldError.smsCode
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
      scene: 'login',
      captchaKey: captcha.value.captchaKey,
      captchaCode: form.captchaCode.trim(),
    })
    startCountdown()
    error.value = ''
  } catch (e) {
    error.value = friendlyError(e)
  } finally {
    sending.value = false
    // 图形验证码无论成功失败都已被消费，必须换新的
    form.captchaCode = ''
    void refreshCaptcha()
  }
}

async function submit() {
  if (!validate()) return
  submitting.value = true
  try {
    if (mode.value === 'password') {
      await auth.login(form.username.trim(), form.password)
    } else {
      await auth.loginWithPhone(form.phone, form.smsCode.trim())
    }
    await router.replace(redirect.value)
  } catch (e) {
    error.value = friendlyError(e)
  } finally {
    submitting.value = false
  }
}

/** 切到手机号登录时按需取验证码 */
watch(mode, (m) => {
  error.value = ''
  if (m === 'phone' && !captcha.value) void refreshCaptcha()
})

onMounted(async () => {
  // 本地还有会话就先恢复，恢复成功直接回去，避免重复登录
  await auth.restore()
  if (auth.isLoggedIn) void router.replace(redirect.value)
})
</script>

<template>
  <div class="mw-auth">
    <aside class="mw-auth__aside">
      <RouterLink class="auth__brand" to="/">喵汪</RouterLink>
      <h1 class="auth__head">欢迎回来</h1>
      <p class="auth__sub">
        登录之后，电子宠物会记得你上次说过的话，也会接着提醒你还没做完的照护。
      </p>
      <p class="auth__bubble">「你终于回来了，我今天把饭盆舔得干干净净。」</p>
    </aside>

    <main class="mw-auth__main">
      <form class="mw-auth__form" novalidate @submit.prevent="submit">
        <h2 class="mw-h2">登录喵汪</h2>

        <div class="mw-seg" role="tablist" aria-label="登录方式">
          <button
            class="mw-seg__item"
            :class="{ 'mw-seg__item--on': mode === 'password' }"
            type="button"
            role="tab"
            :aria-selected="mode === 'password'"
            @click="mode = 'password'"
          >
            用户名密码
          </button>
          <button
            class="mw-seg__item"
            :class="{ 'mw-seg__item--on': mode === 'phone' }"
            type="button"
            role="tab"
            :aria-selected="mode === 'phone'"
            @click="mode = 'phone'"
          >
            手机号
          </button>
        </div>

        <p v-if="error" class="mw-alert mw-alert--danger" role="alert">{{ error }}</p>

        <template v-if="mode === 'password'">
          <div class="mw-field">
            <label for="lg-username">用户名</label>
            <input
              id="lg-username"
              v-model="form.username"
              class="mw-input"
              type="text"
              autocomplete="username"
              placeholder="注册时用的用户名"
            />
            <span v-if="fieldError.username" class="mw-field__error">{{
              fieldError.username
            }}</span>
          </div>

          <div class="mw-field">
            <label for="lg-password">密码</label>
            <input
              id="lg-password"
              v-model="form.password"
              class="mw-input"
              type="password"
              autocomplete="current-password"
              placeholder="你的密码"
            />
            <span v-if="fieldError.password" class="mw-field__error">{{
              fieldError.password
            }}</span>
          </div>
        </template>

        <template v-else>
          <div class="mw-field">
            <label for="lg-phone">手机号</label>
            <input
              id="lg-phone"
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
            <label for="lg-captcha">图片验证码</label>
            <div class="mw-captcha">
              <input
                id="lg-captcha"
                v-model="form.captchaCode"
                class="mw-input"
                type="text"
                inputmode="text"
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
            <label for="lg-sms">短信验证码</label>
            <div class="mw-captcha">
              <input
                id="lg-sms"
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
            <span class="auth__hint">同号码 60 秒 1 条、每天 10 条；验证码 5 分钟内有效。</span>
          </div>
        </template>

        <button class="mw-btn mw-btn--primary" type="submit" :disabled="submitting">
          {{ submitting ? '登录中…' : '登录' }}
        </button>

        <p class="auth__foot">
          还没有账号？<RouterLink to="/register">注册一个</RouterLink>
          <span class="auth__sep">或</span>
          <RouterLink to="/">先去逛逛</RouterLink>
        </p>
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

/* 电子宠物的一句话：侧栏唯一的"活"元素 */
.auth__bubble {
  max-width: 26ch;
  padding: var(--mw-space-3) var(--mw-space-4);
  border: 1px solid var(--mw-night-line);
  border-radius: 14px 14px 14px 4px;
  background: var(--mw-night-2);
  font-size: var(--mw-font-caption);
  line-height: 1.7;
}

.auth__hint {
  font-size: var(--mw-font-micro);
  color: var(--mw-ink-2);
}

.auth__foot {
  display: flex;
  align-items: center;
  gap: var(--mw-space-2);
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
}
.auth__sep {
  color: var(--mw-muted);
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
