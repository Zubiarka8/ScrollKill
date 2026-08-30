#!/usr/bin/env bash
# batterystats-dump.sh - ONE job: capture human-readable battery stats after a drain run
# (checklist 10.2 step E.2). POSIX sibling of batterystats-dump.ps1. Appends two timestamped
# files under scripts/profiling/out/. Run bugreport.sh right after for the Historian view.
#   ./scripts/profiling/batterystats-dump.sh [label]
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"

label="${1:-}"
full="$(timestamped_path batterystats-full txt)"
app="$(timestamped_path batterystats-scrollkill txt)"

add_adb_capture "$full" "$label" shell dumpsys batterystats
add_adb_capture "$app"  "$label" shell dumpsys batterystats "$SCROLLKILL_PACKAGE"

stamp "wrote $full"
stamp "wrote $app"
stamp "read: ScrollKill mAh, % of total, CPU time fg/bg, wakelock count/duration (expect ~none)"
