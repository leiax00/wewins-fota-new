<script setup lang="ts">
import { Delete, Edit, InfoFilled, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { cacheMonitorApi, type CacheEvictPreview, type RedisInfo, type RedisKeyItem, type RedisKeyDetail } from '@/api/monitor'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const userStore = useUserStore()

const canUpdateCache = computed(() => userStore.hasPermission('monitor:cache:update'))

const redisInfo = ref<RedisInfo | null>(null)
const loading = reactive({
  info: false,
  keys: false,
  detail: false,
})

const keySearch = reactive({
  pattern: '',
  pageSize: 50,
  cursor: undefined as string | undefined,
})
const pageSizeOptions = [50, 100, 200]
const nextCursor = ref<string | undefined>()
const cursorHistory = ref<string[]>([])
let searchDebounceTimer: number | null = null

const keyList = ref<RedisKeyItem[]>([])
const totalKeys = ref(0)
const browseMode = ref<'tree' | 'table'>('tree')
type RedisKeyTreeNode = {
  id: string
  label: string
  keyItem?: RedisKeyItem
  children?: RedisKeyTreeNode[]
}

const selectedKey = ref<string | null>(null)
const keyDetail = ref<RedisKeyDetail | null>(null)
const editDialogVisible = ref(false)
const editForm = reactive({
  value: '',
  ttl: undefined as number | undefined,
})

const evictForm = reactive({
  productId: undefined as number | undefined,
  productModel: '',
  imeisText: '',
  evictProductCache: true,
  evictPolicyCache: true,
})
const evictPreview = ref<CacheEvictPreview | null>(null)
const previewLoading = ref(false)
const evictSubmitting = ref(false)

const formatBytes = (bytes: number): string => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const formatUptime = (seconds: number): string => {
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const mins = Math.floor((seconds % 3600) / 60)
  if (days > 0) return `${days}d ${hours}h ${mins}m`
  if (hours > 0) return `${hours}h ${mins}m`
  return `${mins}m`
}

const formatTtl = (ttl: number): string => {
  if (ttl === -1) return t('monitor.redisTtlForever')
  if (ttl === -2) return t('monitor.redisTtlExpired')
  if (ttl < 60) return `${ttl}s`
  if (ttl < 3600) return `${Math.floor(ttl / 60)}m ${ttl % 60}s`
  if (ttl < 86400) return `${Math.floor(ttl / 3600)}h ${Math.floor((ttl % 3600) / 60)}m`
  return `${Math.floor(ttl / 86400)}d ${Math.floor((ttl % 86400) / 3600)}h`
}

const keyTreeData = computed<RedisKeyTreeNode[]>(() => {
  const root: RedisKeyTreeNode[] = []
  const branchMap = new Map<string, RedisKeyTreeNode>()

  for (const item of keyList.value) {
    const segments = item.key.split(':').filter(Boolean)
    if (segments.length === 0) {
      root.push({ id: item.key, label: item.key, keyItem: item })
      continue
    }

    let path = ''
    let currentLevel = root
    for (let i = 0; i < segments.length; i++) {
      const segment = segments[i]
      path = path ? `${path}:${segment}` : segment

      const isLeaf = i === segments.length - 1
      let node = branchMap.get(path)
      if (!node) {
        node = {
          id: path,
          label: segment,
          children: isLeaf ? undefined : [],
          keyItem: isLeaf ? item : undefined,
        }
        currentLevel.push(node)
        branchMap.set(path, node)
      }

      if (!isLeaf) {
        if (!node.children) {
          node.children = []
        }
        currentLevel = node.children
      }
    }
  }

  return root
})

const loadRedisInfo = async () => {
  loading.info = true
  try {
    redisInfo.value = await cacheMonitorApi.getInfo()
  } catch (e) {
    ElMessage.error(t('monitor.redisInfoLoadFailed'))
  } finally {
    loading.info = false
  }
}

const searchKeys = async () => {
  loading.keys = true
  try {
    const result = await cacheMonitorApi.listKeys({
      pattern: keySearch.pattern || undefined,
      pageSize: keySearch.pageSize,
      cursor: keySearch.cursor,
    })
    keyList.value = result.keys
    totalKeys.value = result.total
    nextCursor.value = result.cursor
  } catch (e) {
    ElMessage.error(t('monitor.redisKeySearchFailed'))
  } finally {
    loading.keys = false
  }
}

const viewKeyDetail = async (key: string) => {
  loading.detail = true
  selectedKey.value = key
  try {
    keyDetail.value = await cacheMonitorApi.getKeyDetail(key)
  } catch (e) {
    ElMessage.error(t('monitor.redisKeyDetailLoadFailed'))
  } finally {
    loading.detail = false
  }
}

const openEditDialog = () => {
  if (keyDetail.value) {
    editForm.value = keyDetail.value.value?.toString() || ''
    editForm.ttl = keyDetail.value.ttl === -1 ? undefined : keyDetail.value.ttl
    editDialogVisible.value = true
  }
}

const saveKeyEdit = async () => {
  if (!selectedKey.value) return
  try {
    await cacheMonitorApi.setKeyValue(selectedKey.value, editForm.value, editForm.ttl)
    ElMessage.success(t('monitor.redisKeySaved'))
    editDialogVisible.value = false
    viewKeyDetail(selectedKey.value)
  } catch (e) {
    ElMessage.error(t('monitor.redisKeySaveFailed'))
  }
}

const deleteKey = async (key: string) => {
  try {
    await ElMessageBox.confirm(
      t('monitor.redisKeyDeleteConfirm', { key }),
      t('common.warning'),
      { type: 'warning' }
    )
    await cacheMonitorApi.deleteKey(key)
    ElMessage.success(t('monitor.redisKeyDeleted'))
    selectedKey.value = null
    keyDetail.value = null
    searchKeys()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(t('monitor.redisKeyDeleteFailed'))
    }
  }
}

const buildEvictRequest = () => {
  const imeis = evictForm.imeisText
    .split(/[\n,]+/)
    .map(item => item.trim())
    .filter(Boolean)

  return {
    productId: evictForm.productId,
    productModel: evictForm.productModel || undefined,
    imeis,
    evictProductCache: evictForm.evictProductCache,
    evictPolicyCache: evictForm.evictPolicyCache,
  }
}

const loadEvictPreview = async () => {
  previewLoading.value = true
  try {
    evictPreview.value = await cacheMonitorApi.previewEvict(buildEvictRequest())
  } catch (e) {
    ElMessage.error(t('monitor.cacheEvictPreviewFailed'))
  } finally {
    previewLoading.value = false
  }
}

const submitEvict = async () => {
  evictSubmitting.value = true
  try {
    const preview = await cacheMonitorApi.previewEvict(buildEvictRequest())
    evictPreview.value = preview

    if (!preview.executable) {
      ElMessage.warning(t('monitor.cacheEvictNoTarget'))
      return
    }

    const targetList = preview.affectedTargets.join('\n')
    await ElMessageBox.confirm(
      `${t('monitor.cacheEvictConfirmMessage', { scopes: preview.estimatedScopes, devices: preview.estimatedDeviceCount })}\n${targetList}`,
      t('monitor.cacheEvictConfirmTitle'),
      {
        type: 'warning',
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
      }
    )

    const result = await cacheMonitorApi.evict(buildEvictRequest())
    ElMessage.success(t('monitor.cacheEvictSuccess', {
      scopes: result.evictedScopes,
      devices: result.evictedDeviceCount,
    }))
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(t('monitor.cacheEvictFailed'))
    }
  } finally {
    evictSubmitting.value = false
  }
}

const formatValue = (value: unknown): string => {
  if (value === null || value === undefined) return 'null'
  if (typeof value === 'string') return value
  return JSON.stringify(value, null, 2)
}

const handleTreeNodeClick = (node: RedisKeyTreeNode) => {
  if (node.keyItem) {
    void viewKeyDetail(node.keyItem.key)
  }
}

type TreeUiNode = {
  expanded: boolean
  childNodes?: TreeUiNode[]
}

const setSubtreeExpanded = (node: TreeUiNode, expanded: boolean) => {
  node.expanded = expanded
  if (!node.childNodes || node.childNodes.length === 0) {
    return
  }
  for (const child of node.childNodes) {
    setSubtreeExpanded(child, expanded)
  }
}

const toggleNodeSubtreeExpansion = (node: TreeUiNode) => {
  const nextExpanded = !node.expanded
  setSubtreeExpanded(node, nextExpanded)
}

const toggleNodeSingleLevel = (node: TreeUiNode) => {
  node.expanded = !node.expanded
}

const handleTreeNodeDblClick = (data: RedisKeyTreeNode, node: TreeUiNode) => {
  if (data.children && data.children.length > 0 && !data.keyItem) {
    toggleNodeSingleLevel(node)
  }
}

const resetKeyPagination = () => {
  keySearch.cursor = undefined
  nextCursor.value = undefined
  cursorHistory.value = []
}

const goNextPage = async () => {
  if (!nextCursor.value) {
    return
  }
  cursorHistory.value.push(keySearch.cursor || '0')
  keySearch.cursor = nextCursor.value
  await searchKeys()
}

const goPrevPage = async () => {
  if (cursorHistory.value.length === 0) {
    return
  }
  const prev = cursorHistory.value.pop()
  keySearch.cursor = prev === '0' ? undefined : prev
  await searchKeys()
}

onMounted(() => {
  loadRedisInfo()
  searchKeys()
})

watch(() => keySearch.pattern, () => {
  if (searchDebounceTimer !== null) {
    window.clearTimeout(searchDebounceTimer)
  }
  searchDebounceTimer = window.setTimeout(() => {
    resetKeyPagination()
    void searchKeys()
  }, 300)
})

watch(() => keySearch.pageSize, () => {
  resetKeyPagination()
  void searchKeys()
})

onBeforeUnmount(() => {
  if (searchDebounceTimer !== null) {
    window.clearTimeout(searchDebounceTimer)
  }
})
</script>

<template>
  <div>
    <h1 class="ui-page-title">{{ t('menu.monitorCache') }}</h1>

    <el-card class="mb-6">
      <template #header>
        <div class="flex items-center justify-between">
          <span class="inline-flex items-center gap-1 ui-card-title">
            {{ t('monitor.redisInfoTitle') }}
            <el-tooltip :content="t('monitor.redisInfoDesc')" placement="top">
              <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
            </el-tooltip>
          </span>
          <el-button :icon="Refresh" @click="loadRedisInfo" :loading="loading.info" size="small">
            {{ t('common.refresh') }}
          </el-button>
        </div>
      </template>

      <div v-if="redisInfo" class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
        <div class="metric-card p-3 rounded">
          <div class="metric-label text-xs mb-1">{{ t('monitor.redisVersion') }}</div>
          <div class="font-semibold">{{ redisInfo.version }}</div>
        </div>
        <div class="metric-card p-3 rounded">
          <div class="metric-label text-xs mb-1">{{ t('monitor.redisMode') }}</div>
          <div class="font-semibold">{{ redisInfo.mode }}</div>
        </div>
        <div class="metric-card p-3 rounded">
          <div class="metric-label text-xs mb-1">{{ t('monitor.redisClients') }}</div>
          <div class="font-semibold">{{ redisInfo.connectedClients }}</div>
        </div>
        <div class="metric-card p-3 rounded">
          <div class="metric-label text-xs mb-1">{{ t('monitor.redisMemory') }}</div>
          <div class="font-semibold">{{ formatBytes(redisInfo.usedMemory) }}</div>
        </div>
        <div class="metric-card p-3 rounded">
          <div class="metric-label text-xs mb-1">{{ t('monitor.redisMemoryPeak') }}</div>
          <div class="font-semibold">{{ formatBytes(redisInfo.usedMemoryPeak) }}</div>
        </div>
        <div class="metric-card p-3 rounded">
          <div class="metric-label text-xs mb-1">{{ t('monitor.redisMemoryUsage') }}</div>
          <div class="font-semibold">{{ redisInfo.memoryUsagePercent.toFixed(1) }}%</div>
        </div>
        <div class="metric-card p-3 rounded">
          <div class="metric-label text-xs mb-1">{{ t('monitor.redisTotalKeys') }}</div>
          <div class="font-semibold">{{ redisInfo.totalKeys }}</div>
        </div>
        <div class="metric-card p-3 rounded">
          <div class="metric-label text-xs mb-1">{{ t('monitor.redisHitRate') }}</div>
          <div class="font-semibold">{{ redisInfo.hitRate.toFixed(1) }}%</div>
        </div>
        <div class="metric-card p-3 rounded">
          <div class="metric-label text-xs mb-1">{{ t('monitor.redisOps') }}</div>
          <div class="font-semibold">{{ redisInfo.instantaneousOpsPerSec }}/s</div>
        </div>
        <div class="metric-card p-3 rounded">
          <div class="metric-label text-xs mb-1">{{ t('monitor.redisUptime') }}</div>
          <div class="font-semibold">{{ formatUptime(redisInfo.uptimeInSeconds) }}</div>
        </div>
        <div class="metric-card p-3 rounded">
          <div class="metric-label text-xs mb-1">{{ t('monitor.redisExpired') }}</div>
          <div class="font-semibold">{{ redisInfo.expiredKeys }}</div>
        </div>
        <div class="metric-card p-3 rounded">
          <div class="metric-label text-xs mb-1">{{ t('monitor.redisEvicted') }}</div>
          <div class="font-semibold">{{ redisInfo.evictedKeys }}</div>
        </div>
      </div>
    </el-card>

    <el-card class="mb-6">
      <template #header>
        <span class="inline-flex items-center gap-1 ui-card-title">
          {{ t('monitor.redisKeyBrowser') }}
          <el-tooltip :content="t('monitor.redisKeyBrowserDesc')" placement="top">
            <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
          </el-tooltip>
        </span>
      </template>

      <div class="mb-4 flex gap-2">
        <el-input
          v-model="keySearch.pattern"
          :placeholder="t('monitor.redisKeyPatternPlaceholder')"
          :prefix-icon="Search"
          clearable
          style="max-width: 400px"
          @keyup.enter="() => { resetKeyPagination(); void searchKeys() }"
        />
        <el-button :icon="Search" @click="() => { resetKeyPagination(); void searchKeys() }" :loading="loading.keys">
          {{ t('common.search') }}
        </el-button>
        <el-select v-model="keySearch.pageSize" style="width: 120px">
          <el-option v-for="size in pageSizeOptions" :key="size" :label="`${size}`" :value="size" />
        </el-select>
        <el-radio-group v-model="browseMode" size="small">
          <el-radio-button label="tree">{{ t('monitor.redisBrowseTree') }}</el-radio-button>
          <el-radio-button label="table">{{ t('monitor.redisBrowseTable') }}</el-radio-button>
        </el-radio-group>
      </div>

      <div class="key-browser-layout" v-loading="loading.keys || loading.detail">
        <div class="key-browser-panel">
          <el-tree
            v-if="browseMode === 'tree'"
            :data="keyTreeData"
            node-key="id"
            :current-node-key="selectedKey || undefined"
            :expand-on-click-node="false"
            highlight-current
            class="redis-key-tree"
            @node-click="handleTreeNodeClick"
          >
            <template #default="{ data, node }">
              <div class="redis-tree-node" @dblclick.stop="handleTreeNodeDblClick(data, node)">
                <el-button
                  v-if="data.children && data.children.length > 0 && !data.keyItem"
                  type="primary"
                  link
                  size="small"
                  class="redis-node-toggle"
                  @click.stop="toggleNodeSubtreeExpansion(node)"
                >
                  {{ node.expanded ? '-' : '+' }}
                </el-button>
                <span v-else class="redis-node-toggle-placeholder" />
                <span class="redis-tree-label">{{ data.label }}</span>
                <span v-if="data.keyItem" class="redis-tree-meta">
                  {{ data.keyItem.type }} · {{ formatTtl(data.keyItem.ttl) }} · {{ formatBytes(data.keyItem.memoryUsage) }}
                </span>
              </div>
            </template>
          </el-tree>

          <el-table v-else :data="keyList" stripe height="460" class="redis-key-table">
            <el-table-column prop="key" :label="t('monitor.redisKeyName')" min-width="260" show-overflow-tooltip />
            <el-table-column prop="type" :label="t('monitor.redisKeyType')" width="90" />
            <el-table-column :label="t('monitor.redisKeyTtl')" width="120">
              <template #default="{ row }">
                {{ formatTtl(row.ttl) }}
              </template>
            </el-table-column>
            <el-table-column :label="t('monitor.redisKeySize')" width="110">
              <template #default="{ row }">
                {{ formatBytes(row.memoryUsage) }}
              </template>
            </el-table-column>
            <el-table-column :label="t('common.actions')" width="100" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="viewKeyDetail(row.key)">
                  {{ t('common.view') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="key-pagination-actions">
            <el-button size="small" @click="goPrevPage" :disabled="cursorHistory.length === 0">
              {{ t('common.prev') }}
            </el-button>
            <el-button size="small" @click="goNextPage" :disabled="!nextCursor">
              {{ t('common.next') }}
            </el-button>
          </div>
        </div>

        <div class="key-browser-panel key-detail-panel">
          <template v-if="keyDetail">
            <el-descriptions :column="2" border class="mb-4">
              <el-descriptions-item :label="t('monitor.redisKeyName')">{{ keyDetail.key }}</el-descriptions-item>
              <el-descriptions-item :label="t('monitor.redisKeyType')">{{ keyDetail.type }}</el-descriptions-item>
              <el-descriptions-item :label="t('monitor.redisKeyTtl')">{{ formatTtl(keyDetail.ttl) }}</el-descriptions-item>
              <el-descriptions-item :label="t('monitor.redisKeySize')">{{ formatBytes(keyDetail.memoryUsage) }}</el-descriptions-item>
              <el-descriptions-item :label="t('monitor.redisKeyEncoding')">{{ keyDetail.encoding }}</el-descriptions-item>
              <el-descriptions-item :label="t('monitor.redisKeyLength')">{{ keyDetail.length }}</el-descriptions-item>
            </el-descriptions>

            <div class="mb-2 font-semibold">{{ t('monitor.redisKeyValue') }}</div>
            <el-input
              :model-value="formatValue(keyDetail.value)"
              type="textarea"
              :rows="12"
              readonly
              class="font-mono"
            />

            <div class="mt-3 flex items-center gap-2">
              <el-button v-if="canUpdateCache" :icon="Edit" @click="openEditDialog">
                {{ t('common.edit') }}
              </el-button>
              <el-button v-if="canUpdateCache" type="danger" :icon="Delete" @click="deleteKey(selectedKey!)">
                {{ t('common.delete') }}
              </el-button>
            </div>

            <div v-if="keyDetail.error" class="mt-2 text-red-500">{{ keyDetail.error }}</div>
          </template>
          <div v-else class="key-detail-empty">
            {{ t('monitor.redisKeyDetail') }}
          </div>
        </div>
      </div>
    </el-card>

    <el-card>
      <template #header>
        <span class="inline-flex items-center gap-1 ui-card-title">
          {{ t('monitor.cacheEvictTitle') }}
          <el-tooltip :content="t('monitor.cacheEvictDesc')" placement="top">
            <el-icon class="text-slate-400 cursor-help"><InfoFilled /></el-icon>
          </el-tooltip>
        </span>
      </template>
      <el-form label-width="160px">
        <el-form-item :label="t('monitor.cacheEvictProductId')">
          <el-input-number v-model="evictForm.productId" :min="1" :disabled="!canUpdateCache" />
        </el-form-item>

        <el-form-item :label="t('monitor.cacheEvictProductModel')">
          <el-input v-model="evictForm.productModel" :disabled="!canUpdateCache" />
        </el-form-item>

        <el-form-item :label="t('monitor.cacheEvictImeis')">
          <el-input
            v-model="evictForm.imeisText"
            type="textarea"
            :rows="4"
            :disabled="!canUpdateCache"
            :placeholder="t('monitor.cacheEvictImeisPlaceholder')"
          />
        </el-form-item>

        <el-form-item :label="t('monitor.cacheEvictScopes')">
          <el-checkbox v-model="evictForm.evictProductCache" :disabled="!canUpdateCache">
            {{ t('monitor.cacheEvictProductCache') }}
          </el-checkbox>
          <el-checkbox v-model="evictForm.evictPolicyCache" :disabled="!canUpdateCache">
            {{ t('monitor.cacheEvictPolicyCache') }}
          </el-checkbox>
        </el-form-item>

        <el-form-item v-if="canUpdateCache">
          <el-button @click="loadEvictPreview" :loading="previewLoading">
            {{ t('monitor.cacheEvictPreviewAction') }}
          </el-button>
          <el-button type="danger" @click="submitEvict" :loading="evictSubmitting">
            {{ t('monitor.cacheEvictAction') }}
          </el-button>
        </el-form-item>

        <el-form-item>
          <div class="cache-evict-preview" v-loading="previewLoading">
            <div class="cache-evict-preview-title">{{ t('monitor.cacheEvictPreviewTitle') }}</div>
            <template v-if="evictPreview">
              <div class="cache-evict-preview-meta">
                {{ t('monitor.cacheEvictPreviewScope', { scopes: evictPreview.estimatedScopes }) }}
                ·
                {{ t('monitor.cacheEvictPreviewDevices', { devices: evictPreview.estimatedDeviceCount }) }}
              </div>
              <ul class="cache-evict-preview-list">
                <li v-for="target in evictPreview.affectedTargets" :key="target">{{ target }}</li>
              </ul>
            </template>
            <div v-else class="cache-evict-preview-empty">{{ t('monitor.cacheEvictPreviewEmpty') }}</div>
          </div>
        </el-form-item>
      </el-form>
    </el-card>

    <el-dialog v-model="editDialogVisible" :title="t('monitor.redisKeyEdit')" width="600px">
      <el-form label-width="100px">
        <el-form-item :label="t('monitor.redisKeyValue')">
          <el-input v-model="editForm.value" type="textarea" :rows="8" />
        </el-form-item>
        <el-form-item :label="t('monitor.redisKeyTtl')">
          <el-input-number v-model="editForm.ttl" :min="-1" :placeholder="t('monitor.redisTtlPlaceholder')" />
          <span class="ml-2 text-slate-500 text-sm">{{ t('monitor.redisTtlHint') }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveKeyEdit">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.metric-card {
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
}

.metric-label {
  color: var(--el-text-color-secondary);
}

.redis-key-tree {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 8px;
  max-height: 460px;
  overflow: auto;
}

.key-browser-layout {
  display: flex;
  gap: 12px;
}

.key-browser-panel {
  width: 50%;
  min-height: 460px;
}

.key-detail-panel {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 12px;
  background: var(--el-fill-color-blank);
}

.key-detail-empty {
  height: 100%;
  min-height: 430px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
}

.key-pagination-actions {
  margin-top: 8px;
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.redis-tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
  user-select: none;
  -webkit-user-select: none;
}

.redis-node-toggle {
  width: 18px;
  padding: 0;
  min-height: 18px;
}

.redis-node-toggle-placeholder {
  width: 18px;
  flex-shrink: 0;
}

.redis-tree-label {
  font-size: 13px;
  color: var(--el-text-color-primary);
  user-select: none;
  -webkit-user-select: none;
}

.redis-tree-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  user-select: none;
  -webkit-user-select: none;
}

.redis-key-tree :deep(.el-tree-node__content ::selection) {
  background: transparent;
}

.cache-evict-preview {
  width: 100%;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 10px 12px;
  background: var(--el-fill-color-blank);
}

.cache-evict-preview-title {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.cache-evict-preview-meta {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.cache-evict-preview-list {
  margin: 8px 0 0;
  padding-left: 18px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.cache-evict-preview-empty {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.redis-key-tree :deep(.el-tree-node__content) {
  border-radius: 6px;
}

.redis-key-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: color-mix(in srgb, var(--el-color-primary) 16%, transparent);
  color: var(--el-color-primary);
}

@media (max-width: 1100px) {
  .key-browser-layout {
    flex-direction: column;
  }

  .key-browser-panel {
    width: 100%;
  }
}
</style>
