import { defineStore } from 'pinia'
import { safeStorage } from '@/utils/storage'

const TAB_STORAGE_KEY = 'app_tabs_v1'
const ACTIVE_TAB_STORAGE_KEY = 'app_active_tab_v1'

export interface AppTabItem {
  key: string
  path: string
  title: string
  affix?: boolean
  closable?: boolean
}

const readStoredTabs = (): AppTabItem[] => {
  const raw = safeStorage.getItem(TAB_STORAGE_KEY)
  if (!raw) return []

  try {
    const parsed = JSON.parse(raw) as AppTabItem[]
    if (!Array.isArray(parsed)) return []
    return parsed
      .filter((item) => item && item.key && item.path && item.title)
      .map((item) => ({
        key: item.key,
        path: item.path,
        title: item.title,
        affix: Boolean(item.affix),
        closable: item.closable !== false,
      }))
  } catch {
    return []
  }
}

export const useAppStore = defineStore('app', {
  state: () => ({
    tabsList: readStoredTabs(),
    activeTab: safeStorage.getItem(ACTIVE_TAB_STORAGE_KEY) || '',
  }),

  actions: {
    persistTabs() {
      safeStorage.setItem(TAB_STORAGE_KEY, JSON.stringify(this.tabsList))
      safeStorage.setItem(ACTIVE_TAB_STORAGE_KEY, this.activeTab)
    },

    setActiveTab(key: string) {
      this.activeTab = key
      safeStorage.setItem(ACTIVE_TAB_STORAGE_KEY, key)
    },

    upsertTab(tab: AppTabItem) {
      const index = this.tabsList.findIndex((item) => item.key === tab.key)
      if (index === -1) {
        this.tabsList.push(tab)
      } else {
        this.tabsList[index] = {
          ...this.tabsList[index],
          ...tab,
          affix: this.tabsList[index].affix || tab.affix,
          closable: tab.closable,
        }
      }
      this.activeTab = tab.key
      this.persistTabs()
    },

    ensureAffixTabs(tabs: AppTabItem[]) {
      for (const tab of tabs) {
        if (!this.tabsList.find((item) => item.key === tab.key)) {
          this.tabsList.unshift(tab)
        }
      }
      this.persistTabs()
    },

    removeTab(key: string) {
      const index = this.tabsList.findIndex((item) => item.key === key)
      if (index < 0) return

      if (this.tabsList[index].affix || this.tabsList[index].closable === false) {
        return
      }

      this.tabsList.splice(index, 1)
      this.persistTabs()
    },

    closeOtherTabs(key: string) {
      this.tabsList = this.tabsList.filter((item) => item.affix || item.key === key)
      this.activeTab = key
      this.persistTabs()
    },

    closeRightTabs(key: string) {
      const index = this.tabsList.findIndex((item) => item.key === key)
      if (index < 0) return

      this.tabsList = this.tabsList.filter((item, itemIndex) => item.affix || itemIndex <= index)
      this.persistTabs()
    },

    closeAllTabs() {
      this.tabsList = this.tabsList.filter((item) => item.affix)
      this.activeTab = this.tabsList[0]?.key || '/dashboard'
      this.persistTabs()
    },

    clearTabState() {
      this.tabsList = []
      this.activeTab = ''
      safeStorage.removeItem(TAB_STORAGE_KEY)
      safeStorage.removeItem(ACTIVE_TAB_STORAGE_KEY)
    },
  },
})
