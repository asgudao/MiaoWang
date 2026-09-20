/**
 * AI 问答 store（Web）
 *
 * 流式回答的关键约束：**一次只跑一条流**（`streaming`），并且后端的增量块是"增量"不是全量，
 * 所以要在本地按 messageId 累加。开始前先插入"用户提问 + 空的 AI 占位"两条，让界面立刻有反馈
 * （一次回答十几秒，不给进行态用户会以为没反应）。
 *
 * 会话主键是 `id`（不是 conversationId）；`currentId` 为 null 表示"新对话"，
 * 第一轮流式的 `meta` 事件回来后才拿到真正的会话 id。
 */
import { ref } from 'vue'
import { defineStore } from 'pinia'
import * as aiApi from '../api/ai'
import type { AiConversation, RagSource } from '../api/ai'
import { ROLE_AI, ROLE_USER } from '../api/ai'
import { aiErrorMessage, isAborted, isQuotaExhausted } from '../utils/ai'

export interface ChatMessage {
  id: number
  role: number
  content: string
  sources: RagSource[]
  hasKnowledge: boolean | null
  feedback: number | null
  /** 正在流式输出 */
  pending?: boolean
  /**
   * 模型的思考过程增量（思考模型才有；后端**不落库** → 回放历史时是 undefined）。
   * 必须展示：思考要跑十几秒，这期间正文一个字都没有，不显示会被当成卡死。
   */
  thinking?: string
  /** 思考阶段是否结束（正文开始吐字或流已结束） */
  thinkingDone?: boolean
  /** 本地错误（这条回答失败的原因） */
  error?: string
  /**
   * 这条回答失败是因为**额度用完**（`B000702`）→ 界面要在错误旁边直接给"去开通会员"的入口。
   * 不靠匹配错误文案判断，避免以后改文案把入口弄丢。
   */
  quotaError?: boolean
}

export interface RagQuota {
  remaining: number
  limit: number
  desc: string
}

function toChatMessage(m: aiApi.AiMessage): ChatMessage {
  return {
    id: m.id,
    role: m.role,
    content: m.content,
    sources: m.sources ?? [],
    hasKnowledge: m.hasKnowledge,
    feedback: m.feedback,
  }
}

export const useAiStore = defineStore('ai', () => {
  const conversations = ref<AiConversation[]>([])
  const listLoading = ref(false)
  const listError = ref('')

  const messages = ref<ChatMessage[]>([])
  const messagesLoading = ref(false)
  /** null = 新对话（还没落库） */
  const currentId = ref<number | null>(null)
  const streaming = ref(false)
  const quota = ref<RagQuota | null>(null)

  let controller: AbortController | null = null
  /** 本地临时消息 id 用负数，避免和后端真实 id 撞 */
  let tempId = -1

  async function fetchConversations() {
    listLoading.value = true
    listError.value = ''
    try {
      conversations.value = await aiApi.listConversations()
    } catch (e) {
      listError.value = aiErrorMessage(e)
    } finally {
      listLoading.value = false
    }
  }

  async function fetchQuota() {
    try {
      const b = await aiApi.refreshRagQuota()
      quota.value = { remaining: b.ragRemaining, limit: b.ragLimit, desc: b.ragPeriodDesc }
    } catch {
      // 额度拿不到不该阻塞问答（问答本身还会再校验一次）
      quota.value = null
    }
  }

  function startNew() {
    stop()
    currentId.value = null
    messages.value = []
  }

  async function open(id: number) {
    stop()
    currentId.value = id
    messagesLoading.value = true
    try {
      const rows = await aiApi.listMessages(id)
      messages.value = rows.map(toChatMessage)
    } catch (e) {
      messages.value = [
        {
          id: tempId--,
          role: ROLE_AI,
          content: '',
          sources: [],
          hasKnowledge: null,
          feedback: null,
          error: aiErrorMessage(e),
        },
      ]
    } finally {
      messagesLoading.value = false
    }
  }

  function patch(id: number, p: Partial<ChatMessage>) {
    messages.value = messages.value.map((m) => (m.id === id ? { ...m, ...p } : m))
  }

  async function ask(question: string) {
    const q = question.trim()
    if (!q || streaming.value) return

    const userId = tempId--
    const aiId = tempId--
    messages.value = [
      ...messages.value,
      { id: userId, role: ROLE_USER, content: q, sources: [], hasKnowledge: null, feedback: null },
      {
        id: aiId,
        role: ROLE_AI,
        content: '',
        sources: [],
        hasKnowledge: null,
        feedback: null,
        pending: true,
      },
    ]
    streaming.value = true
    controller = new AbortController()

    try {
      await aiApi.askRagStream(
        q,
        currentId.value ?? undefined,
        {
          onMeta: (id) => {
            if (currentId.value == null) currentId.value = id
          },
          onSources: (sources) => patch(aiId, { sources }),
          onThinking: (text) => {
            const cur = messages.value.find((m) => m.id === aiId)
            patch(aiId, { thinking: (cur?.thinking ?? '') + text })
          },
          onChunk: (text) => {
            const cur = messages.value.find((m) => m.id === aiId)
            // 正文一来就说明思考结束了（界面把思考块收起，仍可展开）
            patch(aiId, { content: (cur?.content ?? '') + text, thinkingDone: true })
          },
          onDone: (info) => patch(aiId, { id: info.messageId, pending: false }),
        },
        controller.signal,
      )
      patch(aiId, { pending: false, thinkingDone: true })
      // 标题、消息条数、剩余额度都会被这次问答改变
      await Promise.all([fetchConversations(), fetchQuota()])
    } catch (e) {
      if (isAborted(e)) {
        patch(aiId, { pending: false, error: '已停止本次回答' })
      } else {
        const cur = messages.value.find((m) => m.id === aiId)
        // 已经吐了半截就保留半截，只把错误附在后面
        patch(aiId, {
          pending: false,
          error: aiErrorMessage(e),
          content: cur?.content ?? '',
          quotaError: isQuotaExhausted(e),
        })
        await fetchQuota()
      }
    } finally {
      streaming.value = false
      controller = null
    }
  }

  /** 停止生成：SSE 连接在客户端断开，服务端会感知到 */
  function stop() {
    if (controller) {
      controller.abort()
      controller = null
    }
    streaming.value = false
  }

  async function remove(id: number): Promise<void> {
    await aiApi.deleteConversation(id)
    conversations.value = conversations.value.filter((c) => c.id !== id)
    if (currentId.value === id) startNew()
  }

  async function rate(messageId: number, type: 1 | 2): Promise<void> {
    await aiApi.sendFeedback(messageId, type)
    patch(messageId, { feedback: type })
  }

  function reset() {
    stop()
    conversations.value = []
    messages.value = []
    currentId.value = null
    listError.value = ''
  }

  return {
    conversations,
    listLoading,
    listError,
    messages,
    messagesLoading,
    currentId,
    streaming,
    quota,
    fetchConversations,
    fetchQuota,
    startNew,
    open,
    ask,
    stop,
    remove,
    rate,
    reset,
  }
})
