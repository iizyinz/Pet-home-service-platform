<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import SearchForm from '@/components/common/SearchForm.vue'
import TableWrapper from '@/components/common/TableWrapper.vue'
import PageCard from '@/components/common/PageCard.vue'
import ConfirmAction from '@/components/common/ConfirmAction.vue'
import ImageUploader from '@/components/common/ImageUploader.vue'
import { usePage, type PageQuery } from '@/composables/usePage'
import type { PageResult } from '@/utils/request'
import {
  fetchAdminServiceItems,
  fetchAllAdminServiceItems,
  fetchAllAdminServiceTypes,
  createServiceItem,
  updateServiceItem,
  deleteServiceItem,
  type AdminServiceItemRow,
  type AdminServiceTypeRow,
  type ServiceItemPayload,
} from '@/api/admin/catalog'
import { fmtMoney, fmtTime } from '@/utils/constants'

/**
 * 服务项目管理（PRD 7.1：名称/类型/适用品种/价格/时长/注意事项，价格时长必填，下架不影响历史订单快照）
 * 后端管理端列表仅支持 keyword 检索，类型与上下架筛选在前端本地完成。
 */
type ItemQuery = { keyword: string; typeId: number | ''; status: number | '' }

async function fetchItems(q: ItemQuery & PageQuery): Promise<PageResult<AdminServiceItemRow>> {
  const page = q.page ?? 1
  const size = q.size ?? 10
  if (q.typeId === '' && q.status === '') {
    return fetchAdminServiceItems({ keyword: q.keyword, page, size })
  }
  const all = await fetchAllAdminServiceItems(q.keyword)
  const filtered = all.filter(
    (it) => (q.typeId === '' || it.typeId === q.typeId) && (q.status === '' || it.status === q.status),
  )
  const start = (page - 1) * size
  return { list: filtered.slice(start, start + size), total: filtered.length, page, size }
}

const { list, total, page, size, loading, error, query, search, reset, changePage, changeSize, refresh } =
  usePage<AdminServiceItemRow, ItemQuery>(fetchItems, {
    defaultQuery: { keyword: '', typeId: '', status: '' },
  })

/** 服务类型（含停用类型，供筛选与名称映射） */
const types = ref<AdminServiceTypeRow[]>([])
const typeMap = computed(
  () => new Map<number, string>(types.value.map((t): [number, string] => [t.id, t.name])),
)

function typeName(id?: number): string {
  if (id === undefined) return '-'
  return typeMap.value.get(id) ?? `#${id}`
}

async function loadTypes() {
  try {
    types.value = await fetchAllAdminServiceTypes()
  } catch {
    /* 拦截器已统一提示 */
  }
}
onMounted(loadTypes)

/** 上下架状态 */
const ITEM_STATUS: Record<number, { label: string; type: 'success' | 'danger' }> = {
  1: { label: '上架', type: 'success' },
  0: { label: '下架', type: 'danger' },
}
const STATUS_FALLBACK = { label: '未知', type: 'info' as const }
function statusOf(v?: number) {
  return ITEM_STATUS[v === 0 ? 0 : 1] ?? STATUS_FALLBACK
}

/** 后端 applicableBreeds 为 JSON 数组字符串 */
function breedText(v?: string): string {
  if (!v) return '-'
  try {
    const arr: unknown = JSON.parse(v)
    if (Array.isArray(arr)) return arr.length ? arr.join('、') : '-'
    return v
  } catch {
    return v
  }
}

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

/* ---------- 新增 / 编辑 ---------- */
interface ItemForm {
  typeId: number | undefined
  name: string
  price: number
  durationMinutes: number
  coverUrl: string
  breeds: string[]
  notice: string
  status: number
}

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)
const form = reactive<ItemForm>({
  typeId: undefined,
  name: '',
  price: 0,
  durationMinutes: 60,
  coverUrl: '',
  breeds: [],
  notice: '',
  status: 1,
})

const coverList = computed<string[]>({
  get: () => (form.coverUrl ? [form.coverUrl] : []),
  set: (v: string[]) => {
    form.coverUrl = v[0] ?? ''
  },
})

const rules: FormRules = {
  typeId: [{ required: true, message: '请选择所属服务类型', trigger: 'change' }],
  name: [
    { required: true, message: '请输入项目名称', trigger: 'blur' },
    { max: 128, message: '名称不能超过 128 个字符', trigger: 'blur' },
  ],
  price: [
    { required: true, message: '请输入单价', trigger: 'blur' },
    {
      validator: (_r, value: number, cb) => {
        if (!value || value <= 0) return cb(new Error('单价必须大于 0'))
        if (value > 100000) return cb(new Error('单价不能超过 100000 元'))
        cb()
      },
      trigger: 'blur',
    },
  ],
  durationMinutes: [
    { required: true, message: '请输入服务时长', trigger: 'blur' },
    {
      validator: (_r, value: number, cb) => {
        if (!value || value <= 0) return cb(new Error('服务时长必须大于 0 分钟'))
        cb()
      },
      trigger: 'blur',
    },
  ],
}

function openCreate() {
  editingId.value = null
  form.typeId = types.value[0]?.id
  form.name = ''
  form.price = 0
  form.durationMinutes = 60
  form.coverUrl = ''
  form.breeds = []
  form.notice = ''
  form.status = 1
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function openEdit(row: AdminServiceItemRow) {
  editingId.value = row.id
  form.typeId = row.typeId
  form.name = row.name
  form.price = row.price
  form.durationMinutes = row.durationMinutes ?? 60
  form.coverUrl = row.coverUrl ?? ''
  form.breeds = parseBreeds(row.applicableBreeds)
  form.notice = row.notice ?? ''
  form.status = row.status ?? 1
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function parseBreeds(v?: string): string[] {
  if (!v) return []
  try {
    const arr: unknown = JSON.parse(v)
    return Array.isArray(arr) ? arr.map((b) => String(b)) : []
  } catch {
    return []
  }
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const payload: ServiceItemPayload = {
    typeId: form.typeId ?? 0,
    name: form.name.trim(),
    price: form.price,
    durationMinutes: form.durationMinutes,
    coverUrl: form.coverUrl,
    applicableBreeds: JSON.stringify(form.breeds),
    notice: form.notice,
    status: form.status,
  }
  if (editingId.value !== null) {
    await updateServiceItem(editingId.value, payload)
    ElMessage.success('服务项目已更新')
  } else {
    await createServiceItem(payload)
    ElMessage.success('服务项目已创建')
  }
  dialogVisible.value = false
  await refresh()
}

/* ---------- 上架 / 下架 / 删除 ---------- */
async function onToggleStatus(row: AdminServiceItemRow) {
  const next = row.status === 1 ? 0 : 1
  await updateServiceItem(row.id, {
    typeId: row.typeId ?? 0,
    name: row.name,
    price: row.price,
    durationMinutes: row.durationMinutes ?? 60,
    coverUrl: row.coverUrl,
    applicableBreeds: row.applicableBreeds,
    notice: row.notice,
    status: next,
  })
  ElMessage.success(next === 1 ? '已上架' : '已下架')
  await refresh()
}

async function onDelete(row: AdminServiceItemRow) {
  await deleteServiceItem(row.id)
  ElMessage.success('服务项目已删除')
  await refresh()
}

const exportColumns = [
  { label: 'ID', key: 'id' },
  { label: '项目名称', key: 'name' },
  { label: '类型ID', key: 'typeId' },
  { label: '单价(元)', key: 'price' },
  { label: '时长(分钟)', key: 'durationMinutes' },
  { label: '适用品种', key: 'applicableBreeds' },
  { label: '注意事项', key: 'notice' },
  { label: '状态(1上架0下架)', key: 'status' },
  { label: '更新时间', key: 'updatedAt' },
]
</script>

<template>
  <div class="page-container">
    <PageCard title="服务项目管理">
      <SearchForm :cols="4" @search="search" @reset="reset">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="项目名称" clearable :prefix-icon="Search" />
        </el-form-item>
        <el-form-item label="服务类型">
          <el-select v-model="query.typeId" placeholder="全部类型" clearable>
            <el-option v-for="t in types" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部状态" clearable>
            <el-option label="上架中" :value="1" />
            <el-option label="已下架" :value="0" />
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
        export-name="服务项目列表"
        :export-columns="exportColumns"
        empty-text="暂无服务项目"
        @update:page="changePage"
        @update:size="changeSize"
        @refresh="refresh"
      >
        <template #toolbar>
          <div class="toolbar-left">
            <el-button type="primary" @click="openCreate">新增服务项目</el-button>
            <span class="aux-text">共 {{ total }} 个项目</span>
          </div>
        </template>

        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="封面" width="90" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.coverUrl"
              :src="row.coverUrl"
              fit="cover"
              class="item-cover"
              preview-teleported
              :preview-src-list="[row.coverUrl]"
            />
            <span v-else class="aux-text">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="项目名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="所属类型" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ typeName(row.typeId) }}</template>
        </el-table-column>
        <el-table-column label="单价(元)" width="110" align="right">
          <template #default="{ row }">
            <span class="price-text">{{ fmtMoney(row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="时长" width="100" align="center">
          <template #default="{ row }">{{ row.durationMinutes ? row.durationMinutes + ' 分钟' : '-' }}</template>
        </el-table-column>
        <el-table-column label="适用品种" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ breedText(row.applicableBreeds) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusOf(row.status).type" effect="light" round>
              {{ statusOf(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <ConfirmAction
              :title="row.status === 1 ? '确认下架该项目？' : '确认上架该项目？'"
              :message="row.status === 1 ? '下架后用户端不可下单，历史订单快照不受影响。' : '上架后用户端可正常浏览与下单。'"
              :danger="row.status === 1"
              @confirm="onToggleStatus(row)"
            >
              <el-button link :type="row.status === 1 ? 'warning' : 'success'">
                {{ row.status === 1 ? '下架' : '上架' }}
              </el-button>
            </ConfirmAction>
            <ConfirmAction
              title="确认删除该服务项目？"
              message="删除后不可恢复；历史订单保留名称与价格快照。"
              danger
              @confirm="onDelete(row)"
            >
              <el-button link type="danger">删除</el-button>
            </ConfirmAction>
          </template>
        </el-table-column>
      </TableWrapper>
    </PageCard>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId !== null ? '编辑服务项目' : '新增服务项目'"
      width="min(560px, 94vw)"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="服务类型" prop="typeId">
          <el-select v-model="form.typeId" placeholder="请选择服务类型" style="width: 100%">
            <el-option v-for="t in types" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目名称" prop="name">
          <el-input v-model="form.name" placeholder="如：上门喂猫" maxlength="128" show-word-limit />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="单价" prop="price" class="form-row__half">
            <el-input-number
              v-model="form.price"
              :min="0"
              :max="100000"
              :precision="2"
              :step="10"
              controls-position="right"
              style="width: 100%"
            />
            <p class="form-tip aux-text">单位：元 / 次</p>
          </el-form-item>
          <el-form-item label="服务时长" prop="durationMinutes" class="form-row__half">
            <el-input-number
              v-model="form.durationMinutes"
              :min="1"
              :max="1440"
              :step="10"
              controls-position="right"
              style="width: 100%"
            />
            <p class="form-tip aux-text">单位：分钟</p>
          </el-form-item>
        </div>
        <el-form-item label="适用品种" prop="breeds">
          <el-select
            v-model="form.breeds"
            multiple
            filterable
            allow-create
            default-first-option
            clearable
            placeholder="可多选，也可直接输入新品种后回车"
            style="width: 100%"
          >
            <el-option v-for="b in BREED_OPTIONS" :key="b" :label="b" :value="b" />
          </el-select>
        </el-form-item>
        <el-form-item label="封面图" prop="coverUrl">
          <ImageUploader v-model="coverList" :limit="1" tip="建议 1:1 封面图，jpg / png，不超过 5MB" />
        </el-form-item>
        <el-form-item label="注意事项" prop="notice">
          <el-input
            v-model="form.notice"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="如：请提前备好宠物用品、确认宠物状态"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="上架"
            inactive-text="下架"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">{{ editingId !== null ? '保存修改' : '创建' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.item-cover {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-tag);
  overflow: hidden;
}

.form-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.form-row__half {
  flex: 1 1 180px;
  min-width: 160px;
}

.form-tip {
  margin: 6px 0 0;
  line-height: 1.5;
}
</style>
