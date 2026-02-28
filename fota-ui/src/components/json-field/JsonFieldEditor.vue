<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import DynamicJsonForm from '@/components/json-field/DynamicJsonForm.vue'
import type { JsonFieldDefinition, JsonObjectValue } from '@/components/json-field/types/json-field'
import { loadJsonFieldSchema } from '@/components/json-field/utils/schema-loader'
import {
  applyDefaults,
  mergeKnownAndUnknown,
  safeParseJsonObject,
  splitKnownAndUnknown,
  stringifyJsonObject,
} from '@/components/json-field/utils/data-converter'
import { validateBySchema } from '@/components/json-field/utils/validator'

/**
 * JSON 字段编辑器组件
 *
 * 提供双模式编辑（表单模式 + 代码模式），支持自动降级
 */
interface Props {
  /** 字典类型编码（如 'json_schema.device_tags'） */
  dictTypeCode: string
  /** JSON 字符串（v-model） */
  modelValue?: string
  /** 默认模式 */
  mode?: 'form' | 'code'
  /** 是否允许切换模式 */
  allowModeSwitch?: boolean
  /** 是否禁用 */
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  mode: 'form',
  allowModeSwitch: true,
  disabled: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'validation-change', errors: string[]): void
}>()

const { t } = useI18n()

// 状态管理
const loading = ref(false)
const loadError = ref('')
const definitions = ref<JsonFieldDefinition[]>([])
const currentMode = ref<'form' | 'code'>(props.mode)
const codeText = ref(props.modelValue || '')
const knownData = ref<JsonObjectValue>({})
const unknownData = ref<JsonObjectValue>({})

/**
 * 是否可以使用表单模式
 */
const canUseFormMode = computed(
  () => !loading.value && !loadError.value && definitions.value.length > 0
)

/**
 * 格式化校验错误信息
 */
const normalizeValidationError = (raw: string): string => {
  const parts = raw.split('::')
  const code = parts[0]
  const field = parts[1]
  const arg = parts[2]

  switch (code) {
    case 'jsonField.errorInvalidJson':
      return t('jsonField.errorInvalidJson')
    case 'jsonField.errorMustObject':
      return t('jsonField.errorMustObject')
    case 'jsonField.errorRequired':
      return t('jsonField.errorRequired', { field })
    case 'jsonField.errorTypeString':
      return t('jsonField.errorTypeString', { field })
    case 'jsonField.errorTypeNumber':
      return t('jsonField.errorTypeNumber', { field })
    case 'jsonField.errorTypeBoolean':
      return t('jsonField.errorTypeBoolean', { field })
    case 'jsonField.errorInvalidOption':
      return t('jsonField.errorInvalidOption', { field })
    case 'jsonField.errorPattern':
      return t('jsonField.errorPattern', { field })
    case 'jsonField.errorMin':
      return t('jsonField.errorMin', { field, min: arg })
    case 'jsonField.errorMax':
      return t('jsonField.errorMax', { field, max: arg })
    case 'jsonField.errorMinLength':
      return t('jsonField.errorMinLength', { field, min: arg })
    case 'jsonField.errorMaxLength':
      return t('jsonField.errorMaxLength', { field, max: arg })
    case 'jsonField.errorUnknownField':
      return t('jsonField.errorUnknownField', { field })
    case 'jsonField.errorUnknownValueNotPrimitive':
      return t('jsonField.errorUnknownValueNotPrimitive', { field })
    default:
      return raw
  }
}

/**
 * 触发校验事件
 */
const emitValidation = (errors: string[]) => {
  emit('validation-change', errors.map(normalizeValidationError))
}

/**
 * 从 modelValue 同步表单状态
 */
const syncFormStateFromModel = () => {
  const parsed = safeParseJsonObject(props.modelValue)

  if (!parsed.ok) {
    // JSON 解析失败，切换到代码模式
    codeText.value = props.modelValue || ''
    if (currentMode.value === 'form') {
      currentMode.value = 'code'
    }
    emitValidation([parsed.error])
    return
  }

  // 分离已知字段和未知字段
  const split = splitKnownAndUnknown(parsed.data, definitions.value)
  knownData.value = applyDefaults(split.known, definitions.value)
  unknownData.value = split.unknown

  // 更新代码模式的文本
  codeText.value = stringifyJsonObject(
    mergeKnownAndUnknown(knownData.value, unknownData.value),
    true
  )

  // 触发校验
  emitValidation(
    validateBySchema(
      mergeKnownAndUnknown(knownData.value, unknownData.value),
      definitions.value,
      false
    )
  )
}

/**
 * 加载 Schema
 */
const loadSchema = async () => {
  loading.value = true
  loadError.value = ''

  try {
    definitions.value = await loadJsonFieldSchema(props.dictTypeCode)

    if (definitions.value.length === 0) {
      // 没有配置字段，降级到代码模式
      loadError.value = t('jsonField.schemaEmpty')
      currentMode.value = 'code'
      emitValidation([])
      return
    }

    syncFormStateFromModel()

    // 同步默认值到父组件
    // 如果 modelValue 为空，应用默认值后需要立即同步出去
    if (!props.modelValue || !props.modelValue.trim()) {
      emitFromForm()
    }
  } catch (error) {
    // 加载失败，降级到代码模式
    loadError.value = t('jsonField.schemaLoadFailed')
    currentMode.value = 'code'
    emitValidation([])
  } finally {
    loading.value = false
  }
}

/**
 * 从表单模式触发数据更新
 */
const emitFromForm = () => {
  const merged = mergeKnownAndUnknown(knownData.value, unknownData.value)
  const normalized = stringifyJsonObject(merged)

  codeText.value = stringifyJsonObject(merged, true)
  emit('update:modelValue', normalized)

  emitValidation(validateBySchema(merged, definitions.value, false))
}

/**
 * 代码模式实时校验（不格式化）
 */
const validateCodeText = (value: string) => {
  const parsed = safeParseJsonObject(value)
  if (!parsed.ok) {
    emitValidation([parsed.error])
    return
  }

  emitValidation(validateBySchema(parsed.data, definitions.value, false))
}

/**
 * 处理代码模式输入
 */
const onCodeInput = (value: string) => {
  codeText.value = value
  emit('update:modelValue', value)
  validateCodeText(value)
}

/**
 * 代码输入框失焦时格式化（静默，不弹错误提示）
 */
const onCodeBlur = () => {
  formatCode(false)
}

/**
 * 格式化 JSON 代码
 */
const formatCode = (showMessage = true) => {
  // 空值检查
  if (!codeText.value?.trim()) {
    if (showMessage) {
      ElMessage.warning(t('jsonField.noContentToFormat'))
    }
    return
  }

  try {
    const parsed = JSON.parse(codeText.value)

    // 仅 JSON 对象允许进入 schema 校验
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      emitValidation([t('jsonField.errorMustObject')])
      return
    }

    const formatted = JSON.stringify(parsed, null, 2)
    if (formatted !== codeText.value) {
      codeText.value = formatted
      emit('update:modelValue', formatted)
    }

    emitValidation(validateBySchema(parsed, definitions.value, false))
  } catch (error) {
    if (showMessage) {
      ElMessage.error(t('jsonField.errorInvalidJson'))
    }
  }
}

// 监听 modelValue 变化
watch(
  () => props.modelValue,
  () => {
    if (props.modelValue !== codeText.value) {
      codeText.value = props.modelValue || ''
    }

    if (definitions.value.length === 0) {
      return
    }

    // 仅在表单模式下做"解析 + 回填 + 美化字符串"，避免代码模式输入被自动格式化
    if (currentMode.value === 'form' && canUseFormMode.value) {
      syncFormStateFromModel()
      return
    }

    // 代码模式下仅做校验，不改写输入内容
    validateCodeText(codeText.value)
  }
)

// 监听 mode 属性变化
watch(
  () => props.mode,
  (value) => {
    currentMode.value = value
  }
)

// 监听 dictTypeCode 变化，重新加载 Schema
watch(
  () => props.dictTypeCode,
  () => {
    void loadSchema()
  }
)

// 监听模式切换
watch(
  () => currentMode.value,
  (mode) => {
    if (mode === 'form' && canUseFormMode.value) {
      syncFormStateFromModel()
      emitFromForm()
    }
  }
)

// 组件挂载时加载 Schema
onMounted(() => {
  void loadSchema()
})
</script>

<template>
  <div class="json-field-editor">
    <!-- 模式切换图标按钮 -->
    <div
      v-if="allowModeSwitch"
      class="json-field-editor__toolbar"
    >
      <el-tooltip
        :content="t('jsonField.formMode')"
        placement="top"
      >
        <el-button
          :type="currentMode === 'form' ? 'primary' : ''"
          :disabled="!canUseFormMode"
          size="small"
          @click="currentMode = 'form'"
        >
          <el-icon><Grid /></el-icon>
        </el-button>
      </el-tooltip>
      <el-tooltip
        :content="t('jsonField.codeMode')"
        placement="top"
      >
        <el-button
          :type="currentMode === 'code' ? 'primary' : ''"
          size="small"
          @click="currentMode = 'code'"
        >
          <el-icon><DocumentCopy /></el-icon>
        </el-button>
      </el-tooltip>
    </div>

    <!-- 降级提示 -->
    <el-alert
      v-if="loadError"
      type="warning"
      class="mb-2"
      :closable="false"
      :title="t('jsonField.fallbackToCodeMode')"
      :description="loadError"
    />

    <!-- 内容区域（可滚动） -->
    <div class="json-field-editor__content">
      <!-- 加载骨架屏 -->
      <el-skeleton
        v-if="loading"
        :rows="3"
        animated
      />

      <!-- 表单模式 -->
      <DynamicJsonForm
        v-else-if="currentMode === 'form' && canUseFormMode"
        v-model="knownData"
        :fields="definitions"
        :unknown-fields="unknownData"
        :disabled="disabled"
        @update:model-value="
          (value) => {
            knownData = value
            emitFromForm()
          }
        "
        @update:unknown-fields="
          (value) => {
            unknownData = value
            emitFromForm()
          }
        "
        @validation-change="emitValidation"
      />

      <!-- 代码模式 -->
      <div
        v-else
        class="code-mode-wrapper"
      >
        <div class="code-mode-wrapper__toolbar">
          <span class="code-mode-wrapper__title">{{ t('jsonField.codeModeTitle') }}</span>
          <el-tooltip
            :content="t('jsonField.formatJson')"
            placement="top"
          >
            <el-button
              class="code-format-btn"
              size="small"
              :disabled="disabled"
              @click="formatCode"
            >
              <el-icon><MagicStick /></el-icon>
              <span class="code-format-btn__text">{{ t('jsonField.formatJson') }}</span>
            </el-button>
          </el-tooltip>
        </div>
        <el-input
          :model-value="codeText"
          :disabled="disabled"
          type="textarea"
          :autosize="{ minRows: 3, maxRows: 10 }"
          :placeholder="t('jsonField.codePlaceholder')"
          class="code-textarea"
          @update:model-value="onCodeInput"
          @blur="onCodeBlur"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.json-field-editor__toolbar {
  margin-bottom: 10px;
}

.json-field-editor__content {
  max-height: 300px;
  overflow-y: auto;
  padding-right: 4px;
}

/* 自定义滚动条样式 */
.json-field-editor__content::-webkit-scrollbar {
  width: 6px;
}

.json-field-editor__content::-webkit-scrollbar-track {
  background: var(--el-fill-color-light);
  border-radius: 3px;
}

.json-field-editor__content::-webkit-scrollbar-thumb {
  background: var(--el-border-color-darker);
  border-radius: 3px;
}

.json-field-editor__content::-webkit-scrollbar-thumb:hover {
  background: var(--el-border-color-dark);
}

/* 代码模式样式 */
.code-mode-wrapper {
  display: flex;
  flex-direction: column;
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  /* 抵消父容器的 padding-right，实现完全占满 */
  margin-right: -4px;
  padding-right: 4px;
}

.code-mode-wrapper__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 12px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color);
  border-bottom: 0;
  border-radius: 8px 8px 0 0;
}

.code-mode-wrapper__title {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--el-text-color-secondary);
  user-select: none;
  text-transform: uppercase;
}

.code-format-btn {
  --el-button-bg-color: transparent;
  --el-button-text-color: var(--el-text-color-regular);
  --el-button-border-color: var(--el-border-color);
  --el-button-hover-bg-color: var(--el-fill-color);
  --el-button-hover-text-color: var(--el-color-primary);
  --el-button-hover-border-color: var(--el-color-primary-light-5);
  --el-button-active-bg-color: var(--el-fill-color-dark);
  --el-button-active-border-color: var(--el-color-primary);
  --el-button-disabled-bg-color: transparent;
  --el-button-disabled-border-color: var(--el-border-color-lighter);
  --el-button-disabled-text-color: var(--el-text-color-placeholder);
  font-weight: 500;
  border-radius: 6px;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.code-format-btn__text {
  margin-left: 4px;
}

.code-format-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: var(--el-box-shadow-light);
}

.code-textarea {
  display: block;
  width: 100%;
  min-width: 0;
}

.code-textarea :deep(.el-textarea) {
  display: block;
  width: 100%;
}

.code-textarea :deep(.el-textarea__inner) {
  display: block;
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  font-family: 'Monaco', 'Menlo', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  padding: 12px;
  border-radius: 0 0 8px 8px;
  border-top-left-radius: 0;
  border-top-right-radius: 0;
  border-color: var(--el-border-color);
  resize: vertical;
}

.code-textarea :deep(.el-textarea__inner:focus) {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 1px var(--el-color-primary-light-7) inset;
}

.code-textarea :deep(.el-textarea__inner::placeholder) {
  color: var(--el-text-color-placeholder);
  opacity: 0.6;
}
</style>
