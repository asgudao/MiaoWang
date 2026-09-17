/**
 * 鉴权相关错误 → 人话（与 Web 端 utils/auth-error.ts 保持一致）
 *
 * 后端的 message 大多已经是中文，能直接用就直接用；这里只补前端能给出更具体下一步的几类。
 */
import { ApiError } from './request.js'

const FRIENDLY = {
	A000701: '短信验证码不正确或已过期，重新获取一条试试',
	A000703: '发得太频繁了，同一个号码 60 秒只能发 1 条',
	A000704: '这个号码今天的验证码已经发满 10 条，明天再试',
	A000705: '手机号格式不对',
	// 实测：用户名重复返回 B000201「用户名已存在」（不是 B000205）
	B000201: '这个用户名已经有人用了，换一个',
	B000205: '这个手机号已经注册过了，直接用手机号登录吧',
	NETWORK: '连不上后端。真机常见原因是 utils/request.js 里的 BASE 不是电脑的局域网 IP（或防火墙没放行 7000）；H5 则要确认业务服务已注册到 Nacos'
}

/** 图形验证码类错误：调用方要顺手换一张新图（它是一次性的） */
export function isCaptchaError (e) {
	return e instanceof ApiError && (e.code === 'A000700' || e.code === 'A000702')
}

export function authErrorMessage (e) {
	if (e instanceof ApiError) {
		if (isCaptchaError(e)) return e.message || '图形验证码不正确或已过期'
		// NETWORK 的 message 里带着"请求打到哪"，比这里的笼统文案有用得多 → 直接透传
		if (e.code === 'NETWORK') return e.message || FRIENDLY.NETWORK
		return FRIENDLY[e.code] || e.message || '请求失败，请稍后再试'
	}
	return (e && e.message) || '未知错误'
}