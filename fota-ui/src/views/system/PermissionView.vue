<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import {
  createPermission,
  deletePermission,
  listPermissionTree,
  updatePermission,
  type PermissionItem,
  type PermissionTreeNode,
} from '@/api/system'
import { trimFormValues } from '@/utils/form'

interface PermissionRow extends PermissionItem {
  children?: PermissionRow[]
}

const { t } = useI18n()
const userStore = useUserStore()

const loading = ref(false)
const query = reactive({
  name: '',
  type: '',
  status: '',
})

const statusSwitchLoadingIds = ref<number[]>([])
const expandedRowKeys = ref<string[]>([])

const canShowActions = computed(() =>
  userStore.hasPermission('sys:perm:update') || userStore.hasPermission('sys:perm:delete')
)

const typeOptions = [
  { value: 'MENU', label: t('system.permission.typeMenu') },
  { value: 'API', label: t('system.permission.typeApi') },
  { value: 'BUTTON', label: t('system.permission.typeButton') },
]

const mapTree = (nodes: PermissionTreeNode[]): PermissionRow[] => {
  return nodes.map((node) => ({
    ...node.permission,
    children: mapTree(node.children || []),
  }))
}

const matchRow = (row: PermissionRow): boolean => {
  const trimmedName = query.name.trim().toLowerCase()
  const nameMatch = !trimmedName || row.name.toLowerCase().includes(trimmedName) || row.code.toLowerCase().includes(trimmedName)
  const typeMatch = !query.type || row.type === query.type
  const statusMatch = !query.status || row.status === query.status
  return nameMatch && typeMatch && statusMatch
}

const filterTree = (source: PermissionRow[]): PermissionRow[] => {
  return source.reduce<PermissionRow[]>((result, row) => {
    const children = row.children?.length ? filterTree(row.children) : []
    const currentMatches = matchRow(row)
    if (!currentMatches && children.length === 0) return result
    result.push({
      ...row,
      children,
    })
    return result
  }, [])
}

const fullTree = ref<PermissionRow[]>([])
const filteredRows = computed(() => filterTree(fullTree.value))

const fetchTree = async () => {
  loading.value = true
  try {
    const tree = await listPermissionTree()
    const mapped = mapTree(tree)
    fullTree.value = mapped
    expandedRowKeys.value = mapped.map((item) => item.code)
  } finally {
    loading.value = false
  }
}

const refreshTree = () => {
  void fetchTree()
}

const handleExpandChange = (row: PermissionRow, expandedRows: PermissionRow[] | boolean) => {
  const isExpanded = Array.isArray(expandedRows)
    ? expandedRows.some((item) => item.code === row.code)
    : expandedRows

  if (isExpanded) {
    if (!expandedRowKeys.value.includes(row.code)) {
      expandedRowKeys.value = [...expandedRowKeys.value, row.code]
    }
    return
  }

  expandedRowKeys.value = expandedRowKeys.value.filter((code) => !code.startsWith(row.code))
}

const isStatusSwitchLoading = (id: number) => statusSwitchLoadingIds.value.includes(id)

const setSwitchLoading = (id: number, value: boolean) => {
  if (value) {
    if (!statusSwitchLoadingIds.value.includes(id)) {
      statusSwitchLoadingIds.value.push(id)
    }
    return
  }
  statusSwitchLoadingIds.value = statusSwitchLoadingIds.value.filter((item) => item !== id)
}

const handleStatusToggle = async (row: PermissionRow, nextStatus: string) => {
  const previousStatus = row.status
  // 乐观更新
  row.status = nextStatus

  setSwitchLoading(row.id, true)
  try {
    await updatePermission(row.id, {
      code: row.code,
      name: row.name,
      type: row.type,
      path: row.path,
      method: row.method,
      parentId: row.parentId,
      status: nextStatus,
    })
    ElMessage.success(t('system.common.updateSuccess'))
  } catch {
    // 回滚状态
    row.status = previousStatus
    ElMessage.error(t('system.common.updateFailed'))
  } finally {
    setSwitchLoading(row.id, false)
  }
}

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref()
const parentOptions = ref<Array<{ label: string; value: number }>>([])

const form = reactive({
  code: '',
  name: '',
  type: 'MENU',
  path: '',
  method: '',
  parentId: undefined as number | undefined,
  status: 'active',
})

const formRules = {
  code: [{ required: true, message: t('system.permission.codeRequired'), trigger: 'blur' }],
  name: [{ required: true, message: t('system.permission.nameRequired'), trigger: 'blur' }],
  type: [{ required: true, message: t('system.permission.typeRequired'), trigger: 'change' }],
  status: [{ required: true, message: t('system.common.statusRequired'), trigger: 'change' }],
}

const buildParentOptions = (nodes: PermissionTreeNode[], depth = 0): Array<{ label: string; value: number }> => {
  return nodes.flatMap((node) => {
    const label = `${'  '.repeat(depth)}${node.permission.name}`
    const current = [{ label, value: node.permission.id }]
    const children = node.children?.length ? buildParentOptions(node.children, depth + 1) : []
    return [...current, ...children]
  })
}

const loadParentOptions = async () => {
  const tree = await listPermissionTree()
  parentOptions.value = buildParentOptions(tree)
}

const openCreateDialog = async () => {
  await loadParentOptions()
  dialogMode.value = 'create'
  editingId.value = null
  form.code = ''
  form.name = ''
  form.type = 'MENU'
  form.path = ''
  form.method = ''
  form.parentId = undefined
  form.status = 'active'
  dialogVisible.value = true
}

const openEditDialog = async (row: PermissionRow) => {
  await loadParentOptions()
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.code = row.code
  form.name = row.name
  form.type = row.type
  form.path = row.path || ''
  form.method = row.method || ''
  form.parentId = row.parentId || undefined
  form.status = row.status || 'active'
  dialogVisible.value = true
}

const submitForm = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    const payload = trimFormValues({
      code: form.code,
      name: form.name,
      type: form.type,
      path: form.path,
      method: form.method,
      parentId: form.parentId,
      status: form.status,
    })

    if (dialogMode.value === 'create') {
      await createPermission(payload)
      ElMessage.success(t('system.common.createSuccess'))
    } else if (editingId.value) {
      await updatePermission(editingId.value, payload)
      ElMessage.success(t('system.common.updateSuccess'))
    }

    dialogVisible.value = false
    await fetchTree()
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (row: PermissionRow) => {
  await ElMessageBox.confirm(t('system.common.deleteConfirm'), t('common.tip'), { type: 'warning' })
  await deletePermission(row.id)
  ElMessage.success(t('system.common.deleteSuccess'))
  await fetchTree()
}

onMounted(() => {
  void fetchTree()
})
</script>

<template>
  <PageCardTableShell :title="t('system.permission.title')">
    <template #actions>
      <div class="flex items-center gap-2">
        <el-input
          v-model="query.name"
          :placeholder="t('system.permission.name')"
          clearable
          style="width: 220px"
        />
        <el-select
          v-model="query.type"
          :placeholder="t('system.permission.type')"
          clearable
          style="width: 150px"
        >
          <el-option
            v-for="opt in typeOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-select
          v-model="query.status"
          :placeholder="t('common.status')"
          clearable
          style="width: 140px"
        >
          <el-option
            :label="t('status.active')"
            value="active"
          />
          <el-option
            :label="t('status.disabled')"
            value="disabled"
          />
        </el-select>
        <el-button @click="fetchTree">
          {{ t('common.search') }}
        </el-button>
        <el-button @click="refreshTree">
          {{ t('common.refresh') }}
        </el-button>
        <el-button
          v-if="userStore.hasPermission('sys:perm:create')"
          type="primary"
          @click="openCreateDialog"
        >
          {{ t('system.permission.add') }}
        </el-button>
      </div>
    </template>

    <el-table
      v-loading="loading"
      :data="filteredRows"
      stripe
      row-key="code"
      :expand-row-keys="expandedRowKeys"
      :tree-props="{ children: 'children' }"
      @expand-change="handleExpandChange"
    >
      <el-table-column
        prop="name"
        :label="t('system.permission.name')"
        min-width="180"
      />
      <el-table-column
        prop="code"
        :label="t('system.permission.code')"
        min-width="220"
      />
      <el-table-column
        prop="type"
        :label="t('system.permission.type')"
        width="110"
      />
      <el-table-column
        prop="path"
        :label="t('system.permission.path')"
        min-width="190"
      />
      <el-table-column
        prop="method"
        :label="t('system.permission.method')"
        width="100"
      />
      <el-table-column
        :label="t('common.status')"
        width="140"
      >
        <template #default="{ row }">
          <el-switch
            v-if="userStore.hasPermission('sys:perm:update')"
            v-model="row.status"
            active-value="active"
            inactive-value="disabled"
            :loading="isStatusSwitchLoading(row.id)"
            @change="(value: string | number | boolean) => handleStatusToggle(row, String(value))"
          />
          <el-tag
            v-else
            size="small"
            :type="row.status === 'active' ? 'success' : 'info'"
          >
            {{ t(row.status === 'active' ? 'status.active' : 'status.disabled') }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column
        v-if="canShowActions"
        :label="t('common.actions')"
        width="130"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            v-if="userStore.hasPermission('sys:perm:update')"
            link
            class="ui-action-primary"
            @click="openEditDialog(row)"
          >
            {{ t('common.edit') }}
          </el-button>
          <el-button
            v-if="userStore.hasPermission('sys:perm:delete')"
            link
            class="ui-action-danger"
            @click="handleDelete(row)"
          >
            {{ t('common.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </PageCardTableShell>

  <el-dialog
    v-model="dialogVisible"
    :title="dialogMode === 'create' ? t('system.permission.add') : t('common.edit')"
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
        :label="t('system.permission.name')"
      >
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item
        prop="code"
        :label="t('system.permission.code')"
      >
        <el-input v-model="form.code" />
      </el-form-item>
      <el-form-item
        prop="type"
        :label="t('system.permission.type')"
      >
        <el-select
          v-model="form.type"
          style="width: 100%"
        >
          <el-option
            v-for="opt in typeOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('system.permission.parent')">
        <el-select
          v-model="form.parentId"
          clearable
          style="width: 100%"
        >
          <el-option
            v-for="opt in parentOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('system.permission.path')">
        <el-input v-model="form.path" />
      </el-form-item>
      <el-form-item :label="t('system.permission.method')">
        <el-select
          v-model="form.method"
          clearable
          style="width: 100%"
        >
          <el-option
            label="GET"
            value="GET"
          />
          <el-option
            label="POST"
            value="POST"
          />
          <el-option
            label="PUT"
            value="PUT"
          />
          <el-option
            label="DELETE"
            value="DELETE"
          />
        </el-select>
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
            :label="t('status.active')"
            value="active"
          />
          <el-option
            :label="t('status.disabled')"
            value="disabled"
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
