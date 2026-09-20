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
			<view v-for="s in settings" :key="s.key" class="row" @click="openRow(s)">
				<view class="row__main">
					<text class="row__label">{{ s.label }}</text>
					<text class="row__desc">{{ s.desc }}</text>
				</view>
				<!-- 「消息通知」是真入口：有未读就显示未读数，没有就不显示（不写"开发中"占位） -->
				<text v-if="s.key === 'notify' && unreadBadge" class="row__badge row__badge--unread">{{ unreadBadge }}</text>
				<!-- 4 行**全部**已接真页面，右侧不再有任何「开发中」徽标（原来那枚随「提醒设置」接线一起删掉） -->
			</view>
		</view>

		<template v-if="loggedIn">
			<view class="logout" :class="{ 'logout--busy': busy }" @click="onLogout">
				<text>{{ busy ? '退出中' : (confirming ? '确认退出登录' : '退出登录') }}</text>
			</view>
			<text v-if="confirming" class="cancel" @click="confirming = false">先不退</text>
		</template>
		<text v-else class="note">未登录，页面数据为演示内容</text>

		<FloatingTabBar current="mine" :mine-dot="mineDot" />
	</view>
</template>

<script>
import FloatingTabBar from '@/components/FloatingTabBar.vue'
import { hideNativeTabBar } from '@/utils/tabbar.js'
import { auth } from '@/store/auth.js'
import { vip } from '@/store/vip.js'
import { describeTarget } from '@/utils/request.js'
import { fetchUnreadCount, notificationHasUnread, notificationUnreadText } from '@/api/community.js'

export default {
	components: { FloatingTabBar },
	data() {
		return {
			// 退出用行内二次确认，不用 uni.showModal（打断且丑）
			confirming: false,
			busy: false,
			/**
			 * 未读消息数：**只在 `unread-count` 成功时有值**（裸数字接口），拿不到就是 null（"未知"，不是 0）。
			 * 只在 `onShow` 拉一次 —— 全仓库没有推送设施，也**不做全站轮询**（契约 §2.5 做法一）。
			 */
			unread: null,
			settings: [
				{ key: 'profile', label: '个人资料', desc: '昵称、手机号、头像' },
				{ key: 'remind', label: '提醒设置', desc: '提前几天与免打扰' },
				{ key: 'notify', label: '消息通知', desc: '评论、点赞、关注' },
				{ key: 'about', label: '关于喵汪', desc: '版本与开源许可' }
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
		 * 「我的」tab 的红点（自绘 tabbar 的 `mineDot` prop）：
		 * **唯一判据是未读数 > 0**；未读数拿不到（null）时按"没有"处理 → **失败静默降级**。
		 */
		mineDot() {
			return notificationHasUnread(this.unread)
		},
		/** 「消息通知」行右侧的角标（0 / 未读到 → 空串，不显示"0 条未读"） */
		unreadBadge() {
			return notificationUnreadText(this.unread)
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
		// 「我的」tab 的未读红点 / 「消息通知」行角标：**只在这一处拉一次**，不做全站轮询；
		// 失败静默降级（红点不显示、页面其余部分照常）
		if (auth.isLoggedIn()) this.loadUnread()
		else this.unread = null
	},
	methods: {
		/**
		 * 拉未读数（`GET /community/notification/unread-count`，**裸数字**）
		 * ⚠️ **失败一律静默降级**：红点不显示、角标不显示，**不弹 toast、不写红字**（未读数不是这一页的主内容）。
		 */
		async loadUnread() {
			try {
				const n = await fetchUnreadCount()
				const num = n === null || n === undefined ? NaN : Number(n)
				this.unread = isNaN(num) ? null : num
			} catch (e) {
				this.unread = null
			}
		},
		toast() {
			uni.showToast({ title: '功能开发中', icon: 'none' })
		},
		/**
		 * 设置行：现在 4 行**全部**有真实去处（个人资料 / 提醒设置 / 消息通知 / 关于喵汪）。
		 *
		 * 所以这个页面里**已经不存在"占位行"**了：原来那套 `isPlaceholder()` + 右侧「开发中」徽标
		 * 随「提醒设置」接线一起删掉 —— 4 行都有真页面，再挂「开发中」就是假 UI。
		 *
		 * 兜底的 `toast()` 保留，但**4 行都走不到它**：它的作用只是"以后谁加了第 5 行却忘了接页面，
		 * 点上去要有一个明确交代"，而不是给已接好的行当占位。
		 */
		openRow(s) {
			if (!s) return
			if (s.key === 'notify') {
				this.goNotify()
				return
			}
			if (s.key === 'profile') {
				uni.navigateTo({ url: '/pages/profile/profile' })
				return
			}
			if (s.key === 'remind') {
				uni.navigateTo({ url: '/pages/remind/setting' })
				return
			}
			if (s.key === 'about') {
				uni.navigateTo({ url: '/pages/about/about' })
				return
			}
			this.toast()
		},
		goNotify() {
			uni.navigateTo({ url: '/pages/notification/list' })
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
				// 退出后未读数不再适用（红点必须立刻消失，不能留上一个账号的）
				this.unread = null
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
	background: rgba(255, 138, 91, 0.16);
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
	background: rgba(194, 65, 12, 0.08);
	border: 1px solid rgba(194, 65, 12, 0.2);
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
	background: linear-gradient(0deg, rgba(194, 65, 12, 0.06), rgba(194, 65, 12, 0.06)), $mw-surface;
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
	background: rgba(194, 65, 12, 0.12);
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
/* 未读角标：这是**真数据**（未读数），不是占位徽标 —— 「开发中」那枚已随 4 行全部接线一起删掉 */
.row__badge--unread {
	background: rgba(229, 72, 77, 0.12);
	font-weight: 600;
	color: $mw-danger;
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