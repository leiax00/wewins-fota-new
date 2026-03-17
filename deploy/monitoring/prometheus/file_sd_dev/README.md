# Prometheus File-based Service Discovery

## 概述

使用文件服务发现（File-based Service Discovery）实现 targets 的热更新，无需重启 Prometheus。

## 文件说明

| 文件 | 说明 |
|------|------|
| `fota-services.json` | FOTA 服务实例（API、下载服务等） |
| `node-exporters.json` | Node Exporter 主机监控 |
| `cadvisors.json` | cAdvisor 容器监控 |

## 添加新主机

### 示例：添加 region-us 的新主机

在 `node-exporters.json` 中添加：

```json
[
  {
    "targets": ["node-exporter:9100"],
    "labels": {
      "region": "main",
      "env": "production",
      "cluster": "fota-prod",
      "host": "lax",
      "node": "main-lax-node-exporter"
    }
  },
  {
    "targets": ["192.168.1.10:9100"],
    "labels": {
      "region": "us",
      "env": "production",
      "cluster": "fota-prod",
      "host": "us-host-1",
      "node": "us-host-1-node-exporter"
    }
  }
]
```

## 标签规范

| 标签 | 说明 | 示例值 |
|------|------|--------|
| `region` | 区域 | `main`, `us`, `eu`, `cn` |
| `env` | 环境 | `production`, `staging`, `development` |
| `cluster` | 集群名称 | `fota-prod`, `fota-staging` |
| `host` | 主机名 | `lax`, `us-host-1` |
| `node` | 节点唯一标识 | `main-lax-node-exporter` |
| `service` | 服务类型 | `fota-api`, `fota-download` |

## 热更新机制

- Prometheus 每 30 秒自动检测文件变化（`refresh_interval: 30s`）
- 修改 JSON 文件后，最多等待 30 秒即生效
- 无需重启 Prometheus

## 验证方法

1. 访问 http://localhost:19090/targets 查看 targets 状态
2. 修改 JSON 文件后等待 30 秒
3. 刷新页面验证新 targets 已加载

## 注意事项

- JSON 文件必须格式正确，否则 Prometheus 将忽略整个文件
- 每次修改建议先在本地验证 JSON 格式
- `targets` 数组中可包含多个目标（同一主机多个端口）
