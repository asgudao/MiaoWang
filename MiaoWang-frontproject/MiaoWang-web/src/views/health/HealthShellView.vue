<script setup lang="ts">
/**
 * 健康记录壳页（Web /pets/:pid/health）
 *
 * 只做四件事：拉「概览 + 疫苗全量」、给深色头部、渲染子页签、把子页面挂到 `<RouterView>`。
 * 六类记录的列表与增删改在各自子页面里（每类的列与筛选都不一样，不硬凑成一个通用表格）。
 *
 * 两个必须记住的点：
 * 1) 概览与疫苗全量**一起拉**：`overview.nextPreventive` 排除了逾期项（后端 `.ge(nextDueAt, now)`），
 *    所以逾期提醒只能用 `/{pid}/preventive` 全量自己算（见 store 的 `overdueList`）。
 * 2) 付费墙（导出 / AI 解读）的提示统一渲染在页签下方：它是**正常状态**，不是错误，不要做成红框。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import {
  PhArrowClockwise,
  PhArrowLeft,
  PhCrown,
  PhHeartbeat,
  PhSignIn,
  PhWarningCircle,
} from '@phosphor-icons/vue'
import { type HealthRecordType } from '@/api/health'
import { useHealthStore } from '@/stores/health'
import { usePetsStore } from '@/stores/pets'
import { ageLabel, breedLabel, initials, petDisplayName } from '@/utils/pet'

const route = useRoute()
const pets = usePetsStore()
const health = useHealthStore()
const { loading, error, unauthorized, loaded, gate, notice } = storeToRefs(health)

const pid = computed(() => Number(route.params.pid))
const pet = computed(() => pets.byId(pid.value))
const petLabel = computed(() => (pet.value ? petDisplayName(pet.value) : health.petName || '这只宠物'))
const needLogin = computed(() => unauthorized.value)

/** 页签：顺序 = 视觉权重（概览与时间线最常看，放最前） */
const TABS: { name: string; label: string; type: HealthRecordType | null }[] = [
  { name: 'health-overview', label: '概览', type: null },
  { name: 'health-timeline', label: '时间线', type: null },
  { name: 'health-weight', label: '体重曲线', type: 'weight' },
  { name: 'health-preventive', label: '疫苗驱虫', type: 'preventive' },
  { name: 'health-medical', label: '就诊', type: 'medical' },
  { name: 'health-medication', label: '用药', type: 'medication' },
  { name: 'health-allergy', label: '过敏史', type: 'allergy' },
  { name: 'health-symptom', label: '症状日记', type: 'symptom' },
]

/** 六个 key 后端永远都给，但仍然是"没拉到概览"时不显示计数，避免闪一串 0 */
const countsReady = ref(false)

onMounted(async () => {
  if (!pets.loaded) await pets.fetchMine()
  await load()
  countsReady.value = true
})

// 同一路由内换宠物（列表页互相跳）也要重拉
watch(pid, () => {
  void load()
})

async function load() {
  countsReady.value = false
  await health.load(pid.value)
  countsReady.value = true
}

/** 页签上的计数：概览还没到就留空，不要先显示 0（那一瞬间的数字是假的） */
function tabCount(type: HealthRecordType | null): string {
  if (!type || !countsReady.value || !loaded.value) return ''
  return String(health.countOf(type))
}

const overdueCount = computed(() => health.overdueList.length)
const totalText = computed(() => (loaded.value ? health.totalText : ''))
</script>

<template>
  <div class="mw-container mw-page">
    <RouterLink class="back" :to="{ name: 'pet-detail', params: { pid } }">
      <PhArrowLeft :size="16" aria-hidden="true" />
      宠物档案
    </RouterLink>

    <section class="mw-night hero">
      <span class="hero__avatar" aria-hidden="true">
        {{ pet ? initials(pet) : '健' }}
      </span>
      <div class="hero__main">
        <h1 class="hero__name">{{ petLabel }}的健康记录</h1>
        <p class="hero__meta">
          <template v-if="pet">{{ breedLabel(pet) }} {{ ageLabel(pet.birth) }}</template>
          <template v-else>档案读取中</template>
        </p>
        <p v-if="totalText" class="hero__stat">
          <span class="mw-num hero__num">{{ totalText }}</span>
          <span class="hero__stat-label">已记记录</span>
        </p>
      </div>
      <RouterLink
        v-if="overdueCount"
        class="hero__alert"
        :to="{ name: 'health-preventive', params: { pid } }"
      >
        <PhWarningCircle :size="16" aria-hidden="true" />
        {{ overdueCount }} 项已逾期
      </RouterLink>
    </section>

    <!-- 健康接口经网关统一要登录（连症状选项都要），未登录直接给下一步，不发注定失败的请求 -->
    <section v-if="needLogin" class="mw-state gate">
      <PhSignIn :size="26" aria-hidden="true" />
      <p class="mw-state__title">登录后才能看健康记录</p>
      <p class="gate__desc">
        健康档案经网关要求登录，匿名请求会被网关拦下来。登录之后这里会显示真实的疫苗、就诊、用药、过敏、症状与体重记录。
      </p>
      <RouterLink
        class="mw-btn mw-btn--primary"
        :to="{ name: 'login', query: { redirect: route.fullPath } }"
      >
        去登录
      </RouterLink>
    </section>

    <template v-else>
      <div v-if="loading" class="skeletons">
        <span class="mw-skeleton sk sk--hero" />
        <span class="mw-skeleton sk sk--row" />
        <span class="mw-skeleton sk sk--row" />
      </div>

      <div v-else-if="error" class="mw-state">
        <PhArrowClockwise :size="26" aria-hidden="true" />
        <p class="mw-state__title">健康记录没能读到</p>
        <p>{{ error }}</p>
        <button class="mw-btn mw-btn--ghost" type="button" @click="load">重试</button>
      </div>

      <template v-else>
        <nav class="tabs" aria-label="健康记录分类">
          <RouterLink
            v-for="t in TABS"
            :key="t.name"
            class="tab"
            :to="{ name: t.name, params: { pid } }"
          >
            <span>{{ t.label }}</span>
            <span v-if="tabCount(t.type)" class="tab__count mw-num">{{ tabCount(t.type) }}</span>
            <span
              v-if="t.type === 'preventive' && overdueCount"
              class="tab__dot"
              aria-label="有逾期项"
            />
          </RouterLink>
        </nav>

        <!-- 付费墙 / 前置条件：正常状态，给升级引导，不做成错误 -->
        <div v-if="gate" class="gatebar">
          <PhCrown :size="18" aria-hidden="true" />
          <div class="gatebar__main">
            <p class="gatebar__title">{{ gate.title }}</p>
            <p class="gatebar__desc">{{ gate.desc }}</p>
          </div>
          <RouterLink
            v-if="gate.kind !== 'need-symptom'"
            class="mw-btn mw-btn--primary"
            :to="{ name: 'vip' }"
          >
            看会员档位
          </RouterLink>
          <RouterLink
            v-else
            class="mw-btn mw-btn--ghost"
            :to="{ name: 'health-symptom', params: { pid } }"
          >
            去记症状
          </RouterLink>
          <button class="mw-link" type="button" @click="health.dismissGate()">知道了</button>
        </div>

        <p v-if="notice" class="mw-alert mw-alert--info feedback">
          <span>{{ notice }}</span>
          <button class="mw-link" type="button" @click="health.dismissNotice()">关闭</button>
        </p>

        <RouterView />
      </template>
    </template>

    <p class="foot mw-caption">
      <PhHeartbeat :size="14" aria-hidden="true" />
      健康记录仅供参考，任何用药与治疗请听兽医的。删除记录在后端是标记删除，历史数据不会立刻从库里消失。
    </p>
  </div>
</template>

<style scoped>
.back {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-2);
  margin-bottom: var(--mw-space-4);
  color: var(--mw-ink-2);
  font-size: var(--mw-font-caption);
  text-decoration: none;
}
.back:hover {
  color: var(--mw-ink);
}

.hero {
  display: flex;
  align-items: center;
  gap: var(--mw-space-4);
  padding: var(--mw-space-6);
  border-radius: var(--mw-radius-card);
}
.hero__avatar {
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  flex: none;
  border-radius: var(--mw-radius-pill);
  background: rgba(255, 138, 91, 0.16);
  color: var(--mw-brand);
  font-size: 26px;
  font-weight: 700;
}
.hero__main {
  flex: 1;
  min-width: 0;
}
.hero__name {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.02em;
}
.hero__meta {
  margin-top: 2px;
  font-size: var(--mw-font-caption);
  color: var(--mw-on-night-2);
}
.hero__stat {
  display: flex;
  align-items: baseline;
  gap: var(--mw-space-2);
  margin-top: var(--mw-space-2);
}
.hero__num {
  font-size: 30px;
  font-weight: 700;
  letter-spacing: -0.02em;
}
.hero__stat-label {
  font-size: var(--mw-font-micro);
  color: var(--mw-on-night-2);
}
.hero__alert {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-2);
  flex: none;
  padding: 6px var(--mw-space-3);
  border: 1px solid rgba(229, 72, 77, 0.4);
  border-radius: var(--mw-radius-pill);
  background: rgba(229, 72, 77, 0.16);
  color: var(--mw-danger);
  font-size: var(--mw-font-caption);
  font-weight: 600;
  text-decoration: none;
}

.gate {
  margin-top: var(--mw-space-4);
}
.gate__desc {
  max-width: 560px;
}

.skeletons {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-3);
  margin-top: var(--mw-space-4);
}
.sk {
  display: block;
}
.sk--hero {
  height: 92px;
}
.sk--row {
  height: 56px;
}

.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mw-space-2);
  margin-top: var(--mw-space-4);
  padding-bottom: var(--mw-space-3);
  border-bottom: 1px solid var(--mw-line);
}
.tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-2);
  min-height: 36px;
  padding: 0 var(--mw-space-4);
  border: 1px solid transparent;
  border-radius: var(--mw-radius-pill);
  color: var(--mw-ink-2);
  font-size: var(--mw-font-caption);
  font-weight: 600;
  text-decoration: none;
}
.tab:hover {
  background: rgba(29, 21, 18, 0.04);
  color: var(--mw-ink);
  text-decoration: none;
}
.tab.router-link-active {
  background: var(--mw-surface);
  border-color: var(--mw-line);
  color: var(--mw-ink);
  box-shadow: var(--mw-shadow-float);
}
.tab__count {
  padding: 0 6px;
  border-radius: var(--mw-radius-tag);
  background: var(--mw-canvas);
  color: var(--mw-ink-2);
  font-size: var(--mw-font-micro);
}
.tab.router-link-active .tab__count {
  background: rgba(194, 65, 12, 0.12);
  color: var(--mw-brand-ink);
}
.tab__dot {
  width: 6px;
  height: 6px;
  border-radius: var(--mw-radius-pill);
  background: var(--mw-danger);
}

.gatebar {
  display: flex;
  align-items: center;
  gap: var(--mw-space-3);
  margin-top: var(--mw-space-4);
  padding: var(--mw-space-4);
  border: 1px solid rgba(194, 65, 12, 0.2);
  border-radius: var(--mw-radius-input);
  background: rgba(194, 65, 12, 0.06);
}
.gatebar__main {
  flex: 1;
  min-width: 0;
}
.gatebar__title {
  font-weight: 600;
  color: var(--mw-brand-ink);
}
.gatebar__desc {
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
}
.gatebar .mw-btn {
  flex: none;
  padding: 0 var(--mw-space-4);
  font-size: var(--mw-font-caption);
}

.feedback {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--mw-space-3);
  margin-top: var(--mw-space-4);
}

.foot {
  display: flex;
  align-items: center;
  gap: var(--mw-space-2);
  margin-top: var(--mw-space-8);
  color: var(--mw-muted);
}
</style>