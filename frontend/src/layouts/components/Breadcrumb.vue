<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

/** 面包屑：由 route.matched 的 meta.title 自动生成 */
const route = useRoute()

const crumbs = computed(() =>
  route.matched
    .filter((r) => r.meta.title && !r.meta.hidden)
    .map((r) => ({ title: r.meta.title as string, path: r.path })),
)
</script>

<template>
  <el-breadcrumb class="breadcrumb" separator="/">
    <el-breadcrumb-item :to="{ path: crumbs[0]?.path || '/' }">{{ crumbs[0]?.title }}</el-breadcrumb-item>
    <el-breadcrumb-item v-for="(c, i) in crumbs.slice(1)" :key="c.path + i">{{ c.title }}</el-breadcrumb-item>
  </el-breadcrumb>
</template>

<style scoped>
.breadcrumb {
  font-size: var(--font-size-aux);
}
</style>
