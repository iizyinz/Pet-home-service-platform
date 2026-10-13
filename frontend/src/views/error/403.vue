<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Key, ArrowLeft } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

/** 权限不足页（UI 清单 第四节「权限不足页」） */
const router = useRouter()
const auth = useAuthStore()

function goBack() {
  router.replace(auth.isLogin ? auth.homeRouteByRole() : '/login')
}
</script>

<template>
  <div class="state-page">
    <div class="state-page__card">
      <el-icon class="state-page__icon" :size="64"><Key /></el-icon>
      <h2 class="state-page__title">权限不足</h2>
      <p class="state-page__desc">当前账号无权访问该页面，请切换到有权限的账号。</p>
      <div class="state-page__actions">
        <el-button type="primary" :icon="ArrowLeft" @click="goBack">返回首页</el-button>
        <el-button @click="router.push('/404')">返回 404</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.state-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-page);
  padding: 24px;
}

.state-page__card {
  background: var(--color-bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 48px 40px;
  text-align: center;
  max-width: 480px;
  width: 100%;
}

.state-page__icon {
  color: var(--color-warning);
}

.state-page__title {
  margin: 16px 0 8px;
  font-size: var(--font-size-title);
  color: var(--color-text-primary);
}

.state-page__desc {
  margin: 0 0 24px;
  font-size: var(--font-size-body);
  color: var(--color-text-secondary);
}

.state-page__actions {
  display: flex;
  justify-content: center;
  gap: 12px;
}
</style>
