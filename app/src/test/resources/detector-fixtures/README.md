# Detector fixtures

Real (and one synthetic) `uiautomator dump` XML captures, run through the production
`SnapshotExtractor` + `ScreenDetector` by `DetectorFixtureReportTest` and
`UiHierarchyFixtureTest` - detector iteration without a device.

- Capture and promote new ones with `scripts/detector-capture/` (see its README).
- Naming: `<app>-<surface>.xml`, e.g. `tiktok-fyp.xml`, `instagram-reels.xml`,
  `youtube-shorts.xml`, `facebook-feed.xml`. Keep one file per (app, surface).
- `synthetic-*.xml` are hand-authored, not device captures - they keep the harness testable
  when no real capture is checked in. Do not treat them as evidence of real app structure.
- These are committed; the raw dumps under `scripts/detector-capture/out/` are not.

A capture may contain user-visible text in `text` / `content-desc` (a username, a caption).
Trim anything personal before committing - the detectors only need structural labels.
