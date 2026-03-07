<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from '@/components/layout/AppLayout.vue'
import LoadingScreen from '@/components/common/LoadingScreen.vue'

const route = useRoute()

const isLoginPage = computed(() => route.path === '/login')
const isReady = computed(() => route.matched.length > 0)

// 用于缓存的 include 数组
// 如果路由指定了组件名，则使用组件名进行缓存匹配
const cacheComponents = computed(() => {
  // 可以根据需要返回需要缓存的组件名称数组
  // 例如：['DashboardView', 'ProductListView']
  return undefined
})

</script>

<template>
  <LoadingScreen v-if="!isReady" />
  <template v-else>
    <router-view v-if="isLoginPage" />
    <AppLayout v-else>
      <router-view v-slot="{ Component, route: viewRoute }">
        <keep-alive :include="cacheComponents">
          <component
            :is="Component"
            v-if="viewRoute.meta?.keepAlive === true"
            :key="String(viewRoute.name || viewRoute.path)"
          />
        </keep-alive>
        <component
          :is="Component"
          v-if="viewRoute.meta?.keepAlive !== true"
          :key="viewRoute.fullPath"
        />
      </router-view>
    </AppLayout>
  </template>
</template>
