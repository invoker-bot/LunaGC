import re

DLL = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0\YuanShen_Data\Plugins\AccountPlatNative.dll"
d = open(DLL, "rb").read()

for pat in [b"lunagc", b"pre-passport-api", b"passport-api"]:
    print("%-20s %d" % (pat.decode(), len(re.findall(re.escape(pat), d))))

for m in re.finditer(rb"lunagc[\x20-\x7e\x00]{0,96}", d):
    s = m.group(0).split(b"\x00")[0]
    print("LUNAGC @0x%08X  %r" % (m.start(), s))

for m in re.finditer(rb"https?://pre-passport-api[\x20-\x7e]{0,80}", d):
    s = m.group(0).split(b"\x00")[0]
    print("PRE   @0x%08X  %r" % (m.start(), s))
