import { get, post, type PageResult } from '@/utils/request'
import type { ChatMessage, Role } from '@/types'

/**
 * 即时聊天接口（后端 ChatController /api/chat，配合 WebSocket /ws/chat?token=）
 * 说明：会话列表每项是后端 Map 拼装（键名见 ChatService.conversationList），
 * 与 types/index.ts 中的旧占位 ChatConversation 不一致，故在此单独定义并以本文件类型为准。
 */
export interface ChatConversationVO {
  conversationId: number
  peerId: number
  peerNickname?: string
  peerAvatar?: string
  peerRole?: Role
  lastMessage?: string
  lastMessageAt?: string
  unreadCount: number
  online: boolean
}

/** 联系人信息（GET /chat/peer，phone 后端已脱敏） */
export interface ChatPeerVO {
  id: number
  nickname?: string
  avatar?: string
  role?: Role
  phone?: string
  online: boolean
}

export interface ChatSendParams {
  receiverId: number
  content: string
  msgType?: 'TEXT' | 'IMAGE'
}

/** 会话列表：每个聊天对象的最新一条消息、时间、未读数、在线状态 */
export const fetchConversations = () => get<ChatConversationVO[]>('/chat/conversations')

/** 联系人信息（私信入口展示对方昵称/头像/角色/在线状态） */
export const fetchPeer = (peerId: number) => get<ChatPeerVO>('/chat/peer', { peerId })

/** 历史消息分页（按 id 升序；拉取即把发给我的消息标记已读，未读数归零） */
export const fetchChatMessages = (convId: number, page = 1, size = 20) =>
  get<PageResult<ChatMessage>>('/chat/messages', { convId, page, size })

/** 发送消息（先落库再推送；接收方在线时经 WebSocket 定向推送，离线留存） */
export const sendChatMessage = (data: ChatSendParams) => post<ChatMessage>('/chat/messages', data)
