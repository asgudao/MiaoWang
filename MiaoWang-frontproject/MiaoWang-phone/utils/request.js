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
 * baseURL：
 * - H5 开发：用相对路径，由 manifest.json 的 devServer 代理（/miaowang-* → 网关 7000）转发，绕开后端 CORS
 * - 真机/App：localhost 手机访问不到，**必须换成电脑的局域网 IP**（手机与电脑同网段）
 */
/**
 * 网关地址
 *
 * H5：留空走相对路径 → manifest.json 的 devServer 代理（/miaowang-* → 网关 7000），绕开后端 CORS
 * 非 H5（真机 / 小程序）：必须写你电脑的局域网 IP，手机访问不到 localhost
 *
 * 写法说明：**先声明一次、再条件赋值**。若写成两个 `let BASE = ...` 分放在 #ifdef/#ifndef 里，
 * 条件编译没生效时就是"重复声明"硬错误；这种写法无论预处理与否都是合法 JS。
 */
let BASE
// #ifdef H5
BASE = ''
// #endif
// #ifndef H5
// ️ 真机必须写**电脑的局域网 IP**（手机访问不到 localhost），且手机要与电脑在同一个 Wi-Fi。
// 查法：Windows 上 `ipconfig` → 看「无线局域网适配器 WLAN」的 IPv4（**别用 VMware 的 VMnet 地址**）。
// 2026-09-17 本机实测：WLAN = 192.168.225.183。原先写的 192.168.137.1 是 VMware 网段（本机该网段只有 .2），
// 真机必然连不上 —— 这是"连不上后端"的第一大原因。
// 还要确认 Windows 防火墙放行 7000 入站。自检：用**手机浏览器**打开
//   http://192.168.225.183:7000/miaowang-user/mapleleaf/miaowang/v1/user/captcha
// 能返回 JSON 就说明链路通了（返回不了就是 IP / 同网段 / 防火墙三件事之一）。
BASE = 'http://192.168.225.183:7000'
// #endif

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

function buildUrl (service, path, query) {
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
      timeout: 15000,
      success: function (res) {
        if (res.statusCode === 401 || res.statusCode === 403) {
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
        // 真机 BASE 写错 / 防火墙拦了 / H5 代理没起，都走这里 → 把"打到哪"带进消息，便于自查
        reject(new ApiError(
          'NETWORK',
          '连不上后端（' + describeTarget() + '）：真机要确认该 IP 是电脑的局域网地址、手机同 Wi-Fi、防火墙放行 7000；H5 要确认业务服务已注册到 Nacos',
          err
        ))
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