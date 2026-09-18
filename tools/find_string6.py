import os, re, io, struct

ROOT = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0"
OUT = r"D:\Projects\Experiment\LunaGC_7.0.0\tools\_damaged_hits5.txt"

def w(s):
    return s.encode("utf-16-le")

PATS = [
    w("damaged"), w("reinstall"), w("corrupt"),
    w("客户端"), w("损坏"), w("重新安装"),
    w("The client"), w("please reinstall"),
    w("client is"), w("已损坏"),
]
RXS = [re.compile(re.escape(p)) for p in PATS]
NAMES = [p.decode("utf-16-le") for p in PATS]

out = io.open(OUT, "w", encoding="utf-8")
n = 0
CH = 48 * 1024 * 1024
OV = 2048

PL = os.path.join(ROOT, "YuanShen_Data", "Plugins")
files = [os.path.join(PL, f) for f in sorted(os.listdir(PL))]
files += [os.path.join(ROOT, "YuanShen.exe"),
          os.path.join(ROOT, "YuanShen_Data", "Managed", "Metadata", "global-metadata.dat")]

for p in files:
    if not os.path.isfile(p):
        continue
    try:
        sz = os.path.getsize(p)
    except OSError:
        continue
    rel = os.path.relpath(p, ROOT)
    fn_hits = 0
    try:
        with open(p, "rb") as f:
            prev = b""
            while True:
                chunk = f.read(CH)
                if not chunk:
                    break
                buf = prev + chunk
                base = f.tell() - len(buf)
                for rx, nm in zip(RXS, NAMES):
                    for m in rx.finditer(buf):
                        s = max(0, m.start() - 160)
                        e2 = min(len(buf), m.end() + 200)
                        n += 1
                        fn_hits += 1
                        raw = buf[s:e2]
                        # decode as utf-16 where possible
                        try:
                            txt = raw.decode("utf-16-le", "replace")
                        except Exception:
                            txt = repr(raw)
                        out.write("\nHIT(W) %s @0x%X [%s]\n" % (rel, base + m.start(), nm))
                        out.write(txt.replace("\x00", "\\0")[:420] + "\n")
                prev = chunk[-OV:] if len(chunk) >= OV else chunk
    except OSError:
        pass
    out.write("\nDONE %s hits=%d\n" % (rel, fn_hits))

out.write("\ntotal hits: %d\n" % n)
out.close()
print("total hits:", n)
