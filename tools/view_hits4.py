import io, re

data = open('tools/_damaged_hits4.txt', 'rb').read().decode('utf-8', 'replace')
blocks = data.split('\nDONE ')
out = io.open('tools/_hits4_summary.txt', 'w', encoding='utf-8')
for b in blocks:
    lines = b.strip().split('\n')
    if not lines or not lines[0].strip():
        continue
    hdr = lines[0][:100]
    pats = {}
    i = 1
    while i < len(lines):
        if lines[i].startswith('HIT '):
            m = re.search(r'@0x([0-9A-F]+) \[([^\]]*)\]', lines[i])
            if m:
                pats.setdefault(m.group(2), []).append((int(m.group(1), 16), lines[i + 1] if i + 1 < len(lines) else ''))
            i += 2
        else:
            i += 1
    out.write("=" * 80 + "\n" + hdr + "\n")
    for p, v in pats.items():
        out.write("  %-30s x%d\n" % (p, len(v)))
    for p, v in pats.items():
        out.write("\n  --- pattern %s ---\n" % p)
        for off, ctx in v[:6]:
            out.write("    @0x%X: %s\n" % (off, ctx.strip()[:260]))
out.close()
print("ok")
