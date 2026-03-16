import { computed, type MaybeRefOrGetter, toValue } from 'vue'
import { useI18n } from 'vue-i18n'

export type LoadLevel = 'LOW' | 'NORMAL' | 'HIGH' | 'CRITICAL'
export type CircuitState = 'CLOSED' | 'OPEN' | 'HALF_OPEN'

/**
 * 负载等级 Composable
 * @param loadLevel - 负载等级的响应式引用或 getter 函数
 */
export function useLoadLevel(loadLevel: MaybeRefOrGetter<LoadLevel | string | undefined>) {
  const { t } = useI18n()

  const loadLevelColor = computed(() => {
    const level = toValue(loadLevel)
    switch (level) {
      case 'LOW':
        return 'success'
      case 'NORMAL':
        return 'info'
      case 'HIGH':
        return 'warning'
      case 'CRITICAL':
        return 'danger'
      default:
        return 'info'
    }
  })

  const loadLevelText = computed(() => {
    const level = toValue(loadLevel)
    if (!level) return '-'
    const mapping: Record<string, string> = {
      LOW: t('monitor.levelLow'),
      NORMAL: t('monitor.levelNormal'),
      HIGH: t('monitor.levelHigh'),
      CRITICAL: t('monitor.levelCritical'),
    }
    return mapping[level] ?? level
  })

  return {
    loadLevelColor,
    loadLevelText,
  }
}

/**
 * 熔断状态 Composable
 * @param circuitState - 熔断状态的响应式引用或 getter 函数
 */
export function useCircuitState(circuitState: MaybeRefOrGetter<CircuitState | string | undefined>) {
  const circuitStateColor = computed(() => {
    const state = toValue(circuitState)
    switch (state) {
      case 'CLOSED':
        return 'success'
      case 'OPEN':
        return 'danger'
      case 'HALF_OPEN':
        return 'warning'
      default:
        return 'info'
    }
  })

  return {
    circuitStateColor,
  }
}
