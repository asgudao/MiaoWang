<template>
	<view class="page">
		<!-- 深色舞台带：3D 电子宠物是主角，界面退到四周 -->
		<view class="hero">
			<view class="hero__nav" :style="{ paddingTop: statusBarHeight + 'px' }">
				<text class="hero__brand">喵汪</text>
				<text class="hero__greet">{{ greet }}，{{ ownerName }}</text>
			</view>
			<PetStage @failed="onStageFailed" />
		</view>

		<!-- 叠压在舞台边缘上的内容层 -->
		<view class="sheet">
			<!-- ① 数据条：三个数字**全部有据可依**（待办/逾期来自健康记录的「下次到期」，宠物只数来自 /pet）。
			     「已陪伴 N 天」与「完成度 %」没有数据源（pet 接口没有领养日期、后端没有打卡表）→ 已下线 -->
			<view class="stats">
				<view class="stat">
					<text class="stat__label">待办</text>
					<text class="stat__num">{{ home.todoCount }}<text class="stat__unit">项</text></text>
				</view>
				<view class="stat">
					<text class="stat__label">已逾期</text>
					<text class="stat__num">{{ home.overdueCount }}<text class="stat__unit">项</text></text>
				</view>
				<view class="stat">
					<text class="stat__label">宠物</text>
					<text class="stat__num">{{ homePets.length }}<text class="stat__unit">只</text></text>
				</view>
			</view>

			<!-- ② 快捷操作：两个胶囊，都接真实去处（图标 = base64 data-URI 背景图：手机端无 npm，
			     装不了官方图标库；🔴 改前是内联 `<svg>`，App 端落在 HTML 命名空间 → 画不出来，见 styles/icons.scss）。
			     「拍张照」没有识图接口、也没有去处 → 已下线，不留点了没反应的按钮 -->
			<view class="quick">
				<view v-for="a in actions" :key="a.key" class="quick__item" @click="runAction(a)">
					<!-- 图标：**base64 data-URI 背景图**（改前是内联 `<svg>`）。
					     🔴 2026-09-22 实测根因：App 端视图层用 `document.createElement(tag)` 造节点，
					     内联 `<svg>` 落在 HTML 命名空间（HTMLUnknownElement）→ 永远画不出来；
					     CSS 背景图由 webview 的图片解码器按 SVG 命名空间解析，App / H5 都可靠。
					     形状按 `a.key` 二选一，颜色都是既有令牌 $mw-brand-ink（见 styles/icons.scss）。 -->
					<view class="mw-ic"
						:class="a.key === 'log' ? 'mw-ic--edit-brand' : 'mw-ic--bell-brand'"></view>
					<text class="quick__label">{{ a.label }}</text>
				</view>
			</view>

			<!-- ③ 照护提醒：待办 = 逾期 + 今天到期；后端没有打卡表，Done 一律不编 -->
			<view class="sec-head">
				<text class="sec-head__title">照护提醒</text>
				<text class="sec-head__meta">待办 {{ home.todoCount }} 项</text>
			</view>

			<text v-if="degradedText && !loading && !errorText" class="note note--danger">{{ degradedText }}</text>

			<view v-if="loading">
				<view v-for="i in 3" :key="'s' + i" class="skeleton"></view>
			</view>

			<!-- 错误态：连宠物档案都没读到 → 明确说明 + 真能用的重试 -->
			<view v-else-if="errorText" class="kb">
				<text class="kb__title">照护提醒没读出来</text>
				<text class="note note--danger">{{ errorText }}</text>
				<view class="chat__cta" @click="reload"><text>重试</text></view>
			</view>

			<view v-else-if="home.tasks.length">
				<view v-for="t in home.tasks" :key="t.id" class="task" :class="{ 'task--overdue': t.status === 'overdue' }">
					<text class="task__kind">{{ kindLabel(t.kind) }}</text>
					<view class="task__main">
						<text class="task__title">{{ t.title }}</text>
						<text class="task__pet">{{ t.petName }}</text>
					</view>
					<text class="tag" :class="tagTone(t.status)">{{ t.dueLabel }}</text>
				</view>
			</view>

			<!-- 空态：不编"今日已完成 N 项"，只说清提醒从哪来、去哪儿加；
			     健康数据没读到时不许说成"没有照护项" -->
			<view v-else class="kb">
				<text class="kb__title">{{ emptyTitle }}</text>
				<text class="note">{{ emptyHint }}</text>
				<view class="chat__cta" @click="goLog"><text>{{ homePets.length ? '去记一条' : '添加宠物' }}</text></view>
			</view>

			<!-- ④ 最近 7 天：数的是**每天写了多少条记录**，不是照护完成率 -->
			<view class="sec-head sec-head--gap">
				<text class="sec-head__title">最近 7 天记录</text>
				<text class="sec-head__meta">{{ rhythmTotalText }} 条</text>
			</view>
			<view class="rhythm" :aria-label="'最近七天每天写了多少条健康记录'">
				<view v-for="d in home.rhythm" :key="d.label" class="col" :class="{ 'col--today': d.isToday }">
					<view class="col__track">
						<view class="col__bar" :style="{ height: barHeight(d) }"></view>
					</view>
					<text class="col__label">{{ d.label }}</text>
				</view>
			</view>

			<!-- ⑤ 我的宠物：体重来自健康记录；没有记录就整块不渲染，绝不给真档案编体重 -->
			<view class="sec-head sec-head--gap">
				<text class="sec-head__title">我的宠物</text>
				<text class="sec-head__meta">{{ homePets.length }} 只</text>
			</view>
			<view v-for="p in petCards" :key="p.pid" class="pet">
				<view class="pet__top">
					<view class="pet__avatar"><text>{{ p.initial }}</text></view>
					<view class="pet__id">
						<text class="pet__name">{{ p.name }}</text>
						<text class="pet__meta">{{ p.meta }}</text>
					</view>
					<view v-if="p.hasWeight" class="pet__weight">
						<text class="pet__num">{{ p.weightText }}<text class="pet__unit">kg</text></text>
						<text class="pet__delta">{{ p.deltaText }}</text>
					</view>
					<!-- 没有体重记录 / 健康数据没读到：如实说，不编 -->
					<text v-else class="pet__real">{{ p.degraded ? '健康数据未读到' : '无体重记录' }}</text>
				</view>
				<text v-if="p.nextCare" class="pet__next" :class="p.nextTone ? 'pet__next--' + p.nextTone : ''">{{ p.nextCare }}</text>
				<text v-else class="pet__call">{{ p.callText }}</text>
			</view>

			<!-- ⑥ 电子宠物对话：宠物主动说的那一句来自 ai-pet 的 `POST /greet`
			     （**进页面调一次、不消耗聊天次数**），文本与名字直接用接口字段（greeting / petName），
			     不替它编、也不摆演示气泡。`greeting` 为空 = 这次没开口（不是错误）→ 不出气泡；
			     没有宠物档案 / 请求失败两种情况都照实说，并给「去添加宠物」这个真去处。
			     mock 的演示气泡只在演示模式下才渲染（真数据模式下 home.chat 恒为空）。 -->
			<view class="chat">
				<text class="chat__title">它今天说了什么</text>
				<view v-if="petGreeting.text" class="chat__thread">
					<view class="bubble bubble--pet">
						<text v-if="petGreeting.name" class="bubble__who">{{ petGreeting.name }}</text>
						<text>{{ petGreeting.text }}</text>
					</view>
				</view>
				<view v-else-if="home.chat.length" class="chat__thread">
					<text v-for="(m, i) in home.chat" :key="i" class="bubble" :class="'bubble--' + m.from">{{ m.text }}</text>
				</view>
				<text v-else-if="petGreetNote" class="note">{{ petGreetNote }}</text>
				<text v-else class="note">它这次没开口；点下面「去和它说话」就能聊，它记得你们最近说过什么</text>
				<!-- 主按钮 = 这张卡的主题（跟它闲聊）；次按钮 = 健康问诊（另一个模块）→ 降级成描边样式，但**仍是真入口** -->
				<view class="chat__cta" @click="openPetChat"><text>去和它说话</text></view>
				<!-- 没有宠物档案时的真去处（本页已知没档案，或后端回 B000906）：直接开新建宠物页 -->
				<view v-if="petGreetNeedPet" class="chat__cta chat__cta--ghost" @click="goAddPet"><text>去添加宠物</text></view>
				<view class="chat__cta chat__cta--ghost" @click="openAi"><text>去 AI 问诊</text></view>
			</view>

			<!-- ⑦ 知识库内容卡：演示模式给 mock 标题；真数据模式给一条真实入口（都点得动） -->
			<view class="kb">
				<text class="kb__title">知识库</text>
				<view v-for="(a, i) in kbItems" :key="i" class="kb__item" @click="openKnowledge">
					<text class="kb__text">{{ a }}</text>
					<text class="kb__arrow">›</text>
				</view>
			</view>

			<!-- 诚实标注：舞台错误最优先，其次说明数据是不是真的 -->
			<text v-if="stageError" class="note note--danger">{{ stageError }}</text>
			<text v-else-if="home.source === 'mock'" class="note">演示数据（打开了演示开关），不是你的真实记录</text>
			<text v-else-if="remindOff" class="note">提醒总开关关着：待办数字与宠物卡上的照护标记都不显示（在「我的 → 提醒设置」里可以打开）</text>
			<text v-else-if="loggedIn" class="note">照护提醒来自你的健康记录：疫苗与驱虫按「下次到期」算，用药只取「用药中」</text>
			<text v-else class="note">登录后这里会显示你自己宠物的照护提醒</text>
		</view>

		<!-- ⑧ 本机提醒弹窗：进入 App / 从后台回前台时弹一次。
		     内容全部来自健康记录的派生（`utils/remind-schedule.js`），去处是真实的宠物健康档案；
		     总开关 / 免打扰 / 提前 N 天都会真的拦在这里；同一条目同一天只弹一次。
		     ⚠️ 这是**软件内弹窗**，不是系统推送：喵汪没有接服务端推送。 -->
		<view v-if="remind.show" class="mask">
			<view class="dialog">
				<view class="dialog__head">
					<text class="dialog__title">{{ remind.title }}</text>
					<view class="dialog__close" @click="closeRemind"><text>关闭</text></view>
				</view>
				<text class="dialog__sub">{{ remind.subtitle }}</text>
				<view v-for="it in remind.items" :key="it.key" class="ritem" @click="goRemind(it)">
					<view class="ritem__main">
						<text class="ritem__pet">{{ it.petName }}</text>
						<text class="ritem__title">{{ it.title }}</text>
					</view>
					<text class="rtag" :class="'rtag--' + it.dueTone">{{ it.dueLabel }}</text>
					<text class="ritem__arrow">›</text>
				</view>
				<view class="dialog__acts">
					<view class="dialog__ghost" @click="closeRemind"><text>稍后再说</text></view>
					<view class="dialog__primary" @click="goRemind(remind.items[0])"><text>去看健康档案</text></view>
				</view>
				<text class="dialog__note">这些提醒只在本机按健康记录算出来、只在本机弹；喵汪没有接服务端推送，关掉 App 后不会再提醒</text>
			</view>
		</view>

		<FloatingTabBar current="home" />
	</view>
</template>

<script>
import PetStage from '@/components/PetStage.vue'
import FloatingTabBar from '@/components/FloatingTabBar.vue'
import {
	closeRemindPopup,
	fetchHomeOverview,
	fetchRemindPopup,
	kindLabel,
	markPopupShown,
	rhythmTotal
} from '@/api/home.js'
import { hideNativeTabBar } from '@/utils/tabbar.js'
import { auth } from '@/store/auth.js'
import { greetPet } from '@/api/aiPet.js'
import { isNoProfile } from '@/utils/aiPet.js'

/** 弹窗的初始状态（形状与 `api/remind.js` 的快照一致；模板只消费摊平后的字段） */
function emptyRemind () {
	return { show: false, title: '', subtitle: '', items: [] }
}

export default {
	components: { PetStage, FloatingTabBar },
	data() {
		return {
			loading: true,
			statusBarHeight: 20,
			stageError: '',
			/** 是否已经进过本页（用来让 onShow 只在"非首次"时刷新，避免首次重复拉一遍） */
			entered: false,
			/** 取数失败（连 /pet 都没读到）→ 错误态 + 重试；**不回退演示数据** */
			errorText: '',
			/** 两个胶囊都接真实去处：「记一笔」→ 健康档案，「加提醒」→ 直接开一条疫苗/驱虫
			 *（「下次到期」就是提醒的源头）。原来的「拍张照」没有识图接口也没有去处 → 已下线 */
			actions: [
				{ key: 'log', label: '记一笔' },
				{ key: 'remind', label: '加提醒' }
			],
			home: {
				source: 'real',
				ownerName: '铲屎官',
				todoCount: 0,
				overdueCount: 0,
				tasks: [],
				remindOff: false,
				remindAheadDays: 0,
				pets: [],
				rhythm: [],
				degradedPets: [],
				chat: [],
				articles: []
			},
			/** 本机提醒弹窗（进入 App / 回前台时算一次；同一条目同一天只弹一次） */
			remind: emptyRemind(),
			/**
			 * 宠物主动说的那一句（真数据模式下来自 `POST /ai/pet/greet`，**不消耗聊天次数**）。
			 * `text` / `name` **只来自接口字段**（`greeting` / `petName`），一个字都不自己编；
			 * `text` 为空 = 这次没有这句话（`greeting` 为空不是错误）→ 模板不渲染气泡。
			 */
			petGreeting: { text: '', name: '' },
			/** 没有那句话时的**如实**说明（空串 = 页面上不出现这一行） */
			petGreetNote: '',
			/** 「还没有宠物档案」→ 给「去添加宠物」这个真入口（本页已知没档案 与 B000906 共用它） */
			petGreetNeedPet: false,
			/**
			 * 本页是否**真的发出过**一次 `/greet`。
			 * `/greet` 有时段缓存（同一时段后端只会生成一句，响应里的 `cached` 就是这件事），
			 * 所以口径是**一次进页一次**：不轮询、不在返回首页时反复调。
			 * 没发出去（未登录 / 没有档案 / 取数失败）就保持 false —— 等下一次有机会再说。
			 */
			greetTried: false
		}
	},
	computed: {
		greet() {
			const h = new Date().getHours()
			if (h < 6) return '夜深了'
			if (h < 11) return '早上好'
			if (h < 14) return '中午好'
			if (h < 18) return '下午好'
			return '晚上好'
		},
		loggedIn() {
			return auth.isLoggedIn()
		},
		/** 宠物卡：真数据模式下来自 /pet（概览自己拉的）；演示模式下来自 mock */
		homePets() {
			return this.home.pets
		},
		/** 最近 7 天的**记录条数合计**（不是完成率） */
		rhythmTotalText() {
			return rhythmTotal(this.home.rhythm)
		},
		/** 部分失败：真有宠物没读到健康记录 → 如实说，别让人误以为"它没有照护项" */
		degradedText() {
			const names = this.home.degradedPets || []
			if (!names.length) return ''
			return names.join('、') + '的健康记录这次没读到，它的照护项没有算进来'
		},
		/** 知识库卡：演示模式给 mock 标题；真数据模式给一条真实入口（点进去是知识库 tab） */
		kbItems() {
			return this.home.articles.length ? this.home.articles : ['看看护理日程与日常养护知识']
		},
		/** 空态标题：健康数据没读到时不许说成"没有照护项" */
		emptyTitle() {
			if (this.degradedText) return '照护项这次没算出来'
			if (this.remindOff) return '提醒总开关关着'
			return this.homePets.length ? '还没有可提醒的照护项' : '还没有宠物档案'
		},
		emptyHint() {
			if (this.degradedText) return '这只宠物的健康记录没读到，照护项不猜；下拉或重进可以再试一次'
			if (this.remindOff) return '打开「我的 → 提醒设置」里的提醒总开关，待办就会按你设的提前天数重新出现'
			return this.homePets.length
				? '在健康记录里给疫苗或驱虫填上「下次到期」，这里就会自动出现提醒'
				: '先添加一只宠物，再记录疫苗与驱虫的到期时间'
		},
		/**
		 * 本机提醒总开关（`api/home.js` 从本机设置读出来）。
		 * 关掉后：这个数字不再显示待办、宠物卡不再显示照护标记、也不再弹窗/发通知。
		 * **只在真数据模式下看它** —— 演示模式与空概览都不受本机设置影响（那两种情况没有"待办"可言）。
		 */
		remindOff() {
			return this.home.remindOff === true
		},
		/** 首只宠物（「记一笔 / 加提醒」要带它的 pid） */
		primaryPet() {
			return this.home.pets.length ? this.home.pets[0] : null
		},
		/**
		 * 显示字段在这里一次算好。
		 * 模板里**不许**直接调 utils / store（那些不在实例上，会渲染期报错并白屏）。
		 */
		petCards() {
			return this.homePets.map((p) => {
				const demo = p.demo === true
				const hasWeight = p.weight != null
				const up = hasWeight && typeof p.weightDelta === 'number' ? p.weightDelta >= 0 : false
				// 大类品种与年龄拼一行（真档案按 birth 现算，与 Web 端同口径）
				const meta = [p.breed, p.ageLabel].filter((s) => s).join('，')
				// 体重变化量只有演示数据才有（真档案缺 30 天基线，宁可不显示），没有就显示测量日
				let deltaText = ''
				if (hasWeight) {
					if (typeof p.weightDelta === 'number') deltaText = (up ? '↑' : '↓') + Math.abs(p.weightDelta).toFixed(1)
					else if (p.weightAt) deltaText = p.weightAt + ' 测'
				}
				return {
					pid: String(p.pid),
					initial: p.initial,
					name: p.name,
					meta: meta,
					demo: demo,
					degraded: p.degraded === true,
					hasWeight: hasWeight,
					weightText: hasWeight ? Number(p.weight).toFixed(1) : '',
					deltaText: deltaText,
					nextCare: p.nextCare || '',
					nextTone: p.nextTone || '',
					callText: p.callName ? '它叫你「' + p.callName + '」' : '还没有照护提醒'
				}
			})
		},
		/** 登录后问候语用「宠物怎么称呼你」，未登录就用概览里的称呼 */
		ownerName() {
			return auth.isLoggedIn() ? auth.ownerCallName() : this.home.ownerName
		}
	},
	onLoad() {
		hideNativeTabBar()
		try {
			const info = uni.getSystemInfoSync()
			this.statusBarHeight = info.statusBarHeight || 20
		} catch (e) {}
	},
	onShow() {
		hideNativeTabBar()
		// 回到首页要能看到刚记的那条：从健康档案记完疫苗/驱虫返回时，待办得立刻变。
		// 首次进入由 mounted 拉（uni-app 的 onShow 可能在 mounted 之前触发），所以用 entered 挡一次，
		// 保证"首次只拉一次、之后每次回来都刷新一次"。
		if (this.entered) this.reload()
		this.entered = true
		// 弹窗：App.vue 的 onShow 也会算一次（那时用户可能停在别的 tab），
		// 两处共用 `api/remind.js` 里的复用窗口 → **同一次前后台切换只发一遍请求**。
		this.refreshRemind()
	},
	mounted() {
		this.reload()
	},
	methods: {
		kindLabel,
		async reload() {
			this.loading = true
			this.errorText = ''
			try {
				this.home = await fetchHomeOverview()
			} catch (e) {
				this.errorText = (e && e.message) || '照护提醒没读出来，稍后再试'
			} finally {
				this.loading = false
			}
			// 进首页让宠物主动说一句（**不消耗聊天次数**）。
			// 连 `/pet` 都没读到时不问它：有没有档案都不知道，问了也只会拿到一句与事实无关的报错。
			if (!this.errorText) this.loadPetGreeting()
		},
		/**
		 * 宠物主动开口（`POST /ai/pet/greet`，**不扣聊天次数**）。三条不许破的口径：
		 *
		 * 1. **一次进页只调一次**（`greetTried` 真的发出请求才置位）：不轮询、不在返回首页时反复调
		 *    —— 同一时段后端本来也只生成一句（响应的 `cached` 字段就是这件事）。
		 * 2. **没有宠物档案时不发这个请求**：首页已经从 `/pet` 知道有没有档案，
		 *    直接给「去添加宠物」的真去处，而不是先打一次注定 `B000906` 的请求。
		 *    （后端 `PetProfileLoader.load` 里"没档案"短路在归属校验**之前**：
		 *    2026-09-22 用 MapleLeaf1 实测，带别人的 pid 也是 B000906，不是 B000904。）
		 * 3. **`greeting` 为空不是错误**（模型抖动、或关闭问候时后端就回 null）→
		 *    不出气泡、不当错误页；请求失败照实说，绝不写成"它这会儿不想说话"这种假状态。
		 */
		async loadPetGreeting() {
			if (this.greetTried) return
			// 演示模式里的宠物是演示数据，没有真档案可问候（它们的 pid 是 p1/p2，不是真 pid）
			if (this.home.source === 'mock') return
			if (!auth.isLoggedIn()) {
				this.petGreetNote = '登录之后它才会跟你打招呼'
				return
			}
			// pid 来自本页概览里的 `/pet`（`api/home.js` 内部就是 `listMyPets()`，与 store/pets.js 同一份数据源），
			// 所以这里不额外再拉一遍 /pet；概览把它 String() 过，这里转回数字再发（后端字段是 Long）
			const pid = this.primaryPet ? Number(this.primaryPet.pid) : 0
			if (!pid) {
				this.petGreetNeedPet = true
				this.petGreetNote = '还没有宠物档案，添加一只之后它才会开口'
				return
			}
			this.greetTried = true
			try {
				const resp = await greetPet(pid)
				const text = resp && resp.greeting ? String(resp.greeting) : ''
				this.petGreeting = { text: text, name: resp && resp.petName ? String(resp.petName) : '' }
				this.petGreetNeedPet = false
				this.petGreetNote = text ? '' : '它这次没主动开口（问候没生成出来，不影响你主动找它聊）'
			} catch (e) {
				this.petGreeting = { text: '', name: '' }
				// 竞态兜底：概览时还有档案、问候时刚被删（或改了归属）→ 仍按"没档案"处理，给真去处
				if (isNoProfile(e)) {
					this.petGreetNeedPet = true
					this.petGreetNote = (e && e.message) || '还没有宠物档案，添加一只之后它才会开口'
					return
				}
				this.petGreetNote = '问候没读到：' + ((e && e.message) || '稍后再试')
			}
		},
		/** 没有宠物档案时的真去处：新建宠物页（与宠物对话页的「去添加宠物」同一条路径） */
		goAddPet() {
			if (!auth.isLoggedIn()) {
				uni.navigateTo({ url: '/pages/login/login' })
				return
			}
			uni.navigateTo({ url: '/pages/pet-edit/pet-edit' })
		},
		/**
		 * 算一次"现在该不该弹"，并把结果摊平成模板字段。
		 *
		 * ⚠️ `markPopupShown()` **只在这里**（弹窗真的要渲染时）调：账本一记，
		 * 系统通知那条兜底路就不会再响一遍（两条通道共用 `f:` 去重键，避免"弹窗 + 通知"双响）。
		 * 反过来，如果用户停在别的 tab（首页没渲染），就不会记账 → 系统通知仍会兜底，提醒不会丢。
		 */
		async refreshRemind() {
			try {
				const snap = await fetchRemindPopup()
				this.remind = {
					show: snap.show === true,
					title: snap.title || '',
					subtitle: snap.subtitle || '',
					items: Array.isArray(snap.items) ? snap.items : []
				}
				if (this.remind.show) markPopupShown()
			} catch (e) {
				// 弹窗读不到就**不弹**（不拿旧数据弹一个可能已经不成立的提醒），也不打断首页
				this.remind = emptyRemind()
			}
		},
		/** 关闭弹窗（今天不再弹这一条；账已经在 markPopupShown 里记过） */
		closeRemind() {
			closeRemindPopup()
			this.remind = emptyRemind()
		},
		/** 一键去这只宠物的健康档案（**真实页面路径**，与快捷胶囊用的同一条） */
		goRemind(item) {
			const pid = item && item.pid ? String(item.pid) : ''
			this.closeRemind()
			if (!pid) return
			uni.navigateTo({ url: '/pages/health/health?pid=' + pid })
		},
		/** 状态 → 徽标色档（只用既有 scss 类，不新增令牌） */
		tagTone(status) {
			if (status === 'overdue') return 'tag--overdue'
			if (status === 'today') return 'tag--todo'
			if (status === 'active') return 'tag--done'
			return ''
		},
		/** 快捷胶囊：都跳真实页面（没登录先让他登录，不要静默什么都不做） */
		runAction(a) {
			if (!auth.isLoggedIn()) {
				uni.navigateTo({ url: '/pages/login/login' })
				return
			}
			if (a && a.key === 'remind') {
				if (!this.primaryPet) {
					uni.switchTab({ url: '/pages/pets/pets' })
					return
				}
				// 提醒的源头就是疫苗/驱虫的「下次到期」→ 直接开一条
				uni.navigateTo({ url: '/pages/health/record-edit?pid=' + this.primaryPet.pid + '&type=preventive' })
				return
			}
			if (!this.primaryPet) {
				uni.switchTab({ url: '/pages/pets/pets' })
				return
			}
			uni.navigateTo({ url: '/pages/health/health?pid=' + this.primaryPet.pid })
		},
		/** 空态引导：去健康档案记一条（没有宠物就去加宠物） */
		goLog() {
			if (!auth.isLoggedIn()) {
				uni.navigateTo({ url: '/pages/login/login' })
				return
			}
			if (!this.primaryPet) {
				uni.switchTab({ url: '/pages/pets/pets' })
				return
			}
			uni.navigateTo({ url: '/pages/health/record-edit?pid=' + this.primaryPet.pid + '&type=preventive' })
		},
		/** 真实去处：AI 问诊（知识库 RAG，问的是健康问题；跟它闲聊是另一条入口） */
		openAi() {
			if (!auth.isLoggedIn()) {
				uni.navigateTo({ url: '/pages/login/login' })
				return
			}
			uni.navigateTo({ url: '/pages/ai/ai' })
		},
		/** 真实去处：宠物对话（miaowang-ai-pet，非 tab 页 → navigateTo；它按账号记对话，所以先登录） */
		openPetChat() {
			if (!auth.isLoggedIn()) {
				uni.navigateTo({ url: '/pages/login/login' })
				return
			}
			uni.navigateTo({ url: '/pages/ai/pet' })
		},
		/** 真实去处：知识库 tab */
		openKnowledge() {
			uni.switchTab({ url: '/pages/knowledge/knowledge' })
		},
		/** 舞台报错回传（PetStage 的 @failed，与 Web 端同名事件）*/
		onStageFailed(msg) {
			this.stageError = msg || '3D 舞台加载失败'
		},
		/** 柱高按"当天条数 / 本周最多的一天"：0 条的那天不画柱，不用最小高度假装有记录 */
		barHeight(d) {
			let max = 0
			for (let i = 0; i < this.home.rhythm.length; i++) {
				const c = Number(this.home.rhythm[i].count || 0)
				if (c > max) max = c
			}
			const count = Number((d && d.count) || 0)
			if (!max || !count) return '0%'
			return Math.max((count / max) * 100, 6) + '%'
		}
	}
}
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.page {
	min-height: 100vh;
	background: $mw-canvas;
	padding-bottom: calc(#{$mw-tabbar-h} + 80rpx);
}

/* ---------- 舞台带 ---------- */
.hero {
	position: relative;
	background: $mw-night;
}

.hero__nav {
	position: absolute;
	left: 0;
	right: 0;
	top: 0;
	z-index: 5;
	display: flex;
	align-items: baseline;
	justify-content: space-between;
	padding-left: $mw-page-padding;
	padding-right: $mw-page-padding;
	padding-bottom: $mw-space-2;
}

.hero__brand {
	font-size: 38rpx;
	font-weight: 700;
	color: $mw-on-night;
	letter-spacing: -0.02em;
}

.hero__greet {
	font-size: $mw-font-micro;
	color: $mw-on-night-2;
}

/* ---------- 叠压的内容层 ---------- */
.sheet {
	position: relative;
	z-index: 3;
	margin-top: -48rpx;
	padding: $mw-space-4 $mw-page-padding 0;
	background: $mw-canvas;
	border-radius: $mw-radius-stage $mw-radius-stage 0 0;
}

/* ① 数据条 */
.stats {
	display: flex;
	justify-content: space-between;
	padding: $mw-space-2 0 $mw-space-4;
	border-bottom: 1px solid $mw-line;
}

.stat {
	display: flex;
	flex-direction: column;
}

.stat__label {
	font-size: $mw-font-micro;
	color: $mw-ink-2;
}

.stat__num {
	margin-top: 4rpx;
	font-size: 52rpx;
	font-weight: 700;
	color: $mw-ink;
	letter-spacing: -0.02em;
	font-variant-numeric: tabular-nums;
}

.stat__unit {
	font-size: $mw-font-caption;
	font-weight: 500;
	color: $mw-ink-2;
	margin-left: 4rpx;
}

/* ② 快捷操作：胶囊按钮，不是三张等宽卡片 */
.quick {
	display: flex;
	gap: $mw-space-2;
	padding: $mw-space-4 0 $mw-space-2;
}

.quick__item {
	display: flex;
	align-items: center;
	gap: $mw-space-1;
	min-height: 72rpx;
	padding: 0 $mw-space-3;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-pill;
}

.quick__label {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-brand-ink;
}

/* 区块标题 */
.sec-head {
	display: flex;
	align-items: baseline;
	justify-content: space-between;
	margin: $mw-space-6 0 $mw-space-3;
}
.sec-head--gap {
	margin-top: $mw-space-8;
}
.sec-head__title {
	font-size: $mw-font-h2;
	font-weight: 600;
	color: $mw-ink;
}
.sec-head__meta {
	font-size: $mw-font-caption;
	color: $mw-ink-2;
	font-variant-numeric: tabular-nums;
}

.skeleton {
	height: 136rpx;
	margin-bottom: $mw-space-2;
	border-radius: $mw-radius-card;
	background: linear-gradient(90deg, rgba(138, 119, 103, 0.14) 25%, rgba(138, 119, 103, 0.26) 37%, rgba(138, 119, 103, 0.14) 63%);
	background-size: 400% 100%;
	animation: sk 1.4s ease infinite;
}
@keyframes sk {
	0% { background-position: 100% 50%; }
	100% { background-position: 0 50%; }
}

/* ③ 照护行 */
.task {
	display: flex;
	align-items: center;
	min-height: 136rpx;
	padding: $mw-space-3 $mw-space-4;
	margin-bottom: $mw-space-2;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
.task--done {
	opacity: 0.62;
}
.task--done .task__title {
	text-decoration: line-through;
}
.task--overdue {
	border-color: rgba(229, 72, 77, 0.35);
}

/* 类型用方块标签，和 Web 端的图标盒视觉等价（手机端不额外画 5 个图标） */
.task__kind {
	width: 72rpx;
	height: 72rpx;
	flex: none;
	display: flex;
	align-items: center;
	justify-content: center;
	margin-right: $mw-space-3;
	border-radius: $mw-radius-tag;
	background: rgba(194, 65, 12, 0.1);
	font-size: 22rpx;
	font-weight: 600;
	color: $mw-brand-ink;
}

.task__main {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
}
.task__title {
	font-size: $mw-font-body;
	font-weight: 600;
	color: $mw-ink;
}
.task__pet {
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}

.tag {
	flex: none;
	padding: 4rpx 16rpx;
	border-radius: $mw-radius-tag;
	font-size: 22rpx;
	font-weight: 600;
}
.tag--todo {
	background: rgba(255, 176, 32, 0.16);
	color: $mw-accent-ink;
}
.tag--done {
	background: rgba(74, 118, 56, 0.12);
	color: $mw-success;
}
.tag--overdue {
	background: rgba(229, 72, 77, 0.12);
	color: $mw-danger;
}

/* ④ 周节奏柱图 */
.rhythm {
	display: flex;
	gap: $mw-space-2;
	align-items: flex-end;
	padding: $mw-space-4;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
.col {
	flex: 1;
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: $mw-space-1;
}
.col__track {
	display: flex;
	align-items: flex-end;
	width: 100%;
	height: 120rpx;
}
.col__bar {
	width: 100%;
	border-radius: 8rpx 8rpx 2rpx 2rpx;
	background: $mw-brand-ink;
	opacity: 0.34;
}
.col--today .col__bar {
	opacity: 1;
}
.col__label {
	font-size: 22rpx;
	color: $mw-muted;
}
.col--today .col__label {
	color: $mw-brand-ink;
	font-weight: 600;
}

/* ⑤ 宠物卡 */
.pet {
	padding: $mw-space-4;
	margin-bottom: $mw-space-2;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
.pet__top {
	display: flex;
	align-items: center;
}
.pet__avatar {
	width: 88rpx;
	height: 88rpx;
	flex: none;
	border-radius: 50%;
	background: rgba(194, 65, 12, 0.1);
	display: flex;
	align-items: center;
	justify-content: center;
}
.pet__avatar text {
	font-size: 32rpx;
	font-weight: 700;
	color: $mw-brand-ink;
}
.pet__id {
	flex: 1;
	min-width: 0;
	margin-left: $mw-space-3;
	display: flex;
	flex-direction: column;
}
.pet__name {
	font-size: $mw-font-body;
	font-weight: 600;
	color: $mw-ink;
}
.pet__meta {
	font-size: 22rpx;
	color: $mw-ink-2;
}
.pet__weight {
	text-align: right;
	display: flex;
	flex-direction: column;
}
/* 真档案没有体重模块，右侧给个标签占位，别让卡片看着缺了一块 */
.pet__real {
	flex: none;
	padding: 4rpx 16rpx;
	border-radius: $mw-radius-tag;
	background: $mw-canvas;
	font-size: 22rpx;
	color: $mw-ink-2;
}
.pet__call {
	display: block;
	margin-top: $mw-space-3;
	font-size: 22rpx;
	color: $mw-ink-2;
}
.pet__num {
	font-size: 40rpx;
	font-weight: 700;
	color: $mw-ink;
	letter-spacing: -0.02em;
	font-variant-numeric: tabular-nums;
}
.pet__unit {
	font-size: 22rpx;
	font-weight: 500;
	color: $mw-ink-2;
	margin-left: 4rpx;
}
.pet__delta {
	font-size: 22rpx;
	color: $mw-ink-2;
	font-variant-numeric: tabular-nums;
}
.pet__next {
	display: inline-block;
	margin-top: $mw-space-3;
	padding: 6rpx 16rpx;
	border-radius: $mw-radius-tag;
	font-size: 22rpx;
	font-weight: 600;
}
.pet__next--ok {
	background: rgba(74, 118, 56, 0.1);
	color: $mw-success;
}
.pet__next--warn {
	background: rgba(255, 176, 32, 0.16);
	color: $mw-accent-ink;
}
.pet__next--danger {
	background: rgba(229, 72, 77, 0.12);
	color: $mw-danger;
}

/* ⑥ 对话卡 */
.chat {
	margin-top: $mw-space-8;
	padding: $mw-space-4;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
.chat__title {
	display: block;
	font-size: $mw-font-h2;
	font-weight: 600;
	color: $mw-ink;
}
.chat__thread {
	display: flex;
	flex-direction: column;
	gap: $mw-space-2;
	margin-top: $mw-space-3;
}
.bubble {
	max-width: 84%;
	padding: $mw-space-3 $mw-space-4;
	border-radius: 24rpx;
	font-size: $mw-font-caption;
	line-height: 1.7;
}
.bubble--pet {
	align-self: flex-start;
	background: $mw-canvas;
	border: 1px solid $mw-line;
	border-bottom-left-radius: 8rpx;
	color: $mw-ink;
}
/* 这句是谁说的：名字**来自接口的 petName**（没起名时后端回「未命名」，原样显示），不替它编 */
.bubble__who {
	display: block;
	margin-bottom: $mw-space-1;
	font-size: 22rpx;
	font-weight: 600;
	color: $mw-brand-ink;
}
.bubble--me {
	align-self: flex-end;
	background: $mw-brand-ink;
	color: #ffffff;
	border-bottom-right-radius: 8rpx;
}
.chat__cta {
	margin-top: $mw-space-4;
	min-height: $mw-btn-h;
	border-radius: $mw-radius-pill;
	background: $mw-brand-ink;
	display: flex;
	align-items: center;
	justify-content: center;
}
.chat__cta text {
	font-size: $mw-font-body;
	font-weight: 600;
	color: #ffffff;
}
/* 次级动作「去 AI 问诊」：暖灰浅底 + 细描边 + 品牌链接色（浅底上的可点色）。
   只用既有令牌、不写死颜色；与主按钮**样式不同但同样是真入口**（门禁 D 段钉"主次可区分"）。 */
.chat__cta--ghost {
	margin-top: $mw-space-3;
	background: $mw-canvas;
	border: 1rpx solid $mw-line;
}
.chat__cta--ghost text {
	font-weight: 500;
	color: $mw-brand-ink;
}

/* ⑦ 知识库内容卡 */
.kb {
	margin-top: $mw-space-8;
	padding: $mw-space-4;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
.kb__title {
	display: block;
	font-size: $mw-font-h2;
	font-weight: 600;
	color: $mw-ink;
	margin-bottom: $mw-space-1;
}
.kb__item {
	display: flex;
	align-items: center;
	min-height: $mw-touch-min;
	border-bottom: 1px solid $mw-line;
}
.kb__item:last-child {
	border-bottom: none;
}
.kb__text {
	flex: 1;
	font-size: $mw-font-caption;
	color: $mw-ink;
}
.kb__arrow {
	font-size: 36rpx;
	color: $mw-muted;
}

.note {
	display: block;
	margin-top: $mw-space-4;
	font-size: 22rpx;
	color: $mw-muted;
}
.note--danger {
	color: $mw-danger;
}

/* ==========================================================================
   本机提醒弹窗（软件内弹窗）
   令牌全部来自 uni.scss，**不新增任何令牌值**；颜色角色按设计系统分工：
   琥珀 = 时间/照护状态，落日橙深色 = 浅底上的主按钮，danger = 逾期。
   ========================================================================== */
.mask {
	position: fixed;
	left: 0;
	right: 0;
	top: 0;
	bottom: 0;
	z-index: 60;
	background: rgba(26, 19, 16, 0.55);
	display: flex;
	align-items: center;
	justify-content: center;
	padding: $mw-page-padding;
}
.dialog {
	width: 100%;
	max-width: 640rpx;
	background: $mw-surface;
	border-radius: $mw-radius-card;
	padding: $mw-space-4;
	box-shadow: $mw-shadow-float;
}
.dialog__head {
	display: flex;
	align-items: center;
	justify-content: space-between;
}
.dialog__title {
	font-size: $mw-font-h2;
	font-weight: 600;
	color: $mw-ink;
}
.dialog__close {
	min-width: $mw-touch-min;
	min-height: 56rpx;
	display: flex;
	align-items: center;
	justify-content: flex-end;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}
.dialog__sub {
	display: block;
	margin-top: $mw-space-1;
	font-size: $mw-font-micro;
	color: $mw-muted;
}
.ritem {
	display: flex;
	align-items: center;
	min-height: $mw-touch-min;
	border-bottom: 1px solid $mw-line;
}
.ritem__main {
	flex: 1;
	min-width: 0;
}
.ritem__pet {
	display: block;
	font-size: $mw-font-micro;
	color: $mw-muted;
}
.ritem__title {
	display: block;
	font-size: $mw-font-caption;
	color: $mw-ink;
}
.ritem__arrow {
	margin-left: $mw-space-1;
	font-size: 36rpx;
	color: $mw-muted;
}
/* 分档徽标：色档直接取 dueInfo 的 tone（danger / accent / ink / muted），不另定一套 */
.rtag {
	margin-left: $mw-space-2;
	padding: 2rpx $mw-space-1;
	border-radius: $mw-radius-tag;
	font-size: $mw-font-micro;
	background: $mw-canvas;
	color: $mw-ink-2;
}
.rtag--danger {
	color: $mw-danger;
}
.rtag--accent {
	color: $mw-accent-ink;
}
.rtag--muted {
	color: $mw-muted;
}
.rtag--ink {
	color: $mw-ink-2;
}
.dialog__acts {
	display: flex;
	align-items: center;
	margin-top: $mw-space-4;
}
.dialog__ghost {
	flex: 1;
	height: $mw-btn-h;
	display: flex;
	align-items: center;
	justify-content: center;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-pill;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}
.dialog__primary {
	flex: 2;
	margin-left: $mw-space-2;
	height: $mw-btn-h;
	display: flex;
	align-items: center;
	justify-content: center;
	background: $mw-brand-ink;
	border-radius: $mw-radius-pill;
	font-size: $mw-font-caption;
	font-weight: 600;
	color: #ffffff;
}
.dialog__note {
	display: block;
	margin-top: $mw-space-2;
	font-size: 22rpx;
	color: $mw-muted;
}
</style>