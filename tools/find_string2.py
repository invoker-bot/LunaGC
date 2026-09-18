import os, re, io

ROOT = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0"
PATS = [re.compile(b"client is damaged", re.I),
        re.compile(b"reinstall the client", re.I),
        re.compile(b"damaged", re.I)]

SMALL = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0\YuanShen_Data\Plugins"
OUT = r"D:\Projects\Experiment\LunaGC_7.0.0\tools\_damaged_hits.txt"

out = io.open(OUT, "w", encoding="utf-8")
n = 0
for fn in sorted(os.listdir(SMALL)):
    p = os.path.join(SMALL, fn)
    if not os.path.isfile(p):
        continue
    sz = os.path.getsize(p)
    if sz > 128 * 1024 * 1024:
        out.write("skip big: %s %d\n" % (fn, sz))
        continue
    try:
        d = open(p, "rb").read()
    except OSError as e:
        out.write("err %s %s\n" % (fn, e))
        continue
    for rx in PATS:
        for m in rx.finditer(d):
            s = max(0, m.start() - 300)
            e2 = min(len(d), m.end() + 300)
            n += 1
            out.write("\nHIT %s @0x%X [%s]\n" % (fn, m.start(), rx.pattern))
            out.write(repr(d[s:e2]) + "\n")

out.write("\ntotal hits: %d\n" % n)
out.close()
print("total hits:", n)
