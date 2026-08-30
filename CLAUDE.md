# Scroll Kill

## Product
Scroll Kill es una app Android centrada en reducir el consumo compulsivo de contenido infinito en redes sociales.

## Core principles
- Privacy-first
- Local-first
- Zero tracking
- Zero advertising
- No user account required
- No cloud processing
- No unnecessary permissions
- Minimal battery usage
- High performance
- Simple architecture
- Reliable detection

## Technology
- Native Android
- Kotlin
- Jetpack Compose
- Coroutines
- Flow / StateFlow
- DataStore
- Room only where necessary
- AccessibilityService for cross-app detection

## Architecture

AccessibilityService
→ EventFilter
→ ScreenDetector
→ AppDetector
→ BlockingEngine
→ SessionTracker
→ Repository
→ ViewModel
→ Compose UI

Los detectores deben estar separados por aplicación.

Ejemplo:
- InstagramDetector
- YouTubeDetector
- TikTokDetector
- FacebookDetector

## Privacy rules
- Never send accessibility data to a server.
- Never store screenshots.
- Never store raw AccessibilityNodeInfo.
- Never collect unnecessary user data.
- No analytics SDKs.
- No advertising SDKs.
- No tracking SDKs.
- Keep user data local.
- Store only the minimum data required.

## Battery rules
- Never use continuous polling.
- Never create unnecessary infinite loops.
- React to AccessibilityService events.
- Filter package names immediately.
- Ignore irrelevant events.
- Debounce repeated events.
- Avoid expensive operations inside accessibility callbacks.
- Avoid unnecessary database writes.
- Aggregate session information in memory before persistence.

## Performance rules
- Keep AccessibilityService lightweight.
- Avoid traversing the complete accessibility tree unless necessary.
- Cache detection state where appropriate.
- Avoid unnecessary allocations.
- Do not perform blocking I/O in accessibility callbacks.
- Move heavier work away from the callback when appropriate.

## Detection rules
Detection must not depend on a single text string.

Use multiple signals when available:
- text
- contentDescription
- viewIdResourceName
- className
- node hierarchy
- actions
- package name
- window state

Use confidence-based detection where appropriate.

Design detectors so they can be updated independently when third-party apps change.

## Blocking
Detection and blocking must be separate systems.

Detectors return a DetectionResult.

BlockingEngine decides what action to perform.

Do not hardcode blocking behavior inside individual app detectors.

## Storage
Use DataStore for preferences.

Use Room only for structured historical/aggregated statistics.

Do not write every AccessibilityEvent to the database.

## Dependencies
Prefer AndroidX and official Android libraries.

Do not add a dependency when the Android SDK or existing project dependencies already provide the required functionality.

Every new dependency must have a clear justification.

## Code quality
- Prefer simple code over abstraction for abstraction's sake.
- Small classes.
- Single responsibility.
- Avoid giant files.
- Avoid unnecessary design patterns.
- Keep APIs explicit.
- Use immutable state where possible.
- Write tests for important logic.

## Testing
Important logic must have unit tests.

At minimum:
- detectors
- BlockingEngine
- debounce/throttling
- session tracking
- settings/repositories

## Android / Google Play
AccessibilityService is a critical part of the product.

Before implementing or changing AccessibilityService behavior, verify current official Android documentation and current Google Play policy requirements.

Never invent Android APIs.

## Working rules for Claude Code
- At the start of each task, scan graphify-out/ for orientation (GRAPH_REPORT.md, or `graphify query` against graph.json for "where does X live / what depends on Y / trace this flow"). It is a snapshot from the last `/graphify` run and is git-ignored: use it as a map, still read the real source before editing, and fall back to normal exploration if it is stale or absent.
- First inspect existing code before modifying it.
- Do not rewrite working code unnecessarily.
- Keep changes scoped to the requested task.
- Do not add features that were not requested.
- Do not add dependencies without justification.
- Prefer incremental changes.
- After meaningful changes, run the relevant tests/build.
- If uncertain about an Android API, verify official documentation instead of guessing.
- If a requirement conflicts with privacy, battery efficiency or reliability, stop and explain the tradeoff.

## Workflow skills (use proactively)

Route work through the matching skill from `.claude/skills/` instead of ad-hoc editing. Invoke it
via the Skill tool at the start of the task; do not wait to be asked.

- New feature / behaviour / product slice -> `lean-build` (after the `/feature` branch+plan+approve steps; `lean-build` covers the implementation).
- Bug fix or small behaviour change -> `surgical-patch`.
- Restructure with no behaviour change (extract strings, theme rework, rename, consolidate) -> `safe-refactor`.
- Schema / API / data / dependency migration, anything needing rollback (e.g. Room migrations) -> `migration`.
- Validation-only or "is this done / does it pass" -> `verify-and-stop`.
- Ambiguous or intermittent failure, unknown cause -> `investigate-first` before editing.

Rules:
- Pick one skill per task; if the task splits (a fix plus a refactor), do them as separate scoped passes.
- These do not replace CLAUDE.md rules or the `/feature` git workflow - they run inside them.
- Trivial one-liners and pure questions need no skill.
- Do not spawn subagents unless explicitly asked. The `cavecrew-*` subagents referenced by the `cavecrew` skill are not installed here.

## Current development stage
The project is in early development.

Prioritize:
1. Correct architecture
2. AccessibilityService foundation
3. Reliable detection
4. BlockingEngine
5. Privacy
6. Battery efficiency
7. Testing
8. UI polish

Do NOT implement everything at once.

## Response style
- Read existing files before writing. Don't re-read unless changed.
- Thorough in reasoning, concise in output.
- Skip files over 100KB unless required.
- No sycophantic openers or closing fluff.
- No emojis or em-dashes.
- Do not guess APIs, versions, flags, commit SHAs, or package names. Verify by reading code or docs before asserting.

## Git workflow
- Before starting any new feature, create a branch named feature/<short-description> from main (e.g. feature/accessibility-detector).
- Use fix/<short-description> for bug fixes, chore/<short-description> for maintenance tasks.
- Never commit directly to main.
- One feature per branch — do not mix unrelated changes in the same branch.
- Before creating a branch, confirm main is up to date (git pull) to avoid branching from stale state.
- After finishing a feature, do not merge automatically — stop and let the user review the diff first.
