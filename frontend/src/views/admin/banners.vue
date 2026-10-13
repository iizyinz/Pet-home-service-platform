<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import SearchForm from '@/components/common/SearchForm.vue'
import TableWrapper from '@/components/common/TableWrapper.vue'
import PageCard from '@/components/common/PageCard.vue'
import ConfirmAction from '@/components/common/ConfirmAction.vue'
import ImageUploader from '@/components/common/ImageUploader.vue'
import { usePage } from '@/composables/usePage'
import {
  fetchAdminBanners,
  createBanner,
  updateBanner,
  deleteBanner,
  type BannerPayload,
  type BannerRow,
} from '@/api/admin/ops'
import { fmtTime } from '@/utils/constants'

/** 轮播图管理（PRD 7.1）：首页轮播图增删改、排序、启停（写操作统一记审计） */

interface BannerQuery {
  /** 标题关键词（后端 LIKE） */
  keyword: string
  /** 启停状态 —— 后端无该参数，仅在当前页内检索 */
  status: number | ''
}

const { list, total, page, size, loading, error, query, search, reset, changePage, changeSize, refresh } =
  usePage<BannerRow, BannerQuery>((q) => fetchAdminBanners({ keyword: q.keyword, page: q.page, size: q.size }), {
    defaultQuery: { keyword: '', status: '' },
  })

/* ---------- 页内筛选：启停状态 ---------- */
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
const form = reactive({ id: 0, title: '', images: [] as string[], link: '', sort: 0, status: 1 })

const rules: FormRules = {
  title: [
    { required: true, message: '请输入轮播图标题', trigger: 'blur' },
    { max: 50, message: '标题不能超过 50 字', trigger: 'blur' },
  ],
  images: [
    {
      validator: (_rule, _value, cb) => (form.images.length ? cb() : cb(new Error('请上传轮播图图片'))),
      trigger: 'change',
    },
  ],
  link: [
    {
      validator: (_rule, value: string, cb) => {
        const v = (value ?? '').trim()
        if (!v) return cb()
        if (v.startsWith('/') || /^https?:\/\/.+/.test(v)) return cb()
        cb(new Error('跳转地址需以 / 或 http(s):// 开头'))
      },
      trigger: 'blur',
    },
  ],
}

const dialogTitle = computed(() => (form.id ? '编辑轮播图' : '新增轮播图'))

function openCreate() {
  form.id = 0
  form.title = ''
  form.images = []
  form.link = ''
  form.sort = 0
  form.status = 1
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function openEdit(row: BannerRow) {
  form.id = row.id
  form.title = row.title ?? ''
  form.images = row.imageUrl ? [row.imageUrl] : []
  form.link = row.link ?? ''
  form.sort = Number(row.sort ?? 0)
  form.status = Number(row.status ?? 1)
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const payload: BannerPayload = {
    title: form.title.trim(),
    imageUrl: form.images[0] ?? '',
    link: form.link.trim(),
    sort: form.sort,
    status: form.status,
  }
  saving.value = true
  try {
    if (form.id) {
      await updateBanner(form.id, payload)
      ElMessage.success('轮播图已更新')
    } else {
      await createBanner(payload)
      ElMessage.success('轮播图已新增')
    }
    dialogVisible.value = false
    await refresh()
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    saving.value = false
  }
}

/* ---------- 启停 / 删除 ---------- */
async function toggleStatus(row: BannerRow) {
  const next = Number(row.status ?? 1) === 1 ? 0 : 1
  try {
    await updateBanner(row.id, {
      title: row.title ?? '',
      imageUrl: row.imageUrl,
      link: row.link,
      sort: Number(row.sort ?? 0),
      status: next,
    })
    ElMessage.success(next === 1 ? '已启用，首页轮播将展示' : '已停用，首页不再展示')
    await refresh()
  } catch {
    /* 拦截器已统一提示 */
  }
}

async function remove(row: BannerRow) {
  try {
    await deleteBanner(row.id)
    ElMessage.success('轮播图已删除')
    await refresh()
  } catch {
    /* 拦截器已统一提示 */
  }
}

const exportColumns = [
  { label: 'ID', key: 'id' },
  { label: '标题', key: 'title' },
  { label: '图片', key: 'imageUrl' },
  { label: '跳转链接', key: 'link' },
  { label: '排序', key: 'sort' },
  { label: '状态', key: 'status' },
  { label: '创建时间', key: 'createdAt' },
]
</script>

<template>
  <div class="page-container">
    <PageCard title="轮播图管理">
      <template #header>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增轮播图</el-button>
      </template>

      <SearchForm :cols="3" @search="search" @reset="reset">
        <el-form-item label="标题">
          <el-input v-model="query.keyword" placeholder="轮播图标题关键词" clearable :prefix-icon="Search" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部状态" clearable>
            <el-option label="已启用" :value="1" />
            <el-option label="已停用" :value="0" />
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
        export-name="轮播图列表"
        :export-columns="exportColumns"
        empty-text="暂无轮播图"
        @update:page="changePage"
        @update:size="changeSize"
        @refresh="refresh"
      >
        <template #toolbar>
          <span class="aux-text">共 {{ total }} 张轮播图</span>
          <span v-if="hasLocalFilter" class="local-filter-tip">页内筛选：状态仅在当前页 {{ list.length }} 条记录中检索</span>
        </template>

        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="图片" width="160">
          <template #default="{ row }">
            <el-image
              v-if="row.imageUrl"
              :src="row.imageUrl"
              :preview-src-list="[row.imageUrl]"
              fit="cover"
              class="banner-thumb"
              preview-teleported
            />
            <span v-else class="aux-text">未上传</span>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
        <el-table-column label="跳转链接" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.link || '-' }}</template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" align="center">
          <template #default="{ row }">{{ row.sort ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="Number(row.status ?? 1) === 1 ? 'success' : 'info'" effect="light" round>
              {{ Number(row.status ?? 1) === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <ConfirmAction
              v-if="Number(row.status ?? 1) === 1"
              title="确认停用该轮播图？"
              message="停用后小程序首页将不再展示该轮播。"
              danger
              @confirm="toggleStatus(row)"
            >
              <el-button link type="danger">停用</el-button>
            </ConfirmAction>
            <el-button v-else link type="success" @click="toggleStatus(row)">启用</el-button>
            <ConfirmAction
              title="确认删除该轮播图？"
              message="删除后不可恢复，首页将立即移除。"
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="min(560px, 94vw)">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="50" show-word-limit placeholder="首页轮播展示标题" />
        </el-form-item>
        <el-form-item label="轮播图片" prop="images">
          <ImageUploader v-model="form.images" :limit="1" tip="建议尺寸 750×360，支持 jpg / png / webp，单张不超过 5MB" />
        </el-form-item>
        <el-form-item label="跳转链接" prop="link">
          <el-input v-model="form.link" placeholder="选填，如 /services 或 https://…" clearable />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :max="9999" :step="1" />
          <span class="sort-tip aux-text">数字越小越靠前</span>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
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

.banner-thumb {
  width: 120px;
  height: 54px;
  border-radius: var(--radius-tag);
  overflow: hidden;
}

.sort-tip {
  margin-left: 10px;
}
</style>
