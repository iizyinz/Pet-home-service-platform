<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsOption } from 'echarts'
import BaseChart from '@/components/common/BaseChart.vue'
import { useChartTheme } from '@/composables/useChartTheme'
import { AXIS_CATEGORY, AXIS_VALUE, TEXT, gridBase, legendHidden, lighten, shadowTooltip } from '@/utils/chart-theme'

export interface RankDatum {
  name: string
  count: number
}

/**
 * 横向排行柱状图（PRD 7.3 服务项目排行 / 服务人员接单排行）
 * 第 1 名用当端主色，其后按 mix(主色, 白, 30%~70%) 逐级减淡，做视觉降权；
 * 降序由组件内保证（后端已排序，这里兜底，避免「倒着读」的阅读负担）。
 */
const props = withDefaults(
  defineProps<{
    data: RankDatum[]
    height?: string
    loading?: boolean
    color?: string
    topN?: number
    unit?: string
    emptyText?: string
  }>(),
  { height: '300px', loading: false, topN: 10, unit: '单', emptyText: '暂无排行数据' },
)

const { primary } = useChartTheme()
const barColor = computed(() => props.color || primary.value)

const sorted = computed(() => [...props.data].sort((a, b) => b.count - a.count).slice(0, props.topN))

const option = computed<EChartsOption>(() => {
  const base = barColor.value
  return {
    color: [base],
    legend: legendHidden(),
    tooltip: shadowTooltip(),
    grid: { ...gridBase(), left: 8, right: 44, top: 16, bottom: 8 },
    xAxis: AXIS_VALUE,
    yAxis: { ...AXIS_CATEGORY, data: sorted.value.map((i) => i.name), inverse: true },
    series: [
      {
        type: 'bar',
        barMaxWidth: 18,
        data: sorted.value.map((item, idx) => ({
          value: item.count,
          itemStyle: {
            color: idx === 0 ? base : lighten(base, 0.3 + Math.min(idx, 4) * 0.1),
            borderRadius: [0, 9, 9, 0],
          },
        })),
        label: {
          show: true,
          position: 'right' as const,
          color: TEXT.regular,
          fontSize: 12,
          formatter: `{c} ${props.unit}`,
        },
      },
    ],
  }
})
</script>

<template>
  <BaseChart :option="option" :height="height" :loading="loading" :empty="!sorted.length" :empty-text="emptyText" />
</template>
