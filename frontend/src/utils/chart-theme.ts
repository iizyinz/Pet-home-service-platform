/**
 * ECharts 主题与配置工厂 —— 严格派生自《色彩搭配方案.md》
 *
 * 派生规则：
 *  1. 基础色环 = [当端主色, 成功绿, 警告橙, 危险红, 信任蓝]（自动去重）
 *     · 用户端主色 #FF8A3D、服务人员端 #4ECB71、管理后台 #5B7FFF
 *     · 管理后台主色与强调色同为 #5B7FFF，去重后降为 5 色，避免同屏重复色
 *  2. 同一系列内的多档差异 = mix(基础色, #FFFFFF, N%)，即 light-3/5/7
 *  3. 轴线/分割线统一用方案分割线 #EBEDF0，文字用 #666666 / #999999
 *  4. 不引入任何方案之外的新色相
 */
import * as echarts from 'echarts'

/** 方案中的固定色（与 tokens.css 一一对应，勿单独改动） */
export const CHART_COLORS = {
  primary: '#FF8A3D',
  success: '#4ECB71',
  warning: '#FFB020',
  danger: '#FF4D4F',
  info: '#5B7FFF',
  accent: '#5B7FFF',
  textPrimary: '#1A1A1A',
  textSecondary: '#666666',
  textMuted: '#999999',
  border: '#EBEDF0',
  bgPage: '#F7F8FA',
  bgCard: '#FFFFFF',
} as const

/** 三端主色（色彩方案「三端差异化搭配」） */
const THEME_PRIMARY: Record<string, string> = {
  portal: '#FF8A3D',
  staff: '#4ECB71',
  admin: '#5B7FFF',
}

/** 当前端主题（由路由守卫写入 html 类名） */
export function currentTheme(): 'portal' | 'staff' | 'admin' {
  const cls = (typeof document !== 'undefined' && document.documentElement.className) || ''
  if (cls.includes('theme-staff')) return 'staff'
  if (cls.includes('theme-admin')) return 'admin'
  return 'portal'
}

/** 当前端主色：优先读运行时 CSS 变量，读不到再回落到静态映射 */
export function currentPrimary(): string {
  if (typeof document === 'undefined') return CHART_COLORS.primary
  const v = getComputedStyle(document.documentElement).getPropertyValue('--el-color-primary').trim()
  return v || THEME_PRIMARY[currentTheme()] || CHART_COLORS.primary
}

/* ============ 色阶生成：mix(base, target, weight) ============ */

function toRgb(hex: string): [number, number, number] {
  const s = hex.replace('#', '').trim()
  const full = s.length === 3 ? s.split('').map((c) => c + c).join('') : s
  const n = parseInt(full, 16)
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255]
}

function toHex(r: number, g: number, b: number): string {
  const h = (v: number) => Math.round(v).toString(16).padStart(2, '0')
  return `#${h(r)}${h(g)}${h(b)}`.toUpperCase()
}

/** 混合：result = a * (1 - weight) + b * weight */
export function mix(a: string, b: string, weight: number): string {
  const [r1, g1, b1] = toRgb(a)
  const [r2, g2, b2] = toRgb(b)
  const w = Math.min(1, Math.max(0, weight))
  return toHex(r1 * (1 - w) + r2 * w, g1 * (1 - w) + g2 * w, b1 * (1 - w) + b2 * w)
}

/** 向白色靠拢：light-3 = mix(base, #FFF, 30%) */
export const lighten = (hex: string, weight: number) => mix(hex, '#FFFFFF', weight)
/** 向黑色靠拢：dark-2 = mix(base, #000, 20%) */
export const darken = (hex: string, weight: number) => mix(hex, '#000000', weight)

/** 主色环（自动去重，管理后台主色=强调色时降为 5 色） */
export function palette(): string[] {
  const p = currentPrimary()
  const base = [p, CHART_COLORS.success, CHART_COLORS.warning, CHART_COLORS.danger, CHART_COLORS.info, CHART_COLORS.primary]
  return base.filter((c, i) => base.indexOf(c) === i)
}

/** 主色的深浅档，用于同系列多序列区分 */
export function primaryRamp(): string[] {
  const p = currentPrimary()
  return [p, lighten(p, 0.3), lighten(p, 0.5), lighten(p, 0.7), darken(p, 0.2)]
}

/** 文字色阶（方案三级文字） */
export const TEXT = {
  title: CHART_COLORS.textPrimary,
  regular: CHART_COLORS.textSecondary,
  muted: CHART_COLORS.textMuted,
} as const

/* ============ 通用轴样式常量（as const 保证字面量类型可直接赋给 ECharts 配置） ============ */

export const AXIS_LINE = { lineStyle: { color: CHART_COLORS.border } } as const

export const AXIS_LABEL = { color: TEXT.regular, fontSize: 12 } as const

export const AXIS_TICK = { show: false } as const

export const SPLIT_LINE = { lineStyle: { color: CHART_COLORS.border, type: 'dashed' as const } } as const

/** 数值轴名（含虚线分割） */
export const VALUE_AXIS_NAME = { color: TEXT.muted, fontSize: 12 } as const

/* ============ 通用配置工厂 ============ */

/** 全局 tooltip / legend / grid / textStyle 基线 */
export function baseOption(): echarts.EChartsOption {
  return {
    color: palette(),
    textStyle: { color: TEXT.regular, fontSize: 13, fontFamily: 'inherit' },
    legend: {
      bottom: 0,
      itemWidth: 12,
      itemHeight: 12,
      icon: 'circle',
      textStyle: { color: TEXT.regular, fontSize: 13 },
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: CHART_COLORS.bgCard,
      borderColor: CHART_COLORS.border,
      borderWidth: 1,
      padding: [10, 14],
      textStyle: { color: TEXT.title, fontSize: 13 },
      extraCssText: 'box-shadow: 0 4px 12px rgba(0,0,0,0.06); border-radius: 12px;',
    },
    grid: { left: 8, right: 16, top: 32, bottom: 8, containLabel: true },
  }
}

/** 面积渐变（主色 → 透明，用于趋势图填充） */
export function primaryAreaGradient(color = currentPrimary()) {
  return {
    type: 'linear' as const,
    x: 0,
    y: 0,
    x2: 0,
    y2: 1,
    colorStops: [
      { offset: 0, color: lighten(color, 0.55) },
      { offset: 1, color: 'rgba(255,255,255,0)' },
    ],
  }
}

/** 注册 ECharts 主题（main.ts 启动时调用一次） */
export function registerChartTheme(): void {
  echarts.registerTheme('pet', {
    color: palette(),
    textStyle: { color: TEXT.regular, fontFamily: 'inherit' },
    legend: { textStyle: { color: TEXT.regular } },
    tooltip: {
      backgroundColor: CHART_COLORS.bgCard,
      borderColor: CHART_COLORS.border,
      textStyle: { color: TEXT.title },
    },
  })
}

/* ============ 分片配置工厂（返回值标注为对应槽位类型，由 ECharts 做上下文校验） ============ */

type LegendSlot = NonNullable<echarts.EChartsOption['legend']>
type TooltipSlot = NonNullable<echarts.EChartsOption['tooltip']>
type GridSlot = NonNullable<echarts.EChartsOption['grid']>

/** 隐藏图例 */
export function legendHidden(): LegendSlot {
  return { show: false }
}

/** 底部横排图例（默认） */
export function legendBottom(): LegendSlot {
  return {
    bottom: 0,
    itemWidth: 12,
    itemHeight: 12,
    icon: 'circle',
    textStyle: { color: TEXT.regular, fontSize: 13 },
  }
}

/** 右侧竖排图例（饼图） */
export function legendVertical(): LegendSlot {
  return {
    orient: 'vertical',
    right: 0,
    top: 'center',
    itemWidth: 12,
    itemHeight: 12,
    icon: 'circle',
    textStyle: { color: TEXT.regular, fontSize: 13 },
  }
}

/** 轴触发 tooltip（折线/柱状） */
function tooltipBase() {
  return {
    backgroundColor: CHART_COLORS.bgCard,
    borderColor: CHART_COLORS.border,
    borderWidth: 1,
    padding: [10, 14] as number[],
    textStyle: { color: TEXT.title, fontSize: 13 },
    extraCssText: 'box-shadow: 0 4px 12px rgba(0,0,0,0.06); border-radius: 12px;',
  }
}

export function axisTooltip(): TooltipSlot {
  return { ...tooltipBase(), trigger: 'axis' as const }
}

/** 轴触发 + 矩形指示器（横向柱状排行） */
export function shadowTooltip(): TooltipSlot {
  return { ...tooltipBase(), trigger: 'axis' as const, axisPointer: { type: 'shadow' as const } }
}

/** 单项触发 tooltip（饼图/词云） */
export function itemTooltip(formatter?: (params: unknown) => string): TooltipSlot {
  return formatter
    ? { ...tooltipBase(), trigger: 'item' as const, formatter }
    : { ...tooltipBase(), trigger: 'item' as const }
}

/** 统一网格 */
export function gridBase(): GridSlot {
  return { left: 8, right: 16, top: 32, bottom: 8, containLabel: true }
}

/* ============ 轴线样式常量（as const 保证字面量类型，可直接展开进 option） ============ */

/** 类目轴：方案分割线 + 次级文字，隐藏刻度 */
export const AXIS_CATEGORY = {
  type: 'category' as const,
  axisLine: { lineStyle: { color: CHART_COLORS.border } },
  axisTick: { show: false },
  axisLabel: { color: TEXT.regular, fontSize: 12 },
}

/** 数值轴：隐藏轴线，虚线分割 */
export const AXIS_VALUE = {
  type: 'value' as const,
  axisLine: { show: false },
  axisTick: { show: false },
  axisLabel: { color: TEXT.muted, fontSize: 12 },
  splitLine: { lineStyle: { color: CHART_COLORS.border, type: 'dashed' as const } },
}

/** 颜色环 */
export const chartColor = (): string[] => palette()

export { echarts }
