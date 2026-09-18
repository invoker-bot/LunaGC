# The CN client's statically-linked curl honours the WinINET registry proxy.
# ProxyServer = 127.0.0.1:10809 (V2Ray HTTP inbound), and its routing rules send
# *.localtest.me out through a remote node that cannot loop back to our local
# 8088 -> HTTP 502 + curl error 56, killing loginByPassword (MiHoYoSDK.log line 231).
# Fix: teach the bypass list about our redirect hosts. Reversible via the .bak file.

$ErrorActionPreference = "Stop"
$Key = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings"
$Bak = "D:\Projects\Experiment\LunaGC_7.0.0\tools\_proxy_override.bak"
$Add = @("*.localtest.me", "localtest.me")

$cur = (Get-ItemProperty -Path $Key).ProxyOverride
"[before] ProxyOverride = $cur"

if (-not (Test-Path $Bak)) {
    Set-Content -Path $Bak -Value $cur -Encoding UTF8 -NoNewline
    "backup written: $Bak"
}
else {
    "backup already exists: $Bak (not overwritten)"
}

$entries = $cur -split ';' | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne "" }
foreach ($h in $Add) {
    if ($entries -notcontains $h) { $entries += $h; "appending: $h" }
    else { "already present: $h" }
}
$new = ($entries -join ';')

if ($new -ne $cur) {
    Set-ItemProperty -Path $Key -Name ProxyOverride -Value $new -Type String
    "[after]  ProxyOverride = $new"
}
else {
    "no change needed"
}

# Verify: this exact call reproduced the 502 before the fix.
"`n--- verification: POST loginByPassword via default (proxied) path ---"
$body = '{"account":"x","password":"y","is_crypto":false}'
try {
    $r = Invoke-WebRequest -Uri "http://lunagc.localtest.me:8088/account/ma-cn-passport/app/loginByPassword" `
        -Method POST -Body $body -ContentType "application/json" -TimeoutSec 8 -UseBasicParsing
    "OK  $([int]$r.StatusCode)  $($r.Content)"
}
catch {
    "FAIL $($_.Exception.Message)"
}
