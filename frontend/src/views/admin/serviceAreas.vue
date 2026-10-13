<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import SearchForm from '@/components/common/SearchForm.vue'
import TableWrapper from '@/components/common/TableWrapper.vue'
import PageCard from '@/components/common/PageCard.vue'
import ConfirmAction from '@/components/common/ConfirmAction.vue'
import { usePage, type PageQuery } from '@/composables/usePage'
import type { PageResult } from '@/utils/request'
import {
  fetchAdminServiceAreas,
  fetchAllAdminServiceAreas,
  createServiceArea,
  updateServiceArea,
  deleteServiceArea,
  type AdminServiceAreaRow,
  type ServiceAreaPayload,
} from '@/api/admin/catalog'
import { fmtTime } from '@/utils/constants'
import {
  areaCascaderOptions,
  areaCascaderProps,
  findAreaPath,
  resolveAreaPath,
  type ResolvedArea,
} from '@/utils/areaCascader'

/**
 * 服务区域管理（PRD 7.1：区域名称/编码、层级、启用状态；服务人员按区域匹配用户筛选）
 * 后端管理端列表仅支持 keyword，层级与覆盖状态筛选在前端本地完成。
 */
type AreaQuery = { keyword: string; level: number | ''; status: number | '' }

async function fetchAreas(q: AreaQuery & PageQuery): Promise<PageResult<AdminServiceAreaRow>> {
  const page = q.page ?? 1
  const size = q.size ?? 10
  if (q.level === '' && q.status === '') {
    return fetchAdminServiceAreas({ keyword: q.keyword, page, size })
  }
  const all = await fetchAllAdminServiceAreas(q.keyword)
  const filtered = all.filter(
    (a) => (q.level === '' || a.level === q.level) && (q.status === '' || a.status === q.status),
  )
  const start = (page - 1) * size
  return { list: filtered.slice(start, start + size), total: filtered.length, page, size }
}

const { list, total, page, size, loading, error, query, search, reset, changePage, changeSize, refresh } =
  usePage<AdminServiceAreaRow, AreaQuery>(fetchAreas, {
    defaultQuery: { keyword: '', level: '', status: '' },
  })

/** 全量区域：用于上级区域选择与名称映射 */
const allAreas = ref<AdminServiceAreaRow[]>([])
const areaMap = computed(
  () => new Map<number, string>(allAreas.value.map((a): [number, string] => [a.id, a.name])),
)
function areaName(id?: number): string {
  if (!id) return '-'
  return areaMap.value.get(id) ?? `#${id}`
}

async function loadAreas() {
  try {
    allAreas.value = await fetchAllAdminServiceAreas()
  } catch {
    /* 拦截器已统一提示 */
  }
}
onMounted(loadAreas)

/* ---------- 字典 ---------- */
const LEVEL_LABEL: Record<number, string> = { 1: '市级', 2: '区级' }
function levelLabel(v?: number): string {
  if (v === undefined) return '-'
  return LEVEL_LABEL[v] ?? String(v)
}

/** 覆盖状态（1 开放覆盖 0 暂停覆盖） */
const COVER_STATUS: Record<number, { label: string; type: 'success' | 'danger' }> = {
  1: { label: '开放覆盖', type: 'success' },
  0: { label: '暂停覆盖', type: 'danger' },
}
const STATUS_FALLBACK = { label: '未知', type: 'info' as const }
function statusOf(v?: number) {
  return COVER_STATUS[v === 0 ? 0 : 1] ?? STATUS_FALLBACK
}

/* ---------- 新增 / 编辑（级联选择 省→市→区，不再手输） ---------- */
interface AreaForm {
  path: string[]
  status: number
}

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)
const form = reactive<AreaForm>({ path: [], status: 1 })

const rules: FormRules = {
  path: [
    {
      validator: (_r, _v, cb) =>
        Array.isArray(_v) && _v.length >= 2
          ? cb()
          : cb(new Error('请选择 省 / 市（至少选到市级）')),
      trigger: 'change',
    },
  ],
}

/** 当前选择将写入的区域（用于表单预览） */
const preview = computed(() => resolveAreaPath(form.path))

/** 编辑态：记录不在内置地区库中 → 无法回填路径，只允许改状态 */
const pathMissing = computed(() => editingId.value !== null && form.path.length === 0)

function openCreate() {
  editingId.value = null
  form.path = []
  form.status = 1
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function openEdit(row: AdminServiceAreaRow) {
  editingId.value = row.id
  form.status = row.status ?? 1
  // 回填级联路径：市=省+市，区=省+市+区
  form.path = findAreaPath(row.name, row.level, row.parentId ? areaName(row.parentId) : undefined)
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

/** 区级区域必须挂在市级区域下：库里没有就自动创建一条（level=1） */
async function ensureCity(target: ResolvedArea, status: number): Promise<number> {
  if (target.level === 1) return 0
  const cityRow =
    allAreas.value.find((a) => a.level === 1 && a.code === target.cityCode) ??
    allAreas.value.find((a) => a.level === 1 && a.name === target.city)
  if (cityRow) return cityRow.id
  const created = await createServiceArea({
    name: target.city,
    code: target.cityCode,
    parentId: 0,
    level: 1,
    status,
  })
  allAreas.value.push(created)
  ElMessage.success(`已自动创建上级市级区域：${target.city}`)
  return created.id
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const target = resolveAreaPath(form.path)

  if (!target) {
    // 历史数据不在内置地区库：只更新状态，保留原名称/编码/层级
    const row = editingId.value !== null ? allAreas.value.find((a) => a.id === editingId.value) : undefined
    if (!row) return
    await updateServiceArea(row.id, {
      name: row.name,
      code: row.code ?? '',
      parentId: row.parentId ?? 0,
      level: row.level ?? 1,
      status: form.status,
    })
    ElMessage.success('服务区域已更新')
    dialogVisible.value = false
    await Promise.all([refresh(), loadAreas()])
    return
  }

  const conflict = allAreas.value.find(
    (a) => a.code === target.code && a.id !== editingId.value,
  )
  if (conflict) {
    ElMessage.warning(`该区域已在列表中：${conflict.name}（#${conflict.id}）`)
    return
  }

  const payload: ServiceAreaPayload = {
    name: target.name,
    code: target.code,
    parentId: await ensureCity(target, form.status),
    level: target.level,
    status: form.status,
  }
  if (editingId.value !== null) {
    await updateServiceArea(editingId.value, payload)
    ElMessage.success('服务区域已更新')
  } else {
    await createServiceArea(payload)
    ElMessage.success(`服务区域已创建：${target.name}`)
  }
  dialogVisible.value = false
  await Promise.all([refresh(), loadAreas()])
}

/* ---------- 启用 / 停用 / 删除 ---------- */
async function onToggleStatus(row: AdminServiceAreaRow) {
  const next = row.status === 1 ? 0 : 1
  await updateServiceArea(row.id, {
    name: row.name,
    code: row.code ?? '',
    parentId: row.parentId,
    level: row.level,
    status: next,
  })
  ElMessage.success(next === 1 ? '已开放覆盖' : '已暂停覆盖')
  await Promise.all([refresh(), loadAreas()])
}

async function onDelete(row: AdminServiceAreaRow) {
  await deleteServiceArea(row.id)
  ElMessage.success('服务区域已删除')
  await Promise.all([refresh(), loadAreas()])
}

const exportColumns = [
  { label: 'ID', key: 'id' },
  { label: '区域名称', key: 'name' },
  { label: '区域编码', key: 'code' },
  { label: '层级(1市2区)', key: 'level' },
  { label: '父级ID', key: 'parentId' },
  { label: '状态(1启用0停用)', key: 'status' },
  { label: '更新时间', key: 'updatedAt' },
]
</script>

<template>
  <div class="page-container">
    <PageCard title="服务区域管理">
      <SearchForm :cols="4" @search="search" @reset="reset">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="区域名称 / 编码" clearable :prefix-icon="Search" />
        </el-form-item>
        <el-form-item label="层级">
          <el-select v-model="query.level" placeholder="全部层级" clearable>
            <el-option label="市级" :value="1" />
            <el-option label="区级" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="覆盖状态">
          <el-select v-model="query.status" placeholder="全部状态" clearable>
            <el-option label="开放覆盖" :value="1" />
            <el-option label="暂停覆盖" :value="0" />
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
        export-name="服务区域列表"
        :export-columns="exportColumns"
        empty-text="暂无服务区域"
        @update:page="changePage"
        @update:size="changeSize"
        @refresh="refresh"
      >
        <template #toolbar>
          <div class="toolbar-left">
            <el-button type="primary" @click="openCreate">新增服务区域</el-button>
            <span class="aux-text">共 {{ total }} 个区域</span>
          </div>
        </template>

        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="区域名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="code" label="区域编码" width="120" show-overflow-tooltip />
        <el-table-column label="层级" width="90" align="center">
          <template #default="{ row }">{{ levelLabel(row.level) }}</template>
        </el-table-column>
        <el-table-column label="上级区域" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.parentId ? areaName(row.parentId) : '-' }}</template>
        </el-table-column>
        <el-table-column label="覆盖状态" width="110" align="center">
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
              :title="row.status === 1 ? '确认暂停该区域覆盖？' : '确认开放该区域覆盖？'"
              :message="row.status === 1 ? '暂停后用户端筛选不到该区域的服务人员。' : '开放后该区域将纳入服务范围。'"
              :danger="row.status === 1"
              @confirm="onToggleStatus(row)"
            >
              <el-button link :type="row.status === 1 ? 'danger' : 'success'">
                {{ row.status === 1 ? '暂停' : '开放' }}
              </el-button>
            </ConfirmAction>
            <ConfirmAction
              title="确认删除该服务区域？"
              message="删除不可恢复，且不做关联校验，已绑定该区域的服务人员将失去归属。"
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
      :title="editingId !== null ? '编辑服务区域' : '新增服务区域'"
      width="min(460px, 92vw)"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="所属区域" prop="path">
          <el-cascader
            v-model="form.path"
            :options="areaCascaderOptions"
            :props="areaCascaderProps"
            placeholder="请选择 省 / 市 / 区"
            clearable
            style="width: 100%"
          />
          <p class="form-tip aux-text">
            按省 → 市 → 区逐级选择，无需手输：选到「市」即市级区域，选到「区」即区级区域（自动关联/创建上级市）。
          </p>
        </el-form-item>

        <el-form-item v-if="pathMissing" label="提示">
          <p class="form-tip aux-text">该区域不在内置地区库中，仅支持调整覆盖状态。</p>
        </el-form-item>

        <el-form-item v-if="preview" label="将写入">
          <div class="area-preview">
            <span class="area-preview__name">
              {{ preview.province }} / {{ preview.city }}
              <template v-if="preview.district"> / {{ preview.district }}</template>
            </span>
            <el-tag size="small" effect="light">{{ preview.level === 1 ? '市级' : '区级' }}</el-tag>
            <span class="aux-text">编码 {{ preview.code }}</span>
            <span v-if="preview.level === 2" class="aux-text">上级：{{ preview.city }}</span>
          </div>
        </el-form-item>

        <el-form-item label="覆盖状态">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="开放"
            inactive-text="暂停"
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

.form-tip {
  margin: 6px 0 0;
  line-height: 1.5;
}

.area-preview {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  line-height: 1.6;
}

.area-preview__name {
  font-weight: 600;
  color: var(--color-text-primary);
}
</style>
