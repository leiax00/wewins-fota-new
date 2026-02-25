<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { JsonFieldDefinition } from '@/components/json-field/types/json-field'
import type { I18nFieldValue } from '@/components/json-field/types/json-field'

/**
 * I18n 字段展示卡片组件
 *
 * 全宽展示，多语言内容，可展开/折叠
 */
interface Props {
  field: JsonFieldDefinition
  modelValue?: I18nFieldValue
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => ({}),
  disabled: false,
})

const emit = defineEmits<{
  (e: 'edit'): void
  (e: 'remove'): void
  (e: 'removeLocale', locale: string): void
}>()

const { t, te } = useI18n()

const tr = (key: string, fallback: string, params?: Record<string, unknown>) => {
  return te(key) ? t(key, params || {}) : fallback
}

const collapsed = ref(true)

const entries = computed(() => Object.entries(props.modelValue || {}))

const localeCountText = computed(() =>
  tr('jsonField.i18nField.localeCount', '{count} 个语言', { count: entries.value.length })
)

const resolveLocaleLabel = (code: string): string => {
  const labelMap: Record<string, string> = {
    'zh-CN': '简体中文',
    'en-US': 'English',
    'ja-JP': '日本語',
    'ko-KR': '한국어',
    'de-DE': 'Deutsch',
    'fr-FR': 'Français',
    'es-ES': 'Español',
    'it-IT': 'Italiano',
    'pt-PT': 'Português',
    'ru-RU': 'Русский',
    'ar-SA': 'العربية',
    'zh-TW': '繁體中文',
  }
  return labelMap[code] || code
}

const toggleCollapse = (event: MouseEvent) => {
  event.stopPropagation()
  collapsed.value = !collapsed.value
}

const removeLocale = (locale: string, event: MouseEvent) => {
  event.stopPropagation()
  emit('removeLocale', locale)
}

const onEdit = () => {
  emit('edit')
}

const onRemove = (event: MouseEvent) => {
  event.stopPropagation()
  emit('remove')
}
</script>

<template>
  <div class="i18n-display-card" @click="onEdit">
    <div v-if="entries.length > 0">
      <!-- 第一行：字段名 + 删除按钮 -->
      <div class="i18n-display-card__header">
        <el-tag size="small" type="info" effect="plain">
          {{ field.label }}
        </el-tag>
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

      <!-- 第二行：语言数 + 展开/折叠按钮 -->
      <div class="i18n-display-card__subtitle">
        <el-tag size="small" type="success" effect="plain">
          {{ localeCountText }}
        </el-tag>
        <el-button
          link
          size="small"
          :disabled="disabled"
          @click="toggleCollapse"
        >
          <span class="i18n-display-card__toggle-text">
            {{ collapsed ? tr('jsonField.i18nField.expand', '展开') : tr('jsonField.i18nField.collapse', '收起') }}
          </span>
          <el-icon>
            <ArrowDown v-if="collapsed" />
            <ArrowUp v-else />
          </el-icon>
        </el-button>
      </div>

      <!-- 语言列表 -->
      <el-collapse-transition>
        <div v-show="!collapsed" class="i18n-display-card__list">
          <div v-for="[locale, text] in entries" :key="locale" class="i18n-display-item">
            <span class="i18n-display-item__label">{{ resolveLocaleLabel(locale) }}:</span>
            <span class="i18n-display-item__text" :title="text || ''">{{ text || '-' }}</span>
            <el-button
              link
              size="small"
              class="ui-action-danger i18n-display-item__delete"
              :disabled="disabled"
              @click="(e) => removeLocale(locale, e)"
            >
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
        </div>
      </el-collapse-transition>
    </div>

    <div v-else class="i18n-display-card__empty">
      <div class="i18n-display-card__header">
        <el-tag size="small" type="info" effect="plain">
          {{ field.label }}
        </el-tag>
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
      <div class="i18n-display-card__subtitle i18n-display-card__subtitle--empty">
        <el-tag size="small" type="info" effect="plain">
          {{ localeCountText }}
        </el-tag>
        <span class="i18n-display-card__empty-text">
          {{ tr('jsonField.i18nField.empty', '暂无语言条目') }}
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.i18n-display-card {
  background: #fff;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 10px;
  transition: all 0.3s ease;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  cursor: pointer;
  width: 100%;
}

.i18n-display-card:hover {
  border-color: var(--el-color-primary);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.i18n-display-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.i18n-display-card__subtitle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.i18n-display-card__subtitle--empty {
  justify-content: flex-start;
}

.i18n-display-card__toggle-text {
  font-size: 12px;
}

.i18n-display-card__list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.i18n-display-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  transition: background 0.2s ease;
}

.i18n-display-item:hover {
  background: var(--el-fill-color);
}

.i18n-display-item__label {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.i18n-display-item__text {
  flex: 1;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-regular);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.i18n-display-item__delete {
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
  transition: color 0.2s ease;
}

.i18n-display-item__delete:hover {
  color: var(--el-color-danger);
}

.i18n-display-card__empty {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.i18n-display-card__empty-text {
  flex: 1;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  word-break: break-word;
}
</style>
