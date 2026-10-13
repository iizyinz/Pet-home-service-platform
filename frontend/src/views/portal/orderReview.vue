<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import PageCard from '@/components/common/PageCard.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import RatingStars from '@/components/common/RatingStars.vue'
import ImageUploader from '@/components/common/ImageUploader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { fetchOrderDetail } from '@/api/orders'
import { submitReview } from '@/api/reviews'
import { fmtMoney, fmtTime } from '@/utils/constants'
import type { OrderDetailVO } from '@/types'

/**
 * 评价订单（PRD 5.6）：
 * 仅「已完成」且未评价的订单可提交；星级 1-5 必填、内容最多 500 字（对齐后端 ReviewRequest）、
 * 图片经 /files/upload 上传后提交 URL；一单一评，重复提交由后端 2005 拒绝
 */
const route = useRoute()
const router = useRouter()

const orderId = computed(() => Number(route.params.id))
const detail = ref<OrderDetailVO | null>(null)
const loading = ref(true)
const error = ref('')
const submitting = ref(false)

const rating = ref(0)
const content = ref('')
const images = ref<string[]>([])

/** 后端约束：status === COMPLETED 且 reviewed !== true */
const canSubmit = computed(() => detail.value?.status === 'COMPLETED' && !detail.value?.reviewed)

const ratingText = computed(() => ['', '非常差', '较差', '一般', '满意', '非常满意'][rating.value] ?? '')

async function load() {
  loading.value = true
  error.value = ''
  try {
    detail.value = await fetchOrderDetail(orderId.value)
  } catch (e) {
    error.value = (e as Error)?.message || '订单加载失败'
  } finally {
    loading.value = false
  }
}

async function onSubmit() {
  if (!canSubmit.value || !detail.value) return
  if (rating.value < 1 || rating.value > 5) {
    ElMessage.warning('请先点击星星评分（1-5 星）')
    return
  }
  if (content.value.length > 500) {
    ElMessage.warning('评价内容最多 500 字')
    return
  }
  submitting.value = true
  try {
    await submitReview({
      orderId: detail.value.id,
      rating: rating.value,
      content: content.value.trim() || undefined,
      images: images.value.length ? [...images.value] : undefined,
    })
    ElMessage.success('评价提交成功，感谢您的反馈')
    router.replace('/orders')
  } catch {
    // 拦截器已提示（重复评价 2005 / 非已完成订单 2006 等），这里保持在本页可重试
  } finally {
    submitting.value = false
  }
}

watch(orderId, () => void load())
onMounted(load)
</script>

<template>
  <div class="order-review" v-loading="loading">
    <div class="order-review__topbar">
      <el-button link :icon="ArrowLeft" @click="router.back()">返回</el-button>
      <span class="order-review__title">评价订单</span>
    </div>

    <EmptyState v-if="error && !loading" :text="error" action-text="重新加载" @action="load" />

    <template v-else-if="detail">
      <!-- 订单摘要 -->
      <PageCard title="订单摘要" class="order-review__card">
        <div class="order-review__summary">
          <div class="order-review__summary-head">
            <span class="order-review__no">订单号 {{ detail.orderNo }}</span>
            <StatusTag kind="order" :value="detail.status" />
          </div>
          <el-descriptions :column="1" border size="default" class="order-review__desc">
            <el-descriptions-item label="服务项目">{{ detail.itemName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="服务师">{{ detail.staffName || '待分配' }}</el-descriptions-item>
            <el-descriptions-item label="预约时间">{{ fmtTime(detail.appointmentTime) }}</el-descriptions-item>
            <el-descriptions-item label="订单金额">
              <span class="price-text">¥{{ fmtMoney(detail.amount) }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </PageCard>

      <!-- 不可评价 / 已评价提示 -->
      <el-alert
        v-if="detail.status !== 'COMPLETED'"
        class="order-review__alert"
        type="warning"
        show-icon
        :closable="false"
        title="当前订单不可评价"
        description="仅「已完成」状态的订单可以提交评价，服务完成并确认后即可回来评价。"
      />
      <el-alert
        v-else-if="detail.reviewed"
        class="order-review__alert"
        type="success"
        show-icon
        :closable="false"
        title="该订单已评价"
        description="感谢您的反馈，服务师会看到您的评价，可在「我的评价」中查看。"
      />

      <!-- 评价表单 -->
      <PageCard v-if="canSubmit" title="评价内容" class="order-review__card">
        <div class="review-form">
          <div class="review-form__row">
            <label class="review-form__label">星级评分 <span class="review-form__required">*</span></label>
            <div class="review-form__stars">
              <RatingStars v-model="rating" size="large" />
              <span v-if="rating" class="review-form__rating-text">{{ rating }} 星 · {{ ratingText }}</span>
              <span v-else class="aux-text">点击星星打分（1-5 星必填）</span>
            </div>
          </div>

          <div class="review-form__row review-form__row--column">
            <label class="review-form__label">评价内容</label>
            <el-input
              v-model="content"
              type="textarea"
              :rows="4"
              maxlength="500"
              show-word-limit
              placeholder="说说本次上门服务的感受吧（选填，最多 500 字）"
            />
          </div>

          <div class="review-form__row review-form__row--column">
            <label class="review-form__label">服务图片</label>
            <ImageUploader v-model="images" :limit="3" tip="最多上传 3 张服务实拍（选填），支持 jpg / png / gif / webp，单张不超过 5MB" />
          </div>

          <div class="review-form__footer">
            <el-button type="primary" size="large" :loading="submitting" @click="onSubmit">提交评价</el-button>
            <p class="aux-text review-form__note">评价一经提交不可修改，请真实客观地反馈服务体验。</p>
          </div>
        </div>
      </PageCard>

      <!-- 返回入口 -->
      <div class="order-review__actions">
        <el-button @click="router.push(`/orders/${detail.id}`)">返回订单详情</el-button>
        <el-button @click="router.push('/orders')">返回订单列表</el-button>
        <el-button v-if="detail.reviewed" type="primary" plain @click="router.push('/my-reviews')">
          查看我的评价
        </el-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.order-review__topbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.order-review__title {
  font-size: var(--font-size-module);
  font-weight: 600;
  color: var(--color-text-primary);
}

.order-review__card {
  margin-bottom: 16px;
}

.order-review__alert {
  margin-bottom: 16px;
  border-radius: var(--radius-btn);
}

.order-review__summary-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.order-review__no {
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
}

.review-form__row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.review-form__row--column {
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
}

.review-form__label {
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
  white-space: nowrap;
}

.review-form__required {
  color: var(--color-danger);
}

.review-form__stars {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.review-form__rating-text {
  font-size: var(--font-size-body);
  color: var(--color-warning);
  font-weight: 500;
}

.review-form__footer {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.review-form__footer .el-button {
  width: 100%;
  max-width: 320px;
}

.review-form__note {
  margin: 0;
  text-align: center;
}

.order-review__actions {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 12px;
}

.order-review__actions .el-button + .el-button {
  margin-left: 0;
}

@media (max-width: 768px) {
  .review-form__footer .el-button {
    max-width: none;
  }
}
</style>
