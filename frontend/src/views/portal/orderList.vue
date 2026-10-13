<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ConfirmAction from '@/components/common/ConfirmAction.vue'
import { fetchOrders, doOrderAction, type OrderQuery } from '@/api/orders'
import { usePage } from '@/composables/usePage'
import { ORDER_TABS, ORDER_STATUS, fmtMoney, fmtTime } from '@/utils/constants'
import type { OrderListVO } from '@/types'

/**
 * 我的订单（PRD 5.5）：
 * 状态 Tab 全量筛选 + 订单卡片列表（订单号/项目/服务师/宠物/金额/预约时间/状态）
 * 行内动作严格按后端状态机（用户侧）：待确认/已确认可取消，待确认完成可确认，已完成去评价
 */
const router = useRouter()

const { list, total, page, size, loading, error, query, search, refresh, changePage, changeSize } = usePage<
  OrderListVO,
  OrderQuery
>(fetchOrders, { defaultQuery: { status: 'ALL' as const }, size: 10 })

/** 正在提交状态机动作的订单（按钮 loading 定位） */
const actingId = ref(0)

/** 空态文案随 Tab 变化 */
const emptyText = computed(() => {
  const v = query.status || 'ALL'
  if (v === 'ALL') return '暂无订单，去预约一次上门服务吧'
  return `暂无「${ORDER_STATUS[v]?.label ?? v}」状态的订单`
})

function onTab(v: string) {
  if (query.status === v) return
  query.status = v as OrderQuery['status']
  void search()
}

/** 取消订单（仅待确认/已确认，后端退款并写 REFUND 流水） */
async function cancelOrder(o: OrderListVO) {
  actingId.value = o.id
  try {
    await doOrderAction(o.id, 'cancel', { reason: '用户主动取消' })
    ElMessage.success('订单已取消，退款已退回余额')
    await refresh()
  } catch {
    // 拦截器已提示业务错误；刷新以同步状态机
    await refresh()
  } finally {
    actingId.value = 0
  }
}

/** 确认完成（待确认完成 -> 已完成，随后可评价） */
async function confirmOrder(o: OrderListVO) {
  actingId.value = o.id
  try {
    await doOrderAction(o.id, 'confirm')
    ElMessage.success('已确认完成，可以评价了')
    await refresh()
  } catch {
    await refresh()
  } finally {
    actingId.value = 0
  }
}

function goDetail(o: OrderListVO) {
  router.push(`/orders/${o.id}`)
}

function goReview(o: OrderListVO) {
  router.push(`/orders/${o.id}/review`)
}
</script>

<template>
  <div class="order-list">
    <div class="order-list__topbar">
      <span class="order-list__title">我的订单</span>
      <span class="aux-text order-list__count">共 {{ total }} 单</span>
      <div class="order-list__topbar-actions">
        <el-button size="small" plain @click="router.push('/my-reviews')">我的评价</el-button>
        <el-button size="small" type="primary" @click="router.push('/services')">去预约</el-button>
      </div>
    </div>

    <!-- 状态 Tab -->
    <PageCard class="order-list__tabs-card">
      <div class="order-tabs">
        <button
          v-for="t in ORDER_TABS"
          :key="t.value"
          type="button"
          class="order-tabs__item"
          :class="{ 'is-active': (query.status || 'ALL') === t.value }"
          @click="onTab(t.value)"
        >
          {{ t.label }}
        </button>
      </div>
    </PageCard>

    <!-- 列表区：加载 / 错误 / 空 / 卡片 -->
    <div class="order-list__body" v-loading="loading && !list.length" element-loading-text="加载订单中…">
      <ErrorState v-if="error && !loading" :text="error" @retry="refresh" />

      <EmptyState v-else-if="!loading && !list.length" :text="emptyText" action-text="去预约服务" @action="router.push('/services')">
        <template #tip>下单后可在此跟踪服务进度、确认完成并评价</template>
      </EmptyState>

      <template v-else>
        <PageCard v-for="o in list" :key="o.id" class="order-card" @click="goDetail(o)">
          <template #header>
            <div class="order-card__head">
              <span class="order-card__no">订单号 {{ o.orderNo }}</span>
              <span v-if="Number(o.unreadProgress || 0) > 0" class="unread-dot">进度</span>
              <span class="aux-text order-card__time">{{ fmtTime(o.createdAt) }} 下单</span>
              <StatusTag kind="order" :value="o.status" />
            </div>
          </template>

          <div class="order-card__main">
            <div class="order-card__info">
              <div class="order-card__title-row">
                <span class="order-card__item">{{ o.itemName || '上门服务' }}</span>
                <span class="price-text">¥{{ fmtMoney(o.amount) }}</span>
              </div>
              <div class="order-card__meta aux-text">
                <span>
                  宠物：{{ o.petName || '-' }}<template v-if="o.petBreed">（{{ o.petBreed }}）</template>
                </span>
                <span>服务师：{{ o.staffName || '待分配' }}</span>
              </div>
              <div class="order-card__meta aux-text">
                <span>预约时间：{{ fmtTime(o.appointmentTime) }}</span>
                <span v-if="o.status === 'CANCELLED' && o.cancelReason" class="order-card__reason">
                  取消原因：{{ o.cancelReason }}
                </span>
              </div>
            </div>

            <div class="order-card__actions" @click.stop>
              <!-- 取消：仅待确认/已确认 -->
              <ConfirmAction
                v-if="o.status === 'PENDING_CONFIRM' || o.status === 'CONFIRMED'"
                title="确认取消该订单？"
                message="取消后金额将退回余额账户，并生成退款流水。"
                danger
                @confirm="cancelOrder(o)"
              >
                <el-button type="danger" plain size="small" :loading="actingId === o.id">取消订单</el-button>
              </ConfirmAction>

              <!-- 确认完成：待确认完成 -->
              <el-button
                v-if="o.status === 'PENDING_SETTLE'"
                type="primary"
                size="small"
                :loading="actingId === o.id"
                @click="confirmOrder(o)"
              >
                确认完成
              </el-button>

              <!-- 去评价：已完成 -->
              <el-button v-if="o.status === 'COMPLETED'" type="primary" size="small" @click="goReview(o)">
                去评价
              </el-button>

              <el-button plain size="small" @click="goDetail(o)">查看详情</el-button>
            </div>
          </div>
        </PageCard>

        <div v-if="total > size" class="order-list__pager">
          <el-pagination
            :current-page="page"
            :page-size="size"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            background
            @current-change="changePage"
            @size-change="changeSize"
          />
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.order-list__topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.order-list__title {
  font-size: var(--font-size-module);
  font-weight: 600;
  color: var(--color-text-primary);
}

.order-list__count {
  flex: 1;
}

.order-list__topbar-actions {
  display: flex;
  gap: 8px;
}

.order-list__topbar-actions .el-button + .el-button {
  margin-left: 0;
}

.order-list__tabs-card {
  margin-bottom: 12px;
}

.order-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.order-tabs__item {
  padding: 6px 14px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-card);
  color: var(--color-text-secondary);
  font-size: var(--font-size-body);
  cursor: pointer;
  transition: all 0.15s ease;
}

.order-tabs__item:hover {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-5);
}

.order-tabs__item.is-active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
  font-weight: 500;
}

.order-list__body {
  min-height: 200px;
}

.order-card {
  margin-bottom: 12px;
  cursor: pointer;
  transition: box-shadow 0.15s ease;
}

.order-card:hover {
  box-shadow: var(--shadow-hover, 0 6px 16px rgba(0, 0, 0, 0.08));
}

.order-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  width: 100%;
}

.order-card__no {
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
}

.order-card__time {
  flex: 1;
  text-align: right;
}

.order-card__main {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.order-card__info {
  flex: 1 1 260px;
  min-width: 0;
}

.order-card__title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.order-card__item {
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
}

.order-card__meta {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  line-height: 1.8;
}

.order-card__reason {
  color: var(--color-danger);
}

.order-card__actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  cursor: default;
}

.order-card__actions .el-button,
.order-card__actions .el-button + .el-button {
  margin-left: 0;
}

.order-list__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .order-card__time {
    flex: 1 1 100%;
    text-align: left;
    order: 3;
  }

  .order-card__actions {
    width: 100%;
    justify-content: flex-end;
  }

  .order-list__pager {
    justify-content: center;
  }
}
</style>
