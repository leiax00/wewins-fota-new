<script setup lang="ts">
import { InfoFilled } from '@element-plus/icons-vue'
import type { HostMetricsEnhanced } from '@/api/monitor'
import { formatPercentDirect, formatBytes } from '../utils/formatters'

const props = withDefaults(
  defineProps<{
    data: HostMetricsEnhanced[]
    loading?: boolean
  }>(),
  {
    loading: false
  }
)
</script>

<template>
  <el-card>
    <template #header>
      <div class="flex items-center justify-between">
        <span class="inline-flex items-center gap-1 ui-card-title">
          {{ $t('monitor.hostOverview') }}
          <el-tooltip :content="$t('monitor.hostOverviewDesc')" placement="top">
            <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
          </el-tooltip>
        </span>
      </div>
    </template>

    <el-table :data="data" size="small" :loading="loading" stripe>
      <el-table-column prop="host" :label="$t('monitor.host')" min-width="160" fixed />

      <el-table-column prop="region" :label="$t('monitor.belongRegion')" min-width="100" />

      <el-table-column prop="cpuUsage" label="CPU" min-width="90" align="right">
        <template #default="{ row }">{{ formatPercentDirect(row.cpuUsage) }}</template>
      </el-table-column>

      <el-table-column prop="memoryUsage" :label="$t('monitor.memory')" min-width="90" align="right">
        <template #default="{ row }">{{ formatPercentDirect(row.memoryUsage) }}</template>
      </el-table-column>

      <el-table-column :label="$t('monitor.networkIn')" min-width="110" align="right">
        <template #default="{ row }">{{ formatBytes(row.networkInBytes) }}</template>
      </el-table-column>

      <el-table-column :label="$t('monitor.networkOut')" min-width="110" align="right">
        <template #default="{ row }">{{ formatBytes(row.networkOutBytes) }}</template>
      </el-table-column>

      <el-table-column :label="$t('monitor.hostInstanceCount')" min-width="80" align="right">
        <template #default="{ row }">{{ row.instanceCount }}</template>
      </el-table-column>
    </el-table>

    <div v-if="!loading && data.length === 0" class="text-center text-gray-400 py-4">
      {{ $t('monitor.noDataAvailable') }}
    </div>
  </el-card>
</template>
