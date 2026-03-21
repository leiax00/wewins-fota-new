import type { JsonFieldDefinition } from '@/components/json-field/types/json-field'

/**
 * 格式化文件大小
 */
export function formatFileSize(bytes?: number): string {
  if (!bytes) return '-'
  const kb = bytes / 1024
  const mb = kb / 1024
  if (mb >= 1) return `${mb.toFixed(2)} MB`
  if (kb >= 1) return `${kb.toFixed(2)} KB`
  return `${bytes} B`
}

/**
 * 解析 JSON 字符串为对象
 */
export function parseJsonObject(jsonStr?: string | null): Record<string, unknown> | null {
  if (!jsonStr) return null
  try {
    return JSON.parse(jsonStr) as Record<string, unknown>
  } catch {
    return null
  }
}

/**
 * 检查是否为 i18n 字段格式
 * 判断值是否是语言代码映射对象（所有 key 都是语言代码格式）
 */
export function isI18nField(value: unknown): value is Record<string, string> {
  if (!value || typeof value !== 'object') return false
  const keys = Object.keys(value)
  if (keys.length === 0) return false
  // 检查是否所有 key 都是语言代码格式 (如 zh-CN, en-US)
  const localePattern = /^[a-z]{2}(-[A-Z]{2})?$/
  return keys.every(key => localePattern.test(key))
}

/**
 * 获取 i18n 字段的语言标签
 */
export function getLocaleLabel(locale: string): string {
  const localeMap: Record<string, string> = {
    'zh-CN': '简体中文',
    'en-US': 'English',
    'ja-JP': '日本語',
    'ko-KR': '한국어',
    'de-DE': 'Deutsch',
    'fr-FR': 'Français',
    'es-ES': 'Español',
  }
  return localeMap[locale] || locale
}

/**
 * 获取包状态标签类型
 */
export function getPackageStatusTagType(status?: string): 'success' | 'info' | 'warning' {
  if (status === 'READY') return 'success'
  if (status === 'NONE') return 'info'
  return 'warning'
}

/**
 * 根据 schema 获取字段的显示标签
 */
export function getFieldLabel(schema: JsonFieldDefinition[], key: string, t?: (key: string) => string): string {
  const field = schema.find(f => f.key === key)
  if (field?.i18nKey && t) {
    return t(field.i18nKey)
  }
  return field?.label || key
}

/**
 * 判断字段是否需要独占一行（i18n 或 textarea 类型）
 */
export function isBlockField(schema: JsonFieldDefinition[], key: string, value: unknown): boolean {
  // i18n 字段（值是语言代码映射对象）
  if (isI18nField(value)) return true

  // 查找 schema 中的字段定义
  const field = schema.find(f => f.key === key)
  if (field?.config?.schema?.type === 'textarea') return true

  return false
}

/**
 * 检查 meta 中是否有普通字段（非 i18n、非 textarea）
 */
export function hasNormalMetaFields(meta: string | null | undefined, metaSchema: JsonFieldDefinition[]): boolean {
  const parsed = parseJsonObject(meta)
  if (!parsed) return false

  for (const key in parsed) {
    const value = parsed[key]
    if (!isI18nField(value) && !isBlockField(metaSchema, key, value)) {
      return true
    }
  }
  return false
}
