<script setup lang="ts">
import { useRouter } from 'vue-router'
import PageCard from '@/components/common/PageCard.vue'
import RatingStars from '@/components/common/RatingStars.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { fetchMyReviewItems, type MyReviewItem, type MyReviewQuery } from '@/api/reviews'
import { usePage } from '@/composables/usePage'
import { fmtMoney, fmtTime } from '@/utils/constants'

/**
 * 我的评价（PRD 5.6）：
 * 我发出的评价分页列表（星级/内容/图片/时间 + 订单摘要）。
 * 后端仅提供提交与查询，不支持用户删除/追评，故本页只读。
 */
const router = useRouter()

const { list, total, page, size, loading, error, refresh, changePage, changeSize } = usePage<MyReviewItem, MyReviewQuery>(
  fetchMyReviewItems,
  { size: 10 },
)
</script>

<template>
  <div class="my-reviews">
    <div class="my-reviews__topbar">
      <span class="my-reviews__title">我的评价</span>
      <span class="aux-text my-reviews__count">共 {{ total }} 条</span>
      <el-button size="small" plain @click="router.push('/orders')">我的订单</el-button>
    </div>

    <div class="my-reviews__body" v-loading="loading && !list.length" element-loading-text="加载评价中…">
      <ErrorState v-if="error && !loading" :text="error" @retry="refresh" />

      <EmptyState
        v-else-if="!loading && !list.length"
        text="还没有发表过评价"
        action-text="去订单列表评价"
        @action="router.push('/orders')"
      >
        <template #tip>服务完成后即可对服务师评价，一单一评</template>
      </EmptyState>

      <template v-else>
        <PageCard v-for="item in list" :key="item.review.id" class="review-card">
          <!-- 头部：服务师 + 星级 + 时间 -->
          <div class="review-card__head">
            <el-avatar :size="40" class="review-card__avatar">
              <img v-if="item.staffAvatar" :src="item.staffAvatar" alt="" />
              <span v-else>{{ (item.staffName || '师')[0] }}</span>
            </el-avatar>
            <div class="review-card__head-main">
              <div class="review-card__name-row">
                <span class="review-card__name">{{ item.staffName || '服务师' }}</span>
                <RatingStars :model-value="Number(item.review.rating || 0)" size="small" readonly />
              </div>
              <div class="aux-text">
                {{ fmtTime(item.review.createdAt) }}
                <template v-if="item.orderNo"> · 订单 {{ item.orderNo }}</template>
              </div>
            </div>
          </div>

          <!-- 订单摘要 -->
          <div class="review-card__order aux-text">
            <span v-if="item.itemName">服务项目：{{ item.itemName }}</span>
            <span v-if="item.amount !== undefined">金额：¥{{ fmtMoney(item.amount) }}</span>
            <span v-else-if="item.orderMissing">订单信息已不可用</span>
            <el-button
              v-if="!item.orderMissing"
              link
              type="primary"
              size="small"
              @click="router.push(`/orders/${item.review.orderId}`)"
            >
              查看订单
            </el-button>
          </div>

          <!-- 评价内容 -->
          <p class="review-card__content">{{ item.review.content || '（未填写文字评价）' }}</p>

          <!-- 图片 -->
          <div v-if="item.images.length" class="review-card__images">
            <el-image
              v-for="(img, idx) in item.images"
              :key="img + idx"
              class="review-card__image"
              :src="img"
              fit="cover"
              :preview-src-list="item.images"
              :initial-index="idx"
              :preview-teleported="true"
            />
          </div>
        </PageCard>

        <p class="my-reviews__note aux-text">
          评价一经提交不可修改或删除；如遇服务问题，请通过订单详情联系服务师或联系平台客服处理。
        </p>

        <div v-if="total > size" class="my-reviews__pager">
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
.my-reviews__topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.my-reviews__title {
  font-size: var(--font-size-module);
  font-weight: 600;
  color: var(--color-text-primary);
}

.my-reviews__count {
  flex: 1;
}

.my-reviews__body {
  min-height: 200px;
}

.review-card {
  margin-bottom: 12px;
}

.review-card__head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.review-card__avatar {
  background: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
  font-size: 16px;
  flex-shrink: 0;
}

.review-card__head-main {
  flex: 1;
  min-width: 0;
}

.review-card__name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 2px;
}

.review-card__name {
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
}

.review-card__order {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.review-card__content {
  margin: 0 0 12px;
  font-size: var(--font-size-body);
  line-height: 1.8;
  color: var(--color-text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
}

.review-card__images {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.review-card__image {
  width: 96px;
  height: 96px;
  border-radius: 12px;
  overflow: hidden;
  flex-shrink: 0;
}

.my-reviews__note {
  margin: 4px 0 12px;
  line-height: 1.6;
}

.my-reviews__pager {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .my-reviews__pager {
    justify-content: center;
  }

  .review-card__image {
    width: 80px;
    height: 80px;
  }
}
</style>
