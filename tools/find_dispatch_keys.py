# Searches the game's native DLLs for the RSA public-key moduli that LunaGC
# ships under keys/game_keys/.  The client embeds the *private* halves keyed by
# an id; the id it puts in the query_cur_region URL must match one the server
# has loaded, or dispatch dies with a 500 right before the gateserver connect.
# Also hunts the literals "key_id", "dispatchSeed" and "query_cur_region" to see
# where the URL is assembled.

import os

GAME = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0"
PLUGINS = os.path.join(GAME, "YuanShen_Data", "Plugins")

from cryptography.hazmat.primitives.serialization import load_der_public_key

JAR_KEYS = r"D:\Projects\Experiment\LunaGC_7.0.0\resources\keys\game_keys"
if not os.path.isdir(JAR_KEYS):
    # fall back to the copies unpacked from the jar
    JAR_KEYS = r"D:\Projects\Experiment\LunaGC_7.0.0\tools\_game_keys"

moduli = {}
for fn in sorted(os.listdir(JAR_KEYS)):
    if not fn.endswith("_Pub.der"):
        continue
    kid = fn.split("_")[0]
    pub = load_der_public_key(open(os.path.join(JAR_KEYS, fn), "rb").read())
    n = pub.public_numbers().n
    moduli[kid] = n.to_bytes((n.bit_length() + 7) // 8, "big")
    print("key_id=%s modulus=%d bytes sha1=%s" % (kid, len(moduli[kid]),
          __import__("hashlib").sha1(moduli[kid]).hexdigest()[:16]))

literals = [b"key_id", b"dispatchSeed", b"query_cur_region", b"client_custom_config"]

targets = []
if os.path.isdir(PLUGINS):
    for fn in sorted(os.listdir(PLUGINS)):
        if fn.lower().endswith((".dll", ".exe")):
            targets.append(os.path.join(PLUGINS, fn))
targets.append(os.path.join(GAME, "YuanShen.exe"))

for p in targets:
    try:
        buf = open(p, "rb").read()
    except Exception as e:
        print("skip %s (%s)" % (p, e))
        continue
    for kid, mod in moduli.items():
        # try the full modulus and its DER Sequence Tag+Length prefix context
        if mod in buf:
            i = buf.find(mod)
            print("HIT  %s contains modulus of key_id=%s at 0x%X" % (os.path.basename(p), kid, i))
        else:
            # mihoyo stores keys with a leading 0x00 stripped sometimes; try suffixes
            for drop in (1, 2):
                if mod[drop:] in buf:
                    i = buf.find(mod[drop:])
                    print("HIT* %s contains modulus(key_id=%s) minus %d byte(s) at 0x%X"
                          % (os.path.basename(p), kid, drop, i))
    for lit in literals:
        i = buf.find(lit)
        if i >= 0:
            print("LIT  %s has %r at 0x%X  ctx=%r"
                  % (os.path.basename(p), lit, i, buf[max(0, i - 40):i + 60]))
print("done targets=%d" % len(targets))
