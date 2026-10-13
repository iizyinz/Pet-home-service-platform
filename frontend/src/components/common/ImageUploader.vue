<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import type { UploadFile, UploadRequestOptions } from 'element-plus'
import { upload } from '@/utils/request'

/**
 * 图片上传（对接 POST /api/files/upload，5MB / jpg·png·gif·webp）
 * v-model 为已上传的 URL 数组，可用于评价图、进度图、资质证书、宠物头像。
 */
const props = withDefaults(
  defineProps<{
    modelValue?: string[]
    limit?: number
    tip?: string
    disabled?: boolean
  }>(),
  { modelValue: () => [], limit: 3, tip: '支持 jpg / png / gif / webp，单张不超过 5MB', disabled: false },
)

const emit = defineEmits<{ 'update:modelValue': [value: string[]] }>()

const uploading = ref(false)
const ACCEPT = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
const MAX = 5 * 1024 * 1024

function beforeUpload(file: File): boolean {
  if (!ACCEPT.includes(file.type)) {
    ElMessage.error('仅支持 jpg / png / gif / webp 格式图片')
    return false
  }
  if (file.size > MAX) {
    ElMessage.error('图片大小不能超过 5MB')
    return false
  }
  if (props.modelValue.length >= props.limit) {
    ElMessage.warning(`最多上传 ${props.limit} 张图片`)
    return false
  }
  return true
}

async function doUpload(options: UploadRequestOptions) {
  uploading.value = true
  try {
    const res = await upload(options.file)
    emit('update:modelValue', [...props.modelValue, res.url])
  } catch {
    // 拦截器已统一 toast
  } finally {
    uploading.value = false
  }
}

function handleRemove(file: UploadFile) {
  const target = (file.url || '').replace(/^https?:\/\/[^/]+/, '')
  emit(
    'update:modelValue',
    props.modelValue.filter((u) => u !== target && u !== file.url),
  )
}
</script>

<template>
  <div class="image-uploader">
    <el-upload
      list-type="picture-card"
      :file-list="modelValue.map((u) => ({ name: u, url: u }))"
      :limit="limit"
      :disabled="disabled || uploading"
      :http-request="doUpload"
      :before-upload="beforeUpload"
      :on-remove="handleRemove"
      accept="image/jpeg,image/png,image/gif,image/webp"
    >
      <el-icon v-if="!disabled && modelValue.length < limit"><Plus /></el-icon>
    </el-upload>
    <p class="image-uploader__tip aux-text">{{ tip }}</p>
  </div>
</template>

<style scoped>
.image-uploader__tip {
  margin: 8px 0 0;
}
</style>
