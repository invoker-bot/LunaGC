import os, io

ROOT = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0"
OUT  = r"D:\Projects\Experiment\LunaGC_7.0.0\tools\_deep_scan.txt"

# phase 1: who references "Astrolabe" (i.e. what loads the proxy dll)
# phase 2: the "client is damaged" phrase, utf-8 and utf-16le, in native binaries

TERMS = ["damaged", "reinstall", "corrupt", "integrity", "tamper", "client is damaged",
         u"损坏", u"重新安装", u"客户端", u"完整性", u"被修改", u"重新下载", u"安装包", u"异常"]
PATS = []
for t in TERMS:
    PATS.append(("utf8", t, t.encode("utf-8")))
    PATS.append(("u16",  t, t.encode("utf-16-le")))

targets = []
for fn in sorted(os.listdir(ROOT)):
    p = os.path.join(ROOT, fn)
    if os.path.isfile(p) and fn.lower().endswith((".dll", ".exe", ".sys", ".dat")):
        targets.append(p)
PL = os.path.join(ROOT, "YuanShen_Data", "Plugins")
for fn in sorted(os.listdir(PL)):
    p = os.path.join(PL, fn)
    if os.path.isfile(p) and fn.lower().endswith((".dll", ".exe")):
        targets.append(p)

out = io.open(OUT, "w", encoding="utf-8")
CH, OV = 1 << 20, 64

def chunked(p):
    prev, off = b"", 0
    with open(p, "rb") as f:
        while True:
            b = f.read(CH)
            if not b:
                break
            buf = prev + b
            yield off - len(prev), buf
            prev = buf[-OV:]
            off += len(b)

out.write("=== phase 1: files referencing \"Astrolabe\" ===\n")
for p in targets:
    try:
        n = 0
        for base, buf in chunked(p):
            i = 0
            while True:
                j = buf.find(b"Astrolabe", i)
                if j < 0:
                    break
                n += 1
                i = j + 9
        if n:
            out.write("  %-60s x%d\n" % (os.path.relpath(p, ROOT), n))
            out.flush()
    except OSError:
        pass

out.write("\n=== phase 2: damaged/reinstall phrases ===\n")
for p in targets:
    try:
        seen = {}
        for base, buf in chunked(p):
            for enc, t, pat in PATS:
                i = 0
                while True:
                    j = buf.find(pat, i)
                    if j < 0:
                        break
                    key = (enc, t)
                    if seen.get(key, 0) < 4:
                        ctx = buf[max(0, j - 48):j + 96]
                        try:
                            s = ctx.decode("utf-16-le" if enc == "u16" else "utf-8", "replace")
                        except Exception:
                            s = repr(ctx)
                        out.write("  %-46s @0x%-9X [%s/%s] ...%s...\n" % (
                            os.path.relpath(p, ROOT), base + j, enc, t,
                            " ".join(s.split())))
                        out.flush()
                    seen[key] = seen.get(key, 0) + 1
                    i = j + len(pat)
    except OSError:
        pass
out.close()
print("done")
