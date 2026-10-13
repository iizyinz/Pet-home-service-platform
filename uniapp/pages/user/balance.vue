<template>
	<view class="page balance-page">
		<!-- 余额卡 -->
		<view class="balance-card">
			<text class="balance-label">当前余额（元）</text>
			<text class="balance-num">{{ money(balance) }}</text>
			<text class="balance-tip">余额用于预约上门服务，充值为平台内部模拟充值</text>
		</view>

		<!-- 充值 -->
		<view class="card">
			<text class="t-module">充值</text>
			<view class="amount-grid">
				<view
					class="amount-cell"
					:class="{ active: amount === n }"
					v-for="n in amountOptions"
					:key="n"
					@tap="amount = n"
				>
					<text class="amount-text">¥{{ n }}</text>
				</view>
				<view class="amount-cell amount-custom" :class="{ active: customAmount }" @tap="useCustom">
					<input class="amount-input" type="digit" v-model="customAmount" placeholder="自定义" placeholder-class="form-input-placeholder" @input="amount = 0" />
				</view>
			</view>
			<button
				class="btn btn-primary btn-block recharge-btn"
				:class="{ 'btn-disabled': submitting || !payAmount }"
				:disabled="submitting || !payAmount"
				@tap="recharge"
			>{{ submitting ? '充值中...' : '立即充值' }}</button>
		</view>

		<!-- 流水 -->
		<view class="card">
			<text class="t-module">余额变动记录</text>

			<state-view v-if="recState === 'loading' && !records.length" state="loading" :rows="3" />
			<state-view v-else-if="recState === 'error'" state="error" error-text="流水加载失败" @retry="loadRecords" />

			<block v-else-if="records.length">
				<view class="rec" v-for="r in records" :key="r.id">
					<view class="flex-1">
						<text class="t-body">{{ typeLabel(r.type) }}</text>
						<text class="t-weak rec-sub">{{ r.remark || (r.orderId ? '关联订单 #' + r.orderId : typeLabel(r.type)) }}</text>
						<text class="t-weak rec-sub">{{ r.createdAt }}</text>
					</view>
					<view class="rec-amount-wrap">
						<text class="rec-amount" :class="{ plus: r.type !== 'CONSUME' }">{{ sign(r.type) }}{{ money(r.amount) }}</text>
						<text class="t-weak rec-after">余额 {{ money(r.balanceAfter) }}</text>
					</view>
				</view>
				<text class="load-more">{{ recLoadingMore ? '加载中...' : (recHasMore ? '上拉加载更多' : '没有更多了') }}</text>
			</block>

			<state-view v-else state="empty" empty-text="暂无余额变动记录" />
		</view>

		<view class="foot-space"></view>
	</view>
</template>

<script>
	import StateView from '../../components/state-view/state-view.vue'
	import { apiBalance, apiBalanceRecords, apiRecharge } from '../../api/index'
	import { money } from '../../utils/format'
	import { RECORD_TYPE } from '../../utils/order-status'
	import { ensureLogin } from '../../utils/auth'

	export default {
		components: { StateView },
		data() {
			return {
				balance: 0,
				amountOptions: [50, 100, 200, 500],
				amount: 100,
				customAmount: '',
				submitting: false,
				records: [],
				recState: 'loading',
				recPage: 1,
				recHasMore: true,
				recLoadingMore: false
			}
		},
		computed: {
			payAmount() {
				if (this.amount) return this.amount
				const n = Number(this.customAmount)
				return Number.isNaN(n) || n <= 0 ? 0 : n
			}
		},
		onLoad(options) {
			if (!ensureLogin()) return
			if (options && options.recharge) this.$nextTick(() => this.scrollToRecharge())
			this.loadBalance()
			this.loadRecords()
		},
		onPullDownRefresh() {
			Promise.all([this.loadBalance(), this.reloadRecords()]).finally(() => uni.stopPullDownRefresh())
		},
		methods: {
			money,
			typeLabel(t) {
				return (RECORD_TYPE[t] && RECORD_TYPE[t].label) || t
			},
			sign(t) {
				return (RECORD_TYPE[t] && RECORD_TYPE[t].sign) || ''
			},
			useCustom() {
				this.amount = 0
			},
			scrollToRecharge() {
				// 从下单页余额不足跳入时给出提示
				uni.showToast({ title: '请选择充值金额', icon: 'none' })
			},
			async loadBalance() {
				try {
					const res = await apiBalance()
					this.balance = Number(res.balance) || 0
				} catch (e) {
					// 请求层已提示
				}
			},
			async reloadRecords() {
				this.recPage = 1
				this.recState = 'loading'
				try {
					const res = await apiBalanceRecords(1, 10)
					this.records = res.list || []
					this.recPage = 1
					this.recHasMore = this.records.length < (res.total || 0)
					this.recState = 'ready'
				} catch (e) {
					this.recState = 'error'
				}
			},
			loadRecords() {
				return this.reloadRecords()
			},
			/** POST /api/user/balance/recharge：模拟充值，写 RECHARGE 流水 */
			async recharge() {
				if (this.submitting || !this.payAmount) return
				if (this.payAmount <= 0 || this.payAmount > 10000) {
					uni.showToast({ title: '充值金额需在 0-10000 元之间', icon: 'none' })
					return
				}
				uni.showModal({
					title: '确认充值',
					content: `本次充值 ¥${money(this.payAmount)}（平台内部模拟充值，不产生真实扣款）`,
					confirmText: '确认充值',
					cancelText: '取消',
					success: async (r) => {
						if (!r.confirm) return
						this.submitting = true
						try {
							const res = await apiRecharge(this.payAmount, { loading: true })
							this.balance = Number(res.balance) || 0
							uni.showToast({ title: '充值成功', icon: 'success' })
							this.customAmount = ''
							this.amount = 100
							this.reloadRecords()
						} catch (e) {
							if (e && e.code === 2018) uni.showToast({ title: '充值金额必须大于 0', icon: 'none' })
						} finally {
							this.submitting = false
						}
					}
				})
			}
		}
	}
</script>

<style scoped>
	.balance-card {
		margin: 24rpx 32rpx 0; padding: 40rpx 32rpx;
		background: var(--c-primary); border-radius: var(--radius-card);
		box-shadow: var(--shadow-card); display: flex; flex-direction: column;
	}
	.balance-label { font-size: 26rpx; color: rgba(255, 255, 255, 0.9); }
	.balance-num { font-size: 72rpx; font-weight: 600; color: #FFFFFF; margin-top: 12rpx; }
	.balance-tip { font-size: 22rpx; color: rgba(255, 255, 255, 0.8); margin-top: 16rpx; }

	.card { margin: 24rpx 32rpx 0; }
	.amount-grid { display: flex; flex-direction: row; flex-wrap: wrap; margin-top: 24rpx; }
	.amount-cell {
		width: calc(25% - 12rpx); margin: 0 16rpx 16rpx 0;
		height: 88rpx; display: flex; align-items: center; justify-content: center;
		background: var(--c-bg-page); border: 2rpx solid transparent;
		border-radius: 16rpx;
	}
	.amount-cell:nth-child(4n) { margin-right: 0; }
	.amount-cell.active { background: var(--c-primary-weak); border-color: var(--c-primary); }
	.amount-text { font-size: 30rpx; color: var(--c-text-title); font-weight: 500; }
	.amount-cell.active .amount-text { color: var(--c-primary); }
	.amount-custom { padding: 0 16rpx; }
	.amount-input { width: 100%; text-align: center; font-size: 28rpx; }
	.recharge-btn { margin-top: 16rpx; }

	.rec { display: flex; flex-direction: row; padding: 24rpx 0; border-bottom: 1px solid var(--c-border); }
	.rec:last-child { border-bottom: none; }
	.rec-sub { display: block; margin-top: 8rpx; }
	.rec-amount-wrap { display: flex; flex-direction: column; align-items: flex-end; margin-left: 16rpx; }
	.rec-amount { font-size: 34rpx; font-weight: 600; color: var(--c-text-title); }
	.rec-amount.plus { color: var(--c-success); }
	.rec-after { margin-top: 8rpx; }
	.load-more { display: block; text-align: center; color: var(--c-text-weak); font-size: 24rpx; padding: 24rpx 0; }
	.foot-space { height: 40rpx; }
</style>
