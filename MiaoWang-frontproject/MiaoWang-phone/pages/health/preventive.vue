<template>
	<view class="hpage">
		<view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav__back" @click="goBack"><view class="chev chev--left"></view></view>
			<text class="nav__title">疫苗与驱虫</text>
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
					v-for="c in typeChips"
					:key="c.value"
					class="seg__item"
					:class="{ 'seg__item--on': c.on }"
					@click="selectType(c.value)"
				>{{ c.label }}</text>
			</view>

			<view v-if="loading && !rows.length" class="skeleton">
				<view class="skeleton__card"></view>
				<view class="skeleton__card"></view>
			</view>

			<view v-else-if="error && !rows.length" class="state">
				<text class="state__title">疫苗与驱虫记录没读到</text>
				<text class="state__desc">{{ error }}</text>
				<view class="state__btn" @click="retry"><text>重试</text></view>
			</view>

			<view v-else-if="!rows.length" class="state">
				<text class="state__title">{{ emptyTitle }}</text>
				<text class="state__desc">{{ emptyDesc }}</text>
				<view class="state__btn" @click="goNew"><text>记第一条</text></view>
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

				<!-- 逾期统计：overview.nextPreventive 拿不到逾期项，所以这里也自己数一遍 -->
				<view v-if="overdueCount" class="notice">
					<text>有 {{ overdueCount }} 条已经逾期：{{ overdueNames }}。列表里带红标签的就是它们</text>
				</view>

				<view v-for="c in cards" :key="c.key" class="rec">
					<view class="rec__head">
						<text class="rec__title">{{ c.itemName }}</text>
						<text class="tag" :class="'tag--' + c.tagTone">{{ c.dueLabel }}</text>
					</view>
					<text class="rec__sub">{{ c.typeLabel }} {{ c.happenedText }}</text>
					<view class="row">
						<text class="row__k">下次到期</text>
						<text class="row__v">{{ c.nextDueText }}</text>
					</view>
					<view v-if="c.hasHospital" class="row">
						<text class="row__k">医院</text>
						<text class="row__v">{{ c.hospital }}</text>
					</view>
					<view v-if="c.hasBatchNo" class="row">
						<text class="row__k">批号</text>
						<text class="row__v">{{ c.batchNo }}</text>
					</view>
					<view v-if="c.hasRemark" class="row row--last">
						<text class="row__k">备注</text>
						<text class="row__v row__v--wrap">{{ c.remark }}</text>
					</view>
					<text v-if="c.dueDetail" class="hc__dim">{{ c.dueDetail }}</text>
					<view class="rec__acts">
						<text class="rec__act" @click="goEdit(c.id)">编辑</text>
						<text class="rec__act rec__act--danger" @click="askDelete(c.id)">{{ confirmingId === c.id ? '确认删除' : '删除' }}</text>
						<text v-if="confirmingId === c.id" class="rec__act" @click="confirmingId = null">取消</text>
					</view>
					<text v-if="confirmingId === c.id" class="hc__dim">删除是逻辑删除：列表里不再出现，原始数据仍留在库里。</text>
				</view>

				<text class="foot">{{ countText }}。时间窗、类型过滤都由后端算，前端只负责显示与逾期分档。</text>
			</template>
		</template>
	</view>
</template>

<script>
/**
 * 疫苗与驱虫列表（手机端 / Options API）
 *
 * 契约（实测）：
 * - 列表是**裸数组**（没有分页信封）
 * - `?type=` 只吃 `VACCINE` / `DEWORM_IN` / `DEWORM_OUT`；**非法值后端静默忽略**（返回全部），
 *   大小写不敏感 → 这里只发这三个合法值，"全部"就不传
 * - ⚠️ 逾期项只能自己算：`nextDueDays < 0` 就是已逾期（实测 `-10`），`0` 是今天，`> 0` 是还有 N 天；
 *   `nextDueAt` 为空时该字段是 `null` → 先判空再比大小（`utils/health.js` 的 `dueInfo` 已经做了）
 * - ⚠️ PUT 全量覆盖：编辑走独立的表单页，它先读全量再整体 PUT
 * - 删除是**逻辑删除**；本端真调过一次自己新建的测试记录（成功分支已实测），失败按 `B000402` 显示原因
 */
import { auth } from '@/store/auth.js'
import { ApiError } from '@/utils/request.js'
import { listPreventive, removePreventive } from '@/api/health.js'
import { healthErrorMessage, overdueItems, preventiveCard } from '@/utils/health.js'

export default {
	data() {
		return {
			pid: 0,
			statusBarHeight: 20,
			/** '' = 全部（不传 type 参数） */
			activeType: '',
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
		typeChips() {
			return [
				{ value: '', label: '全部', on: this.activeType === '' },
				{ value: 'VACCINE', label: '疫苗', on: this.activeType === 'VACCINE' },
				{ value: 'DEWORM_IN', label: '体内驱虫', on: this.activeType === 'DEWORM_IN' },
				{ value: 'DEWORM_OUT', label: '体外驱虫', on: this.activeType === 'DEWORM_OUT' }
			]
		},
		cards() {
			const out = []
			for (let i = 0; i < this.rows.length; i++) {
				const c = preventiveCard(this.rows[i])
				c.tagTone = c.isOverdue ? 'danger' : c.dueKey === 'today' || c.dueKey === 'soon' ? 'accent' : 'muted'
				out.push(c)
			}
			return out
		},
		overdueCount() {
			return overdueItems(this.rows).length
		},
		overdueNames() {
			const names = []
			const list = overdueItems(this.rows)
			for (let i = 0; i < list.length && i < 3; i++) {
				names.push(list[i].itemName || '未填写项目名')
			}
			return names.join('、')
		},
		emptyTitle() {
			if (this.activeType) return '这个类型还没有记录'
			return '还没有疫苗或驱虫记录'
		},
		emptyDesc() {
			if (this.activeType) return '接口这次返回 0 条。切回「全部」看看，或者按这个类型记一条。'
			return '接口是通的，这只宠物名下这一类还是空的。记一条（项目名 + 这次的时间）就会出现在这里。'
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
				const rows = await listPreventive(this.pid, this.activeType || undefined)
				this.rows = Array.isArray(rows) ? rows : []
				this.loadedOnce = true
			} catch (e) {
				if (this.noteAuth(e)) return
				this.error = healthErrorMessage(e)
			} finally {
				this.loading = false
			}
		},

		selectType(value) {
			if (value === this.activeType) return
			this.activeType = value
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
			uni.navigateTo({ url: '/pages/health/record-edit?pid=' + this.pid + '&type=preventive' })
		},

		goEdit(id) {
			uni.navigateTo({ url: '/pages/health/record-edit?pid=' + this.pid + '&type=preventive&id=' + id })
		},

		/** 两步确认：不用原生 confirm（手机端没有它的位置，而且它是阻塞式的） */
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
				await removePreventive(id)
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