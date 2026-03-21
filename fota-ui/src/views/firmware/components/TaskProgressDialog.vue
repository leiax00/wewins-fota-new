<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { CircleCheck, CircleClose, Clock, Loading } from '@element-plus/icons-vue'
import type { TaskProgressState } from '../types'

const { t } = useI18n()

const props = defineProps<{
  modelValue: boolean
  taskProgress: TaskProgressState
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'finish'): void
  (e: 'close'): void
}>()

const isProcessing = computed(() => props.taskProgress.stage === 'PROCESSING')
const isCompleted = computed(() => props.taskProgress.stage === 'COMPLETED')
const isFailed = computed(() => props.taskProgress.stage === 'FAILED' || props.taskProgress.stage === 'CANCELLED')
const isInit = computed(() => props.taskProgress.stage === 'INIT')

const handleFinish = () => {
  emit('finish')
}

const handleClose = () => {
  emit('close')
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="t('firmware.publishProgress')"
    width="480px"
    :close-on-click-modal="taskProgress.finished"
    :close-on-press-escape="taskProgress.finished"
    :show-close="taskProgress.finished"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="task-progress">
      <!-- 上传固件阶段 -->
      <div class="task-progress__item">
        <div class="task-progress__icon">
          <el-icon v-if="isProcessing || isCompleted">
            <CircleCheck />
          </el-icon>
          <el-icon v-else-if="isFailed">
            <CircleClose />
          </el-icon>
          <el-icon v-else>
            <Loading />
          </el-icon>
        </div>
        <div class="task-progress__content">
          <div class="task-progress__title">{{ t('firmware.taskUploadToStorage') }}</div>
          <div class="task-progress__status">
            <template v-if="isInit">
              <span class="task-progress__pending">{{ t('firmware.taskWaiting') }}</span>
            </template>
            <template v-else-if="isProcessing">
              <el-progress
                :percentage="taskProgress.percent"
                :status="taskProgress.percent === 100 ? 'success' : undefined"
                :show-text="false"
              />
              <span class="task-progress__message">{{ taskProgress.message || `${taskProgress.percent}%` }}</span>
            </template>
            <template v-else-if="isCompleted">
              <span class="task-progress__done">{{ t('firmware.taskDone') }}</span>
            </template>
            <template v-else-if="isFailed">
              <span class="task-progress__error">{{ t('firmware.taskFailed') }}</span>
            </template>
          </div>
        </div>
      </div>

      <!-- CDN 预热阶段 -->
      <div class="task-progress__item">
        <div class="task-progress__icon">
          <template v-if="isCompleted">
            <el-icon><CircleCheck /></el-icon>
          </template>
          <template v-else-if="isFailed">
            <el-icon><CircleClose /></el-icon>
          </template>
          <template v-else-if="isProcessing">
            <el-icon class="is-loading"><Loading /></el-icon>
          </template>
          <template v-else>
            <el-icon><Clock /></el-icon>
          </template>
        </div>
        <div class="task-progress__content">
          <div class="task-progress__title">{{ t('firmware.taskCdnWarm') }}</div>
          <div class="task-progress__status">
            <template v-if="isProcessing && taskProgress.percent > 0">
              <el-progress
                :percentage="taskProgress.percent"
                :status="taskProgress.percent === 100 ? 'success' : undefined"
                :show-text="false"
              />
              <span class="task-progress__message">{{ taskProgress.message || t('firmware.taskWarming') }}</span>
            </template>
            <template v-else-if="isCompleted">
              <span class="task-progress__done">{{ t('firmware.taskDone') }}</span>
            </template>
            <template v-else-if="isFailed">
              <span class="task-progress__error">{{ taskProgress.errorMsg || t('firmware.taskFailed') }}</span>
            </template>
            <template v-else>
              <span class="task-progress__pending">{{ t('firmware.taskWaiting') }}</span>
            </template>
          </div>
        </div>
      </div>

      <!-- 错误信息展示 -->
      <el-alert
        v-if="isFailed"
        class="mt-4"
        type="error"
        :closable="false"
        :title="taskProgress.errorMsg || t('firmware.taskPublishFailed')"
      />
    </div>

    <template #footer>
      <el-button
        v-if="taskProgress.finished"
        type="primary"
        @click="handleFinish"
      >
        {{ t('firmware.close') }}
      </el-button>
      <el-button
        v-else
        @click="handleClose"
      >
        {{ t('common.cancel') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.task-progress {
  padding: 8px 0;
}

.task-progress__item {
  display: flex;
  align-items: flex-start;
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.task-progress__item:last-child {
  border-bottom: none;
}

.task-progress__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  margin-right: 12px;
  font-size: 20px;
  border-radius: 50%;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
}

.task-progress__icon .el-icon {
  color: var(--el-color-success);
}

.task-progress__icon .is-loading {
  animation: rotate 1s linear infinite;
  color: var(--el-color-primary);
}

.task-progress__icon .el-icon:last-child {
  color: var(--el-color-danger);
}

.task-progress__content {
  flex: 1;
  min-width: 0;
}

.task-progress__title {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  margin-bottom: 8px;
}

.task-progress__status {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}

.task-progress__status .el-progress {
  flex: 1;
}

.task-progress__message {
  color: var(--el-text-color-secondary);
  min-width: 60px;
}

.task-progress__done {
  color: var(--el-color-success);
}

.task-progress__error {
  color: var(--el-color-danger);
}

.task-progress__pending {
  color: var(--el-text-color-placeholder);
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
