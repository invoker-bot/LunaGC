# Extract every request/response pair in a time window from the Javalin debug
# log and flag response bodies whose braces are unbalanced -- the client's
# MiHoYo.SDK.JSONNode.Parse throws "Too many closing brackets" on those.

import re, sys

LOG = r"D:\Projects\Experiment\LunaGC_7.0.0\start_stdout.log"
STRIP = re.compile(rb"\x1b\[[0-9;]*m")

raw = open(LOG, "rb").read().replace(b"\r\n", b"\n")
raw = STRIP.sub(b"", raw)
text = raw.decode("utf-8", "replace")

# split into request blocks
blocks = re.split(r"-{30,}", text)

bad = []
for blk in blocks:
    m = re.search(r"Request: (GET|POST) \[([^\]]+)\]", blk)
    if not m:
        continue
    method, path = m.group(1), m.group(2)
    ts = re.search(r"^(\d\d:\d\d:\d\d)", blk, re.M)
    tss = ts.group(1) if ts else "??"
    bm = re.search(r"Response: \[(\d+ [^\]]+)\].*?Body is (\d+) bytes \(starts on next line\):\n(.*)\Z",
                   blk, re.S)
    if not bm:
        continue
    code, size, body = bm.group(1), int(bm.group(2)), bm.group(3)
    depth = 0
    instr = False
    esc = False
    for ch in body:
        if instr:
            if esc:
                esc = False
            elif ch == "\\":
                esc = True
            elif ch == '"':
                instr = False
            continue
        if ch == '"':
            instr = True
        elif ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth < 0:
                bad.append((tss, method, path, code, size, body[:200]))
                break
    else:
        if depth != 0:
            bad.append((tss, method, path, code, size, "(unclosed) " + body[:200]))

print("unbalanced responses: %d" % len(bad))
for tss, method, path, code, size, body in bad:
    print("%s %s %-60s %s %dB %r" % (tss, method, path[:60], code, size, body[:160]))
