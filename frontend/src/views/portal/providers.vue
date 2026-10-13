<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Star, StarFilled } from '@element-plus/icons-vue'
import PageCard from '@/components/common/PageCard.vue'
import SearchForm from '@/components/common/SearchForm.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import RatingStars from '@/components/common/RatingStars.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import { usePage } from '@/composables/usePage'
import { fetchServiceAreas } from '@/api/catalog'
import { fetchStaffs, toggleFavorite } from '@/api/staffs'
import type { ServiceArea, StaffListVO } from '@/types'

/**
 * 服务人员列表（PRD 5.3）：
 * 区域 / 擅长品种 / 关键词筛选 + 卡片流 + 分页 + 收藏切换。
 * 从服务详情进入时地址栏带 itemId，会继续携带到详情页。
 */
const route = useRoute()
const router = useRouter()

interface ProviderQuery {
  areaId: number | ''
  breed: string
  keyword: string
}

const { list, total, page, size, loading, error, query, search, reset, refresh, changePage, changeSize } = usePage<
  StaffListVO,
  ProviderQuery
>(
  (q) =>
    fetchStaffs({
      areaId: q.areaId === '' ? undefined : q.areaId,
      breed: q.breed || undefined,
      keyword: q.keyword || undefined,
      page: q.page ?? 1,
      size: q.size ?? 12,
    }),
  { defaultQuery: { areaId: '', breed: '', keyword: '' }, size: 12 },
)

/** 携带的服务项目（来自服务详情「立即预约」） */
const carryItemId = computed(() => Number(route.query.itemId || 0) || undefined)

/* ---------- 筛选项：区域 / 擅长品种 ---------- */
const areas = ref<ServiceArea[]>([])
const areasError = ref('')

const BREED_OPTIONS = [
  '布偶猫',
  '英短',
  '美短',
  '暹罗',
  '加菲',
  '无毛猫',
  '泰迪',
  '比熊',
  '金毛',
  '拉布拉多',
  '柯基',
  '兔子',
  '其他',
]

async function loadAreas(): Promise<void> {
  areasError.value = ''
  try {
    areas.value = (await fetchServiceAreas()) ?? []
  } catch (e) {
    areasError.value = (e as Error)?.message || '服务区域加载失败'
  }
}

/* ---------- 收藏 ---------- */
/** 已知收藏态（列表接口不返回收藏态，首次点击后回填） */
const favState = reactive<Record<number, boolean>>({})
const favBusy = reactive(new Set<number>())

async function onFavorite(staff: StaffListVO): Promise<void> {
  if (favBusy.has(staff.id)) return
  favBusy.add(staff.id)
  try {
    const res = await toggleFavorite(staff.id)
    const next = res?.favorited ?? false
    favState[staff.id] = next
    ElMessage.success(next ? `已收藏「${staff.nickname || '服务师'}」，可在「我的收藏」查看` : '已取消收藏')
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    favBusy.delete(staff.id)
  }
}

/* ---------- 跳转 ---------- */
function detailPath(id: number): string {
  return carryItemId.value ? `/providers/${id}?itemId=${carryItemId.value}` : `/providers/${id}`
}

function goDetail(staff: StaffListVO): void {
  router.push(detailPath(staff.id))
}

function book(staff: StaffListVO, e: Event): void {
  e.stopPropagation()
  if (!carryItemId.value) {
    // 未携带服务项目：进详情页选择服务项目后预约
    router.push(detailPath(staff.id))
    return
  }
  router.push({ path: '/orders/create', query: { itemId: String(carryItemId.value), staffId: String(staff.id) } })
}

const canCarry = computed(() => Boolean(carryItemId.value))

/** 卡片上最多展示 4 个擅长品种 */
function topBreeds(breeds?: string[], max = 4): string[] {
  return (breeds ?? []).slice(0, max)
}

onMounted(() => void loadAreas())
</script>

<template>
  <div class="pf">
    <PageCard title="服务人员" class="pf__card">
      <template #header>
        <div class="pf__head">
          <span class="aux-text">共 {{ total }} 位认证服务师</span>
          <el-button size="small" :loading="loading" @click="refresh">刷新</el-button>
        </div>
      </template>

      <SearchForm :cols="3" @search="search" @reset="reset">
        <el-form-item label="服务区域">
          <el-select v-model="query.areaId" placeholder="全部区域" clearable>
            <el-option v-for="a in areas" :key="a.id" :label="a.name" :value="a.id" />
          </el-select>
          <span v-if="areasError" class="aux-text pf__inline-error">区域加载失败，可直接查询</span>
        </el-form-item>

        <el-form-item label="擅长品种">
          <el-select v-model="query.breed" placeholder="全部品种" clearable filterable allow-create>
            <el-option v-for="b in BREED_OPTIONS" :key="b" :label="b" :value="b" />
          </el-select>
        </el-form-item>

        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            clearable
            placeholder="服务师昵称"
            :prefix-icon="Search"
          />
        </el-form-item>
      </SearchForm>

      <div class="pf__body" v-loading="loading">
        <ErrorState v-if="error && !loading" :text="error" @retry="refresh" />

        <EmptyState
          v-else-if="!list.length && !loading"
          text="没有符合条件的服务师"
          action-text="重置筛选"
          @action="reset"
        >
          <template #tip>可以放宽区域或品种条件，平台认证服务师持续入驻中</template>
        </EmptyState>

        <div v-else class="pf__grid">
          <article v-for="s in list" :key="s.id" class="pf-item" @click="goDetail(s)">
            <div class="pf-item__head">
              <el-avatar :size="52" class="pf-item__avatar">
                <img v-if="s.avatar" :src="s.avatar" alt="" />
                <span v-else>{{ (s.nickname || '师')[0] }}</span>
              </el-avatar>

              <div class="pf-item__base">
                <div class="pf-item__name">
                  {{ s.nickname || '服务师' }}
                  <StatusTag v-if="s.certStatus !== undefined" kind="cert" :value="s.certStatus" />
                </div>
                <div class="pf-item__rating">
                  <RatingStars :model-value="Number(s.avgRating || 0)" size="small" readonly />
                  <span class="aux-text">{{ Number(s.avgRating || 0).toFixed(1) }} 分</span>
                </div>
                <div class="aux-text">
                  {{ s.experienceYears ?? 0 }} 年从业 · {{ s.completedCount ?? 0 }} 单 · {{ s.areaName || '全城可约' }}
                </div>
              </div>
            </div>

            <div v-if="s.goodAtBreeds?.length" class="pf-item__breeds">
              <span v-for="b in topBreeds(s.goodAtBreeds)" :key="b" class="pf-tag">{{ b }}</span>
              <span v-if="(s.goodAtBreeds?.length ?? 0) > 4" class="aux-text">
                +{{ (s.goodAtBreeds?.length ?? 0) - 4 }}
              </span>
            </div>

            <div class="pf-item__foot">
              <button
                type="button"
                class="pf-fav"
                :class="{ 'is-on': favState[s.id] === true }"
                :disabled="favBusy.has(s.id)"
                @click.stop="onFavorite(s)"
              >
                <el-icon :size="16">
                  <StarFilled v-if="favState[s.id] === true" />
                  <Star v-else />
                </el-icon>
                {{ favState[s.id] === true ? '已收藏' : '收藏' }}
              </button>

              <el-button v-if="canCarry" size="small" type="primary" plain @click.stop="book(s, $event)">
                立即预约
              </el-button>
              <el-button v-else size="small" plain @click.stop="goDetail(s)">查看详情</el-button>
            </div>
          </article>
        </div>
      </div>

      <div v-if="total > 0" class="pf__pager">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :current-page="page"
          :page-size="size"
          :page-sizes="[12, 24, 48]"
          @current-change="changePage"
          @size-change="changeSize"
        />
      </div>
    </PageCard>
  </div>
</template>

<style scoped>
.pf__card {
  min-height: 420px;
}

.pf__head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pf__inline-error {
  width: 100%;
  color: var(--color-danger);
  line-height: 1.4;
}

.pf__body {
  min-height: 260px;
}

.pf__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.pf-item {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  border-radius: var(--radius-card);
  background: var(--color-bg-page);
  cursor: pointer;
  transition: all 0.15s ease;
}

.pf-item:hover {
  box-shadow: var(--shadow-card);
  transform: translateY(-2px);
}

.pf-item__head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.pf-item__avatar {
  flex: 0 0 auto;
  background: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
  font-size: 18px;
}

.pf-item__base {
  flex: 1 1 auto;
  min-width: 0;
}

.pf-item__name {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
}

.pf-item__rating {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 5px 0 3px;
}

.pf-item__breeds {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.pf-tag {
  padding: 2px 8px;
  border-radius: var(--radius-tag);
  background: var(--color-bg-card);
  color: var(--color-text-secondary);
  font-size: var(--font-size-aux);
}

.pf-item__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: auto;
}

.pf-item__foot .el-button {
  margin-left: 0;
}

.pf-fav {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border: none;
  border-radius: var(--radius-tag);
  background: transparent;
  color: var(--color-text-muted);
  font-size: var(--font-size-aux);
  cursor: pointer;
  transition: all 0.15s ease;
}

.pf-fav:hover:not(:disabled) {
  background: var(--color-warning-light-9);
  color: var(--color-warning);
}

.pf-fav.is-on {
  color: var(--color-warning);
  font-weight: 600;
}

.pf-fav:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.pf__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 768px) {
  .pf__pager {
    justify-content: center;
  }

  .pf__grid {
    grid-template-columns: 1fr;
  }
}
</style>
