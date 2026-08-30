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
- Shows a one-time pre-permission rationale before the system Accessibility dialog, naming
  what the service reads and how it is used (the disclosure Google Play requires).
- Lets you, from a settings screen, choose which apps the service watches at all, turn the
  nudge off per app, set a global and per-app daily time budget (a preset or a custom number
  of minutes), pick the stats window, and choose how long history is kept.

## Core principles

- Privacy-first, local-first, zero tracking, zero advertising.
- No user account, no cloud processing, no analytics or ad SDKs.
- No unnecessary permissions; no `INTERNET` permission.
- Accessibility data is read on-device only, never stored, never sent anywhere.
- No cloud or device-to-device backup of app data (`android:allowBackup="false"`).
- Minimal battery usage: react to events, filter early, debounce, no polling.

## Current status

Early development. Priorities, in order: correct architecture, AccessibilityService
foundation, reliable detection, BlockingEngine, privacy, battery efficiency, testing, UI.

The full pipeline is wired end to end. Implemented:

- `ScrollKillAccessibilityService`: filters `typeWindowStateChanged` /
  `typeWindowContentChanged` events, drops anything outside the watched package set,
  debounces bursts per package (250 ms), then drives the rest of the pipeline. It is the
  only component that acts (one `GLOBAL_ACTION_BACK`). The watched set is user-configurable
  and pushed to the framework via `setServiceInfo()`, so unwatched apps are filtered before
  their events ever reach the callback. The package filter + debounce (`EventFilter`) and
  the bounded tree walk (`SnapshotExtractor` over a framework-free `NodeView`) are extracted
  from the service so they are plain unit tests.
- Detection: `AppDetector`, `DetectionResult`, `ScreenSnapshot`, `SnapshotExtractor`,
  `ScreenDetector`, and the shared `SignalMatching` helper. Four pure, unit-tested
  detectors: `InstagramDetector` (Reels + home feed + Explore),
  `YouTubeShortsDetector` (Shorts), `TikTokDetector` (For You / Following),
  `FacebookDetector` (News Feed + Reels). Their signal-token lists are best-effort against
  current app builds and expected to drift.
- `BlockingEngine`: turns a `DetectionResult` into a `BlockingDecision` using a confidence
  floor, a blockable-surface set (`FEED`, `SHORT_VIDEO`, `EXPLORE`), a per-package cooldown,
  and a rolling per-app daily time budget (`DailyUsageMeter`, seeded from history on start);
  honours a per-app disable from settings. The daily budget is `DailyLimit.Off`, one of a
  few presets, or any custom whole-minute value.
- Detection/blocking tuning in DataStore (`DetectionPolicy`): the `BlockingEngine` cooldown
  and confidence floor and the `SessionTracker` idle timeout and minimum session duration
  are settings-backed, pushed to the running service on change. Defaults reproduce the
  previous compile-time constants, so behaviour is unchanged until edited; there is no
  picker UI for them yet.
- `SessionTracker`: folds the detection stream into in-memory `Session` records (per app,
  per surface, with an idle timeout and a minimum duration).
- Repository: Room for session history (`SessionRepository`, epoch stamping, retention
  pruning, a per-app usage aggregate query, exported schema, no destructive migration
  fallback) and DataStore for preferences (`SettingsRepository`). `ScrollKillApp` is the
  composition root (no DI framework).
- ViewModel + Compose UI: a one-time onboarding/rationale screen; a home screen
  (accessibility-service status with a deep link, the master "nudge me" toggle, a "today"
  card with feed time since local midnight and a per-app daily-limit progress bar, and a
  per-app usage summary over a selectable window); and a settings screen (watch on/off and
  nudge on/off per app, a global plus per-app daily time budget, stats window, history
  retention).
- A fixed brand Material 3 theme with explicit light and dark colour schemes (no dynamic
  colour), replacing the project template palette.

Planned / not yet built:

- A settings screen for the `DetectionPolicy` values (they are DataStore-backed but not yet
  surfaced), and per-surface toggles for the blockable / tracked surface sets (still
  compile-time constants).
- The first real Room `Migration`. The schema is still at version 1, so there is nothing to
  migrate yet; the destructive fallback is already gone and `MigrationTestHelper`
  scaffolding is in place for the first schema change.
- Verifying the per-app detector signal tokens against current app builds, plus a repeatable
  re-check process, as the target apps drift.
- Richer interventions beyond a single Back press.
- More surfaces and detectors.
- A designed launcher icon (still the Android Studio template) and general UI polish.
- A release keystore and a signed build: the signing config is wired but reads a
  `keystore.properties` that is not checked in, and R8 / minification is left off for now.

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
  MainActivity.kt              hosts the Compose screens (Onboarding / Home / Settings)
  ScrollKillApp.kt             Application; composition root for the DB and repositories
  blocking/                    BlockingDecision, BlockingEngine, DailyUsageMeter
  data/session/                Room: SessionEntity, SessionDao, ScrollKillDatabase,
                               Migrations, SessionRecord, PerAppUsage, SessionRepository
  data/settings/               DataStore: ScrollKillSettings, SettingsRepository,
                               DailyLimit, DetectionPolicy, StatsWindow, RetentionWindow
  detection/                   AppDetector, DetectionResult, ScreenSnapshot,
                               SignalMatching, ScreenDetector, and the per-app detectors
                               (Instagram, YouTube Shorts, TikTok, Facebook)
  service/                     ScrollKillAccessibilityService, EventFilter, SnapshotExtractor,
                               NodeView, AccessibilityServiceStatus
  session/                     Session, SessionTracker
  ui/onboarding/               OnboardingViewModel, OnboardingScreen
  ui/home/                     HomeViewModel, HomeScreen, HomeUiState, DurationFormat,
                               DayBoundary, KnownApps
  ui/settings/                 SettingsViewModel, SettingsScreen, SettingsUiState
  ui/theme/                    Compose theme (fixed brand light/dark schemes)
app/schemas/                   exported Room schema (v1)
app/src/main/res/xml/accessibility_service_config.xml
app/src/test/java/.../         unit tests: detectors, BlockingEngine, SessionTracker,
                               EventFilter, SnapshotExtractor, repositories, view models
                               (Home / Settings / Onboarding) and the Home / Settings
                               screens (Robolectric + Compose UI test)
app/src/androidTest/java/.../  instrumented tests: Room schema / migration
                               (ScrollKillDatabaseMigrationTest)
```

## Building and testing

Requirements: Android SDK with API 37, and the Gradle wrapper. The build's JVM toolchain
is auto-provisioned (JDK 25 via foojay); the app itself compiles to Java 11 bytecode.

```
./gradlew assembleDebug              # build the debug APK
./gradlew testDebugUnitTest          # run JVM unit tests (some use Robolectric)
./gradlew connectedDebugAndroidTest  # run instrumented tests (needs a device/emulator)
./gradlew installDebug               # install on a connected device/emulator
./gradlew assembleRelease            # release APK; unsigned unless keystore.properties exists
```

For a signed release build, copy `keystore.properties.example` to `keystore.properties` (it
is git-ignored) and fill in the keystore path and credentials. Without that file the release
build still assembles, as `app-release-unsigned.apk`. R8 / minification is currently off;
`app/proguard-rules.pro` holds the keep rules for when it is enabled.

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
