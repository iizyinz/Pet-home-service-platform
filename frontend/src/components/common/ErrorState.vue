<script setup lang="ts">
import { WarningFilled } from '@element-plus/icons-vue'

/** 加载失败态（UI 清单 第四节「加载失败页」），带重试入口 */
withDefaults(defineProps<{ text?: string; retryText?: string }>(), {
  text: '加载失败，请检查网络后重试',
  retryText: '重新加载',
})

defineEmits<{ retry: [] }>()
</script>

<template>
  <div class="error-state">
    <el-icon class="error-state__icon" :size="40"><WarningFilled /></el-icon>
    <p class="error-state__text">{{ text }}</p>
    <el-button type="primary" plain @click="$emit('retry')">{{ retryText }}</el-button>
  </div>
</template>

<style scoped>
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 240px;
  padding: 24px;
}
.error-state__icon {
  color: var(--color-danger);
}
.error-state__text {
  margin: 0;
  font-size: var(--font-size-body);
  color: var(--color-text-secondary);
}
</style>
