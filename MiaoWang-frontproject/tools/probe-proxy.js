// Why: verifies the exact URL shape + Vite proxy + response envelope the browser uses.
const B = 'http://localhost:5173/api/miaowang-user/mapleleaf/miaowang/v1';
const u = process.env.PROBE_USER || 'probe114904';
const p = process.env.PROBE_PASS || 'MiaoWang123';
(async () => {
  const r1 = await fetch(B + '/user/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: u, password: p }) });
  const j1 = await r1.json();
  console.log('login   HTTP ' + r1.status + '  code=' + j1.code + '  hasToken=' + !!(j1.data && j1.data.token));
  const token = j1.data && j1.data.token;
  if (!token) { console.log('  -> no token, stop'); return; }
  const r2 = await fetch(B + '/user/' + u, { headers: { username: u, token } });
  const j2 = await r2.json();
  console.log('profile HTTP ' + r2.status + '  code=' + j2.code + '  ' + JSON.stringify(j2.data));
  const r3 = await fetch(B + '/user/check-login?username=' + u + '&token=' + token, { headers: { username: u, token } });
  console.log('check   HTTP ' + r3.status + '  ' + JSON.stringify(await r3.json()));
  const r4 = await fetch(B + '/user/captcha');
  const j4 = await r4.json();
  console.log('captcha HTTP ' + r4.status + '  code=' + j4.code + '  img=' + String(j4.data && j4.data.imageBase64).slice(0, 24));
  const r5 = await fetch(B + '/user/captcha', { method: 'HEAD' }).catch(() => null);
  console.log('memorable: proxy prefix /api -> gateway 7000 works =', r1.status === 200);
})();
