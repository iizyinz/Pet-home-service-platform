<template>
	<view class="status-tag" :style="{ background: meta.bg, color: meta.color }">{{ meta.label }}</view>
</template>

<script>
	import { statusMeta } from '../../utils/order-status'

	export default {
		name: 'StatusTag',
		props: {
			status: { type: String, default: '' }
		},
		computed: {
			meta() {
				return statusMeta(this.status)
			}
		}
	}
</script>

<style scoped>
	.status-tag {
		display: inline-block;
		font-size: 24rpx;
		line-height: 1;
		padding: 10rpx 16rpx;
		border-radius: 12rpx;
		font-weight: 500;
	}
</style>
