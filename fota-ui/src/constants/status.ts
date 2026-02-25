export type StatusTagType = 'success' | 'warning' | 'danger' | 'info'
export type DeviceStatus = 'online' | 'offline' | 'upgrading' | 'failed'
export type PolicyStatus = 'active' | 'paused' | 'expired' | 'failed'
export type UserStatus = 'active' | 'disabled' | 'locked'
export type RoleStatus = 'active' | 'disabled'

export const deviceStatusTypeMap: Record<string, StatusTagType> = {
  online: 'success',
  offline: 'info',
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
  online: 'status.online',
  offline: 'status.offline',
  upgrading: 'status.upgrading',
  failed: 'status.failed',
  active: 'status.active',
  paused: 'status.paused',
  expired: 'status.expired',
  ACTIVE: 'status.active',
  PAUSED: 'status.paused',
  EXPIRED: 'status.expired',
  enabled: 'status.enabled',
  disabled: 'status.disabled',
  locked: 'status.locked',
}

export const resolveStatusLabelKey = (status?: string): string => {
  if (!status) return 'status.unknown'
  return statusLabelKeyMap[status] || 'status.unknown'
}
