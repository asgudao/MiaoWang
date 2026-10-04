<template>
	<view class="tabbar" :style="{ paddingBottom: safeBottom }">
		<view class="tabbar__pill">
			<view
				v-for="item in items"
				:key="item.path"
				class="tab"
				:class="{ 'tab--on': item.path === current }"
				@click="go(item.path)"
			>
				<view class="tab__icon">
					<!-- 「我的」tab 的未读红点：由 prop 驱动、v-if 控制。
					     ⚠️ 原生 tabBar 被 utils/tabbar.js 隐藏了，uni.setTabBarBadge / showTabBarRedDot
					     画了也看不见 —— 徽标只能画在这里（契约 §2.4）。只做入口红点，不做全站轮询。 -->
					<view v-if="item.key === 'mine' && mineDot" class="tab__dot"></view>
					<!--
						图标：**base64 data-URI 背景图**，不依赖已废弃的 static/tabbar PNG，也不引图标库。
						🔴 2026-09-22 实测根因（别再改回内联 `<svg>`）：App 端视图层
						（`unpackage/dist/dev/app-plus/uni-app-view.umd.js`）的元素工厂只认 uni 内置组件表，
						表外的标签一律 `document.createElement(tag)` → `<svg>` 落在 **HTML 命名空间**
						（HTMLUnknownElement，没有几何、不渲染）。CSS 背景图由 webview 的图片解码器
						按 SVG 命名空间解析，App / H5 都可靠。形状 = `item.key`；
						两态 = `-brand`（选中，$mw-brand-ink）/ `-muted`（未选，$mw-muted）。
					-->
					<view class="mw-ic mw-ic--tab"
						:class="'mw-ic--' + item.key + (item.path === current ? '-brand' : '-muted')"></view>
				</view>
				<text class="tab__label">{{ item.label }}</text>
			</view>
		</view>
	</view>
</template>

<script>
/**
 * 自绘浮岛 tabbar
 *
 * pages.json 里仍声明了原生 tabBar —— 只为保留 switchTab 的页面栈语义，
 * 运行时用 uni.hideTabBar() 隐藏（见 utils/tabbar.js），这里画我们自己的浮岛。
 */
export default {
	name: 'FloatingTabBar',
	props: {
		current: {
			type: String,
			default: 'home'
		},
		/**
		 * 「我的」tab 的**未读红点**（只做入口红点，不做全站轮询）
		 *
		 * 页面的 `onShow` 拉一次 `unread-count`（**裸数字**接口）→ 有未读就传 true。
		 * 拿不到未读数（未登录 / 掉线 / 网络失败）一律传 false —— **失败静默降级**，不影响页面本身。
		 */
		mineDot: {
			type: Boolean,
			default: false
		}
	},
	data() {
		return {
			safeBottom: 'env(safe-area-inset-bottom)',
			items: [
				{ key: 'home', label: '首页', path: '/pages/index/index' },
				{ key: 'community', label: '社区', path: '/pages/community/community' },
				{ key: 'pets', label: '萌宠', path: '/pages/pets/pets' },
				{ key: 'knowledge', label: '知识库', path: '/pages/knowledge/knowledge' },
				{ key: 'mine', label: '我的', path: '/pages/mine/mine' }
			]
		}
	},
	methods: {
		go(path) {
			const pages = getCurrentPages()
			const cur = pages.length ? '/' + pages[pages.length - 1].route : ''
			if (cur === path) return
			uni.switchTab({ url: path })
		}
	}
}
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.tabbar {
	position: fixed;
	left: 0;
	right: 0;
	bottom: 0;
	z-index: 90;
	padding: $mw-space-2 $mw-page-padding;
	pointer-events: none;
}

.tabbar__pill {
	display: flex;
	align-items: center;
	height: $mw-tabbar-h;
	padding: 0 $mw-space-1;
	background: rgba(255, 252, 249, 0.92);
	backdrop-filter: blur(20rpx);
	border: 1px solid $mw-line;
	border-radius: $mw-radius-pill;
	box-shadow: $mw-shadow-float;
	pointer-events: auto;
}

.tab {
	flex: 1;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	gap: 2rpx;
	min-height: $mw-touch-min;
}

.tab__icon {
	display: flex;
	align-items: center;
	justify-content: center;
	width: 56rpx;
	height: 56rpx;
	position: relative;
}

/* 未读红点：挂在图标右上角（原生 tabBar 被隐藏，徽标只有这一个落点） */
.tab__dot {
	position: absolute;
	top: 0;
	right: 0;
	width: 16rpx;
	height: 16rpx;
	border-radius: 50%;
	background: $mw-danger;
	border: 2rpx solid $mw-surface;
}

.tab__label {
	font-size: 20rpx;
	color: $mw-muted;
}

.tab--on .tab__label {
	color: $mw-brand-ink;
	font-weight: 600;
}
</style>