# Locate the RSA public key that AccountPlatNative.dll uses to encrypt the
# ma-cn-passport account/password fields.
#
# Evidence so far:
#   * the client's ciphertext is 128 bytes -> a 1024-bit RSA public key
#   * patch/server_public_key.bin is 2048-bit -> the on_mhy_rsa replacement
#     (which only fires for a 271-byte key blob, and only inside YuanShen.exe)
#     is NOT what produced this ciphertext
#   * on_mhy_rsa never logged a single "key:"/"len:" line -> passport RSA does
#     not go through mhyrsa_perform_crypto_action at all
#
# So the key must live inside the native account SDK itself. Look for:
#   1. <RSAKeyValue> XML blobs
#   2. long base64 runs (a 1024-bit modulus is 172 base64 chars)
#   3. "mhyrsa" / openssl-style symbols
#
# Output goes to a UTF-8 file (Python's stdout is GBK on this box).

import base64, os, re, sys

OUT = r"D:\Projects\Experiment\LunaGC_7.0.0\tools\_rsa_key_candidates.txt"
DIR = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0\YuanShen_Data\Plugins"

TARGETS = [
    "AccountPlatNative.dll",
    "HoYoNetworkSDK.dll",
    "HoYoSDKNetworkFallback.dll",
    "MiHoYoMTRSDK.dll",
    "MiHoYoSDK.dll",
]

XML_RE = re.compile(rb"<RSAKeyValue>.{0,900}?</RSAKeyValue>", re.S)
B64_RE = re.compile(rb"[A-Za-z0-9+/]{128,400}={0,2}")

out = open(OUT, "w", encoding="utf-8")

for name in TARGETS:
    path = os.path.join(DIR, name)
    if not os.path.exists(path):
        out.write("=== %s : MISSING ===\n" % name)
        continue
    b = open(path, "rb").read()
    out.write("=== %s  %d bytes ===\n" % (name, len(b)))

    # 1. XML key blobs
    for m in XML_RE.finditer(b):
        s = m.group(0)
        out.write("  [XML key] @0x%08X len=%d\n" % (m.start(), len(s)))
        out.write("    %s\n" % s.decode("ascii", "replace")[:600])

    # 2. long base64 runs -> try to interpret as a DER RSA public key
    seen = 0
    for m in B64_RE.finditer(b):
        s = m.group(0)
        if len(s) > 500:
            continue
        try:
            der = base64.b64decode(s, validate=True)
        except Exception:
            continue
        # DER SubjectPublicKeyInfo for RSA starts with 30 82 .. 30 0d 06 09 2a 86 48 86 f7 0d 01 01 01
        if der[:2] == b"\x30\x82" and b"\x2a\x86\x48\x86\xf7\x0d\x01\x01\x01" in der[:40]:
            out.write("  [DER SPKI] @0x%08X b64len=%d derlen=%d\n"
                      % (m.start(), len(s), len(der)))
            out.write("    %s\n" % s.decode("ascii"))
            seen += 1
            continue
        # a bare INTEGER sequence: 30 81 89 02 81 81 <128-byte modulus> 02 03 01 00 01
        if der[:2] in (b"\x30\x81", b"\x30\x82") and der[2:4] == b"\x02\x81":
            out.write("  [DER SEQUENCE-of-INT, likely raw RSAPublicKey] @0x%08X b64len=%d derlen=%d\n"
                      % (m.start(), len(s), len(der)))
            out.write("    %s\n" % s.decode("ascii"))
            seen += 1
            continue
    if seen == 0:
        out.write("  no base64-encoded DER public key found\n")

    # 3. crypto hints
    for needle in (b"RSAKeyValue", b"mhyrsa", b"openssl", b"PEM", b"BEGIN PUBLIC",
                   b"public_key", b"PublicKey", b"rsa"):
        n = b.count(needle)
        if n:
            first = b.find(needle)
            out.write("  string %-14r count=%-5d first@0x%08X\n"
                      % (needle.decode("ascii", "replace"), n, first))
    out.write("\n")

# 4. also dump the modulus we DO control, for reference
out.write("=== reference: patch/server_public_key.bin ===\n")
out.write(open(r"D:\Projects\Experiment\LunaGC_7.0.0\patch\server_public_key.bin", "rb")
          .read().hex() + "\n")
out.close()
print("done")
