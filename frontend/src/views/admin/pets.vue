<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import SearchForm from '@/components/common/SearchForm.vue'
import TableWrapper from '@/components/common/TableWrapper.vue'
import PageCard from '@/components/common/PageCard.vue'
import ConfirmAction from '@/components/common/ConfirmAction.vue'
import { usePage } from '@/composables/usePage'
import { adminFetchPets, adminDeletePet } from '@/api/pets'
import { fmtTime } from '@/utils/constants'
import type { Pet } from '@/types'

/** 宠物档案管理（PRD 7.1：按主人归属筛选、查看详情、逻辑删除，供下单信息带入） */
type PetQuery = { userId?: number }

const { list, total, page, size, loading, error, query, search, reset, changePage, changeSize, refresh } =
  usePage<Pet, PetQuery>((q) => adminFetchPets({ userId: q.userId, page: q.page ?? 1, size: q.size ?? 10 }), {
    defaultQuery: { userId: undefined },
  })

/* ---------- 详情（管理端无单独详情接口，直接展示行数据） ---------- */
const detailVisible = ref(false)
const current = ref<Pet | null>(null)

function openDetail(row: Pet) {
  current.value = row
  detailVisible.value = true
}

/* ---------- 删除（逻辑删除 + 审计） ---------- */
async function onDelete(row: Pet) {
  await adminDeletePet(row.id)
  ElMessage.success(`宠物档案「${row.name}」已删除`)
  await refresh()
}

const exportColumns = [
  { label: 'ID', key: 'id' },
  { label: '主人用户ID', key: 'userId' },
  { label: '宠物昵称', key: 'name' },
  { label: '品种', key: 'breed' },
  { label: '年龄', key: 'age' },
  { label: '体重(kg)', key: 'weightKg' },
  { label: '健康信息', key: 'healthInfo' },
  { label: '特殊需求', key: 'specialNeed' },
  { label: '创建时间', key: 'createdAt' },
]
</script>

<template>
  <div class="page-container">
    <PageCard title="宠物档案管理">
      <SearchForm :cols="4" @search="search" @reset="reset">
        <el-form-item label="用户ID">
          <el-input-number
            v-model="query.userId"
            :min="1"
            :controls="false"
            placeholder="按主人用户 ID 筛选"
            style="width: 200px"
          />
        </el-form-item>
      </SearchForm>

      <TableWrapper
        :data="list as unknown as Record<string, unknown>[]"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        :error="error"
        export-name="宠物档案列表"
        :export-columns="exportColumns"
        empty-text="暂无宠物档案"
        @update:page="changePage"
        @update:size="changeSize"
        @refresh="refresh"
      >
        <template #toolbar>
          <span class="aux-text">共 {{ total }} 只宠物</span>
        </template>

        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="头像" width="80" align="center">
          <template #default="{ row }">
            <el-avatar :size="40">
              <img v-if="row.avatar" :src="row.avatar" alt="" />
              <span v-else>{{ (row.name || '?').slice(0, 1) }}</span>
            </el-avatar>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="宠物昵称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="breed" label="品种" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.breed || '-' }}</template>
        </el-table-column>
        <el-table-column label="年龄" width="90" align="center">
          <template #default="{ row }">{{ row.age ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="体重(kg)" width="100" align="right">
          <template #default="{ row }">{{ row.weightKg ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="userId" label="主人ID" width="100" />
        <el-table-column label="健康信息" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.healthInfo || '-' }}</template>
        </el-table-column>
        <el-table-column label="特殊需求" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.specialNeed || '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <ConfirmAction
              title="确认删除该宠物档案？"
              message="删除为逻辑删除，不可恢复，相关下单信息将不再带入。"
              danger
              @confirm="onDelete(row)"
            >
              <el-button link type="danger">删除</el-button>
            </ConfirmAction>
          </template>
        </el-table-column>
      </TableWrapper>
    </PageCard>

    <el-dialog v-model="detailVisible" title="宠物档案详情" width="min(560px, 92vw)">
      <template v-if="current">
        <div class="detail-head">
          <el-avatar :size="64">
            <img v-if="current.avatar" :src="current.avatar" alt="" />
            <span v-else>{{ (current.name || '?').slice(0, 1) }}</span>
          </el-avatar>
          <div class="detail-head__info">
            <div class="detail-head__name">{{ current.name }}</div>
            <div class="aux-text">主人用户 ID：{{ current.userId ?? '-' }}</div>
          </div>
        </div>

        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="品种">{{ current.breed || '-' }}</el-descriptions-item>
          <el-descriptions-item label="年龄">{{ current.age ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="体重">{{ current.weightKg ? current.weightKg + ' kg' : '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ fmtTime(current.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="健康信息" :span="2">{{ current.healthInfo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="特殊需求" :span="2">{{ current.specialNeed || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.detail-head {
  display: flex;
  align-items: center;
  gap: 12px;
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
</style>
