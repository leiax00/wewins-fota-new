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

</script>

<template>
  <LoadingScreen v-if="!isReady" />
  <template v-else>
    <router-view v-if="isLoginPage" />
    <AppLayout v-else>
      <router-view v-slot="{ Component, route: viewRoute }">
        <keep-alive v-if="tabsEnabled">
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
