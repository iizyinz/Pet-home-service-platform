<template>
	<view class="page order-page">
		<!-- 状态 Tab -->
		<scroll-view scroll-x class="tab-bar" :show-scrollbar="false">
			<view
				class="tab-item"
				:class="{ active: currentTab === t.key }"
				v-for="t in tabs"
				:key="t.key"
				@tap="switchTab(t.key)"
			>
				<text class="tab-text">{{ t.label }}</text>
				<view v-if="currentTab === t.key" class="tab-line"></view>
			</view>
		</scroll-view>

		<scroll-view
			scroll-y
			class="list-scroll"
			:refresher-enabled="true"
			:refresher-triggered="refreshing"
			@refresherrefresh="onRefresh"
			@scrolltolower="loadMore"
		>
			<view class="page-pad">
				<state-view v-if="state === 'loading' && !list.length" state="loading" :rows="4" />
				<state-view v-else-if="state === 'error'" state="error" :error-text="errorText" @retry="reload" />

				<block v-else-if="list.length">
					<view class="order-card" v-for="o in list" :key="o.id" @tap="goDetail(o)">
						<!-- 卡片头 -->
						<view class="row-between card-head">
							<text class="order-no">{{ o.orderNo }}</text>
							<status-tag :status="o.status" />
						</view>

						<!-- 主体 -->
						<view class="row card-body">
							<view class="flex-1">
								<text class="t-body ellipsis">{{ o.itemName || '服务项目' }}</text>
								<text class="t-weak line">宠物：{{ o.petName || '-' }}<block v-if="o.petBreed">（{{ o.petBreed }}）</block></text>
								<text class="t-weak line">服务师：{{ o.staffName || '待接单' }}</text>
								<text class="t-weak line">预约：{{ timeShort(o.appointmentTime) || '-' }}</text>
							</view>
							<view class="amount">
								<text class="amount-unit">¥</text>
								<text class="amount-num">{{ money(o.amount) }}</text>
							</view>
						</view>

						<!-- 未读进度提醒 -->
						<view class="unread-tip" v-if="Number(o.unreadProgress) > 0">
							<text class="unread-dot"></text>
							<text class="unread-text">{{ o.unreadProgress }} 条服务进度未读</text>
						</view>

						<!-- 操作区：按后端 availableActions 渲染，不自行推断 -->
						<view class="row-right card-actions" v-if="(o.availableActions || []).length">
							<view
								class="btn btn-outline btn-sm"
								v-for="act in visibleActions(o)"
								:key="act"
								@tap.stop="onAction(act, o)"
							>{{ actionText(act) }}</view>
						</view>
					</view>

					<text class="load-more">{{ loadingMore ? '加载中...' : (hasMore ? '上拉加载更多' : '没有更多了') }}</text>
				</block>

				<state-view v-else state="empty" :empty-text="emptyText">
					<template #empty-action>
						<button class="btn btn-primary btn-sm" @tap="goService">去逛逛服务</button>
					</template>
				</state-view>
			</view>
		</scroll-view>
	</view>
</template>

<script>
	import StateView from '../../components/state-view/state-view.vue'
	import StatusTag from '../../components/status-tag/status-tag.vue'
	import { apiOrders, apiCancelOrder, apiConfirmOrder } from '../../api/index'
	import { money, timeShort } from '../../utils/format'
	import { ORDER_TABS, ACTION_TEXT } from '../../utils/order-status'
	import { ensureLogin } from '../../utils/auth'

	export default {
		components: { StateView, StatusTag },
		data() {
			return {
				tabs: ORDER_TABS,
				currentTab: 'ALL',
				state: 'loading',
				errorText: '',
				refreshing: false,
				loadingMore: false,
				list: [],
				page: 1,
				size: 10,
				total: 0,
				hasMore: true,
				actionLock: false
			}
		},
		computed: {
			emptyText() {
				const t = this.tabs.find((x) => x.key === this.currentTab)
				return this.currentTab === 'ALL' ? '还没有订单，先去预约服务吧' : `暂无${t ? t.label : ''}订单`
			}
		},
		onLoad(options) {
			if (options && options.status) this.currentTab = options.status
		},
		onShow() {
			if (!ensureLogin()) return
			// 消费"我的订单"入口写入的 Tab 选择（switchTab 无法带参）
			const preset = uni.getStorageSync('orderTabStatus')
			if (preset) {
				uni.removeStorageSync('orderTabStatus')
				const hit = this.tabs.find((t) => t.key === preset)
				if (hit) this.currentTab = preset
			}
			// 每次回到订单 Tab 从第一页刷新（下单/取消/确认后状态变化）
			this.reload()
		},
		onPullDownRefresh() {
			this.reload().finally(() => uni.stopPullDownRefresh())
		},
		methods: {
			money,
			timeShort,
			actionText(act) {
				return ACTION_TEXT[act] || act
			},
			/** 列表卡片仅展示轻量动作，重动作收敛在详情页 */
			visibleActions(o) {
				const acts = o.availableActions || []
				return acts.filter((a) => ['cancel', 'confirm', 'review', 'chat'].includes(a))
			},
			switchTab(key) {
				if (this.currentTab === key) return
				this.currentTab = key
				this.reload()
			},
			async reload() {
				this.page = 1
				this.state = 'loading'
				this.errorText = ''
				try {
					const res = await apiOrders({
						status: this.currentTab === 'ALL' ? undefined : this.currentTab,
						page: this.page,
						size: this.size
					})
					this.list = res.list || []
					this.total = res.total || 0
					this.hasMore = this.list.length < this.total
					this.state = 'ready'
				} catch (e) {
					this.state = 'error'
					this.errorText = (e && e.message) || '订单加载失败'
				}
			},
			async loadMore() {
				if (this.loadingMore || !this.hasMore || this.state === 'error') return
				this.loadingMore = true
				try {
					const res = await apiOrders({
						status: this.currentTab === 'ALL' ? undefined : this.currentTab,
						page: this.page + 1,
						size: this.size
					})
					this.page += 1
					this.list = this.list.concat(res.list || [])
					this.hasMore = this.list.length < (res.total || 0)
				} catch (e) {
					uni.showToast({ title: '加载更多失败，请上拉重试', icon: 'none' })
				} finally {
					this.loadingMore = false
				}
			},
			async onRefresh() {
				this.refreshing = true
				await this.reload()
				this.refreshing = false
			},
			goDetail(o) {
				uni.navigateTo({ url: '/pages/order/detail?id=' + o.id })
			},
			goService() {
				uni.switchTab({ url: '/pages/service/list' })
			},
			/** 列表内动作：取消（弹窗确认）、确认完成（弹窗确认）、评价、私信 */
			onAction(act, o) {
				if (this.actionLock) return
				if (act === 'cancel') return this.confirmCancel(o)
				if (act === 'confirm') return this.confirmComplete(o)
				if (act === 'review') return uni.navigateTo({ url: '/pages/order/review?orderId=' + o.id })
				if (act === 'chat') {
					const q = o.staffId ? '?peerId=' + o.staffId + '&peerName=' + encodeURIComponent(o.staffName || '') : ''
					return uni.navigateTo({ url: '/pages/chat/detail' + q })
				}
			},
			confirmCancel(o) {
				uni.showModal({
					title: '取消订单',
					content: '取消后将退回订单金额至余额，确定取消该订单吗？',
					confirmText: '确定取消',
					confirmColor: '#FF4D4F',
					cancelText: '再想想',
					success: async (r) => {
						if (!r.confirm) return
						this.actionLock = true
						try {
							await apiCancelOrder(o.id, '用户主动取消')
							uni.showToast({ title: '已取消并退款', icon: 'success' })
							this.reload()
						} catch (e) {
							// 2002 非法流转等由请求层提示
						} finally {
							this.actionLock = false
						}
					}
				})
			},
			confirmComplete(o) {
				uni.showModal({
					title: '确认完成',
					content: '确认后订单完成并可提交评价，确定服务已完成吗？',
					confirmText: '确认完成',
					cancelText: '再等等',
					success: async (r) => {
						if (!r.confirm) return
						this.actionLock = true
						try {
							await apiConfirmOrder(o.id)
							uni.showToast({ title: '已确认完成', icon: 'success' })
							this.reload()
						} catch (e) {
							// 保持当前列表
						} finally {
							this.actionLock = false
						}
					}
				})
			}
		}
	}
</script>

<style scoped>
	.order-page { padding-bottom: 0; }
	.tab-bar { white-space: nowrap; background: #FFFFFF; padding: 0 16rpx; }
	.tab-item { display: inline-block; position: relative; padding: 24rpx 24rpx 20rpx; }
	.tab-text { font-size: 28rpx; color: var(--c-text-secondary); }
	.tab-item.active .tab-text { color: var(--c-primary); font-weight: 600; }
	.tab-line {
		position: absolute; left: 50%; transform: translateX(-50%); bottom: 8rpx;
		width: 48rpx; height: 6rpx; border-radius: 3rpx; background: var(--c-primary);
	}

	.list-scroll { height: calc(100vh - 100rpx); padding-top: 24rpx; }
	.order-card {
		background: var(--c-bg-card); border-radius: var(--radius-card);
		padding: 28rpx; margin-bottom: 24rpx; box-shadow: var(--shadow-card);
	}
	.card-head { padding-bottom: 20rpx; border-bottom: 1px solid var(--c-border); }
	.order-no { font-size: 26rpx; color: var(--c-text-secondary); font-weight: 500; }
	.card-body { padding-top: 24rpx; align-items: flex-start; }
	.line { display: block; margin-top: 10rpx; }
	.amount { display: flex; flex-direction: row; align-items: baseline; flex-shrink: 0; margin-left: 16rpx; }
	.amount-unit { font-size: 24rpx; color: var(--c-primary); }
	.amount-num { font-size: 36rpx; font-weight: 600; color: var(--c-primary); }

	.unread-tip {
		display: flex; flex-direction: row; align-items: center;
		background: var(--c-primary-weak); border-radius: 12rpx;
		padding: 14rpx 20rpx; margin-top: 20rpx;
	}
	.unread-dot { width: 14rpx; height: 14rpx; border-radius: 50%; background: var(--c-error); margin-right: 12rpx; }
	.unread-text { font-size: 24rpx; color: var(--c-primary); }

	.card-actions {
		justify-content: flex-end; flex-wrap: wrap;
		margin-top: 24rpx; padding-top: 20rpx; border-top: 1px solid var(--c-border);
	}
	.card-actions .btn { margin-left: 16rpx; margin-top: 8rpx; }
	.load-more { display: block; text-align: center; color: var(--c-text-weak); font-size: 24rpx; padding: 24rpx 0 40rpx; }
</style>
