import { ref, watch, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { UploadProps, UploadRequestOptions } from 'element-plus'
import type { UploadAjaxError } from 'element-plus/es/components/upload/src/ajax'
import { cancelUploadSession, uploadFirmwarePackage, getUploadSession } from '@/api/firmware'
import type { UploadState } from '../types'

export interface UseFirmwareUploadOptions {
  productId: number
  uploadState: UploadState
  form: { uploadSessionId?: string }
  onUploadSuccess?: (sessionId: string) => void
}

export function useFirmwareUpload(options: UseFirmwareUploadOptions) {
  const { t } = useI18n()
  const uploadRef = ref()
  const retryFile = ref<File | null>(null)
  const uploadTicket = ref(0)

  const createUploadAjaxError = (message: string): UploadAjaxError => {
    const error = new Error(message) as UploadAjaxError
    error.name = 'UploadAjaxError'
    error.status = 400
    error.method = 'POST'
    error.url = '/admin/firmware/upload'
    return error
  }

  /**
   * 重置上传状态
   */
  const resetUploadState = () => {
    options.uploadState.status = 'IDLE'
    options.uploadState.percent = 0
    options.uploadState.uploadSessionId = ''
    options.uploadState.fileName = ''
    options.uploadState.fileSize = 0
    options.uploadState.error = ''
    options.uploadState.abortController = null
    options.form.uploadSessionId = ''
    retryFile.value = null
    uploadRef.value?.clearFiles()
  }

  /**
   * 取消上传
   */
  const cancelUpload = async () => {
    if (options.uploadState.abortController) {
      options.uploadState.abortController.abort()
    }
    if (options.uploadState.uploadSessionId) {
      try {
        await cancelUploadSession(options.uploadState.uploadSessionId)
      } catch {
        // ignore cancel error
      }
    }
    resetUploadState()
  }

  /**
   * 上传前校验
   */
  const beforeUpload: UploadProps['beforeUpload'] = () => {
    if (!options.productId) {
      ElMessage.warning(t('firmware.selectProductFirst'))
      return false
    }
    return true
  }

  /**
   * 自定义上传方法
   */
  const customUpload: UploadProps['httpRequest'] = async (uploadOptions: UploadRequestOptions) => {
    const file = uploadOptions.file as File
    if (!options.productId) {
      const err = createUploadAjaxError(t('firmware.selectProductFirst'))
      options.uploadState.status = 'FAILED'
      options.uploadState.error = err.message
      uploadOptions.onError?.(err)
      return
    }

    retryFile.value = file
    const currentTicket = Date.now()
    uploadTicket.value = currentTicket

    const abortController = new AbortController()
    options.uploadState.abortController = abortController
    options.uploadState.status = 'UPLOADING'
    options.uploadState.percent = 0
    options.uploadState.error = ''
    options.uploadState.fileName = file.name
    options.uploadState.fileSize = file.size

    // 先取消旧的上传会话，避免临时文件泄漏
    const oldSessionId = options.uploadState.uploadSessionId
    options.uploadState.uploadSessionId = ''
    options.form.uploadSessionId = ''
    if (oldSessionId) {
      try {
        await cancelUploadSession(oldSessionId)
      } catch {
        // 忽略取消失败，继续上传
      }
    }

    try {
      // 调用上传API
      const response = await uploadFirmwarePackage(
        file,
        options.productId,
        (percent) => {
          if (uploadTicket.value !== currentTicket) return
          options.uploadState.percent = percent
        },
        abortController.signal
      )

      if (uploadTicket.value !== currentTicket) return

      options.uploadState.uploadSessionId = response.sessionId
      options.form.uploadSessionId = response.sessionId
      options.uploadState.percent = 100

      // 查询会话详情获取准确的文件信息
      try {
        const session = await getUploadSession(response.sessionId)
        options.uploadState.fileName = session.fileName || file.name
        options.uploadState.fileSize = session.fileSize || file.size
      } catch {
        // ignore, fallback to local file info
      }

      options.uploadState.status = 'SUCCESS'
      uploadOptions.onSuccess?.(response as unknown as Record<string, unknown>)
      options.onUploadSuccess?.(response.sessionId)

      // 清空上传组件的文件列表，允许选择相同文件重新上传
      uploadRef.value?.clearFiles()
    } catch (e: unknown) {
      if (uploadTicket.value !== currentTicket) return

      // 检查是否是取消操作
      const aborted = abortController.signal.aborted
      if (aborted) {
        options.uploadState.status = 'IDLE'
        options.uploadState.error = ''
        options.uploadState.percent = 0
        return
      }

      const message = e instanceof Error ? e.message : t('firmware.uploadFailed')
      options.uploadState.status = 'FAILED'
      options.uploadState.error = message
      uploadOptions.onError?.(createUploadAjaxError(message))
    } finally {
      if (uploadTicket.value === currentTicket) {
        options.uploadState.abortController = null
      }
    }
  }

  /**
   * 重试上传
   */
  const retryUpload = async () => {
    if (!retryFile.value || !options.productId) return

    const retryOptions: UploadRequestOptions = {
      action: '',
      method: 'post',
      data: {},
      filename: 'file',
      file: retryFile.value as never,
      headers: {},
      withCredentials: false,
      onProgress: () => {},
      onSuccess: () => {},
      onError: () => {},
    }
    await customUpload(retryOptions)
  }

  /**
   * 监听 uploadSessionId 状态变化
   */
  watch(
    () => options.uploadState.uploadSessionId,
    (val) => {
      if (val) {
        options.onUploadSuccess?.(val)
      }
    }
  )

  onUnmounted(() => {
    // 组件卸载时清理上传会话
    if (options.uploadState.uploadSessionId) {
      cancelUploadSession(options.uploadState.uploadSessionId).catch(() => {
        // ignore cancel error
      })
    }
  })

  return {
    t,
    uploadRef,
    resetUploadState,
    cancelUpload,
    retryUpload,
    beforeUpload,
    customUpload,
    createUploadAjaxError,
  }
}
