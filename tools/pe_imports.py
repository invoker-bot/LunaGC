import struct, os, sys

PL = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0\YuanShen_Data\Plugins"
EXE = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0\YuanShen.exe"


def imports(path):
    d = open(path, "rb").read()
    e_lfanew = struct.unpack_from("<I", d, 0x3C)[0]
    magic = struct.unpack_from("<H", d, e_lfanew + 24)[0]
    is64 = magic == 0x20B
    nsec = struct.unpack_from("<H", d, e_lfanew + 6)[0]
    opt = e_lfanew + 24
    dd_off = opt + (112 if is64 else 96)
    imp_rva = struct.unpack_from("<I", d, dd_off + 8)[0]
    sec_off = opt + (240 if is64 else 224)

    def rva2off(rva):
        for i in range(nsec):
            s = d[sec_off + i * 40: sec_off + (i + 1) * 40]
            va, vs, pr, ps = struct.unpack_from("<IIII", s, 12)
            if va <= rva < va + max(vs, ps):
                return pr + (rva - va)
        return None

    out = []
    o = rva2off(imp_rva) if imp_rva else None
    if o is None:
        return out
    i = 0
    while i < 500:
        ent = struct.unpack_from("<I", d, o + i * 20)[0]
        if ent == 0:
            break
        name_rva = struct.unpack_from("<I", d, o + i * 20 + 12)[0]
        no = rva2off(name_rva)
        if no is not None:
            end = d.index(b"\x00", no)
            out.append(d[no:end].decode("ascii", "replace").lower())
        i += 1
    return out


targets = [
    "AccountPlatNative.dll", "HYPass.dll", "HoYoSDKNetworkFallback.dll",
    "MiHoYoSDKUploader.dll", "HoYoChannel.dll", "Mmoron.dll", "gmesdk.dll",
    "ZFEmbedWeb.dll", "telemetry.dll",
]
for fn in targets:
    p = os.path.join(PL, fn)
    if not os.path.exists(p):
        continue
    imps = imports(p)
    nets = [x for x in imps if any(k in x for k in ("winhttp", "wininet", "ws2", "wsock", "curl", "schannel", "crypt32", "iphlpapi", "secur32", "ncrypt"))]
    print("%-32s NET: %s" % (fn, ",".join(nets) or "(none)"))
    print("%-32s ALL: %s" % ("", ",".join(imps)[:220]))

print()
print("YuanShen.exe NET:", [x for x in imports(EXE) if any(k in x for k in ("winhttp", "wininet", "ws2", "curl", "schannel", "ncrypt"))])
