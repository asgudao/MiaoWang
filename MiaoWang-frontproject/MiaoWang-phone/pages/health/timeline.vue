<template>
	<view class="hpage">
		<view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav__back" @click="goBack"><view class="chev chev--left"></view></view>
			<text class="nav__title">健康时间线</text>
			<text class="nav__act" @click="load">刷新</text>
		</view>

		<view v-if="showGate" class="state">
			<text class="state__title">{{ sessionExpired ? '登录已过期' : '要登录才能看健康档案' }}</text>
			<text class="state__desc">{{ gateDesc }}</text>
			<view class="state__btn" @click="goLogin"><text>去登录</text></view>
			<view class="state__ghost" @click="retry"><text>我已经登录了，重新读取</text></view>
		</view>

		<template v-else>
			<view class="filters">
				<text class="filters__label">看哪几类</text>
				<view class="seg">
					<text
						v-for="c in typeChips"
						:key="c.key"
						class="seg__item"
						:class="{ 'seg__item--on': c.on }"
						@click="toggleType(c.value)"
					>{{ c.label }}</text>
				</view>
				<text class="filters__label">看多少条</text>
				<view class="seg">
					<text
						v-for="l in limitChips"
						:key="l.value"
						class="seg__item"
						:class="{ 'seg__item--on': l.on }"
						@click="selectLimit(l.value)"
					>{{ l.label }}</text>
				</view>
				<text class="filters__hint">{{ filterHint }}</text>
			</view>

			<view v-if="loading && !rows.length" class="skeleton">
				<view class="skeleton__card"></view>
				<view class="skeleton__card"></view>
			</view>

			<view v-else-if="error && !rows.length" class="state">
				<text class="state__title">时间线没读到</text>
				<text class="state__desc">{{ error }}</text>
				<view class="state__btn" @click="retry"><text>重试</text></view>
			</view>

			<view v-else-if="!rows.length" class="state">
				<text class="state__title">{{ emptyTitle }}</text>
				<text class="state__desc">{{ emptyDesc }}</text>
				<view class="state__btn" @click="clearFilters"><text>清掉筛选再看一次</text></view>
			</view>

			<template v-else>
				<view v-if="error" class="inline-err">
					<text class="inline-err__text">{{ error }}</text>
					<text class="inline-err__act" @click="retry">重试</text>
				</view>

				<view v-for="c in cards" :key="c.key" class="tl">
					<view class="tl__rail">
						<view class="tl__dot" :class="'tl__dot--' + c.dotTone"></view>
						<view class="tl__line"></view>
					</view>
					<view class="tl__body">
						<view class="tl__head">
							<text class="tl__title">{{ c.title }}</text>
							<text v-if="c.hasSeverity" class="tag" :class="'tag--' + c.tagTone">{{ c.severityLabel }}</text>
						</view>
						<text class="tl__meta">{{ c.recordTypeLabel }} {{ c.happenedText }}</text>
						<text v-if="c.hasSummary" class="tl__summary">{{ c.summary }}</text>
					</view>
				</view>

				<text class="foot">{{ countText }}。六类记录合并按时间倒序，同一条记录的类型编号可能重复，所以列表用「类型 + 编号」当唯一键。</text>
			</template>
		</template>
	</view>
</template>

<script>
/**
 * 健康时间线（手机端 / Options API）
 *
 * 契约（实测）：
 * - 返回**裸数组**，六类合并、时间倒序；`limit` 钳到 [1,200]（`0`≡`1`，默认 50）
 * - ⚠️ **三个筛选参数三种行为**，这里踩的是最凶的那个：
 *   `timeline?types=` **非法值直接报 `A000001`**（消息「不支持的记录类型：BOGUS」），
 *   **不是**静默忽略（`preventive?type` 才是静默忽略、`medication?status` 是越界按值过滤）。
 *   所以选项由 `RECORD_TYPES` 生成，发请求前再用 `validateTimelineTypes` 校验一遍，
 *   绝不把用户拼出来的非法串发给后端。
 * - ⚠️ `recordId` 是各类表各自的 id（**会重复**：`allergy#2, allergy#1, weight#111...`）
 *   → `v-for` 的 key 必须用 `recordType + recordId`
 * - 只有 `allergy` / `symptom` 的 `severity` 有值；`allergy` 的 `happenedAt` 是**录入时间**
 * - `types` 大小写不敏感（这里统一发小写）
 */
import { auth } from '@/store/auth.js'
import { ApiError } from '@/utils/request.js'
import { timeline } from '@/api/health.js'
import {
	RECORD_TYPES,
	healthErrorMessage,
	recordTypeLabel,
	timelineCard,
	validateTimelineTypes
} from '@/utils/health.js'

/** 时间线条数档（后端默认 50 / 上限 200） */
const LIMITS = [10, 20, 50, 200]

export default {
	data() {
		return {
			pid: 0,
			statusBarHeight: 20,
			/** 空数组 = 不传 types（全都要） */
			selectedTypes: [],
			limit: 20,
			rows: [],
			loading: false,
			loadedOnce: false,
			error: '',
			unauthorized: false
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
		typeChips() {
			const list = [
				{ key: 'all', value: '', label: '全部', on: this.selectedTypes.length === 0 }
			]
			for (let i = 0; i < RECORD_TYPES.length; i++) {
				const t = RECORD_TYPES[i]
				list.push({
					key: t.value,
					value: t.value,
					label: t.label,
					on: this.selectedTypes.indexOf(t.value) >= 0
				})
			}
			return list
		},
		limitChips() {
			const out = []
			for (let i = 0; i < LIMITS.length; i++) {
				out.push({
					value: LIMITS[i],
					label: LIMITS[i] === 200 ? '200 条（上限）' : LIMITS[i] + ' 条',
					on: this.limit === LIMITS[i]
				})
			}
			return out
		},
		/** 实际发给后端的 types 串（空串 = 不传参数） */
		typesParam() {
			return this.selectedTypes.join(',')
		},
		filterHint() {
			if (!this.selectedTypes.length) return '现在看的是全部六类，按时间倒序。'
			const labels = []
			for (let i = 0; i < this.selectedTypes.length; i++) {
				labels.push(recordTypeLabel(this.selectedTypes[i]))
			}
			return '只看：' + labels.join('、') + '。后端对非法类型名会直接报错，所以选项是写死的六个。'
		},
		cards() {
			const out = []
			for (let i = 0; i < this.rows.length; i++) {
				const c = timelineCard(this.rows[i])
				c.tagTone = c.hasSeverity ? c.severityTone === 'high' ? 'danger' : c.severityTone === 'mid' ? 'accent' : 'brand' : 'muted'
				c.dotTone = c.hasSeverity && c.severityTone === 'high' ? 'danger' : c.recordType === 'weight' ? 'accent' : 'brand'
				out.push(c)
			}
			return out
		},
		emptyTitle() {
			return this.selectedTypes.length ? '这几类这段时间没有记录' : '还没有任何健康记录'
		},
		emptyDesc() {
			if (this.selectedTypes.length) return '接口是通的，这次返回 0 条。把筛选清掉看全部，或者去记一条。'
			return '接口是通的，这只宠物名下六类记录都还是空的。先记一条疫苗、体重或症状，时间线就有内容了。'
		},
		countText() {
			return '这次读到 ' + this.rows.length + ' 条（上限 ' + this.limit + '）'
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
			// ⚠️ 发请求前先自检：非法类型名后端会回 A000001，锅在前端不在用户
			const bad = validateTimelineTypes(this.typesParam)
			if (bad) {
				this.error = bad
				this.rows = []
				return
			}
			this.loading = true
			this.error = ''
			this.unauthorized = false
			try {
				const rows = await timeline(this.pid, {
					types: this.typesParam || undefined,
					limit: this.limit
				})
				this.rows = Array.isArray(rows) ? rows : []
				this.loadedOnce = true
			} catch (e) {
				if (this.noteAuth(e)) return
				this.error = healthErrorMessage(e)
			} finally {
				this.loading = false
			}
		},

		/** 多选：点已选中的就取消它；点「全部」清空全部选择 */
		toggleType(value) {
			if (value === '') {
				if (!this.selectedTypes.length) return
				this.selectedTypes = []
			} else {
				const next = []
				let removed = false
				for (let i = 0; i < this.selectedTypes.length; i++) {
					if (this.selectedTypes[i] === value) {
						removed = true
						continue
					}
					next.push(this.selectedTypes[i])
				}
				if (!removed) next.push(value)
				this.selectedTypes = next
			}
			this.load()
		},

		selectLimit(value) {
			if (value === this.limit) return
			this.limit = value
			this.load()
		},

		clearFilters() {
			this.selectedTypes = []
			this.limit = 20
			this.load()
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

.filters {
	margin: 0 $mw-page-padding $mw-space-3;
	padding: $mw-space-3 $mw-space-3 0;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
.filters__label {
	display: block;
	margin-bottom: $mw-space-2;
	font-size: $mw-font-micro;
	color: $mw-ink-2;
}
.filters__hint {
	display: block;
	padding: $mw-space-2 0 $mw-space-3;
	font-size: 22rpx;
	line-height: 1.6;
	color: $mw-muted;
}

/* 时间线：左边一条竖轴 + 圆点 */
.tl {
	display: flex;
	margin: 0 $mw-page-padding;
}
.tl__rail {
	width: 32rpx;
	display: flex;
	flex-direction: column;
	align-items: center;
	flex: none;
}
.tl__dot {
	width: 16rpx;
	height: 16rpx;
	margin-top: 12rpx;
	border-radius: 50%;
	background: $mw-brand-ink;
	flex: none;
}
.tl__dot--danger {
	background: $mw-danger;
}
.tl__dot--accent {
	background: $mw-accent;
}
.tl__line {
	flex: 1;
	width: 2rpx;
	margin-top: 6rpx;
	background: $mw-line;
}
.tl__body {
	flex: 1;
	min-width: 0;
	margin-left: $mw-space-2;
	padding-bottom: $mw-space-3;
}
.tl__head {
	display: flex;
	align-items: center;
}
.tl__title {
	flex: 1;
	min-width: 0;
	font-size: $mw-font-body;
	font-weight: 600;
	color: $mw-ink;
}
.tl__meta {
	display: block;
	margin-top: 2rpx;
	font-size: 22rpx;
	color: $mw-muted;
}
.tl__summary {
	display: block;
	margin-top: $mw-space-1;
	font-size: $mw-font-caption;
	line-height: 1.7;
	color: $mw-ink-2;
}
</style>