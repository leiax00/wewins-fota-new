/**
 * JSON 字段校验逻辑
 *
 * 负责根据 Schema 定义校验 JSON 对象的合法性
 */

import type { JsonFieldDefinition, JsonObjectValue } from '@/components/json-field/types/json-field'
import { isPrimitive } from '@/components/json-field/types/json-field'

/**
 * 校验 JSON 对象的基本类型
 *
 * @param value 待校验的值
 * @returns 错误信息（null 表示校验通过）
 */
export const validateJsonObject = (value: unknown): string | null => {
  if (value === null || value === undefined) return null

  if (Array.isArray(value)) {
    return 'jsonField.errorMustObject'
  }

  if (typeof value !== 'object') {
    return 'jsonField.errorMustObject'
  }

  return null
}

/**
 * 根据 Schema 校验 JSON 对象
 *
 * @param payload 待校验的 JSON 对象
 * @param fields 字段定义列表
 * @param strict 是否严格模式（true 时禁止未知字段）
 * @returns 错误信息列表（空数组表示校验通过）
 */
export const validateBySchema = (
  payload: JsonObjectValue,
  fields: JsonFieldDefinition[],
  strict = false
): string[] => {
  const errors: string[] = []

  // 构建字段映射，方便快速查找
  const fieldMap = new Map(fields.map((item) => [item.key, item]))

  // 遍历所有字段定义进行校验
  for (const field of fields) {
    const value = payload[field.key]
    const schema = field.config.schema

    // 处理空值
    if (value === undefined || value === null || value === '') {
      if (schema.required) {
        errors.push(`jsonField.errorRequired::${field.key}`)
      }
      continue
    }

    // 根据字段类型进行校验
    switch (schema.type) {
      case 'string':
      case 'textarea': {
        if (typeof value !== 'string') {
          errors.push(`jsonField.errorTypeString::${field.key}`)
          break
        }

        // 长度校验
        if (schema.validator?.minLength !== undefined && value.length < schema.validator.minLength) {
          errors.push(`jsonField.errorMinLength::${field.key}::${schema.validator.minLength}`)
        }
        if (schema.validator?.maxLength !== undefined && value.length > schema.validator.maxLength) {
          errors.push(`jsonField.errorMaxLength::${field.key}::${schema.validator.maxLength}`)
        }

        // 正则校验
        if (schema.validator?.pattern) {
          try {
            const reg = new RegExp(schema.validator.pattern)
            if (!reg.test(value)) {
              errors.push(`jsonField.errorPattern::${field.key}`)
            }
          } catch (e) {
            console.error(`[JsonFieldEditor] 正则表达式错误: field=${field.key}, pattern=${schema.validator.pattern}`, e)
          }
        }
        break
      }

      case 'number': {
        if (typeof value !== 'number' || Number.isNaN(value)) {
          errors.push(`jsonField.errorTypeNumber::${field.key}`)
          break
        }

        // 范围校验
        if (schema.validator?.min !== undefined && value < schema.validator.min) {
          errors.push(`jsonField.errorMin::${field.key}::${schema.validator.min}`)
        }
        if (schema.validator?.max !== undefined && value > schema.validator.max) {
          errors.push(`jsonField.errorMax::${field.key}::${schema.validator.max}`)
        }
        break
      }

      case 'boolean': {
        if (typeof value !== 'boolean') {
          errors.push(`jsonField.errorTypeBoolean::${field.key}`)
        }
        break
      }

      case 'select': {
        const options = schema.options || []
        const optionValues = new Set(options.map((opt) => opt.value))

        if (!optionValues.has(value as string | number)) {
          errors.push(`jsonField.errorInvalidOption::${field.key}`)
        }
        break
      }

      case 'textarea': {
        if (typeof value !== 'string') {
          errors.push(`jsonField.errorTypeString::${field.key}`)
          break
        }

        // 长度校验
        if (schema.validator?.minLength !== undefined && value.length < schema.validator.minLength) {
          errors.push(`jsonField.errorMinLength::${field.key}::${schema.validator.minLength}`)
        }
        if (schema.validator?.maxLength !== undefined && value.length > schema.validator.maxLength) {
          errors.push(`jsonField.errorMaxLength::${field.key}::${schema.validator.maxLength}`)
        }
        break
      }

      case 'i18n': {
        if (typeof value !== 'object' || value === null || Array.isArray(value)) {
          errors.push(`jsonField.errorMustObject::${field.key}`)
          break
        }

        // 校验每个语言条目的长度
        if (schema.validator?.i18nMaxLength) {
          for (const [locale, text] of Object.entries(value)) {
            if (typeof text === 'string' && text.length > schema.validator.i18nMaxLength) {
              errors.push(`jsonField.errorMaxLength::${field.key}.${locale}::${schema.validator.i18nMaxLength}`)
            }
          }
        }
        break
      }

      default:
        break
    }
  }

  // 未知字段校验
  if (strict) {
    // 严格模式：禁止所有未知字段
    for (const key of Object.keys(payload)) {
      if (!fieldMap.has(key)) {
        errors.push(`jsonField.errorUnknownField::${key}`)
      }
    }
  } else {
    // 宽松模式：未知字段只允许原始值（string/number/boolean）
    for (const [key, value] of Object.entries(payload)) {
      if (!fieldMap.has(key) && !isPrimitive(value)) {
        errors.push(`jsonField.errorUnknownValueNotPrimitive::${key}`)
      }
    }
  }

  return errors
}
