<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import SearchForm from '@/components/common/SearchForm.vue'
import TableWrapper from '@/components/common/TableWrapper.vue'
import PageCard from '@/components/common/PageCard.vue'
import { usePage } from '@/composables/usePage'
import { fetchAdminNotices, resendNotice } from '@/api/admin/ops'
import { fmtTime } from '@/utils/constants'
import type { Notice } from '@/types'

/** 通知管理（PRD 7.1）：全量站内通知查询 + 补发（写审计） */

interface NoticeQuery {
  /** 接收人 ID（后端支持） */
  userId: string
  /** 通知类型 —— 后端无该参数，仅在当前页内检索 */
  type: string
  /** 标题/内容关键词 —— 后端无该参数，仅在当前页内检索 */
  keyword: string
}

const { list, total, page, size, loading, error, query, search, reset, changePage, changeSize, refresh } =
  usePage<Notice, NoticeQuery>(
    (q) => fetchAdminNotices({ userId: toNum(q.userId), page: q.page, size: q.size }),
    { defaultQuery: { userId: '', type: '', keyword: '' } },
  )

/** 输入框文本 → 合法正整数 */
function toNum(v: string): number | undefined {
  const n = Number(v)
  return v.trim() !== '' && Number.isInteger(n) && n > 0 ? n : undefined
}

/** 通知类型字典（notice.type: ORDER/REVIEW/CERT/SYSTEM） */
type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'
const TYPE_DICT: Record<string, { label: string; type: TagType }> = {
  ORDER: { label: '订单', type: 'primary' },
  REVIEW: { label: '评价', type: 'success' },
  CERT: { label: '资质审核', type: 'warning' },
  SYSTEM: { label: '系统', type: 'info' },
}

function typeOf(v: unknown): { label: string; type: TagType } {
  const key = String(v ?? '')
  return TYPE_DICT[key] ?? { label: key || '未知', type: 'info' }
}

/* ---------- 页内筛选：类型 + 关键词 ---------- */
const keyword = computed(() => query.keyword.trim().toLowerCase())
const hasLocalFilter = computed(() => !!query.type || keyword.value !== '')
const displayList = computed(() => {
  const t = query.type
  const kw = keyword.value
  if (!t && !kw) return list.value
  return list.value.filter((r) => {
    if (t && String(r.type ?? '') !== t) return false
    if (kw && !`${r.title ?? ''} ${r.content ?? ''}`.toLowerCase().includes(kw)) return false
    return true
  })
})
const tableData = computed(() => displayList.value as unknown as Record<string, unknown>[])

/* ---------- 详情 ---------- */
const detailVisible = ref(false)
const current = ref<Notice | null>(null)

function openDetail(row: Notice) {
  current.value = row
  detailVisible.value = true
}

/* ---------- 补发通知 ---------- */
const resendVisible = ref(false)
const resendRef = ref<FormInstance>()
const resendSaving = ref(false)
const resendForm = reactive({ userId: '', title: '', content: '' })

const resendRules: FormRules = {
  userId: [
    {
      validator: (_rule, _value, cb) => {
        const n = Number(resendForm.userId)
        if (!resendForm.userId.trim()) return cb(new Error('请输入接收人用户 ID'))
        if (!Number.isInteger(n) || n <= 0) return cb(new Error('用户 ID 必须为正整数'))
        cb()
      },
      trigger: 'blur',
    },
  ],
  title: [
    { required: true, message: '请输入通知标题', trigger: 'blur' },
    { max: 128, message: '标题不能超过 128 字', trigger: 'blur' },
  ],
  content: [{ max: 500, message: '内容不能超过 500 字', trigger: 'blur' }],
}

function openResend(userId?: number) {
  resendForm.userId = userId ? String(userId) : ''
  resendForm.title = ''
  resendForm.content = ''
  resendVisible.value = true
  resendRef.value?.clearValidate()
}

async function submitResend() {
  const valid = await resendRef.value?.validate().catch(() => false)
  if (!valid) return
  resendSaving.value = true
  try {
    await resendNotice({
      userId: Number(resendForm.userId),
      title: resendForm.title.trim(),
      content: resendForm.content.trim() || undefined,
    })
    ElMessage.success('通知已补发')
    resendVisible.value = false
    await refresh()
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    resendSaving.value = false
  }
}

const exportColumns = [
  { label: 'ID', key: 'id' },
  { label: '接收人ID', key: 'userId' },
  { label: '类型', key: 'type' },
  { label: '标题', key: 'title' },
  { label: '内容', key: 'content' },
  { label: '业务ID', key: 'bizId' },
  { label: '是否已读', key: 'isRead' },
  { label: '发送时间', key: 'createdAt' },
]
</script>

<template>
  <div class="page-container">
    <PageCard title="通知管理">
      <template #header>
        <el-button type="primary" :icon="Plus" @click="openResend()">补发通知</el-button>
      </template>

      <SearchForm :cols="3" @search="search" @reset="reset">
        <el-form-item label="接收人ID">
          <el-input v-model="query.userId" placeholder="接收用户 ID" clearable :prefix-icon="Search" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.type" placeholder="全部类型" clearable>
            <el-option label="订单" value="ORDER" />
            <el-option label="评价" value="REVIEW" />
            <el-option label="资质审核" value="CERT" />
            <el-option label="系统" value="SYSTEM" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="标题 / 内容关键词" clearable :prefix-icon="Search" />
        </el-form-item>
      </SearchForm>

      <TableWrapper
        :data="tableData"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        :error="error"
        export-name="站内通知列表"
        :export-columns="exportColumns"
        empty-text="暂无通知记录"
        @update:page="changePage"
        @update:size="changeSize"
        @refresh="refresh"
      >
        <template #toolbar>
          <span class="aux-text">共 {{ total }} 条通知</span>
          <span v-if="hasLocalFilter" class="local-filter-tip">
            页内筛选：类型与关键词仅在当前页 {{ list.length }} 条记录中检索
          </span>
        </template>

        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="接收人" width="110">
          <template #default="{ row }">用户 {{ row.userId }}</template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="typeOf(row.type).type" effect="light" round>{{ typeOf(row.type).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="内容" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ row.content || '-' }}</template>
        </el-table-column>
        <el-table-column label="业务ID" width="90" align="center">
          <template #default="{ row }">{{ row.bizId ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="阅读状态" width="90">
          <template #default="{ row }">
            <el-tag :type="Number(row.isRead ?? 0) === 1 ? 'info' : 'danger'" effect="light" round>
              {{ Number(row.isRead ?? 0) === 1 ? '已读' : '未读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发送时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="warning" @click="openResend(row.userId)">补发</el-button>
          </template>
        </el-table-column>
      </TableWrapper>
    </PageCard>

    <!-- 通知详情 -->
    <el-dialog v-model="detailVisible" title="通知详情" width="min(560px, 94vw)">
      <template v-if="current">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="通知 ID">{{ current.id }}</el-descriptions-item>
          <el-descriptions-item label="接收人">用户 {{ current.userId }}</el-descriptions-item>
          <el-descriptions-item label="类型">
            <el-tag :type="typeOf(current.type).type" effect="light" round>
              {{ typeOf(current.type).label }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="关联业务">
            {{ current.bizId ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="阅读状态">
            {{ Number(current.isRead ?? 0) === 1 ? '已读' : '未读' }}
          </el-descriptions-item>
          <el-descriptions-item label="发送时间">{{ fmtTime(current.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="标题" :span="2">{{ current.title }}</el-descriptions-item>
          <el-descriptions-item label="内容" :span="2">
            <span class="notice-content">{{ current.content || '（无正文）' }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </template>
      <el-empty v-else description="暂无数据" :image-size="90" />
    </el-dialog>

    <!-- 补发通知 -->
    <el-dialog v-model="resendVisible" title="补发站内通知" width="min(520px, 94vw)">
      <el-form ref="resendRef" :model="resendForm" :rules="resendRules" label-width="90px">
        <el-form-item label="接收人ID" prop="userId">
          <el-input v-model="resendForm.userId" placeholder="接收用户 ID（正整数）" />
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="resendForm.title" maxlength="128" show-word-limit placeholder="通知标题" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input
            v-model="resendForm.content"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="通知正文（选填）"
          />
          <p class="resend-tip aux-text">补发类型固定为「系统」通知，操作会写入审计日志。</p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resendVisible = false">取消</el-button>
        <el-button type="primary" :loading="resendSaving" @click="submitResend">确认补发</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.local-filter-tip {
  margin-left: 12px;
  font-size: var(--font-size-aux);
  color: var(--color-warning);
}

.notice-content {
  white-space: pre-wrap;
  word-break: break-word;
}

.resend-tip {
  margin: 6px 0 0;
  line-height: 1.5;
}
</style>
