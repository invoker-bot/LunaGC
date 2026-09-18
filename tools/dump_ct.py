import base64, re

LOG = r"D:\Projects\Experiment\LunaGC_7.0.0\tools\_sdk.log"
raw = open(LOG, "rb").read().decode("utf-8", "replace")

n = 0
for line in raw.splitlines():
    if "request body is:" not in line:
        continue
    body = line.split("request body is:", 1)[1].strip()
    n += 1
    print("--- loginByPassword body #%d (raw len %d) ---" % (n, len(body)))
    print(repr(body[:120]))
    print("...")
    print(repr(body[-80:]))
    # pull each field without assuming the body is short
    for key in ("account", "password", "is_crypto"):
        m = re.search(r'"%s"\s*:\s*"([^"]*)"' % key, body)
        if m:
            v = m.group(1)
            try:
                dec = base64.b64decode(v)
                print("  %-9s b64=%-6d bytes=%-5d tail=%s"
                      % (key, len(v), len(dec), dec[-6:].hex()))
            except Exception as e:
                print("  %-9s b64=%-6d (not valid b64: %s)" % (key, len(v), e))
        else:
            m2 = re.search(r'"%s"\s*:\s*([^,}]*)' % key, body)
            print("  %-9s non-string: %s" % (key, m2.group(1) if m2 else "<absent>"))
print("\ntotal request bodies found:", n)
