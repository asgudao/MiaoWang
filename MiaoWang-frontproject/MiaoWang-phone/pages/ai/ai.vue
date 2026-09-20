<template>
	<view class="page">
		<!-- App 端逐字流式的载体：renderjs 在视图层跑 fetch，把每个原始分片回传逻辑层。
		     这里只做传输，解析仍走 api/ai.js 的同一份代码（避免两套 SSE 解析必然分叉） -->
		<view
			class="sse-host"
			:sse-args="sseArgs"
			:change:sse-args="sse.start"
			:sse-abort="sseAbort"
			:change:sse-abort="sse.abort"
		></view>

		<!-- 全局 navigationStyle 是 custom，非 tab 页要自绘返回栏 -->
		<view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav__back" @click="goBack">
				<view class="chev chev--left"></view>
			</view>
			<text class="nav__title">AI 问诊</text>
			<view class="nav__actions">
				<text class="nav__btn" @click="toggleHistory">历史</text>
				<text class="nav__btn nav__btn--primary" @click="startNew">新对话</text>
			</view>
		</view>

		<!-- 额度提示 + 会员入口（次数用完时明确给"去开通"，不用弹窗打断）
		     额度拿不到也要说出来 + 给下一步：静默会让用户以为这页根本没这个功能 -->
		<view v-if="quotaText || quotaFailText" class="quota">
			<text class="quota__text">{{ quotaText || quotaFailText }}</text>
			<text v-if="quotaFailText && !loggedIn" class="quota__link" @click="goLogin">去登录</text>
			<text v-else-if="quotaFailText" class="quota__link" @click="reloadQuota">重试</text>
			<text v-else class="quota__link" @click="goVip">{{ quotaOut ? '去开通会员' : '会员' }}</text>
		</view>

		<!-- 历史会话面板（手机屏幕放不下侧栏，做成覆盖层） -->
		<view v-if="showHistory" class="history">
			<view class="history__head">
				<text class="history__title">历史对话</text>
				<text class="history__close" @click="toggleHistory">收起</text>
			</view>
			<text v-if="listLoading" class="history__hint">正在读取</text>
			<text v-else-if="listError" class="history__err">{{ listError }}</text>
			<text v-else-if="!convs.length" class="history__hint">还没有对话记录</text>
			<view v-for="c in convs" :key="c.id" class="conv">
				<view class="conv__main" @click="openConv(c.id)">
					<text class="conv__title">{{ c.title }}</text>
					<text class="conv__meta">{{ c.meta }}</text>
				</view>
				<text class="conv__del" @click="removeConv(c.id)">{{ confirmingId === c.id ? '确认删除' : '删除' }}</text>
			</view>
		</view>

		<scroll-view class="thread" scroll-y :scroll-into-view="anchor" :scroll-with-animation="true">
			<view v-if="messagesLoading" class="state">
				<text class="state__desc">正在读取对话内容</text>
			</view>

			<view v-else-if="!cards.length" class="intro">
				<text class="intro__title">问什么都可以，比如</text>
				<view v-for="q in examples" :key="q" class="intro__ask" @click="send(q)">
					<text class="intro__asktext">{{ q }}</text>
					<view class="chev chev--right"></view>
				</view>
			</view>

			<view v-for="m in cards" :key="m.id" :id="'m' + m.id" class="msg" :class="m.isAi ? 'msg--ai' : 'msg--me'">
				<!-- 思考过程：思考模型才有，后端不落库 → 回放历史时不会有 -->
				<view v-if="m.isAi && m.thinking" class="think" @click="toggleThink(m.id)">
					<view class="think__head">
						<text class="think__dot"></text>
						<text class="think__label">{{ m.thinkHead }}</text>
						<text class="think__fold">{{ m.thinkOpen ? '收起' : '展开' }}</text>
					</view>
					<text v-if="m.thinkOpen" class="think__body">{{ m.thinking }}</text>
				</view>

				<view class="bubble" :class="m.isAi ? 'bubble--ai' : 'bubble--me'">
					<text v-if="m.isAi && m.pending && !m.content" class="typing">{{ m.pendingText }}</text>
					<template v-else>
						<view v-for="(b, bi) in m.blocks" :key="bi" class="line" :class="b.kind === 'li' ? 'line--li' : ''">
							<text v-if="b.marker" class="line__marker">{{ b.marker }}</text>
							<text
								v-for="(p, pi) in b.parts"
								:key="pi"
								class="part"
								:class="p.bold ? 'part--bold' : (p.cite ? 'part--cite' : '')"
								@click="onPartTap(m.id, p.cite)"
							>{{ p.text }}</text>
						</view>
					</template>
				</view>

				<!-- 引用来源：标出引用次数（模型常把一整段都归到 [1]，要让它看起来是有信息的） -->
				<view v-if="m.isAi && m.sources.length" class="srcs">
					<view
						v-for="s in m.sources"
						:key="s.id"
						class="src"
						:class="s.focus ? 'src--focus' : (s.cited ? 'src--cited' : '')"
					>
						<text class="src__idx">{{ s.idx }}</text>
						<text class="src__title">{{ s.title }}</text>
						<text class="src__sp">{{ s.sp }}</text>
						<text v-if="s.count" class="src__count">引用 {{ s.count }} 次</text>
					</view>
					<text class="srcs__note">回答里的编号对应这里，点编号可以对照</text>
				</view>

				<view v-if="m.isAi && m.canRate" class="rate">
					<text class="rate__btn" :class="m.feedback === 1 ? 'rate__btn--on' : ''" @click="rate(m.id, 1)">有帮助</text>
					<text class="rate__btn" :class="m.feedback === 2 ? 'rate__btn--bad' : ''" @click="rate(m.id, 2)">没帮助</text>
					<text v-if="m.feedback" class="rate__done">已反馈</text>
				</view>

				<text v-if="m.error" class="msg__error">{{ m.error }}</text>
			</view>

			<view class="thread__pad"></view>
		</scroll-view>

		<view class="composer">
			<textarea
				class="composer__input"
				v-model="draft"
				:disabled="streaming"
				:placeholder="placeholder"
				auto-height
				:maxlength="-1"
			/>
			<view v-if="streaming" class="composer__send composer__send--stop" @click="stop">
				<text>停止</text>
			</view>
			<view v-else class="composer__send" :class="draft.trim() ? '' : 'composer__send--off'" @click="send()">
				<text>发送</text>
			</view>
		</view>
		<text class="note">{{ noteText }}</text>
	</view>
</template>

<script>
/**
 * AI 问诊（手机端 / Options API）
 *
 * 模板只访问实例属性（data/computed/methods）—— store 与 utils 一律不进模板，
 * 文本解析、引用计数、来源展开全在 computed 里做完（白屏事故的根治办法，见 miaowang-phone-frontend）。
 *
 * 流式能力说明：H5 走浏览器原生 fetch 逐块流式；App 逻辑层没有 fetch，退化为
 * "整段返回后一次性解析 SSE"（内容一样，只是不分段出现）。见 api/ai.js 顶部注释。
 */
import * as aiApi from '../../api/ai.js'
import { ROLE_AI } from '../../api/ai.js'
import { ApiError } from '../../utils/request.js'
import { ai } from '../../store/ai.js'
import { auth } from '../../store/auth.js'
import { citationCounts, parseAnswer, relativeTime, speciesLabel } from '../../utils/ai.js'

export default {
	data () {
		return {
			statusBarHeight: 0,
			draft: '',
			showHistory: false,
			confirmingId: null,
			/** 当前高亮的引用：{ id, n } */
			focused: null,
			/** 思考块展开状态：{ [消息id]: true/false } */
			thinkOpen: {},
			/** 本轮已跑秒数（思考要十几秒，没有计时会被当成卡死） */
			elapsed: 0,
			timer: null,
			/** renderjs 下行参数（换新对象触发 :change 绑定）与下行中止信号 */
			sseArgs: null,
			sseAbort: 0,
			sseNonce: 0,
			/** 本次是否已降级为整段解析（renderjs 的 fetch 被拦时会降级） */
			appFallback: false,
			examples: [
				'狗狗一直呕吐怎么办？',
				'猫可以喝牛奶吗？',
				'三个月大的猫该打什么疫苗？',
				'怎么判断猫咪发烧了？'
			]
		}
	},

	computed: {
		streaming () {
			return ai.streaming
		},
		loggedIn () {
			return auth.isLoggedIn()
		},
		messagesLoading () {
			return ai.messagesLoading
		},
		listLoading () {
			return ai.listLoading
		},
		listError () {
			return ai.listError
		},
		/** 会话列表：摊平成显示字段（分隔用逗号，不用装饰性的"·"） */
		convs () {
			return ai.conversations.map(function (c) {
				const time = relativeTime(c.lastMessageTime)
				const count = (c.messageCount || 0) + ' 条'
				return {
					id: c.id,
					title: c.title,
					meta: time ? time + '，' + count : count
				}
			})
		},
		quotaText () {
			const q = ai.quota
			if (!q) return ''
			if (q.remaining < 0) return 'AI 问答不限次数'
			return '本' + (q.desc || '月') + '还剩 ' + q.remaining + ' 次问答'
		},
		/** 额度没读到：给一句可见的说明 + 下一步（网络类文案太长就只留短句，避免占满首屏） */
		quotaFailText () {
			const msg = ai.quotaError
			if (!msg) return ''
			if (!this.loggedIn) return '还没登录，额度和问答都要登录后才能用'
			if (msg.indexOf('连不上后端') >= 0) return '额度没读到，连不上后端；问答仍可以试'
			return '额度没读到：' + msg
		},
		/** 次数已用完（0 不是"不限"，剩下的负数才是不限）→ 入口文案换成引导开通 */
		quotaOut () {
			const q = ai.quota
			return Boolean(q && q.remaining === 0)
		},
		/** 消息卡片：解析正文、统计引用、展开来源，全部在这里做完 */
		cards () {
			const self = this
			return ai.messages.map(function (m) {
				const isAi = m.role === ROLE_AI
				const counts = citationCounts(m.content)
				let open = self.thinkOpen[m.id]
				if (open === undefined) open = !m.thinkingDone
				return {
					id: m.id,
					isAi: isAi,
					content: m.content,
					blocks: parseAnswer(m.content),
					thinking: m.thinking || '',
					thinkOpen: open,
					thinkHead: m.thinkingDone ? '模型的思考过程' : '模型正在思考 ' + self.elapsed + 's',
					pending: !!m.pending,
					pendingText: m.thinking
						? '思考完了，正在组织回答'
						: '正在检索知识库并生成回答 ' + self.elapsed + 's',
					sources: (m.sources || []).map(function (s, i) {
						const n = String(i + 1)
						const count = counts[n] || 0
						return {
							id: s.id,
							idx: i + 1,
							title: s.title,
							sp: speciesLabel(s.species),
							count: count,
							cited: count > 0,
							focus: !!self.focused && self.focused.id === m.id && self.focused.n === i + 1
						}
					}),
					canRate: isAi && m.id > 0 && !m.pending,
					feedback: m.feedback,
					error: m.error || ''
				}
			})
		},
		/** 滚动锚点：始终追最后一条 */
		anchor () {
			const list = ai.messages
			return list.length ? 'm' + list[list.length - 1].id : ''
		},
		placeholder () {
			return ai.streaming ? '正在回答，可以点右侧停止' : '描述症状或想问的问题'
		},
		canStreamFlag () {
			return aiApi.canStream()
		},
		noteText () {
			if (!this.canStreamFlag) {
				const base = '这台设备（App 端）会走 renderjs 逐字流式；若被系统拦住，会自动改为整段出现，内容与来源不受影响'
				return this.appFallback ? '本次已自动降级为整段解析（逐字流式被系统拦住）' : base
			}
			return '回答会消耗 AI 问答次数，一次生成通常十几秒'
		}
	},

	onLoad () {
		try {
			const info = uni.getSystemInfoSync()
			this.statusBarHeight = info.statusBarHeight || 0
		} catch (e) {
			this.statusBarHeight = 0
		}
		ai.fetchConversations()
		ai.fetchQuota()
	},

	onShow () {
		// 从登录页回来：上一次读额度必然失败（未登录），重新读一次，别把旧结论留在页面上
		if (this.loggedIn && ai.quotaError) ai.fetchQuota()
	},

	onUnload () {
		this.clearTimer()
	},

	methods: {
		goBack () {
			uni.navigateBack()
		},

		/** 去会员页（额度用完时这里是唯一的下一步） */
		goVip () {
			uni.navigateTo({ url: '/pages/vip/vip' })
		},

		/** 额度没读到时的重试：重新读一次，结果由 store 决定（失败会继续显示原因） */
		reloadQuota () {
			ai.fetchQuota()
			uni.showToast({ title: '正在重新读取额度', icon: 'none' })
		},

		goLogin () {
			uni.navigateTo({ url: '/pages/login/login' })
		},

		toggleHistory () {
			this.showHistory = !this.showHistory
			this.confirmingId = null
			if (this.showHistory) ai.fetchConversations()
		},

		startNew () {
			ai.startNew()
			this.showHistory = false
			this.focused = null
			this.thinkOpen = {}
			this.draft = ''
		},

		async openConv (id) {
			this.showHistory = false
			this.focused = null
			this.thinkOpen = {}
			await ai.open(id)
		},

		async removeConv (id) {
			if (this.confirmingId !== id) {
				this.confirmingId = id
				return
			}
			this.confirmingId = null
			try {
				await ai.remove(id)
			} catch (e) {
				uni.showToast({ title: '删除失败，稍后再试', icon: 'none' })
			}
		},

		startTimer () {
			this.clearTimer()
			this.elapsed = 0
			const self = this
			this.timer = setInterval(function () {
				self.elapsed += 1
			}, 1000)
		},

		clearTimer () {
			if (this.timer) {
				clearInterval(this.timer)
				this.timer = null
			}
		},

		async send (text) {
			const raw = text === undefined ? this.draft : text
			const q = String(raw === undefined || raw === null ? '' : raw).trim()
			if (!q || ai.streaming) return
			const self = this
			this.draft = ''
			this.focused = null
			this.appFallback = false
			this.startTimer()
			// H5 的逻辑层自带 fetch → 用默认那路；App 逻辑层没有 fetch → 交给 renderjs 逐字回传
			const transport = aiApi.canStream()
				? null
				: function (question, conversationId, handlers) {
					return self.makeSseTransport(question, conversationId, handlers)
				}
			await ai.ask(q, transport)
			this.clearTimer()
		},

		/**
		 * App 端 transport：renderjs 逐字流式。
		 *
		 * renderjs 只负责把原始分片搬回来，解析仍用 api/ai.js 的 dispatchBuffer。
		 * 若一个分片都没收到就失败（真机 webview 的 fetch 可能被 CORS 拦住），
		 * 自动降级为"整段收完再解析" —— 宁可退化成整段出现，也不留一个假死的界面。
		 */
		makeSseTransport (question, conversationId, handlers) {
			const self = this
			const nonce = this.sseNonce + 1
			this.sseNonce = nonce
			let resolveDone = null
			let rejectDone = null
			const promise = new Promise(function (resolve, reject) {
				resolveDone = resolve
				rejectDone = reject
			})
			const session = {
				nonce: nonce,
				question: question,
				conversationId: conversationId || 0,
				handlers: handlers,
				buffer: '',
				gotChunk: false,
				fallback: null
			}
			session.finish = function (errPayload) {
				if (!errPayload) {
					resolveDone()
					return
				}
				if (session.gotChunk) {
					rejectDone(new ApiError('SSE', errPayload.message || '流式连接中断'))
					return
				}
				// 一个分片都没收到 → 降级（多半是 renderjs 的 fetch 被 CORS 拦了）
				self.appFallback = true
				const fb = aiApi.bufferedStream(session.question, session.conversationId, session.handlers)
				session.fallback = fb
				fb.done.then(function () { resolveDone() }, function (e) { rejectDone(e) })
			}
			this.sseSession = session
			this.sseArgs = {
				nonce: nonce,
				question: question,
				conversationId: conversationId || 0,
				request: aiApi.buildStreamRequest(question, conversationId)
			}
			return {
				abort: function () {
					self.sseAbort = nonce
					if (session.fallback && session.fallback.abort) session.fallback.abort()
				},
				done: promise
			}
		},

		/** renderjs 回传入口：chunk（原始分片）/ end / error */
		onSseEvent (payload) {
			const s = this.sseSession
			if (!s || !payload || payload.nonce !== s.nonce) return
			if (payload.type === 'chunk') {
				s.gotChunk = true
				s.buffer = aiApi.dispatchBuffer(s.buffer + payload.text, s.handlers)
			} else if (payload.type === 'end') {
				s.finish(null)
			} else if (payload.type === 'error') {
				s.finish(payload)
			}
		},

		stop () {
			ai.stop()
			this.clearTimer()
		},

		/** 点引用角标：高亮对应来源（再点一次取消） */
		onPartTap (messageId, cite) {
			if (!cite) return
			const same = this.focused && this.focused.id === messageId && this.focused.n === cite
			this.focused = same ? null : { id: messageId, n: cite }
		},

		toggleThink (messageId) {
			const next = Object.assign({}, this.thinkOpen)
			const cur = this.thinkOpen[messageId]
			let now = cur
			if (now === undefined) {
				// 默认值取决于思考是否结束：进行中展开，结束后收起
				const m = ai.find(messageId)
				now = m ? !m.thinkingDone : false
			}
			next[messageId] = !now
			this.thinkOpen = next
		},

		async rate (messageId, type) {
			try {
				await ai.rate(messageId, type)
			} catch (e) {
				uni.showToast({ title: '评价失败，稍后再试', icon: 'none' })
			}
		}
	}
}
</script>

<script module="sse" lang="renderjs">
/**
 * App 端逐字流式的传输层（跑在视图层 webview 里）
 *
 * 为什么需要它：`uni.request` 的 enableChunked / onChunkReceived 是**微信小程序**能力，
 * H5 与 App 都不支持（官方参数表标注）。而 renderjs 跑在 webview 里，能拿到浏览器原生的
 * fetch + ReadableStream，所以逐字流式只能这样做。
 *
 * 分工：**这里只搬分片**，通过 callMethod 把原始文本回传逻辑层，由 api/ai.js 统一解析。
 * 不在 renderjs 里再写一套 SSE 解析 —— 两套解析必然分叉，出了 bug 极难查。
 */
export default {
	methods: {
		start (newValue, oldValue, ownerInstance, instance) {
			if (!newValue || !newValue.request) return
			const nonce = newValue.nonce
			const prev = instance.__mwSse
			if (prev && prev.reader) {
				// 上一轮还没结束（换会话/快速连问）先掐掉，避免两条流往同一个界面写
				try { prev.reader.cancel() } catch (e) { /* 忽略 */ }
			}
			const state = { nonce: nonce, aborted: false, reader: null }
			instance.__mwSse = state

			const emit = function (type, extra) {
				try {
					ownerInstance.callMethod('onSseEvent', Object.assign({ nonce: nonce, type: type }, extra || {}))
				} catch (e) {
					// 逻辑层已卸载，忽略
				}
			}

			const req = newValue.request
			fetch(req.url, { method: 'POST', headers: req.header, body: req.body })
				.then(function (res) {
					if (res.status === 401 || res.status === 403) {
						emit('error', { message: '登录已过期，请重新登录' })
						return
					}
					const ctype = res.headers.get('content-type') || ''
					if (ctype.indexOf('text/event-stream') < 0) {
						// 业务错误（额度用完等）回的是普通 JSON 信封
						return res.text().then(function (text) {
							let message = 'AI 服务返回了非流式响应（HTTP ' + res.status + '）'
							try {
								const env = JSON.parse(text)
								if (env && env.message) message = env.message
							} catch (e) { /* 保持默认文案 */ }
							emit('error', { message: message })
						})
					}
					if (!res.body || !res.body.getReader) {
						emit('error', { message: '这个 webview 不支持流式读取' })
						return
					}
					const reader = res.body.getReader()
					state.reader = reader
					const decoder = new TextDecoder()
					const pump = function () {
						if (state.aborted || instance.__mwSse !== state) {
							try { reader.cancel() } catch (e) { /* 忽略 */ }
							return
						}
						return reader.read().then(function (r) {
							if (r.done) {
								emit('end')
								return
							}
							emit('chunk', { text: decoder.decode(r.value, { stream: true }) })
							return pump()
						})
					}
					return pump()
				})
				.catch(function (e) {
					const msg = e && e.message ? e.message : String(e)
					emit('error', { message: '逐字流式连不上：' + msg })
				})
		},

		abort (newValue, oldValue, ownerInstance, instance) {
			const state = instance.__mwSse
			if (!state) return
			state.aborted = true
			if (state.reader) {
				try { state.reader.cancel() } catch (e) { /* 忽略 */ }
			}
		}
	}
}
</script>

<style lang="scss" scoped>
.sse-host {
	width: 0;
	height: 0;
	overflow: hidden;
}
.page {
	display: flex;
	flex-direction: column;
	height: 100vh;
	background: $mw-canvas;
}

/* ---- 顶部返回栏 ---- */
.nav {
	display: flex;
	align-items: center;
	padding: 0 $mw-space-3;
	background: $mw-surface;
	border-bottom: 1rpx solid $mw-line;
}
.nav__back {
	display: flex;
	align-items: center;
	justify-content: center;
	width: $mw-touch-min;
	height: $mw-touch-min;
}
.nav__title {
	flex: 1;
	font-size: $mw-font-h2;
	font-weight: 600;
	color: $mw-ink;
}
.nav__actions {
	display: flex;
	align-items: center;
}
.nav__btn {
	padding: 0 $mw-space-2;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}
.nav__btn--primary {
	color: $mw-brand-ink;
	font-weight: 600;
}

/* 自绘箭头（素材已废弃，不用图标字体） */
.chev {
	width: 20rpx;
	height: 20rpx;
	border-top: 4rpx solid $mw-ink-2;
	border-right: 4rpx solid $mw-ink-2;
}
.chev--left {
	transform: rotate(-135deg);
}
.chev--right {
	transform: rotate(45deg);
	border-color: $mw-muted;
}

.quota {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: $mw-space-2 $mw-space-3;
	background: $mw-surface;
	border-bottom: 1rpx solid $mw-line;
}
.quota__text {
	flex: 1;
	min-width: 0;
	font-size: $mw-font-micro;
	color: $mw-ink-2;
}
.quota__link {
	margin-left: $mw-space-2;
	font-size: $mw-font-micro;
	font-weight: 600;
	color: $mw-brand-ink;
}

/* ---- 历史会话覆盖层 ---- */
.history {
	max-height: 60vh;
	overflow: auto;
	padding: $mw-space-3;
	background: $mw-surface;
	border-bottom: 1rpx solid $mw-line;
}
.history__head {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: $mw-space-2;
}
.history__title {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-ink;
}
.history__close {
	font-size: $mw-font-micro;
	color: $mw-brand-ink;
}
.history__hint {
	font-size: $mw-font-micro;
	color: $mw-muted;
}
.history__err {
	font-size: $mw-font-micro;
	color: $mw-danger;
}
.conv {
	display: flex;
	align-items: center;
	border-bottom: 1rpx solid $mw-line;
}
.conv__main {
	flex: 1;
	padding: $mw-space-2 0;
}
.conv__title {
	display: block;
	font-size: $mw-font-caption;
	color: $mw-ink;
}
.conv__meta {
	display: block;
	margin-top: 4rpx;
	font-size: $mw-font-micro;
	color: $mw-muted;
}
.conv__del {
	padding: 0 $mw-space-2;
	font-size: $mw-font-micro;
	color: $mw-danger;
}

/* ---- 消息流 ---- */
/*
	⚠️ `box-sizing: border-box` 是**真机宽度 bug 的修复**（与姊妹页 pages/ai/pet.vue 同一根因，长说明在那边）：
	本页 `.thread` 也是 `<scroll-view>`，框架基础样式给 `uni-scroll-view` 写了 `display:block; width:100%`
	（真机构建产物 `unpackage/dist/dev/app-plus/app.css` 实测）；`width` 不是 `auto` 时 `align-items: stretch`
	退回 `flex-start`（MDN `align-items` 的 stretch 条），默认又是 content-box、框架没有全局 border-box
	⇒ 这个 padding 落在 `100%` **之外**：外沿 = 750 + 32×2 = **814rpx**，整列右移 32rpx、右侧恒越屏 64rpx
	（三档屏宽 27.31 / 32.00 / 35.33px），被 `body{overflow-x:hidden}` 吃掉。

	后果与本页特有的地方：右对齐的「我」气泡贴在那条越界边界上 → 气泡右侧 32rpx 在屏外、
	每行文字再被裁 8rpx（4px@375 ≈ 1/4 个汉字）。**本页 AI 侧是三块 92% 的盒子（`.bubble` / `.think` / `.srcs`）**，
	它们左对齐、文字右边界都在屏内，所以症状同样只出在用户这一侧。

	border-box 让 padding 收进 `100%` 之内：内容盒 = 750 − 64 = **686rpx**，外沿恰好 750rpx，不再越界。
*/
.thread {
	flex: 1;
	overflow: hidden;
	box-sizing: border-box;
	padding: 0 $mw-page-padding;
}
.thread__pad {
	height: $mw-space-4;
}
.state {
	padding: $mw-space-8 0;
	text-align: center;
}
.state__desc {
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}

.intro {
	padding: $mw-space-4 0;
}
.intro__title {
	display: block;
	margin-bottom: $mw-space-2;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}
.intro__ask {
	display: flex;
	align-items: center;
	justify-content: space-between;
	min-height: $mw-touch-min;
	margin-bottom: $mw-space-2;
	padding: 0 $mw-space-3;
	background: $mw-surface;
	border-radius: $mw-radius-card;
}
.intro__asktext {
	font-size: $mw-font-body;
	color: $mw-ink;
}

.msg {
	margin-top: $mw-space-3;
}
.msg--me {
	display: flex;
	flex-direction: column;
	align-items: flex-end;
}
/*
	气泡宽度**相对列宽**（不写死 rpx）。本页 AI 侧有三块同宽的盒子（`.bubble` / `.think` / `.srcs`），
	原设计三块都是 `max-width: 92%` 的 content-box，改宽度必须**一起改**，否则三块会错位：
	  旧布局（列内容盒 = 750rpx）：.bubble 外沿 0.92×750 + 48 + 2 = **740rpx**；.think 同样 740rpx（两者对齐）；
	                              .srcs 是块级、无 padding → 外沿 0.92×750 = **690rpx**
	  只修列宽不动气泡（列内容盒 686rpx，92% 仍是 content-box）：.bubble 外沿 = 0.92×686 + 48 + 2 = **681rpx**
	                              —— 距列宽 686rpx 只剩 **5rpx**，中文字体稍宽就溢出被裁（.think 同样 681rpx）
	  新：三块统一 `box-sizing: border-box; max-width: 84%` → 外沿都是 0.84×686 = **576.24rpx**（三者仍对齐）

	1) `box-sizing: border-box`：`max-width` 量到**外沿**，占比可以直接复算（= 84%），不再有那 81rpx 的暗账。
	2) `max-width: 84%`：列宽 686rpx → 外沿 576rpx、对侧留 ≥16%（≥110rpx ≈ 55px@375），
	   文字列 = 576 − 48 = **528rpx ≈ 264px@375 ≈ 17.6 汉字/行**，够读且给字体差异留余量。
	3) `overflow-wrap: anywhere` + `word-break: break-word`：URL / 长英文这类**无空格长串**必须在盒内折行
	   （前者连 min-content 都按单字符算 —— 本页 `.line--li` 是 flex 行，这条直接影响 flex 项能不能收缩；
	   后者兜更老的内核）。两个都是可继承属性，`.part` / `.line__marker` 也跟着生效。**不许**横向滚动兜底。
	`min-width: 0` 是防御性的：列方向（`.msg--me` 是 flex column）`min-width: auto` 本来就是 0。
*/
.bubble {
	display: inline-block;
	box-sizing: border-box;
	max-width: 84%;
	min-width: 0;
	padding: $mw-space-2 $mw-space-3;
	border-radius: $mw-radius-card;
	overflow-wrap: anywhere;
	word-break: break-word;
}
.bubble--ai {
	background: $mw-surface;
	border: 1rpx solid $mw-line;
}
.bubble--me {
	background: $mw-brand-ink;
}
.line {
	margin-bottom: 6rpx;
}
.line--li {
	display: flex;
}
.line__marker {
	margin-right: 8rpx;
	color: $mw-brand-ink;
	font-size: $mw-font-body;
	line-height: 1.75;
}
/* `min-width: 0` 在本页**有实际作用**（不是纯防御）：`.line--li` 是 `display: flex` 的行，
   `.part` 是它的 flex 项，`min-width: auto` 会拿内容的 min-content 当最小宽度 —— 一个长 URL
   就能把整行撑破气泡；显式 0 + 上面的 `overflow-wrap: anywhere` 才真的收得住。 */
.part {
	min-width: 0;
	font-size: $mw-font-body;
	line-height: 1.75;
	color: $mw-ink;
}
/* 用户自己的气泡里文字要反色 */
.msg--me .part {
	color: #ffffff;
}
.msg--me .line__marker {
	color: #ffffff;
}
.part--bold {
	font-weight: 600;
}
.part--cite {
	margin: 0 4rpx;
	padding: 0 8rpx;
	background: rgba(194, 65, 12, 0.12);
	border-radius: $mw-radius-tag;
	font-size: $mw-font-micro;
	color: $mw-brand-ink;
}
.typing {
	font-size: $mw-font-body;
	color: $mw-ink-2;
}

/* ---- 思考过程 ---- */
.think {
	box-sizing: border-box;
	max-width: 84%;
	margin-bottom: $mw-space-1;
	padding: $mw-space-2 $mw-space-3;
	background: $mw-surface;
	border: 1rpx dashed $mw-line;
	border-radius: $mw-radius-card;
	overflow: hidden;
}
.think__head {
	display: flex;
	align-items: center;
}
.think__dot {
	width: 12rpx;
	height: 12rpx;
	margin-right: 10rpx;
	background: $mw-brand-ink;
	border-radius: $mw-radius-pill;
	opacity: 0.7;
}
.think__label {
	flex: 1;
	font-size: $mw-font-micro;
	color: $mw-ink-2;
}
.think__fold {
	font-size: $mw-font-micro;
	color: $mw-muted;
}
/* ⚠️ 这里**不能**只写 max-height：文本会溢出盒子盖住下面的来源列表（真机上踩过）。
   思考过程很长（实测上百个分片），靠"正文开始后自动收起"控制观感，不靠裁剪。 */
.think__body {
	display: block;
	margin-top: $mw-space-1;
	font-size: $mw-font-micro;
	line-height: 1.7;
	color: $mw-ink-2;
	word-break: break-all;
}

/* ---- 引用来源 ---- */
/* 与 `.bubble` / `.think` **同宽同规则**（84% + border-box），否则三块会错位（见 `.bubble` 上方的复算） */
.srcs {
	box-sizing: border-box;
	max-width: 84%;
	margin-top: $mw-space-1;
	overflow-wrap: anywhere;
	word-break: break-word;
}
.src {
	display: flex;
	align-items: center;
	margin-bottom: 8rpx;
	padding: $mw-space-1 $mw-space-2;
	background: $mw-surface;
	border: 1rpx solid $mw-line;
	border-radius: $mw-radius-tag;
}
.src--cited {
	background: rgba(194, 65, 12, 0.08);
}
.src--focus {
	border-color: $mw-brand-ink;
}
.src__idx {
	margin-right: 10rpx;
	font-size: $mw-font-micro;
	font-weight: 600;
	color: $mw-brand-ink;
}
.src__title {
	flex: 1;
	font-size: $mw-font-micro;
	color: $mw-ink;
}
.src__sp {
	margin-left: 10rpx;
	font-size: $mw-font-micro;
	color: $mw-muted;
}
.src__count {
	margin-left: 10rpx;
	font-size: $mw-font-micro;
	font-weight: 600;
	color: $mw-brand-ink;
}
.srcs__note {
	font-size: $mw-font-micro;
	color: $mw-muted;
}

/* ---- 反馈 ---- */
.rate {
	display: flex;
	align-items: center;
	margin-top: 8rpx;
}
.rate__btn {
	margin-right: $mw-space-2;
	padding: 6rpx $mw-space-2;
	border: 1rpx solid $mw-line;
	border-radius: $mw-radius-tag;
	font-size: $mw-font-micro;
	color: $mw-ink-2;
}
.rate__btn--on {
	border-color: $mw-brand-ink;
	color: $mw-brand-ink;
}
.rate__btn--bad {
	border-color: $mw-danger;
	color: $mw-danger;
}
.rate__done {
	font-size: $mw-font-micro;
	color: $mw-muted;
}
.msg__error {
	display: block;
	margin-top: 6rpx;
	font-size: $mw-font-micro;
	color: $mw-danger;
}

/* ---- 输入区 ---- */
.composer {
	display: flex;
	align-items: flex-end;
	padding: $mw-space-2 $mw-space-3;
	padding-bottom: calc(#{$mw-space-2} + env(safe-area-inset-bottom));
	background: $mw-surface;
	border-top: 1rpx solid $mw-line;
}
/*
	`min-width: 0`：框架基础样式给 `uni-textarea` 写了 `width:300px; height:150px`（`app.css` 实测；
	高度被 `auto-height` 的 `fit-content!important` 收掉，宽度这条仍在）。`flex: 1` 的自动最小尺寸
	（`min-width: auto`）会参考"指定尺寸建议"，显式 0 把收缩权交回 flex —— 否则输入框不收缩时
	会把「发送」按钮顶出屏幕右边。（与姊妹页 pet.vue 同一条加固；算下来当前内核 min-content 很小，
	**不是**本次宽度 bug 的成因，如实标注为加固。）
*/
.composer__input {
	flex: 1;
	min-width: 0;
	min-height: 72rpx;
	max-height: 240rpx;
	padding: $mw-space-2;
	background: $mw-canvas;
	border-radius: $mw-radius-input;
	font-size: $mw-font-body;
	color: $mw-ink;
}
.composer__send {
	display: flex;
	align-items: center;
	justify-content: center;
	width: 140rpx;
	height: 72rpx;
	margin-left: $mw-space-2;
	background: $mw-brand-ink;
	border-radius: $mw-radius-input;
	font-size: $mw-font-caption;
	color: #ffffff;
}
.composer__send--off {
	opacity: 0.45;
}
.composer__send--stop {
	background: $mw-ink-2;
}
.note {
	display: block;
	padding: 0 $mw-space-3 $mw-space-2;
	padding-bottom: calc(#{$mw-space-2} + env(safe-area-inset-bottom));
	background: $mw-surface;
	font-size: $mw-font-micro;
	color: $mw-muted;
}
</style>