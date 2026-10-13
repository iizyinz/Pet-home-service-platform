<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, ArrowLeft } from '@element-plus/icons-vue'
import PageCard from '@/components/common/PageCard.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import RatingStars from '@/components/common/RatingStars.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ConfirmAction from '@/components/common/ConfirmAction.vue'
import OrderStatusSteps from '@/components/business/OrderStatusSteps.vue'
import OrderProgressTimeline from '@/components/business/OrderProgressTimeline.vue'
import { fetchOrderDetail, fetchProgress, doOrderAction } from '@/api/orders'
import { fmtMoney, fmtTime } from '@/utils/constants'
import type { OrderDetailVO, OrderProgress } from '@/types'

/**
 * 订单详情（PRD 5.5）：
 * 状态时间轴 + 完整信息快照 + 服务进度（查看即已读） + 按状态机可执行动作
 */
const route = useRoute()
const router = useRouter()

const orderId = computed(() => Number(route.params.id))
const detail = ref<OrderDetailVO | null>(null)
const progress = ref<OrderProgress[]>([])
const loading = ref(true)
const error = ref('')
const acting = ref(false)

async function load() {
  loading.value = true
  error.value = ''
  try {
    detail.value = await fetchOrderDetail(orderId.value)
    // 查看进度触发已读（AC-05）
    progress.value = (await fetchProgress(orderId.value)) ?? []
  } catch (e) {
    error.value = (e as Error)?.message || '订单加载失败'
  } finally {
    loading.value = false
  }
}

/** 状态机时间轴（由订单各时间字段组装） */
const timeline = computed(() => {
  const d = detail.value
  if (!d) return []
  const list: Array<{ label: string; time?: string; desc?: string }> = []
  list.push({ label: '提交预约并扣款', time: fmtTime(d.createdAt), desc: d.remark || undefined })
  if (d.status !== 'PENDING_CONFIRM' && d.status !== 'CANCELLED') {
    list.push({ label: '服务师接单', time: fmtTime(d.startTime || d.appointmentTime) })
  }
  if (['SERVING', 'PENDING_SETTLE', 'COMPLETED'].includes(d.status)) {
    list.push({ label: '开始服务', time: fmtTime(d.startTime) })
  }
  if (['PENDING_SETTLE', 'COMPLETED'].includes(d.status)) {
    list.push({ label: '服务师标记完成', time: fmtTime(d.finishTime) })
  }
  if (d.status === 'COMPLETED') {
    list.push({ label: '用户确认完成', time: fmtTime(d.confirmTime) })
  }
  if (d.status === 'CANCELLED') {
    list.push({ label: '订单已取消', time: fmtTime(d.updatedAt), desc: d.cancelReason || '用户取消，金额已退回余额' })
  }
  return list
})

/** 可执行动作（与后端 availableActions 互为兜底） */
const canCancel = computed(() => ['PENDING_CONFIRM', 'CONFIRMED'].includes(detail.value?.status ?? ''))
const canConfirm = computed(() => detail.value?.status === 'PENDING_SETTLE')
const canReview = computed(() => detail.value?.status === 'COMPLETED')
const hasUnread = computed(() => Number(detail.value?.unreadProgress ?? 0) > 0)

async function runAction(action: 'cancel' | 'confirm', success: string) {
  if (!detail.value) return
  acting.value = true
  try {
    await doOrderAction(detail.value.id, action, action === 'cancel' ? { reason: '用户主动取消' } : undefined)
    ElMessage.success(success)
    await load()
  } catch {
    // 拦截器已提示「订单当前状态不允许此操作」等业务错误，这里刷新以同步状态机
    await load()
  } finally {
    acting.value = false
  }
}

function goChat() {
  const peer = detail.value?.staffId
  if (peer) router.push(`/chat/${peer}`)
}

watch(orderId, () => void load())
onMounted(load)
</script>

<template>
  <div class="order-detail" v-loading="loading">
    <EmptyState v-if="error && !loading" :text="error" action-text="重新加载" @action="load" />

    <template v-else-if="detail">
      <div class="order-detail__topbar">
        <el-button link :icon="ArrowLeft" @click="router.back()">返回</el-button>
        <span class="order-detail__no">订单号 {{ detail.orderNo }}</span>
        <StatusTag kind="order" :value="detail.status" />
      </div>

      <PageCard>
        <OrderStatusSteps :status="detail.status" :timeline="timeline" />
      </PageCard>

      <el-row :gutter="16">
        <!-- 服务信息 -->
        <el-col :xs="24" :lg="14">
          <PageCard title="服务信息" class="order-detail__card">
            <el-descriptions :column="2" border size="default">
              <el-descriptions-item label="服务项目">{{ detail.itemName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="订单金额">
                <span class="price-text-lg">¥{{ fmtMoney(detail.amount) }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="预约时间">{{ fmtTime(detail.appointmentTime) }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ fmtTime(detail.createdAt) }}</el-descriptions-item>
              <el-descriptions-item label="备注" :span="2">{{ detail.remark || '无' }}</el-descriptions-item>
              <el-descriptions-item v-if="detail.cancelReason" label="取消原因" :span="2">
                {{ detail.cancelReason }}
              </el-descriptions-item>
            </el-descriptions>

            <h3 class="order-detail__subtitle">宠物信息</h3>
            <div class="order-detail__pet">
              <el-avatar :size="48" class="order-detail__pet-avatar">
                <img v-if="detail.pet?.avatar" :src="detail.pet.avatar" alt="" />
                <span v-else>{{ (detail.pet?.name || '宠')[0] }}</span>
              </el-avatar>
              <div>
                <div class="order-detail__pet-name">{{ detail.pet?.name || '-' }}</div>
                <div class="aux-text">
                  {{ detail.pet?.breed || '未知品种' }}
                  <template v-if="detail.pet?.age"> · {{ detail.pet.age }} 岁</template>
                  <template v-if="detail.pet?.weightKg"> · {{ detail.pet.weightKg }} kg</template>
                </div>
                <div v-if="detail.pet?.specialNeed" class="aux-text">特殊需求：{{ detail.pet.specialNeed }}</div>
              </div>
            </div>

            <h3 class="order-detail__subtitle">上门地址</h3>
            <div v-if="detail.address" class="order-detail__address">
              <div>{{ detail.contactName || '-' }} · {{ detail.contactPhone || '-' }}</div>
              <div class="aux-text">{{ detail.address }}</div>
            </div>
            <div v-else class="aux-text">地址信息未带入</div>
          </PageCard>

          <PageCard title="服务进度" class="order-detail__card">
            <OrderProgressTimeline :list="progress" :unread="hasUnread" />
          </PageCard>
        </el-col>

        <!-- 服务师 + 操作 -->
        <el-col :xs="24" :lg="10">
          <PageCard title="服务师" class="order-detail__card">
            <div class="order-detail__staff">
              <el-avatar :size="56" class="order-detail__staff-avatar">
                <img v-if="detail.staffAvatar" :src="detail.staffAvatar" alt="" />
                <span v-else>{{ (detail.staffName || '师')[0] }}</span>
              </el-avatar>
              <div class="order-detail__staff-info">
                <div class="order-detail__staff-name">{{ detail.staffName || '待分配' }}</div>
                <RatingStars :model-value="Number(detail.staff?.avgRating || 0)" size="small" readonly />
              </div>
              <el-button v-if="detail.staffId" size="small" :icon="ChatDotRound" @click="goChat">私信</el-button>
            </div>
          </PageCard>

          <PageCard title="订单操作" class="order-detail__card">
            <div class="order-detail__actions">
              <template v-if="canCancel">
                <ConfirmAction
                  title="确认取消该订单？"
                  message="取消后金额将退回余额账户，并生成退款流水。"
                  danger
                  @confirm="runAction('cancel', '订单已取消，退款已退回余额')"
                >
                  <el-button type="danger" plain :loading="acting">取消订单</el-button>
                </ConfirmAction>
              </template>

              <el-button
                v-if="canConfirm"
                type="primary"
                :loading="acting"
                @click="runAction('confirm', '已确认完成，可以评价了')"
              >
                确认完成
              </el-button>

              <el-button v-if="canReview" type="primary" @click="router.push(`/orders/${detail.id}/review`)">
                去评价
              </el-button>

              <el-button v-if="detail.staffId" plain @click="goChat">联系服务师</el-button>

              <el-button v-if="!canCancel && !canConfirm && !canReview" plain disabled>
                当前状态无可执行操作
              </el-button>
            </div>
          </PageCard>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<style scoped>
.order-detail__topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.order-detail__no {
  flex: 1;
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
}

.order-detail__card {
  margin-bottom: 16px;
}

.order-detail__subtitle {
  margin: 20px 0 12px;
  font-size: var(--font-size-module);
  font-weight: 600;
  color: var(--color-text-primary);
}

.order-detail__pet {
  display: flex;
  align-items: center;
  gap: 12px;
}

.order-detail__pet-avatar {
  background: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
  font-size: 18px;
}

.order-detail__pet-name {
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
}

.order-detail__address div {
  line-height: 1.6;
}

.order-detail__staff {
  display: flex;
  align-items: center;
  gap: 12px;
}

.order-detail__staff-avatar {
  background: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
  font-size: 18px;
}

.order-detail__staff-info {
  flex: 1;
}

.order-detail__staff-name {
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 4px;
}

.order-detail__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.order-detail__actions .el-button + .el-button {
  margin-left: 0;
}
</style>
