# 部署规划文档（主区/分区/多实例）

本文档描述当前系统在“统一设备入口 + 多分区多实例 + 主区控制面”的部署形态、配置约定与运行职责划分，作为部署与配置的统一依据。

生成时间：2026-02-12

---

## 1. 总体目标

- 设备侧只配置一个域名，所有实例均可承接设备流量。
- 主区与分区的交互最小单位是“分区级”，不要求定位到某一实例。
- 主区与分区均可多实例部署，实例无状态，依赖共享存储。
- 非幂等的“区域级任务”通过分区主节点（leader）执行，避免重复与冲突。

---

## 2. 域名与入口

### 2.1 设备入口域名（统一）

- 设备入口域名（示例）：`api.fota.example.com`
- DNS/GeoDNS/Global LB 负责将设备请求路由到就近分区
- 对设备透明，不区分主区/分区

### 2.2 主区控制面域名

- 主区控制面域名（示例）：`main.fota.example.com`
- 分区仅通过该域名访问主区控制面 API
- 主区的控制面入口需负载均衡至任意主区实例

### 2.3 分区域名（可选）

分区域名可作为运维/调试入口，但不强制公开给设备侧。
如需要暴露，可与设备入口域名一致（同一域名由 DNS/GeoDNS 就近解析）。

---

## 3. 部署模式与职责划分

### 3.1 主区（MODE=main）

职责：
- 管理后台（Admin API）
- 配置中心（/internal/config/*）
- 数据汇聚（/internal/ingest/*）
- 设备 API（/v1/upgrade/*）可选开启

建议：
- 主区也可承接设备流量（与分区一致）
- 控制面 API 必须无状态 + 共享数据库/缓存

### 3.2 分区（MODE=region）

职责：
- 设备 API（/v1/upgrade/check /report）
- 配置同步（从主区拉取版本与快照）
- 数据转发（Forwarder）与区域级任务

---

## 4. 实例与分区模型

### 4.1 实例标识（app.node）

app.node 表示“实例身份”，用于注册与观测。

建议命名约定：
- `node.code = {regionCode}-{instanceId}`（如 `us-east-1a`）
- `node.baseUrl = 分区域名`（例如 `https://ue.fota.example.com`）

> 说明：node.baseUrl 指向分区入口域名，不是单实例地址。

### 4.2 分区标识

分区标识用于区域级任务与主区交互。
建议来源：
- 从 `node.code` 解析 `regionCode`（如 `us-east-1a` → `us-east`）
- 或新增显式字段（若后续需要）

---

## 5. 分区主节点（Leader）

### 5.1 什么时候需要 Leader

以下“区域级任务”需要只有一个实例执行：
- 配置同步（region → main）
- 数据转发（Forwarder）
- 区域级定时任务（聚合、清理等）

设备 API 不需要 leader，所有实例都可处理。

### 5.2 Leader 选举建议

使用 Redis 分布式锁：
- Key：`fota:region:leader:{regionCode}`
- TTL + 心跳续约
- 锁持有者执行区域级任务

### 5.3 主区 Leader 不影响分区

分区访问主区只依赖 `main.fota.example.com`，
主区 Leader 切换不影响分区交互。

---

## 6. 交互与调用规则

### 6.1 设备流量

- 设备 → `api.fota.example.com` → 就近分区实例
- 任意实例都可处理设备请求
- 设备请求不需要命中 leader

### 6.2 分区 → 主区（控制面）

- 分区通过 `main.fota.example.com` 拉取配置
- 分区通过 `main.fota.example.com` 上报聚合数据
- 主区入口负载均衡至任意实例即可

### 6.3 主区 → 分区（如需）

主区如需反向访问分区，只需知道“分区域名 + regionCode”。
不要求定位到某一个实例，避免耦合。

---

## 7. 配置结构建议

### 7.1 主区配置示例（MODE=main）

```yaml
app:
  mode: main
  node:
    code: main-1
    name: Main-1
    baseUrl: https://api.fota.example.com
  features:
    admin: true
    device-api: true
    data-ingest: true
    data-forward: false
```

### 7.2 分区配置示例（MODE=region）

```yaml
app:
  mode: region
  node:
    code: us-east-1a
    name: US-East-1a
    baseUrl: https://api.fota.example.com
  main:
    baseUrl: https://main.fota.example.com
    bootstrapSecret: change-me
  features:
    admin: false
    device-api: true
    data-ingest: false
    data-forward: true
```

环境变量示例：

```env
APP_MODE=region
APP_MAIN_BASE_URL=https://main.fota.example.com
APP_MAIN_BOOTSTRAP_SECRET=change-me
```

---

## 8. 安全与认证

- 分区 → 主区：使用 HMAC 签名请求
- 主区需要维护 regionCode → secret 的映射
- 设备接口仍按既有鉴权策略执行（如 JWT/签名 URL）

### 8.1 Redis 密钥初始化示例

密钥存储在 Redis：
- key：`fota:region:secret:{regionCode}`
- value：密钥明文

示例（Redis CLI）：

```bash
SET fota:region:secret:us-east "your-secret-1"
SET fota:region:secret:eu-west "your-secret-2"
```

建议：
- 定期轮换密钥
- 轮换时可先双写新旧密钥，平滑切换

### 8.2 字典引导（推荐）

在主区控制台维护“分区初始密钥”字典：

- 字典类型编码：`region_bootstrap_secret`
- 字典项建议字段：
  - `value`：regionCode（如 `us-east`）
  - `extra.secret`：初始密钥

兼容模式：
- 若未提供 `extra.secret`，则使用 `label` 作为 regionCode、`value` 作为密钥

说明：
- 仅在 Redis 未存在密钥时写入
- 适合“新增分区”的初始化场景

#### 8.2.1 数据库初始化示例（SQL）

```sql
-- 字典类型：分区初始密钥
INSERT INTO sys_dict_type (code, name, status, description)
VALUES ('region_bootstrap_secret', '分区初始密钥', 'active', '分区首次握手使用的初始密钥');

-- 字典项示例（value=regionCode, extra.secret=初始密钥）
INSERT INTO sys_dict_item (dict_type_id, label, value, status, sort_order, extra)
SELECT id, 'us-east', 'us-east', 'active', 1, '{"secret":"your-secret-1"}'::jsonb
FROM sys_dict_type WHERE code = 'region_bootstrap_secret';

INSERT INTO sys_dict_item (dict_type_id, label, value, status, sort_order, extra)
SELECT id, 'eu-west', 'eu-west', 'active', 2, '{"secret":"your-secret-2"}'::jsonb
FROM sys_dict_type WHERE code = 'region_bootstrap_secret';
```

### 8.3 轮换下发（复用配置版本轮询）

轮换不新增接口，通过 `/internal/config/version` 响应附带：

```json
{
  "version": 123,
  "rotateKey": {
    "keyId": "2026-02-12-001",
    "secret": "new-secret"
  }
}
```

分区 leader 收到后：
- 更新本地密钥
- 在下一次轮询携带 `X-Secret-Ack` 进行确认

主区收到 `X-Secret-Ack` 后清理待下发密钥。

---

## 9. 高可用与扩缩容

### 9.1 主区

- 通过负载均衡扩容实例
- 共享 PostgreSQL/Redis/ClickHouse
- 控制面 API 无状态，任意实例可处理

### 9.2 分区

- 通过负载均衡扩容实例
- 设备请求分摊到所有实例
- 仅 leader 执行区域级任务

---

## 10. 故障与降级策略

- 主区不可用：分区继续使用本地缓存（不做同步）
- 分区 leader 故障：锁超时后由其他实例接管
- 单实例故障：设备入口无感知，DNS/LB 自动绕过

---

## 11. 分区入口与健康检查（避免单点）

### 11.1 是否必须接入 LB

不“必须”，但强烈建议。理由：
- DNS 只能做粗粒度路由，无法剔除单实例故障
- 没有 LB 时，实例故障会直接暴露给设备端（需要客户端重试兜底）

### 11.2 不接入 LB 的最低成本方案（有风险）

- DNS 为一个分区配置多个 A 记录（多台入口机）
- 入口机运行 Nginx/HAProxy，后端指向该分区所有实例
- Nginx 配置被动剔除（`max_fails` / `fail_timeout`）

风险：
- DNS 缓存导致故障切换慢（不可控）
- 入口机本身需要高可用，否则入口成单点

### 11.3 推荐方案（稳定性更好）

- DNS → 云 LB / 或自建 Nginx 集群（至少 2 台 + VIP/Keepalived）
- LB/Nginx 对后端做健康检查
- 后端实例故障会自动剔除，不影响设备请求

---

## 12. 当前代码需匹配的关键点

以下为当前代码与本部署规划需对齐的关键缺口：

1. RegionSyncService 必须使用 `app.main.baseUrl`（而非 `app.node.baseUrl`）
2. Leader 选举逻辑需实现（Redis 锁）
3. 主区 HMAC 校验与密钥管理需补齐
4. 文档与配置需明确 node.code 的命名约定与 regionCode 来源

---

## 13. 部署检查清单

- DNS 已配置 `api.fota.example.com`（全局设备入口）
- DNS 已配置 `main.fota.example.com`（主区控制面入口）
- 分区实例 `app.main.baseUrl` 指向 `main.*`
- 分区实例具备 HMAC 密钥
- Redis 可用（leader 选举 + 注册）
- MQ 可用（Forwarder/消费者）
