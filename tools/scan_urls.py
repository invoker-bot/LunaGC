import os, re
from collections import Counter

base = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0\YuanShen_Data\Plugins"
for f in ["AccountPlatNative.dll", "HoYoNetworkSDK.dll", "HoYoChannel.dll",
          "HYPass.dll", "HoYoSDKNetworkFallback.dll"]:
    p = os.path.join(base, f)
    if not os.path.exists(p):
        print("missing", f)
        continue
    d = open(p, "rb").read()
    hits = [m.group(0).decode() for m in re.finditer(rb"https?://[A-Za-z0-9_.\-]+", d)]
    c = Counter(hits)
    print("==", f, len(d))
    for u, n in sorted(c.items()):
        if any(k in u for k in ("mihoyo", "hoyoverse", "yuanshen", "127.0.0.1", "localhost")):
            print("   %-55s x%d" % (u, n))
