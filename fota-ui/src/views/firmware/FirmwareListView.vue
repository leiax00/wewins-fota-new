<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import JsonFieldEditor from '@/components/json-field/JsonFieldEditor.vue'
import {
  createFirmwareVersion,
  deleteFirmwareVersion,
  pageFirmwareVersions,
  updateFirmwareVersion,
  type FirmwareVersionItem,
} from '@/api/firmware'
import { pageProducts, type ProductItem } from '@/api/product'

const { t } = useI18n()
const userStore = useUserStore()

const list = ref<FirmwareVersionItem[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({
  page: 1,
  size: 20,
  productId: undefined as number | undefined,
  version: '',
})

const products = ref<ProductItem[]>([])
const productsLoading = ref(false)

const canShowActions = computed(() =>
  userStore.hasPermission('fota:firmware:update') ||
  userStore.hasPermission('fota:firmware:delete')
)

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref()

const form = reactive({
  productId: undefined as number | undefined,
  version: '',
  fileUrl: '',
  fileSize: 0,
  md5: '',
  sha256: '',
  tags: '',
  meta: '',
})

const formRules = {
  productId: [{ required: true, message: t('firmware.productIdRequired'), trigger: 'change' }],
  version: [{ required: true, message: t('firmware.versionRequired'), trigger: 'blur' }],
  fileUrl: [{ required: true, message: t('firmware.fileUrlRequired'), trigger: 'blur' }],
  fileSize: [{ required: true, message: t('firmware.fileSizeRequired'), trigger: 'blur' }],
  md5: [{ required: true, message: t('firmware.md5Required'), trigger: 'blur' }],
}

const fetchProducts = async () => {
  productsLoading.value = true
  try {
    const result = await pageProducts({ page: 1, size: 1000 })
    products.value = result.records || []
  } finally {
    productsLoading.value = false
  }
}

const fetchList = async () => {
  loading.value = true
  try {
    const result = await pageFirmwareVersions(query)
    list.value = result.records || []
    total.value = result.total || 0
  } finally {
    loading.value = false
  }
}

const openCreateDialog = () => {
  dialogMode.value = 'create'
  editingId.value = null
  form.productId = undefined
  form.version = ''
  form.fileUrl = ''
  form.fileSize = 0
  form.md5 = ''
  form.sha256 = ''
  form.tags = ''
  form.meta = ''
  dialogVisible.value = true
}

const openEditDialog = (row: FirmwareVersionItem) => {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.productId = row.productId
  form.version = row.version
  form.fileUrl = row.fileUrl
  form.fileSize = row.fileSize
  form.md5 = row.md5
  form.sha256 = row.sha256
  form.tags = row.tags || ''
  form.meta = row.meta || ''
  dialogVisible.value = true
}

const submitForm = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    const payload = {
      productId: form.productId!,
      version: form.version,
      fileUrl: form.fileUrl,
      fileSize: form.fileSize,
      md5: form.md5,
      sha256: form.sha256,
      tags: form.tags || undefined,
      meta: form.meta || undefined,
    }

    if (dialogMode.value === 'create') {
      await createFirmwareVersion(payload)
      ElMessage.success(t('common.createSuccess'))
    } else if (editingId.value) {
      await updateFirmwareVersion(editingId.value, payload)
      ElMessage.success(t('common.updateSuccess'))
    }

    dialogVisible.value = false
    await fetchList()
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (row: FirmwareVersionItem) => {
  await ElMessageBox.confirm(t('common.deleteConfirm'), t('common.tip'), { type: 'warning' })
  await deleteFirmwareVersion(row.id)
  ElMessage.success(t('common.deleteSuccess'))
  await fetchList()
}

const resetSearch = () => {
  query.page = 1
  query.productId = undefined
  query.version = ''
  void fetchList()
}

const getProductName = (productId: number) => {
  const product = products.value.find(p => p.id === productId)
  return product?.name || `${productId}`
}

const formatFileSize = (bytes: number) => {
  if (!bytes) return '-'
  const kb = bytes / 1024
  const mb = kb / 1024
  if (mb >= 1) return `${mb.toFixed(2)} MB`
  if (kb >= 1) return `${kb.toFixed(2)} KB`
  return `${bytes} B`
}

onMounted(() => {
  void fetchProducts()
  void fetchList()
})
</script>

<template>
  <PageCardTableShell :title="t('firmware.title')">
    <template #actions>
      <div class="flex items-center gap-2">
        <el-select
          v-model="query.productId"
          :loading="productsLoading"
          :placeholder="t('firmware.product')"
          clearable
          style="width: 180px"
          @change="fetchList"
        >
          <el-option
            v-for="product in products"
            :key="product.id"
            :label="product.name"
            :value="product.id"
          />
        </el-select>
        <el-input
          v-model="query.version"
          :placeholder="t('firmware.version')"
          clearable
          style="width: 140px"
          @keyup.enter="fetchList"
        />
        <el-button @click="fetchList">{{ t('common.search') }}</el-button>
        <el-button @click="resetSearch">{{ t('common.refresh') }}</el-button>
        <el-button
          v-if="userStore.hasPermission('fota:firmware:create')"
          type="primary"
          @click="openCreateDialog"
        >
          {{ t('firmware.add') }}
        </el-button>
      </div>
    </template>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="version" :label="t('firmware.version')" min-width="120" />
      <el-table-column prop="productId" :label="t('firmware.product')" min-width="160">
        <template #default="{ row }">
          {{ getProductName(row.productId) }}
        </template>
      </el-table-column>
      <el-table-column prop="fileSize" :label="t('firmware.fileSize')" min-width="100">
        <template #default="{ row }">
          {{ formatFileSize(row.fileSize) }}
        </template>
      </el-table-column>
      <el-table-column prop="md5" :label="t('firmware.md5')" min-width="200" show-overflow-tooltip />
      <el-table-column prop="createdAt" :label="t('firmware.uploadTime')" width="170" />

      <el-table-column
        v-if="canShowActions"
        :label="t('common.actions')"
        width="150"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            v-if="userStore.hasPermission('fota:firmware:update')"
            link
            class="ui-action-primary"
            @click="openEditDialog(row)"
          >
            {{ t('common.edit') }}
          </el-button>
          <el-button
            v-if="userStore.hasPermission('fota:firmware:delete')"
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
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        @current-change="fetchList"
        @size-change="fetchList"
      />
    </div>
  </PageCardTableShell>

  <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? t('firmware.add') : t('common.edit')" width="680px">
    <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px">
      <el-form-item prop="productId" :label="t('firmware.product')">
        <el-select
          v-model="form.productId"
          :loading="productsLoading"
          filterable
          style="width: 100%"
        >
          <el-option
            v-for="product in products"
            :key="product.id"
            :label="product.name"
            :value="product.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item prop="version" :label="t('firmware.version')">
        <el-input v-model="form.version" placeholder="1.0.0" />
      </el-form-item>
      <el-form-item prop="fileUrl" :label="t('firmware.fileUrl')">
        <el-input v-model="form.fileUrl" />
      </el-form-item>
      <el-form-item prop="fileSize" :label="t('firmware.fileSize')">
        <el-input-number v-model="form.fileSize" :min="0" style="width: 100%" />
      </el-form-item>
      <el-form-item prop="md5" :label="t('firmware.md5')">
        <el-input v-model="form.md5" />
      </el-form-item>
      <el-form-item prop="sha256" :label="t('firmware.sha256')">
        <el-input v-model="form.sha256" />
      </el-form-item>
      <el-form-item :label="t('firmware.tags')">
        <JsonFieldEditor
          v-model="form.tags"
          dict-type-code="json_schema.firmware_tags"
          mode="form"
          class="w-full"
          :allow-mode-switch="true"
          :disabled="submitting"
        />
      </el-form-item>
      <el-form-item :label="t('firmware.meta')">
        <JsonFieldEditor
          v-model="form.meta"
          dict-type-code="json_schema.firmware_meta"
          mode="form"
          class="w-full"
          :allow-mode-switch="true"
          :disabled="submitting"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="submitting" @click="submitForm">{{ t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>
