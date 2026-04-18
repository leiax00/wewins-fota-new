<script setup lang="ts">
import { RefreshRight } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { RefreshScope } from '@/api/statistics'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

export interface Props {
  // 数据计算时间（ISO8601 字符串）
  dataCalculatedAt?: string
  // 是否正在加载
  loading?: boolean
  // 刷新范围（用于刷新 API）
  refreshScope?: RefreshScope
  // 刷新范围的 ID
  refreshScopeId?: number
  // 是否禁用刷新按钮
  refreshDisabled?: boolean
  // 组件标题（可选）
  title?: string
}

export interface Emits {
  // 刷新事件
  (e: 'refresh', signal: AbortSignal): void
}

const props = withDefaults(defineProps<Props>(), {
  dataCalculatedAt: '',
  loading: false,
  refreshScope: 'all',
  refreshScopeId: undefined,
  refreshDisabled: false,
  title: '',
})

const emit = defineEmits<Emits>()

const { t, locale } = useI18n()

const relativeTimeText = ref('')
const abortController = ref<AbortController | null>(null)
let timer: number | null = null

const canRefreshByScope = computed(() => {
  if (props.refreshScope === 'all') {
    return true
  }

  return props.refreshScopeId != null
})

const refreshButtonDisabled = computed(() => (
  props.refreshDisabled || !canRefreshByScope.value
))

const getRelativeTime = (calculatedAt: string): string => {
  const calculated = dayjs(calculatedAt)

  if (!calculated.isValid()) {
    return ''
  }

  const now = dayjs()
  const secondsDiff = Math.max(now.diff(calculated, 'second'), 0)

  if (secondsDiff < 5) {
    return t('statistics.updatedJustNow')
  }

  return t('statistics.updatedSecondsAgo', { seconds: secondsDiff })
}

// 同步刷新“更新于 N 秒前”文案，保证显示实时变化。
const updateRelativeTime = () => {
  if (!props.dataCalculatedAt) {
    relativeTimeText.value = ''
    return
  }

  relativeTimeText.value = getRelativeTime(props.dataCalculatedAt)
}

const startTimer = () => {
  updateRelativeTime()

  if (timer != null) {
    window.clearInterval(timer)
  }

  timer = window.setInterval(updateRelativeTime, 1000)
}

const stopTimer = () => {
  if (timer == null) {
    return
  }

  window.clearInterval(timer)
  timer = null
}

const handleRefresh = () => {
  if (refreshButtonDisabled.value) {
    return
  }

  // 每次刷新前取消上一个未完成的请求，避免旧结果覆盖新状态。
  abortController.value?.abort()
  abortController.value = new AbortController()

  emit('refresh', abortController.value.signal)
}

watch(() => props.dataCalculatedAt, () => {
  updateRelativeTime()
})

watch(locale, () => {
  updateRelativeTime()
})

onMounted(() => {
  startTimer()
})

onUnmounted(() => {
  stopTimer()
  abortController.value?.abort()
  abortController.value = null
})
</script>

<template>
  <el-card
    class="statistics-tab-container"
    :data-refresh-scope="refreshScope"
    :data-refresh-scope-id="refreshScopeId ?? undefined"
  >
    <template #header>
      <div class="flex items-center justify-between gap-4">
        <div class="min-w-0">
          <span
            v-if="title"
            class="ui-card-title"
          >
            {{ title }}
          </span>
        </div>

        <div class="flex items-center gap-3">
          <span
            v-if="relativeTimeText"
            class="text-sm text-slate-500"
          >
            {{ relativeTimeText }}
          </span>

          <el-button
            :icon="RefreshRight"
            :loading="loading"
            :disabled="refreshButtonDisabled"
            size="small"
            @click="handleRefresh"
          >
            {{ t('statistics.refresh') }}
          </el-button>
        </div>
      </div>
    </template>

    <div
      v-loading="loading"
      class="statistics-tab-container__content"
    >
      <slot />
    </div>
  </el-card>
</template>

<style scoped>
.statistics-tab-container__content {
  position: relative;
  min-height: 160px;
}
</style>
