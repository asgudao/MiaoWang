/**
 * AI 问答状态（手机端 / reactive 单例，工程无 Pinia）
 *
 * 关键约束（与 Web 端 store 一致）：
 * - 一次只跑一条流（`streaming`）；增量块是"增量"不是全量，按临时 id 累加
 * - 先插入"用户提问 + 空 AI 占位"，界面立刻有反馈（一次回答十几秒）
 * - 会话主键是 `id`；`currentId` 为 null 表示新对话，首轮流式的 meta 事件回来才有真 id
 *
 * ⚠️ 页面模板不许直接写 `ai.xxx`（不在实例上）→ 页面用 computed 转一手（见 pages/ai/ai.vue）
 */
import { reactive } from 'vue'
import * as aiApi from '../api/ai.js'
import { ROLE_AI, ROLE_USER } from '../api/ai.js'
import { aiErrorMessage, isAborted } from '../utils/ai.js'

let controller = null
/** 本地临时消息 id 用负数，避免和后端真实 id 撞 */
let tempId = -1

export const ai = reactive({
  conversations: [],
  listLoading: false,
  listError: '',

  messages: [],
  messagesLoading: false,
  /** null = 新对话（还没落库） */
  currentId: null,
  streaming: false,
  /** { remaining, limit, desc }；remaining < 0 表示不限次数 */
  quota: null,
  /** 额度没读到时的原因（空串 = 正常）。**不静默**：页面据此显示一行提示 + 重试 */
  quotaError: '',

  find (id) {
    for (let i = 0; i < this.messages.length; i++) {
      if (this.messages[i].id === id) return this.messages[i]
    }
    return null
  },

  patch (id, p) {
    for (let i = 0; i < this.messages.length; i++) {
      if (this.messages[i].id === id) {
        this.messages[i] = Object.assign({}, this.messages[i], p)
        return
      }
    }
  },

  async fetchConversations () {
    this.listLoading = true
    this.listError = ''
    try {
      const rows = await aiApi.listConversations()
      this.conversations = rows || []
    } catch (e) {
      this.listError = aiErrorMessage(e)
    } finally {
      this.listLoading = false
    }
  },

  async fetchQuota () {
    this.quotaError = ''
    try {
      const b = await aiApi.getMyBenefit()
      this.quota = { remaining: b.ragRemaining, limit: b.ragLimit, desc: b.ragPeriodDesc }
    } catch (e) {
      // 额度拿不到不该挡住问答（问答本身还会再校验一次），但**不能静默**：
      // 否则用户只会以为"这个页面没有额度提示"，而不知道是请求失败了
      this.quota = null
      this.quotaError = aiErrorMessage(e)
    }
  },

  startNew () {
    this.stop()
    this.currentId = null
    this.messages = []
  },

  async open (id) {
    this.stop()
    this.currentId = id
    this.messagesLoading = true
    try {
      const rows = await aiApi.listMessages(id)
      const list = rows || []
      this.messages = list.map(function (m) {
        return {
          id: m.id,
          role: m.role,
          content: m.content,
          sources: m.sources || [],
          thinking: '',
          thinkingDone: true,
          hasKnowledge: m.hasKnowledge,
          feedback: m.feedback
        }
      })
    } catch (e) {
      this.messages = [{
        id: tempId--,
        role: ROLE_AI,
        content: '',
        sources: [],
        thinking: '',
        thinkingDone: true,
        hasKnowledge: null,
        feedback: null,
        error: aiErrorMessage(e)
      }]
    } finally {
      this.messagesLoading = false
    }
  },

  /**
   * @param {string} question
   * @param {Function} [transport] 可选：自定义传输层（App 端传 renderjs 那条路进来）
   */
  async ask (question, transport) {
    const q = String(question === undefined || question === null ? '' : question).trim()
    if (!q || this.streaming) return
    const self = this
    const userId = tempId--
    const aiId = tempId--
    this.messages = this.messages.concat([
      { id: userId, role: ROLE_USER, content: q, sources: [], thinking: '', thinkingDone: true, hasKnowledge: null, feedback: null },
      { id: aiId, role: ROLE_AI, content: '', sources: [], thinking: '', thinkingDone: false, hasKnowledge: null, feedback: null, pending: true }
    ])
    this.streaming = true

    const handlers = {
      onMeta: function (id) {
        if (!self.currentId) self.currentId = id
      },
      onSources: function (sources) {
        self.patch(aiId, { sources: sources || [] })
      },
      onThinking: function (text) {
        const cur = self.find(aiId)
        self.patch(aiId, { thinking: (cur && cur.thinking ? cur.thinking : '') + text })
      },
      onChunk: function (text) {
        const cur = self.find(aiId)
        // 正文一来就说明思考结束（界面把思考块收起，仍可点开）
        self.patch(aiId, { content: (cur && cur.content ? cur.content : '') + text, thinkingDone: true })
      },
      onDone: function (info) {
        self.patch(aiId, { id: info.messageId, pending: false, thinkingDone: true })
      }
    }

    const handle = transport
      ? transport(q, this.currentId || undefined, handlers)
      : aiApi.askStream(q, this.currentId || undefined, handlers)
    controller = handle

    try {
      await handle.done
      this.patch(aiId, { pending: false, thinkingDone: true })
      await Promise.all([this.fetchConversations(), this.fetchQuota()])
    } catch (e) {
      if (isAborted(e)) {
        this.patch(aiId, { pending: false, thinkingDone: true, error: '已停止本次回答' })
      } else {
        const cur = this.find(aiId)
        // 已经吐了半截就保留半截，只把错误附在后面
        this.patch(aiId, { pending: false, thinkingDone: true, error: aiErrorMessage(e), content: cur && cur.content ? cur.content : '' })
        await this.fetchQuota()
      }
    } finally {
      this.streaming = false
      controller = null
    }
  },

  /** 停止生成：客户端断开，服务端会感知到 */
  stop () {
    if (controller) {
      controller.abort()
      controller = null
    }
    this.streaming = false
  },

  async remove (id) {
    await aiApi.deleteConversation(id)
    this.conversations = this.conversations.filter(function (c) { return c.id !== id })
    if (this.currentId === id) this.startNew()
  },

  async rate (messageId, type) {
    await aiApi.sendFeedback(messageId, type)
    this.patch(messageId, { feedback: type })
  },

  reset () {
    this.stop()
    this.conversations = []
    this.messages = []
    this.currentId = null
    this.listError = ''
    this.quota = null
    this.quotaError = ''
  }
})