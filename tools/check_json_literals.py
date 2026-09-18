# Brace-balance check for every HttpJsonResponse literal in the source tree.
# Catches the class of bug that broke /hk4e_cn/mdk/shield/api/loadConfig:
# the CN adaptation emptied an object but left the extra '}' in.

import os, re

ROOT = r"D:\Projects\Experiment\LunaGC_7.0.0\src\main\java"

# find "..."  strings that look like JSON
PAT = re.compile(r'"((?:[^"\\]|\\.)*?)"')

def balanced(s):
    depth, instr, esc = 0, False, False
    for ch in s:
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
                return False
    return depth == 0

bad = []
n = 0
for dirpath, dirnames, filenames in os.walk(ROOT):
    for fn in filenames:
        if not fn.endswith(".java"):
            continue
        p = os.path.join(dirpath, fn)
        src = open(p, encoding="utf-8").read()
        for i, line in enumerate(src.splitlines(), 1):
            for m in PAT.finditer(line):
                s = m.group(1)
                if not s.startswith("{"):
                    continue
                # it's a JSON-ish literal in the source (escapes doubled)
                body = s.replace('\\"', '"').replace("\\\\", "\\")
                n += 1
                if not balanced(body):
                    bad.append((p, i, body[:120]))

print("JSON literals scanned: %d" % n)
print("unbalanced: %d" % len(bad))
for p, i, s in bad:
    print("  %s:%d  %s" % (p, i, s))
