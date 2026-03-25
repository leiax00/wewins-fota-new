<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  Delete,
  Document,
  Edit,
  Filter,
  FolderOpened,
  MoreFilled,
  Plus,
  Refresh,
  Upload,
} from '@element-plus/icons-vue'
import { deviceStatusTypeMap, resolveStatusLabelKey, resolveStatusType } from '@/constants/status'
import { useUserStore } from '@/stores/user'
import JsonFieldEditor from '@/components/json-field/JsonFieldEditor.vue'
import { formatDateTime } from '@/utils/date'
import { trimFormValues } from '@/utils/form'
import {
  createDevice,
  deleteDevice,
  pageDevices,
  updateDevice,
  type BatchOperationType,
  type DeviceItem,
  type DeviceStatus,
  type DeviceVersionParts,
} from '@/api/device'
import { searchProducts, type ProductItem } from '@/api/product'
import { pageBatches, type DeviceImportBatchItem } from '@/api/deviceImportBatch'
import { getFirmwareVersionsByProduct, type FirmwareVersionItem } from '@/api/firmware'
import DeviceImportDialog from './DeviceImportDialog.vue'
import DeviceBatchOperationDialog from './DeviceBatchOperationDialog.vue'

const { t } = useI18n()
const userStore = useUserStore()

const list = ref<DeviceItem[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({
  page: 1,
  size: 20,
  productId: undefined as number | undefined,
  imei: '',
  status: undefined as DeviceStatus | undefined,
  importBatchId: undefined as number | undefined,
})

// 导入对话框
const importDialogVisible = ref(false)

// 批量操作对话框
const batchOperationDialogVisible = ref(false)
const batchOperationDefaultType = ref<BatchOperationType | null>(null)

// 高级筛选抽屉状态
const advancedFilterVisible = ref(false)

// 批次搜索选项
const batchSearchOptions = ref<DeviceImportBatchItem[]>([])
const batchSearchLoading = ref(false)

let batchSearchTimer: number | null = null

// 产品远程搜索选项
const productSearchOptions = ref<ProductItem[]>([])
const productSearchLoading = ref(false)

let productSearchTimer: number | null = null

const firmwareOptions = ref<FirmwareVersionItem[]>([])
const firmwareLoading = ref(false)

const canCreate = computed(() => userStore.hasPermission('fota:device:import'))
const canShowActions = computed(() =>
  userStore.hasPermission('fota:device:update')
)

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref()
const tableRef = ref()
const tagsValidationErrors = ref<string[]>([])

const form = reactive({
  imei: '',
  productId: undefined as number | undefined,
  currentVersionId: undefined as number | undefined,
  status: 'OFFLINE' as DeviceStatus,
  tags: {} as Record<string, unknown>,
})

const statusOptions: DeviceStatus[] = ['ONLINE', 'OFFLINE', 'LOST']

const imeiValidator = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!value) {
    callback(new Error(t('device.imeiRequired')))
    return
  }
  if (!/^\d{15}$/.test(value.trim())) {
    callback(new Error(t('device.imeiInvalid')))
    return
  }
  callback()
}

const tagsValidator = (_rule: unknown, value: Record<string, unknown>, callback: (error?: Error) => void) => {
  // 如果有 JsonFieldEditor 的校验错误，优先显示
  if (tagsValidationErrors.value.length > 0) {
    callback(new Error(tagsValidationErrors.value[0]))
    return
  }

  // 空对象是有效的
  if (!value || Object.keys(value).length === 0) {
    callback()
    return
  }

  // value 已经是对象类型，直接通过
  callback()
}

/**
 * 处理 JsonFieldEditor 的校验变化
 */
const handleTagsValidationChange = (errors: string[]) => {
  tagsValidationErrors.value = errors
  // 触发表单重新校验
  formRef.value?.validateField('tags').catch(() => {
    // 忽略校验错误
  })
}

const formRules = {
  imei: [{ required: true, validator: imeiValidator, trigger: 'blur' }],
  productId: [{ required: true, message: t('device.productIdRequired'), trigger: 'change' }],
  status: [{ required: true, message: t('device.statusRequired'), trigger: 'change' }],
  tags: [{ validator: tagsValidator, trigger: 'blur' }],
}

/**
 * 产品远程搜索（按产品名称和型号）
 * 只在用户输入内容后触发，获得焦点时不触发
 */
const handleProductSearch = async (keyword: string) => {
  // 如果输入为空，不执行搜索（避免获得焦点时触发不必要的请求）
  const trimmedKeyword = (keyword || '').trim()
  if (!trimmedKeyword) {
    return
  }

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
 * 批次远程搜索（按批次名称）
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
 * 打开导入对话框
 */
const openImportDialog = () => {
  importDialogVisible.value = true
}

/**
 * 导入成功回调
 */
const handleImportSuccess = () => {
  importDialogVisible.value = false
  void fetchList()
}

/**
 * 批量操作成功回调
 */
const handleBatchOperationSuccess = () => {
  batchOperationDialogVisible.value = false
  void fetchList()
}

const fetchList = async () => {
  loading.value = true
  try {
    const result = await pageDevices(trimFormValues(query))
    list.value = result.records || []
    total.value = result.total || 0
  } finally {
    loading.value = false
  }
}

const fetchFirmwareByProduct = async (productId?: number) => {
  if (!productId) {
    firmwareOptions.value = []
    form.currentVersionId = undefined
    return
  }
  firmwareLoading.value = true
  try {
    firmwareOptions.value = await getFirmwareVersionsByProduct(productId)
  } finally {
    firmwareLoading.value = false
  }
}

const openCreateDialog = () => {
  dialogMode.value = 'create'
  editingId.value = null
  form.imei = ''
  form.productId = undefined
  form.currentVersionId = undefined
  form.status = 'OFFLINE'
  form.tags = {}
  tagsValidationErrors.value = []
  firmwareOptions.value = []
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

const openEditDialog = async (row: DeviceItem) => {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.imei = row.imei
  form.productId = row.productId
  form.currentVersionId = getPrimaryVersionId(row.versionParts)
  form.status = row.status
  form.tags = row.tags || {}
  tagsValidationErrors.value = []

  // 将当前产品添加到搜索选项中，确保编辑时能正确显示产品名称
  if (row.productId && row.productName) {
    const exists = productSearchOptions.value.some(p => p.id === row.productId)
    if (!exists) {
      productSearchOptions.value.unshift({
        id: row.productId,
        name: row.productName,
      } as ProductItem)
    }
  }

  await fetchFirmwareByProduct(row.productId)
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

const onFormProductChange = async (productId?: number) => {
  form.currentVersionId = undefined
  await fetchFirmwareByProduct(productId)
}

const submitForm = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    const payload = {
      imei: form.imei.trim(),
      productId: form.productId!,
      versionParts: form.currentVersionId
        ? {
            primaryPart: 'main',
            parts: {
              main: {
                versionId: form.currentVersionId,
              },
            },
          }
        : undefined,
      status: form.status,
      tags: Object.keys(form.tags).length > 0 ? (form.tags as Record<string, string>) : undefined,
    }

    if (dialogMode.value === 'create') {
      await createDevice(payload)
      ElMessage.success(t('common.createSuccess'))
    } else if (editingId.value) {
      await updateDevice(editingId.value, payload)
      ElMessage.success(t('common.updateSuccess'))
    }

    dialogVisible.value = false
    await fetchList()
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (row: DeviceItem) => {
  await ElMessageBox.confirm(t('common.deleteConfirm'), t('common.tip'), { type: 'warning' })
  await deleteDevice(row.id)
  ElMessage.success(t('common.deleteSuccess'))
  await fetchList()
}

const handleSearch = () => {
  query.page = 1
  void fetchList()
}

// 高级筛选中的 change 事件，不立即搜索
const handleAdvancedFilterChange = () => {
  query.page = 1
}

const resetSearch = () => {
  query.page = 1
  query.productId = undefined
  query.imei = ''
  query.status = undefined
  query.importBatchId = undefined
  void fetchList()
}

/**
 * 高级筛选搜索
 */
const handleAdvancedSearch = () => {
  handleSearch()
  // 搜索后不关闭弹窗，让用户可以继续筛选
}

/**
 * 重置高级筛选
 */
const resetAdvancedFilter = () => {
  query.productId = undefined
  query.imei = ''
  query.status = undefined
  query.importBatchId = undefined
  query.page = 1
  void fetchList()
}

/**
 * 打开批量操作对话框（指定类型）
 */
const openBatchOperationWithType = (type: BatchOperationType) => {
  batchOperationDefaultType.value = type
  batchOperationDialogVisible.value = true
}

// 监听批量操作对话框关闭，重置默认类型
watch(batchOperationDialogVisible, (val) => {
  if (!val) {
    batchOperationDefaultType.value = null
  }
})

const getPrimaryPartName = (parts?: DeviceVersionParts) => {
  if (!parts) return 'main'
  return parts.primaryPart || 'main'
}

const getPrimaryVersionId = (parts?: DeviceVersionParts) => {
  if (!parts?.parts) return undefined
  const primaryPart = getPrimaryPartName(parts)
  return parts.parts[primaryPart]?.versionId
}

const formatVersionParts = (parts?: DeviceVersionParts) => {
  if (!parts?.parts || Object.keys(parts.parts).length === 0) return []
  const primaryPart = getPrimaryPartName(parts)
  return Object.entries(parts.parts).map(([partName, info]) => {
    let version = info.version || '-'
    if (info.internalVersion) {
      version += `(${info.internalVersion})`
    }
    return {
      partName,
      isPrimary: partName === primaryPart,
      versionId: info.versionId,
      version: version,
      updatedAt: info.updatedAt,
    };
  })
}

const getDeviceEnv = (row: DeviceItem) => {
  const env = row.tags?.env
  if (typeof env !== 'string') {
    return ''
  }
  return env.trim().toLowerCase()
}

const isTestDevice = (row: DeviceItem) => {
  const env = getDeviceEnv(row)
  return env === 'test' || env === 'dev'
}

/**
 * 点击行展开/收起详情
 */
const handleRowClick = (row: DeviceItem) => {
  tableRef.value?.toggleRowExpansion(row)
}

onMounted(() => {
  void fetchList()
})
</script>

<template>
  <PageCardTableShell :title="t('device.title')">
    <template #actions>
      <div class="flex items-center gap-2">
        <!-- 刷新按钮 -->
        <el-button :icon="Refresh" @click="resetSearch">
          {{ t('common.refresh') }}
        </el-button>

        <!-- 高级筛选弹出层 -->
        <el-popover
          v-model:visible="advancedFilterVisible"
          :title="t('device.advancedFilter')"
          placement="bottom-end"
          :width="360"
          trigger="click"
          :show-arrow="true"
          popper-class="advanced-filter-popper"
          :popper-options="{
            modifiers: [
              {
                name: 'eventListeners',
                enabled: true,
              },
            ],
          }"
        >
          <template #reference>
            <el-button :icon="Filter">
              {{ t('device.advancedFilter') }}
            </el-button>
          </template>

          <el-form
            :model="query"
            label-width="70px"
            class="advanced-filter-form"
            @click.stop
            @mousedown.stop
          >
            <el-form-item :label="t('device.productId')">
              <el-select
                v-model="query.productId"
                :placeholder="t('device.productId')"
                clearable
                filterable
                remote
                reserve-keyword
                :teleported="false"
                :remote-method="handleProductSearch"
                :loading="productSearchLoading"
                style="width: 100%"
                @change="handleAdvancedFilterChange"
                popper-class="advanced-filter-select-dropdown"
              >
                <el-option
                  v-for="product in productSearchOptions"
                  :key="product.id"
                  :label="product.name"
                  :value="product.id"
                />
              </el-select>
            </el-form-item>

            <el-form-item :label="t('device.imei')">
              <el-input
                v-model="query.imei"
                :placeholder="t('device.imei')"
                clearable
                @keyup.enter="handleAdvancedSearch"
              />
            </el-form-item>

            <el-form-item :label="t('device.status')">
              <el-select
                v-model="query.status"
                :placeholder="t('device.statusPlaceholder')"
                :teleported="false"
                clearable
                style="width: 100%"
                @change="handleAdvancedFilterChange"
                popper-class="advanced-filter-select-dropdown"
              >
                <el-option
                  v-for="status in statusOptions"
                  :key="status"
                  :label="t(resolveStatusLabelKey(status))"
                  :value="status"
                />
              </el-select>
            </el-form-item>

            <el-form-item :label="t('device.importBatch')">
              <el-select
                v-model="query.importBatchId"
                :placeholder="t('device.importBatch')"
                :teleported="false"
                clearable
                filterable
                remote
                reserve-keyword
                :remote-method="handleBatchSearch"
                :loading="batchSearchLoading"
                style="width: 100%"
                @change="handleAdvancedFilterChange"
                popper-class="advanced-filter-select-dropdown"
              >
                <el-option
                  v-for="batch in batchSearchOptions"
                  :key="batch.id"
                  :label="batch.batchName"
                  :value="batch.id"
                />
              </el-select>
            </el-form-item>

            <el-form-item class="mb-0">
              <div class="flex justify-end gap-2 w-full">
                <el-button size="small" @click="resetAdvancedFilter">
                  {{ t('common.reset') }}
                </el-button>
                <el-button size="small" type="primary" @click="handleAdvancedSearch">
                  {{ t('common.search') }}
                </el-button>
              </div>
            </el-form-item>
          </el-form>
        </el-popover>

        <!-- 更多操作下拉菜单 -->
        <el-dropdown trigger="click">
          <el-button :icon="MoreFilled">
            {{ t('common.moreActions') }}
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <!-- 新增和导入 -->
              <el-dropdown-item
                v-if="canCreate"
                :icon="Plus"
                @click="openCreateDialog"
              >
                {{ t('device.add') }}
              </el-dropdown-item>
              <el-dropdown-item
                v-if="canCreate"
                :icon="Upload"
                @click="openImportDialog"
              >
                {{ t('device.import') }}
              </el-dropdown-item>

              <!-- 批量操作组 -->
              <template v-if="canShowActions">
                <el-dropdown-item
                  :icon="Delete"
                  divided
                  @click="openBatchOperationWithType('DELETE_BY_BATCH')"
                >
                  {{ t('device.opDeleteByBatch') }}
                </el-dropdown-item>
                <el-dropdown-item
                  :icon="Edit"
                  @click="openBatchOperationWithType('UPDATE_TAG_BY_BATCH')"
                >
                  {{ t('device.opUpdateTagByBatch') }}
                </el-dropdown-item>
                <el-dropdown-item
                  :icon="Document"
                  @click="openBatchOperationWithType('UPDATE_TAG_BY_IMEI')"
                >
                  {{ t('device.opUpdateTagByImei') }}
                </el-dropdown-item>
                <el-dropdown-item
                  :icon="FolderOpened"
                  @click="openBatchOperationWithType('UPDATE_BATCH_BY_IMEI')"
                >
                  {{ t('device.opUpdateBatchByImei') }}
                </el-dropdown-item>
              </template>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </template>

    <el-table
      ref="tableRef"
      v-loading="loading"
      :data="list"
      stripe
      row-key="id"
      @row-click="handleRowClick"
    >
      <el-table-column type="expand" width="56">
        <template #default="{ row }">
          <div class="device-expand-content">
            <div class="expand-grid">
              <div class="expand-left">
                <div class="expand-section compact">
                  <div class="compact-section-title">{{ t('device.basicInfo') }}</div>
                  <div class="compact-section-body">
                    <div class="compact-info-item">
                      <span class="compact-label">{{ t('device.deviceId') }}</span>
                      <span class="compact-value">{{ row.id }}</span>
                    </div>
                    <div class="compact-info-item">
                      <span class="compact-label">{{ t('device.productId') }}</span>
                      <span class="compact-value">{{ row.productName || '-' }}</span>
                    </div>
                    <div class="compact-info-item">
                      <span class="compact-label">{{ t('device.importBatch') }}</span>
                      <el-tag v-if="row.importBatchName" size="small" type="info" effect="plain">
                        {{ row.importBatchName }}
                      </el-tag>
                      <span v-else class="compact-value">-</span>
                    </div>
                    <div class="compact-info-item">
                      <span class="compact-label">{{ t('device.status') }}</span>
                      <div class="status-cell">
                        <el-tag size="small" :type="resolveStatusType(deviceStatusTypeMap, row.status)">
                          {{ t(resolveStatusLabelKey(row.status)) }}
                        </el-tag>
                        <el-tag v-if="isTestDevice(row)" size="small" type="warning" effect="plain">
                          {{ t('device.testFlag') }}
                        </el-tag>
                      </div>
                    </div>
                  </div>
                </div>

                <div class="expand-section compact">
                  <div class="compact-section-title">{{ t('device.timeInfo') }}</div>
                  <div class="compact-section-body">
                    <div class="compact-info-item">
                      <span class="compact-label">{{ t('device.firstSeenAt') }}</span>
                      <span class="compact-value">{{ formatDateTime(row.firstSeenAt) }}</span>
                    </div>
                    <div class="compact-info-item">
                      <span class="compact-label">{{ t('device.lastSeenAt') }}</span>
                      <span class="compact-value">{{ formatDateTime(row.lastSeenAt) }}</span>
                    </div>
                    <div class="compact-info-item">
                      <span class="compact-label">{{ t('common.createTime') }}</span>
                      <span class="compact-value">{{ formatDateTime(row.createdAt) }}</span>
                    </div>
                    <div class="compact-info-item">
                      <span class="compact-label">{{ t('common.updateTime') }}</span>
                      <span class="compact-value">{{ formatDateTime(row.updatedAt) }}</span>
                    </div>
                  </div>
                </div>
              </div>

              <div class="expand-right">
                <div class="expand-section compact">
                  <div class="compact-section-title">{{ t('device.currentVersionParts') }}</div>
                  <div class="compact-section-body">
                    <div class="version-tags compact">
                      <el-tag
                        v-for="part in formatVersionParts(row.versionParts)"
                        :key="`current-${row.id}-${part.partName}`"
                        size="small"
                        :type="part.isPrimary ? 'primary' : 'info'"
                        effect="plain"
                        class="version-tag"
                      >
                        {{ part.partName }}: {{ part.version }}
                      </el-tag>
                      <span v-if="formatVersionParts(row.versionParts).length === 0" class="compact-value">-</span>
                    </div>
                  </div>
                </div>

                <div class="expand-section compact">
                  <div class="compact-section-title">{{ t('device.initialFirmwareVersion') }}</div>
                  <div class="compact-section-body">
                    <div class="version-tags compact">
                      <el-tag
                        v-for="part in formatVersionParts(row.initialVersionParts)"
                        :key="`initial-${row.id}-${part.partName}`"
                        size="small"
                        type="warning"
                        effect="plain"
                        class="version-tag"
                      >
                        {{ part.partName }}: {{ part.version }}
                      </el-tag>
                      <span v-if="formatVersionParts(row.initialVersionParts).length === 0" class="compact-value">-</span>
                    </div>
                  </div>
                </div>

                <div class="expand-section compact">
                  <div class="compact-section-title">{{ t('device.tags') }}</div>
                  <div class="compact-section-body">
                    <div v-if="row.tags && Object.keys(row.tags).length" class="target-tag-pairs compact">
                      <div
                        v-for="([key, value], idx) in Object.entries(row.tags).slice(0, 8)"
                        :key="`tag-${row.id}-${idx}`"
                        class="tag-pair-item"
                      >
                        <el-tag size="small" type="warning" effect="plain" class="tag-pair-key">
                          {{ key }}
                        </el-tag>
                        <span class="tag-pair-separator">:</span>
                        <span class="tag-pair-value">{{ value }}</span>
                      </div>
                    </div>
                    <span v-else class="compact-value">-</span>
                  </div>
                </div>
              </div>
            </div>

            <div class="audit-info">
              <div class="audit-item">
                <span class="audit-label">{{ t('common.createdBy') }}</span>
                <span class="audit-value">{{ row.createdBy ?? '-' }}</span>
              </div>
              <div class="audit-item">
                <span class="audit-label">{{ t('common.updatedBy') }}</span>
                <span class="audit-value">{{ row.updatedBy ?? '-' }}</span>
              </div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column
        prop="imei"
        :label="t('device.imei')"
        min-width="180"
      />
      <el-table-column
        prop="productId"
        :label="t('device.productId')"
        min-width="180"
      >
        <template #default="{ row }">
          {{ row.productName }}
        </template>
      </el-table-column>
      <el-table-column
        :label="t('device.importBatch')"
        min-width="150"
      >
        <template #default="{ row }">
          <el-tag
            v-if="row.importBatchName"
            size="small"
            type="info"
          >
            {{ row.importBatchName }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('device.firstSeenAt')"
        width="170"
      >
        <template #default="{ row }">
          {{ formatDateTime(row.firstSeenAt) }}
        </template>
      </el-table-column>
      <el-table-column
        :label="t('device.lastSeenAt')"
        width="170"
      >
        <template #default="{ row }">
          {{ formatDateTime(row.lastSeenAt) }}
        </template>
      </el-table-column>
      <el-table-column
        prop="status"
        :label="t('device.status')"
        width="160"
      >
        <template #default="{ row }">
          <div class="status-cell">
            <el-tag
              size="small"
              :type="resolveStatusType(deviceStatusTypeMap, row.status)"
            >
              {{ t(resolveStatusLabelKey(row.status)) }}
            </el-tag>
            <el-tag v-if="isTestDevice(row)" size="small" type="warning" effect="plain">
              {{ t('device.testFlag') }}
            </el-tag>
          </div>
        </template>
      </el-table-column>

      <el-table-column
        v-if="canShowActions"
        :label="t('common.actions')"
        width="150"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            v-if="userStore.hasPermission('fota:device:update')"
            link
            class="ui-action-primary"
            @click="openEditDialog(row)"
          >
            {{ t('common.edit') }}
          </el-button>
          <el-button
            v-if="userStore.hasPermission('fota:device:update')"
            link
            class="ui-action-danger"
            @click="handleDelete(row)"
          >
            {{ t('common.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="mt-4 flex justify-end">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        @current-change="fetchList"
        @size-change="fetchList"
      />
    </div>
  </PageCardTableShell>

  <el-dialog
    v-model="dialogVisible"
    :title="dialogMode === 'create' ? t('device.add') : t('common.edit')"
    width="680px"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="formRules"
      label-width="130px"
    >
      <el-form-item
        prop="imei"
        :label="t('device.imei')"
      >
        <el-input
          v-model="form.imei"
          maxlength="15"
          :disabled="dialogMode === 'edit'"
        />
      </el-form-item>
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
          style="width: 100%"
          @change="onFormProductChange"
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
        prop="currentVersionId"
        :label="t('device.currentVersionId')"
      >
        <el-select
          v-model="form.currentVersionId"
          :loading="firmwareLoading"
          clearable
          filterable
          :placeholder="t('device.currentVersionIdPlaceholder')"
          style="width: 100%"
        >
          <el-option
            v-for="firmware in firmwareOptions"
            :key="firmware.id"
            :label="firmware.version"
            :value="firmware.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        prop="status"
        :label="t('device.status')"
      >
        <el-select
          v-model="form.status"
          style="width: 100%"
        >
          <el-option
            v-for="status in statusOptions"
            :key="status"
            :label="t(resolveStatusLabelKey(status))"
            :value="status"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        prop="tags"
        :label="t('device.tags')"
      >
        <JsonFieldEditor
          v-model="form.tags"
          dict-type-code="json_schema.device_tags"
          class="w-full"
          mode="form"
          :allow-mode-switch="true"
          :disabled="submitting"
          @validation-change="handleTagsValidationChange"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        :loading="submitting"
        @click="submitForm"
      >
        {{ t('common.save') }}
      </el-button>
    </template>
  </el-dialog>

  <DeviceImportDialog
    v-model="importDialogVisible"
    @success="handleImportSuccess"
  />

  <DeviceBatchOperationDialog
    v-model="batchOperationDialogVisible"
    :default-operation-type="batchOperationDefaultType"
    @success="handleBatchOperationSuccess"
  />
</template>

<style scoped>
.advanced-filter-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.advanced-filter-form :deep(.el-form-item__label) {
  font-size: 13px;
}

.advanced-filter-form :deep(.el-select),
.advanced-filter-form :deep(.el-input) {
  font-size: 13px;
}

/* 确保弹出层内的元素点击不会关闭弹出层 */
.advanced-filter-form,
.advanced-filter-form * {
  pointer-events: auto !important;
}

.device-expand-content {
  padding: 12px 16px;
  background: var(--bg-hover);
}

.dark .device-expand-content {
  background: var(--surface-fill);
}

.expand-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.expand-left,
.expand-right {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.expand-section {
  padding: 10px 12px;
  background: var(--bg-card);
  border-radius: 6px;
  border: 1px solid var(--border-light);
  transition: all 0.2s ease;
}

.expand-section:hover {
  border-color: var(--border-color);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.dark .expand-section:hover {
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.2);
}

.compact-section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.compact-section-body {
  font-size: 12px;
  color: var(--text-regular);
}

.compact-info-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.compact-info-item:last-child {
  margin-bottom: 0;
}

.compact-label {
  font-size: 12px;
  color: var(--text-secondary);
  min-width: 70px;
  text-align: right;
}

.compact-value {
  color: var(--text-primary);
  word-break: break-all;
}

.version-tags.compact {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.version-tag {
  font-size: 11px;
}

.target-tag-pairs.compact {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-pair-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 6px;
  background: #fef3e7;
  border-radius: 4px;
  font-size: 11px;
}

.dark .tag-pair-item {
  background: #3d3a2a;
}

.tag-pair-key {
  font-weight: 600;
}

.tag-pair-separator {
  color: #dcdfe6;
}

.tag-pair-value {
  color: var(--text-regular);
}

.audit-info {
  display: flex;
  gap: 24px;
  padding-top: 8px;
  margin-top: 8px;
  border-top: 1px dashed var(--border-light);
}

.audit-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.audit-label {
  color: #909399;
  font-weight: 500;
}

.audit-value {
  color: var(--text-primary);
  font-weight: 600;
}

.status-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

:deep(.el-table__expanded-cell) {
  padding: 0 !important;
}

/* 行可点击样式 */
:deep(.el-table__row) {
  cursor: pointer;
}

@media (max-width: 768px) {
  .expand-grid {
    grid-template-columns: 1fr;
  }

  .audit-info {
    flex-direction: column;
    gap: 8px;
  }
}
</style>
