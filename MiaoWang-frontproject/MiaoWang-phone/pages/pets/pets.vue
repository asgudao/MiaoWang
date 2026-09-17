<template>
	<view class="page">
		<template v-if="loggedIn">
			<view class="head">
				<view>
					<text class="h1">萌宠</text>
					<text class="sub">档案、体重、疫苗与驱虫记录</text>
				</view>
				<view class="add-btn" @click="goNew"><text>添加</text></view>
			</view>

			<!-- 首次加载：骨架，别让空页面闪一下 -->
			<view v-if="showSkeleton" class="card">
				<view class="card__head">
					<view class="avatar avatar--sk"></view>
					<view class="card__who">
						<view class="sk sk--line"></view>
						<view class="sk sk--line sk--short"></view>
					</view>
				</view>
			</view>

			<view v-else-if="error" class="alert">
				<text>{{ error }}</text>
				<view class="btn btn--ghost" @click="retry"><text>重试</text></view>
			</view>

			<view v-else-if="isEmpty" class="empty">
				<text class="empty__title">还没有宠物档案</text>
				<text class="empty__desc">添加之后，体重曲线和疫苗提醒都会挂在它名下</text>
				<view class="btn btn--primary empty__btn" @click="goNew"><text>添加第一只</text></view>
			</view>

			<view v-else>
				<view v-for="c in cards" :key="c.pid" class="card" @click="goDetail(c.pid)">
					<view class="card__head">
						<view class="avatar"><text>{{ c.initial }}</text></view>
						<view class="card__who">
							<text class="card__name">{{ c.name }}</text>
							<text class="card__meta">{{ c.breed }}</text>
						</view>
						<view class="chev"></view>
					</view>

					<view class="stats">
						<view class="stat">
							<text class="stat__label">年龄</text>
							<text class="stat__value">{{ c.age }}</text>
						</view>
						<view class="stat">
							<text class="stat__label">性别</text>
							<text class="stat__value">{{ c.gender }}</text>
						</view>
						<view class="stat">
							<text class="stat__label">绝育</text>
							<text class="stat__value">{{ c.sterile }}</text>
						</view>
					</view>

					<text v-if="c.callName" class="tag">它叫你「{{ c.callName }}」</text>
					<text v-else-if="c.needName" class="tag tag--warn">档案里还没名字，点进去补一个</text>
				</view>

				<text class="tail">体重、疫苗与驱虫记录在健康模块里管，那个模块还没接上</text>
			</view>
		</template>

		<!-- 未登录：软门槛。硬跳登录页会在返回时反复弹出，反而更差 -->
		<view v-else class="guest">
			<text class="guest__title">登录后才能看到宠物档案</text>
			<text class="guest__desc">档案、体重曲线、疫苗与驱虫记录都跟着账号走。</text>
			<view class="guest__actions">
				<view class="btn btn--primary" @click="goLogin"><text>去登录</text></view>
				<view class="btn btn--ghost" @click="goRegister"><text>注册一个</text></view>
			</view>
		</view>

		<FloatingTabBar current="pets" />
	</view>
</template>

<script>
import FloatingTabBar from '@/components/FloatingTabBar.vue'
import { hideNativeTabBar } from '@/utils/tabbar.js'
import { auth } from '@/store/auth.js'
import { pets } from '@/store/pets.js'
import {
	ageLabel,
	breedLabel,
	genderLabel,
	initials,
	needsName,
	petDisplayName,
	sterilizedLabel
} from '@/utils/pet.js'

export default {
	components: { FloatingTabBar },
	computed: {
		loggedIn() {
			return auth.isLoggedIn()
		},
		list() {
			return pets.state.list
		},
		loading() {
			return pets.state.loading
		},
		loaded() {
			return pets.state.loaded
		},
		error() {
			return pets.state.error
		},
		showSkeleton() {
			return this.loading && !this.loaded
		},
		isEmpty() {
			return this.loaded && this.list.length === 0
		},
		/**
		 * 显示字段在这里一次算好。
		 * 模板里**不许**直接调 utils 的函数（那些函数不在实例上，会渲染期报错并白屏）。
		 */
		cards() {
			return this.list.map((p) => ({
				pid: p.pid,
				initial: initials(p),
				name: petDisplayName(p),
				breed: breedLabel(p),
				age: ageLabel(p.birth),
				gender: genderLabel(p.gender),
				sterile: sterilizedLabel(p.sterilizedState),
				callName: p.ownerCallName || '',
				needName: needsName(p)
			}))
		}
	},
	onLoad() {
		hideNativeTabBar()
	},
	onShow() {
		hideNativeTabBar()
		// 不强制刷新：新建/修改/删除都会就地更新 store，来回切页不会白闪
		pets.fetchMine()
	},
	methods: {
		retry() {
			pets.fetchMine(true)
		},
		goNew() {
			uni.navigateTo({ url: '/pages/pet-edit/pet-edit' })
		},
		goDetail(pid) {
			uni.navigateTo({ url: '/pages/pet-detail/pet-detail?pid=' + pid })
		},
		goLogin() {
			uni.navigateTo({ url: '/pages/login/login' })
		},
		goRegister() {
			uni.navigateTo({ url: '/pages/register/register' })
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
	display: flex;
	align-items: flex-end;
	justify-content: space-between;
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
.add-btn {
	min-height: 72rpx;
	padding: 0 $mw-space-4;
	border-radius: $mw-radius-pill;
	background: $mw-brand-ink;
	display: flex;
	align-items: center;
}
.add-btn text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: #ffffff;
}

.card {
	padding: $mw-space-4;
	margin-bottom: $mw-space-3;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
.card__head {
	display: flex;
	align-items: center;
}
.avatar {
	width: 96rpx;
	height: 96rpx;
	border-radius: 50%;
	background: rgba(10, 125, 109, 0.1);
	display: flex;
	align-items: center;
	justify-content: center;
	flex: none;
}
.avatar text {
	font-size: 36rpx;
	font-weight: 700;
	color: $mw-brand-ink;
}
.card__who {
	flex: 1;
	min-width: 0;
	margin-left: $mw-space-3;
	display: flex;
	flex-direction: column;
}
.card__name {
	font-size: 36rpx;
	font-weight: 700;
	color: $mw-ink;
}
.card__meta {
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}

/* 自绘右箭头（本工程不用 npm 图标库，统一 1.7 描边） */
.chev {
	width: 18rpx;
	height: 18rpx;
	flex: none;
	border-right: 3rpx solid $mw-muted;
	border-bottom: 3rpx solid $mw-muted;
	transform: rotate(-45deg);
}

.stats {
	display: flex;
	margin-top: $mw-space-4;
}
.stat {
	margin-right: $mw-space-8;
	display: flex;
	flex-direction: column;
}
.stat__label {
	font-size: 22rpx;
	color: $mw-ink-2;
}
.stat__value {
	margin-top: 4rpx;
	font-size: 30rpx;
	font-weight: 600;
	color: $mw-ink;
}

.tag {
	display: inline-block;
	margin-top: $mw-space-3;
	padding: 6rpx 16rpx;
	border-radius: $mw-radius-tag;
	background: rgba(10, 125, 109, 0.08);
	font-size: 22rpx;
	color: $mw-brand-ink;
}
.tag--warn {
	background: rgba(255, 176, 32, 0.16);
	color: $mw-accent-ink;
}

.tail {
	display: block;
	margin-top: $mw-space-2;
	text-align: center;
	font-size: 22rpx;
	color: $mw-muted;
}

.alert {
	padding: $mw-space-4;
	background: rgba(229, 72, 77, 0.08);
	border: 1px solid rgba(229, 72, 77, 0.2);
	border-radius: $mw-radius-card;
}
.alert text {
	font-size: $mw-font-caption;
	color: $mw-danger;
	line-height: 1.6;
}
.alert .btn {
	margin-top: $mw-space-3;
}

.empty {
	margin-top: $mw-space-6;
	padding: $mw-space-6 $mw-space-4;
	display: flex;
	flex-direction: column;
	align-items: center;
	text-align: center;
}
.empty__title {
	font-size: $mw-font-h2;
	font-weight: 600;
	color: $mw-ink;
}
.empty__desc {
	margin: $mw-space-2 0 $mw-space-4;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}
.empty__btn {
	flex: none;
	padding: 0 $mw-space-6;
}

/* 骨架屏 */
.sk {
	background: $mw-canvas;
	border-radius: $mw-radius-input;
}
.avatar--sk {
	background: $mw-canvas;
}
.sk--line {
	height: 24rpx;
	margin-bottom: 12rpx;
}
.sk--short {
	width: 60%;
}

/* ---------- 未登录软门槛 ---------- */
.guest {
	margin-top: $mw-space-6;
	padding: $mw-space-6 $mw-space-4;
	display: flex;
	flex-direction: column;
	align-items: center;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
.guest__title {
	font-size: $mw-font-h2;
	font-weight: 600;
	color: $mw-ink;
	text-align: center;
}
.guest__desc {
	margin: $mw-space-2 0 $mw-space-4;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
	text-align: center;
	line-height: 1.7;
}
.guest__actions {
	display: flex;
	gap: $mw-space-2;
	width: 100%;
}

.btn {
	flex: 1;
	min-height: $mw-touch-min;
	border-radius: $mw-radius-pill;
	display: flex;
	align-items: center;
	justify-content: center;
}
.btn text {
	font-size: $mw-font-caption;
	font-weight: 600;
}
.btn--ghost {
	border: 1px solid $mw-line;
	background: transparent;
}
.btn--ghost text {
	color: $mw-ink;
}
.btn--primary {
	background: $mw-brand-ink;
}
.btn--primary text {
	color: #ffffff;
}
</style>