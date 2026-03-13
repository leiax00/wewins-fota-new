<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { JsonFieldDefinition, JsonFieldOption } from '@/components/json-field/types/json-field'

/**
 * 选择字段组件
 *
 * 用于编辑单选下拉类型的 JSON 字段
 */
interface Props {
  /** 字段定义 */
  field: JsonFieldDefinition
  /** 字段值 */
  modelValue?: string | number
  /** 是否禁用 */
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | number): void
}>()

const { t, te } = useI18n()

/**
 * 占位文本
 */
const placeholder = computed(() => props.field.config.schema.placeholder || '')

/**
 * 帮助文本
 */
const helpText = computed(() => props.field.config.schema.help || '')

/**
 * 是否显示帮助图标
 */
const showHelpIcon = computed(() => Boolean(helpText.value))

/**
 * 选项列表
 */
const options = computed<JsonFieldOption[]>(() => props.field.config.schema.options || [])

/**
 * 获取选项显示标签（支持国际化，回退到 label）
 */
const getOptionLabel = (option: JsonFieldOption) => {
  if (option.i18nKey && te(option.i18nKey)) {
    return t(option.i18nKey)
  }
  return option.label
}

/**
 * 处理选择变化
 */
const onChange = (value: string | number) => {
  emit('update:modelValue', value)
}
</script>

<template>
  <div class="json-field-item">
    <el-select
      :model-value="modelValue"
      :disabled="disabled"
      :placeholder="placeholder"
      clearable
      style="width: 100%"
      @update:model-value="onChange"
    >
      <el-option
        v-for="option in options"
        :key="String(option.value)"
        :label="getOptionLabel(option)"
        :value="option.value"
      />
    </el-select>
    <div
      v-if="showHelpIcon"
      class="json-field-item__help-icon-wrapper"
    >
      <el-tooltip
        :content="helpText"
        placement="top"
      >
        <el-icon class="json-field-item__help-icon">
          <InfoFilled />
        </el-icon>
      </el-tooltip>
    </div>
  </div>
</template>

<style scoped>
.json-field-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.json-field-item__label {
  display: none;
}

.json-field-item__help-icon {
  font-size: 14px;
  color: var(--el-color-info);
  cursor: help;
  flex-shrink: 0;
}
</style>
