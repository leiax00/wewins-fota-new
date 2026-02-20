import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN'
import enUS from './en-US'
import { safeStorage } from '@/utils/storage'

const messages = {
  'zh-CN': zhCN,
  'en-US': enUS,
}

const savedLocale = safeStorage.getItem('locale') || 'zh-CN'

const i18n = createI18n({
  legacy: false,
  locale: savedLocale,
  fallbackLocale: 'en-US',
  messages,
})

export default i18n

export const availableLocales = [
  { value: 'zh-CN', label: '中文' },
  { value: 'en-US', label: 'English' },
]

export const setLocale = (locale: string) => {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  i18n.global.locale.value = locale as any
  safeStorage.setItem('locale', locale)
  document.querySelector('html')?.setAttribute('lang', locale)
}
