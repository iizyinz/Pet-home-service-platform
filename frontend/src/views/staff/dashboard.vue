<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Calendar, ChatDotRound, Document, Star, User } from '@element-plus/icons-vue'
import PageCard from '@/components/common/PageCard.vue'
import StatCard from '@/components/common/StatCard.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import ConfirmAction from '@/components/common/ConfirmAction.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import { useAuthStore } from '@/stores/auth'
import {
  fetchWorkbench,
  fetchSchedule,
  type WorkbenchOverview,
  type ScheduleMap,
} from '@/api/staff/workbench'
import { fetchOrders, doOrderAction } from '@/api/orders'
import { fmtMoney, fmtTime } from '@/utils/constants'
import type { OrderListVO } from '@/types'

/** 工作台（PRD 6.1）：概览指标 + 待办订单 + 今日排期 + 快捷入口 */
const router = useRouter()
const auth = useAuthStore()

const loading = ref(true)
const error = ref('')
const overview = ref<WorkbenchOverview | null>(null)
/** 待接单队列（前 5 条） */
const pending = ref<OrderListVO[]>([])
/** 今日上门排期（前 5 条） */
const todayOrders = ref<OrderListVO[]>([])
const actingId = ref(0)

/** yyyy-MM-dd（与后端排期分组 key 对齐） */
function dayKey(d: Date): string {
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

const ratingText = computed(() => Number(overview.value?.avgRating ?? 0).toFixed(1))
const pendingCount = computed(() => pending.value.length)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const now = new Date()
    const month = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
    const [ov, pd, sc] = await Promise.all([
      fetchWorkbench(),
      fetchOrders({ status: 'PENDING_CONFIRM', page: 1, size: 5 }),
      // 排期为辅助信息，失败不阻塞工作台主指标
      fetchSchedule(month).catch(() => ({}) as ScheduleMap),
    ])
    overview.value = ov
    pending.value = pd?.list ?? []
    todayOrders.value = (sc?.[dayKey(now)] ?? []).slice(0, 5)
  } catch (e) {
    error.value = (e as Error)?.message || '工作台加载失败'
    overview.value = null
    pending.value = []
    todayOrders.value = []
  } finally {
    loading.value = false
  }
}

/** 待办订单快捷动作：接单 / 拒单（危险动作二次确认） */
async function runAction(row: OrderListVO, action: 'accept' | 'reject', success: string): Promise<void> {
  actingId.value = row.id
  try {
    await doOrderAction(row.id, action)
    ElMessage.success(success)
    await load()
  } catch {
    // 拦截器已提示「订单当前状态不允许此操作」等业务错误，刷新以同步状态机
    await load()
  } finally {
    actingId.value = 0
  }
}

const entries = [
  { title: '订单处理', desc: '接单 / 开始 / 完成', path: '/staff/orders', icon: Document },
  { title: '服务排期', desc: '月历查看上门安排', path: '/staff/schedule', icon: Calendar },
  { title: '消息中心', desc: '通知与客户会话', path: '/staff/messages', icon: ChatDotRound },
  { title: '我的评价', desc: '客户评分与口碑', path: '/staff/reviews', icon: Star },
  { title: '个人资料', desc: '资质与可接项目', path: '/staff/profile', icon: User },
]

onMounted(() => void load())
</script>

<template>
  <div class="wb" v-loading="loading" element-loading-background="rgba(247,248,250,0.7)">
    <ErrorState v-if="error && !loading" :text="error" @retry="load" />

    <template v-else>
      <!-- 问候 + 认证状态 -->
      <div class="wb__head">
        <div class="wb__hello">
          <h2 class="wb__title">工作台</h2>
          <p class="aux-text">{{ auth.displayName }}，今天也请好好照顾毛孩子们～</p>
        </div>
        <div class="wb__head-right">
          <span class="aux-text">接单认证</span>
          <StatusTag v-if="overview" kind="cert" :value="overview.certStatus" />
          <el-button size="small" :loading="loading" @click="load">刷新</el-button>
        </div>
      </div>

      <!-- 概览指标（PRD 6.1：待接单/进行中/今日完成/累计完成/累计收入/评分） -->
      <el-row :gutter="16" class="wb__row">
        <el-col :xs="12" :sm="12" :md="8" :lg="4">
          <StatCard label="待接单" :value="overview?.pendingCount ?? 0" suffix="单" color="warning" />
        </el-col>
        <el-col :xs="12" :sm="12" :md="8" :lg="4">
          <StatCard label="进行中" :value="overview?.inProgressCount ?? 0" suffix="单" color="primary" />
        </el-col>
        <el-col :xs="12" :sm="12" :md="8" :lg="4">
          <StatCard label="今日完成" :value="overview?.todayCompletedCount ?? 0" suffix="单" color="success" />
        </el-col>
        <el-col :xs="12" :sm="12" :md="8" :lg="4">
          <StatCard label="累计完成" :value="overview?.completedCount ?? 0" suffix="单" color="info" />
        </el-col>
        <el-col :xs="12" :sm="12" :md="8" :lg="4">
          <StatCard label="累计收入" prefix="¥" :value="fmtMoney(overview?.totalIncome)" color="success">
            <template #extra>已完成订单金额合计</template>
          </StatCard>
        </el-col>
        <el-col :xs="12" :sm="12" :md="8" :lg="4">
          <StatCard label="综合评分" :value="ratingText" suffix="分" color="warning">
            <template #extra>来自客户的星级评价</template>
          </StatCard>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <!-- 待办订单 -->
        <el-col :xs="24" :lg="14">
          <PageCard title="待办订单" class="wb__card">
            <template #header>
              <span class="aux-text">共 {{ pendingCount }} 单待接</span>
              <el-button link type="primary" size="small" @click="router.push('/staff/orders')">
                查看全部订单
              </el-button>
            </template>

            <div v-if="pending.length" class="wb__list">
              <div
                v-for="o in pending"
                :key="o.id"
                class="wb-order"
                role="button"
                tabindex="0"
                @click="router.push(`/staff/orders/${o.id}`)"
                @keydown.enter="router.push(`/staff/orders/${o.id}`)"
              >
                <div class="wb-order__main">
                  <div class="wb-order__title">
                    <span class="wb-order__item">{{ o.itemName || '上门服务' }}</span>
                    <StatusTag kind="order" :value="o.status" />
                  </div>
                  <div class="wb-order__meta aux-text">
                    <span>{{ o.petName || '-' }}{{ o.petBreed ? `（${o.petBreed}）` : '' }}</span>
                    <span>客户 {{ o.ownerName || '-' }}</span>
                    <span>{{ fmtTime(o.appointmentTime) }}</span>
                    <span class="price-text">¥{{ fmtMoney(o.amount) }}</span>
                  </div>
                </div>
                <div class="wb-order__actions" @click.stop>
                  <ConfirmAction
                    title="确认接单该订单？"
                    message="接单后请按预约时间准时上门服务。"
                    @confirm="runAction(o, 'accept', '接单成功，请准时上门')"
                  >
                    <el-button type="primary" size="small" :loading="actingId === o.id">接单</el-button>
                  </ConfirmAction>
                  <ConfirmAction
                    title="确认拒绝该订单？"
                    message="拒单后款项将立即退回用户，订单自动关闭。"
                    danger
                    @confirm="runAction(o, 'reject', '已拒单，款项已退回用户')"
                  >
                    <el-button type="danger" size="small" plain :loading="actingId === o.id">拒单</el-button>
                  </ConfirmAction>
                </div>
              </div>
            </div>

            <EmptyState v-else text="暂无待接单订单" action-text="查看全部订单" @action="router.push('/staff/orders')" />
          </PageCard>
        </el-col>

        <!-- 今日排期 + 快捷入口 -->
        <el-col :xs="24" :lg="10">
          <PageCard title="今日排期" class="wb__card">
            <template #header>
              <el-button link type="primary" size="small" @click="router.push('/staff/schedule')">
                服务排期
              </el-button>
            </template>

            <div v-if="todayOrders.length" class="wb__list">
              <div
                v-for="o in todayOrders"
                :key="o.id"
                class="wb-order wb-order--compact"
                role="button"
                tabindex="0"
                @click="router.push(`/staff/orders/${o.id}`)"
                @keydown.enter="router.push(`/staff/orders/${o.id}`)"
              >
                <span class="wb-order__time">{{ fmtTime(o.appointmentTime).slice(11) }}</span>
                <div class="wb-order__main">
                  <div class="wb-order__title">
                    <span class="wb-order__item">{{ o.itemName || '上门服务' }}</span>
                    <StatusTag kind="order" :value="o.status" />
                  </div>
                  <div class="aux-text">{{ o.petName || '-' }} · 客户 {{ o.ownerName || '-' }}</div>
                </div>
              </div>
            </div>
            <EmptyState v-else text="今天没有上门安排">
              <template #tip>可在服务排期中查看整月安排</template>
            </EmptyState>
          </PageCard>

          <PageCard title="快捷入口" class="wb__card">
            <div class="wb__entries">
              <div
                v-for="e in entries"
                :key="e.path"
                class="wb-entry"
                role="button"
                tabindex="0"
                @click="router.push(e.path)"
                @keydown.enter="router.push(e.path)"
              >
                <span class="wb-entry__icon"><el-icon :size="18"><component :is="e.icon" /></el-icon></span>
                <span class="wb-entry__text">
                  <span class="wb-entry__title">{{ e.title }}</span>
                  <span class="aux-text">{{ e.desc }}</span>
                </span>
              </div>
            </div>
          </PageCard>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<style scoped>
.wb__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.wb__title {
  margin: 0;
  font-size: var(--font-size-title);
  font-weight: 600;
  color: var(--color-text-primary);
}

.wb__hello p {
  margin: 4px 0 0;
}

.wb__head-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.wb__row {
  margin-bottom: 16px;
}

.wb__card {
  margin-bottom: 16px;
}

.wb__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.wb-order {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border-radius: var(--radius-btn, 12px);
  background: var(--color-bg-page);
  cursor: pointer;
  transition: box-shadow 0.15s ease;
}

.wb-order:hover {
  box-shadow: var(--shadow-card);
}

.wb-order--compact {
  align-items: flex-start;
}

.wb-order__main {
  flex: 1 1 auto;
  min-width: 0;
}

.wb-order__title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.wb-order__item {
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
}

.wb-order__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin-top: 6px;
}

.wb-order__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.wb-order__actions .el-button + .el-button {
  margin-left: 0;
}

.wb-order__time {
  flex-shrink: 0;
  width: 46px;
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-primary);
}

.wb__entries {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}

.wb-entry {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border-radius: var(--radius-btn, 12px);
  background: var(--color-bg-page);
  cursor: pointer;
  transition: box-shadow 0.15s ease;
}

.wb-entry:hover {
  box-shadow: var(--shadow-card);
}

.wb-entry__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.wb-entry__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.wb-entry__title {
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
}

@media (max-width: 768px) {
  .wb-order {
    flex-wrap: wrap;
  }

  .wb-order__actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
