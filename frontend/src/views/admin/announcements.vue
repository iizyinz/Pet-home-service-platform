<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import SearchForm from '@/components/common/SearchForm.vue'
import TableWrapper from '@/components/common/TableWrapper.vue'
import PageCard from '@/components/common/PageCard.vue'
import ConfirmAction from '@/components/common/ConfirmAction.vue'
import { usePage } from '@/composables/usePage'
import {
  fetchAdminAnnouncements,
  createAnnouncement,
  updateAnnouncement,
  deleteAnnouncement,
  type AnnouncementPayload,
  type AnnouncementRow,
} from '@/api/admin/ops'
import { fmtTime } from '@/utils/constants'

/** 系统公告（PRD 7.1）：公告增删改、发布 / 下架，小程序端公告入口同步展示 */

interface AnnouncementQuery {
  /** 标题关键词（后端 LIKE） */
  keyword: string
  /** 发布状态 —— 后端无该参数，仅在当前页内检索 */
  status: number | ''
}

const { list, total, page, size, loading, error, query, search, reset, changePage, changeSize, refresh } =
  usePage<AnnouncementRow, AnnouncementQuery>(
    (q) => fetchAdminAnnouncements({ keyword: q.keyword, page: q.page, size: q.size }),
    { defaultQuery: { keyword: '', status: '' } },
  )

/* ---------- 页内筛选：发布状态 ---------- */
/** 选择框（可能被 clearable 清成 undefined）→ 整数参数 */
function toInt(v: unknown): number | undefined {
  if (v === '' || v === null || v === undefined) return undefined
  const n = Number(v)
  return Number.isInteger(n) ? n : undefined
}

const statusFilter = computed(() => toInt(query.status))
const hasLocalFilter = computed(() => statusFilter.value !== undefined)
const displayList = computed(() => {
  const st = statusFilter.value
  if (st === undefined) return list.value
  return list.value.filter((r) => Number(r.status ?? 1) === st)
})
const tableData = computed(() => displayList.value as unknown as Record<string, unknown>[])

/* ---------- 新增 / 编辑 ---------- */
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const saving = ref(false)
const form = reactive({ id: 0, title: '', content: '', status: 1 })

const rules: FormRules = {
  title: [
    { required: true, message: '请输入公告标题', trigger: 'blur' },
    { max: 50, message: '标题不能超过 50 字', trigger: 'blur' },
  ],
  content: [
    { required: true, message: '请输入公告内容', trigger: 'blur' },
    { max: 2000, message: '内容不能超过 2000 字', trigger: 'blur' },
  ],
}

const dialogTitle = computed(() => (form.id ? '编辑公告' : '新增公告'))

function openCreate() {
  form.id = 0
  form.title = ''
  form.content = ''
  form.status = 1
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function openEdit(row: AnnouncementRow) {
  form.id = row.id
  form.title = row.title ?? ''
  form.content = row.content ?? ''
  form.status = Number(row.status ?? 1)
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const payload: AnnouncementPayload = {
    title: form.title.trim(),
    content: form.content.trim(),
    status: form.status,
  }
  saving.value = true
  try {
    if (form.id) {
      await updateAnnouncement(form.id, payload)
      ElMessage.success('公告已更新')
    } else {
      await createAnnouncement(payload)
      ElMessage.success('公告已新增')
    }
    dialogVisible.value = false
    await refresh()
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    saving.value = false
  }
}

/* ---------- 发布 / 下架 / 删除 ---------- */
async function toggleStatus(row: AnnouncementRow) {
  const next = Number(row.status ?? 1) === 1 ? 0 : 1
  try {
    await updateAnnouncement(row.id, {
      title: row.title,
      content: row.content ?? '',
      status: next,
    })
    ElMessage.success(next === 1 ? '公告已发布' : '公告已下架，小程序端不再展示')
    await refresh()
  } catch {
    /* 拦截器已统一提示 */
  }
}

async function remove(row: AnnouncementRow) {
  try {
    await deleteAnnouncement(row.id)
    ElMessage.success('公告已删除')
    await refresh()
  } catch {
    /* 拦截器已统一提示 */
  }
}

const exportColumns = [
  { label: 'ID', key: 'id' },
  { label: '标题', key: 'title' },
  { label: '内容', key: 'content' },
  { label: '状态', key: 'status' },
  { label: '创建时间', key: 'createdAt' },
  { label: '更新时间', key: 'updatedAt' },
]
</script>

<template>
  <div class="page-container">
    <PageCard title="系统公告">
      <template #header>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增公告</el-button>
      </template>

      <SearchForm :cols="3" @search="search" @reset="reset">
        <el-form-item label="标题">
          <el-input v-model="query.keyword" placeholder="公告标题关键词" clearable :prefix-icon="Search" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部状态" clearable>
            <el-option label="已发布" :value="1" />
            <el-option label="已下架" :value="0" />
          </el-select>
        </el-form-item>
      </SearchForm>

      <TableWrapper
        :data="tableData"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        :error="error"
        export-name="系统公告列表"
        :export-columns="exportColumns"
        empty-text="暂无公告"
        @update:page="changePage"
        @update:size="changeSize"
        @refresh="refresh"
      >
        <template #toolbar>
          <span class="aux-text">共 {{ total }} 条公告</span>
          <span v-if="hasLocalFilter" class="local-filter-tip">页内筛选：状态仅在当前页 {{ list.length }} 条记录中检索</span>
        </template>

        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="内容" min-width="300" show-overflow-tooltip>
          <template #default="{ row }">{{ row.content || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="Number(row.status ?? 1) === 1 ? 'success' : 'info'" effect="light" round>
              {{ Number(row.status ?? 1) === 1 ? '已发布' : '已下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <ConfirmAction
              v-if="Number(row.status ?? 1) === 1"
              title="确认下架该公告？"
              message="下架后小程序端公告入口不再展示。"
              danger
              @confirm="toggleStatus(row)"
            >
              <el-button link type="danger">下架</el-button>
            </ConfirmAction>
            <el-button v-else link type="success" @click="toggleStatus(row)">发布</el-button>
            <ConfirmAction
              title="确认删除该公告？"
              message="删除后不可恢复。"
              danger
              @confirm="remove(row)"
            >
              <el-button link type="danger">删除</el-button>
            </ConfirmAction>
          </template>
        </el-table-column>
      </TableWrapper>
    </PageCard>

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="min(640px, 94vw)">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="50" show-word-limit placeholder="公告标题" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="8"
            maxlength="2000"
            show-word-limit
            placeholder="公告正文，支持多行文本"
          />
        </el-form-item>
        <el-form-item label="发布状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">立即发布</el-radio>
            <el-radio :value="0">暂存（下架）</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">{{ form.id ? '保存修改' : '确认新增' }}</el-button>
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
</style>
