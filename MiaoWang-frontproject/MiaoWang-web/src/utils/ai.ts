/**
 * AI 问答的展示工具（Web）
 *
 * 1) `parseAnswer`：模型爱输出 `**加粗**` 和 `•`/`1.` 列表，直接塞 textContent 会看到一堆星号。
 *    这里解析成结构化片段给模板渲染 —— **不用 v-html**：模型输出和知识碎片都属于不可信内容。
 * 2) `relativeTime`：会话列表的时间文案
 * 3) `aiErrorMessage`：把后端的错误码翻成能给用户看的话
 */
import { ApiError } from '../api/request'

export interface TextPart {
  bold?: boolean
  /** 引用编号：回答里的 `[n]` 会被单独切出来，渲染成可点的角标 */
  cite?: number
  text: string
}

export interface TextBlock {
  kind: 'p' | 'li'
  parts: TextPart[]
}

/**
 * 行内解析：`**加粗**` 与引用角标 `[1]`。
 *
 * 引用必须单独成段渲染，否则用户看到「每条都写着 [1]」会以为是坏的 —— 变成可点的角标后，
 * 点一下就能对上下面是哪条碎片，同时来源那边会显示"引用 N 次"。
 */
function parseInline(line: string): TextPart[] {
  const parts: TextPart[] = []
  const re = /\*\*(.+?)\*\*|\[(\d+)\]/g
  let last = 0
  let m = re.exec(line)
  while (m !== null) {
    if (m.index > last) parts.push({ text: line.slice(last, m.index) })
    if (m[1] !== undefined) parts.push({ bold: true, text: m[1] })
    else parts.push({ cite: Number(m[2]), text: `[${m[2]}]` })
    last = m.index + m[0].length
    m = re.exec(line)
  }
  if (last < line.length) parts.push({ text: line.slice(last) })
  return parts.length ? parts : [{ text: line }]
}

/** 极简块级解析：段落 + 无序/有序列表项（有序项前面的序号保留在正文里，够用且不会错位） */
export function parseAnswer(src: string): TextBlock[] {
  const blocks: TextBlock[] = []
  for (const raw of (src ?? '').split('\n')) {
    const line = raw.trim()
    if (!line) continue
    const ul = /^(?:[•·]|[-*])\s+(.*)$/.exec(line)
    if (ul) {
      blocks.push({ kind: 'li', parts: parseInline(ul[1] ?? '') })
      continue
    }
    const ol = /^(\d+[.、)]\s*)(.*)$/.exec(line)
    if (ol) {
      blocks.push({
        kind: 'li',
        parts: [{ bold: true, text: ol[1] ?? '' }, ...parseInline(ol[2] ?? '')],
      })
      continue
    }
    blocks.push({ kind: 'p', parts: parseInline(line) })
  }
  return blocks
}

/**
 * 统计回答里每个编号被引用了几次：`[1]` 出现 5 次说明整段都来自同一条碎片。
 * 这不是错误（实测后端给模型的编号与返回的 sources 顺序一致），但必须让用户看得出来。
 */
export function citationCounts(text: string): Record<number, number> {
  const counts: Record<number, number> = {}
  const re = /\[(\d+)\]/g
  let m = re.exec(text ?? '')
  while (m !== null) {
    const n = Number(m[1])
    counts[n] = (counts[n] ?? 0) + 1
    m = re.exec(text ?? '')
  }
  return counts
}

/** 1=猫 2=狗，其它当通用（后端 species 用的是这个约定） */
export function speciesLabel(species: number): string {
  if (species === 1) return '猫'
  if (species === 2) return '狗'
  return '通用'
}

/** 会话列表的时间：刚发生的用"刚刚/x 分钟前"，今天用 HH:mm，更早用 MM-DD */
export function relativeTime(iso: string | null): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  const sameDay =
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  if (sameDay) return `${hh}:${mm}`
  return `${d.getMonth() + 1}-${d.getDate()}`
}

/**
 * AI 接口的错误文案。
 *
 * `B000702`（次数用完）是唯一需要引导开通会员的码；其余优先透传后端 message（本身就是中文）。
 */
export function aiErrorMessage(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.isUnauthorized) return '登录已过期，请重新登录'
    if (e.code === 'B000702') return '这个月的 AI 问答次数用完了，开通会员可以继续问'
    if (e.code === 'B000703') return '会员权益服务暂时不可用，稍后再试（不是你操作的问题）'
    if (e.code === 'B000700') return e.message || '问题不能为空'
    if (e.code === 'B000704') return '这个会话不存在或已被删除'
    if (e.code === 'NETWORK') return e.message
    return e.message || 'AI 服务出错了，稍后再试'
  }
  if (e instanceof Error && e.name === 'AbortError') return '已停止本次回答'
  return e instanceof Error ? e.message : 'AI 服务出错了，稍后再试'
}

/** 被用户主动停止（不是错误） */
export function isAborted(e: unknown): boolean {
  return e instanceof DOMException && e.name === 'AbortError'
}

/**
 * 额度用完（`B000702`）。
 *
 * 单独判一下而不是去匹配错误文案：界面要靠它决定**在哪里放下"去开通会员"的入口**
 * （文案里已经写了"开通会员可以继续问"，但没有入口就等于这条引导是断的）。
 */
export function isQuotaExhausted(e: unknown): boolean {
  return e instanceof ApiError && e.code === 'B000702'
}
