import os, re, base64

def parse_tlv(data, p):
    tag = data[p]; p += 1
    ln = data[p]; p += 1
    if ln & 0x80:
        nb = ln & 0x7f
        ln = int.from_bytes(data[p:p + nb], 'big'); p += nb
    val = data[p:p + ln]; p += ln
    return tag, val, p

def seq_ints(data):
    """Parse a SEQUENCE of INTEGERs -> [int,...]"""
    tag, content, _ = parse_tlv(data, 0)
    assert tag == 0x30, hex(tag)
    out, p = [], 0
    while p < len(content):
        t, val, p = parse_tlv(content, p)
        assert t == 0x02, hex(t)
        out.append(int.from_bytes(val, 'big'))
    return out

def priv_modulus(path):
    """PKCS#8 PrivateKeyInfo -> RSAPrivateKey ints (version,n,e,d,p,q,...)"""
    d = open(path, 'rb').read()
    tag, content, _ = parse_tlv(d, 0)
    assert tag == 0x30 and content[0] == 0x02, "not PKCS#8"
    p = 0
    _, _, p = parse_tlv(content, p)          # version
    _, _, p = parse_tlv(content, p)          # AlgorithmIdentifier SEQUENCE
    t, octet, _ = parse_tlv(content, p)      # OCTET STRING
    assert t == 0x04, hex(t)
    return seq_ints(octet)                   # PKCS#1 RSAPrivateKey

print("=== LunaGC private keys ===")
priv = {}
for name in ("private_key.der", "auth_private-key.der"):
    p = os.path.join(r"D:\Projects\Experiment\LunaGC_7.0.0\src\main\resources\keys", name)
    if not os.path.exists(p):
        print("missing", p); continue
    ints = priv_modulus(p)
    n, e = ints[1], ints[2]
    priv[name] = n
    print("%-22s n=%d bits, e=%d" % (name, n.bit_length(), e))
    print("   n:", hex(n))

print("\n=== patch public keys ===")
spk = r"D:\Projects\Experiment\LunaGC_7.0.0\patch\server_public_key.bin"
d = open(spk, 'rb').read()
print("server_public_key.bin  %d bytes" % len(d))
try:
    ints = seq_ints(d)
    n = max(ints, key=lambda x: x.bit_length())
    print("   modulus bits:", n.bit_length())
    print("   n:", hex(n))
    m = priv.get("private_key.der")
    print("   MATCHES private_key.der:", m == n)
except Exception as ex:
    print("   not a DER SEQUENCE:", ex)

sx = open(r"D:\Projects\Experiment\LunaGC_7.0.0\patch\sdk_public_key.xml", 'rb').read()
print("\nsdk_public_key.xml:")
print(sx.decode(errors="replace"))
m = re.search(rb"<Modulus>([A-Za-z0-9+/=]+)</Modulus>", sx)
if m:
    raw = base64.b64decode(m.group(1))
    n = int.from_bytes(raw, 'big')
    print("   modulus bytes: %d, bits: %d" % (len(raw), n.bit_length()))
    print("   n:", hex(n))
    a = priv.get("auth_private-key.der")
    print("   MATCHES auth_private-key.der:", a == n)
