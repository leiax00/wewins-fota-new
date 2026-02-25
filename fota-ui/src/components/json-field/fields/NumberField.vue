<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { JsonFieldDefinition } from '@/components/json-field/types/json-field'

/**
 * 数字字段组件
 *
 * 用于编辑数字类型的 JSON 字段
 */
interface Props {
  /** 字段定义 */
  field: JsonFieldDefinition
  /** 字段值 */
  modelValue?: number
  /** 是否禁用 */
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value?: number): void
}>()

const { t, te } = useI18n()

/**
 * 字段显示标签（支持国际化，回退到 label）
 */
const label = computed(() => {
  if (props.field.i18nKey && te(props.field.i18nKey)) {
    return t(props.field.i18nKey)
  }
  return props.field.label
})

/**
 * 最小值
 */
const min = computed(() => props.field.config.schema.validator?.min)

/**
 * 最大值
 */
const max = computed(() => props.field.config.schema.validator?.max)

/**
 * 帮助文本
 */
const helpText = computed(() => props.field.config.schema.help || '')

/**
 * 是否显示帮助图标
 */
const showHelpIcon = computed(() => Boolean(helpText.value))

/**
 * 处理数值变化
 */
const onChange = (value: number | undefined) => {
  emit('update:modelValue', value)
}
</script>

<template>
  <div class="json-field-item">
    <div class="json-field-item__wrapper">
      <el-input-number
        :model-value="modelValue"
        :disabled="disabled"
        :min="min"
        :max="max"
        controls-position="right"
        style="width: 100%"
        @update:model-value="onChange"
      />
      <div v-if="showHelpIcon" class="json-field-item__help-icon-wrapper">
        <el-tooltip :content="helpText" placement="top">
          <el-icon class="json-field-item__help-icon"><InfoFilled /></el-icon>
        </el-tooltip>
      </div>
    </div>
  </div>
</template>

<style scoped>
.json-field-item__label {
  display: none;
}

.json-field-item__wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
}

.json-field-item__help-icon {
  font-size: 14px;
  color: var(--el-color-info);
  cursor: help;
  flex-shrink: 0;
}
</style>
