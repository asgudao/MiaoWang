/**
 * 登录态（Pinia）
 *
 * 会话同时存 localStorage（刷新不丢）与内存（响应式）。用户名 + token 两个头后续请求都要带，
 * 所以这里把 username 也当作会话的一部分（网关注入 username 之前必须这样）。
 */
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ApiError, auth } from '@/api/request'
import * as userApi from '@/api/user'

export const useAuthStore = defineStore('auth', () => {
  const username = ref(auth.username ?? '')
  const token = ref(auth.token ?? '')
  const info = ref<userApi.UserInfo | null>(null)
  const loading = ref(false)
  /** 后端业务服务未注册时会一直失败，用一个标记让界面能诚实提示 */
  const backendUnavailable = ref(false)

  const isLoggedIn = computed(() => Boolean(username.value && token.value))
  const displayName = computed(
    () => info.value?.realName || info.value?.username || username.value || '',
  )
  const initial = computed(() => (displayName.value || '我').slice(0, 1))
  /** 首页问候语用「宠物怎么称呼你」，没设置就退回铲屎官 */
  const ownerCallName = computed(() => info.value?.ownerCallName || '铲屎官')

  function setSession(u: string, t: string) {
    username.value = u
    token.value = t
    auth.save(u, t)
  }

  function clearSession() {
    username.value = ''
    token.value = ''
    info.value = null
    auth.clear()
  }

  /** 网络层失败（后端没起/代理没通）与鉴权失败要分开处理 */
  function noteError(e: unknown) {
    if (e instanceof ApiError && (e.code === 'NETWORK' || e.code === '404')) {
      backendUnavailable.value = true
    }
  }

  async function login(u: string, p: string) {
    loading.value = true
    try {
      const resp = await userApi.loginByPassword(u, p)
      setSession(userApi.resolveLoginUsername(resp, u), resp.token)
      await fetchMe()
    } finally {
      loading.value = false
    }
  }

  async function loginWithPhone(phone: string, smsCode: string) {
    loading.value = true
    try {
      const resp = await userApi.loginByPhone(phone, smsCode)
      setSession(userApi.resolveLoginUsername(resp), resp.token)
      await fetchMe()
    } finally {
      loading.value = false
    }
  }

  async function register(body: userApi.RegisterBody) {
    loading.value = true
    try {
      await userApi.register(body)
    } finally {
      loading.value = false
    }
    // 注册成功后直接用刚填的账号密码登录，用户不用再输一遍
    await login(body.username, body.password)
  }

  async function fetchMe() {
    if (!isLoggedIn.value) return
    try {
      info.value = await userApi.getUserInfo(username.value)
    } catch (e) {
      noteError(e)
      if (e instanceof ApiError && e.isUnauthorized) clearSession()
    }
  }

  /** 启动时恢复会话：先问后端这个 token 还有效吗（Redis 会话 1 小时） */
  async function restore() {
    if (!auth.username || !auth.token) return
    username.value = auth.username
    token.value = auth.token
    try {
      const ok = await userApi.checkLogin(auth.username, auth.token)
      if (ok) await fetchMe()
      else clearSession()
    } catch (e) {
      // 只有"确定失效"才清会话；网络层失败只说明后端没起，不该把人踢出去
      if (e instanceof ApiError && e.isUnauthorized) clearSession()
      else noteError(e)
    }
  }

  async function logout() {
    try {
      await userApi.logout(username.value, token.value)
    } catch {
      // 退出失败也要清本地，否则用户被困在"退不出去"的状态
    }
    clearSession()
  }

  return {
    username,
    token,
    info,
    loading,
    backendUnavailable,
    isLoggedIn,
    displayName,
    initial,
    ownerCallName,
    login,
    loginWithPhone,
    register,
    fetchMe,
    restore,
    logout,
    clearSession,
  }
})
