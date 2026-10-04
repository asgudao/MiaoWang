<template>
	<view class="page">
		<view class="head">
			<text class="h1">社区</text>
			<text class="sub">{{ headSub }}</text>
		</view>

		<!--
			搜索入口：**真有去处**（pages/community/search），不是死按钮。
			未登录时也带着走 —— 搜索页自己有 /login 软门槛，与社区页是同一套约定；
			这里不置灰、不弹"请先登录"，免得两个页面各说一套登录话术。
		-->
		<view class="search-entry" @click="goSearch">
			<!-- 放大镜：素材已全部废弃，也不引图标库 → **base64 data-URI 背景图**
			     （24×24 viewBox / stroke 1.7 / 圆头圆角，18px 盒 = `mw-ic--sm`）。
			     🔴 2026-09-22 实测：App 端内联 `<svg>` 落在 HTML 命名空间 → 永远画不出来（见 styles/icons.scss 抬头） -->
			<view class="search-entry__icon mw-ic mw-ic--sm mw-ic--search-muted"></view>
			<text class="search-entry__text">搜索帖子</text>
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

				<!--
					卡片**纵向分区顺序是契约**（门禁按真实渲染结构逐段比对，调换会变红）：
					① 用户信息 → ② 标题（**完整显示，不折叠**）→ ③ 内容摘要 → ④ 内容展开
					→ ⑤ 图片 → ⑥ 操作区（点赞 / 收藏 / 评论条数 + 进详情入口）。
					🔴 **卡片上不渲染任何评论内容**（2026-09-21 用户定稿）：评论的看与发都在详情页，
					卡片外面**只显示有多少条评论**。
				-->
				<view v-for="p in cards" :key="p.postId" class="post" @click="goDetail(p.postId)">
					<!-- ① 用户信息（头像位拿显示名首字：社区 DTO 没有头像字段，不是"没加载出来"） -->
					<view class="post__head">
						<view class="avatar"><text>{{ p.initial }}</text></view>
						<view class="post__who">
							<text class="post__author" @click.stop="goUser(p.uid)">{{ p.author }}</text>
							<text class="post__time">{{ p.timeText }}</text>
						</view>
						<!-- ⚠️ **2026-09-22 口径变更（用户：「不要在帖子上方显示含几个视频，web 和手机都不要」）**：
						     原来这里还有一枚 `{{ p.videoText }}`（"含 N 个视频"）**已按用户要求删掉**
						     —— 视频现在就在下面的九宫格里看得见，再写一行数量是噪音。
						     ⚠️ **置顶标签保留**；⚠️ `utils/community.js` 的 `videoText` 字段**保留**（数据层不动、
						        其它消费方/门禁仍可读），这里只是**不再渲染**它。 -->
						<text v-if="p.isTop" class="badge badge--top">置顶</text>
					</view>

					<!--
						② 标题：**完整显示，不折**（2026-09-21 用户要求）。
						真标题只有 14~19 字，按 40 字折标题只会让人看不全；
						折叠的对象改成下面的**正文摘要**（③④）。
					-->
					<text class="post__title">{{ p.title }}</text>

					<view v-if="p.hasTopics" class="topics">
						<text v-for="(t, ti) in p.topics" :key="ti" class="topic">#{{ t }}</text>
					</view>

					<!--
						③ 内容摘要：字段由后端 2026-09-21 定稿（`contentExcerpt` + `contentTruncated`）。
						⚠️ **没有这个字段就整块不渲染**（不是渲染空框，更不许拿标题冒充摘要）；
						折叠/省略号是这一块的事（服务端给的是**纯前缀、不带省略号**）。
					-->
					<text
						v-if="p.hasExcerpt"
						:id="'exc-' + p.postId"
						class="post__excerpt"
						:class="{ 'post__excerpt--clamp': p.excerptClamped }"
					>{{ p.excerptText }}</text>

					<!--
						④ 内容展开：懒加载**详情**接口拿全文，🔴 **必须带 `countView=false`**
						（否则"点一下展开就涨一次浏览量"，把浏览数据污染掉）。
						失败 → **保留摘要** + 在这里如实说清原因，不假装展开成功。
					-->
					<view v-if="p.hasExcerpt" class="post__expand" @click.stop="toggleContent(p.postId)">
						<text>{{ p.excerptLoading ? '正文加载中' : p.excerptToggleText }}</text>
					</view>
					<text v-if="p.excerptError" class="post__err">{{ p.excerptError }}</text>

					<!--
						⑤ 媒体区（图片 + **视频**）：三处（信息流 / 搜索 / 详情）**共用 `components/PostImages.vue`**。
						⚠️ **2026-09-22**：判据从 `hasImages` 换成 **`hasMedia`**（图片 + 视频的合计个数）——
						   改前 0 图 + N 视频的帖子（真实数据 198）这里**整块不渲染**，列表里什么都看不到；
						   现在视频进同一套九宫格（列数由**合计个数**决定、格子宽度相对容器，没有第二套几何）。
						   点视频格 = 进详情页播（`@open`），列表里不内联播放。
					-->
					<PostImages
						v-if="p.hasMedia"
						:media="p.media"
						:grid="p.grid"
						:rows="p.rows"
						:single-failed="p.imageSingleFailed"
						@preview="previewPic(p.images, $event)"
						@fail="onImageFail"
						@open="goDetail(p.postId)"
					/>

					<!--
						⑥ 操作区：点赞 / 收藏 / 评论条数 + 进详情入口 —— 三个都是**真操作**。
						- 点赞、收藏：真调 `POST|DELETE /community/like|favorite/{postId}`（后端两个方向都有，
						  依据 `CommunityInteractionController.java:27/36/45/54`），**乐观更新 + 失败回滚**；
						  DTO 没给 `liked`/`favorited` 时**不显示选中态**（不许瞎猜"已点赞"）。
						- 评论：卡片上**不渲染任何评论内容**，只显示条数，点它**真跳详情页**（看/发都在那里）。
						触控目标都是 `min-height: $mw-touch-min` 的按钮，不是挤在一起的小字。
					-->
					<view class="acts">
						<view class="act" :class="{ 'act--on': p.likedOn }" @click.stop="toggleLike(p.postId)">
							<!-- 图标两态 = **两套 data-URI**：未赞 = 空心 $mw-ink-2 → 已赞 = 实心 $mw-brand-ink -->
							<view class="act__icon mw-ic"
								:class="p.likedOn ? 'mw-ic--heart-brand-fill' : 'mw-ic--heart-ink2'"></view>
							<text class="act__text">{{ p.likeText }}</text>
						</view>
						<view class="act" :class="{ 'act--on': p.favoritedOn }" @click.stop="toggleFavorite(p.postId)">
							<!-- 收藏同理：未藏空心 → 已藏实心（色 + 填充双差异） -->
							<view class="act__icon mw-ic"
								:class="p.favoritedOn ? 'mw-ic--mark-brand-fill' : 'mw-ic--mark-ink2'"></view>
							<text class="act__text">{{ p.favoriteText }}</text>
						</view>
						<view class="act" @click.stop="openComments(p.postId)">
							<!-- 评论：单一态（$mw-ink-2，与选中态的橙色拉开） -->
							<view class="act__icon mw-ic mw-ic--chat-ink2"></view>
							<text class="act__text">{{ p.commentActionText }}</text>
						</view>
						<!--
							浏览量：**只读元信息**（不是按钮，没有 @click），但一样要有配套图标 ——
							用户真机反馈的就是「浏览量没有配套图标」。图标与数字**并存**（不折成只剩图标），
							与三个操作**同一族**（24×24 viewBox / stroke 1.7 / 圆头圆角），
							但画法已统一改成 **base64 data-URI 背景图**（App 端内联 svg 落在 HTML 命名空间 → 不渲染，
							根因见 `styles/icons.scss` 抬头）。
						-->
						<view class="post__meta post__meta--dim">
							<view class="post__meta-icon mw-ic mw-ic--eye-muted"></view>
							<text class="post__meta-text">{{ p.viewText }}</text>
						</view>
					</view>
					<text v-if="p.actionError" class="post__err">{{ p.actionError }}</text>
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
import PostImages from '@/components/PostImages.vue'
import { hideNativeTabBar } from '@/utils/tabbar.js'
import { auth } from '@/store/auth.js'
import { ApiError } from '@/utils/request.js'
import {
	PAGE_SIZE,
	favoritePost,
	getPost,
	likePost,
	listPosts,
	listSections,
	unfavoritePost,
	unlikePost
} from '@/api/community.js'
import { cardView, communityErrorMessage, excerptOverflow, postActionView } from '@/utils/community.js'

export default {
	components: { FloatingTabBar, PostImages },
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
			unauthorized: false,
			/**
			 * 本页这一份数据属于哪个账号（= 上次加载时登录态里的 username）。
			 *
			 * ⚠️ 社区是**常驻 tab 页**：切 tab 只走 onHide/onShow，**不会 onUnload**，
			 * 所以 posts / pageNum / loadedOnce 全都活在实例里。换账号后必须靠这个字段
			 * 认出"手里这份数据已经是上一位身份的"（帖子里的 liked/favorited 是按旧 token 算的）。
			 */
			sessionOwner: '',
			/**
			 * 图片的**运行时状态**（每张 url 一份，跨帖子共用一张表）：
			 * - `brokenImages[url] = true`：这张**真的**加载失败（`@error`）→ 该格**留在原地**并如实写
			 *   「图片加载失败」（2026-09-21 定稿：**不许静默当"没图"**；也不再把图从列表里剔掉）
			 * ⚠️ 状态**不进 `postCard`**（那是纯函数，别的切片按它断言），只在 `cards` 里叠加。
			 * ⚠️ 不再需要 `imageSizes`（没有"按自然尺寸算盒高"这件事了：单图 `widthFix` 自己保比例）
			 */
			brokenImages: {},
			/**
			 * `postId -> { open, loading, text, error }`：**正文展开**的懒加载状态。
			 * 🔴 只在用户点了「内容展开」之后才去读详情，并且**必须带 `countView=false`**
			 * （不写 `view_count`）—— 否则"在列表里摊开看看"会变成"进帖子看了一次"。
			 */
			contentMap: {},
			/**
			 * `postId -> true`：**实测**"摘要渲染超过 5 行"（`uni.createSelectorQuery` 量出来的）。
			 * 🔴 只用字符数猜"超没超 5 行"在不同屏宽/字号下会错 → 这里量**真实渲染高度**，
			 * 拿 `5 × EXCERPT_LINE_RPX`（rpx→px 按当前窗口换算）当阈值（`excerptOverflow()`）。
			 * ⚠️ 平台没有 `createSelectorQuery`（或量不到）→ **什么都不写**，如实退回"只看服务端
			 * `contentTruncated`"（`excerptHasToggle` 仍可能为 true），不假装量过。
			 */
			excerptOverflowMap: {},
			/**
			 * `postId -> { liked, likeCount, favorited, favoriteCount, busy, error }`：
			 * **点赞 / 收藏的本地状态**（只在用户真的点过之后才写）。
			 * 🔴 DTO 没给 `liked`/`favorited` 时**不瞎猜"已点赞"**（界面不显示选中态），
			 * 点一下才按"未选中 → 选中"乐观更新，失败**回滚**到点之前的值。
			 * ⚠️ **卡片上不渲染任何评论内容**（评论的看与发都在详情页），所以这里没有评论状态。
			 */
			actionMap: {}
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
			const broken = this.brokenImages
			const content = this.contentMap
			const action = this.actionMap
			const over = this.excerptOverflowMap
			return this.posts.map(function (p) {
				const c = content[p.postId]
				return cardView(p, {
					broken: broken,
					// 实测结论按 postId 并进"正文展开"的运行时状态（`cardExcerptView` 只读 `overflow === true`）
					content: Object.assign({}, c, { overflow: over[p.postId] === true }),
					action: action[p.postId]
				})
			})
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
		this.sessionOwner = auth.state.username
		this.loadAll()
	},
	onShow() {
		hideNativeTabBar()
		// 换账号（退出登录 → 登录另一个号）：常驻 tab 页不会重建，论坛流里的 liked/favorited
		// 是按上一位身份的 token 算出来的，必须把整个分页状态机复位再重拉。
		// 复位后由下面的 `!loadedOnce` 分支发请求（onShow 的时序与"从登录页返回"完全一致）。
		if (auth.state.username !== this.sessionOwner) {
			this.sessionOwner = auth.state.username
			this.resetFeed()
		}
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
	/**
	 * 每次渲染完量一次"摘要有没有真的超过 5 行"（**真溢出判断**，不是拿字符数猜）。
	 * ⚠️ 只量**还不知道结论**的卡片 → 量完写进 `excerptOverflowMap` 会再触发一次渲染，
	 *    但那时这些 id 已被过滤掉 → **不会再发查询，不会死循环**。
	 * ⚠️ 平台不支持 `createSelectorQuery` 时直接返回：如实退回"只看服务端 `contentTruncated`"。
	 */
	updated() {
		this.measureExcerpts()
	},
	methods: {
		/**
		 * 实测摘要高度 → `excerptOverflow()` 判断是否超过 5 行（详见 `utils/community.js`）。
		 * 量不到（平台不支持 / 节点还没渲染出来）就**什么都不写**（保持"未测量"），不假装量过。
		 */
		measureExcerpts() {
			if (typeof uni === 'undefined' || typeof uni.createSelectorQuery !== 'function') return
			if (typeof uni.getSystemInfoSync !== 'function') return
			const known = this.excerptOverflowMap
			const ids = []
			for (let i = 0; i < this.posts.length; i++) {
				const p = this.posts[i]
				if (!p || !p.postId) continue
				if (known[p.postId] !== undefined) continue
				if (!cardView(p, {}).hasExcerpt) continue
				ids.push(p.postId)
			}
			if (!ids.length) return
			const w = Number((uni.getSystemInfoSync() || {}).windowWidth) || 0
			if (!(w > 0)) return
			try {
				const q = uni.createSelectorQuery().in(this)
				ids.forEach(function (id) { q.select('#exc-' + id).boundingClientRect() })
				q.exec((res) => {
					const list = Array.isArray(res) ? res : []
					const patch = {}
					for (let i = 0; i < ids.length; i++) {
						const r = list[i]
						// 量不到（null）→ 不写结论（下次渲染还会再试），也不当"没超"
						if (!r || !(Number(r.height) > 0)) continue
						patch[ids[i]] = excerptOverflow(r.height, w)
					}
					if (Object.keys(patch).length) {
						this.excerptOverflowMap = Object.assign({}, this.excerptOverflowMap, patch)
					}
				})
			} catch (e) {
				// 测量失败不影响页面本身（列表照常显示，只是少一个「展开」入口的判定依据）
			}
		},
		/**
		 * 把信息流的分页状态机整体复位（换账号专用；切版块那条路走 selectSection 自己的复位块）。
		 * 复位后由 onShow 的 `!loadedOnce` 分支触发 loadAll —— 这里**不发请求**，
		 * 免得和 onShow 的判重逻辑各发一次。
		 */
		resetFeed() {
			this.posts = []
			this.pageNum = 1
			this.total = 0
			this.hasMore = false
			this.loadedOnce = false
			this.loading = false
			this.loadingMore = false
			this.listError = ''
			this.moreError = ''
			// 上一位的"会话过期"标记对新身份没有意义（刚登录成功），留着会把软门槛一直挂在页面上
			this.unauthorized = false
			// 上一位展开过的正文 / 点过的赞与收藏也一起清掉：那些是按旧 token 的身份算出来的状态
			this.contentMap = {}
			this.actionMap = {}
		},

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
			// 换号守卫：请求发出去之后如果账号变了，这份响应属于**上一个身份**，必须丢弃 ——
			// 否则一个迟到的"加载更多"会把上一位的帖子 concat 进新账号的列表里（onShow 的复位挡不住它）。
			const owner = auth.state.username
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
				if (auth.state.username !== owner) return
				this.posts = reset ? records : this.posts.concat(records)
				this.total = Number(page && page.total) || 0
				const pages = Number(page && page.pages) || 0
				// 进度以"这一页的页码"为准：请求发出去时的 pageNum 还没自增
				this.hasMore = this.pageNum < pages
				this.pageNum = this.pageNum + 1
				this.loadedOnce = true
			} catch (e) {
				if (auth.state.username !== owner) return
				if (this.noteAuth(e)) return
				const text = communityErrorMessage(e)
				if (reset) this.listError = text
				else this.moreError = text
			} finally {
				// 换号后新的一轮请求自己会清这两个标记（同 owner 才清，避免把新请求的进行态提前抹掉）
				if (auth.state.username === owner) {
					this.loading = false
					this.loadingMore = false
				}
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

		/**
		 * 进社区搜索页（**真入口**）。
		 * 关键词由搜索页自己管 —— 这里不预填、不带参数，免得两页各持一份"当前关键词"。
		 */
		goSearch() {
			uni.navigateTo({ url: '/pages/community/search' })
		},

		/**
		 * 点作者名进他的主页。`.stop` 是必须的：整张卡片本身就是"进详情"，
		 * 不拦一下会先跳主页再跳详情（栈里多一层）。
		 */
		goUser(uid) {
			if (!uid) return
			uni.navigateTo({ url: '/pages/user/profile?uid=' + uid })
		},

		/**
		 * ③④ 「内容展开 / 收起」：懒加载**详情**接口拿全文。
		 *
		 * 🔴 **必须 `getPost(postId, { countView: false })`**：这只是"在列表里把正文摊开看"，
		 * 不是"进帖子看" —— 不传就是"点一下展开涨一次浏览量"（`CommunityPostController.java:68-77`）。
		 * - 收起：只翻本地状态，**不发请求**
		 * - 展开：拿到过全文就直接展开（不重复请求）；否则请求一次
		 * - 失败 / 详情里没有正文：**摘要留着**，如实说清原因，**不假装展开成功**
		 */
		async toggleContent(postId) {
			const cur = this.contentMap[postId] || {}
			if (cur.open) {
				this.patchContent(postId, { open: false })
				return
			}
			if (cur.loading) return
			if (cur.text) {
				this.patchContent(postId, { open: true, error: '' })
				return
			}
			this.patchContent(postId, { loading: true, error: '' })
			try {
				const detail = await getPost(postId, { countView: false })
				const raw = detail && detail.content
				const text = raw === null || raw === undefined ? '' : String(raw)
				if (!text.trim()) {
					this.patchContent(postId, { loading: false, open: false, error: '这条帖子没有正文可展开' })
					return
				}
				this.patchContent(postId, { loading: false, open: true, text: text, error: '' })
			} catch (e) {
				if (this.noteAuth(e)) {
					this.patchContent(postId, { loading: false, open: false })
					return
				}
				this.patchContent(postId, {
					loading: false,
					open: false,
					error: '正文没读到：' + communityErrorMessage(e)
				})
			}
		},

		/**
		 * ⑥ 评论入口：**卡片上不渲染任何评论内容**，点它**真跳详情页**
		 * （评论的看与发都在详情里；卡片外面只显示有多少条评论 —— 见 `postCommentEntryView`）。
		 * ⚠️ 这是真跳转，不是死按钮：走的就是整卡那条 `goDetail` 路。
		 */
		openComments(postId) {
			this.goDetail(postId)
		},

		/** 点赞（真调接口，见 `toggleReaction`） */
		async toggleLike(postId) {
			await this.toggleReaction(postId, 'like')
		},

		/** 收藏（真调接口，见 `toggleReaction`） */
		async toggleFavorite(postId) {
			await this.toggleReaction(postId, 'favorite')
		},

		/**
		 * 点赞 / 收藏的公共实现：**乐观更新 + 失败回滚 + 未登录跳登录**
		 *
		 * - 接口：`POST|DELETE /community/like|favorite/{postId}`（两个方向后端都有，
		 *   依据 `CommunityInteractionController.java:27/36/45/54`）→ **不是假交互**；幂等（重复点赞也返回 code 0）。
		 * - 🔴 **不许瞎猜"已点赞"**：DTO 的 `liked`/`favorited` 不是布尔时（字段缺失）按"未选中"起步，
		 *   但**不写进本地状态**（`act.liked === null` 回滚时能恢复"不知道"），界面也不显示选中态。
		 * - 失败：把状态与数字**改回点之前的值**并如实提示；掉线（`noteAuth`）则切软门槛 + 跳登录。
		 */
		async toggleReaction(postId, kind) {
			const isLike = kind === 'like'
			const label = isLike ? '点赞' : '收藏'
			if (!this.loggedIn) {
				// 未登录：**真跳登录页**（不静默失败、不假装成功）
				this.patchAction(postId, { error: '登录后才能' + label })
				uni.navigateTo({ url: '/pages/login/login' })
				return
			}
			const cur = this.actionMap[postId] || {}
			if (cur.busy) return
			const raw = this.rawPost(postId) || {}
			const act = postActionView(raw, cur)
			const beforeOn = isLike ? act.liked : act.favorited
			const beforeCount = isLike ? act.likeCount : act.favoriteCount
			const nextOn = beforeOn !== true
			const nextCount = Math.max(0, beforeCount + (nextOn ? 1 : -1))
			// ① 乐观更新：先按用户的意图改界面
			this.patchAction(
				postId,
				isLike
					? { busy: true, error: '', liked: nextOn, likeCount: nextCount }
					: { busy: true, error: '', favorited: nextOn, favoriteCount: nextCount }
			)
			try {
				if (isLike) {
					if (nextOn) await likePost(postId)
					else await unlikePost(postId)
				} else {
					if (nextOn) await favoritePost(postId)
					else await unfavoritePost(postId)
				}
				this.patchAction(postId, { busy: false })
			} catch (e) {
				// ② 失败回滚：状态与数字都回到点之前（`beforeOn` 可能就是 null = "不知道"）
				const dropped = this.noteAuth(e)
				this.patchAction(
					postId,
					Object.assign(
						{ busy: false },
						isLike ? { liked: beforeOn, likeCount: beforeCount } : { favorited: beforeOn, favoriteCount: beforeCount },
						{ error: dropped ? '' : label + '没成功：' + communityErrorMessage(e) }
					)
				)
				if (dropped) uni.navigateTo({ url: '/pages/login/login' })
			}
		},

		/** 按 postId 找**原始 DTO**（乐观更新的基线必须来自响应，不能来自上一次的本地状态） */
		rawPost(postId) {
			for (let i = 0; i < this.posts.length; i++) {
				const p = this.posts[i]
				if (p && p.postId === postId) return p
			}
			return null
		},

		/** 正文展开状态的不可变更新（键一律在 methods 里创建，computed 只读 —— 避免渲染期写状态） */
		patchContent(postId, patch) {
			const next = Object.assign({}, this.contentMap)
			next[postId] = Object.assign({}, next[postId], patch)
			this.contentMap = next
		},

		/** 点赞 / 收藏本地状态的不可变更新（同 patchContent） */
		patchAction(postId, patch) {
			const next = Object.assign({}, this.actionMap)
			next[postId] = Object.assign({}, next[postId], patch)
			this.actionMap = next
		},

		/**
		 * 图片**真的**加载失败（`@error`）→ 记下这张 url；`cards` 会把这一格标成 `failed`，
		 * 组件**留在原地**写一句「图片加载失败」。
		 *
		 * 🔴 2026-09-21 定稿：**不再把失败的图从帖子里剔掉**。旧做法（剔掉 → 一张不剩就整块不渲染）
		 * 会让"图挂了"和"这条帖子本来没图"长得一模一样 —— 那正是用户说的"静默当没图"，
		 * 而且抽掉格子会让后面的图跳位、看不出这里原本还有一张。
		 */
		onImageFail(url) {
			if (!url) return
			if (this.brokenImages[url]) return
			this.brokenImages = Object.assign({}, this.brokenImages, { [url]: true })
		},

		/**
		 * 点图看大图：`current` 必须是**点的那一张**（`cells[].index` 就是原列表下标）。
		 *
		 * 🔴 给 `previewImage` 的是**原图 url**：缩略格的 `aspectFill` 裁切只发生在卡片里，
		 * 点开看到的是**完整原图**（用户定稿要求）。
		 */
		previewPic(urls, index) {
			const list = Array.isArray(urls) ? urls : []
			if (!list.length) return
			const at = Number(index) > 0 && Number(index) < list.length ? Number(index) : 0
			uni.previewImage({ urls: list, current: list[at] })
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

/* ---- 搜索入口（真有去处：pages/community/search）---- */
.search-entry {
	display: flex;
	align-items: center;
	margin-bottom: $mw-space-3;
	padding: 0 $mw-space-3;
	min-height: $mw-touch-min;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-pill;
}
/* 放大镜（图标 = **base64 data-URI 背景图**：模板里那个 view 就是图标本体，不再有内联 svg；
 * 盒子尺寸由 `.mw-ic--sm`（36rpx = 18px）给，形状/颜色烘在 URI 里，见 styles/icons.scss） */
.search-entry__icon {
	display: flex;
	align-items: center;
	margin-right: $mw-space-2;
	flex: none;
}
.search-entry__text {
	font-size: $mw-font-caption;
	color: $mw-muted;
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
/*
	③ 内容摘要：**折叠的对象是正文摘要，不是标题**（2026-09-21 用户要求）。
	折叠 = **最多 5 行** + 行尾省略号，**不写死高度** —— 短摘要不折时高度仍由内容决定，不留空白。
	⚠️ `line-height` 写成**具体 rpx**（不是 1.65 这种倍数）：页面要拿"5 行高度"跟实测高度比
	   （`EXCERPT_LINE_RPX`），行高是倍数就没法换算出像素阈值。两边由门禁钉在一起。
	没做渐隐遮罩：设计系统「渐变只允许舞台顶部辉光一处」是硬规则，`-webkit-line-clamp`
	自带的行尾省略号已经是"截断可见"的信号，够用。
*/
.post__excerpt {
	display: block;
	margin-top: $mw-space-2;
	font-size: $mw-font-caption;
	line-height: 42rpx;
	color: $mw-ink-2;
}
.post__excerpt--clamp {
	display: -webkit-box;
	-webkit-box-orient: vertical;
	-webkit-line-clamp: 5;
	overflow: hidden;
}
/* ④ 内容展开 / 收起：触控区下限 $mw-touch-min（可点区域不能只是个小字） */
.post__expand {
	display: inline-flex;
	align-items: center;
	min-height: $mw-touch-min;
}
.post__expand text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-brand-ink;
}
/* 摘要展开失败 / 详情里确实没有正文：如实说清（摘要不会被顶掉） */
.post__err {
	display: block;
	font-size: $mw-font-caption;
	color: $mw-danger;
}

/* ---- ⑤ 图片区已搬进共用组件 `components/PostImages.vue` ----
 * 1 张大图（保比例）/ 2 张一行两列 / 3 张一行三列 / 4 张 2×2 / 5-9 张 3×3 / >9 张第 9 格盖 +N。
 * 🔴 这里**故意不再有** `.pics__cell / .pics__one / .pics--*` 那套样式：
 *    它们是三份重复代码中的一份（"9 张排成 2 列"的现场），留在页面上就会有人照着改回来。
 *    **列数由张数决定、格子宽度 `calc((100% − gap)/N)` 相对容器** —— 见组件内的算式与说明。
 */

/* ---- ⑥ 操作区（点赞 / 收藏 / 评论条数 + 进详情入口）----
 * 🔴 三个操作都是**真按钮**（真调接口 / 真跳转），触控目标一律 ≥ `$mw-touch-min`，
 *    等宽铺满一行 —— 不是几个挤在一起的小字（手指点不准）。
 * 只用既有 `$mw-*` 令牌做层级，半透明色统一跟母色（设计系统 §1.6），不新增令牌、不写死颜色。
 */
.acts {
	display: flex;
	align-items: center;
	margin-top: $mw-space-3;
	padding-top: $mw-space-2;
	border-top: 1px solid $mw-line;
}
.act {
	flex: 1;
	display: flex;
	align-items: center;
	justify-content: center;
	min-height: $mw-touch-min;
	padding: 0 $mw-space-1;
}
/* 图标盒：图标是 **base64 data-URI 背景图**（形状与颜色都烘在 URI 里，见 styles/icons.scss）。
 * ⚠️ 下面的 `color` 已**不参与图标渲染**（URI 自带色），保留是为了让"选中态改色"这条口径仍可读；
 * 真正的两态差异在模板上：未赞 `mw-ic--heart-ink2`（空心）↔ 已赞 `mw-ic--heart-brand-fill`（实心）。 */
.act__icon {
	display: flex;
	align-items: center;
	justify-content: center;
	margin-right: 6rpx;
	color: $mw-ink-2;
}
.act__text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-ink-2;
}
/* 选中态：主色 + 实心（实心由模板上的 `mw-ic--*-brand-fill` 给）—— 明显但克制 */
.act--on .act__text {
	color: $mw-brand-ink;
}
.act--on .act__icon {
	color: $mw-brand-ink;
}
.act--on {
	background: rgba(194, 65, 12, 0.08);
	border-radius: $mw-radius-pill;
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
	background: rgba(74, 118, 56, 0.12);
	font-size: 20rpx;
	font-weight: 600;
	color: $mw-success;
}
/* 浏览数是**只读元信息**（不是按钮），排在操作区右端，不参与等宽。
 * 图标 + 文字并排：图标盒子与 `.act__icon` 同尺寸（20px），而行高由三个 `.act` 的
 * `$mw-touch-min`（88rpx = 44px）主导 → **加图标不会把卡片撑高**，也不会折行（复算见门禁 E 段）。
 * 宽度一律相对：`margin-left: auto` + flex，不写死 rpx。 */
.post__meta {
	display: flex;
	align-items: center;
	margin-right: $mw-space-3;
	color: $mw-ink-2;
}
.post__meta text {
	font-size: $mw-font-caption;
	font-variant-numeric: tabular-nums;
}
/* 眼睛图标：**base64 data-URI 背景图**（单态，$mw-muted 烘在 URI 里），与 `.act__icon` 同一批形状 */
.post__meta-icon {
	display: flex;
	align-items: center;
	justify-content: center;
	margin-right: 6rpx;
}
.post__meta--dim {
	margin-left: auto;
	margin-right: 0;
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