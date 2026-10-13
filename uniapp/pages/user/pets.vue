<template>
	<view class="page pets-page">
		<state-view v-if="state === 'loading'" state="loading" :rows="3" />
		<state-view v-else-if="state === 'error'" state="error" :error-text="errorText" @retry="load" />

		<block v-else>
			<view class="page-pad">
				<view class="pet-card" v-for="p in pets" :key="p.id">
					<image class="pet-avatar" :src="p.avatar || placeholder" mode="aspectFill" @tap="edit(p)" />
					<view class="pet-info flex-1" @tap="edit(p)">
						<view class="row">
							<text class="t-body pet-name">{{ p.name }}</text>
							<text class="tag" v-if="p.breed">{{ p.breed }}</text>
						</view>
						<text class="t-weak pet-meta">
							{{ p.age || '年龄未填' }}<block v-if="p.weightKg"> · {{ p.weightKg }} kg</block>
						</text>
						<text class="t-weak pet-meta ellipsis" v-if="p.specialNeed">特殊需求：{{ p.specialNeed }}</text>
					</view>
					<view class="pet-del" @tap.stop="confirmDelete(p)">删除</view>
				</view>

				<view v-if="!pets.length" class="state-view">
					<view class="state-icon empty-icon">
						<image class="state-img" src="/static/icons/state-empty.png" mode="aspectFit" />
					</view>
					<text class="state-text">还没有宠物档案</text>
					<text class="state-text sub">添加后下单可直接选择宠物</text>
				</view>
			</view>

			<view class="action-bar">
				<button class="btn btn-primary btn-block" @tap="goCreate">添加宠物档案</button>
			</view>
		</block>
	</view>
</template>

<script>
	import StateView from '../../components/state-view/state-view.vue'
	import { apiPets, apiDeletePet } from '../../api/index'
	import { ensureLogin } from '../../utils/auth'

	const PLACEHOLDER = '/static/logo.png'

	export default {
		components: { StateView },
		data() {
			return {
				state: 'loading',
				errorText: '',
				pets: [],
				placeholder: PLACEHOLDER,
				deleting: false
			}
		},
		onShow() {
			if (!ensureLogin()) return
			this.load()
		},
		methods: {
			async load() {
				this.state = 'loading'
				try {
					this.pets = (await apiPets()) || []
					this.state = 'ready'
				} catch (e) {
					this.state = 'error'
					this.errorText = (e && e.message) || '宠物档案加载失败'
				}
			},
			edit(p) {
				uni.navigateTo({ url: '/pages/user/pet-edit?id=' + p.id })
			},
			goCreate() {
				uni.navigateTo({ url: '/pages/user/pet-edit' })
			},
			confirmDelete(p) {
				uni.showModal({
					title: '删除宠物档案',
					content: `确定删除「${p.name}」的档案吗？删除后不可恢复。`,
					confirmText: '删除',
					confirmColor: '#FF4D4F',
					cancelText: '取消',
					success: async (r) => {
						if (!r.confirm || this.deleting) return
						this.deleting = true
						try {
							await apiDeletePet(p.id)
							uni.showToast({ title: '已删除', icon: 'success' })
							this.load()
						} catch (e) {
							// 2015 存在关联数据由请求层提示
						} finally {
							this.deleting = false
						}
					}
				})
			}
		}
	}
</script>

<style scoped>
	.pets-page { padding-bottom: calc(140rpx + env(safe-area-inset-bottom)); }
	.pet-card {
		display: flex; flex-direction: row; align-items: center;
		background: var(--c-bg-card); border-radius: var(--radius-card);
		padding: 28rpx; margin-top: 24rpx; box-shadow: var(--shadow-card);
	}
	.pet-avatar { width: 112rpx; height: 112rpx; border-radius: 50%; background: #F2F3F5; flex-shrink: 0; }
	.pet-info { margin-left: 24rpx; display: flex; flex-direction: column; min-width: 0; }
	.pet-name { font-weight: 600; margin-right: 16rpx; }
	.pet-meta { display: block; margin-top: 8rpx; }
	.pet-del { color: var(--c-error); font-size: 26rpx; padding: 12rpx; flex-shrink: 0; }
	.empty-icon { background: #F2F3F5; width: 96rpx; height: 96rpx; display: flex; align-items: center; justify-content: center; border-radius: 50%; margin: 0 auto; }
	.state-img { width: 56rpx; height: 56rpx; }
	.state-text { display: block; text-align: center; color: var(--c-text-weak); font-size: 28rpx; margin-top: 24rpx; }
	.state-text.sub { font-size: 24rpx; margin-top: 12rpx; }
	.action-bar {
		position: fixed; left: 0; right: 0; bottom: 0;
		background: #FFFFFF; box-shadow: var(--shadow-card);
		padding: 16rpx 32rpx calc(16rpx + env(safe-area-inset-bottom));
	}
</style>
