# Locate the passport/login URL strings inside AccountPlatNative.dll
# (current patched copy + pristine backup) so we can re-point it at 127.0.0.1.

import re, sys

DLL_DIR = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0\YuanShen_Data\Plugins"

targets = {
    "patched":  DLL_DIR + r"\AccountPlatNative.dll",
    "pristine": DLL_DIR + r"\AccountPlatNative.dll.lunagc-bak",
}

# host-ish ascii runs
PAT = re.compile(rb"[ -~]{8,}")

for label, path in targets.items():
    b = open(path, "rb").read()
    print("=== %s  (%d bytes) ===" % (label, len(b)))
    for needle in (b"localtest", b"127.0.0.1", b"loginByPassword", b"ma-cn-passport"):
        print("  %-18r count=%d" % (needle, b.count(needle)))
    for m in PAT.finditer(b):
        s = m.group(0)
        if (b"localtest" in s or b"loginByPassword" in s
                or b"ma-cn-passport" in s or b"mihoyo.com" in s):
            if len(s) > 300:
                s = s[:300] + b"..."
            print("  @0x%08X  %r" % (m.start(), s))
    print()
