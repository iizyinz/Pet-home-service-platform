<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import SearchForm from '@/components/common/SearchForm.vue'
import TableWrapper from '@/components/common/TableWrapper.vue'
import PageCard from '@/components/common/PageCard.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import ConfirmAction from '@/components/common/ConfirmAction.vue'
import { usePage } from '@/composables/usePage'
import {
  fetchAdminStaffs,
  fetchAdminStaffDetail,
  auditStaffCert,
  updateAdminStaffStatus,
  fetchAllAdminServiceAreas,
  type AdminStaffDetail,
} from '@/api/admin/catalog'
import { CERT_STATUS, fmtTime } from '@/utils/constants'
import type { AdminStaffRow } from '@/types'

/** 服务人员管理（PRD 7.1：资料/认证状态/服务区域/资质，资质审核与上下架，写操作记审计） */
type StaffQuery = { keyword: string; certStatus: number | '' }

const { list, total, page, size, loading, error, query, search, reset, changePage, changeSize, refresh } =
  usePage<AdminStaffRow, StaffQuery>((q) => fetchAdminStaffs(q), {
    defaultQuery: { keyword: '', certStatus: '' },
  })

/** 认证状态筛选项（复用 constants 字典） */
const CERT_OPTIONS = Object.entries(CERT_STATUS).map(([k, v]) => ({ label: v.label, value: Number(k) }))

/** 账号状态（staff_profile.status：1 正常 0 下架） */
const STAFF_STATUS: Record<number, { label: string; type: 'success' | 'danger' }> = {
  1: { label: '正常', type: 'success' },
  0: { label: '已下架', type: 'danger' },
}
const STATUS_FALLBACK = { label: '未知', type: 'info' as const }
function statusOf(v?: number) {
  return STAFF_STATUS[v === 0 ? 0 : 1] ?? STATUS_FALLBACK
}

/** 全量区域：区域 ID → 名称 */
const areas = ref<Array<{ id: number; name: string }>>([])
const areaMap = computed(
  () => new Map<number, string>(areas.value.map((a): [number, string] => [a.id, a.name])),
)

function areaName(id?: number): string {
  if (!id) return '-'
  return areaMap.value.get(id) ?? `#${id}`
}

async function loadAreas() {
  try {
    areas.value = (await fetchAllAdminServiceAreas()).map((a) => ({ id: a.id, name: a.name }))
  } catch {
    /* 拦截器已统一提示 */
  }
}
onMounted(loadAreas)

/** 后端 JSON 数组字符串（goodAtBreeds / certImages）解析 */
function jsonList(v?: string | string[]): string[] {
  if (!v) return []
  if (Array.isArray(v)) return v
  try {
    const arr: unknown = JSON.parse(v)
    return Array.isArray(arr) ? arr.map((s) => String(s)) : []
  } catch {
    return []
  }
}

function breedText(v?: string): string {
  const list = jsonList(v)
  return list.length ? list.join('、') : '-'
}

/* ---------- 详情 ---------- */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<AdminStaffDetail | null>(null)
const current = ref<AdminStaffRow | null>(null)
const certImages = computed(() => jsonList(detail.value?.certImages))

async function openDetail(row: AdminStaffRow) {
  current.value = row
  detail.value = null
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await fetchAdminStaffDetail(row.userId)
  } catch {
    /* 拦截器已统一提示，弹窗内展示空态 */
  } finally {
    detailLoading.value = false
  }
}

/* ---------- 资质审核 ---------- */
const auditVisible = ref(false)
const auditRef = ref<FormInstance>()
/** certStatus：1 通过 2 驳回（提交时收敛为字面量类型） */
const auditForm = reactive({ userId: 0, target: '', certStatus: 1, rejectReason: '' })

const auditRules: FormRules = {
  rejectReason: [
    {
      validator: (_r, _v, cb) =>
        auditForm.certStatus === 2 && !auditForm.rejectReason.trim()
          ? cb(new Error('驳回必须填写原因'))
          : cb(),
      trigger: 'blur',
    },
  ],
}

function openAudit(row: AdminStaffRow) {
  auditForm.userId = row.userId
  auditForm.target = row.nickname || row.username || `#${row.userId}`
  auditForm.certStatus = 1
  auditForm.rejectReason = ''
  auditVisible.value = true
  auditRef.value?.clearValidate()
}

async function submitAudit() {
  const valid = await auditRef.value?.validate().catch(() => false)
  if (!valid) return
  const passed = auditForm.certStatus !== 2
  await auditStaffCert(auditForm.userId, {
    certStatus: passed ? 1 : 2,
    rejectReason: passed ? undefined : auditForm.rejectReason.trim(),
  })
  ElMessage.success(passed ? '已通过资质审核，可开始接单' : '已驳回并通知该服务人员')
  auditVisible.value = false
  await refresh()
}

/* ---------- 上架 / 下架 ---------- */
async function onToggleStatus(row: AdminStaffRow) {
  const next = row.status === 1 ? 0 : 1
  await updateAdminStaffStatus(row.userId, next)
  ElMessage.success(next === 1 ? '已上架展示' : '已下架隐藏')
  await refresh()
}

const exportColumns = [
  { label: '用户ID', key: 'userId' },
  { label: '账号', key: 'username' },
  { label: '昵称', key: 'nickname' },
  { label: '手机号', key: 'phone' },
  { label: '区域ID', key: 'areaId' },
  { label: '从业年限', key: 'experienceYears' },
  { label: '评分', key: 'avgRating' },
  { label: '完成单数', key: 'completedCount' },
  { label: '认证状态(0待审1通过2驳回)', key: 'certStatus' },
  { label: '状态(1正常0下架)', key: 'status' },
]
</script>

<template>
  <div class="page-container">
    <PageCard title="服务人员管理">
      <SearchForm :cols="4" @search="search" @reset="reset">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="昵称" clearable :prefix-icon="Search" />
        </el-form-item>
        <el-form-item label="认证状态">
          <el-select v-model="query.certStatus" placeholder="全部状态" clearable>
            <el-option v-for="c in CERT_OPTIONS" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
      </SearchForm>

      <TableWrapper
        :data="list as unknown as Record<string, unknown>[]"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        :error="error"
        export-name="服务人员列表"
        :export-columns="exportColumns"
        empty-text="暂无服务人员"
        @update:page="changePage"
        @update:size="changeSize"
        @refresh="refresh"
      >
        <template #toolbar>
          <span class="aux-text">共 {{ total }} 名服务人员</span>
        </template>

        <el-table-column prop="userId" label="用户ID" width="90" />
        <el-table-column label="头像" width="76" align="center">
          <template #default="{ row }">
            <el-avatar :size="36">
              <img v-if="row.avatar" :src="row.avatar" alt="" />
              <span v-else>{{ (row.nickname || row.username || '?').slice(0, 1) }}</span>
            </el-avatar>
          </template>
        </el-table-column>
        <el-table-column prop="nickname" label="昵称" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.nickname || '-' }}</template>
        </el-table-column>
        <el-table-column prop="username" label="账号" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.username || '-' }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="130">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="服务区域" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ areaName(row.areaId) }}</template>
        </el-table-column>
        <el-table-column label="从业年限" width="90" align="center">
          <template #default="{ row }">{{ row.experienceYears ?? 0 }} 年</template>
        </el-table-column>
        <el-table-column label="评分" width="80" align="center">
          <template #default="{ row }">{{ Number(row.avgRating ?? 0).toFixed(1) }}</template>
        </el-table-column>
        <el-table-column label="完成单" width="90" align="center">
          <template #default="{ row }">{{ row.completedCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="认证状态" width="100" align="center">
          <template #default="{ row }">
            <StatusTag kind="cert" :value="row.certStatus ?? 0" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusOf(row.status).type" effect="light" round>
              {{ statusOf(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="warning" @click="openAudit(row)">资质审核</el-button>
            <ConfirmAction
              :title="row.status === 1 ? '确认下架该服务人员？' : '确认上架该服务人员？'"
              :message="row.status === 1 ? '下架后不在用户端服务人员列表展示。' : '上架后用户端可浏览并预约该服务人员。'"
              :danger="row.status === 1"
              @confirm="onToggleStatus(row)"
            >
              <el-button link :type="row.status === 1 ? 'danger' : 'success'">
                {{ row.status === 1 ? '下架' : '上架' }}
              </el-button>
            </ConfirmAction>
          </template>
        </el-table-column>
      </TableWrapper>
    </PageCard>

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" title="服务人员详情" width="min(660px, 94vw)">
      <div class="detail-body" v-loading="detailLoading" element-loading-background="rgba(255,255,255,0.7)">
        <template v-if="detail">
          <div class="detail-head">
            <el-avatar :size="56">
              <img v-if="current?.avatar" :src="current?.avatar" alt="" />
              <span v-else>{{ (current?.nickname || '?').slice(0, 1) }}</span>
            </el-avatar>
            <div class="detail-head__info">
              <div class="detail-head__name">{{ current?.nickname || current?.username || '-' }}</div>
              <div class="aux-text">用户 ID：{{ detail.userId }} · {{ current?.phone || '未绑定手机号' }}</div>
            </div>
            <StatusTag kind="cert" :value="detail.certStatus ?? 0" />
          </div>

          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="服务区域">{{ areaName(detail.areaId) }}</el-descriptions-item>
            <el-descriptions-item label="从业年限">{{ detail.experienceYears ?? 0 }} 年</el-descriptions-item>
            <el-descriptions-item label="综合评分">
              {{ Number(detail.avgRating ?? 0).toFixed(1) }} 分
            </el-descriptions-item>
            <el-descriptions-item label="完成订单">{{ detail.completedCount ?? 0 }} 单</el-descriptions-item>
            <el-descriptions-item label="账号状态">{{ statusOf(detail.status).label }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ fmtTime(detail.updatedAt) }}</el-descriptions-item>
            <el-descriptions-item label="擅长品种" :span="2">{{ breedText(detail.goodAtBreeds) }}</el-descriptions-item>
            <el-descriptions-item label="个人简介" :span="2">{{ detail.intro || '-' }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.rejectReason" label="驳回原因" :span="2">
              {{ detail.rejectReason }}
            </el-descriptions-item>
            <el-descriptions-item label="资质证书" :span="2">
              <div v-if="certImages.length" class="detail-images">
                <el-image
                  v-for="(img, i) in certImages"
                  :key="i"
                  :src="img"
                  fit="cover"
                  class="detail-image"
                  :preview-src-list="certImages"
                  :initial-index="i"
                  preview-teleported
                />
              </div>
              <span v-else class="aux-text">暂无证书图片</span>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ fmtTime(detail.createdAt) }}</el-descriptions-item>
          </el-descriptions>
        </template>

        <el-empty v-else-if="!detailLoading" description="详情不存在或加载失败" :image-size="80" />
      </div>
    </el-dialog>

    <!-- 资质审核 -->
    <el-dialog v-model="auditVisible" title="资质审核" width="min(460px, 92vw)">
      <el-form ref="auditRef" :model="auditForm" :rules="auditRules" label-width="90px">
        <el-form-item label="服务人员">
          <el-input :model-value="auditForm.target" disabled />
        </el-form-item>
        <el-form-item label="审核结果" prop="certStatus">
          <el-radio-group v-model="auditForm.certStatus">
            <el-radio :value="1">通过</el-radio>
            <el-radio :value="2">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="驳回原因" prop="rejectReason">
          <el-input
            v-model="auditForm.rejectReason"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            :disabled="auditForm.certStatus === 1"
            placeholder="驳回时必填，将连同结果一并推送给服务人员"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <ConfirmAction
          v-if="auditForm.certStatus === 2"
          title="确认驳回该服务人员资质？"
          message="驳回后将推送站内通知，该服务人员无法接单。"
          danger
          confirm-text="确认驳回"
          @confirm="submitAudit"
        >
          <el-button type="danger">提交</el-button>
        </ConfirmAction>
        <el-button v-else type="primary" @click="submitAudit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.detail-body {
  min-height: 160px;
}

.detail-head {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.detail-head__info {
  flex: 1 1 auto;
  min-width: 160px;
}

.detail-head__name {
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 4px;
}

.detail-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.detail-image {
  width: 72px;
  height: 72px;
  border-radius: var(--radius-tag);
  overflow: hidden;
}
</style>
