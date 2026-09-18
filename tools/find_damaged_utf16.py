# The earlier ASCII-only byte scan found "The client is damaged, please reinstall the
# client" nowhere.  IL2CPP/Unity strings in the native binaries are frequently stored
# UTF-16LE, which an ASCII search misses entirely.  This re-hunts the sentence in both
# encodings, ASCII and UTF-16LE, over every DLL in YuanShen_Data/Plugins plus the exe.

import os

GAME = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0"
PLUGINS = os.path.join(GAME, "YuanShen_Data", "Plugins")

NEEDLES = [
    "client is damaged",
    "damaged",
    "reinstall",
    "client is broken",
    "corrupt",
    # Chinese equivalents the CN build would more likely embed
    "客户端已损坏",
    "客户端损坏",
    "请重新安装",
    "重新安装客户端",
    "安装包损坏",
    "客户端文件",
]

targets = []
if os.path.isdir(PLUGINS):
    for fn in sorted(os.listdir(PLUGINS)):
        if fn.lower().endswith((".dll", ".exe")):
            targets.append(os.path.join(PLUGINS, fn))
targets.append(os.path.join(GAME, "YuanShen.exe"))

# pre-compute ascii + utf-16le forms
forms = []
for n in NEEDLES:
    forms.append((n, n.encode("ascii", "ignore")))
    forms.append((n + " [utf16]", n.encode("utf-16-le")))

hits = 0
for p in targets:
    try:
        buf = open(p, "rb").read()
    except Exception as e:
        print("skip %s (%s)" % (p, e))
        continue
    for label, needle in forms:
        if not needle:
            continue
        i = buf.find(needle)
        while i >= 0:
            ctx = buf[max(0, i - 60):i + len(needle) + 80]
            printable = bytes(b if 32 <= b < 127 else 46 for b in ctx)
            print("HIT  %s :: %r at 0x%X" % (os.path.basename(p), label, i))
            print("     ctx=%r" % printable)
            hits += 1
            nxt = buf.find(needle, i + 1)
            if nxt < 0:
                break
            i = nxt
print("done targets=%d hits=%d" % (len(targets), hits))
