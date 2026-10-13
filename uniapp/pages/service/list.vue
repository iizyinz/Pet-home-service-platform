<template>
	<view class="page service-page">
		<!-- 搜索栏 -->
		<view class="search-bar">
			<view class="search-input-wrap" :class="{ focus: searchFocus }">
				<image class="search-icon" src="/static/icons/search.png" mode="aspectFit" />
				<input
					class="search-input"
					v-model="keyword"
					placeholder="搜索服务项目"
					placeholder-class="form-input-placeholder"
					:focus="autoFocus"
					confirm-type="search"
					@focus="searchFocus = true"
					@blur="searchFocus = false"
					@confirm="onSearch"
				/>
			</view>
			<text class="search-btn" @tap="onSearch">搜索</text>
		</view>

		<!-- 类型筛选 -->
		<scroll-view scroll-x class="type-bar" :show-scrollbar="false">
			<view
				class="type-chip"
				:class="{ active: currentTypeId === 0 }"
				@tap="selectType(0)"
			>全部</view>
			<view
				class="type-chip"
				:class="{ active: currentTypeId === t.id }"
				v-for="t in types"
				:key="t.id"
				@tap="selectType(t.id)"
			>{{ t.name }}</view>
		</scroll-view>

		<!-- 服务列表 -->
		<scroll-view
			scroll-y
			class="list-scroll"
			:refresher-enabled="true"
			:refresher-triggered="refreshing"
			@refresherrefresh="onRefresh"
			@scrolltolower="loadMore"
		>
			<view class="page-pad">
				<state-view v-if="state === 'loading' && !list.length" state="loading" :rows="4" />

				<state-view
					v-else-if="state === 'error'"
					state="error"
					:error-text="errorText"
					@retry="reload"
				/>

				<block v-else-if="list.length">
					<view class="item-card" v-for="item in list" :key="item.id" @tap="goDetail(item.id)">
						<image class="item-cover" :src="item.coverUrl || placeholder" mode="aspectFill" />
						<view class="item-info flex-1">
							<text class="item-name ellipsis-2">{{ item.name }}</text>
							<view class="row" v-if="typeNames[item.typeId]">
								<text class="tag">{{ typeNames[item.typeId] }}</text>
							</view>
							<text class="item-meta" v-if="item.durationMinutes">时长 {{ item.durationMinutes }} 分钟</text>
							<text class="item-meta ellipsis" v-if="item.applicableBreeds">适用：{{ item.applicableBreeds }}</text>
							<view class="row-between item-bottom">
								<view class="price-wrap">
									<text class="price-unit">¥</text>
									<text class="t-price-lg">{{ money(item.price) }}</text>
								</view>
								<view class="btn btn-primary btn-sm">立即预约</view>
							</view>
						</view>
					</view>

					<text class="load-more">{{ loadingMore ? '加载中...' : (hasMore ? '上拉加载更多' : '没有更多了') }}</text>
				</block>

				<state-view
					v-else
					state="empty"
					:empty-text="keyword ? '未找到相关服务，换个关键词试试' : '该分类暂无上架服务'"
				>
					<template #empty-action>
						<button class="btn btn-outline btn-sm" @tap="keyword ? onSearch(true) : selectType(0)">
							{{ keyword ? '清空搜索' : '查看全部服务' }}
						</button>
					</template>
				</state-view>
			</view>
		</scroll-view>
	</view>
</template>

<script>
	import StateView from '../../components/state-view/state-view.vue'
	import { apiServiceTypes, apiServiceItems } from '../../api/index'
	import { money } from '../../utils/format'

	const PLACEHOLDER = '/static/logo.png'

	export default {
		components: { StateView },
		data() {
			return {
				state: 'loading',
				errorText: '',
				refreshing: false,
				loadingMore: false,
				keyword: '',
				searchFocus: false,
				autoFocus: false,
				types: [],
				typeNames: {},
				currentTypeId: 0,
				list: [],
				page: 1,
				size: 10,
				total: 0,
				hasMore: true,
				placeholder: PLACEHOLDER
			}
		},
		onLoad(options) {
			if (options && options.focus) this.autoFocus = true
			if (options && options.typeId) this.currentTypeId = Number(options.typeId)
			if (options && options.keyword) this.keyword = decodeURIComponent(options.keyword)
			this.loadTypes()
			this.reload()
		},
		onShow() {
			// 消费首页分类入口写入的类型（switchTab 无法带参）
			const preset = uni.getStorageSync('serviceTypeId')
			if (preset !== '' && preset !== undefined && preset !== null) {
				uni.removeStorageSync('serviceTypeId')
				const id = Number(preset) || 0
				if (id !== this.currentTypeId) {
					this.currentTypeId = id
					this.reload()
				}
			}
			// 消费首页搜索入口的聚焦标记
			if (uni.getStorageSync('serviceFocus')) {
				uni.removeStorageSync('serviceFocus')
				this.autoFocus = true
			}
		},
		methods: {
			money,
			async loadTypes() {
				try {
					this.types = (await apiServiceTypes()) || []
					this.typeNames = this.types.reduce((m, t) => { m[t.id] = t.name; return m }, {})
				} catch (e) {
					// 类型加载失败不阻断列表，仍可看全部
				}
			},
			/** 列表加载：page=1 重置，失败进入 error 态并可重试 */
			async reload() {
				this.page = 1
				this.state = 'loading'
				this.errorText = ''
				try {
					const res = await apiServiceItems({
						typeId: this.currentTypeId || undefined,
						keyword: this.keyword.trim() || undefined,
						page: this.page,
						size: this.size
					})
					this.list = res.list || []
					this.total = res.total || 0
					this.hasMore = this.list.length < this.total
					this.state = 'ready'
				} catch (e) {
					this.state = 'error'
					this.errorText = (e && e.message) || '服务列表加载失败'
				}
			},
			async loadMore() {
				if (this.loadingMore || !this.hasMore || this.state === 'error') return
				this.loadingMore = true
				try {
					const res = await apiServiceItems({
						typeId: this.currentTypeId || undefined,
						keyword: this.keyword.trim() || undefined,
						page: this.page + 1,
						size: this.size
					})
					this.page += 1
					this.list = this.list.concat(res.list || [])
					this.hasMore = this.list.length < (res.total || 0)
				} catch (e) {
					uni.showToast({ title: '加载更多失败，请上拉重试', icon: 'none' })
				} finally {
					this.loadingMore = false
				}
			},
			onSearch(clear) {
				if (clear === true) this.keyword = ''
				this.reload()
			},
			selectType(id) {
				if (this.currentTypeId === id) return
				this.currentTypeId = id
				this.reload()
			},
			async onRefresh() {
				this.refreshing = true
				await this.reload()
				this.refreshing = false
			},
			goDetail(id) {
				uni.navigateTo({ url: '/pages/service/detail?id=' + id })
			}
		}
	}
</script>

<style scoped>
	.service-page { padding-bottom: 0; }
	.search-bar {
		display: flex; flex-direction: row; align-items: center;
		padding: 16rpx 32rpx; background: #FFFFFF;
	}
	.search-input-wrap {
		flex: 1; height: 72rpx; background: var(--c-bg-page); border-radius: 36rpx;
		display: flex; flex-direction: row; align-items: center; padding: 0 28rpx;
		border: 1px solid transparent;
	}
	.search-input-wrap.focus { border-color: var(--c-primary); background: #FFFFFF; }
	.search-icon { width: 34rpx; height: 34rpx; margin-right: 12rpx; flex-shrink: 0; }
	.search-input { flex: 1; font-size: 28rpx; height: 72rpx; }
	.search-btn { color: var(--c-primary); font-size: 28rpx; margin-left: 20rpx; flex-shrink: 0; }

	.type-bar { white-space: nowrap; padding: 20rpx 32rpx 8rpx; background: #FFFFFF; }
	.type-chip {
		display: inline-block; padding: 14rpx 32rpx; margin-right: 16rpx;
		background: var(--c-bg-page); color: var(--c-text-secondary);
		font-size: 28rpx; border-radius: 32rpx;
	}
	.type-chip.active { background: var(--c-primary); color: #FFFFFF; font-weight: 500; }

	.list-scroll { height: calc(100vh - 200rpx); padding-top: 24rpx; }
	.item-card {
		display: flex; flex-direction: row;
		background: var(--c-bg-card); border-radius: var(--radius-card);
		padding: 24rpx; margin-bottom: 24rpx; box-shadow: var(--shadow-card);
	}
	.item-cover { width: 200rpx; height: 200rpx; border-radius: 16rpx; background: #F2F3F5; flex-shrink: 0; }
	.item-info { margin-left: 24rpx; display: flex; flex-direction: column; }
	.item-name { font-size: 30rpx; font-weight: 600; color: var(--c-text-title); }
	.item-meta { font-size: 24rpx; color: var(--c-text-weak); margin-top: 8rpx; }
	.row .tag { margin-top: 12rpx; }
	.item-bottom { margin-top: auto; padding-top: 16rpx; }
	.price-wrap { display: flex; flex-direction: row; align-items: baseline; }
	.price-unit { font-size: 24rpx; color: var(--c-primary); margin-right: 4rpx; }
	.load-more { display: block; text-align: center; color: var(--c-text-weak); font-size: 24rpx; padding: 24rpx 0 40rpx; }
</style>
