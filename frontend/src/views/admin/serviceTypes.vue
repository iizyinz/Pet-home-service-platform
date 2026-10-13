<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import SearchForm from '@/components/common/SearchForm.vue'
import TableWrapper from '@/components/common/TableWrapper.vue'
import PageCard from '@/components/common/PageCard.vue'
import ConfirmAction from '@/components/common/ConfirmAction.vue'
import { usePage } from '@/composables/usePage'
import {
  fetchAdminServiceTypes,
  createServiceType,
  updateServiceType,
  deleteServiceType,
  type AdminServiceTypeRow,
  type ServiceTypePayload,
} from '@/api/admin/catalog'
import { fmtTime } from '@/utils/constants'

/** 服务类型管理（PRD 7.1：名称/图标/排序/启用状态，删除前校验关联服务项目） */
const { list, total, page, size, loading, error, query, search, reset, changePage, changeSize, refresh } =
  usePage<AdminServiceTypeRow, { keyword: string }>((q) => fetchAdminServiceTypes(q), {
    defaultQuery: { keyword: '' },
  })

/** 启用 / 停用（项目 StatusTag 只覆盖订单/认证等业务状态枚举） */
const ENABLE_STATUS: Record<number, { label: string; type: 'success' | 'danger' }> = {
  1: { label: '启用', type: 'success' },
  0: { label: '停用', type: 'danger' },
}
const STATUS_FALLBACK = { label: '未知', type: 'info' as const }
function statusOf(v?: number) {
  return ENABLE_STATUS[v === 0 ? 0 : 1] ?? STATUS_FALLBACK
}

/* ---------- 新增 / 编辑 ---------- */
/** 表单态字段全部必填，提交时再映射为接口 payload */
interface TypeForm {
  name: string
  icon: string
  sort: number
  status: number
}

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)
const form = reactive<TypeForm>({ name: '', icon: '', sort: 0, status: 1 })

const rules: FormRules = {
  name: [
    { required: true, message: '请输入类型名称', trigger: 'blur' },
    { max: 64, message: '名称不能超过 64 个字符', trigger: 'blur' },
  ],
  sort: [{ required: true, message: '请输入排序值', trigger: 'blur' }],
}

function openCreate() {
  editingId.value = null
  form.name = ''
  form.icon = ''
  form.sort = 0
  form.status = 1
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function openEdit(row: AdminServiceTypeRow) {
  editingId.value = row.id
  form.name = row.name
  form.icon = row.icon ?? ''
  form.sort = row.sort ?? 0
  form.status = row.status ?? 1
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const payload: ServiceTypePayload = {
    name: form.name.trim(),
    icon: form.icon,
    sort: form.sort,
    status: form.status,
  }
  if (editingId.value !== null) {
    await updateServiceType(editingId.value, payload)
    ElMessage.success('服务类型已更新')
  } else {
    await createServiceType(payload)
    ElMessage.success('服务类型已创建')
  }
  dialogVisible.value = false
  await refresh()
}

/* ---------- 启用 / 停用 / 删除 ---------- */
async function onToggleStatus(row: AdminServiceTypeRow) {
  const next = row.status === 1 ? 0 : 1
  await updateServiceType(row.id, {
    name: row.name,
    icon: row.icon,
    sort: row.sort,
    status: next,
  })
  ElMessage.success(next === 1 ? '已启用' : '已停用')
  await refresh()
}

async function onDelete(row: AdminServiceTypeRow) {
  await deleteServiceType(row.id)
  ElMessage.success('服务类型已删除')
  await refresh()
}

const exportColumns = [
  { label: 'ID', key: 'id' },
  { label: '类型名称', key: 'name' },
  { label: '图标', key: 'icon' },
  { label: '排序', key: 'sort' },
  { label: '状态(1启用0停用)', key: 'status' },
  { label: '更新时间', key: 'updatedAt' },
]
</script>

<template>
  <div class="page-container">
    <PageCard title="服务类型管理">
      <SearchForm :cols="4" @search="search" @reset="reset">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="类型名称" clearable :prefix-icon="Search" />
        </el-form-item>
      </SearchForm>

      <TableWrapper
        :data="list as unknown as Record<string, unknown>[]"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        :error="error"
        export-name="服务类型列表"
        :export-columns="exportColumns"
        empty-text="暂无服务类型"
        @update:page="changePage"
        @update:size="changeSize"
        @refresh="refresh"
      >
        <template #toolbar>
          <div class="toolbar-left">
            <el-button type="primary" @click="openCreate">新增服务类型</el-button>
            <span class="aux-text">共 {{ total }} 个类型</span>
          </div>
        </template>

        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="图标" width="80" align="center">
          <template #default="{ row }">
            <span class="type-icon">{{ row.icon || '🐾' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="类型名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="排序" width="90" align="center">
          <template #default="{ row }">{{ row.sort ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
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
              :title="row.status === 1 ? '确认停用该服务类型？' : '确认启用该服务类型？'"
              :message="row.status === 1 ? '停用后首页不再展示该分类。' : '启用后首页将重新展示该分类。'"
              :danger="row.status === 1"
              @confirm="onToggleStatus(row)"
            >
              <el-button link :type="row.status === 1 ? 'danger' : 'success'">
                {{ row.status === 1 ? '停用' : '启用' }}
              </el-button>
            </ConfirmAction>
            <ConfirmAction
              title="确认删除该服务类型？"
              message="删除后不可恢复；类型下若存在服务项目将被拒绝。"
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
      :title="editingId !== null ? '编辑服务类型' : '新增服务类型'"
      width="min(460px, 92vw)"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="类型名称" prop="name">
          <el-input v-model="form.name" placeholder="如：洗护 / 上门喂养" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="图标" prop="icon">
          <el-input v-model="form.icon" placeholder="建议填写 emoji，如 🐾" maxlength="8" />
          <p class="form-tip aux-text">首页分类入口展示的图标，留空时前端兜底显示 🐾。</p>
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :max="9999" :step="1" style="width: 100%" />
          <p class="form-tip aux-text">数值越小越靠前。</p>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
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

.type-icon {
  font-size: 20px;
  line-height: 1;
}

.form-tip {
  margin: 6px 0 0;
  line-height: 1.5;
}
</style>
