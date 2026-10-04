/**
 * 宠物对话的展示口径与状态机（手机端 / JS，**纯函数**）
 *
 * 为什么单独一个文件而不是全写进页面：
 * 1) 手机端是 HBuilderX + JS，Agent 跑不了 HBuilderX，也跑不了 `.vue` 里的 Options API；
 *    把"历史/空态/问候/发送成功/发送失败"这几条状态迁移写成**可执行的纯函数**，
 *    离线门禁（`tools/check-ai-pet-phone.mjs`）就能用真实 fixture 真跑一遍，
 *    而不是只做"字符串存在"式的假断言。
 * 2) 页面只负责"发请求 + 把结果交给这些函数"，模板里也不出现 store/utils（白屏事故的根治办法）。
 *
 * 契约要点（每一条都有源码/实测出处，见 `api/aiPet.js` 顶部）：
 * - `role` 1=主人 2=宠物；`kind` 0=一问一答 1=**自动问候**
 * - `petName` 没起名时后端回**字面量 "未命名"** → 这里**原样显示，不替它编名字**
 * - `/greet` 的响应**没有 messageId**（`AiPetGreetRespDTO` 只有 greeting/petName/pid/conversationId/cached），
 *   所以问候气泡只能用**客户端临时 id**（负数）；下次进页面走 `GET /message` 才有真 id
 */
import { relativeTime } from './ai.js'

export const ROLE_OWNER = 1
export const ROLE_PET = 2
export const KIND_CHAT = 0
export const KIND_GREETING = 1

/** 后端「没有宠物档案」的**原文**（AiPetErrorCodeEnum.java:32）—— 页面直接用它，不自己编一句 */
export const NO_PROFILE_TEXT = '还没有宠物档案，先添加一只再来找它聊天'

/** 额度用尽的**错误码**（唯一判据；文案是 vip 侧动态拼的，绝不能匹配文案） */
export const QUOTA_EXHAUSTED_CODE = 'B000902'

/**
 * 「还没有宠物档案」的错误码（`B000906`）。
 * 它不是"出错了"，是"还没到能开口的那一步" → 页面给建宠物的真去处。
 * 与 Web `utils/petChat.ts` 的 `NO_PROFILE_CODE` / `isNoProfile` 镜像（两端口径必须一致）。
 */
export const NO_PROFILE_CODE = 'B000906'

/** 未登录的归一码（`utils/request.js` 的 unwrap 会把它抛出来） */
const UNAUTHORIZED_CODE = 'UNAUTHORIZED'

/**
 * 乐观插入的消息用一个**负数 id** 占位（后端不回主人那条消息的 id）。
 * 用递减计数器而不是固定 -1：同一次会话里连发几条时 `v-for :key` 不能撞。
 */
let localSeq = 0
export function nextLocalId () {
  localSeq -= 1
  return localSeq
}

/** 页面/状态机共用的初始状态（**所有字段都在这里出现一次**，别在别处偷偷加字段） */
export function initialChatState () {
  return {
    /** 当前聊的宠物 id；null = 还不知道（由 GET /pet 或 greet/chat 的响应补上） */
    pid: null,
    /** 宠物名；'' → 显示「它」 */
    petName: '',
    /** 原始消息行（含乐观插入的那条），时间正序 */
    messages: [],
    historyLoading: false,
    historyError: '',
    /** 静音提示：问候没生成、没有宠物档案…（**不冒充错误**，不放红字） */
    greetNote: '',
    /** 历史被接口上限截断（一次最多 200 条） */
    truncated: false,
    sending: false,
    sendError: '',
    /** B000902：次数用完 → 页面给「去开通会员」入口 */
    quotaOut: false,
    /** 额度（来自 /vip/benefit 或 chat 的响应）；null = 还没读到 */
    quota: null,
    quotaError: '',
    /** 软门槛：未登录 / 登录过期（一个请求都不发，页内给「去登录」） */
    needLogin: false,
    /** 有没有宠物档案（GET /pet 是空数组就是 false）；null = 没读到，不猜 */
    hasProfile: null,
    /** 输入框内容 */
    draft: ''
  }
}

/**
 * 后端/网关错误的**人话**：除「未登录」外一律**原样透传后端 message**。
 *
 * 为什么不给每个 B0009xx 都写一句"更友好"的话：这些文案本来就是后端写给用户看的
 * （`AiPetErrorCodeEnum` 类注释明说"要说人话、给出路"），前端再加工一遍只会两边漂移。
 * 唯一例外是"未登录"：它在网关（`JsonResult{state:-1}`，还带一个错别字"未登录或者的登录过期"）、
 * 业务层（`B000901 请先登录后再和它聊天`）、`A000001` 兜底三处形状都不同，页面要靠它切软门槛。
 */
export function petErrorMessage (e) {
  if (!e) return '宠物对话出错了，稍后再试'
  if (e.isUnauthorized || String(e.code) === UNAUTHORIZED_CODE) return '登录已过期，请重新登录'
  return e.message || '宠物对话出错了，稍后再试'
}

/** 额度用尽：**只认码**（文案随档位周期在"今日/本月"之间变，见 api/aiPet.js 顶部） */
export function isQuotaExhausted (e) {
  return !!e && String(e.code) === QUOTA_EXHAUSTED_CODE
}

export function isUnauthorized (e) {
  return !!e && (e.isUnauthorized === true || String(e.code) === UNAUTHORIZED_CODE)
}

/**
 * 是不是「还没有宠物档案」（B000906）：**只认码，不匹配文案**
 * （文案是后端写给用户看的，改动不该让前端判断失准）。
 * 首页用它决定"给不给去添加宠物的入口"，而不是把 B000906 当成一个故障。
 */
export function isNoProfile (e) {
  return !!e && String(e.code) === NO_PROFILE_CODE
}

/** 历史回放：**整份替换**（服务端才是真的），并如实记下有没有被 200 条上限截断 */
export function applyHistory (state, rows, limit) {
  const list = Array.isArray(rows) ? rows : []
  state.messages = list.slice()
  state.historyLoading = false
  state.historyError = ''
  state.truncated = list.length >= (limit || 200)
  return list.length
}

export function applyHistoryError (state, e) {
  state.historyLoading = false
  state.historyError = petErrorMessage(e)
}

/**
 * 宠物主动开口。三条口径（契约 + `AiPetChatService.greet`）：
 * - `greeting` 有值 → 真插一条 `kind=1` 的气泡（后端**已经落库**，不是本地编的）
 * - `greeting` 为 null（code 仍是 0）→ **不是错误**，一句静音说明就够了，页面绝不能变错误页
 * - 非 0 码（没档案 / 宠物服务不可用…）→ 同样只进静音提示，正文用后端原文
 */
export function applyGreet (state, resp) {
  state.greetNote = ''
  if (resp && resp.petName) state.petName = resp.petName
  if (resp && resp.pid !== null && resp.pid !== undefined && state.pid === null) state.pid = resp.pid
  if (resp && resp.greeting) {
    state.messages.push({
      id: nextLocalId(),
      role: ROLE_PET,
      kind: KIND_GREETING,
      content: resp.greeting,
      createTime: new Date().toISOString()
    })
    return true
  }
  state.greetNote = '它这次没主动开口（问候没生成出来，不影响你主动找它聊）'
  return false
}

export function applyGreetError (state, e) {
  state.greetNote = petErrorMessage(e)
}

/** 主人按下发送：清掉上一次的错误、乐观插一条「我问的」，并把输入框清空 */
export function beginSend (state, text) {
  state.sendError = ''
  state.quotaOut = false
  state.sending = true
  state.draft = ''
  const row = {
    id: nextLocalId(),
    role: ROLE_OWNER,
    kind: KIND_CHAT,
    content: text,
    createTime: new Date().toISOString(),
    pending: true
  }
  state.messages.push(row)
  return row
}

/**
 * 一问一答成功。额度口径：
 * - `quotaRemaining` 是**本次调用之后**的剩余次数，`-1` = 不限（判 `< 0`）
 * - `null` = 后端没取到 → **不覆盖**已有额度，绝不显示"null 次"
 */
export function applyChatSuccess (state, resp, ownerRowId) {
  state.sending = false
  state.sendError = ''
  if (!resp) return false
  if (resp.petName) state.petName = resp.petName
  if (resp.pid !== null && resp.pid !== undefined) state.pid = resp.pid
  if (ownerRowId !== undefined && ownerRowId !== null) {
    for (let i = 0; i < state.messages.length; i++) {
      if (state.messages[i].id === ownerRowId) state.messages[i].pending = false
    }
  }
  state.messages.push({
    id: resp.messageId === undefined ? nextLocalId() : resp.messageId,
    role: ROLE_PET,
    kind: KIND_CHAT,
    content: resp.answer,
    createTime: new Date().toISOString(),
    latencyMs: resp.latencyMs
  })
  if (typeof resp.quotaRemaining === 'number') {
    state.quota = { remaining: resp.quotaRemaining, desc: resp.quotaPeriodDesc }
  }
  return true
}

/**
 * 一问一答失败（额度用完 / 模型没接上话 / 网络断）。
 *
 * ⚠️ 这里有一个**真实存在的语义分叉**，前端必须处理，否则会骗用户：
 * 后端是先落"主人那句"再调模型（`AiPetChatService.chat` 第 155 行 appendOwnerMessage → 157 callModel），
 * 所以 **B000902/B000903（扣额度就失败）时库里什么都没有**，而 **B000907/B000910（模型失败）时
 * 主人那句已经落库了**。判断办法只有一个：**重新拉一次历史**，看那句在不在。
 * - 不在 → 把原文放回输入框（用户不用重打）并说明"没发出去"
 * - 在   → 不回填（否则用户会看到"自己打的话"在框里、列表里也有一条，以为发了两遍），
 *          错误条里说明"它没接上话"
 *
 * @param rows 重新拉到的历史；传 null 表示**连重载都失败了**（那就不能断言发没发出去）
 * @param limit 历史接口上限（由页面从 `api/aiPet.js` 传进来，避免两处各写一个 200）
 */
export function applyChatFailure (state, e, sentText, rows, limit) {
  state.sending = false
  state.sendError = petErrorMessage(e)
  state.quotaOut = isQuotaExhausted(e)
  if (isUnauthorized(e)) {
    // 切软门槛，输入框不再有意义；也不假装"你发的话还在"
    state.needLogin = true
    return { restored: false, stored: null }
  }
  if (!Array.isArray(rows)) {
    state.draft = sentText
    state.sendError = state.sendError + '（重载历史也没成功，这条到底发出去没有暂时不确定）'
    return { restored: true, stored: null }
  }
  applyHistory(state, rows, limit)
  let stored = false
  for (let i = 0; i < rows.length; i++) {
    const r = rows[i]
    if (r && r.role === ROLE_OWNER && r.content === sentText) stored = true
  }
  if (stored) return { restored: false, stored: true }
  state.draft = sentText
  state.sendError = state.sendError + '（这句没发出去，已经放回输入框）'
  return { restored: true, stored: false }
}

/** `/vip/benefit` 读到额度 */
export function applyBenefit (state, data) {
  state.quotaError = ''
  if (!data) return false
  state.quota = {
    remaining: data.chatRemaining,
    desc: data.chatPeriodDesc
  }
  return true
}

export function applyQuotaError (state, e) {
  state.quotaError = petErrorMessage(e)
}

/**
 * 消息 → 气泡显示字段（模板只消费这些字段）。
 * `content` 为 null 一律显示空串（**不许把 null 渲染成 "null"**）。
 * 宠物名：`petName` 为空才用「它」；后端回的 "未命名" **原样显示**（不替它起名）。
 */
export function petBubbles (state) {
  const petName = state.petName || '它'
  return state.messages.map(function (row, i) {
    const isPet = row && row.role === ROLE_PET
    const content = row && row.content !== null && row.content !== undefined ? String(row.content) : ''
    return {
      id: row && row.id !== undefined ? row.id : i,
      isPet: isPet,
      isGreeting: isPet && row.kind === KIND_GREETING,
      name: isPet ? petName : '',
      text: content,
      timeText: relativeTime(row ? row.createTime : ''),
      pending: !!(row && row.pending),
      pendingText: '它正在想…'
    }
  })
}

/** 额度文案：**只用后端给的数字与周期描述**，不自己算限额、不提档位名 */
export function quotaText (quota) {
  if (!quota) return ''
  const desc = quota.desc ? String(quota.desc) : ''
  const n = quota.remaining
  if (typeof n !== 'number') return ''
  if (n < 0) return '宠物聊天不限次数'
  if (n === 0) return desc + '的聊天次数已用完'
  return desc + '还剩 ' + n + ' 次聊天'
}

/** 额度没读到：给一句可见的说明 + 下一步（与 AI 问诊页同一套口径） */
export function quotaFailText (message, loggedIn) {
  if (!message) return ''
  if (!loggedIn) return '还没登录，额度和聊天都要登录后才能用'
  if (String(message).indexOf('连不上后端') >= 0) return '额度没读到，连不上后端；聊天仍可以试'
  return '额度没读到：' + message
}

/** 一次最多 200 条；真的顶到上限时如实说，别让用户以为"就聊过这些" */
export function historyLimitNote (count, limit) {
  const max = limit || 200
  if (typeof count !== 'number' || count < max) return ''
  return '接口一次最多回 ' + max + ' 条，这里只显示了最近 ' + max + ' 条'
}