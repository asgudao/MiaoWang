/**
 * 用户与鉴权接口
 *
 * 契约以 .agents/skills/miaowang-api-contract 为准。几个**反直觉点**记在这里：
 * - 登录返回的 `data` 是**对象** `{ token }`，不是裸字符串
 * - 注册（POST /user）的 `smsCode` **必填**，且 scene 必须是 `register`
 * - 图形验证码**一次性**：同一张图只能用一次，重发短信要重新取一张
 * - 短信码分场景存（login / register 互不通用），连续错 5 次作废
 * - 这些接口里**只有验证码 / 发短信 / 登录 / 注册**走网关白名单，要带 `anonymous: true`；
 *   其余（check-login / has-username / logout / 用户信息）实测**必须带 username + token 头**，
 *   只放 query 会被网关拦成 `state:-1`（契约里标注的"免登录"不准，见 tools/probe-auth.ps1 实测）
 */
import { api, ApiError } from './request'

export interface CaptchaResp {
  captchaKey: string
  /** `data:image/png;base64,...`，可直接塞 <img :src> */
  imageBase64: string
  expireSeconds: number
}

export interface TokenResp {
  token: string
  /** 手机号登录理论上可能一并返回；后端当前只保证 token */
  username?: string
}

export interface UserInfo {
  /** ⚠️ 后端 DTO 叫 id、DO 叫 uid，且拷贝有缺陷恒为 null（挂账 #14），别依赖它 */
  id?: number | null
  username: string
  realName?: string
  phone?: string
  mail?: string
  /** 宠物怎么称呼你，用于首页问候语 */
  ownerCallName?: string
  avatar?: string
}

export type SmsScene = 'login' | 'register'

export interface RegisterBody {
  username: string
  password: string
  realName: string
  phone: string
  mail?: string
  ownerCallName?: string
  smsCode: string
}

/** 图形验证码（免登录，一次性） */
export const getCaptcha = () => api.get<CaptchaResp>('user', '/user/captcha', { anonymous: true })

/** 发短信码 */
export const sendSms = (body: {
  phone: string
  scene: SmsScene
  captchaKey: string
  captchaCode: string
}) => api.post<null>('user', '/user/sms', { body, anonymous: true })

/** 密码登录 */
export const loginByPassword = (username: string, password: string) =>
  api.post<TokenResp>('user', '/user/login', { body: { username, password }, anonymous: true })

/** 手机号登录；手机号没注册过会自动建号（用户名随机，如 mw_46451234） */
export const loginByPhone = (phone: string, smsCode: string) =>
  api.post<TokenResp>('user', '/user/login/phone', { body: { phone, smsCode }, anonymous: true })

/** 注册 */
export const register = (body: RegisterBody) =>
  api.post<null>('user', '/user', { body, anonymous: true })

/** 用户名是否可用
 *  ⚠️ **需要登录**：网关没把它放进白名单，实测匿名调用直接返回 `state:-1`（契约标注的"免登录"是错的），
 *  所以注册页不能用它做实时查重，只能在登录后（如改资料）用。
 */
export const hasUsername = (username: string) =>
  api.get<boolean>('user', '/user/has-username', { query: { username } })

/** 脱敏用户信息（后端返回的 phone 已经是 `137****0402` 这种） */
export const getUserInfo = (username: string) =>
  api.get<UserInfo>('user', `/user/${encodeURIComponent(username)}`)

/** 校验登录态是否仍有效（Redis 会话 1 小时，与 JWT 对齐）
 *  ⚠️ **必须带 username/token 头**：匿名调用实测返回 `state:-1`；有效返回 `data:true`，
 *  已登出返回 `data:false`（是干净的假值，不是报错）
 */
export const checkLogin = (username: string, token: string) =>
  api.get<boolean>('user', '/user/check-login', { query: { username, token } })

/** 退出登录（同样需要鉴权头；只把 username/token 放 query 会被网关拦掉） */
export const logout = (username: string, token: string) =>
  api.del<null>('user', '/user/logout', { query: { username, token } })

/* ------------------------------------------------------------------ *
 * 从 JWT 里尽力取出 username
 *
 * 为什么需要：**手机号登录只返回 `{ token }`**，而网关还没把 username 注入下游请求头（挂账 #8），
 * 前端必须自己凑齐 `username` + `token` 两个头，否则下游服务按错误用户名查库（实测会 `B000200 用户记录不存在`）。
 *
 * 实测真 token 的载荷（tools/probe-auth.ps1 打印）：
 *   {"claims":{"uid":7,"username":"probe114904"},"sub":"user","iat":…,"exp":…}
 * 注意两点：
 * - 用户名在 `claims.username`（契约里写的 `clainms` 拼写已过时）
 * - **`sub` 是常量 `"user"`，不是用户名** —— 所以绝不能拿 `sub` 兜底，
 *   否则会静默地把 `user` 当用户名发出去（网关只校验 token，会放行，问题被推到下游）
 * ------------------------------------------------------------------ */

function b64urlDecode(input: string): string {
  const s = input.replace(/-/g, '+').replace(/_/g, '/')
  const pad = s.length % 4 ? '='.repeat(4 - (s.length % 4)) : ''
  const bin = atob(s + pad)
  // atob 给的是 latin1，要按 UTF-8 还原（用户名可能含中文）
  const bytes = Uint8Array.from(bin, (c) => c.charCodeAt(0))
  return new TextDecoder().decode(bytes)
}

/** 在（可能嵌套、可能二次编码的）结构里找第一个非空的候选键 */
function findKey(node: unknown, keys: string[], depth = 0): string | undefined {
  if (depth > 4 || node == null) return undefined

  if (typeof node === 'string') {
    const t = node.trim()
    if (t.startsWith('{') || t.startsWith('[')) {
      try {
        return findKey(JSON.parse(t), keys, depth + 1)
      } catch {
        return undefined
      }
    }
    // 形如一段 base64url 的 JSON（二次编码）
    if (/^[A-Za-z0-9_-]{20,}$/.test(t)) {
      try {
        return findKey(JSON.parse(b64urlDecode(t)), keys, depth + 1)
      } catch {
        return undefined
      }
    }
    return undefined
  }

  if (typeof node !== 'object') return undefined
  const obj = node as Record<string, unknown>
  for (const k of keys) {
    const v = obj[k]
    if (typeof v === 'string' && v) return v
  }
  for (const v of Object.values(obj)) {
    const found = findKey(v, keys, depth + 1)
    if (found) return found
  }
  return undefined
}

/**
 * 解出 token 里的 username；解不出返回 undefined。
 * 只认显式的 username 类字段 —— 实测的 `sub` 是常量 `"user"`，拿它兜底会是个静默的错。
 */
export function usernameFromToken(token?: string | null): string | undefined {
  if (!token) return undefined
  const parts = token.split('.')
  const payloadPart = parts[1]
  if (!payloadPart) return undefined
  let payload: unknown
  try {
    payload = JSON.parse(b64urlDecode(payloadPart))
  } catch {
    return undefined
  }
  return findKey(payload, ['username', 'userName', 'user_name', 'nickName'])
}

/** 登录成功后确定 username；确实拿不到就明确报错，绝不静默 */
export function resolveLoginUsername(resp: TokenResp | null, fallback?: string): string {
  const u = resp?.username || usernameFromToken(resp?.token) || fallback
  if (!u) {
    throw new ApiError(
      'NO_USERNAME',
      '登录成功但没能确定用户名：手机号登录只返回 token。请改用用户名密码登录，或让后端在登录响应里带上 username。',
      resp,
    )
  }
  return u
}

/* ==================================================================== *
 * 个人资料（「我的 → 个人资料」页用）：读自己的资料 / 改资料 / 换头像
 *
 * 2026-09-21 追加，**上面既有导出一个字节都没动**（纯追加段）。
 * 契约与依据（逐个对着后端源码核过，不是照记忆写的）：
 *
 * | 能力 | 接口 | 依据 |
 * |---|---|---|
 * | 读自己的资料（手机号已脱敏） | `GET /user/{username}` | UserController.java:34 |
 * | 改显示名称 / 宠物称呼 / 邮箱 | `PUT /user` | UserController.java:68 + UserUpdateReqDTO.java:14 |
 * | 换头像 | `POST /user/avatar`（multipart，字段名 `file`） | UserController.java:126 + UserServiceImpl.java:279 |
 *
 * ⚠️ 三条**不能做**的（页面上如实写明，不放进一个点了没反应的按钮）：
 * - **用户名改不了**：`PUT /user` 的 body.username 必须等于请求头还原出的登录身份，
 *   对不上直接 `B000206`（UserServiceImpl.java:170）。
 * - **手机号换绑**要新号的短信验证码（`PUT /user/phone`，scene=`change_phone`），
 *   本页不带短信流程 → 只读，且后端返回的本来就是脱敏号（`PhoneDesensitizationSerializer`）。
 * - **改密码**（`PUT /user/password`）成功后当前 token 立即失效，属于另一个流程，本页不放入口。
 *
 * ⚠️ `GET /user/{username}` 的 `data.id` **恒为 null**（DO 叫 uid、DTO 叫 id，拷贝对不上，挂账 #14）
 * → 前端拿不到 uid；要 uid 只能走社区侧（见 `api/community.ts` 的自查资料）。
 * ==================================================================== */

/** 改资料请求体。`username` **必须**带上且与登录身份一致，它是后端"只能改自己"的判据 */
export interface ProfileUpdateBody {
  username: string
  realName?: string
  mail?: string
  ownerCallName?: string
}

/** 改资料：只能改自己的（body.username 与登录身份不一致 → `B000206`） */
export const updateMyProfile = (body: ProfileUpdateBody) =>
  api.put<null>('user', '/user', { body })

/**
 * 头像上传上限与允许的格式：与后端保持一致。
 * 依据：容器层 `max-file-size: 5MB`（超了 `A000602`）、扩展名白名单
 * `jpg/jpeg/png/webp`（别的 `A000601`）。前端先本地卡一道只是省一次白传，**不是安全边界**。
 */
export const AVATAR_MAX_BYTES = 5 * 1024 * 1024
export const AVATAR_ACCEPT = 'image/jpeg,image/png,image/webp'
export const AVATAR_ALLOWED_EXT = ['jpg', 'jpeg', 'png', 'webp'] as const

/**
 * 换头像：`multipart/form-data`，字段名就是 `file`（后端 `@RequestParam("file")`）。
 *
 * 改谁的头像**由后端按请求头还原的登录身份决定**，前端不传 username —— 结构上杜绝改别人头像。
 * 返回 `data` = 新头像的 URL（MinIO 取址后的直链，见 common 的 `ObjectStorageUrlBuilder`）。
 */
export const uploadAvatar = (file: File) => {
  const fd = new FormData()
  fd.append('file', file)
  return api.post<string>('user', '/user/avatar', { formData: fd })
}

/**
 * 手机号的展示文本。
 *
 * 后端 `UserRespDTO.phone` 上挂了 `PhoneDesensitizationSerializer`，**返回的本来就是脱敏号**
 * （实测 fixture：`137****0402`）→ 不能再打一次码，否则会把星号又切一遍。
 * 只有拿到"看起来是完整 11 位数字"的号时才兜底打码（防上游哪天改成不脱敏）。
 */
export function phoneText(phone: string | null | undefined): string {
  const p = (phone ?? '').trim()
  if (!p) return ''
  if (/^\d{11}$/.test(p)) return `${p.slice(0, 3)}****${p.slice(-4)}`
  return p
}

/** 显示名：真名 → 用户名 → 空（页面再兜底，兜底口径见 `api/community.ts` 的 `profileName`） */
export function displayNameOf(
  info: Pick<UserInfo, 'realName' | 'username'> | null | undefined,
): string {
  return (info?.realName || info?.username || '').trim()
}

/**
 * 资料相关错误 → 人话。
 *
 * 后端这些 message 本身就是给用户看的中文（如「只能改自己的资料」），所以**优先原样透传**，
 * 只对少数需要指路的码做加工。`A000001` 同时是"参数错"与"未登录"（pet/healthy 口径），
 * 所以未登录的判据一律用 `ApiError.isUnauthorized`（码集合 + message 正则），不自己列码表。
 */
export function profileErrorMessage(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.isUnauthorized) return '登录状态失效了，重新登录后再改'
    if (e.code === 'NETWORK') return e.message || '连不上后端，请检查后端进程与 Vite 代理'
    if (e.code === 'B000206') return e.message || '只能改自己的资料'
    if (e.code === 'A000600') return '没有选到文件，重新选一张'
    if (e.code === 'A000601') return '这个格式后端不收，只支持 jpg / jpeg / png / webp'
    if (e.code === 'A000602') return '这张图超过了后端 5MB 的上限，换一张小点的'
    if (e.code === 'C000100') return '对象存储上传失败（MinIO 那侧的问题），稍后再试'
    return e.message || '操作失败，请稍后再试'
  }
  return e instanceof Error ? e.message : '操作失败，请稍后再试'
}
