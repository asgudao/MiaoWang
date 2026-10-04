<template>
	<view class="page">
		<!-- 全局 navigationStyle 是 custom，非 tab 页要自绘返回栏（与 pages/ai/ai.vue 同一套写法） -->
		<view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav__back" @click="goBack">
				<view class="chev chev--left"></view>
			</view>
			<text class="nav__title">宠物对话</text>
		</view>

		<!-- 未登录软门槛：**一个请求都不发**（三个端点全在网关注册的鉴权之后），页内给真去处 -->
		<view v-if="chat.needLogin" class="gate">
			<text class="gate__title">登录后就能跟它说话</text>
			<text class="gate__desc">它记得你说过什么，所以对话按账号走；这里没有游客模式</text>
			<view class="gate__btn" @click="goLogin"><text>去登录</text></view>
		</view>

		<template v-else>
			<!-- 额度：数字与周期描述全部来自后端（/vip/benefit 或 /chat 的 quotaRemaining），前端不自己算限额 -->
			<view v-if="quotaLine || quotaFailLine" class="quota">
				<text class="quota__text">{{ quotaLine || quotaFailLine }}</text>
				<text v-if="quotaFailLine" class="quota__link" @click="reloadQuota">重试</text>
				<text v-else class="quota__link" @click="goVip">{{ chat.quotaOut ? '去开通会员' : '会员' }}</text>
			</view>

			<scroll-view class="thread" scroll-y :scroll-into-view="anchor" :scroll-with-animation="true">
				<view v-if="chat.historyLoading" class="state">
					<text class="state__desc">正在读你们的对话</text>
				</view>

				<view v-else-if="chat.historyError" class="state">
					<text class="state__err">{{ chat.historyError }}</text>
					<view class="state__btn" @click="loadHistory"><text>重试</text></view>
				</view>

				<template v-else>
					<!-- 没有宠物档案：文案就是后端 B000906 的原文，去处是真实的建宠物页 -->
					<view v-if="chat.hasProfile === false" class="notice">
						<text class="notice__text">{{ noProfileText }}</text>
						<view class="notice__btn" @click="goAddPet"><text>去添加宠物</text></view>
					</view>

					<view v-if="!bubbles.length" class="intro">
						<text class="intro__title">还没跟它说过话</text>
						<text class="intro__desc">它知道自己的名字、品种、年龄，也知道你怎么叫它</text>
					</view>

					<view
						v-for="b in bubbles"
						:id="'m' + b.id"
						:key="b.id"
						class="msg"
						:class="b.isPet ? 'msg--pet' : 'msg--me'"
					>
						<view v-if="b.isPet" class="msg__who">
							<text class="msg__name">{{ b.name }}</text>
							<text v-if="b.isGreeting" class="msg__tag">它先开口</text>
						</view>
						<view class="bubble" :class="b.isPet ? 'bubble--pet' : 'bubble--me'">
							<text v-if="b.pending && !b.text" class="bubble__pending">{{ b.pendingText }}</text>
							<text v-else class="bubble__text">{{ b.text }}</text>
						</view>
						<text class="msg__time">{{ b.timeText }}</text>
					</view>

					<!-- 问候没生成出来 / 没有档案：静音说明，绝不把整页变成错误页 -->
					<text v-if="chat.greetNote" class="note">{{ chat.greetNote }}</text>
					<text v-if="limitNote" class="note">{{ limitNote }}</text>
					<view class="thread__pad"></view>
				</template>
			</scroll-view>

			<!-- 发送失败：后端文案原样展示；额度用完时额外给「去开通会员」这一个动作 -->
			<view v-if="chat.sendError" class="err">
				<text class="err__text">{{ chat.sendError }}</text>
				<text v-if="chat.quotaOut" class="err__link" @click="goVip">去开通会员</text>
			</view>

			<view class="composer">
				<textarea
					class="composer__input"
					v-model="chat.draft"
					:disabled="chat.sending"
					:placeholder="placeholder"
					auto-height
					:maxlength="-1"
				/>
				<view class="composer__send" :class="canSend ? '' : 'composer__send--off'" @click="send">
					<text>{{ chat.sending ? '发送中' : '发送' }}</text>
				</view>
			</view>
			<text v-if="lengthHint" class="note note--warn">{{ lengthHint }}</text>
			<text class="note note--foot">{{ footNote }}</text>
		</template>
	</view>
</template>

<script>
/**
 * 宠物对话（手机端 / Options API）—— 档 1：**非流式**一问一答 + 宠物人设 + 会话历史
 *
 * 与 `pages/ai/ai.vue`（AI 问诊 = ai-knowledge 的 RAG）是**两个模块、两个服务名**：
 * 这里是 `miaowang-ai-pet`，没有知识库检索、没有 sources、没有思考过程，**也没有流式**。
 * 后端就是一次 `chatModel.call()`（回复 1~2 句、平均 ~14 token / ~683ms），做流式只增加复杂度，
 * 所以本页**没有 renderjs / fetch / onChunk**，一次请求拿一个完整对象。
 *
 * 状态机不在这个文件里：`utils/aiPet.js` 是**可离线执行的纯函数**（门禁会用它跑真实 fixture），
 * 页面只做三件事 —— 发请求、把结果交给那些函数、把 `chat.*` 显示出来。
 * 模板只访问实例属性（data/computed/methods），不出现 store/utils 对象（白屏事故的根治办法）。
 */
import { HISTORY_LIMIT, chatWithPet, getChatQuota, greetPet, listPetMessages } from '../../api/aiPet.js'
import { listMyPets } from '../../api/pet.js'
import { auth } from '../../store/auth.js'
import {
	NO_PROFILE_TEXT,
	applyBenefit,
	applyChatFailure,
	applyChatSuccess,
	applyGreet,
	applyGreetError,
	applyHistory,
	applyHistoryError,
	applyQuotaError,
	beginSend,
	historyLimitNote,
	initialChatState,
	isUnauthorized,
	petBubbles,
	quotaFailText,
	quotaText
} from '../../utils/aiPet.js'

/** B000909 的阈值（`AiPetErrorCodeEnum:"一次最多说 500 字"`，后端可由 miaowang.ai-pet.max-question-length 调） */
const MAX_QUESTION_HINT = 500

export default {
	data () {
		return {
			statusBarHeight: 0,
			/** 全部对话状态（字段清单见 utils/aiPet.js 的 initialChatState） */
			chat: initialChatState()
		}
	},

	computed: {
		bubbles () {
			return petBubbles(this.chat)
		},
		quotaLine () {
			return quotaText(this.chat.quota)
		},
		quotaFailLine () {
			return quotaFailText(this.chat.quotaError, auth.isLoggedIn())
		},
		noProfileText () {
			return NO_PROFILE_TEXT
		},
		limitNote () {
			return historyLimitNote(this.chat.messages.length, HISTORY_LIMIT)
		},
		lengthHint () {
			const n = String(this.chat.draft || '').length
			if (n <= MAX_QUESTION_HINT) return ''
			return '已经 ' + n + ' 字，它一次最多听 ' + MAX_QUESTION_HINT + ' 字，分几次说吧'
		},
		canSend () {
			return !!String(this.chat.draft || '').trim() && !this.chat.sending && !this.chat.needLogin
		},
		placeholder () {
			return this.chat.sending ? '它正在想…' : '跟它说点什么'
		},
		/** 滚动锚点：始终追最后一条 */
		anchor () {
			const list = this.chat.messages
			return list.length ? 'm' + list[list.length - 1].id : ''
		},
		footNote () {
			return '聊天会消耗次数（它主动打招呼不消耗）；生成回复时它只看你们最近 6 轮左右，更早的话记不住'
		}
	},

	onLoad (query) {
		try {
			const info = uni.getSystemInfoSync()
			this.statusBarHeight = info.statusBarHeight || 0
		} catch (e) {
			this.statusBarHeight = 0
		}
		const raw = query && query.pid ? Number(query.pid) : NaN
		this.chat.pid = isFinite(raw) && raw > 0 ? raw : null
		if (!auth.isLoggedIn()) {
			// 软门槛：未登录**一个请求都不发**（省掉三条必然 state:-1 的往返）
			this.chat.needLogin = true
			return
		}
		this.bootstrap()
	},

	onShow () {
		// 从登录页回来：上一次必然读不到任何东西，重来一遍，别把旧结论留在页面上
		if (this.chat.needLogin && auth.isLoggedIn()) {
			this.chat.needLogin = false
			this.bootstrap()
		}
	},

	methods: {
		goBack () {
			uni.navigateBack()
		},

		goLogin () {
			uni.navigateTo({ url: '/pages/login/login' })
		},

		goVip () {
			uni.navigateTo({ url: '/pages/vip/vip' })
		},

		/** 没有宠物档案时的真去处（不是一句"去建一只"就算了） */
		goAddPet () {
			uni.navigateTo({ url: '/pages/pet-edit/pet-edit' })
		},

		/**
		 * 顺序刻意如此：**先定 pid，再读历史**。
		 * chat / greet 不传 pid 是「第一只宠物」，message 不传 pid 是「最近聊过的那只」——
		 * 口径不同（AiPetController.java:82-85），所以本页一律显式带 pid，先读 `GET /pet` 拿到"第一只"。
		 */
		async bootstrap () {
			this.reloadQuota()
			await this.resolvePet()
			this.loadHistory()
		},

		/** 读我的宠物：提供 pid / 名字，也是"有没有档案"的唯一判据（读不到就保持 null，不猜） */
		async resolvePet () {
			try {
				const pets = await listMyPets()
				const list = Array.isArray(pets) ? pets : []
				this.chat.hasProfile = list.length > 0
				if (!this.chat.pid && list.length && list[0] && list[0].pid) this.chat.pid = list[0].pid
				if (this.chat.pid) {
					for (let i = 0; i < list.length; i++) {
						const p = list[i]
						if (p && String(p.pid) === String(this.chat.pid) && p.pName) this.chat.petName = p.pName
					}
				}
			} catch (e) {
				if (isUnauthorized(e)) {
					this.chat.needLogin = true
					return
				}
				// 宠物档案服务抖动不该让"聊天"这一页打不开：名字退回「它」，其余照常
				this.chat.hasProfile = null
			}
		},

		async loadHistory () {
			this.chat.historyLoading = true
			this.chat.historyError = ''
			try {
				const rows = await listPetMessages(this.chat.pid, HISTORY_LIMIT)
				applyHistory(this.chat, rows, HISTORY_LIMIT)
				// 空态才让宠物先开口（**/greet 不扣次数**）；已经聊过就不打扰它了
				if (!rows || !rows.length) {
					if (this.chat.hasProfile !== false) this.greet()
				}
			} catch (e) {
				if (isUnauthorized(e)) {
					this.chat.needLogin = true
					return
				}
				applyHistoryError(this.chat, e)
			}
		},

		/** 宠物主动开口。失败只进静音提示（greeting=null 也是正常的），首页/本页都不会因此变错误页 */
		async greet () {
			try {
				const resp = await greetPet(this.chat.pid)
				applyGreet(this.chat, resp)
			} catch (e) {
				if (isUnauthorized(e)) {
					this.chat.needLogin = true
					return
				}
				applyGreetError(this.chat, e)
			}
		},

		async reloadQuota () {
			try {
				applyBenefit(this.chat, await getChatQuota())
			} catch (e) {
				applyQuotaError(this.chat, e)
			}
		},

		/**
		 * 发送：先乐观插一条，再等后端那一次完整回复（**非流式**）。
		 * 失败要重新拉一次历史来分辨"那句到底落库没有"——见 utils/aiPet.js 的 applyChatFailure 注释。
		 */
		async send () {
			const text = String(this.chat.draft || '').trim()
			if (!text || this.chat.sending || this.chat.needLogin) return
			const row = beginSend(this.chat, text)
			try {
				const resp = await chatWithPet(this.chat.pid, text)
				applyChatSuccess(this.chat, resp, row.id)
			} catch (e) {
				// 未登录/过期：再拉一次历史必然也是同一个错，别白打一次请求
				if (isUnauthorized(e)) {
					applyChatFailure(this.chat, e, text, null, HISTORY_LIMIT)
					return
				}
				let rows = null
				try {
					rows = await listPetMessages(this.chat.pid, HISTORY_LIMIT)
				} catch (e2) {
					if (isUnauthorized(e2)) {
						applyChatFailure(this.chat, e2, text, null, HISTORY_LIMIT)
						return
					}
					rows = null
				}
				applyChatFailure(this.chat, e, text, rows, HISTORY_LIMIT)
			}
		}
	}
}
</script>

<style lang="scss" scoped>
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

/* ---- 未登录软门槛 ---- */
.gate {
	padding: $mw-space-8 $mw-page-padding;
}
.gate__title {
	display: block;
	font-size: $mw-font-h2;
	font-weight: 600;
	color: $mw-ink;
}
.gate__desc {
	display: block;
	margin-top: $mw-space-2;
	font-size: $mw-font-caption;
	line-height: 1.7;
	color: $mw-ink-2;
}
.gate__btn {
	display: flex;
	align-items: center;
	justify-content: center;
	height: $mw-btn-h;
	margin-top: $mw-space-4;
	background: $mw-brand-ink;
	border-radius: $mw-radius-input;
}
.gate__btn text {
	font-size: $mw-font-body;
	font-weight: 600;
	color: #ffffff;
}

/* ---- 额度 ---- */
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
	padding: 0 $mw-space-1;
	font-size: $mw-font-micro;
	font-weight: 600;
	color: $mw-brand-ink;
}

/* ---- 消息流 ---- */
/*
	⚠️ `box-sizing: border-box` 是**真机宽度 bug 的修复**，不是随手加的（删掉就会重现"我的字被切掉一块"）：

	uni-app 框架基础样式给 `uni-scroll-view` 写了 `display:block; width:100%`
	（真机构建产物 `unpackage/dist/dev/app-plus/app.css` 实测）；而 `width` **不是 `auto`** 时，
	`align-items: stretch` 会退回 `flex-start`（MDN `align-items` 的 stretch 条：
	"If the item is not auto-sized, this value falls back to flex-start"）→ 这个 padding
	**不参与收缩**，落在 `100%` 之外。默认 `box-sizing` 又是 content-box（框架没有全局 border-box）：

	  内容盒 = 100% 屏宽 = 750rpx；外沿 = 750 + 32×2 = **814rpx** → 越出屏幕右边界 64rpx
	  整列右移 32rpx（内容盒是 [32rpx, 782rpx]），右侧 32rpx 被 `body{overflow-x:hidden}` 吃掉

	后果：右对齐的「我」气泡贴在**越界**的那条边界上 → 气泡右侧 32rpx 在屏幕外，
	每行文字再被切掉 8rpx（375px 屏 = 4px，约一个汉字的 1/4）→ 用户报的「用户这边的内容被挤掉一块儿」。
	左对齐的宠物气泡起点在可见区，文字不会被切（只有圆角在屏幕外），所以**症状只出在用户这一侧**。

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
.state__err {
	display: block;
	font-size: $mw-font-caption;
	line-height: 1.7;
	color: $mw-danger;
}
.state__btn {
	display: flex;
	align-items: center;
	justify-content: center;
	min-height: $mw-touch-min;
	margin-top: $mw-space-2;
}
.state__btn text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-brand-ink;
}

.notice {
	margin-top: $mw-space-3;
	padding: $mw-space-3;
	background: $mw-surface;
	border: 1rpx solid $mw-line;
	border-radius: $mw-radius-card;
}
.notice__text {
	display: block;
	font-size: $mw-font-caption;
	line-height: 1.7;
	color: $mw-ink;
}
.notice__btn {
	display: flex;
	align-items: center;
	justify-content: center;
	height: $mw-touch-min;
	margin-top: $mw-space-2;
	background: $mw-brand-ink;
	border-radius: $mw-radius-input;
}
.notice__btn text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: #ffffff;
}

.intro {
	padding: $mw-space-8 0 0;
}
.intro__title {
	display: block;
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-ink;
}
.intro__desc {
	display: block;
	margin-top: $mw-space-1;
	font-size: $mw-font-micro;
	line-height: 1.7;
	color: $mw-muted;
}

/*
	消息行 = **一条消息一个 flex 列**（宠物侧与用户侧同构，只有对齐方向相反）。

	🔴 2026-09-21 用户真机反馈「宠物的时间显示会顶到用户这边来」的**根因**（逐层复算见门禁 J 段）：
	   改前只有 `.msg--me` 是 flex 列，宠物侧的 `.msg` 是**普通块盒**；而 `.bubble` 是
	   `display: inline-block`、`.msg__time` 是 `<text>`（框架 `uni-text` **没有任何 display 声明**
	   → 自定义元素默认 `display: inline`，读真机构建产物 `app.css` 实测）。
	   于是宠物侧的「气泡 + 时间」落在**同一个行内格式化上下文**里：时间的左边界 = 气泡右边界，
	   气泡越长它越往右 → 顶到用户那一侧；而气泡 + 时间一旦超过列宽，它又折到下一行的**左端**
	   → 位置随文案长度与字体宽度来回飘（同一页两种表现）。
	   另外 `margin-top` 对行内非替换元素**不生效** → 改前宠物侧的时间连 6rpx 的间距都没有。

	   改后：时间是**自己的 flex 项**（块化 → 独占一行），`margin-top` 生效，
	   再由下面的 `align-items` 把它钉在**自己那条消息**的一侧。
*/
.msg {
	display: flex;
	flex-direction: column;
	align-items: flex-start;
	margin-top: $mw-space-3;
}
/* 用户侧：气泡与时间都贴右边（与宠物侧对称） */
.msg--me {
	display: flex;
	flex-direction: column;
	align-items: flex-end;
}
.msg__who {
	display: flex;
	align-items: center;
	margin-bottom: 6rpx;
}
.msg__name {
	font-size: $mw-font-micro;
	color: $mw-ink-2;
}
/* 「它先开口」标记：把 kind=1 的自动问候和普通回答分开，不让用户以为自己在自言自语 */
.msg__tag {
	margin-left: $mw-space-1;
	padding: 0 8rpx;
	background: rgba(194, 65, 12, 0.12);
	border-radius: $mw-radius-tag;
	font-size: $mw-font-micro;
	color: $mw-brand-ink;
}
/*
	气泡宽度**相对列宽**（不写死 rpx），三条各自有理由：

	1) `box-sizing: border-box`：`max-width` 量到**气泡外沿**，占比可以直接复算（= 84%）。
	   这 81rpx 的"暗账"分两种情形看：
	     · 旧布局（列内容盒 750rpx）：外沿 = 92%×750 + 48(padding) + 2(border) = **740rpx**（气泡右边界 772rpx，越屏 22rpx）
	     · 只修列宽、不动气泡（列内容盒 686rpx）：外沿 = 92%×686 + 48 + 2 = **681rpx** —— 离列宽 686rpx 只剩 5rpx，
	       中文字体稍宽就溢出被裁；而且占比数字（92%）与实际观感（外沿对列宽 = 99.3%）对不上，所以必须一起修。
	2) `max-width: 84%`（列宽 686rpx → 外沿 576rpx）：与对侧留 ≥16%（≥110rpx ≈ 55px@375），
	   长消息也能一眼看出是谁说的；文字列 = 576 − 48 = 528rpx ≈ 264px@375 ≈ 17.6 汉字/行，够读；
	   同时给不同机型的中文字体宽度差异留了安全余量（顶到 100% 时任何字体差异都会溢出被裁）。
	3) `overflow-wrap: anywhere` + `word-break: break-word`：URL / 长英文单词这类**无空格长串**
	   必须在气泡内折行（前者连 min-content 都按单字符算，后者兜更老的内核）；
	   两个都是可继承属性，`.bubble__pending` 也跟着生效。**不许**用横向滚动兜底。
	`min-width: 0` 是防御性的：列方向（`.msg--me` 是 flex column）`min-width:auto` 本来就是 0，
	写出来是为了将来有人把 `.msg` 改成 row 时，气泡不会被内容撑破。
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
.bubble--pet {
	background: $mw-surface;
	border: 1rpx solid $mw-line;
}
.bubble--me {
	background: $mw-brand-ink;
}
.bubble__text {
	font-size: $mw-font-body;
	line-height: 1.75;
	color: $mw-ink;
	word-break: break-word;
}
/* 用户自己的气泡里文字要反色（本工程没有"浅底反色文字"令牌，沿用 pages/ai/ai.vue 的 #ffffff 写法） */
.msg--me .bubble__text {
	color: #ffffff;
}
.bubble__pending {
	font-size: $mw-font-body;
	line-height: 1.75;
	color: $mw-muted;
}
/*
	时间：**跟在自己那条消息下面、只在自己那一侧**。
	- `display: block`：让它在任何容器里都独占一行（防御：以后有人把 `.msg` 的 flex 去掉，
	  它也不会退回行内、跟气泡挤在同一行 —— 那正是本次真机 bug 的形态）。
	- `max-width: 100%`：宽度**相对容器**（列宽），不写死 rpx；再长的日期文案也只会折行不会越界。
	- `margin-top` 现在真的生效（行内元素上它本来是被忽略的）。
*/
.msg__time {
	display: block;
	max-width: 100%;
	margin-top: 6rpx;
	font-size: $mw-font-micro;
	color: $mw-muted;
}

/* ---- 发送失败 ---- */
.err {
	display: flex;
	align-items: center;
	padding: $mw-space-2 $mw-space-3;
	background: rgba(229, 72, 77, 0.08);
	border-top: 1rpx solid $mw-line;
}
.err__text {
	flex: 1;
	min-width: 0;
	font-size: $mw-font-micro;
	line-height: 1.7;
	color: $mw-danger;
}
.err__link {
	margin-left: $mw-space-2;
	padding: 0 $mw-space-1;
	font-size: $mw-font-micro;
	font-weight: 600;
	color: $mw-brand-ink;
}

/* ---- 输入区 ---- */
.composer {
	display: flex;
	align-items: flex-end;
	padding: $mw-space-2 $mw-space-3;
	background: $mw-surface;
	border-top: 1rpx solid $mw-line;
}
/*
	`min-width: 0`：框架基础样式给 `uni-textarea` 写了 `width:300px; height:150px`
	（`app.css` 实测；高度被 `auto-height` 的 `fit-content!important` 收掉，宽度这条仍在）。
	`flex: 1` 的自动最小尺寸（`min-width: auto`）会参考"指定尺寸建议"，
	显式 `min-width: 0` 把收缩权交回 flex —— 否则输入框不收缩时会把「发送」按钮顶出屏幕右边。
	（逐条排除记录：算下来当前内核的 min-content 很小，这条**不是**本次 bug 的成因，属加固。）
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

.note {
	display: block;
	padding: $mw-space-1 $mw-space-3 0;
	background: $mw-surface;
	font-size: $mw-font-micro;
	line-height: 1.7;
	color: $mw-muted;
}
.note--warn {
	color: $mw-danger;
}
.note--foot {
	padding-bottom: calc(#{$mw-space-2} + env(safe-area-inset-bottom));
}
</style>