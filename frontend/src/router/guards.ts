import type { Router } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useAppStore, type ThemeName } from '@/stores/app'

/** 根据路径推断默认主题（无 meta.theme 的兜底） */
function themeOf(path: string): ThemeName {
  if (path.startsWith('/admin')) return 'admin'
  if (path.startsWith('/staff')) return 'staff'
  return 'portal'
}

/**
 * 全局前置守卫（设计文档 §2.3）：
 *  1. 注入主题类到 <html>（含下拉/对话框 teleport 也继承当端主色）
 *  2. 未登录访问受保护路由 -> /login?redirect=
 *  3. 已登录访问登录页    -> 按角色回跳
 *  4. 角色不符            -> /403
 */
export function setupGuards(router: Router): void {
  router.beforeEach((to, from, next) => {
    const auth = useAuthStore()
    const app = useAppStore()

    // 1) 主题与标题
    const theme = (to.meta.theme as ThemeName | undefined) || themeOf(to.path)
    app.setTheme(theme)
    const title = to.meta.title as string | undefined
    document.title = title ? `${title} · 宠物上门服务` : '宠物上门服务'

    const needsAuth = to.matched.some((r) => r.meta.requiresAuth === true)

    // 2) 未登录
    if (!auth.isLogin) {
      if (needsAuth) {
        next({ path: '/login', query: { redirect: to.fullPath } })
        return
      }
      next()
      return
    }

    // 3) 已登录访问登录页
    if (to.path === '/login' || to.path === '/admin/login') {
      next(auth.homeRouteByRole())
      return
    }

    // 4) 角色校验（父路由 meta.roles 会被 to.meta 合并）
    const roles = (to.meta.roles as string[] | undefined) ?? []
    if (roles.length > 0 && !roles.includes(auth.role)) {
      next('/403')
      return
    }

    next()
  })
}
