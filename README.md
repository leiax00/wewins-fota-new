# wewins-fota-new

<div align="center">

**千万级 IoT 设备固件 OTA（FOTA）管理平台**

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16+-blue)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

面向大规模设备升级、跨区域部署与高并发场景设计

[项目概述](#项目概述) • [快速开始](#快速开始) • [文档导航](#文档导航) • [开发指南](#开发指南)

</div>

---

## 项目概述

`wewins-fota-new` 是一个面向千万级 IoT 设备的固件 OTA（Over-The-Air）管理平台，旨在解决大规模设备升级中的核心挑战：

- **高并发**：支撑 10,000+ QPS，99% 的检查请求 < 50ms
- **跨区域**：通过公网 HTTPS 实现主区域与区域的数据同步与配置管理
- **灰度发布**：基于 Hash 的稳定灰度机制，支持 0-100% 步进控制
- **可观测**：实时统计设备活跃度、升级进度，支持离线分析与故障演练
- **安全可靠**：JWT 认证、HMAC 签名、幂等设计、断线降级与恢复

### 当前状态

> 📋 **规划完成，实施启动中（文档驱动阶段）**

✅ 架构方案、任务分解、里程碑规划已完成
🚀 正在按照 Milestone 1 推进基础框架搭建

---

## 核心特性

### 🚀 高性能设备检查链路

- **目标**：10,000+ QPS，99% 请求 < 50ms
- **热路径优化**：只读本地 Redis + Caffeine 二级缓存
- **并发控制**：设备级限流、时间窗口管理

### 🌐 同构单体多模式部署

- **同一代码库，同一镜像**
- `MODE=main`：主区域模式（管理后台 + 配置中心 + 数据汇聚）
- `MODE=region`：区域模式（设备 API + 配置同步 + 数据转发）

### 🔄 跨区域控制面与数据面

- **配置同步**：Region 每 30 秒轮询 Main，版本号 + 快照模式
- **数据转发**：Forwarder 批量聚合数据，支持断线缓冲与恢复
- **幂等设计**：HMAC + nonce 防重放，batch_id 幂等键

### 🎯 灰度发布

- **灰度算法**：`hash(imei) % 100 < gray_rate`
- **精确控制**：百分比控制、时间窗口、一键停止

### 📊 可观测与可靠性

- **设备活跃度**：Redis Bitmap 统计（1 亿设备 ≈ 12MB/天）
- **事件追踪**：ClickHouse 明细日志 + 1 分钟聚合
- **故障演练**：主区不可达、网络分区、MQ 堆积恢复

---

## 技术架构

### 技术栈

| 类别 | 技术 | 版本 | 用途 |
|------|------|------|------|
| **后端** | Java | 21 | 开发语言 |
| **框架** | Spring Boot | 3.2+ | 应用框架 |
| **数据库** | PostgreSQL | 16+ | 主数据存储 |
| **缓存** | Redis | 7+ | 缓存、限流、Bitmap |
| **分析** | ClickHouse | 23+ | 事件日志与聚合 |
| **消息队列** | RabbitMQ | 3.12+ | 异步事件处理 |
| **部署** | Docker | - | 容器化部署 |

### 系统架构图

```mermaid
flowchart TB
    subgraph "设备侧"
        Device[IoT 设备]
    end

    subgraph "区域部署 (MODE=region)"
        RegionAPI[设备 API<br/>check/report]
        RegionSync[配置同步 Worker<br/>30s 轮询]
        RegionForward[Forwarder<br/>批量转发]
        LocalRedis[(Redis<br/>策略快照)]
        LocalMQ[(RabbitMQ)]
        LocalCH[(ClickHouse<br/>事件明细)]
    end

    subgraph "主区域 (MODE=main)"
        MainAPI[Internal API<br/>版本/快照/Ingest]
        MainAdmin[管理后台<br/>产品/策略/固件]
        MainPG[(PostgreSQL<br/>产品/设备/策略)]
        MainCH[(ClickHouse<br/>汇聚数据)]
        MainStorage[(RustFS/S3<br/>固件存储)]
    end

    subgraph "CDN"
        CDN[CDN 节点]
    end

    Device -->|check/report| RegionAPI
    RegionAPI --> LocalRedis
    RegionAPI --> LocalMQ
    LocalMQ --> LocalCH

    RegionSync -->|HTTPS+HMAC| MainAPI
    RegionForward -->|HTTPS+HMAC| MainAPI
    MainAPI --> MainPG
    MainAPI --> MainCH

    MainStorage --> CDN
    RegionAPI -->|签名 URL| CDN
    Device -->|下载固件| CDN
```

### 数据流说明

1. **检查更新流程**：
   - 设备 → Region API（读本地 Redis 策略快照）
   - 标记活跃设备（Redis Bitmap）
   - 匹配策略（灰度、时间窗）
   - 返回签名下载 URL

2. **上报流程**：
   - 设备 → Region API → RabbitMQ（立即返回）
   - Consumer → ClickHouse（异步写入）
   - 聚合器 → Forwarder → Main（批量转发）

3. **配置同步**：
   - Region 每 30s 轮询 Main 版本号
   - 版本变化时拉取快照
   - 原子切换本地 Redis 版本指针

---

## 快速开始

> ⚠️ **注意**：当前仓库处于规划阶段，工程骨架正在搭建中。以下为预期启动路径。

### 环境准备

**必需组件**：
- JDK 21
- Maven 3.9+
- Docker & Docker Compose
- Git

**依赖服务**：
```bash
# 使用 Docker Compose 启动开发环境
docker-compose up -d postgres redis rabbitmq clickhouse
```

### 获取代码

```bash
git clone https://github.com/your-org/wewins-fota-new.git
cd wewins-fota-new
```

### 构建与运行

```bash
# 构建
mvn clean package

# 主区域模式
java -jar -DMODE=main target/fota-service.jar

# 区域模式
java -jar -DMODE=region target/fota-service.jar
```

### 验证安装

```bash
# 健康检查
curl http://localhost:8080/actuator/health

# 设备检查更新（示例）
curl "http://localhost:8080/v1/upgrade/check?product=test&imei=123456789012345&version=v1.0.0"
```

---

## 文档导航

### 核心文档

| 文档 | 说明 | 链接 |
|------|------|------|
| **产品需求** | 业务需求、功能规格、交互流程 | [docs/prd.md](docs/prd.md) |
| **技术架构** | 系统架构、技术选型、接口设计 | [docs/FOTA 系统架构及技术说明书.md](docs/FOTA%20系统架构及技术说明书.md) |
| **文件存储策略** | 本地临时文件与 S3 可选最终存储策略 | [docs/02-architecture/storage-strategy.md](docs/02-architecture/storage-strategy.md) |
| **实施计划** | Milestone 规划、验收标准、风险管理 | [docs/implementation-plan.md](docs/implementation-plan.md) |
| **任务列表** | 全量任务清单、任务依赖、技术细节 | [docs/tasks.md](docs/tasks.md) |

### 模板文档

| 文档 | 说明 | 链接 |
|------|------|------|
| **Week 1 验收模板** | Milestone 1 Week 1 验收报告模板 | [docs/week1-acceptance-template.md](docs/week1-acceptance-template.md) |
| **风险登记模板** | 项目风险管理模板 | [docs/risk-log-template.md](docs/risk-log-template.md) |

### Agent 配置

| 文档 | 说明 | 链接 |
|------|------|------|
| **Agent 指南** | Claude Code Agent 使用说明 | [agent/README.md](agent/README.md) |
| **编码规范** | Java/TypeScript 编码标准 | [agent/rules/coding-style.md](.claude/rules/coding-style.md) |

---

## 开发指南

### 开发流程

1. **阅读实施计划**：从 [docs/implementation-plan.md](docs/implementation-plan.md) 了解整体规划
2. **领取任务**：参考 [docs/tasks.md](docs/tasks.md) 中的任务编号（如 #1, #2, N1）
3. **开发与测试**：遵循编码规范，编写单元测试
4. **提交 PR**：关联任务编号，描述变更内容
5. **代码评审**：通过评审后合并主干

### 代码规范

- **Java 规范**：参考 [agent/rules/coding-style.md](.claude/rules/coding-style.md)
- **提交信息**：使用 Conventional Commits 格式
- **分支策略**：feature/* 功能分支，develop 开发分支，main 主干分支

### 测试要求

- **单元测试**：核心业务逻辑覆盖率 > 80%
- **集成测试**：关键 API 端到端测试
- **性能测试**：Milestone 2 开始基线压测

### 环境配置

参考 [CLAUDE.md](CLAUDE.md) 了解项目核心约束与架构原则。

---

## 项目进度

### Milestone 状态

| Milestone | 名称 | 状态 | 完成度 |
|-----------|------|------|--------|
| M1 | 平台基线与最小安全 | 🚀 进行中 | 0% |
| M2 | 核心数据面 MVP | 📋 计划中 | - |
| M3 | 跨区域控制面与汇聚面 | 📋 计划中 | - |
| M4 | 运营能力与完整 RBAC | 📋 计划中 | - |
| M5 | 生产就绪与 GA Gate | 📋 计划中 | - |

### 当前任务

**Milestone 1 - Week 1**（基础框架建立周）
- ⏳ Day 1：工程骨架初始化
- ⏳ Day 2：PostgreSQL 迁移基础
- ⏳ Day 3：Redis 关键结构
- ⏳ Day 4：简化认证与最小授权
- ⏳ Day 5：联调与验收

### 下一步计划

1. ⏳ 完成 Spring Boot 工程骨架搭建
2. ⏳ 建立 PostgreSQL 迁移脚本
3. ⏳ 设计 Redis Key 规范与脚本
4. ⏳ 实现简化 JWT 认证
5. ⏳ 创建测试数据引导脚本

---

## 贡献指南

欢迎贡献代码、文档或提出建议！

### 如何贡献

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交变更 (`git commit -m 'feat: add some amazing feature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交 Pull Request

### 贡献规范

- 遵循现有代码风格
- 添加必要的测试与文档
- 提交信息清晰明确
- PR 描述关联相关任务编号

---

## 许可证

本项目采用 [MIT License](LICENSE) 开源协议。

---

## 联系方式

- **项目维护者**：FOTA 架构组
- **问题反馈**：[GitHub Issues](https://github.com/your-org/wewins-fota-new/issues)
- **讨论交流**：[GitHub Discussions](https://github.com/your-org/wewins-fota-new/discussions)

---

<div align="center">

**Built with ❤️ for IoT Community**

</div>
