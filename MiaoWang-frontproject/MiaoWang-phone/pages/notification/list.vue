<template>
	<view class="page">
		<!-- 全局 navigationStyle 是 custom，非 tab 页自绘返回栏（照 pages/community/detail.vue） -->
		<view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav__back" @click="goBack">
				<view class="chev chev--left"></view>
			</view>
			<text class="nav__title">消息通知</text>
			<view
				v-if="loggedIn && !needLogin"
				class="nav__all"
				:class="{ 'nav__all--busy': markAllBusy, 'nav__all--off': !canMarkAll }"
				@click="markAllRead"
			>
				<text>{{ markAllBusy ? '处理中' : '全部已读' }}</text>
			</view>
		</view>

		<view class="body">
			<!-- 未登录 / 会话过期：软门槛（社区接口经网关一律要登录身份，匿名请求必被拦） -->
			<view v-if="needLogin" class="state">
				<text class="state__title">{{ sessionExpired ? '登录已过期' : '先登录再看消息' }}</text>
				<text class="state__desc">点赞、评论、回复、关注、@提及 这些通知都只对本人可见，登录后才能读到。</text>
				<view class="state__btn" @click="goLogin"><text>去登录</text></view>
			</view>

			<template v-else>
				<!-- 未读数是**裸数字**接口：「读不到」与「没有未读」是两件事，分开说 -->
				<text class="summary">{{ summaryText }}</text>

				<!-- 写操作的行内反馈：乐观更新失败会回滚，并把后端的话原样显示出来 -->
				<view v-if="actionError" class="inline-err">
					<text class="inline-err__text">{{ actionError }}</text>
					<text class="inline-err__act" @click="clearAction">知道了</text>
				</view>
				<text v-else-if="actionNote" class="flash">{{ actionNote }}</text>

				<!-- 四态之一：加载骨架（不是"加载中…"纯文字） -->
				<view v-if="loading && !cards.length" class="skeleton">
					<view class="skeleton__card"></view>
					<view class="skeleton__card"></view>
				</view>

				<!-- 四态之二：首屏失败 —— 说清原因 + 重试 -->
				<view v-else-if="error && !cards.length" class="state">
					<text class="state__title">通知没读到</text>
					<text class="state__desc">{{ error }}</text>
					<view class="state__btn" @click="loadAll"><text>重试</text></view>
				</view>

				<!-- 四态之三：空 —— 这里是「**没有通知**」，与底部的「没有更多」不是一件事 -->
				<text v-else-if="!cards.length" class="hint">{{ emptyText }}</text>

				<template v-else>
					<!-- 已有数据时刷新/续拉失败：行内报错，不把读着的内容换成错误页 -->
					<view v-if="error" class="inline-err">
						<text class="inline-err__text">{{ error }}</text>
						<text class="inline-err__act" @click="refresh">重试</text>
					</view>

					<!-- 列表：游标分页（nextCursor 原样回传）；readFlag=1 不再显示未读样式 -->
					<view
						v-for="n in cards"
						:key="n.id"
						class="card"
						:class="{ 'card--unread': n.unread, 'card--busy': n.busy }"
						@click="openItem(n)"
					>
						<!-- 全站没有头像字段 → 只画触发者名字首字，绝不挂空 <image> -->
						<view class="card__disc"><text>{{ n.initial }}</text></view>
						<view class="card__main">
							<view class="card__top">
								<text class="card__tag">{{ n.typeLabel }}</text>
								<text class="card__text">{{ n.text }}</text>
							</view>
							<text v-if="n.hasExcerpt" class="card__excerpt">{{ n.excerpt }}</text>
							<text class="card__time">{{ n.timeText }}</text>
						</view>
						<view v-if="n.unread" class="card__dot"></view>
					</view>

					<!-- 触底会自动加载；这里同样给可点入口，失败不静默 -->
					<view class="foot">
						<text v-if="loadingMore" class="foot__text">正在加载更多</text>
						<view v-else-if="moreError" class="foot__err">
							<text class="foot__err-text">{{ moreError }}</text>
							<text class="foot__act" @click="loadMore">重试</text>
						</view>
						<text v-else-if="hasMore" class="foot__act" @click="loadMore">加载更多</text>
						<text v-else class="foot__text">{{ endText }}</text>
					</view>
				</template>
			</template>
		</view>
	</view>
</template>

<script>
/**
 * 消息通知（手机端 / Options API）
 *
 * 契约唯一来源：`.dsh-tmp/reminder-notification-contract.md` **§2**（只读实测 19 次 GET + 通知表只读勘验）；
 * fixture 在 `tools/reminder-fixtures/`，离线断言在 `tools/check-notification-phone.mjs`。
 * 接口封装与展示纯函数都在 `api/community.js` 的「消息通知」段（**没有新建 store**）。
 *
 * 这一页为什么长这样 —— 六条硬事实：
 *
 * 1. **游标分页，方向是"向下取更小"**：`nextCursor` = 本页最后一条的 id，下一页传 `lastId=<cursor>`
 *    （后端 `id < lastId`）。第一页**不传** `lastId`；`hasMore=false` 时 `nextCursor=null`，
 *    **别把 null 传回去**（会退化成第一页、看到重复）。"还有没有下一页"**只认 `hasMore`** ——
 *    `size` 是归一化后的**请求值**（实测 `0`→回显 10、`999`→回显 50），`records.length === size` 会骗人。
 * 2. **未读数是裸数字**（`data: 3`）、**不支持按 type 分组** → 这一页**不做按 type 分 Tab**（用户已定）。
 * 3. **`readFlag` 是未读样式的唯一判据**（0 未读 / 1 已读）。单条已读与全部已读都是**写接口**：
 *    **乐观更新 + 失败回滚 + 显示后端文案**。`markRead` 对"已经是已读"是**幂等静默成功**，
 *    只有"不存在 / 别人的"才是 `B000317`（`communityErrorMessage` 已有这条文案）→ **不要把 B000317 之外的失败也当成错误弹窗**。
 * 4. **跳转**：关注类（5）跳 `pages/user/profile?uid=actorUid`（它的 `targetId`/`postId` **都是 0**，
 *    **绝不能拼进 URL**）；其余（1/2/3/4/6）跳 `pages/community/detail?postId=`。
 *    ⚠️ **评论类（2/3/4）无法定位到具体评论**（没有"按 commentId 查评论"的接口）→ 只跳帖子，
 *    **不假装能定位**；页面下方也如实写明这件事。
 * 5. **优雅降级**：取消赞 / 取关 / 删帖都**不会撤回通知**，所以历史通知指向的目标随时可能已经没了
 *    → 目标缺失（`postId`/`actorUid` 为 0）时**给提示、不跳**（不留白屏）；目标还在但已被删的情况
 *    由目标页自己报"帖子不存在或已被删除"，这里不越权替它判断。
 * 6. **`createTime` 是 ISO-8601 带偏移的 UTC** → 一律交给 `notificationTimeText`（`new Date(iso)`）；
 *    **不要**复用 `utils/health.js` 的 `parseHealthTime`（那是"非 ISO 的 GMT+8"的另一套口径）。
 *
 * 四态齐全：加载（骨架）/ 空（**没有通知**）/ 错（说清原因 + 重试）/ 未登录（去登录软门槛）；
 * 下拉刷新 + 触底加载（游标）沿用 `pages/community/detail.vue` 的既有模式；
 * 模板只访问实例属性（卡片一律在 computed 里摊平，绝不 `{{ 函数() }}`）。
 */
import { auth } from '@/store/auth.js'
import { ApiError } from '@/utils/request.js'
import {
	NOTIF_CURSOR_SIZE,
	NOTIF_EMPTY_TEXT,
	NOTIF_NO_TARGET_TEXT,
	fetchUnreadCount,
	listNotifications,
	markAllNotificationsRead,
	markNotificationRead,
	notificationEndText,
	notificationHasUnread,
	notificationUnreadText,
	notificationView
} from '@/api/community.js'
import { communityErrorMessage } from '@/utils/community.js'

export default {
	data() {
		return {
			statusBarHeight: 0,
			/** NotificationRespDTO[]（原始 DTO，展示字段在 computed 里摊平） */
			raw: [],
			/** 下一页的 `lastId`（**原样**吃响应里的 nextCursor，不自己算） */
			cursor: null,
			/** 只认响应里的 hasMore（size / records.length 都不是判据） */
			hasMore: false,
			loading: false,
			loadingMore: false,
			error: '',
			moreError: '',
			/**
			 * 本地交互态：`notificationId -> { read, busy }`（**乐观更新用**，接口 DTO 不回写）
			 * 重新拉第一页时清空 —— 那时以接口的 `readFlag` 为准（避免两处真源）
			 */
			interMap: {},
			markAllBusy: false,
			actionError: '',
			actionNote: '',
			/** 未读数：**只在 unread-count 成功时有值**（裸数字）；拿不到就是 null（"未知"，不是 0） */
			unreadTotal: null,
			/** 会话确实过期了（本地还有 token 但后端不认） */
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
		needLogin() {
			return !this.loggedIn || this.sessionExpired
		},
		/** 列表卡（摊平在 api 层的纯函数里，模板只 {{ }}） */
		cards() {
			const inter = this.interMap
			return this.raw.map(function (n) {
				return notificationView(n, inter[n.notificationId])
			})
		},
		/** 未读数摘要：**"读不到"与"没有未读"分开说**（拿不到就不假装是 0） */
		summaryText() {
			if (this.unreadTotal === null) return '未读数这次没读到，以列表里的未读标记为准'
			if (!notificationHasUnread(this.unreadTotal)) return '没有未读消息'
			return notificationUnreadText(this.unreadTotal) + '，点开一条就会自动标记已读'
		},
		/** "全部已读"在不必要时是禁用态（靠未读数判断；未读数未知时也不假装能点） */
		canMarkAll() {
			return notificationHasUnread(this.unreadTotal) && !this.markAllBusy
		},
		/** 空态：**没有通知**（与 endText 的"没有更多"分开） */
		emptyText() {
			return NOTIF_EMPTY_TEXT
		},
		/** 到底文案：**没有更多** */
		endText() {
			return notificationEndText(this.raw.length)
		}
	},
	onLoad() {
		try {
			const info = uni.getSystemInfoSync()
			this.statusBarHeight = info.statusBarHeight || 0
		} catch (e) {
			this.statusBarHeight = 0
		}
		// 未登录时不发请求（经网关必被拦），页面直接给软门槛
		if (!auth.isLoggedIn()) return
		this.loadAll()
	},
	onShow() {
		// 从登录页返回：这时才有身份，补一次加载
		if (this.loggedIn && !this.needLogin && !this.loading && !this.raw.length) this.loadAll()
	},
	onPullDownRefresh() {
		this.refresh()
	},
	onReachBottom() {
		this.loadMore()
	},
	methods: {
		/** 首屏 / 重试：列表与未读数一起拉（互不阻塞，各自处理失败） */
		async loadAll() {
			if (!this.loggedIn) return
			this.unauthorized = false
			this.error = ''
			this.moreError = ''
			this.actionError = ''
			this.actionNote = ''
			await Promise.all([this.loadCursor(true), this.loadUnread()])
		},

		/**
		 * 游标分页（**唯一的分页实现**）
		 * @param {boolean} reset true = 第一页（首屏 / 重试 / 下拉刷新），false = 用 `nextCursor` 续拉
		 */
		async loadCursor(reset) {
			if (!this.loggedIn) return
			if (!reset && (this.cursor === null || this.cursor === undefined)) {
				// 没有游标就别硬拉（否则会把第一页再请求一遍、列表里出现重复）
				this.hasMore = false
				return
			}
			if (reset) {
				this.loading = true
				this.error = ''
				this.raw = []
				this.cursor = null
				this.hasMore = false
				this.interMap = {}
			} else {
				if (this.loadingMore) return
				this.loadingMore = true
				this.moreError = ''
			}
			try {
				// `lastId` 一律**原样**回传游标；第一页不传（undefined 会被 buildUrl 省略）
				const page = await listNotifications({
					lastId: reset ? undefined : this.cursor,
					size: NOTIF_CURSOR_SIZE
				})
				const records = (page && page.records) || []
				this.raw = reset ? records : this.raw.concat(records)
				// ⚠️ 只认响应里的 hasMore
				this.hasMore = !!(page && page.hasMore)
				this.cursor = page ? page.nextCursor : null
				// "说还有更多、却没给游标"是死循环入口 → 收尾停住
				if (this.hasMore && (this.cursor === null || this.cursor === undefined)) this.hasMore = false
			} catch (e) {
				if (this.noteAuth(e)) return
				const text = communityErrorMessage(e)
				if (reset) this.error = text
				else this.moreError = text
			} finally {
				this.loading = false
				this.loadingMore = false
			}
		},

		/**
		 * 未读数（**裸数字**接口）
		 * ⚠️ **失败静默降级**：拿不到就把 `unreadTotal` 留成 null（摘要会如实说"没读到"），
		 * 绝不因此把整页变成错误页 —— 列表本身照常可用。
		 */
		async loadUnread() {
			if (!this.loggedIn) return
			try {
				const n = await fetchUnreadCount()
				const num = n === null || n === undefined ? NaN : Number(n)
				this.unreadTotal = isNaN(num) ? null : num
			} catch (e) {
				this.unreadTotal = null
				// 只有"确实掉线"才切软门槛；其它失败一律静默
				this.noteAuth(e)
			}
		},

		/** 未读数的乐观增减（**未知时保持未知**，不把 null 当成 0 再加） */
		bumpUnread(delta) {
			if (this.unreadTotal === null) return
			this.unreadTotal = Math.max(0, this.unreadTotal + delta)
		},

		/* ---------------- 标记已读（PUT，**本端从未真调**） ---------------- */

		/**
		 * 单条标记已读：**乐观更新 + 失败回滚 + 显示后端文案**
		 *
		 * 幂等语义（契约 §2.1 坑 8）：已经是已读 → 后端**静默成功** → 本地也算成功、不弹错；
		 * 只有"不存在 / 别人的"才是 `B000317`。
		 */
		async markRead(card) {
			if (!card || !card.id) return
			if (this.needLogin) {
				this.toast('先登录再看消息')
				return
			}
			if (card.busy) return
			const id = card.id
			const before = this.interMap[id]
			const beforeRead = before ? !!before.read : false
			const unreadBefore = this.unreadTotal
			const wasRead = !!card.read
			this.setInter(id, { read: true, busy: true })
			if (!wasRead) this.bumpUnread(-1)
			this.actionError = ''
			this.actionNote = ''
			try {
				await markNotificationRead(id)
				this.setInter(id, { read: true, busy: false })
				if (!wasRead) this.actionNote = '已标记为已读'
			} catch (e) {
				// 回滚：读态与未读数都回到操作前的值
				this.setInter(id, { read: beforeRead, busy: false })
				this.unreadTotal = unreadBefore
				if (this.noteAuth(e)) {
					this.toast('登录已过期，请重新登录')
					return
				}
				const text = communityErrorMessage(e)
				this.actionError = '标记已读没成功：' + text
				this.toast(text)
			}
		},

		/**
		 * 全部标记已读：一次性乐观把**已经拿到的**都标成已读，失败整批回滚。
		 * （接口是 `read-all`：后端会把该用户**全部**通知标已读，不止当前这一页。）
		 */
		async markAllRead() {
			if (this.needLogin) {
				this.toast('先登录再看消息')
				return
			}
			if (this.markAllBusy) return
			if (!this.canMarkAll) {
				this.toast(this.unreadTotal === null ? '未读数没读到，先下拉刷新一次' : '没有未读消息')
				return
			}
			const beforeMap = this.interMap
			const beforeUnread = this.unreadTotal
			const next = {}
			this.raw.forEach(function (n) {
				next[n.notificationId] = { read: true, busy: true }
			})
			this.interMap = next
			this.unreadTotal = 0
			this.markAllBusy = true
			this.actionError = ''
			this.actionNote = ''
			try {
				await markAllNotificationsRead()
				const done = {}
				this.raw.forEach(function (n) {
					done[n.notificationId] = { read: true, busy: false }
				})
				this.interMap = done
				this.actionNote = '已全部标记为已读'
			} catch (e) {
				// 整批回滚（含"本来就已读"的那些，逐条恢复原值）
				this.interMap = beforeMap
				this.unreadTotal = beforeUnread
				if (this.noteAuth(e)) {
					this.toast('登录已过期，请重新登录')
					return
				}
				const text = communityErrorMessage(e)
				this.actionError = '全部标记已读没成功：' + text
				this.toast(text)
			} finally {
				this.markAllBusy = false
			}
		},

		/** 点一条通知：先跳过去（标记已读在后台跑，不阻塞跳转），目标缺失则**降级提示、不跳** */
		openItem(card) {
			if (!card) return
			if (card.unread) this.markRead(card)
			this.jump(card)
		},

		jump(card) {
			// 取消赞 / 取关 / 删帖都不撤回通知 → 目标为 0（关注类没有 actorUid、帖子类没有 postId）是常态
			if (!card || !card.clickable) {
				this.toast(NOTIF_NO_TARGET_TEXT)
				return
			}
			uni.navigateTo({ url: card.url })
		},

		setInter(id, value) {
			const next = Object.assign({}, this.interMap)
			next[id] = value
			this.interMap = next
		},

		clearAction() {
			this.actionError = ''
			this.actionNote = ''
		},

		loadMore() {
			if (this.needLogin) return
			if (!this.hasMore || this.loading || this.loadingMore) return
			this.loadCursor(false)
		},

		async refresh() {
			if (!this.loggedIn) {
				uni.stopPullDownRefresh()
				return
			}
			this.unauthorized = false
			await this.loadAll()
			uni.stopPullDownRefresh()
		},

		goBack() {
			const pages = getCurrentPages()
			if (pages.length > 1) uni.navigateBack()
			else uni.switchTab({ url: '/pages/community/community' })
		},

		goLogin() {
			// 会话确实过期时先清本地会话，否则从登录页回来还是拿旧 token 反复被拒
			if (this.unauthorized) auth.clearSession()
			uni.navigateTo({ url: '/pages/login/login' })
		},

		toast(title) {
			uni.showToast({ title: title, icon: 'none' })
		},

		/** 掉线统一处理：切回软门槛，不摆一堆"请求失败"红字（沿用既有 noteAuth 约定） */
		noteAuth(e) {
			if (!(e instanceof ApiError) || !e.isUnauthorized) return false
			this.unauthorized = true
			this.error = ''
			this.moreError = ''
			this.actionError = ''
			this.actionNote = ''
			this.loading = false
			this.loadingMore = false
			this.markAllBusy = false
			return true
		}
	}
}
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

/* ---- 顶部返回栏（自绘箭头，素材已废弃） ---- */
.nav {
	display: flex;
	align-items: center;
	padding: 0 $mw-space-3;
	background: $mw-surface;
	border-bottom: 1rpx solid $mw-line;
}
.nav__back {
	display: flex;
	align-items: center;
	justify-content: center;
	width: $mw-touch-min;
	height: $mw-touch-min;
}
.nav__title {
	flex: 1;
	font-size: $mw-font-h2;
	font-weight: 600;
	color: $mw-ink;
}
.nav__all {
	padding: 0 $mw-space-2;
	min-width: $mw-touch-min;
	min-height: $mw-touch-min;
	display: flex;
	align-items: center;
	justify-content: flex-end;
}
.nav__all text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-brand-ink;
}
.nav__all--off text {
	color: $mw-muted;
}
.nav__all--busy {
	opacity: 0.6;
}
.chev {
	width: 20rpx;
	height: 20rpx;
	border-top: 4rpx solid $mw-ink-2;
	border-right: 4rpx solid $mw-ink-2;
}
.chev--left {
	transform: rotate(-135deg);
}

.body {
	padding: $mw-space-3 $mw-page-padding $mw-space-8;
	background: $mw-canvas;
	min-height: 100vh;
}
.summary {
	display: block;
	margin-bottom: $mw-space-3;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}

/* ---- 通知卡 ---- */
.card {
	display: flex;
	align-items: flex-start;
	padding: $mw-space-3 $mw-space-4;
	margin-bottom: $mw-space-2;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
/* 未读：左侧一条暖橙竖线 + 右上角红点（readFlag=1 时两者都不出现） */
.card--unread {
	border-left: 6rpx solid $mw-brand-ink;
}
.card--busy {
	opacity: 0.6;
}
.card__disc {
	width: 80rpx;
	height: 80rpx;
	border-radius: 50%;
	background: rgba(194, 65, 12, 0.1);
	display: flex;
	align-items: center;
	justify-content: center;
	flex: none;
}
.card__disc text {
	font-size: 32rpx;
	font-weight: 700;
	color: $mw-brand-ink;
}
.card__main {
	flex: 1;
	min-width: 0;
	margin-left: $mw-space-3;
	display: flex;
	flex-direction: column;
}
.card__top {
	display: flex;
	align-items: center;
}
.card__tag {
	flex: none;
	margin-right: $mw-space-1;
	padding: 4rpx 12rpx;
	border-radius: $mw-radius-tag;
	background: $mw-canvas;
	font-size: 20rpx;
	font-weight: 600;
	color: $mw-ink-2;
}
.card__text {
	flex: 1;
	min-width: 0;
	font-size: $mw-font-body;
	font-weight: 600;
	line-height: 1.5;
	color: $mw-ink;
}
.card__excerpt {
	margin-top: 6rpx;
	font-size: $mw-font-caption;
	line-height: 1.6;
	color: $mw-ink-2;
}
.card__time {
	margin-top: 6rpx;
	font-size: $mw-font-micro;
	color: $mw-muted;
	font-variant-numeric: tabular-nums;
}
.card__dot {
	flex: none;
	width: 16rpx;
	height: 16rpx;
	margin-left: $mw-space-2;
	border-radius: 50%;
	background: $mw-danger;
}

/* ---- 四态 ---- */
.skeleton__card {
	height: 160rpx;
	margin-bottom: $mw-space-2;
	border-radius: $mw-radius-card;
	background: $mw-surface;
	border: 1px solid $mw-line;
	animation: mw-breathe 1.4s ease-in-out infinite;
}
@keyframes mw-breathe {
	0% { opacity: 0.55; }
	50% { opacity: 1; }
	100% { opacity: 0.55; }
}
.hint {
	display: block;
	padding: $mw-space-3 0;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}
.state {
	padding: $mw-space-4;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
.state__title {
	display: block;
	font-size: $mw-font-body;
	font-weight: 600;
	color: $mw-ink;
}
.state__desc {
	display: block;
	margin-top: $mw-space-1;
	font-size: $mw-font-caption;
	line-height: 1.7;
	color: $mw-ink-2;
}
.state__btn {
	margin-top: $mw-space-3;
	min-height: $mw-touch-min;
	border-radius: $mw-radius-pill;
	background: $mw-brand-ink;
	display: flex;
	align-items: center;
	justify-content: center;
}
.state__btn text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: #ffffff;
}
.inline-err {
	display: flex;
	align-items: center;
	margin-bottom: $mw-space-2;
	padding: $mw-space-2;
	background: $mw-surface;
	border: 1px solid rgba(229, 72, 77, 0.3);
	border-radius: $mw-radius-input;
}
.inline-err__text {
	flex: 1;
	min-width: 0;
	font-size: $mw-font-micro;
	line-height: 1.6;
	color: $mw-danger;
}
.inline-err__act {
	margin-left: $mw-space-2;
	padding: 0 $mw-space-2;
	min-width: $mw-touch-min;
	text-align: right;
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-brand-ink;
}
.flash {
	display: block;
	margin-bottom: $mw-space-2;
	font-size: $mw-font-micro;
	line-height: 1.6;
	color: $mw-success;
}

/* ---- 底部加载更多 ---- */
.foot {
	display: flex;
	align-items: center;
	justify-content: center;
	padding: $mw-space-3 0;
}
.foot__text {
	font-size: $mw-font-micro;
	color: $mw-muted;
}
.foot__act {
	padding: $mw-space-2 $mw-space-4;
	min-height: $mw-touch-min;
	line-height: 56rpx;
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-brand-ink;
}
.foot__err {
	display: flex;
	flex-direction: column;
	align-items: center;
	width: 100%;
}
.foot__err-text {
	font-size: $mw-font-micro;
	line-height: 1.6;
	text-align: center;
	color: $mw-danger;
}
</style>