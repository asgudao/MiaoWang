<template>
	<view class="hpage">
		<view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav__back" @click="goBack"><view class="chev chev--left"></view></view>
			<text class="nav__title">就诊与病史</text>
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
				<text class="state__title">就诊记录没读到</text>
				<text class="state__desc">{{ error }}</text>
				<view class="state__btn" @click="retry"><text>重试</text></view>
			</view>

			<view v-else-if="!rows.length" class="state">
				<text class="state__title">还没有就诊记录</text>
				<text class="state__desc">接口是通的，这只宠物名下这一类还是空的。记一条（就诊时间 + 诊断）就会出现在这里。</text>
				<view class="state__btn" @click="goNew"><text>记一次就诊</text></view>
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
					<text>附件（化验单 / 影像）在库里只存了绝对地址，地址是按当时请求的主机名生成的。用 127.0.0.1 存的图在手机上会打不开，需要重新上传或用局域网地址。</text>
				</view>

				<view v-for="c in cards" :key="c.key" class="rec">
					<view class="rec__head">
						<text class="rec__title">{{ c.diagnosis }}</text>
						<text v-if="c.hasCost" class="tag tag--muted">{{ c.costText }}</text>
					</view>
					<text class="rec__sub">就诊时间 {{ c.visitText }}</text>
					<view v-if="c.hasHospital || c.hasDoctor" class="row">
						<text class="row__k">医院 / 医生</text>
						<text class="row__v">{{ c.placeText }}</text>
					</view>
					<view v-if="c.hasTreatment" class="row">
						<text class="row__k">处置</text>
						<text class="row__v row__v--wrap">{{ c.treatment }}</text>
					</view>
					<view v-if="c.hasRemark" class="row row--last">
						<text class="row__k">备注</text>
						<text class="row__v row__v--wrap">{{ c.remark }}</text>
					</view>

					<view v-if="c.hasAttachments" class="shots">
						<text class="shots__label">附件 {{ c.attachmentCount }} 张（后端出参是数组）</text>
						<view class="shots__grid">
							<image
								v-for="(u, ui) in c.attachments"
								:key="ui"
								class="shots__img"
								:src="u"
								mode="aspectFill"
								@click="preview(c.attachments, ui)"
							/>
						</view>
					</view>
					<text v-else class="hc__dim">这条没有附件（后端返回的是空数组，不是 null）</text>

					<view class="rec__acts">
						<text class="rec__act" @click="goEdit(c.id)">编辑</text>
						<text class="rec__act rec__act--danger" @click="askDelete(c.id)">{{ confirmingId === c.id ? '确认删除' : '删除' }}</text>
						<text v-if="confirmingId === c.id" class="rec__act" @click="confirmingId = null">取消</text>
					</view>
					<text v-if="confirmingId === c.id" class="hc__dim">删除是逻辑删除：列表里不再出现，原始数据仍留在库里。</text>
				</view>

				<text class="foot">{{ countText }}。附件地址由后端按请求主机名下发，换一台机器看历史附件可能裂图。</text>
			</template>
		</template>
	</view>
</template>

<script>
/**
 * 就诊与病史列表（手机端 / Options API）
 *
 * 契约（实测）：
 * - 列表是**裸数组**；按 `visitAt` 倒序
 * - 必填是 `visitAt` **和** `diagnosis`（缺一个 → `B000407`）
 * - ⚠️ **`attachments` 出参是数组、入参是逗号串**；没有附件时是 **`[]` 不是 `null`**
 *   → 卡片用 `splitList` 之后一定拿到数组，模板里不需要判 null
 * - ⚠️ 附件 URL **按请求 Host 推导**（`X-Forwarded-Host` → `Host`，端口换成 MinIO 的 9000）：
 *   同一个 objectKey 在不同 Host 下 URL 不同 → 用 `127.0.0.1` 存的附件在手机上必然裂图（挂账 #67/#68）
 */
import { auth } from '@/store/auth.js'
import { ApiError } from '@/utils/request.js'
import { listMedical, removeMedical } from '@/api/health.js'
import { healthErrorMessage, medicalCard } from '@/utils/health.js'

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
			const out = []
			for (let i = 0; i < this.rows.length; i++) {
				const c = medicalCard(this.rows[i])
				// 医院 / 医生合成一行，两者都空时整行不渲染
				const place = []
				if (c.hasHospital) place.push(c.hospital)
				if (c.hasDoctor) place.push(c.doctor)
				c.placeText = place.join(' ')
				c.hasPlace = place.length > 0
				out.push(c)
			}
			return out
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
				const rows = await listMedical(this.pid)
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
			uni.navigateTo({ url: '/pages/health/record-edit?pid=' + this.pid + '&type=medical' })
		},

		goEdit(id) {
			uni.navigateTo({ url: '/pages/health/record-edit?pid=' + this.pid + '&type=medical&id=' + id })
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
				await removeMedical(id)
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

/* 附件九宫格（不是所有记录都有图，有图才渲染） */
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