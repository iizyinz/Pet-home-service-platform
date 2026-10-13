<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ChatDotRound, Refresh, Star, StarFilled } from '@element-plus/icons-vue'
import PageCard from '@/components/common/PageCard.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import RatingStars from '@/components/common/RatingStars.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import { usePage } from '@/composables/usePage'
import { fetchServiceItem, fetchServiceItems } from '@/api/catalog'
import { fetchStaff, fetchStaffReviews, toggleFavorite } from '@/api/staffs'
import { fmtMoney, fmtTime } from '@/utils/constants'
import type { Review, ServiceItem, StaffDetailVO } from '@/types'

/**
 * 服务人员详情（PRD 5.3）：
 * 资料 / 资质证书 / 擅长品种 / 收藏切换 / 服务评价分页 / 预约入口。
 * 后端未返回该服务师的可接项目（StaffDetailVO.itemIds/itemNames 未填充），
 * 因此「可预约服务」按平台在架项目提供选择，预约时同时携带 staffId。
 */
const route = useRoute()
const router = useRouter()

const staffId = computed(() => Number(route.params.id))
/** 从服务详情跳入时已选好的服务项目 */
const carryItemId = computed(() => Number(route.query.itemId || 0) || 0)

const detail = ref<StaffDetailVO | null>(null)
const picked = ref<ServiceItem | null>(null)
const loading = ref(true)
const error = ref('')
const favorited = ref(false)
const acting = ref(false)

/* ---------- 服务评价（分页） ---------- */
interface ReviewQuery {
  page?: number
  size?: number
}

const {
  list: reviews,
  total: reviewTotal,
  page: reviewPage,
  size: reviewSize,
  loading: reviewLoading,
  error: reviewError,
  search: searchReviews,
  refresh: refreshReviews,
  changePage: changeReviewPage,
  changeSize: changeReviewSize,
} = usePage<Review, ReviewQuery>((q) => fetchStaffReviews(staffId.value, q.page ?? 1, q.size ?? 5), {
  immediate: false,
  size: 5,
})

/* ---------- 可预约服务（平台在架项目） ---------- */
const services = ref<ServiceItem[]>([])
const servicesLoading = ref(true)
const servicesError = ref('')

async function loadServices(): Promise<void> {
  servicesLoading.value = true
  servicesError.value = ''
  try {
    const res = await fetchServiceItems({ page: 1, size: 20 })
    services.value = res?.list ?? []
  } catch (e) {
    servicesError.value = (e as Error)?.message || '服务项目加载失败'
    services.value = []
  } finally {
    servicesLoading.value = false
  }
}

/** 已选服务项目单独加载：失效（下架/ID 不存在）时不影响服务师详情展示 */
async function loadPicked(): Promise<void> {
  if (!carryItemId.value) {
    picked.value = null
    return
  }
  try {
    picked.value = await fetchServiceItem(carryItemId.value)
  } catch {
    picked.value = null
  }
}

async function load(): Promise<void> {
  if (!staffId.value || Number.isNaN(staffId.value)) {
    error.value = '服务人员参数不合法'
    loading.value = false
    detail.value = null
    return
  }
  loading.value = true
  error.value = ''
  try {
    const [staff] = await Promise.all([
      fetchStaff(staffId.value),
      loadPicked(),
      searchReviews(),
    ])
    detail.value = staff ?? null
    favorited.value = staff?.favorited === true
    if (!detail.value) error.value = '该服务师不存在或已下架'
  } catch (e) {
    detail.value = null
    error.value = (e as Error)?.message || '服务人员详情加载失败'
  } finally {
    loading.value = false
  }
}

/* ---------- 收藏 ---------- */
async function onFavorite(): Promise<void> {
  if (!detail.value || acting.value) return
  acting.value = true
  try {
    const res = await toggleFavorite(detail.value.id)
    favorited.value = res?.favorited ?? false
    ElMessage.success(favorited.value ? '已收藏，可在「我的收藏」查看' : '已取消收藏')
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    acting.value = false
  }
}

/* ---------- 预约 / 私信 ---------- */
function book(itemId: number): void {
  if (!staffId.value) return
  router.push({
    path: '/orders/create',
    query: { itemId: String(itemId), staffId: String(staffId.value) },
  })
}

function goChat(): void {
  router.push(`/chat/${staffId.value}`)
}

function goBack(): void {
  if (window.history.length > 1) router.back()
  else router.push('/providers')
}

/** 后端评价 images 为 JSON 数组字符串，做兼容解析 */
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

const serviceAnchor = 'book-services'

/** 擅长品种 / 资质证书（统一兜底为空数组，避免模板里反复判空） */
const goodAtBreeds = computed<string[]>(() => detail.value?.goodAtBreeds ?? [])
const certImages = computed<string[]>(() => detail.value?.certImages ?? [])

/** 空评价时引导到预约区 */
function scrollToBook(): void {
  document.getElementById(serviceAnchor)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

watch(staffId, () => void load())
onMounted(() => {
  void load()
  void loadServices()
})
</script>

<template>
  <div class="pd" v-loading="loading">
    <div class="pd__topbar">
      <el-button link :icon="ArrowLeft" @click="goBack">返回</el-button>
      <span class="pd__crumb">服务人员详情</span>
      <el-button
        v-if="detail"
        size="small"
        plain
        :type="favorited ? 'warning' : 'default'"
        :loading="acting"
        @click="onFavorite"
      >
        <el-icon><StarFilled v-if="favorited" /><Star v-else /></el-icon>
        &nbsp;{{ favorited ? '已收藏' : '收藏' }}
      </el-button>
    </div>

    <EmptyState v-if="error && !loading" :text="error" action-text="重新加载" @action="load">
      <template #tip>服务师可能已下架，可返回列表选择其他服务师</template>
    </EmptyState>

    <template v-else-if="detail">
      <el-row :gutter="16">
        <!-- 资料 + 评价 -->
        <el-col :xs="24" :lg="15">
          <PageCard class="pd__card">
            <div class="pd__head">
              <el-avatar :size="72" class="pd__avatar">
                <img v-if="detail.avatar" :src="detail.avatar" alt="" />
                <span v-else>{{ (detail.nickname || '师')[0] }}</span>
              </el-avatar>

              <div class="pd__head-info">
                <div class="pd__name">
                  {{ detail.nickname || '服务师' }}
                  <StatusTag kind="cert" :value="detail.certStatus ?? 0" />
                  <span v-if="detail.phone" class="aux-text">{{ detail.phone }}</span>
                </div>
                <div class="pd__stats">
                  <div class="pd__stat">
                    <strong>{{ Number(detail.avgRating || 0).toFixed(1) }}</strong>
                    <span class="aux-text">综合评分</span>
                  </div>
                  <div class="pd__stat">
                    <strong>{{ detail.completedCount ?? 0 }}</strong>
                    <span class="aux-text">累计完成</span>
                  </div>
                  <div class="pd__stat">
                    <strong>{{ detail.experienceYears ?? 0 }}</strong>
                    <span class="aux-text">从业年限(年)</span>
                  </div>
                  <div class="pd__stat">
                    <strong class="pd__stat-area">{{ detail.areaName || '全城' }}</strong>
                    <span class="aux-text">服务区域</span>
                  </div>
                </div>
              </div>

              <div class="pd__head-actions">
                <el-button
                  plain
                  :type="favorited ? 'warning' : 'default'"
                  :loading="acting"
                  @click="onFavorite"
                >
                  <el-icon><StarFilled v-if="favorited" /><Star v-else /></el-icon>
                  &nbsp;{{ favorited ? '已收藏' : '收藏' }}
                </el-button>
                <el-button type="primary" plain :icon="ChatDotRound" @click="goChat">在线私聊</el-button>
              </div>
            </div>

            <div v-if="detail.rejectReason && detail.certStatus === 2" class="pd__reject">
              <el-alert type="warning" :closable="false" show-icon title="资质认证被驳回">
                <template #default>{{ detail.rejectReason }}</template>
              </el-alert>
            </div>

            <h3 class="pd__subtitle">个人简介</h3>
            <p class="pd__intro">{{ detail.intro || '该服务师还没有填写个人简介。' }}</p>

            <h3 class="pd__subtitle">擅长品种</h3>
            <div v-if="goodAtBreeds.length" class="pd__breeds">
              <span v-for="b in goodAtBreeds" :key="b" class="pd__breed">{{ b }}</span>
            </div>
            <p v-else class="aux-text">暂无特别标注，可私信沟通宠物情况</p>

            <h3 class="pd__subtitle">资质证书</h3>
            <div v-if="certImages.length" class="pd__certs">
              <el-image
                v-for="(img, i) in certImages"
                :key="i"
                :src="img"
                :preview-src-list="certImages"
                :initial-index="i"
                fit="cover"
                class="pd__cert"
                preview-teleported
              />
            </div>
            <p v-else class="aux-text">暂未上传资质证书图片</p>
          </PageCard>

          <!-- 服务评价 -->
          <PageCard :title="`服务评价（${reviewTotal}）`" class="pd__card">
            <template #header>
              <div class="pd__review-head">
                <div class="pd__review-score">
                  <RatingStars :model-value="Math.round(Number(detail.avgRating || 0))" size="small" readonly />
                  <span class="aux-text">{{ Number(detail.avgRating || 0).toFixed(1) }} 分</span>
                </div>
                <el-button size="small" :loading="reviewLoading" :icon="Refresh" @click="refreshReviews">
                  刷新
                </el-button>
              </div>
            </template>

            <div class="pd__reviews" v-loading="reviewLoading">
              <ErrorState v-if="reviewError && !reviewLoading" :text="reviewError" @retry="refreshReviews" />

              <EmptyState
                v-else-if="!reviews.length && !reviewLoading"
                text="还没有评价"
                action-text="预约一次服务"
                @action="scrollToBook"
              />

              <div v-else class="pd__review-list">
                <article v-for="r in reviews" :key="r.id" class="pd-review">
                  <div class="pd-review__head">
                    <el-avatar :size="32" class="pd-review__avatar">
                      <span>{{ (r.userNickname || '匿')[0] }}</span>
                    </el-avatar>
                    <div class="pd-review__user">
                      <div class="pd-review__name">{{ r.userNickname || '匿名用户' }}</div>
                      <span class="aux-text">{{ fmtTime(r.createdAt) }}</span>
                    </div>
                    <RatingStars :model-value="Number(r.rating)" size="small" readonly />
                  </div>

                  <p class="pd-review__content">{{ r.content || '用户未填写文字评价' }}</p>

                  <div v-if="imagesOf(r.images).length" class="pd-review__images">
                    <el-image
                      v-for="(img, i) in imagesOf(r.images)"
                      :key="i"
                      :src="img"
                      :preview-src-list="imagesOf(r.images)"
                      :initial-index="i"
                      fit="cover"
                      class="pd-review__img"
                      preview-teleported
                    />
                  </div>
                </article>
              </div>
            </div>

            <div v-if="reviewTotal > 0" class="pd__pager">
              <el-pagination
                background
                layout="total, sizes, prev, pager, next"
                :total="reviewTotal"
                :current-page="reviewPage"
                :page-size="reviewSize"
                :page-sizes="[5, 10, 20]"
                @current-change="changeReviewPage"
                @size-change="changeReviewSize"
              />
            </div>
          </PageCard>
        </el-col>

        <!-- 预约 -->
        <el-col :xs="24" :lg="9">
          <PageCard :id="serviceAnchor" title="立即预约" class="pd__card">
            <div v-if="picked" class="pd__picked">
              <div class="pd__picked-info">
                <span class="biz-tag">已选服务</span>
                <strong>{{ picked.name }}</strong>
                <span class="price-text">¥{{ fmtMoney(picked.price) }}</span>
              </div>
              <el-button type="primary" size="large" class="pd__book-btn" @click="book(picked.id)">
                去下单
              </el-button>
            </div>

            <p class="pd__hint aux-text">
              选择服务项目后，将携带本服务师信息进入下单页（宠物 / 地址 / 时间）。
            </p>

            <div class="pd__services" v-loading="servicesLoading" element-loading-text="服务项目加载中">
              <ErrorState v-if="servicesError && !servicesLoading" :text="servicesError" @retry="loadServices" />

              <EmptyState v-else-if="!services.length && !servicesLoading" text="暂无可预约的服务项目" />

              <div v-else class="pd__service-list">
                <div v-for="s in services" :key="s.id" class="pd-service">
                  <div class="pd-service__info">
                    <div class="pd-service__name">{{ s.name }}</div>
                    <div class="pd-service__meta aux-text">
                      {{ s.durationMinutes ? `${s.durationMinutes} 分钟` : '时长面议' }}
                    </div>
                  </div>
                  <span class="price-text pd-service__price">¥{{ fmtMoney(s.price) }}</span>
                  <el-button size="small" type="primary" plain @click="book(s.id)">预约</el-button>
                </div>
              </div>
            </div>

            <p class="pd__note aux-text">
              下单后从账户余额扣款；未开始服务可取消，金额原路退回。
            </p>
          </PageCard>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<style scoped>
.pd__topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.pd__crumb {
  flex: 1 1 auto;
  font-size: var(--font-size-body);
  color: var(--color-text-secondary);
}

.pd__card {
  margin-bottom: 16px;
}

.pd__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 16px;
}

.pd__avatar {
  flex: 0 0 auto;
  background: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
  font-size: 24px;
}

.pd__head-info {
  flex: 1 1 260px;
  min-width: 220px;
}

.pd__name {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: var(--font-size-title);
  font-weight: 600;
  color: var(--color-text-primary);
}

.pd__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 12px;
}

.pd__stat {
  min-width: 84px;
  padding: 8px 12px;
  border-radius: var(--radius-btn);
  background: var(--color-bg-page);
  text-align: center;
}

.pd__stat strong {
  display: block;
  font-size: var(--font-size-price);
  font-weight: 600;
  color: var(--color-primary);
  line-height: 1.4;
}

.pd__stat-area {
  font-size: var(--font-size-body) !important;
}

.pd__head-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pd__head-actions .el-button + .el-button {
  margin-left: 0;
}

.pd__reject {
  margin-top: 14px;
}

.pd__subtitle {
  margin: 20px 0 10px;
  font-size: var(--font-size-module);
  font-weight: 600;
  color: var(--color-text-primary);
}

.pd__intro {
  margin: 0;
  font-size: var(--font-size-body);
  line-height: 1.8;
  color: var(--color-text-secondary);
  white-space: pre-wrap;
}

.pd__breeds {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pd__breed {
  padding: 4px 12px;
  border-radius: 999px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: var(--font-size-aux);
}

.pd__certs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.pd__cert {
  width: 108px;
  height: 108px;
  border-radius: var(--radius-btn);
  overflow: hidden;
  cursor: zoom-in;
  background: var(--color-bg-page);
}

.pd__review-head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pd__review-score {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pd__reviews {
  min-height: 160px;
}

.pd__review-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.pd-review {
  padding: 14px;
  border-radius: var(--radius-btn);
  background: var(--color-bg-page);
}

.pd-review__head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.pd-review__avatar {
  flex: 0 0 auto;
  background: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
}

.pd-review__user {
  flex: 1 1 auto;
  min-width: 0;
}

.pd-review__name {
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
}

.pd-review__content {
  margin: 10px 0 0;
  font-size: var(--font-size-body);
  line-height: 1.7;
  color: var(--color-text-secondary);
}

.pd-review__images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.pd-review__img {
  width: 64px;
  height: 64px;
  border-radius: var(--radius-tag);
  overflow: hidden;
}

.pd__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.pd__picked {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
  border-radius: var(--radius-btn);
  background: var(--el-color-primary-light-9);
}

.pd__picked-info {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: var(--font-size-body);
  color: var(--color-text-primary);
}

.pd__book-btn {
  width: 100%;
  margin-left: 0;
}

.pd__hint {
  margin: 12px 0;
  line-height: 1.7;
}

.pd__services {
  min-height: 160px;
}

.pd__service-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 360px;
  overflow-y: auto;
}

.pd-service {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--radius-btn);
  background: var(--color-bg-page);
}

.pd-service__info {
  flex: 1 1 auto;
  min-width: 0;
}

.pd-service__name {
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
}

.pd-service__price {
  flex: 0 0 auto;
  font-size: var(--font-size-body);
}

.pd-service .el-button {
  margin-left: 0;
  flex: 0 0 auto;
}

.pd__note {
  margin: 12px 0 0;
  line-height: 1.7;
}

@media (max-width: 768px) {
  .pd__pager {
    justify-content: center;
  }

  .pd__head-actions {
    width: 100%;
  }

  .pd__head-actions .el-button {
    flex: 1 1 auto;
  }
}
</style>
