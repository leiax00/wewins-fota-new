<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import StringField from './StringField.vue'
import NumberField from './NumberField.vue'
import BooleanField from './BooleanField.vue'
import SelectField from './SelectField.vue'
import type { JsonFieldDefinition } from '@/components/json-field/types/json-field'

/**
 * Primitive 字段编辑对话框
 *
 * 用于编辑 string、number、boolean、select、textarea 等简单字段
 */
interface Props {
  modelValue: boolean
  field: JsonFieldDefinition | null
  value?: string | number | boolean
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm', value: string | number | boolean): void
}>()

const { t } = useI18n()

/**
 * 内部编辑值
 */
const internalValue = ref<string | number | boolean>()

/**
 * 对话框可见性
 */
const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

/**
 * 根据字段类型获取对应组件
 */
const editorComponent = computed(() => {
  if (!props.field) return null

  switch (props.field.config.schema.type) {
    case 'string':
    case 'textarea':
      return StringField
    case 'number':
      return NumberField
    case 'boolean':
      return BooleanField
    case 'select':
      return SelectField
    default:
      return StringField
  }
})

/**
 * 获取默认值
 */
const getDefaultValue = (): string | number | boolean => {
  if (!props.field) return ''

  const { defaultValue } = props.field.config.schema
  if (defaultValue !== undefined) {
    return defaultValue as string | number | boolean
  }

  switch (props.field.config.schema.type) {
    case 'string':
    case 'textarea':
      return ''
    case 'number':
      return 0
    case 'boolean':
      return false
    case 'select':
      const options = props.field.config.schema.options || []
      return options.length > 0 ? (options[0].value as string | number | boolean) : ''
    default:
      return ''
  }
}

/**
 * 确认编辑
 */
const confirm = () => {
  if (props.field && internalValue.value !== undefined) {
    emit('confirm', internalValue.value)
    visible.value = false
  }
}

/**
 * 取消编辑
 */
const cancel = () => {
  visible.value = false
}

/**
 * 监听对话框打开，初始化值
 */
watch(visible, (val) => {
  if (val) {
    // 对话框打开时，使用当前值或默认值
    internalValue.value = props.value ?? getDefaultValue()
  }
})
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="field ? `${t('jsonField.editField')}: ${field.label}` : t('jsonField.editField')"
    width="500px"
    :close-on-click-modal="false"
    @close="cancel"
  >
    <div v-if="field" class="primitive-edit-dialog__content">
      <component
        :is="editorComponent"
        :field="field"
        v-model="internalValue"
        :disabled="disabled"
      />
    </div>
    <div v-else class="primitive-edit-dialog__empty">
      <el-empty :description="t('jsonField.fieldNotFound')" />
    </div>

    <template #footer>
      <el-button @click="cancel">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" @click="confirm" :disabled="!field">
        {{ t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.primitive-edit-dialog__content {
  padding: 20px 0;
}

.primitive-edit-dialog__empty {
  padding: 40px 0;
}
</style>
