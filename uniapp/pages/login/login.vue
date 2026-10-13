<template>
	<view class="page login-page">
		<view class="brand">
			<view class="brand-logo">宠</view>
			<text class="brand-name">宠物上门服务</text>
			<text class="brand-slogan">专业服务师上门，服务全程可追溯</text>
		</view>

		<!-- 登录 / 注册 切换 -->
		<view class="seg">
			<view class="seg-item" :class="{ active: mode === 'login' }" @click="switchMode('login')">登录</view>
			<view class="seg-item" :class="{ active: mode === 'register' }" @click="switchMode('register')">注册</view>
		</view>

		<view class="card form-card">
			<!-- 用户名 -->
			<view class="field">
				<text class="field-label">账号</text>
				<input
					class="field-input"
					v-model="form.username"
					placeholder="请输入账号"
					placeholder-class="form-input-placeholder"
					:maxlength="64"
					@input="clearError('username')"
				/>
			</view>
			<text v-if="errors.username" class="field-error">{{ errors.username }}</text>

			<!-- 密码 -->
			<view class="field">
				<text class="field-label">密码</text>
				<input
					class="field-input"
					v-model="form.password"
					placeholder="请输入密码"
					placeholder-class="form-input-placeholder"
					password
					:maxlength="32"
					@input="clearError('password')"
				/>
			</view>
			<text v-if="errors.password" class="field-error">{{ errors.password }}</text>

			<!-- 注册时才显示昵称/手机号 -->
			<block v-if="mode === 'register'">
				<view class="field">
					<text class="field-label">昵称</text>
					<input
						class="field-input"
						v-model="form.nickname"
						placeholder="请输入昵称（选填）"
						placeholder-class="form-input-placeholder"
						:maxlength="64"
					/>
				</view>
				<view class="field">
					<text class="field-label">手机号</text>
					<input
						class="field-input"
						v-model="form.phone"
						type="number"
						placeholder="请输入手机号（选填）"
						placeholder-class="form-input-placeholder"
						:maxlength="11"
					/>
				</view>
			</block>

			<!-- 图形验证码 -->
			<view class="field">
				<text class="field-label">验证码</text>
				<input
					class="field-input"
					v-model="form.captchaCode"
					placeholder="请输入验证码"
					placeholder-class="form-input-placeholder"
					:maxlength="6"
					@input="clearError('captchaCode')"
				/>
				<image
					class="captcha-img"
					:src="captchaSrc"
					mode="aspectFit"
					@tap="loadCaptcha"
				/>
			</view>
			<text v-if="errors.captchaCode" class="field-error">{{ errors.captchaCode }}</text>

			<!-- 记住我（仅登录） -->
			<view class="row-between remember" v-if="mode === 'login'">
				<view class="row" @tap="rememberMe = !rememberMe">
					<view class="checkbox" :class="{ checked: rememberMe }">
						<text v-if="rememberMe" class="checkbox-mark">✓</text>
					</view>
					<text class="t-sub remember-text">记住我</text>
				</view>
				<text class="link" @tap="onForgot">忘记密码</text>
			</view>
		</view>

		<!-- 主操作 -->
		<button
			class="btn btn-primary btn-block submit-btn"
			:class="{ 'btn-disabled': submitting }"
			:disabled="submitting"
			@tap="submit"
		>
			{{ submitting ? (mode === 'login' ? '登录中...' : '注册中...') : (mode === 'login' ? '登录' : '注册并登录') }}
		</button>

		<text class="agreement">登录即代表同意《用户协议》与《隐私政策》</text>
	</view>
</template>

<script>
	import { apiCaptcha, apiLogin, apiRegister } from '../../api/index'
	import { setAuth, isTabRoute } from '../../utils/auth'

	export default {
		data() {
			return {
				mode: 'login',
				form: {
					username: '',
					password: '',
					nickname: '',
					phone: '',
					captchaKey: '',
					captchaCode: ''
				},
				errors: {},
				rememberMe: true,
				captchaSrc: '',
				submitting: false,
				redirect: ''
			}
		},
		onLoad(options) {
			if (options && options.redirect) this.redirect = decodeURIComponent(options.redirect)
			this.form.username = uni.getStorageSync('rememberedUsername') || ''
			this.loadCaptcha()
		},
		methods: {
			switchMode(mode) {
				if (this.submitting) return
				this.mode = mode
				this.errors = {}
				this.loadCaptcha()
			},
			clearError(key) {
				if (this.errors[key]) this.errors[key] = ''
			},
			/** 获取验证码：GET /api/auth/captcha → { captchaKey, captchaImg(base64) } */
			async loadCaptcha() {
				try {
					const data = await apiCaptcha()
					this.form.captchaKey = data.captchaKey
					this.form.captchaCode = ''
					this.captchaSrc = 'data:image/png;base64,' + data.captchaImg
				} catch (e) {
					this.captchaSrc = ''
					uni.showToast({ title: '验证码加载失败，点击重试', icon: 'none' })
				}
			},
			/** 前端表单校验：失败时聚焦首个错误项并红框提示 */
			validate() {
				const errs = {}
				if (!this.form.username.trim()) errs.username = '请输入账号'
				else if (this.mode === 'register' && this.form.username.trim().length < 3) errs.username = '账号长度至少 3 位'
				if (!this.form.password) errs.password = '请输入密码'
				else if (this.mode === 'register' && this.form.password.length < 6) errs.password = '密码长度 6-32 位'
				if (!this.form.captchaCode.trim()) errs.captchaCode = '请输入验证码'
				this.errors = errs
				return Object.keys(errs).length === 0
			},
			async submit() {
				if (this.submitting) return
				if (!this.validate()) return

				this.submitting = true
				try {
					if (this.mode === 'login') {
						const res = await apiLogin(
							{
								username: this.form.username.trim(),
								password: this.form.password,
								captchaKey: this.form.captchaKey,
								captchaCode: this.form.captchaCode.trim(),
								rememberMe: this.rememberMe
							},
							{ loading: true }
						)
						setAuth(res.token, res.user, res.expiresAt)
						if (this.rememberMe) uni.setStorageSync('rememberedUsername', this.form.username.trim())
						else uni.removeStorageSync('rememberedUsername')
						// 服务人员与用户同 App，按 role 由工作台承载（本端默认用户视角）
						if (res.user && res.user.role === 'ADMIN') {
							uni.showToast({ title: '管理端请在网页后台登录', icon: 'none' })
						}
						this.afterAuth()
					} else {
						await apiRegister(
							{
								username: this.form.username.trim(),
								password: this.form.password,
								nickname: this.form.nickname.trim(),
								phone: this.form.phone.trim(),
								role: 'USER',
								captchaKey: this.form.captchaKey,
								captchaCode: this.form.captchaCode.trim()
							},
							{ loading: true }
						)
						uni.showToast({ title: '注册成功，请登录', icon: 'success' })
						this.mode = 'login'
						this.form.password = ''
						this.loadCaptcha()
					}
				} catch (e) {
					// 登录失败（验证码错误/密码错误等）后端验证码一次性消费，必须刷新
					this.loadCaptcha()
					this.form.captchaCode = ''
				} finally {
					this.submitting = false
				}
			},
			afterAuth() {
				if (this.redirect && !isTabRoute(this.redirect)) {
					uni.redirectTo({ url: this.redirect })
				} else if (this.redirect && isTabRoute(this.redirect)) {
					uni.switchTab({ url: this.redirect })
				} else {
					uni.switchTab({ url: '/pages/index/index' })
				}
			},
			onForgot() {
				uni.showModal({
					title: '忘记密码',
					content: '请联系平台客服重置密码（暂未开放自助找回）',
					confirmText: '我知道了',
					showCancel: false
				})
			}
		}
	}
</script>

<style scoped>
	.login-page {
		padding: 96rpx 32rpx 64rpx;
		background: #FFFFFF;
	}
	.brand { display: flex; flex-direction: column; align-items: center; margin-bottom: 56rpx; }
	.brand-logo {
		width: 128rpx; height: 128rpx; line-height: 128rpx; text-align: center;
		background: var(--c-primary); color: #FFFFFF; font-size: 56rpx; font-weight: 600;
		border-radius: 32rpx;
	}
	.brand-name { font-size: 40rpx; font-weight: 600; margin-top: 24rpx; color: var(--c-text-title); }
	.brand-slogan { font-size: 26rpx; color: var(--c-text-weak); margin-top: 12rpx; }

	.seg {
		display: flex; background: #F2F3F5; border-radius: var(--radius-btn); padding: 8rpx; margin-bottom: 32rpx;
	}
	.seg-item {
		flex: 1; text-align: center; height: 72rpx; line-height: 72rpx;
		font-size: 30rpx; color: var(--c-text-secondary); border-radius: 16rpx;
	}
	.seg-item.active { background: #FFFFFF; color: var(--c-primary); font-weight: 600; box-shadow: var(--shadow-card); }

	.form-card { padding: 8rpx 32rpx; }
	.field {
		display: flex; flex-direction: row; align-items: center;
		min-height: 96rpx; border-bottom: 1px solid var(--c-border);
	}
	.field:last-of-type { border-bottom: none; }
	.field-label { width: 140rpx; font-size: 30rpx; color: var(--c-text-secondary); flex-shrink: 0; }
	.field-input { flex: 1; height: 96rpx; font-size: 30rpx; }
	.captcha-img { width: 180rpx; height: 72rpx; border-radius: 12rpx; background: #F0FAF4; flex-shrink: 0; }
	.field-error { display: block; color: var(--c-error); font-size: 26rpx; padding-bottom: 12rpx; }

	.remember { padding: 24rpx 0 8rpx; }
	.checkbox {
		width: 36rpx; height: 36rpx; border: 2rpx solid var(--c-input-border); border-radius: 8rpx;
		display: flex; align-items: center; justify-content: center;
	}
	.checkbox.checked { background: var(--c-primary); border-color: var(--c-primary); }
	.checkbox-mark { color: #FFFFFF; font-size: 24rpx; line-height: 1; }
	.remember-text { margin-left: 12rpx; }
	.link { color: var(--c-info); font-size: 28rpx; }
	.submit-btn { margin-top: 48rpx; }
	.agreement { display: block; text-align: center; margin-top: 32rpx; font-size: 24rpx; color: var(--c-text-weak); }
</style>
