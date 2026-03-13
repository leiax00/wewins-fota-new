# 多实例部署监控改进方案

> **版本**: v1.1  
> **创建日期**: 2026-03-13  
> **最后更新**: 2026-03-13  
> **状态**: 待实施  
> **关联文档**: [动态周期与负载控制设计说明](./dynamic-interval-and-load-control.md)

---

## 1. 背景

### 1.1 部署架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         负载均衡器                               │
│                         (Nginx/ALB)                             │
└────────────────────────────┬────────────────────────────────────┘
                             │ 轮询分发
         ┌───────────────────┼───────────────────┐
         │                   │                   │
         ▼                   ▼                   ▼
    ┌─────────┐         ┌─────────┐         ┌─────────┐
    │ Host 1  │         │ Host 2  │         │ Host 3  │
    ├─────────┤         ├─────────┤         ├─────────┤
    │fota-svc │         │fota-svc │         │fota-svc │
    │instance │         │instance │         │instance │
    │   1     │         │   2     │         │   3     │
    └────┬────┘         └────┬────┘         └────┬────┘
         │                   │                   │
         └───────────────────┼───────────────────┘
                             │
                    ┌────────┴────────┐
                    │  Redis Cluster  │
                    │  (区域共享)      │
                    └─────────────────┘
```

**关键特征**：
- 同一区域多台主机
- 每台主机可部署多个实例
- 所有实例共享同一 Redis 集群
- 每个实例独立运行 Sentinel 限流

### 1.2 现有能力

| 能力 | 实现位置 | 说明 |
|------|---------|------|
| 节点注册 | `NodeRegistryService` | `fota:registry:node:{code}` 存储节点信息 |
| 节点列表 | `listOnlineNodes()` | 从 `fota:registry:nodes` 获取所有在线节点 |
| 字典系统 | `DictType` / `DictItem` | 支持动态配置，extra 字段为 JSON 类型 |

Node code 格式：`{region}-{host}-{domain}`，可解析出区域信息进行过滤

### 1.2 当前实现分析

**负载计算流程**：

```
请求到达实例 A
    │
    ▼
SystemLoadIndicatorImpl.getSnapshot()
    │
    ├── JVM CPU (本实例) ──────────────┐
    ├── JVM 内存 (本实例) ─────────────┤
    ├── 连接池 (本实例) ───────────────┤
    │                                  ├──> 加权求和 ──> 本实例负载评分
    ├── 区域 QPS (Prometheus 聚合) ────┤
    ├── 区域 P99 (Prometheus 聚合) ────┤
    └── 主机 CPU/内存 (本主机) ────────┘
    │
    ▼
DynamicIntervalService.calculateCheckInterval()
    │
    ▼
返回动态 checkInterval 给设备
```

**监控数据流程**：

```
Admin API 请求 ──> MonitorOverviewService
                        │
                        ├── 本实例 LoadSnapshot
                        ├── 区域主机列表 (Prometheus)
                        └── 区域实例列表 (Prometheus)
```

---

## 2. 问题分析

### 2.1 负载计算作用域混乱

**问题描述**：

| 指标 | 当前作用域 | 问题 |
|------|-----------|------|
| JVM CPU/内存/连接池 | 实例 | ✅ 正确 |
| QPS | 区域聚合 | ❌ 无法感知本实例压力 |
| P99 延迟 | 区域聚合 | ❌ 无法感知本实例性能 |
| 主机 CPU/内存 | 本主机 | ✅ 正确 |

**场景示例**：

```
区域总 QPS = 8000
实例数量 = 4

理想情况：每实例 2000 QPS
实际情况：实例 A=4000, 实例 B=2000, 实例 C=1500, 实例 D=500

当前算法：所有实例看到相同的区域 QPS (8000)
问题：实例 A 已经过载，但负载评分无法反映
```

### 2.2 P99 阈值过于苛刻

**当前阈值**：
- 警告：30ms
- 严重：50ms

**实际延迟构成**：

| 延迟来源 | 典型耗时 | 说明 |
|---------|---------|------|
| 网络往返（LB → 实例） | 1-3ms | 负载均衡器转发 |
| Redis 读（热路径） | 1-5ms | 本地 Redis |
| 策略匹配（内存计算） | <1ms | 纯 CPU |
| 序列化/反序列化 | 1-2ms | JSON 处理 |
| GC 暂停（偶发） | 10-50ms | Young GC / Mixed GC |
| **理论最优** | **5-15ms** | 无干扰情况 |
| **现实 P99** | **20-80ms** | 含长尾影响 |

**结论**：50ms 作为严重阈值过于严格，容易频繁触发高负载判定。

### 2.3 缓存 TTL 过短

**当前值**：`cachedSnapshot` TTL = 1 秒

**问题**：
- Prometheus 采集周期为 15 秒，1 秒 TTL 可能导致频繁的指标采集开销
- 短 TTL 无法有效减少 Prometheus 查询频率

**建议**：TTL 调整为 10 秒，与 Prometheus 采集周期匹配

### 2.4 阈值硬编码

**当前实现**：阈值固化在 `SystemLoadIndicatorImpl.calculateTotalScore()` 中

```java
int cpuScore = calculateMetricScore(cpu, 70, 90, 20);
int memoryScore = calculateMetricScore(memory, 75, 90, 15);
int p99Score = calculateMetricScore(p99, 30, 50, 10);
// ...
```

**问题**：无法动态调整，修改需要重新部署

### 2.5 监控维度不清晰

**当前问题**：

1. `loadScore` 是**本实例**的评分，但 UI 没有明确标注
2. 用户可能误以为是区域或全局评分
3. Admin API 部署在哪个区域，就只能看到哪个区域的监控
4. 无法对比不同区域的健康状态

### 2.4 缺少分层次监控

**当前能力**：
- ✅ 本实例详细指标
- ⚠️ 区域主机/实例列表（只读，无聚合）
- ❌ 无区域级汇总评分
- ❌ 无全局视图（跨区域）
- ❌ 无实例级下钻

---

## 3. 改进方案

### 3.1 负载计算改进

#### 3.1.1 混合指标计算

**原则**：
- JVM 指标：保持实例级（反映本实例状态）
- QPS/延迟：混合实例级 + 区域级（反映局部压力 + 整体压力）

**改进公式**：

```
实例负载评分 = Σ (指标值 × 权重 × 作用域系数)

其中：
- JVM CPU/内存/连接池：作用域系数 = 1.0（纯实例级）
- QPS：作用域系数 = 0.4 × 实例QPS + 0.6 × (区域QPS / 实例数)
- P99：作用域系数 = 0.5 × 实例P99 + 0.5 × 区域P99
```

**代码改进**：

```java
// SystemLoadIndicatorImpl.java 改进

private double getEffectiveQps() {
    double instanceQps = getInstanceQpsFromMicrometer();  // 本实例 QPS
    double regionQps = getRegionQps();                    // 区域 QPS
    int instanceCount = getInstanceCount();               // 区域实例数
    
    if (instanceCount <= 0) {
        return regionQps;
    }
    
    // 混合计算：40% 实例自身 + 60% 区域平均
    double regionAvgQps = regionQps / instanceCount;
    return instanceQps * 0.4 + regionAvgQps * 0.6;
}

private double getEffectiveP99Latency() {
    double instanceP99 = getInstanceP99FromMicrometer();  // 本实例 P99
    double regionP99 = getRegionP99();                    // 区域 P99
    
    if (regionP99 < 0) {
        return instanceP99;
    }
    
    // 混合计算：50% 实例 + 50% 区域
    return instanceP99 * 0.5 + regionP99 * 0.5;
}

private double getInstanceQpsFromMicrometer() {
    // 从 Micrometer 获取本实例 QPS
    double checkQps = getCounterRate("fota_device_checks_total");
    double reportQps = getCounterRate("fota_upgrade_events_total");
    return checkQps + reportQps;
}

private double getInstanceP99FromMicrometer() {
    // 从 Micrometer Timer 获取本实例 P99
    Timer timer = meterRegistry.find("http.server.requests").timer();
    if (timer != null) {
        return timer.takeSnapshot().percentile(0.99) * 1000; // 转换为 ms
    }
    return -1;
}
```

#### 3.1.2 P99 阈值调整

| 指标 | 警告阈值 | 严重阈值 | 说明 |
|------|---------|---------|------|
| P50 延迟 | 40ms | 60ms | 新增 |
| P99 延迟 | 50ms | 100ms | 放宽 |
| 其他指标 | 保持不变 | 保持不变 | - |

#### 3.1.3 实例数获取

从 `NodeRegistryService.listOnlineNodes()` 获取当前区域在线实例列表。

Node code 格式：`{region}-{host}-{domain}`，可解析出区域信息进行过滤。

#### 3.1.4 缓存 TTL 调整

| 缓存 | 当前值 | 建议值 | 说明 |
|------|-------|-------|------|
| cachedSnapshot | 1s | 10s | 与 Prometheus 15s 采集周期匹配 |

#### 3.1.5 阈值配置化

**字典类型定义**：`system.load.metrics`

**字典项结构**（存储在 `DictItem.extra` JSON 字段）：

```json
{
  "warning": 70,
  "critical": 90,
  "maxScore": 20
}
```

**指标配置项**（总分 100）：

| 字典项 value | 说明 | 阈值 | 权重 | 作用域 |
|--------------|------|------|------|--------|
| jvm_cpu | JVM CPU 使用率 | w=70%, c=90% | 15 | 实例 |
| jvm_memory | JVM 堆内存使用率 | w=75%, c=90% | 10 | 实例 |
| connection_pool | 连接池使用率 | w=80%, c=95% | 6 | 实例 |
| host_cpu | 主机 CPU 使用率 | w=70%, c=90% | 7 | 主机 |
| host_memory | 主机内存使用率 | w=75%, c=90% | 4 | 主机 |
| check_qps | Check API QPS（实例） | w=60%, c=80% | 8 | 实例 |
| report_qps | Report API QPS（实例） | w=60%, c=80% | 4 | 实例 |
| check_p50_latency | Check P50 延迟（实例） | w=40ms, c=60ms | 4 | 实例 |
| report_p50_latency | Report P50 延迟（实例） | w=40ms, c=60ms | 2 | 实例 |
| check_p99_latency | Check P99 延迟（实例） | w=50ms, c=100ms | 12 | 实例 |
| report_p99_latency | Report P99 延迟（实例） | w=50ms, c=100ms | 6 | 实例 |
| region_check_qps | Check API QPS（区域） | w=60%, c=80% | 10 | 区域 |
| region_report_qps | Report API QPS（区域） | w=60%, c=80% | 5 | 区域 |
| region_check_p99_latency | Check P99 延迟（区域） | w=50ms, c=100ms | 5 | 区域 |
| region_report_p99_latency | Report P99 延迟（区域） | w=50ms, c=100ms | 2 | 区域 |

**权重分布汇总**：

| 分类 | 指标 | 权重 | 小计 |
|------|------|------|------|
| 实例资源 | jvm_cpu + jvm_memory + connection_pool + host_cpu + host_memory | 15+10+6+7+4 | **42** |
| 实例 API | check_qps + report_qps + check_p50 + report_p50 + check_p99 + report_p99 | 8+4+4+2+12+6 | **36** |
| 区域 API | region_check_qps + region_report_qps + region_check_p99 + region_report_p99 | 10+5+5+2 | **22** |
| **总计** | | | **100** |

**关键设计决策**：

1. **实例资源占主导(42%)**：反映实例自身状态，是主要判断依据
2. **实例 API 次之(36%)**：反映本实例实时负载
3. **区域 API 辅助(22%)**：反映整体背景压力
4. **Check > Report**：Check API 总权重（实例8+12 + 区域10+5 = 35）远高于 Report API（实例4+6 + 区域5+2 = 17）
5. **P99 > P50**：P99 权重是 P50 的 2-3 倍，更关注长尾延迟
6. **阈值基于利用率**：QPS 类指标使用集群容量利用率（60%/80%），而非绝对值

#### 3.1.6 Sentinel 限流配置

**字典类型定义**：`system.sentinel.rules`

**字典项结构**（存储在 `DictItem.extra` JSON 字段）：

```json
{
  "grade": "QPS",
  "count": 1000,
  "controlBehavior": "RATE_LIMITER",
  "maxQueueingTimeMs": 50,
  "enabled": true
}
```

**配置项**：

| 字典项 value | 说明 | 默认值 |
|--------------|------|-------|
| upgrade_check | 升级检查 API | QPS=1000, 排队等待 |
| upgrade_report | 升级上报 API | QPS=2500, 快速失败 |

**默认值计算依据**：

基于单实例 10Mbps 出带宽限制计算：

```
1. 带宽换算
   10 Mbps = 10 × 1024 × 1024 bits/s = 10,485,760 bits/s
   换算成 KB/s = 10,485,760 / 8 / 1024 = 1,280 KB/s

2. Check API 响应大小（实测）
   - HTTP Header + Body ≈ 800 bytes
   - 预留冗余 ≈ 224 bytes
   - 估算响应大小 = 1 KB

3. Report API 响应大小（估算）
   - HTTP Header + Body ≈ 200 bytes

4. 纯 Check API 理论 QPS
   1,280 KB/s / 1 KB = 1,280 QPS

5. 混合场景（Check : Report ≈ 1 : 3）
   设 Check QPS = x, Report QPS = 3x
   总带宽 = x × 1 + 3x × 0.2 = 1.6x
   1.6x ≤ 1,280
   x ≤ 800

6. 限流阈值（预留 20% 余量）
   Check QPS = 800 × 0.8 ≈ 640 → 取整 1000
   Report QPS = 2400 × 0.8 ≈ 1920 → 取整 2500
```

**多实例共享带宽**：

如果一台主机部署多个实例，它们共享 10Mbps 带宽，需按比例调整：

| 主机实例数 | 单实例 Check QPS | 单实例 Report QPS |
|-----------|-----------------|------------------|
| 1 | 1000 | 2500 |
| 2 | 500 | 1250 |
| 3 | 330 | 830 |

**与 QPS 评分的关系**：

```
集群容量 = Σ (各实例 upgrade_check 限流值) + Σ (各实例 upgrade_report 限流值)

示例：4 实例 × (1000 + 2500) = 14,000 QPS 集群容量
```

**QPS 评分基准**：使用集群容量作为 100% 利用率基准，阈值 60%/80%

**缓存策略**：

| 操作 | 行为 |
|------|------|
| 首次加载 | 从数据库查询，缓存到 Redis（无 TTL） |
| 配置修改 | 清除 Redis 缓存，触发重新加载 |
| 服务启动 | 尝试从 Redis 读取，不存在则查数据库 |
| 配置缺失 | 使用代码中的默认值兜底 |

**Redis Key**：`fota:config:sentinel:{rule_value}`

### 3.2 区域级负载评分

#### 3.2.1 定义

**区域负载评分** = 反映整个区域的健康状态，用于监控大盘展示

**计算方式**：

```
区域负载评分 = 加权平均(
    JVM 指标: avg(各实例 JVM 指标),
    QPS: sum(各实例 QPS) / 集群容量,
    P99: max(各实例 P99),  // 关注最差情况
    主机指标: avg(各主机指标)
)
```

**简化计算**：

```java
public LoadSnapshot getRegionLoadSnapshot() {
    // 从 Prometheus 获取区域级聚合指标
    double regionCpu = getRegionAvgCpuUsage();
    double regionMemory = getRegionAvgMemoryUsage();
    double regionQps = getRegionTotalQps();
    double regionP99 = getRegionMaxP99();  // 取最大值，关注最差情况
    double regionPoolUsage = getRegionAvgPoolUsage();
    
    int totalScore = calculateTotalScore(
        regionCpu, regionMemory, regionQps, regionP99, regionPoolUsage
    );
    
    return LoadSnapshot.builder()
        .scope("region")
        .region(region)
        .totalScore(totalScore)
        .level(LoadLevel.fromScore(totalScore))
        .build();
}
```

#### 3.2.2 作用域区分

```java
public enum LoadScope {
    INSTANCE,   // 单个实例
    HOST,       // 单台主机
    REGION,     // 单个区域
    GLOBAL      // 全局（所有区域）
}

public record LoadSnapshot(
    LoadScope scope,          // 作用域
    String scopeId,           // 作用域标识（实例ID/主机名/区域名）
    int totalScore,
    LoadLevel level
    // ... 其他指标
) {}
```

### 3.3 监控 API 改进

#### 3.3.1 分层 API 设计

| API 端点 | 作用域 | 说明 |
|---------|-------|------|
| `GET /api/admin/monitor/global` | 全局 | 所有区域汇总（仅 Main 可用） |
| `GET /api/admin/monitor/regions` | 区域列表 | 各区域健康状态 |
| `GET /api/admin/monitor/region/{region}` | 单区域 | 区域详细指标 |
| `GET /api/admin/monitor/hosts` | 主机列表 | 区域内所有主机 |
| `GET /api/admin/monitor/host/{host}` | 单主机 | 主机详细指标 |
| `GET /api/admin/monitor/instances` | 实例列表 | 区域内所有实例 |
| `GET /api/admin/monitor/instance/{instance}` | 单实例 | 实例详细指标 |

#### 3.3.2 数据结构改进

**全局视图**：

```java
public record GlobalMetricsDTO(
    List<RegionSummaryDTO> regions,
    int totalActiveDevices,
    double totalQps,
    Instant timestamp
) {}

public record RegionSummaryDTO(
    String region,
    int loadScore,
    LoadLevel loadLevel,
    double qps,
    double avgP99Latency,
    int healthyInstances,
    int totalInstances,
    long activeDevices
) {}
```

**区域视图**：

```java
public record RegionMetricsDTO(
    // 区域汇总
    RegionSummaryDTO summary,
    
    // 下钻数据
    List<HostSummaryDTO> hosts,
    List<InstanceSummaryDTO> instances,
    
    // 趋势数据
    MonitorTrendsDTO trends,
    
    // 热点产品
    List<HotProductDTO> hotProducts,
    
    Instant timestamp
) {}
```

**实例视图**：

```java
public record InstanceMetricsDTO(
    String instance,
    String host,
    String region,
    
    // JVM 指标
    double cpuUsage,
    double heapUsage,
    double heapUsedMB,
    double heapMaxMB,
    int gcCount,
    double gcTimeMs,
    
    // API 指标
    double checkQps,
    double reportQps,
    double totalQps,
    double p50Latency,
    double p99Latency,
    
    // 连接池
    int activeConnections,
    int maxConnections,
    double connectionPoolUsage,
    
    // Sentinel
    double blockRate,
    String circuitState,
    int activeRequests,
    
    // 负载评分
    int loadScore,
    LoadLevel loadLevel,
    
    Instant timestamp
) {}
```

### 3.4 UI 改进

#### 3.4.1 分层导航

```
┌────────────────────────────────────────────────────────────────┐
│  系统监控                                                       │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  [全局]  [区域 ▼]  [主机 ▼]  [实例 ▼]                          │
│                                                                │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  当前视图: 区域 - main                                    │ │
│  └──────────────────────────────────────────────────────────┘ │
│                                                                │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  区域负载评分                                    45/100  │  │
│  │  [████████████████░░░░░░░░░░░░░░░░░░░░]  NORMAL         │  │
│  │                                                         │  │
│  │  健康实例: 5/6    总 QPS: 8,234    P99: 42ms            │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                                │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  实例状态                                                │  │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐        │  │
│  │  │ instance-1  │ │ instance-2  │ │ instance-3  │        │  │
│  │  │ ● NORMAL    │ │ ● NORMAL    │ │ ⚠ HIGH      │        │  │
│  │  │ QPS: 1,823  │ │ QPS: 1,756  │ │ QPS: 2,455  │        │  │
│  │  │ P99: 38ms   │ │ P99: 41ms   │ │ P99: 67ms   │        │  │
│  │  │ [详情]      │ │ [详情]      │ │ [详情]      │        │  │
│  │  └─────────────┘ └─────────────┘ └─────────────┘        │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

#### 3.4.2 实例详情页

```
┌────────────────────────────────────────────────────────────────┐
│  实例详情 - main-host1-instance1                               │
│  区域: main    主机: host1    状态: ● NORMAL                   │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │ 负载评分      │  │ CPU 使用率    │  │ 堆内存        │         │
│  │    42        │  │    58%       │  │   1.2/2.0 GB │         │
│  │   NORMAL     │  │              │  │      60%     │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
│                                                                │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  API 指标                                                │  │
│  │  Check QPS: 1,823    Report QPS: 412    P99: 38ms       │  │
│  │                                                         │  │
│  │  [最近 1 小时 QPS 趋势图]                                │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                                │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  Sentinel 状态                                           │  │
│  │  熔断器: ● 关闭    限流率: 1.2%    活跃请求: 23          │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                                │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  JVM 详情                                                │  │
│  │  Young GC: 156 次 / 1.2s    Old GC: 2 次 / 0.3s         │  │
│  │  连接池: 45/100 (45%)      线程数: 89                    │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

---

## 4. 实施计划

### 4.1 阶段 1：阈值配置化（优先级：高）

| 任务 | 说明 | 预计工时 |
|------|------|---------|
| 新增字典类型 | `system.load.metrics` 及字典项 | 1h |
| 阈值加载服务 | 从字典/Redis 加载阈值配置 | 2h |
| 阈值缓存实现 | Redis 永久缓存 + 清除机制 | 1h |

### 4.2 阶段 2：负载计算改进（优先级：高）

| 任务 | 说明 | 预计工时 |
|------|------|---------|
| 混合 QPS 计算 | 实例 QPS + 区域 QPS 加权 | 2h |
| 混合 P99 计算 | 实例 P99 + 区域 P99 加权 | 2h |
| 实例数获取 | NodeRegistryService 集成 | 1h |
| 缓存 TTL 调整 | 1s → 10s | 0.5h |
| 阈值更新 | P50/P99 阈值调整 | 0.5h |
| 单元测试 | 负载计算测试 | 2h |

### 4.3 阶段 3：区域级监控（优先级：高）

| 任务 | 说明 | 预计工时 |
|------|------|---------|
| 区域负载评分 | 实现 getRegionLoadSnapshot | 3h |
| API 改进 | 新增区域级端点 | 2h |
| 数据结构改进 | LoadSnapshot 增加作用域 | 1h |
| 单元测试 | 区域监控测试 | 2h |

### 4.3 阶段 3：UI 分层展示（优先级：中）

| 任务 | 说明 | 预计工时 |
|------|------|---------|
| 区域选择器 | 前端区域切换组件 | 2h |
| 实例列表展示 | 区域内实例状态卡片 | 3h |
| 实例详情页 | 实例下钻详情 | 4h |
| 联调测试 | 前后端联调 | 2h |

### 4.4 阶段 4：全局视图（优先级：低）

| 任务 | 说明 | 预计工时 |
|------|------|---------|
| 区域数据上报 | Region 定期上报状态到 Main | 4h |
| 全局聚合 API | Main 汇总各区域数据 | 3h |
| 全局监控页 | 前端全局视图 | 4h |

---

## 5. 改进前后对比

### 5.1 负载计算

| 维度 | 改进前 | 改进后 |
|------|-------|-------|
| JVM 指标 | 实例级 ✅ | 实例级（不变） |
| QPS | 区域聚合 | 40% 实例 + 60% 区域 |
| P99 | 区域聚合 | 50% 实例 + 50% 区域 |
| 作用域 | 混乱 | 明确（实例级 / 区域级） |

### 5.2 监控展示

| 维度 | 改进前 | 改进后 |
|------|-------|-------|
| 负载评分 | 本实例（未标注） | 支持实例/区域/全局切换 |
| 实例列表 | 只读列表 | 带状态卡片，可下钻 |
| 区域对比 | 不支持 | 全局视图支持 |
| 作用域标注 | 无 | 明确显示当前查看范围 |
| 实例数获取 | 无 | NodeRegistryService |
| 缓存 TTL | 1s | 10s |
| 阈值配置 | 硬编码 | 字典类型 + Redis 缓存 |

### 5.3 阈值配置

| 指标 | 改进前 | 改进后 |
|------|-------|-------|
| P50 警告 | 无 | 40ms |
| P50 严重 | 无 | 60ms |
| P99 警告 | 30ms | 50ms |
| P99 严重 | 50ms | 100ms |
| 配置方式 | 硬编码 | 字典类型（可动态调整） |

---

## 6. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| Prometheus 查询增加 | 性能 | 增加缓存（10s TTL），异步查询 |
| 混合计算复杂度 | 可维护性 | 充分注释，单元测试覆盖 |
| UI 改动较大 | 用户体验 | 分阶段发布，保留原入口 |
| 实例数获取延迟 | 计算误差 | 本地缓存实例列表，定期刷新 |
| 字典配置缺失 | 运行异常 | 使用代码中的默认值兜底 |
| Redis 缓存失效 | 性能 | 降级到数据库查询 |

---

## 7. 变更记录

| 日期 | 版本 | 变更 |
|------|------|------|
| 2026-03-13 | v1.0 | 初版，基于问题分析提出改进方案 |
| 2026-03-13 | v1.1 | 增加：阈值配置化（字典类型）、缓存 TTL 调整（10s）、实例数获取方案、P50 阈值定义 |
| 2026-03-13 | v1.2 | 增加：Sentinel 限流配置字典化、集群容量定义、Check API 权重提升（5>2） |
