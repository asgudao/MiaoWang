/**
 * 宠物资料的展示工具（Web）
 *
 * 手机端 utils/pet.js 是同一套规则的镜像，改这里记得同步那边。
 */
import { ApiError } from '../api/request'
import type { Pet } from '../api/pet'

/** 后端允许「名字为空」，历史数据里已有这种无名宠物 —— 统一兜底文案，别显示空白 */
export function petDisplayName(pet: Pick<Pet, 'pName'> | null | undefined): string {
  const n = pet?.pName?.trim()
  return n || '未命名宠物'
}

/** 名字缺失时给个引导（新建表单和详情页都用） */
export function needsName(pet: Pick<Pet, 'pName'> | null | undefined): boolean {
  return !pet?.pName?.trim()
}

/**
 * 按出生日期算年龄文案。
 *
 * 只到「岁 / 个月 / 天」三档：宠物几个月内变化最快，超过 1 岁就按月计。
 * 后端只回日期（不含时区），所以拼接 T00:00:00 避免被解析成 UTC 前一天的坑。
 */
export function ageLabel(birth: string | null | undefined): string {
  if (!birth) return '生日未填'
  const d = new Date(`${birth}T00:00:00`)
  if (Number.isNaN(d.getTime())) return '生日未填'
  const now = new Date()
  if (d.getTime() > now.getTime()) return '生日还没到'

  let months = (now.getFullYear() - d.getFullYear()) * 12 + (now.getMonth() - d.getMonth())
  if (now.getDate() < d.getDate()) months -= 1

  if (months < 1) {
    const days = Math.max(0, Math.floor((now.getTime() - d.getTime()) / 86_400_000))
    return days <= 0 ? '刚出生' : `${days} 天`
  }
  const years = Math.floor(months / 12)
  const rest = months % 12
  if (years < 1) return `${rest} 个月`
  return rest ? `${years} 岁 ${rest} 个月` : `${years} 岁`
}

/** 大类 + 具体品种，用「·」连接会跟设计规范冲突，所以用空格 */
export function breedLabel(pet: Pick<Pet, 'species' | 'specificSpecies'>): string {
  const s = pet.specificSpecies?.trim()
  return s ? `${pet.species} ${s}` : pet.species
}

export function genderLabel(gender: number | null | undefined): string {
  if (gender === 0) return '雌性'
  if (gender === 1) return '雄性'
  return '未填'
}

export function sterilizedLabel(v: number | null | undefined): string {
  if (v === 0) return '未绝育'
  if (v === 1) return '已绝育'
  return '未填'
}

export function initials(pet: Pick<Pet, 'pName' | 'species'>): string {
  const n = pet.pName?.trim()
  if (n) return n.slice(0, 1)
  return pet.species ? pet.species.slice(0, 1) : '宠'
}

/**
 * 宠物接口的错误文案。
 *
 * 后端这些 message 本身就是给用户看的中文（如「宠物大类不合法，可选值：猫、狗…」），
 * 所以优先透传 message，只对少数需要引导的码做加工。
 */
export function petErrorMessage(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.isUnauthorized) return '登录已过期，请重新登录'
    if (e.code === 'B000800') return e.message || '宠物数量已达当前档位上限，开通会员可提升'
    if (e.code === 'B000801') return '会员权益服务暂时不可用，稍后再试（不是你操作的问题）'
    if (e.code === 'NETWORK') return e.message
    return e.message || '操作失败，请稍后再试'
  }
  return e instanceof Error ? e.message : '操作失败，请稍后再试'
}
