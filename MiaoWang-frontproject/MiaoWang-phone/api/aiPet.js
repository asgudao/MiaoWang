/**
 * 电子宠物对话接口（手机端 / JS）—— 与「AI 问诊（RAG）」是**两个模块**，别混
 *
 * 服务名 `miaowang-ai-pet`（端口 8072，`utils/request.js:23` 里早就有 `SERVICE.aiPet`），
 * 前缀 `/mapleleaf/miaowang/v1/ai/pet`，三个端点**全部要登录**（网关白名单**故意没有**它们）：
 *
 *   POST /ai/pet/chat          主人先说，宠物回 —— **会消耗「宠物聊天」次数**（biz=chat）
 *   POST /ai/pet/greet         打开页面时宠物先开口 —— **不扣次数**（AiPetChatService.greet 全程无 quotaGuard）
 *   GET  /ai/pet/message       回放历史，时间正序，默认 50 / 上限 200
 *
 * ⚠️ **本项目刻意不做流式**（后端 `chatModel.call()` 非流式，回复只有一两句、平均 ~14 token / ~683ms）。
 * 这里**没有任何 SSE**：不 import `api/ai.js` 的流式那套、不用 `fetch`/`onChunk`/`renderjs`，
 * 一次请求拿一个完整对象就行。**新增"假流式"是明确禁止的**（门禁 `tools/check-ai-pet-phone.mjs` 会拦）。
 *
 * 为什么不追加进 `api/ai.js`：那个文件的 5 个函数 + 整套 SSE 解析全部只服务 `miaowang-ai-knowledge`（RAG），
 * 两个服务名、两种额度（rag / chat）、两种产品形态（话题制 / 一只宠物一条会话）。混在一个文件里，
 * 以后写路径白名单或做额度排查时极易串台；且 `api/ai.js` 是别的切片正在用的既有导出，**一行都不该动**。
 *
 * 真实契约（本轮读后端源码 + 真实抓包核对，与侦察报告一致的地方不再重复，**不一致的见下方注释**）：
 * - 入参：chat `{ pid?, message }`、greet `{ pid? }`（body 可整个不传）、message query `pid?`/`limit?`
 * - 返回：chat `{ answer, petName, pid, conversationId, messageId, latencyMs, quotaRemaining, quotaPeriodDesc }`、
 *         greet `{ greeting, petName, pid, conversationId, cached }`（`greeting` 可能为 **null**，那不是错误）、
 *         message `[{ id, role, kind, content, createTime }]`
 * - `role`：**1=主人 2=宠物**；`kind`：**0=一问一答 1=自动问候**
 * - `createTime` 是 Jackson 默认序列化的 `java.util.Date` → **ISO-8601 带时区**（本轮实测形状
 *   `2026-09-19T11:52:17.000+00:00`：DB 存 Asia/Shanghai 墙上时间，串里是同一时刻的 UTC 表现）
 * - `petName` 在宠物没起名时是字面量 **"未命名"**（`AiPetChatService.displayName`）
 * - `quotaRemaining` **`-1` = 不限次数**（判 `< 0`），`null` = 没取到（不影响聊天本身）
 *
 * ⚠️ **`pid` 缺省口径三处不同（Controller 注释 `AiPetController.java:82-85` 写明了）**：
 * chat / greet 不传 = 「**第一只**宠物」，message 不传 = 「**最近聊过的那只**」。
 * 所以本页**一律显式传 pid** 来消除歧义（页面拿不到 pid 时先读 `GET /pet` 取第一只，见 `pages/ai/pet.vue`）。
 *
 * ⚠️ **额度用尽只能按错误码判，绝不许匹配文案**：`B000902` 的 message 是 **vip 侧动态拼的**
 * （`VipQuotaServiceImpl.java:96` = `periodPrefix + bizDesc + "次数已用完，开通会员可提升次数"`，
 * 周期是 DAY 就是「今日…」、MONTH 就是「本月…」），而 `AiPetErrorCodeEnum.java:28` 那句
 * "今天和它聊天的次数用完了…" **只在 vip 没给 message 时兜底**（`PetChatQuotaGuard.java:61-65`）。
 * → 前端**原样透传 message**，只拿 `code === 'B000902'` 决定要不要给「去开通会员」的入口。
 */
import { api } from '../utils/request.js'

/** 历史一次能拉多少：后端 `normalizeLimit(limit, 50, 200)` 上限就是 200（AiPetConversationService.java:96/245-250） */
export const HISTORY_LIMIT = 200

/**
 * 超时：默认 15s 对"一次模型调用"够了，但后端**没有配任何模型超时**（侦察报告 A6：LlmProperties 无 timeout 字段），
 * 模型侧挂起时它会一直等 → 客户端给一个明确的天花板，超时后如实报"连不上/没回"。
 */
export const PET_CHAT_TIMEOUT = 60000

/** 主人先说，宠物回（消耗聊天次数） */
export function chatWithPet (pid, message) {
  // pid 未知时**整个字段不带**（不传 null）—— 让后端走它自己的默认口径，而不是我们编一个 pid
  const body = pid === null || pid === undefined ? { message: message } : { pid: pid, message: message }
  return api.post('aiPet', '/ai/pet/chat', { body: body, timeout: PET_CHAT_TIMEOUT })
}

/**
 * 宠物先开口（**不扣聊天次数**）。
 * body 语义是"我来了"，pid 未知时发 `{}`（Controller 是 `@RequestBody(required=false)`，空 body 也收）。
 */
export function greetPet (pid) {
  const body = pid === null || pid === undefined ? {} : { pid: pid }
  return api.post('aiPet', '/ai/pet/greet', { body: body, timeout: PET_CHAT_TIMEOUT })
}

/** 回放与这只宠物的历史（时间正序；`pid` 为 null 时 buildUrl 会自动不拼这个参数） */
export function listPetMessages (pid, limit) {
  return api.get('aiPet', '/ai/pet/message', { query: { pid: pid, limit: limit || HISTORY_LIMIT } })
}

/**
 * 「宠物聊天」额度（`chatLimit/chatPeriod/chatPeriodDesc/chatRemaining`，vip 侧早就在 `/vip/benefit` 回填了，
 * 不需要新接口）。这里自己写一行、**不 import `api/ai.js` 的 `getMyBenefit`**：那个文件属于 RAG 切片，
 * 本页只跟 ai-pet 打交道，少一条跨切片的 import。
 */
export function getChatQuota () {
  return api.get('vip', '/vip/benefit')
}