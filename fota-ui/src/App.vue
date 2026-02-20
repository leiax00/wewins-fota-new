<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from '@/components/layout/AppLayout.vue'
import LoadingScreen from '@/components/common/LoadingScreen.vue'

const route = useRoute()

const isLoginPage = computed(() => route.path === '/login')
const isReady = computed(() => route.matched.length > 0)
</script>

<template>
  <LoadingScreen v-if="!isReady" />
  <template v-else>
    <router-view v-if="isLoginPage" />
    <AppLayout v-else>
      <router-view />
    </AppLayout>
  </template>
</template>
