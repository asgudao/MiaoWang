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
					<!-- 图标自绘：不依赖已废弃的 static/tabbar PNG -->
					<svg viewBox="0 0 24 24" width="24" height="24" fill="none"
						:stroke="item.path === current ? '#0A7D6D' : '#9AA8B2'"
						stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
						<!-- 首页：房子 -->
						<template v-if="item.key === 'home'">
							<path d="M4 10.5 12 4l8 6.5V19a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z" />
						</template>
						<!-- 社区：对话气泡 -->
						<template v-else-if="item.key === 'community'">
							<path d="M4 7a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H9l-5 4z" />
							<path d="M8 10h8" />
						</template>
						<!-- 萌宠：爪印 -->
						<template v-else-if="item.key === 'pets'">
							<circle cx="7" cy="9" r="1.9" />
							<circle cx="12" cy="7" r="1.9" />
							<circle cx="17" cy="9" r="1.9" />
							<path d="M12 12.5c3 0 5.2 1.9 5.2 4.1 0 1.6-1.2 2.9-2.8 2.9h-4.8c-1.6 0-2.8-1.3-2.8-2.9 0-2.2 2.2-4.1 5.2-4.1z" />
						</template>
						<!-- 知识库：书 -->
						<template v-else-if="item.key === 'knowledge'">
							<path d="M5 5a2 2 0 0 1 2-2h11v16H7a2 2 0 0 0-2 2z" />
							<path d="M9 7.5h6" />
						</template>
						<!-- 我的：人 -->
						<template v-else>
							<circle cx="12" cy="8.5" r="3.4" />
							<path d="M5.5 20c0-3.2 2.9-5.6 6.5-5.6s6.5 2.4 6.5 5.6" />
						</template>
					</svg>
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
	background: rgba(255, 255, 255, 0.9);
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