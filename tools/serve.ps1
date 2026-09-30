# ---------------------------------------------------------------------------
#  Start / stop the LunaGC server, detached from the shell that launched it.
#
#  serve.ps1 -Mode start    -> bring up the shared Compose MongoDB, then the
#                             server in a hidden process (survives this script)
#  serve.ps1 -Mode stop     -> kill the java process serving 8088
#  serve.ps1 -Mode status   -> report whether the server is up
#
#  The server is launched with Start-Process cmd.exe /c "..." so that cmd owns
#  the stdout/stderr redirection; redirecting from .NET ProcessStartInfo would
#  swallow the output instead of writing it to the log files.
# ---------------------------------------------------------------------------
[CmdletBinding()]
param(
    [Parameter()]
    [ValidateSet('start', 'stop', 'status')]
    [string] $Mode = 'status',

    # Extra arguments appended to the java command line. The runner passes
    # none by default; '-debug' turns on DEBUG logging (quest acceptance,
    # quest exec handlers, packet routing decisions) without packet logging,
    # '-debugall' adds packet logging, and '-dev' is the full developer setup
    # (debug plus the unimplemented-request reporter and packet logging).
    # NOTE: the space form '-debug all' does not work -- the space lands as its
    # own argv entry, no handler matches the bare word 'all', and it is dropped
    # silently. Use '-debugall' or the '=' form. See StartupArguments.
    [string] $ServerArgs = ''
)

$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $repo

$jarName = if ($env:LUNAGC_JAR) { $env:LUNAGC_JAR } else { 'LunaGC-7.1.0.jar' }
$jar = Join-Path $repo $jarName

$useComposeMongo = [string]::IsNullOrWhiteSpace($env:LUNAGC_MONGO)
$mongoContainer = $env:LUNAGC_MONGO
if ($useComposeMongo) {
    # Resolve the Compose service instead of maintaining a second dev database.
    $mongoContainer = docker compose ps --all --quiet mongo
    if ($LASTEXITCODE -ne 0) {
        throw "Could not locate the Compose MongoDB service. Is Docker Desktop running?"
    }
    if ([string]::IsNullOrWhiteSpace($env:LUNAGC_MONGO_URI)) {
        $mongoPort = if ($env:LUNAGC_MONGO_PORT) { $env:LUNAGC_MONGO_PORT } else { '27017' }
        $env:LUNAGC_MONGO_URI = "mongodb://127.0.0.1:$mongoPort"
    }
}
$httpPort = if ($env:LUNAGC_HTTP_PORT) { $env:LUNAGC_HTTP_PORT } else { '8088' }
$gamePort  = if ($env:LUNAGC_GAME_PORT) { $env:LUNAGC_GAME_PORT } else { '22101' }

$stdoutLog = Join-Path $repo 'start_stdout.log'
$stderrLog = Join-Path $repo 'start_stderr.log'

# The dispatch server is TCP; the KCP game server is UDP, so a TCP-only check
# would report it as down when it is actually up.
function Test-PortListening([string] $port) {
    $tcp = Get-NetTCPConnection -State Listen -LocalPort ([int] $port) -ErrorAction SilentlyContinue
    if ($null -ne $tcp) { return $true }
    $udp = Get-NetUDPEndpoint -LocalPort ([int] $port) -ErrorAction SilentlyContinue
    return $null -ne $udp
}

function Get-ServerPid {
    $conns = Get-NetTCPConnection -State Listen -LocalPort ([int] $httpPort) -ErrorAction SilentlyContinue
    if (-not $conns) { return $null }
    # there can be several listening entries (IPv4 + IPv6); the owning process
    # is the same java process for all of them
    return ($conns | Select-Object -First 1).OwningProcess
}

# ---------------------------------------------------------------- status ----

if ($Mode -eq 'status') {
    $mongo = if ($mongoContainer) {
        docker inspect --format '{{.State.Status}}' $mongoContainer 2>$null
    } else { 'absent' }
    if (-not $mongo) { $mongo = 'absent' }

    $http = if (Test-PortListening $httpPort) { 'listening' } else { 'down' }
    $game = if (Test-PortListening $gamePort) { 'listening' } else { 'down' }
    # $game is UDP; the TCP probe above already covers it via the UDP fallback.
    $jarOk = if (Test-Path $jar) { 'present' } else { 'MISSING' }

    Write-Host ("mongo [{0}]  http 127.0.0.1:{1} [{2}]  game 127.0.0.1:{3} [{4}]  jar [{5}]" `
        -f $mongo, $httpPort, $http, $gamePort, $game, $jarOk)
    if (-not (Test-Path $jar)) {
        Write-Host "  build it first: task build:jar"
    }
    exit 0
}

# ----------------------------------------------------------------- start ----

if ($Mode -eq 'start') {
    if (-not (Test-Path $jar)) {
        throw "Server jar not found: $jar -- run 'task build:jar' first."
    }

    if (Test-PortListening $httpPort) {
        $existing = Get-ServerPid
        Write-Host "server already running (http 127.0.0.1:$httpPort, PID $existing) -- nothing to do"
        exit 0
    }

    if ($useComposeMongo) {
        Write-Host "starting the shared Compose MongoDB service..."
        docker compose up --detach --wait --wait-timeout 60 mongo
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to start the shared Compose MongoDB service (docker exit $LASTEXITCODE)."
        }
    } else {
        Write-Host "starting MongoDB container '$mongoContainer' (if stopped)..."
        $mongoState = docker inspect --format '{{.State.Status}}' $mongoContainer 2>$null
        if (-not $mongoState) {
            throw "MongoDB container '$mongoContainer' does not exist. Create it first or unset LUNAGC_MONGO to use Compose."
        }
        if ($mongoState -ne 'running') {
            docker start $mongoContainer | Out-Null
            if ($LASTEXITCODE -ne 0) {
                throw "Failed to start MongoDB container '$mongoContainer' (docker exit $LASTEXITCODE). Is Docker Desktop running?"
            }
        }
    }

    Write-Host "starting LunaGC (dispatch 0.0.0.0:$httpPort, game 127.0.0.1:$gamePort)..."
    Write-Host "  logs: start_stdout.log / start_stderr.log (overwritten each launch)"

    # cmd owns the redirection; the hidden process survives this script exiting.
    # The inner command line is quoted so cmd treats it as one statement and
    # keeps the redirects attached to java rather than to cmd itself.
    # $ServerArgs is interpolated into that single quoted statement, so it may
    # contain spaces ('-debug all') without needing extra quoting of its own.
    $args_ = if ($ServerArgs) { ' ' + $ServerArgs } else { '' }
    $inner = 'java -jar "{0}"{1} > "{2}" 2> "{3}"' -f $jar, $args_, $stdoutLog, $stderrLog
    Start-Process -FilePath 'cmd.exe' `
        -ArgumentList "/c `"$inner`"" `
        -WorkingDirectory $repo `
        -WindowStyle Hidden | Out-Null

    # wait for the dispatch port to accept connections (server boot takes a
    # few seconds while it loads resources and the handbook)
    $deadline = (Get-Date).AddSeconds(120)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Milliseconds 500
        if (Test-PortListening $httpPort) {
            $pid_ = Get-ServerPid
            Write-Host "server is up -- http 127.0.0.1:$httpPort  (PID $pid_)"
            Write-Host "log tail:"
            Get-Content $stdoutLog -Tail 5 | ForEach-Object { Write-Host "  $_" }
            exit 0
        }
        if (Test-Path $stderrLog) {
            $err = Get-Content $stderrLog -ErrorAction SilentlyContinue
            if ($err -and ($err -join "`n").Length -gt 0) {
                # the server writes ordinary startup progress to stderr too, so
                # only bail on a hard failure marker. JLine's "WARNING: Unable
                # to create a system terminal, creating a dumb terminal" is on
                # that list and is harmless -- it prints whenever this script
                # launches java from a shell without a real console (which is
                # every launch from a task runner), the server keeps booting and
                # the port comes up. Matching bare 'Unable to' turned that into
                # a phantom startup failure, which in turn made dev.ps1 abort
                # before it launched the game. WARNING: lines are advice from a
                # library, not a failure, so they are filtered out first; the
                # remaining markers are things java itself cannot recover from.
                $bad = $err | Where-Object { $_ -notmatch '^WARNING:' } |
                    Where-Object { $_ -match 'Exception in thread|Could not find or load|UnsatisfiedLinkError|Unable to (load|initialize|start|access)' }
                if ($bad) {
                    Write-Host "server failed to start:"
                    $bad | Select-Object -First 5 | ForEach-Object { Write-Host "  $_" }
                    Write-Host "full log: $stderrLog"
                    exit 1
                }
            }
        }
    }

    Write-Host "server did not come up within 120s -- check $stderrLog"
    exit 1
}

# ------------------------------------------------------------------ stop ----

if ($Mode -eq 'stop') {
    $pid_ = Get-ServerPid
    if (-not $pid_) {
        Write-Host "no server listening on 127.0.0.1:$httpPort -- nothing to stop"
        exit 0
    }
    $serverProcess = Get-Process -Id $pid_ -ErrorAction SilentlyContinue
    if (-not $serverProcess -or $serverProcess.ProcessName -ne 'java') {
        throw "The HTTP port is owned by a non-Java process. For the Compose runtime, use 'docker compose stop server'."
    }
    Write-Host "stopping LunaGC java process PID $pid_"
    Stop-Process -Id $pid_ -Force
    # the dispatch port closes quickly; the KCP game port follows
    $deadline = (Get-Date).AddSeconds(10)
    while ((Get-Date) -lt $deadline -and (Test-PortListening $httpPort)) {
        Start-Sleep -Milliseconds 250
    }
    Write-Host "stopped (mongo container left running so player data is untouched)"
    exit 0
}
