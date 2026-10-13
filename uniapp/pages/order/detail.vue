<template>
	<view class="page detail-page">
		<state-view v-if="state === 'loading'" state="loading" :rows="5" />
		<state-view v-else-if="state === 'error'" state="error" :error-text="errorText" @retry="load" />

		<block v-else-if="order">
			<!-- 状态头 -->
			<view class="status-head">
				<text class="status-label" :style="{ color: statusMeta.color }">{{ statusMeta.label }}</text>
				<text class="status-desc">{{ statusDesc }}</text>
			</view>

			<!-- 时间轴 -->
			<view class="card">
				<text class="t-module">服务进度</text>
				<view class="timeline" v-if="timeline.length">
					<view class="tl-item" v-for="(t, i) in timeline" :key="i" :class="{ current: i === 0 }">
						<view class="tl-dot"></view>
						<view class="tl-body">
							<text class="tl-title">{{ t.label }}</text>
							<text class="t-weak">{{ t.time }}</text>
						</view>
					</view>
				</view>
				<text class="t-weak block-text" v-else>暂无进度记录</text>
			</view>

			<!-- 服务过程反馈（服务师发布，V1 订单粒度已读：进入详情即请求进度接口触发后端标记已读） -->
			<view class="card">
				<view class="row-between">
					<text class="t-module">服务反馈</text>
					<text class="unread-flag" v-if="order.progressUnread">有未读</text>
				</view>

				<state-view v-if="progressState === 'loading'" state="loading" :rows="2" />
				<state-view v-else-if="progressState === 'error'" state="error" error-text="反馈加载失败" @retry="loadProgress" />
				<block v-else-if="progressList.length">
					<view class="progress" v-for="p in progressList" :key="p.id">
						<view class="row-between">
							<text class="t-body progress-title">{{ p.title }}</text>
							<text class="t-weak">{{ timeShort(p.createdAt) }}</text>
						</view>
						<text class="t-sub progress-time" v-if="p.serviceTime">服务时间：{{ p.serviceTime }}</text>
						<view class="row progress-imgs" v-if="progressImages(p).length">
							<image
								class="progress-img"
								v-for="(img, i) in progressImages(p)"
								:key="i"
								:src="img"
								mode="aspectFill"
								@tap="preview(progressImages(p), img)"
							/>
						</view>
					</view>
				</block>
				<text v-else class="t-weak block-text">服务师尚未发布服务反馈</text>
			</view>

			<!-- 订单信息 -->
			<view class="card">
				<text class="t-module">订单信息</text>
				<view class="kv"><text class="k">订单编号</text><text class="v">{{ order.orderNo }}</text></view>
				<view class="kv"><text class="k">服务项目</text><text class="v">{{ order.itemName || '-' }}</text>
					<text class="v-sub" v-if="order.durationMinutes">时长 {{ order.durationMinutes }} 分钟</text></view>
				<view class="kv"><text class="k">订单金额</text><text class="v price">¥{{ money(order.amount) }}</text></view>
				<view class="kv"><text class="k">预约时间</text><text class="v">{{ timeShort(order.appointmentTime) || '-' }}</text></view>
				<view class="kv" v-if="order.startTime"><text class="k">开始时间</text><text class="v">{{ timeShort(order.startTime) }}</text></view>
				<view class="kv" v-if="order.finishTime"><text class="k">完成时间</text><text class="v">{{ timeShort(order.finishTime) }}</text></view>
				<view class="kv" v-if="order.confirmTime"><text class="k">确认时间</text><text class="v">{{ timeShort(order.confirmTime) }}</text></view>
				<view class="kv" v-if="order.remark"><text class="k">我的备注</text><text class="v">{{ order.remark }}</text></view>
				<view class="kv" v-if="order.cancelReason"><text class="k">取消原因</text><text class="v error">{{ order.cancelReason }}</text></view>
			</view>

			<!-- 服务信息 -->
			<view class="card">
				<text class="t-module">服务信息</text>
				<view class="kv"><text class="k">宠物</text><text class="v">{{ order.petName || '-' }}<block v-if="order.petBreed">（{{ order.petBreed }}）</block></text></view>
				<view class="kv"><text class="k">服务师</text><text class="v">{{ order.staffName || '待接单' }}</text></view>
				<view class="kv"><text class="k">联系人</text><text class="v">{{ order.contactName || '-' }}</text></view>
				<view class="kv"><text class="k">联系电话</text><text class="v">{{ order.contactPhone || '-' }}</text></view>
				<view class="kv kv-top"><text class="k">上门地址</text><text class="v">{{ order.address || '-' }}</text></view>
			</view>

			<view class="foot-space"></view>

			<!-- 底部动作：仅渲染后端 availableActions -->
			<view class="action-bar" v-if="actions.length">
				<button
					v-for="(act, i) in actions"
					:key="act"
					class="btn btn-sm action-btn"
					:class="actionClass(act, i)"
					:disabled="actionLock"
					@tap="onAction(act)"
				>{{ actionText(act) }}</button>
			</view>
		</block>

		<!-- 取消原因弹窗 -->
		<view class="modal-mask" v-if="showCancel" @tap="showCancel = false">
			<view class="modal-panel" @tap.stop>
				<text class="modal-title">取消订单</text>
				<text class="modal-text">取消后订单金额将退回余额，确定取消吗？</text>
				<textarea
					class="cancel-input"
					v-model="cancelReason"
					placeholder="选填：取消原因（最多 255 字）"
					placeholder-class="form-input-placeholder"
					:maxlength="255"
				/>
				<view class="modal-actions">
					<button class="btn btn-outline" @tap="showCancel = false">再想想</button>
					<button class="btn btn-danger" :disabled="actionLock" @tap="doCancel">{{ actionLock ? '提交中...' : '确认取消' }}</button>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	import StateView from '../../components/state-view/state-view.vue'
	import { apiOrderDetail, apiOrderProgress, apiCancelOrder, apiConfirmOrder } from '../../api/index'
	import { money, timeShort } from '../../utils/format'
	import { statusMeta, ACTION_TEXT } from '../../utils/order-status'
	import { ensureLogin } from '../../utils/auth'

	export default {
		components: { StateView },
		data() {
			return {
				state: 'loading',
				errorText: '',
				id: null,
				order: null,
				progressList: [],
				progressState: 'loading',
				showCancel: false,
				cancelReason: '',
				actionLock: false
			}
		},
		computed: {
			statusMeta() {
				return statusMeta(this.order && this.order.status)
			},
			statusDesc() {
				const map = {
					PENDING_CONFIRM: '已扣款，等待服务师接单',
					CONFIRMED: '服务师已接单，请等待上门',
					SERVING: '服务进行中，可查看服务反馈',
					PENDING_SETTLE: '服务师已完成，请确认服务结果',
					COMPLETED: '订单已完成，可提交评价',
					CANCELLED: '订单已取消，款项已退回余额'
				}
				return (this.order && map[this.order.status]) || ''
			},
			actions() {
				return (this.order && this.order.availableActions) || []
			},
			timeline() {
				const o = this.order
				if (!o) return []
				const steps = []
				if (o.confirmTime) steps.push({ label: '服务师已确认完成', time: timeShort(o.confirmTime) })
				if (o.finishTime) steps.push({ label: '服务师标记完成', time: timeShort(o.finishTime) })
				if (o.startTime) steps.push({ label: '服务开始', time: timeShort(o.startTime) })
				if (o.status !== 'PENDING_CONFIRM') {
					steps.push({ label: '服务师已接单', time: timeShort(o.createdAt) })
				}
				steps.push({ label: '预约提交成功', time: timeShort(o.createdAt) })
				if (o.status === 'CANCELLED' && o.cancelReason) {
					steps.unshift({ label: '订单取消：' + o.cancelReason, time: timeShort(o.updatedAt) })
				}
				return steps
			}
		},
		onLoad(options) {
			if (!ensureLogin()) return
			this.id = options && options.id ? Number(options.id) : null
			if (!this.id) {
				this.state = 'error'
				this.errorText = '参数缺失，请返回重试'
				return
			}
			this.load()
			// 进入详情即拉取进度：后端在读取时标记已读（AC-05）
			this.loadProgress()
		},
		methods: {
			money,
			timeShort,
			actionText(act) {
				return ACTION_TEXT[act] || { feedback: '发布进度', accept: '接单', start: '开始服务', finish: '完成服务' }[act] || act
			},
			actionClass(act, i) {
				const primary = ['confirm', 'review', 'accept', 'start', 'finish', 'feedback']
				if (act === 'cancel') return 'btn-outline'
				return primary.includes(act) && i === 0 ? 'btn-primary' : 'btn-outline'
			},
			progressImages(p) {
				if (!p.images) return []
				try {
					const arr = typeof p.images === 'string' ? JSON.parse(p.images) : p.images
					return Array.isArray(arr) ? arr : []
				} catch (e) {
					return []
				}
			},
			async load() {
				this.state = 'loading'
				try {
					this.order = await apiOrderDetail(this.id)
					this.state = 'ready'
				} catch (e) {
					this.state = 'error'
					this.errorText = (e && e.message) || (e && e.code === 403 ? '无权查看该订单' : '订单详情加载失败')
				}
			},
			async loadProgress() {
				this.progressState = 'loading'
				try {
					this.progressList = (await apiOrderProgress(this.id)) || []
					this.progressState = 'ready'
					// 后端已标记已读，本地同步未读标识
					if (this.order) this.order.progressUnread = false
				} catch (e) {
					this.progressState = 'error'
				}
			},
			preview(urls, current) {
				uni.previewImage({ urls, current })
			},
			onAction(act) {
				if (this.actionLock) return
				if (act === 'cancel') {
					this.cancelReason = ''
					this.showCancel = true
					return
				}
				if (act === 'confirm') return this.doConfirm()
				if (act === 'review') return uni.navigateTo({ url: '/pages/order/review?orderId=' + this.id })
				if (act === 'chat') {
					const q = this.order.staffId
						? '?peerId=' + this.order.staffId + '&peerName=' + encodeURIComponent(this.order.staffName || '')
						: ''
					return uni.navigateTo({ url: '/pages/chat/detail' + q })
				}
			},
			/** POST /api/orders/{id}/cancel：PENDING_CONFIRM/CONFIRMED 可取消，退款同事务 */
			async doCancel() {
				if (this.actionLock) return
				this.actionLock = true
				try {
					await apiCancelOrder(this.id, this.cancelReason.trim())
					this.showCancel = false
					uni.showToast({ title: '已取消并退款', icon: 'success' })
					this.load()
					this.loadProgress()
				} catch (e) {
					// 失败保持弹窗，用户可修改后重试
				} finally {
					this.actionLock = false
				}
			},
			/** POST /api/orders/{id}/confirm：PENDING_SETTLE → COMPLETED */
			async doConfirm() {
				uni.showModal({
					title: '确认完成',
					content: '确认后订单完成并可提交评价，确定服务已完成吗？',
					confirmText: '确认完成',
					cancelText: '再等等',
					success: async (r) => {
						if (!r.confirm) return
						this.actionLock = true
						try {
							await apiConfirmOrder(this.id)
							uni.showToast({ title: '已确认完成', icon: 'success' })
							this.load()
						} catch (e) {
							// 状态已变化时提示后刷新
							this.load()
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
	.detail-page { padding-bottom: calc(140rpx + env(safe-area-inset-bottom)); }
	.status-head {
		background: #FFFFFF; padding: 40rpx 32rpx;
		display: flex; flex-direction: column;
	}
	.status-label { font-size: 44rpx; font-weight: 600; }
	.status-desc { font-size: 26rpx; color: var(--c-text-secondary); margin-top: 12rpx; }

	.card { margin: 24rpx 32rpx 0; }
	.block-text { display: block; margin-top: 20rpx; }
	.unread-flag { font-size: 24rpx; color: var(--c-error); background: #FFF1F0; padding: 6rpx 14rpx; border-radius: 12rpx; }

	.timeline { margin-top: 24rpx; }
	.tl-item { display: flex; flex-direction: row; position: relative; padding-bottom: 32rpx; }
	.tl-item:last-child { padding-bottom: 0; }
	.tl-item::before {
		content: ''; position: absolute; left: 11rpx; top: 28rpx; bottom: 0;
		width: 2rpx; background: var(--c-border);
	}
	.tl-item:last-child::before { display: none; }
	.tl-dot {
		width: 24rpx; height: 24rpx; border-radius: 50%;
		background: #E9EAEE; flex-shrink: 0; margin-top: 6rpx; z-index: 1;
	}
	.tl-item.current .tl-dot { background: var(--c-primary); }
	.tl-body { margin-left: 20rpx; display: flex; flex-direction: column; }
	.tl-title { font-size: 28rpx; color: var(--c-text-title); }
	.tl-item.current .tl-title { font-weight: 600; }
	.tl-body .t-weak { margin-top: 6rpx; }

	.progress { padding: 24rpx 0; border-bottom: 1px solid var(--c-border); }
	.progress:last-child { border-bottom: none; padding-bottom: 0; }
	.progress-title { font-weight: 500; }
	.progress-time { display: block; margin-top: 10rpx; }
	.progress-imgs { margin-top: 16rpx; flex-wrap: wrap; }
	.progress-img { width: 152rpx; height: 152rpx; border-radius: 12rpx; background: #F2F3F5; margin: 0 16rpx 16rpx 0; }

	.kv { display: flex; flex-direction: row; padding: 16rpx 0; align-items: baseline; }
	.k { width: 176rpx; font-size: 28rpx; color: var(--c-text-weak); flex-shrink: 0; }
	.v { flex: 1; font-size: 28rpx; color: var(--c-text-title); text-align: right; }
	.v.price { color: var(--c-primary); font-weight: 600; }
	.v.error { color: var(--c-error); }
	.v-sub { font-size: 24rpx; color: var(--c-text-weak); margin-left: 12rpx; }
	.kv-top { align-items: flex-start; }

	.foot-space { height: 32rpx; }
	.action-bar {
		position: fixed; left: 0; right: 0; bottom: 0;
		background: #FFFFFF; box-shadow: var(--shadow-card);
		display: flex; flex-direction: row; justify-content: flex-end; align-items: center;
		padding: 16rpx 32rpx calc(16rpx + env(safe-area-inset-bottom));
		z-index: 20;
	}
	.action-btn { min-width: 200rpx; margin-left: 16rpx; }

	.cancel-input {
		width: 100%; min-height: 120rpx; background: var(--c-bg-page);
		border-radius: 16rpx; padding: 20rpx; margin-top: 24rpx;
		font-size: 28rpx; line-height: 1.6;
	}
</style>
