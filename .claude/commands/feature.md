---
description: Start new work the disciplined way — branch, plan mode, scope confirmation, explicit approval before any code
---

## Task
The user wants to start new work: $ARGUMENTS

This command enforces the front-of-feature workflow from CLAUDE.md so it does not have to be
restated every time: branch first, plan before code, confirm scope, wait for explicit approval.
It supersedes `/new-feature` (it does the same branch step, then keeps going).

## Steps

1. **Branch.** Run `git status`. If the tree is not clean, stop and tell the user to commit or
   stash first. Otherwise run `git checkout main && git pull`, then create and check out a branch:
   `feature/<short-description>` derived from $ARGUMENTS (lowercase, hyphens for spaces). Use
   `fix/<...>` if $ARGUMENTS clearly describes a bug fix, `chore/<...>` for maintenance. If
   `git pull` fails for lack of an upstream, tell the user to run `git push -u origin main` first
   and stop. Confirm with `git branch --show-current`. Skip this step only if already on a
   non-`main` `feature|fix|chore/` branch that matches this work.

2. **Enter plan mode.** Call EnterPlanMode now. Do not create or edit any file, do not write code,
   until the plan is approved in step 6.

3. **Inspect first.** Read the existing code and config this work will touch (CLAUDE.md working
   rule: inspect existing code before modifying). Do not plan to rewrite working code.

4. **Confirm scope.** State explicitly, in a few lines:
   - In scope: what this work will do.
   - Out of scope: what it deliberately will not do.
   - Which current-development-stage priority it serves (architecture / AccessibilityService
     foundation / reliable detection / BlockingEngine / privacy / battery / testing / UI).
   Ask at most 2-3 clarifying questions, most critical first, if anything is ambiguous. Wait for
   answers before drafting the plan.

5. **Draft the plan.** A numbered, incremental step list. Explicitly flag any of:
   - a change likely to exceed ~200 lines,
   - a new dependency (must be justified per CLAUDE.md; prefer AndroidX / existing deps),
   - anything that changes AccessibilityService behavior — verify current official Android docs
     and current Google Play policy first, never invent APIs,
   - a conflict with privacy, battery efficiency or reliability — stop and explain the tradeoff,
   - which tests will cover it (detectors, BlockingEngine, debounce/throttling, session tracking,
     settings/repositories).

6. **Get approval.** Present the plan with ExitPlanMode. Do not implement anything until the user
   approves it.

7. **Implement.** After approval only: incremental changes, scoped to what was agreed — no extra
   features. Run the relevant tests/build after meaningful changes and report pass/fail. If new
   gaps or a direction change appear, stop and re-confirm scope before continuing.

## Session note
Run this in a fresh Claude Code session so no unrelated history is carried in. Do not assume
knowledge of prior features beyond what CLAUDE.md documents. When the work is done, hand it off
with `/finish-feature` and start the next feature in a new session.
