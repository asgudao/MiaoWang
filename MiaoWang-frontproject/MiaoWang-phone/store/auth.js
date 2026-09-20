/**
 * 登录态（手机端）
 *
 * 本工程不引 Pinia（HBuilderX + 无 npm），所以用 reactive 单例 + 方法。
 * 因为暴露的是普通对象而不是 ref，模板里要**当函数调用**：`auth.isLoggedIn()`。
 *
 * 会话同时进内存（响应式）与本地存储（uni storage，杀掉 App 也还在）。
 */
import { reactive } from 'vue'
import { ApiError, auth as storage } from '../utils/request.js'
import * as userApi from '../api/user.js'
import { pets } from './pets.js'
import { ai } from './ai.js'
import { vip } from './vip.js'

/**
 * 清掉"跟账号走"的 store。
 *
 * ⚠️ 这三个 store 都是**模块级 reactive 单例**（本工程没有 Pinia），
 * 模块只在 App 启动时求值一次 —— 页面卸载（onUnload）**不会**把它们清空，
 * 所以必须由登录/退出来显式 reset，否则下一个账号会直接看到上一个人的数据。
 *
 * 判据在 `pets.state.loaded`：`fetchMine()` 见到"已拉过一次"就直接 return，连请求都不发，
 * 于是列表页会**一直渲染上一个账号的数组**（2026-09-20 用脚手架实测复现，见报告）。
 */
function resetAccountStores () {
	pets.reset()
	ai.reset()
	vip.reset()
}

const state = reactive({
	username: storage.getUsername(),
	token: storage.getToken(),
	info: null,
	loading: false,
	/** 后端业务服务没注册到 Nacos 时会一直失败，用这个标记让界面能诚实提示 */
	backendUnavailable: false
})

function noteError (e) {
	if (e instanceof ApiError && (e.code === 'NETWORK' || e.code === '404')) {
		state.backendUnavailable = true
	}
}

function setSession (username, token) {
	// 换账号的入口（"先退出再登录"和"没退出直接换号"都会经过这里）：
	// 先把上一个账号的数据清掉，再写新会话，中间不留"新 token + 旧列表"的窗口
	resetAccountStores()
	state.username = username
	state.token = token
	storage.save(username, token)
}

function clearSession () {
	state.username = ''
	state.token = ''
	state.info = null
	storage.clear()
	// 退出登录同样要清：只清 auth 会让宠物/AI/会员三个单例把上一个账号的数据留在内存里
	resetAccountStores()
}

async function fetchMe () {
	if (!state.username || !state.token) return
	try {
		state.info = await userApi.getUserInfo(state.username)
	} catch (e) {
		noteError(e)
		if (e instanceof ApiError && e.isUnauthorized) clearSession()
	}
}

async function login (username, password) {
	state.loading = true
	try {
		const resp = await userApi.loginByPassword(username, password)
		setSession(userApi.resolveLoginUsername(resp, username), resp.token)
		await fetchMe()
	} finally {
		state.loading = false
	}
}

async function loginWithPhone (phone, smsCode) {
	state.loading = true
	try {
		const resp = await userApi.loginByPhone(phone, smsCode)
		setSession(userApi.resolveLoginUsername(resp), resp.token)
		await fetchMe()
	} finally {
		state.loading = false
	}
}

/** 注册成功后直接用刚填的账号密码登录，用户不用再输一遍 */
async function register (body) {
	state.loading = true
	try {
		await userApi.register(body)
	} finally {
		state.loading = false
	}
	await login(body.username, body.password)
}

/** 启动时恢复：先问后端这个 token 还有效吗（Redis 会话 1 小时） */
async function restore () {
	if (!state.username || !state.token) return
	try {
		const ok = await userApi.checkLogin(state.username, state.token)
		if (ok) await fetchMe()
		else clearSession()
	} catch (e) {
		// 只有"确定失效"才清会话；网络层失败只说明后端没起，不该把人踢出去
		if (e instanceof ApiError && e.isUnauthorized) clearSession()
		else noteError(e)
	}
}

async function logout () {
	try {
		await userApi.logout(state.username, state.token)
	} catch (e) {
		// 退出失败也要清本地，否则用户被困在"退不出去"的状态
	}
	clearSession()
}

export const auth = {
	state: state,
	isLoggedIn () {
		return Boolean(state.username && state.token)
	},
	displayName () {
		return (state.info && (state.info.realName || state.info.username)) || state.username || ''
	},
	initial () {
		return (this.displayName() || '我').slice(0, 1)
	},
	/** 首页问候语用「宠物怎么称呼你」 */
	ownerCallName () {
		return (state.info && state.info.ownerCallName) || '铲屎官'
	},
	maskedPhone () {
		const p = state.info && state.info.phone
		if (!p || p.length < 7) return p || ''
		return p.slice(0, 3) + '****' + p.slice(-4)
	},
	login: login,
	loginWithPhone: loginWithPhone,
	register: register,
	fetchMe: fetchMe,
	restore: restore,
	logout: logout,
	clearSession: clearSession
}