---
name: finish-task
type: command
version: v1.1.0
description: Worktree-safe merge current task branch into base branch and remove worktree, keep branch
---

You are finishing the current task in a worktree-safe manner.

STEP 1 — Detect current branch and worktree path

Run:

git branch --show-current
git rev-parse --show-toplevel

Store as CURRENT_BRANCH and FEATURE_WT_PATH.

If CURRENT_BRANCH is empty (detached HEAD):
Abort: "Detached HEAD detected. Please checkout a branch first."

If CURRENT_BRANCH is one of: main, master, develop:
Abort: "Refusing to finish protected branch: CURRENT_BRANCH."

STEP 2 — Determine base branch

If $ARGUMENTS is not empty:
  Set BASE_BRANCH to the first token of $ARGUMENTS.
Else:
  Set BASE_BRANCH to "develop".

If CURRENT_BRANCH == BASE_BRANCH:
  Abort: "Current branch is already BASE_BRANCH."

If BASE_BRANCH is one of: main, master:
  Abort: "Refusing to merge into protected base branch: BASE_BRANCH."

STEP 3 — Locate base branch worktree

Run:

git worktree list --porcelain

Find the worktree path where `branch refs/heads/BASE_BRANCH` is checked out.
Store as BASE_WT_PATH.

Use this copy/paste snippet:

```bash
BASE_WT_PATH="$(
python3 - "$BASE_BRANCH" <<'PY'
import subprocess
import sys

target = f"refs/heads/{sys.argv[1]}"
output = subprocess.check_output(
    ["git", "worktree", "list", "--porcelain"],
    text=True,
)

current_wt = None
for line in output.splitlines():
    if line.startswith("worktree "):
        current_wt = line.split(" ", 1)[1]
    elif line.startswith("branch "):
        branch = line.split(" ", 1)[1]
        if branch == target and current_wt:
            print(current_wt)
            break
PY
)"
```

If BASE_WT_PATH is not found:
Abort: "Base branch 'BASE_BRANCH' is not checked out in any worktree. Please checkout 'BASE_BRANCH' to a worktree first."

STEP 4 — Safety checks (Feature Worktree)

Run:

git -C "$FEATURE_WT_PATH" status --porcelain

git -C "$FEATURE_WT_PATH" rev-parse --git-path MERGE_HEAD
git -C "$FEATURE_WT_PATH" rev-parse --git-path REBASE_HEAD
git -C "$FEATURE_WT_PATH" rev-parse --git-path CHERRY_PICK_HEAD
git -C "$FEATURE_WT_PATH" rev-parse --git-path REVERT_HEAD

Store outputs as FEATURE_MERGE_HEAD_PATH, FEATURE_REBASE_HEAD_PATH, FEATURE_CHERRY_PICK_HEAD_PATH, FEATURE_REVERT_HEAD_PATH.

If output is not empty:
Abort: "Feature worktree is dirty. Commit or stash changes first."

If any of those feature operation paths exists:
Abort: "Feature worktree has an operation in progress (merge/rebase/etc.). Please resolve it first."

STEP 5 — Safety checks (Base Worktree)

Run:

git -C "$BASE_WT_PATH" status --porcelain
git -C "$BASE_WT_PATH" branch --show-current

If status output is not empty:
Abort: "Base worktree at 'BASE_WT_PATH' is dirty. Please clean it first."

If current branch in base worktree is not BASE_BRANCH:
Abort: "Base worktree at 'BASE_WT_PATH' is not on 'BASE_BRANCH'. It is on '$(git -C "$BASE_WT_PATH" branch --show-current)'."

Run:

git -C "$BASE_WT_PATH" rev-parse --git-path MERGE_HEAD
git -C "$BASE_WT_PATH" rev-parse --git-path REBASE_HEAD
git -C "$BASE_WT_PATH" rev-parse --git-path CHERRY_PICK_HEAD
git -C "$BASE_WT_PATH" rev-parse --git-path REVERT_HEAD

Store outputs as MERGE_HEAD_PATH, REBASE_HEAD_PATH, CHERRY_PICK_HEAD_PATH, REVERT_HEAD_PATH.

If any of those paths exists:

Abort: "Base worktree has an operation in progress (merge/rebase/etc.). Please resolve it first."

STEP 6 — Merge with no fast-forward

Run:

git -C "$BASE_WT_PATH" merge --no-ff "$CURRENT_BRANCH" -m "merge: $CURRENT_BRANCH into $BASE_BRANCH"

If conflict:
Stop and report: "Merge conflict in '$BASE_WT_PATH'. Please resolve manually. Worktree '$FEATURE_WT_PATH' NOT removed."

STEP 7 — Remove feature worktree

Run:

git -C "$BASE_WT_PATH" worktree remove "$FEATURE_WT_PATH"

Note: This runs in the base worktree context (via `git -C`), so it avoids the failure mode where removing a worktree fails because the current working directory is inside it.

STEP 8 — Prune worktrees (optional)

Run:

git -C "$BASE_WT_PATH" worktree prune

STEP 9 — Done

Print:

Task merged into BASE_BRANCH.
Base Worktree: BASE_WT_PATH
Feature Worktree: FEATURE_WT_PATH (Removed)
Branch kept: CURRENT_BRANCH
