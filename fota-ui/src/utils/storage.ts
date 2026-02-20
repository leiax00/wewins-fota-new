/**
 * 安全的 localStorage 工具函数
 * 处理 Safari 隐私模式或禁用 localStorage 时的异常情况
 */
export const safeStorage = {
  /**
   * 从 localStorage 获取值
   */
  getItem: (key: string): string | null => {
    try {
      return localStorage.getItem(key)
    } catch (error) {
      console.warn('localStorage is not available:', error)
      return null
    }
  },

  /**
   * 向 localStorage 设置值
   */
  setItem: (key: string, value: string): boolean => {
    try {
      localStorage.setItem(key, value)
      return true
    } catch (error) {
      console.warn('Failed to set localStorage item:', error)
      return false
    }
  },

  /**
   * 从 localStorage 移除值
   */
  removeItem: (key: string): boolean => {
    try {
      localStorage.removeItem(key)
      return true
    } catch (error) {
      console.warn('Failed to remove localStorage item:', error)
      return false
    }
  },

  /**
   * 清空 localStorage
   */
  clear: (): boolean => {
    try {
      localStorage.clear()
      return true
    } catch (error) {
      console.warn('Failed to clear localStorage:', error)
      return false
    }
  },
}
