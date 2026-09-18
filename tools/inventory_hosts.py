import re, collections

DLL = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0\YuanShen_Data\Plugins\AccountPlatNative.dll"
d = open(DLL, "rb").read()

hosts = collections.Counter()
for m in re.finditer(rb"https?://[a-z0-9][a-z0-9.\-:]*", d):
    hosts[m.group(0).decode()] += 1

print("host constants in AccountPlatNative.dll:")
for h, n in sorted(hosts.items()):
    print("  %4dx  %s" % (n, h))
