# ---------------------------------------------------------------------------
#  Resolve the game install directory.
#
#  Priority:
#    1. GAME_PATH from .env (task loads it into the environment)
#    2. miHoYo launcher registry (HYP 1_1 / Genshin Impact keys)
#    3. fail
#
#  Prints ONLY the resolved path on stdout (this script is also used as a
#  Taskfile variable, so it must not emit anything else).  Diagnostics go to
#  stderr.
# ---------------------------------------------------------------------------
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

function Test-GameRoot([string] $path) {
    if (-not $path) { return $false }
    return Test-Path (Join-Path $path 'YuanShen_Data\Plugins\Astrolabe.dll')
}

function Resolve-GamePath {
    # 1. explicit env / .env value
    $envPath = $env:GAME_PATH
    if ($envPath -and $envPath.Trim() -ne '') {
        return $envPath.Trim()
    }

    # 2. launcher registry -- prefer the HYP (new launcher) hk4e_cn subkey,
    #    then scan every HYP game subkey, then the legacy single-launcher keys.
    $candidates = @()
    $candidates += 'HKCU:\Software\miHoYo\HYP\1_1\hk4e_cn'
    $candidates += 'HKLM:\Software\WOW6432Node\miHoYo\HYP\1_1\hk4e_cn'
    foreach ($key in 'HKCU:\Software\miHoYo\HYP\1_1', 'HKLM:\Software\WOW6432Node\miHoYo\HYP\1_1') {
        try {
            foreach ($child in Get-ChildItem $key -ErrorAction SilentlyContinue) {
                $candidates += $child.PSPath
            }
        } catch { }
    }
    $candidates += 'HKCU:\Software\miHoYo\Genshin Impact'
    $candidates += 'HKLM:\Software\WOW6432Node\miHoYo\Genshin Impact'

    foreach ($key in $candidates) {
        try {
            $val = (Get-ItemProperty $key -ErrorAction SilentlyContinue).GameInstallPath
            if ($val -and $val.Trim() -ne '') {
                return $val.Trim()
            }
        } catch { }
    }

    return $null
}

$path = Resolve-GamePath

if (-not $path) {
    [Console]::Error.WriteLine(
        "GAME_PATH is not set and no miHoYo launcher install was found in the registry.`n" +
        "Set GAME_PATH in .env to the directory that contains YuanShen.exe.")
    exit 1
}
if (-not (Test-GameRoot $path)) {
    [Console]::Error.WriteLine(
        "Resolved game path does not look like a Genshin client (missing YuanShen_Data\Plugins\Astrolabe.dll): $path")
    exit 1
}

Write-Output $path
