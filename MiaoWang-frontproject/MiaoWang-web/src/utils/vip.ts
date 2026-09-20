/**
 * 会员支付的展示与错误文案（Web）
 *
 * 两件事：
 * 1) 金额 / 时间 / 状态的中文格式化 —— 会员页与「支付结果」页都要用，放一处免得两边写岔
 * 2) `vipPayErrorMessage`：把支付相关的后端错误码翻成人话
 *
 * 这里最要紧的一条是**渠道没配好**这一类错误：后端报的是 `B000513 暂不支持该支付渠道`
 * 或一条带着"未配置 / 密钥"的 message，直接丢给用户等于没说。翻译成
 * 「管理员还没配好支付宝沙箱密钥」之后，用户知道该找谁、也知道不是自己的操作问题。
 */
import { PAY_CLOSED, PAY_PAID, PAY_PENDING, PAY_REFUNDED, type VipOrder } from '../api/vip'
import { ApiError } from '../api/request'

/* ---------- 展示用的格式化 ---------- */

/** 金额一律两位小数，界面里不要出现 4.9 这种未对齐的写法 */
export function moneyText(v: number): string {
  return `¥${Number(v).toFixed(2)}`
}

/** `2026-09-18T06:20:53.000+00:00` → `2026-09-18 14:20`（本地时区）；解析不了就原样吐出 */
export function dateTimeText(iso: string | null | undefined): string {
  if (!iso) return '暂无'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/** `payStatusDesc` 实测总是有值；没有时按 code 兜底，别让界面出现空白状态 */
export function payStatusText(o: VipOrder): string {
  if (o.payStatusDesc) return o.payStatusDesc
  if (o.payStatus === PAY_PENDING) return '待支付'
  if (o.payStatus === PAY_PAID) return '已支付'
  if (o.payStatus === PAY_CLOSED) return '已关闭'
  if (o.payStatus === PAY_REFUNDED) return '已退款'
  return '未知状态'
}

/** 订单状态徽标的配色类（沿用全站四个徽标类，不新造颜色） */
export function payStatusClass(o: VipOrder): string {
  if (o.payStatus === PAY_PENDING) return 'mw-badge--time'
  if (o.payStatus === PAY_PAID) return 'mw-badge--done'
  return 'badge--void'
}

/**
 * 渠道的中文说法。支付宝必须点明是**沙箱**，否则用户会以为这是真实收款渠道。
 * 认不出的渠道原样吐出（后端以后加微信时不用改前端）。
 */
export function channelText(payType: string): string {
  if (payType === 'MOCK') return '模拟支付'
  if (payType === 'ALIPAY') return '支付宝沙箱'
  if (payType === 'PROMO') return '兑换码'
  return payType || '未知渠道'
}

/* ---------- 支付宝沙箱两种场景的取用（扫码 / 跳收银台） ---------- */

/**
 * 取下单返回里的支付宝**支付码字符串**（扫码场景，`payScene: 'QR'`）。
 *
 * 拿到的是形如 `https://qr.alipay.com/bavh4wjlxf12tper3a` 的一串**码**，
 * 不是图片地址：要交给二维码库编码成图，`<img :src>` 直接塞进去只会是裂图。
 *
 * 为什么要在下单那一步就抄出来：`POST /vip/order/{no}/sync` 返回的订单里 `payParams` 是 `null`，
 * 对账每 3 秒就会把订单对象换成新的一份，指着 `order.payParams` 渲染的二维码会自己消失。
 */
export function qrCodeText(order: VipOrder): string {
  return (order.payParams?.qrCode ?? '').trim()
}

/** 取下单返回里的沙箱收银台地址（跳收银台场景，旧路径）。列表/对账返回的订单里没有它 */
export function payUrlText(order: VipOrder): string {
  return (order.payParams?.payUrl ?? '').trim()
}

/**
 * 取跳收银台场景要用的**表单 HTML**（新沙箱的正路）。
 *
 * 为什么表单才是正路：`openapi-sandbox.dl.alipaydev.com` 是新版沙箱的 **API 网关主机**，
 * 只接受 POST 的接口调用、不渲染收银台页面 —— 把上面那条 `payUrl`（GET 链接）直接
 * `window.open` 出去，支付宝会回 **404**（真沙箱实测）。`alipay.trade.page.pay` 的官方集成方式
 * 一直是「把这段表单 POST 到网关，由网关再跳到收银台」，所以**有它就用它**。
 *
 * ⚠️ 返回的是一整段 HTML（含 `<script>document.forms[0].submit()</script>`），
 * 只允许喂给 `window.open('', '_blank')` 得到的那个窗口的 `document.write`，
 * **不要**塞进页面的可见区域，也**不要**打印出来。
 */
export function payHtmlText(order: VipOrder): string {
  return (order.payParams?.payHtml ?? '').trim()
}

/* ---------- 错误文案 ---------- */

/**
 * 「后端还没把沙箱密钥配上」的兜底识别。
 *
 * 渠道未启用时后端可能给 `B000513`，也可能给一条带"未配置 / 密钥 / appId"的 message
 * （新码与旧码同时存在的那段时间里两种都会出现）。码先判，这里只兜没有码的情况。
 */
const NOT_CONFIGURED_PATTERN = /未配置|没配置|尚未配置|没有配置|密钥|私钥|公钥|app-?id|签名方式/i

/** 对账这条路本身走不通（后端还没上线 `/sync`、网络不通、或渠道密钥没配好）→ 不用再等 3 分钟 */
export function isSyncUnavailable(e: unknown): boolean {
  if (!(e instanceof ApiError)) return false
  return (
    e.code === 'NETWORK' ||
    e.code === 'BAD_JSON' ||
    // 未映射路径在 vip 服务上不回 404，而是笼统的 B000001「系统执行出错」
    e.code === 'B000001' ||
    e.code === '404' ||
    e.code === 'B000513' ||
    // B000533 渠道未配置：密钥没配好时对账同样走不通
    e.code === 'B000533'
  )
}

/**
 * 支付相关错误码 → 人话。
 *
 * 有意把「渠道没配好」和「你操作错了」分开说：前者用户只能等管理员，
 * 后者用户自己能处理。原始错误码仍然带上（`B000513` 这种），方便排查时对日志。
 */
export function vipPayErrorMessage(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.isUnauthorized) return '登录已过期，请重新登录后再对账。'
    const byCode: Record<string, string> = {
      B000001:
        '对账接口没能调通。后端可能还没上线这个接口，或者服务没起来，待支付订单不受影响，稍后再试。',
      B000500: '这个档位不存在了，刷新页面重新选一个。',
      B000501: e.message || '这个档位暂不支持购买。',
      B000502: '这笔订单不存在，可能已经在超时后被自动关闭了。',
      B000503: '订单没能创建成功，稍后再试。',
      B000512: '这笔订单当前的状态不允许做这个操作，刷新订单列表看看最新状态。',
      B000513: '管理员还没配好支付宝沙箱密钥，后端暂时不能走支付宝这个渠道。可以先用模拟支付，或者等配置好再来。',
      // 渠道未配置（后端 `/vip/order` 在密钥缺失时给的就是它）
      B000533:
        '管理员还没配好支付宝沙箱密钥（要在配置里补齐 appId、应用私钥、支付宝公钥），这个渠道暂时用不了。可以先用模拟支付，或者等配置好再来。',
      // 渠道调用失败（支付宝下单/查单调不通，或压根没返回支付地址）
      B000534: '支付宝沙箱那边没调通，这次下单或查单失败了，稍后再试。不是你操作的问题。',
      B000514: '支付宝的签名没验过，这笔支付没有被确认。收款账号的密钥可能配错了，请联系管理员。',
      B000515: '支付宝返回的金额和订单对不上，没有发放会员。请联系管理员核对订单。',
      B000516: '这条支付结果已经处理过或已过期，以订单列表里的状态为准。',
      B000517: '这个渠道不接受外部回调，支付宝付款请走沙箱收银台。',
      B000518: e.message || '已超过自助退款期限。',
      B000519: '退款没成功，稍后再试或联系管理员。',
      B000520: '这个兑换码不存在或已失效。',
      NETWORK: e.message || '连不上后端，检查后端与代理是否已启动。',
      BAD_JSON: '服务返回了非 JSON 内容，多半是网关或代理打到了别的地方。',
    }
    const mapped = byCode[e.code]
    if (mapped) return mapped
    if (NOT_CONFIGURED_PATTERN.test(e.message)) {
      return `管理员还没配好支付宝沙箱密钥，这个渠道暂时用不了。原始提示：${e.message}（${e.code}）`
    }
    return e.message || `操作失败（${e.code}）`
  }
  return e instanceof Error ? e.message : '操作失败，稍后再试'
}