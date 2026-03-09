import { get, put } from './request'

export interface RealtimeMetrics {
  loadScore: number
  loadLevel: string
  cpuUsage: number
  memoryUsage: number
  currentQps: number
  p99Latency: number
  activeRequests: number
  blockRate: number
  circuitState: string
  timestamp: string
}

export interface ControlParameter {
  productId?: number
  checkIntervalMultiplier: number
  downloadDelayMultiplier: number
  forceMaintenance: boolean
  maintenanceMessage: string
  updatedAt: string
  updatedBy: string
}

export const monitorApi = {
  getRealtimeMetrics: () => get<RealtimeMetrics>('/admin/monitor/realtime'),
}

export const controlApi = {
  getGlobalConfig: () => get<ControlParameter>('/admin/control/global'),
  updateGlobalConfig: (data: Partial<ControlParameter>) => put<void>('/admin/control/global', data),
  getProductConfig: (productId: number) => get<ControlParameter>(`/admin/control/product/${productId}`),
  updateProductConfig: (productId: number, data: Partial<ControlParameter>) => 
    put<void>(`/admin/control/product/${productId}`, data),
}
