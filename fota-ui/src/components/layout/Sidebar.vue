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
const openedMenus = computed(() => (route.path.startsWith('/system') ? ['/system'] : []))

const menuItems = [
  { path: '/dashboard', labelKey: 'menu.dashboard', icon: 'Odometer' },
  { path: '/product', labelKey: 'menu.product', icon: 'Box' },
  { path: '/firmware', labelKey: 'menu.firmware', icon: 'Cpu' },
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
    class="sidebar-shell"
  >
    <div class="sidebar-brand" :class="{ 'is-collapsed': collapsed }">
      <div class="brand-icon-wrap">
        <el-icon class="brand-icon"><Promotion /></el-icon>
      </div>
      <div v-if="!collapsed" class="brand-copy">
        <p class="brand-title">{{ t('header.brand') }}</p>
        <p class="brand-subtitle">Device OTA Console</p>
      </div>
    </div>

    <el-menu
      :default-active="activeMenu"
      :default-openeds="openedMenus"
      :collapse="collapsed"
      :collapse-transition="false"
      background-color="var(--color-ui-bg-sidebar)"
      text-color="var(--color-ui-menu-text)"
      active-text-color="var(--color-ui-brand)"
      class="sidebar-menu"
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
            <el-icon class="menu-icon"><component :is="item.icon" /></el-icon>
            <span class="menu-label">{{ t(item.labelKey) }}</span>
          </template>
          <el-menu-item
            v-for="child in item.children"
            :key="child.path"
            :index="child.path"
            class="menu-item-child"
          >
            <el-icon class="menu-icon"><component :is="child.icon" /></el-icon>
            <span class="menu-label">{{ t(child.labelKey) }}</span>
          </el-menu-item>
        </el-sub-menu>
        <el-menu-item
          v-else
          :index="item.path"
          class="menu-item-top"
        >
          <el-icon class="menu-icon"><component :is="item.icon" /></el-icon>
          <span class="menu-label">{{ t(item.labelKey) }}</span>
        </el-menu-item>
      </template>
    </el-menu>
  </el-aside>
</template>

<style scoped>
.sidebar-shell {
  overflow: hidden;
  transition: width 0.3s ease;
  background:
    radial-gradient(circle at 8% 5%, rgb(255 255 255 / 0.08) 0, transparent 26%),
    linear-gradient(180deg, color-mix(in srgb, var(--color-ui-bg-sidebar) 94%, #0f273d) 0%, var(--color-ui-bg-sidebar) 100%);
  border-right: 1px solid color-mix(in srgb, var(--color-ui-brand) 14%, transparent);
  display: flex;
  flex-direction: column;
}

.sidebar-brand {
  min-height: 66px;
  padding: 12px 12px 10px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid color-mix(in srgb, var(--color-ui-brand) 18%, transparent);
}

.sidebar-brand.is-collapsed {
  justify-content: center;
  padding-left: 0;
  padding-right: 0;
}

.brand-icon-wrap {
  width: 36px;
  height: 36px;
  border-radius: 11px;
  background: linear-gradient(155deg, color-mix(in srgb, var(--color-ui-brand) 80%, #ffffff), color-mix(in srgb, var(--color-ui-brand) 55%, #70e1f5));
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 18px -8px rgb(0 0 0 / 0.45);
}

.brand-icon {
  font-size: 18px;
  color: #fff;
}

.brand-copy {
  min-width: 0;
}

.brand-title {
  margin: 0;
  color: #eef6ff;
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
}

.brand-subtitle {
  margin: 2px 0 0;
  color: color-mix(in srgb, var(--color-ui-menu-text) 72%, #fff);
  font-size: 11px;
  letter-spacing: 0.2px;
  white-space: nowrap;
}

.sidebar-menu {
  border: none;
  flex: 1;
  overflow-y: auto;
  padding: 10px 8px 12px;
}

.menu-icon {
  font-size: 16px;
}

.menu-label {
  font-weight: 500;
}

.sidebar-menu :deep(.el-menu-item),
.sidebar-menu :deep(.el-sub-menu__title) {
  margin-bottom: 6px;
  border-radius: 10px;
  height: 40px;
  line-height: 40px;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.sidebar-menu :deep(.el-menu-item:hover),
.sidebar-menu :deep(.el-sub-menu__title:hover) {
  background-color: color-mix(in srgb, var(--color-ui-brand) 16%, transparent) !important;
  color: #f4f9ff !important;
  border-color: color-mix(in srgb, var(--color-ui-brand) 35%, transparent);
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(100deg, color-mix(in srgb, var(--color-ui-brand) 30%, transparent) 0%, color-mix(in srgb, var(--color-ui-brand) 18%, transparent) 100%) !important;
  border-color: color-mix(in srgb, var(--color-ui-brand) 50%, transparent);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--color-ui-brand) 20%, transparent);
}

.sidebar-menu :deep(.el-menu-item.is-active::before) {
  content: '';
  position: absolute;
  left: 8px;
  top: 9px;
  width: 3px;
  height: 22px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--color-ui-brand) 88%, #8bdfff);
}

.sidebar-menu :deep(.el-sub-menu .el-menu-item) {
  padding-left: 42px !important;
  height: 36px;
  line-height: 36px;
  margin-bottom: 4px;
  border-radius: 8px;
}

.sidebar-menu :deep(.el-menu--collapse .el-sub-menu__title),
.sidebar-menu :deep(.el-menu--collapse .el-menu-item) {
  justify-content: center;
}

.sidebar-menu :deep(.el-menu--collapse .el-sub-menu__title .menu-label),
.sidebar-menu :deep(.el-menu--collapse .el-menu-item .menu-label) {
  display: none;
}

</style>
