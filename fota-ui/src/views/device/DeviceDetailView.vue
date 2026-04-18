<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { getDeviceById, type DeviceItem } from '@/api/device'
import { formatDateTime } from '@/utils/date'
import { resolveStatusLabelKey } from '@/constants/status'
import DeviceTimeline from './components/DeviceTimeline.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const deviceId = computed(() => Number(route.params.id))
const device = ref<DeviceItem | null>(null)
const loading = ref(false)

const fetchDevice = async () => {
  loading.value = true
  try {
    device.value = await getDeviceById(deviceId.value)
  } finally {
    loading.value = false
  }
}

const goBack = () => router.back()

onMounted(() => {
  void fetchDevice()
})
</script>

<template>
  <PageDetailShell
    :title="t('device.detail')"
    @back="goBack"
  >
    <div v-loading="loading">
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
                {{ device?.imei ?? '-' }}
              </el-descriptions-item>
              <el-descriptions-item :label="t('firmware.product')">
                {{ device?.productName ?? '-' }}
              </el-descriptions-item>
              <el-descriptions-item :label="t('device.currentVersion')">
                {{ device?.versionParts?.version ?? '-' }}
              </el-descriptions-item>
              <el-descriptions-item :label="t('common.status')">
                <el-tag
                  v-if="device?.status"
                  size="small"
                  :type="device.status === 'ONLINE' ? 'success' : 'info'"
                >
                  {{ t(resolveStatusLabelKey(device.status)) }}
                </el-tag>
                <span v-else>-</span>
              </el-descriptions-item>
              <el-descriptions-item :label="t('device.lastSeen')">
                {{ device?.lastSeenAt ? formatDateTime(device.lastSeenAt) : '-' }}
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
            <template v-if="device?.tags && Object.keys(device.tags).length">
              <el-tag
                v-for="(value, key) in device.tags"
                :key="key"
                class="mr-2 mb-2"
              >
                {{ key }}: {{ value }}
              </el-tag>
            </template>
            <el-empty
              v-else
              :description="'-'"
              :image-size="32"
            />
          </el-card>
        </el-col>
      </el-row>
      <DeviceTimeline
        v-if="device?.imei"
        :imei="device.imei"
      />
    </div>
  </PageDetailShell>
</template>
