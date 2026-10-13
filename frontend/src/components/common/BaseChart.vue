<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import type { EChartsOption } from 'echarts'
import { echarts } from '@/utils/chart-theme'
import { useAppStore } from '@/stores/app'
import EmptyState from './EmptyState.vue'

type ChartInstance = ReturnType<typeof echarts.init>

/**
 * ECharts 基座：
 *  - init / dispose 生命周期管理
 *  - ResizeObserver 响应式缩放
 *  - 监听 app.theme，三端主题切换时重建实例以换主色
 *  - loading / empty 状态覆盖
 */
const props = withDefaults(
  defineProps<{
    option: EChartsOption
    height?: string
    loading?: boolean
    empty?: boolean
    emptyText?: string
  }>(),
  { height: '320px', loading: false, empty: false, emptyText: '暂无数据' },
)

const emit = defineEmits<{ 'chart-ready': [chart: ChartInstance] }>()

const el = ref<HTMLDivElement>()
const app = useAppStore()
let chart: ChartInstance | null = null
let observer: ResizeObserver | null = null

function init() {
  if (!el.value || props.empty) return
  chart?.dispose()
  chart = echarts.init(el.value, 'pet')
  chart.setOption(props.option, true)
  if (props.loading) {
    chart.showLoading({
      text: '加载中…',
      color: '#5B7FFF',
      textColor: '#666666',
      maskColor: 'rgba(247,248,250,0.8)',
    })
  }
  emit('chart-ready', chart)
}

watch(
  () => props.option,
  (opt) => {
    if (chart && !props.empty) chart.setOption(opt, true)
  },
  { deep: true },
)

// 数据加载前 empty=true 时画布（v-else 分支）尚未渲染，init 会跳过；
// 数据到达后 empty 变 false，画布挂载后需在此重新 init，否则图表永远空白
watch(
  () => props.empty,
  async (isEmpty) => {
    if (isEmpty) {
      chart?.dispose()
      chart = null
      return
    }
    await nextTick()
    init()
  },
)

watch(
  () => props.loading,
  (isLoading) => {
    if (!chart) return
    if (isLoading) {
      chart.showLoading({
        text: '加载中…',
        color: '#5B7FFF',
        textColor: '#666666',
        maskColor: 'rgba(247,248,250,0.8)',
      })
    } else {
      chart.hideLoading()
    }
  },
)

// 主题切换（portal/staff/admin）时重建图表，保证主色跟随
watch(() => app.theme, () => void nextTick(init))

onMounted(() => {
  init()
  if (el.value) {
    observer = new ResizeObserver(() => chart?.resize())
    observer.observe(el.value)
  }
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
  chart?.dispose()
  chart = null
})

defineExpose({
  resize: () => chart?.resize(),
  getInstance: () => chart,
})
</script>

<template>
  <div class="base-chart" :style="{ height }">
    <EmptyState v-if="empty" :text="emptyText" />
    <div v-else ref="el" class="base-chart__canvas" />
  </div>
</template>

<style scoped>
.base-chart {
  position: relative;
  width: 100%;
}

.base-chart__canvas {
  width: 100%;
  height: 100%;
}
</style>
