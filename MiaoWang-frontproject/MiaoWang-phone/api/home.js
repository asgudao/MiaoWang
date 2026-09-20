/**
 * 首页数据（手机端）
 *
 * 与 Web 端 `src/api/home.ts` **同一口径**，改一边必须同步另一边：
 *
 * - **宠物档案**：真接口（`miaowang-pet`）的 `GET /pet`
 * - **照护提醒 / 本周记录**：**全部由健康记录派生**（`miaowang-healthy`）。后端**没有任何提醒接口**
 *   （143 处 controller mapping 里没有 reminder/plan/schedule/task；7 条不存在性探针全 `B000001`，
 *   因为 `GlobalExceptionHandler` 把 404/405 一并兜底 → **别靠 404 判断接口在不在**）。
 *   详见 `.dsh-tmp/reminder-notification-contract.md` §A
 *
 * 派生口径两条（页面不许另算一套）：
 * 1. **待办 = 逾期 + 今天到期**（`nextDueDays < 0` / `=== 0`）。后端**没有打卡表** →
 *    "今天做了几项 / 完成率"没有分子分母 → 本文件**不产出任何 Done 字段**，
 *    只有 `todoCount`（待办）与 `overdueCount`（逾期）。
 * 2. **周节奏 = 每天写了多少条健康记录**（`timeline` 条数），**不是照护完成率** → 字段叫 `count`。
 *
 * 换算一律复用 `utils/health.js` 里**已被健康门禁脚本覆盖**的那套纯函数
 * （`dueInfo` / `sortedByDue` / `urgentItem` / `parseHealthTime`），不另写分档。
 *
 * ⚠️ 实测钉死的一条：`overview.nextPreventive` **排除逾期项**（源码 `.ge(nextDueAt, now)`，挂账 #87）→
 * 最紧急的一条**必须**自己对 `preventive` 全量排序（`urgentItem`），拿 overview 会漏掉逾期。
 *
 * ⚠️ 会员权益 `reminderLimit`（PET_49=3 / AI_149 起 -1）在"前端派生"方案里**无处可挂**
 * （派生出来的提醒不是"用户创建的提醒条数"）→ 本切片**暂定不管它**（不显示上限、不做拦截）。
 */
import { ageLabel, breedLabel, initials, petDisplayName } from '../utils/pet.js'
import { auth } from '../utils/request.js'
import { listMyPets } from './pet.js'
import { listMedication, listPreventive, listWeight, timeline as listTimeline } from './health.js'
import { dueInfo, parseHealthTime, sortedByDue, urgentItem } from '../utils/health.js'
import { readSetting } from '../utils/remind-setting.js'
import { isWithinWindow } from '../utils/remind-schedule.js'

// 软件内弹窗的入口也挂在这里（`api/remind.js` 是它的实现），**只做转发**。
// 为什么转发而不是让页面直接 import `@/api/remind.js`：`pages/index/index.vue` 与 `App.vue`
// 都已经/将要 import 本文件，多取几个具名导出**不需要新增 import 语句**
// （本工程是 HBuilderX 工程，页面里的 `@/` 路径没有打包器兜底，少一处新路径就少一处风险）。
export {
  closeRemindPopup,
  fetchRemindPopup,
  invalidateRemindPopup,
  markPopupShown,
  remindPopupState
} from './remind.js'

/**
 * 演示数据开关：**默认关**（照护提醒走真数据）。
 *
 * ⚠️ 语义已改成真正的开关：关着的时候 `fetchHomeOverview()` **一个编造的照护项都不返回**；
 * 要演示就 `setUseMock(true)`（HBuilderX 工程没有 `import.meta.env`，所以开关是
 * 模块内可变标记 + 这个 setter，而不是打包期环境变量）。
 */
let mockEnabled = false
export function setUseMock (on) {
	mockEnabled = !!on
}
export function isUseMock () {
	return mockEnabled
}

export const kindLabel = (k) => ({
	vaccine: '疫苗',
	deworm: '驱虫',
	med: '用药'
}[k] || '照护')

/** 排序用的"无穷大"：用药没有到期时间，排在所有有到期时间的项之后 */
const FAR = Infinity

/**
 * 演示数据（**只有 `setUseMock(true)` 才会返回**，页面会标注"演示数据"）。
 *
 * 形状与真实数据完全一致（含 `source`），所以模板只有一套；
 * 类别只用真有数据源的三类（疫苗 / 驱虫 / 用药），不再编 feed/water/walk。
 * `chat` / `articles` **保留**：只有演示模式会渲染它们（真数据模式下这两块没有数据源，
 * 页面按空数组不渲染，绝不摆假气泡）。
 */
const MOCK = {
	source: 'mock',
	ownerName: '铲屎官',
	todoCount: 1,
	overdueCount: 1,
	tasks: [
		{ id: 'demo:preventive#2', title: '大宠爱（体外驱虫）', kind: 'deworm', petName: '汪汪', dueLabel: '已逾期 1 天', status: 'overdue', dueRank: -1 },
		{ id: 'demo:preventive#1', title: '猫三联', kind: 'vaccine', petName: '喵喵', dueLabel: '还有 23 天', status: 'upcoming', dueRank: 23 },
		{ id: 'demo:medication#1', title: '速诺（阿莫西林克拉维酸钾）', kind: 'med', petName: '喵喵', dueLabel: '用药中', status: 'active', dueRank: FAR }
	],
	pets: [
		{
			pid: 'p1', name: '喵喵', breed: '英短蓝猫', ageLabel: '2 岁 3 个月',
			initial: '喵', demo: true, weight: 4.2, weightDelta: -0.1,
			nextCare: '猫三联还有 23 天', nextTone: 'ok'
		},
		{
			pid: 'p2', name: '汪汪', breed: '柯基', ageLabel: '1 岁 7 个月',
			initial: '汪', demo: true, weight: 11.6, weightDelta: 0.3,
			nextCare: '体外驱虫已逾期 1 天', nextTone: 'danger'
		}
	],
	rhythm: [
		{ label: '一', count: 4 },
		{ label: '二', count: 3 },
		{ label: '三', count: 4 },
		{ label: '四', count: 2 },
		{ label: '五', count: 4 },
		{ label: '六', count: 4 },
		{ label: '日', count: 1, isToday: true }
	],
	degradedPets: [],
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

/* ==================================================================
 * 纯函数（与 Web 端 home.ts 一一对应；页面只消费结果，不自己算）
 * ================================================================== */

/** 最近 7 天的**记录条数合计**（周节奏图的标题数字；不是完成率） */
export function rhythmTotal (days) {
	const list = Array.isArray(days) ? days : []
	return list.reduce(function (n, d) { return n + Number((d && d.count) || 0) }, 0)
}

const WEEK_LABELS = ['日', '一', '二', '三', '四', '五', '六']

function pad2 (n) {
	return n < 10 ? '0' + n : String(n)
}

/** 本地日期键 `yyyy-MM-dd`（后端时间串是 GMT+8 本地、非 ISO，必须走 parseHealthTime 再取本地年月日） */
function dayKey (d) {
	return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate())
}

/** `2026-09-20 08:00:00` → `09-20`（宠物卡上"哪天量的"）；解析不出来时返回空串 */
export function dayText (value) {
	const d = parseHealthTime(value)
	return d ? pad2(d.getMonth() + 1) + '-' + pad2(d.getDate()) : ''
}

/**
 * 预防记录的类别：`VACCINE` → 疫苗，其余（`DEWORM_IN` / `DEWORM_OUT` / 未知）→ 驱虫。
 * 未知值不编第四类 —— 后端枚举只有三种。
 */
export function preventiveKind (type) {
	return String(type || '').toUpperCase() === 'VACCINE' ? 'vaccine' : 'deworm'
}

/** `dueInfo()` 的分档 → 照护项状态（`none` 不参与：没有到期时间就没有提醒） */
const TONE_STATUS = {
	overdue: 'overdue',
	today: 'today',
	soon: 'upcoming',
	later: 'upcoming'
}

/** `dueInfo()` 的分档 → 卡片色档（逾期红、今天与 7 天内琥珀、更远中性） */
export function nextCareTone (r) {
	if (!r) return ''
	const key = dueInfo(r.nextDueDays, r.nextDueAt).key
	if (key === 'overdue') return 'danger'
	if (key === 'today' || key === 'soon') return 'warn'
	if (key === 'later') return 'ok'
	return ''
}

/** 宠物卡上的"下一件照护事项"：`itemName` + 分档文案（实测样本文案「体外驱虫已逾期 10 天」） */
export function nextCareText (r) {
	if (!r) return ''
	const info = dueInfo(r.nextDueDays, r.nextDueAt)
	if (info.key === 'none') return ''
	return String(r.itemName || r.typeDesc || '照护项').trim() + info.label
}

/**
 * 一只宠物的照护项（**纯函数**，便于离线断言）。
 *
 * - `preventive`：**只有 `nextDueDays` 能判定的才产出**（`nextDueAt` 为空 → 不编）；
 *   顺序用 `sortedByDue()`：逾期最久的排最前（负数升序）
 * - `medication`：只有 `status === 0`（用药中）算"正在进行的照护"；它**没有到期时间** →
 *   排在所有有到期时间的项之后，也**不计入待办**
 */
export function buildCareTasks (pid, petName, preventive, medication) {
	const tasks = []
	const rows = sortedByDue(Array.isArray(preventive) ? preventive : [])
	for (let i = 0; i < rows.length; i++) {
		const r = rows[i] || {}
		const info = dueInfo(r.nextDueDays, r.nextDueAt)
		if (info.key === 'none') continue
		tasks.push({
			id: pid + ':preventive#' + r.id,
			title: String(r.itemName || r.typeDesc || '照护项').trim(),
			kind: preventiveKind(r.type),
			petName: petName,
			dueLabel: info.label,
			status: TONE_STATUS[info.key],
			dueRank: typeof info.days === 'number' ? info.days : FAR
		})
	}
	const meds = Array.isArray(medication) ? medication : []
	for (let j = 0; j < meds.length; j++) {
		const m = meds[j] || {}
		if (m.status !== 0) continue
		tasks.push({
			id: pid + ':medication#' + m.id,
			title: String(m.medicineName || '用药').trim(),
			kind: 'med',
			petName: petName,
			dueLabel: m.statusDesc || '用药中',
			status: 'active',
			dueRank: FAR
		})
	}
	return tasks
}

/** 跨宠物合并后的排序：逾期最久 → 今天 → 越快到期的越前 → 用药（无到期）最后；同档按 id 稳定 */
export function sortCareTasks (tasks) {
	const list = (Array.isArray(tasks) ? tasks : []).slice()
	return list.sort(function (a, b) {
		if (a.dueRank !== b.dueRank) return a.dueRank - b.dueRank
		return a.id < b.id ? -1 : a.id > b.id ? 1 : 0
	})
}

/**
 * 最新一次体重：**按 `measuredAt` 取最大，不假设列表顺序**。
 * 全部解析不出来时返回第一条（或 null），不编数字。
 */
export function latestWeight (list) {
	const rows = Array.isArray(list) ? list : []
	let best = null
	let bestAt = -Infinity
	for (let i = 0; i < rows.length; i++) {
		const w = rows[i] || {}
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
 * ⚠️ 数的是"记录活跃度"，**不是"照护完成率"** —— 后端没有打卡历史。
 * `now` 可注入：fixture 是 2026-09-20 抓的，离线断言不能依赖"今天"。
 */
export function buildRhythm (timelines, now) {
	const at = now instanceof Date && !isNaN(now.getTime()) ? now : new Date()
	const days = []
	const index = {}
	for (let i = 6; i >= 0; i--) {
		const d = new Date(at.getFullYear(), at.getMonth(), at.getDate() - i)
		const item = { label: WEEK_LABELS[d.getDay()], count: 0 }
		if (i === 0) item.isToday = true
		index[dayKey(d)] = days.length
		days.push(item)
	}
	const rows = Array.isArray(timelines) ? timelines : []
	for (let j = 0; j < rows.length; j++) {
		const t = parseHealthTime(rows[j] && rows[j].happenedAt)
		if (!t) continue
		const pos = index[dayKey(t)]
		if (pos !== undefined) days[pos].count = days[pos].count + 1
	}
	return days
}

/** 没有宠物档案 / 未登录时的空概览：**一个数字都不编**；周节奏仍给 7 天（全 0），免得柱图跳版 */
export function emptyOverview (now) {
	// ⚠️ `remindOff` **不看本机设置**：这里没登录（或没有宠物），"提醒关着"和"没数据"是两件事，
	// 空态文案不能把后者说成前者。页面只在 `source === 'real'` 且 `pets.length` 时才看这个字段。
	return {
		source: 'real',
		ownerName: '铲屎官',
		todoCount: 0,
		overdueCount: 0,
		tasks: [],
		remindOff: false,
		remindAheadDays: 0,
		pets: [],
		rhythm: buildRhythm([], now || new Date()),
		degradedPets: [],
		chat: [],
		articles: []
	}
}

/**
 * 真实宠物档案 → 首页卡片。
 *
 * 只搬 pet 接口真有的字段；**体重与下一件照护事项不在这个函数里** ——
 * 它们来自健康记录，由 `fetchHomeOverview()` 用 `latestWeight()` / `nextCareText()` 补上
 * （没有记录就整个字段不产出，页面不渲染，绝不编一个体重）。
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

/** 当前有没有登录（健康接口经网关全都要身份，未登录就不发请求、也不编数据） */
function loggedIn () {
	return !!(auth.getUsername() && auth.getToken())
}

/**
 * 一只宠物的四类健康数据（`failures` = 4 个请求里失败几个）。
 *
 * ⚠️ healthy **没有跨宠物聚合接口**（挂账 #82）→ 多宠物就是 `1 + 4N` 个请求
 * （免费 2 只 / 会员 5 只 → 最多 21 个）。四个请求**并发**发出，
 * 单项失败只记进 `failures`（不用 `Promise.all`：一个失败不该拖垮其它三项，
 * 也不用 `Promise.allSettled`：老安卓 WebView 上不一定有）。
 */
function loadPetHealth (pid) {
	const jobs = [listPreventive(pid), listMedication(pid), listWeight(pid), listTimeline(pid)]
	const settled = jobs.map(function (job) {
		return job.then(
			function (value) { return { ok: true, value: Array.isArray(value) ? value : [] } },
			function () { return { ok: false, value: [] } }
		)
	})
	return Promise.all(settled).then(function (rs) {
		let failures = 0
		for (let i = 0; i < rs.length; i++) if (!rs[i].ok) failures++
		return {
			pid: pid,
			preventive: rs[0].value,
			medication: rs[1].value,
			weight: rs[2].value,
			timeline: rs[3].value,
			failures: failures
		}
	})
}

/**
 * 首页概览（真数据）。
 *
 * - `setUseMock(true)` → 演示数据（`source: 'mock'`）
 * - 未登录 → **一个请求都不发、也不编数据** → 空概览
 * - 已登录 → `GET /pet` + 每只宠物 4 个健康接口（并发）→ 纯函数拼装
 *
 * 失败降级分两层：`/pet` 失败会**抛出**（页面进错误态 + 重试）；
 * 某只宠物的健康数据全失败时，只把它记进 `degradedPets`（页面如实说明），其余宠物照常。
 */
export function fetchHomeOverview () {
	if (mockEnabled) {
		return new Promise(function (resolve) {
			setTimeout(function () { resolve(MOCK) }, 240)
		})
	}
	if (!loggedIn()) return Promise.resolve(emptyOverview())

	// 本机「提醒设置」（`utils/remind-setting.js` 是唯一真源，页面才写它）：
	//   ① 提前 N 天 → 窗口内还没到期的项**也计入待办**（默认 0 天 ≡ 原来的「逾期 + 今天到期」）
	//   ② 总开关关掉 → 首页一条照护项都不列（不是"没有照护项"，页面文案会说明）
	// 两个影响都落在真实调用路径上：`todoCount` / `tasks` / 宠物卡的 `nextCare` 都会跟着变。
	const remindSetting = readSetting(auth.getUsername())
	const remindOff = remindSetting.enabled === false
	const remindAheadDays = remindSetting.aheadDays

	return listMyPets().then(function (pets) {
		if (!pets || !pets.length) return emptyOverview()
		const briefs = pets.map(petBriefFromApi)
		const jobs = pets.map(function (p) { return loadPetHealth(p.pid) })
		return Promise.all(jobs).then(function (health) {
			const byPid = {}
			const timelines = []
			for (let i = 0; i < health.length; i++) {
				byPid[String(health[i].pid)] = health[i]
				for (let t = 0; t < health[i].timeline.length; t++) timelines.push(health[i].timeline[t])
			}
			const tasks = []
			const degradedPets = []
			const enriched = []
			let earlyTodo = 0
			for (let j = 0; j < briefs.length; j++) {
				const brief = briefs[j]
				const h = byPid[brief.pid]
				if (!h || h.failures === 4) {
					// 这只宠物的健康数据一条都没读到：只显示档案，照护项**不猜**
					degradedPets.push(brief.name)
					enriched.push(Object.assign({}, brief, { degraded: true }))
					continue
				}
				tasks.push.apply(tasks, buildCareTasks(brief.pid, brief.name, h.preventive, h.medication))
				// 提前 N 天：窗口内还没到期的项也计入待办。口径**不另造** —— 直接问
				// `utils/remind-schedule.js` 的 `isWithinWindow`（它内部走 `dueInfo()` 同一真源）。
				// 默认 `aheadDays = 0` 时，这里恒等于"逾期 + 今天到期"，所以既有行为一字不变。
				const preventiveRows = Array.isArray(h.preventive) ? h.preventive : []
				for (let r = 0; r < preventiveRows.length; r++) {
					const days = dueInfo(preventiveRows[r].nextDueDays, preventiveRows[r].nextDueAt).days
					if (typeof days === 'number' && days > 0 && isWithinWindow(days, remindAheadDays)) earlyTodo++
				}
				const urgent = urgentItem(h.preventive)
				const care = nextCareText(urgent)
				const latest = latestWeight(h.weight)
				const card = Object.assign({}, brief)
				if (latest) {
					card.weight = latest.weightKg
					card.weightAt = dayText(latest.measuredAt)
				}
				// 总开关关掉 → 宠物卡上的"下一件照护事项"也不再显示（它同样是派生出来的提醒标记）
				if (care && !remindOff) {
					card.nextCare = care
					card.nextTone = nextCareTone(urgent) || 'ok'
				}
				enriched.push(card)
			}
			const sorted = sortCareTasks(tasks)
			let todo = 0
			let overdue = 0
			for (let k = 0; k < sorted.length; k++) {
				if (sorted[k].status === 'overdue') { overdue++; todo++ } else if (sorted[k].status === 'today') { todo++ }
			}
			if (!remindOff) todo += earlyTodo
			if (remindOff) {
				todo = 0
				overdue = 0
			}
			return {
				source: 'real',
				ownerName: '铲屎官',
				todoCount: todo,
				overdueCount: overdue,
				tasks: remindOff ? [] : sorted,
				remindOff: remindOff,
				remindAheadDays: remindAheadDays,
				pets: enriched,
				rhythm: buildRhythm(timelines),
				degradedPets: degradedPets,
				chat: [],
				articles: []
			}
		})
	})
}