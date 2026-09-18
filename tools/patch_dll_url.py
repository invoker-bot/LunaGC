# Re-point AccountPlatNative.dll's hardcoded passport URLs at 127.0.0.1.
#
# Why not just edit the registry: v2rayN owns the system proxy settings and
# rewrites HKCU\...\Internet Settings\ProxyOverride on a timer, so any bypass
# entry we add for localtest.me is silently erased.  Patching the URL in the
# binary is permanent and leaves the user's proxy config alone.
#
# Why userinfo padding: the host has to keep its byte length, or every RVA
# after it shifts and the DLL's relocation table is no longer valid.
#
#   lunagc.localtest.me:8088      (24) -> xxxxxxxxx@127.0.0.1:8088      (24)
#   lunagc-pre.localtest.me:8088  (28) -> xxxxxxxxx@127.0.0.1:8088      (28)
#
# libcurl parses user@host per RFC 3986 and only sends the userinfo if the
# server challenges for auth, so LunaGC's handler never sees it.  Verified
# with curl 8 (same libcurl family the SDK bundles) for both direct and
# proxied requests: 200 + correct `Host: 127.0.0.1:8088`.

import shutil, sys

DLL = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0\YuanShen_Data\Plugins\AccountPlatNative.dll"
BAK = DLL + ".lunagc-bak3"

REPLACEMENTS = [
    (b"lunagc.localtest.me:8088",     b"xxxxxxxxx@127.0.0.1:8088"),
    (b"lunagc-pre.localtest.me:8088", b"xxxxxxxxxxxxx@127.0.0.1:8088"),
]

def main():
    b = bytearray(open(DLL, "rb").read())
    print("loaded %d bytes" % len(b))

    changed = 0
    for old, new in REPLACEMENTS:
        if len(old) != len(new):
            print("FATAL: length mismatch %d != %d for %r" % (len(old), len(new), old))
            sys.exit(1)
        n = b.count(old)
        if n == 0:
            print("WARN  %-28r not found (already patched?)" % old)
            continue
        b = b.replace(old, new)
        print("patched %-28r x%d -> %r" % (old, n, new))
        changed += n

    # leftover fragments: the second host string shares the ".localtest.me"
    # suffix, and the plain domain appears without a port in a few places.
    for frag in (b".localtest.me", b"localtest.me"):
        n = b.count(frag)
        if n:
            print("WARN  leftover %r x%d (non-port contexts, left as-is)" % (frag, n))

    if changed == 0:
        print("nothing to do, exiting without writing")
        return

    import os
    if not os.path.exists(BAK):
        shutil.copy2(DLL, BAK)
        print("backup -> %s" % BAK)
    else:
        print("backup already exists, not overwriting: %s" % BAK)

    open(DLL, "wb").write(bytes(b))
    print("wrote %d bytes (%d host strings redirected)" % (len(b), changed))

if __name__ == "__main__":
    main()
