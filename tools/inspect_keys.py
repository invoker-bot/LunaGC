import base64, os, binascii

PUB  = r"D:\Projects\Experiment\LunaGC_7.0.0\patch\server_public_key.bin"
RES  = r"D:\Projects\Experiment\LunaGC_7.0.0\src\main\resources\keys"

b = open(PUB, "rb").read()
print("=== patch/server_public_key.bin ===")
print("size:", len(b))
print("hex :", b.hex()[:160])
print("ascii-repr:", repr(b[:80]))
print("pem-looking:", b"-----BEGIN" in b)
print("base64-looking:", repr(b[:64]))

# If it is PEM/base64 text, decode the inner DER.
txt = b.decode("utf-8", "replace").strip()
if "-----BEGIN" in txt:
    der = base64.b64decode("".join(l for l in txt.splitlines()
                                  if not l.startswith("-----")))
    print("\ninner DER len:", len(der), "first bytes:", der[:16].hex())
else:
    try:
        der = base64.b64decode(txt)
        print("\nplain-base64 decode OK, inner len:", len(der),
              "first bytes:", der[:16].hex())
    except Exception as e:
        print("\nnot plain base64:", e)
        der = None

print("\n=== server private keys ===")
for name in ("private_key.der", "auth_private-key.der", "SigningKey.der"):
    p = os.path.join(RES, name)
    d = open(p, "rb").read()
    print("%-24s len=%-5d head=%s" % (name, len(d), d[:12].hex()))

# Parse the DER modulus length of each private key with a minimal ASN.1 walk.
def parse_der_len(d, i):
    b = d[i]; i += 1
    if b < 0x80:
        return b, i
    n = b & 0x7F
    v = 0
    for _ in range(n):
        v = (v << 8) | d[i]; i += 1
    return v, i

for name in ("private_key.der", "auth_private-key.der", "SigningKey.der"):
    d = open(os.path.join(RES, name), "rb").read()
    # SEQUENCE, then walk INTEGERs; report bit length of the first INTEGER (version)
    # and the second (modulus).
    i = 0
    assert d[i] == 0x30
    _, i = parse_der_len(d, i)
    ints = []
    while i < len(d) and len(ints) < 3:
        if d[i] != 0x02:
            # might be another SEQUENCE (algid) - just report and stop
            break
        i += 1
        L, i = parse_der_len(d, i)
        v = int.from_bytes(d[i:i+L], "big")
        ints.append(v)
        i += L
    if len(ints) >= 2:
        print("%-24s modulus bit-length = %d" % (name, ints[1].bit_length()))
