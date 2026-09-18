import re

DLL = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0\YuanShen_Data\Plugins\AccountPlatNative.dll"
d = open(DLL, "rb").read()

urls = set()
for m in re.finditer(rb"https?://lunagc[a-z0-9.\-:]*[^\x00]*", d):
    urls.add(m.group(0).decode())

for u in sorted(urls):
    print(u)
print("\ntotal:", len(urls))
