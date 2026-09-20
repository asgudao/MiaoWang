/**
 * pet 模块真调用探针：把"响应形状 + 错误码"打出来，前端照着它写，不靠 DTO 猜。
 *
 * 覆盖：登录 → 读列表 → 新建(合法) → 新建(非法大类) → 改名 → 再读列表 → 新建(空名字)
 *
 * 用法：node probe-pet.js [username] [password]
 * 注意：**会往 dev 库写数据**（默认写在探针账号 probe114904 名下，免费档上限 2 只）。
 */
const GW = process.env.MIAOWANG_GW || 'http://localhost:7000'
const USERNAME = process.argv[2] || 'probe114904'
const PASSWORD = process.argv[3] || 'MiaoWang123'
const PREFIX = '/mapleleaf/miaowang/v1'

function claimsOf(token) {
  try {
    const part = token.split('.')[1]
    return JSON.parse(Buffer.from(part.replace(/-/g, '+').replace(/_/g, '/'), 'base64').toString('utf8'))
  } catch {
    return null
  }
}

function makeClient(token, username) {
  return async function call(method, path, body, opts) {
    const header = {}
    if (body !== undefined) header['Content-Type'] = 'application/json'
    if (token && !(opts && opts.anonymous)) {
      header.token = token
      header.username = username
    }
    const c = new AbortController()
    const t = setTimeout(() => c.abort(), 10000)
    try {
      const r = await fetch(GW + path, {
        method,
        headers: header,
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: c.signal,
      })
      const text = await r.text()
      let json = null
      try {
        json = JSON.parse(text)
      } catch {
        /* 保留原始文本 */
      }
      return { status: r.status, json, text }
    } finally {
      clearTimeout(t)
    }
  }
}

function show(step, r, extra) {
  const j = r.json
  if (!j) {
    console.log(`\n[${step}] HTTP ${r.status}  ${r.text.slice(0, 200)}`)
    return
  }
  console.log(
    `\n[${step}] HTTP ${r.status}  code=${j.code}  success=${j.success}  message=${j.message}` +
      (extra ? `  (${extra})` : ''),
  )
  if (j.data !== undefined) console.log('    data = ' + JSON.stringify(j.data))
}

;(async () => {
  const login = await makeClient(null)('POST', `/miaowang-user${PREFIX}/user/login`, {
    username: USERNAME,
    password: PASSWORD,
  })
  const token = (login.json && login.json.data && login.json.data.token) || ''
  if (!token) {
    console.log(`登录失败：${login.text.slice(0, 200)}`)
    process.exit(1)
  }
  const username = (claimsOf(token) || {}).claims.username
  const call = makeClient(token, username)
  console.log(`用户 ${username}（uid ${(claimsOf(token) || {}).claims.uid}）`)

  const base = '/miaowang-pet' + PREFIX + '/pet'

  show('0 读列表（基线）', await call('GET', base))

  const created = await call('POST', base, {
    pName: '探针小白',
    species: '猫',
    specificSpecies: '中华田园猫',
    ownerCallName: '铲屎的',
    birth: '2021-05-01',
    gender: 0,
    sterilizedState: 1,
  })
  show('1 新建（合法）', created)
  const pid = created.json && created.json.data && created.json.data.pid

  show(
    '2 新建（非法大类 "CAT"）',
    await call('POST', base, { pName: 'x', species: 'CAT' }),
    '看错误码与提示文案',
  )
  show('3 新建（大类缺失）', await call('POST', base, { pName: 'x' }))

  if (pid) {
    show('4 改名 PUT /pet/{pid}', await call('PUT', `${base}/${pid}`, { pName: '探针小黑' }))
    const after = await call('GET', base)
    const list = (after.json && after.json.data) || []
    console.log(`\n[5] 改名后列表：${list.length} 只 → ` + list.map((p) => p.pName).join(', '))
    console.log('    birth 字段实际类型 = ' + typeof (list[0] && list[0].birth) + '  值 = ' + JSON.stringify(list[0] && list[0].birth))
  }

  show(
    '6 新建（pName 为空）',
    await call('POST', base, { species: '狗' }),
    '后端未校验 pName，看是否会被放进库 / 或先撞数量上限',
  )
  show('7 越权改名（别人的 pid 999999）', await call('PUT', `${base}/999999`, { pName: 'hack' }))
  show('8 读别人的宠物（公开读）', await call('GET', base + '/user/1'))

  show(
    '9 新建第 3 只（撞免费档总数上限）',
    await call('POST', base, { pName: '探针超限', species: '猫', ownerCallName: 'x' }),
    '期望 B000800；若 vip 服务不可用则是 B000801',
  )

  // 自清理：删掉本次探针造的垃圾（pName 以"探针"开头，或后端未校验 pName 造出的无名宠物）
  const finalList = await call('GET', base)
  const mine = (finalList.json && finalList.json.data) || []
  const junk = mine.filter((p) => p.pName === null || String(p.pName).startsWith('探针'))
  if (junk.length) {
    console.log('\n[清理] 删除探针造的数据：')
    for (const p of junk) {
      const r = await call('DELETE', `${base}/${p.pid}`)
      console.log(`    DELETE pid=${p.pid} (${p.pName}) → code=${(r.json && r.json.code) || r.status}`)
    }
  }
  show('10 清理后列表', await call('GET', base))
})()