/**
 * JSON 字段 Schema 加载器
 *
 * 负责从字典 API 加载字段定义，并提供缓存机制
 */

import { get } from '@/api/request'
import {
  isJsonFieldConfig,
  type JsonFieldConfig,
  type JsonFieldDefinition,
} from '@/components/json-field/types/json-field'

/**
 * 字典项响应数据结构
 */
interface DictItemResp {
  id: number
  label: string
  value: string
  i18nKey?: string
  sortOrder?: number
  status?: string
  extra?: unknown
}

/**
 * 缓存条目
 */
interface CacheEntry {
  expireAt: number
  value: JsonFieldDefinition[]
}

/**
 * Schema 缓存（5分钟 TTL）
 */
const CACHE_TTL_MS = 5 * 60 * 1000
const schemaCache = new Map<string, CacheEntry>()

/**
 * 将字典项数据转换为字段定义
 */
const normalizeDefinition = (item: DictItemResp): JsonFieldDefinition | null => {
  // 只处理启用的字段（不区分大小写）
  const status = (item.status || 'active').toLowerCase()
  if (status !== 'active') return null

  // 字段 key 不能为空
  if (!item.value || !item.value.trim()) return null

  // 校验 extra 是否为合法的 JsonFieldConfig
  if (!isJsonFieldConfig(item.extra)) return null

  const config = item.extra as JsonFieldConfig

  return {
    id: item.id,
    key: item.value.trim(),
    label: item.label?.trim() || item.value.trim(),
    i18nKey: item.i18nKey,
    sortOrder: item.sortOrder ?? 0,
    config,
  }
}

/**
 * 加载 JSON 字段 Schema
 *
 * @param dictTypeCode 字典类型编码（如 'json_schema.device_tags'）
 * @param forceRefresh 是否强制刷新缓存
 * @returns 字段定义列表（按 sortOrder 排序）
 */
export const loadJsonFieldSchema = async (
  dictTypeCode: string,
  forceRefresh = false
): Promise<JsonFieldDefinition[]> => {
  const code = dictTypeCode.trim()
  if (!code) return []

  const now = Date.now()

  // 检查缓存
  const cached = schemaCache.get(code)
  if (!forceRefresh && cached && cached.expireAt > now) {
    return cached.value
  }

  try {
    // 调用后端 API 加载字典项（前端会自动添加 /api 前缀）
    const rows = await get<DictItemResp[]>(`/sys/dict-types/code/${encodeURIComponent(code)}/items`)

    // 转换并过滤
    const definitions = (rows || [])
      .map(normalizeDefinition)
      .filter((item): item is JsonFieldDefinition => item !== null)
      .sort((a, b) => {
        // 先按 sortOrder 排序
        if (a.sortOrder !== b.sortOrder) return a.sortOrder - b.sortOrder
        // sortOrder 相同时按 id 排序
        return a.id - b.id
      })

    // 更新缓存
    schemaCache.set(code, {
      expireAt: now + CACHE_TTL_MS,
      value: definitions,
    })

    return definitions
  } catch (error) {
    console.error(`[JsonFieldEditor] 加载 Schema 失败: code=${code}`, error)
    throw error
  }
}

/**
 * 清除 Schema 缓存
 *
 * @param dictTypeCode 字典类型编码（不传则清除全部缓存）
 */
export const clearJsonFieldSchemaCache = (dictTypeCode?: string): void => {
  if (!dictTypeCode) {
    schemaCache.clear()
    return
  }
  schemaCache.delete(dictTypeCode.trim())
}
