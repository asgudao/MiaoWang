/**
 * 喵汪 H5 本地通知用的 Service Worker（**故意极简**）
 *
 * 为什么需要它：MDN 明确写着 `new Notification()` 在**大多数手机浏览器**上会抛
 * `TypeError`，要弹系统通知必须注册 Service Worker 后调
 * `ServiceWorkerRegistration.showNotification()`（见 `utils/local-notify.js` 文件头）。
 * 桌面浏览器两条路都行，所以那边先试构造函数、抛了才来这里。
 *
 * ⚠️ **故意不注册 `fetch` 处理器**：
 *   有 fetch 处理器才会让 SW 参与网络请求；没有它，这个 SW **不缓存、不拦截任何请求**，
 *   对 HBuilderX 的 H5 调试零干扰（不会被一个"看不见的缓存"坑到"改了代码不生效"）。
 *
 * ⚠️ 这个文件属于**能力补丁**，不是"能后台到点响"的方案：
 *   `showNotification()` 必须由**页面上下文**调用 → **页面关掉后这条 SW 也没有任何执行机会**
 *   （没有后端推送 / 没有 Push 订阅）。所以 H5 的降级行为仍然是
 *   "打开页面时检查 + 页内提示"，这条写在 `utils/local-notify.js` 的 `scheduleTruthText()`。
 *
 * ⚠️ **一次都没在真机浏览器上跑过**（Agent 无浏览器）；注册失败会在
 *   `utils/local-notify.js` 里变成一条可读的失败原因（`sw-failed`），不会静默。
 */

self.addEventListener('install', function () {
  // 不等旧 SW 退休，立刻接管（这个 SW 不缓存任何东西，跳过等待是安全的）
  self.skipWaiting()
})

self.addEventListener('activate', function (event) {
  event.waitUntil(self.clients.claim())
})

/** 点通知 → 尽量把已经打开的喵汪页面拉到前台；没有就新开一个 */
self.addEventListener('notificationclick', function (event) {
  event.notification.close()
  const target = (event.notification && event.notification.data && event.notification.data.url) || './'
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function (list) {
      for (let i = 0; i < list.length; i++) {
        const c = list[i]
        if (c && typeof c.focus === 'function') return c.focus()
      }
      if (self.clients.openWindow) return self.clients.openWindow(target)
      return null
    })
  )
})