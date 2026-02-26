<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { deviceStatusTypeMap, resolveStatusLabelKey, resolveStatusType } from '@/constants/status'
import { useUserStore } from '@/stores/user'
import JsonFieldEditor from '@/components/json-field/JsonFieldEditor.vue'
import {
  createDevice,
  deleteDevice,
  pageDevices,
  updateDevice,
  type DeviceItem,
  type DeviceStatus,
} from '@/api/device'
import { searchProducts, type ProductItem } from '@/api/product'
import { getFirmwareVersionsByProduct, type FirmwareVersionItem } from '@/api/firmware'

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
})

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
const tagsValidationErrors = ref<string[]>([])

const form = reactive({
  imei: '',
  productId: undefined as number | undefined,
  currentVersionId: undefined as number | undefined,
  status: 'OFFLINE' as DeviceStatus,
  tags: '',
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

const tagsValidator = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  // 如果有 JsonFieldEditor 的校验错误，优先显示
  if (tagsValidationErrors.value.length > 0) {
    callback(new Error(tagsValidationErrors.value[0]))
    return
  }

  // 保留原有兜底校验逻辑
  if (!value || !value.trim()) {
    callback()
    return
  }

  try {
    const parsed = JSON.parse(value.trim())

    if (Array.isArray(parsed)) {
      callback(new Error(t('device.tagsMustBeObject')))
      return
    }

    if (parsed === null || typeof parsed !== 'object') {
      callback(new Error(t('device.tagsMustBeObject')))
      return
    }

    callback()
  } catch (error) {
    if (error instanceof Error) {
      callback(error)
    } else {
      callback(new Error(t('device.tagsJsonInvalid')))
    }
  }
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

const fetchList = async () => {
  loading.value = true
  try {
    const result = await pageDevices(query)
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
  form.tags = ''
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
  form.currentVersionId = row.currentVersionId
  form.status = row.status
  form.tags = row.tags || ''
  tagsValidationErrors.value = []

  // 将当前产品添加到搜索选项中，确保编辑时能正确显示产品名称
  if (row.productId && row.productName) {
    const exists = productSearchOptions.value.some(p => p.id === row.productId)
    if (!exists) {
      productSearchOptions.value.unshift({
        id: row.productId,
        name: row.productName,
      })
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
      currentVersionId: form.currentVersionId || undefined,
      status: form.status,
      tags: form.tags?.trim() || undefined,
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

const handleFilterChange = () => {
  query.page = 1
  void fetchList()
}

const resetSearch = () => {
  query.page = 1
  query.productId = undefined
  query.imei = ''
  query.status = undefined
  void fetchList()
}

const getVersionName = (row: DeviceItem) => {
  if (row.versionName) return row.versionName
  if (!row.currentVersionId) return '-'
  const version = firmwareOptions.value.find(item => item.id === row.currentVersionId)
  return version?.version || `${row.currentVersionId}`
}

onMounted(() => {
  void fetchList()
})
</script>

<template>
  <PageCardTableShell :title="t('device.title')">
    <template #actions>
      <div class="flex items-center gap-2">
        <el-select
          v-model="query.productId"
          :placeholder="t('device.productId')"
          clearable
          filterable
          remote
          reserve-keyword
          :remote-method="handleProductSearch"
          style="width: 180px"
          @change="handleFilterChange"
        >
          <el-option
            v-for="product in productSearchOptions"
            :key="product.id"
            :label="product.name"
            :value="product.id"
          />
        </el-select>
        <el-input
          v-model="query.imei"
          :placeholder="t('device.imei')"
          clearable
          style="width: 180px"
          @keyup.enter="handleSearch"
        />
        <el-select
          v-model="query.status"
          :placeholder="t('device.statusPlaceholder')"
          clearable
          style="width: 180px"
          @change="handleFilterChange"
        >
          <el-option
            v-for="status in statusOptions"
            :key="status"
            :label="t(resolveStatusLabelKey(status))"
            :value="status"
          />
        </el-select>
        <el-button @click="handleSearch">
          {{ t('common.search') }}
        </el-button>
        <el-button @click="resetSearch">
          {{ t('common.refresh') }}
        </el-button>
        <el-button
          v-if="canCreate"
          type="primary"
          @click="openCreateDialog"
        >
          {{ t('device.add') }}
        </el-button>
      </div>
    </template>

    <el-table
      v-loading="loading"
      :data="list"
      stripe
    >
      <el-table-column
        prop="imei"
        :label="t('device.imei')"
        min-width="160"
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
        prop="versionName"
        :label="t('device.currentVersionId')"
        min-width="180"
      >
        <template #default="{ row }">
          {{ getVersionName(row) }}
        </template>
      </el-table-column>
      <el-table-column
        prop="status"
        :label="t('device.status')"
        width="120"
      >
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="resolveStatusType(deviceStatusTypeMap, row.status)"
          >
            {{ t(resolveStatusLabelKey(row.status)) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="lastSeenAt"
        :label="t('device.lastSeenAt')"
        width="180"
      />
      <el-table-column
        prop="tags"
        :label="t('device.tags')"
        min-width="220"
        show-overflow-tooltip
      />

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
</template>
