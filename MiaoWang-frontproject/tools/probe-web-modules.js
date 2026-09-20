/**
 * Web 改动自检：确认 dev server 真的能编译并送出改过的模块。
 *
 * 为什么需要它：`vue-tsc` 全绿只证明类型对，SFC 的**模板编译**是 Vite 在请求时做的，
 * 模板里写错（比如访问了不存在的绑定）类型检查抓不到，但浏览器会白屏。
 * 这里直接按浏览器的路径请求模块，拿到 500 就是编译不过。
 *
 * 用法：
 *   node tools/probe-web-modules.js                       # 默认查 5173/5175 上的 AI 相关模块
 *   node tools/probe-web-modules.js 5175                  # 只查某个端口
 *   node tools/probe-web-modules.js 5175 src/views/PetsView.vue ...
 */
const DEFAULT_PORTS = ['5173', '5175']
const DEFAULT_MODULES = [
  'src/views/AiChatView.vue',
  'src/stores/ai.ts',
  'src/stores/auth.ts',
  'src/api/ai.ts',
  'src/api/vip.ts',
  'src/api/request.ts',
  'src/utils/ai.ts',
  'src/router/index.ts',
  'src/components/AppNav.vue',
]

const ports = []
const modules = []
for (const a of process.argv.slice(2)) {
  if (/^\d+$/.test(a)) ports.push(a)
  else modules.push(a.replace(/^\/+/, ''))
}
const usePorts = ports.length ? ports : DEFAULT_PORTS
const useModules = modules.length ? modules : DEFAULT_MODULES

const looksLikeJs = (t) =>
  t.includes('export default') ||
  t.includes('export {') ||
  t.includes('export const') ||
  t.includes('export function') ||
  t.includes('import ')

let bad = 0

for (const port of usePorts) {
  const origin = `http://localhost:${port}`
  console.log(`\n=== ${origin} ===`)
  // 1) SPA 外壳（history 模式下深链应该返回同一个 HTML）
  try {
    const r = await fetch(`${origin}/ai`)
    const html = await r.text()
    const ok = r.status === 200 && html.includes('id="app"')
    console.log(`  ${ok ? '✅' : '❌'} GET /ai  HTTP ${r.status}，挂载点 ${html.includes('id="app"') ? '有' : '没有'}`)
    if (!ok) bad += 1
  } catch (e) {
    console.log(`  ❌ GET /ai 连不上：${e.message}`)
    bad += 1
    continue
  }
  // 2) 逐个模块（Vite 按需编译，500 就是模板/语法错）
  for (const m of useModules) {
    try {
      const r = await fetch(`${origin}/${m}`)
      const t = await r.text()
      const ok = r.status === 200 && looksLikeJs(t)
      if (!ok) bad += 1
      console.log(`  ${ok ? '✅' : '❌'} ${m}  HTTP ${r.status}（${t.length} 字节）`)
      if (!ok) console.log(`     ${t.slice(0, 500).replace(/\s+/g, ' ')}`)
    } catch (e) {
      bad += 1
      console.log(`  ❌ ${m}  请求失败：${e.message}`)
    }
  }
  // 3) 代理是否通（前端所有请求都走它）
  try {
    const r = await fetch(`${origin}/api/miaowang-user/mapleleaf/miaowang/v1/user/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'probe114904', password: 'MiaoWang123' }),
    })
    const j = await r.json().catch(() => null)
    const ok = r.status === 200 && j && j.code === '0'
    if (!ok) bad += 1
    console.log(`  ${ok ? '✅' : '❌'} 代理 /api → 网关  登录 HTTP ${r.status} code=${j ? j.code : '非 JSON'}`)
  } catch (e) {
    bad += 1
    console.log(`  ❌ 代理 /api 请求失败：${e.message}`)
  }
}

console.log(bad === 0 ? '\n全部通过 ✅' : `\n有 ${bad} 项没过 ❌`)
if (bad > 0) process.exitCode = 1