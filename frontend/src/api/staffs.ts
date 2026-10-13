import { get, post, put, type PageResult } from '@/utils/request'
import type { StaffListVO, StaffDetailVO, StaffProfile, Review } from '@/types'

export const fetchStaffs = (q: { areaId?: number; breed?: string; keyword?: string; page?: number; size?: number }) =>
  get<PageResult<StaffListVO>>('/staffs', {
    areaId: q.areaId || undefined,
    breed: q.breed || undefined,
    keyword: q.keyword || undefined,
    page: q.page ?? 1,
    size: q.size ?? 10,
  })

export const fetchStaff = (id: number) => get<StaffDetailVO>(`/staffs/${id}`)

export const fetchStaffReviews = (id: number, page = 1, size = 10) =>
  get<PageResult<Review>>(`/staffs/${id}/reviews`, { page, size })

/** 收藏/取消收藏（幂等切换），返回最新收藏态 */
export const toggleFavorite = (id: number) => post<{ favorited: boolean }>(`/staffs/${id}/favorite`)

export const fetchFavorites = () => get<StaffListVO[]>('/staffs/favorites')

/** 服务人员本人资料（仅 STAFF） */
export const fetchMyProfile = () => get<StaffProfile>('/staffs/me/profile')

export interface StaffProfilePayload {
  intro?: string
  experienceYears?: number
  goodAtBreeds?: string[]
  areaId?: number
  certImages?: string[]
  itemIds?: number[]
}

export const updateMyProfile = (data: StaffProfilePayload) => put<void>('/staffs/me/profile', data)
