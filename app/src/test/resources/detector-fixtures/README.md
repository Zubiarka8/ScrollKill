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

Checked in (all from OPPO Find X9 Pro / ColorOS 16, device language Spanish, 2026-09-01):
`tiktok-fyp.xml`, `instagram-feed.xml`, `instagram-reels.xml`, `instagram-explore.xml`,
`youtube-shorts.xml`. Every one currently reports `BELOW 0.60` - see
`docs/maintenance/detector-token-recheck.md` section 7 for why (depth cap B-4 + drifted
`VIEW_ID` lists + English-only tokens). Usernames, captions and channel names were replaced
with `Test User` / `testuser` / `Sample caption`; the structural labels the detectors read
are untouched. Facebook not captured yet.

A capture may contain user-visible text in `text` / `content-desc` (a username, a caption).
Trim anything personal before committing - the detectors only need structural labels.
