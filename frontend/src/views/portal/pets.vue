<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import PageCard from '@/components/common/PageCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ConfirmAction from '@/components/common/ConfirmAction.vue'
import ImageUploader from '@/components/common/ImageUploader.vue'
import { fetchPets, createPet, updatePet, deletePet } from '@/api/pets'
import { fmtTime } from '@/utils/constants'
import type { Pet } from '@/types'

/**
 * 宠物档案（PRD 5.9）：本人宠物档案卡片列表 + 新增 / 编辑 / 删除（后端逻辑删除）
 * 字段对齐 PetRequest：名称必填(64)、品种(64)、年龄描述(32)、体重 kg、健康信息(500)、特殊需求(500)、头像
 */
const pets = ref<Pet[]>([])
const loading = ref(true)
const error = ref('')
const removingId = ref(0)

const dialogVisible = ref(false)
const editingId = ref(0) // 0 表示新增
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  breed: '',
  age: '',
  weightKg: undefined as number | undefined,
  healthInfo: '',
  specialNeed: '',
  avatarList: [] as string[],
})

const rules: FormRules = {
  name: [
    { required: true, message: '请填写宠物昵称', trigger: 'blur' },
    { max: 64, message: '昵称不能超过 64 个字符', trigger: 'blur' },
  ],
  breed: [{ max: 64, message: '品种不能超过 64 个字符', trigger: 'blur' }],
  age: [{ max: 32, message: '年龄描述不能超过 32 个字符', trigger: 'blur' }],
  weightKg: [
    {
      validator: (_r, value: unknown, cb) => {
        const n = num(value)
        if (n === undefined) return cb()
        if (n <= 0) return cb(new Error('体重必须大于 0 kg'))
        if (n > 200) return cb(new Error('体重不能超过 200 kg'))
        cb()
      },
      trigger: 'blur',
    },
  ],
  healthInfo: [{ max: 500, message: '健康信息不能超过 500 个字符', trigger: 'blur' }],
  specialNeed: [{ max: 500, message: '特殊需求不能超过 500 个字符', trigger: 'blur' }],
}

/** JSON null/undefined 统一转空串，避免出现 “null” 字样 */
function text(v: unknown): string {
  return v === undefined || v === null ? '' : String(v)
}

/** JSON null/undefined 统一转 undefined，数值化兜底 */
function num(v: unknown): number | undefined {
  return v === undefined || v === null || text(v) === '' ? undefined : Number(v)
}

/** 年龄展示：后端为字符串描述（如“3岁”），纯数字时补“岁” */
function ageText(v: unknown): string {
  const s = text(v).trim()
  if (!s) return '-'
  return /^\d+(\.\d+)?$/.test(s) ? `${s} 岁` : s
}

/** 体重展示（kg） */
function weightText(v: unknown): string {
  const n = num(v)
  return n === undefined ? '-' : `${n} kg`
}

/**
 * 提交转换：后端 Pet.age 是字符串描述，而 api/pets.ts 的 PetPayload 声明为 number；
 * 纯数字转数值（Jackson 会安全转成字符串），含单位的描述原样以字符串提交。
 */
function toAgePayload(age: string): number | undefined {
  const v = age.trim()
  if (!v) return undefined
  return /^\d+(\.\d+)?$/.test(v) ? Number(v) : (v as unknown as number)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    pets.value = (await fetchPets()) ?? []
  } catch (e) {
    error.value = (e as Error)?.message || '宠物档案加载失败'
    pets.value = []
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.name = ''
  form.breed = ''
  form.age = ''
  form.weightKg = undefined
  form.healthInfo = ''
  form.specialNeed = ''
  form.avatarList = []
}

function openCreate() {
  editingId.value = 0
  resetForm()
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function openEdit(p: Pet) {
  editingId.value = p.id
  form.name = p.name ?? ''
  form.breed = text(p.breed)
  form.age = text(p.age)
  form.weightKg = num(p.weightKg)
  form.healthInfo = text(p.healthInfo)
  form.specialNeed = text(p.specialNeed)
  form.avatarList = p.avatar ? [p.avatar] : []
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      breed: form.breed.trim() || undefined,
      age: toAgePayload(form.age),
      weightKg: form.weightKg,
      healthInfo: form.healthInfo.trim() || undefined,
      specialNeed: form.specialNeed.trim() || undefined,
      avatar: form.avatarList[0],
    }
    if (editingId.value) {
      await updatePet(editingId.value, payload)
      ElMessage.success('宠物档案已更新')
    } else {
      await createPet(payload)
      ElMessage.success('宠物档案已新增')
    }
    dialogVisible.value = false
    await load()
  } catch {
    /* 拦截器已统一提示后端错误 */
  } finally {
    saving.value = false
  }
}

async function onRemove(p: Pet) {
  removingId.value = p.id
  try {
    await deletePet(p.id)
    ElMessage.success(`已删除「${p.name}」的档案`)
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
  <div class="pet-page">
    <div class="pet-page__topbar">
      <span class="pet-page__title">宠物档案</span>
      <span class="aux-text pet-page__count">共 {{ pets.length }} 只宠物</span>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增宠物</el-button>
    </div>

    <PageCard :loading="loading">
      <EmptyState v-if="error" :text="error" action-text="重新加载" @action="load" />

      <EmptyState
        v-else-if="!pets.length"
        text="还没有宠物档案"
        action-text="新增第一只宠物"
        @action="openCreate"
      >
        <template #tip>完善档案后，下单时可一键带入宠物信息</template>
      </EmptyState>

      <div v-else class="pet-grid">
        <div v-for="p in pets" :key="p.id" class="pet-card">
          <div class="pet-card__head">
            <el-avatar :size="56" class="pet-card__avatar">
              <img v-if="p.avatar" :src="p.avatar" alt="" />
              <span v-else>{{ (p.name || '宠')[0] }}</span>
            </el-avatar>
            <div class="pet-card__head-info">
              <div class="pet-card__name">{{ p.name }}</div>
              <div class="aux-text">{{ p.breed || '未知品种' }}</div>
            </div>
          </div>

          <ul class="pet-card__meta">
            <li>
              <span class="pet-card__label">年龄</span>
              <span>{{ ageText(p.age) }}</span>
            </li>
            <li>
              <span class="pet-card__label">体重</span>
              <span>{{ weightText(p.weightKg) }}</span>
            </li>
            <li>
              <span class="pet-card__label">建档时间</span>
              <span>{{ fmtTime(p.createdAt) }}</span>
            </li>
          </ul>

          <p class="pet-card__line aux-text">
            健康状况：{{ text(p.healthInfo) || '无特殊说明' }}
          </p>
          <p v-if="text(p.specialNeed)" class="pet-card__line pet-card__line--warn">
            特殊需求：{{ p.specialNeed }}
          </p>

          <div class="pet-card__actions">
            <el-button size="small" plain @click="openEdit(p)">编辑</el-button>
            <ConfirmAction
              :title="`确认删除「${p.name}」的档案？`"
              message="删除后不可恢复，历史订单中的宠物快照不受影响。"
              danger
              @confirm="onRemove(p)"
            >
              <el-button size="small" type="danger" plain :loading="removingId === p.id">删除</el-button>
            </ConfirmAction>
          </div>
        </div>
      </div>
    </PageCard>

    <!-- 新增 / 编辑档案 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑宠物档案' : '新增宠物档案'"
      width="520px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="宠物昵称" prop="name">
          <el-input v-model="form.name" maxlength="64" placeholder="如：咪咪" clearable />
        </el-form-item>
        <el-form-item label="品种" prop="breed">
          <el-input v-model="form.breed" maxlength="64" placeholder="如：布偶猫 / 金毛（选填）" clearable />
        </el-form-item>
        <el-form-item label="年龄" prop="age">
          <el-input v-model="form.age" maxlength="32" placeholder="如：3岁（选填）" clearable />
        </el-form-item>
        <el-form-item label="体重" prop="weightKg">
          <el-input-number
            v-model="form.weightKg"
            :min="0"
            :max="200"
            :precision="1"
            :step="0.5"
            placeholder="kg"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="健康状况" prop="healthInfo">
          <el-input
            v-model="form.healthInfo"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="疫苗情况、既往病史、过敏史等（选填）"
          />
        </el-form-item>
        <el-form-item label="特殊需求" prop="specialNeed">
          <el-input
            v-model="form.specialNeed"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="怕生、需喂药、牵引要求等，方便服务师提前准备（选填）"
          />
        </el-form-item>
        <el-form-item label="头像">
          <ImageUploader v-model="form.avatarList" :limit="1" tip="支持 jpg / png / gif / webp，单张不超过 5MB" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">
          {{ editingId ? '保存修改' : '新增档案' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.pet-page__topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.pet-page__title {
  font-size: var(--font-size-module);
  font-weight: 600;
  color: var(--color-text-primary);
}

.pet-page__count {
  flex: 1;
}

.pet-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}

.pet-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  border-radius: var(--radius-card);
  background: var(--color-bg-page);
  transition: all 0.15s ease;
}

.pet-card:hover {
  background: var(--color-bg-card);
  box-shadow: var(--shadow-card);
}

.pet-card__head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pet-card__avatar {
  flex: 0 0 auto;
  background: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
  font-size: 20px;
}

.pet-card__head-info {
  min-width: 0;
}

.pet-card__name {
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
}

.pet-card__meta {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 6px;
}

.pet-card__meta li {
  display: flex;
  justify-content: space-between;
  font-size: var(--font-size-aux);
  color: var(--color-text-secondary);
}

.pet-card__label {
  color: var(--color-text-muted);
}

.pet-card__line {
  margin: 0;
  line-height: 1.6;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.pet-card__line--warn {
  color: var(--color-warning);
  font-size: var(--font-size-aux);
}

.pet-card__actions {
  display: flex;
  gap: 8px;
  margin-top: auto;
  padding-top: 4px;
}

.pet-card__actions .el-button + .el-button {
  margin-left: 0;
}

@media (max-width: 768px) {
  .pet-page__topbar {
    flex-wrap: wrap;
  }
}
</style>
