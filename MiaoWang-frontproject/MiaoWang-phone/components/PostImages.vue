<template>
	<!--
		帖子图片区（**详情页 + 社区信息流 + 搜索结果 三处共用这一个组件**）

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

		无图 → 调用方 `v-if` 整块不渲染（本组件自己**不写** display:none / height:0 的兜底）。
	-->
	<view v-if="hasImages" class="pics" :class="['pics--' + tier, wide ? 'pics--wide' : '']">
		<!--
			✅ **恰好 1 张：原比例完整显示**（`widthFix`：宽度铺满、高度按**原图长宽比**自动算）
			—— 不裁切、**不限高**（用户定稿："不要收窄图片高度"）。
		-->
		<image
			v-if="tier === 'single' && !singleFailed"
			class="pics__single"
			:src="images[0]"
			mode="widthFix"
			@click.stop="$emit('preview', 0)"
			@error="$emit('fail', images[0])"
		/>
		<!-- ⚠️ 单图挂了也要**说出来**（不然和"这条帖子没图"长得一样 = 静默当没图） -->
		<view v-else-if="tier === 'single'" class="pics__fail pics__fail--single">
			<text class="pics__fail-text">图片加载失败</text>
		</view>
		<template v-else>
			<!-- 一行一个 nowrap 容器：**几个格子就是几列** -->
			<view v-for="(row, ri) in rows" :key="ri" class="pics__row">
				<view
					v-for="c in row"
					:key="c.index"
					class="pics__cell"
					@click.stop="$emit('preview', c.index)"
				>
					<!-- ✅ ≥2 张：**裁剪**成方形缩略格（`aspectFill` = 等比铺满 + 居中裁掉溢出，**不变形**） -->
					<image
						v-if="!c.failed"
						class="pics__one"
						:src="c.url"
						mode="aspectFill"
						@error="$emit('fail', c.url)"
					/>
					<!-- ⚠️ 加载**真失败**：格子留在原地并如实说明（**不许**静默当"没图"、也不许抽掉格子） -->
					<view v-else class="pics__fail">
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
 * 帖子图片区（无图 → 整块不渲染；**1 张 = 原比例不裁切大图**；≥2 张 = **裁剪**成方形缩略格，
 * 列数由张数决定：2→一行两列、3→一行三列、4→2×2、5–9→3×3）
 *
 * **组件自己不发请求、不持数据、不算布局**：`images / grid / rows` 全由页面从
 * `utils/community.js` 的 `cardView()` / `imageGridOf()` 算好后传进来。
 * 事件：`preview(index)` 点图看大图（**给的是原图 url，不是缩略图**）、`fail(url)` 加载失败。
 *
 * 三处用法（community.vue / search.vue / detail.vue）完全一样，只差外层容器：
 * 列表卡与详情页历史上分别用 `.pics` / `.imgs` 前缀 —— 前缀差异只体现在**调用方的注释**里，
 * 组件内部统一 `.pics*`，不再有三套会各自跑偏的 CSS。
 */
export default {
	name: 'PostImages',
	props: {
		/** 图片 url（**原图**；点开大图直接用它，缩略格的裁切只发生在本组件的 `aspectFill`） */
		images: { type: Array, default: () => [] },
		/** `imageLayout()` 的结果 `{ tier, perRow, columns, visible, overflow, count }` */
		grid: { type: Object, default: () => ({ tier: 'none', perRow: 1, columns: 1 }) },
		/** `imageRows()` 的结果：每行一个数组，每行的长度就是这一行的列数（**不含固定 rpx 尺寸**） */
		rows: { type: Array, default: () => [] },
		/** **恰好 1 张**且它加载失败 → 单图那一支改渲染「图片加载失败」占位（`imageGridOf().singleFailed`） */
		singleFailed: { type: Boolean, default: false },
		/**
		 * 单图**不套「最宽 = 2 格」上限**（宽度铺满媒体区）—— 只有**详情页**传 `true`。
		 * 依据（2026-09-21 用户定稿）：列表里单图最宽 = 一行两格（媒体区 2/3），
		 * 但「大图预览与**详情页**里的图不受此限 —— 那里要看完整原图」。
		 * ⚠️ 多图档位不受这个开关影响（它们的单格宽一律 = 媒体区 1/3）。
		 */
		wide: { type: Boolean, default: false }
	},
	computed: {
		hasImages() {
			return this.images.length > 0
		},
		tier() {
			return (this.grid && this.grid.tier) || 'none'
		}
	}
}
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

/* ---- 图片区（无图 → 整块不渲染；1 张大图；≥2 张正方形网格） ---- */
.pics {
	margin-top: $mw-space-3;
}
/*
	🔴 **单图最宽 = 几个格子宽**：只在这一行改（2026-09-21 用户定稿：「限制单张图的宽度最宽为 1*2 的宽度」
	   ⇒ 2 = 一行两格 = 媒体区 2/3 = `2×单格 + 1×间距`，与「2 张一行」的整块宽**逐像素相等**）。
	   ⚠️ 若本意是"媒体区 1/2"，把这里的 `2` 改成 `1.5` 即可 —— 下面那条宽度就是这一处算出来的。
*/
$pics-single-slots: 2;
/* 单图宽度里的"格间间距份数" = 格子数 − 1（**在 SCSS 里先算好**，别把 `(2 - 1)` 丢进 calc 里赌编译器） */
$pics-single-gaps: $pics-single-slots - 1;
/*
	✅ 单图：**原比例完整显示**（不裁切、不限高）。宽度用**同一套相对算式**（没有固定 rpx / 像素值）：
	   `(媒体区 − 2×间距) / 3 × 格子数 + 间距 × 间距份数` —— 两个数都来自上面的具名常量。
	   ⚠️ `.pics__single` 与"单图挂掉"的占位**共用同一条**宽度声明 → 挂掉时不会突然变满宽。
*/
.pics__single,
.pics__fail--single {
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
	⚠️ 只管单图：多图档位的单格宽仍恒为媒体区 1/3。
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
	🔴 **所有多图档位共用同一条单格宽度**（2026-09-21 用户定稿：「2×2 单独一个格的宽度要与 3×3 的相同」）：
	单格宽 = `(媒体区宽 − 2×间距) / 3` —— 也就是**媒体区的 1/3**（减掉它那份间距）。

	| 张数 | 排列 | 单格宽 | 整块（格子 + 格间间距） |
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
	/* ✅ 多图缩略格 = **正方形**（`aspect-ratio` 让高度跟着宽度走）→ 图再用 `aspectFill` 裁成方形 */
	aspect-ratio: 1;
}
/* 每行最后一个格子不留右边距（行内格子数由 `rows` 决定，所以这里用 `:last-child` 就是对的） */
.pics__cell:last-child {
	margin-right: 0;
}
/* 最后一行不留底部外边距（否则图片区下面多出一条空白） */
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
*/
@supports not (aspect-ratio: 1) {
	.pics__cell::before {
		content: '';
		display: block;
		padding-top: 100%;
	}
	.pics__cell .pics__one {
		position: absolute;
		top: 0;
		left: 0;
		width: 100%;
		height: 100%;
	}
}
/* >9 张：第 9 格右下角盖 +N */
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