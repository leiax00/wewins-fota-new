<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { CircleCheck, CircleClose, Loading } from '@element-plus/icons-vue'
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

const isCompleted = computed(() => props.taskProgress.stage === 'COMPLETED')
const isFailed = computed(() => props.taskProgress.stage === 'FAILED' || props.taskProgress.stage === 'CANCELLED')
const isRunning = computed(() => props.taskProgress.stage === 'INIT' || props.taskProgress.stage === 'PROCESSING')

const uploadStepStatus = computed((): 'wait' | 'process' | 'success' | 'error' => {
  if (props.taskProgress.phase === 'CDN_WARM' || isCompleted.value) return 'success'
  if (isFailed.value) return 'error'
  if (isRunning.value) return 'process'
  return 'wait'
})

const warmStepStatus = computed((): 'wait' | 'process' | 'success' | 'error' => {
  if (isCompleted.value) return 'success'
  if (isFailed.value && props.taskProgress.phase === 'CDN_WARM') return 'error'
  if (props.taskProgress.phase === 'CDN_WARM' && isRunning.value) return 'process'
  return 'wait'
})

const statusType = computed(() => {
  if (isCompleted.value) return 'success'
  if (isFailed.value) return 'danger'
  return 'info'
})

const statusText = computed(() => {
  if (isCompleted.value) return t('firmware.taskDone')
  if (isFailed.value) return t('firmware.taskFailed')
  return t('firmware.taskProcessing')
})

const steps = computed(() => [
  {
    title: t('firmware.taskUploadToStorage'),
    description: t('firmware.taskUploadToStorageDesc'),
    status: uploadStepStatus.value,
  },
  {
    title: t('firmware.taskCdnWarm'),
    description: t('firmware.taskCdnWarmDesc'),
    status: warmStepStatus.value,
  },
])

const handleFinish = () => emit('finish')
const handleClose = () => emit('close')
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="t('firmware.publishProgress')"
    width="520px"
    :close-on-click-modal="taskProgress.finished"
    :close-on-press-escape="taskProgress.finished"
    :show-close="taskProgress.finished"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="task-progress">
      <div class="task-progress__header">
        <div class="task-progress__title">{{ t('firmware.processingVersion') }}</div>
        <el-tag :type="statusType" effect="light">
          {{ statusText }}
        </el-tag>
      </div>

      <div class="task-progress__message">
        {{ taskProgress.message || t('firmware.taskPreparing') }}
      </div>

      <el-progress
        :percentage="taskProgress.percent"
        :status="isFailed ? 'exception' : isCompleted ? 'success' : undefined"
        :stroke-width="10"
      />

      <el-steps direction="vertical" :space="88" class="task-progress__steps">
        <el-step
          v-for="step in steps"
          :key="step.title"
          :status="step.status"
        >
          <template #title>
            <div class="task-progress__step-title">{{ step.title }}</div>
          </template>
          <template #description>
            <div class="task-progress__step-description">{{ step.description }}</div>
          </template>
          <template #icon>
            <el-icon v-if="step.status === 'success'" class="task-progress__icon task-progress__icon--success">
              <CircleCheck />
            </el-icon>
            <el-icon v-else-if="step.status === 'error'" class="task-progress__icon task-progress__icon--error">
              <CircleClose />
            </el-icon>
            <el-icon v-else-if="step.status === 'process'" class="task-progress__icon task-progress__icon--process">
              <Loading />
            </el-icon>
            <span v-else class="task-progress__icon task-progress__icon--wait" />
          </template>
        </el-step>
      </el-steps>

      <el-alert
        v-if="isFailed && taskProgress.errorMsg"
        type="error"
        :closable="false"
        :title="taskProgress.errorMsg"
      />

      <el-alert
        v-else-if="isCompleted"
        type="success"
        :closable="false"
        :title="t('firmware.processSuccess')"
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
        {{ t('firmware.continueInBackground') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.task-progress {
  display: grid;
  gap: 16px;
}

.task-progress__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.task-progress__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.task-progress__message {
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  line-height: 1.6;
}

.task-progress__steps {
  padding: 4px 0;
}

.task-progress__step-title {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.task-progress__step-description {
  margin-top: 4px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.task-progress__icon {
  display: inline-flex;
  width: 20px;
  height: 20px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
}

.task-progress__icon--success {
  color: var(--el-color-success);
}

.task-progress__icon--error {
  color: var(--el-color-danger);
}

.task-progress__icon--process {
  color: var(--el-color-primary);
  animation: task-progress-spin 0.9s linear infinite;
}

.task-progress__icon--wait {
  width: 10px;
  height: 10px;
  margin: 5px;
  background: var(--el-border-color-darker);
}

@keyframes task-progress-spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
