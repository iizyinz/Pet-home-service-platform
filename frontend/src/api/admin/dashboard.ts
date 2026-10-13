import { get } from '@/utils/request'

/** GET /admin/dashboard/overview → 9 个指标 */
export interface DashboardOverview {
  totalOrders: number
  todayOrders: number
  totalRevenue: number
  todayRevenue: number
  userCount: number
  staffCount: number
  petCount: number
  satisfiedRate: number
  totalReviews: number
}

export const fetchOverview = () => get<DashboardOverview>('/admin/dashboard/overview')

/** 近 N 天订单趋势 */
export interface TrendPoint {
  date: string
  count: number
}

export const fetchOrderTrend = (days = 7) => get<TrendPoint[]>('/admin/dashboard/order-trend', { days })

/** 订单状态分布 */
export interface StatusPoint {
  status: string
  count: number
}

export const fetchStatusDist = () => get<StatusPoint[]>('/admin/dashboard/status-dist')

/** 服务项目排行（Top10，按已完成订单数） */
export interface RankPoint {
  name: string
  count: number
}

export const fetchItemRank = () => get<RankPoint[]>('/admin/dashboard/item-rank')

/** 服务人员接单排行（Top10） */
export interface StaffRankPoint {
  staffId: number
  name: string
  count: number
}

export const fetchStaffRank = () => get<StaffRankPoint[]>('/admin/dashboard/staff-rank')

/** 评分分布（固定 5 条，rating 1~5） */
export interface RatingPoint {
  rating: number
  count: number
  percent: number
}

export const fetchRatingDist = () => get<RatingPoint[]>('/admin/dashboard/rating-dist')

/** 评价词云（HanLP 分词去停用词，Top80） */
export interface WordPoint {
  name: string
  value: number
}

export const fetchWordcloud = (topN = 80) => get<WordPoint[]>('/admin/dashboard/wordcloud', { topN })
