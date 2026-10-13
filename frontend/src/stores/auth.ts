import { defineStore } from 'pinia'
import { get, post, TOKEN_KEY, USER_KEY } from '@/utils/request'

/** 登录用户信息（后端 LoginResponse.user） */
export interface UserInfo {
  id: number
  username: string
  nickname?: string
  avatar?: string
  phone?: string
  role: 'USER' | 'STAFF' | 'ADMIN'
  balance?: number
  status?: number
}

interface LoginParams {
  username: string
  password: string
  captchaKey: string
  captchaCode: string
  rememberMe?: boolean
}

interface LoginResult {
  token: string
  expiresAt: number
  user: UserInfo
}

/**
 * 认证状态（选择 Pinia 的理由见架构文档：
 * 1) Vue3 官方推荐、TS 类型推导友好；2) setup 语法简洁、模块化；
 * 3) 与 Vue Router 5 / 生态一等公民集成，较 Vuex 样板更少）
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: (JSON.parse(localStorage.getItem(USER_KEY) || 'null') as UserInfo | null),
  }),
  getters: {
    isLogin: (s) => !!s.token,
    role: (s) => s.user?.role || '',
    isAdmin: (s) => s.user?.role === 'ADMIN',
    isStaff: (s) => s.user?.role === 'STAFF',
    isUser: (s) => s.user?.role === 'USER',
    displayName: (s) => s.user?.nickname || s.user?.username || '',
  },
  actions: {
    async login(params: LoginParams) {
      const data = await post<LoginResult>('/auth/login', params)
      this.token = data.token
      this.user = data.user
      localStorage.setItem(TOKEN_KEY, data.token)
      localStorage.setItem(USER_KEY, JSON.stringify(data.user))
      return data
    },
    async refreshProfile() {
      if (!this.token) return
      const user = await get<UserInfo>('/user/profile')
      this.user = user
      localStorage.setItem(USER_KEY, JSON.stringify(user))
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    },
    /** 登录后按角色路由到各自工作台（PRD 2.1 统一入口按角色路由） */
    homeRouteByRole(): string {
      switch (this.user?.role) {
        case 'ADMIN':
          return '/admin'
        case 'STAFF':
          return '/staff'
        default:
          return '/'
      }
    },
  },
})
