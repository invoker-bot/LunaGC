import base64, json, urllib.request, os
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.serialization import load_der_private_key

KEYS = r"D:\Projects\Experiment\LunaGC_7.0.0\src\main\resources\keys"
URL = "http://lunagc.localtest.me:8088/account/ma-cn-passport/app/loginByPassword"

priv = load_der_private_key(open(os.path.join(KEYS, "SigningKey.der"), "rb").read(), password=None)
pub = priv.public_key()
print("SigningKey.der: key_size=%d" % pub.key_size)

for pad_name, pad in [
    ("PKCS1", padding.PKCS1v15()),
    ("OAEP-SHA1", padding.OAEP(mgf=padding.MGF1(hashes.SHA1()), algorithm=hashes.SHA1(), label=None)),
]:
    ct = pub.encrypt(b"rsa_selftest_user", pad)
    body = json.dumps({
        "account": base64.b64encode(ct).decode(),
        "password": base64.b64encode(ct).decode(),
    }).encode()
    req = urllib.request.Request(URL, data=body, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=8) as r:
            print("%-10s -> HTTP %s %s" % (pad_name, r.status, r.read()[:160].decode(errors="replace")))
    except urllib.error.HTTPError as e:
        print("%-10s -> HTTP %s %s" % (pad_name, e.code, e.read()[:160].decode(errors="replace")))
