<template>
	<view class="page review-page">
		<state-view v-if="state === 'loading'" state="loading" :rows="3" />
		<state-view v-else-if="state === 'error'" state="error" :error-text="errorText" @retry="load" />

		<block v-else>
			<!-- 服务信息 -->
			<view class="card order-brief">
				<text class="t-body">{{ order.itemName || '服务项目' }}</text>
				<text class="t-weak brief-sub">{{ order.orderNo }} · {{ timeShort(order.appointmentTime) }}</text>
				<text class="t-weak brief-sub">服务师：{{ order.staffName || '-' }}</text>
			</view>

			<!-- 已评价 -->
			<view class="card" v-if="order.reviewed">
				<text class="t-module">该订单已评价</text>
				<text class="t-weak block-text">一单一评，感谢你的反馈</text>
				<button class="btn btn-outline btn-sm mt-16" @tap="goBack">返回订单</button>
			</view>

			<block v-else>
				<!-- 星级 -->
				<view class="card star-card">
					<text class="t-module">总体评分</text>
					<view class="row star-row">
						<view
							class="star"
							v-for="n in 5"
							:key="n"
							@tap="setRating(n)"
						>
							<text class="star-mark" :class="{ on: n <= form.rating }">★</text>
						</view>
					</view>
					<text class="star-label">{{ ratingLabel }}</text>
					<text v-if="errors.rating" class="field-error">{{ errors.rating }}</text>
				</view>

				<!-- 文字 -->
				<view class="card">
					<text class="t-module">评价内容</text>
					<textarea
						class="content-input"
						v-model="form.content"
						placeholder="说说本次服务体验（最多 500 字）"
						placeholder-class="form-input-placeholder"
						:maxlength="500"
						@input="clearError"
					/>
					<view class="row-between">
						<text v-if="errors.content" class="field-error no-margin">{{ errors.content }}</text>
						<text class="t-weak count">{{ form.content.length }}/500</text>
					</view>
				</view>

				<!-- 图片 -->
				<view class="card">
					<text class="t-module">图片（选填，最多 3 张）</text>
					<view class="row img-row">
						<view class="img-cell" v-for="(img, i) in form.images" :key="i">
							<image class="img" :src="img" mode="aspectFill" @tap="preview(i)" />
							<view class="img-del" @tap.stop="removeImg(i)">✕</view>
						</view>
						<view class="img-cell img-add" v-if="form.images.length < 3" @tap="chooseImage">
							<text class="img-add-icon">＋</text>
							<text class="img-add-text">添加图片</text>
						</view>
					</view>
				</view>

				<view class="foot-space"></view>
				<view class="action-bar">
					<button
						class="btn btn-primary btn-block"
						:class="{ 'btn-disabled': submitting }"
						:disabled="submitting"
						@tap="submit"
					>{{ submitting ? '提交中...' : '提交评价' }}</button>
				</view>
			</block>
		</block>
	</view>
</template>

<script>
	import StateView from '../../components/state-view/state-view.vue'
	import { apiOrderDetail, apiSubmitReview, apiUpload } from '../../api/index'
	import { timeShort } from '../../utils/format'
	import { ensureLogin } from '../../utils/auth'

	const RATING_LABELS = ['', '很不满意', '不满意', '一般', '满意', '非常满意']

	export default {
		components: { StateView },
		data() {
			return {
				state: 'loading',
				errorText: '',
				orderId: null,
				order: {},
				form: { rating: 0, content: '', images: [] },
				errors: {},
				submitting: false,
				uploading: false
			}
		},
		computed: {
			ratingLabel() {
				return this.form.rating ? RATING_LABELS[this.form.rating] : '请点击星级评分'
			}
		},
		onLoad(options) {
			if (!ensureLogin()) return
			this.orderId = options && options.orderId ? Number(options.orderId) : null
			if (!this.orderId) {
				this.state = 'error'
				this.errorText = '参数缺失，请返回重试'
				return
			}
			this.load()
		},
		methods: {
			timeShort,
			async load() {
				this.state = 'loading'
				try {
					this.order = await apiOrderDetail(this.orderId)
					// 服务端强约束：仅已完成订单可评（2006），前端同步拦截
					if (this.order.status !== 'COMPLETED') {
						this.state = 'error'
						this.errorText = '仅已完成订单可评价'
						return
					}
					this.state = 'ready'
				} catch (e) {
					this.state = 'error'
					this.errorText = (e && e.message) || '订单信息加载失败'
				}
			},
			setRating(n) {
				this.form.rating = n
				delete this.errors.rating
			},
			clearError() {
				if (this.errors.content) this.errors.content = ''
			},
			validate() {
				const errs = {}
				if (!this.form.rating) errs.rating = '请选择 1-5 星评分'
				if (this.form.content && this.form.content.trim().length > 500) errs.content = '评价内容最多 500 字'
				this.errors = errs
				if (Object.keys(errs).length) {
					uni.showToast({ title: Object.values(errs)[0], icon: 'none' })
					return false
				}
				return true
			},
			chooseImage() {
				if (this.uploading) return
				uni.chooseImage({
					count: 3 - this.form.images.length,
					sizeType: ['compressed'],
					success: async (res) => {
						this.uploading = true
						uni.showLoading({ title: '上传中', mask: true })
						try {
							for (const path of res.tempFilePaths) {
								const url = await apiUpload(path)
								this.form.images.push(url)
							}
						} catch (e) {
							// 单张失败由 uploadFile 统一提示
						} finally {
							uni.hideLoading()
							this.uploading = false
						}
					}
				})
			},
			removeImg(i) {
				this.form.images.splice(i, 1)
			},
			preview(i) {
				uni.previewImage({ urls: this.form.images, current: this.form.images[i] })
			},
			/**
			 * POST /api/reviews：仅已完成订单可评、一单一评（2005/2006），
			 * 成功后后端重算服务师信誉并推送通知（AC-06）
			 */
			async submit() {
				if (this.submitting) return
				if (!this.validate()) return
				this.submitting = true
				try {
					await apiSubmitReview(
						{
							orderId: this.orderId,
							rating: this.form.rating,
							content: this.form.content.trim(),
							images: this.form.images
						},
						{ loading: true }
					)
					uni.showToast({ title: '评价成功', icon: 'success' })
					setTimeout(() => this.goBack(), 800)
				} catch (e) {
					if (e && e.code === 2005) {
						uni.showModal({
							title: '已评价',
							content: '该订单已评价，不可重复提交',
							showCancel: false,
							success: () => this.goBack()
						})
					}
				} finally {
					this.submitting = false
				}
			},
			goBack() {
				uni.navigateBack({
					delta: 1,
					fail: () => uni.switchTab({ url: '/pages/order/list' })
				})
			}
		}
	}
</script>

<style scoped>
	.review-page { padding-bottom: calc(140rpx + env(safe-area-inset-bottom)); }
	.card { margin: 24rpx 32rpx 0; }
	.brief-sub { display: block; margin-top: 10rpx; }
	.block-text { display: block; margin-top: 16rpx; }

	.star-card { display: flex; flex-direction: column; align-items: center; }
	.star-card .t-module { align-self: flex-start; }
	.star-row { margin-top: 32rpx; }
	.star { padding: 0 12rpx; }
	.star-mark { font-size: 72rpx; color: #E9EAEE; line-height: 1; }
	.star-mark.on { color: var(--c-warning); }
	.star-label { font-size: 26rpx; color: var(--c-text-secondary); margin-top: 20rpx; }

	.content-input {
		width: 100%; min-height: 200rpx; background: var(--c-bg-page);
		border-radius: 16rpx; padding: 24rpx; margin-top: 20rpx;
		font-size: 28rpx; line-height: 1.7;
	}
	.count { font-size: 24rpx; color: var(--c-text-weak); margin-left: auto; }
	.field-error.no-margin { margin-top: 12rpx; }

	.img-row { margin-top: 24rpx; flex-wrap: wrap; }
	.img-cell { position: relative; width: 152rpx; height: 152rpx; margin: 0 16rpx 16rpx 0; }
	.img { width: 152rpx; height: 152rpx; border-radius: 12rpx; background: #F2F3F5; }
	.img-del {
		position: absolute; top: -12rpx; right: -12rpx;
		width: 40rpx; height: 40rpx; line-height: 40rpx; text-align: center;
		background: rgba(0, 0, 0, 0.55); color: #FFFFFF; font-size: 22rpx; border-radius: 50%;
	}
	.img-add {
		border: 2rpx dashed var(--c-input-border); border-radius: 12rpx;
		display: flex; flex-direction: column; align-items: center; justify-content: center;
		background: var(--c-bg-page);
	}
	.img-add-icon { font-size: 48rpx; color: var(--c-text-weak); line-height: 1; }
	.img-add-text { font-size: 22rpx; color: var(--c-text-weak); margin-top: 8rpx; }

	.foot-space { height: 32rpx; }
	.action-bar {
		position: fixed; left: 0; right: 0; bottom: 0;
		background: #FFFFFF; box-shadow: var(--shadow-card);
		padding: 16rpx 32rpx calc(16rpx + env(safe-area-inset-bottom));
		z-index: 20;
	}
</style>
