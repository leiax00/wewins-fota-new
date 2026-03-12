<script setup lang="ts">
import { InfoFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { cacheMonitorApi } from '@/api/monitor'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const userStore = useUserStore()

const canUpdateCache = computed(() => userStore.hasPermission('monitor:cache:update'))

const form = reactive({
  productId: undefined as number | undefined,
  productModel: '',
  imeisText: '',
  evictProductCache: true,
  evictPolicyCache: true,
})

const submit = async () => {
  const imeis = form.imeisText
    .split(/[\n,]+/)
    .map(item => item.trim())
    .filter(Boolean)

  const result = await cacheMonitorApi.evict({
    productId: form.productId,
    productModel: form.productModel || undefined,
    imeis,
    evictProductCache: form.evictProductCache,
    evictPolicyCache: form.evictPolicyCache,
  })
  ElMessage.success(t('monitor.cacheEvictSuccess', {
    scopes: result.evictedScopes,
    devices: result.evictedDeviceCount,
  }))
}
</script>

<template>
  <div>
    <h1 class="ui-page-title">{{ t('menu.monitorCache') }}</h1>

    <el-card class="mb-6">
      <template #header>
        <span class="inline-flex items-center gap-1 ui-card-title">
          {{ t('monitor.cacheMonitorTitle') }}
          <el-tooltip :content="t('monitor.cacheMonitorDesc')" placement="top">
            <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
          </el-tooltip>
        </span>
      </template>
      <p class="text-sm text-slate-500">
        {{ t('monitor.cacheMonitorEmpty') }}
      </p>
    </el-card>

    <el-card>
      <template #header>
        <span class="inline-flex items-center gap-1 ui-card-title">
          {{ t('monitor.cacheEvictTitle') }}
          <el-tooltip :content="t('monitor.cacheEvictDesc')" placement="top">
            <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
          </el-tooltip>
        </span>
      </template>
      <el-form label-width="160px">
        <el-form-item :label="t('monitor.cacheEvictProductId')">
          <el-input-number v-model="form.productId" :min="1" :disabled="!canUpdateCache" />
        </el-form-item>

        <el-form-item :label="t('monitor.cacheEvictProductModel')">
          <el-input v-model="form.productModel" :disabled="!canUpdateCache" />
        </el-form-item>

        <el-form-item :label="t('monitor.cacheEvictImeis')">
          <el-input
            v-model="form.imeisText"
            type="textarea"
            :rows="4"
            :disabled="!canUpdateCache"
            :placeholder="t('monitor.cacheEvictImeisPlaceholder')"
          />
        </el-form-item>

        <el-form-item :label="t('monitor.cacheEvictScopes')">
          <el-checkbox v-model="form.evictProductCache" :disabled="!canUpdateCache">
            {{ t('monitor.cacheEvictProductCache') }}
          </el-checkbox>
          <el-checkbox v-model="form.evictPolicyCache" :disabled="!canUpdateCache">
            {{ t('monitor.cacheEvictPolicyCache') }}
          </el-checkbox>
        </el-form-item>

        <el-form-item v-if="canUpdateCache">
          <el-button type="danger" @click="submit">
            {{ t('monitor.cacheEvictAction') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>
