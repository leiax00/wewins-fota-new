<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import type { UploadProps } from 'element-plus'
import * as XLSX from 'xlsx'

const { t } = useI18n()

const props = withDefaults(
  defineProps<{
    modelValue: string
    mode?: 'text' | 'file' | 'both'
    maxSize?: number
    maxCount?: number
    placeholder?: string
    disabled?: boolean
  }>(),
  {
    mode: 'both',
    maxSize: 10,
    maxCount: 100000,
    placeholder: '',
    disabled: false,
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'change': [imeis: string[], validCount: number, invalidCount: number]
}>()

const activeMode = ref<'text' | 'file'>('text')
const textInput = ref(props.modelValue)
const uploadRef = ref()
const uploadingFile = ref(false)
const currentFileName = ref<string | null>(null)

watch(
  () => props.modelValue,
  (val) => {
    textInput.value = val
  }
)

watch(textInput, (val) => {
  emit('update:modelValue', val)
  const result = parseImeis(val)
  emit('change', result.imeis, result.validCount, result.invalidCount)
})

const parseImeis = (text: string) => {
  if (!text) {
    return { imeis: [] as string[], validCount: 0, invalidCount: 0 }
  }

  const imeis = text
    .split(/[,\n，]/)
    .map(line => line.trim())
    .filter(line => line.length > 0)

  const imeiPattern = /^\d{15}$/
  const validImeis: string[] = []
  let invalidCount = 0

  for (const imei of imeis) {
    if (imeiPattern.test(imei)) {
      validImeis.push(imei)
    } else {
      invalidCount++
    }
  }

  return {
    imeis: validImeis,
    validCount: validImeis.length,
    invalidCount,
  }
}

const parseResult = computed(() => parseImeis(textInput.value))
const validCount = computed(() => parseResult.value.validCount)
const invalidCount = computed(() => parseResult.value.invalidCount)
const totalCount = computed(() => validCount.value + invalidCount.value)

const canSubmit = computed(() => {
  return validCount.value > 0 && (!props.maxCount || validCount.value <= props.maxCount)
})

const countExceeded = computed(() => {
  return props.maxCount && validCount.value > props.maxCount
})

const showModeSwitch = computed(() => props.mode === 'both')

const currentMode = computed({
  get: () => {
    if (props.mode === 'both') {
      return activeMode.value
    }
    return props.mode
  },
  set: (val) => {
    activeMode.value = val
    textInput.value = ''
    currentFileName.value = null
    // 切换模式时清空上传组件的文件列表
    uploadRef.value?.clearFiles()
  },
})

const readExcelFile = async (file: File): Promise<string[]> => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (e) => {
      try {
        const data = new Uint8Array(e.target?.result as ArrayBuffer)
        const workbook = XLSX.read(data, { type: 'array' })

        const firstSheetName = workbook.SheetNames[0]
        const worksheet = workbook.Sheets[firstSheetName]
        const jsonData = XLSX.utils.sheet_to_json<(string | number | null)[]>(worksheet, { header: 1 })

        if (jsonData.length === 0) {
          resolve([])
          return
        }

        // 获取第一行作为表头，查找 "imei" 列（忽略大小写）
        const header = jsonData[0]
        if (!Array.isArray(header)) {
          resolve([])
          return
        }

        const imeiColumnIndex = header.findIndex(
          (cell) => cell && typeof cell === 'string' && cell.trim().toLowerCase() === 'imei'
        )

        if (imeiColumnIndex === -1) {
          resolve([])
          return
        }

        // 只解析 imei 列的数据（从第二行开始）
        const imeis: string[] = []
        for (let i = 1; i < jsonData.length; i++) {
          const row = jsonData[i]
          if (!Array.isArray(row)) continue

          const cell = row[imeiColumnIndex]
          if (cell != null) {
            const value = typeof cell === 'number' ? String(cell) : String(cell).trim()
            if (value.length > 0) {
              imeis.push(value)
            }
          }
        }

        resolve(imeis)
      } catch (err) {
        reject(err)
      }
    }
    reader.onerror = () => reject(new Error('文件读取失败'))
    reader.readAsArrayBuffer(file)
  })
}

const handleFileChange: UploadProps['onChange'] = async (uploadFile) => {
  const file = uploadFile.raw
  if (!file) return

  const maxSizeBytes = props.maxSize * 1024 * 1024
  if (file.size > maxSizeBytes) {
    ElMessage.error(t('device.fileSizeExceeded'))
    return
  }

  const fileName = file.name.toLowerCase()
  const allowedExtensions = ['.txt', '.xlsx', '.xls']

  if (!allowedExtensions.some(ext => fileName.endsWith(ext))) {
    ElMessage.error(t('device.fileFormatInvalid'))
    return
  }

  uploadingFile.value = true

  try {
    let imeis: string[] = []

    if (fileName.endsWith('.txt')) {
      const text = await file.text()
      imeis = text
        .split('\n')
        .map(line => line.trim())
        .filter(line => line.length > 0)
    } else if (fileName.endsWith('.xlsx') || fileName.endsWith('.xls')) {
      imeis = await readExcelFile(file)
    }

    if (imeis.length === 0) {
      ElMessage.warning(t('device.noValidImeiInFile'))
      return
    }

    // 更新文本输入
    textInput.value = imeis.join('\n')
    // 存储文件名
    currentFileName.value = file.name

    const result = parseImeis(textInput.value)
    if (result.validCount > 0) {
      ElMessage.success(t('device.imeiFileParsed', { count: result.validCount }))
    }

    if (result.invalidCount > 0) {
      ElMessage.warning(t('device.imeiFileInvalidCount', { count: result.invalidCount }))
    }
  } catch (err) {
    console.error('文件解析失败:', err)
    ElMessage.error(t('device.imeiFileParseFailed'))
  } finally {
    uploadingFile.value = false
  }
}

const clearUploadFiles = () => {
  uploadRef.value?.clearFiles()
  currentFileName.value = null
}

const clearInput = () => {
  textInput.value = ''
  currentFileName.value = null
  // 清空上传组件的文件列表
  uploadRef.value?.clearFiles()
}

defineExpose({
  clearInput,
  getImeis: () => parseResult.value.imeis,
  canSubmit,
  getSourceName: () => currentFileName.value || (currentMode.value === 'file' ? '文件上传' : '文本输入'),
})
</script>

<template>
  <div class="imei-input-panel">
    <!-- 模式切换 - 使用策略表单的 compact-radio-group 样式 -->
    <div v-if="showModeSwitch" class="mode-switch-group">
      <el-radio-group v-model="currentMode" :disabled="disabled" class="compact-radio-group">
        <el-radio-button value="text">
          {{ t('device.textInput') }}
        </el-radio-button>
        <el-radio-button value="file">
          {{ t('device.fileUpload') }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <!-- 文本输入 -->
    <div v-if="currentMode === 'text'" class="input-section">
      <el-input
        v-model="textInput"
        type="textarea"
        :rows="4"
        :placeholder="placeholder || t('device.imeisPlaceholder')"
        :disabled="disabled"
      />

      <!-- 统计信息 -->
      <Transition name="fade-slide">
        <div v-if="totalCount > 0 || countExceeded" class="result-card" :class="{ warning: countExceeded }">
          <div class="result-stats">
            <div class="stat-item">
              <span class="stat-label">{{ t('device.totalCount') }}</span>
              <span class="stat-value">{{ totalCount }}</span>
            </div>
            <div class="stat-divider"></div>
            <div class="stat-item success">
              <span class="stat-label">{{ t('device.validCount') }}</span>
              <span class="stat-value">{{ validCount }}</span>
            </div>
            <div v-if="invalidCount > 0" class="stat-divider"></div>
            <div v-if="invalidCount > 0" class="stat-item error">
              <span class="stat-label">{{ t('device.invalidCount') }}</span>
              <span class="stat-value">{{ invalidCount }}</span>
            </div>
          </div>
          <div v-if="countExceeded" class="result-message">
            <el-icon><Warning /></el-icon>
            <span>{{ t('device.countExceeded', { max: maxCount }) }}</span>
          </div>
        </div>
      </Transition>
    </div>

    <!-- 文件上传 -->
    <div v-else class="upload-section">
      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :show-file-list="true"
        :limit="1"
        :on-change="handleFileChange"
        :on-remove="clearUploadFiles"
        :disabled="disabled || uploadingFile"
        drag
        accept=".txt,.xlsx,.xls"
      >
        <div class="upload-area" :class="{ uploading: uploadingFile }">
          <template v-if="!uploadingFile">
            <el-icon class="upload-icon"><UploadFilled /></el-icon>
            <span class="upload-text">{{ t('device.uploadTip') }}</span>
            <span class="upload-hint">.txt .xlsx .xls</span>
          </template>
          <template v-else>
            <el-icon class="upload-icon is-loading"><Loading /></el-icon>
            <span class="upload-text">{{ t('device.parsing') }}</span>
          </template>
        </div>
      </el-upload>

      <Transition name="fade-slide">
        <div v-if="totalCount > 0" class="result-card">
          <div class="result-stats">
            <div class="stat-item">
              <span class="stat-label">{{ t('device.totalCount') }}</span>
              <span class="stat-value">{{ totalCount }}</span>
            </div>
            <div class="stat-divider"></div>
            <div class="stat-item success">
              <span class="stat-label">{{ t('device.validCount') }}</span>
              <span class="stat-value">{{ validCount }}</span>
            </div>
            <div v-if="invalidCount > 0" class="stat-divider"></div>
            <div v-if="invalidCount > 0" class="stat-item error">
              <span class="stat-label">{{ t('device.invalidCount') }}</span>
              <span class="stat-value">{{ invalidCount }}</span>
            </div>
          </div>
        </div>
      </Transition>
    </div>
  </div>
</template>

<style scoped>
.imei-input-panel {
  width: 100%;
}

/* 模式切换组 - 复用策略表单的 compact-radio-group 样式 */
.mode-switch-group {
  margin-bottom: 16px;
}

.compact-radio-group :deep(.el-radio-button) {
  flex: 1;
}

.compact-radio-group :deep(.el-radio-button__inner) {
  width: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
}

/* 移除按钮之间的间隙 - 紧贴效果 */
.compact-radio-group :deep(.el-radio-button:first-child .el-radio-button__inner) {
  border-top-right-radius: 0;
  border-bottom-right-radius: 0;
}

.compact-radio-group :deep(.el-radio-button:last-child .el-radio-button__inner) {
  border-top-left-radius: 0;
  border-bottom-left-radius: 0;
}

.compact-radio-group :deep(.el-radio-button:not(:first-child):not(:last-child) .el-radio-button__inner) {
  border-radius: 0;
}

.compact-radio-group :deep(.el-radio-button:not(:first-child) .el-radio-button__inner) {
  margin-left: -1px;
}

/* 输入区域 */
.input-section,
.upload-section {
  width: 100%;
}

/* 结果卡片 - 适配系统风格 */
.result-card {
  padding: 12px 16px;
  margin-top: 12px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.result-card.warning {
  background: var(--el-color-warning-light-9);
  border-color: var(--el-color-warning-light-5);
}

.result-stats {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.stat-value {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.stat-item.success .stat-value {
  color: var(--el-color-success);
}

.stat-item.error .stat-value {
  color: var(--el-color-danger);
}

.stat-divider {
  width: 1px;
  height: 24px;
  background: var(--el-border-color);
}

.result-message {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  font-size: 13px;
  color: var(--el-color-warning);
}

/* 上传区域 */
.upload-section :deep(.el-upload) {
  width: 100%;
}

.upload-section :deep(.el-upload-dragger) {
  padding: 24px;
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
  background: transparent;
  transition: all 0.3s;
}

.upload-section :deep(.el-upload-dragger:hover) {
  border-color: var(--el-color-primary);
}

.upload-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.upload-icon {
  font-size: 32px;
  color: var(--el-text-color-placeholder);
}

.upload-area.uploading .upload-icon {
  color: var(--el-color-primary);
}

.upload-text {
  font-size: 14px;
  color: var(--el-text-color-regular);
}

.upload-hint {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

/* 过渡动画 - 与系统一致 */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.3s ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(-10px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
</style>
