/**
 * 宠物档案接口（Web）
 *
 * 契约已用真调用验证（tools/probe-pet.js），不是照 DTO 猜的：
 * - GET    /pet            读我的宠物（身份由后端从 JWT 取 uid，前端只带 token/username 头）
 * - GET    /pet/user/{uid} 公开读某人的宠物（社区个人主页用）
 * - POST   /pet            新建，**直接返回带 pid 的完整对象**，不用二次查询
 * - PUT    /pet/{pid}      改名/改资料，data 为 null
 * - DELETE /pet/{pid}      逻辑删除，data 为 null
 *
 * 实测细节：
 * - `birth` 是 `"2021-05-01"` 这样的字符串（后端 LocalDate，只到日）
 * - `species` 存的是**中文大类**（猫/狗/蛇/兔/鸟/仓鼠/龟/鱼/其他），不是枚举名 CAT/DOG
 * - 大类非法 / 缺失 / 越权改别人的 pid 都是 `A000001` + 中文 message（message 可直接展示）
 * - 数量上限错误码是 `B000800`，权益服务不可用是 `B000801`
 */
import { api } from './request'

/** 大类白名单：与后端 PetSpeciesEnum 的 value 一一对应（是中文！别写成 CAT/DOG） */
export const PET_SPECIES = ['猫', '狗', '蛇', '兔', '鸟', '仓鼠', '龟', '鱼', '其他'] as const
export type PetSpecies = (typeof PET_SPECIES)[number]

export interface Pet {
  pid: number
  /** ⚠️ 后端未校验名字，历史数据里可能是 null（库里已有这种无名宠物），前端要兜底显示 */
  pName: string | null
  species: PetSpecies | string
  specificSpecies: string | null
  ownerCallName: string | null
  /** YYYY-MM-DD */
  birth: string | null
  /** 0=雌性 1=雄性 */
  gender: number | null
  /** 0=未绝育 1=已绝育 */
  sterilizedState: number | null
}

/** 新建/修改的请求体；修改时字段不传表示不改动 */
export interface PetUpsertBody {
  pName: string
  species: string
  specificSpecies?: string
  ownerCallName?: string
  birth?: string
  gender?: number
  sterilizedState?: number
}

export function listMyPets() {
  return api.get<Pet[]>('pet', '/pet')
}

export function listPetsByUid(uid: number) {
  return api.get<Pet[]>('pet', `/pet/user/${uid}`)
}

export function createPet(body: PetUpsertBody) {
  return api.post<Pet>('pet', '/pet', { body })
}

export function updatePet(pid: number, body: Partial<PetUpsertBody>) {
  return api.put<void>('pet', `/pet/${pid}`, { body })
}

export function deletePet(pid: number) {
  return api.del<void>('pet', `/pet/${pid}`)
}
