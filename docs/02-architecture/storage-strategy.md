# 文件存储策略说明

- 文档版本：v1.0
- 创建日期：2026-02-16
- 适用范围：`fota-framework-storage`、`fota-service`

---

## 1. 目标

在同一套代码中支持两种最终存储方式：

1. 本地存储（默认可用）
2. S3 兼容存储（可选启用）

并且始终使用本地目录作为临时文件（staging）落盘位置。

---

## 2. 存储策略

系统遵循以下固定策略：

1. 本地存储始终可用（`LocalStorageClient`）
2. S3 存储按配置启用（`app.storage.s3.enabled=true`）
3. 固件最终落库规则：
   - 若 S3 已启用且可用：最终对象存入 S3
   - 若 S3 未启用：最终对象存入本地
4. 临时文件始终写入本地 `tmp` 目录

---

## 3. 自动装配行为

核心配置类：`com.wewins.fota.storage.config.StorageFrameworkAutoConfiguration`

关键 Bean：

1. `localStorageClient`：始终创建
2. `s3StorageClient`：仅 `app.storage.s3.enabled=true` 时创建
3. `storageClient`：统一入口，按“有 S3 则 S3，否则本地”选择
4. `fileTransferService`：始终依赖 `storageClient` 和本地临时目录

说明：`fileTransferService` 使用 `@Qualifier("storageClient")` 显式注入，避免多个 `StorageClient` 实现导致的歧义。

---

## 4. 配置建议

### 4.1 基础配置（所有环境）

在 `application.yml` 中保留结构和环境变量占位，不放生产密钥：

```yaml
app:
  storage:
    defaultTtlSeconds: ${STORAGE_DEFAULT_TTL_SECONDS:3600}
    local:
      baseDir: ${STORAGE_LOCAL_BASE_DIR:data/storage}
    s3:
      enabled: ${STORAGE_S3_ENABLED:true}
      endpoint: ${STORAGE_S3_ENDPOINT}
      region: ${STORAGE_S3_REGION:us-east-1}
      bucket: ${STORAGE_S3_BUCKET}
      accessKey: ${STORAGE_S3_ACCESS_KEY}
      secretKey: ${STORAGE_S3_SECRET_KEY}
      url-access-type: ${STORAGE_S3_URL_ACCESS_TYPE:path_style}
```

### 4.2 开发配置（可选默认值）

在 `application-dev.yml` 中提供开发默认值，并允许环境变量覆盖：

```yaml
app:
  storage:
    s3:
      accessKey: ${STORAGE_S3_ACCESS_KEY:change-me}
      secretKey: ${STORAGE_S3_SECRET_KEY:change-me}
```

---

## 5. 运行模式示例

### 仅本地存储

```bash
export STORAGE_S3_ENABLED=false
```

### S3 最终存储

```bash
export STORAGE_S3_ENABLED=true
export STORAGE_S3_ENDPOINT=https://oss.example.com
export STORAGE_S3_BUCKET=fota
export STORAGE_S3_ACCESS_KEY=xxx
export STORAGE_S3_SECRET_KEY=yyy
```

---

## 6. 运维注意事项

1. 本地 `tmp` 目录要保证写权限与容量监控
2. S3 模式下需配置合理的超时与重试策略（后续可扩展）
3. 生产环境禁止在仓库内提交真实密钥
4. 建议通过环境变量或密钥管理系统注入敏感参数
