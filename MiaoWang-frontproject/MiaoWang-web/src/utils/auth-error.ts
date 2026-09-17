/**
 * 鉴权相关错误 → 人话
 *
 * 后端的 message 大多已经是中文，所以**能用就直接用**；这里只补三类信息：
 * 1) 前端能给出更具体下一步的（验证码、限流）
 * 2) 后端 message 太笼统的
 * 3) 网络层失败（业务服务没注册到 Nacos 时就是这个表现）
 */
import { ApiError } from '@/api/request'

/** 图形验证码类错误：调用方要顺手换一张新图（它是一次性的） */
export function isCaptchaError(e: unknown): boolean {
  return e instanceof ApiError && (e.code === 'A000700' || e.code === 'A000702')
}

export function authErrorMessage(e: unknown): string {
  if (e instanceof ApiError) {
    if (isCaptchaError(e)) return e.message || '图形验证码不正确或已过期'
    if (e.code === 'A000701') return '短信验证码不正确或已过期，重新获取一条试试'
    if (e.code === 'A000703') return '发得太频繁了，同一个号码 60 秒只能发 1 条'
    if (e.code === 'A000704') return '这个号码今天的验证码已经发满 10 条，明天再试'
    if (e.code === 'A000705') return '手机号格式不对'
    // 实测：用户名重复返回 B000201「用户名已存在」（不是 B000205）
    if (e.code === 'B000201') return '这个用户名已经有人用了，换一个'
    if (e.code === 'B000205') return '这个手机号已经注册过了，直接用手机号登录吧'
    // NETWORK 的 message 里带着实际请求地址，比笼统文案有用 → 直接透传
    if (e.code === 'NETWORK') return e.message || '连不上后端，请检查后端与代理是否已启动'
    return e.message || '请求失败，请稍后再试'
  }
  return (e as Error)?.message || '未知错误'
}
