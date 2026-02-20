<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { availableLocales, setLocale } from '@/locales'
import { useThemeStore, type ThemeMode } from '@/stores/theme'
import { useLayoutStore } from '@/stores/layout'
import { useUserStore } from '@/stores/user'
import { safeStorage } from '@/utils/storage'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const themeStore = useThemeStore()
const layoutStore = useLayoutStore()
const userStore = useUserStore()

const breadcrumbs = computed(() => {
  const matched = route.matched.filter((item) => item.meta?.titleKey || item.meta?.title)
  return matched.map((item) => ({
    title: item.meta?.titleKey ? t(item.meta.titleKey as string) : (item.meta?.title as string),
    path: item.path,
  }))
})

const handleCommand = async (command: string) => {
  if (command === 'logout') {
    await userStore.logout()
    return
  }

  if (command === 'profile') {
    router.push('/system/user')
  }
}

const currentLocale = computed(() => safeStorage.getItem('locale') || 'zh-CN')

const handleLocaleChange = (locale: string) => {
  if (locale === currentLocale.value) {
    return
  }
  setLocale(locale)
  // locale change is reactive, no need to reload page
}

const themeOptions: { value: ThemeMode; labelKey: string; icon: string }[] = [
  { value: 'light', labelKey: 'theme.light', icon: 'Sunny' },
  { value: 'dark', labelKey: 'theme.dark', icon: 'Moon' },
  { value: 'system', labelKey: 'theme.system', icon: 'Monitor' },
]

const currentThemeIcon = computed(() => {
  return themeOptions.find((opt) => opt.value === themeStore.currentMode)?.icon || 'Monitor'
})

const handleThemeChange = (mode: ThemeMode) => {
  themeStore.setMode(mode)
}
</script>

<template>
  <el-header
    class="flex h-[50px] items-center justify-between border-b border-ui-border-light bg-ui-bg-header px-4"
  >
    <div class="flex items-center gap-4">
      <div
        class="ui-brand-text flex items-center gap-2 font-semibold"
      >
        <el-icon class="text-lg">
          <Promotion />
        </el-icon>
        <span class="text-sm">{{ t('header.brand') }}</span>
      </div>
      <el-divider direction="vertical" />
      <el-icon
        class="ui-interactive-text cursor-pointer text-xl"
        @click="layoutStore.toggleCollapsed()"
      >
        <Fold v-if="!layoutStore.collapsed" />
        <Expand v-else />
      </el-icon>
      <el-breadcrumb separator="/">
        <el-breadcrumb-item
          v-for="item in breadcrumbs"
          :key="item.path"
        >
          {{ item.title }}
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>
    <div class="flex items-center gap-3">
      <el-dropdown @command="handleThemeChange">
        <div
          class="ui-interactive-text header-dropdown-trigger flex cursor-pointer items-center gap-1 text-sm"
        >
          <el-icon><component :is="currentThemeIcon" /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-for="opt in themeOptions"
              :key="opt.value"
              :command="opt.value"
            >
              <div class="flex items-center gap-2">
                <el-icon><component :is="opt.icon" /></el-icon>
                <span>{{ t(opt.labelKey) }}</span>
                <el-icon
                  v-if="themeStore.currentMode === opt.value"
                  class="ui-action-primary"
                >
                  <Check />
                </el-icon>
              </div>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-dropdown @command="handleLocaleChange">
        <div
          class="ui-interactive-text header-dropdown-trigger flex cursor-pointer items-center gap-1 text-sm"
        >
          <el-icon><Globe /></el-icon>
          <span>{{ availableLocales.find((it) => it.value === currentLocale)?.label }}</span>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-for="locale in availableLocales"
              :key="locale.value"
              :command="locale.value"
            >
              <div class="flex items-center gap-2">
                <span>{{ locale.label }}</span>
                <el-icon
                  v-if="currentLocale === locale.value"
                  class="ui-action-primary"
                >
                  <Check />
                </el-icon>
              </div>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-dropdown @command="handleCommand">
        <div
          class="ui-interactive-text header-dropdown-trigger flex cursor-pointer items-center gap-2"
        >
          <el-avatar
            :size="32"
            class="bg-ui-brand"
          >
            <el-icon><User /></el-icon>
          </el-avatar>
          <span class="text-sm">{{ t('header.admin') }}</span>
          <el-icon><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              {{ t('header.profile') }}
            </el-dropdown-item>
            <el-dropdown-item
              divided
              command="logout"
            >
              {{ t('header.logout') }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </el-header>
</template>
