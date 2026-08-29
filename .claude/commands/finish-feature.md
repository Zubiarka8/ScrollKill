---
description: Finish the current feature branch — commit, push, and prepare a PR summary
---

## Task
The user wants to finish the current feature branch and hand it off for review.

## Steps
1. Run `git status` and `git diff` to review what changed. Confirm nothing unrelated to this feature is included — if unrelated files are staged or modified, stop and ask the user how to handle them (separate commit, separate branch, or leave out).
2. Confirm the current branch is not `main`. If it is, stop — this command must not run on `main`.
3. Run the relevant tests/build for what changed (per CLAUDE.md testing rules). Report pass/fail. If tests fail, stop and report the failure — do not commit broken code.
4. Stage and commit the changes with a clear, conventional commit message (type: short description).
5. Push the branch: `git push -u origin <current-branch-name>`.
6. Write a short PR summary (3–5 lines): what was built, why, and anything the reviewer should pay attention to (tradeoffs, known limitations, follow-up work).
7. Do NOT merge automatically. Report the branch is pushed and ready for PR/review, and stop.

## Session note
This is the end of this feature's work. Tell the user to start a new Claude Code session before beginning the next feature (via `/new-feature`), rather than continuing in this one — a fresh session avoids carrying this feature's exploration history into the next, which wastes tokens and can leak irrelevant context.
