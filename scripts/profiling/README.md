# On-device battery profiling harness (checklist D3 / Session 10)

Turn-key capture scripts + DEBUG-only in-app instrumentation for the on-device battery
profiling run described in `.claude/checklist.md` section D ("Measure real callback cost")
and the Session 10 block, procedure **10.2 steps A-F**.

The static cost model (10.1) is already done and merged (PR #36). What is left is the real
device run: run 1 was **aborted** because the OPPO ColorOS CPH2791 drops the adb link after
~20-30 s (10.3). Everything here exists so a human can execute A-F the moment a stable adb
session exists.

Nothing here runs a device by itself. Read this file top to bottom before the run.

---

## What got added to the app (all DEBUG-only, all marked `// HAY QUE ELIMINAR (D3 profiling harness)`)

R8 is off, so these compile into the release APK but are unreachable there (every call site
is behind `BuildConfig.DEBUG`). Same situation as the Session 10 debug card (10.4). Delete
on the same pass.

| Where | What | Serves |
|---|---|---|
| `ScrollKillApp.onCreate()` | `StrictMode` thread + VM policy, `penaltyLog` only (no `penaltyDeath`) | step D - surfaces any disk I/O on the callback thread |
| `ScrollKillAccessibilityService` field `profiling` | `ProfilingCounters?` - non-null only in DEBUG | steps A, B, D |
| `onAccessibilityEvent` top, before `EventFilter` | `profiling?.onRawEvent()` | step A - pre-debounce volume |
| `onAccessibilityEvent`, after the filter passes | `profiling?.onPassedFilter()` | step A - post-debounce volume |
| around `rootInActiveWindow` | `profiling?.onRootRead()` | step B |
| root wrapped in `CountingNodeView` (DEBUG only) | counts `getChild` IPC attempts | step B |
| around `SnapshotExtractor.extract` | `System.nanoTime()` bracket -> `profiling?.onExtract(...)` | step B |
| end of `onAccessibilityEvent` | `profiling?.maybeFlush(now)` -> one `~1 Hz` log line | steps A, B |
| inside the session-record coroutine | `profile sessions INSERT <pkg>/<surface>` log line | step D - confirms one INSERT per completed session, not per event |
| `service/ProfilingCounters.kt` | whole file: counters + `CountingNodeView` | - |

`SnapshotExtractor.kt` is deliberately **not** touched (a hook there tipped it over the
detekt complexity ceiling); the `CountingNodeView` decorator does the counting instead.

### The 1 Hz log line

Tag `ScrollKillA11y`, emitted on the first handled event after each 1 s boundary:

```
profile window=<ms> raw=<n> passed=<n> rootReads=<n> getChild=<n> extractN=<n> extractTotalUs=<n> extractMaxUs=<n>
```

Derive:

| Metric | Formula |
|---|---|
| raw events/s | `raw / (window/1000)` |
| passed events/s | `passed / (window/1000)` |
| debounce drop ratio | `1 - passed/raw` |
| getChild calls/event | `getChild / extractN` |
| `extract` mean ms/event | `extractTotalUs / extractN / 1000` |
| `extract` max ms/event | `extractMaxUs / 1000` |

---

## Prerequisites (10.2 Prereqs)

- **Physical device**, not an emulator (emulator a11y event volume is unrepresentative).
- **Instagram + TikTok** installed and logged in.
- Debug build installed: `./gradlew installDebug` (the `debugLog` / `profile` lines are only
  in a `debug` build).
- Accessibility service enabled for ScrollKill.
- `interveneEnabled` **ON** in the app.
- At least **one watched app with a daily limit set** and **one without**, to exercise both
  `BlockingEngine.decide` branches.
- `adb` on PATH. PowerShell 7 (`pwsh`) or Windows PowerShell 5.1 for the `.ps1` scripts;
  bash 4+ for the `.sh` siblings.
- Battery Historian for step E (Docker image or the hosted instance).

---

## ColorOS pre-flight checklist (do ALL of this before run 2)

Run 1 died because ColorOS kills adb sessions that write system settings or inject input
(10.3). Work through every item; the first is the usual culprit.

- [ ] Developer options -> **"USB debugging (Security settings)"** ON. This needs a signed-in
      OPPO / HeyTap account on the device. This is the one that stops ColorOS from killing
      adb sessions that touch system settings / inject input.
- [ ] Developer options -> **"Stay awake"** ON; keep the screen unlocked for the whole run.
- [ ] Default **USB configuration = "File transfer" (MTP)**, not "Charging only".
- [ ] Disable **"Sleep standby optimization"** (Battery settings) and any **"permission
      monitoring"** / auto-manage for ScrollKill and for the shell.
- [ ] Put ScrollKill (and, if present, "Android Debug Bridge" / shell) on the battery
      **allow / don't-optimize** list.
- [ ] Try a **different cable** and a **direct USB-A port** (no hub).
- [ ] Confirm stability: `adb shell "while true; do date; sleep 2; done"` should run for
      several minutes without dropping before you start the real capture.

If adb still drops, the scripts here will keep retrying and appending, but `adb bugreport`
(step E) needs a few unbroken minutes - get that far first.

---

## Run order (maps to 10.2 steps A-F)

All scripts write timestamped, append-mode files to `scripts/profiling/out/` (git-ignored).
Windows examples shown; swap `powershell -File ...\x.ps1` for `./scripts/profiling/x.sh` on
bash.

### Before anything
```
powershell -File scripts\profiling\accessibility-dump.ps1 -Label before
```
Confirms the service is bound, event types, `notificationTimeout` (10.2 step A.1).

### Start the long capture (leave running in its own terminal, whole session)
```
powershell -File scripts\profiling\logcat-capture.ps1
```
Streams `-s ScrollKillA11y`, auto-reconnects on every adb drop. Ctrl+C at the very end.

### Step A - event volume + debounce effectiveness
Scroll, keeping a steady cadence: **IG Reels 60 s -> IG home feed 60 s -> TikTok FYP 60 s**.
Afterwards, in the logcat file:
- count `detected <SURFACE> in <pkg> conf=...` lines = pipeline runs that passed the 250 ms
  debounce. Expect `<= ~4/s/app`.
- read the `profile window=...` lines for `raw/s`, `passed/s`, `drop ratio`.
- if the rate sits at the ceiling for the whole run, hotspot **H1** is real.

### Step B - CPU / wall-time in the callback
Two sources:
- The `profile ...` line already gives `extract` mean/max ms and `getChild`/event with no
  profiler attached.
- For a call-tree: Android Studio CPU profiler, "Trace System Calls" (or `simpleperf`),
  attach to the service process while scrolling Reels 60 s; read inclusive time of
  `onAccessibilityEvent`, `SnapshotExtractor.extract`, `AccessibilityNodeInfo.getChild`,
  `rootInActiveWindow`.

**Flag if:** `extract` mean `> ~8-10 ms/event`, or `getChild > ~150 calls/event`.

### Step C - allocations per event
Android Studio Memory profiler, "Record allocations" (or `art` allocation tracking), 15 s of
steady Reels scroll. Group by call site; record bytes + object count/event for
`SnapshotExtractor`, `AccessibilityNodeView`, `NodeAtDepth`, `String`, and the detector
(`Score`, `Pair`, `listOf`). Note gen-0 GC count during the window.

**Flag if:** GCs fire more than a few times/min while scrolling, or `> ~50 KB/event`.

> Caveat: the DEBUG `CountingNodeView` adds one wrapper alloc per visited node on top of
> `AccessibilityNodeView`. Either subtract that (`= getChild`/event bytes of one wrapper) or
> take step C from a build with this harness removed.

### Step D - DB write frequency
- `adb shell "setprop log.tag.SQLiteStatements VERBOSE"` (optional; noisy).
- 10 min mixed use. In the logcat file, count `profile sessions INSERT ...` lines vs the
  number of sessions you actually did - must be 1:1, never per event.
- Check logcat (tag `StrictMode`) for any `StrictMode policy violation` with a disk-write on
  the callback thread - there should be none (`record()` runs on `Dispatchers.IO`).
  StrictMode is already enabled for debug builds by this harness.

### Step E - battery drain
```
powershell -File scripts\profiling\batterystats-reset.ps1
```
Unplug USB. Do **30 min scripted mixed use** (10 min each: IG feed / IG Reels / TikTok FYP).
Re-plug, then:
```
powershell -File scripts\profiling\accessibility-dump.ps1 -Label after
powershell -File scripts\profiling\batterystats-dump.ps1 -Label after-30min
powershell -File scripts\profiling\bugreport.ps1
```
Load the `bugreport-*.zip` into Battery Historian. Record: ScrollKill mAh, % of total, CPU
time fg/bg, wakelock count/duration (expect ~none), "computed drain" for the package.
Repeat the 30 min run once more with the accessibility service **disabled** as a control.

### Step F - record + decide
Paste the numbers into the **10.3 Results** block in `.claude/checklist.md` (template below).
Then, only if warranted (step A at sustained ceiling rate AND B/C show the BFS dominating):
implement **H1** (surface-change short-circuit) on `feature/callback-short-circuit` and
re-run A-C to quantify the win. Fallback low-risk levers: `EventFilter` debounce 250 -> 350-500
ms, `notificationTimeout` 100 -> 200-300. File follow-ups as Phase 6 backlog.

---

## Script inventory

| Script (`.ps1` + `.sh`) | ONE job |
|---|---|
| `lib.ps1` / `lib.sh` | shared helpers (dot-source only): device wait, adb retry-with-backoff, timestamped append files |
| `accessibility-dump` | one `dumpsys accessibility` snapshot, `-Label before` / `-Label after` |
| `logcat-capture` | stream `-s ScrollKillA11y` for the whole run, auto-reconnect on adb drop, append to one file |
| `batterystats-reset` | `dumpsys batterystats --reset` + enable full wake history, write a t=0 marker |
| `batterystats-dump` | `dumpsys batterystats` (full + app-scoped) to timestamped files |
| `bugreport` | `adb bugreport` zip for Battery Historian, retried whole (bugreport cannot resume) |

Every script: one job, short idempotent adb bursts, retry-with-backoff, resumable/append
output, timestamped filenames - so a ColorOS adb drop mid-run never loses what is already on
disk.

---

## Flag thresholds (from 10.2)

| Signal | Flag when |
|---|---|
| `SnapshotExtractor.extract` mean | `> ~8-10 ms/event` |
| `getChild` calls | `> ~150 calls/event` |
| allocations | `> ~50 KB/event` |
| gen-0 GC | more than a few times/min while scrolling |
| `detected`/s | sustained at the `~4/s/app` ceiling for a whole run -> H1 is real |
| wakelocks (batterystats) | anything more than ~none |

---

## 10.3 Results template (paste into `.claude/checklist.md`, fill in)

```
##### 10.3 Results

- <date> run 2 - Device: <make/model>, ColorOS/Android <ver> / SDK <n>. App: <versionName> (<versionCode>), debug.
  IG <version>, TikTok <version>. adb stable for <mins> min (pre-flight checklist applied: <which items>).

  A. Event volume + debounce
     IG Reels 60s:  raw <n>/s, passed <n>/s, drop <n>%, detected <n>/s
     IG feed 60s:   raw <n>/s, passed <n>/s, drop <n>%, detected <n>/s
     TikTok FYP 60s: raw <n>/s, passed <n>/s, drop <n>%, detected <n>/s
     -> ceiling hit? <yes/no>  H1 implicated? <yes/no>

  B. Callback CPU / wall-time (Reels 60s)
     onAccessibilityEvent inclusive: <n> ms/event
     SnapshotExtractor.extract:      mean <n> ms, max <n> ms   [flag if mean > 8-10]
     getChild:                       <n> calls/event           [flag if > 150]
     rootInActiveWindow:             <n> ms/event

  C. Allocations (Reels 15s)
     SnapshotExtractor + NodeAtDepth + AccessibilityNodeView: <n> KB/event, <n> objects/event
     String: <n> KB/event   detector (Score/Pair/listOf): <n> KB/event
     gen-0 GC: <n>/min      [flag if > a few/min or > 50 KB/event]

  D. DB writes (10 min mixed)
     sessions observed: <n>   "profile sessions INSERT" lines: <n>   (must match)
     StrictMode disk-write violations on callback thread: <n>   (expect 0)

  E. Battery drain (30 min scripted, vs 30 min control with service off)
     ScrollKill: <n> mAh, <n>% of total, CPU fg <n>s / bg <n>s
     wakelocks: <count> / <duration>   (expect ~none)
     computed drain: <n>   control: <n>   delta: <n>

  F. Decision: <no change | debounce bump to <n> ms | notificationTimeout bump | implement H1 on feature/callback-short-circuit>
     Follow-ups filed: <list / none>
```
