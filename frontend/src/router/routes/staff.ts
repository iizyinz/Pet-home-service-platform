import type { RouteRecordRaw } from 'vue-router'

/**
 * 服务人员端（PRD 第六章 / UI 清单 5.2）
 * 需登录 + 仅 STAFF，主题 theme-staff
 */
const staffRoutes: RouteRecordRaw[] = [
  {
    path: '/staff',
    component: () => import('@/layouts/StaffLayout.vue'),
    meta: { requiresAuth: true, roles: ['STAFF'], theme: 'staff' },
    children: [
      { path: '', name: 'StaffDashboard', component: () => import('@/views/staff/dashboard.vue'), meta: { title: '工作台', theme: 'staff' } },
      { path: 'orders', name: 'StaffOrders', component: () => import('@/views/staff/orders.vue'), meta: { title: '订单处理', theme: 'staff' } },
      { path: 'orders/:id', name: 'StaffOrderDetail', component: () => import('@/views/staff/orderDetail.vue'), meta: { title: '订单详情', theme: 'staff', hidden: true, activeMenu: '/staff/orders' } },
      { path: 'schedule', name: 'StaffSchedule', component: () => import('@/views/staff/schedule.vue'), meta: { title: '服务排期', theme: 'staff' } },
      { path: 'reviews', name: 'StaffReviews', component: () => import('@/views/staff/reviews.vue'), meta: { title: '我的评价', theme: 'staff' } },
      { path: 'profile', name: 'StaffProfile', component: () => import('@/views/staff/profile.vue'), meta: { title: '个人资料', theme: 'staff' } },
      { path: 'messages', name: 'StaffMessages', component: () => import('@/views/staff/messages.vue'), meta: { title: '消息中心', theme: 'staff' } },
      { path: 'chat/:peerId', name: 'StaffChat', component: () => import('@/views/staff/chat.vue'), meta: { title: '在线聊天', theme: 'staff', hidden: true, activeMenu: '/staff/messages' } },
    ],
  },
]

export default staffRoutes
