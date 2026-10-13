<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, Calendar } from '@element-plus/icons-vue'
import PageCard from '@/components/common/PageCard.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import { fetchSchedule, type ScheduleMap } from '@/api/staff/workbench'
import { fmtMoney, fmtTime } from '@/utils/constants'
import type { OrderListVO } from '@/types'

/**
 * 服务排期（PRD 6.4）：月历 + 点击某天查看当日上门安排。
 * 数据源：GET /api/staff/workbench/schedule?month=yyyy-MM（后端按预约时间按天分组）
 */
const router = useRouter()

const month = ref('') // yyyy-MM
const selected = ref('') // yyyy-MM-dd
const group = ref<ScheduleMap>({})
const loading = ref(true)
const error = ref('')

const WEEK_LABELS = ['一', '二', '三', '四', '五', '六', '日']

interface DayCell {
  day: number
  key: string
  count: number
  isToday: boolean
}

function pad(n: number): string {
  return String(n).padStart(2, '0')
}

function dayKeyOf(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function monthOf(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}`
}

function todayKey(): string {
  return dayKeyOf(new Date())
}

/** 月历格子：前置补空（周一为首），不足整周后置补空 */
const cells = computed<(DayCell | null)[]>(() => {
  const y = Number(month.value.slice(0, 4))
  const m = Number(month.value.slice(5, 7))
  if (!y || !m) return []
  const firstWeekday = new Date(y, m - 1, 1).getDay()
  const offset = (firstWeekday + 6) % 7
  const days = new Date(y, m, 0).getDate()
  const today = todayKey()
  const list: (DayCell | null)[] = []
  for (let i = 0; i < offset; i++) list.push(null)
  for (let d = 1; d <= days; d++) {
    const key = `${y}-${pad(m)}-${pad(d)}`
    list.push({ day: d, key, count: (group.value[key] ?? []).length, isToday: key === today })
  }
  while (list.length % 7 !== 0) list.push(null)
  return list
})

const dayOrders = computed<OrderListVO[]>(() => group.value[selected.value] ?? [])

/** 当月统计 */
const monthStat = computed(() => {
  let orders = 0
  let busyDays = 0
  for (const key of Object.keys(group.value)) {
    const n = group.value[key]?.length ?? 0
    orders += n
    if (n > 0) busyDays += 1
  }
  return { orders, busyDays }
})

const selectedLabel = computed(() => {
  if (!selected.value) return ''
  const [y, m, d] = selected.value.split('-').map(Number)
  if (!y || !m || !d) return selected.value
  const weekLabel = ['日', '一', '二', '三', '四', '五', '六'][new Date(y, m - 1, d).getDay()] ?? ''
  return `${y} 年 ${m} 月 ${d} 日 星期${weekLabel}`
})

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    group.value = (await fetchSchedule(month.value)) ?? {}
    // 默认选中今天（不在所选月份时退化为当月第一个有排期的日期，再退化为 1 号）
    const usable = selected.value && selected.value.startsWith(month.value)
    if (!usable) {
      const today = todayKey()
      const firstBusy = Object.keys(group.value).sort()[0]
      selected.value = today.startsWith(month.value) ? today : (firstBusy ?? `${month.value}-01`)
    }
  } catch (e) {
    error.value = (e as Error)?.message || '排期加载失败'
    group.value = {}
  } finally {
    loading.value = false
  }
}

function selectCell(cell: DayCell | null): void {
  if (cell) selected.value = cell.key
}

function shiftMonth(delta: number): void {
  const y = Number(month.value.slice(0, 4))
  const m = Number(month.value.slice(5, 7))
  if (!y || !m) return
  month.value = monthOf(new Date(y, m - 1 + delta, 1))
  selected.value = ''
  void load()
}

function onMonthChange(v: unknown): void {
  month.value = typeof v === 'string' && v ? v : monthOf(new Date())
  selected.value = ''
  void load()
}

function goDetail(o: OrderListVO): void {
  router.push(`/staff/orders/${o.id}`)
}

onMounted(() => {
  month.value = monthOf(new Date())
  selected.value = todayKey()
  void load()
})
</script>

<template>
  <div class="schedule">
    <ErrorState v-if="error && !loading" :text="error" @retry="load" />

    <template v-else>
      <PageCard title="服务排期">
        <template #header>
          <div class="schedule__toolbar">
            <el-button size="small" :icon="ArrowLeft" :disabled="loading" @click="shiftMonth(-1)">上月</el-button>
            <el-date-picker
              v-model="month"
              type="month"
              placeholder="选择月份"
              value-format="YYYY-MM"
              :clearable="false"
              :disabled="loading"
              style="width: 140px"
              @change="onMonthChange"
            />
            <el-button size="small" :disabled="loading" @click="shiftMonth(1)">
              下月<el-icon><ArrowRight /></el-icon>
            </el-button>
            <span class="aux-text">
              本月 {{ monthStat.orders }} 单 · {{ monthStat.busyDays }} 天有安排
            </span>
          </div>
        </template>

        <div v-loading="loading" class="schedule__body">
          <el-row :gutter="16">
            <!-- 月历 -->
            <el-col :xs="24" :lg="15">
              <div class="cal">
                <div v-for="w in WEEK_LABELS" :key="w" class="cal__week">{{ w }}</div>
                <div
                  v-for="(cell, i) in cells"
                  :key="i"
                  class="cal__cell"
                  :class="{
                    'is-empty': !cell,
                    'is-today': cell?.isToday,
                    'is-selected': cell && cell.key === selected,
                    'has-order': !!cell && cell.count > 0,
                  }"
                  role="button"
                  tabindex="0"
                  @click="selectCell(cell)"
                  @keydown.enter="selectCell(cell)"
                >
                  <template v-if="cell">
                    <span class="cal__day">{{ cell.day }}</span>
                    <span v-if="cell.count" class="cal__count">{{ cell.count }}</span>
                  </template>
                </div>
              </div>

              <p v-if="!loading && !monthStat.orders" class="aux-text schedule__hint">
                本月暂无上门安排，客户下单并被接单后会自动出现在这里。
              </p>
            </el-col>

            <!-- 当日安排 -->
            <el-col :xs="24" :lg="9">
              <div class="schedule__day">
                <div class="schedule__day-head">
                  <span class="schedule__day-title">
                    <el-icon><Calendar /></el-icon>
                    {{ selectedLabel || '请选择日期' }}
                  </span>
                  <span class="aux-text">{{ dayOrders.length }} 单</span>
                </div>

                <div v-if="dayOrders.length" class="schedule__list">
                  <div
                    v-for="o in dayOrders"
                    :key="o.id"
                    class="sched-item"
                    role="button"
                    tabindex="0"
                    @click="goDetail(o)"
                    @keydown.enter="goDetail(o)"
                  >
                    <div class="sched-item__time">{{ fmtTime(o.appointmentTime).slice(11) }}</div>
                    <div class="sched-item__main">
                      <div class="sched-item__title">
                        <span class="sched-item__item">{{ o.itemName || '上门服务' }}</span>
                        <StatusTag kind="order" :value="o.status" />
                      </div>
                      <div class="aux-text sched-item__meta">
                        <span>{{ o.petName || '-' }}{{ o.petBreed ? `（${o.petBreed}）` : '' }}</span>
                        <span>客户 {{ o.ownerName || '-' }}</span>
                        <span class="price-text">¥{{ fmtMoney(o.amount) }}</span>
                      </div>
                      <div class="aux-text sched-item__no">订单号 {{ o.orderNo }}</div>
                    </div>
                  </div>
                </div>

                <EmptyState v-else-if="!loading" text="该日没有上门安排">
                  <template #tip>点击左侧日历上的日期查看当天排期</template>
                </EmptyState>
              </div>
            </el-col>
          </el-row>
        </div>
      </PageCard>
    </template>
  </div>
</template>

<style scoped>
.schedule__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.schedule__body {
  min-height: 260px;
}

.schedule__hint {
  margin: 12px 0 0;
}

.cal {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 6px;
}

.cal__week {
  text-align: center;
  font-size: var(--font-size-aux);
  color: var(--color-text-muted);
  padding-bottom: 4px;
}

.cal__cell {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 56px;
  border-radius: var(--radius-btn, 12px);
  background: var(--color-bg-page);
  cursor: pointer;
  transition:
    background 0.15s ease,
    color 0.15s ease;
}

.cal__cell.is-empty {
  background: transparent;
  cursor: default;
}

.cal__cell:not(.is-empty):hover {
  background: var(--el-color-primary-light-9);
}

.cal__cell.is-today {
  border: 1px solid var(--el-color-primary);
}

.cal__cell.is-selected {
  background: var(--el-color-primary);
}

.cal__day {
  font-size: var(--font-size-body);
  color: var(--color-text-primary);
}

.cal__cell.is-selected .cal__day {
  color: #fff;
  font-weight: 600;
}

.cal__count {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
  font-size: 12px;
  line-height: 18px;
  text-align: center;
}

.cal__cell.is-selected .cal__count {
  background: rgba(255, 255, 255, 0.25);
  color: #fff;
}

.schedule__day {
  height: 100%;
  border-radius: var(--radius-card);
  background: var(--color-bg-page);
  padding: 12px;
}

.schedule__day-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}

.schedule__day-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
}

.schedule__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sched-item {
  display: flex;
  gap: 10px;
  padding: 12px;
  border-radius: var(--radius-btn, 12px);
  background: var(--color-bg-card);
  box-shadow: var(--shadow-card);
  cursor: pointer;
}

.sched-item:hover {
  outline: 1px solid var(--el-color-primary-light-7);
}

.sched-item__time {
  flex-shrink: 0;
  width: 46px;
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-primary);
}

.sched-item__main {
  flex: 1 1 auto;
  min-width: 0;
}

.sched-item__title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.sched-item__item {
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
}

.sched-item__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin-top: 6px;
}

.sched-item__no {
  margin-top: 4px;
}

@media (max-width: 768px) {
  .cal__cell {
    min-height: 44px;
  }

  .schedule__day {
    margin-top: 12px;
  }
}
</style>
