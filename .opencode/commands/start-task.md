---
name: start-task
type: command
version: v1.0.0
description: Start a new isolated development task with branch + worktree + tmux
---

User wants to start a new development task.

Task description:
$ARGUMENTS

Your responsibilities:

STEP 1 — Analyze Task
- Determine type: feature | refactor | fix | experiment
- Generate git-safe kebab-case name
- Branch format: type/name
- Session format: type-name
- Worktree format: ../worktrees/type-name

STEP 2 — Execute Shell Commands

You MUST:

1. Check if branch exists
2. Create branch if not exists
3. Create worktree if not exists
4. Create tmux session if not exists
5. cd into worktree
6. Start claude CLI inside tmux session

Use the following shell command structure:

Check branch:

git show-ref --verify --quiet refs/heads/<branch>

Create branch:

git branch <branch>

Create worktree:

git worktree add <worktree> <branch>

Check session:

tmux has-session -t <session>

Create session:

tmux new-session -d -s <session>

Send commands:

tmux send-keys -t <session> "cd <worktree>" C-m
tmux send-keys -t <session> "claude" C-m

Important:

- Execute commands step by step.
- Do NOT ask the user for confirmation unless necessary.
- If something exists, skip creation.
- At the end, print:

Task ready.
Branch:
Worktree:
Session:
Attach using: tmux attach -t <session>
