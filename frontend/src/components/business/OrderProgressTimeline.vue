<script setup lang="ts">
import { computed } from 'vue'
import { fmtTime } from '@/utils/constants'
import type { OrderProgress } from '@/types'

/** 服务进度时间轴（PRD 5.5/6.3：标题 + 图片 + 服务时间，未读红点） */
const props = withDefaults(defineProps<{ list?: OrderProgress[]; unread?: boolean; showRead?: boolean }>(), {
  list: () => [],
  unread: false,
  showRead: true,
})

const images = (val?: string[] | string): string[] => {
  if (!val) return []
  if (Array.isArray(val)) return val
  try {
    const parsed = JSON.parse(val)
    return Array.isArray(parsed) ? parsed : [val]
  } catch {
    return val ? [val] : []
  }
}

const isEmpty = computed(() => !props.list.length)
</script>

<template>
  <div class="progress-timeline">
    <div v-if="unread && showRead" class="progress-timeline__unread">
      <span class="unread-dot">新</span>
      <span class="aux-text">有新的服务进度，点击查看后自动标记已读</span>
    </div>

    <el-empty v-if="isEmpty" description="暂无服务进度" :image-size="80" />

    <el-timeline v-else>
      <el-timeline-item
        v-for="p in list"
        :key="p.id"
        :timestamp="fmtTime(p.createdAt)"
        type="primary"
        placement="top"
      >
        <div class="progress-item">
          <div class="progress-item__head">
            <span class="progress-item__title">{{ p.title }}</span>
            <span v-if="p.serviceTime" class="biz-tag">{{ p.serviceTime }}</span>
          </div>
          <div v-if="images(p.images).length" class="progress-item__images">
            <el-image
              v-for="(img, i) in images(p.images)"
              :key="i"
              :src="img"
              :preview-src-list="images(p.images)"
              :initial-index="i"
              fit="cover"
              class="progress-item__img"
              preview-teleported
            />
          </div>
        </div>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<style scoped>
.progress-timeline__unread {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.progress-item__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.progress-item__title {
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-primary);
}

.progress-item__images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.progress-item__img {
  width: 84px;
  height: 84px;
  border-radius: var(--radius-tag);
  overflow: hidden;
}
</style>
