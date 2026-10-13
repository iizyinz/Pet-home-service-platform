import { get, post, type PageResult } from '@/utils/request'
import type { Review } from '@/types'
import { fetchOrderDetail } from '@/api/orders'

/**
 * 评价接口封装（对齐 ReviewController / ReviewService）：
 * - POST /reviews           提交评价（仅本人已完成订单、一单一评，重复提交后端 2005 拒绝）
 * - GET  /reviews?my=1      我发出的评价（分页；后端只返回 Review 实体，无订单快照字段）
 */

export interface ReviewPayload {
  /** 已完成订单 ID */
  orderId: number
  /** 星级 1-5（后端 @Min(1) @Max(5)） */
  rating: number
  /** 文字内容（选填，后端最多 500 字） */
  content?: string
  /** 图片 URL 列表（选填，经 POST /files/upload 上传后得到） */
  images?: string[]
}

export interface MyReviewQuery {
  page?: number
  size?: number
}

/** 我的评价列表项：评价本体 + 前端补充拼装的订单摘要（后端列表接口不含订单信息） */
export interface MyReviewItem {
  review: Review
  /** 已解析的图片数组（后端 images 为 JSON 数组字符串） */
  images: string[]
  orderNo?: string
  itemName?: string
  staffName?: string
  staffAvatar?: string
  amount?: number
  appointmentTime?: string
  /** 订单详情拉取失败时兜底展示 */
  orderMissing?: boolean
}

/** 提交评价 */
export const submitReview = (data: ReviewPayload) => post<Review>('/reviews', data)

/** 我发出的评价（my=1） */
export const fetchMyReviews = (q: MyReviewQuery = {}) =>
  get<PageResult<Review>>('/reviews', {
    my: 1,
    page: q.page ?? 1,
    size: q.size ?? 10,
  })

/** 后端 images 是 JSON 数组字符串（可能为 "null"），统一解析为 string[] */
export function parseReviewImages(images?: string[] | string | null): string[] {
  if (Array.isArray(images)) {
    return images.filter((u): u is string => typeof u === 'string' && u.length > 0)
  }
  if (typeof images !== 'string' || !images.startsWith('[')) return []
  try {
    const arr = JSON.parse(images) as unknown
    return Array.isArray(arr) ? arr.filter((u): u is string => typeof u === 'string' && u.length > 0) : []
  } catch {
    return []
  }
}

/**
 * 我的评价分页：列表 + 按 orderId 并行补拉订单摘要（订单号/项目/服务师）。
 * 单笔订单详情失败不影响整页渲染，降级为仅展示评价内容。
 */
export async function fetchMyReviewItems(q: MyReviewQuery = {}): Promise<PageResult<MyReviewItem>> {
  const page = await fetchMyReviews(q)
  const list = page?.list ?? []
  const items = await Promise.all(
    list.map(async (review): Promise<MyReviewItem> => {
      const base: MyReviewItem = { review, images: parseReviewImages(review.images) }
      const orderId = Number(review.orderId ?? 0)
      if (!orderId) return { ...base, orderMissing: true }
      try {
        const order = await fetchOrderDetail(orderId)
        return {
          ...base,
          orderNo: order.orderNo,
          itemName: order.itemName,
          staffName: order.staffName,
          staffAvatar: order.staffAvatar,
          amount: order.amount,
          appointmentTime: order.appointmentTime,
        }
      } catch {
        // 拦截器已提示；该条订单信息降级展示
        return { ...base, orderMissing: true }
      }
    }),
  )
  return {
    list: items,
    total: page?.total ?? 0,
    page: page?.page ?? q.page ?? 1,
    size: page?.size ?? q.size ?? 10,
  }
}
