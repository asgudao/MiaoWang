<script setup lang="ts">
/**
 * 症状日记（/pets/:pid/health/symptom）
 *
 * 两个契约点：
 * - **传 `symptomId` 会覆盖 `symptomName`**（知识库标题胜出，POST/PUT 都成立），
 *   所以"选了知识库条目"和"自由填写名字"是两条互斥的路；列表上直接标出来是哪种。
 * - 图片字段叫 `images`，与就诊的 `attachments` 一样：**出参数组、入参逗号串**，空是 `[]`。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { PhArrowRight, PhPencilSimple, PhPlus, PhSparkle } from '@phosphor-icons/vue'
import {
  SEVERITY_LEVELS,
  attachmentList,
  deleteSymptom,
  healthTimeText,
  listSymptomLogs,
  severityTone,
  type SymptomLog,
} from '@/api/health'
import { useRecordList } from '@/composables/useRecordList'
import HealthDeleteButton from '@/components/health/HealthDeleteButton.vue'
import RecordListStates from '@/components/health/RecordListStates.vue'

const route = useRoute()
const pid = computed(() => Number(route.params.pid))

/** null = 全部；1/2/3 = 只看某档严重度（本地过滤，接口没有这个参数） */
const severity = ref<number | null>(null)

const { items: records, loading, error, busyId, load, remove } =
  useRecordList<SymptomLog>(listSymptomLogs)

const shown = computed(() =>
  severity.value === null
    ? records.value
    : records.value.filter((r) => r.severity === severity.value),
)

onMounted(() => void load(pid.value))
watch(pid, () => void load(pid.value))

function newRecord() {
  return { name: 'health-record-new', params: { pid: pid.value, kind: 'symptom' } }
}

function editRecord(id: number) {
  return { name: 'health-record-edit', params: { pid: pid.value, kind: 'symptom', id } }
}
</script>

<template>
  <div class="wrap">
    <header class="head">
      <div>
        <h2 class="mw-h2">症状日记</h2>
        <p class="mw-caption">
          从知识库的症状列表里选，名字以知识库为准；不在列表里的症状可以自由填写。
        </p>
      </div>
      <RouterLink class="mw-btn mw-btn--primary" :to="newRecord()">
        <PhPlus :size="16" aria-hidden="true" />
        记一条症状
      </RouterLink>
    </header>

    <p class="mw-alert mw-alert--info ai-tip">
      <PhSparkle :size="16" aria-hidden="true" />
      <span>记满几条之后，可以在概览页用「AI 解读症状」把最近的变化讲成一段话。</span>
      <RouterLink class="mw-link" :to="{ name: 'health-overview', params: { pid } }">
        去概览
        <PhArrowRight :size="14" aria-hidden="true" />
      </RouterLink>
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
      :empty-title="severity === null ? '还没有症状记录' : '没有这一档严重度的记录'"
      empty-desc="记下时间、表现和严重度，AI 解读与复诊都要靠这些细节。"
      @retry="load(pid)"
    >
      <template #empty-actions>
        <RouterLink class="mw-btn mw-btn--primary" :to="newRecord()">记第一条症状</RouterLink>
        <button
          v-if="severity !== null"
          class="mw-btn mw-btn--ghost"
          type="button"
          @click="severity = null"
        >
          看全部
        </button>
      </template>

      <ul class="mw-rows">
        <li v-for="r in shown" :key="r.id" class="mw-row">
          <div class="mw-row__main">
            <p class="mw-row__title">
              {{ r.symptomName || '未命名症状' }}
              <span class="mw-badge" :class="`mw-sev--${severityTone(r.severity)}`">
                {{ r.severityDesc || '未分级' }}
              </span>
              <span class="mw-badge source" :class="r.symptomId ? 'mw-badge--done' : 'mw-sev--none'">
                {{ r.symptomId ? '知识库条目' : '自由填写' }}
              </span>
            </p>
            <p class="mw-row__sub mw-num">{{ healthTimeText(r.occurredAt) }}</p>
            <p v-if="r.description" class="mw-caption">{{ r.description }}</p>
            <p v-if="r.remark" class="mw-caption">{{ r.remark }}</p>

            <ul v-if="attachmentList(r.images).length" class="files">
              <li v-for="url in attachmentList(r.images)" :key="url">
                <a class="files__link" :href="url" target="_blank" rel="noopener noreferrer">
                  <img :src="url" alt="症状照片" loading="lazy" />
                </a>
              </li>
            </ul>
          </div>
          <div class="mw-row__actions">
            <RouterLink class="mw-btn mw-btn--ghost act" :to="editRecord(r.id)">
              <PhPencilSimple :size="16" aria-hidden="true" />
              编辑
            </RouterLink>
            <HealthDeleteButton :busy="busyId === r.id" @confirm="remove(r.id, deleteSymptom)" />
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
.ai-tip {
  display: flex;
  align-items: center;
  gap: var(--mw-space-2);
  margin-bottom: var(--mw-space-4);
}
.ai-tip .mw-link {
  margin-left: auto;
}
.wrap > .mw-chips {
  margin-bottom: var(--mw-space-4);
}
.source {
  margin-left: var(--mw-space-1);
}
.files {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mw-space-2);
  margin-top: var(--mw-space-2);
}
.files__link {
  display: block;
  width: 56px;
  height: 56px;
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-tag);
  overflow: hidden;
}
.files__link img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.act {
  min-height: 36px;
  padding: 0 var(--mw-space-4);
  font-size: var(--mw-font-caption);
}
</style>