import { get, post, type PageResult } from '@/utils/request'
import type { OrderListVO, OrderDetailVO, OrderProgress, OrderStatus } from '@/types'

export interface OrderQuery {
  /** 传 'ALL' 或空则不过滤 */
  status?: OrderStatus | 'ALL' | ''
  page?: number
  size?: number
}

export interface CreateOrderParams {
  petId: number
  serviceItemId: number
  staffId: number
  addressId: number
  appointmentTime: string
  remark?: string
}

/** 订单状态机动作（PRD 第四章转移表） */
export type OrderAction = 'cancel' | 'accept' | 'reject' | 'start' | 'finish' | 'confirm'

export const fetchOrders = (q: OrderQuery) =>
  get<PageResult<OrderListVO>>('/orders', {
    status: !q.status || q.status === 'ALL' ? undefined : q.status,
    page: q.page ?? 1,
    size: q.size ?? 10,
  })

export const fetchOrderDetail = (id: number) => get<OrderDetailVO>(`/orders/${id}`)

export const createOrder = (data: CreateOrderParams) => post<OrderListVO>('/orders', data)

/** 统一的状态机动作调用，body 视动作可选（cancel/reject 带 reason，finish 带实际时间） */
export const doOrderAction = (id: number, action: OrderAction, body?: unknown) =>
  post<void>(`/orders/${id}/${action}`, body ?? {})

export const publishProgress = (
  id: number,
  data: { title: string; images?: string[]; serviceTime?: string },
) => post<OrderProgress>(`/orders/${id}/progress`, data)

/** 主人查看进度即触发已读（后端处理） */
export const fetchProgress = (id: number) => get<OrderProgress[]>(`/orders/${id}/progress`)
