/**
 * 宠物对话的展示口径与状态机（Web，**纯函数**）
 *
 * 为什么单独一个文件而不是全写进页面：
 * 1) 离线门禁（`tools/check-ai-pet-web.js`）要能用**真实 fixture** 把这些状态迁移真跑一遍，
 *    而不是只做"源码里存在某个字符串"式的假断言。
 * 2) 页面只负责"发请求 + 把结果交给这些函数"；判断口径集中在一处，
 *    改一处不会漏另一处（手机端 `utils/aiPet.js` 是同一个设计的 JS 版镜像）。
 *
 * ⚠️ 这个模块**刻意不 import 任何东西**（连 `utils/ai.ts` 的 `relativeTime` 都不引）：
 * 引了它就会把 `api/request` → `localStorage` / `fetch` 那条链拖进门禁的加载器。
 * 时间文案因此自带一份极小的实现（口径与 `utils/ai.ts:93` 一致：刚刚 / N 分钟前 / HH:mm / MM-DD）。
 *
 * 契约要点（每一条都有源码/实测出处，见 `api/petChat.ts` 顶部）：
 * - `role` 1=主人 2=宠物；`kind` 0=一问一答 1=**自动问候**
 * - `petName` 没起名时后端回**字面量 "未命名"** → **原样显示，不替它编名字**
 * - `/greet` 的响应**没有 messageId**（`AiPetGreetRespDTO` 只有 greeting/petName/pid/conversationId/cached），
 *   所以问候气泡只能用**客户端临时 id**（负数）；下次进页面走 `GET /message` 才有真 id
 */

export const ROLE_OWNER = 1
export const ROLE_PET = 2
export const KIND_CHAT = 0
export const KIND_GREETING = 1

/**
 * 后端「没有宠物档案」的**原文**（`AiPetErrorCodeEnum.java:32` 的 B000906 message）
 * —— 页面直接用它，不自己编一句更"友好"的话（编了就会和后端漂移）。
 * 门禁会拿后端枚举源码逐字比对这个常量。
 */
export const NO_PROFILE_TEXT = '还没有宠物档案，先添加一只再来找它聊天'

/** 额度用尽的**错误码**（唯一判据；文案是 vip 侧动态拼的，绝不能匹配文案） */
export const QUOTA_EXHAUSTED_CODE = 'B000902'

/** 没有宠物档案（B000906）：它不是"出错了"，是"还没到能聊的那一步" → 页面给建宠物的真去处 */
export const NO_PROFILE_CODE = 'B000906'

/**
 * 未登录在**两个地方**形状不同，两个都要认：
 * - 经网关：`JsonResult{state:-1}` → `api/request.ts` 归一成 `ApiError('UNAUTHORIZED')`
 * - 直连 ai-pet（或将来服务间调用）：服务自己回 `Result{code:"B000901"}`
 */
export const UNAUTHORIZED_CODE = 'B000901'
const UNAUTHORIZED_CODES = new Set(['UNAUTHORIZED', '-1', UNAUTHORIZED_CODE])

/** 一次最多说多少字（B000909 的阈值；后端可由 `miaowang.ai-pet.max-question-length` 调，前端只做提示） */
export const PET_CHAT_MAX_CHARS = 500

/** 历史接口一次的上限（与 `api/petChat.ts` 的 `PET_HISTORY_LIMIT` 同值，这里只用于文案） */
export const PET_HISTORY_LIMIT = 200

/** 后端/网关错误的最小形状：`ApiError` 与普通对象都能喂进来（本模块不认识 ApiError 类） */
export interface PetChatErrorLike {
  code?: string | number | null
  message?: string | null
  isUnauthorized?: boolean
  /** 网关信封 `JsonResult` 的形状（`state: -1` = 未登录/过期）；正常路径上它已经被 request 层归一过 */
  state?: number | null
}

/** 一条消息（历史接口的字段 + 本地乐观插入时才有的 pending/latencyMs） */
export interface PetChatRow {
  id: number
  /** 1=主人 2=宠物 */
  role: number
  /** 0=一问一答 1=自动问候 */
  kind: number
  content: string | null
  createTime: string | null
  /** 本地乐观插入、还没等到后端那一次回复 */
  pending?: boolean
  latencyMs?: number | null
}

/** 额度：数字与周期描述**全部来自后端**，前端不自己算限额、也不提档位名 */
export interface PetChatQuota {
  /** -1 = 不限次数 */
  remaining: number | null
  desc: string | null
}

export interface PetChatState {
  /** 当前聊的宠物 id；null = 还不知道（由 `GET /pet` 或 greet/chat 的响应补上） */
  pid: number | null
  /** 宠物名；'' → 显示「它」 */
  petName: string
  /** 原始消息行（含乐观插入的那条），时间正序 */
  messages: PetChatRow[]
  historyLoading: boolean
  historyError: string
  /** 静音提示：问候没生成、没有宠物档案…（**不冒充错误**，不放红字） */
  greetNote: string
  /** 历史被接口上限截断（一次最多 200 条） */
  truncated: boolean
  sending: boolean
  sendError: string
  /** B000902：次数用完 → 页面给「去开通会员」入口 */
  quotaOut: boolean
  /** 额度（来自 `/vip/benefit` 或 chat 的响应）；null = 还没读到 */
  quota: PetChatQuota | null
  quotaError: string
  /** 软门槛：未登录 / 登录过期（一个请求都不发，页内给「去登录」） */
  needLogin: boolean
  /** 有没有宠物档案（`GET /pet` 是空数组就是 false）；null = 没读到，不猜 */
  hasProfile: boolean | null
  /** 输入框内容 */
  draft: string
}

/** 页面/状态机共用的初始状态（**所有字段都在这里出现一次**，别在别处偷偷加字段） */
export function initialPetChatState(): PetChatState {
  return {
    pid: null,
    petName: '',
    messages: [],
    historyLoading: false,
    historyError: '',
    greetNote: '',
    truncated: false,
    sending: false,
    sendError: '',
    quotaOut: false,
    quota: null,
    quotaError: '',
    needLogin: false,
    hasProfile: null,
    draft: '',
  }
}

/**
 * 乐观插入的消息用一个**负数 id** 占位（后端不回主人那条消息的 id）。
 * 用递减计数器而不是固定 -1：同一次会话里连发几条时 `v-for :key` 不能撞。
 */
let localSeq = 0
export function nextLocalId(): number {
  localSeq -= 1
  return localSeq
}

/** 把 unknown 收窄成错误形状（`ApiError` 有 getter `isUnauthorized`，普通对象也能造） */
function asErrorLike(e: unknown): PetChatErrorLike {
  if (e && typeof e === 'object') return e as PetChatErrorLike
  if (typeof e === 'string' && e) return { message: e }
  return {}
}

/**
 * 后端/网关错误的**人话**：除「未登录」外一律**原样透传后端 message**。
 *
 * 为什么不给每个 B0009xx 都写一句"更友好"的话：这些文案本来就是后端写给用户看的
 * （`AiPetErrorCodeEnum` 类注释明说"要说人话、给出路"），前端再加工一遍只会两边漂移；
 * 更严重的是**把失败说成拟人化的状态**（"它现在不想说话"）会把"后端挂了"骗成"宠物有情绪"，
 * 让用户永远等不到修复 —— 这是本页明确禁止的。
 * 唯一例外是"未登录"：它在网关、业务层、`A000001` 兜底三处形状都不同，页面要靠它切软门槛。
 */
export function petErrorMessage(e: unknown): string {
  const err = asErrorLike(e)
  if (!err.code && !err.message) return '宠物对话出错了，稍后再试'
  if (isUnauthorizedPetChat(e)) return '登录已过期，请重新登录'
  return typeof err.message === 'string' && err.message ? err.message : '宠物对话出错了，稍后再试'
}

/** 额度用尽：**只认码**（文案随档位周期在"今日/本月"之间变） */
export function isQuotaExhausted(e: unknown): boolean {
  return String(asErrorLike(e).code ?? '') === QUOTA_EXHAUSTED_CODE
}

/** 没有宠物档案（B000906）：页面据此给"去添加宠物"的真去处，而不是显示一句红字了事 */
export function isNoProfile(e: unknown): boolean {
  return String(asErrorLike(e).code ?? '') === NO_PROFILE_CODE
}

export function isUnauthorizedPetChat(e: unknown): boolean {
  const err = asErrorLike(e)
  return (
    err.isUnauthorized === true ||
    err.state === -1 ||
    UNAUTHORIZED_CODES.has(String(err.code ?? ''))
  )
}

/** 历史回放：**整份替换**（服务端才是真的），并如实记下有没有被 200 条上限截断 */
export function applyHistory(state: PetChatState, rows: unknown, limit: number = PET_HISTORY_LIMIT): number {
  const list = Array.isArray(rows) ? (rows as PetChatRow[]) : []
  state.messages = list.slice()
  state.historyLoading = false
  state.historyError = ''
  state.truncated = list.length >= limit
  return list.length
}

export function applyHistoryError(state: PetChatState, e: unknown): void {
  state.historyLoading = false
  // 失败时**必须**留下一句话，页面靠它和"空态"区分开（空 = 真的没聊过，失败 = 不知道聊没聊过）
  state.historyError = petErrorMessage(e)
}

/**
 * 宠物主动开口。三条口径（契约 + `AiPetChatService.greet`）：
 * - `greeting` 有值 → 真插一条 `kind=1` 的气泡（后端**已经落库**，不是本地编的）
 * - `greeting` 为 null（code 仍是 0）→ **不是错误**，一句静音说明就够了，页面绝不能变错误页
 * - 非 0 码（没有档案 / 宠物服务不可用…）→ 同样只进静音提示，正文用后端原文
 */
export function applyGreet(state: PetChatState, resp: { greeting?: string | null; petName?: string | null; pid?: number | null } | null | undefined): boolean {
  state.greetNote = ''
  if (resp && resp.petName) state.petName = resp.petName
  if (resp && resp.pid !== null && resp.pid !== undefined && state.pid === null) state.pid = resp.pid
  if (resp && resp.greeting) {
    state.messages.push({
      id: nextLocalId(),
      role: ROLE_PET,
      kind: KIND_GREETING,
      content: resp.greeting,
      createTime: new Date().toISOString(),
    })
    return true
  }
  state.greetNote = '它这次没主动开口（问候没生成出来，不影响你主动找它聊）'
  return false
}

export function applyGreetError(state: PetChatState, e: unknown): void {
  state.greetNote = petErrorMessage(e)
}

/** 主人按下发送：清掉上一次的错误、乐观插一条「我问的」，并把输入框清空 */
export function beginSend(state: PetChatState, text: string): PetChatRow {
  state.sendError = ''
  state.quotaOut = false
  state.sending = true
  state.draft = ''
  const row: PetChatRow = {
    id: nextLocalId(),
    role: ROLE_OWNER,
    kind: KIND_CHAT,
    content: text,
    createTime: new Date().toISOString(),
    pending: true,
  }
  state.messages.push(row)
  return row
}

/**
 * 一问一答成功。额度口径：
 * - `quotaRemaining` 是**本次调用之后**的剩余次数，`-1` = 不限（判 `< 0`）
 * - `null` = 后端没取到 → **不覆盖**已有额度，绝不显示"null 次"
 */
export function applyChatSuccess(
  state: PetChatState,
  resp: { answer?: string | null; petName?: string | null; pid?: number | null; messageId?: number | null; latencyMs?: number | null; quotaRemaining?: number | null; quotaPeriodDesc?: string | null } | null | undefined,
  ownerRowId?: number,
): boolean {
  state.sending = false
  state.sendError = ''
  if (!resp) return false
  if (resp.petName) state.petName = resp.petName
  if (resp.pid !== null && resp.pid !== undefined) state.pid = resp.pid
  if (ownerRowId !== undefined) {
    for (const row of state.messages) {
      if (row.id === ownerRowId) row.pending = false
    }
  }
  state.messages.push({
    id: resp.messageId === null || resp.messageId === undefined ? nextLocalId() : resp.messageId,
    role: ROLE_PET,
    kind: KIND_CHAT,
    content: resp.answer ?? '',
    createTime: new Date().toISOString(),
    latencyMs: resp.latencyMs ?? null,
  })
  if (typeof resp.quotaRemaining === 'number') {
    state.quota = { remaining: resp.quotaRemaining, desc: resp.quotaPeriodDesc ?? null }
  }
  return true
}

/**
 * 一问一答失败（额度用完 / 模型没接上话 / 网络断）。
 *
 * ⚠️ 这里有一个**真实存在的语义分叉**，前端必须处理，否则会骗用户：
 * 后端是**先落"主人那句"再调模型**（`AiPetChatService.chat`：appendOwnerMessage → callModel），
 * 所以 **B000902 / B000903（扣额度就失败）时库里什么都没有**，而 **B000907 / B000910（模型失败）时
 * 主人那句已经落库了**。判断办法只有一个：**重新拉一次历史**，看那句在不在。
 * - 不在 → 把原文放回输入框（用户不用重打）并说明"没发出去"
 * - 在   → 不回填（否则用户会看到"自己打的话"在框里、列表里也有一条，以为发了两遍），
 *          错误条里说明"它没接上话"
 *
 * @param rows 重新拉到的历史；传 null 表示**连重载都失败了**（那就不能断言发没发出去）
 * @param limit 历史接口上限（由页面从 `api/petChat.ts` 传进来，避免两处各写一个 200）
 */
export function applyChatFailure(
  state: PetChatState,
  e: unknown,
  sentText: string,
  rows: unknown,
  limit: number = PET_HISTORY_LIMIT,
): { restored: boolean; stored: boolean | null } {
  state.sending = false
  state.sendError = petErrorMessage(e)
  state.quotaOut = isQuotaExhausted(e)
  if (isUnauthorizedPetChat(e)) {
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
  const stored = (rows as PetChatRow[]).some((r) => r && r.role === ROLE_OWNER && r.content === sentText)
  if (stored) return { restored: false, stored: true }
  state.draft = sentText
  state.sendError = state.sendError + '（这句没发出去，已经放回输入框）'
  return { restored: true, stored: false }
}

/** `/vip/benefit` 读到额度 */
export function applyBenefit(state: PetChatState, data: { chatRemaining?: number | null; chatPeriodDesc?: string | null } | null | undefined): boolean {
  state.quotaError = ''
  if (!data) return false
  const remaining = typeof data.chatRemaining === 'number' ? data.chatRemaining : null
  state.quota = { remaining, desc: data.chatPeriodDesc ?? null }
  return remaining !== null
}

export function applyQuotaError(state: PetChatState, e: unknown): void {
  state.quotaError = petErrorMessage(e)
}

/**
 * 换一只宠物聊：把"和这只宠物有关"的东西全部清掉。
 * 不清的话上一只的气泡会留在新会话里（用户会以为它们共用一个对话）。
 */
export function switchPet(state: PetChatState, pid: number | null): void {
  state.pid = pid
  state.petName = ''
  state.messages = []
  state.historyLoading = true
  state.historyError = ''
  state.greetNote = ''
  state.truncated = false
  state.sending = false
  state.sendError = ''
  state.quotaOut = false
  state.draft = ''
}

/** 输入框到没到能发的程度（空串、发送中、未登录都不发） */
export function canSend(state: PetChatState): boolean {
  return Boolean(String(state.draft || '').trim()) && !state.sending && !state.needLogin
}

/** 超长提示（后端 B000909 会拒，前端提前说清"分几次说"；**不禁用发送**，阈值以真源码为准） */
export function lengthHint(text: string | null | undefined): string {
  const n = String(text || '').length
  if (n <= PET_CHAT_MAX_CHARS) return ''
  return `已经 ${n} 字，它一次最多听 ${PET_CHAT_MAX_CHARS} 字，分几次说吧`
}

/** 消息 → 气泡显示字段（模板只消费这些字段） */
export interface PetBubble {
  id: number
  isPet: boolean
  isGreeting: boolean
  name: string
  text: string
  timeText: string
  pending: boolean
  pendingText: string
}

/**
 * `content` 为 null 一律显示空串（**不许把 null 渲染成 "null"**）。
 * 宠物名：`petName` 为空才用「它」；后端回的 "未命名" **原样显示**（不替它起名）。
 */
export function petBubbles(state: PetChatState): PetBubble[] {
  const petName = state.petName || '它'
  return state.messages.map((row, i) => {
    const isPet = Boolean(row) && row.role === ROLE_PET
    const content = row && row.content !== null && row.content !== undefined ? String(row.content) : ''
    return {
      id: row && row.id !== undefined ? row.id : i,
      isPet,
      isGreeting: isPet && row.kind === KIND_GREETING,
      name: isPet ? petName : '',
      text: content,
      timeText: chatTimeText(row ? row.createTime : ''),
      pending: Boolean(row && row.pending),
      pendingText: '它正在想…',
    }
  })
}

/** 额度文案：**只用后端给的数字与周期描述**，不自己算限额、不提档位名 */
export function quotaText(quota: PetChatQuota | null | undefined): string {
  if (!quota) return ''
  const desc = quota.desc ? String(quota.desc) : ''
  const n = quota.remaining
  if (typeof n !== 'number') return ''
  if (n < 0) return '宠物聊天不限次数'
  if (n === 0) return desc ? `${desc}的聊天次数已用完` : '聊天次数已用完'
  return desc ? `${desc}还剩 ${n} 次聊天` : `还剩 ${n} 次聊天`
}

/** 额度没读到：给一句可见的说明 + 下一步（与 AI 问诊页同一套口径） */
export function quotaFailText(message: string, loggedIn: boolean): string {
  if (!message) return ''
  if (!loggedIn) return '还没登录，额度和聊天都要登录后才能用'
  if (String(message).indexOf('连不上后端') >= 0) return '额度没读到，连不上后端；聊天仍可以试'
  if (String(message).indexOf('卡住了') >= 0) return '额度没读到，这次请求卡住了；聊天仍可以试'
  return '额度没读到：' + message
}

/** 一次最多 200 条；真的顶到上限时如实说，别让用户以为"就聊过这些" */
export function historyLimitNote(count: number, limit: number = PET_HISTORY_LIMIT): string {
  const max = limit || PET_HISTORY_LIMIT
  if (typeof count !== 'number' || count < max) return ''
  return `接口一次最多回 ${max} 条，这里只显示了最近 ${max} 条`
}

/**
 * 时间文案（**自带实现，不 import `utils/ai.ts`**，理由见文件头）：
 * 刚发生的用"刚刚 / N 分钟前"，今天用 HH:mm，更早用 MM-DD。非法时间给空串，不渲染 "Invalid Date"。
 */
export function chatTimeText(iso: string | null | undefined): string {
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