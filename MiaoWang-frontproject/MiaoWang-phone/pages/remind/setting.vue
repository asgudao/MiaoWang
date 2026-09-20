<template>
	<view class="page">
		<!-- 全局 navigationStyle 是 custom，非 tab 页自绘返回栏（照 pages/notification/list.vue） -->
		<view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav__back" @click="goBack">
				<view class="chev chev--left"></view>
			</view>
			<text class="nav__title">提醒设置</text>
			<view class="nav__right"></view>
		</view>

		<view class="body">
			<!-- 未登录 / 会话过期：软门槛（健康接口经网关一律要身份，匿名请求必被拦） -->
			<view v-if="needLogin" class="state">
				<text class="state__title">先登录再设提醒</text>
				<text class="state__desc">照护提醒是从你的宠物健康记录里派生的，未登录读不到，也设不了。</text>
				<view class="state__btn" @click="goLogin"><text>去登录</text></view>
			</view>

			<template v-else>
				<!-- ⚠️ 诚实声明：本页设置只在本机生效，且这个项目没有服务端推送。
				     这两句必须留在页面上 —— 少一句就是把"打开 App 才检查"说成了"到点自动提醒"。 -->
				<view class="lede">
					<text class="lede__title">这些设置只在本机生效</text>
					<text class="lede__text">喵汪目前没有接服务端推送，这些开关只写进这台手机的本地存储，不会同步到账号、也不会上传。提醒是在你打开 App（或从后台切回来）时按你的健康记录现算的。</text>
					<text class="lede__text">{{ truthText }}</text>
				</view>

				<!-- ① 提醒总开关 -->
				<view class="card">
					<view class="row">
						<view class="row__main">
							<text class="row__title">照护提醒</text>
							<text class="row__desc">关掉后：首页不再显示待办与照护标记、不再弹提醒、也不再发本地通知</text>
						</view>
						<view class="tog" :class="{ 'tog--on': setting.enabled }" @click="toggleEnabled"><view class="tog__knob"></view></view>
					</view>
					<text class="row__now">当前：{{ enabledText }}</text>
				</view>

				<!-- ② 提前 N 天 -->
				<view class="card">
					<view class="row">
						<view class="row__main">
							<text class="row__title">提前几天提醒</text>
							<text class="row__desc">窗口口径与健康档案一致：逾期 + 到期当天 + 还有不超过 N 天。0 天就是原来的「逾期 + 今天到期」</text>
						</view>
						<picker mode="selector" :range="aheadLabels" :value="aheadIndex" @change="onAhead">
							<view class="pick"><text>{{ aheadText }}</text><text class="pick__arrow">›</text></view>
						</picker>
					</view>
					<text class="row__now">提醒时刻：到期日当天的 09:00 再往前 {{ setting.aheadDays }} 天（日级口径，和「下次到期天数」的分档保持一致）</text>
					<text class="row__now">按当前设置，本机现有 <text class="em">{{ board.todoCount }}</text> 项在提醒范围内</text>
				</view>

				<!-- ③ 免打扰时段 -->
				<view class="card">
					<view class="row">
						<view class="row__main">
							<text class="row__title">免打扰时段</text>
							<text class="row__desc">这时段内不弹提醒、也不发本地通知；时段过去后打开 App 会补上</text>
						</view>
						<view class="tog" :class="{ 'tog--on': setting.quietEnabled }" @click="toggleQuiet"><view class="tog__knob"></view></view>
					</view>
					<view class="times">
						<picker mode="time" :value="setting.quietStart" @change="onQuietStart">
							<view class="pick"><text>{{ setting.quietStart }}</text><text class="pick__arrow">›</text></view>
						</picker>
						<text class="times__mid">至</text>
						<picker mode="time" :value="setting.quietEnd" @change="onQuietEnd">
							<view class="pick"><text>{{ setting.quietEnd }}</text><text class="pick__arrow">›</text></view>
						</picker>
					</view>
					<text class="row__now">当前：{{ quietText }}</text>
					<text v-if="quietNote" class="row__warn">{{ quietNote }}</text>
				</view>

				<!-- ④ 能力与权限：做到什么、做不到什么，全部照实说 -->
				<view class="card">
					<text class="card__title">这台设备能不能弹系统通知</text>
					<text class="row__now">运行环境：{{ capPlatformText }}</text>
					<text class="row__now">系统通知：{{ capPermissionText }}</text>
					<text class="row__now">通知接口：{{ capApiText }}</text>
					<text class="row__note">{{ capNoteText }}</text>
					<view class="acts">
						<view v-if="canAskPermission" class="btn" @click="askPermission"><text>授权系统通知</text></view>
						<view class="btn" @click="testNotice"><text>发一条测试通知</text></view>
					</view>
					<text v-if="permMsg" class="row__warn">{{ permMsg }}</text>
				</view>

				<!-- ⑤ 这一次到底发生了什么（数字都要对得上，不糊成一句"检查完成"） -->
				<view class="card">
					<view class="row">
						<view class="row__main">
							<text class="row__title">本机的照护提醒</text>
							<text class="row__desc">来自健康记录的疫苗与驱虫「下次到期」。用药没有到期时间，所以不参与提醒</text>
						</view>
						<view class="btn btn--sm" @click="checkNow"><text>立即检查一次</text></view>
					</view>

					<text v-if="board.notice" class="row__warn">{{ board.notice }}</text>
					<text v-if="errorText" class="row__warn">{{ errorText }}</text>
					<text v-else-if="degradedText" class="row__warn">{{ degradedText }}</text>

					<view v-if="boardItems.length">
						<view v-for="it in boardItems" :key="it.key" class="item" @click="goHealth(it)">
							<view class="item__main">
								<text class="item__pet">{{ it.petName }}</text>
								<text class="item__title">{{ it.title }}</text>
								<text class="item__when">{{ it.fireStateLabel }}</text>
							</view>
							<text class="tag" :class="'tag--' + it.dueTone">{{ it.dueLabel }}</text>
							<text class="item__arrow">›</text>
						</view>
					</view>
					<text v-else-if="!errorText" class="row__note">{{ emptyText }}</text>

					<text v-if="runText" class="row__note">{{ runText }}</text>
				</view>

				<text class="foot">这页的设置改了会立刻生效：重算提醒、重排通知、并把首页的待办一起改掉。真机上的系统通知长什么样、会不会响，必须由你在 HBuilderX 里跑一次才算数。</text>
			</template>
		</view>
	</view>
</template>

<script>
import { AHEAD_DAY_OPTIONS, parseHm, readSetting } from '@/utils/remind-setting.js'
import {
	currentUsername,
	fetchRemindBoard,
	invalidateRemindPopup,
	notifyState,
	requestNotifyPermission,
	saveSetting,
	sendTestNotice,
	syncLocalNotifications
} from '@/api/remind.js'

const CAP_PLATFORM = { app: 'App（Android / iOS）', h5: '浏览器（H5）', unknown: '其它平台' }
const CAP_PERMISSION = {
	granted: '已授权',
	default: '还没授权（需要你点一下授权按钮）',
	denied: '被拒绝了（要去浏览器或系统设置里改）',
	system: '由系统管理（App 端没有申请权限的 JS 接口）',
	unsupported: '当前环境不支持',
	unknown: '未知'
}

export default {
	data() {
		return {
			statusBarHeight: 20,
			/** 是否已经进过本页（onShow 只在"非首次"时重算，避免首次重复拉一遍） */
			entered: false,
			loading: true,
			loggedIn: false,
			sessionExpired: false,
			errorText: '',
			busy: false,
			setting: {
				enabled: true,
				aheadDays: 0,
				quietEnabled: false,
				quietStart: '22:00',
				quietEnd: '08:00'
			},
			board: {
				items: [],
				pets: [],
				todoCount: 0,
				overdueCount: 0,
				suppressedCount: 0,
				degradedPets: [],
				notice: '',
				aheadDays: 0,
				enabled: true
			},
			cap: { platform: 'unknown', canNotify: false, supportsSchedule: false, permission: 'unknown', needsGesture: false, api: '', reason: '', note: '' },
			truth: '',
			permMsg: '',
			runText: ''
		}
	},
	computed: {
		/** 未登录软门槛 */
		needLogin() {
			return !this.loggedIn
		},
		aheadLabels() {
			return AHEAD_DAY_OPTIONS.map((o) => o.label)
		},
		aheadIndex() {
			const n = Number(this.setting.aheadDays)
			return n >= 0 && n < AHEAD_DAY_OPTIONS.length ? n : 0
		},
		aheadText() {
			return this.aheadLabels[this.aheadIndex] || '到期当天'
		},
		enabledText() {
			return this.setting.enabled ? '已开启' : '已关闭（首页待办、弹窗与通知都停用）'
		},
		quietText() {
			if (!this.setting.quietEnabled) return '不开启'
			const s = parseHm(this.setting.quietStart)
			const e = parseHm(this.setting.quietEnd)
			if (s === null || e === null) return '时段没设好'
			if (s === e) return '两端相同，这个时段不会生效'
			return s < e ? this.setting.quietStart + ' 至 ' + this.setting.quietEnd : this.setting.quietStart + ' 至次日 ' + this.setting.quietEnd
		},
		quietNote() {
			if (this.setting.quietEnabled && parseHm(this.setting.quietStart) === parseHm(this.setting.quietEnd)) {
				return '开始与结束时间一样，免打扰不会生效；请把结束时间设成另一个时刻'
			}
			return ''
		},
		canAskPermission() {
			return this.cap.platform === 'h5'
		},
		capPlatformText() {
			return CAP_PLATFORM[this.cap.platform] || CAP_PLATFORM.unknown
		},
		capPermissionText() {
			return CAP_PERMISSION[this.cap.permission] || CAP_PERMISSION.unknown
		},
		capApiText() {
			return this.cap.api || '没有可用的通知接口'
		},
		capNoteText() {
			return this.cap.note || ''
		},
		truthText() {
			return this.truth || ''
		},
		/** 摊平成模板能直接用的字段（模板里不许调 utils / store） */
		boardItems() {
			return this.board.items.map((it) => ({
				key: it.key,
				pid: it.pid,
				petName: it.petName,
				title: it.title,
				dueLabel: it.dueLabel,
				dueTone: it.dueTone,
				fireStateLabel: it.fireStateLabel
			}))
		},
		degradedText() {
			const names = this.board.degradedPets || []
			if (!names.length) return ''
			return names.join('、') + '的健康记录这次没读到，它的提醒没有算进来'
		},
		emptyText() {
			if (!this.board.enabled) {
				return this.board.suppressedCount
					? '提醒总开关关着：有 ' + this.board.suppressedCount + ' 项本来会提醒，现在不列也不通知'
					: '提醒总开关关着：不列待办、也不通知'
			}
			return '按当前设置（提前 ' + this.board.aheadDays + ' 天），没有到期的疫苗或驱虫需要提醒'
		}
	},
	onLoad() {
		try {
			const info = uni.getSystemInfoSync()
			this.statusBarHeight = info.statusBarHeight || 20
		} catch (e) {}
	},
	onShow() {
		if (this.entered) this.load()
		this.entered = true
	},
	mounted() {
		this.load()
	},
	methods: {
		async load() {
			this.loading = true
			const username = currentUsername()
			this.loggedIn = !!username
			if (!this.loggedIn) {
				this.loading = false
				return
			}
			this.setting = readSetting(username)
			const state = notifyState()
			this.cap = state.capability
			this.truth = state.truth
			await this.reloadBoard()
			this.loading = false
		},
		async reloadBoard() {
			this.errorText = ''
			try {
				this.board = await fetchRemindBoard()
			} catch (e) {
				this.errorText = (e && e.message) || '照护提醒没读出来，稍后再试'
			}
		},
		/** 改设置：**先落盘 → 作废弹窗缓存 → 重算看板**（三步缺一不可，否则改了不生效） */
		async apply(patch) {
			const username = currentUsername()
			if (!username) return
			this.setting = saveSetting(patch)
			invalidateRemindPopup()
			await this.reloadBoard()
		},
		toggleEnabled() {
			this.apply({ enabled: !this.setting.enabled })
		},
		toggleQuiet() {
			this.apply({ quietEnabled: !this.setting.quietEnabled })
		},
		onAhead(e) {
			const idx = Number(e && e.detail ? e.detail.value : 0)
			this.apply({ aheadDays: isNaN(idx) ? 0 : idx })
		},
		onQuietStart(e) {
			this.apply({ quietStart: (e && e.detail && e.detail.value) || this.setting.quietStart })
		},
		onQuietEnd(e) {
			this.apply({ quietEnd: (e && e.detail && e.detail.value) || this.setting.quietEnd })
		},
		/** 申请系统通知权限（H5 才有这个 API；必须在用户点击里调） */
		async askPermission() {
			const r = await requestNotifyPermission()
			this.permMsg = r.message
			const state = notifyState()
			this.cap = state.capability
		},
		/** 真发一条（走的是和到期提醒完全相同的发送路径，不是弹个 toast 骗人） */
		async testNotice() {
			if (this.busy) return
			this.busy = true
			try {
				const r = await sendTestNotice()
				this.permMsg = r.ok ? '已发出：' + r.message : '没发出去：' + r.message
			} finally {
				this.busy = false
			}
		},
		/** 立即重算并走一次发送/排程（等于手动触发一次"回前台"） */
		async checkNow() {
			if (this.busy) return
			this.busy = true
			this.runText = ''
			try {
				const run = await syncLocalNotifications()
				await this.reloadBoard()
				const c = { quiet: 0, duplicate: 0, off: 0, notYet: 0 }
				for (let i = 0; i < run.decisions.length; i++) {
					const r = run.decisions[i].reason
					if (c[r] !== undefined) c[r]++
				}
				this.runText =
					'本次检查：发出 ' + run.sent + ' 条、排给系统 ' + run.scheduled + ' 条' +
					'（免打扰挡下 ' + c.quiet + ' 条、今天已提醒过 ' + c.duplicate + ' 条、还没到点 ' + c.notYet + ' 条）'
			} catch (e) {
				this.errorText = (e && e.message) || '检查失败'
			} finally {
				this.busy = false
			}
		},
		goHealth(it) {
			const pid = it && it.pid ? String(it.pid) : ''
			if (!pid) return
			uni.navigateTo({ url: '/pages/health/health?pid=' + pid })
		},
		goLogin() {
			uni.navigateTo({ url: '/pages/login/login' })
		},
		goBack() {
			uni.navigateBack()
		}
	}
}
</script>

<style lang="scss" scoped>
/* 令牌全部取自 uni.scss，**不新增任何令牌值** */
.page {
	min-height: 100vh;
	background: $mw-canvas;
}
.nav {
	display: flex;
	align-items: center;
	padding: 0 $mw-page-padding $mw-space-2;
	background: $mw-canvas;
}
.nav__back {
	width: $mw-touch-min;
	height: $mw-touch-min;
	display: flex;
	align-items: center;
}
.nav__title {
	flex: 1;
	text-align: center;
	font-size: $mw-font-h2;
	font-weight: 600;
	color: $mw-ink;
}
.nav__right {
	width: $mw-touch-min;
}
.chev {
	width: 20rpx;
	height: 20rpx;
	border-left: 4rpx solid $mw-ink;
	border-bottom: 4rpx solid $mw-ink;
}
.chev--left {
	transform: rotate(45deg);
}

.body {
	padding: 0 $mw-page-padding $mw-space-8;
}

.lede {
	padding: $mw-space-3;
	margin-bottom: $mw-space-3;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-left: 6rpx solid $mw-accent;
	border-radius: $mw-radius-card;
}
.lede__title {
	display: block;
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-ink;
}
.lede__text {
	display: block;
	margin-top: $mw-space-1;
	font-size: $mw-font-micro;
	color: $mw-ink-2;
}

.card {
	margin-bottom: $mw-space-3;
	padding: $mw-space-4;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
.card__title {
	display: block;
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-ink;
}
.row {
	display: flex;
	align-items: center;
}
.row__main {
	flex: 1;
	min-width: 0;
}
.row__title {
	display: block;
	font-size: $mw-font-body;
	color: $mw-ink;
}
.row__desc {
	display: block;
	margin-top: 4rpx;
	font-size: $mw-font-micro;
	color: $mw-muted;
}
.row__now {
	display: block;
	margin-top: $mw-space-2;
	font-size: $mw-font-micro;
	color: $mw-ink-2;
}
.row__warn {
	display: block;
	margin-top: $mw-space-2;
	font-size: $mw-font-micro;
	color: $mw-danger;
}
.row__note {
	display: block;
	margin-top: $mw-space-2;
	font-size: $mw-font-micro;
	color: $mw-muted;
}
.em {
	color: $mw-accent-ink;
	font-weight: 600;
}

/* 自绘开关：不用原生 switch（它的选中色没法用令牌，会跟设计系统打架） */
.tog {
	width: 96rpx;
	height: 56rpx;
	border-radius: $mw-radius-pill;
	background: $mw-line;
	padding: 4rpx;
	display: flex;
	align-items: center;
}
.tog--on {
	background: $mw-brand-ink;
	justify-content: flex-end;
}
.tog__knob {
	width: 48rpx;
	height: 48rpx;
	border-radius: 50%;
	background: $mw-surface;
}

.pick {
	min-width: 180rpx;
	min-height: 72rpx;
	padding: 0 $mw-space-2;
	display: flex;
	align-items: center;
	justify-content: flex-end;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-input;
	font-size: $mw-font-caption;
	color: $mw-ink;
}
.pick__arrow {
	margin-left: $mw-space-1;
	color: $mw-muted;
}
.times {
	display: flex;
	align-items: center;
	margin-top: $mw-space-3;
}
.times__mid {
	margin: 0 $mw-space-2;
	font-size: $mw-font-micro;
	color: $mw-muted;
}

.acts {
	display: flex;
	margin-top: $mw-space-3;
}
.btn {
	flex: 1;
	height: $mw-btn-h;
	margin-right: $mw-space-2;
	display: flex;
	align-items: center;
	justify-content: center;
	border: 1px solid $mw-brand-ink;
	border-radius: $mw-radius-pill;
	font-size: $mw-font-caption;
	color: $mw-brand-ink;
}
.btn:last-child {
	margin-right: 0;
}
.btn--sm {
	flex: 0 0 auto;
	height: 72rpx;
	padding: 0 $mw-space-3;
	border-radius: $mw-radius-input;
}

.item {
	display: flex;
	align-items: center;
	min-height: $mw-touch-min;
	border-bottom: 1px solid $mw-line;
}
.item__main {
	flex: 1;
	min-width: 0;
}
.item__pet {
	display: block;
	font-size: $mw-font-micro;
	color: $mw-muted;
}
.item__title {
	display: block;
	font-size: $mw-font-caption;
	color: $mw-ink;
}
.item__when {
	display: block;
	font-size: $mw-font-micro;
	color: $mw-muted;
}
.item__arrow {
	margin-left: $mw-space-1;
	font-size: 36rpx;
	color: $mw-muted;
}
/* 分档徽标色档直接取 dueInfo 的 tone，不另定一套 */
.tag {
	margin-left: $mw-space-2;
	padding: 2rpx $mw-space-1;
	border-radius: $mw-radius-tag;
	background: $mw-canvas;
	font-size: $mw-font-micro;
	color: $mw-ink-2;
}
.tag--danger {
	color: $mw-danger;
}
.tag--accent {
	color: $mw-accent-ink;
}
.tag--muted {
	color: $mw-muted;
}
.tag--ink {
	color: $mw-ink-2;
}

.foot {
	display: block;
	margin-top: $mw-space-4;
	font-size: 22rpx;
	color: $mw-muted;
}
</style>