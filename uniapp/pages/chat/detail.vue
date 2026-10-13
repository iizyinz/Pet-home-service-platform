<template>
	<view class="page chat-page">
		<!-- 消息区 -->
		<scroll-view
			scroll-y
			class="msg-scroll"
			:scroll-top="scrollTop"
			:scroll-with-animation="true"
			@scrolltoupper="loadHistory"
		>
			<view class="msg-wrap">
				<text class="load-hint" v-if="loadingHistory">正在加载历史消息...</text>
				<text class="load-hint" v-else-if="!hasMore && messages.length">—— 以下是更早的对话 ——</text>

				<state-view v-if="historyState === 'error'" state="error" error-text="历史消息加载失败" @retry="loadHistory" />

				<view
					class="msg-row"
					:class="m.senderId === myId ? 'mine' : 'theirs'"
					v-for="m in messages"
					:key="m.id"
				>
					<image class="msg-avatar" :src="m.senderId === myId ? myAvatar : peerAvatar" mode="aspectFill" />
					<view class="msg-bubble">
						<!-- 图片消息 -->
						<image
							v-if="m.msgType === 'IMAGE'"
							class="msg-image"
							:src="m.content"
							mode="widthFix"
							@tap="previewImg(m.content)"
						/>
						<text v-else class="msg-text">{{ m.content }}</text>
					</view>
				</view>

				<!-- 发送中 -->
				<view class="msg-row mine" v-for="p in pending" :key="p.key">
					<image class="msg-avatar" :src="myAvatar" mode="aspectFill" />
					<view class="msg-bubble sending">
						<text class="msg-text">{{ p.content }}</text>
						<text class="sending-flag">{{ p.fail ? '发送失败，点击重试' : '发送中...' }}</text>
					</view>
				</view>

				<view class="scroll-end" id="scrollEnd"></view>
			</view>
		</scroll-view>

		<!-- 连接状态提示 -->
		<view class="conn-tip" v-if="!wsOpen">
			<text class="conn-tip-text">连接已断开，新消息将以发送后刷新方式展示</text>
		</view>

		<!-- 输入区 -->
		<view class="input-bar">
			<view class="input-action" @tap="sendImage">
				<image class="input-action-icon" src="/static/icons/image.png" mode="aspectFit" />
			</view>
			<input
				class="msg-input"
				v-model="draft"
				placeholder="发送消息..."
				placeholder-class="form-input-placeholder"
				confirm-type="send"
				@confirm="send"
			/>
			<button
				class="btn btn-primary btn-sm send-btn"
				:class="{ 'btn-disabled': !draft.trim() || sending }"
				:disabled="!draft.trim() || sending"
				@tap="send"
			>{{ sending ? '发送中' : '发送' }}</button>
		</view>
	</view>
</template>

<script>
	import StateView from '../../components/state-view/state-view.vue'
	import { apiPeer, apiMessages, apiSendMessage, apiUpload, apiConversations } from '../../api/index'
	import { getLoginUser, getToken, ensureLogin } from '../../utils/auth'
	import { BASE_URL_CONFIG } from '../../utils/request'
	import { timeLabel } from '../../utils/format'

	const PLACEHOLDER = '/static/logo.png'

	export default {
		components: { StateView },
		data() {
			return {
				convId: null,
				peerId: null,
				peerName: '',
				peerAvatar: '',
				myId: null,
				myAvatar: PLACEHOLDER,
				messages: [],
				pending: [],
				draft: '',
				sending: false,
				page: 1,
				size: 20,
				hasMore: true,
				loadingHistory: false,
				historyState: 'ready',
				scrollTop: 0,
				wsOpen: false,
				socket: null,
				pendingKey: 0
			}
		},
		onLoad(options) {
			if (!ensureLogin()) return
			const user = getLoginUser() || {}
			this.myId = user.id
			this.myAvatar = user.avatar || PLACEHOLDER
			this.peerId = options && options.peerId ? Number(options.peerId) : null
			this.peerName = options && options.peerName ? decodeURIComponent(options.peerName) : ''
			this.convId = options && options.convId ? Number(options.convId) : null
			if (this.peerName) uni.setNavigationBarTitle({ title: this.peerName })
			this.init()
		},
		onUnload() {
			this.closeSocket()
		},
		methods: {
			timeLabel,
			async init() {
				if (this.peerId) {
					try {
						const p = await apiPeer(this.peerId)
						this.peerAvatar = p.avatar || PLACEHOLDER
						if (p.nickname) uni.setNavigationBarTitle({ title: p.nickname })
					} catch (e) { /* 头像/昵称失败不影响收发 */ }
				}
				await this.loadHistory()
				this.connectSocket()
			},
			/** 历史消息：首页即最后一页，上拉加载更早（page 递增按新到旧） */
			async loadHistory() {
				if (this.loadingHistory || !this.hasMore) return
				this.loadingHistory = true
				if (!this.messages.length) this.historyState = 'loading'
				try {
					if (!this.convId) {
						// 会话 ID 未知时（首次私聊）从会话列表解析
						await this.resolveConv()
					}
					if (!this.convId) {
						this.historyState = 'ready'
						this.hasMore = false
						return
					}
					const res = await apiMessages(this.convId, this.page, this.size)
					const list = res.list || []
					this.messages = list.concat(this.messages)
					this.hasMore = this.messages.length < (res.total || 0)
					this.page += 1
					this.historyState = 'ready'
					this.scrollToBottom()
				} catch (e) {
					this.historyState = 'error'
				} finally {
					this.loadingHistory = false
				}
			},
			/** 首次私聊（无 convId）时从会话列表解析会话 ID；不存在则由首次发送创建 */
			async resolveConv() {
				try {
					const convs = (await apiConversations()) || []
					const hit = convs.find((c) => c.peerId === this.peerId)
					if (hit) this.convId = hit.conversationId
				} catch (e) { /* 无会话则首次发送时后端创建 */ }
			},
			/** WebSocket：接收实时消息，断线自动重连（最多 5 次） */
			connectSocket() {
				const token = getToken()
				if (!token) return
				const url = BASE_URL_CONFIG.replace(/^http/, 'ws') + '/ws/chat?token=' + token
				this.socket = uni.connectSocket({ url, complete: () => {} })
				if (!this.socket) return
				this.socket.onOpen(() => { this.wsOpen = true })
				this.socket.onClose(() => {
					this.wsOpen = false
					this.reconnect()
				})
				this.socket.onError(() => { this.wsOpen = false })
				this.socket.onMessage((res) => {
					try {
						const data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
						this.onSocketMessage(data)
					} catch (e) { /* 忽略非法帧 */ }
				})
			},
			reconnect() {
				if (this._retries === undefined) this._retries = 0
				if (this._retries >= 5) return
				this._retries += 1
				setTimeout(() => this.connectSocket(), 3000)
			},
			onSocketMessage(data) {
				// 后端推送格式：{ conversationId, senderId, content, msgType, createdAt }
				if (!data) return
				if (this.convId && data.conversationId && data.conversationId !== this.convId) return
				if (data.senderId === this.myId) {
					// 自己的消息以服务端落库结果为准
					const idx = this.pending.findIndex((p) => p.content === data.content)
					if (idx > -1) this.pending.splice(idx, 1)
				}
				this.messages.push({ ...data, id: data.id || 'ws-' + Date.now() })
				this.scrollToBottom()
			},
			scrollToBottom() {
				this.$nextTick(() => {
					this.scrollTop = this.scrollTop + 99999 + Math.random()
				})
			},
			/** 发送：先 REST 落库（AC-07），WebSocket 仅做下行推送 */
			async send() {
				const content = this.draft.trim()
				if (!content || this.sending) return
				this.sending = true
				const key = ++this.pendingKey
				this.pending.push({ key, content, fail: false })
				this.draft = ''
				this.scrollToBottom()
				try {
					const msg = await apiSendMessage({
						receiverId: this.peerId,
						content,
						msgType: 'TEXT'
					})
					const i = this.pending.findIndex((p) => p.key === key)
					if (i > -1) this.pending.splice(i, 1)
					this.messages.push(msg)
					if (!this.convId && msg.conversationId) this.convId = msg.conversationId
					this.scrollToBottom()
				} catch (e) {
					const i = this.pending.findIndex((p) => p.key === key)
					if (i > -1) this.pending[i].fail = true
					this.draft = this.draft || content
				} finally {
					this.sending = false
				}
			},
			async sendImage() {
				uni.chooseImage({
					count: 1,
					sizeType: ['compressed'],
					success: async (res) => {
						uni.showLoading({ title: '发送中', mask: true })
						try {
							const url = await apiUpload(res.tempFilePaths[0])
							const msg = await apiSendMessage({
								receiverId: this.peerId,
								content: url,
								msgType: 'IMAGE'
							})
							this.messages.push(msg)
							this.scrollToBottom()
						} catch (e) { /* 请求层已提示 */ }
						finally { uni.hideLoading() }
					}
				})
			},
			previewImg(url) {
				const urls = this.messages.filter((m) => m.msgType === 'IMAGE').map((m) => m.content)
				uni.previewImage({ urls, current: url })
			}
		}
	}
</script>

<style scoped>
	.chat-page { display: flex; flex-direction: column; height: 100vh; background: var(--c-bg-page); }
	.msg-scroll { flex: 1; height: calc(100vh - 140rpx); }
	.msg-wrap { padding: 24rpx 32rpx; }
	.load-hint { display: block; text-align: center; font-size: 24rpx; color: var(--c-text-weak); padding: 12rpx 0 24rpx; }

	.msg-row { display: flex; flex-direction: row; margin-bottom: 28rpx; }
	.msg-row.mine { flex-direction: row-reverse; }
	.msg-avatar { width: 72rpx; height: 72rpx; border-radius: 50%; background: #E9EAEE; flex-shrink: 0; }
	.msg-bubble {
		max-width: 480rpx; padding: 20rpx 24rpx;
		background: #FFFFFF; border-radius: 8rpx 24rpx 24rpx 24rpx;
		margin-left: 16rpx; box-shadow: var(--shadow-card);
	}
	.msg-row.mine .msg-bubble {
		background: var(--c-primary); border-radius: 24rpx 8rpx 24rpx 24rpx;
		margin-left: 0; margin-right: 16rpx;
	}
	.msg-text { font-size: 28rpx; line-height: 1.6; color: var(--c-text-title); word-break: break-all; }
	.msg-row.mine .msg-text { color: #FFFFFF; }
	.msg-image { width: 320rpx; border-radius: 12rpx; background: #F2F3F5; }
	.msg-bubble.sending { opacity: 0.75; }
	.sending-flag { display: block; font-size: 22rpx; color: rgba(255, 255, 255, 0.85); margin-top: 8rpx; }
	.scroll-end { height: 8rpx; }

	.conn-tip { background: #FFF7E6; padding: 12rpx 32rpx; }
	.conn-tip-text { font-size: 24rpx; color: var(--c-warning); }

	.input-bar {
		display: flex; flex-direction: row; align-items: center;
		background: #FFFFFF; padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
		border-top: 1px solid var(--c-border);
	}
	.input-action { width: 64rpx; height: 64rpx; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
	.input-action-icon {
		width: 56rpx; height: 56rpx;
		background: var(--c-bg-page); border-radius: 16rpx; padding: 8rpx;
	}
	.msg-input {
		flex: 1; height: 72rpx; background: var(--c-bg-page);
		border-radius: 36rpx; padding: 0 28rpx; font-size: 28rpx; margin: 0 16rpx;
	}
	.send-btn { flex-shrink: 0; }
</style>
