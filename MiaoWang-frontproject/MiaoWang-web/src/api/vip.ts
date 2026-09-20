/**
 * 会员接口（Web）
 *
 * 字段与流程全部来自真调用实测（`tools/probe-vip.js` 直连网关 7000 跑通），不是照契约想象的：
 * - `GET /vip/plan`    5 个档位，**9 项权益是 9 个扁平字段**（没有"权益名称数组"，名称由前端格式化），
 *                      ⚠️ 实测经网关**需要 token**（匿名返回 `state:-1`），所以只能在登录后调
 * - `GET /vip/benefit` 未开会员也正常返回（`vip:false` + 免费档权益），`-1` = 不限
 * - `GET /vip/order`   没有订单时返回**空数组**（不是 null）
 * - `POST /vip/subscribe`      一键开通（内部 = 下单 + 模拟支付），返回**订单**，`payStatus` 直接是 1
 * - `POST /vip/order`          只落「待支付」，返回里的 `payParams` 才是去收款要用的东西
 * - `POST /vip/order/{no}/pay` **模拟支付**（只给 `MOCK` 渠道用），走的是与真实回调完全相同的链路
 * - `POST /vip/order/{no}/sync` **对账**：后端主动向支付宝查单，已支付就发会员，返回刷新后的订单。
 *                             局域网开发机收不到支付宝的异步回调，所以「跳转支付 → 回到站内 → 对账」是本地唯一能跑通的路径
 * - `POST /vip/order/{no}/refund` 退款：仅本人 / 仅已支付 / 仅窗口期内（默认 7 天）；0 元促销单不能退（`B000527`）
 * - `POST /vip/promo/redeem`   核销**送会员时长**的兑换码，返回 0 元促销订单
 *
 * **权益数值一律以接口返回为准**（价格/次数改的是数据库），前端只做格式化、不写死任何数值。
 *
 * 支付渠道（`payType`）：
 * - `MOCK`   模拟支付，**不真实扣款**，走 `/vip/order/{no}/pay` 一步到账
 * - `ALIPAY` **支付宝沙箱**（测试环境，不会真实扣款）：下单返回 `payParams`，回来后用 `/sync` 对账；
 *            **不要**对它调 `/pay`。`ALIPAY` 有两种场景（下单时用 `payScene` 选）：
 *            `QR` = 后端返回 `payParams.qrCode`（一串**支付码字符串，不是图片地址**），
 *                   我们自己的页面用二维码库把它画成图，用户拿手机上的支付宝沙箱版扫；
 *            `PAGE` = 后端返回 `payParams.payHtml`（**完整 HTML 表单**）与 `payParams.payUrl`
 *                   （GET 链接，只作旧沙箱回退）。**有 `payHtml` 就用它**：新开标签页把表单写进去
 *                   自动 POST 给网关；新沙箱的网关不渲染收银台页面，直接打开 `payUrl` 会 404
 * - `PROMO`  0 元促销单（兑换码），没有支付动作
 */
import { api } from './request'

/** 我的权益总览（`GET /vip/benefit`）；`-1` = 不限次数 */
export interface VipBenefit {
  vip: boolean
  /** 实测是**数组**（如 `["全能月卡"]`）；非会员是 `[]` 而不是 null */
  planNames: string[]
  expireTime: string | null
  /** 宠物总数上限（字段名是历史遗留，语义已是总数） */
  petLimitPerType: number
  ragLimit: number
  ragPeriod: string
  ragPeriodDesc: string
  ragUsed: number
  ragRemaining: number
  chatLimit: number
  chatPeriod: string
  chatPeriodDesc: string
  chatUsed: number
  chatRemaining: number
  healthRecordLimit: number
  healthExport: number
  healthAiAnalysis: number
  reminderLimit: number
}

/**
 * 可购买档位（`GET /vip/plan`）
 *
 * 实测原始 JSON（首档）：
 * `{"planCode":"PET_49","planName":"宠物位扩容月卡","price":4.9,"durationDays":30,"petLimitPerType":5,`
 * `"ragLimit":5,"ragPeriod":"MONTH","chatLimit":5,"chatPeriod":"DAY","healthRecordLimit":20,`
 * `"healthExport":0,"healthAiAnalysis":0,"reminderLimit":3,"sort":1}`
 *
 * 所谓「9 项权益」= `petLimitPerType` / `ragLimit` / `ragPeriod` / `chatLimit` / `chatPeriod` /
 * `healthRecordLimit` / `healthExport` / `healthAiAnalysis` / `reminderLimit` 这 9 个字段本身。
 */
export interface VipPlan {
  planCode: string
  planName: string
  price: number
  durationDays: number
  petLimitPerType: number
  ragLimit: number
  /** `DAY` / `MONTH`（`-1` 不限时这个字段仍会回） */
  ragPeriod: string
  chatLimit: number
  chatPeriod: string
  healthRecordLimit: number
  /** 0/1 开关 */
  healthExport: number
  /** 0/1 开关 */
  healthAiAnalysis: number
  reminderLimit: number
  sort: number
}

/** 支付状态（后端 `VipPayStatusEnum`，响应里另有中文 `payStatusDesc` 可直接展示） */
export const PAY_PENDING = 0
export const PAY_PAID = 1
export const PAY_CLOSED = 2
export const PAY_REFUNDED = 3

/** 支付渠道（`payType`）。`PROMO` 只出现在返回里（0 元促销单），不能拿来下单 */
export type VipPayType = 'MOCK' | 'ALIPAY' | 'PROMO'

/** 模拟支付渠道：不真实扣款，走 `/vip/order/{no}/pay` 一步到账 */
export const PAY_MOCK: VipPayType = 'MOCK'
/**
 * 支付宝**沙箱**渠道：测试环境，不会真实扣款。
 * 默认走扫码场景（`PAY_SCENE_QR`，页内出二维码）；后端没给码时会回退到跳收银台
 * （`payParams.payHtml` 表单提交，没有再退到 `payParams.payUrl` 那条 GET 链接）。
 * 两种场景付完款都用 `syncOrder` 对账，**不要**调 `payOrder`。
 */
export const PAY_ALIPAY: VipPayType = 'ALIPAY'

/**
 * 支付场景（下单请求体里的 `payScene`）。
 *
 * 只有支付宝渠道用得上：`MOCK` / `PROMO` 两步就完事，没有"场景"这个概念。
 */
export type VipPayScene = 'QR' | 'PAGE'

/**
 * 扫码支付场景（下单时下发）：后端返回 `payParams.qrCode`，
 * 那是一串**支付码字符串**（形如 `https://qr.alipay.com/bavh4wjlxf12tper3a`），
 * **不是图片地址** —— 必须由前端用二维码库渲染成图，用户拿手机上的支付宝沙箱版扫。
 */
export const PAY_SCENE_QR: VipPayScene = 'QR'

/**
 * 跳收银台场景（旧路径）：后端返回 `payParams.payHtml`（完整 HTML 表单，写进新窗口自动提交）
 * 与 `payParams.payUrl`（GET 链接，旧沙箱可用；**新沙箱打开会 404**，只作回退）。
 *
 * ⚠️ 这是后端**不传 `payScene` 时的默认行为**，所以下单时不要下发这个值
 * （契约里只承诺了 `'QR'` 的含义，不下发最稳）。这个常量用在前端的场景选择上。
 */
export const PAY_SCENE_PAGE: VipPayScene = 'PAGE'

/**
 * 下单返回的渠道支付参数。
 *
 * - `MOCK`：实测形如 `{"channel":"MOCK","orderNo":"…","amount":4.9,"tip":"模拟支付：…"}`
 * - `ALIPAY`（沙箱）**跳收银台场景**（不传 `payScene` 时的默认，后端内部 `PAGE`）：
 *   `{"channel":"ALIPAY","orderNo":"VIP2026…","outTradeNo":"VIP2026…",`
 *   `"subject":"AI 基础月卡","amount":14.9,`
 *   `"payUrl":"https://openapi-sandbox…/gateway.do?…",`
 *   `"payHtml":"<form name=\"punchout_form\" method=\"post\" action=\"https://openapi-sandbox…/gateway.do\">…",`
 *   `"tip":"用 payHtml（自动提交表单，POST 到网关）打开沙箱收银台…"}`
 *   —— `payUrl` 是 GET 链接（**新沙箱打开会 404**，只作旧沙箱回退）；
 *   `payHtml` 是**完整 HTML 表单**，写进新窗口自动提交才是新沙箱的收银台入口。
 * - `ALIPAY`（沙箱）**扫码场景**（下单带 `payScene: 'QR'`）：
 *   `{"channel":"ALIPAY","scene":"QR","outTradeNo":"VIP2026…",`
 *   `"qrCode":"https://qr.alipay.com/bavh4wjlxf12tper3a",`
 *   `"subject":"AI 基础月卡","amount":14.9,"tip":"用手机上的支付宝沙箱版扫这个码付款"}`
 *   —— ⚠️ **`qrCode` 是一串支付码字符串，不是图片地址**；而且这类响应里**没有 `payUrl`**
 *   （收银台那条路才给 `payUrl`），所以"有没有 `qrCode`"就是前端选哪条路的判据
 *
 * 两个场景的键**不完全一样**：`scene` 只有扫码那份带，`orderNo` 只有收银台那份带
 * （扫码那份只给 `outTradeNo`）。所以前端一律用 `VipOrder.orderNo`，别从 `payParams` 里取订单号。
 *
 * `outTradeNo` / `subject` 只用于展示与排查（`outTradeNo` 正常情况下等于 `orderNo`）。
 */
export interface VipPayParams {
  channel: string
  /**
   * ⚠️ 可选：后端**扫码场景的 `payParams` 里没有这个键**（只有跳收银台时才放），
   * 订单号一律用 `VipOrder.orderNo`，别指望这里。
   */
  orderNo?: string
  amount: number
  tip?: string
  /**
   * 支付场景：**只有扫码那份 `payParams` 带它**（值 `QR`），跳收银台那份没有这个键。
   * 所以判断走哪条路，用"有没有 `qrCode` / `payHtml`"比读它更可靠。
   */
  scene?: string
  /**
   * 支付宝沙箱扫码场景的**支付码字符串**（形如 `https://qr.alipay.com/bavh4wjlxf12tper3a`）。
   *
   * ⚠️ 这不是图片地址，`<img src>` 直接塞进去只会得到一张裂图，
   * 必须用二维码库（`qrcode`）把它编码成二维码图片。
   */
  qrCode?: string
  /** 支付宝沙箱跳收银台场景：浏览器可直接打开的完整支付地址 */
  payUrl?: string
  /**
   * 支付宝沙箱跳收银台场景：**完整的一段 HTML 表单**（`<form method="post" action="<网关>">` +
   * 隐藏域 + `<script>document.forms[0].submit();</script>`）。
   *
   * ⚠️ 为什么要用它而不是 `payUrl`：新版沙箱的网关主机 `openapi-sandbox.dl.alipaydev.com`
   * 是 **API 网关**，只接受 POST 的接口调用、**不渲染收银台页面** —— 把 `payUrl`（GET 链接）
   * 直接 `window.open` 出去，支付宝只会回 **404**（真沙箱实测）。`alipay.trade.page.pay`
   * 的官方集成方式本来就是「把这段表单 POST 给网关，由网关再跳到收银台」。
   *
   * 所以前端要：**有 `payHtml` 就用它**（写进新窗口让它自动提交），`payUrl` 只作回退。
   * ⚠️ 这段 HTML **很长**，只用于 `window.open` + `document.write`，**不要**渲染进页面的可见区域、
   * 也不要打印到界面上。
   */
  payHtml?: string
  /** 支付宝沙箱：商户订单号（展示/排查用） */
  outTradeNo?: string
  /** 支付宝沙箱：订单标题（展示/排查用） */
  subject?: string
}

/** 订阅订单（`GET /vip/order`、`POST /vip/subscribe`、支付/退款返回同一个形状） */
export interface VipOrder {
  orderNo: string
  planCode: string
  planName: string
  /** 原价；0 元促销单这里就是 0 */
  amount: number
  discountAmount: number
  /** **应付额，前端展示"实付"就用它** */
  payableAmount: number
  promoCode: string | null
  /** `MOCK` / `ALIPAY` / `PROMO`（0 元促销单）；已实测只见过 MOCK 与 PROMO */
  payType: string
  payStatus: number
  payTime: string | null
  startTime: string | null
  expireTime: string | null
  createTime: string
  /** 中文状态，直接展示 */
  payStatusDesc: string
  durationDays: number
  /**
   * 只有**下单**返回里有值（拿去收款用），列表/支付/退款/对账都是 null。
   * 支付宝沙箱扫码场景（`payScene: 'QR'`）这里带 `qrCode`（支付码字符串，前端自己画二维码）；
   * 跳收银台场景（`PAGE`）带 `payHtml`（表单 HTML，正路）与 `payUrl`（GET 链接，回退）。
   * 两种都以 `payParams` 里实际出现的字段为准；**收银台优先用 `payHtml`**。
   */
  payParams: VipPayParams | null
  /**
   * 渠道流水号（支付宝的 `trade_no`）。
   *
   * 口径提醒（2026-09-18 核对后端源码）：库里 `miaowang_vip_order.channel_trade_no` 有存这个值，
   * 但 `VipOrderRespDTO` **还没把它吐出来**，所以前端目前基本拿不到，界面按"有值才显示"处理。
   * 金额与状态一律以本订单对象为准，这个字段只用于展示与排查。
   */
  channelTradeNo?: string | null
  closeTime: string | null
  refundTime: string | null
  refundReason: string | null
}

export interface CreateOrderParams {
  planCode: string
  /** 不传 = 后端默认 `MOCK`；支付宝沙箱传 `PAY_ALIPAY`（`'ALIPAY'`） */
  payType?: VipPayType
  /**
   * 支付场景，只对支付宝有意义：传 `PAY_SCENE_QR`（`'QR'`）时返回 `payParams.qrCode`（扫码支付预下单）。
   * 不传 = 后端默认的跳收银台（`PAGE`），返回 `payParams.payHtml`（表单 HTML，正路）
   * 与 `payParams.payUrl`（GET 链接，回退）。
   */
  payScene?: VipPayScene
  /** 幂等键：同一键只会出一单（实测重放返回同一个 orderNo） */
  requestId?: string
  /** 立减码（`promoType=DISCOUNT`），支付成功才占名额 */
  promoCode?: string
}

/** 可购买档位列表（需要登录） */
export function listPlans() {
  return api.get<VipPlan[]>('vip', '/vip/plan')
}

/** 我的权益总览（未登录/非会员也正常返回免费档权益） */
export function getMyBenefit() {
  return api.get<VipBenefit>('vip', '/vip/benefit')
}

/** 一键开通/续费（内部 = 下单 + 模拟支付；同档位续费在原到期时间上叠加） */
export function subscribe(planCode: string) {
  return api.post<VipOrder>('vip', '/vip/subscribe', { body: { planCode } })
}

/** 下单：只落「待支付」，**不发会员**；返回里带 `payParams`（按 `payScene` 决定给 `qrCode` 还是收银台的 `payHtml` + `payUrl`） */
export function createOrder(params: CreateOrderParams) {
  return api.post<VipOrder>('vip', '/vip/order', { body: params })
}

/** 模拟支付（渠道是 mock）：走的是与真实回调完全相同的链路。**支付宝订单不要调它** */
export function payOrder(orderNo: string) {
  return api.post<VipOrder>('vip', `/vip/order/${encodeURIComponent(orderNo)}/pay`)
}

/**
 * 对账（`POST /vip/order/{orderNo}/sync`，仅订单本人）。
 *
 * 后端拿这笔订单的商户号主动去支付宝**查单**：查到了就按与异步回调完全相同的链路发会员
 * （验签、金额核对、状态 CAS、发会员都在后端做），并把刷新后的订单返回。
 *
 * 为什么必须有它：局域网开发机上支付宝的异步回调打不进来，所以「打开沙箱收银台 →
 * 用户付完回到站内 → 调这个接口对账」才是本地唯一能跑通的路径，真实支付也是这么做的
 * （查询接口才是权威来源，回调只是加速）。
 *
 * ⚠️ **返回的订单才是可信来源**：`return_url` 带回来的 query 谁都能拼，一律以这里的
 * `payStatus` 为准（`1` = 已支付、已发会员）。
 */
export function syncOrder(orderNo: string) {
  return api.post<VipOrder>('vip', `/vip/order/${encodeURIComponent(orderNo)}/sync`)
}

/** 退款：仅本人 / 仅已支付 / 仅窗口期内；成功后按订单快照时长回收会员时长 */
export function refundOrder(orderNo: string, reason?: string) {
  return api.post<VipOrder>('vip', `/vip/order/${encodeURIComponent(orderNo)}/refund`, {
    body: reason ? { reason } : {},
  })
}

/** 我的订阅订单列表（没有订单时是空数组） */
export function listMyOrders() {
  return api.get<VipOrder[]>('vip', '/vip/order')
}

/** 核销送会员时长的兑换码，成功即到账，返回 0 元促销订单 */
export function redeemPromo(code: string) {
  return api.post<VipOrder>('vip', '/vip/promo/redeem', { body: { code } })
}

/* ---------- 展示用的格式化（数值全部来自接口，这里只负责翻译成人话） ---------- */

/** -1 表示不限次数（会员档），直接用文案表达，避免界面出现 "-1 次" */
export function quotaText(remaining: number): string {
  return remaining < 0 ? '不限次数' : `剩 ${remaining} 次`
}

/** 周期字段转中文：`DAY`→每日、`MONTH`→每月；不认识的原样吐出，不硬编 */
export function periodText(period: string): string {
  if (period === 'DAY') return '每日'
  if (period === 'MONTH') return '每月'
  if (period === 'YEAR') return '每年'
  return period || ''
}

/** 次数类数值：-1 直接说"不限"，不要显示成 "-1 次" */
export function limitText(limit: number, unit: string): string {
  return limit < 0 ? '不限' : `${limit} ${unit}`
}

/** 0/1 开关类权益：后端就是 0/1，别猜第三种状态 */
export function switchText(on: number): string {
  return on > 0 ? '支持' : '不支持'
}
