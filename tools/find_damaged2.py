# Broad hunt for the "client is damaged" dialog text.
# ASCII + UTF-16LE, English + Chinese variants, across user profile and
# Program Files.  Skips huge files (CEF caches, unity asset bundles > 200MB).

import os, sys

ROOTS = [
    r"C:\Users\InvokerBot\AppData\Local",
    r"C:\Users\InvokerBot\AppData\LocalLow",
    r"C:\Users\InvokerBot\AppData\Roaming",
    r"C:\Program Files",
    r"C:\Program Files (x86)",
]

NEEDLES = [
    b"client is damaged",
    b"Client is damaged",
    b"please reinstall the client",
    b"damaged, please reinstall",
    b"reinstall the client",
    # Chinese variants (the CN client may localise it)
    "客户端已损坏".encode("utf-8"),
    "客户端已损坏".encode("utf-16-le"),
    "请重新安装客户端".encode("utf-8"),
    "请重新安装客户端".encode("utf-16-le"),
    "重新安装客户端".encode("utf-8"),
    "重新安装客户端".encode("utf-16-le"),
    "客户端损坏".encode("utf-8"),
    "客户端损坏".encode("utf-16-le"),
]

EXTS = (".exe", ".dll", ".pak", ".txt", ".log", ".json", ".xml", ".ini",
        ".js", ".html", ".htm", ".loc", ".csv", ".bytes", ".cfg", ".config",
        ".resx", ".resources", ".dat", ".db", ".sqlite")
MAXBYTES = 200 * 1024 * 1024

hits = []
scanned = 0
for root in ROOTS:
    if not os.path.isdir(root):
        continue
    for dirpath, dirnames, filenames in os.walk(root):
        # skip noisy caches
        dirnames[:] = [d for d in dirnames if d.lower() not in
                       ("temp", "__pycache__", "cache", "code cache",
                        "gpu cache", "shader cache", "jump listicons")]
        for fn in filenames:
            if not fn.lower().endswith(EXTS):
                continue
            p = os.path.join(dirpath, fn)
            try:
                if os.path.getsize(p) > MAXBYTES:
                    continue
                b = open(p, "rb").read()
            except Exception:
                continue
            scanned += 1
            for n in NEEDLES:
                i = b.find(n)
                if i >= 0:
                    hits.append("%s @0x%X needle=%r ctx=%r"
                                % (p, i, n[:24], b[max(0, i-80):i+140]))

fp = r"D:\Projects\Experiment\LunaGC_7.0.0\tools\_damaged_hits2.txt"
with open(fp, "w", encoding="utf-8") as f:
    f.write("scanned=%d hits=%d\n" % (scanned, len(hits)))
    f.write("\n".join(hits))
print("scanned=%d hits=%d -> %s" % (scanned, len(hits), fp))
