<script setup lang="ts">
import { InfoFilled } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import dayjs from 'dayjs'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { monitorApi, type MonitorTrends, type RealtimeMetrics, type TrendPoint } from '@/api/monitor'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const userStore = useUserStore()

const metrics = ref<RealtimeMetrics | null>(null)
const trends = ref<MonitorTrends | null>(null)
const trendLoading = ref(false)
const trendRange = ref<'15m' | '1h' | '6h' | '24h'>('15m')
const refreshTimer = ref<number | null>(null)
const trendRefreshTimer = ref<number | null>(null)

const checkTrendRef = ref<HTMLDivElement | null>(null)
const reportTrendRef = ref<HTMLDivElement | null>(null)
const latencyTrendRef = ref<HTMLDivElement | null>(null)
const blockTrendRef = ref<HTMLDivElement | null>(null)
const activeTotalTrendRef = ref<HTMLDivElement | null>(null)
const activeIncrementTrendRef = ref<HTMLDivElement | null>(null)

let checkTrendChart: echarts.ECharts | null = null
let reportTrendChart: echarts.ECharts | null = null
let latencyTrendChart: echarts.ECharts | null = null
let blockTrendChart: echarts.ECharts | null = null
let activeTotalTrendChart: echarts.ECharts | null = null
let activeIncrementTrendChart: echarts.ECharts | null = null

const trendRangeOptions = computed(() => [
  { label: t('monitor.range15m'), value: '15m' },
  { label: t('monitor.range1h'), value: '1h' },
  { label: t('monitor.range6h'), value: '6h' },
  { label: t('monitor.range24h'), value: '24h' },
])

const stats = computed(() => [
  { titleKey: 'dashboard.reportQps', value: formatQps(metrics.value?.reportQps), icon: 'TrendCharts', color: 'bg-ui-status-info' },
  { titleKey: 'dashboard.checkQps', value: formatQps(metrics.value?.checkQps), icon: 'Connection', color: 'bg-ui-status-success' },
  { titleKey: 'dashboard.todayActiveDevices', value: formatCount(metrics.value?.todayActiveDevices), icon: 'UserFilled', color: 'bg-ui-brand' },
  { titleKey: 'dashboard.checkP99Latency', value: formatLatency(metrics.value?.checkP99Latency), icon: 'Timer', color: 'bg-ui-status-warning' },
  { titleKey: 'dashboard.blockRate', value: formatPercent((metrics.value?.blockRate ?? 0) * 100), icon: 'Warning', color: 'bg-ui-status-danger' },
])

const fetchMetrics = async () => {
  metrics.value = await monitorApi.getRealtimeMetrics()
}

const fetchTrends = async () => {
  trendLoading.value = true
  try {
    trends.value = await monitorApi.getTrends(trendRange.value)
    await nextTick()
    renderTrendCharts()
  } finally {
    trendLoading.value = false
  }
}

const startRefresh = () => {
  refreshTimer.value = window.setInterval(fetchMetrics, 5000)
  trendRefreshTimer.value = window.setInterval(fetchTrends, 30000)
}

const stopRefresh = () => {
  if (refreshTimer.value != null) {
    window.clearInterval(refreshTimer.value)
    refreshTimer.value = null
  }
  if (trendRefreshTimer.value != null) {
    window.clearInterval(trendRefreshTimer.value)
    trendRefreshTimer.value = null
  }
}

const formatQps = (value?: number) => `${(value ?? 0).toFixed(2)}`
const formatCount = (value?: number) => `${Math.round(value ?? 0).toLocaleString()}`
const formatPercent = (value?: number) => `${(value ?? 0).toFixed(2)}%`
const formatLatency = (value?: number) => `${(value ?? 0).toFixed(0)} ms`

const formatTrendTime = (timestamp: number) => {
  const date = dayjs(timestamp * 1000)
  return trendRange.value === '24h' ? date.format('MM-DD HH:mm') : date.format('HH:mm')
}

const toChartData = (points: TrendPoint[] = []) => points.map(point => [point.timestamp * 1000, point.value] as [number, number])

const getOrCreateChart = (current: echarts.ECharts | null, el: HTMLDivElement | null) => {
  if (!el) return null
  if (current != null) return current
  return echarts.init(el)
}

const baseLineOption = (
  series: echarts.SeriesOption[],
  yAxisFormatter?: (value: number) => string,
  yAxisOverrides: echarts.YAXisComponentOption = {},
) => {
  const yAxis = {
    type: 'value',
    axisLabel: yAxisFormatter ? { formatter: yAxisFormatter } : undefined,
    splitLine: {
      lineStyle: { color: '#e2e8f0' },
    },
    ...yAxisOverrides,
  } as echarts.YAXisComponentOption

  return {
    animation: false,
    grid: { left: 48, right: 20, top: 24, bottom: 32 },
    tooltip: {
      trigger: 'axis',
      valueFormatter: (value: number | string) => typeof value === 'number' ? value.toFixed(2) : String(value),
    },
    xAxis: {
      type: 'time',
      axisLabel: {
        formatter: (value: number) => formatTrendTime(Math.round(value / 1000)),
      },
    },
    yAxis,
    series,
  }
}

const renderTrendCharts = () => {
  if (!trends.value) return

  checkTrendChart = getOrCreateChart(checkTrendChart, checkTrendRef.value)
  reportTrendChart = getOrCreateChart(reportTrendChart, reportTrendRef.value)
  latencyTrendChart = getOrCreateChart(latencyTrendChart, latencyTrendRef.value)
  blockTrendChart = getOrCreateChart(blockTrendChart, blockTrendRef.value)
  activeTotalTrendChart = getOrCreateChart(activeTotalTrendChart, activeTotalTrendRef.value)
  activeIncrementTrendChart = getOrCreateChart(activeIncrementTrendChart, activeIncrementTrendRef.value)

  checkTrendChart?.setOption(baseLineOption([
    {
      name: 'Check QPS',
      type: 'line',
      smooth: true,
      showSymbol: false,
      areaStyle: { opacity: 0.08 },
      lineStyle: { width: 2, color: '#0f766e' },
      itemStyle: { color: '#0f766e' },
      data: toChartData(trends.value.checkQps),
    },
  ]))

  reportTrendChart?.setOption(baseLineOption([
    {
      name: 'Report QPS',
      type: 'line',
      smooth: true,
      showSymbol: false,
      areaStyle: { opacity: 0.08 },
      lineStyle: { width: 2, color: '#2563eb' },
      itemStyle: { color: '#2563eb' },
      data: toChartData(trends.value.reportQps),
    },
  ]))

  latencyTrendChart?.setOption(baseLineOption([
    {
      name: 'Check P50',
      type: 'line',
      smooth: true,
      showSymbol: true,
      connectNulls: true,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { width: 2, color: '#f59e0b' },
      itemStyle: { color: '#f59e0b' },
      data: toChartData(trends.value.checkP50Latency),
    },
    {
      name: 'Check P99',
      type: 'line',
      smooth: true,
      showSymbol: true,
      connectNulls: true,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { width: 2, color: '#dc2626' },
      itemStyle: { color: '#dc2626' },
      data: toChartData(trends.value.checkP99Latency),
    },
    {
      name: 'Report P50',
      type: 'line',
      smooth: true,
      showSymbol: true,
      connectNulls: true,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { width: 2, color: '#2563eb' },
      itemStyle: { color: '#2563eb' },
      data: toChartData(trends.value.reportP50Latency),
    },
    {
      name: 'Report P99',
      type: 'line',
      smooth: true,
      showSymbol: true,
      connectNulls: true,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { width: 2, color: '#7c3aed' },
      itemStyle: { color: '#7c3aed' },
      data: toChartData(trends.value.reportP99Latency),
    },
  ], value => `${value} ms`))

  blockTrendChart?.setOption(baseLineOption([
    {
      name: 'Block Rate',
      type: 'line',
      smooth: true,
      showSymbol: false,
      areaStyle: { opacity: 0.08 },
      lineStyle: { width: 2, color: '#7c3aed' },
      itemStyle: { color: '#7c3aed' },
      data: trends.value.blockRate.map(point => [point.timestamp * 1000, point.value * 100] as [number, number]),
    },
  ], value => `${value.toFixed(2)}%`))

  activeTotalTrendChart?.setOption(baseLineOption([
    {
      name: 'Active Devices',
      type: 'line',
      smooth: true,
      showSymbol: false,
      areaStyle: { opacity: 0.08 },
      lineStyle: { width: 2, color: '#0ea5e9' },
      itemStyle: { color: '#0ea5e9' },
      data: toChartData(trends.value.activeDevicesTotal),
    },
  ], value => value.toFixed(0), { min: 0, minInterval: 1 }))

  activeIncrementTrendChart?.setOption(baseLineOption([
    {
      name: 'Active Increment(5m)',
      type: 'line',
      smooth: true,
      showSymbol: false,
      areaStyle: { opacity: 0.08 },
      lineStyle: { width: 2, color: '#10b981' },
      itemStyle: { color: '#10b981' },
      data: toChartData(trends.value.activeDevicesIncrement),
    },
  ], value => value.toFixed(0), { min: 0, minInterval: 1 }))
}

const resizeCharts = () => {
  checkTrendChart?.resize()
  reportTrendChart?.resize()
  latencyTrendChart?.resize()
  blockTrendChart?.resize()
  activeTotalTrendChart?.resize()
  activeIncrementTrendChart?.resize()
}

watch(trendRange, () => {
  fetchTrends()
})

onMounted(async () => {
  await Promise.all([fetchMetrics(), fetchTrends()])
  startRefresh()
  window.addEventListener('resize', resizeCharts)
})

onUnmounted(() => {
  stopRefresh()
  window.removeEventListener('resize', resizeCharts)
  checkTrendChart?.dispose()
  reportTrendChart?.dispose()
  latencyTrendChart?.dispose()
  blockTrendChart?.dispose()
  activeTotalTrendChart?.dispose()
  activeIncrementTrendChart?.dispose()
})
</script>

<template>
  <div>
    <h1 class="ui-page-title">
      {{ t('menu.dashboard') }}
    </h1>

    <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-4 mb-6">
      <div
        v-for="stat in stats"
        :key="stat.titleKey"
        class="ui-stat-card"
      >
        <div :class="[stat.color, 'ui-stat-icon']">
          <el-icon class="text-ui-white text-2xl">
            <component :is="stat.icon" />
          </el-icon>
        </div>
        <div>
          <div class="ui-stat-label">
            {{ t(stat.titleKey) }}
          </div>
          <div class="ui-stat-value">
            {{ stat.value }}
          </div>
        </div>
      </div>
    </div>

    <div class="mb-6">
      <div class="flex flex-col gap-3 mb-4 sm:flex-row sm:items-center sm:justify-between">
        <div class="inline-flex items-center gap-1 ui-card-title">
          {{ t('dashboard.trendOverview') }}
          <el-tooltip :content="t('dashboard.trendOverviewDesc')" placement="top">
            <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
          </el-tooltip>
        </div>
        <el-radio-group v-model="trendRange" size="small">
          <el-radio-button
            v-for="option in trendRangeOptions"
            :key="option.value"
            :label="option.value"
          >
            {{ option.label }}
          </el-radio-button>
        </el-radio-group>
      </div>

      <div class="grid grid-cols-1 gap-4 xl:grid-cols-2">
        <el-card v-loading="trendLoading">
          <template #header>
            <span class="inline-flex items-center gap-1 ui-card-title">
              {{ t('dashboard.checkTrend') }}
              <el-tooltip :content="t('dashboard.checkTrendDesc')" placement="top">
                <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
              </el-tooltip>
            </span>
          </template>
          <div ref="checkTrendRef" class="h-72" />
        </el-card>

        <el-card v-loading="trendLoading">
          <template #header>
            <span class="inline-flex items-center gap-1 ui-card-title">
              {{ t('dashboard.reportTrend') }}
              <el-tooltip :content="t('dashboard.reportTrendDesc')" placement="top">
                <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
              </el-tooltip>
            </span>
          </template>
          <div ref="reportTrendRef" class="h-72" />
        </el-card>

        <el-card v-loading="trendLoading">
          <template #header>
            <span class="inline-flex items-center gap-1 ui-card-title">
              {{ t('dashboard.latencyTrend') }}
              <el-tooltip :content="t('dashboard.latencyTrendDesc')" placement="top">
                <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
              </el-tooltip>
            </span>
          </template>
          <div ref="latencyTrendRef" class="h-72" />
        </el-card>

        <el-card v-loading="trendLoading">
          <template #header>
            <span class="inline-flex items-center gap-1 ui-card-title">
              {{ t('dashboard.blockTrend') }}
              <el-tooltip :content="t('dashboard.blockTrendDesc')" placement="top">
                <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
              </el-tooltip>
            </span>
          </template>
          <div ref="blockTrendRef" class="h-72" />
        </el-card>

        <el-card v-loading="trendLoading">
          <template #header>
            <span class="inline-flex items-center gap-1 ui-card-title">
              {{ t('dashboard.activeTotalTrend') }}
              <el-tooltip :content="t('dashboard.activeTotalTrendDesc')" placement="top">
                <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
              </el-tooltip>
            </span>
          </template>
          <div ref="activeTotalTrendRef" class="h-72" />
        </el-card>

        <el-card v-loading="trendLoading">
          <template #header>
            <span class="inline-flex items-center gap-1 ui-card-title">
              {{ t('dashboard.activeIncrementTrend') }}
              <el-tooltip :content="t('dashboard.activeIncrementTrendDesc')" placement="top">
                <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
              </el-tooltip>
            </span>
          </template>
          <div ref="activeIncrementTrendRef" class="h-72" />
        </el-card>
      </div>
    </div>

    <el-card class="mb-6">
      <template #header>
        <span class="ui-card-title">{{ t('dashboard.quickEntry') }}</span>
      </template>
      <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
        <router-link
          v-if="userStore.hasPermission('fota:product:read')"
          to="/product"
          class="ui-nav-tile"
        >
          <el-icon class="ui-brand-text text-3xl mb-2">
            <Box />
          </el-icon>
          <div>{{ t('menu.product') }}</div>
        </router-link>
        <router-link
          v-if="userStore.hasPermission('fota:firmware:read')"
          to="/firmware"
          class="ui-nav-tile"
        >
          <el-icon class="ui-brand-text text-3xl mb-2">
            <Cpu />
          </el-icon>
          <div>{{ t('menu.firmware') }}</div>
        </router-link>
        <router-link
          v-if="userStore.hasPermission('fota:policy:read')"
          to="/policy"
          class="ui-nav-tile"
        >
          <el-icon class="ui-brand-text text-3xl mb-2">
            <Document />
          </el-icon>
          <div>{{ t('menu.policy') }}</div>
        </router-link>
        <router-link
          v-if="userStore.hasPermission('fota:device:read')"
          to="/device"
          class="ui-nav-tile"
        >
          <el-icon class="ui-brand-text text-3xl mb-2">
            <Iphone />
          </el-icon>
          <div>{{ t('menu.device') }}</div>
        </router-link>
      </div>
    </el-card>
  </div>
</template>
