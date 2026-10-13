import { defineStore } from 'pinia'

export type ThemeName = 'portal' | 'staff' | 'admin'

const COLLAPSE_KEY = 'pet_sidebar_collapsed'

/**
 * 全局 UI 状态：
 *  - theme：当前端主题（路由守卫写入），用于 ECharts 换肤重绘
 *  - collapsed：管理端侧边栏折叠
 *  - unread：站内通知未读数（NoticeBell 轮询写入）
 */
export const useAppStore = defineStore('app', {
  state: () => ({
    // 初始为 null：必须允许「首次写入 portal」真正落到 <html> 类名上，
    // 否则 element-theme.css 的 :root（0,1,0）会压过 tokens.css 的 theme-* 覆盖。
    theme: null as ThemeName | null,
    collapsed: localStorage.getItem(COLLAPSE_KEY) === '1',
    unread: 0,
  }),
  actions: {
    setTheme(theme: ThemeName) {
      const cls = `theme-${theme}`
      if (this.theme === theme && document.documentElement.classList.contains(cls)) return
      this.theme = theme
      document.documentElement.className = cls
    },
    toggleCollapsed() {
      this.collapsed = !this.collapsed
      localStorage.setItem(COLLAPSE_KEY, this.collapsed ? '1' : '0')
    },
    setUnread(count: number) {
      this.unread = count
    },
  },
})
