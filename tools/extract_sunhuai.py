import io

data = open('tools/_damaged_hits2.txt', 'rb').read()
lines = data.split(b'\n')

# the file stores repr()-escaped patterns; decode them back to real bytes
def unescape(s):
    out = bytearray()
    i = 0
    while i < len(s):
        if s[i:i+1] == b'\\' and i + 3 < len(s) and s[i+1:i+2] == b'x':
            try:
                out.append(int(s[i+2:i+4], 16))
                i += 4
                continue
            except ValueError:
                pass
        out.extend(s[i:i+1])
        i += 1
    return bytes(out)

targets = {
    b"\\xe6\\x8d\\x9f\\xe5\\x9d\\x8f": "sunhuai",
    b"\\xe5\\xae\\xa2\\xe6\\x88\\xb7\\xe7\\xab\\xaf": "kehuduan",
    b"\\xe9\\x87\\x8d\\xe6\\x96\\xb0\\xe5\\xae\\x89\\xe8\\xa3\\x85": "chongxinanzhuang",
}

out = io.open('tools/_sunhuai.txt', 'w', encoding='utf-8')
cur = None
seen = set()
n = 0
for l in lines:
    if l.startswith(b'HIT'):
        cur = l
    elif cur is not None:
        tag = None
        for k, v in targets.items():
            if k in cur:
                tag = v
                break
        if tag is None:
            cur = None
            continue
        key = cur[:160]
        if key in seen:
            continue
        seen.add(key)
        n += 1
        out.write(cur.decode('utf-8', 'replace').strip() + "\n")
        body = unescape(l.strip())
        out.write(body.decode('utf-8', 'replace')[:600] + "\n====\n")
        cur = None

out.write("\nunique: %d\n" % n)
out.close()
print("unique:", n)
