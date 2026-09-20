/**
 * AI 问答展示工具（手机端 / JS，与 Web 端 src/utils/ai.ts 镜像）
 *
 * 1) parseAnswer：模型爱输出 `**加粗**` 和 `•`/`1.` 列表，直接塞进 <text> 会看到一堆星号。
 *    这里解析成结构化片段（模板里用 v-for 渲染，不拼 HTML）。
 * 2) 引用角标：回答里的 `[n]` 单独切出来，点它高亮对应来源，来源上再标"引用 N 次"。
 *    模型常把一整段都归到 [1]，这不是编号错位（后端给模型的编号与 sources 同序），
 *    但必须让它看起来是有信息量的。
 */

/**
 * 行内解析：返回 [{ bold, cite, text }]
 * cite 有值表示这是引用角标（text 形如 '[1]'，cite 是数字 1）
 */
function parseInline (line) {
  const parts = []
  const re = /\*\*(.+?)\*\*|\[(\d+)\]/g
  let last = 0
  let m = re.exec(line)
  while (m !== null) {
    if (m.index > last) parts.push({ text: line.slice(last, m.index) })
    if (m[1] !== undefined) parts.push({ bold: true, text: m[1] })
    else parts.push({ cite: Number(m[2]), text: '[' + m[2] + ']' })
    last = m.index + m[0].length
    m = re.exec(line)
  }
  if (last < line.length) parts.push({ text: line.slice(last) })
  return parts.length ? parts : [{ text: line }]
}

/** 块级解析：返回 [{ kind: 'p'|'li', parts, marker }]（有序列表的序号放在 marker 里） */
export function parseAnswer (src) {
  const blocks = []
  const lines = String(src === null || src === undefined ? '' : src).split('\n')
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim()
    if (!line) continue
    const ul = /^(?:[•·]|[-*])\s+(.*)$/.exec(line)
    if (ul) {
      blocks.push({ kind: 'li', marker: '', parts: parseInline(ul[1] || '') })
      continue
    }
    const ol = /^(\d+[.、)]\s*)(.*)$/.exec(line)
    if (ol) {
      blocks.push({ kind: 'li', marker: ol[1] || '', parts: parseInline(ol[2] || '') })
      continue
    }
    blocks.push({ kind: 'p', marker: '', parts: parseInline(line) })
  }
  return blocks
}

/** 统计每个编号被引用几次：{'1': 5, '2': 1} */
export function citationCounts (text) {
  const counts = {}
  const re = /\[(\d+)\]/g
  for (let m = re.exec(text || ''); m !== null; m = re.exec(text || '')) {
    counts[m[1]] = (counts[m[1]] || 0) + 1
  }
  return counts
}

/** 1=猫 2=狗，其它当通用（后端 species 用这个约定） */
export function speciesLabel (species) {
  if (species === 1) return '猫'
  if (species === 2) return '狗'
  return '通用'
}

/** 会话列表时间：刚刚 / x 分钟前 / HH:mm / MM-DD */
export function relativeTime (iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return ''
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + ' 分钟前'
  const pad = function (n) { return n < 10 ? '0' + n : String(n) }
  const sameDay = d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() && d.getDate() === now.getDate()
  if (sameDay) return pad(d.getHours()) + ':' + pad(d.getMinutes())
  return (d.getMonth() + 1) + '-' + d.getDate()
}

/** AI 接口错误文案：B000702（次数用完）是唯一要引导开通会员的码，其余透传后端中文 message */
export function aiErrorMessage (e) {
  if (!e) return 'AI 服务出错了，稍后再试'
  const code = e.code ? String(e.code) : ''
  if (e.isUnauthorized || code === 'UNAUTHORIZED') return '登录已过期，请重新登录'
  if (code === 'B000702') return '这个月的 AI 问答次数用完了，开通会员可以继续问'
  if (code === 'B000703') return '会员权益服务暂时不可用，稍后再试（不是你操作的问题）'
  if (code === 'B000704') return '这个会话不存在或已被删除'
  if (code === 'B000700') return e.message || '问题不能为空'
  if (code === 'NETWORK') return e.message
  return e.message || 'AI 服务出错了，稍后再试'
}

/** 用户主动停止（不是错误） */
export function isAborted (e) {
  return !!(e && (e.name === 'AbortError' || e.code === 'ABORTED'))
}