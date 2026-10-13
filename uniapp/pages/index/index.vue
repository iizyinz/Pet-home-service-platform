<template>
	<view class="page home-page">
		<!-- 顶部搜索 + 通知 -->
		<view class="topbar">
			<view class="search" @tap="goSearch">
				<image class="search-icon" src="/static/icons/search.png" mode="aspectFit" />
				<text class="search-placeholder">搜索服务项目 / 服务人员</text>
			</view>
			<view class="notice-btn" @tap="goNotice">
				<image class="notice-icon" src="/static/icons/bell.png" mode="aspectFit" />
				<view v-if="unreadCount > 0" class="badge">{{ unreadText }}</view>
			</view>
		</view>

		<scroll-view scroll-y class="home-scroll" :refresher-enabled="true" :refresher-triggered="refreshing" @refresherrefresh="onRefresh">
			<!-- 轮播图 -->
			<view class="page-pad">
				<swiper v-if="banners.length" class="banner-swiper" :indicator-dots="banners.length > 1" indicator-color="rgba(255,255,255,.5)" indicator-active-color="#FFFFFF" circular autoplay :interval="4000">
					<swiper-item v-for="b in banners" :key="b.id" @tap="onBanner(b)">
						<image class="banner-img" :src="b.imageUrl" mode="aspectFill" />
					</swiper-item>
				</swiper>
				<view v-else class="banner-swiper banner-empty">
					<text class="banner-empty-text">服务展示位</text>
				</view>
			</view>

			<!-- 服务分类 -->
			<view class="card">
				<view class="row-between">
					<text class="t-module">服务分类</text>
					<text class="link" @tap="goServiceList">全部服务 ›</text>
				</view>
				<view class="cat-grid">
					<view class="cat-item" v-for="t in types" :key="t.id" @tap="goServiceList(t.id)">
						<view class="cat-icon" :style="{ backgroundImage: t.icon ? 'url(' + t.icon + ')' : 'none' }">
							<text v-if="!t.icon" class="cat-icon-text">{{ (t.name || '').slice(0, 1) }}</text>
						</view>
						<text class="cat-name ellipsis">{{ t.name }}</text>
					</view>
					<view class="cat-item cat-item-empty" v-if="!types.length && state === 'loading'"></view>
				</view>
			</view>

			<!-- AI 宠物顾问入口 -->
			<view class="ai-entry" @tap="goAi">
				<image class="ai-entry-icon" src="/static/icons/ai.png" mode="aspectFit" />
				<view class="ai-left">
					<text class="ai-title">AI 宠物顾问</text>
					<text class="ai-desc">护理 · 健康 · 饮食 · 训练，在线即时解答</text>
				</view>
				<view class="ai-btn">立即提问</view>
			</view>

			<!-- 通知公告 -->
			<view class="card">
				<view class="row-between">
					<text class="t-module">通知公告</text>
					<text class="link" @tap="goNotice">更多 ›</text>
				</view>
				<view v-if="announcements.length" class="notice-list">
					<view class="notice-item" v-for="a in announcements" :key="a.id" @tap="goAnnouncement(a.id)">
						<text class="notice-tag">公告</text>
						<text class="notice-title flex-1 ellipsis">{{ a.title }}</text>
						<text class="t-weak">{{ dateShort(a.createdAt) }}</text>
					</view>
				</view>
				<state-view v-else-if="state === 'loading'" state="loading" :rows="2" />
				<state-view v-else-if="state === 'error'" state="error" @retry="loadAll" />
				<text v-else class="notice-empty">暂无公告</text>
			</view>

			<!-- 推荐服务师 -->
			<view class="card">
				<view class="row-between">
					<text class="t-module">推荐服务师</text>
					<text class="link" @tap="goStaffList">查看更多 ›</text>
				</view>

				<state-view v-if="state === 'loading'" state="loading" :rows="3" />
				<state-view v-else-if="state === 'error'" state="error" @retry="loadAll" />
				<view v-else-if="staffs.length" class="staff-list">
					<view class="staff-item" v-for="s in staffs" :key="s.id" @tap="goStaffDetail(s.id)">
						<image class="staff-avatar" :src="s.avatar || placeholderAvatar" mode="aspectFill" />
						<view class="staff-info flex-1">
							<view class="row">
								<text class="staff-name ellipsis">{{ s.nickname }}</text>
								<text v-if="s.certStatus === 1" class="tag cert-tag">已认证</text>
							</view>
							<text class="staff-meta">{{ s.areaName || '服务区域未标注' }} · 从业 {{ s.experienceYears || 0 }} 年</text>
							<text class="staff-meta">完成 {{ s.completedCount || 0 }} 单 · {{ (s.goodAtBreeds || []).join('、') || '擅长品种未标注' }}</text>
						</view>
						<view class="staff-rating">
							<text class="rating-num">{{ ratingText(s.avgRating) }}</text>
							<text class="rating-label">综合评分</text>
						</view>
					</view>
				</view>
				<view v-else class="state-view">
					<text class="state-text">暂无推荐服务师</text>
				</view>
			</view>

			<view class="foot-space"></view>
		</scroll-view>
	</view>
</template>

<script>
	import StateView from '../../components/state-view/state-view.vue'
	import { apiBanners, apiServiceTypes, apiAnnouncements, apiStaffs, apiUnreadCount } from '../../api/index'
	import { badgeText, dateShort } from '../../utils/format'

	const PLACEHOLDER_AVATAR = '/static/logo.png'

	export default {
		components: { StateView },
		data() {
			return {
				state: 'loading', // loading | ready | error
				refreshing: false,
				banners: [],
				types: [],
				announcements: [],
				staffs: [],
				unreadCount: 0,
				placeholderAvatar: PLACEHOLDER_AVATAR
			}
		},
		computed: {
			unreadText() {
				return badgeText(this.unreadCount)
			}
		},
		onLoad() {
			this.loadAll()
		},
		onShow() {
			// 从消息页返回时刷新未读数
			if (this.state === 'ready') this.loadUnread()
		},
		onPullDownRefresh() {
			this.loadAll().finally(() => uni.stopPullDownRefresh())
		},
		methods: {
			dateShort,
			ratingText(v) {
				return v === null || v === undefined ? '暂无' : Number(v).toFixed(1)
			},
			async loadAll() {
				this.state = 'loading'
				try {
					const [banners, types, announcements, staffPage] = await Promise.all([
						apiBanners(),
						apiServiceTypes(),
						apiAnnouncements(1, 3),
						apiStaffs({ page: 1, size: 5 }, { silent: true }).catch(() => ({ list: [] }))
					])
					this.banners = banners || []
					this.types = types || []
					this.announcements = (announcements && announcements.list) || []
					this.staffs = (staffPage && staffPage.list) || []
					this.state = 'ready'
					this.loadUnread()
				} catch (e) {
					this.state = 'error'
				}
			},
			async loadUnread() {
				try {
					const res = await apiUnreadCount()
					this.unreadCount = res.count || 0
				} catch (e) {
					// 静默失败：未读数仅作提醒，不影响主流程
				}
			},
			async onRefresh() {
				this.refreshing = true
				await this.loadAll()
				this.refreshing = false
			},
			goSearch() {
				// service/list 是 tabBar 页：写缓存由其 onShow 消费
				uni.setStorageSync('serviceFocus', '1')
				uni.switchTab({ url: '/pages/service/list' })
			},
			goNotice() {
				uni.switchTab({ url: '/pages/message/index' })
			},
			goServiceList(typeId) {
				// tabBar 页 switchTab 不支持参数：落缓存由服务页 onShow 消费
				if (typeId) uni.setStorageSync('serviceTypeId', typeId)
				else uni.removeStorageSync('serviceTypeId')
				uni.switchTab({ url: '/pages/service/list' })
			},
			goStaffList() {
				uni.navigateTo({ url: '/pages/staff/list' })
			},
			goStaffDetail(id) {
				uni.navigateTo({ url: '/pages/staff/detail?id=' + id })
			},
			goAi() {
				uni.navigateTo({ url: '/pages/ai/assistant' })
			},
			goAnnouncement(id) {
				uni.navigateTo({ url: '/pages/announcement/detail?id=' + id })
			},
			onBanner(b) {
				// 后台可配置 link，V1 支持站内路径跳转；外部链接暂不开放
				if (b.link && b.link.indexOf('/pages/') === 0) {
					if (b.link.indexOf('list') > -1 || b.link.indexOf('center') > -1 || b.link.indexOf('index') > -1) {
						uni.switchTab({ url: b.link })
					} else {
						uni.navigateTo({ url: b.link })
					}
				}
			}
		}
	}
</script>

<style scoped>
	.home-page { padding-bottom: 0; }
	.topbar {
		display: flex; flex-direction: row; align-items: center;
		padding: 16rpx 32rpx; background: #FFFFFF;
		position: sticky; top: 0; z-index: 10;
	}
	.search {
		flex: 1; height: 72rpx; background: var(--c-bg-page); border-radius: 36rpx;
		display: flex; flex-direction: row; align-items: center; padding: 0 28rpx;
	}
	.search-icon { width: 34rpx; height: 34rpx; margin-right: 12rpx; flex-shrink: 0; }
	.search-placeholder { color: var(--c-text-weak); font-size: 28rpx; }
	.notice-btn { width: 72rpx; height: 72rpx; position: relative; display: flex; align-items: center; justify-content: center; margin-left: 16rpx; }
	.notice-icon { width: 42rpx; height: 42rpx; }
	.badge {
		position: absolute; top: 6rpx; right: 0;
		min-width: 32rpx; height: 32rpx; line-height: 32rpx; text-align: center;
		background: var(--c-error); color: #FFFFFF; font-size: 20rpx;
		border-radius: 16rpx; padding: 0 8rpx;
	}
	.home-scroll { height: calc(100vh - 104rpx); }

	.banner-swiper { width: 100%; height: 300rpx; border-radius: var(--radius-card); overflow: hidden; }
	.banner-img { width: 100%; height: 300rpx; background: #F2F3F5; }
	.banner-empty {
		background: var(--c-primary-weak); display: flex; align-items: center; justify-content: center;
	}
	.banner-empty-text { color: var(--c-primary); font-size: 28rpx; }

	.card { margin-top: 24rpx; }
	.link { color: var(--c-info); font-size: 26rpx; }

	.cat-grid { display: flex; flex-direction: row; flex-wrap: wrap; margin-top: 24rpx; }
	.cat-item { width: 25%; display: flex; flex-direction: column; align-items: center; margin-bottom: 20rpx; }
	.cat-item-empty { height: 140rpx; }
	.cat-icon {
		width: 96rpx; height: 96rpx; border-radius: 28rpx;
		background-color: var(--c-primary-weak);
		background-size: cover; background-position: center;
		display: flex; align-items: center; justify-content: center;
	}
	.cat-icon-text { color: var(--c-primary); font-size: 36rpx; font-weight: 600; }
	.cat-name { font-size: 26rpx; color: var(--c-text-secondary); margin-top: 12rpx; max-width: 140rpx; }

	.ai-entry {
		display: flex; flex-direction: row; align-items: center; justify-content: space-between;
		background: #EEF2FF; border-radius: var(--radius-card); padding: 32rpx; margin-top: 24rpx;
	}
	.ai-entry-icon { width: 56rpx; height: 56rpx; flex-shrink: 0; margin-right: 20rpx; }
	.ai-left { display: flex; flex-direction: column; flex: 1; min-width: 0; }
	.ai-title { font-size: 32rpx; font-weight: 600; color: var(--c-info); }
	.ai-desc { font-size: 24rpx; color: var(--c-text-secondary); margin-top: 8rpx; }
	.ai-btn {
		background: var(--c-info); color: #FFFFFF; font-size: 26rpx;
		padding: 16rpx 28rpx; border-radius: 32rpx; flex-shrink: 0; margin-left: 16rpx;
	}

	.notice-list { margin-top: 16rpx; }
	.notice-item {
		display: flex; flex-direction: row; align-items: center;
		padding: 20rpx 0; border-bottom: 1px solid var(--c-border);
	}
	.notice-item:last-child { border-bottom: none; }
	.notice-tag {
		background: var(--c-primary-weak); color: var(--c-primary);
		font-size: 22rpx; padding: 6rpx 12rpx; border-radius: 12rpx; margin-right: 16rpx; flex-shrink: 0;
	}
	.notice-title { font-size: 28rpx; color: var(--c-text-title); margin-right: 16rpx; }
	.notice-empty { display: block; text-align: center; color: var(--c-text-weak); font-size: 26rpx; padding: 32rpx 0; }

	.staff-list { margin-top: 8rpx; }
	.staff-item {
		display: flex; flex-direction: row; align-items: center;
		padding: 24rpx 0; border-bottom: 1px solid var(--c-border);
	}
	.staff-item:last-child { border-bottom: none; }
	.staff-avatar { width: 96rpx; height: 96rpx; border-radius: 50%; background: #F2F3F5; flex-shrink: 0; }
	.staff-info { margin-left: 20rpx; display: flex; flex-direction: column; }
	.staff-name { font-size: 30rpx; font-weight: 600; color: var(--c-text-title); max-width: 280rpx; }
	.cert-tag { margin-left: 12rpx; font-size: 20rpx; padding: 6rpx 10rpx; flex-shrink: 0; }
	.staff-meta { font-size: 24rpx; color: var(--c-text-weak); margin-top: 6rpx; }
	.staff-rating { display: flex; flex-direction: column; align-items: center; flex-shrink: 0; margin-left: 16rpx; }
	.rating-num { font-size: 36rpx; font-weight: 600; color: var(--c-primary); }
	.rating-label { font-size: 22rpx; color: var(--c-text-weak); margin-top: 4rpx; }

	.foot-space { height: 32rpx; }
</style>
