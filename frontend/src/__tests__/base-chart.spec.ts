import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import type { EChartsOption } from 'echarts'
import BaseChart from '@/components/common/BaseChart.vue'

/**
 * 用假实例替代 echarts.init（jsdom 无 canvas，且便于断言调用时机）。
 * vi.hoisted 保证在 vi.mock / 模块导入之前创建，避免 TDZ 报错。
 */
const { initMock, setOption, showLoading, hideLoading, resize, dispose } = vi.hoisted(() => {
  const setOption = vi.fn()
  const showLoading = vi.fn()
  const hideLoading = vi.fn()
  const resize = vi.fn()
  const dispose = vi.fn()
  const initMock = vi.fn((_el: HTMLElement, _theme?: string) => ({
    setOption,
    showLoading,
    hideLoading,
    resize,
    dispose,
  }))
  return { initMock, setOption, showLoading, hideLoading, resize, dispose }
})

vi.mock('@/utils/chart-theme', () => ({
  echarts: { init: initMock },
}))

// jsdom 未实现 ResizeObserver
class ObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
globalThis.ResizeObserver = ObserverStub as unknown as typeof ResizeObserver

function mountChart(props: { option: EChartsOption; empty: boolean; loading?: boolean }) {
  return mount(BaseChart, {
    props,
    global: { plugins: [ElementPlus, createPinia()] },
  })
}

describe('BaseChart 空态切换（数据看板空白回归）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('初始 empty=true（看板加载中）时只显示空态，不初始化图表', async () => {
    const wrapper = mountChart({ option: { series: [] } as EChartsOption, empty: true })
    expect(wrapper.find('.empty-state').exists()).toBe(true)
    expect(wrapper.find('.base-chart__canvas').exists()).toBe(false)
    expect(initMock).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('数据到达 empty 变为 false 后，画布挂载并完成初始化与 setOption', async () => {
    const wrapper: VueWrapper = mountChart({ option: { series: [] } as EChartsOption, empty: true })

    const option = { series: [{ type: 'bar', data: [1, 2, 3] }] } as EChartsOption
    await wrapper.setProps({ empty: false, option })
    await flushPromises()

    expect(wrapper.find('.base-chart__canvas').exists()).toBe(true)
    expect(initMock).toHaveBeenCalledTimes(1)
    expect(initMock.mock.calls[0]?.[0]).toBeInstanceOf(HTMLElement)
    expect(setOption).toHaveBeenCalledTimes(1)
    expect(setOption).toHaveBeenCalledWith(option, true)
    expect(wrapper.emitted('chart-ready')).toHaveLength(1)
    wrapper.unmount()
  })

  it('初始化后数据继续更新能正常 setOption 刷新', async () => {
    const wrapper = mountChart({ option: { series: [] } as EChartsOption, empty: false })
    expect(initMock).toHaveBeenCalledTimes(1)

    const next = { series: [{ type: 'line', data: [4, 5] }] } as EChartsOption
    await wrapper.setProps({ option: next })
    await flushPromises()
    expect(initMock).toHaveBeenCalledTimes(1) // 不重复 init
    expect(setOption).toHaveBeenLastCalledWith(next, true)
    wrapper.unmount()
  })

  it('再次变回 empty=true 时销毁实例，避免持有已移除的画布', async () => {
    const wrapper = mountChart({ option: { series: [] } as EChartsOption, empty: false })
    expect(initMock).toHaveBeenCalledTimes(1)

    await wrapper.setProps({ empty: true })
    await flushPromises()
    expect(dispose).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.base-chart__canvas').exists()).toBe(false)

    // 数据恢复后可再次初始化
    await wrapper.setProps({ empty: false })
    await flushPromises()
    expect(initMock).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('初始化时处于 loading 状态会立即显示加载动画', async () => {
    const wrapper = mountChart({
      option: { series: [] } as EChartsOption,
      empty: false,
      loading: true,
    })
    expect(showLoading).toHaveBeenCalledTimes(1)

    await wrapper.setProps({ loading: false })
    expect(hideLoading).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })
})
