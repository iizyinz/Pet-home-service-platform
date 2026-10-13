import type { RouteRecordRaw } from 'vue-router'

/**
 * 管理端（PRD 第七章 / UI 清单 5.3）
 * 需登录 + 仅 ADMIN，主题 theme-admin
 * 注：/admin/login 为同级独立路由（见 common.ts），不会被本父路由吞掉。
 */
const adminRoutes: RouteRecordRaw[] = [
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { requiresAuth: true, roles: ['ADMIN'], theme: 'admin' },
    children: [
      { path: '', name: 'AdminDashboard', component: () => import('@/views/admin/dashboard.vue'), meta: { title: '数据看板', theme: 'admin' } },
      { path: 'users', name: 'AdminUsers', component: () => import('@/views/admin/users.vue'), meta: { title: '用户管理', theme: 'admin' } },
      { path: 'staffs', name: 'AdminStaffs', component: () => import('@/views/admin/staffs.vue'), meta: { title: '服务人员管理', theme: 'admin' } },
      { path: 'pets', name: 'AdminPets', component: () => import('@/views/admin/pets.vue'), meta: { title: '宠物档案管理', theme: 'admin' } },
      { path: 'service-types', name: 'AdminServiceTypes', component: () => import('@/views/admin/serviceTypes.vue'), meta: { title: '服务类型管理', theme: 'admin' } },
      { path: 'service-items', name: 'AdminServiceItems', component: () => import('@/views/admin/serviceItems.vue'), meta: { title: '服务项目管理', theme: 'admin' } },
      { path: 'service-areas', name: 'AdminServiceAreas', component: () => import('@/views/admin/serviceAreas.vue'), meta: { title: '服务区域管理', theme: 'admin' } },
      { path: 'orders', name: 'AdminOrders', component: () => import('@/views/admin/orders.vue'), meta: { title: '订单管理', theme: 'admin' } },
      { path: 'reviews', name: 'AdminReviews', component: () => import('@/views/admin/reviews.vue'), meta: { title: '评价管理', theme: 'admin' } },
      { path: 'banners', name: 'AdminBanners', component: () => import('@/views/admin/banners.vue'), meta: { title: '轮播图管理', theme: 'admin' } },
      { path: 'announcements', name: 'AdminAnnouncements', component: () => import('@/views/admin/announcements.vue'), meta: { title: '系统公告', theme: 'admin' } },
      { path: 'notices', name: 'AdminNotices', component: () => import('@/views/admin/notices.vue'), meta: { title: '通知管理', theme: 'admin' } },
      { path: 'balance-records', name: 'AdminBalanceRecords', component: () => import('@/views/admin/balanceRecords.vue'), meta: { title: '余额流水', theme: 'admin' } },
      { path: 'operation-logs', name: 'AdminOperationLogs', component: () => import('@/views/admin/operationLogs.vue'), meta: { title: '操作日志', theme: 'admin' } },
    ],
  },
]

export default adminRoutes
