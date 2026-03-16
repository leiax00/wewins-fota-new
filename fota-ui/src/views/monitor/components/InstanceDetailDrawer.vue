<script setup lang="ts">
import { Loading } from '@element-plus/icons-vue'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { InstanceDetail, InstanceMetricsEnhanced } from '@/api/monitor'
import { formatPercent, formatQps, formatLatency } from '../utils/formatters'
import { useLoadLevel, useCircuitState } from '../composables/useLoadLevel'

const props = defineProps<{
  visible: boolean
  instance: InstanceMetricsEnhanced | null
  detail: InstanceDetail | null
  loading?: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const { t } = useI18n()

const drawerVisible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value),
})

const { loadLevelColor, loadLevelText } = useLoadLevel(() => props.instance?.loadLevel)
const { circuitStateColor } = useCircuitState(() => props.instance?.circuitState)

const handleClose = () => {
  drawerVisible.value = false
}
</script>

<template>
  <el-drawer
    v-model="drawerVisible"
    :title="`${t('monitor.instanceDetail')} - ${instance?.instance ?? '-'}`"
    size="60%"
    @close="handleClose"
  >
    <div v-if="loading" class="flex items-center justify-center py-8">
      <el-icon class="is-loading"><Loading /></el-icon>
    </div>

    <div v-else-if="instance" class="space-y-4">
      <!-- 实例摘要卡片 -->
      <el-card>
        <template #header>
          <span class="ui-card-title">{{ t('monitor.globalSummary') }}</span>
        </template>

        <div class="grid grid-cols-2 gap-4 md:grid-cols-4">
          <div class="summary-item">
            <div class="summary-label">{{ t('monitor.loadScoreLevel') }}</div>
            <div class="summary-value flex items-center justify-center gap-1">
              <span class="font-semibold">{{ instance.loadScore }}</span>
              <el-tag :type="loadLevelColor" size="small">{{ loadLevelText }}</el-tag>
            </div>
          </div>

          <div class="summary-item">
            <div class="summary-label">Check / Report QPS</div>
            <div class="summary-value">
              <div>{{ formatQps(instance.checkQps) }}</div>
              <div class="text-sm text-gray-500">{{ formatQps(instance.reportQps) }}</div>
            </div>
          </div>

          <div class="summary-item">
            <div class="summary-label">Check / Report P99</div>
            <div class="summary-value">
              <div>{{ formatLatency(instance.checkP99Latency) }}</div>
              <div class="text-sm text-gray-500">{{ formatLatency(instance.reportP99Latency) }}</div>
            </div>
          </div>

          <div class="summary-item">
            <div class="summary-label">{{ t('monitor.activeRequests') }}</div>
            <div class="summary-value">{{ instance.activeRequests }}</div>
          </div>

          <div class="summary-item">
            <div class="summary-label">{{ t('monitor.blockRate') }}</div>
            <div class="summary-value">{{ formatPercent(instance.blockRate) }}</div>
          </div>

          <div class="summary-item">
            <div class="summary-label">{{ t('monitor.circuitState') }}</div>
            <div class="summary-value">
              <el-tag :type="circuitStateColor" size="small">{{ instance.circuitState }}</el-tag>
            </div>
          </div>

          <div class="summary-item">
            <div class="summary-label">JVM CPU / {{ t('monitor.memory') }}</div>
            <div class="summary-value">
              <div>{{ formatPercent(instance.cpuUsage) }}</div>
              <div class="text-sm text-gray-500">{{ formatPercent(instance.memoryUsage) }}</div>
            </div>
          </div>

          <div class="summary-item">
            <div class="summary-label">Host CPU / {{ t('monitor.memory') }}</div>
            <div class="summary-value">
              <div>{{ formatPercent(detail?.hostCpuUsage ?? 0) }}</div>
              <div class="text-sm text-gray-500">{{ formatPercent(detail?.hostMemoryUsage ?? 0) }}</div>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 归属信息 -->
      <el-card>
        <template #header>
          <span class="ui-card-title">{{ t('common.detail') }}</span>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('monitor.belongRegion')">
            {{ instance.region }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('monitor.belongHost')">
            {{ instance.host }}
          </el-descriptions-item>
          <el-descriptions-item label="Instance">
            {{ instance.instance }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('monitor.lastRefreshTime')">
            {{ detail?.lastRefreshTime ?? '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 热点产品 Top N -->
      <el-card>
        <template #header>
          <span class="ui-card-title">{{ t('monitor.hotProducts') }}</span>
        </template>
        <el-table :data="detail?.hotProducts ?? []" size="small" stripe>
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
        </el-table>
      </el-card>
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
