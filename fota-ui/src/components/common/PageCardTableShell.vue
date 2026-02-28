<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string
    emptyDescription?: string
  }>(),
  {
    emptyDescription: '',
  }
)
</script>

<template>
  <el-card>
    <template #header>
      <div class="flex items-center justify-between">
        <span class="ui-card-title">{{ title }}</span>
        <slot name="actions" />
      </div>
    </template>

    <el-empty
      v-if="emptyDescription"
      :description="emptyDescription"
    />
    <div
      v-else
      class="ui-table-scroll"
    >
      <slot />
    </div>
  </el-card>
</template>
