/**
 * AI 问答接口（手机端 / JS）
 *
 * 契约与 Web 端 `src/api/ai.ts` 同一份（见 miaowang-api-contract），这里有两点手机端特有：
 *
 * 1) **流式只有两条路**（官方文档实测：`enableChunked` / `onChunkReceived` 是微信小程序能力，
 *    H5 与 App 都不支持）：
 *    - 能用浏览器原生 `fetch` 时（H5，以及 webview 里带 ReadableStream 的 App）→ 真正的逐块流式
 *    - 否则（App 逻辑层没有 fetch）→ 用 `uni.request` 收完整段 SSE 文本，再**一次性解析**事件。
 *      观感是"等十几秒后整段出现"，但内容、来源、思考过程都一样，不会出错。
 * 2) 超时必须放大：一次回答十几秒，`uni.request` 默认 15s 会直接把长回答掐断。
 *
 * 事件名：meta{conversationId} → sources[...] → thinking(增量纯文本) → message(增量文本) → done{messageId,...}
 * 会话主键是 `id`（不是 conversationId）；消息 role 是整数 1=用户 2=AI。
 */
import { ApiError, api, authHeaders, buildUrl } from '../utils/request.js'

export const ROLE_USER = 1
export const ROLE_AI = 2
export const FEEDBACK_UP = 1
export const FEEDBACK_DOWN = 2

/** 流式接口地址（H5 下是相对路径，走 manifest.json 的 devServer 代理） */
const STREAM_PATH = '/ai/rag/ask/stream'

export function askRag (question, conversationId) {
  const body = conversationId ? { question: question, conversationId: conversationId } : { question: question }
  return api.post('aiKnowledge', '/ai/rag/ask', { body: body, timeout: 120000 })
}

export function listConversations () {
  return api.get('aiKnowledge', '/ai/conversation/list')
}

export function listMessages (conversationId) {
  return api.get('aiKnowledge', '/ai/conversation/' + conversationId + '/message')
}

export function deleteConversation (conversationId) {
  return api.del('aiKnowledge', '/ai/conversation/' + conversationId)
}

export function sendFeedback (messageId, type) {
  return api.post('aiKnowledge', '/ai/conversation/message/' + messageId + '/feedback', { body: { type: type } })
}

/**
 * 会员权益总览（额度展示用）。未开会员也返回免费档，所以不用写两套逻辑。
 * 放在这里是为了让 AI 页面不必再引一个 vip 模块；字段见 miaowang-api-contract 的 vip 段。
 */
export function getMyBenefit () {
  return api.get('vip', '/vip/benefit')
}

/* ---------------------------- SSE 解析 ---------------------------- */

/** 把缓冲区里已完整的事件块全部派发掉，返回剩余缓冲 */
export function dispatchBuffer (buffer, handlers) {
  let sep = buffer.indexOf('\n\n')
  while (sep >= 0) {
    emitBlock(buffer.slice(0, sep), handlers)
    buffer = buffer.slice(sep + 2)
    sep = buffer.indexOf('\n\n')
  }
  return buffer
}

/** 先按 `event:` 分派，再取 `data:` —— 不能见到 data 就往正文里塞（sources 的 JSON 会混进答案） */
function emitBlock (block, handlers) {
  let event = 'message'
  const dataLines = []
  const lines = block.split('\n')
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    if (line.indexOf('event:') === 0) event = line.slice(6).trim()
    else if (line.indexOf('data:') === 0) dataLines.push(line.slice(5).replace(/^ /, ''))
  }
  if (!dataLines.length) return
  const payload = dataLines.join('\n')
  try {
    if (event === 'meta') {
      if (handlers.onMeta) handlers.onMeta(JSON.parse(payload).conversationId)
    } else if (event === 'sources') {
      if (handlers.onSources) handlers.onSources(JSON.parse(payload))
    } else if (event === 'thinking') {
      if (handlers.onThinking) handlers.onThinking(payload)
    } else if (event === 'message') {
      if (handlers.onChunk) handlers.onChunk(payload)
    } else if (event === 'done') {
      if (handlers.onDone) handlers.onDone(JSON.parse(payload))
    }
  } catch (e) {
    // 单个事件块解析失败不该中断整条流
  }
}

/** 非 SSE 响应（额度用完等业务错误走的是普通 JSON 信封）→ ApiError */
export function envelopeError (text, statusCode) {
  let message = 'AI 服务返回了非流式响应（HTTP ' + statusCode + '）'
  let code = String(statusCode)
  try {
    const env = JSON.parse(text)
    if (env && env.code) code = String(env.code)
    if (env && env.message) message = env.message
    if (env && env.state === -1) code = 'UNAUTHORIZED'
  } catch (e) {
    // 保持默认文案
  }
  return new ApiError(code, message, text)
}

function looksLikeSse (ctype) {
  return !!ctype && ctype.indexOf('text/event-stream') >= 0
}

/** 真流式：fetch + ReadableStream（H5 / 支持 ReadableStream 的 webview） */
function runFetchStream (url, header, body, handlers, state) {
  return fetch(url, { method: 'POST', headers: header, body: body }).then(function (res) {
    if (res.status === 401 || res.status === 403) {
      throw new ApiError('UNAUTHORIZED', '登录已过期，请重新登录')
    }
    if (!looksLikeSse(res.headers.get('content-type'))) {
      return res.text().then(function (text) {
        throw envelopeError(text, res.status)
      })
    }
    if (!res.body || !res.body.getReader) {
      throw new ApiError('NO_BODY', 'AI 服务没有返回可读的流')
    }
    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    function pump () {
      if (state.aborted) {
        try { reader.cancel() } catch (e) { /* 忽略 */ }
        return Promise.resolve()
      }
      return reader.read().then(function (r) {
        if (r.done) return
        buffer += decoder.decode(r.value, { stream: true })
        buffer = dispatchBuffer(buffer, handlers)
        return pump()
      })
    }
    return pump()
  })
}

/** 兜底：uni.request 收完整段再解析（App 逻辑层没有 fetch） */
function runBufferedStream (url, header, body, handlers, state) {
  return new Promise(function (resolve, reject) {
    const task = uni.request({
      url: url,
      method: 'POST',
      header: header,
      data: body,
      // 必须显式 text：默认 dataType=json 会去 JSON.parse 整个 SSE 文本
      dataType: 'text',
      responseType: 'text',
      timeout: 120000,
      success: function (res) {
        if (res.statusCode === 401 || res.statusCode === 403) {
          reject(new ApiError('UNAUTHORIZED', '登录已过期，请重新登录'))
          return
        }
        const raw = typeof res.data === 'string' ? res.data : ''
        if (!raw) {
          reject(new ApiError(String(res.statusCode), 'AI 服务没有返回内容（HTTP ' + res.statusCode + '）'))
          return
        }
        if (!looksLikeSse(res.header && (res.header['Content-Type'] || res.header['content-type']))) {
          reject(envelopeError(raw, res.statusCode))
          return
        }
        dispatchBuffer(raw + '\n\n', handlers)
        resolve()
      },
      fail: function (err) {
        if (state.aborted) { resolve(); return }
        reject(new ApiError('NETWORK', '连不上 AI 服务：手机与电脑要同网段、防火墙放行 7000（H5 则确认代理与网关正常）', err))
      }
    })
    state.task = task
  })
}

/** 流式请求的地址/头/体：fetch、renderjs、整段兜底三条路共用，避免各处拼得不一致 */
export function buildStreamRequest (question, conversationId) {
  return {
    url: buildUrl('aiKnowledge', STREAM_PATH),
    header: Object.assign({ 'Content-Type': 'application/json' }, authHeaders()),
    body: JSON.stringify(conversationId ? { question: question, conversationId: conversationId } : { question: question })
  }
}

function abortState (state) {
  state.aborted = true
  if (state.task && state.task.abort) {
    try { state.task.abort() } catch (e) { /* 忽略 */ }
  }
}

/**
 * 提问（自动选路）。
 * - 逻辑层有 fetch + ReadableStream（H5）→ 真流式；失败且**一个分片都没收到** → 自动换整段解析重试
 * - 否则（App 逻辑层）→ 整段收完再解析
 * App 端要真逐字流式时，页面会传自己的 transport（renderjs）进来，见 pages/ai/ai.vue
 * @returns {{ abort: Function, done: Promise }}
 */
export function askStream (question, conversationId, handlers) {
  const req = buildStreamRequest(question, conversationId)
  const state = { aborted: false, task: null }
  const canStream = typeof fetch === 'function' && typeof ReadableStream !== 'undefined'
  if (!canStream) {
    return {
      abort: function () { abortState(state) },
      done: runBufferedStream(req.url, req.header, req.body, handlers, state)
    }
  }

  // 判断"有没有真的开始出内容"：有内容就不能重试了（否则会把答案打两遍）
  let got = false
  const wrapped = Object.assign({}, handlers, {
    onThinking: function (text) {
      got = true
      if (handlers.onThinking) handlers.onThinking(text)
    },
    onChunk: function (text) {
      got = true
      if (handlers.onChunk) handlers.onChunk(text)
    }
  })

  const done = runFetchStream(req.url, req.header, req.body, wrapped, state).catch(function (e) {
    if (state.aborted || got) throw e
    // 一个分片都没收到：可能是这个环境的 fetch 不能流读，或被同源策略拦住 → 换整段解析再来一次
    const fb = bufferedStream(question, conversationId, handlers)
    state.task = fb
    return fb.done
  })

  return {
    abort: function () {
      abortState(state)
      if (state.task && state.task.abort) {
        try { state.task.abort() } catch (e) { /* 忽略 */ }
      }
    },
    done: done
  }
}

/**
 * 整段解析那一路单独暴露：App 端 renderjs 的 fetch 若被 CORS 拦住，页面据此自动降级。
 * @returns {{ abort: Function, done: Promise }}
 */
export function bufferedStream (question, conversationId, handlers) {
  const req = buildStreamRequest(question, conversationId)
  const state = { aborted: false, task: null }
  return {
    abort: function () { abortState(state) },
    done: runBufferedStream(req.url, req.header, req.body, handlers, state)
  }
}

/** 当前环境能不能真流式（页面据此提示"这台设备上会整段出现"） */
export function canStream () {
  return typeof fetch === 'function' && typeof ReadableStream !== 'undefined'
}