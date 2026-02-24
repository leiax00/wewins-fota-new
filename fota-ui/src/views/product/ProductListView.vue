<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const userStore = useUserStore()

const canShowActions = computed(() => userStore.hasPermission('fota:product:update') || userStore.hasPermission('fota:product:delete'))
</script>

<template>
  <div>
    <PageCardTableShell :title="t('product.title')">
      <template #actions>
        <el-button
          v-if="userStore.hasPermission('fota:product:create')"
          type="primary"
          size="small"
        >
          {{ t('product.add') }}
        </el-button>
      </template>

      <el-table
        :data="[]"
        stripe
      >
        <el-table-column
          prop="name"
          :label="t('product.name')"
        />
        <el-table-column
          prop="manufacturer"
          :label="t('product.manufacturer')"
        />
        <el-table-column
          prop="model"
          :label="t('product.model')"
        />
        <el-table-column
          prop="createdAt"
          :label="t('common.createTime')"
        />
        <el-table-column
          v-if="canShowActions"
          :label="t('common.actions')"
          width="150"
        >
          <template #default>
            <el-button
              v-if="userStore.hasPermission('fota:product:update')"
              link
              class="ui-action-primary"
            >
              {{ t('common.edit') }}
            </el-button>
            <el-button
              v-if="userStore.hasPermission('fota:product:delete')"
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
