/**
 * 健康记录展示工具（手机端 / JS）
 *
 * 为什么单独一个文件：手机端模板**不能调 utils / store**（Options API 的模板只认实例属性，
 * 渲染期访问不到 import 进来的对象会直接报错并白屏）。所以这里只放**纯函数**，
 * 由页面 `computed` 调一次，把原始 DTO 摊平成模板可以直接 `{{ }}` 的字段。
 *
 * 口径全部来自**实测**（`.dsh-tmp/healthy-contract.md` 末节 + `tools/health-fixtures/` 的真实响应），
 * 不许按 REST 惯例补字段。踩过的坑逐条写在这里：
 *
 * - ⚠️ **时间是 `yyyy-MM-dd HH:mm:ss`（GMT+8，非 ISO）**：`new Date('2026-08-01 09:30:00')`
 *   在 iOS Safari / 部分安卓 WebView 上得到 `Invalid Date` → 一律走 `parseHealthTime()`，
 *   它把空格换成 `T`（社区那次没踩是因为社区是 ISO 串）。
 *   例外：`weight/curve.date` 与宠物 `birth` 是 `yyyy-MM-dd`（**没有时分秒**）。
 * - ⚠️ **`nextDueDays` 可为负**（实测 `-10` = 已逾期 10 天），整数截断（向零）；
 *   `nextDueAt` 为空时该字段是 `null` → 必须先判空再比大小。
 *   `0` 表示"今天到期 / 不到 1 天"，**不能当"还没到期"**。
 * - ⚠️ **`overview.nextPreventive` 排除了逾期项**（源码 `.ge(nextDueAt, now)`）→
 *   逾期只能自己拉 `GET /{pid}/preventive` 全量后按 `nextDueDays` 分档（见 `overdueItems`）。
 * - ⚠️ **`attachments` / `images`：入参逗号串、出参数组**；空值是 **`[]` 不是 `null`**。
 * - ⚠️ **PUT 是全量覆盖**：不传的字段被置 null。所以表单页拿真实 DTO 填表单
 *   （`*FormFromDto`），提交时再**整体**转 body（`*SaveReqFromForm`）——
 *   这一对函数就是"读全量 → 合并 → 整体 PUT"，断言里用真实 fixture 逐字段对过。
 * - `typeDesc` / `statusDesc` / `severityDesc` / `recordTypeDesc` 后端都算好了，优先用它们；
 *   这里只在缺失时兜底映射，**不另建一套真源**。
 * - 文案约束：不用 `—`、不用装饰性 `·`（分隔一律用中文顿号或空格）。
 */

/* ==================================================================
 * 1. 时间
 * ================================================================== */

function pad2 (n) {
  return n < 10 ? '0' + n : String(n)
}

/**
 * 解析健康模块的时间串。
 *
 * 后端给的是 `2026-08-01 09:30:00`（GMT+8 本地时间，**非 ISO**）。
 * iOS Safari / WebView 解析"空格分隔"会得到 Invalid Date，所以：
 *   1) 把第一个空格换成 `T`
 *   2) 只有日期（`yyyy-MM-dd`）时补 `T00:00:00`，避免被当 UTC 而差一天
 * @returns {Date|null} 解析不出来返回 null（调用方不许直接 `new Date()`）
 */
export function parseHealthTime (value) {
  if (value === null || value === undefined || value === '') return null
  if (value instanceof Date) return isNaN(value.getTime()) ? null : value
  let s = String(value).trim()
  if (!s) return null
  s = s.replace(' ', 'T')
  if (/^\d{4}-\d{2}-\d{2}$/.test(s)) s = s + 'T00:00:00'
  const d = new Date(s)
  return isNaN(d.getTime()) ? null : d
}

/** `2026-08-01 09:30`（去掉秒；年月日时分是健康记录里够用的精度） */
export function timeText (value) {
  const d = parseHealthTime(value)
  if (!d) return '时间未记录'
  return (
    d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate()) +
    ' ' + pad2(d.getHours()) + ':' + pad2(d.getMinutes())
  )
}

/** `2026-08-01`（curve.date 与宠物 birth 这种只有日期的字段用） */
export function dateText (value) {
  const d = parseHealthTime(value)
  if (!d) return '日期未记录'
  return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate())
}

/** 时间线用：同一年省掉年份，读起来短一点（`09-20 10:20`） */
export function shortTimeText (value) {
  const d = parseHealthTime(value)
  if (!d) return '时间未记录'
  const now = new Date()
  const md = pad2(d.getMonth() + 1) + '-' + pad2(d.getDate()) + ' ' + pad2(d.getHours()) + ':' + pad2(d.getMinutes())
  return d.getFullYear() === now.getFullYear() ? md : d.getFullYear() + '-' + md
}

/** 表单入参要的格式：`yyyy-MM-dd HH:mm:ss`（不传/格式错 → 后端 `B000407`） */
export function toHealthTime (date) {
  const d = date instanceof Date ? date : parseHealthTime(date)
  if (!d) return ''
  return (
    d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate()) +
    ' ' + pad2(d.getHours()) + ':' + pad2(d.getMinutes()) + ':' + pad2(d.getSeconds())
  )
}

/** 表单日期选择器要的 `yyyy-MM-dd` */
export function toDateOnly (date) {
  const d = date instanceof Date ? date : parseHealthTime(date)
  if (!d) return ''
  return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate())
}

/** 新建表单的默认时间：现在（后端要 `yyyy-MM-dd HH:mm:ss`） */
export function nowHealthTime () {
  return toHealthTime(new Date())
}

/** 新建表单的默认日期：今天 */
export function todayDateOnly () {
  return toDateOnly(new Date())
}

/* ==================================================================
 * 2. 空值与列表（逗号串 ↔ 数组）
 * ================================================================== */

/**
 * 出参数组 → 表单数组。**三种形状都要吃**：
 * - `[]`（实测：没有附件时的真实形状）
 * - `null` / `undefined`（同一批 DTO 里其它可选字段是 null）
 * - `"a,b"`（逗号串，万一后端换回 String）
 */
export function splitList (value) {
  if (value === null || value === undefined || value === '') return []
  if (Array.isArray(value)) {
    const out = []
    for (let i = 0; i < value.length; i++) {
      const s = value[i] === null || value[i] === undefined ? '' : String(value[i]).trim()
      if (s) out.push(s)
    }
    return out
  }
  return String(value)
    .split(',')
    .map(function (s) { return s.trim() })
    .filter(function (s) { return !!s })
}

/** 表单数组 → 入参逗号串（后端要 String）；空数组 → `''`（后端按空处理，不会变成 `"null"`） */
export function joinList (list) {
  const arr = splitList(list)
  return arr.join(',')
}

/** 空串一律转 null（可选字段缺失时后端回的是 null，不是什么都没有；别塞 `''` 进去） */
export function orNull (value) {
  if (value === null || value === undefined) return null
  const s = String(value).trim()
  return s ? s : null
}

/** 数字字段：`''` / null / NaN → null */
export function orNullNumber (value) {
  if (value === null || value === undefined || value === '') return null
  const n = Number(value)
  return isNaN(n) ? null : n
}

/* ==================================================================
 * 2b. 存量绝对 URL 的展示兜底（挂账 #67 的窄口子）
 *
 * 背景：附件地址是**上传时就把绝对 URL 写进库**的（`medical.attachments` / `symptom.images`），
 * 而下发 URL 走"按本次请求 Host 推导"（取址第 ② 级）。种子阶段用 `127.0.0.1:7000` 上传，
 * 库里就永久留下 `http://127.0.0.1:9000/...` —— 手机（另一个网段）解析 `127.0.0.1` 只会打到自己，
 * 于是历史附件必然裂图（根因与挂账 #67/#88 同源）。
 *
 * 这里只做**展示层**的一处窄归一化：把**明显不可达**的主机名换成"当前请求真正在用的 API 主机"，
 * path 与端口一律保留（对象存储是 9000，不是网关的 7000）。
 * ⚠️ **只重写 `127.0.0.1` 与 `localhost` 两个主机名**，其它 host 一律不动 ——
 *    免得把一个真正的配置错误（写错域名/IP）悄悄掩盖成"看起来能跑"。
 * ⚠️ 这是**兜底**，不是正解：正解是后端**只存 objectKey、下发时再拼 URL**；
 *    后端改成那样之后，本函数与 `displayAssetList` 应当**一并删除**。
 * ================================================================== */

/** 仅这两个主机名被认定"换了环境就必然不可达"（别往里加内网 IP，那是配置问题不是存量问题） */
const UNREACHABLE_ASSET_HOSTS = ['127.0.0.1', 'localhost']

/**
 * "当前请求用的 API 主机"解析器，由 `api/health.js` 在模块加载时注入。
 *
 * 为什么用**注入**而不是在这里 import：本文件被 `tools/check-health-phone.mjs` 原样复制到临时目录
 * 单独 import（手机端是 HBuilderX 工程、没有 package.json），任何相对 import 都会让那个 harness 挂掉；
 * 也因为**纯函数**才好断言。注入的是个**函数**，取值推迟到渲染那一刻 ——
 * 真机地址自愈（换 Wi-Fi / 换租约）之后，下一次渲染拿到的就是新主机，不会拿到启动时的旧快照。
 */
let displayHostResolver = null

/** 注册"当前 API 主机"解析器（`api/health.js` 调；harness 里不注册 = 拿不到主机 = 不归一化） */
export function setHealthDisplayHostResolver (fn) {
  displayHostResolver = typeof fn === 'function' ? fn : null
}

/** 当前 API 主机（不含协议与端口；拿不到时返回 `''`，调用方据此"宁可不改也不猜"） */
export function currentDisplayHost () {
  if (!displayHostResolver) return ''
  try {
    return apiHostName(displayHostResolver())
  } catch (e) {
    return ''
  }
}

/** `http://192.168.225.183:7000/miaowang-healthy/...` / `192.168.225.183:7000` → `192.168.225.183` */
function apiHostName (apiHost) {
  let s = apiHost === null || apiHost === undefined ? '' : String(apiHost).trim()
  if (!s) return ''
  s = s.replace(/^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//, '')
  s = s.split('/')[0].split('?')[0]
  if (!s || s.charAt(0) === '[') return '' // IPv6 字面量不在本次范围，不猜
  const colon = s.indexOf(':')
  if (colon >= 0) s = s.slice(0, colon)
  return s
}

/**
 * 存量绝对 URL → 当前可达 URL（**纯函数**，第二个参数显式传主机，便于断言）
 *
 * @param {string} url 库里存的地址（也可能已经是好的、或根本不是绝对 URL）
 * @param {string} [apiHost] 当前 API 主机（`192.168.225.183` / `192.168.225.183:7000` / 带协议都行）
 * @returns {string} 能改就改，**其它情况原样返回**（相对路径、非 http(s)、拿不到主机、其它 host）
 */
export function normalizeStoredAssetUrl (url, apiHost) {
  const raw = url === null || url === undefined ? '' : String(url).trim()
  if (!raw) return ''
  const m = /^(https?):\/\/([^/?#]+)([\s\S]*)$/i.exec(raw)
  if (!m) return raw // 相对路径 / 非 http(s)：不动
  const scheme = m[1]
  const authority = m[2]
  const rest = m[3]
  const hostPort = authority.split('@').pop() || '' // 去掉 userinfo（正常不会有，防御一下）
  if (!hostPort || hostPort.charAt(0) === '[') return raw // IPv6 字面量：不动
  let hostname = hostPort
  let port = ''
  const colon = hostPort.indexOf(':')
  if (colon >= 0) {
    hostname = hostPort.slice(0, colon)
    port = hostPort.slice(colon)
  }
  if (UNREACHABLE_ASSET_HOSTS.indexOf(hostname.toLowerCase()) < 0) return raw // 只认这两个
  const target = apiHostName(apiHost === undefined ? currentDisplayHost() : apiHost)
  if (!target) return raw // 拿不到当前主机 → 不猜
  // 只换主机名：协议、端口（对象存储的 9000）、path、query 全部保留
  return scheme + '://' + target + port + rest
}

/**
 * **展示用**的附件 / 图片列表：`splitList` + 存量地址归一化。
 *
 * ⚠️ 只给卡片/详情这类**只读展示**用。表单那条路（`medicalFormFromDto` / `symptomFormFromDto`）
 *    继续走 `splitList` 拿**库里的原值** —— 免得"看一眼编辑页再保存"就把浏览器自己的主机名写回库，
 *    那正是挂账 #67 的病灶（两端互相覆盖对方的地址）。
 */
export function displayAssetList (value, apiHost) {
  const host = apiHost === undefined ? currentDisplayHost() : apiHost
  return splitList(value).map(function (u) { return normalizeStoredAssetUrl(u, host) })
}

/* ==================================================================
 * 3. 逾期分档（逾期项只能自己算）
 * ================================================================== */

/**
 * `nextDueDays` → 分档（**这是首页「今日照护」的唯一口径**）
 *
 * | 输入 | key | 文案 |
 * |---|---|---|
 * | `null`（`nextDueAt` 为空） | `none` | 没有下次到期提醒 |
 * | `< 0` | `overdue` | 已逾期 10 天 |
 * | `=== 0` | `today` | 今天到期 |
 * | `1 ~ 7` | `soon` | 还有 5 天 |
 * | `> 7` | `later` | 还有 175 天 |
 *
 * ⚠️ `nextDueDays` 是**整数截断（向零）**且**可为负**：`-0.5 天` 会变成 `0`，
 * 所以 `0` 的文案是「今天到期」，**不能**说成"还没到期"。
 * @param {number|string|null} nextDueDays
 * @param {string} [nextDueAt] 仅用于文案里带出具体日期（可空）
 */
export function dueInfo (nextDueDays, nextDueAt) {
  const hasDue = nextDueAt !== null && nextDueAt !== undefined && nextDueAt !== ''
  if (nextDueDays === null || nextDueDays === undefined || nextDueDays === '') {
    return {
      key: 'none',
      days: null,
      label: hasDue ? '到期时间待确认' : '没有下次到期提醒',
      detail: hasDue ? '这条记录填了到期时间，但后端没算出天数，回档案页看看原始记录' : '',
      tone: 'muted'
    }
  }
  const n = Number(nextDueDays)
  if (isNaN(n)) {
    return { key: 'none', days: null, label: '没有下次到期提醒', detail: '', tone: 'muted' }
  }
  const day = Math.abs(n)
  if (n < 0) {
    return {
      key: 'overdue',
      days: n,
      label: '已逾期 ' + day + ' 天',
      detail: hasDue ? '原定 ' + dateText(nextDueAt) + ' 到期，还没补做' : '还没补做',
      tone: 'danger'
    }
  }
  if (n === 0) {
    return {
      key: 'today',
      days: 0,
      label: '今天到期',
      detail: hasDue ? '到期时间 ' + timeText(nextDueAt) : '不到 1 天',
      tone: 'accent'
    }
  }
  if (n <= 7) {
    return {
      key: 'soon',
      days: n,
      label: '还有 ' + n + ' 天',
      detail: hasDue ? '到期时间 ' + timeText(nextDueAt) : '',
      tone: 'accent'
    }
  }
  return {
    key: 'later',
    days: n,
    label: '还有 ' + n + ' 天',
    detail: hasDue ? '到期时间 ' + timeText(nextDueAt) : '',
    tone: 'ink'
  }
}

/**
 * 逾期 / 最紧急的照护项（**必须拉 `GET /{pid}/preventive` 全量后自己算**）
 *
 * 为什么不看 `overview.nextPreventive`：它源码是 `.ge(nextDueAt, now)`，**只给未来最近一条**，
 * 实测同一只宠物上"已逾期 10 天"与"2027 年到期"各一条时，它只回后者，逾期那条被完全排除。
 *
 * @param {object[]} rows `preventive` 列表（裸数组）
 * @param {object} [opts] `{ onlyOverdue }`
 * @returns {object[]} 按 `nextDueDays` **升序**（逾期最久的排最前）；`nextDueDays` 为 null 的排在最后
 */
export function sortedByDue (rows, opts) {
  const list = Array.isArray(rows) ? rows.slice() : []
  const onlyOverdue = !!(opts && opts.onlyOverdue)
  const withDays = []
  const withoutDays = []
  for (let i = 0; i < list.length; i++) {
    const r = list[i] || {}
    const raw = r.nextDueDays
    if (raw === null || raw === undefined || raw === '') {
      if (!onlyOverdue) withoutDays.push(r)
      continue
    }
    const n = Number(raw)
    if (isNaN(n)) {
      if (!onlyOverdue) withoutDays.push(r)
      continue
    }
    if (onlyOverdue && n >= 0) continue
    withDays.push({ row: r, days: n })
  }
  withDays.sort(function (a, b) { return a.days - b.days })
  return withDays.map(function (x) { return x.row }).concat(withoutDays)
}

/** 只要逾期项（`nextDueDays < 0`），按逾期最久在前 */
export function overdueItems (rows) {
  return sortedByDue(rows, { onlyOverdue: true })
}

/** 最紧急的一条（逾期最久 → 今天 → 最快到期）；全都没有到期时间时返回 null */
export function urgentItem (rows) {
  const sorted = sortedByDue(rows)
  for (let i = 0; i < sorted.length; i++) {
    const raw = sorted[i] && sorted[i].nextDueDays
    if (raw !== null && raw !== undefined && raw !== '') return sorted[i]
  }
  return sorted.length ? sorted[0] : null
}

/* ==================================================================
 * 4. 枚举与人话
 * ================================================================== */

/** 疫苗 + 驱虫合并表；⚠️ `type` 非法值时后端**静默忽略过滤**（返回全部） */
export const PREVENTIVE_TYPES = [
  { value: 'VACCINE', label: '疫苗' },
  { value: 'DEWORM_IN', label: '体内驱虫' },
  { value: 'DEWORM_OUT', label: '体外驱虫' }
]

/** 时间线的六类（`timeline?types` 的合法值；⚠️ 非法值后端**直接报 `A000001`**） */
export const RECORD_TYPES = [
  { value: 'preventive', label: '疫苗与驱虫' },
  { value: 'medical', label: '就诊' },
  { value: 'medication', label: '用药' },
  { value: 'allergy', label: '过敏' },
  { value: 'symptom', label: '症状' },
  { value: 'weight', label: '体重' }
]

/** 用药状态：0 用药中 / 1 已结束 / 2 已停用（`status` 不传时后端由 `endAt` 推断） */
export const MEDICATION_STATUSES = [
  { value: 0, label: '用药中' },
  { value: 1, label: '已结束' },
  { value: 2, label: '已停用' }
]

/** 过敏 / 症状的严重度：1 轻度 / 2 中度 / 3 重度 */
export const SEVERITY_OPTIONS = [
  { value: 1, label: '轻度' },
  { value: 2, label: '中度' },
  { value: 3, label: '重度' }
]

/** 体重曲线的时间窗（后端钳到 [1,24] 个月） */
export const CURVE_MONTH_OPTIONS = [1, 3, 6, 12, 24]

export function typeLabel (value) {
  const list = PREVENTIVE_TYPES
  for (let i = 0; i < list.length; i++) {
    if (list[i].value === value) return list[i].label
  }
  return value ? String(value) : '未分类'
}

export function recordTypeLabel (value) {
  const list = RECORD_TYPES
  for (let i = 0; i < list.length; i++) {
    if (list[i].value === value) return list[i].label
  }
  return value ? String(value) : '记录'
}

/**
 * 严重度人话：**优先用后端算好的 `severityDesc`**（实测 1/2/3 ↔ 轻度/中度/重度），
 * 缺失时才用本地映射兜底。
 */
export function severityText (severity, severityDesc) {
  const desc = severityDesc === null || severityDesc === undefined ? '' : String(severityDesc).trim()
  if (desc) return desc
  const n = Number(severity)
  if (n === 1) return '轻度'
  if (n === 2) return '中度'
  if (n === 3) return '重度'
  return '未分级'
}

/** 严重度的展示档（模板给标签上色用；不做严格 `=== '重度'` 这种脆弱判断） */
export function severityTone (severity) {
  const n = Number(severity)
  if (n >= 3) return 'high'
  if (n === 2) return 'mid'
  if (n === 1) return 'low'
  return 'none'
}

export function medicationStatusText (status, statusDesc) {
  const desc = statusDesc === null || statusDesc === undefined ? '' : String(statusDesc).trim()
  if (desc) return desc
  const n = Number(status)
  if (n === 0) return '用药中'
  if (n === 1) return '已结束'
  if (n === 2) return '已停用'
  return '状态未知'
}

export function medicationStatusTone (status) {
  const n = Number(status)
  if (n === 0) return 'active'
  if (n === 2) return 'stopped'
  if (n === 1) return 'ended'
  return 'none'
}

/**
 * 严重度 → 卡片标签的配色档。
 * 说明：`severityTone` 是语义档（high/mid/low），标签 class 是视觉档（danger/accent/brand/muted），
 * 两者不混用（模板里 `'tag--' + tone` 拼字符串，拼错就是一个没样式的标签）。
 */
export function severityTagTone (severity) {
  const t = severityTone(severity)
  if (t === 'high') return 'danger'
  if (t === 'mid') return 'accent'
  if (t === 'low') return 'brand'
  return 'muted'
}

/** 用药状态 → 标签配色档 */
export function medicationTagTone (status) {
  const n = Number(status)
  if (n === 0) return 'brand'
  if (n === 2) return 'danger'
  return 'muted'
}

/** 体重：后端是 DECIMAL，JSON 里是数字（实测 `4.25`）→ 统一保留两位 */
export function weightText (kg) {
  const n = Number(kg)
  if (kg === null || kg === undefined || kg === '' || isNaN(n)) return '未记录'
  return n.toFixed(2) + ' kg'
}

/** 费用：`cost` 可为 null（实测 `395.00`）；空值不给 `¥` 前缀，免得像"0 元" */
export function costText (cost) {
  const n = Number(cost)
  if (cost === null || cost === undefined || cost === '' || isNaN(n)) return ''
  return '¥' + n.toFixed(2)
}

/**
 * 体重变化人话：口径 = 最新一次 − 30 天前的基线（实测 4.35 → 4.25 = `-0.10`）
 * ⚠️ **没有"≤30 天前"的基线记录时后端回 `null`**（不是 0）→ 文案要说清"攒够记录才有"，
 *    别写成"没有变化"（那是在编数据）。
 */
export function weightChangeText (kg) {
  if (kg === null || kg === undefined || kg === '') {
    return '还没有 30 天前的基线记录，攒够两次就能看变化'
  }
  const n = Number(kg)
  if (isNaN(n)) return '还没有 30 天前的基线记录，攒够两次就能看变化'
  const abs = Math.abs(n).toFixed(2)
  if (n === 0) return '和 30 天前持平'
  return n < 0 ? '比 30 天前轻 ' + abs + ' kg' : '比 30 天前重 ' + abs + ' kg'
}

export function weightChangeTone (kg) {
  if (kg === null || kg === undefined || kg === '') return 'none'
  const n = Number(kg)
  if (isNaN(n)) return 'none'
  if (n === 0) return 'flat'
  return n < 0 ? 'down' : 'up'
}

/* ==================================================================
 * 5. 卡片：DTO → 模板字段（模板里绝不出现 null / undefined）
 * ================================================================== */

/** 通用文本兜底：null 不能被渲染出来（后端可选字段一律回 null） */
export function text (value, fallback) {
  const s = value === null || value === undefined ? '' : String(value).trim()
  if (s) return s
  return fallback === undefined ? '' : fallback
}

/** 疫苗 / 驱虫一条 → 模板字段 */
export function preventiveCard (dto) {
  const r = dto || {}
  const due = dueInfo(r.nextDueDays, r.nextDueAt)
  return {
    key: 'preventive-' + r.id,
    id: r.id,
    typeValue: r.type || '',
    typeLabel: text(r.typeDesc, typeLabel(r.type)),
    itemName: text(r.itemName, '未填写项目名'),
    happenedText: timeText(r.happenedAt),
    nextDueText: r.nextDueAt ? timeText(r.nextDueAt) : '没有设置下次到期时间',
    dueKey: due.key,
    dueLabel: due.label,
    dueDetail: due.detail,
    dueTone: due.tone,
    isOverdue: due.key === 'overdue',
    hospital: text(r.hospital),
    hasHospital: !!text(r.hospital),
    batchNo: text(r.batchNo),
    hasBatchNo: !!text(r.batchNo),
    remark: text(r.remark),
    hasRemark: !!text(r.remark)
  }
}

/**
 * 就诊 / 病史一条 → 模板字段（`attachments` 出参是数组，空是 `[]`）
 *
 * 附件地址走 `displayAssetList`：只管**展示**，把库里存量的 `127.0.0.1` / `localhost` 换成本机可达主机
 * （见上面 2b 节；表单与写接口一律不经过它）。
 */
export function medicalCard (dto) {
  const r = dto || {}
  const attachments = displayAssetList(r.attachments)
  return {
    key: 'medical-' + r.id,
    id: r.id,
    visitText: timeText(r.visitAt),
    diagnosis: text(r.diagnosis, '未填写诊断'),
    treatment: text(r.treatment),
    hasTreatment: !!text(r.treatment),
    hospital: text(r.hospital),
    hasHospital: !!text(r.hospital),
    doctor: text(r.doctor),
    hasDoctor: !!text(r.doctor),
    costText: costText(r.cost),
    hasCost: !!costText(r.cost),
    attachments: attachments,
    attachmentCount: attachments.length,
    hasAttachments: attachments.length > 0,
    remark: text(r.remark),
    hasRemark: !!text(r.remark)
  }
}

/** 用药一条 → 模板字段（状态优先用后端 `statusDesc`） */
export function medicationCard (dto) {
  const r = dto || {}
  const dosage = text(r.dosage)
  const frequency = text(r.frequency)
  const parts = []
  if (dosage) parts.push('每次 ' + dosage)
  if (frequency) parts.push(frequency)
  return {
    key: 'medication-' + r.id,
    id: r.id,
    medicineName: text(r.medicineName, '未填写药名'),
    dosage: dosage,
    hasDosage: !!dosage,
    frequency: frequency,
    hasFrequency: !!frequency,
    /** 卡片副标题：用法 + 频次（两个都空时给一句实话，不编） */
    subText: parts.length ? parts.join('，') : '用法用量没填',
    startText: timeText(r.startAt),
    endText: r.endAt ? timeText(r.endAt) : '',
    hasEnd: !!r.endAt,
    statusValue: r.status === null || r.status === undefined ? null : Number(r.status),
    statusLabel: medicationStatusText(r.status, r.statusDesc),
    statusTone: medicationStatusTone(r.status),
    tagTone: medicationTagTone(r.status),
    isActive: Number(r.status) === 0,
    medicalId: r.medicalId === null || r.medicalId === undefined ? null : Number(r.medicalId),
    medicalText: r.medicalId === null || r.medicalId === undefined ? '' : '关联就诊 #' + r.medicalId,
    remark: text(r.remark),
    hasRemark: !!text(r.remark)
  }
}

/** 过敏一条 → 模板字段（⚠️ 过敏表**没有发生时间列**，按录入时间算的锚点不在 DTO 里） */
export function allergyCard (dto) {
  const r = dto || {}
  const reaction = text(r.reaction)
  return {
    key: 'allergy-' + r.id,
    id: r.id,
    allergen: text(r.allergen, '未填写过敏原'),
    severityValue: r.severity === null || r.severity === undefined ? null : Number(r.severity),
    severityLabel: severityText(r.severity, r.severityDesc),
    severityTone: severityTone(r.severity),
    tagTone: severityTagTone(r.severity),
    reaction: reaction,
    hasReaction: !!reaction,
    /** 卡片副标题：反应描述（没写时说明"没写"，不显示空白行） */
    subText: reaction || '没写具体反应',
    note: text(r.note),
    hasNote: !!text(r.note)
  }
}

/**
 * 症状一条 → 模板字段（`symptomId` 有值时名字来自知识库，实测会被覆盖）
 *
 * `images` 与就诊的 `attachments` 同口径：出参数组、入参逗号串，展示走同一条存量地址兜底。
 */
export function symptomCard (dto) {
  const r = dto || {}
  const images = displayAssetList(r.images)
  const fromLibrary = r.symptomId !== null && r.symptomId !== undefined && r.symptomId !== ''
  return {
    key: 'symptom-' + r.id,
    id: r.id,
    symptomName: text(r.symptomName, '未填写症状名'),
    symptomId: fromLibrary ? Number(r.symptomId) : null,
    fromLibrary: fromLibrary,
    sourceLabel: fromLibrary ? '选自症状库' : '自己填写的',
    description: text(r.description),
    hasDescription: !!text(r.description),
    severityValue: r.severity === null || r.severity === undefined ? null : Number(r.severity),
    severityLabel: severityText(r.severity, r.severityDesc),
    severityTone: severityTone(r.severity),
    tagTone: severityTagTone(r.severity),
    occurredText: timeText(r.occurredAt),
    images: images,
    imageCount: images.length,
    hasImages: images.length > 0,
    remark: text(r.remark),
    hasRemark: !!text(r.remark)
  }
}

/** 体重一条 → 模板字段（⚠️ 实测 id 是 111/112，别假设从 1 开始） */
export function weightCard (dto) {
  const r = dto || {}
  return {
    key: 'weight-' + r.id,
    id: r.id,
    weightText: weightText(r.weightKg),
    weightKg: r.weightKg === null || r.weightKg === undefined ? null : Number(r.weightKg),
    measuredText: timeText(r.measuredAt),
    note: text(r.note),
    hasNote: !!text(r.note)
  }
}

/**
 * 时间线一条 → 模板字段
 *
 * ⚠️ `recordId` 是**各类表各自的 id**（实测会重复：`allergy#2, allergy#1, weight#111, ...`）
 * → `v-for` 的 key 必须用 `recordType + recordId`，否则同 id 的条目会被 Vue 复用错。
 * ⚠️ 只有 `allergy` / `symptom` 的 `severity` 有值，其余四类是 `null`。
 * ⚠️ `allergy` 的 `happenedAt` 是**录入时间**（过敏表没有时间列）。
 */
export function timelineCard (item) {
  const r = item || {}
  const hasSeverity = r.severity !== null && r.severity !== undefined && r.severity !== ''
  return {
    key: String(r.recordType || 'unknown') + '#' + String(r.recordId),
    recordType: r.recordType || '',
    recordTypeLabel: text(r.recordTypeDesc, recordTypeLabel(r.recordType)),
    recordId: r.recordId,
    title: text(r.title, '未命名记录'),
    summary: text(r.summary),
    hasSummary: !!text(r.summary),
    happenedText: shortTimeText(r.happenedAt),
    happenedFullText: timeText(r.happenedAt),
    severityLabel: hasSeverity ? severityText(r.severity, null) : '',
    severityTone: hasSeverity ? severityTone(r.severity) : 'none',
    hasSeverity: hasSeverity
  }
}

/** 症状选项一条 → 模板字段（猫 52 / 狗 46；`description` 是可能病因、`remark` 是提醒，都有真值） */
export function symptomOptionCard (dto) {
  const r = dto || {}
  return {
    key: 'option-' + r.symptomId,
    symptomId: Number(r.symptomId),
    name: text(r.name, '未命名症状'),
    description: text(r.description),
    hasDescription: !!text(r.description),
    remark: text(r.remark),
    hasRemark: !!text(r.remark),
    matched: false
  }
}

/**
 * 宠物 `species`（**中文大类**，实测是 `猫`/`狗`，不是 `CAT`）→ 症状选项的 `species` 编号。
 *
 * ⚠️ 两个 `species` 不是一个东西：宠物接口存中文大类，`GET /health/symptom/options?species=`
 * 要的是 **1 猫 / 2 狗**。这里做唯一一次映射，页面别各写一份。
 * 猫 52 条、狗 46 条；其它物种（蛇/兔/鸟…）后端没有选项表，兜底给猫并让页面说明这件事。
 */
export function symptomSpeciesCode (speciesName) {
  const s = speciesName === null || speciesName === undefined ? '' : String(speciesName).trim()
  if (s.indexOf('狗') >= 0) return 2
  return 1
}

/**
 * 症状选项搜索过滤（52 条不能靠长列表硬翻；**纯函数**，页面在 methods 里按关键字调）
 * @param {object[]} options 原始 DTO 数组
 * @param {string} keyword
 */
export function filterSymptomOptions (options, keyword) {
  const kw = keyword === null || keyword === undefined ? '' : String(keyword).trim().toLowerCase()
  const list = Array.isArray(options) ? options : []
  const out = []
  for (let i = 0; i < list.length; i++) {
    const r = list[i] || {}
    const name = text(r.name)
    const desc = text(r.description)
    const remark = text(r.remark)
    if (
      !kw ||
      name.toLowerCase().indexOf(kw) >= 0 ||
      desc.toLowerCase().indexOf(kw) >= 0 ||
      remark.toLowerCase().indexOf(kw) >= 0
    ) {
      const card = symptomOptionCard(r)
      card.matched = !!kw
      out.push(card)
    }
  }
  return out
}

/* ==================================================================
 * 6. 概览
 * ================================================================== */

/** `recordCounts` 的 6 个 key 恒在（某类为 0 也是 `0`），但缺了也不能炸 */
export const COUNT_KEYS = ['preventive', 'medical', 'medication', 'allergy', 'symptom', 'weight']

/**
 * 概览 → 模板字段
 *
 * ⚠️ `nextPreventive` **不含逾期项**（实测），所以页面必须另外拉 `preventive` 全量，
 * 用 `overdueItems()` 自己算「今日照护」。这里不把两套口径混着用。
 * ⚠️ `weightChangeKg` 可为 `null`；`recordLimit = -1` 表示不限条数。
 */
export function overviewView (dto, extra) {
  const o = dto || {}
  const counts = o.recordCounts || {}
  const rows = []
  for (let i = 0; i < COUNT_KEYS.length; i++) {
    const key = COUNT_KEYS[i]
    const n = Number(counts[key])
    const value = isNaN(n) ? 0 : n
    rows.push({
      key: key,
      label: recordTypeLabel(key),
      value: value,
      countText: value + ' 条',
      empty: value === 0
    })
  }
  const limit = o.recordLimit === null || o.recordLimit === undefined ? null : Number(o.recordLimit)
  const reached = Array.isArray(o.limitReachedTypes) ? o.limitReachedTypes : []
  const reachedLabels = reached.map(recordTypeLabel)
  const e = extra || {}
  /** ⚠️ 逾期只能自己算（`overview.nextPreventive` 排除了逾期项）：这里把口径固定下来 */
  const overdue = overdueItems(e.overdueRows)
  return {
    pid: o.pid,
    petName: text(o.petName, '这只宠物'),
    species: text(o.species),
    totalRecords: Number(o.totalRecords) || 0,
    totalText: (Number(o.totalRecords) || 0) + ' 条记录',
    counts: rows,
    recordLimit: limit,
    /** `-1` = 不限（不是"减一条"）；`null` = 后端没给（权益服务可能拿不到） */
    limitText:
      limit === null
        ? '条数上限这次没读到（会员权益服务可能暂时不可用）'
        : limit < 0
          ? '当前档位不限记录条数'
          : '当前档位每类最多 ' + limit + ' 条',
    limitReachedLabels: reachedLabels,
    hasLimitWarning: reachedLabels.length > 0,
    limitWarningText: reachedLabels.length
      ? reachedLabels.join('、') + ' 已经记满，开通会员可以继续记录'
      : '',
    latestWeight: o.latestWeight ? weightCard(o.latestWeight) : null,
    hasLatestWeight: !!o.latestWeight,
    weightChangeText: weightChangeText(o.weightChangeKg),
    weightChangeTone: weightChangeTone(o.weightChangeKg),
    allergies: Array.isArray(o.allergies) ? o.allergies.map(allergyCard) : [],
    activeMedications: Array.isArray(o.activeMedications) ? o.activeMedications.map(medicationCard) : [],
    /** 付费墙开关（实测本账号两个都是 false）—— 入口照给，点了给升级提示，不当报错 */
    exportAllowed: !!o.exportAllowed,
    aiAnalysisAllowed: !!o.aiAnalysisAllowed,
    /** 逾期（页面自己算出来的，见 overdueItems）：这里只负责把口径带上 */
    overdueCount: overdue.length,
    overdueLabels: overdue.map(function (r) { return text(r.itemName, '未填写项目名') }),
    urgent: e.urgentRow ? preventiveCard(e.urgentRow) : null
  }
}

/* ==================================================================
 * 7. 体重曲线：rpx 视图手写折线（**不引 npm / 不用 canvas**）
 * ================================================================== */

/**
 * 曲线点 → 可直接渲染的 rpx 几何
 *
 * 坐标系就是 `view` 的 rpx 盒子：x 向右、y 向下，直接写到 `style` 上。
 * 折线用"若干条旋转过的细 view"拼（`segments`），点用绝对定位的圆点（`dots`）。
 * 单点时不给折线（两点才成线），但仍然给圆点与数值。
 *
 * @param {object[]} points `[{date, weightKg, count}]`（`date` 是 `yyyy-MM-dd`，升序）
 * @param {object} [opts] `{ width, height, padLeft, padRight, padTop, padBottom }` 单位 rpx
 */
export function curveView (points, opts) {
  const o = opts || {}
  const width = Number(o.width) || 630
  const height = Number(o.height) || 320
  const padLeft = o.padLeft === undefined ? 40 : Number(o.padLeft)
  const padRight = o.padRight === undefined ? 40 : Number(o.padRight)
  const padTop = o.padTop === undefined ? 48 : Number(o.padTop)
  const padBottom = o.padBottom === undefined ? 64 : Number(o.padBottom)

  const list = []
  const raw = Array.isArray(points) ? points : []
  for (let i = 0; i < raw.length; i++) {
    const p = raw[i] || {}
    const kg = Number(p.weightKg)
    if (isNaN(kg)) continue
    list.push({
      date: p.date,
      dateLabel: dateText(p.date),
      weightKg: kg,
      weightText: kg.toFixed(2) + ' kg',
      count: Number(p.count) || 0,
      countText: (Number(p.count) || 0) > 1 ? '当天测了 ' + (Number(p.count) || 0) + ' 次' : ''
    })
  }

  if (!list.length) {
    return {
      hasPoints: false,
      count: 0,
      width: width,
      height: height,
      points: [],
      segments: [],
      dots: [],
      minText: '',
      maxText: '',
      spanText: '',
      emptyText: '这个时间窗里没有体重记录，换一个月份范围或先记一次体重'
    }
  }

  let min = list[0].weightKg
  let max = list[0].weightKg
  for (let i = 1; i < list.length; i++) {
    if (list[i].weightKg < min) min = list[i].weightKg
    if (list[i].weightKg > max) max = list[i].weightKg
  }
  // 上下各留一点余量，免得点贴在边框上；全部相等时钳成一条水平线
  const span = max - min
  const pad = span === 0 ? 0.5 : span * 0.18
  const lo = min - pad
  const hi = max + pad
  const innerW = Math.max(1, width - padLeft - padRight)
  const innerH = Math.max(1, height - padTop - padBottom)

  const dots = []
  for (let i = 0; i < list.length; i++) {
    const ratio = list.length === 1 ? 0.5 : i / (list.length - 1)
    const x = padLeft + innerW * ratio
    const y = padTop + innerH * (1 - (list[i].weightKg - lo) / (hi - lo))
    dots.push({
      index: i,
      left: Math.round(x * 100) / 100,
      top: Math.round(y * 100) / 100,
      centerY: Math.round((y - 14) * 100) / 100,
      dateLabel: list[i].dateLabel,
      weightText: list[i].weightText,
      countText: list[i].countText,
      /** 端点 / 峰值点上的数值标签：只给首尾与最大最小值，免得 24 个点糊成一片 */
      showLabel:
        list.length <= 6 ||
        i === 0 ||
        i === list.length - 1 ||
        list[i].weightKg === max ||
        list[i].weightKg === min
    })
  }

  const segments = []
  for (let i = 0; i < dots.length - 1; i++) {
    const a = dots[i]
    const b = dots[i + 1]
    const dx = b.left - a.left
    const dy = b.top - a.top
    const len = Math.sqrt(dx * dx + dy * dy)
    segments.push({
      key: 'seg-' + i,
      left: a.left,
      top: a.top,
      width: Math.round(len * 100) / 100,
      rotate: Math.round((Math.atan2(dy, dx) * 180) / Math.PI * 100) / 100
    })
  }

  // 底部日期刻度：最多 4 个，均匀取（保证首尾一定在）
  const ticks = []
  const tickCount = Math.min(4, dots.length)
  for (let i = 0; i < tickCount; i++) {
    const idx = tickCount === 1 ? 0 : Math.round((i * (dots.length - 1)) / (tickCount - 1))
    ticks.push({ key: 'tick-' + idx, left: dots[idx].left, label: dots[idx].dateLabel })
  }

  const first = list[0]
  const last = list[list.length - 1]
  const delta = last.weightKg - first.weightKg
  return {
    hasPoints: true,
    count: list.length,
    width: width,
    height: height,
    points: list,
    dots: dots,
    segments: segments,
    ticks: ticks,
    minText: min.toFixed(2) + ' kg',
    maxText: max.toFixed(2) + ' kg',
    spanText: '区间最低 ' + min.toFixed(2) + ' kg，最高 ' + max.toFixed(2) + ' kg',
    firstText: first.weightText + '（' + first.dateLabel + '）',
    lastText: last.weightText + '（' + last.dateLabel + '）',
    deltaText:
      list.length < 2
        ? '只有一条记录，还画不出变化'
        : '这段区间' + (delta === 0 ? '没有变化' : delta < 0 ? '轻了 ' + Math.abs(delta).toFixed(2) + ' kg' : '重了 ' + delta.toFixed(2) + ' kg'),
    deltaTone: list.length < 2 ? 'none' : delta === 0 ? 'flat' : delta < 0 ? 'down' : 'up',
    emptyText: ''
  }
}

/* ==================================================================
 * 8. 表单：DTO ↔ 表单 ↔ 入参（PUT 全量覆盖的唯一实现）
 * ================================================================== */

/**
 * 六个表单的"从 DTO 填表"。
 * `dto` 传 `null` 就是**新建**（给默认值：日期取现在）。
 *
 * ⚠️ 这就是"读全量"那一步：它把 DTO 里**每一个可写字段**都搬进表单，
 * 页面改完再整体 PUT，才不会因为漏字段被后端置 null。
 */
export function preventiveFormFromDto (dto) {
  const r = dto || {}
  return {
    id: r.id === undefined ? null : r.id,
    type: r.type || 'VACCINE',
    itemName: r.itemName || '',
    happenedAt: r.happenedAt || (dto ? '' : nowHealthTime()),
    nextDueAt: r.nextDueAt || '',
    hospital: r.hospital || '',
    batchNo: r.batchNo || '',
    remark: r.remark || ''
  }
}

export function medicalFormFromDto (dto) {
  const r = dto || {}
  return {
    id: r.id === undefined ? null : r.id,
    visitAt: r.visitAt || (dto ? '' : nowHealthTime()),
    hospital: r.hospital || '',
    doctor: r.doctor || '',
    diagnosis: r.diagnosis || '',
    treatment: r.treatment || '',
    cost: r.cost === null || r.cost === undefined ? '' : String(r.cost),
    /** 出参是数组 → 表单里就是数组（提交时 joinList 成逗号串） */
    attachments: splitList(r.attachments),
    remark: r.remark || ''
  }
}

export function medicationFormFromDto (dto) {
  const r = dto || {}
  return {
    id: r.id === undefined ? null : r.id,
    medicalId: r.medicalId === null || r.medicalId === undefined ? '' : String(r.medicalId),
    medicineName: r.medicineName || '',
    dosage: r.dosage || '',
    frequency: r.frequency || '',
    startAt: r.startAt || (dto ? '' : nowHealthTime()),
    endAt: r.endAt || '',
    /** 空串 = 不指定，交给后端按 `endAt` 推断（新建时的默认；编辑时用 DTO 的真值） */
    status: r.status === null || r.status === undefined ? '' : String(r.status),
    remark: r.remark || ''
  }
}

export function allergyFormFromDto (dto) {
  const r = dto || {}
  return {
    id: r.id === undefined ? null : r.id,
    allergen: r.allergen || '',
    severity: r.severity === null || r.severity === undefined ? '1' : String(r.severity),
    reaction: r.reaction || '',
    note: r.note || ''
  }
}

export function symptomFormFromDto (dto) {
  const r = dto || {}
  const fromLibrary = r.symptomId !== null && r.symptomId !== undefined && r.symptomId !== ''
  return {
    id: r.id === undefined ? null : r.id,
    /** true = 用知识库选项（提交 symptomId，后端会用库里的名字覆盖自填名） */
    useOption: fromLibrary,
    symptomId: fromLibrary ? String(r.symptomId) : '',
    symptomName: r.symptomName || '',
    description: r.description || '',
    severity: r.severity === null || r.severity === undefined ? '1' : String(r.severity),
    occurredAt: r.occurredAt || (dto ? '' : nowHealthTime()),
    images: splitList(r.images),
    remark: r.remark || ''
  }
}

export function weightFormFromDto (dto) {
  const r = dto || {}
  return {
    id: r.id === undefined ? null : r.id,
    weightKg: r.weightKg === null || r.weightKg === undefined ? '' : String(r.weightKg),
    measuredAt: r.measuredAt || (dto ? '' : nowHealthTime()),
    note: r.note || ''
  }
}

/* ---- 表单 → 入参（**字段名逐字照 DTO**，可选空值一律 null 而不是空串） ---- */

export function preventiveSaveReqFromForm (form) {
  const f = form || {}
  return {
    type: orNull(f.type),
    itemName: orNull(f.itemName),
    happenedAt: orNull(f.happenedAt),
    nextDueAt: orNull(f.nextDueAt),
    hospital: orNull(f.hospital),
    batchNo: orNull(f.batchNo),
    remark: orNull(f.remark)
  }
}

export function medicalSaveReqFromForm (form) {
  const f = form || {}
  return {
    visitAt: orNull(f.visitAt),
    hospital: orNull(f.hospital),
    doctor: orNull(f.doctor),
    diagnosis: orNull(f.diagnosis),
    treatment: orNull(f.treatment),
    cost: orNullNumber(f.cost),
    /** ⚠️ 入参是**逗号串**（String），不是数组；空则是 `''` */
    attachments: joinList(f.attachments),
    remark: orNull(f.remark)
  }
}

export function medicationSaveReqFromForm (form) {
  const f = form || {}
  return {
    medicalId: orNullNumber(f.medicalId),
    medicineName: orNull(f.medicineName),
    dosage: orNull(f.dosage),
    frequency: orNull(f.frequency),
    startAt: orNull(f.startAt),
    endAt: orNull(f.endAt),
    /** null = 让后端按 `endAt` 推断（`endAt==null` → 0 用药中，否则 1 已结束） */
    status: orNullNumber(f.status),
    remark: orNull(f.remark)
  }
}

export function allergySaveReqFromForm (form) {
  const f = form || {}
  return {
    allergen: orNull(f.allergen),
    severity: orNullNumber(f.severity),
    reaction: orNull(f.reaction),
    note: orNull(f.note)
  }
}

/**
 * 症状入参
 *
 * ⚠️ **`symptomId` 会覆盖 `symptomName`**（POST/PUT 都实测成立）→ 选了选项就不要指望自填名生效，
 *    所以走选项时**主动把 `symptomName` 置 null**（诚实，不给用户"我填的名字被吃了"的错觉）。
 * ⚠️ 非法 `symptomId` → `B000409`（不会退化成用 `symptomName`）；两个都不给 → `B000407`。
 */
export function symptomSaveReqFromForm (form) {
  const f = form || {}
  const useOption = !!f.useOption && orNullNumber(f.symptomId) !== null
  return {
    symptomId: useOption ? orNullNumber(f.symptomId) : null,
    symptomName: useOption ? null : orNull(f.symptomName),
    description: orNull(f.description),
    severity: orNullNumber(f.severity),
    occurredAt: orNull(f.occurredAt),
    images: joinList(f.images),
    remark: orNull(f.remark)
  }
}

export function weightSaveReqFromForm (form) {
  const f = form || {}
  return {
    weightKg: orNullNumber(f.weightKg),
    measuredAt: orNull(f.measuredAt),
    note: orNull(f.note)
  }
}

/* ---- 前端先拦一道（后端也会拦，但 B000407 太笼统，用户看不出缺哪个） ---- */

export function validatePreventiveForm (form) {
  const f = form || {}
  if (!orNull(f.type)) return '请选择类型（疫苗 / 体内驱虫 / 体外驱虫）'
  if (!orNull(f.itemName)) return '请填写项目名，例如「猫三联」「大宠爱」'
  if (!orNull(f.happenedAt)) return '请选择这次的时间'
  if (!parseHealthTime(f.happenedAt)) return '时间格式不对，重新选一次'
  if (orNull(f.nextDueAt) && !parseHealthTime(f.nextDueAt)) return '下次到期时间格式不对，重新选一次'
  return ''
}

export function validateMedicalForm (form) {
  const f = form || {}
  if (!orNull(f.visitAt)) return '请选择就诊时间'
  if (!parseHealthTime(f.visitAt)) return '就诊时间格式不对，重新选一次'
  if (!orNull(f.diagnosis)) return '请填写诊断结论（后端要求必填）'
  if (orNull(f.cost) !== null) {
    const n = Number(f.cost)
    if (isNaN(n) || n < 0) return '费用要填数字，不填就留空'
  }
  return ''
}

export function validateMedicationForm (form) {
  const f = form || {}
  if (!orNull(f.medicineName)) return '请填写药名'
  if (!orNull(f.startAt)) return '请选择开始用药时间'
  if (!parseHealthTime(f.startAt)) return '开始时间格式不对，重新选一次'
  if (orNull(f.endAt) && !parseHealthTime(f.endAt)) return '结束时间格式不对，重新选一次'
  const st = orNullNumber(f.status)
  if (st !== null && [0, 1, 2].indexOf(st) < 0) return '用药状态只能是「用药中 / 已结束 / 已停用」'
  return ''
}

export function validateAllergyForm (form) {
  const f = form || {}
  if (!orNull(f.allergen)) return '请填写过敏原，例如「牛奶」「尘螨」'
  const sv = orNullNumber(f.severity)
  if (sv !== null && [1, 2, 3].indexOf(sv) < 0) return '严重度只能是「轻度 / 中度 / 重度」'
  return ''
}

export function validateSymptomForm (form) {
  const f = form || {}
  if (!orNull(f.occurredAt)) return '请选择症状发生时间'
  if (!parseHealthTime(f.occurredAt)) return '症状时间格式不对，重新选一次'
  if (f.useOption) {
    if (orNullNumber(f.symptomId) === null) return '选「从症状库选」就要挑一条症状，或者改成自己填写'
  } else if (!orNull(f.symptomName)) {
    return '请填写症状名，或者改成从症状库里选一条'
  }
  const sv = orNullNumber(f.severity)
  if (sv !== null && [1, 2, 3].indexOf(sv) < 0) return '严重度只能是「轻度 / 中度 / 重度」'
  return ''
}

export function validateWeightForm (form) {
  const f = form || {}
  if (!orNull(f.measuredAt)) return '请选择测量时间'
  if (!parseHealthTime(f.measuredAt)) return '测量时间格式不对，重新选一次'
  const n = orNullNumber(f.weightKg)
  if (n === null) return '请填写体重（kg）'
  if (n < 0.05 || n > 200) return '体重需在 0.05 ~ 200 kg 之间'
  return ''
}

/* ==================================================================
 * 9. 错误码 → 人话
 * ================================================================== */

/** 付费墙错误码：**这是正常状态**，不是故障，调用方要给升级入口而不是"重试" */
export const PAYWALL_CODES = ['B000404', 'B000411']

/** 判断是不是"当前档位不支持"（付费墙）；调用方据此切到升级提示分支 */
export function isPaywall (e) {
  const code = e && e.code ? String(e.code) : ''
  return PAYWALL_CODES.indexOf(code) >= 0
}

/**
 * 健康模块错误码 → 人话（唯一真源：`HealthyErrorCodeEnum` 全表 + 实测触发表）
 *
 * 注意三条容易写错的：
 * - `A000001` **同时用于**"未登录或登录已过期"（healthy 的掉线走这条）与参数错误
 *   （如「不支持的记录类型：BOGUS」「体重需在 0.05 ~ 200 kg 之间」）→
 *   掉线由 `ApiError.isUnauthorized`（码 + message 正则）判定，剩下的一律透传后端 message。
 * - `B000001` **不是"服务崩了"的同义词**：未映射路径 / 方法不对 / 参数类型转换失败都被
 *   GlobalExceptionHandler 吞成它（挂账 #48）→ 文案要同时给"路径或参数不对"和"稍后重试"两条路。
 * - `B000404` / `B000411` 是**付费墙**（本账号档位不够），不是错误：给升级提示，**不要重试**。
 */
export function healthErrorMessage (e) {
  if (!e) return '健康模块出错了，稍后再试'
  const code = e.code ? String(e.code) : ''
  if (code === 'UNAUTHORIZED' || e.isUnauthorized) return '登录已过期，请重新登录'
  if (code === 'NETWORK') return e.message || '连不上后端，检查网络或网关是否已启动'
  if (code === '404') return '健康服务没找到（网关 404）。确认 miaowang-healthy 已注册到 Nacos，别把服务名写错'
  const MAP = {
    B000400: '宠物不存在或无权访问。后端对「不存在」和「不是你的」故意回同一个码，所以先确认这是你名下的宠物',
    B000401: '宠物服务暂时不可用，稍后重试',
    B000402: '这条记录不存在或不属于你，刷新一次列表再试',
    B000403: '这个档位能存的健康记录条数已经满了，开通会员可以继续记录',
    B000404: '导出完整档案是会员功能，当前档位不支持。开通后可导出结构化 JSON',
    B000405: '会员权益服务暂时不可用，稍后重试（不是你操作的问题）',
    B000406: '记录保存失败，稍后重试',
    B000407: '必填项没填全，补上再提交（时间要选，项目名 / 诊断 / 药名这类不能空）',
    B000408: '知识库服务暂时不可用，稍后重试',
    B000409: '这个症状选项在知识库里查不到，重新选一条（非法选项不会退化成自填名）',
    B000410: '缺少宠物 ID。回到宠物档案重新进健康页',
    B000411: 'AI 解读症状是会员功能，当前档位不支持。开通后可对最近的症状做一次解读',
    B000412: '还没有症状记录，先记一条症状再做 AI 解读',
    B000413: 'AI 解读服务暂时不可用，稍后重试',
    B000001: '后端返回「系统执行出错」。常见原因是请求的路径或筛选参数不对（也可能是健康服务在重启），稍后重试一次'
  }
  return MAP[code] || e.message || '健康模块出错了，稍后再试'
}

/**
 * `/timeline?types=` 的非法值会让后端回 `A000001` + 「不支持的记录类型：xxx」——
 * 这是**前端自己传错了**，所以页面在发请求前先用这个函数校验一遍，别把锅甩给用户。
 * @returns {string} 合法返回 ''，非法返回给用户看的话
 */
export function validateTimelineTypes (types) {
  const s = types === null || types === undefined ? '' : String(types).trim()
  if (!s) return ''
  const parts = s.split(',')
  const bad = []
  for (let i = 0; i < parts.length; i++) {
    const v = parts[i].trim().toLowerCase()
    if (!v) continue
    let ok = false
    for (let j = 0; j < RECORD_TYPES.length; j++) {
      if (RECORD_TYPES[j].value === v) { ok = true; break }
    }
    if (!ok) bad.push(parts[i].trim())
  }
  if (bad.length) return '不支持的记录类型：' + bad.join('、')
  return ''
}