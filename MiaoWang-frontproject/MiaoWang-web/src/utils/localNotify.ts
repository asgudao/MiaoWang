/**
 * 本机（浏览器）通知（Web / TS）：**能力探测 + 真实发送 + 失败如实返回**
 *
 * ============================================================================
 * 先说实话：Web 端**做不到**"关掉标签页也到点响"
 * ============================================================================
 * 核实的真值（与手机端 `MiaoWang-phone/utils/local-notify.js` 同一套结论，官方文档 2026-09-21 查）：
 *
 * | 事实 | 说明 |
 * |---|---|
 * | **关掉标签页 = 永远不会响** | 没有 Service Worker push + 后端推送服务，网页在关闭后没有任何执行机会。**这不是实现问题，是平台边界** |
 * | **必须安全上下文** | `Notification` 在非 https / 非 localhost 下**根本不可用**（MDN：`you cannot request notifications at all unless the site is a secure context`）。本项目 Web 联调是 `http://localhost:5173`（算安全上下文 ✓）；用局域网 IP 的 `http://192.168.x.x:5173` 打开就**不是** |
 * | **必须用户手势** | `Notification.requestPermission()` 在非手势触发时会被浏览器拒绝 → 设置页把它挂在按钮的 `@click` 上，**绝不在 onMounted 里自动申请** |
 * | **手机浏览器 `new Notification()` 会抛 `TypeError`** | MDN 原话：`on most mobile browsers this will throw a TypeError` → 必须退到 `ServiceWorkerRegistration.showNotification()`（SW 文件 `public/mw-notify-sw.js`，**故意不写 fetch 处理器** → 不缓存任何东西） |
 *
 * **`setTimeout` 为什么不能当"排程"**：浏览器会把后台标签页的定时器钳到 ≥1 分钟一次，
 * 关掉页面更是直接消失 → 拿它当闹钟就是"设计一个根本不会响的方案"。
 * 所以 Web 端**没有任何"排给系统"的能力**（`supportsSchedule` 恒 `false`），
 * 只有"页面开着时到点弹一条" + "打开/回前台时检查并补发"。
 *
 * ⚠️ **仍未验证**（Agent 无浏览器）：本文件一次都没在真浏览器里跑过；
 * Service Worker 能否在 Vite dev server 下注册成功、`showNotification` 在手机浏览器上的实际表现，
 * 都只有文档依据。失败时返回的是**可读原因**（`sw-failed` / `denied` / `unsupported`），不会静默。
 */

/** 通知标题（系统通知栏的标题；iOS 低版本忽略标题） */
export const NOTICE_TITLE = '喵汪 · 照护提醒'

/** 默认通知 tag（同 tag 的通知在系统通知栏里会互相替换，避免堆一屏同样的提醒） */
export const DEFAULT_NOTICE_TAG = 'miaowang-remind'

/** Service Worker 路径（相对站点 base，兼容部署在子路径的情况） */
const SW_PATH = 'mw-notify-sw.js'

/** 本端"到点响"到底靠什么 —— 页面文案直接用这一句，**不许各写一份** */
export const WEB_NOTIFY_TRUTH =
  '浏览器端：做不到"关掉页面也到点响"。现在的做法是打开页面时立刻检查一遍、页面还在开着时到点弹一条系统通知；关掉标签页就不会有任何提醒。'

export interface NotifyCapability {
  /** Web 端只有这一个值（本文件就是 Web 的落地，不做条件编译） */
  platform: 'web'
  /** 现在能不能发系统通知（**只看事实，不申请权限**） */
  canNotify: boolean
  /** 能不能把提醒**排给系统** —— 浏览器恒 `false`（关掉标签页没有执行机会） */
  supportsSchedule: boolean
  /** 是不是安全上下文（https / localhost） */
  secureContext: boolean
  /** `granted` / `default` / `denied` / `unsupported` */
  permission: string
  /** 还需要用户点一下授权（`permission === 'default'`） */
  needsGesture: boolean
  /** 实际会用的 API 名 */
  api: string
  /** 不能发时的原因码（页面文案走 `notifyReasonText()`） */
  reason: string
  /** 人话说明（含"关掉标签页不会响"这条硬事实） */
  note: string
}

type Bag = Record<string, unknown>
const bag = (): Bag => globalThis as unknown as Bag

function notificationCtor(): typeof Notification | null {
  const N = bag().Notification
  if (typeof N === 'function' || (N !== null && typeof N === 'object')) return N as typeof Notification
  return null
}

function navigatorObj(): (Navigator & { serviceWorker?: ServiceWorkerContainer }) | null {
  const n = bag().navigator
  return n && typeof n === 'object' ? (n as Navigator) : null
}

function hasServiceWorker(): boolean {
  const n = navigatorObj()
  return !!(n && n.serviceWorker && typeof n.serviceWorker.register === 'function')
}

/**
 * 是不是安全上下文。
 * 优先问运行时的 `isSecureContext`（浏览器真值）；拿不到就按地址推：
 * `https:` / `localhost` / `127.0.0.1` / `[::1]` 算，**局域网 IP 的 http 不算**。
 */
export function isSecureContext(): boolean {
  const g = bag()
  if (typeof g.isSecureContext === 'boolean') return g.isSecureContext
  const loc = g.location as { protocol?: string; hostname?: string } | undefined
  if (!loc) return false
  if (String(loc.protocol || '') === 'https:') return true
  const host = String(loc.hostname || '')
  return host === 'localhost' || host === '127.0.0.1' || host === '[::1]' || host === '::1'
}

/** 当前环境能不能发系统通知（**只回答事实，不申请权限、不做副作用**） */
export function capability(): NotifyCapability {
  const secure = isSecureContext()
  const N = notificationCtor()
  const base = {
    platform: 'web' as const,
    supportsSchedule: false,
    secureContext: secure,
    needsGesture: false,
    api: '',
    reason: '',
    note: '',
  }
  if (!secure) {
    return {
      ...base,
      canNotify: false,
      permission: 'unsupported',
      reason: 'unsupported',
      note: '当前环境拿不到浏览器通知能力：页面不是安全上下文。需要 https，或 http://localhost（用局域网 IP 的 http 地址打开不算）。',
    }
  }
  if (!N) {
    return {
      ...base,
      canNotify: false,
      permission: 'unsupported',
      reason: 'unsupported',
      note: '当前环境拿不到浏览器通知 API（浏览器不支持，或页面不是安全上下文）。这里不会假装能发通知。',
    }
  }
  const perm = String(N.permission || 'default')
  return {
    ...base,
    canNotify: perm === 'granted',
    permission: perm,
    needsGesture: perm === 'default',
    api: hasServiceWorker()
      ? 'Notification / ServiceWorkerRegistration.showNotification'
      : 'Notification',
    reason: perm === 'granted' ? '' : perm,
    note:
      '浏览器端：只有在页面开着的时候才可能响；关掉标签页/长时间切到后台就不会响（网页没有后台执行机会）。' +
      '手机浏览器还需要 Service Worker，且地址必须是 https 或 localhost。',
  }
}

/** 失败原因 → 人话（页面统一走这里，免得各处自造文案） */
export function notifyReasonText(reason: unknown): string {
  const MAP: Record<string, string> = {
    off: '提醒总开关关掉了，不发通知',
    unsupported:
      '当前环境不支持系统通知（需要 https 或 http://localhost，且浏览器要支持通知 API）',
    default: '还没有拿到通知权限，点上面的按钮授权后再试',
    denied: '通知权限被拒绝了：请到浏览器地址栏左侧的站点设置里把「通知」改成允许，再回来重试',
    'sw-failed':
      '这个浏览器不允许页面自己弹通知（手机浏览器必须走 Service Worker），而 Service Worker 没注册成功',
    'not-schedulable':
      '浏览器不能把提醒排给系统：这里只能在你打开页面时提醒，关掉标签页就不会响',
    'not-ready': '系统通知能力还没就绪',
    unknown: '通知没能发出去（原因未知）',
  }
  const k = reason === null || reason === undefined ? '' : String(reason)
  return MAP[k] || ''
}

/* ==================================================================
 * 2. 申请权限（**必须在用户手势里调**）
 * ================================================================== */

export interface PermissionResult {
  ok: boolean
  status: string
  message: string
}

/**
 * 申请系统通知权限：`Notification.requestPermission()`。
 *
 * ⚠️ MDN：浏览器会拒绝非手势触发的授权请求 → 设置页把它挂在按钮的 `@click` 上，
 * **不要在 onMounted 里自动调**（那会被静默拒绝，看起来像"功能坏了"）。
 */
export function ensurePermission(): Promise<PermissionResult> {
  return new Promise<PermissionResult>((resolve) => {
    const cap = capability()
    const N = notificationCtor()
    if (!cap.secureContext || !N) {
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
        message:
          '通知权限被拒绝了。浏览器只会记住这一次拒绝，请到地址栏左侧的站点设置里把「通知」改成允许，再回来重试',
      })
      return
    }
    try {
      const ret = N.requestPermission((status: string) => {
        resolve({
          ok: status === 'granted',
          status: String(status || 'default'),
          message:
            status === 'granted'
              ? '系统通知已授权'
              : notifyReasonText(status === 'denied' ? 'denied' : 'default'),
        })
      })
      // 新版浏览器返回 Promise；旧版返回 undefined（走上面的回调）
      if (ret && typeof (ret as Promise<NotificationPermission>).then === 'function') {
        ;(ret as Promise<NotificationPermission>).then(
          (status) => {
            resolve({
              ok: status === 'granted',
              status: String(status || 'default'),
              message:
                status === 'granted'
                  ? '系统通知已授权'
                  : notifyReasonText(status === 'denied' ? 'denied' : 'default'),
            })
          },
          () => {
            resolve({ ok: false, status: 'default', message: '授权请求被浏览器打断，再点一次试试' })
          },
        )
      }
    } catch (e) {
      resolve({
        ok: false,
        status: 'error',
        message: `申请通知权限时出错：${e instanceof Error ? e.message : String(e)}`,
      })
    }
  })
}

/* ==================================================================
 * 3. 发一条通知（**唯一出口，绝不抛异常**）
 * ================================================================== */

function swRegister(): Promise<ServiceWorkerRegistration | null> {
  return new Promise<ServiceWorkerRegistration | null>((resolve) => {
    const n = navigatorObj()
    if (!n || !hasServiceWorker()) {
      resolve(null)
      return
    }
    const sw = n.serviceWorker as ServiceWorkerContainer
    let base = ''
    try {
      const doc = bag().document as { baseURI?: string } | undefined
      base = doc && doc.baseURI ? doc.baseURI : ''
    } catch {
      base = ''
    }
    let url = SW_PATH
    try {
      url = base ? new URL(SW_PATH, base).href : SW_PATH
    } catch {
      url = SW_PATH
    }
    sw.getRegistration().then(
      (reg) => {
        if (reg) {
          resolve(reg)
          return
        }
        sw.register(url).then(
          (r) => resolve(r),
          () => resolve(null),
        )
      },
      () => resolve(null),
    )
  })
}

export interface LocalNoticeMessage {
  title?: string
  body?: string
  /** 同 tag 的通知会互相替换（避免堆一屏同样的提醒） */
  tag?: string
  /**
   * 手机端 App 用原生 `delay` 把通知排给系统；**浏览器没有这个能力** →
   * 传了正数会**如实失败**（`not-schedulable`），绝不假装排上了。
   */
  delaySeconds?: number
}

export interface LocalNoticeResult {
  ok: boolean
  via: string
  reason: string
  message: string
}

/**
 * 发一条**本机**通知。
 *
 * @returns **永远 resolve**（不 reject）：调用方按 `ok` 分流，页面把 `message` 如实显示给用户
 */
export function sendLocalNotice(msg?: LocalNoticeMessage): Promise<LocalNoticeResult> {
  const m = msg || {}
  const title = String(m.title || NOTICE_TITLE)
  const body = String(m.body || '')
  const tag = String(m.tag || DEFAULT_NOTICE_TAG)

  return new Promise<LocalNoticeResult>((resolve) => {
    if (Number(m.delaySeconds) > 0) {
      // 浏览器做不到"排给系统"：如实失败，不假装（关掉标签页没有执行机会）
      resolve({
        ok: false,
        via: 'web',
        reason: 'not-schedulable',
        message: notifyReasonText('not-schedulable'),
      })
      return
    }

    const cap = capability()
    const N = notificationCtor()
    if (!cap.canNotify || !N) {
      const key = cap.reason || 'unsupported'
      const reasonText = notifyReasonText(key)
      const useReasonText = (key === 'denied' || key === 'default') && reasonText
      resolve({
        ok: false,
        via: 'web',
        reason: key,
        message: useReasonText ? reasonText : cap.note,
      })
      return
    }

    const options: NotificationOptions = { body, tag }
    // 桌面浏览器：直接构造。手机浏览器会抛 TypeError（MDN 明确写了）→ 退到 Service Worker
    try {
      // 桌面浏览器上这条通知会一直挂着，用户点掉即可；这里不做自动关闭（没有可靠的关闭时机）
      // eslint-disable-next-line no-new -- 构造出来就已经弹了，返回值没有用途
      new N(title, options)
      resolve({ ok: true, via: 'web-Notification', reason: '', message: '浏览器已弹出系统通知' })
      return
    } catch {
      /* fallthrough → Service Worker */
    }
    swRegister().then((reg) => {
      if (!reg || typeof reg.showNotification !== 'function') {
        resolve({
          ok: false,
          via: 'web',
          reason: 'sw-failed',
          message: notifyReasonText('sw-failed'),
        })
        return
      }
      reg.showNotification(title, options).then(
        () => {
          resolve({
            ok: true,
            via: 'web-showNotification',
            reason: '',
            message: '已通过 Service Worker 弹出系统通知',
          })
        },
        (err: unknown) => {
          resolve({
            ok: false,
            via: 'web-showNotification',
            reason: 'unknown',
            message: `showNotification 失败：${err instanceof Error ? err.message : String(err)}`,
          })
        },
      )
    })
  })
}

/** 本端"到点响"到底靠什么 —— 页面文案直接用这一句（写错就是把"打开页面才检查"说成"到点自动提醒"） */
export function scheduleTruthText(): string {
  return WEB_NOTIFY_TRUTH
}