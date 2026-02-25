<script setup lang="ts">
import { computed, reactive, watch, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import StringField from '@/components/json-field/fields/StringField.vue'
import NumberField from '@/components/json-field/fields/NumberField.vue'
import BooleanField from '@/components/json-field/fields/BooleanField.vue'
import SelectField from '@/components/json-field/fields/SelectField.vue'
import I18nField from '@/components/json-field/fields/I18nField.vue'
import I18nDisplayCard from '@/components/json-field/fields/I18nDisplayCard.vue'
import I18nEditDialog from '@/components/json-field/fields/I18nEditDialog.vue'
import { validateBySchema } from '@/components/json-field/utils/validator'
import type {
  I18nFieldValue,
  JsonFieldDefinition,
  JsonObjectValue,
} from '@/components/json-field/types/json-field'

/**
 * 动态 JSON 表单组件（美化版本）
 *
 * 用户主动添加字段，卡片式布局
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

// 添加字段相关状态
const showAddField = ref(false)
const selectedFieldKey = ref<string>('')
const newFieldValue = ref<unknown>(undefined)

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
 * 当前选中的字段定义
 */
const selectedField = computed(() => {
  if (!selectedFieldKey.value) return null
  return props.fields.find((f) => f.key === selectedFieldKey.value) || null
})

/**
 * 已添加的字段列表（按添加顺序排序，新添加的在前）
 */
const addedFieldKeys = computed(() => {
  return fieldOrder.value.filter(key => key in localKnown)
})

/**
 * 确认添加字段
 */
const confirmAddField = () => {
  if (!selectedFieldKey.value) return

  const field = selectedField.value
  if (!field) return

  let value = newFieldValue.value
  if (value === undefined && field.config.schema.defaultValue !== undefined) {
    value = field.config.schema.defaultValue
  }

  if (value !== undefined && value !== '') {
    // 添加到顺序数组最前面
    fieldOrder.value.unshift(selectedFieldKey.value)
    localKnown[selectedFieldKey.value] = value
  }

  emitAll()
  cancelAddField()
}

/**
 * 快速添加字段（直接从可用字段列表点击）
 */
const quickAddField = (fieldKey: string) => {
  const field = props.fields.find((f) => f.key === fieldKey)
  if (!field) return

  // i18n 字段直接打开编辑对话框
  if (isI18nField(field)) {
    openI18nEdit(fieldKey)
    return
  }

  // 获取默认值，如果没有则根据类型设置合理的默认值
  let value = field.config.schema.defaultValue
  if (value === undefined) {
    // 根据字段类型设置默认值
    switch (field.config.schema.type) {
      case 'string':
      case 'textarea':
        value = ''
        break
      case 'number':
        value = 0
        break
      case 'boolean':
        value = false
        break
      case 'select':
        // 使用第一个选项
        const options = field.config.schema.options || []
        if (options.length > 0) {
          value = options[0].value
        }
        break
    }
  }

  // 只有获取到有效值才添加
  if (value !== undefined) {
    // 添加到顺序数组最前面
    fieldOrder.value.unshift(fieldKey)
    localKnown[fieldKey] = value
    emitAll()
  }
}

/**
 * 根据 key 获取字段定义
 */
const getFieldByKey = (key: string): JsonFieldDefinition | null => {
  return props.fields.find((f) => f.key === key) || null
}

/**
 * 判断是否为 i18n 字段
 */
const isI18nField = (field: JsonFieldDefinition | null): boolean => {
  return field?.config.schema.type === 'i18n'
}

/**
 * 判断给定 key 是否为 i18n 字段
 */
const isI18nFieldKey = (key: string): boolean => {
  return isI18nField(getFieldByKey(key))
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
  // 同步已知字段
  const oldKeys = new Set(Object.keys(localKnown))

  Object.entries(props.modelValue || {}).forEach(([key, value]) => {
    localKnown[key] = value
    // 如果是新字段，添加到顺序数组最前面
    if (!oldKeys.has(key) && !fieldOrder.value.includes(key)) {
      fieldOrder.value.unshift(key)
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
 * 根据字段类型获取对应组件
 */
const componentOf = (field: JsonFieldDefinition) => {
  switch (field.config.schema.type) {
    case 'string':
    case 'textarea':
      return StringField
    case 'number':
      return NumberField
    case 'boolean':
      return BooleanField
    case 'select':
      return SelectField
    case 'i18n':
      return I18nField
    default:
      return StringField
  }
}

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
const getFieldLabel = (field: JsonFieldDefinition): string => {
  return field.label
}

/**
 * 打开添加字段面板
 */
const openAddField = () => {
  showAddField.value = true
  selectedFieldKey.value = ''
  newFieldValue.value = undefined
}

/**
 * 取消添加字段
 */
const cancelAddField = () => {
  showAddField.value = false
  selectedFieldKey.value = ''
  newFieldValue.value = undefined
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
    <transition-group name="field-list" tag="div" class="field-list">
      <div
        v-for="key in addedFieldKeys"
        :key="key"
        class="field-card"
        :class="{ 'field-card--i18n': isI18nFieldKey(key) }"
      >
        <div class="field-card__header">
          <el-tag size="small" type="info" effect="plain">
            {{ getFieldLabel(getFieldByKey(key)!) }}
          </el-tag>
          <el-button
            link
            size="small"
            class="field-card__delete"
            :disabled="disabled"
            @click="removeField(key)"
          >
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
        <div class="field-card__body">
          <!-- i18n 字段使用只读展示卡片 -->
          <I18nDisplayCard
            v-if="isI18nFieldKey(key)"
            :model-value="normalizeI18nValue(localKnown[key])"
            :disabled="disabled"
            @edit="openI18nEdit(key)"
            @remove="removeField(key)"
            @remove-locale="(locale) => removeLocaleFromI18nField(key, locale)"
          />
          <!-- 其他字段使用正常编辑组件 -->
          <component
            v-else
            :is="componentOf(getFieldByKey(key)!)"
            :field="getFieldByKey(key)!"
            :model-value="localKnown[key] as string | number | boolean | I18nFieldValue"
            :disabled="disabled"
            @update:model-value="(value: string | number | boolean | I18nFieldValue) => updateKnownField(key, value)"
          />
        </div>
      </div>
    </transition-group>

    <!-- 添加字段按钮 -->
    <transition name="fade">
      <div v-if="!showAddField && availableFields.length > 0" class="add-field-section">
        <!-- 可选字段列表 -->
        <div class="available-fields">
          <span class="available-fields__label">{{ t('jsonField.availableFields') }}:</span>
          <el-tag
            v-for="field in availableFields"
            :key="field.key"
            size="small"
            type="info"
            effect="plain"
            class="available-field-tag"
            @click="quickAddField(field.key)"
          >
            {{ getFieldLabel(field) }}
          </el-tag>
        </div>
        <el-button
          class="add-field-btn"
          size="small"
          :disabled="disabled"
          @click="openAddField"
        >
          <el-icon><Plus /></el-icon>
        </el-button>
      </div>
    </transition>

    <!-- 添加字段面板 -->
    <transition name="slide-down">
      <div v-if="showAddField" class="add-field-panel">
        <div class="add-field-panel__row">
          <el-select
            v-model="selectedFieldKey"
            :placeholder="t('jsonField.selectFieldPlaceholder')"
            size="default"
            @change="() => { newFieldValue = undefined }"
          >
            <el-option
              v-for="field in availableFields"
              :key="field.key"
              :label="getFieldLabel(field)"
              :value="field.key"
            />
          </el-select>
        </div>

        <transition name="fade">
          <div v-if="selectedField" class="add-field-panel__row">
            <component
              :is="componentOf(selectedField)"
              :field="selectedField"
              :model-value="newFieldValue as string | number | boolean | I18nFieldValue | undefined"
              :disabled="disabled"
              @update:model-value="(value: string | number | boolean | I18nFieldValue) => { newFieldValue = value }"
            />
          </div>
        </transition>

        <div class="add-field-panel__actions">
          <el-button size="small" @click="cancelAddField">
            {{ t('common.cancel') }}
          </el-button>
          <el-button
            type="primary"
            size="small"
            :disabled="!selectedFieldKey"
            @click="confirmAddField"
          >
            {{ t('common.confirm') }}
          </el-button>
        </div>
      </div>
    </transition>

    <!-- 未知字段区域 -->
    <div v-if="unknownKeys.length > 0" class="unknown-fields">
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
  </div>
</template>

<style scoped>
/* 字段列表 - 简单流式布局 */
.field-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

/* 字段卡片 */
.field-card {
  background: #fff;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 10px;
  transition: all 0.3s ease;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  width: 150px;
  flex-shrink: 0;
}

.field-card:hover {
  border-color: var(--el-color-primary);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.field-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.field-card__delete {
  color: var(--el-text-color-secondary);
  transition: color 0.2s ease;
}

.field-card__delete:hover {
  color: var(--el-color-danger);
}

/* i18n 字段 header 特殊样式 */
.field-card__i18n-count {
  margin-left: 8px;
}

/* i18n 字段卡片特殊样式：更宽，占半行 */
.field-card--i18n {
  width: calc(50% - 6px);
  min-width: 360px;
  flex-grow: 1;
}

.field-card__delete:hover {
  color: var(--el-color-danger);
}

.field-card__body {
  flex: 1;
  min-height: 0;
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

/* 添加字段按钮 - 小尺寸 */
.add-field-btn {
  flex-shrink: 0;
  padding: 8px 12px;
  border: 1px dashed var(--el-border-color);
  background-color: var(--el-fill-color-blank);
  transition: all 0.2s ease;
}

.add-field-btn:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.add-field-btn:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
  background-color: var(--el-color-primary-light-9);
  border-style: solid;
}

.add-field-btn .el-icon {
  margin-right: 6px;
  font-size: 18px;
}

/* 添加字段面板 */
.add-field-panel {
  width: 100%;
  padding: 20px;
  background: linear-gradient(135deg, #f5f7fa 0%, #fafbfc 100%);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.add-field-panel__row {
  margin-bottom: 16px;
}

.add-field-panel__row:last-of-type {
  margin-bottom: 0;
}

.add-field-panel__actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
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
  width: 260px;
  flex-shrink: 0;
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
  .field-card {
    width: 100%;
  }

  .field-card--i18n {
    min-width: 0;
  }

  .unknown-field-item {
    width: 100%;
  }
}
</style>
