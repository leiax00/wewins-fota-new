<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

interface BreadcrumbItem {
  title: string
  path?: string
}

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const breadcrumbItems = computed<BreadcrumbItem[]>(() => {
  const items: BreadcrumbItem[] = []

  // 从 route.matched 自动构建面包屑
  for (const matched of route.matched) {
    // 跳过隐藏的节点和根布局节点
    if (matched.meta?.breadcrumbHidden) continue
    if (matched.path === '/' || !matched.path) continue

    // 解析标题：优先使用 title，其次 i18nKey，最后 name
    let title = matched.meta?.title as string | undefined;
    if (!title && matched.meta?.i18nKey) {
      title = t(matched.meta?.i18nKey as string)
    }

    if (!title) continue

    const currentPath = matched.path || undefined
    const alreadyExists = items.some((item) => item.title === title && item.path === currentPath)
    if (alreadyExists) continue

    items.push({ title, path: currentPath })
  }

  return items
})

const handleNavigate = (path?: string) => {
  if (!path) return
  if (path === route.path) return
  router.push(path)
}
</script>

<template>
  <el-breadcrumb
    separator="/"
    class="ui-breadcrumb"
  >
    <el-breadcrumb-item
      v-for="(item, index) in breadcrumbItems"
      :key="`${item.path || item.title}-${index}`"
    >
      <button
        v-if="item.path && index < breadcrumbItems.length - 1"
        type="button"
        class="ui-breadcrumb-link"
        @click="handleNavigate(item.path)"
      >
        {{ item.title }}
      </button>
      <span v-else>{{ item.title }}</span>
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>

<style scoped>
.ui-breadcrumb-link {
  border: none;
  padding: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
}
</style>
