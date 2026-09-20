<template>
	<view class="hpage">
		<view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav__back" @click="goBack"><view class="chev chev--left"></view></view>
			<text class="nav__title">健康档案</text>
			<view class="nav__spacer"></view>
		</view>

		<!-- 未登录 / 会话过期：/health/** 整个前缀经网关都要登录（匿名请求被拦成 state:-1） -->
		<view v-if="showGate" class="state">
			<text class="state__title">{{ sessionExpired ? '登录已过期' : '健康档案要登录才能看' }}</text>
			<text class="state__desc">{{ gateDesc }}</text>
			<view class="state__btn" @click="goLogin"><text>去登录</text></view>
			<view class="state__ghost" @click="retry"><text>我已经登录了，重新读取</text></view>
		</view>

		<template v-else>
			<!-- 首屏骨架 -->
			<view v-if="firstLoading" class="skeleton">
				<view class="skeleton__card"></view>
				<view class="skeleton__card"></view>
				<view class="skeleton__card"></view>
			</view>

			<template v-else>
				<!-- 概览没读到：说清原因 + 可重试，绝不白屏 -->
				<view v-if="!view" class="state">
					<text class="state__title">健康档案没读到</text>
					<text class="state__desc">{{ overviewError || '接口这次没有返回概览数据' }}</text>
					<view class="state__btn" @click="retry"><text>重试</text></view>
				</view>

				<template v-else>
					<!-- 部分模块失败：行内报错 + 重试，不把已经读到的东西换成错误页 -->
					<view v-for="e in errorList" :key="e.key" class="inline-err">
						<text class="inline-err__text">{{ e.text }}</text>
						<text class="inline-err__act" @click="retry">重试</text>
					</view>

					<view class="hero">
						<view class="hero__top">
							<text class="hero__name">{{ view.petName }}</text>
							<text class="hero__species">{{ view.species }}</text>
						</view>
						<text class="hero__total">{{ view.totalText }}</text>
						<view class="hero__weight">
							<view class="hero__wcol">
								<text class="hero__wlabel">最新体重</text>
								<text class="hero__wvalue">{{ latestWeightText }}</text>
							</view>
							<view class="hero__wcol">
								<text class="hero__wlabel">30 天变化</text>
								<text class="hero__wvalue hero__wvalue--small" :class="'tone--' + view.weightChangeTone">{{ view.weightChangeText }}</text>
							</view>
						</view>
						<view class="hero__acts">
							<view class="hero__btn" @click="goWeight"><text>体重曲线</text></view>
							<view class="hero__btn" @click="goNew('weight')"><text>记一次体重</text></view>
						</view>
					</view>

					<!-- 今日照护：⚠️ overview.nextPreventive 排除了逾期项，这里是前端拉全量 preventive 自己按 nextDueDays 算的 -->
					<view class="hc">
						<view class="hc__head">
							<text class="hc__title">接下来要做</text>
							<text class="hc__act" @click="goPreventive">全部 {{ preventiveCount }} 条</text>
						</view>
						<text class="hc__dim">{{ careBasisText }}</text>

						<view v-if="careCards.length" class="care">
							<view v-for="c in careCards" :key="c.key" class="care__item" @click="goEdit('preventive', c.id)">
								<view class="care__main">
									<text class="care__name">{{ c.itemName }}</text>
									<text class="care__when">{{ c.typeLabel }} {{ c.nextDueText }}</text>
								</view>
								<text class="tag" :class="'tag--' + c.tagTone">{{ c.dueLabel }}</text>
							</view>
						</view>
						<text v-else class="hc__note">{{ careEmptyText }}</text>

						<view class="hc__head hc__head--gap">
							<text class="hc__title">在用药</text>
							<text class="hc__act" @click="goMedication">全部</text>
						</view>
						<view v-if="view.activeMedications.length" class="care">
							<view v-for="m in view.activeMedications" :key="m.key" class="care__item" @click="goEdit('medication', m.id)">
								<view class="care__main">
									<text class="care__name">{{ m.medicineName }}</text>
									<text class="care__when">{{ m.subText }}</text>
								</view>
								<text class="tag tag--brand">{{ m.statusLabel }}</text>
							</view>
						</view>
						<text v-else class="hc__note">没有在用的药（概览最多给 10 条，仅含「用药中」）</text>

						<view class="hc__head hc__head--gap">
							<text class="hc__title">过敏史</text>
							<text class="hc__act" @click="goAllergy">全部</text>
						</view>
						<view v-if="view.allergies.length" class="care">
							<view v-for="a in view.allergies" :key="a.key" class="care__item" @click="goEdit('allergy', a.id)">
								<view class="care__main">
									<text class="care__name">{{ a.allergen }}</text>
									<text class="care__when">{{ a.subText }}</text>
								</view>
								<text class="tag" :class="'tag--' + a.tagTone">{{ a.severityLabel }}</text>
							</view>
						</view>
						<text v-else class="hc__note">还没有记录过敏史。已知的过敏原写下来，换粮换药时能少踩坑</text>
					</view>

					<!-- 六类计数：点进去就是对应列表页 -->
					<view class="hc">
						<view class="hc__head">
							<text class="hc__title">六类记录</text>
							<text class="hc__act" @click="goTimeline">时间线</text>
						</view>
						<view class="tiles">
							<view v-for="t in view.counts" :key="t.key" class="tile" @click="goList(t.key)">
								<text class="tile__num">{{ t.value }}</text>
								<text class="tile__label">{{ t.label }}</text>
							</view>
						</view>
						<text class="hc__dim">{{ view.limitText }}</text>
						<view v-if="view.hasLimitWarning" class="warning">
							<text>{{ view.limitWarningText }}</text>
						</view>
					</view>

					<!-- 最近时间线 -->
					<view class="hc">
						<view class="hc__head">
							<text class="hc__title">最近动静</text>
							<text class="hc__act" @click="goTimeline">完整时间线</text>
						</view>
						<view v-if="recent.length" class="tl">
							<view v-for="t in recent" :key="t.key" class="tl__item">
								<view class="tl__dot"></view>
								<view class="tl__body">
									<text class="tl__title">{{ t.title }}</text>
									<text class="tl__meta">{{ t.recordTypeLabel }} {{ t.happenedText }}</text>
								</view>
							</view>
						</view>
						<text v-else class="hc__note">还没有任何记录。先从疫苗、体重或一条症状开始记，时间线就会有内容</text>
					</view>

					<!-- 两个付费墙入口：本账号档位不够，**把付费墙当正常状态**（明确升级提示，不当报错、不重试） -->
					<view class="hc">
						<view class="hc__head">
							<text class="hc__title">AI 解读症状</text>
							<text class="tag" :class="view.aiAnalysisAllowed ? 'tag--brand' : 'tag--muted'">
								{{ view.aiAnalysisAllowed ? '本档位可用' : '会员功能' }}
							</text>
						</view>
						<text class="hc__note">{{ aiIntroText }}</text>
						<view v-if="aiPaywall" class="notice"><text>{{ aiPaywall }}</text></view>
						<view v-if="aiError" class="inline-err inline-err--plain">
							<text class="inline-err__text">{{ aiError }}</text>
							<text class="inline-err__act" @click="onAi">重试</text>
						</view>
						<view v-if="aiResult" class="ai">
							<text class="ai__meta">{{ aiResult.metaText }}</text>
							<text class="ai__body">{{ aiResult.analysis }}</text>
							<view v-if="aiResult.sources.length" class="ai__sources">
								<text v-for="s in aiResult.sources" :key="s.key" class="tag tag--brand">{{ s.title }}</text>
							</view>
							<text v-if="aiResult.disclaimer" class="hc__dim">{{ aiResult.disclaimer }}</text>
						</view>
						<view class="ai__btn" @click="onAi"><text>{{ aiBusy ? '解读中' : '解读最近的症状' }}</text></view>
						<text class="hc__dim">{{ aiCostText }}</text>
					</view>

					<view class="hc">
						<view class="hc__head">
							<text class="hc__title">导出完整档案</text>
							<text class="tag" :class="view.exportAllowed ? 'tag--brand' : 'tag--muted'">
								{{ view.exportAllowed ? '本档位可用' : '会员功能' }}
							</text>
						</view>
						<text class="hc__note">{{ exportIntroText }}</text>
						<view v-if="exportPaywall" class="notice"><text>{{ exportPaywall }}</text></view>
						<view v-if="exportError" class="inline-err inline-err--plain">
							<text class="inline-err__text">{{ exportError }}</text>
							<text class="inline-err__act" @click="onExport">重试</text>
						</view>
						<view v-if="exportResult" class="ai">
							<text class="ai__meta">{{ exportResult.metaText }}</text>
							<text class="hc__dim">{{ exportResult.countsText }}</text>
						</view>
						<view class="ai__btn" @click="onExport"><text>{{ exportBusy ? '导出中' : '导出为结构化数据' }}</text></view>
					</view>

					<view class="newbar">
						<text class="newbar__label">记一条</text>
						<view class="newbar__grid">
							<view v-for="n in newTargets" :key="n.key" class="newbar__item" @click="goNew(n.key)">
								<text>{{ n.label }}</text>
							</view>
						</view>
					</view>

					<text class="foot">{{ sourceNote }}</text>
				</template>
			</template>
		</template>
	</view>
</template>

<script>
/**
 * 健康概览（手机端 / Options API）
 *
 * 契约与坑（全部来自实测，见 `.dsh-tmp/healthy-contract.md` 末节）：
 * - 概览是**对象**：`recordCounts` 的 6 个 key 恒在、`recordLimit` 可为 `-1`（不限）、
 *   `weightChangeKg` 可为 `null`（没有 30 天前基线时）
 * - ⚠️ **`overview.nextPreventive` 拿不到逾期项**（源码 `.ge(nextDueAt, now)`，实测只回未来那条）
 *   → 「接下来要做」这一段是**前端自己拉 `GET /{pid}/preventive` 全量**、按 `nextDueDays` 分档算的
 *   （`< 0` 已逾期、`0` 今天、`> 0` 还有 N 天；`nextDueAt` 为空时该字段是 `null`）。
 *   所以 `overview.nextPreventive` 这里**根本不用**（两套口径混着算必错）。
 * - 两个付费墙（`/export` `B000404`、`/ai-analysis` `B000411`）：本账号档位不够、成功形状未观测。
 *   入口照给，点了**真调一次**，把 `B000404` / `B000411` 当**正常状态**（升级提示），
 *   **不当报错、不给"重试"**（重试也不会变）。
 * - 时间一律走 `utils/health.js` 的 `parseHealthTime`（内部 `replace(' ','T')`，iOS 才不会 Invalid Date）。
 */
import { auth } from '@/store/auth.js'
import { ApiError } from '@/utils/request.js'
import * as healthApi from '@/api/health.js'
import {
	healthErrorMessage,
	isPaywall,
	overdueItems,
	overviewView,
	preventiveCard,
	sortedByDue,
	text,
	timelineCard
} from '@/utils/health.js'

/** 首屏只放最近 8 条动静（完整去时间线页，那里有 limit 与类型筛选） */
const RECENT_LIMIT = 8

export default {
	data() {
		return {
			pid: 0,
			statusBarHeight: 20,
			loading: false,
			overview: null,
			preventiveRows: [],
			timelineRows: [],
			/** 三块各自报错，互不掩盖（读到的内容不会被错误页顶掉） */
			errors: { overview: '', preventive: '', timeline: '' },
			unauthorized: false,
			aiBusy: false,
			aiPaywall: '',
			aiError: '',
			aiResult: null,
			exportBusy: false,
			exportPaywall: '',
			exportError: '',
			exportResult: null
		}
	},
	computed: {
		loggedIn() {
			return auth.isLoggedIn()
		},
		sessionExpired() {
			return auth.isLoggedIn() && this.unauthorized
		},
		showGate() {
			return !this.loggedIn || this.sessionExpired
		},
		gateDesc() {
			if (this.sessionExpired) return '本地的登录状态后端已经不认了，重新登录一次就能继续看。'
			return '健康档案的接口经过网关时一律要带登录身份（连症状库选项也一样），匿名请求会被直接拦掉。'
		},
		firstLoading() {
			return this.loading && !this.overview
		},
		overviewError() {
			return this.errors.overview
		},
		errorList() {
			const out = []
			if (this.errors.preventive) out.push({ key: 'preventive', text: '照护提醒没读到（概览本身是读到了的）：' + this.errors.preventive })
			if (this.errors.timeline) out.push({ key: 'timeline', text: '最近动静没读到：' + this.errors.timeline })
			return out
		},
		view() {
			if (!this.overview) return null
			// ⚠️ overdueRows 只放**逾期项**（overviewView 里的 overdueCount 直接用它算）；
			// 照护清单另用 sortedByDue 排全部（含未来到期）
			return overviewView(this.overview, {
				overdueRows: overdueItems(this.preventiveRows),
				urgentRow: null
			})
		},
		latestWeightText() {
			const v = this.view
			if (!v || !v.hasLatestWeight) return '还没记过'
			return v.latestWeight.weightText
		},
		/** 「接下来要做」用的卡片（逾期最久在前；每条带 tagTone 给标签上色） */
		careCards() {
			const sorted = sortedByDue(this.preventiveRows)
			const out = []
			for (let i = 0; i < sorted.length && i < 3; i++) {
				const c = preventiveCard(sorted[i])
				c.tagTone = c.isOverdue ? 'danger' : c.dueKey === 'today' || c.dueKey === 'soon' ? 'accent' : 'muted'
				out.push(c)
			}
			return out
		},
		preventiveCount() {
			return this.preventiveRows.length
		},
		careBasisText() {
			if (!this.preventiveRows.length) return '疫苗与驱虫一条都还没记，先记一条就有提醒了'
			return (
				'按「下次到期还有几天」排的（负数就是已逾期）。概览接口里的 nextPreventive 只给未来最近一条，' +
				'逾期项它不给，所以这一段是拉全量列表自己算的'
			)
		},
		careEmptyText() {
			return '列表里 ' + this.preventiveRows.length + ' 条都没有填「下次到期时间」，所以算不出提醒'
		},
		recent() {
			const out = []
			for (let i = 0; i < this.timelineRows.length && i < RECENT_LIMIT; i++) {
				out.push(timelineCard(this.timelineRows[i]))
			}
			return out
		},
		aiIntroText() {
			if (this.view && this.view.aiAnalysisAllowed) {
				return '把最近 10 条症状拼成问题交给模型解读，不消耗 AI 问答次数。结果只作参考，不能替代兽医面诊。'
			}
			return '当前档位不含这项功能。点下面的按钮会真调一次接口，后端闸门会回一条「档位不支持」的提示（这一条是正常状态，不是报错）。'
		},
		aiCostText() {
			return '解读会真的调用大模型：一次十几秒，只在点了按钮之后发生；后端从不缓存结果。'
		},
		exportIntroText() {
			if (this.view && this.view.exportAllowed) {
				return '导出这只宠物的完整档案：六类记录各有几类、每类的内容都会带上时间与备注。'
			}
			return '当前档位不含导出功能。点下面的按钮会真调一次接口，后端会回「档位不支持导出」的提示（正常状态，不是报错）。'
		},
		newTargets() {
			return [
				{ key: 'preventive', label: '疫苗驱虫' },
				{ key: 'medical', label: '就诊' },
				{ key: 'medication', label: '用药' },
				{ key: 'allergy', label: '过敏' },
				{ key: 'symptom', label: '症状' },
				{ key: 'weight', label: '体重' }
			]
		},
		sourceNote() {
			return '概览 / 疫苗驱虫 / 时间线三块各拉各的接口，哪一块失败只影响那一块，不会整页白屏。'
		}
	},
	onLoad(options) {
		try {
			const info = uni.getSystemInfoSync()
			this.statusBarHeight = info.statusBarHeight || 20
		} catch (e) {
			// 拿不到就用默认值
		}
		this.pid = Number((options && options.pid) || 0)
		this.loadAll()
	},
	onShow() {
		// 从新建/编辑页返回：列表可能变了，安静地重读一次（首屏已经读过才重读）
		if (this.loggedIn && this.overview && !this.loading) this.loadAll()
	},
	methods: {
		async loadAll() {
			if (!this.loggedIn) return
			this.unauthorized = false
			this.loading = true
			await Promise.all([this.loadOverview(), this.loadPreventive(), this.loadTimeline()])
			this.loading = false
		},

		async loadOverview() {
			if (!this.loggedIn) return
			this.errors.overview = ''
			try {
				this.overview = await healthApi.overview(this.pid)
			} catch (e) {
				if (this.noteAuth(e)) return
				this.errors.overview = healthErrorMessage(e)
			}
		},

		async loadPreventive() {
			if (!this.loggedIn) return
			this.errors.preventive = ''
			try {
				const rows = await healthApi.listPreventive(this.pid)
				this.preventiveRows = Array.isArray(rows) ? rows : []
			} catch (e) {
				if (this.noteAuth(e)) return
				this.errors.preventive = healthErrorMessage(e)
			}
		},

		async loadTimeline() {
			if (!this.loggedIn) return
			this.errors.timeline = ''
			try {
				const rows = await healthApi.timeline(this.pid, { limit: RECENT_LIMIT })
				this.timelineRows = Array.isArray(rows) ? rows : []
			} catch (e) {
				if (this.noteAuth(e)) return
				this.errors.timeline = healthErrorMessage(e)
			}
		},

		retry() {
			if (!this.loggedIn) {
				uni.navigateTo({ url: '/pages/login/login' })
				return
			}
			this.unauthorized = false
			this.loadAll()
		},

		goLogin() {
			if (this.unauthorized) auth.clearSession()
			uni.navigateTo({ url: '/pages/login/login' })
		},

		goBack() {
			uni.navigateBack({
				fail() {
					uni.switchTab({ url: '/pages/pets/pets' })
				}
			})
		},

		/** 六类计数与「记一条」都走这里：写页是按类型分开的 */
		goList(key) {
			const map = {
				preventive: '/pages/health/preventive',
				medical: '/pages/health/medical',
				medication: '/pages/health/medication',
				allergy: '/pages/health/allergy',
				symptom: '/pages/health/symptom',
				weight: '/pages/health/weight'
			}
			if (key === 'weight') {
				this.goWeight()
				return
			}
			const url = map[key]
			if (url) uni.navigateTo({ url: url + '?pid=' + this.pid })
		},

		goWeight() {
			uni.navigateTo({ url: '/pages/health/weight?pid=' + this.pid })
		},

		goPreventive() {
			uni.navigateTo({ url: '/pages/health/preventive?pid=' + this.pid })
		},

		goMedication() {
			uni.navigateTo({ url: '/pages/health/medication?pid=' + this.pid })
		},

		goAllergy() {
			uni.navigateTo({ url: '/pages/health/allergy?pid=' + this.pid })
		},

		goTimeline() {
			uni.navigateTo({ url: '/pages/health/timeline?pid=' + this.pid })
		},

		goNew(type) {
			uni.navigateTo({ url: '/pages/health/record-edit?pid=' + this.pid + '&type=' + type })
		},

		goEdit(type, id) {
			uni.navigateTo({ url: '/pages/health/record-edit?pid=' + this.pid + '&type=' + type + '&id=' + id })
		},

		async onAi() {
			this.aiError = ''
			this.aiPaywall = ''
			this.aiResult = null
			this.aiBusy = true
			try {
				const resp = await healthApi.aiAnalysis(this.pid)
				const d = resp || {}
				const sources = Array.isArray(d.sources) ? d.sources : []
				this.aiResult = {
					metaText:
						text(d.petName, '这只宠物') + ' 最近 ' + (Number(d.symptomCount) || 0) + ' 条症状，' +
						(d.hasKnowledge ? '知识库有相关内容' : '知识库这次没有命中内容'),
					analysis: text(d.analysis, '模型这次没有返回解读内容'),
					disclaimer: text(d.disclaimer),
					sources: sources.map(function (s, i) {
						return { key: 'src-' + i, title: text(s && s.title, '来源 ' + (i + 1)) }
					})
				}
			} catch (e) {
				if (this.noteAuth(e)) return
				// ⚠️ 付费墙是**正常状态**：给升级提示，不给"重试"（重试不会变）
				if (isPaywall(e)) this.aiPaywall = healthErrorMessage(e)
				else this.aiError = healthErrorMessage(e)
			} finally {
				this.aiBusy = false
			}
		},

		async onExport() {
			this.exportError = ''
			this.exportPaywall = ''
			this.exportResult = null
			this.exportBusy = true
			try {
				const d = (await healthApi.exportArchive(this.pid)) || {}
				const parts = []
				const list = [
					['preventives', '疫苗驱虫'],
					['medicals', '就诊'],
					['medications', '用药'],
					['allergies', '过敏'],
					['symptoms', '症状'],
					['weights', '体重']
				]
				for (let i = 0; i < list.length; i++) {
					const arr = d[list[i][0]]
					if (Array.isArray(arr)) parts.push(list[i][1] + ' ' + arr.length + ' 条')
				}
				this.exportResult = {
					metaText: text(d.petName, '这只宠物') + ' 的档案已导出，导出时间 ' + text(d.exportedAt, '未给'),
					countsText: parts.length ? parts.join('，') : '返回体里没有可识别的记录数组'
				}
			} catch (e) {
				if (this.noteAuth(e)) return
				if (isPaywall(e)) this.exportPaywall = healthErrorMessage(e)
				else this.exportError = healthErrorMessage(e)
			} finally {
				this.exportBusy = false
			}
		},

		/** 掉线：切回软门槛，不摆一堆红字 【返回 true 表示已处理】 */
		noteAuth(e) {
			if (!(e instanceof ApiError) || !e.isUnauthorized) return false
			this.unauthorized = true
			this.errors = { overview: '', preventive: '', timeline: '' }
			this.loading = false
			return true
		}
	}
}
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
@import '@/styles/health.scss';

/* ---- 深底 hero（夜幕舞台：主色只在这种深底上用） ---- */
.hero {
	margin: 0 $mw-page-padding $mw-space-4;
	padding: $mw-space-4;
	background: $mw-night;
	border-radius: $mw-radius-card;
}
.hero__top {
	display: flex;
	align-items: baseline;
}
.hero__name {
	font-size: 40rpx;
	font-weight: 700;
	color: #ffffff;
}
.hero__species {
	margin-left: $mw-space-2;
	font-size: $mw-font-caption;
	color: $mw-on-night-2;
}
.hero__total {
	display: block;
	margin-top: 4rpx;
	font-size: $mw-font-caption;
	color: $mw-on-night-2;
}
.hero__weight {
	display: flex;
	margin-top: $mw-space-3;
	padding-top: $mw-space-3;
	border-top: 1px solid $mw-night-line;
}
.hero__wcol {
	flex: 1;
	min-width: 0;
}
.hero__wlabel {
	display: block;
	font-size: 22rpx;
	color: $mw-on-night-2;
}
.hero__wvalue {
	display: block;
	margin-top: 4rpx;
	font-size: $mw-font-h2;
	font-weight: 700;
	color: $mw-brand;
}
.hero__wvalue--small {
	font-size: $mw-font-caption;
	font-weight: 600;
}
.tone--up { color: $mw-accent; }
.tone--down { color: $mw-brand; }
.tone--flat { color: $mw-on-night; }
.tone--none { color: $mw-on-night-2; }
.hero__acts {
	display: flex;
	margin-top: $mw-space-3;
}
.hero__btn {
	flex: 1;
	margin-right: $mw-space-2;
	min-height: $mw-touch-min;
	border: 1px solid $mw-night-line;
	border-radius: $mw-radius-pill;
	display: flex;
	align-items: center;
	justify-content: center;
}
.hero__btn:last-child {
	margin-right: 0;
}
.hero__btn text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-on-night;
}

/* ---- 照护 / 在用药 / 过敏 的紧凑清单 ---- */
.hc__head--gap {
	margin-top: $mw-space-3;
}
.care__item {
	display: flex;
	align-items: center;
	padding: $mw-space-2 0;
	border-bottom: 1px solid $mw-line;
}
.care__item:last-child {
	border-bottom: none;
}
.care__main {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
}
.care__name {
	font-size: $mw-font-body;
	font-weight: 600;
	color: $mw-ink;
}
.care__when {
	margin-top: 2rpx;
	font-size: $mw-font-micro;
	color: $mw-ink-2;
}

/* ---- 六类计数 ---- */
.tiles {
	display: flex;
	flex-wrap: wrap;
}
.tile {
	width: 33.33%;
	padding: $mw-space-2 0;
	display: flex;
	flex-direction: column;
	align-items: center;
}
.tile__num {
	font-size: $mw-font-h1;
	font-weight: 700;
	color: $mw-ink;
	font-variant-numeric: tabular-nums;
}
.tile__label {
	margin-top: 2rpx;
	font-size: 22rpx;
	color: $mw-ink-2;
}
.warning {
	margin-top: $mw-space-2;
	padding: $mw-space-2;
	background: rgba(229, 72, 77, 0.08);
	border: 1px solid rgba(229, 72, 77, 0.2);
	border-radius: $mw-radius-input;
}
.warning text {
	font-size: $mw-font-micro;
	line-height: 1.6;
	color: $mw-danger;
}

/* ---- 最近动静（时间线预览） ---- */
.tl__item {
	display: flex;
	align-items: flex-start;
	padding: $mw-space-2 0;
}
.tl__dot {
	width: 12rpx;
	height: 12rpx;
	margin-top: 14rpx;
	margin-right: $mw-space-2;
	border-radius: 50%;
	background: $mw-brand-ink;
	flex: none;
}
.tl__body {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
}
.tl__title {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-ink;
}
.tl__meta {
	margin-top: 2rpx;
	font-size: 22rpx;
	color: $mw-muted;
}

/* ---- AI 解读 / 导出 ---- */
.ai {
	margin-top: $mw-space-2;
	padding: $mw-space-3;
	background: $mw-canvas;
	border-radius: $mw-radius-input;
}
.ai__meta {
	display: block;
	font-size: $mw-font-micro;
	color: $mw-ink-2;
}
.ai__body {
	display: block;
	margin: $mw-space-2 0 0;
	font-size: $mw-font-caption;
	line-height: 1.75;
	color: $mw-ink;
	white-space: pre-wrap;
}
.ai__sources {
	display: flex;
	flex-wrap: wrap;
	margin-top: $mw-space-2;
}
.ai__btn {
	margin-top: $mw-space-3;
	min-height: $mw-touch-min;
	border-radius: $mw-radius-pill;
	background: $mw-brand-ink;
	display: flex;
	align-items: center;
	justify-content: center;
}
.ai__btn text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: #ffffff;
}
.inline-err--plain {
	margin: $mw-space-2 0 0;
}

/* ---- 底部快捷新建 ---- */
.newbar {
	margin: 0 $mw-page-padding;
}
.newbar__label {
	display: block;
	margin-bottom: $mw-space-2;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}
.newbar__grid {
	display: flex;
	flex-wrap: wrap;
}
.newbar__item {
	width: 33.33%;
	padding: $mw-space-1;
}
.newbar__item text {
	display: block;
	min-height: $mw-touch-min;
	line-height: $mw-touch-min;
	text-align: center;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-pill;
	background: $mw-surface;
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-ink;
}
</style>