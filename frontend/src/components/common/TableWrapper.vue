<script setup lang="ts">
import { Download } from '@element-plus/icons-vue'
import ErrorState from './ErrorState.vue'
import { exportCsv, type ExportColumn } from '@/utils/export'

/**
 * 表格 + 分页 + 状态容器（约束 5：列表必须有分页/加载态，接口失败必须有提示）
 * - loading -> v-loading 遮罩
 * - error   -> ErrorState + 重试
 * - 空数据  -> el-empty
 * - total>0 -> el-pagination
 */
withDefaults(
  defineProps<{
    data: Record<string, unknown>[]
    total?: number
    page?: number
    size?: number
    loading?: boolean
    error?: string
    emptyText?: string
    exportName?: string
    exportColumns?: ExportColumn[]
    stripe?: boolean
    border?: boolean
  }>(),
  {
    total: 0,
    page: 1,
    size: 10,
    loading: false,
    error: '',
    emptyText: '暂无数据',
    exportName: '',
    exportColumns: () => [],
    stripe: true,
    border: false,
  },
)

const emit = defineEmits<{
  'update:page': [page: number]
  'update:size': [size: number]
  refresh: []
}>()

function onPage(p: number) {
  emit('update:page', p)
}
function onSize(s: number) {
  emit('update:size', s)
}
</script>

<template>
  <div class="table-wrapper" v-loading="loading" element-loading-background="rgba(255,255,255,0.7)">
    <ErrorState v-if="error" :text="error" @retry="emit('refresh')" />

    <template v-else>
      <div v-if="$slots.toolbar || exportName" class="table-toolbar">
        <div class="table-toolbar__left"><slot name="toolbar" /></div>
        <el-button
          v-if="exportName"
          :disabled="!data.length"
          :title="data.length ? '导出当前已加载数据为 CSV' : '暂无可导出数据'"
          @click="exportCsv(exportName, exportColumns, data)"
        >
          <el-icon><Download /></el-icon>
          导出
        </el-button>
      </div>

      <el-table :data="data" :stripe="stripe" :border="border" class="table-wrapper__table">
        <slot />
        <template #empty>
          <el-empty :description="emptyText" :image-size="80" />
        </template>
      </el-table>

      <div v-if="total > 0" class="table-pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          :current-page="page"
          :page-size="size"
          :page-sizes="[10, 20, 50]"
          @current-change="onPage"
          @size-change="onSize"
        />
      </div>
    </template>
  </div>
</template>

<style scoped>
.table-wrapper {
  min-height: 200px;
}

.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.table-wrapper__table {
  width: 100%;
}

.table-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
