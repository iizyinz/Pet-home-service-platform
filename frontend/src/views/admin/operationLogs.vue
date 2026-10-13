<script setup lang="ts">
import { computed } from 'vue'
import { Search } from '@element-plus/icons-vue'
import SearchForm from '@/components/common/SearchForm.vue'
import TableWrapper from '@/components/common/TableWrapper.vue'
import PageCard from '@/components/common/PageCard.vue'
import { usePage } from '@/composables/usePage'
import { fetchOperationLogs } from '@/api/admin/ops'
import { fmtTime } from '@/utils/constants'
import type { OperationLog } from '@/types'

/** 操作日志（PRD 7.1）：管理端关键操作审计（操作人 / 动作 / 对象 / IP / 结果），详情可展开 */

interface LogQuery {
  /** 动作关键词（后端 LIKE 匹配 action） */
  action: string
  /** 操作人 —— 后端无该参数，仅在当前页内检索 */
  operator: string
  /** 结果 —— 后端无该参数，仅在当前页内检索 */
  result: '' | 'SUCCESS' | 'FAIL'
  /** 操作时间范围 —— 后端无该参数，仅在当前页内检索 */
  dateRange: string[] | null
}

const { list, total, page, size, loading, error, query, search, reset, changePage, changeSize, refresh } =
  usePage<OperationLog, LogQuery>((q) => fetchOperationLogs({ action: q.action, page: q.page, size: q.size }), {
    defaultQuery: { action: '', operator: '', result: '', dateRange: null },
  })

/** 常见动作的中文释义（未收录的原样展示） */
const ACTION_LABEL: Record<string, string> = {
  'order.close.refund': '关闭订单并退款',
  'review.status': '评价隐藏 / 恢复',
  'user.status': '用户冻结 / 解冻',
  'user.balance.adjust': '余额调整',
  'notice.resend': '补发站内通知',
  create: '新增数据',
  update: '修改数据',
  delete: '删除数据',
}

function actionLabel(v: unknown): string {
  const key = String(v ?? '')
  return (ACTION_LABEL[key] ?? key) || '-'
}

/** 日期归一化为 yyyy-MM-dd（兼容 value-format 未生效时返回 Date 的情况） */
function toDateStr(v: string | Date | number | null | undefined): string {
  if (v === undefined || v === null) return ''
  if (typeof v === 'string') return v.slice(0, 10)
  if (typeof v === 'number') return new Date(v).toISOString().slice(0, 10)
  const d = new Date(v.getTime() - v.getTimezoneOffset() * 60000)
  return d.toISOString().slice(0, 10)
}

/* ---------- 页内筛选：操作人 / 结果 / 时间范围 ---------- */
const hasLocalFilter = computed(
  () => query.operator.trim() !== '' || !!query.result || (query.dateRange?.length ?? 0) > 0,
)

const displayList = computed(() => {
  const op = query.operator.trim().toLowerCase()
  const res = query.result
  const range = query.dateRange
  const start = range && range[0] ? toDateStr(range[0]) : ''
  const end = range && range[1] ? toDateStr(range[1]) : ''
  if (!op && !res && !start && !end) return list.value
  return list.value.filter((r) => {
    if (op && !`${r.operatorName ?? ''} ${r.operatorId ?? ''}`.toLowerCase().includes(op)) return false
    if (res && String(r.result ?? '') !== res) return false
    if (start || end) {
      const day = toDateStr(r.createdAt)
      if (!day) return false
      if (start && day < start) return false
      if (end && day > end) return false
    }
    return true
  })
})
const tableData = computed(() => displayList.value as unknown as Record<string, unknown>[])

const exportColumns = [
  { label: 'ID', key: 'id' },
  { label: '操作人ID', key: 'operatorId' },
  { label: '操作人', key: 'operatorName' },
  { label: '动作', key: 'action' },
  { label: '对象', key: 'target' },
  { label: '详情', key: 'detail' },
  { label: '结果', key: 'result' },
  { label: 'IP', key: 'ip' },
  { label: '操作时间', key: 'createdAt' },
]
</script>

<template>
  <div class="page-container">
    <PageCard title="操作日志">
      <SearchForm :cols="4" @search="search" @reset="reset">
        <el-form-item label="动作">
          <el-input
            v-model="query.action"
            placeholder="如 order.close.refund / update / delete"
            clearable
            :prefix-icon="Search"
          />
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="query.operator" placeholder="操作人账号 / ID" clearable :prefix-icon="Search" />
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="query.result" placeholder="全部结果" clearable>
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAIL" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="query.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :editable="false"
            style="width: 100%"
          />
        </el-form-item>
      </SearchForm>

      <TableWrapper
        :data="tableData"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        :error="error"
        export-name="操作日志"
        :export-columns="exportColumns"
        empty-text="暂无操作日志"
        @update:page="changePage"
        @update:size="changeSize"
        @refresh="refresh"
      >
        <template #toolbar>
          <span class="aux-text">共 {{ total }} 条日志</span>
          <span v-if="hasLocalFilter" class="local-filter-tip">
            页内筛选：操作人 / 结果 / 时间仅在当前页 {{ list.length }} 条记录中检索（展开行可看详情）
          </span>
        </template>

        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="log-detail">
              <p><span class="log-detail__label">动作：</span>{{ row.action }}</p>
              <p><span class="log-detail__label">对象：</span>{{ row.target || '-' }}</p>
              <p><span class="log-detail__label">详情：</span>{{ row.detail || '（无）' }}</p>
              <p><span class="log-detail__label">结果：</span>{{ row.result }}</p>
              <p><span class="log-detail__label">来源 IP：</span>{{ row.ip || '-' }}</p>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column label="操作人" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.operatorName || '-' }}<template v-if="row.operatorId">（{{ row.operatorId }}）</template>
          </template>
        </el-table-column>
        <el-table-column label="动作" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="action-label">{{ actionLabel(row.action) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="target" label="对象" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.target || '-' }}</template>
        </el-table-column>
        <el-table-column label="结果" width="100">
          <template #default="{ row }">
            <el-tag :type="row.result === 'FAIL' ? 'danger' : 'success'" effect="light" round>
              {{ row.result === 'FAIL' ? '失败' : '成功' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ip" label="来源IP" width="130">
          <template #default="{ row }">{{ row.ip || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
      </TableWrapper>
    </PageCard>
  </div>
</template>

<style scoped>
.local-filter-tip {
  margin-left: 12px;
  font-size: var(--font-size-aux);
  color: var(--color-warning);
}

.action-label {
  font-weight: 600;
  color: var(--color-text-primary);
}

.log-detail {
  padding: 8px 16px 12px 48px;
  background: var(--color-bg-page);
}

.log-detail p {
  margin: 4px 0;
  font-size: var(--font-size-aux);
  color: var(--color-text-secondary);
  line-height: 1.6;
  word-break: break-word;
}

.log-detail__label {
  color: var(--color-text-muted);
}
</style>
