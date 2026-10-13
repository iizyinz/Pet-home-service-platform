<script setup lang="ts">
import { nextTick, ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import TableWrapper from '@/components/common/TableWrapper.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import ConfirmAction from '@/components/common/ConfirmAction.vue'
import { usePage } from '@/composables/usePage'
import { fetchOrders, doOrderAction, type OrderAction } from '@/api/orders'
import { ORDER_TABS, fmtMoney, fmtTime } from '@/utils/constants'
import type { OrderListVO, OrderStatus } from '@/types'

/** 订单处理（PRD 6.2）：状态分页筛选 + 状态机动作（接单 / 拒单 / 开始 / 完成） */
const router = useRouter()

const { list, total, page, size, loading, error, query, search, refresh, changePage, changeSize } =
  usePage<OrderListVO, { status: string }>(
    (q) => fetchOrders({ ...q, status: q.status as OrderStatus | 'ALL' }),
    { defaultQuery: { status: 'ALL' }, size: 10 },
  )

/** Tab 切换即按后端 status 参数重新查询（回到第 1 页） */
function onTabChange(name: string | number): void {
  query.status = String(name)
  void search()
}

function openDetail(row: OrderListVO): void {
  router.push(`/staff/orders/${row.id}`)
}

/* ---------- 状态机动作 ---------- */
const actingId = ref(0)

async function runAction(row: OrderListVO, action: OrderAction, success: string): Promise<void> {
  if (actingId.value) return
  actingId.value = row.id
  try {
    await doOrderAction(row.id, action)
    ElMessage.success(success)
    await refresh()
  } catch {
    // 拦截器已提示「订单当前状态不允许此操作」等业务错误，刷新以同步状态机
    await refresh()
  } finally {
    actingId.value = 0
  }
}

/* ---------- 标记完成：录入实际服务开始 / 结束时间（PRD 6.2 强制） ---------- */
const finishVisible = ref(false)
const finishRef = ref<FormInstance>()
const finishForm = reactive({ id: 0, orderNo: '', actualStartTime: '', actualFinishTime: '' })

const finishRules: FormRules = {
  actualStartTime: [{ required: true, message: '请选择实际开始时间', trigger: 'change' }],
  actualFinishTime: [
    { required: true, message: '请选择实际完成时间', trigger: 'change' },
    {
      validator: (_r, value: string, cb) => {
        if (value && finishForm.actualStartTime && value < finishForm.actualStartTime) {
          return cb(new Error('完成时间不能早于开始时间'))
        }
        cb()
      },
      trigger: 'change',
    },
  ],
}

/** 后端序列化格式 yyyy-MM-dd HH:mm:ss → DatePicker 值 */
function toPicker(v?: string): string {
  if (!v) return ''
  const normalized = v.replace('T', ' ')
  return normalized.length > 19 ? normalized.slice(0, 19) : normalized
}

function nowPicker(): string {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(
    d.getSeconds(),
  )}`
}

function openFinish(row: OrderListVO): void {
  finishForm.id = row.id
  finishForm.orderNo = row.orderNo
  finishForm.actualStartTime = toPicker(row.startTime) || toPicker(row.appointmentTime) || nowPicker()
  finishForm.actualFinishTime = nowPicker()
  finishVisible.value = true
  void nextTick(() => finishRef.value?.clearValidate())
}

async function submitFinish(): Promise<void> {
  const valid = await finishRef.value?.validate().catch(() => false)
  if (!valid) return
  try {
    await doOrderAction(finishForm.id, 'finish', {
      actualStartTime: finishForm.actualStartTime,
      actualFinishTime: finishForm.actualFinishTime,
    })
    ElMessage.success('已标记完成，等待用户确认')
    finishVisible.value = false
    await refresh()
  } catch {
    // 状态机错误已由拦截器提示
    await refresh()
  }
}

const exportColumns = [
  { label: '订单号', key: 'orderNo' },
  { label: '服务项目', key: 'itemName' },
  { label: '宠物', key: 'petName' },
  { label: '客户', key: 'ownerName' },
  { label: '预约时间', key: 'appointmentTime' },
  { label: '金额(元)', key: 'amount' },
  { label: '状态', key: 'statusLabel' },
]
</script>

<template>
  <div class="page-container staff-orders">
    <PageCard title="订单处理">
      <!-- 状态筛选 Tab（ORDER_TABS 与后端 status 参数一一对应） -->
      <el-tabs
        :model-value="query.status"
        class="staff-orders__tabs"
        @update:model-value="onTabChange"
      >
        <el-tab-pane v-for="t in ORDER_TABS" :key="t.value" :label="t.label" :name="t.value" />
      </el-tabs>

      <TableWrapper
        :data="list as unknown as Record<string, unknown>[]"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        :error="error"
        export-name="我的订单"
        :export-columns="exportColumns"
        empty-text="当前状态下暂无订单"
        @update:page="changePage"
        @update:size="changeSize"
        @refresh="refresh"
      >
        <template #toolbar>
          <div class="staff-orders__toolbar">
            <span class="aux-text">共 {{ total }} 笔订单</span>
            <el-button size="small" :loading="loading" @click="refresh">刷新</el-button>
          </div>
        </template>

        <el-table-column prop="orderNo" label="订单号" min-width="170" show-overflow-tooltip />
        <el-table-column label="宠物" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.petName || '-' }}</span>
            <span v-if="row.petBreed" class="aux-text">（{{ row.petBreed }}）</span>
          </template>
        </el-table-column>
        <el-table-column label="客户" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.ownerName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="itemName" label="服务项目" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.itemName || '-' }}</template>
        </el-table-column>
        <el-table-column label="预约时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.appointmentTime) }}</template>
        </el-table-column>
        <el-table-column label="金额(元)" width="110" align="right">
          <template #default="{ row }">
            <span class="price-text">{{ fmtMoney(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }"><StatusTag kind="order" :value="row.status" /></template>
        </el-table-column>

        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>

            <template v-if="row.status === 'PENDING_CONFIRM'">
              <el-button
                link
                type="primary"
                :loading="actingId === row.id"
                @click="runAction(row, 'accept', '接单成功，请按预约时间上门')"
              >
                接单
              </el-button>
              <ConfirmAction
                title="确认拒绝该订单？"
                message="拒单后款项将立即退回用户，订单自动关闭。"
                danger
                @confirm="runAction(row, 'reject', '已拒单，款项已退回用户')"
              >
                <el-button link type="danger">拒单</el-button>
              </ConfirmAction>
            </template>

            <ConfirmAction
              v-else-if="row.status === 'CONFIRMED'"
              title="确认开始服务？"
              message="开始后将记录服务开始时间，并通知客户。"
              @confirm="runAction(row, 'start', '服务已开始')"
            >
              <el-button link type="primary">开始服务</el-button>
            </ConfirmAction>

            <el-button v-else-if="row.status === 'SERVING'" link type="success" @click="openFinish(row)">
              完成服务
            </el-button>

            <span v-else-if="row.status === 'PENDING_SETTLE'" class="aux-text">待客户确认</span>
            <span v-else-if="row.status === 'CANCELLED'" class="aux-text">{{ row.cancelReason || '已取消' }}</span>
          </template>
        </el-table-column>
      </TableWrapper>
    </PageCard>

    <!-- 标记完成：录入实际服务时间，提交后进入「待确认完成」 -->
    <el-dialog v-model="finishVisible" title="标记完成" width="460px" align-center>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="提交后订单将进入「待确认完成」"
        description="请核对实际服务开始与结束时间，确认后不可自行修改。"
        class="staff-orders__alert"
      />
      <el-form ref="finishRef" :model="finishForm" :rules="finishRules" label-width="110px">
        <el-form-item label="订单号">
          <el-input :model-value="finishForm.orderNo" disabled />
        </el-form-item>
        <el-form-item label="实际开始时间" prop="actualStartTime">
          <el-date-picker
            v-model="finishForm.actualStartTime"
            type="datetime"
            placeholder="选择实际开始时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            :clearable="false"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="实际完成时间" prop="actualFinishTime">
          <el-date-picker
            v-model="finishForm.actualFinishTime"
            type="datetime"
            placeholder="选择实际完成时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            :clearable="false"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="finishVisible = false">取消</el-button>
        <el-button type="primary" @click="submitFinish">确认完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.staff-orders__tabs {
  margin-bottom: 4px;
}

.staff-orders__toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.staff-orders__alert {
  margin-bottom: 16px;
  border-radius: var(--radius-btn);
}
</style>
