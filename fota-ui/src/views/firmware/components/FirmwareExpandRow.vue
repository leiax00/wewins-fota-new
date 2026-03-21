<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/date'
import { Clock, Document, Files, Box } from '@element-plus/icons-vue'
import {
  formatFileSize,
  getPackageStatusTagType,
  parseJsonObject,
  isI18nField,
  getLocaleLabel,
  isBlockField,
  hasNormalMetaFields,
  getFieldLabel,
} from '../utils/formatters'
import type { FirmwareVersionItem } from '@/api/firmware'
import type { JsonFieldDefinition } from '@/components/json-field/types/json-field'

const { t } = useI18n()
const userStore = useUserStore()

const props = defineProps<{
  row: FirmwareVersionItem
  tagsSchema: JsonFieldDefinition[]
  metaSchema: JsonFieldDefinition[]
}>()

const emit = defineEmits<{
  (e: 'warm-cdn', row: FirmwareVersionItem): void
}>()

const parsedTags = computed(() => parseJsonObject(props.row.tags))
const parsedMeta = computed(() => parseJsonObject(props.row.meta))

const canWarmCdn = computed(() => {
  return userStore.hasPermission('fota:firmware:update') && props.row.packageStatus === 'READY'
})

const handleWarmCdn = () => {
  emit('warm-cdn', props.row)
}

const getTagLabel = (key: string) => getFieldLabel(props.tagsSchema, key)
const getMetaLabel = (key: string) => getFieldLabel(props.metaSchema, key)
</script>

<template>
  <div class="firmware-expand-content">
    <!-- 顶部：基本信息 -->
    <div class="expand-header">
      <div class="expand-header__row">
        <div class="expand-header__item">
          <span class="expand-header__label">{{ t('firmware.version') }}</span>
          <span class="expand-header__value text-lg font-semibold">{{ row.version }}</span>
        </div>
        <div v-if="row.internalVersion" class="expand-header__item">
          <span class="expand-header__label">{{ t('firmware.internalVersion') }}</span>
          <span class="expand-header__value font-mono">{{ row.internalVersion }}</span>
        </div>
        <div class="expand-header__item">
          <span class="expand-header__label">{{ t('firmware.product') }}</span>
          <span class="expand-header__value">{{ row.productName || row.productId }}</span>
        </div>
        <div class="expand-header__item">
          <span class="expand-header__label">{{ t('firmware.packageStatus') }}</span>
          <el-tag
            :type="getPackageStatusTagType(row.packageStatus)"
            size="large"
          >
            {{ row.packageStatus === 'READY' ? t('firmware.packageStatusReady') : row.packageStatus === 'NONE' ? t('firmware.packageStatusNone') : row.packageStatus }}
          </el-tag>
        </div>
      </div>
    </div>

    <!-- 中间：两栏布局 -->
    <div class="expand-body">
      <!-- 左侧：文件信息 + 短标签 -->
      <div class="expand-left">
        <!-- 文件信息 -->
        <div class="expand-section">
          <div class="expand-section__title">
            <el-icon><Files /></el-icon>
            <span>{{ t('firmware.fileInfo') }}</span>
            <el-tag v-if="row.packageStatus" size="small" :type="getPackageStatusTagType(row.packageStatus)" class="ml-2">
              {{ row.packageStatus === 'READY' ? t('firmware.packageStatusReady') : t('firmware.packageStatusNone') }}
            </el-tag>
          </div>
          <div class="expand-section__content">
            <template v-if="row.packageStatus === 'READY'">
              <div class="info-row">
                <span class="info-row__label">{{ t('firmware.fileName') }}</span>
                <span class="info-row__value font-mono">{{ row.fileUrl ? row.fileUrl.split('/').pop() : '-' }}</span>
              </div>
              <div class="info-row">
                <span class="info-row__label">{{ t('firmware.fileSize') }}</span>
                <span class="info-row__value">{{ row.fileSize ? formatFileSize(row.fileSize) : '-' }}</span>
              </div>
              <div class="info-row">
                <span class="info-row__label">{{ t('firmware.md5') }}</span>
                <span class="info-row__value info-row__value--hash">{{ row.md5 || '-' }}</span>
              </div>
              <div class="info-row">
                <span class="info-row__label">{{ t('firmware.sha256') }}</span>
                <span class="info-row__value info-row__value--hash">{{ row.sha256 || '-' }}</span>
              </div>
              <!-- CDN 预热操作 -->
              <div class="info-row info-row--action">
                <span class="info-row__label">{{ t('firmware.warm.cdn') }}</span>
                <el-button
                  type="warning"
                  size="small"
                  :disabled="!canWarmCdn"
                  @click.stop="handleWarmCdn"
                >
                  {{ t('firmware.warm.action') }}
                </el-button>
              </div>
            </template>
            <template v-else>
              <div class="empty-placeholder">
                <el-icon :size="24"><Box /></el-icon>
                <span>{{ t('firmware.noFilePackage') }}</span>
              </div>
            </template>
          </div>
        </div>

        <!-- 标签 (紧凑展示) -->
        <div v-if="parsedTags && Object.keys(parsedTags).length > 0" class="expand-section expand-section--compact">
          <div class="expand-section__title">
            <el-icon><Box /></el-icon>
            <span>{{ t('firmware.tags') }}</span>
            <el-tag size="small" type="info" effect="plain" class="ml-2">
              {{ Object.keys(parsedTags).length }} {{ t('firmware.fieldsCount') }}
            </el-tag>
          </div>
          <div class="expand-section__content">
            <div class="tags-compact">
              <div
                v-for="(value, key) in parsedTags"
                :key="key"
                class="tag-compact"
              >
                <span class="tag-compact__key">{{ getTagLabel(String(key)) }}</span>
                <span class="tag-compact__value">{{ String(value) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧：元数据 (i18n 专门渲染) -->
      <div class="expand-right">
        <div v-if="parsedMeta" class="expand-section">
          <div class="expand-section__title">
            <el-icon><Document /></el-icon>
            <span>{{ t('firmware.meta') }}</span>
            <el-tag size="small" type="info" effect="plain" class="ml-2">
              {{ Object.keys(parsedMeta).length }} {{ t('firmware.fieldsCount') }}
            </el-tag>
          </div>
          <div class="expand-section__content">
            <!-- 流式堆叠区域：普通字段 -->
            <div v-if="hasNormalMetaFields(row.meta, metaSchema)" class="meta-flow">
              <template v-for="(value, key) in parsedMeta" :key="key">
                <div
                  v-if="!isI18nField(value) && !isBlockField(metaSchema, String(key), value)"
                  class="meta-flow__item"
                >
                  <span class="meta-flow__label">{{ getMetaLabel(String(key)) }}</span>
                  <span class="meta-flow__value">{{ String(value) }}</span>
                </div>
              </template>
            </div>
            <!-- 独占一行区域：i18n 和 textarea 字段 -->
            <template v-for="(value, key) in parsedMeta" :key="key">
              <!-- i18n 字段特殊渲染 -->
              <div v-if="isI18nField(value)" class="meta-item meta-item--full">
                <div class="meta-item__header">
                  <el-tag size="small" type="primary" effect="plain">{{ getMetaLabel(String(key)) }}</el-tag>
                  <span class="meta-item__badge">{{ t('firmware.i18nField') }}</span>
                </div>
                <div class="i18n-list">
                  <div
                    v-for="(text, locale) in value"
                    :key="locale"
                    class="i18n-item"
                  >
                    <el-tag size="small" type="info" effect="plain" class="i18n-item__locale">
                      {{ getLocaleLabel(locale) }}
                    </el-tag>
                    <span class="i18n-item__text">{{ text }}</span>
                  </div>
                </div>
              </div>
              <!-- textarea 字段 -->
              <div v-else-if="isBlockField(metaSchema, String(key), value)" class="meta-item meta-item--full">
                <div class="meta-item__header">
                  <el-tag size="small" type="warning" effect="plain">{{ getMetaLabel(String(key)) }}</el-tag>
                </div>
                <div class="meta-item__textarea">{{ String(value) }}</div>
              </div>
            </template>
          </div>
        </div>
        <div v-else class="expand-section expand-section--empty">
          <div class="expand-section__title">
            <el-icon><Document /></el-icon>
            <span>{{ t('firmware.meta') }}</span>
          </div>
          <div class="expand-section__content">
            <div class="empty-placeholder empty-placeholder--small">
              <span>{{ t('firmware.noMeta') }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部：审计信息 -->
    <div class="expand-footer">
      <div class="audit-item">
        <el-icon class="audit-icon"><Clock /></el-icon>
        <span class="audit-label">{{ t('firmware.uploadTime') }}</span>
        <span class="audit-time">{{ formatDateTime(row.createdAt) }}</span>
      </div>
      <div v-if="row.updatedAt && row.updatedAt !== row.createdAt" class="audit-item">
        <el-icon class="audit-icon"><Clock /></el-icon>
        <span class="audit-label">{{ t('common.updateTime') }}</span>
        <span class="audit-time">{{ formatDateTime(row.updatedAt) }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.firmware-expand-content {
  padding: 20px 24px;
  background: var(--bg-hover);
}

.dark .firmware-expand-content {
  background: var(--surface-fill);
}

/* 顶部信息栏 */
.expand-header {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-bottom: 20px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--border-light);
}

.expand-header__row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 32px;
}

.expand-header__row--compact {
  gap: 24px;
  margin-top: 4px;
}

.expand-header__item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.expand-header__item--compact {
  gap: 8px;
}

.expand-header__label {
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
}

.expand-header__value {
  font-size: 15px;
  color: var(--text-primary);
}

/* 中间两栏布局 */
.expand-body {
  display: grid;
  grid-template-columns: 1fr 1.5fr;
  gap: 20px;
  margin-bottom: 20px;
}

.expand-left,
.expand-right {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 区块样式 */
.expand-section {
  background: var(--bg-card);
  border-radius: 10px;
  border: 1px solid var(--border-light);
  overflow: hidden;
  transition: all 0.2s ease;
}

.expand-section:hover {
  border-color: var(--border-color);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.dark .expand-section:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.25);
}

.expand-section--compact {
  flex: 1;
}

.expand-section--empty {
  opacity: 0.7;
}

.expand-section--full {
  grid-column: 1 / -1;
}

.expand-section__title {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 18px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  background: var(--el-fill-color-light);
  border-bottom: 1px solid var(--border-light);
}

.expand-section__content {
  padding: 16px 18px;
}

/* 信息行 */
.info-row {
  display: flex;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid var(--border-lighter);
}

.info-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.info-row:first-child {
  padding-top: 0;
}

.info-row--action {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--border-light);
  border-bottom: none;
}

.info-row__label {
  flex-shrink: 0;
  width: 80px;
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
}

.info-row__value {
  flex: 1;
  font-size: 13px;
  color: var(--text-primary);
  word-break: break-all;
}

.info-row__value--hash {
  font-family: 'SF Mono', 'Courier New', monospace;
  font-size: 11px;
  background: var(--el-fill-color);
  padding: 6px 10px;
  border-radius: 6px;
  word-break: break-all;
  color: var(--text-secondary);
}

/* 空状态占位 */
.empty-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 32px;
  color: var(--text-secondary);
  font-size: 13px;
}

.empty-placeholder--small {
  padding: 20px;
}

/* 紧凑标签 */
.tags-compact {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-compact {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background: var(--el-fill-color);
  border-radius: 6px;
  font-size: 12px;
}

.tag-compact__key {
  color: var(--el-color-primary);
  font-weight: 600;
}

.tag-compact__value {
  color: var(--text-primary);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 元数据网格 */
.meta-grid {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.meta-item {
  padding: 14px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.meta-item--i18n {
  background: linear-gradient(135deg, var(--el-fill-color-light) 0%, var(--el-color-primary-light-9) 100%);
}

.meta-item--normal {
  display: flex;
  align-items: center;
  gap: 12px;
}

.meta-item__header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.meta-item__badge {
  font-size: 11px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-8);
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 500;
}

.meta-item__key {
  flex-shrink: 0;
}

/* 流式堆叠区域 */
.meta-flow {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 16px;
}

.meta-flow__item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: var(--el-fill-color);
  border-radius: 6px;
  font-size: 13px;
  transition: all 0.2s ease;
}

.meta-flow__item:hover {
  background: var(--el-fill-color-dark);
}

.dark .meta-flow__item:hover {
  background: var(--el-fill-color);
}

.meta-flow__label {
  color: var(--text-secondary);
  font-weight: 500;
}

.meta-flow__value {
  color: var(--text-primary);
  font-weight: 500;
}

/* i18n 列表 */
.i18n-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.i18n-item {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 10px 14px;
  background: var(--bg-card);
  border-radius: 8px;
  border: 1px solid var(--border-lighter);
  transition: all 0.2s ease;
}

.i18n-item:hover {
  background: var(--el-color-primary-light-9);
}

.dark .i18n-item:hover {
  background: var(--el-fill-color);
}

.i18n-item__locale {
  flex-shrink: 0;
  min-width: 80px;
}

.i18n-item__text {
  flex: 1;
  font-size: 13px;
  color: var(--text-primary);
  line-height: 1.6;
}

/* 底部审计信息 */
.expand-footer {
  display: flex;
  align-items: center;
  gap: 32px;
  padding-top: 20px;
  border-top: 1px solid var(--border-light);
}

.audit-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.audit-icon {
  color: var(--text-secondary);
  font-size: 14px;
}

.audit-label {
  color: var(--text-secondary);
  font-weight: 500;
}

.audit-time {
  color: var(--text-primary);
  font-family: 'SF Mono', 'Courier New', monospace;
}
</style>
