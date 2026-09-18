# Hunt for a raw (non-base64) RSA public key in the native account SDK.
#
# The client's loginByPassword ciphertext is 128 bytes => a 1024-bit modulus.
# The only base64/PEM key in AccountPlatNative.dll is 2048-bit, so the 1024-bit
# key must sit in .rdata as raw DER/SEMI. Signature to look for:
#
#   02 81 80 <128 bytes modulus> 02 03 01 00 01      (len 0x80 = 128)
#   02 82 00 80 <128 bytes modulus> 02 03 01 00 01
#   30 81 89 02 81 80 ...                             (RSAPublicKey SEQUENCE)
#   30 81 9D 30 0D 06 09 2A 86 48 86 F7 0D 01 01 01  (SubjectPublicKeyInfo 1024)
#
# Also dump the neighbourhood of the known 2048-bit PEM to see what else is there.

import os, re

OUT = r"D:\Projects\Experiment\LunaGC_7.0.0\tools\_raw_key_hits.txt"
DIR = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0\YuanShen_Data\Plugins"

TARGETS = ["AccountPlatNative.dll", "HoYoSDKNetworkFallback.dll",
           "HoYoNetworkSDK.dll", "MiHoYoMTRSDK.dll"]

# modulus + trailing exponent, with an ASN.1 length that implies 128 bytes
PATTERNS = [
    (b"\x02\x81\x80", 2 + 128, "INTEGER len=0x80"),
    (b"\x02\x82\x00\x80", 2 + 2 + 128, "INTEGER len=0x0080"),
    (b"\x30\x81\x89\x02\x81\x80", None, "RSAPublicKey SEQUENCE 1024"),
    (b"\x30\x81\x9d\x30\x0d\x06\x09\x2a\x86\x48\x86\xf7\x0d\x01\x01\x01", None,
     "SubjectPublicKeyInfo 1024"),
    (b"\x30\x81\x92\x30\x0d\x06\x09\x2a\x86\x48\x86\xf7\x0d\x01\x01\x01", None,
     "SubjectPublicKeyInfo 1024 (variant)"),
    # e = 3 (some miHoYo keys historically used e=3) and e=17
    (b"\x02\x03\x01\x00\x01", None, "exponent 65537 trailer"),
    (b"\x02\x01\x03", None, "exponent 3 trailer"),
    (b"\x02\x01\x11", None, "exponent 17 trailer"),
]

out = open(OUT, "w", encoding="utf-8")

for name in TARGETS:
    path = os.path.join(DIR, name)
    if not os.path.exists(path):
        continue
    b = open(path, "rb").read()
    out.write("=== %s  %d bytes ===\n" % (name, len(b)))

    # exponent trailers are the cheapest discriminator: a real RSA key has a
    # 128/256-byte high-entropy blob immediately before them
    for pat, plen, label in PATTERNS:
        start = 0
        hits = []
        while True:
            i = b.find(pat, start)
            if i < 0:
                break
            hits.append(i)
            start = i + 1
            if len(hits) > 4000:
                break
        if not hits:
            continue
        # only report hits preceded by a plausible modulus-sized blob
        interesting = []
        for h in hits:
            if label.startswith("exponent"):
                # look 140 bytes back; require the preceding byte run to look like a
                # big-integer length prefix over random-looking data
                for back in (132, 136, 140, 262, 266, 270):
                    s = h - back
                    if s < 0:
                        continue
                    blob = b[s:h]
                    nz = sum(1 for x in blob if x != 0)
                    if nz > back * 0.9:   # high-entropy
                        interesting.append((s, back, blob))
                        break
            else:
                interesting.append((h, 0, b[h:h + (plen or 24)]))
        out.write("  pattern %-34r hits=%d interesting=%d\n" % (pat.hex(), len(hits), len(interesting)))
        for s, back, blob in interesting[:12]:
            out.write("    @0x%08X  %s\n" % (s, blob[:40].hex()))
    out.write("\n")

# neighbourhood of the known 2048-bit PEM in AccountPlatNative.dll
p = os.path.join(DIR, "AccountPlatNative.dll")
b = open(p, "rb").read()
out.write("=== AccountPlatNative.dll around the 2048-bit PEM @0x6C92C5 ===\n")
lo, hi = 0x6C9200, 0x6C9600
region = b[lo:hi]
# print as mixed ascii/hex, 32 bytes per line
for off in range(0, len(region), 32):
    chunk = region[off:off + 32]
    asc = "".join(chr(c) if 32 <= c < 127 else "." for c in chunk)
    out.write("  0x%08X  %-64s  %s\n" % (lo + off, chunk.hex(), asc))
out.close()
print("done")
