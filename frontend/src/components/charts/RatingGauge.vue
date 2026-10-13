<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsOption } from 'echarts'
import BaseChart from '@/components/common/BaseChart.vue'
import { useChartTheme } from '@/composables/useChartTheme'
import { CHART_COLORS, TEXT, itemTooltip } from '@/utils/chart-theme'

/**
 * 环形仪表盘（PRD 7.3 满意率 = 4 星及以上占比）
 * 阈值配色直接取方案状态色：≥90 成功绿 / ≥75 当端主色 / ≥60 警告橙 / <60 错误红。
 */
const props = withDefaults(
  defineProps<{
    /** 0 ~ 100 */
    value: number
    height?: string
    loading?: boolean
    label?: string
    /** 分档阈值，默认 [60, 75, 90] */
    thresholds?: [number, number, number]
  }>(),
  { height: '240px', loading: false, label: '满意率', thresholds: () => [60, 75, 90] },
)

const { primary } = useChartTheme()

const level = computed(() => {
  const v = Number(props.value) || 0
  const [low, mid, high] = props.thresholds
  if (v >= high) return CHART_COLORS.success
  if (v >= mid) return primary.value
  if (v >= low) return CHART_COLORS.warning
  return CHART_COLORS.danger
})

const option = computed<EChartsOption>(() => {
  const color = level.value
  return {
    series: [
      {
        type: 'gauge',
        startAngle: 90,
        endAngle: -270,
        radius: '88%',
        pointer: { show: false },
        progress: {
          show: true,
          overlap: false,
          roundCap: true,
          width: 14,
          itemStyle: { color, borderColor: 'transparent' },
        },
        axisLine: { lineStyle: { width: 14, color: [[1, CHART_COLORS.border]] } },
        splitLine: { show: false },
        axisTick: { show: false },
        axisLabel: { show: false },
        data: [
          {
            value: Number(props.value) || 0,
            name: props.label,
            title: { color: TEXT.muted, fontSize: 14, offsetCenter: [0, '30%'] },
            detail: {
              valueAnimation: true,
              fontSize: 30,
              fontWeight: 600 as const,
              color: TEXT.title,
              offsetCenter: [0, '-8%'],
              formatter: (v: number) => `${v.toFixed(1)}%`,
            },
          },
        ],
      },
    ],
    tooltip: itemTooltip(),
  }
})
</script>

<template>
  <BaseChart :option="option" :height="height" :loading="loading" />
</template>

<style scoped>
.gauge-label {
  font-size: var(--font-size-aux);
  color: var(--color-text-muted);
}
</style>
