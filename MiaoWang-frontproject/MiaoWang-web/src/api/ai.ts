/**
 * AI 问答接口（Web）
 *
 * 契约已用真调用验证（tools/probe-ai.js，走 Vite 代理与浏览器同路径）：
 * - `POST /ai/rag/ask`         非流式，`data` = `{ answer, sources, hasKnowledge, conversationId, messageId, reasoning? }`
 * - `POST /ai/rag/ask/stream`  SSE（**POST**，不是 EventSource 那种 GET）：
 *                              `event: meta{conversationId}` → `sources[...]` → 多个 `message`（增量文本）→ `done{messageId,tokenIn,tokenOut,latencyMs}`
 * - 会话列表项主键是 **`id`**（不是 `conversationId`！`conversationId` 只出现在 ask 的返回体和 SSE 的 meta 里）
 * - 消息 `role` 是**整数**：1=用户 2=AI；`feedback`：null=未评价 1=👍 2=👎
 * - 额度：调大模型**之前**先扣一次 RAG 次数，用完返回 `B000702`；生成失败会自动回退（失败不扣）
 * - 一次回答十几秒（实测 1352 token ≈ 19s）→ 界面必须做进行态
 */
import { ApiError, api, authHeaders, buildUrl } from './request'
import { getMyBenefit } from './vip'

/** 知识碎片（检索命中的来源） */
export interface RagSource {
  id: number
  title: string
  /** 1=猫 2=狗 */
  species: number
  categoryCode?: string
}

export interface AskResult {
  answer: string
  sources: RagSource[]
  hasKnowledge: boolean
  conversationId: number
  messageId: number
  /** 思考过程，只在后端开关打开时才有 */
  reasoning?: string | null
}

export interface AiConversation {
  /** ⚠️ 就是它，不是 conversationId */
  id: number
  title: string
  messageCount: number
  lastMessageTime: string | null
  createTime: string
}

export interface AiMessage {
  id: number
  /** 1=用户 2=AI */
  role: number
  content: string
  hasKnowledge: boolean | null
  sources: RagSource[] | null
  /** null=未评价 1=👍 2=👎 */
  feedback: number | null
  createTime: string
}

export const ROLE_USER = 1
export const ROLE_AI = 2
export const FEEDBACK_UP = 1
export const FEEDBACK_DOWN = 2

export function askRag(question: string, conversationId?: number) {
  return api.post<AskResult>('aiKnowledge', '/ai/rag/ask', {
    body: conversationId ? { question, conversationId } : { question },
  })
}

export function listConversations() {
  return api.get<AiConversation[]>('aiKnowledge', '/ai/conversation/list')
}

export function listMessages(conversationId: number) {
  return api.get<AiMessage[]>('aiKnowledge', `/ai/conversation/${conversationId}/message`)
}

export function deleteConversation(conversationId: number) {
  return api.del<void>('aiKnowledge', `/ai/conversation/${conversationId}`)
}

export function sendFeedback(messageId: number, type: typeof FEEDBACK_UP | typeof FEEDBACK_DOWN) {
  return api.post<void>('aiKnowledge', `/ai/conversation/message/${messageId}/feedback`, {
    body: { type },
  })
}

export interface RagDoneInfo {
  messageId: number
  tokenIn: number
  tokenOut?: number
  latencyMs?: number
  /** 会员档的 done 还带这些分段时间（免费档没有）：不要去渲染，两边字段不一致 */
  quotaMs?: number
  conversationMs?: number
  retrievalMs?: number
  modelMs?: number
}

export interface StreamHandlers {
  onMeta?: (conversationId: number) => void
  onSources?: (sources: RagSource[]) => void
  /** 增量文本（**不是**全量），要自己累加 */
  onChunk?: (text: string) => void
  /**
   * 模型的思考过程增量（`event: thinking`）。**会员/思考模型才有**，且是纯文本不是 JSON。
   * 后端不落库、回放时不会有；不接这个回调也不会漏答案。
   */
  onThinking?: (text: string) => void
  onDone?: (info: RagDoneInfo) => void
}

/**
 * 流式问答。
 *
 * 用 fetch + ReadableStream 手解 SSE（浏览器原生支持，不能走 EventSource —— 那个只能发 GET、还带不了鉴权头）。
 * 解析规则按后端调试页 `ai-knowledge/.../static/chat.html` 的写法：**先按 `event:` 分派，再取 `data:` 累加**，
 * 不能见到 data 就往正文里塞（否则 sources 的 JSON 会混进答案）。
 *
 * 抛出：额度用完（B000702）等业务错误时后端**不回 SSE 而是回普通 JSON 信封**，这里会转成 ApiError。
 */
export async function askRagStream(
  question: string,
  conversationId: number | undefined,
  handlers: StreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  let res: Response
  try {
    res = await fetch(buildUrl('aiKnowledge', '/ai/rag/ask/stream'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      body: JSON.stringify(conversationId ? { question, conversationId } : { question }),
      signal,
    })
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e
    throw new ApiError('NETWORK', '连不上 AI 服务：检查网关与 ai-knowledge 是否在运行', e)
  }

  if (res.status === 401 || res.status === 403) {
    throw new ApiError('UNAUTHORIZED', '登录已过期，请重新登录')
  }

  const ctype = res.headers.get('content-type') ?? ''
  if (!ctype.includes('text/event-stream')) {
    // 业务错误（额度用完、问题为空、未登录…）走的是普通信封
    const text = await res.text()
    let msg = `AI 服务返回了非流式响应（HTTP ${res.status}）`
    let code = String(res.status)
    try {
      const env = JSON.parse(text) as { code?: string; message?: string; state?: number }
      if (env.code) code = env.code
      if (env.message) msg = env.message
      if (env.state === -1) code = 'UNAUTHORIZED'
    } catch {
      /* 保持默认文案 */
    }
    throw new ApiError(code, msg, text.slice(0, 200))
  }
  if (!res.body) throw new ApiError('NO_BODY', 'AI 服务没有返回可读的流')

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let sep = buffer.indexOf('\n\n')
    while (sep >= 0) {
      const block = buffer.slice(0, sep)
      buffer = buffer.slice(sep + 2)
      let event = 'message'
      const dataLines: string[] = []
      for (const line of block.split('\n')) {
        if (line.startsWith('event:')) event = line.slice(6).trim()
        else if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^ /, ''))
      }
      if (dataLines.length) {
        const payload = dataLines.join('\n')
        try {
          if (event === 'meta') {
            handlers.onMeta?.((JSON.parse(payload) as { conversationId: number }).conversationId)
          } else if (event === 'sources') {
            handlers.onSources?.(JSON.parse(payload) as RagSource[])
          } else if (event === 'message') {
            handlers.onChunk?.(payload)
          } else if (event === 'thinking') {
            handlers.onThinking?.(payload)
          } else if (event === 'done') {
            handlers.onDone?.(JSON.parse(payload) as RagDoneInfo)
          }
        } catch {
          // 单个事件块解析失败不该中断整条流：继续读后面的事件
        }
      }
      sep = buffer.indexOf('\n\n')
    }
  }
}

/**
 * 供页面显示"还剩几次"：RAG 免费档 5 次/月，不显示剩余次数用户会被 B000702 搞懵。
 * 权益总览本身在 vip 模块（未开会员也返回免费档），所以这里只做薄封装。
 */
export function refreshRagQuota() {
  return getMyBenefit()
}
