import { get, post, put, del, type PageResult } from '@/utils/request'
import type {
  Announcement,
  BalanceRecord,
  Banner,
  Notice,
  OperationLog,
  OrderDetailVO,
  OrderListVO,
  OrderStatus,
  Review,
} from '@/types'

/**
 * 管理端 · 运营域接口封装（订单干预 / 评价审核 / 内容运营 / 站内通知 / 资金流水 / 审计日志）
 * 路径、参数名与返回字段严格对齐后端：
 *  - AdminOrderController / AdminReviewController / AbstractCrudController
 *  - AdminOpsController / NoticeController
 * 统一响应 { code, message, data }，分页统一 { list, total, page, size }（page 从 1 开始）。
 */

/* ===================== 订单（/api/admin/orders） ===================== */

export interface AdminOrderQuery {
  /** 状态机状态；'ALL' 或空 = 不过滤 */
  status?: OrderStatus | 'ALL' | ''
  /** 下单用户 ID */
  userId?: number
  /** 接单服务师 ID */
  staffId?: number
  page?: number
  size?: number
}

/** 订单分页（状态 / 用户 / 服务师筛选，按 ID 倒序） */
export const fetchAdminOrders = (q: AdminOrderQuery) =>
  get<PageResult<OrderListVO>>('/admin/orders', {
    status: !q.status || q.status === 'ALL' ? undefined : q.status,
    userId: q.userId || undefined,
    staffId: q.staffId || undefined,
    page: q.page ?? 1,
    size: q.size ?? 10,
  })

/** 订单详情（后端归属校验放行 ADMIN，可查看任意订单） */
export const fetchAdminOrderDetail = (id: number) => get<OrderDetailVO>(`/admin/orders/${id}`)

/** 运营干预：关闭订单并退款（写 REFUND 流水 + 通知双方 + 审计），仅非终态订单可干预 */
export const closeOrderWithRefund = (id: number, reason?: string) =>
  post<void>(`/admin/orders/${id}/close`, reason ? { reason } : {})

/* ===================== 评价（/api/admin/reviews） ===================== */

export interface AdminReviewQuery {
  /** 被评服务师 ID */
  staffId?: number
  /** 评价人 ID */
  userId?: number
  /** 1 显示 0 隐藏 */
  status?: number
  page?: number
  size?: number
}

/** 评价分页（服务师 / 评价人 / 显示状态筛选） */
export const fetchAdminReviews = (q: AdminReviewQuery) =>
  get<PageResult<Review>>('/admin/reviews', {
    staffId: q.staffId || undefined,
    userId: q.userId || undefined,
    status: q.status,
    page: q.page ?? 1,
    size: q.size ?? 10,
  })

/** 隐藏 / 恢复评价（status=1 显示，0 隐藏；触发服务师信誉重算 + 审计） */
export const updateReviewStatus = (id: number, status: number) =>
  put<void>(`/admin/reviews/${id}/status`, undefined, { params: { status } })

/* ===================== 轮播图（/api/admin/banners，通用 CRUD） ===================== */

/** banner 行数据（补上实体里的审计时间字段，类型主干复用 @/types 的 Banner） */
export interface BannerRow extends Banner {
  createdAt?: string
  updatedAt?: string
}

export interface AdminContentQuery {
  /** 标题关键词（后端 LIKE） */
  keyword?: string
  page?: number
  size?: number
}

/** 轮播图分页（按 sort 升序、id 倒序） */
export const fetchAdminBanners = (q: AdminContentQuery) =>
  get<PageResult<BannerRow>>('/admin/banners', {
    keyword: q.keyword || undefined,
    page: q.page ?? 1,
    size: q.size ?? 10,
  })

export interface BannerPayload {
  title: string
  /** 单图地址（POST /files/upload 返回的 url） */
  imageUrl: string
  link?: string
  sort?: number
  /** 1 启用 0 停用 */
  status?: number
}

export const createBanner = (data: BannerPayload) => post<BannerRow>('/admin/banners', data)
export const updateBanner = (id: number, data: BannerPayload) => put<void>(`/admin/banners/${id}`, data)
export const deleteBanner = (id: number) => del<void>(`/admin/banners/${id}`)

/* ===================== 系统公告（/api/admin/announcements，通用 CRUD） ===================== */

/** announcement 行数据（补上 updatedAt，类型主干复用 @/types 的 Announcement） */
export interface AnnouncementRow extends Announcement {
  updatedAt?: string
}

/** 公告分页（标题关键词，按 id 倒序） */
export const fetchAdminAnnouncements = (q: AdminContentQuery) =>
  get<PageResult<AnnouncementRow>>('/admin/announcements', {
    keyword: q.keyword || undefined,
    page: q.page ?? 1,
    size: q.size ?? 10,
  })

export interface AnnouncementPayload {
  title: string
  content: string
  /** 1 发布 0 下架 */
  status?: number
}

export const createAnnouncement = (data: AnnouncementPayload) =>
  post<AnnouncementRow>('/admin/announcements', data)
export const updateAnnouncement = (id: number, data: AnnouncementPayload) =>
  put<void>(`/admin/announcements/${id}`, data)
export const deleteAnnouncement = (id: number) => del<void>(`/admin/announcements/${id}`)

/* ===================== 站内通知（/api/admin/notices） ===================== */

export interface AdminNoticeQuery {
  /** 接收人 ID */
  userId?: number
  page?: number
  size?: number
}

/** 全量站内通知分页（按 id 倒序） */
export const fetchAdminNotices = (q: AdminNoticeQuery) =>
  get<PageResult<Notice>>('/admin/notices', {
    userId: q.userId || undefined,
    page: q.page ?? 1,
    size: q.size ?? 10,
  })

/** 补发通知（注意：后端为 @RequestParam，参数走 query string） */
export const resendNotice = (data: { userId: number; title: string; content?: string }) =>
  post<void>('/admin/notices/resend', undefined, {
    params: { userId: data.userId, title: data.title, content: data.content },
  })

/* ===================== 余额流水（/api/admin/balance-records） ===================== */

export interface AdminBalanceRecordQuery {
  /** CONSUME / RECHARGE / REFUND / ADJUST */
  type?: string
  /** 归属用户 ID */
  userId?: number
  page?: number
  size?: number
}

/** 全量资金流水分页（对账审计用，含变动后余额快照） */
export const fetchAdminBalanceRecords = (q: AdminBalanceRecordQuery) =>
  get<PageResult<BalanceRecord>>('/admin/balance-records', {
    type: q.type || undefined,
    userId: q.userId || undefined,
    page: q.page ?? 1,
    size: q.size ?? 10,
  })

/* ===================== 操作日志（/api/admin/operation-logs） ===================== */

export interface OperationLogQuery {
  /** 动作（后端 LIKE 匹配 action，如 order.close.refund / update / delete） */
  action?: string
  page?: number
  size?: number
}

/** 管理端审计日志分页 */
export const fetchOperationLogs = (q: OperationLogQuery) =>
  get<PageResult<OperationLog>>('/admin/operation-logs', {
    action: q.action || undefined,
    page: q.page ?? 1,
    size: q.size ?? 10,
  })
