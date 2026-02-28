<script setup lang="ts">
import { computed, onMounted, onActivated, reactive, ref } from 'vue'
import { useRouter, onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Lock, Clock, Download, Aim, User, Grid, Box } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { resolveStatusLabelKey } from '@/constants/status'
import { useUserStore } from '@/stores/user'
import {
  deletePolicy,
  pagePolicies,
  updatePolicyStatus,
  type PolicyStatus,
  type TimeWindowDTO,
  type UpgradePolicyItem,
} from '@/api/policy'
import { searchProducts, type ProductItem } from '@/api/product'
import {formatDateTime as utilsFormatDateTime, formatLocalDateTime} from '@/utils/date'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()

const list = ref<UpgradePolicyItem[]>([])
const total = ref(0)
const loading = ref(false)
const expandRowKeys = ref<string[]>([])
// 标记是否需要刷新（从表单页返回时）
let shouldRefreshOnReturn = false
const query = reactive({
  page: 1,
  size: 20,
  productId: undefined as number | undefined,
  name: '',
  status: undefined as PolicyStatus | undefined,
  sortBy: undefined as string | undefined,
  sortOrder: undefined as 'asc' | 'desc' | undefined,
})

const productSearchOptions = ref<ProductItem[]>([])
const productSearchLoading = ref(false)

let productSearchTimer: number | null = null

const canShowActions = computed(() =>
  userStore.hasPermission('fota:policy:update') ||
  userStore.hasPermission('fota:policy:delete')
)

const statusOptions: PolicyStatus[] = ['DRAFT', 'TESTING', 'VERIFIED', 'ACTIVE', 'PAUSED', 'EXPIRED']

// 状态流转加载中的策略 ID 列表
const transitionLoadingIds = ref<number[]>([])

// 所有状态选项（用于状态切换下拉菜单）
const allStatusOptions: PolicyStatus[] = ['DRAFT', 'TESTING', 'VERIFIED', 'ACTIVE', 'PAUSED', 'EXPIRED']

// 获取状态切换的可用目标状态列表
const getAvailableTargetStatuses = (currentStatus: PolicyStatus): PolicyStatus[] => {
  return allStatusOptions.filter(s => s !== currentStatus)
}

// 检查是否可以切换到指定状态
const canTransitionTo = (currentStatus: PolicyStatus, targetStatus: PolicyStatus): boolean => {
  const hasRelease = userStore.hasPermission('fota:policy:release')

  // 涉及 ACTIVE/PAUSED 的切换需要 release 权限
  if (currentStatus === 'ACTIVE' || currentStatus === 'PAUSED' ||
      targetStatus === 'ACTIVE' || targetStatus === 'PAUSED') {
    return hasRelease
  }

  return userStore.hasPermission('fota:policy:update')
}

// 状态标签类型映射（生命周期色系）
type PolicyStatusTagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'

const policyStatusTagTypeMap: Record<string, PolicyStatusTagType> = {
  DRAFT: 'info',      // 中性灰 - 未开始
  TESTING: 'warning', // 琥珀色 - 测试中
  VERIFIED: 'primary', // 蓝色 - 已验证待发布
  ACTIVE: 'success',  // 绿色 - 生产生效
  PAUSED: 'warning',  // 橙色 - 人工暂停
  EXPIRED: 'info',    // 灰蓝色 - 自然结束
}

// 状态样式类映射
const policyStatusClassMap: Record<string, string> = {
  DRAFT: 'is-draft',
  TESTING: 'is-testing',
  VERIFIED: 'is-verified',
  ACTIVE: 'is-active',
  PAUSED: 'is-paused',
  EXPIRED: 'is-expired',
}

const getPolicyStatusTagType = (status?: string): PolicyStatusTagType =>
  status ? (policyStatusTagTypeMap[status] || 'info') : 'info'

const getPolicyStatusClass = (status?: string): string => {
  if (!status) return ''
  return policyStatusClassMap[status] || ''
}

// 优先级标签类型映射
type PriorityTagType = 'danger' | 'warning' | 'info'

const getPriorityTagType = (priority: number): PriorityTagType => {
  // 数值越大优先级越高
  if (priority >= 80) return 'danger'   // 高优先级
  if (priority >= 40) return 'warning'  // 中优先级
  return 'info'                          // 低优先级
}

const getPriorityClass = (priority: number): string => {
  // 数值越大优先级越高
  if (priority >= 80) return 'is-high'
  if (priority >= 40) return 'is-medium'
  return 'is-low'
}

const getPriorityLabel = (priority: number): string => {
  if (priority >= 80) return t('policy.priorityHigh')
  if (priority >= 40) return t('policy.priorityMedium')
  return t('policy.priorityLow')
}

// 表格排序处理
const handleSortChange = (payload: { prop?: string; order?: 'ascending' | 'descending' | null }) => {
  query.page = 1
  if (!payload.prop || !payload.order) {
    query.sortBy = undefined
    query.sortOrder = undefined
  } else {
    query.sortBy = payload.prop
    query.sortOrder = payload.order === 'ascending' ? 'asc' : 'desc'
  }
  void fetchList()
}

// 辅助函数：获取触发模式标签
const getTriggerModeLabel = (mode: string): string => {
  return t(`policy.trigger${mode.charAt(0)}${mode.slice(1).toLowerCase()}`)
}

// 辅助函数：获取目标模式标签
const getTargetModeLabel = (mode: string): string => {
  const labels: Record<string, string> = {
    ALL: 'policy.targetAll',
    DEVICE_IDS: 'policy.targetDeviceIds',
    DEVICE_BATCHES: 'policy.targetBatches',
    DEVICE_TAGS: 'policy.targetTags',
  }
  return t(labels[mode] || mode)
}

// 辅助函数：获取时间窗口摘要
const getTimeWindowSummary = (window: { type: string; startAt: string | null; endAt: string | null }): string => {
  if (!window) return '-'

  // UNLIMITED 类型
  if (window.type === 'UNLIMITED') {
    return t('policy.timeWindowUnlimited')
  }

  // RANGE 或 DAILY 类型
  if (!window.startAt || !window.endAt) return '-'

  // 格式化函数：仅 HH:mm:ss
  const formatTime = (dateStr: string): string => {
    return formatLocalDateTime(dateStr, 'HH:mm:ss')
  }

  const typeLabel = t(`policy.timeWindow${window.type.charAt(0)}${window.type.slice(1).toLowerCase()}`)

  // DAILY 类型只显示时分秒
  if (window.type === 'DAILY') {
    const start = formatTime(window.startAt)
    const end = formatTime(window.endAt)
    return `${typeLabel}: ${start} ~ ${end}`
  }

  // RANGE 类型显示完整日期时间
  const start = formatLocalDateTime(window.startAt)
  const end = formatLocalDateTime(window.endAt)
  return `${typeLabel}: ${start} ~ ${end}`
}

// 切换展开行
const toggleExpand = (row: UpgradePolicyItem) => {
  const rowKey = String(row.id)
  const index = expandRowKeys.value.indexOf(rowKey)
  expandRowKeys.value = index > -1
    ? expandRowKeys.value.filter(key => key !== rowKey)
    : [...expandRowKeys.value, rowKey]
}

// 检查用户是否可以编辑策略
const canEditPolicy = (policy: UpgradePolicyItem): boolean => {
  // ACTIVE/PAUSED 状态修改需要 release 权限
  if (policy.status === 'ACTIVE' || policy.status === 'PAUSED') {
    return userStore.hasPermission('fota:policy:release')
  }
  return userStore.hasPermission('fota:policy:update')
}

// 检查用户是否可以删除策略
const canDeletePolicy = (policy: UpgradePolicyItem): boolean => {
  // ACTIVE 状态永远不允许删除
  if (policy.status === 'ACTIVE') {
    return false
  }

  // PAUSED 状态需要 release 权限
  if (policy.status === 'PAUSED') {
    return userStore.hasPermission('fota:policy:release')
  }

  // 其他状态需要普通删除权限
  return userStore.hasPermission('fota:policy:delete')
}

// 检查策略是否正在流转
const isTransitionLoading = (id: number) => transitionLoadingIds.value.includes(id)

// 检查是否可以流转策略（是否有任意可切换的目标状态）
const canTransitionPolicy = (row: UpgradePolicyItem): boolean => {
  const availableTargets = getAvailableTargetStatuses(row.status)
  return availableTargets.some(target => canTransitionTo(row.status, target)) && !isTransitionLoading(row.id)
}

// 获取状态流转的 tooltip 提示
const getStatusTransitionTooltip = (row: UpgradePolicyItem): string => {
  if (!canTransitionPolicy(row)) return ''
  return t('policy.clickToChangeStatus')
}

const handleProductSearch = async (keyword: string) => {
  if (productSearchTimer !== null) {
    clearTimeout(productSearchTimer)
  }

  productSearchTimer = window.setTimeout(async () => {
    productSearchLoading.value = true
    try {
      const result = await searchProducts(keyword.trim())
      productSearchOptions.value = result.records || []
    } finally {
      productSearchLoading.value = false
    }
  }, 300)
}

const fetchList = async () => {
  loading.value = true
  try {
    const result = await pagePolicies(query)
    list.value = result.records || []
    total.value = result.total || 0
  } finally {
    loading.value = false
  }
}

const handleCreate = () => {
  router.push({ name: 'policy-create' })
}

const handleEdit = (row: UpgradePolicyItem) => {
  router.push({ name: 'policy-edit', params: { id: row.id } })
}

const handleDelete = async (row: UpgradePolicyItem) => {
  await ElMessageBox.confirm(t('common.deleteConfirm'), t('common.tip'), { type: 'warning' })
  await deletePolicy(row.id)
  ElMessage.success(t('common.deleteSuccess'))
  await fetchList()
}

// 状态切换处理
const handleStatusTransition = async (row: UpgradePolicyItem, targetStatus: PolicyStatus) => {
  if (!canTransitionTo(row.status, targetStatus) || isTransitionLoading(row.id)) return

  try {
    await ElMessageBox.confirm(
      t('policy.statusChangeConfirm', {
        from: t(resolveStatusLabelKey(row.status)),
        to: t(resolveStatusLabelKey(targetStatus))
      }),
      t('common.tip'),
      { type: 'warning' }
    )
  } catch {
    return // 用户取消
  }

  transitionLoadingIds.value.push(row.id)
  try {
    await updatePolicyStatus(row.id, targetStatus)
    ElMessage.success(t('policy.statusChangeSuccess'))
    await fetchList()
  } catch {
    // 错误由请求拦截器统一提示
  } finally {
    transitionLoadingIds.value = transitionLoadingIds.value.filter((id) => id !== row.id)
  }
}

const resetSearch = () => {
  query.page = 1
  query.productId = undefined
  query.name = ''
  query.status = undefined
  void fetchList()
}

const handleSearch = () => {
  query.page = 1
  void fetchList()
}

const handleFilterChange = () => {
  query.page = 1
  void fetchList()
}

// 使用工具函数格式化日期时间
const formatDateTime = (dateStr: string): string => {
  return utilsFormatDateTime(dateStr)
}

// 初始化数据
onMounted(async () => {
  void fetchList()
})

// 离开列表页时，如果前往表单页，标记返回时需要刷新
onBeforeRouteLeave((to, _from, next) => {
  shouldRefreshOnReturn = to.path.startsWith('/policy/create') || to.path.startsWith('/policy/edit')
  next()
})

// 使用 onActivated（如果使用了 keep-alive）
onActivated(() => {
  if (shouldRefreshOnReturn) {
    shouldRefreshOnReturn = false
    void fetchList()
  }
})
</script>

<template>
  <PageCardTableShell :title="t('policy.title')">
    <template #actions>
      <div class="flex items-center gap-2">
        <el-select
          v-model="query.productId"
          :loading="productSearchLoading"
          :placeholder="t('policy.product')"
          clearable
          filterable
          remote
          reserve-keyword
          :remote-method="handleProductSearch"
          style="width: 180px"
          @change="handleFilterChange"
        >
          <el-option
            v-for="product in productSearchOptions"
            :key="product.id"
            :label="product.name"
            :value="product.id"
          />
        </el-select>
        <el-input
          v-model="query.name"
          :placeholder="t('policy.name')"
          clearable
          style="width: 180px"
          @keyup.enter="handleSearch"
        />
        <el-select
          v-model="query.status"
          :placeholder="t('policy.statusPlaceholder')"
          clearable
          style="width: 180px"
          @change="handleFilterChange"
        >
          <el-option
            v-for="status in statusOptions"
            :key="status"
            :label="t(resolveStatusLabelKey(status))"
            :value="status"
          />
        </el-select>
        <el-button @click="handleSearch">
          {{ t('common.search') }}
        </el-button>
        <el-button @click="resetSearch">
          {{ t('common.refresh') }}
        </el-button>
        <el-button
          v-if="userStore.hasPermission('fota:policy:create')"
          type="primary"
          @click="handleCreate"
        >
          {{ t('policy.add') }}
        </el-button>
      </div>
    </template>

    <el-table
      v-loading="loading"
      :data="list"
      row-key="id"
      :expand-row-keys="expandRowKeys"
      stripe
      @row-click="toggleExpand"
      @sort-change="handleSortChange"
    >
      <!-- 展开列：显示详细信息 -->
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="policy-expand-content">
            <div class="expand-grid">
              <!-- 左侧列：配置信息 -->
              <div class="expand-left">
                <!-- 优先级、灰度、触发模式 -->
                <div class="expand-section compact">
                  <div class="compact-section-title">{{ t('policy.executionConfig') }}</div>
                  <div class="compact-section-body">
                    <div class="compact-info-item">
                      <span class="compact-label">P{{ row.priority }}</span>
                      <el-tag
                        size="small"
                        :type="getPriorityTagType(row.priority)"
                        :class="['priority-tag', 'priority-' + getPriorityClass(row.priority)]"
                      >
                        {{ getPriorityLabel(row.priority) }}
                      </el-tag>
                    </div>
                    <div class="compact-info-item">
                      <span class="compact-label">{{ t('policy.grayRate') }}</span>
                      <div class="flex items-center gap-2">
                        <el-progress
                          :percentage="row.grayRate"
                          :stroke-width="6"
                          :show-text="false"
                          :color="row.grayRate >= 80 ? '#67c23a' : row.grayRate >= 40 ? '#e6a23c' : '#909399'"
                          class="w-16"
                        />
                        <span class="text-xs text-gray-600 dark:text-gray-400">{{ row.grayRate }}%</span>
                      </div>
                    </div>
                    <div class="compact-info-item">
                      <span class="compact-label">{{ t('policy.triggerMode') }}</span>
                      <el-tag
                        size="small"
                        :type="row.triggerMode === 'MANUAL' ? 'warning' : 'success'"
                        effect="plain"
                      >
                        {{ getTriggerModeLabel(row.triggerMode) }}
                      </el-tag>
                    </div>
                  </div>
                </div>

                <!-- 时间窗口 -->
                <div class="expand-section compact">
                  <div class="compact-section-header">
                    <div class="compact-section-title">
                      <el-icon class="mr-1"><Clock /></el-icon>
                      {{ t('policy.timeWindow') }}
                    </div>
                    <el-tag
                      :type="row.timeWindow?.type === 'UNLIMITED' ? 'info' : 'primary'"
                      size="small"
                      effect="plain"
                    >
                      {{ row.timeWindow?.type || 'UNLIMITED' }}
                    </el-tag>
                  </div>
                  <div class="compact-section-body">
                    <div class="text-xs text-gray-700 dark:text-gray-300">
                      {{ getTimeWindowSummary(row.timeWindow) }}
                    </div>
                  </div>
                </div>
              </div>

              <!-- 右侧列：版本和目标 -->
              <div class="expand-right">
                <!-- 源版本 -->
                <div class="expand-section compact">
                  <div class="compact-section-header">
                    <div class="compact-section-title">
                      <el-icon class="mr-1"><Download /></el-icon>
                      {{ t('policy.sourceVersions') }}
                    </div>
                    <el-tag size="small" type="info" effect="plain">
                      {{ row.sourceVersions.length }} {{ t('policy.versions') }}
                    </el-tag>
                  </div>
                  <div class="compact-section-body">
                    <div class="version-tags compact">
                      <el-tag
                        v-for="versionId in row.sourceVersions.slice(0, 4)"
                        :key="versionId"
                        size="small"
                        type="info"
                        effect="plain"
                        class="version-tag"
                      >
                        v{{ row.sourceVersionNames?.[versionId] ?? versionId }}
                      </el-tag>
                      <el-tag
                        v-if="row.sourceVersions.length > 4"
                        size="small"
                        type="info"
                        effect="plain"
                        class="version-tag-more"
                      >
                        +{{ row.sourceVersions.length - 4 }}
                      </el-tag>
                    </div>
                  </div>
                </div>

                <!-- 目标范围 -->
                <div class="expand-section compact">
                  <div class="compact-section-header">
                    <div class="compact-section-title">
                      <el-icon class="mr-1"><Aim /></el-icon>
                      {{ t('policy.targetMode') }}
                    </div>
                    <div class="compact-section-actions">
                      <!-- 使用自定义样式标签以确保颜色统一 -->
                      <div
                        class="status-badge"
                        :class="`status-badge-${row.status.toLowerCase()}`"
                      >
                        <template v-if="row.status === 'TESTING' || row.status === 'VERIFIED'">
                          {{ getTargetModeLabel(row.targetMode) }}
                          <span class="mx-1">·</span>
                          <span class="text-xs">{{ t('policy.testingScopeLabel') }}</span>
                        </template>
                        <template v-else>
                          {{ getTargetModeLabel(row.targetMode) }}
                        </template>
                      </div>
                    </div>
                  </div>
                  <div class="compact-section-body">
                    <!-- 具体内容 -->
                    <div class="target-details">
                      <!-- ALL: 显示提示 -->
                      <div v-if="row.targetMode === 'ALL'" class="text-gray-500 dark:text-gray-400 text-xs">
                        <el-icon class="mr-1"><Grid /></el-icon>
                        {{ t('policy.targetAllDevices') }}
                      </div>

                      <!-- DEVICE_IDS: 显示设备IMEI列表 -->
                      <div v-else-if="row.targetMode === 'DEVICE_IDS' && row.targetImeis?.length" class="target-tags compact">
                        <el-tag
                          v-for="(imei, idx) in row.targetImeis.slice(0, 4)"
                          :key="idx"
                          size="small"
                          type="primary"
                          effect="plain"
                          class="target-tag"
                        >
                          {{ imei }}
                        </el-tag>
                        <el-tag
                          v-if="row.targetImeis.length > 4"
                          size="small"
                          type="info"
                          effect="plain"
                          class="target-tag-more"
                        >
                          +{{ row.targetImeis.length - 4 }}
                        </el-tag>
                      </div>

                      <!-- DEVICE_BATCHES: 显示批次ID列表 -->
                      <div v-else-if="row.targetMode === 'DEVICE_BATCHES' && row.targetDeviceBatchIds?.length" class="target-tags compact">
                        <el-tag
                          v-for="(batchId, idx) in row.targetDeviceBatchIds.slice(0, 4)"
                          :key="idx"
                          size="small"
                          type="success"
                          effect="plain"
                          class="target-tag"
                        >
                          <el-icon class="mr-1"><Box /></el-icon>
                          {{ batchId }}
                        </el-tag>
                        <el-tag
                          v-if="row.targetDeviceBatchIds.length > 4"
                          size="small"
                          type="info"
                          effect="plain"
                          class="target-tag-more"
                        >
                          +{{ row.targetDeviceBatchIds.length - 4 }}
                        </el-tag>
                      </div>

                      <!-- DEVICE_TAGS: 显示标签键值对 -->
                      <div v-else-if="row.targetMode === 'DEVICE_TAGS' && row.targetDeviceTags" class="target-tag-pairs compact">
                        <div
                          v-for="(value, key) in Object.entries(row.targetDeviceTags).slice(0, 3)"
                          :key="key"
                          class="tag-pair-item"
                        >
                          <el-tag size="small" type="warning" effect="plain" class="tag-pair-key">
                            {{ key }}
                          </el-tag>
                          <span class="tag-pair-separator">:</span>
                          <span class="tag-pair-value">{{ String(value) }}</span>
                        </div>
                        <el-tag
                          v-if="Object.keys(row.targetDeviceTags).length > 3"
                          size="small"
                          type="info"
                          effect="plain"
                          class="target-tag-more"
                        >
                          +{{ Object.keys(row.targetDeviceTags).length - 3 }}
                        </el-tag>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 审计信息：创建/修改人和时间 -->
            <div class="audit-info">
              <div class="audit-item">
                <el-icon class="audit-icon"><User /></el-icon>
                <span class="audit-label">{{ t('policy.createdBy') }}</span>
                <span class="audit-value">{{ row.createdByName ?? '-' }}</span>
                <span class="audit-time">{{ formatDateTime(row.createdAt) }}</span>
              </div>
              <div class="audit-item">
                <el-icon class="audit-icon"><User /></el-icon>
                <span class="audit-label">{{ t('policy.updatedBy') }}</span>
                <span class="audit-value">{{ row.updatedByName ?? '-' }}</span>
                <span class="audit-time">{{ formatDateTime(row.updatedAt) }}</span>
              </div>
            </div>
          </div>
        </template>
      </el-table-column>

      <!-- 主列：策略名称 -->
      <el-table-column
        prop="name"
        :label="t('policy.name')"
        min-width="180"
        show-overflow-tooltip
      />

      <!-- 所属产品 -->
      <el-table-column
        prop="productName"
        :label="t('policy.product')"
        min-width="200"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.productName || t('policy.product') + ` ${row.productId}` }}
        </template>
      </el-table-column>

      <!-- 目标版本 -->
      <el-table-column
        prop="firmwareVersion"
        :label="t('policy.firmwareVersion')"
        min-width="200"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.firmwareVersion || `v${row.firmwareVersionId}` }}
        </template>
      </el-table-column>

      <!-- 优先级 -->
      <el-table-column
        prop="priority"
        :label="t('policy.priority')"
        width="110"
        sortable="custom"
      >
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="getPriorityTagType(row.priority)"
            :class="['policy-priority-tag', getPriorityClass(row.priority)]"
          >
            P{{ row.priority }}
          </el-tag>
        </template>
      </el-table-column>

      <!-- 状态 -->
      <el-table-column
        prop="status"
        :label="t('policy.status')"
        width="120"
        fixed="right"
      >
        <template #default="{ row }">
          <el-dropdown
            v-if="canTransitionPolicy(row)"
            trigger="click"
            placement="bottom"
            @command="(status: PolicyStatus) => handleStatusTransition(row, status)"
          >
            <div
              class="status-dropdown-trigger"
              @click.stop
            >
              <span class="status-text" :class="`status-color-${row.status.toLowerCase()}`">
                {{ t(resolveStatusLabelKey(row.status)) }}
              </span>
              <el-icon class="status-arrow" :class="{ 'is-loading': isTransitionLoading(row.id) }">
                <ArrowDown />
              </el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu class="status-dropdown-menu">
                <div class="px-3 py-2 text-xs text-gray-500 dark:text-gray-400 border-b">
                  {{ t('policy.selectTargetStatus') }}
                </div>
                <el-dropdown-item
                  v-for="targetStatus in getAvailableTargetStatuses(row.status)"
                  :key="targetStatus"
                  :command="targetStatus"
                  :disabled="!canTransitionTo(row.status, targetStatus)"
                  class="status-dropdown-item"
                  :class="`status-item-${targetStatus.toLowerCase()}`"
                >
                  <div class="flex items-center justify-between w-full gap-3">
                    <div class="flex items-center gap-2">
                      <span class="status-dot w-2 h-2 rounded-full" :class="`status-dot-${targetStatus.toLowerCase()}`"></span>
                      <span class="status-name">{{ t(resolveStatusLabelKey(targetStatus)) }}</span>
                    </div>
                    <el-icon v-if="!canTransitionTo(row.status, targetStatus)" class="text-gray-400 text-sm">
                      <Lock />
                    </el-icon>
                  </div>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-tooltip
            v-else
            placement="top"
            :content="getStatusTransitionTooltip(row)"
          >
            <el-tag
              size="small"
              :type="getPolicyStatusTagType(row.status)"
              :class="['policy-status-tag', getPolicyStatusClass(row.status)]"
            >
              {{ t(resolveStatusLabelKey(row.status)) }}
            </el-tag>
          </el-tooltip>
        </template>
      </el-table-column>

      <!-- 操作（仅编辑/删除） -->
      <el-table-column
        v-if="canShowActions"
        :label="t('common.actions')"
        width="120"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            v-if="canEditPolicy(row)"
            link
            @click.stop="handleEdit(row)"
          >
            {{ t('common.edit') }}
          </el-button>
          <el-button
            v-if="canDeletePolicy(row)"
            link
            type="danger"
            @click.stop="handleDelete(row)"
          >
            {{ t('common.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="mt-4 flex justify-end">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        @current-change="fetchList"
        @size-change="fetchList"
      />
    </div>
  </PageCardTableShell>
</template>

<style scoped>
/* 状态标签样式（展开行中使用） */
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
  transition: all 0.2s ease;
}

/* DRAFT - 草稿（灰色系） */
.status-badge-draft {
  background-color: #f9fafb;
  color: #6b7280;
  border: 1px solid #e5e7eb;
}

/* TESTING - 测试中（橙色系） */
.status-badge-testing {
  background-color: #fffbeb;
  color: #d97706;
  border: 1px solid #fde68a;
}

/* VERIFIED - 已验证（靛蓝色系） */
.status-badge-verified {
  background-color: #eef2ff;
  color: #4f46e5;
  border: 1px solid #c7d2fe;
}

/* ACTIVE - 生产中（绿色系） */
.status-badge-active {
  background-color: #ecfdf5;
  color: #059669;
  border: 1px solid #a7f3d0;
}

/* PAUSED - 暂停（琥珀色系） */
.status-badge-paused {
  background-color: #fef3c7;
  color: #b45309;
  border: 1px solid #fcd34d;
}

/* EXPIRED - 已过期（浅灰色系） */
.status-badge-expired {
  background-color: #f3f4f6;
  color: #9ca3af;
  border: 1px solid #d1d5db;
}

/* 暗色主题适配 */
.dark .status-badge-draft {
  background-color: #2a2f3a;
  color: #9ca3af;
  border: 1px solid #3c4049;
}

.dark .status-badge-testing {
  background-color: #3d3a2a;
  color: #fbbf24;
  border: 1px solid #4d4938;
}

.dark .status-badge-verified {
  background-color: #2a2f4a;
  color: #818cf8;
  border: 1px solid #3a3f5a;
}

.dark .status-badge-active {
  background-color: #2a3a35;
  color: #34d399;
  border: 1px solid #3a4a40;
}

.dark .status-badge-paused {
  background-color: #3d3a2a;
  color: #fbbf24;
  border: 1px solid #4d4938;
}

.dark .status-badge-expired {
  background-color: #2a2f3a;
  color: #6b7280;
  border: 1px solid #3c4049;
}

/* 展开行内容样式 */
.policy-expand-content {
  padding: 12px 16px;
  background: var(--bg-hover);
}

.dark .policy-expand-content {
  background: var(--surface-fill);
}

.expand-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.expand-left,
.expand-right {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.expand-section {
  padding: 10px 12px;
  background: var(--bg-card);
  border-radius: 6px;
  border: 1px solid var(--border-light);
  transition: all 0.2s ease;
}

.expand-section:hover {
  border-color: var(--border-color);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.dark .expand-section:hover {
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.2);
}

.compact-section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
  display: flex;
  align-items: center;
}

.compact-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.compact-section-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.compact-section-body {
  font-size: 12px;
  color: var(--text-regular);
}

.compact-info-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.compact-info-item:last-child {
  margin-bottom: 0;
}

.compact-label {
  font-size: 12px;
  color: var(--text-secondary);
  min-width: 50px;
  text-align: right;
}

/* 紧凑版标签 */
.version-tags.compact {
  gap: 4px;
}

.version-tags.compact .el-tag {
  font-size: 11px;
  padding: 2px 6px;
}

.target-tags.compact {
  gap: 4px;
}

.target-tags.compact .el-tag {
  font-size: 11px;
  padding: 2px 6px;
}

.target-tag-pairs.compact {
  gap: 6px;
}

.target-tag-pairs.compact .tag-pair-item {
  padding: 3px 6px;
  font-size: 11px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.info-label {
  font-size: 12px;
  color: #909399;
  min-width: 60px;
}

/* 优先级标签 */
.priority-tag {
  border: none;
  font-weight: 600;
  padding: 4px 12px;
}

.priority-high {
  background: linear-gradient(135deg, #fee 0%, #fed7d7 100%);
  color: #c62828;
}

.priority-medium {
  background: linear-gradient(135deg, #fef3e7 0%, #feebc7 100%);
  color: #ad6800;
}

.priority-low {
  background: linear-gradient(135deg, #eef3f8 0%, #dcebfb 100%);
  color: #5b6b7f;
}

/* 版本标签 */
.version-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.version-tag {
  font-size: 12px;
  font-family: 'Courier New', monospace;
}

.version-tag-more {
  font-size: 11px;
  font-weight: 600;
}

/* 时间窗口显示 */
.time-window-display {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 目标模式显示 */
.target-mode-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.target-details {
}

.target-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.target-tag {
  font-family: 'Courier New', monospace;
  font-size: 12px;
}

.target-tag-more {
  font-size: 11px;
  font-weight: 600;
}

.target-tag-pairs {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.tag-pair-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: #fef3e7;
  border-radius: 4px;
  font-size: 12px;
}

.dark .tag-pair-item {
  background: #3d3a2a;
}

.tag-pair-key {
  font-weight: 600;
  font-family: 'Courier New', monospace;
}

.tag-pair-separator {
  color: #dcdfe6;
}

.tag-pair-value {
  color: var(--text-regular);
  font-family: 'Courier New', monospace;
}

/* 状态下拉触发器 */
.status-dropdown-trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;
}

.status-dropdown-trigger:hover {
  background-color: var(--bg-hover);
}

.status-text {
  font-size: 12px;
  font-weight: 500;
}

.status-arrow {
  font-size: 12px;
  color: #909399;
  transition: transform 0.2s ease;
}

.status-dropdown-trigger:hover .status-arrow {
  transform: translateY(1px);
}

.status-arrow.is-loading {
  animation: rotate 1s linear infinite;
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

/* 状态颜色（统一颜色系统 - 下拉触发器） */
.status-color-draft { color: #6b7280; }
.status-color-testing { color: #d97706; }
.status-color-verified { color: #4f46e5; }
.status-color-active { color: #059669; }
.status-color-paused { color: #b45309; }
.status-color-expired { color: #9ca3af; }

/* 下拉菜单中的状态点（统一颜色系统） */
.status-dot-draft { background-color: #6b7280; }
.status-dot-testing { background-color: #d97706; }
.status-dot-verified { background-color: #4f46e5; }
.status-dot-active { background-color: #059669; }
.status-dot-paused { background-color: #b45309; }
.status-dot-expired { background-color: #9ca3af; }

/* 下拉菜单项样式 */
:deep(.status-dropdown-item) {
  padding: 0 !important;
  margin: 2px 4px;
  border-radius: 4px;
  transition: all 0.2s ease;
}

:deep(.status-dropdown-item > div) {
  width: 100%;
  padding: 8px 12px;
  border-radius: 4px;
}

:deep(.status-dropdown-item:hover > div) {
  background-color: #f5f7fa;
}

:deep(.status-dropdown-item.is-disabled > div) {
  opacity: 0.5;
  cursor: not-allowed;
}

:deep(.status-dropdown-item.is-disabled:hover > div) {
  background-color: transparent;
}

/* 状态名称 */
.status-name {
  font-size: 13px;
  font-weight: 500;
}

:deep(.status-dropdown-item.is-disabled) {
  opacity: 0.6;
}

/* 表格行点击可展开的提示 */
:deep(.el-table__body-wrapper .el-table__row) {
  cursor: pointer;
}

/* 展开行样式优化 */
:deep(.el-table__expanded-cell) {
  padding: 0 !important;
}

:deep(.el-table__expand-icon) {
  cursor: pointer;
}

/* 状态标签样式优化 */
:deep(.policy-status-tag) {
  border: none;
  font-weight: 500;
  padding: 4px 10px;
}

/* 可点击状态标签 */
:deep(.policy-status-tag.is-clickable) {
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease, opacity 0.15s ease;
}

:deep(.policy-status-tag.is-clickable:hover) {
  transform: translateY(-1px);
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.12);
}

:deep(.policy-status-tag.is-transition-loading) {
  opacity: 0.65;
}

:deep(.policy-status-tag.is-draft) {
  background-color: #f9fafb;
  color: #6b7280;
}

:deep(.policy-status-tag.is-testing) {
  background-color: #fffbeb;
  color: #d97706;
}

:deep(.policy-status-tag.is-verified) {
  background-color: #eef2ff;
  color: #4f46e5;
}

:deep(.policy-status-tag.is-active) {
  background-color: #ecfdf5;
  color: #059669;
}

:deep(.policy-status-tag.is-paused) {
  background-color: #fef3c7;
  color: #b45309;
}

:deep(.policy-status-tag.is-expired) {
  background-color: #f3f4f6;
  color: #9ca3af;
}

/* 优先级标签样式 */
:deep(.policy-priority-tag) {
  border: none;
  font-weight: 600;
}

:deep(.policy-priority-tag.is-high) {
  background-color: #fdecec;
  color: #c62828;
}

:deep(.policy-priority-tag.is-medium) {
  background-color: #fff7e6;
  color: #ad6800;
}

:deep(.policy-priority-tag.is-low) {
  background-color: #eef3f8;
  color: #5b6b7f;
}

/* 审计信息样式 */
.audit-info {
  display: flex;
  gap: 24px;
  padding-top: 8px;
  margin-top: 8px;
  border-top: 1px dashed var(--border-light);
}

.audit-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.audit-icon {
  color: #909399;
  font-size: 14px;
}

.audit-label {
  color: #909399;
  font-weight: 500;
}

.audit-value {
  color: var(--text-primary);
  font-weight: 600;
}

.audit-time {
  color: var(--text-secondary);
  font-family: 'Courier New', monospace;
}
</style>
