<script setup lang="ts">
import { useRouter } from 'vue-router'
import { HomeFilled, Refresh } from '@element-plus/icons-vue'

/** 404 页面不存在（UI 清单 第四节） */
const router = useRouter()
</script>

<template>
  <div class="state-page">
    <div class="state-page__card">
      <div class="state-page__code">404</div>
      <h2 class="state-page__title">页面不存在</h2>
      <p class="state-page__desc">您访问的页面可能已被移除、重命名或从未存在。</p>
      <div class="state-page__actions">
        <el-button type="primary" :icon="HomeFilled" @click="router.replace('/')">返回首页</el-button>
        <el-button :icon="Refresh" @click="router.back()">返回上一页</el-button>
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

.state-page__code {
  font-size: 72px;
  font-weight: 600;
  line-height: 1;
  color: var(--el-color-primary);
}

.state-page__title {
  margin: 12px 0 8px;
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
