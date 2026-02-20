<script setup lang="ts">
import Sidebar from './Sidebar.vue'
import Header from './Header.vue'
import TabsView from './TabsView.vue'
import { useLayoutStore } from '@/stores/layout'

const layoutStore = useLayoutStore()
</script>

<template>
  <el-container class="app-shell">
    <Sidebar :collapsed="layoutStore.collapsed" />
    <el-container direction="vertical" class="app-right">
      <Header />
      <TabsView />
      <el-main
        class="app-main bg-ui-bg-page"
      >
        <transition
          name="fade"
          mode="out-in"
        >
          <slot />
        </transition>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.app-shell {
  height: 100vh;
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
