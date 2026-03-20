# FOTA CDN 预热完整方案

> Cloudflare R2 + Tiered Cache + Workers + Spring Boot 代码集成
>
> 免费方案 · 全球多节点覆盖 · 亚太 / 欧洲 / 中东

---

## 组件总览

| 组件 | 作用 | 费用 |
|---|---|---|
| Cloudflare R2 | 固件存储，替代自建 RustFS/S3 兼容存储 | 免费 10GB / 1000万次读 |
| Cloudflare CDN | 全球边缘分发，绑定自定义域名 | 免费（R2 回源零费用）|
| Smart Tiered Cache | 一次预热，自动扩散到其他 PoP | 完全免费，一键开启 |
| Cloudflare Workers | 手动触发 Cloudflare 网络内部预热请求 | 免费 10万次/天 |
| Cache Rules | 固件文件长期缓存策略配置 | 免费 |
| fota-framework-storage | 现有存储框架，已支持 R2 `LIKE_R2` 模式 | 无额外成本 |
| fota-framework-task（新增）| 通用异步任务 + SSE 进度推送 | 无额外成本 |
| fota-framework-cdn（新增）| CDN 预热逻辑，集成到框架层 | 无额外成本 |

---

## 一、整体架构

### 1.1 CDN 链路

核心原则是 Smart Tiered Cache 承担跨地区扩散，代码只负责触发首次缓存：

```
控制后台创建版本（上传固件）
        │
        ▼
Cloudflare R2（Origin 存储）
        │  绑定自定义域名后，R2 → CDN 零回源费用
        ▼
Cloudflare 边缘 PoP（就近节点）
        │
┌───────┴──────── Smart Tiered Cache ────────────────┐
│  SIN PoP ← 代码自动预热触发                         │
│                                                      │
│  其他 PoP Miss → 询问上级节点（不回源 R2）           │
│                  ↓           ↓          ↓            │
│                 NRT          FRA        DXB           │
└──────────────────────────────────────────────────────┘
        │
        ▼
设备 Check 升级 → 后台返回固件信息 + CDN 下载地址
        │
        ▼
设备自行下载安装（命中就近 PoP 缓存）
```

> **关于缓存持久性：** 只要持续有设备请求某个固件，Cloudflare 基于 LRU 策略会持续续期缓存，不会回源。活跃版本的固件在设备下载期内始终保持缓存状态。

### 1.2 存储方案选择

| | 自建 S3（RustFS）| Cloudflare R2 |
|---|---|---|
| 回源方式 | 公网回源，延迟取决于服务器位置 | CF 内网回源，极低延迟 |
| 回源带宽费用 | 产生出流量费用 | **零费用** |
| 缓存命中后表现 | ✅ 完全一样 | ✅ 完全一样 |
| 迁移成本 | - | 极低，改 3 个参数（endpoint、url-access-type）|

**项目现有配置**：已在 `application.yml` 中支持配置切换：

```yaml
app:
  storage:
    s3:
      enabled: true
      endpoint: ${STORAGE_S3_ENDPOINT:https://<CF_ACCOUNT_ID>.r2.cloudflarestorage.com}
      bucket: fota-firmware
      # 切换为 LIKE_R2 模式即可启用 R2 自定义域名
      url-access-type: ${STORAGE_S3_URL_ACCESS_TYPE:like_r2}
```

两者仅在 Cache Miss 时的那一次回源有差异。开启 Tiered Cache 后回源频率已极低，自建 S3 完全可用；选择 R2 可进一步消除回源延迟和费用。

---

## 二、Cloudflare R2 配置

### 2.1 创建 Bucket

在 Cloudflare Dashboard → R2 Object Storage → Create Bucket，或使用 Wrangler CLI：

```bash
npx wrangler r2 bucket create fota-firmware
```

Bucket 命名建议：`fota-firmware`（生产）、`fota-firmware-staging`（测试）

### 2.2 生成 R2 API Token

1. Dashboard → My Profile → API Tokens → Create Token
2. 选择 "R2 Token" 模板
3. 权限设置：Object Read & Write，限定 Bucket 为 `fota-firmware`
4. 记录 Access Key ID 和 Secret Access Key

> **⚠ 注意：** Token 权限最小化原则：只给 `fota-firmware` bucket 的读写权限，不要使用全账户权限 Token。

### 2.3 绑定自定义域名（启用 CDN）

这一步是启用 CDN 加速的关键，完成后 R2 文件才会经过 Cloudflare 边缘节点缓存：

1. Dashboard → R2 → fota-firmware → Settings → Custom Domains
2. 点击 Connect Domain，填入：`fota-cdn.example.com`
3. 前提：该域名的 DNS 必须托管在 Cloudflare
4. Cloudflare 自动配置 HTTPS 证书，无需额外操作

| 访问方式 | URL 示例 | 是否经过 CDN |
|---|---|---|
| Dev URL（不推荐）| `https://<bucket>.r2.dev/firmware/...` | ❌ 直连 R2，无缓存 |
| 自定义域名（推荐）| `https://fota-cdn.example.com/firmware/...` | ✅ CDN 缓存，零回源费 |

### 2.4 S3 客户端迁移（Java）

R2 完全兼容 S3 API，在 Spring Boot 中修改配置即可：

```yaml
# application.yml
storage:
  endpoint: https://<CF_ACCOUNT_ID>.r2.cloudflarestorage.com
  access-key: <R2_ACCESS_KEY>
  secret-key: <R2_SECRET_KEY>
  region: auto        # R2 固定填 auto
  bucket: fota-firmware
```

其余 S3 SDK 调用代码（上传、下载、列举）无需改动。

---

## 三、Cache Rules 配置

### 3.1 开启 Smart Tiered Cache（核心配置）

Smart Tiered Cache 是免费功能，开启后各地区 PoP 在 Cache Miss 时会优先查询上级节点而非直接回源，代码只需预热一个 PoP 即可自动扩散全球。

**Dashboard 操作路径：**

```
Dashboard → 选择域名 → Caching → Tiered Cache
→ 开启 Smart Tiered Cache Topology
```

或通过 API 开启：

```bash
curl -X PATCH \
  "https://api.cloudflare.com/client/v4/zones/{ZONE_ID}/cache/tiered_cache_smart_topology_enable" \
  -H "Authorization: Bearer {CF_API_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"value": "on"}'
```

### 3.2 固件缓存规则

Dashboard → 选择域名 → Rules → Cache Rules → Create Rule：

| 配置项 | 值 |
|---|---|
| 规则名称 | FOTA Firmware Cache |
| 匹配条件 | `hostname = fota-cdn.example.com` AND `URI Path matches /firmware/*` |
| Cache Level | Cache Everything |
| Edge Cache TTL | 30 days |
| Browser Cache TTL | 1 day |
| Respect Strong ETags | ON（支持断点续传 Range 请求）|

> **ℹ 说明：** 固件 URL 应包含版本号（如 `/firmware/v2.1.0/device-a.bin`），设计为不可变路径，不会出现缓存脏数据问题。

---

## 四、后台工程模块设计（Spring Boot + Maven）

### 4.1 模块划分

基于项目现有 `fota-framework` 框架层设计，新增两个模块：

```
fota-parent
├── fota-framework
│   ├── fota-framework-common     # 通用工具
│   ├── fota-framework-database  # 数据库访问
│   ├── fota-framework-cache     # 缓存（Redis Bitmap）
│   ├── fota-framework-mq        # 消息队列
│   ├── fota-framework-storage   # 已有：S3/R2 存储（已支持 LIKE_R2）
│   ├── fota-framework-web       # Web 安全
│   ├── fota-framework-security  # 认证鉴权
│   ├── fota-framework-task     # 新增：通用异步任务 + SSE 进度推送
│   └── fota-framework-cdn      # 新增：CDN 预热逻辑
├── fota-service                 # 已有：业务服务层
└── fota-module-system          # 系统模块
```

**依赖关系：**

```
fota-service       →  依赖 fota-framework-task（提交任务、订阅进度）
fota-service       →  依赖 fota-framework-storage（上传/下载固件）
fota-service       →  依赖 fota-framework-cdn（预热能力）
fota-framework-task →  不依赖任何业务模块（通用基础设施）
fota-framework-cdn  →  不依赖 task（纯预热逻辑）
```

### 4.2 fota-framework-task 模块（新增）

通用异步任务模块，不感知具体业务，未来其他耗时操作（批量导出、镜像构建等）均可复用。

**位置：** `fota-framework/fota-framework-task`

**核心职责：**
- 任务记录持久化（DB 存储任务状态，支持页面刷新后状态恢复）
- 异步任务提交与执行编排
- SSE 连接管理与实时进度推送
- 提供任务状态查询接口（断线重连兜底）

**对外接口：**

```
POST   /api/tasks/{taskId}/subscribe   建立 SSE 连接，实时接收进度
GET    /api/tasks/{taskId}/status      查询任务最终状态（断线重连用）
```

**任务阶段枚举（通用）：**

```
INIT    → 已创建，等待执行
PROCESSING  → 处理中
COMPLETED    → 已完成
FAILED       → 失败
CANCELLED     → 取消
```

**任务表结构：**

```sql
CREATE TABLE async_task (
    id          VARCHAR(36) PRIMARY KEY,
    biz_type    VARCHAR(64),          -- 业务类型，如 FIRMWARE_PUBLISH
    biz_id      VARCHAR(64),          -- 关联业务 ID
    stage       VARCHAR(32),          -- 当前阶段
    percent     INT,                  -- 进度百分比
    message     VARCHAR(256),         -- 阶段消息
    error_msg   TEXT,                 -- 错误信息
    created_at  DATETIME,
    updated_at  DATETIME
);
```

### 4.3 fota-framework-storage 现有存储能力

项目已有完整的 S3 存储框架，仅需配置切换即可支持 R2：

**StorageProperties 现有配置（已支持）：**

```java
// fota-framework-storage/src/main/java/com/wewins/fota/storage/config/StorageProperties.java
public enum UrlAccessType {
    PATH_STYLE,    // http://endpoint/bucket/key（MinIO 等）
    LIKE_R2,       // http://custom-domain/key（Cloudflare R2 自定义域名）
    VIRTUAL_HOSTED  // http://bucket.endpoint/key（AWS S3）
}
```

### 4.4 fota-framework-cdn 模块（新增）

**位置：** `fota-framework/fota-framework-cdn`

纯粹的 CDN 预热能力封装，不感知业务逻辑。

**核心职责：**
- 向 CDN 域名发起 HTTP 请求触发边缘节点缓存
- 大文件（>50MB）自动分片请求（20MB/片，并发数限制为 3）
- 检查 `CF-Cache-Status` 响应头验证预热结果

**配置项：**

```yaml
app:
  cdn:
    warm:
      enabled: true
      timeout-seconds: 300                     # 预热超时
      chunk-size-mb: 20                        # 分片大小
      chunk-concurrency: 3                     # 分片并发数
```

**对外提供：**

```java
// 供 fota-service 调用
CdnWarmResult warmFirmware(String firmwarePath);
```

### 4.5 fota-service 固件版本发布流程

现有 `FirmwareVersionAppService` 集成 CDN 预热：

```
前端填写版本信息 + 选择固件文件（先上传至临时目录）
        │
        ▼  POST /api/admin/firmware/versions
后端持久化版本记录（status = PENDING）
创建异步任务记录（biz_type = FIRMWARE_PUBLISH）
        │
        ▼  立即返回 { taskId }
前端建立 SSE 连接 /api/tasks/{taskId}/subscribe
        │
        ▼  后台异步执行
┌─────────────────────────────────────────┐
│  Step 1：上传临时文件 → R2              │
│    推送：UPLOADING 0% → 100%            │
│                                         │
│  Step 2：CDN 预热（fota-framework-cdn）  │
│    推送：WARMING → 完成                 │
│                                         │
│  Step 3：版本 status → ACTIVE           │
│    删除本地临时文件                      │
│    推送：DONE                           │
└─────────────────────────────────────────┘
        │
        ▼
版本创建完成，可被设备 Check 到
```

**现有相关服务（供参考）：**

- `FirmwareUploadAppService` - 固件上传（已有）
- `FirmwareVersionAppService` - 固件版本管理（已有）
- `SignedUrlService` - 下载 URL 签名（已有，支持 SELF_SIGNED/S3_PRESIGNED/NONE 三种模式）

> **说明：** 预热失败不阻断版本发布，版本仍会置为 ACTIVE。首次 Cache Miss 时设备请求会自动回源并缓存；创建、更新与手动触发均统一按字典中的 `strategy` 执行预热。

---

## 五、固件版本与 CDN 缓存的关系

### 5.1 设备升级流程

```
设备定期 Check
        │
        ▼  GET /api/ota/check?model=xxx&currentVersion=yyy
后台查询是否存在 status = ACTIVE 的更新版本
        │
        ├─ 无更新 → {"update": false}
        │
        └─ 有更新 → 返回固件概览 + CDN 下载地址
                    {
                      "update": true,
                      "version": "v2.1.0",
                      "url": "https://fota-cdn.example.com/firmware/v2.1.0/device-a.bin",
                      "size": 52428800,
                      "md5": "abc123..."
                    }
                            │
                            ▼
                    设备自行下载 + 校验 MD5 + 安装
```

### 5.2 CDN 缓存状态说明

预热状态**不存入版本记录**，原因：

- 缓存是 Cloudflare 侧的状态，服务端无法感知也无需同步
- 缓存会自然失效，但活跃下载期内不会被驱逐（LRU 机制）
- 缓存失效后设备请求会自动回源并重建缓存，不影响设备下载

**需要手动补充预热的场景：**
- 某版本长期无请求导致缓存失效，即将进行大批量推送前
- 需要在正式大规模下发前，提前触发一次 Cloudflare 边缘缓存建立

---

## 六、预热策略与 Worker 脚本

系统同时支持两种预热方式，并通过字典 `cdn_warm.worker.config` 的单个配置项统一选择：

- `SERVER`：由业务服务直接发起预热请求
- `WORKER`：由 Cloudflare Worker 在 Cloudflare 网络内部发起预热请求

选中的策略会同时作用于：

- 新建版本带包发布
- 更新版本带包发布
- 管理后台手动触发预热

当策略选择为 `WORKER` 时，使用下面的 Worker 脚本和部署方式。

### 6.1 Worker 代码

```javascript
// cloudflare/warm-worker.js
// 部署命令: npx wrangler deploy

export default {
  async fetch(request, env) {
    const DEFAULT_CACHE_TTL = 86400 * 30;

    if (request.method !== "POST") {
      return new Response("Method Not Allowed", { status: 405 });
    }

    const token = request.headers.get("X-Warm-Token");
    if (token !== env.WARM_SECRET) {
      return new Response("Unauthorized", { status: 401 });
    }

    const { firmware_url, ttl } = await request.json();
    if (!firmware_url) {
      return new Response("Missing firmware_url", { status: 400 });
    }

    const cacheTtl = normalizeCacheTtl(ttl, DEFAULT_CACHE_TTL);
    const result = await warmUrl(firmware_url, cacheTtl);
    return Response.json({ warmed: result.status > 0, url: firmware_url, ttl: cacheTtl, result });
  }
};

function normalizeCacheTtl(ttl, defaultTtl) {
  const parsed = Number(ttl);
  if (Number.isFinite(parsed) && parsed > 0) {
    return Math.floor(parsed);
  }
  return defaultTtl;
}

async function warmUrl(url, cacheTtl) {
  try {
    const resp = await fetch(url, {
      cf: {
        cacheEverything: true,
        cacheTtl,
      }
    });
    return {
      status: resp.status,
      cache: resp.headers.get("CF-Cache-Status"),
    };
  } catch (e) {
    return { status: 0, cache: "ERROR", error: e.message };
  }
}
```

### 6.2 Worker 部署配置

```toml
# wrangler.toml
name = "fota-warmer"
main = "warm-worker.js"
compatibility_date = "2024-01-01"

# WARM_SECRET 通过 wrangler secret 设置，不写在此文件
```

```bash
# 部署
npx wrangler deploy

# 设置鉴权 Secret
npx wrangler secret put WARM_SECRET
```

### 6.3 手动触发方式

```bash
# 预热单个固件
curl -X POST https://fota-warmer.your-subdomain.workers.dev \
  -H "Content-Type: application/json" \
  -H "X-Warm-Token: YOUR_SECRET" \
  -d '{"firmware_url": "https://fota-cdn.example.com/firmware/v2.1.0/device-a.bin"}'

# 指定 TTL
curl -X POST https://fota-warmer.your-subdomain.workers.dev \
  -H "Content-Type: application/json" \
  -H "X-Warm-Token: YOUR_SECRET" \
  -d '{"firmware_url": "...", "ttl": 604800}'
```

### 6.4 说明

- 字典配置示例：
  ```json
  {
    "strategy": "WORKER",
    "workerUrl": "https://fota-warmer.your-subdomain.workers.dev",
    "warmSecret": "YOUR_SECRET",
    "defaultTtlSeconds": 2592000
  }
  ```
- 当前 Worker 支持 `firmware_url` 和可选 `ttl`
- 当前 Worker 不支持指定远端 PoP 预热
- Worker 发起的请求只会沿 Cloudflare 实际入口路径建立缓存
- 如果开启 Tiered Cache，缓存可能随着上层节点传播，但这不等于“已逐个预热指定 PoP”

---

## 七、前端进度弹窗（Vue + SSE）

创建版本时前端通过 SSE 实时接收进度，无需轮询：

```
前端点击"保存并发布"
        │
        ▼  POST /api/firmware/versions  →  返回 taskId
建立 SSE 连接 /api/tasks/{taskId}/subscribe
        │
        ▼  进度弹窗展示各阶段状态

┌──────────────────────────────────────┐
│  ⬆  上传固件到存储    [████████░░] 80% │
│  🔥 CDN 预热          [等待中]         │
└──────────────────────────────────────┘

        │  完成后
        ▼

┌──────────────────────────────────────┐
│  ✅  上传固件到存储    完成            │
│  ✅  CDN 预热          完成            │
│                                      │
│      固件版本发布成功！               │
│                        [ 关闭 ]      │
└──────────────────────────────────────┘
```

**断线重连处理：** 页面刷新或网络抖动时，通过 `GET /api/tasks/{taskId}/status` 查询 DB 中记录的最后任务状态，判断是否需要重连 SSE 或直接展示最终结果。

---

## 八、迁移 Checklist

| # | 步骤 | 备注 |
|---|---|---|
| ☐ 1 | 创建 R2 Bucket：`fota-firmware` | |
| ☐ 2 | 生成 R2 API Token（仅 fota-firmware 读写权限）| 记录 Access Key + Secret |
| ☐ 3 | 修改 `application.yml` 环境变量指向 R2 | 见下方配置示例 |
| ☐ 4 | 测试上传一个文件验证 S3 API 兼容性 | 确认 200 响应 |
| ☐ 5 | R2 Bucket → Settings → 绑定自定义域名 `fota-cdn.example.com` | 域名需托管在 CF |
| ☐ 6 | Caching → Tiered Cache → 开启 Smart Tiered Cache | 免费，一键开启 |
| ☐ 7 | 添加 Cache Rules：`/firmware/*` 缓存 30 天，Cache Everything = ON | |
| ☐ 8 | 新建 `fota-framework/fota-framework-task` 模块，实现异步任务 + SSE | |
| ☐ 9 | 新建 `fota-framework/fota-framework-cdn` 模块，实现预热逻辑 | |
| ☐ 10 | `FirmwareVersionAppService` 集成 task + cdn，改造创建版本流程 | |
| ☐ 11 | Vue 前端创建版本弹窗接入 SSE 进度展示，实现断线重连 | |
| ☐ 12 | 部署 `warm-worker.js` 到 Cloudflare Workers，设置 `WARM_SECRET` | |
| ☐ 13 | 灰度验证：发布测试版本，观察 `CF-Cache-Status` 是否为 `HIT` | |

**环境变量配置示例：**

```bash
# Storage S3 配置切换为 R2
STORAGE_S3_ENABLED=true
STORAGE_S3_ENDPOINT=https://<CF_ACCOUNT_ID>.r2.cloudflarestorage.com
STORAGE_S3_REGION=auto
STORAGE_S3_BUCKET=fota-firmware
STORAGE_S3_URL_ACCESS_TYPE=like_r2

# CDN 预热配置（新增）
APP_CDN_WARM_ENABLED=true
APP_CDN_WARM_BASE_URL=https://fota-cdn.example.com
```

---

## 九、免费额度说明

| 服务 | 免费额度 | 预估用量（每月3次发布）| 是否够用 |
|---|---|---|---|
| R2 存储 | 10 GB | < 1 GB（10个固件包×100MB）| ✅ |
| R2 Class A 操作 | 100万次/月 | < 100次（上传）| ✅ |
| R2 Class B 操作 | 1000万次/月 | < 1000次（CDN 回源）| ✅ |
| CDN 带宽 | 无限（R2→CDN 免费）| 无限制 | ✅ |
| Workers 请求 | 10万次/天 | < 100次/天（手动预热）| ✅ |
| Smart Tiered Cache | 完全免费 | - | ✅ |

> 超出免费额度费用参考：R2 超出存储 $0.015/GB/月，Class B 超出 $0.36/百万次。按每月发布几次的频率，几乎不会触发额外费用。
