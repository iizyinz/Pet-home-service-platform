<template>
	<view class="page create-page">
		<state-view v-if="state === 'loading'" state="loading" :rows="4" />
		<state-view v-else-if="state === 'error'" state="error" :error-text="errorText" @retry="init" />

		<block v-else>
			<!-- 已选服务 -->
			<view class="card" v-if="item">
				<view class="row">
					<image class="item-cover" :src="item.coverUrl || placeholder" mode="aspectFill" />
					<view class="flex-1 item-info">
						<text class="t-body ellipsis-2">{{ item.name }}</text>
						<text class="t-weak" v-if="item.durationMinutes">时长 {{ item.durationMinutes }} 分钟</text>
					</view>
					<view class="price-wrap">
						<text class="price-unit">¥</text>
						<text class="t-price">{{ money(item.price) }}</text>
					</view>
				</view>
			</view>
			<view class="card" v-else>
				<text class="t-weak">请先选择服务项目</text>
				<button class="btn btn-outline btn-sm mt-16" @tap="goPickItem">去选择服务项目</button>
			</view>

			<!-- 表单 -->
			<view class="card form-card">
				<!-- 宠物 -->
				<view class="form-row" @tap="openPicker('pet')">
					<text class="form-label">宠物</text>
					<text class="form-value" :class="{ placeholder: !form.petId }">
						{{ selectedPet ? selectedPet.name + '（' + (selectedPet.breed || '品种未填') + '）' : '请选择宠物' }}
					</text>
					<text class="arrow">›</text>
				</view>
				<text v-if="errors.petId" class="field-error">{{ errors.petId }}</text>

				<!-- 服务人员 -->
				<view class="form-row" @tap="openPicker('staff')">
					<text class="form-label">服务师</text>
					<text class="form-value" :class="{ placeholder: !form.staffId }">
						{{ selectedStaff ? selectedStaff.nickname : '请选择服务人员' }}
					</text>
					<text class="arrow">›</text>
				</view>
				<text v-if="errors.staffId" class="field-error">{{ errors.staffId }}</text>

				<!-- 上门地址 -->
				<view class="form-row" @tap="openPicker('address')">
					<text class="form-label">上门地址</text>
					<text class="form-value ellipsis" :class="{ placeholder: !form.addressId }">
						{{ selectedAddress ? selectedAddress.region + ' ' + selectedAddress.detail : '请选择地址' }}
					</text>
					<text class="arrow">›</text>
				</view>
				<text v-if="errors.addressId" class="field-error">{{ errors.addressId }}</text>

				<!-- 预约时间：日期 picker + 时段 picker 组合 -->
				<view class="form-row">
					<text class="form-label">预约时间</text>
					<picker class="time-cell" mode="date" :start="dateStart" :end="dateEnd" :value="dateValue" @change="onDateChange">
						<text class="form-value" :class="{ placeholder: !dateValue }">{{ dateValue || '选择日期' }}</text>
					</picker>
					<picker class="time-cell" mode="selector" :range="timeRange" :disabled="!dateValue" @change="onTimeChange">
						<text class="form-value" :class="{ placeholder: !timeValue, 'time-disabled': !dateValue }">
							{{ timeValue || '选择时段' }}
						</text>
					</picker>
				</view>
				<text v-if="errors.appointmentTime" class="field-error">{{ errors.appointmentTime }}</text>

				<!-- 备注 -->
				<view class="form-row form-row-top">
					<text class="form-label">备注</text>
					<textarea
						class="form-textarea"
						v-model="form.remark"
						placeholder="选填：宠物习性、注意事项等（最多 255 字）"
						placeholder-class="form-input-placeholder"
						:maxlength="255"
						:auto-height="true"
					/>
				</view>
			</view>

			<!-- 费用明细 -->
			<view class="card">
				<text class="t-module">费用明细</text>
				<view class="row-between fee-row">
					<text class="t-sub">服务费（按次计费）</text>
					<text class="t-body">¥{{ money(item ? item.price : 0) }}</text>
				</view>
				<view class="divider"></view>
				<view class="row-between fee-row">
					<text class="t-body">应付金额</text>
					<text class="t-price">¥{{ money(item ? item.price : 0) }}</text>
				</view>
				<view class="row-between fee-row">
					<text class="t-sub">当前余额</text>
					<text class="t-sub" :class="{ 'balance-low': balanceLow }">¥{{ money(balance) }}</text>
				</view>
				<view v-if="balanceLow" class="balance-tip">
					<text class="balance-tip-text">余额不足，请先充值</text>
					<text class="link" @tap="goRecharge">去充值 ›</text>
				</view>
			</view>

			<view class="foot-space"></view>

			<!-- 底部提交 -->
			<view class="action-bar">
				<view class="total">
					<text class="t-sub">合计：</text>
					<text class="t-price-lg">¥{{ money(item ? item.price : 0) }}</text>
				</view>
				<button
					class="btn btn-primary submit-btn"
					:class="{ 'btn-disabled': submitting || !canSubmit }"
					:disabled="submitting || !canSubmit"
					@tap="submit"
				>
					{{ submitting ? '提交中...' : (balanceLow ? '余额不足' : '提交预约') }}
				</button>
			</view>
		</block>

		<!-- 选择弹窗：宠物 / 服务师 / 地址 -->
		<view class="modal-mask" v-if="pickerType" @tap="closePicker">
			<view class="picker-panel" @tap.stop>
				<view class="row-between picker-head">
					<text class="t-module">{{ pickerTitle }}</text>
					<text class="picker-close" @tap="closePicker">✕</text>
				</view>

				<scroll-view scroll-y class="picker-body">
					<state-view v-if="pickerLoading" state="loading" :rows="3" />
					<state-view v-else-if="pickerError" state="error" error-text="数据加载失败" @retry="loadPicker" />

					<!-- 宠物 -->
					<block v-else-if="pickerType === 'pet'">
						<view
							class="option"
							:class="{ active: form.petId === p.id }"
							v-for="p in pets"
							:key="p.id"
							@tap="choosePet(p)"
						>
							<image class="option-avatar" :src="p.avatar || placeholder" mode="aspectFill" />
							<view class="flex-1">
								<text class="t-body">{{ p.name }}</text>
								<text class="t-weak option-sub">{{ p.breed || '品种未填' }} · {{ p.age || '年龄未填' }}</text>
							</view>
							<text class="check" v-if="form.petId === p.id">✓</text>
						</view>
						<view v-if="!pets.length" class="state-view">
							<text class="state-text">还没有宠物档案</text>
							<button class="btn btn-outline btn-sm state-action" @tap="goPetEdit">去添加宠物</button>
						</view>
					</block>

					<!-- 服务师 -->
					<block v-else-if="pickerType === 'staff'">
						<view
							class="option"
							:class="{ active: form.staffId === s.id }"
							v-for="s in staffs"
							:key="s.id"
							@tap="chooseStaff(s)"
						>
							<image class="option-avatar" :src="s.avatar || placeholder" mode="aspectFill" />
							<view class="flex-1">
								<text class="t-body">{{ s.nickname }}</text>
								<text class="t-weak option-sub">{{ s.areaName || '区域未标注' }} · 评分 {{ ratingText(s.avgRating) }} · 完成 {{ s.completedCount || 0 }} 单</text>
							</view>
							<text class="check" v-if="form.staffId === s.id">✓</text>
						</view>
						<view v-if="!staffs.length" class="state-view">
							<text class="state-text">暂无可选服务师</text>
							<button class="btn btn-outline btn-sm state-action" @tap="goStaffList">去服务人员页</button>
						</view>
					</block>

					<!-- 地址 -->
					<block v-else>
						<view
							class="option"
							:class="{ active: form.addressId === a.id }"
							v-for="a in addresses"
							:key="a.id"
							@tap="chooseAddress(a)"
						>
							<view class="flex-1">
								<text class="t-body">{{ a.contactName }} {{ a.contactPhone }}</text>
								<text class="t-weak option-sub ellipsis-2">{{ a.region }} {{ a.detail }}</text>
								<text class="mini-tag" v-if="a.isDefault === 1">默认</text>
							</view>
							<text class="check" v-if="form.addressId === a.id">✓</text>
						</view>
						<view v-if="!addresses.length" class="state-view">
							<text class="state-text">还没有上门地址</text>
							<button class="btn btn-outline btn-sm state-action" @tap="goAddressEdit">去添加地址</button>
						</view>
					</block>
				</scroll-view>
			</view>
		</view>
	</view>
</template>

<script>
	import StateView from '../../components/state-view/state-view.vue'
	import {
		apiServiceItemDetail, apiPets, apiAddresses, apiStaffs,
		apiBalance, apiCreateOrder
	} from '../../api/index'
	import { money } from '../../utils/format'
	import { ensureLogin } from '../../utils/auth'

	const PLACEHOLDER = '/static/logo.png'

	export default {
		components: { StateView },
		data() {
			return {
				state: 'loading',
				errorText: '',
				itemId: null,
				item: null,
				balance: 0,
				form: {
					petId: null,
					serviceItemId: null,
					staffId: null,
					addressId: null,
					appointmentTime: '',
					remark: ''
				},
				errors: {},
				dateStart: '',
				dateEnd: '',
				dateValue: '',
				timeValue: '',
				timeRange: [],
				pets: [],
				staffs: [],
				addresses: [],
				selectedPet: null,
				selectedStaff: null,
				selectedAddress: null,
				pickerType: '', // pet | staff | address
				pickerLoading: false,
				pickerError: false,
				submitting: false,
				placeholder: PLACEHOLDER
			}
		},
		computed: {
			pickerTitle() {
				return { pet: '选择宠物', staff: '选择服务人员', address: '选择上门地址' }[this.pickerType] || ''
			},
			price() {
				return this.item ? Number(this.item.price) : 0
			},
			balanceLow() {
				return this.balance > 0 && this.price > this.balance
			},
			canSubmit() {
				return !!(this.form.petId && this.form.staffId && this.form.addressId && this.form.appointmentTime)
			}
		},
		onLoad(options) {
			if (!ensureLogin()) return
			if (options && options.serviceItemId) this.itemId = Number(options.serviceItemId)
			this.form.staffId = options && options.staffId ? Number(options.staffId) : null
			this.initTimeOptions()
			this.init()
		},
		methods: {
			/** 可约日期为今天起 14 天；时段 08:00-21:30 每 30 分钟 */
			initTimeOptions() {
				const pad = (n) => (n < 10 ? '0' + n : '' + n)
				const now = new Date()
				const end = new Date(now.getTime() + 13 * 24 * 60 * 60 * 1000)
				this.dateStart = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
				this.dateEnd = `${end.getFullYear()}-${pad(end.getMonth() + 1)}-${pad(end.getDate())}`
				const range = []
				for (let h = 8; h <= 21; h++) {
					range.push(`${pad(h)}:00`)
					if (h < 21) range.push(`${pad(h)}:30`)
				}
				this.timeRange = range
			},
			money,
			ratingText(v) {
				return v === null || v === undefined ? '暂无' : Number(v).toFixed(1)
			},
			async init() {
				this.state = 'loading'
				try {
					if (this.itemId) {
						this.item = await apiServiceItemDetail(this.itemId)
						this.form.serviceItemId = this.item.id
					}
					const balance = await apiBalance()
					this.balance = Number(balance.balance) || 0
					// 一次性预载三类选择数据，失败单独兜底
					await Promise.all([
						apiPets().then((d) => { this.pets = d || [] }).catch(() => { this.pets = [] }),
						apiAddresses().then((d) => { this.addresses = d || [] }).catch(() => { this.addresses = [] }),
						apiStaffs({ page: 1, size: 20 }).then((d) => { this.staffs = (d && d.list) || [] }).catch(() => { this.staffs = [] })
					])
					this.syncSelection()
					this.state = 'ready'
				} catch (e) {
					this.state = 'error'
					this.errorText = (e && e.message) || '页面初始化失败'
				}
			},
			/** 按已传入参数（服务项目/服务师）回填选择 */
			syncSelection() {
				this.selectedPet = this.pets.find((p) => p.id === this.form.petId) || null
				this.selectedStaff = this.staffs.find((s) => s.id === this.form.staffId) || null
				this.selectedAddress = this.addresses.find((a) => a.id === this.form.addressId) || null
				if (!this.selectedAddress) {
					const def = this.addresses.find((a) => a.isDefault === 1)
					if (def) this.chooseAddress(def, true)
				}
			},
			openPicker(type) {
				this.pickerType = type
				this.pickerError = false
				this.pickerLoading = false
			},
			async loadPicker() {
				this.pickerLoading = true
				this.pickerError = false
				try {
					if (this.pickerType === 'pet') this.pets = (await apiPets()) || []
					if (this.pickerType === 'address') this.addresses = (await apiAddresses()) || []
					if (this.pickerType === 'staff') {
						const d = await apiStaffs({ page: 1, size: 20 })
						this.staffs = (d && d.list) || []
					}
				} catch (e) {
					this.pickerError = true
				} finally {
					this.pickerLoading = false
				}
			},
			closePicker() {
				this.pickerType = ''
			},
			choosePet(p, silent) {
				this.form.petId = p.id
				this.selectedPet = p
				delete this.errors.petId
				if (!silent) this.closePicker()
			},
			chooseStaff(s) {
				this.form.staffId = s.id
				this.selectedStaff = s
				delete this.errors.staffId
				this.closePicker()
			},
			chooseAddress(a, silent) {
				this.form.addressId = a.id
				this.selectedAddress = a
				delete this.errors.addressId
				if (!silent) this.closePicker()
			},
			/** 预约时间 = 日期 picker + 时段 picker 组合，服务端二次校验必须晚于当前时间 */
			pad(n) {
				return n < 10 ? '0' + n : '' + n
			},
			onDateChange(e) {
				this.dateValue = e.detail.value
				this.timeValue = ''
				this.composeAppointmentTime()
			},
			onTimeChange(e) {
				this.timeValue = this.timeRange[Number(e.detail.value)]
				this.composeAppointmentTime()
			},
			composeAppointmentTime() {
				if (!this.dateValue || !this.timeValue) return
				const full = this.dateValue + ' ' + this.timeValue + ':00'
				const ts = new Date(full.replace(/-/g, '/')).getTime()
				if (ts <= Date.now()) {
					this.form.appointmentTime = ''
					this.errors.appointmentTime = '预约时间必须晚于当前时间'
					return
				}
				this.form.appointmentTime = full
				this.errors.appointmentTime = ''
			},
			validate() {
				const errs = {}
				if (!this.form.serviceItemId) errs.serviceItemId = '请先选择服务项目'
				if (!this.form.petId) errs.petId = '请选择宠物'
				if (!this.form.staffId) errs.staffId = '请选择服务人员'
				if (!this.form.addressId) errs.addressId = '请选择上门地址'
				if (!this.form.appointmentTime) errs.appointmentTime = '请选择预约时间'
				this.errors = errs
				if (Object.keys(errs).length) {
					uni.showToast({ title: Object.values(errs)[0], icon: 'none' })
					return false
				}
				return true
			},
			/**
			 * 提交下单：POST /api/orders
			 * 后端校验余额 ≥ 金额（AC-01），不足返回 2001；成功返回订单（status=PENDING_CONFIRM）
			 */
			async submit() {
				if (this.submitting) return
				if (!this.validate()) return

				this.submitting = true
				try {
					const order = await apiCreateOrder(this.form, { loading: true })
					uni.showToast({ title: '预约成功', icon: 'success' })
					setTimeout(() => {
						uni.redirectTo({ url: '/pages/order/detail?id=' + order.id })
					}, 600)
				} catch (e) {
					// 2001 余额不足：刷新余额并引导充值
					if (e && e.code === 2001) {
						this.refreshBalance()
						uni.showModal({
							title: '余额不足',
							content: '当前余额不足以支付本单，请先充值后再提交。',
							confirmText: '去充值',
							cancelText: '取消',
							success: (r) => {
								if (r.confirm) this.goRecharge()
							}
						})
					} else if (e && e.code === 2016) {
						this.errors.appointmentTime = '预约时间必须晚于当前时间'
					} else if (e && e.code === 400) {
						uni.showToast({ title: '请检查表单填写是否完整', icon: 'none' })
					}
				} finally {
					this.submitting = false
				}
			},
			async refreshBalance() {
				try {
					const b = await apiBalance()
					this.balance = Number(b.balance) || 0
				} catch (e) { /* 忽略 */ }
			},
			goRecharge() {
				uni.navigateTo({ url: '/pages/user/balance?recharge=1' })
			},
			goPickItem() {
				uni.switchTab({ url: '/pages/service/list' })
			},
			goPetEdit() {
				uni.navigateTo({ url: '/pages/user/pet-edit' })
			},
			goAddressEdit() {
				uni.navigateTo({ url: '/pages/user/address-edit' })
			},
			goStaffList() {
				uni.navigateTo({ url: '/pages/staff/list' })
			}
		}
	}
</script>

<style scoped>
	.create-page { padding-bottom: calc(140rpx + env(safe-area-inset-bottom)); }
	.card { margin: 24rpx 32rpx 0; }
	.item-cover { width: 120rpx; height: 120rpx; border-radius: 16rpx; background: #F2F3F5; flex-shrink: 0; }
	.item-info { margin-left: 20rpx; display: flex; flex-direction: column; }
	.item-info .t-weak { margin-top: 8rpx; }
	.price-wrap { display: flex; flex-direction: row; align-items: baseline; }
	.price-unit { font-size: 24rpx; color: var(--c-primary); }

	.form-card { padding: 8rpx 32rpx; }
	.form-row {
		display: flex; flex-direction: row; align-items: center;
		min-height: 96rpx; border-bottom: 1px solid var(--c-border);
	}
	.form-row:last-child { border-bottom: none; }
	.form-row-top { align-items: flex-start; padding: 24rpx 0; }
	.form-label { width: 176rpx; font-size: 30rpx; color: var(--c-text-secondary); flex-shrink: 0; }
	.form-value { flex: 1; font-size: 30rpx; color: var(--c-text-title); text-align: right; }
	.form-value.placeholder { color: var(--c-text-placeholder); }
	.time-cell { flex: 1; text-align: right; }
	.time-cell + .time-cell { margin-left: 16rpx; }
	.form-value.time-disabled { color: var(--c-disabled); }
	.arrow { color: var(--c-text-weak); font-size: 32rpx; margin-left: 12rpx; }
	.form-textarea { flex: 1; font-size: 28rpx; min-height: 96rpx; line-height: 1.6; text-align: left; }

	.fee-row { padding: 12rpx 0; }
	.balance-low { color: var(--c-error) !important; }
	.balance-tip {
		display: flex; flex-direction: row; align-items: center; justify-content: space-between;
		background: #FFF1F0; border-radius: 16rpx; padding: 20rpx 24rpx; margin-top: 16rpx;
	}
	.balance-tip-text { color: var(--c-error); font-size: 26rpx; }
	.link { color: var(--c-info); font-size: 26rpx; }

	.foot-space { height: 32rpx; }
	.action-bar {
		position: fixed; left: 0; right: 0; bottom: 0;
		background: #FFFFFF; box-shadow: var(--shadow-card);
		display: flex; flex-direction: row; align-items: center; justify-content: space-between;
		padding: 16rpx 32rpx calc(16rpx + env(safe-area-inset-bottom));
		z-index: 20;
	}
	.total { display: flex; flex-direction: row; align-items: baseline; }
	.submit-btn { width: 320rpx; }

	/* 选择弹窗 */
	.picker-panel {
		width: 100%; max-height: 70vh; background: #FFFFFF;
		border-radius: 32rpx 32rpx 0 0; display: flex; flex-direction: column;
		position: fixed; left: 0; bottom: 0; padding-bottom: env(safe-area-inset-bottom);
	}
	.modal-mask { align-items: flex-end; }
	.picker-head { padding: 32rpx 32rpx 16rpx; }
	.picker-close { font-size: 36rpx; color: var(--c-text-weak); padding: 0 8rpx; }
	.picker-body { flex: 1; max-height: 56vh; padding: 0 32rpx 32rpx; }
	.option {
		display: flex; flex-direction: row; align-items: center;
		padding: 24rpx; border-bottom: 1px solid var(--c-border);
		border-radius: 16rpx; margin-bottom: 8rpx;
	}
	.option.active { background: var(--c-primary-weak); }
	.option-avatar { width: 72rpx; height: 72rpx; border-radius: 50%; background: #F2F3F5; margin-right: 20rpx; flex-shrink: 0; }
	.option-sub { display: block; margin-top: 8rpx; }
	.check { color: var(--c-primary); font-size: 36rpx; font-weight: 600; margin-left: 16rpx; }
</style>
