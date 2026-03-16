<script setup lang="ts">
import { InfoFilled } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import type { GlobalSummary } from '@/api/monitor'
import { formatPercent, formatQps } from '../utils/formatters'
import { useLoadLevel } from '../composables/useLoadLevel'

const props = defineProps<{
  summary: GlobalSummary | null
}>()

const { t } = useI18n()

const { loadLevelColor, loadLevelText } = useLoadLevel(() => props.summary?.loadLevel)
</script>

<template>
  <el-card class="system-summary-card">
    <template #header>
      <div class="flex items-center justify-between">
        <span class="inline-flex items-center gap-1 ui-card-title">
          {{ t('monitor.globalSummary') }}
          <el-tooltip :content="t('monitor.globalSummaryDesc')" placement="top">
            <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
          </el-tooltip>
        </span>
        <el-tag :type="loadLevelColor" size="small">{{ loadLevelText }}</el-tag>
      </div>
    </template>

    <div class="grid grid-cols-1 gap-4 md:grid-cols-3 lg:grid-cols-5">
      <!-- 负载分/等级 -->
      <div class="summary-item">
        <div class="summary-label">{{ t('monitor.loadScore') }}</div>
        <div class="summary-value text-3xl font-bold">{{ summary?.loadScore ?? '-' }}</div>
      </div>

      <!-- QPS -->
      <div class="summary-item">
        <div class="summary-label">Check / Report QPS</div>
        <div class="summary-value text-lg">
          <div class="font-semibold">{{ formatQps(summary?.checkQps) }}</div>
          <div class="text-sm text-gray-500">{{ formatQps(summary?.reportQps) }}</div>
        </div>
      </div>

      <!-- 延迟 -->
      <div class="summary-item">
        <div class="summary-label">Check P50 / P99</div>
        <div class="summary-value text-lg">
          <div class="font-semibold">{{ summary?.checkP50Latency?.toFixed(0) ?? '-' }} ms</div>
          <div class="text-sm text-gray-500">{{ summary?.checkP99Latency?.toFixed(0) ?? '-' }} ms</div>
        </div>
      </div>

      <!-- 活跃设备 -->
      <div class="summary-item">
        <div class="summary-label">{{ t('dashboard.todayActiveDevices') }}</div>
        <div class="summary-value text-3xl font-bold">
          {{ summary?.todayActiveDevices?.toLocaleString() ?? '-' }}
        </div>
      </div>

      <!-- 限流率 -->
      <div class="summary-item">
        <div class="summary-label">{{ t('monitor.blockRate') }}</div>
        <div class="summary-value text-3xl font-bold">
          {{ formatPercent(summary?.blockRate) }}
        </div>
      </div>
    </div>
  </el-card>
</template>

<style scoped>
.system-summary-card :deep(.el-card__header) {
  padding: 12px 16px;
}

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
  color: var(--el-text-color-primary);
}
</style>
