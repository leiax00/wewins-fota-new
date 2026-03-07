<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadProps, UploadRequestOptions } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import JsonFieldEditor from '@/components/json-field/JsonFieldEditor.vue'
import { formatDateTime } from '@/utils/date'
import { trimFormValues } from '@/utils/form'
import {
  cancelUploadSession,
  createFirmwareVersion,
  deleteFirmwareVersion,
  getUploadSession,
  pageFirmwareVersions,
  uploadFirmwarePackage,
  updateFirmwareVersion,
  type FirmwareVersionItem,
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

const { t } = useI18n()
const userStore = useUserStore()
const uploadRef = ref()

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
 * 用于重试的文件缓存
 */
const retryFile = ref<File | null>(null)
/**
 * 上传请求票据，用于取消过期请求
 */
const uploadTicket = ref(0)

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
          callback(new Error('请先上传固件包'))
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
    ElMessage.warning('请先选择产品')
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
    const err = new Error('请先选择产品')
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

    const message = e instanceof Error ? e.message : '上传失败'
    uploadState.status = 'FAILED'
    uploadState.error = message
    options.onError?.(e as Error)
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
      ElMessage.warning('请先上传固件包，或勾选无包版本')
      submitting.value = false
      return
    }

    if (dialogMode.value === 'create') {
      await createFirmwareVersion(payload as never)
      ElMessage.success(t('common.createSuccess'))
    } else if (editingId.value) {
      await updateFirmwareVersion(editingId.value, payload as never)
      ElMessage.success(t('common.updateSuccess'))
    }

    // 保存成功后立刻清空上传状态，避免对话框关闭时重复清理会话
    resetUploadState()
    dialogVisible.value = false
    await fetchList()
  } catch (e: unknown) {
    // 保存失败：保留上传状态，不重置，允许用户修改后重试
    const message = e instanceof Error ? e.message : '保存失败，请重试'
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
onMounted(() => {
  void handleProductSearch('')
  void fetchList()
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
          placeholder="内部版本"
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
      stripe
    >
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
        show-overflow-tooltipx
      />
      <el-table-column
        prop="internalVersion"
        label="内部版本"
        min-width="200"
        show-overflow-tooltip
      />
      <el-table-column
        prop="packageStatus"
        label="包状态"
        min-width="100"
      >
        <template #default="{ row }">
          <el-tag
            v-if="row.packageStatus === 'READY'"
            type="success"
          >
            就绪
          </el-tag>
          <el-tag
            v-else-if="row.packageStatus === 'NONE'"
            type="info"
          >
            无包
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
        width="150"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            v-if="userStore.hasPermission('fota:firmware:update')"
            link
            class="ui-action-primary"
            @click="openEditDialog(row)"
          >
            {{ t('common.edit') }}
          </el-button>
          <el-button
            v-if="userStore.hasPermission('fota:firmware:delete')"
            link
            class="ui-action-danger"
            @click="handleDelete(row)"
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
        label="内部版本"
      >
        <el-input
          v-model="form.internalVersion"
          placeholder="如：v1.0.0-rc.1"
          :disabled="dialogMode === 'edit'"
        />
      </el-form-item>

      <el-form-item label="无包版本">
        <el-switch v-model="form.noPackage" />
        <span class="ml-2 text-sm text-gray-500">
          （占位版本号，用于版本规划，暂不可用于升级策略）
        </span>
      </el-form-item>

      <el-form-item
        v-if="!form.noPackage"
        prop="uploadSessionId"
        label="固件包上传"
      >
        <div class="w-full rounded border border-[var(--el-border-color)] p-4">
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
              type="primary"
              plain
              :loading="uploadState.status === 'UPLOADING'"
              :disabled="submitting || !form.productId"
            >
              <template v-if="uploadState.status === 'IDLE'">
                选择并上传固件包
              </template>
              <template v-else>
                上传中...
              </template>
            </el-button>
          </el-upload>

          <!-- 文件信息显示 -->
          <div
            v-if="uploadState.fileName"
            class="mt-3 text-sm"
          >
            <div class="grid grid-cols-2 gap-2">
              <div>
                <span class="text-gray-500">文件名：</span>
                <span class="ml-1 font-medium">{{ uploadState.fileName }}</span>
              </div>
              <div>
                <span class="text-gray-500">文件大小：</span>
                <span class="ml-1 font-medium">{{ formatFileSize(uploadState.fileSize) }}</span>
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
                {{ uploadState.status === 'SUCCESS' ? '✓ 上传成功' : `${percentage}%` }}
              </span>
            </template>
          </el-progress>

          <!-- 失败提示 -->
          <el-alert
            v-if="uploadState.status === 'FAILED'"
            class="mt-3"
            type="error"
            :closable="false"
            :title="uploadState.error || '上传失败'"
          />

          <!-- 操作按钮 -->
          <div class="mt-3 flex items-center gap-2">
            <el-button
              v-if="uploadState.status === 'UPLOADING'"
              size="small"
              @click="cancelUpload"
            >
              取消上传
            </el-button>
            <el-button
              v-if="uploadState.status === 'FAILED'"
              size="small"
              type="warning"
              @click="retryUpload"
            >
              重试上传
            </el-button>
            <el-button
              v-if="uploadState.status === 'SUCCESS' && !submitting"
              size="small"
              @click="cancelUpload"
            >
              替换文件
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
</template>
