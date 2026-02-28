/**
 * API 错误码常量
 * 统一管理系统中的错误码，避免硬编码
 */
export const ERROR_CODES = {
  // 成功
  SUCCESS: 0,
  SUCCESS_ALT: 200,

  // Token 相关
  TOKEN_INVALID: 40101,
  TOKEN_EXPIRED: 40102,

  // 其他常见错误
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  SERVER_ERROR: 500,

  // 网络错误（前端自定义）
  NETWORK_ERROR: 'networkError',
  UNKNOWN: 'unknown',
  TIMEOUT: 'timeout',
} as const

export type ErrorCode = (typeof ERROR_CODES)[keyof typeof ERROR_CODES]

/**
 * 判断是否为 Token 无效错误
 */
export const isTokenInvalidError = (code: number | string): boolean => {
  return code === ERROR_CODES.TOKEN_INVALID || code === ERROR_CODES.TOKEN_EXPIRED
}

/**
 * 判断是否为认证错误
 */
export const isAuthError = (code: number | string): boolean => {
  return (
    isTokenInvalidError(code) ||
    code === ERROR_CODES.UNAUTHORIZED
  )
}
