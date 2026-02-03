# FOTA 管理平台

## 系统架构及技术说明书（V1.0）

### 1. 目标与范围

* 多产品合计千万设备
* 多地域部署（各区域就近接入）
* Check 不要求强一致灰度（允许秒级~几十秒级配置延迟）
* 业务统计分钟级即可；系统负载监控要求实时（秒级）
* 部署形态：Docker 多容器 + Nginx LB
* 核心问题：跨地域配置同步、日志分散下的控制台展示、按设备定位明细日志

---

## 2. 总体架构分层

### 2.1 数据面（Data Plane，各区域本地化）

每个区域（JP/SG/EU/…）都部署：

* `nginx`：入口负载均衡、基础限流
* `fota-api`：Check/Report 接口（多副本）
* `redis-region`：本地缓存（策略快照、配额、熔断、限流、设备索引缓存、bitmap）
* `rabbitmq-region`：本地消息队列（Report 事件削峰）
* `consumer-region`：消费 Report，写本区 ClickHouse（明细）
* `clickhouse-region`：本区业务事件明细与本区聚合

> 关键原则：**任何高频热路径不跨区访问主区 DB**。跨区抖动不影响本区可用性。

### 2.2 控制面（Control Plane，主区域集中 + HTTP 分发）

主区域（Main）部署：

* `admin`：策略/版本/产品配置管理（写权威 PG）
* `postgres-main`：权威主数据（产品、版本、策略、设备全量）
* `config-service`：配置快照生成与版本管理（可与 admin 合并）
* `snapshot-store`：快照存储（建议 RustFS/S3，gzip）
* `ingest-service`：接收各区 5 分钟桶索引/全局聚合（HTTP）
* `clickhouse-main`：存全局聚合表、全局设备定位索引表
* `grafana/prometheus`：全局可观测（可在主区汇总）

> 控制面跨区域采用 **HTTP**：各区“拉版本号 + 拉快照”，各区“推索引聚合”。

---

## 3. 控制面同步：像 MQ 一样设计，但用 HTTP 实现

### 3.1 核心概念：事件、版本、快照

虽然实现用 HTTP，但我们用“事件驱动”思想来约束一致性：

* **版本号（Version）**：单调递增，作为幂等与乱序判断基准
* **快照（Snapshot）**：Check 可直接执行的配置包（策略集合、产品配置、控制参数）
* **事件（Event）**：逻辑上表示发生了变更；在 HTTP 方案中通过“版本号变化”体现

#### 快照类型（建议至少 3 类）

1. `policy_snapshot`：某产品所有可执行策略的集合
2. `product_snapshot`：产品级配置（secret、默认 check interval、开关）
3. `control_snapshot`：控制回路下发参数（例如 next_check_interval 目标）

### 3.2 各区域同步流程（HTTP Pull）

每个区域部署 `config-sync-worker`（可独立容器）：

**Step A：拉版本号（轻量轮询）**

* 周期：默认 15 秒
* 接口（主区）：

    * `GET /internal/config/version?product_id=xxx`
    * 返回示例：`{policy_ver: 42, product_ver: 18, control_ver: 7}`

**Step B：仅当版本变更才拉快照**

* `GET /internal/config/snapshot/policy?product_id=xxx&ver=42`
* `GET /internal/config/snapshot/product?product_id=xxx&ver=18`
* `GET /internal/config/snapshot/control?product_id=xxx&ver=7`
* 支持：gzip、ETag、If-None-Match、hash 校验

**Step C：写本区 Redis（原子切换）**
采用“版本化 Key + 指针”避免半更新：

* 写入版本化快照：

    * `pol:snap:{product}:v{ver}`（或拆分多 key）
* 切换指针：

    * `pol:active_ver:{product} = ver`
* 保留最近 N 个版本（建议 3 个），异步清理旧版本

**Step D：通知本区 API 刷新内存缓存**
最简单稳妥：API 自己轮询本区 `pol:active_ver`（例如每 5 秒）
（你选了“实现上先 HTTP”，这里也保持简单，不依赖 Pub/Sub）

### 3.3 熔断（stop）单独走“快速通道”

熔断要求更快生效，建议：

* stop key：`pol:stop:{product}:{policy_id} = 1/0`
* sync-worker 对 stop 也可走轮询：

    * `GET /internal/config/stop?product_id=xxx`（3 秒轮询）
* Check 热路径 **直读本区 Redis stop key**，不只依赖内存缓存

### 3.4 主区不可达的降级语义（必须写进说明书）

* 主区短暂不可达：各区继续使用最近一次快照，Check/Report 不受影响
* 超过阈值（建议 24h）仍不可达：

    * 禁止新策略生效（仅执行现有 active_ver）
    * 自动拉长 `next_check_interval`（保护系统）
    * 控制台提示“配置同步滞后”

---

## 4. 数据面关键链路

### 4.1 Check（就近区域处理）

Check 热路径只依赖：

* 本区 Redis：策略快照指针、配额计数、stop、限流、设备索引缓存、bitmap
* 本地内存缓存：策略快照反序列化结果（Caffeine）

典型步骤：

1. 限流：`rl:check:{product}:{imei}`
2. 设备索引：`dev:stat:{product}:{imei}`（miss 再回源，见 6）
3. 策略匹配：selector + hash 灰度桶 + 时间窗 + stop
4. 配额：Lua 原子检查 `pol:quota:{product}:{policy_id}` < max_limit_region
5. 返回：下载 URL（CDN + 短签名）+ `next_check_interval`（控制回路值优先）

> 你允许策略延迟，因此策略快照最终一致即可；熔断/配额在本区强一致。

### 4.2 Report（就近区域处理）

* API：仅做校验 + 投递本区 MQ，立即返回 200
* 本区 consumer：

    * 批量写本区 ClickHouse 明细表 `upgrade_events`
    * 产出本区 1m/5m 聚合（用于本区看板）
    * 额外产出“5 分钟桶设备定位索引”并推送主区（见 5）

---

## 5. 日志分散下的控制台方案：全局聚合 + 设备定位索引 + 区域下钻

### 5.1 全局看板：只汇总聚合，不汇总明细

* 各区 ClickHouse 生成 `upgrade_events_1m`（按分钟聚合）
* 各区通过 HTTP Ingest 推送到主区 `upgrade_events_1m_global`
* 控制台默认查询主区全局表，展示全局态势与按区域分布
* 下钻到某区域时，再查该区域 ClickHouse 明细/聚合

### 5.2 按设备查明细：引入“5 分钟桶设备定位索引”（你选的 5 分钟）

你担心“设备漫游、日志可能在多个区域”——索引用来解决“在哪些区域”的问题。

#### 主区索引表：`device_region_index_5m`（建议 ClickHouse）

字段建议：

* `bucket_start`（5 分钟对齐）
* `product_id`
* `imei_hash`（脱敏：hash64(imei + salt)）
* `region`
* `event_cnt`
* `last_event_time`

分区与排序：

* PARTITION BY date(bucket_start)
* ORDER BY (product_id, imei_hash, bucket_start, region)

TTL：

* 默认保留 7～30 天（按排障需求）

#### 各区怎么写索引（避免“每条事件一次跨区写入”）

* consumer 在内存里按 `(bucket_start, product_id, imei_hash, region)` 聚合计数
* 每 5～10 秒批量 flush 一次到主区：

    * `POST /internal/ingest/device_index_5m`（主区 ingest-service）
* ingest-service 批量写 `device_region_index_5m`

#### 控制台按设备查询流程（两段式）

1. 用户输入 IMEI → 控制台计算 `imei_hash`
2. 查主区 `device_region_index_5m` 得到最近 N 天出现过的区域与时间桶
3. 控制台并行查询这些区域的明细 ClickHouse（按时间范围 + imei）
4. 合并排序展示；支持按 region 过滤（解决漫游多区域）

> 这样你“不用猜区域”，也不需要把所有明细汇总到主区。

---

## 6. 设备数据“跨区域保存/漫游”的处理：权威主区 + 各区索引缓存

### 6.1 权威存储

* 设备全量（Excel 导入）落主区 PG：`device` 表
* 各区不保存全量设备库（避免多主复杂度）

### 6.2 各区需要缓存什么？

各区 Redis 缓存 **设备索引**（不是全量）：

* enabled/disabled
* tag_hash 或 tags_version（用于策略匹配）
* home_region（可选）
* device_id（如果你用 bitmap offset）
* synced_at

key 示例：

* `dev:stat:{product}:{imei} -> {enabled, tag_ver, tag_hash, home_region, device_id}`

### 6.3 缓存 miss 怎么办（推荐默认）

* 本区 miss → 调用主区 `device-index-api` 批量回源（HTTP）
* 回源结果写本区 Redis，并设置 TTL（例如 1～7 天）
* 主区变更（禁用/标签变更）通过下一次 TTL 过期或后续可加“变更列表版本号”轮询加速收敛（第二阶段优化）

---

## 7. 安全与跨公网通信边界（HTTP 方案的底线）

跨公网只开放 **主区的 HTTP 接入层**（不暴露主区 DB/MQ）：

* 主区暴露：

    * `config-service`（/version, /snapshot…）
    * `ingest-service`（接收聚合/索引）
* 鉴权建议：

    * 区域级 API token / mTLS（二选一）
    * 请求签名（HMAC）防重放
* 访问控制：

    * IP 白名单（区域出口固定时）
    * WAF / 速率限制
* 幂等：

    * ingest 批次带 `batch_id`，重复提交可去重或允许“可重复写 + 汇总时去重”

---

## 8. 可观测性与闭环控制（next_check_interval）

你要求“实时监控 API 负载并据此调整设备下一次检查时间”，建议：

* 各区 Prometheus 采集：

    * check QPS、P99、5xx、Redis 延迟、Lua 耗时、MQ 堆积、consumer lag
* 控制策略：

    * **各区自治优先**（抗主区故障）：每区根据本区指标写 `ctrl:product:{product}:next_interval`
    * 主区控制台汇总展示全局，但不强依赖主区实时下发

> 这能保证跨区断链时，各区仍能“自保”并平滑负载。

---

## 9. 关键默认参数（可直接落地）

* 配置轮询周期（version）：15 秒
* stop 轮询周期：3 秒
* 快照保留：最近 3 个版本
* 主区不可达降级阈值：24 小时
* 设备索引缓存 TTL：1～7 天（取决于变更频率）
* 设备定位索引桶：5 分钟
* 索引 flush：每 5～10 秒批量推送

---

# 下一步我建议你现在定稿的 6 个“实现清单”

你选了方案 1 后，最关键的“开工清单”就是：

1. 主区 `config-service`：version/snapshot API + 快照生成器（从 PG 生成可执行快照）
2. 各区 `config-sync-worker`：轮询 version、拉快照、写本区 Redis、维护 active_ver
3. 本区 `fota-api`：读取本区 active_ver，内存缓存快照，stop 直读 Redis
4. 各区 `consumer-region`：写本区明细 CH + 内存聚合 device_index_5m
5. 主区 `ingest-service`：接收 device_index_5m 批量写主区 CH
6. 控制台：

    * 全局：查主区 `upgrade_events_1m_global`
    * 设备：先查主区 `device_region_index_5m` → 再下钻区域明细

