<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { use, type ComposeOption } from 'echarts/core'
import { PieChart, BarChart, LineChart, type PieSeriesOption, type BarSeriesOption, type LineSeriesOption } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  type TitleComponentOption,
  type TooltipComponentOption,
  type GridComponentOption,
  type LegendComponentOption,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import VChart from 'vue-echarts'
import StatisticsTabContainer from '@/components/statistics/StatisticsTabContainer.vue'
import ProductDeviceDrawer from './ProductDeviceDrawer.vue'
import {
  getProductVersionDistribution,
  getProductVersionTrend,
  type ProductVersionDistributionItem,
  type ProductVersionTrendResponse,
  type Granularity,
} from '@/api/statistics'

use([
  TitleComponent, TooltipComponent, GridComponent, LegendComponent,
  PieChart, BarChart, LineChart, CanvasRenderer,
])

type ECOption = ComposeOption<
  TitleComponentOption | TooltipComponentOption | GridComponentOption |
  LegendComponentOption | PieSeriesOption | BarSeriesOption | LineSeriesOption
>

interface Props {
  productId: number
}

const props = defineProps<Props>()

const { t, locale } = useI18n()

const versionDistribution = ref<ProductVersionDistributionItem[]>([])
const neverVisitedCount = ref(0)
const totalDevices = ref(0)
const dataCalculatedAt = ref('')
const loading = ref(false)

const trendData = ref<ProductVersionTrendResponse | null>(null)
const trendLoading = ref(false)
const selectedDays = ref(7)

const deviceDrawerVisible = ref(false)

let abortController: AbortController | null = null

const numberFormatter = computed(() => new Intl.NumberFormat(locale.value === 'zh-CN' ? 'zh-CN' : undefined))

const dayOptions = computed(() => [
  { label: t('statistics.timeline.days', { days: 7 }), value: 7 },
  { label: t('statistics.timeline.days', { days: 15 }), value: 15 },
  { label: t('statistics.timeline.days', { days: 30 }), value: 30 },
])

const granularity = computed<Granularity>(() => selectedDays.value <= 7 ? 'hour' : 'day')

const chartColors = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#6366f1', '#14b8a6', '#f97316', '#8b5cf6']

const versionItems = computed(() => versionDistribution.value)

const chartType = computed(() => {
  if (versionItems.value.length === 0) return 'none'
  return versionItems.value.length > 8 ? 'bar' : 'pie'
})

const hasDistributionData = computed(() => (
  versionItems.value.length > 0 || neverVisitedCount.value > 0
))

const maxDeviceCount = computed(() => {
  if (versionItems.value.length === 0) return 1
  return Math.max(...versionItems.value.map(item => item.deviceCount), 1)
})

const formatCount = (value: number) => numberFormatter.value.format(value)
const formatPercent = (value: number) => `${value.toFixed(value % 1 === 0 ? 0 : 1)}%`

// 为 version+internalVersion 相同的条目追加 #versionId 以区分
const versionLabelMap = computed(() => {
  const groups = new Map<string, number>()
  for (const item of versionItems.value) {
    const key = `${item.version}||${item.internalVersion ?? ''}`
    groups.set(key, (groups.get(key) ?? 0) + 1)
  }

  const result = new Map<number | null, string>()
  for (const item of versionItems.value) {
    const key = `${item.version}||${item.internalVersion ?? ''}`
    let label = item.version
    if (item.internalVersion) {
      label += ` (${item.internalVersion})`
    }
    if ((groups.get(key) ?? 0) > 1 && item.versionId != null) {
      label += ` #${item.versionId}`
    }
    result.set(item.versionId ?? null, label)
  }
  return result
})

const getVersionLabel = (item: { versionId: number; version: string; internalVersion?: string }) => {
  return versionLabelMap.value.get(item.versionId) ?? item.version
}

const pieOption = computed<ECOption>(() => ({
  color: chartColors,
  tooltip: {
    trigger: 'item',
    formatter: (params) => {
      const value = typeof params.value === 'number' ? params.value : Number(params.value ?? 0)
      const percent = typeof params.percent === 'number' ? params.percent : 0
      return `${params.name}<br/>${formatCount(value)} ${t('statistics.productVersionChart.devices')} (${formatPercent(percent)})`
    },
  },
  legend: {
    type: 'scroll',
    orient: 'vertical',
    right: 0,
    top: 'middle',
    itemWidth: 10,
    itemHeight: 10,
    textStyle: { color: '#475569' },
  },
  series: [{
    name: t('statistics.productVersionChart.title'),
    type: 'pie',
    radius: ['42%', '70%'],
    center: ['35%', '50%'],
    avoidLabelOverlap: true,
    itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
    label: { show: false, position: 'center' },
    emphasis: {
      label: {
        show: true,
        fontSize: 16,
        fontWeight: 'bold',
        formatter: ({ name, percent }) => `${name}\n${formatPercent(Number(percent ?? 0))}`,
      },
    },
    labelLine: { show: false },
    data: versionItems.value.map(item => ({ value: item.deviceCount, name: getVersionLabel(item) })),
  }],
}))

const barOption = computed<ECOption>(() => ({
  color: ['#3b82f6'],
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'shadow' },
    formatter: (params) => {
      const [first] = Array.isArray(params) ? params : [params]
      const data = versionItems.value[first?.dataIndex ?? -1]
      if (!data) return ''
      return `${getVersionLabel(data)}<br/>${formatCount(data.deviceCount)} ${t('statistics.productVersionChart.devices')} (${formatPercent(data.percentage)})`
    },
  },
  grid: { left: '3%', right: '4%', bottom: '3%', top: 20, containLabel: true },
  xAxis: {
    type: 'category',
    axisLabel: { interval: 0, rotate: 30, color: '#64748b' },
    axisTick: { alignWithLabel: true },
    data: versionItems.value.map(item => getVersionLabel(item)),
  },
  yAxis: {
    type: 'value',
    axisLabel: { color: '#64748b' },
    splitLine: { lineStyle: { color: '#e2e8f0' } },
  },
  series: [{
    name: t('statistics.productVersionChart.devices'),
    type: 'bar',
    barMaxWidth: 36,
    data: versionItems.value.map(item => item.deviceCount),
    itemStyle: { color: '#409eff', borderRadius: [8, 8, 0, 0] },
  }],
}))

const distributionChartOption = computed<ECOption | null>(() => {
  if (chartType.value === 'pie') return pieOption.value
  if (chartType.value === 'bar') return barOption.value
  return null
})

const trendChartOption = computed<ECOption | null>(() => {
  if (!trendData.value || trendData.value.timestamps.length === 0) return null

  const { timestamps, series } = trendData.value
  const topSeries = series.slice(0, 8)

  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
    },
    legend: {
      type: 'scroll',
      bottom: 0,
      data: topSeries.map(s => s.version),
      textStyle: { color: '#64748b' },
    },
    grid: { left: '3%', right: '4%', bottom: '15%', top: 20, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: timestamps,
      axisLabel: {
        rotate: 30,
        fontSize: 11,
        color: '#64748b',
        formatter: (val: string) => val.length > 10 ? val.slice(5) : val,
      },
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        color: '#64748b',
        formatter: (v: number) => v >= 1000 ? `${(v / 1000).toFixed(1)}k` : String(v),
      },
      splitLine: { lineStyle: { color: '#e2e8f0' } },
    },
    series: topSeries.map((s, i) => ({
      name: s.version,
      type: 'line',
      smooth: true,
      data: s.counts,
      itemStyle: { color: chartColors[i % chartColors.length] },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: `${chartColors[i % chartColors.length]}30` },
            { offset: 1, color: `${chartColors[i % chartColors.length]}05` },
          ],
        },
      },
    })),
  }
})

const abortPendingRequest = () => {
  abortController?.abort()
  abortController = null
}

const fetchDistribution = async (signal?: AbortSignal) => {
  if (!props.productId) return
  loading.value = true
  try {
    const result = await getProductVersionDistribution(props.productId, signal)
    versionDistribution.value = result.data.versionDistributions ?? []
    neverVisitedCount.value = result.data.neverVisitedCount ?? 0
    totalDevices.value = result.data.totalDevices ?? 0
    dataCalculatedAt.value = result.dataCalculatedAt ?? ''
  } catch (e) {
    if ((e as Error).name === 'AbortError') return
  } finally {
    loading.value = false
  }
}

const fetchTrend = async (signal?: AbortSignal) => {
  if (!props.productId) return
  trendLoading.value = true
  try {
    const result = await getProductVersionTrend(
      props.productId,
      { days: selectedDays.value, granularity: granularity.value },
      signal
    )
    trendData.value = result.data
  } catch (e) {
    if ((e as Error).name === 'AbortError') return
    trendData.value = null
  } finally {
    trendLoading.value = false
  }
}

const fetchAll = async () => {
  abortPendingRequest()
  abortController = new AbortController()
  const { signal } = abortController
  await Promise.all([fetchDistribution(signal), fetchTrend(signal)])
}

const handleRefresh = (signal?: AbortSignal) => {
  if (signal) {
    signal.addEventListener('abort', () => abortPendingRequest(), { once: true })
  }
  void fetchAll()
}

const handleDaysChange = () => {
  void fetchTrend()
}

watch(
  () => props.productId,
  (id) => {
    if (!id) return
    void fetchAll()
  },
  { immediate: true }
)

onUnmounted(() => {
  abortPendingRequest()
})
</script>

<template>
  <StatisticsTabContainer
    :title="t('statistics.productVersionChart.title')"
    :data-calculated-at="dataCalculatedAt"
    :loading="loading"
    refresh-scope="product"
    :refresh-scope-id="productId"
    :refresh-disabled="productId <= 0"
    @refresh="handleRefresh"
  >
    <!-- 版本分布图 + 从未访问 -->
    <div
      v-if="hasDistributionData"
      class="product-stats-panel"
    >
      <div class="product-stats-panel__overview">
        <div class="chart-panel">
          <div class="panel-title">
            {{ t('statistics.productVersionChart.title') }}
          </div>
          <VChart
            v-if="distributionChartOption"
            class="chart-panel__canvas"
            :option="distributionChartOption"
            autoresize
          />
          <div
            v-else
            class="chart-panel__empty"
          >
            <el-empty :description="t('statistics.productVersionChart.noData')" />
          </div>
        </div>

        <div class="summary-col">
          <div class="summary-card">
            <div class="panel-title">
              {{ t('statistics.productVersionChart.neverVisited') }}
            </div>
            <div class="summary-card__divider" />
            <div class="summary-card__value">
              {{ formatCount(neverVisitedCount) }}
            </div>
            <div class="summary-card__unit">
              {{ t('statistics.productVersionChart.devices') }}
            </div>
          </div>

          <div class="action-card">
            <el-button
              type="primary"
              plain
              size="small"
              @click="deviceDrawerVisible = true"
            >
              {{ t('statistics.productVersionChart.viewDevices') }}
            </el-button>
          </div>
        </div>
      </div>

      <!-- 版本列表 -->
      <div
        v-if="versionItems.length > 0"
        class="version-list"
      >
        <div class="panel-title">
          {{ t('statistics.productVersionChart.versionList') }}
        </div>
        <div class="version-list__items">
          <div
            v-for="item in versionItems"
            :key="item.versionId"
            class="version-list__item"
          >
            <div class="version-list__header">
              <span class="version-list__name">{{ getVersionLabel(item) }}</span>
              <span class="version-list__meta">
                {{ formatPercent(item.percentage) }}
                <span class="version-list__count">
                  ({{ formatCount(item.deviceCount) }} {{ t('statistics.productVersionChart.devices') }})
                </span>
              </span>
            </div>
            <div class="version-list__track">
              <div
                class="version-list__bar"
                :style="{ width: `${Math.max((item.deviceCount / maxDeviceCount) * 100, 6)}%` }"
              />
            </div>
          </div>
        </div>
        <div class="version-list__footer">
          {{ formatCount(totalDevices) }} {{ t('statistics.productVersionChart.devices') }}
        </div>
      </div>

      <!-- 趋势图 -->
      <div class="trend-panel">
        <div class="trend-panel__header">
          <div class="panel-title">
            {{ t('statistics.productVersionChart.trend') }}
          </div>
          <el-radio-group
            v-model="selectedDays"
            size="small"
            @change="handleDaysChange"
          >
            <el-radio-button
              v-for="option in dayOptions"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </el-radio-button>
          </el-radio-group>
        </div>

        <div
          v-if="trendLoading"
          v-loading="trendLoading"
          class="trend-panel__loading"
        />

        <VChart
          v-else-if="trendChartOption"
          class="trend-panel__canvas"
          :option="trendChartOption"
          autoresize
        />

        <el-empty
          v-else
          :description="t('statistics.productVersionChart.trendNoData')"
          class="trend-panel__empty"
        />
      </div>
    </div>

    <div
      v-else
      class="product-stats-panel__empty"
    >
      <el-empty :description="t('statistics.productVersionChart.noData')" />
    </div>
  </StatisticsTabContainer>

  <ProductDeviceDrawer
    v-model:visible="deviceDrawerVisible"
    :product-id="productId"
  />
</template>

<style scoped>
.product-stats-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.product-stats-panel__overview {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(200px, 1fr);
  gap: 20px;
}

.chart-panel,
.version-list,
.trend-panel {
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  padding: 20px;
}

.chart-panel {
  min-height: 280px;
}

.summary-col {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.summary-card {
  flex: 1;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  padding: 20px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  text-align: center;
}

.action-card {
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  padding: 16px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.panel-title {
  color: #0f172a;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.5;
}

.chart-panel__canvas,
.trend-panel__canvas {
  height: 240px;
  margin-top: 12px;
}

.chart-panel__empty,
.product-stats-panel__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
}

.summary-card__divider {
  height: 8px;
  width: 100%;
  margin: 16px 0 20px;
  border-radius: 999px;
  background: linear-gradient(90deg, #dbeafe 0%, #93c5fd 100%);
}

.summary-card__value {
  color: #0f172a;
  font-size: 36px;
  font-weight: 700;
  line-height: 1.1;
}

.summary-card__unit {
  margin-top: 8px;
  color: #64748b;
  font-size: 14px;
}

.version-list__items {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 14px;
}

.version-list__item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.version-list__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.version-list__name {
  color: #0f172a;
  font-size: 14px;
  font-weight: 600;
}

.version-list__meta {
  color: #334155;
  font-size: 13px;
  white-space: nowrap;
}

.version-list__count {
  color: #64748b;
}

.version-list__track {
  overflow: hidden;
  height: 10px;
  border-radius: 999px;
  background: #e2e8f0;
}

.version-list__bar {
  height: 100%;
  min-width: 6px;
  border-radius: inherit;
  background: linear-gradient(90deg, #60a5fa 0%, #2563eb 100%);
}

.version-list__footer {
  margin-top: 14px;
  color: #64748b;
  font-size: 13px;
  text-align: right;
}

.trend-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.trend-panel__loading {
  height: 240px;
}

.trend-panel__empty {
  min-height: 160px;
  display: flex;
  align-items: center;
  justify-content: center;
}

@media (max-width: 960px) {
  .product-stats-panel__overview {
    grid-template-columns: 1fr;
  }
}
</style>
