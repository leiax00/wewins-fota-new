# 多实例负载评估与动态周期优化方案

> **版本**: v2.0  
> **创建日期**: 2026-03-13  
> **最后更新**: 2026-03-13  
> **状态**: 待实施  
> **关联文档**: [动态周期与负载控制设计说明](./dynamic-interval-and-load-control.md), [负载控制运行期配置设计](./load-control-runtime-configuration.md)

---

## 1. 背景与当前实现

本文档聚焦多实例部署下的两类问题：

- 负载评估是否能真实反映当前实例的承压情况
- 动态 `checkInterval` 是否会因为指标作用域不合理而被错误放大或缩小

本次不展开监控平台重构，不新增全局/区域/主机/实例分层 API，也不讨论 UI 下钻设计。

### 1.1 部署假设

- 单区域可部署多台主机、多实例
- 所有实例共享同一区域 Redis 集群
- 每个实例独立运行 Sentinel 规则与动态周期逻辑
- `app.node.code` 采用 `region-host-instance` 约定
- 节点注册按“一实例一节点”处理，节点数可代表在线实例数

### 1.2 当前实现事实

当前负载快照由 `SystemLoadIndicatorImpl.getSnapshot()` 生成，`DynamicIntervalService` 只消费最终的 `LoadLevel`。

| 指标 | 当前来源 | 当前作用域 | 用途 |
|------|----------|------------|------|
| JVM CPU | Micrometer / OS Bean | 当前实例 | 负载评分 |
| JVM 堆内存 | `MemoryMXBean` | 当前实例 | 负载评分 |
| 连接池使用率 | Micrometer `hikaricp.connections.*` | 当前实例 | 负载评分 |
| Check QPS | Prometheus | 区域聚合 | 负载评分 |
| Report QPS | Prometheus | 区域聚合 | 负载评分 |
| 设备 API P50 延迟 | Prometheus | 区域聚合 | 负载评分 |
| 设备 API P99 延迟 | Prometheus | 区域聚合 | 负载评分 |
| Host CPU / 内存 | Prometheus | 当前主机 | 负载评分 |
| LoadSnapshot 缓存 | 内存原子引用 | 当前实例 | 指标采集降频 |

### 1.3 当前调用链

```text
设备请求到达实例 A
    -> SystemLoadIndicatorImpl.getSnapshot()
        -> 采集本实例 JVM / 连接池
        -> 查询区域聚合 QPS / 延迟
        -> 查询本主机 CPU / 内存
        -> 计算 totalScore / LoadLevel
    -> DynamicIntervalService.resolveInterval()
        -> 根据 LoadLevel 计算 loadMultiplier
        -> 结合保护态倍率、min/max、jitter
        -> 返回 checkInterval
```

### 1.4 当前实现的直接结论

- `loadScore` 本质上是“当前实例生成的评分”，但其中混入了区域级 QPS 和区域级延迟指标
- 当单个实例热点偏斜时，区域聚合指标可能掩盖实例局部过载
- 当前 `cachedSnapshot` TTL 为 `1s`，对 Prometheus 查询降频效果有限
- 阈值和权重硬编码在服务内部，运行期不可调
- 当前代码里的延迟查询仍按设备 API 合并口径处理，尚未拆分为 `check` / `report`

---

## 2. 已确认问题

### 2.1 指标作用域混杂，实例过载不敏感

当前最核心的问题不是“缺少更多监控视图”，而是动态周期控制所依赖的评分语义不稳定。

示例：

```text
区域总 Check QPS = 8000
在线实例数 = 4

理想分布: 2000 / 2000 / 2000 / 2000
实际分布: 4000 / 2000 / 1500 / 500
```

在现有实现中，4 个实例看到的区域 QPS 接近相同，实例 A 的热点压力无法被充分放大，导致：

- 实例 A 的动态周期放大不足
- Sentinel 可能已开始保护，但负载评分仍偏“正常”
- 管控逻辑更像“区域平均状态”，而不是“当前实例状态”

### 2.2 延迟指标口径过粗，且 P99 阈值偏紧

当前问题有两层：

- 延迟指标没有区分 `check` 和 `report`
- 现有 P99 阈值偏紧

当前 P99 阈值为：

- warning = `30ms`
- critical = `50ms`

对设备检查接口而言，这一组阈值过于乐观。在经过负载均衡、Redis 访问、序列化、JVM 抖动后，`20ms` 到 `80ms` 的 P99 更接近现实分布。与此同时，`check` 与 `report` 的处理路径、负载特征和控制目标也不同，不应继续共享一组延迟评分指标。现有做法会导致：

- `check` 长尾与 `report` 长尾互相污染
- 普通长尾波动被过早识别为高负载
- 负载等级在 `NORMAL` / `HIGH` 间频繁切换
- `checkInterval` 出现不必要放大

### 2.3 `cachedSnapshot` TTL 过短

当前 TTL 为 `1s`，而 Prometheus 采集周期为 `15s`。这意味着：

- 单实例在高并发下仍可能频繁查询 Prometheus
- 相邻秒内拿到的指标几乎没有统计学差异
- 查询成本高于控制收益

### 2.4 阈值与权重硬编码，不利于后续调优

现阶段的默认值已经明确，但继续硬编码会带来两个直接问题：

- 实例级与区域级指标拆分后，调优项显著增多
- QPS 利用率评分与 Sentinel 阈值之间容易出现配置漂移

因此本专题仍定义“默认阈值与默认权重”，但运行期配置化不再只是远期想法，而是下一阶段的直接实施项，详见 [负载控制运行期配置设计](./load-control-runtime-configuration.md)。

### 2.5 QPS 评分存在重复计分

当前实现中：

- `qps = checkQps + reportQps` 会参与一次总 QPS 评分
- `checkQps` 与 `reportQps` 又分别参与单独评分

这会导致 API 流量类指标在总分中被重复放大，而且三项指标目前都来自区域聚合数据，重复计分的问题会进一步放大“区域平均值掩盖实例热点”的偏差。

---

## 3. 优化目标与设计原则

### 3.1 目标

将动态周期控制调整为“实例优先”的负载评估模型：

- 优先反映当前实例是否承压
- 区域指标只作为背景修正，不主导最终评分
- 在 Prometheus 短暂失败时，仍能依靠本地指标完成退化计算

### 3.2 设计原则

1. 实例优先：动态 `checkInterval` 首先服务于当前实例自保护，而不是区域平均控制。
2. 区域修正：区域总量与区域长尾延迟用于感知“整体背景压力”，但权重低于实例指标。
3. 主机辅助：Host CPU / 内存保留为辅助项，用于发现同机竞争。
4. 退化可用：Prometheus 查询失败时，允许缺失部分远端指标，不能阻断周期计算。
5. 默认值先行：本次文档给出默认阈值与权重，作为运行期配置的默认值基线。
6. 容量归一：QPS 类指标按容量利用率评判，而不是固定绝对值。

### 3.3 本次明确不做

以下内容只记录为后续演进，不纳入本次方案主体：

- 全局/区域/主机/实例分层监控 API
- 监控 UI 重构与下钻页面
- 区域级或全局级独立 `LoadScope` 模型
- Sentinel 规则字典化

---

## 4. 指标模型与评分方案

> 本章定义的是**默认评分模型基线**。运行期配置结构、Redis key、统一接口与 Sentinel 联动方式，见 [负载控制运行期配置设计](./load-control-runtime-configuration.md)。

### 4.1 指标分层

本次评分拆为三层：实例主指标、主机辅助指标、区域修正指标。

| 分类 | 指标 | 目标作用域 | 推荐来源 |
|------|------|------------|----------|
| 实例主指标 | JVM CPU | 当前实例 | Micrometer / OS Bean |
| 实例主指标 | JVM 堆内存 | 当前实例 | `MemoryMXBean` |
| 实例主指标 | 连接池使用率 | 当前实例 | Micrometer |
| 实例主指标 | Check QPS | 当前实例 | Prometheus `instance` 维度 |
| 实例主指标 | Report QPS | 当前实例 | Prometheus `instance` 维度 |
| 实例主指标 | Check P50 延迟 | 当前实例 | Prometheus `instance` 维度 |
| 实例主指标 | Check P99 延迟 | 当前实例 | Prometheus `instance` 维度 |
| 实例主指标 | Report P50 延迟 | 当前实例 | Prometheus `instance` 维度 |
| 实例主指标 | Report P99 延迟 | 当前实例 | Prometheus `instance` 维度 |
| 主机辅助指标 | Host CPU | 当前主机 | Prometheus `host` 维度 |
| 主机辅助指标 | Host 内存 | 当前主机 | Prometheus `host` 维度 |
| 区域修正指标 | 区域 Check 总 QPS | 当前区域 | Prometheus `region` 聚合 |
| 区域修正指标 | 区域 Report 总 QPS | 当前区域 | Prometheus `region` 聚合 |
| 区域修正指标 | 区域 Check P50 延迟 | 当前区域 | Prometheus `region` 聚合 |
| 区域修正指标 | 区域 Check P99 延迟 | 当前区域 | Prometheus `region` 聚合 |
| 区域修正指标 | 区域 Report P50 延迟 | 当前区域 | Prometheus `region` 聚合 |
| 区域修正指标 | 区域 Report P99 延迟 | 当前区域 | Prometheus `region` 聚合 |

### 4.2 采集方式约束

#### 4.2.1 实例 QPS / 延迟

实例级 API 指标推荐继续由 Prometheus 提供，而不是在业务代码中直接从本地 `MeterRegistry` 计算 rate 或 percentile。

原因：

- 当前系统已经为所有指标注入了 `region` / `host` / `instance` 标签
- Prometheus 已具备按 `instance` 聚合查询的能力
- 在业务代码中自行计算 counter rate / timer percentile，需要额外维护滑动窗口和聚合状态，复杂度高且语义不一致

推荐查询维度：

- `sum(rate(fota_device_checks_total{region="...",instance="..."}[5m]))`
- `sum(rate(fota_upgrade_events_total{region="...",instance="..."}[5m]))`
- `histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket{region="...",instance="...",uri="/v1/upgrade/check"}[5m])) by (le))`
- `histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{region="...",instance="...",uri="/v1/upgrade/check"}[5m])) by (le))`
- `histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket{region="...",instance="...",uri="/v1/upgrade/report"}[5m])) by (le))`
- `histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{region="...",instance="...",uri="/v1/upgrade/report"}[5m])) by (le))`

区域修正指标也应保持同样的拆分口径：

- `sum(rate(fota_device_checks_total{region="..."}[5m]))`
- `sum(rate(fota_upgrade_events_total{region="..."}[5m]))`
- `histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket{region="...",uri="/v1/upgrade/check"}[5m])) by (le))`
- `histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{region="...",uri="/v1/upgrade/check"}[5m])) by (le))`
- `histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket{region="...",uri="/v1/upgrade/report"}[5m])) by (le))`
- `histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{region="...",uri="/v1/upgrade/report"}[5m])) by (le))`

#### 4.2.2 区域在线实例数

区域在线实例数可由两种方式得到：

- 首选：Prometheus `instance` 标签去重后的实例数
- 备选：`NodeRegistryService.listOnlineNodes()` 过滤当前 `region`

文档中按“一实例一节点”约定描述，但实现上不要求依赖节点注册才能完成实例级评分。

#### 4.2.3 QPS 容量基线

QPS 类指标不应直接使用固定绝对阈值评分，而应先换算为容量利用率：

```text
capacityUsage = currentQps / effectiveCapacity
```

其中：

- 实例 Check 容量 = 当前实例 `check` 路由的有效限流阈值
- 实例 Report 容量 = 当前实例 `report` 路由的有效限流阈值
- 区域 Check 容量 = 当前区域所有在线实例的 `check` 有效限流阈值之和
- 区域 Report 容量 = 当前区域所有在线实例的 `report` 有效限流阈值之和

在所有实例配置一致时，可近似为：

```text
区域 Check 容量 = 在线实例数 × 单实例 Check 容量
区域 Report 容量 = 在线实例数 × 单实例 Report 容量
```

如果后续支持按实例差异化配置，则区域容量应按各实例有效阈值逐个求和，不能再退化为“实例数 × 默认值”。

本次文档建议的评分口径：

- warning = `60%`
- critical = `80%`

这组阈值适用于实例 QPS 和区域 QPS 两层，只是容量基线不同。

### 4.3 评分结构

总分仍保持 `0-100`，但改为实例优先分配：

| 分类 | 指标 | warning | critical | 权重 |
|------|------|---------|----------|------|
| 实例资源 | JVM CPU | 70% | 90% | 16 |
| 实例资源 | JVM 堆内存 | 75% | 90% | 10 |
| 实例资源 | 连接池使用率 | 80% | 95% | 8 |
| 实例 API | 实例 Check QPS 利用率 | 60% | 80% | 11 |
| 实例 API | 实例 Report QPS 利用率 | 60% | 80% | 4 |
| 实例 API | 实例 Check P50 延迟 | 30ms | 60ms | 5 |
| 实例 API | 实例 Check P99 延迟 | 50ms | 100ms | 11 |
| 实例 API | 实例 Report P50 延迟 | 20ms | 40ms | 2 |
| 实例 API | 实例 Report P99 延迟 | 40ms | 80ms | 6 |
| 主机辅助 | Host CPU | 70% | 90% | 7 |
| 主机辅助 | Host 内存 | 75% | 90% | 4 |
| 区域修正 | 区域 Check 总 QPS 利用率 | 60% | 80% | 5 |
| 区域修正 | 区域 Report 总 QPS 利用率 | 60% | 80% | 2 |
| 区域修正 | 区域 Check P50 延迟 | 35ms | 70ms | 2 |
| 区域修正 | 区域 Check P99 延迟 | 60ms | 120ms | 4 |
| 区域修正 | 区域 Report P50 延迟 | 25ms | 50ms | 1 |
| 区域修正 | 区域 Report P99 延迟 | 50ms | 100ms | 2 |

权重合计：`100`

### 4.4 评分解释

- 实例主指标共 `73` 分，是决定动态周期的主要依据
- 其中延迟指标按 `check` / `report`、`P50` / `P99` 分开独立计分，避免不同接口相互污染
- 其中实例 API 指标共 `39` 分，`check QPS` 与 `check P99` 基本持平，兼顾前馈预警与结果反馈
- 主机辅助指标共 `11` 分，用于识别同机资源争用
- 区域修正指标共 `16` 分，同样按 `check` / `report` 分开，用于提供区域背景压力，但不单独主导评分
- `report` 路径权重整体低于 `check` 路径，避免上报链路波动过度主导设备检查周期

推荐按以下方式理解结果：

```text
totalScore = instanceScore + hostScore + regionScore
```

其中：

- `instanceScore` 决定主趋势
- `hostScore` 用于补充实例未直接覆盖的竞争资源
- `regionScore` 用于在区域整体吃紧时提前保守一些，但不替代实例局部事实
- QPS 项进入评分前，应先转换为容量利用率，再套用 `60% / 80%` 阈值

### 4.5 单项评分函数

单项评分仍沿用现有分段线性函数，不修改基本算法：

```text
if value < 0:
    score = 0
else if value >= critical:
    score = maxScore
else if value >= warning:
    score = maxScore * 0.3 + maxScore * 0.7 * (value - warning) / (critical - warning)
else:
    score = maxScore * 0.3 * value / warning
```

这意味着本次主要改的是：

- 指标来源
- 指标作用域
- 阈值默认值
- 权重分布
- 缓存策略

不是推翻现有评分函数。

---

## 5. 动态周期的预期行为

评分模型调整后，`DynamicIntervalService` 的行为不变，仍然是：

```text
baseInterval * loadMultiplier * protectedMultiplier * jitter
```

变化只体现在 `LoadLevel` 更贴近实例实际承压。

### 5.1 预期效果

- 单实例热点偏斜时，该实例会更早进入 `HIGH` 或 `CRITICAL`
- 区域 Check 总 QPS 或区域 Check 延迟升高时，只进行有限放大，不会被区域平均值强行拉高
- `check` 延迟恶化时，应明显强于 `report` 延迟对 `checkInterval` 的影响
- 区域 Prometheus 短暂异常时，仍可依靠 JVM / 连接池 / 主机指标得到退化结果

### 5.2 典型场景

#### 场景 A：单实例热点

```text
实例 A Check QPS = 2300, Check P99 = 88ms
其他实例均正常
区域总 QPS 正常
```

预期：若实例 A 的 `Check QPS` 已接近其单实例容量上限，则其 `loadScore` 明显升高，`checkInterval` 被主动拉长。

#### 场景 B：区域整体升压但实例正常

```text
当前实例 Check QPS = 900, Check P99 = 42ms, Report P99 = 28ms
区域 Check 总 QPS 接近 warning
区域 Check P99 = 70ms
区域 Report P99 = 45ms
```

预期：当前实例评分小幅上升，但不应直接进入 `CRITICAL`。

#### 场景 C：Prometheus 查询失败

```text
区域 Check/Report QPS、区域 Check/Report 延迟、实例 QPS、实例延迟查询失败
JVM、连接池、主机指标仍可读取
```

预期：缺失的远端指标记为 0 分，不影响动态周期正常计算。

---

## 6. 与当前实现的差异

### 6.1 需要调整的实现点

1. `PrometheusClient` 补充实例维度查询方法：
   - 实例 Check QPS
   - 实例 Report QPS
   - 实例 Check P50 / P99 延迟
   - 实例 Report P50 / P99 延迟
2. `SystemLoadIndicatorImpl` 用实例维度 API 指标替换当前区域聚合指标作为主评分来源。
3. `SystemLoadIndicatorImpl` 保留区域 Check/Report QPS 与区域 Check/Report 延迟，但仅作为修正项参与评分。
4. `cachedSnapshot` TTL 从 `1s` 调整为 `10s`。
5. 监控页面需明确 `loadScore` 的语义为“当前实例评分”，避免被理解为区域评分。

### 6.2 暂不纳入本次的实现点

- 区域级负载评分对象
- 全局监控汇总接口
- Host / Instance 详情接口
- 自动按历史指标推荐阈值
- Sentinel SystemRule 独立管理

---

## 7. 验证与验收

### 7.1 验收场景

- 单实例 CPU、连接池升高时，即使区域总 QPS 平稳，评分也应升高。
- 单实例 Check QPS / Check P99 升高时，应比现有实现更快进入 `HIGH`。
- 单实例只有 Report 延迟升高、Check 延迟正常时，评分应上升但弱于 Check 延迟恶化场景。
- 只有区域 Check/Report 总 QPS 或区域延迟升高、当前实例平稳时，评分只能温和上升。
- Prometheus 查询失败时，服务仍可返回合法的 `LoadSnapshot`。
- 10 秒缓存窗口内重复调用 `getSnapshot()` 应复用相同快照。
- 保护态倍率仍在 `loadMultiplier` 之后生效，且继续受到 `min/max interval` 限制。

### 7.2 观察指标

上线后重点观察：

- `HIGH` / `CRITICAL` 比例是否明显高于现状
- Sentinel block rate 与 `loadScore` 的相关性是否增强
- 实例热点场景下，过载实例的 `checkInterval` 是否明显大于平稳实例
- Prometheus 查询量是否因 `10s` TTL 明显下降

---

## 8. 后续演进

以下事项保留到后续专题，不在本次文档中展开：

- 区域级与全局级健康分模型
- 监控 API 分层与跨区域汇总
- 监控 UI 分层导航与实例下钻

---

## 9. 变更记录

| 日期 | 版本 | 变更 |
|------|------|------|
| 2026-03-13 | v1.0 | 初版，多实例监控改进思路整理 |
| 2026-03-13 | v2.0 | 收敛为实例优先的负载评估与动态周期优化方案，移除超出本次范围的监控平台重构内容 |
