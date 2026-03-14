# 动态周期与负载控制设计说明

> **版本**: v1.0  
> **创建日期**: 2026-03-13  
> **状态**: 已实现（默认模型） / 待扩展（运行期统一配置）

---

## 概述

本文档描述 FOTA 系统中**动态周期调整**与**系统负载控制**的设计方案。核心目标是根据系统实时负载状态，动态调整设备检查更新的间隔时间，实现系统资源的最优利用。

> 说明：本文档聚焦“动态周期机制本身”和默认负载模型。运行期统一配置、实例级/区域级评分配置、Sentinel 规则统一管理，见 [负载控制运行期配置设计](./load-control-runtime-configuration.md)。

---

## 1. 动态周期实现原理

### 1.1 核心思想

设备检查更新时，系统根据当前负载状态动态计算 `checkInterval`（下次检查间隔），而非返回固定值。这实现了：

- **高负载时延长间隔**：减轻系统压力，保证服务稳定性
- **低负载时缩短间隔**：提高更新及时性，优化用户体验

### 1.2 计算公式

```
finalInterval = clamp(
    baseInterval × loadMultiplier × protectedMultiplier × jitter,
    minInterval,
    maxInterval
)
```

**参数说明**：

| 参数 | 来源 | 默认值 | 说明 |
|------|------|--------|------|
| `baseInterval` | 产品配置 | 6 小时 | 产品 `checkPeriodSeconds` 字段 |
| `loadMultiplier` | 负载评估 | 0.9 ~ 3.2 | 根据负载级别动态计算 |
| `protectedMultiplier` | 控制参数 | 1.8 ~ 2.8 | 被限流时的额外放大倍率 |
| `jitter` | 随机抖动 | 0.95 ~ 1.05 | 避免请求同时到达 |
| `minInterval` | 控制参数 | 30 分钟 | 最小检查间隔 |
| `maxInterval` | 控制参数 | 2 天 | 最大检查间隔 |

### 1.3 负载系数映射

| 负载级别 | 评分范围 | 负载系数 | 效果（基于 6h 基础间隔） |
|---------|---------|---------|------------------------|
| LOW | 0-24 | 0.9 | ~5.4 小时 |
| NORMAL | 25-49 | 1.0 | ~6 小时（基准） |
| HIGH | 50-74 | 1.6 | ~9.6 小时 |
| CRITICAL | 75-100 | 3.2 | ~19.2 小时 |

### 1.4 保护态倍率

当请求被 Sentinel 限流/熔断时，额外应用保护态倍率：

| 被拒绝原因 | 倍率 | 说明 |
|-----------|------|------|
| FLOW_QPS / FLOW_CONCURRENCY | 1.8 | QPS 或并发限流 |
| DEGRADE | 2.2 | 熔断降级 |
| SYSTEM | 2.8 | 系统自适应保护 |
| 其他 | 2.0 | 默认值 |

---

## 2. 负载计算方案

### 2.1 架构概览

```
┌─────────────────────────────────────────────────────────────────┐
│                     SystemLoadIndicator                         │
│                     (负载评估服务)                               │
├─────────────────────────────────────────────────────────────────┤
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐       │
│  │ JVM 指标      │  │ API 指标       │  │ 宿主机指标    │       │
│  │ Micrometer    │  │ Prometheus     │  │ Prometheus    │       │
│  │ - CPU 使用率  │  │ - 区域 QPS     │  │ - 主机 CPU    │       │
│  │ - 堆内存      │  │ - P50/P99 延迟 │  │ - 主机内存    │       │
│  │ - 连接池      │  │ - 限流率       │  │ - 网络流量    │       │
│  └───────┬───────┘  └───────┬───────┘  └───────┬───────┘       │
│          │                  │                  │                │
│          └──────────────────┼──────────────────┘                │
│                             ▼                                   │
│                    ┌─────────────────┐                          │
│                    │  评分算法        │                          │
│                    │  加权求和 0-100  │                          │
│                    └────────┬────────┘                          │
│                             ▼                                   │
│                    ┌─────────────────┐                          │
│                    │  LoadSnapshot   │                          │
│                    │  (负载快照)      │                          │
│                    └─────────────────┘                          │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 指标采集来源（当前默认实现）

| 指标类别 | 指标名称 | 采集方式 | 权重 |
|---------|---------|---------|------|
| **JVM** | CPU 使用率 | `OperatingSystemMXBean` | 默认值，运行期可配置 |
| **JVM** | 堆内存使用率 | `MemoryMXBean` | 默认值，运行期可配置 |
| **JVM** | 连接池使用率 | Micrometer `hikaricp.connections` | 默认值，运行期可配置 |
| **API** | QPS / 延迟 | Prometheus | 默认值，运行期可配置 |
| **主机** | 主机 CPU / 内存 | Prometheus | 默认值，运行期可配置 |

默认总分固定为 `100`，运行期配置也必须保持启用项权重总和为 `100`。

### 2.3 单项评分算法

每个指标独立评分，采用分段线性函数：

```
function calculateMetricScore(value, warning, critical, maxScore):
    if value < 0:           # 指标不可用
        return 0
    if value >= critical:   # 严重区
        return maxScore
    if value >= warning:    # 警告区
        partial = maxScore × 0.3
        range = maxScore × 0.7
        return partial + range × (value - warning) / (critical - warning)
    return maxScore × 0.3 × value / warning  # 正常区
```

**阈值配置（旧默认模型）**：

| 指标 | 警告阈值 | 严重阈值 | 满分 |
|------|---------|---------|------|
| JVM CPU | 70% | 90% | 20 |
| JVM 内存 | 75% | 90% | 15 |
| 总 QPS | 8000 | 12000 | 15 |
| P99 延迟 | 30ms | 50ms | 10 |
| 连接池 | 80% | 95% | 10 |
| 主机 CPU | 70% | 90% | 15 |
| 主机内存 | 75% | 90% | 10 |

### 2.4 负载级别判定

| 级别 | 评分范围 | 含义 | 系统行为 |
|------|---------|------|---------|
| LOW | 0-24 | 系统空闲 | 缩短检查间隔 |
| NORMAL | 25-49 | 正常负载 | 使用基准间隔 |
| HIGH | 50-74 | 高负载 | 延长检查间隔 |
| CRITICAL | 75-100 | 过载 | 大幅延长间隔 + 告警 |

### 2.5 缓存策略

- **缓存 TTL**: 1 秒
- **目的**: 避免高频请求时重复采集指标
- **实现**: `AtomicReference<LoadSnapshot>` + `AtomicReference<Instant>`

> 当前多实例优化建议已将 TTL 调整为更长的秒级缓存，并建议将评分模型拆为实例级、主机级、区域级三层。详见 [多实例负载评估与动态周期优化方案](./multi-instance-monitoring-improvement.md)。

---

## 3. 核心组件

### 3.1 组件职责

| 组件 | 职责 | 位置 |
|------|------|------|
| `DynamicIntervalService` | 动态间隔计算入口 | `application/load` |
| `SystemLoadIndicator` | 负载评估接口 | `domain/load/service` |
| `SystemLoadIndicatorImpl` | 负载评估实现 | `application/load` |
| `PrometheusClient` | Prometheus 指标查询 | `infra/metrics` |
| `ControlParameterRepository` | 控制参数存储 | `domain/load/repository` |
| `LoadHistoryRepository` | 负载历史存储 | `domain/load/repository` |

### 3.2 数据模型

**LoadSnapshot（负载快照）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `timestamp` | Instant | 采集时间 |
| `totalScore` | int | 综合评分 (0-100) |
| `level` | LoadLevel | 负载级别 |
| `cpuUsage` | double | JVM CPU 使用率 % |
| `memoryUsage` | double | JVM 堆内存使用率 % |
| `qps` | double | 总 QPS |
| `p50Latency` | double | P50 延迟 ms |
| `p99Latency` | double | P99 延迟 ms |
| `connectionPoolUsage` | double | 连接池使用率 % |
| `hostCpuUsage` | double | 主机 CPU 使用率 % |
| `hostMemoryUsage` | double | 主机内存使用率 % |
| `checkQps` | double | 检查 API QPS |
| `reportQps` | double | 上报 API QPS |

**ControlParameter（控制参数）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `productId` | Long | 产品 ID（空=全局） |
| `protectedIntervalMultiplier` | Double | 保护态倍率 |
| `downloadDelayMultiplier` | Double | 下载延迟倍率 |
| `minCheckIntervalSeconds` | Integer | 最小检查间隔 |
| `maxCheckIntervalSeconds` | Integer | 最大检查间隔 |

### 3.3 配置优先级

控制参数采用**全局 + 产品级**两层配置，产品级优先：

```
最终参数 = merge(全局参数, 产品参数)
```

合并规则：产品级字段非空时覆盖全局值，否则使用全局值。

### 3.4 与统一负载控制配置的关系

当前 `ControlParameter` 仍然只负责设备侧周期和下载延迟控制，不直接承载：

- 评分阈值
- 评分权重
- Sentinel Flow / Degrade 规则

这些能力在下一阶段统一收敛到 [负载控制运行期配置设计](./load-control-runtime-configuration.md) 中定义的 `LoadControlConfig`。

---

## 4. Redis 存储设计

### 4.1 Key 规范

| Key Pattern | 说明 | TTL |
|-------------|------|-----|
| `ctrl:global` | 全局控制参数 | 永久 |
| `ctrl:product:{productId}` | 产品级控制参数 | 永久 |
| `load:history:{yyyyMMdd}` | 负载历史记录 | 7 天 |

### 4.2 负载历史存储

- 按日期分片存储
- 使用 Redis List 追加记录
- 用于趋势分析和历史查询

---

## 5. 限流集成

### 5.1 Sentinel 保护

| 资源 | 保护类型 | 阈值 |
|------|---------|------|
| `/v1/upgrade/check` | QPS 限流 | 2500/实例 |
| `UpgradeCheckService` | 熔断降级 | 慢调用 50ms，比例 50% |
| 系统自适应 | 系统保护 | CPU 80% |

### 5.2 被限流时的处理

1. Sentinel 拦截请求
2. 调用 `BlockHandler`
3. 获取当前负载快照
4. 计算保护态间隔（负载系数 × 保护态倍率）
5. 返回 `RATE_LIMITED` 响应 + 动态 `checkInterval`

---

## 6. 设计决策

### 6.1 为何选择加权评分而非单一指标？

- 单一指标（如仅 CPU）无法反映系统真实状态
- 多指标加权可平衡 CPU 密集型、IO 密集型、网络密集型场景
- 权重可调优，适应不同部署环境

### 6.2 为何使用 1 秒缓存？

- 指标采集（特别是 Prometheus 查询）有网络开销
- 1 秒粒度足够支撑动态调整决策
- 避免高频请求时的性能损耗

### 6.3 为何添加随机抖动？

- 防止大量设备在相同时刻发起检查请求
- 实现请求时间的自然分散
- 抖动范围 ±5% 为经验值

---

## 7. 相关文件

| 文件 | 说明 |
|------|------|
| `DynamicIntervalService.java` | 动态间隔计算服务 |
| `SystemLoadIndicatorImpl.java` | 负载评估实现 |
| `LoadSnapshot.java` | 负载快照模型 |
| `LoadLevel.java` | 负载级别枚举 |
| `ControlParameter.java` | 控制参数实体 |
| `PrometheusClient.java` | Prometheus 客户端 |
| `RedisLoadHistoryRepository.java` | 负载历史存储 |
| `RedisControlParameterRepository.java` | 控制参数存储 |

---

## 8. 变更记录

| 日期 | 版本 | 变更 |
|------|------|------|
| 2026-03-13 | v1.0 | 初版，基于 Sprint 4 实现总结 |
