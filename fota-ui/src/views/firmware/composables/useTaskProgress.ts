import { ref, reactive, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { createTaskProgressStream, getTaskStatus, type TaskProgressEvent } from '@/api/firmware'
import type { TaskProgressState } from '../types'

export interface UseTaskProgressOptions {
  onTaskComplete?: () => void
  onTaskError?: (errorMsg: string) => void
}

export function useTaskProgress(options: UseTaskProgressOptions = {}) {
  const { t } = useI18n()

  const taskProgressRef = ref<ReturnType<typeof createTaskProgressStream> | null>(null)

  const taskProgress = reactive<TaskProgressState>({
    visible: false,
    taskId: 0,
    stage: 'INIT',
    phase: 'UPLOAD',
    percent: 0,
    message: '',
    errorMsg: '',
    finished: false,
  })

  /**
   * 翻译任务消息
   */
  const translateTaskMessage = (message?: string): string => {
    if (!message) return ''
    const translated = t(message)
    return translated !== message ? translated : message
  }

  /**
   * 判断当前阶段
   */
  const determinePhase = (message?: string): 'UPLOAD' | 'CDN_WARM' => {
    if (!message) return 'UPLOAD'
    const warmKeywords = ['预热', 'warm', 'Warm', 'CDN']
    return warmKeywords.some(keyword => message.includes(keyword)) ? 'CDN_WARM' : 'UPLOAD'
  }

  /**
   * 更新任务进度弹窗
   */
  const updateTaskProgress = (event: TaskProgressEvent) => {
    taskProgress.stage = event.stage
    taskProgress.percent = event.percent
    taskProgress.message = translateTaskMessage(event.message)
    taskProgress.phase = determinePhase(event.message)

    if (event.stage === 'COMPLETED') {
      taskProgress.finished = true
      taskProgress.phase = 'CDN_WARM'
      taskProgress.message = t('firmware.processSuccess')
      options.onTaskComplete?.()
    } else if (event.stage === 'FAILED' || event.stage === 'CANCELLED') {
      taskProgress.finished = true
      taskProgress.errorMsg = translateTaskMessage(event.message)
      options.onTaskError?.(taskProgress.errorMsg)
    }
  }

  /**
   * 打开任务进度弹窗
   */
  const openTaskProgressDialog = (taskId: number) => {
    taskProgress.visible = true
    taskProgress.taskId = taskId
    taskProgress.stage = 'INIT'
    taskProgress.phase = 'UPLOAD'
    taskProgress.percent = 0
    taskProgress.message = t('firmware.processingVersion')
    taskProgress.errorMsg = ''
    taskProgress.finished = false

    // 建立 SSE 连接
    taskProgressRef.value = createTaskProgressStream()
    taskProgressRef.value.connect(taskId, {
      onmessage: (event) => {
        updateTaskProgress(event)
      },
      onerror: (error) => {
        console.error('SSE 连接错误:', error)
        // 断线后尝试自动重连
        if (!taskProgress.finished) {
          setTimeout(() => {
            if (!taskProgress.finished && taskProgress.taskId) {
              reconnectTaskProgress()
            }
          }, 3000)
        }
      },
      onopen: () => {
        console.log('SSE 连接已建立')
      },
      onclose: () => {
        // 服务器主动断开连接，检查是否完成
        if (!taskProgress.finished) {
          reconnectTaskProgress()
        }
      },
    })
  }

  /**
   * 断线重连 - 查询任务状态
   */
  const reconnectTaskProgress = async () => {
    if (!taskProgress.taskId || taskProgress.finished) return

    try {
      const status = await getTaskStatus(taskProgress.taskId)
      taskProgress.stage = status.stage
      taskProgress.percent = status.percent
      taskProgress.message = translateTaskMessage(status.message)
      taskProgress.phase = determinePhase(status.message)

      if (status.stage === 'COMPLETED') {
        taskProgress.finished = true
        taskProgress.message = t('firmware.processSuccess')
        options.onTaskComplete?.()
      } else if (status.stage === 'FAILED' || status.stage === 'CANCELLED') {
        taskProgress.finished = true
        taskProgress.errorMsg = translateTaskMessage(status.errorMsg || status.message)
        options.onTaskError?.(taskProgress.errorMsg)
      } else {
        // 任务仍在进行中，重新建立 SSE 连接
        taskProgressRef.value?.disconnect()
        taskProgressRef.value = createTaskProgressStream()
        taskProgressRef.value.connect(taskProgress.taskId, {
          onmessage: (event) => {
            updateTaskProgress(event)
          },
          onerror: () => {
            // 再次失败则停止重连
          },
        })
      }
    } catch {
      // 查询失败，继续尝试重连
    }
  }

  /**
   * 关闭任务进度弹窗
   */
  const closeTaskProgressDialog = () => {
    taskProgressRef.value?.disconnect()
    taskProgressRef.value = null
    taskProgress.visible = false
    taskProgress.taskId = 0
    taskProgress.finished = false
  }

  /**
   * 完成进度弹窗 - 关闭并刷新列表
   */
  const finishTaskProgress = async () => {
    closeTaskProgressDialog()
  }

  onUnmounted(() => {
    taskProgressRef.value?.disconnect()
    taskProgressRef.value = null
  })

  return {
    t,
    taskProgress,
    openTaskProgressDialog,
    closeTaskProgressDialog,
    finishTaskProgress,
    reconnectTaskProgress,
    translateTaskMessage,
  }
}
