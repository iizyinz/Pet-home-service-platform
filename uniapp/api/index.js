import { get, post, put, del, uploadFile } from '../utils/request'

/* ---------- 认证 / 用户 ---------- */
export const apiCaptcha = () => get('/api/auth/captcha')
export const apiLogin = (data, opts) => post('/api/auth/login', data, opts)
export const apiRegister = (data, opts) => post('/api/auth/register', data, opts)

export const apiProfile = () => get('/api/user/profile')
export const apiUpdateProfile = (data, opts) => put('/api/user/profile', data, opts)
export const apiBalance = () => get('/api/user/balance')
export const apiBalanceRecords = (page = 1, size = 10) =>
	get('/api/user/balance/records', { page, size })
export const apiRecharge = (amount, opts) => post('/api/user/balance/recharge', { amount }, opts)

/* ---------- 宠物 / 地址 ---------- */
export const apiPets = () => get('/api/pets')
export const apiPetDetail = (id) => get('/api/pets/' + id)
export const apiCreatePet = (data, opts) => post('/api/pets', data, opts)
export const apiUpdatePet = (id, data, opts) => put('/api/pets/' + id, data, opts)
export const apiDeletePet = (id) => del('/api/pets/' + id)

export const apiAddresses = () => get('/api/addresses')
export const apiCreateAddress = (data, opts) => post('/api/addresses', data, opts)
export const apiUpdateAddress = (id, data, opts) => put('/api/addresses/' + id, data, opts)
export const apiDeleteAddress = (id) => del('/api/addresses/' + id)

/* ---------- 服务浏览 ---------- */
export const apiServiceTypes = () => get('/api/service-types')
export const apiServiceAreas = () => get('/api/service-areas')
export const apiServiceItems = (params = {}, opts) => get('/api/service-items', params, opts)
export const apiServiceItemDetail = (id) => get('/api/service-items/' + id)

/* ---------- 服务人员 ---------- */
export const apiStaffs = (params = {}, opts) => get('/api/staffs', params, opts)
export const apiStaffDetail = (id) => get('/api/staffs/' + id)
export const apiStaffReviews = (id, page = 1, size = 10) =>
	get(`/api/staffs/${id}/reviews`, { page, size })
export const apiToggleFavorite = (id) => post(`/api/staffs/${id}/favorite`)
export const apiFavorites = () => get('/api/staffs/favorites')

/* ---------- 订单 ---------- */
export const apiCreateOrder = (data, opts) => post('/api/orders', data, opts)
export const apiOrders = (params = {}, opts) => get('/api/orders', params, opts)
export const apiOrderDetail = (id) => get('/api/orders/' + id)
export const apiCancelOrder = (id, reason) => post(`/api/orders/${id}/cancel`, { reason })
export const apiConfirmOrder = (id) => post(`/api/orders/${id}/confirm`)
export const apiOrderProgress = (id) => get(`/api/orders/${id}/progress`)

/* ---------- 评价 ---------- */
export const apiSubmitReview = (data, opts) => post('/api/reviews', data, opts)
export const apiReviews = (params = {}, opts) => get('/api/reviews', params, opts)

/* ---------- 内容 / 通知 ---------- */
export const apiBanners = () => get('/api/banners')
export const apiAnnouncements = (page = 1, size = 10) => get('/api/announcements', { page, size })
export const apiAnnouncementDetail = (id) => get('/api/announcements/' + id)
export const apiNotices = (page = 1, size = 10) => get('/api/notices', { page, size })
export const apiUnreadCount = () => get('/api/notices/unread-count')
export const apiNoticeRead = (id) => post(`/api/notices/${id}/read`)
export const apiNoticeReadAll = () => post('/api/notices/read-all')

/* ---------- 聊天 ---------- */
export const apiConversations = (opts) => get('/api/chat/conversations', undefined, opts)
export const apiPeer = (peerId) => get('/api/chat/peer', { peerId })
export const apiMessages = (convId, page = 1, size = 20) =>
	get('/api/chat/messages', { convId, page, size })
export const apiSendMessage = (data, opts) => post('/api/chat/messages', data, opts)

/* ---------- AI 宠物顾问 ---------- */
export const apiAiConversations = () => get('/api/ai/conversations')
export const apiCreateAiConversation = () => post('/api/ai/conversations')
export const apiAiMessages = (conversationId) => get(`/api/ai/conversations/${conversationId}/messages`)
export const apiQuickQuestions = () => get('/api/ai/quick-questions')

/* ---------- 文件 ---------- */
export const apiUpload = uploadFile
