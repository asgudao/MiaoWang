<template>
	<view class="page">
		<!-- 已登录：深色资料卡，与首页舞台同族 -->
		<view v-if="loggedIn" class="profile">
			<view class="profile__avatar">
				<image v-if="avatar" class="profile__img" :src="avatar" mode="aspectFill" />
				<text v-else>{{ initial }}</text>
			</view>
			<view class="profile__main">
				<text class="profile__name">{{ displayName }}</text>
				<text class="profile__sub">
					<text v-if="phone" class="num">{{ phone }}</text>
					<text v-if="callName">宠物叫你「{{ callName }}」</text>
					<text v-else>还没告诉宠物该怎么称呼你</text>
				</text>
			</view>
		</view>

		<!-- 未登录：空态，直接给下一步，不假装有资料 -->
		<view v-else class="guest">
			<text class="guest__title">还没有登录</text>
			<text class="guest__desc">登录后这里会显示你的宠物档案、照护提醒和会员权益。知识库不用登录也能看。</text>
			<view class="guest__actions">
				<view class="btn btn--primary" @click="goLogin"><text>去登录</text></view>
				<view class="btn btn--ghost" @click="goRegister"><text>注册一个</text></view>
			</view>
			<view v-if="backendUnavailable" class="alert">
				<text>连不上后端（{{ target }}）：真机要确认局域网 IP 与电脑防火墙；H5 要确认后端已启动并注册到 Nacos。</text>
			</view>
		</view>

		<!-- 会员入口：整卡可点，进会员页。文案不吹（权益数值以接口为准） -->
		<view class="vip" @click="goVip">
			<view class="vip__head">
				<text class="vip__title">喵汪会员</text>
				<text class="vip__badge">{{ vipBadge }}</text>
			</view>
			<text class="vip__desc">{{ vipDesc }}</text>
			<view class="vip__btn"><text>查看档位与权益</text></view>
		</view>

		<view class="rows">
			<view v-for="s in settings" :key="s.label" class="row" @click="toast">
				<view class="row__main">
					<text class="row__label">{{ s.label }}</text>
					<text class="row__desc">{{ s.desc }}</text>
				</view>
				<text class="row__badge">开发中</text>
			</view>
		</view>

		<template v-if="loggedIn">
			<view class="logout" :class="{ 'logout--busy': busy }" @click="onLogout">
				<text>{{ busy ? '退出中' : (confirming ? '确认退出登录' : '退出登录') }}</text>
			</view>
			<text v-if="confirming" class="cancel" @click="confirming = false">先不退</text>
		</template>
		<text v-else class="note">未登录，页面数据为演示内容</text>

		<FloatingTabBar current="mine" />
	</view>
</template>

<script>
import FloatingTabBar from '@/components/FloatingTabBar.vue'
import { hideNativeTabBar } from '@/utils/tabbar.js'
import { auth } from '@/store/auth.js'
import { vip } from '@/store/vip.js'
import { describeTarget } from '@/utils/request.js'

export default {
	components: { FloatingTabBar },
	data() {
		return {
			// 退出用行内二次确认，不用 uni.showModal（打断且丑）
			confirming: false,
			busy: false,
			settings: [
				{ label: '个人资料', desc: '昵称、手机号、头像' },
				{ label: '提醒设置', desc: '推送时间与免打扰' },
				{ label: '消息通知', desc: '评论、点赞、关注' },
				{ label: '关于喵汪', desc: '版本与开源许可' }
			]
		}
	},
	computed: {
		loggedIn() {
			return auth.isLoggedIn()
		},
		/**
		 * ⚠️ 模板里**不能**直接写 `auth.xxx()`：Options API 的模板只认实例上暴露的属性，
		 * `auth` 是 import 进来的普通对象、不在实例上 → 渲染期报
		 * "Property auth was accessed during render but is not defined on instance" 并白屏。
		 * store 的一切都要经过 computed / methods 转一手（tools/check-phone.js 会静态检查这一点）。
		 */
		initial() {
			return auth.initial()
		},
		displayName() {
			return auth.displayName()
		},
		avatar() {
			return (auth.state.info && auth.state.info.avatar) || ''
		},
		phone() {
			return auth.maskedPhone()
		},
		callName() {
			return (auth.state.info && auth.state.info.ownerCallName) || ''
		},
		backendUnavailable() {
			return auth.state.backendUnavailable
		},
		/** 出错时告诉用户"请求打到哪了"，省得猜是 H5 还是真机的问题 */
		target() {
			return describeTarget()
		},
		/**
		 * 会员卡角标：只在**拿得到权益**时才显示状态，拿不到就如实说取不到。
		 * 不猜、不写死免费档数值（权益一律以接口为准）；失败也不能留一个空胶囊在标题右边。
		 */
		vipBadge() {
			if (!this.loggedIn) return ''
			if (vip.benefitError) return '读取失败'
			const b = vip.benefit
			if (!b) return '读取中'
			return b.vip ? '生效中' : '未开通'
		},
		vipDesc() {
			// 失败要说出来（进会员页可以重试），不能假装是"未开通"
			if (this.loggedIn && vip.benefitError) return '会员状态没读到，点进去可以重试'
			const b = vip.benefit
			if (b && b.vip) {
				const names = (b.planNames || []).join('、')
				return names ? names + ' 生效中，点进去看用量与订单' : '会员生效中，点进去看用量与订单'
			}
			return '用兑换码或直接开通，看当前档位与 9 项权益'
		}
	},
	onLoad() {
		hideNativeTabBar()
	},
	onShow() {
		hideNativeTabBar()
		// 直接进本页（没走过首页）时也要把资料补齐
		auth.restore()
		// 会员卡角标要用权益数据；未登录时这个接口不需要调（拿不到身份）
		if (auth.isLoggedIn()) vip.fetchBenefit()
	},
	methods: {
		toast() {
			uni.showToast({ title: '功能开发中', icon: 'none' })
		},
		goVip() {
			uni.navigateTo({ url: '/pages/vip/vip' })
		},
		async onLogout() {
			if (this.busy) return
			if (!this.confirming) {
				this.confirming = true
				return
			}
			this.busy = true
			try {
				await auth.logout()
				uni.showToast({ title: '已退出登录', icon: 'none' })
			} finally {
				this.busy = false
				this.confirming = false
			}
		},
		goLogin() {
			uni.navigateTo({ url: '/pages/login/login' })
		},
		goRegister() {
			uni.navigateTo({ url: '/pages/register/register' })
		}
	}
}
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.page {
	min-height: 100vh;
	background: $mw-canvas;
	padding: calc(var(--status-bar-height, 20px) + #{$mw-space-4}) $mw-page-padding 0;
	padding-bottom: calc(#{$mw-tabbar-h} + 80rpx);
}

.profile {
	display: flex;
	align-items: center;
	padding: $mw-space-4;
	background: $mw-night;
	border-radius: $mw-radius-card;
}
.profile__avatar {
	width: 112rpx;
	height: 112rpx;
	border-radius: 50%;
	background: rgba(53, 214, 168, 0.16);
	display: flex;
	align-items: center;
	justify-content: center;
	flex: none;
	overflow: hidden;
}
.profile__avatar text {
	font-size: 44rpx;
	font-weight: 700;
	color: $mw-brand;
}
.profile__img {
	width: 100%;
	height: 100%;
}
.profile__main {
	flex: 1;
	min-width: 0;
	margin-left: $mw-space-3;
	display: flex;
	flex-direction: column;
}
.profile__name {
	font-size: 40rpx;
	font-weight: 700;
	color: $mw-on-night;
}
.profile__sub {
	font-size: $mw-font-caption;
	color: $mw-on-night-2;
}
.num {
	color: $mw-brand;
	font-variant-numeric: tabular-nums;
	margin-right: $mw-space-2;
}

/* ---------- 未登录空态 ---------- */
.guest {
	padding: $mw-space-6 $mw-space-4;
	display: flex;
	flex-direction: column;
	align-items: center;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
.guest__title {
	font-size: $mw-font-h1;
	font-weight: 700;
	letter-spacing: -0.02em;
	color: $mw-ink;
}
.guest__desc {
	margin: $mw-space-2 0 $mw-space-4;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
	text-align: center;
	line-height: 1.7;
}
.guest__actions {
	display: flex;
	gap: $mw-space-2;
	width: 100%;
}
.alert {
	margin-top: $mw-space-3;
	padding: $mw-space-3 $mw-space-4;
	background: rgba(10, 125, 109, 0.08);
	border: 1px solid rgba(10, 125, 109, 0.2);
	border-radius: $mw-radius-input;
}
.alert text {
	font-size: 22rpx;
	color: $mw-brand-ink;
	line-height: 1.6;
}

.btn {
	flex: 1;
	min-height: $mw-touch-min;
	border-radius: $mw-radius-pill;
	display: flex;
	align-items: center;
	justify-content: center;
}
.btn text {
	font-size: $mw-font-caption;
	font-weight: 600;
}
.btn--ghost {
	border: 1px solid $mw-line;
	background: transparent;
}
.btn--ghost text {
	color: $mw-ink;
}
.btn--primary {
	background: $mw-brand-ink;
}
.btn--primary text {
	color: #ffffff;
}

.vip {
	margin-top: $mw-space-3;
	padding: $mw-space-4;
	background: linear-gradient(0deg, rgba(10, 125, 109, 0.06), rgba(10, 125, 109, 0.06)), $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
.vip__head {
	display: flex;
	align-items: center;
	justify-content: space-between;
}
.vip__badge {
	padding: 4rpx 12rpx;
	border-radius: $mw-radius-tag;
	background: rgba(10, 125, 109, 0.12);
	font-size: $mw-font-micro;
	font-weight: 600;
	color: $mw-brand-ink;
}
.vip__title {
	display: block;
	font-size: $mw-font-h2;
	font-weight: 600;
	color: $mw-ink;
}
.vip__desc {
	display: block;
	margin-top: $mw-space-1;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}
.vip__btn {
	margin-top: $mw-space-3;
	min-height: $mw-touch-min;
	border-radius: $mw-radius-pill;
	background: $mw-brand-ink;
	display: flex;
	align-items: center;
	justify-content: center;
}
.vip__btn text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: #ffffff;
}

.rows {
	margin-top: $mw-space-3;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
	overflow: hidden;
}
.row {
	display: flex;
	align-items: center;
	min-height: 120rpx;
	padding: 0 $mw-space-4;
	border-bottom: 1px solid $mw-line;
}
.row:last-child {
	border-bottom: none;
}
.row__main {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
}
.row__label {
	font-size: $mw-font-body;
	font-weight: 600;
	color: $mw-ink;
}
.row__desc {
	font-size: 22rpx;
	color: $mw-muted;
}
.row__badge {
	padding: 4rpx 12rpx;
	border-radius: $mw-radius-tag;
	background: $mw-canvas;
	font-size: 22rpx;
	color: $mw-muted;
}

.logout {
	margin-top: $mw-space-4;
	min-height: $mw-btn-h;
	border: 1px solid rgba(229, 72, 77, 0.3);
	border-radius: $mw-radius-pill;
	display: flex;
	align-items: center;
	justify-content: center;
}
.logout text {
	font-size: $mw-font-body;
	font-weight: 600;
	color: $mw-danger;
}
.logout--busy {
	opacity: 0.6;
}

.cancel {
	display: block;
	margin-top: $mw-space-3;
	text-align: center;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}

.note {
	display: block;
	margin-top: $mw-space-3;
	text-align: center;
	font-size: 22rpx;
	color: $mw-muted;
}
</style>