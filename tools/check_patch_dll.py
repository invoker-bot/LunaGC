import re

BUILT = r"D:\Projects\Experiment\LunaGC_7.0.0\patch\target\release\Astrolabe.dll"
DEPLOYED = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0\YuanShen_Data\Plugins\Astrolabe.dll"

for name, p in (("built", BUILT), ("deployed", DEPLOYED)):
    d = open(p, "rb").read()
    print("%-9s %d bytes" % (name, len(d)))
    for s in [b"Redirect: ", b"Failed to load winhttp", b"winhttp.dll", b"Astrolabe_orig"]:
        print("   %-24s %d" % (s.decode(), len(re.findall(re.escape(s), d))))
    print()
