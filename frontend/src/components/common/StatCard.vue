<script setup lang="ts">
import { computed, useAttrs } from 'vue'

/** 指标卡（看板 / 工作台通用） */
const props = withDefaults(
  defineProps<{
    label: string
    value: string | number
    prefix?: string
    suffix?: string
    trend?: number
    color?: 'primary' | 'success' | 'warning' | 'info' | 'danger'
    loading?: boolean
  }>(),
  { prefix: '', suffix: '', trend: 0, color: 'primary', loading: false },
)

const attrs = useAttrs()

const trendClass = computed(() => (props.trend > 0 ? 'up' : props.trend < 0 ? 'down' : ''))
const trendArrow = computed(() => (props.trend > 0 ? '↑' : props.trend < 0 ? '↓' : ''))
</script>

<template>
  <div v-bind="attrs" class="stat-card" :class="`stat-card--${color}`" v-loading="loading">
    <div class="stat-card__label">{{ label }}</div>
    <div class="stat-card__value">
      <span v-if="prefix" class="stat-card__prefix">{{ prefix }}</span>
      {{ value }}
      <span v-if="suffix" class="stat-card__suffix">{{ suffix }}</span>
    </div>
    <div v-if="trend" class="stat-card__trend" :class="trendClass">
      {{ trendArrow }} {{ Math.abs(trend) }}%
      <span class="stat-card__trend-label">较昨日</span>
    </div>
    <div v-else-if="$slots.extra" class="stat-card__extra"><slot name="extra" /></div>
  </div>
</template>

<style scoped>
.stat-card {
  position: relative;
  background: var(--color-bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 16px;
  overflow: hidden;
}

.stat-card::after {
  content: '';
  position: absolute;
  right: -18px;
  top: -18px;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: var(--stat-tint);
  opacity: 0.5;
}

.stat-card--primary { --stat-tint: var(--color-primary); }
.stat-card--success { --stat-tint: var(--color-success); }
.stat-card--warning { --stat-tint: var(--color-warning); }
.stat-card--info    { --stat-tint: var(--color-info); }
.stat-card--danger  { --stat-tint: var(--color-danger); }

.stat-card__label {
  font-size: var(--font-size-aux);
  color: var(--color-text-muted);
}

.stat-card__value {
  margin-top: 8px;
  font-size: var(--font-size-price-lg);
  font-weight: 600;
  color: var(--color-text-primary);
  line-height: 1.2;
}

.stat-card__prefix,
.stat-card__suffix {
  font-size: var(--font-size-aux);
  font-weight: 400;
  color: var(--color-text-secondary);
  margin: 0 2px;
}

.stat-card__trend {
  margin-top: 8px;
  font-size: var(--font-size-aux);
  color: var(--color-text-muted);
}
.stat-card__trend.up { color: var(--color-success); }
.stat-card__trend.down { color: var(--color-danger); }
.stat-card__trend-label { color: var(--color-text-muted); margin-left: 4px; }

.stat-card__extra {
  margin-top: 8px;
  font-size: var(--font-size-aux);
  color: var(--color-text-muted);
}
</style>
