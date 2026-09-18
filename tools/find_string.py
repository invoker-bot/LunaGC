import os, re, sys

ROOT = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0"
PATS = [b"client is damaged", b"reinstall the client", b"damaged, please"]

EXTS = (".exe", ".dll", ".bin", ".dat", ".json", ".txt", ".xml", ".cfg")
MAX = 400 * 1024 * 1024

hits = []
for dirpath, dirnames, filenames in os.walk(ROOT):
    for fn in filenames:
        p = os.path.join(dirpath, fn)
        try:
            sz = os.path.getsize(p)
        except OSError:
            continue
        if sz == 0 or sz > MAX:
            continue
        try:
            d = open(p, "rb").read()
        except OSError:
            continue
        for pat in PATS:
            for m in re.finditer(re.escape(pat), d, re.IGNORECASE):
                s = max(0, m.start() - 80)
                e = min(len(d), m.end() + 80)
                ctx = d[s:e].decode("utf-8", errors="replace")
                hits.append((p, m.start(), pat.decode(), ctx))

print("matches:", len(hits))
for p, off, pat, ctx in hits[:60]:
    print("\n%s  @0x%X  (%s)" % (p, off, pat))
    print("   ", ctx.replace("\x00", " "))
