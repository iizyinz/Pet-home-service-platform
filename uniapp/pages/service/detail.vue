<template>
	<view class="page detail-page">
		<state-view v-if="state === 'loading'" state="loading" :rows="4" />

		<state-view v-else-if="state === 'error'" state="error" :error-text="errorText" @retry="load" />

		<block v-else-if="item">
			<!-- 服务封面 -->
			<view class="page-pad">
				<image class="cover" :src="item.coverUrl || placeholder" mode="aspectFill" />
			</view>

			<!-- 基本信息 -->
			<view class="card page-pad-margin">
				<view class="row-between">
					<text class="t-title flex-1 ellipsis-2">{{ item.name }}</text>
				</view>
				<view class="row price-row">
					<view class="price-wrap">
						<text class="price-unit">¥</text>
						<text class="t-price-lg">{{ money(item.price) }}</text>
					</view>
					<text class="t-sub" v-if="item.durationMinutes">时长 {{ item.durationMinutes }} 分钟</text>
					<text class="tag" v-if="typeName">{{ typeName }}</text>
				</view>
			</view>

			<!-- 适用品种 -->
			<view class="card">
				<text class="t-module">适用品种</text>
				<text class="t-body block-text" v-if="item.applicableBreeds">{{ item.applicableBreeds }}</text>
				<text class="t-weak block-text" v-else>不限品种</text>
			</view>

			<!-- 注意事项 -->
			<view class="card">
				<text class="t-module">注意事项</text>
				<text class="t-body block-text" v-if="item.notice">{{ item.notice }}</text>
				<text class="t-weak block-text" v-else>暂无特别注意事项</text>
			</view>

			<!-- 服务流程（静态说明，PRD 5.3 服务流程展示） -->
			<view class="card">
				<text class="t-module">服务流程</text>
				<view class="flow">
					<view class="flow-step" v-for="(s, i) in flow" :key="i">
						<view class="flow-dot"><text class="flow-num">{{ i + 1 }}</text></view>
						<view class="flow-body">
							<text class="flow-title">{{ s.title }}</text>
							<text class="t-weak">{{ s.desc }}</text>
						</view>
					</view>
				</view>
			</view>

			<view class="foot-space"></view>

			<!-- 底部操作区 -->
			<view class="action-bar">
				<view class="action-item" @tap="goStaffList">
					<image class="action-icon" src="/static/icons/people.png" mode="aspectFit" />
					<text class="action-text">选服务师</text>
				</view>
				<button class="btn btn-primary flex-1 action-main" :class="{ 'btn-disabled': submitting }" :disabled="submitting" @tap="book">
					{{ submitting ? '提交中...' : '立即预约' }}
				</button>
			</view>
		</block>
	</view>
</template>

<script>
	import StateView from '../../components/state-view/state-view.vue'
	import { apiServiceItemDetail } from '../../api/index'
	import { money } from '../../utils/format'
	import { ensureLogin } from '../../utils/auth'

	const PLACEHOLDER = '/static/logo.png'

	export default {
		components: { StateView },
		data() {
			return {
				state: 'loading',
				errorText: '',
				id: null,
				item: null,
				typeName: '',
				submitting: false,
				placeholder: PLACEHOLDER,
				flow: [
					{ title: '提交预约', desc: '选择宠物、服务师、地址与时间，余额支付' },
					{ title: '服务师接单', desc: '接单后可在订单详情查看服务进度' },
					{ title: '上门服务', desc: '服务过程图文反馈，全程可追溯' },
					{ title: '确认完成', desc: '确认后可对本次服务进行评价' }
				]
			}
		},
		onLoad(options) {
			this.id = options && options.id ? Number(options.id) : null
			if (!this.id) {
				this.state = 'error'
				this.errorText = '参数缺失，请返回重试'
				return
			}
			this.load()
		},
		methods: {
			money,
			async load() {
				this.state = 'loading'
				try {
					this.item = await apiServiceItemDetail(this.id)
					this.typeName = (this.item && this.item.typeName) || ''
					this.state = 'ready'
				} catch (e) {
					this.state = 'error'
					this.errorText = (e && e.message) || '服务详情加载失败'
				}
			},
			book() {
				if (this.submitting) return
				if (!ensureLogin('/pages/service/detail?id=' + this.id)) return
				uni.navigateTo({ url: '/pages/order/create?serviceItemId=' + this.id })
			},
			goStaffList() {
				if (!ensureLogin('/pages/service/detail?id=' + this.id)) return
				uni.navigateTo({ url: '/pages/staff/list?itemId=' + this.id })
			}
		}
	}
</script>

<style scoped>
	.detail-page { padding-bottom: calc(140rpx + env(safe-area-inset-bottom)); }
	.cover { width: 100%; height: 360rpx; border-radius: var(--radius-card); background: #F2F3F5; }
	.card { margin: 24rpx 32rpx 0; }
	.price-row { margin-top: 20rpx; }
	.price-wrap { display: flex; flex-direction: row; align-items: baseline; margin-right: 24rpx; }
	.price-unit { font-size: 26rpx; color: var(--c-primary); margin-right: 4rpx; }
	.block-text { display: block; margin-top: 20rpx; line-height: 1.7; }

	.flow { margin-top: 24rpx; }
	.flow-step { display: flex; flex-direction: row; position: relative; padding-bottom: 28rpx; }
	.flow-step:last-child { padding-bottom: 0; }
	.flow-step::before {
		content: ''; position: absolute; left: 23rpx; top: 48rpx; bottom: 4rpx;
		width: 2rpx; background: var(--c-border);
	}
	.flow-step:last-child::before { display: none; }
	.flow-dot {
		width: 48rpx; height: 48rpx; border-radius: 50%; background: var(--c-primary-weak);
		display: flex; align-items: center; justify-content: center; flex-shrink: 0;
	}
	.flow-num { color: var(--c-primary); font-size: 24rpx; font-weight: 600; }
	.flow-body { margin-left: 20rpx; display: flex; flex-direction: column; }
	.flow-title { font-size: 28rpx; color: var(--c-text-title); font-weight: 500; }
	.flow-body .t-weak { margin-top: 6rpx; }

	.foot-space { height: 32rpx; }
	.action-bar {
		position: fixed; left: 0; right: 0; bottom: 0;
		background: #FFFFFF; box-shadow: var(--shadow-card);
		display: flex; flex-direction: row; align-items: center;
		padding: 16rpx 32rpx calc(16rpx + env(safe-area-inset-bottom));
		z-index: 20;
	}
	.action-item {
		width: 120rpx; display: flex; flex-direction: column; align-items: center;
		margin-right: 20rpx;
	}
	.action-icon {
		width: 44rpx; height: 44rpx;
		background: var(--c-primary-weak); border-radius: 14rpx; padding: 6rpx;
	}
	.action-text { font-size: 22rpx; color: var(--c-text-secondary); margin-top: 6rpx; }
	.action-main { flex: 1; }
</style>
