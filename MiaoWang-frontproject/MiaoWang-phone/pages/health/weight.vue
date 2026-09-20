<template>
	<view class="hpage">
		<view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav__back" @click="goBack"><view class="chev chev--left"></view></view>
			<text class="nav__title">体重曲线</text>
			<text class="nav__act" @click="goNew">记一次</text>
		</view>

		<view v-if="showGate" class="state">
			<text class="state__title">{{ sessionExpired ? '登录已过期' : '要登录才能看健康档案' }}</text>
			<text class="state__desc">{{ gateDesc }}</text>
			<view class="state__btn" @click="goLogin"><text>去登录</text></view>
			<view class="state__ghost" @click="retry"><text>我已经登录了，重新读取</text></view>
		</view>

		<template v-else>
			<view class="seg seg--page">
				<text
					v-for="m in monthChips"
					:key="m.value"
					class="seg__item"
					:class="{ 'seg__item--on': m.on }"
					@click="selectMonths(m.value)"
				>{{ m.label }}</text>
			</view>

			<view v-if="firstLoading" class="skeleton">
				<view class="skeleton__card"></view>
				<view class="skeleton__card"></view>
			</view>

			<template v-else>
				<view v-for="e in errorList" :key="e.key" class="inline-err">
					<text class="inline-err__text">{{ e.text }}</text>
					<text class="inline-err__act" @click="retry">重试</text>
				</view>
				<view v-if="actionError" class="inline-err">
					<text class="inline-err__text">{{ actionError }}</text>
					<text class="inline-err__act" @click="actionError = ''">知道了</text>
				</view>

				<!-- 曲线卡：纯 rpx 视图手写折线（不引 npm、不用 canvas） -->
				<view class="hc">
					<view class="hc__head">
						<text class="hc__title">最近 {{ months }} 个月</text>
						<text class="tag tag--muted">{{ curve.count }} 个测量日</text>
					</view>

					<view v-if="!curve.hasPoints" class="empty">
						<text class="hc__note">{{ curve.emptyText }}</text>
					</view>

					<view v-else>
						<view class="charthead">
							<text class="charthead__num">{{ curve.maxText }}</text>
							<text class="charthead__num charthead__num--dim">{{ curve.minText }}</text>
						</view>
						<view class="chart" :style="{ height: curve.height + 'rpx' }">
							<view v-for="s in curve.segments" :key="s.key" class="chart__seg" :style="segStyle(s)"></view>
							<view
								v-for="d in curve.dots"
								:key="d.index"
								class="chart__dot"
								:style="{ left: d.left + 'rpx', top: d.top + 'rpx' }"
							>
								<text v-if="d.showLabel" class="chart__value">{{ d.weightText }}</text>
							</view>
							<text v-for="t in curve.ticks" :key="t.key" class="chart__tick" :style="{ left: t.left + 'rpx' }">{{ t.label }}</text>
						</view>
						<text class="hc__dim">{{ curve.spanText }}。折线是两条相邻测量点之间的一条细线，每天只取当天最后一次测量。</text>
						<view class="delta">
							<text class="delta__k">区间首末</text>
							<text class="delta__v">{{ curve.firstText }} 到 {{ curve.lastText }}</text>
						</view>
						<view class="delta">
							<text class="delta__k">区间变化</text>
							<text class="delta__v" :class="'tone--' + curve.deltaTone">{{ curve.deltaText }}</text>
						</view>
					</view>
				</view>

				<!-- 概览给的「最新体重 + 30 天变化」 -->
				<view class="hc">
					<text class="hc__title">和 30 天前比</text>
					<view class="delta">
						<text class="delta__k">最新一次</text>
						<text class="delta__v">{{ latestText }}</text>
					</view>
					<view class="delta">
						<text class="delta__k">变化</text>
						<text class="delta__v" :class="'tone--' + changeTone">{{ changeText }}</text>
					</view>
					<text class="hc__dim">这条口径来自概览接口，它只跟「30 天前最近的一次」比。曲线上的区间变化是这条曲线自己的首末点，两个数不一样是正常的。</text>
				</view>

				<!-- 体重记录（列表是裸数组，实测 id 是 111/112） -->
				<view class="hc">
					<view class="hc__head">
						<text class="hc__title">全部体重记录</text>
						<text class="hc__act" @click="goNew">记一次</text>
					</view>
					<view v-if="!cards.length" class="empty">
						<text class="hc__note">还没有体重记录。记一次（体重 + 测量时间），曲线立刻就会有第一个点。</text>
					</view>
					<view v-else>
						<view v-for="c in cards" :key="c.key" class="wrow">
							<view class="wrow__main">
								<text class="wrow__value">{{ c.weightText }}</text>
								<text class="wrow__time">{{ c.measuredText }}</text>
								<text v-if="c.hasNote" class="wrow__note">{{ c.note }}</text>
							</view>
							<view class="wrow__acts">
								<text class="rec__act" @click="goEdit(c.id)">编辑</text>
								<text class="rec__act rec__act--danger" @click="askDelete(c.id)">{{ confirmingId === c.id ? '确认删除' : '删除' }}</text>
							</view>
						</view>
						<text v-if="confirmingId" class="hc__dim">再点一次「确认删除」就会删掉这条。删除是逻辑删除，原始数据仍留在库里。</text>
					</view>
				</view>

				<text class="foot">曲线用的是按天聚合的接口（同一天只留最后一次测量），时间窗由 months 参数控制，后端会把它钳在 1 到 24 个月之间。</text>
			</template>
		</template>
	</view>
</template>

<script>
/**
 * 体重曲线（手机端 / Options API）
 *
 * 契约（实测）：
 * - `GET /{pid}/weight/curve?months=` → **裸数组** `[{date:"yyyy-MM-dd", weightKg, count}]`，
 *   `date` **升序**、按天聚合（每天取最后一次），`count` 是当天测量次数；
 *   `date` 只有日期**没有时分秒** → 别拿它当 `yyyy-MM-dd HH:mm:ss` 解析
 * - `months` 后端钳到 **[1,24]**（实测 `999`≡`24`、`0`/`-5`≡`1`，默认 6）
 * - `GET /{pid}/weight` → 裸数组，按 `measuredAt` 倒序；⚠️ 实测 id 是 **111/112**，不是 1/2
 * - 概览的 `weightChangeKg` 口径 = 最新 − 30 天前基线，**没有基线时是 `null`**（不是 0）
 * - 折线是**纯 rpx 视图**拼的（相邻点之间一条旋转过的细 view + 绝对定位圆点），不引 npm、不用 canvas
 */
import { auth } from '@/store/auth.js'
import { ApiError } from '@/utils/request.js'
import { CURVE_MONTHS_DEFAULT, listWeight, overview, removeWeight, weightCurve } from '@/api/health.js'
import {
	CURVE_MONTH_OPTIONS,
	curveView,
	healthErrorMessage,
	weightCard,
	weightChangeText,
	weightChangeTone,
	weightText
} from '@/utils/health.js'

export default {
	data() {
		return {
			pid: 0,
			statusBarHeight: 20,
			months: CURVE_MONTHS_DEFAULT,
			curvePoints: [],
			rows: [],
			overview: null,
			loading: false,
			loadedOnce: false,
			errors: { curve: '', list: '', overview: '' },
			unauthorized: false,
			confirmingId: null,
			actionError: ''
		}
	},
	computed: {
		loggedIn() {
			return auth.isLoggedIn()
		},
		sessionExpired() {
			return auth.isLoggedIn() && this.unauthorized
		},
		showGate() {
			return !this.loggedIn || this.sessionExpired
		},
		gateDesc() {
			if (this.sessionExpired) return '本地的登录状态后端已经不认了，重新登录一次就能继续看。'
			return '健康档案接口经过网关时一律要带登录身份，匿名请求会被直接拦掉。'
		},
		monthChips() {
			const out = []
			for (let i = 0; i < CURVE_MONTH_OPTIONS.length; i++) {
				const v = CURVE_MONTH_OPTIONS[i]
				out.push({ value: v, label: v + ' 个月', on: this.months === v })
			}
			return out
		},
		curve() {
			// 盒子 630rpx 宽（750 稿减掉左右页边距 32*2 与卡片内边距 32*2）、320rpx 高
			return curveView(this.curvePoints, { width: 630, height: 320 })
		},
		cards() {
			return this.rows.map(weightCard)
		},
		firstLoading() {
			return this.loading && !this.curvePoints.length && !this.rows.length && !this.overview
		},
		errorList() {
			const out = []
			if (this.errors.curve) out.push({ key: 'curve', text: '体重曲线没读到：' + this.errors.curve })
			if (this.errors.list) out.push({ key: 'list', text: '体重记录没读到：' + this.errors.list })
			if (this.errors.overview) out.push({ key: 'overview', text: '30 天对比没读到：' + this.errors.overview })
			return out
		},
		latestText() {
			const o = this.overview
			if (!o) return '概览没读到'
			if (!o.latestWeight) return '还没记过体重'
			return weightText(o.latestWeight.weightKg)
		},
		changeText() {
			const o = this.overview
			if (!o) return '概览没读到'
			return weightChangeText(o.weightChangeKg)
		},
		changeTone() {
			const o = this.overview
			if (!o) return 'none'
			return weightChangeTone(o.weightChangeKg)
		}
	},
	onLoad(options) {
		try {
			const info = uni.getSystemInfoSync()
			this.statusBarHeight = info.statusBarHeight || 20
		} catch (e) {
			// 拿不到就用默认值
		}
		this.pid = Number((options && options.pid) || 0)
		this.loadAll()
	},
	onShow() {
		if (this.loggedIn && this.loadedOnce && !this.loading) this.loadAll()
	},
	methods: {
		async loadAll() {
			if (!this.loggedIn) return
			this.loading = true
			this.unauthorized = false
			await Promise.all([this.loadCurve(), this.loadList(), this.loadOverview()])
			this.loadedOnce = true
			this.loading = false
		},

		async loadCurve() {
			if (!this.loggedIn) return
			this.errors.curve = ''
			try {
				const rows = await weightCurve(this.pid, this.months)
				this.curvePoints = Array.isArray(rows) ? rows : []
			} catch (e) {
				if (this.noteAuth(e)) return
				this.errors.curve = healthErrorMessage(e)
			}
		},

		async loadList() {
			if (!this.loggedIn) return
			this.errors.list = ''
			try {
				const rows = await listWeight(this.pid)
				this.rows = Array.isArray(rows) ? rows : []
			} catch (e) {
				if (this.noteAuth(e)) return
				this.errors.list = healthErrorMessage(e)
			}
		},

		async loadOverview() {
			if (!this.loggedIn) return
			this.errors.overview = ''
			try {
				this.overview = await overview(this.pid)
			} catch (e) {
				if (this.noteAuth(e)) return
				this.errors.overview = healthErrorMessage(e)
			}
		},

		selectMonths(value) {
			if (value === this.months) return
			this.months = value
			this.loadCurve()
		},

		/** 折线的每一段就是一个旋转过的细 view：左下角为原点旋转 dx/dy 对应的角度 */
		segStyle(seg) {
			return (
				'left:' + seg.left + 'rpx;top:' + seg.top + 'rpx;width:' + seg.width +
				'rpx;transform:rotate(' + seg.rotate + 'deg);'
			)
		},

		retry() {
			if (!this.loggedIn) {
				uni.navigateTo({ url: '/pages/login/login' })
				return
			}
			this.loadAll()
		},

		goLogin() {
			if (this.unauthorized) auth.clearSession()
			uni.navigateTo({ url: '/pages/login/login' })
		},

		goBack() {
			uni.navigateBack({
				fail() {
					uni.switchTab({ url: '/pages/pets/pets' })
				}
			})
		},

		goNew() {
			uni.navigateTo({ url: '/pages/health/record-edit?pid=' + this.pid + '&type=weight' })
		},

		goEdit(id) {
			uni.navigateTo({ url: '/pages/health/record-edit?pid=' + this.pid + '&type=weight&id=' + id })
		},

		askDelete(id) {
			if (this.confirmingId !== id) {
				this.confirmingId = id
				this.actionError = ''
				return
			}
			this.confirmingId = null
			this.onDelete(id)
		},

		async onDelete(id) {
			this.actionError = ''
			try {
				await removeWeight(id)
				this.rows = this.rows.filter(function (r) { return r.id !== id })
				await Promise.all([this.loadCurve(), this.loadOverview()])
			} catch (e) {
				if (this.noteAuth(e)) return
				this.actionError = '删除失败：' + healthErrorMessage(e)
			}
		},

		noteAuth(e) {
			if (!(e instanceof ApiError) || !e.isUnauthorized) return false
			this.unauthorized = true
			this.errors = { curve: '', list: '', overview: '' }
			this.loading = false
			return true
		}
	}
}
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
@import '@/styles/health.scss';

.seg--page {
	padding: 0 $mw-page-padding;
}
.empty {
	padding: $mw-space-2 0 $mw-space-1;
}

/* ---- 手写折线 ---- */
.charthead {
	display: flex;
	justify-content: space-between;
	margin-bottom: $mw-space-1;
}
.charthead__num {
	font-size: 22rpx;
	color: $mw-ink-2;
	font-variant-numeric: tabular-nums;
}
.charthead__num--dim {
	color: $mw-muted;
}
.chart {
	position: relative;
	width: 100%;
	background: $mw-canvas;
	border-radius: $mw-radius-input;
	overflow: hidden;
}
.chart__seg {
	position: absolute;
	height: 4rpx;
	background: $mw-brand-ink;
	transform-origin: 0 50%;
	border-radius: 2rpx;
}
.chart__dot {
	position: absolute;
	width: 16rpx;
	height: 16rpx;
	margin-left: -8rpx;
	margin-top: -8rpx;
	border-radius: 50%;
	background: $mw-surface;
	border: 4rpx solid $mw-brand-ink;
}
.chart__value {
	position: absolute;
	left: -40rpx;
	top: -36rpx;
	width: 100rpx;
	text-align: center;
	font-size: 20rpx;
	color: $mw-ink-2;
	font-variant-numeric: tabular-nums;
}
.chart__tick {
	position: absolute;
	bottom: 8rpx;
	width: 140rpx;
	margin-left: -70rpx;
	text-align: center;
	font-size: 20rpx;
	color: $mw-muted;
}

/* ---- 首末变化 ---- */
.delta {
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
	padding: $mw-space-2 0;
	border-bottom: 1px solid $mw-line;
}
.delta__k {
	flex: none;
	margin-right: $mw-space-3;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}
.delta__v {
	flex: 1;
	min-width: 0;
	text-align: right;
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-ink;
}
.tone--up { color: $mw-accent-ink; }
.tone--down { color: $mw-brand-ink; }
.tone--flat { color: $mw-ink; }
.tone--none { color: $mw-muted; }

/* ---- 体重记录行 ---- */
.wrow {
	display: flex;
	align-items: center;
	padding: $mw-space-3 0;
	border-bottom: 1px solid $mw-line;
}
.wrow:last-child {
	border-bottom: none;
}
.wrow__main {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
}
.wrow__value {
	font-size: $mw-font-h2;
	font-weight: 700;
	color: $mw-ink;
	font-variant-numeric: tabular-nums;
}
.wrow__time {
	margin-top: 2rpx;
	font-size: $mw-font-micro;
	color: $mw-ink-2;
}
.wrow__note {
	margin-top: 2rpx;
	font-size: 22rpx;
	color: $mw-muted;
}
.wrow__acts {
	flex: none;
	display: flex;
	align-items: center;
}
.wrow__acts .rec__act {
	flex: none;
	padding: 0 $mw-space-2;
}
</style>