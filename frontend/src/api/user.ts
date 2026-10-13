import { get, post, put, type PageResult } from '@/utils/request'
import type { SysUser, BalanceRecord } from '@/types'

/** 当前用户信息 */
export const fetchProfile = () => get<SysUser>('/user/profile')

export interface ProfilePayload {
  nickname?: string
  avatar?: string
  phone?: string
  oldPassword?: string
  newPassword?: string
}

export const updateProfile = (data: ProfilePayload) => put<void>('/user/profile', data)

/** 当前余额（元） */
export const fetchBalance = () => get<{ balance: number }>('/user/balance')

/** 余额流水分页（消费/充值/退款，含变动后余额快照） */
export const fetchBalanceRecords = (page = 1, size = 10) =>
  get<PageResult<BalanceRecord>>('/user/balance/records', { page, size })

/** 充值（模拟支付） */
export const recharge = (amount: number) => post<{ balance: number }>('/user/balance/recharge', { amount })

/** 头像上传后直接更新个人信息 */
export const saveAvatar = (avatar: string) => updateProfile({ avatar })
