# Detector fixture capture

Record a real app's on-screen view hierarchy once, then iterate the detectors against it on
the JVM - no device, no ScrollKill install, no accessibility permission, no ColorOS
"restricted settings" dance.

Why this exists: enabling ScrollKill's `AccessibilityService` on a sideloaded debug build is
painful on ColorOS, and the in-app debug card needs a human to read it. `uiautomator dump` is
part of Android and returns the same `resource-id` / `class` / `text` / `content-desc` /
`package` the detectors key on, for whatever is on screen.

## 1. Capture (needs adb once)

Open the app + surface you want on the device (real or emulator), scroll once so the feed is
populated, then:

```
./scripts/detector-capture/capture.sh   tiktok-fyp        # bash
./scripts/detector-capture/capture.ps1  tiktok-fyp        # PowerShell
```

Writes `scripts/detector-capture/out/<name>-<timestamp>.xml` (git-ignored) and prints the
focused package so you can confirm you captured the right screen.

Suggested names: `tiktok-fyp`, `instagram-reels`, `instagram-feed`, `instagram-explore`,
`youtube-shorts`, `facebook-feed`, `facebook-reels`.

adb tips for ColorOS (the link drops after ~20-30 s): use **wireless debugging**
(`adb pair` / `adb connect`) rather than USB; the scripts already retry with backoff.
An **emulator** avoids the problem entirely (the doc warns emulators are unrepresentative for
battery *profiling*, but they are fine for "does the detector read the right tokens").

## 2. Promote to a fixture

Review the XML, then copy it into the committed fixtures dir:

```
cp scripts/detector-capture/out/tiktok-fyp-20260901-183000.xml \
   app/src/test/resources/detector-fixtures/tiktok-fyp.xml
```

## 3. Iterate on the JVM

```
./gradlew testDebugUnitTest
```

- `DetectorFixtureReportTest` runs every fixture through the production `SnapshotExtractor` +
  `ScreenDetector` and writes `app/build/reports/detector-fixtures/report.txt`: the routed
  detector's surface + confidence + fired signals, a pass/`BELOW THRESHOLD` verdict, and the
  full token dump per fixture. It always passes - it is a worksheet, not a gate.
- Add regression assertions for a specific fixture in
  `app/src/test/java/com/ikasle/scrollkill/detection/UiHierarchyFixtureTest.kt`.

To fix drift: read the `BELOW THRESHOLD` fixture's token dump in the report, pick stable
substrings for that surface out of the real `viewIds` / `classNames` / `texts` /
`contentDescriptions`, update the detector's `*_TOKENS` lists, re-run. See
`docs/maintenance/detector-token-recheck.md` for the scoring model and which token groups are
structurally dead.

## What the parser keeps

`UiHierarchyFixture` maps each `<node>`: `resource-id` -> viewId, `class` -> className,
`text`, `content-desc`, `package`. Bounds/clickable/etc. are dropped. The tree is fed through
the real `SnapshotExtractor` (same `maxNodes` / `maxDepth` caps as on device), so the report
reflects what the service would actually collect.

Caveat: `uiautomator dump` only includes nodes marked important-for-accessibility - the same
blind spot as the service (`flagIncludeNotImportantViews` is off), so this is faithful, but
tokens living on skipped nodes are invisible to both.
