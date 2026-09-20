# ---------------------------------------------------------------------------
#  `task dev` -- one command that brings up a debuggable session.
#
#  dev.ps1 -Mode start    -> server in -debug mode (if not already up), the
#                           patch state checked, the client launched, and a
#                           hidden detached monitor process left in charge of
#                           collecting everything. This script then exits; the
#                           monitor outlives it and outlives the terminal.
#  dev.ps1 -Mode stop     -> end the session. The game is closed, the monitor
#                           notices, writes the report, and exits.
#  dev.ps1 -Mode status   -> is a session being watched, and by whom.
#  dev.ps1 -Mode report   -> print the latest session report.
#
#  The collection itself lives in dev_monitor.ps1; this script is only the
#  setup and teardown, so everything it starts has to be able to run without
#  it.
# ---------------------------------------------------------------------------
[CmdletBinding()]
param(
    [Parameter()]
    [ValidateSet('start', 'stop', 'status', 'report')]
    [string] $Mode = 'status',

    # Extra server arguments. The dev default is '-debug': DEBUG logging
    # without packet spam. '-debug all' adds packet logging. See
    # StartupArguments. Passed straight through to serve.ps1, which quotes the
    # whole java command line as one cmd statement, so spaces are fine.
    [string] $ServerArgs = '-debug',

    # skip the patch state check (use after a manual `task patch`)
    [switch] $SkipPatchCheck
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $repo

$debugDir = Join-Path $repo 'debug'
$monitorPidFile = Join-Path $debugDir 'monitor.pid'
$gamePidFile = Join-Path $debugDir 'game.pid'
$stdoutLog = Join-Path $repo 'start_stdout.log'
$httpPort = if ($env:LUNAGC_HTTP_PORT) { $env:LUNAGC_HTTP_PORT } else { '8088' }

function Test-PortListening([int] $port) {
    $tcp = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
    if ($null -ne $tcp) { return $true }
    return $null -ne (Get-NetUDPEndpoint -LocalPort $port -ErrorAction SilentlyContinue)
}

function Get-ServerPid {
    $conns = Get-NetTCPConnection -State Listen -LocalPort ([int] $httpPort) -ErrorAction SilentlyContinue
    if (-not $conns) { return $null }
    return ($conns | Select-Object -First 1).OwningProcess
}

function Get-SessionMonitor {
    # the pid file is only advisory; a monitor whose process is gone is not
    # watching anything, so check the process too
    if (-not (Test-Path $monitorPidFile)) { return $null }
    $mpid = (Get-Content $monitorPidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
    if (-not $mpid) { return $null }
    $p = Get-Process -Id ([int] $mpid) -ErrorAction SilentlyContinue
    if (-not $p) { return $null }
    return $p
}

# ---------------------------------------------------------------- status ----

if ($Mode -eq 'status') {
    $mon = Get-SessionMonitor
    $gpid = $null
    if (Test-Path $gamePidFile) {
        $gpid = (Get-Content $gamePidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
    }
    $game = if ($gpid) { Get-Process -Id ([int] $gpid) -ErrorAction SilentlyContinue } else { $null }

    Write-Host ("monitor  [{0}]" -f $(if ($mon) { "running PID $($mon.Id)" } else { 'off' }))
    Write-Host ("game     [{0}]" -f $(if ($game) { "running PID $($game.Id) ($($game.ProcessName))" } elseif ($gpid) { 'exited -- see the report' } else { 'no session' }))
    Write-Host ("server   [{0}]" -f $(if (Test-PortListening ([int] $httpPort)) { "listening 127.0.0.1:$httpPort (PID $(Get-ServerPid))" } else { 'down' }))
    if (Test-Path (Join-Path $debugDir 'report.md')) {
        Write-Host "report   : $debugDir\report.md"
    }
    exit 0
}

# ---------------------------------------------------------------- report ----

if ($Mode -eq 'report') {
    $report = Join-Path $debugDir 'report.md'
    if (-not (Test-Path $report)) {
        Write-Host "no session report yet (looked for $report)"
        Write-Host "start a session first: task dev"
        exit 1
    }
    Get-Content $report -Encoding UTF8
    exit 0
}

# ----------------------------------------------------------------- start ----

if ($Mode -eq 'start') {
    $null = New-Item -ItemType Directory -Force -Path $debugDir

    # --- server -----------------------------------------------------------
    $serverStarted = $false
    if (Test-PortListening ([int] $httpPort)) {
        # an already-running server is fine, but there is no way to tell from
        # here whether it was started with -debug, so say so and do not touch
        # its log: the tail job starts at the current end of the file instead.
        Write-Host "server already listening on 127.0.0.1:$httpPort (PID $(Get-ServerPid)) -- reusing it"
        Write-Host "  if it was not started with -debug, quest and routing detail will be missing"
    } else {
        # Archive the PREVIOUS run's log before starting anything. serve.ps1
        # launches java with a cmd `>` redirect, which truncates
        # start_stdout.log on open and then holds that handle for the server's
        # whole lifetime -- after the start the file is empty AND cannot be
        # renamed underneath it, so rotating had to happen before the launch.
        # The guard is load-bearing: under $ErrorActionPreference = 'Stop' a
        # refused rename is a terminating IOException, this block sits before
        # the patch check, the game launch and the monitor spawn, and an
        # unguarded Move-Item here was killing `task dev` on its primary path
        # before any of those ran. A server left over from a session that died
        # can still be holding the old handle, so a failure here is reported
        # and swallowed rather than fatal.
        if (Test-Path $stdoutLog) {
            try {
                Move-Item -Path $stdoutLog -Destination "$stdoutLog.predev" -Force
                Write-Host "rotated the previous server log to start_stdout.log.predev"
            } catch {
                Write-Host "could not rotate the previous server log (still held open by a stale server) - continuing"
            }
        }
        Write-Host "starting the server with '$ServerArgs'..."
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
            (Join-Path $repo 'tools\serve.ps1') -Mode start -ServerArgs $ServerArgs
        if ($LASTEXITCODE -ne 0) {
            Write-Host "server did not come up (serve.ps1 exit $LASTEXITCODE) -- run 'task serve' by hand"
            exit 1
        }
        $serverStarted = $true
    }

    # Only rotate when this script owns the server start. The java process
    # keeps its write handle open across a rename, so rotating a live server's
    # log would send the whole session to the rotated copy and leave the tail
    # job watching a file nothing writes to.
    # if ($serverStarted -and (Test-Path $stdoutLog)) {
    #     Move-Item -Path $stdoutLog -Destination "$stdoutLog.predev" -Force
    #     Write-Host "rotated the previous server log to start_stdout.log.predev"
    # }

    # The byte offset the session starts at: after the rotation above, the log
    # is either empty (this script started the server) or holds the pre-session
    # output (a live server is being reused). Recording it here -- before the
    # game is even launched -- is what makes the monitor's tail exact. It seeks
    # to this offset instead of asking Get-Content for "the end of the file",
    # which is wherever that cmdlet happens to run, and every line the server
    # writes while the monitor process is still coming up is lost.
    $startOffset = 0
    if (Test-Path $stdoutLog) {
        $startOffset = (Get-Item -Path $stdoutLog).Length
    }

    # --- patch -------------------------------------------------------------
    if (-not $SkipPatchCheck) {
        $patchStatus = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
            (Join-Path $repo 'tools\patch_game.ps1') -Mode status
        $patchStatus | ForEach-Object { Write-Host "  patch | $_" }
        # Each pattern names the tools/patch_game.ps1 status line it catches.
        # A launch-ready install prints none of them, so this is the whole
        # definition of "patch state is good enough to play".
        $blockers = @(
            'Astrolabe\.dll\s*: stock',    # the anti-cheat slot is unpatched
            'AccountPlatNat\s*: (stock|PARTIAL)',  # the passport SDK is, or half is
            ':\s*missing',                 # a DLL or the repo's ext.dll is absent
            'STALE',                       # orig proxy, or a backup holding a patched build
            'miHoYo key',                  # passport key not swapped -- "account or password error"
            'PRESENT --'                   # a live copy stranded by a session that died swapped
        )
        $bad = @($patchStatus | Where-Object {
            foreach ($b in $blockers) { if ($_ -match $b) { return $true } }
            return $false
        })
        if ($bad.Count -gt 0) {
            Write-Host ""
            Write-Host "patch state is not launch-ready:"
            $bad | ForEach-Object { Write-Host "  $_" }
            Write-Host "run 'task patch' and then 'task dev' again (or -SkipPatchCheck to launch anyway)"
            exit 1
        }
    }

    # --- game --------------------------------------------------------------
    $gamePath = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
        (Join-Path $repo 'tools\game_path.ps1')
    if ($LASTEXITCODE -ne 0 -or -not $gamePath) {
        Write-Host "could not resolve the game install path -- set GAME_PATH in .env"
        exit 1
    }
    $exe = Join-Path $gamePath 'YuanShen.exe'
    if (-not (Test-Path $exe)) {
        Write-Host "YuanShen.exe not found in $gamePath"
        exit 1
    }

    Write-Host "launching the client from $gamePath"
    $gameProc = Start-Process -FilePath $exe -WorkingDirectory $gamePath -PassThru
    if (-not $gameProc) {
        Write-Host "Start-Process failed for $exe"
        exit 1
    }
    "$($gameProc.Id)" | Out-File -FilePath $gamePidFile -Encoding UTF8
    Write-Host "game PID $($gameProc.Id)"

    # --- monitor -----------------------------------------------------------
    # Hidden + its own process: this script is about to exit and the task
    # runner's shell goes with it, but the collection has to run until the game
    # does. The monitor writes its own pid file so `task dev:stop` can find it.
    $monArgs = @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-WindowStyle', 'Hidden',
        '-File', (Join-Path $repo 'tools\dev_monitor.ps1'),
        '-GamePid', "$($gameProc.Id)",
        '-StdoutLog', $stdoutLog,
        '-OutDir', $debugDir,
        '-RepoDir', $repo,
        '-StartOffset', "$startOffset"
    )
    $mon = Start-Process -FilePath 'powershell.exe' -ArgumentList $monArgs -PassThru -WindowStyle Hidden
    if (-not $mon) {
        Write-Host "WARNING: the monitor process did not start -- the session will not be recorded"
    } else {
        "$($mon.Id)" | Out-File -FilePath $monitorPidFile -Encoding UTF8
        Write-Host "monitor PID $($mon.Id) (hidden, detached)"
    }

    Write-Host ""
    Write-Host "session is being recorded in $debugDir"
    Write-Host "stop it with: task dev:stop"
    Write-Host "read it with: task dev:report"
    exit 0
}

# ------------------------------------------------------------------ stop ----

if ($Mode -eq 'stop') {
    $mon = Get-SessionMonitor
    $gpid = $null
    if (Test-Path $gamePidFile) {
        $gpid = (Get-Content $gamePidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
    }
    $game = if ($gpid) { Get-Process -Id ([int] $gpid) -ErrorAction SilentlyContinue } else { $null }

    if (-not $game) {
        Write-Host "no game session to stop (pid file: $gamePidFile)"
    } else {
        Write-Host "closing the game (PID $($game.Id))..."
        Stop-Process -Id $game.Id -Force
        # the monitor polls every 500ms; it will see the exit, give the tail job
        # its 3s of trailing log, and write the report
    }

    if ($mon) {
        Write-Host "waiting for the monitor (PID $($mon.Id)) to finish the report..."
        $deadline = (Get-Date).AddSeconds(60)
        while ((Get-Date) -lt $deadline -and (Get-SessionMonitor)) {
            Start-Sleep -Milliseconds 500
        }
        if (Get-SessionMonitor) {
            Write-Host "the monitor is still running after 60s; the report will land in $debugDir when it exits"
        }
    }

    $report = Join-Path $debugDir 'report.md'
    if (Test-Path $report) {
        Write-Host ""
        Get-Content $report -Encoding UTF8 | Select-Object -First 30
    }
    exit 0
}
