# 负载监控页面重构实施计划

## 一、背景

当前负载监控页面以**当前实例为中心**展示数据，缺少全局视角。本次重构将页面改造为**"全局总览 + 分层概览 + 抽屉下钻"**的单页信息架构。

### 当前问题
- 仅展示当前区域/实例的指标
- 缺少多区域聚合和对比能力
- 数据层级不清晰（区域 → 主机 → 实例）

### 重构目标
1. 首屏展示**全局系统级摘要**
2. 按**区域概览 → 主机概览 → 实例概览 → 热点产品**四层展开
3. 区域和实例支持**抽屉下钻**查看详情

---

## 二、新页面结构

```
┌─────────────────────────────────────────────────────────────────┐
│  系统级摘要卡片: 全局负载分/等级 | QPS | P50/P99 | 活跃设备 | 限流率  │
├─────────────────────────────────────────────────────────────────┤
│  区域概览表: region | 负载分 | QPS | P99 | 活跃设备 | 限流率 | 实例数 │
│  [点击行 → 打开区域详情抽屉]                                     │
├─────────────────────────────────────────────────────────────────┤
│  主机概览表: host | 区域 | CPU | 内存 | 网络 | 实例数               │
├─────────────────────────────────────────────────────────────────┤
│  实例概览表: instance | 区域 | 主机 | 负载分 | QPS | 延迟 | 限流 │
│  [点击行 → 打开实例详情抽屉]                                     │
├─────────────────────────────────────────────────────────────────┤
│  热点产品表: product | QPS | 流量占比 | 活跃区域数                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 三、关键文件

### 需要修改的文件
| 文件 | 修改内容 |
|------|----------|
| `fota-ui/src/api/monitor.ts` | 扩展数据类型定义，新增 API 方法 |
| `fota-ui/src/views/monitor/MonitorView.vue` | 重构主页面容器 |
| `fota-ui/src/locales/zh-CN.ts` | 新增中文翻译键 |
| `fota-ui/src/locales/en-US.ts` | 新增英文翻译键 |

### 需要新建的组件
| 组件文件 | 功能描述 |
|----------|----------|
| `fota-ui/src/views/monitor/components/SystemSummaryCard.vue` | 系统级摘要卡片（5个关键指标） |
| `fota-ui/src/views/monitor/components/RegionOverviewTable.vue` | 区域概览表格（支持行点击） |
| `fota-ui/src/views/monitor/components/HostOverviewTable.vue` | 主机概览表格 |
| `fota-ui/src/views/monitor/components/InstanceOverviewTable.vue` | 实例概览表格（支持行点击） |
| `fota-ui/src/views/monitor/components/HotProductsTable.vue` | 热点产品表格 |
| `fota-ui/src/views/monitor/components/RegionDetailDrawer.vue` | 区域详情抽屉 |
| `fota-ui/src/views/monitor/components/InstanceDetailDrawer.vue` | 实例详情抽屉 |

---

## 四、数据类型扩展

在 `fota-ui/src/api/monitor.ts` 中新增以下类型：

```typescript
/**
 * 区域指标
 */
export interface RegionMetrics {
  region: string
  loadScore: number
  loadLevel: string
  checkQps: number
  reportQps: number
  checkP50Latency: number
  checkP99Latency: number
  reportP50Latency: number
  reportP99Latency: number
  todayActiveDevices: number
  blockRate: number
  instanceCount: number
  hotProductCount: number
}

/**
 * 增强的主机指标（包含区域信息）
 */
export interface HostMetricsEnhanced {
  host: string
  region: string
  cpuUsage: number
  memoryUsage: number
  networkInBytes: number
  networkOutBytes: number
  instanceCount: number
}

/**
 * 增强的实例指标（包含区域和主机信息）
 */
export interface InstanceMetricsEnhanced {
  instance: string
  region: string
  host: string
  loadScore: number
  loadLevel: string
  cpuUsage: number
  memoryUsage: number
  checkQps: number
  reportQps: number
  checkP50Latency: number
  checkP99Latency: number
  reportP50Latency: number
  reportP99Latency: number
  activeRequests: number
  blockRate: number
  circuitState: string
}

/**
 * 增强的热点产品指标
 */
export interface HotProductMetricsEnhanced {
  product: string
  checkQps: number
  reportQps: number
  trafficShare: number
  activeRegionCount: number
}

/**
 * 全局聚合指标
 */
export interface GlobalSummary {
  loadScore: number
  loadLevel: string
  checkQps: number
  reportQps: number
  checkP50Latency: number
  checkP99Latency: number
  todayActiveDevices: number
  blockRate: number
  regionCount: number
  totalInstances: number
}

/**
 * 全系统监控指标（新 API 返回类型）
 */
export interface GlobalMonitorMetrics {
  global: GlobalSummary
  regions: RegionMetrics[]
  hosts: HostMetricsEnhanced[]
  instances: InstanceMetricsEnhanced[]
  hotProducts: HotProductMetricsEnhanced[]
  timestamp: string
}

/**
 * 区域详情（用于抽屉）
 */
export interface RegionDetail {
  summary: RegionMetrics
  hosts: HostMetrics[]
  instances: InstanceMetrics[]
  hotProducts: HotProductMetrics[]
}

/**
 * 实例详情（用于抽屉）
 */
export interface InstanceDetail {
  summary: InstanceMetricsEnhanced
  hostCpuUsage: number
  hostMemoryUsage: number
  hotProducts: HotProductMetrics[]
  region: string
  host: string
  instance: string
  lastRefreshTime: string
}
```

### API 扩展

```typescript
export const monitorApi = {
  // 现有端点保持不变
  getRealtimeMetrics: () => get<RealtimeMetrics>('/admin/monitor/realtime'),
  getHotProducts: () => get<HotProductMetrics[]>('/admin/monitor/products/hotspots'),
  getTrends: (range = '15m') => get<MonitorTrends>('/admin/monitor/trends', { params: { range } }),

  // 新增端点
  getGlobalMetrics: () => get<GlobalMonitorMetrics>('/admin/monitor/global'),
  getRegionDetail: (region: string) => get<RegionDetail>(`/admin/monitor/regions/${encodeURIComponent(region)}`),
  getInstanceDetail: (instance: string) => get<InstanceDetail>(`/admin/monitor/instances/${encodeURIComponent(instance)}`),
}
```

---

## 五、组件设计

### 5.1 主页面 MonitorView.vue

状态管理：
```typescript
const metrics = ref<GlobalMonitorMetrics | null>(null)
const loading = ref(false)
const refreshTimer = ref<number | null>(null)

// 抽屉状态
const regionDrawerVisible = ref(false)
const selectedRegion = ref<RegionMetrics | null>(null)
const regionDetail = ref<RegionDetail | null>(null)

const instanceDrawerVisible = ref(false)
const selectedInstance = ref<InstanceMetricsEnhanced | null>(null)
const instanceDetail = ref<InstanceDetail | null>(null)
```

事件处理：
```typescript
const handleRegionClick = async (region: RegionMetrics) => {
  selectedRegion.value = region
  regionDrawerVisible.value = true
  regionDetail.value = await monitorApi.getRegionDetail(region.region)
}

const handleInstanceClick = async (instance: InstanceMetricsEnhanced) => {
  selectedInstance.value = instance
  instanceDrawerVisible.value = true
  instanceDetail.value = await monitorApi.getInstanceDetail(instance.instance)
}
```

### 5.2 系统摘要卡片 SystemSummaryCard.vue

展示5个核心指标：
1. 总体负载分/等级
2. Check QPS / Report QPS
3. Check P50 / P99
4. 今日活跃设备数
5. 限流率

使用 `grid-cols-5` 响应式布局，每个指标一个卡片。

### 5.3 区域概览表 RegionOverviewTable.vue

表格列：
- region（固定列）
- 负载分/等级（Tag 显示等级）
- Check QPS
- Report QPS
- Check P99
- Report P99
- 今日活跃设备数
- 限流率
- 实例数
- 热点产品数

支持行点击触发区域详情抽屉。

### 5.4 主机概览表 HostOverviewTable.vue

表格列：
- host（固定列）
- 所属区域
- CPU
- 内存
- 网络入/出
- 承载实例数

默认按 CPU 降序排序。

### 5.5 实例概览表 InstanceOverviewTable.vue

表格列：
- instance（固定列）
- 所属区域
- 所属主机
- 负载分/等级
- Check QPS / Report QPS
- Check P99 / Report P99
- 活跃请求
- 限流率
- 熔断状态

支持行点击触发实例详情抽屉。

### 5.6 热点产品表 HotProductsTable.vue

表格列：
- product（产品型号）
- Check QPS
- Report QPS
- 流量占比
- 活跃区域数

### 5.7 区域详情抽屉 RegionDetailDrawer.vue

抽屉内容：
1. 区域摘要卡片（复用指标卡片样式）
2. 区域内主机表（复用 HostOverviewTable 组件）
3. 区域内实例表（复用 InstanceOverviewTable 组件）
4. 区域热点产品表（复用 HotProductsTable 组件）

### 5.8 实例详情抽屉 InstanceDetailDrawer.vue

抽屉内容：
1. 实例摘要卡片
   - 负载分/等级
   - Check/Report QPS
   - Check/Report P50/P99
   - 活跃请求
   - 限流率
   - 熔断状态
   - JVM CPU/内存
   - 主机 CPU/内存

2. 归属信息（el-descriptions）
   - 区域
   - 主机
   - 实例标识
   - 最近刷新时间

3. 该实例承载的热点产品 Top N

---

## 六、国际化键新增

### zh-CN.ts

```typescript
monitor: {
  // ... 现有键保持不变

  // 新增
  regionOverview: '区域概览',
  regionOverviewDesc: '展示各区域的整体负载和流量情况，点击区域行可查看详细信息。',
  instanceOverview: '实例概览',
  instanceOverviewDesc: '展示各实例的详细运行状态，点击实例行可查看详细信息。',
  regionDetail: '区域详情',
  instanceDetail: '实例详情',
  instanceCount: '实例数',
  hotProductCount: '热点产品数',
  activeRegionCount: '活跃区域数',
  belongRegion: '所属区域',
  belongHost: '所属主机',
  loadScoreLevel: '负载分/等级',
  lastRefreshTime: '最近刷新',
  clickToViewDetail: '点击行查看详情',
  noDataAvailable: '暂无数据',
}
```

### en-US.ts

```typescript
monitor: {
  // ... existing keys...

  regionOverview: 'Region Overview',
  regionOverviewDesc: 'Shows overall load and traffic for each region. Click a row to view details.',
  instanceOverview: 'Instance Overview',
  instanceOverviewDesc: 'Shows detailed status for each instance. Click a row to view details.',
  regionDetail: 'Region Detail',
  instanceDetail: 'Instance Detail',
  instanceCount: 'Instances',
  hotProductCount: 'Hot Products',
  activeRegionCount: 'Active Regions',
  belongRegion: 'Region',
  belongHost: 'Host',
  loadScoreLevel: 'Load Score/Level',
  lastRefreshTime: 'Last Refresh',
  clickToViewDetail: 'Click row to view details',
  noDataAvailable: 'No data available',
}
```

---

## 七、实施步骤

### 阶段一：类型和 API（前端优先）
1. 扩展 `monitor.ts` 中的类型定义
2. 添加新的 API 方法声明
3. 添加国际化键

### 阶段二：组件开发
1. 创建 `SystemSummaryCard.vue`
2. 创建 `RegionOverviewTable.vue`
3. 创建 `HostOverviewTable.vue`
4. 创建 `InstanceOverviewTable.vue`
5. 创建 `HotProductsTable.vue`
6. 创建 `RegionDetailDrawer.vue`
7. 创建 `InstanceDetailDrawer.vue`

### 阶段三：主页面集成
1. 重构 `MonitorView.vue` 主容器
2. 集成所有子组件
3. 实现数据获取和状态管理
4. 实现抽屉联动逻辑

### 阶段四：后端适配（需后端配合）
1. 实现 `/admin/monitor/global` 端点
   - 聚合所有区域数据
   - 计算全局摘要指标
2. 实现 `/admin/monitor/regions/{region}` 端点
   - 返回区域内主机、实例、热点产品详情
3. 实现 `/admin/monitor/instances/{instance}` 端点
   - 返回实例详情和承载的热点产品

---

## 八、验证方案

### 前端验证
1. 检查页面5个板块是否正常渲染
2. 验证区域行点击能打开抽屉
3. 验证实例行点击能打开抽屉
4. 验证数据刷新机制（5秒轮询）
5. 验证响应式布局（不同屏幕尺寸）

### 后端验证
1. 验证 `/admin/monitor/global` 返回完整的多区域数据
2. 验证区域详情 API 包含区域内所有主机和实例
3. 验证实例详情 API 包含实例关联的热点产品

### 集成验证
1. 使用 Mock 数据测试前端组件
2. 连接真实 API 验证数据流
3. 验证多区域场景下的数据展示
