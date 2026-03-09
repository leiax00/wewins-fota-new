<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { monitorApi, controlApi, type RealtimeMetrics, type ControlParameter } from '@/api/monitor'
import { ElMessage } from 'element-plus'

const { t } = useI18n()

const metrics = ref<RealtimeMetrics | null>(null)
const controlParam = ref<ControlParameter | null>(null)
const loading = ref(false)
const refreshInterval = ref<number | null>(null)

const loadLevelColor = computed(() => {
  if (!metrics.value) return 'info'
  switch (metrics.value.loadLevel) {
    case 'LOW': return 'success'
    case 'NORMAL': return 'info'
    case 'HIGH': return 'warning'
    case 'CRITICAL': return 'danger'
    default: return 'info'
  }
})

const loadLevelText = computed(() => {
  if (!metrics.value) return '-'
  const levelMap: Record<string, string> = {
    'LOW': t('monitor.levelLow'),
    'NORMAL': t('monitor.levelNormal'),
    'HIGH': t('monitor.levelHigh'),
    'CRITICAL': t('monitor.levelCritical'),
  }
  return levelMap[metrics.value.loadLevel] || metrics.value.loadLevel
})

const loadScoreColor = computed(() => {
  if (!metrics.value) return '#67C23A'
  const score = metrics.value.loadScore
  if (score < 25) return '#67C23A'
  if (score < 50) return '#409EFF'
  if (score < 75) return '#E6A23C'
  return '#F56C6C'
})

const fetchMetrics = async () => {
  try {
    metrics.value = await monitorApi.getRealtimeMetrics()
  } catch (e) {
    console.error('Failed to fetch metrics', e)
  }
}

const fetchControlParam = async () => {
  try {
    controlParam.value = await controlApi.getGlobalConfig()
  } catch (e) {
    console.error('Failed to fetch control param', e)
  }
}

const updateControlParam = async () => {
  if (!controlParam.value) return
  try {
    loading.value = true
    await controlApi.updateGlobalConfig({
      checkIntervalMultiplier: controlParam.value.checkIntervalMultiplier,
      downloadDelayMultiplier: controlParam.value.downloadDelayMultiplier,
      forceMaintenance: controlParam.value.forceMaintenance,
      maintenanceMessage: controlParam.value.maintenanceMessage,
    })
    ElMessage.success(t('common.saveSuccess'))
    fetchControlParam()
  } catch (e) {
    ElMessage.error(t('common.saveFailed'))
  } finally {
    loading.value = false
  }
}

const startAutoRefresh = () => {
  refreshInterval.value = window.setInterval(fetchMetrics, 5000)
}

const stopAutoRefresh = () => {
  if (refreshInterval.value) {
    clearInterval(refreshInterval.value)
    refreshInterval.value = null
  }
}

onMounted(() => {
  fetchMetrics()
  fetchControlParam()
  startAutoRefresh()
})

onUnmounted(() => {
  stopAutoRefresh()
})
</script>

<template>
  <div>
    <h1 class="ui-page-title">
      {{ t('menu.monitor') }}
    </h1>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-4 mb-6">
      <el-card>
        <template #header>
          <div class="flex items-center justify-between">
            <span class="ui-card-title">{{ t('monitor.loadScore') }}</span>
            <el-tag :type="loadLevelColor" size="small">{{ loadLevelText }}</el-tag>
          </div>
        </template>
        <div class="text-center">
          <div class="text-5xl font-bold mb-4" :style="{ color: loadScoreColor }">
            {{ metrics?.loadScore ?? '-' }}
          </div>
          <el-progress
            :percentage="metrics?.loadScore ?? 0"
            :color="loadScoreColor"
            :stroke-width="10"
            :show-text="false"
          />
          <div class="text-sm text-gray-500 mt-2">/ 100</div>
        </div>
      </el-card>

      <el-card>
        <template #header>
          <span class="ui-card-title">{{ t('monitor.resourceUsage') }}</span>
        </template>
        <div class="space-y-4">
          <div>
            <div class="flex justify-between text-sm mb-1">
              <span>CPU</span>
              <span>{{ (metrics?.cpuUsage ?? 0).toFixed(1) }}%</span>
            </div>
            <el-progress :percentage="metrics?.cpuUsage ?? 0" :stroke-width="8" />
          </div>
          <div>
            <div class="flex justify-between text-sm mb-1">
              <span>{{ t('monitor.memory') }}</span>
              <span>{{ (metrics?.memoryUsage ?? 0).toFixed(1) }}%</span>
            </div>
            <el-progress :percentage="metrics?.memoryUsage ?? 0" :stroke-width="8" />
          </div>
        </div>
      </el-card>

      <el-card>
        <template #header>
          <span class="ui-card-title">{{ t('monitor.apiMetrics') }}</span>
        </template>
        <div class="grid grid-cols-2 gap-4">
          <div class="text-center">
            <div class="text-2xl font-bold text-blue-500">{{ (metrics?.currentQps ?? 0).toFixed(0) }}</div>
            <div class="text-sm text-gray-500">QPS</div>
          </div>
          <div class="text-center">
            <div class="text-2xl font-bold text-green-500">{{ (metrics?.p99Latency ?? 0).toFixed(0) }} ms</div>
            <div class="text-sm text-gray-500">P99 {{ t('monitor.latency') }}</div>
          </div>
          <div class="text-center">
            <div class="text-2xl font-bold text-orange-500">{{ ((metrics?.blockRate ?? 0) * 100).toFixed(2) }}%</div>
            <div class="text-sm text-gray-500">{{ t('monitor.blockRate') }}</div>
          </div>
          <div class="text-center">
            <el-tag :type="metrics?.circuitState === 'CLOSED' ? 'success' : 'danger'">
              {{ metrics?.circuitState ?? '-' }}
            </el-tag>
            <div class="text-sm text-gray-500 mt-1">{{ t('monitor.circuitState') }}</div>
          </div>
        </div>
      </el-card>
    </div>

    <el-card>
      <template #header>
        <span class="ui-card-title">{{ t('monitor.controlParams') }}</span>
      </template>
      <el-form
        v-if="controlParam"
        :model="controlParam"
        label-width="180px"
        class="max-w-xl"
      >
        <el-form-item :label="t('monitor.checkIntervalMultiplier')">
          <el-input-number
            v-model="controlParam.checkIntervalMultiplier"
            :min="0.1"
            :max="10"
            :step="0.1"
            :precision="1"
          />
        </el-form-item>
        <el-form-item :label="t('monitor.downloadDelayMultiplier')">
          <el-input-number
            v-model="controlParam.downloadDelayMultiplier"
            :min="0.1"
            :max="10"
            :step="0.1"
            :precision="1"
          />
        </el-form-item>
        <el-form-item :label="t('monitor.forceMaintenance')">
          <el-switch v-model="controlParam.forceMaintenance" />
        </el-form-item>
        <el-form-item
          v-if="controlParam.forceMaintenance"
          :label="t('monitor.maintenanceMessage')"
        >
          <el-input
            v-model="controlParam.maintenanceMessage"
            type="textarea"
            :rows="2"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="updateControlParam">
            {{ t('common.save') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>
