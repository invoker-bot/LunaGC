# Generate a 1024-bit RSA keypair to replace the passport public key baked into
# AccountPlatNative.dll @0x6C9480.
#
# Constraint: the replacement PEM must be EXACTLY 268 bytes, byte-for-byte
# interchangeable with the original literal (single-line base64, no 64-char
# wrapping, LF line endings, no trailing newline). That requires a 216-char
# base64 body, which a 1024-bit SPKI always yields -- either 162 DER bytes
# (modulus top bit set -> INTEGER is 02 81 81 00 <128 bytes>, sign-padded,
# which is exactly the form the original key uses) or 161 DER bytes (top bit
# clear -> 02 81 80 <128 bytes>). Both are standard DER Java accepts.

import base64
from Crypto.PublicKey import RSA

TOOLS = r"D:\Projects\Experiment\LunaGC_7.0.0\tools"
PUB_PEM = TOOLS + r"\_key1024_pub.pem"
PRIV_DER = TOOLS + r"\_key1024_priv.der"

TARGET_PEM_LEN = 268
TARGET_DER_LEN = 162

for attempt in range(2000):
    key = RSA.generate(1024, e=65537)
    der = key.publickey().export_key(format="DER")
    assert len(der) in (161, 162), len(der)
    b64 = base64.b64encode(der).decode("ascii")
    if len(b64) != 216:
        continue
    pem = "-----BEGIN PUBLIC KEY-----\n" + b64 + "\n-----END PUBLIC KEY-----"
    if len(pem) != TARGET_PEM_LEN:
        continue
    priv = key.export_key(format="DER", pkcs=8)
    open(PUB_PEM, "w", encoding="ascii", newline="\n").write(pem)
    open(PRIV_DER, "wb").write(priv)

    # report
    f = open(TOOLS + r"\_key1024_gen.txt", "w", encoding="utf-8")
    f.write("attempts=%d\n" % (attempt + 1))
    f.write("public PEM  %s  len=%d\n" % (PUB_PEM, len(pem)))
    f.write("private DER %s  len=%d\n" % (PRIV_DER, len(priv)))
    f.write("\nn bits=%d  e=%d\n" % (key.n.bit_length(), key.e))
    f.write("n=%X\n" % key.n)
    f.write("\nPEM literal (single line, LF endings, no trailing newline):\n")
    f.write(pem + "\n")
    f.write("\nDER hex:\n%s\n" % der.hex())
    f.write("\nprivate PKCS#8 DER hex:\n%s\n" % priv.hex())
    f.close()
    print("ok after %d attempts: pem=%d der=%d priv=%d"
          % (attempt + 1, len(pem), len(der), len(priv)))
    break
else:
    raise SystemExit("no suitable key found")
