<template>
	<view class="page staff-page">
		<!-- 筛选区 -->
		<view class="filter-bar">
			<picker class="filter-picker" :range="areaNames" @change="onAreaChange">
				<view class="filter-cell" :class="{ active: areaIndex > 0 }">
					<text class="filter-text">{{ areaIndex > 0 ? areaNames[areaIndex] : '服务区域' }}</text>
					<text class="filter-arrow">▾</text>
				</view>
			</picker>
			<view class="filter-cell filter-search">
				<input
					class="filter-input"
					v-model="keyword"
					placeholder="搜索服务师昵称"
					placeholder-class="form-input-placeholder"
					confirm-type="search"
					@confirm="reload"
				/>
			</view>
			<view class="filter-cell filter-breed" @tap="showBreed = !showBreed">
				<text class="filter-text" :class="{ 'filter-on': breed }">{{ breed || '擅长品种' }}</text>
				<text class="filter-arrow">{{ showBreed ? '▴' : '▾' }}</text>
			</view>
		</view>

		<!-- 品种快捷筛选 -->
		<scroll-view v-if="showBreed" scroll-x class="breed-bar" :show-scrollbar="false">
			<view class="breed-chip" :class="{ active: !breed }" @tap="selectBreed('')">全部</view>
			<view
				class="breed-chip"
				:class="{ active: breed === b }"
				v-for="b in breedOptions"
				:key="b"
				@tap="selectBreed(b)"
			>{{ b }}</view>
		</scroll-view>

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
				<state-view v-else-if="state === 'error'" state="error" :error-text="errorText" @retry="reload" />

				<block v-else-if="list.length">
					<view class="staff-card" v-for="s in list" :key="s.id" @tap="goDetail(s.id)">
						<image class="avatar" :src="s.avatar || placeholder" mode="aspectFill" />
						<view class="info flex-1">
							<view class="row">
								<text class="name ellipsis">{{ s.nickname }}</text>
								<text v-if="s.certStatus === 1" class="tag cert">已认证</text>
							</view>
							<text class="meta">{{ s.areaName || '区域未标注' }} · 从业 {{ s.experienceYears || 0 }} 年 · 完成 {{ s.completedCount || 0 }} 单</text>
							<view class="row breed-row">
								<text class="mini-tag" v-for="b in (s.goodAtBreeds || []).slice(0, 3)" :key="b">{{ b }}</text>
								<text class="mini-tag mini-tag-weak" v-if="!(s.goodAtBreeds || []).length">擅长品种未标注</text>
							</view>
						</view>
						<view class="rating">
							<text class="rating-num">{{ ratingText(s.avgRating) }}</text>
							<text class="rating-label">评分</text>
						</view>
					</view>
					<text class="load-more">{{ loadingMore ? '加载中...' : (hasMore ? '上拉加载更多' : '没有更多了') }}</text>
				</block>

				<state-view v-else state="empty" :empty-text="hasFilter ? '没有符合条件的服务师' : '暂无认证通过的服务师'">
					<template #empty-action>
						<button v-if="hasFilter" class="btn btn-outline btn-sm" @tap="resetFilter">清空筛选条件</button>
					</template>
				</state-view>
			</view>
		</scroll-view>
	</view>
</template>

<script>
	import StateView from '../../components/state-view/state-view.vue'
	import { apiStaffs, apiServiceAreas, apiServiceItems } from '../../api/index'

	const PLACEHOLDER = '/static/logo.png'
	const DEFAULT_BREEDS = ['猫', '狗', '金毛', '布偶', '柯基', '泰迪']

	export default {
		components: { StateView },
		data() {
			return {
				state: 'loading',
				errorText: '',
				refreshing: false,
				loadingMore: false,
				keyword: '',
				areas: [],
				areaIndex: 0,
				showBreed: false,
				breed: '',
				breedOptions: DEFAULT_BREEDS,
				list: [],
				page: 1,
				size: 10,
				total: 0,
				hasMore: true,
				preItemId: null,
				placeholder: PLACEHOLDER
			}
		},
		computed: {
			areaNames() {
				return ['全部区域'].concat(this.areas.map((a) => a.name))
			},
			hasFilter() {
				return !!this.keyword || this.areaIndex > 0 || !!this.breed
			}
		},
		onLoad(options) {
			if (options && options.itemId) this.preItemId = Number(options.itemId)
			this.loadFilters()
			this.reload()
		},
		methods: {
			ratingText(v) {
				return v === null || v === undefined ? '暂无' : Number(v).toFixed(1)
			},
			async loadFilters() {
				try {
					this.areas = (await apiServiceAreas()) || []
				} catch (e) {
					// 区域加载失败仅影响筛选项，列表仍可用
				}
				try {
					const res = await apiServiceItems({ page: 1, size: 50 }, { silent: true })
					const breeds = new Set()
					;(res.list || []).forEach((i) => {
						if (i.applicableBreeds) {
							String(i.applicableBreeds).split(/[、,，\s/]+/).filter(Boolean).forEach((b) => breeds.add(b))
						}
					})
					if (breeds.size) this.breedOptions = Array.from(breeds).slice(0, 12)
				} catch (e) {
					// 使用默认品种候选
				}
			},
			filterParams() {
				const area = this.areaIndex > 0 ? this.areas[this.areaIndex - 1] : null
				return {
					areaId: area ? area.id : undefined,
					breed: this.breed || undefined,
					keyword: this.keyword.trim() || undefined,
					page: this.page,
					size: this.size
				}
			},
			async reload() {
				this.page = 1
				this.state = 'loading'
				this.errorText = ''
				try {
					const res = await apiStaffs(this.filterParams())
					this.list = res.list || []
					this.total = res.total || 0
					this.hasMore = this.list.length < this.total
					this.state = 'ready'
				} catch (e) {
					this.state = 'error'
					this.errorText = (e && e.message) || '服务人员列表加载失败'
				}
			},
			async loadMore() {
				if (this.loadingMore || !this.hasMore || this.state === 'error') return
				this.loadingMore = true
				try {
					const res = await apiStaffs({ ...this.filterParams(), page: this.page + 1 })
					this.page += 1
					this.list = this.list.concat(res.list || [])
					this.hasMore = this.list.length < (res.total || 0)
				} catch (e) {
					uni.showToast({ title: '加载更多失败，请上拉重试', icon: 'none' })
				} finally {
					this.loadingMore = false
				}
			},
			onAreaChange(e) {
				const idx = Number(e.detail.value)
				if (idx === this.areaIndex) return
				this.areaIndex = idx
				this.reload()
			},
			selectBreed(b) {
				if (this.breed === b) return
				this.breed = b
				this.reload()
			},
			resetFilter() {
				this.keyword = ''
				this.areaIndex = 0
				this.breed = ''
				this.reload()
			},
			async onRefresh() {
				this.refreshing = true
				await this.reload()
				this.refreshing = false
			},
			goDetail(id) {
				const extra = this.preItemId ? '&itemId=' + this.preItemId : ''
				uni.navigateTo({ url: '/pages/staff/detail?id=' + id + extra })
			}
		}
	}
</script>

<style scoped>
	.staff-page { padding-bottom: 0; }
	.filter-bar {
		display: flex; flex-direction: row; align-items: center;
		padding: 16rpx 32rpx; background: #FFFFFF;
	}
	.filter-picker { flex-shrink: 0; }
	.filter-cell {
		height: 64rpx; display: flex; flex-direction: row; align-items: center;
		padding: 0 20rpx; background: var(--c-bg-page); border-radius: 32rpx;
		margin-right: 16rpx; border: 1px solid transparent;
	}
	.filter-cell.active { background: var(--c-primary-weak); }
	.filter-text { font-size: 26rpx; color: var(--c-text-secondary); max-width: 160rpx; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
	.filter-on { color: var(--c-primary); }
	.filter-arrow { font-size: 22rpx; color: var(--c-text-weak); margin-left: 8rpx; }
	.filter-search { flex: 1; margin-right: 16rpx; }
	.filter-input { flex: 1; font-size: 26rpx; height: 64rpx; }
	.filter-breed { margin-right: 0; }

	.breed-bar { white-space: nowrap; padding: 16rpx 32rpx 0; background: #FFFFFF; }
	.breed-chip {
		display: inline-block; padding: 12rpx 28rpx; margin-right: 16rpx;
		background: var(--c-bg-page); color: var(--c-text-secondary);
		font-size: 26rpx; border-radius: 28rpx;
	}
	.breed-chip.active { background: var(--c-primary); color: #FFFFFF; }

	.list-scroll { height: calc(100vh - 160rpx); padding-top: 24rpx; }
	.staff-card {
		display: flex; flex-direction: row; align-items: center;
		background: var(--c-bg-card); border-radius: var(--radius-card);
		padding: 28rpx; margin-bottom: 24rpx; box-shadow: var(--shadow-card);
	}
	.avatar { width: 112rpx; height: 112rpx; border-radius: 50%; background: #F2F3F5; flex-shrink: 0; }
	.info { margin-left: 20rpx; display: flex; flex-direction: column; min-width: 0; }
	.name { font-size: 30rpx; font-weight: 600; color: var(--c-text-title); max-width: 300rpx; }
	.cert { margin-left: 12rpx; flex-shrink: 0; font-size: 20rpx; padding: 6rpx 10rpx; }
	.meta { font-size: 24rpx; color: var(--c-text-weak); margin-top: 10rpx; }
	.breed-row { margin-top: 12rpx; flex-wrap: wrap; }
	.mini-tag {
		font-size: 22rpx; color: var(--c-primary); background: var(--c-primary-weak);
		padding: 6rpx 14rpx; border-radius: 10rpx; margin-right: 10rpx;
	}
	.mini-tag-weak { color: var(--c-text-weak); background: #F2F3F5; }
	.rating { display: flex; flex-direction: column; align-items: center; flex-shrink: 0; margin-left: 12rpx; }
	.rating-num { font-size: 40rpx; font-weight: 600; color: var(--c-primary); }
	.rating-label { font-size: 22rpx; color: var(--c-text-weak); }
	.load-more { display: block; text-align: center; color: var(--c-text-weak); font-size: 24rpx; padding: 24rpx 0 40rpx; }
</style>
