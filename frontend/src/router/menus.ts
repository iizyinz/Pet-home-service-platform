/**
 * 菜单结构（对应设计文档 §3.2）
 * 与路由表分离：菜单可分组、可排序、可隐藏，路由负责组件与权限。
 */
import type { Component } from 'vue'
import {
  DataAnalysis,
  User,
  UserFilled,
  ChatDotRound,
  Collection,
  Box,
  Location,
  Tickets,
  Star,
  Picture,
  Document,
  Bell,
  Wallet,
  Memo,
  Odometer,
  Calendar,
  HomeFilled,
  Service,
  List,
} from '@element-plus/icons-vue'

export interface MenuNode {
  title: string
  path: string
  icon?: Component
}

export interface MenuGroup {
  title: string
  /** 分组图标：侧边栏折叠（collapse）时仅展示图标，必须提供 */
  icon?: Component
  items: MenuNode[]
}

/** 管理后台左侧菜单 */
export const adminMenus: MenuGroup[] = [
  {
    title: '概览',
    icon: DataAnalysis,
    items: [{ title: '数据看板', path: '/admin', icon: DataAnalysis }],
  },
  {
    title: '主数据',
    icon: User,
    items: [
      { title: '用户管理', path: '/admin/users', icon: User },
      { title: '服务人员管理', path: '/admin/staffs', icon: UserFilled },
      { title: '宠物档案管理', path: '/admin/pets', icon: ChatDotRound },
    ],
  },
  {
    title: '服务目录',
    icon: Collection,
    items: [
      { title: '服务类型管理', path: '/admin/service-types', icon: Collection },
      { title: '服务项目管理', path: '/admin/service-items', icon: Box },
      { title: '服务区域管理', path: '/admin/service-areas', icon: Location },
    ],
  },
  {
    title: '运营管理',
    icon: Tickets,
    items: [
      { title: '订单管理', path: '/admin/orders', icon: Tickets },
      { title: '评价管理', path: '/admin/reviews', icon: Star },
      { title: '轮播图管理', path: '/admin/banners', icon: Picture },
      { title: '系统公告', path: '/admin/announcements', icon: Document },
      { title: '通知管理', path: '/admin/notices', icon: Bell },
    ],
  },
  {
    title: '财务与审计',
    icon: Wallet,
    items: [
      { title: '余额流水', path: '/admin/balance-records', icon: Wallet },
      { title: '操作日志', path: '/admin/operation-logs', icon: Memo },
    ],
  },
]

/** 服务人员端顶部导航 */
export const staffMenus: MenuNode[] = [
  { title: '工作台', path: '/staff', icon: Odometer },
  { title: '订单处理', path: '/staff/orders', icon: Tickets },
  { title: '服务排期', path: '/staff/schedule', icon: Calendar },
  { title: '我的评价', path: '/staff/reviews', icon: Star },
  { title: '消息中心', path: '/staff/messages', icon: ChatDotRound },
  { title: '个人资料', path: '/staff/profile', icon: User },
]

/** 用户端底部 Tab（PRD 5.2：首页 / 服务 / 订单 / 消息 / 我的） */
export const portalTabs: MenuNode[] = [
  { title: '首页', path: '/home', icon: HomeFilled },
  { title: '服务', path: '/services', icon: Service },
  { title: '订单', path: '/orders', icon: List },
  { title: '消息', path: '/messages', icon: ChatDotRound },
  { title: '我的', path: '/profile', icon: UserFilled },
]
