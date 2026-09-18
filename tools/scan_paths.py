import re

p = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0\YuanShen_Data\Plugins\AccountPlatNative.dll"
d = open(p, "rb").read()

# map each mihoyo host constant to nearby API paths (paths appear as separate constants)
hosts = sorted(
    set(m.group(0).decode() for m in re.finditer(rb"https?://[A-Za-z0-9_.\-]+\.mihoyo\.com", d))
)
print("hosts:", len(hosts))
for h in hosts:
    print("  ", h)

print("\n=== api paths mentioning key flows ===")
paths = sorted(
    set(m.group(0).decode(errors="replace") for m in re.finditer(rb"/[A-Za-z0-9_./\-]*(?:passport|granter|combo|shield|session|account|login|token)[A-Za-z0-9_./\-]*", d))
)
for x in paths:
    print("  ", x)
