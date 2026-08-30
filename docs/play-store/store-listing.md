# E6 - Store listing

Draft listing copy + the asset checklist. Nothing here is final; the descriptions need
a pass once the UI and icon are settled.

## Text metadata

**App name** (max 30 chars)
`Scroll Kill` &nbsp; _(11)_

**Short description** (max 80 chars)
`Set daily limits for Reels, Shorts and For You feeds. All on-device, no tracking.`
_(79)_

**Full description** (max 4000 chars) - draft:

> Scroll Kill helps you stop losing hours to infinite feeds.
>
> Pick the apps you want to keep in check - Instagram, TikTok, YouTube, Facebook - and
> set a daily time budget for their endless feeds: Reels, Shorts, the For You page, the
> main feed. When you have used up a feed's budget for the day, Scroll Kill nudges you
> back out of it.
>
> Built to stay out of your way:
>
> - Private by design. Detection runs entirely on your device. Scroll Kill has no
>   internet permission, so nothing can be sent anywhere.
> - No account, no cloud, no analytics, no ads, no tracking SDKs.
> - Your usage history and settings never leave your phone, and cloud backup is off.
> - Light on battery. Scroll Kill reacts to system events instead of polling, and does
>   nothing while you are not in a watched app.
>
> How it works: with your explicit consent, Scroll Kill uses Android's accessibility
> service to recognise when an infinite feed is on screen in an app you have chosen to
> watch. It reads on-screen text in the moment to make that decision and then discards
> it - it is never stored or transmitted.
>
> You are always in control. Change which apps are watched and what the limits are at
> any time, decide how long usage history is kept, and switch the whole thing off from
> Android Settings whenever you want.

**Category:** Productivity (alternative: Health & Fitness > digital wellbeing)
**Tags:** digital wellbeing, focus, screen time, productivity
**Contact email:** [TBD - must match the privacy policy contact]
**Website:** [optional - GitHub repo or a landing page]
**Privacy policy URL:** [publish `privacy-policy.md` and put the URL here]

## Graphic assets - checklist

| Asset | Spec | Status |
| --- | --- | --- |
| App icon | 512x512 PNG, 32-bit, <1 MB | **Missing** - still the template green robot (checklist A5) |
| Feature graphic | 1024x500 PNG or JPG, no alpha | **Missing** |
| Phone screenshots | 2-8, PNG/JPG, 16:9 or 9:16, min 1080 px on the short side | **Missing** - need a device/emulator run of Home, Settings (limits + per-app), the onboarding/disclosure screen, and a nudge in progress |
| 7" tablet screenshots | optional | Not planned for v1 |
| 10" tablet screenshots | optional | Not planned for v1 |
| Promo video (YouTube URL) | optional for the listing | Separate from the mandatory accessibility **demo video**, which goes in the permissions declaration, not the listing |

## Other Console sections to fill

- **App access:** all functionality is available without special access; note that the
  core feature needs the user to enable the accessibility service, granted in-app via
  the onboarding screen. Provide no test credentials (none needed).
- **Content rating:** complete the IARC questionnaire - no user-generated content, no
  ads, no data sharing; expect "Everyone".
- **Target audience and content:** general audiences, not designed for children.
- **Ads:** declare **no ads**.
- **Government app / financial features / health:** none.
- **Data safety:** transcribe [`data-safety.md`](data-safety.md).
- **Permissions declaration:** transcribe
  [`accessibility-declaration.md`](accessibility-declaration.md) and attach the demo
  video.

## Release checklist (pre-upload)

1. Generate the release keystore; fill `keystore.properties` (see
   `keystore.properties.example`).
2. Decide on R8 (checklist E4). If enabling, smoke-test a minified build on a device.
3. Replace the launcher icon (checklist A5).
4. Bump `versionCode` if any prior internal upload used `1`.
5. Build a signed AAB (`bundleRelease`) - note the project currently only wires
   `assembleRelease`; add the bundle output when signing lands.
6. Capture screenshots from the signed build.
7. Publish the privacy policy; paste the URL into the listing and data-safety form.
8. Record the accessibility demo video.
