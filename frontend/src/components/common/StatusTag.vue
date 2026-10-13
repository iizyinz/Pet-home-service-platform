<script setup lang="ts">
import { computed } from 'vue'
import { ORDER_STATUS, CERT_STATUS, RECORD_TYPE, USER_STATUS, ROLE_LABEL } from '@/utils/constants'

/** 枚举 → el-tag 统一映射，色彩严格对应方案状态色 */
const props = defineProps<{
  kind: 'order' | 'cert' | 'record' | 'user' | 'role'
  value: string | number
}>()

type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

const label = computed(() => {
  const v = props.value
  switch (props.kind) {
    case 'order':
      return ORDER_STATUS[String(v)]?.label ?? String(v)
    case 'cert':
      return CERT_STATUS[Number(v)]?.label ?? String(v)
    case 'record':
      return RECORD_TYPE[String(v)]?.label ?? String(v)
    case 'user':
      return USER_STATUS[Number(v)]?.label ?? String(v)
    case 'role':
      return ROLE_LABEL[String(v)] ?? String(v)
    default:
      return String(v)
  }
})

const type = computed<TagType>(() => {
  const v = props.value
  switch (props.kind) {
    case 'order':
      return (ORDER_STATUS[String(v)]?.type ?? 'info') as TagType
    case 'cert':
      return (CERT_STATUS[Number(v)]?.type ?? 'info') as TagType
    case 'user':
      return (USER_STATUS[Number(v)]?.type ?? 'info') as TagType
    case 'record':
      // 流水类型用方案状态色：消费=危险 退款=信息 充值=成功 调整=警告
      return ({ CONSUME: 'danger', RECHARGE: 'success', REFUND: 'info', ADJUST: 'warning' } as Record<string, TagType>)[
        String(v)
      ] ?? 'info'
    case 'role':
      return ({ USER: 'info', STAFF: 'success', ADMIN: 'primary' } as Record<string, TagType>)[String(v)] ?? 'info'
    default:
      return 'info'
  }
})
</script>

<template>
  <el-tag :type="type" effect="light" round>{{ label }}</el-tag>
</template>
