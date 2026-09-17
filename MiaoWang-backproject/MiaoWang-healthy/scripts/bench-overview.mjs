/**
 * MiaoWang-healthy overview 接口压测脚本
 *
 * 用来对比「校验缓存 + 查询批量化」改造前后的延迟。测法刻意做得可重复：
 * 每次都新建宠物、预置固定条数的体重记录，测完删掉，保证前后数据量一致。
 *
 * 用法（仓库根目录，需 user/pet/healthy 已启动）：
 *   node MiaoWang-backproject/MiaoWang-healthy/scripts/bench-overview.mjs
 *   HEALTH_URL=http://127.0.0.1:8074 node .../bench-overview.mjs      # 对比新构建
 *
 * 环境变量：SEQ 顺序请求数(100) / CONC 并发数(25) / ROUNDS 并发轮数(4) / SEED 预置记录数(10)
 *          HEALTH_URL 目标地址 / KEEP=1 测完不删宠物（排查用）
 */
const USER = 'http://127.0.0.1:8051';
const PET = 'http://127.0.0.1:8071';
const HEALTH = process.env.HEALTH_URL || 'http://127.0.0.1:8073';
const VIP = process.env.VIP_URL || 'http://127.0.0.1:8052';
const SEQ = Number(process.env.SEQ || 100);
const CONC = Number(process.env.CONC || 25);
const ROUNDS = Number(process.env.ROUNDS || 4);
const SEED = Number(process.env.SEED || 10);
const KEEP = process.env.KEEP === '1';

async function req(method, url, headers, body) {
  const opt = { method, headers: { ...(headers || {}) } };
  if (body) {
    opt.headers['Content-Type'] = 'application/json';
    opt.body = JSON.stringify(body);
  }
  const r = await fetch(url, opt);
  const text = await r.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* ignore */ }
  return { status: r.status, json, text };
}

function pct(sorted, p) {
  if (!sorted.length) return 0;
  const i = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1);
  return sorted[Math.max(0, i)];
}

function stat(durations) {
  const s = [...durations].sort((a, b) => a - b);
  const sum = s.reduce((a, b) => a + b, 0);
  return {
    n: s.length,
    mean: Math.round((sum / s.length) * 100) / 100,
    p50: Math.round(pct(s, 50) * 100) / 100,
    p95: Math.round(pct(s, 95) * 100) / 100,
    p99: Math.round(pct(s, 99) * 100) / 100,
    max: Math.round(s[s.length - 1] * 100) / 100,
  };
}

console.log(`=== overview 压测 @ ${HEALTH} ===`);
const login = await req('POST', `${USER}/mapleleaf/miaowang/v1/user/login`, null,
  { username: 'MapleLeaf1', password: '1234567' });
const token = login.json?.data?.token;
if (!token) { console.log('登录失败，终止'); process.exit(1); }
const hdr = { username: 'MapleLeaf1', token };

// ---- 组件耗时拆解：看清一次 overview 的钱花在哪个跳 ----
async function avg(label, url, headers, times = 20) {
  const ds = [];
  for (let i = 0; i < times; i++) {
    const t = performance.now();
    await req('GET', url, headers);
    ds.push(performance.now() - t);
  }
  const s = stat(ds);
  console.log(`  ${label}: mean=${s.mean}ms  p50=${s.p50}ms`);
  return s.mean;
}
console.log('\n--- 组件耗时（各 20 次）---');
const petHop = await avg('pet  GET /pet（归属校验那一跳）', `${PET}/mapleleaf/miaowang/v1/pet`, hdr);
const vipHop = await avg('vip  /internal/benefit/2（权益那一跳）', `${VIP}/mapleleaf/miaowang/v1/vip/internal/benefit/2`);
console.log(`  两跳合计 ≈ ${Math.round((petHop + vipHop) * 100) / 100}ms（缓存要干掉的就是这部分）`);

const pet = await req('POST', `${PET}/mapleleaf/miaowang/v1/pet`, hdr,
  { pName: '压测猫', species: '猫', ownerCallName: '铲屎官', birth: '2023-05-01', gender: 0, sterilizedState: 1 });
const pid = pet.json?.data?.pid;
if (!pid) { console.log(`建宠物失败：${pet.text.slice(0, 200)}`); process.exit(1); }

for (let i = 0; i < SEED; i++) {
  const r = await req('POST', `${HEALTH}/mapleleaf/miaowang/v1/health/${pid}/weight`, hdr,
    { weightKg: 4 + i / 100, measuredAt: `2026-09-1${i % 10} 12:00:00` });
  if (r.json?.code !== '0') { console.log(`预置记录失败：${r.text.slice(0, 200)}`); break; }
}
console.log(`宠物 pid=${pid}，预置体重记录 ${SEED} 条`);

const url = `${HEALTH}/mapleleaf/miaowang/v1/health/${pid}/overview`;
for (let i = 0; i < 10; i++) await req('GET', url, hdr);

const seqDurations = [];
let seqFail = 0;
for (let i = 0; i < SEQ; i++) {
  const t0 = performance.now();
  const r = await req('GET', url, hdr);
  const dt = performance.now() - t0;
  if (r.json?.code === '0') seqDurations.push(dt); else seqFail++;
}
const s = stat(seqDurations);
console.log(`\n顺序 ${SEQ} 次（失败 ${seqFail}）：mean=${s.mean}ms  p50=${s.p50}ms  p95=${s.p95}ms  p99=${s.p99}ms  max=${s.max}ms`);

let concFail = 0;
const concDurations = [];
const t0 = performance.now();
for (let round = 0; round < ROUNDS; round++) {
  await Promise.all(Array.from({ length: CONC }, async () => {
    const t = performance.now();
    const r = await req('GET', url, hdr);
    concDurations.push(performance.now() - t);
    if (r.json?.code !== '0') concFail++;
  }));
}
const total = performance.now() - t0;
const c = stat(concDurations);
const qps = Math.round((CONC * ROUNDS) / (total / 1000) * 100) / 100;
console.log(`并发 ${ROUNDS}×${CONC}=${CONC * ROUNDS} 次（失败 ${concFail}）：总耗时 ${Math.round(total)}ms  QPS=${qps}  p50=${c.p50}ms  p95=${c.p95}ms  p99=${c.p99}ms  max=${c.max}ms`);

if (!KEEP) {
  const del = await req('DELETE', `${PET}/mapleleaf/miaowang/v1/pet/${pid}`, hdr);
  console.log(`\n清理：宠物 pid=${pid} 已删除（code=${del.json?.code}）`);
} else {
  console.log(`\nKEEP=1，宠物 pid=${pid} 保留`);
}

// ---- 缓存命中率（改造后的版本才有这个内部接口；老版本会 404，忽略即可）----
// 计数是进程启动以来的累计值，所以多跑几轮后看这个比例更有意义
const stats = await req('GET', `${HEALTH}/mapleleaf/miaowang/v1/health/internal/cache-stats`, hdr);
if (stats.json?.data) {
  console.log('\n--- 缓存命中率（进程累计）---');
  for (const [name, part] of Object.entries(stats.json.data)) {
    console.log(`  ${name}: 命中率=${part.hitRate}  L1=${part.l1Hit}  L2=${part.l2Hit}  回源=${part.load}  被拒复核=${part.recheck}  合计=${part.total}`);
  }
} else {
  console.log(`\n--- 缓存命中率：该实例没有此接口（${String(stats.text).slice(0, 60)}）`);
}