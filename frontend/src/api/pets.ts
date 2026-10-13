import { get, post, put, del, type PageResult } from '@/utils/request'
import type { Pet, UserAddress } from '@/types'

/* ============ 宠物档案（本人归属） ============ */
export const fetchPets = () => get<Pet[]>('/pets')

export const fetchPet = (id: number) => get<Pet>(`/pets/${id}`)

export interface PetPayload {
  name: string
  breed?: string
  age?: number
  weightKg?: number
  healthInfo?: string
  specialNeed?: string
  avatar?: string
}

export const createPet = (data: PetPayload) => post<Pet>('/pets', data)
export const updatePet = (id: number, data: PetPayload) => put<void>(`/pets/${id}`, data)
export const deletePet = (id: number) => del<void>(`/pets/${id}`)

/* ============ 管理端宠物全量 ============ */
export const adminFetchPets = (q: { userId?: number; page?: number; size?: number }) =>
  get<PageResult<Pet>>('/admin/pets', {
    userId: q.userId || undefined,
    page: q.page ?? 1,
    size: q.size ?? 10,
  })

export const adminDeletePet = (id: number) => post<void>(`/admin/pets/${id}/delete`)

/* ============ 上门地址（本人归属） ============ */
export const fetchAddresses = () => get<UserAddress[]>('/addresses')

export interface AddressPayload {
  contactName: string
  contactPhone: string
  region?: string
  detail: string
  isDefault?: boolean
}

export const createAddress = (data: AddressPayload) => post<UserAddress>('/addresses', data)
export const updateAddress = (id: number, data: AddressPayload) => put<void>(`/addresses/${id}`, data)
export const deleteAddress = (id: number) => del<void>(`/addresses/${id}`)
