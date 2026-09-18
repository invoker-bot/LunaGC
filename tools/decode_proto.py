import struct, sys

def read_varint(b, i):
    shift = 0; val = 0
    while True:
        x = b[i]; i += 1
        val |= (x & 0x7f) << shift
        if not (x & 0x80):
            break
        shift += 7
    return val, i

def parse(b, depth=0):
    out = []; i = 0
    while i < len(b):
        tag, i = read_varint(b, i)
        fn = tag >> 3; wt = tag & 7
        if wt == 0:
            v, i = read_varint(b, i); out.append((depth, fn, "varint", v))
        elif wt == 2:
            ln, i = read_varint(b, i)
            payload = b[i:i+ln]; i += ln
            out.append((depth, fn, "len", payload.hex(), len(payload)))
        elif wt == 5:
            v = struct.unpack('<I', b[i:i+4])[0]; i += 4; out.append((depth, fn, "i32", v))
        elif wt == 7:
            v = struct.unpack('<d', b[i:i+8])[0]; i += 8; out.append((depth, fn, "f64", v))
        else:
            out.append((depth, fn, "WIRE?", wt)); break
    return out

def show(path):
    b = open(path, 'rb').read()
    print("total", len(b))
    for d in parse(b):
        print("  " * d[0], *d[1:])

if __name__ == "__main__":
    for p in sys.argv[1:]:
        print("==", p)
        show(p)
