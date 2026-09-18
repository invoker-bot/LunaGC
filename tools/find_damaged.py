# Hunt "The client is damaged, please reinstall the client" in ASCII + UTF-16LE.
# Scope: game tree, launcher tree, and the miHoYo AppData dirs.

import os

ROOTS = [
    r"C:\Users\InvokerBot\AppData\Local\genshin",
    r"C:\Users\InvokerBot\AppData\LocalLow\miHoYo",
]

NEEDLES = [b"damaged", b"reinstall", b"Reinstall", b"client is damaged"]
EXTS = (".exe", ".dll", ".pak", ".uasset", ".txt", ".log", ".json", ".xml",
        ".ini", ".loc", ".csv", ".bytes")

def scan(path):
    hits = []
    with open(path, "rb") as f:
        b = f.read()
    for n in NEEDLES:
        # ASCII
        i = 0
        while True:
            i = b.find(n, i)
            if i < 0:
                break
            hits.append((i, b[max(0, i-60):i+90]))
            i += 1
        # UTF-16LE
        u = n.decode("ascii").encode("utf-16-le")
        i = 0
        while True:
            i = b.find(u, i)
            if i < 0:
                break
            hits.append((i, b[max(0, i-120):i+180]))
            i += 1
    return hits

out = []
for root in ROOTS:
    if not os.path.isdir(root):
        continue
    for dirpath, dirnames, filenames in os.walk(root):
        for fn in filenames:
            if not fn.lower().endswith(EXTS):
                continue
            p = os.path.join(dirpath, fn)
            try:
                hits = scan(p)
            except Exception as e:
                continue
            for off, ctx in hits:
                out.append("%s @0x%X %r" % (p, off, ctx))

fp = r"D:\Projects\Experiment\LunaGC_7.0.0\tools\_damaged_hits.txt"
with open(fp, "w", encoding="utf-8") as f:
    f.write("\n".join(out) if out else "(no hits)")
print("hits=%d -> %s" % (len(out), fp))
for line in out[:40]:
    print(line)
