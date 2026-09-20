<template>
	<view class="hpage">
		<view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav__back" @click="goBack"><view class="chev chev--left"></view></view>
			<text class="nav__title">症状日记</text>
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
				<text class="state__title">症状记录没读到</text>
				<text class="state__desc">{{ error }}</text>
				<view class="state__btn" @click="retry"><text>重试</text></view>
			</view>

			<view v-else-if="!rows.length" class="state">
				<text class="state__title">还没有症状记录</text>
				<text class="state__desc">接口是通的，这只宠物名下这一类还是空的。记一条症状，攒几条之后可以在健康概览里做 AI 解读。</text>
				<view class="state__btn" @click="goNew"><text>记一条症状</text></view>
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
					<text>名字有两种来源：从症状库选的（技能库里猫 52 条 / 狗 46 条）与自己填写的。传了症状库编号，后端会用库里的名字覆盖自填的名字，所以选了库就别指望自填名生效。</text>
				</view>

				<view v-for="c in cards" :key="c.key" class="rec">
					<view class="rec__head">
						<text class="rec__title">{{ c.symptomName }}</text>
						<text class="tag" :class="'tag--' + c.tagTone">{{ c.severityLabel }}</text>
					</view>
					<text class="rec__sub">{{ c.sourceLabel }}，发生时间 {{ c.occurredText }}</text>
					<view v-if="c.hasDescription" class="row">
						<text class="row__k">表现</text>
						<text class="row__v row__v--wrap">{{ c.description }}</text>
					</view>
					<view v-if="c.hasRemark" class="row row--last">
						<text class="row__k">备注</text>
						<text class="row__v row__v--wrap">{{ c.remark }}</text>
					</view>

					<view v-if="c.hasImages" class="shots">
						<text class="shots__label">照片 {{ c.imageCount }} 张（后端出参是数组）</text>
						<view class="shots__grid">
							<image
								v-for="(u, ui) in c.images"
								:key="ui"
								class="shots__img"
								:src="u"
								mode="aspectFill"
								@click="preview(c.images, ui)"
							/>
						</view>
					</view>

					<view class="rec__acts">
						<text class="rec__act" @click="goEdit(c.id)">编辑</text>
						<text class="rec__act rec__act--danger" @click="askDelete(c.id)">{{ confirmingId === c.id ? '确认删除' : '删除' }}</text>
						<text v-if="confirmingId === c.id" class="rec__act" @click="confirmingId = null">取消</text>
					</view>
					<text v-if="confirmingId === c.id" class="hc__dim">删除是逻辑删除：列表里不再出现，原始数据仍留在库里。</text>
				</view>

				<text class="foot">{{ countText }}。AI 解读入口在健康概览页（它取最近 10 条症状拼成一个问题）。</text>
			</template>
		</template>
	</view>
</template>

<script>
/**
 * 症状日记列表（手机端 / Options API）
 *
 * 契约（实测）：
 * - 列表是**裸数组**，按 `occurredAt` 倒序
 * - ⚠️ **传 `symptomId` 会覆盖 `symptomName`**（POST 与 PUT 都成立，实测 307 → 「食欲突然大增」）；
 *   不传 `symptomId` 时自填名原样保留（实测 id=2「偶尔打喷嚏」，`symptomId` 回 `null`）
 * - 非法 `symptomId` → `B000409`（**不会**退化成用 `symptomName`，也不落库）
 * - ⚠️ `images` 出参是数组、入参是逗号串；没有图时是 **`[]` 不是 `null`**
 * - 症状选项接口（`/health/symptom/options`）**经网关也要登录**（实测匿名回 `{state:-1}`），
 *   所以表单页不是"免登录也能选"，而是必须先有登录身份
 */
import { auth } from '@/store/auth.js'
import { ApiError } from '@/utils/request.js'
import { listSymptom, removeSymptom } from '@/api/health.js'
import { healthErrorMessage, symptomCard } from '@/utils/health.js'

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
			return this.rows.map(symptomCard)
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
				const rows = await listSymptom(this.pid)
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
			uni.navigateTo({ url: '/pages/health/record-edit?pid=' + this.pid + '&type=symptom' })
		},

		goEdit(id) {
			uni.navigateTo({ url: '/pages/health/record-edit?pid=' + this.pid + '&type=symptom&id=' + id })
		},

		preview(urls, index) {
			if (!urls || !urls.length) return
			uni.previewImage({ urls: urls, current: index })
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
				await removeSymptom(id)
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

.shots {
	margin-top: $mw-space-3;
}
.shots__label {
	display: block;
	margin-bottom: $mw-space-2;
	font-size: $mw-font-micro;
	color: $mw-ink-2;
}
.shots__grid {
	display: flex;
	flex-wrap: wrap;
}
.shots__img {
	width: 148rpx;
	height: 148rpx;
	margin: 0 $mw-space-2 $mw-space-2 0;
	border-radius: $mw-radius-input;
	background: $mw-canvas;
}
</style>