<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import ErrorState from '@/components/common/ErrorState.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import {
  fetchChatMessages,
  fetchConversations,
  fetchPeer,
  sendChatMessage,
  type ChatPeerVO,
} from '@/api/chat'
import { createChatSocket } from '@/utils/request'
import { useAuthStore } from '@/stores/auth'
import { fmtTime, ROLE_LABEL } from '@/utils/constants'
import type { ChatMessage } from '@/types'

/**
 * 在线聊天（PRD 5.7）：路由 /chat/:peerId
 * 历史消息分页加载（后端按 id 升序返回，默认展示最后一页）+ 气泡 UI + 自动滚动到底
 * 实时收发走 WebSocket /ws/chat?token=（createChatSocket）；断线指数退避重连
 * 会话由首次发送自动创建（后端 getOrCreateConversation），拉取会话列表获得 convId
 */
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const PAGE_SIZE = 20
const MAX_RECONNECT = 5

const peerId = computed(() => Number(route.params.peerId))
const peer = ref<ChatPeerVO | null>(null)
const convId = ref<number | null>(null)

/** 对端角色文案（用户 / 服务人员） */
const peerRoleLabel = computed(() => {
  const r = peer.value?.role
  return r ? ROLE_LABEL[r] || r : ''
})

const messages = ref<ChatMessage[]>([])
const total = ref(0)
const page = ref(1)

const loading = ref(true)
const error = ref('')
const loadingMore = ref(false)
const draft = ref('')
const sending = ref(false)

const bodyRef = ref<HTMLDivElement>()

/* ---------- WebSocket ---------- */
type WsStatus = 'connecting' | 'on' | 'off'
const wsStatus = ref<WsStatus>('connecting')

let ws: WebSocket | null = null
let disposed = false
let reconnectTimer: number | undefined
let attempts = 0

function teardownWs() {
  if (reconnectTimer) {
    window.clearTimeout(reconnectTimer)
    reconnectTimer = undefined
  }
  if (ws) {
    ws.onclose = null
    ws.close()
    ws = null
  }
}

function scheduleReconnect() {
  if (disposed || reconnectTimer) return
  if (attempts >= MAX_RECONNECT) {
    wsStatus.value = 'off'
    return
  }
  const delay = Math.min(1000 * 2 ** attempts, 15000)
  attempts += 1
  reconnectTimer = window.setTimeout(() => {
    reconnectTimer = undefined
    connectWs()
  }, delay)
}

function connectWs() {
  if (disposed || !peerId.value) return
  teardownWs()
  wsStatus.value = 'connecting'
  try {
    ws = createChatSocket(
      (data) => {
        // 推送协议：{ type: "chat", data: ChatMessage }
        const payload = data as { type?: string; data?: ChatMessage } | null
        if (payload?.type === 'chat' && payload.data) appendMessage(payload.data)
      },
      () => {
        ws = null
        if (disposed) return
        wsStatus.value = 'off'
        scheduleReconnect()
      },
    )
    ws.onopen = () => {
      wsStatus.value = 'on'
      attempts = 0
    }
  } catch {
    wsStatus.value = 'off'
    scheduleReconnect()
  }
}

/* ---------- 数据加载 ---------- */
async function load() {
  if (!peerId.value || peerId.value <= 0) {
    loading.value = false
    error.value = '联系人参数无效，请从消息中心重新进入'
    return
  }
  loading.value = true
  error.value = ''
  try {
    // 联系人信息 + 会话列表（会话列表用于拿 convId；失败不阻断聊天）
    const [p, convs] = await Promise.all([
      fetchPeer(peerId.value),
      fetchConversations().catch(() => []),
    ])
    peer.value = p
    const conv = (convs ?? []).find((c) => c.peerId === peerId.value)
    convId.value = conv?.conversationId ?? null
    messages.value = []
    total.value = 0
    page.value = 1
    if (convId.value) await loadLatest()
  } catch (e) {
    error.value = (e as Error)?.message || '聊天信息加载失败'
  } finally {
    loading.value = false
  }
}

/** 默认展示最后一页（后端历史消息按 id 升序分页，拉取即把发给我的消息置为已读） */
async function loadLatest() {
  if (!convId.value) return
  const first = await fetchChatMessages(convId.value, 1, PAGE_SIZE)
  total.value = first?.total ?? 0
  const lastPage = Math.max(1, Math.ceil(total.value / PAGE_SIZE))
  page.value = lastPage
  const res = lastPage === 1 ? first : await fetchChatMessages(convId.value, lastPage, PAGE_SIZE)
  messages.value = res?.list ?? []
  await scrollToBottom()
}

/** 加载更早一页并前置，保持当前滚动位置 */
async function loadMore() {
  if (loadingMore.value || page.value <= 1 || !convId.value) return
  loadingMore.value = true
  try {
    const el = bodyRef.value
    const prevHeight = el?.scrollHeight ?? 0
    const res = await fetchChatMessages(convId.value, page.value - 1, PAGE_SIZE)
    page.value -= 1
    const older = (res?.list ?? []).filter((m) => !messages.value.some((x) => x.id === m.id))
    messages.value = [...older, ...messages.value]
    await nextTick()
    if (el) el.scrollTop = el.scrollHeight - prevHeight
  } catch {
    /* 拦截器已提示 */
  } finally {
    loadingMore.value = false
  }
}

/* ---------- 收发 ---------- */
function isMine(m: ChatMessage): boolean {
  const myId = auth.user?.id
  return myId ? m.senderId === myId : m.senderId !== peerId.value
}

/** 追加消息：仅接受属于当前会话的消息，按 id 去重 */
function appendMessage(m: ChatMessage) {
  if (!m || !m.id) return
  const belongsPeer = m.senderId === peerId.value || m.receiverId === peerId.value
  const belongsConv = !convId.value || !m.conversationId || m.conversationId === convId.value
  if (!belongsPeer || !belongsConv) return
  if (messages.value.some((x) => x.id === m.id)) return
  messages.value = [...messages.value, m]
  void scrollToBottom()
}

async function onSend() {
  const content = draft.value.trim()
  if (!content || sending.value) return

  sending.value = true
  try {
    const saved = await sendChatMessage({ receiverId: peerId.value, content })
    if (saved) {
      // 首条消息会创建会话：用返回体补齐 convId，保证后续历史可拉取
      if (!convId.value && saved.conversationId) {
        convId.value = saved.conversationId
        total.value = 0
        page.value = 1
      }
      appendMessage(saved)
    }
    draft.value = ''
  } catch {
    /* 拦截器已统一提示（如与管理员不可私聊） */
  } finally {
    sending.value = false
  }
}

async function scrollToBottom() {
  await nextTick()
  const el = bodyRef.value
  if (el) el.scrollTop = el.scrollHeight
}

/* ---------- 生命周期 ---------- */
onMounted(() => {
  void load()
  connectWs()
})

watch(peerId, () => {
  peer.value = null
  convId.value = null
  messages.value = []
  total.value = 0
  page.value = 1
  attempts = 0
  void load()
  connectWs()
})

onBeforeUnmount(() => {
  disposed = true
  teardownWs()
})
</script>

<template>
  <div class="chat">
    <!-- 顶栏：返回 + 对端信息 + 连接状态 -->
    <div class="chat__topbar">
      <el-button link :icon="ArrowLeft" @click="router.back()">返回</el-button>
      <el-avatar :size="36" class="chat__avatar">
        <img v-if="peer?.avatar" :src="peer?.avatar" alt="" />
        <span v-else>{{ (peer?.nickname || '友')[0] }}</span>
      </el-avatar>
      <div class="chat__peer">
        <div class="chat__peer-name">{{ peer?.nickname || '联系人' }}</div>
        <div class="chat__peer-meta aux-text">
          <template v-if="peerRoleLabel">{{ peerRoleLabel }} · </template>
          {{ peer?.online ? '在线' : '离线' }}
        </div>
      </div>
      <span class="chat__spacer" />
      <el-tag v-if="wsStatus === 'on'" type="success" size="small" effect="light" round>实时连接</el-tag>
      <el-tag v-else-if="wsStatus === 'connecting'" type="warning" size="small" effect="light" round>
        连接中…
      </el-tag>
      <el-tag v-else type="info" size="small" effect="plain" round>离线（发送仍会送达）</el-tag>
    </div>

    <div class="chat__panel">
      <ErrorState v-if="error && !loading" :text="error" retry-text="重新加载" @retry="load" />

      <template v-else>
        <div ref="bodyRef" v-loading="loading" class="chat__body">
          <div v-if="page > 1" class="chat__more">
            <el-button link type="primary" size="small" :loading="loadingMore" @click="loadMore">
              加载更早的消息
            </el-button>
          </div>

          <EmptyState v-if="!messages.length && !loading" text="暂无聊天记录，发送第一条消息吧" />

          <div
            v-for="m in messages"
            :key="m.id"
            class="chat__row"
            :class="{ 'is-mine': isMine(m) }"
          >
            <el-avatar :size="32" class="chat__row-avatar">
              <img v-if="!isMine(m) && peer?.avatar" :src="peer?.avatar" alt="" />
              <span v-else>{{ isMine(m) ? (auth.displayName || '我')[0] : (peer?.nickname || '友')[0] }}</span>
            </el-avatar>
            <div class="chat__bubble-wrap">
              <div class="chat__bubble">
                <img v-if="m.msgType === 'IMAGE'" class="chat__img" :src="m.content" alt="聊天图片" />
                <template v-else>{{ m.content }}</template>
              </div>
              <div class="chat__time aux-text">{{ fmtTime(m.createdAt) }}</div>
            </div>
          </div>
        </div>

        <div class="chat__input">
          <el-input
            v-model="draft"
            :maxlength="500"
            show-word-limit
            placeholder="输入消息，回车发送"
            clearable
            @keyup.enter="onSend"
          />
          <el-button type="primary" :loading="sending" :disabled="!draft.trim()" @click="onSend">
            发送
          </el-button>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.chat {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
  min-height: 420px;
}

.chat__topbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: var(--color-bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  margin-bottom: 12px;
}

.chat__avatar {
  flex: 0 0 auto;
  background: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
}

.chat__peer {
  min-width: 0;
}

.chat__peer-name {
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
}

.chat__peer-meta {
  line-height: 1.4;
}

.chat__spacer {
  flex: 1;
}

.chat__panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--color-bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 16px;
}

.chat__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 4px 8px;
}

.chat__more {
  text-align: center;
  padding: 4px 0 8px;
}

.chat__row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 14px;
}

.chat__row.is-mine {
  flex-direction: row-reverse;
}

.chat__row-avatar {
  flex: 0 0 auto;
  background: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
}

.chat__bubble-wrap {
  max-width: 70%;
  min-width: 0;
}

.chat__row.is-mine .chat__bubble-wrap {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.chat__bubble {
  display: inline-block;
  padding: 10px 14px;
  border-radius: var(--radius-btn);
  background: var(--color-bg-page);
  color: var(--color-text-primary);
  font-size: var(--font-size-body);
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}

.chat__row.is-mine .chat__bubble {
  background: var(--el-color-primary);
  color: #fff;
}

.chat__img {
  max-width: 200px;
  border-radius: var(--radius-tag);
  display: block;
}

.chat__time {
  margin-top: 4px;
  padding: 0 4px;
}

.chat__input {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
}

.chat__input .el-input {
  flex: 1;
}

@media (max-width: 640px) {
  .chat {
    height: calc(100vh - 160px);
  }

  .chat__bubble-wrap {
    max-width: 78%;
  }
}
</style>
