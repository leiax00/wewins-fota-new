<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import {
  createPolicy,
  getPolicyById,
  updatePolicy,
  type PolicyStatus,
  type TargetMode,
  type TimeWindowType,
  type TriggerMode,
  type UpgradePolicyItem,
} from '@/api/policy'
import { pageBatches, type DeviceImportBatchItem } from '@/api/deviceImportBatch'
import { searchProducts, type ProductItem } from '@/api/product'
import { getFirmwareVersionsByProduct, type FirmwareVersionItem } from '@/api/firmware'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

type TriggerModeOption = { value: TriggerMode; label: string; desc: string }
type TargetModeOption = { value: TargetMode; label: string; desc: string }
type TimeWindowOption = { value: TimeWindowType; label: string; desc: string }

const formRef = ref()
const pageLoading = ref(false)
const submitting = ref(false)
const productSearchLoading = ref(false)
const firmwareLoading = ref(false)
const batchLoading = ref(false)
const skipLeaveGuard = ref(false)

const productSearchOptions = ref<ProductItem[]>([])
const firmwareOptions = ref<FirmwareVersionItem[]>([])
const batchOptions = ref<DeviceImportBatchItem[]>([])
let productSearchTimer: number | null = null

const triggerModeOptions: TriggerModeOption[] = [
  { value: 'BOTH', label: 'policy.triggerBoth', desc: 'policy.triggerBothDesc' },
  { value: 'AUTO', label: 'policy.triggerAuto', desc: 'policy.triggerAutoDesc' },
  { value: 'MANUAL', label: 'policy.triggerManual', desc: 'policy.triggerManualDesc' },
]

const targetModeOptions: TargetModeOption[] = [
  { value: 'ALL', label: 'policy.targetAll', desc: 'policy.targetAllDesc' },
  { value: 'DEVICE_IDS', label: 'policy.targetDeviceIds', desc: 'policy.targetDeviceIdsDesc' },
  { value: 'DEVICE_BATCHES', label: 'policy.targetBatches', desc: 'policy.targetBatchesDesc' },
  { value: 'DEVICE_TAGS', label: 'policy.targetTags', desc: 'policy.targetTagsDesc' },
]

const timeWindowOptions: TimeWindowOption[] = [
  { value: 'UNLIMITED', label: 'policy.timeWindowUnlimited', desc: 'policy.timeWindowUnlimitedDesc' },
  { value: 'RANGE', label: 'policy.timeWindowRange', desc: 'policy.timeWindowRangeDesc' },
  { value: 'DAILY', label: 'policy.timeWindowDaily', desc: 'policy.timeWindowDailyDesc' },
]

const createDefaultForm = () => ({
  productId: undefined as number | undefined,
  firmwareVersionId: undefined as number | undefined,
  name: '',
  grayRate: 100,
  priority: 50,
  triggerMode: 'BOTH' as TriggerMode,
  timeWindow: {
    type: 'UNLIMITED' as TimeWindowType,
    startAt: null as string | null,
    endAt: null as string | null,
  },
  sourceVersions: [] as number[],
  targetMode: 'ALL' as TargetMode,
  targetDeviceIds: [] as string[],
  targetDeviceIdsInput: '',
  targetDeviceBatchIds: [] as string[],
  targetDeviceTags: {} as Record<string, unknown>,
  // KV 形式的标签
  tagKeys: [] as string[],
  tagValues: [] as string[],
  // 标签编辑状态
  tagEditing: [] as boolean[],
  status: 'DRAFT' as PolicyStatus,
  remark: '',
})

const form = reactive(createDefaultForm())
const initialSnapshot = ref('')

const isEditMode = computed(() => route.name === 'policy-edit' || !!route.params.id)
const editingId = computed(() => {
  if (!isEditMode.value) return null
  const id = Number(route.params.id)
  return Number.isFinite(id) && id > 0 ? id : null
})

const pageTitle = computed(() => (isEditMode.value ? t('policy.editTitle') : t('policy.createTitle')))

// 检查用户是否可以编辑生产中的策略
const canEditProductionPolicy = computed(() =>
  userStore.hasPermission('fota:policy:release')
)

// 检查是否可以选择某个目标状态
const canSelectStatus = (targetStatus: PolicyStatus): boolean => {
  // 创建模式：ACTIVE/PAUSED 需要 release 权限
  if (!isEditMode.value) {
    if (targetStatus === 'ACTIVE' || targetStatus === 'PAUSED') {
      return userStore.hasPermission('fota:policy:release')
    }
    return true
  }

  // 编辑模式：涉及 ACTIVE/PAUSED 的切换需要 release 权限
  const hasRelease = userStore.hasPermission('fota:policy:release')
  const currentStatus = form.status

  if (currentStatus === 'ACTIVE' || currentStatus === 'PAUSED' ||
      targetStatus === 'ACTIVE' || targetStatus === 'PAUSED') {
    return hasRelease && userStore.hasPermission('fota:policy:update')
  }

  return userStore.hasPermission('fota:policy:update')
}

// 检查当前策略是否可以被当前用户编辑
const canEditCurrentPolicy = computed(() => {
  if (!isEditMode.value) return true

  // ACTIVE/PAUSED 策略需要发布权限
  return !((form.status === 'ACTIVE' || form.status === 'PAUSED') && !canEditProductionPolicy.value);
})

// 产品下的所有版本选项（用于源版本选择）
const sourceVersionOptions = ref<FirmwareVersionItem[]>([])

// 标签 KV 对计算属性
const tagPairs = computed({
  get: () => {
    const pairs: Array<{ key: string; value: string; editing: boolean }> = []
    const len = Math.max(form.tagKeys.length, form.tagValues.length, form.tagEditing.length)
    for (let i = 0; i < len; i++) {
      pairs.push({
        key: form.tagKeys[i] || '',
        value: form.tagValues[i] || '',
        editing: form.tagEditing[i] || false,
      })
    }
    return pairs
  },
  set: (pairs: Array<{ key: string; value: string; editing?: boolean }>) => {
    form.tagKeys = pairs.map((p) => p.key).filter((k) => k.trim())
    form.tagValues = pairs.map((p) => p.value)
    form.tagEditing = pairs.map((p) => p.editing || false)
    // 同步更新 targetDeviceTags
    form.targetDeviceTags = {}
    pairs.forEach((p) => {
      if (p.key.trim()) {
        form.targetDeviceTags[p.key.trim()] = p.value
      }
    })
  },
})

// 添加新标签对
const addTagPair = () => {
  form.tagKeys.push('')
  form.tagValues.push('')
  form.tagEditing.push(true)
}

// 移除标签对
const removeTagPair = (index: number) => {
  form.tagKeys.splice(index, 1)
  form.tagValues.splice(index, 1)
  form.tagEditing.splice(index, 1)
  // 同步更新 targetDeviceTags
  form.targetDeviceTags = {}
  tagPairs.value.forEach((p, i) => {
    if (p.key.trim() && i !== index) {
      form.targetDeviceTags[p.key.trim()] = p.value
    }
  })
}

// 编辑标签对
const editTagPair = (index: number) => {
  form.tagEditing[index] = true
}

// 保存标签对
const saveTagPair = (index: number) => {
  const key = form.tagKeys[index]?.trim()
  if (key) {
    form.targetDeviceTags[key] = form.tagValues[index] || ''
  }
  form.tagEditing[index] = false
}

const targetDeviceIdsComputed = computed({
  get: () => form.targetDeviceIdsInput,
  set: (val: string) => {
    form.targetDeviceIdsInput = val
    form.targetDeviceIds = val
      .split(/[,\n]+/)
      .map((s) => s.trim())
      .filter((s) => s.length > 0)
  },
})

const formRules = {
  productId: [{ required: true, message: t('policy.productIdRequired'), trigger: 'change' }],
  firmwareVersionId: [{ required: true, message: t('policy.firmwareVersionRequired'), trigger: 'change' }],
  name: [{ required: true, message: t('policy.nameRequired'), trigger: 'blur' }],
  grayRate: [
    { required: true, type: 'number' as const, min: 0, max: 100, message: t('policy.grayRateRange'), trigger: 'blur' },
  ],
  priority: [
    { required: true, type: 'number' as const, min: 0, message: t('policy.priorityMin'), trigger: 'blur' },
  ],
  sourceVersions: [
    { required: true, message: t('policy.sourceVersionsRequired'), trigger: 'change' },
    {
      validator: (_rule: unknown, _value: unknown, callback: any) => {
        if (!form.sourceVersions || form.sourceVersions.length === 0) {
          callback(new Error(t('policy.sourceVersionsRequired')))
        } else {
          callback()
        }
      },
      trigger: 'change',
    },
  ],
  timeWindow: [
    {
      validator: (_rule: unknown, _value: unknown, callback: any) => {
        // UNLIMITED 时不验证
        if (form.timeWindow.type === 'UNLIMITED') {
          callback()
          return
        }
        const { startAt, endAt, type } = form.timeWindow
        if (!startAt || !endAt) {
          callback(new Error(t('policy.timeWindowRequired')))
          return
        }
        const start = new Date(startAt)
        const end = new Date(endAt)

        if (start >= end) {
          callback(new Error(t('policy.endTimeMustBeAfterStart')))
          return
        }

        if (type === 'DAILY') {
          const hours = (end.getTime() - start.getTime()) / (1000 * 60 * 60)
          if (hours > 24) {
            callback(new Error(t('policy.dailyWindowMax24Hours')))
            return
          }
        }

        callback()
      },
      trigger: ['change', 'blur'],
    },
  ],
  targetMode: [{ required: true, message: t('policy.targetModeRequired'), trigger: 'change' }],
  targetDeviceIds: [
    {
      validator: (_rule: unknown, _value: unknown, callback: any) => {
        if (form.targetMode === 'DEVICE_IDS' && (!form.targetDeviceIds || form.targetDeviceIds.length === 0)) {
          callback(new Error(t('policy.targetDeviceIdsRequired')))
        } else {
          callback()
        }
      },
      trigger: 'change',
    },
  ],
  targetDeviceBatchIds: [
    {
      validator: (_rule: unknown, _value: unknown, callback: any) => {
        if (form.targetMode === 'DEVICE_BATCHES' && (!form.targetDeviceBatchIds || form.targetDeviceBatchIds.length === 0)) {
          callback(new Error(t('policy.targetDeviceBatchIdsRequired')))
        } else {
          callback()
        }
      },
      trigger: 'change',
    },
  ],
  targetDeviceTags: [
    {
      validator: (_rule: unknown, _value: unknown, callback: any) => {
        if (form.targetMode === 'DEVICE_TAGS') {
          if (!form.targetDeviceTags || Object.keys(form.targetDeviceTags).length === 0) {
            callback(new Error(t('policy.targetDeviceTagsRequired')))
            return
          }
          if (typeof form.targetDeviceTags !== 'object' || Array.isArray(form.targetDeviceTags)) {
            callback(new Error(t('device.tagsMustBeObject')))
            return
          }
        }
        callback()
      },
      trigger: 'change',
    },
  ],
  status: [{ required: true, message: t('policy.statusRequired'), trigger: 'change' }],
}

const toUtcIso = (date: Date): string => date.toISOString()

const createSnapshot = () =>
  JSON.stringify({
    productId: form.productId,
    firmwareVersionId: form.firmwareVersionId,
    name: form.name,
    grayRate: form.grayRate,
    priority: form.priority,
    triggerMode: form.triggerMode,
    timeWindow: form.timeWindow,
    sourceVersions: form.sourceVersions,
    targetMode: form.targetMode,
    targetDeviceIds: form.targetDeviceIds,
    targetDeviceBatchIds: form.targetDeviceBatchIds,
    targetDeviceTags: form.targetDeviceTags,
    status: form.status,
    remark: form.remark,
  })

const isDirty = computed(() => createSnapshot() !== initialSnapshot.value)

const fetchProducts = async (keyword = '') => {
  productSearchLoading.value = true
  try {
    const result = await searchProducts(keyword.trim())
    productSearchOptions.value = result.records || []
  } finally {
    productSearchLoading.value = false
  }
}

const handleProductSearch = async (keyword: string) => {
  if (productSearchTimer !== null) {
    clearTimeout(productSearchTimer)
  }
  productSearchTimer = window.setTimeout(() => {
    void fetchProducts(keyword)
  }, 300)
}

// 获取产品下的所有版本（用于源版本选择）
const fetchSourceVersionsByProduct = async (productId?: number) => {
  if (!productId) {
    sourceVersionOptions.value = []
    form.sourceVersions = []
    return
  }
  try {
    const versions = await getFirmwareVersionsByProduct(productId, false) // 获取所有版本，不只是 READY 状态
    sourceVersionOptions.value = versions
  } catch {
    sourceVersionOptions.value = []
  }
}

const fetchFirmwareByProduct = async (productId?: number) => {
  if (!productId) {
    firmwareOptions.value = []
    form.firmwareVersionId = undefined
    return
  }
  firmwareLoading.value = true
  try {
    const versions = await getFirmwareVersionsByProduct(productId, true)
    firmwareOptions.value = versions
  } finally {
    firmwareLoading.value = false
  }
}

const fetchBatches = async (keyword?: string) => {
  batchLoading.value = true
  try {
    const result = await pageBatches({
      batchName: keyword || '',
      page: 1,
      size: 50,
    })
    batchOptions.value = result.records || []
  } finally {
    batchLoading.value = false
  }
}

const resetFormToDefault = () => {
  Object.assign(form, createDefaultForm())
  firmwareOptions.value = []
  batchOptions.value = []
  sourceVersionOptions.value = []
}

const patchFormFromPolicy = async (policy: UpgradePolicyItem) => {
  form.productId = policy.productId
  form.firmwareVersionId = policy.firmwareVersionId
  form.name = policy.name
  form.grayRate = policy.grayRate
  form.priority = policy.priority
  form.triggerMode = policy.triggerMode || 'BOTH'
  // 如果没有时间窗口，设为 UNLIMITED
  form.timeWindow = policy.timeWindow || { type: 'UNLIMITED' as TimeWindowType, startAt: null, endAt: null }
  form.sourceVersions = policy.sourceVersions || []
  form.targetMode = policy.targetMode || 'ALL'
  form.targetDeviceIds = policy.targetDeviceIds || []
  form.targetDeviceIdsInput = (policy.targetDeviceIds || []).join('\n')
  form.targetDeviceBatchIds = policy.targetDeviceBatchIds || []
  form.targetDeviceTags = policy.targetDeviceTags || {}
  // 将 targetDeviceTags 转换为 KV 数组
  const tags = policy.targetDeviceTags || {}
  form.tagKeys = Object.keys(tags)
  form.tagValues = Object.values(tags).map((v) => String(v))
  form.tagEditing = Object.keys(tags).map(() => false) // 默认不处于编辑状态
  form.status = policy.status
  form.remark = policy.remark || ''

  // 检查权限：ACTIVE/PAUSED 策略需要发布权限
  if ((policy.status === 'ACTIVE' || policy.status === 'PAUSED') && !canEditProductionPolicy.value) {
    ElMessage.warning(t('policy.noPermissionToEditCurrentStatus'))
    await goList(true)
    return
  }

  await fetchFirmwareByProduct(policy.productId)
  await fetchSourceVersionsByProduct(policy.productId)
  // 如果目标模式是批次筛选，无论是否有已选批次都需要加载批次列表
  if (policy.targetMode === 'DEVICE_BATCHES') {
    await fetchBatches()
  }
}

const goList = async (skipGuard = false) => {
  if (skipGuard) skipLeaveGuard.value = true
  await router.push('/policy')
}

const confirmLeaveIfDirty = async () => {
  if (!isDirty.value || submitting.value) return true
  try {
    await ElMessageBox.confirm(t('policy.leaveConfirm'), t('common.tip'), {
      type: 'warning',
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
    })
    return true
  } catch {
    return false
  }
}

const goBackWithConfirm = async () => {
  if (!(await confirmLeaveIfDirty())) return
  skipLeaveGuard.value = true
  await router.push('/policy')
}

const handleBack = () => goBackWithConfirm()
const handleCancel = () => goBackWithConfirm()

const handleTargetModeChange = (newMode: TargetMode | string) => {
  form.targetMode = newMode as TargetMode
  form.targetDeviceIds = []
  form.targetDeviceIdsInput = ''
  form.targetDeviceBatchIds = []
  form.targetDeviceTags = {}
  form.tagKeys = []
  form.tagValues = []
  form.tagEditing = []

  // 切换到批次模式时，加载批次列表
  if (form.targetMode === 'DEVICE_BATCHES') {
    void fetchBatches()
  }
}

const onFormProductChange = async (productId?: number) => {
  form.firmwareVersionId = undefined
  form.sourceVersions = []
  await fetchFirmwareByProduct(productId)
  await fetchSourceVersionsByProduct(productId)
}

const loadPageData = async () => {
  pageLoading.value = true
  try {
    await fetchProducts('')
    resetFormToDefault()

    if (isEditMode.value) {
      if (!editingId.value) {
        ElMessage.error(t('error.unknown'))
        await goList(true)
        return
      }
      const data = await getPolicyById(editingId.value)
      await patchFormFromPolicy(data)
    }

    initialSnapshot.value = createSnapshot()
    formRef.value?.clearValidate()
  } catch {
    if (isEditMode.value) {
      await goList(true)
    }
  } finally {
    pageLoading.value = false
  }
}

const submitForm = async () => {
  await formRef.value?.validate()

  if (form.targetMode === 'ALL') {
    try {
      await ElMessageBox.confirm(t('policy.allModeWarning'), t('common.tip'), {
        type: 'warning',
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
      })
    } catch {
      return
    }
  }

  submitting.value = true
  try {
    // timeWindow 始终传值，UNLIMITED 时传类型和 null 时间
    const timeWindow = form.timeWindow.type === 'UNLIMITED'
      ? { type: 'UNLIMITED' as TimeWindowType, startAt: null, endAt: null }
      : {
          type: form.timeWindow.type,
          startAt: toUtcIso(new Date(form.timeWindow.startAt!)),
          endAt: toUtcIso(new Date(form.timeWindow.endAt!)),
        }

    const payload = {
      productId: form.productId!,
      firmwareVersionId: form.firmwareVersionId!,
      name: form.name,
      grayRate: form.grayRate,
      priority: form.priority,
      triggerMode: form.triggerMode,
      timeWindow,
      sourceVersions: form.sourceVersions,
      targetMode: form.targetMode,
      targetDeviceIds: form.targetMode === 'DEVICE_IDS' ? form.targetDeviceIds : undefined,
      targetDeviceBatchIds: form.targetMode === 'DEVICE_BATCHES' ? form.targetDeviceBatchIds : undefined,
      targetDeviceTags: form.targetMode === 'DEVICE_TAGS' ? form.targetDeviceTags : undefined,
      status: form.status,
      remark: form.remark || undefined,
    }

    if (isEditMode.value && editingId.value) {
      await updatePolicy(editingId.value, payload)
      ElMessage.success(t('common.updateSuccess'))
    } else {
      await createPolicy(payload)
      ElMessage.success(t('common.createSuccess'))
    }

    initialSnapshot.value = createSnapshot()
    skipLeaveGuard.value = true
    await router.push('/policy')
  } finally {
    submitting.value = false
  }
}

watch(
  () => route.params.id,
  () => {
    void loadPageData()
  }
)

onBeforeRouteLeave(async () => {
  if (skipLeaveGuard.value) return true
  return confirmLeaveIfDirty()
})

onMounted(() => {
  void loadPageData()
})
</script>

<template>
  <PageCardTableShell :title="pageTitle">
    <template #actions>
      <div class="flex items-center gap-2">
        <el-button @click="handleBack">
          {{ t('policy.backToList') }}
        </el-button>
      </div>
    </template>

    <el-skeleton :loading="pageLoading" animated>
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="140px"
      >
        <!-- 基础信息区 -->
        <el-divider content-position="left">{{ t('policy.basicInfo') }}</el-divider>

        <!-- 策略名称独占一行 -->
        <el-form-item prop="name" :label="t('policy.name')">
          <el-input v-model="form.name" :placeholder="t('policy.nameRequired')" />
        </el-form-item>

        <!-- 产品和目标版本一行 -->
        <div class="flex gap-4">
          <el-form-item prop="productId" :label="t('policy.product')" class="flex-1">
            <el-select
              v-model="form.productId"
              :loading="productSearchLoading"
              filterable
              remote
              reserve-keyword
              :remote-method="handleProductSearch"
              style="width: 100%"
              @change="onFormProductChange"
            >
              <el-option
                v-for="product in productSearchOptions"
                :key="product.id"
                :label="product.name"
                :value="product.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item prop="firmwareVersionId" :label="t('policy.firmwareVersion')" class="flex-1">
            <el-select
              v-model="form.firmwareVersionId"
              :loading="firmwareLoading"
              filterable
              style="width: 100%"
            >
              <el-option
                v-for="firmware in firmwareOptions"
                :key="firmware.id"
                :label="firmware.version"
                :value="firmware.id"
              />
            </el-select>
          </el-form-item>
        </div>

        <!-- 灰度和优先级一行 -->
        <div class="flex gap-4">
          <el-form-item prop="grayRate" :label="t('policy.grayRate')" class="flex-1">
            <el-input-number v-model="form.grayRate" :min="0" :max="100" style="width: 100%" />
          </el-form-item>
          <el-form-item prop="priority" :label="t('policy.priority')" class="flex-1">
            <el-input-number v-model="form.priority" :min="0" style="width: 100%" />
          </el-form-item>
        </div>

        <!-- 触发配置区 -->
        <el-divider content-position="left">{{ t('policy.triggerConfig') }}</el-divider>
        <el-form-item>
          <template #label>
            <div class="flex items-center gap-1">
              {{ t('policy.triggerMode') }}
              <el-popover placement="top" :width="280" trigger="click">
                <template #reference>
                  <el-icon class="cursor-pointer hover:text-primary">
                    <InfoFilled class="text-gray-500"/>
                  </el-icon>
                </template>
                <div class="space-y-2 text-sm">
                  <div v-for="option in triggerModeOptions" :key="option.value">
                    <div class="font-medium text-gray-700">{{ t(option.label) }}</div>
                    <div class="text-gray-500">{{ t(option.desc) }}</div>
                  </div>
                </div>
              </el-popover>
            </div>
          </template>
          <el-radio-group v-model="form.triggerMode" class="w-full compact-radio-group">
            <el-radio-button
              v-for="option in triggerModeOptions"
              :key="option.value"
              :value="option.value"
            >
              {{ t(option.label) }}
            </el-radio-button>
          </el-radio-group>
          <div class="mt-2 flex items-center gap-1.5 text-xs text-gray-400">
            <span class="inline-block w-1 h-3 bg-primary rounded-full"></span>
            <span>{{ t(triggerModeOptions.find(o => o.value === form.triggerMode)?.desc || '') }}</span>
          </div>
        </el-form-item>

        <!-- 时间窗口 -->
        <el-form-item>
          <template #label>
            <div class="flex items-center gap-1">
              {{ t('policy.timeWindow') }}
              <el-popover placement="top" :width="280" trigger="click">
                <template #reference>
                  <el-icon class="cursor-pointer hover:text-primary">
                    <InfoFilled class="text-gray-500"/>
                  </el-icon>
                </template>
                <div class="space-y-2 text-sm">
                  <div v-for="option in timeWindowOptions" :key="option.value">
                    <div class="font-medium text-gray-700">{{ t(option.label) }}</div>
                    <div class="text-gray-500">{{ t(option.desc) }}</div>
                  </div>
                </div>
              </el-popover>
            </div>
          </template>
          <div class="w-full space-y-2">
            <el-radio-group v-model="form.timeWindow.type" class="w-full compact-radio-group">
              <el-radio-button
                v-for="option in timeWindowOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ t(option.label) }}
              </el-radio-button>
            </el-radio-group>
            <div class="flex items-center gap-1.5 text-xs text-gray-400">
              <span class="inline-block w-1 h-3 bg-primary rounded-full"></span>
              <span>
                <template v-if="form.timeWindow.type === 'UNLIMITED'">
                  {{ t('policy.timeWindowUnlimitedDesc') }}
                </template>
                <template v-else-if="form.timeWindow.type === 'RANGE'">
                  {{ t('policy.timeWindowRangeDesc') }}
                </template>
                <template v-else-if="form.timeWindow.type === 'DAILY'">
                  {{ t('policy.timeWindowDailyDesc') }}
                </template>
              </span>
            </div>
            <template v-if="form.timeWindow.type !== 'UNLIMITED'">
              <div class="flex gap-2">
                <el-date-picker
                  v-model="form.timeWindow.startAt"
                  type="datetime"
                  :placeholder="t('policy.startTime')"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                  style="flex: 1"
                />
                <span class="flex items-center">~</span>
                <el-date-picker
                  v-model="form.timeWindow.endAt"
                  type="datetime"
                  :placeholder="t('policy.endTime')"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                  style="flex: 1"
                />
              </div>
            </template>
          </div>
        </el-form-item>

        <!-- 生效范围区 -->
        <el-divider content-position="left">{{ t('policy.effectScope') }}</el-divider>

        <!-- 测试设备范围提示 -->
        <el-alert
          v-if="form.status === 'TESTING' || form.status === 'VERIFIED'"
          :title="t('policy.testingScopeTip')"
          type="info"
          :closable="false"
          show-icon
          class="mb-4 scope-alert"
        />

        <!-- 源版本限制 - 改为 select 多选 -->
        <el-form-item prop="sourceVersions" :label="t('policy.sourceVersions')">
          <el-select
            v-model="form.sourceVersions"
            multiple
            filterable
            :disabled="!form.productId || sourceVersionOptions.length === 0"
            style="width: 100%"
            :placeholder="!form.productId ? t('policy.productIdRequired') : sourceVersionOptions.length === 0 ? t('policy.firmwareVersionIdRequired') : t('policy.sourceVersionsPlaceholder')"
          >
            <el-option
              v-for="version in sourceVersionOptions"
              :key="version.id"
              :label="`${version.version}`"
              :value="version.id"
            />
          </el-select>
          <div v-if="form.productId && sourceVersionOptions.length > 0" class="text-xs text-gray-400 mt-1">
            {{ t('policy.sourceVersionsTip') }}
          </div>
        </el-form-item>

        <!-- 目标模式 - 紧凑按钮组 + 下方说明 -->
        <el-form-item prop="targetMode">
          <template #label>
            <div class="flex items-center gap-1">
              {{ t('policy.targetMode') }}
              <el-popover placement="top" :width="280" trigger="click">
                <template #reference>
                  <el-icon class="cursor-pointer text-gray-400 hover:text-primary">
                    <InfoFilled class="text-gray-500" />
                  </el-icon>
                </template>
                <div class="space-y-2 text-sm">
                  <div v-for="option in targetModeOptions" :key="option.value">
                    <div class="font-medium text-gray-700">{{ t(option.label) }}</div>
                    <div class="text-gray-500">{{ t(option.desc) }}</div>
                  </div>
                </div>
              </el-popover>
            </div>
          </template>
          <el-radio-group v-model="form.targetMode" class="w-full compact-radio-group" @change="(val: any) => handleTargetModeChange(val)">
            <el-radio-button
              v-for="option in targetModeOptions"
              :key="option.value"
              :value="option.value"
            >
              {{ t(option.label) }}
            </el-radio-button>
          </el-radio-group>
          <div class="mt-2 flex items-center gap-1.5 text-xs text-gray-400">
            <span class="inline-block w-1 h-3 bg-primary rounded-full"></span>
            <span>{{ t(targetModeOptions.find(o => o.value === form.targetMode)?.desc || '') }}</span>
          </div>
        </el-form-item>

        <!-- 动态目标字段 -->
        <template v-if="form.targetMode === 'DEVICE_IDS'">
          <el-form-item prop="targetDeviceIds" :label="t('policy.targetDeviceIdsInput')">
            <el-input
              v-model="targetDeviceIdsComputed"
              type="textarea"
              :rows="2"
              :placeholder="t('policy.deviceIdsPlaceholder')"
            />
          </el-form-item>
        </template>

        <template v-if="form.targetMode === 'DEVICE_BATCHES'">
          <el-form-item prop="targetDeviceBatchIds" :label="t('policy.targetDeviceBatchIds')">
            <el-select v-model="form.targetDeviceBatchIds" :loading="batchLoading" multiple filterable style="width: 100%">
              <el-option
                v-for="batch in batchOptions"
                :key="batch.id"
                :label="`${batch.batchName} (${batch.status})`"
                :value="String(batch.id)"
              />
            </el-select>
          </el-form-item>
        </template>

        <!-- 标签筛选 - Tag 显示 + 编辑模式 -->
        <template v-if="form.targetMode === 'DEVICE_TAGS'">
          <el-form-item prop="targetDeviceTags" :label="t('policy.targetDeviceTags')">
            <div class="w-full">
              <!-- 已保存的标签显示 -->
              <div v-if="tagPairs.filter(p => !p.editing && p.key).length > 0" class="flex flex-wrap gap-2 mb-2">
                <div
                  v-for="(pair, index) in tagPairs.filter(p => !p.editing && p.key)"
                  :key="index"
                  class="inline-flex items-center gap-1 bg-blue-50 text-blue-700 px-2 py-1 rounded text-sm"
                >
                  <span class="font-medium">{{ pair.key }}:</span>
                  <span>{{ pair.value }}</span>
                  <el-button
                    link
                    size="small"
                    @click="editTagPair(tagPairs.indexOf(pair))"
                  >
                    {{ t('common.edit') }}
                  </el-button>
                  <el-button
                    link
                    size="small"
                    type="danger"
                    @click="removeTagPair(tagPairs.indexOf(pair))"
                  >
                    ×
                  </el-button>
                </div>
              </div>

              <!-- 编辑中的标签输入 -->
              <div v-if="tagPairs.some(p => p.editing)" class="space-y-2 border rounded-lg p-3 bg-gray-50">
                <div v-for="(pair, index) in tagPairs.filter(p => p.editing)" :key="index" class="flex gap-2 items-center">
                  <el-input
                    v-model="form.tagKeys[tagPairs.indexOf(pair)]"
                    :placeholder="t('policy.tagKeyPlaceholder')"
                    style="flex: 1"
                  />
                  <span class="text-gray-400">:</span>
                  <el-input
                    v-model="form.tagValues[tagPairs.indexOf(pair)]"
                    :placeholder="t('policy.tagValuePlaceholder')"
                    style="flex: 1"
                  />
                  <el-button
                    link
                    type="primary"
                    size="small"
                    @click="saveTagPair(tagPairs.indexOf(pair))"
                  >
                    ✓
                  </el-button>
                  <el-button
                    link
                    type="danger"
                    size="small"
                    @click="removeTagPair(tagPairs.indexOf(pair))"
                  >
                    ×
                  </el-button>
                </div>
              </div>

              <!-- 添加新标签按钮 -->
              <el-button
                v-if="!tagPairs.some(p => p.editing)"
                link
                type="primary"
                @click="addTagPair"
              >
                + {{ t('policy.addTagPair') }}
              </el-button>
            </div>
          </el-form-item>
        </template>

        <template v-if="form.targetMode === 'ALL'">
          <el-alert :title="t('policy.allModeWarning')" type="warning" :closable="false" show-icon />
        </template>

        <!-- 状态和备注 -->
        <el-divider content-position="left">{{ t('policy.other') }}</el-divider>
        <el-form-item prop="status" :label="t('policy.status')">
          <el-select
            v-model="form.status"
            style="width: 100%"
            :disabled="isEditMode && !canEditCurrentPolicy"
          >
            <el-option
              value="DRAFT"
              :label="t('policy.statusDraft')"
              :disabled="!canSelectStatus('DRAFT')"
            >
              <div class="flex items-center justify-between w-full pr-4">
                <span>{{ t('policy.statusDraft') }}</span>
                <span class="form-status-badge form-status-draft">{{ t('policy.statusDraftLabel') }}</span>
              </div>
            </el-option>
            <el-option
              value="TESTING"
              :label="t('policy.statusTesting')"
              :disabled="!canSelectStatus('TESTING')"
            >
              <div class="flex items-center justify-between w-full pr-4">
                <span>{{ t('policy.statusTesting') }}</span>
                <span class="form-status-badge form-status-testing">{{ t('policy.statusTestingLabel') }}</span>
              </div>
            </el-option>
            <el-option
              value="VERIFIED"
              :label="t('policy.statusVerified')"
              :disabled="!canSelectStatus('VERIFIED')"
            >
              <div class="flex items-center justify-between w-full pr-4">
                <span>{{ t('policy.statusVerified') }}</span>
                <span class="form-status-badge form-status-verified">{{ t('policy.statusVerifiedLabel') }}</span>
              </div>
            </el-option>
            <el-option
              value="ACTIVE"
              :label="t('policy.statusActive')"
              :disabled="!canSelectStatus('ACTIVE')"
            >
              <div class="flex items-center justify-between w-full pr-4">
                <span>{{ t('policy.statusActive') }}</span>
                <span class="form-status-badge form-status-active">{{ t('policy.statusActiveLabel') }}</span>
              </div>
            </el-option>
            <el-option
              value="PAUSED"
              :label="t('status.paused')"
              :disabled="!canSelectStatus('PAUSED')"
            >
              <div class="flex items-center justify-between w-full pr-4">
                <span>{{ t('status.paused') }}</span>
                <span class="form-status-badge form-status-paused">{{ t('policy.statusPausedLabel') }}</span>
              </div>
            </el-option>
            <el-option
              value="EXPIRED"
              :label="t('status.expired')"
              :disabled="!canSelectStatus('EXPIRED')"
            >
              <div class="flex items-center justify-between w-full pr-4">
                <span>{{ t('status.expired') }}</span>
                <span class="form-status-badge form-status-expired">{{ t('policy.statusExpiredLabel') }}</span>
              </div>
            </el-option>
          </el-select>
          <div class="text-xs text-gray-400 mt-1">
            <template v-if="isEditMode">
              <template v-if="!canEditCurrentPolicy">
                {{ t('policy.statusEditPermissionDenied') }}
              </template>
              <template v-else>
                {{ t('policy.statusTransitionRequiresRelease') }}
              </template>
            </template>
            <template v-else>
              {{ t('policy.initialStateHint') }}
            </template>
          </div>
        </el-form-item>
        <el-form-item :label="t('policy.remark')">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>

        <div class="flex justify-end items-center mt-6">
          <!-- 保存和取消 -->
          <div class="flex gap-2">
            <el-button :disabled="submitting" @click="handleCancel">
              {{ t('common.cancel') }}
            </el-button>
            <el-button type="primary" :loading="submitting" @click="submitForm">
              {{ t('common.save') }}
            </el-button>
          </div>
        </div>
      </el-form>
    </el-skeleton>
  </PageCardTableShell>
</template>

<style scoped>
/* 确保按钮组占满整行且无间隙 */
.compact-radio-group :deep(.el-radio-button) {
  flex: 1;
}

.compact-radio-group :deep(.el-radio-button__inner) {
  width: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
}

/* 移除按钮之间的间隙 - 紧贴效果 */
.compact-radio-group :deep(.el-radio-button:first-child .el-radio-button__inner) {
  border-top-right-radius: 0;
  border-bottom-right-radius: 0;
}

.compact-radio-group :deep(.el-radio-button:last-child .el-radio-button__inner) {
  border-top-left-radius: 0;
  border-bottom-left-radius: 0;
}

.compact-radio-group :deep(.el-radio-button:not(:first-child):not(:last-child) .el-radio-button__inner) {
  border-radius: 0;
}

.compact-radio-group :deep(.el-radio-button:not(:first-child) .el-radio-button__inner) {
  margin-left: -1px;
}

/* 设备范围提示 - 缩小字体 */
.scope-alert {
  margin-bottom: 16px !important;
}

.scope-alert :deep(.el-alert__title) {
  font-size: 13px;
  line-height: 1.5;
}

.scope-alert :deep(.el-alert__description) {
  font-size: 12px;
}

.scope-alert :deep(.el-alert__content) {
  font-size: 13px;
}

/* 表单页状态标签样式（与列表页统一） */
.form-status-badge {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
  line-height: 1.4;
}

.form-status-draft {
  background-color: #f9fafb;
  color: #6b7280;
  border: 1px solid #e5e7eb;
}

.form-status-testing {
  background-color: #fffbeb;
  color: #d97706;
  border: 1px solid #fde68a;
}

.form-status-verified {
  background-color: #eef2ff;
  color: #4f46e5;
  border: 1px solid #c7d2fe;
}

.form-status-active {
  background-color: #ecfdf5;
  color: #059669;
  border: 1px solid #a7f3d0;
}

.form-status-paused {
  background-color: #fef3c7;
  color: #b45309;
  border: 1px solid #fcd34d;
}

.form-status-expired {
  background-color: #f3f4f6;
  color: #9ca3af;
  border: 1px solid #d1d5db;
}
</style>
