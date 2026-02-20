import { defineStore } from 'pinia'
import { safeStorage } from '@/utils/storage'

export type ThemeMode = 'light' | 'dark' | 'system'

export const useThemeStore = defineStore('theme', {
  state: () => ({
    mode: (safeStorage.getItem('theme') || 'system') as ThemeMode,
  }),

  getters: {
    isDark: (state) => {
      if (state.mode === 'system') {
        return window.matchMedia('(prefers-color-scheme: dark)').matches
      }
      return state.mode === 'dark'
    },
    currentMode: (state) => state.mode,
  },

  actions: {
    setMode(mode: ThemeMode) {
      this.mode = mode
      safeStorage.setItem('theme', mode)
      this.applyTheme()
    },

    applyTheme() {
      const isDark = this.isDark
      document.documentElement.classList.toggle('dark', isDark)
    },

    initTheme() {
      this.applyTheme()
      window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
        if (this.mode === 'system') {
          this.applyTheme()
        }
      })
    },
  },
})
