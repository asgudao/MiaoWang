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
import { api, ApiError, auth, authHeaders, buildUrl } from '../utils/request.js'

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

/* ------------------------------------------------------------------ *
 * 当前登录用户的资料：读 / 写 / 换头像
 *
 * 归属一律由后端从请求头 username+token 还原（UserContext），前端不传"改谁"。
 * 实测与源码依据见 .agents/skills/miaowang-api-contract 的 user 小节，以及
 * MiaoWang-backproject/MiaoWang-user/src/main/java/com/MapleLeaf/MiaoWang/controller/UserController.java
 * ------------------------------------------------------------------ */

/**
 * 读自己的资料（手机号已由后端脱敏）。
 *
 * 实测返回（fixture tools/userpage-fixtures/user-profile-probe114904.json）：
 *   `{ id: null, username, realName, phone: '137****0402', mail, ownerCallName, avatar: null }`
 * ⚠️ `id` **恒为 null**（`uid` 没有映射进 `UserRespDTO`）→ **uid 不能从这里取**。
 * ⚠️ `avatar` 在库里 13 行全是 null（快照见 tools/userpage-fixtures/_db-recon.txt），
 *    所以界面必须有"没有头像"的兜底，不能摆一个空 image。
 */
export function getMyProfile (username) {
	return api.get('user', '/user/' + encodeURIComponent(username))
}

/**
 * 改自己的资料。请求体只认四个字段：`{ username, realName, mail, ownerCallName }`
 * （`UserUpdateReqDTO`，源码 UserController.java:68 + UserUpdateReqDTO.java:14）。
 *
 * ⚠️ body 里的 `username` 必须与请求头还原出的登录身份**一致**，否则报
 *    `B000206 只能修改自己的资料`；未登录报 `B000204`
 *    （源码 UserServiceImpl.java:163，文案取自 common 的 UserErrorCodeEnum.java:12/14）。
 * ⚠️ `password` / `phone` **已从本接口移除**：它们各走 `PUT /user/password`（要旧密码）与
 *    `PUT /user/phone`（要新号短信验证码）。所以本接口**改不了密码、也改不了手机号**。
 * ⚠️ 未传的字段不会被更新（MyBatis-Plus 的 update 只写非 null 字段）；
 *    传空串是"清空"，不是"不改"。
 */
export function updateMyProfile (body) {
	return api.put('user', '/user', { body: body })
}

/**
 * 换头像：`multipart/form-data`，字段名 **`file`**（源码 UserController.java:126）。
 * 后端限制：扩展名只允许 `jpg/jpeg/png/webp`、单张 ≤ 5MB
 * （`MinioProperties.java:93` 与 `FileErrorCodeEnum` 的 A000600/A000601/A000602）。
 * 返回 `data` = 头像 URL（绝对地址，MinIO 直链）。
 *
 * ⚠️ 下发的 URL 由后端**按本次请求的 Host 推导**（`ObjectStorageUrlBuilder`），
 *    网关已配 `PreserveHostHeader`（application.yml:27）→ 真机联调时后端地址必须是手机能到的地址，
 *    否则存下来的 URL 在手机上是裂图（与社区图片同一条链）。
 * ⚠️ `uni.request` 发不了 multipart，只能走 `uni.uploadFile`（同 api/health.js:292 的写法）。
 * ⚠️ 本端**从未真调过**这个接口（只读红线），调用方按"失败可重试"处理。
 *
 * @param {string} filePath `uni.chooseImage` 给的本地临时路径
 * @returns {Promise<string>} 头像 URL
 */
export function uploadMyAvatar (filePath) {
	return new Promise(function (resolve, reject) {
		uni.uploadFile({
			// 复用 request.js 的 buildUrl：H5 走 devServer 代理、App 直连 BASE，与普通请求同一套地址规则
			url: buildUrl('user', '/user/avatar'),
			filePath: filePath,
			name: 'file',
			header: authHeaders(),
			success: function (res) {
				let env = res.data
				try {
					env = JSON.parse(res.data)
				} catch (e) {
					reject(new ApiError('UPLOAD_SHAPE', '上传返回的不是 JSON：' + String(res.data).slice(0, 120)))
					return
				}
				if (res.statusCode === 401 || res.statusCode === 403) {
					auth.clear()
					reject(new ApiError('UNAUTHORIZED', '登录已过期，请重新登录'))
					return
				}
				// 网关信封（state）
				if (env && env.state !== undefined) {
					if (env.state === 0) {
						resolve(env.data)
						return
					}
					reject(new ApiError(env.state === -1 ? 'UNAUTHORIZED' : env.state, env.message, env))
					return
				}
				// 业务信封（code）：成功才取 data；空串 URL 也算失败，别把空串写进界面
				if (!env || env.code !== '0' || !env.data) {
					reject(new ApiError((env && env.code) || 'UPLOAD_FAIL', (env && env.message) || '头像上传失败', env))
					return
				}
				resolve(env.data)
			},
			fail: function () {
				reject(new ApiError('NETWORK', '头像没传上去（连不上后端，或选中的文件读不到）'))
			}
		})
	})
}

/**
 * 资料接口的错误文案。
 *
 * 后端这些 message 本身就是给用户看的中文（如「只能修改自己的资料」），所以优先透传；
 * 只对"前端能给出更具体下一步"的几个码做加工。
 * ⚠️ `B000206`（只能改自己）**刻意不加工**：后端那句已经很清楚了，再包一层只会把原文盖掉，
 *    门禁里专门断言页面显示的就是后端原文（而不是前端自己编的一句）。
 * ⚠️ `A000001` 同时用于"参数错误"与 pet/healthy 的"未登录"，不能整体当未登录
 *    （判定统一走 `ApiError.isUnauthorized`，见 utils/request.js:136）。
 */
export function profileErrorMessage (e) {
	if (e instanceof ApiError) {
		if (e.isUnauthorized) return '登录已过期，请重新登录'
		if (e.code === 'NETWORK') return e.message
		if (e.code === 'B000200') return '用户记录不存在，重新登录一次试试'
		if (e.code === 'A000601') return '头像格式不支持，只支持 jpg / jpeg / png / webp'
		if (e.code === 'A000602') return '头像太大了，单张不能超过 5MB'
		if (e.code === 'A000600') return '没有选到文件'
		if (e.code === 'B000600') return '头像存储桶没配置（后端问题，不是你操作的问题）'
		if (e.code === 'C000100') return '头像存进对象存储失败，稍后再试'
		return e.message || '操作失败，请稍后再试'
	}
	return (e && e.message) || '操作失败，请稍后再试'
}