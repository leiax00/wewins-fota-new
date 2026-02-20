<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

interface Props {
  collapsed: boolean
}

defineProps<Props>()

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const activeMenu = computed(() => route.path)

const menuItems = [
  { path: '/dashboard', labelKey: 'menu.dashboard', icon: 'Odometer' },
  { path: '/product', labelKey: 'menu.product', icon: 'Box' },
  { path: '/firmware', labelKey: 'menu.firmware', icon: 'Disc' },
  { path: '/policy', labelKey: 'menu.policy', icon: 'Document' },
  { path: '/device', labelKey: 'menu.device', icon: 'Iphone' },
  {
    path: '/system',
    labelKey: 'menu.system',
    icon: 'Setting',
    children: [
      { path: '/system/user', labelKey: 'menu.user', icon: 'User' },
      { path: '/system/role', labelKey: 'menu.role', icon: 'UserFilled' },
      { path: '/system/permission', labelKey: 'menu.permission', icon: 'Lock' },
      { path: '/system/dict', labelKey: 'menu.dict', icon: 'Collection' },
    ],
  },
]

const handleSelect = (path: string) => {
  router.push(path)
}
</script>

<template>
  <el-aside
    :width="collapsed ? '64px' : '210px'"
    class="overflow-hidden bg-ui-bg-sidebar transition-all duration-300"
  >
    <el-menu
      :default-active="activeMenu"
      :collapse="collapsed"
      :collapse-transition="false"
      background-color="var(--color-ui-bg-sidebar)"
      text-color="var(--color-ui-menu-text)"
      active-text-color="var(--color-ui-brand)"
      class="border-none h-full"
      @select="handleSelect"
    >
      <template
        v-for="item in menuItems"
        :key="item.path"
      >
        <el-sub-menu
          v-if="item.children"
          :index="item.path"
        >
          <template #title>
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ t(item.labelKey) }}</span>
          </template>
          <el-menu-item
            v-for="child in item.children"
            :key="child.path"
            :index="child.path"
          >
            <el-icon><component :is="child.icon" /></el-icon>
            <span>{{ t(child.labelKey) }}</span>
          </el-menu-item>
        </el-sub-menu>
        <el-menu-item
          v-else
          :index="item.path"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ t(item.labelKey) }}</span>
        </el-menu-item>
      </template>
    </el-menu>
  </el-aside>
</template>
