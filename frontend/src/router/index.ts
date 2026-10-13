import { createRouter, createWebHistory } from 'vue-router'
import commonRoutes from './routes/common'
import portalRoutes from './routes/portal'
import staffRoutes from './routes/staff'
import adminRoutes from './routes/admin'
import { setupGuards } from './guards'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior: () => ({ top: 0 }),
  routes: [...commonRoutes, ...portalRoutes, ...staffRoutes, ...adminRoutes],
})

setupGuards(router)

export default router
