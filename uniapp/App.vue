<script>
	export default {
		onLaunch: function() {
			console.log('App Launch')
		},
		onShow: function() {},
		onHide: function() {}
	}
</script>

<style>
	/* ========== 主题变量（温暖宠物风，见 文档/色彩搭配方案.md） ========== */
	page {
		--c-primary: #FF8A3D;
		--c-primary-weak: #FFF3EC;
		--c-success: #4ECB71;
		--c-info: #5B7FFF;
		--c-warning: #FFB020;
		--c-error: #FF4D4F;
		--c-disabled: #BFBFBF;
		--c-bg-page: #F7F8FA;
		--c-bg-card: #FFFFFF;
		--c-text-title: #1A1A1A;
		--c-text-secondary: #666666;
		--c-text-weak: #999999;
		--c-text-placeholder: #C0C4CC;
		--c-border: #EBEDF0;
		--c-input-border: #DCDEE0;
		--radius-card: 24rpx;
		--radius-btn: 24rpx;
		--shadow-card: 0 4px 12px rgba(0, 0, 0, 0.06);

		background-color: var(--c-bg-page);
		color: var(--c-text-title);
		font-family: "PingFang SC", "HarmonyOS Sans", "Source Han Sans SC", "Noto Sans SC", system-ui, -apple-system, sans-serif;
		font-size: 30rpx;
		line-height: 1.5;
	}

	view, text, image, input, textarea, button, scroll-view {
		box-sizing: border-box;
	}

	/* ========== 排版层级 ========== */
	.t-title { font-size: 40rpx; font-weight: 600; color: var(--c-text-title); }
	.t-module { font-size: 36rpx; font-weight: 600; color: var(--c-text-title); }
	.t-body { font-size: 30rpx; font-weight: 400; color: var(--c-text-title); }
	.t-sub { font-size: 26rpx; font-weight: 400; color: var(--c-text-secondary); }
	.t-weak { font-size: 26rpx; font-weight: 400; color: var(--c-text-weak); }
	.t-price { font-size: 36rpx; font-weight: 600; color: var(--c-primary); }
	.t-price-lg { font-size: 44rpx; font-weight: 600; color: var(--c-primary); }

	/* ========== 布局 ========== */
	.page {
		min-height: 100vh;
		padding-bottom: calc(32rpx + env(safe-area-inset-bottom));
	}
	.page-pad { padding-left: 32rpx; padding-right: 32rpx; }
	.card {
		background: var(--c-bg-card);
		border-radius: var(--radius-card);
		padding: 32rpx;
		box-shadow: var(--shadow-card);
	}
	.card + .card { margin-top: 24rpx; }
	.row { display: flex; flex-direction: row; align-items: center; }
	.row-between { display: flex; flex-direction: row; align-items: center; justify-content: space-between; }
	.flex-1 { flex: 1; min-width: 0; }
	.mt-8 { margin-top: 16rpx; }
	.mt-16 { margin-top: 32rpx; }
	.mt-24 { margin-top: 48rpx; }
	.ellipsis { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
	.ellipsis-2 {
		overflow: hidden;
		display: -webkit-box;
		-webkit-line-clamp: 2;
		-webkit-box-orient: vertical;
	}
	.divider { height: 1px; background: var(--c-border); margin: 24rpx 0; }

	/* ========== 按钮 ========== */
	.btn {
		height: 88rpx;
		line-height: 88rpx;
		border-radius: var(--radius-btn);
		font-size: 30rpx;
		font-weight: 500;
		text-align: center;
		padding: 0 40rpx;
		border: none;
		transition: opacity 0.15s ease, transform 0.15s ease;
	}
	.btn::after { border: none; }
	.btn-primary { background: var(--c-primary); color: #FFFFFF; }
	.btn-primary:active { opacity: 0.85; transform: scale(0.99); }
	.btn-outline { background: #FFFFFF; color: var(--c-primary); border: 2rpx solid var(--c-primary); }
	.btn-outline:active { background: var(--c-primary-weak); }
	.btn-text { background: transparent; color: var(--c-primary); height: 60rpx; line-height: 60rpx; padding: 0 16rpx; }
	.btn-danger { background: var(--c-error); color: #FFFFFF; }
	.btn-danger:active { opacity: 0.85; }
	.btn-disabled,
	.btn[disabled] {
		background: var(--c-disabled) !important;
		color: #FFFFFF !important;
		border-color: var(--c-disabled) !important;
		opacity: 1;
	}
	.btn-block { width: 100%; }
	.btn-sm { height: 64rpx; line-height: 64rpx; font-size: 26rpx; padding: 0 28rpx; border-radius: 16rpx; }

	/* ========== 表单 ========== */
	.form-item {
		display: flex;
		flex-direction: row;
		align-items: center;
		min-height: 88rpx;
		border-bottom: 1px solid var(--c-border);
	}
	.form-label { width: 176rpx; font-size: 30rpx; color: var(--c-text-secondary); flex-shrink: 0; }
	.form-input { flex: 1; height: 88rpx; font-size: 30rpx; color: var(--c-text-title); }
	.form-input-placeholder { color: var(--c-text-placeholder); }
	.input-box {
		background: #FFFFFF;
		border: 1px solid var(--c-input-border);
		border-radius: var(--radius-btn);
		height: 88rpx;
		padding: 0 24rpx;
	}
	.input-box-focus { border-color: var(--c-primary); }
	.input-box-error { border-color: var(--c-error); }
	.field-error { color: var(--c-error); font-size: 26rpx; margin-top: 8rpx; }

	/* ========== 标签 ========== */
	.tag {
		display: inline-block;
		font-size: 24rpx;
		line-height: 1;
		padding: 10rpx 16rpx;
		border-radius: 12rpx;
		background: var(--c-primary-weak);
		color: var(--c-primary);
	}
	.tag + .tag { margin-left: 12rpx; }

	/* ========== 状态提示条 ========== */
	.state-view { padding: 96rpx 32rpx; text-align: center; }
	.state-view .state-text { margin-top: 24rpx; font-size: 28rpx; color: var(--c-text-weak); }
	.state-view .state-action { margin-top: 32rpx; display: inline-block; }

	/* 骨架/加载 */
	.loading-line {
		height: 28rpx;
		border-radius: 14rpx;
		background: linear-gradient(90deg, #F1F2F5 25%, #E9EAEE 37%, #F1F2F5 63%);
		background-size: 400% 100%;
		animation: skeleton 1.4s ease infinite;
	}
	@keyframes skeleton {
		0% { background-position: 100% 50%; }
		100% { background-position: 0 50%; }
	}

	/* ========== 弹窗 ========== */
	.modal-mask {
		position: fixed;
		left: 0; top: 0; right: 0; bottom: 0;
		background: rgba(0, 0, 0, 0.45);
		z-index: 999;
		display: flex;
		align-items: center;
		justify-content: center;
	}
	.modal-panel {
		width: 608rpx;
		background: #FFFFFF;
		border-radius: var(--radius-card);
		padding: 40rpx 32rpx 32rpx;
		box-shadow: var(--shadow-card);
	}
	.modal-title { font-size: 36rpx; font-weight: 600; text-align: center; }
	.modal-text { font-size: 28rpx; color: var(--c-text-secondary); text-align: center; margin-top: 20rpx; line-height: 1.6; }
	.modal-actions { display: flex; flex-direction: row; margin-top: 40rpx; }
	.modal-actions .btn { flex: 1; }
	.modal-actions .btn + .btn { margin-left: 20rpx; }
</style>
