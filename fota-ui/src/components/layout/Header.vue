<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { availableLocales, setLocale } from '@/locales'
import { useThemeStore, type ThemeMode } from '@/stores/theme'
import { useLayoutStore } from '@/stores/layout'
import { useUserStore } from '@/stores/user'
import { safeStorage } from '@/utils/storage'
import Breadcrumb from './Breadcrumb.vue'

const router = useRouter()
const { t } = useI18n()
const themeStore = useThemeStore()
const layoutStore = useLayoutStore()
const userStore = useUserStore()

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

const currentThemeLabel = computed(() => {
  return t(themeOptions.find((opt) => opt.value === themeStore.currentMode)?.labelKey || 'theme.system')
})

const handleThemeChange = (mode: ThemeMode) => {
  themeStore.setMode(mode)
}
</script>

<template>
  <el-header class="header-shell">
    <div class="header-left">
      <button
        type="button"
        class="header-collapse-btn"
        @click="layoutStore.toggleCollapsed()"
      >
        <el-icon>
          <Fold v-if="!layoutStore.collapsed" />
          <Expand v-else />
        </el-icon>
      </button>

      <el-divider direction="vertical" class="header-divider" />

      <div class="header-breadcrumb-wrap">
        <Breadcrumb />
      </div>
    </div>

    <div class="header-right">
      <el-dropdown @command="handleThemeChange">
        <div
          class="ui-interactive-text header-dropdown-trigger"
        >
          <el-icon><component :is="currentThemeIcon" /></el-icon>
          <span class="hidden lg:inline">{{ currentThemeLabel }}</span>
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
          class="ui-interactive-text header-dropdown-trigger"
        >
          <el-icon><Globe /></el-icon>
          <span class="hidden md:inline">{{ availableLocales.find((it) => it.value === currentLocale)?.label }}</span>
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
          class="ui-interactive-text header-dropdown-trigger"
        >
          <el-avatar
            :size="32"
            class="bg-ui-brand"
          >
            <el-icon><User /></el-icon>
          </el-avatar>
          <span class="text-sm hidden md:inline">{{ t('header.admin') }}</span>
          <el-icon class="hidden md:inline-flex"><ArrowDown /></el-icon>
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

<style scoped>
.header-shell {
  height: var(--header-height);
  border-bottom: 1px solid color-mix(in srgb, var(--color-ui-brand) 18%, var(--color-ui-border-light));
  background:
    linear-gradient(
      90deg,
      color-mix(in srgb, var(--color-ui-bg-header) 82%, var(--color-ui-brand) 10%) 0%,
      color-mix(in srgb, var(--color-ui-bg-header) 95%, transparent) 34%,
      var(--color-ui-bg-header) 100%
    );
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 14px 0 10px;
}

.header-left {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-collapse-btn {
  width: 30px;
  height: 30px;
  border: 1px solid color-mix(in srgb, var(--color-ui-brand) 20%, var(--color-ui-border-light));
  border-radius: 8px;
  background: color-mix(in srgb, var(--color-ui-brand) 8%, transparent);
  color: var(--color-ui-text-regular);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
}

.header-collapse-btn:hover {
  color: var(--color-ui-brand);
  background: color-mix(in srgb, var(--color-ui-brand) 14%, transparent);
  border-color: color-mix(in srgb, var(--color-ui-brand) 35%, var(--color-ui-border-light));
}

.header-divider {
  margin: 0 2px;
}

.header-breadcrumb-wrap {
  min-width: 0;
  overflow: hidden;
}

.header-right {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-dropdown-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 14px;
  border: 1px solid transparent;
  padding: 5px 8px;
  border-radius: 8px;
  transition: all 0.2s;
}

.header-dropdown-trigger:hover {
  background: color-mix(in srgb, var(--color-ui-brand) 10%, transparent);
  border-color: color-mix(in srgb, var(--color-ui-brand) 26%, var(--color-ui-border-light));
}

@media (max-width: 768px) {
  .header-shell {
    padding: 0 10px 0 8px;
    gap: 8px;
  }

  .header-left {
    gap: 8px;
  }
}
</style>
