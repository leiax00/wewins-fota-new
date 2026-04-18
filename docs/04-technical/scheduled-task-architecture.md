# 定时任务架构设计

> **版本**: v2.0
> **创建日期**: 2026-04-15
> **修订日期**: 2026-04-16
> **适用范围**: fota-service 全部业务级定时任务 + fota-framework-scheduler 框架模块

---

## 背景

随着业务迭代，定时任务数量不断增长，但原先分散在各处：

- 固件清理任务（`infra/task/`）
- ClickHouse 回放任务（`infra/persistence/clickhouse/`）
- Sprint 5 新增的统计快照任务（待实现）

这种分散方式有两个问题：
1. **难以统计全貌**：没有一个地方能看到所有业务定时任务
2. **难以切换调度引擎**：`@Scheduled` 注解直接嵌在业务类里，未来引入 XXL-Job 需要改动所有任务类

---

## 设计原则

### 三层分离

定时任务分为**框架抽象层**、**基础设施级**和**业务级**三类，采用不同的管理策略：

| 类型 | 代表任务 | 管理方式 |
|------|---------|---------|
| **框架抽象层** | `FotaScheduledJob` 接口、`LeaderElectionService` 接口 | 独立模块 `fota-framework-scheduler`，不包含任何具体任务实现 |
| **基础设施级** | 固件清理、Leader 心跳、节点注册、Sentinel 规则刷新、SSE 队列 | 留在各自模块的 `scheduler` 包下，自带 `@Scheduled`，每个实例独立执行 |
| **业务级** | ClickHouse 回放、统计快照计算 | 统一在 `fota-service/infra/scheduler/` 中，由 `ScheduledJobRunner` 管理触发 + Leader 选举 |

### 触发与执行分离

`@Scheduled` 注解（或未来的 XXL-Job Handler）只管**何时触发**，业务逻辑只管**做什么**，两者通过 `FotaScheduledJob` 接口隔开。切换调度引擎时，业务逻辑类零改动。

### 各模块统一包名

所有模块的定时任务统一放在 `scheduler` 包下，便于快速定位：

```
fota-framework-storage/.../storage/scheduler/   # 基础设施级
fota-service/.../infra/scheduler/                # 业务级
fota-framework-scheduler/                        # 框架抽象层
```

---

## 框架模块：fota-framework-scheduler

独立的框架模块，只提供抽象和默认实现，不包含任何具体业务任务。

### 目录结构

```
fota-framework/fota-framework-scheduler/
└── src/main/java/com/wewins/fota/scheduler/
    ├── FotaSchedulerMarker.java              # 模块标记类
    ├── config/
    │   ├── SchedulerAutoConfiguration.java   # @EnableScheduling + 默认 Leader Bean
    │   └── SchedulerProperties.java          # 配置属性 (app.scheduler.enabled)
    ├── job/
    │   └── FotaScheduledJob.java             # 业务任务公共接口
    └── leader/
        ├── LeaderElectionService.java        # Leader 选举接口
        └── AlwaysLeaderElectionService.java  # 默认实现（单实例模式始终返回 true）
```

### FotaScheduledJob 接口

```java
/**
 * 业务级定时任务接口。
 * 所有业务定时任务实现此接口，与调度引擎解耦。
 */
public interface FotaScheduledJob {

    /** 任务唯一名称，切换 XXL-Job 时作为 Handler 名使用 */
    String jobName();

    /** 任务执行入口 */
    void execute();
}
```

### LeaderElectionService 接口

```java
/**
 * Leader 选举服务接口。
 * 用于在多实例部署场景下确保定时任务只在 Leader 节点执行。
 */
public interface LeaderElectionService {

    /** 判断当前节点是否为 Leader */
    boolean isLeader();
}
```

默认实现 `AlwaysLeaderElectionService` 始终返回 `true`，适用于单实例部署。多实例部署时由 `RegionLeaderService` 覆盖。

---

## 基础设施级任务

基础设施级任务**留在各自所属模块**，自带 `@Scheduled` 注解，每个实例独立执行，不经过 `ScheduledJobRunner`。

### 固件临时文件清理（fota-framework-storage）

```
fota-framework-storage/.../storage/scheduler/
├── FirmwareCleanupTask.java          # 固件临时文件清理（@Scheduled，每 30 分钟）
└── FirmwareCleanupProperties.java    # 配置属性 (app.firmware.upload.cleanup.*)
```

- 每个实例独立执行（清理本地文件系统，不需要 Leader 选举）
- 通过 `@ConditionalOnProperty` 控制是否启用

### 其他基础设施级任务（不迁移，保持原位）

| 任务 | 所在模块 | 频率 | 说明 |
|------|---------|------|------|
| `RegionLeaderService.heartbeat` | fota-framework-cache | 每 10 秒 | Leader 续期，与选举逻辑强耦合 |
| `NodeRegistryService.heartbeat` | fota-framework-cache | 每 20 秒 | 节点注册，与集群逻辑强耦合 |
| `SentinelRuleManager.refreshRules` | fota-service/sentinel | 每 30 秒 | 各实例本地刷新，与 Sentinel 配置强耦合 |
| `SseLogBroadcaster`（×2） | fota-service/logging | 50ms / 心跳 | 各实例管理自己的 SSE 连接 |

> 这些任务的 `@Scheduled` 嵌入在多功能服务类中，不适合拆分为独立的 `FotaScheduledJob` 实现。

---

## 业务级任务

业务级任务统一在 `fota-service/infra/scheduler/` 中管理。

### 目录结构

```
fota-service/src/main/java/com/wewins/fota/infra/scheduler/
├── ScheduledJobRunner.java            # 业务任务统一触发层
└── job/
    ├── ClickHouseFallbackJob.java     # ClickHouse 回放兜底
    └── StatisticsSnapshotJob.java     # 统计快照计算（Sprint 5）
```

### ScheduledJobRunner（触发层）

所有业务级定时任务的触发逻辑**集中在这一个类里**：

```java
@Component
@RequiredArgsConstructor
public class ScheduledJobRunner {

    private final ClickHouseFallbackJob clickHouseFallbackJob;
    private final StatisticsSnapshotJob statisticsSnapshotJob;
    private final ObjectProvider<RegionLeaderService> leaderServiceProvider;

    /** ClickHouse 事件回放兜底，每 5 分钟执行一次 */
    @Scheduled(fixedDelayString = "${app.scheduler.clickhouse-fallback.interval-ms:300000}")
    public void runClickHouseFallback() {
        runIfLeader(clickHouseFallbackJob);
    }

    /** 统计快照计算，每 5 分钟执行一次 */
    @Scheduled(fixedDelayString = "${app.scheduler.statistics-snapshot.interval-ms:300000}")
    public void runStatisticsSnapshot() {
        runIfLeader(statisticsSnapshotJob);
    }

    private void runIfLeader(FotaScheduledJob job) {
        RegionLeaderService leaderService = leaderServiceProvider.getIfAvailable();
        if (leaderService != null && !leaderService.isLeader()) {
            return;
        }
        try {
            job.execute();
        } catch (Exception e) {
            log.error("[Scheduler] 任务执行失败: job={}", job.jobName(), e);
        }
    }
}
```

`ScheduledJobRunner` 是项目中**业务级定时任务数量的唯一入口**，新增任务只需在此类加一个方法。

---

## Leader 选举集成

### RegionLeaderService 改造

`RegionLeaderService`（位于 `fota-framework-cache`）原先仅限 `app.mode=region` 激活。改造后移除该限制，使其在所有部署模式下可用：

```
多实例部署
  Pod-A（Leader）→ isLeader() = true  → 执行
  Pod-B          → isLeader() = false → 跳过
  Pod-C          → isLeader() = false → 跳过
```

主区域 nodeCode 形如 `main-01`，`RegionCodeResolver` 解析出 `regionCode=main`，Redis key 为 `fota:region:leader:main`，选举逻辑与区域节点完全一致。

**不需要引入 XXL-Job 或其他框架**来解决 Leader 选举问题，项目已有现成实现。

---

## 切换 XXL-Job 的路径

当满足以下条件时可以考虑引入 XXL-Job：
- 需要在管理后台动态修改任务 cron（不重启服务）
- 需要可视化任务执行历史和失败告警
- 业务级任务数量增长到 10 个以上，运维负担明显

**切换步骤**（业务任务类**零改动**）：

1. 引入 XXL-Job 依赖，部署 XXL-Job Server
2. 新增 `XxlJobRunner` 类，用 `@XxlJob(job.jobName())` 注册各任务
3. 通过 `@ConditionalOnProperty` 禁用 `ScheduledJobRunner`
4. XXL-Job 本身保证单机路由，可移除 `isLeader()` 判断

```java
// 切换后仅新增此类，原有 Job 实现类一行不改
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduler.engine", havingValue = "xxl-job")
public class XxlJobRunner {

    private final ClickHouseFallbackJob clickHouseFallbackJob;
    private final StatisticsSnapshotJob statisticsSnapshotJob;

    @XxlJob("clickhouse.fallback")
    public void runClickHouseFallback() { clickHouseFallbackJob.execute(); }

    @XxlJob("statistics.snapshot")
    public void runStatisticsSnapshot() { statisticsSnapshotJob.execute(); }
}
```

---

## 现有任务一览

### 业务级（ScheduledJobRunner 统一管理，需 Leader）

| 任务类 | jobName | 频率 | 状态 |
|--------|---------|------|------|
| `ClickHouseFallbackJob` | `clickhouse.fallback` | 每 5 分钟 | 已迁移 |
| `StatisticsSnapshotJob` | `statistics.snapshot` | 每 5 分钟 | Sprint 5 新增 |

### 基础设施级（各自模块自带 @Scheduled，每实例执行）

| 任务 | 所在模块 | 频率 | 说明 |
|------|---------|------|------|
| `FirmwareCleanupTask` | fota-framework-storage | 每 30 分钟 | 已迁移到 storage 模块 |
| `RegionLeaderService.heartbeat` | fota-framework-cache | 每 10 秒 | Leader 续期 |
| `NodeRegistryService.heartbeat` | fota-framework-cache | 每 20 秒 | 节点注册 |
| `SentinelRuleManager.refreshRules` | fota-service/sentinel | 每 30 秒 | 各实例本地刷新 |
| `SseLogBroadcaster`（×2） | fota-service/logging | 50ms / 心跳 | 各实例管理自己的连接 |
