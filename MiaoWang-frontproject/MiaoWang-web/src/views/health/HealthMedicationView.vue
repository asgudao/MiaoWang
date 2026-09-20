<script setup lang="ts">
/**
 * 用药记录（/pets/:pid/health/medication）
 *
 * 两个契约点：
 * - 状态筛选 `?status=` **越界值不报错、也不忽略，而是按值过滤回空数组** ——
 *   所以"筛出 0 条"是正常结果，不要给它套一个"出错了"的样子。这里只发 0/1/2。
 * - 不传 `status` 时后端按 `endAt` 推断（有结束时间就是"已结束"）；本页是读，不涉及写。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { PhPencilSimple, PhPlus } from '@phosphor-icons/vue'
import {
  MEDICATION_STATUS,
  deleteMedication,
  healthTimeText,
  listMedication,
  type MedicationRecord,
} from '@/api/health'
import { useRecordList } from '@/composables/useRecordList'
import HealthDeleteButton from '@/components/health/HealthDeleteButton.vue'
import RecordListStates from '@/components/health/RecordListStates.vue'

const route = useRoute()
const pid = computed(() => Number(route.params.pid))

/** null = 全部（**不传 status**） */
const status = ref<number | null>(null)

const {
  items: records,
  loading,
  error,
  busyId,
  load,
  remove,
} = useRecordList<MedicationRecord>((p) => listMedication(p, status.value ?? undefined))

onMounted(() => void load(pid.value))
watch(pid, () => void load(pid.value))
watch(status, () => void load(pid.value))

const activeCount = computed(() => records.value.filter((r) => r.status === 0).length)

function statusTone(s: number): string {
  if (s === 0) return 'mw-badge--time'
  if (s === 1) return 'mw-badge--done'
  return 'mw-sev--none'
}

function newRecord() {
  return { name: 'health-record-new', params: { pid: pid.value, kind: 'medication' } }
}

function editRecord(id: number) {
  return { name: 'health-record-edit', params: { pid: pid.value, kind: 'medication', id } }
}
</script>

<template>
  <div class="wrap">
    <header class="head">
      <div>
        <h2 class="mw-h2">用药记录</h2>
        <p class="mw-caption">
          状态由后端推断或指定：填了结束时间就是"已结束"，中途停药选"已停用"。
        </p>
      </div>
      <RouterLink class="mw-btn mw-btn--primary" :to="newRecord()">
        <PhPlus :size="16" aria-hidden="true" />
        记一条用药
      </RouterLink>
    </header>

    <div class="mw-chips" role="group" aria-label="按状态筛选">
      <button
        class="mw-chip"
        :class="{ 'mw-chip--on': status === null }"
        type="button"
        @click="status = null"
      >
        全部
      </button>
      <button
        v-for="s in MEDICATION_STATUS"
        :key="s.value"
        class="mw-chip"
        :class="{ 'mw-chip--on': status === s.value }"
        type="button"
        @click="status = s.value"
      >
        {{ s.label }}
      </button>
    </div>

    <p v-if="status === null && activeCount" class="mw-caption summary">
      当前有 <span class="mw-num">{{ activeCount }}</span> 条"用药中"。
    </p>

    <RecordListStates
      :loading="loading"
      :error="error"
      :empty="!records.length"
      :empty-title="status === null ? '还没有用药记录' : '这个状态下没有记录'"
      :empty-desc="
        status === null
          ? '记下药名、用量与频次，复诊时医生一眼就能看完。'
          : '换个状态看看，或者记一条新的用药。'
      "
      @retry="load(pid)"
    >
      <template #empty-actions>
        <RouterLink class="mw-btn mw-btn--primary" :to="newRecord()">记一条用药</RouterLink>
        <button v-if="status !== null" class="mw-btn mw-btn--ghost" type="button" @click="status = null">
          看全部
        </button>
      </template>

      <ul class="mw-rows">
        <li v-for="r in records" :key="r.id" class="mw-row">
          <div class="mw-row__main">
            <p class="mw-row__title">
              {{ r.medicineName }}
              <span class="mw-badge" :class="statusTone(r.status)">{{ r.statusDesc }}</span>
            </p>
            <p class="mw-row__sub">
              <template v-if="r.dosage">{{ r.dosage }}</template>
              <template v-if="r.dosage && r.frequency">，</template>
              <template v-if="r.frequency">{{ r.frequency }}</template>
              <template v-if="!r.dosage && !r.frequency">未填用量</template>
            </p>
            <p class="mw-caption mw-num">
              {{ healthTimeText(r.startAt) }}
              <template v-if="r.endAt"> 到 {{ healthTimeText(r.endAt) }}</template>
              <template v-else> 开始，没有结束时间</template>
            </p>
            <p v-if="r.medicalId" class="mw-caption">关联就诊记录编号 {{ r.medicalId }}</p>
            <p v-if="r.remark" class="mw-caption">{{ r.remark }}</p>
          </div>
          <div class="mw-row__actions">
            <RouterLink class="mw-btn mw-btn--ghost act" :to="editRecord(r.id)">
              <PhPencilSimple :size="16" aria-hidden="true" />
              编辑
            </RouterLink>
            <HealthDeleteButton :busy="busyId === r.id" @confirm="remove(r.id, deleteMedication)" />
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
.summary {
  margin-top: var(--mw-space-3);
}
.wrap > .mw-chips {
  margin-bottom: var(--mw-space-4);
}
.act {
  min-height: 36px;
  padding: 0 var(--mw-space-4);
  font-size: var(--mw-font-caption);
}
</style>