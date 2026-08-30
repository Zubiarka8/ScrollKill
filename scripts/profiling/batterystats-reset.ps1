# batterystats-reset.ps1
#
# ONE job: zero the battery stats on the device so a drain measurement starts from a clean
# slate. Run this immediately before the scripted mixed-use run (checklist 10.2 step E.1),
# then unplug USB.
#
# Also flips on full wake-lock history so the "wakelock count/duration" line in step E.3 is
# populated (expected ~none for ScrollKill).
#
# Idempotent, retry-with-backoff (survives ColorOS dropping adb mid-command). Writes a small
# marker file so you have a timestamp for "t=0" of the drain window.
#
#   pwsh scripts/profiling/batterystats-reset.ps1        # PowerShell 7
#   powershell -File scripts\profiling\batterystats-reset.ps1   # Windows PowerShell 5.1

. "$PSScriptRoot\lib.ps1"

Write-Stamp "resetting batterystats"
[void](Invoke-AdbBurst -AdbArgs @('shell', 'dumpsys', 'batterystats', '--reset'))
[void](Invoke-AdbBurst -AdbArgs @('shell', 'dumpsys', 'batterystats', '--enable', 'full-wake-history'))

$marker = Get-TimestampedPath -Prefix 'batterystats-reset' -Extension 'txt'
Set-Content -Path $marker -Value ("batterystats --reset at {0}`nUnplug USB now, start the scripted run." -f (Get-Date -Format 'o'))
Write-Stamp "done - t=0 marker: $marker"
Write-Stamp "next: unplug USB, run the 30 min scripted mixed use, then batterystats-dump.ps1 + bugreport.ps1"
