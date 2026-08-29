# ScrollKill

An Android app that helps you stop compulsively scrolling infinite feeds in social media apps.

ScrollKill runs a lightweight `AccessibilityService` that notices when an infinite-scroll
surface (Reels, Shorts, the TikTok feed, ...) comes to the foreground in another app, and
is being built up toward gently interrupting that session. Everything happens on-device.

## What it does

- Watches a configurable set of social apps for infinite-content surfaces.
- Uses per-app detectors that combine several UI signals (view ids, class names, content
  descriptions, package, window state) into a confidence score, rather than matching a
  single text string.
- Keeps detection and blocking as separate systems: a detector only returns a
  `DetectionResult`; deciding what to do with it is a different concern.

## Core principles

- Privacy-first, local-first, zero tracking, zero advertising.
- No user account, no cloud processing, no analytics or ad SDKs.
- No unnecessary permissions.
- Accessibility data is read on-device only, never stored, never sent anywhere.
- Minimal battery usage: react to events, filter early, debounce, no polling.

## Current status

Early development. Priorities, in order: correct architecture, AccessibilityService
foundation, reliable detection, BlockingEngine, privacy, battery efficiency, testing, UI.

Implemented:

- `ScrollKillAccessibilityService` skeleton: receives `typeWindowStateChanged` /
  `typeWindowContentChanged` events, drops anything outside the watched package set, and
  debounces bursts per package (250 ms).
- Detection primitives: `AppDetector` interface, `DetectionResult`, `ScreenSnapshot`, and
  the shared `SignalMatching.containsAnyToken` helper.
- Three pure, unit-tested detectors: `InstagramDetector` (Reels), `YouTubeShortsDetector`
  (Shorts), `TikTokDetector` (For You / Following feed).

Planned / not yet built:

- Wiring the detectors into the service (`EventFilter` -> `ScreenDetector` ->
  `AppDetector` -> `DetectionResult`).
- `BlockingEngine`, `SessionTracker`, repositories, and persistence (DataStore for
  preferences, Room only for aggregated stats).
- Real Compose UI (`MainActivity` is still the template scaffold).
- User-configurable watched-app list via `setServiceInfo()`.

## Architecture

Target pipeline (see [CLAUDE.md](CLAUDE.md) for the full rules):

```
AccessibilityService
  -> EventFilter
  -> ScreenDetector
  -> AppDetector        (one implementation per third-party app)
  -> BlockingEngine     (decides the action; detectors never do)
  -> SessionTracker
  -> Repository
  -> ViewModel
  -> Compose UI
```

Detectors are deliberately framework-free and side-effect free so they stay unit-testable
and can be updated independently when a third-party app changes its UI.

## Project layout

Single Gradle module, `:app` (namespace `com.ikasle.scrollkill`).

```
app/src/main/java/com/ikasle/scrollkill/
  MainActivity.kt              template Compose scaffold (placeholder UI)
  detection/                   AppDetector, DetectionResult, ScreenSnapshot,
                               SignalMatching, InstagramDetector,
                               YouTubeShortsDetector, TikTokDetector
  service/                     ScrollKillAccessibilityService
  ui/theme/                    Compose theme
app/src/main/res/xml/accessibility_service_config.xml
app/src/test/java/.../detection/   detector unit tests
```

## Building and testing

Requirements: Android SDK with API 37, and the Gradle wrapper. The build's JVM toolchain
is auto-provisioned (JDK 25 via foojay); the app itself compiles to Java 11 bytecode.

```
./gradlew assembleDebug        # build the debug APK
./gradlew testDebugUnitTest    # run JVM unit tests (detectors)
./gradlew installDebug         # install on a connected device/emulator
```

Toolchain: Kotlin 2.2.10, Android Gradle Plugin 9.3.2, Compose BOM 2026.02.01,
`minSdk 24`, `targetSdk 37`.

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
