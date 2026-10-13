<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsOption } from 'echarts'
import BaseChart from '@/components/common/BaseChart.vue'
import { useChartTheme } from '@/composables/useChartTheme'
import { CHART_COLORS, TEXT, itemTooltip, legendVertical } from '@/utils/chart-theme'

export interface PieDatum {
  name: string
  value: number
  /** 指定色（如订单状态语义色），不传则按色环顺序取 */
  color?: string
}

/**
 * 环形饼图（PRD 7.3 订单状态分布）
 * 颜色优先用调用方给的语义色（如 COMPLETED=成功绿、CANCELLED=错误红），
 * 缺省时回落到当端色环。
 */
const props = withDefaults(
  defineProps<{
    data: PieDatum[]
    height?: string
    loading?: boolean
    legend?: 'vertical' | 'none'
    radius?: [string, string]
    center?: [string, string]
    emptyText?: string
    unit?: string
  }>(),
  {
    height: '300px',
    loading: false,
    legend: 'vertical',
    radius: () => ['45%', '70%'],
    center: () => ['32%', '50%'],
    emptyText: '暂无数据',
    unit: '个',
  },
)

const { colors } = useChartTheme()

const option = computed<EChartsOption>(() => {
  const colorRing = colors.value
  return {
    color: colorRing,
    tooltip: itemTooltip(
      (p) => {
        const item = p as { name: string; value: number; percent: number }
        return `${item?.name ?? ''}：${item?.value ?? 0} ${props.unit}（${item?.percent ?? 0}%）`
      },
    ),
    legend: props.legend === 'none' ? { show: false } : legendVertical(),
    series: [
      {
        name: '占比',
        type: 'pie',
        radius: props.radius,
        center: props.center,
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: CHART_COLORS.bgCard, borderWidth: 2 },
        label: { show: false },
        emphasis: {
          label: { show: true, fontSize: 14, fontWeight: 600 as const, color: TEXT.title },
        },
        data: props.data.map((d, i) => ({
          name: d.name,
          value: d.value,
          itemStyle: { color: d.color ?? colorRing[i % colorRing.length] },
        })),
      },
    ],
  }
})
</script>

<template>
  <BaseChart :option="option" :height="height" :loading="loading" :empty="!data.length" :empty-text="emptyText" />
</template>
