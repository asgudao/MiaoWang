/**
 * 个人资料的数据编排（Web）
 *
 * 为什么单独抽一层（与 `composables/useNotifications.ts` 同一个理由）：
 * 这一页最容易写错的三件事 —— **乐观更新与失败回滚**、**掉线后切页内软门槛**、
 * **uid 第二路失败不拖垮整页** —— 全塞进 SFC 就只能靠正则去猜。这里只放数据与状态机，
 * `views/ProfileView.vue` 只做渲染，离线门禁 `tools/check-web-mine-rest.js` 用假 fetch 真跑它。
 *
 * **刻意不建 Pinia store**、也**不 import 任何 store**（任务口径 + 避开 `check-stores.js`
 * 的"导出 reset 的 store 必须在换号清理里被调用"门禁）。代价：写成功后
 * "我的页 / 顶栏的显示名"要由**页面**去调用 `authStore.fetchMe()` 对齐 ——
 * 所以 `save()` / `changeAvatar()` 都**返回 boolean**，页面据此决定要不要刷新 store，
 * 而不是让这一层去反向依赖 store。
 *
 * 五条对着后端源码写死的规则（依据都写在 `api/user.ts` 的追加段注释里）：
 * 1) **读**只走 `GET /user/{username}`：真名 / 用户名 / 手机号（后端已脱敏）/ 邮箱 /
 *    宠物称呼 / 头像六个字段，**一个都不多读**（后端就这些）。
 * 2) **写**只走 `PUT /user`，而且 body 里**必须带上 username**（与登录身份不一致会 `B000206`）。
 *    只提交三项能改的字段，**用户名与手机号不在其中**（后端已把它们从该接口移除）。
 * 3) **乐观更新 + 失败整份回滚 + 显示后端文案**：先把新值落到界面，请求失败再退回改前的值。
 * 4) **掉线切软门槛**：`ApiError.isUnauthorized` 为真时置 `unauthorized`，
 *    页面据此走"去登录"的软门槛，而不是摆一堆红字。**不新造登录弹窗**。
 * 5) **头像**：没有真头像 URL 就**不画图**（页面渲染显示名首字）。
 *    ⚠️ 上传接口是**真存在**的（`POST /user/avatar`），所以这里是真接；
 *    "后端没有上传接口"那句话是错的，页面上不许写。
 */
import { computed, ref } from 'vue'
import {
  AVATAR_ALLOWED_EXT,
  AVATAR_MAX_BYTES,
  displayNameOf,
  getUserInfo,
  phoneText,
  profileErrorMessage,
  updateMyProfile,
  uploadAvatar,
  type UserInfo,
} from '@/api/user'
import { getMySelfProfile, profileName } from '@/api/community'
import { ApiError, auth } from '@/api/request'

/** 编辑表单的三项（**只有这三项**能改，与 `UserUpdateReqDTO` 的字段一一对应） */
export interface ProfileForm {
  realName: string
  ownerCallName: string
  mail: string
}

const emptyForm = (): ProfileForm => ({ realName: '', ownerCallName: '', mail: '' })

export function useProfile() {
  /** `GET /user/{username}` 的原样返回（模板只读这里，不自己拼兜底之外的东西） */
  const info = ref<UserInfo | null>(null)
  const loaded = ref(false)
  const loading = ref(false)
  const error = ref('')

  /** 会话确实失效了（本地还有 token 但后端不认）→ 页内软门槛的第二道 */
  const unauthorized = ref(false)

  /** uid 第二路（社区侧）。**它失败了不降级整页**，只如实说"这次没读到" */
  const uid = ref<number | null>(null)
  const uidError = ref('')

  const editing = ref(false)
  const form = ref<ProfileForm>(emptyForm())
  const saving = ref(false)
  const saveError = ref('')
  const note = ref('')

  const avatarBusy = ref(false)

  /**
   * 本地有没有会话。
   *
   * 数据源就是请求层用的那两个 localStorage 键（`api/request.ts` 的 `auth`）——
   * 没有会话时每个请求都会**不带鉴权头**发出去、经网关必然 `state:-1`，
   * 所以"没有会话就别发请求"这条门槛与真实行为完全一致。
   * ⚠️ `auth` 是 localStorage 的 getter，**不是响应式**：这里只在 `unauthorized` 变化时重算。
   * 本页只可能在登录后被路由守卫放进来（`/profile` 挂着 `requiresAuth`），所以这个值在页面生命周期里稳定。
   */
  const needLogin = computed(() => !(auth.username && auth.token) || unauthorized.value)

  /** 资料读到了才给"编辑"入口（读不到就别让改） */
  const canEdit = computed(() => loaded.value && !needLogin.value)

  /** 显示名：真名 → 用户名 → 「喵友 #uid」（社区口径，**不用"匿名用户"**，后端从没返回过它） */
  const displayName = computed(
    () => displayNameOf(info.value) || profileName(null, uid.value) || '',
  )

  /** 没有头像时的兜底首字。⚠️ 不能用 `utils/pet.ts` 的 `initials()`：那是**宠物**口径（兜底给"宠"） */
  const initial = computed(() => (displayName.value || '我').slice(0, 1))

  /** 真头像 URL：后端没给就是空串，模板据此决定画 <img> 还是画首字（**绝不画假头像**） */
  const avatarUrl = computed(() => info.value?.avatar || '')

  const avatarText = computed(() =>
    avatarUrl.value ? '头像已设置' : '还没有头像，先用显示名的首字代替',
  )

  const avatarBtnText = computed(() => {
    if (avatarBusy.value) return '上传中…'
    return avatarUrl.value ? '换一张' : '选一张上传'
  })

  const usernameText = computed(() => info.value?.username || auth.username || '')

  /** 手机号：后端已脱敏，`phoneText` 只在拿到"完整 11 位"时才兜底打码 */
  const phoneShown = computed(() => phoneText(info.value?.phone))

  const realNameShown = computed(() => info.value?.realName || '')
  const callNameShown = computed(() => info.value?.ownerCallName || '')
  const mailShown = computed(() => info.value?.mail || '')

  /** 掉线统一处理：置软门槛并清掉正在进行的写状态，返回是否命中 */
  function noteAuth(e: unknown): boolean {
    if (!(e instanceof ApiError) || !e.isUnauthorized) return false
    unauthorized.value = true
    error.value = ''
    saveError.value = ''
    note.value = ''
    loading.value = false
    saving.value = false
    avatarBusy.value = false
    editing.value = false
    return true
  }

  /** 把接口值灌进表单（进入编辑态时用，取消时也要回灌） */
  function syncForm() {
    form.value = {
      realName: info.value?.realName || '',
      ownerCallName: info.value?.ownerCallName || '',
      mail: info.value?.mail || '',
    }
  }

  /**
   * 首屏：读资料。uid 是**第二路读**，失败了也不把整页变错误态。
   * 未登录/掉线时**一个请求都不发**（发了也只会拿回网关的 `state:-1`）。
   *
   * 顺序刻意如此：先把资料落进 `info` 并把 `loaded` / `loading` 收尾（首屏立刻能渲染），
   * **之后**才 `await loadUid()` —— 所以 uid 这一路晚到不会让页面一直停在骨架屏上，
   * 而 `load()` 仍然是一个"等所有读都结束"的完整 Promise（离线门禁据此确定性地断言）。
   */
  async function load() {
    if (needLogin.value) return
    loading.value = true
    error.value = ''
    try {
      info.value = await getUserInfo(auth.username || '')
      loaded.value = true
      syncForm()
    } catch (e) {
      if (noteAuth(e)) return
      error.value = profileErrorMessage(e)
      return
    } finally {
      loading.value = false
    }
    await loadUid()
  }

  /**
   * uid 与社区计数。**这一路失败不降级整页**：昵称、手机号、邮箱都已经读到了，
   * 不能因为 uid 没读到就把一整页换成错误态；如实说"这次没读到"就够了。
   */
  async function loadUid() {
    try {
      const p = await getMySelfProfile()
      uid.value = p && p.uid !== undefined && p.uid !== null ? p.uid : null
      uidError.value = uid.value === null ? '这次返回里没有 uid。' : ''
    } catch (e) {
      uid.value = null
      uidError.value =
        e instanceof ApiError && e.isUnauthorized
          ? '这次没读到（登录态可能已经过期）。'
          : `这次没读到：${profileErrorMessage(e)}`
    }
  }

  function startEdit() {
    if (!canEdit.value || saving.value) return
    syncForm()
    saveError.value = ''
    note.value = ''
    editing.value = true
  }

  function cancelEdit() {
    // 取消 = 丢掉未保存的输入，回到接口值
    syncForm()
    editing.value = false
    saveError.value = ''
  }

  function toggleEdit() {
    if (editing.value) cancelEdit()
    else startEdit()
  }

  function clearSaveError() {
    saveError.value = ''
  }

  function clearNote() {
    note.value = ''
  }

  /**
   * 保存资料：**乐观更新 + 失败回滚 + 显示后端文案**。
   *
   * 顺序刻意如此：先把新值落到界面（用户立刻看到结果），再发请求；失败就把三项**整份**回滚
   * （不做字段级猜谜），并把后端的 message 原样显示出来（后端这些 message 就是中文，
   * 如「只能改自己的资料」）。掉线时**不显示错误**，只切软门槛。
   *
   * ⚠️ `username` 必须原样带上：`PUT /user` 用它做"只能改自己"的校验（对不上报 `B000206`）。
   * ⚠️ 返回 `true` 才算写成功 → 页面据此去 `authStore.fetchMe()` 对齐"我的页 / 顶栏"。
   */
  async function save(): Promise<boolean> {
    if (saving.value || !canEdit.value) return false
    const before: ProfileForm = {
      realName: info.value?.realName || '',
      ownerCallName: info.value?.ownerCallName || '',
      mail: info.value?.mail || '',
    }
    const next: ProfileForm = {
      realName: form.value.realName.trim(),
      ownerCallName: form.value.ownerCallName.trim(),
      mail: form.value.mail.trim(),
    }
    // 乐观更新
    info.value = info.value ? { ...info.value, ...next } : info.value
    editing.value = false
    saving.value = true
    saveError.value = ''
    note.value = ''
    try {
      await updateMyProfile({
        username: usernameText.value,
        realName: next.realName,
        ownerCallName: next.ownerCallName,
        mail: next.mail,
      })
      note.value = '已保存'
      return true
    } catch (e) {
      const authLost = noteAuth(e)
      // 失败回滚：**整份**退回改前的值（不做字段级猜谜）
      info.value = info.value ? { ...info.value, ...before } : info.value
      if (!authLost) {
        // ⚠️ 这里**故意不调 `syncForm()`**：回滚的是界面上的资料，
        // 用户刚敲进去的那几个字要留着，否则"保存失败"还得让人重打一遍。
        saveError.value = profileErrorMessage(e)
        editing.value = true
      }
      return false
    } finally {
      saving.value = false
    }
  }

  /**
   * 换头像：本地先卡格式与 5MB（省一次白传，**不是安全边界**）→ 真上传 →
   * 用**后端返回的 URL** 更新界面。
   *
   * 这里**没有乐观更新**：URL 是后端生成的（MinIO 取址），前端猜不出来；
   * 失败也**不动**现有头像（不把已经好的头像换成空白）。
   * 返回 `true` 才算写成功。
   */
  async function changeAvatar(file: File | null | undefined): Promise<boolean> {
    if (!canEdit.value || avatarBusy.value) return false
    saveError.value = ''
    note.value = ''
    if (!file) {
      saveError.value = '没有选到文件，重新选一张'
      return false
    }
    const ext = (file.name.split('.').pop() || '').toLowerCase()
    if (!AVATAR_ALLOWED_EXT.includes(ext as (typeof AVATAR_ALLOWED_EXT)[number])) {
      saveError.value = '这个格式后端不收，只支持 jpg / jpeg / png / webp'
      return false
    }
    if (file.size > AVATAR_MAX_BYTES) {
      saveError.value = `这张图 ${Math.round(file.size / 1024 / 1024)}MB，超过后端 5MB 的上限，换一张小点的`
      return false
    }
    avatarBusy.value = true
    try {
      const url = await uploadAvatar(file)
      info.value = info.value ? { ...info.value, avatar: url } : info.value
      note.value = '头像已更新'
      return true
    } catch (e) {
      if (noteAuth(e)) return false
      saveError.value = profileErrorMessage(e)
      return false
    } finally {
      avatarBusy.value = false
    }
  }

  return {
    // 读
    info,
    loaded,
    loading,
    error,
    unauthorized,
    needLogin,
    canEdit,
    load,
    // uid（社区侧第二路）
    uid,
    uidError,
    // 展示口径
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
    // 编辑
    editing,
    form,
    saving,
    saveError,
    note,
    startEdit,
    cancelEdit,
    toggleEdit,
    clearSaveError,
    clearNote,
    save,
    // 头像
    avatarBusy,
    changeAvatar,
  }
}