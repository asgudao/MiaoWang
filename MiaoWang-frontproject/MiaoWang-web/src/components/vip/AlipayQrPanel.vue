<script setup lang="ts">
/**
 * 支付宝沙箱「扫码支付」面板
 *
 * 这是扫码这条路的落点：用户**不跳收银台**，二维码直接出在我们自己的页面上，
 * 用手机上的「支付宝沙箱版」扫，付完款由已有的主动查单对账（`POST /vip/order/{no}/sync`）确认。
 *
 * ⚠️ 最容易搞错的一点：后端给的 `payParams.qrCode` 是一串**支付码字符串**
 * （形如 `https://qr.alipay.com/bavh4wjlxf12tper3a`），**不是图片地址**。
 * 所以这里用 `qrcode` 把它编码成 dataURL 再交给 `<img>`，绝不直接塞进 `src`（那样只会是裂图）。
 *
 * 二维码字符串由 VipView 抄进 ref 再传进来：对账每 3 秒会把订单对象换成 `/sync` 返回的那一份，
 * 而那份里 `payParams` 是 `null`，指着 `order.payParams` 渲染的码会自己消失。
 *
 * 定时器与对账**全部**在 `composables/useVipAlipayWait.ts` 里，这里不碰任何轮询逻辑，
 * 只负责把状态画出来（二维码、金额、倒计时、已查次数、三个动作）。
 *
 * 文案纪律：全程说明这是**沙箱测试环境、不会真实扣款**，也不把"后端查到支付"说成"钱到账"。
 */
import { computed, ref, watch } from 'vue'
import { toDataURL } from 'qrcode'
import { PhArrowClockwise, PhCheckCircle, PhDeviceMobile, PhQrCode, PhX } from '@phosphor-icons/vue'
import { ALIPAY_POLL_WINDOW_MS, type AlipayWaitPhase } from '@/composables/useVipAlipayWait'
import { moneyText, payStatusText } from '@/utils/vip'
import type { VipOrder } from '@/api/vip'

const props = defineProps<{
  order: VipOrder
  /** 后端返回的支付码字符串，不是图片地址 */
  qrCode: string
  /** 后端给的提示文案；为空时用兜底，界面不留白 */
  tip: string
  phase: AlipayWaitPhase
  notice: string
  error: string
  checks: number
  syncing: boolean
  countdownText: string
  /** 自动对账窗口的剩余秒数（进度条用它，别去解析 countdownText） */
  secondsLeft: number
  polling: boolean
}>()

const emit = defineEmits<{
  /** 我已支付，立即对账（也会把停下来的自动对账重新打开） */
  check: []
  /** 停止等待（订单仍然待支付） */
  stop: []
  /** 关掉面板 */
  dismiss: []
}>()

const DEFAULT_TIP = '用手机上的支付宝沙箱版扫这个码付款'

/** 画好的二维码（dataURL）；空串 = 还没画出来 */
const qrImage = ref('')
const qrError = ref('')
const rendering = ref(false)

/** 把支付码字符串编码成二维码图片。失败就说清楚，绝不假装有码 */
async function renderQr() {
  const code = props.qrCode.trim()
  if (!code) {
    qrImage.value = ''
    qrError.value = '后端这次没有返回支付码，画不出二维码。'
    return
  }
  rendering.value = true
  qrError.value = ''
  try {
    // 前景用设计令牌里的 ink（#0E1720）、底用纯白：二维码靠对比度才扫得动，
    // 所以这一小块必须留在白底上，不跟着页面与深色区走
    qrImage.value = await toDataURL(code, {
      errorCorrectionLevel: 'M',
      margin: 1,
      width: 320,
      color: { dark: '#0E1720', light: '#FFFFFF' },
    })
  } catch {
    qrImage.value = ''
    qrError.value = '这串支付码没能画成二维码，点「重新生成二维码」再试一次。'
  } finally {
    rendering.value = false
  }
}

// 换了支付码（重新下单）就重画；immediate 让面板一出现就有码
watch(() => props.qrCode, () => void renderQr(), { immediate: true })

/** 每个阶段一个徽标：等待=琥珀（时间语义）、成功=水青、失败=红。与跳收银台面板同一套 */
const badgeClass = computed(() => {
  if (props.phase === 'paid') return 'mw-badge--done'
  if (props.phase === 'failed') return 'mw-badge--danger'
  if (props.phase === 'waiting' || props.phase === 'timeout') return 'mw-badge--time'
  return 'badge--void'
})

const badgeText = computed(() => {
  if (props.phase === 'paid') return '已确认支付'
  if (props.phase === 'failed') return '对账没成功'
  if (props.phase === 'timeout') return '还没查到'
  if (props.phase === 'stopped') return '已停止对账'
  return '等待扫码'
})

const lead = computed(() => {
  if (props.phase === 'paid') return '支付结果已经由后端向支付宝核对过了。'
  if (props.phase === 'failed') return '对账没能完成，这笔订单还没有被确认。'
  if (props.phase === 'stopped' || props.phase === 'timeout') {
    return '二维码还在下面。扫完付了款就点「我已支付，立即对账」，后端会主动去支付宝查这一单。'
  }
  return '用手机上的支付宝沙箱版扫下面的二维码付款，付完留在这个页面就行，这里每 3 秒自动向后端对账一次，查到支付才发会员。'
})

/** 进度条：3 分钟对账窗口已经过去多少（琥珀只做填充，不做文字色） */
const progress = computed(() => {
  const total = ALIPAY_POLL_WINDOW_MS / 1000
  const used = total - Math.min(total, Math.max(0, props.secondsLeft))
  return (used / total) * 100
})

const tipText = computed(() => props.tip.trim() || DEFAULT_TIP)
</script>

<template>
  <article class="qr mw-card">
    <header class="qr__head">
      <div>
        <p class="qr__eyebrow">支付宝沙箱扫码支付</p>
        <h3 class="qr__title">{{ order.planName }}</h3>
      </div>
      <span class="mw-badge" :class="badgeClass">{{ badgeText }}</span>
    </header>

    <p class="qr__lead">{{ lead }}</p>

    <div class="qr__body">
      <!-- 二维码：白底版心，外面那圈细线只用来框住它 -->
      <div class="qr__plate">
        <img v-if="qrImage" class="qr__img" :src="qrImage" alt="支付宝沙箱扫码支付的二维码" />
        <span v-else-if="rendering" class="qr__skeleton mw-skeleton" />
        <div v-else class="qr__failed">
          <PhQrCode :size="26" aria-hidden="true" />
          <p>{{ qrError }}</p>
          <button class="mw-btn mw-btn--ghost qr__retry" type="button" @click="renderQr">
            <PhArrowClockwise :size="14" aria-hidden="true" />
            重新生成二维码
          </button>
        </div>

        <p class="qr__tip">
          <PhDeviceMobile :size="14" aria-hidden="true" />
          {{ tipText }}
        </p>
      </div>

      <div class="qr__side">
        <p class="qr__amount-label">实付金额</p>
        <p class="qr__amount mw-num">{{ moneyText(order.payableAmount) }}</p>

        <dl class="qr__meta">
          <div>
            <dt>档位</dt>
            <dd>{{ order.planName }}</dd>
          </div>
          <div>
            <dt>订单号</dt>
            <dd class="mw-num">{{ order.orderNo }}</dd>
          </div>
          <div>
            <dt>订单状态</dt>
            <dd>{{ payStatusText(order) }}</dd>
          </div>
          <div>
            <dt>渠道</dt>
            <dd>{{ order.payType }}（沙箱测试环境）</dd>
          </div>
          <div>
            <dt>已对账</dt>
            <dd class="mw-num">{{ checks }} 次</dd>
          </div>
          <div v-if="order.channelTradeNo">
            <dt>渠道单号</dt>
            <dd class="mw-num">{{ order.channelTradeNo }}</dd>
          </div>
        </dl>

        <div v-if="polling" class="qr__meter">
          <p class="qr__count mw-num">{{ countdownText }}</p>
          <div class="qr__track" role="presentation">
            <span class="qr__fill" :style="{ width: `${progress}%` }" />
          </div>
          <p class="qr__meter-note">
            这是自动对账的剩余时间，最多等 3 分钟，不代表二维码的有效期。期间随时可以自己点对账。
          </p>
        </div>
      </div>
    </div>

    <p v-if="error" class="mw-alert mw-alert--danger qr__msg">{{ error }}</p>
    <p v-else-if="notice" class="mw-alert mw-alert--info qr__msg">{{ notice }}</p>

    <div class="qr__actions">
      <button
        v-if="phase !== 'paid'"
        class="mw-btn mw-btn--primary qr__act"
        type="button"
        :disabled="syncing"
        @click="emit('check')"
      >
        <PhArrowClockwise :size="16" aria-hidden="true" />
        {{ syncing ? '对账中…' : '我已支付，立即对账' }}
      </button>
      <button
        v-else
        class="mw-btn mw-btn--primary qr__act"
        type="button"
        @click="emit('dismiss')"
      >
        <PhCheckCircle :size="16" weight="fill" aria-hidden="true" />
        知道了，去看订单
      </button>

      <button v-if="polling" class="mw-btn mw-btn--ghost qr__act" type="button" @click="emit('stop')">
        <PhX :size="16" aria-hidden="true" />
        停止等待
      </button>
      <button
        v-else-if="phase !== 'paid'"
        class="mw-btn mw-btn--ghost qr__act"
        type="button"
        @click="emit('dismiss')"
      >
        关闭提示
      </button>
    </div>

    <details v-if="qrCode" class="qr__raw">
      <summary class="qr__raw-summary">支付码字符串（排查用，它不是图片地址）</summary>
      <p class="qr__raw-code mw-num">{{ qrCode }}</p>
    </details>

    <p class="qr__note">
      支付宝沙箱是测试环境，不会真实扣款。这个码属于沙箱环境，要用手机上的支付宝沙箱版来扫。
      本地开发机收不到支付宝的异步回调，所以会员是否发放只认后端对账返回的订单状态，不认页面地址上的参数。
    </p>
  </article>
</template>

<style scoped>
.qr {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-4);
  margin-top: var(--mw-space-4);
  padding: var(--mw-space-6);
  border-left: 3px solid var(--mw-accent);
}
.qr__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--mw-space-4);
}
.qr__eyebrow {
  font-size: var(--mw-font-micro);
  letter-spacing: 0.04em;
  color: var(--mw-accent-ink);
}
.qr__title {
  font-size: var(--mw-font-h2);
  font-weight: 600;
  letter-spacing: -0.02em;
}
.qr__lead {
  max-width: 72ch;
  color: var(--mw-ink-2);
}
.qr__body {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--mw-space-6);
  align-items: start;
  padding-top: var(--mw-space-4);
  border-top: 1px solid var(--mw-line);
}
/* 二维码必须白底黑块：扫描靠的是对比度，这里不跟页面配色走 */
.qr__plate {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--mw-space-3);
  padding: var(--mw-space-4);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
  background: var(--mw-surface);
}
.qr__img {
  display: block;
  width: 240px;
  max-width: 100%;
  height: auto;
  image-rendering: pixelated;
}
.qr__skeleton {
  display: block;
  width: 240px;
  max-width: 100%;
  height: 240px;
  border-radius: var(--mw-radius-input);
}
.qr__failed {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--mw-space-2);
  width: 240px;
  max-width: 100%;
  padding: var(--mw-space-4) var(--mw-space-2);
  text-align: center;
  color: var(--mw-danger);
}
.qr__failed p {
  font-size: var(--mw-font-caption);
}
.qr__retry {
  min-height: 34px;
  padding: 0 var(--mw-space-3);
  font-size: var(--mw-font-micro);
}
.qr__tip {
  display: flex;
  align-items: center;
  gap: var(--mw-space-1);
  max-width: 24ch;
  text-align: center;
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
}
.qr__side {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-2);
}
.qr__amount-label {
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}
.qr__amount {
  font-size: 44px;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.05;
}
.qr__meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: var(--mw-space-3);
  margin-top: var(--mw-space-2);
  padding: var(--mw-space-3) 0;
  border-top: 1px solid var(--mw-line);
  border-bottom: 1px solid var(--mw-line);
}
.qr__meta dt {
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}
.qr__meta dd {
  font-size: var(--mw-font-caption);
  font-weight: 600;
  word-break: break-all;
}
.qr__meter {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-2);
  margin-top: var(--mw-space-2);
}
.qr__count {
  font-size: 40px;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1;
}
.qr__track {
  height: 6px;
  border-radius: var(--mw-radius-pill);
  background: var(--mw-canvas);
  overflow: hidden;
}
.qr__fill {
  display: block;
  height: 100%;
  background: var(--mw-accent);
  transition: width 1s linear;
}
.qr__meter-note {
  font-size: var(--mw-font-micro);
  color: var(--mw-ink-2);
}
.qr__msg {
  margin: 0;
}
.qr__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--mw-space-3);
}
.qr__act {
  min-height: 40px;
  padding: 0 var(--mw-space-4);
  font-size: var(--mw-font-caption);
}
.qr__raw {
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}
.qr__raw-summary {
  cursor: pointer;
}
.qr__raw-code {
  margin-top: var(--mw-space-2);
  word-break: break-all;
  color: var(--mw-ink-2);
}
.qr__note {
  max-width: 76ch;
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}

@media (max-width: 720px) {
  .qr__body {
    grid-template-columns: minmax(0, 1fr);
  }
  .qr__plate {
    align-items: center;
  }
}
</style>