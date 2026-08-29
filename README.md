# ScrollKill

An Android app that helps you stop compulsively scrolling infinite feeds in social media apps.

ScrollKill runs a lightweight `AccessibilityService` that notices when an infinite-scroll
surface (Reels, Shorts, the TikTok feed, the Facebook feed, ...) comes to the foreground in
another app, and interrupts the session with a single Back press when you have that turned
on. It records how long you spend on those surfaces and shows it back to you. Everything
happens on-device.

## What it does

- Watches a set of social apps for infinite-content surfaces (home feeds, Reels/Shorts,
  explore grids).
- Uses per-app detectors that combine several UI signals (view ids, class names, content
  descriptions, package, window state) into a confidence score, rather than matching a
  single text string.
- Keeps detection and blocking as separate systems: a detector only returns a
  `DetectionResult`; the `BlockingEngine` decides whether to act (with a per-app cooldown),
  and only the service performs the action.
- Aggregates each visit into an in-memory session, persists it to a local Room database,
  and surfaces it on a home screen: a "today" summary (feed time since local midnight, with
  a per-app daily-limit progress bar) plus per-app "time spent" and "nudges" over a
  selectable window.
- Lets you disable the nudge per app, pick the stats window, and choose how long history
  is kept, from a settings screen.

## Core principles

- Privacy-first, local-first, zero tracking, zero advertising.
- No user account, no cloud processing, no analytics or ad SDKs.
- No unnecessary permissions.
- Accessibility data is read on-device only, never stored, never sent anywhere.
- Minimal battery usage: react to events, filter early, debounce, no polling.

## Current status

Early development. Priorities, in order: correct architecture, AccessibilityService
foundation, reliable detection, BlockingEngine, privacy, battery efficiency, testing, UI.

The full pipeline is wired end to end. Implemented:

- `ScrollKillAccessibilityService`: filters `typeWindowStateChanged` /
  `typeWindowContentChanged` events, drops anything outside the watched package set,
  debounces bursts per package (250 ms), then drives the rest of the pipeline. It is the
  only component that acts (one `GLOBAL_ACTION_BACK`).
- Detection: `AppDetector`, `DetectionResult`, `ScreenSnapshot`, `SnapshotExtractor`,
  `ScreenDetector`, and the shared `SignalMatching` helper. Four pure, unit-tested
  detectors: `InstagramDetector` (Reels + home feed + Explore),
  `YouTubeShortsDetector` (Shorts), `TikTokDetector` (For You / Following),
  `FacebookDetector` (News Feed + Reels).
- `BlockingEngine`: turns a `DetectionResult` into a `BlockingDecision` using a confidence
  floor, a blockable-surface set (`FEED`, `SHORT_VIDEO`, `EXPLORE`), a per-package cooldown,
  and a rolling per-app daily time budget (`DailyUsageMeter`, seeded from history on start);
  honours a per-app disable from settings.
- `SessionTracker`: folds the detection stream into in-memory `Session` records (per app,
  per surface, with an idle timeout and a minimum duration).
- Repository: Room for session history (`SessionRepository`, epoch stamping, retention
  pruning, a per-app usage aggregate query, exported schema) and DataStore for preferences
  (`SettingsRepository`). `ScrollKillApp` is the composition root (no DI framework).
- ViewModel + Compose UI: a home screen (accessibility-service status with a deep link, the
  master "nudge me" toggle, a "today" card with feed time since local midnight and a per-app
  daily-limit progress bar, and a per-app usage summary over a selectable window) and a
  settings screen (per-app nudge toggles, stats window, history retention).

Planned / not yet built:

- A picker in Settings for the per-app daily time budgets. The `BlockingEngine` already
  enforces them and the home screen already shows progress, but every limit currently
  resolves to "off" until the picker lands.
- User-configurable *watched*-app set via `setServiceInfo()`.
- Moving the `BlockingEngine` / `SessionTracker` numeric tuning (cooldown, confidence
  floor, idle timeout, minimum session) into settings.
- Real Room migrations (currently a destructive fallback, acceptable only in early dev).
- Richer interventions beyond a single Back press.
- More surfaces and detectors, plus hardening as the target apps drift.
- UI polish.

## Architecture

Pipeline (see [CLAUDE.md](CLAUDE.md) for the full rules):

```
AccessibilityService
  -> EventFilter        (package filter + per-package debounce, in the service)
  -> ScreenDetector
  -> AppDetector        (one implementation per third-party app)
  -> BlockingEngine     (decides the action; detectors never do)
  -> SessionTracker
  -> Repository         (Room for stats, DataStore for settings)
  -> ViewModel
  -> Compose UI
```

Detectors, `BlockingEngine` and `SessionTracker` are deliberately framework-free and
side-effect free so they stay unit-testable and can be updated independently when a
third-party app changes its UI.

## Project layout

Single Gradle module, `:app` (namespace `com.ikasle.scrollkill`).

```
app/src/main/java/com/ikasle/scrollkill/
  MainActivity.kt              hosts the Compose screens (Home / Settings)
  ScrollKillApp.kt             Application; composition root for the DB and repositories
  blocking/                    BlockingDecision, BlockingEngine, DailyUsageMeter
  data/session/                Room: SessionEntity, SessionDao, ScrollKillDatabase,
                               SessionRecord, PerAppUsage, SessionRepository
  data/settings/               DataStore: ScrollKillSettings, SettingsRepository,
                               DailyLimit, StatsWindow, RetentionWindow
  detection/                   AppDetector, DetectionResult, ScreenSnapshot,
                               SignalMatching, ScreenDetector, and the per-app detectors
                               (Instagram, YouTube Shorts, TikTok, Facebook)
  service/                     ScrollKillAccessibilityService, SnapshotExtractor,
                               AccessibilityServiceStatus
  session/                     Session, SessionTracker
  ui/home/                     HomeViewModel, HomeScreen, HomeUiState, DurationFormat,
                               DayBoundary, KnownApps
  ui/settings/                 SettingsViewModel, SettingsScreen, SettingsUiState
  ui/theme/                    Compose theme
app/schemas/                   exported Room schema
app/src/main/res/xml/accessibility_service_config.xml
app/src/test/java/.../         unit tests: detectors, BlockingEngine, SessionTracker,
                               repositories, view models
```

## Building and testing

Requirements: Android SDK with API 37, and the Gradle wrapper. The build's JVM toolchain
is auto-provisioned (JDK 25 via foojay); the app itself compiles to Java 11 bytecode.

```
./gradlew assembleDebug        # build the debug APK
./gradlew testDebugUnitTest    # run JVM unit tests (some use Robolectric)
./gradlew installDebug         # install on a connected device/emulator
```

Toolchain: Kotlin 2.2.10, Android Gradle Plugin 9.3.2, Compose BOM 2026.02.01, Room 2.8.4
(via KSP), DataStore 1.2.1, `minSdk 24`, `targetSdk 37`. Repository tests run under
Robolectric.

After installing, enable ScrollKill under Settings -> Accessibility to let the service run.

## Contributing

- Branch from `main`: `feature/<short-description>`, `fix/<short-description>`, or
  `chore/<short-description>`. Never commit directly to `main`.
- One feature per branch. Pull `main` before branching.
- Important logic needs unit tests (detectors, BlockingEngine, debounce/throttling,
  session tracking, settings/repositories).
- Before changing AccessibilityService behavior, check current official Android docs and
  Google Play policy. Do not invent Android APIs.
- If a change conflicts with privacy, battery efficiency, or reliability, stop and explain
  the tradeoff.

See [CLAUDE.md](CLAUDE.md) for the complete working rules.

## License

Released under the MIT License. See [LICENSE](LICENSE).
