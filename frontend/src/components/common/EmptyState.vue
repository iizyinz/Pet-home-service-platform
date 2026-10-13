<script setup lang="ts">
/** 空状态（PRD/UI 清单 第四节「空状态页」） */
withDefaults(defineProps<{ text?: string; actionText?: string }>(), {
  text: '暂无数据',
  actionText: '',
})

defineEmits<{ action: [] }>()
</script>

<template>
  <div class="empty-state">
    <el-empty :description="text" :image-size="96">
      <template #description>
        <p class="empty-state__text">{{ text }}</p>
        <p v-if="$slots.tip" class="empty-state__tip"><slot name="tip" /></p>
      </template>
      <el-button v-if="actionText" type="primary" @click="$emit('action')">{{ actionText }}</el-button>
      <slot />
    </el-empty>
  </div>
</template>

<style scoped>
.empty-state {
  padding: 32px 0;
}
.empty-state__text {
  margin: 8px 0 0;
  font-size: var(--font-size-body);
  color: var(--color-text-secondary);
}
.empty-state__tip {
  margin: 4px 0 0;
  font-size: var(--font-size-aux);
  color: var(--color-text-muted);
}
</style>
