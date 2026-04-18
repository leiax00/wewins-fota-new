<script setup lang="ts">
import { reactive, ref, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { warmCdn, deleteFirmwareVersion, cancelUploadSession } from '@/api/firmware'

// 导入 composables
import { useFirmwareList } from './composables/useFirmwareList'
import { useFirmwareUpload } from './composables/useFirmwareUpload'
import { useFirmwareForm } from './composables/useFirmwareForm'
import { useTaskProgress } from './composables/useTaskProgress'

// 导入子组件
import FirmwareSearchForm from './components/FirmwareSearchForm.vue'
import FirmwareTable from './components/FirmwareTable.vue'
import FirmwareExpandRow from './components/FirmwareExpandRow.vue'
import FirmwareFormDialog from './components/FirmwareFormDialog.vue'
import FirmwareDeviceDrawer from './components/FirmwareDeviceDrawer.vue'
import TaskProgressDialog from './components/TaskProgressDialog.vue'

// 导入类型
import type { UploadState, FirmwareFormData } from './types'
import type { FirmwareVersionItem } from '@/api/firmware'

const { t } = useI18n()

// 使用 composables
const {
  list,
  total,
  loading,
  productSearchOptions,
  productSearchLoading,
  query,
  canShowActions,
  expandRowKeys,
  handleProductSearch,
  fetchList,
  resetSearch,
  toggleExpand,
} = useFirmwareList()

// 上传状态
const uploadState = reactive<UploadState>({
  status: 'IDLE',
  percent: 0,
  uploadSessionId: '',
  fileName: '',
  fileSize: 0,
  error: '',
  abortController: null,
})

// 设备列表抽屉
const deviceDrawerVisible = ref(false)
const deviceDrawerVersionId = ref(0)
const deviceDrawerVersion = ref('')

const openDeviceDrawer = (row: FirmwareVersionItem) => {
  deviceDrawerVersionId.value = row.id
  deviceDrawerVersion.value = row.version
  deviceDrawerVisible.value = true
}

// 表单数据
const form = reactive<FirmwareFormData>({
  productId: undefined,
  version: '',
  internalVersion: '',
  noPackage: false,
  uploadSessionId: '',
  tags: {},
  meta: {},
})

// 使用上传 composable
const {
  resetUploadState,
  cancelUpload,
  retryUpload,
  customUpload,
  beforeUpload,
} = useFirmwareUpload({
  getProductId: () => form.productId,
  uploadState,
  form,
  onUploadSuccess: (sessionId) => {
    form.uploadSessionId = sessionId
  },
})

// 使用表单 composable
const {
  tagsSchema,
  metaSchema,
  dialogVisible,
  dialogMode,
  submitting,
  editingId,
  rules,
  openCreateDialog,
  openEditDialog,
  submitForm,
  handleDialogClosed,
} = useFirmwareForm({
  form,
  uploadState,
  onSubmitSuccess: (taskId) => {
    openTaskProgressDialog(taskId)
  },
  onDirectSubmitSuccess: () => {
    void fetchList()
  },
  resetUploadState,
})

// 使用任务进度 composable
const {
  taskProgress,
  openTaskProgressDialog,
  closeTaskProgressDialog,
  finishTaskProgress,
} = useTaskProgress({
  onTaskComplete: () => {
    void fetchList()
  },
})

/**
 * CDN 预热
 */
const handleWarmCdn = async (row: FirmwareVersionItem) => {
  try {
    await ElMessageBox.confirm(
      t('firmware.warm.confirmMessage'),
      t('firmware.warm.confirmTitle'),
      { type: 'info', confirmButtonText: t('firmware.warm.confirmButton'), cancelButtonText: t('common.cancel') }
    )

    const result = await warmCdn(row.id)
    if (result.triggered) {
      const message = result.messageKey ? t(result.messageKey) : t('firmware.warm.requested')
      ElMessage.success(message + (result.pop ? ` ${t('firmware.actualPopSuffix', { pop: result.pop })}` : ''))
    } else {
      const message = result.messageKey ? t(result.messageKey) : t('firmware.warm.failed')
      ElMessage.warning(message)
    }
  } catch (e) {
    if (e !== 'cancel') {
      const message = e instanceof Error ? e.message : t('firmware.warm.error')
      ElMessage.error(message)
    }
  }
}

/**
 * 删除固件版本
 */
const handleDelete = async (row: FirmwareVersionItem) => {
  try {
    await ElMessageBox.confirm(t('common.deleteConfirm'), t('common.tip'), { type: 'warning' })
    await deleteFirmwareVersion(row.id)
    ElMessage.success(t('common.deleteSuccess'))
    await fetchList()
  } catch (e) {
    if (e !== 'cancel') {
      const message = e instanceof Error ? e.message : t('common.deleteFailed')
      ElMessage.error(message)
    }
  }
}

/**
 * 对话框关闭时清理上传会话
 */
const onDialogClosed = async () => {
  // 清理上传会话（无论提交成功还是失败）
  if (uploadState.uploadSessionId) {
    try {
      await cancelUploadSession(uploadState.uploadSessionId)
    } catch {
      // 忽略清理失败
    }
  }
  handleDialogClosed()
}

/**
 * 处理上传文件
 */
const handleUploadFile = (file: File) => {
  customUpload({
    action: '',
    method: 'post',
    data: {},
    filename: 'file',
    file: file as never,
    headers: {},
    withCredentials: false,
    onProgress: () => {},
    onSuccess: () => {},
    onError: () => {},
  })
}

/**
 * 处理产品搜索
 */
const handleProductSearchForForm = (keyword: string) => {
  handleProductSearch(keyword)
}

/**
 * 打开新增对话框
 */
const handleCreate = () => {
  openCreateDialog()
}

/**
 * 打开编辑对话框
 */
const handleEdit = (row: FirmwareVersionItem) => {
  openEditDialog(row)
}

/**
 * 提交表单
 */
const handleSubmit = () => {
  submitForm()
}

// 生命周期
onMounted(() => {
  void fetchList()
})

onUnmounted(() => {
  // 组件卸载时清理资源
})
</script>

<template>
  <PageCardTableShell :title="t('firmware.title')">
    <template #actions>
      <FirmwareSearchForm
        :query="query"
        :product-search-options="productSearchOptions"
        :product-search-loading="productSearchLoading"
        @search="fetchList"
        @reset="resetSearch"
        @create="handleCreate"
        @product-search="handleProductSearchForForm"
      />
    </template>

    <FirmwareTable
      :list="list"
      :loading="loading"
      :expand-row-keys="expandRowKeys"
      :can-show-actions="canShowActions"
      :tags-schema="tagsSchema"
      :meta-schema="metaSchema"
      @edit="handleEdit"
      @delete="handleDelete"
      @toggle-expand="toggleExpand"
      @show-devices="openDeviceDrawer"
    >
      <template #expand="{ row }">
        <FirmwareExpandRow
          :row="row"
          :tags-schema="tagsSchema"
          :meta-schema="metaSchema"
          @warm-cdn="handleWarmCdn"
        />
      </template>
    </FirmwareTable>

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

  <FirmwareFormDialog
    v-model="dialogVisible"
    :mode="dialogMode"
    :editing-id="editingId"
    :form="form"
    :form-rules="rules"
    :upload-state="uploadState"
    :product-search-options="productSearchOptions"
    :product-search-loading="productSearchLoading"
    :tags-schema="tagsSchema"
    :meta-schema="metaSchema"
    :submitting="submitting"
    :http-request="customUpload"
    :before-upload="beforeUpload"
    @submit="handleSubmit"
    @cancel-upload="cancelUpload"
    @retry-upload="retryUpload"
    @product-search="handleProductSearchForForm"
    @upload-file="handleUploadFile"
    @closed="onDialogClosed"
  />

  <TaskProgressDialog
    v-model="taskProgress.visible"
    :task-progress="taskProgress"
    @finish="finishTaskProgress"
    @close="closeTaskProgressDialog"
  />

  <FirmwareDeviceDrawer
    v-model:visible="deviceDrawerVisible"
    :version-id="deviceDrawerVersionId"
    :version="deviceDrawerVersion"
  />
</template>
