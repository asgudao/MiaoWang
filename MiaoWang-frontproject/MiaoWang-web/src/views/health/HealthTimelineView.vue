<script setup lang="ts">
/**
 * 时间线（/pets/:pid/health/timeline）
 *
 * `GET /{pid}/timeline?types=&limit=`：六类记录按时间倒序合并成一条流。
 *
 * 三个契约点：
 * 1) ⚠️ **`types` 非法值是直接报错**（`A000001 不支持的记录类型：xxx`），
 *    与 `preventive?type` 的"静默忽略"完全相反 —— 所以这里的选项只能来自
 *    `HEALTH_RECORD_TYPES`，绝不透传用户输入（`timelineTypesParam()` 再做一次过滤）。
 * 2) `limit` 默认 50、最大 200（源码写的；实测记录数太少，无法证明上界是否真的钳住）。
 * 3) **`recordId` 会重复**（是各类表自己的 id：allergy#1 与 preventive#1 同时存在），
 *    所以列表 key 必须用 `recordType + recordId`。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { PhArrowClockwise, PhFunnel } from '@phosphor-icons/vue'
import {
  HEALTH_RECORD_TYPES,
  RECORD_TYPE_TEXT,
  TIMELINE_LIMITS,
  clampLimit,
  getTimeline,
  healthErrorMessage,
  healthTimeText,
  recordTypeText,
  severityText,
  severityTone,
  timelineKey,
  timelineTypesParam,
  type HealthTimelineItem,
} from '@/api/health'
import { ApiError } from '@/api/request'
import RecordTypeIcon from '@/components/health/RecordTypeIcon.vue'

const route = useRoute()
const router = useRouter()
const pid = computed(() => Number(route.params.pid))

/** 把 URL 里的 `types` 解析成合法类型数组（非法项直接丢掉，不往外发） */
function parseTypes(raw: unknown): string[] {
  const text = Array.isArray(raw) ? raw.join(',') : typeof raw === 'string' ? raw : ''
  if (!text) return []
  const valid = timelineTypesParam(text.split(',').map((s) => s.trim()))
  return valid ? valid.split(',') : []
}

const selected = computed(() => parseTypes(route.query.types))
/** 条数：URL → 钳制（后端还会再钳一次） */
const limit = computed(() => clampLimit(Number(route.query.limit ?? 50)))

const items = ref<HealthTimelineItem[]>([])
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    // 不选类型 = 不传 types = 全部六类（后端按 `types` 为空处理）
    const list = await getTimeline(pid.value, { types: selected.value, limit: limit.value })
    items.value = Array.isArray(list) ? list : []
  } catch (e) {
    error.value = e instanceof ApiError ? healthErrorMessage(e) : '时间线没能读到'
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(
  () => [route.query.types, route.query.limit, pid.value],
  () => void load(),
  { immediate: false },
)

/** 改动筛选统一走 URL（可分享、可前进后退），页面的 watch 负责重拉 */
function applyQuery(nextTypes: string[], nextLimit: number) {
  const query: Record<string, string> = { limit: String(nextLimit) }
  const types = timelineTypesParam(nextTypes)
  if (types) query.types = types
  void router.replace({ query })
}

function toggleType(t: string) {
  const has = selected.value.includes(t)
  const next = has ? selected.value.filter((x) => x !== t) : [...selected.value, t]
  // 按固定顺序（而不是点击顺序）拼串，URL 才稳定、可复制
  applyQuery(
    HEALTH_RECORD_TYPES.filter((x) => next.includes(x)),
    limit.value,
  )
}

function setLimit(l: number) {
  applyQuery(selected.value, l)
}

function clearTypes() {
  applyQuery([], limit.value)
}
</script>

<template>
  <div class="wrap">
    <header class="head">
      <div>
        <h2 class="mw-h2">时间线</h2>
        <p class="mw-caption">
          六类记录合成一条流，按发生时间倒序。不选类型就是全部，选多个类型会按逗号串一次发给后端。
        </p>
      </div>
    </header>

    <div class="filters">
      <p class="filter-label">
        <PhFunnel :size="14" aria-hidden="true" />
        记录类型
      </p>
      <div class="mw-chips" role="group" aria-label="按记录类型筛选">
        <button
          class="mw-chip"
          :class="{ 'mw-chip--on': selected.length === 0 }"
          type="button"
          @click="clearTypes"
        >
          全部
        </button>
        <button
          v-for="t in HEALTH_RECORD_TYPES"
          :key="t"
          class="mw-chip"
          :class="{ 'mw-chip--on': selected.includes(t) }"
          type="button"
          @click="toggleType(t)"
        >
          {{ RECORD_TYPE_TEXT[t] }}
        </button>
      </div>

      <p class="filter-label">
        <PhFunnel :size="14" aria-hidden="true" />
        最多显示
      </p>
      <div class="mw-chips" role="group" aria-label="条数上限">
        <button
          v-for="l in TIMELINE_LIMITS"
          :key="l"
          class="mw-chip"
          :class="{ 'mw-chip--on': limit === l }"
          type="button"
          @click="setLimit(l)"
        >
          <span class="mw-num">{{ l }}</span> 条
        </button>
      </div>
    </div>

    <div v-if="loading" class="mw-rows">
      <span v-for="i in 5" :key="i" class="mw-skeleton sk" />
    </div>

    <div v-else-if="error" class="mw-state mw-state--box">
      <PhArrowClockwise :size="24" aria-hidden="true" />
      <p class="mw-state__title">时间线没能读到</p>
      <p>{{ error }}</p>
      <button class="mw-btn mw-btn--ghost" type="button" @click="load">重试</button>
    </div>

    <div v-else-if="!items.length" class="mw-state mw-state--box">
      <p class="mw-state__title">这个筛选下没有记录</p>
      <p>换一个类型，或者把条数放宽一点。</p>
      <button class="mw-btn mw-btn--ghost" type="button" @click="clearTypes">看全部类型</button>
    </div>

    <template v-else>
      <p class="mw-caption count">
        已加载 <span class="mw-num">{{ items.length }}</span> 条
        <template v-if="selected.length">
          ，类型：{{ selected.map(recordTypeText).join('、') }}
        </template>
        <template v-else>，包含全部六类</template>
      </p>

      <ul class="mw-rows">
        <li v-for="item in items" :key="timelineKey(item)" class="mw-row">
          <span class="mw-row__icon"><RecordTypeIcon :type="item.recordType" :size="16" /></span>
          <div class="mw-row__main">
            <p class="mw-row__title">{{ item.title }}</p>
            <p class="mw-row__sub">
              {{ item.recordTypeDesc }}
              <template v-if="item.summary">，{{ item.summary }}</template>
            </p>
          </div>
          <span
            v-if="item.severity !== null"
            class="mw-badge"
            :class="`mw-sev--${severityTone(item.severity)}`"
          >
            {{ severityText(item.severity) }}
          </span>
          <span class="mw-caption mw-num time">{{ healthTimeText(item.happenedAt) }}</span>
        </li>
      </ul>
    </template>
  </div>
</template>

<style scoped>
.wrap {
  margin-top: var(--mw-space-4);
}
.head {
  margin-bottom: var(--mw-space-4);
}
.filters {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-2);
  margin-bottom: var(--mw-space-4);
}
.filter-label {
  display: flex;
  align-items: center;
  gap: var(--mw-space-1);
  font-size: var(--mw-font-micro);
  font-weight: 600;
  color: var(--mw-ink-2);
}
.filters .mw-chips {
  margin-bottom: var(--mw-space-2);
}
.count {
  margin-bottom: var(--mw-space-3);
}
.time {
  flex: none;
}
.sk {
  display: block;
  height: 64px;
}
</style>