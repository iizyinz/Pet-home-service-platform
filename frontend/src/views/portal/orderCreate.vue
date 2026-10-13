<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import PageCard from '@/components/common/PageCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import RatingStars from '@/components/common/RatingStars.vue'
import { fetchServiceItem } from '@/api/catalog'
import { fetchStaff } from '@/api/staffs'
import { fetchPets, fetchAddresses } from '@/api/pets'
import { fetchBalance, recharge } from '@/api/user'
import { createOrder } from '@/api/orders'
import { fmtMoney } from '@/utils/constants'
import type { Pet, UserAddress, ServiceItem, StaffDetailVO } from '@/types'

/**
 * 预约下单页（PRD 5.4）：
 * 选择宠物 + 服务项目 + 服务师 + 上门地址 + 预约时间 + 备注
 * 【强制】提交时校验余额 ≥ 订单金额，不足提示充值；成功生成 SO 单号并写消费流水
 */
const route = useRoute()
const router = useRouter()

const itemId = computed(() => Number(route.query.itemId || 0))
const staffId = computed(() => Number(route.query.staffId || 0))

const item = ref<ServiceItem | null>(null)
const staff = ref<StaffDetailVO | null>(null)
const pets = ref<Pet[]>([])
const addresses = ref<UserAddress[]>([])
const balance = ref(0)
const loading = ref(true)
const submitting = ref(false)
const insufficient = ref(false)

const formRef = ref<FormInstance>()
const form = reactive({
  petId: undefined as number | undefined,
  addressId: undefined as number | undefined,
  appointmentTime: '',
  remark: '',
})

const rules: FormRules = {
  petId: [{ required: true, type: 'number' as const, message: '请选择宠物', trigger: 'change' }],
  addressId: [{ required: true, type: 'number' as const, message: '请选择上门地址', trigger: 'change' }],
  appointmentTime: [{ required: true, message: '请选择预约时间', trigger: 'change' }],
}

const amount = computed(() => Number(item.value?.price ?? 0))
const enough = computed(() => balance.value >= amount.value)

/** 禁选过去时间 */
function disabledDate(d: Date): boolean {
  return d.getTime() < Date.now() - 24 * 60 * 60 * 1000
}

async function load() {
  loading.value = true
  try {
    const [i, s, p, a, b] = await Promise.all([
      itemId.value ? fetchServiceItem(itemId.value) : Promise.resolve(null),
      staffId.value ? fetchStaff(staffId.value) : Promise.resolve(null),
      fetchPets(),
      fetchAddresses(),
      fetchBalance(),
    ])
    item.value = i
    staff.value = s
    pets.value = p ?? []
    addresses.value = a ?? []
    balance.value = Number(b?.balance ?? 0)

    if (!pets.value.length) ElMessage.warning('请先新增宠物档案再下单')
    if (!addresses.value.length) ElMessage.warning('请先新增上门地址再下单')
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!item.value || !staff.value) {
    ElMessage.error('缺少服务项目或服务师信息，请从列表重新进入')
    return
  }
  if (!enough.value) {
    insufficient.value = true
    return
  }

  submitting.value = true
  try {
    const order = await createOrder({
      petId: form.petId as number,
      serviceItemId: item.value.id,
      staffId: staff.value.id,
      addressId: form.addressId as number,
      appointmentTime: form.appointmentTime,
      remark: form.remark || undefined,
    })
    ElMessage.success('预约成功，已从余额扣款')
    router.replace(`/orders/${order.id}`)
  } catch (e) {
    const msg = (e as Error)?.message || ''
    if (msg.includes('余额不足')) insufficient.value = true
  } finally {
    submitting.value = false
  }
}

/** 模拟充值后重新提交 */
async function doRecharge() {
  try {
    const res = await recharge(Math.max(amount.value - balance.value, 10))
    balance.value = Number(res?.balance ?? 0)
    ElMessage.success('充值成功')
    insufficient.value = false
  } catch {
    /* 拦截器已提示 */
  }
}

onMounted(load)
</script>

<template>
  <div class="order-create" v-loading="loading">
    <div class="order-create__topbar">
      <el-button link :icon="ArrowLeft" @click="router.back()">返回</el-button>
      <span class="order-create__title">预约下单</span>
    </div>

    <EmptyState
      v-if="!loading && (!item || !staff)"
      text="缺少服务项目或服务师信息"
      action-text="返回服务列表"
      @action="router.push('/services')"
    />

    <el-row v-else :gutter="16">
      <!-- 预约表单 -->
      <el-col :xs="24" :lg="15">
        <PageCard title="预约信息">
          <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
            <el-form-item label="服务项目">
              <div class="order-create__readonly">
                <span class="biz-tag">上门服务</span>
                <strong>{{ item?.name }}</strong>
                <span class="aux-text">{{ item?.durationMinutes }} 分钟</span>
              </div>
            </el-form-item>

            <el-form-item label="服务师">
              <div class="order-create__readonly">
                <el-avatar :size="32">
                  <img v-if="staff?.avatar" :src="staff.avatar" alt="" />
                  <span v-else>{{ (staff?.nickname || '师')[0] }}</span>
                </el-avatar>
                <strong>{{ staff?.nickname || '服务师' }}</strong>
                <RatingStars :model-value="Number(staff?.avgRating || 0)" size="small" readonly />
                <span class="aux-text">{{ staff?.completedCount ?? 0 }} 单</span>
              </div>
            </el-form-item>

            <el-form-item label="宠物" prop="petId">
              <el-select v-model="form.petId" placeholder="选择宠物" style="width: 100%">
                <el-option v-for="p in pets" :key="p.id" :label="`${p.name}（${p.breed || '未知品种'}）`" :value="p.id" />
              </el-select>
              <div class="order-create__hint aux-text">
                没有档案？
                <el-button link type="primary" size="small" @click="router.push('/pets')">去新增宠物</el-button>
              </div>
            </el-form-item>

            <el-form-item label="上门地址" prop="addressId">
              <el-select v-model="form.addressId" placeholder="选择地址" style="width: 100%">
                <el-option
                  v-for="a in addresses"
                  :key="a.id"
                  :label="`${a.contactName} ${a.contactPhone} · ${a.region || ''}${a.detail}`"
                  :value="a.id"
                />
              </el-select>
              <div class="order-create__hint aux-text">
                没有地址？
                <el-button link type="primary" size="small" @click="router.push('/addresses')">去新增地址</el-button>
              </div>
            </el-form-item>

            <el-form-item label="预约时间" prop="appointmentTime">
              <el-date-picker
                v-model="form.appointmentTime"
                type="datetime"
                placeholder="选择上门时间"
                value-format="YYYY-MM-DD HH:mm:ss"
                :disabled-date="disabledDate"
                :clearable="false"
                style="width: 100%"
              />
            </el-form-item>

            <el-form-item label="备注">
              <el-input
                v-model="form.remark"
                type="textarea"
                :rows="3"
                maxlength="255"
                show-word-limit
                placeholder="宠物性格、门禁、联系方式补充说明（选填）"
              />
            </el-form-item>
          </el-form>
        </PageCard>
      </el-col>

      <!-- 费用明细 -->
      <el-col :xs="24" :lg="9">
        <PageCard title="费用明细">
          <div class="fee">
            <div class="fee__row">
              <span>服务项目</span>
              <span>{{ item?.name }}</span>
            </div>
            <div class="fee__row">
              <span>计费方式</span>
              <span>按次计费</span>
            </div>
            <div class="fee__row">
              <span>我的余额</span>
              <span :class="{ 'fee__danger': !enough }">¥{{ fmtMoney(balance) }}</span>
            </div>
            <el-divider />
            <div class="fee__row fee__total">
              <span>应付金额</span>
              <span class="price-text-lg">¥{{ fmtMoney(amount) }}</span>
            </div>

            <el-alert
              v-if="!enough"
              type="warning"
              :closable="false"
              show-icon
              title="余额不足，无法下单"
              description="请先充值后再提交预约。"
              class="fee__alert"
            />

            <el-button
              class="fee__submit"
              type="primary"
              size="large"
              :loading="submitting"
              :disabled="!item || !staff"
              @click="onSubmit"
            >
              提交预约并支付
            </el-button>
            <p class="fee__note aux-text">
              提交即表示同意从余额扣款；取消/拒单将原路退回并生成退款流水。
            </p>
          </div>
        </PageCard>
      </el-col>
    </el-row>

    <!-- 余额不足 -->
    <el-dialog v-model="insufficient" title="余额不足" width="400px" align-center>
      <p class="fee__dialog-text">
        当前余额 <strong>¥{{ fmtMoney(balance) }}</strong>，本单需支付
        <strong class="price-text">¥{{ fmtMoney(amount) }}</strong>。
      </p>
      <template #footer>
        <el-button @click="insufficient = false">稍后处理</el-button>
        <el-button type="primary" @click="doRecharge">立即充值</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.order-create__topbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.order-create__title {
  font-size: var(--font-size-module);
  font-weight: 600;
  color: var(--color-text-primary);
}

.order-create__readonly {
  display: flex;
  align-items: center;
  gap: 8px;
}

.order-create__hint {
  width: 100%;
  line-height: 1.4;
}

.fee__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  font-size: var(--font-size-body);
  color: var(--color-text-secondary);
}

.fee__row strong {
  color: var(--color-text-primary);
}

.fee__total {
  font-size: var(--font-size-module);
  font-weight: 600;
  color: var(--color-text-primary);
}

.fee__danger {
  color: var(--color-danger) !important;
  font-weight: 600;
}

.fee__alert {
  margin: 12px 0;
  border-radius: var(--radius-btn);
}

.fee__submit {
  width: 100%;
  margin-top: 8px;
  font-size: var(--font-size-body);
  font-weight: 500;
}

.fee__note {
  margin: 12px 0 0;
  line-height: 1.6;
  text-align: center;
}

.fee__dialog-text {
  margin: 0;
  font-size: var(--font-size-body);
  line-height: 1.8;
}
</style>
