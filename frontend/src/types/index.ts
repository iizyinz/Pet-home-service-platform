/** 后端实体 / VO 类型定义 —— 与 Controller 返回 JSON 逐字对齐 */

/** 订单状态机（PRD 附录 A） */
export type OrderStatus = 'PENDING_CONFIRM' | 'CONFIRMED' | 'SERVING' | 'PENDING_SETTLE' | 'COMPLETED' | 'CANCELLED'
export type Role = 'USER' | 'STAFF' | 'ADMIN'

/* ============ 认证 ============ */
export interface Captcha {
  captchaKey: string
  /** base64 图片（GET /api/auth/captcha 返回字段名与后端 CaptchaService 对齐） */
  captchaImg: string
}

export interface RegisterParams {
  username: string
  password: string
  nickname?: string
  phone?: string
  role: Role
  captchaKey: string
  captchaCode: string
}

/* ============ 用户（sys_user，password 为 WRITE_ONLY 不返回） ============ */
export interface SysUser {
  id: number
  username: string
  nickname?: string
  avatar?: string
  phone?: string
  role: Role
  balance?: number
  status?: number
  lastLoginAt?: string
  createdAt?: string
  updatedAt?: string
}

/* ============ 宠物 / 地址 ============ */
export interface Pet {
  id: number
  userId?: number
  name: string
  breed?: string
  age?: number
  weightKg?: number
  healthInfo?: string
  specialNeed?: string
  avatar?: string
  createdAt?: string
}

export interface UserAddress {
  id: number
  contactName: string
  contactPhone: string
  region?: string
  detail: string
  isDefault?: number
}

/* ============ 服务目录 ============ */
export interface ServiceType {
  id: number
  name: string
  icon?: string
  sort?: number
  status?: number
}

export interface ServiceArea {
  id: number
  name: string
  code?: string
  parentId?: number
  level?: number
  status?: number
}

export interface ServiceItem {
  id: number
  typeId?: number
  name: string
  price: number
  durationMinutes?: number
  coverUrl?: string
  /** 后端为 JSON 数组字符串 */
  applicableBreeds?: string
  notice?: string
  status?: number
}

/* ============ 内容 ============ */
export interface Banner {
  id: number
  title?: string
  imageUrl: string
  link?: string
  sort?: number
  status?: number
}

export interface Announcement {
  id: number
  title: string
  content?: string
  status?: number
  createdAt?: string
}

/* ============ 服务人员 ============ */
/** 用户端服务师列表 VO（phone 已脱敏） */
export interface StaffListVO {
  id: number
  nickname?: string
  avatar?: string
  phone?: string
  areaId?: number
  areaName?: string
  experienceYears?: number
  avgRating?: number
  completedCount?: number
  certStatus?: number
  goodAtBreeds?: string[]
}

/** 服务师详情（含收藏态与可服务项目） */
export interface StaffDetailVO extends StaffListVO {
  intro?: string
  certImages?: string[]
  rejectReason?: string
  favorited?: boolean
  items?: ServiceItem[]
}

/** GET /staffs/me/profile */
export interface StaffProfile {
  id?: number
  userId?: number
  nickname?: string
  avatar?: string
  intro?: string
  experienceYears?: number
  goodAtBreeds?: string[]
  areaId?: number
  areaName?: string
  certStatus?: number
  certImages?: string[]
  rejectReason?: string
  avgRating?: number
  completedCount?: number
  itemIds?: number[]
}

/** GET /admin/staffs 分页元素（主键是 userId，不是 id） */
export interface AdminStaffRow {
  userId: number
  username?: string
  nickname?: string
  phone?: string
  avatar?: string
  intro?: string
  experienceYears?: number
  areaId?: number
  certStatus?: number
  certImages?: string[] | string
  rejectReason?: string
  avgRating?: number
  completedCount?: number
  status?: number
}

/* ============ 订单（service_order + OrderListVO 拼装） ============ */
export interface OrderListVO {
  id: number
  orderNo: string
  /** 主人昵称（管理端列表拼装） */
  userId: number
  staffId?: number
  petId?: number
  serviceItemId?: number
  addressId?: number
  itemName?: string
  amount: number
  status: OrderStatus
  statusLabel?: string
  appointmentTime?: string
  startTime?: string
  finishTime?: string
  confirmTime?: string
  cancelReason?: string
  remark?: string
  progressReadAt?: string
  /** VO 扩展字段 */
  petName?: string
  petBreed?: string
  staffName?: string
  staffAvatar?: string
  ownerName?: string
  unreadProgress?: number
  createdAt?: string
  updatedAt?: string
}

export interface OrderProgress {
  id: number
  orderId: number
  title: string
  images?: string[] | string
  serviceTime?: string
  createdBy?: number
  createdAt?: string
}

/** 订单详情（OrderDetailVO，管理端与用户端共用同一 DTO） */
export interface OrderDetailVO extends OrderListVO {
  /** 上门地址：后端返回「省市区+详细」拼接字符串（非地址对象），联系人/电话在 contactName/contactPhone */
  address?: string
  contactName?: string
  contactPhone?: string
  pet?: Pet
  staff?: StaffListVO
  user?: SysUser
  durationMinutes?: number
  /** 服务进度列表（注意字段名是 progressList，不是 progress） */
  progressList?: OrderProgress[]
  progressUnread?: boolean
  reviewed?: boolean
  availableActions?: string[]
}

/* ============ 评价 / 通知 / 流水 / 日志 ============ */
export interface Review {
  id: number
  orderId?: number
  userId?: number
  userNickname?: string
  userAvatar?: string
  staffId?: number
  staffNickname?: string
  rating: number
  content?: string
  images?: string[] | string
  status?: number
  createdAt?: string
}

export interface Notice {
  id: number
  userId?: number
  type?: 'ORDER' | 'REVIEW' | 'CERT' | 'SYSTEM' | string
  title: string
  content?: string
  bizId?: number
  isRead?: number
  createdAt?: string
}

export interface BalanceRecord {
  id: number
  userId?: number
  type: 'CONSUME' | 'RECHARGE' | 'REFUND' | 'ADJUST'
  amount: number
  balanceAfter?: number
  orderId?: number
  channel?: string
  remark?: string
  createdAt?: string
}

export interface OperationLog {
  id: number
  operatorId?: number
  operatorName?: string
  action: string
  target?: string
  detail?: string
  result?: 'SUCCESS' | 'FAIL' | string
  ip?: string
  createdAt?: string
}

/* ============ 聊天 / AI ============ */
export interface ChatConversation {
  id: number
  peerId: number
  peerName?: string
  peerAvatar?: string
  peerRole?: Role
  online?: boolean
  lastMessage?: string
  lastMessageAt?: string
  unread?: number
}

export interface ChatMessage {
  id: number
  conversationId?: number
  senderId: number
  receiverId?: number
  content: string
  msgType?: 'TEXT' | 'IMAGE'
  isRead?: number
  createdAt?: string
}

export interface AiConversation {
  id: number
  title?: string
  updatedAt?: string
  createdAt?: string
}

export interface AiMessage {
  id: number
  conversationId?: number
  role: 'system' | 'user' | 'assistant'
  content: string
  createdAt?: string
}
