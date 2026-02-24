<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { resolveStatusLabelKey, resolveStatusType, roleStatusTypeMap } from '@/constants/status'
import { useUserStore } from '@/stores/user'
import {
  assignRolePermissions,
  createRole,
  deleteRole,
  listPermissionTree,
  listRolePermissions,
  pageRoles,
  updateRole,
  type PermissionTreeNode,
  type RoleItem,
} from '@/api/system'

interface PermissionTreeOption {
  id: number
  label: string
  children: PermissionTreeOption[]
}

const { t } = useI18n()
const userStore = useUserStore()

const loading = ref(false)
const list = ref<RoleItem[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 20,
  name: '',
  status: '',
})

const canShowActions = computed(() =>
  userStore.hasPermission('sys:role:update') ||
  userStore.hasPermission('sys:role:assign_perm') ||
  userStore.hasPermission('sys:role:delete')
)

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref()

const form = reactive({
  code: '',
  name: '',
  description: '',
  status: 'active',
})

const formRules = {
  code: [{ required: true, message: t('system.role.codeRequired'), trigger: 'blur' }],
  name: [{ required: true, message: t('system.role.nameRequired'), trigger: 'blur' }],
  status: [{ required: true, message: t('system.common.statusRequired'), trigger: 'change' }],
}

const statusOptions = [
  { value: 'active', label: t('status.active') },
  { value: 'disabled', label: t('status.disabled') },
]

const permissionDialogVisible = ref(false)
const permissionSubmitting = ref(false)
const currentRoleId = ref<number | null>(null)
const permissionTree = ref<PermissionTreeOption[]>([])
const checkedPermissionIds = ref<number[]>([])
const permissionTreeRef = ref()
const permissionLinkage = ref(true)
const permissionExpandedKeys = ref<number[]>([])
const permissionTreeRenderKey = ref(0)

const permissionProps = {
  label: 'label',
  children: 'children',
}

const allPermissionIds = computed(() => {
  const collect = (nodes: PermissionTreeOption[]): number[] => {
    return nodes.reduce<number[]>((acc, node) => {
      acc.push(node.id)
      if (node.children?.length) {
        acc.push(...collect(node.children))
      }
      return acc
    }, [])
  }
  return collect(permissionTree.value)
})

const fetchList = async () => {
  loading.value = true
  try {
    const result = await pageRoles(query)
    list.value = result.records || []
    total.value = result.total || 0
  } finally {
    loading.value = false
  }
}

const refreshList = () => {
  void fetchList()
}

const openCreateDialog = () => {
  dialogMode.value = 'create'
  editingId.value = null
  form.code = ''
  form.name = ''
  form.description = ''
  form.status = 'active'
  dialogVisible.value = true
}

const openEditDialog = (row: RoleItem) => {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.code = row.code
  form.name = row.name
  form.description = row.description
  form.status = row.status || 'active'
  dialogVisible.value = true
}

const submitForm = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    const payload = {
      code: form.code,
      name: form.name,
      description: form.description,
      status: form.status,
    }
    if (dialogMode.value === 'create') {
      await createRole(payload)
      ElMessage.success(t('system.common.createSuccess'))
    } else if (editingId.value) {
      await updateRole(editingId.value, payload)
      ElMessage.success(t('system.common.updateSuccess'))
    }
    dialogVisible.value = false
    await fetchList()
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (row: RoleItem) => {
  await ElMessageBox.confirm(t('system.common.deleteConfirm'), t('common.tip'), { type: 'warning' })
  await deleteRole(row.id)
  ElMessage.success(t('system.common.deleteSuccess'))
  await fetchList()
}

const flattenIds = (nodes: PermissionTreeNode[]): number[] => {
  return nodes.reduce<number[]>((acc, node) => {
    acc.push(node.permission.id)
    if (node.children?.length) {
      acc.push(...flattenIds(node.children))
    }
    return acc
  }, [])
}

const mapTree = (nodes: PermissionTreeNode[]): PermissionTreeOption[] => {
  return nodes.map((node) => ({
    id: node.permission.id,
    label: `${node.permission.name} (${node.permission.code})`,
    children: mapTree(node.children || []),
  }))
}

/**
 * 收集所有叶子节点（API类型的权限）
 * 在父子关联模式下，只需要设置叶子节点的选中状态，父节点会自动勾选
 */
const collectLeafPermissionIds = (nodes: PermissionTreeNode[]): number[] => {
  const leafIds: number[] = []

  const traverse = (permissions: PermissionTreeNode[]) => {
    for (const node of permissions) {
      // 如果是叶子节点（没有子节点），或者是API类型，则收集
      const isLeaf = !node.children || node.children.length === 0
      const isApi = node.permission.type === 'API'

      if (isLeaf || isApi) {
        leafIds.push(node.permission.id)
      }

      // 递归遍历子节点
      if (node.children?.length) {
        traverse(node.children)
      }
    }
  }

  traverse(nodes)
  return leafIds
}

const openPermissionDialog = async (row: RoleItem) => {
  currentRoleId.value = row.id
  permissionDialogVisible.value = true

  const [tree, selected] = await Promise.all([
    listPermissionTree(),
    listRolePermissions(row.id),
  ])
  permissionTree.value = mapTree(tree)
  permissionExpandedKeys.value = permissionTree.value.map((item) => item.id)
  permissionTreeRenderKey.value += 1

  // 只设置叶子节点（API类型）的选中状态
  // 在父子关联模式下，父节点会自动勾选
  const selectedIds = new Set(selected.map((item) => item.id))
  const allLeafIds = collectLeafPermissionIds(tree)
  checkedPermissionIds.value = allLeafIds.filter((id) => selectedIds.has(id))
}

const submitPermissions = async () => {
  if (!currentRoleId.value) return
  permissionSubmitting.value = true
  try {
    const checked = permissionTreeRef.value?.getCheckedKeys() || []
    const half = permissionTreeRef.value?.getHalfCheckedKeys() || []
    const ids = [...new Set([...checked, ...half])].map((item) => Number(item))
    await assignRolePermissions(currentRoleId.value, ids)
    ElMessage.success(t('system.role.assignPermissionSuccess'))
    permissionDialogVisible.value = false
  } finally {
    permissionSubmitting.value = false
  }
}

const checkAllPermissions = () => {
  permissionTreeRef.value?.setCheckedKeys(allPermissionIds.value)
}

const clearAllPermissions = () => {
  permissionTreeRef.value?.setCheckedKeys([])
}

onMounted(() => {
  void fetchList()
})
</script>

<template>
  <PageCardTableShell :title="t('system.role.title')">
    <template #actions>
      <div class="flex items-center gap-2">
        <el-input
          v-model="query.name"
          :placeholder="t('system.role.name')"
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
        <el-button @click="fetchList">{{ t('common.search') }}</el-button>
        <el-button @click="refreshList">{{ t('common.refresh') }}</el-button>
        <el-button
          v-if="userStore.hasPermission('sys:role:create')"
          type="primary"
          @click="openCreateDialog"
        >
          {{ t('system.role.add') }}
        </el-button>
      </div>
    </template>

    <el-table
      v-loading="loading"
      :data="list"
      stripe
    >
      <el-table-column prop="code" :label="t('system.role.code')" min-width="160" />
      <el-table-column prop="name" :label="t('system.role.name')" min-width="160" />
      <el-table-column prop="description" :label="t('system.role.description')" min-width="220" />
      <el-table-column prop="status" :label="t('common.status')" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="resolveStatusType(roleStatusTypeMap, row.status)">
            {{ t(resolveStatusLabelKey(row.status)) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" :label="t('common.createTime')" min-width="170" />

      <el-table-column
        v-if="canShowActions"
        :label="t('common.actions')"
        width="210"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            v-if="userStore.hasPermission('sys:role:update')"
            link
            class="ui-action-primary"
            @click="openEditDialog(row)"
          >
            {{ t('common.edit') }}
          </el-button>
          <el-button
            v-if="userStore.hasPermission('sys:role:assign_perm')"
            link
            class="ui-action-success"
            @click="openPermissionDialog(row)"
          >
            {{ t('system.role.assignPermission') }}
          </el-button>
          <el-button
            v-if="userStore.hasPermission('sys:role:delete')"
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

  <el-dialog
    v-model="dialogVisible"
    :title="dialogMode === 'create' ? t('system.role.add') : t('common.edit')"
    width="520px"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="formRules"
      label-width="110px"
    >
      <el-form-item prop="code" :label="t('system.role.code')">
        <el-input v-model="form.code" :disabled="dialogMode === 'edit'" />
      </el-form-item>
      <el-form-item prop="name" :label="t('system.role.name')">
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item :label="t('system.role.description')">
        <el-input v-model="form.description" type="textarea" :rows="3" />
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

  <el-dialog
    v-model="permissionDialogVisible"
    :title="t('system.role.assignPermission')"
    width="600px"
  >
    <div class="permission-toolbar">
      <el-switch
        v-model="permissionLinkage"
        :active-text="t('system.role.parentChildLinkage')"
      />
      <div class="permission-toolbar-actions">
        <el-button size="small" @click="checkAllPermissions">{{ t('system.role.selectAllPermissions') }}</el-button>
        <el-button size="small" @click="clearAllPermissions">{{ t('system.role.clearAllPermissions') }}</el-button>
      </div>
    </div>

    <div class="permission-tree-wrap">
      <el-tree
        :key="permissionTreeRenderKey"
        ref="permissionTreeRef"
        node-key="id"
        show-checkbox
        :check-strictly="!permissionLinkage"
        :default-expanded-keys="permissionExpandedKeys"
        :data="permissionTree"
        :props="permissionProps"
        :default-checked-keys="checkedPermissionIds"
      />
    </div>

    <template #footer>
      <el-button @click="permissionDialogVisible = false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="permissionSubmitting" @click="submitPermissions">{{ t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.permission-tree-wrap {
  max-height: 52vh;
  overflow: auto;
  padding-right: 6px;
}

.permission-toolbar {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.permission-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
