<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, Search } from '@element-plus/icons-vue'
import PageCard from '@/components/common/PageCard.vue'
import RatingStars from '@/components/common/RatingStars.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import { fetchServiceItem, fetchServiceTypes } from '@/api/catalog'
import { fetchStaffs } from '@/api/staffs'
import { fmtMoney } from '@/utils/constants'
import type { ServiceItem, ServiceType, StaffListVO } from '@/types'

/**
 * 服务详情（PRD 5.3）：
 * 价格 / 时长 / 适用品种 / 须知 / 所属分类 + 推荐服务师 +「立即预约」。
 * 下单页要求同时携带 itemId 与 staffId，因此预约时先选择服务师。
 */
const route = useRoute()
const router = useRouter()

const itemId = computed(() => Number(route.params.id))

const item = ref<ServiceItem | null>(null)
const types = ref<ServiceType[]>([])
const recommend = ref<StaffListVO[]>([])
const loading = ref(true)
const error = ref('')

async function load(): Promise<void> {
  if (!itemId.value || Number.isNaN(itemId.value)) {
    error.value = '服务参数不合法'
    loading.value = false
    item.value = null
    return
  }
  loading.value = true
  error.value = ''
  try {
    const [detail, typeList, staffPage] = await Promise.all([
      fetchServiceItem(itemId.value),
      fetchServiceTypes(),
      fetchStaffs({ page: 1, size: 4 }),
    ])
    item.value = detail ?? null
    types.value = typeList ?? []
    recommend.value = staffPage?.list ?? []
    if (!item.value) error.value = '该服务不存在或已下架'
  } catch (e) {
    item.value = null
    error.value = (e as Error)?.message || '服务详情加载失败'
  } finally {
    loading.value = false
  }
}

const typeName = computed(() => {
  const t = types.value.find((x) => x.id === item.value?.typeId)
  return t?.name || '综合服务'
})

/** 后端 applicableBreeds 为 JSON 数组字符串 */
const breeds = computed<string[]>(() => {
  const raw = item.value?.applicableBreeds
  if (!raw) return []
  try {
    const arr: unknown = JSON.parse(raw)
    if (Array.isArray(arr)) return (arr as string[]).filter(Boolean)
  } catch {
    /* 非 JSON 时按原文展示 */
  }
  return raw ? [raw] : []
})

/* ---------- 选择服务师（下单页必填 staffId） ---------- */
const pickerVisible = ref(false)
const pickerLoading = ref(false)
const pickerError = ref('')
const pickerKeyword = ref('')
const pickerList = ref<StaffListVO[]>([])

async function loadPicker(): Promise<void> {
  pickerLoading.value = true
  pickerError.value = ''
  try {
    const res = await fetchStaffs({
      keyword: pickerKeyword.value || undefined,
      page: 1,
      size: 8,
    })
    pickerList.value = res?.list ?? []
  } catch (e) {
    pickerError.value = (e as Error)?.message || '服务师加载失败'
    pickerList.value = []
  } finally {
    pickerLoading.value = false
  }
}

function openPicker(): void {
  pickerVisible.value = true
  pickerKeyword.value = ''
  void loadPicker()
}

function goBook(staffId: number): void {
  router.push({
    path: '/orders/create',
    query: { itemId: String(itemId.value), staffId: String(staffId) },
  })
}

function goProviders(): void {
  router.push({ path: '/providers', query: { itemId: String(itemId.value) } })
}

function goProvider(id: number): void {
  router.push({ path: `/providers/${id}`, query: { itemId: String(itemId.value) } })
}

watch(itemId, () => void load())
onMounted(() => void load())
</script>

<template>
  <div class="sd" v-loading="loading">
    <div class="sd__topbar">
      <el-button link :icon="ArrowLeft" @click="router.push('/services')">返回服务列表</el-button>
      <span class="sd__crumb">服务详情</span>
    </div>

    <EmptyState v-if="error && !loading" :text="error" action-text="重新加载" @action="load">
      <template #tip>可能该服务已下架，可返回列表选择其它服务</template>
    </EmptyState>

    <template v-else-if="item">
      <el-row :gutter="16">
        <!-- 服务信息 -->
        <el-col :xs="24" :lg="15">
          <PageCard class="sd__card">
            <div class="sd__hero">
              <div class="sd__cover">
                <img v-if="item.coverUrl" :src="item.coverUrl" :alt="item.name" />
                <span v-else class="sd__cover-empty">🐾</span>
              </div>
              <div class="sd__hero-info">
                <div class="sd__hero-tags">
                  <span class="biz-tag">{{ typeName }}</span>
                  <span class="biz-tag">上门服务</span>
                </div>
                <h2 class="sd__name">{{ item.name }}</h2>
                <p class="sd__brief aux-text">
                  {{ item.durationMinutes ? `单次服务约 ${item.durationMinutes} 分钟` : '服务时长以实际沟通为准' }}
                </p>
                <div class="sd__price">
                  <span class="price-text-lg">¥{{ fmtMoney(item.price) }}</span>
                  <span class="aux-text">/ 次 · 按次计费</span>
                </div>
              </div>
            </div>

            <el-descriptions :column="1" border class="sd__desc">
              <el-descriptions-item label="所属分类">{{ typeName }}</el-descriptions-item>
              <el-descriptions-item label="服务时长">
                {{ item.durationMinutes ? `${item.durationMinutes} 分钟` : '以实际沟通为准' }}
              </el-descriptions-item>
              <el-descriptions-item label="适用品种">
                <template v-if="breeds.length">
                  <span v-for="b in breeds" :key="b" class="sd__breed">{{ b }}</span>
                </template>
                <span v-else class="aux-text">不限品种</span>
              </el-descriptions-item>
              <el-descriptions-item label="服务须知">
                <span class="sd__notice">{{ item.notice || '无特殊须知，请提前准备好宠物与环境。' }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </PageCard>

          <!-- 推荐服务师 -->
          <PageCard title="推荐服务师" class="sd__card">
            <template #header>
              <el-button link type="primary" @click="goProviders()">
                查看全部
                <el-icon><ArrowRight /></el-icon>
              </el-button>
            </template>

            <EmptyState v-if="!recommend.length" text="暂无可用服务师" action-text="去服务人员列表" @action="goProviders" />

            <div v-else class="sd__staffs">
              <article
                v-for="s in recommend"
                :key="s.id"
                class="sd-staff"
                @click="goProvider(s.id)"
              >
                <el-avatar :size="44" class="sd-staff__avatar">
                  <img v-if="s.avatar" :src="s.avatar" alt="" />
                  <span v-else>{{ (s.nickname || '师')[0] }}</span>
                </el-avatar>
                <div class="sd-staff__info">
                  <div class="sd-staff__name">
                    {{ s.nickname || '服务师' }}
                    <span v-if="s.certStatus === 1" class="biz-tag">已认证</span>
                  </div>
                  <div class="sd-staff__meta">
                    <RatingStars :model-value="Number(s.avgRating || 0)" size="small" readonly />
                    <span class="aux-text">{{ s.completedCount ?? 0 }} 单</span>
                  </div>
                  <div class="aux-text">{{ s.experienceYears ?? 0 }} 年从业 · {{ s.areaName || '全城可约' }}</div>
                </div>
                <el-button size="small" type="primary" plain @click.stop="goBook(s.id)">选TA预约</el-button>
              </article>
            </div>
          </PageCard>
        </el-col>

        <!-- 预约入口 -->
        <el-col :xs="24" :lg="9">
          <PageCard title="立即预约" class="sd__card sd__book">
            <div class="sd__book-price">
              <span class="sd__book-label">服务单价</span>
              <span class="price-text-lg">¥{{ fmtMoney(item.price) }}</span>
            </div>
            <el-divider class="sd__book-divider" />

            <el-button type="primary" size="large" class="sd__book-btn" @click="openPicker">
              立即预约
            </el-button>
            <el-button size="large" class="sd__book-btn" plain @click="goProviders">
              自选服务师
            </el-button>

            <p class="sd__book-note aux-text">
              预约需选择服务师、宠物、上门地址与时间；提交后从账户余额扣款，可在「我的订单」跟踪进度。
            </p>
          </PageCard>

          <PageCard title="服务保障" class="sd__card">
            <ul class="sd__guard">
              <li>认证服务师接单，资质平台审核</li>
              <li>服务全程留痕，进度随时可查</li>
              <li>未开始服务可取消，余额原路退回</li>
              <li>完成后一单一评，信誉可追溯</li>
            </ul>
          </PageCard>
        </el-col>
      </el-row>
    </template>

    <!-- 选择服务师 -->
    <el-dialog v-model="pickerVisible" title="选择服务师" width="520px" align-center>
      <div class="sd-picker__search">
        <el-input
          v-model="pickerKeyword"
          clearable
          placeholder="输入服务师昵称"
          :prefix-icon="Search"
          @keyup.enter="loadPicker"
          @clear="loadPicker"
        />
        <el-button type="primary" @click="loadPicker">查询</el-button>
      </div>

      <div class="sd-picker__body" v-loading="pickerLoading" element-loading-text="服务师加载中">
        <ErrorState v-if="pickerError && !pickerLoading" :text="pickerError" @retry="loadPicker" />

        <EmptyState v-else-if="!pickerList.length && !pickerLoading" text="没有匹配的服务师" action-text="浏览全部服务师" @action="goProviders" />

        <div v-else class="sd-picker__list">
          <div v-for="s in pickerList" :key="s.id" class="sd-picker__item" @click="goProvider(s.id)">
            <el-avatar :size="36">
              <img v-if="s.avatar" :src="s.avatar" alt="" />
              <span v-else>{{ (s.nickname || '师')[0] }}</span>
            </el-avatar>
            <div class="sd-picker__info">
              <div class="sd-picker__name">
                {{ s.nickname || '服务师' }}
                <span v-if="s.certStatus === 1" class="biz-tag">已认证</span>
              </div>
              <div class="sd-picker__meta aux-text">
                {{ Number(s.avgRating || 0).toFixed(1) }} 分 · {{ s.completedCount ?? 0 }} 单 ·
                {{ s.experienceYears ?? 0 }} 年经验
              </div>
            </div>
            <el-button size="small" type="primary" @click.stop="goBook(s.id)">选TA预约</el-button>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="pickerVisible = false">关闭</el-button>
        <el-button plain @click="goProviders">去服务人员列表</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sd__topbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.sd__crumb {
  font-size: var(--font-size-body);
  color: var(--color-text-secondary);
}

.sd__card {
  margin-bottom: 16px;
}

.sd__hero {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 16px;
}

.sd__cover {
  flex: 0 0 240px;
  width: 240px;
  height: 160px;
  border-radius: var(--radius-btn);
  overflow: hidden;
  background: var(--el-color-primary-light-9);
}

.sd__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.sd__cover-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 40px;
}

.sd__hero-info {
  flex: 1 1 240px;
  min-width: 200px;
}

.sd__hero-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.sd__name {
  margin: 0;
  font-size: var(--font-size-title);
  font-weight: 600;
  color: var(--color-text-primary);
  line-height: 1.4;
}

.sd__brief {
  margin: 6px 0 10px;
  line-height: 1.6;
}

.sd__price {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.sd__desc {
  margin-top: 4px;
}

.sd__breed {
  display: inline-block;
  margin: 0 6px 4px 0;
  padding: 2px 8px;
  border-radius: var(--radius-tag);
  background: var(--color-bg-page);
  color: var(--color-text-secondary);
  font-size: var(--font-size-aux);
}

.sd__notice {
  font-size: var(--font-size-body);
  line-height: 1.7;
  color: var(--color-text-secondary);
  white-space: pre-wrap;
}

.sd__staffs {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sd-staff {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: var(--radius-btn);
  background: var(--color-bg-page);
  cursor: pointer;
  transition: box-shadow 0.15s ease;
}

.sd-staff:hover {
  box-shadow: var(--shadow-card);
}

.sd-staff__avatar {
  flex: 0 0 auto;
  background: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
  font-size: 16px;
}

.sd-staff__info {
  flex: 1 1 auto;
  min-width: 0;
}

.sd-staff__name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
}

.sd-staff__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 4px 0;
}

.sd-staff .el-button {
  margin-left: 0;
  flex: 0 0 auto;
}

.sd__book-price {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.sd__book-label {
  font-size: var(--font-size-body);
  color: var(--color-text-secondary);
}

.sd__book-divider {
  margin: 12px 0;
}

.sd__book-btn {
  width: 100%;
  margin-left: 0;
  margin-bottom: 10px;
}

.sd__book-note {
  margin: 6px 0 0;
  line-height: 1.7;
}

.sd__guard {
  margin: 0;
  padding: 0;
  list-style: none;
}

.sd__guard li {
  position: relative;
  padding: 7px 0 7px 18px;
  font-size: var(--font-size-body);
  color: var(--color-text-secondary);
  line-height: 1.6;
}

.sd__guard li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 15px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-secondary);
}

.sd-picker__search {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.sd-picker__body {
  min-height: 200px;
  max-height: 48vh;
  overflow-y: auto;
}

.sd-picker__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sd-picker__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--radius-btn);
  background: var(--color-bg-page);
  cursor: pointer;
}

.sd-picker__item:hover {
  background: var(--el-color-primary-light-9);
}

.sd-picker__item .el-avatar {
  flex: 0 0 auto;
  background: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
}

.sd-picker__info {
  flex: 1 1 auto;
  min-width: 0;
}

.sd-picker__name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
}

.sd-picker__meta {
  margin-top: 2px;
}

.sd-picker__item .el-button {
  margin-left: 0;
  flex: 0 0 auto;
}

@media (max-width: 768px) {
  .sd__cover {
    flex: 1 1 100%;
    width: 100%;
    height: 180px;
  }

  .sd-picker__search {
    flex-wrap: wrap;
  }
}
</style>
