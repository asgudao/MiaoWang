<template>
	<view class="hpage">
		<view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav__back" @click="goBack"><view class="chev chev--left"></view></view>
			<text class="nav__title">用药</text>
			<text class="nav__act" @click="goNew">记一条</text>
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
					v-for="c in statusChips"
					:key="c.key"
					class="seg__item"
					:class="{ 'seg__item--on': c.on }"
					@click="selectStatus(c.value)"
				>{{ c.label }}</text>
			</view>

			<view v-if="loading && !rows.length" class="skeleton">
				<view class="skeleton__card"></view>
				<view class="skeleton__card"></view>
			</view>

			<view v-else-if="error && !rows.length" class="state">
				<text class="state__title">用药记录没读到</text>
				<text class="state__desc">{{ error }}</text>
				<view class="state__btn" @click="retry"><text>重试</text></view>
			</view>

			<view v-else-if="!rows.length" class="state">
				<text class="state__title">{{ emptyTitle }}</text>
				<text class="state__desc">{{ emptyDesc }}</text>
				<view class="state__btn" @click="goNew"><text>记一条用药</text></view>
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

				<view v-for="c in cards" :key="c.key" class="rec">
					<view class="rec__head">
						<text class="rec__title">{{ c.medicineName }}</text>
						<text class="tag" :class="'tag--' + c.tagTone">{{ c.statusLabel }}</text>
					</view>
					<text class="rec__sub">{{ c.subText }}</text>
					<view class="row">
						<text class="row__k">开始</text>
						<text class="row__v">{{ c.startText }}</text>
					</view>
					<view class="row" :class="{ 'row--last': !c.medicalText && !c.hasRemark }">
						<text class="row__k">结束</text>
						<text class="row__v">{{ c.hasEnd ? c.endText : '还没结束' }}</text>
					</view>
					<view v-if="c.medicalText" class="row" :class="{ 'row--last': !c.hasRemark }">
						<text class="row__k">关联</text>
						<text class="row__v">{{ c.medicalText }}</text>
					</view>
					<view v-if="c.hasRemark" class="row row--last">
						<text class="row__k">备注</text>
						<text class="row__v row__v--wrap">{{ c.remark }}</text>
					</view>
					<view class="rec__acts">
						<text class="rec__act" @click="goEdit(c.id)">编辑</text>
						<text
							v-if="c.isActive"
							class="rec__act"
							@click="endMedication(c.id)"
						>{{ putBusyId === c.id ? '提交中' : '标记已结束' }}</text>
						<text class="rec__act rec__act--danger" @click="askDelete(c.id)">{{ confirmingId === c.id ? '确认删除' : '删除' }}</text>
						<text v-if="confirmingId === c.id" class="rec__act" @click="confirmingId = null">取消</text>
					</view>
					<text v-if="confirmingId === c.id" class="hc__dim">删除是逻辑删除：列表里不再出现，原始数据仍留在库里。</text>
				</view>

				<text class="foot">{{ countText }}。标记已结束走的是编辑接口：PUT 是全量覆盖，所以前端先取全量再整体提交，不会把别的字段清掉。</text>
			</template>
		</template>
	</view>
</template>

<script>
/**
 * 用药列表（手机端 / Options API）
 *
 * 契约（实测）：
 * - 列表是**裸数组**；`?status=` 吃 0 / 1 / 2，**越界值按值过滤**（`status=99` → `data:[]`，不报错）
 *   → 这里只发 0/1/2，"全部"就不传
 * - `status` 不传时后端**由 `endAt` 推断**（`endAt==null` → 0 用药中，否则 1 已结束）；
 *   实测 id=2 被 PUT 成 `status:2`（已停用）
 * - ⚠️ **结束用药也走 PUT**，而 **PUT 是全量覆盖**（不传的字段会被置 null）→
 *   「标记已结束」必须**先用 `medicationFormFromDto` 读全量、再用 `medicationSaveReqFromForm` 整体提交**，
 *   绝不允许只发一个 `{status:1}`（那会把药名、用法、开始时间全清空）。
 */
import { auth } from '@/store/auth.js'
import { ApiError } from '@/utils/request.js'
import { listMedication, removeMedication, updateMedication } from '@/api/health.js'
import {
	healthErrorMessage,
	medicationCard,
	medicationFormFromDto,
	medicationSaveReqFromForm,
	nowHealthTime
} from '@/utils/health.js'

export default {
	data() {
		return {
			pid: 0,
			statusBarHeight: 20,
			/** '' = 全部（不传 status 参数） */
			activeStatus: '',
			rows: [],
			loading: false,
			loadedOnce: false,
			error: '',
			unauthorized: false,
			confirmingId: null,
			putBusyId: null,
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
		statusChips() {
			return [
				{ key: 'all', value: '', label: '全部', on: this.activeStatus === '' },
				{ key: 'active', value: 0, label: '用药中', on: this.activeStatus === 0 },
				{ key: 'ended', value: 1, label: '已结束', on: this.activeStatus === 1 },
				{ key: 'stopped', value: 2, label: '已停用', on: this.activeStatus === 2 }
			]
		},
		cards() {
			return this.rows.map(medicationCard)
		},
		emptyTitle() {
			if (this.activeStatus !== '') return '这个状态下没有用药记录'
			return '还没有用药记录'
		},
		emptyDesc() {
			if (this.activeStatus !== '') return '后端是按状态值过滤的，这个状态这次返回 0 条。切回「全部」看看。'
			return '接口是通的，这类记录还是空的。记一条（药名 + 开始时间）就会出现在这里。'
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
				const rows = await listMedication(this.pid, this.activeStatus === '' ? undefined : this.activeStatus)
				this.rows = Array.isArray(rows) ? rows : []
				this.loadedOnce = true
			} catch (e) {
				if (this.noteAuth(e)) return
				this.error = healthErrorMessage(e)
			} finally {
				this.loading = false
			}
		},

		selectStatus(value) {
			if (value === this.activeStatus) return
			this.activeStatus = value
			this.rows = []
			this.error = ''
			this.confirmingId = null
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

		goNew() {
			uni.navigateTo({ url: '/pages/health/record-edit?pid=' + this.pid + '&type=medication' })
		},

		goEdit(id) {
			uni.navigateTo({ url: '/pages/health/record-edit?pid=' + this.pid + '&type=medication&id=' + id })
		},

		/**
		 * 「标记已结束」：读全量 → 合并（status=1 + 补 endAt）→ **整体 PUT**
		 * ⚠️ 只发 `{status:1}` 是错的：PUT 全量覆盖，其余字段会被清成 null（实测过）。
		 */
		async endMedication(id) {
			const dto = this.rows.filter(function (r) { return r.id === id })[0]
			if (!dto) {
				this.actionError = '这条记录已经不在当前列表里了，刷新一次再试'
				return
			}
			this.actionError = ''
			this.putBusyId = id
			try {
				const form = medicationFormFromDto(dto)
				form.status = '1'
				if (!form.endAt) form.endAt = nowHealthTime()
				await updateMedication(id, medicationSaveReqFromForm(form))
				await this.load()
			} catch (e) {
				if (this.noteAuth(e)) return
				this.actionError = '标记失败：' + healthErrorMessage(e)
			} finally {
				this.putBusyId = null
			}
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
				await removeMedication(id)
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

.seg--page {
	padding: 0 $mw-page-padding;
}
</style>