<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const userStore = useUserStore()
</script>

<template>
  <PageCardTableShell
    :title="t('system.permission.title')"
    :empty-description="t('system.permission.devInProgress')"
  >
    <template #actions>
      <el-button
        v-if="userStore.hasPermission('sys:perm:create')"
        type="primary"
        size="small"
      >
        {{ t('system.permission.add') }}
      </el-button>
    </template>
  </PageCardTableShell>
</template>
