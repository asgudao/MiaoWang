<template>
	<!--
		帖子媒体区（**详情页 + 社区信息流 + 搜索结果 三处共用这一个组件**）

		🔴 为什么必须共用一个组件：2026-09-21 真机实测「9 张照片排成 2 列 5 行 / 3 张排成 2 列 2 行 /
		2 张排成 1 列 2 行」。根因是**三份重复代码**都在用"按假定卡片宽 622rpx 硬算出的格子边长"
		（3 格 = 202rpx×3 + 8rpx×2 = 622rpx；2 格 = 307rpx×2 + 8rpx = 622rpx），
		而卡片自己还有 `border: 1px`（左右各 1px）→ **真实可用宽是 622rpx − 2px**，
		一行差这 2px 就 `flex-wrap`，最后一格被挤到下一行（症状与真机报告逐条对上）。
		三份代码各错一次、各修一次必然再次分叉，所以规则收敛到这一个文件里。

		✅ 现在有两道结构性保证：
		① **行由 `rows` 给出**：一行一个 `display:flex`（**不换行**）的容器，
		   "这一行有几列" = 这一行里有几个格子 → **列数只由张数决定**，不由换行行为决定；
		② 格子宽度**相对容器**：`calc((100% − (N−1)×间距) / N)`（等价于 `repeat(N, 1fr)`），
		   没有任何"按 622rpx 假定宽算出来的定值"。
		⚠️ 模板里**不再有** `:style="c.style"`（那正是固定 rpx 边长进模板的入口）。
		⚠️ 布局用 flex + 百分比 + calc 而不是 `display:grid`：grid 在部分小程序 / 老 webview 上不可用。

		🔴 **2026-09-22：视频也进九宫格**（用户："视频要有和照片一样的九宫格，在社区初始页面显示，
		效果和照片一样"）——`media` 是**图片 + 视频合并**的序列（每项 `{url, kind}`），
		视频格**用的就是图片格那一套几何**（同一个 `.pics__cell`、同一条宽度算式、同一个 `gap`），
		区别只在"格子里面画什么"：图片用 `<image mode="aspectFill">`，视频用
		**静态中性占位 + 自绘播放角标**（**列表里不放 `<video>`**）。
		⚠️ **为什么列表里不放 `<video>`**：App 端的 `<video>` 是 native 组件，真机实测**既不出首帧
		（看不到封面）又吞点击（点不开）**；后端 `PostVideoRespDTO` 也**没有封面字段**
		（逐字只有 `{url, objectKey}`）⇒ 只能"不编造封面"地给一块中性底 + 三角，
		点它进详情页播（详情页才是真播放器）。
		⚠️ 视频格**不能是空的**（用户要求）：底 + 角标保证"绝不空白、绝不纯黑"。

		无图无视频 → 调用方 `v-if` 整块不渲染（本组件自己**不写** display:none / height:0 的兜底）。
	-->
	<view v-if="hasMedia" class="pics" :class="['pics--' + tier, wide ? 'pics--wide' : '']">
		<!--
			✅ **恰好 1 个媒体**：
			  · 是图片 → **原比例完整显示**（`widthFix`：宽度铺满，高度按**原图长宽比**自动算），
			    不裁切、**不限高**（用户定稿："不要收窄图片高度"）；
			  · 是视频 → 16:9 的框；⚠️ **2026-09-22 后端已交付首帧封面**（`PostVideoRespDTO.coverUrl`）
			    ⇒ 有封面就把封面按 `aspectFill` 铺满这个框（框的比例仍保留为**兜底产品口径**，
			    因为 `coverUrl` 可能为 `null`，那时只有中性占位可画）。
			两者宽度**共用同一条声明**（`1*2 = 一行两格`上限），所以单图和单视频一样宽。
		-->
		<image
			v-if="tier === 'single' && singleKind !== 'video' && !singleFailed"
			class="pics__single"
			:src="firstUrl"
			mode="widthFix"
			@click.stop="$emit('preview', 0)"
			@error="$emit('fail', firstUrl)"
		/>
		<!-- 单个视频：点它进详情页播（列表里**不内联播放**，也不给每个视频建播放器） -->
		<view
			v-else-if="tier === 'single' && singleKind === 'video'"
			class="pics__video pics__video--single"
			@click.stop="$emit('open', firstUrl)"
		>
			<!-- ✅ 有真封面就显示真封面（后端抽帧的 `coverUrl`）；⚠️ 空/失败 ⇒ 下面的中性占位（**绝不空白**） -->
			<image
				v-if="firstCover && !coverFailed[firstCover]"
				class="pics__video-cover"
				:src="firstCover"
				mode="aspectFill"
				@error="onCoverError(firstCover)"
			/>
			<view v-if="!(firstCover && !coverFailed[firstCover])" class="pics__video-hold">
				<view class="pics__video-play"><view class="pics__video-tri"></view></view>
			</view>
			<!-- 有封面时角标也要在：它是"这是视频 + 点进去能播"的唯一标识（无白底、无"视频"二字） -->
			<view v-if="firstCover && !coverFailed[firstCover]" class="pics__video-play">
				<view class="pics__video-tri"></view>
			</view>
		</view>
		<!-- ⚠️ 单图挂了也要**说出来**（不然和"这条帖子没图"长得一样 = 静默当没图） -->
		<view v-else-if="tier === 'single'" class="pics__fail pics__fail--single">
			<text class="pics__fail-text">图片加载失败</text>
		</view>
		<template v-else>
			<!-- 一行一个 nowrap 容器：**几个格子就是几列** -->
			<view v-for="(row, ri) in rows" :key="ri" class="pics__row">
				<view
					v-for="(c, ci) in row"
					:key="ri + '-' + ci"
					class="pics__cell"
					:class="{ 'pics__cell--video': c.kind === 'video' }"
					@click.stop="onCell(c)"
				>
					<!--
						🔴 **视频格**：与图片格同宽同高同间距（几何就是这个外层 `.pics__cell`），
						里面 = **封面（有就用真的）+ 自绘播放角标**，**不放 `<video>`**。

						⚠️ **为什么列表里不放 `<video>`（真机实测结论，2026-09-22）**：
						  用户实测「视频**没有显示封面**，**也点不开**」——
						  ① App 端的 `<video>` 是 **native 组件**，压在 webview 之上：
						     `preload` 语义不保证渲染首帧 ⇒ **看不到封面**；
						  ② 同一个 native 层会**吞掉点击**（`pointer-events` 对 native 组件无效）
						     ⇒ **点格子进不了详情页**。
						  所以退回"静态（封面/中性占位）+ 角标"，点击一律走 `@open` → 详情页播。

						✅ **2026-09-22 后端交付首帧封面后的现状**：
						  `PostVideoRespDTO.coverUrl`（`dto/resp/PostVideoRespDTO.java:34`）有值 ⇒ 用**真封面**，
						  并且**复用图片格那条 `.pics__one` + `mode="aspectFill"`**（裁剪口径与图片格逐项相同）；
						  为空或 `@error` ⇒ 退回中性占位 + 三角（**绝不空白、绝不纯黑**，格子也不从布局里消失）。
						  ⚠️ **不编造封面**：封面只认 `coverUrl`，**不许**拿 `post.coverUrl`（帖子封面）顶替。
					-->
					<image
						v-if="c.kind === 'video' && c.cover && !coverFailed[c.cover]"
						class="pics__one"
						:src="c.cover"
						mode="aspectFill"
						@error="onCoverError(c.cover)"
					/>
					<view v-if="c.kind === 'video' && !(c.cover && !coverFailed[c.cover])" class="pics__video-hold">
						<view class="pics__video-play"><view class="pics__video-tri"></view></view>
					</view>
					<!-- 有封面时角标也必须在：它是"这是视频 + 点进去能播"的唯一自绘标识（无白底、无"视频"二字） -->
					<view v-if="c.kind === 'video' && c.cover && !coverFailed[c.cover]" class="pics__video-play">
						<view class="pics__video-tri"></view>
					</view>
					<!-- ✅ ≥2 个：**裁剪**成方形缩略格（`aspectFill` = 等比铺满 + 居中裁掉溢出，**不变形**） -->
					<image
						v-if="c.kind !== 'video' && !c.failed"
						class="pics__one"
						:src="c.url"
						mode="aspectFill"
						@error="$emit('fail', c.url)"
					/>
					<!-- ⚠️ 加载**真失败**：格子留在原地并如实说明（**不许**静默当"没图"、也不许抽掉格子） -->
					<view v-if="c.kind !== 'video' && c.failed" class="pics__fail">
						<text class="pics__fail-text">{{ c.failedText }}</text>
					</view>
					<text v-if="c.overflowText" class="pics__more">{{ c.overflowText }}</text>
				</view>
			</view>
		</template>
	</view>
</template>

<script>
/**
 * 帖子媒体区（无媒体 → 整块不渲染；**1 个 = 原比例不裁切大图 / 16:9 视频框**；
 * ≥2 个 = **裁剪**成方形缩略格，列数由个数决定：2→一行两列、3→一行三列、4→2×2、5–9→3×3）
 *
 * **组件自己不发请求、不持数据、不算布局**：`media / grid / rows` 全由页面从
 * `utils/community.js` 的 `cardView()` / `mediaGridOf()` 算好后传进来。
 * 事件：`preview(index)` 点图看大图（**给的是原图 url，不是缩略图**）、`fail(url)` 图片加载失败、
 *       `open(url)` 点视频格 → 调用方进详情页播（列表里**不内联播放**）。
 *
 * 三处用法（community.vue / search.vue / detail.vue）完全一样，只差外层容器与 `media` 的来源：
 * 列表卡用 `cardView().media`（图片 + 视频），详情页用 `postDetailView().media`（**只有图片** ——
 * 详情页的视频在下方的独立播放器里，并进九宫格会变成"同一段视频出现两次"）。
 */
export default {
	name: 'PostImages',
	props: {
		/**
		 * 媒体序列（**原图/原视频 url**）：`[{ url, kind }]`，`kind` 是 `'image' | 'video'`。
		 * ⚠️ 兼容纯字符串数组（老调用与门禁按 `string[]` 传过）：那种一律当图片。
		 */
		media: { type: Array, default: () => [] },
		/** `imageLayout()` 的结果 `{ tier, perRow, columns, visible, overflow, count }`（**合并后**的个数算的） */
		grid: { type: Object, default: () => ({ tier: 'none', perRow: 1, columns: 1 }) },
		/** `imageRows()` 的结果：每行一个数组，每行的长度就是这一行的列数（**不含固定 rpx 尺寸**） */
		rows: { type: Array, default: () => [] },
		/** **恰好 1 个**且它是图片、且加载失败 → 单图那一支改渲染「图片加载失败」占位（`mediaGridOf().singleFailed`） */
		singleFailed: { type: Boolean, default: false },
		/**
		 * 单项**不套「最宽 = 2 格」上限**（宽度铺满媒体区）—— 只有**详情页**传 `true`。
		 * 依据（2026-09-21 用户定稿）：列表里单图最宽 = 一行两格（媒体区 2/3），
		 * 但「大图预览与**详情页**里的图不受此限 —— 那里要看完整原图」。
		 * ⚠️ 多格档位不受这个开关影响（它们的单格宽一律 = 媒体区 1/3）。
		 */
		wide: { type: Boolean, default: false }
	},
	data() {
		return {
			/**
			 * ⚠️ **2026-09-22（后端交付首帧封面后新增）**：封面图**真加载失败**（`@error`）的 url 集合。
			 * 记下来的目的只有一个：**退回中性占位 + 角标**（用户硬要求"绝不能空白格"）。
			 * ⚠️ 只影响这一格自己：不摘格子、不改 `media`、不影响别的格子，也不动布局（几何全在外层 `.pics__cell`）。
			 */
			coverFailed: {}
		}
	},
	computed: {
		/** 图片 + 视频的**总个数**（不是只有图片） */
		hasMedia() {
			return this.media.length > 0
		},
		tier() {
			return (this.grid && this.grid.tier) || 'none'
		},
		/** 恰好 1 个媒体时的那个（模板只用它的 url / kind） */
		first() {
			return this.media.length ? this.media[0] : {}
		},
		firstUrl() {
			return this.first && this.first.url ? this.first.url : ''
		},
		/** 恰好 1 个时，它是图还是视频（`''` = 不是单项档） */
		singleKind() {
			return this.media.length === 1 ? (this.first.kind === 'video' ? 'video' : 'image') : ''
		},
		/** ⚠️ **2026-09-22 新增**：单项视频的**封面 URL**（空串 = 后端没给 ⇒ 中性占位兜底） */
		firstCover() {
			return this.first && this.first.cover ? String(this.first.cover) : ''
		}
	},
	methods: {
		/** 点格子：视频进详情页播，图片看大图（`c.index` 是**图片序列**里的下标，视频格是 -1） */
		onCell(c) {
			if (c && c.kind === 'video') {
				this.$emit('open', c.url)
				return
			}
			this.$emit('preview', c ? c.index : 0)
		},
		/**
		 * ⚠️ **2026-09-22 新增**：视频**封面图**加载失败 → 记下来，模板退回"中性占位 + 角标"。
		 * ⚠️ 只标记这一格的封面；**不摘格子、不改布局、不影响其它格子**（用户硬要求"绝不空白格"）。
		 */
		onCoverError(url) {
			if (!url) return
			const next = Object.assign({}, this.coverFailed)
			next[url] = true
			this.coverFailed = next
		}
	}
}
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

/* ---- 媒体区（无媒体 → 整块不渲染；1 个大图/16:9 视频框；≥2 个正方形网格） ---- */
.pics {
	margin-top: $mw-space-3;
}
/*
	🔴 **单项最宽 = 几个格子宽**：只在这一行改（2026-09-21 用户定稿：「限制单张图的宽度最宽为 1*2 的宽度」
	   ⇒ 2 = 一行两格 = 媒体区 2/3 = `2×单格 + 1×间距`，与「2 张一行」的整块宽**逐像素相等**）。
	   ⚠️ 若本意是"媒体区 1/2"，把这里的 `2` 改成 `1.5` 即可 —— 下面那条宽度就是这一处算出来的。
*/
$pics-single-slots: 2;
/* 单项宽度里的"格间间距份数" = 格子数 − 1（**在 SCSS 里先算好**，别把 `(2 - 1)` 丢进 calc 里赌编译器） */
$pics-single-gaps: $pics-single-slots - 1;
/*
	✅ 单项（单图 / **单视频** / 单图挂掉的占位）：**原比例完整显示**（不裁切、不限高）。
	   宽度用**同一套相对算式**（没有固定 rpx / 像素值）：
	   `(媒体区 − 2×间距) / 3 × 格子数 + 间距 × 间距份数` —— 两个数都来自上面的具名常量。
	   🔴 **2026-09-22**：`.pics__video--single` 也挂到**这一条**上（单视频与单图**同宽**，
	   不是第二套尺寸）；"单图挂掉"的占位同样共用 → 挂掉时不会突然变满宽。
*/
.pics__single,
.pics__fail--single,
.pics__video--single {
	width: calc((100% - #{$mw-space-1} * 2) / 3 * #{$pics-single-slots} + #{$mw-space-1} * #{$pics-single-gaps});
}
.pics__single {
	display: block;
	border-radius: $mw-radius-input;
	background: $mw-canvas;
}
/*
	🔴 **详情页**（`wide`）的单图不套上面那条上限：铺满媒体区。
	   依据（2026-09-21 用户定稿）：「大图预览（`uni.previewImage`）与**详情页里的图**不受此限 —— 那里要看完整原图」。
	   预览本来就拿**原图 url**、不经过任何 CSS；详情页靠这个开关把宽度放开。
	⚠️ 只管单图：多格档位的单格宽仍恒为媒体区 1/3；详情页的 `media` 里**根本没有视频**。
*/
.pics--wide .pics__single {
	width: 100%;
}

/*
	🔴 一行一个容器，**不换行**：列数 = 这一行里的格子数（DOM 结构说了算，不是 wrap 说了算）。
	这就是"9 张排成两列"的根治点 —— 只要行不 wrap，宽度哪怕差半个像素也绝不会掉到下一行。
	（这里刻意**不写** `flex-wrap`：默认 `nowrap`；本文件全局也不许再出现 `flex-wrap`。）
	⚠️ 行本身**恒为满宽**（媒体区 100%），格子只占左边几格 → 2 格 / 2×2 时右边自然留白。
*/
.pics__row {
	display: flex;
}
/*
	🔴 **所有多格档位共用同一条单格宽度**（2026-09-21 用户定稿：「2×2 单独一个格的宽度要与 3×3 的相同」）：
	单格宽 = `(媒体区宽 − 2×间距) / 3` —— 也就是**媒体区的 1/3**（减掉它那份间距）。
	🔴 **2026-09-22**：图片格与视频格**用的是这同一条**（视频没有自己的宽度规则）。

	| 个数 | 排列 | 单格宽 | 整块（格子 + 格间间距） |
	|---|---|---|---|
	| 2 | 一行 2 格 | 1/3 | `2×单格 + 间距` = 2/3 − 间距/3（右侧留白）|
	| 3 | 一行 3 格 | 1/3 | `3×单格 + 2×间距` = **满宽** |
	| 4 | 2×2 | 1/3 | 同上（2/3，右侧留白）|
	| 5–9 | 3 列 | 1/3 | **满宽** |

	⚠️ 所以这里**只有一条** `width`，不许再出现 `.pics--two .pics__cell` 这类"每档各写一个宽度"
	   （那正是"2×2 的格子比 3×3 的宽"的成因）；也**不许**任何"按 622rpx 假定卡片宽算出来的固定边长"。
	⚠️ `flex: 0 0 auto`（不是 `1 1 auto`）：格子宽度就是上面算出来的值，**不许被 flex 拉伸** ——
	   一旦让它弹性伸缩，"每档宽度相同"就不再由这条算式保证，而是由"恰好还剩多少空间"决定。
*/
.pics__cell {
	flex: 0 0 auto;
	width: calc((100% - #{$mw-space-1} * 2) / 3);
	min-width: 0;
	position: relative;
	margin-right: $mw-space-1;
	margin-bottom: $mw-space-1;
	/* ✅ 多格缩略格 = **正方形**（`aspect-ratio` 让高度跟着宽度走）→ 图再用 `aspectFill` 裁成方形 */
	aspect-ratio: 1;
}
/* 每行最后一个格子不留右边距（行内格子数由 `rows` 决定，所以这里用 `:last-child` 就是对的） */
.pics__cell:last-child {
	margin-right: 0;
}
/* 最后一行不留底部外边距（否则媒体区下面多出一条空白） */
.pics__row:last-child .pics__cell {
	margin-bottom: 0;
}
.pics__one {
	width: 100%;
	height: 100%;
	border-radius: $mw-radius-input;
	background: $mw-canvas;
}
/*
	⚠️ **2026-09-22 视频格**（用户："视频要有和照片一样的九宫格，效果和照片一样"）。
	几何**一个字都没动**：宽高间距全来自外层 `.pics__cell`（正方形、1/3 宽、同一个 gap），
	`.pics__cell--video` 这条规则里**不写**任何 width / height / aspect-ratio —— 写了就是第二套几何。
	它只给格子一块**中性底**（既有令牌色）：**列表里不放 `<video>`**（真机实测 native 组件既不出首帧、
	又吞点击），只有静态底 + 自绘角标 ⇒ 永远是一块"看得见、点得动"的格子，不是空白、也不是纯黑。
*/
.pics__cell--video {
	background: $mw-canvas;
	border: 1rpx solid $mw-line;
	box-sizing: border-box;
	border-radius: $mw-radius-input;
	overflow: hidden;
}
/* 单个视频的框：宽度来自上面那条与单图共用的声明；比例是**产品口径** 16:9
   （后端不给封面/首帧字段 → 拿不到真比例，见 `script` 里的注释） */
.pics__video--single {
	position: relative;
	overflow: hidden;
	aspect-ratio: 16 / 9;
	background: $mw-canvas;
	border: 1rpx solid $mw-line;
	box-sizing: border-box;
	border-radius: $mw-radius-input;
}
/* 视频格的静态占位层：铺满格子（不写死宽高，靠 100%），底色沿用格子的中性底 */
.pics__video-hold {
	width: 100%;
	height: 100%;
	background: $mw-canvas;
}
/* ⚠️ **单项视频的真封面**（2026-09-22 后端交付 `coverUrl` 后新增）：
   只负责"铺满那个 16:9 的框"，**不参与任何尺寸决策**（框的比例来自 `.pics__video--single`）。
   多格档位里视频封面直接复用图片格的 `.pics__one`（同一裁剪口径），**没有第二条封面样式**。 */
.pics__video-cover {
	width: 100%;
	height: 100%;
}
/*
	自绘**播放角标**（用户 2026-09-22：「视频中间的播放键为什么有白色背景，可以去掉吗，
	还有"视频"两个字也去掉」）：
	  · **没有白底**：圆盘是**半透明深色**（`$mw-night` 派生，跟着母色走，不写死 rgb），
	    压在视频画面上也看得清；
	  · **没有文案**：只有三角，"这是视频"靠角标本身表达；
	  · 三角用 border 画（**不引图标库、不用 emoji** —— 手机端没有 Web 那套图标依赖）。
	⚠️ 角标**不吃点击**（`pointer-events: none`）：点格子 = 进详情页播。
*/
.pics__video-play {
	position: absolute;
	top: 50%;
	left: 50%;
	transform: translate(-50%, -50%);
	display: flex;
	align-items: center;
	justify-content: center;
	padding: $mw-space-2;
	border-radius: $mw-radius-input;
	background: rgba($mw-night, 0.62);
	pointer-events: none;
}
.pics__video-tri {
	/*
		三角用**内边距定尺寸 + `clip-path` 切形状**。
		⚠️ 刻意**不用** `width: 0; height: 0` + `border` 那套经典写法：门禁把 `height: 0` 当
		"假装不渲染"的信号（见 `check-community-search-phone.mjs` 的 I24），这里不给自己挖坑。
	*/
	padding: 14rpx 11rpx;
	background: $mw-on-night;
	clip-path: polygon(0 0, 100% 50%, 0 100%);
	margin-left: 6rpx;
}
/*
	加载**真失败**的格子：留在原地（不抽掉、不塌成 0 高 —— 抽掉会让后面的图跳位，用户就看不出这里少了一张），
	写一句实话。配色只用既有令牌：底 `$mw-canvas`、字 `$mw-muted`。
*/
.pics__fail {
	display: flex;
	align-items: center;
	justify-content: center;
	width: 100%;
	height: 100%;
	padding: $mw-space-1;
	box-sizing: border-box;
	border-radius: $mw-radius-input;
	background: $mw-canvas;
	border: 1rpx solid $mw-line;
}
.pics__fail-text {
	font-size: $mw-font-micro;
	color: $mw-muted;
	text-align: center;
}
/* 单图挂掉时的占位：宽度来自上面那条与单图共用的声明，这里只补高度（不是 0 高、也不是一块假图） */
.pics__fail--single {
	height: 220rpx;
}
/*
	老 webview 不支持 `aspect-ratio`（Chrome < 88）时的兜底：用 `padding-top: 100%`
	（百分比内边距按**包含块宽度**算 → 正好撑出一个正方形），图改成绝对定位铺满。
	@supports 不被支持时整块被忽略 → 退回上面的 aspect-ratio 路径，两条路都不会两个高度叠加。
	⚠️ 视频层也一并铺满（否则老 webview 上视频格的 `<video>` 只有 0 高 → 看起来就是"空格子"）。
*/
@supports not (aspect-ratio: 1) {
	.pics__cell::before {
		content: '';
		display: block;
		padding-top: 100%;
	}
	.pics__cell .pics__one,
	.pics__cell .pics__video-hold {
		position: absolute;
		top: 0;
		left: 0;
		width: 100%;
		height: 100%;
	}
}
/* >9 个：第 9 格右下角盖 +N */
.pics__more {
	position: absolute;
	right: 0;
	bottom: 0;
	min-width: 64rpx;
	text-align: center;
	padding: 4rpx 10rpx;
	border-top-left-radius: $mw-radius-input;
	border-bottom-right-radius: $mw-radius-input;
	/* 半透明派生色跟着母色走：`$mw-night` = #1a1310（不写死 rgb 字面量，改令牌时这里自动跟） */
	background: rgba($mw-night, 0.62);
	font-size: 24rpx;
	font-weight: 600;
	color: $mw-on-night;
}
</style>