/**
 * 原生 tabBar 只用来保留 switchTab 的页面栈语义，
 * 视觉上是自绘的浮岛（components/FloatingTabBar.vue），所以要把原生的隐藏掉。
 *
 * 用法：每个 tab 页的 onShow 里调用 hideNativeTabBar()
 * 注意：App/H5 要等 tabBar 真正渲染出来再隐藏，所以延后一帧 + 兜底重试。
 */
export function hideNativeTabBar () {
	// #ifdef MP
	// 小程序端 hideTabBar 需要 animation 参数，且必须已渲染
	// #endif
	const hide = () => {
		try {
			uni.hideTabBar({ animation: false, fail: () => {} })
		} catch (e) {}
	}
	hide()
	setTimeout(hide, 60)
	setTimeout(hide, 320)
}