<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import SearchForm from '@/components/common/SearchForm.vue'
import TableWrapper from '@/components/common/TableWrapper.vue'
import PageCard from '@/components/common/PageCard.vue'
import ConfirmAction from '@/components/common/ConfirmAction.vue'
import RatingStars from '@/components/common/RatingStars.vue'
import { usePage } from '@/composables/usePage'
import { fetchAdminReviews, updateReviewStatus } from '@/api/admin/ops'
import { fmtTime } from '@/utils/constants'
import type { Review } from '@/types'

/** 评价管理（PRD 7.1）：全量评价查询 + 违规隐藏 / 恢复（触发服务师信誉重算，写审计） */

interface ReviewQuery {
  staffId: string
  userId: string
  status: number | ''
  /** 星级 1-5 —— 后端无该参数，仅在当前页内检索 */
  rating: number | ''
  /** 评价内容关键词 —— 后端无该参数，仅在当前页内检索 */
  keyword: string
}

const { list, total, page, size, loading, error, query, search, reset, changePage, changeSize, refresh } =
  usePage<Review, ReviewQuery>(
    (q) =>
      fetchAdminReviews({
        staffId: toNum(q.staffId),
        userId: toNum(q.userId),
        status: toInt(q.status),
        page: q.page,
        size: q.size,
      }),
    { defaultQuery: { staffId: '', userId: '', status: '', rating: '', keyword: '' } },
  )

/** 输入框文本 → 合法正整数 */
function toNum(v: string): number | undefined {
  const n = Number(v)
  return v.trim() !== '' && Number.isInteger(n) && n > 0 ? n : undefined
}

/** 选择框（可能被 clearable 清成 undefined）→ 后端整数参数 */
function toInt(v: unknown): number | undefined {
  if (v === '' || v === null || v === undefined) return undefined
  const n = Number(v)
  return Number.isInteger(n) ? n : undefined
}

/* ---------- 页内筛选：星级 + 内容关键词 ---------- */
const ratingFilter = computed(() => toInt(query.rating))
const keyword = computed(() => query.keyword.trim().toLowerCase())
const hasLocalFilter = computed(() => ratingFilter.value !== undefined || keyword.value !== '')
const displayList = computed(() => {
  const rating = ratingFilter.value
  const kw = keyword.value
  if (rating === undefined && !kw) return list.value
  return list.value.filter((r) => {
    if (rating !== undefined && Number(r.rating) !== rating) return false
    if (kw && !(r.content ?? '').toLowerCase().includes(kw)) return false
    return true
  })
})
const tableData = computed(() => displayList.value as unknown as Record<string, unknown>[])

/** 后端返回的图片为 JSON 数组字符串 */
function parseImages(v: string | string[] | undefined): string[] {
  if (!v) return []
  if (Array.isArray(v)) return v
  try {
    const arr: unknown = JSON.parse(v)
    return Array.isArray(arr) ? arr.filter((s): s is string => typeof s === 'string') : []
  } catch {
    return []
  }
}

/* ---------- 隐藏 / 恢复 ---------- */
const acting = ref(false)

async function changeStatus(row: Review, next: number) {
  acting.value = true
  try {
    await updateReviewStatus(row.id, next)
    ElMessage.success(next === 1 ? '评价已恢复显示' : '评价已隐藏，服务师信誉已重新计算')
    await refresh()
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    acting.value = false
  }
}

/* ---------- 详情 ---------- */
const detailVisible = ref(false)
const current = ref<Review | null>(null)

function openDetail(row: Review) {
  current.value = row
  detailVisible.value = true
}

const exportColumns = [
  { label: 'ID', key: 'id' },
  { label: '订单ID', key: 'orderId' },
  { label: '评价人ID', key: 'userId' },
  { label: '服务师ID', key: 'staffId' },
  { label: '星级', key: 'rating' },
  { label: '内容', key: 'content' },
  { label: '状态', key: 'status' },
  { label: '评价时间', key: 'createdAt' },
]
</script>

<template>
  <div class="page-container">
    <PageCard title="评价管理">
      <SearchForm :cols="4" @search="search" @reset="reset">
        <el-form-item label="服务师ID">
          <el-input v-model="query.staffId" placeholder="被评服务师 ID" clearable :prefix-icon="Search" />
        </el-form-item>
        <el-form-item label="评价人ID">
          <el-input v-model="query.userId" placeholder="评价用户 ID" clearable :prefix-icon="Search" />
        </el-form-item>
        <el-form-item label="显示状态">
          <el-select v-model="query.status" placeholder="全部状态" clearable>
            <el-option label="显示中" :value="1" />
            <el-option label="已隐藏" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="星级">
          <el-select v-model="query.rating" placeholder="全部星级" clearable>
            <el-option v-for="n in 5" :key="n" :label="`${n} 星`" :value="n" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="query.keyword" placeholder="评价内容关键词" clearable :prefix-icon="Search" />
        </el-form-item>
      </SearchForm>

      <TableWrapper
        :data="tableData"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        :error="error"
        export-name="评价列表"
        :export-columns="exportColumns"
        empty-text="暂无评价数据"
        @update:page="changePage"
        @update:size="changeSize"
        @refresh="refresh"
      >
        <template #toolbar>
          <span class="aux-text">共 {{ total }} 条评价</span>
          <span v-if="hasLocalFilter" class="local-filter-tip">页内筛选：星级与内容仅在当前页 {{ list.length }} 条记录中检索</span>
        </template>

        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="orderId" label="订单ID" width="100" />
        <el-table-column label="评价人" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.userNickname || `用户 ${row.userId}` }}</template>
        </el-table-column>
        <el-table-column prop="staffId" label="服务师ID" width="100" />
        <el-table-column label="评分" width="150">
          <template #default="{ row }">
            <RatingStars :model-value="Number(row.rating)" size="small" readonly />
          </template>
        </el-table-column>
        <el-table-column label="评价内容" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.content || '-' }}</template>
        </el-table-column>
        <el-table-column label="图片" width="70" align="center">
          <template #default="{ row }">{{ parseImages(row.images).length || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light" round>
              {{ row.status === 1 ? '显示中' : '已隐藏' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="评价时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <ConfirmAction
              v-if="row.status === 1"
              title="确认隐藏该评价？"
              message="隐藏后小程序端不再展示，并触发服务师信誉重算。"
              danger
              @confirm="changeStatus(row, 0)"
            >
              <el-button link type="danger" :loading="acting">隐藏</el-button>
            </ConfirmAction>
            <el-button v-else link type="success" :loading="acting" @click="changeStatus(row, 1)">恢复</el-button>
          </template>
        </el-table-column>
      </TableWrapper>
    </PageCard>

    <!-- 评价详情 -->
    <el-dialog v-model="detailVisible" title="评价详情" width="min(640px, 94vw)">
      <template v-if="current">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="评价 ID">{{ current.id }}</el-descriptions-item>
          <el-descriptions-item label="订单 ID">{{ current.orderId }}</el-descriptions-item>
          <el-descriptions-item label="评价人">用户 {{ current.userId }}</el-descriptions-item>
          <el-descriptions-item label="被评服务师">服务师 {{ current.staffId ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="评分">
            <RatingStars :model-value="Number(current.rating)" readonly />
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="current.status === 1 ? 'success' : 'info'" effect="light" round>
              {{ current.status === 1 ? '显示中' : '已隐藏' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="评价时间" :span="2">{{ fmtTime(current.createdAt) }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="detail-subtitle">评价内容</h4>
        <p class="detail-content">{{ current.content || '（无文字内容）' }}</p>

        <template v-if="parseImages(current.images).length">
          <h4 class="detail-subtitle">评价图片</h4>
          <div class="detail-images">
            <el-image
              v-for="(img, i) in parseImages(current.images)"
              :key="i"
              :src="img"
              :preview-src-list="parseImages(current.images)"
              :initial-index="i"
              fit="cover"
              class="detail-image"
              preview-teleported
            />
          </div>
        </template>
      </template>
      <el-empty v-else description="暂无数据" :image-size="90" />
    </el-dialog>
  </div>
</template>

<style scoped>
.local-filter-tip {
  margin-left: 12px;
  font-size: var(--font-size-aux);
  color: var(--color-warning);
}

.detail-subtitle {
  margin: 20px 0 8px;
  font-size: var(--font-size-module);
  font-weight: 600;
  color: var(--color-text-primary);
}

.detail-content {
  margin: 0;
  font-size: var(--font-size-body);
  line-height: 1.7;
  color: var(--color-text-primary);
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.detail-image {
  width: 96px;
  height: 96px;
  border-radius: var(--radius-tag);
  overflow: hidden;
}
</style>
