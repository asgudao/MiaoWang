<template>
	<view class="page">
		<!-- 全局 navigationStyle 是 custom，非 tab 页要自绘返回栏（照 pages/ai/ai.vue） -->
		<view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav__back" @click="goBack">
				<view class="chev chev--left"></view>
			</view>
			<text class="nav__title">喵汪会员</text>
			<text class="nav__refresh" @click="refresh">刷新</text>
		</view>

		<view class="body">
			<!-- ---------- 会员状态（深色区，首页"夜幕舞台"同族） ---------- -->
			<view class="hero">
				<text class="hero__eyebrow">我的会员状态</text>
				<text class="hero__title">{{ statusTitle }}</text>
				<text class="hero__sub">{{ statusSub }}</text>
				<!-- 拿不到会员状态时不留死路：这里给一个可点的重试 -->
				<view v-if="benefitFailed" class="hero__btn" @click="refresh"><text>重新读取会员状态</text></view>
				<view v-if="balanceRows.length" class="balance">
					<view v-for="b in balanceRows" :key="b.label" class="balance__row">
						<text class="balance__label">{{ b.label }}</text>
						<text class="balance__value">{{ b.value }}</text>
					</view>
				</view>
				<view v-if="!loggedIn || sessionExpired" class="hero__btn" @click="goLogin"><text>去登录</text></view>
			</view>

			<!-- ---------- 档位 ---------- -->
			<view class="sec">
				<text class="sec__title">可选档位</text>
				<text class="sec__note">价格与权益全部来自接口，改配置不用改前端</text>
			</view>

			<view v-if="!loggedIn || sessionExpired" class="soft">
				<text class="soft__title">{{ sessionExpired ? '登录已过期' : '先登录再看档位' }}</text>
				<text class="soft__desc">{{ sessionExpired ? '重新登录后就能开通档位、查订单、用兑换码。' : '档位接口需要登录身份，登录后就能开通、查订单、用兑换码。' }}</text>
				<view class="soft__btn" @click="goLogin"><text>去登录</text></view>
			</view>

			<template v-else>
				<view v-if="plansLoading" class="skeleton">
					<view v-for="n in 3" :key="n" class="skeleton__card"></view>
				</view>
				<view v-else-if="plansError" class="err">
					<text class="err__text">{{ plansError }}</text>
					<!-- 失败时旧的档位数据还在（store 只在成功时覆盖）：下面若还有卡片，说清那是旧数据 -->
					<text class="err__hint">{{ plans.length ? '档位刷新失败，下面还是上次读到的内容' : '档位没读到，点下面重试；若一直失败，先确认后端网关是否已启动' }}</text>
					<view class="act" @click="refresh"><text>重试</text></view>
				</view>
				<view v-else-if="!plans.length" class="empty">
					<text class="empty__text">暂时没有可购买的档位</text>
					<view class="act" @click="refresh"><text>重新读取</text></view>
				</view>

				<view
					v-for="p in plans"
					:key="p.planCode"
					class="plan"
					:class="p.selected ? 'plan--on' : ''"
					@click="selectPlan(p.planCode)"
				>
					<view class="plan__head">
						<view class="plan__names">
							<text class="plan__name">{{ p.planName }}</text>
							<text class="plan__code">{{ p.planCode }}</text>
						</view>
						<view class="plan__price">
							<text class="plan__cur">¥</text>
							<text class="plan__num">{{ p.price }}</text>
							<text class="plan__per">{{ p.per }}</text>
						</view>
					</view>
					<text v-if="p.tagline" class="plan__tagline">{{ p.tagline }}</text>
					<view class="gains">
						<view v-for="g in p.benefits" :key="g.label" class="gain">
							<text class="gain__label">{{ g.label }}</text>
							<text class="gain__value">{{ g.value }}</text>
						</view>
					</view>

					<!-- 开通：行内两步确认（工程规矩：不用原生 confirm / uni.showModal） -->
					<template v-if="subStep === p.planCode">
						<view class="confirm">
							<text class="confirm__line">开通 {{ p.planName }}，实付 ¥{{ p.price }}</text>
							<input
								class="confirm__input"
								v-model="promoInput"
								placeholder="有立减码就填在这里（可不填）"
								placeholder-class="promo__ph"
								maxlength="32"
							/>
							<text class="confirm__memo">{{ mockMemo }}</text>
							<view class="confirm__acts">
								<view class="mini mini--ghost" @click="cancelSub"><text>再想想</text></view>
								<view class="mini mini--primary" @click="confirmSub(p.planCode)"><text>{{ submitting ? '处理中' : '确认开通' }}</text></view>
							</view>
						</view>
					</template>
					<view v-else class="plan__cta" @click="askSub(p.planCode)">
						<text>{{ isCurrentPlan(p.planCode) ? '续费这个档位' : '开通这个档位' }}</text>
					</view>
				</view>
			</template>

			<!-- ---------- 兑换码 ---------- -->
			<view class="sec">
				<text class="sec__title">兑换码</text>
				<text class="sec__note">送会员时长的兑换码，核销后直接到账</text>
			</view>
			<view class="promo">
				<input
					class="promo__input"
					v-model="promoCode"
					:disabled="!loggedIn || sessionExpired"
					placeholder="输入兑换码"
					placeholder-class="promo__ph"
					maxlength="32"
				/>
				<view class="promo__btn" :class="loggedIn && !sessionExpired ? '' : 'promo__btn--off'" @click="confirmRedeem">
					<text>{{ redeeming ? '兑换中' : '兑换' }}</text>
				</view>
			</view>
			<text v-if="promoMsg" class="promo__msg">{{ promoMsg }}</text>

			<!-- ---------- 我的订单 ---------- -->
			<view class="sec">
				<text class="sec__title">我的订单</text>
				<text class="sec__note">待支付的单 30 分钟后会自动关闭，已支付的单 7 天内可以自助退款</text>
			</view>

			<text v-if="!loggedIn || sessionExpired" class="empty">{{ sessionExpired ? '重新登录后这里会显示你的开通记录' : '登录后这里会显示你的开通记录' }}</text>
			<template v-else>
				<text v-if="ordersLoading" class="hint">正在读取订单</text>
				<view v-else-if="ordersError" class="err">
					<text class="err__text">{{ ordersError }}</text>
					<text class="err__hint">{{ orders.length ? '订单刷新失败，下面还是上次读到的内容' : '订单没读到，不代表没有订单，点下面重试' }}</text>
					<view class="act" @click="refresh"><text>重试</text></view>
				</view>
				<text v-else-if="!orders.length" class="empty">还没有开通记录，选一个档位试试</text>

				<view v-for="o in orders" :key="o.orderNo" class="order">
					<view class="order__head">
						<text class="order__name">{{ o.planName }}</text>
						<text class="order__status" :class="o.statusClass">{{ o.statusText }}</text>
					</view>
					<view class="order__money">
						<text class="order__pay">实付 ¥{{ o.payable }}</text>
						<text v-if="o.discountText" class="order__discount">{{ o.discountText }}</text>
						<text v-if="o.showOrigin" class="order__origin">原价 ¥{{ o.amount }}</text>
					</view>
					<view class="order__meta">
						<text class="order__m">{{ o.createdText }}下单</text>
						<text v-if="o.durationText" class="order__m">{{ o.durationText }}</text>
						<text v-if="o.expireText" class="order__m">{{ o.expireText }}到期</text>
					</view>
					<text class="order__no">{{ o.orderNo }}</text>
					<text v-if="o.refundReason" class="order__reason">退款原因：{{ o.refundReason }}</text>

					<view v-if="o.isPending" class="order__acts">
						<view class="mini mini--ghost" @click="cancelOrder"><text>取消</text></view>
						<view class="mini mini--primary" @click="payOrder(o.orderNo)"><text>{{ submitting ? '处理中' : '去支付' }}</text></view>
					</view>
					<template v-else-if="o.canRefund">
						<template v-if="refundNo === o.orderNo">
							<input
								class="order__reason-input"
								v-model="refundReason"
								placeholder="写个退款原因（可不填）"
								placeholder-class="promo__ph"
								maxlength="60"
							/>
							<view class="order__acts">
								<view class="mini mini--ghost" @click="cancelRefund"><text>先不退</text></view>
								<view class="mini mini--danger" @click="doRefund(o.orderNo)"><text>{{ submitting ? '处理中' : '确认退款' }}</text></view>
							</view>
						</template>
						<view v-else class="order__acts">
							<view class="mini mini--ghost" @click="askRefund(o.orderNo)"><text>退款</text></view>
						</view>
					</template>
					<text v-else-if="o.refundExpired" class="order__note">已超过 7 天自助退款期限</text>
					<text v-else-if="o.isFree" class="order__note">促销兑换的 0 元订单不支持退款</text>
				</view>
			</template>

			<text class="foot">{{ footNote }}</text>
		</view>
	</view>
</template>

<script>
/**
 * 会员页（手机端 / Options API）
 *
 * 模板只访问实例属性（data/computed/methods）—— store 与 utils 一律不进模板，
 * 档位卡、订单卡、状态文案全在 computed 里摊平成显示字段（白屏事故的根治办法，见 miaowang-phone-frontend）。
 *
 * 诚实标注：渠道是 **mock**，支付按钮旁一律写"模拟支付（渠道为 mock）"，不假装是真支付。
 * 权益数值与档位数量**一律取接口**（utils/vip.js 里一个数字都没写死）。
 *
 * ⚠️ 实测：`GET /vip/plan` 经网关**要 token**（白名单里没有它），所以未登录时本页是软门槛，
 * 只提示去登录、不硬调接口。
 */
import { vip } from '../../store/vip.js'
import { auth } from '../../store/auth.js'
import { ApiError } from '../../utils/request.js'
import {
	mockTip,
	orderCard,
	planCard,
	vipErrorMessage
} from '../../utils/vip.js'

export default {
	data () {
		return {
			statusBarHeight: 0,
			/** 正在两步确认的档位编码：'' 表示没有 */
			subStep: '',
			/** 正在两步确认退款的订单号 */
			refundNo: '',
			refundReason: '',
			/** 开通时的立减码（可不填；只有填了才走 createOrder 那两步） */
			promoInput: '',
			promoCode: '',
			promoMsg: '',
			/** 上一次操作的结果提示（成功后给一句人话，不弹原生弹窗） */
			flash: ''
		}
	},

	computed: {
		loggedIn () {
			return auth.isLoggedIn()
		},
		/**
		 * 会话真过期了（后端回 401 / B000204 这类码）：本地还有 token 但已经不好使。
		 * 这时不要摆三个红色错误框，直接把页面回到软门槛并给"去登录"。
		 */
		sessionExpired () {
			return auth.isLoggedIn() && vip.unauthorized
		},
		/** 会员状态没读到（且不是掉线）：hero 里给一个重试，不留死路 */
		benefitFailed () {
			return auth.isLoggedIn() && !vip.unauthorized && Boolean(vip.benefitError)
		},
		plans () {
			return vip.plans.map(function (p) { return planCard(p, vip.selected) })
		},
		plansLoading () {
			return vip.plansLoading
		},
		plansError () {
			return vip.plansError
		},
		orders () {
			return vip.orders.map(function (o) { return orderCard(o) })
		},
		ordersLoading () {
			return vip.ordersLoading
		},
		ordersError () {
			return vip.ordersError
		},
		submitting () {
			return Boolean(vip.submitting)
		},
		redeeming () {
			return vip.submitting === 'redeem'
		},
		/** 会员状态标题：会员 / 未开通 / 拿不到 / 掉线 */
		statusTitle () {
			if (!this.loggedIn) return '还没登录'
			if (this.sessionExpired) return '登录已过期'
			if (vip.benefitError) return '会员状态没读到'
			const b = vip.benefit
			if (!b) return '正在读取会员状态'
			return b.vip ? '会员生效中' : '还没有开通会员'
		},
		/** 会员状态副标题：生效档位 + 到期时间，或一句下一步 */
		statusSub () {
			if (!this.loggedIn) return '登录后才能开通会员、查看订单'
			if (this.sessionExpired) return '重新登录后就能看到会员状态与订单'
			if (vip.benefitError) return vip.benefitError
			const b = vip.benefit
			if (!b) return ''
			const names = (b.planNames || []).join('、')
			if (b.vip) {
				const exp = this.expireText
				if (names && exp) return names + '，' + exp + ' 到期'
				if (names) return names
				return '权益已生效'
			}
			return '免费档的权益有限，开通后 AI 问答次数与宠物位都会放开'
		},
		/** 到期时间：后端可能返回 null，不编 */
		expireText () {
			const b = vip.benefit
			if (!b || !b.expireTime) return ''
			const d = new Date(b.expireTime)
			if (isNaN(d.getTime())) return ''
			return d.getFullYear() + ' 年 ' + (d.getMonth() + 1) + ' 月 ' + d.getDate() + ' 日'
		},
		/** 当前额度小队（这一组是"今天还剩多少"，不是档位表） */
		balanceRows () {
			const b = vip.benefit
			if (!b) return []
			const rows = [{ label: 'AI 问答', value: this.quotaText(b.ragRemaining, b.ragLimit, b.ragPeriodDesc) }]
			if (b.chatRemaining !== undefined && b.chatRemaining !== null) {
				rows.push({ label: 'AI 聊天', value: this.quotaText(b.chatRemaining, b.chatLimit, b.chatPeriodDesc) })
			}
			if (b.petLimitPerType !== undefined && b.petLimitPerType !== null) {
				rows.push({ label: '可养宠物', value: Number(b.petLimitPerType) < 0 ? '不限只数' : '共 ' + b.petLimitPerType + ' 只' })
			}
			return rows
		},
		/** 开通前的诚实说明：渠道是 mock */
		mockMemo () {
			return '模拟支付（渠道为 mock）：演示环境不会真的扣款，点确认后直接按支付成功的链路发放会员'
		},
		/** 底部声明 */
		footNote () {
			if (this.flash) return this.flash
			return '支付渠道目前是 mock（模拟支付），接入真实微信或支付宝后流程不变、权益口径也不变。权益数值以接口返回为准。'
		}
	},

	onLoad () {
		try {
			const info = uni.getSystemInfoSync()
			this.statusBarHeight = info.statusBarHeight || 0
		} catch (e) {
			this.statusBarHeight = 0
		}
		this.load()
	},

	onShow () {
		// 从登录页返回后要立刻拿到档位与权益
		if (this.loggedIn && !vip.plans.length) this.load()
	},

	methods: {
		load () {
			if (!this.loggedIn) return
			vip.refresh()
		},

		/**
		 * "还剩 x 次"；-1 一律显示"不限次数"，绝不出现 "-1 次"。
		 *
		 * ⚠️ 必须放在 methods 里，**不能放 computed**：页面要按参数调用它（`this.quotaText(a, b, c)`），
		 * 而 Vue3 的 computed 取到的是**值本身**（这里是字符串），再调用就是
		 * `TypeError: this.quotaText is not a function`。这条 TypeError 发生在渲染读到的 computed 里，
		 * 会让整棵子树被替换成注释节点 → **整页白屏**（2026-09-18 手机端就是踩了这个）。
		 * tools/check-phone.js 第 5 类检查专门拦这种写法。
		 */
		quotaText (remaining, limit, desc) {
			const r = Number(remaining)
			const l = Number(limit)
			if (remaining === undefined || remaining === null || isNaN(r)) return '不提供'
			if (r < 0) return '不限次数'
			const period = desc || ''
			if (!isNaN(l) && l >= 0 && l > r) return period + '还剩 ' + r + ' 次（上限 ' + l + ' 次）'
			return period + '还剩 ' + r + ' 次'
		},

		/** 这个档位是不是正在生效（名字对上即可，后端 planNames 是数组）。同样按参数调用 → 放 methods */
		isCurrentPlan (planCode) {
			const b = vip.benefit
			const names = (b && b.planNames) || []
			const plans = vip.plans
			for (let i = 0; i < plans.length; i++) {
				if (plans[i].planCode === planCode) return names.indexOf(plans[i].planName) >= 0
			}
			return false
		},

		goBack () {
			uni.navigateBack()
		},

		refresh () {
			if (!this.loggedIn || this.sessionExpired) {
				this.toast('重新登录后才能读取会员信息')
				return
			}
			// 诚实一点：这里只负责"发起"，结果到了页面自己会显示，不提前说"已刷新"
			vip.refresh()
			this.toast('正在重新读取')
		},

		goLogin () {
			// 会话确实过期时先把本地会话清掉，否则从登录页回来还是拿旧 token 反复被拒
			if (vip.unauthorized) {
				auth.clearSession()
				vip.reset()
			}
			uni.navigateTo({ url: '/pages/login/login' })
		},

		toast (title) {
			uni.showToast({ title: title, icon: 'none' })
		},

		selectPlan (planCode) {
			// 高亮的真源是 store（computed plans 读它），页面不另存一份，免得两处不同步
			vip.selected = vip.selected === planCode ? '' : planCode
		},

		/* ---------- 开通：两步确认 ---------- */
		askSub (planCode) {
			if (!this.loggedIn) {
				this.toast('先登录再开通')
				return
			}
			if (this.submitting) return
			vip.selected = planCode
			this.subStep = planCode
			this.promoInput = ''
			this.flash = ''
		},

		cancelSub () {
			this.subStep = ''
			this.promoInput = ''
		},

		async confirmSub (planCode) {
			if (this.submitting) return
			const discount = this.promoInput.trim().toUpperCase()
			try {
				let order
				if (discount) {
					// 填了立减码：立减只在 `POST /vip/order` 的 body 里生效
					// → 走标准两步（下单拿 payParams → 模拟支付）
					const res = await vip.createAndPay(planCode, discount)
					order = res.order
				} else {
					// 没填码：`/vip/subscribe` 内部就是「下单 + 模拟支付」两步，少一次往返
					order = await vip.subscribe(planCode)
				}
				this.subStep = ''
				this.promoInput = ''
				this.flash = this.paySuccessText(order)
				this.toast('开通成功')
			} catch (e) {
				this.subStep = ''
				this.handleError(e, '开通没成功')
			}
		},

		paySuccessText (order) {
			const name = (order && order.planName) || '会员'
			const tip = mockTip(order && order.payParams)
			return name + ' 已开通（模拟支付，渠道为 mock）。' + tip
		},

		/* ---------- 待支付订单：去支付 / 取消 ---------- */
		async payOrder (orderNo) {
			if (this.submitting) return
			try {
				const order = await vip.pay(orderNo)
				this.flash = this.paySuccessText(order)
				this.toast('支付成功（模拟）')
			} catch (e) {
				this.handleError(e, '支付没成功')
			}
		},

		cancelOrder () {
			// 后端没有"取消订单"接口：待支付的单 30 分钟后由后端自动关（读列表时顺手关）。
			// 所以这里如实说明，不做一个假的取消按钮。
			this.toast('待支付的单 30 分钟后会自动关闭，不用手动取消')
		},

		/* ---------- 退款：两步确认 ---------- */
		askRefund (orderNo) {
			if (this.submitting) return
			this.refundNo = orderNo
			this.refundReason = ''
		},

		cancelRefund () {
			this.refundNo = ''
			this.refundReason = ''
		},

		async doRefund (orderNo) {
			if (this.submitting) return
			try {
				await vip.refund(orderNo, this.refundReason)
				this.cancelRefund()
				this.flash = '退款已受理，商家退款按订单快照回收了会员时长。渠道是 mock，退款同样是模拟的。'
				this.toast('已退款（模拟）')
			} catch (e) {
				this.cancelRefund()
				this.handleError(e, '退款没成功')
			}
		},

		/* ---------- 兑换码 ---------- */
		async confirmRedeem () {
			if (!this.loggedIn || this.sessionExpired) {
				this.toast(this.sessionExpired ? '登录已过期，重新登录后再兑换' : '先登录再兑换')
				return
			}
			if (this.submitting) return
			const code = this.promoCode.trim().toUpperCase()
			if (!code) {
				this.toast('先填入兑换码')
				return
			}
			this.promoMsg = ''
			try {
				const order = await vip.redeem(code)
				this.promoCode = ''
				this.promoMsg = '兑换成功：' + ((order && order.planName) || '会员时长') + ' 已到账'
				this.flash = '兑换码已核销，会员时长直接到账（0 元促销订单不支持退款）。'
			} catch (e) {
				if (e instanceof ApiError && e.isUnauthorized) {
					// 兑换是写操作，掉线要显式处理（读接口那边靠错误文案提示就够了）
					this.handleError(e, '兑换没成功')
					return
				}
				this.promoMsg = vipErrorMessage(e)
			}
		},

		/** 统一错误出口：掉线就清会话并引导重登，其余给人话 */
		handleError (e, prefix) {
			const text = vipErrorMessage(e)
			this.flash = prefix + '：' + text
			if (e instanceof ApiError && e.isUnauthorized) {
				// 会话真过期了：本地会话与会员状态都清掉，界面立刻回到软门槛（否则会一直被拒却不自知）
				auth.clearSession()
				vip.reset()
				this.toast('登录已过期，请重新登录')
				return
			}
			this.toast(text)
		}
	}
}
</script>

<style lang="scss" scoped>
/* ---- 顶部返回栏（自绘箭头，素材已废弃） ---- */
.nav {
	display: flex;
	align-items: center;
	padding: 0 $mw-space-3;
	background: $mw-surface;
	border-bottom: 1rpx solid $mw-line;
}
.nav__back {
	display: flex;
	align-items: center;
	justify-content: center;
	width: $mw-touch-min;
	height: $mw-touch-min;
}
.nav__title {
	flex: 1;
	font-size: $mw-font-h2;
	font-weight: 600;
	color: $mw-ink;
}
.nav__refresh {
	padding: 0 $mw-space-2;
	min-width: $mw-touch-min;
	text-align: right;
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-brand-ink;
}
.chev {
	width: 20rpx;
	height: 20rpx;
	border-top: 4rpx solid $mw-ink-2;
	border-right: 4rpx solid $mw-ink-2;
}
.chev--left {
	transform: rotate(-135deg);
}

.body {
	padding: $mw-space-3 $mw-page-padding $mw-space-8;
	background: $mw-canvas;
	min-height: 100vh;
}

/* ---- 会员状态（深色区：文字色只用 on-night，不混浅色区令牌） ---- */
.hero {
	padding: $mw-space-4;
	background: $mw-night;
	border-radius: $mw-radius-card;
}
.hero__eyebrow {
	display: block;
	font-size: $mw-font-micro;
	color: $mw-on-night-2;
}
.hero__title {
	display: block;
	margin-top: $mw-space-1;
	font-size: $mw-font-h1;
	font-weight: 700;
	letter-spacing: -0.02em;
	color: $mw-on-night;
}
.hero__sub {
	display: block;
	margin-top: $mw-space-1;
	font-size: $mw-font-caption;
	line-height: 1.65;
	color: $mw-on-night-2;
}
.hero__note {
	display: block;
	margin-top: $mw-space-2;
	font-size: $mw-font-micro;
	line-height: 1.6;
	color: $mw-on-night-2;
}
/* 深底上的按钮用亮水青（白字只放浅底的 brand-ink，见设计令牌硬规则） */
.hero__btn {
	margin-top: $mw-space-3;
	min-height: $mw-touch-min;
	border-radius: $mw-radius-pill;
	border: 1rpx solid $mw-brand;
	display: flex;
	align-items: center;
	justify-content: center;
}
.hero__btn text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-brand;
}
.balance {
	margin-top: $mw-space-3;
	border-top: 1rpx solid $mw-night-line;
}
.balance__row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: $mw-space-2 0;
	border-bottom: 1rpx solid $mw-night-line;
}
.balance__label {
	font-size: $mw-font-caption;
	color: $mw-on-night-2;
}
.balance__value {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-brand;
	font-variant-numeric: tabular-nums;
}

/* ---- 区块标题 ---- */
.sec {
	margin: $mw-space-4 0 $mw-space-2;
}
.sec__title {
	display: block;
	font-size: $mw-font-h2;
	font-weight: 600;
	color: $mw-ink;
}
.sec__note {
	display: block;
	margin-top: 6rpx;
	font-size: $mw-font-micro;
	color: $mw-ink-2;
}

/* ---- 未登录软门槛 ---- */
.soft {
	padding: $mw-space-4;
	background: $mw-surface;
	border: 1rpx solid $mw-line;
	border-radius: $mw-radius-card;
}
.soft__title {
	display: block;
	font-size: $mw-font-body;
	font-weight: 600;
	color: $mw-ink;
}
.soft__desc {
	display: block;
	margin-top: $mw-space-1;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}
.soft__btn {
	margin-top: $mw-space-3;
	min-height: $mw-touch-min;
	border-radius: $mw-radius-pill;
	background: $mw-brand-ink;
	display: flex;
	align-items: center;
	justify-content: center;
}
.soft__btn text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: #ffffff;
}

/* ---- 档位卡 ---- */
.plan {
	margin-bottom: $mw-space-3;
	padding: $mw-space-4;
	background: $mw-surface;
	border: 1rpx solid $mw-line;
	border-radius: $mw-radius-card;
}
.plan--on {
	border-color: $mw-brand-ink;
}
.plan__head {
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
}
.plan__names {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
}
.plan__name {
	font-size: $mw-font-body;
	font-weight: 700;
	color: $mw-ink;
}
.plan__code {
	margin-top: 4rpx;
	font-size: $mw-font-micro;
	color: $mw-muted;
	letter-spacing: 0.04em;
}
.plan__price {
	display: flex;
	align-items: baseline;
}
.plan__cur {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-brand-ink;
}
.plan__num {
	margin-left: 2rpx;
	font-size: 52rpx;
	font-weight: 700;
	letter-spacing: -0.02em;
	color: $mw-brand-ink;
	font-variant-numeric: tabular-nums;
}
.plan__per {
	margin-left: 4rpx;
	font-size: $mw-font-micro;
	color: $mw-muted;
}
.plan__tagline {
	display: block;
	margin-top: $mw-space-2;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}
.gains {
	margin-top: $mw-space-3;
	padding-top: $mw-space-2;
	border-top: 1rpx solid $mw-line;
}
.gain {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 6rpx 0;
}
.gain__label {
	font-size: $mw-font-micro;
	color: $mw-ink-2;
}
.gain__value {
	font-size: $mw-font-micro;
	font-weight: 600;
	color: $mw-ink;
	font-variant-numeric: tabular-nums;
}
.plan__cta {
	margin-top: $mw-space-3;
	min-height: $mw-touch-min;
	border-radius: $mw-radius-pill;
	background: $mw-brand-ink;
	display: flex;
	align-items: center;
	justify-content: center;
}
.plan__cta text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: #ffffff;
}

/* ---- 行内二次确认（不用原生 confirm / uni.showModal） ---- */
.confirm {
	margin-top: $mw-space-3;
	padding: $mw-space-3;
	background: $mw-canvas;
	border-radius: $mw-radius-input;
}
.confirm__line {
	display: block;
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-ink;
}
.confirm__memo {
	display: block;
	margin-top: 6rpx;
	font-size: $mw-font-micro;
	line-height: 1.6;
	color: $mw-ink-2;
}
.confirm__input {
	margin-top: $mw-space-2;
	padding: 0 $mw-space-2;
	min-height: 72rpx;
	background: $mw-surface;
	border: 1rpx solid $mw-line;
	border-radius: $mw-radius-input;
	font-size: $mw-font-caption;
	color: $mw-ink;
}
.confirm__acts {
	display: flex;
	margin-top: $mw-space-2;
}
/* 行内二次确认的两个按钮：各占一半宽度（不用 :last-child 改宽度，避免单个按钮被拉满） */
.mini {
	width: calc(50% - #{$mw-space-1});
	min-height: $mw-touch-min;
	margin-right: $mw-space-2;
	border-radius: $mw-radius-pill;
	display: flex;
	align-items: center;
	justify-content: center;
}
.mini:last-child {
	margin-right: 0;
}
.mini text {
	font-size: $mw-font-caption;
	font-weight: 600;
}
.mini--ghost {
	border: 1rpx solid $mw-line;
	background: $mw-surface;
}
.mini--ghost text {
	color: $mw-ink;
}
.mini--primary {
	background: $mw-brand-ink;
}
.mini--primary text {
	color: #ffffff;
}
.mini--danger {
	background: $mw-danger;
}
.mini--danger text {
	color: #ffffff;
}

/* ---- 兑换码 ---- */
.promo {
	display: flex;
	align-items: center;
	padding: $mw-space-2;
	background: $mw-surface;
	border: 1rpx solid $mw-line;
	border-radius: $mw-radius-card;
}
.promo__input {
	flex: 1;
	min-height: 72rpx;
	padding: 0 $mw-space-2;
	font-size: $mw-font-body;
	color: $mw-ink;
}
.promo__ph {
	color: $mw-muted;
}
.promo__btn {
	width: 160rpx;
	min-height: 72rpx;
	border-radius: $mw-radius-input;
	background: $mw-brand-ink;
	display: flex;
	align-items: center;
	justify-content: center;
}
.promo__btn--off {
	opacity: 0.45;
}
.promo__btn text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: #ffffff;
}
.promo__msg {
	display: block;
	margin-top: $mw-space-2;
	font-size: $mw-font-micro;
	color: $mw-brand-ink;
}

/* ---- 订单卡 ---- */
.order {
	margin-bottom: $mw-space-3;
	padding: $mw-space-3 $mw-space-4;
	background: $mw-surface;
	border: 1rpx solid $mw-line;
	border-radius: $mw-radius-card;
}
.order__head {
	display: flex;
	align-items: center;
	justify-content: space-between;
}
.order__name {
	flex: 1;
	min-width: 0;
	font-size: $mw-font-body;
	font-weight: 600;
	color: $mw-ink;
}
.order__status {
	padding: 4rpx 12rpx;
	border-radius: $mw-radius-tag;
	font-size: $mw-font-micro;
	background: $mw-canvas;
	color: $mw-ink-2;
}
.order__status--pending {
	background: rgba(255, 176, 32, 0.16);
	color: $mw-accent-ink;
}
.order__status--paid {
	background: rgba(194, 65, 12, 0.12);
	color: $mw-brand-ink;
}
.order__status--closed {
	background: $mw-canvas;
	color: $mw-muted;
}
.order__status--refunded {
	background: rgba(229, 72, 77, 0.12);
	color: $mw-danger;
}
.order__money {
	display: flex;
	align-items: baseline;
	flex-wrap: wrap;
	margin-top: $mw-space-2;
}
.order__pay {
	font-size: 40rpx;
	font-weight: 700;
	letter-spacing: -0.02em;
	color: $mw-ink;
	font-variant-numeric: tabular-nums;
}
.order__discount {
	margin-left: $mw-space-2;
	font-size: $mw-font-micro;
	color: $mw-brand-ink;
}
.order__origin {
	margin-left: $mw-space-2;
	font-size: $mw-font-micro;
	color: $mw-muted;
	text-decoration: line-through;
}
.order__meta {
	display: flex;
	flex-wrap: wrap;
	margin-top: $mw-space-1;
}
.order__m {
	margin-right: $mw-space-2;
	font-size: $mw-font-micro;
	color: $mw-ink-2;
}
.order__no {
	display: block;
	margin-top: 6rpx;
	font-size: 22rpx;
	color: $mw-muted;
	letter-spacing: 0.04em;
}
.order__reason {
	display: block;
	margin-top: 6rpx;
	font-size: $mw-font-micro;
	color: $mw-ink-2;
}
.order__note {
	display: block;
	margin-top: $mw-space-1;
	font-size: $mw-font-micro;
	color: $mw-muted;
}
.order__reason-input {
	margin-top: $mw-space-2;
	padding: 0 $mw-space-2;
	min-height: 72rpx;
	background: $mw-canvas;
	border-radius: $mw-radius-input;
	font-size: $mw-font-caption;
	color: $mw-ink;
}
.order__acts {
	display: flex;
	margin-top: $mw-space-2;
}

/* ---- 状态 ---- */
.skeleton__card {
	height: 220rpx;
	margin-bottom: $mw-space-3;
	border-radius: $mw-radius-card;
	background: $mw-surface;
	border: 1rpx solid $mw-line;
	/* 呼吸，不是"加载中…"纯文字 */
	animation: mw-breathe 1.4s ease-in-out infinite;
}
@keyframes mw-breathe {
	0% { opacity: 0.55; }
	50% { opacity: 1; }
	100% { opacity: 0.55; }
}
.hint {
	display: block;
	padding: $mw-space-3 0;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}
.empty {
	display: block;
	padding: $mw-space-4;
	background: $mw-surface;
	border: 1rpx solid $mw-line;
	border-radius: $mw-radius-card;
	text-align: center;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}
.empty__text {
	display: block;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}
.err {
	display: block;
	padding: $mw-space-4;
	background: $mw-surface;
	border: 1rpx solid rgba(229, 72, 77, 0.3);
	border-radius: $mw-radius-card;
	font-size: $mw-font-caption;
	line-height: 1.7;
	color: $mw-danger;
}
.err__text {
	display: block;
	font-size: $mw-font-caption;
	line-height: 1.7;
	color: $mw-danger;
}
.err__hint {
	display: block;
	margin-top: $mw-space-1;
	font-size: $mw-font-micro;
	line-height: 1.6;
	color: $mw-ink-2;
}
/* 错误态与空态里的下一步：说清原因之后必须留一个可点的动作，不能只显示一句红字 */
.act {
	margin-top: $mw-space-3;
	min-height: $mw-touch-min;
	border-radius: $mw-radius-pill;
	background: $mw-brand-ink;
	display: flex;
	align-items: center;
	justify-content: center;
}
.act text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: #ffffff;
}
.foot {
	display: block;
	margin-top: $mw-space-4;
	font-size: $mw-font-micro;
	line-height: 1.7;
	color: $mw-muted;
}
</style>