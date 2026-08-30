# Detector signal-token re-verification

Repeatable process for checking every `AppDetector`'s signal tokens against a **current**
third-party app build, and for spotting drift. Covers checklist B5
(`InstagramDetector`, `FacebookDetector`, `TikTokDetector`, `YouTubeShortsDetector` -
"expected to drift" TODOs) and the "TikTok daily limit never blocks" symptom.

Detectors are pure and framework-free; their token lists are the only part that rots when
Instagram / TikTok / YouTube / Facebook ship a new UI. This doc is how a human with a device
re-confirms or repairs them. **You need a physical device** (emulators do not run the real
apps and their a11y event volume is unrepresentative).

---

## 1. How detection scores a surface

All four detectors use the same additive model (`InstagramDetector.kt`,
`FacebookDetector.kt`, `TikTokDetector.kt`, `YouTubeShortsDetector.kt`):

| Signal group | Weight | Fires when |
|---|---|---|
| `PACKAGE_NAME` | `0.10` | always (foreground package is the target) |
| `VIEW_ID` | `0.45` | any observed `viewIdResourceName` **contains** any token in the group |
| `CLASS_NAME` | `0.25` | any observed `className` **contains** any token in the group |
| `CONTENT_DESCRIPTION` | `0.25` | any observed `contentDescription` **contains** any token in the group |

- `MATCH_THRESHOLD = 0.60`. Below it the detector returns `DetectionResult.none(...)`.
- Match test is **case-insensitive substring** (`SignalMatching.kt#containsAnyToken`), so a
  token is a fragment of the real value, not the whole value.
- A group contributes its weight **once**, no matter how many of its tokens match.
- `confidence` is clamped to `1.0`.

Reachable totals (package is always in):

| Groups that fire | Confidence | Match? |
|---|---|---|
| none | `0.10` | no |
| `CLASS` only, or `CONTENT_DESC` only | `0.35` | no |
| `VIEW_ID` only | `0.55` | no |
| `CLASS` + `CONTENT_DESC` | `0.60` | **yes, exactly at threshold (fragile)** |
| `VIEW_ID` + one other | `0.80` | yes |
| all three | `1.00` (clamped) | yes |

So a healthy detector needs **`VIEW_ID` + one more**, or the fragile `CLASS` + `CONTENT_DESC`
pair. If `VIEW_ID` has drifted, the detector is one token change away from silence.

### Multi-surface detectors

- **Instagram** scores `SHORT_VIDEO` (Reels), `EXPLORE`, `FEED` from the same snapshot;
  highest confidence wins, ties resolve most-specific-first (Reels > Explore > Feed).
- **Facebook** scores `FEED` and `SHORT_VIDEO`; a tie goes to `SHORT_VIDEO`.
- **TikTok** and **YouTube Shorts** score a single `SHORT_VIDEO` surface.

---

## 2. Two structural gaps to keep in mind while reading evidence

These are code-level, not build drift; they change how you interpret a capture.

- **`text` is never checked.** `SnapshotExtractor` collects node `text`,
  `ScreenSnapshot.texts` carries it, `DetectionResult.Signal.TEXT` exists - but **no detector
  reads `snapshot.texts`**. Every `CONTENT_DESCRIPTION` token that is really a visible label
  ("For You", "Following", "Shorts", "Subscriptions", "What's on your mind", "Explore",
  "Trending", "Reels") only matches if the app *also* sets it as a `contentDescription`. If it
  is `android:text` only, the token silently never fires. The debug card now dumps `texts`
  separately (see section 3) precisely so you can tell "token drifted" from "token is checked
  against the wrong bucket".
- **`className` carries the View class, not the Fragment.** `AccessibilityNodeInfo.className`
  is the underlying `View`'s class (or an app-set override), almost always
  `android.widget.*` / `android.view.*` / `androidx.*`. Tokens like `ClipsViewerFragment`,
  `FeedRecommendFragment`, `ReelWatchFragment`, `NewsFeedFragment`, `VideoViewHolder` are
  Fragment / ViewHolder names and will essentially never appear there. On Instagram / TikTok /
  YouTube the app code is R8-obfuscated as well, so even custom `View` names are mangled.
  Treat every `*Fragment` / `*ViewHolder` `CLASS_NAME` token as **probably dead** until a
  capture proves otherwise.
- **`flagIncludeNotImportantViews` is not set** (`accessibility_service_config.xml` has only
  `flagReportViewIds`). Feed apps mark many custom feed views "not important for
  accessibility"; those nodes never enter the snapshot, so any `VIEW_ID` / `CLASS_NAME` token
  living on them cannot match. Turning the flag on would help detection but raises node
  volume and callback cost - a battery tradeoff, not a free fix.

---

## 3. The capture tool: the DEBUG debug card

`ui/settings/DebugDetectionPanel.kt` - a **DEBUG-build-only** card at the bottom of Settings,
polled 1 Hz from the running `ScrollKillAccessibilityService`. Release builds pass
`debugPanel = null` (guarded by `BuildConfig.DEBUG` in `MainActivity`), so nothing ships.
All of it is marked `// HAY QUE ELIMINAR (Session 10 battery profiling)` /
`// HAY QUE ELIMINAR (Session 13 detector token verify)` and is deleted per checklist 10.4
once drift is fixed.

Build and install a debug build, enable the accessibility service, then the card shows:

```
foreground : <last evaluated package>
last match : <surface> | none   (conf 0.NN)
signals    : PACKAGE_NAME,VIEW_ID,...
decision   : None | Intervene(SURFACE)
<app label> : <metered usedMs> / <budget or "no limit">      (one line per watched app)

--- last snapshot tokens (<package>) ---
viewIds (N):
  <every viewIdResourceName seen, verbatim, sorted, <=80>
classNames (N):
  <every className seen, verbatim, sorted, <=80>
contentDescriptions (N, digit-free <=40ch):
  <short digit-free contentDescription labels, distinct, <=40>
texts (N, digit-free <=40ch):
  <short digit-free text labels, distinct, <=40>
```

`viewIds` / `classNames` are pure structure and printed verbatim. `contentDescriptions` and
`texts` can carry user content, so both are filtered to short (<=40 char), digit-free,
distinct entries - structural labels like "For You" survive, captions / comments / counts do
not. "Copy diagnostics" copies the whole block as plain text.

> If a capture needs a value the filter drops (a long or digit-bearing label you know is a
> real detector cue), raise `MAX_DESC_LEN` / relax the digit filter in
> `DebugTokens.Companion` **on the branch only**, capture, then revert. Do not ship a looser
> filter.

---

## 4. Per-app / per-surface capture protocol

### 4.0 Session setup (once)

1. `./gradlew installDebug` a build off the re-check branch.
2. Enable "ScrollKill detection" in system Accessibility settings; `interveneEnabled` ON.
3. In ScrollKill Settings: set a **short daily limit (e.g. 2 min)** on TikTok, Instagram,
   YouTube and Facebook, and leave one watched app with **no limit**, so both
   `BlockingEngine.decide` branches are exercised.
4. Record, for each target app, the **installed version** (system Settings > Apps > *app* >
   scroll to bottom) and the **device language**. Every `CONTENT_DESCRIPTION` / `text` token
   is hard-coded English; if your user base is es / eu, also run this whole protocol on a
   device set to Spanish or Basque.

### 4.1 One surface

For each surface in the table below:

1. Open the app, navigate to the surface, and **scroll it 3-5 times** at a steady cadence
   (scrolling is what forces `TYPE_WINDOW_CONTENT_CHANGED` events; a still screen produces
   almost none).
2. Without closing the app from recents, switch to **ScrollKill > Settings** and scroll to
   the debug card.
3. Read `foreground` - it must equal the surface's package. If not, you captured the wrong
   screen; go back and scroll the app again.
4. Watch the card for ~5 seconds (5 poll cycles). Note whether `last match` / `conf` is
   **stable** or **flickering**.
5. Tap **Copy diagnostics**, paste into the capture log with a heading
   `APP / SURFACE / app version / device lang / date`.

### 4.2 Surfaces to visit

| App | Package | Surface to open | Expected `last match` |
|---|---|---|---|
| Instagram | `com.instagram.android` | Reels tab, scroll 3-5 reels | `SHORT_VIDEO` |
| Instagram | `com.instagram.android` | Home tab, scroll 3-5 posts | `FEED` |
| Instagram (optional) | `com.instagram.android` | Search / Explore grid, scroll | `EXPLORE` |
| TikTok | `com.zhiliaoapp.musically` | For You, scroll 3-5 videos | `SHORT_VIDEO` |
| YouTube | `com.google.android.youtube` | open a Short, swipe 3-5 | `SHORT_VIDEO` |
| Facebook | `com.facebook.katana` | Home feed, scroll 3-5 posts | `FEED` |
| Facebook | `com.facebook.katana` | Reels / Watch, scroll 3-5 | `SHORT_VIDEO` |

(If the device carries the regional TikTok build `com.ss.android.ugc.trill`, no detector
watches it - note that and stop; it is a separate B7-style item, not drift.)

### 4.3 Reading the capture - where drift shows up

Work through, in order:

1. **`foreground` = target but `last match : none`** -> detector is below threshold on this
   build = **drift**. Go to the reference table (section 5) for that surface and, for each of
   the three groups, check whether **any** listed token is a case-insensitive substring of
   **any** captured value *in that group's bucket*:
   - `VIEW_ID` tokens vs the `viewIds` block
   - `CLASS_NAME` tokens vs the `classNames` block
   - `CONTENT_DESCRIPTION` tokens vs the `contentDescriptions` block
   Then compute `0.10 + 0.45*(viewId fires) + 0.25*(class fires) + 0.25*(contentDesc fires)`
   and confirm it is `< 0.60`.
   - If a `CONTENT_DESCRIPTION` token instead shows up under the **`texts`** block, record it
     as **"present as text (wrong bucket)"** - that is gap B-2, not a vanished token.
2. **`last match : <wrong surface>`** (e.g. `FEED` while you are on Reels) -> a token from
   another surface's list is matching. Diff all of that detector's surface lists against the
   capture to find which token is too broad.
3. **`last match : <surface> (conf 0.60)` exactly** -> only `CLASS` + `CONTENT_DESC` fired,
   `VIEW_ID` did not. Detection works but is one token change from failing. Flag for a
   `VIEW_ID` token refresh even though it currently "passes".
4. **Flickering `last match`** (alternating match / none across poll cycles) -> confidence is
   riding the threshold; same remedy as (3), refresh `VIEW_ID`.
5. **Usage line** for a limited app reads `0m 00s / 2m 00s` after 2+ minutes of real
   scrolling **and** `last match : none` -> confirms the never-metered path (section 6).
6. **Usage line advances but far slower than wall-clock** while `last match : SHORT_VIDEO`
   holds steady -> `DailyUsageMeter` under-count (section 6, secondary).

### 4.4 Pass / fail per surface

- **PASS**: on the surface, after 3-5 scrolls, `foreground` = target package **and**
  `last match` = the expected `Surface` **and** `conf >= 0.60` on at least 3 of 5
  consecutive card reads (stable, not flickering). For a limited blockable surface, the
  usage line advances roughly in step with time on the surface (within ~30%), and leaving it
  on the surface past the limit flips `decision` to `Intervene(...)`.
- **FAIL**: `last match : none` on the surface, or the wrong surface, or `conf` pinned at
  exactly `0.60` with `VIEW_ID` not firing, or usage stuck at `0` after 2+ minutes of use.

### 4.5 On FAIL - repair

1. From the captured `viewIds` block for that surface, pick 2-4 values that are (a) clearly
   part of the scrolling feed container, (b) **specific to this surface** - absent from the
   other surfaces' captures of the same app. Use a stable substring, not the whole
   `com.pkg:id/...` string.
2. Do the same for `classNames` **only if** real (non-`android.*`/`androidx.*`) class names
   appear; otherwise leave that group and rely on `VIEW_ID` + `CONTENT_DESCRIPTION`.
3. For `CONTENT_DESCRIPTION`, use labels from the `contentDescriptions` block; if the label
   you want is only in `texts`, that is gap B-2 - do not force it into the contentDesc list,
   raise it as a detector change instead.
4. Edit the token list in the detector's `private companion object` **and** update that
   detector's unit-test synthetic fixture (`*DetectorTest.kt` - the `feedViewId` /
   `feedClass` / `feedContentDesc` style vals) so the test still exercises real-shaped
   strings. Keep the "not guaranteed to be real" comment honest.
5. Re-install, re-capture, confirm PASS.
6. Append a row to the log table (section 7).

---

## 5. Reference: every token each detector matches on

Confidence weights are identical for all four: `PACKAGE 0.10`, `VIEW_ID 0.45`,
`CLASS_NAME 0.25`, `CONTENT_DESC 0.25`, threshold `0.60`. Tokens below are **substrings**,
matched case-insensitively.

### 5.1 InstagramDetector - `com.instagram.android`

| Surface | `VIEW_ID` tokens | `CLASS_NAME` tokens | `CONTENT_DESCRIPTION` tokens |
|---|---|---|---|
| `SHORT_VIDEO` (Reels) | `clips_viewer`, `reel_feed_timeline`, `reels_viewer` | `ClipsViewerFragment`, `ReelViewerFragment` | `Reel by`, `Audio page`, `Like number` |
| `EXPLORE` | `explore_grid`, `explore_recycler_view`, `search_and_explore` | `ExploreFragment`, `ExploreGridFragment`, `DiscoverFragment` | `Search and explore`, `Explore`, `Trending` |
| `FEED` | `feed_timeline_recycler_view`, `main_feed_recycler_view`, `feed_recycler_view` | `MainFeedFragment`, `FeedTimelineFragment` | `Photo by`, `New posts`, `Your story`, `Suggested for you` |

Drift notes: the `CLASS_NAME` groups are all Fragment names (gap B-2, likely dead).
`Explore` as a `CONTENT_DESCRIPTION` token is a single word and is likely a nav-bar
**text** label -> may only live in `texts`. `Like number` / `Audio page` look like guessed
strings; verify against a real Reels capture. `clips_viewer` has been Instagram's Reels
container id for a long time - most likely still good.

### 5.2 FacebookDetector - `com.facebook.katana`, `com.facebook.lite`

| Surface | `VIEW_ID` tokens | `CLASS_NAME` tokens | `CONTENT_DESCRIPTION` tokens |
|---|---|---|---|
| `FEED` | `news_feed`, `newsfeed_recycler_view`, `feed_recycler`, `newsfeed_container` | `NewsFeedFragment`, `FeedFragment` | `What's on your mind`, `News Feed`, `Stories tray` |
| `SHORT_VIDEO` (Reels) | `reels_viewer`, `video_home_reels`, `reels_tab`, `reels_root` | `ReelsViewerFragment`, `ReelsPlayerFragment` | `Reel by`, `Reels`, `Play reel` |

Drift notes: Facebook Lite has a different UI and may need its own lists (already flagged in
the detector KDoc). `CLASS_NAME` groups are Fragment names (likely dead). `What's on your
mind` is the composer hint - almost certainly **text**, not contentDescription. `Reels` as a
one-word token is broad and could match the nav tab from the feed, mis-reporting `FEED` as
`SHORT_VIDEO`; check for over-match.

### 5.3 TikTokDetector - `com.zhiliaoapp.musically`

| Surface | `VIEW_ID` tokens | `CLASS_NAME` tokens | `CONTENT_DESCRIPTION` tokens |
|---|---|---|---|
| `SHORT_VIDEO` (For You / Following feed) | `feed_recycler_view`, `video_feed`, `aweme_feed`, `detail_feed` | `FeedRecommendFragment`, `MainFragment`, `FeedFragment`, `VideoViewHolder` | `For You`, `Following`, `Like number`, `Speed dial` |

**Most drift-prone detector - and the "never blocks" suspect. See section 6.**

- `VIEW_ID`: TikTok is aggressively obfuscated and much of the FYP is custom / non-standard
  rendered; stable `viewIdResourceName` values are scarce. These four are old guesses.
- `CLASS_NAME`: `FeedRecommendFragment` / `MainFragment` / `FeedFragment` are Fragment names,
  `VideoViewHolder` is a ViewHolder - none are `View` classes (gap B-2). This group is
  effectively **dead**.
- `CONTENT_DESCRIPTION`: `For You` / `Following` are the top-tab labels - almost certainly
  `android:text`, so they land in `snapshot.texts`, which no detector reads. They are also
  localized (`Para ti` / `Siguiendo` on a Spanish device). `Like number` is not a literal
  a11y string. `Speed dial` looks hallucinated (dialer term). This group is **probably
  dead** too.

With `CLASS_NAME` and `CONTENT_DESCRIPTION` both dead, the only route to `0.60` is
`VIEW_ID` + one of them = impossible. Max reachable confidence is `0.10` (package only) or
`0.55` (`VIEW_ID` alone) - both `< 0.60` -> `DetectionResult.none` on every event.

### 5.4 YouTubeShortsDetector - `com.google.android.youtube`

| Surface | `VIEW_ID` tokens | `CLASS_NAME` tokens | `CONTENT_DESCRIPTION` tokens |
|---|---|---|---|
| `SHORT_VIDEO` (Shorts player) | `reel_recycler`, `reel_player_page`, `reel_watch_pager`, `shorts_container` | `ReelWatchFragment`, `ShortsPlayerFragment` | `Shorts player`, `Short number`, `Shorts feed` |

Drift notes: Shorts is "Reel" internally at YouTube, hence the `reel_*` ids; YouTube reworks
Shorts internals often, so these need a fresh capture. `CLASS_NAME` group is Fragment names
(likely dead). `Short number` / `Shorts feed` look like placeholders; `Shorts player` is
plausible as a real `contentDescription` on the player container - verify.

### 5.5 Drift-prone ranking (all detectors)

1. **All `*Fragment` / `*ViewHolder` `CLASS_NAME` tokens** (every detector). Wrong kind of
   string for `AccessibilityNodeInfo.className`; near-certainly never fire.
2. **TikTok `VIEW_ID` tokens** - obfuscated app, custom-rendered feed.
3. **TikTok `CONTENT_DESCRIPTION` tokens** - `Speed dial` / `Like number` likely bogus;
   `For You` / `Following` are text, not contentDescription, and localized.
4. **YouTube `reel_*` `VIEW_ID` tokens** - Shorts internals churn.
5. **Every English `CONTENT_DESCRIPTION` / would-be `text` token on a non-English device** -
   all tokens are hard English; a localized device fails all of them.
6. Least drift-prone: package names; Instagram `clips_viewer` and the
   `*feed*_recycler_view` family.

---

## 6. TikTok FEED_* "daily limit never blocks" - code-level analysis

**Do not blind-fix. This section is reasoning for the human holding the device.**

### 6.1 The chain

`onAccessibilityEvent` -> `SnapshotExtractor.extract` -> `ScreenDetector.detect` ->
`TikTokDetector.detect`.

If `TikTokDetector` cannot reach `0.60`, it returns `DetectionResult.none(...)`, so
`result.isMatch == false`. Then in `BlockingEngine.decide`:

```
onWatchedSurface = result.isMatch && surface in blockableSurfaces && confidence >= minConfidence
```

`onWatchedSurface` is `false` ->
- `usageMeter.record(pkg, nowMs)` is **never called** ->
- `DailyUsageMeter.usedMs("com.zhiliaoapp.musically", now)` stays `0` ->
- budget gate `budgetMs != null && usedMs < budgetMs` is `0 < 300_000` = always true ->
- `decide` returns `BlockingDecision.None` on every event.

`SessionTracker.track` sees `engaged == false` too, so no `Session` is ever emitted, nothing
is persisted, and the next-boot `seedUsage` also reads `0`. **The 5-minute TikTok limit can
never be reached because nothing is ever metered.** This reproduces the reported symptom
exactly.

### 6.2 Why `TikTokDetector` likely never reaches 0.60 on the current build

To hit `0.60` it needs `PACKAGE (0.10)` + `VIEW_ID (0.45)` + one of {`CLASS` `0.25`,
`CONTENT_DESC` `0.25`}, or the `CLASS` + `CONTENT_DESC` pair (`0.60` exactly).

- `CLASS_NAME` group: `FeedRecommendFragment`, `MainFragment`, `FeedFragment`,
  `VideoViewHolder` - Fragment / ViewHolder names, not `View` classes. Against
  `AccessibilityNodeInfo.className` (which is `android.widget.*` / obfuscated custom views)
  this **never fires**.
- `CONTENT_DESCRIPTION` group: `For You` / `Following` are tab **text** (and localized),
  `Like number` / `Speed dial` are not real a11y strings. Against
  `snapshot.contentDescriptions` this **almost never fires**.
- `VIEW_ID` group: obfuscated, largely custom-rendered FYP -> `feed_recycler_view` etc.
  likely **absent**.

With `CLASS` and `CONTENT_DESC` dead, `VIEW_ID + one` is unreachable; confidence tops out at
`0.55`. Result: `none` on every event -> never metered -> never blocks.

### 6.3 Secondary (only bites once detection fires): `DailyUsageMeter` under-count

`DailyUsageMeter.record` credits `(nowMs - lastEventMs).coerceIn(0, maxGapMs)` where
`maxGapMs = 30_000` (30 s, hardcoded, not settings-driven). Passive TikTok playback (watching
without scrolling) emits very few `TYPE_WINDOW_CONTENT_CHANGED` events - the video is a
`SurfaceView` / `TextureView`, not a11y-eventful - so between scrolls `lastEventMs` does not
advance, and any real gap over 30 s is clamped to 30 s. Five real minutes of passive watching
with a scroll every ~45 s credits ~30 s per scroll ≈ 3.3 min counted, ~33% under. Contributes
to "limit takes too long", but is **not** the cause of "never blocks" - that is 6.1 / 6.2.

### 6.4 Bugs found (report, leave the fix to the human)

- **B-1 (design bug, not a one-liner).** Every `*Fragment` / `*ViewHolder` `CLASS_NAME`
  token in all four detectors is matched against `AccessibilityNodeInfo.className`, which is
  the `View` class, not the Fragment / ViewHolder. These groups are near-dead weight.
  Repair needs real captured `classNames` per surface - device work, out of scope for a
  speculative edit.
- **B-2 (design gap).** No detector reads `snapshot.texts`, though it is extracted and
  `DetectionResult.Signal.TEXT` is defined. Tokens that are visible labels (`For You`,
  `Following`, `Shorts`, `What's on your mind`, `Explore`, `Trending`) are likely text-only.
  Fixing this is a behaviour change (add a `texts` check / weight to the detectors) with its
  own tests - a separate task, not part of this re-check.
- **B-3 (config).** `flagIncludeNotImportantViews` is not set, so feed nodes marked
  not-important-for-a11y never reach the snapshot and their `VIEW_ID` / `CLASS_NAME` tokens
  cannot match. Enabling it helps detection but costs battery (more nodes per event) -
  measure before changing.
- **Not a bug:** `CLASS` + `CONTENT_DESC` = `0.60` passing `>= MATCH_THRESHOLD` is intended
  (needs two independent cues, which it has).

No zero-risk one-line fix is available. **The TikTok FEED_* token lists must not be changed
without captured evidence** from section 4.

---

## 7. Log

Append one row per surface per re-check run.

| Date | Device / OS | App + version | Device lang | Surface | Result | Conf | Action taken |
|---|---|---|---|---|---|---|---|
| _2026-08-30_ | _(pending - no device this session)_ | - | - | - | - | - | doc + `texts` added to debug card |

## 8. Cadence

Run the whole protocol when:
- any target app ships a major version bump, or
- a user reports "ScrollKill stopped nudging me off app X", or
- quarterly, as routine maintenance.

Keep the debug-card changes on a branch; delete the card (checklist 10.4) once the detectors
pass again.
