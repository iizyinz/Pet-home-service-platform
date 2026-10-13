<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsOption } from 'echarts'
import BaseChart from '@/components/common/BaseChart.vue'
import { useChartTheme } from '@/composables/useChartTheme'
import {
  AXIS_CATEGORY,
  AXIS_VALUE,
  CHART_COLORS,
  TEXT,
  gridBase,
  legendHidden,
  shadowTooltip,
} from '@/utils/chart-theme'

export interface RatingDatum {
  rating: number
  count: number
  percent?: number
}

/**
 * 评分分布柱状图（PRD 7.3 / 7.6）
 * 4-5 星用方案成功绿（满意），1-3 星用方案警告橙（待改善），语义不靠猜。
 */
const props = withDefaults(
  defineProps<{
    data: RatingDatum[]
    height?: string
    loading?: boolean
    emptyText?: string
  }>(),
  { height: '240px', loading: false, emptyText: '暂无评价数据' },
)

const { colors } = useChartTheme()

const sorted = computed(() => [...props.data].sort((a, b) => a.rating - b.rating))

const option = computed<EChartsOption>(() => ({
  color: colors.value,
  legend: legendHidden(),
  tooltip: shadowTooltip(),
  grid: gridBase(),
  xAxis: { ...AXIS_CATEGORY, data: sorted.value.map((r) => `${r.rating} 星`) },
  yAxis: AXIS_VALUE,
  series: [
    {
      name: '评价数',
      type: 'bar',
      barMaxWidth: 32,
      data: sorted.value.map((r) => ({
        value: r.count,
        itemStyle: {
          color: r.rating >= 4 ? CHART_COLORS.success : CHART_COLORS.warning,
          borderRadius: [8, 8, 0, 0],
        },
      })),
      label: { show: true, position: 'top' as const, color: TEXT.regular, fontSize: 12 },
    },
  ],
}))
</script>

<template>
  <BaseChart :option="option" :height="height" :loading="loading" :empty="!sorted.length" :empty-text="emptyText" />
</template>
