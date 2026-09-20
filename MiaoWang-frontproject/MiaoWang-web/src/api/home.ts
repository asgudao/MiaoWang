/**
 * 首页数据（Web）
 *
 * 两类数据来源不同，**不要混在一起**：
 * - **宠物档案**：真接口（`miaowang-pet`），`petBriefFromApi()` 映射成卡片形状
 * - **照护提醒 / 本周记录**：**全部由健康记录派生**（`miaowang-healthy`）。
 *   后端**没有任何提醒接口**（143 处 controller mapping 里没有 reminder/plan/schedule/task；
 *   7 条不存在性探针全是 `B000001`，原因是 `GlobalExceptionHandler` 把 404/405 一并兜底了 →
 *   **不能靠 404 判断接口没上线**。详见 `.dsh-tmp/reminder-notification-contract.md` §A）
 *
 * 派生口径就两条，写在这里，页面**不许另算一套**：
 *
 * 1. **待办 = 逾期 + 今天到期**（`nextDueDays < 0` / `=== 0`）。后端**没有打卡表** →
 *    "今天做了几项 / 完成率"既没有分子也没有分母，所以本文件**不产出任何 Done 字段**，
 *    只产出 `todoCount`（待办）与 `overdueCount`（逾期）。
 * 2. **周节奏 = 每天写了多少条健康记录**（`timeline` 的条数），**不是照护完成率** →
 *    字段叫 `count`，没有 `done/total`，标题也必须说清是"记录"。
 * 3. **本机「提醒设置」**（`utils/remindSetting.ts`，与手机端 `utils/remind-setting.js` 同一把）→
 *    由 `clipOverviewByRemind()` **统一**作用在 `todoCount` / `overdueCount` / `tasks` /
 *    宠物卡的 `nextCare` 上：提前 N 天扩窗口、总开关关掉则整体下线并记 `suppressedCount`。
 *    默认设置（开启 + 提前 0 天）下结果与改动前**逐位一致**。
 *
 * 换算一律复用 `./health` 里**已被健康门禁脚本覆盖**的那套（`overdueLabel` / `sortByDue` /
 * `mostUrgentPreventive` / `parseHealthTime`），不另写一份分档逻辑。
 *
 * ⚠️ 实测钉死的一条：`overview.nextPreventive` **排除逾期项**（源码 `.ge(nextDueAt, now)`，挂账 #87）→
 * 最紧急的一条**必须**自己对 `preventive` 全量排序（`mostUrgentPreventive`），拿 overview 会漏掉逾期。
 *
 * ⚠️ 会员权益 `reminderLimit`（PET_49=3 / AI_149 起 -1）在"前端派生"方案里**无处可挂**：
 * 派生出来的提醒不是"用户创建的提醒条数"。本切片**暂定不管它**（不显示上限、不做拦截），
 * 等后端 `MiaoWang-reminder` 模块落地再谈（vip README 里的规划）。
 */
import { auth } from './request'
import type { Pet } from './pet'
import { listMyPets } from './pet'
import { ageLabel, breedLabel, initials, petDisplayName } from '../utils/pet'
import { readSetting } from '../utils/remindSetting'
import type { RemindSetting } from '../utils/remindSetting'
import { isWithinWindow } from '../utils/remindSchedule'
import {
  getTimeline,
  listMedication,
  listPreventive,
  listWeight,
  medicationStatusText,
  mostUrgentPreventive,
  overdueLabel,
  parseHealthTime,
  sortByDue,
  type HealthTimelineItem,
  type MedicationRecord,
  type PreventiveRecord,
  type WeightRecord,
} from './health'

/**
 * 演示数据开关。
 *
 * ⚠️ **语义已改成真正的开关**：默认**关**（不设 `VITE_USE_MOCK` 就走真数据），
 * 只有显式设成 `'1'` 才回到演示数据；关掉之后本文件**不会再返回任何编造的照护项**
 * （未登录时返回空概览，页面显示空态，而不是拿 mock 顶上）。
 */
export const USE_MOCK = import.meta.env.VITE_USE_MOCK === '1'

/** >= 保留给"转成数字再比较"的兜底，避免 NaN 参与排序 */
const FAR = Number.POSITIVE_INFINITY

/** 照护项的类别：**只有后端真有数据源的三类**（喂食/饮水/运动在后端没有任何排程数据，不编） */
export type TaskKind = 'vaccine' | 'deworm' | 'med'
/** 照护项状态：**没有 `done`** —— 后端没有打卡记录，"完成"无法判定 */
export type TaskStatus = 'overdue' | 'today' | 'upcoming' | 'active'
export type CareTone = 'ok' | 'warn' | 'danger'

export interface CareTask {
  id: string
  title: string
  kind: TaskKind
  petName: string
  /** 时间或状态文案，如「已逾期 10 天」「今天到期」「还有 175 天」「用药中」 */
  dueLabel: string
  status: TaskStatus
  /** 排序键（越小越紧急）：逾期最久的最前；用药没有到期时间 → 排最后（`Infinity`） */
  dueRank: number
}

export interface PetBrief {
  pid: string
  name: string
  /** 大类 + 品种，如「猫 中华田园猫」 */
  breed: string
  ageLabel: string
  initial: string
  /** true = 演示数据；false = 用户自己的真实档案 */
  demo: boolean
  /** 最新一次体重（kg）；**没有体重记录时整个字段不产出**（页面不渲染，不编 0） */
  weight?: number
  /** 最新体重的测量日，`MM-DD` */
  weightAt?: string
  /** 演示数据专有：真档案的体重变化缺 30 天基线，宁可不显示 */
  weightDelta?: number
  /** 最紧急的照护事项文案，如「大宠爱（体外驱虫）已逾期 10 天」 */
  nextCare?: string
  nextTone?: CareTone
  /** 真实档案才有：它怎么称呼主人 */
  callName?: string
  /**
   * 这只宠物的健康记录**一条都没读到**（4 个请求全失败）。
   * 页面据此说明「健康数据未读到」，而不是说成「它没有体重/没有照护项」。
   */
  degraded?: boolean
}

/** 最近 7 天**每天写了多少条健康记录**（不是"完成率"：后端没有打卡表） */
export interface RhythmDay {
  label: string
  count: number
  isToday?: boolean
}

export interface HomeOverview {
  /** `'real'` = 全部来自真接口；`'mock'` = 演示数据（只有显式打开 `VITE_USE_MOCK=1` 才会出现） */
  source: 'real' | 'mock'
  ownerName: string
  /** 待办项数 = 逾期 + 今天到期（没有"已完成"，理由见文件头）+ 「提前 N 天」窗口内还没到期的项 */
  todoCount: number
  overdueCount: number
  tasks: CareTask[]
  pets: PetBrief[]
  rhythm: RhythmDay[]
  /** 健康记录**一条都没读到**的宠物名（部分失败降级用；有值时页面要如实说明） */
  degradedPets: string[]
  /**
   * 本机「提醒设置」的总开关**关着**（`utils/remindSetting.ts`，与手机端同一把设置）。
   *
   * 关掉时 `tasks` 会被裁空 —— ⚠️ 那**不等于"没有照护项"**：
   * 页面必须用 `suppressedCount` 说明"有 N 条本来会提醒"，否则就是**谎报"什么都没有"**
   * （手机端踩过这个真 bug：看板被裁空后 `suppressed` 归零）。
   */
  remindOff: boolean
  /** 生效中的「提前 N 天」（0 = 既有口径「逾期 + 今天到期」） */
  remindAheadDays: number
  /** 被总开关关掉、**本来**落在提醒窗口内的条数（`remindOff` 为 true 时才有意义） */
  suppressedCount: number
}

/**
 * 演示数据（**只有显式 `VITE_USE_MOCK=1` 才会返回**，页面会标注"演示数据"）。
 *
 * 形状与真实数据**完全一致**（连 `source` 都有），所以模板只有一套；
 * 类别只用真实数据源里存在的三类（疫苗 / 驱虫 / 用药），不再编 feed/water/walk。
 */
const MOCK: HomeOverview = {
  source: 'mock',
  ownerName: '铲屎官',
  todoCount: 1,
  overdueCount: 1,
  tasks: [
    {
      id: 'demo:preventive#2',
      title: '大宠爱（体外驱虫）',
      kind: 'deworm',
      petName: '汪汪',
      dueLabel: '已逾期 1 天',
      status: 'overdue',
      dueRank: -1,
    },
    {
      id: 'demo:preventive#1',
      title: '猫三联',
      kind: 'vaccine',
      petName: '喵喵',
      dueLabel: '还有 23 天',
      status: 'upcoming',
      dueRank: 23,
    },
    {
      id: 'demo:medication#1',
      title: '速诺（阿莫西林克拉维酸钾）',
      kind: 'med',
      petName: '喵喵',
      dueLabel: '用药中',
      status: 'active',
      dueRank: FAR,
    },
  ],
  pets: [
    {
      pid: 'p1',
      name: '喵喵',
      breed: '英短蓝猫',
      ageLabel: '2 岁 3 个月',
      initial: '喵',
      demo: true,
      weight: 4.2,
      weightDelta: -0.1,
      nextCare: '猫三联还有 23 天',
      nextTone: 'ok',
    },
    {
      pid: 'p2',
      name: '汪汪',
      breed: '柯基',
      ageLabel: '1 岁 7 个月',
      initial: '汪',
      demo: true,
      weight: 11.6,
      weightDelta: 0.3,
      nextCare: '体外驱虫已逾期 1 天',
      nextTone: 'danger',
    },
  ],
  rhythm: [
    { label: '一', count: 4 },
    { label: '二', count: 3 },
    { label: '三', count: 4 },
    { label: '四', count: 2 },
    { label: '五', count: 4 },
    { label: '六', count: 4 },
    { label: '日', count: 1, isToday: true },
  ],
  degradedPets: [],
  // 演示数据也走同一把本机设置裁剪（`clipOverviewByRemind`），所以这里给"没关"的中性初值；
  // 真值由 `fetchHomeOverview()` 每次现算 —— 演示模式下也不许无视用户的开关
  remindOff: false,
  remindAheadDays: 0,
  suppressedCount: 0,
}

const TASK_LABEL: Record<TaskKind, string> = {
  vaccine: '疫苗',
  deworm: '驱虫',
  med: '用药',
}
export const taskKindLabel = (k: TaskKind) => TASK_LABEL[k]

const STATUS_LABEL: Record<TaskStatus, string> = {
  overdue: '已逾期',
  today: '今天到期',
  upcoming: '未到期',
  active: '用药中',
}
export const taskStatusLabel = (s: TaskStatus) => STATUS_LABEL[s]

/** 最近 7 天的**记录条数合计**（周节奏图的标题数字；不是完成率） */
export function rhythmTotal(days: RhythmDay[]): number {
  return (Array.isArray(days) ? days : []).reduce((n, d) => n + Number(d.count || 0), 0)
}

const WEEK_LABELS = ['日', '一', '二', '三', '四', '五', '六']
const pad2 = (n: number) => String(n).padStart(2, '0')

/** 本地日期键 `yyyy-MM-dd`（时间串是 GMT+8 本地、非 ISO，必须走 `parseHealthTime` 再取本地年月日） */
function dayKey(d: Date): string {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

/** `2026-09-20 08:00:00` → `09-20`（宠物卡上"哪天量的"）；解析不出来时返回空串 */
export function dayText(value: string | null | undefined): string {
  const d = parseHealthTime(value)
  return d ? `${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}` : ''
}

/**
 * 预防记录的类别：`VACCINE` → 疫苗，其余（`DEWORM_IN` / `DEWORM_OUT` / 未知）→ 驱虫。
 * 未知值不编第四类 —— 后端枚举只有三种，多出来的类别一定是我方表格写错了。
 */
export function preventiveKind(type: string | null | undefined): TaskKind {
  return String(type ?? '').toUpperCase() === 'VACCINE' ? 'vaccine' : 'deworm'
}

/** `overdueLabel()` 的分档 → 照护项状态（`none` 不参与：没有到期时间就没有提醒） */
const TONE_STATUS: Record<'overdue' | 'today' | 'soon' | 'later', TaskStatus> = {
  overdue: 'overdue',
  today: 'today',
  soon: 'upcoming',
  later: 'upcoming',
}

/** `overdueLabel()` 的分档 → 卡片色档（逾期红、今天与 30 天内琥珀、更远中性） */
export function nextCareTone(r: PreventiveRecord | null | undefined): CareTone | undefined {
  if (!r) return undefined
  const tone = overdueLabel(r.nextDueDays).tone
  if (tone === 'overdue') return 'danger'
  if (tone === 'today' || tone === 'soon') return 'warn'
  if (tone === 'later') return 'ok'
  return undefined
}

/** 宠物卡上的"下一件照护事项"：`itemName` + 分档文案（实测样本文案「体外驱虫已逾期 10 天」） */
export function nextCareText(r: PreventiveRecord | null | undefined): string {
  if (!r) return ''
  const info = overdueLabel(r.nextDueDays)
  if (info.tone === 'none') return ''
  return `${(r.itemName || r.typeDesc || '照护项').trim()}${info.text}`
}

/**
 * 一只宠物的照护项（**纯函数**，便于离线断言）。
 *
 * - `preventive`：**只有 `nextDueDays` 能判定的才产出**（`nextDueAt` 为空的记录没有到期时间 → 不编）；
 *   顺序用 `sortByDue()`：逾期最久的排最前（负数升序）
 * - `medication`：只有 `status === 0`（用药中）算"正在进行的照护"。它**没有到期时间** →
 *   排在所有有到期时间的项之后，也**不计入待办**
 */
export function buildCareTasks(
  pid: number | string,
  petName: string,
  preventive: PreventiveRecord[],
  medication: MedicationRecord[],
): CareTask[] {
  const tasks: CareTask[] = []
  for (const r of sortByDue(Array.isArray(preventive) ? preventive : [])) {
    const info = overdueLabel(r.nextDueDays)
    if (info.tone === 'none') continue
    tasks.push({
      id: `${pid}:preventive#${r.id}`,
      title: (r.itemName || r.typeDesc || '照护项').trim(),
      kind: preventiveKind(r.type),
      petName,
      dueLabel: info.text,
      status: TONE_STATUS[info.tone],
      dueRank: info.days ?? FAR,
    })
  }
  for (const m of Array.isArray(medication) ? medication : []) {
    if (m.status !== 0) continue
    tasks.push({
      id: `${pid}:medication#${m.id}`,
      title: (m.medicineName || '用药').trim(),
      kind: 'med',
      petName,
      dueLabel: m.statusDesc || medicationStatusText(m.status),
      status: 'active',
      dueRank: FAR,
    })
  }
  return tasks
}

/** 跨宠物合并后的排序：逾期最久 → 今天 → 越快到期的越前 → 用药（无到期）最后；同档按 id 稳定 */
export function sortCareTasks(tasks: CareTask[]): CareTask[] {
  return [...tasks].sort(
    (a, b) => a.dueRank - b.dueRank || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0),
  )
}

/**
 * 最新一次体重：**按 `measuredAt` 取最大，不假设列表顺序**。
 * 全部解析不出来时返回第一条（或 null），不编数字。
 */
export function latestWeight(list: WeightRecord[]): WeightRecord | null {
  let best: WeightRecord | null = null
  let bestAt = -Infinity
  for (const w of Array.isArray(list) ? list : []) {
    const t = parseHealthTime(w.measuredAt)
    const ts = t ? t.getTime() : -Infinity
    if (!best || ts > bestAt) {
      best = w
      bestAt = ts
    }
  }
  return best
}

/**
 * 最近 7 天**每天的健康记录条数**（周节奏图的数据源）。
 *
 * ⚠️ 数的是"记录活跃度"，**不是"照护完成率"** —— 后端没有打卡历史
 * （healthy README 明说"完成打卡历史归 reminder 模块"），所以标题必须写"记录"。
 * `now` 可注入：fixture 是 2026-09-20 抓的，离线断言不能依赖"今天"。
 */
export function buildRhythm(timelines: HealthTimelineItem[], now: Date = new Date()): RhythmDay[] {
  const days: RhythmDay[] = []
  const index = new Map<string, number>()
  for (let i = 6; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth(), now.getDate() - i)
    const item: RhythmDay = { label: WEEK_LABELS[d.getDay()] ?? '日', count: 0 }
    if (i === 0) item.isToday = true
    index.set(dayKey(d), days.length)
    days.push(item)
  }
  for (const row of Array.isArray(timelines) ? timelines : []) {
    const t = parseHealthTime(row?.happenedAt)
    if (!t) continue
    const at = index.get(dayKey(t))
    const day = at === undefined ? undefined : days[at]
    if (day) day.count++
  }
  return days
}

/**
 * 真实宠物档案 → 首页卡片。
 *
 * 只搬 pet 接口真有的字段；**体重与下一件照护事项不在这个函数里**——
 * 它们来自健康记录，由 `fetchHomeOverview()` 用 `latestWeight()` / `nextCareText()` 补上
 * （没有记录就整个字段不产出，页面不渲染，绝不编一个体重）。
 * 空名字用「未命名宠物」兜底（后端不校验名字，库里已有空名数据）。
 */
export function petBriefFromApi(p: Pet): PetBrief {
  return {
    pid: String(p.pid),
    name: petDisplayName(p),
    breed: breedLabel(p),
    ageLabel: ageLabel(p.birth),
    initial: initials(p),
    demo: false,
    callName: p.ownerCallName?.trim() || undefined,
  }
}

/**
 * 登录后取自己的宠物档案（映射成首页卡片形状）。
 *
 * 未登录时不调用；失败就抛出，让调用方决定回退。
 */
export async function fetchMyPetBriefs(): Promise<PetBrief[]> {
  return (await listMyPets()).map(petBriefFromApi)
}

/** 一只宠物的四类健康数据（`failures` = 4 个请求里失败几个） */
interface PetHealth {
  pid: number
  preventive: PreventiveRecord[]
  medication: MedicationRecord[]
  weight: WeightRecord[]
  timeline: HealthTimelineItem[]
  failures: number
}

/**
 * 一只宠物的四类健康数据。
 *
 * ⚠️ healthy **没有跨宠物聚合接口**（挂账 #82）→ 多宠物就是 `1 + 4N` 个请求
 * （免费 2 只 / 会员 5 只 → 最多 21 个）。这里**并发**发出（`Promise.allSettled`），
 * 单项失败只记进 `failures`，**不会**让整页进错误态。
 */
async function loadPetHealth(pid: number): Promise<PetHealth> {
  const results = await Promise.allSettled([
    listPreventive(pid),
    listMedication(pid),
    listWeight(pid),
    getTimeline(pid),
  ])
  const pick = <T>(r: PromiseSettledResult<T[]>): T[] =>
    r.status === 'fulfilled' && Array.isArray(r.value) ? r.value : []
  return {
    pid,
    preventive: pick(results[0] as PromiseSettledResult<PreventiveRecord[]>),
    medication: pick(results[1] as PromiseSettledResult<MedicationRecord[]>),
    weight: pick(results[2] as PromiseSettledResult<WeightRecord[]>),
    timeline: pick(results[3] as PromiseSettledResult<HealthTimelineItem[]>),
    failures: results.filter((r) => r.status === 'rejected').length,
  }
}

/**
 * **本机「提醒设置」怎么改变首页**（唯一一处；页面不许另算一套）
 *
 * 手机端 `MiaoWang-phone/api/home.js` 的同一套口径，落到 Web 的 `HomeOverview` 上：
 *
 * | 设置 | 真的改变了什么 |
 * |---|---|
 * | **提前 N 天** | `todoCount` 从"逾期 + 今天到期"扩成"窗口内全部"（`isWithinWindow`，
 *   内部走 `overdueLabel()` 同一真源）；`N = 0` 时**恒等于原口径** → 默认设置下既有行为一字不变 |
 * | **总开关关** | `todoCount` / `overdueCount` 归 0、`tasks` 清空、宠物卡的 `nextCare` 下线（它同样是派生出来的提醒标记）|
 * | （防谎报） | 关掉时把"本来有几条"记进 `suppressedCount` —— 页面据此说明，
 *   **不许把"被开关关掉"说成"没有照护项"** |
 *
 * 关于 `tasks`：**默认不按窗口裁剪**（保留全部，含很远的未来项），与手机端一致 ——
 * 被窗口裁剪的只有 `todoCount`（"待办"这个数字）与总开关关掉后的整体清空。
 * 用药项 `dueRank` 是 `Infinity`（没有到期时间）→ `isWithinWindow` 判 `none` → 永远不进窗口。
 *
 * @returns **新对象**（不改入参：`MOCK` 是模块级常量，改它会污染下一次调用）
 */
export function clipOverviewByRemind(overview: HomeOverview, setting?: RemindSetting): HomeOverview {
  const s = setting ?? readSetting(auth.username)
  const tasks = Array.isArray(overview.tasks) ? overview.tasks : []
  const inWindow = tasks.filter((t) => isWithinWindow(t.dueRank, s.aheadDays))
  const overdue = inWindow.filter((t) => t.status === 'overdue').length

  if (s.enabled) {
    return {
      ...overview,
      todoCount: inWindow.length,
      overdueCount: overdue,
      remindOff: false,
      remindAheadDays: s.aheadDays,
      suppressedCount: 0,
    }
  }
  return {
    ...overview,
    todoCount: 0,
    overdueCount: 0,
    tasks: [],
    // 宠物卡上的"下一件照护事项"也是派生提醒 → 总开关关掉就不再显示（不编一个空的占位文案）
    pets: overview.pets.map((p) => ({ ...p, nextCare: undefined, nextTone: undefined })),
    remindOff: true,
    remindAheadDays: s.aheadDays,
    suppressedCount: inWindow.length,
  }
}

/**
 * 空概览：没有宠物档案、或未登录时用它。
 * **一个数字都不编**；周节奏仍给 7 天（全 0），免得柱图跳版。
 */
export function emptyOverview(now: Date = new Date()): HomeOverview {
  return clipOverviewByRemind({
    source: 'real',
    ownerName: '铲屎官',
    todoCount: 0,
    overdueCount: 0,
    tasks: [],
    pets: [],
    rhythm: buildRhythm([], now),
    degradedPets: [],
    remindOff: false,
    remindAheadDays: 0,
    suppressedCount: 0,
  })
}

/**
 * 首页概览。
 *
 * - `USE_MOCK`（`VITE_USE_MOCK=1`）→ 演示数据，`source: 'mock'`
 * - 未登录 → **一个请求都不发、也不编数据**（`/health/**` 经网关全都要登录）→ 空概览
 * - 已登录 → `GET /pet` + 每只宠物 4 个健康接口（并发）→ 纯函数拼装
 *
 * 失败降级分两层：`/pet` 失败会**抛出**（页面进错误态 + 重试）；
 * 某只宠物的健康数据全失败时，只把它记进 `degradedPets`（页面如实说明），其余宠物照常。
 */
export async function fetchHomeOverview(): Promise<HomeOverview> {
  // 本机「提醒设置」是**唯一真源**（`utils/remindSetting.ts`，与手机端同一把设置）：
  // 它在返回前由 `clipOverviewByRemind()` 统一作用到 todoCount / tasks / 宠物卡上。
  // 默认设置（开启 + 提前 0 天）下结果与改动前**逐位一致**，所以既有断言不受影响。
  if (USE_MOCK) {
    await new Promise((r) => setTimeout(r, 260))
    return clipOverviewByRemind(MOCK)
  }
  if (!auth.token || !auth.username) return emptyOverview()

  const pets = await listMyPets()
  if (!pets.length) return emptyOverview()

  const briefs = pets.map(petBriefFromApi)
  const health = await Promise.all(pets.map((p) => loadPetHealth(p.pid)))
  const byPid = new Map(health.map((h) => [String(h.pid), h]))

  const tasks: CareTask[] = []
  const degradedPets: string[] = []
  const enriched: PetBrief[] = []

  for (const brief of briefs) {
    const h = byPid.get(brief.pid)
    if (!h || h.failures === 4) {
      // 这只宠物的健康数据一条都没读到：只显示档案，照护项**不猜**
      degradedPets.push(brief.name)
      enriched.push({ ...brief, degraded: true })
      continue
    }
    tasks.push(...buildCareTasks(brief.pid, brief.name, h.preventive, h.medication))
    const urgent = mostUrgentPreventive(h.preventive)
    const latest = latestWeight(h.weight)
    enriched.push({
      ...brief,
      ...(latest ? { weight: latest.weightKg, weightAt: dayText(latest.measuredAt) } : {}),
      ...(urgent ? { nextCare: nextCareText(urgent), nextTone: nextCareTone(urgent) } : {}),
    })
  }

  const sorted = sortCareTasks(tasks)
  return clipOverviewByRemind({
    source: 'real',
    ownerName: '铲屎官',
    todoCount: sorted.filter((t) => t.status === 'overdue' || t.status === 'today').length,
    overdueCount: sorted.filter((t) => t.status === 'overdue').length,
    tasks: sorted,
    pets: enriched,
    rhythm: buildRhythm(health.flatMap((h) => h.timeline)),
    degradedPets,
    remindOff: false,
    remindAheadDays: 0,
    suppressedCount: 0,
  })
}
