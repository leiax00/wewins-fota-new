<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { resolveStatusLabelKey, resolveStatusType, userStatusTypeMap } from '@/constants/status'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const userStore = useUserStore()

const canShowActions = computed(() =>
  userStore.hasPermission('sys:user:update') ||
  userStore.hasPermission('sys:user:delete')
)
</script>

<template>
  <div>
    <PageCardTableShell :title="t('system.user.title')">
      <template #actions>
        <el-button
          v-if="userStore.hasPermission('sys:user:create')"
          type="primary"
          size="small"
        >
          {{ t('system.user.add') }}
        </el-button>
      </template>

      <el-table
        :data="[]"
        stripe
      >
        <el-table-column
          prop="username"
          :label="t('system.user.username')"
        />
        <el-table-column
          prop="displayName"
          :label="t('system.user.displayName')"
        />
        <el-table-column
          prop="email"
          :label="t('system.user.email')"
        />
        <el-table-column
          prop="status"
          :label="t('common.status')"
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
          v-if="canShowActions"
          :label="t('common.actions')"
          width="180"
        >
          <template #default>
            <el-button
              v-if="userStore.hasPermission('sys:user:update')"
              link
              class="ui-action-primary"
            >
              {{ t('common.edit') }}
            </el-button>
            <el-button
              v-if="userStore.hasPermission('sys:user:update')"
              link
              class="ui-action-warning"
            >
              {{ t('system.user.resetPassword') }}
            </el-button>
            <el-button
              v-if="userStore.hasPermission('sys:user:delete')"
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
