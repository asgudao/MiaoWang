// Does the gateway listen on the LAN interface (not just 127.0.0.1)? The phone can only reach a bound LAN IP.
const urls = [
  'http://192.168.225.183:7000/miaowang-user/mapleleaf/miaowang/v1/user/captcha',
  'http://192.168.100.45:7000/miaowang-user/mapleleaf/miaowang/v1/user/captcha',
  'http://127.0.0.1:7000/miaowang-user/mapleleaf/miaowang/v1/user/captcha'
];
(async () => {
  for (const u of urls) {
    try {
      const c = new AbortController(); const t = setTimeout(() => c.abort(), 5000);
      const r = await fetch(u, { signal: c.signal }); clearTimeout(t);
      const j = await r.json();
      console.log('OK   HTTP ' + r.status + '  code=' + j.code + '  ' + u);
    } catch (e) { console.log('FAIL ' + e.name + ' ' + e.message + '  ' + u); }
  }
})();
