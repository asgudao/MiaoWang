import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'

import './assets/styles/main.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

// 启动时恢复登录态。放在 mount 之前，避免首屏先闪一下"未登录"再跳回来。
// 显式传入 pinia 实例，不依赖"当前激活的 pinia"这种隐式状态。
void useAuthStore(pinia).restore()

app.mount('#app')
