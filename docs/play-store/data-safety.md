# E2 - Data safety form

Draft answers for the Play Console **Data safety** section. Basis: Session 9 privacy
audit + a fresh read of the manifest (no `<uses-permission>`, no `INTERNET`).

## Summary posture

Scroll Kill collects **no** data and shares **no** data. All app data is stored on the
device, is not transmitted off it (no network permission), and cloud backup is disabled
(`android:allowBackup="false"`).

## Section-by-section

### Data collection and sharing

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | N/A - no data is transmitted (app has no network access) |
| Do you provide a way for users to request that their data is deleted? | Data never leaves the device; uninstalling the app, or "Clear storage" in Android app settings, removes all of it. Settings also let the user shorten history retention. |

Because the answer to "collect or share" is **No**, the form does not ask for
per-data-type detail. If the reviewer's tooling forces per-type entry, the honest
mapping is:

| Data type | Collected | Shared | Why |
| --- | --- | --- | --- |
| App activity - "App interactions" (per-app feed usage) | Not collected (stored on-device only, not sent) | No | Session history is written to a local Room DB to show usage stats and enforce daily limits. It is never sent anywhere. |
| Everything else (location, personal info, financial, health, messages, photos, contacts, calendar, files, web history, identifiers, contacts, audio) | No | No | Not accessed. |

> Google's definition: data kept only on the device and never sent off it is **not
> "collected"** for the purposes of this form. Scroll Kill's session history and
> settings fall under that definition.

### Accessibility data specifically

On-screen text and view metadata read via the AccessibilityService API are processed
transiently in memory to detect feeds and are then discarded. They are not stored, not
logged in release builds, and cannot be transmitted. This matches the "No" answers in
[`accessibility-declaration.md`](accessibility-declaration.md) question 4.

### Security practices

| Question | Answer |
| --- | --- |
| Is data encrypted in transit? | N/A (no transmission) |
| Can users request data deletion? | Yes - uninstall or "Clear storage"; no server-side data exists |
| Committed to Play Families policy? | Decide with target audience; not a kids' app |
| Independent security review? | No |

### Data types actually stored on-device (for internal reference, not a form field)

- **Room `scrollkill.db`**, table `sessions`: `packageName`, surface label (enum name),
  `startedAtEpochMs`, `endedAtEpochMs`, `durationMs`, `detectionCount`,
  `interventionCount`. No free text, no screen content.
- **DataStore `settings.preferences_pb`**: intervene on/off, per-app nudge/watch
  exclusion sets (package names), default + per-app daily limit tokens, detection
  policy enum names, stats window / retention enum names, `onboarding_complete`.

Neither store is included in cloud backup or device-to-device transfer.
