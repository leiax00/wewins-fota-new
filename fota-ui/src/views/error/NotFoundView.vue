<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <div class="flex flex-col items-center justify-center h-full">
    <el-icon
      class="text-6xl mb-4 text-ui-text-secondary"
    >
      <Warning />
    </el-icon>
    <h1
      class="text-4xl font-bold mb-2 text-ui-text-primary"
    >
      404
    </h1>
    <p
      class="mb-4 text-ui-text-secondary"
    >
      {{ t('error.notFound') }}
    </p>
    <el-button
      type="primary"
      @click="$router.push('/')"
    >
      {{ t('error.backHome') }}
    </el-button>
  </div>
</template>
