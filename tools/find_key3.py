# Extract the passport public keys from AccountPlatNative.dll and prove which
# one encrypted the client's loginByPassword payload.
#
# Proof: for a valid RSA ciphertext c under modulus n, c < n must hold.

import base64, os, re

OUT  = r"D:\Projects\Experiment\LunaGC_7.0.0\tools\_key1024.txt"
DLL  = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0\YuanShen_Data\Plugins\AccountPlatNative.dll"
LOG  = r"D:\Projects\Experiment\LunaGC_7.0.0\tools\_sdk.log"

b = open(DLL, "rb").read()
out = open(OUT, "w", encoding="utf-8")


class Cursor:
    def __init__(self, d, i=0):
        self.d, self.i = d, i

    def tag(self):
        return self.d[self.i]

    def read_tlv(self):
        d, i = self.d, self.i
        tag = d[i]; i += 1
        L = d[i]; i += 1
        if L & 0x80:
            k = L & 0x7F; L = 0
            for _ in range(k):
                L = (L << 8) | d[i]; i += 1
        content = d[i:i + L]
        self.i = i + L
        return tag, content


def parse_spki(der):
    c = Cursor(der)
    tag, seq = c.read_tlv()                 # outer SEQUENCE
    assert tag == 0x30
    inner = Cursor(seq)
    tag, algid = inner.read_tlv()           # AlgorithmIdentifier
    assert tag == 0x30
    tag, bitstring = inner.read_tlv()       # BIT STRING
    assert tag == 0x03
    rsapub = bitstring[1:]                  # drop unused-bits byte
    c2 = Cursor(rsapub)
    tag, rseq = c2.read_tlv()               # RSAPublicKey SEQUENCE
    assert tag == 0x30
    c3 = Cursor(rseq)
    tag, n = c3.read_tlv(); assert tag == 0x02
    tag, e = c3.read_tlv(); assert tag == 0x02
    return int.from_bytes(n, "big"), int.from_bytes(e, "big")


PEM_RE = re.compile(rb"-----BEGIN PUBLIC KEY-----(.*?)-----END PUBLIC KEY-----", re.S)
keys = []
for m in PEM_RE.finditer(b):
    body = b"".join(m.group(1).split()).decode("ascii")
    der = base64.b64decode(body)
    keys.append((m.start(), m.end(), body, der))
    out.write("PEM @0x%08X-0x%08X  b64len=%d  derlen=%d\n"
              % (m.start(), m.end(), len(body), len(der)))

parsed = []
for (start, end, body, der) in keys:
    n, e = parse_spki(der)
    parsed.append((start, end, body, n, e))
    out.write("\n  modulus bits=%d  e=%d\n" % (n.bit_length(), e))
    out.write("  n=%X\n" % n)

# client ciphertext
raw = open(LOG, "rb").read().decode("utf-8", "replace")
acct = None
for line in raw.splitlines():
    if "request body is:" in line:
        mm = re.search(r'"account"\s*:\s*"([^"]+)"',
                       line.split("request body is:", 1)[1])
        if mm:
            acct = mm.group(1)
ct = base64.b64decode(acct)
c = int.from_bytes(ct, "big")
out.write("\nclient ciphertext: %d bytes, value bit-length=%d\n"
          % (len(ct), c.bit_length()))

out.write("\n--- c < n test (necessary condition for a valid ciphertext) ---\n")
for (start, end, body, n, e) in parsed:
    out.write("  key@0x%08X (%d-bit, e=%d): c < n = %s\n"
              % (start, n.bit_length(), e, c < n))

# also record the exact byte range of the whole PEM literal, for patching
out.write("\n--- exact PEM literals in the file ---\n")
for m in PEM_RE.finditer(b):
    out.write("  0x%08X .. 0x%08X  total %d bytes\n"
              % (m.start(), m.end(), m.end() - m.start()))
    out.write("  %r\n" % b[m.start():m.end()])

out.close()
print("done")
