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
							<text class="card__author" @click="goUser(view.uid)">{{ view.author }}</text>
							<text class="card__time">{{ view.timeText }}</text>
						</view>
					</view>

					<text v-if="view.hasContent" class="card__content">{{ view.content }}</text>

					<!--
						图片：**一张能渲染的图都没有 → 整块不渲染**（不是 display:none、也不是 height:0 遮丑）；
						1 张 = 大图（保留原始长宽比、按真实比例限最大高，不裁成方块、不拉成长条）；
						2 张一行两张 / 3 张一行三张 / 4 张 2×2 / 5-9 张 3×3 / >9 张只排 9 格、第 9 格右下角盖 +N。
						格子正方形裁切（aspectFill）+ 间距一致；点开大图**从点的那一张开始**（previewImage 的 current）。
						加载失败、或 load 回来是 8×8 占位图（真实脏数据：post 181）→ 把这张收掉。

						⚠️ 2026-09-21 真机排错：图片区改成**与列表卡共用的 `PostImages` 组件**
						（三份重复代码各错一次、各修一次必然再分叉）。列数由张数决定、格子宽度**相对容器**，
						不再有"按假定卡片宽硬算的 rpx 边长"（那正是"9 张排成 2 列"的根因）。
					-->
					<!-- 🔴 详情页单图**不套「最宽 = 一行两格（媒体区 2/3）」上限**：`:wide="true"`。
					     依据（2026-09-21 用户定稿）：列表里单图最宽 = 一行两格；「大图预览与详情页里的图不受此限」。 -->
					<PostImages
						v-if="view.hasImages"
						:images="view.images"
						:grid="view.grid"
						:rows="view.rows"
						:single-failed="view.singleFailed"
						:wide="true"
						@preview="previewImage"
						@fail="onImageFail"
					/>

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
							<view class="act__icon">
								<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor"
									stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
									<path d="M12 20s-7-4.4-7-9.3A4.2 4.2 0 0 1 12 8a4.2 4.2 0 0 1 7 2.7C19 15.6 12 20 12 20z"
										:fill="view.liked ? 'currentColor' : 'none'" />
								</svg>
							</view>
							<text>{{ view.likeText }}</text>
						</view>
						<view class="act" :class="{ 'act--on': view.favorited }" @click="toggleFavorite">
							<view class="act__icon">
								<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor"
									stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
									<path d="M6 4h12v16l-6-4.4L6 20z"
										:fill="view.favorited ? 'currentColor' : 'none'" />
								</svg>
							</view>
							<text>{{ view.favoriteText }}</text>
						</view>
					</view>
					<text v-if="flash" class="flash">{{ flash }}</text>
				</view>

				<view class="sec">
					<text class="sec__title">评论</text>
					<text class="sec__note">{{ commentsNote }}</text>
				</view>

				<!--
					发评论：**真调 `POST /community/comment`**（body `{postId, content}`），
					成功后把响应里的 `CommentRespDTO` **插到列表头部**并把"评论/回复总数" +1
					（`commentCount` 含回复，所以叫"总数"），**不重拉整页**。
					内容为空在本地就拦下（后端对空内容是 `B000313`，没内容可发，不必白跑一趟网络）；
					其余失败（超 1000 字 `B000314` / 敏感词 `B000318` / 未登录 `B000310` / 帖子没了 `B000301`）
					一律**照实显示后端给的话**（`communityErrorMessage` 映射），不自己编。
				-->
				<view class="compose">
					<input
						class="compose__input"
						type="text"
						:value="commentDraft"
						:maxlength="commentMaxLength"
						placeholder="说点什么…"
						placeholder-class="compose__ph"
						confirm-type="send"
						@input="onCommentInput"
						@confirm="postComment"
					/>
					<view class="compose__go" :class="{ 'compose__go--busy': commentPosting }" @click="postComment">
						<text>{{ commentPosting ? '发送中' : '发送' }}</text>
					</view>
				</view>
				<text v-if="commentPostError" class="compose__err">{{ commentPostError }}</text>

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
							<text class="cmt__author" @click="goUser(c.uid)">{{ c.author }}</text>
							<text class="cmt__time">{{ c.timeText }}</text>
						</view>
						<view
							class="cmt__like"
							:class="{ 'cmt__like--on': c.liked, 'cmt__like--busy': c.likeBusy }"
							@click="toggleCommentLike(c.commentId)"
						>
							<view class="cmt__heart"></view>
							<text>{{ c.likeText }}</text>
						</view>
					</view>
					<text class="cmt__content">{{ c.content }}</text>

					<view v-if="c.likeError" class="inline-err">
						<text class="inline-err__text">{{ c.likeError }}</text>
						<text class="inline-err__act" @click="toggleCommentLike(c.commentId)">重试</text>
					</view>

					<!-- 回复：默认用接口给的正序预览；展开后用游标分页拉回来的完整列表（两者同向） -->
					<view v-if="c.replyOpen ? c.replyItems.length : c.children.length" class="reps">
						<view
							v-for="r in (c.replyOpen ? c.replyItems : c.children)"
							:key="r.commentId"
							class="rep"
						>
							<view class="rep__main">
								<text class="rep__who" @click="goUser(r.uid)">{{ r.author }}</text>
								<text v-if="r.replyToText" class="rep__to">{{ r.replyToText }}</text>
								<text class="rep__text">{{ r.content }}</text>
							</view>
							<view
								class="rep__like"
								:class="{ 'rep__like--on': r.liked, 'rep__like--busy': r.likeBusy }"
								@click="toggleCommentLike(r.commentId)"
							>
								<view class="cmt__heart"></view>
								<text>{{ r.likeText }}</text>
							</view>
							<text v-if="r.likeError" class="rep__err">{{ r.likeError }}</text>
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

				<!--
					评论底部：**进页面就把评论循环拉完了**（用户要求"默认显示所有的评论"），
					所以这里**没有**"加载更多评论"这类分页交互，只有两种真实状态：
					① 还在续拉（加载中）；② 拉到一半失败 / 被页数上限截断 → 如实说 + 可重试续上。
				-->
				<view v-if="comments.length" class="foot">
					<text v-if="commentsLoadingMore" class="foot__text">正在加载更多评论</text>
					<view v-else-if="commentsMoreError" class="foot__err">
						<text class="foot__err-text">{{ commentsMoreError }}</text>
						<text class="foot__act" @click="loadComments(false)">重试</text>
					</view>
					<view v-else-if="commentsIncomplete" class="foot__err">
						<text class="foot__err-text">{{ commentsIncompleteText }}</text>
					</view>
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
 * - ⚠️ 读详情**默认**会让后端 `viewCount` +1（不是幂等读）→ 只有真的进这一页才调。
 *   2026-09-21 定稿：详情多了请求参数 `countView`（默认 `true` = 上面的老行为；`false` = 一次都不写 `view_count`），
 *   依据 `CommunityPostController.java:68-77`；本页**不传**它（= 默认 `true`，行为与从前一致）。
 * - 评论是**游标分页**（`{records,size,hasMore,nextCursor}`，**没有 total**）：
 *   一级评论用 `lastId` 触底续拉；每条评论的回复另走 `/comment/reply`（也是游标，原样回传 nextCursor）。
 *   ⚠️ 一级评论的 `parentId` 实测是**数字 `0`**、`children` 是**正序预览**（条数不保证，≤3 不是硬契约）；
 *   回复项的 `children` / `replyCount` 都是 **null** → 渲染前一律判空
 * - `commentCount`（详情 DTO）**把回复也算进去**（实测 178：2 一级 + 2 回复 = 4）→ 文案写明"含回复"
 * - 点赞 / 收藏是**写接口**：后端已用真实账号实测通过（幂等），**本端未真调过** →
 *   页面按乐观更新 + 失败回滚，失败一定把状态改回去并把后端的话显示出来（不静默）
 * - **评论点赞**（2026-09-21 真账号 `probe114904` 实测）：`POST|DELETE /comment/like/{commentId}`，
 *   `CommentRespDTO` 的 `liked`（布尔）与 `likeCount`（数字）**一级评论与回复项都有** → 回复也能点赞。
 *   重复点赞 / 未点赞就取消一律 `code:"0"` 且计数不动；**评论不存在时 `POST` 回 `B000311`，
 *   而 `DELETE` 回 `code:"0"` 静默成功**（不对称，见 `api/community.js`）。
 *   本页同样走乐观更新 + 失败回滚，未登录沿用上面的软门槛，不新造登录弹窗。
 *
 * 性能取舍：**整页只挂一个 `<video>` 元素**，默认第一个视频、不自动播放，
 * 其余视频靠按钮切换 src。一屏挂一堆 video 会把列表拖垮，也违背"不要自动播一堆"。
 */
import { auth } from '@/store/auth.js'
import { ApiError } from '@/utils/request.js'
import {
	COMMENT_MAX_LENGTH,
	COMMENT_PAGE_SIZE,
	CURSOR_SIZE,
	createComment,
	favoritePost,
	getPost,
	likeComment,
	listComments,
	listReplies,
	unfavoritePost,
	unlikeComment,
	unlikePost,
	likePost
} from '@/api/community.js'
import {
	commentCard,
	communityErrorMessage,
	imageGridOf,
	postDetailView,
	replyCard
} from '@/utils/community.js'
import PostImages from '@/components/PostImages.vue'

/**
 * 「把评论循环拉完」的**页数上限**（防后端一直回 `hasMore=true` 把页面卡死）。
 * 20 页 × `COMMENT_PAGE_SIZE`(50) = 最多 1000 条一级评论；撞上限时界面**如实**说明没拉完。
 */
const COMMENT_PAGE_LIMIT = 20

export default {
	components: { PostImages },
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
			/** 图片运行时状态：`{url:true}` = 这张**真的**加载失败（格子照留、如实提示，**不再剔掉**） */
			brokenImages: {},
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
			/**
			 * 撞到页数上限（`COMMENT_PAGE_LIMIT`）却还有更多 → `true`。
			 * 界面据此**如实**写"还有没拉完的"，**不写"一级评论都在这儿了"**（那是假话）。
			 */
			commentsIncomplete: false,
			/** 发评论的输入框内容（真提交时原样送后端，页面**不自己截断**） */
			commentDraft: '',
			/** 后端允许的评论长度上限（`CommunityConstant.MAX_COMMENT_LENGTH`），只用于输入框 maxlength */
			commentMaxLength: COMMENT_MAX_LENGTH,
			commentPosting: false,
			commentPostError: '',
			/** commentId -> { open, loading, error, items, cursor, hasMore }（回复懒加载状态） */
			replyMap: {},
			/**
			 * commentId -> { liked, count, busy, error }（**评论点赞**的本地乐观状态）
			 *
			 * 只放"用户点过的那几条"：没点过的评论直接吃 DTO 给的 `liked` / `likeCount`（真源优先）。
			 * 键一律在 methods 里创建（`likeState`），computed 里只读 —— 与 `replyMap` 同一套纪律，
			 * 在 computed 里写响应式状态会触发重新求值，容易死循环。
			 */
			likeMap: {},
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
		/** 详情 → 模板字段（摊平在 utils 里，模板只 {{ }}，不调函数）
		 *  ⚠️ 这里再叠一层**运行时图片状态**：加载真失败的格子会被标 `failed`（**整块仍在**），
		 *  然后重算网格（`imageGridOf` 与列表卡共用一份口径）。**一个 url 都没有**才 `hasImages=false`。
		 *  ✅ 图片显示口径：恰好 1 张 → 原比例**不裁切**；≥2 张 → **裁成方形缩略格**（点开看原图）。
		 *  正文（`content`）**不折叠**：微博 / QQ空间也是"列表折叠、点进详情看全文"。
		 */
		view() {
			if (!this.detail) return null
			const v = postDetailView(this.detail, this.inter)
			const img = imageGridOf(v, { broken: this.brokenImages })
			v.images = img.images
			v.hasImages = img.hasImages
			v.grid = img.grid
			v.cells = img.cells
			v.rows = img.rows
			v.crop = img.crop
			v.singleFailed = img.singleFailed
			return v
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
			const likes = this.likeMap
			const self = this
			return this.rawComments.map(function (c) {
				const card = commentCard(c)
				const st = map[c.commentId]
				const preview = card.children.length
				const items = st && st.items ? st.items : []
				card.replyOpen = !!(st && st.open)
				card.replyLoading = !!(st && st.loading)
				card.replyError = (st && st.error) || ''
				card.replyItems = items.map(replyCard)
				card.replyHasMore = !!(st && st.hasMore)
				// 回复全在预览里就不用给按钮；否则给"查看全部 N 条回复"。
				// 预览条数不保证（"最多 3 条"不是硬契约）→ 用 replyCount 与预览实际条数比
				card.showReplyToggle = card.replyCount > preview || card.replyOpen
				card.replyToggleText = '查看全部 ' + card.replyCount + ' 条回复'
				// 点赞：本地乐观状态优先，没点过的直接吃 DTO 给的 liked/likeCount（真源）
				self.withLike(card, c, likes)
				// 回复项同样能点赞（实测 CommentRespDTO 对回复也带 liked/likeCount）→ 逐条盖上。
				// 卡片与原始 DTO 同序同长（都是同一数组 map 出来的），所以按下标取 DTO 是安全的
				card.children = card.children.map(function (rc, i) {
					return self.withLike(rc, c.children[i], likes)
				})
				card.replyItems = card.replyItems.map(function (rc, i) {
					return self.withLike(rc, items[i], likes)
				})
				return card
			})
		},
		commentsNote() {
			return '一级评论按时间倒序（新的在前），回复与回复预览都按时间正序（旧的在前）。点「查看全部回复」展开这条评论。'
		},
		commentsEndText() {
			// `view.commentText` 那个数是 `commentCount`（**含回复**），这里的数是一级评论条数
			// → 两者口径不同，文案必须写清是"一级评论"，别让人以为对不上
			return this.rawComments.length ? '一级评论都在这儿了，共 ' + this.rawComments.length + ' 条' : ''
		},
		/** 被页数上限截断时的**如实**说明：不说"全部"，也不摆一个"查看更多"按钮（用户明确不要分页交互） */
		commentsIncompleteText() {
			return '已经加载了 ' + this.rawComments.length + ' 条一级评论，这条帖子还有更多没拉完'
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
		// 评论**进页面就已经循环拉完了**（见 loadComments），这里不再负责续拉评论；
		// 只有"被页数上限截断 / 中途失败"时才用底部的重试接着拉。
		if (this.needLogin) return
		if (this.commentsIncomplete && !this.commentsLoading && !this.commentsLoadingMore && !this.commentsMoreError) {
			this.loadComments(false)
		}
	},
	methods: {
		async loadAll() {
			this.unauthorized = false
			await this.loadDetail()
			// 详情拿到了才拉评论（评论接口要 postId；帖子不存在就没必要发第二个请求）
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
		 * 拉评论：**默认把这条帖子的评论全拉完**。
		 *
		 * 用户 2026-09-21 定稿：「帖子里**默认显示所有的评论**就可以了」→ 进页面就把评论全部加载并显示，
		 * **不要**"查看更多 / 展开 / 分页"这类交互。
		 *
		 * 接口能力（读源码）：`GET /community/comment` 是**游标分页**
		 * （`lastId` = 上一页的 `nextCursor`，按 `commentId` **倒序**），`size` 由后端 `normalizeSize`
		 * 归一化：null/<1 → `DEFAULT_PAGE_SIZE = 10`，上限 `MAX_PAGE_SIZE = 50`
		 * （`CommentServiceImpl.java:380`）→ **一次拿不完**，所以这里**循环续拉直到 `hasMore=false`**。
		 * 每次都用满 `COMMENT_PAGE_SIZE`(=上限 50) 减少往返。
		 * ⚠️ 循环有页数上限 `COMMENT_PAGE_LIMIT`（防后端一直回 `hasMore=true` 把页面卡死）：
		 * 真被上限截断时 `commentsIncomplete = true`，界面**如实说"还有没拉完的"**，**不假装"全部"**。
		 *
		 * @param {boolean} reset true = 从头拉（进页面 / 重试），false = 接着上次的游标继续
		 */
		async loadComments(reset) {
			if (!this.postId) return
			if (reset) {
				this.commentsLoading = true
				this.commentsError = ''
				this.commentsMoreError = ''
				this.commentsIncomplete = false
				this.commentsCursor = null
				this.commentsHasMore = true
				this.rawComments = []
				// 整份评论被换掉 → 评论点赞的本地乐观状态一并作废，重新以接口的 liked/likeCount 为准
				this.likeMap = {}
			} else if (!this.commentsHasMore) {
				// 没有更多（或没有游标）就别硬拉，否则会重复请求最后一页
				return
			} else {
				this.commentsLoadingMore = true
				this.commentsMoreError = ''
			}
			let rounds = 0
			try {
				do {
					const page = await listComments({
						postId: this.postId,
						lastId: this.commentsCursor === null || this.commentsCursor === undefined ? undefined : this.commentsCursor,
						size: COMMENT_PAGE_SIZE
					})
					const records = (page && page.records) || []
					this.rawComments = this.rawComments.concat(records)
					// ⚠️ 游标只认响应字段：`hasMore=true` 但 `nextCursor` 为空 = 死循环入口，立刻收尾
					const next = page ? page.nextCursor : null
					this.commentsHasMore = !!(page && page.hasMore) && next !== null && next !== undefined
					this.commentsCursor = next
					rounds++
				} while (this.commentsHasMore && rounds < COMMENT_PAGE_LIMIT)
				// 撞到页数上限还有更多 → **如实标记**（界面会写"还没拉完"，不写"已到末尾"）
				if (this.commentsHasMore) this.commentsIncomplete = true
			} catch (e) {
				if (this.noteAuth(e)) return
				const text = communityErrorMessage(e)
				// 一条都没拉到 = 首屏失败（说清原因）；拉到一半失败 = 底部续拉失败（可重试续上）
				if (reset && !this.rawComments.length) this.commentsError = text
				else this.commentsMoreError = text
			} finally {
				this.commentsLoading = false
				this.commentsLoadingMore = false
			}
		},

		/** 输入框内容变化（`input` 事件在 uni-app 里是 `e.detail.value`） */
		onCommentInput(e) {
			const d = (e && e.detail) || {}
			this.commentDraft = d.value === undefined || d.value === null ? '' : String(d.value)
		},

		/**
		 * 发评论：**真调 `POST /community/comment`**，成功后把返回的评论插到列表头部 + 总数 +1。
		 *
		 * - 空内容：本地拦下（没内容可发；后端对空内容是 `B000313`）→ 不算"请求失败"，文案直说
		 * - 其余失败：**照实显示后端的话**（超长 `B000314` / 敏感词 `B000318` / 掉线 `B000310` / 帖子没了 `B000301`）
		 * - 掉线（`noteAuth`）→ 切软门槛 + 去登录，不摆一堆红字
		 * - 🔴 成功之后**不重拉整页**：响应就是一条 `CommentRespDTO`，直接插到头部
		 */
		async postComment() {
			const text = String(this.commentDraft || '').trim()
			if (!text) {
				// 空内容不发请求：这不是"网络失败"，如实说清"先写点什么"
				this.commentPostError = '先写点什么再发'
				return
			}
			if (this.commentPosting) return
			this.commentPosting = true
			this.commentPostError = ''
			try {
				const created = await createComment({ postId: this.postId, content: text })
				// 后端回的就是评论对象；万一形状不对（脏响应）也不许编一条假的进列表
				if (created && created.commentId) {
					this.rawComments = [created].concat(this.rawComments)
					this.inter.commentCount = (Number(this.inter.commentCount) || 0) + 1
					this.commentDraft = ''
					this.flash = '评论发出去了'
				} else {
					this.commentPostError = '评论接口这次没有返回评论内容，去评论列表看看是否已经发出去'
				}
			} catch (e) {
				if (this.noteAuth(e)) {
					this.commentPosting = false
					return
				}
				this.commentPostError = communityErrorMessage(e)
			} finally {
				this.commentPosting = false
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

		/* ---------------- 评论点赞（2026-09-21 实测过的写接口） ---------------- */

		/**
		 * 把评论点赞的本地乐观状态盖到摊平后的卡片上。
		 *
		 * **只读** `likeMap`（不建键）：键一律由 `likeState` 在点击时创建。
		 * @param {object} card commentCard / replyCard 的产物
		 * @param {object} dto 这一条对应的**原始 CommentRespDTO**（没点过时以它为准）
		 * @param {object} likes this.likeMap
		 */
		withLike(card, dto, likes) {
			const st = likes && likes[card.commentId]
			const liked = st ? !!st.liked : !!(dto && dto.liked)
			const count = st ? Number(st.count) || 0 : Math.max(0, Number(dto && dto.likeCount) || 0)
			card.liked = liked
			card.likeCount = count
			card.likeText = (liked ? '已赞 ' : '赞 ') + count
			card.likeBusy = !!(st && st.busy)
			card.likeError = (st && st.error) || ''
			return card
		},

		/**
		 * 评论点赞的本地状态（在 methods 里创建键，computed 里只读）。
		 *
		 * 初值取**接口给的真源**：从已加载的评论 / 回复预览 / 回复桶里找这条 DTO。
		 * 找不到（不该发生）就按"未点赞 + 0"起步，**不编数字**。
		 */
		likeState(commentId) {
			if (!this.likeMap[commentId]) {
				const dto = this.findCommentDto(commentId)
				this.likeMap[commentId] = {
					liked: !!(dto && dto.liked),
					count: Math.max(0, Number(dto && dto.likeCount) || 0),
					busy: false,
					error: ''
				}
			}
			return this.likeMap[commentId]
		},

		/** 在"一级评论 / 预览回复 / 已展开的回复"里找一条原始 DTO */
		findCommentDto(commentId) {
			for (let i = 0; i < this.rawComments.length; i++) {
				const c = this.rawComments[i]
				if (c.commentId === commentId) return c
				const children = Array.isArray(c.children) ? c.children : []
				for (let j = 0; j < children.length; j++) {
					if (children[j].commentId === commentId) return children[j]
				}
			}
			const keys = Object.keys(this.replyMap)
			for (let k = 0; k < keys.length; k++) {
				const items = this.replyMap[keys[k]].items || []
				for (let i = 0; i < items.length; i++) {
					if (items[i].commentId === commentId) return items[i]
				}
			}
			return null
		},

		/**
		 * 评论 / 回复的点赞与取消（写接口，**2026-09-21 真账号实测过**）
		 *
		 * 乐观更新：先改本地，失败再回滚，并把后端的话显示在这条评论下面（+ toast）。
		 * 与帖子点赞同一套写法；未登录沿用本页既有的软门槛，**不新造登录弹窗**。
		 */
		async toggleCommentLike(commentId) {
			if (this.needLogin) {
				this.toast('先登录再点赞')
				return
			}
			if (!commentId) return
			const st = this.likeState(commentId)
			if (st.busy) return
			const next = !st.liked
			const before = st.count
			st.liked = next
			st.count = Math.max(0, before + (next ? 1 : -1))
			st.busy = true
			st.error = ''
			try {
				if (next) await likeComment(commentId)
				else await unlikeComment(commentId)
			} catch (e) {
				st.liked = !next
				st.count = before
				const text = communityErrorMessage(e)
				st.error = (next ? '点赞没成功：' : '取消点赞没成功：') + text
				// 写操作掉线要显式处理：光说"登录已过期"却不给下一步，用户会卡在原地反复点
				if (this.noteAuth(e)) {
					this.toast('登录已过期，请重新登录')
					return
				}
				this.toast(text)
			} finally {
				st.busy = false
			}
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

		/**
		 * 点图看大图（`previewImage`）：`current` 必须是**点的那一张**。
		 *
		 * 🔴 传进去的是 `view.images` = **原图 url**：卡片/详情里的 `aspectFill` 只裁**缩略格**，
		 * 点开看到的是**完整原图**（用户定稿要求）。
		 */
		previewImage(index) {
			const urls = this.view ? this.view.images : []
			if (!urls.length) return
			const at = Number(index) > 0 && Number(index) < urls.length ? Number(index) : 0
			uni.previewImage({ urls: urls, current: urls[at] })
		},

		/**
		 * 图片**真的**加载失败（`@error`）→ 记下这张 url；`view` 里这一格标成 `failed`，
		 * 组件**留在原地**写「图片加载失败」（2026-09-21 定稿：**不再剔掉**，剔掉就等于"静默当没图"）。
		 */
		onImageFail(url) {
			if (!url) return
			if (this.brokenImages[url]) return
			this.brokenImages = Object.assign({}, this.brokenImages, { [url]: true })
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

		/**
		 * 点作者名进他的主页（uid 只能从社区 DTO 带过去：`GET /user/{username}` 的 id 恒 null、
		 * user 模块没有按 uid 查的接口 → 页面参数是唯一入口）。没带 uid 的旧数据不跳，免得进空页。
		 */
		goUser(uid) {
			if (!uid) return
			uni.navigateTo({ url: '/pages/user/profile?uid=' + uid })
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
			// 评论点赞是逐条的行内报错：掉线时同样清掉，别让"点赞没成功：登录已过期"挂一屏
			const ids = Object.keys(this.likeMap)
			for (let i = 0; i < ids.length; i++) {
				this.likeMap[ids[i]].error = ''
				this.likeMap[ids[i]].busy = false
			}
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
	background: rgba(194, 65, 12, 0.1);
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

/* ---- 图片区已搬进共用组件 `components/PostImages.vue` ----
 * 1 张大图 / 2 张一行两张 / 3 张一行三张 / 4 张 2×2 / 5-9 张 3×3 / >9 张第 9 格盖 +N。
 * ⚠️ 无图时**整块不渲染**（模板是 `v-if="view.hasImages"`），组件里**不写** display:none / height:0 的兜底，
 *    否则就成了"渲染了但看不见"——那正是本项目吃过亏的写法。
 * 🔴 这里**故意不再有** `.imgs__cell / .imgs__one / .imgs--*` 那套样式：
 *    它们是三份重复代码中的一份（"9 张排成 2 列"的现场），留在页面上就会有人照着改回来。
 */

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
	background: rgba(74, 118, 56, 0.12);
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
/* 自绘 SVG 图标（与 tabbar / 列表卡同一套方案）；颜色走 `currentColor`，不写死色值 */
.act__icon {
	display: flex;
	align-items: center;
	justify-content: center;
	margin-right: 6rpx;
	color: $mw-ink;
}
.act--on {
	background: $mw-brand-ink;
	border-color: $mw-brand-ink;
}
.act--on text {
	color: $mw-on-night;
}
.act--on .act__icon {
	color: $mw-on-night;
}
/* ---- 发评论：输入框 + 发送（真调 POST /community/comment；触达 ≥ $mw-touch-min） ---- */
.compose {
	display: flex;
	align-items: center;
	margin-top: $mw-space-2;
}
.compose__input {
	flex: 1;
	height: $mw-touch-min;
	padding: 0 $mw-space-3;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-input;
	font-size: $mw-font-caption;
	color: $mw-ink;
}
.compose__ph {
	color: $mw-muted;
}
.compose__go {
	margin-left: $mw-space-2;
	min-width: 128rpx;
	height: $mw-touch-min;
	padding: 0 $mw-space-3;
	display: flex;
	align-items: center;
	justify-content: center;
	background: $mw-brand-ink;
	border-radius: $mw-radius-pill;
}
.compose__go text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-on-night;
}
/* 提交中：降低不透明度（**不写死颜色**），点击在方法里被 `commentPosting` 挡住 */
.compose__go--busy {
	opacity: 0.6;
}
.compose__err {
	display: block;
	margin-top: $mw-space-1;
	font-size: $mw-font-micro;
	line-height: 1.6;
	color: $mw-danger;
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
	flex: none;
	display: flex;
	align-items: center;
	justify-content: center;
	/* 触控下限 88rpx ≈ 44px：点赞是可点区域，不能只是个小数字 */
	min-width: $mw-touch-min;
	min-height: $mw-touch-min;
	padding: 0 $mw-space-1;
	border-radius: $mw-radius-pill;
	font-size: 22rpx;
	color: $mw-muted;
	font-variant-numeric: tabular-nums;
}
.cmt__like--on {
	color: $mw-brand-ink;
}
.cmt__like--busy {
	opacity: 0.5;
}
/* 自绘小桃心（素材已废弃，不用 PNG），只拿边框画，配色全走既有令牌 */
.cmt__heart {
	width: 16rpx;
	height: 16rpx;
	margin-right: 6rpx;
	border: 2rpx solid currentColor;
	border-radius: 50%;
	flex: none;
}
.cmt__like--on .cmt__heart,
.rep__like--on .cmt__heart {
	background: currentColor;
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
	display: flex;
	flex-wrap: wrap;
	align-items: flex-start;
	margin-bottom: $mw-space-1;
}
.rep:last-child {
	margin-bottom: 0;
}
.rep__main {
	flex: 1;
	min-width: 0;
}
.rep__like {
	flex: none;
	display: flex;
	align-items: center;
	justify-content: flex-end;
	min-width: $mw-touch-min;
	min-height: $mw-touch-min;
	padding-left: $mw-space-1;
	font-size: 22rpx;
	color: $mw-muted;
	font-variant-numeric: tabular-nums;
}
.rep__like--on {
	color: $mw-brand-ink;
}
.rep__like--busy {
	opacity: 0.5;
}
.rep__err {
	display: block;
	width: 100%;
	padding-bottom: $mw-space-1;
	font-size: 22rpx;
	line-height: 1.5;
	color: $mw-danger;
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