/**
 * 后端地址解析（手机端 App 直连用）
 *
 * 为什么需要它：App 直连时后端地址是**电脑的局域网 IP**，它会变 ——
 * DHCP 换租约、换 Wi-Fi、无线切有线、开热点，都会换地址；写死就必然出现
 * "昨天还能连，今天突然连不上，放行防火墙也没用"。所以地址按这个顺序解析：
 *
 *   1. 用户手填的（uni storage，优先级最高 —— 任何时候都能手动救回来）
 *   2. 上次探测成功 / 自动发现的地址（同一份 storage）
 *   3. 内置候选（本机历史上真用过的地址）
 *   4. 自动发现：取候选的前三段，扫 1~254 的网关端口，谁先应答就用谁
 *
 * 找到就写回 storage，下次启动直接命中。请求层在网络失败时会调 ensureHost() 自愈一次。
 *
 * H5 不走这套：H5 由 manifest.json 的 devServer 代理转发（/miaowang-* → 127.0.0.1:7000），
 * 所以 getBase() 在 H5 下返回 ''（相对路径）。
 *
 * ⚠️ 本文件用 uni.request 探测，不依赖任何 npm 包（工程约束：无 npm / 无 TS / 无 Pinia）。
 */

/** 网关端口：固定不变（后端所有服务都从网关 7000 进） */
export const PORT = 7000

const HOST_KEY = 'mw_host'
const MANUAL_KEY = 'mw_host_manual'

/** 探测端点：验证码接口免登录、不写库，最适合当"后端活着吗"的探针 */
const PROBE_PATH = '/miaowang-user/mapleleaf/miaowang/v1/user/captcha'
const PROBE_TIMEOUT = 1500
/** 扫描并发度：太大手机会卡，太小太慢；24 路在局域网里通常 1~3 秒出结果 */
const SCAN_CONCURRENCY = 24

/** 本机真用过的地址，按可能性排序（以太网 / 电脑热点 / 曾经的 WLAN） */
const KNOWN_HOSTS = [
  '192.168.100.45:7000',
  '192.168.137.1:7000',
  '192.168.225.183:7000'
]

/** 自动发现进行中时的共享 Promise：避免多处同时触发导致扫好几遍 */
let discovering = null

/**
 * 把用户输入/历史记录规整成 `IP:端口`
 * 允许输入：`192.168.1.5` / `192.168.1.5:7000` / `http://192.168.1.5:7000/`
 */
export function normalizeHost (input) {
  let s = String(input || '').trim()
  if (!s) return ''
  s = s.replace(/^https?:\/\//i, '')
  s = s.replace(/\/+$/, '')
  if (s.indexOf('/') >= 0) s = s.split('/')[0]
  if (!s) return ''
  if (s.indexOf(':') < 0) s = s + ':' + PORT
  // 只认 IPv4 或主机名，挡掉明显写错的输入（比如带空格、带中文）
  if (!/^[A-Za-z0-9.-]+:\d{1,5}$/.test(s)) return ''
  const p = Number(s.split(':')[1])
  if (!(p > 0 && p < 65536)) return ''
  return s
}

function prefixOf (host) {
  const ip = String(host || '').split(':')[0]
  const parts = ip.split('.')
  if (parts.length !== 4) return ''
  return parts[0] + '.' + parts[1] + '.' + parts[2] + '.'
}

export function getManualHost () {
  return normalizeHost(uni.getStorageSync(MANUAL_KEY))
}

export function setManualHost (input) {
  const h = normalizeHost(input)
  if (!h) return ''
  uni.setStorageSync(MANUAL_KEY, h)
  uni.setStorageSync(HOST_KEY, h)
  return h
}

export function clearManualHost () {
  uni.removeStorageSync(MANUAL_KEY)
}

/** 当前生效的 host（不含协议）。没有手填也没探测过时，用内置候选里第一个 */
export function getHost () {
  return getManualHost() || normalizeHost(uni.getStorageSync(HOST_KEY)) || KNOWN_HOSTS[0]
}

/** 请求层用的前缀：H5 留空走代理，App 直连拼 http://（写法与 request.js 的 describeTarget 一致） */
export function getBase () {
  // #ifdef H5
  return ''
  // #endif
  // #ifndef H5
  return 'http://' + getHost()
  // #endif
}

/**
 * 探一个地址是否活着
 * @returns {Promise<boolean>}
 */
export function probe (host) {
  const h = normalizeHost(host)
  if (!h) return Promise.resolve(false)
  return new Promise(function (resolve) {
    uni.request({
      url: 'http://' + h + PROBE_PATH,
      method: 'GET',
      timeout: PROBE_TIMEOUT,
      success: function (res) {
        // 只要 HTTP 层通了就算活（业务码不重要：网关/服务返回什么信封都行）
        resolve(res.statusCode >= 200 && res.statusCode < 500)
      },
      fail: function () {
        resolve(false)
      }
    })
  })
}

/** 扫一个 /24 网段，返回第一个应答的 host（扫到就停，不再等剩下的） */
function scanPrefix (prefix) {
  if (!prefix) return Promise.resolve('')
  return new Promise(function (resolve) {
    let next = 1
    let pending = 0
    let done = false

    function launch () {
      if (done) return
      if (next > 254) {
        if (pending === 0) resolve('')
        return
      }
      const host = prefix + next
      next += 1
      pending += 1
      probe(host).then(function (ok) {
        pending -= 1
        if (ok && !done) {
          done = true
          resolve(host + ':' + PORT)
          return
        }
        launch()
      })
    }

    for (let i = 0; i < SCAN_CONCURRENCY; i++) launch()
  })
}

/**
 * 自动发现后端。
 * 先按可能性探已知候选（快），都不通再扫当前候选所在网段（慢但兜底）。
 * @returns {Promise<string>} 找到的 host，没找到返回 ''
 */
export function discover () {
  if (discovering) return discovering

  discovering = (async function () {
    const candidates = []
    const manual = getManualHost()
    const stored = normalizeHost(uni.getStorageSync(HOST_KEY))
    if (manual) candidates.push(manual)
    if (stored && candidates.indexOf(stored) < 0) candidates.push(stored)
    KNOWN_HOSTS.forEach(function (h) {
      if (candidates.indexOf(h) < 0) candidates.push(h)
    })

    // ① 候选直探
    for (let i = 0; i < candidates.length; i++) {
      // eslint-disable-next-line no-await-in-loop
      const ok = await probe(candidates[i])
      if (ok) {
        uni.setStorageSync(HOST_KEY, candidates[i])
        return candidates[i]
      }
    }

    // ② 扫网段（用最可能的前缀：手填/历史 > 内置第一个）
    const prefixes = []
    const baseForPrefix = manual || stored || KNOWN_HOSTS[0]
    const p1 = prefixOf(baseForPrefix)
    if (p1) prefixes.push(p1)
    const p2 = prefixOf(KNOWN_HOSTS[0])
    if (p2 && prefixes.indexOf(p2) < 0) prefixes.push(p2)

    for (let i = 0; i < prefixes.length; i++) {
      // eslint-disable-next-line no-await-in-loop
      const found = await scanPrefix(prefixes[i])
      if (found) {
        uni.setStorageSync(HOST_KEY, found)
        return found
      }
    }
    return ''
  })()

  // 无论成功失败都释放锁，允许下次重试
  const settle = function () {
    discovering = null
  }
  discovering.then(settle, settle)
  return discovering
}

/**
 * 自愈入口：当前地址活着就直接返回；不活就自动发现一个。
 * 请求层在网络失败时调它，然后拿返回的新地址重试一次。
 * @returns {Promise<string>} 可用 host（'' 表示实在找不到）
 */
export async function ensureHost () {
  const current = getHost()
  if (await probe(current)) return current
  return discover()
}