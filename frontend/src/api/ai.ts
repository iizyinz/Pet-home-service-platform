import { get, post, ssePost } from '@/utils/request'
import type { AiConversation, AiMessage } from '@/types'

/**
 * AI 宠物顾问接口（AiController，/api/ai/**）
 * - 会话管理：GET/POST /api/ai/conversations、GET /api/ai/conversations/{id}/messages
 * - 流式问答：POST /api/ai/chat（text/event-stream：message / done / error）
 * - 快捷问题：GET /api/ai/quick-questions
 * 后端 /chat 成功后才落库（user + assistant），失败仅回 error 事件且不写会话。
 */

/** GET /api/ai/quick-questions 元素（后端为 Map<String,String>） */
export interface AiQuickQuestion {
  title: string
  question: string
}

/** POST /api/ai/chat 请求体（对齐 dto/ai/AiChatRequest） */
export interface AiChatBody {
  /** 为空时后端自动新建会话；为避免拿不到新会话 ID，前端会先显式建会话 */
  conversationId?: number
  question: string
}

/** SSE 事件回调（与 utils/request.ts 的 ssePost 对齐） */
export interface AiStreamHandlers {
  onDelta: (delta: string) => void
  onDone: (content: string) => void
  onError: (message: string) => void
}

/** 本地会话气泡（历史消息与流式临时气泡统一渲染模型） */
export interface AiChatItem {
  key: number
  role: 'user' | 'assistant'
  content: string
  createdAt?: string
  /** 回答失败/中断标记，渲染错误气泡 + 重试入口 */
  error?: boolean
}

/** 我的会话列表（按更新时间倒序） */
export const fetchAiConversations = () => get<AiConversation[]>('/ai/conversations')

/** 新建会话（首个提问前调用，保证拿得到 conversationId） */
export const createAiConversation = () => post<AiConversation>('/ai/conversations')

/** 会话历史消息（role: system/user/assistant，按 id 升序） */
export const fetchAiMessages = (conversationId: number) =>
  get<AiMessage[]>(`/ai/conversations/${conversationId}/messages`)

/** 快捷提问建议 */
export const fetchQuickQuestions = () => get<AiQuickQuestion[]>('/ai/quick-questions')

/** SSE 流式提问：POST /api/ai/chat */
export function streamAiChat(body: AiChatBody, handlers: AiStreamHandlers, signal?: AbortSignal): Promise<void> {
  return ssePost('/ai/chat', body, handlers, signal)
}
