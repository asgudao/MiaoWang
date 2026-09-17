<script>
	import { auth } from '@/store/auth.js'

	export default {
		onLaunch: function() {
			// 启动时恢复登录态：本地有会话就先当已登录，有效性由请求层判定
			// （后端没起时不要把用户踢出去）
			auth.restore()
		}
	}
</script>

<style>
	/* 每个页面公共css */
	page {
		background-color: #f4f6f7;
		color: #0e1720;
		font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'HarmonyOS Sans SC', 'Noto Sans SC', 'Microsoft YaHei', system-ui, sans-serif;
		line-height: 1.65;
	}
</style>
