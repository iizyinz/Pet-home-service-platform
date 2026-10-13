<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ChatDotRound, ArrowRight, Service } from '@element-plus/icons-vue'
import PageCard from '@/components/common/PageCard.vue'
import RatingStars from '@/components/common/RatingStars.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { fetchBanners, fetchAnnouncements } from '@/api/content'
import { fetchServiceTypes } from '@/api/catalog'
import { fetchStaffs } from '@/api/staffs'
import { fmtTime } from '@/utils/constants'
import type { Banner, Announcement, ServiceType, StaffListVO } from '@/types'

/**
 * 用户端首页（PRD 5.2）：
 * 轮播图 / 服务分类入口 / AI 宠物顾问入口 / 通知公告 / 推荐服务师
 */
const router = useRouter()

const banners = ref<Banner[]>([])
const types = ref<ServiceType[]>([])
const announcements = ref<Announcement[]>([])
const staffs = ref<StaffListVO[]>([])
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [b, t, a, s] = await Promise.all([
      fetchBanners(),
      fetchServiceTypes(),
      fetchAnnouncements(1, 5),
      fetchStaffs({ page: 1, size: 4 }),
    ])
    banners.value = b ?? []
    types.value = (t ?? []).slice(0, 10)
    announcements.value = a?.list ?? []
    staffs.value = s?.list ?? []
  } catch (e) {
    error.value = (e as Error)?.message || '首页数据加载失败'
  } finally {
    loading.value = false
  }
}

function goServices(typeId?: number) {
  router.push({ path: '/services', query: typeId ? { typeId } : {} })
}

onMounted(load)
</script>

<template>
  <div class="home" v-loading="loading">
    <EmptyState v-if="error && !loading" :text="error" action-text="重新加载" @action="load" />

    <template v-else>
      <!-- 轮播图 -->
      <el-carousel v-if="banners.length" class="home__banner" height="220px" indicator-position="outside">
        <el-carousel-item v-for="b in banners" :key="b.id" @click="b.link && router.push(b.link)">
          <img class="home__banner-img" :src="b.imageUrl" :alt="b.title || '轮播图'" />
          <div v-if="b.title" class="home__banner-title">{{ b.title }}</div>
        </el-carousel-item>
      </el-carousel>

      <el-row :gutter="16" class="home__row">
        <!-- 服务分类入口 -->
        <el-col :xs="24" :lg="16">
          <PageCard title="服务分类">
            <template #header>
              <el-button link type="primary" @click="goServices()">
                全部服务
                <el-icon><ArrowRight /></el-icon>
              </el-button>
            </template>

            <EmptyState v-if="!types.length" text="暂无服务分类" />
            <div v-else class="home__types">
              <div v-for="t in types" :key="t.id" class="home__type" @click="goServices(t.id)">
                <span class="home__type-icon">{{ t.icon || '🐾' }}</span>
                <span class="home__type-name">{{ t.name }}</span>
              </div>
            </div>
          </PageCard>

          <!-- 通知公告 -->
          <PageCard title="通知公告">
            <EmptyState v-if="!announcements.length" text="暂无公告" />
            <ul v-else class="home__notice">
              <li v-for="a in announcements" :key="a.id" class="home__notice-item">
                <span class="home__notice-dot" />
                <span class="home__notice-title" @click="router.push({ path: '/services' })">{{ a.title }}</span>
                <span class="aux-text">{{ fmtTime(a.createdAt) }}</span>
              </li>
            </ul>
          </PageCard>
        </el-col>

        <!-- AI 宠物顾问入口 -->
        <el-col :xs="24" :lg="8">
          <div class="home__ai" @click="router.push('/ai')">
            <div class="home__ai-icon"><el-icon :size="28"><ChatDotRound /></el-icon></div>
            <h3 class="home__ai-title">AI 宠物顾问</h3>
            <p class="home__ai-desc">护理 · 健康 · 饮食 · 训练，流式问答即时解答</p>
            <el-button type="primary" round>立即提问</el-button>
          </div>

          <PageCard title="快捷入口">
            <div class="home__quick">
              <el-button @click="router.push('/providers')"><el-icon><Service /></el-icon>&nbsp;找服务师</el-button>
              <el-button @click="router.push('/orders/create')">立即预约</el-button>
              <el-button @click="router.push('/pets')">宠物档案</el-button>
              <el-button @click="router.push('/wallet')">我的余额</el-button>
            </div>
          </PageCard>
        </el-col>
      </el-row>

      <!-- 推荐服务师 -->
      <PageCard title="推荐服务师">
        <template #header>
          <el-button link type="primary" @click="router.push('/providers')">
            查看更多
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </template>

        <EmptyState v-if="!staffs.length" text="暂无认证服务师" />
        <el-row v-else :gutter="16">
          <el-col v-for="s in staffs" :key="s.id" :xs="12" :sm="12" :md="6">
            <div class="home__staff" @click="router.push(`/providers/${s.id}`)">
              <el-avatar :size="56" class="home__staff-avatar">
                <img v-if="s.avatar" :src="s.avatar" alt="" />
                <span v-else>{{ (s.nickname || '师')[0] }}</span>
              </el-avatar>
              <div class="home__staff-name">
                {{ s.nickname || '服务师' }}
                <el-tag v-if="s.certStatus === 1" type="success" size="small" effect="light">已认证</el-tag>
              </div>
              <div class="home__staff-meta">
                <RatingStars :model-value="Number(s.avgRating || 0)" size="small" readonly />
                <span class="aux-text">{{ s.completedCount ?? 0 }} 单</span>
              </div>
              <div class="home__staff-meta aux-text">{{ s.experienceYears ?? 0 }} 年从业 · {{ s.areaName || '全城' }}</div>
            </div>
          </el-col>
        </el-row>
      </PageCard>
    </template>
  </div>
</template>

<style scoped>
.home__banner {
  border-radius: var(--radius-card);
  overflow: hidden;
  box-shadow: var(--shadow-card);
  margin-bottom: 16px;
}

.home__banner-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.home__banner-title {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16px 24px;
  color: #fff;
  font-size: var(--font-size-module);
  font-weight: 600;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.45), transparent);
}

.home__row {
  margin-bottom: 0;
}

.home__types {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(96px, 1fr));
  gap: 12px;
}

.home__type {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 14px 8px;
  border-radius: var(--radius-btn);
  background: var(--color-bg-page);
  cursor: pointer;
  transition: all 0.15s ease;
}

.home__type:hover {
  background: var(--el-color-primary-light-9);
}

.home__type-icon {
  font-size: 24px;
}

.home__type-name {
  font-size: var(--font-size-aux);
  color: var(--color-text-secondary);
  text-align: center;
}

.home__notice {
  list-style: none;
  margin: 0;
  padding: 0;
}

.home__notice-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 0;
  border-bottom: 1px solid var(--color-border);
}

.home__notice-item:last-child {
  border-bottom: none;
}

.home__notice-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-primary);
  flex: 0 0 auto;
}

.home__notice-title {
  flex: 1;
  font-size: var(--font-size-body);
  color: var(--color-text-primary);
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home__notice-title:hover {
  color: var(--el-color-primary);
}

.home__ai {
  background: linear-gradient(135deg, var(--el-color-primary), var(--color-primary-dark-2));
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 24px;
  color: #fff;
  text-align: center;
  cursor: pointer;
  margin-bottom: 16px;
}

.home__ai-icon {
  width: 56px;
  height: 56px;
  margin: 0 auto 8px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
}

.home__ai-title {
  margin: 8px 0 4px;
  font-size: var(--font-size-module);
}

.home__ai-desc {
  margin: 0 0 16px;
  font-size: var(--font-size-aux);
  opacity: 0.9;
}

.home__quick {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.home__quick .el-button {
  width: 100%;
  margin-left: 0;
}

.home__staff {
  background: var(--color-bg-page);
  border-radius: var(--radius-card);
  padding: 16px;
  text-align: center;
  cursor: pointer;
  transition: all 0.15s ease;
}

.home__staff:hover {
  box-shadow: var(--shadow-card);
  transform: translateY(-2px);
}

.home__staff-avatar {
  background: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
  font-size: 20px;
}

.home__staff-name {
  margin-top: 8px;
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.home__staff-meta {
  margin-top: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: var(--font-size-aux);
}
</style>
