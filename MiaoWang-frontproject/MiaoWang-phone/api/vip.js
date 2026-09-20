/**
 * 会员（VIP）接口（手机端 / JS）
 *
 * 契约与 Web 端同一份（见 miaowang-api-contract），路径都经网关带服务名前缀：
 *   /miaowang-vip/mapleleaf/miaowang/v1/vip/...
 *
 * 三点前端必须知道的事实（2026-09-18 实测，别按技能库旧文案写）：
 * 1) **`GET /vip/plan` 经网关是要 token 的**（网关白名单里没有它）。无 token 会拿到
 *    网关信封 `{state:-1,message:"未登录或者的登录过期"}`（HTTP 200）→ 由 utils/request.js
 *    归一化成 ApiError('UNAUTHORIZED')。所以会员页对未登录用户是**软门槛**：先提示去登录，不硬调。
 * 2) **档位里没有"权益名称数组"**：所谓 9 项权益就是 DTO 上那 9 个数值/周期字段
 *    （petLimitPerType / ragLimit+ragPeriod / chatLimit+chatPeriod / healthRecordLimit /
 *     healthExport / healthAiAnalysis / reminderLimit）。名称与文案全在前端（utils/vip.js）。
 *    **数值一律取接口，禁止写死**，档位个数也不写死（后端返回几个就渲染几个）。
 * 3) 价格字段是**元**（BigDecimal → JSON number），`-1` 表示不限次数。
 *
 * 渠道仍是 mock：`POST /vip/order/{orderNo}/pay` 是**模拟支付**，界面必须如实标注，
 * 不能假装是真支付（payParams.tip 就把这件事说清楚了，见 utils/vip.js 的 mockTip()）。
 */
import { api } from '../utils/request.js'

/** 可购买档位列表（含价格与 9 项权益数值，免费档不返回）。需要登录。 */
export function listPlans () {
  return api.get('vip', '/vip/plan')
}

/**
 * 我的权益总览。未登录/非会员也正常返回（vip=false + 免费档权益），
 * 所以不用写两套逻辑。
 */
export function getMyBenefit () {
  return api.get('vip', '/vip/benefit')
}

/**
 * 一键开通/续费（内部 = 下单 + 模拟支付，演示与联调用）。
 * 真实支付流程走 createOrder + payOrder 那两步。
 */
export function subscribe (planCode) {
  return api.post('vip', '/vip/subscribe', { body: { planCode: planCode }, timeout: 30000 })
}

/**
 * 下单：只落「待支付」，不发会员；返回体里带 payParams（mock 渠道只有操作提示）。
 * @param {string} planCode
 * @param {string} [promoCode] 立减码（DISCOUNT 类型），支付成功才占名额
 * @param {string} [requestId] 幂等键（同一键只会出一单）；不传由本函数生成
 */
export function createOrder (planCode, promoCode, requestId) {
  const body = {
    planCode: planCode,
    // mock 渠道；接真实渠道时这里换成 WECHAT / ALIPAY 即可，业务逻辑不用改
    payType: 'MOCK',
    requestId: requestId || genRequestId()
  }
  const code = String(promoCode === undefined || promoCode === null ? '' : promoCode).trim()
  if (code) body.promoCode = code
  return api.post('vip', '/vip/order', { body: body, timeout: 30000 })
}

/**
 * 模拟支付（渠道为 mock）：走的是与真实回调**完全相同**的链路
 * （验签 → 时间戳 → nonce 防重放 → 金额校验 → CAS 状态流转 → 发会员），
 * 所以"换真实渠道"只需要后端新增一个 PayChannel 实现类。
 */
export function payOrder (orderNo) {
  return api.post('vip', '/vip/order/' + orderNo + '/pay', { timeout: 30000 })
}

/**
 * 退款：仅本人、仅已支付、仅 7 天内；成功后按订单快照时长回收会员时长。
 * 0 元促销单不能退款（后端 B000527）。
 */
export function refundOrder (orderNo, reason) {
  const body = {}
  const r = String(reason === undefined || reason === null ? '' : reason).trim()
  if (r) body.reason = r
  return api.post('vip', '/vip/order/' + orderNo + '/refund', { body: body, timeout: 30000 })
}

/** 我的订单列表（无订单时后端返回空数组，不是 null）。需要登录。 */
export function listMyOrders () {
  return api.get('vip', '/vip/order')
}

/** 核销送会员时长的兑换码（PLAN 类型）：成功即到账，返回 0 元促销订单。 */
export function redeemPromo (code) {
  return api.post('vip', '/vip/promo/redeem', {
    body: { code: String(code === undefined || code === null ? '' : code).trim().toUpperCase() },
    timeout: 30000
  })
}

/** 下单幂等键：不用 crypto.randomUUID（老 webview / 小程序不一定有），自己拼一个够用的 */
function genRequestId () {
  const rnd = Math.random().toString(36).slice(2, 10) + Math.random().toString(36).slice(2, 10)
  return 'mw' + Date.now().toString(36) + rnd
}