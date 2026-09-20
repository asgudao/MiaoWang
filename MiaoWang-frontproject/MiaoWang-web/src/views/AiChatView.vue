<script setup lang="ts">
/**
 * AI 问诊（Web）
 *
 * 一个页面装三件事：会话列表、消息流、提问框。流式回答一次十几秒，所以：
 * - 发送后立刻插入"用户提问 + 空 AI 气泡（进行态）"，不等后端
 * - 支持「停止」（断开 SSE）
 * - 半途失败保留已生成的内容，只把错误附在气泡下面
 *
 * 额度是硬约束（免费档 5 次/月），页头常显剩余次数，`B000702` 时给开通会员的引导。
 */
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import {
  PhArrowRight,
  PhBrain,
  PhChatCircleDots,
  PhPlus,
  PhStop,
  PhThumbsDown,
  PhThumbsUp,
  PhTrash,
} from '@phosphor-icons/vue'
import { useAiStore, type ChatMessage } from '@/stores/ai'
import { useAuthStore } from '@/stores/auth'
import { FEEDBACK_DOWN, FEEDBACK_UP, ROLE_AI } from '@/api/ai'
import { citationCounts, parseAnswer, relativeTime, speciesLabel } from '@/utils/ai'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const ai = useAiStore()
const { conversations, listLoading, listError, messages, messagesLoading, streaming, quota } =
  storeToRefs(ai)

const draft = ref('')
const rateError = ref('')
const confirmDelete = ref<number | null>(null)
const deleting = ref(false)
const thread = ref<HTMLElement | null>(null)

/* ---- 思考过程：思考模型要跑十几秒，这段时间必须有东西在动 ---- */

/** 这一轮流式已经跑了多少秒 */
const elapsed = ref(0)
let elapsedTimer: ReturnType<typeof setInterval> | null = null

/** 思考块的折叠状态：进行中默认展开，正文开始后默认收起；用户点过就以用户的为准 */
const thinkOpen = ref<Record<number, boolean>>({})

function thinkExpanded(m: ChatMessage) {
  const v = thinkOpen.value[m.id]
  return v === undefined ? !m.thinkingDone : v
}
function toggleThink(m: ChatMessage) {
  thinkOpen.value = { ...thinkOpen.value, [m.id]: !thinkExpanded(m) }
}

/* ---- 引用角标：点回答里的 [n] 高亮对应的来源 ---- */

const focused = ref<{ id: number; n: number } | null>(null)

function focusSource(m: ChatMessage, n: number) {
  const same = focused.value !== null && focused.value.id === m.id && focused.value.n === n
  focused.value = same ? null : { id: m.id, n }
}
function isFocused(m: ChatMessage, n: number) {
  return focused.value?.id === m.id && focused.value.n === n
}

const EXAMPLES = [
  '三个月大的猫该打什么疫苗？',
  '狗狗一直呕吐怎么办？',
  '猫可以喝牛奶吗？',
  '怎么判断猫咪发烧了？',
]

const isEmptyThread = computed(() => !messagesLoading.value && messages.value.length === 0)
const quotaLabel = computed(() => {
  const q = quota.value
  if (!q) return ''
  if (q.remaining < 0) return 'AI 问答不限次数'
  return `本${q.desc || '月'}还剩 ${q.remaining} 次问答`
})
const isVip = computed(() => auth.isLoggedIn && (quota.value?.remaining ?? 0) < 0)
/**
 * 该不该引导开通会员：剩余次数正好为 0，或者这一轮就是被 B000702 挡回来的。
 * （不限次数是 -1，所以判断 == 0 而不是 <= 0；未登录时 quota 为 null，不会误亮）
 */
const needsVip = computed(
  () => quota.value?.remaining === 0 || messages.value.some((m) => m.quotaError),
)

function scrollToBottom() {
  void nextTick(() => {
    const el = thread.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

/** 增量刷新时也跟着滚（思考过程也在增量刷新，所以要一起看） */
watch(
  () => [
    messages.value.length,
    messages.value.at(-1)?.content.length ?? 0,
    messages.value.at(-1)?.thinking?.length ?? 0,
  ],
  () => scrollToBottom(),
)

/** 流式秒表 */
watch(streaming, (on) => {
  if (elapsedTimer) {
    clearInterval(elapsedTimer)
    elapsedTimer = null
  }
  if (on) {
    elapsed.value = 0
    elapsedTimer = setInterval(() => {
      elapsed.value += 1
    }, 1000)
  }
})
onUnmounted(() => {
  if (elapsedTimer) clearInterval(elapsedTimer)
})

onMounted(async () => {
  await Promise.all([ai.fetchConversations(), ai.fetchQuota()])
  const id = Number(route.params.conversationId ?? 0)
  if (id > 0) await ai.open(id)
  scrollToBottom()
})

watch(
  () => route.params.conversationId,
  async (v) => {
    const id = Number(v ?? 0)
    if (id > 0 && id !== ai.currentId) await ai.open(id)
    if (!id && ai.currentId !== null) ai.startNew()
  },
)

async function send(text?: string) {
  const q = (text ?? draft.value).trim()
  if (!q || streaming.value) return
  draft.value = ''
  rateError.value = ''
  await ai.ask(q)
  // 新会话落库后把地址换成带 id 的，刷新页面还能回到这个会话
  if (ai.currentId && !route.params.conversationId) {
    void router.replace({ name: 'ai-chat', params: { conversationId: ai.currentId } })
  }
  await ai.fetchConversations()
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    void send()
  }
}

function startNew() {
  ai.startNew()
  void router.replace({ name: 'ai' })
}

async function rate(messageId: number, type: 1 | 2) {
  rateError.value = ''
  try {
    await ai.rate(messageId, type)
  } catch (e) {
    rateError.value = e instanceof Error ? e.message : '评价失败，稍后再试'
  }
}

async function remove(id: number) {
  if (confirmDelete.value !== id) {
    confirmDelete.value = id
    return
  }
  deleting.value = true
  try {
    await ai.remove(id)
    if (Number(route.params.conversationId ?? 0) === id) void router.replace({ name: 'ai' })
  } catch (e) {
    rateError.value = e instanceof Error ? e.message : '删除失败，稍后再试'
  } finally {
    deleting.value = false
    confirmDelete.value = null
  }
}
</script>

<template>
  <div class="mw-container mw-page">
    <header class="head">
      <div>
        <h1 class="mw-h1">AI 问诊</h1>
        <p class="mw-caption">基于知识库检索的宠物健康问答，答案仅供参考，严重情况请就医</p>
      </div>
      <div class="head__right">
        <span v-if="quotaLabel" class="quota" :class="{ 'quota--vip': isVip }">{{
          quotaLabel
        }}</span>
        <!-- 次数用完时的落地入口：以前只说"开通会员可以继续问"，但全站没有开通入口 -->
        <RouterLink v-if="needsVip" class="head__vip" :to="{ name: 'vip' }">去开通会员</RouterLink>
        <button class="mw-btn mw-btn--primary" type="button" @click="startNew">
          <PhPlus :size="16" weight="bold" aria-hidden="true" />
          新对话
        </button>
      </div>
    </header>

    <p v-if="!auth.isLoggedIn" class="mw-alert mw-alert--info">
      <span>AI 问答按账号计次，登录后才能提问。</span>
      <RouterLink class="mw-btn mw-btn--ghost" :to="{ name: 'login', query: { redirect: '/ai' } }">
        去登录
      </RouterLink>
    </p>

    <div class="layout">
      <!-- 会话列表 -->
      <aside class="side">
        <p class="side__title">历史对话</p>
        <p v-if="listLoading && !conversations.length" class="mw-caption">正在读取</p>
        <p v-else-if="listError" class="mw-field__error">{{ listError }}</p>
        <p v-else-if="!conversations.length" class="mw-caption">
          还没有对话记录，从右边问第一个问题吧
        </p>
        <ul v-else class="side__list">
          <li v-for="c in conversations" :key="c.id" class="conv">
            <RouterLink
              class="conv__main"
              :class="{ 'conv__main--on': c.id === ai.currentId }"
              :to="{ name: 'ai-chat', params: { conversationId: c.id } }"
            >
              <span class="conv__title">{{ c.title }}</span>
              <span class="conv__meta"
                >{{ relativeTime(c.lastMessageTime) }} · {{ c.messageCount }} 条</span
              >
            </RouterLink>
            <button
              class="conv__del"
              type="button"
              :disabled="deleting"
              :title="confirmDelete === c.id ? '再点一次确认删除' : '删除这个对话'"
              @click="remove(c.id)"
            >
              <PhTrash :size="14" aria-hidden="true" />
            </button>
          </li>
        </ul>
      </aside>

      <!-- 消息流 -->
      <section class="panel">
        <div ref="thread" class="thread">
          <p v-if="messagesLoading" class="mw-caption">正在读取对话内容</p>

          <div v-else-if="isEmptyThread" class="intro">
            <PhChatCircleDots :size="28" weight="regular" aria-hidden="true" />
            <p class="intro__title">问什么都可以，比如</p>
            <ul class="intro__list">
              <li v-for="q in EXAMPLES" :key="q">
                <button class="intro__ask" type="button" @click="send(q)">
                  {{ q }}
                  <PhArrowRight :size="14" aria-hidden="true" />
                </button>
              </li>
            </ul>
          </div>

          <template v-else>
            <article
              v-for="m in messages"
              :key="m.id"
              class="msg"
              :class="m.role === ROLE_AI ? 'msg--ai' : 'msg--me'"
            >
              <!-- 思考过程：思考模型才有，且只在实时这一轮（后端不落库、回放时没有） -->
              <div v-if="m.role === ROLE_AI && m.thinking" class="think">
                <button class="think__head" type="button" @click="toggleThink(m)">
                  <PhBrain :size="14" aria-hidden="true" />
                  <span>{{ m.thinkingDone ? '模型的思考过程' : `模型正在思考 ${elapsed}s` }}</span>
                  <span class="think__fold">{{ thinkExpanded(m) ? '收起' : '展开' }}</span>
                </button>
                <p v-show="thinkExpanded(m)" class="think__body">{{ m.thinking }}</p>
              </div>

              <div class="bubble">
                <template v-if="m.role === ROLE_AI">
                  <p v-if="m.pending && !m.content" class="typing">
                    {{
                      m.thinking ? '思考完了，正在组织回答' : `正在检索知识库并生成回答 ${elapsed}s`
                    }}
                  </p>
                  <template v-else>
                    <p
                      v-for="(b, i) in parseAnswer(m.content)"
                      :key="i"
                      class="line"
                      :class="`line--${b.kind}`"
                    >
                      <template v-for="(p, j) in b.parts" :key="j">
                        <strong v-if="p.bold">{{ p.text }}</strong>
                        <button
                          v-else-if="p.cite"
                          class="cite"
                          :class="{ 'cite--on': isFocused(m, p.cite) }"
                          type="button"
                          :title="`对应下面的来源 ${p.cite}`"
                          @click="focusSource(m, p.cite)"
                        >
                          {{ p.cite }}
                        </button>
                        <template v-else>{{ p.text }}</template>
                      </template>
                    </p>
                    <span v-if="m.pending" class="caret" aria-hidden="true" />
                  </template>
                </template>
                <p v-else class="line line--p">{{ m.content }}</p>
              </div>

              <!-- 引用来源：标出引用次数。模型常把一整段都归到 [1]，这不是错（编号与来源同序），
                   但必须让它看起来是有信息量的，而不是"引用坏了" -->
              <ul v-if="m.role === ROLE_AI && m.sources.length" class="srcs">
                <li
                  v-for="(s, i) in m.sources"
                  :key="s.id"
                  class="src"
                  :class="{
                    'src--cited': (citationCounts(m.content)[i + 1] ?? 0) > 0,
                    'src--focus': isFocused(m, i + 1),
                  }"
                >
                  <span class="src__idx">{{ i + 1 }}</span>
                  <span class="src__title">{{ s.title }}</span>
                  <span class="src__sp">{{ speciesLabel(s.species) }}</span>
                  <span v-if="citationCounts(m.content)[i + 1]" class="src__count">
                    引用 {{ citationCounts(m.content)[i + 1] }} 次
                  </span>
                </li>
              </ul>

              <!-- 反馈：只在流式结束后、且拿到了真实 messageId（正数）时可用 -->
              <div v-if="m.role === ROLE_AI && m.id > 0 && !m.pending" class="rate">
                <button
                  class="rate__btn"
                  :class="{ 'rate__btn--on': m.feedback === 1 }"
                  type="button"
                  title="有帮助"
                  @click="rate(m.id, FEEDBACK_UP)"
                >
                  <PhThumbsUp
                    :size="14"
                    :weight="m.feedback === 1 ? 'fill' : 'regular'"
                    aria-hidden="true"
                  />
                </button>
                <button
                  class="rate__btn"
                  :class="{ 'rate__btn--bad': m.feedback === 2 }"
                  type="button"
                  title="没帮助"
                  @click="rate(m.id, FEEDBACK_DOWN)"
                >
                  <PhThumbsDown
                    :size="14"
                    :weight="m.feedback === 2 ? 'fill' : 'regular'"
                    aria-hidden="true"
                  />
                </button>
                <span v-if="m.feedback" class="rate__done">已反馈</span>
              </div>

              <p v-if="m.error" class="msg__error">{{ m.error }}</p>
              <RouterLink v-if="m.quotaError" class="msg__vip" :to="{ name: 'vip' }">
                去开通会员
              </RouterLink>
            </article>
          </template>
        </div>

        <p v-if="rateError" class="mw-field__error">{{ rateError }}</p>

        <div class="composer">
          <textarea
            v-model="draft"
            class="composer__input"
            rows="2"
            :placeholder="
              streaming ? '正在回答，可以点右侧停止' : '描述宠物的症状或你想问的问题，回车发送'
            "
            :disabled="streaming"
            @keydown="onKeydown"
          />
          <button
            v-if="streaming"
            class="mw-btn mw-btn--ghost composer__send"
            type="button"
            @click="ai.stop()"
          >
            <PhStop :size="16" weight="fill" aria-hidden="true" />
            停止
          </button>
          <button
            v-else
            class="mw-btn mw-btn--primary composer__send"
            type="button"
            :disabled="!draft.trim()"
            @click="send()"
          >
            发送
          </button>
        </div>
        <p class="mw-caption composer__note">
          回答会消耗 AI 问答次数。一次生成通常十几秒，期间可以继续输入但需要等这一轮结束。
        </p>
      </section>
    </div>
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
.head__right {
  display: flex;
  align-items: center;
  gap: var(--mw-space-3);
}
.head__right .mw-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-2);
}
.quota {
  padding: 4px 10px;
  border-radius: var(--mw-radius-tag);
  background: var(--mw-canvas);
  font-size: var(--mw-font-micro);
  color: var(--mw-ink-2);
}
.quota--vip {
  background: rgba(255, 176, 32, 0.16);
  color: var(--mw-accent-ink);
}
/* 「去开通会员」的入口：额度用完时才有，不是常驻按钮 */
.head__vip {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 var(--mw-space-3);
  border: 1px solid var(--mw-brand-ink);
  border-radius: var(--mw-radius-pill);
  font-size: var(--mw-font-micro);
  font-weight: 600;
  white-space: nowrap;
}
.head__vip:hover {
  background: rgba(10, 125, 109, 0.08);
  text-decoration: none;
}

.layout {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: var(--mw-space-4);
  align-items: start;
}
@media (max-width: 900px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }
}

/* ---- 会话列表 ---- */
.side {
  padding: var(--mw-space-4);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
  background: var(--mw-surface);
}
.side__title {
  margin-bottom: var(--mw-space-3);
  font-size: var(--mw-font-caption);
  font-weight: 600;
  color: var(--mw-ink-2);
}
.side__list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
  max-height: 60vh;
  overflow: auto;
}
.conv {
  display: flex;
  align-items: center;
  gap: var(--mw-space-1);
}
.conv__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  border-radius: var(--mw-radius-input);
  color: inherit;
  text-decoration: none;
}
.conv__main:hover {
  background: var(--mw-canvas);
}
.conv__main--on {
  background: rgba(10, 125, 109, 0.08);
}
.conv__title {
  font-size: var(--mw-font-caption);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.conv__meta {
  font-size: var(--mw-font-micro);
  color: var(--mw-ink-2);
}
.conv__del {
  flex: none;
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: var(--mw-radius-input);
  background: transparent;
  color: var(--mw-muted);
  cursor: pointer;
}
.conv__del:hover {
  background: rgba(229, 72, 77, 0.1);
  color: var(--mw-danger);
}

/* ---- 消息区 ---- */
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
}
.intro__list {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--mw-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}
.intro__ask {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-pill);
  background: transparent;
  color: var(--mw-ink);
  font: inherit;
  font-size: var(--mw-font-caption);
  cursor: pointer;
}
.intro__ask:hover {
  border-color: var(--mw-brand-ink);
  color: var(--mw-brand-ink);
}

.msg {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-2);
  max-width: 88%;
}
.msg--me {
  align-self: flex-end;
  align-items: flex-end;
}
.msg--ai {
  align-self: flex-start;
}
.bubble {
  padding: 10px 14px;
  border-radius: var(--mw-radius-card);
  font-size: var(--mw-font-body);
  line-height: 1.75;
}
.msg--me .bubble {
  background: var(--mw-brand-ink);
  color: #ffffff;
}
.msg--ai .bubble {
  background: var(--mw-canvas);
  color: var(--mw-ink);
}
.line {
  margin: 0 0 6px;
}
.line:last-child {
  margin-bottom: 0;
}
.line--li {
  position: relative;
  padding-left: 14px;
}
.line--li::before {
  content: '';
  position: absolute;
  left: 2px;
  top: 0.72em;
  width: 5px;
  height: 5px;
  border-radius: var(--mw-radius-pill);
  background: var(--mw-brand-ink);
  opacity: 0.6;
}
.typing {
  margin: 0;
  color: var(--mw-ink-2);
}
.typing::after {
  content: '…';
  animation: blink 1.2s steps(3, end) infinite;
}
@keyframes blink {
  0%,
  100% {
    opacity: 0.25;
  }
  50% {
    opacity: 1;
  }
}
.caret {
  display: inline-block;
  width: 7px;
  height: 14px;
  margin-left: 2px;
  vertical-align: -2px;
  background: var(--mw-brand-ink);
  animation: blink 1s steps(2, end) infinite;
}

.srcs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mw-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}
.src {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: var(--mw-radius-tag);
  background: var(--mw-canvas);
  font-size: var(--mw-font-micro);
  color: var(--mw-ink-2);
}
.src__idx {
  font-weight: 700;
  color: var(--mw-brand-ink);
}
.src__sp {
  color: var(--mw-muted);
}

.rate {
  display: flex;
  align-items: center;
  gap: var(--mw-space-1);
}
.rate__btn {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-input);
  background: transparent;
  color: var(--mw-ink-2);
  cursor: pointer;
}
.rate__btn--on {
  border-color: var(--mw-brand-ink);
  color: var(--mw-brand-ink);
}
.rate__btn--bad {
  border-color: var(--mw-danger);
  color: var(--mw-danger);
}
.rate__done {
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}
.msg__error {
  font-size: var(--mw-font-micro);
  color: var(--mw-danger);
}
.msg__vip {
  font-size: var(--mw-font-micro);
  font-weight: 600;
}

.composer {
  display: flex;
  gap: var(--mw-space-2);
  align-items: flex-end;
  margin-top: var(--mw-space-4);
}
.composer__input {
  flex: 1;
  min-height: 56px;
  max-height: 160px;
  padding: 10px 12px;
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-input);
  background: var(--mw-canvas);
  color: var(--mw-ink);
  font: inherit;
  font-size: var(--mw-font-body);
  resize: vertical;
}
.composer__input:focus {
  outline: 2px solid var(--mw-brand-ink);
  outline-offset: -1px;
}
.composer__send {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 44px;
  padding: 0 18px;
}
.composer__note {
  margin-top: var(--mw-space-2);
}

/* ---- 思考过程 ---- */
.think {
  max-width: 100%;
  border: 1px dashed var(--mw-line);
  border-radius: var(--mw-radius-card);
  background: var(--mw-canvas);
  overflow: hidden;
}
.think__head {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 8px 12px;
  border: none;
  background: transparent;
  color: var(--mw-ink-2);
  font: inherit;
  font-size: var(--mw-font-micro);
  text-align: left;
  cursor: pointer;
}
.think__fold {
  margin-left: auto;
  color: var(--mw-muted);
}
.think__body {
  margin: 0;
  max-height: 220px;
  overflow: auto;
  padding: 0 12px 10px;
  color: var(--mw-ink-2);
  font-size: var(--mw-font-micro);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

/* ---- 引用角标 ---- */
.cite {
  display: inline-block;
  min-width: 16px;
  height: 16px;
  margin: 0 2px;
  padding: 0 4px;
  border: none;
  border-radius: var(--mw-radius-tag);
  background: rgba(10, 125, 109, 0.12);
  color: var(--mw-brand-ink);
  font: inherit;
  font-size: 11px;
  line-height: 16px;
  vertical-align: 1px;
  cursor: pointer;
}
.cite:hover,
.cite--on {
  background: var(--mw-brand-ink);
  color: #ffffff;
}
.src--cited {
  background: rgba(10, 125, 109, 0.08);
}
.src--focus {
  outline: 2px solid var(--mw-brand-ink);
  outline-offset: 1px;
}
.src__count {
  color: var(--mw-brand-ink);
  font-weight: 600;
}
</style>
