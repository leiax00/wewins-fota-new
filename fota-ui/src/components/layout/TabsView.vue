<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAppStore, type AppTabItem } from '@/stores/app'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const appStore = useAppStore()

const tabsEnabled = computed(() => appStore.multiTabsEnabled)

const affixTabs: AppTabItem[] = [
  {
    key: '/dashboard',
    path: '/dashboard',
    title: t('menu.dashboard'),
    affix: true,
    closable: false,
  },
]

onMounted(() => {
  if (tabsEnabled.value) {
    appStore.ensureAffixTabs(affixTabs)
  }
})

watch(tabsEnabled, (enabled) => {
  if (enabled) appStore.ensureAffixTabs(affixTabs)
})

const getRouteTitle = () => {
  const titleKey = route.meta?.titleKey as string | undefined
  if (titleKey) return t(titleKey)
  return (route.meta?.title as string) || String(route.name || route.path)
}

const shouldIgnoreRoute = () => {
  if (!tabsEnabled.value) return true
  if (route.path === '/login') return true
  return route.meta?.tabHidden === true
}

const syncRouteTab = () => {
  if (shouldIgnoreRoute()) return

  const tab: AppTabItem = {
    key: route.fullPath,
    path: route.fullPath,
    title: getRouteTitle(),
    affix: route.meta?.affix === true,
    closable: route.meta?.tabClosable !== false && route.meta?.affix !== true,
  }
  appStore.upsertTab(tab)
}

watch(
  () => route.fullPath,
  () => {
    syncRouteTab()
  },
  { immediate: true }
)

const activeTab = computed({
  get: () => appStore.activeTab || route.fullPath,
  set: (value: string) => {
    appStore.setActiveTab(value)
  },
})

const tabs = computed(() => appStore.tabsList)

const canCloseCurrent = computed(() => {
  const current = tabs.value.find((tab) => tab.key === activeTab.value)
  if (!current) return false
  return current.affix !== true && current.closable !== false
})

const hasRightClosableTabs = computed(() => {
  const index = tabs.value.findIndex((tab) => tab.key === activeTab.value)
  if (index < 0) return false
  return tabs.value.slice(index + 1).some((tab) => !tab.affix && tab.closable !== false)
})

const handleTabClick = (tabKey: string) => {
  if (tabKey === route.fullPath) return
  router.push(tabKey)
}

const resolveFallbackTab = (removedKey: string): string => {
  const index = tabs.value.findIndex((tab) => tab.key === removedKey)
  const right = tabs.value[index + 1]
  if (right) return right.key
  const left = tabs.value[index - 1]
  if (left) return left.key
  return '/dashboard'
}

const removeTab = (tabKey: string) => {
  const target = tabs.value.find((tab) => tab.key === tabKey)
  if (!target || target.affix || target.closable === false) return

  const nextKey = resolveFallbackTab(tabKey)
  appStore.removeTab(tabKey)

  if (route.fullPath === tabKey) {
    appStore.setActiveTab(nextKey)
    router.push(nextKey)
    return
  }

  appStore.setActiveTab(route.fullPath)
}

const closeOthers = () => {
  const keepKey = activeTab.value
  appStore.closeOtherTabs(keepKey)
}

const closeRight = () => {
  const current = activeTab.value
  appStore.closeRightTabs(current)
}

const closeAll = () => {
  appStore.closeAllTabs()
  const fallback = appStore.tabsList[0]?.key || '/dashboard'
  router.push(fallback)
}

const refreshCurrent = () => {
  const current = activeTab.value
  router.replace('/').then(() => {
    router.replace(current)
  })
}
</script>

<template>
  <div class="tabs-view">
    <el-scrollbar
      class="tabs-scroll"
      view-class="tabs-scroll-view"
    >
      <div class="tabs-list">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          type="button"
          class="tab-chip"
          :class="{ 'is-active': activeTab === tab.key }"
          @click="handleTabClick(tab.key)"
        >
          <span class="tab-chip-label">{{ tab.title }}</span>
          <el-icon
            v-if="tab.affix"
            class="tab-pin"
          >
            <Paperclip />
          </el-icon>
          <el-icon
            v-else-if="tab.closable !== false"
            class="tab-close"
            @click.stop="removeTab(tab.key)"
          >
            <Close />
          </el-icon>
        </button>
      </div>
    </el-scrollbar>

    <el-dropdown trigger="click">
      <button
        type="button"
        class="tab-actions"
      >
        <el-icon><ArrowDown /></el-icon>
      </button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item @click="refreshCurrent">
            {{ t('tabs.refresh') }}
          </el-dropdown-item>
          <el-dropdown-item
            :disabled="!canCloseCurrent"
            @click="removeTab(activeTab)"
          >
            {{ t('tabs.closeCurrent') }}
          </el-dropdown-item>
          <el-dropdown-item @click="closeOthers">
            {{ t('tabs.closeOthers') }}
          </el-dropdown-item>
          <el-dropdown-item
            :disabled="!hasRightClosableTabs"
            @click="closeRight"
          >
            {{ t('tabs.closeRight') }}
          </el-dropdown-item>
          <el-dropdown-item @click="closeAll">
            {{ t('tabs.closeAll') }}
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<style scoped>
.tabs-view {
  height: 40px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  border-bottom: 1px solid var(--color-ui-border-light);
  background: color-mix(in srgb, var(--color-ui-bg-header) 90%, var(--color-ui-bg-page));
}

.tabs-scroll {
  flex: 1;
  min-width: 0;
}

.tabs-scroll :deep(.el-scrollbar__wrap) {
  height: 100%;
  display: flex;
  align-items: center;
}

.tabs-scroll :deep(.el-scrollbar__view) {
  width: 100%;
  display: flex;
  align-items: center;
}

.tabs-list {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: max-content;
}

.tab-chip {
  height: 28px;
  line-height: 1;
  border: 1px solid var(--color-ui-border-light);
  border-radius: 8px;
  background: color-mix(in srgb, var(--color-ui-bg-card) 80%, transparent);
  color: var(--color-ui-text-regular);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 10px;
  cursor: pointer;
  transition: all 0.2s;
}

.tab-chip:hover {
  border-color: color-mix(in srgb, var(--color-ui-brand) 40%, var(--color-ui-border-light));
  color: var(--color-ui-brand);
}

.tab-chip.is-active {
  background: color-mix(in srgb, var(--color-ui-brand) 13%, var(--color-ui-bg-card));
  border-color: color-mix(in srgb, var(--color-ui-brand) 35%, var(--color-ui-border-light));
  color: var(--color-ui-brand);
}

.tab-chip-label {
  max-width: 168px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.tab-close,
.tab-pin {
  font-size: 12px;
}

.tab-close:hover {
  color: var(--color-ui-status-danger);
}

.tab-actions {
  width: 26px;
  height: 26px;
  border: 1px solid var(--color-ui-border-light);
  border-radius: 7px;
  background: var(--color-ui-bg-card);
  color: var(--color-ui-text-secondary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.tab-actions:hover {
  color: var(--color-ui-brand);
  border-color: color-mix(in srgb, var(--color-ui-brand) 45%, var(--color-ui-border-light));
}

@media (max-width: 768px) {
  .tabs-view {
    padding: 0 8px;
    height: 36px;
  }

  .tab-chip {
    height: 26px;
    padding: 0 8px;
  }

  .tab-chip-label {
    max-width: 118px;
    font-size: 12px;
  }
}
</style>
