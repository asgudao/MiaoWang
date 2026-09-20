<script setup lang="ts">
/**
 * 过敏史（/pets/:pid/health/allergy）
 *
 * 这类记录**没有发生时间字段**（表里就没有这一列），后端按 `severity` 倒序返回（重度在前）。
 * 所以页面上的筛选是**本地按严重度过滤**（后端这个接口没有 status/type 这类参数），
 * 而不是像其他页那样发 `?severity=`（发了也不会生效）。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { PhPencilSimple, PhPlus } from '@phosphor-icons/vue'
import {
  SEVERITY_LEVELS,
  deleteAllergy,
  listAllergy,
  severityTone,
} from '@/api/health'
import { useRecordList } from '@/composables/useRecordList'
import HealthDeleteButton from '@/components/health/HealthDeleteButton.vue'
import RecordListStates from '@/components/health/RecordListStates.vue'

const route = useRoute()
const pid = computed(() => Number(route.params.pid))

/** null = 全部；1/2/3 = 只看某个严重度（本地过滤） */
const severity = ref<number | null>(null)

const { items: records, loading, error, busyId, load, remove } = useRecordList(listAllergy)

/** 本地过滤：严重度筛选不该再打一次后端（接口本来也没有这个参数） */
const shown = computed(() =>
  severity.value === null ? records.value : records.value.filter((r) => r.severity === severity.value),
)

const severeCount = computed(() => records.value.filter((r) => r.severity === 3).length)

onMounted(() => void load(pid.value))
watch(pid, () => void load(pid.value))

function newRecord() {
  return { name: 'health-record-new', params: { pid: pid.value, kind: 'allergy' } }
}

function editRecord(id: number) {
  return { name: 'health-record-edit', params: { pid: pid.value, kind: 'allergy', id } }
}
</script>

<template>
  <div class="wrap">
    <header class="head">
      <div>
        <h2 class="mw-h2">过敏史</h2>
        <p class="mw-caption">
          过敏史没有时间字段，按严重度排序（重度在前）；这里的严重度筛选是本地过滤。
        </p>
      </div>
      <RouterLink class="mw-btn mw-btn--primary" :to="newRecord()">
        <PhPlus :size="16" aria-hidden="true" />
        记一条过敏
      </RouterLink>
    </header>

    <p v-if="severeCount" class="mw-alert mw-alert--danger severe">
      有 <span class="mw-num">{{ severeCount }}</span> 项重度过敏，喂食与用药前务必先确认成分。
    </p>

    <div class="mw-chips" role="group" aria-label="按严重度筛选">
      <button
        class="mw-chip"
        :class="{ 'mw-chip--on': severity === null }"
        type="button"
        @click="severity = null"
      >
        全部
      </button>
      <button
        v-for="s in SEVERITY_LEVELS"
        :key="s.value"
        class="mw-chip"
        :class="{ 'mw-chip--on': severity === s.value }"
        type="button"
        @click="severity = s.value"
      >
        {{ s.label }}
      </button>
    </div>

    <RecordListStates
      :loading="loading"
      :error="error"
      :empty="!shown.length"
      :empty-title="severity === null ? '还没有过敏记录' : '没有这个严重度的记录'"
      empty-desc="已知的过敏原写在档案里，换医院或寄养时能少一次试错。"
      @retry="load(pid)"
    >
      <template #empty-actions>
        <RouterLink class="mw-btn mw-btn--primary" :to="newRecord()">记一条过敏</RouterLink>
        <button v-if="severity !== null" class="mw-btn mw-btn--ghost" type="button" @click="severity = null">
          看全部
        </button>
      </template>

      <ul class="mw-rows">
        <li v-for="r in shown" :key="r.id" class="mw-row">
          <div class="mw-row__main">
            <p class="mw-row__title">{{ r.allergen }}</p>
            <p class="mw-row__sub">{{ r.reaction || '未记录过敏反应' }}</p>
            <p v-if="r.note" class="mw-caption">{{ r.note }}</p>
          </div>
          <span class="mw-badge sev" :class="`mw-sev--${severityTone(r.severity)}`">
            {{ r.severityDesc }}
          </span>
          <div class="mw-row__actions">
            <RouterLink class="mw-btn mw-btn--ghost act" :to="editRecord(r.id)">
              <PhPencilSimple :size="16" aria-hidden="true" />
              编辑
            </RouterLink>
            <HealthDeleteButton :busy="busyId === r.id" @confirm="remove(r.id, deleteAllergy)" />
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
.severe {
  margin-bottom: var(--mw-space-4);
}
.wrap > .mw-chips {
  margin-bottom: var(--mw-space-4);
}
.sev {
  flex: none;
}
.act {
  min-height: 36px;
  padding: 0 var(--mw-space-4);
  font-size: var(--mw-font-caption);
}
</style>