<script setup lang="ts">
import { InfoFilled } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import type { RegionMetrics } from '@/api/monitor'
import { formatPercent, formatQps, formatLatency, formatNumber } from '../utils/formatters'
import { useLoadLevel } from '../composables/useLoadLevel'

const props = defineProps<{
  data: RegionMetrics[]
  loading?: boolean
}>()

const emit = defineEmits<{
  rowClick: [row: RegionMetrics]
}>()

const { t } = useI18n()

// 使用 composable 获取负载等级映射
const getLoadLevelType = (level: string) => {
  const { loadLevelColor } = useLoadLevel(() => level)
  return loadLevelColor.value
}

const getLoadLevelText = (level: string) => {
  const { loadLevelText } = useLoadLevel(() => level)
  return loadLevelText.value
}

const handleRowClick = (row: RegionMetrics) => {
  emit('rowClick', row)
}
</script>

<template>
  <el-card>
    <template #header>
      <div class="flex items-center justify-between">
        <span class="inline-flex items-center gap-1 ui-card-title">
          {{ t('monitor.regionOverview') }}
          <el-tooltip :content="t('monitor.regionOverviewDesc')" placement="top">
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
      <el-table-column prop="region" :label="t('monitor.regionOverview')" min-width="100" fixed />

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

      <el-table-column label="Check QPS" min-width="100" align="right">
        <template #default="{ row }">{{ formatQps(row.checkQps) }}</template>
      </el-table-column>

      <el-table-column label="Report QPS" min-width="100" align="right">
        <template #default="{ row }">{{ formatQps(row.reportQps) }}</template>
      </el-table-column>

      <el-table-column label="Check P99" min-width="90" align="right">
        <template #default="{ row }">{{ formatLatency(row.checkP99Latency) }}</template>
      </el-table-column>

      <el-table-column label="Report P99" min-width="90" align="right">
        <template #default="{ row }">{{ formatLatency(row.reportP99Latency) }}</template>
      </el-table-column>

      <el-table-column :label="t('dashboard.todayActiveDevices')" min-width="110" align="right">
        <template #default="{ row }">{{ formatNumber(row.todayActiveDevices) }}</template>
      </el-table-column>

      <el-table-column :label="t('monitor.blockRate')" min-width="80" align="right">
        <template #default="{ row }">{{ formatPercent(row.blockRate) }}</template>
      </el-table-column>

      <el-table-column :label="t('monitor.instanceCount')" min-width="80" align="right">
        <template #default="{ row }">{{ row.instanceCount }}</template>
      </el-table-column>

      <el-table-column :label="t('monitor.hotProductCount')" min-width="90" align="right">
        <template #default="{ row }">{{ row.hotProductCount }}</template>
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
