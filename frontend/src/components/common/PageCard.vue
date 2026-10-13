<script setup lang="ts">
/** 页面卡片容器：统一 16px 圆角 / 16px 内边距 / 方案阴影 */
withDefaults(
  defineProps<{
    title?: string
    bodyPadding?: string
    shadow?: 'never' | 'always' | 'hover'
    loading?: boolean
  }>(),
  { bodyPadding: '16px', shadow: 'never', loading: false },
)
</script>

<template>
  <section class="page-card" :style="{ padding: bodyPadding }" v-loading="loading">
    <header v-if="title || $slots.header" class="page-card__header">
      <h3 class="page-card__title">{{ title }}</h3>
      <div class="page-card__actions"><slot name="header" /></div>
    </header>
    <div class="page-card__body">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.page-card {
  background: var(--color-bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.page-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.page-card__title {
  margin: 0;
  font-size: var(--font-size-module);
  font-weight: 600;
  color: var(--color-text-primary);
}

.page-card__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
