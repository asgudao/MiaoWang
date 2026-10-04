<template>
	<view class="page">
		<view class="head">
			<text class="h1">知识库</text>
			<text class="sub">{{ headSub }}</text>
		</view>

		<!-- AI 问诊入口（2026-09-22 从整页最底部挪到这里）：
		     原先它挂在列表与触底 footer 之下，而 onReachBottom 每追加一页就把列表下方的内容整体下推，
		     入口正好落在"只会变高的区域"里 → 越靠近越往下跑。挪到顶部即彻底离开这个区域。
		     整卡可点（@click 在外层），内层按钮只是视觉锚点：不能给它再挂一个 handler，否则事件冒泡会跳两次。 -->
		<view class="qa" @click="goAi">
			<view class="qa__text">
				<text class="qa__title">AI 问诊</text>
				<text class="qa__desc">大模型检索问答，回答标出引用来源</text>
			</view>
			<view class="qa__btn"><text>去提问</text></view>
		</view>

		<!-- 搜索：输入即搜（防抖）；回车立刻搜；有词时给一键清空 -->
		<view class="search">
			<input
				class="search__input"
				type="text"
				confirm-type="search"
				placeholder="搜症状、食材、疫苗…"
				placeholder-class="ph"
				v-model="keyword"
				@input="onKeywordInput"
				@confirm="submitSearch"
			/>
			<text v-if="keyword" class="search__clear" @click="clearKeyword">清空</text>
		</view>

		<!-- 物种：知识碎片按物种分库（猫 206 / 狗 203）→ 不选物种时"饮食安全"会把猫狗混在一起 -->
		<view class="segs">
			<view
				v-for="s in speciesSegs"
				:key="s.key"
				class="seg"
				:class="{ 'seg--on': s.on }"
				@click="selectSpecies(s.value)"
			>
				<text>{{ s.label }}</text>
			</view>
		</view>

		<!-- 分类 chips：接口给的 12 条是 4 个 code × 3 个物种的重复行 → 去重后是 4 个分类 + 全部 -->
		<scroll-view class="chips" scroll-x :show-scrollbar="false">
			<view
				v-for="c in chips"
				:key="c.code"
				class="chip"
				:class="{ 'chip--on': c.on }"
				@click="selectCategory(c.code)"
			>
				<text>{{ c.name }}</text>
			</view>
		</scroll-view>
		<view v-if="categoriesError" class="inline-err">
			<text class="inline-err__text">{{ categoriesError }}</text>
			<text class="inline-err__act" @click="loadCategories">重试</text>
		</view>

		<!-- 首屏骨架（不是"加载中…"纯文字） -->
		<view v-if="firstLoading" class="skeleton">
			<view v-for="n in 3" :key="n" class="skeleton__card"></view>
		</view>

		<!-- 错态：说清原因 + 可重试，绝不静默 -->
		<view v-else-if="listError" class="state">
			<text class="state__title">{{ searchMode ? '没搜到结果' : '知识没读到' }}</text>
			<text class="state__desc">{{ listError }}</text>
			<view class="state__btn" @click="retry"><text>重试</text></view>
		</view>

		<!-- 空态：接口通了但确实没数据（换了分类 / 关键词无命中时就是这一态） -->
		<view v-else-if="!cards.length" class="state">
			<text class="state__title">{{ emptyTitle }}</text>
			<text class="state__desc">{{ emptyDesc }}</text>
			<view class="state__btn" @click="retry"><text>{{ searchMode ? '重新搜索' : '重新读取' }}</text></view>
		</view>

		<template v-else>
			<view class="count">
				<text class="count__text">{{ countText }}</text>
			</view>

			<!-- 卡片是**纯文字**的：知识碎片没有封面图字段（记录只有 12 个字段，无 coverUrl） -->
			<view v-for="c in cards" :key="c.id" class="item" @click="goDetail(c.id)">
				<view class="item__top">
					<text class="tag tag--cat">{{ c.categoryName }}</text>
					<text class="tag tag--sp">{{ c.speciesText }}</text>
					<text class="tag" :class="'tag--' + c.contentTypeTone">{{ c.contentTypeLabel }}</text>
				</view>

				<text class="item__title">{{ c.title }}</text>
				<text v-if="c.hasSummary" class="item__summary">{{ c.summary }}</text>

				<view v-if="c.hasTags" class="item__tags">
					<text v-for="(t, ti) in c.tags" :key="ti" class="tag tag--plain">{{ t }}</text>
				</view>

				<view class="item__meta">
					<!-- ⚠️ stage 实测全是 null → 只在真有阶段时才占位（否则每张卡都多一个恒为「通用」的噪音标签） -->
					<text v-if="c.hasStage" class="item__time">{{ c.stageText }}</text>
					<text class="item__time" :class="{ 'item__time--gap': c.hasStage }">{{ c.viewText }}</text>
				</view>
			</view>

			<!-- 触底自动加载；同时给一个可点入口，失败不静默
			     ⚠️ handler 挂在**整行**上（不再是行内那几个字）：四种文案状态下这一行的位置、行高、
			     命中区与 handler 全都不变，所以"恰好在加载中按下手指"也不会把这次点击吃掉；
			     能不能加载由 onFootTap 按当前状态判（有更多 / 上一次失败 = 可点，到底了 = 不可点）。 -->
			<view class="foot" @click="onFootTap">
				<text v-if="loadingMore" class="foot__text">正在加载更多</text>
				<view v-else-if="moreError" class="foot__err">
					<text class="foot__err-text">{{ moreError }}</text>
					<text class="foot__act">重试</text>
				</view>
				<text v-else-if="hasMore" class="foot__act">加载更多</text>
				<text v-else class="foot__text">{{ footText }}</text>
			</view>
		</template>

		<text class="note">{{ note }}</text>

		<FloatingTabBar current="knowledge" />
	</view>
</template>

<script>
/**
 * 知识库列表（手机端 / Options API）
 *
 * 从"4 条硬编码演示数据"改成**真接口**（`api/knowledge.js` 的 5 个只读接口）。
 * 契约是实测出来的，不是从 REST 惯例猜的，几条最容易写错的写在下面：
 *
 * 1) **知识库是全仓库唯一"匿名可读"的业务模块**：网关白名单按读路径逐条列
 *    （`/knowledge/category`、`/fragment`、`/fragment/**`），**不校验 token**。
 *    → 请求层一律 `anonymous: true`（在 `api/knowledge.js` 里），
 *      所以"未登录能读"这句**不依赖登录态是否有效**：实测带垃圾 token 也回 200 + `code:"0"`。
 *    → 本页**没有登录软门槛**：知识库不需要登录，摆一个"去登录"反而把内容挡在门外。
 *
 * 2) ⚠️ `/category` 返回 **12 条 = 4 个 `code` × 3 个 `species`**（同名同码，只有 id/species 不同）。
 *    照原样渲染会出现"饮食安全"连排三次 → **必须按 `code` 去重**（`dedupeCategories`），
 *    去重后是 4 个分类 + 「全部」= 5 个 chips。真正的筛选维度是 `categoryCode` × `species`，
 *    所以上面另有一排**物种分段控件**（全部 / 猫 / 狗），默认「全部物种」→ 第一页 `total = 409`。
 *
 * 3) 筛选参数名是 **`categoryCode`**（`category` / `code` / `categoryId` 实测**一律被忽略**）。
 *
 * 4) ⚠️ **搜索不分页、硬上限 30 条**（源码 `LIMIT 30`，`pageNum`/`pageSize` 传了也没用）
 *    → 搜索态**没有触底加载**，底部如实写明"一次最多 30 条"，不假装还能翻页。
 *    关键词为空**不发搜索请求**（空串等价于"无关键词"，会白拿 30 条最新），走分页接口。
 *
 * 5) ⚠️ 分页推进量用**自己发出去的那个页码**，不用响应回显的 `pageNum`
 *    （越界时后端原样回显，照它推会永远卡在越界页）—— 见 `api/knowledge.js` 的 `pageStateOf`。
 *
 * 6) **AI 问诊入口在列表之上，永远不许再挪回列表下方**（2026-09-22 修订）。
 *    ⚠️ 触底"入口点不动"的根因**不是遮罩**：本工程全端没有任何 `uni.showLoading`
 *    （整个 `MiaoWang-phone/` 实测零命中），页面级 `loading` 只驱动骨架屏与 footer 文案。
 *    真因是**布局生长**：`onReachBottom()` → `loadMore()` → `loadPage()` →
 *    `this.fragments = this.fragments.concat(st.records)`（每页 10 条，`total` 434 时共 44 页）
 *    → 列表变长，而入口当时挂在列表**之下**，于是每靠近一次就被下推一屏；
 *    `pages.json` 的 `onReachBottomDistance: 100` 又保证"先触发加载、再被推走"，手指永远追不上。
 *    修法：① 入口移到顶部（只增不减的区域之外）；② 触底 footer 固定行高（文本互换不再改高度，
 *    顺带让「加载更多 / 重试」拿到 `$mw-touch-min` 的触达高度）。
 *    ⚠️ 因此也**禁止**给它加铺满全屏的 loading 遮罩：那会把整页的可点元素一起吞掉，
 *    用"遮住"换"点不动"不算修好（`check-knowledge-phone.mjs` 的 [N] 段钉住这两条）。
 *
 * 模板只访问实例属性：卡片字段一律在 `computed` 里摊平（`fragmentCard`），
 * 模板里不出现 store/utils 对象、也不调函数（`check-phone.js` 第 4、5 类检查盯着这两条）。
 */
import FloatingTabBar from '@/components/FloatingTabBar.vue'
import { hideNativeTabBar } from '@/utils/tabbar.js'
import { ApiError } from '@/utils/request.js'
import {
	PAGE_SIZE,
	SEARCH_LIMIT,
	SPECIES,
	categoryNameMap,
	dedupeCategories,
	fragmentCard,
	isMissingFragment,
	knowledgeErrorMessage,
	listCategories,
	listFragments,
	pageStateOf,
	searchFragments
} from '@/api/knowledge.js'

/** 输入防抖：打字过程中不要每按一个键就打一次接口 */
const SEARCH_DEBOUNCE_MS = 400

export default {
	components: { FloatingTabBar },
	data() {
		return {
			/** `/category` 原始 12 条（不直接进模板） */
			categories: [],
			categoriesError: '',

			/** 搜索词（v-model 绑原始值；真正发出去的词在 activeKeyword） */
			keyword: '',
			/**
			 * 已经**生效**的搜索词（防抖之后、且已 trim）。
			 * 用"生效词"而不是输入框的值来判断搜索态，是为了让"正在打字"与
			 * "结果属于哪个词"这两件事分开：结果永远对得上 activeKeyword，不会闪。
			 */
			activeKeyword: '',

			/** '' = 全部物种；1 猫 / 2 狗 */
			activeSpecies: '',
			/** '' = 全部分类；否则 diet/behavior/care/health */
			activeCategory: '',

			/** 输入防抖的定时器句柄（放 data 里是为了 onUnload 能清掉，不在模板里用） */
			searchTimer: null,

			/** 原始 fragment DTO 数组 */
			fragments: [],
			/** 下一次请求要发的页码（发出后按"发出去的值"推进） */
			pageNum: 1,
			total: 0,
			pages: 0,

			/** 成功加载过至少一次（区分"还没加载"与"加载完是空的"） */
			loadedOnce: false,
			loading: false,
			loadingMore: false,
			hasMore: false,

			/** 列表级错误（列表为空时显示大卡片） */
			listError: '',
			/** 加载更多的错误（列表已有内容时显示行内） */
			moreError: ''
		}
	},
	computed: {
		/** 搜索态：生效词非空（空格词不算） */
		searchMode() {
			return Boolean(this.activeKeyword)
		},
		headSub() {
			if (this.searchMode) return '搜索一次最多 ' + SEARCH_LIMIT + ' 条，想看更多请按分类翻'
			return '经人工审校，仅供参考，具体请咨询兽医'
		},
		note() {
			if (this.searchMode) return '搜索结果来自知识库标题与结论的匹配，一次最多 ' + SEARCH_LIMIT + ' 条'
			return '已接入真实知识库接口，当前筛选下共 ' + this.total + ' 条可见内容'
		},
		/** 物种分段（'key' 用字符串：data 里 activeSpecies 用 '' 表示全部，避免 undefined 参与比较） */
		speciesSegs() {
			const out = []
			for (let i = 0; i < SPECIES.length; i++) {
				const s = SPECIES[i]
				const v = s.value === undefined ? '' : s.value
				out.push({ key: 'sp-' + (v === '' ? 'all' : v), label: s.label, value: v, on: v === this.activeSpecies })
			}
			return out
		},
		/** 分类 chips：全部 + 去重后的真实分类（**不是**接口原样的 12 条） */
		chips() {
			const list = [{ code: '', name: '全部', on: this.activeCategory === '' }]
			for (let i = 0; i < this.categories.length; i++) {
				const c = this.categories[i]
				list.push({ code: c.code, name: c.name, on: c.code === this.activeCategory })
			}
			return list
		},
		/** code → 中文名（卡片要用；避免在模板里调函数） */
		nameMap() {
			return categoryNameMap(this.categories)
		},
		cards() {
			const map = this.nameMap
			return this.fragments.map(function (f) {
				return fragmentCard(f, map)
			})
		},
		firstLoading() {
			return this.loading && !this.fragments.length
		},
		emptyTitle() {
			if (this.searchMode) return '没有匹配「' + this.activeKeyword + '」的知识'
			if (this.activeCategory || this.activeSpecies !== '') return '这个筛选下还没有内容'
			return '知识库还没有内容'
		},
		emptyDesc() {
			if (this.searchMode) {
				return '换个更短的关键词试试，或者清空搜索按分类翻。空格加词会收窄结果（多个词必须同时命中），加得越多越搜不到。'
			}
			if (this.activeCategory || this.activeSpecies !== '') {
				return '接口这次返回 0 条。换一个分类或物种看看，或者下拉刷新一次。'
			}
			return '接口是通的，后端这条列表这次返回 0 条。等有内容发布出来，这里就会出现。'
		},
		countText() {
			if (this.searchMode) return '搜到 ' + this.fragments.length + ' 条'
			return '共 ' + this.total + ' 条'
		},
		footText() {
			if (this.searchMode) return '搜索一次最多 ' + SEARCH_LIMIT + ' 条，没有更多了'
			if (!this.fragments.length) return '没有更多了'
			return '一共 ' + this.fragments.length + ' 条，没有更多了'
		}
	},
	onLoad() {
		hideNativeTabBar()
		this.loadAll()
	},
	onShow() {
		hideNativeTabBar()
		// 知识库是**公开读**：没有"登录态变了要重拉"这回事（结果与身份无关），
		// 所以常驻 tab 页回到前台**不重新请求**，保住"切 tab 不白闪"的体验。
	},
	onUnload() {
		this.clearSearchTimer()
	},
	onPullDownRefresh() {
		this.refresh()
	},
	onReachBottom() {
		// 搜索态不分页（后端硬上限 30 条），触底不做任何事
		if (this.searchMode) return
		this.loadMore()
	},
	methods: {
		clearSearchTimer() {
			if (this.searchTimer) {
				clearTimeout(this.searchTimer)
				this.searchTimer = null
			}
		},

		/** 首次进入 / 重试：分类与列表一起拉（互不阻塞，各自报错） */
		async loadAll() {
			this.listError = ''
			this.moreError = ''
			await Promise.all([this.loadCategories(), this.reload()])
		},

		async loadCategories() {
			this.categoriesError = ''
			try {
				const rows = await listCategories()
				// 12 条 → 4 个分类（同名同码的 species 行折掉）
				this.categories = dedupeCategories(rows)
			} catch (e) {
				this.categoriesError = '分类没读到：' + knowledgeErrorMessage(e)
			}
		},

		/**
		 * 从第 1 页重来（首屏 / 刷新 / 切分类 / 切物种 / 换关键词）
		 *
		 * 三条复位纪律都在这里，别在别处各写一份：
		 *   - 页码回 1、清空列表与分页进度（否则新筛选会接在旧结果后面）
		 *   - 清掉两种错误（上一位筛选留下的红字对新筛选没有意义）
		 *   - **只在搜索态打搜索接口**：空关键词绝不能发搜索请求
		 *     （实测空串不报错，但会白拿 30 条最新，把分页语义悄悄换掉）
		 */
		async reload() {
			const sentPageNum = 1
			this.pageNum = sentPageNum
			this.fragments = []
			this.total = 0
			this.pages = 0
			this.hasMore = false
			this.loadedOnce = false
			this.listError = ''
			this.moreError = ''
			this.loading = true
			try {
				if (this.searchMode) {
					const rows = await searchFragments({
						keyword: this.activeKeyword,
						species: this.activeSpecies === '' ? undefined : this.activeSpecies,
						categoryCode: this.activeCategory
					})
					this.fragments = Array.isArray(rows) ? rows : []
					// ⚠️ 搜索接口**不返回 total**（裸数组）→ total 只能用拿到的条数，
					// 且**绝不能**据此推断"还有下一页"（hasMore 保持 false）
					this.total = this.fragments.length
					this.pages = 1
					this.hasMore = false
				} else {
					const page = await listFragments({
						species: this.activeSpecies === '' ? undefined : this.activeSpecies,
						categoryCode: this.activeCategory,
						pageNum: sentPageNum,
						pageSize: PAGE_SIZE
					})
					const st = pageStateOf(page, sentPageNum)
					this.fragments = st.records
					this.total = st.total
					this.pages = st.pages
					this.pageNum = st.nextPageNum
					this.hasMore = st.hasMore
				}
				this.loadedOnce = true
			} catch (e) {
				this.listError = this.errorText(e)
			} finally {
				this.loading = false
			}
		},

		loadMore() {
			if (this.searchMode) return
			if (!this.hasMore || this.loadingMore || this.loading) return
			this.loadPage()
		},

		/**
		 * 触底 footer 的整行点击（2026-09-22 新增）
		 *
		 * ⚠️ 为什么 handler 挂在整行而不是行内文字上：触底加载会用新一页把这一行下面的内容顶下去，
		 * 上一版给行内文字各挂一个 `@click`，于是 ①「重试 / 加载更多」的命中区只有 22rpx 的文字高
		 * （远低于 `$mw-touch-min`），②文案一换（`正在加载更多` 会把可点的字换成不可点的字）
		 * 命中区就跟着变，手指落下的那一刻正好切换时这次点击就被吃掉。
		 * 现在命中区恒等于这一行（`min-height: $mw-touch-min`），状态判断挪进函数：
		 * 正在加载 = 不做；有更多或上一次失败 = 再加载一次；到底了 = 不做。
		 * 行为与上一版等价（上一版两个可点字样也都只是调 `loadMore()`），`loadMore()` 里那三道守卫照旧。
		 */
		onFootTap() {
			if (this.loadingMore) return
			if (!this.hasMore && !this.moreError) return
			this.loadMore()
		},

		/** 触底加载下一页（推进量用自己发出去的页码，见 pageStateOf 的注释） */
		async loadPage() {
			const sentPageNum = this.pageNum
			this.loadingMore = true
			this.moreError = ''
			try {
				const page = await listFragments({
					species: this.activeSpecies === '' ? undefined : this.activeSpecies,
					categoryCode: this.activeCategory,
					pageNum: sentPageNum,
					pageSize: PAGE_SIZE
				})
				const st = pageStateOf(page, sentPageNum)
				this.fragments = this.fragments.concat(st.records)
				this.total = st.total
				this.pages = st.pages
				this.pageNum = st.nextPageNum
				this.hasMore = st.hasMore
			} catch (e) {
				this.moreError = this.errorText(e)
			} finally {
				this.loadingMore = false
			}
		},

		async refresh() {
			await Promise.all([this.loadCategories(), this.reload()])
			uni.stopPullDownRefresh()
		},

		retry() {
			if (this.searchMode) {
				this.reload()
				return
			}
			this.loadAll()
		},

		/* ---------- 搜索 ---------- */

		onKeywordInput() {
			this.clearSearchTimer()
			const self = this
			this.searchTimer = setTimeout(function () {
				self.searchTimer = null
				self.applyKeyword()
			}, SEARCH_DEBOUNCE_MS)
		},

		submitSearch() {
			this.clearSearchTimer()
			this.applyKeyword()
		},

		/** 把输入框的值提交为"生效词"；词没变就不打接口（防抖后重复触发要挡住） */
		applyKeyword() {
			const next = String(this.keyword || '').trim()
			if (next === this.activeKeyword) return
			this.activeKeyword = next
			this.reload()
		},

		clearKeyword() {
			this.clearSearchTimer()
			this.keyword = ''
			if (!this.activeKeyword) return
			this.activeKeyword = ''
			this.reload()
		},

		/* ---------- 筛选 ---------- */

		selectSpecies(value) {
			const v = value === undefined || value === null ? '' : value
			if (v === this.activeSpecies) return
			this.activeSpecies = v
			this.reload()
		},

		selectCategory(code) {
			const c = code === undefined || code === null ? '' : code
			if (c === this.activeCategory) return
			this.activeCategory = c
			this.reload()
		},

		goDetail(id) {
			uni.navigateTo({ url: '/pages/knowledge/detail?id=' + id })
		},

		goAi() {
			uni.navigateTo({ url: '/pages/ai/ai' })
		},

		/**
		 * 统一的错误文案
		 * ⚠️ 知识库是匿名可读接口，**不存在"掉线跳登录"**：
		 * `A000001` 在这里只可能是"知识条目不存在"（详情），
		 * 参数类错误说成"页面 bug"比说成"网络问题"更接近事实。
		 */
		errorText(e) {
			if (e instanceof ApiError && isMissingFragment(e)) return '这条知识不存在，或者已经下架了'
			return knowledgeErrorMessage(e)
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
	margin-bottom: $mw-space-4;
	font-size: 22rpx;
	color: $mw-ink-2;
}

.search {
	display: flex;
	align-items: center;
	margin-bottom: $mw-space-3;
}
.search__input {
	flex: 1;
	height: $mw-touch-min;
	padding: 0 $mw-space-4;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-input;
	font-size: $mw-font-body;
	color: $mw-ink;
}
.search__clear {
	margin-left: $mw-space-2;
	padding: 0 $mw-space-2;
	min-height: $mw-touch-min;
	line-height: $mw-touch-min;
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-brand-ink;
}
.ph {
	color: $mw-muted;
}

/* 物种分段：胶囊容器 + 选中态用深落日橙（白字只在 brand-ink 上，见设计系统 §1 硬规则 1） */
.segs {
	display: flex;
	margin-bottom: $mw-space-3;
	padding: 4rpx;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-pill;
}
.seg {
	flex: 1;
	min-height: 72rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	border-radius: $mw-radius-pill;
}
.seg text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-ink-2;
}
.seg--on {
	background: $mw-brand-ink;
}
.seg--on text {
	color: #ffffff;
}

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

.inline-err {
	display: flex;
	align-items: center;
	margin-bottom: $mw-space-3;
	padding: $mw-space-2 $mw-space-3;
	background: rgba(229, 72, 77, 0.08);
	border-radius: $mw-radius-tag;
}
.inline-err__text {
	flex: 1;
	font-size: 22rpx;
	color: $mw-danger;
}
.inline-err__act {
	margin-left: $mw-space-2;
	font-size: 22rpx;
	font-weight: 600;
	color: $mw-brand-ink;
}

/* 骨架：不是"加载中…"纯文字 */
.skeleton__card {
	height: 200rpx;
	margin-bottom: $mw-space-3;
	border-radius: $mw-radius-card;
	background: $mw-line;
	opacity: 0.5;
}

.state {
	margin-top: $mw-space-6;
	padding: $mw-space-6 $mw-space-4;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
	display: flex;
	flex-direction: column;
	align-items: center;
}
.state__title {
	font-size: $mw-font-h2;
	font-weight: 600;
	color: $mw-ink;
}
.state__desc {
	margin-top: $mw-space-2;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
	line-height: 1.6;
	text-align: center;
}
.state__btn {
	margin-top: $mw-space-4;
	padding: 0 $mw-space-6;
	min-height: $mw-touch-min;
	display: flex;
	align-items: center;
	justify-content: center;
	background: $mw-brand-ink;
	border-radius: $mw-radius-pill;
}
.state__btn text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: #ffffff;
}

.count {
	margin-bottom: $mw-space-2;
}
.count__text {
	font-size: 22rpx;
	color: $mw-muted;
	font-variant-numeric: tabular-nums;
}

.item {
	padding: $mw-space-4;
	margin-bottom: $mw-space-3;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
.item__top {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	margin-bottom: $mw-space-2;
}
.item__title {
	display: block;
	font-size: 32rpx;
	font-weight: 600;
	color: $mw-ink;
	line-height: 1.4;
}
.item__summary {
	display: block;
	margin-top: $mw-space-2;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
	line-height: 1.6;
}
.item__tags {
	display: flex;
	flex-wrap: wrap;
	margin-top: $mw-space-2;
}
.item__meta {
	display: flex;
	align-items: center;
	margin-top: $mw-space-3;
}
.item__time {
	font-size: 22rpx;
	color: $mw-muted;
	font-variant-numeric: tabular-nums;
}
.item__time--gap {
	margin-left: $mw-space-3;
}

.tag {
	margin: 0 $mw-space-1 $mw-space-1 0;
	padding: 4rpx 14rpx;
	border-radius: $mw-radius-tag;
	font-size: 20rpx;
	font-weight: 600;
	background: rgba(138, 119, 103, 0.12);
	color: $mw-ink-2;
}
/* 分类：品牌橙的浅底（品牌色只做浅底 + 深色字，不做亮橙底白字） */
.tag--cat {
	background: rgba(194, 65, 12, 0.1);
	color: $mw-brand-ink;
}
/* 物种：中性，不抢分类的视觉权重 */
.tag--sp {
	background: rgba(138, 119, 103, 0.12);
	color: $mw-ink-2;
}
/* 内容类型：按 tone 分工（普通=苔绿、照护=琥珀、警示=危险红，见设计系统 §1 角色分工） */
.tag--default {
	background: rgba(74, 118, 56, 0.12);
	color: $mw-success;
}
.tag--care {
	background: rgba(255, 176, 32, 0.16);
	color: $mw-accent-ink;
}
.tag--warn {
	background: rgba(229, 72, 77, 0.12);
	color: $mw-danger;
}
.tag--danger {
	background: rgba(229, 72, 77, 0.12);
	color: $mw-danger;
}
.tag--plain {
	background: transparent;
	border: 1px solid $mw-line;
	color: $mw-muted;
	font-weight: 500;
}

/* 触底 footer：**固定行高**，一行四个状态（加载中 / 失败可重试 / 可手动加载 / 到底）
   ① 文本互换不再改变这一行的高度 → 列表下方不再"加载时抖一下"，滚动位置不会被带偏；
   ② 这一行本身就是触底区里唯一的可点目标（`@click="onFootTap"` 挂在行上），
      命中区恒为 $mw-touch-min 高、四态不移动也不换 handler，加载中按下手指也不会丢点击。
   ⚠️ 这里**只做行内文字**，不加任何铺满全屏的 loading 遮罩：遮罩会把整页可点元素一起吞掉，
      那正是本页触底"点不动"要避免的事（见文件头第 6 条与 REPORT.md）。 */
.foot {
	display: flex;
	align-items: center;
	justify-content: center;
	min-height: $mw-touch-min;
}
.foot__text {
	font-size: 22rpx;
	color: $mw-muted;
}
.foot__err {
	display: flex;
	align-items: center;
}
.foot__err-text {
	font-size: 22rpx;
	color: $mw-danger;
}
.foot__act {
	display: flex;
	align-items: center;
	margin-left: $mw-space-2;
	padding: 0 $mw-space-3;
	min-height: $mw-touch-min;
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-brand-ink;
}

/* AI 问诊入口（页面最顶端，2026-09-22 位置修订）
   单行卡 = 左文 + 右按钮。**整卡可点**（@click 在外层 view 上），按钮只是视觉锚点，
   所以手指不必精确命中那个胶囊；卡片本身高于 $mw-touch-min（按钮 88rpx + 上下 padding）。
   与搜索框的关系：入口在搜索框**之上**，搜索 / 物种 / 分类仍紧贴列表（它们是列表的筛选器，
   离列表近才顺手）；代价是搜索框整体下移约 150rpx，取舍见 REPORT.md。 */
.qa {
	display: flex;
	align-items: center;
	margin-bottom: $mw-space-4;
	padding: $mw-space-3 $mw-space-4;
	background: rgba(194, 65, 12, 0.05);
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
.qa__text {
	flex: 1;
	min-width: 0;
}
.qa__title {
	display: block;
	font-size: $mw-font-h2;
	font-weight: 600;
	color: $mw-ink;
}
.qa__desc {
	display: block;
	margin-top: $mw-space-1;
	font-size: 22rpx;
	color: $mw-ink-2;
}
.qa__btn {
	margin-left: $mw-space-3;
	padding: 0 $mw-space-4;
	min-height: $mw-touch-min;
	border-radius: $mw-radius-pill;
	background: $mw-brand-ink;
	display: flex;
	align-items: center;
	justify-content: center;
}
.qa__btn text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: #ffffff;
}

.note {
	display: block;
	margin-top: $mw-space-4;
	font-size: 22rpx;
	color: $mw-muted;
}
</style>