<script setup lang="ts">
import { InfoFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { controlApi, monitorApi, type ControlParameter, type RealtimeMetrics } from '@/api/monitor'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const userStore = useUserStore()

const metrics = ref<RealtimeMetrics | null>(null)
const controlParam = ref<ControlParameter | null>(null)
const loading = ref(false)
const refreshTimer = ref<number | null>(null)

type DurationUnit = 'minutes' | 'hours' | 'days'
type DurationFieldKey = 'minCheckIntervalSeconds' | 'maxCheckIntervalSeconds'

const DURATION_UNIT_SECONDS: Record<DurationUnit, number> = {
  minutes: 60,
  hours: 60 * 60,
  days: 24 * 60 * 60,
}

const durationFields = reactive<Record<DurationFieldKey, { value: number; unit: DurationUnit }>>({
  minCheckIntervalSeconds: { value: 30, unit: 'minutes' },
  maxCheckIntervalSeconds: { value: 2, unit: 'days' },
})

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

const canUpdateLoadControl = computed(() => userStore.hasPermission('monitor:load:update'))

const fetchMetrics = async () => {
  metrics.value = await monitorApi.getRealtimeMetrics()
}

const setDurationField = (field: DurationFieldKey, seconds?: number) => {
  const value = seconds ?? 0
  if (value > 0 && value % DURATION_UNIT_SECONDS.days === 0) {
    durationFields[field].unit = 'days'
    durationFields[field].value = value / DURATION_UNIT_SECONDS.days
    return
  }
  if (value > 0 && value % DURATION_UNIT_SECONDS.hours === 0) {
    durationFields[field].unit = 'hours'
    durationFields[field].value = value / DURATION_UNIT_SECONDS.hours
    return
  }
  durationFields[field].unit = 'minutes'
  durationFields[field].value = Math.max(1, Math.round(value / DURATION_UNIT_SECONDS.minutes))
}

const getDurationSeconds = (field: DurationFieldKey) => {
  const { value, unit } = durationFields[field]
  return Math.max(60, Math.round(value * DURATION_UNIT_SECONDS[unit]))
}

const syncDurationFieldsFromControl = () => {
  if (!controlParam.value) return
  setDurationField('minCheckIntervalSeconds', controlParam.value.minCheckIntervalSeconds)
  setDurationField('maxCheckIntervalSeconds', controlParam.value.maxCheckIntervalSeconds)
}

const syncControlFromDurationFields = () => {
  if (!controlParam.value) return
  controlParam.value.minCheckIntervalSeconds = getDurationSeconds('minCheckIntervalSeconds')
  controlParam.value.maxCheckIntervalSeconds = getDurationSeconds('maxCheckIntervalSeconds')
}

const fetchControlParam = async () => {
  controlParam.value = await controlApi.getGlobalConfig()
  syncDurationFieldsFromControl()
}

const updateControlParam = async () => {
  if (!controlParam.value) return
  syncControlFromDurationFields()
  loading.value = true
  try {
    await controlApi.updateGlobalConfig({
      protectedIntervalMultiplier: controlParam.value.protectedIntervalMultiplier,
      downloadDelayMultiplier: controlParam.value.downloadDelayMultiplier,
      minCheckIntervalSeconds: controlParam.value.minCheckIntervalSeconds,
      maxCheckIntervalSeconds: controlParam.value.maxCheckIntervalSeconds,
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

const getDurationMax = (field: DurationFieldKey) => {
  const unit = durationFields[field].unit
  if (unit === 'days') return 7
  if (unit === 'hours') return 24 * 7
  return 60 * 24 * 7
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
    <h1 class="ui-page-title">{{ t('menu.monitorLoad') }}</h1>

    <div class="grid grid-cols-1 gap-4 mb-6 xl:grid-cols-4">
      <el-card>
        <template #header>
          <div class="flex items-center justify-between">
            <span class="inline-flex items-center gap-1 ui-card-title">
              {{ t('monitor.loadScore') }}
              <el-tooltip :content="t('monitor.loadScoreDesc')" placement="top">
                <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
              </el-tooltip>
            </span>
            <el-tag :type="loadLevelColor" size="small">{{ loadLevelText }}</el-tag>
          </div>
        </template>
        <div class="text-4xl font-bold text-center">{{ metrics?.loadScore ?? '-' }}</div>
        <div class="mt-2 text-sm text-center text-gray-500">{{ metrics?.region ?? '-' }}</div>
      </el-card>

      <el-card>
        <template #header>
          <span class="inline-flex items-center gap-1 ui-card-title">
            {{ t('monitor.apiMetrics') }}
            <el-tooltip :content="t('monitor.apiMetricsDesc')" placement="top">
              <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
            </el-tooltip>
          </span>
        </template>
        <div class="space-y-2 text-sm">
          <div class="flex justify-between"><span>Check QPS</span><strong>{{ formatQps(metrics?.checkQps) }}</strong></div>
          <div class="flex justify-between"><span>Report QPS</span><strong>{{ formatQps(metrics?.reportQps) }}</strong></div>
          <div class="flex justify-between"><span>Check P50</span><strong>{{ (metrics?.checkP50Latency ?? 0).toFixed(0) }} ms</strong></div>
          <div class="flex justify-between"><span>Check P99</span><strong>{{ (metrics?.checkP99Latency ?? 0).toFixed(0) }} ms</strong></div>
          <div class="flex justify-between"><span>Report P50</span><strong>{{ (metrics?.reportP50Latency ?? 0).toFixed(0) }} ms</strong></div>
          <div class="flex justify-between"><span>Report P99</span><strong>{{ (metrics?.reportP99Latency ?? 0).toFixed(0) }} ms</strong></div>
        </div>
      </el-card>

      <el-card>
        <template #header>
          <span class="inline-flex items-center gap-1 ui-card-title">
            {{ t('monitor.resourceUsage') }}
            <el-tooltip :content="t('monitor.resourceUsageDesc')" placement="top">
              <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
            </el-tooltip>
          </span>
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
          <span class="inline-flex items-center gap-1 ui-card-title">
            {{ t('monitor.controlState') }}
            <el-tooltip :content="t('monitor.controlStateDesc')" placement="top">
              <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
            </el-tooltip>
          </span>
        </template>
        <div class="space-y-2 text-sm">
          <div class="flex justify-between"><span>{{ t('monitor.protectedIntervalMultiplier') }}</span><strong>{{ controlParam?.protectedIntervalMultiplier?.toFixed(2) ?? '-' }}</strong></div>
          <div class="flex justify-between"><span>{{ t('monitor.blockRate') }}</span><strong>{{ formatPercent((metrics?.blockRate ?? 0) * 100) }}</strong></div>
          <div class="flex justify-between"><span>{{ t('monitor.circuitState') }}</span><strong>{{ metrics?.circuitState ?? '-' }}</strong></div>
          <div class="flex justify-between"><span>{{ t('monitor.recommendedMultiplier') }}</span><strong>{{ metrics?.controlState?.recommendedMultiplier?.toFixed(2) ?? '-' }}</strong></div>
          <div class="flex justify-between"><span>{{ t('monitor.activeRequests') }}</span><strong>{{ metrics?.activeRequests ?? 0 }}</strong></div>
        </div>
      </el-card>
    </div>

    <div class="grid grid-cols-1 gap-4 mb-6 xl:grid-cols-2">
      <el-card>
        <template #header>
          <span class="inline-flex items-center gap-1 ui-card-title">
            {{ t('monitor.hostOverview') }}
            <el-tooltip :content="t('monitor.hostOverviewDesc')" placement="top">
              <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
            </el-tooltip>
          </span>
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
          <span class="inline-flex items-center gap-1 ui-card-title">
            {{ t('monitor.nodeOverview') }}
            <el-tooltip :content="t('monitor.nodeOverviewDesc')" placement="top">
              <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
            </el-tooltip>
          </span>
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

    <div class="grid grid-cols-1 gap-4 xl:grid-cols-2">
      <el-card>
        <template #header>
          <span class="inline-flex items-center gap-1 ui-card-title">
            {{ t('monitor.hotProducts') }}
            <el-tooltip :content="t('monitor.hotProductsDesc')" placement="top">
              <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
            </el-tooltip>
          </span>
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
        </el-table>
      </el-card>

      <el-card>
        <template #header>
          <span class="inline-flex items-center gap-1 ui-card-title">
            {{ t('monitor.controlParams') }}
            <el-tooltip :content="t('monitor.controlParamsDesc')" placement="top">
              <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
            </el-tooltip>
          </span>
        </template>
        <el-form v-if="controlParam" :model="controlParam" label-width="180px">
          <el-form-item>
            <template #label>
              <span class="inline-flex items-center gap-1">
                {{ t('monitor.protectedIntervalMultiplier') }}
                <el-tooltip :content="t('monitor.protectedIntervalMultiplierDesc')" placement="top">
                  <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
                </el-tooltip>
              </span>
            </template>
            <el-input-number v-model="controlParam.protectedIntervalMultiplier" :min="1" :max="10" :step="0.1" :precision="2" />
          </el-form-item>

          <el-form-item>
            <template #label>
              <span class="inline-flex items-center gap-1">
                {{ t('monitor.downloadDelayMultiplier') }}
                <el-tooltip :content="t('monitor.downloadDelayMultiplierDesc')" placement="top">
                  <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
                </el-tooltip>
              </span>
            </template>
            <el-input-number v-model="controlParam.downloadDelayMultiplier" :min="0.1" :max="10" :step="0.1" :precision="2" />
          </el-form-item>

          <el-form-item>
            <template #label>
              <span class="inline-flex items-center gap-1">
                {{ t('monitor.minCheckIntervalSeconds') }}
                <el-tooltip :content="t('monitor.minCheckIntervalSecondsDesc')" placement="top">
                  <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
                </el-tooltip>
              </span>
            </template>
            <div class="w-full">
              <div class="flex w-full gap-3">
                <el-input-number
                  v-model="durationFields.minCheckIntervalSeconds.value"
                  :min="1"
                  :max="getDurationMax('minCheckIntervalSeconds')"
                  :step="1"
                  class="flex-1"
                />
                <el-select v-model="durationFields.minCheckIntervalSeconds.unit" style="width: 140px">
                  <el-option :label="t('product.periodUnitMinutes')" value="minutes" />
                  <el-option :label="t('product.periodUnitHours')" value="hours" />
                  <el-option :label="t('product.periodUnitDays')" value="days" />
                </el-select>
              </div>
              <div class="mt-2 text-xs text-slate-500">
                {{ t('monitor.durationValueHint', { value: formatSeconds(getDurationSeconds('minCheckIntervalSeconds')) }) }}
              </div>
            </div>
          </el-form-item>

          <el-form-item>
            <template #label>
              <span class="inline-flex items-center gap-1">
                {{ t('monitor.maxCheckIntervalSeconds') }}
                <el-tooltip :content="t('monitor.maxCheckIntervalSecondsDesc')" placement="top">
                  <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
                </el-tooltip>
              </span>
            </template>
            <div class="w-full">
              <div class="flex w-full gap-3">
                <el-input-number
                  v-model="durationFields.maxCheckIntervalSeconds.value"
                  :min="1"
                  :max="getDurationMax('maxCheckIntervalSeconds')"
                  :step="1"
                  class="flex-1"
                />
                <el-select v-model="durationFields.maxCheckIntervalSeconds.unit" style="width: 140px">
                  <el-option :label="t('product.periodUnitMinutes')" value="minutes" />
                  <el-option :label="t('product.periodUnitHours')" value="hours" />
                  <el-option :label="t('product.periodUnitDays')" value="days" />
                </el-select>
              </div>
              <div class="mt-2 text-xs text-slate-500">
                {{ t('monitor.durationValueHint', { value: formatSeconds(getDurationSeconds('maxCheckIntervalSeconds')) }) }}
              </div>
            </div>
          </el-form-item>

          <el-form-item v-if="canUpdateLoadControl">
            <el-button type="primary" :loading="loading" @click="updateControlParam">
              {{ t('common.save') }}
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </div>
</template>
