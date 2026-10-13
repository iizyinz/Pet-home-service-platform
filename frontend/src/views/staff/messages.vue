<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Refresh } from '@element-plus/icons-vue'
import PageCard from '@/components/common/PageCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import { usePage } from '@/composables/usePage'
import { useAppStore } from '@/stores/app'
import { fetchNotices, fetchUnreadCount, markNoticeRead, markAllRead } from '@/api/notices'
import { fetchConversations, type ConversationItem } from '@/api/staff/workbench'
import { fmtTime } from '@/utils/constants'
import type { Notice } from '@/types'

/** 消息中心（PRD 6.5 / 3.3）：站内通知（未读数 + 全部已读）+ 客户会话入口 */
const router = useRouter()
const app = useAppStore()

/** el-tabs 的 modelValue 类型为 string | number，显式声明避免类型回写报错 */
const tab = ref<string | number>('notice')
const unread = ref(0)

interface NoticeQuery {
  page?: number
  size?: number
}

const { list, total, page, size, loading, error, refresh, changePage, changeSize } = usePage<
  Notice,
  NoticeQuery
>((q) => fetchNotices(q.page ?? 1, q.size ?? 10), { size: 10 })

/* ---------- 会话列表 ---------- */
const convs = ref<ConversationItem[]>([])
const convLoading = ref(false)
const convError = ref('')

async function loadConversations(): Promise<void> {
  convLoading.value = true
  convError.value = ''
  try {
    convs.value = (await fetchConversations()) ?? []
  } catch (e) {
    convError.value = (e as Error)?.message || '会话加载失败'
    convs.value = []
  } finally {
    convLoading.value = false
  }
}

async function loadUnread(): Promise<void> {
  try {
    const res = await fetchUnreadCount()
    unread.value = Number(res?.count ?? 0)
    app.setUnread(unread.value)
  } catch {
    /* 未读数轮询失败静默 */
  }
}

/* ---------- 通知操作 ---------- */
async function onNoticeClick(n: Notice): Promise<void> {
  if (!n.isRead) {
    try {
      await markNoticeRead(n.id)
      n.isRead = 1
      unread.value = Math.max(0, unread.value - 1)
      app.setUnread(unread.value)
    } catch {
      /* 拦截器已提示 */
    }
  }
  if (n.type === 'ORDER' && n.bizId) router.push(`/staff/orders/${n.bizId}`)
  if (n.type === 'REVIEW') router.push('/staff/reviews')
  if (n.type === 'CERT') router.push('/staff/profile')
}

async function onReadAll(): Promise<void> {
  try {
    await markAllRead()
    unread.value = 0
    app.setUnread(0)
    ElMessage.success('全部通知已标记为已读')
    await refresh()
  } catch {
    /* 拦截器已提示 */
  }
}

const TYPE_LABEL: Record<string, string> = {
  ORDER: '订单',
  REVIEW: '评价',
  CERT: '认证',
  SYSTEM: '系统',
}

async function reloadAll(): Promise<void> {
  await Promise.all([refresh(), loadConversations(), loadUnread()])
}

onMounted(() => {
  void loadUnread()
  void loadConversations()
})
</script>

<template>
  <div class="msg">
    <PageCard title="消息中心">
      <template #header>
        <el-button size="small" :icon="Refresh" :loading="loading || convLoading" @click="reloadAll">
          刷新
        </el-button>
      </template>

      <el-tabs v-model="tab">
        <!-- 站内通知 -->
        <el-tab-pane name="notice">
          <template #label>
            <span class="msg__tab-label">
              通知
              <el-badge v-if="unread > 0" :value="unread" :max="99" class="msg__badge" />
            </span>
          </template>

          <div class="msg__toolbar">
            <span class="aux-text">共 {{ total }} 条通知{{ unread ? `，${unread} 条未读` : '' }}</span>
            <el-button size="small" type="primary" plain :disabled="!unread" @click="onReadAll">
              全部已读
            </el-button>
          </div>

          <div v-loading="loading" class="msg__body">
            <ErrorState v-if="error" :text="error" @retry="refresh" />

            <EmptyState v-else-if="!list.length" text="暂无通知" />

            <div v-else class="msg__list">
              <div
                v-for="n in list"
                :key="n.id"
                class="notice-row"
                :class="{ 'is-unread': !n.isRead }"
                role="button"
                tabindex="0"
                @click="onNoticeClick(n)"
                @keydown.enter="onNoticeClick(n)"
              >
                <span class="notice-row__dot" :class="{ 'is-hidden': !!n.isRead }" />
                <div class="notice-row__main">
                  <div class="notice-row__title">
                    <span class="notice-row__text">{{ n.title }}</span>
                    <span v-if="n.type" class="biz-tag">{{ TYPE_LABEL[n.type] ?? n.type }}</span>
                  </div>
                  <p class="aux-text notice-row__content">{{ n.content || '查看详情' }}</p>
                  <span class="aux-text">{{ fmtTime(n.createdAt) }}</span>
                </div>
                <el-button link type="primary" size="small" @click.stop="onNoticeClick(n)">
                  {{ n.type === 'ORDER' && n.bizId ? '查看订单' : '查看' }}
                </el-button>
              </div>
            </div>
          </div>

          <div v-if="total > 0" class="msg__pager">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next"
              :total="total"
              :current-page="page"
              :page-size="size"
              :page-sizes="[10, 20, 50]"
              @current-change="changePage"
              @size-change="changeSize"
            />
          </div>
        </el-tab-pane>

        <!-- 客户会话 -->
        <el-tab-pane name="chat">
          <template #label>
            <span class="msg__tab-label">
              <el-icon><ChatDotRound /></el-icon>
              会话
            </span>
          </template>

          <div v-loading="convLoading" class="msg__body">
            <ErrorState v-if="convError" :text="convError" @retry="loadConversations" />

            <EmptyState v-else-if="!convs.length" text="暂无客户会话">
              <template #tip>客户通过订单详情发起咨询后会出现在这里</template>
            </EmptyState>

            <div v-else class="msg__list">
              <div
                v-for="c in convs"
                :key="c.conversationId"
                class="conv-row"
                role="button"
                tabindex="0"
                @click="router.push(`/staff/chat/${c.peerId}`)"
                @keydown.enter="router.push(`/staff/chat/${c.peerId}`)"
              >
                <el-avatar :size="44" class="conv-row__avatar">
                  <img v-if="c.peerAvatar" :src="c.peerAvatar" alt="" />
                  <span v-else>{{ (c.peerNickname || '客')[0] }}</span>
                </el-avatar>
                <div class="conv-row__main">
                  <div class="conv-row__title">
                    <span class="conv-row__name">{{ c.peerNickname || '客户' }}</span>
                    <span v-if="c.online" class="conv-row__online">在线</span>
                    <span class="aux-text conv-row__time">{{ fmtTime(c.lastMessageAt) }}</span>
                  </div>
                  <div class="conv-row__last aux-text">{{ c.lastMessage || '开始你们的对话吧' }}</div>
                </div>
                <el-badge v-if="c.unreadCount > 0" :value="c.unreadCount" :max="99" class="conv-row__badge" />
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </PageCard>
  </div>
</template>

<style scoped>
.msg__tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.msg__badge {
  margin-top: -6px;
  margin-left: 2px;
}

.msg__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.msg__body {
  min-height: 220px;
}

.msg__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.msg__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.notice-row,
.conv-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: var(--radius-btn, 12px);
  background: var(--color-bg-page);
  cursor: pointer;
  transition: box-shadow 0.15s ease;
}

.notice-row:hover,
.conv-row:hover {
  box-shadow: var(--shadow-card);
}

.notice-row__dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-unread, #ff4d4f);
}

.notice-row__dot.is-hidden {
  background: transparent;
}

.notice-row__main {
  flex: 1 1 auto;
  min-width: 0;
}

.notice-row__title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.notice-row__text {
  font-size: var(--font-size-body);
  color: var(--color-text-primary);
}

.notice-row.is-unread .notice-row__text {
  font-weight: 600;
}

.notice-row__content {
  margin: 4px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-row__avatar {
  flex-shrink: 0;
  background: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
}

.conv-row__main {
  flex: 1 1 auto;
  min-width: 0;
}

.conv-row__title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.conv-row__name {
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
}

.conv-row__online {
  font-size: 12px;
  color: var(--color-success);
}

.conv-row__time {
  margin-left: auto;
}

.conv-row__last {
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .msg__pager {
    justify-content: center;
  }
}
</style>
