# Sprint 4: 动态周期调整与系统可观测性

> **时间**: 2026-03 (Week 8-10)
> **目标**: 实现基于系统负载的动态周期调整能力，集成 Sentinel 限流熔断，自建监控仪表盘
> **范围**: 单区域多实例场景

**Sprint Owner**: FOTA 后端组
**文档版本**: v1.2
**创建日期**: 2026-03-08
**最后更新**: 2026-03-14

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
  - 负载控制运行期配置设计固化
- ❌ **不包含**: 
  - 跨区域同步（Sprint 5）
  - CDN 预热
  - MQTT 长连接

### 验收标准

- [x] 升级检查 API 集成 Sentinel 限流保护
- [x] 被限流时返回动态退避时间（基于负载计算）
- [x] 系统负载评估服务可获取综合评分 (0-100)
- [x] checkInterval 根据负载动态调整
- [x] 管理后台可查看实时监控数据
- [x] 管理后台可手动调整控制参数
- [x] 单元测试覆盖率 ≥ 60%
- [x] 负载评分阈值与权重运行期配置化
- [x] 实例级 / 区域级评分模型统一管理
- [x] Sentinel 规则纳入统一负载控制配置
- [x] 负载控制配置接入字典并支持保存即生效

### 本阶段新增说明

Sprint 4 已完成默认动态周期、监控页和 Sentinel 基础集成，但“运行期统一配置”仍是待补齐能力。该能力的设计基线已单独固化为：

- [负载控制运行期配置设计](../04-technical/load-control-runtime-configuration.md)
- [多实例负载评估与动态周期优化方案](../04-technical/multi-instance-monitoring-improvement.md)

当前阶段约束：

- 直接通过“系统管理 > 字典管理”维护配置
- 不单独建设负载控制配置页

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

#### 1.3 规则持久化（混合方案）

**设计原则**：配置文件提供默认值 + Redis 支持运行时动态覆盖

```
启动加载流程:
┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│  application.yml │ ──► │   合并覆盖        │ ──► │   最终生效规则    │
│  (默认规则)      │     │   Redis 规则      │     │                  │
└──────────────────┘     └──────────────────┘     └──────────────────┘
        │                         │
        ▼                         ▼
   版本控制                    运行时动态
   部署时确定                  无需重启
```

**配置文件 (application.yml)**:
```yaml
app:
  sentinel:
    enabled: true
    rule-source: hybrid  # config(仅配置文件)/redis(仅Redis)/hybrid(合并)
    flow-rules:
      - resource: upgrade:check
        grade: QPS
        count: 2500
        control-behavior: RATE_LIMITER
        max-queueing-time-ms: 50
        enabled: true
      - resource: upgrade:report
        grade: QPS
        count: 5000
        enabled: true
    degrade-rules:
      - resource: UpgradeCheckService
        grade: RT
        count: 50
        time-window: 30
        min-request-amount: 100
        slow-ratio-threshold: 0.5
        enabled: true
    redis:
      flow-rules-key: fota:sentinel:flow:rules
      degrade-rules-key: fota:sentinel:degrade:rules
```

**规则来源模式**:

| 模式 | 行为 | 适用场景 |
|------|------|---------|
| `config` | 仅使用配置文件 | 规则固定，无需动态调整 |
| `redis` | 仅使用 Redis | 完全动态，Redis 必须可用 |
| `hybrid` | 配置文件 + Redis 合并（推荐） | 有默认值，支持动态覆盖 |

**合并策略 (hybrid 模式)**:
1. 加载配置文件中的规则作为基础
2. 从 Redis 加载动态规则
3. 按 `resource` 字段合并：Redis 规则覆盖配置文件规则
4. Redis 中不存在的规则保留配置文件默认值

**管理 API**:

```
GET  /api/admin/sentinel/config     # 查看当前配置
GET  /api/admin/sentinel/rules      # 查看当前规则
PUT  /api/admin/sentinel/rules/flow   # 更新流控规则（写入 Redis）
PUT  /api/admin/sentinel/rules/degrade # 更新熔断规则（写入 Redis）
POST /api/admin/sentinel/rules/refresh # 手动刷新规则
```

**多实例同步**:
- 所有实例共享同一个 Redis
- 规则更新写入 Redis 后，各实例每 30 秒自动刷新
- 也可通过 `/rules/refresh` API 手动触发立即生效

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
**分支**: `feature/sprint-4-load-control`

| # | 任务 | 说明 | 预计 | 状态 |
|---|------|------|------|------|
| 1.1 | 引入 Sentinel 依赖 | sentinel-core + sentinel-annotation-aspectj | 0.5h | ✅ |
| 1.2 | 配置 Sentinel 规则 | QPS 限流 + 熔断降级 | 1h | ✅ |
| 1.3 | 实现 SmartBackoffHandler | 智能退避算法 | 2h | ✅ |
| 1.4 | 实现 UpgradeCheckBlockHandler | 被拒绝时返回动态间隔 | 1h | ✅ |
| 1.5 | 创建 SentinelProperties 配置类 | 混合方案支持 | 0.5h | ✅ |
| 1.6 | 创建 SentinelRuleManager 规则管理服务 | 配置文件 + Redis 合并 | 1h | ✅ |
| 1.7 | 创建 SentinelAdminController | 规则管理 API | 1h | ✅ |
| 1.8 | 单元测试 | Sentinel 相关测试 | 1h | ✅ |

### 阶段 2: 系统负载评估 (Day 3)

**预计时间**: 1天
**分支**: `feature/sprint-4-load-control`

| # | 任务 | 说明 | 预计 | 状态 |
|---|------|------|------|------|
| 2.1 | SystemLoadIndicator 实现 | 综合评分服务 | 2h | ✅ |
| 2.2 | LoadLevel 枚举 | 负载级别定义 | 0.5h | ✅ |
| 2.3 | 负载历史记录 | Redis 存储 7 天历史 | 1h | ✅ |
| 2.4 | Micrometer 指标采集 | CPU/内存/QPS/延迟指标 | 1h | ✅ |
| 2.5 | 单元测试 | 负载评估测试 | 1h | ✅ |

### 阶段 3: 动态周期调整 (Day 4)

**预计时间**: 1天
**分支**: `feature/sprint-4-load-control`

| # | 任务 | 说明 | 预计 | 状态 |
|---|------|------|------|------|
| 3.1 | DynamicIntervalService | 根据负载计算间隔 | 2h | ✅ |
| 3.2 | 控制参数 Redis 存储 | 全局 + 产品级 | 1h | ✅ |
| 3.3 | 控制参数 Repository 接口 | 控制参数存储接口 | 0.5h | ✅ |
| 3.4 | 控制 Parameter Repository 实现 | Redis 控制参数存储实现 | 1h | ✅ |
| 3.5 | ControlParameterController | 参数查询/更新 API | 1h | ✅ |
| 3.6 | UpgradeResponseBuilder 集成 | 注入动态间隔 | 1h | ✅ |
| 3.7 | 单元测试 | 动态间隔测试 | 1h | ✅ |

### 阶段 4: 监控集成 (Day 5-6)

**预计时间**: 2天
**分支**: `feature/sprint-4-load-control`

| # | 任务 | 说明 | 预计 | 状态 |
|---|------|------|------|------|
| 4.1 | MonitorApiController | 监控数据 API | 2h | ✅ |
| 4.2 | MonitorView.vue | 监控仪表盘页面组件 | 4h | ✅ |
| 4.3 | monitor.ts API 接口 | 监控 API 接口 | 0.5h | ✅ |
| 4.4 | 国际化支持 | 中英文支持 | 0.5h | ✅ |
| 4.5 | 实时数据刷新 | 5 秒轮询刷新 | 1h | ✅ |
| 4.6 | 单元测试 | 监控功能测试 | 1h | ✅ |

### 阶段 5: 验收 (Day 7)

**预计时间**: 1天

| # | 任务 | 说明 | 预计 | 状态 |
|---|------|------|------|------|
| 5.1 | 单元测试补充 | 提升覆盖率 ≥ 60% | 2h | ✅ |
| 5.2 | 文档更新 | Sprint 4 完成报告 | 1h | ✅ |

---

## 📊 进度跟踪

```
Sprint 4: [████████████████████] 100%

阶段 1: Sentinel 集成       ✅ 已完成
阶段 2: 系统负载评估         ✅ 已完成
阶段 3: 动态周期调整         ✅ 已完成
阶段 4: 监控集成             ✅ 已完成
阶段 5: 验收                 ✅ 已完成

总计: 7 天 (约 1.5 周)
```

### 新增文件清单

**后端 Java (22 个文件)**:

| 包路径 | 文件 | 说明 |
|--------|------|------|
| `domain/load/model/enums` | `LoadLevel.java` | 负载级别枚举 (LOW/NORMAL/HIGH/CRITICAL) |
| `domain/load/model/vo` | `LoadSnapshot.java` | 负载快照 (评分、CPU、内存、QPS、延迟) |
| `domain/load/model/vo` | `BackoffResult.java` | 智能退避结果 |
| `domain/load/model/entity` | `ControlParameter.java` | 控制参数实体 |
| `domain/load/service` | `SystemLoadIndicator.java` | 负载评估接口 |
| `domain/load/repository` | `LoadHistoryRepository.java` | 负载历史存储接口 |
| `domain/load/repository` | `ControlParameterRepository.java` | 控制参数存储接口 |
| `application/load` | `SystemLoadIndicatorImpl.java` | 负载评估实现 (Micrometer 指标采集) |
| `application/load` | `DynamicIntervalService.java` | 动态间隔计算服务 |
| `application/load` | `SmartBackoffHandler.java` | 智能退避处理器 |
| `infra/sentinel` | `SentinelConfig.java` | Sentinel 配置类 |
| `infra/sentinel/config` | `SentinelProperties.java` | Sentinel 配置属性类 |
| `infra/sentinel/config` | `SentinelRuleManager.java` | 规则管理服务（合并配置+Redis） |
| `infra/sentinel` | `UpgradeCheckBlockHandler.java` | 升级检查 BlockHandler |
| `adapter/api/admin` | `SentinelAdminController.java` | Sentinel 规则管理 Admin API |
| `infra/cache/repository` | `RedisLoadHistoryRepository.java` | Redis 负载历史存储 |
| `infra/cache/repository` | `RedisControlParameterRepository.java` | Redis 控制参数存储 |
| `adapter/api/admin` | `ControlParameterController.java` | 控制参数 Admin API |
| `adapter/api/admin` | `MonitorApiController.java` | 监控数据 Admin API |
| `adapter/api/admin/dto` | `ControlParameterDTO.java` | 控制参数 DTO |
| `adapter/api/admin/dto` | `RealtimeMetricsDTO.java` | 实时指标 DTO |
| `test/.../load` | `SystemLoadIndicatorImplTest.java` | 负载评估单元测试 |
| `test/.../load` | `DynamicIntervalServiceTest.java` | 动态间隔单元测试 |
| `test/.../load` | `SmartBackoffHandlerTest.java` | 智能退避单元测试 |
| `test/.../load/model` | `LoadLevelTest.java` | 负载级别枚举测试 |

**修改文件**:
- `fota-service/pom.xml` - 添加 Sentinel 依赖
- `fota-framework-cache/.../RedisKeyConstants.java` - 新增 ctrl/load/sentinel Key 常量
- `application/upgrade/UpgradeResponseBuilder.java` - 集成 DynamicIntervalService
- `adapter/api/device/UpgradeCheckController.java` - 添加 @SentinelResource 注解

**前端 Vue (3 个文件)**:

| 路径 | 文件 | 说明 |
|------|------|------|
| `src/api` | `monitor.ts` | 监控 API 接口 |
| `src/views/monitor` | `MonitorView.vue` | 监控仪表盘页面组件 |
| `src/locales` | `zh-CN.ts` | 新增监控相关国际化文本 |
| `src/router` | `index.ts` | 新增 /monitor 路由 |

## 🔗 相关文档

| 路径 | 文件 | 说明 |
|------|------|------|
| `src/api` | `monitor.ts` | 监控 API 接口 |
| `src/views/monitor` | `MonitorView.vue` | 监控仪表盘页面组件 |
| `src/locales` | `zh-CN.ts` | 新增监控相关国际化文本 |
| `src/router` | `index.ts` | 新增 /monitor 路由 |

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

### 2026-03-09 (实施完成 - v1.1)
- ✅ **Sprint 4 实施完成**
- ✅ Sentinel 限流熔断集成完成
  - 添加 sentinel-core, sentinel-annotation-aspectj 依赖
  - 实现 SentinelConfig 配置类 (QPS 2500/实例, 熔断 50ms)
  - 实现 UpgradeCheckBlockHandler 处理被限流请求
  - UpgradeCheckController 添加 @SentinelResource 注解
- ✅ 系统负载评估服务完成
  - SystemLoadIndicator 接口和实现
  - LoadLevel 枚举 (LOW/NORMAL/HIGH/CRITICAL)
  - LoadSnapshot 负载快照 (CPU/内存/QPS/P99延迟/连接池)
  - 负载历史 Redis 存储
- ✅ 动态周期调整完成
  - DynamicIntervalService 根据负载计算 checkInterval
  - 控制参数 Redis 存储 (全局 + 产品级)
  - UpgradeResponseBuilder 集成动态间隔
- ✅ 智能退避算法完成
  - SmartBackoffHandler 计算动态退避时间
  - 基础退避: FLOW_QPS(60s), DEGRADE(300s), SYSTEM(600s)
  - 负载系数: LOW(0.5), NORMAL(1.0), HIGH(2.0), CRITICAL(4.0)
- ✅ 管理 API 完成
  - ControlParameterController: 控制参数 CRUD
  - MonitorApiController: 实时监控数据
- ✅ 前端监控仪表盘完成
  - MonitorView.vue: 系统负载评分、资源使用率、API 指标、控制参数
  - monitor.ts: 监控 API 接口
  - 国际化支持 (中英文)
  - 5 秒自动刷新
- ✅ 单元测试完成 (覆盖率 ≥ 60%)
  - SystemLoadIndicatorImplTest
  - DynamicIntervalServiceTest
  - SmartBackoffHandlerTest
  - LoadLevelTest

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
