<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Location } from '@element-plus/icons-vue'
import PageCard from '@/components/common/PageCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ConfirmAction from '@/components/common/ConfirmAction.vue'
import {
  fetchAddresses,
  createAddress,
  updateAddress,
  deleteAddress,
  type AddressPayload,
} from '@/api/pets'
import { maskPhone } from '@/utils/constants'
import type { UserAddress } from '@/types'

/**
 * 上门地址管理（PRD 5.9）：本人地址列表 + 新增 / 编辑 / 设为默认 / 删除
 * 字段与校验对齐 AddressRequest：联系人必填、手机号 ^1[3-9]\d{9}$、省市区选填、详细地址必填、isDefault 0/1
 */
const list = ref<UserAddress[]>([])
const loading = ref(true)
const error = ref('')
const removingId = ref(0)
const defaultingId = ref(0)

const dialogVisible = ref(false)
const editingId = ref(0) // 0 表示新增
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  contactName: '',
  contactPhone: '',
  region: '',
  detail: '',
  isDefault: false,
})

const rules: FormRules = {
  contactName: [
    { required: true, message: '请填写联系人', trigger: 'blur' },
    { max: 64, message: '联系人不能超过 64 个字符', trigger: 'blur' },
  ],
  contactPhone: [
    { required: true, message: '请填写联系电话', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  region: [{ max: 128, message: '省市区不能超过 128 个字符', trigger: 'blur' }],
  detail: [
    { required: true, message: '请填写详细地址', trigger: 'blur' },
    { max: 255, message: '详细地址不能超过 255 个字符', trigger: 'blur' },
  ],
}

function text(v: unknown): string {
  return v === undefined || v === null ? '' : String(v)
}

function isDefaultOf(a: UserAddress): boolean {
  return Number(a.isDefault ?? 0) === 1
}

/**
 * 组装请求体：后端 AddressRequest.isDefault 为 Integer(0/1)，
 * 而 api/pets.ts 的 AddressPayload 声明为 boolean —— 统一以 0/1 数字提交，
 * 用 as 断言绕过声明差异，避免 Jackson 把布尔值反序列化成 Integer 失败。
 */
function buildPayload(src: {
  contactName: string
  contactPhone: string
  region: string
  detail: string
  isDefault: boolean
}): AddressPayload {
  return {
    contactName: src.contactName.trim(),
    contactPhone: src.contactPhone.trim(),
    region: src.region.trim() || undefined,
    detail: src.detail.trim(),
    isDefault: (src.isDefault ? 1 : 0) as unknown as boolean,
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    list.value = (await fetchAddresses()) ?? []
  } catch (e) {
    error.value = (e as Error)?.message || '地址列表加载失败'
    list.value = []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = 0
  form.contactName = ''
  form.contactPhone = ''
  form.region = ''
  form.detail = ''
  form.isDefault = list.value.length === 0
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function openEdit(a: UserAddress) {
  editingId.value = a.id
  form.contactName = a.contactName ?? ''
  form.contactPhone = a.contactPhone ?? ''
  form.region = text(a.region)
  form.detail = a.detail ?? ''
  form.isDefault = isDefaultOf(a)
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const payload = buildPayload(form)
    if (editingId.value) {
      await updateAddress(editingId.value, payload)
      ElMessage.success('地址已保存')
    } else {
      await createAddress(payload)
      ElMessage.success('地址已新增')
    }
    dialogVisible.value = false
    await load()
  } catch {
    /* 拦截器已统一提示后端错误 */
  } finally {
    saving.value = false
  }
}

/** 设为默认：带上原字段重新提交并置 isDefault=1（后端会取消其它默认） */
async function setDefault(a: UserAddress) {
  if (isDefaultOf(a)) return
  defaultingId.value = a.id
  try {
    await updateAddress(
      a.id,
      buildPayload({
        contactName: a.contactName,
        contactPhone: a.contactPhone,
        region: text(a.region),
        detail: a.detail,
        isDefault: true,
      }),
    )
    ElMessage.success('已设为默认地址')
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    defaultingId.value = 0
  }
}

async function onRemove(a: UserAddress) {
  removingId.value = a.id
  try {
    await deleteAddress(a.id)
    ElMessage.success('地址已删除')
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    removingId.value = 0
  }
}

onMounted(load)
</script>

<template>
  <div class="addr-page">
    <div class="addr-page__topbar">
      <span class="addr-page__title">地址管理</span>
      <span class="aux-text addr-page__count">共 {{ list.length }} 个地址</span>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增地址</el-button>
    </div>

    <PageCard :loading="loading">
      <EmptyState v-if="error" :text="error" action-text="重新加载" @action="load" />

      <EmptyState
        v-else-if="!list.length"
        text="还没有上门地址"
        action-text="新增第一个地址"
        @action="openCreate"
      >
        <template #tip>保存地址后，预约下单时可直接选择</template>
      </EmptyState>

      <div v-else class="addr-list">
        <div v-for="a in list" :key="a.id" class="addr-item" :class="{ 'is-default': isDefaultOf(a) }">
          <div class="addr-item__body">
            <div class="addr-item__head">
              <span class="addr-item__name">{{ a.contactName }}</span>
              <span class="addr-item__phone">{{ maskPhone(a.contactPhone) }}</span>
              <el-tag v-if="isDefaultOf(a)" type="success" size="small" effect="light" round>默认</el-tag>
            </div>
            <div class="addr-item__detail">
              <el-icon class="addr-item__pin"><Location /></el-icon>
              <span>{{ text(a.region) ? `${a.region} ` : '' }}{{ a.detail }}</span>
            </div>
          </div>

          <div class="addr-item__actions">
            <el-button
              v-if="!isDefaultOf(a)"
              size="small"
              plain
              :loading="defaultingId === a.id"
              @click="setDefault(a)"
            >
              设为默认
            </el-button>
            <el-button size="small" plain @click="openEdit(a)">编辑</el-button>
            <ConfirmAction
              title="确认删除该地址？"
              message="删除后不可恢复，进行中的订单不受影响。"
              danger
              @confirm="onRemove(a)"
            >
              <el-button size="small" type="danger" plain :loading="removingId === a.id">删除</el-button>
            </ConfirmAction>
          </div>
        </div>
      </div>
    </PageCard>

    <!-- 新增 / 编辑地址 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑地址' : '新增地址'"
      width="520px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="联系人" prop="contactName">
          <el-input v-model="form.contactName" maxlength="64" placeholder="上门服务时的对接人" clearable />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="form.contactPhone" maxlength="11" placeholder="11 位手机号" clearable />
        </el-form-item>
        <el-form-item label="省市区" prop="region">
          <el-input v-model="form.region" maxlength="128" placeholder="如：北京市/朝阳区（选填）" clearable />
        </el-form-item>
        <el-form-item label="详细地址" prop="detail">
          <el-input
            v-model="form.detail"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            placeholder="小区 / 门牌号 / 楼栋楼层，便于服务师上门"
          />
        </el-form-item>
        <el-form-item label="默认地址">
          <el-switch v-model="form.isDefault" active-text="设为默认" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">
          {{ editingId ? '保存修改' : '新增地址' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.addr-page__topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.addr-page__title {
  font-size: var(--font-size-module);
  font-weight: 600;
  color: var(--color-text-primary);
}

.addr-page__count {
  flex: 1;
}

.addr-list {
  display: grid;
  gap: 12px;
}

.addr-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  border-radius: var(--radius-card);
  background: var(--color-bg-page);
  border: 1px solid transparent;
  transition: all 0.15s ease;
}

.addr-item.is-default {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.addr-item__body {
  min-width: 0;
  flex: 1;
}

.addr-item__head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.addr-item__name {
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
}

.addr-item__phone {
  font-size: var(--font-size-aux);
  color: var(--color-text-secondary);
}

.addr-item__detail {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  margin-top: 6px;
  font-size: var(--font-size-aux);
  color: var(--color-text-secondary);
  line-height: 1.6;
  word-break: break-all;
}

.addr-item__pin {
  margin-top: 3px;
  color: var(--el-color-primary);
  flex: 0 0 auto;
}

.addr-item__actions {
  display: flex;
  gap: 8px;
  flex: 0 0 auto;
}

.addr-item__actions .el-button + .el-button {
  margin-left: 0;
}

@media (max-width: 640px) {
  .addr-item {
    flex-direction: column;
    align-items: stretch;
  }

  .addr-item__actions {
    justify-content: flex-end;
  }
}
</style>
