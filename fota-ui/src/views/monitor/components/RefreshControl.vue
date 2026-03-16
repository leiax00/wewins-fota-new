<script setup lang="ts">
import { RefreshRight } from '@element-plus/icons-vue'
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

export interface RefreshControlProps {
  modelValue: boolean
  interval?: number
  loading?: boolean
}

export interface RefreshControlEmits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'update:interval', value: number): void
  (e: 'refresh'): void
}

const props = withDefaults(defineProps<RefreshControlProps>(), {
  interval: 15000,
  loading: false
})

const emit = defineEmits<RefreshControlEmits>()

const { t } = useI18n()

const intervalOptions = [
  { label: '5s', value: 5000 },
  { label: '10s', value: 10000 },
  { label: '15s', value: 15000 },
  { label: '30s', value: 30000 },
  { label: '1min', value: 60000 },
]

const localInterval = ref(props.interval)

watch(() => props.interval, (newVal) => {
  localInterval.value = newVal
})

watch(localInterval, (newVal) => {
  emit('update:interval', newVal)
})

const handleIntervalChange = (value: number) => {
  emit('update:interval', value)
}

const handleRefresh = () => {
  emit('refresh')
}
</script>

<template>
  <div class="flex items-center gap-3">
    <!-- 自动刷新开关 -->
    <div class="flex items-center gap-2">
      <span class="text-sm text-slate-600">{{ t('monitor.autoRefresh') }}</span>
      <el-switch
        :model-value="modelValue"
        @update:model-value="(val: boolean | string | number) => emit('update:modelValue', val as boolean)"
        inline-prompt
        :active-text="t('common.on')"
        :inactive-text="t('common.off')"
      />
    </div>

    <!-- 刷新间隔选择 -->
    <el-select
      :model-value="localInterval"
      @update:model-value="handleIntervalChange($event)"
      :disabled="!modelValue"
      size="small"
      style="width: 90px"
    >
      <el-option
        v-for="option in intervalOptions"
        :key="option.value"
        :label="option.label"
        :value="option.value"
      />
    </el-select>

    <!-- 手动刷新按钮 -->
    <el-button
      :icon="RefreshRight"
      :loading="loading"
      @click="handleRefresh"
      size="small"
      circle
    />
  </div>
</template>
