<template>
	<view class="page sd-page">
		<state-view v-if="state === 'loading'" state="loading" :rows="4" />
		<state-view v-else-if="state === 'error'" state="error" :error-text="errorText" @retry="load" />

		<block v-else-if="staff">
			<!-- 头部资料卡 -->
			<view class="head">
				<image class="head-avatar" :src="staff.avatar || placeholder" mode="aspectFill" />
				<view class="head-info">
					<view class="row">
						<text class="head-name ellipsis">{{ staff.nickname }}</text>
						<text v-if="staff.certStatus === 1" class="tag">已认证</text>
					</view>
					<text class="head-meta">{{ staff.areaName || '区域未标注' }} · 从业 {{ staff.experienceYears || 0 }} 年</text>
				</view>
				<view class="fav" :class="{ on: staff.favorited }" @tap="toggleFavorite">
					<text class="fav-icon">{{ staff.favorited ? '★' : '☆' }}</text>
					<text class="fav-text">{{ staff.favorited ? '已收藏' : '收藏' }}</text>
				</view>
			</view>

			<!-- 数据指标 -->
			<view class="stat-row">
				<view class="stat">
					<text class="stat-num">{{ ratingText(staff.avgRating) }}</text>
					<text class="stat-label">综合评分</text>
				</view>
				<view class="stat">
					<text class="stat-num">{{ staff.completedCount || 0 }}</text>
					<text class="stat-label">累计完成</text>
				</view>
				<view class="stat">
					<text class="stat-num">{{ staff.experienceYears || 0 }}</text>
					<text class="stat-label">从业年限</text>
				</view>
			</view>

			<!-- 个人简介 -->
			<view class="card">
				<text class="t-module">个人简介</text>
				<text class="t-body block-text" v-if="staff.intro">{{ staff.intro }}</text>
				<text class="t-weak block-text" v-else>服务师暂未填写简介</text>
			</view>

			<!-- 擅长品种 -->
			<view class="card">
				<text class="t-module">擅长品种</text>
				<view class="row wrap-row">
					<text class="tag" v-for="b in staff.goodAtBreeds || []" :key="b">{{ b }}</text>
					<text class="t-weak" v-if="!(staff.goodAtBreeds || []).length">暂未标注</text>
				</view>
			</view>

			<!-- 资质证书 -->
			<view class="card">
				<text class="t-module">资质证书</text>
				<view class="row wrap-row" v-if="(staff.certImages || []).length">
					<image
						class="cert-img"
						v-for="(img, i) in staff.certImages"
						:key="i"
						:src="img"
						mode="aspectFill"
						@tap="preview(staff.certImages, i)"
					/>
				</view>
				<text class="t-weak block-text" v-else-if="staff.certStatus === 1">已完成平台认证，证书原件由平台留存</text>
				<text class="t-weak block-text" v-else>暂无证书图片</text>
			</view>

			<!-- 可服务项目 -->
			<view class="card">
				<text class="t-module">可服务项目</text>
				<view class="item-chips" v-if="(staff.itemNames || []).length">
					<text class="item-chip" v-for="(n, i) in staff.itemNames" :key="i">{{ n }}</text>
				</view>
				<text class="t-weak block-text" v-else>暂无可预约项目</text>
			</view>

			<!-- 服务评价 -->
			<view class="card">
				<view class="row-between">
					<text class="t-module">服务评价</text>
					<text class="link" v-if="reviews.length" @tap="showAllReviews">全部评价 ›</text>
				</view>

				<state-view v-if="reviewState === 'loading'" state="loading" :rows="2" />
				<state-view v-else-if="reviewState === 'error'" state="error" error-text="评价加载失败" @retry="loadReviews" />
				<block v-else-if="reviews.length">
					<view class="review" v-for="r in reviews" :key="r.id">
						<view class="row-between">
							<text class="stars">{{ stars(r.rating) }}</text>
							<text class="t-weak">{{ dateShort(r.createdAt) }}</text>
						</view>
						<text class="t-body review-text">{{ r.content || '用户未填写文字评价' }}</text>
					</view>
				</block>
				<text v-else class="t-weak block-text">暂无评价</text>
			</view>

			<view class="foot-space"></view>

			<!-- 底部操作栏 -->
			<view class="action-bar">
				<view class="action-item" @tap="goChat">
					<image class="action-icon" src="/static/icons/chat.png" mode="aspectFit" />
					<text class="action-text">私聊</text>
				</view>
				<view class="action-item" @tap="toggleFavorite">
					<image class="action-icon" :src="staff.favorited ? '/static/icons/favorite-on.png' : '/static/icons/favorite.png'" mode="aspectFit" />
					<text class="action-text">{{ staff.favorited ? '已收藏' : '收藏' }}</text>
				</view>
				<button class="btn btn-primary flex-1 action-main" @tap="book" :class="{ 'btn-disabled': !(staff.itemIds || []).length }" :disabled="!(staff.itemIds || []).length">
					{{ (staff.itemIds || []).length ? '立即预约' : '暂无可约项目' }}
				</button>
			</view>
		</block>
	</view>
</template>

<script>
	import StateView from '../../components/state-view/state-view.vue'
	import { apiStaffDetail, apiStaffReviews, apiToggleFavorite } from '../../api/index'
	import { dateShort } from '../../utils/format'
	import { ensureLogin } from '../../utils/auth'

	const PLACEHOLDER = '/static/logo.png'

	export default {
		components: { StateView },
		data() {
			return {
				state: 'loading',
				errorText: '',
				id: null,
				itemId: null,
				staff: null,
				reviewState: 'loading',
				reviews: [],
				placeholder: PLACEHOLDER,
				favLoading: false
			}
		},
		onLoad(options) {
			this.id = options && options.id ? Number(options.id) : null
			this.itemId = options && options.itemId ? Number(options.itemId) : null
			if (!this.id) {
				this.state = 'error'
				this.errorText = '参数缺失，请返回重试'
				return
			}
			this.load()
			this.loadReviews()
		},
		methods: {
			dateShort,
			ratingText(v) {
				return v === null || v === undefined ? '暂无' : Number(v).toFixed(1)
			},
			stars(n) {
				const r = Number(n) || 0
				return '★★★★★'.slice(0, r) + '☆☆☆☆☆'.slice(0, 5 - r)
			},
			async load() {
				this.state = 'loading'
				try {
					this.staff = await apiStaffDetail(this.id)
					this.state = 'ready'
				} catch (e) {
					this.state = 'error'
					this.errorText = (e && e.message) || '服务人员详情加载失败'
				}
			},
			async loadReviews() {
				this.reviewState = 'loading'
				try {
					const res = await apiStaffReviews(this.id, 1, 3)
					this.reviews = (res && res.list) || []
					this.reviewState = 'ready'
				} catch (e) {
					this.reviewState = 'error'
				}
			},
			/** 收藏：POST /api/staffs/{id}/favorite 幂等切换，返回 { favorited } */
			async toggleFavorite() {
				if (this.favLoading) return
				if (!ensureLogin('/pages/staff/detail?id=' + this.id)) return
				this.favLoading = true
				try {
					const res = await apiToggleFavorite(this.id)
					this.staff.favorited = res.favorited
					uni.showToast({ title: res.favorited ? '已收藏' : '已取消收藏', icon: 'none' })
				} catch (e) {
					// 错误提示由请求层统一 toast
				} finally {
					this.favLoading = false
				}
			},
			book() {
				if (!ensureLogin('/pages/staff/detail?id=' + this.id)) return
				if (!(this.staff.itemIds || []).length) return
				const query = this.itemId
					? 'serviceItemId=' + this.itemId + '&staffId=' + this.id
					: 'staffId=' + this.id
				uni.navigateTo({ url: '/pages/order/create?' + query })
			},
			/** 私聊：先创建会话占位，由后端按参与者对聚合 */
			async goChat() {
				if (!ensureLogin('/pages/staff/detail?id=' + this.id)) return
				uni.navigateTo({ url: '/pages/chat/detail?peerId=' + this.id + '&peerName=' + encodeURIComponent(this.staff.nickname) })
			},
			showAllReviews() {
				uni.showToast({ title: 'V1 版本仅展示最近 3 条评价', icon: 'none' })
			},
			preview(urls, current) {
				uni.previewImage({ urls, current: urls[current] })
			}
		}
	}
</script>

<style scoped>
	.sd-page { padding-bottom: calc(140rpx + env(safe-area-inset-bottom)); }
	.head {
		display: flex; flex-direction: row; align-items: center;
		background: #FFFFFF; padding: 32rpx;
	}
	.head-avatar { width: 128rpx; height: 128rpx; border-radius: 50%; background: #F2F3F5; flex-shrink: 0; }
	.head-info { flex: 1; min-width: 0; margin-left: 24rpx; display: flex; flex-direction: column; }
	.head-name { font-size: 36rpx; font-weight: 600; max-width: 320rpx; }
	.head-name + .tag { margin-left: 12rpx; flex-shrink: 0; }
	.head-meta { font-size: 26rpx; color: var(--c-text-weak); margin-top: 12rpx; }
	.fav { display: flex; flex-direction: column; align-items: center; padding: 8rpx 12rpx; }
	.fav-icon { font-size: 40rpx; color: var(--c-text-weak); }
	.fav.on .fav-icon { color: var(--c-primary); }
	.fav-text { font-size: 22rpx; color: var(--c-text-weak); margin-top: 4rpx; }
	.fav.on .fav-text { color: var(--c-primary); }

	.stat-row {
		display: flex; flex-direction: row;
		background: #FFFFFF; margin-top: 2rpx; padding: 24rpx 0;
	}
	.stat { flex: 1; display: flex; flex-direction: column; align-items: center; }
	.stat + .stat { border-left: 1px solid var(--c-border); }
	.stat-num { font-size: 40rpx; font-weight: 600; color: var(--c-primary); }
	.stat-label { font-size: 24rpx; color: var(--c-text-weak); margin-top: 8rpx; }

	.card { margin: 24rpx 32rpx 0; }
	.block-text { display: block; margin-top: 20rpx; line-height: 1.7; }
	.wrap-row { margin-top: 20rpx; flex-wrap: wrap; }
	.cert-img { width: 160rpx; height: 160rpx; border-radius: 12rpx; background: #F2F3F5; margin: 0 16rpx 16rpx 0; }
	.item-chips { display: flex; flex-direction: row; flex-wrap: wrap; margin-top: 20rpx; }
	.item-chip {
		font-size: 26rpx; color: var(--c-text-secondary); background: var(--c-bg-page);
		padding: 14rpx 24rpx; border-radius: 16rpx; margin: 0 16rpx 16rpx 0;
	}
	.link { color: var(--c-info); font-size: 26rpx; }

	.review { padding: 24rpx 0; border-bottom: 1px solid var(--c-border); }
	.review:last-child { border-bottom: none; padding-bottom: 0; }
	.stars { color: var(--c-warning); font-size: 28rpx; letter-spacing: 4rpx; }
	.review-text { display: block; margin-top: 12rpx; color: var(--c-text-secondary); line-height: 1.7; }

	.foot-space { height: 32rpx; }
	.action-bar {
		position: fixed; left: 0; right: 0; bottom: 0;
		background: #FFFFFF; box-shadow: var(--shadow-card);
		display: flex; flex-direction: row; align-items: center;
		padding: 16rpx 32rpx calc(16rpx + env(safe-area-inset-bottom));
		z-index: 20;
	}
	.action-item {
		width: 96rpx; display: flex; flex-direction: column; align-items: center;
		margin-right: 16rpx;
	}
	.action-icon {
		width: 44rpx; height: 44rpx;
		background: var(--c-primary-weak); border-radius: 14rpx; padding: 6rpx;
	}
	.action-text { font-size: 22rpx; color: var(--c-text-secondary); margin-top: 6rpx; }
	.action-main { flex: 1; }
</style>
