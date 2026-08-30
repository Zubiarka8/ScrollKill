# E1 - "Use of the AccessibilityService API" declaration

Draft answers for the Play Console permissions declaration form. Verified against the
current policy page *"Use of the AccessibilityService API"* (Play Console Help
`answer/10964491`) and *"Permissions and APIs that Access Sensitive Information"*
(`answer/16558241`). Re-check the live pages before filing - this policy changes.

## Classification

Scroll Kill is **not an accessibility tool**. It does not help users with disabilities
operate their device; it uses the API to detect that an infinite feed is on screen in
another app so it can nudge the user to stop. Therefore:

- `android:isAccessibilityTool` is **not** set (and must not be).
- The prominent-disclosure + affirmative-consent requirement **applies** and is
  satisfied by the first-run onboarding screen (see "Prominent disclosure" below).
- The permissions declaration form and a demo video are **required** before the app
  can be published.

## Form answers

**1. Why does your app need to use the AccessibilityService API?**
Select: **App functionality** only. (Not analytics, not advertising, not personalization,
not account management, not fraud/security, not developer communications.)

**2. Which functionality relies on the AccessibilityService API?**

> Scroll Kill helps users cut back on compulsive use of infinite-scroll feeds. It uses
> the AccessibilityService API to read on-screen text and view metadata from a small,
> user-chosen set of social apps (for example Instagram, TikTok, YouTube, Facebook)
> while they are in the foreground, so it can recognise when an infinite feed - Reels,
> Shorts, the For You page, the main feed - is on screen. When a watched feed is
> detected and the user's daily budget for that app is spent, Scroll Kill performs a
> global Back action to leave the feed. Detection runs entirely on the device inside
> the accessibility callback; nothing is written to disk or sent off the device.

**3. Why is the AccessibilityService API the only way to implement this?**

> There is no other Android API that reports which screen or feed is visible inside a
> third-party app. Usage-access stats (`UsageStatsManager`) only report which package
> is in the foreground, not whether the user is on an infinite feed versus a settings
> page or a chat, so they cannot drive feed-specific nudging. `QUERY_ALL_PACKAGES`,
> media APIs, and notification access do not expose on-screen content either. The
> AccessibilityService API is the only mechanism that provides the on-screen signals
> (text, `viewIdResourceName`, class names, window state) the detectors need.

**4. Do you collect and/or share personal or sensitive user data using the accessibility
capabilities?**
**No.**

- Screen content read through the API lives only as local variables inside the
  `onAccessibilityEvent` callback and the pure detector functions. It is used to
  compute a `DetectionResult` (surface enum + confidence + which signal types matched)
  and then discarded.
- No screen text, no `AccessibilityNodeInfo`, and no screenshot is ever persisted or
  logged (release logging is gated off - Session 9 audit).
- The app has no `INTERNET` permission and makes no network calls, so nothing can be
  transmitted.
- What is stored locally is session history (package name, surface label, timestamps,
  durations, detection/intervention counts) and settings (toggles, package-name sets,
  enum names) - none of it derived from screen text, none of it leaving the device
  (`allowBackup="false"`).

**5. Data types** - N/A (answered "No" to #4).

**6. Target audience / accessibility-tool claim** - Scroll Kill is a general
productivity/digital-wellbeing app, not an accessibility tool; do not claim the
`isAccessibilityTool` exemption.

## Prominent disclosure (in-app)

Shown by `OnboardingScreen` on first launch, before the system accessibility dialog,
in the normal app flow (not behind a menu). Current copy (`res/values/strings.xml`):

- **Title** - "Before you turn on detection"
- **Intro** - "Scroll Kill uses Android's accessibility service to notice when an
  infinite feed opens in another app, so it can nudge you to stop scrolling."
- **What it reads** - "While the service is on, Scroll Kill reads on-screen text and
  layout from the social apps you choose to watch. It uses this only on your device,
  only to tell whether a feed is on screen."
- **What it does not do** - "Screen contents are never saved and never sent anywhere.
  No account, no cloud, no analytics. You can turn the service off at any time in
  Android Settings."
- **Consent note** - "Choosing 'Enable detection' opens Android's Accessibility
  settings so you can turn Scroll Kill on."
- **Buttons** - "Enable detection" (affirmative consent, recorded to
  `onboardingComplete`) / "Not now".

Checklist before filing: confirm this wording still matches the shipped build and that
"Enable detection" is a clear affirmative action distinct from a plain "OK".

## Demo video script (required)

Record a single continuous screen capture:

1. Fresh install. Launch Scroll Kill from the launcher.
2. The onboarding / disclosure screen appears immediately. Scroll through it slowly so
   the full text is readable, including "What it reads" and "What it does not do".
3. Tap **Not now** once to show the denial path returns to the app without enabling the
   service. Re-open onboarding.
4. Tap **Enable detection**. Show that it opens Android's Accessibility settings and the
   user enabling "Scroll Kill detection" there (the system's own consent step).
5. Back in Scroll Kill, set a short daily limit for one app (e.g. Instagram, 1 minute)
   in Settings.
6. Open that app, scroll the feed until the limit is hit, and show Scroll Kill nudging
   the user out of the feed (the Back action) - the core feature that uses the API.
7. Keep it under ~2 minutes; no cuts across the disclosure and consent portion.
