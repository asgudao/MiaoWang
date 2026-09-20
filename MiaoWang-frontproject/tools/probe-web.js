const BASE = 'http://localhost:5173';
const urls = ['/', '/login', '/register', '/mine', '/pets',
  '/src/views/LoginView.vue', '/src/views/RegisterView.vue', '/src/views/MineView.vue',
  '/src/views/HomeView.vue', '/src/stores/auth.ts', '/src/api/user.ts', '/src/api/request.ts',
  '/src/utils/auth-error.ts', '/src/components/AppNav.vue', '/src/main.ts', '/src/router/index.ts',
  '/src/assets/styles/main.css'];
(async () => {
  let bad = 0;
  for (const u of urls) {
    try {
      const r = await fetch(BASE + u); const t = await r.text();
      const err = /Internal Server Error|Failed to resolve import|Transform failed|Pre-transform error|\[plugin:/i.test(t);
      if (r.status !== 200 || err) { bad++; console.log('⚠  ' + r.status + ' ' + u); }
    } catch (e) { bad++; console.log('ERR ' + u + ' ' + e.message); }
  }
  console.log(bad ? '异常 ' + bad + ' 个' : '全部 ' + urls.length + ' 个地址 200 且无编译错误');
})();
