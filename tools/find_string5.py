import os, re, io

ROOT = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0"
OUT = r"D:\Projects\Experiment\LunaGC_7.0.0\tools\_damaged_hits4.txt"

# focused file list: IL2CPP metadata, exe, and all native SDK dlls
TARGETS = [
    r"YuanShen_Data\Managed\Metadata\global-metadata.dat",
    r"YuanShen.exe",
    r"YuanShen_Data\Plugins\AccountPlatNative.dll",
    r"YuanShen_Data\Plugins\HYPass.dll",
    r"YuanShen_Data\Plugins\HoYoSDKNetworkFallback.dll",
    r"YuanShen_Data\Plugins\MiHoYoSDKUploader.dll",
    r"YuanShen_Data\Plugins\gmesdk.dll",
    r"YuanShen_Data\Plugins\mhypbase.dll",
    r"YuanShen_Data\Plugins\Mmoron.dll",
    r"YuanShen_Data\Plugins\HoYoChannel.dll",
    r"YuanShen_Data\Plugins\ZFEmbedWeb.dll",
    r"YuanShen_Data\Plugins\telemetry.dll",
    r"YuanShen_Data\Plugins\Astrolabe.dll",
]

# looser patterns
PATS = [
    b"damaged", b"reinstall", b"corrupt",
    b"\xe6\x8d\x9f\xe5\x9d\x8f",          # 损坏
    b"\xe9\x87\x8d\xe6\x96\xb0\xe5\xae\x89\xe8\xa3\x85",  # 重新安装
    b"\xe5\xae\xa2\xe6\x88\xb7\xe7\xab\xaf",              # 客户端
    b"\xe8\xaf\xb7\xe9\x87\x8d\xe6\x96\xb0",              # 请重新
    b"integrity", b"verify_fail", b"file_check",
]
RXS = [re.compile(re.escape(p), re.I) for p in PATS]

out = io.open(OUT, "w", encoding="utf-8")
n = 0
CH = 48 * 1024 * 1024
OV = 2048

for rel in TARGETS:
    p = os.path.join(ROOT, rel)
    if not os.path.exists(p):
        out.write("MISSING %s\n" % rel)
        continue
    fn_hits = 0
    with open(p, "rb") as f:
        prev = b""
        while True:
            chunk = f.read(CH)
            if not chunk:
                break
            buf = prev + chunk
            base = f.tell() - len(buf)
            for rx in RXS:
                for m in rx.finditer(buf):
                    s = max(0, m.start() - 160)
                    e2 = min(len(buf), m.end() + 160)
                    n += 1
                    fn_hits += 1
                    out.write("\nHIT %s @0x%X [%s]\n" % (rel, base + m.start(), rx.pattern.decode("utf8", "replace")[:40]))
                    out.write(buf[s:e2].decode("utf8", "replace").replace("\x00", "\\0")[:400] + "\n")
            prev = chunk[-OV:] if len(chunk) >= OV else chunk
    out.write("\nDONE %s hits=%d\n" % (rel, fn_hits))

out.write("\ntotal hits: %d\n" % n)
out.close()
print("total hits:", n)
