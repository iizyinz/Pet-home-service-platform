<template>
	<view class="page addr-edit-page">
		<state-view v-if="state === 'loading'" state="loading" :rows="3" />
		<state-view v-else-if="state === 'error'" state="error" :error-text="errorText" @retry="load" />

		<block v-else>
			<view class="card form-card">
				<view class="form-row">
					<text class="form-label">联系人 <text class="req">*</text></text>
					<input class="form-input" v-model="form.contactName" placeholder="收货人姓名" placeholder-class="form-input-placeholder" :maxlength="64" @input="clearError('contactName')" />
				</view>
				<text v-if="errors.contactName" class="field-error">{{ errors.contactName }}</text>

				<view class="form-row">
					<text class="form-label">手机号 <text class="req">*</text></text>
					<input class="form-input" v-model="form.contactPhone" type="number" placeholder="11 位手机号" placeholder-class="form-input-placeholder" :maxlength="11" @input="clearError('contactPhone')" />
				</view>
				<text v-if="errors.contactPhone" class="field-error">{{ errors.contactPhone }}</text>

				<view class="form-row">
					<text class="form-label">省市区</text>
					<picker
						class="region-picker flex-1"
						mode="multiSelector"
						:range="regionRange"
						:value="regionIdx"
						@columnchange="onRegionColumnChange"
						@change="onRegionChange"
					>
						<view class="picker-box">
							<text class="picker-text ellipsis" :class="{ placeholder: !regionDisplay }">{{ regionDisplay || '请选择省 / 市 / 区' }}</text>
							<text class="picker-arrow">›</text>
						</view>
					</picker>
				</view>

				<view class="form-row form-row-top">
					<text class="form-label">详细地址 <text class="req">*</text></text>
					<textarea class="form-textarea" v-model="form.detail" placeholder="街道、门牌号等（最多 255 字）" placeholder-class="form-input-placeholder" :maxlength="255" @input="clearError('detail')" />
				</view>
				<text v-if="errors.detail" class="field-error">{{ errors.detail }}</text>

				<view class="form-row">
					<text class="form-label">设为默认</text>
					<view class="flex-1 switch-wrap">
						<switch :checked="form.isDefault === 1" color="#FF8A3D" @change="onDefaultChange" />
					</view>
				</view>
			</view>

			<view class="foot-space"></view>
			<view class="action-bar">
				<button class="btn btn-primary btn-block" :class="{ 'btn-disabled': saving }" :disabled="saving" @tap="save">
					{{ saving ? '保存中...' : '保存地址' }}
				</button>
			</view>
		</block>
	</view>
</template>

<script>
	import StateView from '../../components/state-view/state-view.vue'
	import { apiAddresses, apiCreateAddress, apiUpdateAddress } from '../../api/index'
	import { ensureLogin } from '../../utils/auth'
	// 三级联动地区表（省 / 市 / 区），见 utils/areaData.ts
	import { provinceData } from '../../utils/areaData.ts'

	export default {
		components: { StateView },
		data() {
			return {
				state: 'ready',
				errorText: '',
				id: null,
				form: { contactName: '', contactPhone: '', region: '', detail: '', isDefault: 0 },
				// 省市区联动：三列下标 + 是否已选择 + 旧值兜底（原手工填写的 region）
				regionIdx: [0, 0, 0],
				regionTouched: false,
				legacyRegion: '',
				errors: {},
				saving: false
			}
		},
		computed: {
			/** multiSelector 三列数据：省 → 当前省的市 → 当前市的区 */
			regionRange() {
				const provs = provinceData || []
				const cities = (provs[this.regionIdx[0]] && provs[this.regionIdx[0]].children) || []
				const dists = (cities[this.regionIdx[1]] && cities[this.regionIdx[1]].children) || []
				return [
					provs.map((p) => p.name),
					cities.map((c) => c.name),
					dists.map((d) => d.name)
				]
			},
			/** 展示文案：已选择显示联动结果，否则回显旧数据 */
			regionDisplay() {
				if (!this.regionTouched) return this.legacyRegion || ''
				return this.buildRegionText()
			}
		},
		onLoad(options) {
			if (!ensureLogin()) return
			this.id = options && options.id ? Number(options.id) : null
			uni.setNavigationBarTitle({ title: this.id ? '编辑地址' : '添加地址' })
			if (this.id) this.load()
		},
		methods: {
			async load() {
				this.state = 'loading'
				try {
					const list = (await apiAddresses()) || []
					const hit = list.find((a) => a.id === this.id)
					if (!hit) throw { code: 2011, message: '地址不存在或已被删除' }
					this.form = {
						contactName: hit.contactName || '',
						contactPhone: hit.contactPhone || '',
						region: hit.region || '',
						detail: hit.detail || '',
						isDefault: hit.isDefault || 0
					}
					this.syncRegion(hit.region)
					this.state = 'ready'
				} catch (e) {
					this.state = 'error'
					this.errorText = (e && e.message) || '地址加载失败'
				}
			},
			/** 列切换联动：省变化重置市/区，市变化重置区 */
			onRegionColumnChange(e) {
				const { column, value } = e.detail
				const idx = this.regionIdx.slice()
				idx[column] = Number(value) || 0
				if (column === 0) {
					idx[1] = 0
					idx[2] = 0
				} else if (column === 1) {
					idx[2] = 0
				}
				this.regionIdx = idx
			},
			/** 确认选择 */
			onRegionChange(e) {
				const v = (e.detail && e.detail.value) || [0, 0, 0]
				const provs = provinceData || []
				const pi = Math.min(Math.max(Number(v[0]) || 0, 0), provs.length - 1)
				const cities = (provs[pi] && provs[pi].children) || []
				const ci = cities.length ? Math.min(Math.max(Number(v[1]) || 0, 0), cities.length - 1) : 0
				const dists = (cities[ci] && cities[ci].children) || []
				const di = dists.length ? Math.min(Math.max(Number(v[2]) || 0, 0), dists.length - 1) : 0
				this.regionIdx = [pi, ci, di]
				this.regionTouched = true
				this.legacyRegion = ''
			},
			/** 解析已保存的 region 文本，尽力回填三列下标 */
			parseRegion(str) {
				const parts = String(str || '')
					.split(/[\s,，、/·]+/)
					.filter(Boolean)
				if (!parts.length) return null
				const matchIdx = (list, name) => {
					if (!list || !list.length) return -1
					let i = list.findIndex((x) => x.name === name)
					if (i >= 0) return i
					i = list.findIndex((x) => x.name.indexOf(name) > -1 || name.indexOf(x.name) > -1)
					return i
				}
				let pi = -1
				for (const p of parts) {
					pi = matchIdx(provinceData, p)
					if (pi >= 0) break
				}
				let ci = -1
				if (pi >= 0) {
					const cities = provinceData[pi].children || []
					for (const p of parts) {
						ci = matchIdx(cities, p)
						if (ci >= 0) break
					}
				}
				if (ci < 0) {
					// 市全局反查（如旧数据省略省：「杭州市 西湖区」）
					for (let i = 0; i < provinceData.length && ci < 0; i++) {
						const cities = provinceData[i].children || []
						for (const p of parts) {
							const k = matchIdx(cities, p)
							if (k >= 0) {
								pi = i
								ci = k
								break
							}
						}
					}
				}
				let di = -1
				if (pi >= 0 && ci >= 0) {
					const dists = (provinceData[pi].children[ci] || {}).children || []
					for (const p of parts) {
						di = matchIdx(dists, p)
						if (di >= 0) break
					}
				}
				const ok = pi >= 0 && ci >= 0 && di >= 0
				return {
					pi: Math.max(pi, 0),
					ci: Math.max(ci, 0),
					di: Math.max(di, 0),
					ok
				}
			},
			/** 用旧 region 字串初始化联动选择 */
			syncRegion(str) {
				const parsed = this.parseRegion(str)
				if (!parsed) {
					this.regionIdx = [0, 0, 0]
					this.regionTouched = false
					this.legacyRegion = ''
					return
				}
				this.regionIdx = [parsed.pi, parsed.ci, parsed.di]
				this.regionTouched = parsed.ok
				this.legacyRegion = parsed.ok ? '' : str || ''
			},
			/** 当前三列名称（直辖市省=市同名时去重） */
			buildRegionText() {
				const provs = provinceData || []
				const p = provs[this.regionIdx[0]]
				if (!p) return ''
				const c = (p.children || [])[this.regionIdx[1]]
				const d = (c && (c.children || [])[this.regionIdx[2]]) || null
				const names = [p.name]
				if (c && c.name !== p.name) names.push(c.name)
				if (d) names.push(d.name)
				return names.join(' ')
			},
			clearError(key) {
				if (this.errors[key]) this.errors[key] = ''
			},
			onDefaultChange(e) {
				this.form.isDefault = e.detail.value ? 1 : 0
			},
			validate() {
				const errs = {}
				if (!this.form.contactName.trim()) errs.contactName = '联系人不能为空'
				if (!/^1\d{10}$/.test(this.form.contactPhone.trim())) errs.contactPhone = '请输入正确的 11 位手机号'
				if (!this.form.detail.trim()) errs.detail = '详细地址不能为空'
				this.errors = errs
				if (Object.keys(errs).length) {
					uni.showToast({ title: Object.values(errs)[0], icon: 'none' })
					return false
				}
				return true
			},
			async save() {
				if (this.saving) return
				if (!this.validate()) return
				const payload = {
					contactName: this.form.contactName.trim(),
					contactPhone: this.form.contactPhone.trim(),
					// 优先取联动选择结果，未选择时回显原手工填写值
					region: (this.regionTouched ? this.buildRegionText() : this.legacyRegion).trim(),
					detail: this.form.detail.trim(),
					isDefault: this.form.isDefault
				}
				this.saving = true
				try {
					if (this.id) {
						await apiUpdateAddress(this.id, payload, { loading: true })
					} else {
						await apiCreateAddress(payload, { loading: true })
					}
					uni.showToast({ title: '保存成功', icon: 'success' })
					setTimeout(() => uni.navigateBack(), 600)
				} catch (e) {
					if (e && e.code === 2011) uni.showToast({ title: '地址不存在或已被删除', icon: 'none' })
				} finally {
					this.saving = false
				}
			}
		}
	}
</script>

<style scoped>
	.addr-edit-page { padding-bottom: calc(140rpx + env(safe-area-inset-bottom)); }
	.card { margin: 24rpx 32rpx 0; padding: 8rpx 32rpx; }
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
	.picker-box {
		display: flex; flex-direction: row; align-items: center; justify-content: flex-end;
		height: 96rpx;
	}
	.picker-text { font-size: 30rpx; color: var(--c-text-title); max-width: 420rpx; text-align: right; }
	.picker-text.placeholder { color: var(--c-text-placeholder); }
	.picker-arrow { font-size: 36rpx; color: var(--c-text-weak); margin-left: 12rpx; }
	.switch-wrap { display: flex; justify-content: flex-end; }
	.foot-space { height: 32rpx; }
	.action-bar {
		position: fixed; left: 0; right: 0; bottom: 0;
		background: #FFFFFF; box-shadow: var(--shadow-card);
		padding: 16rpx 32rpx calc(16rpx + env(safe-area-inset-bottom));
	}
</style>
