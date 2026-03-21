---
name: start-task
description: Start a new isolated development task with branch + worktree（交互式版本）
version: 3.0.0
---

# Start Task

启动新的隔离开发任务，支持创建分支和可选的 worktree。通过交互式询问让用户自定义命名和工作方式。

## 何时启用

- 用户开始新的功能开发、Bug 修复或重构任务
- 需要创建隔离的开发环境
- 调用方式：`/start-task [文档路径或任务描述]`

## 使用方式

```bash
# 指定文档
/start-task docs/05-plans/sprint-4-load-control.md

# 直接描述
/start-task 实现 Sentinel 限流集成

# 空输入（从上下文推断）
/start-task
```

## 执行流程

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

**任务类型推断规则**：
- 包含 `fix/修复/bug` → `fix`
- 包含 `refactor/重构` → `refactor`
- 包含 `test/测试` → `test`
- 包含 `docs/文档` → `docs`
- 包含 `chore/杂项/清理` → `chore`
- 默认 → `feature`

**建议的分支名格式**：`<type>/<name>`（如 `feature/sentinel-integration`）

### STEP 2 — 询问分支命名

使用 `AskUserQuestion` 询问用户分支命名：

**问题**: 是否采用建议的分支名？

**选项**:
- `采用建议` → 使用 `<type>/<name>` 格式
- `自定义名称` → 用户输入自定义分支名

如果选择自定义，获取用户输入的分支名。

**验证分支名**：
- 必须符合 git 分支命名规范
- 不能以 `-` 结尾
- 不能包含特殊字符（除 `/` 和 `-`）

### STEP 3 — 询问是否创建 Worktree

使用 `AskUserQuestion` 询问：

**问题**: 是否为分支创建独立的 worktree？

**选项**:
- `创建 worktree` → 在 `../worktrees/` 下创建独立工作目录
- `不创建` → 仅创建分支，不创建 worktree

**Worktree 命名格式**：`../worktrees/<type>-<name>`（如 `../worktrees/feature-sentinel-integration`）

### STEP 4 — 执行 Git 操作

#### 如果选择创建 Worktree：

**检查并创建分支**：
```bash
# 检查分支是否存在
if ! git show-ref --verify --quiet "refs/heads/$BRANCH"; then
    git branch "$BRANCH"
fi
```

**检查并创建 worktree**：
```bash
# 检查 worktree 是否存在
if ! git worktree list | grep -q "$WORKTREE_PATH"; then
    git worktree add "$WORKTREE_PATH" "$BRANCH"
fi
```

#### 如果选择不创建 Worktree：

**检查并创建分支**：
```bash
# 检查分支是否存在
if ! git show-ref --verify --quiet "refs/heads/$BRANCH"; then
    git branch "$BRANCH"
fi
```

**询问是否切换分支**：

使用 `AskUserQuestion` 询问：

**问题**: 是否切换到新创建的分支？

**选项**:
- `切换` → 执行 `git checkout "$BRANCH"`
- `保留当前` → 保持在当前分支

**重要**：如果当前 worktree 有未提交的更改，切换前必须警告用户并中止。

### STEP 5 — 输出概览

根据用户选择输出相应的概览：

**创建了 Worktree**：

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

**未创建 Worktree（已切换分支）**：

```
✅ 任务已准备就绪

📋 任务信息
   类型: <type>
   名称: <name>
   描述: <description>

🌿 Git 信息
   分支: <branch>
   当前位置: <current-worktree>

📄 任务文档（如适用）
   <doc-path>
```

**未创建 Worktree（保留当前分支）**：

```
✅ 任务已准备就绪

📋 任务信息
   类型: <type>
   名称: <name>
   描述: <description>

🌿 Git 信息
   分支: <branch>
   当前分支: <current-branch>

💡 切换到新分支
   git checkout <branch>

📄 任务文档（如适用）
   <doc-path>
```

## 示例

### 示例 1：创建 Worktree

```
/start-task docs/05-plans/sprint-4-load-control.md
```

询问流程：
1. 分支命名：采用建议 `feature/sprint-4-load-control`
2. 创建 worktree：是

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

### 示例 2：仅创建分支并切换

```
/start-task 修复登录超时问题
```

询问流程：
1. 分支命名：采用建议 `fix/login-timeout`
2. 创建 worktree：否
3. 切换分支：是

输出：
```
✅ 任务已准备就绪

📋 任务信息
   类型: fix
   名称: login-timeout
   描述: 修复登录超时问题

🌿 Git 信息
   分支: fix/login-timeout
   当前位置: /workspace/code/wewins/wewins-fota-new
```

### 示例 3：自定义分支名

```
/start-task 添加用户头像功能
```

询问流程：
1. 分支命名：自定义 `feature/avatar-upload-v2`
2. 创建 worktree：是

输出：
```
✅ 任务已准备就绪

📋 任务信息
   类型: feature
   名称: avatar-upload-v2
   描述: 添加用户头像功能

🌿 Git 信息
   分支: feature/avatar-upload-v2
   Worktree: ../worktrees/feature-avatar-upload-v2
```

## 注意事项

1. **不要自动切换目录**：只提供切换命令，让用户自己执行
2. **不要覆盖已有资源**：分支或 worktree 存在时跳过创建，提示用户
3. **切换分支前检查**：确保当前 worktree 没有未提交的更改
4. **保持简洁**：输出清晰，不要冗余信息
