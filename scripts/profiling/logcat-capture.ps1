# logcat-capture.ps1
#
# ONE job: stream the ScrollKill accessibility-service log to a file for the whole run, and
# reconnect on its own every time ColorOS kills the adb link (checklist 10.3: adb drops after
# ~20-30 s). This is the long-running capture behind steps A, B and D - start it in its own
# terminal and leave it up for the entire session.
#
# Filter is exactly `-s ScrollKillA11y` (checklist 10.2 step A.3). Output is one timestamped,
# append-mode file under scripts/profiling/out/; each (re)connection writes a marker line, so
# gaps from adb drops are visible. Stop with Ctrl+C when the run is done.
#
# Lines to count / read afterwards:
#   "detected <SURFACE> in <pkg> conf=<x>"   -> pipeline runs that passed the 250 ms debounce (step A)
#   "profile window=<ms> raw=<n> passed=<n> rootReads=<n> getChild=<n> extractN=<n>
#      extractTotalUs=<n> extractMaxUs=<n>"  -> ~1 Hz counter line (steps A + B); derive:
#        raw/s = raw / (window/1000);  passed/s = passed / (window/1000)
#        debounce drop ratio = 1 - passed/raw
#        getChild/event = getChild / extractN
#        extract mean ms = extractTotalUs / extractN / 1000 ;  extract max ms = extractMaxUs / 1000
#   "profile sessions INSERT <pkg>/<surface>" -> one line per completed session = DB write cadence (step D)
#   "StrictMode policy violation" (tag StrictMode) is on a separate tag; also capture with:
#      adb logcat -s StrictMode:D   (run a second copy of this idea if you want it in-file)
#
# NOTE: a reconnect can replay a few buffered lines; dedupe on the leading timestamp when counting.
#
#   powershell -File scripts\profiling\logcat-capture.ps1

. "$PSScriptRoot\lib.ps1"

$logFile = Get-TimestampedPath -Prefix 'logcat-ScrollKillA11y' -Extension 'log'
Write-Stamp "streaming -s ScrollKillA11y to $logFile (Ctrl+C to stop)"

# Clear the ring buffer once so the first block is fresh, then follow.
[void](Invoke-AdbBurst -AdbArgs @('logcat', '-c') -MaxAttempts 4)

while ($true) {
    if (-not (Wait-ForDevice -TimeoutSec 120)) {
        Add-Content -Path $logFile -Value ("===== [{0}] device not back after 120s, still waiting =====" -f (Get-Date -Format 'o'))
        continue
    }
    Add-Content -Path $logFile -Value ("===== [{0}] (re)connected, streaming =====" -f (Get-Date -Format 'o'))
    Write-Stamp "connected, streaming"

    # Blocks here until adb dies. -v time keeps the wall-clock column for dedupe + rate math.
    & adb logcat -s ScrollKillA11y -v time | Add-Content -Path $logFile

    Add-Content -Path $logFile -Value ("===== [{0}] stream ended (exit {1}), reconnecting =====" -f (Get-Date -Format 'o'), $LASTEXITCODE)
    Write-Stamp "stream dropped, reconnecting in 2s"
    Start-Sleep -Seconds 2
}
