"""Resolve a faulting ntdll.dll RVA to an exported function name.

The WER event log only gives 'ntdll.dll + 0xfb77'. ntdll on this machine is
C:\\Windows\\System32\\ntdll.dll (same build as the one that crashed), so
walking its export table tells us which function contains that RVA -- which
in turn tells us what kind of operation faulted.
"""
import struct
import sys

TARGET = int(sys.argv[1], 16) if len(sys.argv) > 1 else 0xFB77
PATH = sys.argv[2] if len(sys.argv) > 2 else r"C:\Windows\System32\ntdll.dll"

d = open(PATH, "rb").read()
pe = struct.unpack_from("<I", d, 0x3C)[0]
coff = pe + 4
f_num_sections = struct.unpack_from("<H", d, coff + 2)[0]
size_opt = struct.unpack_from("<H", d, coff + 16)[0]
opt = coff + 20
magic = struct.unpack_from("<H", d, opt)[0]
dd = opt + (112 if magic == 0x20B else 96)  # data directories

sects = []
so = opt + size_opt
for _ in range(f_num_sections):
    name = d[so:so + 8].rstrip(b"\0").decode()
    vsize, vaddr, rawsize, rawptr = struct.unpack_from("<IIII", d, so + 8)
    sects.append((name, vaddr, vsize, rawptr, rawsize))
    so += 40


def r2f(rva):
    for name, vaddr, vsize, rawptr, rawsize in sects:
        if vaddr <= rva < vaddr + max(vsize, rawsize):
            return rawptr + (rva - vaddr)
    return None


exp_rva, exp_size = struct.unpack_from("<II", d, dd)  # dir 0 = export
e = r2f(exp_rva)
# Export dir: Characteristics, TimeDateStamp, Major, Minor, Name, Base,
#             NumberOfFunctions, NumberOfNames, AF, AN, ANO
(_char, _ts, _maj, _min, _lib_name, base, n_func, n_names,
 af, an, ano) = struct.unpack_from("<IIHHIIIIIII", d, e)

# (function RVA, name), resolved via name -> ordinal -> address
funcs = []
for i in range(n_names):
    name_rva = struct.unpack_from("<I", d, r2f(an) + 4 * i)[0]
    name = d[r2f(name_rva):d.index(b"\0", r2f(name_rva))].decode()
    ordinal = struct.unpack_from("<H", d, r2f(ano) + 2 * i)[0]
    func_rva = struct.unpack_from("<I", d, r2f(af) + 4 * ordinal)[0]
    # skip forwarders
    if exp_rva <= func_rva < exp_rva + exp_size:
        continue
    funcs.append((func_rva, name))

funcs.sort()
best = None
for rva, name in funcs:
    if rva <= TARGET and (best is None or rva > best[0]):
        best = (rva, name)

print("target RVA        : 0x%X" % TARGET)
if best:
    print("containing export : %s @ 0x%X" % (best[1], best[0]))
    print("offset into func  : +0x%X" % (TARGET - best[0]))
print("exports within +-0x3000:")
for rva, name in funcs:
    if abs(rva - TARGET) < 0x3000:
        print("   0x%-8X %s" % (rva, name))
