<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/date'
import { formatFileSize } from '../utils/formatters'
import type { FirmwareVersionItem } from '@/api/firmware'
import type { JsonFieldDefinition } from '@/components/json-field/types/json-field'

const { t } = useI18n()
const userStore = useUserStore()

const props = defineProps<{
  list: FirmwareVersionItem[]
  loading: boolean
  expandRowKeys: string[]
  canShowActions: boolean
  tagsSchema: JsonFieldDefinition[]
  metaSchema: JsonFieldDefinition[]
}>()

const emit = defineEmits<{
  (e: 'edit', row: FirmwareVersionItem): void
  (e: 'delete', row: FirmwareVersionItem): void
  (e: 'warm-cdn', row: FirmwareVersionItem): void
  (e: 'toggle-expand', row: FirmwareVersionItem): void
  (e: 'page-change', page: number): void
  (e: 'size-change', size: number): void
}>()

const handleRowClick = (row: FirmwareVersionItem) => {
  emit('toggle-expand', row)
}

const handleEdit = (row: FirmwareVersionItem) => {
  emit('edit', row)
}

const handleDelete = (row: FirmwareVersionItem) => {
  emit('delete', row)
}

const canEdit = () => {
  return userStore.hasPermission('fota:firmware:update')
}

const canDelete = () => {
  return userStore.hasPermission('fota:firmware:delete')
}
</script>

<template>
  <el-table
    v-loading="loading"
    :data="list"
    row-key="id"
    :expand-row-keys="expandRowKeys"
    stripe
    @row-click="handleRowClick"
  >
    <!-- 展开列插槽 -->
    <el-table-column type="expand">
      <template #default="{ row }">
        <slot name="expand" :row="row" :tags-schema="tagsSchema" :meta-schema="metaSchema" />
      </template>
    </el-table-column>

    <el-table-column
      prop="productId"
      :label="t('firmware.product')"
      min-width="160"
    >
      <template #default="{ row }">
        {{ row.productName || row.productId }}
      </template>
    </el-table-column>

    <el-table-column
      prop="version"
      :label="t('firmware.version')"
      min-width="160"
      show-overflow-tooltip
    />

    <el-table-column
      prop="internalVersion"
      :label="t('firmware.internalVersion')"
      min-width="200"
      show-overflow-tooltip
    />

    <el-table-column
      prop="packageStatus"
      :label="t('firmware.packageStatus')"
      min-width="100"
    >
      <template #default="{ row }">
        <el-tag
          v-if="row.packageStatus === 'READY'"
          type="success"
        >
          {{ t('firmware.packageStatusReady') }}
        </el-tag>
        <el-tag
          v-else-if="row.packageStatus === 'NONE'"
          type="info"
        >
          {{ t('firmware.packageStatusNone') }}
        </el-tag>
        <el-tag
          v-else
          type="warning"
        >
          {{ row.packageStatus }}
        </el-tag>
      </template>
    </el-table-column>

    <el-table-column
      prop="fileSize"
      :label="t('firmware.fileSize')"
      min-width="100"
    >
      <template #default="{ row }">
        {{ row.fileSize ? formatFileSize(row.fileSize) : '-' }}
      </template>
    </el-table-column>

    <el-table-column
      :label="t('firmware.uploadTime')"
      width="170"
    >
      <template #default="{ row }">
        {{ formatDateTime(row.createdAt) }}
      </template>
    </el-table-column>

    <el-table-column
      v-if="canShowActions"
      :label="t('common.actions')"
      width="120"
      fixed="right"
    >
      <template #default="{ row }">
        <el-button
          v-if="canEdit()"
          link
          class="ui-action-primary"
          @click.stop="handleEdit(row)"
        >
          {{ t('common.edit') }}
        </el-button>
        <el-button
          v-if="canDelete()"
          link
          class="ui-action-danger"
          @click.stop="handleDelete(row)"
        >
          {{ t('common.delete') }}
        </el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped>
:deep(.el-table__body-wrapper .el-table__row) {
  cursor: pointer;
}

:deep(.el-table__expanded-cell) {
  padding: 0 !important;
}

:deep(.el-table__expand-icon) {
  cursor: pointer;
}
</style>
