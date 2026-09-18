import os, re, sys

ROOT = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0"
DATA = os.path.join(ROOT, "YuanShen_Data")
TARGETS = [
    # il2cpp (unity 2022 layout: <game>_Data/Native/Data/Metadata)
    os.path.join(DATA, "Native", "Data", "Metadata", "global-metadata.dat"),
    os.path.join(DATA, "Native", "Data", "Metadata", "startup-metadata.dat"),
    # game code is linked into the exe, no standalone GameAssembly.dll
    os.path.join(ROOT, "YuanShen.exe"),
    os.path.join(ROOT, "mhypbase.dll"),
    os.path.join(ROOT, "rtlbase.dll"),
    # native account sdk (already patched in place)
    os.path.join(DATA, "Plugins", "AccountPlatNative.dll"),
    # managed/sdk shims
    os.path.join(DATA, "Plugins", "HYPass.dll"),
    os.path.join(DATA, "Plugins", "HoYoNetworkSDK.dll"),
    os.path.join(DATA, "Plugins", "HoYoChannel.dll"),
    os.path.join(DATA, "Plugins", "HoYoSDKNetworkFallback.dll"),
]
PAT = re.compile(rb"passport-api")
# only report hits that still point at the real host
RAW = re.compile(rb"https?://[a-z0-9.\-:]*passport-api[a-z0-9.\-/]*")

for p in TARGETS:
    if not os.path.exists(p):
        print("missing:", p); continue
    d = open(p, "rb").read()
    hits = [m.start() for m in PAT.finditer(d)]
    raw = [m.group(0) for m in RAW.finditer(d)]
    print("%-26s %11d bytes  passport-api: %4d  url-constants: %d"
          % (os.path.basename(p), len(d), len(hits), len(raw)))
    for u in sorted(set(raw))[:12]:
        print("     %s" % u.decode(errors="replace"))
