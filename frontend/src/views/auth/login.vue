<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { refreshCaptcha } from './captcha'
import { register } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

/**
 * 统一登录/注册页（PRD 5.1：账号密码 + 图形验证码 + 记住我）
 * /login     -> 用户/服务人员端主题（暖橙）
 * /admin/login -> 管理后台主题（信任蓝）
 */
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const isAdmin = computed(() => route.path.startsWith('/admin'))
const mode = ref<'login' | 'register'>('login')
const loading = ref(false)
const captchaSrc = ref('')
const captchaKey = ref('')

const formRef = ref<FormInstance>()
const form = reactive({
  username: '',
  password: '',
  captchaCode: '',
  rememberMe: true,
  // 注册附加
  nickname: '',
  phone: '',
  role: 'USER' as 'USER' | 'STAFF',
})

const loginRules: FormRules = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 3, max: 64, message: '账号长度 3-64 位', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度 6-32 位', trigger: 'blur' },
  ],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
}

const registerRules: FormRules = {
  ...loginRules,
  nickname: [{ max: 64, message: '昵称最长 64 字', trigger: 'blur' }],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
}

const rules = computed<FormRules>(() => (mode.value === 'login' ? loginRules : registerRules))

async function loadCaptcha() {
  try {
    const res = await refreshCaptcha()
    captchaKey.value = res?.captchaKey ?? ''
    const img = res?.captchaImg ?? ''
    captchaSrc.value = img ? (img.startsWith('data:') ? img : `data:image/png;base64,${img}`) : ''
  } catch {
    ElMessage.error('验证码获取失败，请点击刷新重试')
  }
}

async function onSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    if (mode.value === 'register') {
      await register({
        username: form.username,
        password: form.password,
        nickname: form.nickname || undefined,
        phone: form.phone || undefined,
        role: form.role,
        captchaKey: captchaKey.value,
        captchaCode: form.captchaCode,
      })
      ElMessage.success('注册成功，请使用账号登录')
      mode.value = 'login'
      form.password = ''
      await loadCaptcha()
      form.captchaCode = ''
      return
    }

    await auth.login({
      username: form.username,
      password: form.password,
      captchaKey: captchaKey.value,
      captchaCode: form.captchaCode,
      rememberMe: form.rememberMe,
    })

    const redirect = route.query.redirect as string | undefined
    router.replace(redirect || auth.homeRouteByRole())
  } catch {
    // 拦截器已统一 toast（1001 验证码错误 / 1004 密码错误 / 1005 冻结 / 1007 限流）
    await loadCaptcha()
    form.captchaCode = ''
  } finally {
    loading.value = false
  }
}

onMounted(loadCaptcha)
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-card__brand">
        <span class="login-card__logo">🐾</span>
        <h1 class="login-card__title">{{ isAdmin ? '宠物上门服务 · 管理后台' : '宠物上门服务' }}</h1>
        <p class="login-card__subtitle">
          {{ mode === 'login' ? '下单 · 履约 · 评价 · 追溯，一站式上门宠物服务' : '注册后即可开始预约上门服务' }}
        </p>
      </div>

      <el-tabs v-model="mode" class="login-card__tabs">
        <el-tab-pane label="登录" name="login" />
        <el-tab-pane label="注册" name="register" />
      </el-tabs>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="onSubmit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入账号" clearable />
        </el-form-item>

        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password @keyup.enter="onSubmit" />
        </el-form-item>

        <template v-if="mode === 'register'">
          <el-form-item prop="nickname">
            <el-input v-model="form.nickname" placeholder="昵称（选填）" clearable />
          </el-form-item>
          <el-form-item prop="phone">
            <el-input v-model="form.phone" placeholder="手机号（选填）" clearable />
          </el-form-item>
          <el-form-item prop="role">
            <el-radio-group v-model="form.role">
              <el-radio-button value="USER">我是宠物主</el-radio-button>
              <el-radio-button value="STAFF">我是服务人员</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </template>

        <el-form-item prop="captchaCode">
          <div class="login-captcha">
            <el-input v-model="form.captchaCode" placeholder="验证码" clearable @keyup.enter="onSubmit" />
            <div class="login-captcha__img" title="点击刷新验证码" @click="loadCaptcha">
              <img v-if="captchaSrc" :src="captchaSrc" alt="验证码" />
              <span v-else class="login-captcha__placeholder">加载中…</span>
            </div>
          </div>
        </el-form-item>

        <div v-if="mode === 'login'" class="login-extra">
          <el-checkbox v-model="form.rememberMe">记住我</el-checkbox>
          <el-button link type="primary" size="small" @click="loadCaptcha">刷新验证码</el-button>
        </div>

        <el-button
          class="login-submit"
          type="primary"
          size="large"
          :loading="loading"
          native-type="submit"
        >
          {{ mode === 'login' ? '登 录' : '注 册' }}
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  background:
    radial-gradient(circle at 15% 20%, var(--el-color-primary-light-9) 0%, transparent 45%),
    radial-gradient(circle at 85% 80%, var(--color-info-light-9) 0%, transparent 45%),
    var(--color-bg-page);
}

.login-card {
  width: 100%;
  max-width: 420px;
  background: var(--color-bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 32px 28px 24px;
}

.login-card__brand {
  text-align: center;
  margin-bottom: 8px;
}

.login-card__logo {
  font-size: 40px;
}

.login-card__title {
  margin: 8px 0 4px;
  font-size: var(--font-size-title);
  font-weight: 600;
  color: var(--color-text-primary);
}

.login-card__subtitle {
  margin: 0;
  font-size: var(--font-size-aux);
  color: var(--color-text-muted);
}

.login-card__tabs {
  margin-bottom: 8px;
}

.login-card__tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.login-card__tabs :deep(.el-tabs__item) {
  flex: 1;
  text-align: center;
}

.login-captcha {
  display: flex;
  gap: 12px;
  width: 100%;
}

.login-captcha__img {
  flex: 0 0 128px;
  height: 44px;
  border: 1px solid var(--color-border-input);
  border-radius: var(--radius-btn);
  overflow: hidden;
  cursor: pointer;
  background: var(--color-bg-page);
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-captcha__img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.login-captcha__placeholder {
  font-size: var(--font-size-aux);
  color: var(--color-text-muted);
}

.login-extra {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.login-submit {
  width: 100%;
  font-size: var(--font-size-body);
  font-weight: 500;
}
</style>
