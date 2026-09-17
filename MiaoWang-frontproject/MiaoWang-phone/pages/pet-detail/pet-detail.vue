<template>
	<view class="page">
		<view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav__back" @click="goBack">
				<view class="chev chev--left"></view>
			</view>
			<text class="nav__title">宠物档案</text>
			<view class="nav__spacer"></view>
		</view>

		<view v-if="loading" class="state">
			<text class="state__desc">正在读取档案</text>
		</view>

		<view v-else-if="!view" class="state">
			<text class="state__title">找不到这只宠物</text>
			<text class="state__desc">它可能已经被删除了</text>
			<view class="btn btn--primary state__btn" @click="goBack"><text>回到萌宠</text></view>
		</view>

		<template v-else>
			<view class="hero">
				<view class="hero__avatar"><text>{{ view.initial }}</text></view>
				<view class="hero__main">
					<text class="hero__name">{{ view.name }}</text>
					<text class="hero__meta">{{ view.breed }} {{ view.age }}</text>
					<text class="hero__call">{{ callText }}</text>
				</view>
			</view>

			<view v-if="view.needName" class="alert">
				<text>这只宠物的名字是空的（后端目前不校验名字，历史数据里会出现空名）。点下面的「编辑」补一个吧。</text>
			</view>

			<view class="panel">
				<text class="panel__title">基本信息</text>
				<view class="row">
					<text class="row__k">大类</text>
					<text class="row__v">{{ view.species }}</text>
				</view>
				<view class="row">
					<text class="row__k">具体品种</text>
					<text class="row__v">{{ view.specific }}</text>
				</view>
				<view class="row">
					<text class="row__k">生日</text>
					<text class="row__v">{{ view.birth }}</text>
				</view>
				<view class="row">
					<text class="row__k">性别</text>
					<text class="row__v">{{ view.gender }}</text>
				</view>
				<view class="row">
					<text class="row__k">绝育</text>
					<text class="row__v">{{ view.sterile }}</text>
				</view>
				<view class="row row--last">
					<text class="row__k">档案编号</text>
					<text class="row__v">#{{ view.pid }}</text>
				</view>
			</view>

			<view class="panel">
				<text class="panel__title">接下来</text>
				<text class="panel__note">体重曲线、疫苗与驱虫提醒、AI 解读症状都归健康模块管，那个模块还没接上，现在点进去也没有数据。</text>
			</view>

			<view v-if="error" class="alert">
				<text>{{ error }}</text>
			</view>

			<view class="actions">
				<view class="btn btn--primary" @click="goEdit"><text>编辑</text></view>
				<view class="btn btn--danger" @click="onDelete">
					<text>{{ busy ? '删除中' : confirming ? '确认删除' : '删除档案' }}</text>
				</view>
				<view v-if="confirming" class="btn btn--ghost" @click="confirming = false"><text>先不删</text></view>
			</view>
			<text class="foot">{{ confirming ? '删除后它会从列表里消失（后端做的是标记删除，原始数据仍留在库里）' : '删除会同时失去它的健康记录入口' }}</text>
		</template>
	</view>
</template>

<script>
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
	data() {
		return {
			pid: 0,
			loading: true,
			confirming: false,
			error: '',
			statusBarHeight: 20
		}
	},
	computed: {
		busy() {
			return pets.state.busy
		},
		/** 显示字段一次算好；模板不调 utils 的函数（那些不在实例上） */
		view() {
			const p = pets.byId(this.pid)
			if (!p) return null
			return {
				pid: p.pid,
				initial: initials(p),
				name: petDisplayName(p),
				breed: breedLabel(p),
				age: ageLabel(p.birth),
				species: p.species || '未填',
				specific: p.specificSpecies || '未填',
				birth: p.birth || '未填',
				gender: genderLabel(p.gender),
				sterile: sterilizedLabel(p.sterilizedState),
				needName: needsName(p)
			}
		},
		callText() {
			const p = pets.byId(this.pid)
			const call = p && p.ownerCallName
			return call ? '它叫你「' + call + '」' : '还没告诉它该怎么称呼你'
		}
	},
	onLoad(options) {
		try {
			const info = uni.getSystemInfoSync()
			this.statusBarHeight = info.statusBarHeight || 20
		} catch (e) {
			// 拿不到就用默认值
		}
		this.pid = Number((options && options.pid) || 0)
		pets.fetchMine().then(() => {
			this.loading = false
		})
	},
	methods: {
		goBack() {
			uni.navigateBack({
				fail() {
					uni.switchTab({ url: '/pages/pets/pets' })
				}
			})
		},
		goEdit() {
			uni.navigateTo({ url: '/pages/pet-edit/pet-edit?pid=' + this.pid })
		},
		async onDelete() {
			// 两步确认：手机上没有原生 confirm 的位置，就地改成两次点击
			if (!this.confirming) {
				this.confirming = true
				return
			}
			this.error = ''
			try {
				await pets.remove(this.pid)
				uni.navigateBack()
			} catch (e) {
				this.error = (e && e.message) || '删除失败，请稍后再试'
			} finally {
				this.confirming = false
			}
		}
	}
}
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.page {
	min-height: 100vh;
	background: $mw-canvas;
	padding-bottom: $mw-space-8;
}

.nav {
	display: flex;
	align-items: center;
	padding: 0 $mw-page-padding $mw-space-3;
	background: $mw-canvas;
}
.nav__back {
	width: 64rpx;
	height: 64rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}
.nav__title {
	flex: 1;
	text-align: center;
	font-size: $mw-font-h2;
	font-weight: 600;
	color: $mw-ink;
}
.nav__spacer {
	width: 64rpx;
}
.chev {
	width: 16rpx;
	height: 16rpx;
	flex: none;
	border-right: 3rpx solid $mw-muted;
	border-bottom: 3rpx solid $mw-muted;
	transform: rotate(-45deg);
}
.chev--left {
	transform: rotate(135deg);
	margin-left: 8rpx;
}

/* 夜幕卡片：主色只在这种深底上用（设计系统 v2 的硬规则） */
.hero {
	margin: 0 $mw-page-padding $mw-space-4;
	padding: $mw-space-4;
	background: $mw-night;
	border-radius: $mw-radius-card;
	display: flex;
	align-items: center;
}
.hero__avatar {
	width: 112rpx;
	height: 112rpx;
	border-radius: 50%;
	background: rgba(53, 214, 168, 0.16);
	display: flex;
	align-items: center;
	justify-content: center;
	flex: none;
}
.hero__avatar text {
	font-size: 44rpx;
	font-weight: 700;
	color: $mw-brand;
}
.hero__main {
	flex: 1;
	min-width: 0;
	margin-left: $mw-space-3;
	display: flex;
	flex-direction: column;
}
.hero__name {
	font-size: 40rpx;
	font-weight: 700;
	color: #ffffff;
}
.hero__meta {
	margin-top: 4rpx;
	font-size: $mw-font-caption;
	color: $mw-on-night-2;
}
.hero__call {
	margin-top: $mw-space-2;
	font-size: $mw-font-caption;
	color: $mw-brand;
}

.panel {
	margin: 0 $mw-page-padding $mw-space-4;
	padding: $mw-space-4;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
.panel__title {
	display: block;
	margin-bottom: $mw-space-3;
	font-size: $mw-font-h2;
	font-weight: 600;
	color: $mw-ink;
}
.panel__note {
	display: block;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
	line-height: 1.7;
}

.row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: $mw-space-2 0;
	border-bottom: 1px solid $mw-line;
}
.row--last {
	border-bottom: none;
}
.row__k {
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}
.row__v {
	font-size: $mw-font-body;
	font-weight: 600;
	color: $mw-ink;
}

.alert {
	margin: 0 $mw-page-padding $mw-space-4;
	padding: $mw-space-3;
	background: rgba(229, 72, 77, 0.08);
	border: 1px solid rgba(229, 72, 77, 0.2);
	border-radius: $mw-radius-input;
}
.alert text {
	font-size: $mw-font-caption;
	color: $mw-danger;
	line-height: 1.6;
}

.actions {
	display: flex;
	gap: $mw-space-2;
	margin: 0 $mw-page-padding;
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
.btn--primary {
	background: $mw-brand-ink;
}
.btn--primary text {
	color: #ffffff;
}
.btn--danger {
	border: 1px solid rgba(229, 72, 77, 0.3);
}
.btn--danger text {
	color: $mw-danger;
}
.btn--ghost {
	border: 1px solid $mw-line;
}
.btn--ghost text {
	color: $mw-ink;
}

.foot {
	display: block;
	margin: $mw-space-2 $mw-page-padding 0;
	text-align: center;
	font-size: 22rpx;
	color: $mw-muted;
	line-height: 1.6;
}

.state {
	margin: $mw-space-6 $mw-page-padding 0;
	padding: $mw-space-6 $mw-space-4;
	display: flex;
	flex-direction: column;
	align-items: center;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
.state__title {
	font-size: $mw-font-h2;
	font-weight: 600;
	color: $mw-ink;
}
.state__desc {
	margin: $mw-space-2 0 $mw-space-4;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}
.state__btn {
	flex: none;
	padding: 0 $mw-space-6;
}
</style>