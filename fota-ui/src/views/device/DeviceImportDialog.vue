<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadProps } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { importDevices, type DeviceImportResult } from '@/api/device'
import { searchProducts, type ProductItem } from '@/api/product'

const { t } = useI18n()

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

const uploading = ref(false)
const uploadPercent = ref(0)
const uploadResult = ref<DeviceImportResult | null>(null)
const uploadError = ref('')

const formRef = ref()
const uploadRef = ref()
const form = reactive({
  productId: undefined as number | undefined,
  batchName: '',
  file: null as File | null,
})

const productSearchOptions = ref<ProductItem[]>([])
const productSearchLoading = ref(false)

let productSearchTimer: number | null = null

const formRules = {
  productId: [{ required: true, message: t('device.productIdRequired'), trigger: 'change' }],
  file: [
    {
      validator: (_rule: unknown, value: File | null, callback: (error?: Error) => void) => {
        if (!value) {
          callback(new Error(t('device.fileRequired')))
          return
        }
        // 检查文件大小 10MB
        const maxSize = 10 * 1024 * 1024
        if (value.size > maxSize) {
          callback(new Error(t('device.fileSizeExceeded')))
          return
        }
        // 检查文件格式
        const fileName = value.name.toLowerCase()
        const allowedExtensions = ['.xlsx', '.xls', '.txt']
        const isValidExtension = allowedExtensions.some(ext => fileName.endsWith(ext))
        if (!isValidExtension) {
          callback(new Error(t('device.fileFormatInvalid')))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
}

/**
 * 产品远程搜索
 */
const handleProductSearch = async (keyword: string) => {
  const trimmedKeyword = (keyword || '').trim()

  if (productSearchTimer !== null) {
    clearTimeout(productSearchTimer)
  }

  productSearchTimer = window.setTimeout(async () => {
    productSearchLoading.value = true
    try {
      // 空字符串时也会搜索，返回所有产品
      const result = await searchProducts(trimmedKeyword)
      productSearchOptions.value = result.records || []
    } finally {
      productSearchLoading.value = false
    }
  }, 300)
}

/**
 * 产品选择框获得焦点时触发搜索（加载前20个产品）
 */
const handleProductFocus = () => {
  if (productSearchOptions.value.length === 0 && !productSearchLoading.value) {
    handleProductSearch('')
  }
}

/**
 * 文件选择变化
 */
const handleFileChange: UploadProps['onChange'] = (uploadFile) => {
  if (uploadFile.raw) {
    form.file = uploadFile.raw
    formRef.value?.validateField('file')
  }
}

/**
 * 文件移除
 */
const handleFileRemove = () => {
  form.file = null
  uploadResult.value = null
  uploadError.value = ''
}

/**
 * 清理上传组件的文件列表
 */
const clearUploadFiles = () => {
  uploadRef.value?.clearFiles()
  form.file = null
}

/**
 * 上传前校验
 */
const beforeUpload: UploadProps['beforeUpload'] = () => {
  if (!form.productId) {
    ElMessage.warning(t('device.productIdRequired'))
    return false
  }
  if (!form.file) {
    ElMessage.warning(t('device.fileRequired'))
    return false
  }
  return true
}

/**
 * 执行导入
 */
const handleImport = async () => {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  if (!form.file) {
    ElMessage.warning(t('device.fileRequired'))
    return
  }

  uploading.value = true
  uploadPercent.value = 0
  uploadResult.value = null
  uploadError.value = ''

  try {
    const result = await importDevices({
      file: form.file,
      productId: form.productId!,
      batchName: form.batchName || undefined,
    })

    uploadPercent.value = 100
    uploadResult.value = result

    if (result.status === 'SUCCESS') {
      ElMessage.success(t('device.importSuccess'))
    } else if (result.status === 'PARTIAL') {
      ElMessage.warning(t('device.importPartial'))
    } else if (result.status === 'FAILED') {
      ElMessage.error(t('device.importFailed'))
    }

    // 清理文件
    clearUploadFiles()
    emit('success')
  } catch (error: any) {
    uploadError.value = error?.message || t('device.importFailed')
    ElMessage.error(uploadError.value)
  } finally {
    uploading.value = false
  }
}

/**
 * 关闭对话框
 */
const handleClose = () => {
  if (uploading.value) {
    return
  }
  dialogVisible.value = false
}

/**
 * 对话框关闭后重置
 */
const handleClosed = () => {
  // 清理上传组件文件列表
  clearUploadFiles()
  formRef.value?.resetFields()
  form.productId = undefined
  form.batchName = ''
  uploadPercent.value = 0
  uploadResult.value = null
  uploadError.value = ''
  productSearchOptions.value = []
}

/**
 * 获取状态类型
 */
const getStatusType = (status: string) => {
  switch (status) {
    case 'SUCCESS':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'PARTIAL':
      return 'warning'
    default:
      return 'info'
  }
}

/**
 * 获取状态文本
 */
const getStatusText = (status: string) => {
  switch (status) {
    case 'SUCCESS':
      return t('device.importSuccess')
    case 'FAILED':
      return t('device.importFailed')
    case 'PARTIAL':
      return t('device.importPartial')
    default:
      return status
  }
}
</script>

<template>
  <el-dialog
    :model-value="dialogVisible"
    :title="t('device.import')"
    width="600px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    @close="handleClose"
    @closed="handleClosed"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="formRules"
      label-width="120px"
    >
      <el-form-item
        prop="productId"
        :label="t('device.productId')"
      >
        <el-select
          v-model="form.productId"
          filterable
          remote
          reserve-keyword
          :remote-method="handleProductSearch"
          :placeholder="t('device.productId')"
          :loading="productSearchLoading"
          :disabled="uploading"
          style="width: 100%"
          @focus="handleProductFocus"
        >
          <el-option
            v-for="product in productSearchOptions"
            :key="product.id"
            :label="product.name"
            :value="product.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item
        prop="batchName"
        :label="t('device.batchName')"
      >
        <el-input
          v-model="form.batchName"
          :placeholder="t('device.batchNamePlaceholder')"
          :disabled="uploading"
        />
        <div class="text-xs text-gray-500 mt-1">
          {{ t('device.batchNameTip') }}
        </div>
      </el-form-item>

      <el-form-item
        prop="file"
        :label="t('device.uploadFile')"
      >
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :show-file-list="true"
          :limit="1"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
          :before-upload="beforeUpload"
          :disabled="uploading"
          drag
        >
          <div class="el-upload__text">
            {{ t('device.uploadTip') }}
          </div>
          <template #tip>
            <div class="el-upload__tip">
              {{ t('device.supportedFormats') }}
            </div>
          </template>
        </el-upload>
      </el-form-item>

      <!-- 导入进度 -->
      <div
        v-if="uploading || uploadResult"
        class="mb-4"
      >
        <div class="text-sm font-medium mb-2">
          {{ t('device.importProgress') }}
        </div>

        <!-- 进度条 -->
        <el-progress
          v-if="uploading"
          :percentage="uploadPercent"
          :status="uploadError ? 'exception' : undefined"
          :indeterminate="uploading && uploadPercent === 0"
        />

        <!-- 结果展示 -->
        <div
          v-if="uploadResult"
          class="mt-3 p-3 bg-gray-50 dark:bg-gray-800 rounded"
        >
          <div class="flex items-center gap-2 mb-2">
            <span class="text-sm font-medium">{{ t('device.importResult') }}:</span>
            <el-tag
              :type="getStatusType(uploadResult.status)"
              size="small"
            >
              {{ getStatusText(uploadResult.status) }}
            </el-tag>
          </div>

          <div class="grid grid-cols-3 gap-4 text-sm">
            <div>
              <span class="text-gray-500">{{ t('device.totalDevices') }}:</span>
              <span class="ml-1 font-medium">{{ uploadResult.totalCount }}</span>
            </div>
            <div>
              <span class="text-gray-500">{{ t('device.successCount') }}:</span>
              <span class="ml-1 font-medium text-green-600">{{ uploadResult.successCount }}</span>
            </div>
            <div>
              <span class="text-gray-500">{{ t('device.failedCount') }}:</span>
              <span class="ml-1 font-medium text-red-600">{{ uploadResult.failedCount }}</span>
            </div>
          </div>

          <div
            v-if="uploadResult.errorMessage"
            class="mt-2 text-sm text-red-600"
          >
            {{ uploadResult.errorMessage }}
          </div>
        </div>
      </div>
    </el-form>

    <template #footer>
      <el-button
        :disabled="uploading"
        @click="handleClose"
      >
        {{ uploading ? t('device.importing') : t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        :loading="uploading"
        @click="handleImport"
      >
        {{ uploading ? t('device.importing') : t('device.startImport') }}
      </el-button>
    </template>
  </el-dialog>
</template>
