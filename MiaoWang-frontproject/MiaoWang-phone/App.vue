<script>
	import { auth } from '@/store/auth.js'
	import { fetchRemindPopup } from '@/api/home.js'

	export default {
		onLaunch: function() {
			// 启动时恢复登录态：本地有会话就先当已登录，有效性由请求层判定
			// （后端没起时不要把用户踢出去）
			auth.restore()
			// 照护提醒弹窗：**启动时算一次**。
			// 弹窗的 DOM 由首页（`pages/index/index.vue`）渲染 —— uni-app 的 App.vue 没有 <template>，
			// 所以这里只负责"把状态算出来"；用户停在别的 tab 时，回到首页就会立刻显示（onShow 里读同一份状态）。
			fetchRemindPopup()
		},
		onShow: function() {
			// 从后台回前台也要重算：到点的那几条就是靠这一下补上的
			// （`setTimeout` 在后台会被系统挂起，所以"回前台重算"才是可靠路径，见 utils/local-notify.js 文件头）。
			// 与首页 onShow 共用 `api/remind.js` 的复用窗口 → 同一次前后台切换只发一遍请求。
			fetchRemindPopup()
		}
	}
</script>

<style lang="scss">
	/* 全局图标（base64 data-URI SVG 背景图）+ 每个页面公共 css
	 * 🔴 2026-09-22：图标从内联 `<svg>` 换成 CSS 背景图 —— App 端视图层用 `document.createElement(tag)`
	 * 造节点，内联 svg 落在 HTML 命名空间（HTMLUnknownElement）→ 编译得进去也**永远画不出来**；
	 * 详见 `styles/icons.scss` 抬头的实测根因。全局引入，页面只写 class（零运行时依赖）。 */
	@import '@/styles/icons.scss';

	page {
		background-color: #f7f2ee;
		color: #1d1512;
		font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'HarmonyOS Sans SC', 'Noto Sans SC', 'Microsoft YaHei', system-ui, sans-serif;
		line-height: 1.65;
	}
</style>