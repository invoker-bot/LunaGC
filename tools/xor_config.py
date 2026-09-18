import sys

# field-6 payload of QueryRegionListHttpRsp, XOR-ed with Crypto.DISPATCH_KEY
hex_payload = sys.argv[1]
key_path = sys.argv[2]

payload = bytes.fromhex(hex_payload)
key = open(key_path, 'rb').read()

out = bytes(b ^ key[i % len(key)] for i, b in enumerate(payload))
print(out.decode('utf-8', 'replace'))
