/**
 * 监控页面格式化工具函数
 */

/**
 * 格式化百分比（输入为 0-1 之间的小数）
 * @param value - 百分比值（0.01 表示 1%）
 * @param decimals - 小数位数，默认 2
 */
export function formatPercent(value: number, decimals = 2): string {
  return `${(value * 100).toFixed(decimals)}%`
}

/**
 * 格式化百分比（输入已经是百分比，如 50 表示 50%）
 * @param value - 百分比值（50 表示 50%）
 * @param decimals - 小数位数，默认 1
 */
export function formatPercentDirect(value: number, decimals = 1): string {
  return `${value.toFixed(decimals)}%`
}

/**
 * 格式化 QPS
 */
export function formatQps(value: number): string {
  return value.toFixed(1)
}

/**
 * 格式化延迟
 */
export function formatLatency(value: number): string {
  return `${value.toFixed(0)} ms`
}

/**
 * 格式化字节/秒
 */
export function formatBytes(value: number): string {
  const bytes = value ?? 0
  if (bytes < 1024) return `${bytes.toFixed(0)} B/s`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB/s`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB/s`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(1)} GB/s`
}

/**
 * 格式化数字（添加千位分隔符）
 */
export function formatNumber(value: number): string {
  return value.toLocaleString()
}
