<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadProps, UploadRequestOptions } from 'element-plus'
import type { UploadAjaxError } from 'element-plus/es/components/upload/src/ajax'
import { CircleCheck, CircleClose, Clock, Loading, Document, Files, Box } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import JsonFieldEditor from '@/components/json-field/JsonFieldEditor.vue'
import { loadJsonFieldSchema, type JsonFieldDefinition } from '@/components/json-field/utils/schema-loader'
import { formatDateTime } from '@/utils/date'
import { trimFormValues } from '@/utils/form'
import {
  cancelUploadSession,
  publishFirmwareVersion,
  updatePublishedFirmwareVersion,
  createTaskProgressStream,
  deleteFirmwareVersion,
  getTaskStatus,
  getUploadSession,
  pageFirmwareVersions,
  uploadFirmwarePackage,
  warmCdn,
  type FirmwareVersionItem,
  type TaskProgressEvent,
} from '@/api/firmware'
import { searchProducts, type ProductItem } from '@/api/product'

/**
 * 上传状态管理
 */
interface UploadState {
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
interface TaskProgressState {
  visible: boolean
  taskId: number
  stage: 'INIT' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  percent: number
  message: string
  errorMsg: string
  finished: boolean
}

const { t } = useI18n()
const userStore = useUserStore()
const uploadRef = ref()

/**
 * 任务进度 SSE 连接管理
 */
const taskProgressRef = ref<ReturnType<typeof createTaskProgressStream> | null>(null)

const list = ref<FirmwareVersionItem[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({
  page: 1,
  size: 20,
  productId: undefined as number | undefined,
  version: '',
  internalVersion: '',
})

const productSearchOptions = ref<ProductItem[]>([])
const productSearchLoading = ref(false)

let productSearchTimer: number | null = null

const canShowActions = computed(() =>
  userStore.hasPermission('fota:firmware:update') ||
  userStore.hasPermission('fota:firmware:delete')
)

// 展开行控制
const expandRowKeys = ref<string[]>([])

// 切换展开行
const toggleExpand = (row: FirmwareVersionItem) => {
  const rowKey = String(row.id)
  const index = expandRowKeys.value.indexOf(rowKey)
  expandRowKeys.value = index > -1
    ? expandRowKeys.value.filter(key => key !== rowKey)
    : [...expandRowKeys.value, rowKey]
}

// 检查是否可以执行预热
const canWarmCdn = (row: FirmwareVersionItem): boolean => {
  return userStore.hasPermission('fota:firmware:update') && row.packageStatus === 'READY'
}

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref()

/**
 * 表单数据
 */
const form = reactive({
  productId: undefined as number | undefined,
  version: '',
  internalVersion: '',
  noPackage: false,
  uploadSessionId: '',
  tags: {} as Record<string, unknown>,
  meta: {} as Record<string, unknown>,
})

/**
 * JSON 字段 Schema（用于显示字段标签）
 */
const tagsSchema = ref<JsonFieldDefinition[]>([])
const metaSchema = ref<JsonFieldDefinition[]>([])

/**
 * 根据 key 获取字段的显示标签
 */
const getFieldLabel = (schema: JsonFieldDefinition[], key: string): string => {
  const field = schema.find(f => f.key === key)
  if (field?.i18nKey) {
    return t(field.i18nKey)
  }
  return field?.label || key
}

/**
 * 判断字段是否需要独占一行（i18n 或 textarea 类型）
 */
const isBlockField = (schema: JsonFieldDefinition[], key: string, value: unknown): boolean => {
  // i18n 字段（值是语言代码映射对象）
  if (isI18nField(value)) return true

  // 查找 schema 中的字段定义
  const field = schema.find(f => f.key === key)
  if (field?.config?.schema?.type === 'textarea') return true

  return false
}

/**
 * 检查 meta 中是否有普通字段（非 i18n、非 textarea)
 */
const hasNormalMetaFields = (meta: string | null | undefined): boolean => {
  const parsed = parseJsonObject(meta)
  if (!parsed) return false

  for (const key in parsed) {
    const value = parsed[key]
    if (!isI18nField(value) && !isBlockField(metaSchema.value, String(key), value)) {
      return true
    }
  }
  return false
}

/**
 * 上传状态
 */
const uploadState = reactive<UploadState>({
  status: 'IDLE',
  percent: 0,
  uploadSessionId: '',
  fileName: '',
  fileSize: 0,
  error: '',
  abortController: null,
})

/**
 * 任务进度弹窗状态
 */
const taskProgress = reactive<TaskProgressState>({
  visible: false,
  taskId: 0,
  stage: 'INIT',
  percent: 0,
  message: '',
  errorMsg: '',
  finished: false,
})

const translateTaskMessage = (message?: string) => {
  const messageKeyMap: Record<string, string> = {
    '开始创建无包版本': 'firmware.uploadSessionCreate',
    '固件版本处理完成': 'firmware.taskCompleted',
    '开始更新版本信息': 'firmware.taskStartUpdate',
    '版本信息已更新': 'firmware.taskUpdated',
    '开始转存文件到对象存储': 'firmware.taskStartTransfer',
    '文件转存完成': 'firmware.taskTransferred',
    '版本记录已更新': 'firmware.taskVersionReady',
    '开始CDN预热': 'firmware.taskStartWarm',
    'CDN预热完成': 'firmware.taskWarmCompleted',
    'CDN预热失败，已记录': 'firmware.taskWarmFailedRecorded',
  }
  const key = message ? messageKeyMap[message] : undefined
  return key ? t(key) : message || ''
}

const formatWarmResultMessage = (result: { messageKey?: string; pop?: string; triggered: boolean }) => {
  const baseKey = result.messageKey || (result.triggered ? 'firmware.warmRequested' : 'firmware.warmFailed')
  let message = t(baseKey)
  if (result.pop) {
    message += t('firmware.actualPopSuffix', { pop: result.pop })
  }
  return message
}

/**
 * 更新任务进度弹窗
 */
const updateTaskProgress = (event: TaskProgressEvent) => {
  taskProgress.stage = event.stage
  taskProgress.percent = event.percent
  taskProgress.message = translateTaskMessage(event.message)

  if (event.stage === 'COMPLETED') {
    taskProgress.finished = true
    taskProgress.message = t('firmware.processSuccess')
  } else if (event.stage === 'FAILED' || event.stage === 'CANCELLED') {
    taskProgress.finished = true
    taskProgress.errorMsg = translateTaskMessage(event.message)
  }
}

/**
 * 打开任务进度弹窗
 */
const openTaskProgressDialog = (taskId: number) => {
  taskProgress.visible = true
  taskProgress.taskId = taskId
  taskProgress.stage = 'INIT'
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

    if (status.stage === 'COMPLETED') {
      taskProgress.finished = true
      taskProgress.message = t('firmware.processSuccess')
    } else if (status.stage === 'FAILED' || status.stage === 'CANCELLED') {
      taskProgress.finished = true
      taskProgress.errorMsg = translateTaskMessage(status.errorMsg || status.message)
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
  await fetchList()
}

/**
 * 用于重试的文件缓存
 */
const retryFile = ref<File | null>(null)
/**
 * 上传请求票据，用于取消过期请求
 */
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
 * 表单校验规则
 */
const formRules = {
  productId: [{ required: true, message: t('firmware.productIdRequired'), trigger: 'change' }],
  version: [{ required: true, message: t('firmware.versionRequired'), trigger: 'blur' }],
  uploadSessionId: [
    {
      validator: (_rule: unknown, _value: unknown, callback: (error?: Error) => void) => {
        if (form.noPackage) {
          callback()
          return
        }
        if (dialogMode.value === 'create' && uploadState.status !== 'SUCCESS') {
          callback(new Error(t('firmware.uploadRequired')))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
}

/**
 * 重置上传状态
 */
const resetUploadState = () => {
  uploadState.status = 'IDLE'
  uploadState.percent = 0
  uploadState.uploadSessionId = ''
  uploadState.fileName = ''
  uploadState.fileSize = 0
  uploadState.error = ''
  uploadState.abortController = null
  form.uploadSessionId = ''
  retryFile.value = null

  // 清空上传组件的文件列表
  uploadRef.value?.clearFiles()
}

/**
 * 取消上传
 */
const cancelUpload = async () => {
  if (uploadState.abortController) {
    uploadState.abortController.abort()
  }
  if (uploadState.uploadSessionId) {
    try {
      await cancelUploadSession(uploadState.uploadSessionId)
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
  if (!form.productId) {
    ElMessage.warning(t('firmware.selectProductFirst'))
    return false
  }
  return true
}

/**
 * 自定义上传方法
 */
const customUpload: UploadProps['httpRequest'] = async (options: UploadRequestOptions) => {
  const file = options.file as File
  if (!form.productId) {
    const err = createUploadAjaxError(t('firmware.selectProductFirst'))
    uploadState.status = 'FAILED'
    uploadState.error = err.message
    options.onError?.(err)
    return
  }

  retryFile.value = file
  const currentTicket = Date.now()
  uploadTicket.value = currentTicket

  const abortController = new AbortController()
  uploadState.abortController = abortController
  uploadState.status = 'UPLOADING'
  uploadState.percent = 0
  uploadState.error = ''
  uploadState.fileName = file.name
  uploadState.fileSize = file.size

  // 先取消旧的上传会话，避免临时文件泄漏
  const oldSessionId = uploadState.uploadSessionId
  uploadState.uploadSessionId = ''
  form.uploadSessionId = ''
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
      form.productId,
      (percent) => {
        if (uploadTicket.value !== currentTicket) return
        uploadState.percent = percent
      },
      abortController.signal
    )

    if (uploadTicket.value !== currentTicket) return

    uploadState.uploadSessionId = response.sessionId
    form.uploadSessionId = response.sessionId
    uploadState.percent = 100

    // 查询会话详情获取准确的文件信息
    try {
      const session = await getUploadSession(response.sessionId)
      uploadState.fileName = session.fileName || file.name
      uploadState.fileSize = session.fileSize || file.size
    } catch {
      // ignore, fallback to local file info
    }

    uploadState.status = 'SUCCESS'
    options.onSuccess?.(response as unknown as Record<string, unknown>)
    formRef.value?.validateField('uploadSessionId')

    // 清空上传组件的文件列表，允许选择相同文件重新上传
    uploadRef.value?.clearFiles()
  } catch (e: unknown) {
    if (uploadTicket.value !== currentTicket) return

    // 检查是否是取消操作
    const aborted = abortController.signal.aborted
    if (aborted) {
      uploadState.status = 'IDLE'
      uploadState.error = ''
      uploadState.percent = 0
      return
    }

    const message = e instanceof Error ? e.message : t('firmware.uploadFailed')
    uploadState.status = 'FAILED'
    uploadState.error = message
    options.onError?.(createUploadAjaxError(message))
  } finally {
    if (uploadTicket.value === currentTicket) {
      uploadState.abortController = null
    }
  }
}

/**
 * 重试上传
 */
const retryUpload = async () => {
  if (!retryFile.value || !form.productId || submitting.value) return

  const options: UploadRequestOptions = {
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
  await customUpload(options)
}

/**
 * 产品搜索
 */
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

/**
 * 获取列表数据
 */
const fetchList = async () => {
  loading.value = true
  try {
    const result = await pageFirmwareVersions(trimFormValues(query))
    list.value = result.records || []
    total.value = result.total || 0
  } finally {
    loading.value = false
  }
}

/**
 * 打开新增弹窗
 */
const openCreateDialog = () => {
  dialogMode.value = 'create'
  editingId.value = null
  form.productId = undefined
  form.version = ''
  form.internalVersion = ''
  form.noPackage = false
  form.uploadSessionId = ''
  form.tags = {}
  form.meta = {}
  resetUploadState()
  dialogVisible.value = true
}

/**
 * 打开编辑弹窗
 */
const openEditDialog = (row: FirmwareVersionItem) => {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.productId = row.productId
  form.version = row.version
  form.internalVersion = row.internalVersion || ''
  form.noPackage = row.packageStatus === 'NONE'
  form.uploadSessionId = ''
  // 解析 JSON 字符串为对象
  form.tags = row.tags ? JSON.parse(row.tags) : {}
  form.meta = row.meta ? JSON.parse(row.meta) : {}
  resetUploadState()
  dialogVisible.value = true
}

/**
 * 提交表单
 */
const submitForm = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    const payload: Record<string, unknown> = trimFormValues({
      productId: form.productId!,
      version: form.version,
      internalVersion: form.internalVersion || undefined,
      // 序列化对象为 JSON 字符串，空对象不发送
      tags: Object.keys(form.tags).length > 0 ? JSON.stringify(form.tags) : undefined,
      meta: Object.keys(form.meta).length > 0 ? JSON.stringify(form.meta) : undefined,
    })

    // 处理包状态
    if (form.noPackage) {
      payload.packageStatus = 'NONE'
    } else if (uploadState.status === 'SUCCESS' && uploadState.uploadSessionId) {
      payload.uploadSessionId = uploadState.uploadSessionId
    } else if (dialogMode.value === 'create') {
      ElMessage.warning(t('firmware.uploadOrNoPackageRequired'))
      submitting.value = false
      return
    }

    if (dialogMode.value === 'create') {
      const response = await publishFirmwareVersion(payload as never)
      resetUploadState()
      dialogVisible.value = false
      openTaskProgressDialog(response.taskId)
    } else if (editingId.value) {
      const response = await updatePublishedFirmwareVersion(editingId.value, payload as never)
      resetUploadState()
      dialogVisible.value = false
      openTaskProgressDialog(response.taskId)
    }
  } catch (e: unknown) {
    // 保存失败：保留上传状态，不重置，允许用户修改后重试
    const message = e instanceof Error ? e.message : t('firmware.saveFailedRetry')
    ElMessage.error(message)
  } finally {
    submitting.value = false
  }
}

/**
 * 删除固件版本
 */
const handleDelete = async (row: FirmwareVersionItem) => {
  await ElMessageBox.confirm(t('common.deleteConfirm'), t('common.tip'), { type: 'warning' })
  await deleteFirmwareVersion(row.id)
  ElMessage.success(t('common.deleteSuccess'))
  await fetchList()
}

/**
 * CDN 预热
 */
const handleWarmCdn = async (row: FirmwareVersionItem) => {
  try {
    await ElMessageBox.confirm(
      t('firmware.warmConfirmMessage'),
      t('firmware.warmConfirmTitle'),
      { type: 'info', confirmButtonText: t('firmware.warmConfirmButton'), cancelButtonText: t('common.cancel') }
    )

    const result = await warmCdn(row.id)
    if (result.triggered) {
      ElMessage.success(formatWarmResultMessage(result))
    } else {
      ElMessage.warning(formatWarmResultMessage(result))
    }
  } catch (e) {
    if (e !== 'cancel') {
      const message = e instanceof Error ? e.message : t('firmware.warmError')
      ElMessage.error(message)
    }
  }
}

/**
 * 对话框关闭时清理上传会话
 */
const handleDialogClosed = async () => {
  // 清理上传会话（无论提交成功还是失败）
  // - 失败场景：用户直接关闭对话框，需要清理
  // - 成功场景：后端已清理会话，前端调用 cancel 可能会报错，忽略即可
  if (uploadState.uploadSessionId) {
    try {
      await cancelUpload()
    } catch {
      // 忽略清理失败（后端可能已清理）
    }
  }
}

/**
 * 重置搜索
 */
const resetSearch = () => {
  query.page = 1
  query.productId = undefined
  query.version = ''
  query.internalVersion = ''
  void fetchList()
}

/**
 * 格式化文件大小
 */
const formatFileSize = (bytes: number) => {
  if (!bytes) return '-'
  const kb = bytes / 1024
  const mb = kb / 1024
  if (mb >= 1) return `${mb.toFixed(2)} MB`
  if (kb >= 1) return `${kb.toFixed(2)} KB`
  return `${bytes} B`
}

/**
 * 解析 JSON 字符串为对象
 */
const parseJsonObject = (jsonStr?: string): Record<string, unknown> | null => {
  if (!jsonStr) return null
  try {
    return JSON.parse(jsonStr)
  } catch {
    return null
  }
}

/**
 * 获取包状态标签类型
 */
const getPackageStatusTagType = (status?: string): 'success' | 'info' | 'warning' => {
  if (status === 'READY') return 'success'
  if (status === 'NONE') return 'info'
  return 'warning'
}

/**
 * 检查是否为 i18n 字段格式
 */
const isI18nField = (value: unknown): value is Record<string, string> => {
  if (!value || typeof value !== 'object') return false
  const keys = Object.keys(value)
  if (keys.length === 0) return false
  // 检查是否所有 key 都是语言代码格式 (如 zh-CN, en-US)
  const localePattern = /^[a-z]{2}(-[A-Z]{2})?$/
  return keys.every(key => localePattern.test(key))
}

/**
 * 获取 i18n 字段的语言标签
 */
const getLocaleLabel = (locale: string): string => {
  const localeMap: Record<string, string> = {
    'zh-CN': '简体中文',
    'en-US': 'English',
    'ja-JP': '日本語',
    'ko-KR': '한국어',
    'de-DE': 'Deutsch',
    'fr-FR': 'Français',
    'es-ES': 'Español',
  }
  return localeMap[locale] || locale
}

/**
 * 监听"无包版本"开关变化
 */
watch(
  () => form.noPackage,
  async (val) => {
    if (val) {
      // 勾选时取消上传
      await cancelUpload()
    } else {
      // 取消勾选时重置状态
      resetUploadState()
    }
    formRef.value?.validateField('uploadSessionId')
  }
)

/**
 * 初始化
 */
onMounted(async () => {
  void handleProductSearch('')
  void fetchList()

  // 加载 JSON 字段 Schema（用于显示字段标签）
  try {
    const [tagsDef, metaDef] = await Promise.all([
      loadJsonFieldSchema('json_schema.firmware_tags'),
      loadJsonFieldSchema('json_schema.firmware_meta'),
    ])
    tagsSchema.value = tagsDef
    metaSchema.value = metaDef
  } catch (error) {
    console.error('Failed to load JSON field schema:', error)
  }
})

/**
 * 组件卸载时清理 SSE 连接
 */
onUnmounted(() => {
  taskProgressRef.value?.disconnect()
})
</script>

<template>
  <PageCardTableShell :title="t('firmware.title')">
    <template #actions>
      <div class="flex items-center gap-2">
        <el-select
          v-model="query.productId"
          :loading="productSearchLoading"
          :placeholder="t('firmware.product')"
          clearable
          filterable
          remote
          reserve-keyword
          :remote-method="handleProductSearch"
          style="width: 180px"
          @change="fetchList"
        >
          <el-option
            v-for="product in productSearchOptions"
            :key="product.id"
            :label="product.name"
            :value="product.id"
          />
        </el-select>
        <el-input
          v-model="query.version"
          :placeholder="t('firmware.version')"
          clearable
          style="width: 140px"
          @keyup.enter="fetchList"
        />
        <el-input
          v-model="query.internalVersion"
          :placeholder="t('firmware.internalVersion')"
          clearable
          style="width: 140px"
          @keyup.enter="fetchList"
        />
        <el-button @click="fetchList">
          {{ t('common.search') }}
        </el-button>
        <el-button @click="resetSearch">
          {{ t('common.refresh') }}
        </el-button>
        <el-button
          v-if="userStore.hasPermission('fota:firmware:create')"
          type="primary"
          @click="openCreateDialog"
        >
          {{ t('firmware.add') }}
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
    >
      <!-- 展开列：显示详细信息 -->
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="firmware-expand-content">
            <!-- 顶部：基本信息 -->
            <div class="expand-header">
              <div class="expand-header__row">
                <div class="expand-header__item">
                  <span class="expand-header__label">{{ t('firmware.version') }}</span>
                  <span class="expand-header__value text-lg font-semibold">{{ row.version }}</span>
                </div>
                <div v-if="row.internalVersion" class="expand-header__item">
                  <span class="expand-header__label">{{ t('firmware.internalVersion') }}</span>
                  <span class="expand-header__value font-mono">{{ row.internalVersion }}</span>
                </div>
                <div class="expand-header__item">
                  <span class="expand-header__label">{{ t('firmware.product') }}</span>
                  <span class="expand-header__value">{{ row.productName || row.productId }}</span>
                </div>
                <div class="expand-header__item">
                  <span class="expand-header__label">{{ t('firmware.packageStatus') }}</span>
                  <el-tag
                    :type="getPackageStatusTagType(row.packageStatus)"
                    size="large"
                  >
                    {{ row.packageStatus === 'READY' ? t('firmware.packageStatusReady') : row.packageStatus === 'NONE' ? t('firmware.packageStatusNone') : row.packageStatus }}
                  </el-tag>
                </div>
              </div>
            </div>

            <!-- 中间：两栏布局 -->
            <div class="expand-body">
              <!-- 左侧：文件信息 + 短标签 -->
              <div class="expand-left">
                <!-- 文件信息 -->
                <div class="expand-section">
                  <div class="expand-section__title">
                    <el-icon><Files /></el-icon>
                    <span>{{ t('firmware.fileInfo') }}</span>
                    <el-tag v-if="row.packageStatus" size="small" :type="getPackageStatusTagType(row.packageStatus)" class="ml-2">
                      {{ row.packageStatus === 'READY' ? t('firmware.packageStatusReady') : t('firmware.packageStatusNone') }}
                    </el-tag>
                  </div>
                  <div class="expand-section__content">
                    <template v-if="row.packageStatus === 'READY'">
                      <div class="info-row">
                        <span class="info-row__label">{{ t('firmware.fileName') }}</span>
                        <span class="info-row__value font-mono">{{ row.fileUrl ? row.fileUrl.split('/').pop() : '-' }}</span>
                      </div>
                      <div class="info-row">
                        <span class="info-row__label">{{ t('firmware.fileSize') }}</span>
                        <span class="info-row__value">{{ row.fileSize ? formatFileSize(row.fileSize) : '-' }}</span>
                      </div>
                      <div class="info-row">
                        <span class="info-row__label">{{ t('firmware.md5') }}</span>
                        <span class="info-row__value info-row__value--hash">{{ row.md5 || '-' }}</span>
                      </div>
                      <div class="info-row">
                        <span class="info-row__label">{{ t('firmware.sha256') }}</span>
                        <span class="info-row__value info-row__value--hash">{{ row.sha256 || '-' }}</span>
                      </div>
                      <!-- CDN 预热操作 -->
                      <div class="info-row info-row--action">
                        <span class="info-row__label">{{ t('firmware.warmCdn') }}</span>
                        <el-button
                          type="warning"
                          size="small"
                          :disabled="!canWarmCdn(row)"
                          @click.stop="handleWarmCdn(row)"
                        >
                          {{ t('firmware.warm') }}
                        </el-button>
                      </div>
                    </template>
                    <template v-else>
                      <div class="empty-placeholder">
                        <el-icon :size="24"><Box /></el-icon>
                        <span>{{ t('firmware.noFilePackage') }}</span>
                      </div>
                    </template>
                  </div>
                </div>

                <!-- 标签 (紧凑展示) -->
                <div v-if="parseJsonObject(row.tags) && Object.keys(parseJsonObject(row.tags) || {}).length > 0" class="expand-section expand-section--compact">
                  <div class="expand-section__title">
                    <el-icon><Box /></el-icon>
                    <span>{{ t('firmware.tags') }}</span>
                    <el-tag size="small" type="info" effect="plain" class="ml-2">
                      {{ Object.keys(parseJsonObject(row.tags) || {}).length }} {{ t('firmware.fieldsCount') }}
                    </el-tag>
                  </div>
                  <div class="expand-section__content">
                    <div class="tags-compact">
                      <div
                        v-for="(value, key) in parseJsonObject(row.tags)"
                        :key="key"
                        class="tag-compact"
                      >
                        <span class="tag-compact__key">{{ getFieldLabel(tagsSchema, String(key)) }}</span>
                        <span class="tag-compact__value">{{ String(value) }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 右侧：元数据 (i18n 专门渲染) -->
              <div class="expand-right">
                <div v-if="parseJsonObject(row.meta)" class="expand-section">
                  <div class="expand-section__title">
                    <el-icon><Document /></el-icon>
                    <span>{{ t('firmware.meta') }}</span>
                    <el-tag size="small" type="info" effect="plain" class="ml-2">
                      {{ Object.keys(parseJsonObject(row.meta) || {}).length }} {{ t('firmware.fieldsCount') }}
                    </el-tag>
                  </div>
                  <div class="expand-section__content">
                    <!-- 流式堆叠区域：普通字段 -->
                    <div v-if="hasNormalMetaFields(row.meta)" class="meta-flow">
                      <template v-for="(value, key) in parseJsonObject(row.meta)" :key="key">
                        <div v-if="!isI18nField(value) && !isBlockField(metaSchema, String(key), value)" class="meta-flow__item">
                          <span class="meta-flow__label">{{ getFieldLabel(metaSchema, String(key)) }}</span>
                          <span class="meta-flow__value">{{ String(value) }}</span>
                        </div>
                      </template>
                    </div>
                    <!-- 独占一行区域：i18n 和 textarea 字段 -->
                    <template v-for="(value, key) in parseJsonObject(row.meta)" :key="key">
                      <!-- i18n 字段特殊渲染 -->
                      <div v-if="isI18nField(value)" class="meta-item meta-item--full">
                        <div class="meta-item__header">
                          <el-tag size="small" type="primary" effect="plain">{{ getFieldLabel(metaSchema, String(key)) }}</el-tag>
                          <span class="meta-item__badge">{{ t('firmware.i18nField') }}</span>
                        </div>
                        <div class="i18n-list">
                          <div
                            v-for="(text, locale) in value"
                            :key="locale"
                            class="i18n-item"
                          >
                            <el-tag size="small" type="info" effect="plain" class="i18n-item__locale">
                              {{ getLocaleLabel(locale) }}
                            </el-tag>
                            <span class="i18n-item__text">{{ text }}</span>
                          </div>
                        </div>
                      </div>
                      <!-- textarea 字段 -->
                      <div v-else-if="isBlockField(metaSchema, String(key), value)" class="meta-item meta-item--full">
                        <div class="meta-item__header">
                          <el-tag size="small" type="warning" effect="plain">{{ getFieldLabel(metaSchema, String(key)) }}</el-tag>
                        </div>
                        <div class="meta-item__textarea">{{ String(value) }}</div>
                      </div>
                    </template>
                  </div>
                </div>
                <div v-else class="expand-section expand-section--empty">
                  <div class="expand-section__title">
                    <el-icon><Document /></el-icon>
                    <span>{{ t('firmware.meta') }}</span>
                  </div>
                  <div class="expand-section__content">
                    <div class="empty-placeholder empty-placeholder--small">
                      <span>{{ t('firmware.noMeta') }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 底部：审计信息 -->
            <div class="expand-footer">
              <div class="audit-item">
                <el-icon class="audit-icon"><Clock /></el-icon>
                <span class="audit-label">{{ t('firmware.uploadTime') }}</span>
                <span class="audit-time">{{ formatDateTime(row.createdAt) }}</span>
              </div>
              <div v-if="row.updatedAt && row.updatedAt !== row.createdAt" class="audit-item">
                <el-icon class="audit-icon"><Clock /></el-icon>
                <span class="audit-label">{{ t('common.updateTime') }}</span>
                <span class="audit-time">{{ formatDateTime(row.updatedAt) }}</span>
              </div>
            </div>
          </div>
        </template>
      </el-table-column>

      <el-table-column
        prop="productId"
        :label="t('firmware.product')"
        min-width="160"
      >
        <template #default="{ row }">
          {{ row.productName || row.productId }}
        </template>
      </el-table-column>
      <el-table-column
        prop="version"
        :label="t('firmware.version')"
        min-width="160"
        show-overflow-tooltip
      />
      <el-table-column
        prop="internalVersion"
        :label="t('firmware.internalVersion')"
        min-width="200"
        show-overflow-tooltip
      />
      <el-table-column
        prop="packageStatus"
        :label="t('firmware.packageStatus')"
        min-width="100"
      >
        <template #default="{ row }">
          <el-tag
            v-if="row.packageStatus === 'READY'"
            type="success"
          >
            {{ t('firmware.packageStatusReady') }}
          </el-tag>
          <el-tag
            v-else-if="row.packageStatus === 'NONE'"
            type="info"
          >
            {{ t('firmware.packageStatusNone') }}
          </el-tag>
          <el-tag
            v-else
            type="warning"
          >
            {{ row.packageStatus }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="fileSize"
        :label="t('firmware.fileSize')"
        min-width="100"
      >
        <template #default="{ row }">
          {{ row.fileSize ? formatFileSize(row.fileSize) : '-' }}
        </template>
      </el-table-column>
      <el-table-column
        :label="t('firmware.uploadTime')"
        width="170"
      >
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>

      <el-table-column
        v-if="canShowActions"
        :label="t('common.actions')"
        width="120"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            v-if="userStore.hasPermission('fota:firmware:update')"
            link
            class="ui-action-primary"
            @click.stop="openEditDialog(row)"
          >
            {{ t('common.edit') }}
          </el-button>
          <el-button
            v-if="userStore.hasPermission('fota:firmware:delete')"
            link
            class="ui-action-danger"
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

  <el-dialog
    v-model="dialogVisible"
    :title="dialogMode === 'create' ? t('firmware.add') : t('common.edit')"
    width="680px"
    @closed="handleDialogClosed"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="formRules"
      label-width="120px"
    >
      <el-form-item
        prop="productId"
        :label="t('firmware.product')"
      >
        <el-select
          v-model="form.productId"
          :loading="productSearchLoading"
          :disabled="dialogMode === 'edit'"
          filterable
          remote
          reserve-keyword
          :remote-method="handleProductSearch"
          style="width: 100%"
        >
          <el-option
            v-for="product in productSearchOptions"
            :key="product.id"
            :label="product.name"
            :value="product.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item
        prop="version"
        :label="t('firmware.version')"
      >
        <el-input
          v-model="form.version"
          placeholder="1.0.0"
          :disabled="dialogMode === 'edit'"
        />
      </el-form-item>

      <el-form-item
        prop="internalVersion"
        :label="t('firmware.internalVersion')"
      >
        <el-input
          v-model="form.internalVersion"
          :placeholder="t('firmware.internalVersionPlaceholder')"
          :disabled="dialogMode === 'edit'"
        />
      </el-form-item>

      <el-form-item :label="t('firmware.noPackageVersion')">
        <el-switch v-model="form.noPackage" />
        <span class="ml-2 text-sm text-gray-500">
          {{ `（${t('firmware.noPackageVersionTip')}）` }}
        </span>
      </el-form-item>

      <el-form-item
        v-if="!form.noPackage"
        prop="uploadSessionId"
        :label="t('firmware.uploadPackage')"
      >
        <div class="firmware-upload-panel">
          <!-- 上传按钮 -->
          <el-upload
            ref="uploadRef"
            class="w-full"
            :show-file-list="false"
            :limit="1"
            :auto-upload="true"
            :http-request="customUpload"
            :before-upload="beforeUpload"
            accept=".bin,.zip,.tar,.tar.gz,.rar"
            :disabled="submitting || uploadState.status === 'UPLOADING'"
          >
            <el-button
              class="firmware-upload-panel__button"
              type="primary"
              plain
              :loading="uploadState.status === 'UPLOADING'"
              :disabled="submitting || !form.productId"
            >
              <template v-if="uploadState.status === 'IDLE'">
                {{ t('firmware.selectAndUpload') }}
              </template>
              <template v-else>
                {{ t('firmware.uploading') }}
              </template>
            </el-button>
          </el-upload>

          <!-- 文件信息显示 -->
          <div
            v-if="uploadState.fileName"
            class="firmware-upload-panel__meta"
          >
            <div class="grid grid-cols-2 gap-2">
              <div>
                <span class="firmware-upload-panel__meta-label">{{ t('firmware.fileName') }}</span>
                <span class="firmware-upload-panel__meta-value">{{ uploadState.fileName }}</span>
              </div>
              <div>
                <span class="firmware-upload-panel__meta-label">{{ t('firmware.fileSizeLabel') }}</span>
                <span class="firmware-upload-panel__meta-value">{{ formatFileSize(uploadState.fileSize) }}</span>
              </div>
            </div>
          </div>

          <!-- 进度条 -->
          <el-progress
            v-if="uploadState.status === 'UPLOADING' || uploadState.status === 'SUCCESS'"
            class="mt-3"
            :percentage="uploadState.percent"
            :status="uploadState.status === 'SUCCESS' ? 'success' : undefined"
          >
            <template #default="{ percentage }">
              <span class="text-sm">
                {{ uploadState.status === 'SUCCESS' ? `✓ ${t('firmware.uploadSuccess')}` : `${percentage}%` }}
              </span>
            </template>
          </el-progress>

          <!-- 失败提示 -->
          <el-alert
            v-if="uploadState.status === 'FAILED'"
            class="mt-3"
            type="error"
            :closable="false"
            :title="uploadState.error || t('firmware.uploadFailed')"
          />

          <!-- 操作按钮 -->
          <div class="mt-3 flex items-center gap-2">
            <el-button
              v-if="uploadState.status === 'UPLOADING'"
              size="small"
              @click="cancelUpload"
            >
              {{ t('firmware.cancelUpload') }}
            </el-button>
            <el-button
              v-if="uploadState.status === 'FAILED'"
              size="small"
              type="warning"
              @click="retryUpload"
            >
              {{ t('firmware.retryUpload') }}
            </el-button>
            <el-button
              v-if="uploadState.status === 'SUCCESS' && !submitting"
              size="small"
              @click="cancelUpload"
            >
              {{ t('firmware.replaceFile') }}
            </el-button>
          </div>
        </div>
      </el-form-item>

      <el-form-item :label="t('firmware.tags')">
        <JsonFieldEditor
          v-model="form.tags"
          dict-type-code="json_schema.firmware_tags"
          mode="form"
          class="w-full"
          :allow-mode-switch="true"
          :disabled="submitting"
        />
      </el-form-item>

      <el-form-item :label="t('firmware.meta')">
        <JsonFieldEditor
          v-model="form.meta"
          dict-type-code="json_schema.firmware_meta"
          mode="form"
          class="w-full"
          :allow-mode-switch="true"
          :disabled="submitting"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="uploadState.status === 'UPLOADING'"
        @click="submitForm"
      >
        {{ t('common.save') }}
      </el-button>
    </template>
  </el-dialog>

  <!-- 任务进度弹窗 -->
  <el-dialog
    v-model="taskProgress.visible"
    :title="t('firmware.publishProgress')"
    width="480px"
    :close-on-click-modal="taskProgress.finished"
    :close-on-press-escape="taskProgress.finished"
    :show-close="taskProgress.finished"
  >
    <div class="task-progress">
      <!-- 上传固件阶段 -->
      <div class="task-progress__item">
        <div class="task-progress__icon">
          <el-icon v-if="taskProgress.stage === 'PROCESSING' || taskProgress.stage === 'COMPLETED'">
            <CircleCheck />
          </el-icon>
          <el-icon v-else-if="taskProgress.stage === 'FAILED' || taskProgress.stage === 'CANCELLED'">
            <CircleClose />
          </el-icon>
          <el-icon v-else>
            <Loading />
          </el-icon>
        </div>
        <div class="task-progress__content">
          <div class="task-progress__title">{{ t('firmware.taskUploadToStorage') }}</div>
          <div class="task-progress__status">
            <template v-if="taskProgress.stage === 'INIT'">
              <span class="task-progress__pending">{{ t('firmware.taskWaiting') }}</span>
            </template>
            <template v-else-if="taskProgress.stage === 'PROCESSING'">
              <el-progress
                :percentage="taskProgress.percent"
                :status="taskProgress.percent === 100 ? 'success' : undefined"
                :show-text="false"
              />
              <span class="task-progress__message">{{ taskProgress.message || `${taskProgress.percent}%` }}</span>
            </template>
            <template v-else-if="taskProgress.stage === 'COMPLETED'">
              <span class="task-progress__done">{{ t('firmware.taskDone') }}</span>
            </template>
            <template v-else-if="taskProgress.stage === 'FAILED' || taskProgress.stage === 'CANCELLED'">
              <span class="task-progress__error">{{ t('firmware.taskFailed') }}</span>
            </template>
          </div>
        </div>
      </div>

      <!-- CDN 预热阶段 -->
      <div class="task-progress__item">
        <div class="task-progress__icon">
          <template v-if="taskProgress.stage === 'COMPLETED'">
            <el-icon><CircleCheck /></el-icon>
          </template>
          <template v-else-if="taskProgress.stage === 'FAILED' || taskProgress.stage === 'CANCELLED'">
            <el-icon><CircleClose /></el-icon>
          </template>
          <template v-else-if="taskProgress.stage === 'PROCESSING'">
            <el-icon class="is-loading"><Loading /></el-icon>
          </template>
          <template v-else>
            <el-icon><Clock /></el-icon>
          </template>
        </div>
        <div class="task-progress__content">
          <div class="task-progress__title">{{ t('firmware.taskCdnWarm') }}</div>
          <div class="task-progress__status">
            <template v-if="taskProgress.stage === 'PROCESSING' && taskProgress.percent > 0">
              <el-progress
                :percentage="taskProgress.percent"
                :status="taskProgress.percent === 100 ? 'success' : undefined"
                :show-text="false"
              />
              <span class="task-progress__message">{{ taskProgress.message || t('firmware.taskWarming') }}</span>
            </template>
            <template v-else-if="taskProgress.stage === 'COMPLETED'">
              <span class="task-progress__done">{{ t('firmware.taskDone') }}</span>
            </template>
            <template v-else-if="taskProgress.stage === 'FAILED' || taskProgress.stage === 'CANCELLED'">
              <span class="task-progress__error">{{ taskProgress.errorMsg || t('firmware.taskFailed') }}</span>
            </template>
            <template v-else>
              <span class="task-progress__pending">{{ t('firmware.taskWaiting') }}</span>
            </template>
          </div>
        </div>
      </div>

      <!-- 错误信息展示 -->
      <el-alert
        v-if="taskProgress.stage === 'FAILED' || taskProgress.stage === 'CANCELLED'"
        class="mt-4"
        type="error"
        :closable="false"
        :title="taskProgress.errorMsg || t('firmware.taskPublishFailed')"
      />
    </div>

    <template #footer>
      <el-button
        v-if="taskProgress.finished"
        type="primary"
        @click="finishTaskProgress"
      >
        {{ t('firmware.close') }}
      </el-button>
      <el-button
        v-else
        @click="closeTaskProgressDialog"
      >
        {{ t('common.cancel') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.firmware-upload-panel {
  width: 100%;
  padding: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-bg-color-overlay);
}

.firmware-upload-panel__meta {
  margin-top: 12px;
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.firmware-upload-panel__meta-label {
  color: var(--el-text-color-secondary);
}

.firmware-upload-panel__meta-value {
  margin-left: 4px;
  color: var(--el-text-color-primary);
  font-weight: 500;
  word-break: break-all;
}

.firmware-upload-panel__button {
  --el-button-bg-color: color-mix(in srgb, var(--el-color-primary) 14%, var(--el-bg-color-overlay));
  --el-button-border-color: color-mix(in srgb, var(--el-color-primary) 38%, var(--el-border-color));
  --el-button-text-color: var(--el-color-primary);
  --el-button-hover-bg-color: color-mix(in srgb, var(--el-color-primary) 20%, var(--el-bg-color-overlay));
  --el-button-hover-border-color: var(--el-color-primary);
  --el-button-hover-text-color: var(--el-color-primary);
  --el-button-active-bg-color: color-mix(in srgb, var(--el-color-primary) 24%, var(--el-bg-color-overlay));
  --el-button-active-border-color: var(--el-color-primary);
  --el-button-active-text-color: var(--el-color-primary);
  --el-button-disabled-bg-color: var(--el-fill-color-light);
  --el-button-disabled-border-color: var(--el-border-color-lighter);
  --el-button-disabled-text-color: var(--el-text-color-placeholder);
}

/* 任务进度弹窗样式 */
.task-progress {
  padding: 8px 0;
}

.task-progress__item {
  display: flex;
  align-items: flex-start;
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.task-progress__item:last-child {
  border-bottom: none;
}

.task-progress__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  margin-right: 12px;
  font-size: 20px;
  border-radius: 50%;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
}

.task-progress__icon .el-icon {
  color: var(--el-color-success);
}

.task-progress__icon .is-loading {
  animation: rotate 1s linear infinite;
  color: var(--el-color-primary);
}

.task-progress__icon .el-icon:last-child {
  color: var(--el-color-danger);
}

.task-progress__content {
  flex: 1;
  min-width: 0;
}

.task-progress__title {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  margin-bottom: 8px;
}

.task-progress__status {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}

.task-progress__status .el-progress {
  flex: 1;
}

.task-progress__message {
  color: var(--el-text-color-secondary);
  min-width: 60px;
}

.task-progress__done {
  color: var(--el-color-success);
}

.task-progress__error {
  color: var(--el-color-danger);
}

.task-progress__pending {
  color: var(--el-text-color-placeholder);
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

/* 展开行内容样式 */
.firmware-expand-content {
  padding: 20px 24px;
  background: var(--bg-hover);
}

.dark .firmware-expand-content {
  background: var(--surface-fill);
}

/* 顶部信息栏 */
.expand-header {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-bottom: 20px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--border-light);
}

.expand-header__row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 32px;
}

.expand-header__row--compact {
  gap: 24px;
  margin-top: 4px;
}

.expand-header__item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.expand-header__item--compact {
  gap: 8px;
}

.expand-header__label {
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
}

.expand-header__value {
  font-size: 15px;
  color: var(--text-primary);
}

/* 中间两栏布局 */
.expand-body {
  display: grid;
  grid-template-columns: 1fr 1.5fr;
  gap: 20px;
  margin-bottom: 20px;
}

.expand-left,
.expand-right {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 区块样式 */
.expand-section {
  background: var(--bg-card);
  border-radius: 10px;
  border: 1px solid var(--border-light);
  overflow: hidden;
  transition: all 0.2s ease;
}

.expand-section:hover {
  border-color: var(--border-color);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.dark .expand-section:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.25);
}

.expand-section--compact {
  flex: 1;
}

.expand-section--empty {
  opacity: 0.7;
}

.expand-section--full {
  grid-column: 1 / -1;
}

.expand-section__title {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 18px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  background: var(--el-fill-color-light);
  border-bottom: 1px solid var(--border-light);
}

.expand-section__content {
  padding: 16px 18px;
}

/* 信息行 */
.info-row {
  display: flex;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid var(--border-lighter);
}

.info-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.info-row:first-child {
  padding-top: 0;
}

.info-row--action {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--border-light);
  border-bottom: none;
}

.info-row__label {
  flex-shrink: 0;
  width: 80px;
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
}

.info-row__value {
  flex: 1;
  font-size: 13px;
  color: var(--text-primary);
  word-break: break-all;
}

.info-row__value--hash {
  font-family: 'SF Mono', 'Courier New', monospace;
  font-size: 11px;
  background: var(--el-fill-color);
  padding: 6px 10px;
  border-radius: 6px;
  word-break: break-all;
  color: var(--text-secondary);
}

/* 空状态占位 */
.empty-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 32px;
  color: var(--text-secondary);
  font-size: 13px;
}

.empty-placeholder--small {
  padding: 20px;
}

/* 紧凑标签 */
.tags-compact {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-compact {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background: var(--el-fill-color);
  border-radius: 6px;
  font-size: 12px;
}

.tag-compact__key {
  color: var(--el-color-primary);
  font-weight: 600;
}

.tag-compact__value {
  color: var(--text-primary);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 元数据网格 */
.meta-grid {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.meta-item {
  padding: 14px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.meta-item--i18n {
  background: linear-gradient(135deg, var(--el-fill-color-light) 0%, var(--el-color-primary-light-9) 100%);
}

.meta-item--normal {
  display: flex;
  align-items: center;
  gap: 12px;
}

.meta-item__header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.meta-item__badge {
  font-size: 11px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-8);
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 500;
}

.meta-item__key {
  flex-shrink: 0;
}

/* 流式堆叠区域 */
.meta-flow {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 16px;
}

.meta-flow__item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: var(--el-fill-color);
  border-radius: 6px;
  font-size: 13px;
  transition: all 0.2s ease;
}

.meta-flow__item:hover {
  background: var(--el-fill-color-dark);
}

.dark .meta-flow__item:hover {
  background: var(--el-fill-color);
}

.meta-flow__label {
  color: var(--text-secondary);
  font-weight: 500;
}

.meta-flow__value {
  color: var(--text-primary);
  font-weight: 500;
}

/* i18n 列表 */
.i18n-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.i18n-item {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 10px 14px;
  background: var(--bg-card);
  border-radius: 8px;
  border: 1px solid var(--border-lighter);
  transition: all 0.2s ease;
}

.i18n-item:hover {
  background: var(--el-color-primary-light-9);
}
.dark .i18n-item:hover {
  background: var(--el-fill-color);
}

.i18n-item__locale {
  flex-shrink: 0;
  min-width: 80px;
}

.i18n-item__text {
  flex: 1;
  font-size: 13px;
  color: var(--text-primary);
  line-height: 1.6;
}

/* 底部审计信息 */
.expand-footer {
  display: flex;
  align-items: center;
  gap: 32px;
  padding-top: 20px;
  border-top: 1px solid var(--border-light);
}

.audit-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.audit-icon {
  color: var(--text-secondary);
  font-size: 14px;
}

.audit-label {
  color: var(--text-secondary);
  font-weight: 500;
}

.audit-time {
  color: var(--text-primary);
  font-family: 'SF Mono', 'Courier New', monospace;
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
</style>
