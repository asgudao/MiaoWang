<template>
	<view class="page">
		<view class="head">
			<text class="h1">社区</text>
			<text class="sub">养宠人的日常，都在这里</text>
		</view>

		<scroll-view class="chips" scroll-x :show-scrollbar="false">
			<view
				v-for="s in sections"
				:key="s"
				class="chip"
				:class="{ 'chip--on': active === s }"
				@click="active = s"
			>
				<text>{{ s }}</text>
			</view>
		</scroll-view>

		<view v-for="p in posts" :key="p.id" class="post">
			<view class="post__head">
				<view class="avatar"><text>{{ p.initial }}</text></view>
				<view class="post__who">
					<text class="post__author">{{ p.author }}</text>
					<text class="post__time">{{ p.time }}</text>
				</view>
				<text class="tag">{{ p.tag }}</text>
			</view>
			<text class="post__text">{{ p.text }}</text>
			<view class="post__foot">
				<text>{{ p.likes }} 赞</text>
				<text>{{ p.comments }} 评论</text>
			</view>
		</view>

		<text class="note">演示数据 · 社区接口已就绪，服务启动后切真数据</text>

		<FloatingTabBar current="community" />
	</view>
</template>

<script>
import FloatingTabBar from '@/components/FloatingTabBar.vue'
import { hideNativeTabBar } from '@/utils/tabbar.js'

export default {
	components: { FloatingTabBar },
	data() {
		return {
			sections: ['推荐', '猫咪', '狗狗', '求助'],
			active: '推荐',
			posts: [
				{
					id: 1,
					author: '三点水的喵',
					initial: '喵',
					time: '12 分钟前',
					text: '第一次给它剪指甲，全程没挠我，奖励了一根猫条。分享一下姿势：抱在腿上背对我，一次只剪一只。',
					likes: 42,
					comments: 8,
					tag: '猫咪'
				},
				{
					id: 2,
					author: '柯基屁股研究所',
					initial: '汪',
					time: '1 小时前',
					text: '换粮第三天，软便终于好了。7 天过渡法真的有用，前三天新旧 1:3，别心急。',
					likes: 76,
					comments: 15,
					tag: '狗狗'
				},
				{
					id: 3,
					author: '夜班铲屎官',
					initial: '夜',
					time: '昨天 22:40',
					text: '猫咪半夜跑酷有解吗？试了睡前 20 分钟逗猫棒，连续三天它都能睡到早上六点。',
					likes: 128,
					comments: 31,
					tag: '求助'
				}
			]
		}
	},
	onLoad() {
		hideNativeTabBar()
	},
	onShow() {
		hideNativeTabBar()
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
.tag {
	padding: 4rpx 14rpx;
	border-radius: $mw-radius-tag;
	background: rgba(10, 125, 109, 0.12);
	font-size: 20rpx;
	font-weight: 600;
	color: $mw-success;
}
.post__text {
	display: block;
	margin-top: $mw-space-3;
	font-size: $mw-font-body;
	color: $mw-ink;
	line-height: 1.6;
}
.post__foot {
	display: flex;
	gap: $mw-space-6;
	margin-top: $mw-space-3;
	padding-top: $mw-space-2;
	border-top: 1px solid $mw-line;
}
.post__foot text {
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}

.note {
	display: block;
	margin-top: $mw-space-4;
	font-size: 22rpx;
	color: $mw-muted;
}
</style>