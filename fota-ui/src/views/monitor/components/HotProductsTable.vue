<script setup lang="ts">
import { InfoFilled } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import type { HotProductMetricsEnhanced } from '@/api/monitor'
import { formatPercent, formatQps } from '../utils/formatters'

const props = withDefaults(
  defineProps<{
    data: HotProductMetricsEnhanced[]
    loading?: boolean
  }>(),
  {
    loading: false
  }
)

const { t } = useI18n()
</script>

<template>
  <el-card>
    <template #header>
      <div class="flex items-center justify-between">
        <span class="inline-flex items-center gap-1 ui-card-title">
          {{ t('monitor.hotProducts') }}
          <el-tooltip :content="t('monitor.hotProductsDesc')" placement="top">
            <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
          </el-tooltip>
        </span>
      </div>
    </template>

    <el-table :data="data" size="small" :loading="loading" stripe>
      <el-table-column prop="product" :label="t('product.model')" min-width="140" />

      <el-table-column label="Check QPS" min-width="100" align="right">
        <template #default="{ row }">{{ formatQps(row.checkQps) }}</template>
      </el-table-column>

      <el-table-column label="Report QPS" min-width="100" align="right">
        <template #default="{ row }">{{ formatQps(row.reportQps) }}</template>
      </el-table-column>

      <el-table-column :label="t('monitor.trafficShare')" min-width="100" align="right">
        <template #default="{ row }">{{ formatPercent(row.trafficShare) }}</template>
      </el-table-column>

      <el-table-column :label="t('monitor.activeRegionCount')" min-width="100" align="right">
        <template #default="{ row }">{{ row.activeRegionCount }}</template>
      </el-table-column>
    </el-table>

    <div v-if="!loading && data.length === 0" class="text-center text-gray-400 py-4">
      {{ t('monitor.noDataAvailable') }}
    </div>
  </el-card>
</template>
