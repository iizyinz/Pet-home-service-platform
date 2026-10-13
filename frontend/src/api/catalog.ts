import { get, type PageResult } from '@/utils/request'
import type { ServiceType, ServiceArea, ServiceItem } from '@/types'

export const fetchServiceTypes = () => get<ServiceType[]>('/service-types')

export const fetchServiceAreas = () => get<ServiceArea[]>('/service-areas')

export const fetchServiceItems = (q: { typeId?: number; keyword?: string; page?: number; size?: number }) =>
  get<PageResult<ServiceItem>>('/service-items', {
    typeId: q.typeId || undefined,
    keyword: q.keyword || undefined,
    page: q.page ?? 1,
    size: q.size ?? 10,
  })

export const fetchServiceItem = (id: number) => get<ServiceItem>(`/service-items/${id}`)
