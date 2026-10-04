<template>
	<view class="page">
		<!-- 全局 navigationStyle 是 custom，非 tab 页自绘返回栏（照 pages/notification/list.vue） -->
		<view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav__back" @click="goBack">
				<view class="chev chev--left"></view>
			</view>
			<text class="nav__title">搜索帖子</text>
		</view>

		<view class="body">
			<!--
				搜索框：关键词**原样**交给 utils/request.js 的 buildUrl 做 encodeURIComponent。
				页面里**绝不手写百分号编码** —— 本项目踩过"手写 %E7%8C 把中文编错、反过来误判成后端 bug"的坑。
			-->
			<view class="bar">
				<input
					class="bar__input"
					v-model="input"
					type="text"
					confirm-type="search"
					placeholder="搜帖子标题或正文"
					placeholder-class="bar__ph"
					@confirm="submit"
				/>
				<view class="bar__go" :class="{ 'bar__go--busy': searching }" @click="submit">
					<text>{{ searching ? '搜索中' : '搜索' }}</text>
				</view>
			</view>

			<!-- 空关键词 / 超长：**本地就拦下、不发请求**（后端对这两种都是 B000327，实测） -->
			<text v-if="hint" class="hint hint--warn">{{ hint }}</text>

			<!-- 未登录 / 会话过期：软门槛。社区接口**没有网关白名单**，匿名一定被拦成 state:-1 -->
			<view v-if="needLogin" class="state">
				<text class="state__title">{{ sessionExpired ? '登录已过期' : '搜索要登录才能用' }}</text>
				<text class="state__desc">社区接口经过网关时一律要带登录身份（社区不在网关白名单里），匿名请求会被直接拦掉。登录后回到这一页会自动重搜。</text>
				<view class="state__btn" @click="goLogin"><text>去登录</text></view>
			</view>

			<template v-else>
				<!-- 版块筛选：chips 来自既有 GET /community/section（与社区页是同一个接口、同一套 id） -->
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

				<!-- 还没搜过：给真实说明，不放骨架（骨架只表示"请求在途"） -->
				<text v-if="!searched" class="hint">{{ introText }}</text>

				<!-- 四态之一：加载骨架（不是"加载中…"纯文字） -->
				<view v-else-if="loading && !cards.length" class="skeleton">
					<view v-for="n in 3" :key="n" class="skeleton__card"></view>
				</view>

				<!-- 四态之二：错误 —— 说清原因 + 可重试，绝不静默 -->
				<view v-else-if="listError && !cards.length" class="state">
					<text class="state__title">搜索没成功</text>
					<text class="state__desc">{{ listError }}</text>
					<view class="state__btn" @click="retry"><text>重试</text></view>
				</view>

				<!-- 四态之三：空 ——「**没有搜到**」，与底部的「没有更多」是两句不同的话 -->
				<view v-else-if="!cards.length" class="state">
					<text class="state__title">{{ emptyText }}</text>
					<text class="state__desc">{{ emptyDesc }}</text>
					<view class="state__ghost" @click="clearKeyword"><text>换个关键词</text></view>
				</view>

				<template v-else>
					<!-- 已有结果时续拉失败：行内报错 + 重试，不把读着的结果换成错误页 -->
					<view v-if="listError" class="inline-err">
						<text class="inline-err__text">{{ listError }}</text>
						<text class="inline-err__act" @click="retry">重试</text>
					</view>

					<text class="result">{{ resultText }}</text>

					<!--
						帖子卡：与 pages/community/community.vue **同一套 class 与同一套字段**
						（列表 DTO 没有正文 → 卡片只有标题 + 摘要 + 图片 + 计数）。
						展示口径复用 `postCard()` / `cardView()`。
						⚠️ 2026-09-21 起**图片区已抽成共用组件** `components/PostImages.vue`
						（此前三处各写一份、各自按假定卡片宽硬算 rpx 边长 → 真机上"9 张排成 2 列"）。
						社区那份门禁 `check-community-phone.mjs` 同步加了 PostImages 桩，所以这里可以放心 import。
					-->
					<view v-for="p in cards" :key="p.postId" class="post" @click="goDetail(p.postId)">
						<view class="post__head">
							<view class="avatar"><text>{{ p.initial }}</text></view>
							<view class="post__who">
								<text class="post__author" @click.stop="goUser(p.uid)">{{ p.author }}</text>
								<text class="post__time">{{ p.timeText }}</text>
							</view>
							<!-- ⚠️ **2026-09-22 口径变更（用户：「不要在帖子上方显示含几个视频，web 和手机都不要」）**：
							     原来这里还有一枚 `{{ p.videoText }}`（"含 N 个视频"）**已删**；置顶标签保留；
							     `utils/community.js` 的 `videoText` 字段保留（数据层不动），这里只是不再渲染。 -->
							<text v-if="p.isTop" class="badge badge--top">置顶</text>
						</view>

						<!--
							② 标题：**完整显示，不折**（2026-09-21 用户要求，与信息流同一套）。
							真标题只有 14~19 字，折它只会让人看不全；折叠的对象改成下一条**正文摘要**。
						-->
						<text class="post__title">{{ p.title }}</text>

						<!--
							③ 内容摘要：字段由后端 2026-09-21 定稿（`contentExcerpt` + `contentTruncated`）。
							⚠️ 没有这个字段就整块不渲染（不是空框、更不许拿标题冒充摘要）。
						-->
						<text
							v-if="p.hasExcerpt"
							:id="'exc-' + p.postId"
							class="post__excerpt"
							:class="{ 'post__excerpt--clamp': p.excerptClamped }"
						>{{ p.excerptText }}</text>

						<!--
							④ 内容展开：懒加载详情拿全文，🔴 **必须带 `countView=false`**（否则点一下展开就涨一次浏览量）；
							失败 → 保留摘要 + 如实说清原因，不假装成功。
						-->
						<view v-if="p.hasExcerpt" class="post__expand" @click.stop="toggleContent(p.postId)">
							<text>{{ p.excerptLoading ? '正文加载中' : p.excerptToggleText }}</text>
						</view>
						<text v-if="p.excerptError" class="post__err">{{ p.excerptError }}</text>

						<!--
							⑤ 媒体区（图片 + **视频**）：与信息流 / 详情**共用 `components/PostImages.vue`**（同一份列数口径）。
							**一个媒体都没有 → 整块不渲染**（不是 display:none、也不是 height:0）。
							1 个 = 大图（保留原始长宽比）/ 16:9 视频框；2 个一行两列；3 个一行三列；4 个 2×2；5-9 个 3×3；
							列数由**图片 + 视频的合计个数**决定、格子宽度相对容器（`calc((100% − gap)/N)`），**不写死 rpx**。
							⚠️ **2026-09-22**：判据是 `p.hasMedia`（不是 `p.hasImages`）—— 否则"0 图 + N 视频"的帖子看不见。
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

						<view v-if="p.hasTopics" class="topics">
							<text v-for="(t, ti) in p.topics" :key="ti" class="topic">#{{ t }}</text>
						</view>

						<!--
							⑥ 操作区：点赞 / 收藏 / 评论条数 + 进详情入口 —— 与信息流同一套（都是真操作）。
							🔴 卡片上**不渲染任何评论内容**：评论的看与发都在详情页，这里只显示条数。
							🔴 图标是**base64 data-URI 背景图**（与 `components/FloatingTabBar.vue`、
							`styles/icons.scss` 同一批形状）：不引 npm 图标包、不用 emoji、不依赖已废弃的 static PNG。
							⚠️ 改前这里是内联 `<svg>` —— 2026-09-22 实测：App 端视图层用 `document.createElement(tag)`
							造节点，内联 svg 落在 **HTML 命名空间**（HTMLUnknownElement）→ 编译得进去也永远画不出来。
							**图标 + 数字**一起给，不许只留图标让人猜；选中态 = 两套 data-URI（色 + 填充双差异）。
						-->
						<view class="acts">
							<view class="act" :class="{ 'act--on': p.likedOn }" @click.stop="toggleLike(p.postId)">
								<!-- 未赞 = 空心 $mw-ink-2 → 已赞 = 实心 $mw-brand-ink -->
								<view class="act__icon mw-ic"
									:class="p.likedOn ? 'mw-ic--heart-brand-fill' : 'mw-ic--heart-ink2'"></view>
								<text class="act__text">{{ p.likeText }}</text>
							</view>
							<view class="act" :class="{ 'act--on': p.favoritedOn }" @click.stop="toggleFavorite(p.postId)">
								<!-- 收藏同理：未藏空心 → 已藏实心 -->
								<view class="act__icon mw-ic"
									:class="p.favoritedOn ? 'mw-ic--mark-brand-fill' : 'mw-ic--mark-ink2'"></view>
								<text class="act__text">{{ p.favoriteText }}</text>
							</view>
							<view class="act" @click.stop="openComments(p.postId)">
								<view class="act__icon mw-ic mw-ic--chat-ink2"></view>
								<text class="act__text">{{ p.commentActionText }}</text>
							</view>
							<!--
							浏览量：**只读元信息**（不是按钮，没有 @click），但一样要有配套图标 ——
							与信息流、详情页三处口径一致。图标与数字**并存**，
							与三个操作同一族（24×24 viewBox / stroke 1.7 / 圆头圆角），画法统一走 data-URI 背景图。
						-->
						<view class="post__meta post__meta--dim">
							<view class="post__meta-icon mw-ic mw-ic--eye-muted"></view>
							<text class="post__meta-text">{{ p.viewText }}</text>
						</view>
						</view>
						<text v-if="p.actionError" class="post__err">{{ p.actionError }}</text>
					</view>

					<!-- 触底会自动加载；同时给可点入口，失败不静默 -->
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
 * 社区搜索（手机端 / Options API）
 *
 * 把两个**后端真实存在、却从未被验收覆盖**的接口做成一页能用的界面：
 * `GET /community/post/search`（游标分页、标题或正文包含关键词）。
 * 契约唯一来源是**真实返回**：`tools/community-search-fixtures/`（真实账号 `MapleLeaf1` 24 个只读 GET 固化），
 * 离线断言在 `tools/check-community-search-phone.mjs`。
 *
 * 六条实测事实（决定这一页为什么长这样）：
 *
 * 1. 🔴 **空关键词后端直接 `B000327`**（`keyword=''` / 只给空格 / 不传，实测全都是这个码）
 *    → 这一页**空关键词一个请求都不发**，页内给真实提示（说清"这次不发请求"以及为什么）。
 *    超过 50 字（`SEARCH_KEYWORD_MAX`）同样 `B000327` → 也在本地拦下，**不静默截断**。
 * 2. **游标分页**：`lastId` **原样**回传上一页的 `nextCursor`；第一页**不传** `lastId`；
 *    "还有没有下一页"**只认响应里的 `hasMore`**（`size` 是归一化后的请求值，实测 `size=0` → 回显 10）。
 *    实测三页：`[182,181]` → `[180,179]` → `[178]`（`hasMore=false`、`nextCursor=null`），再往下取是空页。
 * 3. 🔴 **"说还有更多却没给游标"要立刻收尾**（`hasMore=true && nextCursor==null` → 停住），否则就是死循环。
 * 4. **中文由请求层 `encodeURIComponent`**（`utils/request.js` 的 `buildUrl`）：页面把关键词**原样**放进 query，
 *    **绝不手写百分号编码**（本项目踩过"手写 %E7%8C 编错中文、反过来误判成后端 bug"的坑）。
 * 5. **展示口径复用 `utils/community.js` 的 `postCard()`**：搜索结果与信息流是同一个 `PostRespDTO`
 *    （2026-09-21 定稿后字段从 16 变 18：多了 `images` / `imageCount`，两端共用同一口径）→ 卡片字段、作者兜底、
 *    时间、话题、图片网格都走同一份纯函数，页面不自己拼。
 *    卡片跳转：整卡进详情 `pages/community/detail?postId=`，作者名**必须 `@click.stop`** 才不会一次跳两页。
 * 6. **未登录走既有页内软门槛** `needLogin` / `noteAuth`（**不新造登录弹窗**）：未登录**零请求**；
 *    网关 `state:-1` → `unauthorized` → 同样切软门槛，不摆一屏"请求失败"；只在点"去登录"那一下才 `clearSession()`。
 *
 * 四态齐全：骨架 / 空（**没有搜到**）/ 错（说清原因 + 重试）/ 未登录（去登录）；
 * 空态与"没有更多"是**两句不同的话**（跑到底时显示 `endText`）。
 * 模板只访问实例属性（卡片在 computed 里摊平，模板不出现 store / utils 对象，也不 `{{ 函数() }}`）。
 */
import { auth } from '@/store/auth.js'
import { ApiError } from '@/utils/request.js'
import PostImages from '@/components/PostImages.vue'
import {
	SEARCH_CURSOR_SIZE,
	SEARCH_EMPTY_TEXT,
	SEARCH_INTRO_TEXT,
	SEARCH_KEYWORD_MAX,
	favoritePost,
	getPost,
	likePost,
	listSections,
	searchEndText,
	searchPosts,
	unfavoritePost,
	unlikePost
} from '@/api/community.js'
import { cardView, communityErrorMessage, excerptOverflow, postActionView } from '@/utils/community.js'

export default {
	components: { PostImages },
	data() {
		return {
			statusBarHeight: 0,
			/** 输入框里的文字（**不是**已经搜过的那个词，见 `searchedKeyword`） */
			input: '',
			/** 真正发出去搜的那个关键词：结果/空态/结果说明都必须对得上它，不能跟着输入框变 */
			searchedKeyword: '',
			/** 是否已经发起过至少一次搜索（区分"还没搜"与"搜完是空的"） */
			searched: false,
			/** PostRespDTO[]（原始 DTO，展示字段在 computed 里摊平） */
			raw: [],
			/** 下一页的 `lastId`（**原样**吃响应里的 nextCursor，不自己加减） */
			cursor: null,
			/** 只认响应里的 hasMore（`size` / `records.length` 都不是判据） */
			hasMore: false,
			loading: false,
			loadingMore: false,
			/** 搜索本身失败（结果为 0 条时显示大卡片；已有结果时显示行内报错） */
			listError: '',
			/** 续拉失败（行内报错 + 重试） */
			moreError: '',
			/** 输入侧的提示（空关键词 / 超长）；**与请求错误分开**，它不是"请求失败" */
			hint: '',
			/** 版块筛选：0 = 全部（不传 sectionId） */
			sectionId: 0,
			sections: [],
			sectionsError: '',
			sectionsBusy: false,
			/** 会话确实过期了（本地还有 token 但后端不认） */
			unauthorized: false,
			/** 这一屏数据属于哪个账号（换号后结果里的 liked/favorited 是上一位身份的，必须复位） */
			sessionOwner: '',
			/** 图片运行时状态：`{url:true}` = 这张**真的**加载失败（格子照留、如实提示，**不再剔掉**） */
			brokenImages: {},
			/**
			 * `postId -> { open, loading, text, error }`：**正文展开**的懒加载状态
			 * （点「内容展开」才读详情，且**必须带 `countView=false`**，不写浏览量）。
			 */
			contentMap: {},
			/**
			 * `postId -> { liked, likeCount, favorited, favoriteCount, busy, error }`：
			 * 点赞 / 收藏的本地状态（**只在用户真点过之后**才写；DTO 没给 `liked` 就不显示已点赞）。
			 */
			actionMap: {},
			/**
			 * `postId -> true`：**实测**"摘要渲染超过 5 行"（`uni.createSelectorQuery` 量出来的真实高度，
			 * 阈值 = `5 × EXCERPT_LINE_RPX` 换算成 px）。量不了就**不写**，如实退回"只看 `contentTruncated`"。
			 */
			excerptOverflowMap: {}
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
		searching() {
			return this.loading || this.loadingMore
		},
		/** 版块 chips：全部 + 接口返回的真实版块（id 是 sectionId） */
		chips() {
			const list = [{ id: 0, name: '全部', on: !this.sectionId }]
			for (let i = 0; i < this.sections.length; i++) {
				const s = this.sections[i] || {}
				const id = s.sectionId
				const name = s.name ? String(s.name) : (s.code ? String(s.code) : '版块 ' + id)
				list.push({ id: id, name: name, on: id === this.sectionId })
			}
			return list
		},
		/** 当前版块名（结果说明 / 空态要用；没有就空串） */
		sectionLabel() {
			if (!this.sectionId) return ''
			for (let i = 0; i < this.sections.length; i++) {
				const s = this.sections[i] || {}
				if (s.sectionId === this.sectionId) return s.name ? String(s.name) : String(s.sectionId)
			}
			return String(this.sectionId)
		},
		/** 卡片：与社区信息流同一个纯函数 + 同一套图片/标题运行时状态（搜到的就是 PostRespDTO，字段一致） */
		cards() {
			const broken = this.brokenImages
			const content = this.contentMap
			const action = this.actionMap
			const over = this.excerptOverflowMap
			return this.raw.map(function (p) {
				const c = content[p.postId]
				return cardView(p, {
					broken: broken,
					// 实测结论并进"正文展开"的运行时状态（`cardExcerptView` 只读 `overflow === true`）
					content: Object.assign({}, c, { overflow: over[p.postId] === true }),
					action: action[p.postId]
				})
			})
		},
		introText() {
			return SEARCH_INTRO_TEXT
		},
		/** 空态标题：**没有搜到**（与 endText 的"没有更多"分开） */
		emptyText() {
			return SEARCH_EMPTY_TEXT
		},
		/** 空态说明：对得上**真正搜过的那个词**与当前筛选，不说空话 */
		emptyDesc() {
			if (!this.searchedKeyword) return ''
			const where = this.sectionId ? '（只搜了「' + this.sectionLabel + '」版块）' : ''
			return '后端这次返回 0 条：没有帖子的标题或正文包含「' + this.searchedKeyword + '」' + where +
				'。换一个关键词，或者把版块切回「全部」再试。'
		},
		/** 结果说明：写清搜的词、版块与**已加载**条数（不假装是总数的 total） */
		resultText() {
			const where = this.sectionId ? '「' + this.sectionLabel + '」版块内' : '全部版块'
			return '「' + this.searchedKeyword + '」在' + where + '命中 ' + this.cards.length + ' 条（按最新在前，已加载）'
		},
		/** 到底文案：**没有更多**（与空态分开） */
		endText() {
			return searchEndText(this.raw.length)
		}
	},
	onLoad() {
		try {
			const info = uni.getSystemInfoSync()
			this.statusBarHeight = info.statusBarHeight || 0
		} catch (e) {
			this.statusBarHeight = 0
		}
		this.sessionOwner = auth.state.username
		// 未登录**一个请求都不发**（经网关必被拦成 state:-1），页面直接给软门槛
		if (!this.loggedIn) return
		this.loadSections()
	},
	onShow() {
		// 换账号（退出登录 → 登录另一个号）：结果里的 liked/favorited 是按上一位身份的 token 算的 → 整页复位
		if (auth.state.username !== this.sessionOwner) {
			this.sessionOwner = auth.state.username
			this.resetForAccount()
		}
		if (!this.loggedIn) return
		if (!this.sections.length && !this.sectionsBusy && !this.sectionsError) this.loadSections()
		// 从登录页返回：这时才有身份，把刚才那次搜索补上（同账号再 onShow 不会重复请求）
		if (this.searchedKeyword && !this.raw.length && !this.loading && !this.listError) this.doSearch(true)
	},
	onPullDownRefresh() {
		this.refresh()
	},
	onReachBottom() {
		this.loadMore()
	},
	/**
	 * 每次渲染完量一次"摘要有没有真的超过 5 行"（**真溢出判断**，不是拿字符数猜）。
	 * 只量**还不知道结论**的卡片 → 量完写回状态会再渲染一次，但那些 id 已被过滤 → 不会死循环。
	 * ⚠️ 平台不支持 `createSelectorQuery` 就直接返回：如实退回"只看服务端 `contentTruncated`"。
	 */
	updated() {
		this.measureExcerpts()
	},
	methods: {
		/** 实测摘要高度 → 是否超过 5 行（量不到就不写结论，不假装量过） */
		measureExcerpts() {
			if (typeof uni === 'undefined' || typeof uni.createSelectorQuery !== 'function') return
			if (typeof uni.getSystemInfoSync !== 'function') return
			const known = this.excerptOverflowMap
			const ids = []
			for (let i = 0; i < this.raw.length; i++) {
				const p = this.raw[i]
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
						if (!r || !(Number(r.height) > 0)) continue
						patch[ids[i]] = excerptOverflow(r.height, w)
					}
					if (Object.keys(patch).length) {
						this.excerptOverflowMap = Object.assign({}, this.excerptOverflowMap, patch)
					}
				})
			} catch (e) {
				// 测量失败不影响搜索结果本身（只是少一个「展开」入口的判定依据）
			}
		},
		/**
		 * 点「搜索」（或键盘回车）：**先做本地校验，再决定发不发请求**
		 * 🔴 空关键词 / 超长关键词都不发请求 —— 后端对这两种都回 `B000327`（实测），
		 * 发了只是白跑一趟，还要用户等一个必然失败的网络往返。
		 */
		submit() {
			const kw = String(this.input || '').trim()
			if (!kw) {
				this.hint = '先输入关键词再搜：后端对空关键词直接返回 B000327「搜索关键词为空或过长」，所以这一次不发请求'
				return
			}
			if (kw.length > SEARCH_KEYWORD_MAX) {
				this.hint = '关键词最多 ' + SEARCH_KEYWORD_MAX + ' 字，现在是 ' + kw.length +
					' 字。后端对超长关键词同样返回 B000327，所以这一次不发请求'
				return
			}
			this.hint = ''
			// 记下**真正搜的词**：输入框随后被改也不影响结果说明与空态文案对得上哪一次搜索
			this.searchedKeyword = kw
			this.searched = true
			this.doSearch(true)
		},

		/** 选中版块：已经搜过就用**同一个关键词**重搜（游标从第一页重来）；没搜过只记筛选，不发请求 */
		selectSection(id) {
			if (id === this.sectionId) return
			this.sectionId = id
			if (!this.searchedKeyword) return
			this.doSearch(true)
		},

		/** 换个关键词：把结果与游标一起清掉，回到"还没搜"的状态（不发请求） */
		clearKeyword() {
			this.input = ''
			this.searchedKeyword = ''
			this.searched = false
			this.raw = []
			this.cursor = null
			this.hasMore = false
			this.loading = false
			this.loadingMore = false
			this.listError = ''
			this.moreError = ''
			this.hint = ''
		},

		/**
		 * 搜索的唯一分页实现（游标）
		 * @param {boolean} reset true = 第一页（首屏 / 重搜 / 下拉刷新），false = 用 `nextCursor` 续拉
		 */
		async doSearch(reset) {
			if (!this.loggedIn) return
			const kw = this.searchedKeyword
			// 没有关键词绝不发请求（后端必回 B000327）；没有游标也别硬拉（会把第一页再拉一遍 → 重复卡片）
			if (!kw) return
			if (!reset && (this.cursor === null || this.cursor === undefined)) {
				this.hasMore = false
				return
			}
			// 换号守卫：请求发出去之后账号变了，这份响应属于上一个身份，必须丢弃
			const owner = auth.state.username
			if (reset) {
				this.loading = true
				this.listError = ''
				this.moreError = ''
				this.raw = []
				this.cursor = null
				this.hasMore = false
			} else {
				if (this.loadingMore || this.loading) return
				this.loadingMore = true
				this.moreError = ''
			}
			try {
				const page = await searchPosts({
					keyword: kw,
					sectionId: this.sectionId || undefined,
					// `lastId` 一律**原样**回传游标；第一页不传（undefined 会被 buildUrl 省略）
					lastId: reset ? undefined : this.cursor,
					size: SEARCH_CURSOR_SIZE
				})
				const records = (page && page.records) || []
				if (auth.state.username !== owner) return
				this.raw = reset ? records : this.raw.concat(records)
				// ⚠️ 只认响应里的 hasMore
				this.hasMore = !!(page && page.hasMore)
				this.cursor = page ? page.nextCursor : null
				// "说还有更多、却没给游标"是死循环入口 → 立刻收尾停住，不再发请求
				if (this.hasMore && (this.cursor === null || this.cursor === undefined)) this.hasMore = false
			} catch (e) {
				if (auth.state.username !== owner) return
				if (this.noteAuth(e)) return
				const text = communityErrorMessage(e)
				if (reset) this.listError = text
				else this.moreError = text
			} finally {
				if (auth.state.username === owner) {
					this.loading = false
					this.loadingMore = false
				}
			}
		},

		loadMore() {
			if (this.needLogin) return
			if (!this.hasMore || this.loadingMore || this.loading) return
			this.doSearch(false)
		},

		/** 错误态/空态里的"重试"：会话过期标记也一起复位，否则门槛会一直挂在那 */
		retry() {
			this.unauthorized = false
			if (!this.searchedKeyword) return
			this.doSearch(true)
		},

		async refresh() {
			if (!this.loggedIn) {
				uni.stopPullDownRefresh()
				return
			}
			this.unauthorized = false
			this.searched = this.searched || !!this.searchedKeyword
			await Promise.all([this.loadSections(), this.searchedKeyword ? this.doSearch(true) : null])
			uni.stopPullDownRefresh()
		},

		async loadSections() {
			if (!this.loggedIn) return
			this.sectionsError = ''
			this.sectionsBusy = true
			try {
				const rows = await listSections()
				this.sections = Array.isArray(rows) ? rows : []
			} catch (e) {
				if (this.noteAuth(e)) return
				this.sectionsError = '版块没读到：' + communityErrorMessage(e)
			} finally {
				this.sectionsBusy = false
			}
		},

		goDetail(postId) {
			if (!postId) return
			uni.navigateTo({ url: '/pages/community/detail?postId=' + postId })
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
		 * ③④ 「内容展开 / 收起」：懒加载**详情**拿全文，🔴 **必须带 `countView=false`**（不写浏览量）。
		 * 收起只翻本地状态（不发请求）；拿到过全文就直接展开（不重复请求）；
		 * 失败 / 详情里没有正文 → **摘要留着**，如实说清原因，**不假装展开成功**。
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
		 * （评论的看与发都在详情里；卡片外面只显示有多少条评论）。
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
		 * 点赞 / 收藏：**乐观更新 + 失败回滚 + 未登录跳登录**（与信息流卡片同一套实现）。
		 * 接口两个方向都存在（`CommunityInteractionController.java:27/36/45/54`）→ 不是假交互。
		 */
		async toggleReaction(postId, kind) {
			const isLike = kind === 'like'
			const label = isLike ? '点赞' : '收藏'
			if (!this.loggedIn) {
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

		/** 按 postId 找**原始 DTO**（乐观更新的基线来自响应，不来自上一次的本地状态） */
		rawPost(postId) {
			for (let i = 0; i < this.raw.length; i++) {
				const p = this.raw[i]
				if (p && p.postId === postId) return p
			}
			return null
		},

		/** 正文展开状态的不可变更新 */
		patchContent(postId, patch) {
			const next = Object.assign({}, this.contentMap)
			next[postId] = Object.assign({}, next[postId], patch)
			this.contentMap = next
		},

		/** 点赞 / 收藏本地状态的不可变更新 */
		patchAction(postId, patch) {
			const next = Object.assign({}, this.actionMap)
			next[postId] = Object.assign({}, next[postId], patch)
			this.actionMap = next
		},

		/**
		 * 图片**真的**加载失败（`@error`）→ 记下这张 url；这一格会被标成 `failed`，组件**留在原地**
		 * 写一句「图片加载失败」（2026-09-21 定稿：**不再剔掉**，剔掉就等于"静默当没图"）。
		 */
		onImageFail(url) {
			if (!url) return
			if (this.brokenImages[url]) return
			this.brokenImages = Object.assign({}, this.brokenImages, { [url]: true })
		},

		/**
		 * 点图看大图：`current` 必须是**点的那一张**（`cells[].index` 就是原列表下标）。
		 * 🔴 给 `previewImage` 的是**原图 url** —— 缩略格的 `aspectFill` 裁切只发生在卡片里。
		 */
		previewPic(urls, index) {
			const list = Array.isArray(urls) ? urls : []
			if (!list.length) return
			const at = Number(index) > 0 && Number(index) < list.length ? Number(index) : 0
			uni.previewImage({ urls: list, current: list[at] })
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

		/** 换账号：结果、游标、筛选、错误全都属于上一位身份 → 一起复位（复位后由 onShow 补一次搜索） */
		resetForAccount() {
			this.raw = []
			this.cursor = null
			this.hasMore = false
			this.loading = false
			this.loadingMore = false
			this.listError = ''
			this.moreError = ''
			this.sectionsError = ''
			this.sections = []
			this.sectionsBusy = false
			// 上一位的"会话过期"标记对新身份没有意义（刚登录成功），留着会把软门槛一直挂在页面上
			this.unauthorized = false
			// 上一位展开过的正文、点过的赞与收藏同样是按旧身份算出来的 → 一起清掉
			this.contentMap = {}
			this.actionMap = {}
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
			this.sectionsBusy = false
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

/* ---- 搜索框 ---- */
.bar {
	display: flex;
	align-items: center;
	margin-bottom: $mw-space-3;
}
.bar__input {
	flex: 1;
	min-width: 0;
	height: $mw-touch-min;
	padding: 0 $mw-space-3;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-pill;
	font-size: $mw-font-body;
	color: $mw-ink;
}
.bar__ph {
	font-size: $mw-font-caption;
	color: $mw-muted;
}
.bar__go {
	margin-left: $mw-space-2;
	padding: 0 $mw-space-4;
	min-width: $mw-touch-min;
	min-height: $mw-touch-min;
	border-radius: $mw-radius-pill;
	background: $mw-brand-ink;
	display: flex;
	align-items: center;
	justify-content: center;
}
.bar__go text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: #ffffff;
}
.bar__go--busy {
	opacity: 0.6;
}

/* ---- 版块 chips（与社区页同一套观感） ---- */
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

/* ---- 说明 / 提示 ---- */
.hint {
	display: block;
	margin-bottom: $mw-space-3;
	padding: $mw-space-3;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-input;
	font-size: $mw-font-caption;
	line-height: 1.7;
	color: $mw-ink-2;
}
.hint--warn {
	border-color: rgba(255, 176, 32, 0.4);
	color: $mw-accent-ink;
}
.result {
	display: block;
	margin-bottom: $mw-space-3;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}

/* ---- 帖子卡（与 pages/community/community.vue 同名同款；列表 DTO 没有正文） ---- */
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
	③ 内容摘要：**折叠的对象是正文摘要，不是标题**（2026-09-21 用户要求，与 community.vue 逐条一致）。
	折叠 = **最多 5 行** + 行尾省略号，**不写死高度** → 短摘要不留空白；没做渐隐遮罩：
	设计系统「渐变只允许舞台顶部辉光一处」是硬规则，行尾省略号已足够表达"被截断"。
	⚠️ `line-height` 写成具体 rpx：页面要拿"5 行高度"跟实测高度比（`EXCERPT_LINE_RPX`），两边由门禁钉住。
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
/* ④ 内容展开 / 收起：触控区下限 $mw-touch-min */
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
.post__err {
	display: block;
	font-size: $mw-font-caption;
	color: $mw-danger;
}

/* ---- ⑤ 图片区已搬进共用组件 `components/PostImages.vue` ----
 * 1 张大图（保比例）/ 2 张一行两列 / 3 张一行三列 / 4 张 2×2 / 5-9 张 3×3 / >9 张第 9 格盖 +N。
 * 🔴 这里**故意不再有** `.pics__cell / .pics__one / .pics--*` 那套样式：
 *    它们正是"9 张排成两列"那个真机 bug 的三份重复实现之一，留着就会有人照着改回来。
 *    **列数由张数决定、格子宽度 `calc((100% − gap)/N)` 相对容器** —— 见组件内的算式与说明。
 */
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
/* ---- ⑥ 操作区（点赞 / 收藏 / 评论条数 + 进详情入口）：与信息流同一套 ----
 * 三个都是**真按钮**、触控区 ≥ `$mw-touch-min`、等宽铺满一行（不是挤在一起的小字）。
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
 * ⚠️ 下面的 `color` 已不参与图标渲染，保留只为"选中态改色"这条口径仍可读；
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
/*
	已点赞 / 已收藏的**选中态**：颜色 + 图标填充**双重差异**（明显但克制）。
	未选中 = 空心描边 + 次要文字色；选中 = 主色 + 实心（实心由模板上的 `mw-ic--*-brand-fill` 给）。
*/
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
/* 浏览数是**只读元信息**（不是按钮），排在操作区右端。
 * 图标 + 文字并排；行高由三个 `.act` 的 `$mw-touch-min`（88rpx = 44px）主导 → 不会撑高卡片。
 * 宽度一律相对：`margin-left: auto` + flex，不写死 rpx（复算见门禁）。 */
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