<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { useThemeStore, type ThemeMode } from '@/stores/theme'
import { availableLocales, setLocale } from '@/locales'
import { safeStorage } from '@/utils/storage'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const themeStore = useThemeStore()

const formRef = ref()
const loading = ref(false)
const currentLocale = ref(safeStorage.getItem('locale') || 'zh-CN')

const form = reactive({
  username: '',
  password: '',
  remember: false,
})

const rules = {
  username: [{ required: true, message: () => t('login.usernameRequired'), trigger: 'blur' }],
  password: [
    { required: true, message: () => t('login.passwordRequired'), trigger: 'blur' },
    { min: 6, message: () => t('login.passwordMinLength'), trigger: 'blur' },
  ],
}

const handleLogin = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login(
      {
        username: form.username,
        password: form.password,
      },
      form.remember
    )
    ElMessage.success(t('login.loginSuccess'))
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    router.replace(redirect)
  } catch {
    // error handled in request
  } finally {
    loading.value = false
  }
}

const handleLocaleChange = (locale: string) => {
  currentLocale.value = locale
  setLocale(locale)
  window.location.reload()
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
  <div class="login-container">
    <LoginHeroBackground />
    
    <div class="login-content">
      <LoginCard
        :title="t('login.title')"
        :subtitle="t('login.subtitle')"
      >
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          class="login-form"
          size="large"
        >
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              :placeholder="t('login.usernamePlaceholder')"
              prefix-icon="User"
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              :placeholder="t('login.passwordPlaceholder')"
              prefix-icon="Lock"
              show-password
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-form-item>
            <div class="login-options">
              <el-checkbox v-model="form.remember">
                {{ t('login.rememberMe') }}
              </el-checkbox>
              <el-link
                type="primary"
                :underline="false"
              >
                {{ t('login.forgotPassword') }}
              </el-link>
            </div>
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              class="login-btn"
              :loading="loading"
              @click="handleLogin"
            >
              {{ t('login.login') }}
            </el-button>
          </el-form-item>
        </el-form>
        
        <div class="login-footer">
          <el-dropdown @command="handleThemeChange">
            <div class="locale-selector">
              <el-icon><component :is="currentThemeIcon" /></el-icon>
              <span>{{ t(themeOptions.find((opt) => opt.value === themeStore.currentMode)?.labelKey || 'theme.system') }}</span>
              <el-icon><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="opt in themeOptions"
                  :key="opt.value"
                  :command="opt.value"
                >
                  <div class="footer-dropdown-item">
                    <span>{{ t(opt.labelKey) }}</span>
                    <el-icon
                      v-if="themeStore.currentMode === opt.value"
                      class="footer-dropdown-check"
                    >
                      <Check />
                    </el-icon>
                  </div>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <el-dropdown @command="handleLocaleChange">
            <div class="locale-selector">
              <el-icon><Globe /></el-icon>
              <span>{{ availableLocales.find(l => l.value === currentLocale)?.label }}</span>
              <el-icon><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="locale in availableLocales"
                  :key="locale.value"
                  :command="locale.value"
                  :class="{ 'is-active': currentLocale === locale.value }"
                >
                  {{ locale.label }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </LoginCard>
      
      <div class="login-copyright">
        <span>&copy; 2026 Wewins. All rights reserved.</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  position: relative;
  overflow: hidden;
}

.login-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  z-index: 1;
  padding: 20px;
}

.login-form {
  margin-top: 0;
}

.login-form :deep(.el-input__wrapper) {
  border-radius: 12px;
  background-color: var(--color-ui-login-input-bg);
  box-shadow: var(--shadow-ui-login-input);
  border: 2px solid var(--color-ui-login-input-border);
  transition: all 0.3s;
}

.login-form :deep(.el-input__inner) {
  color: var(--color-ui-login-input-text);
}

.login-form :deep(.el-input__inner::placeholder) {
  color: color-mix(in srgb, var(--color-ui-login-input-text) 60%, transparent);
}

.login-form :deep(.el-input__wrapper:hover) {
  border-color: var(--color-ui-login-grad-from);
}

.login-form :deep(.el-input__wrapper.is-focus) {
  border-color: var(--color-ui-login-grad-from);
  box-shadow: var(--shadow-ui-login-input-focus);
}

.login-options {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.login-options :deep(.el-checkbox__label) {
  color: var(--color-ui-login-muted);
}

.login-options :deep(.el-link.el-link--primary) {
  color: color-mix(in srgb, var(--color-ui-login-grad-to) 70%, var(--color-ui-login-grad-from));
}

.login-options :deep(.el-link.el-link--primary:hover) {
  color: var(--color-ui-login-grad-to);
}

.login-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--color-ui-login-grad-from) 0%, var(--color-ui-login-grad-to) 100%);
  border: none;
  box-shadow: var(--shadow-ui-login-btn);
  transition: all 0.3s;
}

.login-btn:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-ui-login-btn-hover);
}

.login-footer {
  margin-top: 24px;
  display: flex;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
}

.locale-selector {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 20px;
  background: var(--color-ui-login-locale-bg);
  cursor: pointer;
  transition: all 0.3s;
  color: var(--color-ui-login-locale-text);
  font-size: 14px;
}

.locale-selector:hover {
  background: var(--color-ui-login-locale-bg-hover);
  color: var(--color-ui-login-locale-text-hover);
}

.footer-dropdown-item {
  min-width: 92px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.footer-dropdown-check {
  color: var(--color-ui-login-grad-to);
}

.login-copyright {
  margin-top: 32px;
  color: var(--color-ui-login-copyright);
  font-size: 13px;
  text-shadow: 0 1px 2px rgb(0 0 0 / 0.25);
}

@media (max-width: 640px) {
  .login-content {
    padding: 14px;
  }

  .login-copyright {
    margin-top: 24px;
    text-align: center;
  }
}
</style>
