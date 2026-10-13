import { onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'

/**
 * 顶栏头像/昵称与账号资料保持一致：
 * 进入布局时拉一次 GET /user/profile 同步 auth store（本地 localStorage 可能是登录时的旧快照）。
 * 每次页面加载只请求一次；失败则允许下次挂载重试。
 */
let synced = false

export function useProfileSync(): void {
  const auth = useAuthStore()

  onMounted(() => {
    if (!auth.isLogin || synced) return
    synced = true
    auth.refreshProfile().catch(() => {
      synced = false
    })
  })
}
