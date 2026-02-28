import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useLayoutStore = defineStore('layout', () => {
  // 侧边栏折叠状态
  const collapsed = ref(false)

  /**
   * 切换侧边栏折叠状态
   */
  const toggleCollapsed = () => {
    collapsed.value = !collapsed.value
  }

  /**
   * 设置侧边栏折叠状态
   */
  const setCollapsed = (value: boolean) => {
    collapsed.value = value
  }

  return {
    collapsed,
    toggleCollapsed,
    setCollapsed,
  }
})
