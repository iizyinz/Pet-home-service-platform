<script setup lang="ts">
// 根组件：由路由渲染各端布局
// 主题类（theme-portal / theme-staff / theme-admin）由路由守卫挂到 <html> 上，
// 这样 teleport 到 body 的下拉、对话框、抽屉也能继承当端主色。
</script>

<template>
  <router-view />
</template>
