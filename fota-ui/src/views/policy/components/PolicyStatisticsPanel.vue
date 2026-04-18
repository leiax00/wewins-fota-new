<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import StatisticsTabContainer from '@/components/statistics/StatisticsTabContainer.vue'
import {
  getPolicySummary,
  type PolicySummaryResponse,
} from '@/api/statistics'

export interface Props {
  policyId: number
}

const props = defineProps<Props>()

const { t } = useI18n()

const summary = ref<PolicySummaryResponse | null>(null)
const loading = ref(false)
const dataCalculatedAt = ref('')

let abortController: AbortController | null = null

const summaryItems = computed(() => [
  {
    key: 'affectedTotal',
    label: t('statistics.policy.affectedTotal'),
    value: summary.value?.affectedTotal ?? 0,
    color: '#409EFF',
    icon: '📊',
  },
  {
    key: 'upgradedTotal',
    label: t('statistics.policy.upgradedTotal'),
    value: summary.value?.upgradedTotal ?? 0,
    color: '#67C23A',
    icon: '✅',
  },
  {
    key: 'pendingUpgradeTotal',
    label: t('statistics.policy.pendingUpgrade'),
    value: summary.value?.pendingUpgradeTotal ?? 0,
    color: '#E6A23C',
    icon: '⏳',
  },
])

const abortPendingRequest = () => {
  abortController?.abort()
  abortController = null
}

const fetchData = async () => {
  if (loading.value || !props.policyId) {
    return
  }

  abortPendingRequest()
  abortController = new AbortController()
  loading.value = true

  try {
    const result = await getPolicySummary(props.policyId, abortController.signal)
    summary.value = result.data
    dataCalculatedAt.value = result.dataCalculatedAt
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') {
      return
    }

    console.error('Failed to fetch policy statistics:', error)
    summary.value = null
    dataCalculatedAt.value = ''
  } finally {
    loading.value = false
    abortController = null
  }
}

const handleRefresh = (signal?: AbortSignal) => {
  if (signal) {
    signal.addEventListener('abort', () => abortPendingRequest(), { once: true })
  }
  void fetchData()
}

watch(
  () => props.policyId,
  (policyId, previousPolicyId) => {
    if (!policyId || policyId === previousPolicyId) {
      return
    }

    void fetchData()
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
    refresh-scope="policy"
    :refresh-scope-id="policyId"
    @refresh="handleRefresh"
  >
    <div class="policy-statistics-panel">
      <el-row :gutter="16" class="policy-statistics-panel__summary">
        <el-col
          v-for="item in summaryItems"
          :key="item.key"
          :xs="24"
          :sm="12"
          :md="8"
        >
          <div class="summary-card">
            <div
              class="summary-card__icon"
              :style="{ backgroundColor: `${item.color}20` }"
            >
              <span class="summary-card__icon-emoji">{{ item.icon }}</span>
            </div>
            <div class="summary-card__content">
              <div class="summary-card__label">{{ item.label }}</div>
              <div
                class="summary-card__value"
                :style="{ color: item.color }"
              >
                {{ item.value.toLocaleString() }}
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>
  </StatisticsTabContainer>
</template>

<style scoped>
.policy-statistics-panel {
  display: flex;
  flex-direction: column;
}

.summary-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background-color: var(--el-fill-color-blank);
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
  transition: all 0.3s ease;
  height: 100%;
}

.summary-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  border-color: var(--el-border-color);
}

.summary-card__icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  flex-shrink: 0;
}

.summary-card__icon-emoji {
  font-size: 24px;
  line-height: 1;
}

.summary-card__content {
  flex: 1;
  min-width: 0;
}

.summary-card__label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}

.summary-card__value {
  font-size: 24px;
  font-weight: 600;
  line-height: 1.2;
}

@media (max-width: 768px) {
  .summary-card {
    padding: 12px;
  }

  .summary-card__icon {
    width: 40px;
    height: 40px;
  }

  .summary-card__icon-emoji {
    font-size: 20px;
  }

  .summary-card__value {
    font-size: 20px;
  }
}
</style>
