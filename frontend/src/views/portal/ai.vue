<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ChatDotRound, Promotion, Refresh, VideoPause } from '@element-plus/icons-vue'
import PageCard from '@/components/common/PageCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import ConfirmAction from '@/components/common/ConfirmAction.vue'
import { useAuthStore } from '@/stores/auth'
import { fmtTime } from '@/utils/constants'
import { renderMarkdown } from '@/utils/markdown'
import {
  createAiConversation,
  fetchAiConversations,
  fetchAiMessages,
  fetchQuickQuestions,
  streamAiChat,
  type AiChatItem,
  type AiQuickQuestion,
} from '@/api/ai'
import type { AiConversation } from '@/types'

/**
 * AI 宠物顾问（PRD 5.8 / 8.5）：
 * - 会话：GET/POST /api/ai/conversations，历史消息 GET /api/ai/conversations/{id}/messages
 * - 问答：POST /api/ai/chat 走 SSE 流式（message 增量 / done 完成 / error 失败）
 * - 首次提问前先显式建会话，否则拿不到 conversationId，无法回看历史
 * - 失败不落库（后端保证），前端渲染错误气泡并支持重试；可手动停止生成
 */
const auth = useAuthStore()

let keySeed = 0
function nextKey(): number {
  keySeed += 1
  return keySeed
}

/* ---------- 会话列表 ---------- */
const conversations = ref<AiConversation[]>([])
const convId = ref<number | null>(null)

const bootLoading = ref(true)
const bootError = ref('')

const activeConv = computed(() => conversations.value.find((c) => c.id === convId.value) ?? null)

/* ---------- 快捷问题 ---------- */
const quickQuestions = ref<AiQuickQuestion[]>([])

/* ---------- 消息 ---------- */
const items = ref<AiChatItem[]>([])
const historyLoading = ref(false)
const historyError = ref('')

const draft = ref('')
const streaming = ref(false)
const lastQuestion = ref('')
/** 流式增量文本（响应式，直接渲染为临时气泡） */
const streamTextShow = ref('')
const listRef = ref<HTMLElement | null>(null)
const nearBottom = ref(true)

let streamError = ''
let doneReceived = false
let stopRequested = false
let aborter: AbortController | null = null

/* ---------- 渲染辅助 ---------- */
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

function onStreamScroll(): void {
  if (nearBottom.value) scrollToBottom()
}

const canSend = computed(() => draft.value.trim().length > 0 && !streaming.value)

/* ---------- 会话加载 ---------- */
async function boot(): Promise<void> {
  bootLoading.value = true
  bootError.value = ''
  try {
    const [convs, quick] = await Promise.all([fetchAiConversations(), fetchQuickQuestions()])
    conversations.value = convs ?? []
    quickQuestions.value = quick ?? []
  } catch (e) {
    conversations.value = []
    quickQuestions.value = []
    bootError.value = (e as Error)?.message || 'AI 顾问初始化失败'
  } finally {
    bootLoading.value = false
  }

  // 打开最近一次会话，回看历史气泡
  const first = conversations.value[0]
  if (!bootError.value && first) {
    await openConversation(first, true)
  }
}

async function openConversation(conv: AiConversation, showLoading = true): Promise<void> {
  if (streaming.value) {
    return
  }
  if (convId.value === conv.id && !showLoading) return
  convId.value = conv.id
  if (showLoading) {
    historyLoading.value = true
    historyError.value = ''
    items.value = []
  }
  try {
    const msgs = await fetchAiMessages(conv.id)
    items.value = (msgs ?? [])
      .filter((m) => m.role !== 'system')
      .map((m) => ({
        key: nextKey(),
        role: m.role === 'assistant' ? ('assistant' as const) : ('user' as const),
        content: m.content,
        createdAt: m.createdAt,
      }))
    scrollToBottom()
  } catch (e) {
    items.value = []
    historyError.value = (e as Error)?.message || '历史消息加载失败'
  } finally {
    if (showLoading) historyLoading.value = false
  }
}

/** 新会话（本地即可，首次提问时才落库） */
function startNew(): void {
  if (streaming.value) return
  convId.value = null
  items.value = []
  historyError.value = ''
  lastQuestion.value = ''
  scrollToBottom()
}

function newConversation(): void {
  startNew()
}

/* ---------- 提问 ---------- */
function pushErrorItem(content: string): void {
  items.value.push({ key: nextKey(), role: 'assistant', content, error: true })
}

async function ask(question: string, pushUser = true): Promise<void> {
  if (streaming.value || !question.trim()) return
  lastQuestion.value = question

  if (pushUser) {
    items.value.push({ key: nextKey(), role: 'user', content: question })
    nearBottom.value = true
    scrollToBottom()
  }

  // 先建会话，保证拿得到 conversationId 回看历史
  if (convId.value === null) {
    try {
      const conv = await createAiConversation()
      if (!conv?.id) throw new Error('会话创建失败')
      convId.value = conv.id
      conversations.value.unshift(conv)
    } catch (e) {
      pushErrorItem((e as Error)?.message || '会话创建失败，请稍后重试')
      return
    }
  }

  streamTextShow.value = ''
  streamError = ''
  doneReceived = false
  stopRequested = false
  streaming.value = true
  const controller = new AbortController()
  aborter = controller
  scrollToBottom()

  await streamAiChat(
    { conversationId: convId.value ?? undefined, question },
    {
      onDelta: (delta) => {
        streamTextShow.value += delta
        onStreamScroll()
      },
      onDone: (content) => {
        doneReceived = true
        if (content) streamTextShow.value = content
      },
      onError: (message) => {
        if (!stopRequested) streamError = message
      },
    },
    controller.signal,
  )

  streaming.value = false
  aborter = null

  const content = streamTextShow.value
  streamTextShow.value = ''

  if (stopRequested) {
    // 用户主动停止：后端仍会完整落库，本地只展示已生成片段
    if (content) items.value.push({ key: nextKey(), role: 'assistant', content })
  } else if (streamError) {
    pushErrorItem(content ? `${content}\n\n（回答中断：${streamError}）` : streamError)
  } else if (!doneReceived) {
    // 后端业务错误返回 HTTP 200 + JSON，SSE 解析不到事件时兜底
    pushErrorItem('AI 服务暂时不可用，请稍后重试')
  } else if (content) {
    items.value.push({ key: nextKey(), role: 'assistant', content })
    const conv = activeConv.value
    if (conv && (!conv.title || conv.title === '新会话')) {
      conv.title = question.length > 30 ? `${question.slice(0, 30)}…` : question
    }
  }
  nearBottom.value = true
  scrollToBottom()
}

function onSend(): void {
  const q = draft.value.trim()
  if (!q || streaming.value) return
  draft.value = ''
  void ask(q)
}

function useQuick(q: string): void {
  draft.value = q
  onSend()
}

function retry(): void {
  const last = items.value[items.value.length - 1]
  if (last?.error) items.value.pop()
  if (lastQuestion.value) void ask(lastQuestion.value, false)
}

/** 历史消息加载失败后的重试（会话还在就重取，否则重新初始化） */
function retryHistory(): void {
  const conv = activeConv.value
  if (conv) void openConversation(conv, true)
  else void boot()
}

function stopGeneration(): void {
  if (!streaming.value) return
  stopRequested = true
  aborter?.abort()
}

function onKeydown(e: KeyboardEvent): void {
  if (e.key !== 'Enter') return
  if (e.isComposing || e.shiftKey) return
  e.preventDefault()
  onSend()
}

async function refreshAll(): Promise<void> {
  if (streaming.value) return
  await boot()
}

onMounted(() => void boot())

onBeforeUnmount(() => {
  stopRequested = true
  aborter?.abort()
})
</script>

<template>
  <div class="ai">
    <ErrorState v-if="bootError && !bootLoading" :text="bootError" @retry="refreshAll" />

    <el-row v-else :gutter="16">
      <!-- 历史会话 -->
      <el-col :xs="24" :lg="7">
        <PageCard title="历史会话" class="ai__card" :loading="bootLoading">
          <template #header>
            <el-button size="small" type="primary" plain :disabled="streaming" @click="newConversation">
              新建会话
            </el-button>
          </template>

          <div class="ai__convs" v-loading="bootLoading">
            <EmptyState v-if="!conversations.length" text="还没有会话" action-text="开始提问" @action="startNew" />

            <button
              v-for="c in conversations"
              :key="c.id"
              type="button"
              class="ai-conv"
              :class="{ 'is-active': c.id === convId }"
              :disabled="streaming"
              @click="openConversation(c)"
            >
              <span class="ai-conv__title">{{ c.title || '新会话' }}</span>
              <span class="ai-conv__time aux-text">{{ fmtTime(c.updatedAt || c.createdAt) }}</span>
            </button>
          </div>
        </PageCard>
      </el-col>

      <!-- 对话 -->
      <el-col :xs="24" :lg="17">
        <PageCard class="ai__card ai__chat" :loading="bootLoading">
          <template #header>
            <div class="ai__head">
              <div class="ai__head-title">
                <el-icon :size="18"><ChatDotRound /></el-icon>
                <span>AI 宠物顾问</span>
                <span class="biz-tag">护理 · 健康 · 饮食 · 训练</span>
              </div>
              <div class="ai__head-actions">
                <el-button size="small" :icon="Refresh" :disabled="streaming" :loading="bootLoading" @click="refreshAll">
                  刷新
                </el-button>
                <ConfirmAction
                  title="确认清空当前会话？"
                  message="将开启一个新会话，历史会话仍可在左侧回看。"
                  confirm-text="清空"
                  @confirm="startNew"
                >
                  <el-button size="small" plain :disabled="streaming || (!convId && !items.length)">清空会话</el-button>
                </ConfirmAction>
              </div>
            </div>
          </template>

          <!-- 消息区 -->
          <div
            ref="listRef"
            class="ai__body"
            :class="{ 'is-empty': !items.length }"
            v-loading="historyLoading"
            @scroll="onListScroll"
          >
            <ErrorState v-if="historyError && !historyLoading" :text="historyError" @retry="retryHistory" />

            <div v-else-if="!items.length && !historyLoading" class="ai__welcome">
              <div class="ai__welcome-icon">🤖</div>
              <h3 class="ai__welcome-title">你好，我是宠物护理顾问</h3>
              <p class="ai__welcome-desc aux-text">
                可以问我宠物护理、健康、饮食、训练与品种特性的任何问题；涉及疾病用药会建议你线下就医。
              </p>

              <div v-if="quickQuestions.length" class="ai__quick">
                <button
                  v-for="q in quickQuestions"
                  :key="q.question"
                  type="button"
                  class="ai__quick-btn"
                  :disabled="streaming"
                  @click="useQuick(q.question)"
                >
                  {{ q.title }}
                </button>
              </div>

              <EmptyState v-if="quickQuestions.length === 0 && !bootLoading" text="输入下方输入框，开始你的第一个问题" />
            </div>

            <template v-else>
              <div
                v-for="m in items"
                :key="m.key"
                class="ai__row"
                :class="m.role === 'user' ? 'is-mine' : 'is-theirs'"
              >
                <el-avatar :size="32" class="ai__avatar">
                  <span>{{ m.role === 'user' ? (auth.displayName || '我')[0] : 'AI' }}</span>
                </el-avatar>

                <div class="ai__bubble-wrap">
                  <div class="ai__bubble" :class="{ 'is-error': m.error }">
                    <div
                      v-if="m.role === 'assistant' && !m.error"
                      class="ai__text ai__md"
                      v-html="renderMarkdown(m.content)"
                    />
                    <span v-else class="ai__text">{{ m.content }}</span>
                    <button v-if="m.error" type="button" class="ai__retry" @click="retry">重试</button>
                  </div>
                  <span v-if="m.createdAt" class="ai__time aux-text">{{ fmtTime(m.createdAt) }}</span>
                </div>
              </div>

              <!-- 流式临时气泡 -->
              <div v-if="streaming" class="ai__row is-theirs">
                <el-avatar :size="32" class="ai__avatar"><span>AI</span></el-avatar>
                <div class="ai__bubble-wrap">
                  <div class="ai__bubble">
                    <div v-if="streamTextShow" class="ai__text ai__md" v-html="renderMarkdown(streamTextShow)" />
                    <span v-else class="ai__typing"><i /><i /><i /></span>
                  </div>
                </div>
              </div>
            </template>
          </div>

          <!-- 输入区 -->
          <div class="ai__input">
            <el-input
              v-model="draft"
              type="textarea"
              :rows="3"
              maxlength="2000"
              show-word-limit
              resize="none"
              placeholder="描述你的问题，Enter 发送，Shift + Enter 换行"
              :disabled="streaming"
              @keydown="onKeydown"
            />
            <el-button v-if="streaming" class="ai__send" plain @click="stopGeneration">
              <el-icon><VideoPause /></el-icon>
              &nbsp;停止
            </el-button>
            <el-button v-else class="ai__send" type="primary" :disabled="!canSend" @click="onSend">
              <el-icon><Promotion /></el-icon>
              &nbsp;发送
            </el-button>
          </div>
          <p class="ai__hint aux-text">
            AI 回答仅供养护参考，涉及疾病、用药与剂量请及时咨询线下宠物医院。
          </p>
        </PageCard>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.ai__card {
  margin-bottom: 16px;
}

.ai__convs {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 160px;
  max-height: 60vh;
  overflow-y: auto;
}

.ai-conv {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
  padding: 10px 12px;
  border: none;
  border-radius: var(--radius-btn);
  background: var(--color-bg-page);
  text-align: left;
  cursor: pointer;
  transition: background 0.15s ease;
}

.ai-conv:hover:not(:disabled) {
  background: var(--el-color-primary-light-9);
}

.ai-conv.is-active {
  background: var(--el-color-primary-light-9);
  box-shadow: inset 3px 0 0 var(--el-color-primary);
}

.ai-conv:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.ai-conv__title {
  font-size: var(--font-size-body);
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-conv__time {
  line-height: 1.4;
}

.ai__chat {
  display: flex;
  flex-direction: column;
}

.ai__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  width: 100%;
}

.ai__head-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--font-size-module);
  font-weight: 600;
  color: var(--color-text-primary);
}

.ai__head-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.ai__head-actions .el-button + .el-button {
  margin-left: 0;
}

.ai__body {
  height: min(60vh, 540px);
  overflow-y: auto;
  padding: 12px;
  background: var(--color-bg-page);
  border-radius: var(--radius-btn);
}

.ai__body.is-empty {
  display: flex;
  align-items: center;
  justify-content: center;
}

.ai__welcome {
  width: 100%;
  text-align: center;
}

.ai__welcome-icon {
  font-size: 40px;
}

.ai__welcome-title {
  margin: 8px 0 4px;
  font-size: var(--font-size-module);
  color: var(--color-text-primary);
}

.ai__welcome-desc {
  margin: 0 auto;
  max-width: 440px;
  line-height: 1.7;
}

.ai__quick {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin-top: 16px;
}

.ai__quick-btn {
  padding: 7px 14px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-card);
  color: var(--color-text-secondary);
  font-size: var(--font-size-aux);
  cursor: pointer;
  transition: all 0.15s ease;
}

.ai__quick-btn:hover:not(:disabled) {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.ai__quick-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.ai__row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 12px;
}

.ai__row.is-mine {
  flex-direction: row-reverse;
}

.ai__avatar {
  flex-shrink: 0;
  background: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
  font-size: 13px;
}

.ai__row.is-mine .ai__avatar {
  background: var(--el-color-primary);
  color: #fff;
}

.ai__bubble-wrap {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: 78%;
}

.ai__row.is-mine .ai__bubble-wrap {
  align-items: flex-end;
}

.ai__bubble {
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--color-bg-card);
  box-shadow: var(--shadow-card);
  word-break: break-word;
}

.ai__row.is-mine .ai__bubble {
  background: var(--el-color-primary);
}

.ai__bubble.is-error {
  background: var(--color-danger-light-9);
  box-shadow: inset 0 0 0 1px var(--color-danger-light-3);
}

.ai__text {
  font-size: var(--font-size-body);
  line-height: 1.7;
  color: var(--color-text-primary);
  white-space: pre-wrap;
}

.ai__row.is-mine .ai__text {
  color: #fff;
}

/* ---------- Markdown 渲染 ---------- */
/* 样式是 scoped 的，v-html 内容没有 data-v-* 属性，子元素必须用 :deep 才能命中 */
.ai__md {
  white-space: normal; /* 覆盖 .ai__text 的 pre-wrap，避免 HTML 之间的换行被当成空行 */
  margin: 0;
}

.ai__md :deep(p) {
  margin: 0 0 8px;
}

.ai__md :deep(h1),
.ai__md :deep(h2),
.ai__md :deep(h3),
.ai__md :deep(h4),
.ai__md :deep(h5),
.ai__md :deep(h6) {
  margin: 12px 0 6px;
  line-height: 1.4;
  font-weight: 600;
}

.ai__md :deep(h1) { font-size: 1.16em; }
.ai__md :deep(h2) { font-size: 1.1em; }
.ai__md :deep(h3) { font-size: 1.05em; }
.ai__md :deep(h4),
.ai__md :deep(h5),
.ai__md :deep(h6) { font-size: 1em; }

.ai__md :deep(ul),
.ai__md :deep(ol) {
  margin: 0 0 8px;
  padding-left: 1.35em;
}

.ai__md :deep(li) {
  margin: 3px 0;
}

.ai__md :deep(li)::marker {
  color: var(--el-color-primary);
}

.ai__md :deep(strong) {
  font-weight: 600;
}

.ai__md :deep(a) {
  color: var(--el-color-primary);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.ai__md :deep(code) {
  padding: 1px 5px;
  border-radius: var(--radius-tag);
  background: var(--color-bg-page);
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 0.92em;
}

.ai__md :deep(pre) {
  margin: 0 0 8px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--color-bg-page);
  overflow-x: auto;
}

.ai__md :deep(pre code) {
  padding: 0;
  background: none;
}

.ai__md :deep(blockquote) {
  margin: 0 0 8px;
  padding: 6px 10px;
  border-left: 3px solid var(--el-color-primary);
  background: var(--color-bg-page);
  color: var(--color-text-secondary);
}

.ai__md :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 0 0 8px;
  font-size: 0.95em;
}

.ai__md :deep(th),
.ai__md :deep(td) {
  border: 1px solid var(--color-border);
  padding: 4px 8px;
  text-align: left;
}

.ai__md :deep(th) {
  background: var(--color-bg-page);
  font-weight: 600;
}

.ai__md :deep(hr) {
  border: none;
  border-top: 1px solid var(--color-border);
  margin: 10px 0;
}

.ai__md :deep(img) {
  max-width: 100%;
  border-radius: 8px;
}

.ai__retry {
  display: block;
  margin-top: 8px;
  padding: 3px 12px;
  border: 1px solid var(--color-danger);
  border-radius: var(--radius-tag);
  background: transparent;
  color: var(--color-danger);
  font-size: var(--font-size-aux);
  cursor: pointer;
}

.ai__retry:hover {
  background: var(--color-danger);
  color: #fff;
}

.ai__time {
  padding: 0 4px;
}

.ai__typing {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 22px;
}

.ai__typing i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-text-muted);
  animation: ai-blink 1.2s infinite ease-in-out;
}

.ai__typing i:nth-child(2) {
  animation-delay: 0.2s;
}

.ai__typing i:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes ai-blink {
  0%,
  80%,
  100% {
    opacity: 0.3;
    transform: translateY(0);
  }
  40% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

.ai__input {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  margin-top: 12px;
}

.ai__input .el-textarea {
  flex: 1 1 auto;
}

.ai__send {
  height: 44px;
  padding: 0 22px;
  flex: 0 0 auto;
}

.ai__hint {
  margin: 8px 0 0;
  line-height: 1.6;
}

@media (max-width: 768px) {
  .ai__convs {
    max-height: 200px;
  }

  .ai__bubble-wrap {
    max-width: 86%;
  }

  .ai__input {
    flex-direction: column;
    align-items: stretch;
  }

  .ai__send {
    width: 100%;
  }
}
</style>
