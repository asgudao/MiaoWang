/**
 * 喵汪手机端请求层（uni-app / JS）
 *
 * 两条硬规则（来自 miaowang-api-contract）：
 * 1) 走网关必须带服务名前缀：/{serviceName}/mapleleaf/miaowang/v1/...
 * 2) 返回结构两套：业务服务 `Result{code,message,data,success}`（code==='0' 成功）；
 *    网关 `JsonResult{state,message,data}`（state===0 成功，-1 过期）—— 这里归一化
 *
 * ⚠️ 本工程是 HBuilderX + JS，不引 npm/Pinia；状态用 reactive 模块手写。
 */

import { clearManualHost, ensureHost, getBase, getHost, setManualHost } from './host.js'

const API_PREFIX = '/mapleleaf/miaowang/v1'

/** 网关按 Nacos 服务名自动路由 */
export const SERVICE = {
  user: 'miaowang-user',
  pet: 'miaowang-pet',
  community: 'miaowang-community',
  knowledge: 'miaowang-knowledge',
  vip: 'miaowang-vip',
  aiPet: 'miaowang-ai-pet',
  aiKnowledge: 'miaowang-ai-knowledge',
  healthy: 'miaowang-healthy'
}

/**
 * 网关地址
 *
 * H5：留空走相对路径 → manifest.json 的 devServer 代理（/miaowang-* → 网关 7000），绕开后端 CORS
 * 非 H5（真机 / 小程序）：手机访问不到 localhost，地址由 utils/host.js 动态解析（不再写死）
 *
 * 写法说明：**先声明一次、再条件赋值**。若写成两个 `let BASE = ...` 分放在 #ifdef/#ifndef 里，
 * 条件编译没生效时就是"重复声明"硬错误；这种写法无论预处理与否都是合法 JS。
 */
let BASE
// #ifdef H5
BASE = ''
// #endif
// #ifndef H5
// 真机地址**不再写死**：由 utils/host.js 解析 —— 手填 > 上次探测成功 > 内置候选 > 扫网段自动发现。
// 为什么会变：DHCP 换租约、换 Wi-Fi、无线切有线、开热点都会换地址，写死就必然"昨天能连今天连不上"
// （2026-09-17 真踩过：WLAN 一断，192.168.225.183 这个地址就废了，放行防火墙也没用）。
// 网络失败时 request() 还会调 ensureHost() 自愈一次（换地址后自动重试一次请求）。
// 防火墙要按**端口 7000**放行，别按 java.exe：网关用的是 E:\jdk\bin\java.exe，常见的 Java 允许规则覆盖不到它。
// 一键脚本：MiaoWang-frontproject/tools/allow-lan-access.ps1
BASE = getBase()
// #endif

/** 当前后端地址（设置界面显示用；H5 下无意义） */
export function currentHost () {
  return getHost()
}

/** 手动指定后端地址（`192.168.1.5` 或 `192.168.1.5:7000` 都行）；返回规整后的地址，非法返回 '' */
export function applyHost (input) {
  const h = setManualHost(input)
  if (h && BASE) BASE = 'http://' + h
  return h
}

/** 清除手填地址，回到自动解析 */
export function resetHost () {
  clearManualHost()
  if (BASE) BASE = 'http://' + getHost()
}

const TOKEN_KEY = 'mw_token'
const USERNAME_KEY = 'mw_username'

export const auth = {
  getToken () {
    return uni.getStorageSync(TOKEN_KEY) || ''
  },
  getUsername () {
    return uni.getStorageSync(USERNAME_KEY) || ''
  },
  save (username, token) {
    uni.setStorageSync(USERNAME_KEY, username)
    uni.setStorageSync(TOKEN_KEY, token)
  },
  clear () {
    uni.removeStorageSync(USERNAME_KEY)
    uni.removeStorageSync(TOKEN_KEY)
  }
}

/**
 * 自己解流的场景（SSE）用：按与 request() 完全相同的规则拼鉴权头。
 * 走网关两个头都要带（网关只校验 token，但下游服务读 username）。
 */
export function authHeaders () {
  const header = {}
  const username = auth.getUsername()
  const token = auth.getToken()
  if (username && token) {
    header.username = username
    header.token = token
  }
  return header
}

/** 业务错误码里代表"未登录/过期"的几种写法（每个服务各写一套，见 miaowang-api-contract） */
/**
 * 给界面/日志用的"当前请求打到哪"
 *
 * 真机与 H5 的路径完全不同，报错时把这条带上，就不用猜是哪种环境出的问题。
 * （2026-09-17 教训：错误文案被笼统兜底覆盖掉，白白多排查一轮）
 */
export function describeTarget () {
	// #ifdef H5
	return 'H5 经 devServer 代理 → http://localhost:7000'
	// #endif
	// #ifndef H5
	return 'App 直连 ' + BASE
	// #endif
}

const UNAUTHORIZED_CODES = ['UNAUTHORIZED', '-1', 'B000310', 'B000204', 'B000701']

/**
 * pet / healthy 把"未登录或登录已过期"也塞在 A000001 里，而 A000001 同时用于参数错误，
 * 所以不能直接把 A000001 当未登录 —— 只能用 message 兜底识别。
 */
const UNAUTHORIZED_PATTERN = /未登录|登录已过期|登录失效|登录状态已失效/

export class ApiError extends Error {
  constructor (code, message, raw) {
    super(message || '请求失败')
    this.name = 'ApiError'
    this.code = String(code)
    this.raw = raw
  }

  get isUnauthorized () {
    return UNAUTHORIZED_CODES.indexOf(this.code) >= 0 ||
      UNAUTHORIZED_PATTERN.test(this.message || '')
  }
}

export function buildUrl (service, path, query) {
  let url = BASE + '/' + SERVICE[service] + API_PREFIX + path
  if (query) {
    const parts = []
    Object.keys(query).forEach(function (k) {
      const v = query[k]
      if (v !== undefined && v !== null && v !== '') {
        parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(v))
      }
    })
    if (parts.length) url += '?' + parts.join('&')
  }
  return url
}

function unwrap (env) {
  // 网关形状
  if (env && env.state !== undefined) {
    if (env.state === 0) return env.data
    if (env.state === -1) throw new ApiError('UNAUTHORIZED', env.message || '登录已过期', env)
    throw new ApiError(env.state, env.message, env)
  }
  // 业务服务形状
  if (env && env.code !== undefined) {
    if (env.code === '0') return env.data
    throw new ApiError(env.code, env.message, env)
  }
  return env
}

/** 网络层失败的统一文案：把"打到哪"带进消息，便于自查（真机地址过期 / 防火墙拦 / H5 代理没起都走这里） */
function networkError (err) {
  return new ApiError(
    'NETWORK',
    '连不上后端（' + describeTarget() + '）：真机可能是地址过期（已尝试自动查找）、手机与电脑不同网段、或防火墙没放行 7000；H5 要确认业务服务已注册到 Nacos',
    err
  )
}

/**
 * 统一请求；成功 resolve(data)，失败 reject(ApiError)
 * @param {string} service SERVICE 里的键
 * @param {string} path    /user/login 这种（前缀自动拼）
 * @param {object} options { method, query, body, anonymous, loading }
 */
export function request (service, path, options) {
  const opt = options || {}
  const method = opt.method || 'GET'
  const header = {}

  if (opt.body !== undefined) header['Content-Type'] = 'application/json'
  // 网关还没从 JWT 注入 username，前端必须自己带齐两个头
  if (!opt.anonymous) {
    const username = auth.getUsername()
    const token = auth.getToken()
    if (username && token) {
      header.username = username
      header.token = token
    }
  }

  return new Promise(function (resolve, reject) {
    uni.request({
      url: buildUrl(service, path, opt.query),
      method: method,
      header: header,
      data: opt.body,
      // AI 问答这类接口后端要十几秒，调用方可以传 opt.timeout 放宽（默认 15s）
      timeout: opt.timeout || 15000,
      success: function (res) {
        if (res.statusCode === 401 || res.statusCode === 403) {
          // ⚠️ **公开读（anonymous:true）绝不允许清登录态**。
          // 这种请求根本没带身份，401/403 只可能表示"这条路径不对外开放"
          // （网关的 /internal/** 黑名单就是回 HTTP 403），把它当成"登录过期"
          // 会平白把已登录的用户踢下线 —— 一个公开接口就能造成掉线，是结构性隐患。
          // 知识库的 5 个公开读接口正是走 anonymous（实测它们在白名单里一律 200，
          // 这里是结构兜底：就算以后白名单被收窄，也只会"读不到"，不会"被登出"）。
          if (opt.anonymous) {
            reject(new ApiError('FORBIDDEN', '这个接口不对外开放（HTTP ' + res.statusCode + '）'))
            return
          }
          auth.clear()
          reject(new ApiError('UNAUTHORIZED', '登录已过期，请重新登录'))
          return
        }
        try {
          resolve(unwrap(res.data))
        } catch (e) {
          reject(e)
        }
      },
      fail: function (err) {
        // 真机地址过期（换网卡/换 Wi-Fi/换租约）也走这里 → 先自愈：重新找一个地址再重试一次。
        // H5 的 BASE 是空的（走 devServer 代理），没有"换地址"这回事，直接报错。
        if (!BASE || opt.__retried) {
          reject(networkError(err))
          return
        }
        const before = getHost()
        ensureHost().then(function (next) {
          if (!next || next === before) {
            reject(networkError(err))
            return
          }
          BASE = 'http://' + next
          // 换到新地址后重试一次；再失败就是真的不通（__retried 挡住无限重试）
          request(service, path, Object.assign({}, opt, { __retried: true })).then(resolve, reject)
        }, function () {
          reject(networkError(err))
        })
      }
    })
  })
}

export const api = {
  get: function (s, p, o) { return request(s, p, Object.assign({}, o, { method: 'GET' })) },
  post: function (s, p, o) { return request(s, p, Object.assign({}, o, { method: 'POST' })) },
  put: function (s, p, o) { return request(s, p, Object.assign({}, o, { method: 'PUT' })) },
  del: function (s, p, o) { return request(s, p, Object.assign({}, o, { method: 'DELETE' })) }
}