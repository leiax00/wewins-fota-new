<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { resolveStatusLabelKey, resolveStatusType, roleStatusTypeMap } from '@/constants/status'
import { useUserStore } from '@/stores/user'
import {
  createDictItem,
  createDictType,
  deleteDictItem,
  deleteDictType,
  pageDictItems,
  pageDictTypes,
  updateDictItem,
  updateDictType,
  type DictItem,
  type DictTypeItem,
} from '@/api/system'

const { t } = useI18n()
const userStore = useUserStore()

const activeTab = ref<'type' | 'item'>('type')
const statusOptions = [
  { value: 'active', label: t('status.active') },
  { value: 'disabled', label: t('status.disabled') },
]

const dictTypes = ref<DictTypeItem[]>([])
const dictTypeTotal = ref(0)
const dictTypeLoading = ref(false)
const dictTypeQuery = reactive({
  page: 1,
  size: 20,
  name: '',
  status: '',
})

const dictItems = ref<DictItem[]>([])
const dictItemTotal = ref(0)
const dictItemLoading = ref(false)
const dictItemQuery = reactive({
  page: 1,
  size: 20,
  dictTypeId: undefined as number | undefined,
  label: '',
  status: '',
})

const canTypeActions = computed(() =>
  userStore.hasPermission('sys:dict_type:update') || userStore.hasPermission('sys:dict_type:delete')
)
const canItemActions = computed(() =>
  userStore.hasPermission('sys:dict_item:update') || userStore.hasPermission('sys:dict_item:delete')
)

const typeDialogVisible = ref(false)
const typeDialogMode = ref<'create' | 'edit'>('create')
const typeSubmitting = ref(false)
const typeEditingId = ref<number | null>(null)
const typeFormRef = ref()
const typeForm = reactive({
  code: '',
  name: '',
  i18nKey: '',
  status: 'active',
  description: '',
})

const typeRules = {
  code: [{ required: true, message: t('system.dict.codeRequired'), trigger: 'blur' }],
  name: [{ required: true, message: t('system.dict.nameRequired'), trigger: 'blur' }],
  status: [{ required: true, message: t('system.common.statusRequired'), trigger: 'change' }],
}

const itemDialogVisible = ref(false)
const itemDialogMode = ref<'create' | 'edit'>('create')
const itemSubmitting = ref(false)
const itemEditingId = ref<number | null>(null)
const itemFormRef = ref()
const itemForm = reactive({
  dictTypeId: undefined as number | undefined,
  label: '',
  value: '',
  i18nKey: '',
  sortOrder: 0,
  status: 'active',
})

const itemRules = {
  dictTypeId: [{ required: true, message: t('system.dict.typeRequired'), trigger: 'change' }],
  label: [{ required: true, message: t('system.dict.itemLabelRequired'), trigger: 'blur' }],
  value: [{ required: true, message: t('system.dict.itemValueRequired'), trigger: 'blur' }],
  status: [{ required: true, message: t('system.common.statusRequired'), trigger: 'change' }],
}

const fetchDictTypes = async () => {
  dictTypeLoading.value = true
  try {
    const result = await pageDictTypes(dictTypeQuery)
    dictTypes.value = result.records || []
    dictTypeTotal.value = result.total || 0
  } finally {
    dictTypeLoading.value = false
  }
}

const fetchDictItems = async () => {
  dictItemLoading.value = true
  try {
    const result = await pageDictItems(dictItemQuery)
    dictItems.value = result.records || []
    dictItemTotal.value = result.total || 0
  } finally {
    dictItemLoading.value = false
  }
}

const refreshCurrentTab = () => {
  if (activeTab.value === 'type') {
    void fetchDictTypes()
    return
  }
  void fetchDictItems()
}

const openCreateTypeDialog = () => {
  typeDialogMode.value = 'create'
  typeEditingId.value = null
  typeForm.code = ''
  typeForm.name = ''
  typeForm.i18nKey = ''
  typeForm.status = 'active'
  typeForm.description = ''
  typeDialogVisible.value = true
}

const openEditTypeDialog = (row: DictTypeItem) => {
  typeDialogMode.value = 'edit'
  typeEditingId.value = row.id
  typeForm.code = row.code
  typeForm.name = row.name
  typeForm.i18nKey = row.i18nKey
  typeForm.status = row.status || 'active'
  typeForm.description = row.description
  typeDialogVisible.value = true
}

const submitType = async () => {
  await typeFormRef.value?.validate()
  typeSubmitting.value = true
  try {
    const payload = {
      code: typeForm.code,
      name: typeForm.name,
      i18nKey: typeForm.i18nKey,
      status: typeForm.status,
      description: typeForm.description,
    }
    if (typeDialogMode.value === 'create') {
      await createDictType(payload)
      ElMessage.success(t('system.common.createSuccess'))
    } else if (typeEditingId.value) {
      await updateDictType(typeEditingId.value, payload)
      ElMessage.success(t('system.common.updateSuccess'))
    }
    typeDialogVisible.value = false
    await fetchDictTypes()
  } finally {
    typeSubmitting.value = false
  }
}

const removeType = async (row: DictTypeItem) => {
  await ElMessageBox.confirm(t('system.common.deleteConfirm'), t('common.tip'), { type: 'warning' })
  await deleteDictType(row.id)
  ElMessage.success(t('system.common.deleteSuccess'))
  await fetchDictTypes()
}

const openCreateItemDialog = () => {
  itemDialogMode.value = 'create'
  itemEditingId.value = null
  itemForm.dictTypeId = dictItemQuery.dictTypeId
  itemForm.label = ''
  itemForm.value = ''
  itemForm.i18nKey = ''
  itemForm.sortOrder = 0
  itemForm.status = 'active'
  itemDialogVisible.value = true
}

const openEditItemDialog = (row: DictItem) => {
  itemDialogMode.value = 'edit'
  itemEditingId.value = row.id
  itemForm.dictTypeId = row.dictTypeId
  itemForm.label = row.label
  itemForm.value = row.value
  itemForm.i18nKey = row.i18nKey
  itemForm.sortOrder = row.sortOrder || 0
  itemForm.status = row.status || 'active'
  itemDialogVisible.value = true
}

const submitItem = async () => {
  await itemFormRef.value?.validate()
  itemSubmitting.value = true
  try {
    const payload = {
      dictTypeId: Number(itemForm.dictTypeId),
      label: itemForm.label,
      value: itemForm.value,
      i18nKey: itemForm.i18nKey,
      sortOrder: itemForm.sortOrder,
      status: itemForm.status,
    }
    if (itemDialogMode.value === 'create') {
      await createDictItem(payload)
      ElMessage.success(t('system.common.createSuccess'))
    } else if (itemEditingId.value) {
      await updateDictItem(itemEditingId.value, payload)
      ElMessage.success(t('system.common.updateSuccess'))
    }
    itemDialogVisible.value = false
    await fetchDictItems()
  } finally {
    itemSubmitting.value = false
  }
}

const removeItem = async (row: DictItem) => {
  await ElMessageBox.confirm(t('system.common.deleteConfirm'), t('common.tip'), { type: 'warning' })
  await deleteDictItem(row.id)
  ElMessage.success(t('system.common.deleteSuccess'))
  await fetchDictItems()
}

onMounted(async () => {
  await Promise.all([fetchDictTypes(), fetchDictItems()])
})
</script>

<template>
  <PageCardTableShell :title="t('system.dict.title')">
    <el-tabs v-model="activeTab">
      <el-tab-pane :label="t('system.dict.typeTab')" name="type">
        <div class="mb-3 flex items-center gap-2">
          <el-input
            v-model="dictTypeQuery.name"
            :placeholder="t('system.dict.name')"
            clearable
            style="width: 180px"
            @keyup.enter="fetchDictTypes"
          />
          <el-select
            v-model="dictTypeQuery.status"
            :placeholder="t('common.status')"
            clearable
            style="width: 140px"
          >
            <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
          <el-button @click="fetchDictTypes">{{ t('common.search') }}</el-button>
          <el-button @click="refreshCurrentTab">{{ t('common.refresh') }}</el-button>
          <el-button
            v-if="userStore.hasPermission('sys:dict_type:create')"
            type="primary"
            @click="openCreateTypeDialog"
          >
            {{ t('system.dict.addType') }}
          </el-button>
        </div>

        <el-table v-loading="dictTypeLoading" :data="dictTypes" stripe>
          <el-table-column prop="code" :label="t('system.dict.code')" min-width="150" />
          <el-table-column prop="name" :label="t('system.dict.name')" min-width="160" />
          <el-table-column prop="i18nKey" :label="t('system.dict.i18nKey')" min-width="180" />
          <el-table-column prop="description" :label="t('system.dict.description')" min-width="220" />
          <el-table-column prop="status" :label="t('common.status')" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="resolveStatusType(roleStatusTypeMap, row.status)">
                {{ t(resolveStatusLabelKey(row.status)) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            v-if="canTypeActions"
            :label="t('common.actions')"
            width="130"
            fixed="right"
          >
            <template #default="{ row }">
              <el-button
                v-if="userStore.hasPermission('sys:dict_type:update')"
                link
                class="ui-action-primary"
                @click="openEditTypeDialog(row)"
              >
                {{ t('common.edit') }}
              </el-button>
              <el-button
                v-if="userStore.hasPermission('sys:dict_type:delete')"
                link
                class="ui-action-danger"
                @click="removeType(row)"
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
            :total="dictTypeTotal"
            v-model:current-page="dictTypeQuery.page"
            v-model:page-size="dictTypeQuery.size"
            @current-change="fetchDictTypes"
            @size-change="fetchDictTypes"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane :label="t('system.dict.itemTab')" name="item">
        <div class="mb-3 flex items-center gap-2">
          <el-select
            v-model="dictItemQuery.dictTypeId"
            clearable
            :placeholder="t('system.dict.typeFilter')"
            style="width: 220px"
          >
            <el-option v-for="item in dictTypes" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
          <el-input
            v-model="dictItemQuery.label"
            :placeholder="t('system.dict.itemLabel')"
            clearable
            style="width: 180px"
            @keyup.enter="fetchDictItems"
          />
          <el-select
            v-model="dictItemQuery.status"
            :placeholder="t('common.status')"
            clearable
            style="width: 140px"
          >
            <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
          <el-button @click="fetchDictItems">{{ t('common.search') }}</el-button>
          <el-button @click="refreshCurrentTab">{{ t('common.refresh') }}</el-button>
          <el-button
            v-if="userStore.hasPermission('sys:dict_item:create')"
            type="primary"
            @click="openCreateItemDialog"
          >
            {{ t('system.dict.addItem') }}
          </el-button>
        </div>

        <el-table v-loading="dictItemLoading" :data="dictItems" stripe>
          <el-table-column prop="dictTypeId" :label="t('system.dict.typeId')" width="90" />
          <el-table-column prop="label" :label="t('system.dict.itemLabel')" min-width="160" />
          <el-table-column prop="value" :label="t('system.dict.itemValue')" min-width="160" />
          <el-table-column prop="sortOrder" :label="t('system.dict.sortOrder')" width="90" />
          <el-table-column prop="status" :label="t('common.status')" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="resolveStatusType(roleStatusTypeMap, row.status)">
                {{ t(resolveStatusLabelKey(row.status)) }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column
            v-if="canItemActions"
            :label="t('common.actions')"
            width="130"
            fixed="right"
          >
            <template #default="{ row }">
              <el-button
                v-if="userStore.hasPermission('sys:dict_item:update')"
                link
                class="ui-action-primary"
                @click="openEditItemDialog(row)"
              >
                {{ t('common.edit') }}
              </el-button>
              <el-button
                v-if="userStore.hasPermission('sys:dict_item:delete')"
                link
                class="ui-action-danger"
                @click="removeItem(row)"
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
            :total="dictItemTotal"
            v-model:current-page="dictItemQuery.page"
            v-model:page-size="dictItemQuery.size"
            @current-change="fetchDictItems"
            @size-change="fetchDictItems"
          />
        </div>
      </el-tab-pane>
    </el-tabs>
  </PageCardTableShell>

  <el-dialog v-model="typeDialogVisible" :title="typeDialogMode === 'create' ? t('system.dict.addType') : t('common.edit')" width="560px">
    <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="110px">
      <el-form-item prop="code" :label="t('system.dict.code')">
        <el-input v-model="typeForm.code" :disabled="typeDialogMode === 'edit'" />
      </el-form-item>
      <el-form-item prop="name" :label="t('system.dict.name')">
        <el-input v-model="typeForm.name" />
      </el-form-item>
      <el-form-item :label="t('system.dict.i18nKey')">
        <el-input v-model="typeForm.i18nKey" />
      </el-form-item>
      <el-form-item :label="t('system.dict.description')">
        <el-input v-model="typeForm.description" type="textarea" :rows="3" />
      </el-form-item>
      <el-form-item prop="status" :label="t('common.status')">
        <el-select v-model="typeForm.status" style="width: 100%">
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="typeDialogVisible = false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="typeSubmitting" @click="submitType">{{ t('common.save') }}</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="itemDialogVisible" :title="itemDialogMode === 'create' ? t('system.dict.addItem') : t('common.edit')" width="560px">
    <el-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-width="110px">
      <el-form-item prop="dictTypeId" :label="t('system.dict.type')">
        <el-select v-model="itemForm.dictTypeId" style="width: 100%">
          <el-option v-for="item in dictTypes" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item prop="label" :label="t('system.dict.itemLabel')">
        <el-input v-model="itemForm.label" />
      </el-form-item>
      <el-form-item prop="value" :label="t('system.dict.itemValue')">
        <el-input v-model="itemForm.value" />
      </el-form-item>
      <el-form-item :label="t('system.dict.i18nKey')">
        <el-input v-model="itemForm.i18nKey" />
      </el-form-item>
      <el-form-item :label="t('system.dict.sortOrder')">
        <el-input-number v-model="itemForm.sortOrder" :min="0" style="width: 100%" />
      </el-form-item>
      <el-form-item prop="status" :label="t('common.status')">
        <el-select v-model="itemForm.status" style="width: 100%">
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="itemDialogVisible = false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="itemSubmitting" @click="submitItem">{{ t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>
