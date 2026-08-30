#!/usr/bin/env bash
# logcat-capture.sh - ONE job: stream `adb logcat -s ScrollKillA11y` to a file for the whole
# run and auto-reconnect whenever ColorOS kills adb (checklist 10.3). POSIX sibling of
# logcat-capture.ps1 - see that file's header for the full list of lines to count afterwards
# (detected / profile window= / profile sessions INSERT). Ctrl+C to stop.
#   ./scripts/profiling/logcat-capture.sh
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"

log_file="$(timestamped_path logcat-ScrollKillA11y log)"
stamp "streaming -s ScrollKillA11y to $log_file (Ctrl+C to stop)"
adb_burst logcat -c >/dev/null || true

while true; do
  if ! wait_for_device 120; then
    printf '===== [%s] device not back after 120s, still waiting =====\n' "$(date -Iseconds)" >> "$log_file"
    continue
  fi
  printf '===== [%s] (re)connected, streaming =====\n' "$(date -Iseconds)" >> "$log_file"
  stamp "connected, streaming"
  adb logcat -s ScrollKillA11y -v time >> "$log_file" || true
  printf '===== [%s] stream ended, reconnecting =====\n' "$(date -Iseconds)" >> "$log_file"
  stamp "stream dropped, reconnecting in 2s"
  sleep 2
done
