/**
 * JSON 字段数据转换工具
 *
 * 负责 JSON 字符串与对象之间的转换，以及已知字段和未知字段的分离
 */

import type { JsonFieldDefinition, JsonObjectValue } from '@/components/json-field/types/json-field'

/**
 * 解析 JSON 字符串为对象
 *
 * @param input JSON 字符串
 * @returns 解析结果（ok=true 表示成功，data 为解析后的对象；ok=false 表示失败，error 为错误信息）
 */
export const safeParseJsonObject = (
  input?: string
): { ok: true; data: JsonObjectValue } | { ok: false; error: string } => {
  const raw = input?.trim()
  if (!raw) return { ok: true, data: {} }

  try {
    const parsed = JSON.parse(raw)

    // 必须是对象类型（不能是数组或 null）
    if (parsed === null || Array.isArray(parsed) || typeof parsed !== 'object') {
      return { ok: false, error: 'jsonField.errorMustObject' }
    }

    return { ok: true, data: parsed as JsonObjectValue }
  } catch {
    return { ok: false, error: 'jsonField.errorInvalidJson' }
  }
}

/**
 * 将对象序列化为 JSON 字符串
 *
 * @param value JSON 对象
 * @param pretty 是否格式化（美化）
 * @returns JSON 字符串
 */
export const stringifyJsonObject = (value: JsonObjectValue, pretty = false): string => {
  if (pretty) return JSON.stringify(value, null, 2)
  return JSON.stringify(value)
}

/**
 * 将对象中的字段分为已知字段和未知字段
 *
 * @param value 完整的 JSON 对象
 * @param fields 字段定义列表
 * @returns 分离后的已知字段和未知字段
 */
export const splitKnownAndUnknown = (
  value: JsonObjectValue,
  fields: JsonFieldDefinition[]
): { known: JsonObjectValue; unknown: JsonObjectValue } => {
  const fieldKeys = new Set(fields.map((item) => item.key))
  const known: JsonObjectValue = {}
  const unknown: JsonObjectValue = {}

  Object.entries(value).forEach(([key, fieldValue]) => {
    if (fieldKeys.has(key)) {
      known[key] = fieldValue
    } else {
      unknown[key] = fieldValue
    }
  })

  return { known, unknown }
}

/**
 * 应用字段默认值
 *
 * @param known 已知字段对象
 * @param fields 字段定义列表
 * @returns 应用默认值后的对象
 */
export const applyDefaults = (known: JsonObjectValue, fields: JsonFieldDefinition[]): JsonObjectValue => {
  const output: JsonObjectValue = { ...known }

  fields.forEach((field) => {
    const currentValue = output[field.key]
    const defaultValue = field.config.schema.defaultValue

    // 当字段值为空且存在默认值时，应用默认值
    if (
      defaultValue !== undefined &&
      (currentValue === undefined || currentValue === null || currentValue === '')
    ) {
      output[field.key] = defaultValue
    }
  })

  return output
}

/**
 * 合并已知字段和未知字段
 *
 * @param known 已知字段对象
 * @param unknown 未知字段对象
 * @returns 合并后的对象（已知字段优先）
 */
export const mergeKnownAndUnknown = (
  known: JsonObjectValue,
  unknown: JsonObjectValue
): JsonObjectValue => {
  return {
    ...unknown, // 先放未知字段
    ...known,   // 再放已知字段（覆盖同名的未知字段）
  }
}
