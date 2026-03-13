<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Filter, Refresh } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { operationLogApi, type OperationLogDetail, type OperationLogItem } from '@/api/monitor'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/date'
import { trimFormValues } from '@/utils/form'

const { t } = useI18n()
const userStore = useUserStore()

const canViewOperationLog = computed(() => userStore.hasPermission('monitor:operation-log:read'))

const loading = ref(false)
const detailLoading = ref(false)
const list = ref<OperationLogItem[]>([])
const total = ref(0)
const detailVisible = ref(false)
const detail = ref<OperationLogDetail | null>(null)
const advancedFilterVisible = ref(false)

const query = reactive({
  page: 1,
  size: 20,
  operatorKeyword: '',
  moduleCode: '',
  resourceCode: '',
  operationType: '',
  targetId: '',
  timeRange: [] as string[],
})

const moduleOptions = [
  { value: 'sys', label: 'SYS' },
  { value: 'fota', label: 'FOTA' },
  { value: 'monitor', label: 'MONITOR' },
]

const resourceOptions = [
  { value: 'user', label: 'User' },
  { value: 'role', label: 'Role' },
  { value: 'permission', label: 'Permission' },
  { value: 'dict_type', label: 'Dict Type' },
  { value: 'dict_item', label: 'Dict Item' },
  { value: 'product', label: 'Product' },
  { value: 'device', label: 'Device' },
  { value: 'device_import_batch', label: 'Device Import Batch' },
  { value: 'firmware', label: 'Firmware' },
  { value: 'policy', label: 'Policy' },
  { value: 'cache', label: 'Cache' },
  { value: 'control', label: 'Control' },
  { value: 'sentinel', label: 'Sentinel' },
]

const operationTypeOptions = [
  'CREATE',
  'UPDATE',
  'DELETE',
  'IMPORT',
  'EXPORT',
  'ASSIGN',
  'EXECUTE',
]

const fetchList = async () => {
  if (!canViewOperationLog.value) {
    return
  }
  loading.value = true
  try {
    const result = await operationLogApi.pageLogs(trimFormValues({
      ...query,
      timeRange: query.timeRange.length ? query.timeRange : undefined,
    }))
    list.value = result.records || []
    total.value = result.total || 0
  } catch {
    ElMessage.error(t('monitor.operationLogLoadFailed'))
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  query.page = 1
  query.size = 20
  query.operatorKeyword = ''
  query.moduleCode = ''
  query.resourceCode = ''
  query.operationType = ''
  query.targetId = ''
  query.timeRange = []
  void fetchList()
}

const handleAdvancedSearch = () => {
  query.page = 1
  void fetchList()
}

const handleAdvancedFilterChange = () => {
  query.page = 1
}

const openDetail = async (row: OperationLogItem) => {
  detailLoading.value = true
  detailVisible.value = true
  try {
    detail.value = await operationLogApi.getLogDetail(row.id)
  } catch {
    detailVisible.value = false
    ElMessage.error(t('monitor.operationLogDetailLoadFailed'))
  } finally {
    detailLoading.value = false
  }
}

const formatOperator = (row: OperationLogItem): string => {
  return row.operatorDisplayName || row.operatorUsername || '-'
}

const formatTarget = (row: OperationLogItem): string => {
  if (row.targetName && row.targetId) {
    return `${row.targetName} (${row.targetId})`
  }
  return row.targetName || row.targetId || '-'
}

const formatJson = (value: unknown): string => {
  if (!value) {
    return '{}'
  }
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

onMounted(() => {
  void fetchList()
})
</script>

<template>
  <PageCardTableShell :title="t('monitor.operationLogTitle')">
    <template #actions>
      <div class="flex items-center gap-2">
        <el-button :icon="Refresh" @click="resetQuery">
          {{ t('common.refresh') }}
        </el-button>
        <el-popover
          v-model:visible="advancedFilterVisible"
          :title="t('monitor.operationLogAdvancedFilter')"
          placement="bottom-end"
          :width="440"
          trigger="click"
          :show-arrow="true"
          popper-class="advanced-filter-popper"
        >
          <template #reference>
            <el-button :icon="Filter">
              {{ t('monitor.operationLogAdvancedFilter') }}
            </el-button>
          </template>

          <el-form
            :model="query"
            label-width="84px"
            class="advanced-filter-form"
            @click.stop
            @mousedown.stop
          >
            <el-form-item :label="t('monitor.operationLogOperator')">
              <el-input
                v-model="query.operatorKeyword"
                :placeholder="t('monitor.operationLogOperatorPlaceholder')"
                clearable
                @keyup.enter="handleAdvancedSearch"
              />
            </el-form-item>

            <el-form-item :label="t('monitor.operationLogModule')">
              <el-select
                v-model="query.moduleCode"
                :placeholder="t('monitor.operationLogModule')"
                clearable
                :teleported="false"
                style="width: 100%"
                @change="handleAdvancedFilterChange"
              >
                <el-option
                  v-for="option in moduleOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item :label="t('monitor.operationLogResource')">
              <el-select
                v-model="query.resourceCode"
                :placeholder="t('monitor.operationLogResource')"
                clearable
                :teleported="false"
                style="width: 100%"
                @change="handleAdvancedFilterChange"
              >
                <el-option
                  v-for="option in resourceOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item :label="t('monitor.operationLogType')">
              <el-select
                v-model="query.operationType"
                :placeholder="t('monitor.operationLogType')"
                clearable
                :teleported="false"
                style="width: 100%"
                @change="handleAdvancedFilterChange"
              >
                <el-option
                  v-for="option in operationTypeOptions"
                  :key="option"
                  :label="option"
                  :value="option"
                />
              </el-select>
            </el-form-item>

            <el-form-item :label="t('monitor.operationLogTargetId')">
              <el-input
                v-model="query.targetId"
                :placeholder="t('monitor.operationLogTargetIdPlaceholder')"
                clearable
                @keyup.enter="handleAdvancedSearch"
              />
            </el-form-item>

            <el-form-item :label="t('monitor.operationLogTimeRange')">
              <el-date-picker
                v-model="query.timeRange"
                type="datetimerange"
                range-separator="-"
                :start-placeholder="t('monitor.operationLogStartTime')"
                :end-placeholder="t('monitor.operationLogEndTime')"
                :teleported="false"
                value-format="YYYY-MM-DDTHH:mm:ss"
                class="operation-log-range-picker"
              />
            </el-form-item>

            <el-form-item class="mb-0">
              <div class="flex justify-end gap-2 w-full">
                <el-button size="small" @click="resetQuery">
                  {{ t('common.reset') }}
                </el-button>
                <el-button size="small" type="primary" @click="handleAdvancedSearch">
                  {{ t('common.search') }}
                </el-button>
              </div>
            </el-form-item>
          </el-form>
        </el-popover>
      </div>
    </template>

    <template v-if="canViewOperationLog">
      <el-table v-loading="loading" :data="list" border>
        <el-table-column :label="t('monitor.operationLogTime')" min-width="168">
          <template #default="{ row }">
            {{ formatDateTime(row.occurredAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="moduleCode" :label="t('monitor.operationLogModule')" min-width="100" />
        <el-table-column prop="resourceCode" :label="t('monitor.operationLogResource')" min-width="130" />
        <el-table-column prop="actionCode" :label="t('monitor.operationLogAction')" min-width="180" />
        <el-table-column prop="operationType" :label="t('monitor.operationLogType')" min-width="100" />
        <el-table-column :label="t('monitor.operationLogTarget')" min-width="220">
          <template #default="{ row }">
            {{ formatTarget(row) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('monitor.operationLogOperator')" min-width="140">
          <template #default="{ row }">
            {{ formatOperator(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="clientIp" :label="t('monitor.operationLogIp')" min-width="140" />
        <el-table-column :label="t('common.operation')" min-width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">
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
    </template>
    <template v-else>
      <el-empty :description="t('monitor.operationLogNoPermission')" />
    </template>
  </PageCardTableShell>

  <el-drawer
    v-model="detailVisible"
    :title="t('monitor.operationLogDetailTitle')"
    size="640px"
  >
    <div v-loading="detailLoading" class="operation-log-detail">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item :label="t('monitor.operationLogTime')">
            {{ formatDateTime(detail.occurredAt) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('monitor.operationLogOperator')">
            {{ detail.operatorDisplayName || detail.operatorUsername || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('monitor.operationLogAction')">
            {{ detail.actionCode }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('monitor.operationLogType')">
            {{ detail.operationType }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('monitor.operationLogPath')">
            {{ detail.requestMethod }} {{ detail.requestPath }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('monitor.operationLogTarget')">
            {{ formatTarget(detail) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('monitor.operationLogIp')">
            {{ detail.clientIp || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('monitor.operationLogUserAgent')">
            {{ detail.userAgent || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="mt-4">
          <div class="mb-2 font-medium operation-log-section-title">{{ t('monitor.operationLogRequestQuery') }}</div>
          <pre class="operation-log-json">{{ formatJson(detail.requestQuery) }}</pre>
        </div>

        <div class="mt-4">
          <div class="mb-2 font-medium operation-log-section-title">{{ t('monitor.operationLogRequestBody') }}</div>
          <pre class="operation-log-json">{{ formatJson(detail.requestBody) }}</pre>
        </div>
      </template>
    </div>
  </el-drawer>
</template>

<style scoped>
.operation-log-detail {
  min-height: 200px;
}

.operation-log-json {
  margin: 0;
  padding: 12px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-bg-color-page);
  color: var(--el-text-color-primary);
  caret-color: var(--el-text-color-primary);
  opacity: 1;
}

.operation-log-json::selection {
  background: var(--el-color-primary-light-7);
  color: var(--el-text-color-primary);
}

.operation-log-section-title {
  color: var(--el-text-color-primary);
}

.advanced-filter-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.advanced-filter-form :deep(.el-form-item__label) {
  color: var(--el-text-color-regular);
}

.advanced-filter-form :deep(.el-select),
.advanced-filter-form :deep(.el-input) {
  width: 100%;
}

.advanced-filter-form :deep(.operation-log-range-picker) {
  width: 100%;
}

.advanced-filter-form :deep(.operation-log-range-picker .el-range-input) {
  min-width: 0;
}

.advanced-filter-form :deep(.operation-log-range-picker .el-range-separator) {
  padding: 0 8px;
}
</style>
