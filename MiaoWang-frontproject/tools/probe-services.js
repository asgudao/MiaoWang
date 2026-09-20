/**
 * 真接口可用性探针：登录 → 经网关读宠物列表 → 打印原始信封
 *
 * 为什么需要：服务注册到 Nacos ≠ 接口可用（DB 表、Redis、依赖都可能缺）。
 * 写页面前先确认，避免把演示数据换成真接口之后整页报错。
 *
 * 用法：node probe-pet.js [username] [password]
 * 默认用实测可用的探针账号 probe114904 / MiaoWang123（见 tools/probe-auth.ps1）
 */
const GW = process.env.MIAOWANG_GW || 'http://localhost:7000'
/** 网关按服务名路由，业务路径统一带这个前缀 */
const PREFIX = '/mapleleaf/miaowang/v1'
const USERNAME = process.argv[2] || 'probe114904'
const PASSWORD = process.argv[3] || 'MiaoWang123'

function payloadOf(token) {
  try {
    const part = token.split('.')[1]
    if (!part) return null
    const b64 = part.replace(/-/g, '+').replace(/_/g, '/')
    return JSON.parse(Buffer.from(b64, 'base64').toString('utf8'))
  } catch {
    return null
  }
}

async function call(method, url, { token, username, body } = {}) {
  const header = {}
  if (body) header['Content-Type'] = 'application/json'
  if (token) {
    header.token = token
    header.username = username
  }
  const c = new AbortController()
  const t = setTimeout(() => c.abort(), 10000)
  try {
    const r = await fetch(url, {
      method,
      headers: header,
      body: body ? JSON.stringify(body) : undefined,
      signal: c.signal,
    })
    const text = await r.text()
    return { status: r.status, text }
  } finally {
    clearTimeout(t)
  }
}

;(async () => {
  console.log(`[gw] ${GW}`)
  let token = ''
  let username = USERNAME

  const login = await call('POST', `${GW}/miaowang-user/mapleleaf/miaowang/v1/user/login`, {
    body: { username: USERNAME, password: PASSWORD },
  })
  console.log(`\n[1] 登录 ${USERNAME}\n    HTTP ${login.status}  ${login.text.slice(0, 160)}`)
  try {
    const j = JSON.parse(login.text)
    token = (j.data && j.data.token) || ''
  } catch {
    /* 保持空 token */
  }
  if (!token) {
    console.log('\n[!] 没拿到 token（探针账号可能已被清库）→ 后面的鉴权接口跳过。')
    process.exit(1)
  }

  const claims = payloadOf(token)
  username = (claims && claims.claims && claims.claims.username) || USERNAME
  console.log(`    JWT claims.username = ${username}`)

  const cases = [
    ['GET', `${GW}/miaowang-pet${PREFIX}/pet`, '读我的宠物列表'],
    ['GET', `${GW}/miaowang-vip${PREFIX}/vip/benefit`, '读我的会员权益（含宠物数量上限）'],
    ['GET', `${GW}/miaowang-vip${PREFIX}/vip/internal/benefit/1`, '⚠️ 越权读 uid=1 的权益（内部接口，普通 token 不该能读）'],
    ['GET', `${GW}/miaowang-healthy${PREFIX}/health/symptom/options`, '读症状选项（不需要 pid）'],
    ['GET', `${GW}/miaowang-community${PREFIX}/community/post?pageNum=1&pageSize=5`, '读社区帖子分页'],
    ['GET', `${GW}/miaowang-ai-knowledge${PREFIX}/ai/log/stat`, '读 AI 调用统计'],
  ]
  let i = 2
  for (const [method, url, label] of cases) {
    const r = await call(method, url, { token, username })
    console.log(`\n[${i++}] ${label}\n    HTTP ${r.status}  ${r.text.slice(0, 300)}`)
  }
})()