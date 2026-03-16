<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
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

const { t } = useI18n()

const metrics = ref<GlobalMonitorMetrics | null>(null)
const loading = ref(false)
const refreshTimer = ref<number | null>(null)

// 区域详情抽屉状态
const regionDrawerVisible = ref(false)
const selectedRegion = ref<RegionMetrics | null>(null)
const regionDetail = ref<RegionDetail | null>(null)
const regionDetailLoading = ref(false)

// 实例详情抽屉状态
const instanceDrawerVisible = ref(false)
const selectedInstance = ref<InstanceMetricsEnhanced | null>(null)
const instanceDetailData = ref<InstanceDetail | null>(null)
const instanceDetailLoading = ref(false)

const fetchMetrics = async () => {
  loading.value = true
  try {
    metrics.value = await monitorApi.getGlobalMetrics()
  } catch (error) {
    console.error('Failed to fetch global metrics:', error)
  } finally {
    loading.value = false
  }
}

const handleRegionClick = async (region: RegionMetrics) => {
  selectedRegion.value = region
  regionDrawerVisible.value = true
  regionDetailLoading.value = true
  try {
    regionDetail.value = await monitorApi.getRegionDetail(region.region)
  } catch (error) {
    console.error('Failed to fetch region detail:', error)
    ElMessage.error(t('common.failed'))
  } finally {
    regionDetailLoading.value = false
  }
}

const handleInstanceClick = async (instance: InstanceMetricsEnhanced) => {
  selectedInstance.value = instance
  instanceDrawerVisible.value = true
  instanceDetailLoading.value = true
  try {
    instanceDetailData.value = await monitorApi.getInstanceDetail(instance.instance)
  } catch (error) {
    console.error('Failed to fetch instance detail:', error)
    ElMessage.error(t('common.failed'))
  } finally {
    instanceDetailLoading.value = false
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

onMounted(async () => {
  await fetchMetrics()
  startRefresh()
})

onUnmounted(() => {
  stopRefresh()
})
</script>

<template>
  <div>
    <h1 class="ui-page-title">{{ t('menu.monitorLoad') }}</h1>

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
      :region="selectedRegion"
      :detail="regionDetail"
      :loading="regionDetailLoading"
    />

    <!-- 实例详情抽屉 -->
    <InstanceDetailDrawer
      v-model:visible="instanceDrawerVisible"
      :instance="selectedInstance"
      :detail="instanceDetailData"
      :loading="instanceDetailLoading"
    />
  </div>
</template>
