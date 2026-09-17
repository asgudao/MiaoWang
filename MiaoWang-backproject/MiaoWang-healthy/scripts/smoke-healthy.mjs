/**
 * MiaoWang-healthy 联调自检脚本
 *
 * 用法（在仓库根目录执行，需已启动 user/pet/healthy）：
 *   node MiaoWang-backproject/MiaoWang-healthy/scripts/smoke-healthy.mjs
 *       基础自检：身份、宠物归属、跨服务依赖不可用时的表现（VIP 没起也能跑）
 *   node MiaoWang-backproject/MiaoWang-healthy/scripts/smoke-healthy.mjs full
 *       VIP 启动后再跑：权益放行 + 每类记录额度边界（B000403）+ 导出权益
 *
 * 环境变量：
 *   HEALTH_URL   指定 healthy 地址，默认 http://127.0.0.1:8073
 *                （要验证未重启的旧实例 vs 新构建时很有用，比如 http://127.0.0.1:8074）
 *
 * 约定：直连各服务端口、绕过网关，便于定位问题；账号用 MapleLeaf1（uid=2），
 *      测试宠物自建自删，不污染主账号数据。
 */
const USER = 'http://127.0.0.1:8051';
const PET = 'http://127.0.0.1:8071';
const HEALTH = process.env.HEALTH_URL || 'http://127.0.0.1:8073';
const FULL = process.argv.includes('full');

const results = [];

async function call(label, method, url, headers, body) {
  const opt = { method, headers: { ...(headers || {}) } };
  if (body) {
    opt.headers['Content-Type'] = 'application/json';
    opt.body = JSON.stringify(body);
  }
  try {
    const r = await fetch(url, opt);
    const text = await r.text();
    let j = null;
    try { j = JSON.parse(text); } catch { /* 非 JSON */ }
    return { status: r.status, json: j, text };
  } catch (e) {
    return { status: 0, json: null, text: `FETCH ERROR: ${e.message}` };
  }
}

function check(label, got, want) {
  const ok = String(got) === String(want);
  results.push({ label, ok, got, want });
  console.log(`  ${ok ? '✅' : '❌'} ${label}\n       期望 ${want} / 实际 ${got}`);
}

function brief(r) {
  if (!r.json) return `HTTP ${r.status} ${r.text.slice(0, 120)}`;
  const d = r.json.data === null || r.json.data === undefined ? 'null' : JSON.stringify(r.json.data);
  return `code=${r.json.code} message=${r.json.message} data=${d.slice(0, 200)}`;
}

console.log('=== 0. 登录 ===');
const login = await call('login', 'POST', `${USER}/mapleleaf/miaowang/v1/user/login`, null,
  { username: 'MapleLeaf1', password: '1234567' });
// 注意：登录返回的 data 是对象 { token }，不是裸字符串
const token = login.json?.data?.token;
console.log(`  ${brief(login)}`);
console.log(`  token=${String(token).slice(0, 20)}... len=${String(token).length}`);
if (!token) { console.log('登录失败，终止'); process.exit(1); }
const hdr = { username: 'MapleLeaf1', token };

console.log('\n=== 1. 身份与归属（不依赖 VIP） ===');
const noHeader = await call('noheader', 'GET', `${HEALTH}/mapleleaf/miaowang/v1/health/1/weight`);
console.log(`  未登录访问：${brief(noHeader)}`);
check('未登录应报"未登录"而不是"宠物服务不可用"', noHeader.json?.message, '用户未登录或登录已过期');

const petList = await call('pets', 'GET', `${PET}/mapleleaf/miaowang/v1/pet`, hdr);
console.log(`  MapleLeaf1 现有宠物：${brief(petList)}`);
check('带登录头能拿到自己的宠物列表（请求头透传通）', petList.json?.code, '0');

const createPet = await call('createPet', 'POST', `${PET}/mapleleaf/miaowang/v1/pet`, hdr,
  { pName: '自检猫', species: '猫', ownerCallName: '铲屎官', birth: '2023-05-01', gender: 0, sterilizedState: 1 });
const pid = createPet.json?.data?.pid;
console.log(`  建测试宠物：${brief(createPet)}`);
check('建测试宠物成功', createPet.json?.code, '0');

const ownList = await call('own', 'GET', `${HEALTH}/mapleleaf/miaowang/v1/health/${pid}/weight`, hdr);
console.log(`  自己宠物体重列表：${brief(ownList)}`);
check('自己宠物可读（空列表）', ownList.json?.code, '0');

const otherList = await call('other', 'GET', `${HEALTH}/mapleleaf/miaowang/v1/health/1/weight`, hdr);
console.log(`  别人的宠物 pid=1：${brief(otherList)}`);
check('别人宠物应被拒（B000400）', otherList.json?.code, 'B000400');

const deletedList = await call('deleted', 'GET', `${HEALTH}/mapleleaf/miaowang/v1/health/3/weight`, hdr);
console.log(`  已删除宠物 pid=3：${brief(deletedList)}`);
check('已删除宠物应被拒（B000400）', deletedList.json?.code, 'B000400');

console.log('\n=== 2. 跨服务依赖不可用时的表现 ===');
const knowledge = await call('knowledge', 'GET', `${HEALTH}/mapleleaf/miaowang/v1/health/symptom/options?species=1`, hdr);
console.log(`  症状选项（知识库未启动）：${brief(knowledge)}`);
check('知识库不可用应报 B000408（不是笼统的系统错误）', knowledge.json?.code, 'B000408');

const overview = await call('overview', 'GET', `${HEALTH}/mapleleaf/miaowang/v1/health/${pid}/overview`, hdr);
console.log(`  概览：${brief(overview)}`);
const write = await call('write', 'POST', `${HEALTH}/mapleleaf/miaowang/v1/health/${pid}/weight`, hdr,
  { weightKg: 4.2, measuredAt: '2026-09-17 10:00:00' });
console.log(`  写体重：${brief(write)}`);
if (!FULL) {
  check('VIP 未启动时概览应报 B000405', overview.json?.code, 'B000405');
  check('VIP 未启动时写操作应被挡住（失败不放行）', write.json?.code, 'B000405');
  const after = await call('after', 'GET', `${HEALTH}/mapleleaf/miaowang/v1/health/${pid}/weight`, hdr);
  check('被挡住的写入没有落库（列表仍为空）', (after.json?.data || []).length, '0');
}

if (FULL) {
  console.log('\n=== 3. VIP 已启动后的正例与额度边界 ===');
  console.log(`  概览：${brief(overview)}`);
  check('概览放行', overview.json?.code, '0');
  // 第二次调概览时归属缓存大概率已经命中，这里顺带验证"缓存里的宠物列表反序列化没丢字段"
  console.log(`  概览 petName=${overview.json?.data?.petName} species=${overview.json?.data?.species}`);
  check('归属缓存命中后仍能拿到宠物名（JSON 往返没丢字段）',
    overview.json?.data?.petName !== null && overview.json?.data?.petName !== undefined, true);
  console.log(`  写体重：${brief(write)}`);
  check('写体重放行', write.json?.code, '0');
  const limit = overview.json?.data?.recordLimit;
  console.log(`  当前档位每类记录上限 = ${limit}`);
  let blockedAt = null;
  for (let i = 2; i <= (limit > 0 ? limit + 2 : 5); i++) {
    const r = await call(`w${i}`, 'POST', `${HEALTH}/mapleleaf/miaowang/v1/health/${pid}/weight`, hdr,
      { weightKg: 4 + i / 100, measuredAt: `2026-09-17 1${String(i % 10)}:00:00` });
    if (r.json?.code === 'B000403') { blockedAt = i; break; }
  }
  console.log(`  第 ${blockedAt} 条被拒（B000403）`);
  check('达到上限后应报 B000403', blockedAt, limit > 0 ? String(limit + 1) : 'undefined');
  const exportR = await call('export', 'GET', `${HEALTH}/mapleleaf/miaowang/v1/health/${pid}/export`, hdr);
  console.log(`  导出：${brief(exportR)}`);
  console.log('  （免费档期望 B000404，付费档期望 code=0）');
  const timeline = await call('timeline', 'GET',
    `${HEALTH}/mapleleaf/miaowang/v1/health/${pid}/timeline?types=weight&limit=50`, hdr);
  console.log(`  时间线：${brief(timeline)}`);
  check('时间线能查到刚写入的体重记录', (timeline.json?.data || []).length > 0, true);
  const curve = await call('curve', 'GET',
    `${HEALTH}/mapleleaf/miaowang/v1/health/${pid}/weight/curve?months=6`, hdr);
  console.log(`  体重曲线：${brief(curve)}`);
  check('体重曲线能返回聚合点', (curve.json?.data || []).length > 0, true);
}

console.log('\n=== 4. 前端真实路径（过网关） ===');
const GW = process.env.GATEWAY_URL || 'http://127.0.0.1:7000';
// 网关是 discovery locator 自动路由，URL 必须带服务名前缀
const gwPath = `${GW}/miaowang-healthy/mapleleaf/miaowang/v1/health/symptom/options?species=1`;
const gwNoAuth = await call('gw-noauth', 'GET', gwPath);
if (gwNoAuth.status === 0) {
  console.log(`  ⚠️ 网关不可达（${GW}），跳过网关链路检查：${gwNoAuth.text}`);
} else {
  console.log(`  不带 token 过网关：${brief(gwNoAuth)}`);
  check('网关应拦下未登录请求（state=-1 或 401）',
    gwNoAuth.status === 401 || String(gwNoAuth.json?.state) === '-1', true);
  const gwAuth = await call('gw-auth', 'GET', gwPath, hdr);
  console.log(`  带 username+token 过网关：${brief(gwAuth)}`);
  check('网关应把请求路由到 healthy（响应是业务 Result，含 code 字段）',
    gwAuth.json?.code !== undefined, true);
}

console.log('\n=== 9. 清理 ===');
const del = await call('deletePet', 'DELETE', `${PET}/mapleleaf/miaowang/v1/pet/${pid}`, hdr);
console.log(`  删除测试宠物：${brief(del)}`);

const failed = results.filter((r) => !r.ok);
console.log(`\n=== 结果：${results.length - failed.length}/${results.length} 通过 ===`);
failed.forEach((r) => console.log(`  ❌ ${r.label}（期望 ${r.want}，实际 ${r.got}）`));
process.exit(failed.length ? 1 : 0);