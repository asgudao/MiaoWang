import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // ⚠️ 2026-09-22 追加（**单独记账的一处配置改动**）：
    // 手机端「我的 → 社区角色」那一行要跳到 `http://<开发机局域网IP>:5173/user/<uid>`
    // （App / 模拟器里 localhost 是设备自己，不能用作开发机地址）。
    // Vite 默认只绑 localhost：实测 `netstat` 当时只有 `[::1]:5173 LISTENING`，
    // 宿主机连自己的局域网 IP 都连不上，模拟器里 `curl http://192.168.100.45:5173/` 直接 exit 7（连接被拒）。
    // `host: true` = 监听所有地址（0.0.0.0 + ::）→ 局域网可达。
    // 代价：**dev server 暴露在同网段**（谁都能打开这个开发页面），且 Windows 防火墙可能需要放行 5173 入站
    //（放行要管理员权限，本次没做）；只影响 dev server，不影响 `vite build` 的产物。
    host: true,
    proxy: {
      // 后端没有配 CORS，浏览器直连必被拦 → 统一走 dev 代理到网关
      // 请求形如 /api/miaowang-user/mapleleaf/miaowang/v1/user/login
      '/api': {
        // 后端已部署到 CentOS VM（192.168.137.128），dev 代理指向 VM 里的网关；
        // 雷电模拟器 / 宿主浏览器都能直达 192.168.137.128，所以不再需要本地转发工具
        target: 'http://192.168.137.128:7000',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
})
