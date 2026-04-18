<script setup lang="ts">
import {
  CircleCheck,
  CircleClose,
  Download,
  SuccessFilled,
} from '@element-plus/icons-vue'
import { computed, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import StatisticsTabContainer from '@/components/statistics/StatisticsTabContainer.vue'
import {
  getDeviceTimeline,
  type TimelineEvent,
  type StatisticsResponse,
  type DeviceTimelineResponse,
} from '@/api/statistics'
import { formatDateTime } from '@/utils/date'

export interface Props {
  // 设备 IMEI
  imei: string
}

const props = defineProps<Props>()

const { t } = useI18n()

const events = ref<TimelineEvent[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const nextCursor = ref<number | null>(null)
const hasMore = ref(false)
const dataCalculatedAt = ref('')
const selectedDays = ref(7)

let abortController: AbortController | null = null

const canLoadMore = computed(() => (
  hasMore.value && !!nextCursor.value && !loading.value
))

const dayOptions = computed(() => [
  { label: t('statistics.timeline.days', { days: 7 }), value: 7 },
  { label: t('statistics.timeline.days', { days: 15 }), value: 15 },
  { label: t('statistics.timeline.days', { days: 30 }), value: 30 },
])

const eventTypeConfig = computed(() => ({
  CHECK: {
    icon: CircleCheck,
    color: '#409EFF',
    label: t('statistics.timeline.check'),
  },
  DL_START: {
    icon: Download,
    color: '#E6A23C',
    label: t('statistics.timeline.dlStart'),
  },
  DL_OK: {
    icon: CircleCheck,
    color: '#67C23A',
    label: t('statistics.timeline.dlOk'),
  },
  DL_FAIL: {
    icon: CircleClose,
    color: '#F56C6C',
    label: t('statistics.timeline.dlFail'),
  },
  UP_OK: {
    icon: SuccessFilled,
    color: '#67C23A',
    label: t('statistics.timeline.upOk'),
  },
  UP_FAIL: {
    icon: CircleClose,
    color: '#F56C6C',
    label: t('statistics.timeline.upFail'),
  },
}))

const checkResultConfig = computed(() => ({
  UPDATE: {
    label: t('statistics.timeline.resultUpdate'),
    type: 'warning' as const,
  },
  NO_UPDATE: {
    label: t('statistics.timeline.resultNoUpdate'),
    type: 'info' as const,
  },
  ERROR: {
    label: t('statistics.timeline.resultError'),
    type: 'danger' as const,
  },
}))

const abortPendingRequest = () => {
  abortController?.abort()
  abortController = null
}

const resetPagination = () => {
  nextCursor.value = null
  hasMore.value = false
}

const resetListState = () => {
  events.value = []
  dataCalculatedAt.value = ''
  loadFailed.value = false
  resetPagination()
}

const fetchTimeline = async (isLoadMore = false) => {
  if (loading.value || !props.imei) {
    return
  }

  abortPendingRequest()
  abortController = new AbortController()

  loading.value = true
  if (!isLoadMore) {
    loadFailed.value = false
  }

  try {
    const result = await getDeviceTimeline(
      props.imei,
      {
        days: selectedDays.value,
        cursor: isLoadMore ? (nextCursor.value ?? undefined) : undefined,
        size: 20,
      },
      abortController.signal
    )

    applyResponse(result, isLoadMore)
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') {
      return
    }

    console.error('Failed to fetch device timeline:', error)

    if (!isLoadMore) {
      loadFailed.value = true
      events.value = []
      dataCalculatedAt.value = ''
      resetPagination()
    }
  } finally {
    loading.value = false
    abortController = null
  }
}

const applyResponse = (
  result: StatisticsResponse<DeviceTimelineResponse>,
  isLoadMore: boolean
) => {
  const eventList = result.data.timelineItems ?? []

  events.value = isLoadMore
    ? [...events.value, ...eventList]
    : eventList

  nextCursor.value = result.data.nextCursor
  hasMore.value = result.data.nextCursor !== null
  dataCalculatedAt.value = result.dataCalculatedAt
  loadFailed.value = false
}

const handleRefresh = (signal?: AbortSignal) => {
  if (signal) {
    signal.addEventListener('abort', () => {
      abortPendingRequest()
    }, { once: true })
  }

  resetPagination()
  void fetchTimeline()
}

const handleLoadMore = () => {
  if (!canLoadMore.value) {
    return
  }

  void fetchTimeline(true)
}

const handleDaysChange = () => {
  resetListState()
  void fetchTimeline()
}

watch(
  () => props.imei,
  (imei, previousImei) => {
    if (!imei || imei === previousImei) {
      return
    }

    resetListState()
    void fetchTimeline()
  },
  { immediate: true }
)

onUnmounted(() => {
  abortPendingRequest()
})
</script>

<template>
  <StatisticsTabContainer
    :data-calculated-at="dataCalculatedAt"
    :loading="loading"
    :title="t('statistics.timeline.title')"
    @refresh="handleRefresh"
  >
    <div class="device-timeline">
      <div class="device-timeline__controls">
        <el-radio-group
          v-model="selectedDays"
          size="small"
          @change="handleDaysChange"
        >
          <el-radio-button
            v-for="option in dayOptions"
            :key="option.value"
            :value="option.value"
          >
            {{ option.label }}
          </el-radio-button>
        </el-radio-group>
      </div>

      <el-alert
        v-if="loadFailed"
        type="error"
        :closable="false"
        show-icon
        class="mb-4"
      >
        <template #title>
          {{ t('statistics.timeline.loadFailed') }}
        </template>
        <template #default>
          <el-button link type="primary" @click="handleRefresh()">
            {{ t('common.refresh') }}
          </el-button>
        </template>
      </el-alert>

      <template v-else-if="events.length > 0">
        <el-timeline class="device-timeline__list">
          <el-timeline-item
            v-for="(event, index) in events"
            :key="index"
            :timestamp="formatDateTime(event.eventTime)"
            placement="top"
            :icon="eventTypeConfig[event.eventType]?.icon"
            :color="eventTypeConfig[event.eventType]?.color"
            class="device-timeline__item"
          >
            <div class="device-timeline__item-content">
              <div class="device-timeline__header">
                <span class="device-timeline__event-type">
                  {{ eventTypeConfig[event.eventType]?.label }}
                </span>
                <el-tag
                  v-if="event.checkResult"
                  :type="checkResultConfig[event.checkResult]?.type"
                  size="small"
                  class="device-timeline__check-result"
                >
                  {{ t('statistics.timeline.checkResult') }}:
                  {{ checkResultConfig[event.checkResult]?.label }}
                </el-tag>
              </div>

              <div v-if="event.policyId" class="device-timeline__policy">
                <span class="device-timeline__label">{{ t('statistics.timeline.relatedPolicy') }}:</span>
                <span class="device-timeline__value">#{{ event.policyId }}</span>
              </div>

              <div v-if="event.targetVersion" class="device-timeline__version-change">
                <span class="device-timeline__label">{{ t('statistics.timeline.versionChange') }}:</span>
                <span class="device-timeline__value">
                  <span class="device-timeline__version device-timeline__version--target">
                    {{ event.targetVersion }}
                  </span>
                </span>
              </div>

              <div v-if="event.details" class="device-timeline__error">
                {{ event.details }}
              </div>
            </div>
          </el-timeline-item>
        </el-timeline>

        <div class="device-timeline__footer">
          <el-button
            v-if="canLoadMore"
            :loading="loading"
            @click="handleLoadMore"
          >
            {{ t('statistics.timeline.loadMore') }}
          </el-button>
        </div>
      </template>

      <el-empty
        v-else
        :description="t('statistics.timeline.noData')"
      />
    </div>
  </StatisticsTabContainer>
</template>

<style scoped>
.device-timeline {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.device-timeline__controls {
  display: flex;
  justify-content: center;
}

.device-timeline__list {
  padding-left: 8px;
}

.device-timeline__item :deep(.el-timeline-item__timestamp) {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.device-timeline__item-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-left: 4px;
}

.device-timeline__header {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.device-timeline__event-type {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.device-timeline__check-result {
  font-size: 12px;
}

.device-timeline__policy,
.device-timeline__version-change {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  flex-wrap: wrap;
}

.device-timeline__label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.device-timeline__value {
  color: var(--el-text-color-primary);
  display: flex;
  align-items: center;
  gap: 4px;
}

.device-timeline__version {
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Fira Mono', 'Droid Sans Mono', 'Source Code Pro', monospace;
  padding: 2px 6px;
  border-radius: 4px;
  background-color: var(--el-fill-color-light);
  font-size: 13px;
}

.device-timeline__version--source {
  background-color: var(--el-color-info-light-9);
  color: var(--el-color-info);
}

.device-timeline__version--target {
  background-color: var(--el-color-success-light-9);
  color: var(--el-color-success);
}

.device-timeline__arrow {
  color: var(--el-text-color-secondary);
  margin: 0 2px;
}

.device-timeline__error {
  color: var(--el-color-danger);
  font-size: 13px;
  padding: 4px 8px;
  background-color: var(--el-color-danger-light-9);
  border-radius: 4px;
  word-break: break-word;
}

.device-timeline__footer {
  display: flex;
  justify-content: center;
  padding-top: 8px;
}
</style>
