# Exhaustive scan of the game tree -- every file, every extension, no size
# limit, no extension filter.  Earlier scans restricted extensions and so
# never looked at .sys / .cat / .inf / .ini / .loc / .bytes / .dat / .xml.
# "The client is damaged, please reinstall the client" has to live somewhere.

import os, re

ROOT = r"C:\Users\InvokerBot\AppData\Local\genshin"
NEEDLES = [
    b"client is damaged",
    b"please reinstall the client",
    b"reinstall the client",
    "客户端已损坏".encode(),
    "请重新安装客户端".encode(),
    "重新安装客户端".encode(),
    "客户端损坏".encode(),
    b"is damaged",
]

def hits(buf):
    out = []
    for n in NEEDLES:
        i = buf.find(n)
        if i >= 0:
            out.append((n, i))
    return out

scanned = 0
found = 0
for dirpath, dirnames, filenames in os.walk(ROOT):
    for fn in filenames:
        p = os.path.join(dirpath, fn)
        try:
            with open(p, "rb") as f:
                buf = f.read()
        except Exception:
            continue
        scanned += 1
        # also UTF-16LE
        for variant, tag in ((buf, "utf8"), (buf.decode("utf-8", "ignore").encode("utf-16-le", "ignore"), "utf16")):
            hs = hits(variant)
            if not hs:
                continue
            for n, i in hs:
                found += 1
                a = max(0, i - 90)
                ctx = variant[a:i + len(n) + 90]
                print("%s [%s] %s @0x%X %r" % (p, tag, n, i, ctx))
                if found > 60:
                    raise SystemExit

print("scanned=%d hit-lines=%d" % (scanned, found))
