<script setup lang="ts">
defineProps<{
  title: string
}>()

defineEmits<{
  back: []
}>()
</script>

<template>
  <div class="space-y-4">
    <el-page-header @back="$emit('back')">
      <template #content>
        <span class="ui-card-title">{{ title }}</span>
      </template>
      <template #extra>
        <slot name="extra" />
      </template>
    </el-page-header>
    <slot />
  </div>
</template>
