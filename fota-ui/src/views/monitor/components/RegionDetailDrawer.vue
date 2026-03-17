<script setup lang="ts">
import { Loading } from '@element-plus/icons-vue'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { RegionDetail, RegionMetrics } from '@/api/monitor'
import HotProductsTable from './HotProductsTable.vue'
import RefreshControl from './RefreshControl.vue'
import { formatPercent, formatPercentDirect, formatQps, formatNumber, formatBytes } from '../utils/formatters'
import { useLoadLevel } from '../composables/useLoadLevel'

const props = withDefaults(
  defineProps<{
    visible: boolean
    region: RegionMetrics | null
    detail: RegionDetail | null
    loading?: boolean
    autoRefresh?: boolean
    refreshInterval?: number
  }>(),
  {
    loading: false,
    autoRefresh: true,
    refreshInterval: 15000
  }
)

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'update:autoRefresh': [value: boolean]
  'update:refreshInterval': [value: number]
  'refresh': []
}>()

const { t } = useI18n()

const drawerVisible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value),
})

const { loadLevelColor, loadLevelText } = useLoadLevel(() => props.region?.loadLevel)

const handleClose = () => {
  drawerVisible.value = false
}
</script>

<template>
  <el-drawer
    v-model="drawerVisible"
    size="70%"
    @close="handleClose"
    class="monitor-drawer"
  >
    <template #header>
      <div class="flex items-center justify-between w-full">
        <span>{{ t('monitor.regionDetail') }} - {{ region?.region ?? '-' }}</span>
        <RefreshControl
          :model-value="autoRefresh"
          @update:model-value="emit('update:autoRefresh', $event)"
          :interval="refreshInterval"
          @update:interval="emit('update:refreshInterval', $event)"
          :loading="loading"
          @refresh="emit('refresh')"
        />
      </div>
    </template>
    <div v-if="loading" class="flex items-center justify-center py-8">
      <el-icon class="is-loading"><Loading /></el-icon>
    </div>

    <div v-else-if="region" class="space-y-4">
      <!-- 区域摘要卡片 -->
      <el-card>
        <template #header>
          <span class="ui-card-title">{{ t('monitor.globalSummary') }}</span>
        </template>

        <div class="grid grid-cols-2 gap-4 md:grid-cols-4 lg:grid-cols-6">
          <div class="summary-item">
            <div class="summary-label">{{ t('monitor.loadScore') }}</div>
            <div class="summary-value">{{ region.loadScore }}</div>
          </div>

          <div class="summary-item">
            <div class="summary-label">{{ t('monitor.loadLevel') }}</div>
            <div class="summary-value">
              <el-tag :type="loadLevelColor" size="small">{{ loadLevelText }}</el-tag>
            </div>
          </div>

          <div class="summary-item">
            <div class="summary-label">Check QPS</div>
            <div class="summary-value">{{ formatQps(region.checkQps) }}</div>
          </div>

          <div class="summary-item">
            <div class="summary-label">Report QPS</div>
            <div class="summary-value">{{ formatQps(region.reportQps) }}</div>
          </div>

          <div class="summary-item">
            <div class="summary-label">{{ t('dashboard.todayActiveDevices') }}</div>
            <div class="summary-value">{{ formatNumber(region.todayActiveDevices) }}</div>
          </div>

          <div class="summary-item">
            <div class="summary-label">{{ t('monitor.blockRate') }}</div>
            <div class="summary-value">{{ formatPercent(region.blockRate) }}</div>
          </div>
        </div>
      </el-card>

      <!-- 主机概览 -->
      <el-card>
        <template #header>
          <span class="ui-card-title">{{ t('monitor.hostOverview') }}</span>
        </template>
        <el-table :data="detail?.hosts ?? []" size="small" stripe>
          <el-table-column prop="host" :label="t('monitor.host')" min-width="140" />
          <el-table-column label="CPU">
            <template #default="{ row }">{{ formatPercentDirect(row.cpuUsage) }}</template>
          </el-table-column>
          <el-table-column :label="t('monitor.memory')">
            <template #default="{ row }">{{ formatPercentDirect(row.memoryUsage) }}</template>
          </el-table-column>
          <el-table-column :label="t('monitor.networkIn')">
            <template #default="{ row }">{{ formatBytes(row.networkInBytes) }}</template>
          </el-table-column>
          <el-table-column :label="t('monitor.networkOut')">
            <template #default="{ row }">{{ formatBytes(row.networkOutBytes) }}</template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 实例概览 -->
      <el-card>
        <template #header>
          <span class="ui-card-title">{{ t('monitor.instanceOverview') }}</span>
        </template>
        <el-table :data="detail?.instances ?? []" size="small" stripe>
          <el-table-column prop="instance" :label="t('monitor.node')" min-width="140" />
          <el-table-column label="Check QPS">
            <template #default="{ row }">{{ formatQps(row.checkQps) }}</template>
          </el-table-column>
          <el-table-column label="Report QPS">
            <template #default="{ row }">{{ formatQps(row.reportQps) }}</template>
          </el-table-column>
          <el-table-column :label="t('monitor.activeRequests')">
            <template #default="{ row }">{{ row.activeRequests }}</template>
          </el-table-column>
          <el-table-column :label="t('monitor.blockRate')">
            <template #default="{ row }">{{ formatPercent(row.blockRate) }}</template>
          </el-table-column>
          <el-table-column :label="t('monitor.circuitState')">
            <template #default="{ row }">
              <el-tag :type="row.circuitState === 'CLOSED' ? 'success' : row.circuitState === 'OPEN' ? 'danger' : 'warning'" size="small">
                {{ row.circuitState }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 热点产品 -->
      <HotProductsTable :data="detail?.hotProducts ?? []" :show-active-region-count="false" />
    </div>
  </el-drawer>
</template>

<style scoped>
.summary-item {
  padding: 8px;
  border-radius: 6px;
  background-color: var(--el-bg-color-page);
  text-align: center;
}

.summary-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}

.summary-value {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
</style>

<style>
.monitor-drawer .el-drawer__header {
  padding: 12px 16px 8px 16px !important;
  margin-bottom: 0 !important;
}

.monitor-drawer .el-drawer__body {
  padding: 8px 16px 16px 16px !important;
}
</style>
