<template>
	<view class="page">
		<!-- 全局 navigationStyle 是 custom，非 tab 页自绘返回栏（照 pages/vip/vip.vue） -->
		<view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav__back" @click="goBack">
				<view class="chev chev--left"></view>
			</view>
			<text class="nav__title">帖子详情</text>
			<text class="nav__refresh" @click="refresh">刷新</text>
		</view>

		<view class="body">
			<!-- 未登录：软门槛，先给下一步（社区接口经网关一律要登录身份） -->
			<view v-if="needLogin" class="state">
				<text class="state__title">{{ sessionExpired ? '登录已过期' : '先登录再看帖子' }}</text>
				<text class="state__desc">社区接口经过网关时一律要带登录身份，登录后帖子正文、图片视频和评论都能读。</text>
				<view class="state__btn" @click="goLogin"><text>去登录</text></view>
			</view>

			<!-- 没带 postId 直接打开：说清怎么进来，不留白屏 -->
			<view v-else-if="!postId" class="state">
				<text class="state__title">没拿到帖子编号</text>
				<text class="state__desc">从社区列表点一张卡片进来才会带 postId，直接打开这个页面读不到内容。</text>
				<view class="state__btn" @click="goBack"><text>返回社区</text></view>
			</view>

			<!-- 详情骨架 -->
			<view v-else-if="detailLoading && !view" class="skeleton">
				<view class="skeleton__card"></view>
				<view class="skeleton__card skeleton__card--short"></view>
			</view>

			<!-- 详情失败：原因 + 重试 + 退路 -->
			<view v-else-if="detailError && !view" class="state">
				<text class="state__title">帖子没读到</text>
				<text class="state__desc">{{ detailError }}</text>
				<view class="state__btn" @click="loadDetail"><text>重试</text></view>
				<view class="state__ghost" @click="goBack"><text>返回社区</text></view>
			</view>

			<template v-else-if="view">
				<!-- 已有内容时刷新详情失败：行内报错 + 重试，不把读着的内容换成错误页 -->
				<view v-if="detailError" class="inline-err">
					<text class="inline-err__text">{{ detailError }}</text>
					<text class="inline-err__act" @click="loadDetail">重试</text>
				</view>

				<view class="card">
					<text class="card__title">{{ view.title }}</text>
					<view class="card__head">
						<view class="avatar"><text>{{ view.initial }}</text></view>
						<view class="card__who">
							<text class="card__author">{{ view.author }}</text>
							<text class="card__time">{{ view.timeText }}</text>
						</view>
					</view>

					<text v-if="view.hasContent" class="card__content">{{ view.content }}</text>

					<view v-if="view.hasImages" class="imgs">
						<image
							v-for="(url, idx) in view.images"
							:key="idx"
							class="imgs__one"
							:src="url"
							mode="aspectFill"
							@click="previewImage(idx)"
						/>
					</view>

					<!-- 视频：整页只有一个播放器，且不自动播放（一屏挂一堆 video 会拖垮列表） -->
					<view v-if="view.hasVideos" class="videos">
						<video
							class="videos__player"
							:src="activeVideoUrl"
							:autoplay="false"
							:controls="true"
							:show-center-play-btn="true"
							:show-fullscreen-btn="true"
							:enable-progress-gesture="false"
							:enable-play-gesture="false"
							object-fit="contain"
						></video>
						<view v-if="videoChips.length > 1" class="videos__pick">
							<text
								v-for="v in videoChips"
								:key="v.url"
								class="vchip"
								:class="{ 'vchip--on': v.on }"
								@click="playVideo(v.url)"
							>{{ v.label }}</text>
						</view>
						<text class="videos__note">点画面上的播放键才开始，不会自动播放。多条视频时一次只加载一个，切上面的按钮换片。</text>
					</view>

					<view v-if="view.hasTopics" class="topics">
						<text v-for="(t, ti) in view.topics" :key="ti" class="topic">#{{ t }}</text>
					</view>

					<view class="stats">
						<text class="stat">{{ view.viewText }}</text>
						<text class="stat">{{ view.commentText }}</text>
					</view>

					<view class="acts">
						<view class="act" :class="{ 'act--on': view.liked }" @click="toggleLike">
							<text>{{ view.likeText }}</text>
						</view>
						<view class="act" :class="{ 'act--on': view.favorited }" @click="toggleFavorite">
							<text>{{ view.favoriteText }}</text>
						</view>
					</view>
					<text v-if="flash" class="flash">{{ flash }}</text>
				</view>

				<view class="sec">
					<text class="sec__title">评论</text>
					<text class="sec__note">{{ commentsNote }}</text>
				</view>

				<text v-if="commentsLoading && !comments.length" class="hint">正在读取评论</text>
				<view v-else-if="commentsError && !comments.length" class="state">
					<text class="state__desc">{{ commentsError }}</text>
					<view class="state__btn" @click="loadComments(true)"><text>重试</text></view>
				</view>
				<text v-else-if="!comments.length" class="hint">还没有评论</text>

				<view v-for="c in comments" :key="c.commentId" class="cmt">
					<view class="cmt__head">
						<view class="avatar avatar--sm"><text>{{ c.initial }}</text></view>
						<view class="cmt__who">
							<text class="cmt__author">{{ c.author }}</text>
							<text class="cmt__time">{{ c.timeText }}</text>
						</view>
						<text class="cmt__like">{{ c.likeText }}</text>
					</view>
					<text class="cmt__content">{{ c.content }}</text>

					<!-- 回复：默认用接口给的正序预览；展开后用游标分页拉回来的完整列表（两者同向） -->
					<view v-if="c.replyOpen ? c.replyItems.length : c.children.length" class="reps">
						<view
							v-for="r in (c.replyOpen ? c.replyItems : c.children)"
							:key="r.commentId"
							class="rep"
						>
							<text class="rep__who">{{ r.author }}</text>
							<text v-if="r.replyToText" class="rep__to">{{ r.replyToText }}</text>
							<text class="rep__text">{{ r.content }}</text>
						</view>
					</view>

					<text v-if="c.replyLoading" class="rep__loading">正在读取回复</text>
					<view v-if="c.replyError" class="inline-err">
						<text class="inline-err__text">{{ c.replyError }}</text>
						<text class="inline-err__act" @click="retryReplies(c.commentId)">重试</text>
					</view>

					<view v-if="c.showReplyToggle" class="reps__more" @click="toggleReplies(c.commentId)">
						<text>{{ c.replyOpen ? '收起回复' : c.replyToggleText }}</text>
					</view>
					<view v-if="c.replyOpen && c.replyHasMore" class="reps__more" @click="loadMoreReplies(c.commentId)">
						<text>{{ c.replyLoading ? '正在读取回复' : '加载更多回复' }}</text>
					</view>
				</view>

				<!-- 评论底部：触底会自动加载，这里同样给可点入口，失败不静默 -->
				<view v-if="comments.length" class="foot">
					<text v-if="commentsLoadingMore" class="foot__text">正在加载更多评论</text>
					<view v-else-if="commentsMoreError" class="foot__err">
						<text class="foot__err-text">{{ commentsMoreError }}</text>
						<text class="foot__act" @click="loadComments(false)">重试</text>
					</view>
					<text v-else-if="commentsHasMore" class="foot__act" @click="loadComments(false)">加载更多评论</text>
					<text v-else class="foot__text">{{ commentsEndText }}</text>
				</view>
			</template>
		</view>
	</view>
</template>

<script>
/**
 * 帖子详情（手机端 / Options API）
 *
 * 契约（`.dsh-tmp/community-contract.md`）：
 * - `GET /post/{postId}` → `PostDetailRespDTO`：**详情才有** `content` / `images[{url,objectKey,sort}]` /
 *   `videos[{url,objectKey}]` / `topics[]`；**没有 `coverUrl`**（封面 = images 按 sort 的第 0 条）；
 *   **没有 `isTop`**（置顶标记只在列表 DTO 上，所以这页不显示置顶）
 * - ⚠️ 读详情会让后端 `viewCount` +1（不是幂等读）→ 只有真的进这一页才调
 * - 评论是**游标分页**（`{records,size,hasMore,nextCursor}`，**没有 total**）：
 *   一级评论用 `lastId` 触底续拉；每条评论的回复另走 `/comment/reply`（也是游标，原样回传 nextCursor）。
 *   ⚠️ 一级评论的 `parentId` 实测是**数字 `0`**、`children` 是**正序预览**（条数不保证，≤3 不是硬契约）；
 *   回复项的 `children` / `replyCount` 都是 **null** → 渲染前一律判空
 * - `commentCount`（详情 DTO）**把回复也算进去**（实测 178：2 一级 + 2 回复 = 4）→ 文案写明"含回复"
 * - 点赞 / 收藏是**写接口**：后端已用真实账号实测通过（幂等），**本端未真调过** →
 *   页面按乐观更新 + 失败回滚，失败一定把状态改回去并把后端的话显示出来（不静默）
 *
 * 性能取舍：**整页只挂一个 `<video>` 元素**，默认第一个视频、不自动播放，
 * 其余视频靠按钮切换 src。一屏挂一堆 video 会把列表拖垮，也违背"不要自动播一堆"。
 */
import { auth } from '@/store/auth.js'
import { ApiError } from '@/utils/request.js'
import {
	CURSOR_SIZE,
	favoritePost,
	getPost,
	listComments,
	listReplies,
	unfavoritePost,
	unlikePost,
	likePost
} from '@/api/community.js'
import {
	commentCard,
	communityErrorMessage,
	postDetailView,
	replyCard
} from '@/utils/community.js'

export default {
	data() {
		return {
			statusBarHeight: 0,
			postId: 0,
			/** PostDetailRespDTO | null */
			detail: null,
			detailLoading: false,
			detailError: '',
			/** 点赞 / 收藏的交互状态（详情 DTO 不再回写，避免两处真源） */
			inter: {
				liked: null,
				favorited: null,
				likeCount: null,
				favoriteCount: null,
				viewCount: null,
				commentCount: null
			},
			/** 当前挂在播放器上的视频地址（整页只有一个播放器） */
			activeVideoUrl: '',
			/** 正在提交的写操作：'' | 'like' | 'favorite' */
			submitting: '',
			flash: '',
			/** CommentRespDTO[]（原始 DTO） */
			rawComments: [],
			commentsCursor: null,
			commentsHasMore: false,
			commentsLoading: false,
			commentsLoadingMore: false,
			commentsError: '',
			commentsMoreError: '',
			/** commentId -> { open, loading, error, items, cursor, hasMore }（回复懒加载状态） */
			replyMap: {},
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
		/** 详情 → 模板字段（摊平在 utils 里，模板只 {{ }}，不调函数） */
		view() {
			return this.detail ? postDetailView(this.detail, this.inter) : null
		},
		videoChips() {
			const v = this.view
			if (!v || !v.videos.length) return []
			const self = this
			return v.videos.map(function (url, idx) {
				return { url: url, label: '视频 ' + (idx + 1), on: url === self.activeVideoUrl }
			})
		},
		/**
		 * 评论列表 → 模板字段。这里**只读** `replyMap`（不创建键）：
		 * 在 computed 里写响应式状态会触发重新求值，容易变成死循环。
		 */
		comments() {
			const map = this.replyMap
			return this.rawComments.map(function (c) {
				const card = commentCard(c)
				const st = map[c.commentId]
				const preview = card.children.length
				card.replyOpen = !!(st && st.open)
				card.replyLoading = !!(st && st.loading)
				card.replyError = (st && st.error) || ''
				card.replyItems = st && st.items ? st.items.map(replyCard) : []
				card.replyHasMore = !!(st && st.hasMore)
				// 回复全在预览里就不用给按钮；否则给"查看全部 N 条回复"。
				// 预览条数不保证（"最多 3 条"不是硬契约）→ 用 replyCount 与预览实际条数比
				card.showReplyToggle = card.replyCount > preview || card.replyOpen
				card.replyToggleText = '查看全部 ' + card.replyCount + ' 条回复'
				return card
			})
		},
		commentsNote() {
			return '一级评论按时间倒序（新的在前），回复与回复预览都按时间正序（旧的在前）。点「查看全部回复」展开这条评论，往下滑能加载更多。'
		},
		commentsEndText() {
			// `view.commentText` 那个数是 `commentCount`（**含回复**），这里的数是一级评论条数
			// → 两者口径不同，文案必须写清是"一级评论"，别让人以为对不上
			return this.rawComments.length ? '一级评论都在这儿了，共 ' + this.rawComments.length + ' 条' : ''
		}
	},
	onLoad(options) {
		try {
			const info = uni.getSystemInfoSync()
			this.statusBarHeight = info.statusBarHeight || 0
		} catch (e) {
			this.statusBarHeight = 0
		}
		const raw = options && options.postId ? Number(options.postId) : 0
		this.postId = isNaN(raw) ? 0 : raw
		if (!this.postId) return
		// 未登录时不发请求（经网关必被拦），页面直接给软门槛
		if (!auth.isLoggedIn()) return
		this.loadAll()
	},
	onShow() {
		// 从登录页返回：这时才有身份，补一次加载
		if (this.postId && !this.detail && !this.detailLoading && this.loggedIn && !this.needLogin) {
			this.loadAll()
		}
	},
	onReachBottom() {
		if (this.needLogin) return
		if (this.commentsHasMore && !this.commentsLoading && !this.commentsLoadingMore) {
			this.loadComments(false)
		}
	},
	methods: {
		async loadAll() {
			this.unauthorized = false
			await this.loadDetail()
			if (this.detail) await this.loadComments(true)
		},

		async loadDetail() {
			if (!this.postId) return
			this.detailLoading = true
			this.detailError = ''
			this.flash = ''
			try {
				const d = await getPost(this.postId)
				if (!d) {
					this.detail = null
					this.detailError = '接口这次没有返回帖子内容，稍后重试一次'
					return
				}
				this.detail = d
				this.inter = {
					liked: !!d.liked,
					favorited: !!d.favorited,
					likeCount: Number(d.likeCount) || 0,
					favoriteCount: Number(d.favoriteCount) || 0,
					viewCount: Number(d.viewCount) || 0,
					commentCount: Number(d.commentCount) || 0
				}
				const urls = postDetailView(d, this.inter).videos
				this.activeVideoUrl = urls.length ? urls[0] : ''
			} catch (e) {
				if (this.noteAuth(e)) return
				// 保留已经读到的那份内容（模板会用行内错误条提示刷新失败），首屏失败时 detail 本来就是 null
				this.detailError = communityErrorMessage(e)
			} finally {
				this.detailLoading = false
			}
		},

		/**
		 * 拉评论
		 * @param {boolean} reset true = 第一页（换页/重试），false = 用 nextCursor 续拉
		 */
		async loadComments(reset) {
			if (!this.postId) return
			if (!reset && (this.commentsCursor === null || this.commentsCursor === undefined)) {
				// 没有游标就别硬拉（否则会重复请求第一页）
				this.commentsHasMore = false
				return
			}
			if (reset) {
				this.commentsLoading = true
				this.commentsError = ''
				this.commentsCursor = null
				this.commentsHasMore = false
				this.rawComments = []
			} else {
				this.commentsLoadingMore = true
				this.commentsMoreError = ''
			}
			try {
				const page = await listComments({
					postId: this.postId,
					lastId: reset ? undefined : this.commentsCursor,
					size: CURSOR_SIZE
				})
				const records = (page && page.records) || []
				this.rawComments = reset ? records : this.rawComments.concat(records)
				this.commentsHasMore = !!(page && page.hasMore)
				this.commentsCursor = page ? page.nextCursor : null
			} catch (e) {
				if (this.noteAuth(e)) return
				const text = communityErrorMessage(e)
				if (reset) this.commentsError = text
				else this.commentsMoreError = text
			} finally {
				this.commentsLoading = false
				this.commentsLoadingMore = false
			}
		},

		/** 回复的懒加载状态（在 methods 里创建键，computed 里只读） */
		replyState(commentId) {
			if (!this.replyMap[commentId]) {
				this.replyMap[commentId] = {
					open: false,
					loading: false,
					error: '',
					items: [],
					cursor: null,
					hasMore: false
				}
			}
			return this.replyMap[commentId]
		},

		toggleReplies(commentId) {
			const st = this.replyState(commentId)
			if (st.open && st.items.length) {
				st.open = false
				return
			}
			st.open = true
			st.error = ''
			if (st.items.length) return
			this.loadReplies(commentId, true)
		},

		async loadReplies(commentId, reset) {
			const st = this.replyState(commentId)
			if (st.loading) return
			if (!reset && (st.cursor === null || st.cursor === undefined)) {
				st.hasMore = false
				return
			}
			st.loading = true
			st.error = ''
			if (reset) {
				st.items = []
				st.cursor = null
				st.hasMore = false
			}
			try {
				const page = await listReplies({
					commentId: commentId,
					lastId: reset ? undefined : st.cursor,
					size: CURSOR_SIZE
				})
				const records = (page && page.records) || []
				// ⚠️ 这里存**原始 DTO**，摊平只在 comments computed 里做一次（`st.items.map(replyCard)`）。
				// 曾经这里写的是 `records.map(replyCard)`，computed 又 map 一次 → 二次摊平后
				// `username` / `replyUsername` / `createTime` 全丢，界面变成
				// "匿名用户 + 不显示回复谁 + 没有时间"（真实数据一跑就露，假数据看不出来）
				st.items = reset ? records : st.items.concat(records)
				st.hasMore = !!(page && page.hasMore)
				st.cursor = page ? page.nextCursor : null
				st.open = true
			} catch (e) {
				if (this.noteAuth(e)) return
				st.error = communityErrorMessage(e)
			} finally {
				st.loading = false
			}
		},

		loadMoreReplies(commentId) {
			this.loadReplies(commentId, false)
		},

		/** 回复加载失败后的"重试"：已经拉到过内容的就续拉，一条都没拉到的就重来 */
		retryReplies(commentId) {
			const st = this.replyState(commentId)
			this.loadReplies(commentId, !st.items.length)
		},

		/**
		 * 点赞 / 取消（写接口，**未实测**）
		 * 乐观更新：先改本地，失败再回滚并把后端的话显示出来。
		 */
		async toggleLike() {
			if (this.needLogin) {
				this.toast('先登录再点赞')
				return
			}
			if (!this.postId || this.submitting) return
			const next = !this.inter.liked
			const before = this.inter.likeCount
			this.inter.liked = next
			this.inter.likeCount = Math.max(0, before + (next ? 1 : -1))
			this.submitting = 'like'
			this.flash = ''
			try {
				if (next) await likePost(this.postId)
				else await unlikePost(this.postId)
				this.flash = next ? '已点赞' : '已取消点赞'
			} catch (e) {
				this.inter.liked = !next
				this.inter.likeCount = before
				const text = communityErrorMessage(e)
				this.flash = (next ? '点赞没成功：' : '取消点赞没成功：') + text
				// 写操作掉线要显式处理：光说"登录已过期"却不给下一步，用户会卡在原地反复点
				if (this.noteAuth(e)) {
					this.toast('登录已过期，请重新登录')
					return
				}
				this.toast(text)
			} finally {
				this.submitting = ''
			}
		},

		async toggleFavorite() {
			if (this.needLogin) {
				this.toast('先登录再收藏')
				return
			}
			if (!this.postId || this.submitting) return
			const next = !this.inter.favorited
			const before = this.inter.favoriteCount
			this.inter.favorited = next
			this.inter.favoriteCount = Math.max(0, before + (next ? 1 : -1))
			this.submitting = 'favorite'
			this.flash = ''
			try {
				if (next) await favoritePost(this.postId)
				else await unfavoritePost(this.postId)
				this.flash = next ? '已收藏' : '已取消收藏'
			} catch (e) {
				this.inter.favorited = !next
				this.inter.favoriteCount = before
				const text = communityErrorMessage(e)
				this.flash = (next ? '收藏没成功：' : '取消收藏没成功：') + text
				if (this.noteAuth(e)) {
					this.toast('登录已过期，请重新登录')
					return
				}
				this.toast(text)
			} finally {
				this.submitting = ''
			}
		},

		previewImage(index) {
			const urls = this.view ? this.view.images : []
			if (!urls.length) return
			uni.previewImage({ urls: urls, current: urls[index] })
		},

		playVideo(url) {
			this.activeVideoUrl = url
		},

		refresh() {
			if (this.needLogin) {
				this.toast('重新登录后再刷新')
				return
			}
			this.loadAll()
		},

		goBack() {
			const pages = getCurrentPages()
			if (pages.length > 1) uni.navigateBack()
			else uni.switchTab({ url: '/pages/community/community' })
		},

		goLogin() {
			if (this.unauthorized) auth.clearSession()
			uni.navigateTo({ url: '/pages/login/login' })
		},

		toast(title) {
			uni.showToast({ title: title, icon: 'none' })
		},

		/** 掉线统一处理：切回软门槛，不摆一堆"请求失败" */
		noteAuth(e) {
			if (!(e instanceof ApiError) || !e.isUnauthorized) return false
			this.unauthorized = true
			this.detailError = ''
			this.commentsError = ''
			this.commentsMoreError = ''
			this.detailLoading = false
			this.commentsLoading = false
			this.commentsLoadingMore = false
			return true
		}
	}
}
</script>

<style lang="scss" scoped>
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
.nav__refresh {
	padding: 0 $mw-space-2;
	min-width: $mw-touch-min;
	text-align: right;
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-brand-ink;
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

/* ---- 帖子卡 ---- */
.card {
	padding: $mw-space-4;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
.card__title {
	display: block;
	font-size: $mw-font-h2;
	font-weight: 700;
	line-height: 1.5;
	letter-spacing: -0.02em;
	color: $mw-ink;
}
.card__head {
	display: flex;
	align-items: center;
	margin-top: $mw-space-3;
}
.avatar {
	width: 72rpx;
	height: 72rpx;
	border-radius: 50%;
	background: rgba(10, 125, 109, 0.1);
	display: flex;
	align-items: center;
	justify-content: center;
	flex: none;
}
.avatar text {
	font-size: 28rpx;
	font-weight: 700;
	color: $mw-brand-ink;
}
.avatar--sm {
	width: 56rpx;
	height: 56rpx;
}
.avatar--sm text {
	font-size: 24rpx;
}
.card__who {
	flex: 1;
	min-width: 0;
	margin-left: $mw-space-2;
	display: flex;
	flex-direction: column;
}
.card__author {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-ink;
}
.card__time {
	font-size: 22rpx;
	color: $mw-muted;
}
.card__content {
	display: block;
	margin-top: $mw-space-3;
	font-size: $mw-font-body;
	line-height: 1.7;
	color: $mw-ink;
}

/* ---- 图片（九宫格，点开可看大图） ---- */
.imgs {
	display: flex;
	flex-wrap: wrap;
	margin-top: $mw-space-3;
}
.imgs__one {
	width: 218rpx;
	height: 218rpx;
	margin-right: $mw-space-1;
	margin-bottom: $mw-space-1;
	border-radius: $mw-radius-input;
	background: $mw-canvas;
}

/* ---- 视频（只挂一个播放器，不自动播放） ---- */
.videos {
	margin-top: $mw-space-3;
}
.videos__player {
	width: 100%;
	height: 400rpx;
	border-radius: $mw-radius-input;
	background: $mw-night;
}
.videos__pick {
	display: flex;
	flex-wrap: wrap;
	margin-top: $mw-space-2;
}
.vchip {
	margin-right: $mw-space-1;
	margin-bottom: $mw-space-1;
	padding: 8rpx $mw-space-3;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-pill;
	background: $mw-surface;
	font-size: $mw-font-micro;
	font-weight: 600;
	color: $mw-ink-2;
}
.vchip--on {
	background: $mw-brand-ink;
	border-color: $mw-brand-ink;
	color: #ffffff;
}
.videos__note {
	display: block;
	margin-top: $mw-space-1;
	font-size: 22rpx;
	line-height: 1.6;
	color: $mw-muted;
}

/* ---- 话题 / 计数 / 交互 ---- */
.topics {
	display: flex;
	flex-wrap: wrap;
	margin-top: $mw-space-3;
}
.topic {
	margin-right: $mw-space-1;
	margin-bottom: 6rpx;
	padding: 4rpx 14rpx;
	border-radius: $mw-radius-tag;
	background: rgba(10, 125, 109, 0.12);
	font-size: 20rpx;
	font-weight: 600;
	color: $mw-success;
}
.stats {
	display: flex;
	flex-wrap: wrap;
	margin-top: $mw-space-3;
	padding-top: $mw-space-2;
	border-top: 1px solid $mw-line;
}
.stat {
	margin-right: $mw-space-3;
	font-size: $mw-font-micro;
	color: $mw-muted;
	font-variant-numeric: tabular-nums;
}
.acts {
	display: flex;
	margin-top: $mw-space-3;
}
.act {
	flex: 1;
	margin-right: $mw-space-2;
	min-height: $mw-touch-min;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-pill;
	display: flex;
	align-items: center;
	justify-content: center;
}
.act:last-child {
	margin-right: 0;
}
.act text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-ink;
	font-variant-numeric: tabular-nums;
}
.act--on {
	background: $mw-brand-ink;
	border-color: $mw-brand-ink;
}
.act--on text {
	color: #ffffff;
}
.flash {
	display: block;
	margin-top: $mw-space-2;
	font-size: $mw-font-micro;
	line-height: 1.6;
	color: $mw-ink-2;
}

/* ---- 区块标题 ---- */
.sec {
	margin: $mw-space-4 0 $mw-space-2;
}
.sec__title {
	display: block;
	font-size: $mw-font-h2;
	font-weight: 600;
	color: $mw-ink;
}
.sec__note {
	display: block;
	margin-top: 6rpx;
	font-size: $mw-font-micro;
	line-height: 1.6;
	color: $mw-ink-2;
}

/* ---- 评论 ---- */
.cmt {
	padding: $mw-space-3 $mw-space-4;
	margin-bottom: $mw-space-2;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
.cmt__head {
	display: flex;
	align-items: center;
}
.cmt__who {
	flex: 1;
	min-width: 0;
	margin-left: $mw-space-2;
	display: flex;
	flex-direction: column;
}
.cmt__author {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-ink;
}
.cmt__time {
	font-size: 22rpx;
	color: $mw-muted;
}
.cmt__like {
	font-size: 22rpx;
	color: $mw-muted;
	font-variant-numeric: tabular-nums;
}
.cmt__content {
	display: block;
	margin-top: $mw-space-2;
	font-size: $mw-font-caption;
	line-height: 1.7;
	color: $mw-ink;
}
.reps {
	margin-top: $mw-space-2;
	padding: $mw-space-2;
	background: $mw-canvas;
	border-radius: $mw-radius-input;
}
.rep {
	margin-bottom: $mw-space-1;
}
.rep:last-child {
	margin-bottom: 0;
}
.rep__who {
	font-size: $mw-font-micro;
	font-weight: 600;
	color: $mw-brand-ink;
}
.rep__to {
	margin-left: 6rpx;
	font-size: $mw-font-micro;
	color: $mw-muted;
}
.rep__text {
	margin-left: 6rpx;
	font-size: $mw-font-micro;
	line-height: 1.7;
	color: $mw-ink-2;
}
.rep__loading {
	display: block;
	margin-top: $mw-space-1;
	font-size: $mw-font-micro;
	color: $mw-muted;
}
.reps__more {
	margin-top: $mw-space-1;
	min-height: 64rpx;
	line-height: 64rpx;
}
.reps__more text {
	font-size: $mw-font-micro;
	font-weight: 600;
	color: $mw-brand-ink;
}

/* ---- 四态 ---- */
.skeleton__card {
	height: 260rpx;
	margin-bottom: $mw-space-3;
	border-radius: $mw-radius-card;
	background: $mw-surface;
	border: 1px solid $mw-line;
	animation: mw-breathe 1.4s ease-in-out infinite;
}
.skeleton__card--short {
	height: 160rpx;
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
.state__ghost {
	margin-top: $mw-space-2;
	min-height: $mw-touch-min;
	border-radius: $mw-radius-pill;
	border: 1px solid $mw-line;
	display: flex;
	align-items: center;
	justify-content: center;
}
.state__ghost text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-ink;
}
.inline-err {
	display: flex;
	align-items: center;
	margin-top: $mw-space-2;
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