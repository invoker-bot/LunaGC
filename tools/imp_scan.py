import os, io

ROOT = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0"
OUT = r"D:\Projects\Experiment\LunaGC_7.0.0\tools\_imports2.txt"
KEYS = [b"winhttp.dll", b"wininet.dll", b"Astrolabe.dll", b"astrolabe.dll",
        b"ws2_32.dll", b"WinHTTP", b"nssocket", b"curl"]

out = io.open(OUT, "w", encoding="utf-8")
targets = []
for fn in sorted(os.listdir(ROOT)):
    p = os.path.join(ROOT, fn)
    if os.path.isfile(p) and fn.lower().endswith((".dll", ".exe", ".sys")):
        targets.append(p)
PL = os.path.join(ROOT, "YuanShen_Data", "Plugins")
for fn in sorted(os.listdir(PL)):
    p = os.path.join(PL, fn)
    if os.path.isfile(p) and fn.lower().endswith((".dll", ".exe")):
        targets.append(p)
MG = os.path.join(ROOT, "YuanShen_Data", "Managed")
for root, dirs, files in os.walk(MG):
    for fn in files:
        if fn.lower().endswith(".dll"):
            targets.append(os.path.join(root, fn))

for p in targets:
    try:
        with open(p, "rb") as f:
            head = f.read(0x200000)   # imports live in headers/early sections
    except OSError:
        continue
    hits = []
    for k in KEYS:
        if k in head:
            hits.append(k.decode("ascii", "replace"))
    if hits:
        out.write("%-70s %s\n" % (os.path.relpath(p, ROOT), ", ".join(hits)))
out.close()
print("done")
