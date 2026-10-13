import { computed } from 'vue'
import { useAppStore } from '@/stores/app'
import { currentPrimary, currentTheme, palette, primaryRamp } from '@/utils/chart-theme'

/**
 * 图表取色响应式化。
 *
 * 为什么需要它：`currentPrimary()` 读的是 `getComputedStyle`，不是响应式依赖，
 * 三端主题切换时 computed 的 option 不会重新求值，图表会拿着旧颜色重绘。
 * 这里把 `app.theme` 作为显式依赖读一次，切换主题时 option 自动失效 → BaseChart
 * 监听 app.theme 后 dispose+init，拿到的就是新主色。
 */
export function useChartTheme() {
  const app = useAppStore()

  /** 当前端主色（portal #FF8A3D / staff #4ECB71 / admin #5B7FFF） */
  const primary = computed(() => {
    void app.theme
    return currentPrimary()
  })

  /** 当前端主色环（已按主色==强调色去重） */
  const colors = computed(() => {
    void app.theme
    return palette()
  })

  /** 当前端主色的深浅档（同系列多序列区分） */
  const ramp = computed(() => {
    void app.theme
    return primaryRamp()
  })

  /** 当前端标识，便于组件内做分支 */
  const theme = computed(() => {
    void app.theme
    return currentTheme()
  })

  return { primary, colors, ramp, theme }
}
