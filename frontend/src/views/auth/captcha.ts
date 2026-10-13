import { fetchCaptcha } from '@/api/auth'
import type { Captcha } from '@/types'

/** 拉取图形验证码（5 分钟有效），供登录/注册页复用 */
export function refreshCaptcha(): Promise<Captcha> {
  return fetchCaptcha()
}
