import App from './App'

// #ifndef VUE3
import Vue from 'vue'
import './uni.promisify.adaptor'
Vue.config.productionTip = false
App.mpType = 'app'
const app = new Vue({
	...App
})
app.$mount()
// #endif

// #ifdef VUE3
import {
	createSSRApp
} from 'vue'
import { installRenderGuard } from './utils/render-guard.js'
export function createApp() {
	const app = createSSRApp(App)
	// 渲染错误兜底：手机端没 devtools，Vue3 render 抛错会整页白屏且零信息
	// → 至少把"渲染出错了"和下一步提示出来（详见 utils/render-guard.js）
	installRenderGuard(app)
	return {
		app
	}
}
// #endif
