import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { pageFirmwareVersions } from '@/api/firmware'
import { trimFormValues } from '@/utils/form'
import { searchProducts, type ProductItem } from '@/api/product'
import { useUserStore } from '@/stores/user'
import type { FirmwareVersionItem } from '@/api/firmware'
import type { FirmwareQueryParams } from '../types'

export function useFirmwareList() {
  const { t } = useI18n()
  const userStore = useUserStore()

  const list = ref<FirmwareVersionItem[]>([])
  const total = ref(0)
  const loading = ref(false)
  const productSearchOptions = ref<ProductItem[]>([])
  const productSearchLoading = ref(false)
  let productSearchTimer: number | null = null

  const canViewStatistics = computed(() => userStore.hasPermission('fota:statistics:read'))

  const canShowActions = computed(() =>
    userStore.hasPermission('fota:firmware:update') ||
    userStore.hasPermission('fota:firmware:delete') ||
    canViewStatistics.value
  )

  const expandRowKeys = ref<string[]>([])
  const query = reactive<FirmwareQueryParams>({
    page: 1,
    size: 20,
    productId: undefined as number | undefined,
    version: '',
    internalVersion: '',
  })

  const handleProductSearch = async (keyword: string) => {
    if (productSearchTimer !== null) {
      clearTimeout(productSearchTimer)
    }
    productSearchTimer = window.setTimeout(async () => {
      productSearchLoading.value = true
      try {
        const result = await searchProducts(keyword.trim())
        productSearchOptions.value = result.records || []
      } finally {
        productSearchLoading.value = false
      }
    }, 300)
  }

  const fetchList = async () => {
    loading.value = true
    try {
      const result = await pageFirmwareVersions(trimFormValues(query))
      list.value = result.records || []
      total.value = result.total || 0
    } finally {
      loading.value = false
    }
  }

  const resetSearch = () => {
    query.page = 1
    query.productId = undefined
    query.version = ''
    query.internalVersion = ''
    void fetchList()
  }

  const toggleExpand = (row: FirmwareVersionItem) => {
    const rowKey = String(row.id)
    const index = expandRowKeys.value.indexOf(rowKey)
    expandRowKeys.value = index > -1
      ? expandRowKeys.value.filter(key => key !== rowKey)
      : [...expandRowKeys.value, rowKey]
  }

  onMounted(() => {
    void handleProductSearch('')
    void fetchList()
  })

  onUnmounted(() => {
    if (productSearchTimer !== null) {
      clearTimeout(productSearchTimer)
    }
  })

  return {
    t,
    list,
    total,
    loading,
    productSearchOptions,
    productSearchLoading,
    query,
    canShowActions,
    expandRowKeys,
    handleProductSearch,
    fetchList,
    resetSearch,
    toggleExpand,
  }
}
