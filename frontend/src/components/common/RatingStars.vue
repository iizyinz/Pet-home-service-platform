<script setup lang="ts">
import { computed } from 'vue'
import { Star, StarFilled } from '@element-plus/icons-vue'

/** 星级展示 / 输入（PRD 5.6 星级评分 1-5） */
const props = withDefaults(
  defineProps<{
    modelValue?: number
    readonly?: boolean
    size?: 'small' | 'default' | 'large'
  }>(),
  { modelValue: 0, readonly: false, size: 'default' },
)

const emit = defineEmits<{ 'update:modelValue': [value: number] }>()

const display = computed(() => Math.round(props.modelValue || 0))

function onSelect(v: number) {
  if (props.readonly) return
  emit('update:modelValue', v)
}
</script>

<template>
  <span class="rating-stars" :class="[`rating-stars--${size}`, { 'is-readonly': readonly }]">
    <el-icon
      v-for="i in 5"
      :key="i"
      class="rating-stars__star"
      :class="{ 'is-active': i <= display }"
      @click="onSelect(i)"
    >
      <StarFilled v-if="i <= display" />
      <Star v-else />
    </el-icon>
    <span v-if="$slots.default" class="rating-stars__text"><slot /></span>
  </span>
</template>

<style scoped>
.rating-stars {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: var(--color-disabled);
}

.rating-stars__star {
  color: var(--color-disabled);
  transition: color 0.15s ease;
}

.rating-stars__star.is-active {
  color: var(--color-warning);
}

.rating-stars:not(.is-readonly) .rating-stars__star {
  cursor: pointer;
}

.rating-stars--small { font-size: 14px; }
.rating-stars--default { font-size: 18px; }
.rating-stars--large { font-size: 24px; }

.rating-stars__text {
  margin-left: 6px;
  font-size: var(--font-size-aux);
  color: var(--color-text-secondary);
}
</style>
