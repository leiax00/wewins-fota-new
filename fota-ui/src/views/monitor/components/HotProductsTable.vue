<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { HotProductMetrics, HotProductMetricsEnhanced } from '@/api/monitor'
import { formatPercent, formatQps } from '../utils/formatters'

// 使用联合类型支持两种数据结构
type HotProductData = HotProductMetrics | HotProductMetricsEnhanced

const props = withDefaults(
  defineProps<{
    data: HotProductData[]
    loading?: boolean
    showActiveRegionCount?: boolean // 是否显示活跃区域数列
  }>(),
  {
    loading: false,
    showActiveRegionCount: true
  }
)

const { t } = useI18n()

// 检查数据是否有 activeRegionCount 属性
const hasActiveRegionCount = (row: HotProductData): row is HotProductMetricsEnhanced => {
  return 'activeRegionCount' in row
}
</script>

<template>
  <el-card>
    <template #header>
      <div class="flex items-center justify-between gap-2">
        <span class="ui-card-title">{{ t('monitor.hotProducts') }}</span>
        <span class="text-xs text-slate-400">{{ t('monitor.hotProductsDesc') }}</span>
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

      <el-table-column v-if="showActiveRegionCount" :label="t('monitor.activeRegionCount')" min-width="100" align="right">
        <template #default="{ row }">{{ hasActiveRegionCount(row) ? row.activeRegionCount : '-' }}</template>
      </el-table-column>
    </el-table>

    <div v-if="!loading && data.length === 0" class="text-center text-gray-400 py-4">
      {{ t('monitor.noDataAvailable') }}
    </div>
  </el-card>
</template>
