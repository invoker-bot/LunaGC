import struct, io

PL = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0\YuanShen_Data\Plugins"
ORIG = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0\BeyondAssets\BeyondAssistEditor\Astrolabe\Astrolabe.dll"
OUT  = r"D:\Projects\Experiment\LunaGC_7.0.0\tools\_exports.txt"

def exports(path):
    b = open(path, "rb").read()
    e_lfanew = struct.unpack_from("<I", b, 0x3C)[0]
    pe, sig = e_lfanew, struct.unpack_from("<I", b, e_lfanew)[0]
    assert sig == 0x4550, hex(sig)
    coff = pe + 4
    machine = struct.unpack_from("<H", b, coff)[0]
    nsec    = struct.unpack_from("<H", b, coff + 2)[0]
    opt_sz  = struct.unpack_from("<H", b, coff + 16)[0]
    opt     = coff + 20
    magic   = struct.unpack_from("<H", b, opt)[0]
    assert magic == 0x20b, hex(magic)          # PE32+
    exp_rva = struct.unpack_from("<I", b, opt + 112)[0]
    if exp_rva == 0:
        return machine, []
    sec = opt + opt_sz
    rvas = []
    for i in range(nsec):
        s = sec + i * 40
        va  = struct.unpack_from("<I", b, s + 12)[0]
        vsz = struct.unpack_from("<I", b, s + 8)[0]
        raw = struct.unpack_from("<I", b, s + 20)[0]
        rsz = struct.unpack_from("<I", b, s + 16)[0]
        rvas.append((va, vsz, raw, rsz))
    def off(rva):
        for va, vsz, raw, rsz in rvas:
            if va <= rva < va + max(vsz, rsz):
                return raw + (rva - va)
        return None
    e = off(exp_rva)
    n = struct.unpack_from("<I", b, e + 20)[0]          # NumberOfNames
    nf = struct.unpack_from("<I", b, e + 24)[0]         # NumberOfFunctions
    addr_tab = struct.unpack_from("<I", b, e + 28)[0]
    name_ptr = struct.unpack_from("<I", b, e + 32)[0]
    ord_tab  = struct.unpack_from("<I", b, e + 36)[0]
    names = []
    ap = off(name_ptr)
    for i in range(n):
        p = struct.unpack_from("<I", b, ap + 4 * i)[0]
        o = off(p)
        end = b.index(b"\0", o)
        names.append(b[o:end].decode("ascii", "replace"))
    return machine, sorted(names)

out = io.open(OUT, "w", encoding="utf-8")
for tag, path in (("patched proxy", PL + r"\Astrolabe.dll"),
                  ("pristine orig", ORIG)):
    try:
        machine, names = exports(path)
        out.write("=== %s  machine=0x%X  exports=%d ===\n" % (tag, machine, len(names)))
        for n_ in names:
            out.write("  %s\n" % n_)
    except Exception as ex:
        out.write("=== %s  ERROR %r ===\n" % (tag, ex))
out.close()
print("done")
