<template>
	<view class="page">
		<view class="head">
			<text class="h1">社区</text>
			<text class="sub">{{ headSub }}</text>
		</view>

		<!-- 未登录 / 会话过期：软门槛，先给下一步。社区接口经网关一律要登录身份，匿名请求必被拦 -->
		<view v-if="showGate" class="state">
			<text class="state__title">{{ sessionExpired ? '登录已过期' : '社区要登录才能看' }}</text>
			<text class="state__desc">{{ gateDesc }}</text>
			<view class="state__btn" @click="goLogin"><text>去登录</text></view>
			<view class="state__ghost" @click="retry"><text>我已经登录了，重新读取</text></view>
		</view>

		<template v-else>
			<!-- 版块 chips：真实 6 个版块 + 全部（"推荐"在契约里不存在，全站流就是不传 sectionId） -->
			<scroll-view class="chips" scroll-x :show-scrollbar="false">
				<view
					v-for="c in chips"
					:key="c.id"
					class="chip"
					:class="{ 'chip--on': c.on }"
					@click="selectSection(c.id)"
				>
					<text>{{ c.name }}</text>
				</view>
			</scroll-view>
			<view v-if="sectionsError" class="inline-err">
				<text class="inline-err__text">{{ sectionsError }}</text>
				<text class="inline-err__act" @click="loadSections">重试</text>
			</view>

			<!-- 首屏骨架（不是"加载中…"纯文字） -->
			<view v-if="firstLoading" class="skeleton">
				<view v-for="n in 3" :key="n" class="skeleton__card"></view>
			</view>

			<!-- 列表级失败且列表是空的：说清原因 + 可重试，绝不静默 -->
			<view v-else-if="listError && !cards.length" class="state">
				<text class="state__title">帖子没读到</text>
				<text class="state__desc">{{ listError }}</text>
				<view class="state__btn" @click="retry"><text>重试</text></view>
			</view>

			<!-- 空态：接口通了但这一页是空的（换了版块 / 该版块确实没人发帖时就是这一态） -->
			<view v-else-if="!cards.length" class="state">
				<text class="state__title">{{ emptyTitle }}</text>
				<text class="state__desc">{{ emptyDesc }}</text>
				<view class="state__btn" @click="retry"><text>重新读取</text></view>
			</view>

			<template v-else>
				<!-- 列表已有内容时刷新失败：行内报错 + 重试，不把读着的内容换成错误页 -->
				<view v-if="listError" class="inline-err">
					<text class="inline-err__text">{{ listError }}</text>
					<text class="inline-err__act" @click="refresh">重试</text>
				</view>

				<view v-for="p in cards" :key="p.postId" class="post" @click="goDetail(p.postId)">
					<view class="post__head">
						<view class="avatar"><text>{{ p.initial }}</text></view>
						<view class="post__who">
							<text class="post__author">{{ p.author }}</text>
							<text class="post__time">{{ p.timeText }}</text>
						</view>
						<!-- p.isTop 由 postCard 按真值算好（列表 DTO 实测是数字 0/1，不做严格比较） -->
						<text v-if="p.isTop" class="badge badge--top">置顶</text>
						<text v-if="p.videoText" class="badge">{{ p.videoText }}</text>
					</view>

					<text class="post__title">{{ p.title }}</text>
					<image v-if="p.hasCover" class="post__cover" :src="p.coverUrl" mode="aspectFill" />

					<view v-if="p.hasTopics" class="topics">
						<text v-for="(t, ti) in p.topics" :key="ti" class="topic">#{{ t }}</text>
					</view>

					<view class="post__foot">
						<text class="post__meta">{{ p.likeText }}</text>
						<text class="post__meta">{{ p.commentText }}</text>
						<text class="post__meta">{{ p.favoriteText }}</text>
						<text class="post__meta post__meta--dim">{{ p.viewText }}</text>
					</view>
				</view>

				<!-- 触底会自动加载；这里同时给一个可点的入口，失败时不静默 -->
				<view class="foot">
					<text v-if="loadingMore" class="foot__text">正在加载更多</text>
					<view v-else-if="moreError" class="foot__err">
						<text class="foot__err-text">{{ moreError }}</text>
						<text class="foot__act" @click="loadMore">重试</text>
					</view>
					<text v-else-if="hasMore" class="foot__act" @click="loadMore">加载更多</text>
					<text v-else class="foot__text">{{ totalText }}</text>
				</view>
			</template>
		</template>

		<FloatingTabBar current="community" />
	</view>
</template>

<script>
/**
 * 社区信息流（手机端 / Options API）
 *
 * 从"100% 硬编码 3 条演示数据"改成真接口，契约见 `.dsh-tmp/community-contract.md`：
 * - 版块 chips 用 `GET /section` 的**真实 6 个版块** + 「全部」；过滤参数是 `sectionId`（Long），
 *   不传才是全站流（契约里没有"推荐"版块）
 * - 卡片只渲染**列表 DTO 真有的字段**：`title` + `coverUrl` + 四个计数 + `topics` + `videos`。
 *   列表**没有正文**（`content` 只在详情里）→ 这里绝不编一段假正文出来
 * - `liked` / `favorited` 实测是**布尔**、`isTop` 实测是**数字 `0`/`1`** → 置顶判断在
 *   `postCard` 里按真值算好（禁止 `=== true` / `=== 1`）
 * - 分页是**页码分页**（`{pageNum,pageSize,total,pages,records}`）→ 触底加载更多 + 下拉刷新
 * - 四态齐全：加载（骨架）/ 空 / 错（说清原因 + 重试）/ 未登录（去登录）
 *
 * 模板只访问实例属性：卡片字段一律在 `computed` 里摊平（`postCard`），模板里不出现
 * store / utils 的对象，也不调用函数（`check-phone` 第 4、5 类检查盯着这两条）。
 */
import FloatingTabBar from '@/components/FloatingTabBar.vue'
import { hideNativeTabBar } from '@/utils/tabbar.js'
import { auth } from '@/store/auth.js'
import { ApiError } from '@/utils/request.js'
import { PAGE_SIZE, listPosts, listSections } from '@/api/community.js'
import { communityErrorMessage, postCard } from '@/utils/community.js'

export default {
	components: { FloatingTabBar },
	data() {
		return {
			/** SectionRespDTO[]（原始 DTO，不直接进模板） */
			sections: [],
			sectionsError: '',
			/** 0 = 全部（不传 sectionId） */
			activeSectionId: 0,
			/** PostRespDTO[]（原始 DTO） */
			posts: [],
			pageNum: 1,
			total: 0,
			/** 成功加载过至少一次（用来区分"还没加载"和"加载完是空的"） */
			loadedOnce: false,
			loading: false,
			loadingMore: false,
			hasMore: false,
			/** 列表级错误（列表为空时显示大卡片） */
			listError: '',
			/** 加载更多的错误（列表已有内容时显示行内） */
			moreError: '',
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
		showGate() {
			return !this.loggedIn || this.sessionExpired
		},
		headSub() {
			if (this.showGate) return '登录后就能看到大家发的帖子和评论'
			return '养宠人的日常，都在这里'
		},
		gateDesc() {
			if (this.sessionExpired) return '本地的登录状态后端已经不认了，重新登录一次就能继续看帖。'
			return '社区接口经过网关时一律要带登录身份，匿名请求会被网关直接拦掉，所以这里先把登录入口给你。'
		},
		/** 版块 chips：全部 + 接口返回的真实版块（id 是 sectionId，不是名字） */
		chips() {
			const list = [{ id: 0, name: '全部', on: this.activeSectionId === 0 }]
			for (let i = 0; i < this.sections.length; i++) {
				const s = this.sections[i] || {}
				const id = s.sectionId
				const name = s.name ? String(s.name) : (s.code ? String(s.code) : '版块 ' + id)
				list.push({ id: id, name: name, on: id === this.activeSectionId })
			}
			return list
		},
		cards() {
			return this.posts.map(postCard)
		},
		firstLoading() {
			return this.loading && !this.posts.length
		},
		emptyTitle() {
			if (this.activeSectionId) return '这个版块还没有帖子'
			return '社区还没有帖子'
		},
		emptyDesc() {
			if (this.activeSectionId) {
				return '接口这次返回 0 条，换一个版块看看，或者下拉刷新一次。'
			}
			return '接口是通的，后端这条信息流这次返回 0 条。等有帖子发布出来，这里就会出现。'
		},
		totalText() {
			return this.posts.length ? '一共 ' + this.posts.length + ' 条，没有更多了' : '没有更多了'
		}
	},
	onLoad() {
		hideNativeTabBar()
		this.loadAll()
	},
	onShow() {
		hideNativeTabBar()
		// 从登录页返回：这时才有身份，补一次首屏加载
		if (this.loggedIn && !this.loadedOnce && !this.loading) this.loadAll()
	},
	onPullDownRefresh() {
		this.refresh()
	},
	onReachBottom() {
		if (this.showGate) return
		this.loadMore()
	},
	methods: {
		/** 首次进入 / 重试：版块与信息流一起拉（互不阻塞，各自报错） */
		async loadAll() {
			if (!this.loggedIn) return
			this.unauthorized = false
			this.listError = ''
			this.moreError = ''
			await Promise.all([this.loadSections(), this.loadFeed(true)])
		},

		async loadSections() {
			if (!this.loggedIn) return
			this.sectionsError = ''
			try {
				const rows = await listSections()
				this.sections = Array.isArray(rows) ? rows : []
			} catch (e) {
				if (this.noteAuth(e)) return
				this.sectionsError = '版块没读到：' + communityErrorMessage(e)
			}
		},

		/**
		 * 拉信息流
		 * @param {boolean} reset true = 从第 1 页重来（切版块 / 刷新 / 首屏），false = 下一页
		 */
		async loadFeed(reset) {
			if (!this.loggedIn) return
			if (reset) {
				this.loading = true
				this.listError = ''
			} else {
				if (this.loadingMore || !this.hasMore) return
				this.loadingMore = true
			}
			this.moreError = ''
			try {
				const page = await listPosts({
					sectionId: this.activeSectionId || undefined,
					pageNum: this.pageNum,
					pageSize: PAGE_SIZE
				})
				const records = (page && page.records) || []
				this.posts = reset ? records : this.posts.concat(records)
				this.total = Number(page && page.total) || 0
				const pages = Number(page && page.pages) || 0
				// 进度以"这一页的页码"为准：请求发出去时的 pageNum 还没自增
				this.hasMore = this.pageNum < pages
				this.pageNum = this.pageNum + 1
				this.loadedOnce = true
			} catch (e) {
				if (this.noteAuth(e)) return
				const text = communityErrorMessage(e)
				if (reset) this.listError = text
				else this.moreError = text
			} finally {
				this.loading = false
				this.loadingMore = false
			}
		},

		loadMore() {
			if (this.showGate) return
			if (!this.hasMore || this.loadingMore || this.loading) return
			this.loadFeed(false)
		},

		async refresh() {
			if (!this.loggedIn) {
				uni.stopPullDownRefresh()
				return
			}
			this.unauthorized = false
			this.pageNum = 1
			await Promise.all([this.loadSections(), this.loadFeed(true)])
			uni.stopPullDownRefresh()
		},

		/** 错误态/空态里的"重试"：会话过期标记也一起复位，否则门槛会一直挂在那 */
		retry() {
			if (!this.loggedIn) {
				uni.navigateTo({ url: '/pages/login/login' })
				return
			}
			this.unauthorized = false
			this.pageNum = 1
			this.loadAll()
		},

		selectSection(id) {
			if (id === this.activeSectionId) return
			this.activeSectionId = id
			this.posts = []
			this.pageNum = 1
			this.total = 0
			this.hasMore = false
			this.loadedOnce = false
			this.listError = ''
			this.moreError = ''
			this.loadFeed(true)
		},

		goDetail(postId) {
			uni.navigateTo({ url: '/pages/community/detail?postId=' + postId })
		},

		goLogin() {
			// 会话确实过期时先清本地会话，否则从登录页回来还是拿旧 token 反复被拒
			if (this.unauthorized) auth.clearSession()
			uni.navigateTo({ url: '/pages/login/login' })
		},

		/**
		 * 统一的"掉线"处理：真过期就不要摆一堆红字说"请求失败"，
		 * 直接把页面切回软门槛 + 去登录。
		 * @returns {boolean} true 表示这是掉线（调用方别再写错误文案）
		 */
		noteAuth(e) {
			if (!(e instanceof ApiError) || !e.isUnauthorized) return false
			this.unauthorized = true
			this.listError = ''
			this.moreError = ''
			this.sectionsError = ''
			this.loading = false
			this.loadingMore = false
			return true
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

.head {
	margin-bottom: $mw-space-4;
}
.h1 {
	display: block;
	font-size: $mw-font-h1;
	font-weight: 700;
	color: $mw-ink;
	letter-spacing: -0.02em;
}
.sub {
	display: block;
	margin-top: $mw-space-1;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}

/* ---- 版块 chips ---- */
.chips {
	white-space: nowrap;
	margin-bottom: $mw-space-3;
}
.chip {
	display: inline-block;
	padding: 8rpx $mw-space-3;
	margin-right: $mw-space-2;
	min-height: 64rpx;
	line-height: 48rpx;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-pill;
	background: $mw-surface;
}
.chip text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-ink-2;
}
.chip--on {
	background: $mw-brand-ink;
	border-color: $mw-brand-ink;
}
.chip--on text {
	color: #ffffff;
}

/* ---- 帖子卡（列表 DTO 没有正文，只有标题 + 封面 + 计数 + 话题） ---- */
.post {
	padding: $mw-space-4;
	margin-bottom: $mw-space-3;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
.post__head {
	display: flex;
	align-items: center;
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
.post__who {
	flex: 1;
	min-width: 0;
	margin-left: $mw-space-2;
	display: flex;
	flex-direction: column;
}
.post__author {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-ink;
}
.post__time {
	font-size: 22rpx;
	color: $mw-muted;
}
.badge {
	margin-left: $mw-space-1;
	padding: 4rpx 12rpx;
	border-radius: $mw-radius-tag;
	background: $mw-canvas;
	font-size: 20rpx;
	font-weight: 600;
	color: $mw-ink-2;
}
.badge--top {
	background: rgba(255, 176, 32, 0.16);
	color: $mw-accent-ink;
}
.post__title {
	display: block;
	margin-top: $mw-space-3;
	font-size: $mw-font-body;
	font-weight: 600;
	line-height: 1.6;
	color: $mw-ink;
}
.post__cover {
	display: block;
	width: 100%;
	height: 320rpx;
	margin-top: $mw-space-3;
	border-radius: $mw-radius-input;
	background: $mw-canvas;
}
.topics {
	display: flex;
	flex-wrap: wrap;
	margin-top: $mw-space-2;
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
.post__foot {
	display: flex;
	flex-wrap: wrap;
	margin-top: $mw-space-3;
	padding-top: $mw-space-2;
	border-top: 1px solid $mw-line;
}
.post__meta {
	margin-right: $mw-space-3;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
	font-variant-numeric: tabular-nums;
}
.post__meta--dim {
	color: $mw-muted;
}

/* ---- 四态：骨架 / 空 / 错 / 门槛 ---- */
.skeleton__card {
	height: 240rpx;
	margin-bottom: $mw-space-3;
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
	padding: $mw-space-2 $mw-space-3;
	margin-bottom: $mw-space-3;
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

/* ---- 列表底部：加载更多 / 重试 / 到底 ---- */
.foot {
	display: flex;
	align-items: center;
	justify-content: center;
	padding: $mw-space-3 0 $mw-space-4;
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