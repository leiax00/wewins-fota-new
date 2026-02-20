<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { resolveStatusLabelKey, resolveStatusType, roleStatusTypeMap } from '@/constants/status'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const userStore = useUserStore()

const canShowActions = computed(() =>
  userStore.hasPermission('sys:role:update') ||
  userStore.hasPermission('sys:role:assign_perm') ||
  userStore.hasPermission('sys:role:delete')
)
</script>

<template>
  <div>
    <PageCardTableShell :title="t('system.role.title')">
      <template #actions>
        <el-button
          v-if="userStore.hasPermission('sys:role:create')"
          type="primary"
          size="small"
        >
          {{ t('system.role.add') }}
        </el-button>
      </template>

      <el-table
        :data="[]"
        stripe
      >
        <el-table-column
          prop="code"
          :label="t('system.role.code')"
        />
        <el-table-column
          prop="name"
          :label="t('system.role.name')"
        />
        <el-table-column
          prop="description"
          :label="t('system.role.description')"
        />
        <el-table-column
          prop="status"
          :label="t('common.status')"
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
          v-if="canShowActions"
          :label="t('common.actions')"
          width="180"
        >
          <template #default>
            <el-button
              v-if="userStore.hasPermission('sys:role:update')"
              link
              class="ui-action-primary"
            >
              {{ t('common.edit') }}
            </el-button>
            <el-button
              v-if="userStore.hasPermission('sys:role:assign_perm')"
              link
              class="ui-action-success"
            >
              {{ t('system.role.assignPermission') }}
            </el-button>
            <el-button
              v-if="userStore.hasPermission('sys:role:delete')"
              link
              class="ui-action-danger"
            >
              {{ t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCardTableShell>
  </div>
</template>
