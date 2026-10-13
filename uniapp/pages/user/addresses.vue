<template>
	<view class="page addr-page">
		<state-view v-if="state === 'loading'" state="loading" :rows="3" />
		<state-view v-else-if="state === 'error'" state="error" :error-text="errorText" @retry="load" />

		<block v-else>
			<view class="page-pad">
				<view class="addr-card" v-for="a in addresses" :key="a.id">
					<view class="flex-1" @tap="edit(a)">
						<view class="row">
							<text class="t-body addr-name">{{ a.contactName }}</text>
							<text class="t-sub addr-phone">{{ a.contactPhone }}</text>
							<text class="tag" v-if="a.isDefault === 1">默认</text>
						</view>
						<text class="t-sub addr-detail ellipsis-2">{{ a.region }} {{ a.detail }}</text>
					</view>
					<view class="addr-actions">
						<text class="addr-action" @tap="edit(a)">编辑</text>
						<text class="addr-action" @tap="setDefault(a)" v-if="a.isDefault !== 1">设为默认</text>
						<text class="addr-action danger" @tap="confirmDelete(a)">删除</text>
					</view>
				</view>

				<view v-if="!addresses.length" class="state-view">
					<view class="state-icon empty-icon">
						<image class="state-img" src="/static/icons/state-empty.png" mode="aspectFit" />
					</view>
					<text class="state-text">还没有上门地址</text>
					<text class="state-text sub">添加地址后下单可直接选择</text>
				</view>
			</view>

			<view class="action-bar">
				<button class="btn btn-primary btn-block" @tap="goCreate">添加上门地址</button>
			</view>
		</block>
	</view>
</template>

<script>
	import StateView from '../../components/state-view/state-view.vue'
	import { apiAddresses, apiDeleteAddress, apiUpdateAddress } from '../../api/index'
	import { ensureLogin } from '../../utils/auth'

	export default {
		components: { StateView },
		data() {
			return {
				state: 'loading',
				errorText: '',
				addresses: [],
				lock: false
			}
		},
		onShow() {
			if (!ensureLogin()) return
			this.load()
		},
		methods: {
			async load() {
				this.state = 'loading'
				try {
					this.addresses = (await apiAddresses()) || []
					this.state = 'ready'
				} catch (e) {
					this.state = 'error'
					this.errorText = (e && e.message) || '地址列表加载失败'
				}
			},
			edit(a) {
				uni.navigateTo({ url: '/pages/user/address-edit?id=' + a.id })
			},
			goCreate() {
				uni.navigateTo({ url: '/pages/user/address-edit' })
			},
			/** 设默认：PUT /api/addresses/{id} 仅回传 isDefault 标记 */
			async setDefault(a) {
				if (this.lock) return
				this.lock = true
				try {
					await apiUpdateAddress(a.id, {
						contactName: a.contactName,
						contactPhone: a.contactPhone,
						region: a.region,
						detail: a.detail,
						isDefault: 1
					})
					uni.showToast({ title: '已设为默认', icon: 'success' })
					this.load()
				} catch (e) { /* 请求层已提示 */ }
				finally { this.lock = false }
			},
			confirmDelete(a) {
				uni.showModal({
					title: '删除地址',
					content: '确定删除该上门地址吗？',
					confirmText: '删除',
					confirmColor: '#FF4D4F',
					cancelText: '取消',
					success: async (r) => {
						if (!r.confirm || this.lock) return
						this.lock = true
						try {
							await apiDeleteAddress(a.id)
							uni.showToast({ title: '已删除', icon: 'success' })
							this.load()
						} catch (e) { /* 请求层已提示 */ }
						finally { this.lock = false }
					}
				})
			}
		}
	}
</script>

<style scoped>
	.addr-page { padding-bottom: calc(140rpx + env(safe-area-inset-bottom)); }
	.addr-card {
		background: var(--c-bg-card); border-radius: var(--radius-card);
		padding: 28rpx; margin-top: 24rpx; box-shadow: var(--shadow-card);
	}
	.addr-name { font-weight: 600; }
	.addr-phone { margin-left: 20rpx; }
	.addr-name + .tag, .addr-phone + .tag { margin-left: 16rpx; }
	.addr-detail { display: block; margin-top: 14rpx; line-height: 1.6; }
	.addr-actions {
		display: flex; flex-direction: row; justify-content: flex-end;
		margin-top: 20rpx; padding-top: 20rpx; border-top: 1px solid var(--c-border);
	}
	.addr-action { font-size: 26rpx; color: var(--c-text-secondary); margin-left: 40rpx; }
	.addr-action.danger { color: var(--c-error); }
	.empty-icon { background: #F2F3F5; width: 96rpx; height: 96rpx; display: flex; align-items: center; justify-content: center; border-radius: 50%; margin: 0 auto; }
	.state-img { width: 56rpx; height: 56rpx; }
	.state-text { display: block; text-align: center; color: var(--c-text-weak); font-size: 28rpx; margin-top: 24rpx; }
	.state-text.sub { font-size: 24rpx; margin-top: 12rpx; }
	.action-bar {
		position: fixed; left: 0; right: 0; bottom: 0;
		background: #FFFFFF; box-shadow: var(--shadow-card);
		padding: 16rpx 32rpx calc(16rpx + env(safe-area-inset-bottom));
	}
</style>
