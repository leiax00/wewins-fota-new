import { get, post, put } from './request'

export interface HostMetrics {
  host: string
  cpuUsage: number
  memoryUsage: number
  networkInBytes: number
  networkOutBytes: number
}

export interface InstanceMetrics {
  instance: string
  cpuUsage: number
  memoryUsage: number
  currentQps: number
  checkQps: number
  reportQps: number
  p99Latency: number
  activeRequests: number
  blockRate: number
  circuitState: string
}

export interface HotProductMetrics {
  product: string
  checkQps: number
  reportQps: number
  trafficShare: number
}

export interface ControlState {
  region: string
  loadScore: number
  loadLevel: string
  recommendedMultiplier: number
}

export interface RealtimeMetrics {
  loadScore: number
  loadLevel: string
  cpuUsage: number
  memoryUsage: number
  currentQps: number
  checkQps: number
  reportQps: number
  p50Latency: number
  p99Latency: number
  activeRequests: number
  blockRate: number
  circuitState: string
  region: string
  host: string
  hostCpuUsage: number
  hostMemoryUsage: number
  networkInBytes: number
  networkOutBytes: number
  hostSummary: HostMetrics
  instanceSummary: InstanceMetrics
  hosts: HostMetrics[]
  instances: InstanceMetrics[]
  controlState: ControlState
  hotProducts: HotProductMetrics[]
  timestamp: string
}

export interface TrendPoint {
  timestamp: number
  value: number
}

export interface MonitorTrends {
  range: string
  stepSeconds: number
  checkQps: TrendPoint[]
  reportQps: TrendPoint[]
  p50Latency: TrendPoint[]
  p99Latency: TrendPoint[]
  blockRate: TrendPoint[]
}

export interface ControlParameter {
  productId?: number
  protectedIntervalMultiplier: number
  downloadDelayMultiplier: number
  minCheckIntervalSeconds: number
  maxCheckIntervalSeconds: number
  updatedAt: string
  updatedBy: string
}

export interface CacheEvictRequest {
  productId?: number
  productModel?: string
  imeis?: string[]
  evictProductCache?: boolean
  evictPolicyCache?: boolean
}

export interface CacheEvictResult {
  evictedScopes: number
  evictedDeviceCount: number
  message: string
}

export const monitorApi = {
  getRealtimeMetrics: () => get<RealtimeMetrics>('/admin/monitor/realtime'),
  getHotProducts: () => get<HotProductMetrics[]>('/admin/monitor/products/hotspots'),
  getTrends: (range = '15m') => get<MonitorTrends>('/admin/monitor/trends', { params: { range } }),
}

export const controlApi = {
  getGlobalConfig: () => get<ControlParameter>('/admin/control/global'),
  updateGlobalConfig: (data: Partial<ControlParameter>) => put<void>('/admin/control/global', data),
  getProductConfig: (productId: number) => get<ControlParameter>(`/admin/control/product/${productId}`),
  updateProductConfig: (productId: number, data: Partial<ControlParameter>) => 
    put<void>(`/admin/control/product/${productId}`, data),
}

export const cacheMonitorApi = {
  evict: (data: CacheEvictRequest) => post<CacheEvictResult>('/admin/cache/evict', data),
}
