#!/usr/bin/env bash
# batterystats-reset.sh - ONE job: zero battery stats before the drain run (checklist 10.2
# step E.1). POSIX sibling of batterystats-reset.ps1. Retry-with-backoff for ColorOS.
#   ./scripts/profiling/batterystats-reset.sh
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"

stamp "resetting batterystats"
adb_burst shell dumpsys batterystats --reset >/dev/null
adb_burst shell dumpsys batterystats --enable full-wake-history >/dev/null

marker="$(timestamped_path batterystats-reset txt)"
printf 'batterystats --reset at %s\nUnplug USB now, start the scripted run.\n' "$(date -Iseconds)" > "$marker"
stamp "done - t=0 marker: $marker"
stamp "next: unplug USB, 30 min scripted mixed use, then batterystats-dump.sh + bugreport.sh"
