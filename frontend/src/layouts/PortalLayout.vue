<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search, SwitchButton, User } from '@element-plus/icons-vue'
import NoticeBell from './components/NoticeBell.vue'
import { portalTabs } from '@/router/menus'
import { useAuthStore } from '@/stores/auth'
import { useProfileSync } from '@/composables/useProfileSync'

/**
 * 用户端主布局：顶栏（Logo / 搜索 / AI 顾问 / 通知 / 头像）+ 内容 + 底部 Tab
 * 底部 Tab 严格按 PRD 5.2：首页 / 服务 / 订单 / 消息 / 我的
 */
const router = useRouter()
const auth = useAuthStore()
useProfileSync()

function onSearch() {
  router.push({ path: '/services', query: { keyword: keyword.value } })
}

function logout() {
  auth.logout()
  router.replace('/login')
}

const keyword = ref('')
</script>

<template>
  <div class="portal-layout">
    <header class="portal-layout__header">
      <div class="portal-layout__inner">
        <router-link to="/home" class="portal-layout__brand">
          <span class="portal-layout__mark">🐾</span>
          <span class="portal-layout__title">宠物上门服务</span>
        </router-link>

        <nav class="portal-layout__nav">
          <router-link
            v-for="t in portalTabs.slice(0, 2)"
            :key="t.path"
            :to="t.path"
            class="portal-layout__nav-item"
            :class="{ 'is-active': $route.path.startsWith(t.path) }"
          >
            {{ t.title }}
          </router-link>
          <router-link to="/ai" class="portal-layout__nav-item">AI 宠物顾问</router-link>
          <router-link to="/providers" class="portal-layout__nav-item">服务人员</router-link>
        </nav>

        <div class="portal-layout__search">
          <el-input
            v-model="keyword"
            placeholder="搜索服务项目"
            clearable
            :prefix-icon="Search"
            @keyup.enter="onSearch"
          />
        </div>

        <div class="portal-layout__right">
          <NoticeBell />
          <el-dropdown trigger="click" @command="(c: string) => (c === 'logout' ? logout() : router.push(`/${c}`))">
            <span class="portal-layout__user">
              <el-avatar
                :size="32"
                :src="auth.user?.avatar || undefined"
                class="portal-layout__avatar"
              >
                <el-icon><User /></el-icon>
              </el-avatar>
              <span class="portal-layout__name">{{ auth.displayName }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="wallet">我的钱包</el-dropdown-item>
                <el-dropdown-item command="pets">宠物档案</el-dropdown-item>
                <el-dropdown-item command="addresses">地址管理</el-dropdown-item>
                <el-dropdown-item command="favorites">我的收藏</el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </header>

    <main class="portal-layout__content">
      <router-view />
    </main>

    <footer class="portal-layout__footer">
      <div class="portal-layout__footer-inner">
        <span>© 2026 宠物上门服务</span>
        <span class="portal-layout__footer-links">下单 · 履约 · 评价 · 追溯</span>
      </div>
    </footer>

    <nav class="portal-layout__tabbar">
      <router-link v-for="t in portalTabs" :key="t.path" :to="t.path" class="portal-layout__tab">
        <el-icon :size="20"><component :is="t.icon" /></el-icon>
        <span>{{ t.title }}</span>
      </router-link>
    </nav>
  </div>
</template>

<style scoped>
.portal-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--color-bg-page);
}

.portal-layout__header {
  position: sticky;
  top: 0;
  z-index: 20;
  background: var(--color-bg-card);
  box-shadow: var(--shadow-card);
}

.portal-layout__inner {
  display: flex;
  align-items: center;
  gap: 20px;
  max-width: 1200px;
  margin: 0 auto;
  padding: 12px 16px;
}

.portal-layout__brand {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
  font-size: var(--font-size-module);
  font-weight: 600;
  color: var(--color-text-primary);
  white-space: nowrap;
}

.portal-layout__mark {
  font-size: 22px;
}

.portal-layout__nav {
  display: flex;
  align-items: center;
  gap: 4px;
}

.portal-layout__nav-item {
  padding: 8px 12px;
  border-radius: 12px;
  color: var(--color-text-secondary);
  text-decoration: none;
  font-size: var(--font-size-body);
  white-space: nowrap;
}

.portal-layout__nav-item:hover,
.portal-layout__nav-item.is-active {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  font-weight: 500;
}

.portal-layout__search {
  flex: 1 1 220px;
  max-width: 320px;
}

.portal-layout__right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.portal-layout__user {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.portal-layout__avatar {
  background: var(--el-color-primary);
  color: #fff;
}

.portal-layout__name {
  font-size: var(--font-size-body);
  color: var(--color-text-primary);
  max-width: 96px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.portal-layout__content {
  flex: 1 1 auto;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: 16px;
}

.portal-layout__footer {
  background: var(--color-bg-card);
  border-top: 1px solid var(--color-border);
}

.portal-layout__footer-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 1200px;
  margin: 0 auto;
  padding: 16px;
  font-size: var(--font-size-aux);
  color: var(--color-text-muted);
}

.portal-layout__tabbar {
  display: none;
}

@media (max-width: 768px) {
  .portal-layout__nav,
  .portal-layout__search,
  .portal-layout__footer {
    display: none;
  }
  .portal-layout__content {
    padding-bottom: 76px;
  }
  .portal-layout__tabbar {
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
  .portal-layout__tab {
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
  .portal-layout__tab.is-active,
  .portal-layout__tab.router-link-active {
    color: var(--el-color-primary);
  }
}
</style>
