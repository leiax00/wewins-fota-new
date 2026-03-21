/**
 * 上传状态管理
 */
export interface UploadState {
  status: 'IDLE' | 'UPLOADING' | 'SUCCESS' | 'FAILED'
  percent: number
  uploadSessionId: string
  fileName: string
  fileSize: number
  error: string
  abortController: AbortController | null
}

/**
 * 任务进度状态
 */
export interface TaskProgressState {
  visible: boolean
  taskId: number
  stage: 'INIT' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  phase: 'UPLOAD' | 'CDN_WARM' // 当前阶段
  percent: number
  message: string
  errorMsg: string
  finished: boolean
}

/**
 * 表单数据接口
 */
export interface FirmwareFormData {
  productId?: number
  version: string
  internalVersion?: string
  noPackage?: boolean
  uploadSessionId?: string
  tags?: Record<string, unknown>
  meta?: Record<string, unknown>
}

/**
 * 查询参数接口
 */
export interface FirmwareQueryParams {
  page?: number
  size?: number
  productId?: number | undefined
  version?: string
  internalVersion?: string
}

/**
 * CDN 预热操作结果
 */
export interface WarmCdnResult {
  triggered: boolean
  pop?: string
  messageKey?: string
  actualPop?: string
}
