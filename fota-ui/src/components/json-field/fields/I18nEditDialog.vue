<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import I18nField from '@/components/json-field/fields/I18nField.vue'
import type {
  I18nFieldValue,
  JsonFieldDefinition,
} from '@/components/json-field/types/json-field'

interface Props {
  modelValue: boolean
  field: JsonFieldDefinition | null
  value: I18nFieldValue
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm', value: I18nFieldValue): void
}>()

const { t, te } = useI18n()
const localValue = ref<I18nFieldValue>({})

const tr = (key: string, fallback: string) => (te(key) ? t(key) : fallback)

const normalizeI18nValue = (value: unknown): I18nFieldValue => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return {}
  const next: I18nFieldValue = {}
  Object.entries(value as Record<string, unknown>).forEach(([k, v]) => {
    if (typeof v === 'string') next[k] = v
    else if (v != null) next[k] = String(v)
  })
  return next
}

watch(
  () => props.value,
  (value) => {
    localValue.value = normalizeI18nValue(value)
  },
  { immediate: true, deep: true }
)

const dialogTitle = computed(() => {
  if (!props.field) return tr('jsonField.i18nEditDialog.title', '编辑多语言内容')
  return `${props.field.label} - ${tr('common.edit', '编辑')}`
})

const onDialogVisibleChange = (visible: boolean) => {
  emit('update:modelValue', visible)
}

const onConfirm = () => {
  emit('confirm', normalizeI18nValue(localValue.value))
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="880px"
    top="6vh"
    destroy-on-close
    @update:model-value="onDialogVisibleChange"
  >
    <div class="i18n-edit-dialog__body">
      <I18nField
        v-if="field"
        :field="field"
        :model-value="localValue"
        :disabled="disabled"
        @update:model-value="(value) => { localValue = normalizeI18nValue(value) }"
      />
    </div>

    <template #footer>
      <div class="i18n-edit-dialog__footer">
        <el-button @click="onDialogVisibleChange(false)">
          {{ tr('common.cancel', '取消') }}
        </el-button>
        <el-button
          type="primary"
          :disabled="disabled"
          @click="onConfirm"
        >
          {{ tr('common.confirm', '确认') }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.i18n-edit-dialog__body {
  min-height: 180px;
}

.i18n-edit-dialog__footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
