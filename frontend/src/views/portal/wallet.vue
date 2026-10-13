<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Wallet } from '@element-plus/icons-vue'
import PageCard from '@/components/common/PageCard.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import { usePage } from '@/composables/usePage'
import { fetchBalance, fetchBalanceRecords, recharge } from '@/api/user'
import { fmtMoney, fmtTime } from '@/utils/constants'
import type { BalanceRecord } from '@/types'

/**
 * 我的钱包（PRD 5.9）：当前余额 + 模拟充值（0.01~10000 元）+ 余额变动流水分页
 * 流水字段对齐 balance_record：type / amount（带正负）/ balanceAfter / orderId / remark
 */
const router = useRouter()

const balance = ref(0)
const balanceLoading = ref(true)

const { list, total, page, size, loading, error, search, refresh, changePage, changeSize } =
  usePage<BalanceRecord>((q) => fetchBalanceRecords(q.page ?? 1, q.size ?? 10), { size: 10 })

async function loadBalance() {
  balanceLoading.value = true
  try {
    const res = await fetchBalance()
    balance.value = Number(res?.balance ?? 0)
  } catch {
    /* 拦截器已提示 */
  } finally {
    balanceLoading.value = false
  }
}

/* ---------- 充值（模拟支付） ---------- */
const rechargeVisible = ref(false)
const charging = ref(false)
const amount = ref(100)

const PRESETS = [50, 100, 200, 500, 1000]

function openRecharge() {
  amount.value = 100
  rechargeVisible.value = true
}

async function doRecharge() {
  const v = Number(amount.value)
  if (!v || v <= 0) {
    ElMessage.warning('请选择或输入充值金额')
    return
  }
  if (v > 10000) {
    ElMessage.warning('单次充值上限 10000 元')
    return
  }
  charging.value = true
  try {
    const res = await recharge(v)
    balance.value = Number(res?.balance ?? 0)
    ElMessage.success(`充值成功，当前余额 ¥${fmtMoney(res?.balance ?? 0)}`)
    rechargeVisible.value = false
    await search()
  } catch {
    /* 拦截器已提示 */
  } finally {
    charging.value = false
  }
}

/* ---------- 流水展示 ---------- */
/** 变动金额带正负号（后端按正负存储：消费为负，充值/退款为正，调账可正可负） */
function amountText(v: unknown): string {
  const n = Number(v ?? 0)
  const sign = n > 0 ? '+' : n < 0 ? '-' : ''
  return `${sign}¥${Math.abs(n).toFixed(2)}`
}

function amountClass(v: unknown): string {
  const n = Number(v ?? 0)
  return n > 0 ? 'is-in' : n < 0 ? 'is-out' : ''
}

function remarkText(r: BalanceRecord): string {
  return r.remark || ''
}

function goOrder(r: BalanceRecord) {
  if (r.orderId) router.push(`/orders/${r.orderId}`)
}

onMounted(() => {
  void loadBalance()
})
</script>

<template>
  <div class="wallet">
    <div class="wallet__topbar">
      <span class="wallet__title">我的钱包</span>
      <el-button plain size="small" @click="router.push('/orders')">我的订单</el-button>
    </div>

    <el-row :gutter="16">
      <!-- 余额卡片 -->
      <el-col :xs="24" :lg="9">
        <div class="wallet__balance" v-loading="balanceLoading">
          <div class="wallet__balance-head">
            <el-icon :size="20"><Wallet /></el-icon>
            <span>当前余额（元）</span>
          </div>
          <div class="wallet__balance-value">¥{{ fmtMoney(balance) }}</div>
          <p class="wallet__balance-desc">余额用于预约下单直接支付；取消 / 拒单将原路退回并生成退款流水。</p>
          <el-button type="primary" round class="wallet__balance-btn" @click="openRecharge">充值</el-button>
        </div>

        <PageCard title="钱包说明" class="wallet__card">
          <ul class="wallet__tips">
            <li>充值为平台内部余额，不对接真实支付网关（模拟支付）。</li>
            <li>单次充值范围 0.01 ~ 10000 元，充值即写 RECHARGE 流水。</li>
            <li>下单扣款写 CONSUME 流水，取消 / 拒单退款写 REFUND 流水。</li>
          </ul>
        </PageCard>
      </el-col>

      <!-- 流水 -->
      <el-col :xs="24" :lg="15">
        <PageCard title="余额流水" :loading="loading">
          <ErrorState v-if="error" :text="error" @retry="refresh" />

          <EmptyState v-else-if="!list.length" text="暂无余额流水" />

          <template v-else>
            <ul class="wallet__records">
              <li v-for="r in list" :key="r.id" class="wallet__record">
                <div class="wallet__record-main">
                  <div class="wallet__record-top">
                    <StatusTag kind="record" :value="r.type" />
                    <span class="wallet__record-remark">{{ remarkText(r) }}</span>
                    <el-button
                      v-if="r.orderId"
                      link
                      type="primary"
                      size="small"
                      @click="goOrder(r)"
                    >
                      查看订单
                    </el-button>
                  </div>
                  <div class="aux-text">{{ fmtTime(r.createdAt) }}</div>
                </div>
                <div class="wallet__record-side">
                  <span class="wallet__record-amount" :class="amountClass(r.amount)">
                    {{ amountText(r.amount) }}
                  </span>
                  <span class="aux-text">余额 ¥{{ fmtMoney(r.balanceAfter) }}</span>
                </div>
              </li>
            </ul>

            <div v-if="total > size" class="wallet__pager">
              <el-pagination
                :current-page="page"
                :page-size="size"
                :total="total"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                background
                @current-change="changePage"
                @size-change="changeSize"
              />
            </div>
          </template>
        </PageCard>
      </el-col>
    </el-row>

    <!-- 充值 -->
    <el-dialog v-model="rechargeVisible" title="账户充值" width="420px">
      <p class="recharge__label">选择金额</p>
      <div class="recharge__presets">
        <button
          v-for="p in PRESETS"
          :key="p"
          type="button"
          class="recharge__chip"
          :class="{ 'is-active': amount === p }"
          @click="amount = p"
        >
          ¥{{ p }}
        </button>
      </div>

      <p class="recharge__label">自定义金额（元）</p>
      <el-input-number
        v-model="amount"
        :min="0.01"
        :max="10000"
        :precision="2"
        :step="10"
        placeholder="0.01 ~ 10000"
        style="width: 100%"
      />
      <p class="recharge__tip aux-text">模拟支付：确认后立即到账并生成充值流水。</p>

      <template #footer>
        <el-button @click="rechargeVisible = false">取消</el-button>
        <el-button type="primary" :loading="charging" @click="doRecharge">
          确认充值 ¥{{ fmtMoney(amount) }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.wallet__topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.wallet__title {
  flex: 1;
  font-size: var(--font-size-module);
  font-weight: 600;
  color: var(--color-text-primary);
}

.wallet__balance {
  background: linear-gradient(135deg, var(--el-color-primary), var(--color-primary-dark-2));
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 24px;
  color: #fff;
  margin-bottom: 16px;
}

.wallet__balance-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--font-size-aux);
  opacity: 0.9;
}

.wallet__balance-value {
  margin: 12px 0 8px;
  font-size: 36px;
  font-weight: 600;
  line-height: 1.2;
}

.wallet__balance-desc {
  margin: 0 0 16px;
  font-size: var(--font-size-aux);
  line-height: 1.6;
  opacity: 0.9;
}

.wallet__balance-btn {
  background: #fff;
  border-color: #fff;
  color: var(--color-primary-dark-2);
}

.wallet__tips {
  margin: 0;
  padding-left: 18px;
  font-size: var(--font-size-aux);
  color: var(--color-text-secondary);
  line-height: 1.9;
}

.wallet__records {
  list-style: none;
  margin: 0;
  padding: 0;
}

.wallet__record {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--color-border);
}

.wallet__record:last-child {
  border-bottom: none;
}

.wallet__record-main {
  min-width: 0;
}

.wallet__record-top {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 4px;
}

.wallet__record-remark {
  font-size: var(--font-size-body);
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 240px;
}

.wallet__record-side {
  text-align: right;
  flex: 0 0 auto;
}

.wallet__record-amount {
  display: block;
  font-size: var(--font-size-price);
  font-weight: 600;
  color: var(--color-text-primary);
}

.wallet__record-amount.is-in {
  color: var(--color-success);
}

.wallet__record-amount.is-out {
  color: var(--color-danger);
}

.wallet__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.recharge__label {
  margin: 0 0 8px;
  font-size: var(--font-size-body);
  color: var(--color-text-secondary);
}

.recharge__presets {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 8px;
  margin-bottom: 16px;
}

.recharge__chip {
  padding: 10px 0;
  border-radius: var(--radius-btn);
  border: 1px solid var(--color-border);
  background: var(--color-bg-page);
  color: var(--color-text-primary);
  font-size: var(--font-size-body);
  cursor: pointer;
  transition: all 0.15s ease;
}

.recharge__chip:hover {
  border-color: var(--el-color-primary-light-5);
}

.recharge__chip.is-active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}

.recharge__tip {
  margin: 8px 0 0;
  line-height: 1.6;
}

@media (max-width: 640px) {
  .recharge__presets {
    grid-template-columns: repeat(3, 1fr);
  }

  .wallet__pager {
    justify-content: center;
  }
}
</style>
