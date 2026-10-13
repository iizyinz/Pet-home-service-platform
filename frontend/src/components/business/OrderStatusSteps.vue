<script setup lang="ts">
import { computed } from 'vue'
import { ORDER_STATUS } from '@/utils/constants'
import type { OrderStatus } from '@/types'

/**
 * 订单状态时间轴（PRD 第四章状态机可视化）
 * - 正常链路：待确认 → 已确认 → 服务中 → 待确认完成 → 已完成
 * - 已取消：单独归集，展示取消原因
 */
const props = withDefaults(
  defineProps<{
    status: OrderStatus
    timeline?: Array<{ label: string; time?: string; desc?: string }>
    vertical?: boolean
  }>(),
  { timeline: () => [], vertical: false },
)

const STEPS: Array<{ key: OrderStatus; label: string }> = [
  { key: 'PENDING_CONFIRM', label: '待确认' },
  { key: 'CONFIRMED', label: '已确认' },
  { key: 'SERVING', label: '服务中' },
  { key: 'PENDING_SETTLE', label: '待确认完成' },
  { key: 'COMPLETED', label: '已完成' },
]

const cancelled = computed(() => props.status === 'CANCELLED')

const activeIndex = computed(() => {
  if (cancelled.value) return -1
  const idx = STEPS.findIndex((s) => s.key === props.status)
  return idx < 0 ? 0 : idx
})

const statusLabel = computed(() => ORDER_STATUS[props.status]?.label ?? props.status)
</script>

<template>
  <div class="order-steps">
    <el-alert
      v-if="cancelled"
      type="error"
      :closable="false"
      show-icon
      :title="`订单已取消 · ${statusLabel}`"
      :description="timeline.find((t) => t.desc)?.desc || '订单已终止，退款将原路退回余额账户'"
      class="order-steps__alert"
    />

    <el-steps v-else :active="activeIndex" align-center class="order-steps__bar" finish-status="success">
      <el-step v-for="s in STEPS" :key="s.key" :title="s.label" />
    </el-steps>

    <el-timeline v-if="vertical && timeline.length" class="order-steps__timeline">
      <el-timeline-item
        v-for="(t, i) in timeline"
        :key="i"
        :timestamp="t.time || ''"
        :type="i === timeline.length - 1 ? 'primary' : 'success'"
        placement="top"
      >
        <div class="order-steps__item-title">{{ t.label }}</div>
        <div v-if="t.desc" class="order-steps__item-desc aux-text">{{ t.desc }}</div>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<style scoped>
.order-steps__alert {
  margin-bottom: 16px;
  border-radius: var(--radius-btn);
}

.order-steps__bar {
  padding: 8px 0 16px;
}

.order-steps__bar :deep(.el-step__title) {
  font-size: var(--font-size-aux);
}

.order-steps__timeline {
  padding-left: 4px;
}

.order-steps__item-title {
  font-size: var(--font-size-body);
  color: var(--color-text-primary);
}

.order-steps__item-desc {
  margin-top: 2px;
}
</style>
