/**
 * 提醒排程（手机端 / JS）：**从健康记录派生 → 算到期时刻 → 决定这一条现在要不要响**
 *
 * ============================================================================
 * 口径（**与既有健康模块同一套，不另造阈值**）
 * ============================================================================
 * 分档文案与"逾期 / 今天 / 还有 N 天"全部来自 `utils/health.js` 的 `dueInfo()`——
 * 那是首页「今日照护」与健康档案页共用的**唯一真源**（`< 0` 逾期、`=== 0` 今天、`1~7` soon、`>7` later）。
 * 本文件**只做两件 `dueInfo` 不做的事**：
 *   1. **窗口**：设置里的「提前 N 天」决定哪些条目算"该提醒的"（`isWithinWindow`）
 *   2. **时刻**：把"哪一天到期"换成"哪一刻该响"（`reminderFireAt`）
 *
 * ### ① 窗口口径
 * `逾期 + 今天到期 + 还有 <= N 天`。`N = 0` 时**正好等于**首页原来的
 * 「待办 = 逾期 + 今天到期」（所以默认设置下既有行为一字不变，见 `utils/remind-setting.js` 文件头）。
 *
 * ### ② 到期时刻口径（**这是本切片选的、需要写清的那个选择**）
 * **`提醒时刻 = 到期日当天的 09:00，再往前推 N 天`**（`FIRE_HOUR = 9`）。
 * 为什么不直接用 `nextDueAt` 的时分：`nextDueAt` 是"上次接种时间 + 周期"推出来的，
 * 实测样本里有 `2026-09-20 09:00:00` 这种时分，也有只有日期语义的；而健康模块自己的分档
 * （`nextDueDays`）本来就是**按天整数截断**的。用日级口径 → **分档与响铃时刻必然一致**，
 * 不会出现"分档说今天到期、响铃时刻却已经过去 8 小时"这种自相矛盾。
 * `09:00` 是"上午刚开工"的时间点，比 00:00 更可能被看见，也不会在多数人的免打扰时段里。
 *
 * ### ③ 去重与清理（账本里两类前缀，别混用）
 * - **`f:` = 已经发过**。**未逾期条目**：`f:条目id@到期日` → 同一次到期只响一次；
 *   **已逾期条目**：`f:条目id@今天` → **每天最多响一次**（逾期项没有"新的到期日"了，
 *   若也用到期日做键，就会"响过一次以后永远不再响"，那才是最坏的结果）
 * - **`s:` = 已经交给系统排程**（**只有 App 端的原生 `delay` 才有**）。键与 `f:` 同形。
 *   没有它会怎样：每次启动/回前台都重新 `delay` 一遍 → **同一条提醒被排 N 次、响 N 遍**
 * - **已过去的条目**：`fireAt` 在过去不是"要排程"而是"该补提醒"（逾期项就是这种），
 *   所以不丢弃；但账本只保留 `LEDGER_KEEP_DAYS` 天，避免无限增长
 *
 * ### ④ 弹不弹由 `decideFire()` 一处决定；排不排由 `decideSchedule()` 一处决定
 * `decideFire` 顺序：总开关 → 有没有到期时刻 → 到点了没 → 免打扰 → 今天是不是已经响过。
 * **免打扰发生在去重之前**：被免打扰挡掉的那次**不记账**，所以时段一过还能补上
 * （否则"晚上 10 点后静音"会变成"那天这件事彻底不提醒了"）。
 * `decideSchedule` 额外两条：`fireAt` 已经过去 → 交给 `decideFire`（不排程）；
 * `fireAt` 本身落在免打扰时段里 → **不排**（排了就会在静音时段响，那是自欺）。
 */

import { dueInfo, parseHealthTime, sortedByDue } from './health.js'
import { isQuietHours, ledgerKeyFor, normalizeSetting } from './remind-setting.js'

/** 到期当天几点提醒（本地时间，24 小时制） */
export const FIRE_HOUR = 9
export const FIRE_MINUTE = 0

/** 账本保留天数（超过就丢；逾期项每天一条，留着才有意义） */
export const LEDGER_KEEP_DAYS = 30

function pad2 (n) {
  return n < 10 ? '0' + n : String(n)
}

/** 本地日期键 `yyyy-MM-dd`（**不用 `toISOString()`**：那会按 UTC 算，东八区差一天） */
export function dayKeyOf (date) {
  const d = date instanceof Date && !isNaN(date.getTime()) ? date : new Date()
  return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate())
}

/** 今天（可注入 now，便于离线断言） */
export function todayKey (now) {
  return dayKeyOf(now instanceof Date && !isNaN(now.getTime()) ? now : new Date())
}

/** `2026-07-31 09:00`（提醒时刻的人话） */
export function fireText (date) {
  if (!(date instanceof Date) || isNaN(date.getTime())) return '到期时间待确认'
  return (
    date.getFullYear() + '-' + pad2(date.getMonth() + 1) + '-' + pad2(date.getDate()) +
    ' ' + pad2(date.getHours()) + ':' + pad2(date.getMinutes())
  )
}

/* ==================================================================
 * 1. 纯函数：窗口 / 时刻
 * ================================================================== */

/**
 * 这条记录是不是落在「提前 N 天」的提醒窗口里？（**纯函数**）
 *
 * 口径全部取自 `dueInfo()`（同一真源），本函数**不重复实现分档**：
 * `dueInfo().days` 就是后端给的 `nextDueDays`（整数截断、可为负），`key === 'none'` 表示
 * 没有到期时间 → 不参与提醒（**不猜**）。
 *
 * @param {number|string|null} nextDueDays
 * @param {number} aheadDays 0 = 到期当天（默认）；最大 14，见 `AHEAD_DAYS_MAX`
 */
export function isWithinWindow (nextDueDays, aheadDays) {
  const info = dueInfo(nextDueDays, null)
  if (info.key === 'none') return false
  if (typeof info.days !== 'number') return false
  const n = normalizeSetting({ aheadDays: aheadDays }).aheadDays
  return info.days <= n
}

/**
 * 到期时间 → **该提醒的时刻**（口径见文件头 ②：到期日 09:00 往前 N 天）
 * @param {string} nextDueAt 后端格式 `yyyy-MM-dd HH:mm:ss`（非 ISO，必须走 parseHealthTime）
 * @returns {Date|null} 解析不出来返回 null（调用方据此标"时间待确认"，**不编一个时刻**）
 */
export function reminderFireAt (nextDueAt, aheadDays) {
  const due = parseHealthTime(nextDueAt)
  if (!due) return null
  const n = normalizeSetting({ aheadDays: aheadDays }).aheadDays
  return new Date(due.getFullYear(), due.getMonth(), due.getDate() - n, FIRE_HOUR, FIRE_MINUTE, 0, 0)
}

/* ==================================================================
 * 2. 一条 preventive → 一条提醒项（模板可直接消费，**不含 null 漏出**）
 * ================================================================== */

/**
 * @param {object} row `preventive` 的一条（裸数组里的元素）
 * @param {string} petName
 * @param {number} aheadDays
 * @param {Date} now
 * @returns {object|null} 不在窗口内 → `null`（调用方过滤，不要产出空壳）
 */
export function buildReminderItem (row, petName, aheadDays, now) {
  const r = row || {}
  if (!isWithinWindow(r.nextDueDays, aheadDays)) return null
  const at = now instanceof Date && !isNaN(now.getTime()) ? now : new Date()
  const info = dueInfo(r.nextDueDays, r.nextDueAt)
  const due = parseHealthTime(r.nextDueAt)
  const fireAt = reminderFireAt(r.nextDueAt, aheadDays)
  const overdue = info.key === 'overdue'
  const id = r.id === null || r.id === undefined ? '' : String(r.id)
  const title = String(r.itemName || r.typeDesc || '照护项').trim()

  let fireState = 'waiting'
  let fireStateLabel = '还没到提醒时刻'
  if (!fireAt) {
    fireState = 'no-date'
    fireStateLabel = '到期时间待确认，不会响'
  } else if (overdue) {
    fireState = 'overdue'
    fireStateLabel = '已经过期，打开 App 就补提醒'
  } else if (fireAt.getTime() <= at.getTime()) {
    fireState = 'due-now'
    fireStateLabel = '到点了，本次就会提醒'
  } else {
    fireStateLabel = fireText(fireAt) + ' 提醒'
  }

  return {
    key: 'preventive#' + id,
    petId: id,
    petName: String(petName || '这只宠物'),
    title: title,
    typeValue: r.type || '',
    typeLabel: String(r.typeDesc || r.type || '照护项').trim(),
    dueLabel: info.label,
    dueDetail: info.detail,
    dueTone: info.tone,
    statusKey: info.key,
    nextDueDays: typeof info.days === 'number' ? info.days : null,
    overdue: overdue,
    inWindow: true,
    dueDayKey: due ? dayKeyOf(due) : '',
    fireAt: fireAt,
    fireText: fireText(fireAt),
    fireState: fireState,
    fireStateLabel: fireStateLabel,
    /** 有没有一个**确定的**提醒时刻（没有就不排程、也不发通知，页面上如实说） */
    canNotify: !!fireAt
  }
}

/* ==================================================================
 * 3. 整块看板：宠物列表 + 健康数据 → 排序好的提醒项
 * ================================================================== */

/**
 * @param {object} input
 *   `{ pets: [{pid, name}], healthByPid: { <pid>: {preventive, failures} }, setting, now }`
 * @returns {object} 看板（**未登录时的空看板由 `api/remind.js` 产出，不走这里**）
 */
export function buildReminderBoard (input) {
  const i = input || {}
  const setting = normalizeSetting(i.setting)
  const now = i.now instanceof Date && !isNaN(i.now.getTime()) ? i.now : new Date()
  const pets = Array.isArray(i.pets) ? i.pets : []
  const byPid = i.healthByPid && typeof i.healthByPid === 'object' ? i.healthByPid : {}

  const petViews = []
  const items = []
  const degradedPets = []

  for (let p = 0; p < pets.length; p++) {
    const pet = pets[p] || {}
    const pid = String(pet.pid === null || pet.pid === undefined ? '' : pet.pid)
    const name = String(pet.name || '未命名宠物')
    const health = byPid[pid]
    if (!health) {
      // 这只宠物的健康记录一条都没读到：**照护项不猜**，页面点名说明（与首页同一套降级口径）
      degradedPets.push(name)
      petViews.push({ pid: pid, name: name, degraded: true, items: [], suppressedCount: 0, emptyText: '' })
      continue
    }
    // 排序复用健康模块的 `sortedByDue`（逾期最久在前），与首页/健康档案同一顺序
    const rows = sortedByDue(Array.isArray(health.preventive) ? health.preventive : [])
    const all = []
    for (let r = 0; r < rows.length; r++) {
      const item = buildReminderItem(rows[r], name, setting.aheadDays, now)
      if (item) all.push(item)
    }
    // 总开关关掉 → 一条都不列。**不是**"没有照护项"：`suppressedCount` 记下被关掉几条，
    // 页面据此说"有 N 条本来会提醒，被总开关关掉了"（两件事分清，不许糊成一句空态）
    const list = setting.enabled ? all : []
    for (let m = 0; m < list.length; m++) {
      list[m].pid = pid
      items.push(list[m])
    }
    petViews.push({
      pid: pid,
      name: name,
      degraded: false,
      items: list,
      suppressedCount: setting.enabled ? 0 : all.length,
      emptyText: setting.enabled ? '这只宠物没有到期时间落在提醒范围内的疫苗或驱虫' : ''
    })
  }

  items.sort(function (a, b) {
    const da = typeof a.nextDueDays === 'number' ? a.nextDueDays : Infinity
    const db = typeof b.nextDueDays === 'number' ? b.nextDueDays : Infinity
    if (da !== db) return da - db
    return a.key < b.key ? -1 : a.key > b.key ? 1 : 0
  })

  let todoCount = 0
  let overdueCount = 0
  for (let k = 0; k < items.length; k++) {
    if (items[k].overdue) {
      overdueCount++
      todoCount++
    } else {
      todoCount++
    }
  }

  const jobs = []
  for (let j = 0; j < items.length; j++) if (items[j].canNotify) jobs.push(items[j])

  // 被总开关关掉的条数（页面要能说"有 N 条本来会提醒"，不能糊成"没有照护项"）
  let suppressedCount = 0
  for (let s = 0; s < petViews.length; s++) suppressedCount += Number(petViews[s].suppressedCount || 0)

  const quietNow = isQuietHours(now, setting)
  let notice = ''
  if (!setting.enabled) {
    notice = suppressedCount
      ? '提醒总开关已关闭：有 ' + suppressedCount + ' 条本来会提醒，现在不列待办也不发通知'
      : '提醒总开关已关闭：这里不再列待办，也不会发本地通知'
  } else if (quietNow) {
    notice = '现在在免打扰时段（' + setting.quietStart + ' 至 ' + setting.quietEnd + '），本次不发通知；时段过去后打开 App 会补上'
  }

  return {
    loggedIn: true,
    setting: setting,
    aheadDays: setting.aheadDays,
    enabled: setting.enabled,
    pets: petViews,
    items: items,
    jobs: jobs,
    todoCount: todoCount,
    overdueCount: overdueCount,
    suppressedCount: suppressedCount,
    degradedPets: degradedPets,
    quietNow: quietNow,
    notice: notice
  }
}

/** 未登录 / 没有宠物时的空看板（**一个数字都不编**） */
export function emptyBoard (setting) {
  const s = normalizeSetting(setting)
  return {
    loggedIn: false,
    setting: s,
    aheadDays: s.aheadDays,
    enabled: s.enabled,
    pets: [],
    items: [],
    jobs: [],
    todoCount: 0,
    overdueCount: 0,
    suppressedCount: 0,
    degradedPets: [],
    quietNow: false,
    notice: ''
  }
}

/* ==================================================================
 * 4. 「现在要不要响」——唯一决策点
 * ================================================================== */

/**
 * 去重键：**两条通道（软件内弹窗 / 系统本地通知）共用同一个键**
 *
 * 这样弹窗弹过的条目，系统通知就会判成 `duplicate` 而不再响一遍
 * —— 否则用户回前台时会被"弹窗 + 系统通知"同时骚扰两次。
 *
 * 未逾期条目用**到期日**做键（同一次到期只提醒一次）；
 * 已逾期条目用**今天**做键（逾期项没有新的到期日了，用到期日做键就会"响过一次以后永远不再响"）。
 */
export function dedupeKeyOf (item, now) {
  const at = now instanceof Date && !isNaN(now.getTime()) ? now : new Date()
  const it = item || {}
  return String(it.key || 'unknown') + '@' + (it.overdue ? todayKey(at) : (it.dueDayKey || todayKey(at)))
}

/**
 * @param {object} item `buildReminderItem()` 的产物
 * @param {object} ledger 该账号的账本（`f:/s:` 前缀 + 去重键 → 时间戳）
 * @param {object} setting
 * @param {Date} now
 * @returns {{fire:boolean, reason:string, dedupeKey:string}}
 *   `reason` ∈ `off`（总开关关）/ `no-date`（没有到期时刻）/ `not-yet`（还没到点）/
 *   `quiet`（免打扰）/ `duplicate`（今天已响过）/ `ok`
 */
export function decideFire (item, ledger, setting, now) {
  const s = normalizeSetting(setting)
  const at = now instanceof Date && !isNaN(now.getTime()) ? now : new Date()
  const it = item || {}
  const dedupeKey = dedupeKeyOf(it, at)
  if (!s.enabled) return { fire: false, reason: 'off', dedupeKey: dedupeKey }
  if (!it.canNotify || !(it.fireAt instanceof Date) || isNaN(it.fireAt.getTime())) {
    return { fire: false, reason: 'no-date', dedupeKey: dedupeKey }
  }
  if (it.fireAt.getTime() > at.getTime()) return { fire: false, reason: 'not-yet', dedupeKey: dedupeKey }
  // ⚠️ 免打扰在去重**之前**：被静音挡掉的那次不记账，时段过去还能补
  if (isQuietHours(at, s)) return { fire: false, reason: 'quiet', dedupeKey: dedupeKey }
  const book = ledger && typeof ledger === 'object' ? ledger : {}
  if (Object.prototype.hasOwnProperty.call(book, FIRED_PREFIX + dedupeKey)) {
    return { fire: false, reason: 'duplicate', dedupeKey: dedupeKey }
  }
  return { fire: true, reason: 'ok', dedupeKey: dedupeKey }
}

/**
 * 这条**要不要交给系统排程**（只有 App 端的原生 `delay` 才有这个能力）
 *
 * 与 `decideFire` 的分工：`fireAt` **已经过去** → `reason: 'past'`，交给 `decideFire` 立刻补发；
 * `fireAt` **还在未来** → 这里算出 `delaySeconds` 让原生层到点显示。
 *
 * @returns {{schedule:boolean, reason:string, scheduleKey:string, delaySeconds:number}}
 *   `reason` ∈ `off` / `no-date` / `past`（不是这里的事）/ `quiet`（到点那一刻本来就在禁音时段，
 *   **不排** —— 排了就会在静音时段响）/ `duplicate`（已经排过）/ `ok`
 */
export function decideSchedule (item, ledger, setting, now) {
  const s = normalizeSetting(setting)
  const at = now instanceof Date && !isNaN(now.getTime()) ? now : new Date()
  const it = item || {}
  const scheduleKey = SCHEDULED_PREFIX + dedupeKeyOf(it, at)
  const out = { schedule: false, reason: '', scheduleKey: scheduleKey, delaySeconds: 0 }
  if (!s.enabled) return Object.assign(out, { reason: 'off' })
  if (!it.canNotify || !(it.fireAt instanceof Date) || isNaN(it.fireAt.getTime())) {
    return Object.assign(out, { reason: 'no-date' })
  }
  if (it.fireAt.getTime() <= at.getTime()) return Object.assign(out, { reason: 'past' })
  if (isQuietHours(it.fireAt, s)) return Object.assign(out, { reason: 'quiet' })
  const book = ledger && typeof ledger === 'object' ? ledger : {}
  if (Object.prototype.hasOwnProperty.call(book, scheduleKey)) return Object.assign(out, { reason: 'duplicate' })
  return Object.assign(out, {
    schedule: true,
    reason: 'ok',
    delaySeconds: Math.max(1, Math.ceil((it.fireAt.getTime() - at.getTime()) / 1000))
  })
}

/* ==================================================================
 * 4b. 软件内弹窗（**本切片的主通道**，跨平台都能真跑）
 *
 * 为什么它比系统通知更值得做扎实：系统通知在海量机型/权限/后台策略下都不确定
 * （`utils/local-notify.js` 文件头列的三条硬事实），而**软件内弹窗只要 App 在前台就一定能显示**。
 * 触发点是"进入 App / 回前台"（`App.vue` 的 `onLaunch` / `onShow`）+ 首页 `onShow` 兜底，
 * 所以 H5 也真的能弹 —— 这条正好绕开了"H5 关掉页面做不到后台提醒"的死结。
 * ================================================================== */

/** 一次最多列几条（再多就不是提醒，是一面墙；剩下的会被如实计数，不静默丢） */
export const POPUP_MAX_ITEMS = 5

/**
 * 这一次"要不要弹、弹哪几条"（**纯函数**，页面/测试都调它，不许各判一套）
 *
 * 门槛顺序（每条都必须真的生效）：
 *   ① **总开关关 → 不弹**（`reason: 'off'`，并把"本来有几条"记进 `suppressed`）
 *   ② **免打扰时段内 → 不弹**（`reason: 'quiet'`）
 *   ③ 只挑**已经到点**的（`fireAt <= now`）；还没到点的记进 `futureTotal`（**不是**"没有照护项"）
 *   ④ **同一条目同一天只弹一次**：账本里已经有 `f:<去重键>` 的跳过（`dupTotal`）
 *      → 所以"每次切前台都骚扰"不会发生；逾期项明天是新的一天 → 明天会再弹一次（这是想要的）
 *
 * @returns {{show:boolean, reason:string, items:object[], overflow:number,
 *            dueTotal:number, dupTotal:number, futureTotal:number, suppressed:number}}
 *   `reason` ∈ `off` / `quiet` / `ok` / `all-reminded`（今天都提醒过了）/ `nothing-due`（没有到点的）
 */
export function decidePopup (items, ledger, setting, now) {
  const s = normalizeSetting(setting)
  const at = now instanceof Date && !isNaN(now.getTime()) ? now : new Date()
  const list = Array.isArray(items) ? items : []
  const base = { show: false, reason: '', items: [], overflow: 0, dueTotal: 0, dupTotal: 0, futureTotal: 0, suppressed: 0 }
  if (!s.enabled) return Object.assign(base, { reason: 'off', suppressed: list.length })
  if (isQuietHours(at, s)) return Object.assign(base, { reason: 'quiet', suppressed: list.length })

  const book = ledger && typeof ledger === 'object' ? ledger : {}
  const picked = []
  let dueTotal = 0
  let dupTotal = 0
  let futureTotal = 0
  for (let i = 0; i < list.length; i++) {
    const it = list[i] || {}
    if (!it.canNotify || !(it.fireAt instanceof Date) || isNaN(it.fireAt.getTime())) continue
    if (it.fireAt.getTime() > at.getTime()) {
      futureTotal++
      continue
    }
    dueTotal++
    if (Object.prototype.hasOwnProperty.call(book, FIRED_PREFIX + dedupeKeyOf(it, at))) {
      dupTotal++
      continue
    }
    picked.push(it)
  }
  const showItems = picked.slice(0, POPUP_MAX_ITEMS)
  return {
    show: showItems.length > 0,
    reason: showItems.length ? 'ok' : dueTotal ? 'all-reminded' : 'nothing-due',
    items: showItems,
    overflow: Math.max(0, picked.length - showItems.length),
    dueTotal: dueTotal,
    dupTotal: dupTotal,
    futureTotal: futureTotal,
    suppressed: 0
  }
}

/** 弹窗标题/副标题（**数字必须与 `items` 对得上**；这里是唯一出处） */
export function popupHeading (decision) {
  const d = decision || {}
  const n = Array.isArray(d.items) ? d.items.length : 0
  const extra = Number(d.overflow || 0)
  let subtitle = '来自健康记录里的疫苗与驱虫到期时间'
  if (extra > 0) subtitle += '，另外还有 ' + extra + ' 项没列出'
  if (Number(d.dupTotal || 0) > 0) subtitle += '（今天已经提醒过的 ' + d.dupTotal + ' 项不再重复）'
  return {
    title: '有 ' + n + ' 项照护提醒',
    subtitle: subtitle
  }
}

/** 一次排程跑完的汇总（页面直接显示；**每个计数都对得上，不糊成一个数**） */
export function summarizeRun (decisions) {
  const list = Array.isArray(decisions) ? decisions : []
  const out = {
    total: list.length,
    sent: 0,
    scheduled: 0,
    off: 0,
    noDate: 0,
    notYet: 0,
    past: 0,
    quiet: 0,
    duplicate: 0,
    unsupported: 0,
    failed: 0
  }
  for (let i = 0; i < list.length; i++) {
    const r = list[i] && list[i].reason ? String(list[i].reason) : ''
    if (r === 'ok') {
      if (list[i] && list[i].scheduled === true) out.scheduled++
      else out.sent++
    } else if (r === 'off') out.off++
    else if (r === 'no-date') out.noDate++
    else if (r === 'not-yet') out.notYet++
    else if (r === 'past') out.past++
    else if (r === 'quiet') out.quiet++
    else if (r === 'duplicate') out.duplicate++
    else if (r === 'unsupported') out.unsupported++
    if (list[i] && list[i].ok === false) out.failed++
  }
  return out
}

/** 通知文案（标题固定，正文带上宠物名 + 项目 + 分档；**不写"已完成"这种没有数据源的话**） */
export function noticeTitle () {
  return '喵汪 · 照护提醒'
}

export function noticeBody (item) {
  const it = item || {}
  return it.petName + '的' + it.title + '，' + it.dueLabel
}

/* ==================================================================
 * 5. 账本（去重）：存本机，按账号分键
 *
 * ⚠️ 键有**两类前缀**，别混用（文件头 ③）：
 *   `f:<dedupeKey>` = 已经发过；`s:<dedupeKey>` = 已经交给系统排程（App 原生 delay）
 * ================================================================== */

/** 已发送前缀 */
export const FIRED_PREFIX = 'f:'
/** 已排程前缀（App 端原生 delay；没有它就会"每次回前台都重排一遍、同一条响 N 遍"） */
export const SCHEDULED_PREFIX = 's:'

/** 读账本（坏 JSON / 读不到 → 空账本，**不回退成"全都发过"**） */
export function readLedger (username) {
  if (typeof uni === 'undefined' || !uni || typeof uni.getStorageSync !== 'function') return {}
  let raw = ''
  try {
    raw = uni.getStorageSync(ledgerKeyFor(username))
  } catch (e) {
    return {}
  }
  if (!raw) return {}
  if (typeof raw === 'object') return raw
  try {
    const parsed = JSON.parse(String(raw))
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch (e) {
    return {}
  }
}

/** 写账本；返回是否落盘成功（失败时调用方**不能**当成"已记账"） */
export function saveLedger (username, ledger) {
  if (typeof uni === 'undefined' || !uni || typeof uni.setStorageSync !== 'function') return false
  try {
    uni.setStorageSync(ledgerKeyFor(username), JSON.stringify(ledger && typeof ledger === 'object' ? ledger : {}))
    return true
  } catch (e) {
    return false
  }
}

/** 丢掉超过 `LEDGER_KEEP_DAYS` 天的记录（返回新对象，不改入参） */
export function pruneLedger (ledger, now) {
  const at = now instanceof Date && !isNaN(now.getTime()) ? now : new Date()
  const cutoff = at.getTime() - LEDGER_KEEP_DAYS * 24 * 60 * 60 * 1000
  const src = ledger && typeof ledger === 'object' ? ledger : {}
  const out = {}
  Object.keys(src).forEach(function (k) {
    const t = Number(src[k])
    if (!isNaN(t) && t >= cutoff) out[k] = t
  })
  return out
}

/** 记一笔"已发送"（**只有真的发成功了才该调它**） */
export function markFired (ledger, dedupeKey, now) {
  return markLedger(ledger, FIRED_PREFIX + String(dedupeKey), now)
}

/** 记一笔"已交给系统排程"（**只有原生 delay 真的接受了才该调它**） */
export function markScheduled (ledger, scheduleKey, now) {
  return markLedger(ledger, String(scheduleKey), now)
}

function markLedger (ledger, key, now) {
  const at = now instanceof Date && !isNaN(now.getTime()) ? now : new Date()
  const out = Object.assign({}, ledger && typeof ledger === 'object' ? ledger : {})
  out[key] = at.getTime()
  return out
}