<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

const { t } = useI18n()
const router = useRouter()
</script>

<template>
  <PageDetailShell
    :title="t('device.detail')"
    @back="router.back()"
  >
    <el-row :gutter="16">
      <el-col
        :xs="24"
        :sm="24"
        :md="12"
      >
        <el-card>
          <template #header>
            <span class="ui-card-title">{{ t('device.basicInfo') }}</span>
          </template>
          <el-descriptions
            :column="1"
            border
          >
            <el-descriptions-item :label="t('device.imei')">
              -
            </el-descriptions-item>
            <el-descriptions-item :label="t('firmware.product')">
              -
            </el-descriptions-item>
            <el-descriptions-item :label="t('device.currentVersion')">
              -
            </el-descriptions-item>
            <el-descriptions-item :label="t('common.status')">
              -
            </el-descriptions-item>
            <el-descriptions-item :label="t('device.lastSeen')">
              -
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col
        :xs="24"
        :sm="24"
        :md="12"
      >
        <el-card>
          <template #header>
            <span class="ui-card-title">{{ t('device.tags') }}</span>
          </template>
          <el-tag
            v-for="i in 5"
            :key="i"
            class="mr-2 mb-2"
          >
            Tag {{ i }}
          </el-tag>
        </el-card>
      </el-col>
    </el-row>
    <el-card>
      <template #header>
        <span class="ui-card-title">{{ t('device.upgradeHistory') }}</span>
      </template>
      <el-empty :description="t('device.noUpgradeRecord')" />
    </el-card>
  </PageDetailShell>
</template>
