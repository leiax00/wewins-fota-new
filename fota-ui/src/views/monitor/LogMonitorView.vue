<script setup lang="ts">
import { InfoFilled } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <div>
    <h1 class="ui-page-title">{{ t('menu.monitorLog') }}</h1>

    <el-card>
      <template #header>
        <span class="inline-flex items-center gap-1 ui-card-title">
          {{ t('monitor.logMonitorTitle') }}
          <el-tooltip :content="t('monitor.logMonitorDesc')" placement="top">
            <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
          </el-tooltip>
        </span>
      </template>
      <el-empty :description="t('monitor.logMonitorEmpty')" />
    </el-card>
  </div>
</template>
