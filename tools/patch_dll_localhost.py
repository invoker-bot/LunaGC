# Fallback for when the registry ProxyOverride fix is not honored by the game's curl.
#
# Root cause recap: AccountPlatNative.dll was patched (Fix A) to point at
# http://lunagc.localtest.me:8088 . The game's statically-linked curl honours the
# WinINET system proxy (127.0.0.1:10809, a V2Ray HTTP inbound). localtest.me is a
# public-looking domain, so it is NOT in the proxy bypass list -> the V2Ray client
# routes it out through a remote node that cannot loop back to our local 8088 ->
# HTTP 502 + curl error 56 -> "client is damaged" style login failure.
#
# This patch redirects instead to `localhost`, which the WinINET bypass list covers
# twice over: explicitly ("localhost") and via <local> (dotless hosts).
#
# Length must stay identical so no RVA shifts occur and NUL terminators stay put.
# `userinfo@host` is ignored by curl for the connection: it still connects to
# localhost:8088 and sends the original path with Host: localhost:8088.
#
#   "https://passport-api.mihoyo.com"      (31) -> "http://AAAAAAAAA@localhost:8088"      (31)
#   "https://pre-passport-api.mihoyo.com"  (35) -> "http://AAAAAAAAAAAAA@localhost:8088"  (35)
#
# Usage: python tools/patch_dll_localhost.py          # dry run
#        python tools/patch_dll_localhost.py --apply   # write

import os, re, sys, shutil

DLL = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0\YuanShen_Data\Plugins\AccountPlatNative.dll"
BAK = DLL + ".lunagc-bak2"   # keep the Fix-A (localtest) state as its own backup

REPLACEMENTS = [
    (b"http://lunagc.localtest.me:8088",     b"http://AAAAAAAAA@localhost:8088"),
    (b"http://lunagc-pre.localtest.me:8088", b"http://AAAAAAAAAAAAA@localhost:8088"),
]

d = open(DLL, "rb").read()
print("loaded %s  %d bytes" % (os.path.basename(DLL), len(d)))

total = 0
for t, r in REPLACEMENTS:
    print("\n%-36s len=%d  ->  %-36s len=%d  ok=%s"
          % (t.decode(), len(t), r.decode(), len(r), len(t) == len(r)))
    offs = [m.start() for m in re.finditer(re.escape(t), d)]
    print("  hits:", len(offs))
    for o in offs[:4]:
        s = o
        while s > 0 and d[s - 1] != 0:
            s -= 1
        e = d.index(b"\x00", o)
        print("    match@0x%08X  str@0x%08X  [%d]  %s"
              % (o, s, e - s, d[s:e].decode(errors="replace")))
    if len(offs) > 4:
        print("    ... and %d more" % (len(offs) - 4))
    total += len(offs)

if total == 0:
    print("\nnothing to do: the localtest.me strings are not present "
          "(already converted, or Fix A was never applied)")
    sys.exit(0)

if "--apply" in sys.argv:
    for t, r in REPLACEMENTS:
        if len(t) != len(r):
            print("REFUSING: length mismatch for %s" % t.decode())
            sys.exit(1)
    if not os.path.exists(BAK):
        shutil.copy2(DLL, BAK)
        print("\nbackup written:", BAK)
    else:
        print("\nbackup already exists:", BAK)
    out = bytearray(d)
    n = 0
    for t, r in REPLACEMENTS:
        for m in re.finditer(re.escape(t), out):
            out[m.start():m.start() + len(t)] = r
            n += 1
    open(DLL, "wb").write(bytes(out))
    print("patched %d occurrence(s)" % n)
