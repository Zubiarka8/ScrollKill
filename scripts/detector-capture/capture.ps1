# capture.ps1 - dump the on-screen view hierarchy of a social app via `uiautomator dump`,
# to seed the JVM detector fixtures (app\src\test\resources\detector-fixtures\).
#
# No ScrollKill install and no accessibility permission needed: uiautomator is part of
# Android. Works against a real device or an emulator.
#
#   1. On the device, open the app + surface you want (e.g. TikTok > For You) and scroll once.
#   2. ./scripts/detector-capture/capture.ps1 tiktok-fyp
#   3. Review scripts\detector-capture\out\<name>-<date>.xml, then copy it into
#      app\src\test\resources\detector-fixtures\ and run:  ./gradlew testDebugUnitTest
#      (DetectorFixtureReportTest writes app\build\reports\detector-fixtures\report.txt)
#
# adb calls retry with backoff - ColorOS drops the link after ~20-30 s (checklist 10.3);
# wireless debugging is steadier than USB there.
param([Parameter(Mandatory = $true)][string]$Name)
$ErrorActionPreference = 'Stop'

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$outDir = Join-Path $here 'out'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Stamp($m) { Write-Host ("[{0}] {1}" -f (Get-Date -Format HH:mm:ss), $m) }

function Invoke-AdbBurst {
    param([Parameter(Mandatory = $true)][string[]]$AdbArgs)
    for ($n = 1; $n -le 6; $n++) {
        & adb wait-for-device 2>$null
        $out = & adb @AdbArgs 2>&1
        if ($LASTEXITCODE -eq 0) { return $out }
        Stamp ("adb {0} failed ({1}/6); retry in {2}s" -f ($AdbArgs -join ' '), $n, ($n * 3))
        Start-Sleep -Seconds ($n * 3)
    }
    throw ("adb {0} keeps failing - fix the adb link first (try wireless debugging on ColorOS)" -f ($AdbArgs -join ' '))
}

$deviceTmp = '/sdcard/scrollkill-uidump.xml'
$outFile = Join-Path $outDir ("{0}-{1}.xml" -f $Name, (Get-Date -Format yyyyMMdd-HHmmss))

Stamp 'dumping current window hierarchy...'
Invoke-AdbBurst @('shell', 'uiautomator', 'dump', $deviceTmp) | Out-Null
Invoke-AdbBurst @('pull', $deviceTmp, $outFile) | Out-Null
try { Invoke-AdbBurst @('shell', 'rm', '-f', $deviceTmp) | Out-Null } catch { }

$m = Select-String -Path $outFile -Pattern 'package="([^"]*)"' | Select-Object -First 1
$pkg = if ($m) { $m.Matches.Groups[1].Value } else { '<unknown>' }
Stamp "wrote $outFile"
Stamp "focused package: $pkg"
Stamp "next: copy it into app\src\test\resources\detector-fixtures\ then ./gradlew testDebugUnitTest"
