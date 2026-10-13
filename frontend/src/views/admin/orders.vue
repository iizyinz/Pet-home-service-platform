<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import SearchForm from '@/components/common/SearchForm.vue'
import TableWrapper from '@/components/common/TableWrapper.vue'
import PageCard from '@/components/common/PageCard.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import ConfirmAction from '@/components/common/ConfirmAction.vue'
import { usePage } from '@/composables/usePage'
import { fetchAdminOrders, fetchAdminOrderDetail, closeOrderWithRefund } from '@/api/admin/ops'
import { ORDER_TABS, fmtMoney, fmtTime } from '@/utils/constants'
import type { OrderDetailVO, OrderListVO, OrderStatus } from '@/types'

/** 订单管理（PRD 7.1）：全量订单查询 + 详情 + 运营干预（关闭并退款，写审计与通知） */

interface OrderQuery {
  status: OrderStatus | 'ALL' | ''
  userId: string
  staffId: string
  /** 订单号 / 宠物 / 主人 / 服务师 —— 后端无订单关键词接口，仅在当前页内检索 */
  keyword: string
}

const { list, total, page, size, loading, error, query, search, reset, changePage, changeSize, refresh } =
  usePage<OrderListVO, OrderQuery>(
    (q) =>
      fetchAdminOrders({
        status: q.status,
        userId: toNum(q.userId),
        staffId: toNum(q.staffId),
        page: q.page,
        size: q.size,
      }),
    { defaultQuery: { status: 'ALL', userId: '', staffId: '', keyword: '' } },
  )

/** 输入框文本 → 合法正整数（非法值不参与筛选） */
function toNum(v: string): number | undefined {
  const n = Number(v)
  return v.trim() !== '' && Number.isInteger(n) && n > 0 ? n : undefined
}

/* ---------- 页内关键词（后端无该维度，仅检索当前页） ---------- */
const keyword = computed(() => query.keyword.trim().toLowerCase())
const hasLocalFilter = computed(() => keyword.value !== '')
const displayList = computed(() => {
  const k = keyword.value
  if (!k) return list.value
  return list.value.filter((r) =>
    [r.orderNo, r.ownerName, r.staffName, r.itemName, r.petName, r.petBreed].some((s) =>
      (s ?? '').toLowerCase().includes(k),
    ),
  )
})
const tableData = computed(() => displayList.value as unknown as Record<string, unknown>[])

/** 终态订单不可再运营干预 */
function canClose(status: string): boolean {
  return status !== 'COMPLETED' && status !== 'CANCELLED'
}

/* ---------- 订单详情 ---------- */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<OrderDetailVO | null>(null)

async function openDetail(row: OrderListVO) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await fetchAdminOrderDetail(row.id)
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    detailLoading.value = false
  }
}

/**
 * 后端 OrderDetailVO.address 实际返回「省市区+详细」字符串（TypeScript 类型写成了 UserAddress），
 * 这里同时兼容两种结构，避免详情页渲染出空地址。
 */
function addressText(v: unknown): string {
  if (!v) return '-'
  if (typeof v === 'string') return v || '-'
  if (typeof v === 'object') {
    const a = v as { region?: string; detail?: string }
    const text = `${a.region ?? ''}${a.detail ?? ''}`
    return text || '-'
  }
  return '-'
}

/** 服务进度图片（后端为 JSON 数组字符串） */
function parseImages(v: string | string[] | undefined): string[] {
  if (!v) return []
  if (Array.isArray(v)) return v
  try {
    const arr: unknown = JSON.parse(v)
    return Array.isArray(arr) ? arr.filter((s): s is string => typeof s === 'string') : []
  } catch {
    return []
  }
}

/* ---------- 运营干预：关闭订单并退款 ---------- */
const closeVisible = ref(false)
const closing = ref(false)
const closeRef = ref<FormInstance>()
const closeForm = reactive({ id: 0, orderNo: '', reason: '' })
const closeRules: FormRules = {
  reason: [
    { required: true, message: '请填写干预原因，将同步给用户与服务师', trigger: 'blur' },
    { max: 255, message: '原因不能超过 255 字', trigger: 'blur' },
  ],
}

function openClose(row: OrderListVO) {
  closeForm.id = row.id
  closeForm.orderNo = row.orderNo
  closeForm.reason = ''
  closeVisible.value = true
  closeRef.value?.clearValidate()
}

async function submitClose() {
  const valid = await closeRef.value?.validate().catch(() => false)
  if (!valid) return
  closing.value = true
  try {
    await closeOrderWithRefund(closeForm.id, closeForm.reason)
    ElMessage.success('订单已关闭，款项已退回用户余额')
    closeVisible.value = false
    if (detail.value && detail.value.id === closeForm.id) {
      detailVisible.value = false
      detail.value = null
    }
    await refresh()
  } catch {
    /* 拦截器已提示（终态订单不可干预等） */
  } finally {
    closing.value = false
  }
}

const exportColumns = [
  { label: 'ID', key: 'id' },
  { label: '订单号', key: 'orderNo' },
  { label: '下单用户', key: 'ownerName' },
  { label: '服务师', key: 'staffName' },
  { label: '服务项目', key: 'itemName' },
  { label: '宠物', key: 'petName' },
  { label: '金额(元)', key: 'amount' },
  { label: '状态', key: 'status' },
  { label: '预约时间', key: 'appointmentTime' },
  { label: '下单时间', key: 'createdAt' },
]
</script>

<template>
  <div class="page-container">
    <PageCard title="订单管理">
      <SearchForm :cols="4" @search="search" @reset="reset">
        <el-form-item label="订单状态">
          <el-select v-model="query.status" placeholder="全部状态" clearable>
            <el-option v-for="t in ORDER_TABS" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户ID">
          <el-input v-model="query.userId" placeholder="下单用户 ID" clearable :prefix-icon="Search" />
        </el-form-item>
        <el-form-item label="服务师ID">
          <el-input v-model="query.staffId" placeholder="接单服务师 ID" clearable :prefix-icon="Search" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="订单号 / 宠物 / 主人 / 服务师" clearable :prefix-icon="Search" />
        </el-form-item>
      </SearchForm>

      <TableWrapper
        :data="tableData"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        :error="error"
        export-name="订单列表"
        :export-columns="exportColumns"
        empty-text="暂无订单数据"
        @update:page="changePage"
        @update:size="changeSize"
        @refresh="refresh"
      >
        <template #toolbar>
          <span class="aux-text">共 {{ total }} 笔订单</span>
          <span v-if="hasLocalFilter" class="local-filter-tip">
            页内关键词：仅在当前页 {{ list.length }} 条记录中检索
          </span>
        </template>

        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="orderNo" label="订单号" min-width="160" show-overflow-tooltip />
        <el-table-column label="下单用户" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.ownerName || '-' }}（{{ row.userId }}）</template>
        </el-table-column>
        <el-table-column label="服务师" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.staffName || '待分配' }}</template>
        </el-table-column>
        <el-table-column prop="itemName" label="服务项目" min-width="140" show-overflow-tooltip />
        <el-table-column label="宠物" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.petName || '-' }}<template v-if="row.petBreed"> · {{ row.petBreed }}</template>
          </template>
        </el-table-column>
        <el-table-column label="金额(元)" width="110" align="right">
          <template #default="{ row }"><span class="price-text">{{ fmtMoney(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }"><StatusTag kind="order" :value="row.status" /></template>
        </el-table-column>
        <el-table-column label="预约时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.appointmentTime) }}</template>
        </el-table-column>
        <el-table-column label="下单时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="canClose(row.status)" link type="danger" @click="openClose(row)">关闭退款</el-button>
            <span v-else class="aux-text">已终态</span>
          </template>
        </el-table-column>
      </TableWrapper>
    </PageCard>

    <!-- 订单详情 -->
    <el-dialog v-model="detailVisible" title="订单详情" width="min(760px, 94vw)" top="6vh">
      <div v-loading="detailLoading" class="detail-body">
        <template v-if="detail">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <StatusTag kind="order" :value="detail.status" />
            </el-descriptions-item>
            <el-descriptions-item label="服务项目">{{ detail.itemName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="订单金额">
              <span class="price-text">¥{{ fmtMoney(detail.amount) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="下单用户">
              {{ detail.ownerName || '-' }}（ID {{ detail.userId }}）
            </el-descriptions-item>
            <el-descriptions-item label="服务师">
              <template v-if="detail.staffId">{{ detail.staffName || '-' }}（ID {{ detail.staffId }}）</template>
              <template v-else>待分配</template>
            </el-descriptions-item>
            <el-descriptions-item label="宠物">
              {{ detail.petName || '-' }}<template v-if="detail.petBreed"> · {{ detail.petBreed }}</template>
            </el-descriptions-item>
            <el-descriptions-item label="服务时长">
              {{ detail.durationMinutes ? `${detail.durationMinutes} 分钟` : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="预约时间">{{ fmtTime(detail.appointmentTime) }}</el-descriptions-item>
            <el-descriptions-item label="下单时间">{{ fmtTime(detail.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="服务师开始">{{ fmtTime(detail.startTime) }}</el-descriptions-item>
            <el-descriptions-item label="服务师完成">{{ fmtTime(detail.finishTime) }}</el-descriptions-item>
            <el-descriptions-item label="联系人">{{ detail.contactName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ detail.contactPhone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="上门地址" :span="2">{{ addressText(detail.address) }}</el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">{{ detail.remark || '无' }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.cancelReason" label="取消/关闭原因" :span="2">
              {{ detail.cancelReason }}
            </el-descriptions-item>
          </el-descriptions>

          <h4 class="detail-subtitle">服务进度（{{ detail.progressList?.length ?? 0 }}）</h4>
          <el-timeline v-if="detail.progressList?.length">
            <el-timeline-item
              v-for="p in detail.progressList"
              :key="p.id"
              :timestamp="fmtTime(p.createdAt)"
              placement="top"
            >
              <div class="progress-title">{{ p.title }}</div>
              <div v-if="p.serviceTime" class="aux-text">服务时间：{{ p.serviceTime }}</div>
              <div v-if="parseImages(p.images).length" class="progress-images">
                <el-image
                  v-for="(img, i) in parseImages(p.images)"
                  :key="i"
                  :src="img"
                  :preview-src-list="parseImages(p.images)"
                  :initial-index="i"
                  fit="cover"
                  class="progress-image"
                  preview-teleported
                />
              </div>
            </el-timeline-item>
          </el-timeline>
          <p v-else class="aux-text">暂无服务进度记录</p>
        </template>
        <el-empty v-else-if="!detailLoading" description="详情加载失败或订单不存在" :image-size="90" />
      </div>
    </el-dialog>

    <!-- 运营干预：关闭并退款 -->
    <el-dialog v-model="closeVisible" title="运营干预 · 关闭订单" width="min(480px, 94vw)">
      <el-form ref="closeRef" :model="closeForm" :rules="closeRules" label-width="90px">
        <el-form-item label="订单号">
          <el-input :model-value="closeForm.orderNo" disabled />
        </el-form-item>
        <el-form-item label="干预原因" prop="reason">
          <el-input
            v-model="closeForm.reason"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="如：服务师无法履约 / 用户申请退款 / 异常订单处理"
          />
          <p class="close-tip aux-text">确认后订单转为「已取消」，全额退回用户余额并生成退款流水，同时通知双方。</p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeVisible = false">取消</el-button>
        <ConfirmAction
          title="确认关闭该订单？"
          message="将执行退款并写入审计日志，操作不可撤销。"
          danger
          confirm-text="确认关闭"
          @confirm="submitClose"
        >
          <el-button type="danger" :loading="closing">关闭并退款</el-button>
        </ConfirmAction>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.local-filter-tip {
  margin-left: 12px;
  font-size: var(--font-size-aux);
  color: var(--color-warning);
}

.detail-body {
  min-height: 160px;
}

.detail-subtitle {
  margin: 20px 0 12px;
  font-size: var(--font-size-module);
  font-weight: 600;
  color: var(--color-text-primary);
}

.progress-title {
  font-size: var(--font-size-body);
  color: var(--color-text-primary);
}

.progress-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.progress-image {
  width: 72px;
  height: 72px;
  border-radius: var(--radius-tag);
  overflow: hidden;
}

.close-tip {
  margin: 6px 0 0;
  line-height: 1.5;
}
</style>
