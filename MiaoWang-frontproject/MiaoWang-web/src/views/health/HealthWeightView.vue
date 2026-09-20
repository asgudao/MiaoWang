<script setup lang="ts">
/**
 * 体重曲线（/pets/:pid/health/weight?months=）
 *
 * 契约要点：
 * - `GET /{pid}/weight/curve?months=` 的 `date` 是 **`yyyy-MM-dd`**（不是接口里其它地方的
 *   `yyyy-MM-dd HH:mm:ss`），所以画图与文案都按"天"处理，别拿 `parseHealthTime` 硬套
 *   （它按完整时间解析，遇到 `2026-09-20` 这种只有日期串也能解析，但语义上是两码事）。
 * - 只传 `months`（1/3/6/12/24）；**实测没有月份越界校验**，越界会回全量，所以前端自己钳住。
 * - 曲线只有"有记录的月份"才有数据点，**空数组是正常的**（新宠物一定遇到），要画空态而不是报错。
 *
 * 图形是手写 SVG（`WeightChart.vue`，不引第三方图表库），四种边界都在那里处理：
 * 0 个点 / 1 个点 / 体重完全相同 / 极值。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { PhArrowClockwise, PhPencilSimple, PhPlus } from '@phosphor-icons/vue'
import {
  WEIGHT_CURVE_MONTHS,
  clampMonths,
  deleteWeight,
  getWeightCurve,
  healthErrorMessage,
  healthTimeText,
  listWeight,
  weightText,
  type WeightCurvePoint,
} from '@/api/health'
import { ApiError } from '@/api/request'
import { useRecordList } from '@/composables/useRecordList'
import HealthDeleteButton from '@/components/health/HealthDeleteButton.vue'
import RecordListStates from '@/components/health/RecordListStates.vue'
import WeightChart from '@/components/health/WeightChart.vue'

const route = useRoute()
const router = useRouter()
const pid = computed(() => Number(route.params.pid))

/** 月数进 URL（可分享、可后退），非法值钳到最近的合法档 */
const months = computed(() => clampMonths(Number(route.query.months ?? 6)))

const points = ref<WeightCurvePoint[]>([])
const curveLoading = ref(true)
const curveError = ref('')

async function loadCurve() {
  curveLoading.value = true
  curveError.value = ''
  try {
    const list = await getWeightCurve(pid.value, months.value)
    points.value = Array.isArray(list) ? list : []
  } catch (e) {
    curveError.value = e instanceof ApiError ? healthErrorMessage(e) : '体重曲线没能读到'
  } finally {
    curveLoading.value = false
  }
}

const {
  items: records,
  loading: listLoading,
  error: listError,
  busyId: listBusyId,
  load: loadRecords,
  remove: removeRecord,
} = useRecordList(listWeight)

onMounted(() => {
  void loadCurve()
  void loadRecords(pid.value)
})
watch(pid, () => {
  void loadCurve()
  void loadRecords(pid.value)
})
// 只换月数时列表不用重拉（列表是全量，不按月过滤）
watch(months, () => {
  void loadCurve()
})

function setMonths(m: number) {
  void router.replace({ query: { months: String(m) } })
}

/** 曲线区间统计：点数、最低、最高、区间变化 */
const stats = computed(() => {
  const values = points.value.map((p) => p.weightKg).filter((v) => typeof v === 'number')
  if (!values.length) return null
  const first = values[0] ?? 0
  const last = values[values.length - 1] ?? first
  return {
    count: values.length,
    min: Math.min(...values),
    max: Math.max(...values),
    delta: Number((last - first).toFixed(2)),
  }
})

function newRecord() {
  return { name: 'health-record-new', params: { pid: pid.value, kind: 'weight' } }
}

function editRecord(id: number) {
  return { name: 'health-record-edit', params: { pid: pid.value, kind: 'weight', id } }
}
</script>

<template>
  <div class="wrap">
    <header class="head">
      <div>
        <h2 class="mw-h2">体重曲线</h2>
        <p class="mw-caption">
          曲线按测量日期连线，同一天测多次会画成同一个点上的多个点。体重本身就是最灵敏的一项，掉秤通常比别的症状出现得早。
        </p>
      </div>
      <RouterLink class="mw-btn mw-btn--primary" :to="newRecord()">
        <PhPlus :size="16" aria-hidden="true" />
        记体重
      </RouterLink>
    </header>

    <div class="mw-chips" role="group" aria-label="曲线时间范围">
      <button
        v-for="m in WEIGHT_CURVE_MONTHS"
        :key="m"
        class="mw-chip"
        :class="{ 'mw-chip--on': months === m }"
        type="button"
        @click="setMonths(m)"
      >
        近 <span class="mw-num">{{ m }}</span> 个月
      </button>
    </div>

    <section class="panel">
      <div v-if="curveLoading" class="mw-rows">
        <span class="mw-skeleton sk sk--chart" />
      </div>

      <div v-else-if="curveError" class="mw-state mw-state--box">
        <PhArrowClockwise :size="24" aria-hidden="true" />
        <p class="mw-state__title">曲线没能读到</p>
        <p>{{ curveError }}</p>
        <button class="mw-btn mw-btn--ghost" type="button" @click="loadCurve">重试</button>
      </div>

      <WeightChart v-else :points="points" />

      <ul v-if="stats" class="stats">
        <li>
          <span class="stats__label">测量点</span>
          <span class="stats__num mw-num">{{ stats.count }}</span>
        </li>
        <li>
          <span class="stats__label">区间最低</span>
          <span class="stats__num mw-num">{{ weightText(stats.min) }}</span>
        </li>
        <li>
          <span class="stats__label">区间最高</span>
          <span class="stats__num mw-num">{{ weightText(stats.max) }}</span>
        </li>
        <li>
          <span class="stats__label">首尾变化</span>
          <span
            class="stats__num mw-num"
            :class="stats.delta < 0 ? 'stats--down' : stats.delta > 0 ? 'stats--up' : ''"
          >
            {{ stats.delta > 0 ? '+' : '' }}{{ weightText(stats.delta) }}
          </span>
        </li>
      </ul>
    </section>

    <section class="list">
      <div class="list__head">
        <h3 class="mw-h3">全部体重记录</h3>
        <p class="mw-caption">按测量时间倒序，列表不看上面的月份筛选。</p>
      </div>

      <RecordListStates
        :loading="listLoading"
        :error="listError"
        :empty="!records.length"
        empty-title="还没有体重记录"
        empty-desc="建议固定时间与条件（比如晨起空腹、同一台秤）来记，曲线才有可比性。"
        @retry="loadRecords(pid)"
      >
        <template #empty-actions>
          <RouterLink class="mw-btn mw-btn--primary" :to="newRecord()">记第一条体重</RouterLink>
        </template>

        <ul class="mw-rows">
          <li v-for="r in records" :key="r.id" class="mw-row">
            <div class="mw-row__main">
              <p class="mw-row__title">
                <span class="mw-num">{{ weightText(r.weightKg) }}</span>
              </p>
              <p class="mw-row__sub mw-num">{{ healthTimeText(r.measuredAt) }}</p>
              <p v-if="r.note" class="mw-caption">{{ r.note }}</p>
            </div>
            <div class="mw-row__actions">
              <RouterLink class="mw-btn mw-btn--ghost act" :to="editRecord(r.id)">
                <PhPencilSimple :size="16" aria-hidden="true" />
                编辑
              </RouterLink>
              <HealthDeleteButton
                :busy="listBusyId === r.id"
                @confirm="removeRecord(r.id, deleteWeight)"
              />
            </div>
          </li>
        </ul>
      </RecordListStates>
    </section>
  </div>
</template>

<style scoped>
.wrap {
  margin-top: var(--mw-space-4);
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--mw-space-3);
  margin-bottom: var(--mw-space-4);
}
.head .mw-caption {
  max-width: 620px;
}
.wrap > .mw-chips {
  margin-bottom: var(--mw-space-4);
}
.panel {
  padding: var(--mw-space-4) var(--mw-space-4) var(--mw-space-2);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
  background: var(--mw-surface);
}
.sk--chart {
  display: block;
  height: 240px;
}
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: var(--mw-space-3);
  margin-top: var(--mw-space-3);
  padding-top: var(--mw-space-3);
  border-top: 1px solid var(--mw-line);
}
.stats li {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.stats__label {
  font-size: var(--mw-font-micro);
  color: var(--mw-ink-2);
}
.stats__num {
  font-size: 18px;
  font-weight: 700;
}
.stats--down {
  color: var(--mw-brand-ink);
}
.stats--up {
  color: var(--mw-accent-ink);
}

.list {
  margin-top: var(--mw-space-6);
}
.list__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--mw-space-3);
  margin-bottom: var(--mw-space-3);
}
.act {
  min-height: 36px;
  padding: 0 var(--mw-space-4);
  font-size: var(--mw-font-caption);
}
</style>