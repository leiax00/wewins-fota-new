import { createI18n } from 'vue-i18n'
import { ref } from 'vue'
import zhCN from './zh-CN'
import enUS from './en-US'
import { safeStorage } from '@/utils/storage'

const messages = {
  'zh-CN': zhCN,
  'en-US': enUS,
}

const SUPPORTED_LOCALES = ['zh-CN', 'en-US'] as const

export type SupportedLocale = typeof SUPPORTED_LOCALES[number]
export type LocalePreference = SupportedLocale | 'system'

const normalizeLocale = (input?: string | null): SupportedLocale => {
  const value = (input || '').toLowerCase().replace('_', '-')
  if (value.startsWith('zh')) return 'zh-CN'
  return 'en-US'
}

const resolveLocale = (preference: LocalePreference): SupportedLocale => {
  if (preference !== 'system') return preference
  if (typeof navigator === 'undefined') return 'en-US'
  return normalizeLocale(navigator.language)
}

const readPreference = (): LocalePreference => {
  const saved = safeStorage.getItem('locale')
  if (saved === 'system') return 'system'
  if (saved === 'zh-CN' || saved === 'en-US') return saved
  return 'system'
}

export const localePreference = ref<LocalePreference>(readPreference())
const initialLocale = resolveLocale(localePreference.value)

const i18n = createI18n({
  legacy: false,
  locale: initialLocale,
  fallbackLocale: 'en-US',
  messages,
})

export default i18n

export const availableLocales = [
  { value: 'system', labelKey: 'locale.system' },
  { value: 'zh-CN', label: '中文' },
  { value: 'en-US', label: 'English' },
] as const

const applyLocalePreference = (preference: LocalePreference) => {
  const resolved = resolveLocale(preference)
  i18n.global.locale.value = resolved
  document.querySelector('html')?.setAttribute('lang', resolved)
}

if (typeof document !== 'undefined') {
  document.querySelector('html')?.setAttribute('lang', initialLocale)
}

export const setLocale = (preference: LocalePreference) => {
  localePreference.value = preference
  safeStorage.setItem('locale', preference)
  applyLocalePreference(preference)
}

if (typeof window !== 'undefined') {
  window.addEventListener('languagechange', () => {
    if (localePreference.value !== 'system') return
    applyLocalePreference('system')
  })
}
