/**
 * 表单处理工具函数
 */

/**
 * 递归去除对象中所有字符串字段的前后空格
 * @param obj 要处理的对象
 * @returns 处理后的新对象（不修改原对象）
 */
export function trimFormValues<T extends Record<string, unknown>>(obj: T): T {
  const result: Record<string, unknown> = {}

  for (const [key, value] of Object.entries(obj)) {
    if (typeof value === 'string') {
      result[key] = value.trim()
    } else if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
      result[key] = trimFormValues(value as Record<string, unknown>)
    } else {
      result[key] = value
    }
  }

  return result as T
}
