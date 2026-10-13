<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import StatCard from '@/components/common/StatCard.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import ImageUploader from '@/components/common/ImageUploader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import { fetchMyProfile, updateMyProfile, type StaffProfilePayload } from '@/api/staffs'
import { fetchProfile, updateProfile } from '@/api/user'
import { fetchServiceAreas, fetchServiceItems } from '@/api/catalog'
import { fmtMoney } from '@/utils/constants'
import type { ServiceArea, ServiceItem, StaffProfile, SysUser } from '@/types'

/**
 * 个人资料（PRD 6.5）：
 * 账号资料（昵称/头像）走 PUT /user/profile，
 * 服务档案（简介/年限/擅长品种/区域/资质/可接项目）走 PUT /staffs/me/profile。
 */
const loading = ref(true)
const error = ref('')
const saving = ref(false)

const profile = ref<StaffProfile | null>(null)
const user = ref<SysUser | null>(null)
const areas = ref<ServiceArea[]>([])
const items = ref<ServiceItem[]>([])
/** 可接服务项目是否被改动（后端不回显已选项目，未改动时不提交以免覆盖） */
const itemsTouched = ref(false)

const formRef = ref<FormInstance>()
const form = reactive({
  nickname: '',
  avatar: '',
  intro: '',
  experienceYears: 0,
  goodAtBreeds: [] as string[],
  areaId: undefined as number | undefined,
  certImages: [] as string[],
  itemIds: [] as number[],
})

const rules: FormRules = {
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { max: 64, message: '昵称不能超过 64 字', trigger: 'blur' },
  ],
  experienceYears: [{ required: true, message: '请填写从业年限', trigger: 'change' }],
  areaId: [{ required: true, type: 'number' as const, message: '请选择所属区域', trigger: 'change' }],
}

/** 后端 JSON 数组字符串（如 certImages/goodAtBreeds）兼容解析 */
function parseList(val?: string[] | string): string[] {
  if (!val) return []
  if (Array.isArray(val)) return val
  try {
    const parsed: unknown = JSON.parse(val)
    if (Array.isArray(parsed)) return parsed as string[]
  } catch {
    /* 非 JSON，按单值处理 */
  }
  return val ? [val] : []
}

/** 头像单图（ImageUploader 是数组模型） */
const avatarList = computed<string[]>({
  get: () => (form.avatar ? [form.avatar] : []),
  set: (v) => {
    form.avatar = v.length ? (v[v.length - 1] as string) : ''
  },
})

/** 常用品种（支持自定义输入） */
const BREED_OPTIONS = [
  '金毛寻回犬',
  '拉布拉多',
  '泰迪',
  '比熊',
  '博美',
  '柯基',
  '柴犬',
  '边境牧羊犬',
  '中华田园犬',
  '英国短毛猫',
  '美国短毛猫',
  '布偶猫',
  '其他',
]

const areaName = computed(() => areas.value.find((a) => a.id === form.areaId)?.name ?? '未设置')

/**
 * el-checkbox-group 的值类型是 (string | number)[]，
 * 而 form.itemIds 是 number[]，直接 v-model 会有回写类型冲突，
 * 故显式监听更新并统一转成 number。
 */
function onItemsChange(val: (string | number)[]): void {
  form.itemIds = val.map((v) => Number(v)).filter((v) => Number.isInteger(v))
  itemsTouched.value = true
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [p, u, a, it] = await Promise.all([
      fetchMyProfile(),
      fetchProfile(),
      fetchServiceAreas().catch(() => [] as ServiceArea[]),
      fetchServiceItems({ page: 1, size: 100 }).catch(() => ({ list: [] as ServiceItem[] })),
    ])
    profile.value = p
    user.value = u

    form.nickname = u?.nickname ?? ''
    form.avatar = u?.avatar ?? ''
    form.intro = p?.intro ?? ''
    form.experienceYears = Number(p?.experienceYears ?? 0)
    form.goodAtBreeds = parseList(p?.goodAtBreeds)
    form.areaId = p?.areaId ?? undefined
    form.certImages = parseList(p?.certImages)
    // 后端 myProfile 不返回已选服务项目，仅在用户主动勾选后提交
    form.itemIds = []
    itemsTouched.value = false

    areas.value = a ?? []
    items.value = it?.list ?? []
  } catch (e) {
    error.value = (e as Error)?.message || '资料加载失败'
  } finally {
    loading.value = false
  }
}

async function save(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const payload: StaffProfilePayload = {
      intro: form.intro,
      experienceYears: form.experienceYears,
      goodAtBreeds: form.goodAtBreeds,
      areaId: form.areaId,
      certImages: form.certImages,
    }
    if (itemsTouched.value) payload.itemIds = form.itemIds
    await updateMyProfile(payload)

    const userChanged =
      form.nickname !== (user.value?.nickname ?? '') || form.avatar !== (user.value?.avatar ?? '')
    if (userChanged) {
      await updateProfile({ nickname: form.nickname, avatar: form.avatar })
    }

    ElMessage.success('个人资料已保存')
    itemsTouched.value = false
    await load()
  } catch {
    // 拦截器已统一提示
  } finally {
    saving.value = false
  }
}

onMounted(() => void load())
</script>

<template>
  <div class="staff-profile" v-loading="loading">
    <ErrorState v-if="error && !loading" :text="error" @retry="load" />

    <template v-else>
      <el-row :gutter="16">
        <!-- 表单 -->
        <el-col :xs="24" :lg="16">
          <PageCard title="基本资料" class="staff-profile__card">
            <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
              <el-form-item label="头像">
                <ImageUploader
                  v-model="avatarList"
                  :limit="1"
                  tip="支持 jpg / png / gif / webp，单张不超过 5MB"
                />
              </el-form-item>

              <el-form-item label="昵称" prop="nickname">
                <el-input v-model="form.nickname" maxlength="64" show-word-limit placeholder="客户看到的称呼" />
              </el-form-item>

              <el-form-item label="个人简介" prop="intro">
                <el-input
                  v-model="form.intro"
                  type="textarea"
                  :rows="4"
                  maxlength="2000"
                  show-word-limit
                  placeholder="服务经验、服务态度、上门习惯等（选填）"
                />
              </el-form-item>

              <el-form-item label="从业年限" prop="experienceYears">
                <el-input-number
                  v-model="form.experienceYears"
                  :min="0"
                  :max="50"
                  :step="1"
                  controls-position="right"
                  style="width: 180px"
                />
                <span class="aux-text staff-profile__unit">年</span>
              </el-form-item>

              <el-form-item label="擅长品种" prop="goodAtBreeds">
                <el-select
                  v-model="form.goodAtBreeds"
                  multiple
                  filterable
                  allow-create
                  default-first-option
                  :reserve-keyword="false"
                  placeholder="选择或输入品种后回车"
                  style="width: 100%"
                >
                  <el-option v-for="b in BREED_OPTIONS" :key="b" :label="b" :value="b" />
                </el-select>
              </el-form-item>

              <el-form-item label="所属区域" prop="areaId">
                <el-select v-model="form.areaId" placeholder="选择可服务区域" style="width: 100%">
                  <el-option v-for="a in areas" :key="a.id" :label="a.name" :value="a.id" />
                </el-select>
              </el-form-item>
            </el-form>
          </PageCard>

          <PageCard title="资质证书" class="staff-profile__card">
            <template #header>
              <StatusTag v-if="profile" kind="cert" :value="profile.certStatus ?? 0" />
            </template>
            <ImageUploader
              v-model="form.certImages"
              :limit="6"
              tip="健康证 / 宠物护理证书等，支持 jpg / png / gif / webp，单张不超过 5MB"
            />
            <el-alert
              v-if="profile?.certStatus === 2 && profile.rejectReason"
              type="error"
              :closable="false"
              show-icon
              :title="`认证被驳回：${profile.rejectReason}`"
              description="请根据驳回原因修改资质后重新提交。"
              class="staff-profile__alert"
            />
          </PageCard>

          <PageCard title="可提供的服务项目" class="staff-profile__card">
            <div v-if="items.length" class="staff-profile__items">
              <el-checkbox-group :model-value="form.itemIds" @update:model-value="onItemsChange">
                <el-checkbox v-for="it in items" :key="it.id" :value="it.id" class="staff-profile__item">
                  {{ it.name }}<span class="aux-text">（¥{{ fmtMoney(it.price) }}/次）</span>
                </el-checkbox>
              </el-checkbox-group>
            </div>
            <EmptyState v-else text="暂无可选服务项目" />
            <p class="aux-text staff-profile__tip">
              勾选后保存即更新可接单范围；未做任何勾选时保存不会改动已有配置。
            </p>
          </PageCard>

          <div class="staff-profile__footer">
            <el-button type="primary" size="large" :loading="saving" @click="save">保存资料</el-button>
            <el-button size="large" :disabled="saving" @click="load">重置</el-button>
          </div>
        </el-col>

        <!-- 概览 -->
        <el-col :xs="24" :lg="8">
          <PageCard title="认证状态" class="staff-profile__card">
            <div class="staff-profile__cert">
              <StatusTag v-if="profile" kind="cert" :value="profile.certStatus ?? 0" />
              <span v-if="profile?.certStatus === 1" class="aux-text">认证通过后才能被客户搜索到并接单</span>
              <span v-else-if="profile?.certStatus === 0" class="aux-text">资料提交后由管理员审核，请耐心等待</span>
            </div>
            <div class="staff-profile__phone">
              <span class="aux-text">登录手机号</span>
              <strong>{{ user?.phone || '-' }}</strong>
            </div>
          </PageCard>

          <el-row :gutter="16" class="staff-profile__card">
            <el-col :span="12">
              <StatCard label="综合评分" :value="Number(profile?.avgRating ?? 0).toFixed(1)" suffix="分" color="warning" />
            </el-col>
            <el-col :span="12">
              <StatCard label="累计完成" :value="profile?.completedCount ?? 0" suffix="单" color="success" />
            </el-col>
          </el-row>

          <PageCard title="填写说明" class="staff-profile__card">
            <ul class="staff-profile__notes">
              <li>简介与资质会展示在客户的服务人员主页。</li>
              <li>擅长品种用于区域 / 品种筛选时的优先推荐。</li>
              <li>资质变更需管理员重新审核，期间不影响已有订单。</li>
              <li>当前服务区域：{{ areaName }}</li>
            </ul>
          </PageCard>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<style scoped>
.staff-profile__card {
  margin-bottom: 16px;
}

.staff-profile__unit {
  margin-left: 8px;
}

.staff-profile__items {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 8px 12px;
}

.staff-profile__item {
  margin-right: 0;
}

.staff-profile__tip {
  margin: 12px 0 0;
  line-height: 1.6;
}

.staff-profile__alert {
  margin-top: 12px;
  border-radius: var(--radius-btn);
}

.staff-profile__cert {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 12px;
}

.staff-profile__phone {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
  font-size: var(--font-size-body);
  color: var(--color-text-primary);
}

.staff-profile__notes {
  margin: 0;
  padding-left: 18px;
  font-size: var(--font-size-aux);
  line-height: 1.9;
  color: var(--color-text-muted);
}

.staff-profile__footer {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
</style>
