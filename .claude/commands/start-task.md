---
name: start-task
type: command
version: v2.0.0
description: Start a new isolated development task with branch + worktree
---

## 任务输入

用户输入（$ARGUMENTS）可以是以下三种形式之一：

1. **文档路径**：如 `docs/05-plans/sprint-4-load-control.md`
   - 从文档中提取任务名称和描述
   - 自动确定任务类型

2. **任务描述**：如 `实现 Sentinel 限流集成`
   - 直接作为任务名称
   - 自动推断任务类型

3. **空（不输入）**：
   - 从当前对话上下文中推断任务
   - 如果无法推断，询问用户

---

## 执行步骤

### STEP 1 — 解析任务输入

根据 `$ARGUMENTS` 的内容判断输入类型：

```
if $ARGUMENTS 是文件路径（以 docs/ 开头或 .md 结尾）:
    → 从文档读取任务信息
    → 文档路径: $ARGUMENTS
    → 任务名称: 从文档标题或文件名提取
    → 任务类型: 从文件路径推断（如 plans/ → feature）

elif $ARGUMENTS 是非空文本:
    → 直接作为任务描述
    → 任务名称: 提取关键信息生成 kebab-case
    → 任务类型: 根据关键词推断

else (空):
    → 从当前对话上下文推断
    → 查找最近讨论的 sprint 文档或任务
    → 如果无法推断，询问用户
```

### STEP 2 — 确定命名

**任务类型推断规则**：
- 包含 `fix/修复/bug` → `fix`
- 包含 `refactor/重构/优化` → `refactor`
- 包含 `test/测试` → `test`
- 包含 `docs/文档` → `docs`
- 包含 `chore/杂项/清理` → `chore`
- 默认 → `feature`

**命名格式**：
- 分支名: `<type>/<name>`（如 `feature/sentinel-integration`）
- Worktree 目录: `../worktrees/<type>-<name>`（如 `../worktrees/feature-sentinel-integration`）

### STEP 3 — 执行 Git 操作

**必须按顺序执行**：

1. **检查分支是否存在**
   ```bash
   git show-ref --verify --quiet refs/heads/<branch>
   ```

2. **创建分支**（如果不存在）
   ```bash
   git branch <branch>
   ```

3. **检查 worktree 是否存在**
   ```bash
   git worktree list
   ```

4. **创建 worktree**（如果不存在）
   ```bash
   git worktree add <worktree-path> <branch>
   ```

**重要**：
- 如果分支或 worktree 已存在，跳过创建
- 不要删除或覆盖已存在的资源

### STEP 4 — 输出概览

创建完成后，输出以下格式的概览：

```
✅ 任务已准备就绪

📋 任务信息
   类型: <type>
   名称: <name>
   描述: <description>

🌿 Git 信息
   分支: <branch>
   Worktree: <worktree-path>

📂 切换到任务目录
   cd <worktree-path>

📄 任务文档（如适用）
   <doc-path>
```

---

## 示例

### 示例 1：指定文档

```
/start-task docs/05-plans/sprint-4-load-control.md
```

输出：
```
✅ 任务已准备就绪

📋 任务信息
   类型: feature
   名称: sprint-4-load-control
   描述: 动态周期调整与系统可观测性

🌿 Git 信息
   分支: feature/sprint-4-load-control
   Worktree: ../worktrees/feature-sprint-4-load-control

📂 切换到任务目录
   cd ../worktrees/feature-sprint-4-load-control

📄 任务文档
   docs/05-plans/sprint-4-load-control.md
```

### 示例 2：直接描述

```
/start-task 实现 Sentinel 限流集成
```

输出：
```
✅ 任务已准备就绪

📋 任务信息
   类型: feature
   名称: sentinel-integration
   描述: 实现 Sentinel 限流集成

🌿 Git 信息
   分支: feature/sentinel-integration
   Worktree: ../worktrees/feature-sentinel-integration

📂 切换到任务目录
   cd ../worktrees/feature-sentinel-integration
```

### 示例 3：空输入（从上下文推断）

```
/start-task
```

如果当前对话在讨论 Sprint 4，自动推断为 Sprint 4 相关任务。

---

## 注意事项

1. **不要自动切换目录**：只提供切换命令，让用户自己执行
2. **不要覆盖已有资源**：分支或 worktree 存在时跳过创建
3. **保持简洁**：输出清晰，不要冗余信息
