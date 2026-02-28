<script setup lang="ts">
import Sidebar from './Sidebar.vue'
import Header from './Header.vue'
import TabsView from './TabsView.vue'
import { computed } from 'vue'
import { useAppStore } from '@/stores/app'
import { useLayoutStore } from '@/stores/layout'

const layoutStore = useLayoutStore()
const appStore = useAppStore()
const tabsEnabled = computed(() => appStore.multiTabsEnabled)
</script>

<template>
  <el-container class="app-shell">
    <Sidebar :collapsed="layoutStore.collapsed" />
    <el-container
      direction="vertical"
      class="app-right"
    >
      <Header />
      <TabsView v-if="tabsEnabled" />
      <el-main
        class="app-main bg-ui-bg-page"
      >
        <slot />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.app-shell {
  height: 100vh;
  min-width: 1024px;
  overflow: hidden;
}

.app-right {
  flex: 1;
  min-width: 0;
  min-height: 0;
}

.app-main {
  padding: 1rem;
  overflow: auto;
  min-height: 0;
}

</style>
