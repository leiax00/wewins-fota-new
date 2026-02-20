<template>
  <div class="login-bg">
    <div class="login-shapes">
      <div class="shape shape-1" />
      <div class="shape shape-2" />
      <div class="shape shape-3" />
    </div>
  </div>
</template>

<style scoped>
.login-bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background:
    radial-gradient(circle at 12% 20%, rgb(255 255 255 / 0.14) 0, transparent 36%),
    radial-gradient(circle at 84% 82%, rgb(255 255 255 / 0.12) 0, transparent 35%),
    linear-gradient(140deg, var(--color-ui-login-grad-from) 0%, var(--color-ui-login-grad-to) 100%);
}

.login-bg::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgb(255 255 255 / 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgb(255 255 255 / 0.03) 1px, transparent 1px);
  background-size: 48px 48px;
}

.login-shapes {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  overflow: hidden;
}

.shape {
  position: absolute;
  border-radius: 50%;
  background: var(--color-ui-login-shape);
  filter: blur(2px);
  animation: float 22s infinite ease-in-out;
}

.shape-1 {
  width: 600px;
  height: 600px;
  top: -200px;
  left: -200px;
  animation-delay: 0s;
}

.shape-2 {
  width: 400px;
  height: 400px;
  bottom: -100px;
  right: -100px;
  animation-delay: -5s;
}

.shape-3 {
  width: 320px;
  height: 320px;
  top: 46%;
  left: 56%;
  animation-delay: -10s;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0) rotate(0deg);
  }
  50% {
    transform: translateY(-24px) rotate(8deg) scale(1.04);
  }
}

@media (prefers-reduced-motion: reduce) {
  .shape {
    animation: none;
  }
}
</style>
