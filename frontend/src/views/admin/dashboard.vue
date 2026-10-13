<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import 'echarts-wordcloud'
import {
  CHART_COLORS,
  TEXT,
  axisTooltip,
  currentPrimary,
  gridBase,
  itemTooltip,
  legendHidden,
  legendVertical,
  lighten,
  palette,
  primaryAreaGradient,
  shadowTooltip,
} from '@/utils/chart-theme'
import BaseChart from '@/components/common/BaseChart.vue'
import PageCard from '@/components/common/PageCard.vue'
import StatCard from '@/components/common/StatCard.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import {
  fetchOverview,
  fetchOrderTrend,
  fetchStatusDist,
  fetchItemRank,
  fetchStaffRank,
  fetchRatingDist,
  fetchWordcloud,
  type DashboardOverview,
  type TrendPoint,
  type StatusPoint,
  type RankPoint,
  type StaffRankPoint,
  type RatingPoint,
  type WordPoint,
} from '@/api/admin/dashboard'
import { ORDER_STATUS, fmtMoney } from '@/utils/constants'

/** PRD 7.3 数据看板【强制】：指标卡 + 趋势 + 状态分布 + 排行 + 评分 + 词云 */
const loading = ref(true)
const error = ref('')
const overview = ref<DashboardOverview | null>(null)
const trend = ref<TrendPoint[]>([])
const statusDist = ref<StatusPoint[]>([])
const itemRank = ref<RankPoint[]>([])
const staffRank = ref<StaffRankPoint[]>([])
const ratingDist = ref<RatingPoint[]>([])
const wordcloud = ref<WordPoint[]>([])
const days = ref(7)

/** 类目轴（横排 X） */
const AXIS_X = {
  type: 'category' as const,
  axisLine: { lineStyle: { color: CHART_COLORS.border } },
  axisTick: { show: false },
  axisLabel: { color: TEXT.regular, fontSize: 12 },
}

/** 数值轴（含虚线分割） */
const AXIS_Y = {
  type: 'value' as const,
  axisLine: { show: false },
  axisTick: { show: false },
  axisLabel: { color: TEXT.muted, fontSize: 12 },
  splitLine: { lineStyle: { color: CHART_COLORS.border, type: 'dashed' as const } },
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const [ov, tr, sd, ir, sr, rd, wc] = await Promise.all([
      fetchOverview(),
      fetchOrderTrend(days.value),
      fetchStatusDist(),
      fetchItemRank(),
      fetchStaffRank(),
      fetchRatingDist(),
      fetchWordcloud(80),
    ])
    overview.value = ov
    trend.value = tr ?? []
    statusDist.value = sd ?? []
    itemRank.value = ir ?? []
    staffRank.value = sr ?? []
    ratingDist.value = rd ?? []
    wordcloud.value = wc ?? []
  } catch (e) {
    error.value = (e as Error)?.message || '看板数据加载失败'
  } finally {
    loading.value = false
  }
}

/** 切换趋势统计窗口（7/30 天） */
async function switchDays(v: number) {
  days.value = v
  try {
    trend.value = (await fetchOrderTrend(v)) ?? []
  } catch {
    /* 拦截器已提示 */
  }
}

/* ---------- 图表配置（颜色全部来自色彩方案） ---------- */

const trendOption = computed<EChartsOption>(() => ({
  color: palette(),
  legend: legendHidden(),
  tooltip: axisTooltip(),
  grid: gridBase(),
  xAxis: { ...AXIS_X, data: trend.value.map((d) => (d.date || '').slice(5)) },
  yAxis: AXIS_Y,
  series: [
    {
      name: '订单数',
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      data: trend.value.map((d) => d.count),
      lineStyle: { width: 3, color: currentPrimary() },
      itemStyle: { color: currentPrimary() },
      areaStyle: { color: primaryAreaGradient() },
    },
  ],
}))

const statusOption = computed<EChartsOption>(() => ({
  color: palette(),
  tooltip: itemTooltip(),
  legend: legendVertical(),
  series: [
    {
      name: '订单状态',
      type: 'pie',
      radius: ['45%', '70%'],
      center: ['32%', '50%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 6, borderColor: CHART_COLORS.bgCard, borderWidth: 2 },
      label: { show: false },
      emphasis: { label: { show: true, fontSize: 14, fontWeight: 600 as const, color: TEXT.title } },
      data: statusDist.value.map((s, i) => ({
        name: ORDER_STATUS[s.status]?.label ?? s.status,
        value: s.count,
        itemStyle: { color: ORDER_STATUS[s.status]?.color ?? palette()[i % palette().length] },
      })),
    },
  ],
}))

/** 横向柱状排行（末位渐浅做视觉降权） */
function rankOption(list: Array<{ name: string; count: number }>, color: string): EChartsOption {
  return {
    color: [color],
    legend: legendHidden(),
    tooltip: shadowTooltip(),
    grid: { ...gridBase(), left: 8, right: 40, top: 16, bottom: 8 },
    xAxis: AXIS_Y,
    yAxis: { ...AXIS_X, data: list.map((i) => i.name), inverse: true },
    series: [
      {
        type: 'bar',
        barMaxWidth: 18,
        data: list.map((i, idx) => ({
          value: i.count,
          itemStyle: {
            color: idx === 0 ? color : lighten(color, 0.3 + Math.min(idx, 4) * 0.1),
            borderRadius: [0, 9, 9, 0],
          },
        })),
        label: { show: true, position: 'right' as const, color: TEXT.regular, fontSize: 12 },
      },
    ],
  }
}

const itemRankOption = computed(() => rankOption(itemRank.value, currentPrimary()))
const staffRankOption = computed(() => rankOption(staffRank.value, CHART_COLORS.info))

const ratingOption = computed<EChartsOption>(() => ({
  color: palette(),
  legend: legendHidden(),
  tooltip: shadowTooltip(),
  grid: gridBase(),
  xAxis: { ...AXIS_X, data: ratingDist.value.map((r) => `${r.rating} 星`) },
  yAxis: AXIS_Y,
  series: [
    {
      name: '评价数',
      type: 'bar',
      barMaxWidth: 32,
      data: ratingDist.value.map((r) => ({
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

/** 评价词云（HanLP 分词 → 词频 Top80），色环取自色彩方案 */
const wordOption = computed<EChartsOption>(() => {
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
        color: () => {
          const colors = palette()
          return colors[Math.floor(Math.random() * colors.length)] as string
        },
      },
      data: wordcloud.value.map((w) => ({ name: w.name, value: w.value })),
    },
  ]
  return {
    tooltip: itemTooltip(
      (p) => `${(p as { name: string })?.name ?? ''}：${(p as { value: number })?.value ?? 0} 次`,
    ),
    series: series as unknown as NonNullable<EChartsOption['series']>,
  }
})

const satisfiedRate = computed(() => Number(overview.value?.satisfiedRate ?? 0))

onMounted(loadAll)
</script>

<template>
  <div class="dashboard" v-loading="loading" element-loading-background="rgba(247,248,250,0.7)">
    <ErrorState v-if="error" :text="error" @retry="loadAll" />

    <template v-else>
      <!-- 核心指标卡 -->
      <el-row :gutter="16" class="dashboard__row">
        <el-col :xs="12" :sm="12" :md="8" :lg="3">
          <StatCard label="累计订单" :value="overview?.totalOrders ?? 0" suffix="单" color="primary" />
        </el-col>
        <el-col :xs="12" :sm="12" :md="8" :lg="3">
          <StatCard label="今日订单" :value="overview?.todayOrders ?? 0" suffix="单" color="info" />
        </el-col>
        <el-col :xs="12" :sm="12" :md="8" :lg="3">
          <StatCard label="平台收入" prefix="¥" :value="fmtMoney(overview?.totalRevenue)" color="success" />
        </el-col>
        <el-col :xs="12" :sm="12" :md="8" :lg="3">
          <StatCard label="用户数" :value="overview?.userCount ?? 0" suffix="人" color="info" />
        </el-col>
        <el-col :xs="12" :sm="12" :md="8" :lg="3">
          <StatCard label="服务人员" :value="overview?.staffCount ?? 0" suffix="人" color="warning" />
        </el-col>
        <el-col :xs="12" :sm="12" :md="8" :lg="3">
          <StatCard label="宠物数" :value="overview?.petCount ?? 0" suffix="只" color="primary" />
        </el-col>
        <el-col :xs="12" :sm="12" :md="8" :lg="3">
          <StatCard label="满意率" :value="satisfiedRate.toFixed(1)" suffix="%" color="success">
            <template #extra>评价 {{ overview?.totalReviews ?? 0 }} 条</template>
          </StatCard>
        </el-col>
      </el-row>

      <!-- 趋势 + 状态分布 -->
      <el-row :gutter="16" class="dashboard__row">
        <el-col :xs="24" :lg="16">
          <PageCard title="订单趋势">
            <template #header>
              <el-radio-group :model-value="days" size="small" @update:model-value="switchDays">
                <el-radio-button :value="7">近 7 天</el-radio-button>
                <el-radio-button :value="30">近 30 天</el-radio-button>
              </el-radio-group>
            </template>
            <BaseChart :option="trendOption" height="300px" :empty="!trend.length" empty-text="暂无趋势数据" />
          </PageCard>
        </el-col>

        <el-col :xs="24" :lg="8">
          <PageCard title="订单状态分布">
            <BaseChart :option="statusOption" height="300px" :empty="!statusDist.length" empty-text="暂无订单数据" />
          </PageCard>
        </el-col>
      </el-row>

      <!-- 排行 -->
      <el-row :gutter="16" class="dashboard__row">
        <el-col :xs="24" :md="12">
          <PageCard title="服务项目排行 TOP10">
            <BaseChart :option="itemRankOption" height="300px" :empty="!itemRank.length" empty-text="暂无已完成订单" />
          </PageCard>
        </el-col>
        <el-col :xs="24" :md="12">
          <PageCard title="服务人员接单排行 TOP10">
            <BaseChart :option="staffRankOption" height="300px" :empty="!staffRank.length" empty-text="暂无已完成订单" />
          </PageCard>
        </el-col>
      </el-row>

      <!-- 评分 + 词云 -->
      <el-row :gutter="16" class="dashboard__row">
        <el-col :xs="24" :md="10">
          <PageCard title="评分分布与满意率">
            <div class="dashboard__gauge">
              <span class="dashboard__gauge-value">{{ satisfiedRate.toFixed(1) }}%</span>
              <span class="dashboard__gauge-label">满意率（4 星及以上占比）</span>
            </div>
            <BaseChart :option="ratingOption" height="240px" :empty="!ratingDist.length" empty-text="暂无评价数据" />
          </PageCard>
        </el-col>
        <el-col :xs="24" :md="14">
          <PageCard title="评价词云">
            <template #header>
              <span class="aux-text">HanLP 分词 · 词频 Top80</span>
            </template>
            <BaseChart :option="wordOption" height="320px" :empty="!wordcloud.length" empty-text="暂无评价文本" />
          </PageCard>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<style scoped>
.dashboard__row {
  margin-bottom: 16px;
}

.dashboard__gauge {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 4px;
}

.dashboard__gauge-value {
  font-size: 32px;
  font-weight: 600;
  color: var(--color-success);
  line-height: 1.2;
}

.dashboard__gauge-label {
  font-size: var(--font-size-aux);
  color: var(--color-text-muted);
}
</style>
