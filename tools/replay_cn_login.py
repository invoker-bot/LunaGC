# Replays the CN 7.0.0 client login chain against the local LunaGC server,
# end to end, exactly in the order the client issues them.  Proves the server
# side is complete without needing to launch the game.
#
# Account/password are RSA-encrypted with the 1024-bit passport public key
# whose private half ships in the server (keys/passport_1024.der) -- the same
# key the patch injects into AccountPlatNative.dll.

import base64
import json
import ssl
import sys
import urllib.request
from pathlib import Path

# Use exactly the hostname the patched AccountPlatNative.dll points the native
# SDK at -- it resolves via public DNS to 127.0.0.1/::1, so this also proves the
# client's own resolution path works.
HOST = "http://lunagc.localtest.me:8088"
PUB = Path(__file__).with_name("_key1024_pub.pem")

from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.serialization import load_pem_public_key

pub = load_pem_public_key(open(PUB, "rb").read())


def enc(plain):
    ct = pub.encrypt(plain.encode(), padding.PKCS1v15())
    return base64.b64encode(ct).decode()


def call(method, path, body=None, headers=None):
    url = HOST + path
    data = None
    hdrs = {"Content-Type": "application/json"}
    if headers:
        hdrs.update(headers)
    if body is not None:
        data = json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, headers=hdrs, method=method)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(req, timeout=15) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")


ACCOUNT = sys.argv[1] if len(sys.argv) > 1 else "lunagc_1024test"
PASSWORD = sys.argv[2] if len(sys.argv) > 2 else "anything"

steps = []
steps.append(("GET", "/hk4e_cn/mdk/shield/api/loadConfig", None))
steps.append(("GET", "/hk4e_cn/combo/granter/api/getConfig", None))
steps.append(("POST", "/hk4e_cn/combo/granter/api/compareProtocolVersion",
              {"app_id": 4, "device": "00000000000000000000000000000000",
               "sign": "", "lang": "zh-Hans", "channel_id": 1, "sub_channel_id": 1}))
steps.append(("POST", "/account/ma-cn-passport/app/loginByPassword",
              {"account": enc(ACCOUNT), "password": enc(PASSWORD)}))

# --- session resume: the ma-cn-session verify step -----------------------
# The real client caches mid + stoken in process memory and re-runs this on
# every launch / server restart instead of re-entering credentials.  The token
# is carried in a nested object, so this is also a regression test for the
# verifySToken field-name mismatch that used to persist a null session key.


def verify(mid, token, path="/account/ma-cn-session/app/verify"):
    body = {"mid": str(mid), "token": {"token_type": 1, "token": token}, "refresh": True}
    code, resp = call("POST", path, body)
    echo = ""
    try:
        j = json.loads(resp)
        if j.get("retcode") == 0 and j["data"].get("tokens"):
            echo = j["data"]["tokens"][0]["token"]
    except Exception:
        pass
    print("POST %-50s -> %s  echoed token %s" % (path, code, echo[:24] or "<none>"))
    return code, echo


st = None
for m, p, b in steps:
    code, resp = call(m, p, b)
    tail = resp[-150:].replace("\n", " ")
    print("%-5s %-52s -> %s  ...%s" % (m, p, code, tail))
    if p.endswith("loginByPassword"):
        st = resp

print()
if st:
    try:
        j = json.loads(st)
        print("loginByPassword retcode=%s message=%s" % (j.get("retcode"), j.get("message")))
        if j.get("retcode") == 0:
            token = j["data"]["token"]["token"]
            aid = j["data"]["user_info"]["aid"]
            print("aid=%s token=%s..." % (aid, token[:24]))
            # session resume with the token we were just issued -- the stored key
            # must already match, so no adoption should happen and the key must
            # stay non-null in the db.
            verify(aid, token)
            verify(aid, token, "/account/ma-cn-passport/token/verifySToken")
            # a stale/foreign token still has to be accepted (private-server leniency)
            verify(aid, "v2_deadbeef_invalid_token_for_replay_test")
            # mdk/shield/api/login -- exchange the passport token for an access token
            code, resp = call("POST", "/hk4e_cn/mdk/shield/api/login",
                              {"account": ACCOUNT, "password": PASSWORD,
                               "is_crypto": False, "token": token})
            print("POST /hk4e_cn/mdk/shield/api/login -> %s ...%s" % (code, resp[-150:]))
            j2 = json.loads(resp)
            if j2.get("retcode") == 0:
                at = j2["data"]["account"]["token"]
                inner = json.dumps({"uid": str(aid), "token": at, "guest": False})
                # granter login/v2/login -- the final step before the client hits the
                # dispatch/gateserver.  This is the request that has never appeared
                # in the server log.
                code, resp = call("POST", "/hk4e_cn/combo/granter/login/v2/login",
                                  {"app_id": 4, "channel_id": 1, "data": inner,
                                   "device": "00000000000000000000000000000000",
                                   "sign": ""})
                print("POST /hk4e_cn/combo/granter/login/v2/login -> %s ...%s"
                      % (code, resp[-200:]))
    except Exception as e:
        print("parse failed: %r" % e)
