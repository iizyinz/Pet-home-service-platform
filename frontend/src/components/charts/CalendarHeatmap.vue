<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsOption } from 'echarts'
import BaseChart from '@/components/common/BaseChart.vue'
import { useChartTheme } from '@/composables/useChartTheme'
import { CHART_COLORS, TEXT, itemTooltip, lighten } from '@/utils/chart-theme'

export interface HeatDatum {
  /** YYYY-MM-DD */
  date: string
  value: number
}

/**
 * 日历热力图（服务排期 / 接单密度，PRD 6.4 服务排期页）
 * 色阶由方案辅色成功绿按 mix(绿, 白, 90%~0%) 派生 5 档，未引入新色相。
 */
const props = withDefaults(
  defineProps<{
    data: HeatDatum[]
    /** 日历区间：单年 'YYYY' 或 [起, 止] */
    range: string | [string, string]
    height?: string
    loading?: boolean
    unit?: string
    emptyText?: string
  }>(),
  { height: '260px', loading: false, unit: '单', emptyText: '该区间暂无排班记录' },
)

const { colors } = useChartTheme()

const max = computed(() => Math.max(1, ...props.data.map((d) => d.value)))

const option = computed<EChartsOption>(() => ({
  tooltip: itemTooltip(
    (p) => {
      const item = p as { data?: [string, number] }
      const [date, val] = item?.data ?? ['', 0]
      return `${date}：${val} ${props.unit}`
    },
  ),
  visualMap: {
    show: false,
    min: 0,
    max: max.value,
    inRange: {
      color: [
        lighten(CHART_COLORS.success, 0.9),
        lighten(CHART_COLORS.success, 0.7),
        lighten(CHART_COLORS.success, 0.5),
        lighten(CHART_COLORS.success, 0.3),
        CHART_COLORS.success,
      ],
    },
  },
  calendar: {
    range: props.range,
    cellSize: ['auto', 16],
    left: 44,
    right: 16,
    top: 24,
    bottom: 12,
    orient: 'horizontal',
    splitLine: { lineStyle: { color: CHART_COLORS.border } },
    itemStyle: { color: CHART_COLORS.bgPage, borderWidth: 2, borderColor: CHART_COLORS.bgCard },
    dayLabel: { color: TEXT.muted, nameMap: 'ZH', firstDay: 1, fontSize: 11 },
    monthLabel: { color: TEXT.regular, nameMap: 'ZH', fontSize: 11 },
    yearLabel: { show: false },
  },
  series: [
    {
      type: 'heatmap',
      coordinateSystem: 'calendar',
      data: props.data.map((d) => [d.date, d.value]),
      itemStyle: { borderRadius: 3 },
    },
  ],
}))
</script>

<template>
  <BaseChart :option="option" :height="height" :loading="loading" :empty="!data.length" :empty-text="emptyText" />
</template>
