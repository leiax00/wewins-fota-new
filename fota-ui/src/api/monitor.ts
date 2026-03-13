import { del, get, post, put } from './request'
import { getToken } from '@/utils/auth'

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
  todayActiveDevices: number
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
  activeDevicesTotal: TrendPoint[]
  activeDevicesIncrement: TrendPoint[]
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

export interface CacheEvictPreview {
  estimatedScopes: number
  estimatedDeviceCount: number
  affectedTargets: string[]
  executable: boolean
  message: string
}

export interface OperationLogItem {
  id: number
  moduleCode: string
  resourceCode: string
  actionCode: string
  operationType: string
  targetId?: string
  targetName?: string
  operatorId?: number
  operatorUsername?: string
  operatorDisplayName?: string
  clientIp?: string
  occurredAt: string
}

export interface OperationLogDetail extends OperationLogItem {
  requestMethod: string
  requestPath: string
  requestQuery?: Record<string, unknown> | null
  requestBody?: Record<string, unknown> | unknown[] | null
  userAgent?: string
}

export interface OperationLogPageQuery {
  page: number
  size: number
  operatorKeyword?: string
  moduleCode?: string
  resourceCode?: string
  operationType?: string
  targetId?: string
  timeRange?: string[]
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

export interface RedisInfo {
  version: string
  mode: string
  connectedClients: number
  usedMemory: number
  usedMemoryPeak: number
  totalSystemMemory: number
  memoryUsagePercent: number
  totalKeys: number
  expiredKeys: number
  evictedKeys: number
  keyspaceHits: number
  keyspaceMisses: number
  hitRate: number
  totalCommandsProcessed: number
  instantaneousOpsPerSec: number
  uptimeInSeconds: number
  rdbLastSaveTime: number
  rdbLastStatus: string
  aofEnabled: boolean
  dbSizes: Record<string, number>
  rawInfo: Record<string, string>
}

export interface RedisKeyItem {
  key: string
  type: string
  ttl: number
  memoryUsage: number
}

export interface RedisKeyList {
  keys: RedisKeyItem[]
  total: number
  page?: number
  pageSize?: number
  cursor?: string
}

export interface RedisKeyDetail {
  key: string
  type: string
  ttl: number
  memoryUsage: number
  encoding: string
  value: unknown
  length: number
  error?: string
}

export const cacheMonitorApi = {
  evict: (data: CacheEvictRequest) => post<CacheEvictResult>('/admin/cache/evict', data),
  previewEvict: (data: CacheEvictRequest) => post<CacheEvictPreview>('/admin/cache/evict/preview', data),
  getInfo: () => get<RedisInfo>('/admin/cache/info'),
  listKeys: (params: { pattern?: string; cursor?: string; pageSize?: number }) =>
    get<RedisKeyList>('/admin/cache/keys', { params }),
  getKeyDetail: (key: string) => get<RedisKeyDetail>(`/admin/cache/keys/${encodeURIComponent(key)}`),
  setKeyValue: (key: string, value: string, ttl?: number) =>
    put<boolean>(`/admin/cache/keys/${encodeURIComponent(key)}`, { value, ttl }),
  deleteKey: (key: string) => del<boolean>(`/admin/cache/keys/${encodeURIComponent(key)}`),
  setKeyTtl: (key: string, ttl: number) =>
    put<boolean>(`/admin/cache/keys/${encodeURIComponent(key)}/ttl`, null, { params: { ttl } }),
}

export const operationLogApi = {
  pageLogs: (params: OperationLogPageQuery) => get<{
    records: OperationLogItem[]
    page: number
    size: number
    total: number
    pages: number
  }>('/admin/monitor/operation-logs', {
    params,
    paramsSerializer: {
      serialize: (input) => {
        const search = new URLSearchParams()
        Object.entries(input).forEach(([key, value]) => {
          if (value === undefined || value === null || value === '') {
            return
          }
          if (Array.isArray(value)) {
            value.forEach((item) => {
              if (item !== undefined && item !== null && item !== '') {
                search.append(key, String(item))
              }
            })
            return
          }
          search.append(key, String(value))
        })
        return search.toString()
      },
    },
  }),
  getLogDetail: (id: number) => get<OperationLogDetail>(`/admin/monitor/operation-logs/${id}`),
}

/**
 * 日志事件结构
 */
export interface LogEvent {
  timestamp: number
  level: string
  logger: string
  message: string
  thread: string
  formattedTime: string
}

/**
 * SSE 事件类型
 */
interface SSEMessageEvent extends MessageEvent {
  data: LogEvent | string
}

/**
 * SSE 事件处理器
 */
interface SSEEventHandlers {
  onmessage?: (event: SSEMessageEvent) => void
  onerror?: (error: Error) => void
  onopen?: () => void
}

/**
 * 创建 SSE 日志流连接
 * @returns 返回 SSE 连接和清理函数
 */
export function createLogStream(): {
  connect: (handlers: SSEEventHandlers) => () => void
  disconnect: () => void
  isConnected: () => boolean
} {
  let abortController: AbortController | null = null
  let reader: ReadableStreamDefaultReader<Uint8Array> | null = null

  const disconnect = () => {
    if (reader) {
      reader.cancel().catch(() => {})
      reader = null
    }
    if (abortController) {
      abortController.abort()
      abortController = null
    }
  }

  const isConnected = () => abortController !== null && !abortController.signal.aborted

  const connect = (handlers: SSEEventHandlers) => {
    disconnect()

    const token = getToken()
    const url = `${import.meta.env.VITE_API_BASE_URL}/admin/monitor/logs/stream`

    abortController = new AbortController()

    fetch(url, {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${token}`,
      },
      signal: abortController.signal,
    })
      .then((response) => {
        if (!response.ok) {
          throw new Error(`SSE 连接失败: ${response.status} ${response.statusText}`)
        }

        handlers.onopen?.()

        reader = response.body?.getReader() || null
        if (!reader) {
          throw new Error('无法获取响应流')
        }
        const activeReader = reader

        const decoder = new TextDecoder()
        let buffer = '' // 用于处理跨 chunk 的数据
        let currentData = '' // 当前事件数据

        const read = async () => {
          try {
            while (true) {
              const { done, value } = await activeReader.read()
              if (done) break

              // 解码并添加到缓冲区
              buffer += decoder.decode(value, { stream: true })

              // 按行处理
              const lines = buffer.split('\n')
              // 保留最后一个可能不完整的行
              buffer = lines.pop() || ''

              for (const line of lines) {
                if (line === '') {
                  // 空行表示事件结束
                  if (currentData) {
                    try {
                      // 去掉 'data: ' 前缀
                      const dataStr = currentData.replace(/^data:\s*/, '')
                      if (dataStr && dataStr !== '[DONE]') {
                        const event = JSON.parse(dataStr) as LogEvent
                        handlers.onmessage?.({ data: event } as SSEMessageEvent)
                      }
                    } catch (e) {
                      // 忽略 JSON 解析错误
                    }
                    currentData = ''
                  }
                } else if (line.startsWith('event:')) {
                  continue
                } else if (line.startsWith('data:')) {
                  const data = line.slice(5).trim()
                  if (currentData) {
                    currentData += '\n' + data // 支持多行 data
                  } else {
                    currentData = data
                  }
                } else if (line.startsWith(':')) {
                  // 注释行，忽略
                  continue
                }
              }
            }
          } catch (e) {
            if (!abortController?.signal.aborted) {
              handlers.onerror?.(e as Error)
            }
          }
        }

        read()
      })
      .catch((error) => {
        if (!abortController?.signal.aborted) {
          handlers.onerror?.(error)
        }
      })

    return disconnect
  }

  return {
    connect,
    disconnect,
    isConnected,
  }
}
