<template>
	<view class="hpage">
		<view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav__back" @click="goBack"><view class="chev chev--left"></view></view>
			<text class="nav__title">过敏史</text>
			<text class="nav__act" @click="goNew">记一条</text>
		</view>

		<view v-if="showGate" class="state">
			<text class="state__title">{{ sessionExpired ? '登录已过期' : '要登录才能看健康档案' }}</text>
			<text class="state__desc">{{ gateDesc }}</text>
			<view class="state__btn" @click="goLogin"><text>去登录</text></view>
			<view class="state__ghost" @click="retry"><text>我已经登录了，重新读取</text></view>
		</view>

		<template v-else>
			<view v-if="loading && !rows.length" class="skeleton">
				<view class="skeleton__card"></view>
				<view class="skeleton__card"></view>
			</view>

			<view v-else-if="error && !rows.length" class="state">
				<text class="state__title">过敏史没读到</text>
				<text class="state__desc">{{ error }}</text>
				<view class="state__btn" @click="retry"><text>重试</text></view>
			</view>

			<view v-else-if="!rows.length" class="state">
				<text class="state__title">还没有过敏记录</text>
				<text class="state__desc">接口是通的，这只宠物名下这一类还是空的。把已知的过敏原写下来，换粮换药时能少踩坑。</text>
				<view class="state__btn" @click="goNew"><text>记一条过敏</text></view>
				<view class="state__ghost" @click="retry"><text>重新读取</text></view>
			</view>

			<template v-else>
				<view v-if="error" class="inline-err">
					<text class="inline-err__text">{{ error }}</text>
					<text class="inline-err__act" @click="retry">重试</text>
				</view>
				<view v-if="actionError" class="inline-err">
					<text class="inline-err__text">{{ actionError }}</text>
					<text class="inline-err__act" @click="actionError = ''">知道了</text>
				</view>

				<view class="notice notice--plain">
					<text>过敏表没有「发生时间」这一列，所以时间线上的过敏条目用的是录入时间，按时间排序时会全部堆在录入那天，这是后端的数据结构决定的。</text>
				</view>

				<view v-for="c in cards" :key="c.key" class="rec">
					<view class="rec__head">
						<text class="rec__title">{{ c.allergen }}</text>
						<text class="tag" :class="'tag--' + c.tagTone">{{ c.severityLabel }}</text>
					</view>
					<text class="rec__sub">{{ c.subText }}</text>
					<view v-if="c.hasNote" class="row row--last">
						<text class="row__k">注意</text>
						<text class="row__v row__v--wrap">{{ c.note }}</text>
					</view>
					<view class="rec__acts">
						<text class="rec__act" @click="goEdit(c.id)">编辑</text>
						<text class="rec__act rec__act--danger" @click="askDelete(c.id)">{{ confirmingId === c.id ? '确认删除' : '删除' }}</text>
						<text v-if="confirmingId === c.id" class="rec__act" @click="confirmingId = null">取消</text>
					</view>
					<text v-if="confirmingId === c.id" class="hc__dim">删除是逻辑删除：列表里不再出现，原始数据仍留在库里。</text>
				</view>

				<text class="foot">{{ countText }}。列表由后端按严重度倒序给（重度在前），前端不再排一遍。</text>
			</template>
		</template>
	</view>
</template>

<script>
/**
 * 过敏史列表（手机端 / Options API）
 *
 * 契约（实测）：
 * - 列表是**裸数组**，由后端按 `severity` **倒序**给（重度在前，实测 `2/中度` 在 `1/轻度` 前）
 * - `severity` 1/2/3 ↔ `severityDesc` 轻度/中度/重度，**优先用后端的 `severityDesc`**
 * - ⚠️ **过敏表没有"发生时间"列** → 时间线里过敏的 `happenedAt` 是 `create_time`（录入时间），
 *   按时间排序会"全部堆在录入那天"（实测 `2026-09-20 10:20:07`）→ 卡片上不假装它有时间
 */
import { auth } from '@/store/auth.js'
import { ApiError } from '@/utils/request.js'
import { listAllergy, removeAllergy } from '@/api/health.js'
import { allergyCard, healthErrorMessage } from '@/utils/health.js'

export default {
	data() {
		return {
			pid: 0,
			statusBarHeight: 20,
			rows: [],
			loading: false,
			loadedOnce: false,
			error: '',
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
		cards() {
			return this.rows.map(allergyCard)
		},
		countText() {
			return '这次读到 ' + this.rows.length + ' 条'
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
		this.load()
	},
	onShow() {
		if (this.loggedIn && this.loadedOnce && !this.loading) this.load()
	},
	methods: {
		async load() {
			if (!this.loggedIn) return
			this.loading = true
			this.error = ''
			this.unauthorized = false
			try {
				const rows = await listAllergy(this.pid)
				this.rows = Array.isArray(rows) ? rows : []
				this.loadedOnce = true
			} catch (e) {
				if (this.noteAuth(e)) return
				this.error = healthErrorMessage(e)
			} finally {
				this.loading = false
			}
		},

		retry() {
			if (!this.loggedIn) {
				uni.navigateTo({ url: '/pages/login/login' })
				return
			}
			this.load()
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
			uni.navigateTo({ url: '/pages/health/record-edit?pid=' + this.pid + '&type=allergy' })
		},

		goEdit(id) {
			uni.navigateTo({ url: '/pages/health/record-edit?pid=' + this.pid + '&type=allergy&id=' + id })
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
				await removeAllergy(id)
				this.rows = this.rows.filter(function (r) { return r.id !== id })
			} catch (e) {
				if (this.noteAuth(e)) return
				this.actionError = '删除失败：' + healthErrorMessage(e)
			}
		},

		noteAuth(e) {
			if (!(e instanceof ApiError) || !e.isUnauthorized) return false
			this.unauthorized = true
			this.error = ''
			this.loading = false
			return true
		}
	}
}
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
@import '@/styles/health.scss';

.notice--plain {
	background: $mw-surface;
	border-color: $mw-line;
}
.notice--plain text {
	color: $mw-ink-2;
}
</style>