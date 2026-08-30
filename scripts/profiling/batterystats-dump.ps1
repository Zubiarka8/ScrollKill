# batterystats-dump.ps1
#
# ONE job: capture the human-readable battery stats after a drain run (checklist 10.2 step
# E.2). Writes two timestamped, append-mode files under scripts/profiling/out/:
#   batterystats-full-*.txt      - `dumpsys batterystats` (whole device)
#   batterystats-scrollkill-*.txt - `dumpsys batterystats com.ikasle.scrollkill` (app only)
#
# For the Battery Historian view you also need a bugreport - run bugreport.ps1 right after
# this one.
#
# Retry-with-backoff; each dumpsys is a single short burst so a ColorOS adb drop mid-capture
# just retries. Re-running appends another labelled block, so partial runs never lose data.
#
#   powershell -File scripts\profiling\batterystats-dump.ps1 [-Label "after-30min"]

param([string]$Label = '')

. "$PSScriptRoot\lib.ps1"

$full = Get-TimestampedPath -Prefix 'batterystats-full' -Extension 'txt'
$app  = Get-TimestampedPath -Prefix 'batterystats-scrollkill' -Extension 'txt'

Add-AdbCapture -Path $full -Label $Label -AdbArgs @('shell', 'dumpsys', 'batterystats')
Add-AdbCapture -Path $app  -Label $Label -AdbArgs @('shell', 'dumpsys', 'batterystats', $script:ScrollKillPackage)

Write-Stamp "wrote:"
Write-Stamp "  $full"
Write-Stamp "  $app"
Write-Stamp "read: ScrollKill mAh, % of total, CPU time fg/bg, wakelock count/duration (expect ~none)"
