<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const userStore = useUserStore()
</script>

<template>
  <PageCardTableShell
    :title="t('system.dict.title')"
    :empty-description="t('system.dict.devInProgress')"
  >
    <template #actions>
      <el-button
        v-if="userStore.hasPermission('sys:dict_type:create') || userStore.hasPermission('sys:dict_item:create')"
        type="primary"
        size="small"
      >
        {{ t('system.dict.add') }}
      </el-button>
    </template>
  </PageCardTableShell>
</template>
