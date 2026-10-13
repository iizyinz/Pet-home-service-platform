<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound } from '@element-plus/icons-vue'
import PageCard from '@/components/common/PageCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import { usePage } from '@/composables/usePage'
import { useAppStore } from '@/stores/app'
import { fetchNotices, markNoticeRead, markAllRead, fetchUnreadCount } from '@/api/notices'
import { fetchConversations, type ChatConversationVO } from '@/api/chat'
import { fmtTime, ROLE_LABEL } from '@/utils/constants'
import type { Notice } from '@/types'

/**
 * 消息中心（PRD 5.7 / 5.9 / UI 清单「消息中心列表页」）：
 * 通知列表（未读高亮 + 全部已读 + 按类型跳转） + 会话列表（在线状态 + 未读数 -> /chat/:peerId）
 */
const router = useRouter()
const app = useAppStore()

const activeTab = ref<'notice' | 'chat'>('notice')

/* ---------- 站内通知 ---------- */
const { list, total, page, size, loading, error, search, refresh, changePage, changeSize } =
  usePage<Notice>((q) => fetchNotices(q.page ?? 1, q.size ?? 10), { size: 10 })

const NOTICE_TYPE_LABEL: Record<string, string> = {
  ORDER: '订单',
  REVIEW: '评价',
  CERT: '认证',
  SYSTEM: '系统',
}

function typeLabel(t?: string): string {
  return NOTICE_TYPE_LABEL[t ?? ''] || '系统'
}

function isUnread(n: Notice): boolean {
  return Number(n.isRead ?? 0) === 0
}

/** 按通知类型跳转：订单通知直达订单详情，评价通知回「我的评价」 */
function noticeRoute(n: Notice): string {
  if (n.type === 'ORDER') return n.bizId ? `/orders/${n.bizId}` : '/orders'
  if (n.type === 'REVIEW') return '/my-reviews'
  if (n.type === 'CERT') return '/providers'
  return ''
}

async function onNoticeClick(n: Notice) {
  if (isUnread(n)) {
    try {
      await markNoticeRead(n.id)
      app.setUnread(Math.max(0, app.unread - 1))
    } catch {
      /* 拦截器已提示 */
    }
  }
  const target = noticeRoute(n)
  if (target) {
    router.push(target)
  } else {
    n.isRead = 1
    ElMessage.info('该通知已标记为已读')
    await refresh()
  }
}

async function onReadAll() {
  if (!list.value.some(isUnread)) {
    ElMessage.info('没有未读通知')
    return
  }
  try {
    await markAllRead()
    app.setUnread(0)
    ElMessage.success('已全部标记为已读')
    await search()
  } catch {
    /* 拦截器已提示 */
  }
}

/* ---------- 会话列表 ---------- */
const conversations = ref<ChatConversationVO[]>([])
const convLoading = ref(false)
const convError = ref('')

async function loadConversations() {
  convLoading.value = true
  convError.value = ''
  try {
    conversations.value = (await fetchConversations()) ?? []
  } catch (e) {
    convError.value = (e as Error)?.message || '会话列表加载失败'
    conversations.value = []
  } finally {
    convLoading.value = false
  }
}

function onTab(tab: 'notice' | 'chat') {
  activeTab.value = tab
  if (tab === 'chat') void loadConversations()
  else void search()
}

function openChat(c: ChatConversationVO) {
  router.push(`/chat/${c.peerId}`)
}

function peerName(c: ChatConversationVO): string {
  return c.peerNickname || '联系人'
}

/** 通知未读数与全局铃铛保持一致：挂载时同步一次 */
async function syncUnread() {
  try {
    const res = await fetchUnreadCount()
    app.setUnread(res?.count ?? 0)
  } catch {
    /* 轮询兜底，静默 */
  }
}

onMounted(() => {
  void syncUnread()
  void loadConversations()
})
</script>

<template>
  <div class="messages">
    <div class="messages__topbar">
      <span class="messages__title">消息中心</span>
      <span v-if="app.unread" class="unread-dot">{{ app.unread > 99 ? '99+' : app.unread }}</span>
      <span class="messages__spacer" />
      <el-button v-if="activeTab === 'notice'" size="small" plain @click="onReadAll">全部已读</el-button>
      <el-button v-else size="small" plain @click="loadConversations">刷新会话</el-button>
    </div>

    <PageCard>
      <div class="msg-tabs">
        <button
          type="button"
          class="msg-tabs__item"
          :class="{ 'is-active': activeTab === 'notice' }"
          @click="onTab('notice')"
        >
          站内通知
        </button>
        <button
          type="button"
          class="msg-tabs__item"
          :class="{ 'is-active': activeTab === 'chat' }"
          @click="onTab('chat')"
        >
          聊天会话
        </button>
      </div>

      <!-- 通知列表 -->
      <div v-if="activeTab === 'notice'" v-loading="loading" class="msg-body">
        <ErrorState v-if="error" :text="error" @retry="refresh" />

        <EmptyState v-else-if="!list.length" text="暂无通知，服务进度与平台消息都会出现在这里" />

        <template v-else>
          <ul class="notice-list">
            <li
              v-for="n in list"
              :key="n.id"
              class="notice-item"
              :class="{ 'is-unread': isUnread(n) }"
              @click="onNoticeClick(n)"
            >
              <span v-if="isUnread(n)" class="notice-item__dot" />
              <div class="notice-item__body">
                <div class="notice-item__head">
                  <span class="biz-tag">{{ typeLabel(n.type) }}</span>
                  <span class="notice-item__title">{{ n.title }}</span>
                </div>
                <p v-if="n.content" class="notice-item__content aux-text">{{ n.content }}</p>
                <span class="aux-text">{{ fmtTime(n.createdAt) }}</span>
              </div>
            </li>
          </ul>

          <div v-if="total > size" class="messages__pager">
            <el-pagination
              :current-page="page"
              :page-size="size"
              :total="total"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              background
              @current-change="changePage"
              @size-change="changeSize"
            />
          </div>
        </template>
      </div>

      <!-- 会话列表 -->
      <div v-else v-loading="convLoading" class="msg-body">
        <ErrorState v-if="convError" :text="convError" @retry="loadConversations" />

        <EmptyState v-else-if="!conversations.length" text="暂无聊天会话">
          <template #tip>在服务人员详情或订单详情可以发起私信</template>
        </EmptyState>

        <ul v-else class="conv-list">
          <li v-for="c in conversations" :key="c.conversationId" class="conv-item" @click="openChat(c)">
            <div class="conv-item__avatar">
              <el-avatar :size="48" class="conv-item__avatar-inner">
                <img v-if="c.peerAvatar" :src="c.peerAvatar" alt="" />
                <span v-else>{{ peerName(c)[0] }}</span>
              </el-avatar>
              <span v-if="c.online" class="conv-item__online" title="在线" />
            </div>

            <div class="conv-item__body">
              <div class="conv-item__head">
                <span class="conv-item__name">{{ peerName(c) }}</span>
                <el-tag v-if="c.peerRole" size="small" effect="plain" round>
                  {{ c.peerRole ? ROLE_LABEL[c.peerRole] || c.peerRole : '' }}
                </el-tag>
                <span class="conv-item__time aux-text">{{ fmtTime(c.lastMessageAt) }}</span>
              </div>
              <div class="conv-item__last">
                <span class="conv-item__preview">{{ c.lastMessage || '暂无消息' }}</span>
                <span v-if="c.unreadCount" class="unread-dot">{{ c.unreadCount > 99 ? '99+' : c.unreadCount }}</span>
              </div>
            </div>

            <el-icon class="conv-item__arrow"><ChatDotRound /></el-icon>
          </li>
        </ul>
      </div>
    </PageCard>
  </div>
</template>

<style scoped>
.messages__topbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.messages__title {
  font-size: var(--font-size-module);
  font-weight: 600;
  color: var(--color-text-primary);
}

.messages__spacer {
  flex: 1;
}

.msg-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.msg-tabs__item {
  padding: 8px 20px;
  border-radius: var(--radius-btn);
  border: none;
  background: var(--color-bg-page);
  color: var(--color-text-secondary);
  font-size: var(--font-size-body);
  cursor: pointer;
  transition: all 0.15s ease;
}

.msg-tabs__item.is-active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}

.msg-body {
  min-height: 200px;
}

/* ---- 通知 ---- */
.notice-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.notice-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 14px 8px;
  border-bottom: 1px solid var(--color-border);
  border-radius: var(--radius-btn);
  cursor: pointer;
  transition: background 0.15s ease;
}

.notice-item:last-child {
  border-bottom: none;
}

.notice-item:hover {
  background: var(--color-bg-page);
}

.notice-item__dot {
  flex: 0 0 auto;
  width: 8px;
  height: 8px;
  margin-top: 8px;
  border-radius: 50%;
  background: var(--color-unread);
}

.notice-item__body {
  min-width: 0;
  flex: 1;
}

.notice-item__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.notice-item__title {
  font-size: var(--font-size-body);
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-item.is-unread .notice-item__title {
  font-weight: 600;
}

.notice-item__content {
  margin: 4px 0;
  line-height: 1.6;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

/* ---- 会话 ---- */
.conv-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.conv-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 8px;
  border-bottom: 1px solid var(--color-border);
  border-radius: var(--radius-btn);
  cursor: pointer;
  transition: background 0.15s ease;
}

.conv-item:last-child {
  border-bottom: none;
}

.conv-item:hover {
  background: var(--color-bg-page);
}

.conv-item__avatar {
  position: relative;
  flex: 0 0 auto;
}

.conv-item__avatar-inner {
  background: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
  font-size: 18px;
}

.conv-item__online {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--color-success);
  border: 2px solid var(--color-bg-card);
}

.conv-item__body {
  flex: 1;
  min-width: 0;
}

.conv-item__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.conv-item__name {
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
}

.conv-item__time {
  margin-left: auto;
  flex: 0 0 auto;
}

.conv-item__last {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}

.conv-item__preview {
  flex: 1;
  min-width: 0;
  font-size: var(--font-size-aux);
  color: var(--color-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-item__arrow {
  flex: 0 0 auto;
  color: var(--color-text-muted);
}

.messages__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

@media (max-width: 640px) {
  .messages__pager {
    justify-content: center;
  }
}
</style>
