<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { controlApi, monitorApi, type ControlParameter, type RealtimeMetrics } from '@/api/monitor'

const { t } = useI18n()

const metrics = ref<RealtimeMetrics | null>(null)
const controlParam = ref<ControlParameter | null>(null)
const loading = ref(false)
const refreshTimer = ref<number | null>(null)

const loadLevelColor = computed(() => {
  switch (metrics.value?.loadLevel) {
    case 'LOW':
      return 'success'
    case 'NORMAL':
      return 'info'
    case 'HIGH':
      return 'warning'
    case 'CRITICAL':
      return 'danger'
    default:
      return 'info'
  }
})

const loadLevelText = computed(() => {
  const value = metrics.value?.loadLevel
  if (!value) return '-'
  const mapping: Record<string, string> = {
    LOW: t('monitor.levelLow'),
    NORMAL: t('monitor.levelNormal'),
    HIGH: t('monitor.levelHigh'),
    CRITICAL: t('monitor.levelCritical'),
  }
  return mapping[value] ?? value
})

const priorityOptions = [
  { label: 'CRITICAL', value: 'CRITICAL' },
  { label: 'HIGH', value: 'HIGH' },
  { label: 'NORMAL', value: 'NORMAL' },
  { label: 'LOW', value: 'LOW' },
]

const fetchMetrics = async () => {
  metrics.value = await monitorApi.getRealtimeMetrics()
}

const fetchControlParam = async () => {
  controlParam.value = await controlApi.getGlobalConfig()
}

const updateControlParam = async () => {
  if (!controlParam.value) return
  loading.value = true
  try {
    await controlApi.updateGlobalConfig({
      protectedCheckIntervalSeconds: controlParam.value.protectedCheckIntervalSeconds,
      checkIntervalMultiplier: controlParam.value.checkIntervalMultiplier,
      protectedIntervalMultiplier: controlParam.value.protectedIntervalMultiplier,
      downloadDelayMultiplier: controlParam.value.downloadDelayMultiplier,
      intervalBias: controlParam.value.intervalBias,
      minCheckIntervalSeconds: controlParam.value.minCheckIntervalSeconds,
      maxCheckIntervalSeconds: controlParam.value.maxCheckIntervalSeconds,
      priority: controlParam.value.priority,
      hotspotProtectionEnabled: controlParam.value.hotspotProtectionEnabled,
      forceMaintenance: controlParam.value.forceMaintenance,
      maintenanceMessage: controlParam.value.maintenanceMessage,
    })
    ElMessage.success(t('common.updateSuccess'))
    await fetchControlParam()
  } catch {
    ElMessage.error(t('common.failed'))
  } finally {
    loading.value = false
  }
}

const startRefresh = () => {
  refreshTimer.value = window.setInterval(fetchMetrics, 5000)
}

const stopRefresh = () => {
  if (refreshTimer.value != null) {
    window.clearInterval(refreshTimer.value)
    refreshTimer.value = null
  }
}

const formatPercent = (value?: number) => `${(value ?? 0).toFixed(1)}%`

const formatQps = (value?: number) => `${(value ?? 0).toFixed(1)}`

const formatBytes = (value?: number) => {
  const bytes = value ?? 0
  if (bytes < 1024) return `${bytes.toFixed(0)} B/s`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB/s`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB/s`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(1)} GB/s`
}

const formatSeconds = (value?: number) => {
  const seconds = value ?? 0
  if (seconds < 60) return `${seconds}s`
  if (seconds < 3600) return `${(seconds / 60).toFixed(0)}m`
  if (seconds < 86400) return `${(seconds / 3600).toFixed(1)}h`
  return `${(seconds / 86400).toFixed(1)}d`
}

onMounted(async () => {
  await Promise.all([fetchMetrics(), fetchControlParam()])
  startRefresh()
})

onUnmounted(() => {
  stopRefresh()
})
</script>

<template>
  <div>
    <h1 class="ui-page-title">{{ t('menu.monitor') }}</h1>

    <div class="grid grid-cols-1 xl:grid-cols-4 gap-4 mb-6">
      <el-card>
        <template #header>
          <div class="flex items-center justify-between">
            <span class="ui-card-title">{{ t('monitor.loadScore') }}</span>
            <el-tag :type="loadLevelColor" size="small">{{ loadLevelText }}</el-tag>
          </div>
        </template>
        <div class="text-4xl font-bold text-center">{{ metrics?.loadScore ?? '-' }}</div>
        <div class="text-sm text-gray-500 text-center mt-2">{{ metrics?.region ?? '-' }}</div>
      </el-card>

      <el-card>
        <template #header>
          <span class="ui-card-title">{{ t('monitor.apiMetrics') }}</span>
        </template>
        <div class="space-y-2 text-sm">
          <div class="flex justify-between"><span>Total QPS</span><strong>{{ formatQps(metrics?.currentQps) }}</strong></div>
          <div class="flex justify-between"><span>Check QPS</span><strong>{{ formatQps(metrics?.checkQps) }}</strong></div>
          <div class="flex justify-between"><span>Report QPS</span><strong>{{ formatQps(metrics?.reportQps) }}</strong></div>
          <div class="flex justify-between"><span>P99</span><strong>{{ (metrics?.p99Latency ?? 0).toFixed(0) }} ms</strong></div>
        </div>
      </el-card>

      <el-card>
        <template #header>
          <span class="ui-card-title">{{ t('monitor.resourceUsage') }}</span>
        </template>
        <div class="space-y-2 text-sm">
          <div class="flex justify-between"><span>JVM CPU</span><strong>{{ formatPercent(metrics?.cpuUsage) }}</strong></div>
          <div class="flex justify-between"><span>JVM {{ t('monitor.memory') }}</span><strong>{{ formatPercent(metrics?.memoryUsage) }}</strong></div>
          <div class="flex justify-between"><span>Host CPU</span><strong>{{ formatPercent(metrics?.hostCpuUsage) }}</strong></div>
          <div class="flex justify-between"><span>Host {{ t('monitor.memory') }}</span><strong>{{ formatPercent(metrics?.hostMemoryUsage) }}</strong></div>
        </div>
      </el-card>

      <el-card>
        <template #header>
          <span class="ui-card-title">{{ t('monitor.controlState') }}</span>
        </template>
        <div class="space-y-2 text-sm">
          <div class="flex justify-between"><span>{{ t('monitor.protectedCheckIntervalSeconds') }}</span><strong>{{ formatSeconds(controlParam?.protectedCheckIntervalSeconds) }}</strong></div>
          <div class="flex justify-between"><span>{{ t('monitor.blockRate') }}</span><strong>{{ formatPercent((metrics?.blockRate ?? 0) * 100) }}</strong></div>
          <div class="flex justify-between"><span>{{ t('monitor.circuitState') }}</span><strong>{{ metrics?.circuitState ?? '-' }}</strong></div>
          <div class="flex justify-between"><span>{{ t('monitor.recommendedMultiplier') }}</span><strong>{{ metrics?.controlState?.recommendedMultiplier?.toFixed(2) ?? '-' }}</strong></div>
          <div class="flex justify-between"><span>{{ t('monitor.activeRequests') }}</span><strong>{{ metrics?.activeRequests ?? 0 }}</strong></div>
        </div>
      </el-card>
    </div>

    <div class="grid grid-cols-1 xl:grid-cols-2 gap-4 mb-6">
      <el-card>
        <template #header>
          <span class="ui-card-title">{{ t('monitor.hostOverview') }}</span>
        </template>
        <el-table :data="metrics?.hosts ?? []" size="small">
          <el-table-column prop="host" :label="t('monitor.host')" min-width="160" />
          <el-table-column :label="'CPU'">
            <template #default="{ row }">{{ formatPercent(row.cpuUsage) }}</template>
          </el-table-column>
          <el-table-column :label="t('monitor.memory')">
            <template #default="{ row }">{{ formatPercent(row.memoryUsage) }}</template>
          </el-table-column>
          <el-table-column :label="t('monitor.networkIn')">
            <template #default="{ row }">{{ formatBytes(row.networkInBytes) }}</template>
          </el-table-column>
          <el-table-column :label="t('monitor.networkOut')">
            <template #default="{ row }">{{ formatBytes(row.networkOutBytes) }}</template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card>
        <template #header>
          <span class="ui-card-title">{{ t('monitor.nodeOverview') }}</span>
        </template>
        <el-table :data="metrics?.instances ?? []" size="small">
          <el-table-column prop="instance" :label="t('monitor.node')" min-width="160" />
          <el-table-column :label="'CPU'">
            <template #default="{ row }">{{ formatPercent(row.cpuUsage) }}</template>
          </el-table-column>
          <el-table-column :label="t('monitor.memory')">
            <template #default="{ row }">{{ formatPercent(row.memoryUsage) }}</template>
          </el-table-column>
          <el-table-column label="Check QPS">
            <template #default="{ row }">{{ formatQps(row.checkQps) }}</template>
          </el-table-column>
          <el-table-column label="Report QPS">
            <template #default="{ row }">{{ formatQps(row.reportQps) }}</template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <div class="grid grid-cols-1 xl:grid-cols-2 gap-4">
      <el-card>
        <template #header>
          <span class="ui-card-title">{{ t('monitor.hotProducts') }}</span>
        </template>
        <el-table :data="metrics?.hotProducts ?? []" size="small">
          <el-table-column prop="product" :label="t('product.model')" min-width="140" />
          <el-table-column label="Check QPS">
            <template #default="{ row }">{{ formatQps(row.checkQps) }}</template>
          </el-table-column>
          <el-table-column label="Report QPS">
            <template #default="{ row }">{{ formatQps(row.reportQps) }}</template>
          </el-table-column>
          <el-table-column :label="t('monitor.trafficShare')">
            <template #default="{ row }">{{ formatPercent(row.trafficShare * 100) }}</template>
          </el-table-column>
          <el-table-column :label="t('policy.priority')">
            <template #default="{ row }">{{ row.priority }}</template>
          </el-table-column>
          <el-table-column :label="t('monitor.intervalBias')">
            <template #default="{ row }">{{ row.intervalBias.toFixed(2) }}</template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card>
        <template #header>
          <span class="ui-card-title">{{ t('monitor.controlParams') }}</span>
        </template>
        <el-form v-if="controlParam" :model="controlParam" label-width="180px">
          <el-form-item :label="t('monitor.protectedCheckIntervalSeconds')">
            <el-input-number v-model="controlParam.protectedCheckIntervalSeconds" :min="60" :max="604800" :step="300" />
            <div class="text-xs text-gray-500 mt-1">{{ t('monitor.protectedCheckIntervalSecondsDesc') }}</div>
          </el-form-item>
          <el-form-item :label="t('monitor.checkIntervalMultiplier')">
            <el-input-number v-model="controlParam.checkIntervalMultiplier" :min="0.1" :max="10" :step="0.1" :precision="2" />
            <div class="text-xs text-gray-500 mt-1">{{ t('monitor.checkIntervalMultiplierDesc') }}</div>
          </el-form-item>
          <el-form-item :label="t('monitor.protectedIntervalMultiplier')">
            <el-input-number v-model="controlParam.protectedIntervalMultiplier" :min="1" :max="10" :step="0.1" :precision="2" />
            <div class="text-xs text-gray-500 mt-1">{{ t('monitor.protectedIntervalMultiplierDesc') }}</div>
          </el-form-item>
          <el-form-item :label="t('monitor.downloadDelayMultiplier')">
            <el-input-number v-model="controlParam.downloadDelayMultiplier" :min="0.1" :max="10" :step="0.1" :precision="2" />
            <div class="text-xs text-gray-500 mt-1">{{ t('monitor.downloadDelayMultiplierDesc') }}</div>
          </el-form-item>
          <el-form-item :label="t('monitor.intervalBias')">
            <el-input-number v-model="controlParam.intervalBias" :min="0.1" :max="5" :step="0.1" :precision="2" />
            <div class="text-xs text-gray-500 mt-1">{{ t('monitor.intervalBiasDesc') }}</div>
          </el-form-item>
          <el-form-item :label="t('monitor.minCheckIntervalSeconds')">
            <el-input-number v-model="controlParam.minCheckIntervalSeconds" :min="60" :max="172800" :step="60" />
            <div class="text-xs text-gray-500 mt-1">{{ t('monitor.minCheckIntervalSecondsDesc') }}</div>
          </el-form-item>
          <el-form-item :label="t('monitor.maxCheckIntervalSeconds')">
            <el-input-number v-model="controlParam.maxCheckIntervalSeconds" :min="60" :max="604800" :step="300" />
            <div class="text-xs text-gray-500 mt-1">{{ t('monitor.maxCheckIntervalSecondsDesc') }}</div>
          </el-form-item>
          <el-form-item :label="t('policy.priority')">
            <el-select v-model="controlParam.priority" class="w-full">
              <el-option v-for="option in priorityOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
            <div class="text-xs text-gray-500 mt-1">{{ t('monitor.priorityDesc') }}</div>
          </el-form-item>
          <el-form-item :label="t('monitor.hotspotProtectionEnabled')">
            <el-switch v-model="controlParam.hotspotProtectionEnabled" />
            <div class="text-xs text-gray-500 mt-1">{{ t('monitor.hotspotProtectionEnabledDesc') }}</div>
          </el-form-item>
          <el-form-item :label="t('monitor.forceMaintenance')">
            <el-switch v-model="controlParam.forceMaintenance" />
            <div class="text-xs text-gray-500 mt-1">{{ t('monitor.forceMaintenanceDesc') }}</div>
          </el-form-item>
          <el-form-item v-if="controlParam.forceMaintenance" :label="t('monitor.maintenanceMessage')">
            <el-input v-model="controlParam.maintenanceMessage" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" @click="updateControlParam">
              {{ t('common.save') }}
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </div>
</template>
