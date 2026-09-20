/**
 * 会员展示工具（手机端 / JS，与 Web 端 utils/vip 镜像）
 *
 * 为什么单独放一个文件：手机端**模板里不能调函数**（Options API 的模板只认实例属性，
 * 列表数据必须在 computed 里摊平成显示字段）。所以这里只放**纯函数**，
 * 由页面 computed 调用一次把原始 DTO 变成"可以直接 {{ }} 出来"的字段。
 *
 * 核心口径（别在别处重复实现）：
 * - `-1` = **不限次数**，界面上绝不显示 "-1 次"
 * - 价格字段是**元**，固定两位小数
 * - `ragPeriod` / `chatPeriod`：DAY=每日、MONTH=每月（benefit 里另有已翻译好的 `*PeriodDesc`）
 * - 所有权益数值**一律取接口返回**，本文件一个档位数值都不写死
 * - 渠道是 mock：支付相关文案必须如实写"模拟支付（渠道为 mock）"
 */

/** 价格：4.9 → "4.90"；0 → "0.00"（0 元是促销兑换单） */
export function formatPrice (value) {
  const n = Number(value)
  if (value === null || value === undefined || isNaN(n)) return '0.00'
  return n.toFixed(2)
}

/**
 * 次数：-1 或负数 = 不限。
 * 注意返回值**不带单位**（调用处自己接"/每月"或"次"），否则会出现"5 次/每月"这种糙文案。
 */
export function formatValue (value) {
  const n = Number(value)
  if (value === null || value === undefined || isNaN(n)) return '不提供'
  if (n < 0) return '不限'
  return String(n)
}

/** 条数上限（健康记录条数 / 提醒条数）：单位不是"次" */
export function formatCount (value) {
  const n = Number(value)
  if (value === null || value === undefined || isNaN(n)) return '不提供'
  if (n < 0) return '不限'
  return String(n) + ' 条'
}

/** 只数上限（宠物位）：按"只"计数 */
export function formatPets (value) {
  const n = Number(value)
  if (value === null || value === undefined || isNaN(n)) return '不提供'
  if (n < 0) return '不限'
  return String(n) + ' 只'
}

/** 0/1 开关 */
export function formatSwitch (value) {
  return Number(value) === 1 ? '支持' : '不支持'
}

/** DAY/MONTH → 每日/每月（中文说明也是后端给的，这里只做兜底翻译） */
export function periodLabel (period, desc) {
  if (desc) return desc
  if (period === 'DAY') return '每日'
  if (period === 'MONTH') return '每月'
  return ''
}

/** 天数：-1/0/空 都不编，直接说"不提供" */
export function formatDays (value) {
  const n = Number(value)
  if (value === null || value === undefined || isNaN(n) || n <= 0) return ''
  return String(n) + ' 天'
}

function pad (n) {
  return n < 10 ? '0' + n : String(n)
}

/**
 * 后端时间 → "2027-09-17"。
 * 后端 `Date` 走 Jackson 默认序列化（ISO-8601 带时区，如 2027-09-17T11:48:07.000+00:00），
 * 但这里**不解析字符串**，统一交给 `new Date()`，兼容性最好。
 */
export function formatDate (value) {
  if (!value) return ''
  const d = new Date(value)
  if (isNaN(d.getTime())) return ''
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
}

/** 时间 → "2027-09-17 11:48" */
export function formatDateTime (value) {
  const day = formatDate(value)
  if (!day) return ''
  const d = new Date(value)
  return day + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes())
}

/** 相对时间（订单列表用）：刚刚 / x 分钟前 / x 小时前 / 日期 */
export function relativeTime (value) {
  if (!value) return ''
  const d = new Date(value)
  if (isNaN(d.getTime())) return ''
  const diff = Date.now() - d.getTime()
  if (diff < 0) return formatDateTime(value)
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + ' 分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + ' 小时前'
  if (diff < 604800000) return Math.floor(diff / 86400000) + ' 天前'
  return formatDate(value)
}

/** 距离到期还有多少天（已过期返回负数）；拿不到时间返回 null */
export function daysLeft (value) {
  if (!value) return null
  const d = new Date(value)
  if (isNaN(d.getTime())) return null
  return Math.ceil((d.getTime() - Date.now()) / 86400000)
}

/**
 * 一个档位的权益 → 可直接渲染的 [{ label, value }]
 * ⚠️ 数值全部来自入参（本文件一个档位数值都没写死）；后端 DTO 加字段就在这里补一行。
 * @param {object} plan VipPlanRespDTO（或 benefit，两者权益字段同名同义）
 */
export function planBenefits (plan) {
  const p = plan || {}
  return [
    { label: '可养宠物', value: formatPets(p.petLimitPerType) },
    { label: 'AI 问答', value: benefitQuota(p.ragLimit, p.ragPeriod, p.ragPeriodDesc) },
    { label: 'AI 聊天', value: benefitQuota(p.chatLimit, p.chatPeriod, p.chatPeriodDesc) },
    { label: '每类健康记录', value: formatCount(p.healthRecordLimit) },
    { label: '导出健康报告', value: formatSwitch(p.healthExport) },
    { label: 'AI 解读症状', value: formatSwitch(p.healthAiAnalysis) },
    { label: '照护提醒', value: formatCount(p.reminderLimit) }
  ]
}

/**
 * 额度一句话：`5 次/每月`、`不限次数`、`不限次数/每日`
 * （不限次数时周期还是有意义的：后端对不限次数不计数，"每日"就是"每天都不限"）
 */
export function benefitQuota (limit, period, desc) {
  const n = Number(limit)
  if (limit === null || limit === undefined || isNaN(n)) return '不提供'
  const per = periodLabel(period, desc)
  if (n < 0) return per ? '不限次数/' + per : '不限次数'
  const base = String(n) + ' 次'
  return per ? base + '/' + per : base
}

/**
 * 档位卡：把 VipPlanRespDTO 摊平成模板能直接吃的字段
 * @param {object} plan
 * @param {string} selected 当前选中的 planCode
 */
export function planCard (plan, selected) {
  const p = plan || {}
  const code = p.planCode
  const days = Number(p.durationDays)
  return {
    planCode: code,
    planName: p.planName || code,
    price: formatPrice(p.price),
    priceValue: Number(p.price),
    duration: formatDays(p.durationDays),
    /** 价格后缀：30 天 → "/30 天"；拿不到天数就不编 */
    per: days > 0 ? '/' + days + ' 天' : '',
    tagline: planTagline(p),
    benefits: planBenefits(p),
    selected: code === selected
  }
}

/** 一个档位能一句话说清的点（卡片副标题，避免整页都是等长权益表） */
export function planTagline (plan) {
  const p = plan || {}
  const rag = Number(p.ragLimit)
  const chat = Number(p.chatLimit)
  const parts = []
  if (rag < 0) parts.push('AI 问答不限次数')
  else if (rag > 0) parts.push('AI 问答 ' + rag + ' 次/' + periodLabel(p.ragPeriod, p.ragPeriodDesc))
  if (chat < 0) parts.push('聊天不限轮次')
  else if (chat > 0) parts.push('聊天 ' + chat + ' 轮/' + periodLabel(p.chatPeriod, p.chatPeriodDesc))
  return parts.join('，')
}

/** 支付状态中文：后端已给 payStatusDesc，这里只兜底翻译 */
export function payStatusText (status, desc) {
  if (desc) return desc
  const n = Number(status)
  if (n === 0) return '待支付'
  if (n === 1) return '已支付'
  if (n === 2) return '已关闭'
  if (n === 3) return '已退款'
  return '状态未知'
}

/** 优惠/促销信息：有优惠才有值（展示用，不改金额口径） */
export function orderDiscountText (order) {
  const o = order || {}
  const discount = Number(o.discountAmount)
  const promo = o.promoCode ? String(o.promoCode) : ''
  if (promo && discount > 0) return promo + ' 立减 ' + formatPrice(o.discountAmount) + ' 元'
  if (promo) return promo
  return ''
}

/**
 * 订单卡：把 VipOrderRespDTO 摊平成模板字段
 *
 * 三个必须算对的显示口径：
 * - **实付** = `payableAmount`（不是 amount；amount 是档位原价）
 * - 0 元订单是促销兑换单（**不能退款**，后端 B000527）
 * - 已支付单是否还在 7 天自助退款窗口内由前端算个提示，真正把关在后端
 */
export function orderCard (order) {
  const o = order || {}
  const status = Number(o.payStatus)
  const payable = Number(o.payableAmount)
  const isFree = !isNaN(payable) && payable === 0
  const paid = status === 1
  return {
    orderNo: o.orderNo || '',
    planName: o.planName || o.planCode || '',
    payable: formatPrice(o.payableAmount),
    amount: formatPrice(o.amount),
    discountText: orderDiscountText(o),
    status: status,
    statusText: payStatusText(o.payStatus, o.payStatusDesc),
    statusClass: statusClass(status),
    isPending: status === 0,
    isPaid: paid,
    isClosed: status === 2,
    isRefunded: status === 3,
    isFree: isFree,
    canRefund: paid && !isFree,
    /** 原价只在有优惠时才显示（否则和实付一样，属于噪音） */
    showOrigin: Number(o.discountAmount) > 0,
    // 展示口径：已支付但超过 7 天，后端会拒（B000518），前端先标出来
    refundExpired: paid && !isFree && exceededRefundWindow(o),
    createdText: relativeTime(o.createTime),
    createdFull: formatDateTime(o.createTime),
    durationText: formatDays(o.durationDays),
    expireText: o.expireTime ? formatDate(o.expireTime) : '',
    payTypeText: o.payType === 'MOCK' || !o.payType ? '模拟支付（渠道为 mock）' : String(o.payType),
    refundReason: o.refundReason || ''
  }
}

/** 支付状态对应的样式修饰类（模板里拼类名，不写判断） */
function statusClass (status) {
  if (status === 0) return 'order__status--pending'
  if (status === 1) return 'order__status--paid'
  if (status === 3) return 'order__status--refunded'
  return 'order__status--closed'
}

/** 支付时间超过 7 天 = 出了自助退款窗口（后端 B000518） */
function exceededRefundWindow (order) {
  const t = order.payTime || order.createTime
  const left = daysLeft(t)
  if (left === null) return false
  // daysLeft 是"距今天数"：负数说明过去的天数 = -left
  return -left > 7
}

/** mock 渠道的操作提示（payParams.tip 就是这句话，后端 MockPayChannel 里写死的） */
export function mockTip (payParams) {
  if (payParams && payParams.tip) return String(payParams.tip)
  return '模拟支付：渠道是 mock，点「模拟支付」即视为付款成功（后端走的是与真实回调完全相同的链路）'
}

/** vip 错误码 → 人话（后端 message 已中文，只在能给出更具体下一步时才覆盖） */
export function vipErrorMessage (e) {
  if (!e) return '会员服务出错了，稍后再试'
  const code = e.code ? String(e.code) : ''
  if (code === 'UNAUTHORIZED' || e.isUnauthorized) return '登录已过期，请重新登录后再操作'
  if (code === 'NETWORK') return e.message || '连不上后端，检查网络或网关是否已启动'
  const MAP = {
    B000500: '这个档位不存在了，刷新一下再选',
    B000501: '这个档位当前不可购买',
    B000502: '订单不存在，可能在别的设备上已处理',
    B000512: '这个订单的状态不能做该操作',
    B000518: '超过 7 天自助退款期限了，可以联系客服处理',
    B000519: '退款没成功，稍后再试',
    B000520: '兑换码不存在或已失效',
    B000521: '兑换码不在有效期内',
    B000522: '兑换码名额已用完',
    B000523: '这个兑换码你已经用过了，每人每码限一次',
    B000524: '这是下单立减码，请在开通时填在优惠码里，不要拿来兑换',
    B000525: '还没到兑换码的使用门槛',
    B000526: '这个兑换码不适用当前档位',
    B000527: '促销兑换的 0 元订单不支持退款'
  }
  return MAP[code] || e.message || '操作没成功，稍后再试'
}