<script setup lang="ts">
import { useRouter } from 'vue-router'
import { SwitchButton, User } from '@element-plus/icons-vue'
import NoticeBell from './components/NoticeBell.vue'
import { staffMenus } from '@/router/menus'
import { useAuthStore } from '@/stores/auth'
import { useProfileSync } from '@/composables/useProfileSync'

/** 服务人员端主布局：顶部导航（PRD 6 工作台视角），主题 theme-staff */
const router = useRouter()
const auth = useAuthStore()
useProfileSync()

function logout() {
  auth.logout()
  router.replace('/login')
}
</script>

<template>
  <div class="staff-layout">
    <header class="staff-layout__header">
      <div class="staff-layout__brand">
        <span class="staff-layout__mark">🐾</span>
        <span class="staff-layout__title">宠物上门 · 服务人员工作台</span>
      </div>

      <nav class="staff-layout__nav">
        <router-link
          v-for="item in staffMenus"
          :key="item.path"
          :to="item.path"
          class="staff-layout__nav-item"
          :class="{ 'is-active': $route.path === item.path || $route.meta.activeMenu === item.path }"
        >
          <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </router-link>
      </nav>

      <div class="staff-layout__right">
        <NoticeBell />
        <el-dropdown trigger="click" @command="(c: string) => c === 'logout' && logout()">
          <span class="staff-layout__user">
            <el-avatar :size="28" :src="auth.user?.avatar || undefined" class="staff-layout__avatar">
              <el-icon><User /></el-icon>
            </el-avatar>
            {{ auth.displayName }}
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">
                <el-icon><SwitchButton /></el-icon>
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <main class="staff-layout__content">
      <router-view />
    </main>

    <!-- 移动端底部导航（复用用户端 Tab 结构） -->
    <nav class="staff-layout__tabbar">
      <router-link v-for="t in staffMenus.slice(0, 4)" :key="t.path" :to="t.path" class="staff-layout__tab">
        <el-icon v-if="t.icon"><component :is="t.icon" /></el-icon>
        <span>{{ t.title }}</span>
      </router-link>
    </nav>
  </div>
</template>

<style scoped>
.staff-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--color-bg-page);
}

.staff-layout__header {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 24px;
  height: 60px;
  padding: 0 16px;
  background: var(--color-bg-card);
  box-shadow: var(--shadow-card);
}

.staff-layout__brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--font-size-module);
  font-weight: 600;
  color: var(--color-text-primary);
  white-space: nowrap;
}

.staff-layout__nav {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1 1 auto;
  overflow-x: auto;
}

.staff-layout__nav-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 40px;
  padding: 0 14px;
  border-radius: 12px;
  color: var(--color-text-secondary);
  text-decoration: none;
  font-size: var(--font-size-body);
  white-space: nowrap;
  transition: all 0.15s ease;
}

.staff-layout__nav-item:hover {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.staff-layout__nav-item.is-active {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  font-weight: 600;
}

.staff-layout__right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.staff-layout__user {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: var(--font-size-body);
  color: var(--color-text-primary);
}

.staff-layout__content {
  flex: 1 1 auto;
  width: 100%;
  max-width: 1280px;
  margin: 0 auto;
  padding: 16px;
}

.staff-layout__tabbar {
  display: none;
}

@media (max-width: 768px) {
  .staff-layout__nav {
    display: none;
  }
  .staff-layout__content {
    padding-bottom: 76px;
  }
  .staff-layout__tabbar {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 30;
    display: flex;
    height: 60px;
    background: var(--color-bg-card);
    box-shadow: 0 -4px 12px rgba(0, 0, 0, 0.06);
  }
  .staff-layout__tab {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;
    font-size: 12px;
    color: var(--color-text-muted);
    text-decoration: none;
  }
  .staff-layout__tab.is-active,
  .staff-layout__tab.router-link-active {
    color: var(--el-color-primary);
  }
}
</style>
