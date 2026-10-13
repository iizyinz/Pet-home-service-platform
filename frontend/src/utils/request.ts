import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

/** 后端统一响应体 { code, message, data }，code=0 成功 */
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

/** 分页响应 { list, total, page, size } */
export interface PageResult<T = unknown> {
  list: T[]
  total: number
  page: number
  size: number
}

export const TOKEN_KEY = 'pet_token'
export const USER_KEY = 'pet_user'

const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

/** 请求拦截：注入 JWT */
service.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** 响应拦截：统一解析 { code, message, data }，失败统一提示 */
service.interceptors.response.use(
  (response) => {
    // 文件流直接返回
    if (response.config.responseType === 'blob') {
      return response.data
    }
    const res = response.data as ApiResult
    if (res === undefined || res === null) {
      return response.data
    }
    if (res.code === 0) {
      return res.data
    }
    if (res.code === 401) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      ElMessage.error('登录已过期，请重新登录')
      router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
      return Promise.reject(new Error(res.message))
    }
    if (res.code === 403) {
      ElMessage.error(res.message || '无权限访问')
      return Promise.reject(new Error(res.message))
    }
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message))
  },
  (error) => {
    if (error?.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      router.replace('/login')
    } else if (error?.response?.status === 403) {
      ElMessage.error('无权限访问该资源')
    } else if (error?.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请稍后重试')
    } else {
      ElMessage.error(error?.response?.data?.message || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  },
)

export function get<T>(url: string, params?: Record<string, unknown>, config?: AxiosRequestConfig): Promise<T> {
  return service.get(url, { params, ...config }) as Promise<T>
}

export function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return service.post(url, data, config) as Promise<T>
}

export function put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return service.put(url, data, config) as Promise<T>
}

export function del<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return service.delete(url, config) as Promise<T>
}

/** 图片上传：POST /api/files/upload，字段 file，返回 { url } */
export function upload(file: File, onProgress?: (percent: number) => void): Promise<{ url: string }> {
  const form = new FormData()
  form.append('file', file)
  return service.post('/files/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
    onUploadProgress: (e) => {
      if (onProgress && e.total) {
        onProgress(Math.round((e.loaded * 100) / e.total))
      }
    },
  }) as Promise<{ url: string }>
}

/**
 * SSE 流式提问（POST + text/event-stream）：
 * 后端事件：message={delta} / done={content} / error={code,message}
 */
export async function ssePost(
  url: string,
  body: unknown,
  handlers: {
    onDelta: (delta: string) => void
    onDone: (content: string) => void
    onError: (message: string) => void
  },
  signal?: AbortSignal,
): Promise<void> {
  const token = localStorage.getItem(TOKEN_KEY)
  let response: Response
  try {
    response = await fetch(`/api${url}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: token ? `Bearer ${token}` : '',
      },
      body: JSON.stringify(body),
      signal,
    })
  } catch (e) {
    handlers.onError('网络异常，请求失败')
    return
  }
  if (!response.ok || !response.body) {
    handlers.onError('AI 服务暂时不可用')
    return
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let full = ''
  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const parts = buffer.split('\n\n')
      buffer = parts.pop() || ''
      for (const part of parts) {
        const lines = part.split('\n')
        let event = 'message'
        let data = ''
        for (const line of lines) {
          if (line.startsWith('event:')) event = line.slice(6).trim()
          else if (line.startsWith('data:')) data += line.slice(5).trim()
        }
        if (!data) continue
        try {
          const json = JSON.parse(data)
          if (event === 'message') {
            full += json.delta || ''
            handlers.onDelta(json.delta || '')
          } else if (event === 'done') {
            handlers.onDone(json.content ?? full)
          } else if (event === 'error') {
            handlers.onError(json.message || 'AI 服务异常')
          }
        } catch {
          /* 忽略无法解析的块 */
        }
      }
    }
  } catch {
    handlers.onError('流式连接中断')
  }
}

/** WebSocket：聊天定向推送 /ws/chat?token= */
export function createChatSocket(onMessage: (data: unknown) => void, onClose?: () => void): WebSocket {
  const token = localStorage.getItem(TOKEN_KEY) || ''
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  const ws = new WebSocket(`${proto}://${location.host}/ws/chat?token=${token}`)
  ws.onmessage = (event) => {
    try {
      onMessage(JSON.parse(event.data))
    } catch {
      /* ignore */
    }
  }
  ws.onclose = () => onClose?.()
  return ws
}

export default service
