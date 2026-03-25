---
name: finish-task
description: 从目标分支合并指定的功能分支，支持选择性清理 worktree 和分支
version: 2.1.0
---

# Finish Task

从目标分支（如 develop）合并指定的功能分支，并提供清理 worktree 和分支的选项。支持自动检测功能分支。

## 何时启用

- 用户在目标分支（如 develop）上完成功能开发
- 需要合并功能分支到当前所在的目标分支
- 调用方式：`/finish-task [功能分支名]`

## 使用方式

```bash
# 指定功能分支
/finish-task feature/sprint-5-cdn

# 自动检测并选择
/finish-task
```

参数 `<功能分支名>` 为可选项。省略时自动检测。

## 执行流程

### STEP 1 — 解析参数并验证当前分支

从 `$ARGUMENTS` 获取功能分支名：

```bash
FEATURE_BRANCH="${ARGUMENTS}"
CURRENT_BRANCH=$(git branch --show-current)
```

验证规则：

1. **当前分支为受保护分支**（main/master）：拒绝操作
   ```
   错误：禁止在 main/master 分支上执行合并操作
   ```

### STEP 2 — 确定功能分支（自动检测或用户指定）

#### 如果提供了参数

直接使用用户指定的 `$FEATURE_BRANCH`。

#### 如果参数为空

自动检测所有功能/修复分支：

```bash
# 获取所有功能/修复分支（排除 main/master/develop）
FEATURE_BRANCHES="$(
    git branch --format='%(refname:short)' |
    grep -E '^(feature|fix|refactor|test|docs|chore|hotfix)/' |
    grep -v -E '^(main|master|develop)$' |
    sort
)"
```

**无可用分支**：提示用户并中止

```
信息：未找到功能分支（feature/、fix/ 等）
用法: /finish-task <功能分支名>
```

**有可用分支**：使用 `AskUserQuestion` 询问用户选择

**问题**: 选择要合并的功能分支

**选项**:
- 列出检测到的所有分支（最多 4 个）
- `custom` — 用户输入自定义分支名

**示例**：
```
检测到以下功能分支，请选择要合并的分支：
1. feature/sprint-5-cdn
2. feature/sentinel-integration
3. fix/login-timeout
4. custom（自定义）
```

### STEP 3 — 确定目标分支

当前分支即为目标分支：

```bash
TARGET_BRANCH="$CURRENT_BRANCH"
```

如果 `TARGET_BRANCH` 不是 `develop`，发出警告但允许继续：

```
警告：当前分支不是 develop，合并目标为 $TARGET_BRANCH
```

### STEP 4 — 验证功能分支存在

```bash
if ! git rev-parse --verify "$FEATURE_BRANCH" >/dev/null 2>&1; then
    echo "错误：功能分支 '$FEATURE_BRANCH' 不存在"
    exit 1
fi
```

### STEP 5 — 查找功能分支的 worktree

使用以下代码片段查找功能分支的 worktree 路径：

```bash
FEATURE_WT_PATH="$(
python3 - "$FEATURE_BRANCH" <<'PY'
import subprocess, sys
target = f"refs/heads/{sys.argv[1]}"
output = subprocess.check_output(["git", "worktree", "list", "--porcelain"], text=True)
current_wt = None
for line in output.splitlines():
    if line.startswith("worktree "):
        current_wt = line.split(" ", 1)[1]
    elif line.startswith("branch ") and line.split(" ", 1)[1] == target and current_wt:
        print(current_wt)
        break
PY
)"
```

**未找到 worktree**：发出警告但继续合并流程

```
警告：功能分支 '$FEATURE_BRANCH' 无关联 worktree（已删除或从未创建）
```

### STEP 6 — 安全检查

**当前 worktree（目标分支）检查**：

```bash
# 检查是否有未提交的更改
if [ -n "$(git status --porcelain)" ]; then
    echo "错误：当前 worktree 有未提交的更改，请先提交或暂存"
    exit 1
fi

# 检查是否有进行中的操作
if git rev-parse --git-path MERGE_HEAD >/dev/null 2>&1 && [ -f "$(git rev-parse --git-path MERGE_HEAD)" ]; then
    echo "错误：有进行中的 merge 操作，请先完成或中止"
    exit 1
fi
# 同样检查 REBASE_HEAD、CHERRY_PICK_HEAD、REVERT_HEAD
```

**功能 worktree 检查**（如果存在）：

```bash
if [ -n "$FEATURE_WT_PATH" ]; then
    if [ -n "$(git -C "$FEATURE_WT_PATH" status --porcelain)" ]; then
        echo "错误：功能 worktree 有未提交的更改"
        exit 1
    fi
fi
```

### STEP 7 — 执行合并

```bash
git merge --no-ff "$FEATURE_BRANCH" -m "merge: $FEATURE_BRANCH into $TARGET_BRANCH"
```

**合并冲突处理**：中止并提示手动解决

```
错误：合并冲突，请手动解决后继续
```

### STEP 8 — 询问清理选项

合并成功后，使用 `AskUserQuestion` 分别询问清理选项：

**询问 1：是否删除功能 worktree？**

- 如果 `FEATURE_WT_PATH` 为空（无 worktree），跳过此询问
- 否则询问用户选择

**询问 2：是否删除功能分支？**

### STEP 9 — 执行清理

根据用户选择执行：

```bash
# 删除 worktree
if [ "$DELETE_WT" = "true" ] && [ -n "$FEATURE_WT_PATH" ]; then
    git worktree remove "$FEATURE_WT_PATH"
fi

# 删除分支
if [ "$DELETE_BRANCH" = "true" ]; then
    git branch -d "$FEATURE_BRANCH"
fi
```

### STEP 10 — 输出摘要

```
✅ 合并完成
目标分支: $TARGET_BRANCH
功能分支: $FEATURE_BRANCH
Worktree: [已删除/保留]
分支: [已删除/保留]
```

## 关键差异（v2.1 vs v1.1）

| 项目 | v1.1（command） | v2.1（skill） |
|------|-----------------|---------------|
| 执行位置 | 功能分支 | 目标分支（develop） |
| 参数 | 目标分支（可选，默认 develop） | 功能分支名（可选，自动检测） |
| 分支检测 | 无 | 自动检测 feature/、fix/ 等 |
| worktree 处理 | 自动删除 | 询问后决定 |
| 分支处理 | 保留 | 询问后决定 |

## 错误处理

所有错误情况必须明确中止并给出清晰的错误信息：

- 受保护分支操作（main/master）
- 无功能分支可合并（检测为空且未指定）
- 分支不存在
- worktree 未就绪（有未提交更改或进行中操作）
- 合并冲突
