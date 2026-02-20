import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', {
  state: () => ({
    sidebarCollapsed: localStorage.getItem('sidebarCollapsed') === 'true',
    tabsList: [] as { path: string; title: string; name: string }[],
    activeTab: '',
  }),

  actions: {
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
      localStorage.setItem('sidebarCollapsed', String(this.sidebarCollapsed))
    },

    addTab(route: { path: string; title?: string; name: string }) {
      const title = route.title || route.name
      if (!this.tabsList.find((tab) => tab.path === route.path)) {
        this.tabsList.push({ ...route, title })
      }
      this.activeTab = route.path
    },

    removeTab(path: string) {
      const index = this.tabsList.findIndex((tab) => tab.path === path)
      if (index > -1) {
        this.tabsList.splice(index, 1)
        if (this.activeTab === path) {
          const nextTab = this.tabsList[index] || this.tabsList[index - 1]
          this.activeTab = nextTab?.path || '/dashboard'
        }
      }
    },

    closeOtherTabs(path: string) {
      this.tabsList = this.tabsList.filter((tab) => tab.path === path)
      this.activeTab = path
    },

    closeAllTabs() {
      this.tabsList = []
      this.activeTab = '/dashboard'
    },
  },
})
