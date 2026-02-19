---
name: finish-task
type: command
version: v1.0.0
description: Merge current task branch into develop and remove its worktree
---

You are finishing the current task.

STEP 1 — Detect current branch

Run:

git rev-parse --abbrev-ref HEAD

Store branch name as CURRENT_BRANCH.

If CURRENT_BRANCH is:
- main
- master
- develop

Abort with message:
"Refusing to finish a protected branch."

STEP 2 — Ensure clean working tree

Run:

git status --porcelain

If there are changes:
Abort and ask user to commit or stash.

STEP 3 — Switch to main repository root

Run:

git rev-parse --show-toplevel

Store as ROOT_DIR.

STEP 4 — Checkout develop in root repository

Run:

cd ROOT_DIR
git checkout develop

If develop does not exist:
Abort.

STEP 5 — Merge branch

Run:

git merge CURRENT_BRANCH

If merge conflict:
Stop and report.

STEP 6 — Remove worktree

Run:

git worktree remove $(pwd)

If fails:
Report error.

STEP 7 — Delete branch

Run:

git branch -d CURRENT_BRANCH

STEP 8 — Prune

Run:

git worktree prune

STEP 9 — Print result

Print:

Task finished successfully.
Merged into develop.
Worktree removed.
Branch deleted.
