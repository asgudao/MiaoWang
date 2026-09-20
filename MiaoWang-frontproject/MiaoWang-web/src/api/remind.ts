/**
 * 照护提醒接口（Web / TS）—— **手机端 `MiaoWang-phone/api/remind.js` 的同口径落地**
 *
 * 派生 + 按本机设置裁剪 + 真发本机通知。**一个新后端接口都没有。**
 *
 * ============================================================================
 * 数据从哪来
 * ============================================================================
 * - `GET /pet`（`miaowang-pet`）→ 我的宠物
 * - 每只宠物 `GET /health/{pid}/preventive`（`miaowang-healthy`）→ 疫苗/驱虫的 `nextDueAt`
 *   → **`1 + N` 个请求**，宠物之间**并发**，单只宠物失败只把它记进 `degradedPets`
 *   （与 `api/home.ts` 的 `1 + 4N` 同一套降级口径）
 * - 分档文案与到期天数**全部**来自 `api/health.ts` 的 `overdueLabel()` / `sortByDue()`
 * - ⚠️ 后端**没有提醒接口**（143 处 controller mapping 里没有 reminder / plan / schedule / task）
 * - ⚠️ `/health/**` 整个前缀经网关都要登录 → 未登录时**一个请求都不发**（走页内软门槛）
 *
 * ============================================================================
 * 本机设置怎么生效（每一种都接进真实代码路径，不是"只存不用"）
 * ============================================================================
 * | 设置 | 真的影响了什么 | 在哪一行 |
 * |---|---|---|
 * | **总开关** | 关掉后看板一条待办都不列（`suppressedCount` 记着被关掉几条）+ **一条通知都不发** + **弹窗不弹** | `utils/remindSchedule.ts` 的 `buildReminderBoard` / `decideFire` / `decidePopup` |
 * | **提前 N 天** | 决定哪些 `nextDueAt` 进窗口（`逾期 + 今天 + 还有 ≤N 天`）→ 待办条数与通知条数都跟着变 | 同上 `isWithinWindow` |
 * | **免打扰时段** | 到点那一刻落在时段内 → **不发**（且**不记账**，时段过去能补）；弹窗同理 | 同上 `isQuietHours`（在 `decideFire` / `decidePopup` 里真判） |
 * 首页那一处由 `api/home.ts` 的 `clipOverviewByRemind()` 按同一把设置裁剪，
 * 所以"关掉总开关 → 首页待办被裁掉"也是真的，不只是这一页自说自话。
 *
 * ============================================================================
 * 排程能力（**别把浏览器说成"到点自动提醒"**，见 `utils/localNotify.ts` 文件头）
 * ============================================================================
 * - 浏览器**排不了**：关掉标签页没有执行机会 → 页面开着时用 `setTimeout` 兜住最近几条
 *   （`MAX_TIMER_MS` 以上的不挂：长定时器在后台必被节流，回前台重算才是可靠路径）
 * - 到点（或已逾期）的那些：**立即发**，发成功才记 `f:` 账本
 * - **弹窗与通知共用同一把去重键**（`f:<dedupeKeyOf>`）→ 不会"弹窗 + 通知"双响
 */

import { auth } from './request'
import { listMyPets } from './pet'
import { listPreventive } from './health'
import type { PreventiveRecord } from './health'
import { petDisplayName } from '../utils/pet'
import { normalizeSetting, readSetting, writeSetting } from '../utils/remindSetting'
import type { RemindSetting } from '../utils/remindSetting'
import {
  buildReminderBoard,
  decideFire,
  decidePopup,
  dedupeKeyOf,
  emptyBoard,
  markFired,
  noticeBody,
  noticeTitle,
  popupHeading,
  pruneLedger,
  readLedger,
  saveLedger,
} from '../utils/remindSchedule'
import type { Ledger, ReminderBoard, ReminderItem } from '../utils/remindSchedule'
import {
  capability,
  ensurePermission,
  scheduleTruthText,
  sendLocalNotice,
  type LocalNoticeResult,
  type NotifyCapability,
  type PermissionResult,
} from '../utils/localNotify'

/** 前台定时器最多挂几条（挂太多没意义；回前台会整体重算） */
export const MAX_FOREGROUND_TIMERS = 20
/** 超过这个时长的未来提醒不挂 `setTimeout`（长定时器在后台必被节流，回前台重算才是可靠路径） */
export const MAX_TIMER_MS = 12 * 60 * 60 * 1000

/** 当前账号（未登录返回空串；所有读写都用它做存储键后缀） */
export function currentUsername(): string {
  return auth.username || ''
}

/** 当前账号的本机提醒设置（**默认值 = 既有口径**，见 `utils/remindSetting.ts` 文件头） */
export function currentSetting(): RemindSetting {
  return readSetting(currentUsername())
}

/** 保存设置（**写本机 localStorage**，不上后端）；返回落盘后的完整设置 */
export function saveSetting(patch: unknown): RemindSetting {
  return writeSetting(currentUsername(), patch)
}

/** 通知能力 + 本端"到点响"到底靠什么（页面文案直接用，不许各写一份） */
export function notifyState(): { capability: NotifyCapability; truth: string } {
  return { capability: capability(), truth: scheduleTruthText() }
}

/**
 * 申请系统通知权限（**必须在用户点击里调用**：浏览器会拒绝非手势触发的授权请求）
 */
export function requestNotifyPermission(): Promise<PermissionResult> {
  return ensurePermission()
}

/**
 * 派生提醒看板（**未登录 → 零请求**，返回 `loggedIn: false` 的空看板）
 * @param now 可注入（离线断言用；页面不传就是现在）
 */
export async function fetchRemindBoard(now?: Date): Promise<ReminderBoard> {
  const username = currentUsername()
  const setting = readSetting(username)
  if (!username || !auth.token) return emptyBoard(setting)
  const at = now instanceof Date && !Number.isNaN(now.getTime()) ? now : new Date()

  const pets = await listMyPets()
  const list = Array.isArray(pets) ? pets : []
  const briefs = list.map((p) => ({ pid: String(p.pid), name: petDisplayName(p) }))
  if (!briefs.length) {
    const empty = emptyBoard(setting)
    empty.loggedIn = true
    return empty
  }
  // 宠物之间**并发**；单只宠物失败 → 不进 healthByPid → 被 buildReminderBoard 记成 degradedPets
  const rows = await Promise.all(
    briefs.map((b) =>
      listPreventive(Number(b.pid)).then(
        (value) => ({ pid: b.pid, preventive: Array.isArray(value) ? value : [] }),
        () => ({ pid: b.pid, preventive: null as null }),
      ),
    ),
  )
  const healthByPid: Record<string, { preventive: PreventiveRecord[] } | undefined> = {}
  for (const row of rows) {
    if (row.preventive) healthByPid[row.pid] = { preventive: row.preventive }
  }
  return buildReminderBoard({ pets: briefs, healthByPid, setting, now: at })
}

/* ==================================================================
 * 前台定时器（页面开着时的兜底；浏览器没有"排给系统"这回事）
 * ================================================================== */

let foregroundTimers: Array<ReturnType<typeof setTimeout>> = []
let foregroundJobs: ReminderItem[] = []

export function clearRemindTimers(): number {
  for (const t of foregroundTimers) {
    try {
      clearTimeout(t)
    } catch {
      /* 定时器可能已经触发过，清不掉不算错误 */
    }
  }
  const n = foregroundTimers.length
  foregroundTimers = []
  foregroundJobs = []
  return n
}

/** 现在挂着几条前台定时器（离线断言用；不暴露定时器本体） */
export function armedTimerCount(): number {
  return foregroundTimers.length
}

/** 现在挂着哪几条（离线断言用；顺序与挂载顺序一致） */
export function armedTimerKeys(): string[] {
  return foregroundJobs.map((j) => j.key)
}

/** 只发这一条（定时器到点时调；**不重新发请求**） */
async function flushJob(job: ReminderItem): Promise<LocalNoticeResult | null> {
  const username = currentUsername()
  if (!username) return null
  const setting = currentSetting()
  const at = new Date()
  const ledger = pruneLedger(readLedger(username), at)
  const d = decideFire(job, ledger, setting, at)
  if (!d.fire) return null
  const r = await sendLocalNotice({
    title: noticeTitle(),
    body: noticeBody(job),
    tag: job.key,
  })
  // **只有真的发成功才记账**（失败不记 → 下次回前台还会补，不会静默丢一条提醒）
  if (r.ok) saveLedger(username, markFired(ledger, d.dedupeKey, at))
  return r
}

/**
 * 页面在前台时，把最近的未来提醒挂上 `setTimeout`
 * @returns 实际挂了几条
 */
export function armForegroundTimers(board: ReminderBoard | null | undefined, now?: Date): number {
  clearRemindTimers()
  const at = now instanceof Date && !Number.isNaN(now.getTime()) ? now : new Date()
  const jobs = board && Array.isArray(board.jobs) ? board.jobs : []
  let armed = 0
  for (const job of jobs) {
    if (armed >= MAX_FOREGROUND_TIMERS) break
    if (!(job.fireAt instanceof Date) || Number.isNaN(job.fireAt.getTime())) continue
    const ms = job.fireAt.getTime() - at.getTime()
    if (ms <= 0 || ms > MAX_TIMER_MS) continue
    foregroundJobs.push(job)
    foregroundTimers.push(
      setTimeout(() => {
        void flushJob(job)
      }, ms),
    )
    armed++
  }
  return armed
}

/* ==================================================================
 * 一次完整同步：取看板 → 逐条决策 → 发 → 记账
 * ================================================================== */

export interface RemindDecisionView {
  key: string
  petName: string
  title: string
  dueLabel: string
  action: 'fired' | 'skipped'
  reason: string
  ok: boolean
  message: string
}

export interface RemindSyncRun {
  loggedIn: boolean
  board: ReminderBoard
  decisions: RemindDecisionView[]
  results: Array<LocalNoticeResult & { key: string }>
  sent: number
  armed: number
  skipped: string
}

/**
 * @param opts `{ now }`（离线断言注入时间）
 */
export async function syncLocalNotifications(opts?: { now?: Date }): Promise<RemindSyncRun> {
  const o = opts || {}
  const username = currentUsername()
  const at = o.now instanceof Date && !Number.isNaN(o.now.getTime()) ? o.now : new Date()
  const board = await fetchRemindBoard(at)
  const setting = normalizeSetting(board.setting)
  const run: RemindSyncRun = {
    loggedIn: board.loggedIn,
    board,
    decisions: [],
    results: [],
    sent: 0,
    armed: 0,
    skipped: board.loggedIn ? '' : 'guest',
  }
  if (!board.loggedIn) return run

  let ledger: Ledger = pruneLedger(readLedger(username), at)
  const jobs = Array.isArray(board.jobs) ? board.jobs : []

  // 串行发送：系统通知栏不需要并发，而串行让账本顺序可预测（**并发会让去重结果不确定**）
  for (const job of jobs) {
    const fire = decideFire(job, ledger, setting, at)
    if (fire.fire) {
      const r = await sendLocalNotice({
        title: noticeTitle(),
        body: noticeBody(job),
        tag: job.key,
      })
      run.results.push({ ...r, key: job.key })
      run.decisions.push({
        key: job.key,
        petName: job.petName,
        title: job.title,
        dueLabel: job.dueLabel,
        action: 'fired',
        reason: fire.reason,
        ok: r.ok,
        message: r.message,
      })
      if (r.ok) {
        run.sent++
        ledger = markFired(ledger, fire.dedupeKey, at)
        saveLedger(username, ledger)
      }
      continue
    }
    run.decisions.push({
      key: job.key,
      petName: job.petName,
      title: job.title,
      dueLabel: job.dueLabel,
      action: 'skipped',
      reason: fire.reason,
      ok: true,
      message: '',
    })
  }

  run.armed = armForegroundTimers(board, at)
  return run
}

/** 发一条测试通知（设置页的"试一试"按钮；**真的调发送路径**，不是 toast 假动作） */
export function sendTestNotice(now?: Date): Promise<LocalNoticeResult> {
  const at = now instanceof Date && !Number.isNaN(now.getTime()) ? now : new Date()
  const hh = at.getHours()
  const mm = at.getMinutes() < 10 ? `0${at.getMinutes()}` : String(at.getMinutes())
  return sendLocalNotice({
    title: noticeTitle(),
    body: `这是一条测试提醒：喵汪的本机通知是通的（${hh}:${mm}）`,
    tag: 'miaowang-remind-test',
  })
}

/* ==================================================================
 * 软件内弹窗（**主通道**）：状态 + 计算 + 记账 + 关闭
 *
 * 为什么状态放在模块级变量里，而**不新建 Pinia store**：本项目
 * `tools/check-stores.js` 的门禁要求"凡是导出了 `reset*` 的 store，其 reset 必须被
 * 换号清理调用"。与其导出一个"没人调"的 reset（那正是门禁要抓的失败模式），
 * 不如**把账号判定放进每一次读取**：状态里记着算出它的 `username`，
 * **用户名一变立刻作废**（返回空状态）→ 换账号不会看到上一个人的提醒。
 * ================================================================== */

/** `visibilitychange` 与页面挂载会在同一次切换里**都**触发 → 这个窗口内的第二次直接复用，不发第二遍请求 */
export const POPUP_REUSE_MS = 30 * 1000

export interface RemindPopupSnapshot {
  show: boolean
  reason: string
  title: string
  subtitle: string
  items: ReminderItem[]
  overflow: number
  dueTotal: number
  dupTotal: number
  futureTotal: number
  suppressed: number
  aheadDays: number
  quietNow: boolean
  enabled: boolean
  /** 拉不到照护提醒时（网络 / 接口失败）为 true，页面据此**不弹**并如实说明 */
  failed?: boolean
  message?: string
}

const emptyPopupSnapshot = (): RemindPopupSnapshot => ({
  show: false,
  reason: '',
  title: '',
  subtitle: '',
  items: [],
  overflow: 0,
  dueTotal: 0,
  dupTotal: 0,
  futureTotal: 0,
  suppressed: 0,
  aheadDays: 0,
  quietNow: false,
  enabled: true,
})

const popupState: RemindPopupSnapshot & { username: string; checkedAt: number } = {
  ...emptyPopupSnapshot(),
  username: '',
  checkedAt: 0,
}

let popupInFlight: Promise<RemindPopupSnapshot> | null = null

function popupSnapshot(): RemindPopupSnapshot {
  return {
    show: popupState.show,
    reason: popupState.reason,
    title: popupState.title,
    subtitle: popupState.subtitle,
    items: popupState.items.slice(0),
    overflow: popupState.overflow,
    dueTotal: popupState.dueTotal,
    dupTotal: popupState.dupTotal,
    futureTotal: popupState.futureTotal,
    suppressed: popupState.suppressed,
    aheadDays: popupState.aheadDays,
    quietNow: popupState.quietNow,
    enabled: popupState.enabled,
  }
}

function resetPopupState(username: string): RemindPopupSnapshot {
  const snap = emptyPopupSnapshot()
  Object.assign(popupState, snap, { username: username || '', checkedAt: 0 })
  return snap
}

/**
 * 读当前弹窗状态（**纯读，不发请求、不改任何东西**）
 *
 * ⚠️ 账号变了（换号 / 退出）→ 立刻返回空状态并作废缓存。这是本模块**唯一**的"账号隔离"实现。
 */
export function remindPopupState(): RemindPopupSnapshot {
  const username = currentUsername()
  if (!username || popupState.username !== username) return emptyPopupSnapshot()
  return popupSnapshot()
}

/**
 * 算一次"现在该不该弹"（进入站点 / 回前台时调）
 *
 * @param opts `{ now, force }`：`now` 注入时间；`force: true` 跳过 `POPUP_REUSE_MS` 复用窗口
 */
export function fetchRemindPopup(opts?: { now?: Date; force?: boolean }): Promise<RemindPopupSnapshot> {
  const o = opts || {}
  const username = currentUsername()
  const at = o.now instanceof Date && !Number.isNaN(o.now.getTime()) ? o.now : new Date()
  if (!username || !auth.token) {
    resetPopupState(username)
    return Promise.resolve(popupSnapshot())
  }
  if (popupState.username !== username) resetPopupState(username)
  const fresh =
    popupState.checkedAt > 0 && Math.abs(at.getTime() - popupState.checkedAt) < POPUP_REUSE_MS
  if (fresh && !o.force) return Promise.resolve(popupSnapshot())
  if (popupInFlight) return popupInFlight

  popupInFlight = fetchRemindBoard(at)
    .then((board) => {
      const setting = board.setting
      const ledger = pruneLedger(readLedger(username), at)
      const decision = decidePopup(board.items, ledger, setting, at)
      /**
       * ⚠️ 总开关关着时 `board.items` 已经被裁成空数组，`decidePopup` 数出来的 `suppressed` 是 0 ——
       * 于是弹窗状态里"本来有几条"会变成 0，页面就没法说"有 N 条本来会提醒"（等于谎报"什么都没有"）。
       * 被关掉的条数**只有看板知道**（`board.suppressedCount`，由宠物视图逐只累加）→ 这里补上。
       */
      if (decision.reason === 'off') decision.suppressed = Number(board.suppressedCount || 0)
      const head = popupHeading(decision)
      Object.assign(popupState, {
        username,
        checkedAt: at.getTime(),
        show: decision.show,
        reason: decision.reason,
        title: decision.show ? head.title : '',
        subtitle: decision.show ? head.subtitle : '',
        items: decision.items,
        overflow: decision.overflow,
        dueTotal: decision.dueTotal,
        dupTotal: decision.dupTotal,
        futureTotal: decision.futureTotal,
        suppressed: decision.suppressed,
        aheadDays: board.aheadDays,
        quietNow: board.quietNow,
        enabled: board.enabled,
      })
      return popupSnapshot()
    })
    .then(
      (snap) => {
        popupInFlight = null
        return snap
      },
      (e: unknown) => {
        popupInFlight = null
        // 拉不到就**不弹**（宁可不弹，也不要拿旧数据弹一个可能已经取消的提醒）
        resetPopupState(username)
        return {
          ...popupSnapshot(),
          failed: true,
          message: e instanceof Error && e.message ? e.message : '照护提醒没读出来',
        }
      },
    )
  return popupInFlight
}

/**
 * **弹窗真的显示出来了** → 记账（`f:` 前缀，与系统通知共用）
 *
 * ⚠️ 这个函数必须由"渲染弹窗的那一方"在弹窗**真的可见**时调，而不是在 `fetchRemindPopup()` 里顺手记。
 * 因为页面可能已经切到后台（标签页不可见）→ 那时弹窗没被看到，若提前记账，
 * "系统通知兜底"这条路也会被去重挡掉 → **那条提醒就彻底丢了**。
 * @returns 记了几条
 */
export function markPopupShown(now?: Date): number {
  const username = currentUsername()
  if (!username || !popupState.show) return 0
  const at = now instanceof Date && !Number.isNaN(now.getTime()) ? now : new Date()
  let ledger = pruneLedger(readLedger(username), at)
  for (const item of popupState.items) {
    ledger = markFired(ledger, dedupeKeyOf(item, at), at)
  }
  saveLedger(username, ledger)
  return popupState.items.length
}

/** 用户点"知道了" / 右上角关闭：只改状态（记账在 `markPopupShown()` 里已经做过） */
export function closeRemindPopup(): RemindPopupSnapshot {
  popupState.show = false
  return popupSnapshot()
}

/** 强制作废弹窗缓存（改设置之后调：设置一变立刻重算，不等复用窗口过期） */
export function invalidateRemindPopup(): RemindPopupSnapshot {
  popupState.checkedAt = 0
  popupState.show = false
  return popupSnapshot()
}