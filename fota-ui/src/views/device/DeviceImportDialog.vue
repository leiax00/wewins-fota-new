<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  executeImportDevices,
  type DeviceImportResult,
} from '@/api/device'
import { searchProducts, type ProductItem } from '@/api/product'
import ImeiInputPanel from '@/components/imei-input/ImeiInputPanel.vue'

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
const uploadResult = ref<DeviceImportResult | null>(null)
const uploadError = ref('')

const imeiInputRef = ref()
const imeisText = ref('')
const formRef = ref()
const form = reactive({
  productId: undefined as number | undefined,
  batchName: '',
})

const productSearchOptions = ref<ProductItem[]>([])
const productSearchLoading = ref(false)

let productSearchTimer: number | null = null

const formRules = {
  productId: [{ required: true, message: t('device.productIdRequired'), trigger: 'change' }],
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
      const result = await searchProducts(trimmedKeyword)
      productSearchOptions.value = result.records || []
    } finally {
      productSearchLoading.value = false
    }
  }, 300)
}

/**
 * 产品选择框获得焦点时触发搜索
 */
const handleProductFocus = () => {
  if (productSearchOptions.value.length === 0 && !productSearchLoading.value) {
    handleProductSearch('')
  }
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

  if (!form.productId) {
    ElMessage.warning(t('device.productIdRequired'))
    return
  }

  const imeis = imeiInputRef.value?.getImeis()
  if (!imeis || imeis.length === 0) {
    ElMessage.warning(t('device.noValidImei'))
    return
  }

  uploading.value = true
  uploadResult.value = null
  uploadError.value = ''

  try {
    const result = await executeImportDevices({
      imeis,
      productId: form.productId,
      batchName: form.batchName || undefined,
      sourceFile: imeiInputRef.value?.getSourceName(),
    })

    uploadResult.value = result

    if (result.status === 'SUCCESS') {
      ElMessage.success(t('device.importSuccess'))
    } else if (result.status === 'PARTIAL') {
      ElMessage.warning(t('device.importPartial'))
    } else if (result.status === 'FAILED') {
      ElMessage.error(t('device.importFailed'))
    }

    // 清空输入
    imeiInputRef.value?.clearInput()
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
  formRef.value?.resetFields()
  form.productId = undefined
  form.batchName = ''
  uploadResult.value = null
  uploadError.value = ''
  productSearchOptions.value = []
  imeiInputRef.value?.clearInput()
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
    width="700px"
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
        required
        :label="t('device.inputImeis')"
      >
        <ImeiInputPanel
          ref="imeiInputRef"
          v-model="imeisText"
          mode="both"
          :max-count="100000"
          :disabled="uploading"
          :placeholder="t('device.imeisPlaceholder')"
        />
      </el-form-item>

      <!-- 导入结果 -->
      <div
        v-if="uploadResult"
        class="result-panel"
      >
        <div class="flex items-center gap-2 mb-3">
          <span class="text-sm font-medium">{{ t('device.importResult') }}:</span>
          <el-tag
            :type="getStatusType(uploadResult.status)"
            size="small"
          >
            {{ getStatusText(uploadResult.status) }}
          </el-tag>
        </div>

        <div class="grid grid-cols-3 gap-4 text-sm">
          <div class="result-item">
            <span class="text-gray-500">{{ t('device.totalDevices') }}:</span>
            <span class="ml-1 font-medium">{{ uploadResult.totalCount }}</span>
          </div>
          <div class="result-item">
            <span class="text-gray-500">{{ t('device.successCount') }}:</span>
            <span class="ml-1 font-medium text-green-600">{{ uploadResult.successCount }}</span>
          </div>
          <div class="result-item">
            <span class="text-gray-500">{{ t('device.failedCount') }}:</span>
            <span class="ml-1 font-medium text-red-600">{{ uploadResult.failedCount }}</span>
          </div>
        </div>

        <div
          v-if="uploadResult.errorMessage"
          class="mt-3 text-sm text-red-600"
        >
          {{ uploadResult.errorMessage }}
        </div>
      </div>
    </el-form>

    <template #footer>
      <el-button
        :disabled="uploading"
        @click="handleClose"
      >
        {{ t('common.cancel') }}
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

<style scoped>
.result-panel {
  padding: 16px;
  margin-top: 16px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

.result-item {
  display: flex;
  align-items: center;
}
</style>
