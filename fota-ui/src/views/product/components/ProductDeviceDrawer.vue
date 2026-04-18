<script setup lang="ts">
import { Search } from '@element-plus/icons-vue'
import { computed, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import StatisticsTabContainer from '@/components/statistics/StatisticsTabContainer.vue'
import {
  getProductDevices,
  type DeviceListItem,
  type StatisticsResponse,
  type DeviceListResponse,
} from '@/api/statistics'
import { deviceStatusTypeMap, resolveStatusLabelKey, resolveStatusType } from '@/constants/status'
import { formatDateTime } from '@/utils/date'

interface Props {
  visible: boolean
  productId: number
}

interface Emits {
  (e: 'update:visible', value: boolean): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const { t } = useI18n()

const drawerVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
})

const devices = ref<DeviceListItem[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const nextCursor = ref<number | null>(null)
const hasMore = ref(false)
const dataCalculatedAt = ref('')
const searchKeyword = ref('')
const searchInput = ref('')

let abortController: AbortController | null = null

const canLoadMore = computed(() => hasMore.value && !!nextCursor.value && !loading.value)

const abortPendingRequest = () => {
  abortController?.abort()
  abortController = null
}

const resetPagination = () => {
  nextCursor.value = null
  hasMore.value = false
}

const resetListState = () => {
  devices.value = []
  dataCalculatedAt.value = ''
  loadFailed.value = false
  resetPagination()
}

const fetchDevices = async (isLoadMore = false) => {
  if (loading.value || !props.productId) {
    return
  }

  abortPendingRequest()
  abortController = new AbortController()

  loading.value = true
  if (!isLoadMore) {
    loadFailed.value = false
  }

  try {
    const result = await getProductDevices(
      props.productId,
      {
        cursor: isLoadMore ? (nextCursor.value ?? undefined) : undefined,
        size: 20,
        keyword: searchKeyword.value || undefined,
      },
      abortController.signal,
    )

    applyResponse(result, isLoadMore)
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') {
      return
    }

    console.error('Failed to fetch product devices:', error)

    if (!isLoadMore) {
      loadFailed.value = true
      devices.value = []
      dataCalculatedAt.value = ''
      resetPagination()
    }
  } finally {
    loading.value = false
    abortController = null
  }
}

const applyResponse = (result: StatisticsResponse<DeviceListResponse>, isLoadMore: boolean) => {
  const deviceList = result.data.devices ?? []

  devices.value = isLoadMore ? [...devices.value, ...deviceList] : deviceList

  nextCursor.value = result.data.nextCursor
  hasMore.value = result.data.nextCursor !== null
  dataCalculatedAt.value = result.dataCalculatedAt
  loadFailed.value = false
}

const handleSearch = () => {
  searchKeyword.value = searchInput.value.trim()
  resetPagination()
  void fetchDevices()
}

const handleRefresh = (signal?: AbortSignal) => {
  if (signal) {
    signal.addEventListener('abort', () => abortPendingRequest(), { once: true })
  }

  resetPagination()
  void fetchDevices()
}

const handleLoadMore = () => {
  if (!canLoadMore.value) {
    return
  }

  void fetchDevices(true)
}

const handleClose = () => {
  drawerVisible.value = false
}

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      resetListState()
      void fetchDevices()
      return
    }

    abortPendingRequest()
  },
)

watch(
  () => props.productId,
  (productId, previousProductId) => {
    if (!props.visible || !productId || productId === previousProductId) {
      return
    }

    resetListState()
    void fetchDevices()
  },
)

onUnmounted(() => {
  abortPendingRequest()
})
</script>

<template>
  <el-drawer
    v-model="drawerVisible"
    :title="t('statistics.productDeviceDrawer.title')"
    size="70%"
    class="product-device-drawer"
    @close="handleClose"
  >
    <StatisticsTabContainer
      :data-calculated-at="dataCalculatedAt"
      :loading="loading"
      refresh-scope="product"
      :refresh-scope-id="productId"
      @refresh="handleRefresh"
    >
      <div class="device-drawer-toolbar">
        <div class="device-drawer-toolbar__search">
          <el-input
            v-model="searchInput"
            clearable
            :placeholder="t('statistics.productDeviceDrawer.searchPlaceholder')"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button
            type="primary"
            @click="handleSearch"
          >
            {{ t('common.search') }}
          </el-button>
        </div>
      </div>

      <el-alert
        v-if="loadFailed"
        type="error"
        :closable="false"
        show-icon
        class="mb-4"
      >
        <template #title>
          {{ t('statistics.productDeviceDrawer.loadFailed') }}
        </template>
        <template #default>
          <el-button
            link
            type="primary"
            @click="handleRefresh()"
          >
            {{ t('common.refresh') }}
          </el-button>
        </template>
      </el-alert>

      <template v-else-if="devices.length > 0">
        <el-table
          :data="devices"
          stripe
          class="device-drawer-table"
        >
          <el-table-column
            prop="id"
            :label="t('statistics.productDeviceDrawer.deviceId')"
            min-width="120"
          />
          <el-table-column
            prop="imei"
            :label="t('statistics.productDeviceDrawer.imei')"
            min-width="180"
            show-overflow-tooltip
          />
          <el-table-column
            prop="version"
            :label="t('statistics.productDeviceDrawer.currentVersion')"
            min-width="140"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ row.version || '-' }}
            </template>
          </el-table-column>
          <el-table-column
            prop="lastSeenAt"
            :label="t('statistics.productDeviceDrawer.lastSeenAt')"
            min-width="180"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.lastSeenAt) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="status"
            :label="t('statistics.productDeviceDrawer.status')"
            min-width="120"
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
        </el-table>

        <div class="device-drawer-footer">
          <el-button
            v-if="canLoadMore"
            :loading="loading"
            @click="handleLoadMore"
          >
            {{ t('statistics.productDeviceDrawer.loadMore') }}
          </el-button>
        </div>
      </template>

      <el-empty
        v-else
        :description="t('statistics.productDeviceDrawer.noData')"
      />
    </StatisticsTabContainer>
  </el-drawer>
</template>

<style scoped>
.device-drawer-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-bottom: 16px;
}

.device-drawer-toolbar__search {
  display: flex;
  align-items: center;
  gap: 12px;
  width: min(100%, 360px);
}

.device-drawer-table {
  width: 100%;
}

.device-drawer-footer {
  display: flex;
  justify-content: center;
  padding-top: 16px;
}

@media (max-width: 768px) {
  .device-drawer-toolbar {
    justify-content: stretch;
  }

  .device-drawer-toolbar__search {
    width: 100%;
  }
}
</style>

<style>
.product-device-drawer .el-drawer__header {
  padding: 12px 16px 8px 16px !important;
  margin-bottom: 0 !important;
}
.product-device-drawer .el-drawer__body {
  padding: 8px 16px 16px 16px !important;
}
</style>
