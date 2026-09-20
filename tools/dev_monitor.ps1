# ---------------------------------------------------------------------------
#  The instrumented half of `task dev`. Runs detached and hidden until the game
#  process goes away, collecting everything a bug report needs, then writes the
#  session report and applies the remediations it can decide on its own.
#
#  tools/dev.ps1 launches this with -GamePid set and exits; this process owns
#  the watchers from then on, so closing the terminal that ran `task dev` does
#  not stop the collection.
#
#  What it watches:
#
#    the game process       exit code + lifetime. This is the primary crash
#                           signal: the ~99s signature kill leaves no dump, it
#                           just ends the process, so the exit code is all the
#                           evidence there is.
#    %LOCALAPPDATA%\Crash-  new .dmp files. Best effort -- WER LocalDumps is
#      Dumps                not configured by default, so the dir often stays
#                           empty even through a real crash.
#    the Application log    Windows Error Reporting entries for the game exe
#                           since the session started. Counts crashes the game
#                           did not get to report through its own channels.
#    the server stdout log  classified into unhandled opcodes / server errors /
#                           client-uploaded telemetry / quest progress.
#    %TEMP%\lunagc-patch.log  the patch's own swap log: which slots swapped,
#                           when, and whether detach restored them.
#
#  Remediations it applies itself (only deterministic ones; code fixes land in
#  the report with the evidence attached):
#
#    A live copy left behind after the process is gone means the session died
#    while swapped -- detach never ran, so the on-disk slot still holds the
#    signed stock DLL and the NEXT launch would be the unpatched client
#    ("account or password error", or login against the real dispatch). It
#    re-runs the patch deploy to put the patched image back and clear the
#    stranded copy.
# ---------------------------------------------------------------------------
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [int]    $GamePid,

    [Parameter(Mandatory = $true)]
    [string] $StdoutLog,

    [Parameter(Mandatory = $true)]
    [string] $OutDir,

    [Parameter(Mandatory = $true)]
    [string] $RepoDir,

    [string] $GameExe   = 'YuanShen.exe',
    # the patch crate's session log; %TEMP% is where it writes
    [string] $PatchLog  = (Join-Path $env:TEMP 'lunagc-patch.log'),
    # the byte offset in $StdoutLog where the session begins. dev.ps1 records
    # this before it starts the server (or, when reusing a live server, at the
    # current end) and passes it in; the tail job seeks here instead of asking
    # Get-Content for "the end", which is wherever the cmdlet happens to run.
    [long]   $StartOffset = 0,
    # re-running the deploy is the one fix this script can do unattended
    [switch] $SkipAutoFix
)

$ErrorActionPreference = 'Continue'
$startedAt = Get-Date
$null = New-Item -ItemType Directory -Force -Path $OutDir

# The exit code of a process this script did not start: Get-Process drops the
# PID the instant it dies, the .NET Process.ExitCode member throws on a process
# that was not started in-process, and Win32_Process has already dropped the
# record by then. A kernel handle opened while the game is alive is the only
# place the code survives, so the poll loop opens one and reads it after the
# object becomes signalled.
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class ProcHandle {
    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern IntPtr OpenProcess(uint access, bool inherit, int pid);
    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern uint WaitForSingleObject(IntPtr handle, uint ms);
    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool GetExitCodeProcess(IntPtr handle, out uint code);
    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool CloseHandle(IntPtr handle);
}
"@

# ------------------------------------------------------------------------- #

function Write-Index([string[]] $names, [hashtable] $labels) {
    # each classifier gets its own file plus an index entry, so a failing
    # feature can be read without paging through the whole session
    $idx = Join-Path $OutDir 'index.txt'
    $lines = foreach ($n in $names) {
        $p = Join-Path $OutDir "$n.log"
        if (Test-Path $p) {
            $c = @(Get-Content -Path $p -Encoding UTF8 -ErrorAction SilentlyContinue).Count
            "{0,-14} {1,5} lines  {2}" -f $n, $c, $labels[$n]
        }
    }
    $lines | Out-File -FilePath $idx -Encoding UTF8
}

# Everything the tail job finds. A single line can land in several buckets:
# the client's telemetry uploads are JSON bodies that contain the word
# "error", so the ERROR pattern has to exclude them or the report calls every
# client hiccup a server fault.
$patterns = @{
    # GameServerPacketHandler announces each opcode it has no handler for, once
    # per opcode, with the field numbers/wire types/values -- this is how a
    # missing feature names the packet it wants.
    unhandled = 'arrived and nothing handles it'
    # server-side faults. The telemetry exclusion is the Test-Telemetry guard
    # in the tail job, not a lookbehind here: the client's real lines read
    # 'SuperDebug ... "error_code"', never 'telemetry_ERROR', so the lookbehind
    # matched nothing and the guard was doing the whole job alone. FATAL and a
    # lowercase 'error' are included because a JVM fatal exit logs both and
    # would otherwise leave error.log empty for a hard server death.
    error     = 'ERROR|FATAL|Exception|SEVERE|error'
    # the client's own SuperDebug channel: network faults, HLOD warnings,
    # ability errors. Not server bugs, but "something in game did not work"
    # often shows up here first. The last two alternatives are the client's
    # other upload shape -- Javalin's request-debug 'Body:' lines holding a
    # crash report as JSON ('"userName"', '"stackTrace"', '"subErrorCode"').
    # Those contain the words Exception and error, so without them here the
    # Test-Telemetry guard misses the body entirely and every client crash
    # report lands in error.log and flips a clean session to ERRORS. A real
    # JVM fault prints 'at emu.grasscutter...' stack frames, never a quoted
    # JSON key, so this marker cannot swallow a server-side stack trace.
    telemetry = 'SuperDebug|PACKET_HEAD_MAGIC_ERROR|error_code|"eventName"|HLOD_COMPONENT|"userName"|"stackTrace"'
    # quest chain movement, for correlating a bug with how far the player got
    quest     = 'was completed|Added quest|will be finished|will be accepted'
}

# Every bucket starts empty. The tail job opens its writers in APPEND mode and
# only on first use -- a session with no server errors never opens error.log at
# all -- so without this a new session inherits the previous session's buckets:
# a clean session would find last session's client crash reports sitting in
# error.log, and index.txt (which counts the raw file, unlike the report's own
# error count, which re-filters telemetry) would call it 20 errors. The
# timestamped report-*.md copies are the durable history; these logs are this
# session's working set. A held-open handle from a monitor that never let go
# would fail the exclusive open here and the append writers carry on with
# whatever was already there, so the failure is reported, not fatal.
foreach ($name in @('all.log') + @($patterns.Keys | ForEach-Object { "$_.log" })) {
    try {
        [System.IO.File]::Open((Join-Path $OutDir $name), [System.IO.FileMode]::Truncate,
            [System.IO.FileAccess]::Write, [System.IO.FileShare]::None).Close()
    } catch {
        Write-Host ("[dev] could not reset {0} -- {1}" -f $name, $_.Exception.Message)
    }
}

# ---------------------------------------------------------------- watchers #

# WER events arrive out of order relative to the process actually dying, so the
# baseline is "everything for this exe before the session started" and the
# session's crashes are the entries newer than that.
$werBase = 0
try {
    # NOT Measure-Object: that emits one statistics object, and PowerShell
    # gives every object a synthetic .Count of 1, so the baseline would read
    # 0-or-1 whatever the real number of past crashes was. @() wraps the
    # matches into an array, whose Count is the number of them.
    $werBase = @(@(Get-WinEvent -FilterHashtable @{ LogName = 'Application'; Id = 1000, 1001 } `
                -ErrorAction SilentlyContinue) |
        Where-Object { $_.Message -match [regex]::Escape($GameExe) }).Count
} catch { }

$tailScript = {
    # $CancelFlag is a sentinel FILE, not a CancellationToken: Start-Job
    # serializes its arguments as CLIXML across the process boundary, and a
    # deserialized token arrives as a PSObject whose IsCancellationRequested is
    # pinned False -- cancelling it in the parent never reached the job, the
    # loop never exited on its own, and teardown fell back to force-killing it
    # after a full timeout. Both sides see the same filesystem.
    param($StdoutLog, $OutDir, $patterns, $CancelFlag, $StartOffset)
    function Test-Telemetry([string] $line) {
        return $line -match $patterns.telemetry
    }
    $writers = @{}
    foreach ($k in $patterns.Keys) {
        $writers[$k] = Join-Path $OutDir "$k.log"
    }
    $all = Join-Path $OutDir 'all.log'
    $writerCache = @{}
    function Get-Writer([string] $path) {
        if (-not $writerCache.ContainsKey($path)) {
            $writerCache[$path] = [System.IO.StreamWriter]::new($path, $true, [System.Text.Encoding]::UTF8)
        }
        return $writerCache[$path]
    }
    try {
        # the log may not exist yet: the monitor can be up before the server
        # creates it
        $waitUntil = (Get-Date).AddSeconds(60)
        while (-not (Test-Path $StdoutLog) -and (Get-Date) -lt $waitUntil `
                -and -not (Test-Path $CancelFlag)) {
            Start-Sleep -Milliseconds 250
        }
        $pos = [long]$StartOffset
        $pendingBytes = [byte[]]::new(0)
        while (-not (Test-Path $CancelFlag)) {
            if (-not (Test-Path $StdoutLog)) { Start-Sleep -Milliseconds 250; continue }
            try {
                $stream = [System.IO.File]::Open($StdoutLog, [System.IO.FileMode]::Open,
                    [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
            } catch { Start-Sleep -Milliseconds 250; continue }
            try {
                if ($stream.Length -lt $pos) {
                    # truncated or rotated underneath us: start over from the
                    # beginning rather than sitting past the end forever
                    $pos = 0
                    $pendingBytes = [byte[]]::new(0)
                }
                $avail = [int]($stream.Length - $pos)
                if ($avail -le 0) {
                    # nothing new since the last poll. The loop's own sleep is
                    # at the BOTTOM of the body and is only reached when there
                    # is data to copy, so the idle case -- most of any session,
                    # while the player is just standing around -- has to sleep
                    # here as well. Without it the poll spins
                    # open/seek/close as fast as the CPU allows and pins a core
                    # for the whole session.
                    Start-Sleep -Milliseconds 250
                    continue
                }
                $stream.Seek($pos, [System.IO.SeekOrigin]::Begin) | Out-Null
                $chunk = [byte[]]::new($avail)
                [void]$stream.Read($chunk, 0, $avail)
                $allBytes = [byte[]]($pendingBytes + $chunk)
                # Decode only up to the last newline. What follows it is a
                # partial line and possibly a partial multi-byte char, so it
                # stays as raw bytes until the next poll completes it -- this is
                # what keeps a UTF-8 log from being garbled at read boundaries.
                $lastNl = -1
                for ($i = $allBytes.Length - 1; $i -ge 0; $i--) {
                    if ($allBytes[$i] -eq 0x0A) { $lastNl = $i; break }
                }
                if ($lastNl -lt 0) {
                    $pendingBytes = [byte[]]$allBytes
                    continue
                }
                $text = [System.Text.Encoding]::UTF8.GetString($allBytes, 0, $lastNl + 1)
                $pos += $lastNl + 1
                if ($allBytes.Length - 1 -gt $lastNl) {
                    $pendingBytes = [byte[]]($allBytes[($lastNl + 1)..($allBytes.Length - 1)])
                } else {
                    $pendingBytes = [byte[]]::new(0)
                }
                # CRLF (java/Out-File on Windows) and LF both
                $lines = $text -split "`r`n|`n"
                if ($lines.Count -gt 1 -and "$($lines[-1])" -eq '') {
                    $lines = $lines[0..($lines.Count - 2)]
                }
                foreach ($line in $lines) {
                    if (Test-Path $CancelFlag) { break }
                    (Get-Writer $all).WriteLine($line)
                    foreach ($k in $patterns.Keys) {
                        if ($k -eq 'error') {
                            # a telemetry body that says "error" is not a
                            # server error
                            if ((Test-Telemetry $line) -or ($line -notmatch $patterns.error)) { continue }
                        }
                        elseif ($line -notmatch $patterns[$k]) { continue }
                        (Get-Writer $writers[$k]).WriteLine($line)
                    }
                }
            } finally {
                $stream.Dispose()
            }
            Start-Sleep -Milliseconds 250
        }
    } catch { }
    finally {
        # the cancel flag is set or the job was force-stopped; whatever made it
        # into the writers has to be on disk before this job goes away
        foreach ($p in @($writerCache.Keys)) {
            try { $writerCache[$p].Flush(); $writerCache[$p].Dispose() } catch { }
        }
    }
}
# A CancellationTokenSource's token does not survive Start-Job's CLIXML
# serialization -- see the tail script -- so the loop is flagged through a file
# both processes can see instead. Cleared at the top so a flag left behind by a
# monitor that died mid-session cannot make the next tail job exit immediately.
$cancelFlag = Join-Path $OutDir '.tail-cancel'
Remove-Item -Path $cancelFlag -Force -ErrorAction SilentlyContinue
# $tokenSource = [System.Threading.CancellationTokenSource]::new()
$tailJob = Start-Job -ScriptBlock $tailScript -ArgumentList $StdoutLog, $OutDir, $patterns, $cancelFlag, $StartOffset

$dumpDir = Join-Path $env:LOCALAPPDATA 'CrashDumps'
$null = New-Item -ItemType Directory -Force -Path $dumpDir
$dumpBase = @(@(Get-ChildItem -Path $dumpDir -Filter '*.dmp' -ErrorAction SilentlyContinue).Name)

# ------------------------------------------------------------------- wait --- #

$proc = Get-Process -Id $GamePid -ErrorAction SilentlyContinue
$exePath = if ($proc) { $proc.Path } else { $null }
$gameName = if ($proc) { $proc.ProcessName } else { $GameExe }

# poll instead of WaitForExit on the .NET object: Get-Process's Process.ExitCode
# throws for a process this script did not start, so the code is read from a
# kernel handle opened here while the game was still alive. The poll interval
# is invisible at this granularity.
$exitCode = $null
$sawAlive = $false
$kHandle = [IntPtr]::Zero
while ($true) {
    $proc = Get-Process -Id $GamePid -ErrorAction SilentlyContinue
    if ($proc) {
        $sawAlive = $true
        if ($kHandle -eq [IntPtr]::Zero) {
            # SYNCHRONIZE (0x100000) to wait on it, PROCESS_QUERY_LIMITED_INFORMATION
            # (0x1000) to read the code -- both granted to any user, no elevation
            $kHandle = [ProcHandle]::OpenProcess(0x00100000 -bor 0x1000, $false, $GamePid)
        }
        Start-Sleep -Milliseconds 500
        continue
    }
    if ($kHandle -ne [IntPtr]::Zero) {
        # the PID is gone; the kernel object is signalled once it is fully dead.
        # A code of 259 (STILL_ACTIVE) here means the wait gave up, not that the
        # game is running.
        $kDeadline = (Get-Date).AddSeconds(10)
        while ((Get-Date) -lt $kDeadline) {
            if ([ProcHandle]::WaitForSingleObject($kHandle, 500) -eq 0) {
                $code = [uint32]0
                [void][ProcHandle]::GetExitCodeProcess($kHandle, [ref]$code)
                $exitCode = [int]$code
                break
            }
        }
        [void][ProcHandle]::CloseHandle($kHandle)
        $kHandle = [IntPtr]::Zero
    }
    break
}
$endedAt = Get-Date

# The handle above covers the code; the event log's WER records are the
# crash-vs-clean distinction and catch kills the process never got to report.
$werAfter = @()
try {
    $werAfter = @(Get-WinEvent -FilterHashtable @{ LogName = 'Application'; Id = 1000, 1001 } `
            -ErrorAction SilentlyContinue |
        Where-Object { $_.Message -match [regex]::Escape($GameExe) -and $_.TimeCreated -ge $startedAt } |
        Select-Object TimeCreated, Id, Message)
} catch { }

# the log keeps arriving for a beat after the process dies (buffered packets,
# the session-close handlers); give it a moment before cutting the tail job
Start-Sleep -Seconds 3
# $tokenSource.Cancel()
# The sentinel makes the tail loop notice and exit on its own; Wait-Job then
# returns at once instead of burning its whole 10s timeout the way it did when
# the token was inert. Remove-Job -Force stays as the backstop, and it still
# runs the job's finally, so no buffered line is lost either way.
'' | Out-File -FilePath $cancelFlag -Encoding UTF8
$null = Wait-Job $tailJob -Timeout 10
$null = Remove-Job $tailJob -Force
Remove-Item -Path $cancelFlag -Force -ErrorAction SilentlyContinue

# ---------------------------------------------------------------- harvest --- #

# The unhandled-opcode lines are the proto backlog: each one names a packet the
# client already sends and the server does not read. Collect them deduped so
# the list is "what is still missing" rather than "what was sent often".
$harvestPath = Join-Path $OutDir 'harvest-opcodes.txt'
$harvest = [System.Collections.Generic.SortedSet[string]]::new()
$unhandledPath = Join-Path $OutDir 'unhandled.log'
if (Test-Path $unhandledPath) {
    foreach ($line in Get-Content $unhandledPath -Encoding UTF8) {
        if ($line -match '(\w+) \(\d+\) arrived and nothing handles it') {
            $null = $harvest.Add($Matches[1])
        }
    }
}
if ($harvest.Count -gt 0) {
    $harvest | Out-File -FilePath $harvestPath -Encoding UTF8
}

# the patch log's last session: which slots swapped, when, whether detach
# restored them. Read from the end because the log accumulates across runs.
$patchEvents = @()
if (Test-Path $PatchLog) {
    $patchEvents = @(Get-Content $PatchLog -Encoding UTF8 |
        Where-Object { $_ -match 'swap\]' } |
        Select-Object -Last 12)
}

$stranded = @()
# The swapped DLLs live in YuanShen_Data\Plugins, not next to the exe, so
# globbing the exe's own parent directory -- the game root -- found nothing and
# the auto-fix below never fired. $proc.Path is also empty when the game had
# already exited before this script polled it, which is itself the
# unclean-exit case most likely to have stranded a copy, so fall back to the
# same install resolution dev.ps1 used.
$gameDir = if ($exePath) { Split-Path -Parent $exePath } else { $null }
if (-not $gameDir) {
    try { $gameDir = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $RepoDir 'tools\game_path.ps1') } catch { }
}
$plugins = if ($gameDir) { Join-Path $gameDir 'YuanShen_Data\Plugins' } else { $null }
if ($plugins -and (Test-Path $plugins)) {
    $stranded = @(Get-ChildItem -Path (Join-Path $plugins '*.lunagc-live*') -ErrorAction SilentlyContinue)
}

$dumpNew = @()
$dumpNow = @(Get-ChildItem -Path $dumpDir -Filter '*.dmp' -ErrorAction SilentlyContinue)
if ($dumpNow) {
    $dumpNew = @($dumpNow | Where-Object { $dumpBase -notcontains $_.Name })
}

# the error bucket minus lines that turned out to be telemetry after all
$errorCount = 0
$errorPath = Join-Path $OutDir 'error.log'
if (Test-Path $errorPath) {
    $errorCount = @(Get-Content $errorPath -Encoding UTF8 |
        Where-Object { $_ -notmatch $patterns.telemetry }).Count
}

# the index is the one-glance summary of the session: each bucket and how many
# lines it caught. Same labels the report's files section uses.
$labels = @{
    unhandled = 'opcodes with no handler, with field dumps'
    error     = 'server ERROR/Exception/SEVERE lines'
    telemetry = "the client's own SuperDebug fault reports"
    quest     = 'quest acceptance and completion'
}
Write-Index @('unhandled', 'error', 'telemetry', 'quest') $labels

# -------------------------------------------------------------- remediation -- #

$fixes = @()
if (-not $SkipAutoFix -and $stranded.Count -gt 0) {
    # detach did not run, so the slot holds the stock DLL and the next launch
    # is the unpatched client. Re-running the deploy puts the patched image
    # back and drops the stranded copy; it is reversible (the pristine backup
    # is what `task patch:reset` restores from).
    $fixes += 'stranded live copy after an unclean exit - re-running the patch deploy'
    try {
        $out = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
            (Join-Path $RepoDir 'tools\patch_game.ps1') -Mode apply 2>&1
        $out | Select-Object -Last 6 | Out-File -Append -FilePath (Join-Path $OutDir 'autofix.log') -Encoding UTF8
        $fixes += 'patch deploy complete; the client is patched for the next launch'
    } catch {
        $fixes += "patch deploy FAILED: $($_.Exception.Message) -- run 'task patch' by hand"
    }
}

# ----------------------------------------------------------------- report --- #

$duration = ($endedAt - $startedAt).TotalSeconds
# A non-zero exit code is a crash even when WER logged nothing and no dump
# appeared: WER LocalDumps is not configured on this box (see the header), so a
# process that dies on an unhandled fault can leave the code it exited with as
# the only evidence. 259 is STILL_ACTIVE and means the wait gave up, not a
# crash; null means the handle was never opened and the code is unknown rather
# than clean.
$exitCrashed = $null -ne $exitCode -and $exitCode -ne 0 -and $exitCode -ne 259
$crashed = $exitCrashed -or $werAfter.Count -gt 0 -or $dumpNew.Count -gt 0
$verdict = if ($crashed) { 'CRASHED' }
           elseif ($errorCount -gt 0) { 'ERRORS' }
           else { 'CLEAN' }

$report = [System.Text.StringBuilder]::new()
$null = $report.AppendLine("# dev session report")
$null = $report.AppendLine("")
$null = $report.AppendLine("game      : $gameName (PID $GamePid)")
$null = $report.AppendLine("verdict   : $verdict")
$null = $report.AppendLine("started   : $(Get-Date $startedAt -Format 'yyyy-MM-dd HH:mm:ss')")
$null = $report.AppendLine("ended     : $(Get-Date $endedAt -Format 'yyyy-MM-dd HH:mm:ss')  (${duration}s)")
$null = $report.AppendLine("")
$null = $report.AppendLine("## outcome")
$null = $report.AppendLine("")
$null = $report.AppendLine("- exit code        : $(if ($null -ne $exitCode) { '0x' + ([uint32]$exitCode).ToString('X8') } elseif ($sawAlive) { 'not captured (the process exited but never signalled within 10s -- a kernel handle held past death)' } else { 'not captured (the PID was gone before the first poll, ~500ms after launch -- the game died during startup, or the PID is wrong)' })")
$null = $report.AppendLine("- WER events       : $($werAfter.Count) during this session (baseline $werBase)")
foreach ($w in $werAfter) {
    $null = $report.AppendLine("    - $(Get-Date $w.TimeCreated -Format 'HH:mm:ss')  event $($w.Id)")
}
$null = $report.AppendLine("- new crash dumps  : $($dumpNew.Count)")
foreach ($d in $dumpNew) { $null = $report.AppendLine("    - $($d.Name)  ($($d.Length) bytes)") }
$null = $report.AppendLine("- server errors    : $errorCount")
$null = $report.AppendLine("- unhandled opcodes: $($harvest.Count)")
$null = $report.AppendLine("")
if ($fixes.Count -gt 0) {
    $null = $report.AppendLine("## remediations applied")
    $null = $report.AppendLine("")
    foreach ($f in $fixes) { $null = $report.AppendLine("- $f") }
    $null = $report.AppendLine("")
}
$null = $report.AppendLine("## patch log (last $($patchEvents.Count) swap lines)")
$null = $report.AppendLine("")
if ($patchEvents.Count -eq 0) {
    $null = $report.AppendLine("none -- $PatchLog is missing, so the patch DLL never loaded in this session")
} else {
    foreach ($e in $patchEvents) { $null = $report.AppendLine("    $e") }
}
$null = $report.AppendLine("")
$null = $report.AppendLine("## files")
$null = $report.AppendLine("")
$null = $report.AppendLine("- all.log              every log line the session produced")
$null = $report.AppendLine("- error.log            server ERROR/Exception/SEVERE lines")
$null = $report.AppendLine("- unhandled.log        opcodes with no handler, with field dumps")
$null = $report.AppendLine("- harvest-opcodes.txt  the same, deduped -- the proto/handler backlog")
$null = $report.AppendLine("- telemetry.log        the client's own SuperDebug fault reports")
$null = $report.AppendLine("- quest.log            quest acceptance and completion")
$null = $report.AppendLine("- autofix.log          output of the remediations above")

$reportPath = Join-Path $OutDir 'report.md'
$report.ToString() | Out-File -FilePath $reportPath -Encoding UTF8

# the latest report is also stable-named so `task dev:report` does not have to
# glob, and the timestamped copy keeps the history
$stamp = Get-Date $startedAt -Format 'yyyyMMdd-HHmmss'
Copy-Item -Path $reportPath -Destination (Join-Path $OutDir "report-$stamp.md") -Force

# a machine-readable summary for anything scripting against this
$summary = [ordered] @{
    verdict       = $verdict
    gamePid       = $GamePid
    startedAt     = (Get-Date $startedAt -Format 'o')
    endedAt       = (Get-Date $endedAt -Format 'o')
    durationSec   = [math]::Round($duration, 1)
    exitCode      = if ($null -ne $exitCode) { '0x' + ([uint32]$exitCode).ToString('X8') } else { $null }
    werEvents     = $werAfter.Count
    crashDumps    = @($dumpNew | ForEach-Object { $_.Name })
    serverErrors  = $errorCount
    unhandled     = @($harvest)
    fixes         = @($fixes)
}
$summary | ConvertTo-Json -Depth 4 | Out-File -FilePath (Join-Path $OutDir 'session.json') -Encoding UTF8

Write-Host ("[dev] session ended after {0}s -- verdict {1}" -f [math]::Round($duration, 1), $verdict)
Write-Host ("[dev] report: {0}" -f $reportPath)
