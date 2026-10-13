<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsOption } from 'echarts'
import 'echarts-wordcloud'
import BaseChart from '@/components/common/BaseChart.vue'
import { useChartTheme } from '@/composables/useChartTheme'
import { itemTooltip } from '@/utils/chart-theme'

export interface WordDatum {
  name: string
  value: number
}

/**
 * 评价词云（PRD 7.3：HanLP 分词去停用词 + 词频 Top80）
 * 颜色在当端色环内取，不引入新色相。
 */
const props = withDefaults(
  defineProps<{
    data: WordDatum[]
    height?: string
    loading?: boolean
    topN?: number
    emptyText?: string
  }>(),
  { height: '320px', loading: false, topN: 80, emptyText: '暂无评价文本' },
)

const { colors } = useChartTheme()

const trimmed = computed(() =>
  [...props.data].sort((a, b) => b.value - a.value).slice(0, props.topN),
)

const option = computed<EChartsOption>(() => {
  const ring = colors.value
  // echarts-wordcloud 未提供类型声明，整体断言为 ECharts series 槽位类型
  const series = [
    {
      type: 'wordCloud',
      shape: 'circle',
      left: 'center',
      top: 'center',
      width: '100%',
      height: '100%',
      sizeRange: [12, 46],
      rotationRange: [0, 0],
      gridSize: 8,
      textStyle: {
        fontFamily: 'inherit',
        color: () => ring[Math.floor(Math.random() * ring.length)] as string,
      },
      data: trimmed.value.map((w) => ({ name: w.name, value: w.value })),
    },
  ] as unknown as NonNullable<EChartsOption['series']>

  return {
    tooltip: itemTooltip((p) => `${(p as { name: string })?.name ?? ''}：${(p as { value: number })?.value ?? 0} 次`),
    series,
  }
})
</script>

<template>
  <BaseChart :option="option" :height="height" :loading="loading" :empty="!trimmed.length" :empty-text="emptyText" />
</template>
