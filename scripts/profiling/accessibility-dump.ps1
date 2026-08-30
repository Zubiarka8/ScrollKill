# accessibility-dump.ps1
#
# ONE job: snapshot `dumpsys accessibility` (checklist 10.2 step A.1 "before" and the matching
# "after"). Use it to confirm the ScrollKill service is bound, which event types it is
# registered for, and notificationTimeout.
#
# Single short burst with retry-with-backoff. Each run writes its own timestamped file under
# scripts/profiling/out/, so "before" and "after" never collide; pass -Label to tag them.
#
#   powershell -File scripts\profiling\accessibility-dump.ps1 -Label before
#   powershell -File scripts\profiling\accessibility-dump.ps1 -Label after

param([string]$Label = '')

. "$PSScriptRoot\lib.ps1"

$suffix = if ($Label) { "-$Label" } else { '' }
$path = Get-TimestampedPath -Prefix ("accessibility{0}" -f $suffix) -Extension 'txt'

Add-AdbCapture -Path $path -Label $Label -AdbArgs @('shell', 'dumpsys', 'accessibility')

Write-Stamp "wrote $path"
Write-Stamp "check: 'Bound services' lists ScrollKill; eventTypes = WINDOW_STATE/CONTENT_CHANGED; notificationTimeout"
