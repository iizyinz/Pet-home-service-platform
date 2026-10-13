<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell } from '@element-plus/icons-vue'
import { fetchNotices, fetchUnreadCount, markAllRead, markNoticeRead } from '@/api/notices'
import { useAppStore } from '@/stores/app'
import { fmtTime } from '@/utils/constants'
import type { Notice } from '@/types'

/** 未读通知铃铛：60s 轮询未读数 + 下拉近 5 条 + 全部已读 */
const router = useRouter()
const app = useAppStore()

const loading = ref(false)
const recent = ref<Notice[]>([])
let timer: number | undefined

async function loadCount() {
  try {
    const res = await fetchUnreadCount()
    app.setUnread(res?.count ?? 0)
  } catch {
    /* 轮询失败静默，避免打扰 */
  }
}

async function loadRecent() {
  loading.value = true
  try {
    const res = await fetchNotices(1, 5)
    recent.value = res?.list ?? []
  } finally {
    loading.value = false
  }
}

async function onReadAll() {
  await markAllRead()
  app.setUnread(0)
  recent.value = recent.value.map((n) => ({ ...n, isRead: 1 }))
}

async function onItemClick(n: Notice) {
  if (!n.isRead) {
    await markNoticeRead(n.id)
    app.setUnread(Math.max(0, app.unread - 1))
  }
  router.push('/messages')
}

onMounted(() => {
  void loadCount()
  timer = window.setInterval(() => void loadCount(), 60_000)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<template>
  <el-dropdown trigger="click" @visible-change="(v: boolean) => v && loadRecent()">
    <span class="notice-bell">
      <el-badge :value="app.unread || undefined" :max="99" :offset="[4, 6]">
        <el-icon :size="18"><Bell /></el-icon>
      </el-badge>
    </span>

    <template #dropdown>
      <div class="notice-panel">
        <div class="notice-panel__head">
          <span>通知</span>
          <el-button v-if="app.unread" link type="primary" size="small" @click="onReadAll">全部已读</el-button>
        </div>

        <div v-loading="loading" class="notice-panel__body">
          <p v-if="!recent.length" class="notice-panel__empty">暂无通知</p>
          <div
            v-for="n in recent"
            :key="n.id"
            class="notice-item"
            :class="{ 'is-unread': !n.isRead }"
            @click="onItemClick(n)"
          >
            <span class="notice-item__title">{{ n.title }}</span>
            <span class="notice-item__time aux-text">{{ fmtTime(n.createdAt) }}</span>
          </div>
        </div>

        <div class="notice-panel__foot">
          <el-button link type="primary" size="small" @click="router.push('/messages')">查看全部</el-button>
        </div>
      </div>
    </template>
  </el-dropdown>
</template>

<style scoped>
.notice-bell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: background 0.15s ease;
}
.notice-bell:hover {
  background: var(--color-bg-page);
  color: var(--color-primary);
}

.notice-panel {
  width: 300px;
  padding: 12px;
}

.notice-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
  padding-bottom: 8px;
  border-bottom: 1px solid var(--color-border);
}

.notice-panel__body {
  min-height: 80px;
  max-height: 320px;
  overflow-y: auto;
  padding: 4px 0;
}

.notice-panel__empty {
  margin: 24px 0;
  text-align: center;
  color: var(--color-text-muted);
  font-size: var(--font-size-aux);
}

.notice-item {
  padding: 8px;
  border-radius: 8px;
  cursor: pointer;
}
.notice-item:hover {
  background: var(--color-bg-page);
}
.notice-item__title {
  display: block;
  font-size: var(--font-size-body);
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.notice-item.is-unread .notice-item__title {
  font-weight: 600;
}
.notice-item__time {
  display: block;
  margin-top: 2px;
}

.notice-panel__foot {
  border-top: 1px solid var(--color-border);
  padding-top: 8px;
  text-align: center;
}
</style>
