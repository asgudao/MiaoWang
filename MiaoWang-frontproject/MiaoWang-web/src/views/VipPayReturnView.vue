<script setup lang="ts">
/**
 * 支付宝沙箱支付结果页（`/vip/pay/return`）
 *
 * 支付宝付完款会按 `return_url` 跳回这里，地址上带着 `out_trade_no` 等参数。
 *
 * **地址参数不可信**：`return_url` 是浏览器地址，谁都能手拼一个 `out_trade_no` 出来，
 * 而且不管用户到底付没付，支付宝都会把人送回这个地址。所以这里只做两件事：
 * 1) 从 query 里取订单号（仅为"查哪一笔"）
 * 2) 调 `POST /vip/order/{orderNo}/sync` 让**后端**去支付宝查单，界面一律按返回值渲染
 *
 * 因此本页只会说"后端已经向支付宝核对过，这笔支付已确认"，不会说"钱到账了"，
 * 并且全程标明是沙箱测试环境。
 */
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  PhArrowClockwise,
  PhArrowLeft,
  PhCheckCircle,
  PhClock,
  PhReceipt,
  PhWarningCircle,
} from '@phosphor-icons/vue'
import { PAY_CLOSED, PAY_PAID, PAY_PENDING, PAY_REFUNDED, syncOrder, type VipOrder } from '@/api/vip'
import { channelText, dateTimeText, moneyText, payStatusText, vipPayErrorMessage } from '@/utils/vip'

const route = useRoute()

/** 支付宝回跳用的是 `out_trade_no`；同时兼容驼峰写法（后端拼 return_url 时可能用上） */
const orderNo = computed(() => {
  const raw = route.query.out_trade_no ?? route.query.outTradeNo
  const value = Array.isArray(raw) ? (raw[0] ?? '') : (raw ?? '')
  return String(value).trim()
})

type Phase = 'missing' | 'syncing' | 'result' | 'error'

const phase = ref<Phase>('syncing')
const order = ref<VipOrder | null>(null)
const error = ref('')

/** 结果分档：已确认支付 / 仍未支付 / 已退款 / 已关闭 */
const outcome = computed(() => {
  const o = order.value
  if (!o) return 'unknown'
  if (o.payStatus === PAY_PAID) return 'paid'
  if (o.payStatus === PAY_PENDING) return 'pending'
  if (o.payStatus === PAY_REFUNDED) return 'refunded'
  if (o.payStatus === PAY_CLOSED) return 'closed'
  return 'unknown'
})

const headline = computed(() => {
  if (phase.value === 'missing') return '没拿到订单号'
  if (phase.value === 'error') return '支付结果没能确认'
  if (phase.value === 'syncing') return '正在向支付宝核对'
  if (outcome.value === 'paid') return '这笔支付已经确认'
  if (outcome.value === 'pending') return '还不能确认这笔支付'
  if (outcome.value === 'refunded') return '这笔订单已退款'
  if (outcome.value === 'closed') return '这笔订单已关闭'
  return '订单状态未知'
})

const badgeClass = computed(() => {
  if (phase.value === 'error') return 'mw-badge--danger'
  if (outcome.value === 'paid') return 'mw-badge--done'
  if (outcome.value === 'pending') return 'mw-badge--time'
  return 'badge--void'
})

const badgeText = computed(() => {
  if (phase.value === 'missing') return '缺少参数'
  if (phase.value === 'error') return '核对失败'
  if (phase.value === 'syncing') return '核对中'
  return order.value ? payStatusText(order.value) : '未知状态'
})

/**
 * 对账（也用在"立即再对账"按钮上）。
 * 结果一律以后端返回的 `payStatus` 为准：只有 `1` 才算支付被确认，其余都如实说不确定。
 */
async function runSync() {
  const no = orderNo.value
  if (!no) {
    phase.value = 'missing'
    return
  }
  phase.value = 'syncing'
  error.value = ''
  try {
    order.value = await syncOrder(no)
    phase.value = 'result'
  } catch (e) {
    error.value = vipPayErrorMessage(e)
    phase.value = 'error'
  }
}

onMounted(() => {
  void runSync()
})
</script>

<template>
  <div class="mw-container mw-page">
    <RouterLink class="back" :to="{ name: 'vip' }">
      <PhArrowLeft :size="16" aria-hidden="true" />
      返回会员页
    </RouterLink>

    <section class="result mw-card" :class="`result--${phase === 'result' ? outcome : phase}`">
      <header class="result__head">
        <div>
          <p class="result__eyebrow">支付宝沙箱支付结果</p>
          <h1 class="result__title">{{ headline }}</h1>
        </div>
        <span class="mw-badge" :class="badgeClass">{{ badgeText }}</span>
      </header>

      <p v-if="phase === 'syncing'" class="result__loading mw-skeleton" />

      <template v-else-if="phase === 'missing'">
        <p class="result__lead">
          这个地址上没有订单号（支付宝回跳时带的 out_trade_no），所以不知道要核对哪一笔支付。
          可以直接回会员页看订单列表，那里能看到每笔订单的真实状态。
        </p>
      </template>

      <template v-else-if="phase === 'error'">
        <p class="mw-alert mw-alert--danger result__alert">{{ error }}</p>
        <p class="result__lead">
          支付结果没有确认下来，这笔订单现在还按「待支付」处理，没有发放会员。
          可以点下面的「立即再对账」重试，或者回会员页稍后再看。
        </p>
      </template>

      <template v-else-if="order">
        <p class="result__lead">
          <template v-if="outcome === 'paid'">
            后端已经主动向支付宝查过这笔订单，确认支付完成，{{ order.planName }}的会员时长已经发放。
            这是支付宝沙箱，测试环境，没有真实扣款。
          </template>
          <template v-else-if="outcome === 'pending'">
            后端向支付宝查过了，还没查到这笔支付。如果你刚刚付完款，稍等几秒再点一次「立即再对账」；
            支付宝沙箱偶尔会慢一点，没查到就不会发放会员。
          </template>
          <template v-else-if="outcome === 'refunded'">
            这笔订单已经退过款，会员时长按订单快照回收了。
          </template>
          <template v-else-if="outcome === 'closed'">
            这笔订单已经关闭（待支付超过 30 分钟会自动关闭）。需要会员的话回会员页重新下一单。
          </template>
          <template v-else>
            后端返回的订单状态是「{{ payStatusText(order) }}」，这个状态本页没有更细的说明。
          </template>
        </p>

        <dl class="result__meta">
          <div>
            <dt>订单号</dt>
            <dd class="mw-num">{{ order.orderNo }}</dd>
          </div>
          <div>
            <dt>档位</dt>
            <dd>{{ order.planName }}</dd>
          </div>
          <div>
            <dt>实付</dt>
            <dd class="mw-num">{{ moneyText(order.payableAmount) }}</dd>
          </div>
          <div>
            <dt>支付渠道</dt>
            <dd>{{ channelText(order.payType) }}（测试环境）</dd>
          </div>
          <div>
            <dt>下单时间</dt>
            <dd>{{ dateTimeText(order.createTime) }}</dd>
          </div>
          <div>
            <dt>支付时间</dt>
            <dd>{{ dateTimeText(order.payTime) }}</dd>
          </div>
          <div>
            <dt>会员到期</dt>
            <dd>{{ dateTimeText(order.expireTime) }}</dd>
          </div>
          <div>
            <dt>订单状态</dt>
            <dd>{{ payStatusText(order) }}</dd>
          </div>
          <div v-if="order.channelTradeNo">
            <dt>渠道单号</dt>
            <dd class="mw-num">{{ order.channelTradeNo }}</dd>
          </div>
        </dl>
      </template>

      <div class="result__actions">
        <button
          v-if="phase !== 'missing'"
          class="mw-btn mw-btn--primary result__act"
          type="button"
          :disabled="phase === 'syncing'"
          @click="runSync"
        >
          <PhArrowClockwise :size="16" aria-hidden="true" />
          {{ phase === 'syncing' ? '对账中…' : '立即再对账' }}
        </button>

        <RouterLink
          class="mw-btn result__act"
          :class="phase === 'missing' ? 'mw-btn--primary' : 'mw-btn--ghost'"
          :to="{ name: 'vip' }"
        >
          <PhReceipt :size="16" aria-hidden="true" />
          回会员页看订单
        </RouterLink>
      </div>
    </section>

    <section class="notes">
      <h2 class="mw-h2">这一页为什么不能直接说"支付成功"</h2>
      <p>
        跳回本页的地址是浏览器地址，谁都能手拼一个订单号出来，而且不管有没有真的付款，
        支付宝都会把人送回这个地址。所以本页只把地址上的订单号当成"要查哪一笔"，
        真正的结果一律以后端对账接口的返回为准。
      </p>
      <p>
        本地开发机收不到支付宝的异步回调，所以会员是靠"付款后回站内、由后端主动去支付宝查单"发下来的，
        这也是真实支付里的标准做法：查询接口才是权威来源，回调只是加速。
      </p>
      <p class="notes__hint">
        <PhClock :size="14" aria-hidden="true" />
        沙箱是测试环境，不会真实扣款。
      </p>
      <p v-if="!orderNo" class="notes__hint">
        <PhWarningCircle :size="14" aria-hidden="true" />
        这次跳转没有带订单号，所以没有发起对账。
      </p>
      <p v-else-if="outcome === 'paid'" class="notes__hint">
        <PhCheckCircle :size="14" aria-hidden="true" />
        对账已完成，回会员页就能看到最新的会员到期时间。
      </p>
    </section>
  </div>
</template>

<style scoped>
.back {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-1);
  margin-bottom: var(--mw-space-4);
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
  text-decoration: none;
}
.back:hover {
  color: var(--mw-brand-ink);
}
.result {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-4);
  padding: var(--mw-space-6);
  border-left: 3px solid var(--mw-line);
}
/* 左边那道细条只用来说明"现在的状态属于哪一类"，不动文字颜色 */
.result--paid {
  border-left-color: var(--mw-success);
}
.result--pending,
.result--syncing {
  border-left-color: var(--mw-accent);
}
.result--error {
  border-left-color: var(--mw-danger);
}
.result__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--mw-space-4);
}
.result__eyebrow {
  font-size: var(--mw-font-micro);
  letter-spacing: 0.04em;
  color: var(--mw-ink-2);
}
.result__title {
  margin-top: var(--mw-space-1);
  font-size: var(--mw-font-h1);
  font-weight: 700;
  letter-spacing: -0.02em;
}
.result__loading {
  display: block;
  height: 96px;
}
.result__lead {
  max-width: 74ch;
  color: var(--mw-ink-2);
}
.result__alert {
  margin: 0;
}
.result__meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--mw-space-3);
  padding: var(--mw-space-4) 0;
  border-top: 1px solid var(--mw-line);
  border-bottom: 1px solid var(--mw-line);
}
.result__meta dt {
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}
.result__meta dd {
  font-size: var(--mw-font-caption);
  font-weight: 600;
  word-break: break-all;
}
.result__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--mw-space-3);
}
.result__act {
  min-height: 40px;
  padding: 0 var(--mw-space-4);
  font-size: var(--mw-font-caption);
}
.notes {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-2);
  max-width: 82ch;
  margin-top: var(--mw-space-8);
}
.notes p {
  color: var(--mw-ink-2);
}
.notes__hint {
  display: flex;
  align-items: center;
  gap: var(--mw-space-1);
  font-size: var(--mw-font-caption);
  color: var(--mw-muted);
}
</style>