# Sprint 4: 动态周期调整与系统可观测性

> **时间**: 2026-03 (Week 8-10)
> **目标**: 实现基于系统负载的动态周期调整能力，集成 Sentinel 限流熔断，自建监控仪表盘
> **范围**: 单区域多实例场景

**Sprint Owner**: FOTA 后端组
**文档版本**: v1.0
**创建日期**: 2026-03-08
**最后更新**: 2026-03-08

---

## 📋 Sprint 概览

### 背景

随着设备数量增长，固定检查间隔无法适应系统负载变化：
- 高负载时：大量设备同时请求，系统压力大
- 低负载时：设备频繁检查，浪费资源
- 需要一种机制：根据系统状态动态调整设备检查周期

### 目标

1. **动态周期调整**：根据系统负载动态调整 `checkInterval`
2. **智能退避**：被限流时返回智能计算的退避时间
3. **系统集成监控**：在管理后台展示系统状态和负载趋势
4. **限流熔断保护**：集成 Sentinel 保护核心 API

### 范围

- ✅ **包含**: 
  - Sentinel 限流熔断集成
  - 系统负载评估服务
  - 动态 checkInterval 计算
  - 智能退避算法
  - 监控仪表盘（集成到管理后台）
- ❌ **不包含**: 
  - 跨区域同步（Sprint 5）
  - CDN 预热
  - MQTT 长连接

### 验收标准

- [ ] 升级检查 API 集成 Sentinel 限流保护
- [ ] 被限流时返回动态退避时间（基于负载计算）
- [ ] 系统负载评估服务可获取综合评分 (0-100)
- [ ] checkInterval 根据负载动态调整
- [ ] 管理后台可查看实时监控数据
- [ ] 管理后台可手动调整控制参数
- [ ] 单元测试覆盖率 ≥ 60%

---

## 🏗️ 系统架构

### 整体架构图

```
                                    ┌─────────────────────────────────────┐
                                    │         fota-ui 管理后台             │
                                    │   ┌─────────────────────────────┐   │
                                    │   │      监控仪表盘页面          │   │
                                    │   │  - 系统负载评分              │   │
                                    │   │  - API QPS 趋势              │   │
                                    │   │  - 限流/熔断状态             │   │
                                    │   │  - 控制参数调整              │   │
                                    │   └─────────────────────────────┘   │
                                    └──────────────────┬──────────────────┘
                                                       │ Monitor API
                                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              fota-service                                    │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    Sentinel 保护层                                   │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                  │   │
│  │  │ FlowSlot    │  │ DegradeSlot │  │ SystemSlot  │                  │   │
│  │  │ QPS 限流    │  │ 熔断降级     │  │ 系统自适应   │                  │   │
│  │  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘                  │   │
│  │         │                │                │                         │   │
│  │         ▼                ▼                ▼                         │   │
│  │  ┌─────────────────────────────────────────────────────────────┐   │   │
│  │  │              SmartBackoffHandler (智能退避)                  │   │   │
│  │  │  被拒绝时根据负载计算动态退避时间                            │   │   │
│  │  └─────────────────────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                      │                                       │
│                                      ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    业务逻辑层                                        │   │
│  │  UpgradeCheckController → UpgradeCheckService → UpgradeResponseBuilder │
│  │                                      │                               │   │
│  │                                      ▼                               │   │
│  │                    DynamicIntervalService                           │   │
│  │                    (动态 checkInterval 计算)                         │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                      │                                       │
│                                      ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    负载评估层                                        │   │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐      │   │
│  │  │ SystemLoad      │  │ LoadTrend       │  │ MetricsCollector│      │   │
│  │  │ Indicator       │  │ Analyzer        │  │ (Micrometer)    │      │   │
│  │  │ (综合评分)       │  │ (趋势预测)       │  │ (指标采集)       │      │   │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────┘      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                      │                                       │
│                                      ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    数据存储层                                        │   │
│  │  Redis:                                                              │   │
│  │  - ctrl:global         (全局控制参数)                                │   │
│  │  - ctrl:product:*      (产品级参数)                                  │   │
│  │  - load:history:*      (负载历史)                                    │   │
│  │  - sentinel:rules:*    (Sentinel 规则)                               │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 请求处理流程

```
设备请求 /v1/upgrade/check
        │
        ▼
┌───────────────────────┐
│ Sentinel SphU.entry() │
└───────────┬───────────┘
            │
    ┌───────┴───────┐
    │               │
    ▼               ▼
┌───────┐     ┌─────────────────────┐
│ 通过  │     │ 被拒绝              │
└───┬───┘     └──────────┬──────────┘
    │                    │
    ▼                    ▼
┌───────────────────┐  ┌───────────────────────┐
│ UpgradeCheck      │  │ SmartBackoffHandler   │
│ Service           │  │ 1. 获取当前负载        │
│                   │  │ 2. 分析负载趋势        │
│ ┌───────────────┐ │  │ 3. 计算退避时间        │
│ │DynamicInterval│ │  │ 4. 返回响应            │
│ │Service        │ │  └───────────────────────┘
│ │ ↓             │ │
│ │根据负载计算   │ │
│ │checkInterval  │ │
│ └───────────────┘ │
└─────────┬─────────┘
          │
          ▼
    返回响应给设备
```

---

## 📐 技术方案

### 1. Sentinel 集成

#### 1.1 需要保护的资源

| 资源 | 保护策略 | 阈值 | 说明 |
|------|---------|------|------|
| `/v1/upgrade/check` | QPS 限流 | 2500/单实例 | 核心热路径（集群 4-6 实例可达 10000-15000 QPS） |
| `UpgradeCheckService` | 熔断降级 | 慢调用50ms，比例50% | 核心业务 |
| Redis 操作 | 并发限流 | 200线程 | 保护连接池 |

> **QPS 能力说明**：
> - Spring Boot 单实例（含 Redis IO）实测 QPS 约 2000-3000
> - 集群部署 4-6 实例可支撑 10,000-15,000 QPS
> - 限流阈值设为 2500 是保守值，留有余量

#### 1.2 限流规则配置

```yaml
# 升级检查 API 限流
- resource: /v1/upgrade/check
  grade: QPS
  count: 2500            # 单实例保守值（集群 4-6 实例）
  controlBehavior: WAIT      # 排队等待
  maxQueueingTimeMs: 50

# 熔断降级规则
- resource: UpgradeCheckService
  grade: SLOW_REQUEST_RATIO
  count: 50                  # 慢调用阈值 50ms
  timeWindow: 30s
  minRequestAmount: 100
  slowRatioThreshold: 0.5

# 系统自适应保护
- type: SYSTEM
  cpu: 80                    # CPU 阈值 80%
  avgRt: 50                  # 平均响应 50ms
  maxThread: 500             # 最大并发 500
  qps: 12000                 # 入口 QPS
```

#### 1.3 规则持久化

- **存储**: Redis
- **管理方式**: 
  - 方式1: 通过管理后台 API 修改
  - 方式2: 启动时从 Redis 加载，运行时动态更新

#### 1.4 多实例独立限流说明

**Sentinel 默认行为：每个实例独立限流，不共享计数**

```
┌─────────────────────────────────────────────────────────────────┐
│                         负载均衡器                               │
│                         (Nginx/ALB)                             │
└────────────────────────┬────────────────────────────────────────┘
                         │ 轮询分发
        ┌────────────────┼────────────────┐
        │                │                │
        ▼                ▼                ▼
   ┌─────────┐      ┌─────────┐      ┌─────────┐
   │ fota-1  │      │ fota-2  │      │ fota-3  │
   │         │      │         │      │         │
   │ Sentinel│      │ Sentinel│      │ Sentinel│
   │ 2500qps │      │ 2500qps │      │ 2500qps │
   │ (独立)  │      │ (独立)  │      │ (独立)  │
   └─────────┘      └─────────┘      └─────────┘

每个实例独立统计 QPS，互不影响
集群总 QPS = 单实例阈值 × 实例数
```

**限流范围规划**：

| API 路径 | 是否限流 | 阈值 | 说明 |
|---------|---------|------|------|
| `/v1/upgrade/check` | ✅ 限流 | 2500/实例 | Device API，高频请求 |
| `/v1/upgrade/report` | ✅ 限流 | 5000/实例 | Device API，异步处理可放宽 |
| `/api/admin/*` | ❌ 不限流 | - | Admin API，管理员操作，请求量小 |
| `/fota/version/query` | ✅ 限流 | 2500/实例 | 兼容老接口，与 check 共用阈值 |

**集群容量估算**：

| 实例数 | 单实例 QPS | 集群总 QPS | 适用场景 |
|-------|-----------|-----------|---------|
| 4 | 2500 | 10,000 | 最小部署 |
| 5 | 2500 | 12,500 | 推荐部署 |
| 6 | 2500 | 15,000 | 高可用部署 |
| 8+ | 2500 | 20,000+ | 大规模部署 |

> **注意**：Admin API 不限流，因为：
> 1. 管理员操作，请求量极小（每天几十到几百次）
> 2. 不会与 Device API 抢占资源
> 3. 避免影响管理员正常操作

---

### 2. 系统负载评估

#### 2.1 SystemLoadIndicator 接口

```java
/**
 * 系统负载指标服务
 */
public interface SystemLoadIndicator {
    
    /**
     * 获取当前负载快照（带 1 秒缓存）
     */
    LoadSnapshot getSnapshot();
    
    /**
     * 获取当前负载级别
     */
    LoadLevel getLoadLevel();
    
    /**
     * 判断系统是否过载
     */
    boolean isOverloaded();
}

/**
 * 负载快照
 */
record LoadSnapshot(
    Instant timestamp,
    int totalScore,           // 0-100
    LoadLevel level,
    double cpuUsage,          // CPU 使用率 %
    double memoryUsage,       // JVM 堆内存使用率 %
    double qps,               // 当前 QPS
    double p99Latency,        // P99 延迟 ms
    double connectionPoolUsage // 连接池使用率 %
) {
    public boolean isOverloaded() {
        return level == LoadLevel.HIGH || level == LoadLevel.CRITICAL;
    }
    
    public boolean isCritical() {
        return level == LoadLevel.CRITICAL;
    }
}

/**
 * 负载级别
 */
enum LoadLevel {
    LOW,       // 0-24: 系统空闲
    NORMAL,    // 25-49: 正常负载
    HIGH,      // 50-74: 高负载
    CRITICAL   // 75-100: 过载
}
```

#### 2.2 负载评分算法

| 指标 | 权重 | 采集方式 | 警告阈值 | 严重阈值 |
|------|------|---------|---------|---------|
| CPU 使用率 | 30% | OperatingSystemMXBean | 70% | 90% |
| JVM 堆内存 | 20% | MemoryMXBean | 75% | 90% |
| API QPS | 20% | Micrometer Counter | 8000 | 12000 |
| 响应延迟 P99 | 15% | Micrometer Timer | 30ms | 50ms |
| 连接池使用率 | 15% | Lettuce Metrics | 80% | 95% |

**评分公式（伪代码）**:

```
function calculateScore(value, warning, critical, maxScore):
    if value >= critical:
        return maxScore
    if value >= warning:
        return maxScore * 0.3 + maxScore * 0.7 * (value - warning) / (critical - warning)
    return maxScore * 0.3 * value / warning

totalScore = sum(calculateScore(metric) for each metric)
```

---

### 3. 动态周期调整

#### 3.1 DynamicIntervalService 接口

```java
/**
 * 动态间隔计算服务
 */
public interface DynamicIntervalService {
    
    /**
     * 计算动态检查间隔
     * @param productId 产品ID（用于产品级配置）
     * @param autoMode 是否自动检查模式
     * @return 检查间隔（秒）
     */
    int calculateCheckInterval(Long productId, Boolean autoMode);
    
    /**
     * 计算动态下载延迟
     * @param productId 产品ID
     * @return 下载延迟（秒）
     */
    int calculateDownloadDelay(Long productId);
}
```

#### 3.2 间隔调整算法（伪代码）

```
function calculateCheckInterval(productId, autoMode):
    // 1. 检查手动覆盖
    if hasManualOverride(productId):
        return getManualInterval(productId)
    
    // 2. 获取基础间隔
    baseInterval = autoMode ? 86400 : 3600
    
    // 3. 获取负载级别
    loadLevel = systemLoadIndicator.getLoadLevel()
    
    // 4. 计算调整系数
    multiplier = switch (loadLevel):
        LOW      -> 0.8 ~ 1.0
        NORMAL   -> 1.0 ~ 1.5
        HIGH     -> 1.5 ~ 2.5
        CRITICAL -> 2.5 ~ 4.0
    
    // 5. 应用产品级调整（可选）
    if hasProductMultiplier(productId):
        multiplier *= getProductMultiplier(productId)
    
    // 6. 限制范围
    return clamp(baseInterval * multiplier, MIN=1800, MAX=172800)
```

#### 3.3 调整系数对照表

| 负载级别 | 评分范围 | 间隔系数 | 基础1h | 基础24h |
|---------|---------|---------|--------|---------|
| LOW | 0-24 | 0.8x-1.0x | 48-60分钟 | 19-24小时 |
| NORMAL | 25-49 | 1.0x-1.5x | 60-90分钟 | 24-36小时 |
| HIGH | 50-74 | 1.5x-2.5x | 90-150分钟 | 36-60小时 |
| CRITICAL | 75-100 | 2.5x-4.0x | 150-240分钟 | 60-96小时 |

---

### 4. 智能退避算法

#### 4.1 SmartBackoffHandler 接口

```java
/**
 * 智能退避处理器
 */
public interface SmartBackoffHandler {
    
    /**
     * 计算智能退避时间
     * @param blockedReason 被拒绝原因（FLOW_QPS/DEGRADE/SYSTEM）
     * @param loadSnapshot 当前负载快照
     * @return 退避结果
     */
    BackoffResult calculateBackoff(String blockedReason, LoadSnapshot loadSnapshot);
}

/**
 * 退避结果
 */
record BackoffResult(
    int retryAfterSeconds,      // 重试等待时间（秒）
    Instant retryAfterTime,     // 建议重试的具体时间点
    String reason,              // 被拒绝原因
    String suggestion           // 给设备的建议
) {}
```

#### 4.2 退避算法（伪代码）

```
function calculateBackoff(blockedReason, loadSnapshot):
    // 1. 基础退避时间
    baseBackoff = switch (blockedReason):
        FLOW_QPS         -> 60      # 1分钟
        FLOW_CONCURRENCY -> 120     # 2分钟
        DEGRADE          -> 300     # 5分钟
        SYSTEM           -> 600     # 10分钟
    
    // 2. 根据负载级别调整
    loadMultiplier = switch (loadSnapshot.level):
        LOW      -> 0.5
        NORMAL   -> 1.0
        HIGH     -> 2.0
        CRITICAL -> 4.0
    
    // 3. 根据负载趋势调整（如果历史数据可用）
    trendMultiplier = 1.0
    if hasTrendData():
        trend = analyzeTrend(getLast30Minutes())
        if trend == INCREASING:
            trendMultiplier = 1.5   # 负载上升，多等一会
        elif trend == DECREASING:
            trendMultiplier = 0.8   # 负载下降，可以早点重试
    
    // 4. 计算最终退避时间
    retryAfter = baseBackoff * loadMultiplier * trendMultiplier
    
    // 5. 限制范围并添加抖动
    retryAfter = clamp(retryAfter, 60, 7200)  # 1分钟 ~ 2小时
    retryAfter += random(-10%, +10%)          # 随机抖动
    
    return BackoffResult(
        retryAfterSeconds = retryAfter,
        retryAfterTime = now() + retryAfter,
        reason = blockedReason,
        suggestion = "系统繁忙，建议在 {retryAfterTime} 后重试"
    )
```

#### 4.3 Sentinel BlockHandler 集成

```java
/**
 * 升级检查 API 的 Sentinel BlockHandler
 */
@Component
public class UpgradeCheckBlockHandler {
    
    private final SmartBackoffHandler backoffHandler;
    private final SystemLoadIndicator loadIndicator;
    
    /**
     * 处理被限流的请求
     * 方法签名必须符合 Sentinel BlockHandler 规范
     */
    public ResponseEntity<UpgradeCheckRespDTO> handleBlock(
            UpgradeCheckReqDTO request,
            BlockException ex,
            HttpServletRequest httpRequest) {
        
        // 1. 获取负载和计算退避
        LoadSnapshot load = loadIndicator.getSnapshot();
        String reason = getBlockedReason(ex);
        BackoffResult backoff = backoffHandler.calculateBackoff(reason, load);
        
        // 2. 返回限流响应
        return ResponseEntity.ok(UpgradeCheckRespDTO.builder()
            .code(UpgradeDecision.RATE_LIMITED.getCode())
            .control(UpgradeCheckRespDTO.Control.builder()
                .checkInterval(backoff.retryAfterSeconds())
                .downloadDelay(backoff.retryAfterSeconds())
                .build())
            .build());
    }
}
```

---

### 5. 控制参数管理

#### 5.1 Redis 数据结构

```
# 全局控制参数
ctrl:global = {
  "checkIntervalMultiplier": "1.0",
  "downloadDelayMultiplier": "1.0",
  "forceMaintenance": "false",
  "maintenanceMessage": "",
  "updatedAt": "2026-03-08T10:00:00Z",
  "updatedBy": "admin"
}

# 产品级控制参数
ctrl:product:{productId} = {
  "enabled": "true",
  "checkIntervalMultiplier": "1.5",
  "priority": "high"
}

# 负载历史（7天）
load:history:{date} = [
  {"timestamp":"...","score":45,"level":"NORMAL","cpu":60,"memory":55},
  ...
]
```

#### 5.2 控制 API

```java
/**
 * 控制参数管理 API
 */
@RestController
@RequestMapping("/api/admin/control")
public class ControlParameterController {
    
    /** 获取全局控制参数 */
    @GetMapping("/global")
    ControlParameterDTO getGlobalConfig();
    
    /** 更新全局控制参数 */
    @PutMapping("/global")
    void updateGlobalConfig(ControlParameterDTO config, String operator);
    
    /** 获取产品级控制参数 */
    @GetMapping("/product/{productId}")
    ControlParameterDTO getProductConfig(Long productId);
    
    /** 设置产品级控制参数 */
    @PutMapping("/product/{productId}")
    void updateProductConfig(Long productId, ControlParameterDTO config);
    
    /** 触发强制降级 */
    @PostMapping("/degrade/force")
    void forceDegrade(String reason, Duration duration, String operator);
    
    /** 解除强制降级 */
    @DeleteMapping("/degrade/force")
    void cancelForceDegrade(String operator);
}
```

---

### 6. 监控仪表盘

#### 6.1 监控 API

```java
/**
 * 监控数据 API
 */
@RestController
@RequestMapping("/api/admin/monitor")
public class MonitorApiController {
    
    /** 获取实时系统指标 */
    @GetMapping("/realtime")
    RealtimeMetrics getRealtimeMetrics();
    
    /** 获取历史趋势数据 */
    @GetMapping("/history")
    List<MetricsPoint> getHistoryMetrics(String duration, String interval);
    
    /** 获取 Sentinel 限流/熔断状态 */
    @GetMapping("/sentinel")
    SentinelStatus getSentinelStatus();
    
    /** 获取当前活跃告警 */
    @GetMapping("/alerts")
    List<ActiveAlert> getActiveAlerts();
}

/**
 * 实时指标快照
 */
record RealtimeMetrics(
    int loadScore,
    String loadLevel,
    double cpuUsage,
    double memoryUsage,
    double currentQps,
    double p99Latency,
    int activeRequests,
    double blockRate,
    String circuitState,
    Instant timestamp
) {}
```

#### 6.2 前端页面布局

```
┌────────────────────────────────────────────────────────────────┐
│  系统监控                                    🟢 正常  [刷新]    │
├────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  系统负载评分                                    45/100  │  │
│  │  [████████████████░░░░░░░░░░░░░░░░░░░░]  NORMAL         │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │ CPU 使用率    │  │ 内存使用率    │  │ 当前 QPS     │         │
│  │    65%       │  │    58%       │  │   3,245      │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
│                                                                │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  API QPS 趋势（最近 1 小时）                              │  │
│  │  [折线图]                                                │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                                │
│  ┌────────────────────────┐  ┌────────────────────────────┐   │
│  │  限流统计               │  │  熔断器状态                 │   │
│  │  ✅ 通过: 98.5%        │  │  /v1/upgrade/check         │   │
│  │  ⚠️ 限流: 1.2%         │  │  状态: 🟢 关闭             │   │
│  │  ❌ 熔断: 0.3%         │  │  慢调用比例: 12%           │   │
│  └────────────────────────┘  └────────────────────────────┘   │
│                                                                │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  控制参数                                                 │  │
│  │  全局间隔乘数: [1.2]  下载延迟: [360s]  [更新]            │  │
│  │  强制维护: [ ] 开启  维护消息: [________________]         │  │
│  └─────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
```

---

## 📅 任务分解

### 阶段 1: Sentinel 集成 (Day 1-2)

**预计时间**: 2天
**分支**: `feature/sprint-4-sentinel`

| # | 任务 | 说明 | 预计 | 状态 |
|---|------|------|------|------|
| 1.1 | 引入 Sentinel 依赖 | sentinel-spring-boot-starter | 0.5h | ⏸️ |
| 1.2 | 配置 Sentinel 规则 | QPS 限流 + 熔断降级 | 1h | ⏸️ |
| 1.3 | 实现 SmartBackoffHandler | 智能退避算法 | 2h | ⏸️ |
| 1.4 | 实现 UpgradeCheckBlockHandler | 被拒绝时返回动态间隔 | 1h | ⏸️ |
| 1.5 | 规则持久化到 Redis | 支持动态修改 | 1h | ⏸️ |
| 1.6 | 单元测试 | Sentinel 相关测试 | 1h | ⏸️ |

### 阶段 2: 系统负载评估 (Day 3)

**预计时间**: 1天
**分支**: `feature/sprint-4-load-indicator`

| # | 任务 | 说明 | 预计 | 状态 |
|---|------|------|------|------|
| 2.1 | SystemLoadIndicator 实现 | 综合评分服务 | 2h | ⏸️ |
| 2.2 | LoadTrendAnalyzer | 负载趋势分析（预测） | 2h | ⏸️ |
| 2.3 | 负载历史记录 | Redis 存储 7 天历史 | 1h | ⏸️ |
| 2.4 | Micrometer 指标补充 | 自定义 Gauge/Counter | 1h | ⏸️ |
| 2.5 | 单元测试 | 负载评估测试 | 1h | ⏸️ |

### 阶段 3: 动态周期调整 (Day 4)

**预计时间**: 1天
**分支**: `feature/sprint-4-dynamic-interval`

| # | 任务 | 说明 | 预计 | 状态 |
|---|------|------|------|------|
| 3.1 | DynamicIntervalService | 根据负载计算间隔 | 2h | ⏸️ |
| 3.2 | 控制参数 Redis 存储 | 全局 + 产品级 | 1h | ⏸️ |
| 3.3 | 管理后台 API | 参数查询/更新/降级 | 2h | ⏸️ |
| 3.4 | UpgradeResponseBuilder 集成 | 注入动态间隔 | 1h | ⏸️ |
| 3.5 | 单元测试 | 动态间隔测试 | 1h | ⏸️ |

### 阶段 4: 监控集成 (Day 5-6)

**预计时间**: 2天
**分支**: `feature/sprint-4-monitor`

| # | 任务 | 说明 | 预计 | 状态 |
|---|------|------|------|------|
| 4.1 | MonitorApiController | 监控数据 API | 2h | ⏸️ |
| 4.2 | 前端监控仪表盘 | Vue 组件开发 | 4h | ⏸️ |
| 4.3 | 实时数据刷新 | 轮询/WebSocket（可选） | 1h | ⏸️ |
| 4.4 | 告警规则 | 后端判断 + 前端展示 | 1h | ⏸️ |
| 4.5 | 集成测试 | 监控功能测试 | 1h | ⏸️ |

### 阶段 5: 验收 (Day 7)

**预计时间**: 1天

| # | 任务 | 说明 | 预计 | 状态 |
|---|------|------|------|------|
| 5.1 | 压测验证 | 限流 + 退避效果 | 2h | ⏸️ |
| 5.2 | 单元测试补充 | 提升覆盖率 | 2h | ⏸️ |
| 5.3 | 文档更新 | Sprint 4 完成报告 | 1h | ⏸️ |

---

## 📊 进度跟踪

```
Sprint 4: [░░░░░░░░░░░░░░░░░░░░] 0%

阶段 1: Sentinel 集成       ⏸️ 待开始
阶段 2: 系统负载评估         ⏸️ 待开始
阶段 3: 动态周期调整         ⏸️ 待开始
阶段 4: 监控集成             ⏸️ 待开始
阶段 5: 验收                 ⏸️ 待开始

总计: 7 天 (约 1.5 周)
```

---

## ⚠️ 风险与缓解

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| Sentinel 学习成本 | 中 | 中 | 先做技术预研，参考官方文档 |
| 负载评分不准确 | 高 | 中 | 多指标综合评估，持续调优 |
| 智能退避时间过长 | 中 | 低 | 设置最大退避时间（2小时） |
| 前端监控开发量大 | 中 | 中 | 先实现核心指标，后续迭代优化 |
| 动态调整导致设备行为异常 | 高 | 低 | 设置合理的上下限，充分测试 |

---

## 📝 变更日志

### 2026-03-08 (初版 - v1.0)
- 📝 创建 Sprint 4 计划文档
- 📝 确定 Sentinel 集成方案
- 📝 确定智能退避算法
- 📝 确定自建监控方案
- 📝 细化任务分解和时间估算

---

## 🔗 相关文档

- [Sprint 1 计划](./sprint-1.md)
- [Sprint 2 计划](./sprint-2-frontend.md)
- [Sprint 3 计划](./sprint-3-core-pipeline.md)
- [产品需求文档](../01-product/prd.md)
- [技术架构文档](../02-architecture/fota-architecture.md)
- [升级检查 API 规范](../04-technical/upgrade-check-api.md)
