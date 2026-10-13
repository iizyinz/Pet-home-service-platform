<template>
	<view class="state-view">
		<!-- 加载中：骨架屏 -->
		<block v-if="state === 'loading'">
			<view class="skeleton" v-for="(n, i) in rows" :key="i">
				<view class="loading-line sk-avatar"></view>
				<view class="sk-lines">
					<view class="loading-line sk-line-1"></view>
					<view class="loading-line sk-line-2"></view>
				</view>
			</view>
		</block>

		<!-- 空状态 -->
		<block v-else-if="state === 'empty'">
			<view class="state-icon empty-icon">
				<image class="state-img" src="/static/icons/state-empty.png" mode="aspectFit" />
			</view>
			<text class="state-text">{{ emptyText || '暂无数据' }}</text>
			<slot name="empty-action"></slot>
		</block>

		<!-- 加载失败 -->
		<block v-else>
			<view class="state-icon error-icon">
				<image class="state-img" src="/static/icons/state-error.png" mode="aspectFit" />
			</view>
			<text class="state-text">{{ errorText || '加载失败，请检查网络' }}</text>
			<button class="btn btn-outline btn-sm state-action" @click="$emit('retry')">重新加载</button>
		</block>
	</view>
</template>

<script>
	export default {
		name: 'StateView',
		props: {
			/** loading | empty | error */
			state: { type: String, default: 'loading' },
			rows: { type: Number, default: 3 },
			emptyText: { type: String, default: '' },
			errorText: { type: String, default: '' }
		},
		emits: ['retry']
	}
</script>

<style scoped>
	.skeleton {
		display: flex;
		flex-direction: row;
		background: var(--c-bg-card);
		border-radius: var(--radius-card);
		padding: 32rpx;
		margin-bottom: 24rpx;
	}
	.sk-avatar { width: 96rpx; height: 96rpx; border-radius: 50%; flex-shrink: 0; }
	.sk-lines { flex: 1; margin-left: 24rpx; }
	.sk-line-1 { width: 70%; margin-bottom: 20rpx; }
	.sk-line-2 { width: 45%; height: 24rpx; }
	.state-icon {
		width: 96rpx;
		height: 96rpx;
		line-height: 96rpx;
		text-align: center;
		border-radius: 50%;
		margin: 0 auto;
		font-size: 48rpx;
		font-weight: 600;
		display: flex;
		align-items: center;
		justify-content: center;
	}
	.state-img { width: 56rpx; height: 56rpx; }
	.empty-icon { background: #F2F3F5; }
	.error-icon { background: #FFF1F0; }
	.state-text { display: block; margin-top: 24rpx; font-size: 28rpx; color: var(--c-text-weak); }
	.state-action { display: inline-block; margin-top: 32rpx; }
</style>
