<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { policyStatusTypeMap, resolveStatusLabelKey, resolveStatusType } from '@/constants/status'
import { useUserStore } from '@/stores/user'
import {
  createPolicy,
  deletePolicy,
  pagePolicies,
  updatePolicy,
  type PolicyStatus,
  type UpgradePolicyItem,
} from '@/api/policy'
import { searchProducts, type ProductItem } from '@/api/product'
import { getFirmwareVersionsByProduct, type FirmwareVersionItem } from '@/api/firmware'

const { t } = useI18n()
const userStore = useUserStore()

const list = ref<UpgradePolicyItem[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({
  page: 1,
  size: 20,
  productId: undefined as number | undefined,
  name: '',
  status: undefined as PolicyStatus | undefined,
})

const products = ref<ProductItem[]>([])
const productsLoading = ref(false)
const productSearchOptions = ref<ProductItem[]>([])
const productSearchLoading = ref(false)
const firmwareOptions = ref<FirmwareVersionItem[]>([])
const firmwareLoading = ref(false)
const firmwareLabelMap = reactive<Record<number, string>>({})

let productSearchTimer: number | null = null

const canShowActions = computed(() =>
  userStore.hasPermission('fota:policy:update') ||
  userStore.hasPermission('fota:policy:delete')
)

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref()

const form = reactive({
  productId: undefined as number | undefined,
  firmwareVersionId: undefined as number | undefined,
  name: '',
  grayRate: 0,
  priority: 0,
  planTime: '',
  status: 'ACTIVE' as PolicyStatus,
  remark: '',
})

const formRules = {
  productId: [{ required: true, message: t('policy.productIdRequired'), trigger: 'change' }],
  firmwareVersionId: [{ required: true, message: t('policy.firmwareVersionRequired'), trigger: 'change' }],
  name: [{ required: true, message: t('policy.nameRequired'), trigger: 'blur' }],
  grayRate: [
    { required: true, message: t('policy.grayRateRequired'), trigger: 'blur' },
    { type: 'number', min: 0, max: 100, message: t('policy.grayRateRange'), trigger: 'blur' },
  ],
  priority: [{ required: true, message: t('policy.priorityRequired'), trigger: 'blur' }],
  status: [{ required: true, message: t('policy.statusRequired'), trigger: 'change' }],
}

const statusOptions: PolicyStatus[] = ['ACTIVE', 'PAUSED', 'EXPIRED']

const handleProductSearch = async (keyword: string) => {
  if (productSearchTimer !== null) {
    clearTimeout(productSearchTimer)
  }

  productSearchTimer = window.setTimeout(async () => {
    productSearchLoading.value = true
    try {
      const result = await searchProducts(keyword.trim())
      productSearchOptions.value = result.records || []
    } finally {
      productSearchLoading.value = false
    }
  }, 300)
}

const fillFirmwareVersionLabelMap = async (rows: UpgradePolicyItem[]) => {
  const productIds = Array.from(new Set(rows.map(item => item.productId).filter(Boolean)))
  await Promise.all(
    productIds.map(async (productId) => {
      try {
        const versions = await getFirmwareVersionsByProduct(productId)
        versions.forEach((item) => {
          firmwareLabelMap[item.id] = item.version
        })
      } catch {
        // ignore
      }
    })
  )
}

const fetchList = async () => {
  loading.value = true
  try {
    const result = await pagePolicies(query)
    list.value = result.records || []
    total.value = result.total || 0
    // 异步补齐标签，不阻塞列表渲染
    void fillFirmwareVersionLabelMap(list.value)
  } finally {
    loading.value = false
  }
}

const fetchFirmwareByProduct = async (productId?: number) => {
  if (!productId) {
    firmwareOptions.value = []
    form.firmwareVersionId = undefined
    return
  }
  firmwareLoading.value = true
  try {
    const versions = await getFirmwareVersionsByProduct(productId, true) // 仅READY状态
    firmwareOptions.value = versions
    versions.forEach((item) => {
      firmwareLabelMap[item.id] = item.version
    })
  } finally {
    firmwareLoading.value = false
  }
}

const openCreateDialog = () => {
  dialogMode.value = 'create'
  editingId.value = null
  form.productId = undefined
  form.firmwareVersionId = undefined
  form.name = ''
  form.grayRate = 0
  form.priority = 0
  form.planTime = ''
  form.status = 'ACTIVE'
  form.remark = ''
  firmwareOptions.value = []
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

const openEditDialog = async (row: UpgradePolicyItem) => {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.productId = row.productId
  form.firmwareVersionId = row.firmwareVersionId
  form.name = row.name
  form.grayRate = row.grayRate
  form.priority = row.priority
  form.planTime = row.planTime || ''
  form.status = row.status
  form.remark = row.remark || ''
  await fetchFirmwareByProduct(row.productId)
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

const onFormProductChange = async (productId?: number) => {
  form.firmwareVersionId = undefined
  await fetchFirmwareByProduct(productId)
}

const submitForm = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    const payload = {
      productId: form.productId!,
      firmwareVersionId: form.firmwareVersionId!,
      name: form.name,
      grayRate: form.grayRate,
      priority: form.priority,
      planTime: form.planTime || undefined,
      status: form.status,
      remark: form.remark || undefined,
    }

    if (dialogMode.value === 'create') {
      await createPolicy(payload)
      ElMessage.success(t('common.createSuccess'))
    } else if (editingId.value) {
      await updatePolicy(editingId.value, payload)
      ElMessage.success(t('common.updateSuccess'))
    }

    dialogVisible.value = false
    await fetchList()
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (row: UpgradePolicyItem) => {
  await ElMessageBox.confirm(t('common.deleteConfirm'), t('common.tip'), { type: 'warning' })
  await deletePolicy(row.id)
  ElMessage.success(t('common.deleteSuccess'))
  await fetchList()
}

const resetSearch = () => {
  query.page = 1
  query.productId = undefined
  query.name = ''
  query.status = undefined
  void fetchList()
}

const handleSearch = () => {
  query.page = 1
  void fetchList()
}

const handleFilterChange = () => {
  query.page = 1
  void fetchList()
}

const getProductName = (productId: number) => {
  const product = products.value.find(item => item.id === productId)
  return product?.name || `${productId}`
}

const getFirmwareVersionLabel = (firmwareVersionId: number) => {
  return firmwareLabelMap[firmwareVersionId] || `${firmwareVersionId}`
}

onMounted(() => {
  void handleProductSearch('')
  void fetchList()
})
</script>

<template>
  <PageCardTableShell :title="t('policy.title')">
    <template #actions>
      <div class="flex items-center gap-2">
        <el-select
          v-model="query.productId"
          :loading="productSearchLoading"
          :placeholder="t('policy.product')"
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
          v-model="query.name"
          :placeholder="t('policy.name')"
          clearable
          style="width: 180px"
          @keyup.enter="handleSearch"
        />
        <el-select
          v-model="query.status"
          :placeholder="t('policy.statusPlaceholder')"
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
          v-if="userStore.hasPermission('fota:policy:create')"
          type="primary"
          @click="openCreateDialog"
        >
          {{ t('policy.add') }}
        </el-button>
      </div>
    </template>

    <el-table
      v-loading="loading"
      :data="list"
      stripe
    >
      <el-table-column
        prop="name"
        :label="t('policy.name')"
        min-width="180"
      />
      <el-table-column
        prop="productId"
        :label="t('policy.product')"
        min-width="180"
      >
        <template #default="{ row }">
          {{ getProductName(row.productId) }}
        </template>
      </el-table-column>
      <el-table-column
        prop="firmwareVersionId"
        :label="t('policy.firmwareVersion')"
        min-width="180"
      >
        <template #default="{ row }">
          {{ getFirmwareVersionLabel(row.firmwareVersionId) }}
        </template>
      </el-table-column>
      <el-table-column
        prop="grayRate"
        :label="t('policy.grayRate')"
        width="120"
      >
        <template #default="{ row }">
          {{ row.grayRate }}%
        </template>
      </el-table-column>
      <el-table-column
        prop="priority"
        :label="t('policy.priority')"
        width="100"
      />
      <el-table-column
        prop="planTime"
        :label="t('policy.planTime')"
        width="180"
      />
      <el-table-column
        prop="status"
        :label="t('policy.status')"
        width="120"
      >
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="resolveStatusType(policyStatusTypeMap, row.status)"
          >
            {{ t(resolveStatusLabelKey(row.status)) }}
          </el-tag>
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
            v-if="userStore.hasPermission('fota:policy:update')"
            link
            class="ui-action-primary"
            @click="openEditDialog(row)"
          >
            {{ t('common.edit') }}
          </el-button>
          <el-button
            v-if="userStore.hasPermission('fota:policy:delete')"
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
    :title="dialogMode === 'create' ? t('policy.add') : t('common.edit')"
    width="680px"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="formRules"
      label-width="120px"
    >
      <el-form-item
        prop="productId"
        :label="t('policy.product')"
      >
        <el-select
          v-model="form.productId"
          :loading="productSearchLoading"
          filterable
          remote
          reserve-keyword
          :remote-method="handleProductSearch"
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
        prop="firmwareVersionId"
        :label="t('policy.firmwareVersion')"
      >
        <el-select
          v-model="form.firmwareVersionId"
          :loading="firmwareLoading"
          filterable
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
        prop="name"
        :label="t('policy.name')"
      >
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item
        prop="grayRate"
        :label="t('policy.grayRate')"
      >
        <el-input-number
          v-model="form.grayRate"
          :min="0"
          :max="100"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item
        prop="priority"
        :label="t('policy.priority')"
      >
        <el-input-number
          v-model="form.priority"
          :min="0"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item
        prop="planTime"
        :label="t('policy.planTime')"
      >
        <el-date-picker
          v-model="form.planTime"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item
        prop="status"
        :label="t('policy.status')"
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
      <el-form-item :label="t('policy.remark')">
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="3"
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
