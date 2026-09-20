<script setup lang="ts">
/**
 * 支付宝沙箱「等待支付」面板（纯展示）
 *
 * 状态与定时器都在 `composables/useVipAlipayWait.ts` 里，这里只负责把它画出来：
 * 倒计时、已经对账几次、每个阶段该说什么话、以及三个动作（立即对账 / 停止等待 / 关闭）。
 *
 * 文案纪律：支付宝这条路径是**沙箱**，任何时候都不说"钱到账了"，
 * 只说"后端已经从支付宝查到这笔支付，会员发放完成"，并带上"测试环境，没有真实扣款"。
 */
import { computed } from 'vue'
import { PhArrowClockwise, PhArrowSquareOut, PhCheckCircle, PhX } from '@phosphor-icons/vue'
import { ALIPAY_POLL_WINDOW_MS, type AlipayWaitPhase } from '@/composables/useVipAlipayWait'
import { moneyText, payStatusText } from '@/utils/vip'
import type { VipOrder } from '@/api/vip'

const props = defineProps<{
  order: VipOrder
  phase: AlipayWaitPhase
  /** 旧的 GET 链接（旧沙箱可用；新沙箱打开会 404）。**只在没有 payHtml 时才展示成链接** */
  payUrl: string
  /**
   * 收银台**表单 HTML**（新沙箱的正路，由 VipView 写进新窗口自动提交）。
   *
   * 这里只拿它判断"有没有表单"和写按钮文案，**绝不渲染**（太长、也不该出现在页面上）；
   * 真正的 `window.open` + `document.write` 在 VipView 里做（那里才拿得到 `order` 与提示位）。
   */
  payHtml: string
  notice: string
  error: string
  checks: number
  syncing: boolean
  countdownText: string
  /** 自动对账窗口的剩余秒数（进度条用它，别去解析 countdownText） */
  secondsLeft: number
  polling: boolean
  /** 收银台是否真的被 `window.open` 打开了；没打开时文案不能写"已经打开了" */
  opened: boolean
}>()

const emit = defineEmits<{
  /** 我已支付，立即对账（也会把停下来的自动对账重新打开） */
  check: []
  /**
   * 请再打开一次收银台。由 VipView 处理（它才有表单 HTML）。
   * 按钮点击本身是**用户手势**，所以这一次的 `window.open` 不会被弹窗拦截。
   */
  open: []
  /** 停止等待（订单仍然待支付） */
  stop: []
  /** 关掉面板 */
  dismiss: []
}>()

/** 每个阶段一个徽标：等待=琥珀（时间语义）、成功=水青、失败=红 */
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
  return '等待支付'
})

const lead = computed(() => {
  if (props.phase === 'paid') return '支付结果已经由后端向支付宝核对过了。'
  if (props.phase === 'failed') return '对账没能完成，这笔订单还没有被确认。'
  if (!props.opened) {
    return props.payHtml
      ? '沙箱收银台没能自动打开，多半是浏览器拦了弹窗。点下面的「打开沙箱收银台」按钮就行 —— 点击本身是用户手势，不会再被拦。'
      : '沙箱收银台没能自动打开，多半被浏览器拦了。点下面的链接打开它付款，付完回到本页会自动对账。'
  }
  if (props.phase === 'stopped' || props.phase === 'timeout') {
    return '支付宝沙箱收银台已经在另一个标签页打开了。付完款回到这里，点「立即对账」就能查到。'
  }
  return '支付宝沙箱收银台已经在另一个标签页打开了。付完款回到这个页面就行，这里会自动向后端对账，查到支付才发会员。'
})

/** 进度条：3 分钟窗口已经过去多少（琥珀只做填充，不做文字色） */
const progress = computed(() => {
  const total = ALIPAY_POLL_WINDOW_MS / 1000
  const used = total - Math.min(total, Math.max(0, props.secondsLeft))
  return (used / total) * 100
})
</script>

<template>
  <article class="wait mw-card">
    <header class="wait__head">
      <div>
        <p class="wait__eyebrow">支付宝沙箱支付</p>
        <h3 class="wait__title">{{ order.planName }}</h3>
      </div>
      <span class="mw-badge" :class="badgeClass">{{ badgeText }}</span>
    </header>

    <p class="wait__lead">{{ lead }}</p>

    <dl class="wait__meta">
      <div>
        <dt>订单号</dt>
        <dd class="mw-num">{{ order.orderNo }}</dd>
      </div>
      <div>
        <dt>实付</dt>
        <dd class="mw-num">{{ moneyText(order.payableAmount) }}</dd>
      </div>
      <div>
        <dt>订单状态</dt>
        <dd>{{ payStatusText(order) }}</dd>
      </div>
      <div>
        <dt>渠道</dt>
        <dd>{{ order.payType }}（沙箱测试环境）</dd>
      </div>
      <div v-if="order.channelTradeNo">
        <dt>渠道单号</dt>
        <dd class="mw-num">{{ order.channelTradeNo }}</dd>
      </div>
    </dl>

    <div v-if="polling" class="wait__meter">
      <p class="wait__count mw-num">{{ countdownText }}</p>
      <div class="wait__track" role="presentation">
        <span class="wait__fill" :style="{ width: `${progress}%` }" />
      </div>
      <p class="wait__meter-note">
        每 3 秒自动对账一次，已经查了 {{ checks }} 次。最多等 3 分钟，期间随时可以自己点对账。
      </p>
    </div>

    <p v-if="error" class="mw-alert mw-alert--danger wait__msg">{{ error }}</p>
    <p v-else-if="notice" class="mw-alert mw-alert--info wait__msg">{{ notice }}</p>

    <div class="wait__actions">
      <button
        v-if="phase !== 'paid'"
        class="mw-btn mw-btn--primary wait__act"
        type="button"
        :disabled="syncing"
        @click="emit('check')"
      >
        <PhArrowClockwise :size="16" aria-hidden="true" />
        {{ syncing ? '对账中…' : '我已支付，立即对账' }}
      </button>
      <button
        v-else
        class="mw-btn mw-btn--primary wait__act"
        type="button"
        @click="emit('dismiss')"
      >
        <PhCheckCircle :size="16" weight="fill" aria-hidden="true" />
        知道了，去看订单
      </button>

      <button
        v-if="polling"
        class="mw-btn mw-btn--ghost wait__act"
        type="button"
        @click="emit('stop')"
      >
        <PhX :size="16" aria-hidden="true" />
        停止等待
      </button>
      <button
        v-else-if="phase !== 'paid'"
        class="mw-btn mw-btn--ghost wait__act"
        type="button"
        @click="emit('dismiss')"
      >
        关闭提示
      </button>

      <!-- 有收银台表单（新沙箱正路）时给一个按钮：点击是用户手势，window.open 不会再被拦。
           绝不给指向 payUrl 的链接 —— 新沙箱的网关不渲染收银台页面，那条 GET 链接会 404 -->
      <button
        v-if="payHtml"
        class="mw-btn mw-btn--ghost wait__act"
        type="button"
        @click="emit('open')"
      >
        <PhArrowSquareOut :size="16" aria-hidden="true" />
        {{ opened ? '再打开一次沙箱收银台' : '打开沙箱收银台' }}
      </button>
      <!-- 没有表单时才是旧的 GET 链接（旧沙箱可用），老路径一字不动 -->
      <a
        v-else-if="payUrl"
        class="wait__link"
        :href="payUrl"
        target="_blank"
        rel="noopener noreferrer"
      >
        <PhArrowSquareOut :size="14" aria-hidden="true" />
        支付页没自动打开？点这里打开沙箱收银台
      </a>
    </div>

    <p class="wait__note">
      支付宝沙箱是测试环境，不会真实扣款。收银台是这个页面新开一个标签页、把后端现签的支付表单
      自动提交给支付宝网关打开的（沙箱网关只接受 POST，直接打开 GET 链接会 404）。
      本地开发机收不到支付宝的异步回调，所以会员是否发放只认后端对账返回的订单状态，
      不认页面地址上的参数。
    </p>
  </article>
</template>

<style scoped>
.wait {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-4);
  margin-top: var(--mw-space-4);
  padding: var(--mw-space-6);
  border-left: 3px solid var(--mw-accent);
}
.wait__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--mw-space-4);
}
.wait__eyebrow {
  font-size: var(--mw-font-micro);
  letter-spacing: 0.04em;
  color: var(--mw-accent-ink);
}
.wait__title {
  font-size: var(--mw-font-h2);
  font-weight: 600;
  letter-spacing: -0.02em;
}
.wait__lead {
  max-width: 72ch;
  color: var(--mw-ink-2);
}
.wait__meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: var(--mw-space-3);
  border-top: 1px solid var(--mw-line);
  border-bottom: 1px solid var(--mw-line);
  padding: var(--mw-space-3) 0;
}
.wait__meta dt {
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}
.wait__meta dd {
  font-size: var(--mw-font-caption);
  font-weight: 600;
  word-break: break-all;
}
.wait__meter {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-2);
}
.wait__count {
  font-size: 40px;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1;
}
.wait__track {
  height: 6px;
  border-radius: var(--mw-radius-pill);
  background: var(--mw-canvas);
  overflow: hidden;
}
.wait__fill {
  display: block;
  height: 100%;
  background: var(--mw-accent);
  transition: width 1s linear;
}
.wait__meter-note {
  font-size: var(--mw-font-micro);
  color: var(--mw-ink-2);
}
.wait__msg {
  margin: 0;
}
.wait__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--mw-space-3);
}
.wait__act {
  min-height: 40px;
  padding: 0 var(--mw-space-4);
  font-size: var(--mw-font-caption);
}
.wait__link {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-1);
  font-size: var(--mw-font-caption);
  color: var(--mw-brand-ink);
  text-decoration: none;
}
.wait__link:hover {
  text-decoration: underline;
}
.wait__note {
  max-width: 76ch;
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}
</style>