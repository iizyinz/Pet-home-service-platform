<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Camera, Lock, SwitchButton } from '@element-plus/icons-vue'
import PageCard from '@/components/common/PageCard.vue'
import StatCard from '@/components/common/StatCard.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ImageUploader from '@/components/common/ImageUploader.vue'
import ConfirmAction from '@/components/common/ConfirmAction.vue'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'
import { fetchProfile, updateProfile, fetchBalance, saveAvatar } from '@/api/user'
import { fetchPets, fetchAddresses } from '@/api/pets'
import { fetchUnreadCount } from '@/api/notices'
import { fmtTime, maskPhone } from '@/utils/constants'
import type { SysUser } from '@/types'

/**
 * 个人中心（PRD 5.9）：头像上传保存 / 昵称与手机号编辑 / 修改密码 / 角色与状态 /
 * 余额·档案·地址·未读通知统计入口 / 退出登录
 * 更新资料后同步 auth store（refreshProfile），保证顶栏昵称即时刷新
 */
const router = useRouter()
const auth = useAuthStore()
const app = useAppStore()

const profile = ref<SysUser | null>(null)
const loading = ref(true)
const error = ref('')

/* ---------- 统计入口 ---------- */
const balance = ref(0)
const petCount = ref(0)
const addressCount = ref(0)

/* ---------- 头像 ---------- */
const avatarList = ref<string[]>([])
const savingAvatar = ref(false)

/* ---------- 基本信息 ---------- */
const infoRef = ref<FormInstance>()
const savingInfo = ref(false)
const infoForm = reactive({ nickname: '', phone: '' })

const infoRules: FormRules = {
  nickname: [{ max: 64, message: '昵称不能超过 64 个字符', trigger: 'blur' }],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
}

/* ---------- 修改密码 ---------- */
const pwdVisible = ref(false)
const savingPwd = ref(false)
const pwdRef = ref<FormInstance>()
const pwdError = ref('')
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 64, message: '新密码长度需为 6 ~ 64 位', trigger: 'blur' },
  ],
  confirm: [
    {
      validator: (_r, value: string, cb) => {
        if (!value) return cb(new Error('请再次输入新密码'))
        if (value !== pwdForm.newPassword) return cb(new Error('两次输入的密码不一致'))
        cb()
      },
      trigger: 'blur',
    },
  ],
}

function text(v: unknown): string {
  return v === undefined || v === null ? '' : String(v)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    profile.value = await fetchProfile()
    infoForm.nickname = text(profile.value?.nickname)
    infoForm.phone = text(profile.value?.phone)
    avatarList.value = profile.value?.avatar ? [profile.value.avatar] : []
  } catch (e) {
    error.value = (e as Error)?.message || '个人信息加载失败'
    profile.value = null
  } finally {
    loading.value = false
  }

  // 统计数据尽力加载，失败不阻断页面
  try {
    const [b, p, a, u] = await Promise.all([
      fetchBalance().catch(() => null),
      fetchPets().catch(() => null),
      fetchAddresses().catch(() => null),
      fetchUnreadCount().catch(() => null),
    ])
    balance.value = Number(b?.balance ?? 0)
    petCount.value = (p ?? []).length
    addressCount.value = (a ?? []).length
    app.setUnread(u?.count ?? 0)
  } catch {
    /* 静默：统计卡片保持 0 */
  }
}

/** 头像保存：PUT /user/profile { avatar } 后同步 store */
async function onSaveAvatar() {
  const url = avatarList.value[0]
  if (!url) {
    ElMessage.warning('请先上传头像图片')
    return
  }
  savingAvatar.value = true
  try {
    await saveAvatar(url)
    await auth.refreshProfile()
    ElMessage.success('头像已更新')
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    savingAvatar.value = false
  }
}

/** 昵称 / 手机号保存（手机号留空则不修改，与后端语义一致） */
async function onSaveInfo() {
  const valid = await infoRef.value?.validate().catch(() => false)
  if (!valid) return

  savingInfo.value = true
  try {
    await updateProfile({
      nickname: infoForm.nickname.trim() || undefined,
      phone: infoForm.phone.trim() || undefined,
    })
    await auth.refreshProfile()
    ElMessage.success('个人信息已保存')
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    savingInfo.value = false
  }
}

function openPwd() {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirm = ''
  pwdError.value = ''
  pwdVisible.value = true
  pwdRef.value?.clearValidate()
}

/** 修改密码：oldPassword + newPassword 成对提交；原密码错误 / 新密码过短等由后端校验 */
async function onSavePwd() {
  const valid = await pwdRef.value?.validate().catch(() => false)
  if (!valid) return

  pwdError.value = ''
  savingPwd.value = true
  try {
    await updateProfile({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
    })
    ElMessage.success('密码修改成功，下次登录请使用新密码')
    pwdVisible.value = false
  } catch (e) {
    // 拦截器已统一 toast，这里在弹窗内保留错误文案，便于用户直接修正
    pwdError.value = (e as Error)?.message || '密码修改失败，请稍后重试'
  } finally {
    savingPwd.value = false
  }
}

/** 退出登录：清空本地凭证并回到登录页 */
function onLogout() {
  auth.logout()
  router.replace('/login')
}

onMounted(load)
</script>

<template>
  <div class="profile" v-loading="loading">
    <div class="profile__topbar">
      <span class="profile__title">个人中心</span>
    </div>

    <ErrorState v-if="error && !loading" :text="error" retry-text="重新加载" @retry="load" />

    <EmptyState v-else-if="!profile" text="个人信息加载失败" action-text="重新加载" @action="load" />

    <template v-else>
      <el-row :gutter="16">
        <!-- 用户卡片 + 头像 -->
        <el-col :xs="24" :lg="8">
          <PageCard title="我的头像" class="profile__card">
            <div class="profile__avatar-wrap">
              <el-avatar :size="88" class="profile__avatar">
                <img v-if="avatarList[0]" :src="avatarList[0]" alt="" />
                <span v-else>{{ (profile?.nickname || profile?.username || '宠')[0] }}</span>
              </el-avatar>
            </div>
            <ImageUploader
              v-model="avatarList"
              :limit="1"
              tip="支持 jpg / png / gif / webp，单张不超过 5MB"
              class="profile__uploader"
            />
            <el-button
              type="primary"
              class="profile__save-avatar"
              :loading="savingAvatar"
              :disabled="!avatarList.length"
              @click="onSaveAvatar"
            >
              <el-icon><Camera /></el-icon>&nbsp;保存头像
            </el-button>
          </PageCard>

          <PageCard title="账号信息" class="profile__card">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="账号">{{ profile?.username || '-' }}</el-descriptions-item>
              <el-descriptions-item label="昵称">{{ profile?.nickname || '-' }}</el-descriptions-item>
              <el-descriptions-item label="手机号">{{ maskPhone(profile?.phone) }}</el-descriptions-item>
              <el-descriptions-item label="角色">
                <StatusTag kind="role" :value="profile?.role || 'USER'" />
              </el-descriptions-item>
              <el-descriptions-item label="账号状态">
                <StatusTag kind="user" :value="profile?.status ?? 1" />
              </el-descriptions-item>
              <el-descriptions-item label="最近登录">
                {{ fmtTime(profile?.lastLoginAt) }}
              </el-descriptions-item>
              <el-descriptions-item label="注册时间">
                {{ fmtTime(profile?.createdAt) }}
              </el-descriptions-item>
            </el-descriptions>
          </PageCard>
        </el-col>

        <!-- 基本信息 + 修改密码 -->
        <el-col :xs="24" :lg="16">
          <PageCard title="基本信息" class="profile__card">
            <el-form ref="infoRef" :model="infoForm" :rules="infoRules" label-width="88px">
              <el-form-item label="昵称" prop="nickname">
                <el-input v-model="infoForm.nickname" maxlength="64" placeholder="展示给服务师的昵称" clearable />
              </el-form-item>
              <el-form-item label="手机号" prop="phone">
                <el-input v-model="infoForm.phone" maxlength="11" placeholder="11 位手机号" clearable />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="savingInfo" @click="onSaveInfo">保存资料</el-button>
                <el-button :icon="Lock" @click="openPwd">修改密码</el-button>
              </el-form-item>
            </el-form>
          </PageCard>

          <PageCard title="我的服务" class="profile__card">
            <div class="profile__stats">
              <div class="profile__stat" @click="router.push('/orders')">
                <StatCard label="我的订单" value="查看" color="primary" />
              </div>
              <div class="profile__stat" @click="router.push('/wallet')">
                <StatCard label="账户余额" :value="balance.toFixed(2)" prefix="¥" color="success" />
              </div>
              <div class="profile__stat" @click="router.push('/pets')">
                <StatCard label="宠物档案" :value="petCount" suffix="只" color="warning" />
              </div>
              <div class="profile__stat" @click="router.push('/addresses')">
                <StatCard label="上门地址" :value="addressCount" suffix="个" color="info" />
              </div>
              <div class="profile__stat" @click="router.push('/messages')">
                <StatCard label="未读通知" :value="app.unread" suffix="条" color="danger" />
              </div>
              <div class="profile__stat" @click="router.push('/my-reviews')">
                <StatCard label="我的评价" value="查看" color="primary" />
              </div>
            </div>
          </PageCard>

          <PageCard title="账号操作" class="profile__card">
            <div class="profile__logout">
              <ConfirmAction
                title="确认退出登录？"
                message="退出后需重新输入账号密码才能使用下单等需登录功能。"
                danger
                @confirm="onLogout"
              >
                <el-button type="danger" plain :icon="SwitchButton">退出登录</el-button>
              </ConfirmAction>
              <span class="aux-text">退出后本地凭证将被清除，收藏与历史订单保留。</span>
            </div>
          </PageCard>
        </el-col>
      </el-row>
    </template>

    <!-- 修改密码 -->
    <el-dialog v-model="pwdVisible" title="修改密码" width="420px" destroy-on-close>
      <el-form ref="pwdRef" :model="pwdForm" :rules="pwdRules" label-width="88px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input
            v-model="pwdForm.oldPassword"
            type="password"
            show-password
            maxlength="64"
            placeholder="请输入当前密码"
          />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="pwdForm.newPassword"
            type="password"
            show-password
            maxlength="64"
            placeholder="6 ~ 64 位"
          />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirm">
          <el-input
            v-model="pwdForm.confirm"
            type="password"
            show-password
            maxlength="64"
            placeholder="再次输入新密码"
          />
        </el-form-item>
      </el-form>
      <el-alert
        v-if="pwdError"
        type="error"
        :closable="false"
        show-icon
        :title="pwdError"
        class="profile__pwd-error"
      />
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingPwd" @click="onSavePwd">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.profile__topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.profile__title {
  font-size: var(--font-size-module);
  font-weight: 600;
  color: var(--color-text-primary);
}

.profile__card {
  margin-bottom: 16px;
}

.profile__avatar-wrap {
  display: flex;
  justify-content: center;
  margin-bottom: 12px;
}

.profile__avatar {
  background: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
  font-size: 30px;
}

.profile__uploader :deep(.image-uploader__tip) {
  text-align: center;
}

.profile__save-avatar {
  width: 100%;
  margin-top: 4px;
}

.profile__stats {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 12px;
}

.profile__stat {
  cursor: pointer;
}

.profile__logout {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.profile__pwd-error {
  margin-top: 4px;
}

@media (max-width: 768px) {
  .profile__stats {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
