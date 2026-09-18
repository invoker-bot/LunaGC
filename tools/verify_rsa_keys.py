# Offline check: can the server decrypt the REAL ciphertext the CN client already sent?
#
# _sdk.log line 231 holds the actual loginByPassword body the client POSTed at
# 04:47:27.698 (right before the 502 killed it). If any server-side private key
# recovers a readable username from that ciphertext, the key pair matches and the
# only remaining blocker was the proxy -- which is now fixed.
#
# Usage: python tools/verify_rsa_keys.py

import base64, io, os, re, sys

RES = r"D:\Projects\Experiment\LunaGC_7.0.0\src\main\resources\keys"
LOG = r"D:\Projects\Experiment\LunaGC_7.0.0\tools\_sdk.log"

KEYS = [
    ("sdk     /keys/private_key.der",       os.path.join(RES, "private_key.der")),
    ("auth    /keys/auth_private-key.der",  os.path.join(RES, "auth_private-key.der")),
    ("passport/keys/SigningKey.der",        os.path.join(RES, "SigningKey.der")),
]
PUB  = r"D:\Projects\Experiment\LunaGC_7.0.0\patch\server_public_key.bin"
PADDINGS = ["RSA/ECB/PKCS1Padding", "RSA/ECB/OAEPWithSHA-1AndMGF1Padding"]

try:
    from Crypto.PublicKey import RSA
    from Crypto.Cipher import PKCS1_v1_5, PKCS1_OAEP
    from Crypto.Hash import SHA1
except ImportError:
    print("PyCryptodome missing: pip install pycryptodome")
    sys.exit(0)

# --- 1. grab the real ciphertext out of the SDK log -------------------------
body = None
with io.open(LOG, encoding="utf-8", errors="replace") as f:
    for line in f:
        if "loginByPassword" in line and "request body is:" in line:
            body = line.split("request body is:", 1)[1].strip()
if not body:
    print("no loginByPassword body found in", LOG)
    sys.exit(0)

fields = re.findall(r'"(\w+)"\s*:\s*"([^"]*)"', body)
acct = dict(fields).get("account", "")
print("client account ciphertext: %d base64 chars -> %d bytes"
      % (len(acct), len(base64.b64decode(acct)) if acct else 0))

# --- 2. confirm the patch pubkey pairs with a server privkey ---------------
pub_bytes = open(PUB, "rb").read()
try:
    pub = RSA.import_key(pub_bytes)
    print("\npatch server_public_key.bin: %d-bit, exponent %d"
          % (pub.size_in_bits(), pub.e))
except Exception as e:
    print("could not parse patch public key:", e)

for label, path in KEYS:
    priv = RSA.import_key(open(path, "rb").read())
    match = (priv.n == pub.n)
    print("  %-36s %d-bit  n matches patch pubkey: %s"
          % (label, priv.size_in_bits(), match))

# --- 3. try to actually decrypt the client ciphertext ----------------------
print("\n--- decrypting the real client payload ---")
ct = base64.b64decode(acct)
decrypted = False
for label, path in KEYS:
    priv = RSA.import_key(open(path, "rb").read())
    for pad in PADDINGS:
        try:
            if "OAEP" in pad:
                c = PKCS1_OAEP.new(priv, SHA1)
            else:
                c = PKCS1_v1_5.new(priv)
            pt = c.decrypt(ct, None)
            s = pt.decode("utf-8", "replace")
            printable = all(32 <= ord(ch) <= 126 for ch in s)
            print("  [%s + %s] -> %r  printable=%s"
                  % (label.split()[0], pad.split("/")[-1], s, printable))
            if printable:
                decrypted = True
        except Exception as e:
            print("  [%s + %s] failed: %s"
                  % (label.split()[0], pad.split("/")[-1], type(e).__name__))

print("\nRESULT:", "KEY PAIR OK - server can decrypt the client payload"
      if decrypted else "NO KEY MATCHES - login would still fail at RSA")
