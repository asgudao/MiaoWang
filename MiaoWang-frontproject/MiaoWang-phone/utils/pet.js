/**
 * 宠物资料的展示工具（手机端）
 *
 * 与 Web 端 src/utils/pet.ts 是同一套规则的镜像，改一边记得同步另一边。
 */

/** 后端不校验名字，历史数据里有空名宠物 —— 统一兜底，别显示空白 */
export function petDisplayName (pet) {
	const n = pet && pet.pName && String(pet.pName).trim()
	return n || '未命名宠物'
}

export function needsName (pet) {
	return !(pet && pet.pName && String(pet.pName).trim())
}

/**
 * 按出生日期算年龄。
 *
 * 后端只回日期（YYYY-MM-DD，不含时区），所以拼 T00:00:00 再解析，
 * 否则 iOS 上 `new Date('2021-05-01')` 会被当 UTC、可能差一天。
 */
export function ageLabel (birth) {
	if (!birth) return '生日未填'
	const d = new Date(birth + 'T00:00:00')
	if (isNaN(d.getTime())) return '生日未填'
	const now = new Date()
	if (d.getTime() > now.getTime()) return '生日还没到'

	let months = (now.getFullYear() - d.getFullYear()) * 12 + (now.getMonth() - d.getMonth())
	if (now.getDate() < d.getDate()) months -= 1

	if (months < 1) {
		const days = Math.max(0, Math.floor((now.getTime() - d.getTime()) / 86400000))
		return days <= 0 ? '刚出生' : days + ' 天'
	}
	const years = Math.floor(months / 12)
	const rest = months % 12
	if (years < 1) return rest + ' 个月'
	return rest ? years + ' 岁 ' + rest + ' 个月' : years + ' 岁'
}

export function breedLabel (pet) {
	if (!pet) return ''
	const s = pet.specificSpecies && String(pet.specificSpecies).trim()
	return s ? pet.species + ' ' + s : pet.species || ''
}

export function genderLabel (gender) {
	if (gender === 0) return '雌性'
	if (gender === 1) return '雄性'
	return '未填'
}

export function sterilizedLabel (v) {
	if (v === 0) return '未绝育'
	if (v === 1) return '已绝育'
	return '未填'
}

export function initials (pet) {
	if (!pet) return '宠'
	const n = pet.pName && String(pet.pName).trim()
	if (n) return n.slice(0, 1)
	return pet.species ? String(pet.species).slice(0, 1) : '宠'
}

/**
 * 宠物接口的错误文案。
 *
 * 后端这些 message 本身就是给用户看的中文（如「宠物大类不合法，可选值：猫、狗…」），
 * 所以优先透传，只对少数需要引导的码做加工。
 */
export function petErrorMessage (e) {
	if (e && e.code) {
		if (e.isUnauthorized) return '登录已过期，请重新登录'
		if (e.code === 'B000800') return e.message || '宠物数量已达当前档位上限，开通会员可提升'
		if (e.code === 'B000801') return '会员权益服务暂时不可用，稍后再试（不是你操作的问题）'
		if (e.code === 'NETWORK') return e.message
		return e.message || '操作失败，请稍后再试'
	}
	return (e && e.message) || '操作失败，请稍后再试'
}