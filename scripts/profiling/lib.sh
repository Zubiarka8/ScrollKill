#!/usr/bin/env bash
# lib.sh - shared helpers for the ScrollKill on-device battery profiling scripts (POSIX/bash
# sibling of lib.ps1). Source it, do not run it:  . "$(dirname "$0")/lib.sh"
#
# Same design as lib.ps1: every adb call retries with exponential backoff after waiting for
# the device, because OPPO ColorOS drops the adb link after ~20-30 s (checklist 10.3).
# Requires: adb on PATH, bash 4+.

set -euo pipefail

SCROLLKILL_PACKAGE="com.ikasle.scrollkill"
PROFILING_OUT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/out"

stamp() { printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*"; }

timestamped_path() { # $1 prefix  $2 extension
  mkdir -p "$PROFILING_OUT_DIR"
  printf '%s/%s-%s.%s\n' "$PROFILING_OUT_DIR" "$1" "$(date +%Y%m%d-%H%M%S)" "$2"
}

wait_for_device() { # $1 timeout seconds (default 60)
  local timeout="${1:-60}" waited=0
  while [ "$waited" -lt "$timeout" ]; do
    if [ "$(adb get-state 2>/dev/null || true)" = "device" ]; then return 0; fi
    sleep 2; waited=$((waited + 2))
  done
  return 1
}

adb_burst() { # runs: adb "$@" with retry; echoes stdout on success, exits non-zero after N tries
  local max_attempts=8 base=2 attempt delay out
  for attempt in $(seq 1 "$max_attempts"); do
    wait_for_device || true
    if out="$(adb "$@" 2>&1)"; then printf '%s\n' "$out"; return 0; fi
    delay=$(( base * (2 ** (attempt - 1)) )); [ "$delay" -gt 30 ] && delay=30
    stamp "adb $* failed (attempt ${attempt}/${max_attempts}); retrying in ${delay}s" >&2
    sleep "$delay"
  done
  stamp "adb $* still failing - fix the adb link (see README.md ColorOS pre-flight) and re-run." >&2
  return 1
}

add_adb_capture() { # $1 path  $2 label  $3.. adb args
  local path="$1" label="$2"; shift 2
  printf '===== [%s] adb %s %s =====\n' "$(date -Iseconds)" "$*" "$label" >> "$path"
  adb_burst "$@" >> "$path"
  stamp "appended to $(basename "$path")"
}
