/**
 * 提醒排程（Web / TS）—— **手机端 `MiaoWang-phone/utils/remind-schedule.js` 的同口径落地**
 *
 * ============================================================================
 * 口径（**与既有健康模块同一套，不另造阈值**）
 * ============================================================================
 * 分档文案与"逾期 / 今天 / 还有 N 天"全部来自 `api/health.ts` 的 `overdueLabel()`——
 * 那是首页「照护提醒」与健康档案页共用的**唯一真源**（`< 0` 逾期、`=== 0` 今天、
 * `1~30` soon、`>30` later；`null` → none）。
 * 本文件**只做两件 `overdueLabel` 不做的事**：
 *   1. **窗口**：设置里的「提前 N 天」决定哪些条目算"该提醒的"（`isWithinWindow`）
 *   2. **时刻**：把"哪一天到期"换成"哪一刻该响"（`reminderFireAt`）
 *
 * ### ① 窗口口径
 * `逾期 + 今天到期 + 还有 <= N 天`。`N = 0` 时**正好等于**首页原来的
 * 「待办 = 逾期 + 今天到期」（所以默认设置下既有行为一字不变，见 `utils/remindSetting.ts` 文件头）。
 *
 * ### ② 到期时刻口径（**这是与手机端共同选定的那个选择**）
 * **`提醒时刻 = 到期日当天的 09:00，再往前推 N 天`**（`FIRE_HOUR = 9`）。
 * 为什么不直接用 `nextDueAt` 的时分：`nextDueAt` 是"上次接种时间 + 周期"推出来的，
 * 实测样本里有 `2026-09-20 09:00:00` 这种时分，也有只有日期语义的；而健康模块自己的分档
 * （`nextDueDays`）本来就是**按天整数截断**的。用日级口径 → **分档与响铃时刻必然一致**。
 *
 * ### ③ 去重与清理（账本里两类前缀，别混用）
 * - **`f:` = 已经发过**。**未逾期条目**：`f:条目id@到期日` → 同一次到期只响一次；
 *   **已逾期条目**：`f:条目id@今天` → **每天最多响一次**（逾期项没有"新的到期日"了，
 *   若也用到期日做键，就会"响过一次以后永远不再响"，那才是最坏的结果）
 * - 账本只保留 `LEDGER_KEEP_DAYS` 天，避免无限增长
 *
 * ### ④ 弹不弹由 `decidePopup()` 一处决定；系统通知发不发由 `decideFire()` 一处决定
 * `decideFire` 顺序：总开关 → 有没有到期时刻 → 到点了没 → 免打扰 → 今天是不是已经响过。
 * **免打扰发生在去重之前**：被免打扰挡掉的那次**不记账**，所以时段一过还能补上
 * （否则"晚上 10 点后静音"会变成"那天这件事彻底不提醒了"）。
 *
 * ### ⑤ Web 没有"排给系统"这回事
 * 浏览器**不可能**把提醒排给操作系统（关掉标签页就没有任何执行机会），所以手机端的
 * `decideSchedule()` / `s` 前缀账本**在这里刻意不存在**：没有的东西不假装有。
 * 页面如实写"仅页面打开时有效"（`utils/localNotify.ts` 的 `scheduleTruthText()`）。
 */

import { overdueLabel, parseHealthTime, sortByDue } from '../api/health'
import type { PreventiveRecord } from '../api/health'
import { isQuietHours, ledgerKeyFor, normalizeSetting } from './remindSetting'
import type { RemindSetting } from './remindSetting'

/** 到期当天几点提醒（本地时间，24 小时制） */
export const FIRE_HOUR = 9
export const FIRE_MINUTE = 0

/** 账本保留天数（超过就丢；逾期项每天一条，留着才有意义） */
export const LEDGER_KEEP_DAYS = 30

/** 一次最多弹几条（再多就不是提醒，是一面墙；剩下的会被如实计数，不静默丢） */
export const POPUP_MAX_ITEMS = 5

/** 已发送前缀 */
export const FIRED_PREFIX = 'f:'

function pad2(n: number): string {
  return n < 10 ? `0${n}` : String(n)
}

/** 本地日期键 `yyyy-MM-dd`（**不用 `toISOString()`**：那会按 UTC 算，东八区差一天） */
export function dayKeyOf(date: Date): string {
  const d = date instanceof Date && !Number.isNaN(date.getTime()) ? date : new Date()
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

/** 今天（可注入 now，便于离线断言） */
export function todayKey(now?: Date): string {
  return dayKeyOf(now instanceof Date && !Number.isNaN(now.getTime()) ? now : new Date())
}

/** `2026-07-31 09:00`（提醒时刻的人话） */
export function fireText(date: Date | null): string {
  if (!(date instanceof Date) || Number.isNaN(date.getTime())) return '到期时间待确认'
  return (
    `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ` +
    `${pad2(date.getHours())}:${pad2(date.getMinutes())}`
  )
}

/* ==================================================================
 * 1. 纯函数：窗口 / 时刻
 * ================================================================== */

/**
 * 这条记录是不是落在「提前 N 天」的提醒窗口里？（**纯函数**）
 *
 * 口径全部取自 `overdueLabel()`（同一真源），本函数**不重复实现分档**：
 * `overdueLabel().days` 就是后端给的 `nextDueDays`（整数截断、可为负），`tone === 'none'`
 * 表示没有到期时间 → 不参与提醒（**不猜**）。
 */
export function isWithinWindow(nextDueDays: number | null | undefined, aheadDays: unknown): boolean {
  const info = overdueLabel(nextDueDays)
  if (info.tone === 'none') return false
  if (typeof info.days !== 'number') return false
  const n = normalizeSetting({ aheadDays }).aheadDays
  return info.days <= n
}

/**
 * 到期时间 → **该提醒的时刻**（口径见文件头 ②：到期日 09:00 往前 N 天）
 * @param nextDueAt 后端格式 `yyyy-MM-dd HH:mm:ss`（非 ISO，必须走 `parseHealthTime`）
 * @returns 解析不出来返回 `null`（调用方据此标"时间待确认"，**不编一个时刻**）
 */
export function reminderFireAt(nextDueAt: string | null | undefined, aheadDays: unknown): Date | null {
  const due = parseHealthTime(nextDueAt)
  if (!due) return null
  const n = normalizeSetting({ aheadDays }).aheadDays
  return new Date(due.getFullYear(), due.getMonth(), due.getDate() - n, FIRE_HOUR, FIRE_MINUTE, 0, 0)
}

/* ==================================================================
 * 2. 一条 preventive → 一条提醒项（模板可直接消费，**不含 null 漏出**）
 * ================================================================== */

export interface ReminderItem {
  /** `preventive#<记录id>`（去重键的前半段；**不是**宠物 id） */
  key: string
  /** 宠物 id（跳转 `/pets/:pid/health` 用它；由 `buildReminderBoard()` 填） */
  pid: string
  /** 健康记录的 id（**手机端的 `petId` 字段其实装的是这个值**，Web 端按语义命名，见交付说明） */
  recordId: string
  petName: string
  title: string
  typeValue: string
  typeLabel: string
  /** 「已逾期 10 天」「今天到期」「还有 3 天」 */
  dueLabel: string
  dueTone: string
  statusKey: string
  nextDueDays: number | null
  overdue: boolean
  inWindow: true
  dueDayKey: string
  fireAt: Date | null
  fireText: string
  fireState: 'waiting' | 'no-date' | 'overdue' | 'due-now'
  fireStateLabel: string
  /** 有没有一个**确定的**提醒时刻（没有就不发通知，页面上如实说） */
  canNotify: boolean
}

/**
 * @param row `preventive` 的一条（裸数组里的元素）
 * @returns 不在窗口内 → `null`（调用方过滤，不要产出空壳）
 */
export function buildReminderItem(
  row: PreventiveRecord | null | undefined,
  petName: string,
  aheadDays: unknown,
  now?: Date,
): ReminderItem | null {
  const r = row || ({} as PreventiveRecord)
  if (!isWithinWindow(r.nextDueDays, aheadDays)) return null
  const at = now instanceof Date && !Number.isNaN(now.getTime()) ? now : new Date()
  const info = overdueLabel(r.nextDueDays)
  const due = parseHealthTime(r.nextDueAt)
  const fireAt = reminderFireAt(r.nextDueAt, aheadDays)
  const overdue = info.tone === 'overdue'
  const id = r.id === null || r.id === undefined ? '' : String(r.id)
  const title = String(r.itemName || r.typeDesc || '照护项').trim()

  let fireState: ReminderItem['fireState'] = 'waiting'
  let fireStateLabel = '还没到提醒时刻'
  if (!fireAt) {
    fireState = 'no-date'
    fireStateLabel = '到期时间待确认，不会响'
  } else if (overdue) {
    fireState = 'overdue'
    fireStateLabel = '已经过期，打开页面就补提醒'
  } else if (fireAt.getTime() <= at.getTime()) {
    fireState = 'due-now'
    fireStateLabel = '到点了，本次就会提醒'
  } else {
    fireStateLabel = `${fireText(fireAt)} 提醒`
  }

  return {
    key: `preventive#${id}`,
    // pid 由 buildReminderBoard() 按宠物填；这里留空串，**绝不拿记录 id 冒充宠物 id**
    pid: '',
    recordId: id,
    petName: String(petName || '这只宠物'),
    title,
    typeValue: r.type || '',
    typeLabel: String(r.typeDesc || r.type || '照护项').trim(),
    dueLabel: info.text,
    dueTone: info.tone,
    statusKey: overdue ? 'overdue' : info.tone === 'today' ? 'today' : 'upcoming',
    nextDueDays: typeof info.days === 'number' ? info.days : null,
    overdue,
    inWindow: true,
    dueDayKey: due ? dayKeyOf(due) : '',
    fireAt,
    fireText: fireText(fireAt),
    fireState,
    fireStateLabel,
    canNotify: !!fireAt,
  }
}

/* ==================================================================
 * 3. 整块看板：宠物列表 + 健康数据 → 排序好的提醒项
 * ================================================================== */

export interface ReminderPetView {
  pid: string
  name: string
  degraded: boolean
  items: ReminderItem[]
  /** 被总开关关掉、本来会提醒的条数（页面据此说"有 N 条本来会提醒"） */
  suppressedCount: number
  emptyText: string
}

export interface ReminderBoard {
  loggedIn: boolean
  setting: RemindSetting
  aheadDays: number
  enabled: boolean
  pets: ReminderPetView[]
  items: ReminderItem[]
  jobs: ReminderItem[]
  todoCount: number
  overdueCount: number
  suppressedCount: number
  degradedPets: string[]
  quietNow: boolean
  notice: string
}

export interface ReminderBoardInput {
  pets: Array<{ pid: string; name: string }>
  healthByPid: Record<string, { preventive: PreventiveRecord[] } | undefined>
  setting?: unknown
  now?: Date
}

/**
 * @param input `{ pets: [{pid, name}], healthByPid: { <pid>: { preventive } }, setting, now }`
 * @returns 看板（**未登录时的空看板由 `api/remind.ts` 产出，不走这里**）
 */
export function buildReminderBoard(input: ReminderBoardInput | null | undefined): ReminderBoard {
  const i = input || { pets: [], healthByPid: {} }
  const setting = normalizeSetting(i.setting)
  const now = i.now instanceof Date && !Number.isNaN(i.now.getTime()) ? i.now : new Date()
  const pets = Array.isArray(i.pets) ? i.pets : []
  const byPid = i.healthByPid && typeof i.healthByPid === 'object' ? i.healthByPid : {}

  const petViews: ReminderPetView[] = []
  const items: ReminderItem[] = []
  const degradedPets: string[] = []

  for (const pet of pets) {
    const pid = String(pet?.pid ?? '')
    const name = String(pet?.name || '未命名宠物')
    const health = byPid[pid]
    if (!health) {
      // 这只宠物的健康记录一条都没读到：**照护项不猜**，页面点名说明（与首页同一套降级口径）
      degradedPets.push(name)
      petViews.push({ pid, name, degraded: true, items: [], suppressedCount: 0, emptyText: '' })
      continue
    }
    // 排序复用健康模块的 `sortByDue()`（逾期最久在前），与首页/健康档案同一顺序
    const rows = sortByDue(Array.isArray(health.preventive) ? health.preventive : [])
    const all: ReminderItem[] = []
    for (const row of rows) {
      const item = buildReminderItem(row, name, setting.aheadDays, now)
      if (item) {
        item.pid = pid
        all.push(item)
      }
    }
    // 总开关关掉 → 一条都不列。**不是**"没有照护项"：`suppressedCount` 记下被关掉几条，
    // 页面据此说"有 N 条本来会提醒，被总开关关掉了"（两件事分清，不许糊成一句空态）
    const list = setting.enabled ? all : []
    for (const it of list) items.push(it)
    petViews.push({
      pid,
      name,
      degraded: false,
      items: list,
      suppressedCount: setting.enabled ? 0 : all.length,
      emptyText: setting.enabled
        ? '这只宠物没有到期时间落在提醒范围内的疫苗或驱虫'
        : '',
    })
  }

  items.sort((a, b) => {
    const da = typeof a.nextDueDays === 'number' ? a.nextDueDays : Number.POSITIVE_INFINITY
    const db = typeof b.nextDueDays === 'number' ? b.nextDueDays : Number.POSITIVE_INFINITY
    if (da !== db) return da - db
    return a.key < b.key ? -1 : a.key > b.key ? 1 : 0
  })

  let todoCount = 0
  let overdueCount = 0
  for (const it of items) {
    if (it.overdue) overdueCount++
    todoCount++
  }

  const jobs = items.filter((it) => it.canNotify)

  // 被总开关关掉的条数（页面要能说"有 N 条本来会提醒"，不能糊成"没有照护项"）
  const suppressedCount = petViews.reduce((n, p) => n + Number(p.suppressedCount || 0), 0)

  const quietNow = isQuietHours(now, setting)
  let notice = ''
  if (!setting.enabled) {
    notice = suppressedCount
      ? `提醒总开关已关闭：有 ${suppressedCount} 条本来会提醒，现在不列待办也不发通知`
      : '提醒总开关已关闭：这里不再列待办，也不会发本机通知'
  } else if (quietNow) {
    notice = `现在在免打扰时段（${setting.quietStart} 至 ${setting.quietEnd}），本次不发通知；时段过去后打开页面会补上`
  }

  return {
    loggedIn: true,
    setting,
    aheadDays: setting.aheadDays,
    enabled: setting.enabled,
    pets: petViews,
    items,
    jobs,
    todoCount,
    overdueCount,
    suppressedCount,
    degradedPets,
    quietNow,
    notice,
  }
}

/** 未登录 / 没有宠物时的空看板（**一个数字都不编**） */
export function emptyBoard(setting?: unknown): ReminderBoard {
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
    notice: '',
  }
}

/* ==================================================================
 * 4. 「现在要不要响」——唯一决策点
 * ================================================================== */

/**
 * 去重键：**两条通道（软件内弹窗 / 系统通知）共用同一个键**
 *
 * 这样弹窗弹过的条目，系统通知就会判成 `duplicate` 而不再响一遍
 * —— 否则用户回前台时会被"弹窗 + 通知"同时骚扰两次。
 *
 * 未逾期条目用**到期日**做键（同一次到期只提醒一次）；
 * 已逾期条目用**今天**做键（逾期项没有新的到期日了，用到期日做键就会"响过一次以后永远不再响"）。
 */
export function dedupeKeyOf(item: ReminderItem | null | undefined, now?: Date): string {
  const at = now instanceof Date && !Number.isNaN(now.getTime()) ? now : new Date()
  const it = item || ({} as ReminderItem)
  const base = String(it.key || 'unknown')
  const day = it.overdue ? todayKey(at) : it.dueDayKey || todayKey(at)
  return `${base}@${day}`
}

export type FireReason = 'off' | 'no-date' | 'not-yet' | 'quiet' | 'duplicate' | 'ok'

export interface FireDecision {
  fire: boolean
  reason: FireReason
  dedupeKey: string
}

/**
 * 这一条现在要不要**发系统通知**。
 *
 * 顺序（每条都必须真的生效）：总开关 → 有没有到期时刻 → 到点了没 → **免打扰** → 今天是不是已经响过。
 * ⚠️ 免打扰在去重**之前**：被静音挡掉的那次不记账，时段过去还能补。
 */
export function decideFire(
  item: ReminderItem | null | undefined,
  ledger: Ledger | null | undefined,
  setting: unknown,
  now?: Date,
): FireDecision {
  const s = normalizeSetting(setting)
  const at = now instanceof Date && !Number.isNaN(now.getTime()) ? now : new Date()
  const it = item || ({} as ReminderItem)
  const dedupeKey = dedupeKeyOf(it, at)
  if (!s.enabled) return { fire: false, reason: 'off', dedupeKey }
  if (!it.canNotify || !(it.fireAt instanceof Date) || Number.isNaN(it.fireAt.getTime())) {
    return { fire: false, reason: 'no-date', dedupeKey }
  }
  if (it.fireAt.getTime() > at.getTime()) return { fire: false, reason: 'not-yet', dedupeKey }
  if (isQuietHours(at, s)) return { fire: false, reason: 'quiet', dedupeKey }
  const book = ledger && typeof ledger === 'object' ? ledger : {}
  if (Object.prototype.hasOwnProperty.call(book, FIRED_PREFIX + dedupeKey)) {
    return { fire: false, reason: 'duplicate', dedupeKey }
  }
  return { fire: true, reason: 'ok', dedupeKey }
}

/* ==================================================================
 * 4b. 软件内弹窗（**本切片的主通道**，跨浏览器都能真跑）
 *
 * 为什么它比系统通知更值得做扎实：系统通知在安全上下文 / 用户手势授权 / 浏览器策略
 * 下都不确定（`utils/localNotify.ts` 文件头列的三条硬事实），而**软件内弹窗只要页面开着
 * 就一定能显示**。触发点是"进入站点 / 回前台"（`document.visibilitychange`），
 * 所以这条正好绕开了"关掉标签页做不到后台提醒"的死结。
 * ================================================================== */

export interface PopupDecision {
  show: boolean
  reason: 'off' | 'quiet' | 'ok' | 'all-reminded' | 'nothing-due'
  items: ReminderItem[]
  overflow: number
  dueTotal: number
  dupTotal: number
  futureTotal: number
  suppressed: number
}

/**
 * 这一次"要不要弹、弹哪几条"（**纯函数**，页面/测试都调它，不许各判一套）
 *
 * 门槛顺序（每条都必须真的生效）：
 *   ① **总开关关 → 不弹**（`reason: 'off'`，并把"本来有几条"记进 `suppressed`）
 *   ② **免打扰时段内 → 不弹**（`reason: 'quiet'`）
 *   ③ 只挑**已经到点**的（`fireAt <= now`）；还没到点的记进 `futureTotal`（**不是**"没有照护项"）
 *   ④ **同一条目同一天只弹一次**：账本里已经有 `f:<去重键>` 的跳过（`dupTotal`）
 *      → 所以"每次切前台都骚扰"不会发生；逾期项明天是新的一天 → 明天会再弹一次（这是想要的）
 */
export function decidePopup(
  items: ReminderItem[] | null | undefined,
  ledger: Ledger | null | undefined,
  setting: unknown,
  now?: Date,
): PopupDecision {
  const s = normalizeSetting(setting)
  const at = now instanceof Date && !Number.isNaN(now.getTime()) ? now : new Date()
  const list = Array.isArray(items) ? items : []
  const base: PopupDecision = {
    show: false,
    reason: 'nothing-due',
    items: [],
    overflow: 0,
    dueTotal: 0,
    dupTotal: 0,
    futureTotal: 0,
    suppressed: 0,
  }
  if (!s.enabled) return { ...base, reason: 'off', suppressed: list.length }
  if (isQuietHours(at, s)) return { ...base, reason: 'quiet', suppressed: list.length }

  const book = ledger && typeof ledger === 'object' ? ledger : {}
  const picked: ReminderItem[] = []
  let dueTotal = 0
  let dupTotal = 0
  let futureTotal = 0
  for (const it of list) {
    if (!it || !it.canNotify || !(it.fireAt instanceof Date) || Number.isNaN(it.fireAt.getTime())) {
      continue
    }
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
    dueTotal,
    dupTotal,
    futureTotal,
    suppressed: 0,
  }
}

/** 弹窗标题/副标题（**数字必须与 `items` 对得上**；这里是唯一出处） */
export function popupHeading(decision: PopupDecision | null | undefined): {
  title: string
  subtitle: string
} {
  const d = decision || ({} as PopupDecision)
  const n = Array.isArray(d.items) ? d.items.length : 0
  const extra = Number(d.overflow || 0)
  let subtitle = '来自健康记录里的疫苗与驱虫到期时间'
  if (extra > 0) subtitle += `，另外还有 ${extra} 项没列出`
  if (Number(d.dupTotal || 0) > 0) subtitle += `（今天已经提醒过的 ${d.dupTotal} 项不再重复）`
  return { title: `有 ${n} 项照护提醒`, subtitle }
}

/** 通知标题（与手机端一致） */
export function noticeTitle(): string {
  return '喵汪 · 照护提醒'
}

/** 通知正文（宠物名 + 项目 + 分档；**不写"已完成"这种没有数据源的话**） */
export function noticeBody(item: ReminderItem | null | undefined): string {
  const it = item || ({} as ReminderItem)
  return `${it.petName}的${it.title}，${it.dueLabel}`
}

/**
 * 列表/弹窗里的那一句人话：`大宠爱（体外驱虫）已逾期 10 天`（**宠物名单独一列**）。
 *
 * 唯一出处：弹窗、设置页的看板列表、离线断言都调它，三处文案不许各写一份。
 * 宠物名不拼进这一句，是因为设计系统 v3 明确禁止用 `·` 当装饰分隔符（见 SKILL §7）；
 * 需要"一句读全"的场景（无障碍标签）走 `remindItemLine()`，用逗号连接。
 */
export function remindItemSentence(item: ReminderItem | null | undefined): string {
  const it = item || ({} as ReminderItem)
  return `${it.title}${it.dueLabel}`
}

/** 无障碍标签用的整句：`布丁，大宠爱（体外驱虫）已逾期 10 天` */
export function remindItemLine(item: ReminderItem | null | undefined): string {
  const it = item || ({} as ReminderItem)
  return `${it.petName}，${remindItemSentence(it)}`
}

/** 一次发送跑完的汇总（页面直接显示；**每个计数都对得上，不糊成一个数**） */
export interface RunSummary {
  total: number
  sent: number
  off: number
  noDate: number
  notYet: number
  quiet: number
  duplicate: number
  unsupported: number
  failed: number
}

export function summarizeRun(
  decisions: Array<{ reason?: string; ok?: boolean }> | null | undefined,
): RunSummary {
  const list = Array.isArray(decisions) ? decisions : []
  const out: RunSummary = {
    total: list.length,
    sent: 0,
    off: 0,
    noDate: 0,
    notYet: 0,
    quiet: 0,
    duplicate: 0,
    unsupported: 0,
    failed: 0,
  }
  for (const d of list) {
    const r = d && d.reason ? String(d.reason) : ''
    if (r === 'ok') out.sent++
    else if (r === 'off') out.off++
    else if (r === 'no-date') out.noDate++
    else if (r === 'not-yet') out.notYet++
    else if (r === 'quiet') out.quiet++
    else if (r === 'duplicate') out.duplicate++
    else if (r === 'unsupported') out.unsupported++
    if (d && d.ok === false) out.failed++
  }
  return out
}

/* ==================================================================
 * 5. 账本（去重）：存本机 localStorage，按账号分键
 * ================================================================== */

/** 账本：`f:<去重键>` → 记账时刻（毫秒） */
export type Ledger = Record<string, number>

function rawStore(): Storage | null {
  try {
    const s = (globalThis as { localStorage?: Storage }).localStorage
    return s && typeof s.getItem === 'function' ? s : null
  } catch {
    return null
  }
}

/** 读账本（坏 JSON / 读不到 → 空账本，**不回退成"全都发过"**） */
export function readLedger(username?: string | null): Ledger {
  const s = rawStore()
  if (!s) return {}
  let raw: string | null = null
  try {
    raw = s.getItem(ledgerKeyFor(username))
  } catch {
    return {}
  }
  if (!raw) return {}
  try {
    const parsed = JSON.parse(String(raw)) as unknown
    return parsed && typeof parsed === 'object' ? (parsed as Ledger) : {}
  } catch {
    return {}
  }
}

/** 写账本；返回是否落盘成功（失败时调用方**不能**当成"已记账"） */
export function saveLedger(username: string | null | undefined, ledger: Ledger): boolean {
  const s = rawStore()
  if (!s) return false
  try {
    s.setItem(ledgerKeyFor(username), JSON.stringify(ledger && typeof ledger === 'object' ? ledger : {}))
    return true
  } catch {
    return false
  }
}

/** 丢掉超过 `LEDGER_KEEP_DAYS` 天的记录（返回新对象，不改入参） */
export function pruneLedger(ledger: Ledger | null | undefined, now?: Date): Ledger {
  const at = now instanceof Date && !Number.isNaN(now.getTime()) ? now : new Date()
  const cutoff = at.getTime() - LEDGER_KEEP_DAYS * 24 * 60 * 60 * 1000
  const src = ledger && typeof ledger === 'object' ? ledger : {}
  const out: Ledger = {}
  for (const k of Object.keys(src)) {
    const t = Number(src[k])
    if (!Number.isNaN(t) && t >= cutoff) out[k] = t
  }
  return out
}

/** 记一笔"已发送"（**只有真的发成功了才该调它**） */
export function markFired(ledger: Ledger | null | undefined, dedupeKey: string, now?: Date): Ledger {
  const at = now instanceof Date && !Number.isNaN(now.getTime()) ? now : new Date()
  const out: Ledger = { ...(ledger && typeof ledger === 'object' ? ledger : {}) }
  out[FIRED_PREFIX + String(dedupeKey)] = at.getTime()
  return out
}