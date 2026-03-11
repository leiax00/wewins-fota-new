import { get, put } from './request'

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
  priority: 'CRITICAL' | 'HIGH' | 'NORMAL' | 'LOW'
  intervalBias: number
  hotspotProtectionEnabled: boolean
}

export interface ControlState {
  region: string
  loadScore: number
  loadLevel: string
  recommendedMultiplier: number
  baseAutoInterval: number
  baseManualInterval: number
}

export interface RealtimeMetrics {
  loadScore: number
  loadLevel: string
  cpuUsage: number
  memoryUsage: number
  currentQps: number
  checkQps: number
  reportQps: number
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

export interface ControlParameter {
  productId?: number
  checkIntervalMultiplier: number
  downloadDelayMultiplier: number
  intervalBias: number
  minCheckIntervalSeconds: number
  maxCheckIntervalSeconds: number
  priority: 'CRITICAL' | 'HIGH' | 'NORMAL' | 'LOW'
  hotspotProtectionEnabled: boolean
  forceMaintenance: boolean
  maintenanceMessage: string
  updatedAt: string
  updatedBy: string
}

export const monitorApi = {
  getRealtimeMetrics: () => get<RealtimeMetrics>('/admin/monitor/realtime'),
  getHotProducts: () => get<HotProductMetrics[]>('/admin/monitor/products/hotspots'),
}

export const controlApi = {
  getGlobalConfig: () => get<ControlParameter>('/admin/control/global'),
  updateGlobalConfig: (data: Partial<ControlParameter>) => put<void>('/admin/control/global', data),
  getProductConfig: (productId: number) => get<ControlParameter>(`/admin/control/product/${productId}`),
  updateProductConfig: (productId: number, data: Partial<ControlParameter>) => 
    put<void>(`/admin/control/product/${productId}`, data),
}
