<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsOption } from 'echarts'
import BaseChart from '@/components/common/BaseChart.vue'
import { useChartTheme } from '@/composables/useChartTheme'
import {
  AXIS_CATEGORY,
  AXIS_VALUE,
  TEXT,
  axisTooltip,
  gridBase,
  legendHidden,
  primaryAreaGradient,
} from '@/utils/chart-theme'

/** 趋势点（对接 GET /admin/dashboard/order-trend） */
export interface TrendPoint {
  date: string
  count: number
}

/**
 * 折线趋势图（PRD 7.3 订单趋势）
 * 面积渐变填充来自当端主色 → 透明，跟随三端主题。
 */
const props = withDefaults(
  defineProps<{
    data: TrendPoint[]
    height?: string
    loading?: boolean
    unit?: string
    smooth?: boolean
    showArea?: boolean
    emptyText?: string
  }>(),
  { height: '300px', loading: false, unit: '单', smooth: true, showArea: true, emptyText: '暂无趋势数据' },
)

const { primary, colors } = useChartTheme()

const option = computed<EChartsOption>(() => {
  void colors.value
  return {
    color: colors.value,
    legend: legendHidden(),
    tooltip: axisTooltip(),
    grid: gridBase(),
    xAxis: { ...AXIS_CATEGORY, data: props.data.map((d) => (d.date || '').slice(5)) },
    yAxis: AXIS_VALUE,
    series: [
      {
        name: '订单数',
        type: 'line',
        smooth: props.smooth,
        symbol: 'circle',
        symbolSize: 6,
        showSymbol: props.data.length <= 15,
        data: props.data.map((d) => d.count),
        lineStyle: { width: 3, color: primary.value },
        itemStyle: { color: primary.value },
        areaStyle: props.showArea ? { color: primaryAreaGradient(primary.value) } : undefined,
      },
    ],
  }
})
</script>

<template>
  <BaseChart :option="option" :height="height" :loading="loading" :empty="!data.length" :empty-text="emptyText" />
</template>

<style scoped>
:deep(.el-empty__description p) {
  font-size: var(--font-size-aux);
  color: var(--color-text-muted);
}
</style>
