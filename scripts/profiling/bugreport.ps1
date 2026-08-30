# bugreport.ps1
#
# ONE job: pull an `adb bugreport` zip for Battery Historian (checklist 10.2 step E.2).
# Run it right after batterystats-dump.ps1, while still on USB.
#
# `adb bugreport` takes minutes and CANNOT survive a mid-capture adb drop - it restarts from
# scratch. So this script just retries the whole bugreport with backoff until one completes,
# and writes it to a timestamped path under scripts/profiling/out/. On ColorOS, work through
# the pre-flight checklist in README.md FIRST or this will loop.
#
#   powershell -File scripts\profiling\bugreport.ps1

. "$PSScriptRoot\lib.ps1"

$zip = Get-TimestampedPath -Prefix 'bugreport' -Extension 'zip'
Write-Stamp "requesting bugreport -> $zip (this takes a few minutes; do not touch USB)"

# Longer backoff: a failed bugreport has already cost minutes, no point hammering.
[void](Invoke-AdbBurst -AdbArgs @('bugreport', $zip) -MaxAttempts 5 -BaseDelaySec 10 -MaxDelaySec 120)

if (Test-Path $zip) {
    Write-Stamp "done: $zip"
    Write-Stamp "load it at https://developer.android.com/topic/performance/power/battery-historian (or a local Historian instance)"
} else {
    throw "bugreport reported success but $zip is missing - check adb output and disk space."
}
