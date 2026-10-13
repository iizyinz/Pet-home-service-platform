<script setup lang="ts">
import { ref, useId } from 'vue'

/**
 * 筛选表单：统一栅格布局、回车即搜、查询/重置。
 * 内容一律放在具名插槽内，每个字段包 <el-form-item> 即可。
 */
withDefaults(defineProps<{ cols?: number; showReset?: boolean; labelWidth?: string }>(), {
  cols: 4,
  showReset: true,
  labelWidth: '80px',
})

const emit = defineEmits<{ search: []; reset: [] }>()
const formRef = ref()

function onSearch() {
  formRef.value?.validate?.(() => undefined)
  emit('search')
}
function onReset() {
  formRef.value?.resetFields?.()
  emit('reset')
}
</script>

<template>
  <el-form
    ref="formRef"
    class="search-form"
    :inline="true"
    :label-width="labelWidth"
    @submit.prevent="onSearch"
  >
    <div class="search-form__grid" :style="{ '--cols': cols }">
      <slot />
    </div>
    <div class="search-form__actions">
      <el-button type="primary" @click="onSearch">查询</el-button>
      <el-button v-if="showReset" @click="onReset">重置</el-button>
      <slot name="actions" />
    </div>
  </el-form>
</template>

<style scoped>
.search-form {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 8px 12px;
}

.search-form__grid {
  display: grid;
  grid-template-columns: repeat(var(--cols), minmax(0, 1fr));
  gap: 12px;
  flex: 1 1 auto;
}

.search-form__grid :deep(.el-form-item) {
  margin-right: 0;
  width: 100%;
}

.search-form__grid :deep(.el-form-item__content) {
  flex: 1 1 auto;
}

.search-form__actions {
  display: flex;
  gap: 8px;
}

@media (max-width: 960px) {
  .search-form__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
