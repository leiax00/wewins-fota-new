# 系统架构及技术说明书（V1.1）

> 本版本在 V1.0 基础上，补充并定稿：
>
> * 单体同构部署模型（Main / Region 模式）
> * 跨公网 HTTP 控制面通信（Pull + Ingest）
> * 本地 MQ 缓冲 + Forwarder 转发机制
> * 策略 / 缓存同步的最终实现方式
> * 分布式日志下的设备级查询闭环

---

## 1. 架构设计目标（更新）

* **同一套服务、同一套代码、同一套镜像**
* 主区域与其他区域仅通过 **运行模式（MODE）** 区分职责
* 不依赖专网，**跨区域通信全部走公网 HTTPS**
* 数据面完全本地化，跨区域仅同步：

  * 控制面快照
  * 分钟级聚合
  * 设备定位索引（5 分钟桶）

---

## 2. 单体同构部署模型（核心定稿）

### 2.1 服务形态

统一服务名：`fota-service`

* 同一个 Docker image
* 不同区域通过 **MODE** 决定启用模块

### 2.2 运行模式定义（唯一入口）

```env
MODE=main | region
REGION=main | jp | sg | eu | ...
```

### 2.3 MODE → 功能模块映射（内置，不再手工配置）

| 功能模块                            | MODE=main | MODE=region |
| ------------------------------- | --------- | ----------- |
| 对外 API（check / report）          | ✅         | ✅           |
| Admin 后台                        | ✅         | ❌           |
| Control API（version / snapshot） | ✅         | ❌           |
| Ingest API（跨区聚合接收）              | ✅         | ❌           |
| Config Sync Worker              | ❌         | ✅           |
| Forwarder（MQ → 主区）              | ❌         | ✅           |
| 本地 MQ Consumer                  | ✅（可选）     | ✅           |

> 说明：
>
> * **ENABLE_ADMIN / ENABLE_INGEST_API / ENABLE_FORWARDER 等不再暴露为独立配置项**
> * 所有模块是否启用，仅由 `MODE` 决定，避免配置组合错误

---

## 3. 跨区域通信总原则（公网场景定稿）

### 3.1 明确禁止的事情

* ❌ 区域服务直连主区 PostgreSQL
* ❌ 区域服务直连主区 Redis
* ❌ 区域服务直连主区 ClickHouse
* ❌ 主区暴露数据库或 MQ 端口到公网

### 3.2 唯一允许的跨区通信方式

* **HTTPS 调用主区 `fota-service` 的 internal API**
* 所有跨区调用必须：

  * TLS
  * HMAC + nonce 鉴权
  * 批量 + 幂等

---

## 4. 控制面同步（策略 / 缓存 / 配置）

### 4.1 同步模型（HTTP Pull，像 MQ 一样设计）

* **不是推送**
* **不是实时**
* 是“版本号 + 快照”的最终一致模型

### 4.2 版本轮询（已定稿参数）

* 轮询周期：**30 秒**
* 轮询接口（主区）：

```http
GET /internal/config/version?product_id=xxx
```

返回：

```json
{
  "policy_ver": 42,
  "product_ver": 18,
  "control_ver": 7
}
```

### 4.3 快照拉取

仅当版本变化时拉取：

```http
GET /internal/config/snapshot/policy?product_id=xxx&ver=42
GET /internal/config/snapshot/product?product_id=xxx&ver=18
GET /internal/config/snapshot/control?product_id=xxx&ver=7
```

### 4.4 快照内容原则（必须可直接执行）

快照内包含：

* 已编译的策略选择器（version / tags / region）
* 灰度参数（hash bucket）
* 配额参数（region 级 max_limit）
* 目标固件元数据（URL 模板 / hash / size）
* 控制参数（next_check_interval 默认值）

### 4.5 本地缓存写入方式（原子）

```text
pol:snap:{product}:v{ver}     // 写入
pol:active_ver:{product}=ver // 切换指针
```

* 保留最近 3 个版本
* API 热路径只读 `active_ver`

### 4.6 主区不可达的降级语义（写死）

* 使用最近一次成功快照继续运行
* 超过 24h：

  * 禁止新策略生效
  * 拉长 next_check_interval
  * 控制台提示“配置同步滞后”

---

## 5. 数据面链路（区域本地）

### 5.1 Check（就近处理）

依赖：

* 本地 Redis（策略快照、stop、quota、限流、设备索引）
* 本地内存缓存（Caffeine）

不依赖：

* 主区任何数据库
* 跨区通信

### 5.2 Report（快速返回）

```text
API → 本区 RabbitMQ → 立即 200
```

---

## 6. 日志与统计架构（分散 + 汇总）

### 6.1 本区处理

每个区域：

* Consumer 写本区 ClickHouse 明细表 `upgrade_events`
* 同时生成：

  * `upgrade_events_1m`（分钟聚合）
  * `device_index_5m`（设备定位索引，内存聚合）

---

## 7. 设备日志跨区域查询（你关心的核心问题）

### 7.1 为什么需要设备定位索引

* 设备可能漫游
* 明细日志分散在多个区域
* 控制台不能“猜区域”

### 7.2 索引粒度（已定稿）

* **5 分钟桶**

### 7.3 主区索引表（ClickHouse）

表名：`device_region_index_5m`

字段：

* `bucket_start`（5min 对齐）
* `product_id`
* `imei_hash`
* `region`
* `event_cnt`
* `last_event_time`

TTL：

* 默认 30 天

### 7.4 查询流程（两段式）

1. 控制台查主区索引 → 得到 region + 时间段
2. 控制台下钻对应 region → 查明细表
3. 合并排序展示

> 不需要全局明细表
> 不需要跨区 ClickHouse 联邦

---

## 8. Forwarder：跨区聚合转发（2B 定稿）

### 8.1 为什么需要 Forwarder

* 跨公网不稳定
* 不能影响 report/check 响应
* 必须保证不丢数据

### 8.2 转发链路

```text
Consumer → q.forward.* → Forwarder → HTTPS → 主区 Ingest API
```

### 8.3 转发队列

* `q.forward.device_index_5m`
* `q.forward.upgrade_events_1m`
* 每条队列配置 DLQ

### 8.4 Forwarder 触发策略（已定稿）

* **batch_size ≥ 1000** 或 **等待 ≥ 15 秒**
* 失败：

  * 不 ack
  * 重试
  * 超阈值进 DLQ + 告警

---

## 9. 主区 Ingest API（HTTP）

### 9.1 接口

```http
POST /internal/ingest/device_index_5m
POST /internal/ingest/upgrade_events_1m
```

### 9.2 幂等保证

* 每个请求携带 `batch_id`
* 主区记录：

```text
ingest:seen:{batch_id} TTL=7d
```

---

## 10. 跨公网鉴权（HMAC + Nonce，已定稿）

### 10.1 请求头

```text
X-Region
X-Timestamp
X-Nonce
X-Body-SHA256
X-Signature
```

### 10.2 校验规则

* 时间窗 ±5 分钟
* nonce 去重（Redis TTL 10 分钟）
* HMAC 校验通过才处理

---

## 11. 设备数据跨区域处理（权威 + 缓存）

### 11.1 权威存储

* 设备全量数据仅存主区 PostgreSQL

### 11.2 各区缓存

```text
dev:stat:{product}:{imei} = {
  enabled,
  tag_hash,
  device_id,
  home_region
}
```

* TTL：1–7 天
* miss → HTTP 回源主区（批量）

---

## 12. 可观测与控制闭环

* 各区 Prometheus 采集：

  * QPS / P99 / 错误率 / Redis 延迟 / MQ 堆积
* 各区自治控制：

  * `ctrl:product:{product}:next_interval`
* 主区仅展示，不强依赖

---

## 13. 核心默认参数（最终定稿）

| 项目                 | 值            |
| ------------------ | ------------ |
| 配置轮询               | 30s          |
| Forwarder batch    | 1000         |
| Forwarder max wait | 15s          |
| 索引桶                | 5 分钟         |
| 鉴权                 | HMAC + nonce |
| nonce TTL          | 10 分钟        |
| ingest 幂等 TTL      | 7 天          |

---

## 14. 结论（为什么这套方案是“对的”）

* **同构单体**：开发、部署、运维成本最低
* **HTTP 控制面**：比跨公网 MQ 更稳、更可控
* **本地 MQ 缓冲**：保证接口快、数据不丢
* **索引 + 下钻**：解决跨区域设备日志查询难题
* **允许延迟**：用最终一致换系统稳定性
