/**
 * 会员状态（手机端 / reactive 单例，工程无 Pinia）
 *
 * 写法严格照 store/ai.js：**普通对象 + 方法用简写**，内部用 `self` 捕获 `this`
 * （reactive 代理下 `this` 在某些调用路径上不可靠，异步里尤甚）。
 *
 * ⚠️ 页面模板不许直接写 `vip.xxx`（store 不在实例上）→ 页面用 computed 转一手，
 * 并在 computed 里把原始 DTO 摊平成显示字段（见 pages/vip/vip.vue）。
 *
 * 口径：
 * - `benefit` 是**我的**权益总览；未登录/非会员后端也正常返回（vip=false + 免费档）
 * - 档位与权益数值**一律以接口为准**，这里一个数字都不写死
 * - 渠道是 mock：`pay()` 就是模拟支付，界面必须如实标注
 */
import { reactive } from 'vue'
import * as vipApi from '../api/vip.js'
import { vipErrorMessage } from '../utils/vip.js'
import { ApiError } from '../utils/request.js'

/**
 * 错误是不是"会话真过期了"（而不是网络不通 / 档位不存在）。
 * 掉线要单独标记：页面上再重试也是白搭，应该把人引导回登录页，
 * 而不是摆一片红字说"登录已过期"却不给下一步。
 */
function isExpired (e) {
  return e instanceof ApiError && e.isUnauthorized
}

export const vip = reactive({
  /** VipPlanRespDTO[]，后端返回几个就是几个 */
  plans: [],
  plansLoading: false,
  plansError: '',
  /** 三个接口里只要有一个判定为"会话过期"，就置为 true（页面据此回到软门槛 + 去登录） */
  unauthorized: false,

  /** UserVipBenefitRespDTO | null */
  benefit: null,
  benefitLoading: false,
  benefitError: '',

  /** VipOrderRespDTO[] */
  orders: [],
  ordersLoading: false,
  ordersError: '',

  /** 正在提交的动作：'' | 'subscribe' | 'pay' | 'refund' | 'redeem' */
  submitting: '',

  /** 页面上高亮的档位（只影响观感，不影响下单） */
  selected: '',

  /**
   * 档位列表。**经网关需要 token**（实测白名单里没有 /vip/plan），
   * 所以未登录时不要调它 —— 页面会先给软门槛。
   */
  async fetchPlans () {
    this.plansLoading = true
    this.plansError = ''
    this.unauthorized = false
    try {
      const rows = await vipApi.listPlans()
      this.plans = rows || []
    } catch (e) {
      if (isExpired(e)) this.unauthorized = true
      this.plansError = vipErrorMessage(e)
    } finally {
      this.plansLoading = false
    }
  },

  /** 我的权益总览（免费档也返回，所以不用区分两套） */
  async fetchBenefit () {
    this.benefitLoading = true
    this.benefitError = ''
    try {
      this.benefit = await vipApi.getMyBenefit()
    } catch (e) {
      this.benefit = null
      if (isExpired(e)) this.unauthorized = true
      this.benefitError = vipErrorMessage(e)
    } finally {
      this.benefitLoading = false
    }
  },

  /** 我的订单列表（后端无订单返回空数组） */
  async fetchOrders () {
    this.ordersLoading = true
    this.ordersError = ''
    try {
      const rows = await vipApi.listMyOrders()
      this.orders = rows || []
    } catch (e) {
      if (isExpired(e)) this.unauthorized = true
      this.ordersError = vipErrorMessage(e)
    } finally {
      this.ordersLoading = false
    }
  },

  /** 三个接口一起拉：会员页首屏就这一个入口，省得页面自己编排顺序 */
  async refresh () {
    await Promise.all([this.fetchPlans(), this.fetchBenefit(), this.fetchOrders()])
  },

  /**
   * 一键开通/续费（= 下单 + 模拟支付）。
   * 成功后权益与订单都变了 → 一并刷新，不用页面自己管。
   * @returns {Promise<object>} 订单
   */
  async subscribe (planCode) {
    const code = String(planCode === undefined || planCode === null ? '' : planCode).trim()
    if (!code) throw new Error('没选档位')
    this.submitting = 'subscribe'
    try {
      const order = await vipApi.subscribe(code)
      await Promise.all([this.fetchBenefit(), this.fetchOrders()])
      return order
    } finally {
      this.submitting = ''
    }
  },

  /**
   * 走标准两步：下单（只落「待支付」）→ 模拟支付。
   *
   * 与 `subscribe()` 的区别：`subscribe` 是后端提供的一键接口（内部同样是这两步），
   * 而这个方法保留了"先下单、拿到 payParams、再付款"的形状 —— 接真实渠道时前端就是
   * 拿 payParams 去调起收款、然后等回调；mock 渠道直接调 pay 接口，
   * 那条链路与真实回调**完全相同**（验签 → 时间戳 → nonce → 金额 → CAS → 发会员）。
   *
   * 当前会员页用的是 `subscribe()`（少一次往返）；想演示"下单立减码 `promoCode`"时用它，
   * 因为立减码只在 `POST /vip/order` 的 body 里生效。
   * @returns {Promise<{ order: object, payParams: object }>}
   */
  async createAndPay (planCode, promoCode) {
    const code = String(planCode === undefined || planCode === null ? '' : planCode).trim()
    if (!code) throw new Error('没选档位')
    this.submitting = 'subscribe'
    try {
      const created = await vipApi.createOrder(code, promoCode)
      const paid = created && created.orderNo
        ? await vipApi.payOrder(created.orderNo)
        : created
      await Promise.all([this.fetchBenefit(), this.fetchOrders()])
      return { order: paid, payParams: (created && created.payParams) || (paid && paid.payParams) || null }
    } finally {
      this.submitting = ''
    }
  },

  /** 模拟支付一笔待支付订单 */
  async pay (orderNo) {
    this.submitting = 'pay'
    try {
      const order = await vipApi.payOrder(orderNo)
      await Promise.all([this.fetchBenefit(), this.fetchOrders()])
      return order
    } finally {
      this.submitting = ''
    }
  },

  /** 退款（仅本人 / 仅已支付 / 仅 7 天内；0 元促销单不支持） */
  async refund (orderNo, reason) {
    this.submitting = 'refund'
    try {
      const order = await vipApi.refundOrder(orderNo, reason)
      await Promise.all([this.fetchBenefit(), this.fetchOrders()])
      return order
    } finally {
      this.submitting = ''
    }
  },

  /** 核销兑换码（送会员时长）：成功即到账，返回 0 元促销订单 */
  async redeem (code) {
    const c = String(code === undefined || code === null ? '' : code).trim()
    if (!c) throw new Error('先填兑换码')
    this.submitting = 'redeem'
    try {
      const order = await vipApi.redeemPromo(c)
      await Promise.all([this.fetchBenefit(), this.fetchOrders()])
      return order
    } finally {
      this.submitting = ''
    }
  },

  /** 退出登录后清干净，避免下一个账号看到上一个人的订单 */
  reset () {
    this.plans = []
    this.plansError = ''
    this.benefit = null
    this.benefitError = ''
    this.orders = []
    this.ordersError = ''
    this.submitting = ''
    this.selected = ''
    this.unauthorized = false
  }
})