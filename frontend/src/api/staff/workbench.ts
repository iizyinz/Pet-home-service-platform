import { get, post, type PageResult } from '@/utils/request'
import type { OrderListVO, Review, ChatMessage } from '@/types'

/**
 * 服务人员端专属接口（工作台 / 排期 / 我的评价 / 聊天会话）
 * 路径与后端 Controller 一一对应：
 * - StaffWorkbenchController  /api/staff/workbench
 * - ReviewController          /api/reviews?staffId=
 * - ChatController            /api/chat/*
 */

/* ============ 工作台概览（GET /api/staff/workbench） ============ */

/** 工作台指标（OrderService.staffWorkbench + StaffService.myProfile 拼装） */
export interface WorkbenchOverview {
  /** 待接单数（PENDING_CONFIRM） */
  pendingCount: number
  /** 进行中数（已确认 + 服务中 + 待确认完成） */
  inProgressCount: number
  /** 今日完成数（按确认完成时间统计） */
  todayCompletedCount: number
  /** 累计完成订单数 */
  completedCount: number
  /** 累计收入（元，已完成订单金额合计） */
  totalIncome: number
  /** 综合评分 */
  avgRating: number
  /** 认证状态 0 待审核 1 通过 2 驳回 */
  certStatus: number
  /** 接单状态 1 正常 0 已下架 */
  status: number
}

export const fetchWorkbench = () => get<WorkbenchOverview>('/staff/workbench')

/* ============ 月度排期（GET /api/staff/workbench/schedule?month=yyyy-MM） ============ */

/** 按天分组的排期：key = yyyy-MM-dd，value = 当日订单（按预约时间升序） */
export type ScheduleMap = Record<string, OrderListVO[]>

export const fetchSchedule = (month: string) => get<ScheduleMap>('/staff/workbench/schedule', { month })

/* ============ 我收到的评价（GET /api/reviews?staffId=，仅 status=1） ============ */

export const fetchMyReviews = (staffId: number, page = 1, size = 10) =>
  get<PageResult<Review>>('/reviews', { staffId, page, size })

/* ============ 即时聊天（GET/POST /api/chat/*，配合 WebSocket /ws/chat） ============ */

/** GET /api/chat/conversations 返回的会话行（与 types 里的 ChatConversation 字段不同，以后端为准） */
export interface ConversationItem {
  conversationId: number
  peerId: number
  peerNickname?: string
  peerAvatar?: string
  peerRole?: string
  lastMessage?: string
  lastMessageAt?: string
  unreadCount: number
  online: boolean
}

export const fetchConversations = () => get<ConversationItem[]>('/chat/conversations')

/** GET /api/chat/peer?peerId= 对方资料（私信页头部） */
export interface PeerInfo {
  id: number
  nickname?: string
  avatar?: string
  role?: string
  phone?: string
  online?: boolean
}

export const fetchPeerInfo = (peerId: number) => get<PeerInfo>('/chat/peer', { peerId })

/** GET /api/chat/messages?convId= 历史消息分页（按 id 升序，拉取即把发给我的消息标记已读） */
export const fetchChatMessages = (conversationId: number, page = 1, size = 20) =>
  get<PageResult<ChatMessage>>('/chat/messages', { convId: conversationId, page, size })

/** POST /api/chat/messages 发送消息（先落库，接收方在线则 WebSocket 定向推送） */
export const sendChatMessage = (receiverId: number, content: string) =>
  post<ChatMessage>('/chat/messages', { receiverId, content, msgType: 'TEXT' })
