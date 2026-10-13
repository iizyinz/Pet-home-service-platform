import { get, post, type PageResult } from '@/utils/request'
import type { Notice } from '@/types'

export const fetchNotices = (page = 1, size = 10) =>
  get<PageResult<Notice>>('/notices', { page, size })

export const fetchUnreadCount = () => get<{ count: number }>('/notices/unread-count')

export const markNoticeRead = (id: number) => post<void>(`/notices/${id}/read`)

export const markAllRead = () => post<void>('/notices/read-all')
