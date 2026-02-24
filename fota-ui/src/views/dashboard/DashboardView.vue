<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const userStore = useUserStore()

const stats = ref([
  { titleKey: 'dashboard.totalDevices', value: '1,234,567', icon: 'Iphone', color: 'bg-ui-status-info' },
  { titleKey: 'dashboard.todayActive', value: '89,234', icon: 'User', color: 'bg-ui-status-success' },
  { titleKey: 'dashboard.upgradeRate', value: '98.5%', icon: 'CircleCheck', color: 'bg-ui-brand' },
  { titleKey: 'dashboard.pendingUpgrade', value: '12,345', icon: 'Loading', color: 'bg-ui-status-warning' },
])
</script>

<template>
  <div>
    <h1 class="ui-page-title">
      {{ t('menu.dashboard') }}
    </h1>
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
      <div
        v-for="stat in stats"
        :key="stat.titleKey"
        class="ui-stat-card"
      >
        <div :class="[stat.color, 'ui-stat-icon']">
          <el-icon class="text-ui-white text-2xl">
            <component :is="stat.icon" />
          </el-icon>
        </div>
        <div>
          <div class="ui-stat-label">
            {{ t(stat.titleKey) }}
          </div>
          <div class="ui-stat-value">
            {{ stat.value }}
          </div>
        </div>
      </div>
    </div>
    <el-card class="mb-6">
      <template #header>
        <span class="ui-card-title">{{ t('dashboard.quickEntry') }}</span>
      </template>
      <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
        <router-link
          v-if="userStore.hasPermission('fota:product:read')"
          to="/product"
          class="ui-nav-tile"
        >
          <el-icon
            class="ui-brand-text text-3xl mb-2"
          >
            <Box />
          </el-icon>
          <div>{{ t('menu.product') }}</div>
        </router-link>
        <router-link
          v-if="userStore.hasPermission('fota:firmware:read')"
          to="/firmware"
          class="ui-nav-tile"
        >
          <el-icon
            class="ui-brand-text text-3xl mb-2"
          >
            <Cpu />
          </el-icon>
          <div>{{ t('menu.firmware') }}</div>
        </router-link>
        <router-link
          v-if="userStore.hasPermission('fota:policy:read')"
          to="/policy"
          class="ui-nav-tile"
        >
          <el-icon
            class="ui-brand-text text-3xl mb-2"
          >
            <Document />
          </el-icon>
          <div>{{ t('menu.policy') }}</div>
        </router-link>
        <router-link
          v-if="userStore.hasPermission('fota:device:read')"
          to="/device"
          class="ui-nav-tile"
        >
          <el-icon
            class="ui-brand-text text-3xl mb-2"
          >
            <Iphone />
          </el-icon>
          <div>{{ t('menu.device') }}</div>
        </router-link>
      </div>
    </el-card>
  </div>
</template>
