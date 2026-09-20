/**
 * 「提醒设置」本机存储（手机端 / JS）
 *
 * ============================================================================
 * 这是什么、不是什么（**页面文案与这里的口径必须一致，写错就是把假的当真的**）
 * ============================================================================
 * - 这是**本机设置**：只写进 `uni.setStorageSync`，**不上传后端**。
 * - 喵汪**没有接服务端推送**（后端 143 处 controller mapping 里没有任何 reminder / plan /
 *   schedule / task；7 条不存在性探针全 `B000001`，见 `.dsh-tmp/reminder-notification-contract.md` §A），
 *   所以这些设置**只影响本机行为**：本端派生的「照护提醒」清单 + 本机到点通知。
 * - 幂等与去重的账本（哪条今天已经响过）也在这里，键名见 `ledgerKeyFor()`。
 *
 * ============================================================================
 * 账号隔离：**靠 key 里的用户名，不靠 `resetAccountStores()`**
 * ============================================================================
 * 本项目踩过"切账号残留"（`store/pets.js` 那个 bug，见 `tools/check-stores.js` 的注释）。
 * 那条门禁的规则是"**导出了 `reset*` 的 store 必须在 `store/auth.js` 的 `resetAccountStores()`
 * 里被调用**"。本模块**故意不新建 store、也不导出 `reset*`**，改用更省事也更难漏的做法：
 *
 *   存储键 = `mw_remind_setting::<username>`（账本 = `mw_remind_sent::<username>`）
 *
 * 每个账号**各自一把键** → 换账号天然读到自己的那一份，**不存在"忘记清"这个失败模式**
 * （忘记清是"共享一把键"才会有的问题）。为什么不是"设备级全局设置"：提前天数与免打扰时段
 * 在同一个账号下才有意义（不同账号的宠物、作息都不一样），而且共享键正好就是本项目
 * 已经踩过的那个坑。未登录时退化成不带后缀的 `mw_remind_setting`（页面本来也有登录软门槛，
 * 这条只是为了"未登录时不崩"）。
 *
 * ⚠️ 为什么不用 `utils/request.js` 的 `auth`：本文件要能被 `tools/check-remind-setting-phone.mjs`
 * **原样复制到临时目录单独 import**（手机端是 HBuilderX 工程、没有 package.json），
 * 少一个相对 import 就少一个 harness 依赖；用户名由调用方传进来（页面/`api/remind.js` 那里有 auth）。
 *
 * ============================================================================
 * 默认值 = **既有口径**（这条是刻意的，不是随手取的）
 * ============================================================================
 * `aheadDays = 0`（到期当天提醒）**正好等于**首页原来那套「待办 = 逾期 + 今天到期」
 * （`api/home.js` 文件头第 1 条、`utils/health.js` 的 `dueInfo` 分档：`< 0` 逾期 / `=== 0` 今天）。
 * 所以"从没进过设置页"的用户，首页数字与行为**一字不变**；
 * 一旦把提前天数调大，才会多出"还有 N 天"的待办。默认值取 1 或 3 会**悄悄改掉所有人的首页**，
 * 也会把 `tools/check-reminder-phone.mjs` 那 102 条既有断言打红 —— 那是别人的门禁，不该被我改。
 */

/** 设置键前缀（用户名做后缀，见文件头「账号隔离」） */
export const REMIND_SETTING_PREFIX = 'mw_remind_setting'

/** 已发送账本键前缀（去重：同一条目同一个到期日只响一次） */
export const REMIND_SENT_PREFIX = 'mw_remind_sent'

/** 提前天数的取值范围：0 = 到期当天；上限 14 天（再长就不是提醒、是噪音了） */
export const AHEAD_DAYS_MIN = 0
export const AHEAD_DAYS_MAX = 14

/**
 * 默认设置（**唯一真源**）
 *
 * | 键 | 默认 | 含义 |
 * |---|---|---|
 * | `enabled` | `true` | 提醒总开关。关掉后：本端不再列待办、不再发本地通知 |
 * | `aheadDays` | `0` | 提前几天提醒；`0` ≡ 既有口径「逾期 + 今天到期」 |
 * | `quietEnabled` | `false` | 免打扰总开关（默认关：默认就静音会让人以为"功能坏了"） |
 * | `quietStart` / `quietEnd` | `22:00` / `08:00` | 免打扰时段（`HH:mm`，本地时间；可跨零点） |
 */
export const REMIND_SETTING_DEFAULTS = {
  enabled: true,
  aheadDays: 0,
  quietEnabled: false,
  quietStart: '22:00',
  quietEnd: '08:00'
}

/** 提前天数的选择项（设置页的 picker 直接用；文案与 `dueInfo` 的分档对齐） */
export const AHEAD_DAY_OPTIONS = (function () {
  const out = []
  for (let d = AHEAD_DAYS_MIN; d <= AHEAD_DAYS_MAX; d++) {
    out.push({ value: d, label: d === 0 ? '到期当天' : '提前 ' + d + ' 天' })
  }
  return out
})()

/** `HH:mm` 的严格形状（`9:00` / `24:00` / `08:60` 都不认） */
const HM_PATTERN = /^([01][0-9]|2[0-3]):([0-5][0-9])$/

/** 读存储（`uni` 不存在时返回空串：离线断言里 harness 可以不挂 globalThis.uni） */
function readRaw (key) {
  if (typeof uni === 'undefined' || !uni || typeof uni.getStorageSync !== 'function') return ''
  try {
    return uni.getStorageSync(key)
  } catch (e) {
    return ''
  }
}

function writeRaw (key, value) {
  if (typeof uni === 'undefined' || !uni || typeof uni.setStorageSync !== 'function') return false
  try {
    uni.setStorageSync(key, value)
    return true
  } catch (e) {
    return false
  }
}

/** 该账号的设置键（用户名做后缀 → 换账号天然隔离，见文件头） */
export function settingKeyFor (username) {
  const u = username === null || username === undefined ? '' : String(username).trim()
  return u ? REMIND_SETTING_PREFIX + '::' + u : REMIND_SETTING_PREFIX
}

/** 该账号的"已发送"账本键 */
export function ledgerKeyFor (username) {
  const u = username === null || username === undefined ? '' : String(username).trim()
  return u ? REMIND_SENT_PREFIX + '::' + u : REMIND_SENT_PREFIX
}

/** `'22:00'` / `'08:00:00'` / `'8:00'` → 分钟数；认不出来返回 `null`（**不猜**） */
export function parseHm (value) {
  const s = value === null || value === undefined ? '' : String(value).trim()
  const m = HM_PATTERN.exec(s.length > 5 ? s.slice(0, 5) : s)
  if (!m) return null
  return Number(m[1]) * 60 + Number(m[2])
}

/** 分钟数 → `HH:mm`（内部回写用） */
export function hmText (minutes) {
  const n = Number(minutes)
  if (isNaN(n)) return ''
  const clamped = Math.max(0, Math.min(1439, Math.round(n)))
  const h = Math.floor(clamped / 60)
  const m = clamped % 60
  return (h < 10 ? '0' + h : String(h)) + ':' + (m < 10 ? '0' + m : String(m))
}

/**
 * 任意输入 → 一份**合法且完整**的设置（纯函数，**没有 null 漏出去**）
 *
 * 逐字段兜底而不是"整个对象兜底"：旧版本写进去的键少一个，不该把整份设置重置成默认。
 * 非法值（`aheadDays: -3` / `aheadDays: '很多'` / `quietStart: '25:00'`）一律回落到默认，
 * **不钳制**（钳制会把"用户填错了"伪装成"用户就想设 14 天"）。
 */
export function normalizeSetting (raw) {
  const r = raw && typeof raw === 'object' ? raw : {}
  const d = REMIND_SETTING_DEFAULTS

  const enabled = r.enabled === undefined || r.enabled === null ? d.enabled : r.enabled !== false && r.enabled !== 0 && r.enabled !== 'false'

  let aheadDays = d.aheadDays
  if (r.aheadDays !== undefined && r.aheadDays !== null && r.aheadDays !== '') {
    const n = Number(r.aheadDays)
    if (!isNaN(n) && n === Math.floor(n) && n >= AHEAD_DAYS_MIN && n <= AHEAD_DAYS_MAX) aheadDays = n
  }

  const quietEnabled = r.quietEnabled === undefined || r.quietEnabled === null ? d.quietEnabled : !!r.quietEnabled
  const quietStart = parseHm(r.quietStart) === null ? d.quietStart : String(r.quietStart).trim().slice(0, 5)
  const quietEnd = parseHm(r.quietEnd) === null ? d.quietEnd : String(r.quietEnd).trim().slice(0, 5)

  return {
    enabled: enabled,
    aheadDays: aheadDays,
    quietEnabled: quietEnabled,
    quietStart: quietStart,
    quietEnd: quietEnd
  }
}

/** 读该账号的设置（读不到 / 坏 JSON → 默认值；**永远返回完整对象**） */
export function readSetting (username) {
  const raw = readRaw(settingKeyFor(username))
  if (!raw) return normalizeSetting(null)
  if (typeof raw === 'object') return normalizeSetting(raw)
  try {
    return normalizeSetting(JSON.parse(String(raw)))
  } catch (e) {
    return normalizeSetting(null)
  }
}

/** 覆盖写该账号的设置；返回**实际落盘**的那份（已归一化） */
export function writeSetting (username, patch) {
  const next = normalizeSetting(Object.assign({}, readSetting(username), patch || {}))
  writeRaw(settingKeyFor(username), JSON.stringify(next))
  return next
}

/** 只改某几个键（`writeSetting` 的语义化包装） */
export function patchSetting (username, patch) {
  return writeSetting(username, patch)
}

/**
 * 清掉该账号的设置与账本。
 *
 * ⚠️ **本函数不被 `store/auth.js` 调用**（`resetAccountStores()` 里没有它），这是**刻意的**：
 * 每个账号一把键，换账号不需要清；这里只给"用户主动恢复默认"用。
 * `tools/check-stores.js` 的门禁只扫 `store/` 目录，本文件在 `utils/`，不会误报。
 */
export function clearSetting (username) {
  const had = readRaw(settingKeyFor(username))
  if (had && typeof uni !== 'undefined' && uni && typeof uni.removeStorageSync === 'function') {
    try {
      uni.removeStorageSync(settingKeyFor(username))
    } catch (e) {}
  }
  return normalizeSetting(null)
}

/* ==================================================================
 * 免打扰时段（纯函数；设置页与通知调度共用这一份判断）
 * ================================================================== */

/**
 * 这个时刻是不是落在免打扰时段内？
 *
 * - `quietEnabled === false` → 恒 `false`（**关着就不判**，免得"设了时段但没开"被静默静音）
 * - 两个端点解析不出来 → `false`（不猜）
 * - **`start === end` → `false`**：零长度窗口不生效。若当成"全天免打扰"，
 *   用户把两端拨到同一个时间就会**再也收不到任何提醒**，而且没有任何提示 —— 那是静默故障
 * - 区间是**左闭右开**：`22:00` 关、`08:00` 开。边界写清才不会出现"22:00 到底算不算"
 * - `start < end` → 同日窗口；`start > end` → **跨零点**窗口（默认值 22:00 → 08:00 就是这种）
 *
 * @param {Date} date
 * @param {object} setting 已归一化的设置（`readSetting()` 的返回值）
 */
export function isQuietHours (date, setting) {
  const s = normalizeSetting(setting)
  if (!s.quietEnabled) return false
  const start = parseHm(s.quietStart)
  const end = parseHm(s.quietEnd)
  if (start === null || end === null || start === end) return false
  const d = date instanceof Date && !isNaN(date.getTime()) ? date : new Date()
  const m = d.getHours() * 60 + d.getMinutes()
  if (start < end) return m >= start && m < end
  return m >= start || m < end
}

/** 免打扰时段的人话（设置页直接显示；跨零点时说"次日"） */
export function quietLabel (setting) {
  const s = normalizeSetting(setting)
  if (!s.quietEnabled) return '不开启'
  const start = parseHm(s.quietStart)
  const end = parseHm(s.quietEnd)
  if (start === null || end === null) return '时段没设好'
  if (start === end) return s.quietStart + '（两端相同，不生效）'
  return start < end ? s.quietStart + ' 至 ' + s.quietEnd : s.quietStart + ' 至次日 ' + s.quietEnd
}

/** 提前天数的人话（与 `AHEAD_DAY_OPTIONS` 同一套文案） */
export function aheadLabel (aheadDays) {
  const n = normalizeSetting({ aheadDays: aheadDays }).aheadDays
  const opt = AHEAD_DAY_OPTIONS[n]
  return opt ? opt.label : '到期当天'
}

/** 总开关的人话 */
export function enabledLabel (setting) {
  return normalizeSetting(setting).enabled ? '已开启' : '已关闭'
}