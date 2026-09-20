/**
 * VIP 会员切片全链路探针
 *
 * 目的：把 `/vip/plan`、`/vip/benefit`、`/vip/order`、`/vip/subscribe` 的**真实字段结构**打出来，
 *      让前端类型定义与展示口径都对着实测写，而不是照着契约想象。
 *
 * ⚠️ 副作用（预期内，任务已说明可接受）：
 *   `POST /vip/subscribe` **会真的给测试账号开通/续费会员**，会员时长进库、退不回来。
 *   所以默认只跑一次，且用最便宜的可购买档位。
 *
 * 用法（默认直连网关 7000，与浏览器经 Vite 代理后的后端行为一致）：
 *   node tools/probe-vip.js                    # 只读 plan/benefit/order + 一次 subscribe
 *   PROBE_READONLY=1 node tools/probe-vip.js   # 完全不写库：只读 plan/benefit/order（无副作用）
 *   PROBE_ORDER=1 node tools/probe-vip.js      # 额外跑完整订单流（下单→模拟支付→退款）+ 促销码错误分支
 *   PROBE_ORIGIN=http://localhost:5173 PROBE_PREFIX=/api node tools/probe-vip.js   # 走 Vite 代理
 *
 * 后端连不上（网关 7000 不通）时**不要猜结构**：脚本会明确报"未能实测"并退出。
 */
const ORIGIN = process.env.PROBE_ORIGIN || 'http://localhost:7000'
const PREFIX = process.env.PROBE_PREFIX !== undefined ? process.env.PROBE_PREFIX : ''
const VIP = 'miaowang-vip'
const USER = 'miaowang-user'
const V1 = '/mapleleaf/miaowang/v1'
const ACCOUNT = {
  username: process.env.PROBE_USER || 'probe114904',
  password: process.env.PROBE_PASS || 'MiaoWang123',
}

/** /vip/plan 的"9 项权益"就是 9 个扁平字段（没有权益名称数组，名称由前端格式化） */
const PLAN_FIELDS = [
  'planCode',
  'planName',
  'price',
  'durationDays',
  'petLimitPerType',
  'ragLimit',
  'ragPeriod',
  'chatLimit',
  'chatPeriod',
  'healthRecordLimit',
  'healthExport',
  'healthAiAnalysis',
  'reminderLimit',
  'sort',
]

const log = (t) => console.log('\n' + t)
const short = (s, n = 600) => {
  const t = typeof s === 'string' ? s : JSON.stringify(s)
  return t && t.length > n ? t.slice(0, n) + '…' : t
}
/** 打印字段名与类型（结构速查，比整段 JSON 好读） */
const shape = (v, indent = '    ') => {
  if (Array.isArray(v)) return v.length ? `[${v.length} 项]\n${shape(v[0], indent + '  ')}` : '[]'
  if (v && typeof v === 'object') {
    return Object.entries(v)
      .map(([k, x]) => {
        const t = x === null ? 'null' : Array.isArray(x) ? `array(${x.length})` : typeof x
        const shown = Array.isArray(x) || (x && typeof x === 'object') ? '' : ` = ${short(x, 60)}`
        return `${indent}${k}: ${t}${shown}`
      })
      .join('\n')
  }
  return `${indent}${typeof v} = ${short(v, 60)}`
}
/** 不限次数用 -1 表示，打印时翻译一下，免得看报告的人误读 */
const num = (v) => (v === -1 ? '-1(不限)' : String(v))

let token = ''
let username = ''

async function call(method, service, path, { body, headers, auth = true } = {}) {
  const h = Object.assign({}, headers)
  if (body !== undefined) h['Content-Type'] = 'application/json'
  if (auth && token) {
    h.token = token
    h.username = username
  }
  const url = `${ORIGIN}${PREFIX}/${service}${V1}${path}`
  let res
  try {
    res = await fetch(url, {
      method,
      headers: h,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch (e) {
    throw new Error(`连不上 ${url}（${e.message}）：网关没起或代理没通，本次未能实测`)
  }
  const text = await res.text()
  let json = null
  try {
    json = text ? JSON.parse(text) : null
  } catch {
    /* 非 JSON 保留原文 */
  }
  return { status: res.status, text, json, url }
}

function show(label, r, max = 600) {
  const j = r.json
  const code = j ? (j.code !== undefined ? 'code=' + j.code : 'state=' + j.state) : '非 JSON'
  const msg = j ? j.message || '' : ''
  console.log(`  ${label}  HTTP ${r.status}  ${code}  ${short(msg, 110)}`)
  if (j && j.code !== undefined && j.code !== '0') console.log(`     原文: ${short(r.text, max)}`)
  return j
}

const data = (r) => (r.json && r.json.data !== undefined ? r.json.data : null)
const orderNoOf = (d) => (d && (d.orderNo || (d.order && d.order.orderNo))) || null

;(async () => {
  /* ---------- [0] 连通性 + 匿名访问档位（契约说"免登录可看"，这里实测一下） ---------- */
  log('[0] 网关连通性 + 匿名 GET /vip/plan（验证契约里"免登录可看"是否成立）')
  let anon
  try {
    anon = await call('GET', VIP, '/vip/plan', { auth: false })
  } catch (e) {
    console.log('  ✗ 未能实测：' + e.message)
    process.exit(1)
  }
  show('GET /vip/plan（不带任何鉴权头）', anon, 400)
  if (anon.json && anon.json.state === -1) {
    console.log('  ⚠️ 实测结论：经网关**需要 token**，契约里写的"免登录可看"不成立 → VIP 页必须在登录后调它')
  }

  /* ---------- [1] 登录 ---------- */
  log('[1] 登录测试账号（免费档）')
  const login = await call('POST', USER, '/user/login', {
    body: { username: ACCOUNT.username, password: ACCOUNT.password },
    auth: false,
  })
  token = (show('POST /user/login', login) || {}).data?.token
  username = ACCOUNT.username
  if (!token) {
    console.log('  ✗ 没拿到 token，后面的用例没法跑')
    process.exit(1)
  }

  /* ---------- [2] 带 token 拉档位（真实结构） ---------- */
  log('[2] 带 token 的 GET /vip/plan（前端真正要走的那条路）')
  const planRes = await call('GET', VIP, '/vip/plan')
  show('GET /vip/plan', planRes, 900)
  const plans = data(planRes)
  if (!Array.isArray(plans) || !plans.length) {
    console.log('  ✗ 没返回档位数组，后面的用例没法挑档位；原始返回：' + short(planRes.text, 900))
    process.exit(1)
  }
  console.log(`  档位 ${plans.length} 个。首项原始 JSON：`)
  console.log('    ' + short(JSON.stringify(plans[0]), 500))
  console.log('  首项字段结构：')
  console.log(shape(plans[0]))
  console.log('  9 项权益字段是否齐全（缺的会标出来）：')
  const missing = PLAN_FIELDS.filter((k) => !(k in plans[0]))
  console.log(`    ${PLAN_FIELDS.length} 个已知字段，缺失：${missing.length ? missing.join(', ') : '无'}`)
  console.log('  全部档位一览（-1 = 不限）：')
  for (const p of plans) {
    console.log(
      `    ${p.planCode}  ${p.planName}  ¥${p.price}/${p.durationDays}天  ` +
        `宠${num(p.petLimitPerType)}只  RAG ${num(p.ragLimit)}/${p.ragPeriod}  聊天 ${num(p.chatLimit)}/${p.chatPeriod}  ` +
        `健康记录${num(p.healthRecordLimit)} 导出${p.healthExport} AI解读${p.healthAiAnalysis} 提醒${num(p.reminderLimit)}`,
    )
  }

  /* ---------- [3] 开通前的会员状态 ---------- */
  log('[3] 开通前的会员状态（GET /vip/benefit）')
  const before = await call('GET', VIP, '/vip/benefit')
  show('GET /vip/benefit', before, 900)
  console.log('  完整字段结构：')
  console.log(shape(data(before)))
  console.log('  原始 JSON：' + short(before.text, 900))

  /* ---------- [4] 下单前的订单列表 ---------- */
  log('[4] 下单前的订单列表（GET /vip/order）')
  const ordersBefore = await call('GET', VIP, '/vip/order')
  show('GET /vip/order', ordersBefore, 900)
  const listBefore = data(ordersBefore)
  console.log(`  返回类型：${Array.isArray(listBefore) ? '数组' : typeof listBefore}，条数 ${Array.isArray(listBefore) ? listBefore.length : '?'}`)
  if (Array.isArray(listBefore) && listBefore.length) {
    console.log('  订单字段结构（首条）：')
    console.log(shape(listBefore[0]))
    console.log('  金额与促销字段：' + short({
      amount: listBefore[0].amount,
      discountAmount: listBefore[0].discountAmount,
      payableAmount: listBefore[0].payableAmount,
      promoCode: listBefore[0].promoCode,
    }))
    console.log('  状态与时间字段：' + short({
      payStatus: listBefore[0].payStatus,
      payStatusDesc: listBefore[0].payStatusDesc,
      payType: listBefore[0].payType,
      createTime: listBefore[0].createTime,
      payTime: listBefore[0].payTime,
    }))
  } else {
    console.log('  （这个账号还没有订单：空数组，不是 null；前端要显示空态）')
  }

  /* ---------- [4b] 可选：核销真实兑换码（自包含，可以和只读模式一起跑） ---------- */
  if (process.env.PROBE_PROMO) {
    const code = process.env.PROBE_PROMO
    log(`[4b] 核销真实兑换码（PROBE_PROMO=${code}）⚠️ 每人每码限用一次，会真的送会员时长`)
    const r = await call('POST', VIP, '/vip/promo/redeem', { body: { code } })
    show(`POST /vip/promo/redeem（${code}）`, r, 900)
    console.log('  返回字段结构：')
    console.log(shape(data(r)))
    console.log('  原始 JSON：' + short(r.text, 900))
    const b2 = await call('GET', VIP, '/vip/benefit')
    const ba = data(b2) || {}
    console.log(
      `  兑换后会员：vip=${ba.vip}  planNames=${short(ba.planNames, 60)}  expireTime=${ba.expireTime}`,
    )
    const o2 = await call('GET', VIP, '/vip/order')
    const rows2 = data(o2) || []
    const promoOrder = rows2.find((o) => o.payType === 'PROMO') || rows2[0]
    console.log('  促销订单（前端订单列表要展示的形状）：' + short(promoOrder, 700))
  } else {
    console.log('\n[4b] 跳过兑换码核销（要跑就加 PROBE_PROMO=<code>，演示码见 MiaoWang-Vip/sql/vip-promo-v4.sql）')
  }

  /* ---------- [5] 一键开通（有副作用） ---------- */
  if (process.env.PROBE_READONLY === '1') {
    log('[5] 只读模式（PROBE_READONLY=1）：跳过 subscribe 与后面的写操作，不动数据库')
    console.log('  已读到的档位：' + plans.map((p) => p.planCode).join(', '))
    console.log('  当前会员：vip=' + (data(before) || {}).vip + '  planNames=' + short((data(before) || {}).planNames, 60))
    return
  }

  log('[5] 一键开通（POST /vip/subscribe）⚠️ 会真的开/续会员')
  const cheapest = plans
    .filter((p) => Number(p.price) > 0)
    .sort((a, b) => Number(a.price) - Number(b.price))[0]
  const target = cheapest || plans[0]
  const planCode = target.planCode
  console.log(`  挑最便宜的可购买档位：${planCode}（${target.planName}，价格 ${target.price}，${target.durationDays} 天）`)
  const sub = await call('POST', VIP, '/vip/subscribe', { body: { planCode } })
  show('POST /vip/subscribe', sub, 900)
  console.log('  返回字段结构：')
  console.log(shape(data(sub)))
  console.log('  原始 JSON：' + short(sub.text, 900))

  /* ---------- [6] 开通后状态变化 ---------- */
  log('[6] 开通后的会员状态（关键字段是否真的变了）')
  const after = await call('GET', VIP, '/vip/benefit')
  show('GET /vip/benefit', after, 900)
  const a = data(after) || {}
  const b = data(before) || {}
  const pick = (o) => ({
    vip: o.vip,
    planNames: o.planNames,
    expireTime: o.expireTime,
    petLimitPerType: o.petLimitPerType,
    ragLimit: o.ragLimit,
    ragPeriod: o.ragPeriod,
    ragPeriodDesc: o.ragPeriodDesc,
    ragUsed: o.ragUsed,
    ragRemaining: o.ragRemaining,
    chatLimit: o.chatLimit,
    chatPeriod: o.chatPeriod,
    chatPeriodDesc: o.chatPeriodDesc,
    chatUsed: o.chatUsed,
    chatRemaining: o.chatRemaining,
    healthRecordLimit: o.healthRecordLimit,
    healthExport: o.healthExport,
    healthAiAnalysis: o.healthAiAnalysis,
    reminderLimit: o.reminderLimit,
  })
  console.log('  开通前：' + short(pick(b), 700))
  console.log('  开通后：' + short(pick(a), 700))
  console.log(`  vip：${b.vip} → ${a.vip}   planNames：${short(b.planNames, 60)} → ${short(a.planNames, 60)}`)

  /* ---------- [7] 开通后的订单列表 ---------- */
  log('[7] 开通后的订单列表（subscribe 内部会落一条单）')
  const ordersAfter = await call('GET', VIP, '/vip/order')
  show('GET /vip/order', ordersAfter, 900)
  const listAfter = data(ordersAfter)
  if (Array.isArray(listAfter) && listAfter.length) {
    console.log(`  订单 ${listAfter.length} 条，最新一条完整结构：`)
    console.log(shape(listAfter[0]))
    console.log('  最新一条原始 JSON：' + short(JSON.stringify(listAfter[0]), 700))
    console.log('  「实付」该取哪个字段：' + short({
      payableAmount: listAfter[0].payableAmount,
      amount: listAfter[0].amount,
      discountAmount: listAfter[0].discountAmount,
    }))
  }

  /* ---------- [8] 完整订单流（可选） ---------- */
  if (process.env.PROBE_ORDER === '1') {
    log('[8] 完整订单流：下单 → 幂等重放 → 模拟支付 → 退款（PROBE_ORDER=1）')
    const cheap = plans.filter((p) => Number(p.price) > 0).sort((x, y) => Number(x.price) - Number(y.price))[0] || plans[0]
    const code = cheap.planCode
    const requestId = `probe-${Date.now()}`
    const created = await call('POST', VIP, '/vip/order', { body: { planCode: code, requestId } })
    const cj = show('POST /vip/order', created, 900)
    console.log('  下单返回结构：')
    console.log(shape(data(created)))
    console.log('  payParams：' + short(cj && cj.data && cj.data.payParams, 300))
    const orderNo = orderNoOf(cj && cj.data)
    if (!orderNo) {
      console.log('  ✗ 返回里没找到 orderNo，跳过支付（照上面的结构改脚本）')
    } else {
      console.log('  幂等验证：同一 requestId 再下一次单')
      const again = await call('POST', VIP, '/vip/order', { body: { planCode: code, requestId } })
      const aj = show('POST /vip/order（同 requestId）', again, 400)
      const orderNo2 = orderNoOf(aj && aj.data)
      console.log(`    两次 orderNo：${orderNo} vs ${orderNo2} → ${orderNo2 === orderNo ? '✅ 幂等命中同一单' : '⚠️ 不是同一单，幂等键没按预期生效'}`)

      console.log('  待支付订单在列表里的样子（前端"去支付/取消"分支要用的字段）：')
      const mid = await call('GET', VIP, '/vip/order')
      const midRow = (data(mid) || []).find((o) => o.orderNo === orderNo)
      console.log('    ' + short(midRow, 700))

      const paid = await call('POST', VIP, `/vip/order/${orderNo}/pay`, { body: {} })
      show(`POST /vip/order/${orderNo}/pay`, paid, 900)
      console.log('  支付返回结构：')
      console.log(shape(data(paid)))

      const refunded = await call('POST', VIP, `/vip/order/${orderNo}/refund`, {
        body: { reason: '探针验证退款链路' },
      })
      show(`POST /vip/order/${orderNo}/refund`, refunded, 900)
      console.log('  退款返回结构：')
      console.log(shape(data(refunded)))

      const finalList = await call('GET', VIP, '/vip/order')
      const row = (data(finalList) || []).find((o) => o.orderNo === orderNo)
      console.log('  退款后该单状态（payStatus 应为 3，且带 payStatusDesc）：')
      console.log('    ' + short(row, 700))
    }

    log('[9] 促销码兑换错误分支（POST /vip/promo/redeem，故意传不存在的码）')
    const bad = await call('POST', VIP, '/vip/promo/redeem', { body: { code: 'PROBE-NOT-EXIST' } })
    show('POST /vip/promo/redeem（不存在的码）', bad, 400)
  } else {
    log('[8] 跳过完整订单流与促销码（要跑就加 PROBE_ORDER=1）')
  }

  log('[10] 汇总')
  console.log(
    '  ✅ 已实测：GET /vip/plan、GET /vip/benefit、GET /vip/order、POST /vip/subscribe' +
      (process.env.PROBE_ORDER === '1' ? '、POST /vip/order(下单/幂等)、pay、refund、promo/redeem(错误分支)' : ''),
  )
  console.log(`  副作用：${ACCOUNT.username} 的会员时长已按 ${planCode} 延长（不能撤销）`)
})()