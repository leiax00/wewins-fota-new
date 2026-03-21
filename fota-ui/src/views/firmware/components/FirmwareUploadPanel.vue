<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { UploadProps } from 'element-plus'
import type { UploadState } from '../types'
import { formatFileSize } from '../utils/formatters'

const { t } = useI18n()

const props = defineProps<{
  uploadState: UploadState
  productId?: number
  disabled?: boolean
  submitting?: boolean
  httpRequest?: UploadProps['httpRequest']
  beforeUpload?: UploadProps['beforeUpload']
}>()

const emit = defineEmits<{
  (e: 'upload', file: File): void
  (e: 'cancel'): void
  (e: 'retry'): void
}>()

const isUploading = computed(() => props.uploadState.status === 'UPLOADING')
const isUploadSuccess = computed(() => props.uploadState.status === 'SUCCESS')
const isUploadFailed = computed(() => props.uploadState.status === 'FAILED')
const isIdle = computed(() => props.uploadState.status === 'IDLE')

const isDisabled = computed(() => {
  return props.disabled || isUploading.value || !props.productId
})

const buttonText = computed(() => {
  if (isUploading.value) {
    return t('firmware.uploading')
  }
  if (isUploadSuccess.value) {
    return t('firmware.uploadSuccess')
  }
  return t('firmware.selectAndUpload')
})

const handleCancel = () => {
  emit('cancel')
}

const handleRetry = () => {
  emit('retry')
}
</script>

<template>
  <div class="firmware-upload-panel">
    <!-- 上传按钮 -->
    <el-upload
      ref="uploadRef"
      class="w-full"
      :show-file-list="false"
      :limit="1"
      :auto-upload="true"
      :http-request="httpRequest"
      :before-upload="beforeUpload"
      :disabled="isDisabled"
      accept=".bin,.zip,.tar,.tar.gz,.rar"
    >
      <el-button
        class="firmware-upload-panel__button"
        type="primary"
        plain
        :loading="isUploading"
        :disabled="isDisabled"
      >
        <template v-if="isIdle">
          {{ t('firmware.selectAndUpload') }}
        </template>
        <template v-else>
          {{ buttonText }}
        </template>
      </el-button>
    </el-upload>

    <!-- 文件信息显示 -->
    <div
      v-if="uploadState.fileName"
      class="firmware-upload-panel__meta"
    >
      <div class="grid grid-cols-2 gap-2">
        <div>
          <span class="firmware-upload-panel__meta-label">{{ t('firmware.fileName') }}</span>
          <span class="firmware-upload-panel__meta-value">{{ uploadState.fileName }}</span>
        </div>
        <div>
          <span class="firmware-upload-panel__meta-label">{{ t('firmware.fileSizeLabel') }}</span>
          <span class="firmware-upload-panel__meta-value">{{ formatFileSize(uploadState.fileSize) }}</span>
        </div>
      </div>
    </div>

    <!-- 进度条 -->
    <el-progress
      v-if="isUploading || isUploadSuccess"
      class="mt-3"
      :percentage="uploadState.percent"
      :status="isUploadSuccess ? 'success' : undefined"
    >
      <template #default="{ percentage }">
        <span class="text-sm">
          {{ isUploadSuccess ? `✓ ${t('firmware.uploadSuccess')}` : `${percentage}%` }}
        </span>
      </template>
    </el-progress>

    <!-- 失败提示 -->
    <el-alert
      v-if="isUploadFailed"
      class="mt-3"
      type="error"
      :closable="false"
      :title="uploadState.error || t('firmware.uploadFailed')"
    />

    <!-- 操作按钮 -->
    <div class="mt-3 flex items-center gap-2">
      <el-button
        v-if="isUploading"
        size="small"
        @click="handleCancel"
      >
        {{ t('firmware.cancelUpload') }}
      </el-button>
      <el-button
        v-if="isUploadFailed"
        size="small"
        type="warning"
        @click="handleRetry"
      >
        {{ t('firmware.retryUpload') }}
      </el-button>
      <el-button
        v-if="isUploadSuccess && !submitting"
        size="small"
        @click="handleCancel"
      >
        {{ t('firmware.replaceFile') }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.firmware-upload-panel {
  width: 100%;
  padding: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-bg-color-overlay);
}

.firmware-upload-panel__meta {
  margin-top: 12px;
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.firmware-upload-panel__meta-label {
  color: var(--el-text-color-secondary);
}

.firmware-upload-panel__meta-value {
  margin-left: 4px;
  color: var(--el-text-color-primary);
  font-weight: 500;
  word-break: break-all;
}

.firmware-upload-panel__button {
  --el-button-bg-color: color-mix(in srgb, var(--el-color-primary) 14%, var(--el-bg-color-overlay));
  --el-button-border-color: color-mix(in srgb, var(--el-color-primary) 38%, var(--el-border-color));
  --el-button-text-color: var(--el-color-primary);
  --el-button-hover-bg-color: color-mix(in srgb, var(--el-color-primary) 20%, var(--el-bg-color-overlay));
  --el-button-hover-border-color: var(--el-color-primary);
  --el-button-hover-text-color: var(--el-color-primary);
  --el-button-active-bg-color: color-mix(in srgb, var(--el-color-primary) 24%, var(--el-bg-color-overlay));
  --el-button-active-border-color: var(--el-color-primary);
  --el-button-active-text-color: var(--el-color-primary);
  --el-button-disabled-bg-color: var(--el-fill-color-light);
  --el-button-disabled-border-color: var(--el-border-color-lighter);
  --el-button-disabled-text-color: var(--el-text-color-placeholder);
}
</style>
