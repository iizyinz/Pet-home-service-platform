<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, StarFilled } from '@element-plus/icons-vue'
import PageCard from '@/components/common/PageCard.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import RatingStars from '@/components/common/RatingStars.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import ConfirmAction from '@/components/common/ConfirmAction.vue'
import { fetchFavorites, toggleFavorite } from '@/api/staffs'
import type { StaffListVO } from '@/types'

/**
 * 我的收藏（PRD 5.9）：
 * 收藏的服务师列表 + 取消收藏（二次确认）+ 空态引导。
 * 列表接口不分页（GET /staffs/favorites 返回全量），提供加载/错误/空三态与手动刷新。
 */
const router = useRouter()

const list = ref<StaffListVO[]>([])
const loading = ref(true)
const error = ref('')
const busy = ref(false)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    list.value = (await fetchFavorites()) ?? []
  } catch (e) {
    list.value = []
    error.value = (e as Error)?.message || '收藏列表加载失败'
  } finally {
    loading.value = false
  }
}

const total = computed(() => list.value.length)

async function onRemove(staff: StaffListVO): Promise<void> {
  if (busy.value) return
  busy.value = true
  try {
    const res = await toggleFavorite(staff.id)
    if (res?.favorited === false) {
      list.value = list.value.filter((s) => s.id !== staff.id)
      ElMessage.success(`已取消收藏「${staff.nickname || '服务师'}」`)
    } else {
      // 后端状态与预期不一致时以服务端为准，刷新兜底
      ElMessage.warning('收藏状态已更新，列表已刷新')
      await load()
    }
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    busy.value = false
  }
}

function goDetail(id: number): void {
  router.push(`/providers/${id}`)
}

function goProviders(): void {
  router.push('/providers')
}

/** 卡片上最多展示 4 个擅长品种 */
function topBreeds(breeds?: string[], max = 4): string[] {
  return (breeds ?? []).slice(0, max)
}

onMounted(() => void load())
</script>

<template>
  <div class="fav">
    <PageCard title="我的收藏" class="fav__card">
      <template #header>
        <div class="fav__head">
          <span class="aux-text">共 {{ total }} 位服务师</span>
          <el-button size="small" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
          <el-button size="small" type="primary" plain @click="goProviders">发现更多服务师</el-button>
        </div>
      </template>

      <div class="fav__body" v-loading="loading" element-loading-text="收藏加载中">
        <ErrorState v-if="error && !loading" :text="error" @retry="load" />

        <EmptyState
          v-else-if="!list.length && !loading"
          text="还没有收藏服务师"
          action-text="去找服务师"
          @action="goProviders"
        >
          <template #tip>在服务人员列表点击「收藏」，即可把常约的服务师放到这里</template>
        </EmptyState>

        <div v-else class="fav__grid">
          <article v-for="s in list" :key="s.id" class="fav-item" @click="goDetail(s.id)">
            <div class="fav-item__head">
              <el-avatar :size="52" class="fav-item__avatar">
                <img v-if="s.avatar" :src="s.avatar" alt="" />
                <span v-else>{{ (s.nickname || '师')[0] }}</span>
              </el-avatar>

              <div class="fav-item__base">
                <div class="fav-item__name">
                  {{ s.nickname || '服务师' }}
                  <StatusTag v-if="s.certStatus !== undefined" kind="cert" :value="s.certStatus" />
                </div>
                <div class="fav-item__rating">
                  <RatingStars :model-value="Number(s.avgRating || 0)" size="small" readonly />
                  <span class="aux-text">{{ Number(s.avgRating || 0).toFixed(1) }} 分</span>
                </div>
                <div class="aux-text">
                  {{ s.experienceYears ?? 0 }} 年从业 · {{ s.completedCount ?? 0 }} 单 · {{ s.areaName || '全城可约' }}
                </div>
              </div>
            </div>

            <div v-if="s.goodAtBreeds?.length" class="fav-item__breeds">
              <span v-for="b in topBreeds(s.goodAtBreeds)" :key="b" class="fav-tag">{{ b }}</span>
            </div>

            <div class="fav-item__foot">
              <span class="fav-item__star">
                <el-icon :size="16"><StarFilled /></el-icon>
                已收藏
              </span>
              <ConfirmAction
                title="确认取消收藏？"
                message="取消后可随时在服务人员列表重新收藏。"
                confirm-text="取消收藏"
                @confirm="onRemove(s)"
              >
                <el-button size="small" plain :disabled="busy" @click.stop>取消收藏</el-button>
              </ConfirmAction>
              <el-button size="small" type="primary" plain @click.stop="goDetail(s.id)">查看详情</el-button>
            </div>
          </article>
        </div>
      </div>
    </PageCard>
  </div>
</template>

<style scoped>
.fav__card {
  min-height: 360px;
}

.fav__head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.fav__body {
  min-height: 240px;
}

.fav__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.fav-item {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  border-radius: var(--radius-card);
  background: var(--color-bg-page);
  cursor: pointer;
  transition: all 0.15s ease;
}

.fav-item:hover {
  box-shadow: var(--shadow-card);
  transform: translateY(-2px);
}

.fav-item__head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.fav-item__avatar {
  flex: 0 0 auto;
  background: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
  font-size: 18px;
}

.fav-item__base {
  flex: 1 1 auto;
  min-width: 0;
}

.fav-item__name {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
}

.fav-item__rating {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 5px 0 3px;
}

.fav-item__breeds {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.fav-tag {
  padding: 2px 8px;
  border-radius: var(--radius-tag);
  background: var(--color-bg-card);
  color: var(--color-text-secondary);
  font-size: var(--font-size-aux);
}

.fav-item__foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: auto;
}

.fav-item__foot .el-button {
  margin-left: 0;
}

.fav-item__star {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-right: auto;
  color: var(--color-warning);
  font-size: var(--font-size-aux);
  font-weight: 600;
}

@media (max-width: 768px) {
  .fav__grid {
    grid-template-columns: 1fr;
  }

  .fav__head .el-button {
    margin-left: 0;
  }
}
</style>
