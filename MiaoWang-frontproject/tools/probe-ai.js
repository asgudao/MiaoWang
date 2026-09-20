/**
 * AI / RAG 全链路探针（经 Vite 代理，与浏览器同路径）
 *
 * ⚠️ 额度很贵：免费档 RAG **5 次/月**，每跑一次真的成功问答就烧 1 次。
 *    所以默认只跑 1 次非流式问答（顺带验证额度扣减），流式要显式开：
 *      node tools/probe-ai.js                 # 1 次额度
 *      PROBE_STREAM=1 node tools/probe-ai.js  # 2 次额度
 *      PROBE_INDEX=1  node tools/probe-ai.js  # 额外做一次灌库（很重，慎用）
 *
 * 自带清理：只删自己这次新建的会话（先记下跑之前的会话 id）。
 */
const ORIGIN = process.env.PROBE_ORIGIN || 'http://localhost:5173'
const PREFIX = '/api'
const AIK = 'miaowang-ai-knowledge'
const VIP = 'miaowang-vip'
const USER = 'miaowang-user'
const V1 = '/mapleleaf/miaowang/v1'
const ACCOUNT = {
  username: process.env.PROBE_USER || 'probe114904',
  password: process.env.PROBE_PASS || 'MiaoWang123',
}

const QUESTION = '三个月大的猫该打什么疫苗？'
const log = (t) => console.log('\n' + t)
const short = (s, n = 200) => {
  const t = typeof s === 'string' ? s : JSON.stringify(s)
  return t && t.length > n ? t.slice(0, n) + '…' : t
}

let token = ''
let username = ''

async function call(method, service, path, { body, headers } = {}) {
  const h = Object.assign({}, headers)
  if (body !== undefined) h['Content-Type'] = 'application/json'
  if (token) {
    h.token = token
    h.username = username
  }
  const res = await fetch(`${ORIGIN}${PREFIX}/${service}${V1}${path}`, {
    method,
    headers: h,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await res.text()
  let json = null
  try {
    json = text ? JSON.parse(text) : null
  } catch {
    /* 非 JSON 保留原文 */
  }
  return { status: res.status, text, json, headers: res.headers }
}

function show(label, r, max = 200) {
  const j = r.json
  const code = j ? (j.code !== undefined ? 'code=' + j.code : 'state=' + j.state) : '非 JSON'
  const msg = j ? j.message || '' : ''
  console.log(`  ${label}  HTTP ${r.status}  ${code}  ${short(msg, 80)}`)
  if (j && j.code !== '0' && j.code !== undefined) console.log(`     原文: ${short(r.text, max)}`)
  return j
}

async function ragRemaining() {
  const r = await call('GET', VIP, '/vip/benefit')
  const d = r.json && r.json.data
  return d ? { remaining: d.ragRemaining, used: d.ragUsed, limit: d.ragLimit, period: d.ragPeriodDesc } : null
}

;(async () => {
  log('[0] 经代理登录')
  const login = await call('POST', USER, '/user/login', {
    body: { username: ACCOUNT.username, password: ACCOUNT.password },
  })
  const lj = show('POST /user/login', login)
  token = lj && lj.data && lj.data.token
  username = ACCOUNT.username
  if (!token) {
    console.log('  ✗ 没拿到 token，后面的用例没法跑')
    process.exit(1)
  }

  log('[1] 跑之前的额度与会话快照（用于后面比对，也用来只删自己新建的会话）')
  const before = await ragRemaining()
  console.log('  额度：' + JSON.stringify(before))
  const listBefore = await call('GET', AIK, '/ai/conversation/list')
  const oldIds = new Set(
    ((listBefore.json && listBefore.json.data) || []).map((c) => String(c.conversationId ?? c.id)),
  )
  show('GET /ai/conversation/list', listBefore)
  console.log(`  已有会话 ${oldIds.size} 个：${[...oldIds].join(', ') || '（无）'}`)

  log('[2] 非流式问答（消耗 1 次额度）')
  const ask = await call('POST', AIK, '/ai/rag/ask', { body: { question: QUESTION } })
  const aj = show('POST /ai/rag/ask', ask, 400)
  let conversationId = null
  if (aj && aj.code === '0' && aj.data) {
    conversationId = aj.data.conversationId
    console.log(`  回答（前 160 字）：${short(aj.data.answer, 160)}`)
    console.log(`  hasKnowledge=${aj.data.hasKnowledge}  messageId=${aj.data.messageId}  conversationId=${conversationId}`)
    console.log(`  来源条数：${(aj.data.sources || []).length}  首条：${short((aj.data.sources || [])[0], 160)}`)
  }

  log('[3] 额度是否真的扣了（免费档按月 5 次）')
  const after = await ragRemaining()
  console.log('  额度：' + JSON.stringify(after))
  if (before && after && aj && aj.code === '0') {
    const delta = (before.remaining ?? 0) - (after.remaining ?? 0)
    console.log(`  剩余 ${before.remaining} → ${after.remaining}（差 ${delta}）${delta === 1 ? ' ✅ 扣减正确' : ' ⚠️ 与预期 1 不符'}`)
  }

  log('[4] 会话与消息是否落库')
  const list = await call('GET', AIK, '/ai/conversation/list')
  const rows = (list.json && list.json.data) || []
  const mine = rows.filter((c) => !oldIds.has(String(c.conversationId ?? c.id)))
  console.log(`  会话列表 ${rows.length} 条，其中本次新增 ${mine.length} 条`)
  if (mine.length) console.log('  新增会话：' + short(mine[0], 300))
  if (conversationId) {
    const msgs = await call('GET', AIK, `/ai/conversation/${conversationId}/message`)
    const mj = show(`GET /ai/conversation/${conversationId}/message`, msgs)
    const mrows = (mj && mj.data) || []
    console.log(`  消息 ${mrows.length} 条：`)
    for (const m of mrows) {
      console.log(`    [${m.role || m.messageRole}] ${short(m.content, 90)}`)
    }
  }

  if (process.env.PROBE_STREAM === '1') {
    log('[5] 流式问答（再消耗 1 次额度；POST + SSE）')
    const res = await fetch(`${ORIGIN}${PREFIX}/${AIK}${V1}/ai/rag/ask/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', username, token },
      body: JSON.stringify({ question: '猫可以喝牛奶吗？' }),
    })
    console.log(`  HTTP ${res.status}  content-type=${res.headers.get('content-type')}`)
    if (res.ok && res.body) {
      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let sources = 0
      let chunks = 0
      let answer = ''
      let streamConversationId = null
      const deadline = Date.now() + 45000
      while (Date.now() < deadline) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        let sep
        while ((sep = buffer.indexOf('\n\n')) >= 0) {
          const block = buffer.slice(0, sep)
          buffer = buffer.slice(sep + 2)
          let event = 'message'
          const dataLines = []
          for (const line of block.split('\n')) {
            if (line.startsWith('event:')) event = line.slice(6).trim()
            else if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^ /, ''))
          }
          if (!dataLines.length) continue
          const payload = dataLines.join('\n')
          if (event === 'sources') {
            try {
              sources = JSON.parse(payload).length
            } catch {
              /* 忽略解析失败，下面只看增量 */
            }
          } else if (event === 'message') {
            chunks += 1
            answer += payload
          } else if (event === 'conversation') {
            try {
              streamConversationId = JSON.parse(payload).conversationId
            } catch {
              /* 有的实现直接用裸 id */
              streamConversationId = payload
            }
          } else {
            console.log(`  （其它事件 ${event}: ${short(payload, 80)}）`)
          }
        }
        if (chunks > 0 && buffer.indexOf('\n\n') < 0 && answer.length > 400) break
      }
      console.log(`  收到 ${chunks} 个 message 块、${sources} 条来源；正文前 120 字：${short(answer, 120)}`)
      console.log(`  流式会话 id：${streamConversationId || '（事件里没给，看会话列表）'}`)
      if (!streamConversationId) conversationId = conversationId
    } else {
      console.log('  ✗ 流式请求失败：' + short(await res.text(), 300))
    }
  } else {
    log('[5] 跳过流式（要跑就加 PROBE_STREAM=1）')
  }

  if (process.env.PROBE_INDEX === '1') {
    log('[6] 重建向量索引（很重：会重灌 Milvus）')
    show('POST /ai/rag/index/build', await call('POST', AIK, '/ai/rag/index/build'), 400)
  }

  log('[7] 清理：只删本次新建的会话')
  const end = await call('GET', AIK, '/ai/conversation/list')
  const endRows = (end.json && end.json.data) || []
  const created = endRows.filter((c) => !oldIds.has(String(c.conversationId ?? c.id)))
  for (const c of created) {
    const id = c.conversationId ?? c.id
    const r = await call('DELETE', AIK, `/ai/conversation/${id}`)
    console.log(`  DELETE /ai/conversation/${id} → ${r.status} ${short(r.json ? r.json.code : r.text, 40)}`)
  }
  const final = await call('GET', AIK, '/ai/conversation/list')
  const finalRows = (final.json && final.json.data) || []
  console.log(`  清理后会话 ${finalRows.length} 个（跑之前 ${oldIds.size} 个）`)
  const finalQuota = await ragRemaining()
  console.log('  最终额度：' + JSON.stringify(finalQuota))
})()