import type { RouteRecordRaw } from 'vue-router'

/**
 * 用户端（PRD 第五章 / UI 清单 5.1）
 * 需登录 + 仅 USER，主题 theme-portal
 */
const portalRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/layouts/PortalLayout.vue'),
    meta: { requiresAuth: true, roles: ['USER'], theme: 'portal' },
    children: [
      { path: '', redirect: '/home' },
      { path: 'home', name: 'Home', component: () => import('@/views/portal/home.vue'), meta: { title: '首页', theme: 'portal' } },
      { path: 'services', name: 'ServiceList', component: () => import('@/views/portal/services.vue'), meta: { title: '服务列表', theme: 'portal' } },
      { path: 'services/:id', name: 'ServiceDetail', component: () => import('@/views/portal/serviceDetail.vue'), meta: { title: '服务详情', theme: 'portal', hidden: true, activeMenu: '/services' } },
      { path: 'providers', name: 'ProviderList', component: () => import('@/views/portal/providers.vue'), meta: { title: '服务人员', theme: 'portal' } },
      { path: 'providers/:id', name: 'ProviderDetail', component: () => import('@/views/portal/providerDetail.vue'), meta: { title: '服务人员详情', theme: 'portal', hidden: true, activeMenu: '/providers' } },
      { path: 'orders/create', name: 'OrderCreate', component: () => import('@/views/portal/orderCreate.vue'), meta: { title: '预约下单', theme: 'portal', hidden: true, activeMenu: '/orders' } },
      { path: 'orders', name: 'OrderList', component: () => import('@/views/portal/orderList.vue'), meta: { title: '我的订单', theme: 'portal' } },
      { path: 'orders/:id', name: 'OrderDetail', component: () => import('@/views/portal/orderDetail.vue'), meta: { title: '订单详情', theme: 'portal', hidden: true, activeMenu: '/orders' } },
      { path: 'orders/:id/review', name: 'OrderReview', component: () => import('@/views/portal/orderReview.vue'), meta: { title: '评价订单', theme: 'portal', hidden: true, activeMenu: '/orders' } },
      { path: 'pets', name: 'PetList', component: () => import('@/views/portal/pets.vue'), meta: { title: '宠物档案', theme: 'portal' } },
      { path: 'addresses', name: 'AddressList', component: () => import('@/views/portal/addresses.vue'), meta: { title: '地址管理', theme: 'portal' } },
      { path: 'favorites', name: 'FavoriteList', component: () => import('@/views/portal/favorites.vue'), meta: { title: '我的收藏', theme: 'portal' } },
      { path: 'my-reviews', name: 'MyReviews', component: () => import('@/views/portal/myReviews.vue'), meta: { title: '我的评价', theme: 'portal' } },
      { path: 'wallet', name: 'Wallet', component: () => import('@/views/portal/wallet.vue'), meta: { title: '我的钱包', theme: 'portal' } },
      { path: 'messages', name: 'Messages', component: () => import('@/views/portal/messages.vue'), meta: { title: '消息中心', theme: 'portal' } },
      { path: 'chat/:peerId', name: 'Chat', component: () => import('@/views/portal/chat.vue'), meta: { title: '在线聊天', theme: 'portal', hidden: true, activeMenu: '/messages' } },
      { path: 'ai', name: 'AiAdvisor', component: () => import('@/views/portal/ai.vue'), meta: { title: 'AI 宠物顾问', theme: 'portal' } },
      { path: 'profile', name: 'Profile', component: () => import('@/views/portal/profile.vue'), meta: { title: '个人中心', theme: 'portal' } },
    ],
  },
]

export default portalRoutes
