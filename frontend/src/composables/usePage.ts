import { reactive, ref, onMounted, type Ref } from 'vue'
import type { PageResult } from '@/utils/request'

/** 分页列表入参（后端约定 page 从 1 开始） */
export interface PageQuery {
  page?: number
  size?: number
}

export interface UsePageOptions<Q extends object> {
  /** 默认筛选条件，重置时回填 */
  defaultQuery?: Q
  /** 每页条数，默认 10 */
  size?: number
  /** 是否挂载即加载，默认 true */
  immediate?: boolean
}

/**
 * 分页列表通用状态机：
 * 统一管理 loading / list / total / 分页 / 筛选 / 错误态，
 * 保证所有列表页具备「加载态 + 空态 + 错误态 + 分页 + 筛选重置」。
 */
export function usePage<T, Q extends object = Record<string, unknown>>(
  fetcher: (params: Q & PageQuery) => Promise<PageResult<T>>,
  options: UsePageOptions<Q> = {},
) {
  const list = ref<T[]>([]) as Ref<T[]>
  const total = ref(0)
  const page = ref(1)
  const size = ref(options.size ?? 10)
  const loading = ref(false)
  const error = ref('')
  const query = reactive({ ...(options.defaultQuery ?? {}) } as Q)

  async function load(): Promise<void> {
    loading.value = true
    error.value = ''
    try {
      const res = await fetcher({ ...(query as object), page: page.value, size: size.value } as Q & PageQuery)
      list.value = res?.list ?? []
      total.value = res?.total ?? 0
    } catch (e) {
      // 拦截器已统一 toast，这里只负责把错误态渲染到表格区并提供重试
      error.value = (e as Error)?.message || '加载失败，请稍后重试'
      list.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  /** 查询（重置到第 1 页） */
  function search(): Promise<void> {
    page.value = 1
    return load()
  }

  /** 原页刷新（翻页、状态变更后保持上下文） */
  function refresh(): Promise<void> {
    return load()
  }

  /** 重置筛选条件后查询 */
  function reset(): Promise<void> {
    const defaults = (options.defaultQuery ?? {}) as Record<string, unknown>
    for (const key of Object.keys(query as object)) {
      ;(query as Record<string, unknown>)[key] = key in defaults ? defaults[key] : ''
    }
    return search()
  }

  function changePage(p: number): Promise<void> {
    page.value = p
    return load()
  }

  function changeSize(s: number): Promise<void> {
    size.value = s
    page.value = 1
    return load()
  }

  if (options.immediate !== false) {
    onMounted(() => void load())
  }

  return { list, total, page, size, loading, error, query, load, search, refresh, reset, changePage, changeSize }
}
