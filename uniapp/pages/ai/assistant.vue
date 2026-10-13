<template>
	<view class="page ai-page">
		<scroll-view scroll-y class="chat-scroll" :scroll-top="scrollTop" :scroll-with-animation="true">
			<view class="chat-wrap">
				<!-- 空状态：快捷问题 -->
				<view v-if="!messages.length && !streaming" class="empty-state">
					<view class="ai-logo">AI</view>
					<text class="t-module">AI 宠物顾问</text>
					<text class="t-sub empty-desc">聚焦宠物护理、健康、饮食、训练与品种问题</text>

					<view class="quick-title">常见问题</view>
					<view class="quick-list">
						<view class="quick-item" v-for="q in quickQuestions" :key="q.title" @tap="askQuick(q)">
							<text class="quick-text">{{ q.title }}</text>
							<text class="quick-arrow">›</text>
						</view>
						<view class="quick-item" v-for="n in 4" :key="'ph' + n" v-if="quickLoading">
							<view class="loading-line" style="width:60%"></view>
						</view>
					</view>
				</view>

				<!-- 消息列表 -->
				<block v-else>
					<view class="ai-row" :class="m.role === 'user' ? 'mine' : 'theirs'" v-for="m in messages" :key="m.id">
						<view class="ai-bubble" :class="{ assistant: m.role !== 'user' }">
							<text v-if="m.role === 'user'" class="ai-text" user-select>{{ m.content }}</text>
							<rich-text v-else class="ai-md" :nodes="m.html"></rich-text>
						</view>
					</view>
					<!-- 流式输出中（按 Markdown 实时渲染） -->
					<view class="ai-row theirs" v-if="streaming">
						<view class="ai-bubble assistant">
							<rich-text class="ai-md" :nodes="streamHtml"></rich-text>
							<text class="ai-cursor" v-if="!streamDone">▍</text>
						</view>
					</view>
					<!-- 流式失败 -->
					<view class="ai-row theirs" v-if="streamError">
						<view class="ai-bubble error-bubble">
							<text class="ai-text error-text">{{ streamError }}</text>
							<view class="retry-link" @tap="retryAsk">点击重试</view>
						</view>
					</view>
				</block>
				<view id="end" class="scroll-end"></view>
			</view>
		</scroll-view>

		<!-- 输入区 -->
		<view class="input-bar">
			<view class="new-chat-btn" :class="{ disabled: streaming }" @tap="newConversation">
				<image class="new-chat-icon" src="/static/icons/new-chat.png" mode="aspectFit" />
				<text class="new-chat-text">新对话</text>
			</view>
			<input
				class="ai-input"
				v-model="draft"
				:disabled="streaming"
				:placeholder="streaming ? '回答生成中...' : '输入宠物相关问题'"
				placeholder-class="form-input-placeholder"
				confirm-type="send"
				@confirm="ask"
			/>
			<button
				class="btn btn-primary btn-sm send-btn"
				:class="{ 'btn-disabled': !draft.trim() || streaming }"
				:disabled="!draft.trim() || streaming"
				@tap="ask"
			>{{ streaming ? '生成中' : '发送' }}</button>
		</view>
	</view>
</template>

<script>
	import {
		apiQuickQuestions, apiAiConversations, apiCreateAiConversation, apiAiMessages
	} from '../../api/index'
	import { getToken, ensureLogin } from '../../utils/auth'
	import { BASE_URL_CONFIG } from '../../utils/request'
	import { mdToHtml } from '../../utils/markdown'

	export default {
		data() {
			return {
				conversationId: null,
				messages: [],
				draft: '',
				streaming: false,
				streamText: '',
				streamDone: false,
				streamError: '',
				lastQuestion: '',
				quickQuestions: [],
				quickLoading: true,
				scrollTop: 0
			}
		},
		computed: {
			/** 流式内容实时 Markdown 渲染 */
			streamHtml() {
				return mdToHtml(this.streamText)
			}
		},
		onLoad() {
			if (!ensureLogin()) return
			this.loadQuick()
			this.loadHistory()
		},
		methods: {
			async loadQuick() {
				this.quickLoading = true
				try {
					this.quickQuestions = (await apiQuickQuestions()) || []
				} catch (e) {
					this.quickQuestions = []
				} finally {
					this.quickLoading = false
				}
			},
			/** 打开页面时恢复最近会话 */
			async loadHistory() {
				try {
					const list = (await apiAiConversations()) || []
					if (list.length) {
						this.conversationId = list[0].id
						const msgs = (await apiAiMessages(this.conversationId)) || []
						this.messages = msgs
							.filter((m) => m.role !== 'system')
							.map((m) => ({ ...m, html: mdToHtml(m.content || '') }))
						this.scrollToBottom()
					}
				} catch (e) {
					// 无历史不影响新会话
				}
			},
			/** 新建对话：清空当前会话视图，下一次提问时重建会话 */
			newConversation() {
				if (this.streaming) return
				const start = () => {
					this.conversationId = null
					this.messages = []
					this.streamText = ''
					this.streamError = ''
					this.streamDone = false
					this.lastQuestion = ''
					this.scrollTop = 0
					uni.showToast({ title: '已开启新对话', icon: 'none' })
				}
				if (this.messages.length) {
					uni.showModal({
						title: '新建对话',
						content: '当前对话已保留在历史记录中，确定开启新对话吗？',
						confirmText: '新建',
						confirmColor: '#FF8A3D',
						cancelText: '取消',
						success: (r) => {
							if (r.confirm) start()
						}
					})
				} else {
					start()
				}
			},
			askQuick(q) {
				this.draft = q.question
				this.ask()
			},
			scrollToBottom() {
				this.$nextTick(() => {
					this.scrollTop = this.scrollTop + 99999 + Math.random()
				})
			},
			/**
			 * SSE 流式问答：POST /api/ai/chat（text/event-stream）
			 * 事件：message=增量 delta、done=完成（返回完整 content）、error=失败（不写入会话）
			 */
			async ask() {
				const question = this.draft.trim()
				if (!question || this.streaming) return

				// 无会话先建会话
				if (!this.conversationId) {
					try {
						const conv = await apiCreateAiConversation()
						this.conversationId = conv.id
					} catch (e) {
						uni.showToast({ title: '会话创建失败，请重试', icon: 'none' })
						return
					}
				}

				this.messages.push({ id: 'u-' + Date.now(), role: 'user', content: question })
				this.lastQuestion = question
				this.draft = ''
				this.streamText = ''
				this.streamError = ''
				this.streamDone = false
				this.streaming = true
				this.scrollToBottom()

				// #ifdef H5
				this.streamByFetch(question)
				// #endif
				// #ifndef H5
				this.streamByRequest(question)
				// #endif
			},
			/** H5 端：fetch + ReadableStream 解析 SSE */
			async streamByFetch(question) {
				try {
					const res = await fetch(BASE_URL_CONFIG + '/api/ai/chat', {
						method: 'POST',
						headers: {
							'Content-Type': 'application/json',
							Authorization: 'Bearer ' + getToken()
						},
						body: JSON.stringify({ conversationId: this.conversationId, question })
					})
					if (!res.ok || !res.body) throw new Error('network')
					const reader = res.body.getReader()
					const decoder = new TextDecoder('utf-8')
					let buffer = ''
					while (true) {
						const { done, value } = await reader.read()
						if (done) break
						buffer += decoder.decode(value, { stream: true })
						const lines = buffer.split('\n')
						buffer = lines.pop()
						lines.forEach((line) => this.handleSseLine(line))
					}
					this.finishStream()
				} catch (e) {
					this.failStream('网络异常，回答未完成，请重试')
				}
			},
			/** 小程序/App 端：uni.request 分块接收 */
			streamByRequest(question) {
				uni.request({
					url: BASE_URL_CONFIG + '/api/ai/chat',
					method: 'POST',
					header: {
						'Content-Type': 'application/json',
						Authorization: 'Bearer ' + getToken()
					},
					data: { conversationId: this.conversationId, question },
					enableChunked: true,
					timeout: 120000,
					onChunkReceived: (res) => {
						try {
							const text = new TextDecoder('utf-8').decode(res.data)
							text.split('\n').forEach((line) => this.handleSseLine(line))
						} catch (e) { /* 忽略坏帧 */ }
					},
					success: () => this.finishStream(),
					fail: () => this.failStream('网络异常，回答未完成，请重试')
				})
			},
			handleSseLine(line) {
				const t = line.trim()
				if (!t || !t.startsWith('event:') && !t.startsWith('data:')) return
				if (t.startsWith('event:')) {
					this._evt = t.slice(6).trim()
					return
				}
				if (!t.startsWith('data:')) return
				let payload = {}
				try {
					payload = JSON.parse(t.slice(5).trim())
				} catch (e) {
					return
				}
				const evt = this._evt || 'message'
				if (evt === 'message' && payload.delta) {
					this.streamText += payload.delta
					this.scrollToBottom()
				} else if (evt === 'done') {
					if (payload.content) this.streamText = payload.content
					this.finishStream()
				} else if (evt === 'error') {
					this.failStream(payload.message || 'AI 服务暂时不可用，请稍后重试')
				}
				this._evt = ''
			},
			/** 完成：追加 assistant 消息（后端已落库，前端缓存渲染好的 HTML） */
			finishStream() {
				if (!this.streaming || this.streamDone) return
				this.streamDone = true
				this.streaming = false
				if (this.streamText) {
					this.messages.push({
						id: 'a-' + Date.now(),
						role: 'assistant',
						content: this.streamText,
						html: mdToHtml(this.streamText)
					})
				}
				this.streamText = ''
				this.scrollToBottom()
			},
			/** 失败：不污染会话历史（AC-08），仅展示错误与重试入口 */
			failStream(msg) {
				this.streaming = false
				this.streamDone = true
				this.streamText = ''
				this.streamError = msg
				// 失败时撤回乐观追加的用户消息，重试时重新发送
				const last = this.messages[this.messages.length - 1]
				if (last && last.role === 'user' && last.content === this.lastQuestion) {
					this.draft = this.lastQuestion
					this.messages.pop()
				}
			},
			retryAsk() {
				this.streamError = ''
				this.ask()
			}
		}
	}
</script>

<style scoped>
	.ai-page { display: flex; flex-direction: column; height: 100vh; background: var(--c-bg-page); }
	.chat-scroll { flex: 1; height: calc(100vh - 140rpx); }
	.chat-wrap { padding: 32rpx; }

	.empty-state { display: flex; flex-direction: column; align-items: center; padding-top: 64rpx; }
	.ai-logo {
		width: 112rpx; height: 112rpx; line-height: 112rpx; text-align: center;
		background: var(--c-info); color: #FFFFFF;
		font-size: 40rpx; font-weight: 600; border-radius: 32rpx;
	}
	.empty-desc { margin-top: 16rpx; }
	.quick-title { align-self: flex-start; font-size: 26rpx; color: var(--c-text-weak); margin: 56rpx 0 20rpx; }
	.quick-list { width: 100%; }
	.quick-item {
		display: flex; flex-direction: row; align-items: center; justify-content: space-between;
		background: #FFFFFF; border-radius: var(--radius-card);
		padding: 28rpx 32rpx; margin-bottom: 20rpx; box-shadow: var(--shadow-card);
	}
	.quick-item:active { background: var(--c-primary-weak); }
	.quick-text { font-size: 28rpx; color: var(--c-text-title); }
	.quick-arrow { color: var(--c-text-weak); font-size: 32rpx; }

	.ai-row { display: flex; flex-direction: row; margin-bottom: 24rpx; }
	.ai-row.mine { justify-content: flex-end; }
	.ai-bubble {
		max-width: 560rpx; padding: 24rpx 28rpx;
		background: #FFFFFF; border-radius: 8rpx 24rpx 24rpx 24rpx;
		box-shadow: var(--shadow-card);
	}
	.ai-row.mine .ai-bubble {
		background: var(--c-primary); border-radius: 24rpx 8rpx 24rpx 24rpx;
	}
	.ai-bubble.assistant { background: #EEF2FF; }
	.ai-text { font-size: 28rpx; line-height: 1.7; color: var(--c-text-title); word-break: break-all; }
	.ai-row.mine .ai-text { color: #FFFFFF; }
	.ai-md { font-size: 28rpx; line-height: 1.7; color: var(--c-text-title); word-break: break-all; }
	.ai-cursor { color: var(--c-info); font-size: 28rpx; line-height: 1.7; }
	.error-bubble { background: #FFF1F0; }
	.error-text { color: var(--c-error); }
	.retry-link { color: var(--c-info); font-size: 26rpx; margin-top: 16rpx; }
	.scroll-end { height: 16rpx; }

	.input-bar {
		display: flex; flex-direction: row; align-items: center;
		background: #FFFFFF; padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
		border-top: 1px solid var(--c-border);
	}
	.new-chat-btn {
		display: flex; flex-direction: column; align-items: center; justify-content: center;
		width: 92rpx; flex-shrink: 0; margin-right: 12rpx; padding: 8rpx 0;
		background: var(--c-bg-page); border-radius: 16rpx;
	}
	.new-chat-btn:active { background: var(--c-primary-weak); }
	.new-chat-btn.disabled { opacity: 0.5; }
	.new-chat-icon { width: 36rpx; height: 36rpx; }
	.new-chat-text { font-size: 20rpx; color: var(--c-text-secondary); margin-top: 2rpx; }
	.ai-input {
		flex: 1; height: 72rpx; background: var(--c-bg-page);
		border-radius: 36rpx; padding: 0 28rpx; font-size: 28rpx;
	}
	.send-btn { margin-left: 16rpx; flex-shrink: 0; }
</style>
