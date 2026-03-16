<script setup lang="ts">
import { InfoFilled } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import type { InstanceMetricsEnhanced } from '@/api/monitor'
import { formatPercentDirect, formatQps, formatLatency } from '../utils/formatters'
import { useLoadLevel, useCircuitState } from '../composables/useLoadLevel'

const props = withDefaults(
  defineProps<{
    data: InstanceMetricsEnhanced[]
    loading?: boolean
  }>(),
  {
    loading: false
  }
)

const emit = defineEmits<{
  rowClick: [row: InstanceMetricsEnhanced]
}>()

const { t } = useI18n()

// 使用 composables 获取负载等级和熔断状态映射
const getLoadLevelType = (level: string) => {
  const { loadLevelColor } = useLoadLevel(() => level)
  return loadLevelColor.value
}

const getLoadLevelText = (level: string) => {
  const { loadLevelText } = useLoadLevel(() => level)
  return loadLevelText.value
}

const getCircuitStateType = (state: string) => {
  const { circuitStateColor } = useCircuitState(() => state)
  return circuitStateColor.value
}

const handleRowClick = (row: InstanceMetricsEnhanced) => {
  emit('rowClick', row)
}
</script>

<template>
  <el-card>
    <template #header>
      <div class="flex items-center justify-between">
        <span class="inline-flex items-center gap-1 ui-card-title">
          {{ t('monitor.instanceOverview') }}
          <el-tooltip :content="t('monitor.instanceOverviewDesc')" placement="top">
            <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
          </el-tooltip>
        </span>
      </div>
    </template>

    <el-table
      :data="data"
      size="small"
      :loading="loading"
      stripe
      class="clickable-rows"
      @row-click="handleRowClick"
    >
      <el-table-column prop="instance" :label="t('monitor.node')" min-width="140" fixed />

      <el-table-column :label="t('monitor.belongRegion')" min-width="90">
        <template #default="{ row }">{{ row.region }}</template>
      </el-table-column>

      <el-table-column :label="t('monitor.belongHost')" min-width="120">
        <template #default="{ row }">{{ row.host }}</template>
      </el-table-column>

      <el-table-column :label="t('monitor.loadScoreLevel')" min-width="110" align="center">
        <template #default="{ row }">
          <div class="flex items-center justify-center gap-1">
            <span class="font-semibold">{{ row.loadScore }}</span>
            <el-tag :type="getLoadLevelType(row.loadLevel)" size="small">
              {{ getLoadLevelText(row.loadLevel) }}
            </el-tag>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="Check / Report QPS" min-width="120" align="right">
        <template #default="{ row }">
          <div class="flex flex-col items-end">
            <span>{{ formatQps(row.checkQps) }}</span>
            <span class="text-xs text-gray-500">{{ formatQps(row.reportQps) }}</span>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="Check / Report P99" min-width="120" align="right">
        <template #default="{ row }">
          <div class="flex flex-col items-end">
            <span>{{ formatLatency(row.checkP99Latency) }}</span>
            <span class="text-xs text-gray-500">{{ formatLatency(row.reportP99Latency) }}</span>
          </div>
        </template>
      </el-table-column>

      <el-table-column :label="t('monitor.activeRequests')" min-width="90" align="right">
        <template #default="{ row }">{{ row.activeRequests }}</template>
      </el-table-column>

      <el-table-column :label="t('monitor.blockRate')" min-width="80" align="right">
        <template #default="{ row }">{{ formatPercentDirect(row.blockRate) }}</template>
      </el-table-column>

      <el-table-column :label="t('monitor.circuitState')" min-width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="getCircuitStateType(row.circuitState)" size="small">
            {{ row.circuitState }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="!loading && data.length === 0" class="text-center text-gray-400 py-4">
      {{ t('monitor.noDataAvailable') }}
    </div>
  </el-card>
</template>

<style scoped>
.clickable-rows :deep(.el-table__body-row) {
  cursor: pointer;
}

.clickable-rows :deep(.el-table__body-row:hover) {
  background-color: var(--el-table-row-hover-bg-color);
}
</style>
