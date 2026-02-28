<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { JsonFieldDefinition } from '@/components/json-field/types/json-field'

/**
 * 字符串字段组件
 *
 * 用于编辑字符串和文本域类型的 JSON 字段
 */
interface Props {
  /** 字段定义 */
  field: JsonFieldDefinition
  /** 字段值 */
  modelValue?: string
  /** 是否禁用 */
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const { t, te } = useI18n()

/**
 * 字段类型（string 或 textarea）
 */
const fieldType = computed(() => props.field.config.schema.type)

/**
 * 是否为 textarea 模式
 */
const isTextarea = computed(() => fieldType.value === 'textarea')

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
 * autosize 配置
 */
const autosizeConfig = computed(() => {
  const config = props.field.config.schema.textareaConfig
  if (config) {
    const { minRows = 3, maxRows = 8 } = config
    return { minRows, maxRows }
  }
  return { minRows: 3, maxRows: 8 }
})

/**
 * 处理输入变化
 */
const onInput = (value: string) => {
  emit('update:modelValue', value)
}
</script>

<template>
  <div
    class="json-field-item"
    :class="{ 'json-field-item--textarea': isTextarea }"
  >
    <el-input
      :model-value="modelValue ?? ''"
      :type="isTextarea ? 'textarea' : 'text'"
      :placeholder="placeholder"
      :disabled="disabled"
      :autosize="isTextarea ? autosizeConfig : undefined"
      clearable
      class="json-field-item__input"
      @update:model-value="onInput"
    >
      <template
        v-if="showHelpIcon && !isTextarea"
        #suffix
      >
        <el-tooltip
          :content="helpText"
          placement="top"
        >
          <el-icon class="json-field-item__help-icon">
            <InfoFilled />
          </el-icon>
        </el-tooltip>
      </template>
    </el-input>
    <div
      v-if="showHelpIcon && isTextarea"
      class="json-field-item__help-text"
    >
      <el-icon class="json-field-item__help-icon">
        <InfoFilled />
      </el-icon>
      <span>{{ helpText }}</span>
    </div>
  </div>
</template>

<style scoped>
.json-field-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.json-field-item__label {
  display: none;
}

.json-field-item--textarea {
  gap: 6px;
}

.json-field-item__help-icon {
  margin-right: 4px;
  font-size: 14px;
  color: var(--el-color-info);
  cursor: help;
}

.json-field-item__help-text {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
</style>
