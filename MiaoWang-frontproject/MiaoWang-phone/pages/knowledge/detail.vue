<template>
	<view class="page">
		<!-- 全局 navigationStyle 是 custom，非 tab 页自绘返回栏（照 pages/community/detail.vue） -->
		<view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav__back" @click="goBack">
				<view class="chev chev--left"></view>
			</view>
			<text class="nav__title">知识详情</text>
			<text class="nav__refresh" @click="loadDetail">刷新</text>
		</view>

		<view class="body">
			<!-- 没带 id 直接打开：说清怎么进来，不留白屏 -->
			<view v-if="!fragmentId" class="state">
				<text class="state__title">没拿到知识编号</text>
				<text class="state__desc">从知识库列表点一张卡片进来才会带 id，直接打开这个页面读不到内容。</text>
				<view class="state__btn" @click="goBack"><text>返回知识库</text></view>
			</view>

			<!-- 详情骨架 -->
			<view v-else-if="loading && !view" class="skeleton">
				<view class="skeleton__card"></view>
				<view class="skeleton__card skeleton__card--short"></view>
			</view>

			<!-- 失败：原因 + 重试 + 退路。⚠️ "不存在/已下架" 单独给文案，不当网络错误 -->
			<view v-else-if="error && !view" class="state">
				<text class="state__title">{{ missing ? '这条知识不在了' : '知识没读到' }}</text>
				<text class="state__desc">{{ error }}</text>
				<view v-if="!missing" class="state__btn" @click="loadDetail"><text>重试</text></view>
				<view class="state__ghost" @click="goBack"><text>返回知识库</text></view>
			</view>

			<template v-else-if="view">
				<!-- 已有内容时刷新失败：行内报错 + 重试，不把读着的内容换成错误页 -->
				<view v-if="error" class="inline-err">
					<text class="inline-err__text">{{ error }}</text>
					<text class="inline-err__act" @click="loadDetail">重试</text>
				</view>

				<view class="card">
					<view class="badges">
						<text class="tag tag--cat">{{ view.categoryName }}</text>
						<text class="tag tag--sp">{{ view.speciesText }}</text>
						<text class="tag" :class="'tag--' + view.contentTypeTone">{{ view.contentTypeLabel }}</text>
					</view>

					<text class="card__title">{{ view.title }}</text>

					<!-- 知识碎片的"正文"就是这一句结论（实测平均 12 字，最长 43 字）→ 给它主视觉 -->
					<view v-if="view.hasContent" class="conclusion">
						<text class="conclusion__text">{{ view.content }}</text>
					</view>

					<!-- remark 是补充说明（实测 409 条里 343 条有值）→ 与结论分开，不拼成假长文 -->
					<view v-if="view.hasRemark" class="remark">
						<text class="remark__label">补充说明</text>
						<text class="remark__text">{{ view.remark }}</text>
					</view>

					<view v-if="view.hasTags" class="tags">
						<text v-for="(t, ti) in view.tags" :key="ti" class="tag tag--plain">{{ t }}</text>
					</view>

					<view class="meta">
						<text class="meta__one">来源：{{ view.sourceText }}</text>
						<!-- ⚠️ stage 实测全是 null → 只在真有阶段时才显示这一项（否则每篇都挂一个恒为「通用」的噪音标签） -->
						<text v-if="view.hasStage" class="meta__one">适用：{{ view.stageText }}</text>
						<text class="meta__one">{{ view.viewText }}</text>
					</view>
				</view>

				<!-- 专业内容必须附免责声明（设计系统 §7 硬要求） -->
				<view v-if="view.needsDisclaimer" class="warn">
					<text class="warn__text">这条内容涉及健康或安全，仅供参考，具体请咨询兽医。</text>
				</view>

				<view v-if="related.length" class="sec">
					<text class="sec__title">{{ relatedTitle }}</text>
					<text class="sec__note">{{ relatedNote }}</text>
					<view v-for="r in related" :key="r.id" class="rel" @click="goDetail(r.id)">
						<text class="rel__title">{{ r.title }}</text>
						<text v-if="r.hasSummary" class="rel__summary">{{ r.summary }}</text>
					</view>
				</view>

				<text class="note">知识库是公开内容，不用登录就能读。读一次会记一次浏览量。</text>
			</template>
		</view>
	</view>
</template>

<script>
/**
 * 知识详情（手机端 / Options API）
 *
 * 与列表页共用 `api/knowledge.js` 的 5 个只读接口，契约见那个文件的头注释。本页特有的四条：
 *
 * 1) ⚠️ **不存在 / 未发布 → `A000001 知识条目不存在`**（HTTP 200，**不是 404、也不是 B 码**）。
 *    而且"真的不存在"与"存在但 status=1 未发布"**给同一个消息**（后端刻意不区分）→
 *    前端也说一句"这条知识不在了"就够，**不要**编"可能被别人删了"这种无依据的解释。
 * 2) ⚠️ **这个页面不需要登录**：知识库是网关白名单里的公开读，请求层走 `anonymous: true`。
 *    → 这里**没有登录软门槛**（社区那种"先去登录"会把公开内容挡在门外）。
 * 3) ⚠️ **读详情会让 `viewCount` +1，且不是幂等读**（源码：DB 值 + Redis 增量）。
 *    所以"刷新"按钮按一次数字就涨一次，这是后端口径，**不是页面在重复计数**。
 *    文案要如实说明，免得用户以为坏了。
 * 4) **正文是碎片不是文章**：`title`（主张）+ `content`（一句结论，实测平均 12 字）+ `remark`（补充说明）
 *    三段各自独立展示。**绝不**把三段拼成一大段"看起来像文章"的假正文（那会篡改知识边界）。
 *    正因为碎片很短，页面才需要"同分类还有"这一段把版面撑住（见下）。
 *
 * 模板只访问实例属性：字段一律在 `computed` 里摊平，模板里不调函数。
 */
import { ApiError } from '@/utils/request.js'
import {
	PAGE_SIZE,
	categoryNameMap,
	fragmentCard,
	getFragment,
	isMissingFragment,
	knowledgeErrorMessage,
	knowledgeView,
	listCategories,
	listFragments
} from '@/api/knowledge.js'

/** "同分类还有"取几条（详情内容很短，需要这一段把版面撑住） */
const RELATED_SIZE = 5

export default {
	data() {
		return {
			statusBarHeight: 0,
			fragmentId: 0,
			/** 原始 fragment DTO（详情响应） */
			fragment: null,
			loading: false,
			error: '',
			/** 错误是"不存在/已下架"（不是网络问题）→ 不提供"重试"，只提供返回 */
			missing: false,
			/** 同分类的其他条目（原始 DTO） */
			relatedRaw: [],
			/**
			 * code → 分类中文名
			 *
			 * 详情 DTO 里**只有 `categoryCode`（字符串），没有分类名**，分类名在 `/category` 里。
			 * 本页自己拉一次 `/category`（公开读、很便宜）而不是从列表页的 URL 带过来：
			 * 直接打开这个页面时也能显示对的中文名，不会退化成 "behavior" 这种码。
			 */
			nameMap: {}
		}
	},
	computed: {
		view() {
			return this.fragment ? knowledgeView(this.fragment, this.nameMap) : null
		},
		related() {
			const map = this.nameMap
			return this.relatedRaw.map(function (f) {
				return fragmentCard(f, map)
			})
		},
		relatedTitle() {
			if (!this.view) return '同分类还有'
			return '「' + this.view.categoryName + '」还有'
		},
		relatedNote() {
			return '知识库里同一个分类下的其他条目，点一条接着看。'
		}
	},
	onLoad(options) {
		try {
			const info = uni.getSystemInfoSync()
			this.statusBarHeight = info.statusBarHeight || 0
		} catch (e) {
			this.statusBarHeight = 0
		}
		const raw = options && options.id ? Number(options.id) : 0
		this.fragmentId = isNaN(raw) ? 0 : raw
		if (!this.fragmentId) return
		// 分类名表与正文一起拉（互不阻塞）：分类拉失败只影响徽标显示中文，不影响读正文
		this.loadCategories()
		this.loadDetail()
	},
	onShow() {
		// 公开读，没有"从登录页回来要补拉"这回事；也不重复请求（避免白刷一次浏览量）
	},
	methods: {
		/**
		 * 拉分类名表（`/category` 的 12 条 → code→名）。失败静默：
		 * 拿不到就显示 `categoryCode` 原文，比把这个公开页面变成错误页合理。
		 */
		async loadCategories() {
			try {
				const rows = await listCategories()
				this.nameMap = categoryNameMap(rows)
			} catch (e) {
				this.nameMap = {}
			}
		},

		async loadDetail() {
			if (!this.fragmentId) return
			this.loading = true
			this.error = ''
			this.missing = false
			try {
				const d = await getFragment(this.fragmentId)
				if (!d) {
					this.fragment = null
					this.error = '接口这次没有返回这条知识，稍后重试一次'
					return
				}
				this.fragment = d
				// 同分类推荐：不 await 进主流程，失败也不影响正文（它是锦上添花，不是必需品）
				this.loadRelated(d)
			} catch (e) {
				if (this.noteMissing(e)) return
				this.error = knowledgeErrorMessage(e)
			} finally {
				this.loading = false
			}
		},

		/**
		 * "同分类还有"：用列表接口按**同一个 categoryCode + species** 拉一页
		 *
		 * 这里顺带证明了"分类 ↔ 文章"的关联方式：记录里**没有 `categoryId`**，
		 * 只有字符串 `categoryCode`，筛选参数也是 `categoryCode`（实测 `categoryId` 被忽略）。
		 * 取 `RELATED_SIZE + 1` 条再滤掉自己，这样"滤掉自己后还剩几条"不需要第二次请求。
		 * 失败**静默**：正文已经读到了，不该因为推荐位失败弹红字。
		 */
		async loadRelated(d) {
			this.relatedRaw = []
			try {
				const page = await listFragments({
					species: d.species,
					categoryCode: d.categoryCode,
					pageNum: 1,
					pageSize: Math.min(RELATED_SIZE + 1, PAGE_SIZE)
				})
				const rows = Array.isArray(page && page.records) ? page.records : []
				const out = []
				for (let i = 0; i < rows.length; i++) {
					if (rows[i] && rows[i].id === d.id) continue
					out.push(rows[i])
					if (out.length >= RELATED_SIZE) break
				}
				this.relatedRaw = out
			} catch (e) {
				this.relatedRaw = []
			}
		},

		/** @returns {boolean} true = 这条是"不存在/已下架"（调用方别再写网络错误文案） */
		noteMissing(e) {
			this.fragment = null
			if (e instanceof ApiError && isMissingFragment(e)) {
				this.missing = true
				this.error = '接口明确回了「知识条目不存在」。这条可能没发布，或者已经下架了。'
				return true
			}
			return false
		},

		/** 同分类里的另一条：**replace 而不是 navigateTo**，否则连着点会堆一摞页面 */
		goDetail(id) {
			if (!id || id === this.fragmentId) return
			uni.redirectTo({ url: '/pages/knowledge/detail?id=' + id })
		},

		goBack() {
			const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
			if (pages && pages.length > 1) {
				uni.navigateBack()
				return
			}
			// 直接打开这个页面时没有上一页可退 → 回知识库（tab 页用 switchTab）
			uni.switchTab({ url: '/pages/knowledge/knowledge' })
		}
	}
}
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.page {
	min-height: 100vh;
	background: $mw-canvas;
}

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
	min-height: 60vh;
}

.skeleton__card {
	height: 240rpx;
	margin-bottom: $mw-space-3;
	border-radius: $mw-radius-card;
	background: $mw-line;
	opacity: 0.5;
}
.skeleton__card--short {
	height: 120rpx;
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
.state__ghost {
	margin-top: $mw-space-3;
	min-height: $mw-touch-min;
	display: flex;
	align-items: center;
	justify-content: center;
}
.state__ghost text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-ink-2;
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

.card {
	padding: $mw-space-4;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
.badges {
	display: flex;
	flex-wrap: wrap;
	margin-bottom: $mw-space-2;
}
.card__title {
	display: block;
	font-size: $mw-font-h2;
	font-weight: 700;
	line-height: 1.5;
	letter-spacing: -0.02em;
	color: $mw-ink;
}

/* 结论：碎片的正文，给主视觉（左侧一道品牌色竖线做"引文"感，不是彩色卡片） */
.conclusion {
	margin-top: $mw-space-3;
	padding: $mw-space-3;
	background: rgba(194, 65, 12, 0.05);
	border-left: 6rpx solid $mw-brand-ink;
	border-radius: $mw-radius-tag;
}
.conclusion__text {
	font-size: $mw-font-body;
	font-weight: 600;
	line-height: 1.65;
	color: $mw-ink;
}

.remark {
	margin-top: $mw-space-3;
}
.remark__label {
	display: block;
	font-size: 22rpx;
	font-weight: 600;
	color: $mw-muted;
}
.remark__text {
	display: block;
	margin-top: $mw-space-1;
	font-size: $mw-font-caption;
	line-height: 1.7;
	color: $mw-ink-2;
}

.tags {
	display: flex;
	flex-wrap: wrap;
	margin-top: $mw-space-3;
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
.tag--cat {
	background: rgba(194, 65, 12, 0.1);
	color: $mw-brand-ink;
}
.tag--sp {
	background: rgba(138, 119, 103, 0.12);
	color: $mw-ink-2;
}
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

.meta {
	display: flex;
	flex-wrap: wrap;
	margin-top: $mw-space-3;
	padding-top: $mw-space-3;
	border-top: 1rpx solid $mw-line;
}
.meta__one {
	margin-right: $mw-space-3;
	font-size: 22rpx;
	color: $mw-muted;
	font-variant-numeric: tabular-nums;
}

/* 免责声明：琥珀是"时间/照护"色，用于提示条底与左边线是合规用法（不做文字色） */
.warn {
	margin-top: $mw-space-3;
	padding: $mw-space-3;
	background: rgba(255, 176, 32, 0.12);
	border-left: 6rpx solid $mw-accent;
	border-radius: $mw-radius-tag;
}
.warn__text {
	font-size: 22rpx;
	line-height: 1.6;
	color: $mw-accent-ink;
}

.sec {
	margin-top: $mw-space-6;
}
.sec__title {
	display: block;
	font-size: $mw-font-h2;
	font-weight: 600;
	color: $mw-ink;
}
.sec__note {
	display: block;
	margin-top: $mw-space-1;
	margin-bottom: $mw-space-3;
	font-size: 22rpx;
	color: $mw-ink-2;
}
.rel {
	padding: $mw-space-4;
	margin-bottom: $mw-space-2;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
.rel__title {
	display: block;
	font-size: 30rpx;
	font-weight: 600;
	color: $mw-ink;
	line-height: 1.4;
}
.rel__summary {
	display: block;
	margin-top: $mw-space-1;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
	line-height: 1.6;
}

.note {
	display: block;
	margin-top: $mw-space-4;
	font-size: 22rpx;
	color: $mw-muted;
}
</style>