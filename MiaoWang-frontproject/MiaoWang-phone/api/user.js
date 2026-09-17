/**
 * 用户与鉴权接口（手机端，与 Web 端 src/api/user.ts 一致）
 *
 * 反直觉点（见 miaowang-api-contract）：
 * - 登录返回的 `data` 是**对象** `{ token }`，不是裸字符串
 * - 注册（POST /user）的 `smsCode` **必填**，scene 必须是 `register`
 * - 图形验证码**一次性**，重发短信要重新取一张
 * - 这些接口里**只有验证码 / 发短信 / 登录 / 注册**走网关白名单，要带 `anonymous: true`；
 *   其余（check-login / has-username / logout）实测**必须带 username + token 头**，只放 query 会被
 *   网关拦成 `state:-1`（契约标的"免登录"不准，见 tools/probe-auth.ps1 实测）
 */
import { api, ApiError } from '../utils/request.js'

export function getCaptcha () {
	return api.get('user', '/user/captcha', { anonymous: true })
}

export function sendSms (body) {
	return api.post('user', '/user/sms', { body: body, anonymous: true })
}

export function loginByPassword (username, password) {
	return api.post('user', '/user/login', { body: { username: username, password: password }, anonymous: true })
}

/** 手机号没注册过会自动建号（用户名随机，如 mw_46451234） */
export function loginByPhone (phone, smsCode) {
	return api.post('user', '/user/login/phone', { body: { phone: phone, smsCode: smsCode }, anonymous: true })
}

export function register (body) {
	return api.post('user', '/user', { body: body, anonymous: true })
}

/** 用户名是否可用
 *  ⚠️ **需要登录**（网关没白名单它，实测匿名返回 state:-1），所以注册页不能拿它做实时查重
 */
export function hasUsername (username) {
	return api.get('user', '/user/has-username', { query: { username: username } })
}

export function getUserInfo (username) {
	return api.get('user', '/user/' + encodeURIComponent(username))
}

/** 校验登录态是否仍有效（Redis 会话 1 小时）
 *  ️ 必须带鉴权头；有效返回 true，已登出返回 false（干净的假值，不是报错）
 */
export function checkLogin (username, token) {
	return api.get('user', '/user/check-login', { query: { username: username, token: token } })
}

export function logout (username, token) {
	return api.del('user', '/user/logout', { query: { username: username, token: token } })
}

/* ------------------------------------------------------------------ *
 * 从 JWT 里尽力取出 username
 *
 * 为什么需要：**手机号登录只返回 `{ token }`**，而网关还没把 username 注入下游请求头（挂账 #8），
 * 前端必须自己凑齐 `username` + `token` 两个头，否则下游服务按错误用户名查库（实测报 B000200）。
 *
 * 实测真 token 载荷（tools/probe-auth.ps1）：
 *   {"claims":{"uid":7,"username":"probe114904"},"sub":"user","iat":…,"exp":…}
 * - 用户名在 `claims.username`（契约里写的 `clainms` 拼写已过时）
 * - **`sub` 是常量 `"user"`，不是用户名** —— 绝不能拿它兜底，否则会静默发出假用户名
 *   （网关只校验 token 会放行，问题被推到下游）
 * ------------------------------------------------------------------ */

function b64urlDecode (input) {
	try {
		const s = input.replace(/-/g, '+').replace(/_/g, '/')
		const pad = s.length % 4 ? new Array(5 - (s.length % 4)).join('=') : ''
		const bin = atob(s + pad)
		// atob 给的是 latin1，要按 UTF-8 还原（用户名可能含中文）
		const bytes = new Uint8Array(bin.length)
		for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i)
		return new TextDecoder().decode(bytes)
	} catch (e) {
		return ''
	}
}

/** 在（可能嵌套、可能二次编码的）结构里找第一个非空的候选键 */
function findKey (node, keys, depth) {
	if ((depth || 0) > 4 || node === null || node === undefined) return undefined

	if (typeof node === 'string') {
		const t = node.trim()
		if (t.charAt(0) === '{' || t.charAt(0) === '[') {
			try {
				return findKey(JSON.parse(t), keys, (depth || 0) + 1)
			} catch (e) {
				return undefined
			}
		}
		// 形如一段 base64url 的 JSON（二次编码）
		if (/^[A-Za-z0-9_-]{20,}$/.test(t)) {
			const decoded = b64urlDecode(t)
			if (decoded) {
				try {
					return findKey(JSON.parse(decoded), keys, (depth || 0) + 1)
				} catch (e) {
					return undefined
				}
			}
		}
		return undefined
	}

	if (typeof node !== 'object') return undefined
	for (let i = 0; i < keys.length; i++) {
		const v = node[keys[i]]
		if (typeof v === 'string' && v) return v
	}
	const values = Object.keys(node).map(function (k) { return node[k] })
	for (let i = 0; i < values.length; i++) {
		const found = findKey(values[i], keys, (depth || 0) + 1)
		if (found) return found
	}
	return undefined
}

/** 解出 token 里的 username；解不出返回空串。实测 `sub` 是常量 `"user"`，所以只认显式的 username 字段 */
export function usernameFromToken (token) {
	if (!token) return ''
	const parts = String(token).split('.')
	const payloadPart = parts[1]
	if (!payloadPart) return ''
	const payload = b64urlDecode(payloadPart)
	if (!payload) return ''
	let parsed
	try {
		parsed = JSON.parse(payload)
	} catch (e) {
		return ''
	}
	return findKey(parsed, ['username', 'userName', 'user_name', 'nickName'], 0) || ''
}

/** 登录成功后确定 username；确实拿不到就明确报错，绝不静默 */
export function resolveLoginUsername (resp, fallback) {
	const u = (resp && resp.username) || usernameFromToken(resp && resp.token) || fallback || ''
	if (!u) {
		throw new ApiError(
			'NO_USERNAME',
			'登录成功但没能确定用户名：手机号登录只返回 token。请改用用户名密码登录，或让后端在登录响应里带上 username。',
			resp
		)
	}
	return u
}