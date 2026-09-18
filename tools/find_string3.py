import os, re, io

ROOT = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0"
OUT = r"D:\Projects\Experiment\LunaGC_7.0.0\tools\_damaged_hits2.txt"

PATS = [
    b"client is damaged",
    b"reinstall the client",
    b"damaged, please",
    b"please reinstall",
    b"\xe5\xae\xa2\xe6\x88\xb7\xe7\xab\xaf",   # 客户端
    b"\xe9\x87\x8d\xe6\x96\xb0\xe5\xae\x89\xe8\xa3\x85",  # 重新安装
    b"\xe6\x8d\x9f\xe5\x9d\x8f",               # 损坏
    b"file is damaged",
    b"files are damaged",
]
RXS = [re.compile(re.escape(p), re.I) for p in PATS]

out = io.open(OUT, "w", encoding="utf-8")
n = 0
CH = 48 * 1024 * 1024
OV = 2048
skipped = 0
for dirpath, dirnames, filenames in os.walk(ROOT):
    # skip huge irrelevant dirs to save time
    for fn in filenames:
        p = os.path.join(dirpath, fn)
        try:
            sz = os.path.getsize(p)
        except OSError:
            continue
        if sz == 0:
            continue
        ext = os.path.splitext(fn)[1].lower()
        # skip asset bundles / bulk data that obviously won't hold text
        try:
            with open(p, "rb") as f:
                prev = b""
                while True:
                    chunk = f.read(CH)
                    if not chunk:
                        break
                    buf = prev + chunk
                    base = f.tell() - len(buf)
                    for rx in RXS:
                        for m in rx.finditer(buf):
                            s = max(0, m.start() - 200)
                            e2 = min(len(buf), m.end() + 200)
                            n += 1
                            out.write("\nHIT %s @0x%X [%s]\n" % (
                                os.path.relpath(p, ROOT), base + m.start(),
                                rx.pattern[:40]))
                            out.write(repr(buf[s:e2]) + "\n")
                    prev = chunk[-OV:] if len(chunk) >= OV else chunk
        except OSError:
            skipped += 1

out.write("\ntotal hits: %d  unreadable: %d\n" % (n, skipped))
out.close()
print("total hits:", n, "unreadable:", skipped)
