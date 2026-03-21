import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { loadJsonFieldSchema } from '@/components/json-field/utils/schema-loader'
import type { JsonFieldDefinition } from '@/components/json-field/types/json-field'
import { trimFormValues } from '@/utils/form'
import {
  publishFirmwareVersion,
  updatePublishedFirmwareVersion,
  type FirmwareVersionItem,
} from '@/api/firmware'
import type { UploadState, FirmwareFormData } from '../types'

export interface UseFirmwareFormOptions {
  uploadState: UploadState
  onSubmitSuccess?: (taskId: number) => void
  resetUploadState: () => void
}

export function useFirmwareForm(options: UseFirmwareFormOptions) {
  const { t } = useI18n()

  const formRef = ref<FormInstance>()
  const tagsSchema = ref<JsonFieldDefinition[]>([])
  const metaSchema = ref<JsonFieldDefinition[]>([])

  const form = reactive<FirmwareFormData>({
    productId: undefined,
    version: '',
    internalVersion: '',
    noPackage: false,
    uploadSessionId: '',
    tags: {},
    meta: {},
  })

  const dialogVisible = ref(false)
  const dialogMode = ref<'create' | 'edit'>('create')
  const submitting = ref(false)
  const editingId = ref<number | null>(null)

  const rules = computed<FormRules>(() => ({
    productId: [{ required: true, message: t('firmware.productIdRequired'), trigger: 'change' }],
    version: [{ required: true, message: t('firmware.versionRequired'), trigger: 'blur' }],
    uploadSessionId: [
      {
        validator: (_rule: unknown, _value: unknown, callback: (error?: Error) => void) => {
          if (form.noPackage) {
            callback()
            return
          }
          if (dialogMode.value === 'create' && options.uploadState.status !== 'SUCCESS') {
            callback(new Error(t('firmware.uploadRequired')))
            return
          }
          callback()
        },
        trigger: 'change',
      },
    ],
  }))

  /**
   * 打开创建对话框
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
    options.resetUploadState()
    dialogVisible.value = true
  }

  /**
   * 打开编辑对话框
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
    options.resetUploadState()
    dialogVisible.value = true
  }

  /**
   * 提交表单
   */
  const submitForm = async () => {
    try {
      await formRef.value?.validate()
    } catch {
      return
    }

    submitting.value = true
    try {
      const payload: Record<string, unknown> = trimFormValues({
        productId: form.productId,
        version: form.version,
        internalVersion: form.internalVersion || undefined,
        tags: form.tags && Object.keys(form.tags).length > 0 ? JSON.stringify(form.tags) : undefined,
        meta: form.meta && Object.keys(form.meta).length > 0 ? JSON.stringify(form.meta) : undefined,
      })

      // 处理包状态
      if (form.noPackage) {
        payload.packageStatus = 'NONE'
      } else if (options.uploadState.status === 'SUCCESS' && options.uploadState.uploadSessionId) {
        payload.uploadSessionId = options.uploadState.uploadSessionId
      } else if (dialogMode.value === 'create') {
        ElMessage.warning(t('firmware.uploadOrNoPackageRequired'))
        submitting.value = false
        return
      }

      let response: { taskId: number; versionId: number } | undefined

      if (dialogMode.value === 'create') {
        response = await publishFirmwareVersion(payload as never)
      } else if (editingId.value) {
        response = await updatePublishedFirmwareVersion(editingId.value, payload as never)
      }

      options.resetUploadState()
      dialogVisible.value = false

      if (response?.taskId) {
        options.onSubmitSuccess?.(response.taskId)
      }
    } catch (e: unknown) {
      // 保存失败：保留上传状态，允许用户修改后重试
      const message = e instanceof Error ? e.message : t('firmware.saveFailedRetry')
      ElMessage.error(message)
    } finally {
      submitting.value = false
    }
  }

  /**
   * 关闭对话框
   */
  const closeDialog = () => {
    dialogVisible.value = false
  }

  /**
   * 对话框关闭后的处理
   */
  const handleDialogClosed = () => {
    formRef.value?.resetFields()
    options.resetUploadState()
  }

  // 监听"无包版本"开关变化
  watch(
    () => form.noPackage,
    (val) => {
      if (val) {
        // 勾选"无包版本"时重置上传状态
        options.resetUploadState()
      }
      formRef.value?.validateField('uploadSessionId')
    }
  )

  // 加载 JSON 字段 Schema
  onMounted(async () => {
    try {
      const [tagsDef, metaDef] = await Promise.all([
        loadJsonFieldSchema('json_schema.firmware_tags'),
        loadJsonFieldSchema('json_schema.firmware_meta'),
      ])
      tagsSchema.value = tagsDef
      metaSchema.value = metaDef
    } catch (e) {
      console.error('Failed to load JSON field schema:', e)
    }
  })

  return {
    t,
    formRef,
    form,
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
    closeDialog,
    handleDialogClosed,
  }
}
