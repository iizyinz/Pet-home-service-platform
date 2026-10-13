<template>
	<view class="page my-review-page">
		<state-view v-if="state === 'loading'" state="loading" :rows="3" />
		<state-view v-else-if="state === 'error'" state="error" :error-text="errorText" @retry="reload" />

		<block v-else>
			<view class="page-pad">
				<view class="review-card" v-for="r in list" :key="r.id">
					<view class="row-between">
						<text class="stars">{{ stars(r.rating) }}</text>
						<text class="t-weak">{{ dateShort(r.createdAt) }}</text>
					</view>
					<text class="t-body review-content">{{ r.content || '未填写文字评价' }}</text>
					<view class="row review-imgs" v-if="images(r).length">
						<image
							class="review-img"
							v-for="(img, i) in images(r)"
							:key="i"
							:src="img"
							mode="aspectFill"
							@tap="preview(images(r), img)"
						/>
					</view>
					<text class="t-weak order-ref">订单号：{{ r.orderId ? ('SO 订单 #' + r.orderId) : '-' }}</text>
				</view>

				<text class="load-more" v-if="list.length">{{ loadingMore ? '加载中...' : (hasMore ? '上拉加载更多' : '没有更多了') }}</text>

				<view v-if="!list.length" class="state-view">
					<view class="state-icon empty-icon">
						<image class="state-img" src="/static/icons/state-empty.png" mode="aspectFit" />
					</view>
					<text class="state-text">还没有发表过评价</text>
					<button class="btn btn-outline btn-sm state-action" @tap="goOrders">去完成的订单评价</button>
				</view>
			</view>
		</block>
	</view>
</template>

<script>
	import StateView from '../../components/state-view/state-view.vue'
	import { apiReviews } from '../../api/index'
	import { dateShort } from '../../utils/format'
	import { ensureLogin } from '../../utils/auth'

	export default {
		components: { StateView },
		data() {
			return {
				state: 'loading',
				errorText: '',
				list: [],
				page: 1,
				size: 10,
				total: 0,
				hasMore: true,
				loadingMore: false
			}
		},
		onLoad() {
			if (!ensureLogin()) return
			this.reload()
		},
		onPullDownRefresh() {
			this.reload().finally(() => uni.stopPullDownRefresh())
		},
		onReachBottom() {
			this.loadMore()
		},
		methods: {
			dateShort,
			stars(n) {
				const r = Number(n) || 0
				return '★★★★★'.slice(0, r) + '☆☆☆☆☆'.slice(0, 5 - r)
			},
			images(r) {
				if (!r.images) return []
				try {
					const arr = typeof r.images === 'string' ? JSON.parse(r.images) : r.images
					return Array.isArray(arr) ? arr : []
				} catch (e) {
					return []
				}
			},
			async reload() {
				this.page = 1
				this.state = 'loading'
				try {
					// my=1 → 我发出的评价
					const res = await apiReviews({ my: 1, page: 1, size: this.size })
					this.list = res.list || []
					this.total = res.total || 0
					this.hasMore = this.list.length < this.total
					this.state = 'ready'
				} catch (e) {
					this.state = 'error'
					this.errorText = (e && e.message) || '评价加载失败'
				}
			},
			async loadMore() {
				if (this.loadingMore || !this.hasMore || this.state !== 'ready') return
				this.loadingMore = true
				try {
					const res = await apiReviews({ my: 1, page: this.page + 1, size: this.size })
					this.page += 1
					this.list = this.list.concat(res.list || [])
					this.hasMore = this.list.length < (res.total || 0)
				} catch (e) {
					uni.showToast({ title: '加载更多失败', icon: 'none' })
				} finally {
					this.loadingMore = false
				}
			},
			preview(urls, current) {
				uni.previewImage({ urls, current })
			},
			goOrders() {
				uni.setStorageSync('orderTabStatus', 'COMPLETED')
				uni.switchTab({ url: '/pages/order/list' })
			}
		}
	}
</script>

<style scoped>
	.review-card {
		background: var(--c-bg-card); border-radius: var(--radius-card);
		padding: 28rpx; margin-top: 24rpx; box-shadow: var(--shadow-card);
	}
	.stars { color: var(--c-warning); font-size: 30rpx; letter-spacing: 4rpx; }
	.review-content { display: block; margin-top: 16rpx; line-height: 1.7; color: var(--c-text-secondary); }
	.review-imgs { margin-top: 16rpx; flex-wrap: wrap; }
	.review-img { width: 152rpx; height: 152rpx; border-radius: 12rpx; background: #F2F3F5; margin: 0 16rpx 16rpx 0; }
	.order-ref { display: block; margin-top: 8rpx; }
	.load-more { display: block; text-align: center; color: var(--c-text-weak); font-size: 24rpx; padding: 24rpx 0; }
	.empty-icon { background: #F2F3F5; width: 96rpx; height: 96rpx; display: flex; align-items: center; justify-content: center; border-radius: 50%; margin: 0 auto; }
	.state-img { width: 56rpx; height: 56rpx; }
	.state-text { display: block; text-align: center; color: var(--c-text-weak); font-size: 28rpx; margin-top: 24rpx; }
	.state-action { display: inline-block; margin-top: 32rpx; }
</style>
