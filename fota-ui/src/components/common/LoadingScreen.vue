<script setup lang="ts">
</script>

<template>
  <div class="loading-container">
    <div class="loading-content">
      <div class="loading-logo">
        <el-icon class="loading-icon">
          <Promotion />
        </el-icon>
      </div>
      <div class="loading-text">
        FOTA
      </div>
      <div class="loading-bar">
        <div class="loading-bar-inner" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.loading-container {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--color-ui-login-grad-from) 0%, var(--color-ui-login-grad-to) 100%);
}

.loading-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.loading-logo {
  width: 80px;
  height: 80px;
  background: var(--color-ui-loading-logo-bg);
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(10px);
  animation: pulse 2s ease-in-out infinite;
}

.loading-icon {
  font-size: 40px;
  color: var(--color-ui-white);
}

.loading-text {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-ui-white);
  letter-spacing: 4px;
}

.loading-bar {
  width: 120px;
  height: 4px;
  background: var(--color-ui-loading-bar-bg);
  border-radius: 2px;
  overflow: hidden;
}

.loading-bar-inner {
  width: 40%;
  height: 100%;
  background: var(--color-ui-white);
  border-radius: 2px;
  animation: loading-bar 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%,
  100% {
    transform: scale(1);
    box-shadow: var(--shadow-ui-loading-pulse-start);
  }
  50% {
    transform: scale(1.05);
    box-shadow: var(--shadow-ui-loading-pulse-mid);
  }
}

@keyframes loading-bar {
  0% {
    transform: translateX(-100%);
  }
  100% {
    transform: translateX(350%);
  }
}

@media (prefers-reduced-motion: reduce) {
  .loading-logo,
  .loading-bar-inner {
    animation: none;
  }
}
</style>
