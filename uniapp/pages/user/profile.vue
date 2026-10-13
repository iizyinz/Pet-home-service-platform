<template>
	<view class="page profile-page">
		<state-view v-if="state === 'loading'" state="loading" :rows="3" />
		<state-view v-else-if="state === 'error'" state="error" :error-text="errorText" @retry="load" />

		<block v-else>
			<!-- 头像 -->
			<view class="card avatar-card" @tap="changeAvatar">
				<text class="t-body flex-1">头像</text>
				<image class="avatar" :src="form.avatar || placeholder" mode="aspectFill" />
				<text class="head-arrow">›</text>
			</view>

			<!-- 基本信息 -->
			<view class="card form-card">
				<view class="form-row">
					<text class="form-label">昵称</text>
					<input class="form-input" v-model="form.nickname" placeholder="请输入昵称" placeholder-class="form-input-placeholder" :maxlength="64" />
				</view>
				<view class="form-row">
					<text class="form-label">手机号</text>
					<input class="form-input" v-model="form.phone" type="number" placeholder="请输入手机号" placeholder-class="form-input-placeholder" :maxlength="11" />
				</view>
				<view class="form-row">
					<text class="form-label">账号</text>
					<text class="form-static">{{ user.username || '-' }}</text>
				</view>
				<view class="form-row">
					<text class="form-label">余额</text>
					<text class="form-static">¥{{ money(user.balance) }}</text>
				</view>
			</view>

			<!-- 修改密码（可选） -->
			<view class="card form-card">
				<text class="t-module">修改密码</text>
				<view class="form-row">
					<text class="form-label">原密码</text>
					<input class="form-input" v-model="pwd.oldPassword" password placeholder="不修改请留空" placeholder-class="form-input-placeholder" />
				</view>
				<view class="form-row">
					<text class="form-label">新密码</text>
					<input class="form-input" v-model="pwd.newPassword" password placeholder="6-32 位" placeholder-class="form-input-placeholder" />
				</view>
				<text v-if="pwdError" class="field-error">{{ pwdError }}</text>
			</view>

			<view class="foot-space"></view>
			<view class="action-bar">
				<button class="btn btn-primary btn-block" :class="{ 'btn-disabled': saving }" :disabled="saving" @tap="save">
					{{ saving ? '保存中...' : '保存修改' }}
				</button>
			</view>
		</block>
	</view>
</template>

<script>
	import StateView from '../../components/state-view/state-view.vue'
	import { apiProfile, apiUpdateProfile, apiUpload } from '../../api/index'
	import { money } from '../../utils/format'
	import { ensureLogin } from '../../utils/auth'

	const PLACEHOLDER = '/static/logo.png'

	export default {
		components: { StateView },
		data() {
			return {
				state: 'loading',
				errorText: '',
				user: {},
				form: { nickname: '', avatar: '', phone: '' },
				pwd: { oldPassword: '', newPassword: '' },
				pwdError: '',
				saving: false,
				placeholder: PLACEHOLDER
			}
		},
		onLoad() {
			if (!ensureLogin()) return
			this.load()
		},
		methods: {
			money,
			async load() {
				this.state = 'loading'
				try {
					this.user = await apiProfile()
					this.form.nickname = this.user.nickname || ''
					this.form.avatar = this.user.avatar || ''
					this.form.phone = this.user.phone || ''
					this.state = 'ready'
				} catch (e) {
					this.state = 'error'
					this.errorText = (e && e.message) || '个人信息加载失败'
				}
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
			/** 密码修改必须同时提供原密码与新密码，否则视为不修改 */
			validatePwd() {
				this.pwdError = ''
				const { oldPassword, newPassword } = this.pwd
				if (!oldPassword && !newPassword) return true
				if (!oldPassword) { this.pwdError = '请输入原密码'; return false }
				if (!newPassword) { this.pwdError = '请输入新密码'; return false }
				if (newPassword.length < 6) { this.pwdError = '新密码长度至少 6 位'; return false }
				return true
			},
			async save() {
				if (this.saving) return
				if (!this.form.nickname.trim()) {
					uni.showToast({ title: '昵称不能为空', icon: 'none' })
					return
				}
				if (!this.validatePwd()) return

				this.saving = true
				try {
					const payload = {
						nickname: this.form.nickname.trim(),
						avatar: this.form.avatar,
						phone: this.form.phone.trim()
					}
					if (this.pwd.oldPassword && this.pwd.newPassword) {
						payload.oldPassword = this.pwd.oldPassword
						payload.newPassword = this.pwd.newPassword
					}
					await apiUpdateProfile(payload, { loading: true })
					uni.showToast({ title: '保存成功', icon: 'success' })
					this.pwd = { oldPassword: '', newPassword: '' }
					this.load()
				} catch (e) {
					if (e && e.code === 1006) this.pwdError = '原密码错误，请重新输入'
				} finally {
					this.saving = false
				}
			}
		}
	}
</script>

<style scoped>
	.profile-page { padding-bottom: calc(140rpx + env(safe-area-inset-bottom)); }
	.card { margin: 24rpx 32rpx 0; }
	.avatar-card { display: flex; flex-direction: row; align-items: center; }
	.avatar { width: 96rpx; height: 96rpx; border-radius: 50%; background: #F2F3F5; }
	.head-arrow { color: var(--c-text-weak); font-size: 36rpx; margin-left: 12rpx; }
	.form-card { padding: 8rpx 32rpx; }
	.form-card .t-module { display: block; padding-top: 24rpx; }
	.form-row {
		display: flex; flex-direction: row; align-items: center;
		min-height: 96rpx; border-bottom: 1px solid var(--c-border);
	}
	.form-row:last-child { border-bottom: none; }
	.form-label { width: 176rpx; font-size: 30rpx; color: var(--c-text-secondary); flex-shrink: 0; }
	.form-input { flex: 1; height: 96rpx; font-size: 30rpx; text-align: right; }
	.form-static { flex: 1; font-size: 30rpx; color: var(--c-text-weak); text-align: right; }
	.foot-space { height: 32rpx; }
	.action-bar {
		position: fixed; left: 0; right: 0; bottom: 0;
		background: #FFFFFF; box-shadow: var(--shadow-card);
		padding: 16rpx 32rpx calc(16rpx + env(safe-area-inset-bottom));
	}
</style>
