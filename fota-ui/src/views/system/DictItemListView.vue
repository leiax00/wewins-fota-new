<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { resolveStatusLabelKey, resolveStatusType, roleStatusTypeMap } from '@/constants/status'
import { useUserStore } from '@/stores/user'
import {
  createDictItem,
  deleteDictItem,
  getDictTypeById,
  pageDictItems,
  updateDictItem,
  type DictItem,
  type DictTypeItem,
} from '@/api/system'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const dictTypeId = computed(() => Number(route.params.id))
const dictType = ref<DictTypeItem | null>(null)
const contextLoading = ref(false)

const statusOptions = [
  { value: 'active', label: t('status.active') },
  { value: 'disabled', label: t('status.disabled') },
]

const loading = ref(false)
const list = ref<DictItem[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 20,
  label: '',
  status: '',
})

const canShowActions = computed(() =>
  userStore.hasPermission('sys:dict_item:update') || userStore.hasPermission('sys:dict_item:delete')
)

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref()

const form = reactive({
  label: '',
  value: '',
  i18nKey: '',
  sortOrder: 0,
  status: 'active',
  extraText: '',
})

const formRules = {
  label: [{ required: true, message: t('system.dict.itemLabelRequired'), trigger: 'blur' }],
  value: [{ required: true, message: t('system.dict.itemValueRequired'), trigger: 'blur' }],
  status: [{ required: true, message: t('system.common.statusRequired'), trigger: 'change' }],
}

const fetchTypeInfo = async () => {
  const id = dictTypeId.value
  if (!id || Number.isNaN(id)) {
    router.replace('/system/dict')
    return
  }

  contextLoading.value = true
  try {
    dictType.value = await getDictTypeById(id)
  } catch {
    router.replace('/system/dict')
  } finally {
    contextLoading.value = false
  }
}

const fetchList = async () => {
  const id = dictTypeId.value
  if (!id || Number.isNaN(id)) return

  loading.value = true
  try {
    const result = await pageDictItems({
      page: query.page,
      size: query.size,
      dictTypeId: id,
      label: query.label,
      status: query.status,
    })

    const records = result.records || []
    list.value = records
    total.value = result.total || 0
  } finally {
    loading.value = false
  }
}

const openCreateDialog = () => {
  dialogMode.value = 'create'
  editingId.value = null
  form.label = ''
  form.value = ''
  form.i18nKey = ''
  form.sortOrder = 0
  form.status = 'active'
  form.extraText = ''
  dialogVisible.value = true
}

const formatExtraForEdit = (extra: unknown) => {
  if (extra === null || extra === undefined) return ''
  try {
    return JSON.stringify(extra, null, 2)
  } catch {
    return String(extra)
  }
}

const formatExtraForTable = (extra: unknown) => {
  if (extra === null || extra === undefined) return '-'
  try {
    return JSON.stringify(extra)
  } catch {
    return String(extra)
  }
}

const formatExtraSummary = (extra: unknown) => {
  if (extra === null || extra === undefined) return t('system.dict.extraEmpty')
  if (Array.isArray(extra)) {
    return `Array(${extra.length})`
  }
  if (typeof extra === 'object') {
    return `Object(${Object.keys(extra as Record<string, unknown>).length})`
  }
  return formatExtraForTable(extra)
}

const formatExtraPretty = (extra: unknown) => {
  if (extra === null || extra === undefined) return ''
  try {
    return JSON.stringify(extra, null, 2)
  } catch {
    return String(extra)
  }
}

const hasExtra = (extra: unknown) => extra !== null && extra !== undefined

const parseExtraFromForm = (): { valid: boolean; value: unknown | null } => {
  const raw = form.extraText.trim()
  if (!raw) {
    return { valid: true, value: null }
  }

  try {
    return { valid: true, value: JSON.parse(raw) }
  } catch {
    ElMessage.error(t('system.dict.extraJsonInvalid'))
    return { valid: false, value: null }
  }
}

const formatExtraInput = () => {
  const parsedExtra = parseExtraFromForm()
  if (!parsedExtra.valid) {
    return
  }
  if (parsedExtra.value === null) {
    form.extraText = ''
    return
  }
  form.extraText = formatExtraForEdit(parsedExtra.value)
}

const openEditDialog = (row: DictItem) => {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.label = row.label
  form.value = row.value
  form.i18nKey = row.i18nKey
  form.sortOrder = row.sortOrder || 0
  form.status = row.status || 'active'
  form.extraText = formatExtraForEdit(row.extra)
  dialogVisible.value = true
}

const submitForm = async () => {
  await formRef.value?.validate()
  const parsedExtra = parseExtraFromForm()
  if (!parsedExtra.valid) {
    return
  }

  submitting.value = true
  try {
    const payload = {
      dictTypeId: dictTypeId.value,
      label: form.label,
      value: form.value,
      i18nKey: form.i18nKey,
      sortOrder: form.sortOrder,
      status: form.status,
      extra: parsedExtra.value,
    }

    if (dialogMode.value === 'create') {
      await createDictItem(payload)
      ElMessage.success(t('system.common.createSuccess'))
    } else if (editingId.value) {
      await updateDictItem(editingId.value, payload)
      ElMessage.success(t('system.common.updateSuccess'))
    }

    dialogVisible.value = false
    await fetchList()
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (row: DictItem) => {
  await ElMessageBox.confirm(t('system.common.deleteConfirm'), t('common.tip'), { type: 'warning' })
  await deleteDictItem(row.id)
  ElMessage.success(t('system.common.deleteSuccess'))
  await fetchList()
}

onMounted(async () => {
  await fetchTypeInfo()
  await fetchList()
})
</script>

<template>
  <PageCardTableShell :title="t('system.dict.itemTab')">
    <template #actions>
      <div class="flex items-center gap-2">
        <el-button @click="router.push('/system/dict')">
          {{ t('system.dict.backToTypes') }}
        </el-button>
        <el-input
          v-model="query.label"
          :placeholder="t('system.dict.itemLabel')"
          clearable
          style="width: 180px"
          @keyup.enter="fetchList"
        />
        <el-select
          v-model="query.status"
          :placeholder="t('common.status')"
          clearable
          style="width: 140px"
        >
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-button @click="fetchList">{{ t('common.search') }}</el-button>
        <el-button @click="fetchList">{{ t('common.refresh') }}</el-button>
        <el-button
          v-if="userStore.hasPermission('sys:dict_item:create')"
          type="primary"
          @click="openCreateDialog"
        >
          {{ t('system.dict.addItem') }}
        </el-button>
      </div>
    </template>

    <el-alert
      v-if="dictType"
      class="mb-4"
      type="info"
      :closable="false"
    >
      <template #title>
        {{ t('system.dict.currentType') }}: {{ dictType.name }} ({{ dictType.code }})
      </template>
    </el-alert>

    <el-skeleton v-if="contextLoading" :rows="1" animated />

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="label" :label="t('system.dict.itemLabel')" min-width="160" />
      <el-table-column prop="value" :label="t('system.dict.itemValue')" min-width="160" />
      <el-table-column :label="t('system.dict.extra')" min-width="220">
        <template #default="{ row }">
          <div class="extra-cell">
            <span class="extra-summary">{{ formatExtraSummary(row.extra) }}</span>
            <el-popover
              v-if="hasExtra(row.extra)"
              placement="left"
              trigger="hover"
              width="420"
            >
              <template #reference>
                <el-button link type="primary">{{ t('system.dict.viewJson') }}</el-button>
              </template>
              <pre class="extra-json-preview">{{ formatExtraPretty(row.extra) }}</pre>
            </el-popover>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="sortOrder" :label="t('system.dict.sortOrder')" width="100" />
      <el-table-column prop="status" :label="t('common.status')" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="resolveStatusType(roleStatusTypeMap, row.status)">
            {{ t(resolveStatusLabelKey(row.status)) }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column
        v-if="canShowActions"
        :label="t('common.actions')"
        width="140"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            v-if="userStore.hasPermission('sys:dict_item:update')"
            link
            class="ui-action-primary"
            @click="openEditDialog(row)"
          >
            {{ t('common.edit') }}
          </el-button>
          <el-button
            v-if="userStore.hasPermission('sys:dict_item:delete')"
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
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        @current-change="fetchList"
        @size-change="fetchList"
      />
    </div>
  </PageCardTableShell>

  <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? t('system.dict.addItem') : t('common.edit')" width="560px">
    <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px">
      <el-form-item :label="t('system.dict.type')">
        <el-input :model-value="dictType ? `${dictType.name} (${dictType.code})` : '-'" disabled />
      </el-form-item>
      <el-form-item prop="label" :label="t('system.dict.itemLabel')">
        <el-input v-model="form.label" />
      </el-form-item>
      <el-form-item prop="value" :label="t('system.dict.itemValue')">
        <el-input v-model="form.value" />
      </el-form-item>
      <el-form-item :label="t('system.dict.i18nKey')">
        <el-input v-model="form.i18nKey" />
      </el-form-item>
      <el-form-item :label="t('system.dict.sortOrder')">
        <el-input-number v-model="form.sortOrder" :min="0" style="width: 100%" />
      </el-form-item>
      <el-form-item :label="t('system.dict.extra')">
        <div style="width: 100%">
          <div class="mb-1 flex justify-end">
            <el-button link type="primary" @click="formatExtraInput">
              {{ t('system.dict.formatJson') }}
            </el-button>
          </div>
          <el-input
            v-model="form.extraText"
            type="textarea"
            :autosize="{ minRows: 4, maxRows: 10 }"
            :placeholder="t('system.dict.extraJsonPlaceholder')"
          />
        </div>
      </el-form-item>
      <el-form-item prop="status" :label="t('common.status')">
        <el-select v-model="form.status" style="width: 100%">
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="submitting" @click="submitForm">{{ t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.extra-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.extra-summary {
  color: var(--color-ui-text-regular);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
}

.extra-json-preview {
  margin: 0;
  max-height: 320px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.5;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
}
</style>
