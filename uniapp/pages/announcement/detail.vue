<template>
	<view class="page ann-page">
		<state-view v-if="state === 'loading'" state="loading" :rows="4" />
		<state-view v-else-if="state === 'error'" state="error" :error-text="errorText" @retry="load" />

		<block v-else-if="item">
			<view class="card">
				<text class="t-title ann-title">{{ item.title }}</text>
				<text class="t-weak ann-time">发布于 {{ item.createdAt }}</text>
				<view class="divider"></view>
				<text class="t-body ann-content" user-select>{{ item.content }}</text>
			</view>
			<view class="foot-space"></view>
		</block>
	</view>
</template>

<script>
	import StateView from '../../components/state-view/state-view.vue'
	import { apiAnnouncementDetail } from '../../api/index'

	export default {
		components: { StateView },
		data() {
			return {
				state: 'loading',
				errorText: '',
				id: null,
				item: null
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
			async load() {
				this.state = 'loading'
				try {
					this.item = await apiAnnouncementDetail(this.id)
					this.state = 'ready'
				} catch (e) {
					this.state = 'error'
					this.errorText = (e && e.message) || '公告加载失败'
				}
			}
		}
	}
</script>

<style scoped>
	.card { margin: 24rpx 32rpx 0; }
	.ann-title { display: block; line-height: 1.5; }
	.ann-time { display: block; margin-top: 16rpx; }
	.ann-content { display: block; line-height: 1.8; white-space: pre-wrap; }
	.foot-space { height: 32rpx; }
</style>
