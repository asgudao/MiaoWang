<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  PhBowlFood,
  PhChatCircleDots,
  PhDrop,
  PhPersonSimpleRun,
  PhPill,
  PhSyringe,
  PhTrendDown,
  PhTrendUp,
} from '@phosphor-icons/vue'
import PetStage from '@/components/PetStage.vue'
import RhythmBars from '@/components/RhythmBars.vue'
import { useAuthStore } from '@/stores/auth'
import { usePetsStore } from '@/stores/pets'
import {
  USE_MOCK,
  fetchHomeOverview,
  petBriefFromApi,
  taskKindLabel,
  taskStatusLabel,
  type HomeOverview,
  type PetBrief,
  type TaskKind,
} from '@/api/home'

const data = ref<HomeOverview | null>(null)
const loading = ref(true)
const stageError = ref('')

const greet = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 11) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const auth = useAuthStore()
const petsStore = usePetsStore()

/** 登录后从档案 store 映射来的真实宠物；未登录或还没拉到时为 null */
const apiPets = computed<PetBrief[] | null>(() =>
  auth.isLoggedIn && petsStore.loaded ? petsStore.list.map(petBriefFromApi) : null,
)

/** 真实档案（空数组 = 没有真数据可展示，页面回退演示数据） */
const realPets = computed<PetBrief[]>(() => apiPets.value ?? [])

/** 有真档案就用真的，否则回退演示数据（未登录时首页也要有东西可看） */
const homePets = computed<PetBrief[]>(() =>
  realPets.value.length ? realPets.value : (data.value?.pets ?? []),
)

const primaryPet = computed(() => homePets.value[0] ?? null)

/** 登录后问候语用「宠物怎么称呼你」，未登录就用演示数据里的称呼 */
const ownerName = computed(() =>
  auth.isLoggedIn ? auth.ownerCallName : (data.value?.ownerName ?? '铲屎官'),
)

const doneRatio = computed(() => {
  const d = data.value
  if (!d || !d.todayTotal) return 0
  return Math.round((d.todayDone / d.todayTotal) * 100)
})

/** 照护类型 → 图标（同一套图标族，字重统一 regular） */
const KIND_ICON: Record<TaskKind, unknown> = {
  feed: PhBowlFood,
  water: PhDrop,
  walk: PhPersonSimpleRun,
  med: PhPill,
  vaccine: PhSyringe,
}

onMounted(async () => {
  try {
    data.value = await fetchHomeOverview()
  } finally {
    loading.value = false
  }
  // 登录了就换成自己的真实档案（store 有缓存，来回切页不会重复请求）
  if (auth.isLoggedIn) await petsStore.fetchMine()
})
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
            <template v-if="primaryPet">
              {{ primaryPet.breed }}，{{ primaryPet.ageLabel }}，已陪伴
              <span class="mw-num">{{ data?.companionDays ?? 0 }}</span> 天
            </template>
            <template v-else>还没有宠物档案，先把它介绍给我</template>
          </p>

          <dl class="hero__stats">
            <div class="stat">
              <dt>今日照护</dt>
              <dd class="mw-hero-num">
                {{ data?.todayDone ?? 0 }}<span>/{{ data?.todayTotal ?? 0 }}</span>
              </dd>
            </div>
            <div class="stat">
              <dt>完成度</dt>
              <dd class="mw-hero-num">{{ doneRatio }}<span>%</span></dd>
            </div>
            <div class="stat">
              <dt>宠物</dt>
              <dd class="mw-hero-num">{{ homePets.length }}<span>只</span></dd>
            </div>
          </dl>

          <div class="hero__actions">
            <button class="mw-btn mw-btn--brand" type="button">记一笔</button>
            <button class="mw-btn mw-btn--ghost" type="button">和它说句话</button>
          </div>

          <p v-if="stageError" class="hero__note">{{ stageError }}</p>
          <p v-else-if="realPets.length" class="hero__note">
            宠物档案已是你的真实数据；照护提醒与周节奏仍是演示数据
          </p>
          <p v-else-if="USE_MOCK" class="hero__note">演示数据，接口就绪后自动切换</p>
        </div>
      </div>
    </section>

    <!-- 浅色内容区 -->
    <section class="mw-container body">
      <div class="cols">
        <div class="col-main">
          <header class="sec-head">
            <h2 class="mw-h2">今日照护</h2>
            <span class="mw-caption">{{ data?.todayTotal ?? 0 }} 项，按时间排序</span>
          </header>

          <div v-if="loading" class="tasks">
            <div v-for="i in 4" :key="i" class="mw-skeleton task-skeleton" />
          </div>

          <ul v-else-if="data?.tasks.length" class="tasks">
            <li v-for="t in data.tasks" :key="t.id" class="task" :class="`task--${t.status}`">
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
                  'mw-badge--done': t.status === 'done',
                  'mw-badge--time': t.status === 'todo',
                  'mw-badge--danger': t.status === 'overdue',
                }"
              >
                {{ t.dueLabel || taskStatusLabel(t.status) }}
              </span>
            </li>
          </ul>

          <div v-else class="mw-state">
            <p class="mw-state__title">今天还没有照护安排</p>
            <p>给 {{ primaryPet?.name ?? '它' }} 加上第一条提醒，之后就不用记了</p>
            <button class="mw-btn mw-btn--primary" type="button">添加提醒</button>
          </div>

          <!-- 电子宠物对话：整栏宽的气泡卡，与侧栏那种"标题+说明+按钮"完全不同的版式 -->
          <article class="chat">
            <header class="chat__head">
              <PhChatCircleDots :size="20" weight="regular" aria-hidden="true" />
              <h2 class="mw-h2">它今天说了什么</h2>
            </header>
            <div class="chat__thread">
              <p class="bubble bubble--pet">
                早上好呀，我今早的饭盆空了。你昨天说今天要早起，是真的起来了吗？
              </p>
              <p class="bubble bubble--me">真的起了，给你加了半勺罐头。</p>
              <p class="bubble bubble--pet">
                那我今天会乖一点。顺便提醒你，我的驱虫药已经晚了一天。
              </p>
            </div>
            <button class="mw-btn mw-btn--primary chat__cta" type="button">回它一句</button>
          </article>

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
                <!-- 体重是演示数据专有：pet 接口没有这个字段，真档案不编 -->
                <div v-if="p.demo && p.weight != null && p.weightDelta != null" class="pet__weight">
                  <p class="pet__weight-num">
                    <span class="mw-num">{{ p.weight.toFixed(1) }}</span> kg
                  </p>
                  <p class="pet__delta" :class="p.weightDelta >= 0 ? 'is-up' : 'is-down'">
                    <component
                      :is="p.weightDelta >= 0 ? PhTrendUp : PhTrendDown"
                      :size="13"
                      weight="regular"
                      aria-hidden="true"
                    />
                    <span class="mw-num">{{ Math.abs(p.weightDelta).toFixed(1) }}</span>
                  </p>
                </div>
                <span v-else class="pet__real">档案</span>
              </div>
              <p v-if="p.nextCare" class="pet__next" :class="`pet__next--${p.nextTone}`">
                {{ p.nextCare }}
              </p>
              <p v-else class="pet__call">
                {{ p.callName ? `它叫你「${p.callName}」` : '档案已同步，健康数据还没接' }}
              </p>
            </li>
          </ul>
        </div>

        <aside class="col-side">
          <div class="panel">
            <RhythmBars v-if="data" :days="data.rhythm" />
            <div v-else class="mw-skeleton rhythm-skeleton" />
          </div>

          <div class="panel">
            <h3 class="panel__title">知识库</h3>
            <ul class="links">
              <li>猫咪应激的 6 个信号</li>
              <li>换粮到底要不要过渡</li>
              <li>狗狗一年该打哪些疫苗</li>
            </ul>
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
  background: rgba(10, 125, 109, 0.1);
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

/* ---------- 对话卡（整栏宽 + 气泡版式） ---------- */
.chat {
  margin-top: var(--mw-space-12);
  padding: var(--mw-space-6);
  background: var(--mw-surface);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
}

.chat__head {
  display: flex;
  align-items: center;
  gap: var(--mw-space-2);
  color: var(--mw-brand-ink);
}
.chat__head h2 {
  color: var(--mw-ink);
}

.chat__thread {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-2);
  margin: var(--mw-space-4) 0 0;
}

.bubble {
  max-width: 46ch;
  padding: var(--mw-space-3) var(--mw-space-4);
  border-radius: 14px;
  font-size: var(--mw-font-caption);
  line-height: 1.7;
}
.bubble--pet {
  align-self: flex-start;
  background: var(--mw-canvas);
  border: 1px solid var(--mw-line);
  border-bottom-left-radius: 4px;
}
.bubble--me {
  align-self: flex-end;
  background: var(--mw-brand-ink);
  color: #fff;
  border-bottom-right-radius: 4px;
}

.chat__cta {
  margin-top: var(--mw-space-6);
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
  background: rgba(10, 125, 109, 0.1);
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
  background: rgba(10, 125, 109, 0.1);
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
.links li {
  padding: var(--mw-space-2) 0;
  border-bottom: 1px solid var(--mw-line);
  font-size: var(--mw-font-caption);
}
.links li:last-child {
  border-bottom: none;
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
</style>
