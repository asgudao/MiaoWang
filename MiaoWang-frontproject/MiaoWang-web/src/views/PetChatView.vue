<script setup lang="ts">
/**
 * 宠物对话（Web）—— 档 1：**非流式**一问一答 + 宠物人设 + 会话历史
 *
 * 与 `AiChatView.vue`（AI 问诊 = ai-knowledge 的 RAG）是**两个模块、两个服务名**：
 * 这里是 `miaowang-ai-pet`，**没有知识库检索、没有 sources、没有思考过程，也没有流式**。
 * 后端就是一次 `chatModel.call()`（回复一两句，实测 454ms / 21 token 级别），做流式只增加复杂度，
 * 所以本页**没有 fetch / ReadableStream / onChunk**，一次请求拿一个完整对象。
 *
 * 状态机不在这个文件里：`utils/petChat.ts` 是**可离线执行的纯函数**（门禁拿真实 fixture 真跑它），
 * 页面只做三件事 —— 发请求、把结果交给那些函数、把 `chat.*` 显示出来。
 *
 * 三条不许违反的口径（都有后端源码出处，见 `api/petChat.ts` 顶部）：
 * 1) **必须真的发请求**：页面里没有任何编造的回复、没有 `MOCK`、没有"假流式"、没有 `setTimeout` 假打字。
 * 2) **失败如实显示**：错误文案优先用后端原文；额度用尽**按错误码 `B000902` 判**，不匹配文案。
 *    绝不把失败渲染成"宠物不想说话"这类拟人化状态 —— 那会把"后端挂了"骗成"宠物有情绪"。
 * 3) **加载 / 失败 / 空**三态必须能一眼分开：分别是骨架 + "正在读你们的对话"、错误原文 + 重试、
 *    "还没跟它说过话"的引导。空态**不预置任何演示气泡**。
 *
 * 进页面只在"历史为空"时调一次 `/greet`（宠物先开口，**不消耗聊天次数**）；
 * 同一时段后端只会生成一句（`cached=true` 的高频返回是正常的），所以不用担心重复打扰。
 */
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { PhArrowClockwise, PhCat, PhChatCircleDots, PhPaperPlaneRight } from '@phosphor-icons/vue'
import {
  PET_HISTORY_LIMIT,
  chatWithPet,
  getChatQuota,
  greetPet,
  listPetMessages,
} from '@/api/petChat'
import { listMyPets, type Pet } from '@/api/pet'
import { useAuthStore } from '@/stores/auth'
import {
  NO_PROFILE_TEXT,
  applyBenefit,
  applyChatFailure,
  applyChatSuccess,
  applyGreet,
  applyGreetError,
  applyHistory,
  applyHistoryError,
  applyQuotaError,
  beginSend,
  canSend as canSendState,
  historyLimitNote,
  initialPetChatState,
  isNoProfile,
  isUnauthorizedPetChat,
  lengthHint as lengthHintOf,
  petBubbles,
  quotaFailText,
  quotaText,
  switchPet,
} from '@/utils/petChat'

const route = useRoute()
const auth = useAuthStore()

/** 全部对话状态（字段清单见 `utils/petChat.ts` 的 initialPetChatState） */
const chat = ref(initialPetChatState())
/** 我的宠物：用于（多只时）选择在跟谁说话，也是"有没有档案"的唯一判据 */
const pets = ref<Pet[]>([])
const thread = ref<HTMLElement | null>(null)

/** 空态里的开场建议：点一下只**填进输入框**，要不要发由用户决定（发一次就是一次聊天次数） */
const EXAMPLES = ['你叫什么名字', '你今年多大了', '你还记得我刚才说什么吗']

const bubbles = computed(() => petBubbles(chat.value))
const quotaLine = computed(() => quotaText(chat.value.quota))
const quotaFailLine = computed(() => quotaFailText(chat.value.quotaError, auth.isLoggedIn))
/** 该不该引导开通会员：剩余正好为 0，或者这一轮就是被 B000902 挡回来的（不限次数是 -1，所以判 == 0） */
const needsVip = computed(() => chat.value.quota?.remaining === 0 || chat.value.quotaOut)
const noProfileText = computed(() => NO_PROFILE_TEXT)
const limitNote = computed(() => historyLimitNote(chat.value.messages.length, PET_HISTORY_LIMIT))
const hint = computed(() => lengthHintOf(chat.value.draft))
const sendable = computed(() => canSendState(chat.value))
const placeholder = computed(() =>
  chat.value.sending ? '它正在想，等它说完这一句' : '跟它说点什么，回车发送',
)
/** 真的没聊过（既没读到错误、也不在加载、一条消息都没有） */
const isEmptyThread = computed(
  () => !chat.value.historyLoading && !chat.value.historyError && chat.value.messages.length === 0,
)
/** 空态引导：没有档案时不说"还没跟它说过话"（那是两回事），所以档案为空时不显示 */
const showIntro = computed(() => isEmptyThread.value && chat.value.hasProfile !== false)

function scrollToBottom() {
  void nextTick(() => {
    const el = thread.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

watch(
  () => [chat.value.messages.length, chat.value.messages.at(-1)?.content?.length ?? 0],
  () => scrollToBottom(),
)

onMounted(async () => {
  if (!auth.isLoggedIn) {
    // 软门槛：未登录**一个请求都不发**（省掉三条必然 state:-1 的往返）
    chat.value.needLogin = true
    return
  }
  const raw = Number(route.params.pid ?? 0)
  chat.value.pid = Number.isFinite(raw) && raw > 0 ? raw : null
  void reloadQuota()
  await resolvePet()
  await loadHistory()
})

/**
 * 读我的宠物：提供 pid / 名字，也是"有没有档案"的唯一判据。
 * 读不到就保持 `hasProfile = null`（**不猜**），页面照常能聊，只是名字先叫「它」。
 */
async function resolvePet() {
  try {
    const rows = await listMyPets()
    const list = Array.isArray(rows) ? rows : []
    pets.value = list
    chat.value.hasProfile = list.length > 0
    const first = list[0]
    if (chat.value.pid === null && first) chat.value.pid = first.pid
    if (chat.value.pid !== null) {
      const hit = list.find((p) => p.pid === chat.value.pid)
      if (hit && hit.pName) chat.value.petName = hit.pName
    }
  } catch (e) {
    if (isUnauthorizedPetChat(e)) {
      chat.value.needLogin = true
      return
    }
    // 宠物档案服务抖动不该让"聊天"这一页打不开：名字退回「它」，其余照常
    chat.value.hasProfile = null
  }
}

/** 换一只宠物聊：先清掉上一只的气泡，再按新的 pid 重读历史 */
async function pickPet(pid: number) {
  if (chat.value.pid === pid) return
  switchPet(chat.value, pid)
  const hit = pets.value.find((p) => p.pid === pid)
  if (hit && hit.pName) chat.value.petName = hit.pName
  await loadHistory()
}

async function loadHistory() {
  chat.value.historyLoading = true
  chat.value.historyError = ''
  try {
    const rows = await listPetMessages(chat.value.pid, PET_HISTORY_LIMIT)
    applyHistory(chat.value, rows, PET_HISTORY_LIMIT)
    // 空态才让宠物先开口（**/greet 不扣次数**）；已经聊过就不打扰它了
    if (!rows || rows.length === 0) {
      if (chat.value.hasProfile !== false) await greet()
    }
  } catch (e) {
    if (isUnauthorizedPetChat(e)) {
      chat.value.needLogin = true
      chat.value.historyLoading = false
      return
    }
    if (isNoProfile(e)) chat.value.hasProfile = false
    applyHistoryError(chat.value, e)
  }
}

/** 宠物主动开口：失败只进静音提示（`greeting = null` 也是正常的），整页不会因此变成错误页 */
async function greet() {
  try {
    const resp = await greetPet(chat.value.pid)
    applyGreet(chat.value, resp)
  } catch (e) {
    if (isUnauthorizedPetChat(e)) {
      chat.value.needLogin = true
      return
    }
    if (isNoProfile(e)) chat.value.hasProfile = false
    applyGreetError(chat.value, e)
  }
}

async function reloadQuota() {
  if (!auth.isLoggedIn) return
  try {
    applyBenefit(chat.value, await getChatQuota())
  } catch (e) {
    applyQuotaError(chat.value, e)
  }
}

/** 空态建议只是"填进输入框"，不替用户把次数花掉 */
function applyExample(text: string) {
  chat.value.draft = text
}

/**
 * 发送：先乐观插一条，再等后端那一次完整回复（**非流式**）。
 * 失败要重新拉一次历史来分辨"那句到底落库没有"——见 `utils/petChat.ts` 的 applyChatFailure 注释。
 */
async function send() {
  const text = String(chat.value.draft || '').trim()
  if (!text || chat.value.sending || chat.value.needLogin) return
  const row = beginSend(chat.value, text)
  scrollToBottom()
  try {
    const resp = await chatWithPet(chat.value.pid, text)
    applyChatSuccess(chat.value, resp, row.id)
  } catch (e) {
    // 未登录/过期：再拉一次历史必然也是同一个错，别白打一次请求
    if (isUnauthorizedPetChat(e)) {
      applyChatFailure(chat.value, e, text, null, PET_HISTORY_LIMIT)
      return
    }
    let rows: unknown = null
    try {
      rows = await listPetMessages(chat.value.pid, PET_HISTORY_LIMIT)
    } catch (e2) {
      if (isUnauthorizedPetChat(e2)) {
        applyChatFailure(chat.value, e2, text, null, PET_HISTORY_LIMIT)
        return
      }
      rows = null
    }
    applyChatFailure(chat.value, e, text, rows, PET_HISTORY_LIMIT)
  }
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    void send()
  }
}
</script>

<template>
  <div class="mw-container mw-page">
    <header class="head">
      <div class="head__intro">
        <h1 class="mw-h1">宠物对话</h1>
        <p class="mw-caption">
          回复来自真实的大模型调用。它知道自己叫什么、你怎么叫它，也记得你们最近聊过什么。
        </p>
      </div>
      <div class="head__right">
        <span v-if="quotaLine || quotaFailLine" class="quota">{{ quotaLine || quotaFailLine }}</span>
        <button v-if="quotaFailLine" class="mw-link" type="button" @click="reloadQuota">
          <PhArrowClockwise :size="14" aria-hidden="true" />
          重试额度
        </button>
        <RouterLink v-if="needsVip" class="mw-link" :to="{ name: 'vip' }">去开通会员</RouterLink>
      </div>
    </header>

    <!-- 未登录软门槛：三个端点经网关全部要登录，这里一个请求都不发 -->
    <div v-if="chat.needLogin" class="mw-state mw-state--box">
      <PhChatCircleDots :size="28" weight="regular" aria-hidden="true" />
      <p class="mw-state__title">登录后就能跟它说话</p>
      <p>它记得你说过什么，所以对话按账号走，这里没有游客模式。</p>
      <RouterLink
        class="mw-btn mw-btn--primary"
        :to="{ name: 'login', query: { redirect: route.fullPath } }"
      >
        去登录
      </RouterLink>
    </div>

    <template v-else>
      <!-- 多只宠物才给选择：chat / greet 的 pid 缺省口径是"第一只"，显式传才没有歧义 -->
      <section v-if="pets.length > 1" class="pick">
        <p class="mw-caption">在跟谁说话</p>
        <div class="mw-chips">
          <button
            v-for="p in pets"
            :key="p.pid"
            class="mw-chip"
            :class="{ 'mw-chip--on': p.pid === chat.pid }"
            type="button"
            @click="pickPet(p.pid)"
          >
            {{ p.pName || '未命名宠物' }}
          </button>
        </div>
      </section>

      <section class="panel">
        <div ref="thread" class="thread" role="log" aria-live="polite" aria-label="与它的对话">
          <!-- 加载态：骨架 + 明确一句话，和"失败""空"长得完全不一样 -->
          <div v-if="chat.historyLoading" class="loading">
            <p class="mw-caption">正在读你们的对话</p>
            <div v-for="i in 3" :key="i" class="mw-skeleton load-skeleton" />
          </div>

          <!-- 失败态：后端原文 + 重试；连档案都没有时同时给建宠物的真去处 -->
          <div v-else-if="chat.historyError" class="mw-state mw-state--box">
            <p class="mw-state__title">对话没读出来</p>
            <p class="err">{{ chat.historyError }}</p>
            <div class="acts">
              <button class="mw-btn mw-btn--ghost" type="button" @click="loadHistory">
                <PhArrowClockwise :size="16" aria-hidden="true" />
                重试
              </button>
              <RouterLink v-if="chat.hasProfile === false" class="mw-btn mw-btn--primary" to="/pets/new">
                去添加宠物
              </RouterLink>
            </div>
          </div>

          <template v-else>
            <!-- 没有宠物档案：文案就是后端 B000906 的原文，去处是真能建宠物的页面 -->
            <div v-if="chat.hasProfile === false" class="mw-state mw-state--box">
              <PhCat :size="28" weight="regular" aria-hidden="true" />
              <p class="mw-state__title">{{ noProfileText }}</p>
              <p>它只能聊自己的事，所以先有一份档案，它才有名字、品种和年龄可说。</p>
              <RouterLink class="mw-btn mw-btn--primary" to="/pets/new">去添加宠物</RouterLink>
            </div>

            <!-- 空态：真的没聊过（和"失败"是两回事），不预置任何演示气泡 -->
            <div v-if="showIntro" class="intro">
              <PhChatCircleDots :size="28" weight="regular" aria-hidden="true" />
              <p class="intro__title">还没跟它说过话</p>
              <p class="intro__desc">它知道自己的名字、品种、年龄，也知道你怎么叫它。</p>
              <ul class="intro__list">
                <li v-for="q in EXAMPLES" :key="q">
                  <button class="intro__pick" type="button" @click="applyExample(q)">{{ q }}</button>
                </li>
              </ul>
              <p class="intro__hint">点一下只是填进输入框，按发送才算一次聊天</p>
            </div>

            <article
              v-for="b in bubbles"
              :key="b.id"
              class="msg"
              :class="b.isPet ? 'msg--pet' : 'msg--me'"
            >
              <div v-if="b.isPet" class="msg__who">
                <span class="msg__name">{{ b.name }}</span>
                <span v-if="b.isGreeting" class="msg__tag">它先开口</span>
              </div>
              <div class="bubble" :class="b.isPet ? 'bubble--pet' : 'bubble--me'">
                <p v-if="b.pending && !b.text" class="bubble__pending">{{ b.pendingText }}</p>
                <p v-else class="bubble__text">{{ b.text }}</p>
              </div>
              <span class="msg__time">{{ b.timeText }}</span>
            </article>

            <!-- 问候没生成出来 / 历史被截断：静音说明，绝不把整页变成错误页 -->
            <p v-if="chat.greetNote" class="note">{{ chat.greetNote }}</p>
            <p v-if="limitNote" class="note">{{ limitNote }}</p>
          </template>
        </div>

        <!-- 发送失败：后端文案原样展示；额度用完时额外给「去开通会员」这一个动作 -->
        <p v-if="chat.sendError" class="mw-alert mw-alert--danger send-err">
          <span>{{ chat.sendError }}</span>
          <RouterLink v-if="chat.quotaOut" class="mw-link" :to="{ name: 'vip' }">
            去开通会员
          </RouterLink>
        </p>

        <div class="composer">
          <textarea
            v-model="chat.draft"
            class="composer__input"
            rows="2"
            :placeholder="placeholder"
            :disabled="chat.sending"
            @keydown="onKeydown"
          />
          <button
            class="mw-btn mw-btn--primary composer__send"
            type="button"
            :disabled="!sendable"
            @click="send"
          >
            <PhPaperPlaneRight :size="16" weight="fill" aria-hidden="true" />
            {{ chat.sending ? '发送中' : '发送' }}
          </button>
        </div>
        <p v-if="hint" class="mw-field__error">{{ hint }}</p>
        <p class="mw-caption composer__note">
          聊天会消耗次数，它主动打招呼不消耗。生成回复时它只看你们最近几轮，更早的话记不住。
        </p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--mw-space-4);
  margin-bottom: var(--mw-space-6);
}
.head__intro {
  min-width: 0;
  max-width: 62ch;
}
.head__right {
  display: flex;
  flex: none;
  align-items: center;
  gap: var(--mw-space-3);
}
.quota {
  padding: 4px 10px;
  border-radius: var(--mw-radius-tag);
  background: var(--mw-canvas);
  font-size: var(--mw-font-micro);
  color: var(--mw-ink-2);
}

/* 多只宠物时的选择条：只在真有两只以上时出现 */
.pick {
  margin-bottom: var(--mw-space-4);
}
.pick .mw-chips {
  margin-top: var(--mw-space-2);
}

.panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: var(--mw-space-4);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
  background: var(--mw-surface);
}
.thread {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-4);
  min-height: 320px;
  max-height: 62vh;
  overflow: auto;
  padding-right: 4px;
}

/* ---- 三态之加载 ---- */
.loading {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-2);
}
.load-skeleton {
  height: 52px;
}

/* ---- 三态之失败 ---- */
.err {
  color: var(--mw-danger);
}
.acts {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mw-space-2);
  justify-content: center;
}

/* ---- 三态之空 ---- */
.intro {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--mw-space-3);
  padding: var(--mw-space-8) var(--mw-space-4);
  color: var(--mw-ink-2);
  text-align: center;
}
.intro__title {
  font-size: var(--mw-font-caption);
  font-weight: 600;
  color: var(--mw-ink);
}
.intro__desc {
  font-size: var(--mw-font-caption);
}
.intro__list {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--mw-space-2);
}
.intro__pick {
  padding: 8px 12px;
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-pill);
  background: transparent;
  color: var(--mw-ink);
  font-family: inherit;
  font-size: var(--mw-font-caption);
  cursor: pointer;
}
.intro__pick:hover {
  background: var(--mw-canvas);
}
.intro__hint {
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}

/* ---- 消息 ---- */
.msg {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
.msg--me {
  align-items: flex-end;
}
.msg__who {
  display: flex;
  align-items: center;
  gap: var(--mw-space-2);
  margin-bottom: var(--mw-space-1);
}
.msg__name {
  font-size: var(--mw-font-micro);
  color: var(--mw-ink-2);
}
/* 「它先开口」标记：把 kind=1 的自动问候和普通回答分开，别让用户以为自己在自言自语 */
.msg__tag {
  padding: 2px 8px;
  border-radius: var(--mw-radius-tag);
  background: rgba(194, 65, 12, 0.12);
  font-size: var(--mw-font-micro);
  font-weight: 600;
  color: var(--mw-brand-ink);
}
.bubble {
  max-width: min(72ch, 78%);
  padding: var(--mw-space-3) var(--mw-space-4);
  border-radius: var(--mw-radius-card);
}
.bubble--pet {
  background: var(--mw-canvas);
  border: 1px solid var(--mw-line);
}
/* 主人自己的气泡：白字只放 --mw-brand-ink（设计系统硬规则 1） */
.bubble--me {
  background: var(--mw-brand-ink);
}
.bubble__text {
  font-size: var(--mw-font-body);
  line-height: 1.75;
  color: var(--mw-ink);
  word-break: break-word;
  white-space: pre-wrap;
}
.msg--me .bubble__text {
  color: #ffffff;
}
.bubble__pending {
  font-size: var(--mw-font-body);
  line-height: 1.75;
  color: var(--mw-muted);
}
.msg__time {
  margin-top: var(--mw-space-1);
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}

.note {
  font-size: var(--mw-font-micro);
  line-height: 1.7;
  color: var(--mw-muted);
}

/* ---- 发送失败 ---- */
.send-err {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--mw-space-3);
  margin-top: var(--mw-space-3);
}

/* ---- 输入区 ---- */
.composer {
  display: flex;
  align-items: flex-end;
  gap: var(--mw-space-2);
  margin-top: var(--mw-space-4);
}
.composer__input {
  flex: 1;
  min-width: 0;
  min-height: 44px;
  max-height: 160px;
  padding: var(--mw-space-3);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-input);
  background: var(--mw-canvas);
  color: var(--mw-ink);
  font-family: inherit;
  font-size: var(--mw-font-body);
  resize: vertical;
}
.composer__input:focus {
  border-color: var(--mw-brand-ink);
  outline: none;
  background: var(--mw-surface);
}
.composer__send {
  flex: none;
}
.composer__note {
  margin-top: var(--mw-space-2);
}

@media (max-width: 720px) {
  .head {
    flex-direction: column;
    align-items: flex-start;
  }
  .bubble {
    max-width: 92%;
  }
}
</style>