<script setup lang="ts">
/**
 * 健康概览（/pets/:pid/health）
 *
 * 首页要回答三个问题：**该做什么**（逾期/最紧急的照护）、**记了多少**（六类计数与档位上限）、
 * **最近发生了什么**（时间线预览 + 在用药物 + 过敏史）。
 *
 * 两个刻意的口径：
 * 1) 逾期提醒是**自己算的**：`overview.nextPreventive` 用 `.ge(nextDueAt, now)` 排除了逾期项，
 *    所以这里用 store 里的 `/{pid}/preventive` 全量 + `nextDueDays < 0` 分档（可以为负）。
 * 2) 导出与 AI 解读是**付费墙功能**：档位不够时后端回 `B000404` / `B000411`，这属于正常状态，
 *    提示由壳页统一渲染（store 的 `gate`），这里只负责把按钮和"进行中"做好。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import {
  PhArrowClockwise,
  PhArrowRight,
  PhCrown,
  PhDownloadSimple,
  PhInfo,
  PhSparkle,
  PhWarningCircle,
} from '@phosphor-icons/vue'
import {
  RECORD_TYPE_TEXT,
  getTimeline,
  healthErrorMessage,
  healthTimeText,
  isLimitReached,
  recordCountText,
  severityTone,
  timelineKey,
  weightChangeInfo,
  weightText,
  type HealthRecordType,
  type HealthTimelineItem,
} from '@/api/health'
import { ApiError } from '@/api/request'
import { useHealthStore } from '@/stores/health'
import RecordTypeIcon from '@/components/health/RecordTypeIcon.vue'

const route = useRoute()
const health = useHealthStore()
const { overview, loaded, exporting, aiLoading, ai } = storeToRefs(health)

const pid = computed(() => Number(route.params.pid))

/** 概览里显示的六类顺序（与页签一致） */
const TYPES: HealthRecordType[] = [
  'preventive',
  'medical',
  'medication',
  'allergy',
  'symptom',
  'weight',
]

/* ---------- 时间线预览（概览只取 5 条，完整列表在时间线页） ---------- */

const preview = ref<HealthTimelineItem[]>([])
const previewLoading = ref(true)
const previewError = ref('')

async function loadPreview() {
  previewLoading.value = true
  previewError.value = ''
  try {
    const list = await getTimeline(pid.value, { limit: 5 })
    preview.value = Array.isArray(list) ? list : []
  } catch (e) {
    previewError.value = e instanceof ApiError ? healthErrorMessage(e) : '时间线没能读到'
  } finally {
    previewLoading.value = false
  }
}

onMounted(loadPreview)
watch(pid, () => {
  void loadPreview()
})

/* ---------- 派生文案 ---------- */

const weightChange = computed(() => weightChangeInfo(overview.value?.weightChangeKg))
const nextDue = computed(() => health.mostUrgent)
const nextDueLabel = computed(() => (nextDue.value ? health.mostUrgentLabel : null))

/** 概览里"记录额度"的提示：不限档位与已记满都要说人话 */
const limitLabel = computed(() => recordCountText(health.totalRecords, health.recordLimit))

const exportAllowed = computed(() => overview.value?.exportAllowed === true)
const aiAllowed = computed(() => overview.value?.aiAnalysisAllowed === true)
const symptomCount = computed(() => health.countOf('symptom'))

function newRecord(kind: HealthRecordType) {
  return { name: 'health-record-new', params: { pid: pid.value, kind } }
}
</script>

<template>
  <div class="wrap">
    <div v-if="!loaded" class="mw-state">
      <p>正在读取健康概览</p>
    </div>

    <template v-else>
      <!-- 该做什么：最紧急的一件（含逾期） -->
      <section v-if="nextDue && nextDueLabel" class="urgent" :class="`urgent--${nextDueLabel.tone}`">
        <div class="urgent__icon">
          <PhWarningCircle :size="22" aria-hidden="true" />
        </div>
        <div class="urgent__main">
          <p class="urgent__kicker">
            {{ nextDueLabel.tone === 'overdue' ? '已经逾期' : '接下来要做的' }}
          </p>
          <p class="urgent__title">{{ nextDue.typeDesc }}：{{ nextDue.itemName }}</p>
          <p class="urgent__sub">
            <span class="mw-num">{{ nextDueLabel.text }}</span>
            <template v-if="nextDue.nextDueAt">
              ，到期时间 {{ healthTimeText(nextDue.nextDueAt) }}
            </template>
          </p>
        </div>
        <RouterLink
          class="mw-btn mw-btn--ghost urgent__cta"
          :to="{ name: 'health-preventive', params: { pid } }"
        >
          看全部疫苗驱虫
        </RouterLink>
      </section>

      <p v-else class="mw-alert mw-alert--info urgent--empty">
        <PhInfo :size="16" aria-hidden="true" />
        还没有设过「下次到期时间」的疫苗或驱虫记录，记一条并填上下次时间，这里就会提醒你。
      </p>

      <!-- 记了多少：六类计数（数字优先，点数字进对应列表） -->
      <section class="block">
        <div class="block__head">
          <h2 class="mw-h2">记了多少</h2>
          <p class="mw-caption">
            共 <span class="mw-num">{{ limitLabel }}</span>
            <template v-if="health.limitReached">，有分类已经记满</template>
          </p>
        </div>
        <ul class="tiles">
          <li v-for="t in TYPES" :key="t">
            <RouterLink class="tile" :to="{ name: `health-${t}`, params: { pid } }">
              <span class="tile__icon"><RecordTypeIcon :type="t" :size="16" /></span>
              <span class="tile__num mw-num">{{ health.countOf(t) }}</span>
              <span class="tile__label">{{ RECORD_TYPE_TEXT[t] }}</span>
              <span
                v-if="isLimitReached(t, overview?.limitReachedTypes)"
                class="mw-badge mw-badge--danger"
              >
                已记满
              </span>
            </RouterLink>
          </li>
        </ul>
      </section>

      <!-- 体重：一个数字 + 一个变化 -->
      <section class="block">
        <div class="block__head">
          <h2 class="mw-h2">体重</h2>
          <RouterLink class="mw-link" :to="{ name: 'health-weight', params: { pid } }">
            看曲线
            <PhArrowRight :size="14" aria-hidden="true" />
          </RouterLink>
        </div>
        <div v-if="overview?.latestWeight" class="weight">
          <div>
            <p class="weight__num mw-num">{{ weightText(overview.latestWeight.weightKg) }}</p>
            <p class="mw-caption">
              最近一次 {{ healthTimeText(overview.latestWeight.measuredAt) }}
            </p>
          </div>
          <p class="weight__change" :class="`weight__change--${weightChange.tone}`">
            {{ weightChange.text }}
          </p>
        </div>
        <div v-else class="mw-state mw-state--box">
          <p class="mw-state__title">还没有体重记录</p>
          <p>体重是判断宠物状态最直接的一项，建议固定时间（比如晨起空腹）记。</p>
          <RouterLink class="mw-btn mw-btn--primary" :to="newRecord('weight')">记一条体重</RouterLink>
        </div>
      </section>

      <div class="cols">
        <!-- 在用药物 -->
        <section class="block">
          <div class="block__head">
            <h2 class="mw-h2">在用的药</h2>
            <RouterLink class="mw-link" :to="{ name: 'health-medication', params: { pid } }">
              全部用药
              <PhArrowRight :size="14" aria-hidden="true" />
            </RouterLink>
          </div>
          <ul v-if="overview?.activeMedications?.length" class="mw-rows">
            <li v-for="m in overview.activeMedications" :key="m.id" class="mw-row">
              <div class="mw-row__main">
                <p class="mw-row__title">{{ m.medicineName }}</p>
                <p class="mw-row__sub">
                  <template v-if="m.dosage">{{ m.dosage }}</template>
                  <template v-if="m.dosage && m.frequency">，</template>
                  <template v-if="m.frequency">{{ m.frequency }}</template>
                  <template v-if="!m.dosage && !m.frequency">未填用量</template>
                </p>
                <p class="mw-caption">从 {{ healthTimeText(m.startAt) }} 开始</p>
              </div>
              <span class="mw-badge mw-badge--time">{{ m.statusDesc }}</span>
            </li>
          </ul>
          <p v-else class="mw-caption empty">当前没有「用药中」的记录。</p>
        </section>

        <!-- 过敏史 -->
        <section class="block">
          <div class="block__head">
            <h2 class="mw-h2">过敏史</h2>
            <RouterLink class="mw-link" :to="{ name: 'health-allergy', params: { pid } }">
              全部过敏
              <PhArrowRight :size="14" aria-hidden="true" />
            </RouterLink>
          </div>
          <ul v-if="overview?.allergies?.length" class="mw-rows">
            <li v-for="a in overview.allergies" :key="a.id" class="mw-row">
              <div class="mw-row__main">
                <p class="mw-row__title">{{ a.allergen }}</p>
                <p class="mw-row__sub">{{ a.reaction || '未记录反应' }}</p>
                <p v-if="a.note" class="mw-caption">{{ a.note }}</p>
              </div>
              <span class="mw-badge" :class="`mw-sev--${severityTone(a.severity)}`">
                {{ a.severityDesc }}
              </span>
            </li>
          </ul>
          <p v-else class="mw-caption empty">还没有记录过敏原。</p>
        </section>
      </div>

      <!-- 最近发生了什么 -->
      <section class="block">
        <div class="block__head">
          <h2 class="mw-h2">最近记录</h2>
          <RouterLink class="mw-link" :to="{ name: 'health-timeline', params: { pid } }">
            完整时间线
            <PhArrowRight :size="14" aria-hidden="true" />
          </RouterLink>
        </div>

        <div v-if="previewLoading" class="mw-rows">
          <span v-for="i in 3" :key="i" class="mw-skeleton sk" />
        </div>
        <div v-else-if="previewError" class="mw-state mw-state--box">
          <PhArrowClockwise :size="22" aria-hidden="true" />
          <p>{{ previewError }}</p>
          <button class="mw-btn mw-btn--ghost" type="button" @click="loadPreview">重试</button>
        </div>
        <ul v-else-if="preview.length" class="mw-rows">
          <li v-for="item in preview" :key="timelineKey(item)" class="mw-row">
            <span class="mw-row__icon"><RecordTypeIcon :type="item.recordType" :size="16" /></span>
            <div class="mw-row__main">
              <p class="mw-row__title">{{ item.title }}</p>
              <p class="mw-row__sub">
                {{ item.recordTypeDesc }}
                <template v-if="item.summary">，{{ item.summary }}</template>
              </p>
            </div>
            <span class="mw-caption mw-num">{{ healthTimeText(item.happenedAt) }}</span>
          </li>
        </ul>
        <div v-else class="mw-state mw-state--box">
          <p class="mw-state__title">还没有任何健康记录</p>
          <p>从最简单的一条开始，之后翻起来就能看出变化。</p>
          <div class="quick">
            <RouterLink class="mw-btn mw-btn--primary" :to="newRecord('weight')">记体重</RouterLink>
            <RouterLink class="mw-btn mw-btn--ghost" :to="newRecord('preventive')">
              记疫苗驱虫
            </RouterLink>
            <RouterLink class="mw-btn mw-btn--ghost" :to="newRecord('symptom')">记症状</RouterLink>
          </div>
        </div>
      </section>

      <!-- 会员功能：两个付费墙入口。入口必须存在，但档位不够时是"正常状态" -->
      <section class="block vip">
        <div class="block__head">
          <h2 class="mw-h2">
            <PhCrown :size="18" aria-hidden="true" />
            会员功能
          </h2>
          <p class="mw-caption">下面两项按会员档位开放，档位不够时这里会给出升级入口。</p>
        </div>

        <div class="vip__actions">
          <button
            class="mw-btn mw-btn--ghost vip__btn"
            type="button"
            :disabled="exporting"
            @click="health.exportReport()"
          >
            <PhDownloadSimple :size="16" aria-hidden="true" />
            {{ exporting ? '正在导出' : '导出健康档案' }}
          </button>
          <button
            class="mw-btn mw-btn--primary vip__btn"
            type="button"
            :disabled="aiLoading"
            @click="health.analyze()"
          >
            <PhSparkle :size="16" aria-hidden="true" />
            {{ aiLoading ? '正在解读，可能要十几秒' : 'AI 解读症状' }}
          </button>
        </div>

        <p class="mw-caption vip__hint">
          <template v-if="!exportAllowed">当前档位不支持导出。</template>
          <template v-else>当前档位支持导出。</template>
          <template v-if="!aiAllowed">AI 解读需要 34.9 及以上档位。</template>
          <template v-else>AI 解读已开放。</template>
          已有 <span class="mw-num">{{ symptomCount }}</span> 条症状记录。
        </p>

        <p v-if="aiLoading" class="mw-alert mw-alert--info">
          AI 正在读这只宠物最近的症状记录，一次解读要十几秒，请先别离开这一页。
        </p>

        <article v-if="ai" class="ai">
          <p class="ai__head">
            <span class="mw-badge mw-badge--done">AI 解读</span>
            <span class="mw-caption mw-num">
              基于 {{ ai.symptomCount }} 条症状，{{ healthTimeText(ai.generatedAt) }} 生成
            </span>
          </p>
          <p class="ai__text">{{ ai.analysis }}</p>
          <ul v-if="ai.sources.length" class="ai__sources">
            <li v-for="s in ai.sources" :key="s.id">
              <span class="mw-caption">知识库：{{ s.title }}</span>
            </li>
          </ul>
          <p class="mw-caption ai__note">
            {{ ai.disclaimer || 'AI 解读仅供参考，请以兽医面诊为准。' }}
          </p>
        </article>
      </section>
    </template>
  </div>
</template>

<style scoped>
.wrap {
  margin-top: var(--mw-space-4);
}
.block {
  margin-top: var(--mw-space-6);
}
.block__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--mw-space-3);
  margin-bottom: var(--mw-space-3);
}
.block__head .mw-h2 {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-2);
}

.urgent {
  display: flex;
  align-items: center;
  gap: var(--mw-space-4);
  padding: var(--mw-space-4) var(--mw-space-6);
  border: 1px solid var(--mw-line);
  border-left: 3px solid var(--mw-accent);
  border-radius: var(--mw-radius-card);
  background: var(--mw-surface);
}
.urgent--overdue {
  border-left-color: var(--mw-danger);
}
.urgent--none {
  border-left-color: var(--mw-muted);
}
.urgent__icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  flex: none;
  border-radius: var(--mw-radius-pill);
  background: rgba(255, 176, 32, 0.16);
  color: var(--mw-accent-ink);
}
.urgent--overdue .urgent__icon {
  background: rgba(229, 72, 77, 0.12);
  color: var(--mw-danger);
}
.urgent__main {
  flex: 1;
  min-width: 0;
}
.urgent__kicker {
  font-size: var(--mw-font-micro);
  font-weight: 600;
  color: var(--mw-ink-2);
}
.urgent__title {
  font-size: 17px;
  font-weight: 600;
}
.urgent__sub {
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
}
.urgent__cta {
  flex: none;
  min-height: 36px;
  padding: 0 var(--mw-space-4);
  font-size: var(--mw-font-caption);
}
.urgent--empty {
  display: flex;
  align-items: center;
  gap: var(--mw-space-2);
}

.tiles {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: var(--mw-space-3);
}
.tile {
  display: grid;
  grid-template-columns: auto 1fr auto;
  grid-template-areas:
    'icon num badge'
    'icon label badge';
  align-items: center;
  gap: 0 var(--mw-space-2);
  padding: var(--mw-space-4);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
  background: var(--mw-surface);
  color: var(--mw-ink);
  text-decoration: none;
}
.tile:hover {
  border-color: var(--mw-brand-ink);
  text-decoration: none;
}
.tile__icon {
  grid-area: icon;
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: var(--mw-radius-tag);
  background: var(--mw-canvas);
  color: var(--mw-ink-2);
}
.tile__num {
  grid-area: num;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.02em;
}
.tile__label {
  grid-area: label;
  font-size: var(--mw-font-micro);
  color: var(--mw-ink-2);
}
.tile .mw-badge {
  grid-area: badge;
}

.weight {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--mw-space-4);
  padding: var(--mw-space-4) var(--mw-space-6);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
  background: var(--mw-surface);
}
.weight__num {
  font-size: 34px;
  font-weight: 700;
  letter-spacing: -0.02em;
}
.weight__change {
  font-size: var(--mw-font-caption);
  font-weight: 600;
  color: var(--mw-ink-2);
}
.weight__change--down {
  color: var(--mw-brand-ink);
}
.weight__change--up {
  color: var(--mw-accent-ink);
}

.cols {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 0 var(--mw-space-6);
}

.sk {
  display: block;
  height: 56px;
}

.empty {
  padding: var(--mw-space-4);
  border: 1px dashed var(--mw-line);
  border-radius: var(--mw-radius-input);
}
.quick {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mw-space-2);
  justify-content: center;
}

.vip__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mw-space-3);
}
.vip__btn {
  min-height: 40px;
  font-size: var(--mw-font-caption);
}
.vip__hint {
  margin-top: var(--mw-space-3);
}
.ai {
  margin-top: var(--mw-space-4);
  padding: var(--mw-space-4) var(--mw-space-6);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
  background: var(--mw-surface);
}
.ai__head {
  display: flex;
  align-items: center;
  gap: var(--mw-space-3);
}
.ai__text {
  margin-top: var(--mw-space-3);
  white-space: pre-wrap;
}
.ai__sources {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mw-space-2);
  margin-top: var(--mw-space-2);
}
.ai__note {
  margin-top: var(--mw-space-3);
}
</style>