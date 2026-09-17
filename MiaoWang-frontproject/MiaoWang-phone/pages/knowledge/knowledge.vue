<template>
	<view class="page">
		<view class="head">
			<text class="h1">知识库</text>
			<text class="sub">经人工审校 · 仅供参考，具体请咨询兽医</text>
		</view>

		<view class="search">
			<input class="search__input" type="text" placeholder="搜症状、食材、疫苗…" placeholder-class="ph" v-model="keyword" />
		</view>

		<scroll-view class="chips" scroll-x :show-scrollbar="false">
			<view
				v-for="c in categories"
				:key="c"
				class="chip"
				:class="{ 'chip--on': active === c }"
				@click="active = c"
			>
				<text>{{ c }}</text>
			</view>
		</scroll-view>

		<view v-for="a in articles" :key="a.id" class="item" @click="toast">
			<text class="item__title">{{ a.title }}</text>
			<text class="item__summary">{{ a.summary }}</text>
			<view class="item__meta">
				<text class="tag">{{ a.category }}</text>
				<text class="item__time">约 {{ a.minutes }} 分钟</text>
			</view>
		</view>

		<view class="qa" @click="toast">
			<text class="qa__title">智能问答</text>
			<text class="qa__desc">知识库已接入检索增强问答，可以直接问"我家猫吐毛球怎么办"</text>
			<view class="qa__btn"><text>去提问</text></view>
		</view>

		<text class="note">演示数据 · 知识库接口免登录可读，服务启动后切真数据</text>

		<FloatingTabBar current="knowledge" />
	</view>
</template>

<script>
import FloatingTabBar from '@/components/FloatingTabBar.vue'
import { hideNativeTabBar } from '@/utils/tabbar.js'

export default {
	components: { FloatingTabBar },
	data() {
		return {
			keyword: '',
			categories: ['全部', '喂养', '健康', '行为', '新手'],
			active: '全部',
			articles: [
				{ id: 1, title: '猫咪应激的 6 个信号，第 3 个最容易被忽略', summary: '频繁舔毛、躲进柜子、突然不吃东西……这些都不是"闹脾气"，而是它在说压力太大了。', minutes: 4, category: '行为' },
				{ id: 2, title: '换粮要不要过渡？7 天新旧比例表', summary: '直接换粮是软便最常见的原因。这张比例表照着做，肠胃基本不会出问题。', minutes: 3, category: '喂养' },
				{ id: 3, title: '狗狗一年该打哪些疫苗，一张时间表说清', summary: '幼犬首年、成年加强、狂犬疫苗的时间点与间隔，附常见误区。', minutes: 5, category: '健康' },
				{ id: 4, title: '第一次养猫，前两周只需要做对这 5 件事', summary: '别急着买一堆东西。先把安全区、猫砂盆、饮水和就医路线安排好。', minutes: 6, category: '新手' }
			]
		}
	},
	onLoad() {
		hideNativeTabBar()
	},
	onShow() {
		hideNativeTabBar()
	},
	methods: {
		toast() {
			uni.showToast({ title: '功能开发中', icon: 'none' })
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
	margin-bottom: $mw-space-3;
}
.search__input {
	height: $mw-touch-min;
	padding: 0 $mw-space-4;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-input;
	font-size: $mw-font-body;
	color: $mw-ink;
}
.ph {
	color: $mw-muted;
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

.item {
	padding: $mw-space-4;
	margin-bottom: $mw-space-3;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
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
.item__meta {
	display: flex;
	align-items: center;
	margin-top: $mw-space-3;
}
.tag {
	padding: 4rpx 14rpx;
	border-radius: $mw-radius-tag;
	background: rgba(10, 125, 109, 0.12);
	font-size: 20rpx;
	font-weight: 600;
	color: $mw-success;
}
.item__time {
	margin-left: $mw-space-2;
	font-size: 22rpx;
	color: $mw-muted;
}

.qa {
	margin-top: $mw-space-6;
	padding: $mw-space-4;
	background: linear-gradient(0deg, rgba(10, 125, 109, 0.05), rgba(10, 125, 109, 0.05)), $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
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
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}
.qa__btn {
	margin-top: $mw-space-3;
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