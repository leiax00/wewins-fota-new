/**
 * 日期时间工具函数
 * <p>
 * 处理前后端时区转换和格式化
 * </p>
 */

/**
 * 格式化日期时间为本地字符串
 * <p>
 * 将后端返回的 UTC 时间字符串转换为客户端本地时间并格式化
 * </p>
 *
 * @param isoString ISO8601 时间字符串（UTC）
 * @param format 格式模板，默认 'YYYY-MM-DD HH:mm:ss'
 * @returns 格式化后的本地时间字符串，如果输入无效则返回 '-'
 */
export function formatLocalDateTime(isoString: string | null | undefined, format = 'YYYY-MM-DD HH:mm:ss'): string {
  if (!isoString || isoString === '') {
    return '-'
  }

  try {
    const date = new Date(isoString)

    // 检查日期是否有效
    if (isNaN(date.getTime())) {
      return '-'
    }

    // 简单格式化实现
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    const hours = String(date.getHours()).padStart(2, '0')
    const minutes = String(date.getMinutes()).padStart(2, '0')
    const seconds = String(date.getSeconds()).padStart(2, '0')

    return format
      .replace('YYYY', String(year))
      .replace('MM', month)
      .replace('DD', day)
      .replace('HH', hours)
      .replace('mm', minutes)
      .replace('ss', seconds)
  } catch {
    return '-'
  }
}

/**
 * 将本地日期时间转换为 UTC ISO 字符串
 * <p>
 * 用于发送时间数据到后端
 * </p>
 *
 * @param localDate 本地日期对象或时间戳
 * @returns ISO8601 UTC 字符串
 */
export function toUTCISOString(localDate: Date | number): string {
  const date = localDate instanceof Date ? localDate : new Date(localDate)
  return date.toISOString()
}

/**
 * 获取客户端时区标识
 * <p>
 * 例如：Asia/Shanghai, America/New_York
 * </p>
 *
 * @returns 时区标识
 */
export function getClientTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone
}

/**
 * 获取客户端时区偏移（小时）
 * <p>
 * 例如：东八区返回 8，西五区返回 -5
 * </p>
 *
 * @returns 时区偏移小时数
 */
export function getClientTimeZoneOffset(): number {
  return -new Date().getTimezoneOffset() / 60
}
