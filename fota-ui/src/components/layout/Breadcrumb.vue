<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

interface BreadcrumbMetaItem {
  titleKey?: string
  title?: string
  path?: string
}

interface BreadcrumbItem {
  title: string
  path?: string
}

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const resolveTitle = (titleKey?: string, title?: string): string => {
  if (titleKey) return t(titleKey)
  return title || ''
}

const breadcrumbItems = computed<BreadcrumbItem[]>(() => {
  const items: BreadcrumbItem[] = []

  const customTrail = (route.meta.breadcrumb as BreadcrumbMetaItem[] | undefined) || []
  for (const crumb of customTrail) {
    const resolvedTitle = resolveTitle(crumb.titleKey, crumb.title)
    if (!resolvedTitle) continue
    items.push({ title: resolvedTitle, path: crumb.path })
  }

  for (const matched of route.matched) {
    if (matched.meta?.breadcrumbHidden) continue
    const resolvedTitle = resolveTitle(matched.meta?.titleKey as string | undefined, matched.meta?.title as string | undefined)
    if (!resolvedTitle) continue

    const currentPath = matched.path || undefined
    const alreadyExists = items.some((item) => item.title === resolvedTitle && item.path === currentPath)
    if (alreadyExists) continue

    items.push({ title: resolvedTitle, path: currentPath })
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
