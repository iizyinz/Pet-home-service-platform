<template>
	<view class="page msg-page">
		<!-- 顶部 Tab：通知 / 会话 -->
		<view class="seg">
			<view class="seg-item" :class="{ active: tab === 'notice' }" @tap="switchTab('notice')">
				<text>系统通知</text>
				<view class="seg-badge" v-if="noticeUnread > 0">{{ badge(noticeUnread) }}</view>
			</view>
			<view class="seg-item" :class="{ active: tab === 'chat' }" @tap="switchTab('chat')">
				<text>私信</text>
				<view class="seg-badge" v-if="chatUnread > 0">{{ badge(chatUnread) }}</view>
			</view>
		</view>

		<!-- 通知列表 -->
		<scroll-view
			v-if="tab === 'notice'"
			scroll-y
			class="list-scroll"
			:refresher-enabled="true"
			:refresher-triggered="refreshing"
			@refresherrefresh="onRefresh"
			@scrolltolower="loadMoreNotice"
		>
			<view class="page-pad">
				<view class="notice-all row-between" v-if="notices.length">
					<text class="t-sub">共 {{ noticeTotal }} 条通知</text>
					<text class="link" @tap="readAll">全部标记已读</text>
				</view>

				<state-view v-if="noticeState === 'loading' && !notices.length" state="loading" :rows="4" />
				<state-view v-else-if="noticeState === 'error'" state="error" error-text="通知加载失败" @retry="loadNotices" />

				<block v-else-if="notices.length">
					<view class="notice-card" :class="{ unread: n.isRead === 0 }" v-for="n in notices" :key="n.id" @tap="openNotice(n)">
						<view class="notice-dot" v-if="n.isRead === 0"></view>
						<view class="flex-1">
							<text class="t-body ellipsis">{{ n.title }}</text>
							<text class="t-sub notice-content ellipsis-2">{{ n.content }}</text>
							<text class="t-weak notice-time">{{ timeLabel(n.createdAt) }}</text>
						</view>
					</view>
					<text class="load-more">{{ noticeLoadingMore ? '加载中...' : (noticeHasMore ? '上拉加载更多' : '没有更多了') }}</text>
				</block>

				<state-view v-else state="empty" empty-text="暂无系统通知" />
			</view>
		</scroll-view>

		<!-- 会话列表 -->
		<scroll-view
			v-else
			scroll-y
			class="list-scroll"
			:refresher-enabled="true"
			:refresher-triggered="refreshing"
			@refresherrefresh="onRefresh"
		>
			<view class="page-pad">
				<state-view v-if="convState === 'loading' && !convs.length" state="loading" :rows="4" />
				<state-view v-else-if="convState === 'error'" state="error" error-text="会话加载失败" @retry="loadConvs" />

				<block v-else-if="convs.length">
					<view class="conv" v-for="c in convs" :key="c.conversationId" @tap="openChat(c)">
						<view class="conv-avatar-wrap">
							<image class="conv-avatar" :src="c.peerAvatar || placeholder" mode="aspectFill" />
							<view class="online" v-if="c.online"></view>
						</view>
						<view class="conv-info flex-1">
							<view class="row-between">
								<text class="t-body ellipsis conv-name">{{ c.peerNickname || '用户' }}</text>
								<text class="t-weak">{{ timeLabel(c.lastMessageAt) }}</text>
							</view>
							<view class="row-between">
								<text class="t-sub ellipsis conv-last">{{ c.lastMessage || '暂无消息' }}</text>
								<view class="conv-badge" v-if="c.unreadCount > 0">{{ badge(c.unreadCount) }}</view>
							</view>
						</view>
					</view>
				</block>

				<state-view v-else state="empty" empty-text="暂无私信，可在服务师主页发起沟通">
					<template #empty-action>
						<button class="btn btn-outline btn-sm" @tap="goStaff">看看服务师</button>
					</template>
				</state-view>
			</view>
		</scroll-view>
	</view>
</template>

<script>
	import StateView from '../../components/state-view/state-view.vue'
	import { apiNotices, apiNoticeRead, apiNoticeReadAll, apiUnreadCount, apiConversations } from '../../api/index'
	import { badgeText, timeLabel } from '../../utils/format'
	import { ensureLogin } from '../../utils/auth'

	const PLACEHOLDER = '/static/logo.png'

	export default {
		components: { StateView },
		data() {
			return {
				tab: 'notice',
				refreshing: false,
				placeholder: PLACEHOLDER,
				// 通知
				notices: [],
				noticeTotal: 0,
				noticePage: 1,
				noticeHasMore: true,
				noticeLoadingMore: false,
				noticeState: 'loading',
				noticeUnread: 0,
				// 会话
				convs: [],
				convState: 'loading',
				chatUnread: 0
			}
		},
		onLoad(options) {
			if (!ensureLogin()) return
			if (options && options.tab === 'chat') this.tab = 'chat'
			this.loadNotices()
			this.loadConvs()
			this.loadUnread()
		},
		onPullDownRefresh() {
			this.onRefresh().finally(() => uni.stopPullDownRefresh())
		},
		methods: {
			timeLabel,
			badge: badgeText,
			switchTab(tab) {
				if (this.tab === tab) return
				this.tab = tab
			},
			async loadUnread() {
				try {
					const res = await apiUnreadCount()
					this.noticeUnread = res.count || 0
				} catch (e) { /* 静默 */ }
				try {
					const list = (await apiConversations({ silent: true })) || []
					this.chatUnread = list.reduce((s, c) => s + (Number(c.unreadCount) || 0), 0)
				} catch (e) { /* 静默 */ }
			},
			async loadNotices() {
				this.noticeState = 'loading'
				try {
					const res = await apiNotices(1, 10)
					this.notices = res.list || []
					this.noticeTotal = res.total || 0
					this.noticePage = 1
					this.noticeHasMore = this.notices.length < this.noticeTotal
					this.noticeState = 'ready'
				} catch (e) {
					this.noticeState = 'error'
				}
			},
			async loadMoreNotice() {
				if (this.noticeLoadingMore || !this.noticeHasMore) return
				this.noticeLoadingMore = true
				try {
					const res = await apiNotices(this.noticePage + 1, 10)
					this.noticePage += 1
					this.notices = this.notices.concat(res.list || [])
					this.noticeHasMore = this.notices.length < (res.total || 0)
				} catch (e) {
					uni.showToast({ title: '加载更多失败', icon: 'none' })
				} finally {
					this.noticeLoadingMore = false
				}
			},
			async loadConvs() {
				this.convState = 'loading'
				try {
					this.convs = (await apiConversations()) || []
					this.convState = 'ready'
				} catch (e) {
					this.convState = 'error'
				}
			},
			async onRefresh() {
				this.refreshing = true
				await Promise.all([this.loadNotices(), this.loadConvs(), this.loadUnread()])
				this.refreshing = false
			},
			/** 通知点击：标记已读并按业务类型跳转订单 */
			async openNotice(n) {
				if (n.isRead === 0) {
					try {
						await apiNoticeRead(n.id)
						n.isRead = 1
						this.noticeUnread = Math.max(0, this.noticeUnread - 1)
					} catch (e) { /* 点击跳转不受影响 */ }
				}
				if (n.type && n.type.indexOf('ORDER') > -1 && n.bizId) {
					uni.navigateTo({ url: '/pages/order/detail?id=' + n.bizId })
				}
			},
			async readAll() {
				try {
					await apiNoticeReadAll()
					this.notices.forEach((n) => { n.isRead = 1 })
					this.noticeUnread = 0
					uni.showToast({ title: '已全部标记已读', icon: 'success' })
				} catch (e) { /* 请求层已提示 */ }
			},
			openChat(c) {
				uni.navigateTo({
					url: '/pages/chat/detail?convId=' + c.conversationId +
						'&peerId=' + c.peerId +
						'&peerName=' + encodeURIComponent(c.peerNickname || '')
				})
			},
			goStaff() {
				uni.navigateTo({ url: '/pages/staff/list' })
			}
		}
	}
</script>

<style scoped>
	.msg-page { padding-bottom: 0; }
	.seg { display: flex; background: #FFFFFF; padding: 16rpx 32rpx 0; }
	.seg-item {
		flex: 1; text-align: center; position: relative;
		padding: 16rpx 0 20rpx; font-size: 30rpx; color: var(--c-text-secondary);
	}
	.seg-item.active { color: var(--c-primary); font-weight: 600; }
	.seg-item.active::after {
		content: ''; position: absolute; left: 50%; transform: translateX(-50%); bottom: 6rpx;
		width: 56rpx; height: 6rpx; border-radius: 3rpx; background: var(--c-primary);
	}
	.seg-badge {
		position: absolute; top: 4rpx; right: 24rpx;
		min-width: 32rpx; height: 32rpx; line-height: 32rpx; text-align: center;
		background: var(--c-error); color: #FFFFFF; font-size: 20rpx;
		border-radius: 16rpx; padding: 0 8rpx;
	}

	.list-scroll { height: calc(100vh - 110rpx); padding-top: 24rpx; }
	.notice-all { padding: 8rpx 0 16rpx; }
	.link { color: var(--c-info); font-size: 26rpx; }

	.notice-card {
		display: flex; flex-direction: row;
		background: var(--c-bg-card); border-radius: var(--radius-card);
		padding: 28rpx; margin-bottom: 20rpx; box-shadow: var(--shadow-card);
	}
	.notice-dot { width: 14rpx; height: 14rpx; border-radius: 50%; background: var(--c-error); margin: 12rpx 16rpx 0 0; flex-shrink: 0; }
	.notice-content { display: block; margin-top: 10rpx; line-height: 1.6; }
	.notice-time { display: block; margin-top: 12rpx; }

	.conv {
		display: flex; flex-direction: row; align-items: center;
		background: var(--c-bg-card); border-radius: var(--radius-card);
		padding: 24rpx 28rpx; margin-bottom: 20rpx; box-shadow: var(--shadow-card);
	}
	.conv-avatar-wrap { position: relative; flex-shrink: 0; }
	.conv-avatar { width: 96rpx; height: 96rpx; border-radius: 50%; background: #F2F3F5; }
	.online {
		position: absolute; right: 4rpx; bottom: 4rpx;
		width: 20rpx; height: 20rpx; border-radius: 50%;
		background: var(--c-success); border: 3rpx solid #FFFFFF;
	}
	.conv-info { margin-left: 20rpx; display: flex; flex-direction: column; }
	.conv-name { max-width: 340rpx; font-weight: 500; }
	.conv-last { margin-top: 10rpx; max-width: 440rpx; }
	.conv-badge {
		min-width: 36rpx; height: 36rpx; line-height: 36rpx; text-align: center;
		background: var(--c-error); color: #FFFFFF; font-size: 22rpx;
		border-radius: 18rpx; padding: 0 10rpx; margin-left: 12rpx; flex-shrink: 0;
	}
	.load-more { display: block; text-align: center; color: var(--c-text-weak); font-size: 24rpx; padding: 24rpx 0 40rpx; }
</style>
