<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { monitorApi, type GlobalMonitorMetrics, type RegionMetrics, type InstanceMetricsEnhanced, type RegionDetail, type InstanceDetail } from '@/api/monitor'
import SystemSummaryCard from './components/SystemSummaryCard.vue'
import RegionOverviewTable from './components/RegionOverviewTable.vue'
import HostOverviewTable from './components/HostOverviewTable.vue'
import InstanceOverviewTable from './components/InstanceOverviewTable.vue'
import HotProductsTable from './components/HotProductsTable.vue'
import RegionDetailDrawer from './components/RegionDetailDrawer.vue'
import InstanceDetailDrawer from './components/InstanceDetailDrawer.vue'
import RefreshControl from './components/RefreshControl.vue'

const { t } = useI18n()

const metrics = ref<GlobalMonitorMetrics | null>(null)
const loading = ref(false)
const isInitialLoad = ref(true)
const refreshTimer = ref<number | null>(null)
const autoRefresh = ref(true)
const refreshInterval = ref(15000)

// 区域详情抽屉状态
const regionDrawerVisible = ref(false)
const selectedRegion = ref<RegionMetrics | null>(null)
const regionDetail = ref<RegionDetail | null>(null)
const regionDetailLoading = ref(false)
const regionIsInitialLoad = ref(true)
const regionAutoRefresh = ref(true)
const regionRefreshInterval = ref(15000)
const regionRefreshTimer = ref<number | null>(null)

// 实例详情抽屉状态
const instanceDrawerVisible = ref(false)
const selectedInstance = ref<InstanceMetricsEnhanced | null>(null)
const instanceDetailData = ref<InstanceDetail | null>(null)
const instanceDetailLoading = ref(false)
const instanceIsInitialLoad = ref(true)
const instanceAutoRefresh = ref(true)
const instanceRefreshInterval = ref(15000)
const instanceRefreshTimer = ref<number | null>(null)

const fetchMetrics = async () => {
  // 只在初始加载时显示 loading
  if (isInitialLoad.value) {
    loading.value = true
  }
  try {
    metrics.value = await monitorApi.getGlobalMetrics()
  } catch (error) {
    console.error('Failed to fetch global metrics:', error)
  } finally {
    if (isInitialLoad.value) {
      loading.value = false
      isInitialLoad.value = false
    }
  }
}

const fetchRegionDetail = async () => {
  if (!selectedRegion.value) return
  // 只在初始加载时显示 loading
  if (regionIsInitialLoad.value) {
    regionDetailLoading.value = true
  }
  try {
    regionDetail.value = await monitorApi.getRegionDetail(selectedRegion.value.region)
    // 更新区域的实时数据（从 summary 中获取）
    if (regionDetail.value?.summary) {
      selectedRegion.value = {
        ...selectedRegion.value,
        ...regionDetail.value.summary
      }
    }
  } catch (error) {
    console.error('Failed to fetch region detail:', error)
  } finally {
    if (regionIsInitialLoad.value) {
      regionDetailLoading.value = false
      regionIsInitialLoad.value = false
    }
  }
}

const fetchInstanceDetail = async () => {
  if (!selectedInstance.value) return
  // 只在初始加载时显示 loading
  if (instanceIsInitialLoad.value) {
    instanceDetailLoading.value = true
  }
  try {
    instanceDetailData.value = await monitorApi.getInstanceDetail(selectedInstance.value.instance)
    // 更新实例的实时数据（从 summary 中获取）
    if (instanceDetailData.value?.summary) {
      selectedInstance.value = {
        ...selectedInstance.value,
        ...instanceDetailData.value.summary
      }
    }
  } catch (error) {
    console.error('Failed to fetch instance detail:', error)
  } finally {
    if (instanceIsInitialLoad.value) {
      instanceDetailLoading.value = false
      instanceIsInitialLoad.value = false
    }
  }
}

const handleRegionClick = async (region: RegionMetrics) => {
  selectedRegion.value = region
  regionDrawerVisible.value = true
  regionIsInitialLoad.value = true  // 重置初始加载标志
  await fetchRegionDetail()
  startRegionRefresh()
}

const handleInstanceClick = async (instance: InstanceMetricsEnhanced) => {
  selectedInstance.value = instance
  instanceDrawerVisible.value = true
  instanceIsInitialLoad.value = true  // 重置初始加载标志
  await fetchInstanceDetail()
  startInstanceRefresh()
}

const startRefresh = () => {
  stopRefresh()
  if (autoRefresh.value) {
    refreshTimer.value = window.setInterval(fetchMetrics, refreshInterval.value)
  }
}

const stopRefresh = () => {
  if (refreshTimer.value != null) {
    window.clearInterval(refreshTimer.value)
    refreshTimer.value = null
  }
}

const startRegionRefresh = () => {
  stopRegionRefresh()
  if (regionAutoRefresh.value) {
    regionRefreshTimer.value = window.setInterval(fetchRegionDetail, regionRefreshInterval.value)
  }
}

const stopRegionRefresh = () => {
  if (regionRefreshTimer.value != null) {
    window.clearInterval(regionRefreshTimer.value)
    regionRefreshTimer.value = null
  }
}

const startInstanceRefresh = () => {
  stopInstanceRefresh()
  if (instanceAutoRefresh.value) {
    instanceRefreshTimer.value = window.setInterval(fetchInstanceDetail, instanceRefreshInterval.value)
  }
}

const stopInstanceRefresh = () => {
  if (instanceRefreshTimer.value != null) {
    window.clearInterval(instanceRefreshTimer.value)
    instanceRefreshTimer.value = null
  }
}

// 监听自动刷新开关变化
watch(autoRefresh, (enabled) => {
  startRefresh()
})

watch(refreshInterval, () => {
  startRefresh()
})

watch(regionAutoRefresh, () => {
  startRegionRefresh()
})

watch(regionRefreshInterval, () => {
  startRegionRefresh()
})

watch(instanceAutoRefresh, () => {
  startInstanceRefresh()
})

watch(instanceRefreshInterval, () => {
  startInstanceRefresh()
})

// 监听抽屉关闭，停止刷新
watch(regionDrawerVisible, (visible) => {
  if (!visible) {
    stopRegionRefresh()
  }
})

watch(instanceDrawerVisible, (visible) => {
  if (!visible) {
    stopInstanceRefresh()
  }
})

onMounted(async () => {
  await fetchMetrics()
  startRefresh()
})

onUnmounted(() => {
  stopRefresh()
  stopRegionRefresh()
  stopInstanceRefresh()
})
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-4">
      <h1 class="ui-page-title">{{ t('menu.monitorLoad') }}</h1>
      <RefreshControl
        v-model="autoRefresh"
        v-model:interval="refreshInterval"
        :loading="loading"
        @refresh="fetchMetrics"
      />
    </div>

    <!-- 全局摘要卡片 -->
    <div class="mb-4">
      <SystemSummaryCard :summary="metrics?.global ?? null" />
    </div>

    <!-- 区域概览和主机概览 -->
    <div class="grid grid-cols-1 gap-4 mb-4 xl:grid-cols-2">
      <RegionOverviewTable
        :data="metrics?.regions ?? []"
        :loading="loading"
        @row-click="handleRegionClick"
      />
      <HostOverviewTable
        :data="metrics?.hosts ?? []"
        :loading="loading"
      />
    </div>

    <!-- 实例概览 -->
    <div class="mb-4">
      <InstanceOverviewTable
        :data="metrics?.instances ?? []"
        :loading="loading"
        @row-click="handleInstanceClick"
      />
    </div>

    <!-- 热点产品 -->
    <div class="mb-4">
      <HotProductsTable
        :data="metrics?.hotProducts ?? []"
        :loading="loading"
      />
    </div>

    <!-- 区域详情抽屉 -->
    <RegionDetailDrawer
      v-model:visible="regionDrawerVisible"
      v-model:auto-refresh="regionAutoRefresh"
      v-model:refresh-interval="regionRefreshInterval"
      :region="selectedRegion"
      :detail="regionDetail"
      :loading="regionDetailLoading"
      @refresh="fetchRegionDetail"
    />

    <!-- 实例详情抽屉 -->
    <InstanceDetailDrawer
      v-model:visible="instanceDrawerVisible"
      v-model:auto-refresh="instanceAutoRefresh"
      v-model:refresh-interval="instanceRefreshInterval"
      :instance="selectedInstance"
      :detail="instanceDetailData"
      :loading="instanceDetailLoading"
      @refresh="fetchInstanceDetail"
    />
  </div>
</template>
