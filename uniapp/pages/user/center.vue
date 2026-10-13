<template>
	<view class="page center-page">
		<scroll-view scroll-y class="center-scroll">
			<!-- 用户信息头部 -->
			<view class="head" @tap="goProfile">
				<image class="head-avatar" :src="user.avatar || placeholder" mode="aspectFill" />
				<view class="head-info flex-1">
					<text class="head-name ellipsis">{{ user.nickname || user.username || '未登录' }}</text>
					<text class="head-phone">{{ user.phone || '未绑定手机号' }}</text>
				</view>
				<text class="head-arrow">›</text>
			</view>

			<!-- 余额卡 -->
			<view class="balance-card" @tap="goBalance">
				<view class="flex-1">
					<text class="balance-label">账户余额（元）</text>
					<text class="balance-num">{{ money(user.balance) }}</text>
				</view>
				<view class="recharge-btn">充值</view>
			</view>

			<!-- 我的订单 -->
			<view class="card">
				<view class="row-between" @tap="goOrders('ALL')">
					<text class="t-module">我的订单</text>
					<text class="link">全部订单 ›</text>
				</view>
				<view class="order-grid">
					<view class="grid-item" v-for="t in orderEntries" :key="t.key" @tap="goOrders(t.key)">
						<view class="grid-icon" :class="{ warn: t.warn }">
							<image class="grid-icon-img" :src="t.icon" mode="aspectFit" />
							<view class="grid-dot" v-if="t.dot"></view>
						</view>
						<text class="grid-text">{{ t.label }}</text>
					</view>
				</view>
			</view>

			<!-- 功能入口 -->
			<view class="card menu-card">
				<view class="menu-item" v-for="m in menus" :key="m.label" @tap="m.action">
					<view class="menu-left">
						<image class="menu-icon" :src="m.icon" mode="aspectFit" />
						<text class="menu-label">{{ m.label }}</text>
					</view>
					<view class="row">
						<text class="menu-extra" v-if="m.extra">{{ m.extra }}</text>
						<text class="head-arrow">›</text>
					</view>
				</view>
			</view>

			<!-- 退出登录 -->
			<button class="btn btn-outline btn-block logout-btn" @tap="logout">退出登录</button>

			<view class="foot-space"></view>
		</scroll-view>
	</view>
</template>

<script>
	import { apiProfile } from '../../api/index'
	import { money } from '../../utils/format'
	import { getLoginUser, clearAuth, ensureLogin } from '../../utils/auth'

	const PLACEHOLDER = '/static/logo.png'

	export default {
		data() {
			return {
				user: {},
				placeholder: PLACEHOLDER,
				orderEntries: [
					{ key: 'PENDING_CONFIRM', label: '待确认', warn: true, dot: true, icon: '/static/icons/order-pending.png' },
					{ key: 'CONFIRMED', label: '已确认', icon: '/static/icons/order-confirmed.png' },
					{ key: 'SERVING', label: '服务中', icon: '/static/icons/order-serving.png' },
					{ key: 'PENDING_SETTLE', label: '待完成', icon: '/static/icons/order-settling.png' },
					{ key: 'COMPLETED', label: '已完成', icon: '/static/icons/order-done.png' }
				]
			}
		},
		computed: {
			menus() {
				return [
					{ label: '宠物档案', icon: '/static/icons/menu-pets.png', action: () => uni.navigateTo({ url: '/pages/user/pets' }) },
					{ label: '上门地址', icon: '/static/icons/menu-address.png', action: () => uni.navigateTo({ url: '/pages/user/addresses' }) },
					{ label: '我的收藏', icon: '/static/icons/menu-favorites.png', action: () => uni.navigateTo({ url: '/pages/user/favorites' }) },
					{ label: '我的评价', icon: '/static/icons/menu-reviews.png', action: () => uni.navigateTo({ url: '/pages/user/reviews' }) },
					{ label: '余额与流水', icon: '/static/icons/menu-balance.png', extra: '¥' + money(this.user.balance), action: () => uni.navigateTo({ url: '/pages/user/balance' }) },
					{ label: 'AI 宠物顾问', icon: '/static/icons/menu-ai.png', action: () => uni.navigateTo({ url: '/pages/ai/assistant' }) },
					{ label: '个人信息', icon: '/static/icons/menu-profile.png', action: () => uni.navigateTo({ url: '/pages/user/profile' }) }
				]
			}
		},
		onShow() {
			if (!ensureLogin()) return
			this.user = getLoginUser() || {}
			this.loadProfile()
		},
		methods: {
			money,
			async loadProfile() {
				try {
					const p = await apiProfile()
					this.user = p
					uni.setStorageSync('loginUser', p)
				} catch (e) {
					// 401 由请求层跳登录；其它失败保留本地缓存展示
				}
			},
			goProfile() {
				uni.navigateTo({ url: '/pages/user/profile' })
			},
			goBalance() {
				uni.navigateTo({ url: '/pages/user/balance' })
			},
			goOrders(status) {
				// tabBar 页面 switchTab 不支持参数，先落缓存，订单页 onShow 消费
				uni.setStorageSync('orderTabStatus', status)
				uni.switchTab({ url: '/pages/order/list' })
			},
			logout() {
				uni.showModal({
					title: '退出登录',
					content: '确定退出当前账号吗？',
					confirmText: '退出',
					confirmColor: '#FF4D4F',
					cancelText: '取消',
					success: (r) => {
						if (!r.confirm) return
						clearAuth()
						uni.reLaunch({ url: '/pages/login/login' })
					}
				})
			}
		}
	}
</script>

<style scoped>
	.center-scroll { height: 100vh; }
	.head {
		display: flex; flex-direction: row; align-items: center;
		background: #FFFFFF; padding: 40rpx 32rpx;
	}
	.head-avatar { width: 120rpx; height: 120rpx; border-radius: 50%; background: #F2F3F5; flex-shrink: 0; }
	.head-info { margin-left: 24rpx; display: flex; flex-direction: column; }
	.head-name { font-size: 36rpx; font-weight: 600; color: var(--c-text-title); }
	.head-phone { font-size: 26rpx; color: var(--c-text-weak); margin-top: 10rpx; }
	.head-arrow { color: var(--c-text-weak); font-size: 36rpx; margin-left: 12rpx; }

	.balance-card {
		display: flex; flex-direction: row; align-items: center;
		margin: 24rpx 32rpx 0; padding: 32rpx;
		background: var(--c-primary); border-radius: var(--radius-card);
		box-shadow: var(--shadow-card);
	}
	.balance-label { font-size: 24rpx; color: rgba(255, 255, 255, 0.85); }
	.balance-num { display: block; font-size: 56rpx; font-weight: 600; color: #FFFFFF; margin-top: 8rpx; }
	.recharge-btn {
		background: #FFFFFF; color: var(--c-primary);
		font-size: 28rpx; font-weight: 500;
		padding: 16rpx 40rpx; border-radius: 40rpx;
	}

	.card { margin: 24rpx 32rpx 0; }
	.link { color: var(--c-info); font-size: 26rpx; }
	.order-grid { display: flex; flex-direction: row; margin-top: 28rpx; }
	.grid-item { flex: 1; display: flex; flex-direction: column; align-items: center; }
	.grid-icon {
		width: 72rpx; height: 72rpx;
		display: flex; align-items: center; justify-content: center;
		background: var(--c-primary-weak); color: var(--c-primary);
		border-radius: 24rpx; position: relative;
	}
	.grid-icon.warn { background: #FFF7E6; }
	.grid-icon-img { width: 40rpx; height: 40rpx; }
	.grid-dot {
		position: absolute; top: -6rpx; right: -6rpx;
		width: 16rpx; height: 16rpx; border-radius: 50%; background: var(--c-error);
	}
	.grid-text { font-size: 24rpx; color: var(--c-text-secondary); margin-top: 12rpx; }

	.menu-card { padding: 8rpx 32rpx; }
	.menu-item {
		display: flex; flex-direction: row; align-items: center; justify-content: space-between;
		min-height: 96rpx; border-bottom: 1px solid var(--c-border);
	}
	.menu-item:last-child { border-bottom: none; }
	.menu-left { display: flex; flex-direction: row; align-items: center; min-width: 0; }
	.menu-icon { width: 44rpx; height: 44rpx; margin-right: 20rpx; flex-shrink: 0; }
	.menu-label { font-size: 30rpx; color: var(--c-text-title); }
	.menu-extra { font-size: 28rpx; color: var(--c-text-weak); }

	.logout-btn { margin: 40rpx 32rpx 0; color: var(--c-text-secondary); }
	.foot-space { height: 40rpx; }
</style>
