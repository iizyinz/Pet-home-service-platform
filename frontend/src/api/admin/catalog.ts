import { get, post, put, del, type PageResult } from '@/utils/request'
import type { ServiceType, ServiceArea, ServiceItem, AdminStaffRow } from '@/types'

/**
 * 管理端服务目录（类型 / 项目 / 区域 / 服务人员）查询与写操作封装。
 *
 * 后端契约（均位于 /api/admin，仅 ADMIN 可访问，写操作记审计日志）：
 * - AbstractCrudController：GET 分页（keyword/page/size）、POST 新增、PUT /{id} 修改、DELETE /{id} 删除
 * - AdminStaffController：GET 分页（certStatus/keyword）、GET /{userId} 详情、
 *   PUT /{userId}/cert 资质审核、PUT /{userId}/status?status= 上下架
 *
 * 分页统一 { list, total, page, size }，page 从 1 开始。
 */

/** 管理端通用列表入参（后端仅支持 keyword 关键词检索） */
export interface AdminCatalogQuery {
  keyword?: string
  page?: number
  size?: number
}

/** 管理端行数据：基础类型之外后端还返回创建/更新时间 */
export interface AdminServiceTypeRow extends ServiceType {
  createdAt?: string
  updatedAt?: string
}

export interface AdminServiceItemRow extends ServiceItem {
  createdAt?: string
  updatedAt?: string
}

export interface AdminServiceAreaRow extends ServiceArea {
  createdAt?: string
  updatedAt?: string
}

/* ============ 服务类型：/api/admin/service-types ============ */

export interface ServiceTypePayload {
  name: string
  /** 首页分类图标（emoji 或文本） */
  icon?: string
  sort?: number
  /** 1 启用 0 停用 */
  status?: number
}

export const fetchAdminServiceTypes = (q: AdminCatalogQuery = {}) =>
  get<PageResult<AdminServiceTypeRow>>('/admin/service-types', {
    keyword: q.keyword || undefined,
    page: q.page ?? 1,
    size: q.size ?? 10,
  })

export const createServiceType = (data: ServiceTypePayload) => post<AdminServiceTypeRow>('/admin/service-types', data)

export const updateServiceType = (id: number, data: ServiceTypePayload) =>
  put<void>(`/admin/service-types/${id}`, data)

/** 删除；类型下存在服务项目时后端拒绝并返回业务错误 */
export const deleteServiceType = (id: number) => del<void>(`/admin/service-types/${id}`)

/* ============ 服务项目：/api/admin/service-items ============ */

export interface ServiceItemPayload {
  typeId: number
  name: string
  /** 单价（元/次） */
  price: number
  /** 服务时长（分钟） */
  durationMinutes: number
  coverUrl?: string
  /** 后端为 JSON 数组字符串，如 ["布偶猫"]，清空时传 "[]" */
  applicableBreeds?: string
  notice?: string
  /** 1 上架 0 下架 */
  status?: number
}

export const fetchAdminServiceItems = (q: AdminCatalogQuery = {}) =>
  get<PageResult<AdminServiceItemRow>>('/admin/service-items', {
    keyword: q.keyword || undefined,
    page: q.page ?? 1,
    size: q.size ?? 10,
  })

export const createServiceItem = (data: ServiceItemPayload) => post<AdminServiceItemRow>('/admin/service-items', data)

export const updateServiceItem = (id: number, data: ServiceItemPayload) =>
  put<void>(`/admin/service-items/${id}`, data)

/** 删除；订单存项目名/价格快照，删除不影响历史订单 */
export const deleteServiceItem = (id: number) => del<void>(`/admin/service-items/${id}`)

/* ============ 服务区域：/api/admin/service-areas ============ */

export interface ServiceAreaPayload {
  name: string
  /** 区域编码（唯一） */
  code: string
  /** 父级ID，0 为顶级 */
  parentId?: number
  /** 层级 1 市 2 区 */
  level?: number
  /** 1 启用 0 停用 */
  status?: number
}

export const fetchAdminServiceAreas = (q: AdminCatalogQuery = {}) =>
  get<PageResult<AdminServiceAreaRow>>('/admin/service-areas', {
    keyword: q.keyword || undefined,
    page: q.page ?? 1,
    size: q.size ?? 10,
  })

export const createServiceArea = (data: ServiceAreaPayload) => post<AdminServiceAreaRow>('/admin/service-areas', data)

export const updateServiceArea = (id: number, data: ServiceAreaPayload) =>
  put<void>(`/admin/service-areas/${id}`, data)

export const deleteServiceArea = (id: number) => del<void>(`/admin/service-areas/${id}`)

/* ============ 服务人员：/api/admin/staffs ============ */

export interface AdminStaffQuery {
  /** 昵称关键词 */
  keyword?: string
  /** 认证状态 0 待审 1 通过 2 驳回 */
  certStatus?: number | ''
  page?: number
  size?: number
}

export const fetchAdminStaffs = (q: AdminStaffQuery = {}) =>
  get<PageResult<AdminStaffRow>>('/admin/staffs', {
    keyword: q.keyword || undefined,
    certStatus: q.certStatus === '' || q.certStatus === undefined ? undefined : q.certStatus,
    page: q.page ?? 1,
    size: q.size ?? 10,
  })

/** 详情：返回原始 staff_profile 实体（goodAtBreeds / certImages 为 JSON 字符串） */
export interface AdminStaffDetail {
  userId: number
  intro?: string
  experienceYears?: number
  goodAtBreeds?: string
  areaId?: number
  certStatus?: number
  certImages?: string
  rejectReason?: string
  avgRating?: number
  completedCount?: number
  status?: number
  createdAt?: string
  updatedAt?: string
}

export const fetchAdminStaffDetail = (userId: number) => get<AdminStaffDetail>(`/admin/staffs/${userId}`)

export interface CertAuditPayload {
  /** 1 通过 2 驳回（驳回必须填原因） */
  certStatus: 1 | 2
  rejectReason?: string
}

/** 资质审核：通过后可接单，后端向服务人员推送站内通知 */
export const auditStaffCert = (userId: number, data: CertAuditPayload) =>
  put<void>(`/admin/staffs/${userId}/cert`, data)

/** 上/下架：1 正常 0 下架（下架后不在用户端列表展示） */
export const updateAdminStaffStatus = (userId: number, status: number) =>
  put<void>(`/admin/staffs/${userId}/status`, undefined, { params: { status } })

/* ============ 全量拉取（本地筛选/映射用） ============ */

/**
 * 管理端列表接口只支持 keyword 检索，类型/状态/层级等筛选在前端本地完成；
 * 下列方法按页循环拉取全量数据（每页 200，最多 20 页）。
 */
async function fetchAll<T>(
  request: (page: number, size: number) => Promise<PageResult<T>>,
  size = 200,
  maxPages = 20,
): Promise<T[]> {
  const first = await request(1, size)
  const list = [...(first?.list ?? [])]
  const total = first?.total ?? list.length
  const pages = Math.min(Math.max(Math.ceil(total / size), 1), maxPages)
  for (let p = 2; p <= pages; p++) {
    const next = await request(p, size)
    list.push(...(next?.list ?? []))
  }
  return list
}

export function fetchAllAdminServiceTypes(): Promise<AdminServiceTypeRow[]> {
  return fetchAll((page, size) => fetchAdminServiceTypes({ page, size }))
}

export function fetchAllAdminServiceItems(keyword?: string): Promise<AdminServiceItemRow[]> {
  return fetchAll((page, size) => fetchAdminServiceItems({ keyword, page, size }))
}

export function fetchAllAdminServiceAreas(keyword?: string): Promise<AdminServiceAreaRow[]> {
  return fetchAll((page, size) => fetchAdminServiceAreas({ keyword, page, size }))
}
