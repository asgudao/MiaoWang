/**
 * 查 Nacos 里当前注册了哪些服务（决定前端能不能接真接口）
 *
 * 为什么需要：网关是按 Nacos 服务名自动路由的，没注册的服务访问必 404/503，
 * 前端页面就只能继续用演示数据。写页面前先跑这个，别白做。
 *
 * 用法：node probe-nacos.js
 * 覆盖：NACOS_ADDR（默认 192.168.137.128:8848）、NACOS_USER / NACOS_PASSWORD（默认 nacos/nacos）
 */
const ADDR = process.env.NACOS_ADDR || '192.168.137.128:8848'
const USER = process.env.NACOS_USER || 'nacos'
const PASSWORD = process.env.NACOS_PASSWORD || 'nacos'

/** 前端用到的 8 个服务名（见 miaowang-api-contract） */
const EXPECT = [
  'miaowang-user',
  'miaowang-pet',
  'miaowang-community',
  'miaowang-knowledge',
  'miaowang-vip',
  'miaowang-ai-pet',
  'miaowang-ai-knowledge',
  'miaowang-healthy',
]

async function getJson(url, init, timeoutMs = 8000) {
  const c = new AbortController()
  const t = setTimeout(() => c.abort(), timeoutMs)
  try {
    const r = await fetch(url, Object.assign({ signal: c.signal }, init || {}))
    const text = await r.text()
    try {
      return { status: r.status, json: JSON.parse(text) }
    } catch {
      return { status: r.status, text }
    }
  } finally {
    clearTimeout(t)
  }
}

;(async () => {
  let token = ''
  try {
    const body = new URLSearchParams({ username: USER, password: PASSWORD })
    const r = await getJson(`http://${ADDR}/nacos/v1/auth/login`, { method: 'POST', body })
    token = (r.json && r.json.accessToken) || ''
    console.log(token ? '[nacos] 登录成功，已拿到 accessToken' : `[nacos] 登录失败：${JSON.stringify(r.json || r.text)}`)
  } catch (e) {
    console.log(`[nacos] 连不上 ${ADDR}：${e.name} ${e.message}`)
    process.exit(1)
  }

  let list = []
  for (const url of [
    `http://${ADDR}/nacos/v1/ns/service/list?pageNo=1&pageSize=200&accessToken=${token}`,
    `http://${ADDR}/nacos/v2/ns/service/list?pageNo=1&pageSize=200&accessToken=${token}`,
  ]) {
    try {
      const r = await getJson(url)
      const doms = (r.json && (r.json.doms || (r.json.data && r.json.data.doms))) || null
      if (doms) {
        list = doms
        break
      }
    } catch {
      /* 换下一个版本端点 */
    }
  }

  if (!list.length) {
    console.log('[nacos] 拿不到服务列表（v1/v2 都试过了），可能 namespace 不是 public 或需要额外权限')
    process.exit(1)
  }

  list.sort()
  console.log(`\n[nacos] 已注册 ${list.length} 个服务：`)
  for (const s of list) console.log('   ' + s)

  console.log('\n[前端要用的 8 个服务]')
  for (const name of EXPECT) {
    console.log(`   ${list.includes(name) ? '✅ 已注册' : ' 未注册'}  ${name}`)
  }
  const missing = EXPECT.filter((n) => !list.includes(n))
  console.log(
    missing.length
      ? `\n结论：${missing.length} 个服务还没起来，对应页面只能继续用演示数据。`
      : '\n结论：8 个服务全部就绪，可以接真接口了。',
  )
})()