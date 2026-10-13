<script setup lang="ts">
import { computed } from 'vue'
import { Search } from '@element-plus/icons-vue'
import SearchForm from '@/components/common/SearchForm.vue'
import TableWrapper from '@/components/common/TableWrapper.vue'
import PageCard from '@/components/common/PageCard.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import { usePage } from '@/composables/usePage'
import { fetchAdminBalanceRecords } from '@/api/admin/ops'
import { RECORD_TYPE, fmtMoney, fmtTime } from '@/utils/constants'
import type { BalanceRecord } from '@/types'

/** 余额流水（PRD 7.1）：全量资金流水（消费/充值/退款/调账），对账与审计 */

interface BalanceQuery {
  /** CONSUME / RECHARGE / REFUND / ADJUST */
  type: string
  /** 归属用户 ID */
  userId: string
}

const { list, total, page, size, loading, error, query, search, reset, changePage, changeSize, refresh } =
  usePage<BalanceRecord, BalanceQuery>(
    (q) => fetchAdminBalanceRecords({ type: q.type, userId: toNum(q.userId), page: q.page, size: q.size }),
    { defaultQuery: { type: '', userId: '' } },
  )

/** 输入框文本 → 合法正整数 */
function toNum(v: string): number | undefined {
  const n = Number(v)
  return v.trim() !== '' && Number.isInteger(n) && n > 0 ? n : undefined
}

/** 带正负号的金额（正入账 / 负出账） */
function fmtSigned(v: unknown): string {
  const n = Number(v ?? 0)
  return `${n > 0 ? '+' : ''}${fmtMoney(n)}`
}

/** 本页合计（元） */
const pageSum = computed(() => list.value.reduce((sum, r) => sum + Number(r.amount ?? 0), 0))

/** 流水类型选项（来自字典） */
const typeOptions = Object.entries(RECORD_TYPE).map(([value, cfg]) => ({ value, label: cfg.label }))

const exportColumns = [
  { label: 'ID', key: 'id' },
  { label: '用户ID', key: 'userId' },
  { label: '类型', key: 'type' },
  { label: '变动金额(元)', key: 'amount' },
  { label: '变动后余额(元)', key: 'balanceAfter' },
  { label: '关联订单ID', key: 'orderId' },
  { label: '渠道', key: 'channel' },
  { label: '备注', key: 'remark' },
  { label: '时间', key: 'createdAt' },
]
</script>

<template>
  <div class="page-container">
    <PageCard title="余额流水">
      <SearchForm :cols="3" @search="search" @reset="reset">
        <el-form-item label="流水类型">
          <el-select v-model="query.type" placeholder="全部类型" clearable>
            <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户ID">
          <el-input v-model="query.userId" placeholder="归属用户 ID" clearable :prefix-icon="Search" />
        </el-form-item>
      </SearchForm>

      <TableWrapper
        :data="list as unknown as Record<string, unknown>[]"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        :error="error"
        export-name="余额流水"
        :export-columns="exportColumns"
        empty-text="暂无流水记录"
        @update:page="changePage"
        @update:size="changeSize"
        @refresh="refresh"
      >
        <template #toolbar>
          <span class="aux-text">
            共 {{ total }} 条流水 · 本页合计
            <span :class="pageSum >= 0 ? 'amount-in' : 'amount-out'">{{ fmtSigned(pageSum) }}</span>
            元
          </span>
        </template>

        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column label="用户ID" width="110">
          <template #default="{ row }">
            <span v-if="row.userId">用户 {{ row.userId }}</span>
            <span v-else class="aux-text">-</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }"><StatusTag kind="record" :value="row.type" /></template>
        </el-table-column>
        <el-table-column label="变动金额(元)" width="140" align="right">
          <template #default="{ row }">
            <span :class="Number(row.amount) >= 0 ? 'amount-in' : 'amount-out'">{{ fmtSigned(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="变动后余额(元)" width="150" align="right">
          <template #default="{ row }"><span class="price-text">{{ fmtMoney(row.balanceAfter) }}</span></template>
        </el-table-column>
        <el-table-column label="关联订单" width="110" align="center">
          <template #default="{ row }">{{ row.orderId ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="channel" label="渠道" width="110">
          <template #default="{ row }">{{ row.channel || '-' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
      </TableWrapper>
    </PageCard>
  </div>
</template>

<style scoped>
.amount-in {
  color: var(--color-success);
  font-weight: 600;
}

.amount-out {
  color: var(--color-danger);
  font-weight: 600;
}
</style>
