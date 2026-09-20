/**
 * 健康记录接口（Web）
 *
 * 唯一契约来源：`.dsh-tmp/healthy-contract.md` —— **以文末「实测更新（2026-09-20，有真实数据后）」一节为准**
 * （真实种子数据：`probe114904` / petId=43「布丁」，preventive 1,2 / medical 1 / medication 1,2 /
 * allergy 1,2 / symptom 1,2 / **weight 111,112**）。字段名与行为一律照实测，不按 REST 惯例猜。
 *
 * 六条最容易踩的（每条都有实测证据，别再"统一处理"）：
 * 1) **列表全是裸数组 `data:[...]`**，没有任何分页信封（`pageNum/pageSize/total` 与
 *    `records/nextCursor` 都没有）；**写接口返回裸数字 id**（`data: 111`，不是 `{id:…}`）。
 * 2) ⚠️ **PUT 是全量覆盖**：service 里用 `set(...)` 逐字段写，**不传的字段会被置 null**
 *    （实测：preventive 1 的 `batchNo` 因为 PUT 没传就变成了 null）。
 *    → **禁止用 PUT 做局部更新**。本模块的写法是「先把记录读成 draft，再改 draft，最后整体 PUT」，
 *    每个 `xxxBody(draft)` 转换器都**把全部字段写出来**（含 `null`），这样"清空某个字段"才是真的清空。
 * 3) ⚠️ **附件入参是逗号串（String）、出参是数组（List）**；无附件时出参是 **`[]` 不是 `null`**，
 *    入参侧空值统一发 `null`（发 `''` 会在库里留空串）。上传接口回 `{url, objectKey}`，
 *    但**记录层没有 objectKey 字段**（落库只有绝对 URL），所以 objectKey 只用于当次提示，不入库。
 * 4) ⚠️ **时间是 `yyyy-MM-dd HH:mm:ss`（GMT+8，非 ISO）**，例外：`weight/curve.date` 与宠物 `birth`
 *    是 `yyyy-MM-dd`。`new Date("2026-09-20 08:00:00")` 在 iOS Safari / 部分安卓 WebView 上是
 *    `Invalid Date` → 这里一律走 `parseHealthTime()` 手工解析，不依赖引擎的宽松解析。
 * 5) ⚠️ **筛选参数三种行为，不能统一处理**：`preventive?type` 非法 → 静默忽略（回全部）；
 *    `medication?status` 越界 → **按值过滤**回 `[]`；`timeline?types` 非法 → **`A000001` 报错**。
 *    所以 `timeline` 的 types 只能由本文件的 `TIMELINE_TYPE_OPTIONS` 产生，绝不透传用户输入。
 * 6) ⚠️ **逾期项拿不到**：`overview.nextPreventive` 的后端条件是 `.ge(nextDueAt, now)`，
 *    **已逾期的疫苗/驱虫被完全排除**（实测：列表里有 2 号 `nextDueDays=-10`，overview 只回 1 号）。
 *    → 逾期只能靠 `listPreventive()` 全量 + `overdueLabel()` 自己算。
 *
 * 另外两条：
 * - 传 `symptomId` 会**覆盖** `symptomName`（知识库标题胜出，POST/PUT 都成立）；非法 `symptomId` → `B000409`。
 *   所以前端"选选项"和"自由填写"是**两条互斥的路**：选了选项就别指望自定义名生效。
 * - **绝对不要调 `/health/internal/**`**：经网关一律 403，而请求层遇到 403 会**清登录态**（会把用户踢下线）。
 *
 * ⚠️ 两个付费墙接口（`POST /{pid}/ai-analysis`、`GET /{pid}/export`）**成功返回体未观测**：
 * 本账号档位不够（`healthAiAnalysis:0` / `healthExport:0`），实测分别回 `B000411` / `B000404`。
 * 它们的入口照做，但**付费墙要当正常状态处理**（明确的升级提示，不当报错、不重试）。
 */
import { ApiError, api } from './request'

/** 健康路径前缀（服务名由请求层补：`miaowang-healthy`） */
const BASE = '/health'

/* ============================ 数据模型（字段逐字照实测） ============================ */

/** 六类记录的英文枚举（`overview.recordCounts` 的 key、`timeline.recordType` 都用这一套） */
export const HEALTH_RECORD_TYPES = [
  'preventive',
  'medical',
  'medication',
  'allergy',
  'symptom',
  'weight',
] as const
export type HealthRecordType = (typeof HEALTH_RECORD_TYPES)[number]

/** 六类记录的中文名（后端 `HealthRecordTypeEnum` 的 desc：疫苗/驱虫、就诊、用药、过敏史、症状日记、体重） */
export const RECORD_TYPE_TEXT: Record<HealthRecordType, string> = {
  preventive: '疫苗驱虫',
  medical: '就诊',
  medication: '用药',
  allergy: '过敏史',
  symptom: '症状日记',
  weight: '体重',
}

/** 疫苗/驱虫类型（后端 `PreventiveTypeEnum`：VACCINE / DEWORM_IN / DEWORM_OUT） */
export const PREVENTIVE_TYPES = [
  { value: 'VACCINE', label: '疫苗' },
  { value: 'DEWORM_IN', label: '体内驱虫' },
  { value: 'DEWORM_OUT', label: '体外驱虫' },
] as const
export type PreventiveType = (typeof PREVENTIVE_TYPES)[number]['value']

/** 用药状态（0 用药中 / 1 已结束 / 2 已停用；不传时后端按 `endAt` 推断） */
export const MEDICATION_STATUS = [
  { value: 0, label: '用药中' },
  { value: 1, label: '已结束' },
  { value: 2, label: '已停用' },
] as const

/** 症状严重度（1/2/3 ↔ 轻度/中度/重度，`severityDesc` 后端已算好） */
export const SEVERITY_LEVELS = [
  { value: 1, label: '轻度' },
  { value: 2, label: '中度' },
  { value: 3, label: '重度' },
] as const

/** 体重曲线可选回溯窗口（后端默认 6、钳制到 `[1,24]`；实测 `999≡24`、`0/-5≡1`） */
export const WEIGHT_CURVE_MONTHS = [1, 3, 6, 12, 24] as const

/** 时间线条数（后端默认 50、下界钳到 1，上界 200 源码写明但**实测无法证明**：记录数远小于 200） */
export const TIMELINE_LIMITS = [20, 50, 100, 200] as const

/** 症状选项的 species：1=猫 2=狗（其它值合法但回空数组） */
export const SYMPTOM_SPECIES = { cat: 1, dog: 2 } as const

export interface SymptomOption {
  symptomId: number
  name: string
  /** 知识库正文（病因），可能很长 */
  description: string | null
  /** 知识库 remark（建议） */
  remark: string | null
  species: number
}

export interface PreventiveRecord {
  id: number
  pid: number
  type: string
  /** 后端算好的中文类型名（疫苗/体内驱虫/体外驱虫） */
  typeDesc: string
  itemName: string
  /** yyyy-MM-dd HH:mm:ss */
  happenedAt: string
  /** yyyy-MM-dd HH:mm:ss；没填下次到期时间是 null */
  nextDueAt: string | null
  /**
   * 距下次到期的天数：**整数截断、可为负**（负数=已逾期）、`nextDueAt` 为空时是 `null`。
   * `0` 表示"不到 1 天"（-0.5 天也会截成 0），所以展示口径用「今天到期」而不是"还没到期"。
   */
  nextDueDays: number | null
  hospital: string | null
  batchNo: string | null
  remark: string | null
}

export interface MedicalRecord {
  id: number
  pid: number
  visitAt: string
  hospital: string | null
  doctor: string | null
  diagnosis: string | null
  treatment: string | null
  cost: number | null
  /** 出参是数组（空为 `[]`）；入参要转成逗号串 */
  attachments: string[]
  remark: string | null
}

export interface MedicationRecord {
  id: number
  pid: number
  /** 关联的就诊记录 id（可为 null） */
  medicalId: number | null
  medicineName: string
  dosage: string | null
  frequency: string | null
  startAt: string
  endAt: string | null
  /** 0 用药中 / 1 已结束 / 2 已停用 */
  status: number
  statusDesc: string
  remark: string | null
}

export interface AllergyRecord {
  id: number
  pid: number
  allergen: string
  /** 1 轻度 / 2 中度 / 3 重度 */
  severity: number
  severityDesc: string
  reaction: string | null
  note: string | null
}

export interface SymptomLog {
  id: number
  pid: number
  /** 选了知识库条目时非空；自由填写时是 null */
  symptomId: number | null
  symptomName: string | null
  description: string | null
  severity: number
  severityDesc: string
  occurredAt: string
  /** 出参是数组（空为 `[]`）；入参要转成逗号串 */
  images: string[]
  remark: string | null
}

export interface WeightRecord {
  id: number
  pid: number
  weightKg: number
  measuredAt: string
  note: string | null
}

/** 体重曲线点：`date` 是 **`yyyy-MM-dd`**（没有时分秒），`count` 是当天测量次数 */
export interface WeightCurvePoint {
  date: string
  weightKg: number
  count: number
}

export interface HealthTimelineItem {
  recordType: string
  recordTypeDesc: string
  /** 各类表自己的 id → **会重复**，列表 key 必须用 `recordType + recordId` */
  recordId: number
  title: string
  summary: string | null
  happenedAt: string
  /** 只有 allergy 与 symptom 有值，其余四类是 null */
  severity: number | null
}

export interface HealthOverview {
  pid: number
  petName: string | null
  species: string | null
  totalRecords: number
  /** 六个 key **永远都在**（某类为 0 也是 `0`） */
  recordCounts: Record<string, number>
  /** 档位上限；`-1` = 不限 */
  recordLimit: number
  /** 已记满的类型（`-1` 不限时**恒为空数组**） */
  limitReachedTypes: string[]
  /** **只含未逾期的那一条**（逾期项拿不到，见文件头第 6 条）；没有时是 null */
  nextPreventive: PreventiveRecord | null
  latestWeight: WeightRecord | null
  /** 最新 − 30 天前基线；**没有 30 天前的记录时是 null** */
  weightChangeKg: number | null
  /** 最多 10 条，severity 倒序 */
  allergies: AllergyRecord[]
  /** 最多 10 条，只含 status=0 */
  activeMedications: MedicationRecord[]
  /** 与 `/vip/benefit` 的 `healthExport` 对齐（0/false 表示当前档位不支持） */
  exportAllowed: boolean
  /** 与 `/vip/benefit` 的 `healthAiAnalysis` 对齐 */
  aiAnalysisAllowed: boolean
}

/** 上传返回：`objectKey` 在记录层会被丢弃（记录 DTO 没有能装它的字段） */
export interface HealthUploadResult {
  url: string
  objectKey: string
}

/**
 * 导出返回体（⚠️ **成功形状未观测** —— 需 AI_249(24.9) 及以上档位，本账号实测 `B000404`）。
 * 字段按后端 `HealthExportRespDTO` 列，仅用于类型占位，不要当成已验收的契约。
 */
export interface HealthExport {
  pid: number
  petName: string | null
  species: string | null
  exportedAt: string
  recordLimit: number
  recordCounts: Record<string, number>
  preventives: PreventiveRecord[]
  medicals: MedicalRecord[]
  medications: MedicationRecord[]
  allergies: AllergyRecord[]
  symptoms: SymptomLog[]
  weights: WeightRecord[]
}

/**
 * AI 症状解读返回体（⚠️ **成功形状未观测** —— 需 AI_349(34.9) 及以上档位，本账号实测 `B000411`，
 * 且**付费闸门排在"有无症状记录"之前**，所以本账号连 `B000412` 都不可达）。
 */
export interface HealthAiAnalysis {
  pid: number
  petName: string | null
  symptomCount: number
  analysis: string
  hasKnowledge: boolean
  sources: { id: number; title: string; categoryCode: string }[]
  /** 免责声明，**必须展示**（契约要求） */
  disclaimer: string
  generatedAt: string
}

/* ============================ 写接口的请求体（DTO 逐字） ============================ */

export interface PreventiveSaveBody {
  type: string
  itemName: string
  happenedAt: string
  nextDueAt: string | null
  hospital: string | null
  batchNo: string | null
  remark: string | null
}

export interface MedicalSaveBody {
  visitAt: string
  hospital: string | null
  doctor: string | null
  /** 必填（空 → `B000407`） */
  diagnosis: string
  treatment: string | null
  cost: number | null
  /** **逗号串**（不是数组）；空附件发 null */
  attachments: string | null
  remark: string | null
}

export interface MedicationSaveBody {
  medicalId: number | null
  medicineName: string
  dosage: string | null
  frequency: string | null
  startAt: string
  endAt: string | null
  /** 显式传状态；不传时后端按 `endAt` 推断（有结束时间就是"已结束"） */
  status: number
  remark: string | null
}

export interface AllergySaveBody {
  allergen: string
  severity: number
  reaction: string | null
  note: string | null
}

export interface SymptomSaveBody {
  /** 传了就覆盖 `symptomName`；非法值回 `B000409` */
  symptomId: number | null
  /** 只在**不传 symptomId**（自由填写）时有效 */
  symptomName: string | null
  description: string | null
  severity: number
  occurredAt: string
  /** **逗号串**（不是数组）；空图片发 null */
  images: string | null
  remark: string | null
}

export interface WeightSaveBody {
  /** 后端要求 `0.05 ~ 200`，越界回 `A000001` + 自定义消息 */
  weightKg: number
  measuredAt: string
  note: string | null
}

/**
 * 表单草稿 = 记录去掉 `id`/`pid` 与**后端算好的展示字段**（`typeDesc`/`nextDueDays`/`statusDesc`/
 * `severityDesc` 都不可写，留着只会让人以为能改）。
 * 编辑页的做法固定是：**拉列表 → 按 id 找到记录 → 整条拷贝成 draft → 改 draft → 整体 PUT**
 * （没有单条 GET 接口，见"后端缺口"；也绝对不能靠局部 PUT 改字段）。
 */
export type PreventiveDraft = Omit<PreventiveRecord, 'id' | 'pid' | 'typeDesc' | 'nextDueDays'>
export type MedicalDraft = Omit<MedicalRecord, 'id' | 'pid'>
export type MedicationDraft = Omit<MedicationRecord, 'id' | 'pid' | 'statusDesc'>
export type AllergyDraft = Omit<AllergyRecord, 'id' | 'pid' | 'severityDesc'>
export type SymptomDraft = Omit<SymptomLog, 'id' | 'pid' | 'severityDesc'>
export type WeightDraft = Omit<WeightRecord, 'id' | 'pid'>

export type HealthDraft =
  | PreventiveDraft
  | MedicalDraft
  | MedicationDraft
  | AllergyDraft
  | SymptomDraft
  | WeightDraft

/* ============================ 读接口 ============================ */

/** 概览（对象，不是数组）；宠物不存在或不属于你 → `B000400` */
export function getOverview(pid: number) {
  return api.get<HealthOverview>('healthy', `${BASE}/${pid}/overview`)
}

/**
 * 疫苗/驱虫列表（裸数组，`happenedAt` 倒序）。
 *
 * `type` 非法值会被**静默忽略**（回全部）——所以只允许传 `PREVENTIVE_TYPES` 里的值。
 * 首页/概览的"逾期提醒"必须用这个接口的全量结果自己算（`overview.nextPreventive` 排除逾期）。
 */
export function listPreventive(pid: number, type?: string) {
  return api.get<PreventiveRecord[]>('healthy', `${BASE}/${pid}/preventive`, {
    query: { type: type || undefined },
  })
}

/** 就诊列表（裸数组，`visitAt` 倒序） */
export function listMedical(pid: number) {
  return api.get<MedicalRecord[]>('healthy', `${BASE}/${pid}/medical`)
}

/**
 * 用药列表（裸数组，`startAt` 倒序）。
 * ⚠️ `status` 越界（如 99）**不报错也不忽略，而是按值过滤回 `[]`** —— 界面上"筛出 0 条"是正常结果。
 */
export function listMedication(pid: number, status?: number) {
  return api.get<MedicationRecord[]>('healthy', `${BASE}/${pid}/medication`, {
    query: { status: status ?? undefined },
  })
}

/** 过敏史列表（裸数组，`severity` 倒序，重度在前）；**过敏表没有"发生时间"列** */
export function listAllergy(pid: number) {
  return api.get<AllergyRecord[]>('healthy', `${BASE}/${pid}/allergy`)
}

/** 症状日记列表（裸数组，`occurredAt` 倒序） */
export function listSymptomLogs(pid: number) {
  return api.get<SymptomLog[]>('healthy', `${BASE}/${pid}/symptom`)
}

/** 体重记录列表（裸数组，`measuredAt` 倒序）；**id 不一定从 1 开始**（真实数据是 111/112） */
export function listWeight(pid: number) {
  return api.get<WeightRecord[]>('healthy', `${BASE}/${pid}/weight`)
}

/** 体重曲线（按天聚合，每天取最后一次；`date` 升序、`date` 是 `yyyy-MM-dd`） */
export function getWeightCurve(pid: number, months?: number) {
  return api.get<WeightCurvePoint[]>('healthy', `${BASE}/${pid}/weight/curve`, {
    query: { months: months ?? undefined },
  })
}

/**
 * 时间线（六类合并、时间倒序，裸数组）。
 *
 * ⚠️ `types` 只能是合法枚举：非法值走 `ClientException` → **`A000001` 报错**（与 `preventive?type`
 * 的"静默忽略"相反）。所以这里用 `timelineTypesParam()` 过滤，绝不透传用户输入。
 */
export function getTimeline(pid: number, params: { types?: string[]; limit?: number } = {}) {
  const types = timelineTypesParam(params.types)
  return api.get<HealthTimelineItem[]>('healthy', `${BASE}/${pid}/timeline`, {
    query: { types: types || undefined, limit: params.limit ?? undefined },
  })
}

/**
 * 症状选项（知识库「健康自查」条目）。
 * ⚠️ 技能表里写"可免登录"，**实测经网关必登录**（`/health/**` 整个前缀都不在白名单里，匿名回 `state:-1`）。
 * `species` 传 99 这类合法但无数据的值是 `[]`（不报错）；传 `abc` → `B000001`。
 */
export function listSymptomOptions(species?: number) {
  return api.get<SymptomOption[]>('healthy', `${BASE}/symptom/options`, {
    query: { species: species ?? undefined },
  })
}

/* ============================ 写接口 ============================ */

/**
 * 上传健康档案图片（化验单/影像/症状照片）。
 * `multipart/form-data`，字段名固定 **`file`**（后端 `@RequestPart("file")`）；单文件、≤10MB。
 * 返回 `{url, objectKey}`，但**入库只存 URL**（记录层没有 objectKey 字段）。
 */
export function uploadAttachment(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return api.post<HealthUploadResult>('healthy', `${BASE}/file`, { formData })
}

/* ----- 疫苗/驱虫 ----- */

export function createPreventive(pid: number, draft: PreventiveDraft) {
  return api.post<number>('healthy', `${BASE}/${pid}/preventive`, { body: preventiveBody(draft) })
}

export function updatePreventive(id: number, draft: PreventiveDraft) {
  return api.put<null>('healthy', `${BASE}/preventive/${id}`, { body: preventiveBody(draft) })
}

export function deletePreventive(id: number) {
  return api.del<null>('healthy', `${BASE}/preventive/${id}`)
}

/* ----- 就诊 ----- */

export function createMedical(pid: number, draft: MedicalDraft) {
  return api.post<number>('healthy', `${BASE}/${pid}/medical`, { body: medicalBody(draft) })
}

export function updateMedical(id: number, draft: MedicalDraft) {
  return api.put<null>('healthy', `${BASE}/medical/${id}`, { body: medicalBody(draft) })
}

export function deleteMedical(id: number) {
  return api.del<null>('healthy', `${BASE}/medical/${id}`)
}

/* ----- 用药 ----- */

export function createMedication(pid: number, draft: MedicationDraft) {
  return api.post<number>('healthy', `${BASE}/${pid}/medication`, { body: medicationBody(draft) })
}

export function updateMedication(id: number, draft: MedicationDraft) {
  return api.put<null>('healthy', `${BASE}/medication/${id}`, { body: medicationBody(draft) })
}

export function deleteMedication(id: number) {
  return api.del<null>('healthy', `${BASE}/medication/${id}`)
}

/* ----- 过敏史 ----- */

export function createAllergy(pid: number, draft: AllergyDraft) {
  return api.post<number>('healthy', `${BASE}/${pid}/allergy`, { body: allergyBody(draft) })
}

export function updateAllergy(id: number, draft: AllergyDraft) {
  return api.put<null>('healthy', `${BASE}/allergy/${id}`, { body: allergyBody(draft) })
}

export function deleteAllergy(id: number) {
  return api.del<null>('healthy', `${BASE}/allergy/${id}`)
}

/* ----- 症状日记 ----- */

export function createSymptom(pid: number, draft: SymptomDraft) {
  return api.post<number>('healthy', `${BASE}/${pid}/symptom`, { body: symptomBody(draft) })
}

export function updateSymptom(id: number, draft: SymptomDraft) {
  return api.put<null>('healthy', `${BASE}/symptom/${id}`, { body: symptomBody(draft) })
}

export function deleteSymptom(id: number) {
  return api.del<null>('healthy', `${BASE}/symptom/${id}`)
}

/* ----- 体重 ----- */

export function createWeight(pid: number, draft: WeightDraft) {
  return api.post<number>('healthy', `${BASE}/${pid}/weight`, { body: weightBody(draft) })
}

export function updateWeight(id: number, draft: WeightDraft) {
  return api.put<null>('healthy', `${BASE}/weight/${id}`, { body: weightBody(draft) })
}

export function deleteWeight(id: number) {
  return api.del<null>('healthy', `${BASE}/weight/${id}`)
}

/* ----- 付费墙功能（成功形状未观测，见文件头） ----- */

/**
 * AI 症状解读（**POST、无 body**）。
 * 闸门顺序：归属（`B000400`）→ 档位 `healthAiAnalysis`（`B000411`）→ 有无症状记录（`B000412`）→ 调 ai-knowledge。
 * 每次都会真的调大模型，**耗时十几秒**，所以调用方必须做进行态且**不要重试**（重复点就是重复烧 token）。
 */
export function runAiAnalysis(pid: number) {
  return api.post<HealthAiAnalysis>('healthy', `${BASE}/${pid}/ai-analysis`)
}

/**
 * 导出完整健康档案（**结构化 JSON，不是文件**）。
 * 档位不够 → `B000404`；前端拿到对象后自己序列化成 `.json` 下载。
 * ⚠️ 成功形状未观测（本账号 `healthExport:0`）。
 */
export function exportHealth(pid: number) {
  return api.get<HealthExport>('healthy', `${BASE}/${pid}/export`)
}

/* ============================ 展示层小工具 ============================ */

const pad2 = (n: number) => String(n).padStart(2, '0')

/** `yyyy-MM-dd` 或 `yyyy-MM-dd HH:mm:ss`（也容忍 ISO 的 `T` 与毫秒） */
const HEALTH_TIME_RE = /^(\d{4})-(\d{1,2})-(\d{1,2})(?:[ T](\d{1,2}):(\d{2})(?::(\d{2}))?)?/

/**
 * 解析健康模块的时间字符串。
 *
 * **手工解析，不用 `new Date(str)`**：`"2026-09-20 08:00:00"` 这种带空格的形式在
 * iOS Safari / 部分安卓 WebView 上会得到 `Invalid Date`（契约实测提醒）。
 * 字符串本身已经是 GMT+8 本地时间，所以按本地时间构造，不做时区换算。
 * 解析不了返回 `null`（调用方一律先判空，别直接比大小）。
 */
export function parseHealthTime(value: string | null | undefined): Date | null {
  if (!value) return null
  const m = HEALTH_TIME_RE.exec(String(value).trim())
  if (m) {
    const d = new Date(
      Number(m[1]),
      Number(m[2]) - 1,
      Number(m[3]),
      Number(m[4] ?? 0),
      Number(m[5] ?? 0),
      Number(m[6] ?? 0),
    )
    return Number.isNaN(d.getTime()) ? null : d
  }
  // 兜底：ISO 带时区的串（其它模块会给这种，健康模块当前不给）
  const fallback = new Date(value)
  return Number.isNaN(fallback.getTime()) ? null : fallback
}

/** `2026-09-20 08:00:00` → `2026-09-20 08:00`；解析不了就原样返回（不要让界面变空白） */
export function healthTimeText(value: string | null | undefined): string {
  const d = parseHealthTime(value)
  if (!d) return value ? String(value) : ''
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}`
}

/** 只要日期部分：`2026-09-20 08:00:00` → `2026-09-20`（曲线点的 `date` 本来就是这种） */
export function healthDayText(value: string | null | undefined): string {
  const d = parseHealthTime(value)
  if (!d) return value ? String(value) : ''
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

/** 图表轴标签用的短日期：`2026-09-20` → `9/20` */
export function healthMonthDayText(value: string | null | undefined): string {
  const d = parseHealthTime(value)
  if (!d) return ''
  return `${d.getMonth() + 1}/${d.getDate()}`
}

/**
 * `yyyy-MM-dd HH:mm:ss` → `2026-09-20T08:00`（`datetime-local` 输入框要的形式）。
 * 表单的日期时间输入统一用它，反向用 `fromDatetimeLocalValue()`。
 */
export function toDatetimeLocalValue(value: string | null | undefined): string {
  const d = parseHealthTime(value)
  if (!d) return ''
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}T${pad2(d.getHours())}:${pad2(d.getMinutes())}`
}

/**
 * `datetime-local` 的值（`2026-09-20T08:00`）→ 后端要的 `yyyy-MM-dd HH:mm:ss`。
 * 秒补 `00`（输入框只到分钟）；空值返回 `''`（调用方自己判必填）。
 */
export function fromDatetimeLocalValue(value: string | null | undefined): string {
  const v = (value ?? '').trim()
  if (!v) return ''
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?/.exec(v)
  if (!m) return ''
  return `${m[1]}-${m[2]}-${m[3]} ${m[4]}:${m[5]}:${m[6] ?? '00'}`
}

/** `yyyy-MM-dd` 输入框 → 后端 `yyyy-MM-dd HH:mm:ss`（日期类字段统一按当天 00:00:00 存） */
export function dateOnlyToHealthTime(value: string | null | undefined): string {
  const v = (value ?? '').trim()
  if (!/^\d{4}-\d{2}-\d{2}$/.test(v)) return ''
  return `${v} 00:00:00`
}

/**
 * 数组 → 逗号串（**写接口入参**）。空数组 / 全空项 → `null`（不是 `''`，
 * 免得库里留下空串；后端 `set()` 会把 null 写成 null）。
 */
export function toCommaString(
  value: string | string[] | null | undefined,
): string | null {
  const list = Array.isArray(value) ? value : value ? String(value).split(',') : []
  const items = list.map((v) => String(v ?? '').trim()).filter(Boolean)
  return items.length ? items.join(',') : null
}

/**
 * 逗号串 → 数组（**读接口出参已经是数组**，这里同时兼容两种形态，出参为 `[]` 而不是 null）。
 * 附件/图片在界面上是"一串可点的缩略图"，统一用这个收敛成数组。
 */
export function fromCommaString(value: string | string[] | null | undefined): string[] {
  if (Array.isArray(value)) return value.map((v) => String(v ?? '').trim()).filter(Boolean)
  if (!value) return []
  return String(value)
    .split(',')
    .map((v) => v.trim())
    .filter(Boolean)
}

/* ---------- 存量绝对 URL 的展示兜底（挂账 #67 的窄口子） ---------- */

/**
 * 仅这两个主机名被认定"换了环境就必然不可达"。
 * ⚠️ **不要往里加内网 IP / 老域名** —— 那些是"配置写错了"，归一化只会把真问题掩盖成"看起来能跑"。
 */
const UNREACHABLE_ASSET_HOSTS = ['127.0.0.1', 'localhost']

/** 当前页面所在主机（Web 走 Vite 代理 `/api` → 网关，对象存储与页面同主机、端口不同） */
function currentWebHost(): string {
  if (typeof window !== 'undefined' && window.location) return window.location.hostname || ''
  return ''
}

/** 当前 API 主机（展示兜底用；`''` = 拿不到 → 宁可不改也不猜） */
export function currentDisplayHost(): string {
  return currentWebHost()
}

/** `http://192.168.100.45:7000/api/...` / `192.168.100.45:7000` → `192.168.100.45` */
function apiHostName(apiHost: string | null | undefined): string {
  let s = (apiHost ?? '').trim()
  if (!s) return ''
  s = s.replace(/^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//, '')
  s = (s.split('/')[0] ?? '').split('?')[0] ?? ''
  if (!s || s.startsWith('[')) return '' // IPv6 字面量不在本次范围，不猜
  const colon = s.indexOf(':')
  return colon >= 0 ? s.slice(0, colon) : s
}

/**
 * 存量附件绝对 URL → 当前可达 URL（**纯函数**，主机显式传入以便离线断言）。
 *
 * 背景（挂账 #67）：附件地址是**上传时就把绝对 URL 写进库**的，而下发 URL 按"本次请求的 Host"
 * 推导 → 用 `127.0.0.1` 上传过的历史记录里永远躺着 `http://127.0.0.1:9000/...`，
 * 换个端/换个网段打开就是裂图。这里做一处**很窄**的展示期归一化。
 *
 * ⚠️ **只重写 `127.0.0.1` 与 `localhost`**：协议、端口（对象存储的 9000，不是网关的 7000）、
 *    path、query 全部原样保留；其它 host 一律不动。
 * ⚠️ 这是兜底不是正解：正解是后端**只存 objectKey、下发时再拼 URL**；
 *    后端改成那样之后，本函数与 `attachmentList` 里的归一化应当**一并删除**。
 */
export function normalizeStoredAssetUrl(
  url: string | null | undefined,
  apiHost?: string | null,
): string {
  const raw = (url ?? '').trim()
  if (!raw) return ''
  const m = /^(https?):\/\/([^/?#]+)([\s\S]*)$/i.exec(raw)
  if (!m) return raw // 相对路径 / 非 http(s)：不动
  const scheme = m[1] ?? ''
  const hostPort = (m[2] ?? '').split('@').pop() ?? ''
  const rest = m[3] ?? ''
  if (hostPort.startsWith('[')) return raw // IPv6 字面量：不动
  const colon = hostPort.indexOf(':')
  const hostname = colon >= 0 ? hostPort.slice(0, colon) : hostPort
  const port = colon >= 0 ? hostPort.slice(colon) : ''
  if (!UNREACHABLE_ASSET_HOSTS.includes(hostname.toLowerCase())) return raw
  const target = apiHostName(apiHost === undefined ? currentDisplayHost() : apiHost)
  if (!target) return raw
  return `${scheme}://${target}${port}${rest}`
}

/** 附件（就诊 `attachments` / 症状 `images`）出参数的兜底：空值可能是 `[]`，也可能是 null */
export function attachmentList(
  value: string[] | null | undefined,
  apiHost?: string | null,
): string[] {
  const list = Array.isArray(value) ? value.filter(Boolean) : []
  // 展示期顺手把存量 127.0.0.1 / localhost 换成本机可达主机（见 normalizeStoredAssetUrl）。
  // 传 `''` 即关闭归一化 —— 编辑页"读全量"那一步就是这么用的（保持库里的原值）。
  const host = apiHost === undefined ? currentDisplayHost() : apiHost
  return list.map((u) => normalizeStoredAssetUrl(String(u), host))
}

export type OverdueTone = 'overdue' | 'today' | 'soon' | 'later' | 'none'

export interface OverdueInfo {
  tone: OverdueTone
  text: string
  /** 原始天数（可为负），没填下次时间时是 `null` */
  days: number | null
}

/** 分档阈值：30 天内算"快到了"（给琥珀色），超过就退成中性 */
const OVERDUE_SOON_DAYS = 30

/**
 * 逾期分档（**前端自算，后端不给 `overdue` 布尔**）。
 *
 * - `nextDueDays` 为 `null`（没填下次到期时间）→ `none`
 * - `< 0` → 已逾期（实测 -10 → 「已逾期 10 天」）
 * - `=== 0` → 今天到期（注意整数截断：-0.5 天也会得到 0，所以不能说"还没到期"）
 * - `> 0` → 还有 N 天（≤30 天给 soon，更远给 later）
 */
export function overdueLabel(nextDueDays: number | null | undefined): OverdueInfo {
  if (nextDueDays === null || nextDueDays === undefined || !Number.isFinite(Number(nextDueDays))) {
    return { tone: 'none', text: '没有下次时间', days: null }
  }
  const days = Math.trunc(Number(nextDueDays))
  if (days < 0) return { tone: 'overdue', text: `已逾期 ${-days} 天`, days }
  if (days === 0) return { tone: 'today', text: '今天到期', days }
  return { tone: days <= OVERDUE_SOON_DAYS ? 'soon' : 'later', text: `还有 ${days} 天`, days }
}

/** 逾期项的排序键：没填下次时间的排最后（`Infinity`），否则按天数升序（负数最紧急） */
export function overdueRank(nextDueDays: number | null | undefined): number {
  if (nextDueDays === null || nextDueDays === undefined || !Number.isFinite(Number(nextDueDays))) {
    return Number.POSITIVE_INFINITY
  }
  return Math.trunc(Number(nextDueDays))
}

/** 按"下次到期"从最紧急到最不紧急排序（已逾期的负数排最前，没填的排最后） */
export function sortByDue<T extends { nextDueDays: number | null }>(list: T[]): T[] {
  return [...list].sort((a, b) => overdueRank(a.nextDueDays) - overdueRank(b.nextDueDays))
}

/**
 * 最紧急的一条预防记录（**包含已逾期**）。
 * 这是"今日照护 / 逾期提醒"的正解：`overview.nextPreventive` 排除了逾期项，不能拿它当最紧急。
 */
export function mostUrgentPreventive(list: PreventiveRecord[]): PreventiveRecord | null {
  const withDue = list.filter((r) => overdueRank(r.nextDueDays) !== Number.POSITIVE_INFINITY)
  if (!withDue.length) return null
  return sortByDue(withDue)[0] ?? null
}

/** 已逾期的预防记录（`nextDueDays < 0`），最紧急在前 */
export function overduePreventives(list: PreventiveRecord[]): PreventiveRecord[] {
  return sortByDue(list).filter((r) => (r.nextDueDays ?? 0) < 0)
}

/** 体重文案：`4.25 kg`（DECIMAL 两位；没法解析的给「未记录」） */
export function weightText(kg: number | null | undefined): string {
  const v = Number(kg)
  if (kg === null || kg === undefined || !Number.isFinite(v)) return '未记录'
  return `${v.toFixed(2)} kg`
}

export interface WeightChangeInfo {
  tone: 'up' | 'down' | 'flat' | 'none'
  text: string
}

/**
 * 体重变化文案。口径是**最新一次 − 30 天前基线**（后端 `weightChangeKg`），
 * 没有 30 天前的记录时后端回 `null` → 这里必须给"还没有对比基线"，不能显示 0。
 */
export function weightChangeInfo(changeKg: number | null | undefined): WeightChangeInfo {
  if (changeKg === null || changeKg === undefined || !Number.isFinite(Number(changeKg))) {
    return { tone: 'none', text: '还没有 30 天前的记录，无法对比' }
  }
  const v = Number(changeKg)
  const abs = Math.abs(v).toFixed(2)
  if (Math.abs(v) < 0.005) return { tone: 'flat', text: '与 30 天前持平' }
  return v > 0
    ? { tone: 'up', text: `比 30 天前重了 ${abs} kg` }
    : { tone: 'down', text: `比 30 天前轻了 ${abs} kg` }
}

/** 严重度文案：1 轻度 / 2 中度 / 3 重度（`severityDesc` 后端也给了，这里用于兜底与筛选） */
export function severityText(severity: number | null | undefined): string {
  if (severity === 1) return '轻度'
  if (severity === 2) return '中度'
  if (severity === 3) return '重度'
  return '未分级'
}

/** 严重度的视觉档位（模板里映射成不同徽标，不在这里拼 class） */
export function severityTone(severity: number | null | undefined): 'low' | 'mid' | 'high' | 'none' {
  if (severity === 1) return 'low'
  if (severity === 2) return 'mid'
  if (severity === 3) return 'high'
  return 'none'
}

/** 用药状态文案（后端 `statusDesc` 已算好，这里用于筛选控件的标题与兜底） */
export function medicationStatusText(status: number | null | undefined): string {
  return MEDICATION_STATUS.find((s) => s.value === status)?.label ?? '状态未知'
}

/** `recordLimit` 是不是"不限"（`-1`；契约里 `-1` 时 `limitReachedTypes` 恒为空数组） */
export function isUnlimitedLimit(recordLimit: number | null | undefined): boolean {
  return recordLimit !== null && recordLimit !== undefined && Number(recordLimit) < 0
}

/** 条数文案：`3 / 100 条`；不限档位只说 `3 条`（别显示 `3 / -1`） */
export function recordCountText(count: number | null | undefined, recordLimit: number | null | undefined): string {
  const c = Number(count ?? 0)
  if (isUnlimitedLimit(recordLimit)) return `${c} 条`
  if (recordLimit === null || recordLimit === undefined) return `${c} 条`
  return `${c} / ${Number(recordLimit)} 条`
}

/** 某类记录是否已记满（`limitReachedTypes` 后端直接告诉你，不用自己比大小） */
export function isLimitReached(type: string, limitReachedTypes: string[] | null | undefined): boolean {
  return Array.isArray(limitReachedTypes) && limitReachedTypes.includes(type)
}

/** 时间线的列表 key：`recordId` 会重复（各类表自己的 id），必须拼上类型 */
export function timelineKey(item: Pick<HealthTimelineItem, 'recordType' | 'recordId'>): string {
  return `${item.recordType}#${item.recordId}`
}

/** 宠物大类 → 症状选项的 `species`（猫=1 狗=2；其它物种传 undefined，让后端回全部） */
export function symptomSpeciesOf(petSpecies: string | null | undefined): number | undefined {
  const s = (petSpecies ?? '').trim()
  if (s === '猫') return SYMPTOM_SPECIES.cat
  if (s === '狗') return SYMPTOM_SPECIES.dog
  return undefined
}

/** `species` → 中文（症状选项的展示用） */
export function speciesText(species: number | null | undefined): string {
  if (species === SYMPTOM_SPECIES.cat) return '猫'
  if (species === SYMPTOM_SPECIES.dog) return '狗'
  return '其它'
}

/**
 * `timeline?types` 的拼装：**只保留合法枚举**。
 *
 * 非法值会走后端 `resolveTypes()` 的 `ClientException` → `A000001 不支持的记录类型：xxx`，
 * 与 `preventive?type` 的"静默忽略"完全相反，所以这里必须过滤而不是透传。
 */
export function timelineTypesParam(types: string[] | null | undefined): string {
  if (!Array.isArray(types)) return ''
  const valid = types.filter((t) => (HEALTH_RECORD_TYPES as readonly string[]).includes(t))
  return valid.join(',')
}

/** 曲线月份钳制（后端也钳，这里是让 UI 不发出无意义的请求） */
export function clampMonths(months: number | null | undefined): number {
  const v = Math.trunc(Number(months))
  if (!Number.isFinite(v) || v < 1) return 6
  return Math.min(v, 24)
}

/** 时间线条数钳制（后端默认 50、下界钳到 1；上界 200 是源码写的，实测无法证明） */
export function clampLimit(limit: number | null | undefined): number {
  const v = Math.trunc(Number(limit))
  if (!Number.isFinite(v) || v < 1) return 50
  return Math.min(v, 200)
}

/** 记录类型的中文名（`recordCounts` 的 key → 中文） */
export function recordTypeText(type: string | null | undefined): string {
  return RECORD_TYPE_TEXT[type as HealthRecordType] ?? '其它'
}

/**
 * 日期时间输入的最小值：只能填"现在"或之前（症状/就诊/疫苗都是已发生的事）。
 * 体重与用药的开始时间也用它；`endAt` 允许将来（疗程还没结束）。
 */
export function nowDatetimeLocal(): string {
  const d = new Date()
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}T${pad2(d.getHours())}:${pad2(d.getMinutes())}`
}

/* ---------- 付费墙 / 错误文案 ---------- */

export type HealthGateKind = 'record-limit' | 'export' | 'ai' | 'need-symptom'

export interface HealthGate {
  kind: HealthGateKind
  title: string
  desc: string
}

/**
 * 把"付费墙 / 前置条件"这类**正常状态**从错误里分出来。
 *
 * 为什么：`B000403`（条数满）、`B000404`（导出需档位）、`B000411`（AI 解读需档位）都是权益边界，
 * 不是故障 —— 界面要给明确的升级引导（去会员页），**不要当报错、也不要自动重试**。
 * `B000412`（还没有症状记录）不是付费墙，是"先做一件小事"的引导。
 */
export function healthGateOf(e: unknown): HealthGate | null {
  if (!(e instanceof ApiError)) return null
  switch (e.code) {
    case 'B000403':
      return {
        kind: 'record-limit',
        title: '这类记录已经记满了',
        desc: e.message || '当前档位的健康记录条数已达上限，开通会员后可以不限条数记录。',
      }
    case 'B000404':
      return {
        kind: 'export',
        title: '当前档位不支持导出',
        desc: '导出完整健康档案是会员功能，开通 24.9 及以上档位可用。',
      }
    case 'B000411':
      return {
        kind: 'ai',
        title: '当前档位不支持 AI 解读',
        desc: e.message || 'AI 症状解读是会员功能，开通 34.9 及以上档位可用。',
      }
    case 'B000412':
      return {
        kind: 'need-symptom',
        title: '还没有症状记录',
        desc: 'AI 解读要基于症状日记，先记一条症状再来解读。',
      }
    default:
      return null
  }
}

/**
 * 健康模块错误码 → 人话（码表来自契约 §2.4 的后端 `HealthyErrorCodeEnum`）。
 * 后端 message 本身就是中文，能直接用就透传，这里只补"下一步该怎么办"。
 */
export function healthErrorMessage(e: unknown): string {
  if (!(e instanceof ApiError)) return (e as Error)?.message || '未知错误'
  const gate = healthGateOf(e)
  if (gate) return `${gate.title}。${gate.desc}`
  switch (e.code) {
    case 'NETWORK':
      // message 里带着实际请求地址，比笼统文案有用 → 直接透传
      return e.message || '连不上后端，请检查后端进程与 Vite 代理'
    case 'B000400':
      return '这只宠物不存在，或者不属于当前账号'
    case 'B000401':
      return '宠物服务暂时不可用，稍后重试（不是你操作的问题）'
    case 'B000402':
      return '这条记录不存在，或者不属于你（后端对这两种情况用同一个码）'
    case 'B000405':
      return '会员权益服务暂时不可用，稍后重试（不是你操作的问题）'
    case 'B000406':
      return e.message || '记录没有保存成功，稍后重试'
    case 'B000407':
      return '必填信息没填完，请把带星号的字段补齐'
    case 'B000408':
      return '知识库服务暂时不可用，稍后重试'
    case 'B000409':
      return '这个症状选项不存在了，重新从列表里选一个'
    case 'B000413':
      return 'AI 解读服务暂时不可用，稍后重试'
    case 'B000001':
      return '服务没能处理这个请求。多半是路径或参数写错了，也可能是健康服务正在重启，稍后重试一次'
    default:
      return e.message || '请求失败，请稍后再试'
  }
}

/**
 * 把导出结果存成 JSON 文件（后端只给结构化 JSON，没有文件导出）。
 *
 * ⚠️ 成功分支**未实测**（本账号档位不够，`B000404`），这里只做"拿到对象就下载"的稳妥实现。
 * 无 DOM 环境（离线断言脚本）直接返回 false，不抛。
 */
export function saveJsonFile(filename: string, data: unknown): boolean {
  if (typeof document === 'undefined' || typeof URL === 'undefined') return false
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
  return true
}

/* ---------- draft → 请求体（**每类都把全部字段写出来**，PUT 才不会误清字段） ---------- */

export function preventiveBody(draft: PreventiveDraft): PreventiveSaveBody {
  return {
    type: draft.type,
    itemName: (draft.itemName ?? '').trim(),
    happenedAt: draft.happenedAt,
    // 这三项可以为空，显式输出 null 才是"清空"（PUT 是 set() 全量覆盖）
    nextDueAt: draft.nextDueAt || null,
    hospital: draft.hospital || null,
    batchNo: draft.batchNo || null,
    remark: draft.remark || null,
  }
}

export function medicalBody(draft: MedicalDraft): MedicalSaveBody {
  return {
    visitAt: draft.visitAt,
    hospital: draft.hospital || null,
    doctor: draft.doctor || null,
    diagnosis: (draft.diagnosis ?? '').trim(),
    treatment: draft.treatment || null,
    cost: draft.cost === null || draft.cost === undefined ? null : Number(draft.cost),
    // 出参数组 → 入参逗号串；空附件发 null
    attachments: toCommaString(draft.attachments),
    remark: draft.remark || null,
  }
}

export function medicationBody(draft: MedicationDraft): MedicationSaveBody {
  return {
    medicalId: draft.medicalId ?? null,
    medicineName: (draft.medicineName ?? '').trim(),
    dosage: draft.dosage || null,
    frequency: draft.frequency || null,
    startAt: draft.startAt,
    endAt: draft.endAt || null,
    // 显式传状态：不传的话后端会按 endAt 推断，用户选的"已停用"就丢了
    status: draft.status ?? 0,
    remark: draft.remark || null,
  }
}

export function allergyBody(draft: AllergyDraft): AllergySaveBody {
  return {
    allergen: (draft.allergen ?? '').trim(),
    severity: draft.severity ?? 1,
    reaction: draft.reaction || null,
    note: draft.note || null,
  }
}

export function symptomBody(draft: SymptomDraft): SymptomSaveBody {
  const symptomId = draft.symptomId ?? null
  return {
    symptomId,
    // 选了知识库条目就不要再发名字：后端会用标题覆盖它，发了只会让人误以为自定义生效
    symptomName: symptomId ? null : (draft.symptomName ?? '').trim() || null,
    description: draft.description || null,
    severity: draft.severity ?? 1,
    occurredAt: draft.occurredAt,
    images: toCommaString(draft.images),
    remark: draft.remark || null,
  }
}

export function weightBody(draft: WeightDraft): WeightSaveBody {
  return {
    weightKg: Number(draft.weightKg),
    measuredAt: draft.measuredAt,
    note: draft.note || null,
  }
}

/** 记录 → 草稿（编辑页用：整条拷贝，保证 PUT 时有全量字段） */
export function toPreventiveDraft(r: PreventiveRecord): PreventiveDraft {
  return {
    type: r.type,
    itemName: r.itemName ?? '',
    happenedAt: r.happenedAt,
    nextDueAt: r.nextDueAt,
    hospital: r.hospital,
    batchNo: r.batchNo,
    remark: r.remark,
  }
}

export function toMedicalDraft(r: MedicalRecord): MedicalDraft {
  return {
    visitAt: r.visitAt,
    hospital: r.hospital,
    doctor: r.doctor,
    diagnosis: r.diagnosis ?? '',
    treatment: r.treatment,
    cost: r.cost,
    // ⚠️ 显式传 `''` 关掉展示期归一化：这一步是"读全量"，随后可能整体 PUT 回库，
    // 不能让浏览器把自己的主机名写进数据库（那正是挂账 #67 的病灶）。
    attachments: attachmentList(r.attachments, ''),
    remark: r.remark,
  }
}

export function toMedicationDraft(r: MedicationRecord): MedicationDraft {
  return {
    medicalId: r.medicalId,
    medicineName: r.medicineName ?? '',
    dosage: r.dosage,
    frequency: r.frequency,
    startAt: r.startAt,
    endAt: r.endAt,
    status: r.status ?? 0,
    remark: r.remark,
  }
}

export function toAllergyDraft(r: AllergyRecord): AllergyDraft {
  return {
    allergen: r.allergen ?? '',
    severity: r.severity ?? 1,
    reaction: r.reaction,
    note: r.note,
  }
}

export function toSymptomDraft(r: SymptomLog): SymptomDraft {
  return {
    symptomId: r.symptomId,
    symptomName: r.symptomName ?? '',
    description: r.description,
    severity: r.severity ?? 1,
    occurredAt: r.occurredAt,
    // 同 toMedicalDraft：读全量的那一步不归一化，避免把主机名写回库
    images: attachmentList(r.images, ''),
    remark: r.remark,
  }
}

export function toWeightDraft(r: WeightRecord): WeightDraft {
  return { weightKg: r.weightKg, measuredAt: r.measuredAt, note: r.note }
}