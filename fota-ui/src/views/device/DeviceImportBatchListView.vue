<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/date'
import {
  pageBatches,
  getBatchDevices,
  type DeviceImportBatchItem,
  type DeviceItem,
} from '@/api/deviceImportBatch'
import { searchProducts, type ProductItem } from '@/api/product'
import { trimFormValues } from '@/utils/form'

const { t } = useI18n()
const userStore = useUserStore()

const list = ref<DeviceImportBatchItem[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({
  page: 1,
  size: 20,
  batchName: '',
  productId: undefined as number | undefined,
  status: undefined as 'IMPORTING' | 'SUCCESS' | 'FAILED' | 'PARTIAL' | undefined,
})

const canRead = computed(() => userStore.hasPermission('fota:device:read'))

// 产品列表（用于筛选）
const products = ref<ProductItem[]>([])
const productsLoading = ref(false)

/**
 * 获取产品列表
 */
const fetchProducts = async () => {
  productsLoading.value = true
  try {
    const result = await searchProducts('')
    products.value = result.records || []
  } catch (error) {
    console.error('获取产品列表失败:', error)
    products.value = []
  } finally {
    productsLoading.value = false
  }
}

// 批次状态选项
const statusOptions: Array<{ value: string; label: string; type: string }> = [
  { value: 'IMPORTING', label: t('device.importing'), type: 'info' },
  { value: 'SUCCESS', label: t('device.importSuccess'), type: 'success' },
  { value: 'FAILED', label: t('device.importFailed'), type: 'danger' },
  { value: 'PARTIAL', label: t('device.importPartial'), type: 'warning' },
]

/**
 * 批次状态类型
 */
const getStatusType = (status: string) => {
  return statusOptions.find(s => s.value === status)?.type || 'info'
}

/**
 * 批次状态文本
 */
const getStatusText = (status: string) => {
  return statusOptions.find(s => s.value === status)?.label || status
}

/**
 * 计算进度百分比
 */
const getProgressPercent = (batch: DeviceImportBatchItem) => {
  if (batch.totalCount === 0) return 0
  return Math.round((batch.successCount + batch.failedCount) / batch.totalCount * 100)
}

/**
 * 获取列表
 */
const fetchList = async () => {
  if (!canRead.value) return

  loading.value = true
  try {
    const result = await pageBatches(trimFormValues(query))
    list.value = result.records || []
    total.value = result.total || 0
  } finally {
    loading.value = false
  }
}

/**
 * 搜索
 */
const handleSearch = () => {
  query.page = 1
  void fetchList()
}

/**
 * 筛选变化
 */
const handleFilterChange = () => {
  query.page = 1
  void fetchList()
}

/**
 * 重置搜索
 */
const resetSearch = () => {
  query.page = 1
  query.batchName = ''
  query.productId = undefined
  query.status = undefined
  void fetchList()
}

// 详情抽屉
const drawerVisible = ref(false)
const currentBatch = ref<DeviceImportBatchItem | null>(null)
const batchDevices = ref<DeviceItem[]>([])
const batchDevicesTotal = ref(0)
const batchDevicesLoading = ref(false)

/**
 * 打开详情抽屉
 */
const openDetailDrawer = async (batch: DeviceImportBatchItem) => {
  currentBatch.value = batch
  drawerVisible.value = true
  await fetchBatchDevices()
}

/**
 * 获取批次下的设备
 */
const fetchBatchDevices = async () => {
  if (!currentBatch.value) return

  batchDevicesLoading.value = true
  try {
    const result = await getBatchDevices(currentBatch.value.id, {
      page: 1,
      size: 100,
    })
    batchDevices.value = result.records || []
    batchDevicesTotal.value = result.total || 0
  } finally {
    batchDevicesLoading.value = false
  }
}

/**
 * 关闭抽屉
 */
const closeDrawer = () => {
  drawerVisible.value = false
  currentBatch.value = null
  batchDevices.value = []
  batchDevicesTotal.value = 0
}

onMounted(() => {
  void fetchProducts()
  void fetchList()
})
</script>

<template>
  <PageCardTableShell :title="t('device.importBatchTitle')">
    <template #actions>
      <div class="flex items-center gap-2">
        <el-input
          v-model="query.batchName"
          :placeholder="t('device.batchName')"
          clearable
          style="width: 180px"
          @keyup.enter="handleSearch"
        />
        <el-select
          v-model="query.productId"
          :placeholder="t('device.productId')"
          clearable
          filterable
          style="width: 180px"
          @change="handleFilterChange"
        >
          <el-option
            v-for="product in products"
            :key="product.id"
            :label="product.name"
            :value="product.id"
          />
        </el-select>
        <el-select
          v-model="query.status"
          :placeholder="t('device.status')"
          clearable
          style="width: 150px"
          @change="handleFilterChange"
        >
          <el-option
            v-for="status in statusOptions"
            :key="status.value"
            :label="status.label"
            :value="status.value"
          />
        </el-select>
        <el-button @click="handleSearch">
          {{ t('common.search') }}
        </el-button>
        <el-button @click="resetSearch">
          {{ t('common.refresh') }}
        </el-button>
      </div>
    </template>

    <el-table
      v-loading="loading"
      :data="list"
      stripe
    >
      <el-table-column
        prop="batchName"
        :label="t('device.batchName')"
        min-width="180"
      />
      <el-table-column
        :label="t('device.productId')"
        prop="productId"
        min-width="150"
      >
        <template #default="{ row }">
          {{ row.productName || '-' }}
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
            :type="getStatusType(row.status)"
          >
            {{ getStatusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('device.importProgress')"
        min-width="200"
      >
        <template #default="{ row }">
          <div class="flex items-center gap-2">
            <el-progress
              :percentage="getProgressPercent(row)"
              :status="
                row.status === 'SUCCESS' ? 'success' :
                row.status === 'FAILED' ? 'exception' :
                undefined
              "
              :stroke-width="8"
              style="flex: 1"
            />
            <span class="text-xs text-gray-500">
              {{ row.successCount + row.failedCount }} / {{ row.totalCount }}
            </span>
          </div>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('device.totalDevices')"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          {{ row.totalCount }}
        </template>
      </el-table-column>
      <el-table-column
        :label="t('device.successCount')"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          <span class="text-green-600">{{ row.successCount }}</span>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('device.failedCount')"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          <span class="text-red-600">{{ row.failedCount }}</span>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('device.startedAt')"
        width="170"
      >
        <template #default="{ row }">
          {{ formatDateTime(row.startedAt) }}
        </template>
      </el-table-column>
      <el-table-column
        :label="t('common.actions')"
        width="100"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            link
            @click="openDetailDrawer(row)"
          >
            {{ t('common.detail') }}
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

  <!-- 详情抽屉 -->
  <el-drawer
    v-model="drawerVisible"
    :title="t('device.batchDetail')"
    size="800px"
    @close="closeDrawer"
  >
    <template v-if="currentBatch">
      <!-- 批次基本信息 -->
      <div class="mb-6">
        <h3 class="text-base font-medium mb-3">{{ t('device.basicInfo') }}</h3>
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('device.batchName')">
            {{ currentBatch.batchName }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('device.status')">
            <el-tag
              size="small"
              :type="getStatusType(currentBatch.status)"
            >
              {{ getStatusText(currentBatch.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('device.sourceFile')">
            {{ currentBatch.sourceFile || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('device.startedAt')">
            {{ formatDateTime(currentBatch.startedAt) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('device.finishedAt')">
            {{ formatDateTime(currentBatch.finishedAt) || '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 导入统计 -->
      <div class="mb-6">
        <h3 class="text-base font-medium mb-3">{{ t('device.importProgress') }}</h3>
        <div class="grid grid-cols-4 gap-4 mb-4">
          <div class="p-4 bg-gray-50 dark:bg-gray-800 rounded">
            <div class="text-sm text-gray-500 mb-1">{{ t('device.totalDevices') }}</div>
            <div class="text-2xl font-medium">{{ currentBatch.totalCount }}</div>
          </div>
          <div class="p-4 bg-green-50 dark:bg-green-900/20 rounded">
            <div class="text-sm text-green-600 mb-1">{{ t('device.successCount') }}</div>
            <div class="text-2xl font-medium text-green-600">{{ currentBatch.successCount }}</div>
          </div>
          <div class="p-4 bg-red-50 dark:bg-red-900/20 rounded">
            <div class="text-sm text-red-600 mb-1">{{ t('device.failedCount') }}</div>
            <div class="text-2xl font-medium text-red-600">{{ currentBatch.failedCount }}</div>
          </div>
          <div class="p-4 bg-gray-50 dark:bg-gray-800 rounded">
            <div class="text-sm text-gray-500 mb-1">{{ t('device.successRate') }}</div>
            <div class="text-2xl font-medium">
              {{ currentBatch.totalCount > 0 ? Math.round(currentBatch.successCount / currentBatch.totalCount * 100) : 0 }}%
            </div>
          </div>
        </div>

        <div
          v-if="currentBatch.errorMessage"
          class="p-3 bg-red-50 dark:bg-red-900/20 rounded text-sm text-red-600"
        >
          {{ currentBatch.errorMessage }}
        </div>
      </div>

      <!-- 批次下的设备列表 -->
      <div>
        <h3 class="text-base font-medium mb-3">{{ t('device.devicesInBatch') }}</h3>
        <el-table
          v-loading="batchDevicesLoading"
          :data="batchDevices"
          stripe
          max-height="400"
        >
          <el-table-column
            prop="imei"
            :label="t('device.imei')"
            min-width="160"
          />
          <el-table-column
            prop="productName"
            :label="t('device.productId')"
            min-width="150"
          />
          <el-table-column
            prop="status"
            :label="t('device.status')"
            width="100"
          >
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'ONLINE' ? 'success' : 'info'">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="lastSeenAt"
            :label="t('device.lastSeenAt')"
            width="170"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.lastSeenAt) }}
            </template>
          </el-table-column>
        </el-table>

        <div
          v-if="batchDevicesTotal > 100"
          class="mt-2 text-sm text-gray-500 text-center"
        >
          {{ t('device.showingPartialDevices', { count: 100, total: batchDevicesTotal }) }}
        </div>
      </div>
    </template>
  </el-drawer>
</template>
