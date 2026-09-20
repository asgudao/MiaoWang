<script setup lang="ts">
/**
 * 疫苗与驱虫（/pets/:pid/health/preventive）
 *
 * 这个页面是健康模块的"照护提醒"主场，三个口径必须记住：
 * 1) **逾期要自己算**：`overview.nextPreventive` 用 `.ge(nextDueAt, now)` 把逾期项排除了，
 *    所以页首的逾期清单来自 `GET /{pid}/preventive` **全量**，按 `nextDueDays` 分档
 *    （`<0` 已逾期 / `0` 今天 / `>0` 还有 N 天，**可以为负、整数截断**）。
 * 2) 类型筛选 `?type=` **非法值会被静默忽略**（回全部），所以只发 `VACCINE / DEWORM_IN / DEWORM_OUT`。
 * 3) 排序有两种：后端给的是"发生时间倒序"，但"下个月要打针"这种需求要按**到期紧急度**排，
 *    所以页面上给一个排序切换（默认按到期，逾期的最靠前）。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import { PhPencilSimple, PhPlus, PhWarningCircle } from '@phosphor-icons/vue'
import {
  PREVENTIVE_TYPES,
  deletePreventive,
  healthTimeText,
  listPreventive,
  overdueLabel,
  overduePreventives,
  sortByDue,
  type PreventiveRecord,
} from '@/api/health'
import { useHealthStore } from '@/stores/health'
import { useRecordList } from '@/composables/useRecordList'
import HealthDeleteButton from '@/components/health/HealthDeleteButton.vue'
import RecordListStates from '@/components/health/RecordListStates.vue'

const route = useRoute()
const health = useHealthStore()
const { preventive: allPreventive } = storeToRefs(health)

const pid = computed(() => Number(route.params.pid))

/** null = 全部（**不传 type** 才是全部） */
const type = ref<string | null>(null)
/** 'due' = 按到期紧急度（逾期在前）；'happened' = 按发生时间（后端默认） */
const sortMode = ref<'due' | 'happened'>('due')

const {
  items: records,
  loading,
  error,
  busyId,
  load,
  remove,
} = useRecordList<PreventiveRecord>((p) => listPreventive(p, type.value ?? undefined))

/** 逾期清单：用**全量**（含筛选之外的类型）算，页首提醒不该被当前筛选影响 */
const overdueAll = computed(() => overduePreventives(allPreventive.value))

/** 当前筛选结果按所选方式排序；按到期时"没填下次时间"的排最后 */
const shown = computed(() =>
  sortMode.value === 'due' ? sortByDue(records.value) : records.value,
)

onMounted(() => void load(pid.value))
watch(pid, () => void load(pid.value))
watch(type, () => void load(pid.value))

function selectType(v: string | null) {
  type.value = v
}

async function onDelete(id: number) {
  await remove(id, deletePreventive)
}

function newRecord() {
  return { name: 'health-record-new', params: { pid: pid.value, kind: 'preventive' } }
}

function editRecord(id: number) {
  return { name: 'health-record-edit', params: { pid: pid.value, kind: 'preventive', id } }
}
</script>

<template>
  <div class="wrap">
    <header class="head">
      <div>
        <h2 class="mw-h2">疫苗与驱虫</h2>
        <p class="mw-caption">
          逾期提醒按"下次到期时间"自己算：概览接口会把已逾期的项排除，所以这里用完整列表来分档。
        </p>
      </div>
      <RouterLink class="mw-btn mw-btn--primary" :to="newRecord()">
        <PhPlus :size="16" aria-hidden="true" />
        记一条
      </RouterLink>
    </header>

    <!-- 逾期清单：这是健康模块最该被看见的一块 -->
    <section v-if="overdueAll.length" class="overdue">
      <p class="overdue__head">
        <PhWarningCircle :size="18" aria-hidden="true" />
        <span><span class="mw-num">{{ overdueAll.length }}</span> 项已经逾期</span>
      </p>
      <ul class="overdue__list">
        <li v-for="r in overdueAll" :key="r.id">
          <span class="overdue__name">{{ r.typeDesc }}：{{ r.itemName }}</span>
          <span class="overdue__days mw-badge mw-badge--danger">
            {{ overdueLabel(r.nextDueDays).text }}
          </span>
          <span class="mw-caption mw-num">原定 {{ healthTimeText(r.nextDueAt) }}</span>
        </li>
      </ul>
      <p class="mw-caption overdue__note">
        补做之后回来把"下次到期时间"改成新的日期，提醒就会跟着更新。
      </p>
    </section>

    <div class="filters">
      <div class="mw-chips" role="group" aria-label="按类型筛选">
        <button
          class="mw-chip"
          :class="{ 'mw-chip--on': type === null }"
          type="button"
          @click="selectType(null)"
        >
          全部
        </button>
        <button
          v-for="t in PREVENTIVE_TYPES"
          :key="t.value"
          class="mw-chip"
          :class="{ 'mw-chip--on': type === t.value }"
          type="button"
          @click="selectType(t.value)"
        >
          {{ t.label }}
        </button>
      </div>

      <div class="mw-chips" role="group" aria-label="排序方式">
        <button
          class="mw-chip"
          :class="{ 'mw-chip--on': sortMode === 'due' }"
          type="button"
          @click="sortMode = 'due'"
        >
          按到期紧急
        </button>
        <button
          class="mw-chip"
          :class="{ 'mw-chip--on': sortMode === 'happened' }"
          type="button"
          @click="sortMode = 'happened'"
        >
          按发生时间
        </button>
      </div>
    </div>

    <RecordListStates
      :loading="loading"
      :error="error"
      :empty="!shown.length"
      empty-title="这一类还没有记录"
      empty-desc="记下疫苗或驱虫的时间，并填上「下次到期时间」，之后就会在这里提醒你。"
      @retry="load(pid)"
    >
      <template #empty-actions>
        <RouterLink class="mw-btn mw-btn--primary" :to="newRecord()">记第一条</RouterLink>
      </template>

      <ul class="mw-rows">
        <li v-for="r in shown" :key="r.id" class="mw-row">
          <div class="mw-row__main">
            <p class="mw-row__title">
              {{ r.itemName }}
              <span class="mw-badge mw-badge--done type-tag">{{ r.typeDesc }}</span>
            </p>
            <p class="mw-row__sub">
              接种时间 {{ healthTimeText(r.happenedAt) }}
              <template v-if="r.hospital">，{{ r.hospital }}</template>
              <template v-if="r.batchNo">，批号 {{ r.batchNo }}</template>
            </p>
            <p class="due">
              <template v-if="r.nextDueAt">
                <span class="mw-badge" :class="`due--${overdueLabel(r.nextDueDays).tone}`">
                  {{ overdueLabel(r.nextDueDays).text }}
                </span>
                <span class="mw-caption mw-num">下次 {{ healthTimeText(r.nextDueAt) }}</span>
              </template>
              <span v-else class="mw-caption">没有设下次时间</span>
            </p>
            <p v-if="r.remark" class="mw-caption">{{ r.remark }}</p>
          </div>
          <div class="mw-row__actions">
            <RouterLink class="mw-btn mw-btn--ghost act" :to="editRecord(r.id)">
              <PhPencilSimple :size="16" aria-hidden="true" />
              编辑
            </RouterLink>
            <HealthDeleteButton :busy="busyId === r.id" @confirm="onDelete(r.id)" />
          </div>
        </li>
      </ul>
    </RecordListStates>
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

.overdue {
  padding: var(--mw-space-4);
  border: 1px solid rgba(229, 72, 77, 0.28);
  border-radius: var(--mw-radius-card);
  background: rgba(229, 72, 77, 0.06);
}
.overdue__head {
  display: flex;
  align-items: center;
  gap: var(--mw-space-2);
  color: var(--mw-danger);
  font-weight: 700;
}
.overdue__list {
  margin-top: var(--mw-space-3);
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-2);
}
.overdue__list li {
  display: flex;
  align-items: center;
  gap: var(--mw-space-3);
  flex-wrap: wrap;
}
.overdue__name {
  font-weight: 600;
}
.overdue__days {
  flex: none;
}
.overdue__note {
  margin-top: var(--mw-space-3);
}

.filters {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: var(--mw-space-3);
  margin: var(--mw-space-4) 0;
}

.type-tag {
  margin-left: var(--mw-space-2);
  font-weight: 600;
}
.due {
  display: flex;
  align-items: center;
  gap: var(--mw-space-2);
  margin-top: var(--mw-space-1);
}
.due--overdue {
  background: rgba(229, 72, 77, 0.12);
  color: var(--mw-danger);
}
.due--today {
  background: rgba(229, 72, 77, 0.12);
  color: var(--mw-danger);
}
.due--soon {
  background: rgba(255, 176, 32, 0.16);
  color: var(--mw-accent-ink);
}
.due--later {
  background: var(--mw-canvas);
  color: var(--mw-ink-2);
}
.due--none {
  background: var(--mw-canvas);
  color: var(--mw-muted);
}

.act {
  min-height: 36px;
  padding: 0 var(--mw-space-4);
  font-size: var(--mw-font-caption);
}
</style>