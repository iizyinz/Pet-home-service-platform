<script setup lang="ts">
/**
 * 危险操作二次确认：统一删除/冻结/隐藏/关单退款/取消订单的确认交互。
 * 用法：包住触发元素即可
 *   <ConfirmAction title="确认删除该宠物？" danger @confirm="onRemove">
 *     <el-button type="danger" plain>删除</el-button>
 *   </ConfirmAction>
 */
withDefaults(
  defineProps<{
    title: string
    message?: string
    confirmText?: string
    cancelText?: string
    danger?: boolean
  }>(),
  { message: '', confirmText: '确定', cancelText: '取消', danger: false },
)

defineEmits<{ confirm: [] }>()
</script>

<template>
  <el-popconfirm
    :title="title"
    :confirm-button-text="confirmText"
    :cancel-button-text="cancelText"
    :width="280"
    confirm-button-type="primary"
    @confirm="$emit('confirm')"
  >
    <template #reference>
      <slot />
    </template>
    <template #description>
      <span v-if="message" class="confirm-desc">{{ message }}</span>
    </template>
  </el-popconfirm>
</template>

<style scoped>
.confirm-desc {
  display: block;
  font-size: var(--font-size-aux);
  color: var(--color-text-muted);
  line-height: 1.5;
}
</style>
