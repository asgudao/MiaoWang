/**
 * 支付宝沙箱「等待支付」对账会话（Web）
 *
 * 为什么是"对账"而不是"等回调"：局域网开发机上支付宝的异步回调根本打不进来，
 * 所以本地唯一能跑通的路径是「打开沙箱收银台 → 用户付完回到站内 → 主动调
 * `POST /vip/order/{orderNo}/sync` 向后端要状态」。真实支付也是这么做的，
 * 查询接口才是权威来源，回调只是加速。
 *
 * 这个 composable 只做一件事：**管住那个定时器**。
 * - 3 秒一次 sync，最长约 3 分钟，到点如实说"还没查到"，不假装成功
 * - 用户点"立即对账"可以随时插一次（不打断节奏）
 * - 用户点"停止等待"、离开页面（`onUnmounted`）都会把定时器清干净，不留后台轮询
 * - 只有 `payStatus` 变成 1（后端已发会员）才算成功；界面上的 query 一律不可信
 */
import { computed, onUnmounted, ref } from 'vue'
import { ApiError } from '../api/request'
import { PAY_PAID, syncOrder, type VipOrder } from '../api/vip'
import { isSyncUnavailable, payStatusText, vipPayErrorMessage } from '../utils/vip'

/** 轮询间隔：3 秒。支付宝沙箱付款后通常几秒内就能查到 */
export const ALIPAY_POLL_INTERVAL_MS = 3_000
/** 自动对账的总时长：3 分钟。到点停下并如实说明，用户可手动再对账 */
export const ALIPAY_POLL_WINDOW_MS = 3 * 60 * 1_000
/** 连续失败几次就停止自动对账（避免网络抖动时一直空转） */
const MAX_ERROR_STREAK = 3

export type AlipayWaitPhase = 'idle' | 'waiting' | 'paid' | 'timeout' | 'failed' | 'stopped'

export interface VipAlipayWaitOptions {
  /** 查到已支付（后端已发会员）后的回调，用来刷新权益与订单列表 */
  onPaid?: (order: VipOrder) => void | Promise<void>
}

export function useVipAlipayWait(options: VipAlipayWaitOptions = {}) {
  const order = ref<VipOrder | null>(null)
  /** 沙箱收银台地址；用户自己点开的那条链接也用它 */
  const payUrl = ref('')
  const phase = ref<AlipayWaitPhase>('idle')
  const notice = ref('')
  const error = ref('')
  /** 已经对账过几次（界面显示，也让"没查到"看起来不像卡住了） */
  const checks = ref(0)
  const secondsLeft = ref(0)
  const syncing = ref(false)

  let tickTimer: number | undefined
  let pollTimer: number | undefined
  let deadline = 0
  let errorStreak = 0

  function clearTimers() {
    if (tickTimer !== undefined) {
      window.clearInterval(tickTimer)
      tickTimer = undefined
    }
    if (pollTimer !== undefined) {
      window.clearInterval(pollTimer)
      pollTimer = undefined
    }
  }

  /** 倒计时文案：`2:41`；已经用完时给 `0:00` */
  const countdownText = computed(() => {
    const total = Math.max(0, secondsLeft.value)
    const m = Math.floor(total / 60)
    const s = total % 60
    return `${m}:${String(s).padStart(2, '0')}`
  })

  /** 会话是否还占着界面（`idle` 时面板整个不渲染） */
  const active = computed(() => phase.value !== 'idle' && order.value !== null)

  /** 正在等：只有这个阶段才有倒计时与自动轮询 */
  const polling = computed(() => phase.value === 'waiting')

  function markTimeout() {
    clearTimers()
    phase.value = 'timeout'
    notice.value =
      '等了 3 分钟，后端还是没从支付宝查到这笔支付。沙箱偶尔就是慢，你可以稍后点「立即对账」，' +
      '也可以到下面的订单列表看这笔单的状态。会员要等查到支付之后才会发放。'
  }

  /**
   * 对账一次。
   *
   * 判错分两档：
   * - **接口/渠道本身没就绪**（未映射路径的 `B000001`、网络不通、渠道未支持 `B000513`）
   *   → 立刻停下并说清楚，不让用户白等 3 分钟
   * - 其它错误 → 先记着继续轮询，连续 3 次才停（网络抖一下不该把等待打断）
   */
  async function checkNow() {
    if (syncing.value) return
    // 已经确认支付就别再打了（`paid` 之后定时器已清，这里只是兜一层）
    if (phase.value === 'paid') return
    const current = order.value
    if (!current) return

    /** 这一轮刚查到已支付；刷新列表放在 try 外面，刷新失败不该把"已确认"翻回去 */
    let justPaid: VipOrder | null = null

    syncing.value = true
    try {
      const updated = await syncOrder(current.orderNo)
      order.value = updated
      checks.value += 1
      errorStreak = 0
      error.value = ''

      if (updated.payStatus === PAY_PAID) {
        clearTimers()
        phase.value = 'paid'
        notice.value =
          `后端已经从支付宝查到这笔支付，${updated.planName} 的会员时长发放完成。` +
          '这是支付宝沙箱，测试环境，没有真实扣款。'
        justPaid = updated
      } else {
        notice.value = `第 ${checks.value} 次对账：${payStatusText(updated)}。付完款后留在本页等一会儿，查到就会自动停。`
      }
    } catch (e) {
      checks.value += 1
      error.value = vipPayErrorMessage(e)

      if (isSyncUnavailable(e) || (e instanceof ApiError && e.isUnauthorized)) {
        clearTimers()
        phase.value = 'failed'
      } else if (++errorStreak >= MAX_ERROR_STREAK) {
        clearTimers()
        phase.value = 'failed'
        error.value = `${error.value} 连续 ${MAX_ERROR_STREAK} 次都没查到，先停止自动对账。你可以点「立即对账」再试。`
      }
    } finally {
      syncing.value = false
    }

    if (justPaid) {
      try {
        await options.onPaid?.(justPaid)
      } catch {
        // 会员已经发放，刷新权益与订单列表失败只是界面没跟上，不改"已确认"这个结论
      }
    }
  }

  /** 起两个定时器：1 秒的倒计时 + 3 秒的对账。任何地方都只通过它俩调度，清的时候一起清 */
  function armTimers() {
    clearTimers()
    deadline = Date.now() + ALIPAY_POLL_WINDOW_MS
    secondsLeft.value = Math.ceil(ALIPAY_POLL_WINDOW_MS / 1000)

    tickTimer = window.setInterval(() => {
      const left = Math.max(0, Math.ceil((deadline - Date.now()) / 1000))
      secondsLeft.value = left
      if (left <= 0 && phase.value === 'waiting') markTimeout()
    }, 1_000)

    pollTimer = window.setInterval(() => {
      void checkNow()
    }, ALIPAY_POLL_INTERVAL_MS)
  }

  /** 开始等待：进入页面后的第一次对账在 3 秒后（刚下单必然还是待支付，没必要立刻打一次） */
  function start(nextOrder: VipOrder, url?: string) {
    order.value = nextOrder
    payUrl.value = url ?? nextOrder.payParams?.payUrl ?? ''
    phase.value = 'waiting'
    notice.value = ''
    error.value = ''
    checks.value = 0
    errorStreak = 0
    armTimers()
  }

  /** 用户主动停止等待（不是错误，订单仍然待支付） */
  function stop() {
    clearTimers()
    if (phase.value === 'waiting') {
      phase.value = 'stopped'
      notice.value =
        '已经停止自动对账。这笔订单还是待支付状态，30 分钟没付会自动关闭，付过了可以点「立即对账」再查。'
    }
  }

  /** 关掉这个面板，回到档位列表 */
  function dismiss() {
    clearTimers()
    phase.value = 'idle'
    order.value = null
    payUrl.value = ''
    notice.value = ''
    error.value = ''
    checks.value = 0
  }

  /**
   * 「立即对账」在 stopped / timeout / failed 阶段都能用。
   * 还在 waiting 时它只是插一次查询（不打断倒计时）；已经停下时它同时把自动对账重新打开。
   */
  function retry() {
    const current = order.value
    if (!current) return
    if (phase.value !== 'waiting') {
      phase.value = 'waiting'
      error.value = ''
      notice.value = ''
      errorStreak = 0
      armTimers()
    }
    void checkNow()
  }

  // 离开页面必须清定时器：这是"不要留后台定时器"的落点
  onUnmounted(clearTimers)

  return {
    order,
    payUrl,
    phase,
    notice,
    error,
    checks,
    syncing,
    countdownText,
    secondsLeft,
    active,
    polling,
    start,
    stop,
    dismiss,
    retry,
    checkNow,
  }
}