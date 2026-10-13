<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import PageCard from '@/components/common/PageCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import { usePage } from '@/composables/usePage'
import { fetchServiceItems, fetchServiceTypes } from '@/api/catalog'
import { fmtMoney } from '@/utils/constants'
import type { ServiceItem, ServiceType } from '@/types'

/**
 * 服务列表（PRD 5.3）：
 * 分类筛选 + 关键词搜索 + 卡片流（封面/价格/时长/适用品种）+ 分页。
 * 首页分类入口带 typeId、顶栏搜索带 keyword，均与地址栏保持同步。
 */
const route = useRoute()
const router = useRouter()

interface ServiceQuery {
  typeId: number | ''
  keyword: string
}

function routeTypeId(): number | '' {
  const id = Number(route.query.typeId || 0)
  return id > 0 ? id : ''
}

function routeKeyword(): string {
  return String(route.query.keyword || '')
}

const { list, total, page, size, loading, error, query, search, reset, refresh, changePage, changeSize } = usePage<
  ServiceItem,
  ServiceQuery>(
  (q) =>
    fetchServiceItems({
      typeId: q.typeId === '' ? undefined : q.typeId,
      keyword: q.keyword || undefined,
      page: q.page ?? 1,
      size: q.size ?? 12,
    }),
  { defaultQuery: { typeId: '', keyword: '' }, size: 12 },
)

// 首帧即带上地址栏参数（onMounted 才发起请求）
query.typeId = routeTypeId()
query.keyword = routeKeyword()

/** 服务分类（chips） */
const types = ref<ServiceType[]>([])
const typesLoading = ref(true)
const typesError = ref('')

async function loadTypes(): Promise<void> {
  typesLoading.value = true
  typesError.value = ''
  try {
    types.value = (await fetchServiceTypes()) ?? []
  } catch (e) {
    typesError.value = (e as Error)?.message || '服务分类加载失败'
  } finally {
    typesLoading.value = false
  }
}

const typeNameMap = computed(() => {
  const map = new Map<number, string>()
  for (const t of types.value) map.set(t.id, t.name)
  return map
})

function typeName(typeId?: number): string {
  return (typeId && typeNameMap.value.get(typeId)) || '综合服务'
}

function pickType(id: number | ''): void {
  if (query.typeId === id) return
  query.typeId = id
  void search()
}

function onKeyword(): void {
  void search()
}

function onReset(): void {
  reset()
}

/** 后端 applicableBreeds 为 JSON 数组字符串 */
function breedText(v?: string): string {
  if (!v) return ''
  try {
    const arr: unknown = JSON.parse(v)
    if (Array.isArray(arr)) return (arr as string[]).join('、')
    return v
  } catch {
    return v
  }
}

function goDetail(id: number): void {
  router.push(`/services/${id}`)
}

// 顶栏搜索 / 首页分类跳转会复用同一路径，同步 query 后重新查询
watch(
  () => [route.query.typeId, route.query.keyword],
  () => {
    const nextType = routeTypeId()
    const nextKeyword = routeKeyword()
    if (query.typeId === nextType && query.keyword === nextKeyword) return
    query.typeId = nextType
    query.keyword = nextKeyword
    void search()
  },
)

onMounted(() => void loadTypes())
</script>

<template>
  <div class="svc">
    <PageCard title="服务列表" class="svc__card">
      <template #header>
        <div class="svc__head">
          <span class="aux-text">共 {{ total }} 个项目</span>
          <el-button size="small" :loading="loading" @click="refresh">刷新</el-button>
        </div>
      </template>

      <!-- 分类筛选 -->
      <div class="svc__types" v-loading="typesLoading" element-loading-text="分类加载中">
        <el-alert
          v-if="typesError"
          type="warning"
          :closable="false"
          show-icon
          :title="typesError"
          class="svc__types-error"
        >
          <template #default>
            <el-button link type="primary" size="small" @click="loadTypes">重试</el-button>
          </template>
        </el-alert>

        <template v-else>
          <button
            type="button"
            class="svc__type"
            :class="{ 'is-active': query.typeId === '' }"
            @click="pickType('')"
          >
            全部
          </button>
          <button
            v-for="t in types"
            :key="t.id"
            type="button"
            class="svc__type"
            :class="{ 'is-active': query.typeId === t.id }"
            @click="pickType(t.id)"
          >
            <span class="svc__type-icon">{{ t.icon || '🐾' }}</span>
            {{ t.name }}
          </button>
        </template>
      </div>

      <!-- 关键词搜索 -->
      <div class="svc__search">
        <el-input
          v-model="query.keyword"
          clearable
          placeholder="搜索服务名称，如：喂猫、遛狗、洗护"
          :prefix-icon="Search"
          class="svc__search-input"
          @keyup.enter="onKeyword"
          @clear="onKeyword"
        />
        <el-button type="primary" @click="onKeyword">搜索</el-button>
        <el-button @click="onReset">重置</el-button>
      </div>

      <!-- 列表主体：加载 / 错误 / 空态 -->
      <div class="svc__body" v-loading="loading">
        <ErrorState v-if="error && !loading" :text="error" @retry="refresh" />

        <EmptyState
          v-else-if="!list.length && !loading"
          text="没有匹配的服务项目"
          action-text="重置筛选"
          @action="onReset"
        >
          <template #tip>换个分类或关键词试试，也可以直接浏览全部服务</template>
        </EmptyState>

        <div v-else class="svc__grid">
          <article v-for="item in list" :key="item.id" class="svc-item" @click="goDetail(item.id)">
            <div class="svc-item__cover">
              <img v-if="item.coverUrl" :src="item.coverUrl" :alt="item.name" />
              <span v-else class="svc-item__cover-empty">🐾</span>
              <span class="biz-tag svc-item__type">{{ typeName(item.typeId) }}</span>
            </div>

            <div class="svc-item__body">
              <h4 class="svc-item__name">{{ item.name }}</h4>
              <p class="svc-item__meta aux-text">
                <span>{{ item.durationMinutes ? `${item.durationMinutes} 分钟` : '时长面议' }}</span>
                <span v-if="breedText(item.applicableBreeds)">适用 {{ breedText(item.applicableBreeds) }}</span>
              </p>
              <div class="svc-item__foot">
                <span class="price-text">
                  ¥{{ fmtMoney(item.price) }}
                  <small class="svc-item__unit">/次</small>
                </span>
                <el-button size="small" type="primary" plain @click.stop="goDetail(item.id)">查看详情</el-button>
              </div>
            </div>
          </article>
        </div>
      </div>

      <div v-if="total > 0" class="svc__pager">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :current-page="page"
          :page-size="size"
          :page-sizes="[12, 24, 48]"
          @current-change="changePage"
          @size-change="changeSize"
        />
      </div>
    </PageCard>
  </div>
</template>

<style scoped>
.svc__card {
  min-height: 420px;
}

.svc__head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.svc__types {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 40px;
  margin-bottom: 12px;
}

.svc__types-error {
  width: 100%;
  border-radius: var(--radius-btn);
}

.svc__type {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 7px 14px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-card);
  color: var(--color-text-secondary);
  font-size: var(--font-size-aux);
  cursor: pointer;
  transition: all 0.15s ease;
}

.svc__type:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.svc__type.is-active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}

.svc__type-icon {
  font-size: 14px;
}

.svc__search {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.svc__search-input {
  flex: 1 1 260px;
}

.svc__body {
  min-height: 260px;
}

.svc__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}

.svc-item {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: var(--radius-card);
  background: var(--color-bg-page);
  cursor: pointer;
  transition: all 0.15s ease;
}

.svc-item:hover {
  box-shadow: var(--shadow-card);
  transform: translateY(-2px);
}

.svc-item__cover {
  position: relative;
  height: 148px;
  background: var(--el-color-primary-light-9);
}

.svc-item__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.svc-item__cover-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 36px;
}

.svc-item__type {
  position: absolute;
  left: 8px;
  top: 8px;
}

.svc-item__body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px 14px;
}

.svc-item__name {
  margin: 0;
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.svc-item__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  margin: 0;
  line-height: 1.5;
  min-height: 20px;
}

.svc-item__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 4px;
}

.svc-item__foot .el-button {
  margin-left: 0;
}

.svc-item__unit {
  font-size: var(--font-size-aux);
  font-weight: 400;
  color: var(--color-text-muted);
}

.svc__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 768px) {
  .svc__pager {
    justify-content: center;
  }

  .svc__grid {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 12px;
  }

  .svc-item__cover {
    height: 112px;
  }
}
</style>
