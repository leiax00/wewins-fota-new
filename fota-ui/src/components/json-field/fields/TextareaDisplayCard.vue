<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { JsonFieldDefinition } from '@/components/json-field/types/json-field'

/**
 * Textarea 字段展示卡片组件
 *
 * 全宽展示，内容两行预览，可展开/折叠
 */
interface Props {
  field: JsonFieldDefinition
  modelValue?: string
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
})

const emit = defineEmits<{
  (e: 'edit'): void
  (e: 'remove'): void
}>()

const { t } = useI18n()

const collapsed = ref(true)

const displayText = computed(() => {
  const value = props.modelValue
  if (value === undefined || value === null || value === '') {
    return '-'
  }
  return String(value)
})

const shouldShowExpandToggle = computed(() => {
  const text = props.modelValue
  if (text === undefined || text === null || text === '') return false
  const normalized = String(text)
  // 包含换行符或长度超过 80 字符时显示展开按钮
  return normalized.includes('\n') || normalized.length > 80
})

const toggleCollapse = (event: MouseEvent) => {
  event.stopPropagation()
  collapsed.value = !collapsed.value
}

const onRemove = (event: MouseEvent) => {
  event.stopPropagation()
  emit('remove')
}

const onEdit = () => {
  emit('edit')
}
</script>

<template>
  <div
    class="textarea-display-card"
    @click="onEdit"
  >
    <div class="textarea-display-card__header">
      <el-tag
        size="small"
        type="info"
        effect="plain"
      >
        {{ field.label }}
      </el-tag>
      <div class="textarea-display-card__actions">
        <el-button
          v-if="shouldShowExpandToggle"
          link
          size="small"
          :disabled="disabled"
          @click="toggleCollapse"
        >
          <span class="textarea-display-card__toggle-text">
            {{ collapsed ? t('jsonField.i18nField.expand') : t('jsonField.i18nField.collapse') }}
          </span>
          <el-icon>
            <ArrowDown v-if="collapsed" />
            <ArrowUp v-else />
          </el-icon>
        </el-button>
        <el-button
          link
          size="small"
          class="ui-action-danger"
          :disabled="disabled"
          @click="onRemove"
        >
          <el-icon><Delete /></el-icon>
        </el-button>
      </div>
    </div>
    <div
      class="textarea-display-card__body"
      :class="{ 'is-collapsed': collapsed }"
    >
      {{ displayText }}
    </div>
  </div>
</template>

<style scoped>
.textarea-display-card {
  background: #fff;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 10px;
  transition: all 0.3s ease;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  cursor: pointer;
  width: 100%;
}

.textarea-display-card:hover {
  border-color: var(--el-color-primary);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.textarea-display-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.textarea-display-card__actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.textarea-display-card__body {
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-primary);
  word-break: break-word;
  white-space: pre-wrap;
}

.textarea-display-card__body.is-collapsed {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.textarea-display-card__toggle-text {
  font-size: 12px;
}
</style>
