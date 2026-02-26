<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { resolveStatusLabelKey, resolveStatusType, userStatusTypeMap } from '@/constants/status'
import { useUserStore } from '@/stores/user'
import {
  assignUserRoles,
  createUser,
  deleteUser,
  listUserRoles,
  pageRoles,
  pageUsers,
  type RoleItem,
  type UserItem,
  updateUser,
} from '@/api/system'

const { t } = useI18n()
const userStore = useUserStore()

const loading = ref(false)
const list = ref<UserItem[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 20,
  username: '',
  status: '',
})

const canShowActions = computed(() =>
  userStore.hasPermission('sys:user:update') ||
  userStore.hasPermission('sys:user:assign_role') ||
  userStore.hasPermission('sys:user:delete')
)

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const editingId = ref<number | null>(null)

const formRef = ref()
const form = reactive({
  username: '',
  displayName: '',
  email: '',
  phone: '',
  status: 'active',
  passwordHash: '',
})

const formRules = {
  username: [{ required: true, message: t('system.user.usernameRequired'), trigger: 'blur' }],
  displayName: [{ required: true, message: t('system.user.displayNameRequired'), trigger: 'blur' }],
  status: [{ required: true, message: t('system.common.statusRequired'), trigger: 'change' }],
}

const statusOptions = [
  { value: 'active', label: t('status.active') },
  { value: 'disabled', label: t('status.disabled') },
]

const fetchList = async () => {
  loading.value = true
  try {
    const result = await pageUsers(query)
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
  form.username = ''
  form.displayName = ''
  form.email = ''
  form.phone = ''
  form.status = 'active'
  form.passwordHash = ''
  dialogVisible.value = true
}

const openEditDialog = (row: UserItem) => {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.username = row.username
  form.displayName = row.displayName
  form.email = row.email
  form.phone = row.phone
  form.status = row.status || 'active'
  form.passwordHash = ''
  dialogVisible.value = true
}

const submitForm = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    const payload = {
      username: form.username,
      displayName: form.displayName,
      email: form.email,
      phone: form.phone,
      status: form.status,
      passwordHash: form.passwordHash || undefined,
    }

    if (dialogMode.value === 'create') {
      await createUser(payload)
      ElMessage.success(t('system.common.createSuccess'))
    } else if (editingId.value) {
      await updateUser(editingId.value, payload)
      ElMessage.success(t('system.common.updateSuccess'))
    }

    dialogVisible.value = false
    await fetchList()
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (row: UserItem) => {
  await ElMessageBox.confirm(t('system.common.deleteConfirm'), t('common.tip'), { type: 'warning' })
  await deleteUser(row.id)
  ElMessage.success(t('system.common.deleteSuccess'))
  await fetchList()
}

const roleDialogVisible = ref(false)
const roleSubmitting = ref(false)
const currentUserId = ref<number | null>(null)
const roleOptions = ref<Array<{ key: number; label: string; disabled?: boolean }>>([])
const selectedRoleIds = ref<number[]>([])

const openRoleDialog = async (row: UserItem) => {
  currentUserId.value = row.id
  roleDialogVisible.value = true

  const [rolePage, userRoles] = await Promise.all([
    pageRoles({ page: 1, size: 500 }),
    listUserRoles(row.id),
  ])

  roleOptions.value = (rolePage.records || []).map((role: RoleItem) => ({
    key: role.id,
    label: `${role.name} (${role.code})`,
    disabled: role.status === 'disabled',
  }))
  selectedRoleIds.value = (userRoles || []).map((role) => role.id)
}

const submitRoleAssign = async () => {
  if (!currentUserId.value) return
  roleSubmitting.value = true
  try {
    await assignUserRoles(currentUserId.value, selectedRoleIds.value)
    ElMessage.success(t('system.user.assignRoleSuccess'))
    roleDialogVisible.value = false
  } finally {
    roleSubmitting.value = false
  }
}

onMounted(() => {
  void fetchList()
})
</script>

<template>
  <PageCardTableShell :title="t('system.user.title')">
    <template #actions>
      <div class="flex items-center gap-2">
        <el-input
          v-model="query.username"
          :placeholder="t('system.user.username')"
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
        <el-button @click="refreshList">
          {{ t('common.refresh') }}
        </el-button>
        <el-button
          v-if="userStore.hasPermission('sys:user:create')"
          type="primary"
          @click="openCreateDialog"
        >
          {{ t('system.user.add') }}
        </el-button>
      </div>
    </template>

    <el-table
      v-loading="loading"
      :data="list"
      stripe
    >
      <el-table-column
        prop="username"
        :label="t('system.user.username')"
        min-width="150"
      />
      <el-table-column
        prop="displayName"
        :label="t('system.user.displayName')"
        min-width="140"
      />
      <el-table-column
        prop="email"
        :label="t('system.user.email')"
        min-width="180"
      />
      <el-table-column
        prop="phone"
        :label="t('system.user.phone')"
        min-width="140"
      />
      <el-table-column
        prop="status"
        :label="t('common.status')"
        width="110"
      >
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="resolveStatusType(userStatusTypeMap, row.status)"
          >
            {{ t(resolveStatusLabelKey(row.status)) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="createdAt"
        :label="t('common.createTime')"
        min-width="170"
      />

      <el-table-column
        v-if="canShowActions"
        :label="t('common.actions')"
        width="220"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            v-if="userStore.hasPermission('sys:user:update')"
            link
            class="ui-action-primary"
            @click="openEditDialog(row)"
          >
            {{ t('common.edit') }}
          </el-button>
          <el-button
            v-if="userStore.hasPermission('sys:user:assign_role')"
            link
            class="ui-action-success"
            @click="openRoleDialog(row)"
          >
            {{ t('system.user.assignRole') }}
          </el-button>
          <el-button
            v-if="userStore.hasPermission('sys:user:delete')"
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
    :title="dialogMode === 'create' ? t('system.user.add') : t('common.edit')"
    width="520px"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="formRules"
      label-width="110px"
    >
      <el-form-item
        prop="username"
        :label="t('system.user.username')"
      >
        <el-input
          v-model="form.username"
          :disabled="dialogMode === 'edit'"
        />
      </el-form-item>
      <el-form-item
        prop="displayName"
        :label="t('system.user.displayName')"
      >
        <el-input v-model="form.displayName" />
      </el-form-item>
      <el-form-item :label="t('system.user.email')">
        <el-input v-model="form.email" />
      </el-form-item>
      <el-form-item :label="t('system.user.phone')">
        <el-input v-model="form.phone" />
      </el-form-item>
      <el-form-item :label="t('system.user.password')">
        <el-input
          v-model="form.passwordHash"
          type="password"
          show-password
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

  <el-dialog
    v-model="roleDialogVisible"
    :title="t('system.user.assignRole')"
    width="720px"
  >
    <el-transfer
      v-model="selectedRoleIds"
      filterable
      :titles="[t('system.user.availableRoles'), t('system.user.selectedRoles')]"
      :data="roleOptions"
      :props="{ key: 'key', label: 'label', disabled: 'disabled' }"
    />

    <template #footer>
      <el-button @click="roleDialogVisible = false">
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        :loading="roleSubmitting"
        @click="submitRoleAssign"
      >
        {{ t('common.save') }}
      </el-button>
    </template>
  </el-dialog>
</template>
