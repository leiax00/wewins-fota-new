/**
 * JSON 字段配置化类型定义
 *
 * 用于支持基于字典配置的动态 JSON 字段编辑功能
 */

/**
 * 支持的字段类型
 */
export type JsonFieldType = 'string' | 'textarea' | 'number' | 'boolean' | 'select' | 'i18n'

/**
 * i18n 字段的值类型（语言代码 -> 文本）
 */
export type I18nFieldValue = Record<string, string>

/**
 * 字段选项（用于 select 类型）
 */
export interface JsonFieldOption {
  label: string
  value: string | number
  i18nKey?: string
}

/**
 * 字段校验规则
 */
export interface JsonFieldValidator {
  /** 正则表达式（string 类型） */
  pattern?: string
  /** 最小值（number 类型） */
  min?: number
  /** 最大值（number 类型） */
  max?: number
  /** 最小长度（string 类型） */
  minLength?: number
  /** 最大长度（string 类型） */
  maxLength?: number
  /** 每个语言文本的最大长度（i18n 类型） */
  i18nMaxLength?: number
}

/**
 * 字段 Schema 定义
 */
export interface JsonFieldSchema {
  /** 字段类型 */
  type: JsonFieldType
  /** 是否必填 */
  required?: boolean
  /** 默认值 */
  defaultValue?: unknown
  /** 占位文本 */
  placeholder?: string
  /** 帮助提示 */
  help?: string
  /** 选项配置（select 类型） */
  options?: JsonFieldOption[]
  /** 校验规则 */
  validator?: JsonFieldValidator
  /** i18n 特殊配置 */
  i18nConfig?: {
    /** 是否允许自定义语言（不在字典中的） */
    allowCustomLocale?: boolean
    /** 最少语言数量 */
    minLocales?: number
    /** 默认语言列表 */
    defaultLocales?: string[]
  }
  /** textarea 特殊配置 */
  textareaConfig?: {
    /** 最小行数 */
    minRows?: number
    /** 最大行数 */
    maxRows?: number
  }
}

/**
 * 字段配置元数据（存储在字典项 extra 字段）
 */
export interface JsonFieldConfig {
  /** 固定值，用于识别 extra 的用途 */
  kind: 'json_field_definition'
  /** schema 版本，用于未来升级 */
  schemaVersion: 1
  /** 字段定义 */
  schema: JsonFieldSchema
}

/**
 * 字段定义（从字典加载并解析后的数据结构）
 */
export interface JsonFieldDefinition {
  /** 字典项 ID */
  id: number
  /** 字段 key（存储在 JSON 中的键名） */
  key: string
  /** 字段显示名称 */
  label: string
  /** 国际化键（可选） */
  i18nKey?: string
  /** 排序号 */
  sortOrder: number
  /** 字段配置 */
  config: JsonFieldConfig
}

/**
 * JSON 对象值类型
 */
export type JsonObjectValue = Record<string, unknown>

/**
 * 类型守卫：检查值是否为合法的 JsonFieldConfig
 */
export const isJsonFieldConfig = (value: unknown): value is JsonFieldConfig => {
  if (!value || typeof value !== 'object') return false
  const obj = value as Partial<JsonFieldConfig>

  // 检查 kind 和 schemaVersion
  if (obj.kind !== 'json_field_definition') return false
  if (obj.schemaVersion !== 1) return false

  // 检查 schema 是否存在且为对象
  if (!obj.schema || typeof obj.schema !== 'object') return false

  // 检查 type 是否为合法值
  const schema = obj.schema as JsonFieldSchema
  const validTypes = new Set<JsonFieldType>(['string', 'textarea', 'number', 'boolean', 'select', 'i18n'])
  if (!validTypes.has(schema.type)) return false

  return true
}

/**
 * 类型守卫：检查值是否为原始类型（string、number、boolean、null）
 */
export const isPrimitive = (value: unknown): boolean => {
  return value === null || ['string', 'number', 'boolean'].includes(typeof value)
}
