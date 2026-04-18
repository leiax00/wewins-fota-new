import { get, post } from '@/api/request'

export type RefreshScope = 'product' | 'policy' | 'firmware' | 'all'

export type Granularity = 'hour' | 'day' | 'week'

// 拦截器解包后前端拿到的是 StatisticsResponseDTO 内容
export interface StatisticsResponse<T> {
  data: T
  dataCalculatedAt: string
  scope: string
  scopeId: number
}

// ---- 产品版本分布 ----

export interface ProductVersionDistributionItem {
  versionId: number
  version: string
  internalVersion?: string
  deviceCount: number
  percentage: number
}

export interface ProductVersionDistributionResponse {
  versionDistributions: ProductVersionDistributionItem[]
  neverVisitedCount: number
  totalDevices: number
}

// ---- 产品版本趋势 ----

export interface ProductVersionTrendSeries {
  versionId: number
  version: string
  counts: number[]
}

export interface ProductVersionTrendResponse {
  timestamps: string[]
  series: ProductVersionTrendSeries[]
  granularity: string
}

// ---- 设备升级轨迹 ----

export type TimelineEventType = 'CHECK' | 'DL_START' | 'DL_OK' | 'DL_FAIL' | 'UP_OK' | 'UP_FAIL'
export type TimelineCheckResult = 'UPDATE' | 'NO_UPDATE' | 'ERROR'

export interface TimelineItem {
  cursor: number
  eventTime: string
  eventType: TimelineEventType
  requestId?: string
  policyId?: number
  checkResult?: TimelineCheckResult
  targetVersionId?: number
  targetVersion?: string
  targetInternalVersion?: string
  details?: string
}

export interface DeviceTimelineResponse {
  timelineItems: TimelineItem[]
  nextCursor: number | null
}

// ---- 策略统计 ----

export interface PolicySummaryResponse {
  affectedTotal: number
  upgradedTotal: number
  pendingUpgradeTotal: number
}

// ---- 固件/产品设备列表（共用 DTO） ----

export interface DeviceListItem {
  id: number
  imei: string
  status: string
  productId?: number
  versionId?: number
  firstSeenAt?: string
  lastSeenAt?: string
  version?: string
  internalVersion?: string
}

export interface DeviceListResponse {
  devices: DeviceListItem[]
  nextCursor: number | null
}

// ---- 手动刷新 ----

export interface RefreshRequest {
  scope: RefreshScope
  id?: number
}

export interface RefreshResponse {
  success: boolean
  message: string
  dataCalculatedAt: string
}

// ---- API 函数 ----

export const getProductVersionDistribution = (productId: number, signal?: AbortSignal) =>
  get<StatisticsResponse<ProductVersionDistributionResponse>>(
    `/admin/statistics/products/${productId}/version-distribution`,
    { signal }
  )

export const getProductVersionTrend = (
  productId: number,
  params: { days?: number; granularity?: Granularity },
  signal?: AbortSignal
) =>
  get<StatisticsResponse<ProductVersionTrendResponse>>(
    `/admin/statistics/products/${productId}/trend`,
    { params, signal }
  )

export const getProductDevices = (
  productId: number,
  params: { cursor?: number; size?: number; keyword?: string },
  signal?: AbortSignal
) =>
  get<StatisticsResponse<DeviceListResponse>>(
    `/admin/statistics/products/${productId}/devices`,
    { params, signal }
  )

export const getDeviceTimeline = (
  imei: string,
  params: { days: number; cursor?: number; size?: number },
  signal?: AbortSignal
) =>
  get<StatisticsResponse<DeviceTimelineResponse>>(
    `/admin/statistics/devices/${imei}/timeline`,
    { params, signal }
  )

export const getPolicySummary = (policyId: number, signal?: AbortSignal) =>
  get<StatisticsResponse<PolicySummaryResponse>>(
    `/admin/statistics/policies/${policyId}/summary`,
    { signal }
  )

export const getFirmwareDeviceCount = (versionId: number, signal?: AbortSignal) =>
  get<StatisticsResponse<number>>(
    `/admin/statistics/firmware/${versionId}/device-count`,
    { signal }
  )

export const getFirmwareDevices = (
  versionId: number,
  params: { cursor?: number; size?: number; keyword?: string },
  signal?: AbortSignal
) =>
  get<StatisticsResponse<DeviceListResponse>>(
    `/admin/statistics/firmware/${versionId}/devices`,
    { params, signal }
  )

export const refreshStatistics = (params: RefreshRequest, signal?: AbortSignal) =>
  post<RefreshResponse>('/admin/statistics/refresh', undefined, { params, signal })

// 向后兼容别名（旧组件直接用了这些类型名）
export type FirmwareDeviceItem = DeviceListItem
export type FirmwareDeviceListResponse = DeviceListResponse
export type TimelineEvent = TimelineItem
export type ProductVersionDistributionItemType = ProductVersionDistributionItem
