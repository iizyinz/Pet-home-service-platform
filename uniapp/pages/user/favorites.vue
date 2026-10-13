<template>
	<view class="page fav-page">
		<state-view v-if="state === 'loading'" state="loading" :rows="3" />
		<state-view v-else-if="state === 'error'" state="error" :error-text="errorText" @retry="load" />

		<block v-else>
			<view class="page-pad">
				<view class="fav-card" v-for="s in list" :key="s.id" @tap="goDetail(s.id)">
					<image class="fav-avatar" :src="s.avatar || placeholder" mode="aspectFill" />
					<view class="fav-info flex-1">
						<view class="row">
							<text class="t-body fav-name">{{ s.nickname }}</text>
							<text class="tag" v-if="s.certStatus === 1">已认证</text>
						</view>
						<text class="t-weak fav-meta">{{ s.areaName || '区域未标注' }} · 评分 {{ ratingText(s.avgRating) }} · 完成 {{ s.completedCount || 0 }} 单</text>
					</view>
					<view class="fav-btn" @tap.stop="unfavorite(s)">取消收藏</view>
				</view>

				<view v-if="!list.length" class="state-view">
					<view class="state-icon empty-icon">
						<image class="state-img" src="/static/icons/state-empty.png" mode="aspectFit" />
					</view>
					<text class="state-text">还没有收藏服务师</text>
					<button class="btn btn-outline btn-sm state-action" @tap="goStaff">去发现服务师</button>
				</view>
			</view>
		</block>
	</view>
</template>

<script>
	import StateView from '../../components/state-view/state-view.vue'
	import { apiFavorites, apiToggleFavorite } from '../../api/index'
	import { ensureLogin } from '../../utils/auth'

	const PLACEHOLDER = '/static/logo.png'

	export default {
		components: { StateView },
		data() {
			return {
				state: 'loading',
				errorText: '',
				list: [],
				placeholder: PLACEHOLDER,
				lock: false
			}
		},
		onShow() {
			if (!ensureLogin()) return
			this.load()
		},
		methods: {
			ratingText(v) {
				return v === null || v === undefined ? '暂无' : Number(v).toFixed(1)
			},
			async load() {
				this.state = 'loading'
				try {
					this.list = (await apiFavorites()) || []
					this.state = 'ready'
				} catch (e) {
					this.state = 'error'
					this.errorText = (e && e.message) || '收藏列表加载失败'
				}
			},
			goDetail(id) {
				uni.navigateTo({ url: '/pages/staff/detail?id=' + id })
			},
			goStaff() {
				uni.navigateTo({ url: '/pages/staff/list' })
			},
			/** 收藏接口为幂等切换：再次调用即取消 */
			async unfavorite(s) {
				if (this.lock) return
				this.lock = true
				try {
					const res = await apiToggleFavorite(s.id)
					if (!res.favorited) {
						this.list = this.list.filter((x) => x.id !== s.id)
						uni.showToast({ title: '已取消收藏', icon: 'none' })
					}
				} catch (e) { /* 请求层已提示 */ }
				finally { this.lock = false }
			}
		}
	}
</script>

<style scoped>
	.fav-card {
		display: flex; flex-direction: row; align-items: center;
		background: var(--c-bg-card); border-radius: var(--radius-card);
		padding: 28rpx; margin-top: 24rpx; box-shadow: var(--shadow-card);
	}
	.fav-avatar { width: 96rpx; height: 96rpx; border-radius: 50%; background: #F2F3F5; flex-shrink: 0; }
	.fav-info { margin-left: 20rpx; display: flex; flex-direction: column; min-width: 0; }
	.fav-name { font-weight: 600; margin-right: 12rpx; }
	.fav-meta { display: block; margin-top: 10rpx; }
	.fav-btn {
		font-size: 26rpx; color: var(--c-text-secondary);
		border: 2rpx solid var(--c-border); border-radius: 30rpx;
		padding: 12rpx 24rpx; flex-shrink: 0;
	}
	.empty-icon { background: #F2F3F5; width: 96rpx; height: 96rpx; display: flex; align-items: center; justify-content: center; border-radius: 50%; margin: 0 auto; }
	.state-img { width: 56rpx; height: 56rpx; }
	.state-text { display: block; text-align: center; color: var(--c-text-weak); font-size: 28rpx; margin-top: 24rpx; }
	.state-action { display: inline-block; margin-top: 32rpx; }
</style>
