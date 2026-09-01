#!/usr/bin/env bash
# capture.sh - dump the on-screen view hierarchy of a social app via `uiautomator dump`,
# to seed the JVM detector fixtures (app/src/test/resources/detector-fixtures/).
#
# No ScrollKill install and no accessibility permission needed: uiautomator is part of
# Android. Works against a real device or an emulator.
#
#   1. On the device, open the app + surface you want (e.g. TikTok > For You) and scroll once.
#   2. ./scripts/detector-capture/capture.sh tiktok-fyp
#   3. Review scripts/detector-capture/out/<name>-<date>.xml, then copy it into
#      app/src/test/resources/detector-fixtures/ and run:  ./gradlew testDebugUnitTest
#      (DetectorFixtureReportTest writes app/build/reports/detector-fixtures/report.txt)
#
# adb calls retry with backoff - ColorOS drops the link after ~20-30 s (checklist 10.3);
# wireless debugging is steadier than USB there.
set -euo pipefail

name="${1:?usage: capture.sh <name>   e.g. tiktok-fyp / instagram-reels / youtube-shorts}"
here="$(cd "$(dirname "$0")" && pwd)"
out_dir="$here/out"
mkdir -p "$out_dir"

stamp() { printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*"; }

adb_burst() { # adb args...; retries, echoes stdout on success
  local tries=6 n out
  for n in $(seq 1 "$tries"); do
    adb wait-for-device >/dev/null 2>&1 || true
    if out="$(adb "$@" 2>&1)"; then printf '%s\n' "$out"; return 0; fi
    stamp "adb $* failed ($n/$tries); retry in $((n * 3))s" >&2
    sleep "$((n * 3))"
  done
  stamp "adb $* keeps failing - fix the adb link first (try wireless debugging on ColorOS)" >&2
  return 1
}

device_tmp="/sdcard/scrollkill-uidump.xml"
out_file="$out_dir/${name}-$(date +%Y%m%d-%H%M%S).xml"

stamp "dumping current window hierarchy..."
adb_burst shell uiautomator dump "$device_tmp" >/dev/null
adb_burst pull "$device_tmp" "$out_file" >/dev/null
adb_burst shell rm -f "$device_tmp" >/dev/null 2>&1 || true

pkg="$(grep -o 'package="[^"]*"' "$out_file" | head -1 | cut -d'"' -f2 || true)"
stamp "wrote $out_file"
stamp "focused package: ${pkg:-<unknown>}"
stamp "next: cp '$out_file' app/src/test/resources/detector-fixtures/ && ./gradlew testDebugUnitTest"
