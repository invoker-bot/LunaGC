# PE checksum handling for the patched AccountPlatNative.dll.
#
# Finding: the pristine, shipped DLL already carries a STALE checksum
# (stored 0x008E54A4 vs CheckSumMappedFile 0x008EA31D) -- mihoyo rewrites the
# URL strings post-link without fixing the field, and the game runs fine with
# it, so nothing in the load chain verifies it.  Our URL patch must not change
# this field: leaving it at the original shipped value keeps the header
# byte-identical to the pristine file, minimising the diff surface.
#
# This script restores that original value (pe_checksum.py's first pass had
# overwritten it with a recomputed value, which was the wrong call).

import struct

DLL = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0\YuanShen_Data\Plugins\AccountPlatNative.dll"
ORIG_CHECKSUM = 0x008E54A4   # value in the pristine .lunagc-bak3

b = bytearray(open(DLL, "rb").read())
pe_off = struct.unpack_from("<I", b, 0x3C)[0]
ck_off = pe_off + 24 + 64
cur = struct.unpack_from("<I", b, ck_off)[0]
if cur == ORIG_CHECKSUM:
    print("checksum already at original 0x%08X, no change" % ORIG_CHECKSUM)
else:
    struct.pack_into("<I", b, ck_off, ORIG_CHECKSUM)
    open(DLL, "wb").write(bytes(b))
    print("checksum 0x%08X -> 0x%08X (restored to shipped value)" % (cur, ORIG_CHECKSUM))
