<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { get } from '@/api/request'
import type {
  I18nFieldValue,
  JsonFieldDefinition,
} from '@/components/json-field/types/json-field'

interface Props {
  field: JsonFieldDefinition
  modelValue?: I18nFieldValue
  disabled?: boolean
}

interface DictItemResp {
  id: number
  label: string
  value: string
  i18nKey?: string
  status?: string
}

interface LanguageOption {
  label: string
  value: string
  i18nKey?: string
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: I18nFieldValue): void
}>()

const { t, te } = useI18n()

const DRAFT_LOCALE_PREFIX = '__draft_locale__:'

// 基础 BCP-47 校验（再结合 Intl.Locale 做二次校验）
const BCP47_REGEX =
  /^[A-Za-z]{2,3}(?:-[A-Za-z]{4})?(?:-(?:[A-Za-z]{2}|\d{3}))?(?:-(?:[A-Za-z0-9]{5,8}|\d[A-Za-z0-9]{3}))*$/

const localValue = ref<I18nFieldValue>({})
const localeOptions = ref<LanguageOption[]>([])
const loadingLocales = ref(false)
const localeErrors = ref<Record<string, string>>({})

const tr = (key: string, fallback: string, params?: Record<string, unknown>) => {
  return te(key) ? t(key, params || {}) : fallback
}

const label = computed(() => {
  if (props.field.i18nKey && te(props.field.i18nKey)) {
    return t(props.field.i18nKey)
  }
  return props.field.label
})

const placeholder = computed(() => props.field.config.schema.placeholder || '')

const helpText = computed(() => props.field.config.schema.help || '')

const allowCustomLocale = computed(
  () => props.field.config.schema.i18nConfig?.allowCustomLocale !== false
)

const minLocales = computed(
  () => Math.max(0, props.field.config.schema.i18nConfig?.minLocales ?? 0)
)

const isDraftLocaleKey = (locale: string) => locale.startsWith(DRAFT_LOCALE_PREFIX)

const localeEntries = computed(() => Object.entries(localValue.value))

const localeCountText = computed(() =>
  tr('jsonField.i18nField.localeCount', '{count} locales', {
    count: localeEntries.value.length,
  })
)

const normalizeLocaleCode = (locale: string): string => {
  const trimmed = locale.trim()
  if (!trimmed) return ''
  try {
    return new Intl.Locale(trimmed).toString()
  } catch {
    return trimmed
  }
}

const isValidLocaleCode = (locale: string): boolean => {
  const normalized = normalizeLocaleCode(locale)
  if (!normalized || !BCP47_REGEX.test(normalized)) return false
  try {
    // eslint-disable-next-line no-new
    new Intl.Locale(normalized)
    return true
  } catch {
    return false
  }
}

const canonicalLocaleSet = computed(() => {
  const set = new Set<string>()
  for (const code of Object.keys(localValue.value)) {
    if (isDraftLocaleKey(code)) continue
    set.add(normalizeLocaleCode(code).toLowerCase())
  }
  return set
})

const languageLabelMap = computed(() => {
  const map = new Map<string, string>()
  for (const opt of localeOptions.value) {
    const key = normalizeLocaleCode(opt.value).toLowerCase()
    const display = opt.i18nKey && te(opt.i18nKey) ? t(opt.i18nKey) : opt.label
    map.set(key, display)
  }
  return map
})

const getDraftLocaleKey = () =>
  `${DRAFT_LOCALE_PREFIX}${Date.now()}_${Math.random().toString(36).slice(2, 8)}`

const getValidValueSnapshot = (value: I18nFieldValue): I18nFieldValue => {
  const next: I18nFieldValue = {}
  Object.entries(value || {}).forEach(([k, v]) => {
    if (isDraftLocaleKey(k)) return
    if (typeof v === 'string') {
      next[k] = v
    } else if (v != null) {
      next[k] = String(v)
    }
  })
  return next
}

const syncFromProps = () => {
  const next = getValidValueSnapshot(props.modelValue || {})
  const current = getValidValueSnapshot(localValue.value)
  if (JSON.stringify(next) === JSON.stringify(current)) {
    return
  }
  localValue.value = next
  localeErrors.value = {}
}

watch(
  () => props.modelValue,
  () => {
    syncFromProps()
  },
  { immediate: true, deep: true }
)

const emitChange = () => {
  emit('update:modelValue', { ...getValidValueSnapshot(localValue.value) })
}

const loadSupportedLanguages = async () => {
  loadingLocales.value = true
  try {
    const rows = await get<DictItemResp[]>(
      '/sys/dict-types/code/supported_languages/items'
    )
    localeOptions.value = (rows || [])
      .filter((item) => (item.status || 'active').toLowerCase() === 'active')
      .map((item) => ({
        label: item.label || item.value,
        value: item.value,
        i18nKey: item.i18nKey,
      }))
  } catch {
    localeOptions.value = []
    ElMessage.warning(
      tr(
        'jsonField.i18nField.loadLocaleFailed',
        'Failed to load supported languages, custom input is still available.'
      )
    )
  } finally {
    loadingLocales.value = false
  }
}

onMounted(() => {
  void loadSupportedLanguages()
})

const resolveDisplayLocale = (localeCode: string): string => {
  if (isDraftLocaleKey(localeCode)) {
    return tr('jsonField.i18nField.localePlaceholder', 'Select language')
  }
  const key = normalizeLocaleCode(localeCode).toLowerCase()
  return languageLabelMap.value.get(key) || localeCode
}

const clearLocaleError = (localeCode: string) => {
  if (!localeErrors.value[localeCode]) return
  const next = { ...localeErrors.value }
  delete next[localeCode]
  localeErrors.value = next
}

const setLocaleError = (localeCode: string, message: string) => {
  localeErrors.value = {
    ...localeErrors.value,
    [localeCode]: message,
  }
}

const localeSelectOptions = (currentLocale: string) => {
  const currentKey = normalizeLocaleCode(currentLocale).toLowerCase()
  return localeOptions.value.filter((opt) => {
    const optionKey = normalizeLocaleCode(opt.value).toLowerCase()
    return optionKey === currentKey || !canonicalLocaleSet.value.has(optionKey)
  })
}

const addLocaleItem = () => {
  if (props.disabled) return
  localValue.value = {
    ...localValue.value,
    [getDraftLocaleKey()]: '',
  }
}

const onLocaleChange = (previousLocaleCode: string, rawLocaleCode: string) => {
  if (props.disabled) return

  clearLocaleError(previousLocaleCode)

  const text = localValue.value[previousLocaleCode] ?? ''
  const nextLocaleCode = String(rawLocaleCode || '').trim()
  if (!nextLocaleCode) return

  const normalized = normalizeLocaleCode(nextLocaleCode)
  if (!normalized || !isValidLocaleCode(normalized)) {
    setLocaleError(
      previousLocaleCode,
      tr(
        'jsonField.i18nField.errorInvalidLocale',
        'Invalid locale code. Please use BCP-47 format (e.g. zh-CN, en-US).'
      )
    )
    return
  }

  const normalizedKey = normalized.toLowerCase()
  const previousKey = isDraftLocaleKey(previousLocaleCode)
    ? ''
    : normalizeLocaleCode(previousLocaleCode).toLowerCase()

  if (normalizedKey !== previousKey && canonicalLocaleSet.value.has(normalizedKey)) {
    setLocaleError(
      previousLocaleCode,
      tr(
        'jsonField.i18nField.errorDuplicateLocale',
        'This language has already been added.'
      )
    )
    return
  }

  if (!allowCustomLocale.value) {
    const exists = localeOptions.value.some(
      (opt) => normalizeLocaleCode(opt.value).toLowerCase() === normalizedKey
    )
    if (!exists) {
      setLocaleError(
        previousLocaleCode,
        tr(
          'jsonField.i18nField.customLocaleDisabled',
          'Custom locale is disabled for this field.'
        )
      )
      return
    }
  }

  const next = { ...localValue.value }
  delete next[previousLocaleCode]
  next[normalized] = text
  localValue.value = next
  emitChange()
}

const onUpdateText = (localeCode: string, value: string) => {
  localValue.value = {
    ...localValue.value,
    [localeCode]: value,
  }
  emitChange()
}

const canRemoveLocale = computed(() => localeEntries.value.length > minLocales.value)

const onRemoveLocale = (localeCode: string) => {
  if (props.disabled) return

  if (!canRemoveLocale.value) {
    setLocaleError(
      localeCode,
      tr(
        'jsonField.i18nField.errorMinLocales',
        'At least {min} language entries are required.',
        { min: minLocales.value }
      )
    )
    return
  }

  const next = { ...localValue.value }
  delete next[localeCode]
  localValue.value = next
  clearLocaleError(localeCode)
  emitChange()
}
</script>

<template>
  <div class="json-field-item i18n-field">
    <div class="i18n-field__header">
      <div class="i18n-field__header-left">
        <el-tag
          size="small"
          type="info"
          effect="plain"
        >
          {{ label }}
        </el-tag>
        <span class="i18n-field__count">{{ localeCountText }}</span>
      </div>
    </div>

    <div class="i18n-field__panel">
      <div
        v-if="localeEntries.length === 0"
        class="i18n-field__empty"
      >
        {{ tr('jsonField.i18nField.empty', 'No language entries yet.') }}
      </div>

      <div class="i18n-field__list">
        <div
          v-for="[localeCode, text] in localeEntries"
          :key="localeCode"
          class="i18n-locale-card"
        >
          <div class="i18n-locale-card__head">
            <div class="i18n-locale-card__meta">
              <el-select
                :model-value="isDraftLocaleKey(localeCode) ? '' : localeCode"
                class="i18n-locale-card__select"
                filterable
                :allow-create="allowCustomLocale"
                :default-first-option="allowCustomLocale"
                :reserve-keyword="false"
                :loading="loadingLocales"
                :disabled="disabled"
                :placeholder="tr('jsonField.i18nField.localePlaceholder', 'Select language')"
                @update:model-value="(value: string) => onLocaleChange(localeCode, value)"
              >
                <el-option
                  v-for="opt in localeSelectOptions(localeCode)"
                  :key="opt.value"
                  :label="opt.i18nKey && te(opt.i18nKey) ? t(opt.i18nKey) : opt.label"
                  :value="opt.value"
                />
              </el-select>
              <span
                v-if="!isDraftLocaleKey(localeCode)"
                class="i18n-locale-card__code"
              >
                {{ resolveDisplayLocale(localeCode) }}
              </span>
            </div>

            <el-button
              link
              size="small"
              class="ui-action-danger i18n-locale-card__delete"
              :disabled="disabled || !canRemoveLocale"
              @click="onRemoveLocale(localeCode)"
            >
              <el-icon><Close /></el-icon>
            </el-button>
          </div>

          <el-input
            class="i18n-locale-card__textarea"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 6 }"
            :placeholder="placeholder"
            :model-value="text"
            :disabled="disabled"
            @update:model-value="(value: string) => onUpdateText(localeCode, value)"
          />

          <p
            v-if="localeErrors[localeCode]"
            class="i18n-locale-card__error"
          >
            {{ localeErrors[localeCode] }}
          </p>
        </div>
      </div>

      <div class="i18n-add-panel">
        <div class="i18n-add-panel__body">
          <span class="i18n-add-panel__text">
            {{ tr('jsonField.i18nField.addLocaleInline', 'Add a new item, then choose language and input text inside the item.') }}
          </span>
          <div class="i18n-add-panel__actions">
            <el-button
              class="i18n-add-panel__button"
              type="primary"
              plain
              :disabled="disabled || (!allowCustomLocale && localeSelectOptions('').length === 0)"
              @click="addLocaleItem"
            >
              {{ tr('jsonField.i18nField.addLocale', 'Add') }}
            </el-button>
          </div>
        </div>
      </div>

      <p class="i18n-field__hint">
        {{ tr('jsonField.i18nField.localeFormatHint', 'Locale format: BCP-47 (e.g. zh-CN, en-US).') }}
      </p>
      <p
        v-if="helpText"
        class="i18n-field__help"
      >
        {{ helpText }}
      </p>
    </div>
  </div>
</template>

<style scoped>
.i18n-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.i18n-field__header {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  min-height: 24px;
}

.i18n-field__header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.i18n-field__count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.i18n-field__panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.i18n-field__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.i18n-locale-card {
  position: relative;
  padding: 8px 10px;
  background-color: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  transition: all 0.2s ease;
}

.i18n-locale-card:hover {
  background-color: var(--el-fill-color);
  border-color: var(--el-border-color);
}

.i18n-locale-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  margin-bottom: 6px;
}

.i18n-locale-card__meta {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.i18n-locale-card__select {
  width: min(100%, 240px);
}

.i18n-locale-card__code {
  font-size: 12px;
  font-family: 'Monaco', 'Courier New', monospace;
  color: var(--el-text-color-regular);
  line-height: 1.1;
  word-break: break-all;
}

.i18n-locale-card__delete {
  flex-shrink: 0;
  margin-top: 0;
}

.i18n-locale-card__textarea {
  width: 100%;
}

.i18n-locale-card__error {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-color-danger);
}

.i18n-add-panel {
  padding: 8px 10px;
  background: var(--el-fill-color-lighter);
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
}

.i18n-add-panel__body {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.i18n-add-panel__text {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.i18n-add-panel__actions {
  margin-left: auto;
  display: flex;
}

:deep(.i18n-add-panel__button.el-button--primary.is-plain) {
  background-color: color-mix(in srgb, var(--el-color-primary) 14%, var(--el-bg-color-overlay));
  border-color: color-mix(in srgb, var(--el-color-primary) 38%, var(--el-border-color));
  color: var(--el-color-primary);
}

:deep(.i18n-add-panel__button.el-button--primary.is-plain:hover),
:deep(.i18n-add-panel__button.el-button--primary.is-plain:focus-visible) {
  background-color: color-mix(in srgb, var(--el-color-primary) 20%, var(--el-bg-color-overlay));
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

:deep(.i18n-add-panel__button.el-button--primary.is-plain:active) {
  background-color: color-mix(in srgb, var(--el-color-primary) 24%, var(--el-bg-color-overlay));
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

:deep(.i18n-add-panel__button.el-button--primary.is-plain.is-disabled) {
  background-color: var(--el-fill-color-light);
  border-color: var(--el-border-color-lighter);
  color: var(--el-text-color-placeholder);
}

.i18n-field__empty {
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.i18n-field__hint,
.i18n-field__help {
  margin: 0;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}

@media (max-width: 900px) {
  .i18n-add-panel__actions {
    margin-left: 0;
  }
}
</style>
