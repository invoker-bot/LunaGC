# ---------------------------------------------------------------------------
#  Deploy / restore the client patch.
#
#  patch_game.ps1 -Mode apply   -> install the patch into the game directory
#  patch_game.ps1 -Mode reset   -> restore the pristine client files
#  patch_game.ps1 -Mode status  -> report current state, change nothing
#
#  Two files are touched, both under <game>\YuanShen_Data\Plugins :
#
#    Astrolabe.dll          the stock anti-cheat DLL.  Replaced by the Rust
#                           patch build (patch/target/release/ext.dll), which
#                           proxies the original exports to Astrolabe_orig.dll.
#                           Pristine copy kept as Astrolabe.dll.lunagc-bak.
#
#    AccountPlatNative.dll  the CN passport SDK.  Two independent transforms:
#                             (1) its hardcoded passport URLs are rewritten
#                                 (byte-length preserving) to point at the
#                                 local server;
#                             (2) the 1024-bit RSA public key the SDK encrypts
#                                 account/password with is replaced by the
#                                 public half of the server's own
#                                 src/main/resources/keys/passport_1024.der.
#                           Without (2) the server cannot decrypt the login
#                           payload and answers "Unable to decrypt account".
#                           Pristine copy kept as .lunagc-bak.
#
#  The URL rewrite is done as a single stage -- the mihoyo host is replaced by
#  the RFC-3986 userinfo form "xxxx...@127.0.0.1:8088" -- because v2rayN owns
#  the system proxy override and silently erases any localtest.me bypass entry.
#  The userinfo padding keeps the host byte length, so no RVA shifts.
#
#  The key replacement is length-preserving by construction: both the original
#  and the replacement are a 1024-bit SubjectPublicKeyInfo PEM emitted as a
#  single base64 line with no trailing newline, which is exactly 268 bytes.
#  The replacement is DERIVED at apply time from the server private key, so the
#  client can never be handed a public key the server cannot decrypt.
#
#  All byte scanning is done by decoding the file as ASCII and using
#  string.IndexOf: ASCII decoding is a strict 1-byte/char mapping, so a match
#  offset in the string is also the byte offset in the file.  Needles are pure
#  ASCII, so this is exact and ~1000x faster than a per-byte PowerShell loop.
# ---------------------------------------------------------------------------
[CmdletBinding()]
param(
    [Parameter()]
    [ValidateSet('apply', 'reset', 'status')]
    [string] $Mode = 'status'
)

$ErrorActionPreference = 'Stop'

# Load this PowerShell installation's utility cmdlets explicitly so Get-FileHash
# is available when the script is invoked through Task.
Import-Module (Join-Path $PSHOME 'Modules\Microsoft.PowerShell.Utility\Microsoft.PowerShell.Utility.psd1') -ErrorAction Stop

$repo = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

# --- locate the game -------------------------------------------------------

$game = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repo 'tools\game_path.ps1')
if (-not $game -or $LASTEXITCODE -ne 0) {
    throw "Could not resolve the game path. Set GAME_PATH in .env or install the miHoYo launcher."
}
$plugins = Join-Path $game 'YuanShen_Data\Plugins'

$astrolabe     = Join-Path $plugins 'Astrolabe.dll'
$astrolabeBak  = Join-Path $plugins 'Astrolabe.dll.lunagc-bak'
$astrolabeStale = Join-Path $plugins 'Astrolabe.dll.lunagc-bak.stale'
$astrolabeLive = Join-Path $plugins 'Astrolabe.dll.lunagc-live'
$astrolabeOrig = Join-Path $plugins 'Astrolabe_orig.dll'
$extDll        = Join-Path $repo 'patch\target\release\ext.dll'

$plat    = Join-Path $plugins 'AccountPlatNative.dll'
$platBak = Join-Path $plugins 'AccountPlatNative.dll.lunagc-bak'
$platStale = Join-Path $plugins 'AccountPlatNative.dll.lunagc-bak.stale'
# swap.rs parks the SDK's patched image here once the login flow has mapped it
# -- same contract as $astrolabeLive, one per swapped slot
$platLive = Join-Path $plugins 'AccountPlatNative.dll.lunagc-live'

# The server-side private key whose public half the client must encrypt with.
$passportDer = Join-Path $repo 'src\main\resources\keys\passport_1024.der'

$ascii = [System.Text.Encoding]::ASCII

# --- helpers ---------------------------------------------------------------

# Occurrence offsets of an ASCII needle in a byte array.
function Find-Bytes([byte[]] $blob, [byte[]] $needle) {
    $hay = $ascii.GetString($blob)
    $n = $ascii.GetString($needle)
    $out = @()
    $start = 0
    while ($true) {
        $idx = $hay.IndexOf($n, $start)
        if ($idx -lt 0) { break }
        $out += $idx
        $start = $idx + $n.Length
    }
    return , $out
}

# The Rust patch build embeds the proxy target name; the stock anti-cheat DLL
# never references it, so this alone identifies a patched file.
$patchMarker = $ascii.GetBytes('Astrolabe_orig.dll')
$urlMarker   = $ascii.GetBytes('127.0.0.1:8088')

function Test-PatchBuild([byte[]] $blob) {
    return (Find-Bytes $blob $patchMarker).Count -gt 0
}

function Test-UrlPatched([byte[]] $blob) {
    return (Find-Bytes $blob $urlMarker).Count -gt 0
}

# The stock 1024-bit passport public key baked into AccountPlatNative.dll.
# Replaced by the public half of $passportDer, which the server can decrypt.
$mihoPassportKey = $ascii.GetBytes(
    "-----BEGIN PUBLIC KEY-----`n" +
    "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDDvekdPMHN3AYhm/vktJT+YJr7cI5Dc" +
    "sNKqdsx5DZX0gDuWFuIjzdwButrIYPNmRJ1G8ybDIF7oDW2eEpm5sMbL9zs9ExXCdvqrn" +
    "51qELbqj0XxtMTIpaCHFSI50PfPpTFV9Xt/hmyVwokoOXFlAEgCn+QCgGs52bFoYMtyi+x" +
    "EQIDAQAB" +
    "`n-----END PUBLIC KEY-----")

# --- minimal DER reader/writer ---------------------------------------------
# Windows PowerShell 5.1 ships .NET Framework, whose RSA.Create() gives an
# RSACryptoServiceProvider with no PKCS#8 support, so the private key is
# walked by hand instead.  Only what RSA needs: read the modulus and exponent
# INTEGERs out of the PKCS#8 PrivateKeyInfo, then re-emit them inside a
# SubjectPublicKeyInfo.  Both are fixed shapes, so no library is required.

function Read-DerTlv([byte[]] $data, [int] $offset) {
    if ($offset -ge $data.Length) { throw "DER: truncated at $offset" }
    $tag = $data[$offset]
    $pos = $offset + 1
    if ($pos -ge $data.Length) { throw "DER: length byte missing" }
    $b = $data[$pos]; $pos++
    if ($b -band 0x80) {
        $n = $b -band 0x7F
        if ($n -gt 4) { throw "DER: length field too long ($n bytes)" }
        $len = 0
        for ($i = 0; $i -lt $n; $i++) {
            if ($pos -ge $data.Length) { throw "DER: truncated length" }
            $len = ($len -shl 8) -bor $data[$pos]; $pos++
        }
    } else {
        $len = $b
    }
    if (($pos + $len) -gt $data.Length) { throw "DER: content overruns the buffer" }
    return @{ Tag = $tag; Start = $pos; Length = $len; End = $pos + $len }
}

# Everything below writes into a System.Collections.Generic.List[byte] rather
# than returning arrays from functions: PowerShell unwraps a returned array in
# the pipeline, so a 1-element result arrives at the caller as a scalar and a
# 2-byte length field arrives as two separate bytes.  Passing the list to be
# written into sidesteps the whole class of bugs.

function Append-DerLength($list, [int] $len) {
    if ($len -lt 0x80) { [void] $list.Add($len); return }
    if ($len -le 0xFF) { [void] $list.Add(0x81); [void] $list.Add($len); return }
    [void] $list.Add(0x82)
    [void] $list.Add($len -shr 8)
    [void] $list.Add($len -band 0xFF)
}

function Append-DerTlv($list, [byte] $tag, [byte[]] $content) {
    [void] $list.Add($tag)
    Append-DerLength $list $content.Length
    [void] $list.AddRange($content)
}

# The OID 1.2.840.113549.1.1.1 (rsaEncryption) as it appears inside a
# PrivateKeyInfo, used to confirm the .der really is an RSA key before its
# integers are copied into a SubjectPublicKeyInfo.  Only the OID is matched --
# some PKCS#8 emitters omit the trailing NULL parameter, and it is irrelevant
# here because the rebuilt SubjectPublicKeyInfo always emits one.
$rsaAlgId = [byte[]](0x30, 0x0D, 0x06, 0x09, 0x2A, 0x86, 0x48, 0x86,
                     0xF7, 0x0D, 0x01, 0x01, 0x01, 0x05, 0x00)
$rsaOidPrefix = [byte[]](0x06, 0x09, 0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x01)

# Derive the 268-byte single-line PEM literal for the public half of an
# unencrypted PKCS#8 RSA private key, matching the byte layout of the literal
# the SDK stores: header line, ONE base64 line, footer line, no trailing
# newline.  A standard multi-line PEM is 4 bytes longer and would shift every
# RVA after it.
function Get-PassportPublicKeyLiteral {
    if (-not (Test-Path $passportDer)) {
        throw "Server private key not found: $passportDer -- the server would be unable to decrypt login payloads."
    }
    $der = [System.IO.File]::ReadAllBytes($passportDer)

    $outer = Read-DerTlv $der 0
    if ($outer.Tag -ne 0x30) { throw "PKCS#8: outer value is not a SEQUENCE" }
    $p = $outer.Start
    $ver = Read-DerTlv $der $p;  $p = $ver.End      # INTEGER 0
    $alg = Read-DerTlv $der $p;  $p = $alg.End      # AlgorithmIdentifier
    $oct = Read-DerTlv $der $p                      # OCTET STRING (RSAPrivateKey)
    if ($alg.Length -lt $rsaOidPrefix.Length) { throw "PKCS#8: AlgorithmIdentifier too short" }
    for ($i = 0; $i -lt $rsaOidPrefix.Length; $i++) {
        if ($der[$alg.Start + $i] -ne $rsaOidPrefix[$i]) { throw "PKCS#8: algId is not rsaEncryption" }
    }

    $pk = Read-DerTlv $der $oct.Start               # RSAPrivateKey SEQUENCE
    $q = $pk.Start
    $v2  = Read-DerTlv $der $q; $q = $v2.End        # INTEGER 0
    $mod = Read-DerTlv $der $q; $q = $mod.End       # INTEGER modulus
    $exp = Read-DerTlv $der $q                      # INTEGER publicExponent

    # RSAPublicKey ::= SEQUENCE { modulus, publicExponent }.  The INTEGER
    # contents are copied verbatim -- both are already minimal, correctly
    # sign-padded DER -- and re-wrapped in fresh TLVs.
    $rsaPubContent = New-Object System.Collections.Generic.List[byte]
    Append-DerTlv $rsaPubContent 0x02 ($der[$mod.Start..($mod.End - 1)])
    Append-DerTlv $rsaPubContent 0x02 ($der[$exp.Start..($exp.End - 1)])

    $rsaPub = New-Object System.Collections.Generic.List[byte]
    Append-DerTlv $rsaPub 0x30 $rsaPubContent.ToArray()

    # SubjectPublicKeyInfo ::= SEQUENCE { AlgorithmIdentifier, BIT STRING }.
    # The BIT STRING content is a single 0 unused-bits byte followed by the
    # RSAPublicKey.  The algorithm identifier is the fixed rsaEncryption +
    # NULL above, which is what a standard emitter produces.
    $content = New-Object System.Collections.Generic.List[byte]
    [void] $content.AddRange($rsaAlgId)
    [void] $content.Add(0x03)
    Append-DerLength $content (1 + $rsaPub.Count)
    [void] $content.Add(0)
    [void] $content.AddRange($rsaPub)

    $spki = New-Object System.Collections.Generic.List[byte]
    Append-DerTlv $spki 0x30 $content.ToArray()

    $b64 = [Convert]::ToBase64String($spki.ToArray())
    $literal = $ascii.GetBytes("-----BEGIN PUBLIC KEY-----`n$b64`n-----END PUBLIC KEY-----")
    if ($literal.Length -ne $mihoPassportKey.Length) {
        throw "passport_1024.der derives a $($literal.Length)-byte literal but the SDK slot is $($mihoPassportKey.Length) bytes -- the replacement must be the same length or RVAs shift. Use a 1024-bit key."
    }
    if ($literal.Length -ne 268) { throw "Unexpected replacement key length: $($literal.Length)" }
    return $literal
}

$passportKeyLiteral = $null
function Test-KeyPatched([byte[]] $blob) {
    if ($null -eq $script:passportKeyLiteral) {
        $script:passportKeyLiteral = Get-PassportPublicKeyLiteral
    }
    return (Find-Bytes $blob $script:passportKeyLiteral).Count -gt 0
}

function Test-PlatPatched([byte[]] $blob) {
    # Either transform alone counts as "not pristine": a DLL that is
    # URL-redirected but still holds the miHoYo key is not a usable state.
    return (Test-UrlPatched $blob) -or (Test-KeyPatched $blob)
}

function Copy-File([string] $src, [string] $dst) {
    [System.IO.File]::Copy($src, $dst, $true)
}

# Byte-length preserving URL rewrite of the passport SDK DLL.
function Edit-PlatUrls([byte[]] $blob) {
    # host          | original (len)                        | replacement (len)
    # pre-passport  | https://pre-passport-api.mihoyo.com 35 | http://xxxxxxxxxxxxx@127.0.0.1:8088 35
    # passport      | https://passport-api.mihoyo.com     31 | http://xxxxxxxxx@127.0.0.1:8088    31
    $pairs = @(
        ,@($ascii.GetBytes('https://pre-passport-api.mihoyo.com'),
           $ascii.GetBytes('http://xxxxxxxxxxxxx@127.0.0.1:8088'))
        ,@($ascii.GetBytes('https://passport-api.mihoyo.com'),
           $ascii.GetBytes('http://xxxxxxxxx@127.0.0.1:8088'))
    )

    # The pre-passport host contains "passport-api.mihoyo.com" as a substring,
    # so the longer needle must be consumed first, otherwise the second pass
    # would rewrite the tail the first pass already handled.
    $out = [byte[]]::new($blob.Length)
    [Array]::Copy($blob, $out, $blob.Length)

    $total = 0
    foreach ($pair in $pairs) {
        $old = $pair[0]
        $new = $pair[1]
        if ($old.Length -ne $new.Length) {
            throw ("Length mismatch: {0} ({1}) vs {2} ({3}) -- refusing to rewrite, RVAs would shift" `
                -f ($ascii.GetString($old)), $old.Length, ($ascii.GetString($new)), $new.Length)
        }
        $offs = Find-Bytes $out $old
        foreach ($o in $offs) {
            [Array]::Copy($new, 0, $out, $o, $new.Length)
        }
        Write-Host ("  rewrote {0,3} x {1}" -f $offs.Count, ($ascii.GetString($old)))
        $total += $offs.Count
    }
    return @{ Blob = $out; Count = $total }
}

# Byte-length preserving replacement of the SDK's 1024-bit passport public key.
function Edit-PlatKey([byte[]] $blob, [byte[]] $newKey) {
    $offs = Find-Bytes $blob $mihoPassportKey
    if ($offs.Count -eq 0) {
        # Already replaced, or this is not the client the literal was pinned to.
        if ((Find-Bytes $blob $newKey).Count -gt 0) {
            Write-Host "  passport key already replaced"
            return @{ Blob = $blob; Count = 0; Already = $true }
        }
        throw "The stock passport public key was not found in AccountPlatNative.dll -- wrong client version?"
    }
    if ($offs.Count -gt 1) {
        throw "The stock passport public key occurs $($offs.Count) times; expected exactly 1."
    }
    $out = [byte[]]::new($blob.Length)
    [Array]::Copy($blob, $out, $blob.Length)
    [Array]::Copy($newKey, 0, $out, $offs[0], $newKey.Length)
    Write-Host ("  replaced 1024-bit passport key @ 0x{0:X}" -f $offs[0])
    return @{ Blob = $out; Count = 1; Already = $false }
}

# A backup is only usable for reset if it is itself pristine.  A backup taken
# from an already-patched DLL would make reset install the patch instead of
# removing it, so a stale one is quarantined rather than trusted.
function Test-BackupUsable([string] $bak, [scriptblock] $isPatched) {
    if (-not (Test-Path $bak)) { return $false }
    return -not (& $isPatched ([System.IO.File]::ReadAllBytes($bak)))
}

function Backup-Label([string] $bak, [scriptblock] $isPatched) {
    if (-not (Test-Path $bak)) { return 'missing' }
    if (-not (& $isPatched ([System.IO.File]::ReadAllBytes($bak)))) { return 'pristine' }
    return 'STALE (holds a patched build)'
}

# Ensure a pristine backup of a file exists before it is modified.  A backup
# that itself holds a patched build is quarantined rather than trusted.
function Protect-Original([string] $current, [string] $bak, [string] $stale,
                          [bool] $currentIsPristine, [scriptblock] $isPatched) {
    if (-not $currentIsPristine) {
        # The file is already partly patched, so a pristine backup must have been
        # captured by an earlier run; do not clobber it, and do not recapture.
        if (-not (Test-BackupUsable $bak $isPatched)) {
            throw "Cannot patch: $current is already modified and there is no pristine backup at $bak."
        }
        Write-Host "  pristine backup already present, not overwriting"
        return
    }
    if (Test-Path $bak) {
        if (Test-BackupUsable $bak $isPatched) {
            Write-Host "  pristine backup already present, not overwriting"
            return
        }
        Copy-File $bak $stale
        Write-Host "  existing backup holds a patched build, quarantined -> $stale"
    }
    Copy-File $current $bak
    Write-Host "  captured pristine backup -> $bak"
}

# Removes every parked live copy for one slot name. swap.rs prefers the plain
# '.lunagc-live' name, but when an older session is still mapped over it the
# delete fails and the running session parks its image under
# '.lunagc-live.<pid>' instead -- so sweep both spellings. A file that is still
# locked by a live process is left in place; the error is reported per file
# rather than aborting the patch.
function Remove-LiveCopies([string] $slotName) {
    $pattern = Join-Path $plugins ($slotName + '.lunagc-live*')
    $removed = 0
    foreach ($f in @(Get-ChildItem -Path $pattern -File -ErrorAction SilentlyContinue)) {
        try {
            Remove-Item $f.FullName -Force -ErrorAction Stop
            Write-Host ("  dropped stale live copy -> {0}" -f $f.Name)
            $removed++
        } catch {
            Write-Host ("  live copy {0} is locked by a running session, left in place" -f $f.Name)
        }
    }
    return ,$removed
}

# --- status ----------------------------------------------------------------

$astState = 'missing'
$astBytes = $null
if (Test-Path $astrolabe) {
    $astBytes = [System.IO.File]::ReadAllBytes($astrolabe)
    $astState = if (Test-PatchBuild $astBytes) { 'patched' } else { 'stock' }
}

$platState = 'missing'
$platBytes = $null
$platUrlDone = $false
$platKeyDone = $false
if (Test-Path $plat) {
    $platBytes = [System.IO.File]::ReadAllBytes($plat)
    $platUrlDone = Test-UrlPatched $platBytes
    $platKeyDone = Test-KeyPatched $platBytes
    $platState = if ($platUrlDone -and $platKeyDone) { 'patched' }
                 elseif ($platUrlDone -or $platKeyDone) { 'PARTIAL' }
                 else { 'stock' }
}

$astBakLabel = Backup-Label $astrolabeBak { param($b) Test-PatchBuild $b }
$platBakLabel = Backup-Label $platBak { param($b) Test-PlatPatched $b }

$extState = if (Test-Path $extDll) { 'built' } else { 'missing' }

Write-Host "game path      : $game"
Write-Host ("Astrolabe.dll  : {0}  ({1} bytes)" -f $astState, $(if ($astBytes) { $astBytes.Length } else { 0 }))
Write-Host ("  backup       : {0}" -f $astBakLabel)

# swap.rs parks the running patched image here the instant the DLL loads, and
# puts it back at DLL_PROCESS_DETACH.  A leftover means the last session died
# hard (crash or TerminateProcess -- detach never runs), so the on-disk slot
# still holds the signed stock DLL and the NEXT launch would boot the unpatched
# client ("account or password error").  Reported so that state is visible.
# A running client changes what these readings mean: swap.rs has already parked
# the patched image here and the on-disk slot holds the stock build for as long
# as the session lasts.  Reported per-state so the two cases are not confused.
$gamePids = @(Get-Process -Name YuanShen -ErrorAction SilentlyContinue)
$inSession = $gamePids.Count -gt 0

if (Test-Path $astrolabeLive) {
    if ($inSession) {
        Write-Host ("  live copy    : LIVE -- the running client has the patched image")
        Write-Host ("                  mapped; the slot restores itself on exit")
    } else {
        Write-Host ("  live copy    : PRESENT -- last session died while swapped,")
        Write-Host ("                  on-disk slot holds stock; the next launch is unpatched")
    }
}
# swap.rs swaps the SDK slot too (engage_apn), so the same debris exists there
if (Test-Path $platLive) {
    if ($inSession) {
        Write-Host ("  live copy    : LIVE -- same, the SDK slot restores itself on exit")
    } else {
        Write-Host ("  live copy    : PRESENT -- same: the SDK slot is stock on disk,")
        Write-Host ("                  a fresh login would reach the real passport servers")
    }
}
if ((Get-ChildItem -Path (Join-Path $plugins '*.lunagc-live.*') -File -ErrorAction SilentlyContinue).Count -gt 0) {
    Write-Host ("  per-process  : PRESENT -- a live copy from a session that ran while")
    Write-Host ("                  another was still mapped; cleared on the next patch")
}

# The patched DLL forwards every Astrolabe_* export into Astrolabe_orig.dll, so
# that file has to be byte-identical to the pristine build of THIS client.  The
# game updates Astrolabe.dll between patch runs and a stale proxy target is
# silent: same export names, older internals, and the anti-cheat's stack
# unwinder faults ~100s into the session.
$origState = if (Test-Path $astrolabeOrig) { 'present' } else { 'missing' }
if (Test-Path $astrolabeOrig) {
    $origRef = if ($astState -eq 'stock') { $astrolabe }
               elseif (Test-Path $astrolabeBak) { $astrolabeBak }
               else { $null }
    if ($origRef -and (Test-Path $origRef)) {
        if ((Get-FileHash $astrolabeOrig -Algorithm MD5).Hash -eq `
            (Get-FileHash $origRef    -Algorithm MD5).Hash) {
            $origState = 'present, matches pristine'
        } else {
            $origState = 'STALE -- does not match the pristine build; crash likely'
        }
    }
}
Write-Host ("  orig proxy   : {0}" -f $origState)
Write-Host ("AccountPlatNat : {0}  ({1} bytes)" -f $platState, $(if ($platBytes) { $platBytes.Length } else { 0 }))
Write-Host ("  urls         : {0}" -f $(if ($platUrlDone) { 'redirected' } else { 'stock' }))
Write-Host ("  passport key : {0}" -f $(if ($platKeyDone) { 'server key' } else { 'miHoYo key -- login will fail' }))
Write-Host ("  backup       : {0}" -f $platBakLabel)
Write-Host "ext.dll (repo) : $extState"

if ($Mode -eq 'status') {
    # Non-zero when the install is not launch-ready, so a gate or another
    # script can trust the exit code instead of parsing the lines above.
    # This is the same definition of "good enough to play" as the blocker list
    # in tools/dev.ps1 -- if you change one, change the other.
    # Stock slots, stock URLs and a present live copy are all correct while a
    # client is running -- the patched images are parked, not lost.  Only flag
    # a slot when it is stock AND no live copy explains it, and only call a
    # live copy stranded when no game owns it.
    $astLiveThere = Test-Path $astrolabeLive
    $platLiveThere = Test-Path $platLive
    if ($inSession) {
        Write-Host ''
        Write-Host ("client running (PID {0}) -- slots are swapped in-process," -f ($gamePids.Id -join ','))
        Write-Host 'stock readings above are the live state, not faults'
    }
    $problems = @()
    if (-not (($astState  -eq 'patched') -or ($inSession -and $astLiveThere)))  { $problems += "Astrolabe.dll : $astState" }
    if (-not (($platState -eq 'patched') -or ($inSession -and $platLiveThere))) { $problems += "AccountPlatNative : $platState" }
    if ($astBakLabel -ne 'pristine') { $problems += "Astrolabe backup : $astBakLabel" }
    if ($platBakLabel -ne 'pristine') { $problems += "AccountPlatNative backup : $platBakLabel" }
    if ($origState -like 'STALE*') { $problems += "orig proxy : $origState" }
    if ($platBytes -and -not $platKeyDone -and -not ($inSession -and $platLiveThere)) { $problems += 'passport key not swapped' }
    if ($platBytes -and -not $platUrlDone -and -not ($inSession -and $platLiveThere)) { $problems += 'dispatch URLs not redirected' }
    if ($astLiveThere  -and -not $inSession) { $problems += 'stranded live copy (Astrolabe) -- the last session died swapped' }
    if ($platLiveThere -and -not $inSession) { $problems += 'stranded live copy (AccountPlatNative) -- same' }
    if ($extState -eq 'missing') { $problems += 'ext.dll not built -- run `task build:patch`' }
    if ($problems.Count -gt 0) {
        Write-Host ''
        Write-Host ("not launch-ready ({0}):" -f $problems.Count)
        $problems | ForEach-Object { Write-Host "  $_" }
        exit 1
    }
    exit 0
}

# --- reset -----------------------------------------------------------------

if ($Mode -eq 'reset') {
    $changed = $false

    # A leftover live copy is the patched image from a session that died while
    # swapped.  reset's job is a pristine client, and the slot already holds the
    # stock DLL in that state, so the parked copy is just debris.
    # ForEach-Object has its own scope, so the count has to be captured here
    # and folded into $changed in *this* scope, or the "already pristine"
    # shortcut below would fire after a real cleanup.
    $droppedAst = Remove-LiveCopies 'Astrolabe.dll'
    $droppedPlat = Remove-LiveCopies 'AccountPlatNative.dll'
    if ($droppedAst.Count -gt 0 -or $droppedPlat.Count -gt 0) { $changed = $true }

    if ($astState -eq 'patched') {
        if (-not (Test-BackupUsable $astrolabeBak { param($b) Test-PatchBuild $b })) {
            throw "Astrolabe.dll is patched but there is no pristine backup at $astrolabeBak -- cannot restore."
        }
        Copy-File $astrolabeBak $astrolabe
        Write-Host "restored Astrolabe.dll from backup"
        $changed = $true
    } else {
        Write-Host "Astrolabe.dll already stock, nothing to do"
    }

    if ($platState -ne 'stock') {
        if (-not (Test-BackupUsable $platBak { param($b) Test-PlatPatched $b })) {
            throw "AccountPlatNative.dll is patched but there is no pristine backup at $platBak -- cannot restore."
        }
        Copy-File $platBak $plat
        Write-Host "restored AccountPlatNative.dll from backup (urls + passport key)"
        $changed = $true
    } else {
        Write-Host "AccountPlatNative.dll already stock, nothing to do"
    }

    if (-not $changed) { Write-Host "client is already pristine" }
    exit 0
}

# --- apply -----------------------------------------------------------------

if ($Mode -eq 'apply') {
    if ($extState -ne 'built') {
        throw "Patch DLL not built: $extDll -- run 'task build:patch' (or 'task build') first."
    }

    # Astrolabe: never back up an already-patched DLL, and never trust a
    # backup that is itself patched -- a stale one gets quarantined so a real
    # pristine copy can be captured.
    if ($astState -eq 'missing') {
        throw "Astrolabe.dll not found at $astrolabe"
    }
    Write-Host "Astrolabe.dll:"
    if ($astState -eq 'patched') {
        Write-Host "  already patched, leaving backup untouched"
    } else {
        Protect-Original $astrolabe $astrolabeBak $astrolabeStale ($astState -eq 'stock') { param($b) Test-PatchBuild $b }
    }

    # The deploy overwrites the slot with the fresh build, so a parked live copy
    # from a session that died while swapped is superseded -- drop it, or the
    # next launch's swap.rs would see two candidates for the live name.
    Remove-LiveCopies 'Astrolabe.dll' | Out-Null

    # The SDK's URL/key rewrite below has the same problem from the other
    # direction: it leaves the slot patched while a live copy from a dead
    # session still points at the patched image, so swap.rs would see two
    # candidates for the live name.
    Remove-LiveCopies 'AccountPlatNative.dll' | Out-Null

    # The patched DLL proxies the original exports, so the original has to be
    # reachable under the name the Rust code looks up.  It also has to be the
    # pristine build of THIS client: the game updates Astrolabe.dll on its own
    # schedule, and a stale Astrolabe_orig.dll forwards the 7.0 client into an
    # older Astrolabe (same export names, different internals) -- the result is
    # an access violation in the exception unwinder about 100s after launch.
    # So: never reuse one that does not byte-match the pristine source.
    $pristineAst = if ($astState -eq 'stock') { $astrolabe } else { $astrolabeBak }
    if (-not (Test-Path $pristineAst)) {
        throw "No pristine Astrolabe source to stage $astrolabeOrig from -- run 'task patch:reset' and re-apply."
    }
    $needStage = -not (Test-Path $astrolabeOrig)
    if (-not $needStage) {
        $needStage = (Get-FileHash $astrolabeOrig -Algorithm MD5).Hash -ne `
                     (Get-FileHash $pristineAst   -Algorithm MD5).Hash
    }
    if ($needStage) {
        Copy-File $pristineAst $astrolabeOrig
        Write-Host "  refreshed proxy target from pristine -> $astrolabeOrig"
    } else {
        Write-Host "  proxy target already matches pristine"
    }

    Copy-File $extDll $astrolabe
    Write-Host "  installed patch DLL -> $astrolabe"

    # AccountPlatNative: same backup discipline, then both transforms.  Each
    # transform is applied only if that specific one is missing, so a file left
    # half-patched by an older patcher is brought up to date without ever
    # recapturing a non-pristine "backup".
    if ($platState -eq 'missing') {
        throw "AccountPlatNative.dll not found at $plat"
    }
    Write-Host "AccountPlatNative.dll:"

    $platIsPristine = -not $platUrlDone -and -not $platKeyDone
    Protect-Original $plat $platBak $platStale $platIsPristine { param($b) Test-PlatPatched $b }

    $blob = $platBytes
    $needWrite = $false

    if (-not $platUrlDone) {
        $r = Edit-PlatUrls $blob
        if ($r.Count -eq 0) {
            throw "No passport URL strings found in AccountPlatNative.dll -- wrong client version?"
        }
        $blob = $r.Blob
        $needWrite = $true
        Write-Host ("  {0} host strings redirected" -f $r.Count)
    } else {
        Write-Host "  urls already redirected"
    }

    if (-not $platKeyDone) {
        $newKey = Get-PassportPublicKeyLiteral
        $r = Edit-PlatKey $blob $newKey
        if (-not $r.Already) {
            $blob = $r.Blob
            $needWrite = $true
        }
    } else {
        Write-Host "  passport key already the server key"
    }

    if ($needWrite) {
        if ($blob.Length -ne $platBytes.Length) {
            throw ("File length changed ({0} -> {1}) -- refusing to write, RVAs would shift" `
                -f $platBytes.Length, $blob.Length)
        }
        [System.IO.File]::WriteAllBytes($plat, $blob)
        Write-Host ("  wrote {0} bytes" -f $blob.Length)
    } else {
        Write-Host "  nothing to do, file is already fully patched"
    }

    Write-Host ""
    Write-Host "patch applied -- start the server with 'task serve'"
    exit 0
}
