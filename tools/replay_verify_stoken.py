# Decisive regression test for the verifySToken nested-token bug.
#
# Before the fix, /account/ma-cn-session/app/verify sent the game token as a
# nested object {"mid":.., "token": {"token_type":1, "token": "v2_..."}} while
# VerifySTokenRequestJson only had a flat `stoken` field.  That deserialised to
# null and verifySToken() persisted null over the account's session key.
#
# This script logs in, then hits ONLY the verify endpoint with the nested shape
# and checks the stored session key immediately afterwards -- no mdk/shield step
# that would overwrite the key afterwards.

import base64
import json
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

HOST = "http://lunagc.localtest.me:8088"
PUB = Path(__file__).with_name("_key1024_pub.pem")

from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.serialization import load_pem_public_key

pub = load_pem_public_key(open(PUB, "rb").read())


def enc(plain):
    return base64.b64encode(pub.encrypt(plain.encode(), padding.PKCS1v15())).decode()


def call(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        HOST + path, data=data, headers={"Content-Type": "application/json"}, method=method
    )
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(req, timeout=15) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")


def db_key(username):
    q = (
        'JSON.stringify(db.getSiblingDB("grasscutter").accounts'
        ".find({username:'%s'},{_id:0,sessionKey:1}).toArray())" % username
    )
    out = subprocess.run(
        ["docker", "exec", "luna-mongo", "/usr/bin/mongosh", "--quiet", "--eval", q],
        capture_output=True,
        text=True,
    )
    try:
        arr = json.loads(out.stdout)
        if arr:
            return arr[0].get("sessionKey")
    except Exception:
        pass
    return "<unreadable>"


ACCOUNT = sys.argv[1] if len(sys.argv) > 1 else "lunagc_verifytest2"

code, resp = call(
    "POST", "/account/ma-cn-passport/app/loginByPassword",
    {"account": enc(ACCOUNT), "password": enc("hunter2")},
)
j = json.loads(resp)
if j.get("retcode") != 0:
    print("login failed:", resp[:200])
    sys.exit(1)
token = j["data"]["token"]["token"]
aid = j["data"]["user_info"]["aid"]
print("login ok: aid=%s token=%s..." % (aid, token[:20]))

NESTED = "/account/ma-cn-session/app/verify"
FLAT = "/account/ma-cn-passport/token/verifySToken"

# 1. nested form -- used to null out the stored key
code, resp = call("POST", NESTED, {"mid": str(aid), "token": {"token_type": 1, "token": token}, "refresh": True})
k = db_key(ACCOUNT)
print("nested verify -> %s   db sessionKey=%s" % (code, (k or "NULL")[:20]))
ok_nested = k is not None

# 2. flat form -- the shape that always worked
code, resp = call("POST", FLAT, {"mid": str(aid), "stoken": token})
k = db_key(ACCOUNT)
print("flat   verify -> %s   db sessionKey=%s" % (code, (k or "NULL")[:20]))
ok_flat = k is not None

# 3. nested form with no token at all -- must not wipe the key either
code, resp = call("POST", NESTED, {"mid": str(aid), "token": {"token_type": 1}, "refresh": True})
k = db_key(ACCOUNT)
print("nested, no token -> %s  db sessionKey=%s" % (code, (k or "NULL")[:20]))
ok_blank = k is not None

print()
if all([ok_nested, ok_flat, ok_blank]):
    print("PASS: session key survived every verify shape")
else:
    print("FAIL: a verify shape nulled the session key")
    sys.exit(2)
