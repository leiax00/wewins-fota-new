<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const userStore = useUserStore()

const canShowActions = computed(() => userStore.hasPermission('firmware:download') || userStore.hasPermission('firmware:delete'))
</script>

<template>
  <div>
    <PageCardTableShell :title="t('firmware.title')">
      <template #actions>
        <el-button
          v-if="userStore.hasPermission('firmware:create')"
          type="primary"
          size="small"
        >
          {{ t('firmware.upload') }}
        </el-button>
      </template>

      <el-table
        :data="[]"
        stripe
      >
        <el-table-column
          prop="version"
          :label="t('firmware.version')"
        />
        <el-table-column
          prop="product"
          :label="t('firmware.product')"
        />
        <el-table-column
          prop="fileSize"
          :label="t('firmware.fileSize')"
        />
        <el-table-column
          prop="createdAt"
          :label="t('firmware.uploadTime')"
        />
        <el-table-column
          v-if="canShowActions"
          :label="t('common.actions')"
          width="150"
        >
          <template #default>
            <el-button
              v-if="userStore.hasPermission('firmware:download')"
              link
              class="ui-action-primary"
            >
              {{ t('firmware.download') }}
            </el-button>
            <el-button
              v-if="userStore.hasPermission('firmware:delete')"
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
