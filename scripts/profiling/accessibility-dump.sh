#!/usr/bin/env bash
# accessibility-dump.sh - ONE job: snapshot `dumpsys accessibility` (checklist 10.2 step A.1,
# before/after). POSIX sibling of accessibility-dump.ps1. Single burst + retry.
#   ./scripts/profiling/accessibility-dump.sh before
#   ./scripts/profiling/accessibility-dump.sh after
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"

label="${1:-}"
suffix=""; [ -n "$label" ] && suffix="-$label"
path="$(timestamped_path "accessibility${suffix}" txt)"

add_adb_capture "$path" "$label" shell dumpsys accessibility

stamp "wrote $path"
stamp "check: 'Bound services' lists ScrollKill; eventTypes = WINDOW_STATE/CONTENT_CHANGED; notificationTimeout"
