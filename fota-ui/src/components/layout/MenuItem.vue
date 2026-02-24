<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import type { MenuDTO } from '@/stores/user'

defineOptions({ name: 'MenuItem' })

interface Props {
  menu: MenuDTO
}

const props = defineProps<Props>()
const router = useRouter()
const { t } = useI18n()

const visibleChildren = computed(() => {
  return (props.menu.children || []).filter((child) => !child.meta?.hidden)
})

const menuIcon = computed(() => props.menu.meta?.icon || 'Menu')

const menuTitle = computed(() => {
  const i18nKey = props.menu.meta?.i18nKey
  return i18nKey ? t(i18nKey) : props.menu.path
})

// 处理外链菜单
const isExternalLink = computed(() => {
  return props.menu.meta?.externalLink?.url
})

const handleMenuClick = () => {
  // 如果是外链菜单
  if (isExternalLink.value && props.menu.meta?.externalLink) {
    const { url, openMode = '_blank' } = props.menu.meta.externalLink
    if (openMode === '_blank') {
      // 新窗口打开
      window.open(url, '_blank')
    } else {
      // 当前窗口打开
      window.location.href = url
    }
    return
  }

  // 普通菜单路由跳转
  if (props.menu.path) {
    router.push(props.menu.path)
  }
}
</script>

<template>
  <el-sub-menu
    v-if="visibleChildren.length"
    :index="menu.path"
  >
    <template #title>
      <el-icon class="menu-icon"><component :is="menuIcon" /></el-icon>
      <span class="menu-label">{{ menuTitle }}</span>
    </template>
    <MenuItem
      v-for="child in visibleChildren"
      :key="child.path"
      :menu="child"
    />
  </el-sub-menu>

  <el-menu-item
    v-else-if="!menu.meta?.hidden"
    :index="menu.path"
    @click="handleMenuClick"
  >
    <el-icon class="menu-icon"><component :is="menuIcon" /></el-icon>
    <span class="menu-label">{{ menuTitle }}</span>
  </el-menu-item>
</template>

<style scoped>
.menu-icon {
  font-size: 16px;
}

.menu-label {
  font-size: 14px;
}
</style>
