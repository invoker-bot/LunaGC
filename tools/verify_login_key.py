# End-to-end proof of the passport RSA fix.
#
# 1. read the 1024-bit public key straight out of the PATCHED
#    AccountPlatNative.dll (not out of our own pem file) -- this is exactly
#    what the client will use
# 2. encrypt an account name + password with it, the way the SDK does
# 3. POST to /account/ma-cn-passport/app/loginByPassword
# 4. a "token" in the response means the server decrypted our ciphertext and
#    auto-created the account -- i.e. the whole chain works

import base64, json, re, urllib.error, urllib.request
from Crypto.PublicKey import RSA
from Crypto.Cipher import PKCS1_v1_5, PKCS1_OAEP

DLL = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0\YuanShen_Data\Plugins\AccountPlatNative.dll"
URL = "http://lunagc.localtest.me:8088/account/ma-cn-passport/app/loginByPassword"

b = open(DLL, "rb").read()
# there are two PEM literals: a 2048-bit one (30 82 ...) at 0x6C92C0 and the
# 1024-bit passport one (30 81 9F) at 0x6C9480 -- pick by DER prefix
m = None
for mm in re.finditer(rb"-----BEGIN PUBLIC KEY-----(.*?)-----END PUBLIC KEY-----", b, re.S):
    cand = base64.b64decode(b"".join(mm.group(1).split()))
    if cand[:3] == b"\x30\x81\x9f":
        m, body, der = mm, b"".join(mm.group(1).split()), cand
        break
assert m, "no 1024-bit PEM (30 81 9F) in patched dll"
assert len(body) == 216, len(body)
pub = RSA.import_key(der)
print("dll key @0x%08X  bits=%d  e=%d" % (m.start(), pub.n.bit_length(), pub.e))

ACCOUNT = "lunagc_1024test"
PWD     = "hunter2"

for label, enc in (("PKCS1", PKCS1_v1_5.new(pub)),
                   ("OAEP",  PKCS1_OAEP.new(pub))):
    payload = json.dumps({
        "account":  base64.b64encode(enc.encrypt(ACCOUNT.encode())).decode(),
        "password": base64.b64encode(enc.encrypt(PWD.encode())).decode(),
    }).encode()
    req = urllib.request.Request(URL, data=payload,
                                 headers={"Content-Type": "application/json"})
    # never go through the system proxy for the local server
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        r = opener.open(req, timeout=15)
        txt = r.read().decode("utf-8", "replace")
        code = r.status
    except urllib.error.HTTPError as e:
        txt = e.read().decode("utf-8", "replace")
        code = e.code
    ok = '"token"' in txt
    print("[%-6s] http=%d  login_success=%s" % (label, code, ok))
    print("        %s" % txt[:300])
