<template>
	<view class="page pet-edit-page">
		<state-view v-if="state === 'loading'" state="loading" :rows="3" />
		<state-view v-else-if="state === 'error'" state="error" :error-text="errorText" @retry="load" />

		<block v-else>
			<!-- 头像 -->
			<view class="card avatar-card" @tap="changeAvatar">
				<image class="avatar" :src="form.avatar || placeholder" mode="aspectFill" />
				<view class="flex-1 avatar-tip">
					<text class="t-body">宠物照片</text>
					<text class="t-weak">点击上传（jpg/png，≤5MB）</text>
				</view>
				<text class="head-arrow">›</text>
			</view>

			<!-- 表单 -->
			<view class="card form-card">
				<view class="form-row">
					<text class="form-label">宠物昵称 <text class="req">*</text></text>
					<input class="form-input" v-model="form.name" placeholder="如：豆豆" placeholder-class="form-input-placeholder" :maxlength="64" @input="clearError('name')" />
				</view>
				<text v-if="errors.name" class="field-error">{{ errors.name }}</text>

				<view class="form-row">
					<text class="form-label">品种</text>
					<input class="form-input" v-model="form.breed" placeholder="如：金毛 / 布偶" placeholder-class="form-input-placeholder" :maxlength="64" />
				</view>

				<view class="form-row">
					<text class="form-label">年龄</text>
					<input class="form-input" v-model="form.age" placeholder="如：3 岁" placeholder-class="form-input-placeholder" :maxlength="32" />
				</view>

				<view class="form-row">
					<text class="form-label">体重 (kg)</text>
					<input class="form-input" v-model="weightText" type="digit" placeholder="如：12.5" placeholder-class="form-input-placeholder" @input="clearError('weightKg')" />
				</view>
				<text v-if="errors.weightKg" class="field-error">{{ errors.weightKg }}</text>
			</view>

			<view class="card form-card">
				<view class="form-row form-row-top">
					<text class="form-label">健康信息</text>
					<textarea class="form-textarea" v-model="form.healthInfo" placeholder="疫苗、既往病史等（最多 500 字）" placeholder-class="form-input-placeholder" :maxlength="500" />
				</view>
				<view class="form-row form-row-top">
					<text class="form-label">特殊需求</text>
					<textarea class="form-textarea" v-model="form.specialNeed" placeholder="性格、饮食禁忌、注意事项等（最多 500 字）" placeholder-class="form-input-placeholder" :maxlength="500" />
				</view>
			</view>

			<view class="foot-space"></view>
			<view class="action-bar">
				<button class="btn btn-primary btn-block" :class="{ 'btn-disabled': saving }" :disabled="saving" @tap="save">
					{{ saving ? '保存中...' : '保存档案' }}
				</button>
			</view>
		</block>
	</view>
</template>

<script>
	import StateView from '../../components/state-view/state-view.vue'
	import { apiPetDetail, apiCreatePet, apiUpdatePet, apiUpload } from '../../api/index'
	import { ensureLogin } from '../../utils/auth'

	const PLACEHOLDER = '/static/logo.png'

	export default {
		components: { StateView },
		data() {
			return {
				state: 'ready',
				errorText: '',
				id: null,
				form: { name: '', breed: '', age: '', weightKg: null, healthInfo: '', specialNeed: '', avatar: '' },
				weightText: '',
				errors: {},
				saving: false,
				placeholder: PLACEHOLDER
			}
		},
		onLoad(options) {
			if (!ensureLogin()) return
			this.id = options && options.id ? Number(options.id) : null
			uni.setNavigationBarTitle({ title: this.id ? '编辑宠物' : '添加宠物' })
			if (this.id) this.load()
		},
		methods: {
			async load() {
				this.state = 'loading'
				try {
					const p = await apiPetDetail(this.id)
					this.form = {
						name: p.name || '',
						breed: p.breed || '',
						age: p.age || '',
						weightKg: p.weightKg,
						healthInfo: p.healthInfo || '',
						specialNeed: p.specialNeed || '',
						avatar: p.avatar || ''
					}
					this.weightText = p.weightKg === null || p.weightKg === undefined ? '' : String(p.weightKg)
					this.state = 'ready'
				} catch (e) {
					this.state = 'error'
					this.errorText = (e && e.message) || '宠物信息加载失败'
				}
			},
			clearError(key) {
				if (this.errors[key]) this.errors[key] = ''
			},
			changeAvatar() {
				uni.chooseImage({
					count: 1,
					sizeType: ['compressed'],
					success: async (res) => {
						uni.showLoading({ title: '上传中', mask: true })
						try {
							this.form.avatar = await apiUpload(res.tempFilePaths[0])
						} catch (e) { /* 请求层已提示 */ }
						finally { uni.hideLoading() }
					}
				})
			},
			validate() {
				const errs = {}
				if (!this.form.name.trim()) errs.name = '宠物昵称不能为空'
				if (this.weightText) {
					const w = Number(this.weightText)
					if (Number.isNaN(w) || w <= 0 || w > 200) errs.weightKg = '体重需为 0-200 之间的数值'
				}
				this.errors = errs
				if (Object.keys(errs).length) {
					uni.showToast({ title: Object.values(errs)[0], icon: 'none' })
					return false
				}
				return true
			},
			buildPayload() {
				return {
					name: this.form.name.trim(),
					breed: this.form.breed.trim(),
					age: this.form.age.trim(),
					weightKg: this.weightText ? Number(this.weightText) : null,
					healthInfo: this.form.healthInfo.trim(),
					specialNeed: this.form.specialNeed.trim(),
					avatar: this.form.avatar
				}
			},
			async save() {
				if (this.saving) return
				if (!this.validate()) return
				this.saving = true
				try {
					if (this.id) {
						await apiUpdatePet(this.id, this.buildPayload(), { loading: true })
					} else {
						await apiCreatePet(this.buildPayload(), { loading: true })
					}
					uni.showToast({ title: '保存成功', icon: 'success' })
					setTimeout(() => uni.navigateBack(), 600)
				} catch (e) {
					if (e && e.code === 2010) uni.showToast({ title: '宠物档案不存在或已被删除', icon: 'none' })
				} finally {
					this.saving = false
				}
			}
		}
	}
</script>

<style scoped>
	.pet-edit-page { padding-bottom: calc(140rpx + env(safe-area-inset-bottom)); }
	.card { margin: 24rpx 32rpx 0; }
	.avatar-card { display: flex; flex-direction: row; align-items: center; }
	.avatar { width: 120rpx; height: 120rpx; border-radius: 50%; background: #F2F3F5; flex-shrink: 0; }
	.avatar-tip { margin-left: 24rpx; display: flex; flex-direction: column; }
	.avatar-tip .t-weak { margin-top: 8rpx; }
	.head-arrow { color: var(--c-text-weak); font-size: 36rpx; }
	.form-card { padding: 8rpx 32rpx; }
	.form-row {
		display: flex; flex-direction: row; align-items: center;
		min-height: 96rpx; border-bottom: 1px solid var(--c-border);
	}
	.form-row:last-child { border-bottom: none; }
	.form-row-top { align-items: flex-start; padding: 24rpx 0; }
	.form-label { width: 200rpx; font-size: 30rpx; color: var(--c-text-secondary); flex-shrink: 0; }
	.req { color: var(--c-error); }
	.form-input { flex: 1; height: 96rpx; font-size: 30rpx; text-align: right; }
	.form-textarea { flex: 1; font-size: 28rpx; min-height: 120rpx; line-height: 1.6; text-align: left; }
	.foot-space { height: 32rpx; }
	.action-bar {
		position: fixed; left: 0; right: 0; bottom: 0;
		background: #FFFFFF; box-shadow: var(--shadow-card);
		padding: 16rpx 32rpx calc(16rpx + env(safe-area-inset-bottom));
	}
</style>
