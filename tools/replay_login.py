import json, urllib.request, urllib.error, io

OUT = r"D:\Projects\Experiment\LunaGC_7.0.0\tools\_replay.txt"
out = io.open(OUT, "w", encoding="utf-8")

BASE = "http://127.0.0.1:8088"

def post(path, body, ctype="application/json"):
    data = json.dumps(body).encode() if not isinstance(body, bytes) else body
    req = urllib.request.Request(BASE + path, data=data,
                                 headers={"Content-Type": ctype, "User-Agent": "NativeLogin"})
    try:
        with urllib.request.urlopen(req, timeout=8) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:
        return -1, repr(e)

def get(path):
    try:
        with urllib.request.urlopen(BASE + path, timeout=8) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:
        return -1, repr(e)

tests = [
    ("POST", "/account/ma-cn-passport/app/loginByPassword",
     {"account": "testuser", "password": "anything", "is_crypto": False}),
    ("POST", "/account/ma-cn-passport/app/loginByPassword",
     {"account": "testuser", "password": "anything"}),
    ("POST", "/hk4e_cn/mdk/shield/api/login",
     {"account": "testuser", "password": "anything", "is_crypto": False}),
    ("POST", "/hk4e_cn/combo/granter/login/v2/login",
     {"app_id": 4, "channel_id": 1, "data": "", "device": "x", "sign": "x"}),
    ("GET",  "/admin/mi18n/plat_oversea/m2020030410/m2020030410-version.json", None),
    ("GET",  "/admin/mi18n/plat_os/m09291531181441/m09291531181441-version.json", None),
    ("GET",  "/hk4e_cn/combo/granter/api/compareProtocolVersion", None),
    ("GET",  "/sdk-static.mihoyo.com/x", None),
]

for method, path, body in tests:
    if method == "POST":
        st, txt = post(path, body)
    else:
        st, txt = get(path)
    out.write("%s %s\n   -> %s  %s\n" % (method, path, st, txt[:600]))
    out.flush()
out.close()
print("done")
