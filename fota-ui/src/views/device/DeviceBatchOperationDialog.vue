<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {useI18n} from 'vue-i18n'
import {
  Box,
  Check,
  CircleCheck,
  CircleCloseFilled,
  Delete,
  Document,
  Edit,
  FolderOpened,
  SuccessFilled,
  TrendCharts,
  WarningFilled,
} from '@element-plus/icons-vue'
import JsonFieldEditor from '@/components/json-field/JsonFieldEditor.vue'
import ImeiInputPanel from '@/components/imei-input/ImeiInputPanel.vue'
import {
  type BatchOperationRequest,
  type BatchOperationResult,
  type BatchOperationType,
  estimateBatchOperation,
  executeBatchOperation,
} from '@/api/device'
import {type DeviceImportBatchItem, pageBatches} from '@/api/deviceImportBatch'
import {searchProducts, type ProductItem} from '@/api/product'

interface Props {
  modelValue: boolean
  defaultOperationType?: BatchOperationType | null
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void

  (e: 'success'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const {t} = useI18n()
const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

// 当前步骤
const currentStep = computed(() => {
  if (!form.operationType) return 1
  if (result.value) return 4
  if (estimateCount.value !== null) return 3
  return 2
})

// IMEI 输入组件引用
const imeiInputRef = ref()

// 表单数据
const form = reactive<{
  operationType: BatchOperationType | null
  batchId: number | undefined
  imeisText: string
  productId: number | undefined
  imeiKeyword: string
  status: 'ONLINE' | 'OFFLINE' | 'LOST' | undefined
  importBatchId: number | undefined
  tags: Record<string, unknown>
  newBatchId: number | undefined
}>({
  operationType: null,
  batchId: undefined,
  imeisText: '',
  productId: undefined,
  imeiKeyword: '',
  status: undefined,
  importBatchId: undefined,
  tags: {},
  newBatchId: undefined,
})

// 标签校验错误
const tagsValidationErrors = ref<string[]>([])

// 操作类型选项
const operationTypes: { label: string; value: BatchOperationType; icon: any; color: string; description: string }[] = [
  {
    label: 'device.opDeleteByBatch',
    value: 'DELETE_BY_BATCH',
    icon: Delete,
    color: '#f56c6c',
    description: '删除指定批次下的所有设备'
  },
  {
    label: 'device.opUpdateTagByBatch',
    value: 'UPDATE_TAG_BY_BATCH',
    icon: Edit,
    color: '#409eff',
    description: '批量修改批次下设备的标签'
  },
  {
    label: 'device.opUpdateTagByImei',
    value: 'UPDATE_TAG_BY_IMEI',
    icon: Document,
    color: '#67c23a',
    description: '按IMEI列表修改设备标签'
  },
  {
    label: 'device.opUpdateBatchByImei',
    value: 'UPDATE_BATCH_BY_IMEI',
    icon: FolderOpened,
    color: '#e6a23c',
    description: '按IMEI列表修改设备批次'
  },
]

// 计算属性：根据操作类型显示/隐藏相关字段
const needBatchId = computed(() => {
  return form.operationType === 'DELETE_BY_BATCH' || form.operationType === 'UPDATE_TAG_BY_BATCH'
})

const needProductId = computed(() => {
  return form.operationType === 'DELETE_BY_BATCH' || form.operationType === 'UPDATE_TAG_BY_BATCH'
})

const needImeis = computed(() => {
  return form.operationType === 'UPDATE_TAG_BY_IMEI' || form.operationType === 'UPDATE_BATCH_BY_IMEI'
})

const needTags = computed(() => {
  return (
      form.operationType === 'UPDATE_TAG_BY_BATCH' ||
      form.operationType === 'UPDATE_TAG_BY_IMEI'
  )
})

const needNewBatchId = computed(() => {
  return form.operationType === 'UPDATE_BATCH_BY_IMEI'
})

// 产品搜索选项
const productSearchOptions = ref<ProductItem[]>([])
const productSearchLoading = ref(false)
let productSearchTimer: number | null = null

// 批次搜索选项
const batchSearchOptions = ref<DeviceImportBatchItem[]>([])
const batchSearchLoading = ref(false)
let batchSearchTimer: number | null = null

// 新批次搜索选项（用于UPDATE_BATCH操作）
const newBatchSearchOptions = ref<DeviceImportBatchItem[]>([])
const newBatchSearchLoading = ref(false)
let newBatchSearchTimer: number | null = null

// 预估数量
const estimateCount = ref<number | null>(null)
const estimating = ref(false)

// 执行结果
const result = ref<BatchOperationResult | null>(null)
const executing = ref(false)

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
 * 产品变化时清空批次选择
 */
const handleProductChange = () => {
  form.batchId = undefined
  batchSearchOptions.value = []
}

/**
 * 批次远程搜索
 */
const handleBatchSearch = async (keyword: string) => {
  const trimmedKeyword = (keyword || '').trim()
  if (!trimmedKeyword) {
    batchSearchOptions.value = []
    return
  }

  if (batchSearchTimer !== null) {
    clearTimeout(batchSearchTimer)
  }

  batchSearchTimer = window.setTimeout(async () => {
    batchSearchLoading.value = true
    try {
      const result = await pageBatches({
        batchName: trimmedKeyword,
        productId: form.productId, // 添加产品筛选
        page: 1,
        size: 50,
      })
      batchSearchOptions.value = result.records || []
    } finally {
      batchSearchLoading.value = false
    }
  }, 300)
}

/**
 * 新批次远程搜索（用于UPDATE_BATCH操作）
 */
const handleNewBatchSearch = async (keyword: string) => {
  const trimmedKeyword = (keyword || '').trim()
  if (!trimmedKeyword) {
    newBatchSearchOptions.value = []
    return
  }

  if (newBatchSearchTimer !== null) {
    clearTimeout(newBatchSearchTimer)
  }

  newBatchSearchTimer = window.setTimeout(async () => {
    newBatchSearchLoading.value = true
    try {
      const result = await pageBatches({
        batchName: trimmedKeyword,
        page: 1,
        size: 50,
      })
      newBatchSearchOptions.value = result.records || []
    } finally {
      newBatchSearchLoading.value = false
    }
  }, 300)
}

/**
 * 处理标签校验变化
 */
const handleTagsValidationChange = (errors: string[]) => {
  tagsValidationErrors.value = errors
}

/**
 * 预估影响设备数
 */
const handleEstimate = async () => {
  if (!form.operationType) {
    ElMessage.warning(t('device.pleaseSelectOperationType'))
    return
  }

  // 构建请求参数
  const params = buildRequestParams()
  if (!params) return

  estimating.value = true
  estimateCount.value = null

  try {
    const count = await estimateBatchOperation(params)
    estimateCount.value = count
  } finally {
    estimating.value = false
  }
}

/**
 * 构建请求参数
 */
const buildRequestParams = (): BatchOperationRequest | null => {
  if (!form.operationType) return null

  const params: BatchOperationRequest = {
    operationType: form.operationType,
  }

  // 按批次操作
  if (needBatchId.value) {
    if (!form.productId) {
      ElMessage.warning(t('device.pleaseSelectProduct'))
      return null
    }
    if (!form.batchId) {
      ElMessage.warning(t('device.pleaseSelectBatch'))
      return null
    }
    params.productId = form.productId
    params.batchId = form.batchId
  }

  // 按IMEI列表操作
  if (needImeis.value) {
    if (!imeiInputRef.value?.canSubmit) {
      ElMessage.warning(t('device.noValidImei'))
      return null
    }

    const imeis = imeiInputRef.value?.getImeis()
    if (!imeis || imeis.length === 0) {
      ElMessage.warning(t('device.pleaseInputImeis'))
      return null
    }
    params.imeis = imeis
  }

  // 修改标签操作
  if (needTags.value) {
    // 验证不是空对象
    if (Object.keys(form.tags).length === 0) {
      ElMessage.warning(t('device.pleaseInputAtLeastOneTagField'))
      return null
    }
    if (tagsValidationErrors.value.length > 0) {
      ElMessage.error(tagsValidationErrors.value[0])
      return null
    }
    // 序列化对象为 JSON 字符串
    params.tags = JSON.stringify(form.tags)
  }

  // 修改批次操作
  if (needNewBatchId.value) {
    if (!form.newBatchId) {
      ElMessage.warning(t('device.pleaseSelectNewBatch'))
      return null
    }
    params.newBatchId = form.newBatchId
  }

  return params
}

/**
 * 执行批量操作
 */
const handleExecute = async () => {
  if (!form.operationType) {
    ElMessage.warning(t('device.pleaseSelectOperationType'))
    return
  }

  // 先预估
  if (estimateCount.value === null) {
    await handleEstimate()
    if (estimateCount.value === null || estimateCount.value === 0) {
      return
    }
  }

  // 确认操作
  try {
    await ElMessageBox.confirm(
        t('device.confirmBatchOperation', {count: estimateCount.value}),
        t('common.tip'),
        {type: 'warning'}
    )
  } catch {
    return
  }

  // 构建请求参数
  const params = buildRequestParams()
  if (!params) return

  executing.value = true
  result.value = null

  try {
    const response = await executeBatchOperation(params)
    result.value = response

    if (response.failedCount === 0) {
      ElMessage.success(t('device.batchOperationSuccess'))
    } else if (response.successCount > 0) {
      ElMessage.warning(t('device.batchOperationPartial'))
    } else {
      ElMessage.error(t('device.batchOperationFailed'))
    }

    // 成功后刷新列表
    if (response.successCount > 0) {
      emit('success')
    }
  } finally {
    executing.value = false
  }
}

/**
 * 重置表单
 */
const resetForm = () => {
  form.operationType = null
  form.batchId = undefined
  form.imeisText = ''
  form.productId = undefined
  form.imeiKeyword = ''
  form.status = undefined
  form.importBatchId = undefined
  form.tags = {}
  form.newBatchId = undefined
  tagsValidationErrors.value = []
  estimateCount.value = null
  result.value = null
  productSearchOptions.value = []
  batchSearchOptions.value = []
  newBatchSearchOptions.value = []
  imeiInputRef.value?.clearInput()
}

/**
 * 选择操作类型
 */
const selectOperationType = (type: BatchOperationType) => {
  form.operationType = type
  estimateCount.value = null
  result.value = null

  // 如果切换到不需要标签的操作类型，清空标签相关状态
  const typeNeedsTags =
      type === 'UPDATE_TAG_BY_BATCH' ||
      type === 'UPDATE_TAG_BY_IMEI'

  if (!typeNeedsTags) {
    form.tags = {}
    tagsValidationErrors.value = []
  }
}

/**
 * 监听默认操作类型变化
 */
watch(() => props.defaultOperationType, (newType) => {
  if (newType && !form.operationType) {
    selectOperationType(newType)
  } else if (!newType && form.operationType) {
    // 当 defaultOperationType 被清空时，也清空当前选择
    form.operationType = null
  }
}, { immediate: true })

/**
 * 操作类型变化时重置相关字段
 */
watch(
    () => form.operationType,
    (newType) => {
      estimateCount.value = null
      result.value = null

      // 如果切换到不需要标签的操作类型，清空标签相关状态
      const newTypeNeedsTags =
          newType === 'UPDATE_TAG_BY_BATCH' ||
          newType === 'UPDATE_TAG_BY_IMEI'

      if (!newTypeNeedsTags) {
        form.tags = {}
        tagsValidationErrors.value = []
      }
    }
)

// 对话框关闭时重置表单
watch(dialogVisible, (val) => {
  if (!val) {
    resetForm()
  }
})
</script>

<template>
  <el-dialog
      v-model="dialogVisible"
      :title="t('device.batchOperation')"
      width="760px"
      :close-on-click-modal="false"
      class="batch-operation-dialog-wrapper"
  >
    <!-- 步骤指示器 -->
    <div class="steps-container">
      <div class="step-item" :class="{ active: currentStep >= 1, completed: currentStep > 1 }">
        <div class="step-number">1</div>
        <div class="step-label">{{ t('device.selectOperationType') }}</div>
      </div>
      <div class="step-line" :class="{ active: currentStep > 1 }"></div>
      <div class="step-item" :class="{ active: currentStep >= 2, completed: currentStep > 2 }">
        <div class="step-number">2</div>
        <div class="step-label">{{ t('device.setOperationParams') }}</div>
      </div>
      <div class="step-line" :class="{ active: currentStep > 2 }"></div>
      <div class="step-item" :class="{ active: currentStep >= 3, completed: currentStep > 3 }">
        <div class="step-number">3</div>
        <div class="step-label">{{ t('device.estimateAffected') }}</div>
      </div>
      <div class="step-line" :class="{ active: currentStep > 3 }"></div>
      <div class="step-item" :class="{ active: currentStep >= 4 }">
        <div class="step-number">4</div>
        <div class="step-label">{{ t('device.batchOperationResult') }}</div>
      </div>
    </div>

    <div class="batch-operation-dialog">
      <!-- 步骤1：选择操作类型 -->
      <div class="operation-type-section">
        <div class="section-header">
          <h3 class="section-title">{{ t('device.selectOperationType') }}</h3>
          <p class="section-desc">请选择要执行的批量操作类型</p>
        </div>
        <div class="operation-type-grid">
          <div
              v-for="op in operationTypes"
              :key="op.value"
              class="operation-type-card"
              :class="{ selected: form.operationType === op.value }"
              @click="selectOperationType(op.value)"
          >
            <div class="card-icon" :style="{ backgroundColor: op.color + '20', color: op.color }">
              <el-icon :size="24">
                <component :is="op.icon"/>
              </el-icon>
            </div>
            <div class="card-content">
              <div class="card-title">{{ t(op.label) }}</div>
              <div class="card-desc">{{ op.description }}</div>
            </div>
            <div v-if="form.operationType === op.value" class="card-check">
              <el-icon>
                <CircleCheck/>
              </el-icon>
            </div>
          </div>
        </div>
      </div>

      <!-- 步骤2：设置参数 -->
      <Transition name="fade-slide">
        <div v-if="form.operationType" class="operation-params-section">
          <div class="section-header">
            <h3 class="section-title">{{ t('device.setOperationParams') }}</h3>
            <p class="section-desc">配置操作参数</p>
          </div>

          <div class="params-container">
            <!-- 产品选择器（按批次操作时需要） -->
            <div v-if="needProductId" class="param-item">
              <label class="param-label">
                <el-icon>
                  <component :is="Box"></component>
                </el-icon>
                {{ t('device.productId') }}
              </label>
              <el-select
                  v-model="form.productId"
                  filterable
                  remote
                  reserve-keyword
                  :remote-method="handleProductSearch"
                  :loading="productSearchLoading"
                  :placeholder="t('device.productId')"
                  class="param-input"
                  @change="handleProductChange"
              >
                <el-option
                    v-for="product in productSearchOptions"
                    :key="product.id"
                    :label="product.name"
                    :value="product.id"
                />
              </el-select>
            </div>

            <!-- 批次选择器 -->
            <div v-if="needBatchId" class="param-item">
              <label class="param-label">
                <el-icon>
                  <FolderOpened/>
                </el-icon>
                {{ t('device.selectBatch') }}
              </label>
              <el-select
                  v-model="form.batchId"
                  filterable
                  remote
                  reserve-keyword
                  :remote-method="handleBatchSearch"
                  :loading="batchSearchLoading"
                  :placeholder="t('device.selectBatch')"
                  class="param-input"
              >
                <el-option
                    v-for="batch in batchSearchOptions"
                    :key="batch.id"
                    :label="batch.batchName"
                    :value="batch.id"
                />
              </el-select>
            </div>

            <!-- IMEI输入 -->
            <div v-if="needImeis" class="param-item">
              <label class="param-label">
                <el-icon>
                  <Document/>
                </el-icon>
                {{ t('device.inputImeis') }}
              </label>
              <ImeiInputPanel
                  ref="imeiInputRef"
                  v-model="form.imeisText"
                  mode="both"
                  :max-count="10000"
                  :disabled="executing"
                  :placeholder="t('device.imeisPlaceholder')"
                  class="param-input"
              />
            </div>

            <!-- 标签编辑器 -->
            <div v-if="needTags" class="param-item">
              <label class="param-label">
                <el-icon>
                  <Edit/>
                </el-icon>
                {{ t('device.tags') }}
              </label>
              <JsonFieldEditor
                  v-model="form.tags"
                  dict-type-code="json_schema.device_tags"
                  class="param-input json-editor-wrapper"
                  mode="form"
                  :allow-mode-switch="true"
                  :disabled="executing"
                  @validation-change="handleTagsValidationChange"
              />
            </div>

            <!-- 新批次选择器 -->
            <div v-if="needNewBatchId" class="param-item">
              <label class="param-label">
                <el-icon>
                  <FolderOpened/>
                </el-icon>
                {{ t('device.selectNewBatch') }}
              </label>
              <el-select
                  v-model="form.newBatchId"
                  filterable
                  remote
                  reserve-keyword
                  :remote-method="handleNewBatchSearch"
                  :loading="newBatchSearchLoading"
                  :placeholder="t('device.selectNewBatch')"
                  class="param-input"
              >
                <el-option
                    v-for="batch in newBatchSearchOptions"
                    :key="batch.id"
                    :label="batch.batchName"
                    :value="batch.id"
                />
              </el-select>
            </div>

            <!-- 查询条件（已禁用） -->
            <!-- <div v-if="needQuery" class="param-item-group">
              <div class="param-item">
                <label class="param-label">
                  <el-icon><Box /></el-icon>
                  {{ t('device.productId') }}
                </label>
                <el-select
                  v-model="form.productId"
                  filterable
                  remote
                  reserve-keyword
                  :remote-method="handleProductSearch"
                  :loading="productSearchLoading"
                  :placeholder="t('device.productId')"
                  clearable
                  class="param-input"
                >
                  <el-option
                    v-for="product in productSearchOptions"
                    :key="product.id"
                    :label="product.name"
                    :value="product.id"
                  />
                </el-select>
              </div>

              <div class="param-item">
                <label class="param-label">
                  <el-icon><Search /></el-icon>
                  {{ t('device.imei') }}
                </label>
                <el-input
                  v-model="form.imeiKeyword"
                  :placeholder="t('device.imeiKeywordPlaceholder')"
                  clearable
                  class="param-input"
                />
              </div>

              <div class="param-item">
                <label class="param-label">
                  <el-icon><SuccessFilled /></el-icon>
                  {{ t('device.status') }}
                </label>
                <el-select
                  v-model="form.status"
                  :placeholder="t('device.statusPlaceholder')"
                  clearable
                  class="param-input"
                >
                  <el-option
                    v-for="status in statusOptions"
                    :key="status"
                    :label="t(resolveStatusLabelKey(status))"
                    :value="status"
                  />
                </el-select>
              </div>

              <div class="param-item">
                <label class="param-label">
                  <el-icon><FolderOpened /></el-icon>
                  {{ t('device.importBatch') }}
                </label>
                <el-select
                  v-model="form.importBatchId"
                  filterable
                  remote
                  reserve-keyword
                  :remote-method="handleBatchSearch"
                  :loading="batchSearchLoading"
                  :placeholder="t('device.importBatch')"
                  clearable
                  class="param-input"
                >
                  <el-option
                    v-for="batch in batchSearchOptions"
                    :key="batch.id"
                    :label="batch.batchName"
                    :value="batch.id"
                  />
                </el-select>
              </div>
            </div> -->
          </div>
        </div>
      </Transition>

      <!-- 步骤3：预估影响设备数 -->
      <Transition name="fade-slide">
        <div v-if="form.operationType" class="estimate-section">
          <div class="estimate-card">
            <div class="estimate-info">
              <div class="estimate-icon">
                <el-icon :size="32">
                  <TrendCharts/>
                </el-icon>
              </div>
              <div class="estimate-content">
                <div class="estimate-title">{{ t('device.estimateAffected') }}</div>
                <div v-if="estimateCount !== null" class="estimate-count">
                  <span class="count-number">{{ estimateCount }}</span>
                  <span class="count-label">{{ t('device.totalCount') }}</span>
                </div>
                <div v-else class="estimate-placeholder">点击右侧按钮预估影响设备数</div>
              </div>
            </div>
            <el-button
                type="primary"
                size="large"
                :loading="estimating"
                @click="handleEstimate"
            >
              <el-icon v-if="!estimating">
                <TrendCharts/>
              </el-icon>
              {{ estimating ? '计算中...' : '开始预估' }}
            </el-button>
          </div>
        </div>
      </Transition>

      <!-- 步骤4：执行结果 -->
      <Transition name="fade-slide">
        <div v-if="result" class="result-section">
          <div class="result-card"
               :class="result.failedCount === 0 ? 'success' : result.successCount > 0 ? 'warning' : 'error'">
            <div class="result-icon">
              <el-icon v-if="result.failedCount === 0" :size="48">
                <SuccessFilled/>
              </el-icon>
              <el-icon v-else-if="result.successCount > 0" :size="48">
                <WarningFilled/>
              </el-icon>
              <el-icon v-else :size="48">
                <CircleCloseFilled/>
              </el-icon>
            </div>
            <div class="result-content">
              <div class="result-title">
                {{
                  result.failedCount === 0 ? t('device.batchOperationSuccess') : result.successCount > 0 ? t('device.batchOperationPartial') : t('device.batchOperationFailed')
                }}
              </div>
              <div class="result-stats">
                <div class="stat-item">
                  <span class="stat-label">{{ t('device.totalCount') }}</span>
                  <span class="stat-value">{{ result.totalCount }}</span>
                </div>
                <div class="stat-divider"></div>
                <div class="stat-item success">
                  <span class="stat-label">{{ t('device.successCount') }}</span>
                  <span class="stat-value">{{ result.successCount }}</span>
                </div>
                <div v-if="result.failedCount > 0" class="stat-divider"></div>
                <div v-if="result.failedCount > 0" class="stat-item error">
                  <span class="stat-label">{{ t('device.failedCount') }}</span>
                  <span class="stat-value">{{ result.failedCount }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false" size="large">
        {{ t('common.cancel') }}
      </el-button>
      <el-button
          type="primary"
          :loading="executing"
          :disabled="!form.operationType"
          size="large"
          @click="handleExecute"
      >
        <el-icon v-if="!executing">
          <Check/>
        </el-icon>
        {{ executing ? t('device.executing') : t('device.execute') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
/* 步骤指示器 */
.steps-container {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 0;
  margin-bottom: 20px;
}

.step-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.step-number {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--el-fill-color-light);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  transition: all 0.3s ease;
}

.step-item.active .step-number {
  background: var(--el-color-primary);
  color: #fff;
  box-shadow: 0 0 0 4px var(--el-color-primary-light-9);
}

.step-item.completed .step-number {
  background: var(--el-color-success);
  color: #fff;
}

.step-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  transition: color 0.3s ease;
}

.step-item.active .step-label {
  color: var(--el-color-primary);
  font-weight: 500;
}

.step-line {
  flex: 1;
  height: 2px;
  background: var(--el-border-color);
  margin: 0 8px;
  margin-bottom: 28px;
  transition: background 0.3s ease;
}

.step-line.active {
  background: var(--el-color-primary);
}

/* 对话框内容 */
.batch-operation-dialog {
  max-height: 520px;
  overflow-y: auto;
  padding-right: 8px;
}

/* 滚动条美化 */
.batch-operation-dialog::-webkit-scrollbar {
  width: 6px;
}

.batch-operation-dialog::-webkit-scrollbar-track {
  background: var(--el-fill-color-lighter);
  border-radius: 3px;
}

.batch-operation-dialog::-webkit-scrollbar-thumb {
  background: var(--el-border-color-darker);
  border-radius: 3px;
}

.batch-operation-dialog::-webkit-scrollbar-thumb:hover {
  background: var(--el-border-color-dark);
}

/* 区域标题 */
.section-header {
  margin-bottom: 20px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin: 0 0 4px 0;
}

.section-desc {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin: 0;
}

/* 操作类型选择 */
.operation-type-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.operation-type-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: var(--el-fill-color-blank);
  border: 2px solid var(--el-border-color);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.operation-type-card:hover {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.operation-type-card.selected {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  box-shadow: 0 0 0 2px var(--el-color-primary-light-7);
}

.card-icon {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.3s ease;
}

.operation-type-card:hover .card-icon {
  transform: scale(1.1);
}

.card-content {
  flex: 1;
  min-width: 0;
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 4px;
}

.card-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
}

.card-check {
  position: absolute;
  top: 8px;
  right: 8px;
  color: var(--el-color-primary);
  font-size: 20px;
}

/* 参数设置区域 */
.operation-params-section {
  margin-top: 24px;
}

.params-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.param-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.param-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-regular);
}

.param-input {
  width: 100%;
}

.param-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.param-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.param-item-group {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.json-editor-wrapper {
  border-radius: 8px;
  overflow: hidden;
}

/* 预估区域 */
.estimate-section {
  margin-top: 24px;
}

.estimate-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px;
  background: linear-gradient(135deg, var(--el-color-primary-light-9) 0%, var(--el-fill-color-light) 100%);
  border: 1px solid var(--el-color-primary-light-5);
  border-radius: 12px;
  gap: 16px;
}

.estimate-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.estimate-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  background: var(--el-color-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.estimate-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.estimate-title {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.estimate-count {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.count-number {
  font-size: 32px;
  font-weight: 700;
  color: var(--el-color-primary);
  line-height: 1;
}

.count-label {
  font-size: 14px;
  color: var(--el-text-color-regular);
}

.estimate-placeholder {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

/* 结果区域 */
.result-section {
  margin-top: 24px;
}

.result-card {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 24px;
  border-radius: 12px;
  border: 1px solid;
}

.result-card.success {
  background: var(--el-color-success-light-9);
  border-color: var(--el-color-success-light-5);
}

.result-card.warning {
  background: var(--el-color-warning-light-9);
  border-color: var(--el-color-warning-light-5);
}

.result-card.error {
  background: var(--el-color-danger-light-9);
  border-color: var(--el-color-danger-light-5);
}

.result-icon {
  flex-shrink: 0;
}

.result-card.success .result-icon {
  color: var(--el-color-success);
}

.result-card.warning .result-icon {
  color: var(--el-color-warning);
}

.result-card.error .result-icon {
  color: var(--el-color-danger);
}

.result-content {
  flex: 1;
}

.result-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 12px;
}

.result-stats {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.stat-value {
  font-size: 20px;
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
  height: 32px;
  background: var(--el-border-color);
}

/* 过渡动画 */
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

<style>
.batch-operation-dialog-wrapper .el-dialog__body {
  padding: 20px 24px;
}
</style>
