<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { Fold, Expand, ArrowDown, SwitchButton, User } from '@element-plus/icons-vue'
import SideMenu from './components/SideMenu.vue'
import Breadcrumb from './components/Breadcrumb.vue'
import NoticeBell from './components/NoticeBell.vue'
import { adminMenus } from '@/router/menus'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { useProfileSync } from '@/composables/useProfileSync'

/** 管理端主布局：可折叠侧边菜单 + 顶栏（面包屑/通知/用户）+ 内容区 */
const router = useRouter()
const app = useAppStore()
const auth = useAuthStore()
useProfileSync()

const menuGroups = computed(() => adminMenus)

function onCommand(cmd: string) {
  if (cmd === 'logout') {
    auth.logout()
    router.replace('/admin/login')
  }
}
</script>

<template>
  <el-container class="admin-layout">
    <el-aside class="admin-layout__aside" :width="app.collapsed ? '64px' : '220px'">
      <div class="admin-layout__logo" :class="{ 'is-collapsed': app.collapsed }">
        <span class="admin-layout__logo-mark">🐾</span>
        <span v-if="!app.collapsed" class="admin-layout__logo-text">宠物上门服务</span>
      </div>
      <SideMenu :groups="menuGroups" :collapsed="app.collapsed" />
    </el-aside>

    <!-- direction=vertical：el-container 仅当子节点是 <el-header>/<el-footer> 才自动转纵向，
         这里顶栏是原生 <header>，必须显式声明，否则顶栏会与内容左右并排 -->
    <el-container class="admin-layout__main" direction="vertical">
      <header class="admin-layout__header">
        <div class="admin-layout__header-left">
          <el-icon class="admin-layout__toggle" :size="18" @click="app.toggleCollapsed()">
            <component :is="app.collapsed ? Expand : Fold" />
          </el-icon>
          <Breadcrumb />
        </div>

        <div class="admin-layout__header-right">
          <NoticeBell />
          <el-dropdown trigger="click" @command="onCommand">
            <span class="admin-layout__user">
              <el-avatar
                :size="30"
                :src="auth.user?.avatar || undefined"
                class="admin-layout__avatar"
              >
                <el-icon><User /></el-icon>
              </el-avatar>
              <span class="admin-layout__name">{{ auth.displayName || '管理员' }}</span>
              <el-icon :size="12"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>角色：管理员</el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <el-main class="admin-layout__content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.admin-layout {
  height: 100vh;
}

.admin-layout__aside {
  background: var(--color-bg-card);
  border-right: 1px solid var(--color-border);
  overflow-x: hidden;
  overflow-y: auto;
  transition: width 0.2s ease;
}

.admin-layout__logo {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 56px;
  padding: 0 16px;
  border-bottom: 1px solid var(--color-border);
  font-size: var(--font-size-module);
  font-weight: 600;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
}

.admin-layout__logo.is-collapsed {
  justify-content: center;
  padding: 0;
}

.admin-layout__logo-mark {
  font-size: 20px;
}

.admin-layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 0 0 auto;
  height: 56px;
  padding: 0 16px;
  background: var(--color-bg-card);
  border-bottom: 1px solid var(--color-border);
}

.admin-layout__header-left,
.admin-layout__header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.admin-layout__toggle {
  color: var(--color-text-secondary);
  cursor: pointer;
}
.admin-layout__toggle:hover {
  color: var(--el-color-primary);
}

.admin-layout__user {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: var(--color-text-primary);
  font-size: var(--font-size-body);
}

.admin-layout__avatar {
  background: var(--el-color-primary);
  color: #fff;
}

.admin-layout__name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-layout__content {
  padding: 16px;
  background: var(--color-bg-page);
  overflow-y: auto;
}
</style>
