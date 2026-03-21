<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import type { ProductItem } from '@/api/product'
import type { FirmwareQueryParams } from '../types'

const { t } = useI18n()
const userStore = useUserStore()

const props = defineProps<{
  query: FirmwareQueryParams
  productSearchOptions: ProductItem[]
  productSearchLoading: boolean
}>()

const emit = defineEmits<{
  (e: 'search'): void
  (e: 'reset'): void
  (e: 'create'): void
  (e: 'update:query', value: FirmwareQueryParams): void
  (e: 'product-search', keyword: string): void
}>()

const handleProductSearch = (keyword: string) => {
  emit('product-search', keyword)
}

const handleSearch = () => {
  emit('search')
}

const handleReset = () => {
  emit('reset')
}

const handleCreate = () => {
  emit('create')
}

const canCreate = () => {
  return userStore.hasPermission('fota:firmware:create')
}
</script>

<template>
  <div class="flex items-center gap-2">
    <el-select
      v-model="props.query.productId"
      :loading="productSearchLoading"
      :placeholder="t('firmware.product')"
      clearable
      filterable
      remote
      reserve-keyword
      :remote-method="handleProductSearch"
      style="width: 180px"
      @change="handleSearch"
    >
      <el-option
        v-for="product in productSearchOptions"
        :key="product.id"
        :label="product.name"
        :value="product.id"
      />
    </el-select>
    <el-input
      v-model="props.query.version"
      :placeholder="t('firmware.version')"
      clearable
      style="width: 140px"
      @keyup.enter="handleSearch"
    />
    <el-input
      v-model="props.query.internalVersion"
      :placeholder="t('firmware.internalVersion')"
      clearable
      style="width: 140px"
      @keyup.enter="handleSearch"
    />
    <el-button @click="handleSearch">
      {{ t('common.search') }}
    </el-button>
    <el-button @click="handleReset">
      {{ t('common.refresh') }}
    </el-button>
    <el-button
      v-if="canCreate()"
      type="primary"
      @click="handleCreate"
    >
      {{ t('firmware.add') }}
    </el-button>
  </div>
</template>
