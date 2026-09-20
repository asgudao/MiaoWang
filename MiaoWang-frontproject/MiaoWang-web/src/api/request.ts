/**
 * 喵汪 Web 端请求层
 *
 * 两条硬规则（来自 miaowang-api-contract）：
 * 1) 走网关时必须带服务名前缀：/{serviceName}/mapleleaf/miaowang/v1/...
 *    （网关用 discovery locator 自动路由，白名单也印证了这个形状）
 * 2) 返回结构有两套：业务服务是 `Result{code,message,data,success}`（code==='0' 成功），
 *    网关是 `JsonResult{state,message,data}`（state===0 成功，-1 登录过期）—— 这里统一归一化
 */

export const API_PREFIX = '/mapleleaf/miaowang/v1'

/** 网关会自动路由的服务名（Nacos 注册名） */
export const SERVICE = {
  user: 'miaowang-user',
  pet: 'miaowang-pet',
  community: 'miaowang-community',
  knowledge: 'miaowang-knowledge',
  vip: 'miaowang-vip',
  aiPet: 'miaowang-ai-pet',
  aiKnowledge: 'miaowang-ai-knowledge',
  healthy: 'miaowang-healthy',
} as const
export type ServiceKey = keyof typeof SERVICE

/** 开发期由 Vite 代理到网关；生产改为真实网关域名 */
const BASE = '/api'

const TOKEN_KEY = 'mw_token'
const USERNAME_KEY = 'mw_username'

export const auth = {
  get token() {
    return localStorage.getItem(TOKEN_KEY)
  },
  get username() {
    return localStorage.getItem(USERNAME_KEY)
  },
  save(username: string, token: string) {
    localStorage.setItem(USERNAME_KEY, username)
    localStorage.setItem(TOKEN_KEY, token)
  },
  clear() {
    localStorage.removeItem(USERNAME_KEY)
    localStorage.removeItem(TOKEN_KEY)
  },
}

/** 业务错误码里代表"未登录/过期"的几种写法（每个服务各写一套，见 miaowang-api-contract） */
const UNAUTHORIZED_CODES = new Set(['UNAUTHORIZED', '-1', 'B000310', 'B000204', 'B000701'])

/**
 * pet / healthy 把"未登录或登录已过期"也塞在 A000001 里，而 A000001 同时用于参数错误，
 * 所以不能直接把 A000001 当未登录 —— 只能用 message 兜底识别。
 */
const UNAUTHORIZED_PATTERN = /未登录|登录已过期|登录失效|登录状态已失效/

/**
 * 自己解流的场景（SSE）用：按与 request() 完全相同的规则拼鉴权头。
 * 走网关时两个头都要带（网关只校验 token，但下游服务读 username）。
 */
export function authHeaders(): Record<string, string> {
  const h: Record<string, string> = {}
  if (auth.username && auth.token) {
    h.username = auth.username
    h.token = auth.token
  }
  return h
}

export class ApiError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly raw?: unknown,
  ) {
    super(message)
    this.name = 'ApiError'
  }
  get isUnauthorized() {
    return UNAUTHORIZED_CODES.has(this.code) || UNAUTHORIZED_PATTERN.test(this.message)
  }
}

interface Envelope {
  state?: number
  code?: string
  success?: boolean
  message?: string
  data?: unknown
  requestId?: string
}

type Query = Record<string, string | number | boolean | undefined | null>

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  query?: Query
  body?: unknown
  /** 上传文件用；传了就忽略 body，走 multipart */
  formData?: FormData
  /** 业务上不需要登录的接口（如登录/注册）可置 true，避免未登录时附带残缺头 */
  anonymous?: boolean
  signal?: AbortSignal
}

export function buildUrl(service: ServiceKey, path: string, query?: Query) {
  const url = `${BASE}/${SERVICE[service]}${API_PREFIX}${path}`
  if (!query) return url
  const qs = new URLSearchParams()
  for (const [k, v] of Object.entries(query)) {
    if (v !== undefined && v !== null && v !== '') qs.append(k, String(v))
  }
  const s = qs.toString()
  return s ? `${url}?${s}` : url
}

function unwrap<T>(env: Envelope): T {
  // 网关形状
  if (env.state !== undefined) {
    if (env.state === 0) return env.data as T
    if (env.state === -1) throw new ApiError('UNAUTHORIZED', env.message || '登录已过期', env)
    throw new ApiError(String(env.state), env.message || '请求失败', env)
  }
  // 业务服务形状
  if (env.code !== undefined) {
    if (env.code === '0') return env.data as T
    throw new ApiError(env.code, env.message || '请求失败', env)
  }
  // 没有信封（极少数直出 data 的接口）
  return env as unknown as T
}

/** 统一请求；成功直接返回 data，失败抛 ApiError */
export async function request<T = unknown>(
  service: ServiceKey,
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const { method = 'GET', query, body, formData, anonymous, signal } = options

  const headers: Record<string, string> = {}
  if (!formData && body !== undefined) headers['Content-Type'] = 'application/json'
  // 网关尚未从 JWT 注入 username，前端必须自己带齐两个头
  if (!anonymous && auth.username && auth.token) {
    headers['username'] = auth.username
    headers['token'] = auth.token
  }

  let res: Response
  try {
    res = await fetch(buildUrl(service, path, query), {
      method,
      headers,
      body: formData ?? (body !== undefined ? JSON.stringify(body) : undefined),
      signal,
    })
  } catch (e) {
    // 网络层失败（断网 / 被拦 / 代理没起）→ 把实际请求地址带进消息，
    // 这样能一眼区分"Vite 代理没通"和"后端进程没起"
    throw new ApiError(
      'NETWORK',
      `连不上后端（${buildUrl(service, path, query)}）：Vite 代理或后端进程没起`,
      e,
    )
  }

  if (res.status === 401 || res.status === 403) {
    auth.clear()
    throw new ApiError('UNAUTHORIZED', '登录已过期，请重新登录')
  }

  const text = await res.text()
  let env: Envelope
  try {
    env = text ? (JSON.parse(text) as Envelope) : ({} as Envelope)
  } catch {
    throw new ApiError(
      'BAD_JSON',
      `服务返回了非 JSON 内容（HTTP ${res.status}）`,
      text.slice(0, 200),
    )
  }

  if (!res.ok && env.code === undefined && env.state === undefined) {
    throw new ApiError(String(res.status), `请求失败（HTTP ${res.status}）`, env)
  }

  return unwrap<T>(env)
}

export const api = {
  get: <T = unknown>(s: ServiceKey, p: string, o?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(s, p, { ...o, method: 'GET' }),
  post: <T = unknown>(s: ServiceKey, p: string, o?: Omit<RequestOptions, 'method'>) =>
    request<T>(s, p, { ...o, method: 'POST' }),
  put: <T = unknown>(s: ServiceKey, p: string, o?: Omit<RequestOptions, 'method'>) =>
    request<T>(s, p, { ...o, method: 'PUT' }),
  del: <T = unknown>(s: ServiceKey, p: string, o?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(s, p, { ...o, method: 'DELETE' }),
}
