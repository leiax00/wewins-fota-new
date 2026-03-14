# 负载控制运行期配置设计

> **版本**: v1.2  
> **创建日期**: 2026-03-14  
> **状态**: 待实施  
> **关联文档**: [动态周期与负载控制设计说明](./dynamic-interval-and-load-control.md), [多实例负载评估与动态周期优化方案](./multi-instance-monitoring-improvement.md)

---

## 1. 目标

本方案用于将 Sprint 4 负载控制相关的核心参数从“代码硬编码 + 分散管理”升级为“统一运行期配置”。

本次统一纳管三类内容：

1. **控制参数**：动态周期与下载延迟相关参数
2. **评分模型**：实例级、区域级指标的阈值与权重
3. **Sentinel 规则**：Flow / Degrade 规则

设计目标：

- 支持运行期修改并立即生效
- 统一评分模型与 Sentinel 阈值口径
- 明确实例级与区域级指标分层
- 复用现有字典体系作为配置源，减少新增后台能力
- 保留默认值回退能力，避免 Redis 或配置异常时阻断设备链路

---

## 2. 作用域与生效方式

### 2.1 作用域

本次采用**全局唯一配置**：

- 同一区域内所有实例共享同一份负载控制配置
- 不引入产品级评分配置
- 不引入区域级覆盖层

### 2.2 生效方式

采用**字典编辑 + 手动发布**：

- 管理员通过现有“系统管理 > 字典管理”修改配置源
- 普通配置字典项保存时，不立即覆盖 Redis 快照
- 通过单独的“快照发布开关”字典项控制是否触发发布
- 当发布开关发生 `false -> true` 变化时，配置装配服务重建运行态快照并写入 Redis
- 发布成功后触发 `SentinelRuleManager.loadRules()`
- 发布成功后失效评分配置本地缓存，后续请求按新配置计算
- 所有字典变更与快照发布时间均记录 `updatedAt` / `updatedBy`

当前阶段不引入草稿、审批流、灰度发布。

### 2.3 当前管理方式

当前阶段**不新增独立配置页**：

- 直接通过现有字典管理能力维护配置
- 后端按约定的字典类型和字典项结构读取配置
- 后续若配置复杂度继续增加，再单独建设“负载控制配置页”

这意味着本次优先完成“配置模型与运行时生效”，而不是先做专门 UI。

### 2.4 发布控制原则

当前阶段通过单独字典项控制是否发布快照：

- 普通配置项变更：只更新字典，不自动发布
- 发布开关 `false -> true`：触发全量装配并覆盖 Redis 快照
- 发布开关 `true -> false`：进入编辑态，不删除现有 Redis 快照

系统始终保留“最近一次成功发布的快照”作为运行态配置。

---

## 3. 统一配置模型

统一配置聚合对象命名为 `LoadControlConfig`，由三部分组成：

```text
LoadControlConfig
├── control
├── scoring
└── sentinel
```

### 3.1 control

沿用现有 `ControlParameter` 语义，只负责设备侧控制参数：

- `protectedIntervalMultiplier`
- `downloadDelayMultiplier`
- `minCheckIntervalSeconds`
- `maxCheckIntervalSeconds`

### 3.2 scoring

评分配置分为三组：

- `instanceMetrics`
- `hostMetrics`
- `regionMetrics`

其中，实例级与区域级为本次必须覆盖的主配置面，主机级作为辅助项保留。

#### 3.2.1 单项配置结构

| 字段 | 类型 | 说明 |
|------|------|------|
| `metricKey` | String | 指标标识，稳定枚举值 |
| `scope` | String | `INSTANCE` / `HOST` / `REGION` |
| `warning` | Double | 告警阈值 |
| `critical` | Double | 严重阈值 |
| `weight` | Integer | 权重分值 |
| `enabled` | Boolean | 是否参与评分 |
| `capacitySource` | String? | QPS 类指标的容量来源 |

#### 3.2.2 指标项清单

实例级指标：

- `INSTANCE_JVM_CPU`
- `INSTANCE_JVM_HEAP`
- `INSTANCE_DB_POOL_USAGE`
- `INSTANCE_CHECK_QPS_UTILIZATION`
- `INSTANCE_REPORT_QPS_UTILIZATION`
- `INSTANCE_CHECK_P50`
- `INSTANCE_CHECK_P99`
- `INSTANCE_REPORT_P50`
- `INSTANCE_REPORT_P99`

主机辅助指标：

- `HOST_CPU`
- `HOST_MEMORY`

区域级指标：

- `REGION_CHECK_QPS_UTILIZATION`
- `REGION_REPORT_QPS_UTILIZATION`
- `REGION_CHECK_P50`
- `REGION_CHECK_P99`
- `REGION_REPORT_P50`
- `REGION_REPORT_P99`

#### 3.2.3 默认阈值与权重

默认值沿用 [multi-instance-monitoring-improvement.md](./multi-instance-monitoring-improvement.md) 第 4.3 节：

| scope | metricKey | warning | critical | weight |
|------|-----------|---------|----------|--------|
| INSTANCE | `INSTANCE_JVM_CPU` | 70 | 90 | 16 |
| INSTANCE | `INSTANCE_JVM_HEAP` | 75 | 90 | 10 |
| INSTANCE | `INSTANCE_DB_POOL_USAGE` | 80 | 95 | 8 |
| INSTANCE | `INSTANCE_CHECK_QPS_UTILIZATION` | 60 | 80 | 11 |
| INSTANCE | `INSTANCE_REPORT_QPS_UTILIZATION` | 60 | 80 | 4 |
| INSTANCE | `INSTANCE_CHECK_P50` | 30 | 60 | 5 |
| INSTANCE | `INSTANCE_CHECK_P99` | 50 | 100 | 11 |
| INSTANCE | `INSTANCE_REPORT_P50` | 20 | 40 | 2 |
| INSTANCE | `INSTANCE_REPORT_P99` | 40 | 80 | 6 |
| HOST | `HOST_CPU` | 70 | 90 | 7 |
| HOST | `HOST_MEMORY` | 75 | 90 | 4 |
| REGION | `REGION_CHECK_QPS_UTILIZATION` | 60 | 80 | 5 |
| REGION | `REGION_REPORT_QPS_UTILIZATION` | 60 | 80 | 2 |
| REGION | `REGION_CHECK_P50` | 35 | 70 | 2 |
| REGION | `REGION_CHECK_P99` | 60 | 120 | 4 |
| REGION | `REGION_REPORT_P50` | 25 | 50 | 1 |
| REGION | `REGION_REPORT_P99` | 50 | 100 | 2 |

权重总和固定为 `100`。

#### 3.2.4 QPS 容量口径

QPS 项不直接按绝对值评分，统一先换算为容量利用率：

```text
utilizationPercent = currentQps / effectiveCapacity * 100
```

容量来源规则：

- `INSTANCE_CHECK_QPS_UTILIZATION` 使用 `upgrade:check` 的 Sentinel Flow 阈值
- `INSTANCE_REPORT_QPS_UTILIZATION` 使用 `upgrade:report` 的 Sentinel Flow 阈值
- `REGION_CHECK_QPS_UTILIZATION` 使用 `在线实例数 × upgrade:check 单实例阈值`
- `REGION_REPORT_QPS_UTILIZATION` 使用 `在线实例数 × upgrade:report 单实例阈值`

这意味着评分模型与 Sentinel Flow 阈值必须保持一致管理。

### 3.3 sentinel

保留当前 Flow / Degrade 两类规则。

#### Flow Rule

| 字段 | 说明 |
|------|------|
| `resource` | 资源名，如 `upgrade:check` |
| `grade` | `QPS` / `THREAD` |
| `count` | 阈值 |
| `controlBehavior` | 限流行为 |
| `maxQueueingTimeMs` | 最大排队时间 |
| `enabled` | 是否启用 |

#### Degrade Rule

| 字段 | 说明 |
|------|------|
| `resource` | 资源名 |
| `grade` | `RT` / `EXCEPTION_RATIO` / `EXCEPTION_COUNT` |
| `count` | 阈值 |
| `timeWindow` | 熔断窗口 |
| `minRequestAmount` | 最小请求数 |
| `slowRatioThreshold` | 比例阈值 |
| `enabled` | 是否启用 |

---

## 4. 字典存储规划

### 4.1 设计原则

- `sys_dict_type` 负责配置分类
- `sys_dict_item` 负责具体规则项
- `sys_dict_item.extra` 存结构化 JSON
- 字典是**配置源**
- Redis 是**运行态快照**
- 负载评估与 Sentinel 热路径不直接查字典

### 4.2 推荐字典类型

建议新增 6 个 `dict_type`：

| code | name | 用途 |
|------|------|------|
| `load_control.control_parameter` | 负载控制参数 | 动态周期、下载延迟上下限与倍率 |
| `load_control.scoring.instance` | 实例级评分规则 | JVM、连接池、实例 QPS/延迟评分项 |
| `load_control.scoring.host` | 主机辅助评分规则 | Host CPU、Host Memory |
| `load_control.scoring.region` | 区域级评分规则 | 区域 QPS/延迟评分项 |
| `load_control.sentinel.rule` | Sentinel 规则 | Flow / Degrade 规则 |
| `load_control.meta` | 负载控制元信息 | 发布控制、版本辅助信息 |

字典类型编码建议保持 `load_control.*` 命名空间，避免和现有 `json_schema.*` 混淆。

### 4.3 推荐字典项

#### 4.3.1 控制参数

`load_control.control_parameter` 下建议先只有一个字典项：

| label | value | 说明 |
|------|------|------|
| 全局负载控制参数 | `global.control` | 全局唯一控制参数对象 |

#### 4.3.2 实例级评分项

`load_control.scoring.instance` 下建议使用这些 `value`：

- `instance.jvm_cpu`
- `instance.jvm_heap`
- `instance.db_pool_usage`
- `instance.check_qps_utilization`
- `instance.report_qps_utilization`
- `instance.check_p50`
- `instance.check_p99`
- `instance.report_p50`
- `instance.report_p99`

#### 4.3.3 主机辅助评分项

`load_control.scoring.host` 下建议使用这些 `value`：

- `host.cpu`
- `host.memory`

#### 4.3.4 区域级评分项

`load_control.scoring.region` 下建议使用这些 `value`：

- `region.check_qps_utilization`
- `region.report_qps_utilization`
- `region.check_p50`
- `region.check_p99`
- `region.report_p50`
- `region.report_p99`

#### 4.3.5 Sentinel 规则项

`load_control.sentinel.rule` 下建议使用这些 `value`：

- `flow.upgrade_check`
- `flow.upgrade_report`
- `degrade.upgrade_check_service`

后续若扩展 SystemRule，再新增：

- `system.default`

#### 4.3.6 发布控制项

`load_control.meta` 下建议先使用一个字典项：

| label | value | 说明 |
|------|------|------|
| 快照发布开关 | `snapshot.publish` | 手动触发 Redis 快照发布 |

### 4.4 `extra` 结构建议

#### 4.4.1 评分项

```json
{
  "kind": "load_scoring_metric",
  "scope": "INSTANCE",
  "metricKey": "INSTANCE_CHECK_P99",
  "metricType": "LATENCY",
  "unit": "ms",
  "enabled": true,
  "warning": 50,
  "critical": 100,
  "weight": 11,
  "capacitySource": null,
  "description": "实例级 check 接口 P99 延迟评分项",
  "schemaVersion": 1
}
```

QPS 利用率项示例：

```json
{
  "kind": "load_scoring_metric",
  "scope": "REGION",
  "metricKey": "REGION_CHECK_QPS_UTILIZATION",
  "metricType": "QPS_UTILIZATION",
  "unit": "percent",
  "enabled": true,
  "warning": 60,
  "critical": 80,
  "weight": 5,
  "capacitySource": {
    "type": "sentinel_flow",
    "resource": "upgrade:check",
    "aggregation": "region_instance_count_multiply"
  },
  "schemaVersion": 1
}
```

#### 4.4.2 控制参数

```json
{
  "kind": "load_control_parameter",
  "protectedIntervalMultiplier": 2.0,
  "downloadDelayMultiplier": 1.0,
  "minCheckIntervalSeconds": 1800,
  "maxCheckIntervalSeconds": 172800,
  "schemaVersion": 1
}
```

#### 4.4.3 Sentinel Flow

```json
{
  "kind": "sentinel_flow_rule",
  "resource": "upgrade:check",
  "grade": "QPS",
  "count": 2500,
  "controlBehavior": "RATE_LIMITER",
  "maxQueueingTimeMs": 50,
  "enabled": true,
  "schemaVersion": 1
}
```

#### 4.4.4 Sentinel Degrade

```json
{
  "kind": "sentinel_degrade_rule",
  "resource": "UpgradeCheckService",
  "grade": "RT",
  "count": 50,
  "timeWindow": 30,
  "minRequestAmount": 100,
  "slowRatioThreshold": 0.5,
  "enabled": true,
  "schemaVersion": 1
}
```

#### 4.4.5 发布控制

```json
{
  "kind": "load_control_publish_control",
  "enabled": false,
  "schemaVersion": 1
}
```

推荐语义：

- `enabled=false`：允许持续编辑字典，不触发快照发布
- `enabled=true`：表示本次变更已确认发布
- 只有当该项从 `false` 变为 `true` 时，系统才执行一次全量装配并写入 Redis

发布完成后可保留 `enabled=true`，也可由系统自动回写为 `false`。当前阶段推荐**自动回写为 `false`**，便于下次继续按“关闭 -> 编辑 -> 打开”的方式操作。

---

## 5. Redis 存储

### 5.1 Key 设计

| Key | 说明 |
|-----|------|
| `fota:load-control:config:active` | 当前生效的完整负载控制快照 |
| `fota:load-control:config:version` | 当前快照版本号 |
| `fota:load-control:config:history:{version}` | 历史快照，可选保留 |
| `fota:load-control:config:last-publish-at` | 最近一次发布时间，可选 |

### 5.2 快照结构

Redis 中建议存完整聚合对象：

```json
{
  "version": 12,
  "updatedAt": "2026-03-14T10:00:00Z",
  "updatedBy": "admin",
  "control": {},
  "scoring": {
    "instanceMetrics": [],
    "hostMetrics": [],
    "regionMetrics": []
  },
  "sentinel": {
    "flowRules": [],
    "degradeRules": []
  }
}
```

### 5.3 回退策略

- 字典缺失时，回退到代码默认评分配置与默认 Sentinel 规则
- Redis 快照缺失时，后端可重新从字典装配并写回
- Redis 快照反序列化失败时，回退到代码默认配置
- 任一远程指标缺失时，该单项评分记 `0`，不抛错中断链路
- 发布开关为 `false` 时，继续使用最近一次成功发布的快照

---

## 6. 运行时行为

### 6.1 评分计算

`SystemLoadIndicatorImpl` 读取当前评分配置后，按以下语义计算：

```text
totalScore = instanceScore + hostScore + regionScore
```

其中：

- `instanceScore` 为主分
- `hostScore` 为辅助修正
- `regionScore` 为背景压力修正

评分函数继续沿用现有分段线性模型，不改变 `LoadLevel` 分段：

- `LOW`: 0-24
- `NORMAL`: 25-49
- `HIGH`: 50-74
- `CRITICAL`: 75-100

### 6.2 配置校验

保存前至少校验：

- `warning < critical`
- `weight >= 0`
- 启用项的权重总和必须为 `100`
- QPS 类指标必须指定合法 `capacitySource`
- Sentinel rule 字段满足当前 DTO 校验约束
- `dict_type.code` 与 `dict_item.value` 必须命中约定集合
- `extra.kind` 必须与所在字典类型匹配
- `load_control.meta/snapshot.publish` 必须唯一

### 6.3 更新顺序

建议更新顺序：

1. 监听 `load_control.meta/snapshot.publish` 是否发生 `false -> true` 变化
2. 若未发生发布开关变更，则不生成新快照
3. 发布时从字典表读取全部 `load_control.*` 类型
4. 校验整份配置
5. 组装 `LoadControlConfig`
6. 生成新版本号并写入 Redis 快照
7. 调用 `SentinelRuleManager.loadRules()`
8. 失效评分配置本地缓存
9. 将发布开关自动回写为 `false`（推荐）

若步骤 6-8 失败，应保留旧快照并记录错误日志，不允许静默部分成功。

### 6.4 当前访问入口

当前阶段推荐的配置入口是：

- “系统管理 > 字典管理” 直接维护字典
- “系统管理 > 字典管理” 中单独切换 `snapshot.publish`

当前阶段不做：

- 独立“负载控制配置页”
- 独立“Sentinel 配置页”替代字典
- 专门的配置发布页

---

## 7. 实施边界

本方案明确包含：

- 评分阈值运行期配置化
- 评分权重运行期配置化
- 实例级、区域级指标显式分层
- Sentinel Flow / Degrade 规则统一管理
- 基于字典的直接配置入口
- 基于发布控制字典项的手动快照发布

本方案暂不包含：

- 区域级覆盖配置
- 产品级评分配置
- 配置草稿、审批流、灰度发布
- Sentinel SystemRule 独立管理
- 自动按历史指标推荐阈值
- 独立配置页
