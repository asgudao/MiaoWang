<script setup lang="ts">
/**
 * 健康记录：新建 / 编辑（`/pets/:pid/health/new/:kind` 与 `/pets/:pid/health/:kind/:id/edit`）
 *
 * 六类记录共用这一个页面（字段不同、规则不同，但"取数 → 校验 → 提交 → 回列表"是同一条流水线）。
 *
 * 契约要点（都在 `api/health.ts` 里落成函数，这里只负责用）：
 * 1) **PUT 是全量覆盖**（后端 `set()` 到实体：没发的字段会被写成 null）→
 *    编辑页固定走"拉列表 → 按 id 找到记录 → `toXxxDraft()` 整条拷贝 → 改 → 整体 PUT"。
 *    这也是为什么删除一个附件必须把整条记录重发一遍。
 * 2) **没有单条 GET 接口** → 编辑只能从列表里找；找不到就明说"可能已被删除"，不要假装能编辑。
 * 3) **时间入参是 `yyyy-MM-dd HH:mm:ss`**（不是 ISO）：`datetime-local` 输入框与它之间用
 *    `toDatetimeLocalValue()` / `fromDatetimeLocalValue()` 双向转换（手写正则，绕开 iOS 的 Invalid Date）。
 * 4) **`symptomId` 会覆盖 `symptomName`**：选了知识库条目就只发 id（`symptomBody()` 已经这么做了）。
 * 5) **附件/图片入参是逗号串**：界面里一律用数组，`toCommaString()` 在最后一步转换，空 → null。
 * 6) 付费墙（记录数上限 `B000403`）是**正常状态**：交给 store 的 `gate`，由壳页渲染升级引导。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import {
  PhArrowClockwise,
  PhArrowLeft,
  PhFloppyDisk,
} from '@phosphor-icons/vue'
import {
  MEDICATION_STATUS,
  PREVENTIVE_TYPES,
  RECORD_TYPE_TEXT,
  SEVERITY_LEVELS,
  createAllergy,
  createMedical,
  createMedication,
  createPreventive,
  createSymptom,
  createWeight,
  fromDatetimeLocalValue,
  healthErrorMessage,
  healthGateOf,
  listAllergy,
  listMedical,
  listMedication,
  listPreventive,
  listSymptomLogs,
  listWeight,
  nowDatetimeLocal,
  parseHealthTime,
  symptomSpeciesOf,
  toAllergyDraft,
  toDatetimeLocalValue,
  toMedicalDraft,
  toMedicationDraft,
  toPreventiveDraft,
  toSymptomDraft,
  toWeightDraft,
  updateAllergy,
  updateMedical,
  updateMedication,
  updatePreventive,
  updateSymptom,
  updateWeight,
  type HealthRecordType,
  type AllergyDraft,
  type MedicalDraft,
  type MedicationDraft,
  type PreventiveDraft,
  type SymptomDraft,
  type WeightDraft,
} from '@/api/health'
import { useHealthStore } from '@/stores/health'
import { usePetsStore } from '@/stores/pets'
import AttachmentUploader from '@/components/health/AttachmentUploader.vue'
import SymptomPicker from '@/components/health/SymptomPicker.vue'

const route = useRoute()
const router = useRouter()
const health = useHealthStore()
const pets = usePetsStore()

const pid = computed(() => Number(route.params.pid))
const kind = computed(() => route.params.kind as HealthRecordType)
const editId = computed(() => (route.params.id ? Number(route.params.id) : null))
const isEdit = computed(() => editId.value !== null)

const pet = computed(() => pets.byId(pid.value))
/** 症状选项按物种过滤（1=猫 2=狗）；取不到就不传，后端回全部 */
const species = computed(() => symptomSpeciesOf(pet.value?.species ?? health.species))

/* ---------- 六类草稿。同一时刻只有一个 kind 生效，但六个都声明成具体类型，模板里就不用做类型体操 ---------- */

/**
 * 新建时的默认时间：直接就是后端要的 `yyyy-MM-dd HH:mm:ss`。
 * ⚠️ 别把 `datetime-local` 的 `T` 形式（`2026-09-20T10:30`）当默认值塞进 draft ——
 * 那样"没动过时间"的表单会把 ISO 形式发给后端（后端要的是带空格的形式）。
 */
const nowHealth = fromDatetimeLocalValue(nowDatetimeLocal())

function blankPreventive(): PreventiveDraft {
  return {
    type: 'VACCINE',
    itemName: '',
    happenedAt: nowHealth,
    nextDueAt: null,
    hospital: null,
    batchNo: null,
    remark: null,
  }
}
function blankMedical(): MedicalDraft {
  return {
    visitAt: nowHealth,
    hospital: null,
    doctor: null,
    diagnosis: '',
    treatment: null,
    cost: null,
    attachments: [],
    remark: null,
  }
}
function blankMedication(): MedicationDraft {
  return {
    medicalId: null,
    medicineName: '',
    dosage: null,
    frequency: null,
    startAt: nowHealth,
    endAt: null,
    status: 0,
    remark: null,
  }
}
function blankAllergy(): AllergyDraft {
  return { allergen: '', severity: 1, reaction: null, note: null }
}
function blankSymptom(): SymptomDraft {
  return {
    symptomId: null,
    symptomName: '',
    description: null,
    severity: 1,
    occurredAt: nowHealth,
    images: [],
    remark: null,
  }
}
function blankWeight(): WeightDraft {
  // `Number.NaN` = "还没填"：`type="number"` 的空值会变成空串，交给 numberField 统一收敛
  return { weightKg: Number.NaN, measuredAt: nowHealth, note: null }
}

const preventiveDraft = ref<PreventiveDraft>(blankPreventive())
const medicalDraft = ref<MedicalDraft>(blankMedical())
const medicationDraft = ref<MedicationDraft>(blankMedication())
const allergyDraft = ref<AllergyDraft>(blankAllergy())
const symptomDraft = ref<SymptomDraft>(blankSymptom())
const weightDraft = ref<WeightDraft>(blankWeight())

/** 两个可空的时间字段用可写计算属性包一层（`datetime-local` 空串 ↔ 后端 null） */
const nextDueLocal = computed({
  get: () => toDatetimeLocalValue(preventiveDraft.value.nextDueAt),
  set: (v: string) => {
    preventiveDraft.value.nextDueAt = fromDatetimeLocalValue(v) || null
  },
})
const endAtLocal = computed({
  get: () => toDatetimeLocalValue(medicationDraft.value.endAt),
  set: (v: string) => {
    medicationDraft.value.endAt = fromDatetimeLocalValue(v) || null
  },
})

/**
 * 数字输入框的统一包装。
 *
 * 为什么不用 `v-model.number`：`type="number"` 清空后 Vue 给的是**空串**，
 * 而 `''` 到了请求体里会变成 `0`（`Number('')`）——"费用留空"会被悄悄存成 0 元，
 * 体重留空会被存成 0kg（后端再拒一次）。这里让"空"在草稿里始终保持 null / NaN。
 */
function numberField(
  get: () => number | null | undefined,
  set: (v: number | null) => void,
) {
  return computed<string>({
    get: () => {
      const v = get()
      return v === null || v === undefined || !Number.isFinite(Number(v)) ? '' : String(v)
    },
    set: (v) => {
      const text = String(v ?? '').trim()
      set(text === '' ? null : Number(text))
    },
  })
}

const costModel = numberField(
  () => medicalDraft.value.cost,
  (v) => {
    medicalDraft.value.cost = v
  },
)
const medicalIdModel = numberField(
  () => medicationDraft.value.medicalId,
  (v) => {
    medicationDraft.value.medicalId = v
  },
)
/** 体重必填，所以"空"回到 NaN（校验会拦下来），不能变成 null */
const weightKgModel = numberField(
  () => weightDraft.value.weightKg,
  (v) => {
    weightDraft.value.weightKg = v === null ? Number.NaN : v
  },
)

/**
 * 症状名在记录里是可空字段（`symptomName: string | null`），但 SymptomPicker 的第二个
 * v-model 固定是字符串，所以这里包一层：界面给空串，草稿里给 null。
 */
const symptomNameModel = computed({
  get: () => symptomDraft.value.symptomName ?? '',
  set: (v: string) => {
    symptomDraft.value.symptomName = v
  },
})

/* ---------- 编辑：拉列表 → 按 id 找 → 整条拷贝 ---------- */

const loading = ref(isEdit.value)
const ready = ref(!isEdit.value)
const loadError = ref('')

/** 记录不在列表里（可能刚被删掉）时的固定说法 */
const MISSING = '这条记录不在列表里，可能已经被删除了。'

async function loadRecord() {
  const id = editId.value
  if (id === null) return
  loading.value = true
  ready.value = false
  loadError.value = ''
  try {
    switch (kind.value) {
      case 'preventive': {
        const r = (await listPreventive(pid.value)).find((x) => x.id === id)
        if (!r) throw new Error(MISSING)
        preventiveDraft.value = toPreventiveDraft(r)
        break
      }
      case 'medical': {
        const r = (await listMedical(pid.value)).find((x) => x.id === id)
        if (!r) throw new Error(MISSING)
        medicalDraft.value = toMedicalDraft(r)
        break
      }
      case 'medication': {
        const r = (await listMedication(pid.value)).find((x) => x.id === id)
        if (!r) throw new Error(MISSING)
        medicationDraft.value = toMedicationDraft(r)
        break
      }
      case 'allergy': {
        const r = (await listAllergy(pid.value)).find((x) => x.id === id)
        if (!r) throw new Error(MISSING)
        allergyDraft.value = toAllergyDraft(r)
        break
      }
      case 'symptom': {
        const r = (await listSymptomLogs(pid.value)).find((x) => x.id === id)
        if (!r) throw new Error(MISSING)
        symptomDraft.value = toSymptomDraft(r)
        break
      }
      case 'weight': {
        const r = (await listWeight(pid.value)).find((x) => x.id === id)
        if (!r) throw new Error(MISSING)
        weightDraft.value = toWeightDraft(r)
        break
      }
      default:
        throw new Error('不支持的记录类型')
    }
    ready.value = true
  } catch (e) {
    loadError.value = e instanceof Error && e.message === MISSING ? MISSING : healthErrorMessage(e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (!pets.loaded) void pets.fetchMine()
  if (isEdit.value) void loadRecord()
})
watch([pid, kind, editId], () => {
  if (isEdit.value) void loadRecord()
})

/* ---------- 校验（每条都给"哪里不对、怎么改"，不要只说"输入有误"） ---------- */

/** 时间串是否可用（`datetime-local` 清空后会变成空串） */
function timeOk(v: string | null): boolean {
  return !!v && parseHealthTime(v) !== null
}

function validate(): string | null {
  switch (kind.value) {
    case 'preventive': {
      const d = preventiveDraft.value
      if (!PREVENTIVE_TYPES.some((t) => t.value === d.type)) return '请选择疫苗或驱虫的类型'
      if (!d.itemName.trim()) return '请填写疫苗或驱虫的名称'
      if (!timeOk(d.happenedAt)) return '请填写接种/驱虫的时间'
      if (d.nextDueAt && !timeOk(d.nextDueAt)) return '下次到期时间填得不完整'
      if (d.nextDueAt && parseHealthTime(d.nextDueAt)! < parseHealthTime(d.happenedAt)!)
        return '下次到期时间不能早于接种时间'
      return null
    }
    case 'medical': {
      const d = medicalDraft.value
      if (!timeOk(d.visitAt)) return '请填写就诊时间'
      if (!(d.diagnosis ?? '').trim()) return '请填写诊断结论'
      if (d.cost !== null && (!Number.isFinite(Number(d.cost)) || Number(d.cost) < 0))
        return '费用要填 0 或正数'
      return null
    }
    case 'medication': {
      const d = medicationDraft.value
      if (!d.medicineName.trim()) return '请填写药名'
      if (!timeOk(d.startAt)) return '请填写开始用药的时间'
      if (d.endAt && !timeOk(d.endAt)) return '结束时间填得不完整'
      if (d.endAt && parseHealthTime(d.endAt)! < parseHealthTime(d.startAt)!)
        return '结束时间不能早于开始时间'
      if (d.status === 1 && !d.endAt) return '状态选了「已结束」，就要填结束时间'
      return null
    }
    case 'allergy': {
      const d = allergyDraft.value
      if (!d.allergen.trim()) return '请填写过敏原'
      if (!SEVERITY_LEVELS.some((s) => s.value === d.severity)) return '请选择严重度'
      return null
    }
    case 'symptom': {
      const d = symptomDraft.value
      if (!timeOk(d.occurredAt)) return '请填写症状出现的时间'
      if (!d.symptomId && !(d.symptomName ?? '').trim())
        return '请从列表里选一个症状，或者自己填一个症状名'
      return null
    }
    case 'weight': {
      const d = weightDraft.value
      const kg = Number(d.weightKg)
      if (!Number.isFinite(kg)) return '请填写体重（kg）'
      // 与后端一致：0.05 ~ 200 kg（源码 MIN_WEIGHT_KG / MAX_WEIGHT_KG）
      if (kg < 0.05 || kg > 200) return '体重需在 0.05 ~ 200 kg 之间'
      if (!timeOk(d.measuredAt)) return '请填写测量时间'
      return null
    }
    default:
      return '不支持的记录类型'
  }
}

/* ---------- 提交 ---------- */

const saving = ref(false)
const formError = ref('')

async function submit() {
  if (saving.value) return
  const problem = validate()
  if (problem) {
    formError.value = problem
    return
  }
  formError.value = ''
  saving.value = true
  const id = editId.value
  try {
    // 写接口统一收 **draft**，`*Body()` 的转换（逗号串、null 归一化、显式 status）在 api 层做，
    // 这里不要自己先转一次 —— 转两次会把数组再拆成字符串，PUT 时反而出错
    switch (kind.value) {
      case 'preventive': {
        const draft = preventiveDraft.value
        if (id === null) await createPreventive(pid.value, draft)
        else await updatePreventive(id, draft)
        break
      }
      case 'medical': {
        const draft = medicalDraft.value
        if (id === null) await createMedical(pid.value, draft)
        else await updateMedical(id, draft)
        break
      }
      case 'medication': {
        const draft = medicationDraft.value
        if (id === null) await createMedication(pid.value, draft)
        else await updateMedication(id, draft)
        break
      }
      case 'allergy': {
        const draft = allergyDraft.value
        if (id === null) await createAllergy(pid.value, draft)
        else await updateAllergy(id, draft)
        break
      }
      case 'symptom': {
        const draft = symptomDraft.value
        if (id === null) await createSymptom(pid.value, draft)
        else await updateSymptom(id, draft)
        break
      }
      case 'weight': {
        const draft = weightDraft.value
        if (id === null) await createWeight(pid.value, draft)
        else await updateWeight(id, draft)
        break
      }
      default:
        throw new Error('不支持的记录类型')
    }

    // 概览的计数/额度与逾期提醒都会变，先刷新再跳回列表
    await health.refresh()
    health.notice = id === null ? '记录已保存' : '修改已保存'
    await router.push({ name: `health-${kind.value}`, params: { pid: pid.value } })
  } catch (e) {
    // 记录数上限这类权益边界是正常状态，交给壳页的 gatebar；其它才是错误
    const gate = healthGateOf(e)
    if (gate) health.gate = gate
    else formError.value = healthErrorMessage(e)
  } finally {
    saving.value = false
  }
}

const title = computed(() => `${isEdit.value ? '编辑' : '新增'}${RECORD_TYPE_TEXT[kind.value] ?? '记录'}`)

function backToList() {
  return { name: `health-${kind.value}`, params: { pid: pid.value } }
}
</script>

<template>
  <div class="wrap">
    <header class="head">
      <RouterLink class="mw-link" :to="backToList()">
        <PhArrowLeft :size="16" aria-hidden="true" />
        返回{{ RECORD_TYPE_TEXT[kind] ?? '列表' }}
      </RouterLink>
      <h2 class="mw-h2">{{ title }}</h2>
      <p v-if="isEdit" class="mw-caption">
        保存时会整条覆盖这条记录：表单里没有填的字段会被清空（后端 PUT 就是这么实现的）。
      </p>
    </header>

    <div v-if="loading" class="mw-rows">
      <span class="mw-skeleton sk" />
      <span class="mw-skeleton sk" />
    </div>

    <div v-else-if="loadError" class="mw-state mw-state--box">
      <PhArrowClockwise :size="24" aria-hidden="true" />
      <p class="mw-state__title">没能读到要编辑的记录</p>
      <p>{{ loadError }}</p>
      <button class="mw-btn mw-btn--ghost" type="button" @click="loadRecord">重试</button>
      <RouterLink class="mw-btn mw-btn--primary" :to="backToList()">回到列表</RouterLink>
    </div>

    <form v-else-if="ready" class="form" @submit.prevent="submit">
      <p v-if="formError" class="mw-alert mw-alert--danger err">{{ formError }}</p>

      <!-- ===================== 疫苗 / 驱虫 ===================== -->
      <template v-if="kind === 'preventive'">
        <div class="mw-field">
          <span>类型</span>
          <div class="mw-chips" role="group" aria-label="疫苗或驱虫的类型">
            <button
              v-for="t in PREVENTIVE_TYPES"
              :key="t.value"
              class="mw-chip"
              :class="{ 'mw-chip--on': preventiveDraft.type === t.value }"
              type="button"
              @click="preventiveDraft.type = t.value"
            >
              {{ t.label }}
            </button>
          </div>
        </div>
        <label class="mw-field">
          <span>名称（必填）</span>
          <input
            v-model="preventiveDraft.itemName"
            class="mw-input"
            type="text"
            maxlength="50"
            placeholder="比如：猫三联、狂犬、大宠爱"
          />
        </label>
        <label class="mw-field">
          <span>接种 / 驱虫时间（必填）</span>
          <input
            :value="toDatetimeLocalValue(preventiveDraft.happenedAt)"
            class="mw-input"
            type="datetime-local"
            @input="preventiveDraft.happenedAt = fromDatetimeLocalValue(($event.target as HTMLInputElement).value)"
          />
        </label>
        <label class="mw-field">
          <span>下次到期时间（可空）</span>
          <input v-model="nextDueLocal" class="mw-input" type="datetime-local" />
          <span class="mw-caption">
            留空就没有提醒；填了之后概览与疫苗页会按它算「还有几天 / 已逾期几天」。
          </span>
        </label>
        <div class="pair">
          <label class="mw-field">
            <span>医院</span>
            <input v-model="preventiveDraft.hospital" class="mw-input" type="text" maxlength="50" />
          </label>
          <label class="mw-field">
            <span>批号</span>
            <input v-model="preventiveDraft.batchNo" class="mw-input" type="text" maxlength="50" />
          </label>
        </div>
        <label class="mw-field">
          <span>备注</span>
          <textarea v-model="preventiveDraft.remark" class="mw-input area" rows="3" maxlength="200" />
        </label>
      </template>

      <!-- ===================== 就诊 ===================== -->
      <template v-else-if="kind === 'medical'">
        <label class="mw-field">
          <span>就诊时间（必填）</span>
          <input
            :value="toDatetimeLocalValue(medicalDraft.visitAt)"
            class="mw-input"
            type="datetime-local"
            @input="medicalDraft.visitAt = fromDatetimeLocalValue(($event.target as HTMLInputElement).value)"
          />
        </label>
        <label class="mw-field">
          <span>诊断（必填）</span>
          <input v-model="medicalDraft.diagnosis" class="mw-input" type="text" maxlength="100" />
        </label>
        <div class="pair">
          <label class="mw-field">
            <span>医院</span>
            <input v-model="medicalDraft.hospital" class="mw-input" type="text" maxlength="50" />
          </label>
          <label class="mw-field">
            <span>医生</span>
            <input v-model="medicalDraft.doctor" class="mw-input" type="text" maxlength="50" />
          </label>
        </div>
        <label class="mw-field">
          <span>处理方式</span>
          <textarea v-model="medicalDraft.treatment" class="mw-input area" rows="3" maxlength="200" />
        </label>
        <label class="mw-field">
          <span>费用（元）</span>
          <input v-model="costModel" class="mw-input" type="number" min="0" step="0.01" inputmode="decimal" />
          <span class="mw-caption">留空就是不记费用，不会被当成 0。</span>
        </label>
        <div class="mw-field">
          <span>化验单 / 影像</span>
          <AttachmentUploader v-model="medicalDraft.attachments" />
        </div>
        <label class="mw-field">
          <span>备注</span>
          <textarea v-model="medicalDraft.remark" class="mw-input area" rows="3" maxlength="200" />
        </label>
      </template>

      <!-- ===================== 用药 ===================== -->
      <template v-else-if="kind === 'medication'">
        <label class="mw-field">
          <span>药名（必填）</span>
          <input v-model="medicationDraft.medicineName" class="mw-input" type="text" maxlength="50" />
        </label>
        <div class="pair">
          <label class="mw-field">
            <span>用量</span>
            <input
              v-model="medicationDraft.dosage"
              class="mw-input"
              type="text"
              maxlength="50"
              placeholder="比如：半片"
            />
          </label>
          <label class="mw-field">
            <span>频次</span>
            <input
              v-model="medicationDraft.frequency"
              class="mw-input"
              type="text"
              maxlength="50"
              placeholder="比如：每天两次"
            />
          </label>
        </div>
        <label class="mw-field">
          <span>开始时间（必填）</span>
          <input
            :value="toDatetimeLocalValue(medicationDraft.startAt)"
            class="mw-input"
            type="datetime-local"
            @input="medicationDraft.startAt = fromDatetimeLocalValue(($event.target as HTMLInputElement).value)"
          />
        </label>
        <label class="mw-field">
          <span>结束时间（可空）</span>
          <input v-model="endAtLocal" class="mw-input" type="datetime-local" />
        </label>
        <div class="mw-field">
          <span>状态</span>
          <div class="mw-chips" role="group" aria-label="用药状态">
            <button
              v-for="s in MEDICATION_STATUS"
              :key="s.value"
              class="mw-chip"
              :class="{ 'mw-chip--on': medicationDraft.status === s.value }"
              type="button"
              @click="medicationDraft.status = s.value"
            >
              {{ s.label }}
            </button>
          </div>
          <span class="mw-caption">
            状态会明确发给后端；不传状态时后端才会按结束时间自己推断，所以这里一定要选对。
          </span>
        </div>
        <label class="mw-field">
          <span>关联就诊记录编号</span>
          <input
            v-model="medicalIdModel"
            class="mw-input"
            type="number"
            min="1"
            step="1"
            placeholder="可以留空"
          />
          <span class="mw-caption">
            没有单条就诊记录的接口，所以这里手填编号（在就诊列表里能看到编号）；填错只影响这条关联，不会报错。
          </span>
        </label>
        <label class="mw-field">
          <span>备注</span>
          <textarea v-model="medicationDraft.remark" class="mw-input area" rows="3" maxlength="200" />
        </label>
      </template>

      <!-- ===================== 过敏史 ===================== -->
      <template v-else-if="kind === 'allergy'">
        <label class="mw-field">
          <span>过敏原（必填）</span>
          <input
            v-model="allergyDraft.allergen"
            class="mw-input"
            type="text"
            maxlength="50"
            placeholder="比如：鸡肉、青霉素"
          />
        </label>
        <div class="mw-field">
          <span>严重度</span>
          <div class="mw-chips" role="group" aria-label="过敏严重度">
            <button
              v-for="s in SEVERITY_LEVELS"
              :key="s.value"
              class="mw-chip"
              :class="{ 'mw-chip--on': allergyDraft.severity === s.value }"
              type="button"
              @click="allergyDraft.severity = s.value"
            >
              {{ s.label }}
            </button>
          </div>
        </div>
        <label class="mw-field">
          <span>过敏反应</span>
          <textarea v-model="allergyDraft.reaction" class="mw-input area" rows="2" maxlength="200" />
        </label>
        <label class="mw-field">
          <span>备注</span>
          <textarea v-model="allergyDraft.note" class="mw-input area" rows="3" maxlength="200" />
        </label>
        <p class="mw-caption tip">过敏史没有时间字段，所以这里不填发生时间。</p>
      </template>

      <!-- ===================== 症状 ===================== -->
      <template v-else-if="kind === 'symptom'">
        <div class="mw-field">
          <span>症状</span>
          <SymptomPicker
            v-model:symptom-id="symptomDraft.symptomId"
            v-model:symptom-name="symptomNameModel"
            :species="species"
          />
        </div>
        <div class="mw-field">
          <span>严重度</span>
          <div class="mw-chips" role="group" aria-label="症状严重度">
            <button
              v-for="s in SEVERITY_LEVELS"
              :key="s.value"
              class="mw-chip"
              :class="{ 'mw-chip--on': symptomDraft.severity === s.value }"
              type="button"
              @click="symptomDraft.severity = s.value"
            >
              {{ s.label }}
            </button>
          </div>
        </div>
        <label class="mw-field">
          <span>出现时间（必填）</span>
          <input
            :value="toDatetimeLocalValue(symptomDraft.occurredAt)"
            class="mw-input"
            type="datetime-local"
            @input="symptomDraft.occurredAt = fromDatetimeLocalValue(($event.target as HTMLInputElement).value)"
          />
        </label>
        <label class="mw-field">
          <span>具体表现</span>
          <textarea
            v-model="symptomDraft.description"
            class="mw-input area"
            rows="3"
            maxlength="200"
            placeholder="比如：吃完饭后吐了一次黄水，精神还行"
          />
        </label>
        <div class="mw-field">
          <span>照片</span>
          <AttachmentUploader v-model="symptomDraft.images" />
        </div>
        <label class="mw-field">
          <span>备注</span>
          <textarea v-model="symptomDraft.remark" class="mw-input area" rows="2" maxlength="200" />
        </label>
      </template>

      <!-- ===================== 体重 ===================== -->
      <template v-else-if="kind === 'weight'">
        <label class="mw-field">
          <span>体重（kg，必填）</span>
          <input
            v-model="weightKgModel"
            class="mw-input"
            type="number"
            min="0.05"
            max="200"
            step="0.01"
            inputmode="decimal"
            placeholder="比如：4.25"
          />
          <span class="mw-caption">后端只接受 0.05 ~ 200 kg，超出会被拒。</span>
        </label>
        <label class="mw-field">
          <span>测量时间（必填）</span>
          <input
            :value="toDatetimeLocalValue(weightDraft.measuredAt)"
            class="mw-input"
            type="datetime-local"
            @input="weightDraft.measuredAt = fromDatetimeLocalValue(($event.target as HTMLInputElement).value)"
          />
        </label>
        <label class="mw-field">
          <span>备注</span>
          <textarea v-model="weightDraft.note" class="mw-input area" rows="2" maxlength="200" />
        </label>
      </template>

      <p v-else class="mw-alert mw-alert--danger">不支持的记录类型：{{ kind }}</p>

      <div class="actions">
        <button class="mw-btn mw-btn--primary save" type="submit" :disabled="saving">
          <PhFloppyDisk :size="16" aria-hidden="true" />
          {{ saving ? '正在保存' : isEdit ? '保存修改' : '保存' }}
        </button>
        <RouterLink class="mw-btn mw-btn--ghost" :to="backToList()">取消</RouterLink>
      </div>
    </form>
  </div>
</template>

<style scoped>
.wrap {
  margin-top: var(--mw-space-4);
}
.head {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-2);
  margin-bottom: var(--mw-space-4);
}
.form {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-4);
  max-width: 720px;
  padding: var(--mw-space-6);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
  background: var(--mw-surface);
}
.err {
  margin: 0;
}
.pair {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--mw-space-4);
}
.area {
  resize: vertical;
}
.tip {
  margin-top: calc(-1 * var(--mw-space-2));
}
.sk {
  display: block;
  height: 56px;
}
.actions {
  display: flex;
  align-items: center;
  gap: var(--mw-space-3);
  padding-top: var(--mw-space-2);
  border-top: 1px solid var(--mw-line);
}
.save {
  min-height: 40px;
}
</style>