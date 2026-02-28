<script setup lang="ts">
defineProps<{
  title: string
  subtitle: string
}>()
</script>

<template>
  <div class="login-card">
    <div class="login-header">
      <div class="login-logo">
        <el-icon class="logo-icon">
          <Promotion />
        </el-icon>
      </div>
      <h1 class="login-title">
        {{ title }}
      </h1>
      <p class="login-subtitle">
        {{ subtitle }}
      </p>
    </div>

    <slot />
  </div>
</template>

<style scoped>
.login-card {
  width: 100%;
  max-width: 420px;
  background: var(--color-ui-login-card-bg);
  backdrop-filter: blur(20px);
  border: 1px solid color-mix(in srgb, var(--color-ui-login-shape) 45%, transparent);
  border-radius: 24px;
  padding: 48px 40px;
  box-shadow: var(--shadow-ui-login-card);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.login-logo {
  width: 72px;
  height: 72px;
  background: linear-gradient(135deg, var(--color-ui-login-grad-from) 0%, var(--color-ui-login-grad-to) 100%);
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20px;
  box-shadow: var(--shadow-ui-login-logo);
  position: relative;
  overflow: hidden;
}

.login-logo::after {
  content: '';
  position: absolute;
  inset: 4px;
  border-radius: 16px;
  border: 1px solid rgb(255 255 255 / 0.2);
}

.logo-icon {
  font-size: 36px;
  color: var(--color-ui-white);
}

.login-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-ui-login-title);
  margin: 0 0 8px;
}

.login-subtitle {
  font-size: 14px;
  color: var(--color-ui-login-muted);
  margin: 0;
}

@media (max-width: 640px) {
  .login-card {
    border-radius: 20px;
    padding: 34px 22px;
  }

  .login-title {
    font-size: 24px;
  }
}
</style>
