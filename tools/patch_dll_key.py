# Replace the 1024-bit passport public key baked into AccountPlatNative.dll
# with the public half of tools/_key1024_pub.pem.
#
# The replacement is length-preserving by construction (both literals are
# exactly 268 bytes), so no RVA/pointer shifts occur and the trailing NUL
# padding after the literal is untouched.

import shutil

DLL_DIR = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0\YuanShen_Data\Plugins"
DLL    = DLL_DIR + r"\AccountPlatNative.dll"
BACKUP = DLL + r".lunagc-bak2"          # current (localtest-patched) state
PUB    = r"D:\Projects\Experiment\LunaGC_7.0.0\tools\_key1024_pub.pem"

OLD = (b"-----BEGIN PUBLIC KEY-----\n"
       b"MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDDvekdPMHN3AYhm/vktJT+YJr7cI5Dc"
       b"sNKqdsx5DZX0gDuWFuIjzdwButrIYPNmRJ1G8ybDIF7oDW2eEpm5sMbL9zs9ExXCdvqrn"
       b"51qELbqj0XxtMTIpaCHFSI50PfPpTFV9Xt/hmyVwokoOXFlAEgCn+QCgGs52bFoYMtyi+x"
       b"EQIDAQAB"
       b"\n-----END PUBLIC KEY-----")

NEW = open(PUB, "rb").read()

assert len(OLD) == 268, len(OLD)
assert len(NEW) == 268, len(NEW)
assert OLD != NEW

b = open(DLL, "rb").read()
assert b.count(OLD) == 1, b.count(OLD)
off = b.find(OLD)
assert off == 0x6C9480, hex(off)

shutil.copy2(DLL, BACKUP)
print("backup -> %s (%d bytes)" % (BACKUP, len(b)))

nb = b.replace(OLD, NEW, 1)
assert len(nb) == len(b)
assert nb.count(NEW) == 1
assert nb[:off] == b[:off] and nb[off + 268:] == b[off + 268:]

open(DLL, "wb").write(nb)

# re-verify from disk
v = open(DLL, "rb").read()
assert len(v) == len(b)
assert v[off:off + 268] == NEW
assert v.find(OLD) < 0
print("patched @0x%08X  file size unchanged (%d bytes)" % (off, len(v)))
print("  old tail: ...%s" % OLD[-40:])
print("  new tail: ...%s" % NEW[-40:])
