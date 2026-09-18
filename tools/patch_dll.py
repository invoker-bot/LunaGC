import os, re, sys, shutil

DLL = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0\YuanShen_Data\Plugins\AccountPlatNative.dll"
BAK = DLL + ".lunagc-bak"

# All replacements are length-preserving, so no RVA shifts and NUL terminators stay put.
# *.localtest.me resolves to 127.0.0.1 by wildcard DNS; hosts entry is the offline fallback.
REPLACEMENTS = [
    # prod host set ( .rdata 0x6BF430-0x6BFD60 )
    (b"https://passport-api.mihoyo.com",      b"http://lunagc.localtest.me:8088"),      # 31 == 31
    # pre-prod host set ( .rdata 0x6BFBD0-0x6C03B0+ ) - the one the 7.0.0 test client actually uses
    (b"https://pre-passport-api.mihoyo.com",  b"http://lunagc-pre.localtest.me:8088"),  # 35 == 35
]

d = open(DLL, "rb").read()
print("loaded %s  %d bytes" % (os.path.basename(DLL), len(d)))

for t, r in REPLACEMENTS:
    print("\n%-38s len=%d  ->  %-36s len=%d  ok=%s"
          % (t.decode(), len(t), r.decode(), len(r), len(t) == len(r)))
    offs = [m.start() for m in re.finditer(re.escape(t), d)]
    print("  hits:", len(offs))
    for o in offs[:8]:
        s = o
        while s > 0 and d[s - 1] != 0:
            s -= 1
        e = d.index(b"\x00", o)
        print("    match@0x%08X  str@0x%08X  [%d]  %s"
              % (o, s, e - s, d[s:e].decode(errors="replace")))
    if len(offs) > 8:
        print("    ... and %d more" % (len(offs) - 8))

if "--apply" in sys.argv:
    if not os.path.exists(BAK):
        shutil.copy2(DLL, BAK)
        print("\nbackup written:", BAK)
    else:
        print("\nbackup already exists:", BAK)
    out = bytearray(d)
    n = 0
    for t, r in REPLACEMENTS:
        if len(t) != len(r):
            print("REFUSING: length mismatch for %s" % t.decode())
            sys.exit(1)
        for m in re.finditer(re.escape(t), out):
            out[m.start():m.start() + len(t)] = r
            n += 1
    open(DLL, "wb").write(bytes(out))
    print("patched %d occurrence(s)" % n)
