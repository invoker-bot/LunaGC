import re, os

targets = [
    r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0\YuanShen_Data\Plugins\AccountPlatNative.dll",
    r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0\YuanShen.exe",
]

# candidate public-key serialisations
pats = {
    "mhy_tlv_01_0a":      re.compile(rb"\x01\x0a\x02\x82\x01\x01\x00"),
    "mhy_tlv_01_0a_len2": re.compile(rb"\x01\x0a\x02\x82\x01"),
    "der_seq_pub_2048":   re.compile(rb"\x30\x82\x01\x22\x02\x82\x01\x01\x00"),
    "der_seq_pub_1024":   re.compile(rb"\x30\x82\x01\x0a\x02\x81\x81\x00"),
    "der_modonly_2048":   re.compile(rb"\x02\x82\x01\x01\x00"),
    "der_modonly_1024":   re.compile(rb"\x02\x81\x81\x00"),
}

for t in targets:
    if not os.path.exists(t):
        print("missing", t); continue
    d = open(t, "rb").read()
    print("== %s  (%d bytes)" % (os.path.basename(t), len(d)))
    for name, p in pats.items():
        offs = [m.start() for m in p.finditer(d)]
        if offs:
            print("   %-20s %d hits  first@0x%X" % (name, len(offs), offs[0]))
            for o in offs[:4]:
                print("        +0x%X: %s" % (o, d[o:o+24].hex()))
        else:
            print("   %-20s 0 hits" % name)
