import type { RouteRecordRaw } from 'vue-router'

/** 公共路由：登录 / 权限不足 / 404（均免登录） */
const commonRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/login.vue'),
    meta: { title: '登录', theme: 'portal', public: true },
  },
  {
    path: '/admin/login',
    name: 'AdminLogin',
    component: () => import('@/views/auth/login.vue'),
    meta: { title: '后台登录', theme: 'admin', public: true },
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { title: '权限不足', public: true },
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在', public: true },
  },
  {
    // 兜底：未匹配的路径统一进 404（静态路由优先级高于通配，不会遮蔽正常页面）
    path: '/:pathMatch(.*)*',
    name: 'CatchAll',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在', public: true },
  },
]

export default commonRoutes
