# lib.ps1 - shared helpers for the ScrollKill on-device battery profiling scripts.
#
# Usage: dot-source it from a sibling script, never run directly:
#   . "$PSScriptRoot\lib.ps1"
#
# Everything here is built for one hostile condition: OPPO ColorOS (CPH2791) drops the adb
# link after ~20-30 s (checklist 10.3, run 1). So every adb call goes through a
# retry-with-backoff loop that first waits for the device to come back, and every capture
# writes to an append-mode, timestamped file under scripts/profiling/out/ so a killed run
# loses nothing already on disk.
#
# Requires: adb on PATH (Android platform-tools). PowerShell 5.1+ (Windows PowerShell) or
# PowerShell 7. No third-party modules.

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# App under test - the ScrollKill debug build.
$script:ScrollKillPackage = 'com.ikasle.scrollkill'

# Output directory (git-ignored via scripts/profiling/.gitignore).
$script:ProfilingOutDir = Join-Path $PSScriptRoot 'out'

function Initialize-ProfilingOut {
    if (-not (Test-Path $script:ProfilingOutDir)) {
        New-Item -ItemType Directory -Path $script:ProfilingOutDir | Out-Null
    }
}

function Get-TimestampedPath {
    param(
        [Parameter(Mandatory = $true)][string]$Prefix,
        [Parameter(Mandatory = $true)][string]$Extension
    )
    Initialize-ProfilingOut
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    Join-Path $script:ProfilingOutDir ("{0}-{1}.{2}" -f $Prefix, $stamp, $Extension)
}

function Write-Stamp {
    param([string]$Message)
    Write-Host ("[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $Message)
}

# Wait (bounded) for a single device to be ready. ColorOS often re-authorises slowly.
function Wait-ForDevice {
    param([int]$TimeoutSec = 60)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $state = (& adb get-state) 2>$null
        if ($LASTEXITCODE -eq 0 -and $state -match 'device') { return $true }
        Start-Sleep -Seconds 2
    }
    return $false
}

# Run one adb invocation with exponential backoff. Returns the captured stdout on success,
# throws after $MaxAttempts. Keep the payload SHORT - one dumpsys, one setprop - so a
# mid-command adb drop just means "retry", never a half-written capture.
function Invoke-AdbBurst {
    param(
        [Parameter(Mandatory = $true)][string[]]$AdbArgs,
        [int]$MaxAttempts = 8,
        [int]$BaseDelaySec = 2,
        [int]$MaxDelaySec = 30
    )
    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        [void](Wait-ForDevice)
        $out = & adb @AdbArgs 2>&1
        if ($LASTEXITCODE -eq 0) { return $out }

        $delay = [Math]::Min($BaseDelaySec * [Math]::Pow(2, $attempt - 1), $MaxDelaySec)
        Write-Stamp ("adb {0} failed (attempt {1}/{2}, exit {3}); retrying in {4}s" -f `
            ($AdbArgs -join ' '), $attempt, $MaxAttempts, $LASTEXITCODE, $delay)
        Write-Stamp ("  last output: {0}" -f (($out | Out-String).Trim()))
        Start-Sleep -Seconds $delay
    }
    throw ("adb {0} still failing after {1} attempts - fix the adb link (see the ColorOS pre-flight checklist in README.md) and re-run." -f ($AdbArgs -join ' '), $MaxAttempts)
}

# Append a labelled adb capture to $Path, retrying the burst until it lands.
function Add-AdbCapture {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string[]]$AdbArgs,
        [string]$Label
    )
    $header = "===== [{0}] adb {1} {2} =====" -f (Get-Date -Format 'o'), ($AdbArgs -join ' '), $Label
    Add-Content -Path $Path -Value $header
    $out = Invoke-AdbBurst -AdbArgs $AdbArgs
    Add-Content -Path $Path -Value ($out | Out-String)
    Write-Stamp ("appended {0} bytes to {1}" -f (($out | Out-String).Length), (Split-Path $Path -Leaf))
}
