<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import SearchForm from '@/components/common/SearchForm.vue'
import TableWrapper from '@/components/common/TableWrapper.vue'
import PageCard from '@/components/common/PageCard.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import ConfirmAction from '@/components/common/ConfirmAction.vue'
import { usePage } from '@/composables/usePage'
import { fetchUsers, updateUserStatus, adjustBalance } from '@/api/admin/users'
import { fmtMoney, fmtTime } from '@/utils/constants'
import type { SysUser } from '@/types'

/** 用户管理（PRD 7.1：查询 / 冻结解冻 / 余额调整写流水并记审计） */
const { list, total, page, size, loading, error, query, search, reset, changePage, changeSize, refresh } =
  usePage<SysUser, { keyword: string; role: string; status: number | '' }>((q) => fetchUsers(q), {
    defaultQuery: { keyword: '', role: '', status: '' },
  })

/* ---------- 冻结 / 解冻 ---------- */
async function onToggleStatus(row: SysUser) {
  const next = row.status === 1 ? 0 : 1
  await updateUserStatus(row.id, next)
  ElMessage.success(next === 1 ? '已解冻' : '已冻结')
  await refresh()
}

/* ---------- 余额调整 ---------- */
const adjustVisible = ref(false)
const adjustRef = ref<FormInstance>()
const adjustForm = reactive({ userId: 0, username: '', amount: 0, remark: '' })
const adjustRules: FormRules = {
  amount: [
    { required: true, message: '请输入调整金额', trigger: 'blur' },
    {
      validator: (_r, value: number, cb) => {
        if (!value) return cb(new Error('调整金额不能为 0'))
        if (Math.abs(value) > 100000) return cb(new Error('单次调整不能超过 100000 元'))
        cb()
      },
      trigger: 'blur',
    },
  ],
}

function openAdjust(row: SysUser) {
  adjustForm.userId = row.id
  adjustForm.username = row.nickname || row.username
  adjustForm.amount = 0
  adjustForm.remark = ''
  adjustVisible.value = true
  adjustRef.value?.clearValidate()
}

async function submitAdjust() {
  const valid = await adjustRef.value?.validate().catch(() => false)
  if (!valid) return
  await adjustBalance({
    userId: adjustForm.userId,
    amount: adjustForm.amount,
    remark: adjustForm.remark || undefined,
  })
  ElMessage.success('余额已调整，流水与审计日志已记录')
  adjustVisible.value = false
  await refresh()
}

const exportColumns = [
  { label: 'ID', key: 'id' },
  { label: '账号', key: 'username' },
  { label: '昵称', key: 'nickname' },
  { label: '手机号', key: 'phone' },
  { label: '角色', key: 'role' },
  { label: '余额(元)', key: 'balance' },
  { label: '状态', key: 'status' },
  { label: '注册时间', key: 'createdAt' },
]
</script>

<template>
  <div class="page-container">
    <PageCard title="用户管理">
      <SearchForm :cols="4" @search="search" @reset="reset">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="账号 / 昵称" clearable :prefix-icon="Search" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="query.role" placeholder="全部角色" clearable>
            <el-option label="用户" value="USER" />
            <el-option label="服务人员" value="STAFF" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部状态" clearable>
            <el-option label="正常" :value="1" />
            <el-option label="冻结" :value="0" />
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
        export-name="用户列表"
        :export-columns="exportColumns"
        empty-text="暂无用户数据"
        @update:page="changePage"
        @update:size="changeSize"
        @refresh="refresh"
      >
        <template #toolbar>
          <span class="aux-text">共 {{ total }} 个账号</span>
        </template>

        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="账号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="nickname" label="昵称" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.nickname || '-' }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="130">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="110">
          <template #default="{ row }"><StatusTag kind="role" :value="row.role" /></template>
        </el-table-column>
        <el-table-column label="余额(元)" width="120" align="right">
          <template #default="{ row }">
            <span class="price-text">{{ fmtMoney(row.balance) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><StatusTag kind="user" :value="row.status" /></template>
        </el-table-column>
        <el-table-column label="注册时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openAdjust(row)">余额调整</el-button>
            <ConfirmAction
              v-if="row.role !== 'ADMIN'"
              :title="row.status === 1 ? '确认冻结该账号？' : '确认解冻该账号？'"
              :message="row.status === 1 ? '冻结后该账号将无法登录与下单。' : '解冻后账号可正常登录与下单。'"
              danger
              @confirm="onToggleStatus(row)"
            >
              <el-button link :type="row.status === 1 ? 'danger' : 'success'">
                {{ row.status === 1 ? '冻结' : '解冻' }}
              </el-button>
            </ConfirmAction>
            <el-tag v-else type="info" size="small" effect="plain">系统预置</el-tag>
          </template>
        </el-table-column>
      </TableWrapper>
    </PageCard>

    <!-- 余额调整（写 balance_record + operation_log） -->
    <el-dialog v-model="adjustVisible" title="余额调整" width="440px">
      <el-form ref="adjustRef" :model="adjustForm" :rules="adjustRules" label-width="90px">
        <el-form-item label="账号">
          <el-input :model-value="adjustForm.username" disabled />
        </el-form-item>
        <el-form-item label="调整金额" prop="amount">
          <el-input-number
            v-model="adjustForm.amount"
            :min="-100000"
            :max="100000"
            :precision="2"
            :step="10"
            style="width: 100%"
          />
          <p class="adjust-tip aux-text">正数为增加，负数为扣减；将写入 ADJUST 流水并记录审计日志。</p>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="adjustForm.remark" type="textarea" :rows="2" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAdjust">确认调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.adjust-tip {
  margin: 6px 0 0;
  line-height: 1.5;
}
</style>
