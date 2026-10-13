import 'vue-router'

/** 路由元信息（设计文档 §3.1） */
declare module 'vue-router' {
  interface RouteMeta {
    /** 页面标题，同时用于面包屑与 document.title */
    title?: string
    /** 当前端主题类（portal / staff / admin） */
    theme?: 'portal' | 'staff' | 'admin'
    /** 是否需要登录 */
    requiresAuth?: boolean
    /** 允许访问的角色，为空则不限制 */
    roles?: Array<'USER' | 'STAFF' | 'ADMIN'>
    /** 免登录的公开页 */
    public?: boolean
    /** 不在菜单中显示 */
    hidden?: boolean
    /** 高亮的父级菜单路径（用于详情/编辑等二级页） */
    activeMenu?: string
  }
}
