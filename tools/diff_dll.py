import io, sys

PL = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0\YuanShen_Data\Plugins"
a = PL + r"\AccountPlatNative.dll"
b = PL + r"\AccountPlatNative.dll.lunagc-bak"
out = io.open(r"D:\Projects\Experiment\LunaGC_7.0.0\tools\_acct_diff.txt", "w", encoding="utf-8")

da = open(a, "rb").read()
db = open(b, "rb").read()
out.write("patched size %d  orig size %d\n" % (len(da), len(db)))
if len(da) != len(db):
    out.write("SIZE DIFFERS\n")

diffs = []
i = 0
step = 4096
n = min(len(da), len(db))
while i < n:
    if da[i:i + step] != db[i:i + step]:
        j = i
        while j < n and da[j] == db[j]:
            j += 1
        k = j
        while k < n and da[k] != db[k]:
            k += 1
        diffs.append((i, k))
        i = k + 1
    else:
        i += step

out.write("num diff regions: %d\n" % len(diffs))
for s, e in diffs[:30]:
    out.write("  @0x%X..0x%X len=%d\n" % (s, e, e - s))
    out.write("    orig: %s\n" % db[s:e][:96].hex())
    out.write("    new : %s\n" % da[s:e][:96].hex())
    out.write("    ctx : %s\n" % db[max(0, s - 24):s + 96].hex())
out.close()
print("regions:", len(diffs))
