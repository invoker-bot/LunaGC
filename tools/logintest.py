"""End-to-end check of the passport_1024 login path without launching the game.

Encrypts an account/password pair with the public half of passport_1024.der --
i.e. the exact key the patched AccountPlatNative.dll now hands the client -- and
POSTs it to the running dispatch server as loginByPassword.  If the DLL patch and
the server key pair up, the server logs "RSA decrypt succeeded with
passport1024+PKCS1" instead of "all methods failed".
"""
import base64
import json
import sys
import urllib.request
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.serialization import load_der_private_key

ACCOUNT = sys.argv[1] if len(sys.argv) > 1 else "1"
PASSWORD = sys.argv[2] if len(sys.argv) > 2 else "1"
URL = sys.argv[3] if len(sys.argv) > 3 else "http://127.0.0.1:8088/account/ma-cn-passport/app/loginByPassword"

priv = load_der_private_key(
    open("src/main/resources/keys/passport_1024.der", "rb").read(), None)
pub = priv.public_key()

account_ct = base64.b64encode(pub.encrypt(ACCOUNT.encode(), padding.PKCS1v15())).decode()
password_ct = base64.b64encode(pub.encrypt(PASSWORD.encode(), padding.PKCS1v15())).decode()

print("account plaintext     :", ACCOUNT)
print("ciphertext base64 len :", len(account_ct), "(client sends 172)")
print("public key modulus bits:", pub.key_size)

body = json.dumps({"account": account_ct, "password": password_ct}).encode()
req = urllib.request.Request(URL, data=body, method="POST")
req.add_header("Content-Type", "application/json")
req.add_header("User-Agent", "Go-http-client/1.1")
req.add_header("X-Rpc-Game_biz", "hk4e_cn")
req.add_header("X-Rpc-App_id", "c76ync6mutq8")
req.add_header("X-Rpc-Client_type", "3")
req.add_header("X-Rpc-Language", "zh-cn")

try:
    with urllib.request.urlopen(req, timeout=15) as r:
        print("\nHTTP", r.status)
        print(r.read().decode())
except urllib.error.HTTPError as e:
    print("\nHTTP", e.code)
    print(e.read().decode())
