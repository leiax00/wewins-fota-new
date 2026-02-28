<script setup lang="ts">
import { computed, reactive, watch, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import I18nDisplayCard from '@/components/json-field/fields/I18nDisplayCard.vue'
import I18nEditDialog from '@/components/json-field/fields/I18nEditDialog.vue'
import PrimitiveEditDialog from '@/components/json-field/fields/PrimitiveEditDialog.vue'
import TextareaDisplayCard from '@/components/json-field/fields/TextareaDisplayCard.vue'
import { validateBySchema } from '@/components/json-field/utils/validator'
import type {
  I18nFieldValue,
  JsonFieldDefinition,
  JsonObjectValue,
} from '@/components/json-field/types/json-field'

/**
 * 动态 JSON 表单组件（美化版本）
 *
 * 用户主动添加字段，简单字段用 Tag 展示，i18n 字段用卡片展示
 */
interface Props {
  /** 字段定义列表 */
  fields: JsonFieldDefinition[]
  /** 已知字段值 */
  modelValue: JsonObjectValue
  /** 未知字段值 */
  unknownFields: JsonObjectValue
  /** 是否禁用 */
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: JsonObjectValue): void
  (e: 'update:unknownFields', value: JsonObjectValue): void
  (e: 'validation-change', errors: string[]): void
}>()

const { t } = useI18n()

// 本地状态
const localKnown = reactive<JsonObjectValue>({})
const localUnknown = reactive<JsonObjectValue>({})
const unknownDraft = reactive<Record<string, string>>({})

// 字段添加顺序（用于控制显示顺序，新添加的在前）
const fieldOrder = ref<string[]>([])

// Primitive 字段类型集合
const primitiveFieldTypes = new Set(['string', 'textarea', 'number', 'boolean', 'select'])

// Primitive 编辑对话框状态
const primitiveDialogVisible = ref(false)
const editingPrimitiveKey = ref('')
const editingPrimitiveField = ref<JsonFieldDefinition | null>(null)
const editingPrimitiveValue = ref<string | number | boolean>()

// i18n 编辑对话框相关状态
const i18nDialogVisible = ref(false)
const editingI18nKey = ref('')
const editingI18nValue = ref<I18nFieldValue>({})

/**
 * 获取可用的字段列表（未被添加的）
 */
const availableFields = computed(() => {
  return props.fields.filter((field) => !(field.key in localKnown))
})

/**
 * 已添加的字段列表（按添加顺序排序，新添加的在前）
 */
const addedFieldKeys = computed(() => {
  return fieldOrder.value.filter(key => key in localKnown)
})

/**
 * 打开 primitive 字段编辑对话框（新增或编辑）
 */
const openPrimitiveEdit = (key?: string) => {
  if (props.disabled) return

  if (key) {
    // 编辑模式：编辑已存在的字段
    const field = getFieldByKey(key)
    if (!field || !isPrimitiveField(field)) return
    editingPrimitiveKey.value = key
    editingPrimitiveField.value = field
    editingPrimitiveValue.value = localKnown[key] as string | number | boolean | undefined
  } else {
    // 新增模式：此入口暂不使用，预留
    editingPrimitiveKey.value = ''
    editingPrimitiveField.value = null
    editingPrimitiveValue.value = undefined
  }

  primitiveDialogVisible.value = true
}

/**
 * 确认 primitive 字段编辑
 */
const confirmPrimitiveEdit = (value: string | number | boolean) => {
  if (!editingPrimitiveField.value) return

  const fieldKey = editingPrimitiveField.value.key

  if (editingPrimitiveKey.value) {
    // 编辑模式：更新已存在字段的值
    localKnown[fieldKey] = value
  } else {
    // 新增模式：追加到列表末尾
    fieldOrder.value.push(fieldKey)
    localKnown[fieldKey] = value
  }
  emitAll()
}

// 监听 primitive 对话框关闭，清空状态
watch(primitiveDialogVisible, (visible) => {
  if (!visible) {
    editingPrimitiveKey.value = ''
    editingPrimitiveField.value = null
    editingPrimitiveValue.value = undefined
  }
})

/**
 * 根据 key 获取字段定义
 */
const getFieldByKey = (key: string): JsonFieldDefinition | null => {
  return props.fields.find((f) => f.key === key) || null
}

/**
 * 点击可选字段 Tag
 */
const onAvailableFieldClick = (fieldKey: string) => {
  const field = props.fields.find((f) => f.key === fieldKey)
  if (!field) return

  // i18n 字段打开 i18n 编辑对话框
  if (isI18nField(field)) {
    openI18nEdit(fieldKey)
    return
  }

  // 其他字段（包括 textarea）打开 primitive 编辑对话框（新增模式）
  editingPrimitiveKey.value = ''
  editingPrimitiveField.value = field
  editingPrimitiveValue.value = field.config.schema.defaultValue ?? getDefaultValueForField(field)
  primitiveDialogVisible.value = true
}

/**
 * 获取字段默认值
 */
const getDefaultValueForField = (field: JsonFieldDefinition): string | number | boolean => {
  const { defaultValue } = field.config.schema
  if (defaultValue !== undefined) return defaultValue

  switch (field.config.schema.type) {
    case 'string':
    case 'textarea':
      return ''
    case 'number':
      return 0
    case 'boolean':
      return false
    case 'select':
      const options = field.config.schema.options || []
      return options.length > 0 ? options[0].value : ''
    default:
      return ''
  }
}

/**
 * 判断是否为 i18n 字段
 */
const isI18nField = (field: JsonFieldDefinition | null): boolean => {
  return field?.config.schema.type === 'i18n'
}

/**
 * 判断给定 key 是否为 textarea 字段
 */
const isTextareaField = (field: JsonFieldDefinition | null): boolean => {
  return field?.config.schema.type === 'textarea'
}

/**
 * 判断是否为 primitive 字段（非 textarea）
 */
const isSimplePrimitiveField = (field: JsonFieldDefinition | null): boolean => {
  return Boolean(field && primitiveFieldTypes.has(field.config.schema.type) && field.config.schema.type !== 'textarea')
}

/**
 * 判断是否为 primitive 字段
 */
const isPrimitiveField = (field: JsonFieldDefinition | null): boolean => {
  return Boolean(field && primitiveFieldTypes.has(field.config.schema.type))
}

/**
 * 规范化 i18n 值
 */
const normalizeI18nValue = (value: unknown): I18nFieldValue => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return {}
  const next: I18nFieldValue = {}
  Object.entries(value as Record<string, unknown>).forEach(([k, v]) => {
    if (typeof v === 'string') next[k] = v
    else if (v != null) next[k] = String(v)
  })
  return next
}

/**
 * 当前正在编辑的 i18n 字段定义
 */
const editingI18nField = computed(() => {
  if (!editingI18nKey.value) return null
  const field = getFieldByKey(editingI18nKey.value)
  return isI18nField(field) ? field : null
})

/**
 * 同步本地状态
 */
const syncLocalState = () => {
  // 获取 props 中的所有 key
  const propsKeys = new Set(Object.keys(props.modelValue || {}))

  // 清理 localKnown 中已删除的 key
  Object.keys(localKnown).forEach((key) => {
    if (!propsKeys.has(key)) {
      delete localKnown[key]
    }
  })

  // 同步已知字段
  const oldKeys = new Set(Object.keys(localKnown))

  Object.entries(props.modelValue || {}).forEach(([key, value]) => {
    localKnown[key] = value
    // 如果是新字段，追加到顺序数组末尾
    if (!oldKeys.has(key) && !fieldOrder.value.includes(key)) {
      fieldOrder.value.push(key)
    }
  })

  // 清理已删除的字段
  const knownKeys = Object.keys(localKnown)
  fieldOrder.value = fieldOrder.value.filter(key => knownKeys.includes(key))

  // 同步未知字段
  Object.keys(localUnknown).forEach((key) => delete localUnknown[key])
  Object.entries(props.unknownFields || {}).forEach(([key, value]) => {
    localUnknown[key] = value
  })

  // 同步未知字段的草稿
  Object.keys(unknownDraft).forEach((key) => delete unknownDraft[key])
  Object.entries(localUnknown).forEach(([key, value]) => {
    unknownDraft[key] = formatUnknownValue(value)
  })
}

watch(
  () => [props.modelValue, props.unknownFields],
  () => {
    syncLocalState()
  },
  { immediate: true, deep: true }
)

/**
 * 触发所有事件
 */
const emitAll = () => {
  const known = { ...localKnown }
  const unknown = { ...localUnknown }

  emit('update:modelValue', known)
  emit('update:unknownFields', unknown)

  const merged = { ...unknown, ...known }
  emit('validation-change', validateBySchema(merged, props.fields, false))
}

/**
 * 更新已知字段
 */
const updateKnownField = (key: string, value: unknown) => {
  const field = getFieldByKey(key)
  const isI18n = isI18nField(field)

  // i18n 字段的空对象是有效的，不应该删除
  const isEmpty = value === '' || value === undefined || value === null
  const isEmptyI18n = isI18n && typeof value === 'object' && Object.keys(value || {}).length === 0

  if (isEmpty && !isEmptyI18n) {
    delete localKnown[key]
    fieldOrder.value = fieldOrder.value.filter(k => k !== key)
  } else {
    if (!(key in localKnown)) {
      fieldOrder.value.unshift(key)
    }
    localKnown[key] = value
  }
  emitAll()
}

/**
 * 获取字段显示标签
 */
const getFieldLabel = (field: JsonFieldDefinition | null): string => {
  if (!field) return '-'
  return field.label
}

/**
 * 格式化 primitive 字段值用于 Tag 显示（带省略）
 */
const formatPrimitiveValue = (key: string): string => {
  const value = localKnown[key]
  const field = getFieldByKey(key)
  if (!field) return String(value ?? '-')

  const { type, options } = field.config.schema

  // 空值处理
  if (value === undefined || value === null || value === '') {
    return '-'
  }

  // boolean 类型显示是/否
  if (typeof value === 'boolean') {
    return value
      ? t('jsonField.primitiveDisplay.booleanTrue')
      : t('jsonField.primitiveDisplay.booleanFalse')
  }

  // select 类型显示 option label
  if (type === 'select' && Array.isArray(options)) {
    const option = options.find((item: any) => item.value === value)
    if (option?.label) return option.label
  }

  const strValue = String(value)

  // 超过 20 个字符省略显示
  if (strValue.length > 20) {
    return strValue.slice(0, 20) + '...'
  }

  return strValue
}

/**
 * 打开 i18n 字段编辑对话框
 */
const openI18nEdit = (key: string) => {
  if (props.disabled) return
  const field = getFieldByKey(key)
  if (!isI18nField(field)) return

  editingI18nKey.value = key
  editingI18nValue.value = normalizeI18nValue(localKnown[key] || {})
  i18nDialogVisible.value = true
}

/**
 * 确认 i18n 编辑
 */
const confirmI18nEdit = (value: I18nFieldValue) => {
  if (!editingI18nKey.value) return
  updateKnownField(editingI18nKey.value, value)
  i18nDialogVisible.value = false
}

// 监听 i18n 对话框关闭，清空状态
watch(i18nDialogVisible, (visible) => {
  if (!visible) {
    editingI18nKey.value = ''
    editingI18nValue.value = {}
  }
})

/**
 * 删除字段
 */
const removeField = (key: string) => {
  delete localKnown[key]
  fieldOrder.value = fieldOrder.value.filter(k => k !== key)
  emitAll()
}

/**
 * 从 i18n 字段中删除单个语言
 */
const removeLocaleFromI18nField = (fieldKey: string, locale: string) => {
  const currentValue = localKnown[fieldKey]
  if (!currentValue || typeof currentValue !== 'object') return

  const newValue = { ...currentValue as Record<string, unknown> }
  delete newValue[locale]

  // 如果没有语言了，删除整个字段
  if (Object.keys(newValue).length === 0) {
    removeField(fieldKey)
  } else {
    localKnown[fieldKey] = newValue
    emitAll()
  }
}

/**
 * 格式化未知字段值
 */
const formatUnknownValue = (value: unknown): string => {
  if (typeof value === 'string') return value
  return JSON.stringify(value)
}

/**
 * 解析未知字段值
 */
const parseUnknownValue = (raw: string): unknown => {
  const text = raw.trim()
  if (!text) return ''

  try {
    return JSON.parse(text)
  } catch {
    return raw
  }
}

/**
 * 更新未知字段
 */
const updateUnknownField = (key: string, raw: string) => {
  unknownDraft[key] = raw
  const parsed = parseUnknownValue(raw)

  if (parsed === '') {
    delete localUnknown[key]
    delete unknownDraft[key]
  } else {
    localUnknown[key] = parsed
  }

  emitAll()
}

/**
 * 添加未知字段
 */
const addUnknownField = () => {
  const key = `custom_${Date.now()}_${Object.keys(localUnknown).length}`
  localUnknown[key] = ''
  unknownDraft[key] = ''
  emitAll()
}

/**
 * 删除未知字段
 */
const removeUnknownField = (key: string) => {
  delete localUnknown[key]
  delete unknownDraft[key]
  emitAll()
}

/**
 * 未知字段 key 列表
 */
const unknownKeys = computed(() => Object.keys(localUnknown))
</script>

<template>
  <div class="dynamic-json-form">
    <!-- 已添加的字段列表 -->
    <transition-group
      name="field-list"
      tag="div"
      class="field-list"
    >
      <!-- 简单 primitive 字段（非 textarea）使用 Tag 展示 -->
      <div
        v-for="key in addedFieldKeys.filter(k => isSimplePrimitiveField(getFieldByKey(k)))"
        :key="key"
        class="primitive-field-tag-wrapper"
      >
        <el-tag
          class="primitive-field-tag"
          :class="{ 'is-disabled': disabled }"
          :closable="!disabled"
          :disable-transitions="false"
          @click="openPrimitiveEdit(key)"
          @close="removeField(key)"
        >
          <span class="primitive-field-tag__label">
            {{ getFieldLabel(getFieldByKey(key)) }}:
          </span>
          <span class="primitive-field-tag__value">
            {{ formatPrimitiveValue(key) }}
          </span>
        </el-tag>
      </div>

      <!-- textarea 字段使用 TextareaDisplayCard（全宽） -->
      <div
        v-for="key in addedFieldKeys.filter(k => isTextareaField(getFieldByKey(k)))"
        :key="key"
        class="textarea-field-wrapper"
      >
        <TextareaDisplayCard
          :field="getFieldByKey(key)!"
          :model-value="localKnown[key] as string"
          :disabled="disabled"
          @edit="openPrimitiveEdit(key)"
          @remove="removeField(key)"
        />
      </div>

      <!-- i18n 字段使用 I18nDisplayCard（全宽） -->
      <div
        v-for="key in addedFieldKeys.filter(k => isI18nField(getFieldByKey(k)))"
        :key="key"
        class="i18n-field-wrapper"
      >
        <I18nDisplayCard
          :field="getFieldByKey(key)!"
          :model-value="normalizeI18nValue(localKnown[key])"
          :disabled="disabled"
          @edit="openI18nEdit(key)"
          @remove="removeField(key)"
          @remove-locale="(locale) => removeLocaleFromI18nField(key, locale)"
        />
      </div>
    </transition-group>

    <!-- 可选字段列表 -->
    <transition name="fade">
      <div
        v-if="availableFields.length > 0"
        class="add-field-section"
      >
        <div class="available-fields">
          <span class="available-fields__label">{{ t('jsonField.availableFields') }}:</span>
          <el-tag
            v-for="field in availableFields"
            :key="field.key"
            size="small"
            type="info"
            effect="plain"
            class="available-field-tag"
            @click="onAvailableFieldClick(field.key)"
          >
            {{ getFieldLabel(field) }}
          </el-tag>
        </div>
      </div>
    </transition>

    <!-- 未知字段区域 -->
    <div
      v-if="unknownKeys.length > 0"
      class="unknown-fields"
    >
      <div class="unknown-fields__header">
        <span class="unknown-fields__title">{{ t('jsonField.unknownFields') }}</span>
        <el-button
          link
          type="primary"
          size="small"
          :disabled="disabled"
          @click="addUnknownField"
        >
          <el-icon><Plus /></el-icon>
          {{ t('jsonField.addUnknownField') }}
        </el-button>
      </div>

      <div class="unknown-fields__list">
        <div
          v-for="key in unknownKeys"
          :key="key"
          class="unknown-field-item"
        >
          <span class="unknown-field-item__key">{{ key }}</span>
          <el-input
            class="unknown-field-item__value"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 3 }"
            :model-value="unknownDraft[key]"
            :disabled="disabled"
            @update:model-value="(value) => updateUnknownField(key, value)"
          />
          <el-button
            link
            size="small"
            class="ui-action-danger"
            :disabled="disabled"
            @click="removeUnknownField(key)"
          >
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
      </div>
    </div>

    <!-- i18n 编辑对话框 -->
    <I18nEditDialog
      v-model="i18nDialogVisible"
      :field="editingI18nField"
      :value="editingI18nValue"
      :disabled="disabled"
      @confirm="confirmI18nEdit"
    />

    <!-- primitive 字段编辑对话框 -->
    <PrimitiveEditDialog
      v-model="primitiveDialogVisible"
      :field="editingPrimitiveField"
      :value="editingPrimitiveValue"
      :disabled="disabled"
      @confirm="confirmPrimitiveEdit"
    />
  </div>
</template>

<style scoped>
/* 字段列表 - 简单流式布局 */
.field-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-start;
}

/* 字段包装器（全宽） */
.textarea-field-wrapper,
.i18n-field-wrapper {
  width: 100%;
}

/* Primitive 字段 Tag 包装器 */
.primitive-field-tag-wrapper {
  display: inline-flex;
}

.primitive-field-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;
}

.primitive-field-tag:hover {
  background-color: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
}

.primitive-field-tag.is-disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.primitive-field-tag.is-disabled:hover {
  background-color: transparent;
  border-color: var(--el-border-color);
}

.primitive-field-tag__label {
  font-weight: 500;
  color: var(--el-text-color-regular);
}

.primitive-field-tag__value {
  color: var(--el-text-color-secondary);
}

.primitive-field-tag.is-disabled .primitive-field-tag__label,
.primitive-field-tag.is-disabled .primitive-field-tag__value {
  cursor: not-allowed;
}

/* 添加字段区域 */
.add-field-section {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
}

/* 可选字段列表 */
.available-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  flex: 1;
  align-items: center;
}

.available-fields__label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-right: 4px;
}

.available-field-tag {
  cursor: pointer;
  transition: all 0.2s ease;
}

.available-field-tag:hover {
  background-color: var(--el-color-primary);
  border-color: var(--el-color-primary);
  color: #fff;
  transform: translateY(-1px);
}

/* 未知字段区域 */
.unknown-fields {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px dashed var(--el-border-color);
}

.unknown-fields__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.unknown-fields__title {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
}

.unknown-fields__list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.unknown-field-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px;
  background-color: var(--el-fill-color-light);
  border-radius: 8px;
  transition: all 0.2s ease;
}

.unknown-field-item:hover {
  background-color: var(--el-fill-color);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.unknown-field-item__key {
  flex-shrink: 0;
  min-width: 120px;
  max-width: 120px;
  padding: 4px 8px;
  font-size: 12px;
  font-family: 'Monaco', 'Courier New', monospace;
  color: var(--el-text-color-secondary);
  background-color: var(--el-fill-color-blank);
  border-radius: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.unknown-field-item__value {
  flex: 1;
  min-width: 0;
}

/* 过渡动画 */
.field-list-enter-active,
.field-list-leave-active {
  transition: all 0.3s ease;
}

.field-list-enter-from {
  opacity: 0;
  transform: translateY(-10px);
}

.field-list-leave-to {
  opacity: 0;
  transform: scale(0.9);
}

.field-list-move {
  transition: transform 0.3s ease;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.3s ease;
}

.slide-down-enter-from {
  opacity: 0;
  transform: translateY(-10px);
}

.slide-down-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

/* 响应式布局（可选，移动端单列） */
@media (max-width: 480px) {
  .i18n-field-card {
    min-width: 0;
    width: 100%;
  }

  .primitive-field-tag {
    font-size: 12px;
  }
}
</style>
