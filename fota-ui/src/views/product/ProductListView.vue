<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/date'
import {
  createProduct,
  deleteProduct,
  pageProducts,
  updateProduct,
  type ProductItem,
} from '@/api/product'
import { trimFormValues } from '@/utils/form'

const { t } = useI18n()
const userStore = useUserStore()

const list = ref<ProductItem[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
})

const canShowActions = computed(() =>
  userStore.hasPermission('fota:product:update') ||
  userStore.hasPermission('fota:product:delete')
)

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref()

type PeriodUnit = 'minutes' | 'hours' | 'days'

const DEFAULT_CHECK_PERIOD_SECONDS = 6 * 60 * 60
const UNIT_SECONDS: Record<PeriodUnit, number> = {
  minutes: 60,
  hours: 60 * 60,
  days: 24 * 60 * 60,
}

const form = reactive({
  name: '',
  manufacturer: 'wewins',
  model: '',
  remark: '',
  checkPeriodValue: 6,
  checkPeriodUnit: 'hours' as PeriodUnit,
})

const formRules = {
  name: [{ required: true, message: t('product.nameRequired'), trigger: 'blur' }],
  manufacturer: [{ required: true, message: t('product.manufacturerRequired'), trigger: 'blur' }],
  model: [{ required: true, message: t('product.modelRequired'), trigger: 'blur' }],
}

const fetchList = async () => {
  loading.value = true
  try {
    const result = await pageProducts(trimFormValues(query))
    list.value = result.records || []
    total.value = result.total || 0
  } finally {
    loading.value = false
  }
}

const setCheckPeriodForm = (seconds?: number) => {
  const value = seconds || DEFAULT_CHECK_PERIOD_SECONDS
  if (value % UNIT_SECONDS.days === 0) {
    form.checkPeriodUnit = 'days'
    form.checkPeriodValue = value / UNIT_SECONDS.days
    return
  }
  if (value % UNIT_SECONDS.hours === 0) {
    form.checkPeriodUnit = 'hours'
    form.checkPeriodValue = value / UNIT_SECONDS.hours
    return
  }
  form.checkPeriodUnit = 'minutes'
  form.checkPeriodValue = Math.max(1, Math.round(value / UNIT_SECONDS.minutes))
}

const getCheckPeriodSeconds = () => {
  const unitSeconds = UNIT_SECONDS[form.checkPeriodUnit]
  return Math.max(60, Math.round(form.checkPeriodValue * unitSeconds))
}

const openCreateDialog = () => {
  dialogMode.value = 'create'
  editingId.value = null
  form.name = ''
  form.manufacturer = 'wewins'
  form.model = ''
  form.remark = ''
  setCheckPeriodForm(DEFAULT_CHECK_PERIOD_SECONDS)
  dialogVisible.value = true
}

const openEditDialog = (row: ProductItem) => {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.name = row.name
  form.manufacturer = row.manufacturer
  form.model = row.model
  form.remark = row.remark || ''
  setCheckPeriodForm(row.checkPeriodSeconds)
  dialogVisible.value = true
}

const submitForm = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    const payload = trimFormValues({
      name: form.name,
      manufacturer: form.manufacturer,
      model: form.model,
      remark: form.remark,
      checkPeriodSeconds: getCheckPeriodSeconds(),
    })

    if (dialogMode.value === 'create') {
      await createProduct(payload)
      ElMessage.success(t('common.createSuccess'))
    } else if (editingId.value) {
      await updateProduct(editingId.value, payload)
      ElMessage.success(t('common.updateSuccess'))
    }

    dialogVisible.value = false
    await fetchList()
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (row: ProductItem) => {
  await ElMessageBox.confirm(t('common.deleteConfirm'), t('common.tip'), { type: 'warning' })
  await deleteProduct(row.id)
  ElMessage.success(t('common.deleteSuccess'))
  await fetchList()
}

const resetSearch = () => {
  query.page = 1
  query.keyword = ''
  void fetchList()
}

const formatPeriod = (seconds?: number) => {
  const value = seconds ?? 0
  if (value <= 0) return '-'
  if (value % UNIT_SECONDS.days === 0) return `${value / UNIT_SECONDS.days}${t('product.periodUnitDaysShort')}`
  if (value % UNIT_SECONDS.hours === 0) return `${value / UNIT_SECONDS.hours}${t('product.periodUnitHoursShort')}`
  if (value % UNIT_SECONDS.minutes === 0) return `${value / UNIT_SECONDS.minutes}${t('product.periodUnitMinutesShort')}`
  return `${value}${t('product.periodUnitSecondsShort')}`
}

onMounted(() => {
  void fetchList()
})
</script>

<template>
  <PageCardTableShell :title="t('product.title')">
    <template #actions>
      <div class="flex items-center gap-2">
        <el-input
          v-model="query.keyword"
          :placeholder="t('product.name')"
          clearable
          style="width: 180px"
          @keyup.enter="fetchList"
        />
        <el-button @click="fetchList">
          {{ t('common.search') }}
        </el-button>
        <el-button @click="resetSearch">
          {{ t('common.refresh') }}
        </el-button>
        <el-button
          v-if="userStore.hasPermission('fota:product:create')"
          type="primary"
          @click="openCreateDialog"
        >
          {{ t('product.add') }}
        </el-button>
      </div>
    </template>

    <el-table
      v-loading="loading"
      :data="list"
      stripe
    >
      <el-table-column
        prop="name"
        :label="t('product.name')"
        min-width="180"
      />
      <el-table-column
        prop="manufacturer"
        :label="t('product.manufacturer')"
        min-width="160"
      />
      <el-table-column
        prop="model"
        :label="t('product.model')"
        min-width="140"
      />
      <el-table-column
        prop="remark"
        :label="t('common.remark')"
        min-width="200"
        show-overflow-tooltip
      />
      <el-table-column
        :label="t('product.checkPeriodSeconds')"
        width="160"
      >
        <template #default="{ row }">
          {{ formatPeriod(row.checkPeriodSeconds) }}
        </template>
      </el-table-column>
      <el-table-column
        :label="t('common.createTime')"
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
            v-if="userStore.hasPermission('fota:product:update')"
            link
            class="ui-action-primary"
            @click="openEditDialog(row)"
          >
            {{ t('common.edit') }}
          </el-button>
          <el-button
            v-if="userStore.hasPermission('fota:product:delete')"
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
    :title="dialogMode === 'create' ? t('product.add') : t('common.edit')"
    width="560px"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="formRules"
      label-width="110px"
    >
      <el-form-item
        prop="name"
        :label="t('product.name')"
      >
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item
        prop="manufacturer"
        :label="t('product.manufacturer')"
      >
        <el-input v-model="form.manufacturer" />
      </el-form-item>
      <el-form-item
        prop="model"
        :label="t('product.model')"
      >
        <el-input v-model="form.model" />
      </el-form-item>
      <el-form-item :label="t('common.remark')">
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="3"
        />
      </el-form-item>
      <el-form-item :label="t('product.checkPeriodSeconds')">
        <div class="flex w-full gap-3">
          <el-input-number
            v-model="form.checkPeriodValue"
            :min="1"
            :max="form.checkPeriodUnit === 'days' ? 7 : form.checkPeriodUnit === 'hours' ? 168 : 10080"
            :step="1"
            class="flex-1"
          />
          <el-select
            v-model="form.checkPeriodUnit"
            style="width: 140px"
          >
            <el-option
              :label="t('product.periodUnitMinutes')"
              value="minutes"
            />
            <el-option
              :label="t('product.periodUnitHours')"
              value="hours"
            />
            <el-option
              :label="t('product.periodUnitDays')"
              value="days"
            />
          </el-select>
        </div>
        <div class="mt-2 text-xs text-slate-500">
          {{ t('product.checkPeriodHint', { value: formatPeriod(getCheckPeriodSeconds()) }) }}
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        :loading="submitting"
        @click="submitForm"
      >
        {{ t('common.save') }}
      </el-button>
    </template>
  </el-dialog>
</template>
