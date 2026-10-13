<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ChatDotRound, Refresh } from '@element-plus/icons-vue'
import PageCard from '@/components/common/PageCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import { useAuthStore } from '@/stores/auth'
import { createChatSocket } from '@/utils/request'
import {
  fetchConversations,
  fetchPeerInfo,
  fetchChatMessages,
  sendChatMessage,
  type PeerInfo,
} from '@/api/staff/workbench'
import { fmtTime } from '@/utils/constants'
import type { ChatMessage } from '@/types'

/**
 * 在线聊天（PRD 5.7 / 8.4）：
 * - 历史消息：GET /api/chat/messages?convId=（按 id 升序，默认定位最新一页）
 * - 发送：POST /api/chat/messages（先落库，接收方在线则推送）
 * - 实时接收：WebSocket /ws/chat（{ type:'chat', data: ChatMessage }）
 */
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const PAGE_SIZE = 20

const peerId = computed(() => Number(route.params.peerId))
const myId = computed(() => Number(auth.user?.id ?? 0))

const peer = ref<PeerInfo | null>(null)
const messages = ref<ChatMessage[]>([])
const loading = ref(true)
const error = ref('')
const sending = ref(false)
const loadingMore = ref(false)
const draft = ref('')

const convId = ref<number | null>(null)
const total = ref(0)
const page = ref(1)

/** WebSocket 连接状态 */
const wsAlive = ref(false)
const reconnectTip = ref('')

let socket: WebSocket | null = null
let retries = 0
let disposed = false
let reconnectTimer: number | undefined

const listRef = ref<HTMLElement | null>(null)
const nearBottom = ref(true)

const canLoadOlder = computed(() => page.value > 1)

function lastPageOf(t: number): number {
  return Math.max(1, Math.ceil(t / PAGE_SIZE))
}

function scrollToBottom(): void {
  void nextTick(() => {
    const el = listRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function onListScroll(): void {
  const el = listRef.value
  if (!el) return
  nearBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight < 80
}

function appendMessage(msg: ChatMessage, mine = false): void {
  if (messages.value.some((m) => m.id === msg.id)) return
  messages.value.push(msg)
  total.value += 1
  if (mine || nearBottom.value) scrollToBottom()
}

/* ---------- 加载 ---------- */
async function load(): Promise<void> {
  if (!peerId.value || Number.isNaN(peerId.value)) {
    error.value = '会话参数不合法'
    loading.value = false
    return
  }
  loading.value = true
  error.value = ''
  try {
    const convs = (await fetchConversations()) ?? []
    const mine = convs.find((c) => c.peerId === peerId.value)
    convId.value = mine?.conversationId ?? null
    messages.value = []
    page.value = 1
    total.value = 0

    if (convId.value) {
      const first = await fetchChatMessages(convId.value, 1, PAGE_SIZE)
      total.value = first?.total ?? 0
      page.value = lastPageOf(total.value)
      messages.value =
        page.value === 1
          ? (first?.list ?? [])
          : ((await fetchChatMessages(convId.value, page.value, PAGE_SIZE))?.list ?? [])
    }

    peer.value = await fetchPeerInfo(peerId.value)
    reconnectTip.value = ''
    scrollToBottom()
  } catch (e) {
    error.value = (e as Error)?.message || '聊天记录加载失败'
    messages.value = []
    peer.value = null
  } finally {
    loading.value = false
  }
}

/** 加载更早消息（向上翻页） */
async function loadOlder(): Promise<void> {
  if (!convId.value || loadingMore.value || page.value <= 1) return
  loadingMore.value = true
  try {
    const el = listRef.value
    const prevHeight = el?.scrollHeight ?? 0
    const prevTop = el?.scrollTop ?? 0
    const prev = page.value - 1
    const res = await fetchChatMessages(convId.value, prev, PAGE_SIZE)
    messages.value = [...(res?.list ?? []), ...messages.value]
    page.value = prev
    // 顶部插入历史后保持视口位置不变
    if (el) void nextTick(() => (el.scrollTop = el.scrollHeight - prevHeight + prevTop))
  } catch {
    /* 拦截器已提示 */
  } finally {
    loadingMore.value = false
  }
}

/* ---------- 实时消息 ---------- */
function handlePayload(raw: unknown): void {
  const payload = raw as { type?: string; data?: ChatMessage } | null
  if (!payload || payload.type !== 'chat' || !payload.data) return
  const msg = payload.data
  if (msg.senderId !== peerId.value && msg.receiverId !== peerId.value) return
  if (msg.conversationId && convId.value && msg.conversationId !== convId.value) return
  appendMessage(msg)
}

function connect(): void {
  if (disposed) return
  if (socket) {
    socket.onclose = null
    socket.close()
    socket = null
  }
  socket = createChatSocket(handlePayload, () => {
    wsAlive.value = false
    if (disposed) return
    if (retries < 3) {
      retries += 1
      reconnectTip.value = '实时连接已断开，正在重连…'
      reconnectTimer = window.setTimeout(() => void connect(), 3000)
    } else {
      reconnectTip.value = '实时连接不可用，发送消息仍会正常送达'
    }
  })
  socket.onopen = () => {
    wsAlive.value = true
    retries = 0
    reconnectTip.value = ''
  }
}

/* ---------- 发送 ---------- */
async function send(): Promise<void> {
  const text = draft.value.trim()
  if (!text || sending.value) return
  sending.value = true
  try {
    const msg = await sendChatMessage(peerId.value, text)
    draft.value = ''
    if (msg) {
      if (!convId.value && msg.conversationId) convId.value = msg.conversationId
      appendMessage(msg, true)
      nearBottom.value = true
      scrollToBottom()
    }
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    sending.value = false
  }
}

function onKeydown(e: KeyboardEvent): void {
  if (e.key !== 'Enter') return
  if (e.isComposing || e.shiftKey) return
  e.preventDefault()
  void send()
}

function isMine(m: ChatMessage): boolean {
  return myId.value > 0 && m.senderId === myId.value
}

async function init(): Promise<void> {
  await load()
  if (!error.value) connect()
}

onMounted(() => void init())

onBeforeUnmount(() => {
  disposed = true
  if (reconnectTimer) window.clearTimeout(reconnectTimer)
  if (socket) {
    socket.onclose = null
    socket.close()
    socket = null
  }
})

watch(peerId, () => {
  disposed = false
  void init()
})
</script>

<template>
  <div class="chat">
    <PageCard
      :title="peer?.nickname ? `与 ${peer.nickname} 的会话` : '在线聊天'"
      :loading="loading"
      class="chat__card"
    >
      <template #header>
        <div class="chat__head">
          <span v-if="peer" class="chat__state" :class="{ 'is-online': peer.online }">
            {{ peer.online ? '在线' : '离线' }}
          </span>
          <span class="chat__ws">{{ wsAlive ? '实时连接正常' : '实时连接…' }}</span>
          <el-button size="small" :icon="Refresh" :disabled="loading" @click="init">刷新</el-button>
          <el-button size="small" :icon="ArrowLeft" @click="router.push('/staff/messages')">返回</el-button>
        </div>
      </template>

      <div
        ref="listRef"
        class="chat__body"
        :class="{ 'is-empty': !messages.length }"
        v-loading="loading"
        @scroll="onListScroll"
      >
        <ErrorState v-if="error && !loading" :text="error" @retry="init" />

        <EmptyState v-else-if="!messages.length && !loading" text="还没有消息">
          <template #tip>发送第一条消息，开始与客户的对话</template>
        </EmptyState>

        <template v-else>
          <div v-if="canLoadOlder" class="chat__more">
            <el-button size="small" text :loading="loadingMore" @click="loadOlder">加载更早的消息</el-button>
          </div>

          <div v-if="reconnectTip" class="chat__tip">
            <span class="biz-tag">{{ reconnectTip }}</span>
          </div>

          <div
            v-for="m in messages"
            :key="m.id"
            class="chat__row"
            :class="isMine(m) ? 'is-mine' : 'is-theirs'"
          >
            <el-avatar :size="32" class="chat__avatar">
              <span>{{ isMine(m) ? (auth.displayName || '我')[0] : (peer?.nickname || '客')[0] }}</span>
            </el-avatar>
            <div class="chat__bubble-wrap">
              <div class="chat__bubble" :class="{ 'is-image': m.msgType === 'IMAGE' }">
                <el-image
                  v-if="m.msgType === 'IMAGE'"
                  :src="m.content"
                  :preview-src-list="[m.content]"
                  fit="cover"
                  class="chat__image"
                  preview-teleported
                />
                <span v-else class="chat__text">{{ m.content }}</span>
              </div>
              <span class="chat__time aux-text">{{ fmtTime(m.createdAt) }}</span>
            </div>
          </div>
        </template>
      </div>

      <div class="chat__input">
        <el-input
          v-model="draft"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          resize="none"
          placeholder="输入消息，Enter 发送，Shift + Enter 换行"
          :disabled="!!error"
          @keydown="onKeydown"
        />
        <el-button
          type="primary"
          class="chat__send"
          :loading="sending"
          :disabled="!draft.trim()"
          @click="send"
        >
          <el-icon><ChatDotRound /></el-icon>
          发送
        </el-button>
      </div>
      <p class="aux-text chat__hint">聊天内容仅对订单双方可见，请勿脱离平台私下交易。</p>
    </PageCard>
  </div>
</template>

<style scoped>
.chat__card {
  display: flex;
  flex-direction: column;
}

.chat__head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.chat__state {
  font-size: var(--font-size-aux);
  color: var(--color-text-muted);
}

.chat__state.is-online {
  color: var(--color-success);
}

.chat__ws {
  font-size: var(--font-size-aux);
  color: var(--color-text-muted);
}

.chat__body {
  height: min(60vh, 560px);
  overflow-y: auto;
  padding: 8px 4px;
  background: var(--color-bg-page);
  border-radius: var(--radius-btn, 12px);
}

.chat__body.is-empty {
  display: flex;
  align-items: center;
  justify-content: center;
}

.chat__more,
.chat__tip {
  display: flex;
  justify-content: center;
  margin-bottom: 8px;
}

.chat__row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 12px;
}

.chat__row.is-mine {
  flex-direction: row-reverse;
}

.chat__avatar {
  flex-shrink: 0;
  background: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
  font-size: 14px;
}

.chat__bubble-wrap {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: 72%;
}

.chat__row.is-mine .chat__bubble-wrap {
  align-items: flex-end;
}

.chat__bubble {
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--color-bg-card);
  box-shadow: var(--shadow-card);
  word-break: break-word;
}

.chat__row.is-mine .chat__bubble {
  background: var(--el-color-primary);
}

.chat__text {
  font-size: var(--font-size-body);
  line-height: 1.6;
  color: var(--color-text-primary);
  white-space: pre-wrap;
}

.chat__row.is-mine .chat__text {
  color: #fff;
}

.chat__image {
  width: 160px;
  height: 160px;
  border-radius: 8px;
  overflow: hidden;
}

.chat__time {
  padding: 0 4px;
}

.chat__input {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  margin-top: 12px;
}

.chat__input .el-textarea {
  flex: 1 1 auto;
}

.chat__send {
  height: 44px;
  padding: 0 22px;
}

.chat__hint {
  margin: 8px 0 0;
}

@media (max-width: 768px) {
  .chat__bubble-wrap {
    max-width: 82%;
  }

  .chat__input {
    flex-direction: column;
    align-items: stretch;
  }

  .chat__send {
    width: 100%;
  }
}
</style>
