# Runtime check for the GM console's item + banner endpoints.
#
# Usage:  python tools/replay_gm_api.py
# Requires a running server on 127.0.0.1:8088. This repo's config leaves
# server.gm.accessToken empty (loopback-only, no token), so the calls go out
# unauthenticated and only fall back to LUNAGC_GM_TOKEN on a 403.
#
# The POST test flips one banner off and back on, so it ends where it started,
# but it does rewrite data/Banners.json twice. Do not run this against a server
# whose players are mid-pull.

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

HOST = "http://127.0.0.1:8088"
TOKEN = os.environ.get("LUNAGC_GM_TOKEN", "")


def _call(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(HOST + path, data=data, headers=headers, method=method)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(req, timeout=30) as r:
            return r.status, json.loads(r.read().decode("utf-8", "replace"))
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, raw


def call(method, path, body=None):
    # The console may be running with no access token (config sets it empty); only fall back to
    # the shared secret when the server actually refuses the unauthenticated probe.
    st, j = _call(method, path, body)
    if st == 403 and TOKEN:
        return _call(method, path, body, token=TOKEN)
    return st, j


fails = []


def check(label, cond, detail=""):
    print(("ok   " if cond else "FAIL ") + label + (("  -- " + detail) if detail and not cond else ""))
    if not cond:
        fails.append(label)


# --- 1. item search by Chinese name -------------------------------------
st, j = call("GET", "/gm/api/items?" + urllib.parse.urlencode({"q": "原石", "limit": 10}))
items = j.get("items", []) if isinstance(j, dict) else []
ids = {it.get("id") for it in items}
names = {it.get("name") for it in items}
check("GET /gm/api/items?q=原石 -> 200", st == 200, str(j)[:160])
check("201 原石 is in the results", 201 in ids, "got ids %s names %s" % (sorted(ids)[:8], sorted(names)[:5]))
check("names are Chinese, not English", any("石" in (n or "") for n in names), str(sorted(names))[:200])

# --- 2. item search by numeric id --------------------------------------
st, j = call("GET", "/gm/api/items?q=104003&limit=10")
items = j.get("items", []) if isinstance(j, dict) else []
check("GET /gm/api/items?q=104003 finds 大英雄的经验",
      any(it.get("id") == 104003 for it in items), str(j)[:160])

# --- 3. type filter ----------------------------------------------------
st, j = call("GET", "/gm/api/items?" + urllib.parse.urlencode({"type": "ITEM_WEAPON", "limit": 50}))
items = j.get("items", []) if isinstance(j, dict) else []
check("GET /gm/api/items type=ITEM_WEAPON -> 200", st == 200, str(j)[:160])
check("every row is a weapon",
      len(items) > 0 and all(it.get("type") == "ITEM_WEAPON" for it in items),
      "%d rows, types %s" % (len(items), {it.get("type") for it in items}))

# --- 4. paging: total > page ------------------------------------------
st, j = call("GET", "/gm/api/items?limit=5")
total = j.get("total") if isinstance(j, dict) else None
check("total is larger than the page", isinstance(total, int) and total > 5,
      "total=%s page=%d" % (total, len(j.get("items", []))))

# --- 5. banner list ----------------------------------------------------
st, j = call("GET", "/gm/api/banners")
banners = j.get("banners", []) if isinstance(j, dict) else []
check("GET /gm/api/banners -> 200", st == 200, str(j)[:160])
check("at least one banner is configured", len(banners) > 0, str(j)[:200])
check("banners carry scheduleId/loaded/active",
      all("scheduleId" in b and "loaded" in b and "active" in b for b in banners),
      str(banners[0])[:200] if banners else "")

if banners:
    live = next((b for b in banners if b.get("loaded")), banners[0])
    sid = live["scheduleId"]
    was_disabled = bool(live.get("disabled"))

    # --- 6. disable then re-enable, ending where we started --------------
    st, j = call("POST", "/gm/api/banners", {"scheduleId": sid, "action": "disable"})
    check("POST disable a live banner -> 200", st == 200 and j.get("retcode") == 0, str(j)[:200])

    st, j = call("GET", "/gm/api/banners")
    after = next((b for b in j.get("banners", []) if b.get("scheduleId") == sid), {})
    check("banner is no longer loaded after disable",
          after.get("loaded") is False and after.get("disabled") is True,
          str(after)[:200])

    st, j = call("POST", "/gm/api/banners", {"scheduleId": sid, "action": "enable"})
    check("POST enable the same banner -> 200", st == 200 and j.get("retcode") == 0, str(j)[:200])

    st, j = call("GET", "/gm/api/banners")
    after = next((b for b in j.get("banners", []) if b.get("scheduleId") == sid), {})
    check("banner is loaded again after enable", after.get("loaded") is True, str(after)[:200])

    if was_disabled and after.get("disabled"):
        # The round trip ended with a banner that should have been enabled by
        # the operator at some point -- put it back the way it was.
        call("POST", "/gm/api/banners", {"scheduleId": sid, "action": "disable"})

    # --- 7. unknown scheduleId must not touch the file -------------------
    st, j = call("POST", "/gm/api/banners", {"scheduleId": 999999, "action": "enable"})
    check("unknown scheduleId -> 400", st == 400, str(j)[:200])

# --- 8. banner history ---------------------------------------------------
# The history view walks git for every committed Banners.json, so it needs a
# repo with at least one commit behind the working copy to be meaningful.
st, j = call("GET", "/gm/api/banners/history")
revs = j.get("revisions", []) if isinstance(j, dict) else []
check("GET /gm/api/banners/history -> 200", st == 200, str(j)[:160])
check("history has the working copy plus at least one committed table",
      len(revs) >= 2 and any(r.get("working") for r in revs),
      "revisions=%s" % [r.get("commit") for r in revs])
check("committed revisions carry date/subject/bannerCount",
      all(r.get("date") and r.get("subject") and r.get("bannerCount")
          for r in revs if not r.get("working")),
      str([r for r in revs if not r.get("working")][:1])[:300])
check("revisions are ordered working-copy first",
      revs[0].get("working") is True if revs else False,
      str(revs[0].get("commit")) if revs else "")

# --- 9. rerun lifts a historical banner into the live table --------------
# This rewrites data/Banners.json, so snapshot it and put it back afterwards.
with open("data/Banners.json", "rb") as fh:
    saved_table = fh.read()

hist = [r for r in revs if not r.get("working")]
if hist:
    src = hist[0]
    template = next((b for b in src["banners"] if b["scheduleId"] not in
                     {x["scheduleId"] for x in banners}), None)
    if template is None:
        template = src["banners"][0]
    st, j = call("POST", "/gm/api/banners",
                 {"scheduleId": template["scheduleId"], "action": "rerun",
                  "commit": src["commit"]})
    ok_rerun = st == 200 and isinstance(j, dict) and j.get("retcode") == 0
    check("POST rerun a historical banner -> 200", ok_rerun, str(j)[:200])

    if ok_rerun:
        st, j = call("GET", "/gm/api/banners")
        live = j.get("banners", []) if isinstance(j, dict) else []
        reran = next((b for b in live if b["scheduleId"] == template["scheduleId"]), {})
        check("the rerun banner is now live",
              isinstance(reran, dict)
              and reran.get("loaded") is True
              and reran.get("disabled") is not True
              and reran.get("beginTime", 0) != 0,
              str(reran)[:200])

with open("data/Banners.json", "wb") as fh:
    fh.write(saved_table)
# A flip forces the gacha reload that reads the restored table.
if banners:
    call("POST", "/gm/api/banners", {"scheduleId": banners[0]["scheduleId"], "action": "disable"})
    call("POST", "/gm/api/banners", {"scheduleId": banners[0]["scheduleId"], "action": "enable"})

print()
if fails:
    print("FAIL: %d check(s) failed: %s" % (len(fails), fails))
    sys.exit(2)
print("PASS: GM item + banner endpoints behave as specified")
