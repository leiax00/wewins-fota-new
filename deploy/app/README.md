# FOTA 应用部署指南

## 架构说明

本目录包含应用层服务，基础服务由 `deploy/infrastructure/` 提供。

```
deploy/
├── infrastructure/    # 基础服务层
│   ├── pgsql, redis, clickhouse, rustfs, rabbitmq, npm
│
└── app/               # 应用层
    ├── backend    # Spring Boot 后端
    ├── ui         # Vue 前端（独立容器）
    └── npm        # Nginx Proxy Manager（反向代理）
```

## 服务说明

| 容器 | 镜像 | 端口 | 说明 |
|------|------|------|------|
| backend | 自定义 | 8080 | 后端 API 服务 |
| ui | fota-ui:latest | - | 前端静态文件（nginx:alpine） |
| npm | jc21/nginx-proxy-manager | 80, 443, 81 | 反向代理 + SSL |

## 目录结构

```
deploy/app/
├── docker-compose.yaml    # 应用编排
├── .env.example           # 环境变量模板
└── README.md              # 本文档
```

## 快速开始

### 1. 准备外部网络

```bash
docker network create self
```

### 2. 启动基础服务

```bash
cd deploy/infrastructure
docker-compose up -d
```

### 3. 启动应用服务

```bash
cd deploy/app
cp .env.example .env
docker-compose up -d
```

## 访问地址

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端 UI | http://localhost | NPM 代理提供 |
| 后端 API | http://localhost:8080 | 直接访问 |
| NPM 管理界面 | http://localhost:81 | 管理反向代理 |

## Nginx Proxy Manager 配置

NPM 默认登录：
- 地址：http://localhost:81
- 账号：`admin@example.com`
- 密码：`changeme`

### 代理规则配置

登录 NPM 后，配置以下代理规则：

| 规则 | 域名 | 前端 | 后端 | 说明 |
|------|------|------|------|------|
| FOTA 前端 | localhost | / | ui:80 | 静态文件 |
| FOTA API | /api | /api | backend:8080 | 后端接口 |

### 配置步骤

1. **前端代理**：
   - Domain Names: `localhost`
   - Scheme: `http`
   - Forward Hostname: `ui`
   - Forward Port: `80`

2. **API 代理**（在同一个 Host 中添加 Location）：
   - Location: `/api`
   - Scheme: `http`
   - Forward Hostname: `backend`
   - Forward Port: `8080`

## 服务依赖

```
                    ┌───────────────────────────────────────┐
                    │          Nginx Proxy Manager          │
                    │              :80/:81/:443             │
                    └───────────────┬───────────────────────┘
                                    │
                    ┌───────────────┴───────────────┐
                    ▼                               ▼
            ┌───────────────┐               ┌──────────────┐
            │  UI 容器      │               │   Backend    │
            │  (nginx)      │◄──────────────│   :8080      │
            │  ui:80        │               └──────────────┘
            └───────────────┘
                    │
                    └───────────────┐ self
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      基础服务层 (infrastructure/)                    │
│  ┌─────────┐   ┌──────────┐   ┌────────────┐   ┌────────────┐     │
│  │  pgsql  │   │redis-stack│   │ RabbitMQ  │   │ ClickHouse │     │
│  │  :5432  │   │  :6379   │   │  :5672    │   │  :18123    │     │
│  └─────────┘   └──────────┘   └────────────┘   └────────────┘     │
└─────────────────────────────────────────────────────────────────────┘
```

## 常用命令

```bash
# 构建并启动
docker-compose up -d --build

# 仅重新构建 UI
docker-compose build ui
docker-compose up -d ui

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f backend
docker-compose logs -f ui
docker-compose logs -f npm

# 重启服务
docker-compose restart backend
docker-compose restart ui

# 停止服务
docker-compose down
```

## 更新 UI

当 UI 代码变更后：

```bash
cd deploy/app
docker-compose build --no-cache ui
docker-compose up -d ui
```
