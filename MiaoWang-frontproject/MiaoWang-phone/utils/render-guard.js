/**
 * 渲染错误兜底（手机端 / 挂到 app.config.errorHandler）
 *
 * 为什么需要它：手机端没有 devtools，而 Vue3 的 render 一旦抛错，会把**整棵子树替换成注释节点**
 * → 整页白屏，用户看到的是一片空白 + 零信息，既不知道出了错、也不知道下一步做什么。
 * 2026-09-18 会员页白屏就是这么发生的（根因是 computed 被当函数调用，见 pages/vip/vip.vue 的注释）。
 *
 * 这里做两件事：
 * 1. 所有 Vue 错误都 `console.error` 出来（带 `info`，HBuilderX 控制台能查到是哪一类）
 * 2. **只有 render 阶段的错误**才弹一句人话提示 —— 这类错误的表现就是白屏，必须让用户看到
 *    "渲染出错了 + 下一步做什么"，而不是空白；其它阶段的错误（事件回调、接口失败等）只进控制台，
 *    免得把一次点击失败变成骚扰式弹窗
 *
 * ⚠️ 这不是修 bug 的手段，只是"白屏不再等于零信息"的保险。真正的防线是：
 *   ① tools/check-phone.js 的静态检查（模板绑定、computed 被当函数调用这类必崩写法）
 *   ② 每个页面自己对接口失败/空数据给出可见状态 + 重试入口
 */
import { describeTarget } from './request.js'

/** 同一类错误反复触发（渲染重试）时不要连弹 */
let lastToastAt = 0
/** 提示里带的信息要短：真机上用户是拿这句话去反馈问题的 */
const MAX_REASON = 60

function reason (err) {
	const msg = (err && err.message) || String(err === undefined || err === null ? '' : err)
	const text = String(msg).replace(/\s+/g, ' ').trim()
	if (!text) return '未知错误'
	return text.length > MAX_REASON ? text.slice(0, MAX_REASON) + '…' : text
}

/**
 * 把兜底挂到 Vue 应用上（main.js 里调用）。
 * @param {object} app createSSRApp() 返回的应用实例
 */
export function installRenderGuard (app) {
	if (!app || !app.config) return
	const prev = app.config.errorHandler

	app.config.errorHandler = function (err, vm, info) {
		const where = String(info || '')
		// 真机上查问题只能靠这条日志（HBuilderX 控制台 / 打包后的日志）
		try {
			console.error('[mw] Vue 错误（' + (where || '未知阶段') + '）:', err, '请求打到:', describeTarget())
		} catch (e) {}

		if (where.indexOf('render') >= 0) {
			const now = Date.now()
			if (now - lastToastAt > 3000) {
				lastToastAt = now
				// 延后一拍再弹：不在渲染/打补丁的过程中同步调 UI API
				setTimeout(function () {
					try {
						uni.showToast({
							title: '页面渲染出错，请返回重进一次（原因：' + reason(err) + '）',
							icon: 'none',
							duration: 4000
						})
					} catch (e) {}
				}, 100)
			}
		}

		// 不吞掉原有处理（框架或其它插件若挂过 handler，照常执行）
		if (typeof prev === 'function') {
			try {
				prev(err, vm, info)
			} catch (e) {}
		}
	}
}