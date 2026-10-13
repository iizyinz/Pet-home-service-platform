import { get, type PageResult } from '@/utils/request'
import type { Banner, Announcement } from '@/types'

export const fetchBanners = () => get<Banner[]>('/banners')

export const fetchAnnouncements = (page = 1, size = 10) =>
  get<PageResult<Announcement>>('/announcements', { page, size })

export const fetchAnnouncement = (id: number) => get<Announcement>(`/announcements/${id}`)
