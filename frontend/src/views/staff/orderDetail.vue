<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ArrowLeft, ChatDotRound } from '@element-plus/icons-vue'
import PageCard from '@/components/common/PageCard.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ConfirmAction from '@/components/common/ConfirmAction.vue'
import ImageUploader from '@/components/common/ImageUploader.vue'
import OrderStatusSteps from '@/components/business/OrderStatusSteps.vue'
import OrderProgressTimeline from '@/components/business/OrderProgressTimeline.vue'
import { fetchOrderDetail, fetchProgress, doOrderAction, publishProgress } from '@/api/orders'
import { fmtMoney, fmtTime } from '@/utils/constants'
import type { OrderDetailVO, OrderProgress } from '@/types'

/**
 * 订单详情（PRD 6.2 / 6.3）：
 * 状态时间轴 + 客户与宠物信息 + 服务进度 + 服务师动作（接单/拒单/开始/完成/发布进度）
 */
const route = useRoute()
const router = useRouter()

const orderId = computed(() => Number(route.params.id))
const detail = ref<OrderDetailVO | null>(null)
const progress = ref<OrderProgress[]>([])
const loading = ref(true)
const error = ref('')
const acting = ref(false)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [d, p] = await Promise.all([fetchOrderDetail(orderId.value), fetchProgress(orderId.value)])
    detail.value = d
    progress.value = p ?? []
  } catch (e) {
    error.value = (e as Error)?.message || '订单加载失败'
    detail.value = null
    progress.value = []
  } finally {
    loading.value = false
  }
}

/** 状态机时间轴（服务师视角） */
const timeline = computed(() => {
  const d = detail.value
  if (!d) return []
  const list: Array<{ label: string; time?: string; desc?: string }> = []
  list.push({ label: '客户提交预约并支付', time: fmtTime(d.createdAt), desc: d.remark || undefined })
  if (d.status !== 'PENDING_CONFIRM' && d.status !== 'CANCELLED') {
    list.push({ label: '我已接单', time: fmtTime(d.startTime || d.appointmentTime) })
  }
  if (['SERVING', 'PENDING_SETTLE', 'COMPLETED'].includes(d.status)) {
    list.push({ label: '开始服务', time: fmtTime(d.startTime) })
  }
  if (['PENDING_SETTLE', 'COMPLETED'].includes(d.status)) {
    list.push({ label: '我已标记完成', time: fmtTime(d.finishTime) })
  }
  if (d.status === 'COMPLETED') {
    list.push({ label: '客户确认完成', time: fmtTime(d.confirmTime) })
  }
  if (d.status === 'CANCELLED') {
    list.push({
      label: '订单已取消',
      time: fmtTime(d.updatedAt),
      desc: d.cancelReason || '订单已关闭，款项已退回客户',
    })
  }
  return list
})

/** 后端 OrderDetailVO.address 是「省市区+详细地址」拼接字符串（非地址对象），做兼容解析 */
const addressText = computed(() => {
  const raw = detail.value?.address as unknown
  if (!raw) return ''
  if (typeof raw === 'string') return raw
  const obj = raw as { region?: string; detail?: string }
  return `${obj.region ?? ''}${obj.detail ?? ''}`
})

/* ---------- 状态机动作（与后端 availableActions 互为兜底） ---------- */
const status = computed(() => detail.value?.status ?? '')
const canAccept = computed(() => status.value === 'PENDING_CONFIRM')
const canStart = computed(() => status.value === 'CONFIRMED')
const canServe = computed(() => status.value === 'SERVING')
const hasAction = computed(() => canAccept.value || canStart.value || canServe.value)

async function runAction(action: 'accept' | 'reject' | 'start', success: string): Promise<void> {
  if (!detail.value) return
  acting.value = true
  try {
    await doOrderAction(detail.value.id, action)
    ElMessage.success(success)
    await load()
  } catch {
    // 拦截器已提示状态机错误，刷新以同步最新状态
    await load()
  } finally {
    acting.value = false
  }
}

/* ---------- 发布服务进度（仅服务中，PRD 6.3 强制） ---------- */
const publishVisible = ref(false)
const publishRef = ref<FormInstance>()
const publishForm = reactive({ title: '', serviceTime: '', images: [] as string[] })

const publishRules: FormRules = {
  title: [
    { required: true, message: '请填写进度标题', trigger: 'blur' },
    { max: 128, message: '标题不能超过 128 字', trigger: 'blur' },
  ],
  serviceTime: [{ max: 64, message: '服务时间说明不能超过 64 字', trigger: 'blur' }],
}

function openPublish(): void {
  publishForm.title = ''
  publishForm.serviceTime = ''
  publishForm.images = []
  publishVisible.value = true
  void nextTick(() => publishRef.value?.clearValidate())
}

async function submitPublish(): Promise<void> {
  if (!detail.value) return
  const valid = await publishRef.value?.validate().catch(() => false)
  if (!valid) return
  try {
    await publishProgress(detail.value.id, {
      title: publishForm.title,
      images: publishForm.images.length ? publishForm.images : undefined,
      serviceTime: publishForm.serviceTime || undefined,
    })
    ElMessage.success('服务进度已发布，客户可实时查看')
    publishVisible.value = false
    progress.value = (await fetchProgress(detail.value.id)) ?? []
  } catch {
    // 拦截器已提示
  }
}

/* ---------- 标记完成（录入实际服务时间） ---------- */
const finishVisible = ref(false)
const finishRef = ref<FormInstance>()
const finishForm = reactive({ id: 0, actualStartTime: '', actualFinishTime: '' })

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

function openFinish(): void {
  if (!detail.value) return
  finishForm.id = detail.value.id
  finishForm.actualStartTime =
    toPicker(detail.value.startTime) || toPicker(detail.value.appointmentTime) || nowPicker()
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
    ElMessage.success('已标记完成，等待客户确认')
    finishVisible.value = false
    await load()
  } catch {
    await load()
  }
}

function goChat(): void {
  if (!detail.value) return
  router.push(`/staff/chat/${detail.value.userId}`)
}

watch(orderId, () => void load())
onMounted(() => void load())
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
              <el-descriptions-item label="服务时长">
                {{ detail.durationMinutes ? `${detail.durationMinutes} 分钟` : '按次计费' }}
              </el-descriptions-item>
              <el-descriptions-item label="开始时间">{{ fmtTime(detail.startTime) }}</el-descriptions-item>
              <el-descriptions-item label="完成时间">{{ fmtTime(detail.finishTime) }}</el-descriptions-item>
              <el-descriptions-item label="客户备注" :span="2">{{ detail.remark || '无' }}</el-descriptions-item>
              <el-descriptions-item v-if="detail.cancelReason" label="取消原因" :span="2">
                {{ detail.cancelReason }}
              </el-descriptions-item>
            </el-descriptions>

            <h3 class="order-detail__subtitle">宠物信息</h3>
            <div class="order-detail__pet">
              <el-avatar :size="48" class="order-detail__pet-avatar">
                <span>{{ (detail.petName || '宠')[0] }}</span>
              </el-avatar>
              <div>
                <div class="order-detail__pet-name">{{ detail.petName || '-' }}</div>
                <div class="aux-text">{{ detail.petBreed || '未知品种' }}</div>
              </div>
            </div>

            <h3 class="order-detail__subtitle">上门地址</h3>
            <div v-if="addressText" class="order-detail__address">
              <div>{{ detail.contactName || '-' }} · {{ detail.contactPhone || '-' }}</div>
              <div class="aux-text">{{ addressText }}</div>
            </div>
            <div v-else class="aux-text">地址信息未带入，请联系客户确认</div>
          </PageCard>

          <PageCard title="服务进度" class="order-detail__card">
            <template #header>
              <el-button
                v-if="canServe"
                type="primary"
                size="small"
                :icon="ChatDotRound"
                @click="openPublish"
              >
                发布进度
              </el-button>
            </template>
            <OrderProgressTimeline :list="progress" :unread="false" />
          </PageCard>
        </el-col>

        <!-- 客户 + 操作 -->
        <el-col :xs="24" :lg="10">
          <PageCard title="客户信息" class="order-detail__card">
            <div class="order-detail__customer">
              <el-avatar :size="56" class="order-detail__customer-avatar">
                <span>{{ (detail.ownerName || '客')[0] }}</span>
              </el-avatar>
              <div class="order-detail__customer-info">
                <div class="order-detail__customer-name">{{ detail.ownerName || '客户' }}</div>
                <div class="aux-text">{{ detail.contactName || '-' }} · {{ detail.contactPhone || '-' }}</div>
              </div>
            </div>
            <el-button plain style="width: 100%; margin-top: 12px" :icon="ChatDotRound" @click="goChat">
              联系客户
            </el-button>
          </PageCard>

          <PageCard title="订单操作" class="order-detail__card">
            <div class="order-detail__actions">
              <template v-if="canAccept">
                <ConfirmAction
                  title="确认接单该订单？"
                  message="接单后请按预约时间准时上门服务。"
                  @confirm="runAction('accept', '接单成功，请按预约时间上门')"
                >
                  <el-button type="primary" :loading="acting">接单</el-button>
                </ConfirmAction>
                <ConfirmAction
                  title="确认拒绝该订单？"
                  message="拒单后款项将立即退回客户，订单自动关闭。"
                  danger
                  @confirm="runAction('reject', '已拒单，款项已退回客户')"
                >
                  <el-button type="danger" plain :loading="acting">拒单</el-button>
                </ConfirmAction>
              </template>

              <ConfirmAction
                v-if="canStart"
                title="确认开始服务？"
                message="开始后将记录服务开始时间，并通知客户。"
                @confirm="runAction('start', '服务已开始')"
              >
                <el-button type="primary" :loading="acting">开始服务</el-button>
              </ConfirmAction>

              <template v-if="canServe">
                <el-button type="primary" plain @click="openPublish">发布进度</el-button>
                <el-button type="success" @click="openFinish">完成服务</el-button>
              </template>

              <el-button plain :icon="ChatDotRound" @click="goChat">联系客户</el-button>

              <el-button v-if="!hasAction" plain disabled>
                {{ detail.status === 'PENDING_SETTLE' ? '已标记完成，等待客户确认' : '当前状态无需处理' }}
              </el-button>
            </div>

            <el-alert
              v-if="canServe"
              type="info"
              :closable="false"
              show-icon
              title="服务过程中请发布进度"
              description="标题 + 图片 + 服务时间说明，客户查看后会记录已读。"
              class="order-detail__tip"
            />
          </PageCard>
        </el-col>
      </el-row>
    </template>

    <!-- 发布服务进度 -->
    <el-dialog v-model="publishVisible" title="发布服务进度" width="480px" align-center>
      <el-form ref="publishRef" :model="publishForm" :rules="publishRules" label-width="96px">
        <el-form-item label="进度标题" prop="title">
          <el-input
            v-model="publishForm.title"
            maxlength="128"
            show-word-limit
            placeholder="例如：已到家，完成喂食与铲屎"
          />
        </el-form-item>
        <el-form-item label="服务时间" prop="serviceTime">
          <el-input
            v-model="publishForm.serviceTime"
            maxlength="64"
            show-word-limit
            placeholder="例如：14:00-14:30 喂食 + 铲屎（选填）"
          />
        </el-form-item>
        <el-form-item label="现场图片">
          <ImageUploader v-model="publishForm.images" :limit="5" tip="支持 jpg / png / gif / webp，单张不超过 5MB" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPublish">发布</el-button>
      </template>
    </el-dialog>

    <!-- 标记完成 -->
    <el-dialog v-model="finishVisible" title="标记完成" width="460px" align-center>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="提交后订单将进入「待确认完成」"
        description="请核对实际服务开始与结束时间，确认后不可自行修改。"
        class="order-detail__tip"
      />
      <el-form ref="finishRef" :model="finishForm" :rules="finishRules" label-width="110px">
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

.order-detail__customer {
  display: flex;
  align-items: center;
  gap: 12px;
}

.order-detail__customer-avatar {
  background: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
  font-size: 18px;
}

.order-detail__customer-info {
  min-width: 0;
}

.order-detail__customer-name {
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

.order-detail__tip {
  margin-top: 16px;
  border-radius: var(--radius-btn);
}
</style>
