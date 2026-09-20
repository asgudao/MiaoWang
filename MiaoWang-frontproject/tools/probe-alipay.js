/**
 * 支付宝沙箱支付渠道探针
 *
 * 目的：把「下单（payType=ALIPAY）→ 打印 payHtml / payUrl / qrCode → 主动查单对账（/sync）→ 订单与会员状态」的
 *      **真实返回结构**打出来，并顺手验证两件最容易做错的事：
 *        ① 主动查单的幂等（反复调 /sync 不会重复发会员）
 *        ② 密钥没配时后端是否**明确报"渠道未配置"**（而不是 500 / NPE）
 *
 * 两种支付场景（PROBE_SCENE 切换，都走同一个订单状态机与同一套对账逻辑）：
 *   - 不传 / PAGE：网页支付（跳收银台），payParams 里是 `payHtml`（完整 HTML 表单）**与**
 *     `payUrl`（GET 链接，只作旧沙箱回退）—— 见下面 [3] 的说明；
 *   - QR：扫码支付（alipay.trade.precreate 预下单），payParams 里是 `qrCode` ——
 *     ⚠️ **`qrCode` 是一串支付码字符串（形如 https://qr.alipay.com/xxxx），不是图片地址**，
 *     要用二维码库把它**渲染成二维码图片**，再用手机上的支付宝沙箱版扫它付款。
 *
 * ⚠️ 新沙箱的收银台**只能用表单提交打开**（真沙箱实测）：
 *     `openapi-sandbox.dl.alipaydev.com` 是新版沙箱的 **API 网关主机**，只接受 **POST** 的接口调用、
 *     不渲染收银台页面 —— 把 `payUrl`（`pageExecute(request, "GET")` 出来的 GET 链接）直接粘进浏览器，
 *     支付宝只会回 **404**。`alipay.trade.page.pay` 的官方集成方式本来就是「把 SDK 生成的 HTML 表单
 *     POST 到网关，由网关再跳到收银台」，所以后端现在同时下发 `payHtml`（无参 `pageExecute(request)`
 *     的 `getBody()`）。前端应该把 `payHtml` 写进新窗口让它自动提交，`payUrl` 只作旧沙箱的回退。
 *     本探针把 payUrl 完整打出来（方便你亲手复现那个 404），但**只报告 payHtml 是否存在与长度**，
 *     不整段打印（太长）。
 *
 * ⚠️ 付款必须在真实设备上手动完成（脚本不会替你付钱）：
 *      加 PROBE_WAIT=1 时脚本会停下来等你付完再回车，然后调 /sync 主动对账。
 *
 * 用法（默认直连网关 7000，与浏览器经 Vite 代理后的后端行为一致）：
 *   node tools/probe-alipay.js                          # 下单 + 打印 payUrl / payHtml 有无 + 两次 /sync
 *   PROBE_SCENE=QR node tools/probe-alipay.js            # 扫码场景：下单带 payScene=QR，打印 qrCode
 *   PROBE_WAIT=1 node tools/probe-alipay.js             # 付完款回车，再对账并检查会员是否到账（QR 同样可用）
 *   PROBE_PLAN=PET_49 node tools/probe-alipay.js        # 指定档位（默认挑最便宜的可购买档位）
 *   PROBE_ORIGIN=http://localhost:5173 PROBE_PREFIX=/api node tools/probe-alipay.js   # 走 Vite 代理
 *
 * 前置条件：后端（vip 服务）必须已经重启到带 AlipayPayChannel 的版本，且 Nacos 配好
 *          vip.pay.alipay.appId / merchantPrivateKey / alipayPublicKey（密钥不要提交进仓库）。
 *          扫码场景还要求沙箱应用签约了「当面付」（precreate 属该产品）；没签约会报权限类错误，
 *          这时**别去查密钥**，先去沙箱控制台确认产品权限。
 *
 * 后端连不上（网关 7000 不通）时**不要猜结构**：脚本会明确报"未能实测"并退出。
 */
const ORIGIN = process.env.PROBE_ORIGIN || 'http://localhost:7000'
const PREFIX = process.env.PROBE_PREFIX !== undefined ? process.env.PROBE_PREFIX : ''
const VIP = 'miaowang-vip'
const USER = 'miaowang-user'
const V1 = '/mapleleaf/miaowang/v1'
/** 支付场景：QR=扫码（预下单）、PAGE=网页跳转、空=不传（渠道默认，向后兼容） */
const SCENE = (process.env.PROBE_SCENE || '').trim().toUpperCase()
const ACCOUNT = {
  username: process.env.PROBE_USER || 'probe114904',
  password: process.env.PROBE_PASS || 'MiaoWang123',
}

/** 已知的支付相关错误码：探针不崩，而是把"后端到底说了什么"翻译成人话 */
const KNOWN_CODES = {
  B000533: '渠道未配置 → Nacos 里补齐 vip.pay.alipay.appId / merchantPrivateKey / alipayPublicKey',
  B000513: '暂不支持该支付渠道/场景 → 渠道没实现，或 payScene 传了不认识的取值（只认 QR / PAGE，不传=PAGE）；也可能是 vip 服务跑的还是旧包，需要重启',
  B000517: '该渠道不接受外部回调（只有 mock 会这样）',
  B000514: '回调验签失败（支付宝公钥配错了？注意是"支付宝公钥"不是"应用公钥"）',
  B000515: '支付金额与订单不一致（多半是下单时传了 total_amount != payableAmount）',
  B000512: '订单状态不允许该操作（订单已关闭/已退款，或已超 30 分钟被懒关单）',
}

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

let token = ''
let username = ''
let planCode = ''
let orderNo = ''
let expireTimeAfterPaid = null

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

/** 把已知错误码翻译成人话（未知码原样返回，不编） */
function explain(j) {
  if (!j || !j.code || j.code === '0') return null
  const hint = KNOWN_CODES[j.code]
  return hint ? `${j.code}（${j.message}）→ ${hint}` : `${j.code}（${j.message}）→ 这个码探针不认识，按原文判断`
}

async function waitEnter(prompt) {
  const readline = require('node:readline/promises')
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout })
  await rl.question(prompt)
  rl.close()
}

;(async () => {
  /* ---------- [0] 登录 ---------- */
  log('[0] 登录测试账号')
  const login = await call('POST', USER, '/user/login', {
    body: { username: ACCOUNT.username, password: ACCOUNT.password },
    auth: false,
  })
  let lj
  try {
    lj = show('POST /user/login', login)
  } catch (e) {
    console.log('  ✗ 未能实测：' + e.message)
    process.exit(1)
  }
  token = (lj || {}).data?.token
  username = ACCOUNT.username
  if (!token) {
    console.log('  ✗ 没拿到 token，后面的用例没法跑')
    process.exit(1)
  }

  /* ---------- [1] 挑档位 ---------- */
  log('[1] 挑一个档位（GET /vip/plan）')
  const planRes = await call('GET', VIP, '/vip/plan')
  const plans = data(planRes)
  show('GET /vip/plan', planRes, 400)
  if (!Array.isArray(plans) || !plans.length) {
    console.log('  ✗ 没返回档位数组，后面的用例没法跑；原始返回：' + short(planRes.text, 400))
    process.exit(1)
  }
  const cheapest = plans.filter((p) => Number(p.price) > 0).sort((a, b) => Number(a.price) - Number(b.price))[0]
  const target = plans.find((p) => p.planCode === process.env.PROBE_PLAN) || cheapest || plans[0]
  planCode = target.planCode
  console.log(`  选定档位：${planCode}（${target.planName}，价格 ${target.price}，${target.durationDays} 天）`)

  /* ---------- [2] 下单前的会员状态 ---------- */
  log('[2] 下单前的会员状态（GET /vip/benefit）')
  const before = await call('GET', VIP, '/vip/benefit')
  show('GET /vip/benefit', before, 400)
  const benefitBefore = data(before) || {}
  console.log('  关键字段：' + short({
    vip: benefitBefore.vip,
    planNames: benefitBefore.planNames,
    expireTime: benefitBefore.expireTime,
    petLimitPerType: benefitBefore.petLimitPerType,
  }))

  /* ---------- [3] 下单（payType=ALIPAY，可选 payScene） ---------- */
  log(`[3] 下单：POST /vip/order（payType=ALIPAY${SCENE ? '，payScene=' + SCENE : '，不传 payScene=渠道默认'}）`)
  const requestId = `probe-alipay-${Date.now()}`
  /** payScene 只在显式指定时才带：不传就是"渠道默认"，用来验证向后兼容 */
  const orderBody = { planCode, payType: 'ALIPAY', requestId }
  if (SCENE) orderBody.payScene = SCENE
  const created = await call('POST', VIP, '/vip/order', { body: orderBody })
  const cj = show('POST /vip/order', created, 900)
  const explainCreate = explain(cj)
  if (explainCreate) console.log('  ⚠️ ' + explainCreate)
  if (!cj || cj.code !== '0') {
    console.log('\n  下单没成功，后面的支付/对账用例不成立。')
    console.log('  这是后端对"渠道没配好/没重启"的真实应答，探针不会替你猜结构。')
    process.exit(0)
  }
  const order = data(created) || {}
  orderNo = orderNoOf(order)
  console.log('  订单对象结构：')
  console.log(shape(order))
  console.log('  金额口径（前端"实付"要显示 payableAmount）：' + short({
    amount: order.amount,
    discountAmount: order.discountAmount,
    payableAmount: order.payableAmount,
    payType: order.payType,
    payStatus: order.payStatus,
    payStatusDesc: order.payStatusDesc,
  }))
  const payParams = order.payParams || {}
  console.log('  payParams 结构：')
  console.log(shape(payParams))
  if (SCENE === 'QR') {
    /* 扫码场景的契约（前端就按这个写）：
       { channel:"ALIPAY", scene:"QR", outTradeNo, qrCode, subject, amount, tip } */
    console.log('  期望形状：{ channel:"ALIPAY", scene:"QR", outTradeNo, qrCode, subject, amount, tip }')
    const missing = ['channel', 'scene', 'outTradeNo', 'qrCode', 'subject', 'amount', 'tip']
      .filter((k) => payParams[k] === undefined)
    if (missing.length) console.log(`  ✗ payParams 缺契约字段：${missing.join(' / ')}`)
    if (payParams.scene !== 'QR') console.log(`  ✗ payParams.scene=${payParams.scene}，契约要求字符串 "QR"`)
    if (payParams.channel === 'ALIPAY') {
      const amountOk = Number(payParams.amount) === Number(order.payableAmount)
      console.log(`  金额口径：payParams.amount=${payParams.amount} vs 订单 payableAmount=${order.payableAmount} → ` +
        `${amountOk ? '✅ 用的是应付额（不是原价 amount）' : '⚠️ 不一致，可能是立减码口径问题'}`)
    }
    if (!payParams.qrCode) {
      console.log('  ✗ payParams 里没有 qrCode —— 契约要求"支付码字符串"，后端不许返回空码让前端自己发现')
      if (payParams.payUrl) {
        console.log('    但它返回了 payUrl：多半是 vip 服务跑的还是"只有 PAGE 场景"的旧包，payScene=QR 被忽略了，需要重启到新包')
      }
      process.exit(1)
    }
    console.log(`  qrCode（长度 ${String(payParams.qrCode).length}）：`)
    console.log('    ' + payParams.qrCode)
    console.log('  ⚠️ 上面这串是**支付码字符串**，不是图片地址、也不是二维码图片的 base64 ——')
    console.log('     直接塞 <img src> 是打不开的，必须用二维码库渲染成图片。')
    console.log('     快速手工验证：把它粘进任意在线二维码生成器（如 https://cli.im），或本地 qrcode 库转 dataURL。')
    console.log('  然后：用【手机上的支付宝沙箱版】扫这个二维码付款（不是电脑浏览器里的支付宝）。')
    console.log('  若下单直接报 B000534：看 vip 服务日志里的失败原因；若是「无权限 / 产品未开通」，')
    console.log('  先去支付宝沙箱控制台确认应用签了「当面付」（alipay.trade.precreate 属该产品），别先怀疑密钥。')
  } else {
    /* 收银台场景的契约（前端就按这个写）：
       { channel:"ALIPAY", orderNo, outTradeNo, subject, amount, payUrl, payHtml, tip }
       —— payHtml 是**完整 HTML 表单**（新沙箱正路），payUrl 是 GET 链接（只作旧沙箱回退） */
    console.log('  期望形状：{ channel:"ALIPAY", orderNo, outTradeNo, subject, amount, payUrl, payHtml, tip }')
    const missing = ['channel', 'orderNo', 'outTradeNo', 'subject', 'amount', 'tip']
      .filter((k) => payParams[k] === undefined)
    if (missing.length) console.log(`  ✗ payParams 缺契约字段：${missing.join(' / ')}`)

    const payHtml = typeof payParams.payHtml === 'string' ? payParams.payHtml : ''
    const payUrl = typeof payParams.payUrl === 'string' ? payParams.payUrl : ''

    // payHtml：只报告有无与长度，绝不整段打印（是一整段 HTML，几千字符）
    if (payHtml) {
      const looksLikeForm = /<form[\s>]/i.test(payHtml) && /method\s*=\s*"?post/i.test(payHtml)
      const hasAutoSubmit = /document\.forms\[0\]\.submit|\.submit\(\)/i.test(payHtml)
      console.log(`  ✅ payParams.payHtml 存在，长度 ${payHtml.length} 字符`
        + `（${looksLikeForm ? '含 <form method="post">' : '⚠️ 没看到 <form method="post">'}，`
        + `${hasAutoSubmit ? '含自动提交脚本' : '⚠️ 没看到自动提交脚本'}）`)
      console.log('     用法：前端把它 window.open(\'\', \'_blank\') 后 document.write 进去，页面会自动 POST 给网关。')
    } else {
      console.log('  ✗ payParams.payHtml 为空 —— 新沙箱下这条是唯一的收银台入口（GET 链接会 404），'
        + '多半是 vip 服务跑的还是只下发 payUrl 的旧包，需要重启到新包')
    }
    if (payParams.payHtml === undefined) console.log('  ✗ payParams 里根本没有 payHtml 这个键（旧包）')

    if (payUrl) {
      console.log(`  payParams.payUrl（GET 链接，长度 ${String(payUrl).length}，完整打印）：`)
      console.log('    ' + payUrl)
      console.log('    提示语：⚠️ 新沙箱里这条 GET 链接用浏览器打开会 **404** ——')
      console.log('      openapi-sandbox.dl.alipaydev.com 是沙箱的 API 网关主机，只接受 POST 的接口调用，')
      console.log('      不渲染收银台页面。所以**以 payHtml 表单提交为准**，payUrl 只作旧沙箱的回退。')
      console.log('      （你可以亲手粘进浏览器复现那个 404：那是预期的，不是密钥或订单的问题）')
    } else {
      console.log('  ⚠️ payParams.payUrl 为空：旧沙箱的回退路径不可用（若 payHtml 存在则不影响新沙箱使用）')
    }
    if (!payHtml && !payUrl) {
      console.log('  ✗ payHtml 与 payUrl 都为空 —— 前端拿不到任何收银台入口，契约不成立')
      process.exit(1)
    }
  }
  if (payParams.channel && payParams.channel !== 'ALIPAY') {
    console.log(`  ⚠️ payParams.channel=${payParams.channel}，不是 ALIPAY —— 下单时 payType 没被采纳`)
  }

  /* ---------- [4] 幂等重放：同一 requestId 只出一单 ---------- */
  log('[4] 幂等验证：同一 requestId 再下一次单（带同一个 payScene）')
  const again = await call('POST', VIP, '/vip/order', { body: orderBody })
  const aj = show('POST /vip/order（同 requestId）', again, 400)
  const orderNo2 = orderNoOf(data(again))
  console.log(`    两次 orderNo：${orderNo} vs ${orderNo2} → ${orderNo2 === orderNo ? '✅ 幂等命中同一单' : '⚠️ 不是同一单，幂等键没按预期生效'}`)
  if (SCENE === 'QR') {
    const againParams = (data(again) || {}).payParams || {}
    console.log(`    复用订单时 payParams.qrCode 是否仍返回：${
      againParams.qrCode ? '✅ 有（预下单是幂等的，同一订单同一串码）' : '⚠️ 没有 —— 前端复用订单时会渲染不出二维码'}`)
  } else {
    /* 这条很关键：前端「再点一次」命中的就是这条幂等路径；后端 toRespDTO 在两条分支都带 payParams
       （VipOrderServiceImpl 里 existOrder / pendingOrder 都走 channel.createPayment），
       所以 payHtml 在这里也必须还在，否则幂等复用订单时收银台就打不开了 */
    const againParams = (data(again) || {}).payParams || {}
    const againHtml = typeof againParams.payHtml === 'string' ? againParams.payHtml : ''
    console.log(`    复用订单时 payParams.payHtml 是否仍返回：${
      againHtml ? `✅ 有（长度 ${againHtml.length} 字符）` : '⚠️ 没有 —— 幂等复用订单时前端就打不开收银台了'}`)
  }

  /* ---------- [5] 主动查单（未支付时应无副作用） ---------- */
  log('[5] 主动查单对账：POST /vip/order/{orderNo}/sync（未支付，第一次）')
  const sync1 = await call('POST', VIP, `/vip/order/${orderNo}/sync`, { body: {} })
  const sj1 = show(`POST /vip/order/${orderNo}/sync`, sync1, 900)
  const explainSync = explain(sj1)
  if (explainSync) console.log('  ⚠️ ' + explainSync)
  if (!sj1 || sj1.code !== '0') {
    console.log('  ✗ /sync 没成功。若 code=B000001 且 message=系统执行出错 → 多半是 vip 服务没重启，还没这个接口')
  } else {
    console.log('  返回的订单结构（应与下单时同形状）：')
    console.log(shape(data(sync1)))
    const s1 = data(sync1) || {}
    console.log(`  支付状态：payStatus=${s1.payStatus}（${s1.payStatusDesc}）  渠道单号=${s1.channelTradeNo || '（还没有）'}`)
    if (s1.payStatus === 0) {
      console.log('  ✅ 还没付款 → 只是回读订单，没有凭空发会员（benefit 不变才是对的）')
    }
  }

  log('[5b] 幂等验证：同一个订单再查一次 /sync')
  const sync2 = await call('POST', VIP, `/vip/order/${orderNo}/sync`, { body: {} })
  const sj2 = show(`POST /vip/order/${orderNo}/sync（第二次）`, sync2, 400)
  if (sj2 && sj2.code === '0') {
    const s2 = data(sync2) || {}
    const s1 = data(sync1) || {}
    console.log(`    两次 payStatus：${s1.payStatus} vs ${s2.payStatus} → ${s1.payStatus === s2.payStatus ? '✅ 一致（幂等）' : '⚠️ 状态变了，值得查'}`)
  }

  /* ---------- [6] 人工支付后对账（可选） ---------- */
  if (process.env.PROBE_WAIT === '1') {
    log('[6] 人工支付后对账（PROBE_WAIT=1）')
    if (SCENE === 'QR') {
      console.log('  请把上面的 qrCode 渲染成二维码（在线生成器/二维码库都行），用【手机上的支付宝沙箱版】扫码付款。')
      console.log('  注意：扫码支付不会跳回任何页面，所以结果只能靠主动查单；')
      console.log('  且支付宝的异步回调打不到局域网里的开发机，属预期 —— 主动查单就是这条链路的正常组成部分。')
    } else {
      console.log('  请把上面的 payHtml 用「新窗口写入表单」的方式打开（浏览器里随便找个能写 HTML 的地方即可），')
      console.log('  让它自动 POST 到网关、由网关跳到收银台，再用沙箱买家账号完成支付。')
      console.log('  ⚠️ 不要直接把 payUrl 粘进浏览器：新沙箱里那条 GET 链接会 404（那是预期的）。')
      console.log('  注意：支付宝的异步回调打不到局域网里的开发机，属预期；')
      console.log('  支付完它会把浏览器跳到 return_url（Web 端的 /vip/pay/return），然后我们主动查单。')
    }
    await waitEnter('  付完以后按回车继续… ')

    const paid = await call('POST', VIP, `/vip/order/${orderNo}/sync`, { body: {} })
    const pj = show(`POST /vip/order/${orderNo}/sync（支付后）`, paid, 900)
    const explainPaid = explain(pj)
    if (explainPaid) console.log('  ⚠️ ' + explainPaid)
    const p = data(paid) || {}
    console.log('  订单：' + short({
      payStatus: p.payStatus,
      payStatusDesc: p.payStatusDesc,
      payTime: p.payTime,
      channelTradeNo: p.channelTradeNo,
      startTime: p.startTime,
      expireTime: p.expireTime,
    }))
    if (p.payStatus === 1) {
      console.log('  ✅ 主动查单把订单补成「已支付」并发出了会员（回调没到也能到账）')
    } else {
      console.log('  ⚠️ 还是没变成已支付：确认沙箱支付真的成功了、且下单与支付用的是同一个 out_trade_no')
    }

    const benefitPaid = data(await call('GET', VIP, '/vip/benefit')) || {}
    expireTimeAfterPaid = benefitPaid.expireTime
    console.log('  会员：' + short({
      vip: benefitPaid.vip,
      planNames: benefitPaid.planNames,
      expireTime: benefitPaid.expireTime,
      petLimitPerType: benefitPaid.petLimitPerType,
    }))

    log('[6b] 幂等关键验证：已支付的订单再查一次 /sync（不能重复发会员）')
    const syncAfterPaid = await call('POST', VIP, `/vip/order/${orderNo}/sync`, { body: {} })
    show(`POST /vip/order/${orderNo}/sync（已支付后再查）`, syncAfterPaid, 400)
    const benefitAgain = data(await call('GET', VIP, '/vip/benefit')) || {}
    console.log(`    到期时间：${expireTimeAfterPaid} vs ${benefitAgain.expireTime} → ` +
      `${benefitAgain.expireTime === expireTimeAfterPaid ? '✅ 没变，没有重复发会员' : '⚠️ 变了，可能重复发了会员，要查'}`)
  } else {
    log('[6] 跳过人工支付后对账（要跑就加 PROBE_WAIT=1）')
  }

  /* ---------- [7] 订单列表里的样子 ---------- */
  log('[7] 订单列表里的样子（GET /vip/order，前端支付轮询看的就是它）')
  const list = await call('GET', VIP, '/vip/order')
  show('GET /vip/order', list, 400)
  const row = (data(list) || []).find((o) => o.orderNo === orderNo)
  if (row) {
    console.log('  本单完整结构：')
    console.log(shape(row))
    console.log('  「该显示哪个金额」：' + short({
      payableAmount: row.payableAmount,
      amount: row.amount,
      discountAmount: row.discountAmount,
    }))
    console.log('  payParams 在列表里（应为 null，只有下单那一步返回）：' + short(row.payParams))
  } else {
    console.log('  ✗ 列表里没找到本单，可能与懒关单/分页有关，请人工核对')
  }

  /* ---------- [8] 汇总 ---------- */
  log('[8] 汇总')
  console.log(`  支付场景：${SCENE || '（未传 payScene，走后端渠道默认）'}`)
  console.log(`  已实测：登录、GET /vip/plan、GET /vip/benefit、POST /vip/order(payType=ALIPAY${SCENE ? ', payScene=' + SCENE : ''})、` +
    `POST /vip/order/{orderNo}/sync（未支付与幂等）${process.env.PROBE_WAIT === '1' ? '、人工支付后对账' : ''}`)
  console.log(`  订单号：${orderNo}（档位 ${planCode}，金额见上）`)
  if (SCENE === 'QR') {
    console.log('  扫码场景要点：payParams.qrCode 是【支付码字符串】不是图片地址；渲染成二维码后用手机沙箱 App 扫。')
  } else {
    console.log('  收银台场景要点：以 payParams.payHtml（HTML 表单，前端写进新窗口自动提交）为准；')
    console.log('    payParams.payUrl 是 GET 链接，**新沙箱打开会 404**，只作旧沙箱回退。')
  }
  if (process.env.PROBE_WAIT !== '1') {
    console.log('  未验证：真实沙箱支付到账、回调（局域网内回调本来就到不了）、退款（/refund 需要先真付一笔）')
  }
  if (SCENE === 'QR') {
    console.log('  另需人工确认：沙箱应用是否签约了「当面付」（precreate 属该产品）—— 没签约会报权限类错误。')
  }
})()