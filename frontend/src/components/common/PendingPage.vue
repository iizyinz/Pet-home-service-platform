<script setup lang="ts">
import PageCard from './PageCard.vue'

/**
 * 待实现页面占位（第七步只交付关键页面，其余页面在下一批次生成）。
 * 交付完成后本组件会被移除。
 */
defineProps<{ title?: string; hint?: string }>()
</script>

<template>
  <div class="page-container">
    <PageCard :title="title || '页面建设中'">
      <p class="pending-hint">{{ hint || '该页面将在下一批次生成，路由与权限已就位。' }}</p>
    </PageCard>
  </div>
</template>

<style scoped>
.pending-hint {
  margin: 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-body);
}
</style>
