export type StatusTagType = 'success' | 'warning' | 'danger' | 'info'
export type DeviceStatus = 'ONLINE' | 'OFFLINE' | 'LOST' | 'online' | 'offline' | 'upgrading' | 'failed'
export type PolicyStatus = 'active' | 'paused' | 'expired' | 'failed'
export type UserStatus = 'active' | 'disabled' | 'locked'
export type RoleStatus = 'active' | 'disabled'

export const deviceStatusTypeMap: Record<string, StatusTagType> = {
  ONLINE: 'success',
  OFFLINE: 'info',
  LOST: 'danger',
  online: 'success',
  offline: 'warning',
  upgrading: 'warning',
  failed: 'danger',
}

export const policyStatusTypeMap: Record<string, StatusTagType> = {
  active: 'success',
  paused: 'warning',
  expired: 'info',
  ACTIVE: 'success',
  PAUSED: 'warning',
  EXPIRED: 'info',
  failed: 'danger',
  DRAFT: 'info',
  TESTING: 'warning',
  VERIFIED: 'info',
}

export const userStatusTypeMap: Record<string, StatusTagType> = {
  active: 'success',
  enabled: 'success',
  disabled: 'info',
  locked: 'danger',
}

export const roleStatusTypeMap: Record<string, StatusTagType> = {
  active: 'success',
  enabled: 'success',
  disabled: 'info',
}

export const resolveStatusType = (
  map: Record<string, StatusTagType>,
  status?: string
): StatusTagType => {
  if (!status) return 'info'
  return map[status] || 'info'
}

export const statusLabelKeyMap: Record<string, string> = {
  ONLINE: 'status.online',
  OFFLINE: 'status.offline',
  LOST: 'status.lost',
  online: 'status.online',
  offline: 'status.offline',
  upgrading: 'status.upgrading',
  failed: 'status.failed',
  active: 'status.active',
  paused: 'status.paused',
  expired: 'status.expired',
  enabled: 'status.enabled',
  disabled: 'status.disabled',
  locked: 'status.locked',
  // 策略状态使用 policy 前缀
  DRAFT: 'policy.statusDraft',
  TESTING: 'policy.statusTesting',
  VERIFIED: 'policy.statusVerified',
  ACTIVE: 'policy.statusActive',
  PAUSED: 'status.paused',
  EXPIRED: 'status.expired',
  // 兼容旧的小写形式（如果需要）
  draft: 'policy.statusDraft',
  testing: 'policy.statusTesting',
  verified: 'policy.statusVerified',
}

export const resolveStatusLabelKey = (status?: string): string => {
  if (!status) return 'status.unknown'
  return statusLabelKeyMap[status] || 'status.unknown'
}
