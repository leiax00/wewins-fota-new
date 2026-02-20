<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { deviceStatusTypeMap, resolveStatusLabelKey, resolveStatusType } from '@/constants/status'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const userStore = useUserStore()

const canShowActions = computed(() =>
  userStore.hasPermission('device:detail') ||
  userStore.hasPermission('device:update')
)
</script>

<template>
  <div>
    <PageCardTableShell :title="t('device.title')">
      <template #actions>
        <el-button
          v-if="userStore.hasPermission('device:import')"
          type="primary"
          size="small"
        >
          {{ t('device.import') }}
        </el-button>
      </template>

      <el-table
        :data="[]"
        stripe
      >
        <el-table-column
          prop="imei"
          :label="t('device.imei')"
        />
        <el-table-column
          prop="product"
          :label="t('firmware.product')"
        />
        <el-table-column
          prop="currentVersion"
          :label="t('device.currentVersion')"
        />
        <el-table-column
          prop="status"
          :label="t('common.status')"
        >
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="resolveStatusType(deviceStatusTypeMap, row.status)"
            >
              {{ t(resolveStatusLabelKey(row.status)) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="lastSeenAt"
          :label="t('device.lastSeen')"
        />
        <el-table-column
          v-if="canShowActions"
          :label="t('common.actions')"
          width="150"
        >
          <template #default>
            <el-button
              v-if="userStore.hasPermission('device:detail')"
              link
              class="ui-action-info"
            >
              {{ t('common.detail') }}
            </el-button>
            <el-button
              v-if="userStore.hasPermission('device:update')"
              link
              class="ui-action-primary"
            >
              {{ t('common.edit') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCardTableShell>
  </div>
</template>
