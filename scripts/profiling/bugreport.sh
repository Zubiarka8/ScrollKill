#!/usr/bin/env bash
# bugreport.sh - ONE job: pull an `adb bugreport` zip for Battery Historian (checklist 10.2
# step E.2). POSIX sibling of bugreport.ps1. bugreport cannot resume, so the whole thing is
# retried with a long backoff. Work the README.md ColorOS pre-flight checklist first.
#   ./scripts/profiling/bugreport.sh
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"

zip="$(timestamped_path bugreport zip)"
stamp "requesting bugreport -> $zip (takes minutes; do not touch USB)"

attempt=0
until [ "$attempt" -ge 5 ]; do
  attempt=$((attempt + 1))
  wait_for_device 120 || true
  if adb bugreport "$zip"; then break; fi
  delay=$(( 10 * attempt )); [ "$delay" -gt 120 ] && delay=120
  stamp "bugreport failed (attempt ${attempt}/5); retrying in ${delay}s"
  sleep "$delay"
done

[ -f "$zip" ] || { stamp "bugreport did not produce $zip"; exit 1; }
stamp "done: $zip"
stamp "load at https://developer.android.com/topic/performance/power/battery-historian"
