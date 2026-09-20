/**
 * 宠物对话接口（Web）—— 与「AI 问诊（RAG）」是**两个模块**，别混
 *
 * 服务名 `miaowang-ai-pet`（`api/request.ts:20` 早就登记了 `aiPet`），
 * 前缀 `/mapleleaf/miaowang/v1/ai/pet`，三个端点**全部要登录**（网关白名单**故意没有**它们）：
 *
 *   POST /ai/pet/chat          主人先说，宠物回 —— **会消耗「宠物聊天」次数**（vip 侧 biz=chat）
 *   POST /ai/pet/greet         打开页面时宠物先开口 —— **不扣次数**（AiPetChatService.greet 全程没有 quotaGuard）
 *   GET  /ai/pet/message       回放历史，时间正序，默认 50 / 上限 200
 *
 * 契约逐条来自后端真源码（`AiPetController.java:41/54/70/87`、三个 req/resp DTO），
 * 并在 2026-09-20 用探针账号经网关真调用核对过（见 `.dsh-tmp/ai-pet-web-20260921/probe-e2e.json`）：
 * - 入参：chat `{ pid?, message }`、greet `{ pid? }`（body 可整个不传）、message query `pid?`/`limit?`
 * - 返回：chat `{ answer, petName, pid, conversationId, messageId, latencyMs, quotaRemaining, quotaPeriodDesc }`、
 *         greet `{ greeting, petName, pid, conversationId, cached }`（`greeting` 可能为 **null**，那不是错误）、
 *         message `[{ id, role, kind, content, createTime }]`
 * - `role`：**1=主人 2=宠物**；`kind`：**0=一问一答 1=自动问候**
 * - `petName` 在宠物没起名时是字面量 **"未命名"**（`AiPetChatService.displayName`），前端**原样显示，不替它编名字**
 * - `quotaRemaining` **-1 = 不限次数**（判 `< 0`），`null` = 没取到（不影响聊天本身）
 * - `createTime` 是 Jackson 默认序列化的 `java.util.Date` → **ISO-8601 带时区**
 *   （实测形状 `2026-09-20T14:21:29.000+00:00`：DB 存 Asia/Shanghai 墙上时间，串里是同一时刻的 UTC 表现）
 *
 * ⚠️ **`pid` 缺省口径三处不同**（`AiPetController.java:82-85` 写明了）：
 * chat / greet 不传 = 「**第一只**宠物」，message 不传 = 「**最近聊过的那只**」。
 * 所以本页**一律显式传 pid** 来消除歧义（拿不到 pid 时先读 `GET /pet` 取第一只，见 `views/PetChatView.vue`）。
 *
 * ⚠️ **额度用尽只能按错误码判，绝不许匹配文案**：`B000902` 的 message 是 **vip 侧动态拼的**
 * （`VipQuotaServiceImpl` 的 `periodPrefix + bizDesc + "次数已用完，开通会员可提升次数"`，
 * 周期是 DAY 就是「今日…」、MONTH 就是「本月…」），而 `AiPetErrorCodeEnum.java:28` 那句
 * "今天和它聊天的次数用完了…" **只在 vip 没给 message 时兜底**（`PetChatQuotaGuard`）。
 * → 前端**原样透传 message**，只拿 `code === 'B000902'` 决定要不要给「去开通会员」的入口。
 *
 * **本项目刻意不做流式**：后端 `chatModel.call()` 是非流式（回复一两句，实测 454ms），
 * 一次请求拿一个完整对象就够；新增"假流式"是明确禁止的（本切片的门禁会拦）。
 *
 * 为什么不把这些函数追加进 `api/ai.ts`：那个文件的 5 个函数 + 整套 SSE 解析全部只服务
 * `miaowang-ai-knowledge`（RAG）。两个服务名、两种额度（rag / chat）、两种产品形态
 * （一个话题一条会话 / 一只宠物一条会话）。混在一起以后写路径白名单或排查额度时极易串台。
 */
import { ApiError, api } from './request'
import { getMyBenefit, type VipBenefit } from './vip'
import type { PetChatRow } from '@/utils/petChat'

/** 历史一次能拉多少：后端 `normalizeLimit(limit, 50, 200)` 上限就是 200 */
export const PET_HISTORY_LIMIT = 200

/**
 * 客户端天花板：默认 15s 对"一次模型调用"够了，但后端**没有配任何模型超时**
 * （`LlmProperties` 没有 timeout 字段，模型侧挂起时它会一直等）→ 给一个明确的上限，
 * 到点就放弃并把"卡住了"如实说出来，而不是让页面永远停在"加载中"。
 * 与手机端 `api/aiPet.js` 的 `PET_CHAT_TIMEOUT = 60000` 同值。
 */
export const PET_CHAT_TIMEOUT_MS = 60_000

/** `POST /ai/pet/chat` 的响应（`AiPetChatRespDTO`） */
export interface PetChatResult {
  answer: string
  petName: string | null
  pid: number | null
  conversationId: number | null
  messageId: number | null
  /** 本次生成耗时（毫秒） */
  latencyMs: number | null
  /** 本次调用**之后**还剩几次；-1 = 不限；null = 没取到 */
  quotaRemaining: number | null
  quotaPeriodDesc: string | null
}

/** `POST /ai/pet/greet` 的响应（`AiPetGreetRespDTO`） */
export interface PetGreetResult {
  /** **可能为 null**（模型抖动 / 该时段已开过口且内容为空）→ 这不是错误，不显示气泡即可 */
  greeting: string | null
  petName: string | null
  pid: number | null
  conversationId: number | null
  /** 本次是否复用了已有内容（缓存命中，或这一时段宠物已经说过话） */
  cached: boolean | null
}

/**
 * 一次调用的"客户端天花板"。
 *
 * `request()` 把所有 fetch 异常统一转成 `NETWORK`（文案是"连不上后端（url）：Vite 代理或后端进程没起"），
 * 那对"断网/代理没起"是准的，但对"后端收到了、模型卡住了"就不准了。
 * 所以这里按信号是否被**我们自己**掐断来区分，并给一个专门的 `TIMEOUT` 码与文案。
 */
async function withTimeout<T>(run: (signal: AbortSignal) => Promise<T>): Promise<T> {
  const signal = AbortSignal.timeout(PET_CHAT_TIMEOUT_MS)
  try {
    return await run(signal)
  } catch (e) {
    if (signal.aborted) {
      throw new ApiError(
        'TIMEOUT',
        `等了 ${PET_CHAT_TIMEOUT_MS / 1000} 秒后端还是没回，这次调用卡住了（不是网络断了），可以直接重试`,
        e,
      )
    }
    throw e
  }
}

/** 主人先说，宠物回（**消耗聊天次数**） */
export function chatWithPet(pid: number | null, message: string) {
  // pid 未知时**整个字段不带**（不传 null）—— 让后端走它自己的默认口径，而不是我们编一个 pid
  const body = pid === null ? { message } : { pid, message }
  return withTimeout((signal) => api.post<PetChatResult>('aiPet', '/ai/pet/chat', { body, signal }))
}

/**
 * 宠物先开口（**不扣聊天次数**）。
 * body 语义是"我来了"，pid 未知时发 `{}`（Controller 是 `@RequestBody(required=false)`，空 body 也收）。
 */
export function greetPet(pid: number | null = null) {
  const body = pid === null ? {} : { pid }
  return withTimeout((signal) => api.post<PetGreetResult>('aiPet', '/ai/pet/greet', { body, signal }))
}

/** 回放与这只宠物的历史（时间正序；`pid` 为 null 时 `buildUrl` 会自动不拼这个参数） */
export function listPetMessages(pid: number | null, limit: number = PET_HISTORY_LIMIT) {
  return api.get<PetChatRow[]>('aiPet', '/ai/pet/message', { query: { pid, limit } })
}

/**
 * 「宠物聊天」额度：`chatLimit / chatPeriod / chatPeriodDesc / chatUsed / chatRemaining`
 * vip 侧早就回填进 `GET /vip/benefit`（`UserVipBenefitRespDTO`），**不需要新接口**。
 * 这里直接复用 `api/vip.ts` 的 `getMyBenefit`，不另写一份重复的请求（与手机端刻意各写一份的原因不同：
 * Web 侧 `api/vip.ts` 本来就是共享模块，多写一份反而会漂移）。
 */
export function getChatQuota(): Promise<VipBenefit> {
  return getMyBenefit()
}