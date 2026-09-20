<script setup lang="ts">
/**
 * 喵汪会员（Web）
 *
 * 这个页面的存在理由：AI 问答页会说"开通会员可以继续问"，但原来全站**没有一个开通入口**。
 * 这里把那条断头路补上，同时把会员功能做完整（档位、我的状态、开通、订单、退款、兑换码）。
 *
 * 数据全部来自接口，字段与流程都用 `tools/probe-vip.js` 直连网关实测过：
 * - 「档位」用 `GET /vip/plan`（⚠️ 实测**需要 token**，匿名返回 `state:-1`，所以本页放在 `requiresAuth` 路由下）
 * - 「我的状态」用 `GET /vip/benefit`（非会员也正常返回免费档权益）
 * - 「订单」用 `GET /vip/order`（没有订单时是空数组）
 * **权益数值一律以接口为准**（价格与次数改的是数据库），前端只做单位与 0/1 的翻译。
 *
 * 两个开通入口的差别，也就是接真实渠道时的真实时序：
 * - 「开通（模拟支付）」= `POST /vip/subscribe`，内部就是"下单 + 模拟支付"，一步到账
 * - 「只生成待支付订单」= `POST /vip/order`，只落待支付，会员要等支付成功之后才发
 *
 * 支付渠道与场景（页面顶部的分段控件切换）：
 * - **模拟支付（MOCK，默认）**：不真实扣款，走 `POST /vip/order/{no}/pay` 一步到账
 * - **支付宝沙箱（ALIPAY）**：测试环境，**不会真实扣款**。它有两条场景：
 *   - **扫码支付（`payScene: 'QR'`，默认）**：后端返回 `payParams.qrCode`，
 *     那是一串**支付码字符串、不是图片地址**，由 `AlipayQrPanel` 用二维码库画在页面上，
 *     用户拿手机上的支付宝沙箱版扫。**不跳收银台。**
 *   - **跳转收银台（`PAGE`，旧路径）**：后端返回 `payParams.payHtml`（**完整 HTML 表单**）
 *     与 `payParams.payUrl`（GET 链接）。**有 `payHtml` 就用它**：新开一个标签页、把表单写进去
 *     让它自动 POST 给网关，由网关再跳到收银台。原因是新沙箱的网关主机
 *     （`openapi-sandbox.dl.alipaydev.com`）**是 API 网关，只接受 POST、不渲染收银台页面** ——
 *     直接打开 `payUrl` 那条 GET 链接支付宝会回 **404**（真沙箱实测）。
 *     `payUrl` 只作旧沙箱的回退（后端没给 `payHtml` 时才走 `window.open(payUrl)`）。
 *     选扫码但后端没给码、只给了收银台那份时**自动回退到这条**，所以老路径不会被扫码取代掉。
 *   两条场景付完款都进同一个"等待支付"会话：每 3 秒调一次 `POST /vip/order/{no}/sync`
 *   让后端去支付宝查单，查到（`payStatus` 变 1）才刷新权益并说成功。本地开发机收不到支付宝的
 *   异步回调，所以"付款 → 回到站内 → 对账"是本地唯一能跑通的路径。
 *   支付宝订单**绝不调** `/pay`（那个接口只给 mock 用）。
 *
 * 所有涉及支付宝的文案都写明是沙箱、不是真实收款，并且不把"查到支付"说成"钱到账"。
 */
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  PhArrowClockwise,
  PhArrowSquareOut,
  PhClock,
  PhCrown,
  PhGift,
  PhQrCode,
  PhReceipt,
  PhSealCheck,
} from '@phosphor-icons/vue'
import { ApiError } from '@/api/request'
import { useAuthStore } from '@/stores/auth'
import AlipayQrPanel from '@/components/vip/AlipayQrPanel.vue'
import AlipayWaitPanel from '@/components/vip/AlipayWaitPanel.vue'
import { useVipAlipayWait } from '@/composables/useVipAlipayWait'
import {
  channelText,
  dateTimeText,
  moneyText,
  payHtmlText,
  payStatusClass,
  payStatusText,
  payUrlText,
  qrCodeText,
  vipPayErrorMessage,
} from '@/utils/vip'
import {
  PAY_ALIPAY,
  PAY_MOCK,
  PAY_PAID,
  PAY_PENDING,
  PAY_SCENE_PAGE,
  PAY_SCENE_QR,
  createOrder,
  getMyBenefit,
  limitText,
  listMyOrders,
  listPlans,
  payOrder,
  periodText,
  quotaText,
  redeemPromo,
  refundOrder,
  subscribe,
  switchText,
  syncOrder,
  type VipBenefit,
  type VipOrder,
  type VipPayScene,
  type VipPayType,
  type VipPlan,
} from '@/api/vip'

const auth = useAuthStore()

/* ---------- 数据 ---------- */

const plans = ref<VipPlan[]>([])
const plansLoading = ref(false)
const plansError = ref('')

const benefit = ref<VipBenefit | null>(null)
const benefitLoading = ref(false)
const benefitError = ref('')

const orders = ref<VipOrder[]>([])
const ordersLoading = ref(false)
const ordersError = ref('')

/** 任何一次请求被判成"未登录"就亮这个标记，页面给出重新登录的入口 */
const unauthorized = ref(false)

/** 同一时刻只允许一个动作，避免连点出多单或重复退款 */
const busyKey = ref('')

// 反馈分三处放，各自贴近触发它的按钮（都放在页面顶部的话，操作订单时提示会跑到屏幕外）
const planError = ref('')
const planNotice = ref('')
const orderError = ref('')
const orderNotice = ref('')
const promoError = ref('')
const promoNotice = ref('')

const promoCode = ref('')
const promoBusy = ref(false)

/** 退款的两步确认：存 orderNo。不用浏览器原生 confirm，改成按钮行内的"确认退款 / 先不退" */
const confirmRefund = ref<string | null>(null)

/**
 * 支付方式：默认仍是模拟支付（与改动前一致），切到支付宝只是换一条路径，不动任何既有行为。
 * 切换时若还在等待对账，会先把等待会话停掉（见 onPayChannel）。
 */
const payChannel = ref<VipPayType>(PAY_MOCK)

/**
 * 支付宝的支付场景：**默认扫码**（页内出二维码，不跳收银台），可切回原来的跳收银台。
 * 只有 `payChannel === PAY_ALIPAY` 时有意义；切到扫码但后端没给 `qrCode` 会自动回退到收银台。
 */
const payScene = ref<VipPayScene>(PAY_SCENE_QR)

/** 收银台是否真的被浏览器打开了（被拦时不写"已经打开了"，见 AlipayWaitPanel 的 lead） */
const alipayOpened = ref(false)

/**
 * 收银台**表单 HTML** 的副本（新沙箱走这条：写进新窗口自动提交）。
 *
 * 与 `alipayQrCode` 同理必须在这里抄一份：对账每 3 秒会把 `alipayOrder` 换成 sync 返回的那一份，
 * 而那份的 `payParams` 是 `null`，「重新打开收银台」按钮就会失去内容。
 *
 * ⚠️ 这段 HTML 很长，**只**交给新窗口的 `document.write`，绝不渲染进页面可见区域、也不打印。
 */
const alipayPayHtml = ref('')

/**
 * 二维码字符串与提示文案的副本。
 *
 * 必须在这里抄一份：`/vip/order/{no}/sync` 返回的订单里 `payParams` 是 `null`，
 * 而对账每 3 秒就会把 `alipayOrder` 换成 sync 返回的那一份 —— 面板若直接读
 * `alipayOrder.payParams.qrCode`，二维码会在第一次对账后自己消失。
 */
const alipayQrCode = ref('')
const alipayTip = ref('')

/**
 * 支付宝沙箱的"等待支付"会话：定时器全在 composable 里，离开页面自动清。
 * 解构成顶层 ref，模板里就不用写 `.value`（嵌套在对象里的 ref 不会自动解包）。
 */
const {
  order: alipayOrder,
  payUrl: alipayPayUrl,
  phase: alipayPhase,
  notice: alipayNotice,
  error: alipayError,
  checks: alipayChecks,
  syncing: alipaySyncing,
  countdownText: alipayCountdown,
  secondsLeft: alipaySecondsLeft,
  active: alipayActive,
  polling: alipayPolling,
  start: alipayStart,
  stop: alipayStop,
  dismiss: alipayDismiss,
  retry: alipayRetry,
} = useVipAlipayWait({ onPaid: onAlipayPaid })

/* ---------- 展示用的纯函数 ---------- */

function captureUnauthorized(e: unknown) {
  if (e instanceof ApiError && e.isUnauthorized) unauthorized.value = true
}

interface BenefitRow {
  label: string
  value: string
  note?: string
}

/**
 * 档位的「9 项权益」= `GET /vip/plan` 里的 9 个扁平字段（接口没有"权益名称数组"，名称由前端翻译）。
 * 这里 1:1 列出，不合并、也不添一个接口没给的权益：
 * petLimitPerType / ragLimit / ragPeriod / chatLimit / chatPeriod /
 * healthRecordLimit / healthExport / healthAiAnalysis / reminderLimit
 */
function planRows(p: VipPlan): BenefitRow[] {
  return [
    {
      label: '宠物数量上限',
      value: limitText(p.petLimitPerType, '只'),
      note: '猫狗合计，不区分物种',
    },
    { label: 'AI 问答次数', value: limitText(p.ragLimit, '次'), note: '每问一次扣 1 次' },
    { label: 'AI 问答周期', value: periodText(p.ragPeriod) },
    { label: 'AI 对话轮次', value: limitText(p.chatLimit, '轮') },
    { label: 'AI 对话周期', value: periodText(p.chatPeriod) },
    { label: '每类健康记录', value: limitText(p.healthRecordLimit, '条') },
    { label: '健康报告导出', value: switchText(p.healthExport) },
    { label: 'AI 症状解读', value: switchText(p.healthAiAnalysis) },
    { label: '照护提醒', value: limitText(p.reminderLimit, '条') },
  ]
}

/** 自助退款窗口：后端 `vip.pay.refund-window-days` 默认 7 天（已核对源码，没有 Nacos 覆盖） */
const REFUND_WINDOW_DAYS = 7

/** 退款被挡的原因（空串 = 可以退）。理由与后端一致，避免把按钮摆出来却一点就报错 */
function refundBlockReason(o: VipOrder): string {
  if (o.payStatus !== PAY_PAID) return ''
  // 0 元促销单没有钱可退，后端直接 B000527
  if (!(Number(o.amount) > 0)) return '0 元促销单不支持退款'
  if (!o.payTime) return '这笔订单没有支付时间，判断不了退款窗口'
  const paidAt = new Date(o.payTime).getTime()
  if (Number.isNaN(paidAt)) return '支付时间解析失败'
  if (paidAt + REFUND_WINDOW_DAYS * 86_400_000 <= Date.now()) {
    return `已超过 ${REFUND_WINDOW_DAYS} 天自助退款期`
  }
  return ''
}

const canRefund = (o: VipOrder) => o.payStatus === PAY_PAID && refundBlockReason(o) === ''

const pendingOrders = computed(() => orders.value.filter((o) => o.payStatus === PAY_PENDING))

/* ---------- 读数据 ---------- */

async function loadPlans() {
  plansLoading.value = true
  plansError.value = ''
  try {
    plans.value = await listPlans()
  } catch (e) {
    captureUnauthorized(e)
    plansError.value = vipPayErrorMessage(e)
  } finally {
    plansLoading.value = false
  }
}

async function loadBenefit() {
  benefitLoading.value = true
  benefitError.value = ''
  try {
    benefit.value = await getMyBenefit()
  } catch (e) {
    captureUnauthorized(e)
    benefitError.value = vipPayErrorMessage(e)
  } finally {
    benefitLoading.value = false
  }
}

async function loadOrders() {
  ordersLoading.value = true
  ordersError.value = ''
  try {
    orders.value = await listMyOrders()
  } catch (e) {
    captureUnauthorized(e)
    ordersError.value = vipPayErrorMessage(e)
  } finally {
    ordersLoading.value = false
  }
}

/** 开通、支付、退款、兑换都会同时改变权益与订单，所以一起刷 */
function refreshAfterChange() {
  return Promise.all([loadBenefit(), loadOrders()])
}

/* ---------- 写操作 ---------- */

/** 幂等键：后端实测同一 requestId 只会出一单，重放返回同一个 orderNo */
function newRequestId(): string {
  return `web-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

async function onSubscribe(plan: VipPlan) {
  if (busyKey.value) return
  busyKey.value = `subscribe:${plan.planCode}`
  planError.value = ''
  planNotice.value = ''
  try {
    const order = await subscribe(plan.planCode)
    planNotice.value = `${order.planName} 已开通，会员时长 ${order.durationDays} 天已经生效。走的是模拟支付，没有真实扣款。`
    await refreshAfterChange()
  } catch (e) {
    captureUnauthorized(e)
    planError.value = vipPayErrorMessage(e)
  } finally {
    busyKey.value = ''
  }
}

async function onCreateOrder(plan: VipPlan) {
  if (busyKey.value) return
  busyKey.value = `create:${plan.planCode}`
  planError.value = ''
  planNotice.value = ''
  try {
    const order = await createOrder({ planCode: plan.planCode, requestId: newRequestId() })
    planNotice.value = `已生成待支付订单 ${order.orderNo}。会员要等支付成功之后才发，去下面的订单列表点「去支付（模拟）」就完成这一单。`
    await loadOrders()
  } catch (e) {
    captureUnauthorized(e)
    planError.value = vipPayErrorMessage(e)
  } finally {
    busyKey.value = ''
  }
}

/* ---------- 支付宝沙箱（ALIPAY） ---------- */

/** 切换支付方式：还在等对账就先停掉，避免"换了渠道但定时器还在打上一单" */
function onPayChannel(channel: VipPayType) {
  payChannel.value = channel
  planError.value = ''
  planNotice.value = ''
  if (alipayPolling.value) alipayStop()
}

/** 切换支付宝的支付场景（扫码 / 跳收银台）。等待中的对账会话同样先停掉，避免串场景 */
function onPayScene(scene: VipPayScene) {
  payScene.value = scene
  planError.value = ''
  planNotice.value = ''
  if (alipayPolling.value) alipayStop()
}

/** 关掉等待/扫码面板时顺手把二维码副本清掉，免得下次进来先闪一眼上一单的码 */
function onAlipayDismiss() {
  alipayQrCode.value = ''
  alipayTip.value = ''
  alipayPayHtml.value = ''
  alipayOpened.value = false
  alipayDismiss()
}

/**
 * 用「新窗口写入表单并自动提交」打开沙箱收银台，返回是否真的开出来了。
 *
 * 这是新沙箱唯一能用的打开方式：`alipay.trade.page.pay` 的那段表单必须 **POST** 给网关
 * （`openapi-sandbox.dl.alipaydev.com` 是 API 网关，不吃 GET 链接，直接开 `payUrl` 会 404）。
 *
 * 两个刻意的写法：
 * - **不把 `noopener` 写进 `window.open` 的特性串**：按 HTML 规范，带 `noopener` 时
 *   `window.open` 的返回值恒为 `null`，我们就拿不到句柄、没法往新窗口写表单。
 *   改成先拿句柄、再手动 `w.opener = null` —— 安全性等价（新页面拿不到本页的 `window.opener`），
 *   但句柄还在手里。
 * - 写入失败（极少见）时把那个空标签页关掉再返回 false，不留一个空白页给用户。
 *
 * 表单内容是我们自己的后端用 SDK 现签的（同源、第一方数据），不是用户输入拼出来的。
 */
function openAlipayCashier(payHtml: string): boolean {
  if (!payHtml) return false
  const w = window.open('', '_blank')
  if (!w) return false
  try {
    w.opener = null
  } catch {
    // 个别浏览器不允许改 opener，忽略：不影响写表单
  }
  try {
    w.document.open()
    w.document.write(payHtml)
    w.document.close()
    return true
  } catch {
    try {
      w.close()
    } catch {
      // 关不掉就算了，用户自己关
    }
    return false
  }
}

/** 面板里那个「打开沙箱收银台」按钮：点击本身是用户手势，这里再开窗口不会被拦 */
function onOpenAlipayCashier() {
  const ok = openAlipayCashier(alipayPayHtml.value)
  alipayOpened.value = ok
  planNotice.value = ok
    ? `已在新标签页提交表单打开支付宝沙箱收银台，订单 ${alipayOrder.value?.orderNo ?? ''} 在等你付款。付完回到本页会自动对账，查到支付才发会员。`
    : '这次还是没能打开新窗口（浏览器仍在拦截弹窗）。请允许本站弹出窗口后重试，或者改用「扫码支付」那条路。'
}

/**
 * 支付宝沙箱下单：只落一笔待支付，然后按后端返回的 `payParams` 选路。
 *
 * 选路顺序（**扫码是默认场景**，但绝不假设后端一定给码）：
 * 1. 要扫码且后端给了 `qrCode` → 页内出二维码（`AlipayQrPanel`），不跳任何页面
 * 2. 后端给了 `payHtml` → **新沙箱的正路**：新开标签页写入表单并自动提交（`openAlipayCashier`）。
 *    选了扫码却只拿到收银台那两份时也走这条，并在提示里如实说明"这次回退到了收银台"
 * 3. 只给了 `payUrl`（后端没给 `payHtml`，即旧包/旧沙箱）→ `window.open(payUrl)`，老逻辑一字不动
 * 4. 三个都没有 → 如实报错，不进任何面板（进去只会写出"收银台已经打开"这种假话）
 *
 * `PAGE` 不下发 `payScene`：契约只承诺了 `'QR'` 的含义，不传就是后端原本的跳收银台行为，
 * 这样老路径不受新字段影响。`window.open` 在 await 之后调用可能被浏览器拦截，所以**不把它当失败**：
 * 面板里始终留着一个能点的按钮或链接，用户自己点一次就行（点击是用户手势，不会再被拦）。
 */
async function onAlipayPay(plan: VipPlan) {
  if (busyKey.value) return
  busyKey.value = `alipay:${plan.planCode}`
  planError.value = ''
  planNotice.value = ''

  const wantQr = payScene.value === PAY_SCENE_QR

  try {
    const order = await createOrder({
      planCode: plan.planCode,
      payType: PAY_ALIPAY,
      requestId: newRequestId(),
      ...(wantQr ? { payScene: PAY_SCENE_QR } : {}),
    })

    const qr = qrCodeText(order)
    const html = payHtmlText(order)
    const url = payUrlText(order)
    await loadOrders()

    // 有码就用码：这是扫码这条路
    if (qr) {
      alipayQrCode.value = qr
      alipayTip.value = order.payParams?.tip ?? ''
      alipayPayHtml.value = ''
      alipayOpened.value = false
      // 不传 url：扫码场景没有收银台地址，面板也就不会写出"收银台已经打开"
      alipayStart(order)
      planNotice.value = wantQr
        ? `已生成扫码支付订单 ${order.orderNo}。用手机上的支付宝沙箱版扫下面的二维码付款，付完回到本页会自动对账。这是测试环境，不会真实扣款。`
        : `后端这次返回的是支付码（没有收银台地址），所以改用页内二维码支付订单 ${order.orderNo}。用手机上的支付宝沙箱版扫它。`
      return
    }

    // 有收银台表单：**新沙箱的正路**。GET 链接在新沙箱会 404，所以 payHtml 优先于 payUrl
    if (html) {
      alipayQrCode.value = ''
      alipayTip.value = order.payParams?.tip ?? ''
      alipayPayHtml.value = html
      alipayStart(order, url)
      alipayOpened.value = openAlipayCashier(html)
      const how = '在新标签页里把后端给的收银台表单自动提交给支付宝网关（沙箱的网关只接受 POST，直接打开那条 GET 链接会 404）'
      if (!wantQr) {
        planNotice.value = alipayOpened.value
          ? `已${how}，订单 ${order.orderNo} 在等你付款。付完回到本页，下面会自动对账，查到支付才发会员。`
          : `收银台没能自动打开，多半是浏览器拦了弹窗。下面板子里有一个「打开沙箱收银台」按钮，点它就行（这次会${how}）。`
      } else {
        planNotice.value = alipayOpened.value
          ? `后端这次没有返回扫码支付码（payParams.qrCode 为空），已经回退到跳转沙箱收银台：${how}，收银台已打开。订单 ${order.orderNo} 在等你付款，付完回到本页会自动对账。`
          : `后端这次没有返回扫码支付码，已经回退到跳转沙箱收银台，但收银台没能自动打开（多半被浏览器拦了）。下面板子里有一个「打开沙箱收银台」按钮，点它就行。`
      }
      return
    }

    // 没有码也没有表单，但有旧的 GET 链接：走老逻辑（旧沙箱可用；新沙箱这条会 404）
    if (url) {
      alipayQrCode.value = ''
      alipayTip.value = ''
      alipayPayHtml.value = ''
      alipayStart(order, url)
      alipayOpened.value = !!window.open(url, '_blank', 'noopener,noreferrer')
      if (!wantQr) {
        planNotice.value = alipayOpened.value
          ? `已打开支付宝沙箱收银台，订单 ${order.orderNo} 在等你付款。付完回到本页，下面会自动对账，查到支付才发会员。`
          : '支付宝沙箱收银台没能自动打开，多半被浏览器拦了。下面板子里有一条"打开沙箱收银台"的链接，点它就行。'
      } else {
        planNotice.value = alipayOpened.value
          ? `后端这次没有返回扫码支付码（payParams.qrCode 为空），已经回退到跳转沙箱收银台，收银台已打开。订单 ${order.orderNo} 在等你付款，付完回到本页会自动对账。`
          : '后端这次没有返回扫码支付码，已经回退到跳转沙箱收银台，但收银台没能自动打开（多半被浏览器拦了）。下面板子里有一条"打开沙箱收银台"的链接，点它就行。'
      }
      return
    }

    // 三个都没有：别进面板（进了只会说假话）。订单已经落下，在下面订单列表里能看到，也能对它点「立即对账」
    planError.value =
      '后端既没有返回扫码支付码（payParams.qrCode），也没有返回收银台表单（payParams.payHtml）与地址（payParams.payUrl），所以现在没法付款。多半是这个渠道还没配好，可以先用模拟支付。这笔待支付订单已经生成，会在下面的订单列表里。'
  } catch (e) {
    captureUnauthorized(e)
    planError.value = vipPayErrorMessage(e)
  } finally {
    busyKey.value = ''
  }
}

/** 对账查到已支付（后端已发会员）后，刷新权益与订单列表 */
async function onAlipayPaid() {
  await refreshAfterChange()
}

/**
 * 订单列表里对一笔支付宝订单手动对账。
 *
 * 列表返回里 `payParams` 是 null（只有下单那一步带 `payHtml` / `payUrl`），所以这里只对账、不再跳收银台。
 */
async function onSyncOrder(order: VipOrder) {
  if (busyKey.value) return
  busyKey.value = `sync:${order.orderNo}`
  orderError.value = ''
  orderNotice.value = ''
  try {
    const updated = await syncOrder(order.orderNo)
    if (updated.payStatus === PAY_PAID) {
      orderNotice.value = `订单 ${updated.orderNo} 后端已经从支付宝查到这笔支付，会员发放完成（支付宝沙箱，测试环境，没有真实扣款）。`
    } else {
      orderNotice.value = `订单 ${updated.orderNo} 现在还是${payStatusText(updated)}，支付宝那边还没查到这笔支付。付过了可以稍后再点一次对账。`
    }
    await refreshAfterChange()
  } catch (e) {
    captureUnauthorized(e)
    orderError.value = vipPayErrorMessage(e)
  } finally {
    busyKey.value = ''
  }
}

async function onPay(order: VipOrder) {
  if (busyKey.value) return
  busyKey.value = `pay:${order.orderNo}`
  orderError.value = ''
  orderNotice.value = ''
  try {
    const paid = await payOrder(order.orderNo)
    orderNotice.value = `订单 ${paid.orderNo} 模拟支付成功，会员已经发放（渠道为 mock，没有真实扣款）。`
    await refreshAfterChange()
  } catch (e) {
    captureUnauthorized(e)
    orderError.value = vipPayErrorMessage(e)
  } finally {
    busyKey.value = ''
  }
}

function askRefund(order: VipOrder) {
  orderError.value = ''
  orderNotice.value = ''
  confirmRefund.value = order.orderNo
}

async function onRefund(order: VipOrder) {
  if (busyKey.value) return
  busyKey.value = `refund:${order.orderNo}`
  orderError.value = ''
  orderNotice.value = ''
  try {
    const done = await refundOrder(order.orderNo)
    orderNotice.value = `订单 ${done.orderNo} 已退款，按这笔订单的快照时长回收了会员天数。用掉的问答次数和已核销的促销码不会回滚。`
    await refreshAfterChange()
  } catch (e) {
    captureUnauthorized(e)
    orderError.value = vipPayErrorMessage(e)
  } finally {
    busyKey.value = ''
    confirmRefund.value = null
  }
}

async function onRedeem() {
  const code = promoCode.value.trim()
  if (!code || promoBusy.value) return
  promoBusy.value = true
  promoError.value = ''
  promoNotice.value = ''
  try {
    const order = await redeemPromo(code)
    promoNotice.value = `兑换成功，会员时长 ${order.durationDays} 天已经到账。这笔是 0 元促销单，不能退款。`
    promoCode.value = ''
    await refreshAfterChange()
  } catch (e) {
    captureUnauthorized(e)
    promoError.value = vipPayErrorMessage(e)
  } finally {
    promoBusy.value = false
  }
}

onMounted(() => {
  void Promise.all([loadPlans(), loadBenefit(), loadOrders()])
})
</script>

<template>
  <div class="mw-container mw-page">
    <header class="head">
      <h1 class="mw-h1">喵汪会员</h1>
      <p class="mw-caption">
        AI 问答次数、宠物数量上限、健康记录条数与 AI
        症状解读都按档位给量。下面看到的价格与权益数值全部来自接口，
        改价格与次数改的是数据库，前端没有写死任何一个数。
      </p>
    </header>

    <p class="mw-alert mw-alert--info mock">
      这页有两条支付路径，都不会真实收款：默认的「模拟支付」一步到账；
      「支付宝沙箱」是测试环境，默认在我们自己的页面上出二维码给手机上的支付宝沙箱版扫，
      也可以切回原来的跳沙箱收银台。两条路走的都是和真实渠道回调完全相同的链路，
      包括下单、金额核对、发会员、退款回收时长，接入真实支付时换的是渠道实现，这条链路不用改。
    </p>

    <p v-if="unauthorized || !auth.isLoggedIn" class="mw-alert mw-alert--danger auth-warn">
      <span>登录状态失效了，档位与会员信息需要登录后才能读取。</span>
      <RouterLink
        class="mw-btn mw-btn--ghost auth-warn__btn"
        :to="{ name: 'login', query: { redirect: '/vip' } }"
      >
        去登录
      </RouterLink>
    </p>

    <!-- 我的会员状态：深色带，和首页舞台同族。到期时间用琥珀（它只表示时间） -->
    <section class="status mw-night">
      <p v-if="benefitLoading && !benefit" class="status__loading mw-skeleton" />

      <div v-else-if="benefitError" class="status__err">
        <p class="status__title">权益没能读到</p>
        <p class="status__msg">{{ benefitError }}</p>
        <button class="mw-btn mw-btn--brand" type="button" @click="loadBenefit">
          <PhArrowClockwise :size="16" aria-hidden="true" />
          重试
        </button>
      </div>

      <template v-else-if="benefit">
        <div class="status__main">
          <p class="status__eyebrow">我的会员状态</p>
          <p class="status__title">
            {{ benefit.vip ? '会员有效' : '免费档' }}
            <span v-if="benefit.vip" class="mw-badge mw-badge--on-night">
              <PhSealCheck :size="12" weight="fill" aria-hidden="true" />
              已开通
            </span>
          </p>
          <p class="status__plans">
            {{
              benefit.planNames.length
                ? benefit.planNames.join('、')
                : '还没有开通任何档位，下面是可选的'
            }}
          </p>
          <p class="status__expire">
            <PhClock :size="14" aria-hidden="true" />
            <span class="status__expire-badge">到期时间</span>
            <span class="status__expire-time mw-num">{{ dateTimeText(benefit.expireTime) }}</span>
          </p>
        </div>

        <dl class="status__stats">
          <div class="stat">
            <dt>AI 问答</dt>
            <dd class="mw-num">{{ quotaText(benefit.ragRemaining) }}</dd>
            <p>
              {{ benefit.ragPeriodDesc || periodText(benefit.ragPeriod) }}额度，上限
              {{ limitText(benefit.ragLimit, '次') }}
            </p>
          </div>
          <div class="stat">
            <dt>AI 对话</dt>
            <dd class="mw-num">{{ quotaText(benefit.chatRemaining) }}</dd>
            <p>
              {{ benefit.chatPeriodDesc || periodText(benefit.chatPeriod) }}额度，上限
              {{ limitText(benefit.chatLimit, '轮') }}
            </p>
          </div>
          <div class="stat">
            <dt>宠物数量上限</dt>
            <dd class="mw-num">{{ limitText(benefit.petLimitPerType, '只') }}</dd>
            <p>猫狗合计，不区分物种</p>
          </div>
          <div class="stat">
            <dt>每类健康记录</dt>
            <dd class="mw-num">{{ limitText(benefit.healthRecordLimit, '条') }}</dd>
            <p>
              报告导出 {{ switchText(benefit.healthExport) }}，AI 解读
              {{ switchText(benefit.healthAiAnalysis) }}
            </p>
          </div>
        </dl>
      </template>
    </section>

    <!-- 档位 -->
    <section class="plans">
      <div class="plans__head">
        <h2 class="mw-h2">可选档位</h2>
        <p class="mw-caption">续费同一档位会在原到期时间上叠加时长，档位与权益数值都来自接口。</p>
      </div>

      <!-- 支付方式：默认模拟支付（与原来一致），可切到支付宝沙箱 -->
      <div class="channel">
        <div class="mw-seg channel__seg" role="group" aria-label="选择支付方式">
          <button
            class="mw-seg__item"
            :class="{ 'mw-seg__item--on': payChannel === PAY_MOCK }"
            type="button"
            :aria-pressed="payChannel === PAY_MOCK"
            @click="onPayChannel(PAY_MOCK)"
          >
            <PhSealCheck :size="14" aria-hidden="true" />
            模拟支付（不扣款）
          </button>
          <button
            class="mw-seg__item"
            :class="{ 'mw-seg__item--on': payChannel === PAY_ALIPAY }"
            type="button"
            :aria-pressed="payChannel === PAY_ALIPAY"
            @click="onPayChannel(PAY_ALIPAY)"
          >
            <PhArrowSquareOut :size="14" aria-hidden="true" />
            支付宝沙箱（测试环境，不会真实扣款）
          </button>
        </div>
        <p class="channel__hint">
          <template v-if="payChannel === PAY_MOCK">
            模拟支付走的是和真实渠道回调完全相同的链路（下单、金额核对、发会员、退款回收时长），
            只是不会真实扣款，一步到账。
          </template>
          <template v-else>
            支付宝沙箱是测试环境，不会真实扣款。付完款回到本页，每 3 秒自动向后端对账一次，查到支付才发会员。
          </template>
        </p>
      </div>

      <!-- 支付宝的两种场景：默认扫码（页内出二维码，不跳收银台），另一种是原来的跳收银台 -->
      <div v-if="payChannel === PAY_ALIPAY" class="scene">
        <div class="mw-seg scene__seg" role="group" aria-label="选择支付宝支付场景">
          <button
            class="mw-seg__item"
            :class="{ 'mw-seg__item--on': payScene === PAY_SCENE_QR }"
            type="button"
            :aria-pressed="payScene === PAY_SCENE_QR"
            @click="onPayScene(PAY_SCENE_QR)"
          >
            <PhQrCode :size="14" aria-hidden="true" />
            扫码支付（默认，页内出二维码）
          </button>
          <button
            class="mw-seg__item"
            :class="{ 'mw-seg__item--on': payScene === PAY_SCENE_PAGE }"
            type="button"
            :aria-pressed="payScene === PAY_SCENE_PAGE"
            @click="onPayScene(PAY_SCENE_PAGE)"
          >
            <PhArrowSquareOut :size="14" aria-hidden="true" />
            跳转沙箱收银台（原来的方式）
          </button>
        </div>
        <p class="scene__hint">
          <template v-if="payScene === PAY_SCENE_QR">
            扫码支付不下发收银台地址：后端返回的是一串支付码，由本页把它画成二维码，
            用手机上的支付宝沙箱版扫。要是后端这次没给支付码，会自动回退到跳收银台那条路。
          </template>
          <template v-else>
            跳转收银台是原来那条路：后端返回一段收银台表单（payParams.payHtml），
            浏览器新开一个标签页把表单 POST 给支付宝网关，由网关再跳到收银台。
            后端没给表单时退回 payParams.payUrl 那条 GET 链接（旧沙箱可用；新沙箱的网关只接受
            POST、不渲染收银台页面，打开 GET 链接会 404，所以表单才是正路）。
          </template>
        </p>
      </div>

      <div v-if="plansLoading && !plans.length" class="plans__grid">
        <span v-for="i in 3" :key="i" class="mw-skeleton plan-skeleton" />
      </div>

      <div v-else-if="plansError" class="mw-state">
        <p class="mw-state__title">档位没能读到</p>
        <p>{{ plansError }}</p>
        <button class="mw-btn mw-btn--ghost" type="button" @click="loadPlans">
          <PhArrowClockwise :size="16" aria-hidden="true" />
          重试
        </button>
      </div>

      <p v-else-if="!plans.length" class="mw-state">接口没有返回可购买档位，过一会再刷新试试。</p>

      <div v-else class="plans__grid">
        <article v-for="p in plans" :key="p.planCode" class="plan mw-card">
          <header>
            <h3 class="plan__name">{{ p.planName }}</h3>
            <p class="plan__code mw-num">{{ p.planCode }}</p>
            <p class="plan__price mw-num">
              {{ moneyText(p.price) }}
              <span class="plan__unit">/ {{ p.durationDays }} 天</span>
            </p>
          </header>

          <ul class="plan__rows">
            <li v-for="r in planRows(p)" :key="r.label" class="plan__row">
              <span class="plan__label">{{ r.label }}</span>
              <span class="plan__value mw-num">{{ r.value }}</span>
              <span v-if="r.note" class="plan__note">{{ r.note }}</span>
            </li>
          </ul>

          <div class="plan__actions">
            <template v-if="payChannel === PAY_ALIPAY">
              <button
                class="mw-btn mw-btn--primary plan__cta"
                type="button"
                :disabled="!!busyKey"
                @click="onAlipayPay(p)"
              >
                <PhQrCode v-if="payScene === PAY_SCENE_QR" :size="16" aria-hidden="true" />
                <PhArrowSquareOut v-else :size="16" aria-hidden="true" />
                {{
                  busyKey === `alipay:${p.planCode}`
                    ? '下单中…'
                    : payScene === PAY_SCENE_QR
                      ? '生成支付二维码'
                      : '用支付宝沙箱支付'
                }}
              </button>
              <p v-if="payScene === PAY_SCENE_QR" class="plan__hint">
                先落一笔待支付订单，再把支付码画成二维码
              </p>
              <p v-else class="plan__hint">先落一笔待支付订单，再打开沙箱收银台</p>
            </template>

            <template v-else>
              <button
                class="mw-btn mw-btn--primary plan__cta"
                type="button"
                :disabled="!!busyKey"
                @click="onSubscribe(p)"
              >
                <PhCrown :size="16" weight="fill" aria-hidden="true" />
                {{ busyKey === `subscribe:${p.planCode}` ? '开通中…' : '开通（模拟支付）' }}
              </button>
              <button
                class="mw-btn mw-btn--ghost plan__alt"
                type="button"
                :disabled="!!busyKey"
                @click="onCreateOrder(p)"
              >
                {{ busyKey === `create:${p.planCode}` ? '下单中…' : '只生成待支付订单' }}
              </button>
            </template>
          </div>
        </article>
      </div>

      <p v-if="planError" class="mw-alert mw-alert--danger plans__feedback">{{ planError }}</p>
      <p v-else-if="planNotice" class="mw-alert mw-alert--info plans__feedback">{{ planNotice }}</p>

      <!-- 支付宝沙箱：扫码场景出二维码，跳收银台场景出等待面板。两者共用同一个对账会话（定时器在 composable 里，离开页面自动清） -->
      <AlipayQrPanel
        v-if="alipayActive && alipayOrder && alipayQrCode"
        :order="alipayOrder"
        :qr-code="alipayQrCode"
        :tip="alipayTip"
        :phase="alipayPhase"
        :notice="alipayNotice"
        :error="alipayError"
        :checks="alipayChecks"
        :syncing="alipaySyncing"
        :countdown-text="alipayCountdown"
        :seconds-left="alipaySecondsLeft"
        :polling="alipayPolling"
        @check="alipayRetry"
        @stop="alipayStop"
        @dismiss="onAlipayDismiss"
      />

      <AlipayWaitPanel
        v-else-if="alipayActive && alipayOrder"
        :order="alipayOrder"
        :phase="alipayPhase"
        :pay-url="alipayPayUrl"
        :pay-html="alipayPayHtml"
        :notice="alipayNotice"
        :error="alipayError"
        :checks="alipayChecks"
        :syncing="alipaySyncing"
        :countdown-text="alipayCountdown"
        :seconds-left="alipaySecondsLeft"
        :polling="alipayPolling"
        :opened="alipayOpened"
        @check="alipayRetry"
        @open="onOpenAlipayCashier"
        @stop="alipayStop"
        @dismiss="onAlipayDismiss"
      />

      <p v-if="payChannel === PAY_MOCK" class="plans__note">
        「开通」等于下单加模拟支付，一步到账；「只生成待支付订单」只落一条待支付订单，
        会员要等你到下面点「去支付（模拟）」之后才发。真实渠道下这两步就是下单、用户付款、渠道回调、发放会员。
      </p>
      <p v-else-if="payScene === PAY_SCENE_QR" class="plans__note">
        扫码支付只有一步：下单拿回支付码，本页把它画成二维码，你用手机上的支付宝沙箱版扫它付款。
        付款后不必跳回任何页面，本页每 3 秒自动对账。会员是否发放只认后端对账接口
        POST /vip/order/订单号/sync 返回的订单状态，页面上的二维码画得再好看也不算数。
      </p>
      <p v-else class="plans__note">
        跳转收银台只有一步：下单后新开一个标签页，把后端给的收银台表单自动提交给支付宝网关，
        网关再把用户送到收银台；后端没给表单时才退回旧的 GET 链接。付完回到本页自动对账。
        会员是否发放只认后端对账接口 POST /vip/order/订单号/sync 返回的订单状态，页面地址上的参数不作数。
      </p>
    </section>

    <div class="cols">
      <!-- 订单 -->
      <section class="orders">
        <div class="orders__head">
          <h2 class="mw-h2">我的订单</h2>
          <p class="mw-caption">
            <template v-if="ordersLoading && !orders.length">正在读取</template>
            <template v-else>{{ orders.length }} 笔</template>
          </p>
        </div>

        <p v-if="pendingOrders.length && !ordersLoading" class="mw-alert mw-alert--info">
          有 {{ pendingOrders.length }} 笔待支付订单，超过 30
          分钟未支付会自动关闭（后端在读列表时顺手关单）。
        </p>

        <p v-if="orderError" class="mw-alert mw-alert--danger">{{ orderError }}</p>
        <p v-else-if="orderNotice" class="mw-alert mw-alert--info">{{ orderNotice }}</p>

        <div v-if="ordersLoading && !orders.length" class="orders__rows">
          <span v-for="i in 3" :key="i" class="mw-skeleton order-skeleton" />
        </div>

        <div v-else-if="ordersError" class="mw-state">
          <p class="mw-state__title">订单没能读到</p>
          <p>{{ ordersError }}</p>
          <button class="mw-btn mw-btn--ghost" type="button" @click="loadOrders">
            <PhArrowClockwise :size="16" aria-hidden="true" />
            重试
          </button>
        </div>

        <div v-else-if="!orders.length" class="mw-state">
          <PhReceipt :size="26" aria-hidden="true" />
          <p class="mw-state__title">还没有订单</p>
          <p>在上面挑一个档位开通，订单会出现在这里。</p>
        </div>

        <ul v-else class="orders__rows">
          <li v-for="o in orders" :key="o.orderNo" class="order">
            <div class="order__main">
              <p class="order__plan">{{ o.planName }}</p>
              <p class="order__no mw-num">{{ o.orderNo }}</p>
              <p class="order__time">
                <span>下单 {{ dateTimeText(o.createTime) }}</span>
                <span v-if="o.payTime">支付 {{ dateTimeText(o.payTime) }}</span>
                <span>渠道 {{ channelText(o.payType) }}</span>
              </p>
            </div>

            <div class="order__amount">
              <p class="order__pay mw-num">实付 {{ moneyText(o.payableAmount) }}</p>
              <p v-if="o.discountAmount > 0" class="order__origin">
                <span
                  >原价 <s class="mw-num">{{ moneyText(o.amount) }}</s></span
                >
                <span
                  >优惠 <span class="mw-num">{{ moneyText(o.discountAmount) }}</span></span
                >
                <span v-if="o.promoCode">码 {{ o.promoCode }}</span>
              </p>
              <p v-else-if="o.payType === 'PROMO'" class="order__origin">0 元促销单</p>
            </div>

            <div class="order__side">
              <span class="mw-badge" :class="payStatusClass(o)">{{ payStatusText(o) }}</span>

              <template v-if="o.payStatus === PAY_PENDING">
                <!-- 支付宝订单**不能**调 /pay（那个接口只给 mock 用），只能让后端去支付宝查单 -->
                <template v-if="o.payType === PAY_ALIPAY">
                  <button
                    class="mw-btn mw-btn--primary order__act"
                    type="button"
                    :disabled="!!busyKey"
                    @click="onSyncOrder(o)"
                  >
                    <PhArrowClockwise :size="14" aria-hidden="true" />
                    {{ busyKey === `sync:${o.orderNo}` ? '对账中…' : '立即对账' }}
                  </button>
                  <p class="order__hint">
                    支付宝沙箱的待支付单。付过款就点对账，后端会去支付宝查单。订单列表里既不带收银台地址
                    也不带二维码，想重新付就在上面再下一单，这张没付的会自己关闭。
                  </p>
                </template>

                <template v-else>
                  <button
                    class="mw-btn mw-btn--primary order__act"
                    type="button"
                    :disabled="!!busyKey"
                    @click="onPay(o)"
                  >
                    {{ busyKey === `pay:${o.orderNo}` ? '支付中…' : '去支付（模拟）' }}
                  </button>
                  <!-- 后端没有自助取消接口（VipOrderController 只有下单/列表/支付/退款），
                       所以这里如实写清楚，而不是摆一个点了没反应的按钮 -->
                  <button
                    class="mw-btn mw-btn--ghost order__act"
                    type="button"
                    disabled
                    title="后端暂未提供自助取消接口"
                  >
                    取消订单
                  </button>
                  <p class="order__hint">
                    暂不支持自助取消。不想付就别管它，30 分钟后刷新列表它会自己变成已关闭。
                  </p>
                </template>
              </template>

              <template v-else-if="o.payStatus === PAY_PAID">
                <template v-if="canRefund(o)">
                  <template v-if="confirmRefund === o.orderNo">
                    <button
                      class="mw-btn mw-btn--primary order__act"
                      type="button"
                      :disabled="!!busyKey"
                      @click="onRefund(o)"
                    >
                      {{ busyKey === `refund:${o.orderNo}` ? '退款中…' : '确认退款' }}
                    </button>
                    <button
                      class="order__cancel"
                      type="button"
                      :disabled="!!busyKey"
                      @click="confirmRefund = null"
                    >
                      先不退
                    </button>
                  </template>
                  <button
                    v-else
                    class="mw-btn mw-btn--ghost order__act"
                    type="button"
                    :disabled="!!busyKey"
                    @click="askRefund(o)"
                  >
                    申请退款
                  </button>
                </template>
                <p v-else class="order__hint">{{ refundBlockReason(o) }}</p>
              </template>
            </div>
          </li>
        </ul>

        <p class="orders__note">
          退款只回收这笔订单快照里的会员天数，已经用掉的问答次数与已核销的促销码不会回滚。
        </p>
      </section>

      <!-- 兑换码 -->
      <aside class="promo mw-card">
        <h2 class="mw-h2">兑换码</h2>
        <p class="mw-caption">
          手里有送会员时长的兑换码就填在这里，兑换成功立刻到账，并生成一笔 0 元促销订单。
        </p>
        <form class="promo__form" @submit.prevent="onRedeem">
          <label class="promo__field">
            <span class="promo__label">兑换码</span>
            <input
              v-model="promoCode"
              class="mw-input"
              type="text"
              placeholder="粘贴或输入兑换码"
              autocomplete="off"
            />
          </label>
          <button
            class="mw-btn mw-btn--primary promo__btn"
            type="submit"
            :disabled="promoBusy || !promoCode.trim()"
          >
            <PhGift :size="16" aria-hidden="true" />
            {{ promoBusy ? '兑换中…' : '兑换' }}
          </button>
        </form>
        <p v-if="promoError" class="mw-alert mw-alert--danger promo__msg">{{ promoError }}</p>
        <p v-else-if="promoNotice" class="mw-alert mw-alert--info promo__msg">{{ promoNotice }}</p>
        <p class="promo__note">
          送时长的码走这里。下单立减的码（DISCOUNT 类型）是下单时抵扣的，本页没有做立减下单入口，
          接口已经支持，需要时在订单表单里补一个输入框即可。
        </p>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-2);
  max-width: 78ch;
  margin-bottom: var(--mw-space-4);
}
.mock {
  margin-bottom: var(--mw-space-6);
}
.auth-warn {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--mw-space-4);
  margin-bottom: var(--mw-space-6);
}
.auth-warn__btn {
  min-height: 36px;
  padding: 0 var(--mw-space-4);
  font-size: var(--mw-font-caption);
  flex: none;
}

/* ---- 我的会员状态（深色区：不用阴影，靠细线与层次亮度） ---- */
.status {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.15fr);
  gap: var(--mw-space-8);
  padding: var(--mw-space-8);
  border-radius: var(--mw-radius-stage);
  margin-bottom: var(--mw-space-8);
}
.status__loading {
  display: block;
  grid-column: 1 / -1;
  height: 140px;
}
.status__err {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--mw-space-3);
  grid-column: 1 / -1;
}
.status__msg {
  color: var(--mw-on-night-2);
  font-size: var(--mw-font-caption);
}
.status__eyebrow {
  font-size: var(--mw-font-micro);
  letter-spacing: 0.04em;
  color: var(--mw-on-night-2);
}
.status__title {
  display: flex;
  align-items: center;
  gap: var(--mw-space-2);
  margin-top: var(--mw-space-1);
  font-size: var(--mw-font-h1);
  font-weight: 700;
  letter-spacing: -0.02em;
}
.status__plans {
  margin-top: var(--mw-space-1);
  color: var(--mw-on-night-2);
  font-size: var(--mw-font-caption);
}
.status__expire {
  display: flex;
  align-items: center;
  gap: var(--mw-space-2);
  margin-top: var(--mw-space-4);
  color: var(--mw-on-night-2);
  font-size: var(--mw-font-caption);
}
/* 琥珀只做填充（时间语义），深色底上文字用 night 保证对比度 */
.status__expire-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px var(--mw-space-2);
  border-radius: var(--mw-radius-tag);
  background: var(--mw-accent);
  color: var(--mw-night);
  font-size: var(--mw-font-micro);
  font-weight: 600;
}
.status__expire-time {
  color: var(--mw-on-night);
}
.status__stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--mw-space-4);
}
.stat {
  padding: var(--mw-space-4);
  border: 1px solid var(--mw-night-line);
  border-radius: var(--mw-radius-card);
  background: var(--mw-night-2);
}
.stat dt {
  font-size: var(--mw-font-micro);
  color: var(--mw-on-night-2);
}
.stat dd {
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.02em;
}
.stat p {
  font-size: var(--mw-font-micro);
  color: var(--mw-on-night-2);
}

/* ---- 档位 ---- */
.plans {
  margin-bottom: var(--mw-space-8);
}
.plans__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--mw-space-4);
  margin-bottom: var(--mw-space-4);
}
/* 支付方式分段控件（复用全局 .mw-seg，这里只补图标对齐与换行） */
.channel {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-2);
  margin-bottom: var(--mw-space-4);
}
.channel__seg {
  flex-wrap: wrap;
  max-width: 100%;
}
.channel__seg .mw-seg__item {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-1);
}
.channel__hint {
  max-width: 78ch;
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
}
/* 支付宝的两种场景（扫码 / 跳收银台）：比渠道那层退一级，用细线左缩进区分层级 */
.scene {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-2);
  margin: calc(-1 * var(--mw-space-2)) 0 var(--mw-space-4) var(--mw-space-4);
  padding-left: var(--mw-space-4);
  border-left: 1px solid var(--mw-line);
}
.scene__seg {
  flex-wrap: wrap;
  max-width: 100%;
}
.scene__seg .mw-seg__item {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-1);
}
.scene__hint {
  max-width: 78ch;
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
}
.plans__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(236px, 1fr));
  gap: var(--mw-space-4);
  align-items: stretch;
}
.plan-skeleton {
  display: block;
  height: 320px;
}
.plan {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-4);
  padding: var(--mw-space-4);
}
.plan__name {
  font-size: var(--mw-font-body);
  font-weight: 600;
}
.plan__code {
  font-size: var(--mw-font-micro);
  letter-spacing: 0.04em;
  color: var(--mw-muted);
}
.plan__price {
  margin-top: var(--mw-space-2);
  font-size: 34px;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.1;
}
.plan__unit {
  margin-left: var(--mw-space-1);
  font-size: var(--mw-font-caption);
  font-weight: 400;
  color: var(--mw-ink-2);
}
.plan__rows {
  border-top: 1px solid var(--mw-line);
}
.plan__row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 0 var(--mw-space-2);
  padding: var(--mw-space-2) 0;
  border-bottom: 1px solid var(--mw-line);
  font-size: var(--mw-font-caption);
}
.plan__label {
  color: var(--mw-ink-2);
}
.plan__value {
  font-weight: 600;
  text-align: right;
}
.plan__note {
  grid-column: 1 / -1;
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}
.plan__actions {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-2);
  margin-top: auto;
}
.plan__cta {
  min-height: 40px;
  padding: 0 var(--mw-space-4);
  font-size: var(--mw-font-caption);
}
.plan__alt {
  min-height: 36px;
  padding: 0 var(--mw-space-3);
  font-size: var(--mw-font-micro);
}
.plan__hint {
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
  text-align: center;
}
.plans__feedback {
  margin-top: var(--mw-space-4);
}
.plans__note {
  max-width: 96ch;
  margin-top: var(--mw-space-4);
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
}

/* ---- 订单 + 兑换码 ---- */
.cols {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(260px, 0.8fr);
  gap: var(--mw-space-6);
  align-items: start;
}
.orders {
  padding: var(--mw-space-6);
  background: var(--mw-surface);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
}
.orders__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--mw-space-4);
  margin-bottom: var(--mw-space-3);
}
.orders__rows {
  display: flex;
  flex-direction: column;
  margin-top: var(--mw-space-3);
}
.order-skeleton {
  display: block;
  height: 64px;
  margin-bottom: var(--mw-space-2);
}
.order {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 0.9fr) auto;
  gap: var(--mw-space-3);
  align-items: center;
  padding: var(--mw-space-3) 0;
  border-bottom: 1px solid var(--mw-line);
}
.order:last-child {
  border-bottom: none;
}
.order__plan {
  font-size: var(--mw-font-caption);
  font-weight: 600;
}
.order__no {
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
  word-break: break-all;
}
.order__time {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mw-space-2);
  font-size: var(--mw-font-micro);
  color: var(--mw-ink-2);
}
.order__pay {
  font-size: var(--mw-font-caption);
  font-weight: 600;
}
.order__origin {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mw-space-2);
  font-size: var(--mw-font-micro);
  color: var(--mw-ink-2);
}
.order__side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--mw-space-1);
}
.badge--void {
  background: var(--mw-canvas);
  color: var(--mw-ink-2);
}
.order__act {
  min-height: 34px;
  padding: 0 var(--mw-space-3);
  font-size: var(--mw-font-micro);
}
.order__cancel {
  padding: var(--mw-space-1);
  border: none;
  background: transparent;
  font-family: inherit;
  font-size: var(--mw-font-micro);
  color: var(--mw-ink-2);
  cursor: pointer;
}
.order__hint {
  max-width: 28ch;
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
  text-align: right;
}
.orders__note {
  margin-top: var(--mw-space-3);
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}

.promo {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-3);
}
.promo__field {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-2);
}
.promo__label {
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
}
.promo__form {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-3);
}
.promo__btn {
  min-height: 40px;
  font-size: var(--mw-font-caption);
}
.promo__msg {
  margin: 0;
}
.promo__note {
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}

@media (max-width: 960px) {
  .status,
  .cols,
  .order {
    grid-template-columns: minmax(0, 1fr);
  }
  .order__side {
    align-items: flex-start;
  }
  .order__hint {
    text-align: left;
  }
  .plans__head,
  .orders__head {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--mw-space-1);
  }
}
</style>
