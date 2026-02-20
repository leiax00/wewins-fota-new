import { safeStorage } from '@/utils/storage'

const TOKEN_KEY = 'token'

const safeSessionStorage = {
  getItem: (key: string): string | null => {
    try {
      return sessionStorage.getItem(key)
    } catch {
      return null
    }
  },
  setItem: (key: string, value: string): boolean => {
    try {
      sessionStorage.setItem(key, value)
      return true
    } catch {
      return false
    }
  },
  removeItem: (key: string): boolean => {
    try {
      sessionStorage.removeItem(key)
      return true
    } catch {
      return false
    }
  },
}

export const getToken = (): string => {
  return safeStorage.getItem(TOKEN_KEY) || safeSessionStorage.getItem(TOKEN_KEY) || ''
}

export const setToken = (token: string, remember = false): void => {
  if (remember) {
    safeSessionStorage.removeItem(TOKEN_KEY)
    safeStorage.setItem(TOKEN_KEY, token)
    return
  }

  safeStorage.removeItem(TOKEN_KEY)
  safeSessionStorage.setItem(TOKEN_KEY, token)
}

export const clearToken = (): void => {
  safeStorage.removeItem(TOKEN_KEY)
  safeSessionStorage.removeItem(TOKEN_KEY)
}
