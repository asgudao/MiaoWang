/**
 * 照护提醒接口（手机端 / JS）：**从既有健康记录派生 + 按本机设置裁剪 + 真发本机通知**
 *
 * ============================================================================
 * 数据从哪来（**一个新后端接口都没有**）
 * ============================================================================
 * - `GET /pet`（`miaowang-pet`）→ 我的宠物
 * - 每只宠物 `GET /health/{pid}/preventive`（`miaowang-healthy`）→ 疫苗/驱虫的 `nextDueAt`
 *   → **`1 + N` 个请求**（宠物上限免费 2 / 会员 5 → 最多 6 个），宠物之间**并发**，
 *   单只宠物失败只把它记进 `degradedPets`（与 `api/home.js` 的 `1 + 4N` 同一套降级口径）
 * - 分档文案与到期天数**全部**来自 `utils/health.js` 的 `dueInfo()` / `sortedByDue()`
 *   （首页与健康档案页共用的唯一真源，本切片**不另造一套阈值**）
 * - ⚠️ 后端**没有提醒接口**（143 处 controller mapping 里没有 reminder / plan / schedule / task，
 *   7 条不存在性探针全 `B000001`；`GlobalExceptionHandler` 把 404/405 一并兜底 → 别靠 404 判断）
 * - ⚠️ `/health/**` 整个前缀经网关都要登录 → 未登录时**一个请求都不发**（走页内软门槛）
 *
 * ============================================================================
 * 本机设置怎么生效（每一种都接进真实代码路径，不是"只存不用"）
 * ============================================================================
 * | 设置 | 真的影响了什么 | 在哪一行 |
 * |---|---|---|
 * | **总开关** | 关掉后看板一条待办都不列（`suppressedCount` 记着被关掉几条）+ **一条通知都不发** | `utils/remind-schedule.js` 的 `buildReminderBoard` / `decideFire` / `decideSchedule` |
 * | **提前 N 天** | 决定哪些 `nextDueAt` 进窗口（`逾期 + 今天 + 还有 ≤N 天`）→ 待办条数与通知条数都跟着变 | 同上 `isWithinWindow` |
 * | **免打扰时段** | 到点那一刻落在时段内 → **不发**；`fireAt` 本身落在时段内 → **不排给系统** | 同上 `isQuietHours`（在 `decideFire` / `decideSchedule` 里真判） |
 * 首页那一处由 `api/home.js` 按同一把设置裁剪（见那边的注释），所以"关掉总开关 → 首页待办被裁掉"
 * 也是真的，不只是这一页自说自话。
 *
 * ============================================================================
 * 排程能力（**别把 H5 说成"到点自动提醒"**，见 `utils/local-notify.js` 文件头）
 * ============================================================================
 * - **App**：未来的提醒用 `uni.createPushMessage` 的 `delay`（秒）**交给原生层** → 这是本切片唯一的"真排程"；
 *   已排过的记 `s:` 账本，避免每次回前台重排一遍、同一条响 N 遍
 * - **H5**：排不了（关掉标签页没有执行机会）→ 页面开着时用 `setTimeout` 兜住最近几条 + 每次 onShow 补发
 * - 到点（或已逾期）的那些：**立即发**，发成功才记 `f:` 账本
 */

import { auth } from '../utils/request.js'
import { listMyPets } from './pet.js'
import { listPreventive } from './health.js'
import { petDisplayName } from '../utils/pet.js'
import { normalizeSetting, readSetting, writeSetting } from '../utils/remind-setting.js'
import {
  buildReminderBoard,
  decideFire,
  decidePopup,
  decideSchedule,
  dedupeKeyOf,
  emptyBoard,
  markFired,
  markScheduled,
  noticeBody,
  noticeTitle,
  popupHeading,
  pruneLedger,
  readLedger,
  saveLedger
} from '../utils/remind-schedule.js'
import { capability, ensurePermission, scheduleTruthText, sendLocalNotice } from '../utils/local-notify.js'

/** H5 前台定时器最多挂几条（挂太多没意义；回前台会整体重算） */
export const MAX_FOREGROUND_TIMERS = 20
/** 超过这个时长的未来提醒不挂 `setTimeout`（长定时器在后台必被节流，回前台重算才是可靠路径） */
export const MAX_TIMER_MS = 12 * 60 * 60 * 1000

/** 当前账号（未登录返回空串；所有读写都用它做存储键后缀） */
export function currentUsername () {
  return auth.getUsername() || ''
}

/** 当前账号的本机提醒设置（**默认值 = 既有口径**，见 `utils/remind-setting.js` 文件头） */
export function currentSetting () {
  return readSetting(currentUsername())
}

/**
 * 保存设置（**写本机存储**，不上后端）
 * @returns {object} 落盘后的完整设置
 */
export function saveSetting (patch) {
  return writeSetting(currentUsername(), patch)
}

/** 通知能力 + 本端"到点响"到底靠什么（页面文案直接用，不许各写一份） */
export function notifyState () {
  return {
    capability: capability(),
    truth: scheduleTruthText()
  }
}

/**
 * 申请系统通知权限（**只有 H5 有这个 JS API**；App 端由系统管、小程序没有）
 *
 * ⚠️ 必须在**用户点击**里调用：MDN 明确说浏览器会拒绝非手势触发的授权请求，
 * 所以设置页把它挂在按钮的 `@click` 上，绝不在 onLoad 里自动申请（那会被静默拒绝）。
 */
export function requestNotifyPermission () {
  return ensurePermission()
}

/**
 * 派生提醒看板（**未登录 → 零请求**，返回 `loggedIn: false` 的空看板）
 * @param {Date} [now] 可注入（离线断言用；页面不传就是现在）
 * @returns {Promise<object>} `utils/remind-schedule.js` 的 `buildReminderBoard()` 产物
 */
export function fetchRemindBoard (now) {
  const username = currentUsername()
  const setting = readSetting(username)
  if (!username || !auth.getToken()) return Promise.resolve(emptyBoard(setting))
  const at = now instanceof Date && !isNaN(now.getTime()) ? now : new Date()

  return listMyPets().then(function (pets) {
    const list = Array.isArray(pets) ? pets : []
    const briefs = []
    for (let i = 0; i < list.length; i++) {
      briefs.push({ pid: String(list[i].pid), name: petDisplayName(list[i]) })
    }
    if (!briefs.length) {
      const empty = emptyBoard(setting)
      empty.loggedIn = true
      return empty
    }
    // 宠物之间**并发**；单只宠物失败 → 不进 healthByPid → 被 buildReminderBoard 记成 degradedPets
    const jobs = briefs.map(function (b) {
      return listPreventive(b.pid).then(
        function (value) {
          return { pid: b.pid, preventive: Array.isArray(value) ? value : [] }
        },
        function () {
          return { pid: b.pid, preventive: null }
        }
      )
    })
    return Promise.all(jobs).then(function (rows) {
      const byPid = {}
      for (let i = 0; i < rows.length; i++) {
        if (rows[i].preventive) byPid[rows[i].pid] = { preventive: rows[i].preventive }
      }
      return buildReminderBoard({ pets: briefs, healthByPid: byPid, setting: setting, now: at })
    })
  })
}

/* ==================================================================
 * H5 前台定时器（页面开着时的兜底；App 走原生 delay，不需要它）
 * ================================================================== */

let foregroundTimers = []

export function clearRemindTimers () {
  for (let i = 0; i < foregroundTimers.length; i++) {
    try {
      clearTimeout(foregroundTimers[i])
    } catch (e) {}
  }
  foregroundTimers = []
  return 0
}

/** 只发这一条（定时器到点时调；**不重新发请求**） */
function flushJob (job) {
  const username = currentUsername()
  if (!username) return Promise.resolve(null)
  const setting = currentSetting()
  const at = new Date()
  const ledger = pruneLedger(readLedger(username), at)
  const d = decideFire(job, ledger, setting, at)
  if (!d.fire) return Promise.resolve(null)
  return sendLocalNotice({
    title: noticeTitle(),
    content: noticeBody(job),
    payload: { key: job.key, pid: job.pid, petId: job.petId },
    tag: job.key
  }).then(function (r) {
    // **只有真的发成功才记账**（失败不记 → 下次 onShow 还会补，不会静默丢一条提醒）
    if (r.ok) saveLedger(username, markFired(ledger, d.dedupeKey, at))
    return r
  })
}

/**
 * 页面在前台时，把最近的未来提醒挂上 `setTimeout`（**只在 H5 做**）
 * @returns {number} 实际挂了几条
 */
export function armForegroundTimers (board, now) {
  clearRemindTimers()
  if (capability().platform !== 'h5') return 0
  const at = now instanceof Date && !isNaN(now.getTime()) ? now : new Date()
  const jobs = board && Array.isArray(board.jobs) ? board.jobs : []
  let armed = 0
  for (let i = 0; i < jobs.length && armed < MAX_FOREGROUND_TIMERS; i++) {
    const job = jobs[i]
    if (!(job.fireAt instanceof Date) || isNaN(job.fireAt.getTime())) continue
    const ms = job.fireAt.getTime() - at.getTime()
    if (ms <= 0 || ms > MAX_TIMER_MS) continue
    foregroundTimers.push(
      setTimeout(function () {
        flushJob(job)
      }, ms)
    )
    armed++
  }
  return armed
}

/* ==================================================================
 * 一次完整同步：取看板 → 逐条决策 → 发/排 → 记账
 * ================================================================== */

/**
 * @param {object} [opts] `{ now }`（离线断言注入时间）
 * @returns {Promise<{loggedIn:boolean, board:object, decisions:object[], results:object[],
 *                    sent:number, scheduled:number, armed:number, skipped:string}>}
 */
export function syncLocalNotifications (opts) {
  const o = opts || {}
  const username = currentUsername()
  const at = o.now instanceof Date && !isNaN(o.now.getTime()) ? o.now : new Date()
  const cap = capability()

  return fetchRemindBoard(at).then(function (board) {
    const setting = normalizeSetting(board.setting)
    const run = {
      loggedIn: board.loggedIn,
      board: board,
      decisions: [],
      results: [],
      sent: 0,
      scheduled: 0,
      armed: 0,
      skipped: board.loggedIn ? '' : 'guest'
    }
    if (!board.loggedIn) return run

    let ledger = pruneLedger(readLedger(username), at)
    const jobs = Array.isArray(board.jobs) ? board.jobs : []

    // 串行发送：系统通知栏不需要并发，而串行让账本顺序可预测（**并发会让去重结果不确定**）
    let chain = Promise.resolve()
    jobs.forEach(function (job) {
      chain = chain.then(function () {
        const fire = decideFire(job, ledger, setting, at)
        if (fire.fire) {
          return sendLocalNotice({
            title: noticeTitle(),
            content: noticeBody(job),
            payload: { key: job.key, pid: job.pid, petId: job.petId },
            tag: job.key
          }).then(function (r) {
            run.results.push(Object.assign({ key: job.key }, r))
            run.decisions.push({
              key: job.key,
              petName: job.petName,
              title: job.title,
              dueLabel: job.dueLabel,
              action: 'fired',
              reason: fire.reason,
              ok: r.ok,
              message: r.message
            })
            if (r.ok) {
              run.sent++
              ledger = markFired(ledger, fire.dedupeKey, at)
              saveLedger(username, ledger)
            }
            return null
          })
        }

        const sch = decideSchedule(job, ledger, setting, at)
        if (sch.reason === 'ok' && !cap.supportsSchedule) {
          // 平台不支持把提醒排给系统（H5 / 小程序）→ 如实记一条，别假装排上了
          run.decisions.push({
            key: job.key,
            petName: job.petName,
            title: job.title,
            dueLabel: job.dueLabel,
            action: 'not-schedulable',
            reason: 'unsupported',
            ok: true,
            message: '当前平台不能把提醒排给系统，只能页面开着时提醒'
          })
          return null
        }
        if (sch.schedule) {
          return sendLocalNotice({
            title: noticeTitle(),
            content: noticeBody(job),
            payload: { key: job.key, pid: job.pid, petId: job.petId },
            tag: job.key,
            delaySeconds: sch.delaySeconds,
            autoCloseMs: 0
          }).then(function (r) {
            run.results.push(Object.assign({ key: job.key, scheduled: true }, r))
            run.decisions.push({
              key: job.key,
              petName: job.petName,
              title: job.title,
              dueLabel: job.dueLabel,
              action: 'scheduled',
              reason: sch.reason,
              ok: r.ok,
              message: r.message,
              delaySeconds: sch.delaySeconds
            })
            if (r.ok) {
              run.scheduled++
              ledger = markScheduled(ledger, sch.scheduleKey, at)
              saveLedger(username, ledger)
            }
            return null
          })
        }
        run.decisions.push({
          key: job.key,
          petName: job.petName,
          title: job.title,
          dueLabel: job.dueLabel,
          action: 'skipped',
          /** ⚠️ 这里的 reason 是**给汇总/页面看的真实原因**，别让排程那边的 `past` 盖掉它：
           *  逾期项当天已发过时 `decideFire` 判 `duplicate`、而 `decideSchedule` 判 `past`
           *  （到点已过，不归它管）→ 取后者就会把"今天已经提醒过"说成"提醒时刻已过"，
           *  汇总里 `duplicate` 永远是 0，用户看到"今天已提醒过 0 条"（不实）。 */
          reason: fire.reason === 'not-yet'
            ? (sch.reason && sch.reason !== 'past' ? sch.reason : 'not-yet')
            : fire.reason,
          ok: true,
          message: ''
        })
        return null
      })
    })

    return chain.then(function () {
      run.armed = armForegroundTimers(board, at)
      return run
    })
  })
}

/** 发一条测试通知（设置页的"试一试"按钮；**真的调发送路径**，不是 toast 假动作） */
export function sendTestNotice (now) {
  const at = now instanceof Date && !isNaN(now.getTime()) ? now : new Date()
  return sendLocalNotice({
    title: noticeTitle(),
    content: '这是一条测试提醒：喵汪的本机通知是通的（' + at.getHours() + ':' + (at.getMinutes() < 10 ? '0' : '') + at.getMinutes() + '）',
    payload: { test: true },
    tag: 'miaowang-remind-test'
  })
}

/* ==================================================================
 * 软件内弹窗（**主通道**）：状态 + 计算 + 记账 + 关闭
 *
 * 为什么状态放在这个模块的模块级变量里，而**不新建 `store/remind.js`**：
 * `tools/check-stores.js` 的门禁要求"凡是导出了 `reset*` 的 store，其 reset 必须被
 * `store/auth.js` 的 `resetAccountStores()` 调用"——而那个文件不在本切片的可改范围里。
 * 与其导出一个"没人调"的 reset（那正是门禁要抓的失败模式），不如**把账号判定放进每一次读取**：
 * 状态里记着算出它的 `username`，**用户名一变立刻作废**（返回空状态）→ 换账号不会看到上一个人的提醒。
 * ================================================================== */

/** App.onShow 与 首页.onShow 会在同一次前后台切换里**都**触发 → 这个窗口内的第二次直接复用，不发第二遍请求 */
export const POPUP_REUSE_MS = 30 * 1000

const popupState = {
  username: '',
  checkedAt: 0,
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
  enabled: true
}

let popupInFlight = null

function emptyPopupSnapshot () {
  return {
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
    enabled: true
  }
}

function popupSnapshot () {
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
    enabled: popupState.enabled
  }
}

function resetPopupState (username) {
  const snap = emptyPopupSnapshot()
  popupState.username = username || ''
  popupState.checkedAt = 0
  popupState.show = false
  popupState.reason = ''
  popupState.title = ''
  popupState.subtitle = ''
  popupState.items = []
  popupState.overflow = 0
  popupState.dueTotal = 0
  popupState.dupTotal = 0
  popupState.futureTotal = 0
  popupState.suppressed = 0
  popupState.aheadDays = 0
  popupState.quietNow = false
  popupState.enabled = true
  return snap
}

/**
 * 读当前弹窗状态（**纯读，不发请求、不改任何东西**）
 *
 * ⚠️ 账号变了（换号 / 退出）→ 立刻返回空状态并作废缓存。这是本模块**唯一**的"账号隔离"实现，
 * 不做 `reset()` 导出（理由见上面那段注释）。
 */
export function remindPopupState () {
  const username = currentUsername()
  if (!username || popupState.username !== username) return emptyPopupSnapshot()
  return popupSnapshot()
}

/**
 * 算一次"现在该不该弹"（进入 App / 回前台 / 回到首页时调）
 *
 * @param {object} [opts] `{ now, force }`
 *   `now` 注入时间（离线断言用）；`force: true` 跳过 `POPUP_REUSE_MS` 复用窗口
 * @returns {Promise<object>} 弹窗状态快照（形状见 `emptyPopupSnapshot()`）
 */
export function fetchRemindPopup (opts) {
  const o = opts || {}
  const username = currentUsername()
  const at = o.now instanceof Date && !isNaN(o.now.getTime()) ? o.now : new Date()
  if (!username || !auth.getToken()) {
    resetPopupState(username)
    return Promise.resolve(popupSnapshot())
  }
  if (popupState.username !== username) resetPopupState(username)
  const fresh = popupState.checkedAt > 0 && Math.abs(at.getTime() - popupState.checkedAt) < POPUP_REUSE_MS
  if (fresh && !o.force) return Promise.resolve(popupSnapshot())
  if (popupInFlight) return popupInFlight

  popupInFlight = fetchRemindBoard(at)
    .then(function (board) {
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
      popupState.username = username
      popupState.checkedAt = at.getTime()
      popupState.show = decision.show
      popupState.reason = decision.reason
      popupState.title = decision.show ? head.title : ''
      popupState.subtitle = decision.show ? head.subtitle : ''
      popupState.items = decision.items
      popupState.overflow = decision.overflow
      popupState.dueTotal = decision.dueTotal
      popupState.dupTotal = decision.dupTotal
      popupState.futureTotal = decision.futureTotal
      popupState.suppressed = decision.suppressed
      popupState.aheadDays = board.aheadDays
      popupState.quietNow = board.quietNow
      popupState.enabled = board.enabled
      return popupSnapshot()
    })
    .then(
      function (snap) {
        popupInFlight = null
        return snap
      },
      function (e) {
        popupInFlight = null
        // 拉不到就**不弹**（宁可不弹，也不要拿旧数据弹一个可能已经取消的提醒）
        resetPopupState(username)
        return Object.assign(popupSnapshot(), { failed: true, message: (e && e.message) || '照护提醒没读出来' })
      }
    )
  return popupInFlight
}

/**
 * **弹窗真的显示出来了** → 记账（`f:` 前缀，与系统通知共用）
 *
 * ⚠️ 这个函数必须由"渲染弹窗的那一方"在弹窗**真的可见**时调，而不是在 `fetchRemindPopup()` 里顺手记。
 * 因为首页可能不是当前页（用户停在别的 tab）→ 那时弹窗没渲染，若提前记账，
 * "系统通知兜底"这条路也会被去重挡掉 → **那条提醒就彻底丢了**。
 * @returns {number} 记了几条
 */
export function markPopupShown (now) {
  const username = currentUsername()
  if (!username || !popupState.show) return 0
  const at = now instanceof Date && !isNaN(now.getTime()) ? now : new Date()
  let ledger = pruneLedger(readLedger(username), at)
  for (let i = 0; i < popupState.items.length; i++) {
    ledger = markFired(ledger, dedupeKeyOf(popupState.items[i], at), at)
  }
  saveLedger(username, ledger)
  return popupState.items.length
}

/** 用户点"知道了" / 右上角关闭：只改状态（记账在 `markPopupShown()` 里已经做过） */
export function closeRemindPopup () {
  popupState.show = false
  return popupSnapshot()
}

/** 强制作废弹窗缓存（改设置之后调：设置一变立刻重算，不等复用窗口过期） */
export function invalidateRemindPopup () {
  popupState.checkedAt = 0
  popupState.show = false
  return popupSnapshot()
}