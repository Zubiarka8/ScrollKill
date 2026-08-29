---
description: Create a new feature branch and confirm before starting work
---

## Task
The user wants to start a new feature: $ARGUMENTS

## Steps
1. Run `git status` to confirm the working tree is clean. If not, stop and tell the user to commit or stash first.
2. Run `git checkout main && git pull` to sync with the latest main. If `git pull` fails because there is no upstream yet, tell the user to run `git push -u origin main` first and stop.
3. Create and check out a new branch named `feature/<short-description>`, derived from $ARGUMENTS (lowercase, hyphens instead of spaces).
4. Confirm the new branch was created with `git branch --show-current`.
5. Report the branch name and stop — do not start implementing anything yet. Wait for the user's next instruction.

## Session note
After creating the branch, treat this as a fresh context — do not assume knowledge of prior features beyond what CLAUDE.md documents. If this session has already been used for a different feature, tell the user to start a new Claude Code session before continuing, to avoid carrying unrelated history and wasting tokens.
