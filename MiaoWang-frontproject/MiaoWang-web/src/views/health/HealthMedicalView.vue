<script setup lang="ts">
/**
 * 就诊记录（/pets/:pid/health/medical）
 *
 * 这类记录的附件字段叫 `attachments`，契约要点（实测）：
 * - **出参是数组**（无附件时是 `[]` 而不是 `null`）
 * - **入参是逗号串**（`toCommaString()` 负责转换，空则发 null）
 * - 附件只存**绝对 URL**（没有 objectKey 字段），所以界面直接当图片链接用
 */
import { computed, onMounted, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { PhPaperclip, PhPencilSimple, PhPlus } from '@phosphor-icons/vue'
import {
  attachmentList,
  deleteMedical,
  healthTimeText,
  listMedical,
} from '@/api/health'
import { useRecordList } from '@/composables/useRecordList'
import HealthDeleteButton from '@/components/health/HealthDeleteButton.vue'
import RecordListStates from '@/components/health/RecordListStates.vue'

const route = useRoute()
const pid = computed(() => Number(route.params.pid))

const { items: records, loading, error, busyId, load, remove } = useRecordList(listMedical)

onMounted(() => void load(pid.value))
watch(pid, () => void load(pid.value))

function newRecord() {
  return { name: 'health-record-new', params: { pid: pid.value, kind: 'medical' } }
}

function editRecord(id: number) {
  return { name: 'health-record-edit', params: { pid: pid.value, kind: 'medical', id } }
}

/** 费用文案（后端是 DECIMAL，给"未记录"而不是 0） */
function costText(cost: number | null): string {
  return cost === null || cost === undefined ? '未记录费用' : `¥${Number(cost).toFixed(2)}`
}
</script>

<template>
  <div class="wrap">
    <header class="head">
      <div>
        <h2 class="mw-h2">就诊记录</h2>
        <p class="mw-caption">
          化验单、影像这些照片走附件上传，一次最多 9 张；只存外链地址，删除记录不会删除已上传的图片。
        </p>
      </div>
      <RouterLink class="mw-btn mw-btn--primary" :to="newRecord()">
        <PhPlus :size="16" aria-hidden="true" />
        记一次就诊
      </RouterLink>
    </header>

    <RecordListStates
      :loading="loading"
      :error="error"
      :empty="!records.length"
      empty-title="还没有就诊记录"
      empty-desc="把诊断与处理写下来，换医院或换医生时能少说很多重复的话。"
      @retry="load(pid)"
    >
      <template #empty-actions>
        <RouterLink class="mw-btn mw-btn--primary" :to="newRecord()">记第一次就诊</RouterLink>
      </template>

      <ul class="mw-rows">
        <li v-for="r in records" :key="r.id" class="mw-row">
          <div class="mw-row__main">
            <p class="mw-row__title">{{ r.diagnosis || '未填诊断' }}</p>
            <p class="mw-row__sub">
              {{ healthTimeText(r.visitAt) }}
              <template v-if="r.hospital">，{{ r.hospital }}</template>
              <template v-if="r.doctor">，{{ r.doctor }}</template>
            </p>
            <p v-if="r.treatment" class="mw-caption">处理：{{ r.treatment }}</p>
            <p v-if="r.remark" class="mw-caption">{{ r.remark }}</p>

            <ul v-if="attachmentList(r.attachments).length" class="files">
              <li v-for="url in attachmentList(r.attachments)" :key="url">
                <a class="files__link" :href="url" target="_blank" rel="noopener noreferrer">
                  <img :src="url" alt="就诊附件" loading="lazy" />
                </a>
              </li>
            </ul>
          </div>
          <div class="side">
            <p class="mw-row__value">{{ costText(r.cost) }}</p>
            <p v-if="attachmentList(r.attachments).length" class="mw-caption count">
              <PhPaperclip :size="12" aria-hidden="true" />
              <span class="mw-num">{{ attachmentList(r.attachments).length }}</span> 个附件
            </p>
          </div>
          <div class="mw-row__actions">
            <RouterLink class="mw-btn mw-btn--ghost act" :to="editRecord(r.id)">
              <PhPencilSimple :size="16" aria-hidden="true" />
              编辑
            </RouterLink>
            <HealthDeleteButton :busy="busyId === r.id" @confirm="remove(r.id, deleteMedical)" />
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
.side {
  flex: none;
  text-align: right;
}
.count {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-1);
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