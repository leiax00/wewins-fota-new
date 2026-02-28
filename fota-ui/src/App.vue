<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from '@/components/layout/AppLayout.vue'
import LoadingScreen from '@/components/common/LoadingScreen.vue'
import { useAppStore } from '@/stores/app'

const route = useRoute()
const appStore = useAppStore()
const tabsEnabled = computed(() => appStore.multiTabsEnabled)

const isLoginPage = computed(() => route.path === '/login')
const isReady = computed(() => route.matched.length > 0)

// 判断当前路由是否应该缓存
// 优先级：route.meta.keepAlive > tabsEnabled > false
const shouldKeepAlive = computed(() => {
  // 如果路由明确配置了 keepAlive，则使用该配置
  if (typeof route.meta?.keepAlive === 'boolean') {
    return route.meta.keepAlive
  }
  // 否则根据是否启用多标签页决定
  return tabsEnabled.value
})

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
        <keep-alive
          v-if="shouldKeepAlive"
          :include="cacheComponents"
        >
          <component
            :is="Component"
            :key="viewRoute.fullPath"
          />
        </keep-alive>
        <component
          :is="Component"
          v-else
          :key="viewRoute.fullPath"
        />
      </router-view>
    </AppLayout>
  </template>
</template>
