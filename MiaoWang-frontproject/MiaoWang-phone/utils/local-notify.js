/**
 * 本机通知（手机端 / JS）：**能力探测 + 真实发送 + 失败如实返回**
 *
 * ============================================================================
 * 先说实话：这个项目**做不到**"关掉 App 也到点响"的全平台方案
 * ============================================================================
 * 核实的真值（**官方文档，2026-09-21 查**；URL 见每条结论后的注释）：
 *
 * | 端 | 有没有"到点自己响"的真 API | 结论 |
 * |---|---|---|
 * | **App（Android / iOS）** | **有**：`uni.createPushMessage`（HBuilderX 3.5.2+，官方平台表 **App √ / H5 x / 小程序全 x**），带 `delay`（秒，原生层延迟显示） | 走它 |
 * | **H5（浏览器）** | **部分**：只能 `Notification` / `ServiceWorkerRegistration.showNotification()`，**页面关掉后不可能触发** | 如实降级 |
 * | 微信小程序 | 需要"订阅消息"，且本工程 `mp-weixin.appid` 为空（跑不起来） | 不支持 |
 *
 * 三条**硬事实**（都会在页面文案里如实写出来，不许含糊）：
 *   1. **H5 关掉标签页 = 永远不会响**。没有 Service Worker push + 后端推送服务，
 *      网页在关闭后没有任何执行机会 —— 这不是实现问题，是平台的边界。
 *      → H5 的降级行为：**打开 App（页面）时检查一遍 + 页内提示**。
 *   2. **H5 还要求安全上下文**：`Notification` 在非 https / 非 localhost 下**根本不可用**
 *      （MDN：`you cannot request notifications at all unless the site is a secure context`）。
 *      本工程 H5 联调是 `http://localhost:5174`（`manifest.json` 的 devServer.port）→ **localhost 算安全上下文** ✓；
 *      但**手机浏览器用局域网 IP（`http://192.168.x.x:5174`）打开就不是** → 如实报"当前环境不可用"。
 *   3. **手机浏览器里 `new Notification()` 会抛 `TypeError`**（MDN 原话：`on most mobile browsers
 *      this will throw a TypeError`）→ 必须注册 Service Worker 走
 *      `ServiceWorkerRegistration.showNotification()`。本文件两条路都实现：
 *      先试构造函数（桌面浏览器可用、免 SW），抛了就退到 SW。
 *      SW 文件是 `static/mw-notify-sw.js`（**故意不写 fetch 处理器** → 不缓存任何东西，不会干扰 H5 调试）。
 *
 * **`setTimeout` 为什么不能当"排程"**：App 切后台后 webview 的定时器会被系统挂起/节流，
 * H5 后台标签页也被 Chrome 钳到 ≥1 分钟一次 → **拿它当闹钟就是"设计一个在真机上根本不会响的方案"**。
 * 所以本文件的排程只有一个真手段：**App 端把 `delay` 交给原生层**（`uni.createPushMessage` 的 `delay` 秒）；
 * 其它情况一律"打开/回前台时检查并补发"，页面如实说明。
 *
 * ⚠️ **仍然未验证**（Agent 无真机、无浏览器）：
 *   - `delay` 在 App **被系统杀进程后**是否仍触发 —— 官方文档只承诺"延迟显示"，**没有承诺进程死亡后仍触发**
 *   - Android 13+ 的 `POST_NOTIFICATIONS` 运行时权限：本工程 `manifest.json` 的
 *     `app-plus.distribute.android.permissions` 列表里**没有**这条（实测读配置），云打包前需要补
 *   - 本文件**一次都没在真机上跑过**
 */

/** 当前平台：`'app'` / `'h5'` / `'unknown'`（条件编译，写法与 `utils/request.js` 的 BASE 一致） */
let PLATFORM = 'unknown'
// #ifdef APP-PLUS
PLATFORM = 'app'
// #endif
// #ifdef H5
PLATFORM = 'h5'
// #endif

export function platform () {
  return PLATFORM
}

/** Service Worker 路径（相对页面 base，兼容 H5 部署在子路径的情况） */
const SW_PATH = 'static/mw-notify-sw.js'

/** 通知标题（App 端系统通知栏的标题；iOS 低版本忽略标题，固定显示应用名） */
export const NOTICE_TITLE = '喵汪 · 照护提醒'

/* ==================================================================
 * 1. 能力探测（**不做任何副作用**，页面加载就能安全调）
 * ================================================================== */

function h5Notification () {
  if (typeof window === 'undefined' || !window) return null
  return typeof window.Notification === 'function' || typeof window.Notification === 'object'
    ? window.Notification
    : null
}

function hasServiceWorker () {
  return typeof navigator !== 'undefined' && !!navigator && !!navigator.serviceWorker
}

/**
 * 当前环境能不能发系统通知（**只回答事实，不申请权限**）
 *
 * @returns {{platform:string, canNotify:boolean, supportsSchedule:boolean, permission:string,
 *            needsGesture:boolean, api:string, reason:string, note:string}}
 *   `supportsSchedule` 单独一个字段：**能把提醒排给系统**（App 的 `delay`）与
 *   "现在能弹一条"是两件事，页面要分别如实说
 */
export function capability () {
  if (PLATFORM === 'app') {
    const hasUni = typeof uni !== 'undefined' && uni && typeof uni.createPushMessage === 'function'
    const hasPlus = typeof plus !== 'undefined' && plus && plus.push && typeof plus.push.createMessage === 'function'
    if (!hasUni && !hasPlus) {
      return {
        platform: 'app',
        canNotify: false,
        supportsSchedule: false,
        permission: 'unknown',
        needsGesture: false,
        api: '',
        reason: 'no-api',
        note: '这个 App 基座里没有本地通知 API（uni.createPushMessage / plus.push 都取不到）。请用 HBuilderX 3.5.2 及以上的标准基座，云打包时勾选「Push 消息推送」模块'
      }
    }
    return {
      platform: 'app',
      canNotify: true,
      // **只有 App 端能把提醒排给系统**（`uni.createPushMessage` 的 delay，原生层延迟显示）
      supportsSchedule: true,
      // App 端**没有官方的"申请通知权限"JS API** → 状态只能报"由系统管"，不假装知道
      permission: 'system',
      needsGesture: false,
      api: hasUni ? 'uni.createPushMessage' : 'plus.push.createMessage',
      reason: '',
      note: 'App 端用系统通知栏的本地通知。通知权限由系统管：Android 13 及以上要在系统设置里允许「喵汪」发通知，iOS 首次会弹授权框。' +
        '本工程 manifest 还没声明 Android 13 的 POST_NOTIFICATIONS，云打包前需要补一条'
    }
  }
  if (PLATFORM === 'h5') {
    const N = h5Notification()
    if (!N) {
      return {
        platform: 'h5',
        canNotify: false,
        supportsSchedule: false,
        permission: 'unsupported',
        needsGesture: false,
        api: '',
        reason: 'unsupported',
        note: '当前环境拿不到浏览器通知 API。常见原因：页面不是安全上下文（需要 https，或 localhost；用局域网 IP 的 http 地址打开就不算），或浏览器不支持'
      }
    }
    const perm = String(N.permission || 'default')
    return {
      platform: 'h5',
      canNotify: perm === 'granted',
      // 网页没法把提醒排给系统（关掉标签页就没有执行机会）→ 只能页面开着时用定时器
      supportsSchedule: false,
      permission: perm,
      needsGesture: perm === 'default',
      api: hasServiceWorker() ? 'Notification / ServiceWorkerRegistration.showNotification' : 'Notification',
      reason: perm === 'granted' ? '' : perm,
      note: '浏览器环境：**只有在页面开着的时候才可能响**；关掉标签页/长时间切到后台就不会响（网页没有后台执行机会）。' +
        '手机浏览器还需要 Service Worker，且地址必须是 https 或 localhost'
    }
  }
  return {
    platform: 'unknown',
    canNotify: false,
    supportsSchedule: false,
    permission: 'unsupported',
    needsGesture: false,
    api: '',
    reason: 'platform',
    note: '这个平台没有本机通知能力（小程序要「订阅消息」，且本工程 mp-weixin.appid 为空、跑不起来）。本页只做本机设置，不会假装能响'
  }
}

/** 失败原因 → 人话（页面统一走这里，免得各处自造文案） */
export function notifyReasonText (reason) {
  const MAP = {
    off: '提醒总开关关掉了，不发通知',
    'no-api': '当前基座没有本地通知 API',
    unsupported: '当前环境不支持系统通知（需要 https 或 localhost，且浏览器要支持）',
    default: '还没有拿到通知权限，点上面的按钮授权后再试',
    denied: '通知权限被拒绝了：请到浏览器/系统设置里重新允许「喵汪」发通知',
    'sw-failed': '注册 Service Worker 失败，手机浏览器上就没法发通知了',
    'not-ready': '系统通知能力还没就绪',
    unknown: '通知没能发出去（原因未知）'
  }
  const k = reason === null || reason === undefined ? '' : String(reason)
  return MAP[k] || ''
}

/* ==================================================================
 * 2. 申请权限（H5 才有；App 交给系统）
 * ================================================================== */

/**
 * 申请通知权限。
 *
 * - H5：`Notification.requestPermission()`。**必须在用户手势里调**（MDN：浏览器会拒绝
 *   非手势触发的授权请求）→ 页面把它挂在按钮的 `@click` 上，不要在 `onLoad` 里自动调。
 * - App：没有官方 JS API → 返回 `status: 'system'`，**不假装成功也不假装失败**。
 * @returns {Promise<{ok:boolean, status:string, message:string}>}
 */
export function ensurePermission () {
  return new Promise(function (resolve) {
    const cap = capability()
    if (PLATFORM === 'h5') {
      const N = h5Notification()
      if (!N) {
        resolve({ ok: false, status: 'unsupported', message: cap.note })
        return
      }
      if (N.permission === 'granted') {
        resolve({ ok: true, status: 'granted', message: '系统通知已授权' })
        return
      }
      if (N.permission === 'denied') {
        resolve({
          ok: false,
          status: 'denied',
          message: '通知权限被拒绝了。浏览器只会记住这一次拒绝，请到地址栏左侧的站点设置里把「通知」改成允许，再回来重试'
        })
        return
      }
      try {
        const ret = N.requestPermission(function (status) {
          resolve({
            ok: status === 'granted',
            status: String(status || 'default'),
            message: status === 'granted' ? '系统通知已授权' : notifyReasonText(status === 'denied' ? 'denied' : 'default')
          })
        })
        // 新版浏览器返回 Promise；旧版返回 undefined（走上面的回调）
        if (ret && typeof ret.then === 'function') {
          ret.then(
            function (status) {
              resolve({
                ok: status === 'granted',
                status: String(status || 'default'),
                message: status === 'granted' ? '系统通知已授权' : notifyReasonText(status === 'denied' ? 'denied' : 'default')
              })
            },
            function () {
              resolve({ ok: false, status: 'default', message: '授权请求被浏览器打断，再点一次试试' })
            }
          )
        }
      } catch (e) {
        resolve({ ok: false, status: 'error', message: '申请通知权限时出错：' + ((e && e.message) || e) })
      }
      return
    }
    if (PLATFORM === 'app') {
      resolve({
        ok: cap.canNotify,
        status: cap.permission,
        message: cap.note
      })
      return
    }
    resolve({ ok: false, status: 'unsupported', message: cap.note })
  })
}

/* ==================================================================
 * 3. 发一条通知（**唯一出口**，绝不抛异常）
 * ================================================================== */

function swRegister () {
  return new Promise(function (resolve) {
    if (!hasServiceWorker()) {
      resolve(null)
      return
    }
    let base = ''
    try {
      base = typeof document !== 'undefined' && document.baseURI ? document.baseURI : ''
    } catch (e) {
      base = ''
    }
    let url = SW_PATH
    try {
      url = base ? new URL(SW_PATH, base).href : SW_PATH
    } catch (e) {
      url = SW_PATH
    }
    navigator.serviceWorker.getRegistration().then(
      function (reg) {
        if (reg) {
          resolve(reg)
          return
        }
        navigator.serviceWorker.register(url).then(
          function (r) {
            resolve(r)
          },
          function () {
            resolve(null)
          }
        )
      },
      function () {
        resolve(null)
      }
    )
  })
}

/**
 * 发一条**本机**通知。
 *
 * @param {object} msg `{ title, content, payload, delaySeconds, tag }`
 *   `delaySeconds > 0` 时（**只有 App 端支持**）交给原生层延迟显示 —— 这是本项目唯一的"真排程"
 * @returns {Promise<{ok:boolean, via:string, reason:string, message:string, delaySeconds:number}>}
 *   **永远 resolve**（不 reject）：调用方按 `ok` 分流，页面把 `message` 如实显示给用户
 */
export function sendLocalNotice (msg) {
  const m = msg || {}
  const title = String(m.title || NOTICE_TITLE)
  const content = String(m.content || '')
  const payload = m.payload === undefined ? null : m.payload
  const delaySeconds = Number(m.delaySeconds) > 0 ? Math.round(Number(m.delaySeconds)) : 0

  return new Promise(function (resolve) {
    const cap = capability()
    if (!cap.canNotify) {
      /**
       * ⚠️ 文案要**按原因分开**，不能一律甩 `cap.note`：
       * H5 的 `cap.note` 讲的是"关掉标签页就不响 / 要 https"，对"你刚才拒绝了通知权限"这种情形
       * 一点忙都帮不上（用户照着找不着门）。权限类原因走 `notifyReasonText()` 那套可操作的话。
       */
      const key = cap.reason || 'unsupported'
      const reasonText = notifyReasonText(key)
      const useReasonText = (key === 'denied' || key === 'default') && reasonText
      resolve({
        ok: false,
        via: cap.platform,
        reason: key,
        message: useReasonText ? reasonText : cap.note,
        delaySeconds: delaySeconds
      })
      return
    }

    if (PLATFORM === 'app') {
      const hasUni = typeof uni !== 'undefined' && uni && typeof uni.createPushMessage === 'function'
      if (hasUni) {
        try {
          uni.createPushMessage({
            title: title,
            content: content,
            payload: payload,
            sound: 'system',
            delay: delaySeconds,
            cover: false,
            success: function () {
              resolve({ ok: true, via: 'uni.createPushMessage', reason: '', message: '已交给系统通知栏', delaySeconds: delaySeconds })
            },
            fail: function (err) {
              const t = (err && (err.errMsg || err.message)) || '未知原因'
              resolve({
                ok: false,
                via: 'uni.createPushMessage',
                reason: 'unknown',
                message: '系统没接受这条本地通知：' + t + '（Android 13 及以上请确认已允许喵汪发通知）',
                delaySeconds: delaySeconds
              })
            }
          })
        } catch (e) {
          resolve({
            ok: false,
            via: 'uni.createPushMessage',
            reason: 'unknown',
            message: '调本地通知时抛错：' + ((e && e.message) || e),
            delaySeconds: delaySeconds
          })
        }
        return
      }
      // 退路：5+ 底层 API（uni.createPushMessage 就是它的封装）
      try {
        plus.push.createMessage(content, payload, {
          title: title,
          sound: 'system',
          cover: false,
          delay: delaySeconds
        })
        resolve({ ok: true, via: 'plus.push.createMessage', reason: '', message: '已交给系统通知栏', delaySeconds: delaySeconds })
      } catch (e) {
        resolve({
          ok: false,
          via: 'plus.push.createMessage',
          reason: 'unknown',
          message: '调 plus.push.createMessage 抛错：' + ((e && e.message) || e),
          delaySeconds: delaySeconds
        })
      }
      return
    }

    if (PLATFORM === 'h5') {
      const N = h5Notification()
      if (!N) {
        resolve({ ok: false, via: 'h5', reason: 'unsupported', message: cap.note, delaySeconds: delaySeconds })
        return
      }
      if (N.permission !== 'granted') {
        const reason = N.permission === 'denied' ? 'denied' : 'default'
        resolve({ ok: false, via: 'h5', reason: reason, message: notifyReasonText(reason), delaySeconds: delaySeconds })
        return
      }
      const options = { body: content, tag: String(m.tag || 'miaowang-remind') }
      // 桌面浏览器：直接构造。手机浏览器会抛 TypeError（MDN 明确写了）→ 退到 Service Worker
      try {
        const n = new N(title, options)
        if (n && typeof n.close === 'function' && m.autoCloseMs > 0) setTimeout(function () { try { n.close() } catch (e) {} }, Number(m.autoCloseMs))
        resolve({ ok: true, via: 'h5-Notification', reason: '', message: '浏览器已弹出系统通知', delaySeconds: 0 })
        return
      } catch (e) {
        // fallthrough → SW
      }
      swRegister().then(function (reg) {
        if (!reg || typeof reg.showNotification !== 'function') {
          resolve({
            ok: false,
            via: 'h5',
            reason: 'sw-failed',
            message: '这个浏览器不允许页面自己弹通知（手机浏览器必须走 Service Worker），而 Service Worker 没注册成功',
            delaySeconds: delaySeconds
          })
          return
        }
        reg.showNotification(title, options).then(
          function () {
            resolve({ ok: true, via: 'h5-showNotification', reason: '', message: '已通过 Service Worker 弹出系统通知', delaySeconds: 0 })
          },
          function (err) {
            resolve({
              ok: false,
              via: 'h5-showNotification',
              reason: 'unknown',
              message: 'showNotification 失败：' + ((err && err.message) || err),
              delaySeconds: delaySeconds
            })
          }
        )
      })
      return
    }

    resolve({ ok: false, via: PLATFORM, reason: 'platform', message: cap.note, delaySeconds: delaySeconds })
  })
}

/**
 * 本端"到点响"到底靠什么 —— 页面文案直接用这一句，**不许各写一份**
 * （写错就是把"打开 App 才检查"说成"到点自动提醒"）
 */
export function scheduleTruthText () {
  if (PLATFORM === 'app') {
    return 'App 端：到点提醒由系统通知栏负责（本地通知 + 延迟显示）。每次打开 App、从后台回前台、改设置之后都会重算一遍并重新排给系统；' +
      'App 被系统杀进程后还能不能响，官方文档没有承诺，本机未在真机上验证。'
  }
  if (PLATFORM === 'h5') {
    return '浏览器端：做不到"关掉页面也到点响"。现在的做法是打开页面时立刻检查一遍、页面开着时到点弹系统通知；关掉标签页就不会有任何提醒。'
  }
  return '当前平台没有本机通知能力，这里只保存设置，不会响。'
}