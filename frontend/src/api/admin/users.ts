import { get, put, post, type PageResult } from '@/utils/request'
import type { SysUser } from '@/types'

/** 后台用户分页 */
export const fetchUsers = (q: {
  keyword?: string
  role?: string
  status?: number | ''
  page?: number
  size?: number
}) =>
  get<PageResult<SysUser>>('/admin/users', {
    keyword: q.keyword || undefined,
    role: q.role || undefined,
    status: q.status === '' || q.status === undefined ? undefined : q.status,
    page: q.page ?? 1,
    size: q.size ?? 10,
  })

/** 冻结 / 解冻（status 1 正常 0 冻结），不允许冻结 ADMIN */
export const updateUserStatus = (id: number, status: number) =>
  put<void>(`/admin/users/${id}/status`, undefined, { params: { status } })

/** 余额调整（正加负扣），写 ADJUST 流水 + 审计日志 */
export const adjustBalance = (data: { userId: number; amount: number; remark?: string }) =>
  post<void>('/admin/users/balance/adjust', data)
