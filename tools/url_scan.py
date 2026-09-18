import io, re

PL = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0\YuanShen_Data\Plugins"
new = open(PL + r"\AccountPlatNative.dll", "rb").read()
old = open(PL + r"\AccountPlatNative.dll.lunagc-bak", "rb").read()
out = io.open(r"D:\Projects\Experiment\LunaGC_7.0.0\tools\_urls.txt", "w", encoding="utf-8")

rx = re.compile(rb"https?://[A-Za-z0-9_.:\-]+/[A-Za-z0-9_./\-]*")

out.write("=== patched (current) URLs containing ltest/lunagc/127.0.0.1 ===\n")
seen = set()
for m in rx.finditer(new):
    s = m.group(0)
    if b"ltest" in s or b"lunagc" in s or b"127.0.0.1" in s or b"localhost" in s:
        if s not in seen:
            seen.add(s)
            out.write("  @0x%X  %s\n" % (m.start(), s.decode("ascii", "replace")))

out.write("\n=== original URLs for the same endpoints (passport / mdk / granter) ===\n")
seen2 = set()
for m in rx.finditer(old):
    s = m.group(0)
    if re.search(rb"passport|mdk/shield|granter|combo|aigis|verifier", s):
        if s not in seen2:
            seen2.add(s)
            out.write("  @0x%X  %s\n" % (m.start(), s.decode("ascii", "replace")))

out.write("\n=== ALL hosts referenced by patched dll ===\n")
hosts = {}
for m in re.finditer(rb"https?://([A-Za-z0-9_.:\-]+)/", new):
    h = m.group(1)
    hosts[h] = hosts.get(h, 0) + 1
for h, c in sorted(hosts.items(), key=lambda kv: -kv[1]):
    out.write("  %-45s x%d\n" % (h.decode("ascii", "replace"), c))
out.close()
print("ok")
