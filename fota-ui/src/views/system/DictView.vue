<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { resolveStatusLabelKey, resolveStatusType, roleStatusTypeMap } from '@/constants/status'
import { useUserStore } from '@/stores/user'
import {
  createDictType,
  deleteDictType,
  pageDictTypes,
  updateDictType,
  type DictTypeItem,
} from '@/api/system'
import { trimFormValues } from '@/utils/form'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()

const statusOptions = [
  { value: 'active', label: t('status.active') },
  { value: 'disabled', label: t('status.disabled') },
]

const list = ref<DictTypeItem[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({
  page: 1,
  size: 20,
  name: '',
  status: '',
})

const canShowAnyAction = computed(() =>
  userStore.hasPermission('sys:dict_type:update') ||
  userStore.hasPermission('sys:dict_type:delete') ||
  userStore.hasPermission('sys:dict_item:read')
)

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref()

const form = reactive({
  code: '',
  name: '',
  i18nKey: '',
  status: 'active',
  description: '',
})

const formRules = {
  code: [{ required: true, message: t('system.dict.codeRequired'), trigger: 'blur' }],
  name: [{ required: true, message: t('system.dict.nameRequired'), trigger: 'blur' }],
  status: [{ required: true, message: t('system.common.statusRequired'), trigger: 'change' }],
}

const fetchList = async () => {
  loading.value = true
  try {
    const result = await pageDictTypes(trimFormValues(query))
    list.value = result.records || []
    total.value = result.total || 0
  } finally {
    loading.value = false
  }
}

const openCreateDialog = () => {
  dialogMode.value = 'create'
  editingId.value = null
  form.code = ''
  form.name = ''
  form.i18nKey = ''
  form.status = 'active'
  form.description = ''
  dialogVisible.value = true
}

const openEditDialog = (row: DictTypeItem) => {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.code = row.code
  form.name = row.name
  form.i18nKey = row.i18nKey
  form.status = row.status || 'active'
  form.description = row.description
  dialogVisible.value = true
}

const submitForm = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    const payload = trimFormValues({
      code: form.code,
      name: form.name,
      i18nKey: form.i18nKey,
      status: form.status,
      description: form.description,
    })

    if (dialogMode.value === 'create') {
      await createDictType(payload)
      ElMessage.success(t('system.common.createSuccess'))
    } else if (editingId.value) {
      await updateDictType(editingId.value, payload)
      ElMessage.success(t('system.common.updateSuccess'))
    }

    dialogVisible.value = false
    await fetchList()
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (row: DictTypeItem) => {
  await ElMessageBox.confirm(t('system.common.deleteConfirm'), t('common.tip'), { type: 'warning' })
  await deleteDictType(row.id)
  ElMessage.success(t('system.common.deleteSuccess'))
  await fetchList()
}

const goToItems = (row: DictTypeItem) => {
  router.push({ path: `/system/dict/${row.id}/items` })
}

onMounted(() => {
  void fetchList()
})
</script>

<template>
  <PageCardTableShell :title="t('system.dict.title')">
    <template #actions>
      <div class="flex items-center gap-2">
        <el-input
          v-model="query.name"
          :placeholder="t('system.dict.name')"
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
          <el-option
            v-for="opt in statusOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-button @click="fetchList">
          {{ t('common.search') }}
        </el-button>
        <el-button @click="fetchList">
          {{ t('common.refresh') }}
        </el-button>
        <el-button
          v-if="userStore.hasPermission('sys:dict_type:create')"
          type="primary"
          @click="openCreateDialog"
        >
          {{ t('system.dict.addType') }}
        </el-button>
      </div>
    </template>

    <el-table
      v-loading="loading"
      :data="list"
      stripe
    >
      <el-table-column
        prop="code"
        :label="t('system.dict.code')"
        min-width="150"
      />
      <el-table-column
        prop="name"
        :label="t('system.dict.name')"
        min-width="160"
      />
      <el-table-column
        prop="description"
        :label="t('system.dict.description')"
        min-width="220"
      />
      <el-table-column
        prop="status"
        :label="t('common.status')"
        width="110"
      >
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="resolveStatusType(roleStatusTypeMap, row.status)"
          >
            {{ t(resolveStatusLabelKey(row.status)) }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column
        v-if="canShowAnyAction"
        :label="t('common.actions')"
        width="210"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            v-if="userStore.hasPermission('sys:dict_item:read')"
            link
            class="ui-action-success"
            @click="goToItems(row)"
          >
            {{ t('system.dict.viewItems') }}
          </el-button>
          <el-button
            v-if="userStore.hasPermission('sys:dict_type:update')"
            link
            class="ui-action-primary"
            @click="openEditDialog(row)"
          >
            {{ t('common.edit') }}
          </el-button>
          <el-button
            v-if="userStore.hasPermission('sys:dict_type:delete')"
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
    :title="dialogMode === 'create' ? t('system.dict.addType') : t('common.edit')"
    width="560px"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="formRules"
      label-width="110px"
    >
      <el-form-item
        prop="code"
        :label="t('system.dict.code')"
      >
        <el-input
          v-model="form.code"
          :disabled="dialogMode === 'edit'"
        />
      </el-form-item>
      <el-form-item
        prop="name"
        :label="t('system.dict.name')"
      >
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item :label="t('system.dict.i18nKey')">
        <el-input v-model="form.i18nKey" />
      </el-form-item>
      <el-form-item :label="t('system.dict.description')">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
        />
      </el-form-item>
      <el-form-item
        prop="status"
        :label="t('common.status')"
      >
        <el-select
          v-model="form.status"
          style="width: 100%"
        >
          <el-option
            v-for="opt in statusOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
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
