/**
 * 宠物档案 Web 全链路探针
 *
 * 和浏览器完全同路径：Vite devServer(/api 代理) → 网关(7000) → miaowang-pet，
 * 用来证明「页面里那几次请求」真的能通，而不是只测了网关直连。
 *
 * 自带清理：只动自己建的探针宠物，跑完删干净。
 *   用法：node tools/probe-pet-web.js
 */
const ORIGIN = 'http://localhost:5173'
const PREFIX = '/api'
const USER = 'miaowang-user'
const PET = 'miaowang-pet'
const V1 = '/mapleleaf/miaowang/v1'
const ACCOUNT = { username: 'probe114904', password: 'MiaoWang123' }

const log = (t) => console.log('\n' + t)
const short = (s, n = 220) => (typeof s === 'string' && s.length > n ? s.slice(0, n) + '…' : s)

async function call(method, service, path, { body, token, username } = {}) {
  const headers = {}
  if (body) headers['Content-Type'] = 'application/json'
  if (token) {
    headers.token = token
    headers.username = username
  }
  const res = await fetch(ORIGIN + PREFIX + '/' + service + V1 + path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  let json = null
  try {
    json = text ? JSON.parse(text) : null
  } catch {
    /* 非 JSON（比如 HTML）保留原文 */
  }
  return { status: res.status, text, json }
}

function show(label, r) {
  const j = r.json
  const code = j ? (j.code !== undefined ? 'code=' + j.code : 'state=' + j.state) : '非 JSON'
  console.log(`  ${label}  HTTP ${r.status}  ${code}  ${short(j ? j.message : r.text) || ''}`)
  return j
}

;(async () => {
  log('[0] devServer 是否在服务 SPA（/pets 深链由 history 回退接管）')
  const shell = await fetch(ORIGIN + '/pets')
  console.log(`  GET /pets  HTTP ${shell.status}  ${shell.headers.get('content-type')}`)

  log('[1] 经代理登录（页面走的就是这条）')
  const login = await call('POST', USER, '/user/login', {
    body: { username: ACCOUNT.username, password: ACCOUNT.password },
  })
  const lj = show('POST /user/login', login)
  const token = lj && lj.data && lj.data.token
  if (!token) {
    console.log('  ✗ 拿不到 token，后面的用例没法跑')
    process.exit(1)
  }

  log('[2] 经代理读我的宠物')
  const list0 = show('GET /pet', await call('GET', PET, '/pet', { token, username: ACCOUNT.username }))

  log('[3] 新建 → 改名 → 再读（页面上「添加宠物 / 编辑」对应的两次写）')
  const created = await call('POST', PET, '/pet', {
    token,
    username: ACCOUNT.username,
    body: {
      pName: '探针网页端',
      species: '狗',
      specificSpecies: '柯基',
      ownerCallName: '铲屎的',
      birth: '2023-03-08',
      gender: 1,
      sterilizedState: 0,
    },
  })
  const cj = show('POST /pet', created)
  const pid = cj && cj.data && cj.data.pid
  if (!pid) {
    console.log('  ✗ 没拿到 pid，跳过后续并清理')
  } else {
    console.log('  新建返回体: ' + JSON.stringify(cj.data))
    show(
      'PUT /pet/' + pid,
      await call('PUT', PET, '/pet/' + pid, {
        token,
        username: ACCOUNT.username,
        body: { pName: '探针网页端改名', species: '狗' },
      }),
    )
    const list1 = await call('GET', PET, '/pet', { token, username: ACCOUNT.username })
    const rows = (list1.json && list1.json.data) || []
    const mine = rows.find((p) => String(p.pid) === String(pid))
    console.log(
      `  改名后读回: ${rows.length} 只，其中探针那只 = ${mine ? JSON.stringify(mine) : '没找到 ✗'}`,
    )
  }

  log('[4] 错误分支：大类不合法 / 名字为空 / 越权改别人的')
  show(
    'POST /pet 大类=CAT（英文枚举名，应失败）',
    await call('POST', PET, '/pet', {
      token,
      username: ACCOUNT.username,
      body: { pName: '探针英文大类', species: 'CAT' },
    }),
  )
  show(
    'POST /pet 不传 species',
    await call('POST', PET, '/pet', { token, username: ACCOUNT.username, body: { pName: '探针无大类' } }),
  )
  show(
    'PUT /pet/999999（别人的/不存在）',
    await call('PUT', PET, '/pet/999999', {
      token,
      username: ACCOUNT.username,
      body: { pName: '越权', species: '猫' },
    }),
  )

  log('[5] 清理探针数据')
  const list2 = await call('GET', PET, '/pet', { token, username: ACCOUNT.username })
  const rows = (list2.json && list2.json.data) || []
  const junk = rows.filter((p) => p.pName === null || String(p.pName).startsWith('探针'))
  for (const p of junk) {
    const r = await call('DELETE', PET, '/pet/' + p.pid, { token, username: ACCOUNT.username })
    console.log(`  DELETE pid=${p.pid} (${p.pName}) → ${r.status} ${short(r.json ? r.json.code : r.text)}`)
  }
  const left = await call('GET', PET, '/pet', { token, username: ACCOUNT.username })
  const remain = (left.json && left.json.data) || []
  console.log(`  清理后剩余 ${remain.length} 只${remain.length ? ' → ' + remain.map((p) => p.pid + ':' + p.pName).join(', ') : ' ✅'}`)
  const baseline = (list0.json && list0.json.data) || []
  if (remain.length !== baseline.length) {
    console.log(`  ⚠️ 起始 ${baseline.length} 只、收尾 ${remain.length} 只，没回到原状`)
  }

  /* 上限专项：默认不跑（会真的把宠物建到上限）。要跑就 PROBE_LIMIT=1 node tools/probe-pet-web.js
     权益接口说免费档 2 只，那就该在第 3 次 POST 时回 B000800 —— 用来判断上限有没有真的生效。 */
  if (process.env.PROBE_LIMIT !== '1') {
    console.log('\n（跳过数量上限专项，加 PROBE_LIMIT=1 可跑）')
    return
  }

  log('[6] 数量上限专项：按「猫、狗、猫、狗、猫」连建 5 只，看第几次被拦')
  /* 为什么要交替建：源码里这个校验刚从「每个物种各算一份」改成「按总数」，
     只建 猫/狗/猫 的话，两种口径都不会拦 → 分不清"旧包"和"真没生效"。
     第 3 只被拦 = 新口径生效；第 5 只被拦 = 跑的还是旧的按物种口径；都不拦 = 上限没生效。 */
  const createdIds = []
  let blockedAt = 0
  let blockedCode = ''
  for (let i = 1; i <= 5; i++) {
    const species = i % 2 === 1 ? '猫' : '狗'
    const name = '探针上限' + i + species
    const r = await call('POST', PET, '/pet', {
      token,
      username: ACCOUNT.username,
      body: { pName: name, species: species },
    })
    const j = show(`POST /pet 第 ${i} 只（${name}）`, r)
    if (j && j.code === '0' && j.data) createdIds.push(j.data.pid)
    else {
      blockedAt = i
      blockedCode = j ? j.code : '?'
      break
    }
  }
  const now = await call('GET', PET, '/pet', { token, username: ACCOUNT.username })
  const nowRows = (now.json && now.json.data) || []
  let verdict
  if (blockedAt === 3) verdict = '第 3 只被拦 → **总数上限（2 只）已生效** ✅'
  else if (blockedAt === 5) verdict = '第 5 只才被拦 → 跑的还是「按物种各 2 只」的**旧口径**，服务没重新部署'
  else if (blockedAt === 0) verdict = '5 只全建成 → **上限没有生效**'
  else verdict = `第 ${blockedAt} 只被拦（${blockedCode}），与两种口径都不完全吻合，需要单独看日志`
  console.log(`  结论：建成 ${createdIds.length} 只，当前 ${nowRows.length} 只；${verdict}`)
  log('[7] 清理上限专项')
  for (const id of createdIds) {
    const r = await call('DELETE', PET, '/pet/' + id, { token, username: ACCOUNT.username })
    console.log(`  DELETE pid=${id} → ${r.status} ${short(r.json ? r.json.code : r.text)}`)
  }
  const end = await call('GET', PET, '/pet', { token, username: ACCOUNT.username })
  const endRows = (end.json && end.json.data) || []
  console.log(`  清理后剩余 ${endRows.length} 只${endRows.length ? ' → ' + endRows.map((p) => p.pid + ':' + p.pName).join(', ') : ' ✅'}`)
})()