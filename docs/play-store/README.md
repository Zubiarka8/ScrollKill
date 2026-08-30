# Google Play submission pack

Draft paperwork for the first Play Store submission of Scroll Kill. Everything here is
a **draft for review**, not a filed declaration. Prepared in Session 12 (checklist E1,
E2, E6).

Package: `com.ikasle.scrollkill` &nbsp;|&nbsp; minSdk 24 &nbsp;|&nbsp; targetSdk 37
&nbsp;|&nbsp; versionName `1.0.0` / versionCode `1`

## Contents

| File | Covers | Status |
| --- | --- | --- |
| [`accessibility-declaration.md`](accessibility-declaration.md) | E1 - Play Console "Use of the AccessibilityService API" permissions declaration + demo-video script | Draft, needs a recorded video |
| [`data-safety.md`](data-safety.md) | E2 - Data safety form, section by section | Draft, ready to transcribe into Console |
| [`privacy-policy.md`](privacy-policy.md) | E6 - the privacy policy text that needs a public URL | Draft, needs hosting |
| [`store-listing.md`](store-listing.md) | E6 - title, descriptions, graphics spec, category, asset checklist | Draft copy + asset list |

## Blocking gaps before submission

1. **Signing key** - no release keystore yet (checklist E3). `keystore.properties`
   wiring is in place; the key still has to be generated and the release build signed.
2. **R8 not enabled** (checklist E4) - decide before shipping whether to launch with
   minify on; if so, smoke-test a minified build on a device first.
3. **Launcher icon** (checklist A5) - still the template green robot.
4. **Privacy policy URL** - `privacy-policy.md` must be published at a stable HTTPS URL
   (e.g. GitHub Pages) and that URL entered in the listing and the data-safety form.
5. **Demo video** - the AccessibilityService declaration requires a video showing the
   in-app disclosure, the consent and denial flows, and a core feature using the API.
6. **Screenshots** - none captured yet; need a device/emulator run of the real UI.

## Notes

- Scroll Kill declares **no** `<uses-permission>` at all and has no `INTERNET`
  permission, so it cannot do any network I/O. This is the backbone of the data-safety
  answers below.
- It is **not** an accessibility tool: it does not set `android:isAccessibilityTool`,
  so the prominent-disclosure + affirmative-consent requirement applies and is met by
  the first-run onboarding screen (`ui/onboarding/`, added in Session 4).
- All persistence is on-device: Room `scrollkill.db` (session history - package name,
  surface label, timestamps, durations, counts; no text) and DataStore
  `settings.preferences_pb` (toggles, package-name sets, enum names). `allowBackup=false`
  (Session 9), so none of it leaves the device.
