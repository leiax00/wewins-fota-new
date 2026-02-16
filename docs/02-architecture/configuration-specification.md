# 配置规范说明（部署与环境变量迁移）

本文档用于说明 `wewins-fota-new` 的配置结构、环境变量迁移规则、验证校验、部署清单与常见错误排查方式。

---

## 一、配置结构概览

### 1.1 `app.mode`（部署模式）

- `main`：主区域模式（控制面、管理后台、聚合入口）
- `region`：区域模式（设备 API、配置同步、数据转发）

> 只允许取 `main` 或 `region`，大小写敏感，必须为小写。

### 1.2 `app.region` 对象

`region` 节点描述当前实例所在区域的基本信息，主区域与区域实例都需要配置。

```yaml
app:
  region:
    code: "sg"
    name: "Singapore"
    publicUrl: "https://sg.fota.example.com"
```

**字段说明**：

- `code`：区域唯一编码（用于索引、哈希和跨区路由）
- `name`：区域展示名称
- `publicUrl`：该区域对外访问的基础地址（用于生成回调、日志链接或区域索引）
- `timeZone`：区域时区（如 `Asia/Singapore`）
- `defaultQuota`：默认配额（可选，默认 100000）

### 1.3 `app.main` 对象

`main` 节点描述主区域的入口信息，**region 模式必须配置**，用于控制面拉取与聚合上报。

```yaml
app:
  main:
    baseUrl: "https://main.fota.example.com"
    bootstrapSecret: "change-me"
```

**字段说明**：

- `baseUrl`：主区域 `fota-service` 的公网 HTTPS 地址
- `bootstrapSecret`：分区初始密钥（Bootstrap）
- `regions`：子区域注册表（可选，用于管理）

### 1.4 `app.mq` 开关

用于控制是否启用消息队列相关组件（生产/消费）。

```yaml
app:
  mq:
    enabled: true
```

**说明**：
- `true`：启用 MQ 连接、消费者、Forwarder（按 `app.mode` 生效）
- `false`：关闭所有 MQ 相关逻辑（适用于本地调试或无 MQ 环境）

---

## 二、环境变量迁移指南

### 2.1 旧变量名 → 新变量名映射

| 旧变量名 | 新变量名 | 说明 |
| --- | --- | --- |
| `MODE` | `APP_MODE` | 部署模式（`main` / `region`） |
| `REGION` | `APP_REGION_CODE` | **变更点：强制统一为 `region.code`** |
| `REGION_NAME` | `APP_REGION_NAME` | 区域展示名称 |
| `REGION_PUBLIC_URL` | `APP_REGION_PUBLIC_URL` | 区域对外访问地址 |
| `MAIN_BASE_URL` | `APP_MAIN_BASE_URL` | 主区域基地址 |
| `MAIN_BOOTSTRAP_SECRET` | `APP_MAIN_BOOTSTRAP_SECRET` | 分区初始密钥 |
| `MQ_ENABLED` | `APP_MQ_ENABLED` | MQ 总开关 |

> 如果此前使用 `REGION`，请迁移为 `APP_REGION_CODE`，并确保格式符合规范。

### 2.2 重点变更：`REGION` → `REGION_CODE`

- **旧变量 `REGION` 已弃用**
- **新变量统一为 `APP_REGION_CODE`**
- 对应配置结构为 `app.region.code`
- 目的是与结构化配置一致，避免与 `app.region` 节点混淆

### 2.3 迁移前后对比示例

**迁移前（旧变量）**：

```env
MODE=region
REGION=sg
REGION_NAME=Singapore
REGION_PUBLIC_URL=https://sg.fota.example.com
MAIN_BASE_URL=https://main.fota.example.com
MAIN_BOOTSTRAP_SECRET=change-me
MQ_ENABLED=true
```

**迁移后（新变量）**：

```env
APP_MODE=region
APP_REGION_CODE=sg
APP_REGION_NAME=Singapore
APP_REGION_PUBLIC_URL=https://sg.fota.example.com
APP_MAIN_BASE_URL=https://main.fota.example.com
APP_MAIN_BOOTSTRAP_SECRET=change-me
APP_MQ_ENABLED=true
```

---

## 三、配置验证规则

### 3.1 必填字段

| 字段 | 必填条件 | 说明 |
| --- | --- | --- |
| `app.mode` | 总是必填 | 必须为 `main` 或 `region` |
| `app.region.code` | 总是必填 | 区域编码，必须符合格式 |
| `app.main.baseUrl` | 当 `app.mode=region` | 区域模式必须指向主区域 |
| `app.main.bootstrapSecret` | 当 `app.mode=region` | 区域模式必须具备初始密钥 |

### 3.2 默认值说明

| 字段 | 默认值 | 说明 |
| --- | --- | --- |
| `app.mode` | `main` | 默认主区域模式 |
| `app.mq.enabled` | `true` | 默认开启 MQ |
| `app.region.name` | `""` | 可为空 |
| `app.region.publicUrl` | `""` | 可为空，建议生产填写 |

### 3.3 格式要求

| 字段 | 格式 | 示例 |
| --- | --- | --- |
| `app.region.code` | 小写字母 + 连字符 | `sg`, `ap-sg`, `eu-west` |
| `app.main.baseUrl` | HTTPS URL | `https://main.fota.example.com` |
| `app.region.publicUrl` | HTTPS URL | `https://sg.fota.example.com` |

---

## 四、部署配置清单

### 4.1 main 模式需要的配置项

- `app.mode=main`
- `app.region.code`
- `app.region.name`（可选但推荐）
- `app.region.publicUrl`（可选但推荐）
- `app.mq.enabled`（可选）

### 4.2 region 模式需要的配置项

- `app.mode=region`
- `app.region.code`
- `app.main.baseUrl`（**必须**）
- `app.main.bootstrapSecret`（**必须**）
- `app.mq.enabled`（可选）

### 4.3 可选配置项说明

- `app.region.name`：区域展示名称（增强可读性）
- `app.region.publicUrl`：区域对外访问地址（用于生成对外链接）
- `app.mq.enabled`：关闭 MQ 适用于本地测试或无 MQ 环境

---

## 五、常见错误与解决方案

### 5.1 配置缺失导致的错误

**表现**：
- 启动失败或模式识别为 `null`

**原因**：
- 未配置 `app.mode` 或 `app.region.code`

**解决**：
- 确保 `APP_MODE` 与 `APP_REGION_CODE` 已设置
- 检查环境变量是否正确注入

### 5.2 类型转换错误

**表现**：
- `app.mq.enabled` 报类型错误或始终为默认值

**原因**：
- 传入 `APP_MQ_ENABLED=1` 或 `yes` 等非标准布尔值

**解决**：
- 使用 `true` / `false`（小写）

### 5.3 MQ 连接问题

**表现**：
- 连接超时、认证失败、队列不可用

**原因**：
- MQ 配置未启用或凭证错误

**解决**：
- 检查 `APP_MQ_ENABLED`
- 校验 MQ 连接参数（主机、端口、用户名、密码、vhost）

---

## 附：推荐的配置示例（YAML）

```yaml
app:
  mode: "region"
  region:
    code: "sg"
    name: "Singapore"
    publicUrl: "https://sg.fota.example.com"
  main:
    baseUrl: "https://main.fota.example.com"
    bootstrapSecret: "change-me"
  mq:
    enabled: true
```

---

**文档维护**：本文档应与配置变更同步更新，记录最新的配置规范和迁移路径
