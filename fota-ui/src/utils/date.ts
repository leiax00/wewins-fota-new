/**
 * 日期时间工具函数
 * <p>
 * 处理前后端时区转换和格式化，统一使用 yyyy-MM-dd HH:mm:ss 格式
 * </p>
 */

/**
 * 标准日期时间格式：yyyy-MM-dd HH:mm:ss
 */
export const DATETIME_FORMAT = 'yyyy-MM-dd HH:mm:ss'

/**
 * 短日期时间格式：yyyy-MM-dd HH:mm
 */
export const DATETIME_SHORT_FORMAT = 'yyyy-MM-dd HH:mm'

/**
 * 仅时间格式：HH:mm:ss
 */
export const TIME_FORMAT = 'HH:mm:ss'

/**
 * 仅日期格式：yyyy-MM-dd
 */
export const DATE_FORMAT = 'yyyy-MM-dd'

/**
 * 格式化日期时间为标准格式 (yyyy-MM-dd HH:mm:ss)
 * <p>
 * 将后端返回的 UTC 时间字符串转换为客户端本地时间并格式化
 * </p>
 *
 * @param isoString ISO8601 时间字符串（UTC）
 * @returns 格式化后的本地时间字符串，如果输入无效则返回 '-'
 */
export function formatDateTime(isoString: string | null | undefined): string {
  return formatLocalDateTime(isoString, DATETIME_FORMAT)
}

/**
 * 格式化日期时间为本地字符串
 * <p>
 * 将后端返回的 UTC 时间字符串转换为客户端本地时间并格式化
 * </p>
 *
 * @param isoString ISO8601 时间字符串（UTC）
 * @param format 格式模板，默认 'yyyy-MM-dd HH:mm:ss'
 * @returns 格式化后的本地时间字符串，如果输入无效则返回 '-'
 */
export function formatLocalDateTime(isoString: string | null | undefined, format = DATETIME_FORMAT): string {
  if (!isoString || isoString === '') {
    return '-'
  }

  try {
    const date = new Date(isoString)

    // 检查日期是否有效
    if (isNaN(date.getTime())) {
      return '-'
    }

    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    const hours = String(date.getHours()).padStart(2, '0')
    const minutes = String(date.getMinutes()).padStart(2, '0')
    const seconds = String(date.getSeconds()).padStart(2, '0')

    return format
      .replace('yyyy', String(year))
      .replace('MM', month)
      .replace('dd', day)
      .replace('HH', hours)
      .replace('mm', minutes)
      .replace('ss', seconds)
  } catch {
    return '-'
  }
}

/**
 * 格式化日期时间为短格式 (yyyy-MM-dd HH:mm)
 */
export function formatDateTimeShort(isoString: string | null | undefined): string {
  return formatLocalDateTime(isoString, DATETIME_SHORT_FORMAT)
}

/**
 * 格式化仅时间 (HH:mm:ss)
 */
export function formatTime(isoString: string | null | undefined): string {
  return formatLocalDateTime(isoString, TIME_FORMAT)
}

/**
 * 格式化仅日期 (yyyy-MM-dd)
 */
export function formatDate(isoString: string | null | undefined): string {
  return formatLocalDateTime(isoString, DATE_FORMAT)
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

