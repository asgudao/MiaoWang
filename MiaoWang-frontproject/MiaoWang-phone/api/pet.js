/**
 * 宠物档案接口（手机端）
 *
 * 契约已用真调用验证（tools/probe-pet.js），与 Web 端 src/api/pet.ts 一一对应：
 * - GET    /pet            读我的宠物（身份由后端从 JWT 取 uid）
 * - GET    /pet/user/{uid} 公开读某人的宠物
 * - POST   /pet            新建，**直接返回带 pid 的完整对象**
 * - PUT    /pet/{pid}      改资料，data 为 null
 * - DELETE /pet/{pid}      逻辑删除，data 为 null
 *
 * 实测细节：`birth` 是 "2021-05-01" 字符串；`species` 是**中文大类**（不是 CAT/DOG）；
 * 大类非法/缺失/越权都是 A000001 + 中文 message；数量上限是 B000800，权益服务不可用是 B000801。
 */
import { api } from '../utils/request.js'

/** 大类白名单：与后端 PetSpeciesEnum 的 value 一致（是中文！） */
export const PET_SPECIES = ['猫', '狗', '蛇', '兔', '鸟', '仓鼠', '龟', '鱼', '其他']

export function listMyPets () {
	return api.get('pet', '/pet')
}

export function listPetsByUid (uid) {
	return api.get('pet', '/pet/user/' + uid)
}

export function createPet (body) {
	return api.post('pet', '/pet', { body: body })
}

export function updatePet (pid, body) {
	return api.put('pet', '/pet/' + pid, { body: body })
}

export function deletePet (pid) {
	return api.del('pet', '/pet/' + pid)
}