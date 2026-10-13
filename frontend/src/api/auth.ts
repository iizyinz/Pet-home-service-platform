import { get, post } from '@/utils/request'
import type { Captcha, RegisterParams } from '@/types'

/** 图形验证码（5 分钟有效） */
export const fetchCaptcha = () => get<Captcha>('/auth/captcha')

/** 注册（USER / STAFF），STAFF 会自动创建待审核服务人员档案 */
export const register = (data: RegisterParams) => post<void>('/auth/register', data)
