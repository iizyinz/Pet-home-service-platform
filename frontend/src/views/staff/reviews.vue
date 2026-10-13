<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageCard from '@/components/common/PageCard.vue'
import StatCard from '@/components/common/StatCard.vue'
import RatingStars from '@/components/common/RatingStars.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import { usePage } from '@/composables/usePage'
import { fetchMyReviews } from '@/api/staff/workbench'
import { fetchMyProfile } from '@/api/staffs'
import { fmtTime } from '@/utils/constants'
import type { Review, StaffProfile } from '@/types'

/**
 * 我的评价（PRD 6.5 / 5.6）：评分概览 + 星级分布 + 评价卡片流分页。
 * 数据：GET /api/reviews?staffId=（仅 status=1 已显示评价）
 * 后端无评价统计接口，星级分布用近 100 条样本在前端聚合。
 */
const router = useRouter()

const profile = ref<StaffProfile | null>(null)
const sample = ref<Review[]>([])
const statsLoading = ref(true)
const bootError = ref('')

/** 服务人员档案主键 = sys_user.id（与订单 staff_id 同一取值） */
const staffId = ref(0)

/** 列表入参（分页由 usePage 统一维护） */
interface ReviewQuery {
  page?: number
  size?: number
}

const { list, total, page, size, loading, error, search, refresh, changePage, changeSize } = usePage<
  Review,
  ReviewQuery
>((q) => fetchMyReviews(staffId.value, q.page ?? 1, q.size ?? 10), { immediate: false, size: 10 })

async function init(): Promise<void> {
  statsLoading.value = true
  bootError.value = ''
  try {
    const p = await fetchMyProfile()
    profile.value = p
    staffId.value = Number(p?.userId ?? p?.id ?? 0)
    if (!staffId.value) {
      throw new Error('未找到服务人员档案，请联系管理员')
    }
    sample.value = (await fetchMyReviews(staffId.value, 1, 100))?.list ?? []
    await search()
  } catch (e) {
    bootError.value = (e as Error)?.message || '评价加载失败'
    list.value = []
    total.value = 0
  } finally {
    statsLoading.value = false
  }
}

/** 评分概览：优先档案里的聚合评分，样本兜底 */
const avgRating = computed(() => {
  const fromProfile = Number(profile.value?.avgRating ?? 0)
  if (fromProfile > 0) return fromProfile
  if (!sample.value.length) return 0
  const sum = sample.value.reduce((acc, r) => acc + Number(r.rating || 0), 0)
  return Math.round((sum / sample.value.length) * 10) / 10
})

const reviewCount = computed(() => total.value || sample.value.length)

/** 五星分布（5 → 1） */
const dist = computed(() => {
  const base = sample.value.length || 1
  return [5, 4, 3, 2, 1].map((star) => {
    const count = sample.value.filter((r) => Number(r.rating) === star).length
    return { star, count, percent: Math.round((count / base) * 100) }
  })
})

/** 好评率（4 星及以上） */
const positiveRate = computed(() => {
  const positive = sample.value.filter((r) => Number(r.rating) >= 4).length
  return sample.value.length ? Math.round((positive / sample.value.length) * 100) : 0
})

/** 后端 images 为 JSON 数组字符串，做兼容解析 */
function imagesOf(val?: string[] | string): string[] {
  if (!val) return []
  if (Array.isArray(val)) return val
  try {
    const parsed: unknown = JSON.parse(val)
    return Array.isArray(parsed) ? (parsed as string[]) : [val]
  } catch {
    return [val]
  }
}

onMounted(() => void init())
</script>

<template>
  <div class="rv">
    <ErrorState v-if="bootError" :text="bootError" @retry="init" />

    <template v-else>
      <!-- 评分概览 -->
      <el-row :gutter="16" class="rv__row">
        <el-col :xs="24" :md="8">
          <PageCard title="综合评分" class="rv__card">
            <div v-loading="statsLoading" class="rv__score">
              <span class="rv__score-value">{{ avgRating.toFixed(1) }}</span>
              <RatingStars :model-value="Math.round(avgRating)" size="large" readonly />
              <span class="aux-text">累计 {{ reviewCount }} 条评价</span>
            </div>
          </PageCard>
        </el-col>

        <el-col :xs="24" :md="8">
          <StatCard
            label="好评率"
            :value="positiveRate"
            suffix="%"
            color="success"
            :loading="statsLoading"
            class="rv__card"
          >
            <template #extra>4 星及以上评价占比</template>
          </StatCard>
        </el-col>

        <el-col :xs="24" :md="8">
          <StatCard
            label="累计完成订单"
            :value="profile?.completedCount ?? 0"
            suffix="单"
            color="primary"
            :loading="statsLoading"
            class="rv__card"
          >
            <template #extra>来自服务档案统计</template>
          </StatCard>
        </el-col>
      </el-row>

      <!-- 星级分布 + 列表 -->
      <el-row :gutter="16">
        <el-col :xs="24" :lg="8">
          <PageCard title="星级分布" class="rv__card">
            <div v-loading="statsLoading" class="rv__dist">
              <div v-for="d in dist" :key="d.star" class="rv__dist-row">
                <span class="rv__dist-star">{{ d.star }} 星</span>
                <el-progress
                  :percentage="d.percent"
                  :stroke-width="10"
                  :color="d.star >= 4 ? 'var(--color-success)' : 'var(--color-warning)'"
                  class="rv__dist-bar"
                />
                <span class="aux-text rv__dist-count">{{ d.count }} 条</span>
              </div>
              <p class="aux-text rv__dist-tip">分布基于近 100 条评价样本统计</p>
            </div>
          </PageCard>
        </el-col>

        <el-col :xs="24" :lg="16">
          <PageCard title="评价列表" class="rv__card">
            <template #header>
              <div class="rv__head-actions">
                <span class="aux-text">共 {{ total }} 条</span>
                <el-button size="small" :loading="loading" @click="refresh">刷新</el-button>
              </div>
            </template>

            <div v-loading="loading" class="rv__body">
              <ErrorState v-if="error" :text="error" @retry="refresh" />

              <EmptyState
                v-else-if="!list.length"
                text="还没有收到评价"
                action-text="去处理订单"
                @action="router.push('/staff/orders')"
              />

              <div v-else class="rv__list">
                <article v-for="r in list" :key="r.id" class="rv-item">
                  <div class="rv-item__head">
                    <el-avatar :size="36" class="rv-item__avatar">
                      <span>{{ (r.userNickname || '客')[0] }}</span>
                    </el-avatar>
                    <div class="rv-item__user">
                      <div class="rv-item__name">{{ r.userNickname || '匿名客户' }}</div>
                      <span class="aux-text">{{ fmtTime(r.createdAt) }}</span>
                    </div>
                    <RatingStars :model-value="Number(r.rating)" size="small" readonly />
                  </div>

                  <p class="rv-item__content">{{ r.content || '客户未填写文字评价' }}</p>

                  <div v-if="imagesOf(r.images).length" class="rv-item__images">
                    <el-image
                      v-for="(img, i) in imagesOf(r.images)"
                      :key="i"
                      :src="img"
                      :preview-src-list="imagesOf(r.images)"
                      :initial-index="i"
                      fit="cover"
                      class="rv-item__img"
                      preview-teleported
                    />
                  </div>

                  <div class="rv-item__foot">
                    <span class="aux-text">关联订单 #{{ r.orderId ?? '-' }}</span>
                    <el-button
                      v-if="r.orderId"
                      link
                      type="primary"
                      size="small"
                      @click="router.push(`/staff/orders/${r.orderId}`)"
                    >
                      查看订单
                    </el-button>
                  </div>
                </article>
              </div>
            </div>

            <div v-if="total > 0" class="rv__pager">
              <el-pagination
                background
                layout="total, sizes, prev, pager, next, jumper"
                :total="total"
                :current-page="page"
                :page-size="size"
                :page-sizes="[10, 20, 50]"
                @current-change="changePage"
                @size-change="changeSize"
              />
            </div>
          </PageCard>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<style scoped>
.rv__row {
  margin-bottom: 16px;
}

.rv__card {
  margin-bottom: 16px;
}

.rv__score {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
}

.rv__score-value {
  font-size: 40px;
  font-weight: 600;
  line-height: 1.2;
  color: var(--color-warning);
}

.rv__dist {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rv__dist-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rv__dist-star {
  width: 42px;
  font-size: var(--font-size-aux);
  color: var(--color-text-secondary);
}

.rv__dist-bar {
  flex: 1 1 auto;
}

.rv__dist-count {
  width: 48px;
  text-align: right;
}

.rv__dist-tip {
  margin: 4px 0 0;
}

.rv__head-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.rv__body {
  min-height: 200px;
}

.rv__list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.rv-item {
  padding: 14px;
  border-radius: var(--radius-btn, 12px);
  background: var(--color-bg-page);
}

.rv-item__head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.rv-item__avatar {
  background: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
}

.rv-item__user {
  flex: 1 1 auto;
  min-width: 0;
}

.rv-item__name {
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
}

.rv-item__content {
  margin: 10px 0 0;
  font-size: var(--font-size-body);
  line-height: 1.7;
  color: var(--color-text-secondary);
}

.rv-item__images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.rv-item__img {
  width: 72px;
  height: 72px;
  border-radius: var(--radius-tag, 8px);
  overflow: hidden;
}

.rv-item__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 10px;
}

.rv__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 768px) {
  .rv__pager {
    justify-content: center;
  }
}
</style>
