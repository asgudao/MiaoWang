<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { PhBug, PhChatCircleDots, PhPill, PhSyringe, PhTrendDown, PhTrendUp } from '@phosphor-icons/vue'
import PetStage from '@/components/PetStage.vue'
import RhythmBars from '@/components/RhythmBars.vue'
import { useAuthStore } from '@/stores/auth'
import {
  fetchHomeOverview,
  taskKindLabel,
  taskStatusLabel,
  type HomeOverview,
  type PetBrief,
  type TaskKind,
} from '@/api/home'
/**
 * 首页那句「它主动说的话」走 ai-pet 的 `POST /ai/pet/greet`（**不消耗聊天次数**）。
 * 判断口径复用宠物对话切片已经收口的那两个纯函数（没档案还是别的错、错误说什么人话），
 * 首页不另判一遍错误码 —— 两处口径漂移了最难查。
 */
import { greetPet } from '@/api/petChat'
import { isNoProfile, petErrorMessage } from '@/utils/petChat'

/**
 * 首页取数：**一个函数拿全部**（`/pet` + 每只宠物 4 个健康接口，最多 `1 + 4N`）。
 * 页面不自己再拉一次 `/pet`（原来 pets store 那次是重复请求）。
 */
const data = ref<HomeOverview | null>(null)
const loading = ref(true)
/** 取数失败（连 `/pet` 都没读到）→ 错误态 + 重试；**不回退演示数据** */
const errorText = ref('')
const stageError = ref('')

/**
 * 它主动说的那一句（`POST /ai/pet/greet`，**不消耗聊天次数**）。
 *
 * `text` / `name` **只来自接口字段**（`greeting` / `petName`），一个字都不自己编；
 * `null` = 这次没有这句话（`greeting` 为空不是错误）→ 整块不渲染，不摆空气泡。
 */
const petGreeting = ref<{ text: string; name: string } | null>(null)
/** 没有那句话时的**如实**说明（空串 = 页面上不出现这一行） */
const petGreetNote = ref('')
/** 「还没有宠物档案」→ 给「去添加宠物」这个真入口（本页已知没档案 与 后端回 B000906 共用它） */
const petGreetNeedPet = ref(false)
/**
 * 本页是否**真的发出过**一次 `/greet`。
 *
 * `/greet` 有时段缓存（同一时段后端只会生成一句，`cached` 字段就是这件事），
 * 所以口径是**一次进页一次**：不轮询、不在返回首页时反复调。
 * 没发出去（未登录 / 没有档案 / 取数失败）就保持 false —— 等下一次有机会再说，
 * 而不是"试过了"就永远不再开口。
 */
const greetTried = ref(false)

const greet = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 11) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const auth = useAuthStore()

/** 宠物卡：真数据模式下来自 `/pet`；演示模式下来自 mock（`data.source` 会说明） */
const homePets = computed<PetBrief[]>(() => data.value?.pets ?? [])

const primaryPet = computed(() => homePets.value[0] ?? null)

/** 登录后问候语用「宠物怎么称呼你」，未登录就用概览里的称呼 */
const ownerName = computed(() =>
  auth.isLoggedIn ? auth.ownerCallName : (data.value?.ownerName ?? '铲屎官'),
)

/**
 * 那句问候是谁说的：接口的 `petName`（没起名时后端回字面量「未命名」，原样显示）；
 * 真拿不到名字才退回中性的「它」—— **不替它编名字**。
 */
const petGreetName = computed(() => petGreeting.value?.name || '它')

/** 待办 / 逾期：**只读概览的统计**，页面不另算一套（后端没有打卡表，Done 不存在） */
const todoCount = computed(() => data.value?.todoCount ?? 0)
const overdueCount = computed(() => data.value?.overdueCount ?? 0)

/** 部分失败：某只宠物的健康记录一条都没读到 → 如实说明，别让人误以为"它没有照护项" */
const degradedText = computed(() => {
  const names = data.value?.degradedPets ?? []
  return names.length ? `${names.join('、')}的健康记录这次没读到，它的照护项没有算进来` : ''
})

/**
 * 提醒总开关关着时的**补数**（防"谎报什么都没有"）。
 *
 * 关掉总开关后 `tasks` 被裁空 → 空态文案本来会说"还没有可提醒的照护项"，
 * 那是**假的**：明明有几条被你自己关掉了。所以必须把 `suppressedCount` 说出来，
 * 并给一个能改回来的真入口（`/remind/setting`）。手机端踩过这个真 bug。
 */
const remindOffNote = computed(() => {
  const d = data.value
  if (!d || !d.remindOff) return ''
  return d.suppressedCount > 0
    ? `提醒总开关已关闭：有 ${d.suppressedCount} 项本来在提醒范围内，现在不列待办、也不发提醒`
    : '提醒总开关已关闭：这里不列待办，也不会发提醒'
})

/** 「提前 N 天」生效时，标题右边要说清当前窗口口径（默认 0 天时不显示，免得噪音） */
const aheadNote = computed(() => {
  const d = data.value
  if (!d || d.remindOff || d.remindAheadDays <= 0) return ''
  return `提前 ${d.remindAheadDays} 天提醒（待办含窗口内还没到期的项）`
})

/** 「记一笔」的真实去处（有宠物进它的健康记录，没宠物就去加档案） */
const recordTo = computed(() =>
  primaryPet.value ? `/pets/${primaryPet.value.pid}/health` : '/pets/new',
)
/**
 * 「去和它说话」的真实去处：宠物对话页（ai-pet）已上线，路由在 `router/index.ts` 的 `pet-chat` / `pet-chat-pet`。
 * 有档案就带上 pid（`chat` / `greet` 的 pid 缺省口径是"第一只"，显式带才没有歧义）；
 * 没档案进不带 pid 的那条，页面自己会说清"先添加一只"并给建宠物的入口 —— **不做假入口**。
 */
const chatTo = computed(() => (primaryPet.value ? `/pets/${primaryPet.value.pid}/chat` : '/pets/chat'))
/** 空态引导的真实去处：直接开一条疫苗/驱虫（「下次到期」就是提醒的来源） */
const firstRecordTo = computed(() =>
  primaryPet.value ? `/pets/${primaryPet.value.pid}/health/new/preventive` : '/pets/new',
)

/** 照护类型 → 图标（同一套图标族，字重统一 regular）；**只保留真有数据源的三类** */
const KIND_ICON: Record<TaskKind, unknown> = {
  vaccine: PhSyringe,
  deworm: PhBug,
  med: PhPill,
}

async function reload() {
  loading.value = true
  errorText.value = ''
  try {
    data.value = await fetchHomeOverview()
  } catch (e) {
    data.value = null
    errorText.value = e instanceof Error && e.message ? e.message : '照护提醒没读出来，稍后再试'
  } finally {
    loading.value = false
  }
  // 进首页让宠物主动说一句（**不消耗聊天次数**）。
  // 连 `/pet` 都没读到时不问它：有没有档案都不知道，问了也只会拿到一句与事实无关的报错。
  if (!errorText.value) void loadPetGreeting()
}

/**
 * 宠物主动开口。三条不许破的口径（`AiPetChatService.greet` + `/greet` 契约）：
 *
 * 1. **一次进页只调一次**：真的发出请求就置位 `greetTried`，不轮询、不重复调
 *    （同一时段后端本来也只生成一句）。
 * 2. **没有宠物档案时不发这个请求**：首页已经从 `/pet` 知道有没有档案，
 *    直接给「去添加宠物」的真去处，而不是先打一次注定 `B000906` 的请求
 *    （后端 `PetProfileLoader.load` 里"没档案"短路在归属校验**之前**，带谁的 pid 都一样）。
 * 3. **`greeting` 为空不是错误**（模型抖动、或该时段没生成出来，后端就回 null）→
 *    不显示气泡、不当错误页；请求失败照实说，绝不写成"它这会儿不想说话"这种假状态。
 */
async function loadPetGreeting() {
  if (greetTried.value) return
  // 演示模式里的宠物是演示数据（pid 是 p1/p2），没有真档案可问候
  if (data.value?.source === 'mock') return
  if (!auth.isLoggedIn) {
    petGreetNote.value = '登录之后它才会跟你打招呼'
    return
  }
  // `/pet` 的 pid 在本页是字符串（`petBriefFromApi` 统一 String()），转回数字再发，
  // 免得后端 Long 字段吃到一个字符串形态的 pid
  const pid = Number(primaryPet.value?.pid ?? '')
  if (!pid) {
    petGreetNeedPet.value = true
    petGreetNote.value = '还没有宠物档案，添加一只之后它才会开口'
    return
  }
  greetTried.value = true
  try {
    const resp = await greetPet(pid)
    const text = resp && resp.greeting ? String(resp.greeting) : ''
    const name = resp && resp.petName ? String(resp.petName) : ''
    petGreeting.value = text ? { text, name } : null
    petGreetNeedPet.value = false
    petGreetNote.value = text ? '' : '它这次没主动开口（问候没生成出来，不影响你主动找它聊）'
  } catch (e) {
    petGreeting.value = null
    if (isNoProfile(e)) {
      // 竞态兜底：取数时还有档案、问候时刚被删（或改了归属）→ 仍然按"没档案"给真去处
      petGreetNeedPet.value = true
      petGreetNote.value = petErrorMessage(e)
      return
    }
    petGreetNote.value = `问候没读到：${petErrorMessage(e)}`
  }
}

onMounted(reload)
</script>

<template>
  <div class="home">
    <!-- 深色舞台带：3D 电子宠物是主角，界面退到四周 -->
    <section class="hero mw-night">
      <div class="mw-container hero__inner">
        <div class="hero__stage">
          <PetStage @failed="stageError = $event" />
          <p class="hero__hint">拖动可以左右看看它</p>
        </div>

        <div class="hero__copy">
          <p class="hero__greet">{{ greet }}，{{ ownerName }}</p>
          <h1 class="hero__name">{{ primaryPet?.name ?? '你的它' }}</h1>
          <p class="hero__sub">
            <template v-if="primaryPet">{{ primaryPet.breed }}，{{ primaryPet.ageLabel }}</template>
            <template v-else>还没有宠物档案，先把它介绍给我</template>
          </p>

          <!-- 三个数字全部有据可依：待办/逾期来自健康记录的「下次到期」，宠物只数来自 /pet。
               「已陪伴 N 天」与「完成度 %」没有数据源（pet 接口没有领养日期、后端没有打卡表）→ 已下线 -->
          <dl class="hero__stats">
            <div class="stat">
              <dt>待办</dt>
              <dd class="mw-hero-num">{{ todoCount }}<span>项</span></dd>
            </div>
            <div class="stat">
              <dt>已逾期</dt>
              <dd class="mw-hero-num">{{ overdueCount }}<span>项</span></dd>
            </div>
            <div class="stat">
              <dt>宠物</dt>
              <dd class="mw-hero-num">{{ homePets.length }}<span>只</span></dd>
            </div>
          </dl>

          <!-- 三个按钮都有真实去处：健康记录 / 宠物对话（ai-pet 页已上线）/ AI 问诊 -->
          <div class="hero__actions">
            <RouterLink class="mw-btn mw-btn--brand" :to="recordTo">记一笔</RouterLink>
            <RouterLink class="mw-btn mw-btn--ghost" :to="chatTo">去和它说话</RouterLink>
            <RouterLink class="mw-btn mw-btn--ghost" to="/ai">问问 AI</RouterLink>
          </div>

          <p v-if="stageError" class="hero__note">{{ stageError }}</p>
          <p v-else-if="data?.source === 'mock'" class="hero__note">
            演示数据（没有登录，或显式打开了演示开关），不是你的真实记录
          </p>
          <p v-else-if="data" class="hero__note">
            照护提醒来自你的健康记录：疫苗与驱虫按「下次到期」算，用药只取「用药中」
          </p>
        </div>
      </div>
    </section>

    <!-- 浅色内容区 -->
    <section class="mw-container body">
      <div class="cols">
        <div class="col-main">
          <header class="sec-head">
            <h2 class="mw-h2">照护提醒</h2>
            <span class="mw-caption">待办 {{ todoCount }} 项，按紧急程度排序</span>
          </header>

          <!-- 「提前 N 天」生效时把当前窗口口径写出来（默认 0 天不显示，免得噪音） -->
          <p v-if="aheadNote && !loading && !errorText" class="mw-alert mw-alert--info">
            {{ aheadNote }}
          </p>

          <!-- 🔴 防"谎报什么都没有"：总开关关掉后 tasks 被裁空，空态不能就说成"没有照护项"。
               这里把被关掉的条数如实说出来，并给一个能改回来的真入口。 -->
          <p v-if="remindOffNote && !loading && !errorText" class="mw-alert mw-alert--info">
            {{ remindOffNote }}
            <RouterLink class="mw-link" to="/remind/setting">去提醒设置打开</RouterLink>
          </p>

          <!-- 部分失败：真有宠物没读到健康记录时如实说，不静默当成"没有照护项" -->
          <p v-if="degradedText && !loading && !errorText" class="mw-alert mw-alert--info">
            {{ degradedText }}
          </p>

          <div v-if="loading" class="tasks">
            <div v-for="i in 4" :key="i" class="mw-skeleton task-skeleton" />
          </div>

          <!-- 错误态：连宠物档案都没读到 → 明确的错误 + 真能用的重试 -->
          <div v-else-if="errorText" class="mw-state">
            <p class="mw-state__title">照护提醒没读出来</p>
            <p>{{ errorText }}</p>
            <button class="mw-btn mw-btn--primary" type="button" @click="reload">重试</button>
          </div>

          <ul v-else-if="data?.tasks.length" class="tasks">
            <li
              v-for="t in data.tasks"
              :key="t.id"
              class="task"
              :class="{ 'task--overdue': t.status === 'overdue' }"
            >
              <span class="task__icon" aria-hidden="true">
                <component :is="KIND_ICON[t.kind]" :size="18" weight="regular" />
              </span>
              <div class="task__main">
                <p class="task__title">{{ t.title }}</p>
                <p class="task__pet">{{ taskKindLabel(t.kind) }}，{{ t.petName }}</p>
              </div>
              <span
                class="mw-badge"
                :class="{
                  'mw-badge--danger': t.status === 'overdue',
                  'mw-badge--time': t.status === 'today',
                  'mw-badge--done': t.status === 'active',
                }"
              >
                {{ t.dueLabel || taskStatusLabel(t.status) }}
              </span>
            </li>
          </ul>

          <!-- 空态：**不编"今日已完成 N 项"**，直接说清楚提醒是从哪来的、去哪儿加；
               健康数据没读到时（degradedText）不许说成"没有照护项" -->
          <div v-else class="mw-state">
            <p class="mw-state__title">
              {{
                degradedText
                  ? '照护项这次没算出来'
                  : data?.remindOff
                    ? '提醒总开关关着，这里不列照护项'
                    : primaryPet
                      ? '还没有可提醒的照护项'
                      : '还没有宠物档案'
              }}
            </p>
            <p v-if="degradedText">这只宠物的健康记录没读到，照护项不猜；可以点上面的重试再看一次</p>
            <!-- 总开关关掉时不许说成"没有照护项"：说清是被开关关掉的，并给真入口 -->
            <p v-else-if="data?.remindOff">
              这不是"没有照护项"，是你在提醒设置里把总开关关掉了；
              打开它，疫苗与驱虫的到期项就会回到这里。
            </p>
            <p v-else-if="primaryPet">
              在健康记录里给疫苗或驱虫填上「下次到期」，这里就会自动出现提醒
            </p>
            <p v-else>先添加一只宠物，再记录疫苗与驱虫的到期时间</p>
            <RouterLink class="mw-btn mw-btn--primary" :to="firstRecordTo">
              {{ primaryPet ? '去记一条' : '添加宠物' }}
            </RouterLink>
          </div>

          <!--
            这里原来是「它今天说了什么」的对话卡：三条气泡是**模板里硬编码的**，
            `MOCK.chat` 在 Web 端根本不存在，按钮也没有 handler。
            现在换成一真一实两样东西：
              ① 宠物主动说的那一句 —— 进页面调一次 `POST /ai/pet/greet`（不消耗聊天次数），
                 文本与名字直接用接口字段 greeting / petName；
              ② 进入完整对话页的真入口。
            `greeting` 为空 / 没有宠物档案 / 请求失败三种情况**都不摆假气泡**：
            没有那句话就整块不渲染，另两种照实说并给一个能用的去处。
          -->
          <header class="sec-head sec-head--gap">
            <h2 class="mw-h2">宠物对话</h2>
            <span class="mw-caption">它用宠物档案里的事回答你</span>
          </header>

          <div v-if="petGreeting" class="greet">
            <p class="greet__who">{{ petGreetName }}</p>
            <p class="greet__text">{{ petGreeting.text }}</p>
          </div>
          <p v-else-if="petGreetNote" class="greet__note">
            {{ petGreetNote }}
            <RouterLink v-if="petGreetNeedPet" class="mw-link" to="/pets/new">去添加宠物</RouterLink>
          </p>

          <div class="petchat">
            <span class="petchat__icon" aria-hidden="true">
              <PhChatCircleDots :size="20" weight="regular" />
            </span>
            <div class="petchat__main">
              <p class="petchat__title">
                {{
                  primaryPet ? `问问 ${primaryPet.name} 它自己怎么说` : '先添加一只宠物，再跟它说话'
                }}
              </p>
              <p class="petchat__desc">
                它知道自己叫什么、你怎么叫它，也记得你们最近聊过什么。聊天会消耗次数，它主动打招呼不消耗。
              </p>
            </div>
            <RouterLink class="mw-btn mw-btn--ghost" :to="chatTo">去和它说话</RouterLink>
          </div>

          <header class="sec-head sec-head--gap">
            <h2 class="mw-h2">我的宠物</h2>
            <RouterLink class="mw-caption" to="/pets">全部档案</RouterLink>
          </header>

          <ul class="pets">
            <li v-for="p in homePets" :key="p.pid" class="pet">
              <div class="pet__top">
                <span class="pet__avatar" aria-hidden="true">{{ p.initial }}</span>
                <div class="pet__id">
                  <p class="pet__name">{{ p.name }}</p>
                  <p class="pet__meta">{{ p.breed }}，{{ p.ageLabel }}</p>
                </div>
                <!-- 体重来自健康记录（`/weight` 取最新一次）；**没有记录就整块不渲染**，不编数字 -->
                <div v-if="p.weight != null" class="pet__weight">
                  <p class="pet__weight-num">
                    <span class="mw-num">{{ p.weight.toFixed(1) }}</span> kg
                  </p>
                  <!-- 变化量只有演示数据才有：真档案缺 30 天基线，宁可不显示 -->
                  <p
                    v-if="p.weightDelta != null"
                    class="pet__delta"
                    :class="p.weightDelta >= 0 ? 'is-up' : 'is-down'"
                  >
                    <component
                      :is="p.weightDelta >= 0 ? PhTrendUp : PhTrendDown"
                      :size="13"
                      weight="regular"
                      aria-hidden="true"
                    />
                    <span class="mw-num">{{ Math.abs(p.weightDelta).toFixed(1) }}</span>
                  </p>
                  <p v-else-if="p.weightAt" class="pet__delta">{{ p.weightAt }} 测</p>
                </div>
                <span v-else class="pet__real">
                  {{ p.degraded ? '健康数据未读到' : '无体重记录' }}
                </span>
              </div>
              <p
                v-if="p.nextCare"
                class="pet__next"
                :class="p.nextTone ? `pet__next--${p.nextTone}` : ''"
              >
                {{ p.nextCare }}
              </p>
              <p v-else class="pet__call">
                {{ p.callName ? `它叫你「${p.callName}」` : '还没有照护提醒' }}
              </p>
            </li>
          </ul>
        </div>

        <aside class="col-side">
          <div class="panel">
            <RhythmBars v-if="data" :days="data.rhythm" />
            <div v-else-if="loading" class="mw-skeleton rhythm-skeleton" />
            <p v-else class="mw-caption">记录还没读出来</p>
          </div>

          <!-- 知识库：原来那三条标题是**硬编码的**（真实库里并没有「猫咪应激的 6 个信号」这类条目，
               真标题形如「换粮要 7 天过渡」）→ 换成说明 + 真实入口，不摆假标题 -->
          <div class="panel">
            <h3 class="panel__title">知识库</h3>
            <p class="panel__desc">养护、喂食、疫苗这些日常问题，按宠物种类整理好了。</p>
            <RouterLink class="panel__more" to="/knowledge">看全部文章</RouterLink>
          </div>

          <div class="panel panel--flat">
            <h3 class="panel__title">社区</h3>
            <p class="panel__desc">别人家的猫今天干了什么。</p>
            <RouterLink class="panel__more" to="/community">去逛逛</RouterLink>
          </div>
        </aside>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* ---------- 舞台带 ---------- */
.hero {
  padding-top: calc(var(--mw-nav-h) + var(--mw-space-6));
  padding-bottom: var(--mw-space-8);
}

.hero__inner {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(300px, 0.85fr);
  gap: var(--mw-space-8);
  align-items: center;
}

.hero__stage {
  position: relative;
  /* dvh 而不是 vh：移动端地址栏收起时不会跳版 */
  height: min(62dvh, 560px);
  border-radius: var(--mw-radius-stage);
  overflow: hidden;
  border: 1px solid var(--mw-night-line);
}

.hero__hint {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 12px;
  margin: 0;
  text-align: center;
  font-size: var(--mw-font-micro);
  color: var(--mw-on-night-2);
  pointer-events: none;
}

.hero__greet {
  color: var(--mw-on-night-2);
  font-size: var(--mw-font-caption);
}

.hero__name {
  margin-top: var(--mw-space-2);
  font-size: var(--mw-font-hero);
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1.05;
}

.hero__sub {
  margin-top: var(--mw-space-3);
  color: var(--mw-on-night-2);
}

.hero__stats {
  display: flex;
  gap: var(--mw-space-8);
  margin: var(--mw-space-8) 0 0;
}

.stat dt {
  font-size: var(--mw-font-micro);
  color: var(--mw-on-night-2);
}
.stat dd {
  margin: var(--mw-space-1) 0 0;
  font-size: 34px;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.1;
}
.stat dd span {
  font-size: 16px;
  font-weight: 500;
  color: var(--mw-on-night-2);
  margin-left: 2px;
}

.hero__actions {
  display: flex;
  gap: var(--mw-space-3);
  margin-top: var(--mw-space-8);
}

.hero__note {
  margin-top: var(--mw-space-4);
  font-size: var(--mw-font-micro);
  color: var(--mw-brand);
  opacity: 0.85;
}

/* ---------- 内容区 ---------- */
.body {
  padding-top: var(--mw-space-8);
  padding-bottom: var(--mw-space-12);
}

.cols {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: var(--mw-space-8);
}

.sec-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: var(--mw-space-4);
}
.sec-head--gap {
  margin-top: var(--mw-space-12);
}

.tasks {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-2);
}
.task-skeleton {
  height: 68px;
}

.task {
  display: flex;
  align-items: center;
  gap: var(--mw-space-4);
  padding: var(--mw-space-4);
  background: var(--mw-surface);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
}
.task--done {
  opacity: 0.62;
}
.task--done .task__title {
  text-decoration: line-through;
}
.task--overdue {
  border-color: rgba(229, 72, 77, 0.35);
}

.task__icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  flex: none;
  border-radius: var(--mw-radius-tag);
  background: rgba(194, 65, 12, 0.1);
  color: var(--mw-brand-ink);
}
.task__main {
  flex: 1;
  min-width: 0;
}
.task__title {
  font-weight: 600;
}
.task__pet {
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
}

/* ---------- 宠物卡 ---------- */
.pets {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--mw-space-3);
}
.pet {
  padding: var(--mw-space-4);
  background: var(--mw-surface);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
}
.pet__top {
  display: flex;
  align-items: center;
  gap: var(--mw-space-3);
}
.pet__avatar {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  flex: none;
  border-radius: var(--mw-radius-pill);
  background: rgba(194, 65, 12, 0.1);
  color: var(--mw-brand-ink);
  font-weight: 700;
}
.pet__id {
  flex: 1;
  min-width: 0;
}
.pet__name {
  font-weight: 600;
}
.pet__meta {
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
}
/* 真档案没有体重模块，右侧给个标签占位，别让卡片看着"缺了一块" */
.pet__real {
  flex: none;
  padding: 4px 10px;
  border-radius: var(--mw-radius-tag);
  background: var(--mw-canvas);
  font-size: var(--mw-font-micro);
  color: var(--mw-ink-2);
}
.pet__call {
  margin-top: var(--mw-space-3);
  font-size: var(--mw-font-micro);
  color: var(--mw-ink-2);
}
.pet__weight {
  text-align: right;
}
.pet__weight-num {
  font-weight: 700;
  font-size: 18px;
  letter-spacing: -0.02em;
}
.pet__weight-num span {
  font-size: 22px;
}
.pet__delta {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 2px;
  font-size: var(--mw-font-micro);
  color: var(--mw-ink-2);
}
.pet__next {
  margin-top: var(--mw-space-3);
  padding: 6px 10px;
  border-radius: var(--mw-radius-tag);
  font-size: var(--mw-font-micro);
  font-weight: 600;
}
.pet__next--ok {
  background: rgba(74, 118, 56, 0.1);
  color: var(--mw-success);
}
.pet__next--warn {
  background: rgba(255, 176, 32, 0.16);
  color: var(--mw-accent-ink);
}
.pet__next--danger {
  background: rgba(229, 72, 77, 0.12);
  color: var(--mw-danger);
}

/* ---------- 它主动说的那一句（真接口字段；没有就整块不渲染） ---------- */
.greet {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-1);
  margin-bottom: var(--mw-space-3);
  padding: var(--mw-space-4);
  background: var(--mw-canvas);
  border: 1px solid var(--mw-line);
  /* 左下角收窄一点：读起来是"它说的"，而不是又一个信息盒 */
  border-radius: var(--mw-radius-card) var(--mw-radius-card) var(--mw-radius-card)
    var(--mw-radius-input);
}
.greet__who {
  font-size: var(--mw-font-micro);
  font-weight: 600;
  color: var(--mw-brand-ink);
}
.greet__text {
  font-size: 18px;
  line-height: var(--mw-line-height);
  color: var(--mw-ink);
}
/* 没档案 / 没读到时的那句话：中性（不冒充错误，也不假装它在说话） */
.greet__note {
  margin-bottom: var(--mw-space-3);
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
}

/* ---------- 宠物对话入口（真入口：点进去才读历史，这里不摆假气泡） ---------- */
.petchat {
  display: flex;
  align-items: center;
  gap: var(--mw-space-4);
  padding: var(--mw-space-4);
  background: var(--mw-surface);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
}
.petchat__icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  flex: none;
  border-radius: var(--mw-radius-tag);
  background: rgba(194, 65, 12, 0.1);
  color: var(--mw-brand-ink);
}
.petchat__main {
  flex: 1;
  min-width: 0;
}
.petchat__title {
  font-weight: 600;
}
.petchat__desc {
  margin-top: var(--mw-space-1);
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
}

/* ---------- 侧栏 ---------- */
.col-side {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-4);
}
.panel {
  padding: var(--mw-space-6);
  background: var(--mw-surface);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
}
/* 社区这块故意不上卡片底：避免侧栏变成一摞一样的盒子 */
.panel--flat {
  padding: var(--mw-space-4) var(--mw-space-2);
  background: transparent;
  border: none;
  border-top: 1px solid var(--mw-line);
  border-radius: 0;
}
.rhythm-skeleton {
  height: 120px;
}
.panel__title {
  font-size: var(--mw-font-h2);
  font-weight: 600;
  margin-bottom: var(--mw-space-2);
}
.panel__desc {
  color: var(--mw-ink-2);
  font-size: var(--mw-font-caption);
}
.panel__more {
  display: inline-block;
  margin-top: var(--mw-space-3);
  font-size: var(--mw-font-caption);
  font-weight: 600;
  color: var(--mw-brand-ink);
}

/* 窄屏：舞台在上，内容单列（与手机端同构） */
@media (max-width: 960px) {
  .hero__inner,
  .cols {
    grid-template-columns: minmax(0, 1fr);
  }
  .hero__stage {
    height: 46dvh;
    min-height: 300px;
  }
  .hero__name {
    font-size: 40px;
  }
  .hero__stats {
    gap: var(--mw-space-6);
  }
}

/* 更窄：宠物对话入口的按钮换到下一行，别把说明挤成一列两个字 */
@media (max-width: 720px) {
  .petchat {
    flex-wrap: wrap;
  }
}
</style>
