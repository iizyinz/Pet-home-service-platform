<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import type { MenuGroup } from '@/router/menus'

/** 侧边菜单：按分组渲染，active 跟随当前路由（详情页高亮父级 activeMenu） */
withDefaults(defineProps<{ groups: MenuGroup[]; collapsed?: boolean }>(), {
  collapsed: false,
})

const route = useRoute()

const activePath = computed(() => {
  const active = (route.meta.activeMenu as string | undefined) || route.path
  // 数据看板是 /admin，需精确匹配避免吞掉子项
  return active
})

function isActive(path: string): boolean {
  if (path === '/admin') return activePath.value === '/admin'
  if (path === '/staff') return activePath.value === '/staff'
  return activePath.value === path
}
</script>

<template>
  <el-menu
    class="side-menu"
    :default-active="activePath"
    :collapse="collapsed"
    :collapse-transition="false"
    router
  >
    <el-sub-menu v-for="group in groups" :key="group.title" :index="group.title">
      <template #title>
        <!-- 折叠态只显示图标（Element Plus 会隐藏 title 里的 span），故分组图标必填 -->
        <el-icon v-if="group.icon" class="side-menu__group-icon"><component :is="group.icon" /></el-icon>
        <span>{{ group.title }}</span>
      </template>
      <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path" :class="{ 'is-active': isActive(item.path) }">
        <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
        <template #title>{{ item.title }}</template>
      </el-menu-item>
    </el-sub-menu>
  </el-menu>
</template>

<style scoped>
.side-menu {
  border-right: none;
  height: 100%;
}

.side-menu :deep(.el-menu-item) {
  height: 44px;
  line-height: 44px;
  border-radius: 12px;
  margin: 4px 8px;
  font-size: var(--font-size-body);
}

.side-menu :deep(.el-menu-item.is-active) {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}

.side-menu :deep(.el-sub-menu__title) {
  font-size: var(--font-size-aux);
  color: var(--color-text-muted);
}

.side-menu__group-icon {
  margin-right: 8px;
  font-size: 16px;
}
</style>
