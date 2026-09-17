/**
 * 首页数据（手机端）
 *
 * 两类数据来源不同，**不要混在一起**（与 Web 端 src/api/home.ts 同一口径）：
 * - **宠物档案**：真接口（`miaowang-pet`）。登录后由 `petBriefFromApi()` 把真档案映射成卡片形状，
 *   数据本身走 `store/pets.js`（有 loaded 缓存），首页不自己再发一次请求
 * - **照护提醒 / 周节奏 / 陪伴天数**：后端的提醒与健康记录模块还没就绪 → 仍是演示数据，
 *   界面上会明确标注，绝不把 mock 当真实数据展示
 *
 * 演示数据里的体重、下一件照护事项是 pet 接口**没有**的字段：
 * 所以真实档案映射出来的卡片（`demo: false`）不带这两块，页面按 `demo` 决定渲不渲染，
 * 而不是给真档案编一个体重。
 */
import { ageLabel, breedLabel, initials, petDisplayName } from '../utils/pet.js'

export const USE_MOCK = true

export const kindLabel = (k) => ({
	feed: '喂食',
	water: '饮水',
	walk: '运动',
	med: '用药',
	vaccine: '疫苗'
}[k] || '照护')

const MOCK = {
	ownerName: '铲屎官',
	companionDays: 128,
	todayDone: 1,
	todayTotal: 4,
	tasks: [
		{ id: 't1', title: '早餐', kind: 'feed', petName: '喵喵', dueLabel: '已完成', status: 'done' },
		{ id: 't2', title: '换水', kind: 'water', petName: '喵喵', dueLabel: '2 小时后', status: 'todo' },
		{ id: 't3', title: '遛弯 30 分钟', kind: 'walk', petName: '汪汪', dueLabel: '1 小时后', status: 'todo' },
		{ id: 't4', title: '驱虫药', kind: 'med', petName: '汪汪', dueLabel: '已逾期 1 天', status: 'overdue' }
	],
	pets: [
		{
			pid: 'p1', name: '喵喵', species: 'cat', breed: '英短蓝猫', ageLabel: '2 岁 3 个月',
			initial: '喵', demo: true, weight: 4.2, weightDelta: -0.1,
			nextCare: '疫苗加强针还有 23 天', nextTone: 'ok'
		},
		{
			pid: 'p2', name: '汪汪', species: 'dog', breed: '柯基', ageLabel: '1 岁 7 个月',
			initial: '汪', demo: true, weight: 11.6, weightDelta: 0.3,
			nextCare: '体内驱虫已逾期 1 天', nextTone: 'danger'
		}
	],
	rhythm: [
		{ label: '一', done: 4, total: 4 },
		{ label: '二', done: 3, total: 4 },
		{ label: '三', done: 4, total: 4 },
		{ label: '四', done: 2, total: 4 },
		{ label: '五', done: 4, total: 4 },
		{ label: '六', done: 4, total: 4 },
		{ label: '日', done: 1, total: 4, isToday: true }
	],
	chat: [
		{ from: 'pet', text: '早上好呀，我今早的饭盆空了。你昨天说今天要早起，是真的起来了吗？' },
		{ from: 'me', text: '真的起了，给你加了半勺罐头。' },
		{ from: 'pet', text: '那我今天会乖一点。顺便提醒你，我的驱虫药已经晚了一天。' }
	],
	articles: [
		'猫咪应激的 6 个信号',
		'换粮到底要不要过渡',
		'狗狗一年该打哪些疫苗'
	]
}

/** 最近 7 天完成率（0-100） */
export function rhythmRate (days) {
	const done = days.reduce((n, d) => n + d.done, 0)
	const total = days.reduce((n, d) => n + d.total, 0)
	return total ? Math.round((done / total) * 100) : 0
}

/**
 * 真实宠物档案 → 首页卡片。
 *
 * 只搬运真有的字段：体重、下一件照护事项属于健康/提醒模块，接口还没有 → 留空（页面不渲染），
 * 空名字用「未命名宠物」兜底（后端不校验名字，库里已有空名数据）。
 * 与 Web 端 home.ts 的 petBriefFromApi() 一一对应，改一边记得同步另一边。
 */
export function petBriefFromApi (p) {
	const call = p && p.ownerCallName && String(p.ownerCallName).trim()
	return {
		pid: String(p.pid),
		name: petDisplayName(p),
		breed: breedLabel(p),
		ageLabel: ageLabel(p.birth),
		initial: initials(p),
		demo: false,
		callName: call || ''
	}
}

/**
 * 首页概览。
 *
 * 注意：`pets` 字段返回的**永远是演示数据** —— 真实档案由页面从 store/pets.js 取，
 * 这样职责清晰：这个函数只负责"后端还没有接口的那些部分"。
 */
export function fetchHomeOverview () {
	if (USE_MOCK) {
		return new Promise((resolve) => setTimeout(() => resolve(MOCK), 240))
	}
	// TODO 待后端提供：照护提醒接口 + 健康记录接口（宠物档案已接，见 petBriefFromApi）
	return Promise.resolve(MOCK)
}