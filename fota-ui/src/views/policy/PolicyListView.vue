<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { policyStatusTypeMap, resolveStatusLabelKey, resolveStatusType } from '@/constants/status'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const userStore = useUserStore()

const canShowActions = computed(() =>
  userStore.hasPermission('policy:update') ||
  userStore.hasPermission('policy:pause') ||
  userStore.hasPermission('policy:delete')
)
</script>

<template>
  <div>
    <PageCardTableShell :title="t('policy.title')">
      <template #actions>
        <el-button
          v-if="userStore.hasPermission('policy:create')"
          type="primary"
          size="small"
        >
          {{ t('policy.add') }}
        </el-button>
      </template>

      <el-table
        :data="[]"
        stripe
      >
        <el-table-column
          prop="name"
          :label="t('policy.name')"
        />
        <el-table-column
          prop="product"
          :label="t('firmware.product')"
        />
        <el-table-column
          prop="targetVersion"
          :label="t('policy.targetVersion')"
        />
        <el-table-column
          prop="grayRate"
          :label="t('policy.grayRate')"
        />
        <el-table-column
          prop="status"
          :label="t('common.status')"
        >
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="resolveStatusType(policyStatusTypeMap, row.status)"
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
              v-if="userStore.hasPermission('policy:update')"
              link
              class="ui-action-primary"
            >
              {{ t('common.edit') }}
            </el-button>
            <el-button
              v-if="userStore.hasPermission('policy:pause')"
              link
              class="ui-action-warning"
            >
              {{ t('policy.pause') }}
            </el-button>
            <el-button
              v-if="userStore.hasPermission('policy:delete')"
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
