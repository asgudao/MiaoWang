/**
 * 首页数据
 *
 * 两类数据来源不同，**不要混在一起**：
 * - **宠物档案**：真接口（`miaowang-pet`）。登录后由 `petBriefFromApi()` 把真档案映射成卡片形状
 * - **照护提醒 / 周节奏 / 陪伴天数**：后端的提醒与健康记录模块还没就绪 → 仍是演示数据，
 *   界面上会明确标注，绝不把 mock 当真实数据展示
 *
 * 演示数据里的体重、下一件照护事项是 pet 接口**没有**的字段：
 * 所以真实档案映射出来的卡片（`demo: false`）不带这两块，页面按 `demo` 决定渲不渲染，
 * 而不是给真档案编一个体重。
 */
import { api } from './request'
import type { Pet } from './pet'
import { ageLabel, breedLabel, initials, petDisplayName } from '../utils/pet'

/** 首页的照护提醒 / 周节奏 / 陪伴天数仍是演示数据（后端未提供这两类接口） */
export const USE_MOCK = import.meta.env.VITE_USE_MOCK !== '0'

export type TaskKind = 'feed' | 'water' | 'walk' | 'med' | 'vaccine'
export type TaskStatus = 'todo' | 'done' | 'overdue'
export type CareTone = 'ok' | 'warn' | 'danger'

export interface CareTask {
  id: string
  title: string
  kind: TaskKind
  petName: string
  /** 时间或状态文案，如「2 小时后」「已逾期」 */
  dueLabel: string
  status: TaskStatus
}

export interface PetBrief {
  pid: string
  name: string
  /** 大类 + 品种，如「猫 中华田园猫」 */
  breed: string
  ageLabel: string
  initial: string
  /** true = 演示数据（带体重与照护提醒）；false = 用户自己的真实档案 */
  demo: boolean
  /** 演示数据专有：pet 接口没有体重，真档案不编 */
  weight?: number
  weightDelta?: number
  /** 演示数据专有：提醒模块未就绪 */
  nextCare?: string
  nextTone?: CareTone
  /** 真实档案才有：它怎么称呼主人 */
  callName?: string
}

/** 最近 7 天的照护完成情况（首页周节奏图用） */
export interface RhythmDay {
  label: string
  done: number
  total: number
  isToday?: boolean
}

export interface HomeOverview {
  ownerName: string
  companionDays: number
  todayDone: number
  todayTotal: number
  tasks: CareTask[]
  pets: PetBrief[]
  rhythm: RhythmDay[]
}

const MOCK: HomeOverview = {
  ownerName: '铲屎官',
  companionDays: 128,
  todayDone: 1,
  todayTotal: 4,
  tasks: [
    { id: 't1', title: '早餐', kind: 'feed', petName: '喵喵', dueLabel: '已完成', status: 'done' },
    {
      id: 't2',
      title: '换水',
      kind: 'water',
      petName: '喵喵',
      dueLabel: '2 小时后',
      status: 'todo',
    },
    {
      id: 't3',
      title: '遛弯 30 分钟',
      kind: 'walk',
      petName: '汪汪',
      dueLabel: '1 小时后',
      status: 'todo',
    },
    {
      id: 't4',
      title: '驱虫药',
      kind: 'med',
      petName: '汪汪',
      dueLabel: '已逾期 1 天',
      status: 'overdue',
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
      nextCare: '疫苗加强针还有 23 天',
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
      nextCare: '体内驱虫已逾期 1 天',
      nextTone: 'danger',
    },
  ],
  rhythm: [
    { label: '一', done: 4, total: 4 },
    { label: '二', done: 3, total: 4 },
    { label: '三', done: 4, total: 4 },
    { label: '四', done: 2, total: 4 },
    { label: '五', done: 4, total: 4 },
    { label: '六', done: 4, total: 4 },
    { label: '日', done: 1, total: 4, isToday: true },
  ],
}

const TASK_LABEL: Record<TaskKind, string> = {
  feed: '喂食',
  water: '饮水',
  walk: '运动',
  med: '用药',
  vaccine: '疫苗',
}
export const taskKindLabel = (k: TaskKind) => TASK_LABEL[k]

const STATUS_LABEL: Record<TaskStatus, string> = {
  todo: '待办',
  done: '已完成',
  overdue: '已逾期',
}
export const taskStatusLabel = (s: TaskStatus) => STATUS_LABEL[s]

/** 最近 7 天完成率（0-100），用于周节奏图的标题数字 */
export function rhythmRate(days: RhythmDay[]): number {
  const done = days.reduce((n, d) => n + d.done, 0)
  const total = days.reduce((n, d) => n + d.total, 0)
  return total ? Math.round((done / total) * 100) : 0
}

/**
 * 真实宠物档案 → 首页卡片。
 *
 * 只搬运真有的字段：体重、下一件照护事项属于健康/提醒模块，接口还没有 → 留空（页面不渲染），
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
 * 未登录时不调用；失败就抛出，让调用方决定回退（首页会退回演示数据并保留标注）。
 */
export async function fetchMyPetBriefs(): Promise<PetBrief[]> {
  const list = await api.get<Pet[]>('pet', '/pet')
  return list.map(petBriefFromApi)
}

/**
 * 首页概览。
 *
 * 注意：`pets` 字段返回的**永远是演示数据** —— 真实档案由页面用 pet 接口覆盖，
 * 这样职责清晰：这个函数负责"还没有接口的那些部分"。
 */
export async function fetchHomeOverview(): Promise<HomeOverview> {
  if (USE_MOCK) {
    await new Promise((r) => setTimeout(r, 260))
    return MOCK
  }
  // TODO 待后端提供：照护提醒接口 + 健康记录接口（宠物档案已接，见 fetchMyPetBriefs）
  return MOCK
}
