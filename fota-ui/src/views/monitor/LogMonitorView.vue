<script setup lang="ts">
import { InfoFilled, VideoPlay, VideoPause, Delete, ArrowDown, Refresh, Filter } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import type { LogEvent } from '@/api/monitor'
import { createLogStream } from '@/api/monitor'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const userStore = useUserStore()

// 状态
const logs = ref<LogEvent[]>([])
const isConnecting = ref(false)
const isConnected = ref(false)
const isPaused = ref(false)
const autoScroll = ref(true)
const filterKeyword = ref('')
const filterLevel = ref<string>('ALL')
const logContainer = ref<HTMLElement>()
const maxLines = ref(1000)

// 批量处理相关
const logBatch = ref<LogEvent[]>([])
let batchTimer: number | null = null
const BATCH_DELAY = 100

// SSE 连接
let disconnectSSE: (() => void) | null = null
let reconnectTimer: number | null = null

// 组件是否已卸载
let isUnmounted = false

// 过滤后的日志
const filteredLogs = ref<LogEvent[]>([])

// 监听过滤条件变化
watch([filterKeyword, filterLevel], () => {
  applyFilters()
})

// 应用过滤
function applyFilters() {
  let result = logs.value

  if (filterLevel.value !== 'ALL') {
    result = result.filter((log) => log.level === filterLevel.value)
  }

  if (filterKeyword.value) {
    const keyword = filterKeyword.value.toLowerCase()
    result = result.filter((log) =>
      log.message.toLowerCase().includes(keyword) ||
      log.logger.toLowerCase().includes(keyword)
    )
  }

  filteredLogs.value = result
}

// 批量添加日志
function addLogBatch() {
  if (logBatch.value.length === 0) return

  const batch = logBatch.value.splice(0, logBatch.value.length)
  logs.value.push(...batch)

  if (logs.value.length > maxLines.value) {
    const excess = logs.value.length - maxLines.value
    logs.value = logs.value.slice(excess)
  }

  applyFilters()

  if (autoScroll.value && !isPaused.value) {
    nextTick(() => {
      scrollToBottom()
    })
  }
}

// 添加单条日志到批处理队列
function addLog(event: LogEvent) {
  logBatch.value.push(event)

  if (batchTimer) {
    clearTimeout(batchTimer)
  }

  batchTimer = window.setTimeout(() => {
    addLogBatch()
  }, BATCH_DELAY)
}

// 滚动到底部
function scrollToBottom() {
  if (logContainer.value) {
    logContainer.value.scrollTop = logContainer.value.scrollHeight
  }
}

// 清空日志
function clearLogs() {
  logs.value = []
  filteredLogs.value = []
  logBatch.value = []
}

// 暂停/恢复滚动
function togglePause() {
  isPaused.value = !isPaused.value
  if (!isPaused.value) {
    nextTick(() => {
      scrollToBottom()
    })
  }
}

// 切换自动滚动
function toggleAutoScroll() {
  autoScroll.value = !autoScroll.value
  if (autoScroll.value) {
    nextTick(() => {
      scrollToBottom()
    })
  }
}

// 重连
function reconnect() {
  disconnect()
  connect()
}

// 连接日志流
function connect() {
  if (isConnected.value || isConnecting.value || isUnmounted) {
    return
  }

  isConnecting.value = true

  try {
    const stream = createLogStream()

    disconnectSSE = stream.connect({
      onopen: () => {
        if (isUnmounted) return
        isConnecting.value = false
        isConnected.value = true
      },
      onmessage: (event) => {
        if (isUnmounted) return
        const logEvent = event.data as LogEvent
        if (logEvent && typeof logEvent === 'object' && 'level' in logEvent) {
          addLog(logEvent)
        }
      },
      onerror: (error) => {
        console.error('SSE 日志流错误:', error)
        disconnect()
        if (!isUnmounted) {
          reconnectTimer = window.setTimeout(() => {
            if (!isConnected.value && !isUnmounted) {
              connect()
            }
          }, 5000)
        }
      },
    })
  } catch (error) {
    console.error('创建 SSE 连接失败:', error)
    isConnecting.value = false
  }
}

// 断开连接
function disconnect() {
  if (batchTimer) {
    clearTimeout(batchTimer)
    batchTimer = null
  }

  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }

  if (disconnectSSE) {
    disconnectSSE()
    disconnectSSE = null
  }

  isConnected.value = false
  isConnecting.value = false
}

// 获取日志级别对应的标签类型
function getLogLevelType(level: string): 'success' | 'info' | 'warning' | 'danger' {
  switch (level.toUpperCase()) {
    case 'TRACE':
    case 'DEBUG':
      return 'info'
    case 'INFO':
      return 'success'
    case 'WARN':
      return 'warning'
    case 'ERROR':
      return 'danger'
    default:
      return 'info'
  }
}

// 格式化日志消息
function formatMessage(message: string): string {
  return message
}

// 检查是否在底部附近
function isNearBottom(): boolean {
  if (!logContainer.value) return true
  const threshold = 100
  return logContainer.value.scrollHeight - logContainer.value.scrollTop - logContainer.value.clientHeight < threshold
}

// 监听滚动事件
function handleScroll() {
  if (logContainer.value) {
    autoScroll.value = isNearBottom()
  }
}

// 检查权限
const canViewLogMonitor = userStore.hasPermission('monitor:log:read')

// 组件挂载
onMounted(() => {
  if (canViewLogMonitor) {
    connect()
  }
})

// 组件卸载
onUnmounted(() => {
  isUnmounted = true
  disconnect()
})
</script>

<template>
  <div class="log-monitor-page">
    <el-card class="log-monitor-card">
      <template #header>
        <div class="flex items-center justify-between">
          <span class="ui-card-title">{{ t('menu.monitorLog') }}</span>

          <!-- 操作按钮 -->
          <div class="flex items-center gap-2" v-if="canViewLogMonitor">
            <!-- 连接状态 -->
            <el-tag :type="isConnected ? 'success' : isConnecting ? 'warning' : 'info'" size="small">
              {{ isConnected ? '已连接' : isConnecting ? '连接中...' : '未连接' }}
            </el-tag>

            <el-button :icon="Refresh" size="small" @click="reconnect">
              {{ t('common.refresh') }}
            </el-button>
          </div>
        </div>
      </template>

      <!-- 无权限提示 -->
      <el-empty v-if="!canViewLogMonitor" description="无访问权限" />

      <template v-else>
        <!-- 工具栏 -->
        <div class="toolbar">
          <div class="toolbar-left">
            <!-- 级别过滤 -->
            <el-select v-model="filterLevel" size="small" style="width: 110px">
              <template #prefix>
                <el-icon><Filter /></el-icon>
              </template>
              <el-option label="全部" value="ALL" />
              <el-option label="ERROR" value="ERROR" />
              <el-option label="WARN" value="WARN" />
              <el-option label="INFO" value="INFO" />
              <el-option label="DEBUG" value="DEBUG" />
            </el-select>

            <!-- 关键词过滤 -->
            <el-input
              v-model="filterKeyword"
              size="small"
              :placeholder="t('monitor.logFilterPlaceholder')"
              clearable
              style="width: 200px"
            >
              <template #prefix>
                <el-icon><Filter /></el-icon>
              </template>
            </el-input>

            <!-- 日志数量 -->
            <span class="text-sm text-slate-500">
              {{ filteredLogs.length }} / {{ maxLines }}
            </span>
          </div>

          <div class="toolbar-right">
            <!-- 自动滚动 -->
            <el-button
              :type="autoScroll ? 'primary' : 'default'"
              :icon="ArrowDown"
              size="small"
              @click="toggleAutoScroll"
            >
              自动滚动
            </el-button>

            <!-- 暂停/恢复 -->
            <el-button
              :type="isPaused ? 'warning' : 'default'"
              :icon="isPaused ? VideoPlay : VideoPause"
              size="small"
              @click="togglePause"
            >
              {{ isPaused ? '恢复' : '暂停' }}
            </el-button>

            <!-- 回到底部 -->
            <el-tooltip content="回到底部" placement="top">
              <el-button :icon="ArrowDown" size="small" @click="scrollToBottom" />
            </el-tooltip>

            <!-- 清空 -->
            <el-button :icon="Delete" size="small" @click="clearLogs">
              {{ t('monitor.logClear') }}
            </el-button>
          </div>
        </div>

        <!-- 日志容器 -->
        <div
          ref="logContainer"
          class="log-container"
          @scroll="handleScroll"
        >
          <div v-if="filteredLogs.length === 0" class="log-empty">
            <el-icon :size="48"><InfoFilled /></el-icon>
            <p class="text-slate-400 mt-2">
              {{ !isConnected && !isConnecting ? '等待连接...' : '正在接收日志...' }}
            </p>
          </div>

          <template v-else>
            <div v-for="(log, index) in filteredLogs" :key="index" class="log-line" :class="`log-level-${log.level.toLowerCase()}`">
              <span class="log-time">{{ log.formattedTime }}</span>
              <el-tag :type="getLogLevelType(log.level)" size="small" class="log-level">
                {{ log.level }}
              </el-tag>
              <span class="log-thread" :title="log.thread">{{ log.thread }}</span>
              <span class="log-logger" :title="log.logger">{{ log.logger }}</span>
              <span class="log-separator">-</span>
              <span class="log-message">{{ formatMessage(log.message) }}</span>
            </div>
          </template>
        </div>

        <!-- 底部提示 -->
        <div v-if="!autoScroll" class="log-footer">
          <span class="text-xs text-amber-500">
            <el-icon><ArrowDown /></el-icon>
            自动滚动已暂停
          </span>
        </div>
      </template>
    </el-card>
  </div>
</template>

<style scoped>
.log-monitor-page {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.log-monitor-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.log-monitor-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  flex-shrink: 0;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.log-container {
  flex: 1;
  overflow-y: auto;
  background-color: #0f172a;
  border-radius: 6px;
  padding: 12px;
  font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  border: 1px solid #1e293b;
  min-height: 0;
}

.log-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #475569;
}

.log-line {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 3px 8px;
  border-radius: 4px;
  transition: background-color 0.15s ease;
  white-space: pre-wrap;
  word-break: break-all;
}

.log-line:hover {
  background-color: rgba(255, 255, 255, 0.05);
}

.log-time {
  color: #64748b;
  flex-shrink: 0;
  font-size: 12px;
}

.log-level {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 500;
}

.log-thread {
  color: #38bdf8;
  flex-shrink: 0;
  font-size: 12px;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.log-logger {
  color: #34d399;
  flex-shrink: 0;
  font-size: 12px;
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.log-separator {
  color: #64748b;
  flex-shrink: 0;
}

.log-message {
  color: #e2e8f0;
  flex: 1;
  min-width: 0;
}

.log-line.log-level-error {
  background-color: rgba(239, 68, 68, 0.05);
}

.log-line.log-level-error .log-message {
  color: #fca5a5;
}

.log-line.log-level-warn {
  background-color: rgba(245, 158, 11, 0.05);
}

.log-line.log-level-warn .log-message {
  color: #fcd34d;
}

.log-line.log-level-info .log-message {
  color: #e2e8f0;
}

.log-line.log-level-debug .log-message {
  color: #94a3b8;
}

.log-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #e5e7eb;
  flex-shrink: 0;
}

.log-footer .el-icon {
  margin-right: 4px;
  vertical-align: middle;
}

.log-container::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.log-container::-webkit-scrollbar-track {
  background: #1e293b;
  border-radius: 4px;
}

.log-container::-webkit-scrollbar-thumb {
  background: #475569;
  border-radius: 4px;
}

.log-container::-webkit-scrollbar-thumb:hover {
  background: #64748b;
}
</style>
