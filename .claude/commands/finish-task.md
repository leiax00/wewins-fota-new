---
name: finish-task
type: command
version: v1.0.0
description: Merge current task branch into develop (no-ff) and remove worktree, keep branch
---

You are finishing the current task.

STEP 1 — Detect current branch

Run:

git rev-parse --abbrev-ref HEAD

Store as CURRENT_BRANCH.

If CURRENT_BRANCH is:
- main
- master
- develop

Abort:
"Refusing to finish protected branch."

STEP 2 — Ensure clean working tree

Run:

git status --porcelain

If output is not empty:
Abort:
"Working tree not clean. Commit or stash first."

STEP 3 — Get repository root

Run:

git rev-parse --show-toplevel

Store as ROOT_DIR.

STEP 4 — Switch to develop in root repository

Run:

cd ROOT_DIR
git checkout develop

If develop does not exist:
Abort.

STEP 5 — Merge with no fast-forward

Run:

git merge --no-ff CURRENT_BRANCH -m "merge: CURRENT_BRANCH into develop"

If conflict:
Stop and report.

STEP 6 — Remove worktree (but keep branch)

Run:

git worktree remove <PATH_OF_CURRENT_WORKTREE>

STEP 7 — Prune unused metadata

Run:

git worktree prune

STEP 8 — Done

Print:

Task merged with --no-ff.
Branch kept.
Worktree removed.
